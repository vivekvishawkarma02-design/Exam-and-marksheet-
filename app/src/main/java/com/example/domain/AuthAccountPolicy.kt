package com.example.domain

/**
 * Pure Kotlin authentication policy & validation helpers shared by AuthScreen,
 * EmailVerificationGateScreen, DeleteAccountDialog, and AuthManager.
 * Zero Android/Firebase dependencies so every rule is verified directly by JUnit tests.
 */

enum class AuthMethod(val wireValue: String, val label: String) {
    GOOGLE("google", "Google"),
    EMAIL_PASSWORD("password", "E-mail & Password");

    companion object {
        fun fromWire(raw: String?): AuthMethod? =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class EmailAuthMode(val label: String) {
    SIGN_IN("Sign In"),
    SIGN_UP("Sign Up"),
    FORGOT_PASSWORD("Forgot Password")
}

object AuthAccountPolicy {
    const val PROVIDER_GOOGLE = "google.com"
    const val PROVIDER_PASSWORD = "password"
    private const val MIN_PASSWORD_LENGTH = 6

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun validateEmail(raw: String): String? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return "Please enter your e-mail address."
        if (!EMAIL_REGEX.matches(cleaned)) return "Please enter a valid e-mail address."
        return null
    }

    fun validatePassword(password: String, isSignUp: Boolean): String? {
        if (password.isEmpty()) return "Please enter your password."
        if (isSignUp && password.length < MIN_PASSWORD_LENGTH) {
            return "Password must be at least $MIN_PASSWORD_LENGTH characters."
        }
        return null
    }

    fun validateSignInInputs(email: String, password: String): String? {
        validateEmail(email)?.let { return it }
        validatePassword(password, isSignUp = false)?.let { return it }
        return null
    }

    fun validateSignUpInputs(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): String? {
        if (fullName.trim().length < 2) return "Please enter your full name (at least 2 characters)."
        validateEmail(email)?.let { return it }
        validatePassword(password, isSignUp = true)?.let { return it }
        if (password != confirmPassword) return "Passwords do not match."
        return null
    }

    /**
     * E-mail/password accounts must verify their e-mail address before entering any organisation
     * or data screen. Google Sign-In accounts are already verified by Google and never blocked.
     */
    fun requiresEmailVerification(
        providerIds: Collection<String>,
        isEmailVerified: Boolean
    ): Boolean {
        if (isEmailVerified) return false
        val hasGoogle = providerIds.any { it == PROVIDER_GOOGLE }
        val hasPassword = providerIds.any { it == PROVIDER_PASSWORD }
        return hasPassword && !hasGoogle
    }

    /**
     * Selects the re-authentication method required before permanently deleting an account:
     * - Single-provider accounts always use that provider.
     * - Linked accounts (both Google and E-mail/Password) use the method the user signed in with
     *   (defaulting to Google if unknown).
     */
    fun determineReauthMethod(
        providerIds: Collection<String>,
        lastSignInMethod: AuthMethod?
    ): AuthMethod {
        val hasGoogle = providerIds.any { it == PROVIDER_GOOGLE }
        val hasPassword = providerIds.any { it == PROVIDER_PASSWORD }
        return when {
            hasGoogle && !hasPassword -> AuthMethod.GOOGLE
            hasPassword && !hasGoogle -> AuthMethod.EMAIL_PASSWORD
            hasGoogle && hasPassword -> lastSignInMethod ?: AuthMethod.GOOGLE
            else -> lastSignInMethod ?: AuthMethod.GOOGLE
        }
    }

    /**
     * Maps Firebase Auth error codes to clear, actionable messages — including collision/linking
     * guidance so users never end up confused or trying to create duplicate accounts.
     */
    fun friendlyAuthErrorMessage(errorCode: String?, fallbackMessage: String? = null): String {
        return when (errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" ->
                "An account with this e-mail already exists. Sign in with your existing method, or tap 'Continue with Google & Link Password' to add password login to the same account."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "An account with this e-mail already exists with e-mail and password. Sign in with your password below to link Google to the exact same account."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" ->
                "Incorrect e-mail or password. If you originally signed up with Google, tap 'Continue with Google' above."
            "ERROR_USER_NOT_FOUND" ->
                "No account found for this e-mail address. Switch to Sign Up or use 'Continue with Google'."
            "ERROR_INVALID_EMAIL" ->
                "Please enter a valid e-mail address."
            "ERROR_WEAK_PASSWORD" ->
                "Please choose a stronger password (at least $MIN_PASSWORD_LENGTH characters)."
            "ERROR_TOO_MANY_REQUESTS" ->
                "Too many attempts. Please wait a minute and try again."
            "ERROR_NETWORK_REQUEST_FAILED" ->
                "No internet connection. Please connect to the internet and try again."
            "ERROR_REQUIRES_RECENT_LOGIN" ->
                "Please re-authenticate to confirm this sensitive action."
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "E-mail & password sign-in is not enabled yet in your Firebase Console (Authentication -> Sign-in method -> Email/Password). Use 'Continue with Google' or enable Email/Password in Firebase Console."
            "ERROR_CREDENTIAL_ALREADY_IN_USE" ->
                "That sign-in credential is already linked to another account."
            "ERROR_PROVIDER_ALREADY_LINKED" ->
                "This sign-in method is already linked to your account."
            else -> fallbackMessage?.takeIf { it.isNotBlank() }
                ?: "Authentication failed. Please check your details and internet connection."
        }
    }
}
