package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.cloud.CloudMember
import com.example.data.cloud.CloudMembershipEntry
import com.example.data.cloud.CloudPendingRequestRef
import com.example.data.cloud.EmailSignUpOutcome
import com.example.data.cloud.GoogleAuthHelper
import com.example.domain.DefaultSchoolTemplate
import com.example.domain.EmailAuthMode
import com.example.domain.LegalConfig
import com.example.domain.OrgAccessEvaluator
import com.example.domain.OrgRole
import com.example.domain.SyncStatus
import com.google.firebase.auth.AuthCredential
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import com.example.ui.theme.SteelSecondary
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LegalDocType(val title: String) {
    PRIVACY_POLICY("Privacy Policy"),
    TERMS_OF_SERVICE("Terms of Service"),
    SUPPORT("Support & Help"),
    DELETE_ACCOUNT("Delete Account & Data")
}

// =============================================================================
// 1. LOGIN / SIGN-UP FIRST SCREEN (Google Sign-In + E-mail & Password)
// =============================================================================
@Composable
fun AuthScreen(
    isOnline: Boolean = true,
    onOpenLegalDoc: (LegalDocType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isCheckingAutoSignIn by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("Signing in…") }
    var authError by remember { mutableStateOf<String?>(null) }
    var authInfo by remember { mutableStateOf<String?>(null) }

    var emailMode by remember { mutableStateOf(EmailAuthMode.SIGN_IN) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Same-email collision & linking state (never creates a second account)
    var pendingGoogleCredToLink by remember { mutableStateOf<AuthCredential?>(null) }
    var signUpCollisionEmail by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        GoogleAuthHelper.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = { isCheckingAutoSignIn = false },
            onUnauthenticated = { isCheckingAutoSignIn = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Branded Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(listOf(NavyDark, NavyPrimary, SteelSecondary))
                        )
                        .padding(horizontal = 24.dp, vertical = 22.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = "School Report Card",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "School Report Card",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Multi-School Report Cards, Marks & Staff Collaboration",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Sign-In / Sign-Up Card (BOTH Google & E-mail/Password)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Sign in or create account",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (!isOnline) {
                            OfflineActionNoticeBanner(
                                message = "Internet connection required for first-time sign-in. Once signed in, the app works fully offline."
                            )
                        }

                        if (authError != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FailRedContainer,
                                border = BorderStroke(1.dp, FailRed.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_error_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = FailRed)
                                    Text(
                                        text = authError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FailRed
                                    )
                                }
                            }
                        }

                        if (authInfo != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PassGreenContainer,
                                border = BorderStroke(1.dp, PassGreen.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_info_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PassGreen)
                                    Text(
                                        text = authInfo.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PassGreen
                                    )
                                }
                            }
                        }

                        // Guided linking banner if Google Sign-In collided with an existing password account
                        if (pendingGoogleCredToLink != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AbsentAmberContainer,
                                border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = AbsentAmber)
                                    Text(
                                        text = "Link Google Account: Enter your existing password below once to sign in and link Google to the exact same account (single set of memberships).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (isCheckingAutoSignIn || isBusy) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (isCheckingAutoSignIn) "Checking active session…" else busyLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // 1) Continue with Google
                            Button(
                                onClick = {
                                    authError = null
                                    authInfo = null
                                    signUpCollisionEmail = null
                                    busyLabel = "Signing in with Google…"
                                    isBusy = true
                                    GoogleAuthHelper.onGoogleSignInClicked(
                                        context = context,
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onAuthSuccess = {
                                            isBusy = false
                                            pendingGoogleCredToLink = null
                                        },
                                        onCollisionNeedsPasswordLink = { cred, emailHint, msg ->
                                            isBusy = false
                                            pendingGoogleCredToLink = cred
                                            if (emailHint.isNotBlank()) email = emailHint
                                            emailMode = EmailAuthMode.SIGN_IN
                                            authError = msg
                                        },
                                        onAuthError = { msg ->
                                            isBusy = false
                                            authError = msg
                                        },
                                        onAuthCancelled = { isBusy = false }
                                    )
                                },
                                enabled = isOnline,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("google_sign_in_button")
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Continue with Google",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Divider between Google and E-mail/Password
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Text(
                                    text = "OR USE E-MAIL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }

                            // 2) E-mail & Password Mode Selector (Sign In / Sign Up)
                            if (emailMode != EmailAuthMode.FORGOT_PASSWORD) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(EmailAuthMode.SIGN_IN, EmailAuthMode.SIGN_UP).forEach { tab ->
                                        val selected = emailMode == tab
                                        FilterChip(
                                            selected = selected,
                                            onClick = {
                                                emailMode = tab
                                                authError = null
                                                authInfo = null
                                                signUpCollisionEmail = null
                                            },
                                            label = {
                                                Text(
                                                    text = tab.label,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textAlign = TextAlign.Center
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .testTag("auth_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Reset Password",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(
                                        onClick = {
                                            emailMode = EmailAuthMode.SIGN_IN
                                            authError = null
                                            authInfo = null
                                        },
                                        modifier = Modifier.testTag("auth_back_to_sign_in")
                                    ) {
                                        Text("Back to Sign In")
                                    }
                                }
                            }

                            // Sign-Up Full Name field
                            if (emailMode == EmailAuthMode.SIGN_UP) {
                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = { Text("Full Name") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_name_input")
                                )
                            }

                            // E-mail field (all 3 modes)
                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    email = it
                                    if (signUpCollisionEmail != null) signUpCollisionEmail = null
                                },
                                label = { Text("E-mail Address") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_email_input")
                            )

                            // Password field (Sign In & Sign Up)
                            if (emailMode != EmailAuthMode.FORGOT_PASSWORD) {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text(if (emailMode == EmailAuthMode.SIGN_UP) "Password (min. 6 characters)" else "Password") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (showPassword) "Hide password" else "Show password"
                                            )
                                        }
                                    },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_password_input")
                                )
                            }

                            // Confirm Password field (Sign Up)
                            if (emailMode == EmailAuthMode.SIGN_UP) {
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    label = { Text("Confirm Password") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_confirm_password_input")
                                )
                            }

                            // Forgot password link (Sign In mode)
                            if (emailMode == EmailAuthMode.SIGN_IN) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            emailMode = EmailAuthMode.FORGOT_PASSWORD
                                            authError = null
                                            authInfo = null
                                        },
                                        modifier = Modifier.testTag("forgot_password_button")
                                    ) {
                                        Text("Forgot password?")
                                    }
                                }
                            }

                            // Primary E-mail Action Button
                            OutlinedButton(
                                onClick = {
                                    authError = null
                                    authInfo = null
                                    when (emailMode) {
                                        EmailAuthMode.SIGN_IN -> {
                                            busyLabel = if (pendingGoogleCredToLink != null) {
                                                "Signing in & linking Google…"
                                            } else {
                                                "Signing in…"
                                            }
                                            isBusy = true
                                            GoogleAuthHelper.signInWithEmailPassword(
                                                context = context,
                                                email = email,
                                                password = password,
                                                pendingGoogleCredentialToLink = pendingGoogleCredToLink,
                                                scope = scope,
                                                onSuccess = {
                                                    isBusy = false
                                                    pendingGoogleCredToLink = null
                                                },
                                                onError = { msg ->
                                                    isBusy = false
                                                    authError = msg
                                                }
                                            )
                                        }

                                        EmailAuthMode.SIGN_UP -> {
                                            busyLabel = "Creating account & sending verification…"
                                            isBusy = true
                                            GoogleAuthHelper.signUpWithEmailPassword(
                                                context = context,
                                                fullName = fullName,
                                                email = email,
                                                password = password,
                                                confirmPassword = confirmPassword,
                                                scope = scope
                                            ) { outcome ->
                                                isBusy = false
                                                when (outcome) {
                                                    EmailSignUpOutcome.VerificationSent -> {
                                                        signUpCollisionEmail = null
                                                    }
                                                    is EmailSignUpOutcome.EmailAlreadyExists -> {
                                                        signUpCollisionEmail = outcome.email
                                                        authError = outcome.message
                                                    }
                                                    is EmailSignUpOutcome.Error -> {
                                                        authError = outcome.message
                                                    }
                                                }
                                            }
                                        }

                                        EmailAuthMode.FORGOT_PASSWORD -> {
                                            busyLabel = "Sending password reset e-mail…"
                                            isBusy = true
                                            GoogleAuthHelper.sendPasswordResetEmail(
                                                email = email,
                                                scope = scope,
                                                onSuccess = { msg ->
                                                    isBusy = false
                                                    authInfo = msg
                                                },
                                                onError = { msg ->
                                                    isBusy = false
                                                    authError = msg
                                                }
                                            )
                                        }
                                    }
                                },
                                enabled = isOnline,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("email_auth_submit_button")
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (emailMode) {
                                        EmailAuthMode.SIGN_IN -> if (pendingGoogleCredToLink != null) {
                                            "Sign In & Link Google Account"
                                        } else {
                                            "Sign In with E-mail"
                                        }
                                        EmailAuthMode.SIGN_UP -> "Sign Up with E-mail"
                                        EmailAuthMode.FORGOT_PASSWORD -> "Send Reset Link"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 1-tap Link Password to Existing Google Account (prevents duplicate accounts)
                            if (signUpCollisionEmail != null && emailMode == EmailAuthMode.SIGN_UP) {
                                Button(
                                    onClick = {
                                        authError = null
                                        authInfo = null
                                        busyLabel = "Signing in with Google & linking password…"
                                        isBusy = true
                                        GoogleAuthHelper.signInWithGoogleAndLinkPassword(
                                            context = context,
                                            credentialManager = credentialManager,
                                            email = signUpCollisionEmail.orEmpty(),
                                            password = password,
                                            scope = scope,
                                            onSuccess = {
                                                isBusy = false
                                                signUpCollisionEmail = null
                                            },
                                            onError = { msg ->
                                                isBusy = false
                                                authError = msg
                                            },
                                            onCancelled = { isBusy = false }
                                        )
                                    },
                                    enabled = isOnline,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("link_google_and_password_button")
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Continue with Google & Link Password",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Cloud Storage Notice
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Cloud Sync Notice: Sign in with Google or E-mail & Password. Same e-mail addresses stay linked to one account and one set of memberships, with 150 MB offline caching on this phone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Footer: Privacy Policy & Terms
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onOpenLegalDoc(LegalDocType.PRIVACY_POLICY) },
                    modifier = Modifier.testTag("auth_privacy_policy_link")
                ) {
                    Text("Privacy Policy", style = MaterialTheme.typography.labelLarge)
                }
                Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(
                    onClick = { onOpenLegalDoc(LegalDocType.TERMS_OF_SERVICE) },
                    modifier = Modifier.testTag("auth_terms_link")
                ) {
                    Text("Terms of Service", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// =============================================================================
// 1B. E-MAIL VERIFICATION GATE (Blocks data screens until E-mail is verified)
// =============================================================================
@Composable
fun EmailVerificationGateScreen(
    email: String,
    isOnline: Boolean,
    onVerified: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MarkEmailRead,
                                contentDescription = "Verify e-mail",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Text(
                        text = "Verify Your E-mail",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "We sent a verification link to ${email.ifBlank { "your e-mail address" }}. Open the link in your inbox, then tap the button below to enter the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (!isOnline) {
                        OfflineActionNoticeBanner(
                            message = "Connect to the internet to check your e-mail verification status or resend the verification link."
                        )
                    }

                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = FailRedContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = FailRed,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (infoMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PassGreenContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = infoMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = PassGreen,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            errorMessage = null
                            infoMessage = null
                            isChecking = true
                            GoogleAuthHelper.reloadAndCheckEmailVerified(scope) { verified, err ->
                                isChecking = false
                                when {
                                    err != null -> errorMessage = err
                                    verified -> onVerified()
                                    else -> errorMessage = "E-mail is not verified yet. Please click the verification link in your inbox and try again."
                                }
                            }
                        },
                        enabled = isOnline && !isChecking && !isResending,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("verify_email_continue_button")
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking…")
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("I've Verified — Continue", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            errorMessage = null
                            infoMessage = null
                            isResending = true
                            GoogleAuthHelper.resendVerificationEmail(
                                scope = scope,
                                onSuccess = { msg ->
                                    isResending = false
                                    infoMessage = msg
                                },
                                onError = { msg ->
                                    isResending = false
                                    errorMessage = msg
                                }
                            )
                        },
                        enabled = isOnline && !isChecking && !isResending,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("resend_verification_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isResending) "Sending…" else "Resend Verification E-mail")
                    }

                    TextButton(
                        onClick = onSignOutClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("verify_email_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out / Use Another Account")
                    }
                }
            }
        }
    }
}

// =============================================================================
// 2. AFTER LOGIN: ORGANISATION PICKER / CREATE OR JOIN / WAITING FOR APPROVAL
// =============================================================================
@Composable
fun OrgPickerAndOnboardingScreen(
    state: OrgSessionUiState,
    userDisplayName: String,
    userEmail: String,
    onSelectOrg: (String) -> Unit,
    onCreateOrgClick: () -> Unit,
    onJoinOrgClick: () -> Unit,
    onRefresh: () -> Unit,
    onCancelPendingRequest: (String) -> Unit,
    onOpenLegalDoc: (LegalDocType) -> Unit,
    onDeleteAccountClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val account = state.userAccount
    val memberships = account?.memberships.orEmpty()
    val pendingRequests = account?.pendingRequests.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header Bar
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userDisplayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userDisplayName.ifBlank { "Signed In" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = userEmail.ifBlank { "Google Account" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SyncStatusChip(
                    status = if (!state.isOnline) SyncStatus.OFFLINE
                    else if (state.isSyncingMemberships) SyncStatus.SYNCING
                    else SyncStatus.ONLINE
                )
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("org_picker_refresh_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh status")
                }
                IconButton(
                    onClick = onSignOutClick,
                    modifier = Modifier.testTag("org_picker_signout_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign out",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (state.isLoadingAccount && account == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Loading your organisations…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.errorMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = FailRedContainer,
                        border = BorderStroke(1.dp, FailRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = FailRed,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // A) Existing Organisations Picker (when user belongs to 1+ organisations)
            if (memberships.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = "Your Organisations",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Choose an organisation to open its schools, marks, and report cards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(memberships, key = { it.orgId }) { entry ->
                    OrganisationPickerCard(
                        entry = entry,
                        onClick = { onSelectOrg(entry.orgId) }
                    )
                }
            } else {
                item {
                    Column {
                        Text(
                            text = if (pendingRequests.isNotEmpty()) "Waiting for Approval" else "Get Started",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (pendingRequests.isNotEmpty())
                                "Your request is awaiting admin approval. You can refresh anytime or create/join another organisation."
                            else
                                "Create a new organisation for your school(s) or join an existing one with an Organisation Code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // B) Waiting for Approval / Pending Join Requests
            if (pendingRequests.isNotEmpty()) {
                items(pendingRequests, key = { "req_${it.orgId}" }) { req ->
                    PendingJoinRequestCard(
                        req = req,
                        onRefresh = onRefresh,
                        onCancel = { onCancelPendingRequest(req.orgId) }
                    )
                }
            }

            // C) Always keep two big action cards: Create Organisation & Join an Organisation
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (memberships.isEmpty()) "Choose an Option" else "Add Another Organisation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                BigOnboardingActionCard(
                    title = "Create Organisation",
                    subtitle = "Set up a school or a group of schools, reserve your unique Organisation Code, and become the Owner.",
                    icon = Icons.Default.AddBusiness,
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onCreateOrgClick,
                    testTag = "create_organisation_button"
                )
            }

            item {
                BigOnboardingActionCard(
                    title = "Join an Organisation",
                    subtitle = "Enter an Organisation Code to request access, or redeem an admin's one-time invite code.",
                    icon = Icons.Default.GroupAdd,
                    accent = MaterialTheme.colorScheme.secondary,
                    onClick = onJoinOrgClick,
                    testTag = "join_organisation_button"
                )
            }

            // Local data notice if Phase 1 Room data exists on this phone
            if (state.localMigrationSummary.hasDataToUpload) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Existing local data found on this phone (${state.localMigrationSummary.schoolCount} school(s), ${state.localMigrationSummary.studentCount} student(s)). After creating an organisation, you can upload it to the cloud in one tap.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // D) Account & Privacy Footer
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { onOpenLegalDoc(LegalDocType.PRIVACY_POLICY) }) {
                            Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Privacy Policy")
                        }
                        TextButton(onClick = { onOpenLegalDoc(LegalDocType.TERMS_OF_SERVICE) }) {
                            Text("Terms of Service")
                        }
                    }
                    OutlinedButton(
                        onClick = onDeleteAccountClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("delete_account_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete My Account")
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganisationPickerCard(
    entry: CloudMembershipEntry,
    onClick: () -> Unit
) {
    val role = OrgRole.fromRaw(entry.role)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag("org_card_${entry.orgCode}")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Apartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.orgName.ifBlank { entry.orgCode },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "@${entry.orgCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = role.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Enter organisation",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PendingJoinRequestCard(
    req: CloudPendingRequestRef,
    onRefresh: () -> Unit,
    onCancel: () -> Unit
) {
    val isRejected = req.status.equals("rejected", ignoreCase = true)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRejected) FailRedContainer else AbsentAmberContainer
        ),
        border = BorderStroke(
            1.dp,
            if (isRejected) FailRed.copy(alpha = 0.4f) else AbsentAmber.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_request_card_${req.orgCode}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isRejected) Icons.Default.Cancel else Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = if (isRejected) FailRed else AbsentAmber
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = req.orgName.ifBlank { req.orgCode },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRejected) FailRed else Color(0xFF4E2D00)
                    )
                    Text(
                        text = if (isRejected)
                            "Request was not approved • Role: ${OrgRole.fromRaw(req.requestedRole).label}"
                        else
                            "Waiting for admin approval • Requested: ${OrgRole.fromRaw(req.requestedRole).label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRejected) FailRed else Color(0xFF6B4200)
                    )
                }
            }
            Text(
                text = "School and student data remain completely hidden until an admin approves your request.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Check Status")
                }
                TextButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isRejected) "Dismiss" else "Cancel Request")
                }
            }
        }
    }
}

@Composable
private fun BigOnboardingActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.14f),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = title,
                tint = accent
            )
        }
    }
}

// =============================================================================
// 3. CREATE ORGANISATION SCREEN (Live code check + First School + <150 KB logo)
// =============================================================================
@Composable
fun CreateOrganisationScreen(
    state: OrgSessionUiState,
    defaultEmail: String,
    onCheckCode: (String) -> Unit,
    onSubmit: (
        orgName: String,
        orgCode: String,
        contactMobile: String,
        contactEmail: String,
        schoolName: String,
        schoolAddress: String,
        schoolMobile: String,
        schoolEmail: String,
        schoolLogoBase64: String
    ) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var orgName by remember { mutableStateOf("") }
    var orgCode by remember { mutableStateOf("") }
    var contactMobile by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf(defaultEmail) }

    var schoolName by remember { mutableStateOf("") }
    var schoolAddress by remember { mutableStateOf("") }
    var schoolMobile by remember { mutableStateOf("") }
    var schoolEmail by remember { mutableStateOf(defaultEmail) }
    var schoolLogoBase64 by remember { mutableStateOf("") }
    var logoInfoText by remember { mutableStateOf("No logo chosen (optional, auto-resized under 150 KB)") }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    ImageUtils.encodeUriToBase64Under150Kb(context, uri)
                }
                if (encoded != null) {
                    schoolLogoBase64 = encoded
                    val kb = (ImageUtils.estimateDecodedBytes(encoded) / 1024).coerceAtLeast(1)
                    logoInfoText = "Logo ready (~$kb KB, under 150 KB limit)"
                } else {
                    logoInfoText = "Could not load selected image"
                }
            }
        }
    }

    val isCodeAvailable = state.codeCheckState is OrgCodeCheckState.Available
    val canSubmit = !state.isSubmitting &&
        orgName.trim().length >= 2 &&
        isCodeAvailable &&
        schoolName.trim().isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("create_org_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "Create Organisation",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "You will become the Owner with full access",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!state.isOnline) {
                OfflineActionNoticeBanner(
                    message = "Creating an organisation and reserving a unique Organisation Code requires an internet connection."
                )
            }

            if (state.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FailRedContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage,
                        color = FailRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Section 1: Organisation Details
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "1. Organisation Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = orgName,
                        onValueChange = { orgName = it },
                        label = { Text("Organisation Name *") },
                        placeholder = { Text("e.g. Bright Future Education Society") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_org_name_input")
                    )
                    OutlinedTextField(
                        value = orgCode,
                        onValueChange = { raw ->
                            val cleaned = raw.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }.take(30)
                            orgCode = cleaned
                            onCheckCode(cleaned)
                        },
                        label = { Text("Unique Organisation Code *") },
                        placeholder = { Text("e.g. bright-future-schools") },
                        singleLine = true,
                        supportingText = {
                            when (val c = state.codeCheckState) {
                                OrgCodeCheckState.Idle -> Text("4–30 chars: lowercase letters, digits, hyphens")
                                is OrgCodeCheckState.Checking -> Text("Checking availability for '${c.normalized}'…")
                                is OrgCodeCheckState.Available -> Text(
                                    "✓ '${c.normalized}' is available!",
                                    color = PassGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                is OrgCodeCheckState.Taken -> Text(
                                    "✗ '${c.normalized}' is already taken. Try another code.",
                                    color = FailRed,
                                    fontWeight = FontWeight.Bold
                                )
                                is OrgCodeCheckState.Invalid -> Text(c.reason, color = FailRed)
                                is OrgCodeCheckState.Error -> Text(c.message, color = FailRed)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_org_code_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = contactMobile,
                            onValueChange = { contactMobile = it.take(40) },
                            label = { Text("Contact Mobile") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = contactEmail,
                            onValueChange = { contactEmail = it.take(160) },
                            label = { Text("Contact Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section 2: First School Details
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "2. First School Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "You can add more schools anytime later from the Schools screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = schoolName,
                        onValueChange = { schoolName = it.take(120) },
                        label = { Text("School Name *") },
                        placeholder = { Text("e.g. Sunrise Public School") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_org_school_name_input")
                    )
                    OutlinedTextField(
                        value = schoolAddress,
                        onValueChange = { schoolAddress = it.take(300) },
                        label = { Text("School Address") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = schoolMobile,
                            onValueChange = { schoolMobile = it.take(40) },
                            label = { Text("School Mobile") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = schoolEmail,
                            onValueChange = { schoolEmail = it.take(160) },
                            label = { Text("School Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // School Logo Picker (< 150 KB)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SchoolLogoAvatar(
                            logoBase64 = schoolLogoBase64,
                            schoolName = schoolName.ifBlank { "School" },
                            size = 56.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = {
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("create_org_pick_logo_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (schoolLogoBase64.isBlank()) "Upload School Logo" else "Change Logo")
                            }
                            Text(
                                text = logoInfoText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onSubmit(
                        orgName,
                        orgCode,
                        contactMobile,
                        contactEmail,
                        schoolName,
                        schoolAddress,
                        schoolMobile,
                        schoolEmail,
                        schoolLogoBase64
                    )
                },
                enabled = canSubmit,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("create_org_submit_button")
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Creating Organisation…")
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Organisation & Enter App", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =============================================================================
// 4. JOIN AN ORGANISATION SCREEN (Request Approval OR One-Time Invite Code)
// =============================================================================
@Composable
fun JoinOrganisationScreen(
    state: OrgSessionUiState,
    defaultDisplayName: String,
    onLookupCode: (String) -> Unit,
    onResetLookup: () -> Unit,
    onSubmitJoinRequest: (
        orgLookup: com.example.data.cloud.CloudOrgCodeLookup,
        displayName: String,
        message: String,
        requestedRole: OrgRole
    ) -> Unit,
    onRedeemInviteCode: (orgCode: String, inviteCode: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember { mutableIntStateOf(0) }
    var orgCodeInput by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(defaultDisplayName) }
    var message by remember { mutableStateOf("") }
    var requestedRole by remember { mutableStateOf(OrgRole.TEACHER) }

    var inviteOrgCode by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("join_org_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = "Join an Organisation",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Request admin approval or redeem a one-time invite",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Request Access") },
                        modifier = Modifier.testTag("join_tab_request")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Invite Code") },
                        modifier = Modifier.testTag("join_tab_invite")
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!state.isOnline) {
                OfflineActionNoticeBanner(
                    message = "Looking up an organisation, sending a join request, or redeeming an invite code requires an internet connection."
                )
            }

            if (state.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FailRedContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.errorMessage,
                        color = FailRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (selectedTab == 0) {
                // TAB 1: Lookup by Org Code -> Show ONLY org name -> Send join request
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Find Organisation by Code",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = orgCodeInput,
                                onValueChange = { raw ->
                                    orgCodeInput = raw.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }.take(30)
                                    if (state.joinLookupState !is JoinOrgLookupState.Idle) onResetLookup()
                                },
                                label = { Text("Organisation Code") },
                                placeholder = { Text("e.g. bright-future") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("join_org_code_input")
                            )
                            Button(
                                onClick = { onLookupCode(orgCodeInput) },
                                enabled = orgCodeInput.length >= 4 && state.joinLookupState !is JoinOrgLookupState.Searching,
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("join_org_lookup_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Find")
                            }
                        }

                        when (val lookup = state.joinLookupState) {
                            JoinOrgLookupState.Idle -> {
                                Text(
                                    text = "Ask your school admin for the Organisation Code. For privacy, only the organisation name is displayed before approval.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is JoinOrgLookupState.Searching -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Looking up '${lookup.code}'…", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is JoinOrgLookupState.NotFound -> {
                                Text(
                                    text = "No organisation found with code '${lookup.code}'. Please verify the code.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FailRed
                                )
                            }
                            is JoinOrgLookupState.Invalid -> {
                                Text(lookup.reason, style = MaterialTheme.typography.bodySmall, color = FailRed)
                            }
                            is JoinOrgLookupState.Error -> {
                                Text(lookup.message, style = MaterialTheme.typography.bodySmall, color = FailRed)
                            }
                            is JoinOrgLookupState.Found -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = PassGreenContainer,
                                    border = BorderStroke(1.dp, PassGreen.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PassGreen)
                                        Column {
                                            Text(
                                                text = "Organisation Found",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PassGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = lookup.org.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = displayName,
                                    onValueChange = { displayName = it.take(120) },
                                    label = { Text("Your Full Name *") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = "I am joining as:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OrgRole.joinRequestRoles.forEach { role ->
                                        FilterChip(
                                            selected = requestedRole == role,
                                            onClick = { requestedRole = role },
                                            label = { Text(role.shortLabel) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier.testTag("join_role_${role.name}")
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = message,
                                    onValueChange = { message = it.take(400) },
                                    label = { Text("Message to Admin (optional)") },
                                    placeholder = { Text("e.g. I teach Class 5 Math") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("join_message_input")
                                )

                                Button(
                                    onClick = {
                                        onSubmitJoinRequest(lookup.org, displayName, message, requestedRole)
                                    },
                                    enabled = !state.isSubmitting && displayName.trim().isNotEmpty(),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("submit_join_request_button")
                                ) {
                                    if (state.isSubmitting) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sending Request…")
                                    } else {
                                        Text("Send Join Request", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // TAB 2: One-Time Invite Code (Instant join without waiting for approval)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Redeem One-Time Invite Code",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "If your school admin gave you a one-time expiring invite code, enter it below to join immediately with preset permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = inviteOrgCode,
                            onValueChange = { raw ->
                                inviteOrgCode = raw.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }.take(30)
                            },
                            label = { Text("Organisation Code *") },
                            placeholder = { Text("e.g. bright-future") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("invite_org_code_input")
                        )
                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = { raw ->
                                inviteCode = raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(16)
                            },
                            label = { Text("One-Time Invite Code *") },
                            placeholder = { Text("e.g. K7M9P2QR") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("invite_secret_code_input")
                        )
                        Button(
                            onClick = { onRedeemInviteCode(inviteOrgCode, inviteCode) },
                            enabled = !state.isSubmitting && inviteOrgCode.length >= 4 && inviteCode.length >= 6,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("redeem_invite_button")
                        ) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Joining Organisation…")
                            } else {
                                Text("Join Immediately", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 5. TEACHER FIRST-LOGIN PROFILE ONBOARDING SCREEN
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeacherProfileOnboardingScreen(
    orgName: String,
    member: CloudMember,
    availableClasses: List<String>,
    availableSubjects: List<String>,
    onSaveProfile: (
        name: String,
        mobile: String,
        photoBase64: String,
        requestedSubjects: List<String>,
        requestedClasses: List<String>
    ) -> Unit,
    onSwitchOrg: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(member.name) { mutableStateOf(member.name) }
    var mobile by remember(member.mobile) { mutableStateOf(member.mobile) }
    var photoBase64 by remember(member.teacherPhotoBase64) { mutableStateOf(member.teacherPhotoBase64) }
    var customSubject by remember { mutableStateOf("") }
    var customClass by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val selectedSubjects = remember {
        mutableStateListOf<String>().apply { addAll(member.requestedSubjects) }
    }
    val selectedClasses = remember {
        mutableStateListOf<String>().apply { addAll(member.requestedClasses) }
    }

    val subjectChips = remember(availableSubjects, selectedSubjects.toList()) {
        val base = availableSubjects.ifEmpty {
            listOf("ENGLISH", "HINDI", "MATHEMATICS", "SCIENCE", "SOCIAL SCIENCE", "COMPUTER", "G.K.")
        }
        (base + selectedSubjects).distinct()
    }
    val classChips = remember(availableClasses, selectedClasses.toList()) {
        val base = availableClasses.ifEmpty { DefaultSchoolTemplate.allDefaultClasses }
        (base + selectedClasses).distinct()
    }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    ImageUtils.encodeUriToBase64Under150Kb(context, uri)
                }
                if (encoded != null) photoBase64 = encoded
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Complete Your Teacher Profile",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = orgName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onSwitchOrg) {
                    Text("Switch Org")
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StudentPhotoThumbnail(
                            photoBase64 = photoBase64,
                            studentName = name.ifBlank { "T" },
                            size = 64.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = {
                                    photoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("teacher_profile_photo_button")
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (photoBase64.isBlank()) "Add Photo (Optional)" else "Change Photo")
                            }
                            Text(
                                text = "Resized automatically under 150 KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(120) },
                        label = { Text("Full Name *") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("teacher_profile_name_input")
                    )

                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it.take(40) },
                        label = { Text("Mobile Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("teacher_profile_mobile_input")
                    )
                }
            }

            // Subjects you teach
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Subjects You Teach (Requested)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        subjectChips.forEach { sub ->
                            val picked = sub in selectedSubjects
                            FilterChip(
                                selected = picked,
                                onClick = {
                                    if (picked) selectedSubjects.remove(sub) else selectedSubjects.add(sub)
                                },
                                label = { Text(sub) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customSubject,
                            onValueChange = { customSubject = it.take(40) },
                            placeholder = { Text("Add another subject") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val c = customSubject.trim().uppercase()
                                if (c.isNotEmpty() && c !in selectedSubjects) selectedSubjects.add(c)
                                customSubject = ""
                            }
                        ) { Text("Add") }
                    }
                }
            }

            // Classes you teach
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Classes You Teach (Requested)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        classChips.forEach { cls ->
                            val picked = cls in selectedClasses
                            FilterChip(
                                selected = picked,
                                onClick = {
                                    if (picked) selectedClasses.remove(cls) else selectedClasses.add(cls)
                                },
                                label = { Text(cls) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customClass,
                            onValueChange = { customClass = it.take(24) },
                            placeholder = { Text("Add another class (e.g. 9th)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val c = customClass.trim()
                                if (c.isNotEmpty() && c !in selectedClasses) selectedClasses.add(c)
                                customClass = ""
                            }
                        ) { Text("Add") }
                    }
                }
            }

            Button(
                onClick = {
                    isSaving = true
                    onSaveProfile(name, mobile, photoBase64, selectedSubjects.toList(), selectedClasses.toList())
                },
                enabled = !isSaving && name.trim().isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_teacher_profile_button")
            ) {
                Text(
                    text = if (isSaving) "Saving Profile…" else "Save Profile & Enter School",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =============================================================================
// 6. PRIVACY POLICY, SUPPORT, ACCOUNT DELETION & TERMS SCREEN (Play Store Readiness)
// =============================================================================
@Composable
fun PrivacyAndTermsScreen(
    docType: LegalDocType,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val uriHandler = LocalUriHandler.current
    val externalUrl = when (docType) {
        LegalDocType.PRIVACY_POLICY -> LegalConfig.PRIVACY_POLICY_URL
        LegalDocType.SUPPORT -> LegalConfig.SUPPORT_URL
        LegalDocType.DELETE_ACCOUNT -> LegalConfig.DELETE_ACCOUNT_URL
        LegalDocType.TERMS_OF_SERVICE -> LegalConfig.PRIVACY_POLICY_URL
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("legal_doc_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = docType.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (docType) {
                LegalDocType.PRIVACY_POLICY -> {
                    LegalSectionCard(
                        title = "1. What Data This App Collects / कौन-सा डेटा एकत्र होता है",
                        body = "Account details (name, email, Firebase user ID) and authorised school records entered by your staff: school info & logos, student profiles (including DOB for academic records & in-app birthday wishes), guardian & authorised pickup person names/relations/mobile numbers, optional compressed photos (<150 KB, including student/staff avatars, expense bills, class diary blackboard/homework photos, and optional authorised pickup person or visitor photos), attendance, exam marks, class diary entries, sensitive student remarks & Parent-Teacher Meeting (PTM) notes/decisions, early leave gate passes, visitor register entries, fee receipts, cash book & admission enquiries, and staff salary records (Owner/permitted Admin only)."
                    )
                    LegalSectionCard(
                        title = "2. Storage, Sync & Local Cache / डेटा कहाँ सेव रहता है",
                        body = "Records are stored in Google Firebase (Firebase Authentication & Cloud Firestore) and cached on this phone for offline use. Signing out or deleting your account clears this phone's offline cache."
                    )
                    LegalSectionCard(
                        title = "3. Who Can See Data / डेटा कौन देख सकता है",
                        body = "Data is strictly isolated per organisation and role. Teachers only see schools, sections, and modules explicitly enabled by their Organisation Owner or Admin. We never sell or rent user or student data to third parties."
                    )
                    LegalSectionCard(
                        title = "4. Children's Data Note / बच्चों के डेटा की सूचना",
                        body = "This app is designed strictly for adult school staff (owners, principals, teachers, fee clerks)—not for children to use directly. Student academic records are managed exclusively by authorised school staff."
                    )
                    LegalSectionCard(
                        title = "5. Retention, Deletion & Contact / संपर्क और डेटा हटाना",
                        body = "Use Settings → Account → Delete My Account anytime to delete your account. Contact email: ${LegalConfig.CONTACT_EMAIL_PLACEHOLDER}"
                    )
                }

                LegalDocType.SUPPORT -> {
                    LegalSectionCard(
                        title = "Support Contact / सहायता संपर्क",
                        body = "For school onboarding, sign-in help, or account deletion requests (if you no longer have the app installed), email: ${LegalConfig.CONTACT_EMAIL_PLACEHOLDER}"
                    )
                    LegalSectionCard(
                        title = "Common Quick Fixes / सामान्य समाधान",
                        body = "• Offline saves: Marks, attendance, and fees save on this device first and upload automatically when internet reconnects.\n• Missing access: Ask your school's Owner or Admin to open Members and enable the required school/module permission.\n• Backup before big changes: Owners/Admins can export a full JSON + CSV backup from Home or Settings."
                    )
                }

                LegalDocType.DELETE_ACCOUNT -> {
                    LegalSectionCard(
                        title = "How to Delete In-App / ऐप से खाता कैसे हटाएँ",
                        body = "1. Open Settings → Account → Delete My Account (or Organisation Picker → Delete My Account).\n2. Review what will be deleted (your login, profile, memberships, teacher scopes, and device cache).\n3. Re-authenticate with your Password or Google Sign-In."
                    )
                    LegalSectionCard(
                        title = "Normal Member vs Organisation Owner",
                        body = "• Normal member (Teacher / Fee Clerk / Admin): School records you entered remain for school continuity, and your author name appears as \"deleted user\".\n• Only Owner of an organisation: Transfer ownership to another Admin in Members first, OR explicitly tick whole-organisation deletion and type the organisation code to remove the entire organisation and all school data."
                    )
                    LegalSectionCard(
                        title = "Request by Email (If App Uninstalled)",
                        body = "Email ${LegalConfig.CONTACT_EMAIL_PLACEHOLDER} from your registered account email with subject \"Delete Account Request\" and mention your Organisation Code."
                    )
                }

                LegalDocType.TERMS_OF_SERVICE -> {
                    LegalSectionCard(
                        title = "1. Intended Use",
                        body = "This application is intended for authorised school owners, administrators, office clerks, and teachers to manage school operations and academic report cards."
                    )
                    LegalSectionCard(
                        title = "2. Organisation Ownership & Backups",
                        body = "Organisation Owners and Admins are responsible for approving staff members, assigning module/school permissions accurately, and keeping periodic JSON backups."
                    )
                    LegalSectionCard(
                        title = "3. Offline Cache & Revocation",
                        body = "When an Admin removes a member, access is blocked immediately on Firebase servers and local cache clears when the member's phone connects to the internet."
                    )
                }
            }

            if (LegalConfig.isConfiguredUrl(externalUrl)) {
                OutlinedButton(
                    onClick = { runCatching { uriHandler.openUri(externalUrl) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("legal_doc_open_web_button")
                ) {
                    Text("Open Web Page")
                }
            } else {
                Text(
                    text = "Public web URL placeholder: $externalUrl (host the files in repo public/ folder and update LegalConfig.kt before Play release).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LegalSectionCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
