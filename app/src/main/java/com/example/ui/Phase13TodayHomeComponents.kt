package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.domain.FeeClerkTodayData
import com.example.domain.FirstTimeGuideState
import com.example.domain.FirstTimeGuideStep
import com.example.domain.FirstTimeGuideStepId
import com.example.domain.LeaveStatus
import com.example.domain.MissingAttendanceSectionItem
import com.example.domain.OwnerAdminTodayTiles
import com.example.domain.Phase13TodayHomeEngine
import com.example.domain.SyncStatus
import com.example.domain.SyncUiState
import com.example.domain.TeacherTodayData
import com.example.domain.TodayHomePersona
import com.example.domain.TodayQuickAction
import com.example.domain.TodayQuickActionId
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import com.example.ui.theme.SteelSecondary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private fun formatRs(amount: Int): String {
    val nf = NumberFormat.getIntegerInstance(Locale("en", "IN"))
    return "₹${nf.format(amount)}"
}

private fun formatReadableDate(dateIso: String, isHindi: Boolean): String {
    return try {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = inFmt.parse(dateIso) ?: return dateIso
        val outFmt = SimpleDateFormat("EEE, dd MMM yyyy", if (isHindi) Locale("hi", "IN") else Locale.US)
        outFmt.format(parsed)
    } catch (_: Exception) {
        dateIso
    }
}

fun syncStatusFriendlyLabel(syncUiState: SyncUiState, isHindi: Boolean): String {
    return when (syncUiState.status) {
        SyncStatus.ONLINE -> p10Label(isHindi, "ऑनलाइन • सब सिंक है", "Online • Synced")
        SyncStatus.OFFLINE -> {
            if (syncUiState.pendingCount > 0) {
                p10Label(
                    isHindi,
                    "ऑफ़लाइन • ${syncUiState.pendingCount} बदलाव बाकी",
                    "Offline • ${syncUiState.pendingCount} pending"
                )
            } else {
                p10Label(isHindi, "ऑफ़लाइन • लोकल कैश चालू", "Offline • Cached mode")
            }
        }
        SyncStatus.SYNCING -> {
            if (syncUiState.pendingCount > 0) {
                p10Label(
                    isHindi,
                    "सिंक हो रहा है (${syncUiState.pendingCount})…",
                    "Syncing (${syncUiState.pendingCount})…"
                )
            } else {
                p10Label(isHindi, "सिंक हो रहा है…", "Syncing…")
            }
        }
        SyncStatus.ERROR -> p10Label(isHindi, "सिंक समस्या • टैप करें", "Sync issue • Tap for help")
    }
}

// ============================================================================
// 1. TODAY HERO HEADER (School + Date + Role + Bilingual Sync Status + Language Toggle)
// ============================================================================

@Composable
fun TodayHeroHeaderCard(
    activeSchool: SchoolEntity,
    activeClassLabel: String,
    session: String,
    dateIso: String,
    persona: TodayHomePersona,
    syncUiState: SyncUiState,
    isHindi: Boolean,
    guideState: FirstTimeGuideState,
    onToggleLanguage: () -> Unit,
    onSyncStatusClick: () -> Unit,
    onReopenSetupGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val personaLabel = when (persona) {
        TodayHomePersona.OWNER_ADMIN -> p10Label(isHindi, "आज का डैशबोर्ड", "Today • School Admin")
        TodayHomePersona.TEACHER -> p10Label(isHindi, "आज का शिक्षक होम", "Today • Teacher")
        TodayHomePersona.FEE_CLERK -> p10Label(isHindi, "आज का फीस काउंटर", "Today • Fee Counter")
        TodayHomePersona.GENERAL_STAFF -> p10Label(isHindi, "आज का कार्य", "Today • Staff")
    }

    val (syncBg, syncFg, syncIcon) = when (syncUiState.status) {
        SyncStatus.ONLINE -> Triple(Color(0xFF14532D).copy(alpha = 0.72f), Color(0xFFBBF7D0), Icons.Default.CloudDone)
        SyncStatus.OFFLINE -> Triple(Color(0xFF78350F).copy(alpha = 0.82f), Color(0xFFFDE68A), Icons.Default.CloudOff)
        SyncStatus.SYNCING -> Triple(Color(0xFF1E3A8A).copy(alpha = 0.82f), Color(0xFFBFDBFE), Icons.Default.CloudSync)
        SyncStatus.ERROR -> Triple(Color(0xFF7F1D1D).copy(alpha = 0.88f), Color(0xFFFECACA), Icons.Default.ErrorOutline)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(NavyDark, NavyPrimary, SteelSecondary)
                )
            )
            .padding(16.dp)
            .testTag("today_hero_header_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SchoolLogoAvatar(
                    school = activeSchool,
                    size = 54.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = personaLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFBFDBFE),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = activeSchool.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatReadableDate(dateIso, isHindi)}  •  $session",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Instant Hindi / English toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onToggleLanguage)
                        .testTag("phase13_language_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = p10Label(isHindi, "भाषा बदलें", "Switch language"),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isHindi) "EN" else "हिन्दी",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Status & Quick Utility Row (Sync Status + Class badge + Reopen Guide pill)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = syncBg,
                    border = BorderStroke(1.dp, syncFg.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .clickable(onClick = onSyncStatusClick)
                        .testTag("today_sync_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = syncIcon,
                            contentDescription = syncStatusFriendlyLabel(syncUiState, isHindi),
                            tint = syncFg,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = syncStatusFriendlyLabel(syncUiState, isHindi),
                            style = MaterialTheme.typography.labelSmall,
                            color = syncFg,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (activeClassLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = activeClassLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                if (guideState.shouldShowReopenChip) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = AbsentAmberContainer,
                        border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clickable(onClick = onReopenSetupGuide)
                            .testTag("reopen_setup_guide_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = p10Label(isHindi, "सेटअप गाइड खोलें", "Open setup guide"),
                                tint = AbsentAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "सेटअप गाइड (${guideState.completedCount}/${guideState.totalCount})",
                                    "Setup Guide (${guideState.completedCount}/${guideState.totalCount})"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4E2D00),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. FIRST-TIME SETUP GUIDE CHECKLIST (8 Auto-Ticking Steps, Skippable + Undo)
// ============================================================================

@Composable
fun FirstTimeSetupGuideCard(
    guideState: FirstTimeGuideState,
    isHindi: Boolean,
    onStepClick: (FirstTimeGuideStepId) -> Unit,
    onSkipGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (guideState.totalCount > 0) {
        guideState.completedCount.toFloat() / guideState.totalCount.toFloat()
    } else 0f

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("first_time_setup_guide_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = p10Label(isHindi, "शुरुआती सेटअप गाइड", "First-Time Setup Guide"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = p10Label(
                                isHindi,
                                "${guideState.completedCount}/${guideState.totalCount} कदम पूरे • काम पूरा होते ही अपने आप टिक होगा",
                                "${guideState.completedCount}/${guideState.totalCount} steps done • Auto-ticks when completed"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = onSkipGuide,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("skip_setup_guide_button")
                ) {
                    Text(
                        text = p10Label(isHindi, "अभी छोड़ें", "Skip"),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = PassGreen,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )

            val nextStepId = guideState.nextPendingStep?.id

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                guideState.steps.forEach { step ->
                    FirstTimeGuideStepRow(
                        step = step,
                        isNextHighlighted = step.id == nextStepId,
                        isHindi = isHindi,
                        onClick = { onStepClick(step.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstTimeGuideStepRow(
    step: FirstTimeGuideStep,
    isNextHighlighted: Boolean,
    isHindi: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        step.isCompleted -> PassGreenContainer.copy(alpha = 0.45f)
        isNextHighlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val borderColor = when {
        step.isCompleted -> PassGreen.copy(alpha = 0.35f)
        isNextHighlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(enabled = step.canOpen, onClick = onClick)
            .testTag("guide_step_${step.id.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (step.isCompleted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = p10Label(isHindi, "पूरा हुआ", "Completed"),
                    tint = PassGreen,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = if (isNextHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = step.stepNumber.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isNextHighlighted) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = p10Label(isHindi, step.titleHi, step.titleEn),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isNextHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (step.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (step.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                if (!step.isCompleted) {
                    Text(
                        text = p10Label(isHindi, step.hintHi, step.hintEn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (step.canOpen && !step.isCompleted) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = p10Label(isHindi, "खोलें", "Open"),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ============================================================================
// 3. OWNER & ADMIN "TODAY" TILES SECTION (Strictly Permission-Gated)
// ============================================================================

@Composable
fun OwnerAdminTodaySection(
    tiles: OwnerAdminTodayTiles,
    dateIso: String,
    isHindi: Boolean,
    onOpenStudentAttendance: (String?, String?) -> Unit,
    onTakeAttendanceForSection: (MissingAttendanceSectionItem) -> Unit,
    onOpenFeeCollection: () -> Unit,
    onOpenFeeDuesReport: () -> Unit,
    onOpenCashBook: () -> Unit,
    onOpenTeacherSubstitutions: () -> Unit,
    onOpenTeacherAttendance: () -> Unit,
    onOpenEnquiry: () -> Unit,
    onOpenStock: () -> Unit,
    onOpenExams: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasAnyTile = tiles.showAttendanceTile ||
        tiles.showFeeCollectionTile ||
        tiles.showExpensesTile ||
        tiles.showGuardiansDuesTile ||
        tiles.showTeacherAbsenceTile ||
        tiles.showEnquiryFollowUpTile ||
        tiles.showLowStockTile ||
        tiles.showUpcomingExamsTile

    if (!hasAnyTile) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("owner_admin_today_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = p10Label(isHindi, "आज का सारांश (टैप करके सीधे खोलें)", "Today at a Glance (Tap any card)"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 1. Today's Student Attendance per School + Missing Sections
        if (tiles.showAttendanceTile) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("today_tile_attendance")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = p10Label(isHindi, "आज की छात्र हाज़िरी", "Today's Student Attendance"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (tiles.missingAttendanceSections.isEmpty() && tiles.totalSectionsAcrossSchools > 0) {
                                PassGreenContainer
                            } else {
                                AbsentAmberContainer
                            }
                        ) {
                            Text(
                                text = "${tiles.totalSectionsTakenAcrossSchools}/${tiles.totalSectionsAcrossSchools} " +
                                    p10Label(isHindi, "सेक्शन", "sections"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (tiles.missingAttendanceSections.isEmpty() && tiles.totalSectionsAcrossSchools > 0) {
                                    PassGreen
                                } else {
                                    Color(0xFF4E2D00)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (tiles.schoolAttendanceSummaries.isEmpty()) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "अभी कोई सेक्शन उपलब्ध नहीं है। पहले कक्षा और छात्र जोड़ें।",
                                "No sections found yet. Add classes and students first."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        tiles.schoolAttendanceSummaries.forEach { sch ->
                            val pctText = if (sch.sectionsTaken == 0) {
                                p10Label(isHindi, "अभी नहीं ली गई", "Not taken yet")
                            } else {
                                "${sch.presentPercent}% " + p10Label(isHindi, "उपस्थित", "present")
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clickable { onOpenStudentAttendance(sch.schoolDocId, dateIso) }
                                    .testTag("today_attendance_school_${sch.schoolDocId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sch.schoolName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${sch.sectionsTaken}/${sch.totalSections} " +
                                                p10Label(isHindi, "सेक्शन पूरे", "sections taken"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = pctText,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sch.sectionsTaken == 0) AbsentAmber else PassGreen
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Classes where attendance is NOT yet taken
                    if (tiles.missingAttendanceSections.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.testTag("today_missing_attendance_card")
                        ) {
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "हाज़िरी बाकी (${tiles.missingAttendanceSections.size}) — लेने के लिए टैप करें:",
                                    "Attendance not taken yet (${tiles.missingAttendanceSections.size}) — tap to take:"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AbsentAmber
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tiles.missingAttendanceSections.take(12).forEach { sec ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AbsentAmberContainer,
                                        border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clickable { onTakeAttendanceForSection(sec) }
                                            .testTag("missing_att_${sec.className}_${sec.section}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${sec.className}-${sec.section} (${sec.studentCount})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4E2D00)
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = Color(0xFF4E2D00),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Metric Tile Grid (strictly only permitted tiles are added!)
        val metricTiles = buildList<@Composable (Modifier) -> Unit> {
            if (tiles.showFeeCollectionTile) {
                add { mod ->
                    TodayMetricTile(
                        title = p10Label(isHindi, "आज की फीस वसूली", "Today's Fee Collection"),
                        bigValue = formatRs(tiles.todayFeeCollectionRs),
                        subtitle = p10Label(
                            isHindi,
                            "${tiles.todayFeeReceiptCount} रसीद कटी",
                            "${tiles.todayFeeReceiptCount} receipt(s)"
                        ),
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = PassGreen,
                        containerTint = PassGreenContainer.copy(alpha = 0.45f),
                        onClick = onOpenFeeCollection,
                        modifier = mod.testTag("today_tile_fee_collection")
                    )
                }
            }
            if (tiles.showExpensesTile) {
                add { mod ->
                    TodayMetricTile(
                        title = p10Label(isHindi, "आज का खर्च", "Today's Expenses"),
                        bigValue = formatRs(tiles.todayExpensesRs),
                        subtitle = p10Label(
                            isHindi,
                            "${tiles.todayExpenseCount} प्रविष्टियाँ",
                            "${tiles.todayExpenseCount} entr(y/ies)"
                        ),
                        icon = Icons.Default.AccountBalance,
                        accentColor = FailRed,
                        containerTint = FailRedContainer.copy(alpha = 0.4f),
                        onClick = onOpenCashBook,
                        modifier = mod.testTag("today_tile_expenses")
                    )
                }
            }
            if (tiles.showGuardiansDuesTile) {
                add { mod ->
                    TodayMetricTile(
                        title = p10Label(isHindi, "बकाया वाले अभिभावक", "Guardians with Dues"),
                        bigValue = tiles.guardiansWithDuesCount.toString(),
                        subtitle = if (tiles.guardiansWithDuesCount > 0) {
                            p10Label(isHindi, "कुल ${formatRs(tiles.totalDuesAmountRs)} बकाया", "Total ${formatRs(tiles.totalDuesAmountRs)} due")
                        } else {
                            p10Label(isHindi, "कोई बकाया नहीं", "All fees cleared")
                        },
                        icon = Icons.Default.Payments,
                        accentColor = if (tiles.guardiansWithDuesCount > 0) AbsentAmber else PassGreen,
                        containerTint = if (tiles.guardiansWithDuesCount > 0) AbsentAmberContainer.copy(alpha = 0.55f) else PassGreenContainer.copy(alpha = 0.35f),
                        onClick = onOpenFeeDuesReport,
                        modifier = mod.testTag("today_tile_guardians_dues")
                    )
                }
            }
            if (tiles.showTeacherAbsenceTile) {
                add { mod ->
                    val hasUrgent = tiles.pendingSubstitutionsTodayCount > 0 || tiles.absentTeachersTodayCount > 0
                    TodayMetricTile(
                        title = p10Label(isHindi, "अनुपस्थित शिक्षक / विकल्प", "Absent Staff & Subs"),
                        bigValue = "${tiles.absentTeachersTodayCount} / ${tiles.pendingSubstitutionsTodayCount}",
                        subtitle = p10Label(
                            isHindi,
                            "${tiles.absentTeachersTodayCount} अनुपस्थित • ${tiles.pendingSubstitutionsTodayCount} विकल्प बाकी",
                            "${tiles.absentTeachersTodayCount} absent • ${tiles.pendingSubstitutionsTodayCount} subs pending"
                        ),
                        icon = Icons.Default.HowToReg,
                        accentColor = if (tiles.pendingSubstitutionsTodayCount > 0) FailRed else if (hasUrgent) AbsentAmber else PassGreen,
                        containerTint = if (tiles.pendingSubstitutionsTodayCount > 0) FailRedContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
                        onClick = {
                            if (tiles.pendingSubstitutionsTodayCount > 0) onOpenTeacherSubstitutions()
                            else onOpenTeacherAttendance()
                        },
                        modifier = mod.testTag("today_tile_teacher_absence")
                    )
                }
            }
            if (tiles.showEnquiryFollowUpTile) {
                add { mod ->
                    TodayMetricTile(
                        title = p10Label(isHindi, "आज के पूछताछ फ़ॉलो-अप", "Enquiry Follow-Ups"),
                        bigValue = tiles.enquiryFollowUpsDueCount.toString(),
                        subtitle = if (tiles.enquiryFollowUpsDueCount > 0) {
                            p10Label(
                                isHindi,
                                "${tiles.enquiryOverdueCount} विलंबित • ${tiles.enquiryDueTodayCount} आज",
                                "${tiles.enquiryOverdueCount} overdue • ${tiles.enquiryDueTodayCount} today"
                            )
                        } else {
                            p10Label(isHindi, "आज कोई कॉल बाकी नहीं", "No follow-ups due")
                        },
                        icon = Icons.Default.ContactPhone,
                        accentColor = if (tiles.enquiryOverdueCount > 0) FailRed else MaterialTheme.colorScheme.primary,
                        containerTint = if (tiles.enquiryOverdueCount > 0) FailRedContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                        onClick = onOpenEnquiry,
                        modifier = mod.testTag("today_tile_enquiry_followups")
                    )
                }
            }
            if (tiles.showLowStockTile) {
                add { mod ->
                    val sub = if (tiles.lowStockItemCount > 0) {
                        tiles.lowStockTopNames.joinToString(", ")
                    } else {
                        p10Label(isHindi, "सभी स्टॉक पर्याप्त हैं", "All stock levels healthy")
                    }
                    TodayMetricTile(
                        title = p10Label(isHindi, "कम स्टॉक सामान", "Low-Stock Items"),
                        bigValue = tiles.lowStockItemCount.toString(),
                        subtitle = sub,
                        icon = Icons.Default.Inventory2,
                        accentColor = if (tiles.lowStockItemCount > 0) AbsentAmber else PassGreen,
                        containerTint = if (tiles.lowStockItemCount > 0) AbsentAmberContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                        onClick = onOpenStock,
                        modifier = mod.testTag("today_tile_low_stock")
                    )
                }
            }
            if (tiles.showUpcomingExamsTile) {
                add { mod ->
                    val papers = tiles.upcomingExamEntriesThisWeek.size
                    val duties = tiles.upcomingExamDutiesThisWeekCount
                    val nextDate = tiles.upcomingExamEntriesThisWeek.firstOrNull()?.dateIso
                    val sub = when {
                        papers > 0 && nextDate != null -> p10Label(
                            isHindi,
                            "अगला: $nextDate • $duties ड्यूटी",
                            "Next: $nextDate • $duties duties"
                        )
                        duties > 0 -> p10Label(isHindi, "$duties ड्यूटी इस सप्ताह", "$duties duties this week")
                        else -> p10Label(isHindi, "इस सप्ताह कोई परीक्षा नहीं", "No exams this week")
                    }
                    TodayMetricTile(
                        title = p10Label(isHindi, "इस सप्ताह परीक्षा व ड्यूटी", "Exams This Week"),
                        bigValue = papers.toString(),
                        subtitle = sub,
                        icon = Icons.Default.EventNote,
                        accentColor = MaterialTheme.colorScheme.primary,
                        containerTint = MaterialTheme.colorScheme.surface,
                        onClick = onOpenExams,
                        modifier = mod.testTag("today_tile_upcoming_exams")
                    )
                }
            }
        }

        metricTiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowTiles.forEach { renderTile ->
                    renderTile(Modifier.weight(1f))
                }
                if (rowTiles.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TodayMetricTile(
    title: String,
    bigValue: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    containerTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerTint,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = bigValue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================================
// 4. TEACHER "TODAY" HERO SECTION (Big Take Attendance CTA + Pending Marks + Leave Status)
// ============================================================================

@Composable
fun TeacherTodayHeroSection(
    teacherData: TeacherTodayData,
    dateIso: String,
    isHindi: Boolean,
    canViewMarks: Boolean,
    canViewTimetable: Boolean,
    onTakeAttendance: (String, String, String, String) -> Unit,
    onOpenPendingMarks: () -> Unit,
    onOpenLeaveTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("teacher_today_hero_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Big "Take Attendance" CTA for own Class Teacher section(s)
        if (teacherData.classAttendanceTargets.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = p10Label(isHindi, "मेरी कक्षा की आज की हाज़िरी", "My Section Attendance Today"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    teacherData.classAttendanceTargets.forEach { target ->
                        val statusSub = if (target.alreadyTakenToday) {
                            p10Label(
                                isHindi,
                                "हाज़िरी हो चुकी • ${target.presentCount}/${target.studentCount} उपस्थित",
                                "Taken • ${target.presentCount}/${target.studentCount} present"
                            )
                        } else {
                            p10Label(
                                isHindi,
                                "अभी हाज़िरी बाकी (${target.studentCount} छात्र)",
                                "Not taken yet (${target.studentCount} students)"
                            )
                        }
                        Button(
                            onClick = {
                                onTakeAttendance(target.schoolDocId, target.className, target.section, dateIso)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (target.alreadyTakenToday) PassGreen else MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("teacher_big_take_attendance_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p10Label(
                                        isHindi,
                                        "हाज़िरी लें — कक्षा ${target.className}-${target.section}",
                                        "Take Attendance — Class ${target.className}-${target.section}"
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = statusSub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Teacher KPI Row: Pending Marks + Exam Duties
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (canViewMarks) {
                TodayMetricTile(
                    title = p10Label(isHindi, "बकाया अंक प्रविष्टि", "Pending Marks Entry"),
                    bigValue = teacherData.pendingMarksCount.toString(),
                    subtitle = if (teacherData.pendingMarksCount > 0) {
                        p10Label(isHindi, "भरने के लिए टैप करें", "Tap to complete marks")
                    } else {
                        p10Label(isHindi, "सभी अंक पूरे हैं", "All marks up to date")
                    },
                    icon = Icons.Default.AssignmentLate,
                    accentColor = if (teacherData.pendingMarksCount > 0) AbsentAmber else PassGreen,
                    containerTint = if (teacherData.pendingMarksCount > 0) AbsentAmberContainer.copy(alpha = 0.5f) else PassGreenContainer.copy(alpha = 0.35f),
                    onClick = onOpenPendingMarks,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("teacher_today_pending_marks")
                )
            }
            TodayMetricTile(
                title = p10Label(isHindi, "मेरी परीक्षा ड्यूटी", "My Exam Duties"),
                bigValue = teacherData.upcomingExamDutiesCount.toString(),
                subtitle = if (teacherData.upcomingExamDutiesCount > 0) {
                    p10Label(isHindi, "आगामी निरीक्षक ड्यूटी", "Upcoming invigilation")
                } else {
                    p10Label(isHindi, "कोई आगामी ड्यूटी नहीं", "No upcoming duties")
                },
                icon = Icons.Default.EventNote,
                accentColor = MaterialTheme.colorScheme.primary,
                containerTint = MaterialTheme.colorScheme.surface,
                onClick = {},
                modifier = Modifier
                    .weight(1f)
                    .testTag("teacher_today_exam_duties")
            )
        }

        // My Leave Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("teacher_today_leave_status")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventBusy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = p10Label(isHindi, "मेरी छुट्टी की स्थिति", "My Leave Status"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (canViewTimetable) {
                        TextButton(
                            onClick = onOpenLeaveTab,
                            modifier = Modifier.testTag("teacher_open_leave_button")
                        ) {
                            Text(p10Label(isHindi, "छुट्टी देखें", "Open Leaves"))
                        }
                    }
                }

                if (teacherData.leaveStatus.totalCount == 0) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "अभी कोई छुट्टी का आवेदन नहीं है।",
                            "No leave applications recorded yet."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LeaveCountChip(
                            label = p10Label(isHindi, "लंबित: ${teacherData.leaveStatus.pendingCount}", "Pending: ${teacherData.leaveStatus.pendingCount}"),
                            bg = AbsentAmberContainer,
                            fg = Color(0xFF4E2D00)
                        )
                        LeaveCountChip(
                            label = p10Label(isHindi, "स्वीकृत: ${teacherData.leaveStatus.approvedCount}", "Approved: ${teacherData.leaveStatus.approvedCount}"),
                            bg = PassGreenContainer,
                            fg = PassGreen
                        )
                        if (teacherData.leaveStatus.rejectedCount > 0) {
                            LeaveCountChip(
                                label = p10Label(isHindi, "अस्वीकृत: ${teacherData.leaveStatus.rejectedCount}", "Rejected: ${teacherData.leaveStatus.rejectedCount}"),
                                bg = FailRedContainer,
                                fg = FailRed
                            )
                        }
                    }
                    teacherData.leaveStatus.recentLeaves.forEach { lv ->
                        val statusLabel = when (lv.status) {
                            LeaveStatus.APPROVED -> p10Label(isHindi, "स्वीकृत", "Approved")
                            LeaveStatus.PENDING -> p10Label(isHindi, "लंबित", "Pending")
                            LeaveStatus.REJECTED -> p10Label(isHindi, "अस्वीकृत", "Rejected")
                            LeaveStatus.CANCELLED -> p10Label(isHindi, "रद्द", "Cancelled")
                        }
                        val dateRange = if (lv.startDate == lv.endDate) lv.startDate else "${lv.startDate} → ${lv.endDate}"
                        Text(
                            text = "• $dateRange — ${lv.reasonType.label} ($statusLabel)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveCountChip(label: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ============================================================================
// 5. FEE CLERK "TODAY" HERO SECTION (1-Tap Instant Search + Today Collection + Send Queue)
// ============================================================================

@Composable
fun FeeClerkTodayHeroSection(
    feeClerkData: FeeClerkTodayData,
    students: List<StudentEntity>,
    isHindi: Boolean,
    onCollectFeeForStudent: (StudentEntity) -> Unit,
    onOpenFeeCollection: () -> Unit,
    onOpenSendQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val matchingStudents = remember(students, searchQuery) {
        Phase13TodayHomeEngine.searchStudentsForFeeClerk(students, searchQuery, limit = 8)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("fee_clerk_today_hero_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Big Instant Student Search Card -> 1 tap to Fee Collection
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = p10Label(isHindi, "छात्र खोजें और तुरंत फीस लें", "Find Student & Collect Fee (1 Tap)"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = p10Label(
                                isHindi,
                                "नाम, एडमिशन नंबर या मोबाइल नंबर लिखें…",
                                "Type student name, admission no. or mobile…"
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = p10Label(isHindi, "खोजें", "Search"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("fee_clerk_clear_search")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = p10Label(isHindi, "साफ़ करें", "Clear")
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fee_clerk_student_search")
                )

                if (searchQuery.isBlank()) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "सक्रिय छात्र: ${feeClerkData.totalActiveStudentsInSchool} • नाम, एडमिशन नं. या मोबाइल के आखिरी 4 अंक लिखें।",
                            "${feeClerkData.totalActiveStudentsInSchool} active students • Search by name, admission no., or mobile digits."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (matchingStudents.isEmpty()) {
                    FriendlyEmptyStateCard(
                        icon = Icons.Default.Search,
                        title = p10Label(isHindi, "कोई छात्र नहीं मिला", "No matching student found"),
                        message = p10Label(
                            isHindi,
                            "स्पेलिंग जांचें, मोबाइल नंबर के आखिरी अंक डालें या ऊपर से सही स्कूल चुनें।",
                            "Check spelling, try mobile digits, or switch school from the top bar."
                        ),
                        actionLabel = p10Label(isHindi, "खोज साफ़ करें", "Clear search"),
                        onAction = { searchQuery = "" }
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        matchingStudents.forEach { st ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clickable { onCollectFeeForStudent(st) }
                                    .testTag("fee_clerk_result_${st.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = st.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val details = buildList {
                                            add("${st.className}-${st.section}")
                                            if (st.admNo.isNotBlank()) add("Adm ${st.admNo}")
                                            if (st.mobile.isNotBlank()) add(st.mobile)
                                        }.joinToString(" • ")
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = { onCollectFeeForStudent(st) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .heightIn(min = 44.dp)
                                            .testTag("fee_clerk_collect_btn_${st.id}")
                                    ) {
                                        Text(
                                            text = p10Label(isHindi, "फीस लें", "Collect"),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fee Clerk 2-tile Summary: Today's Collection Total + Unsent Dues in Send Queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TodayMetricTile(
                title = p10Label(isHindi, "आज की कुल वसूली", "Today's Collection"),
                bigValue = formatRs(feeClerkData.todayCollectionRs),
                subtitle = p10Label(
                    isHindi,
                    "${feeClerkData.todayReceiptCount} रसीद आज",
                    "${feeClerkData.todayReceiptCount} receipt(s) today"
                ),
                icon = Icons.Default.AccountBalanceWallet,
                accentColor = PassGreen,
                containerTint = PassGreenContainer.copy(alpha = 0.45f),
                onClick = onOpenFeeCollection,
                modifier = Modifier
                    .weight(1f)
                    .testTag("fee_clerk_today_collection")
            )
            TodayMetricTile(
                title = p10Label(isHindi, "भेजने बाकी बकाया", "Unsent Dues Queue"),
                bigValue = feeClerkData.unsentDuesQueueCount.toString(),
                subtitle = if (feeClerkData.unsentDuesQueueCount > 0) {
                    p10Label(
                        isHindi,
                        "कुल ${formatRs(feeClerkData.unsentDuesTotalRs)} • व्हाट्सऐप भेजें",
                        "${formatRs(feeClerkData.unsentDuesTotalRs)} due • Send now"
                    )
                } else {
                    p10Label(isHindi, "सभी रिमाइंडर भेजे गए", "All reminders sent")
                },
                icon = Icons.Default.MarkChatUnread,
                accentColor = if (feeClerkData.unsentDuesQueueCount > 0) AbsentAmber else PassGreen,
                containerTint = if (feeClerkData.unsentDuesQueueCount > 0) AbsentAmberContainer.copy(alpha = 0.5f) else PassGreenContainer.copy(alpha = 0.35f),
                onClick = onOpenSendQueue,
                modifier = Modifier
                    .weight(1f)
                    .testTag("fee_clerk_unsent_dues")
            )
        }
    }
}

// ============================================================================
// 6. BOTTOM / FLOATING QUICK ACTIONS ROW (3 Most-Used Actions for User's Role)
// ============================================================================

@Composable
fun Phase13QuickActionsBar(
    actions: List<TodayQuickAction>,
    isHindi: Boolean,
    onActionClick: (TodayQuickActionId) -> Unit,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return

    Surface(
        shadowElevation = 6.dp,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("phase13_quick_actions_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.take(3).forEachIndexed { index, action ->
                val isPrimary = index == 0
                val icon = quickActionIcon(action.id)
                val bg = if (isPrimary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                }
                val fg = if (isPrimary) Color.White else MaterialTheme.colorScheme.onPrimaryContainer

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bg,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .clickable { onActionClick(action.id) }
                        .testTag(action.testTag)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = p10Label(isHindi, action.titleHi, action.titleEn),
                            tint = fg,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = p10Label(isHindi, action.titleHi, action.titleEn),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun quickActionIcon(id: TodayQuickActionId): ImageVector = when (id) {
    TodayQuickActionId.TAKE_STUDENT_ATTENDANCE -> Icons.Default.FactCheck
    TodayQuickActionId.COLLECT_FEE -> Icons.Default.AccountBalanceWallet
    TodayQuickActionId.SEND_FEE_QUEUE -> Icons.Default.MarkChatUnread
    TodayQuickActionId.ENTER_MARKS -> Icons.Default.EditNote
    TodayQuickActionId.PENDING_MARKS -> Icons.Default.AssignmentLate
    TodayQuickActionId.TIMETABLE -> Icons.Default.CalendarMonth
    TodayQuickActionId.TEACHER_LEAVE -> Icons.Default.EventBusy
    TodayQuickActionId.CASH_BOOK -> Icons.Default.AccountBalance
    TodayQuickActionId.ENQUIRY -> Icons.Default.ContactPhone
    TodayQuickActionId.STOCK -> Icons.Default.Inventory2
    TodayQuickActionId.EXAMS -> Icons.Default.EventNote
    TodayQuickActionId.STUDENT_REGISTER -> Icons.Default.Badge
    TodayQuickActionId.CERTIFICATES -> Icons.Default.Verified
    TodayQuickActionId.SCHOOLS -> Icons.Default.School
}

// ============================================================================
// 7. REUSABLE FRIENDLY EMPTY STATE CARD
// ============================================================================

@Composable
fun FriendlyEmptyStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
