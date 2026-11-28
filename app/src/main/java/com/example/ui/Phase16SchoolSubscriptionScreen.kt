package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.billing.Phase16PlayBillingManager
import com.example.domain.OrgSubscription
import com.example.domain.OrgSubscriptionStatus
import com.example.domain.Phase16BillingConstants
import com.example.domain.PlayBillingUiSnapshot
import com.example.domain.PlaySubscriptionPlanOffer
import com.example.domain.SubscriptionPricingEngine
import com.example.domain.YearlySavingsSummary

/**
 * Phase 16B: Owner-only school subscription screen ("Settings → Remove ads for my school").
 * - Strictly NO ads appear on this screen (`AdScreenSurface.SUBSCRIPTION` is forbidden).
 * - Reads real localised prices & billing periods from Google Play — NEVER hard-coded.
 * - Mobile-first stacked layout: zero clipping at 320dp with 1.3x and 1.5x font scales, all targets >= 48.dp.
 * - Non-owners see ONLY a plain status line ("Your school is ad-free" or "Ask your school owner to remove ads").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase16SchoolSubscriptionScreen(
    orgName: String,
    isOwner: Boolean,
    isHindi: Boolean,
    isOnline: Boolean,
    subscription: OrgSubscription,
    billingState: PlayBillingUiSnapshot,
    onRefreshOffers: () -> Unit,
    onSubscribePlan: (PlaySubscriptionPlanOffer) -> Unit,
    onRestorePurchase: () -> Unit,
    onOpenPrivacyAndTerms: () -> Unit,
    onToggleLanguage: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    LaunchedEffect(isOwner) {
        if (isOwner) {
            onRefreshOffers()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = p10Label(
                            isHindi,
                            "मेरे स्कूल के लिए विज्ञापन हटाएँ",
                            "Remove ads for my school"
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("subscription_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = p10Label(isHindi, "वापस", "Back")
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onToggleLanguage,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("subscription_language_toggle")
                    ) {
                        Text(if (isHindi) "EN" else "हिन्दी")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isOwner) {
                    NonOwnerSubscriptionStatusCard(
                        orgName = orgName,
                        subscription = subscription,
                        isHindi = isHindi
                    )
                    return@Column
                }

                // 1. Current Subscription Status Card
                OwnerSubscriptionStatusCard(
                    orgName = orgName,
                    subscription = subscription,
                    isHindi = isHindi
                )

                // 2. Pending Payment / Offline Queued / Live Feedback Banners
                if (billingState.isPendingPayment) {
                    SubscriptionInfoBanner(
                        icon = Icons.Default.Schedule,
                        title = p10Label(isHindi, "भुगतान लंबित है", "Payment pending"),
                        body = p10Label(
                            isHindi,
                            "Google Play से भुगतान की पुष्टि होते ही पूरे स्कूल के लिए विज्ञापन अपने आप बंद हो जाएंगे।",
                            "As soon as Google Play confirms your payment, ads will turn off automatically for your school."
                        ),
                        testTag = "subscription_pending_banner"
                    )
                }

                if (billingState.hasQueuedOfflineVerification) {
                    SubscriptionInfoBanner(
                        icon = Icons.Default.CloudOff,
                        title = p10Label(isHindi, "ऑनलाइन होने पर पुष्टि होगी", "Waiting to verify online"),
                        body = p10Label(
                            isHindi,
                            "आपकी खरीद सुरक्षित है। इंटरनेट जुड़ते ही सर्वर इसकी पुष्टि कर देगा। आप नीचे 'खरीद वापस लाएँ' भी दबा सकते हैं।",
                            "Your purchase is saved. As soon as you are back online it will verify automatically, or tap Restore purchase below."
                        ),
                        testTag = "subscription_offline_queue_banner"
                    )
                }

                billingState.statusMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                    SubscriptionInfoBanner(
                        icon = Icons.Default.CheckCircle,
                        title = p10Label(isHindi, "अपडेट", "Update"),
                        body = msg,
                        testTag = "subscription_status_message"
                    )
                }

                billingState.errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_error_banner"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                        )
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // 3. What You Get Card (Clear Hindi & English explanation)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subscription_benefits_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "इस सब्सक्रिप्शन में क्या मिलता है?",
                                "What this subscription includes"
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        BenefitBullet(
                            text = p10Label(
                                isHindi,
                                "स्कूल के हर सदस्य (ओनर, एडमिन, शिक्षक, क्लर्क, स्टाफ) के लिए कोई बैनर विज्ञापन नहीं।",
                                "No banner ads for any member of your school (owner, admin, teachers, clerks, office staff)."
                            )
                        )
                        BenefitBullet(
                            text = p10Label(
                                isHindi,
                                "कोई फुल-स्क्रीन (इंटरस्टीशियल) विज्ञापन नहीं।",
                                "No full-screen (interstitial) ads."
                            )
                        )
                        BenefitBullet(
                            text = p10Label(
                                isHindi,
                                "कोई रिवॉर्ड ऑफर या 'विज्ञापन देखें' बटन नहीं दिखेगा।",
                                "No rewarded offers and no 'Watch an ad' buttons anywhere."
                            )
                        )
                        BenefitBullet(
                            text = p10Label(
                                isHindi,
                                "सभी काम के फ़ीचर (छात्र, फीस, अंक, रिपोर्ट कार्ड, उपस्थिति, टाइमटेबल, वेतन, PDF) हमेशा मुफ्त हैं — सब्सक्रिप्शन हो या न हो, कोई फ़ीचर कभी लॉक नहीं होता।",
                                "All school work features stay 100% free and are never locked, with or without this subscription."
                            )
                        )
                    }
                }

                // 4. Two Plans from Google Play (Dynamic prices ONLY — never hard-coded)
                Text(
                    text = p10Label(isHindi, "अपना प्लान चुनें (Google Play)", "Choose a plan (Google Play)"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("subscription_plans_header")
                )

                if (billingState.isLoadingProducts) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_loading_card"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "Google Play से वर्तमान कीमतें लोड हो रही हैं...",
                                    "Loading live prices from Google Play..."
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (billingState.monthlyOffer == null && billingState.yearlyOffer == null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_plans_unavailable_card"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (!isOnline) {
                                    p10Label(
                                        isHindi,
                                        "प्लान और कीमतें देखने के लिए इंटरनेट चालू करें।",
                                        "Connect to the internet to load live prices from Google Play."
                                    )
                                } else if (!billingState.isPlayBillingAvailable) {
                                    p10Label(
                                        isHindi,
                                        "इस डिवाइस पर Google Play Billing उपलब्ध नहीं है।",
                                        "Google Play Billing is not available on this device."
                                    )
                                } else {
                                    p10Label(
                                        isHindi,
                                        "अभी Google Play से प्लान लोड नहीं हो सके। कृपया पुनः प्रयास करें।",
                                        "Plans could not be loaded from Google Play right now. Please try again."
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onRefreshOffers,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("subscription_retry_plans_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(p10Label(isHindi, "कीमतें फिर से लोड करें", "Reload plans"))
                            }
                        }
                    }
                } else {
                    billingState.yearlyOffer?.let { yearly ->
                        SubscriptionPlanCard(
                            offer = yearly,
                            isCurrentActivePlan = subscription.suppressesAllAds &&
                                subscription.plan.equals(Phase16BillingConstants.BASE_PLAN_YEARLY, ignoreCase = true),
                            yearlySavings = billingState.yearlySavings,
                            isHindi = isHindi,
                            isBusy = billingState.isVerifyingPurchase,
                            isOnline = isOnline,
                            onSubscribe = { onSubscribePlan(yearly) },
                            testTagPrefix = "plan_yearly"
                        )
                    }
                    billingState.monthlyOffer?.let { monthly ->
                        SubscriptionPlanCard(
                            offer = monthly,
                            isCurrentActivePlan = subscription.suppressesAllAds &&
                                subscription.plan.equals(Phase16BillingConstants.BASE_PLAN_MONTHLY, ignoreCase = true),
                            yearlySavings = null,
                            isHindi = isHindi,
                            isBusy = billingState.isVerifyingPurchase,
                            isOnline = isOnline,
                            onSubscribe = { onSubscribePlan(monthly) },
                            testTagPrefix = "plan_monthly"
                        )
                    }
                }

                // 5. Manage Subscription & Restore Purchase Actions (stacked, >= 48.dp)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { Phase16PlayBillingManager.openPlayManageSubscriptions(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("manage_subscription_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(p10Label(isHindi, "सब्सक्रिप्शन प्रबंधित करें (Google Play)", "Manage subscription"))
                    }

                    OutlinedButton(
                        onClick = onRestorePurchase,
                        enabled = !billingState.isVerifyingPurchase,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("restore_purchase_button")
                    ) {
                        if (billingState.isVerifyingPurchase) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(p10Label(isHindi, "पुष्टि हो रही है...", "Verifying purchase..."))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(p10Label(isHindi, "खरीद वापस लाएँ", "Restore purchase"))
                        }
                    }
                }

                HorizontalDivider()

                // 6. Required Google Play Subscription Terms + Privacy Policy & Terms links
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subscription_terms_section"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = p10Label(isHindi, "सब्सक्रिप्शन की शर्तें", "Subscription terms"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = p10Label(
                            isHindi,
                            "• भुगतान आपके Google Play खाते से लिया जाता है।\n" +
                                "• अवधि समाप्त होने से पहले रद्द न करने पर सब्सक्रिप्शन अपने आप रिन्यू हो जाता है।\n" +
                                "• आप कभी भी Google Play → Payments & subscriptions में जाकर इसे रद्द या बदल सकते हैं।\n" +
                                "• एक खरीद केवल एक ही स्कूल/संस्था के लिए मान्य होती है। ओनर बदलने पर भी अवधि तक लाभ स्कूल के साथ जुड़ा रहता है।\n" +
                                "• रिफंड Google Play की मानक नीति के अनुसार होते हैं।",
                            "• Payment is charged to your Google Play account.\n" +
                                "• Subscription renews automatically unless cancelled before the end of the current billing period.\n" +
                                "• You can cancel or manage anytime in Google Play → Payments & subscriptions.\n" +
                                "• Each purchase applies to one school organisation only and stays attached to that organisation.\n" +
                                "• Refunds follow standard Google Play policy."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onOpenPrivacyAndTerms,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("subscription_privacy_policy_link")
                        ) {
                            Text(p10Label(isHindi, "गोपनीयता नीति और शर्तें", "Privacy Policy & Terms"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NonOwnerSubscriptionStatusCard(
    orgName: String,
    subscription: OrgSubscription,
    isHindi: Boolean
) {
    val entitled = subscription.suppressesAllAds
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("non_owner_subscription_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entitled) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = orgName.ifBlank { p10Label(isHindi, "आपका स्कूल", "Your school") },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (entitled) {
                    p10Label(isHindi, "आपका स्कूल विज्ञापन-रहित है।", "Your school is ad-free.")
                } else {
                    p10Label(
                        isHindi,
                        "विज्ञापन हटाने के लिए अपने स्कूल ओनर से कहें।",
                        "Ask your school owner to remove ads."
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("non_owner_status_line")
            )
        }
    }
}

@Composable
private fun OwnerSubscriptionStatusCard(
    orgName: String,
    subscription: OrgSubscription,
    isHindi: Boolean
) {
    val entitled = subscription.suppressesAllAds
    val dateLabel = remember(subscription.expiresAtMs) {
        SubscriptionPricingEngine.formatSubscriptionDate(subscription.expiresAtMs)
    }

    val badgeText = when (subscription.status) {
        OrgSubscriptionStatus.ACTIVE -> p10Label(isHindi, "सक्रिय (Active)", "Active")
        OrgSubscriptionStatus.GRACE -> p10Label(isHindi, "ग्रेस अवधि (Grace)", "Grace period")
        OrgSubscriptionStatus.ON_HOLD -> p10Label(isHindi, "होल्ड पर (On hold)", "On hold")
        OrgSubscriptionStatus.CANCELLED_ACTIVE -> p10Label(isHindi, "रद्द — अभी मान्य", "Cancelled (still active)")
        OrgSubscriptionStatus.EXPIRED -> p10Label(isHindi, "समाप्त (Expired)", "Expired")
        OrgSubscriptionStatus.REVOKED -> p10Label(isHindi, "निरस्त / रिफंड", "Revoked / refunded")
        OrgSubscriptionStatus.NONE -> p10Label(isHindi, "मुफ्त (विज्ञापन सहित)", "Free (with ads)")
    }

    val planLabel = when (subscription.plan.lowercase()) {
        Phase16BillingConstants.BASE_PLAN_YEARLY -> p10Label(isHindi, "वार्षिक प्लान", "Yearly plan")
        Phase16BillingConstants.BASE_PLAN_MONTHLY -> p10Label(isHindi, "मासिक प्लान", "Monthly plan")
        else -> ""
    }

    val detailText = when (subscription.status) {
        OrgSubscriptionStatus.ACTIVE -> if (dateLabel.isNotBlank()) {
            p10Label(
                isHindi,
                "आपके स्कूल के सभी सदस्यों के लिए विज्ञापन बंद हैं। अगला नवीनीकरण: $dateLabel",
                "Ads are off for all members of your school. Next renewal: $dateLabel"
            )
        } else {
            p10Label(isHindi, "आपके स्कूल के सभी सदस्यों के लिए विज्ञापन बंद हैं।", "Ads are off for all members of your school.")
        }
        OrgSubscriptionStatus.GRACE -> p10Label(
            isHindi,
            "नवीनीकरण भुगतान पूरा नहीं हुआ। कृपया Google Play में भुगतान विधि अपडेट करें — ग्रेस अवधि में विज्ञापन बंद रहेंगे।",
            "Renewal payment failed. Please update your payment method in Google Play — ads stay off during grace period."
        )
        OrgSubscriptionStatus.ON_HOLD -> p10Label(
            isHindi,
            "सब्सक्रिप्शन भुगतान होल्ड पर है। Google Play में भुगतान ठीक करें ताकि विज्ञापन-रहित लाभ जारी रहे।",
            "Subscription is on hold. Fix payment in Google Play to keep your school ad-free."
        )
        OrgSubscriptionStatus.CANCELLED_ACTIVE -> if (dateLabel.isNotBlank()) {
            p10Label(
                isHindi,
                "ऑटो-रिन्यूअल बंद है, लेकिन $dateLabel तक आपके स्कूल के लिए विज्ञापन बंद रहेंगे।",
                "Auto-renewal is off, and your school remains ad-free until $dateLabel."
            )
        } else {
            p10Label(
                isHindi,
                "ऑटो-रिन्यूअल बंद है; वर्तमान बिलिंग अवधि समाप्त होने तक विज्ञापन बंद रहेंगे।",
                "Auto-renewal is off; ads stay off until the current billing period ends."
            )
        }
        OrgSubscriptionStatus.EXPIRED -> if (dateLabel.isNotBlank()) {
            p10Label(
                isHindi,
                "आपका सब्सक्रिप्शन $dateLabel को समाप्त हो गया। दोबारा विज्ञापन बंद करने के लिए नीचे प्लान चुनें।",
                "Your subscription expired on $dateLabel. Choose a plan below to renew."
            )
        } else {
            p10Label(isHindi, "आपका सब्सक्रिप्शन समाप्त हो गया है। नीचे प्लान चुनकर फिर से शुरू करें।", "Your subscription has expired. Choose a plan below to renew.")
        }
        OrgSubscriptionStatus.REVOKED -> p10Label(
            isHindi,
            "पिछला सब्सक्रिप्शन रिफंड या निरस्त हो गया है। स्कूल के लिए विज्ञापन हटाने हेतु नया प्लान चुनें।",
            "The previous subscription was refunded or revoked. Choose a plan below to subscribe again."
        )
        OrgSubscriptionStatus.NONE -> p10Label(
            isHindi,
            "अभी कोई सक्रिय स्कूल सब्सक्रिप्शन नहीं है।",
            "No active school subscription yet."
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("owner_subscription_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entitled) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
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
                    imageVector = if (entitled) Icons.Default.Verified else Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = if (entitled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = orgName.ifBlank { p10Label(isHindi, "मेरा स्कूल", "My school") },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (planLabel.isNotBlank()) "$badgeText • $planLabel" else badgeText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (entitled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("owner_subscription_status_badge")
                    )
                }
            }
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubscriptionPlanCard(
    offer: PlaySubscriptionPlanOffer,
    isCurrentActivePlan: Boolean,
    yearlySavings: YearlySavingsSummary?,
    isHindi: Boolean,
    isBusy: Boolean,
    isOnline: Boolean,
    onSubscribe: () -> Unit,
    testTagPrefix: String
) {
    val title = if (offer.isYearly) {
        p10Label(isHindi, "वार्षिक प्लान (Yearly)", "Yearly plan")
    } else {
        p10Label(isHindi, "मासिक प्लान (Monthly)", "Monthly plan")
    }
    val periodLabel = remember(offer.billingPeriodIso, isHindi) {
        SubscriptionPricingEngine.formatBillingPeriodLabel(offer.billingPeriodIso, isHindi)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (offer.isYearly) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (offer.isYearly) 1.5.dp else 1.dp,
            color = if (offer.isYearly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${offer.formattedPrice} $periodLabel",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("${testTagPrefix}_price")
            )

            if (offer.isYearly && yearlySavings != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "सालाना ${yearlySavings.formattedSavings} बचाएँ (${yearlySavings.savingsPercent}% बचत)",
                            "Save ${yearlySavings.formattedSavings} per year (${yearlySavings.savingsPercent}% off)"
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("yearly_savings_badge")
                    )
                }
            }

            Button(
                onClick = onSubscribe,
                enabled = !isBusy && isOnline && !isCurrentActivePlan,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("${testTagPrefix}_subscribe_button")
            ) {
                Text(
                    text = when {
                        isCurrentActivePlan -> p10Label(isHindi, "वर्तमान सक्रिय प्लान", "Current active plan")
                        !isOnline -> p10Label(isHindi, "खरीदने के लिए ऑनलाइन हों", "Go online to subscribe")
                        else -> p10Label(isHindi, "सब्सक्राइब करें", "Subscribe")
                    }
                )
            }
        }
    }
}

@Composable
private fun SubscriptionInfoBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun BenefitBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
