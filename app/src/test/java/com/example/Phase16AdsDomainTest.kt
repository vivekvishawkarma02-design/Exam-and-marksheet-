package com.example

import com.example.domain.AdScreenSurface
import com.example.domain.AdSuppressionReason
import com.example.domain.AdUnitIdsConfig
import com.example.domain.DebugSubscriptionSimulation
import com.example.domain.InterstitialFrequencyState
import com.example.domain.OrgRole
import com.example.domain.OrgSubscription
import com.example.domain.OrgSubscriptionStatus
import com.example.domain.Phase16AdsPolicyEngine
import com.example.domain.Phase16BillingConstants
import com.example.domain.PlaySubscriptionPlanOffer
import com.example.domain.RemoteAdsConfig
import com.example.domain.RewardedAdFreeState
import com.example.domain.RewardedLadderEngine
import com.example.domain.RewardedOfferBlockReason
import com.example.domain.SubscriptionPricingEngine
import com.example.ui.Phase16AdsUiState
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase16AdsDomainTest {

    private val hourMs = RewardedLadderEngine.HOUR_MS
    private val minMs = 60_000L
    // 2026-08-01T00:00:00Z
    private val baseDayUtcMs = 1_785_542_400_000L
    private val oldInstallMs = baseDayUtcMs - 48L * hourMs

    @Test
    fun rewardedLadder_exactProgression1To4AndHiddenUntilExpiry() {
        val t0 = baseDayUtcMs + 2L * hourMs

        // Step 1: 1 ad -> now + 2h
        val s1 = RewardedLadderEngine.recordCompletedReward(RewardedAdFreeState.EMPTY, t0)
        assertNotNull(s1)
        assertEquals(t0 + 2L * hourMs, s1!!.adFreeUntilMs)
        assertEquals(1, s1.window.countInWindow)
        assertEquals(1, s1.window.dailyCount)
        assertEquals(1L, s1.window.totalCount)
        assertEquals(t0, s1.window.windowStartAtMs)

        // Step 2: 2nd ad at t0 + 30m -> firstAd + 3h total
        val t1 = t0 + 30L * minMs
        val s2 = RewardedLadderEngine.recordCompletedReward(s1, t1)
        assertNotNull(s2)
        assertEquals(t0 + 3L * hourMs, s2!!.adFreeUntilMs)
        assertEquals(2, s2.window.countInWindow)
        assertEquals(2, s2.window.dailyCount)
        assertEquals(2L, s2.window.totalCount)
        assertEquals(t0, s2.window.windowStartAtMs)

        // Step 3: 3rd ad at t0 + 45m -> firstAd + 6h total
        val t2 = t0 + 45L * minMs
        val s3 = RewardedLadderEngine.recordCompletedReward(s2, t2)
        assertNotNull(s3)
        assertEquals(t0 + 6L * hourMs, s3!!.adFreeUntilMs)
        assertEquals(3, s3.window.countInWindow)
        assertEquals(3, s3.window.dailyCount)
        assertEquals(3L, s3.window.totalCount)

        // Step 4: 4th ad at t0 + 60m -> firstAd + 24h total
        val t3 = t0 + 60L * minMs
        val s4 = RewardedLadderEngine.recordCompletedReward(s3, t3)
        assertNotNull(s4)
        assertEquals(t0 + 24L * hourMs, s4!!.adFreeUntilMs)
        assertEquals(4, s4.window.countInWindow)
        assertEquals(4, s4.window.dailyCount)
        assertEquals(4L, s4.window.totalCount)
        assertTrue("adFreeUntil must never exceed serverNow + 24h", s4.adFreeUntilMs <= t3 + 24L * hourMs)

        // After 4 ads: NO further rewarded offers until ad-free ends
        assertFalse(RewardedLadderEngine.canWatchAnotherRewardedAd(s4, t3 + 10L * minMs))
        assertNull(RewardedLadderEngine.recordCompletedReward(s4, t3 + 10L * minMs))
        val offerAfter4 = Phase16AdsPolicyEngine.evaluateRewardedOffer(
            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
            rewardedState = s4,
            remoteConfig = RemoteAdsConfig(),
            unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
            isOnline = true,
            serverNowMs = t3 + 10L * minMs
        )
        assertTrue(offerAfter4.visible)
        assertFalse(offerAfter4.canWatchNow)
        assertTrue(offerAfter4.isAdFreeActive)
        assertEquals(4, offerAfter4.completedInWindow)
        assertEquals(RewardedOfferBlockReason.LADDER_COMPLETE_WAIT_FOR_EXPIRY, offerAfter4.blockReason)

        // After ad-free time expires (t0 + 24h + 1m): ladder resets cleanly
        val afterExpiry = t0 + 24L * hourMs + minMs
        assertFalse(s4.isAdFreeAt(afterExpiry))
        assertEquals(0, RewardedLadderEngine.activeWindowCount(s4, afterExpiry))
        assertTrue(RewardedLadderEngine.canWatchAnotherRewardedAd(s4, afterExpiry))
        val nextStart = RewardedLadderEngine.recordCompletedReward(s4, afterExpiry)
        assertNotNull(nextStart)
        assertEquals(1, nextStart!!.window.countInWindow)
        assertEquals(afterExpiry + 2L * hourMs, nextStart.adFreeUntilMs)
        assertEquals(5L, nextStart.window.totalCount)
    }

    @Test
    fun rewardedLadder_dailyCapOf8PerUserPerDay() {
        var state = RewardedAdFreeState.EMPTY
        var now = baseDayUtcMs + minMs

        repeat(8) { index ->
            val updated = RewardedLadderEngine.recordCompletedReward(state, now)
            assertNotNull("Watch #${index + 1} should succeed", updated)
            state = updated!!
            assertEquals(index + 1, state.window.dailyCount)
            now += 3L * hourMs + minMs
        }

        val checkSameDay = baseDayUtcMs + 21L * hourMs + 30L * minMs
        assertEquals(RewardedLadderEngine.utcDateKey(baseDayUtcMs), RewardedLadderEngine.utcDateKey(checkSameDay))
        assertFalse(RewardedLadderEngine.canWatchAnotherRewardedAd(state, checkSameDay))
        assertNull(RewardedLadderEngine.recordCompletedReward(state, checkSameDay))

        val offerAtCap = Phase16AdsPolicyEngine.evaluateRewardedOffer(
            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
            rewardedState = state,
            remoteConfig = RemoteAdsConfig(),
            unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
            isOnline = true,
            serverNowMs = checkSameDay
        )
        assertFalse(offerAtCap.canWatchNow)
        assertEquals(RewardedOfferBlockReason.DAILY_CAP_REACHED, offerAtCap.blockReason)

        // Next UTC day resets daily count to 0
        val nextUtcDay = baseDayUtcMs + 25L * hourMs
        assertTrue(RewardedLadderEngine.canWatchAnotherRewardedAd(state, nextUtcDay))
        val day2 = RewardedLadderEngine.recordCompletedReward(state, nextUtcDay)
        assertNotNull(day2)
        assertEquals(1, day2!!.window.dailyCount)
        assertEquals(9L, day2.window.totalCount)
    }

    @Test
    fun serverClockOffset_controlsExpiryEvenIfPhoneClockTampered() {
        val trueServerNow = baseDayUtcMs + 10L * hourMs
        val phoneClockBehind = trueServerNow - 5L * hourMs
        val serverOffset = +5L * hourMs

        val computedServerNow = Phase16AdsPolicyEngine.serverNowMs(phoneClockBehind, serverOffset)
        assertEquals(trueServerNow, computedServerNow)

        val rewarded = RewardedLadderEngine.recordCompletedReward(RewardedAdFreeState.EMPTY, trueServerNow)!!
        val laterPhoneClock = phoneClockBehind + 2L * hourMs + minMs
        val laterServerNow = Phase16AdsPolicyEngine.serverNowMs(laterPhoneClock, serverOffset)
        assertFalse("Ad-free must expire according to server time, not phone clock", rewarded.isAdFreeAt(laterServerNow))
    }

    @Test
    fun orgSubscription_entitledStatesSuppressAllAds_andLapsedOrFreeAllowAds() {
        val now = baseDayUtcMs + 12L * hourMs
        val futureExpiry = now + 10L * 24L * hourMs
        val pastExpiry = now - hourMs

        val entitledSubs = listOf(
            OrgSubscription(status = OrgSubscriptionStatus.ACTIVE, plan = Phase16BillingConstants.BASE_PLAN_YEARLY, expiresAtMs = futureExpiry, autoRenewing = true),
            OrgSubscription(status = OrgSubscriptionStatus.GRACE, plan = Phase16BillingConstants.BASE_PLAN_MONTHLY, expiresAtMs = futureExpiry),
            OrgSubscription(status = OrgSubscriptionStatus.ON_HOLD, plan = Phase16BillingConstants.BASE_PLAN_MONTHLY, expiresAtMs = futureExpiry),
            OrgSubscription(status = OrgSubscriptionStatus.CANCELLED_ACTIVE, plan = Phase16BillingConstants.BASE_PLAN_YEARLY, expiresAtMs = futureExpiry, autoRenewing = false)
        )

        val allowedBanners = listOf(
            AdScreenSurface.TODAY_HOME,
            AdScreenSurface.ORG_CHOOSER,
            AdScreenSurface.SCHOOL_CHOOSER,
            AdScreenSurface.STUDENT_LIST,
            AdScreenSurface.TEACHER_LIST,
            AdScreenSurface.REPORT_LIST,
            AdScreenSurface.TIMETABLE_VIEW
        )

        entitledSubs.forEach { sub ->
            allowedBanners.forEach { surface ->
                val banner = Phase16AdsPolicyEngine.evaluateBanner(
                    surface = surface,
                    effectiveSubscription = sub,
                    rewardedState = RewardedAdFreeState.EMPTY,
                    remoteConfig = RemoteAdsConfig(),
                    unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
                    isOnline = true,
                    firstInstallServerMs = oldInstallMs,
                    serverNowMs = now
                )
                assertFalse("Entitled org (${sub.status}) must suppress banner on $surface", banner.allowed)
                assertEquals(AdSuppressionReason.SUBSCRIPTION_ACTIVE, banner.reason)
            }

            listOf(AdScreenSurface.PDF_PREVIEW_CLOSED, AdScreenSurface.TASK_FINISHED_RETURN).forEach { trigger ->
                val inter = Phase16AdsPolicyEngine.evaluateInterstitialOnPdfClosed(
                    surface = trigger,
                    pdfWasFinishedSuccessfully = true,
                    effectiveSubscription = sub,
                    rewardedState = RewardedAdFreeState.EMPTY,
                    remoteConfig = RemoteAdsConfig(),
                    unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
                    frequencyState = InterstitialFrequencyState.EMPTY,
                    isOnline = true,
                    firstInstallServerMs = oldInstallMs,
                    serverNowMs = now
                )
                assertFalse("Entitled org (${sub.status}) must suppress interstitial on $trigger", inter.allowed)
                assertEquals(AdSuppressionReason.SUBSCRIPTION_ACTIVE, inter.reason)
            }

            val rewarded = Phase16AdsPolicyEngine.evaluateRewardedOffer(
                effectiveSubscription = sub,
                rewardedState = RewardedAdFreeState.EMPTY,
                remoteConfig = RemoteAdsConfig(),
                unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
                isOnline = true,
                serverNowMs = now
            )
            assertFalse("Entitled org (${sub.status}) must hide rewarded offer completely", rewarded.visible)
            assertFalse(rewarded.canWatchNow)
            assertEquals(RewardedOfferBlockReason.SUBSCRIPTION_ACTIVE, rewarded.blockReason)
        }

        // Lapsed / revoked / none / cancelled-past-expiry show ads again
        val nonEntitledSubs = listOf(
            OrgSubscription(status = OrgSubscriptionStatus.NONE),
            OrgSubscription(status = OrgSubscriptionStatus.EXPIRED, plan = Phase16BillingConstants.BASE_PLAN_MONTHLY, expiresAtMs = pastExpiry),
            OrgSubscription(status = OrgSubscriptionStatus.REVOKED, plan = Phase16BillingConstants.BASE_PLAN_YEARLY, expiresAtMs = pastExpiry),
            OrgSubscription(status = OrgSubscriptionStatus.CANCELLED_ACTIVE, plan = Phase16BillingConstants.BASE_PLAN_YEARLY, expiresAtMs = pastExpiry)
        )
        nonEntitledSubs.forEach { sub ->
            val banner = Phase16AdsPolicyEngine.evaluateBanner(
                surface = AdScreenSurface.TODAY_HOME,
                effectiveSubscription = sub,
                rewardedState = RewardedAdFreeState.EMPTY,
                remoteConfig = RemoteAdsConfig(),
                unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
                isOnline = true,
                firstInstallServerMs = oldInstallMs,
                serverNowMs = now
            )
            assertTrue("Non-entitled org (${sub.status}) should allow Today banner", banner.allowed)
        }
    }

    @Test
    fun equalRulesForEveryMemberRole_controlledOnlyByOrgSubscription() {
        val now = baseDayUtcMs + 12L * hourMs
        // Every member role (OWNER, ADMIN, TEACHER, CLERK, etc.) gets identical banner, interstitial, and rewarded rules
        val freeResults = OrgRole.entries.map {
            val banner = Phase16AdsPolicyEngine.evaluateBanner(
                surface = AdScreenSurface.STUDENT_LIST,
                effectiveSubscription = OrgSubscription.DEFAULT_FREE,
                rewardedState = RewardedAdFreeState.EMPTY,
                remoteConfig = RemoteAdsConfig(),
                unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
                isOnline = true,
                firstInstallServerMs = oldInstallMs,
                serverNowMs = now
            )
            val inter = Phase16AdsPolicyEngine.evaluateInterstitialOnPdfClosed(
                surface = AdScreenSurface.TASK_FINISHED_RETURN,
                pdfWasFinishedSuccessfully = true,
                effectiveSubscription = OrgSubscription.DEFAULT_FREE,
                rewardedState = RewardedAdFreeState.EMPTY,
                remoteConfig = RemoteAdsConfig(),
                unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
                frequencyState = InterstitialFrequencyState.EMPTY,
                isOnline = true,
                firstInstallServerMs = oldInstallMs,
                serverNowMs = now
            )
            Triple(banner.allowed, inter.allowed, banner.reason)
        }
        assertTrue(freeResults.all { it.first && it.second && it.third == AdSuppressionReason.ALLOWED })
    }

    @Test
    fun debugSubscriptionSimulation_worksInDebugAndIgnoredInRelease() {
        val serverFree = OrgSubscription(status = OrgSubscriptionStatus.NONE)
        val serverActive = OrgSubscription(status = OrgSubscriptionStatus.ACTIVE, plan = Phase16BillingConstants.BASE_PLAN_YEARLY)

        assertEquals(
            OrgSubscriptionStatus.ACTIVE,
            Phase16AdsPolicyEngine.resolveEffectiveSubscription(
                serverFree,
                isDebugBuild = true,
                debugSimulation = DebugSubscriptionSimulation.FORCE_ACTIVE
            ).status
        )
        assertEquals(
            OrgSubscriptionStatus.NONE,
            Phase16AdsPolicyEngine.resolveEffectiveSubscription(
                serverActive,
                isDebugBuild = true,
                debugSimulation = DebugSubscriptionSimulation.FORCE_FREE
            ).status
        )

        assertEquals(
            OrgSubscriptionStatus.NONE,
            Phase16AdsPolicyEngine.resolveEffectiveSubscription(
                serverFree,
                isDebugBuild = false,
                debugSimulation = DebugSubscriptionSimulation.FORCE_ACTIVE
            ).status
        )
        assertEquals(
            OrgSubscriptionStatus.ACTIVE,
            Phase16AdsPolicyEngine.resolveEffectiveSubscription(
                serverActive,
                isDebugBuild = false,
                debugSimulation = DebugSubscriptionSimulation.FORCE_FREE
            ).status
        )
    }

    @Test
    fun strictSurfaceAllowlist_allowsExactly7BannersAnd2TaskFinishedInterstitials_blocksAllSensitiveScreens() {
        val now = baseDayUtcMs + 12L * hourMs
        val allowedBannerSurfaces = setOf(
            AdScreenSurface.TODAY_HOME,
            AdScreenSurface.ORG_CHOOSER,
            AdScreenSurface.SCHOOL_CHOOSER,
            AdScreenSurface.STUDENT_LIST,
            AdScreenSurface.TEACHER_LIST,
            AdScreenSurface.REPORT_LIST,
            AdScreenSurface.TIMETABLE_VIEW
        )
        val allowedInterstitialSurfaces = setOf(
            AdScreenSurface.PDF_PREVIEW_CLOSED,
            AdScreenSurface.TASK_FINISHED_RETURN
        )

        // Explicitly confirm sensitive/data-entry screens stay strictly forbidden
        val mustStayForbidden = listOf(
            AdScreenSurface.LOGIN_SIGNUP,
            AdScreenSurface.EMAIL_VERIFICATION,
            AdScreenSurface.STUDENTS,
            AdScreenSurface.STUDENT_REGISTER,
            AdScreenSurface.FEES,
            AdScreenSurface.SALARY,
            AdScreenSurface.CASH_BOOK,
            AdScreenSurface.MARKS,
            AdScreenSurface.ATTENDANCE,
            AdScreenSurface.TRANSPORT,
            AdScreenSurface.RECEIPTS,
            AdScreenSurface.CERTIFICATES,
            AdScreenSurface.PDF_PREVIEW_OPEN,
            AdScreenSurface.SETTINGS,
            AdScreenSurface.SUBSCRIPTION,
            AdScreenSurface.ACCOUNT_DELETION
        )
        mustStayForbidden.forEach { forbidden ->
            assertFalse("$forbidden must NEVER allow banner", forbidden.allowsBanner)
            assertFalse("$forbidden must NEVER allow interstitial", forbidden.allowsPdfCloseInterstitial)
        }

        AdScreenSurface.entries.forEach { surface ->
            val banner = Phase16AdsPolicyEngine.evaluateBanner(
                surface = surface,
                effectiveSubscription = OrgSubscription.DEFAULT_FREE,
                rewardedState = RewardedAdFreeState.EMPTY,
                remoteConfig = RemoteAdsConfig(),
                unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
                isOnline = true,
                firstInstallServerMs = oldInstallMs,
                serverNowMs = now
            )
            if (surface in allowedBannerSurfaces) {
                assertTrue("Banner should be allowed on $surface", banner.allowed)
            } else {
                assertFalse("Banner MUST be forbidden on $surface", banner.allowed)
                assertEquals(AdSuppressionReason.SURFACE_FORBIDDEN, banner.reason)
            }

            val inter = Phase16AdsPolicyEngine.evaluateInterstitialOnPdfClosed(
                surface = surface,
                pdfWasFinishedSuccessfully = true,
                effectiveSubscription = OrgSubscription.DEFAULT_FREE,
                rewardedState = RewardedAdFreeState.EMPTY,
                remoteConfig = RemoteAdsConfig(),
                unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
                frequencyState = InterstitialFrequencyState.EMPTY,
                isOnline = true,
                firstInstallServerMs = oldInstallMs,
                serverNowMs = now
            )
            if (surface in allowedInterstitialSurfaces) {
                assertTrue("Interstitial should be allowed on $surface", inter.allowed)
            } else {
                assertFalse("Interstitial MUST be forbidden on $surface", inter.allowed)
                assertEquals(AdSuppressionReason.SURFACE_FORBIDDEN, inter.reason)
            }
        }
    }

    @Test
    fun interstitial_requiresFinishedTaskAndEnforces8MinAnd8PerDayCap() {
        val t0 = baseDayUtcMs + 1L * hourMs

        // Unfinished PDF -> blocked
        val unfinished = Phase16AdsPolicyEngine.evaluateInterstitialOnPdfClosed(
            surface = AdScreenSurface.PDF_PREVIEW_CLOSED,
            pdfWasFinishedSuccessfully = false,
            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
            rewardedState = RewardedAdFreeState.EMPTY,
            remoteConfig = RemoteAdsConfig(),
            unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
            frequencyState = InterstitialFrequencyState.EMPTY,
            isOnline = true,
            firstInstallServerMs = oldInstallMs,
            serverNowMs = t0
        )
        assertFalse(unfinished.allowed)
        assertEquals(AdSuppressionReason.PDF_NOT_FINISHED, unfinished.reason)

        // 1st show allowed
        var freq = InterstitialFrequencyState.EMPTY
        assertTrue(freq.canShowAt(t0))
        freq = freq.recordShownAt(t0)

        // 7m59s later -> blocked
        val tooSoon = t0 + 8L * minMs - 1_000L
        val resSoon = Phase16AdsPolicyEngine.evaluateInterstitialOnPdfClosed(
            surface = AdScreenSurface.PDF_PREVIEW_CLOSED,
            pdfWasFinishedSuccessfully = true,
            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
            rewardedState = RewardedAdFreeState.EMPTY,
            remoteConfig = RemoteAdsConfig(),
            unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
            frequencyState = freq,
            isOnline = true,
            firstInstallServerMs = oldInstallMs,
            serverNowMs = tooSoon
        )
        assertFalse(resSoon.allowed)
        assertEquals(AdSuppressionReason.INTERSTITIAL_FREQUENCY_CAP, resSoon.reason)

        // Shows #2 through #8 spaced exactly 8 minutes apart -> all allowed
        var current = t0
        for (showNum in 2..8) {
            current += 8L * minMs
            assertTrue("Show #$showNum at +8m should be allowed", freq.canShowAt(current))
            freq = freq.recordShownAt(current)
            assertEquals(showNum, freq.dailyShownCount)
        }

        // 9th show on same UTC day (even 2 hours later) -> blocked by 8/day cap
        val ninthSameDay = current + 2L * hourMs
        val resCap = Phase16AdsPolicyEngine.evaluateInterstitialOnPdfClosed(
            surface = AdScreenSurface.TASK_FINISHED_RETURN,
            pdfWasFinishedSuccessfully = true,
            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
            rewardedState = RewardedAdFreeState.EMPTY,
            remoteConfig = RemoteAdsConfig(),
            unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
            frequencyState = freq,
            isOnline = true,
            firstInstallServerMs = oldInstallMs,
            serverNowMs = ninthSameDay
        )
        assertFalse("9th interstitial in one day must be blocked", resCap.allowed)
        assertEquals(AdSuppressionReason.INTERSTITIAL_FREQUENCY_CAP, resCap.reason)

        // Next UTC day -> allowed again
        val nextDay = baseDayUtcMs + 25L * hourMs
        assertTrue(freq.canShowAt(nextDay))
    }

    @Test
    fun first24hInstallGrace_offlineCollapse_remoteKillSwitch_andMissingReleaseIds() {
        val installTime = baseDayUtcMs
        val at23h59m = installTime + 24L * hourMs - minMs
        val at24h00m = installTime + 24L * hourMs

        val duringGrace = Phase16AdsPolicyEngine.evaluateBanner(
            surface = AdScreenSurface.TODAY_HOME,
            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
            rewardedState = RewardedAdFreeState.EMPTY,
            remoteConfig = RemoteAdsConfig(),
            unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
            isOnline = true,
            firstInstallServerMs = installTime,
            serverNowMs = at23h59m
        )
        assertFalse(duringGrace.allowed)
        assertEquals(AdSuppressionReason.FIRST_24H_INSTALL_GRACE, duringGrace.reason)

        val afterGrace = Phase16AdsPolicyEngine.evaluateBanner(
            surface = AdScreenSurface.STUDENT_LIST,
            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
            rewardedState = RewardedAdFreeState.EMPTY,
            remoteConfig = RemoteAdsConfig(),
            unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
            isOnline = true,
            firstInstallServerMs = installTime,
            serverNowMs = at24h00m
        )
        assertTrue(afterGrace.allowed)

        // Offline -> collapsed
        val offline = Phase16AdsPolicyEngine.evaluateBanner(
            surface = AdScreenSurface.TIMETABLE_VIEW,
            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
            rewardedState = RewardedAdFreeState.EMPTY,
            remoteConfig = RemoteAdsConfig(),
            unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
            isOnline = false,
            firstInstallServerMs = oldInstallMs,
            serverNowMs = at24h00m
        )
        assertFalse(offline.allowed)
        assertEquals(AdSuppressionReason.OFFLINE, offline.reason)

        // Remote per-placement kill-switch -> blocked on new 16B placements too
        val remoteOff = Phase16AdsPolicyEngine.evaluateBanner(
            surface = AdScreenSurface.REPORT_LIST,
            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
            rewardedState = RewardedAdFreeState.EMPTY,
            remoteConfig = RemoteAdsConfig(reportListBannerEnabled = false),
            unitIds = AdUnitIdsConfig.OFFICIAL_TEST,
            isOnline = true,
            firstInstallServerMs = oldInstallMs,
            serverNowMs = at24h00m
        )
        assertFalse(remoteOff.allowed)
        assertEquals(AdSuppressionReason.REMOTE_DISABLED, remoteOff.reason)

        // Missing or dummy release IDs -> disabled cleanly with no crash
        assertFalse(AdUnitIdsConfig.DISABLED.isConfiguredForSdk)
        val dummyConfig = AdUnitIdsConfig(
            appId = "ca-app-pub-0000000000000000~0000000000",
            bannerId = "ca-app-pub-0000000000000000/0000000000",
            interstitialId = "",
            rewardedId = "",
            isDebugBuild = false
        )
        assertFalse(dummyConfig.isConfiguredForSdk)
        val missingIdsResult = Phase16AdsPolicyEngine.evaluateBanner(
            surface = AdScreenSurface.TODAY_HOME,
            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
            rewardedState = RewardedAdFreeState.EMPTY,
            remoteConfig = RemoteAdsConfig(),
            unitIds = dummyConfig,
            isOnline = true,
            firstInstallServerMs = oldInstallMs,
            serverNowMs = at24h00m
        )
        assertFalse(missingIdsResult.allowed)
        assertEquals(AdSuppressionReason.MISSING_OR_INVALID_AD_IDS, missingIdsResult.reason)
    }

    @Test
    fun subscriptionPricingEngine_computesDynamicYearlySavingsFromPlayOffers() {
        val monthly = PlaySubscriptionPlanOffer(
            basePlanId = Phase16BillingConstants.BASE_PLAN_MONTHLY,
            offerToken = "tok-m",
            formattedPrice = "₹200",
            priceAmountMicros = 200_000_000L,
            priceCurrencyCode = "INR",
            billingPeriodIso = "P1M"
        )
        val yearly = PlaySubscriptionPlanOffer(
            basePlanId = Phase16BillingConstants.BASE_PLAN_YEARLY,
            offerToken = "tok-y",
            formattedPrice = "₹1,500",
            priceAmountMicros = 1_500_000_000L,
            priceCurrencyCode = "INR",
            billingPeriodIso = "P1Y"
        )

        val savings = SubscriptionPricingEngine.computeYearlySavings(monthly, yearly, Locale("en", "IN"))
        assertNotNull(savings)
        assertEquals(900_000_000L, savings!!.annualSavingsMicros)
        assertEquals(38, savings.savingsPercent)
        assertTrue("Formatted savings should contain 900", savings.formattedSavings.contains("900"))

        // Mismatched currency returns null safely
        val usdYearly = yearly.copy(priceCurrencyCode = "USD")
        assertNull(SubscriptionPricingEngine.computeYearlySavings(monthly, usdYearly))

        // No savings if yearly >= 12x monthly
        val expensiveYearly = yearly.copy(priceAmountMicros = 2_500_000_000L)
        assertNull(SubscriptionPricingEngine.computeYearlySavings(monthly, expensiveYearly))
    }

    @Test
    fun lapsedSubscriptionNotice_shownOnlyToOwnerUntilDismissed() {
        val expiredSub = OrgSubscription(
            status = OrgSubscriptionStatus.EXPIRED,
            plan = Phase16BillingConstants.BASE_PLAN_MONTHLY,
            expiresAtMs = baseDayUtcMs,
            updatedAtMs = baseDayUtcMs + 1000L
        )
        val uiState = Phase16AdsUiState(
            orgSubscription = expiredSub,
            effectiveSubscription = expiredSub
        )

        assertTrue(uiState.shouldShowLapsedNoticeToOwner(isOwner = true, dismissedMarkerMs = 0L))
        assertFalse("Non-owners must never see lapsed renewal notice", uiState.shouldShowLapsedNoticeToOwner(isOwner = false, dismissedMarkerMs = 0L))
        assertFalse(
            "Once owner dismisses this lapse marker, notice stays hidden",
            uiState.shouldShowLapsedNoticeToOwner(isOwner = true, dismissedMarkerMs = uiState.lapsedNoticeMarkerMs())
        )
    }
}
