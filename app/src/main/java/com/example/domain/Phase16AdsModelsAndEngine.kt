package com.example.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 16A: Organisation-level subscription status controlling all ad surfaces.
 * Read-only on the client (Firestore rules deny every client write; only Phase 16B server writes it).
 */
enum class OrgSubscriptionStatus(val wireValue: String) {
    NONE("none"),
    ACTIVE("active"),
    GRACE("grace"),
    ON_HOLD("on_hold"),
    CANCELLED_ACTIVE("cancelled_active"),
    EXPIRED("expired"),
    REVOKED("revoked");

    /**
     * When true, ZERO ads of any kind and ZERO rewarded offers may be shown to any member.
     * Grace period, account hold, and cancelled-but-still-valid MUST NOT show ads wrongly.
     */
    val suppressesAllAds: Boolean
        get() = this == ACTIVE || this == GRACE || this == ON_HOLD || this == CANCELLED_ACTIVE

    val isLapsedOrRevoked: Boolean
        get() = this == EXPIRED || this == REVOKED

    companion object {
        fun fromWire(raw: String?): OrgSubscriptionStatus = when (raw?.trim()?.lowercase()) {
            "active" -> ACTIVE
            "grace" -> GRACE
            "on_hold" -> ON_HOLD
            "cancelled_active", "canceled_active" -> CANCELLED_ACTIVE
            "expired" -> EXPIRED
            "revoked" -> REVOKED
            else -> NONE
        }
    }
}

data class OrgSubscription(
    val status: OrgSubscriptionStatus = OrgSubscriptionStatus.NONE,
    val plan: String = "",
    val expiresAtMs: Long? = null,
    val autoRenewing: Boolean = false,
    val updatedAtMs: Long? = null,
    val purchaseTokenHash: String = ""
) {
    val suppressesAllAds: Boolean get() = status.suppressesAllAds

    val isLapsed: Boolean get() = status.isLapsedOrRevoked

    /**
     * Evaluates ad-free entitlement at [serverNowMs].
     * - ACTIVE, GRACE, ON_HOLD: always ad-free while server reports these states.
     * - CANCELLED_ACTIVE: ad-free strictly while [expiresAtMs] is still in the future.
     * - NONE, EXPIRED, REVOKED: not ad-free (ads show per organisation policy).
     */
    fun isEntitledAt(serverNowMs: Long): Boolean = when (status) {
        OrgSubscriptionStatus.ACTIVE,
        OrgSubscriptionStatus.GRACE,
        OrgSubscriptionStatus.ON_HOLD -> true
        OrgSubscriptionStatus.CANCELLED_ACTIVE -> {
            val exp = expiresAtMs
            exp == null || serverNowMs <= 0L || serverNowMs < exp
        }
        OrgSubscriptionStatus.NONE,
        OrgSubscriptionStatus.EXPIRED,
        OrgSubscriptionStatus.REVOKED -> false
    }

    companion object {
        val DEFAULT_FREE = OrgSubscription(status = OrgSubscriptionStatus.NONE)
    }
}

/**
 * Debug-only local simulation mode (strictly ignored in release builds).
 */
enum class DebugSubscriptionSimulation(val wireValue: String) {
    FOLLOW_SERVER("follow_server"),
    FORCE_ACTIVE("force_active"),
    FORCE_FREE("force_free");

    companion object {
        fun fromWire(raw: String?): DebugSubscriptionSimulation = when (raw?.trim()?.lowercase()) {
            "force_active" -> FORCE_ACTIVE
            "force_free" -> FORCE_FREE
            else -> FOLLOW_SERVER
        }
    }
}

/**
 * Remote kill-switch / per-placement configuration read from Firestore `appConfig/adsConfig`.
 * Safe defaults keep the app functional even if the config document does not exist yet.
 */
data class RemoteAdsConfig(
    val globalEnabled: Boolean = true,
    val todayBannerEnabled: Boolean = true,
    val orgChooserBannerEnabled: Boolean = true,
    val schoolChooserBannerEnabled: Boolean = true,
    val studentListBannerEnabled: Boolean = true,
    val teacherListBannerEnabled: Boolean = true,
    val reportListBannerEnabled: Boolean = true,
    val timetableViewBannerEnabled: Boolean = true,
    val pdfCloseInterstitialEnabled: Boolean = true,
    val rewardedAdFreeEnabled: Boolean = true,
    val requireServerVerifiedRewards: Boolean = false
)

/**
 * Strict allowlist/denylist of screens for ad placements.
 * Banners: Today home, Org chooser, School chooser, Student list, Teacher list, Report list, Timetable view ONLY.
 * Interstitials: ONLY after a finished task (closing a finished PDF preview or returning from a list).
 * Sensitive school data-entry screens are NEVER allowed to render banners or interstitials.
 */
enum class AdScreenSurface(
    val allowsBanner: Boolean,
    val allowsPdfCloseInterstitial: Boolean
) {
    // Allowed banner surfaces (strictly ONE banner per screen, never two)
    TODAY_HOME(allowsBanner = true, allowsPdfCloseInterstitial = false),
    ORG_CHOOSER(allowsBanner = true, allowsPdfCloseInterstitial = false),
    SCHOOL_CHOOSER(allowsBanner = true, allowsPdfCloseInterstitial = false),
    STUDENT_LIST(allowsBanner = true, allowsPdfCloseInterstitial = false),
    TEACHER_LIST(allowsBanner = true, allowsPdfCloseInterstitial = false),
    REPORT_LIST(allowsBanner = true, allowsPdfCloseInterstitial = false),
    TIMETABLE_VIEW(allowsBanner = true, allowsPdfCloseInterstitial = false),

    // Allowed task-finished interstitial triggers (never mid-task)
    PDF_PREVIEW_CLOSED(allowsBanner = false, allowsPdfCloseInterstitial = true),
    TASK_FINISHED_RETURN(allowsBanner = false, allowsPdfCloseInterstitial = true),

    // Strictly forbidden surfaces (data entry, sensitive modules, settings, open PDF, auth)
    LOGIN_SIGNUP(allowsBanner = false, allowsPdfCloseInterstitial = false),
    EMAIL_VERIFICATION(allowsBanner = false, allowsPdfCloseInterstitial = false),
    CREATE_ORG(allowsBanner = false, allowsPdfCloseInterstitial = false),
    JOIN_ORG(allowsBanner = false, allowsPdfCloseInterstitial = false),
    STUDENTS(allowsBanner = false, allowsPdfCloseInterstitial = false),
    STUDENT_REGISTER(allowsBanner = false, allowsPdfCloseInterstitial = false),
    FEES(allowsBanner = false, allowsPdfCloseInterstitial = false),
    SALARY(allowsBanner = false, allowsPdfCloseInterstitial = false),
    CASH_BOOK(allowsBanner = false, allowsPdfCloseInterstitial = false),
    MARKS(allowsBanner = false, allowsPdfCloseInterstitial = false),
    ATTENDANCE(allowsBanner = false, allowsPdfCloseInterstitial = false),
    TRANSPORT(allowsBanner = false, allowsPdfCloseInterstitial = false),
    RECEIPTS(allowsBanner = false, allowsPdfCloseInterstitial = false),
    CERTIFICATES(allowsBanner = false, allowsPdfCloseInterstitial = false),
    PDF_PREVIEW_OPEN(allowsBanner = false, allowsPdfCloseInterstitial = false),
    SETTINGS(allowsBanner = false, allowsPdfCloseInterstitial = false),
    SUBSCRIPTION(allowsBanner = false, allowsPdfCloseInterstitial = false),
    ACCOUNT_DELETION(allowsBanner = false, allowsPdfCloseInterstitial = false),
    REPORTS(allowsBanner = false, allowsPdfCloseInterstitial = false),
    TIMETABLE(allowsBanner = false, allowsPdfCloseInterstitial = false),
    MEMBERS(allowsBanner = false, allowsPdfCloseInterstitial = false),
    EXAMS(allowsBanner = false, allowsPdfCloseInterstitial = false),
    ENQUIRY(allowsBanner = false, allowsPdfCloseInterstitial = false),
    STOCK(allowsBanner = false, allowsPdfCloseInterstitial = false),
    OTHER(allowsBanner = false, allowsPdfCloseInterstitial = false)
}

/**
 * Build-injected AdMob IDs. Debug builds use ONLY Google's official test IDs.
 * Release builds read GitHub Secrets; if missing/blank, ads are cleanly disabled without crashing.
 */
data class AdUnitIdsConfig(
    val appId: String,
    val bannerId: String,
    val interstitialId: String,
    val rewardedId: String,
    val isDebugBuild: Boolean
) {
    val hasValidAppId: Boolean get() = isValidAdMobAppId(appId)
    val hasBannerUnit: Boolean get() = hasValidAppId && isValidAdUnitId(bannerId)
    val hasInterstitialUnit: Boolean get() = hasValidAppId && isValidAdUnitId(interstitialId)
    val hasRewardedUnit: Boolean get() = hasValidAppId && isValidAdUnitId(rewardedId)

    /** True only if SDK initialisation is safe and at least one placement unit ID is present. */
    val isConfiguredForSdk: Boolean
        get() = hasValidAppId && (hasBannerUnit || hasInterstitialUnit || hasRewardedUnit)

    companion object {
        const val OFFICIAL_TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        const val OFFICIAL_TEST_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"
        const val OFFICIAL_TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        const val OFFICIAL_TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

        private val APP_ID_REGEX = Regex("^ca-app-pub-\\d{16}~\\d{10}$")
        private val UNIT_ID_REGEX = Regex("^ca-app-pub-\\d{16}/\\d{10}$")
        private const val INERT_DUMMY_PREFIX = "ca-app-pub-0000000000000000"

        fun isValidAdMobAppId(raw: String?): Boolean {
            val v = raw?.trim().orEmpty()
            if (v.isEmpty() || v.startsWith(INERT_DUMMY_PREFIX)) return false
            return APP_ID_REGEX.matches(v)
        }

        fun isValidAdUnitId(raw: String?): Boolean {
            val v = raw?.trim().orEmpty()
            if (v.isEmpty() || v.startsWith(INERT_DUMMY_PREFIX)) return false
            return UNIT_ID_REGEX.matches(v)
        }

        val DISABLED = AdUnitIdsConfig(
            appId = "",
            bannerId = "",
            interstitialId = "",
            rewardedId = "",
            isDebugBuild = false
        )

        val OFFICIAL_TEST = AdUnitIdsConfig(
            appId = OFFICIAL_TEST_APP_ID,
            bannerId = OFFICIAL_TEST_BANNER_ID,
            interstitialId = OFFICIAL_TEST_INTERSTITIAL_ID,
            rewardedId = OFFICIAL_TEST_REWARDED_ID,
            isDebugBuild = true
        )
    }
}

/**
 * Window tracking for rewarded ad-free time per user inside an organisation.
 * Stored on `organisations/{orgId}/members/{uid}.adsWatchedWindow` with Firestore rules bounds.
 */
data class RewardedAdsWindow(
    val windowStartAtMs: Long = 0L,
    val countInWindow: Int = 0,
    val dailyDateKey: String = "",
    val dailyCount: Int = 0,
    val totalCount: Long = 0L,
    val lastRewardedAtMs: Long = 0L
)

data class RewardedAdFreeState(
    val adFreeUntilMs: Long = 0L,
    val window: RewardedAdsWindow = RewardedAdsWindow()
) {
    fun isAdFreeAt(serverNowMs: Long): Boolean = adFreeUntilMs > serverNowMs

    fun remainingAdFreeMs(serverNowMs: Long): Long = max(0L, adFreeUntilMs - serverNowMs)

    companion object {
        val EMPTY = RewardedAdFreeState()
    }
}

data class RewardedOfferStatus(
    val visible: Boolean,
    val canWatchNow: Boolean,
    val completedInWindow: Int,
    val maxInWindow: Int,
    val dailyCount: Int,
    val maxDailyCount: Int,
    val isAdFreeActive: Boolean,
    val remainingAdFreeMs: Long,
    val nextRewardTotalHours: Int?,
    val blockReason: RewardedOfferBlockReason? = null
)

enum class RewardedOfferBlockReason {
    SUBSCRIPTION_ACTIVE,
    OFFLINE,
    ADS_DISABLED_OR_MISSING_ID,
    DAILY_CAP_REACHED,
    LADDER_COMPLETE_WAIT_FOR_EXPIRY
}

/**
 * Pure domain ladder calculator for rewarded ad-free time.
 *
 * Ladder over active 24h window (n = rewarded ads completed):
 *  - 1 ad  -> ad-free until now + 2 hours
 *  - 2 ads -> ad-free until firstAd + 3 hours in total
 *  - 3 ads -> ad-free until firstAd + 6 hours in total
 *  - 4 ads -> ad-free until firstAd + 24 hours in total
 *
 * After 4 ads, no more rewarded offers are shown until ad-free time ends.
 * Maximum 8 rewarded ads per user per UTC day.
 * Only completed rewarded ads advance state; closing early must NOT call [recordCompletedReward].
 */
object RewardedLadderEngine {
    const val HOUR_MS: Long = 3_600_000L
    const val WINDOW_DURATION_MS: Long = 24L * HOUR_MS
    const val MAX_ADS_PER_LADDER: Int = 4
    const val MAX_ADS_PER_DAY: Int = 8

    private val UTC_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun utcDateKey(serverNowMs: Long): String =
        Instant.ofEpochMilli(serverNowMs.coerceAtLeast(0L))
            .atOffset(ZoneOffset.UTC)
            .toLocalDate()
            .format(UTC_DATE_FORMAT)

    fun ladderTotalHoursForStep(step: Int): Int = when (step) {
        1 -> 2
        2 -> 3
        3 -> 6
        else -> 24
    }

    /**
     * Effective count shown in the "X of 4 ads" progress indicator at [serverNowMs].
     */
    fun activeWindowCount(state: RewardedAdFreeState, serverNowMs: Long): Int {
        val w = state.window
        if (w.windowStartAtMs <= 0L || w.countInWindow <= 0) return 0
        val within24h = (serverNowMs - w.windowStartAtMs) in 0L until WINDOW_DURATION_MS
        if (!within24h) return 0
        // If user finished 4 ads and ad-free time has already ended, ladder resets for the next watch
        if (w.countInWindow >= MAX_ADS_PER_LADDER && serverNowMs >= state.adFreeUntilMs) return 0
        // If previous partial window's next step target is already in the past and ad-free ended, display 0
        if (w.countInWindow < MAX_ADS_PER_LADDER && serverNowMs >= state.adFreeUntilMs) {
            val nextTarget = w.windowStartAtMs + ladderTotalHoursForStep(w.countInWindow + 1) * HOUR_MS
            if (nextTarget <= serverNowMs) return 0
        }
        return w.countInWindow.coerceIn(0, MAX_ADS_PER_LADDER)
    }

    fun effectiveDailyCount(state: RewardedAdFreeState, serverNowMs: Long): Int {
        val today = utcDateKey(serverNowMs)
        return if (state.window.dailyDateKey == today) {
            state.window.dailyCount.coerceAtLeast(0)
        } else {
            0
        }
    }

    fun canWatchAnotherRewardedAd(state: RewardedAdFreeState, serverNowMs: Long): Boolean {
        if (effectiveDailyCount(state, serverNowMs) >= MAX_ADS_PER_DAY) return false
        val completed = activeWindowCount(state, serverNowMs)
        if (completed >= MAX_ADS_PER_LADDER && state.isAdFreeAt(serverNowMs)) return false
        return true
    }

    fun nextStepTotalHours(state: RewardedAdFreeState, serverNowMs: Long): Int? {
        if (!canWatchAnotherRewardedAd(state, serverNowMs)) return null
        val completed = activeWindowCount(state, serverNowMs)
        return ladderTotalHoursForStep((completed + 1).coerceIn(1, MAX_ADS_PER_LADDER))
    }

    /**
     * Records ONE genuinely completed rewarded ad at [serverNowMs].
     * Returns null if the user has already hit the 8/day cap or completed 4 ads while ad-free is active.
     * Guarantees:
     *  - `adFreeUntilMs >= current.adFreeUntilMs` (monotonic extension)
     *  - `adFreeUntilMs <= serverNowMs + 24h` (matches Firestore rules upper bound)
     *  - `window.totalCount == current.window.totalCount + 1` (strictly monotonic)
     */
    fun recordCompletedReward(
        current: RewardedAdFreeState,
        serverNowMs: Long
    ): RewardedAdFreeState? {
        if (serverNowMs <= 0L) return null
        val todayKey = utcDateKey(serverNowMs)
        val currentDaily = if (current.window.dailyDateKey == todayKey) {
            current.window.dailyCount.coerceAtLeast(0)
        } else {
            0
        }
        if (currentDaily >= MAX_ADS_PER_DAY) return null

        val completedInActiveWindow = activeWindowCount(current, serverNowMs)
        if (completedInActiveWindow >= MAX_ADS_PER_LADDER && current.isAdFreeAt(serverNowMs)) {
            return null
        }

        val maxAllowedUntil = serverNowMs + WINDOW_DURATION_MS
        val newWindowStart: Long
        val newCountInWindow: Int
        val candidateUntil: Long

        if (completedInActiveWindow == 0) {
            newWindowStart = serverNowMs
            newCountInWindow = 1
            candidateUntil = serverNowMs + ladderTotalHoursForStep(1) * HOUR_MS
        } else {
            newWindowStart = current.window.windowStartAtMs
            newCountInWindow = (completedInActiveWindow + 1).coerceAtMost(MAX_ADS_PER_LADDER)
            candidateUntil = newWindowStart + ladderTotalHoursForStep(newCountInWindow) * HOUR_MS
        }

        // Enforce monotonic extension and strict <= serverNowMs + 24h bound
        val boundedUntil = min(max(candidateUntil, current.adFreeUntilMs), maxAllowedUntil)
        val safeUntil = max(boundedUntil, current.adFreeUntilMs)

        return RewardedAdFreeState(
            adFreeUntilMs = safeUntil,
            window = RewardedAdsWindow(
                windowStartAtMs = newWindowStart,
                countInWindow = newCountInWindow,
                dailyDateKey = todayKey,
                dailyCount = currentDaily + 1,
                totalCount = current.window.totalCount.coerceAtLeast(0L) + 1L,
                lastRewardedAtMs = serverNowMs
            )
        )
    }

    /**
     * Merges local cached state and cloud snapshot conservatively (keeps latest monotonic progress).
     */
    fun mergeMonotonic(local: RewardedAdFreeState, remote: RewardedAdFreeState): RewardedAdFreeState {
        return if (remote.window.totalCount > local.window.totalCount ||
            remote.adFreeUntilMs > local.adFreeUntilMs
        ) {
            remote.copy(adFreeUntilMs = max(local.adFreeUntilMs, remote.adFreeUntilMs))
        } else {
            local
        }
    }

    fun formatRemainingTime(remainingMs: Long, isHindi: Boolean): String {
        val totalMinutes = ((remainingMs.coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()
        if (totalMinutes <= 0) {
            return if (isHindi) "0 मिनट" else "0 min"
        }
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 ->
                if (isHindi) "${hours} घंटे ${mins} मिनट" else "${hours}h ${mins}m"
            hours > 0 ->
                if (isHindi) "${hours} घंटे" else "${hours}h"
            else ->
                if (isHindi) "${mins} मिनट" else "${mins}m"
        }
    }
}

/**
 * Per-device interstitial frequency cap state:
 *  - At most once per 15 minutes
 *  - At most 3 per UTC day per device
 */
data class InterstitialFrequencyState(
    val lastShownServerMs: Long = 0L,
    val dailyDateKey: String = "",
    val dailyShownCount: Int = 0
) {
    fun effectiveDailyCount(serverNowMs: Long): Int {
        val today = RewardedLadderEngine.utcDateKey(serverNowMs)
        return if (dailyDateKey == today) dailyShownCount.coerceAtLeast(0) else 0
    }

    fun canShowAt(serverNowMs: Long): Boolean {
        if (serverNowMs <= 0L) return false
        if (effectiveDailyCount(serverNowMs) >= MAX_PER_DAY) return false
        if (lastShownServerMs > 0L) {
            val elapsed = serverNowMs - lastShownServerMs
            if (elapsed < MIN_INTERVAL_MS) return false
        }
        return true
    }

    fun recordShownAt(serverNowMs: Long): InterstitialFrequencyState {
        val today = RewardedLadderEngine.utcDateKey(serverNowMs)
        val count = if (dailyDateKey == today) dailyShownCount.coerceAtLeast(0) + 1 else 1
        return InterstitialFrequencyState(
            lastShownServerMs = serverNowMs,
            dailyDateKey = today,
            dailyShownCount = count
        )
    }

    companion object {
        const val MIN_INTERVAL_MS: Long = 8L * 60_000L
        const val MAX_PER_DAY: Int = 8
        val EMPTY = InterstitialFrequencyState()
    }
}

enum class AdSuppressionReason {
    ALLOWED,
    SURFACE_FORBIDDEN,
    SUBSCRIPTION_ACTIVE,
    AD_FREE_REWARD_ACTIVE,
    OFFLINE,
    FIRST_24H_INSTALL_GRACE,
    REMOTE_DISABLED,
    MISSING_OR_INVALID_AD_IDS,
    INTERSTITIAL_FREQUENCY_CAP,
    PDF_NOT_FINISHED
}

data class AdEligibilityResult(
    val allowed: Boolean,
    val reason: AdSuppressionReason
)

/**
 * Central, pure decision engine for Phase 16A ad eligibility, server-time calculation,
 * subscription suppression, first-24h install grace, offline collapse, and rewarded offers.
 */
object Phase16AdsPolicyEngine {
    const val FIRST_INSTALL_GRACE_MS: Long = 24L * 3_600_000L

    /** Computes server-adjusted current timestamp using last known Firestore server offset. */
    fun serverNowMs(phoneNowMs: Long, serverOffsetMs: Long): Long =
        (phoneNowMs + serverOffsetMs).coerceAtLeast(0L)

    /**
     * Resolves effective subscription status. Debug simulation ONLY works when [isDebugBuild] is true.
     */
    fun resolveEffectiveSubscription(
        orgSubscription: OrgSubscription,
        isDebugBuild: Boolean,
        debugSimulation: DebugSubscriptionSimulation
    ): OrgSubscription {
        if (!isDebugBuild) return orgSubscription
        return when (debugSimulation) {
            DebugSubscriptionSimulation.FOLLOW_SERVER -> orgSubscription
            DebugSubscriptionSimulation.FORCE_ACTIVE -> OrgSubscription(
                status = OrgSubscriptionStatus.ACTIVE,
                plan = "debug_simulated"
            )
            DebugSubscriptionSimulation.FORCE_FREE -> OrgSubscription(
                status = OrgSubscriptionStatus.NONE,
                plan = ""
            )
        }
    }

    /**
     * Evaluates whether a banner ad may appear on [surface].
     * When [AdEligibilityResult.allowed] is false, the UI MUST collapse the banner space to 0.dp.
     */
    fun evaluateBanner(
        surface: AdScreenSurface,
        effectiveSubscription: OrgSubscription,
        rewardedState: RewardedAdFreeState,
        remoteConfig: RemoteAdsConfig,
        unitIds: AdUnitIdsConfig,
        isOnline: Boolean,
        firstInstallServerMs: Long,
        serverNowMs: Long,
        debugBypassInstallGrace: Boolean = false
    ): AdEligibilityResult {
        if (!surface.allowsBanner) {
            return AdEligibilityResult(false, AdSuppressionReason.SURFACE_FORBIDDEN)
        }
        if (effectiveSubscription.isEntitledAt(serverNowMs)) {
            return AdEligibilityResult(false, AdSuppressionReason.SUBSCRIPTION_ACTIVE)
        }
        if (!unitIds.hasBannerUnit) {
            return AdEligibilityResult(false, AdSuppressionReason.MISSING_OR_INVALID_AD_IDS)
        }
        if (!remoteConfig.globalEnabled || !isBannerRemoteEnabled(surface, remoteConfig)) {
            return AdEligibilityResult(false, AdSuppressionReason.REMOTE_DISABLED)
        }
        if (!isOnline) {
            return AdEligibilityResult(false, AdSuppressionReason.OFFLINE)
        }
        if (!debugBypassInstallGrace && isWithinFirst24hGrace(firstInstallServerMs, serverNowMs)) {
            return AdEligibilityResult(false, AdSuppressionReason.FIRST_24H_INSTALL_GRACE)
        }
        if (rewardedState.isAdFreeAt(serverNowMs)) {
            return AdEligibilityResult(false, AdSuppressionReason.AD_FREE_REWARD_ACTIVE)
        }
        return AdEligibilityResult(true, AdSuppressionReason.ALLOWED)
    }

    /**
     * Evaluates whether an optional interstitial may be shown right after closing a finished PDF preview
     * or returning from a list (never in the middle of a task).
     * Equal rules apply to EVERY member role — controlled solely by organisation subscription status.
     */
    fun evaluateInterstitialOnPdfClosed(
        surface: AdScreenSurface,
        pdfWasFinishedSuccessfully: Boolean,
        effectiveSubscription: OrgSubscription,
        rewardedState: RewardedAdFreeState,
        remoteConfig: RemoteAdsConfig,
        unitIds: AdUnitIdsConfig,
        frequencyState: InterstitialFrequencyState,
        isOnline: Boolean,
        firstInstallServerMs: Long,
        serverNowMs: Long,
        debugBypassInstallGrace: Boolean = false
    ): AdEligibilityResult {
        if (!surface.allowsPdfCloseInterstitial) {
            return AdEligibilityResult(false, AdSuppressionReason.SURFACE_FORBIDDEN)
        }
        if (!pdfWasFinishedSuccessfully) {
            return AdEligibilityResult(false, AdSuppressionReason.PDF_NOT_FINISHED)
        }
        if (effectiveSubscription.isEntitledAt(serverNowMs)) {
            return AdEligibilityResult(false, AdSuppressionReason.SUBSCRIPTION_ACTIVE)
        }
        if (!unitIds.hasInterstitialUnit) {
            return AdEligibilityResult(false, AdSuppressionReason.MISSING_OR_INVALID_AD_IDS)
        }
        if (!remoteConfig.globalEnabled || !remoteConfig.pdfCloseInterstitialEnabled) {
            return AdEligibilityResult(false, AdSuppressionReason.REMOTE_DISABLED)
        }
        if (!isOnline) {
            return AdEligibilityResult(false, AdSuppressionReason.OFFLINE)
        }
        if (!debugBypassInstallGrace && isWithinFirst24hGrace(firstInstallServerMs, serverNowMs)) {
            return AdEligibilityResult(false, AdSuppressionReason.FIRST_24H_INSTALL_GRACE)
        }
        if (rewardedState.isAdFreeAt(serverNowMs)) {
            return AdEligibilityResult(false, AdSuppressionReason.AD_FREE_REWARD_ACTIVE)
        }
        if (!frequencyState.canShowAt(serverNowMs)) {
            return AdEligibilityResult(false, AdSuppressionReason.INTERSTITIAL_FREQUENCY_CAP)
        }
        return AdEligibilityResult(true, AdSuppressionReason.ALLOWED)
    }

    /**
     * Evaluates the optional "Watch ads to remove ads for a while" card and button state.
     * For paid orgs (active/grace/on_hold/cancelled_active), visible is strictly FALSE (no rewarded offer or watch button).
     */
    fun evaluateRewardedOffer(
        effectiveSubscription: OrgSubscription,
        rewardedState: RewardedAdFreeState,
        remoteConfig: RemoteAdsConfig,
        unitIds: AdUnitIdsConfig,
        isOnline: Boolean,
        serverNowMs: Long
    ): RewardedOfferStatus {
        val completed = RewardedLadderEngine.activeWindowCount(rewardedState, serverNowMs)
        val daily = RewardedLadderEngine.effectiveDailyCount(rewardedState, serverNowMs)
        val isAdFree = rewardedState.isAdFreeAt(serverNowMs)
        val remaining = rewardedState.remainingAdFreeMs(serverNowMs)

        if (effectiveSubscription.isEntitledAt(serverNowMs)) {
            return RewardedOfferStatus(
                visible = false,
                canWatchNow = false,
                completedInWindow = 0,
                maxInWindow = RewardedLadderEngine.MAX_ADS_PER_LADDER,
                dailyCount = daily,
                maxDailyCount = RewardedLadderEngine.MAX_ADS_PER_DAY,
                isAdFreeActive = false,
                remainingAdFreeMs = 0L,
                nextRewardTotalHours = null,
                blockReason = RewardedOfferBlockReason.SUBSCRIPTION_ACTIVE
            )
        }

        if (!unitIds.hasRewardedUnit || !remoteConfig.globalEnabled || !remoteConfig.rewardedAdFreeEnabled) {
            return RewardedOfferStatus(
                visible = isAdFree, // Still show active ad-free countdown if user already earned it
                canWatchNow = false,
                completedInWindow = completed,
                maxInWindow = RewardedLadderEngine.MAX_ADS_PER_LADDER,
                dailyCount = daily,
                maxDailyCount = RewardedLadderEngine.MAX_ADS_PER_DAY,
                isAdFreeActive = isAdFree,
                remainingAdFreeMs = remaining,
                nextRewardTotalHours = null,
                blockReason = RewardedOfferBlockReason.ADS_DISABLED_OR_MISSING_ID
            )
        }

        // After 4 ads, no more rewarded offers are shown until ad-free time ends
        if (completed >= RewardedLadderEngine.MAX_ADS_PER_LADDER && isAdFree) {
            return RewardedOfferStatus(
                visible = true, // Shows active remaining ad-free time, hides watch button
                canWatchNow = false,
                completedInWindow = completed,
                maxInWindow = RewardedLadderEngine.MAX_ADS_PER_LADDER,
                dailyCount = daily,
                maxDailyCount = RewardedLadderEngine.MAX_ADS_PER_DAY,
                isAdFreeActive = true,
                remainingAdFreeMs = remaining,
                nextRewardTotalHours = null,
                blockReason = RewardedOfferBlockReason.LADDER_COMPLETE_WAIT_FOR_EXPIRY
            )
        }

        if (daily >= RewardedLadderEngine.MAX_ADS_PER_DAY) {
            return RewardedOfferStatus(
                visible = true,
                canWatchNow = false,
                completedInWindow = completed,
                maxInWindow = RewardedLadderEngine.MAX_ADS_PER_LADDER,
                dailyCount = daily,
                maxDailyCount = RewardedLadderEngine.MAX_ADS_PER_DAY,
                isAdFreeActive = isAdFree,
                remainingAdFreeMs = remaining,
                nextRewardTotalHours = null,
                blockReason = RewardedOfferBlockReason.DAILY_CAP_REACHED
            )
        }

        if (!isOnline) {
            return RewardedOfferStatus(
                visible = isAdFree, // When offline, only show remaining countdown if ad-free; hide offer card
                canWatchNow = false,
                completedInWindow = completed,
                maxInWindow = RewardedLadderEngine.MAX_ADS_PER_LADDER,
                dailyCount = daily,
                maxDailyCount = RewardedLadderEngine.MAX_ADS_PER_DAY,
                isAdFreeActive = isAdFree,
                remainingAdFreeMs = remaining,
                nextRewardTotalHours = null,
                blockReason = RewardedOfferBlockReason.OFFLINE
            )
        }

        return RewardedOfferStatus(
            visible = true,
            canWatchNow = true,
            completedInWindow = completed,
            maxInWindow = RewardedLadderEngine.MAX_ADS_PER_LADDER,
            dailyCount = daily,
            maxDailyCount = RewardedLadderEngine.MAX_ADS_PER_DAY,
            isAdFreeActive = isAdFree,
            remainingAdFreeMs = remaining,
            nextRewardTotalHours = RewardedLadderEngine.nextStepTotalHours(rewardedState, serverNowMs),
            blockReason = null
        )
    }

    fun isWithinFirst24hGrace(firstInstallServerMs: Long, serverNowMs: Long): Boolean {
        if (firstInstallServerMs <= 0L) return true
        val elapsed = serverNowMs - firstInstallServerMs
        return elapsed < FIRST_INSTALL_GRACE_MS
    }

    private fun isBannerRemoteEnabled(surface: AdScreenSurface, cfg: RemoteAdsConfig): Boolean = when (surface) {
        AdScreenSurface.TODAY_HOME -> cfg.todayBannerEnabled
        AdScreenSurface.ORG_CHOOSER -> cfg.orgChooserBannerEnabled
        AdScreenSurface.SCHOOL_CHOOSER -> cfg.schoolChooserBannerEnabled
        AdScreenSurface.STUDENT_LIST -> cfg.studentListBannerEnabled
        AdScreenSurface.TEACHER_LIST -> cfg.teacherListBannerEnabled
        AdScreenSurface.REPORT_LIST -> cfg.reportListBannerEnabled
        AdScreenSurface.TIMETABLE_VIEW -> cfg.timetableViewBannerEnabled
        else -> false
    }
}
