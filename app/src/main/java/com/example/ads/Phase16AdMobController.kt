package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.example.BuildConfig
import com.example.domain.AdUnitIdsConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure factory for AdMob ad requests.
 * PRIVACY GUARANTEE: NEVER attaches any personal data, user identifiers, emails, phone numbers,
 * or organisation records to any AdRequest or RequestConfiguration.
 */
object AdMobRequestFactory {

    /**
     * Reads build-injected AdMob IDs.
     * Debug builds always use Google's official test IDs.
     * Release builds read GitHub Secrets; missing secrets produce an empty config (ads disabled, no crash).
     */
    fun currentBuildUnitIds(): AdUnitIdsConfig = AdUnitIdsConfig(
        appId = BuildConfig.ADMOB_APP_ID,
        bannerId = BuildConfig.ADMOB_BANNER_ID,
        interstitialId = BuildConfig.ADMOB_INTERSTITIAL_ID,
        rewardedId = BuildConfig.ADMOB_REWARDED_ID,
        isDebugBuild = BuildConfig.DEBUG
    )

    /**
     * Workplace and all-audiences safe RequestConfiguration:
     * - Tagged as NOT directed to children (adults/staff workplace app)
     * - Max content rating set to G (suitable for all audiences / workplace)
     */
    fun buildWorkplaceSafeRequestConfiguration(): RequestConfiguration =
        RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .build()

    /**
     * Builds a clean AdRequest with ZERO custom targeting, ZERO keywords, ZERO content URLs,
     * and ZERO personal or institutional data.
     */
    fun buildZeroPiiAdRequest(): AdRequest = AdRequest.Builder().build()
}

/**
 * Runtime controller for Google Mobile Ads SDK and Google UMP consent.
 * Completely defensive: never crashes or blocks the app if Play Services is missing,
 * the device is offline, ads are blocked, or release AdMob secrets are absent.
 */
object Phase16AdMobController {
    private const val TAG = "Phase16AdMob"

    private val sdkInitialized = AtomicBoolean(false)
    private val consentFlowStarted = AtomicBoolean(false)

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _isPrivacyOptionsRequired = MutableStateFlow(false)
    val isPrivacyOptionsRequired: StateFlow<Boolean> = _isPrivacyOptionsRequired.asStateFlow()

    private val _isRewardedLoading = MutableStateFlow(false)
    val isRewardedLoading: StateFlow<Boolean> = _isRewardedLoading.asStateFlow()

    @Volatile
    private var preloadedInterstitial: InterstitialAd? = null

    @Volatile
    private var isInterstitialLoading: Boolean = false

    @Volatile
    private var preloadedRewarded: RewardedAd? = null

    fun isPlayServicesAvailable(context: Context): Boolean = try {
        val status = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context.applicationContext)
        status == ConnectionResult.SUCCESS
    } catch (_: Throwable) {
        false
    }

    fun isAdsRuntimeReady(context: Context, unitIds: AdUnitIdsConfig = AdMobRequestFactory.currentBuildUnitIds()): Boolean =
        unitIds.isConfiguredForSdk && isPlayServicesAvailable(context)

    /**
     * Runs Google UMP consent check where required and initializes MobileAds safely.
     * Safe to invoke repeatedly; no-ops if ads are disabled, offline, or Play Services is unavailable.
     */
    fun gatherConsentAndInitializeIfAllowed(
        activity: Activity?,
        isOnline: Boolean,
        unitIds: AdUnitIdsConfig = AdMobRequestFactory.currentBuildUnitIds()
    ) {
        if (activity == null || !isOnline || !isAdsRuntimeReady(activity, unitIds)) {
            return
        }
        val appContext = activity.applicationContext
        try {
            val consentInfo = UserMessagingPlatform.getConsentInformation(appContext)
            _isPrivacyOptionsRequired.value =
                consentInfo.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

            if (consentInfo.canRequestAds()) {
                _canRequestAds.value = true
                initializeMobileAdsSdkOnce(appContext, unitIds)
            }

            if (!consentFlowStarted.compareAndSet(false, true)) {
                return
            }

            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()

            consentInfo.requestConsentInfoUpdate(
                activity,
                params,
                {
                    try {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            if (formError != null) {
                                Log.w(TAG, "UMP form error: ${formError.message}")
                            }
                            _isPrivacyOptionsRequired.value =
                                consentInfo.privacyOptionsRequirementStatus ==
                                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                            if (consentInfo.canRequestAds()) {
                                _canRequestAds.value = true
                                initializeMobileAdsSdkOnce(appContext, unitIds)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "UMP show form skipped safely: ${t.message}")
                    }
                },
                { requestError ->
                    Log.w(TAG, "UMP consent update failed safely: ${requestError.message}")
                    consentFlowStarted.set(false)
                    if (consentInfo.canRequestAds()) {
                        _canRequestAds.value = true
                        initializeMobileAdsSdkOnce(appContext, unitIds)
                    }
                }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Consent init skipped safely: ${t.message}")
            consentFlowStarted.set(false)
        }
    }

    private fun initializeMobileAdsSdkOnce(
        appContext: Context,
        unitIds: AdUnitIdsConfig
    ) {
        if (!unitIds.isConfiguredForSdk || !isPlayServicesAvailable(appContext)) return
        if (!sdkInitialized.compareAndSet(false, true)) return
        try {
            MobileAds.setRequestConfiguration(AdMobRequestFactory.buildWorkplaceSafeRequestConfiguration())
            MobileAds.initialize(appContext) {
                // Ready
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MobileAds.initialize skipped safely: ${t.message}")
            sdkInitialized.set(false)
        }
    }

    /**
     * Opens Google UMP privacy options form from Settings ("Privacy and ad choices").
     */
    fun showPrivacyOptionsForm(
        activity: Activity?,
        onCompleted: (shown: Boolean, message: String?) -> Unit
    ) {
        if (activity == null || !isPlayServicesAvailable(activity)) {
            onCompleted(false, null)
            return
        }
        try {
            val consentInfo = UserMessagingPlatform.getConsentInformation(activity.applicationContext)
            if (consentInfo.privacyOptionsRequirementStatus !=
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            ) {
                onCompleted(false, null)
                return
            }
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                _isPrivacyOptionsRequired.value =
                    consentInfo.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                _canRequestAds.value = consentInfo.canRequestAds()
                onCompleted(true, formError?.message)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Privacy options form unavailable: ${t.message}")
            onCompleted(false, null)
        }
    }

    /**
     * Computes an anchored adaptive banner AdSize for the given container width in dp.
     */
    fun adaptiveBannerSize(context: Context, widthDp: Int): AdSize {
        val safeWidth = widthDp.coerceAtLeast(300)
        return try {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, safeWidth)
        } catch (_: Throwable) {
            AdSize.BANNER
        }
    }

    /**
     * Non-blocking preload for optional PDF-close interstitial.
     */
    fun preloadInterstitialIfEligible(
        context: Context,
        eligible: Boolean,
        unitIds: AdUnitIdsConfig = AdMobRequestFactory.currentBuildUnitIds()
    ) {
        if (!eligible || !unitIds.hasInterstitialUnit || !_canRequestAds.value) return
        if (!isAdsRuntimeReady(context, unitIds)) return
        if (preloadedInterstitial != null || isInterstitialLoading) return

        initializeMobileAdsSdkOnce(context.applicationContext, unitIds)
        isInterstitialLoading = true
        try {
            InterstitialAd.load(
                context.applicationContext,
                unitIds.interstitialId,
                AdMobRequestFactory.buildZeroPiiAdRequest(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isInterstitialLoading = false
                        preloadedInterstitial = ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isInterstitialLoading = false
                        preloadedInterstitial = null
                        Log.d(TAG, "Interstitial failed to load (non-blocking): ${error.code}")
                    }
                }
            )
        } catch (t: Throwable) {
            isInterstitialLoading = false
            preloadedInterstitial = null
            Log.w(TAG, "Interstitial load skipped safely: ${t.message}")
        }
    }

    /**
     * Shows a preloaded interstitial ONLY if already ready (never blocks the user).
     * Invokes [onShown] once the ad actually opens so the frequency cap can be recorded.
     */
    fun showPreloadedInterstitialIfReady(
        activity: Activity?,
        onShown: () -> Unit
    ): Boolean {
        val ad = preloadedInterstitial ?: return false
        if (activity == null || activity.isFinishing || activity.isDestroyed) return false
        preloadedInterstitial = null
        return try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    onShown()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Interstitial failed to show: ${adError.code}")
                }
            }
            ad.show(activity)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Interstitial show failed safely: ${t.message}")
            false
        }
    }

    /**
     * Loads (if needed) and shows a Rewarded Ad.
     * STRICT REWARD RULE: [onRewardEarned] is invoked ONLY from AdMob's `OnUserEarnedRewardListener`.
     * Closing early calls [onClosedWithoutReward] and grants NOTHING.
     */
    fun showRewardedAd(
        activity: Activity?,
        isOnline: Boolean,
        unitIds: AdUnitIdsConfig = AdMobRequestFactory.currentBuildUnitIds(),
        opaqueSsvNonce: String? = null,
        onRewardEarned: () -> Unit,
        onClosedWithoutReward: () -> Unit,
        onUnavailable: (reason: String) -> Unit
    ) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onUnavailable("Activity unavailable")
            return
        }
        if (!isOnline) {
            onUnavailable("Offline")
            return
        }
        if (!unitIds.hasRewardedUnit || !isAdsRuntimeReady(activity, unitIds)) {
            onUnavailable("Ads unavailable")
            return
        }
        initializeMobileAdsSdkOnce(activity.applicationContext, unitIds)

        val readyAd = preloadedRewarded
        if (readyAd != null) {
            preloadedRewarded = null
            presentRewardedAd(activity, readyAd, opaqueSsvNonce, onRewardEarned, onClosedWithoutReward, onUnavailable)
            return
        }

        if (_isRewardedLoading.value) return
        _isRewardedLoading.value = true
        try {
            RewardedAd.load(
                activity.applicationContext,
                unitIds.rewardedId,
                AdMobRequestFactory.buildZeroPiiAdRequest(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        _isRewardedLoading.value = false
                        presentRewardedAd(activity, ad, opaqueSsvNonce, onRewardEarned, onClosedWithoutReward, onUnavailable)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        _isRewardedLoading.value = false
                        Log.d(TAG, "Rewarded ad failed to load: ${error.code}")
                        onUnavailable("No ad available right now")
                    }
                }
            )
        } catch (t: Throwable) {
            _isRewardedLoading.value = false
            Log.w(TAG, "Rewarded ad load exception: ${t.message}")
            onUnavailable("Ad service unavailable")
        }
    }

    private fun presentRewardedAd(
        activity: Activity,
        ad: RewardedAd,
        opaqueSsvNonce: String?,
        onRewardEarned: () -> Unit,
        onClosedWithoutReward: () -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        val earned = AtomicBoolean(false)
        try {
            // Attach ONLY a server-generated opaque random nonce (contains ZERO PII, UIDs, emails, or school info)
            if (!opaqueSsvNonce.isNullOrBlank()) {
                val ssvOptions = ServerSideVerificationOptions.Builder()
                    .setCustomData(opaqueSsvNonce.trim())
                    .build()
                ad.setServerSideVerificationOptions(ssvOptions)
            }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (!earned.get()) {
                        onClosedWithoutReward()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    onUnavailable("Could not display ad")
                }
            }
            ad.show(activity) { _ ->
                if (earned.compareAndSet(false, true)) {
                    onRewardEarned()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Rewarded ad show exception: ${t.message}")
            onUnavailable("Could not display ad")
        }
    }

    /** Clears cached full-screen ads when org switches to an active subscription or signs out. */
    fun clearPreloadedAds() {
        preloadedInterstitial = null
        preloadedRewarded = null
    }
}

tailrec fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
