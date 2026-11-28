package com.example.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.domain.Phase16BillingConstants
import com.example.domain.PlaySubscriptionPlanOffer
import java.util.concurrent.atomic.AtomicBoolean

data class PlayPurchaseSnapshot(
    val purchaseToken: String,
    val productId: String,
    val obfuscatedAccountId: String?,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val purchaseState: Int
)

interface Phase16PlayBillingCallbacks {
    fun onConnectionStateChanged(connected: Boolean, billingAvailable: Boolean)
    fun onOffersLoaded(
        monthly: PlaySubscriptionPlanOffer?,
        yearly: PlaySubscriptionPlanOffer?,
        errorMessage: String?
    )
    fun onPurchaseCompletedReadyForServer(purchase: PlayPurchaseSnapshot)
    fun onPurchasePending()
    fun onPurchaseCancelled()
    fun onPurchaseFlowError(message: String)
    fun onRestoredPurchasesFound(purchases: List<PlayPurchaseSnapshot>, errorMessage: String?)
}

/**
 * Phase 16B: Safe wrapper around Google Play Billing Library 7.x for the single
 * school subscription product (`school_ad_free`) with strictly two base plans (`monthly`, `yearly`).
 *
 * - Reads real localised prices & billing periods from Google Play (never hard-coded).
 * - Links every purchase to the organisation via `setObfuscatedAccountId(orgId)`.
 * - Defensively handles missing Google Play Services, offline devices, pending payments,
 *   cancellations, and already-owned restorations without ever crashing the app.
 */
class Phase16PlayBillingManager(
    context: Context,
    private val callbacks: Phase16PlayBillingCallbacks
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isConnecting = AtomicBoolean(false)
    private var billingClient: BillingClient? = null
    @Volatile
    private var cachedProductDetails: ProductDetails? = null

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun ensureClient(): BillingClient? {
        billingClient?.let { return it }
        return try {
            val pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
            BillingClient.newBuilder(appContext)
                .setListener(this)
                .enablePendingPurchases(pendingParams)
                .build()
                .also { billingClient = it }
        } catch (_: Throwable) {
            runOnMain { callbacks.onConnectionStateChanged(connected = false, billingAvailable = false) }
            null
        }
    }

    fun connectAndLoadProducts() {
        val client = ensureClient() ?: return
        if (client.isReady) {
            runOnMain { callbacks.onConnectionStateChanged(connected = true, billingAvailable = true) }
            querySubscriptionOffers(client)
            return
        }
        if (!isConnecting.compareAndSet(false, true)) return
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    isConnecting.set(false)
                    when (billingResult.responseCode) {
                        BillingClient.BillingResponseCode.OK -> {
                            runOnMain {
                                callbacks.onConnectionStateChanged(connected = true, billingAvailable = true)
                            }
                            querySubscriptionOffers(client)
                        }
                        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                            runOnMain {
                                callbacks.onConnectionStateChanged(connected = false, billingAvailable = false)
                                callbacks.onOffersLoaded(
                                    monthly = null,
                                    yearly = null,
                                    errorMessage = "Google Play Billing is not available on this device."
                                )
                            }
                        }
                        else -> {
                            runOnMain {
                                callbacks.onConnectionStateChanged(connected = false, billingAvailable = true)
                                callbacks.onOffersLoaded(
                                    monthly = null,
                                    yearly = null,
                                    errorMessage = mapBillingError(billingResult)
                                )
                            }
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    isConnecting.set(false)
                    runOnMain {
                        callbacks.onConnectionStateChanged(connected = false, billingAvailable = true)
                    }
                }
            })
        } catch (_: Throwable) {
            isConnecting.set(false)
            runOnMain {
                callbacks.onConnectionStateChanged(connected = false, billingAvailable = false)
            }
        }
    }

    private fun querySubscriptionOffers(client: BillingClient) {
        try {
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(Phase16BillingConstants.SUBSCRIPTION_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build()

            client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    runOnMain {
                        callbacks.onOffersLoaded(
                            monthly = null,
                            yearly = null,
                            errorMessage = mapBillingError(billingResult)
                        )
                    }
                    return@queryProductDetailsAsync
                }

                val subProduct = productDetailsList.firstOrNull {
                    it.productId == Phase16BillingConstants.SUBSCRIPTION_PRODUCT_ID
                }
                cachedProductDetails = subProduct

                if (subProduct == null) {
                    runOnMain {
                        callbacks.onOffersLoaded(
                            monthly = null,
                            yearly = null,
                            errorMessage = null
                        )
                    }
                    return@queryProductDetailsAsync
                }

                val offers = subProduct.subscriptionOfferDetails.orEmpty()
                val monthly = extractBasePlanOffer(subProduct, offers, Phase16BillingConstants.BASE_PLAN_MONTHLY)
                val yearly = extractBasePlanOffer(subProduct, offers, Phase16BillingConstants.BASE_PLAN_YEARLY)

                runOnMain {
                    callbacks.onOffersLoaded(
                        monthly = monthly,
                        yearly = yearly,
                        errorMessage = null
                    )
                }
            }
        } catch (_: Throwable) {
            runOnMain {
                callbacks.onOffersLoaded(
                    monthly = null,
                    yearly = null,
                    errorMessage = "Could not load plans from Google Play right now."
                )
            }
        }
    }

    private fun extractBasePlanOffer(
        productDetails: ProductDetails,
        offers: List<ProductDetails.SubscriptionOfferDetails>,
        basePlanId: String
    ): PlaySubscriptionPlanOffer? {
        val matching = offers.filter { it.basePlanId == basePlanId }
        // Prefer standard base-plan offer (offerId == null) if present, else first matching offer
        val selected = matching.firstOrNull { it.offerId == null } ?: matching.firstOrNull() ?: return null
        val phases = selected.pricingPhases.pricingPhaseList
        val recurringPhase = phases.lastOrNull { it.priceAmountMicros > 0L } ?: phases.lastOrNull() ?: return null
        return PlaySubscriptionPlanOffer(
            productId = productDetails.productId,
            basePlanId = basePlanId,
            offerToken = selected.offerToken,
            formattedPrice = recurringPhase.formattedPrice,
            priceAmountMicros = recurringPhase.priceAmountMicros,
            priceCurrencyCode = recurringPhase.priceCurrencyCode,
            billingPeriodIso = recurringPhase.billingPeriod
        )
    }

    /**
     * Launches Google Play Billing sheet for the selected base plan.
     * Caller MUST verify the user is the Organisation OWNER before invoking.
     * Sets `obfuscatedAccountId = orgId` so the server can bind the purchase strictly to this org.
     */
    fun launchSubscriptionPurchase(
        activity: Activity?,
        orgId: String,
        offer: PlaySubscriptionPlanOffer
    ): Boolean {
        if (activity == null || orgId.isBlank()) {
            runOnMain { callbacks.onPurchaseFlowError("Could not open Google Play purchase window.") }
            return false
        }
        val client = ensureClient()
        val details = cachedProductDetails
        if (client == null || !client.isReady || details == null) {
            connectAndLoadProducts()
            runOnMain {
                callbacks.onPurchaseFlowError("Connecting to Google Play. Please try again in a moment.")
            }
            return false
        }

        return try {
            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offer.offerToken)
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .setObfuscatedAccountId(orgId.take(64))
                .build()
            val result = client.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.USER_CANCELED ->
                        runOnMain { callbacks.onPurchaseCancelled() }
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                        queryActivePurchasesForRestore()
                    else ->
                        runOnMain { callbacks.onPurchaseFlowError(mapBillingError(result)) }
                }
                false
            } else {
                true
            }
        } catch (_: Throwable) {
            runOnMain { callbacks.onPurchaseFlowError("Google Play purchase could not be started.") }
            false
        }
    }

    fun queryActivePurchasesForRestore() {
        val client = ensureClient()
        if (client == null) {
            runOnMain {
                callbacks.onRestoredPurchasesFound(
                    emptyList(),
                    "Google Play Billing is not available on this device."
                )
            }
            return
        }
        if (!client.isReady) {
            connectAndLoadProducts()
            runOnMain {
                callbacks.onRestoredPurchasesFound(
                    emptyList(),
                    "Connecting to Google Play. Please tap Restore again in a moment."
                )
            }
            return
        }
        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            client.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    runOnMain {
                        callbacks.onRestoredPurchasesFound(emptyList(), mapBillingError(billingResult))
                    }
                    return@queryPurchasesAsync
                }
                val matching = purchases
                    .filter { it.products.contains(Phase16BillingConstants.SUBSCRIPTION_PRODUCT_ID) }
                    .map { it.toSnapshot() }
                runOnMain {
                    callbacks.onRestoredPurchasesFound(matching, null)
                }
            }
        } catch (_: Throwable) {
            runOnMain {
                callbacks.onRestoredPurchasesFound(emptyList(), "Could not check Google Play purchases.")
            }
        }
    }

    /**
     * Client-side acknowledgement backup invoked ONLY AFTER the server verifies the purchase.
     * The Cloud Function also acknowledges purchases directly via Google Play Developer API.
     */
    fun acknowledgePurchaseIfNeeded(purchaseToken: String) {
        val client = billingClient ?: return
        if (!client.isReady || purchaseToken.isBlank()) return
        try {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
            client.acknowledgePurchase(params) { _ ->
                // Best-effort backup; server-side acknowledgement is authoritative.
            }
        } catch (_: Throwable) {
            // Never crash on backup acknowledge
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val matching = purchases.orEmpty().filter {
                    it.products.contains(Phase16BillingConstants.SUBSCRIPTION_PRODUCT_ID)
                }
                matching.forEach { purchase ->
                    when (purchase.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> {
                            runOnMain {
                                callbacks.onPurchaseCompletedReadyForServer(purchase.toSnapshot())
                            }
                        }
                        Purchase.PurchaseState.PENDING -> {
                            runOnMain { callbacks.onPurchasePending() }
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                runOnMain { callbacks.onPurchaseCancelled() }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryActivePurchasesForRestore()
            }
            else -> {
                runOnMain { callbacks.onPurchaseFlowError(mapBillingError(billingResult)) }
            }
        }
    }

    fun destroy() {
        try {
            billingClient?.endConnection()
        } catch (_: Throwable) {
        }
        billingClient = null
        cachedProductDetails = null
    }

    private fun Purchase.toSnapshot(): PlayPurchaseSnapshot = PlayPurchaseSnapshot(
        purchaseToken = purchaseToken,
        productId = products.firstOrNull { it == Phase16BillingConstants.SUBSCRIPTION_PRODUCT_ID }
            ?: Phase16BillingConstants.SUBSCRIPTION_PRODUCT_ID,
        obfuscatedAccountId = accountIdentifiers?.obfuscatedAccountId,
        isAcknowledged = isAcknowledged,
        isAutoRenewing = isAutoRenewing,
        purchaseState = purchaseState
    )

    private fun mapBillingError(result: BillingResult): String = when (result.responseCode) {
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingClient.BillingResponseCode.NETWORK_ERROR ->
            "No internet connection or Google Play is temporarily unavailable."
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
            "Google Play Billing is not available on this device or account."
        BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
            "Subscription plan is not yet active in Google Play Console."
        BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
            "Google Play setup is incomplete. Check product and track setup in Play Console."
        else ->
            result.debugMessage.takeIf { it.isNotBlank() }
                ?: "Could not complete Google Play request (code ${result.responseCode})."
    }

    companion object {
        fun openPlayManageSubscriptions(context: Context): Boolean {
            return try {
                val uri = Uri.parse(Phase16BillingConstants.manageSubscriptionUrl(context.packageName))
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
