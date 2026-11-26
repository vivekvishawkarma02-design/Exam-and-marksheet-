package com.example.domain

/**
 * Central Play Store legal/support URL and contact metadata.
 * Replace the [YOUR_...] placeholders with your hosted GitHub Pages / Firebase Hosting
 * URLs and real support e-mail address before publishing on Google Play Console.
 */
object LegalConfig {
    const val CONTACT_EMAIL_PLACEHOLDER = "[YOUR_CONTACT_EMAIL]"
    const val PRIVACY_POLICY_URL = "https://[YOUR_HOST_DOMAIN]/privacy.html"
    const val DELETE_ACCOUNT_URL = "https://[YOUR_HOST_DOMAIN]/delete-account.html"
    const val SUPPORT_URL = "https://[YOUR_HOST_DOMAIN]/support.html"

    fun isConfiguredUrl(url: String): Boolean =
        url.startsWith("https://") && !url.contains("[YOUR_")
}
