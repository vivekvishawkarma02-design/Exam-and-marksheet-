package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.BuildConfig
import com.example.domain.LegalConfig
import com.example.domain.Phase6DateUtils
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.GlassBackdropCanvas
import com.example.ui.designsystem.GlassCard
import com.example.ui.designsystem.GlassIntensity
import com.example.ui.designsystem.GlassListItem
import com.example.ui.designsystem.GlassTopBar
import com.example.ui.designsystem.SectionHeader
import com.example.ui.theme.AppTheme

@Composable
fun SectionHomePage(
    section: NavSection,
    uiState: SchoolUiState,
    onNavigateToRoute: (String) -> Unit,
    onOpenLegalDoc: (LegalDocType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isHindi = uiState.phase10HindiLabels
    val spacing = AppTheme.spacing
    val colors = AppTheme.colors

    val cards = remember(section, uiState) {
        AppNavigationRegistry.cardsForSection(section, uiState)
    }
    val groupedCards = remember(cards) {
        cards.groupBy { it.group }
    }

    val sectionTitle = if (isHindi) section.titleHi else section.titleEn
    val sectionSubtitle = when (section) {
        NavSection.HOME -> ""
        NavSection.STUDENTS -> p10Label(
            isHindi,
            "${uiState.activeSchool.name} • सत्र ${uiState.filter.session}",
            "${uiState.activeSchool.name} • Session ${uiState.filter.session}"
        )
        NavSection.ACADEMICS -> p10Label(
            isHindi,
            "कक्षा ${uiState.effectiveClassName.ifBlank { "—" }}-${uiState.filter.section} • ${uiState.filter.session}",
            "Class ${uiState.effectiveClassName.ifBlank { "—" }}-${uiState.filter.section} • ${uiState.filter.session}"
        )
        NavSection.FINANCE_OPS -> p10Label(
            isHindi,
            "${uiState.activeSchool.name} • शुल्क, रोकड़ व संचालन",
            "${uiState.activeSchool.name} • Fees, Cash & Operations"
        )
        NavSection.MANAGE -> p10Label(
            isHindi,
            uiState.activeOrg?.name ?: "संगठन प्रबंधन व सेटिंग्स",
            uiState.activeOrg?.name ?: "Organisation management & settings"
        )
    }

    GlassBackdropCanvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("section_page_${section.id}")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = sectionTitle,
                subtitle = sectionSubtitle,
                navigationIcon = null,
                onNavigationClick = null,
                applyStatusBarInsets = false,
                modifier = Modifier.testTag("section_top_bar_${section.id}")
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("section_list_${section.id}"),
                contentPadding = PaddingValues(
                    horizontal = spacing.lg,
                    vertical = spacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                if (cards.isEmpty() && section != NavSection.MANAGE) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Lock,
                            title = p10Label(
                                isHindi,
                                "इस अनुभाग में कोई मॉड्यूल उपलब्ध नहीं है",
                                "No modules available in $sectionTitle"
                            ),
                            message = p10Label(
                                isHindi,
                                "यदि आपको इस अनुभाग की आवश्यकता है तो अपने संगठन एडमिन से अनुमति मांगें।",
                                "Ask your organisation admin if you need access to modules in this section."
                            ),
                            modifier = Modifier.testTag("section_empty_${section.id}")
                        )
                    }
                } else {
                    groupedCards.forEach { (group, groupItems) ->
                        if (group != null) {
                            item(key = "header_${group.name}") {
                                SectionHeader(
                                    title = if (isHindi) group.titleHi else group.titleEn,
                                    modifier = Modifier.padding(top = spacing.xs)
                                )
                            }
                        }

                        items(
                            items = groupItems,
                            key = { "card_${it.route}" }
                        ) { routeInfo ->
                            val canEdit = routeInfo.canEditResolver(uiState)
                            val title = if (isHindi) routeInfo.titleHi else routeInfo.titleEn
                            val description = when {
                                isHindi && canEdit -> routeInfo.editDescriptionHi.ifBlank { routeInfo.viewOnlyDescriptionHi }
                                isHindi -> routeInfo.viewOnlyDescriptionHi.ifBlank { routeInfo.editDescriptionHi }
                                canEdit -> routeInfo.editDescriptionEn.ifBlank { routeInfo.viewOnlyDescriptionEn }
                                else -> routeInfo.viewOnlyDescriptionEn.ifBlank { routeInfo.editDescriptionEn }
                            }
                            val badge = routeInfo.statusBadge(uiState)

                            GlassListItem(
                                headline = title,
                                supportingText = description.ifBlank { null },
                                overlineText = if (!canEdit && routeInfo.controllingModule != null) {
                                    p10Label(isHindi, "केवल देखने की अनुमति", "View only")
                                } else null,
                                badgeText = badge?.first,
                                isWarning = badge?.second == true,
                                leadingIcon = routeInfo.icon,
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary
                                    )
                                },
                                onClick = { onNavigateToRoute(routeInfo.route) },
                                modifier = Modifier.testTag(routeInfo.testTag)
                            )
                        }
                    }

                    if (section == NavSection.MANAGE) {
                        item(key = "manage_about_header") {
                            SectionHeader(
                                title = p10Label(isHindi, "ऐप जानकारी व कानूनी", "About & Legal"),
                                modifier = Modifier.padding(top = spacing.xs)
                            )
                        }

                        item(key = "manage_about_version_card") {
                            GlassCard(
                                intensity = GlassIntensity.Subtle,
                                modifier = Modifier.testTag("manage_about_summary_card")
                            ) {
                                Text(
                                    text = p10Label(isHindi, "स्कूल रिपोर्ट कार्ड", "School Report Card"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • ${LegalConfig.CONTACT_EMAIL_PLACEHOLDER}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        item(key = "manage_legal_privacy") {
                            GlassListItem(
                                headline = p10Label(isHindi, "गोपनीयता नीति", "Privacy Policy"),
                                supportingText = p10Label(isHindi, "डेटा सुरक्षा व ऑफ़लाइन स्टोरेज नीति", "Data protection & offline storage policy"),
                                leadingIcon = Icons.Default.PrivacyTip,
                                onClick = { onOpenLegalDoc(LegalDocType.PRIVACY_POLICY) },
                                modifier = Modifier.testTag("manage_legal_privacy")
                            )
                        }

                        item(key = "manage_legal_terms") {
                            GlassListItem(
                                headline = p10Label(isHindi, "सेवा की शर्तें", "Terms of Service"),
                                supportingText = p10Label(isHindi, "उपयोग की शर्तें व नियम", "Terms of use & organisation responsibilities"),
                                leadingIcon = Icons.Default.Gavel,
                                onClick = { onOpenLegalDoc(LegalDocType.TERMS_OF_SERVICE) },
                                modifier = Modifier.testTag("manage_legal_terms")
                            )
                        }

                        item(key = "manage_legal_support") {
                            GlassListItem(
                                headline = p10Label(isHindi, "सहायता व संपर्क", "Support & Help"),
                                supportingText = p10Label(isHindi, "सहायता ईमेल व मार्गदर्शन", "Contact support & troubleshooting guide"),
                                leadingIcon = Icons.Default.HelpOutline,
                                onClick = { onOpenLegalDoc(LegalDocType.SUPPORT) },
                                modifier = Modifier.testTag("manage_legal_support")
                            )
                        }

                        item(key = "manage_legal_delete") {
                            GlassListItem(
                                headline = p10Label(isHindi, "खाता हटाने की जानकारी", "Delete Account Info"),
                                supportingText = p10Label(isHindi, "स्थायी खाता विलोपन प्रक्रिया देखें", "Review permanent account deletion policy"),
                                leadingIcon = Icons.Default.DeleteOutline,
                                onClick = { onOpenLegalDoc(LegalDocType.DELETE_ACCOUNT) },
                                modifier = Modifier.testTag("manage_legal_delete")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyExamDutiesScreen(
    uiState: SchoolUiState,
    onBack: () -> Unit,
    todayIsoOverride: String? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val isHindi = uiState.phase10HindiLabels
    val spacing = AppTheme.spacing
    val todayIso = remember(todayIsoOverride) { todayIsoOverride ?: Phase6DateUtils.todayIsoDate() }
    val duties = uiState.ownExamDuties
    val upcomingDuties = remember(duties, todayIso) {
        duties.filter { it.dateIso >= todayIso }
    }
    val pastDuties = remember(duties, todayIso) {
        duties.filter { it.dateIso < todayIso }
    }

    GlassBackdropCanvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("my_exam_duties_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = p10Label(isHindi, "मेरी परीक्षा ड्यूटी", "My Exam Duties"),
                subtitle = p10Label(
                    isHindi,
                    "${duties.size} कक्ष निरीक्षक ड्यूटी",
                    "${duties.size} invigilator duty slot(s)"
                ),
                onNavigationClick = onBack,
                applyStatusBarInsets = false,
                modifier = Modifier.testTag("my_exam_duties_top_bar")
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = spacing.lg,
                    vertical = spacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                if (duties.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.EventNote,
                            title = p10Label(isHindi, "कोई परीक्षा ड्यूटी नहीं", "No Exam Duties Assigned"),
                            message = p10Label(
                                isHindi,
                                "जब परीक्षा समन्वयक आपको कक्ष निरीक्षक ड्यूटी देंगे, तो वह यहाँ दिखेगी।",
                                "Assigned invigilation rooms and timings will appear here automatically."
                            ),
                            modifier = Modifier.testTag("my_exam_duties_empty")
                        )
                    }
                } else {
                    if (upcomingDuties.isNotEmpty()) {
                        item(key = "upcoming_header") {
                            SectionHeader(
                                title = p10Label(isHindi, "आगामी परीक्षा ड्यूटी", "Upcoming Duties"),
                                subtitle = p10Label(
                                    isHindi,
                                    "${upcomingDuties.size} ड्यूटी शेष",
                                    "${upcomingDuties.size} scheduled"
                                ),
                                leadingIcon = Icons.Default.AssignmentInd
                            )
                        }
                        items(upcomingDuties, key = { "up_${it.id}" }) { duty ->
                            val isToday = duty.dateIso == todayIso
                            GlassListItem(
                                headline = "${duty.examTitle} — ${duty.roomName}",
                                supportingText = "${duty.dateIso} • ${duty.startTime} – ${duty.endTime}",
                                overlineText = if (duty.assignedManually) {
                                    p10Label(isHindi, "मैनुअल आवंटन", "Assigned manually")
                                } else {
                                    p10Label(isHindi, "ऑटो आवंटन", "Scheduled duty")
                                },
                                badgeText = if (isToday) p10Label(isHindi, "आज", "TODAY") else duty.dateIso,
                                isWarning = isToday,
                                leadingIcon = Icons.Default.EventNote,
                                modifier = Modifier.testTag("my_exam_duty_item_${duty.id}")
                            )
                        }
                    }

                    if (pastDuties.isNotEmpty()) {
                        item(key = "past_header") {
                            SectionHeader(
                                title = p10Label(isHindi, "पिछली परीक्षा ड्यूटी", "Completed Duties"),
                                leadingIcon = Icons.Default.Info,
                                modifier = Modifier.padding(top = spacing.xs)
                            )
                        }
                        items(pastDuties, key = { "past_${it.id}" }) { duty ->
                            GlassListItem(
                                headline = "${duty.examTitle} — ${duty.roomName}",
                                supportingText = "${duty.dateIso} • ${duty.startTime} – ${duty.endTime}",
                                leadingIcon = Icons.Default.EventNote,
                                modifier = Modifier.testTag("my_exam_duty_item_${duty.id}")
                            )
                        }
                    }
                }
            }
        }
    }
}
