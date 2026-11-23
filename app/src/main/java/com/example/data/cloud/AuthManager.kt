package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.domain.AuthAccountPolicy
import com.example.domain.AuthMethod
import com.example.domain.OfflineSyncEngine
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FirestoreProvider {
    @Volatile
    private var configuredDbId: String? = null

    fun getDatabase(context: Context): FirebaseFirestore {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        return getDatabaseById(dbId)
    }

    fun getDatabaseById(databaseId: String): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance(databaseId)
        if (configuredDbId != databaseId) {
            synchronized(this) {
                if (configuredDbId != databaseId) {
                    try {
                        val settings = FirebaseFirestoreSettings.Builder()
                            .setLocalCacheSettings(
                                PersistentCacheSettings.newBuilder()
                                    .setSizeBytes(OfflineSyncEngine.FIRESTORE_CACHE_SIZE_BYTES)
                                    .build()
                            )
                            .build()
                        db.firestoreSettings = settings
                    } catch (e: IllegalStateException) {
                        // Firestore already started with persistence enabled
                        Log.d("FirestoreProvider", "Firestore already initialized: ${e.message}")
                    }
                    configuredDbId = databaseId
                }
            }
        }
        return db
    }

    suspend fun clearLocalAccountCache(databaseId: String) {
        try {
            val db = FirebaseFirestore.getInstance(databaseId)
            db.terminate().await()
            db.clearPersistence().await()
            configuredDbId = null
        } catch (e: Exception) {
            Log.w("FirestoreProvider", "Could not clear Firestore offline cache: ${e.message}")
        }
    }

    suspend fun resetLocalCacheAndReinit(context: Context): FirebaseFirestore {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        clearLocalAccountCache(dbId)
        return getDatabaseById(dbId)
    }
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

sealed interface EmailSignUpOutcome {
    data object VerificationSent : EmailSignUpOutcome
    data class EmailAlreadyExists(val email: String, val message: String) : EmailSignUpOutcome
    data class Error(val message: String) : EmailSignUpOutcome
}

object GoogleAuthHelper {
    private const val TAG = "Auth"

    fun currentProviderIds(user: FirebaseUser?): List<String> =
        user?.providerData?.mapNotNull { it.providerId?.takeIf { id -> id != "firebase" } }.orEmpty()

    fun requiresEmailVerification(user: FirebaseUser?): Boolean {
        if (user == null) return false
        return AuthAccountPolicy.requiresEmailVerification(
            providerIds = currentProviderIds(user),
            isEmailVerified = user.isEmailVerified
        )
    }

    fun determineReauthMethod(context: Context, user: FirebaseUser?): AuthMethod {
        val uid = user?.uid.orEmpty()
        val lastMethod = if (uid.isNotBlank()) {
            OfflinePrefsStore(context.applicationContext).getLastSignInMethod(uid)
        } else null
        return AuthAccountPolicy.determineReauthMethod(
            providerIds = currentProviderIds(user),
            lastSignInMethod = lastMethod
        )
    }

    private fun recordSignInMethod(context: Context, method: AuthMethod) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        OfflinePrefsStore(context.applicationContext).setLastSignInMethod(uid, method)
    }

    private fun resolveDefaultWebClientId(context: Context): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) return null
        return try {
            context.getString(resId).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun requestGoogleCredential(
        context: Context,
        credentialManager: CredentialManager
    ): Result<Pair<AuthCredential, String>> {
        val activity = context.findActivity()
            ?: return Result.failure(IllegalStateException("Unable to find active window for Google Sign-In."))
        val clientId = resolveDefaultWebClientId(context)
            ?: return Result.failure(IllegalStateException("Google Sign-In configuration missing: default_web_client_id not found."))
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        val cred = credentialManager.getCredential(activity, request).credential
        if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
            return Result.success(GoogleAuthProvider.getCredential(googleCred.idToken, null) to googleCred.id)
        }
        return Result.failure(IllegalStateException("Unexpected credential type returned."))
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            onUnauthenticated()
            return
        }
        val clientId = resolveDefaultWebClientId(context)
        if (clientId == null) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    recordSignInMethod(context, AuthMethod.GOOGLE)
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onCollisionNeedsPasswordLink: ((pendingGoogleCredential: AuthCredential, emailHint: String, message: String) -> Unit)? = null,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit
    ) {
        scope.launch {
            var capturedCred: AuthCredential? = null
            var capturedEmail = ""
            try {
                val (authCred, email) = requestGoogleCredential(context, credentialManager).getOrThrow()
                capturedCred = authCred
                capturedEmail = email
                Firebase.auth.signInWithCredential(authCred).await()
                recordSignInMethod(context, AuthMethod.GOOGLE)
                onAuthSuccess()
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: FirebaseAuthUserCollisionException) {
                Log.w(TAG, "Google Sign-In collision with existing e-mail/password account", e)
                val msg = AuthAccountPolicy.friendlyAuthErrorMessage(
                    e.errorCode,
                    "An account with this e-mail already exists. Enter your password below to link Google to the same account."
                )
                val cred = capturedCred
                if (cred != null && onCollisionNeedsPasswordLink != null) {
                    onCollisionNeedsPasswordLink(cred, e.email ?: capturedEmail, msg)
                } else {
                    onAuthError(msg)
                }
            } catch (e: FirebaseAuthException) {
                Log.e(TAG, "Google Sign-In FirebaseAuthException (${e.errorCode})", e)
                onAuthError(AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage))
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Sign in failed. Please check internet connection and try again.")
            }
        }
    }

    /**
     * Signs in with E-mail & Password. If a pending Google credential was captured from a prior
     * collision on the same e-mail, atomically links it so both providers share one Firebase UID
     * and one set of organisation memberships.
     */
    fun signInWithEmailPassword(
        context: Context,
        email: String,
        password: String,
        pendingGoogleCredentialToLink: AuthCredential? = null,
        scope: CoroutineScope,
        onSuccess: (linkedGoogle: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val validationError = AuthAccountPolicy.validateSignInInputs(email, password)
        if (validationError != null) {
            onError(validationError)
            return
        }
        val normalizedEmail = AuthAccountPolicy.normalizeEmail(email)
        scope.launch {
            try {
                val result = Firebase.auth.signInWithEmailAndPassword(normalizedEmail, password).await()
                val signedInUser = result.user
                var linked = false
                if (signedInUser != null && pendingGoogleCredentialToLink != null) {
                    try {
                        signedInUser.linkWithCredential(pendingGoogleCredentialToLink).await()
                        linked = true
                    } catch (linkErr: Exception) {
                        Log.w(TAG, "Could not link pending Google credential: ${linkErr.message}")
                    }
                }
                recordSignInMethod(context, AuthMethod.EMAIL_PASSWORD)
                onSuccess(linked)
            } catch (e: FirebaseAuthException) {
                Log.w(TAG, "Email sign-in failed (${e.errorCode}): ${e.message}")
                onError(AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage))
            } catch (e: Exception) {
                Log.e(TAG, "Email sign-in failed", e)
                onError(e.localizedMessage ?: "Could not sign in. Please check your internet connection.")
            }
        }
    }

    /**
     * Registers a new E-mail & Password user, sets their display name, and sends a verification e-mail.
     * If an account with the same e-mail already exists (e.g. registered with Google), Firebase refuses
     * creating a duplicate account and we return [EmailSignUpOutcome.EmailAlreadyExists] so the user can
     * link their password onto the same single account via Google Sign-In.
     */
    fun signUpWithEmailPassword(
        context: Context,
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String,
        scope: CoroutineScope,
        onResult: (EmailSignUpOutcome) -> Unit
    ) {
        val validationError = AuthAccountPolicy.validateSignUpInputs(fullName, email, password, confirmPassword)
        if (validationError != null) {
            onResult(EmailSignUpOutcome.Error(validationError))
            return
        }
        val normalizedEmail = AuthAccountPolicy.normalizeEmail(email)
        scope.launch {
            try {
                val authResult = Firebase.auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
                val newUser = authResult.user
                if (newUser != null) {
                    try {
                        val profileUpdate = UserProfileChangeRequest.Builder()
                            .setDisplayName(fullName.trim().take(120))
                            .build()
                        newUser.updateProfile(profileUpdate).await()
                    } catch (profileErr: Exception) {
                        Log.w(TAG, "Display name update skipped: ${profileErr.message}")
                    }
                    try {
                        newUser.sendEmailVerification().await()
                    } catch (verifyErr: Exception) {
                        Log.w(TAG, "Initial verification email warning: ${verifyErr.message}")
                    }
                    recordSignInMethod(context, AuthMethod.EMAIL_PASSWORD)
                }
                onResult(EmailSignUpOutcome.VerificationSent)
            } catch (e: FirebaseAuthUserCollisionException) {
                onResult(
                    EmailSignUpOutcome.EmailAlreadyExists(
                        email = normalizedEmail,
                        message = AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage)
                    )
                )
            } catch (e: FirebaseAuthException) {
                Log.w(TAG, "Email sign-up failed (${e.errorCode}): ${e.message}")
                if (e.errorCode == "ERROR_EMAIL_ALREADY_IN_USE") {
                    onResult(
                        EmailSignUpOutcome.EmailAlreadyExists(
                            email = normalizedEmail,
                            message = AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage)
                        )
                    )
                } else {
                    onResult(EmailSignUpOutcome.Error(AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Email sign-up failed", e)
                onResult(EmailSignUpOutcome.Error(e.localizedMessage ?: "Could not create account."))
            }
        }
    }

    /**
     * Resolves a Sign-Up collision when the user already has a Google account with [email]:
     * signs in with Google via Credential Manager, confirms the Google e-mail matches [email],
     * and links the E-mail/Password credential onto that exact same user (single UID, single memberships).
     */
    fun signInWithGoogleAndLinkPassword(
        context: Context,
        credentialManager: CredentialManager,
        email: String,
        password: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val passError = AuthAccountPolicy.validatePassword(password, isSignUp = true)
        if (passError != null) {
            onError(passError)
            return
        }
        val normalizedTarget = AuthAccountPolicy.normalizeEmail(email)
        scope.launch {
            try {
                val (googleCred, _) = requestGoogleCredential(context, credentialManager).getOrThrow()
                val signInRes = Firebase.auth.signInWithCredential(googleCred).await()
                val user = signInRes.user
                    ?: throw IllegalStateException("Google sign-in did not return an active user.")
                val signedInEmail = AuthAccountPolicy.normalizeEmail(user.email.orEmpty())
                if (signedInEmail == normalizedTarget) {
                    val alreadyHasPassword = currentProviderIds(user).contains(AuthAccountPolicy.PROVIDER_PASSWORD)
                    if (!alreadyHasPassword) {
                        val emailCred = EmailAuthProvider.getCredential(normalizedTarget, password)
                        user.linkWithCredential(emailCred).await()
                    }
                }
                recordSignInMethod(context, AuthMethod.GOOGLE)
                onSuccess()
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google sign-in cancelled during password link", e)
                onCancelled()
            } catch (e: FirebaseAuthException) {
                Log.e(TAG, "Google + password link failed (${e.errorCode})", e)
                onError(AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage))
            } catch (e: Exception) {
                Log.e(TAG, "Google + password link failed", e)
                onError(e.localizedMessage ?: "Could not link Google and password accounts.")
            }
        }
    }

    fun sendPasswordResetEmail(
        email: String,
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val emailError = AuthAccountPolicy.validateEmail(email)
        if (emailError != null) {
            onError(emailError)
            return
        }
        val normalizedEmail = AuthAccountPolicy.normalizeEmail(email)
        scope.launch {
            try {
                Firebase.auth.sendPasswordResetEmail(normalizedEmail).await()
                onSuccess("Password reset link sent to $normalizedEmail. Check your inbox (and spam folder).")
            } catch (e: FirebaseAuthException) {
                Log.w(TAG, "Password reset failed (${e.errorCode}): ${e.message}")
                onError(AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage))
            } catch (e: Exception) {
                Log.e(TAG, "Password reset failed", e)
                onError(e.localizedMessage ?: "Could not send password reset e-mail.")
            }
        }
    }

    fun resendVerificationEmail(
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            onError("Not signed in.")
            return
        }
        scope.launch {
            try {
                user.sendEmailVerification().await()
                onSuccess("Verification e-mail resent to ${user.email.orEmpty()}.")
            } catch (e: FirebaseAuthException) {
                onError(AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage))
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Could not resend verification e-mail.")
            }
        }
    }

    fun reloadAndCheckEmailVerified(
        scope: CoroutineScope,
        onResult: (verified: Boolean, error: String?) -> Unit
    ) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            onResult(false, "Not signed in.")
            return
        }
        scope.launch {
            try {
                user.reload().await()
                val refreshed = Firebase.auth.currentUser
                onResult(refreshed?.isEmailVerified == true, null)
            } catch (e: FirebaseAuthException) {
                onResult(false, AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage))
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Could not refresh verification status.")
            }
        }
    }

    /**
     * Re-authenticates the currently signed-in user with Google before sensitive actions
     * such as permanent account deletion.
     */
    suspend fun reauthenticateWithGoogle(
        context: Context,
        credentialManager: CredentialManager
    ): Result<Unit> {
        val user = Firebase.auth.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in."))
        return try {
            val (googleCred, _) = requestGoogleCredential(context, credentialManager).getOrThrow()
            user.reauthenticate(googleCred).await()
            Result.success(Unit)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google re-authentication cancelled", e)
            Result.failure(IllegalStateException("Google verification was cancelled."))
        } catch (e: FirebaseAuthException) {
            Result.failure(IllegalStateException(AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage)))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Google re-authentication failed."))
        }
    }

    /**
     * Re-authenticates the currently signed-in user with their e-mail & password before
     * sensitive actions such as permanent account deletion.
     */
    suspend fun reauthenticateWithPassword(password: String): Result<Unit> {
        val user = Firebase.auth.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in."))
        val email = user.email?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("No e-mail address is associated with this account."))
        if (password.isBlank()) {
            return Result.failure(IllegalStateException("Please enter your password to confirm account deletion."))
        }
        return try {
            val cred = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(cred).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthException) {
            Result.failure(IllegalStateException(AuthAccountPolicy.friendlyAuthErrorMessage(e.errorCode, e.localizedMessage)))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Password verification failed."))
        }
    }

    fun signOutAndClearLocalCache(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onBeforeSignOut: () -> Unit = {},
        onSignOutComplete: () -> Unit = {}
    ) {
        val dbId = context.applicationContext.getString(R.string.firestore_database_id)
        scope.launch {
            withContext(NonCancellable) {
                try {
                    onBeforeSignOut()
                } catch (e: Exception) {
                    Log.w(TAG, "Listener cleanup warning: ${e.message}")
                }
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                }
                Firebase.auth.signOut()
                OfflinePrefsStore(context.applicationContext).clearAll()
                FirestoreProvider.clearLocalAccountCache(dbId)
                onSignOutComplete()
            }
        }
    }
}
