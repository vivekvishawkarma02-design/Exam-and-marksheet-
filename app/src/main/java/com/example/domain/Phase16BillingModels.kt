package com.example.domain

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Phase 16B: Google Play Billing product & base plan constants.
 * Prices are NEVER hard-coded — always read dynamically from Google Play.
 */
object Phase16BillingConstants {
    const val SUBSCRIPTION_PRODUCT_ID = "school_ad_free"
    const val BASE_PLAN_MONTHLY = "monthly"
    const val BASE_PLAN_YEARLY = "yearly"
    const val FUNCTIONS_REGION = "asia-south1"
    const val FN_VERIFY_PURCHASE = "verifyPurchase"
    const val FN_CREATE_REWARDED_NONCE = "createRewardedAdNonce"

    val ALLOWED_BASE_PLANS: Set<String> = setOf(BASE_PLAN_MONTHLY, BASE_PLAN_YEARLY)

    fun manageSubscriptionUrl(packageName: String): String =
        "https://play.google.com/store/account/subscriptions?sku=$SUBSCRIPTION_PRODUCT_ID&package=$packageName"
}

/**
 * Dynamic offer info read directly from Google Play's ProductDetails (never hard-coded prices).
 */
data class PlaySubscriptionPlanOffer(
    val productId: String = Phase16BillingConstants.SUBSCRIPTION_PRODUCT_ID,
    val basePlanId: String,
    val offerToken: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val billingPeriodIso: String
) {
    val isYearly: Boolean get() = basePlanId == Phase16BillingConstants.BASE_PLAN_YEARLY
    val isMonthly: Boolean get() = basePlanId == Phase16BillingConstants.BASE_PLAN_MONTHLY
}

data class YearlySavingsSummary(
    val annualSavingsMicros: Long,
    val formattedSavings: String,
    val savingsPercent: Int
)

/**
 * Queued purchase waiting for online server verification (e.g. purchased while offline or network dropped).
 */
data class PendingPurchaseVerification(
    val orgId: String,
    val purchaseToken: String,
    val productId: String = Phase16BillingConstants.SUBSCRIPTION_PRODUCT_ID,
    val basePlanId: String = "",
    val queuedAtMs: Long = 0L
)

/**
 * Snapshot of Google Play Billing state for the Owner subscription screen.
 */
data class PlayBillingUiSnapshot(
    val isConnected: Boolean = false,
    val isPlayBillingAvailable: Boolean = true,
    val isLoadingProducts: Boolean = false,
    val isVerifyingPurchase: Boolean = false,
    val isPendingPayment: Boolean = false,
    val monthlyOffer: PlaySubscriptionPlanOffer? = null,
    val yearlyOffer: PlaySubscriptionPlanOffer? = null,
    val hasQueuedOfflineVerification: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val yearlySavings: YearlySavingsSummary?
        get() = SubscriptionPricingEngine.computeYearlySavings(monthlyOffer, yearlyOffer)
}

/**
 * Pure domain calculations for Google Play dynamic prices, billing period labels,
 * yearly savings, and subscription date formatting.
 */
object SubscriptionPricingEngine {

    /**
     * Computes exact yearly saving purely from Google Play's dynamic monthly and yearly prices.
     * Returns null if either plan is missing, currencies differ, or yearly is not cheaper than 12× monthly.
     */
    fun computeYearlySavings(
        monthly: PlaySubscriptionPlanOffer?,
        yearly: PlaySubscriptionPlanOffer?,
        locale: Locale = Locale.getDefault()
    ): YearlySavingsSummary? {
        if (monthly == null || yearly == null) return null
        if (monthly.priceAmountMicros <= 0L || yearly.priceAmountMicros <= 0L) return null
        if (!monthly.priceCurrencyCode.equals(yearly.priceCurrencyCode, ignoreCase = true)) return null

        val twelveMonthsMicros = monthly.priceAmountMicros * 12L
        val savingsMicros = twelveMonthsMicros - yearly.priceAmountMicros
        if (savingsMicros <= 0L) return null

        val pct = ((savingsMicros.toDouble() / twelveMonthsMicros.toDouble()) * 100.0).roundToInt().coerceIn(1, 99)
        val formatted = formatCurrencyFromMicros(savingsMicros, yearly.priceCurrencyCode, locale)
        return YearlySavingsSummary(
            annualSavingsMicros = savingsMicros,
            formattedSavings = formatted,
            savingsPercent = pct
        )
    }

    fun formatCurrencyFromMicros(
        amountMicros: Long,
        currencyCode: String,
        locale: Locale = Locale.getDefault()
    ): String {
        val majorAmount = amountMicros / 1_000_000.0
        return try {
            val fmt = NumberFormat.getCurrencyInstance(locale)
            val currency = Currency.getInstance(currencyCode.uppercase(Locale.US))
            fmt.currency = currency
            if (amountMicros % 1_000_000L == 0L) {
                fmt.maximumFractionDigits = 0
                fmt.minimumFractionDigits = 0
            } else {
                fmt.maximumFractionDigits = 2
            }
            fmt.format(majorAmount)
        } catch (_: Throwable) {
            val rounded = if (amountMicros % 1_000_000L == 0L) "${amountMicros / 1_000_000L}" else String.format(Locale.US, "%.2f", majorAmount)
            "$currencyCode $rounded"
        }
    }

    /**
     * Converts ISO-8601 Play billing periods (`P1M`, `P1Y`, `P1W`, `P3M`, `P6M`) to clean Hindi/English labels.
     */
    fun formatBillingPeriodLabel(isoPeriod: String, isHindi: Boolean): String = when (isoPeriod.trim().uppercase(Locale.US)) {
        "P1M" -> if (isHindi) "प्रति माह" else "per month"
        "P1Y" -> if (isHindi) "प्रति वर्ष" else "per year"
        "P3M" -> if (isHindi) "प्रति 3 माह" else "per 3 months"
        "P6M" -> if (isHindi) "प्रति 6 माह" else "per 6 months"
        "P1W" -> if (isHindi) "प्रति सप्ताह" else "per week"
        else -> if (isHindi) "नवीकरणीय अवधि" else isoPeriod.ifBlank { "recurring" }
    }

    fun formatSubscriptionDate(epochMs: Long?, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (epochMs == null || epochMs <= 0L) return ""
        return try {
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
                .withZone(zoneId)
                .format(Instant.ofEpochMilli(epochMs))
        } catch (_: Throwable) {
            ""
        }
    }
}
