package com.example

import com.example.domain.AuthAccountPolicy
import com.example.domain.AuthMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthAccountPolicyTest {

    @Test
    fun emailAndPasswordValidation_enforcesFormatAndLengthRules() {
        assertNotNull(AuthAccountPolicy.validateEmail(""))
        assertNotNull(AuthAccountPolicy.validateEmail("not-an-email"))
        assertNull(AuthAccountPolicy.validateEmail("  Principal@School.org  "))
        assertEquals("principal@school.org", AuthAccountPolicy.normalizeEmail("  Principal@School.org  "))

        assertNotNull(AuthAccountPolicy.validatePassword("", isSignUp = false))
        assertNull(AuthAccountPolicy.validatePassword("1234", isSignUp = false))
        assertNotNull(AuthAccountPolicy.validatePassword("12345", isSignUp = true))
        assertNull(AuthAccountPolicy.validatePassword("123456", isSignUp = true))
    }

    @Test
    fun signUpValidation_requiresNameValidEmailStrongPasswordAndMatch() {
        assertNotNull(AuthAccountPolicy.validateSignUpInputs("A", "a@b.com", "secret1", "secret1"))
        assertNotNull(AuthAccountPolicy.validateSignUpInputs("Asha Devi", "bad", "secret1", "secret1"))
        assertNotNull(AuthAccountPolicy.validateSignUpInputs("Asha Devi", "asha@school.in", "123", "123"))
        assertNotNull(AuthAccountPolicy.validateSignUpInputs("Asha Devi", "asha@school.in", "secret1", "secret2"))
        assertNull(AuthAccountPolicy.validateSignUpInputs("Asha Devi", "asha@school.in", "secret1", "secret1"))
    }

    @Test
    fun emailVerificationGate_blocksOnlyUnverifiedPasswordOnlyAccounts() {
        // Unverified E-mail & Password account -> MUST verify before entering app
        assertTrue(
            AuthAccountPolicy.requiresEmailVerification(
                providerIds = listOf(AuthAccountPolicy.PROVIDER_PASSWORD),
                isEmailVerified = false
            )
        )
        // Verified E-mail & Password account -> allowed (including offline restarts)
        assertFalse(
            AuthAccountPolicy.requiresEmailVerification(
                providerIds = listOf(AuthAccountPolicy.PROVIDER_PASSWORD),
                isEmailVerified = true
            )
        )
        // Google Sign-In account -> already verified by Google
        assertFalse(
            AuthAccountPolicy.requiresEmailVerification(
                providerIds = listOf(AuthAccountPolicy.PROVIDER_GOOGLE),
                isEmailVerified = false
            )
        )
        // Linked Google + Password account -> allowed without extra gate
        assertFalse(
            AuthAccountPolicy.requiresEmailVerification(
                providerIds = listOf(AuthAccountPolicy.PROVIDER_GOOGLE, AuthAccountPolicy.PROVIDER_PASSWORD),
                isEmailVerified = false
            )
        )
    }

    @Test
    fun determineReauthMethod_matchesProviderAndLastUsedMethodForLinkedAccounts() {
        // Google-only user -> always Google re-auth
        assertEquals(
            AuthMethod.GOOGLE,
            AuthAccountPolicy.determineReauthMethod(
                providerIds = listOf(AuthAccountPolicy.PROVIDER_GOOGLE),
                lastSignInMethod = AuthMethod.EMAIL_PASSWORD
            )
        )
        // Password-only user -> always E-mail & Password re-auth
        assertEquals(
            AuthMethod.EMAIL_PASSWORD,
            AuthAccountPolicy.determineReauthMethod(
                providerIds = listOf(AuthAccountPolicy.PROVIDER_PASSWORD),
                lastSignInMethod = AuthMethod.GOOGLE
            )
        )
        // Linked user who signed in with Password -> Password re-auth
        assertEquals(
            AuthMethod.EMAIL_PASSWORD,
            AuthAccountPolicy.determineReauthMethod(
                providerIds = listOf(AuthAccountPolicy.PROVIDER_GOOGLE, AuthAccountPolicy.PROVIDER_PASSWORD),
                lastSignInMethod = AuthMethod.EMAIL_PASSWORD
            )
        )
        // Linked user who signed in with Google -> Google re-auth
        assertEquals(
            AuthMethod.GOOGLE,
            AuthAccountPolicy.determineReauthMethod(
                providerIds = listOf(AuthAccountPolicy.PROVIDER_GOOGLE, AuthAccountPolicy.PROVIDER_PASSWORD),
                lastSignInMethod = AuthMethod.GOOGLE
            )
        )
    }

    @Test
    fun friendlyAuthErrorMessages_guideSameEmailLinkingAndFirebaseConsoleSetup() {
        val emailInUse = AuthAccountPolicy.friendlyAuthErrorMessage("ERROR_EMAIL_ALREADY_IN_USE")
        assertTrue(emailInUse.contains("Link Password", ignoreCase = true))

        val diffCred = AuthAccountPolicy.friendlyAuthErrorMessage("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL")
        assertTrue(diffCred.contains("same account", ignoreCase = true))

        val invalidCred = AuthAccountPolicy.friendlyAuthErrorMessage("ERROR_INVALID_CREDENTIAL")
        assertTrue(invalidCred.contains("Continue with Google", ignoreCase = true))

        val notAllowed = AuthAccountPolicy.friendlyAuthErrorMessage("ERROR_OPERATION_NOT_ALLOWED")
        assertTrue(notAllowed.contains("Firebase Console", ignoreCase = true))
    }
}
