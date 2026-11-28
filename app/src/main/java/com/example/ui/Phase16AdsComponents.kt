package com.example.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.ads.AdMobRequestFactory
import com.example.ads.Phase16AdMobController
import com.example.ads.findActivityOrNull
import com.example.domain.AdEligibilityResult
import com.example.domain.AdUnitIdsConfig
import com.example.domain.DebugSubscriptionSimulation
import com.example.domain.OrgSubscription
import com.example.domain.OrgSubscriptionStatus
import com.example.domain.RewardedLadderEngine
import com.example.domain.RewardedOfferBlockReason
import com.example.domain.RewardedOfferStatus
import com.example.domain.SubscriptionPricingEngine
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Fixed reserved height for the adaptive banner container when ads are eligible & online.
 * Reserving fixed space prevents any layout shift when the banner loads asynchronously.
 * When ineligible (offline, paid org, ad-free reward, first 24h, forbidden screen), height collapses to 0.dp.
 */
val ADAPTIVE_BANNER_RESERVED_HEIGHT = 60.dp

@Composable
fun AdaptiveAdBannerSlot(
    eligibility: AdEligibilityResult,
    canRequestAds: Boolean,
    bannerUnitId: String,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    useTestPlaceholderInRobolectric: Boolean = false
) {
    if (!eligibility.allowed || !AdUnitIdsConfig.isValidAdUnitId(bannerUnitId)) {
        return
    }

    val context = LocalContext.current
    val runtimeReady = remember(context, bannerUnitId) {
        Phase16AdMobController.isPlayServicesAvailable(context)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(ADAPTIVE_BANNER_RESERVED_HEIGHT)
            .testTag("${testTagPrefix}_banner_slot"),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val containerWidthDp = maxWidth.value.toInt().coerceAtLeast(300)
            if (useTestPlaceholderInRobolectric || !runtimeReady || !canRequestAds) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ADAPTIVE_BANNER_RESERVED_HEIGHT)
                        .testTag("${testTagPrefix}_banner_reserved_space")
                )
            } else {
                var loadFailed by remember(bannerUnitId) { mutableStateOf(false) }
                if (!loadFailed) {
                    val adView = remember(context, bannerUnitId, containerWidthDp) {
                        try {
                            AdView(context).apply {
                                adUnitId = bannerUnitId
                                setAdSize(Phase16AdMobController.adaptiveBannerSize(context, containerWidthDp))
                                adListener = object : AdListener() {
                                    override fun onAdFailedToLoad(error: LoadAdError) {
                                        loadFailed = true
                                    }
                                }
                                loadAd(AdMobRequestFactory.buildZeroPiiAdRequest())
                            }
                        } catch (_: Throwable) {
                            loadFailed = true
                            null
                        }
                    }

                    if (adView != null) {
                        DisposableEffect(adView) {
                            onDispose {
                                try {
                                    adView.destroy()
                                } catch (_: Throwable) {
                                }
                            }
                        }
                        AndroidView(
                            factory = { adView },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ADAPTIVE_BANNER_RESERVED_HEIGHT)
                                .testTag("${testTagPrefix}_banner_adview")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Optional, never-forced card: "Watch ads to remove ads for a while".
 * - Hidden completely when organisation has active/grace subscription.
 * - Shows remaining ad-free time and progress (e.g., "2 of 4 ads").
 * - Opens a short explanation dialog BEFORE any rewarded ad starts.
 * - Mobile-first: stacked Column layout, zero clipping at 320dp and 1.5x font, >= 48.dp touch target.
 */
@Composable
fun RewardedAdFreeCard(
    offerStatus: RewardedOfferStatus,
    isHindi: Boolean,
    isRewardedLoading: Boolean,
    onWatchConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    testTagPrefix: String = "rewarded_card"
) {
    if (!offerStatus.visible) return

    var showExplanationDialog by remember { mutableStateOf(false) }

    val remainingText = remember(offerStatus.remainingAdFreeMs, isHindi) {
        RewardedLadderEngine.formatRemainingTime(offerStatus.remainingAdFreeMs, isHindi)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_container"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (offerStatus.isAdFreeActive) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (offerStatus.isAdFreeActive) Icons.Default.CheckCircle else Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = if (offerStatus.isAdFreeActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (offerStatus.isAdFreeActive) {
                            p10Label(
                                isHindi,
                                "विज्ञापन-रहित समय चालू: $remainingText शेष",
                                "Ad-free active: $remainingText left"
                            )
                        } else {
                            p10Label(
                                isHindi,
                                "विज्ञापन-रहित समय पाने के लिए विज्ञापन देखें",
                                "Watch ads to remove ads for a while"
                            )
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val progressLabel = p10Label(
                        isHindi,
                        "प्रगति: ${offerStatus.completedInWindow} / ${offerStatus.maxInWindow} विज्ञापन (आज ${offerStatus.dailyCount}/${offerStatus.maxDailyCount})",
                        "Progress: ${offerStatus.completedInWindow} of ${offerStatus.maxInWindow} ads (today ${offerStatus.dailyCount}/${offerStatus.maxDailyCount})"
                    )
                    Text(
                        text = progressLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = {
                    (offerStatus.completedInWindow.toFloat() / offerStatus.maxInWindow.coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("${testTagPrefix}_progress")
            )

            when {
                offerStatus.canWatchNow -> {
                    val nextHours = offerStatus.nextRewardTotalHours ?: 2
                    val buttonText = if (offerStatus.isAdFreeActive) {
                        p10Label(
                            isHindi,
                            "एक और विज्ञापन देखें (कुल ${nextHours} घंटे विज्ञापन-रहित समय)",
                            "Watch another ad (total ${nextHours}h ad-free)"
                        )
                    } else {
                        p10Label(
                            isHindi,
                            "विज्ञापन देखें (${nextHours} घंटे विज्ञापन-रहित समय)",
                            "Watch an ad (${nextHours}h ad-free)"
                        )
                    }
                    OutlinedButton(
                        onClick = { showExplanationDialog = true },
                        enabled = !isRewardedLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("${testTagPrefix}_watch_button")
                    ) {
                        if (isRewardedLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(p10Label(isHindi, "विज्ञापन लोड हो रहा है...", "Loading ad..."))
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = buttonText)
                        }
                    }
                }

                offerStatus.blockReason == RewardedOfferBlockReason.LADDER_COMPLETE_WAIT_FOR_EXPIRY -> {
                    Text(
                        text = p10Label(
                            isHindi,
                            "4 में से 4 विज्ञापन पूरे हो गए। विज्ञापन-रहित समय समाप्त होने के बाद अगला विकल्प दिखेगा।",
                            "4 of 4 ads completed. More rewarded offers appear after your ad-free time ends."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("${testTagPrefix}_complete_note")
                    )
                }

                offerStatus.blockReason == RewardedOfferBlockReason.DAILY_CAP_REACHED -> {
                    Text(
                        text = p10Label(
                            isHindi,
                            "आज की 8 रिवॉर्ड विज्ञापन सीमा पूरी हो गई। कल फिर देख सकते हैं।",
                            "Daily limit of 8 rewarded ads reached. Available again tomorrow."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("${testTagPrefix}_daily_cap_note")
                    )
                }
            }
        }
    }

    if (showExplanationDialog) {
        RewardedAdExplanationDialog(
            offerStatus = offerStatus,
            isHindi = isHindi,
            onDismiss = { showExplanationDialog = false },
            onStartAd = {
                showExplanationDialog = false
                onWatchConfirmed()
            }
        )
    }
}

@Composable
fun RewardedAdExplanationDialog(
    offerStatus: RewardedOfferStatus,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onStartAd: () -> Unit
) {
    val nextStep = (offerStatus.completedInWindow + 1).coerceIn(1, RewardedLadderEngine.MAX_ADS_PER_LADDER)
    val nextHours = offerStatus.nextRewardTotalHours ?: RewardedLadderEngine.ladderTotalHoursForStep(nextStep)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = p10Label(
                    isHindi,
                    "विज्ञापन-रहित समय पाएं",
                    "Earn ad-free time"
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = p10Label(
                        isHindi,
                        "यह पूरी तरह वैकल्पिक है। पूरा वीडियो विज्ञापन देखने पर ही आपको विज्ञापन-रहित समय मिलेगा — बीच में बंद करने पर कोई समय नहीं मिलेगा।",
                        "This is completely optional. You receive ad-free time only when you finish the full rewarded ad — closing early grants nothing."
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "इस विज्ञापन के बाद (विज्ञापन #$nextStep / 4): पहले विज्ञापन से कुल $nextHours घंटे विज्ञापन-रहित समय।",
                                "After this ad (ad #$nextStep of 4): $nextHours hours ad-free in total from your first ad."
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = p10Label(
                                isHindi,
                                "विज्ञापन-रहित समय सीढ़ी: 1 विज्ञापन = 2 घंटे • 2 = कुल 3 घंटे • 3 = कुल 6 घंटे • 4 = कुल 24 घंटे।",
                                "Ladder: 1 ad = 2h • 2 ads = 3h total • 3 ads = 6h total • 4 ads = 24h total."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStartAd,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("rewarded_dialog_start_button")
            ) {
                Text(p10Label(isHindi, "विज्ञापन शुरू करें", "Start ad"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("rewarded_dialog_cancel_button")
            ) {
                Text(p10Label(isHindi, "अभी नहीं", "Not now"))
            }
        }
    )
}

/**
 * Phase 16B: Organisation-level subscription entry card shown in Settings.
 * - For OWNER: opens the dedicated "Remove ads for my school" subscription screen (replaces Phase 16A "Coming soon").
 * - For non-OWNER members: displays ONLY a plain status line ("Your school is ad-free" or "Ask your school owner to remove ads").
 */
@Composable
fun RemoveAdsForEveryoneCard(
    effectiveSubscription: OrgSubscription,
    isOwner: Boolean,
    isHindi: Boolean,
    modifier: Modifier = Modifier,
    onOpenSubscriptionScreen: (() -> Unit)? = null
) {
    val isEntitled = effectiveSubscription.suppressesAllAds
    val dateLabel = remember(effectiveSubscription.expiresAtMs) {
        SubscriptionPricingEngine.formatSubscriptionDate(effectiveSubscription.expiresAtMs)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("remove_ads_for_everyone_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEntitled) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isEntitled) Icons.Default.Verified else Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = if (isEntitled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isOwner) {
                        p10Label(
                            isHindi,
                            "मेरे स्कूल के लिए विज्ञापन हटाएँ",
                            "Remove ads for my school"
                        )
                    } else if (isEntitled) {
                        p10Label(
                            isHindi,
                            "आपका स्कूल विज्ञापन-रहित है",
                            "Your school is ad-free"
                        )
                    } else {
                        p10Label(
                            isHindi,
                            "स्कूल विज्ञापन स्थिति",
                            "School ads status"
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (!isOwner) {
                Text(
                    text = if (isEntitled) {
                        p10Label(
                            isHindi,
                            "आपका स्कूल विज्ञापन-रहित है — इस संस्था के किसी भी सदस्य को कोई विज्ञापन नहीं दिखेगा।",
                            "Your school is ad-free — no ads are shown to any member of this school."
                        )
                    } else {
                        p10Label(
                            isHindi,
                            "विज्ञापन हटाने के लिए अपने स्कूल ओनर से कहें।",
                            "Ask your school owner to remove ads."
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(
                        if (isEntitled) "member_school_ad_free_text" else "remove_ads_ask_owner_text"
                    )
                )
            } else {
                val statusLine = when (effectiveSubscription.status) {
                    OrgSubscriptionStatus.ACTIVE -> if (dateLabel.isNotBlank()) {
                        p10Label(
                            isHindi,
                            "सब्सक्रिप्शन सक्रिय • अगला नवीनीकरण: $dateLabel",
                            "Subscription active • Next renewal: $dateLabel"
                        )
                    } else {
                        p10Label(isHindi, "सब्सक्रिप्शन सक्रिय — पूरा स्कूल विज्ञापन-रहित है।", "Subscription active — your school is ad-free.")
                    }
                    OrgSubscriptionStatus.GRACE -> p10Label(
                        isHindi,
                        "ग्रेस अवधि चालू — भुगतान अपडेट करें; विज्ञापन अभी बंद हैं।",
                        "Grace period active — update payment in Google Play; ads remain off."
                    )
                    OrgSubscriptionStatus.ON_HOLD -> p10Label(
                        isHindi,
                        "खाता होल्ड पर — Google Play में भुगतान ठीक करें; विज्ञापन अभी बंद हैं।",
                        "Account on hold — fix payment in Google Play; ads remain off."
                    )
                    OrgSubscriptionStatus.CANCELLED_ACTIVE -> if (dateLabel.isNotBlank()) {
                        p10Label(
                            isHindi,
                            "रद्द किया गया • $dateLabel तक विज्ञापन-रहित चालू रहेगा।",
                            "Cancelled • Ad-free remains valid until $dateLabel."
                        )
                    } else {
                        p10Label(
                            isHindi,
                            "रद्द किया गया • वर्तमान अवधि समाप्त होने तक विज्ञापन-रहित।",
                            "Cancelled • Ad-free remains valid until end of current period."
                        )
                    }
                    OrgSubscriptionStatus.EXPIRED -> p10Label(
                        isHindi,
                        "सब्सक्रिप्शन समाप्त हो गया है — दोबारा शुरू करने के लिए प्लान देखें।",
                        "Subscription expired — open plans to renew for your school."
                    )
                    OrgSubscriptionStatus.REVOKED -> p10Label(
                        isHindi,
                        "सब्सक्रिप्शन रिफंड या निरस्त हो गया है — विज्ञापन फिर से चालू हैं।",
                        "Subscription was refunded or revoked — ads are active again."
                    )
                    OrgSubscriptionStatus.NONE -> p10Label(
                        isHindi,
                        "एक प्लान से स्कूल के सभी शिक्षकों और स्टाफ के लिए सभी विज्ञापन हटाएँ। सभी काम के फ़ीचर हमेशा मुफ्त रहेंगे।",
                        "Remove all ads for every teacher and staff member with one school plan. All work features stay free forever."
                    )
                }

                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("owner_subscription_status_summary")
                )

                if (onOpenSubscriptionScreen != null) {
                    if (isEntitled) {
                        OutlinedButton(
                            onClick = onOpenSubscriptionScreen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("open_school_subscription_button")
                        ) {
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "सब्सक्रिप्शन देखें और प्रबंधित करें",
                                    "View & manage subscription"
                                )
                            )
                        }
                    } else {
                        Button(
                            onClick = onOpenSubscriptionScreen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("open_school_subscription_button")
                        ) {
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "मेरे स्कूल के लिए विज्ञापन हटाएँ",
                                    "Remove ads for my school"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Short dismissible notice shown ONLY to the Organisation Owner when the school's subscription
 * has lapsed (expired or revoked), giving one-tap access to renew without blocking work.
 */
@Composable
fun OwnerLapsedSubscriptionNoticeBanner(
    subscription: OrgSubscription,
    isHindi: Boolean,
    onOpenSubscriptionScreen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("owner_lapsed_subscription_notice"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (subscription.status == OrgSubscriptionStatus.REVOKED) {
                        p10Label(
                            isHindi,
                            "स्कूल का विज्ञापन-रहित प्लान निरस्त/रिफंड हो गया",
                            "School ad-free subscription was revoked"
                        )
                    } else {
                        p10Label(
                            isHindi,
                            "स्कूल का विज्ञापन-रहित प्लान समाप्त हो गया",
                            "School ad-free subscription expired"
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = p10Label(
                    isHindi,
                    "आपके स्कूल का सारा डेटा और सभी काम के फ़ीचर पहले की तरह चालू हैं। विज्ञापन दोबारा बंद करने के लिए प्लान रिन्यू करें।",
                    "All your school data and work features remain fully unlocked. Renew anytime to remove ads again."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSubscriptionScreen,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("lapsed_notice_renew_button")
                ) {
                    Text(p10Label(isHindi, "प्लान देखें", "View plans"))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("lapsed_notice_dismiss_button")
                ) {
                    Text(p10Label(isHindi, "बाद में", "Later"))
                }
            }
        }
    }
}

/**
 * Settings entry & dialog for "Privacy and ad choices" (Google UMP consent + Android ad opt-out guidance).
 */
@Composable
fun PrivacyAndAdChoicesDialog(
    isHindi: Boolean,
    isPrivacyOptionsRequired: Boolean,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivityOrNull() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.PrivacyTip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = p10Label(isHindi, "गोपनीयता और विज्ञापन विकल्प", "Privacy and ad choices"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = p10Label(
                        isHindi,
                        "हम कभी भी छात्र, अभिभावक, स्टाफ या स्कूल का डेटा विज्ञापन SDK को नहीं भेजते। विज्ञापन केवल होम, संस्था/स्कूल चयन और सूची/टाइमटेबल देखने की स्क्रीन पर ही दिखते हैं — डेटा एंट्री, फीस, अंक या PDF के अंदर कभी नहीं।",
                        "We never share student, guardian, staff, or school data with the ad SDK. Ads appear only on Today home, chooser, and list/timetable view screens — never on data entry, fees, marks, or inside PDFs."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider()
                Text(
                    text = p10Label(
                        isHindi,
                        "व्यक्तिगत विज्ञापन बंद करने के लिए: फोन की Settings → Google → Ads → Delete advertising ID या Opt out of Ads Personalization चुनें।",
                        "To turn off personalised ads: open Phone Settings → Google → Ads → Delete advertising ID or opt out of ad personalisation."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isPrivacyOptionsRequired) {
                    Button(
                        onClick = {
                            Phase16AdMobController.showPrivacyOptionsForm(activity) { shown, errMsg ->
                                if (shown) {
                                    onFeedback(
                                        errMsg ?: p10Label(
                                            isHindi,
                                            "विज्ञापन सहमति विकल्प अपडेट किए गए।",
                                            "Ad consent choices updated."
                                        )
                                    )
                                } else {
                                    onFeedback(
                                        p10Label(
                                            isHindi,
                                            "सहमति फॉर्म अभी उपलब्ध नहीं है।",
                                            "Consent form is not required in your region right now."
                                        )
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("ump_privacy_options_button")
                    ) {
                        Text(p10Label(isHindi, "सहमति विकल्प बदलें (UMP)", "Manage consent choices"))
                    }
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Throwable) {
                            try {
                                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(fallback)
                            } catch (_: Throwable) {
                                onFeedback(
                                    p10Label(
                                        isHindi,
                                        "कृपया फोन की Settings → Google → Ads खोलें।",
                                        "Please open Phone Settings → Google → Ads."
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("open_device_ad_settings_button")
                ) {
                    Text(p10Label(isHindi, "फोन गोपनीयता सेटिंग्स खोलें", "Open phone privacy settings"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "बंद करें", "Close"))
            }
        }
    )
}

/**
 * Debug-only developer switch for testing subscription ON/OFF and 24h install grace locally.
 * Strictly compiled/rendered ONLY when BuildConfig.DEBUG is true; does NOT exist in release builds.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugAdsSimulationSection(
    isHindi: Boolean,
    currentSimulation: DebugSubscriptionSimulation,
    bypassInstallGrace: Boolean,
    onSimulationChanged: (DebugSubscriptionSimulation) -> Unit,
    onBypassInstallGraceChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.DEBUG) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("debug_ads_simulation_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = p10Label(
                        isHindi,
                        "डेवलपर विज्ञापन टेस्ट (केवल Debug बिल्ड)",
                        "Developer Ads Testing (Debug build only)"
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = p10Label(
                    isHindi,
                    "केवल स्थानीय परीक्षण के लिए सब्सक्रिप्शन और 24-घंटे इंस्टॉल नियम सिमुलेट करें। रिलीज़ बिल्ड में यह विकल्प नहीं होता।",
                    "Locally simulate subscription on/off and first-24h install grace for QA. Stripped from release builds."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = currentSimulation == DebugSubscriptionSimulation.FOLLOW_SERVER,
                    onClick = { onSimulationChanged(DebugSubscriptionSimulation.FOLLOW_SERVER) },
                    label = { Text(p10Label(isHindi, "सर्वर अनुसार", "Follow server")) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("debug_sub_follow_server")
                )
                FilterChip(
                    selected = currentSimulation == DebugSubscriptionSimulation.FORCE_ACTIVE,
                    onClick = { onSimulationChanged(DebugSubscriptionSimulation.FORCE_ACTIVE) },
                    label = { Text(p10Label(isHindi, "सब्सक्रिप्शन ON", "Simulate Sub ON")) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("debug_sub_force_active")
                )
                FilterChip(
                    selected = currentSimulation == DebugSubscriptionSimulation.FORCE_FREE,
                    onClick = { onSimulationChanged(DebugSubscriptionSimulation.FORCE_FREE) },
                    label = { Text(p10Label(isHindi, "सब्सक्रिप्शन OFF", "Simulate Sub OFF")) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("debug_sub_force_free")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = p10Label(
                        isHindi,
                        "24 घंटे नया-इंस्टॉल इंतजार छोड़ें",
                        "Bypass 24h first-install grace"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = bypassInstallGrace,
                    onCheckedChange = onBypassInstallGraceChanged,
                    modifier = Modifier
                        .semantics { contentDescription = "Bypass 24h first install grace" }
                        .testTag("debug_bypass_install_grace_switch")
                )
            }
        }
    }
}
