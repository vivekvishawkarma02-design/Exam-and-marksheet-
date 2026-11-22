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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.example.domain.OfflineSyncEngine
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

object GoogleAuthHelper {
    private const val TAG = "Auth"

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
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
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
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit
    ) {
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("Unable to find active window for Google Sign-In.")
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found.")
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type returned.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Sign in failed. Please check internet connection and try again.")
            }
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
