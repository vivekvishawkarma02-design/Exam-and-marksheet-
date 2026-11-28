package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.ads.AdMobRequestFactory
import com.example.domain.AdEligibilityResult
import com.example.domain.AdSuppressionReason
import com.example.domain.AdUnitIdsConfig
import com.example.domain.DebugSubscriptionSimulation
import com.example.domain.OrgSubscription
import com.example.domain.OrgSubscriptionStatus
import com.example.domain.Phase16BillingConstants
import com.example.domain.PlayBillingUiSnapshot
import com.example.domain.PlaySubscriptionPlanOffer
import com.example.domain.RewardedOfferBlockReason
import com.example.domain.RewardedOfferStatus
import com.example.ui.ADAPTIVE_BANNER_RESERVED_HEIGHT
import com.example.ui.AdaptiveAdBannerSlot
import com.example.ui.DebugAdsSimulationSection
import com.example.ui.OwnerLapsedSubscriptionNoticeBanner
import com.example.ui.Phase16SchoolSubscriptionScreen
import com.example.ui.RemoveAdsForEveryoneCard
import com.example.ui.RewardedAdFreeCard
import com.example.ui.theme.SchoolAppTheme
import com.google.android.gms.ads.RequestConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
class Phase16AdsLayoutRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun adMobRequestFactory_workplaceSafeAndZeroPii() {
        val cfg = AdMobRequestFactory.buildWorkplaceSafeRequestConfiguration()
        assertEquals(
            RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE,
            cfg.tagForChildDirectedTreatment
        )
        assertEquals(
            RequestConfiguration.MAX_AD_CONTENT_RATING_G,
            cfg.maxAdContentRating
        )

        val request = AdMobRequestFactory.buildZeroPiiAdRequest()
        assertTrue("Zero keywords must be attached to AdRequest", request.keywords.isEmpty())
        assertTrue("Zero neighboring content URLs", request.neighboringContentUrls.isEmpty())
        assertNull("Zero content URL attached", request.contentUrl)
        assertTrue("Zero custom targeting extras", request.customTargeting.isEmpty)
    }

    @Test
    fun adaptiveBannerSlot_reservesFixedHeightWhenEligible_andCollapsesWhenOfflineOrPaid() {
        composeRule.setContent {
            SchoolAppTheme {
                Column(modifier = Modifier.width(320.dp)) {
                    AdaptiveAdBannerSlot(
                        eligibility = AdEligibilityResult(true, AdSuppressionReason.ALLOWED),
                        canRequestAds = true,
                        bannerUnitId = AdUnitIdsConfig.OFFICIAL_TEST_BANNER_ID,
                        testTagPrefix = "eligible",
                        useTestPlaceholderInRobolectric = true
                    )
                    AdaptiveAdBannerSlot(
                        eligibility = AdEligibilityResult(false, AdSuppressionReason.OFFLINE),
                        canRequestAds = true,
                        bannerUnitId = AdUnitIdsConfig.OFFICIAL_TEST_BANNER_ID,
                        testTagPrefix = "offline",
                        useTestPlaceholderInRobolectric = true
                    )
                    AdaptiveAdBannerSlot(
                        eligibility = AdEligibilityResult(false, AdSuppressionReason.SUBSCRIPTION_ACTIVE),
                        canRequestAds = true,
                        bannerUnitId = AdUnitIdsConfig.OFFICIAL_TEST_BANNER_ID,
                        testTagPrefix = "paid",
                        useTestPlaceholderInRobolectric = true
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val eligibleBounds = composeRule
            .onNodeWithTag("eligible_banner_slot")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals(
            "Banner slot must reserve exact fixed height to prevent layout shift",
            ADAPTIVE_BANNER_RESERVED_HEIGHT.value,
            eligibleBounds.height.value,
            0.5f
        )
        assertTrue("Banner slot must fit inside 320dp", eligibleBounds.right <= 320.5.dp)

        composeRule.onNodeWithTag("offline_banner_slot").assertDoesNotExist()
        composeRule.onNodeWithTag("paid_banner_slot").assertDoesNotExist()
    }

    private fun verifyRewardedCardAndExplanationDialogAt320dp(fontScale: Float, isHindi: Boolean) {
        var watchStarted = false
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = fontScale)) {
                SchoolAppTheme {
                    Box(modifier = Modifier.width(320.dp).padding(12.dp)) {
                        RewardedAdFreeCard(
                            offerStatus = RewardedOfferStatus(
                                visible = true,
                                canWatchNow = true,
                                completedInWindow = 2,
                                maxInWindow = 4,
                                dailyCount = 2,
                                maxDailyCount = 8,
                                isAdFreeActive = true,
                                remainingAdFreeMs = 2L * 3_600_000L + 15L * 60_000L,
                                nextRewardTotalHours = 6,
                                blockReason = null
                            ),
                            isHindi = isHindi,
                            isRewardedLoading = false,
                            onWatchConfirmed = { watchStarted = true },
                            testTagPrefix = "test_rewarded"
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        if (isHindi) {
            assertTrue(
                "Hindi rewarded UI must use 'विज्ञापन-रहित समय'",
                composeRule.onAllNodesWithText("विज्ञापन-रहित समय", substring = true).fetchSemanticsNodes().isNotEmpty()
            )
            assertEquals(
                "Hindi ad-free UI must NEVER use 'छुट्टी'",
                0,
                composeRule.onAllNodesWithText("छुट्टी", substring = true).fetchSemanticsNodes().size
            )
        }

        val cardBounds = composeRule.onNodeWithTag("test_rewarded_container").assertIsDisplayed().getUnclippedBoundsInRoot()
        val btnBounds = composeRule.onNodeWithTag("test_rewarded_watch_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Card right (${cardBounds.right}) <= 320dp at $fontScale", cardBounds.right <= 320.5.dp)
        assertTrue("Watch button >= 48dp touch target, got ${btnBounds.height}", btnBounds.height >= 47.5.dp)
        assertTrue("Watch button right (${btnBounds.right}) <= 320dp at $fontScale", btnBounds.right <= 320.5.dp)

        // Clicking opens explanation dialog BEFORE starting any ad
        composeRule.onNodeWithTag("test_rewarded_watch_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Ad must not start before explanation dialog confirmation", !watchStarted)

        if (isHindi) {
            assertEquals(
                "Hindi explanation dialog must NEVER use 'छुट्टी'",
                0,
                composeRule.onAllNodesWithText("छुट्टी", substring = true).fetchSemanticsNodes().size
            )
        }

        val startBtnBounds = composeRule.onNodeWithTag("rewarded_dialog_start_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        val cancelBtnBounds = composeRule.onNodeWithTag("rewarded_dialog_cancel_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Dialog start button >= 48dp, got ${startBtnBounds.height}", startBtnBounds.height >= 47.5.dp)
        assertTrue("Dialog cancel button >= 48dp, got ${cancelBtnBounds.height}", cancelBtnBounds.height >= 47.5.dp)

        composeRule.onNodeWithTag("rewarded_dialog_start_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Confirming explanation dialog starts rewarded ad", watchStarted)
    }

    @Test
    fun rewardedCardAndDialog_320dp_font1_3_hindi_noClip() {
        verifyRewardedCardAndExplanationDialogAt320dp(fontScale = 1.3f, isHindi = true)
    }

    @Test
    fun rewardedCardAndDialog_320dp_font1_5_english_noClip() {
        verifyRewardedCardAndExplanationDialogAt320dp(fontScale = 1.5f, isHindi = false)
    }

    @Test
    fun rewardedCard_hiddenWhenPaidOrg_andHidesWatchButtonAfter4Ads() {
        composeRule.setContent {
            SchoolAppTheme {
                Column(modifier = Modifier.width(320.dp).padding(12.dp)) {
                    RewardedAdFreeCard(
                        offerStatus = RewardedOfferStatus(
                            visible = false,
                            canWatchNow = false,
                            completedInWindow = 0,
                            maxInWindow = 4,
                            dailyCount = 0,
                            maxDailyCount = 8,
                            isAdFreeActive = false,
                            remainingAdFreeMs = 0L,
                            nextRewardTotalHours = null,
                            blockReason = RewardedOfferBlockReason.SUBSCRIPTION_ACTIVE
                        ),
                        isHindi = false,
                        isRewardedLoading = false,
                        onWatchConfirmed = {},
                        testTagPrefix = "paid_rewarded"
                    )
                    RewardedAdFreeCard(
                        offerStatus = RewardedOfferStatus(
                            visible = true,
                            canWatchNow = false,
                            completedInWindow = 4,
                            maxInWindow = 4,
                            dailyCount = 4,
                            maxDailyCount = 8,
                            isAdFreeActive = true,
                            remainingAdFreeMs = 20L * 3_600_000L,
                            nextRewardTotalHours = null,
                            blockReason = RewardedOfferBlockReason.LADDER_COMPLETE_WAIT_FOR_EXPIRY
                        ),
                        isHindi = false,
                        isRewardedLoading = false,
                        onWatchConfirmed = {},
                        testTagPrefix = "full_rewarded"
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("paid_rewarded_container").assertDoesNotExist()
        composeRule.onNodeWithTag("full_rewarded_container").assertIsDisplayed()
        composeRule.onNodeWithTag("full_rewarded_watch_button").assertDoesNotExist()
        composeRule.onNodeWithTag("full_rewarded_complete_note").assertIsDisplayed()
    }

    @Test
    fun removeAdsForEveryoneCard_andDebugSwitch_320dp_font1_5_noClip() {
        var openedSubscriptionScreen = false
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = 1.5f)) {
                SchoolAppTheme {
                    Column(modifier = Modifier.width(320.dp).padding(12.dp)) {
                        RemoveAdsForEveryoneCard(
                            effectiveSubscription = OrgSubscription.DEFAULT_FREE,
                            isOwner = true,
                            isHindi = true,
                            modifier = Modifier.fillMaxWidth(),
                            onOpenSubscriptionScreen = { openedSubscriptionScreen = true }
                        )
                        DebugAdsSimulationSection(
                            isHindi = true,
                            currentSimulation = DebugSubscriptionSimulation.FOLLOW_SERVER,
                            bypassInstallGrace = false,
                            onSimulationChanged = {},
                            onBypassInstallGraceChanged = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val ownerCard = composeRule.onNodeWithTag("remove_ads_for_everyone_card").assertIsDisplayed().getUnclippedBoundsInRoot()
        val openSubBtn = composeRule.onNodeWithTag("open_school_subscription_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Owner card right (${ownerCard.right}) <= 320dp", ownerCard.right <= 320.5.dp)
        assertTrue("Open subscription button >= 48dp, got ${openSubBtn.height}", openSubBtn.height >= 47.5.dp)
        assertTrue("Open subscription button right (${openSubBtn.right}) <= 320dp", openSubBtn.right <= 320.5.dp)

        composeRule.onNodeWithTag("open_school_subscription_button").performClick()
        assertTrue("Owner tapping card opens subscription screen", openedSubscriptionScreen)

        val debugCard = composeRule.onNodeWithTag("debug_ads_simulation_card").assertIsDisplayed().getUnclippedBoundsInRoot()
        val chipBounds = composeRule.onNodeWithTag("debug_sub_force_active").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Debug card right (${debugCard.right}) <= 320dp", debugCard.right <= 320.5.dp)
        assertTrue("Debug chip >= 48dp height, got ${chipBounds.height}", chipBounds.height >= 47.5.dp)
    }

    @Test
    fun removeAdsForEveryoneCard_nonOwnerSeesOnlyStatusLine_freeAndAdFree() {
        composeRule.setContent {
            SchoolAppTheme {
                Column(modifier = Modifier.width(320.dp).padding(12.dp)) {
                    RemoveAdsForEveryoneCard(
                        effectiveSubscription = OrgSubscription(status = OrgSubscriptionStatus.NONE),
                        isOwner = false,
                        isHindi = false,
                        onOpenSubscriptionScreen = {}
                    )
                    RemoveAdsForEveryoneCard(
                        effectiveSubscription = OrgSubscription(status = OrgSubscriptionStatus.ACTIVE, plan = Phase16BillingConstants.BASE_PLAN_YEARLY),
                        isOwner = false,
                        isHindi = false,
                        onOpenSubscriptionScreen = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("remove_ads_ask_owner_text").assertIsDisplayed()
        composeRule.onNodeWithTag("member_school_ad_free_text").assertIsDisplayed()
        composeRule.onNodeWithTag("open_school_subscription_button").assertDoesNotExist()
    }

    @Test
    fun ownerSubscriptionScreen_320dp_font1_5_hindi_showsDynamicPricesSavingsAnd48dpTargets() {
        var subscribedPlanId: String? = null
        var restoreClicked = false
        val monthlyOffer = PlaySubscriptionPlanOffer(
            basePlanId = Phase16BillingConstants.BASE_PLAN_MONTHLY,
            offerToken = "tok-monthly",
            formattedPrice = "₹200",
            priceAmountMicros = 200_000_000L,
            priceCurrencyCode = "INR",
            billingPeriodIso = "P1M"
        )
        val yearlyOffer = PlaySubscriptionPlanOffer(
            basePlanId = Phase16BillingConstants.BASE_PLAN_YEARLY,
            offerToken = "tok-yearly",
            formattedPrice = "₹1,500",
            priceAmountMicros = 1_500_000_000L,
            priceCurrencyCode = "INR",
            billingPeriodIso = "P1Y"
        )

        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = 1.5f)) {
                SchoolAppTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        Phase16SchoolSubscriptionScreen(
                            orgName = "सरस्वती विद्या मंदिर",
                            isOwner = true,
                            isHindi = true,
                            isOnline = true,
                            subscription = OrgSubscription.DEFAULT_FREE,
                            billingState = PlayBillingUiSnapshot(
                                isConnected = true,
                                isPlayBillingAvailable = true,
                                monthlyOffer = monthlyOffer,
                                yearlyOffer = yearlyOffer
                            ),
                            onRefreshOffers = {},
                            onSubscribePlan = { subscribedPlanId = it.basePlanId },
                            onRestorePurchase = { restoreClicked = true },
                            onOpenPrivacyAndTerms = {},
                            onToggleLanguage = {},
                            onBack = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(
            "Subscription screen in Hindi must NEVER use 'छुट्टी'",
            0,
            composeRule.onAllNodesWithText("छुट्टी", substring = true).fetchSemanticsNodes().size
        )

        composeRule.onNodeWithTag("owner_subscription_status_card").assertIsDisplayed()
        composeRule.onNodeWithTag("plan_yearly_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("yearly_savings_badge").assertIsDisplayed()
        val yearlyBtnBounds = composeRule
            .onNodeWithTag("plan_yearly_subscribe_button")
            .performScrollTo()
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue("Yearly Subscribe button >= 48dp, got ${yearlyBtnBounds.height}", yearlyBtnBounds.height >= 47.5.dp)
        assertTrue("Yearly Subscribe button fits 320dp, right=${yearlyBtnBounds.right}", yearlyBtnBounds.right <= 320.5.dp)
        composeRule.onNodeWithTag("plan_yearly_subscribe_button").performClick()
        assertEquals(Phase16BillingConstants.BASE_PLAN_YEARLY, subscribedPlanId)

        val manageBtnBounds = composeRule
            .onNodeWithTag("manage_subscription_button")
            .performScrollTo()
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue("Manage subscription button >= 48dp", manageBtnBounds.height >= 47.5.dp)
        assertTrue("Manage subscription button fits 320dp", manageBtnBounds.right <= 320.5.dp)

        val restoreBtnBounds = composeRule
            .onNodeWithTag("restore_purchase_button")
            .performScrollTo()
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue("Restore purchase button >= 48dp", restoreBtnBounds.height >= 47.5.dp)
        composeRule.onNodeWithTag("restore_purchase_button").performClick()
        assertTrue("Restore purchase click invokes callback", restoreClicked)

        val privacyLinkBounds = composeRule
            .onNodeWithTag("subscription_privacy_policy_link")
            .performScrollTo()
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue("Privacy policy link >= 48dp", privacyLinkBounds.height >= 47.5.dp)
    }

    @Test
    fun ownerLapsedSubscriptionNoticeBanner_320dp_font1_3_has48dpTargets() {
        var renewClicked = false
        var dismissClicked = false
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = 1.3f)) {
                SchoolAppTheme {
                    Box(modifier = Modifier.width(320.dp).padding(12.dp)) {
                        OwnerLapsedSubscriptionNoticeBanner(
                            subscription = OrgSubscription(status = OrgSubscriptionStatus.EXPIRED),
                            isHindi = true,
                            onOpenSubscriptionScreen = { renewClicked = true },
                            onDismiss = { dismissClicked = true }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val cardBounds = composeRule.onNodeWithTag("owner_lapsed_subscription_notice").assertIsDisplayed().getUnclippedBoundsInRoot()
        val renewBounds = composeRule.onNodeWithTag("lapsed_notice_renew_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        val dismissBounds = composeRule.onNodeWithTag("lapsed_notice_dismiss_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Lapsed banner fits 320dp", cardBounds.right <= 320.5.dp)
        assertTrue("Renew button >= 48dp", renewBounds.height >= 47.5.dp)
        assertTrue("Dismiss button >= 48dp", dismissBounds.height >= 47.5.dp)

        composeRule.onNodeWithTag("lapsed_notice_renew_button").performClick()
        composeRule.onNodeWithTag("lapsed_notice_dismiss_button").performClick()
        assertTrue(renewClicked)
        assertTrue(dismissClicked)
    }
}
