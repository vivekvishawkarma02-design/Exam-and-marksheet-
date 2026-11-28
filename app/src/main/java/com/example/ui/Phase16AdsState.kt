package com.example.ui

import com.example.ads.AdMobRequestFactory
import com.example.domain.AdEligibilityResult
import com.example.domain.AdScreenSurface
import com.example.domain.AdUnitIdsConfig
import com.example.domain.DebugSubscriptionSimulation
import com.example.domain.InterstitialFrequencyState
import com.example.domain.OrgSubscription
import com.example.domain.Phase16AdsPolicyEngine
import com.example.domain.RemoteAdsConfig
import com.example.domain.RewardedAdFreeState
import com.example.domain.RewardedOfferStatus

/**
 * UI-ready snapshot of Phase 16A ad eligibility, org subscription status, rewarded ad-free progress,
 * and debug-only simulation flags.
 */
data class Phase16AdsUiState(
    val orgSubscription: OrgSubscription = OrgSubscription.DEFAULT_FREE,
    val effectiveSubscription: OrgSubscription = OrgSubscription.DEFAULT_FREE,
    val rewardedState: RewardedAdFreeState = RewardedAdFreeState.EMPTY,
    val remoteConfig: RemoteAdsConfig = RemoteAdsConfig(),
    val unitIds: AdUnitIdsConfig = AdMobRequestFactory.currentBuildUnitIds(),
    val interstitialFrequency: InterstitialFrequencyState = InterstitialFrequencyState.EMPTY,
    val isOnline: Boolean = true,
    val firstInstallServerMs: Long = 0L,
    val serverNowMs: Long = 0L,
    val canRequestAdsFromConsent: Boolean = false,
    val isPrivacyOptionsRequired: Boolean = false,
    val isRewardedLoading: Boolean = false,
    val debugSimulation: DebugSubscriptionSimulation = DebugSubscriptionSimulation.FOLLOW_SERVER,
    val debugBypassInstallGrace: Boolean = false
) {
    fun bannerEligibility(surface: AdScreenSurface): AdEligibilityResult =
        Phase16AdsPolicyEngine.evaluateBanner(
            surface = surface,
            effectiveSubscription = effectiveSubscription,
            rewardedState = rewardedState,
            remoteConfig = remoteConfig,
            unitIds = unitIds,
            isOnline = isOnline,
            firstInstallServerMs = firstInstallServerMs,
            serverNowMs = serverNowMs,
            debugBypassInstallGrace = unitIds.isDebugBuild && debugBypassInstallGrace
        )

    fun pdfCloseInterstitialEligibility(pdfWasFinishedSuccessfully: Boolean): AdEligibilityResult =
        Phase16AdsPolicyEngine.evaluateInterstitialOnPdfClosed(
            surface = AdScreenSurface.PDF_PREVIEW_CLOSED,
            pdfWasFinishedSuccessfully = pdfWasFinishedSuccessfully,
            effectiveSubscription = effectiveSubscription,
            rewardedState = rewardedState,
            remoteConfig = remoteConfig,
            unitIds = unitIds,
            frequencyState = interstitialFrequency,
            isOnline = isOnline,
            firstInstallServerMs = firstInstallServerMs,
            serverNowMs = serverNowMs,
            debugBypassInstallGrace = unitIds.isDebugBuild && debugBypassInstallGrace
        )

    fun taskFinishedReturnInterstitialEligibility(): AdEligibilityResult =
        Phase16AdsPolicyEngine.evaluateInterstitialOnPdfClosed(
            surface = AdScreenSurface.TASK_FINISHED_RETURN,
            pdfWasFinishedSuccessfully = true,
            effectiveSubscription = effectiveSubscription,
            rewardedState = rewardedState,
            remoteConfig = remoteConfig,
            unitIds = unitIds,
            frequencyState = interstitialFrequency,
            isOnline = isOnline,
            firstInstallServerMs = firstInstallServerMs,
            serverNowMs = serverNowMs,
            debugBypassInstallGrace = unitIds.isDebugBuild && debugBypassInstallGrace
        )

    fun lapsedNoticeMarkerMs(): Long =
        effectiveSubscription.updatedAtMs
            ?: effectiveSubscription.expiresAtMs
            ?: 1L

    fun shouldShowLapsedNoticeToOwner(isOwner: Boolean, dismissedMarkerMs: Long): Boolean {
        if (!isOwner) return false
        if (!effectiveSubscription.isLapsed) return false
        return lapsedNoticeMarkerMs() > dismissedMarkerMs
    }

    val rewardedOfferStatus: RewardedOfferStatus
        get() = Phase16AdsPolicyEngine.evaluateRewardedOffer(
            effectiveSubscription = effectiveSubscription,
            rewardedState = rewardedState,
            remoteConfig = remoteConfig,
            unitIds = unitIds,
            isOnline = isOnline,
            serverNowMs = serverNowMs
        )
}

/**
 * Callback invoked strictly AFTER a user closes a finished PDF preview dialog.
 * No ad is ever shown while a PDF preview is open.
 */
val LocalOnFinishedPdfPreviewClosed = androidx.compose.runtime.staticCompositionLocalOf<(Boolean) -> Unit> { {} }
