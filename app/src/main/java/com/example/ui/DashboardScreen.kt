package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SteelSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: CpiUiState,
    onSessionSelected: (String) -> Unit,
    onClassSelected: (String) -> Unit,
    onSectionSelected: (String) -> Unit,
    onStudentSelected: (Long?) -> Unit,
    onDeleteLegacyMarks: () -> Unit,
    onNavigateToStudents: () -> Unit,
    onNavigateToMarks: () -> Unit,
    onNavigateToCoScholastic: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = uiState.filteredStudents
    val selectedStudent = uiState.selectedStudent
    val activeIdentity = uiState.activeSchoolIdentity
    var showLegacyConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Dynamic Dual-Identity School Header Card (Auto-switches CPS vs CPI by selected class)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NavyDark, NavyPrimary, SteelSecondary)
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SchoolLogoAvatar(
                        identity = activeIdentity,
                        size = 60.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeIdentity.schoolName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeIdentity.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Class ${uiState.filter.className} → ${activeIdentity.code.code}  •  Session ${uiState.filter.session}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // One-time Legacy Exam Structure Warning (if Note Book / Subject Enrich / Periodic Test marks exist)
        if (uiState.legacyMarksCount > 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FailRedContainer,
                    border = BorderStroke(1.dp, FailRed.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("legacy_marks_warning_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = FailRed)
                            Text(
                                text = "Old Exam Structure Data Detected (${uiState.legacyMarksCount} entries)",
                                style = MaterialTheme.typography.titleSmall,
                                color = FailRed
                            )
                        }
                        Text(
                            text = "This app now uses one unified 10 + 30 + 60 exam structure for both terms (no Note Book, Subject Enrich, or Periodic Test). Delete the old-structure records now to keep calculations clean.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5A1212)
                        )
                        Button(
                            onClick = { showLegacyConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                            modifier = Modifier.testTag("delete_legacy_marks_button")
                        ) {
                            Text("Review & Delete Old Marks")
                        }
                    }
                }
            }
        }

        // Backup 30-day reminder banner
        if (uiState.isBackupOverdue) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AbsentAmberContainer,
                    border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_backup_warning")
                        .clickable { onNavigateToBackup() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Backup reminder",
                            tint = AbsentAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.settings.lastBackupEpochMs == 0L)
                                    "No local JSON backup yet"
                                else
                                    "Backup is over 30 days old",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF4E2D00)
                            )
                            Text(
                                text = "Data stays offline on this phone. Tap here to save a JSON backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B4200)
                            )
                        }
                    }
                }
            }
        }

        // Quick Context Pickers (Session, Class, Section, Student)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                        Text(
                            text = "Active Class & Student",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "10 / 30 / 60  •  ${uiState.subjectsForClass(uiState.filter.className).size} Subjects",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    SessionClassSectionBar(
                        uiState = uiState,
                        onSessionSelected = onSessionSelected,
                        onClassSelected = onClassSelected,
                        onSectionSelected = onSectionSelected
                    )
                    val studentOptions = if (filtered.isEmpty()) {
                        listOf("No students in Class ${uiState.filter.className}-${uiState.filter.section}")
                    } else {
                        filtered.map { "Roll ${it.rollNo} • ${it.name}" }
                    }
                    val currentLabel = selectedStudent?.let { "Roll ${it.rollNo} • ${it.name}" }
                        ?: studentOptions.first()

                    CompactDropdownSelector(
                        label = "Selected Student (${filtered.size} in section)",
                        selectedValue = currentLabel,
                        options = studentOptions,
                        onSelect = { chosen ->
                            val idx = studentOptions.indexOf(chosen)
                            if (idx in filtered.indices) {
                                onStudentSelected(filtered[idx].id)
                            }
                        },
                        testTag = "picker_student"
                    )
                }
            }
        }

        // KPI Summary Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Students (${uiState.filter.session})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.sessionStudents.size.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${filtered.size} in Class ${uiState.filter.className}-${uiState.filter.section}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Marks Entered",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.marksCompletionPercent}%",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { uiState.marksCompletionPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }
            }
        }

        // Quick Action Tiles
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardActionTile(
                        title = "Enter Marks",
                        subtitle = "IA 10, UT 30, Exam 60",
                        icon = Icons.Default.EditNote,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateToMarks,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_enter_marks")
                    )
                    DashboardActionTile(
                        title = "Students",
                        subtitle = "Add, edit, CSV import",
                        icon = Icons.Default.Groups,
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = onNavigateToStudents,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_students")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardActionTile(
                        title = "Co-Scholastic",
                        subtitle = "Grades & attendance",
                        icon = Icons.Default.FactCheck,
                        accent = MaterialTheme.colorScheme.secondary,
                        onClick = onNavigateToCoScholastic,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_coscholastic")
                    )
                    DashboardActionTile(
                        title = "A4 Reports",
                        subtitle = "Slips, Annual & ZIP",
                        icon = Icons.Default.Description,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateToReports,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_reports")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardActionTile(
                        title = "Settings",
                        subtitle = "CPS/CPI logos & map",
                        icon = Icons.Default.Settings,
                        accent = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_settings")
                    )
                    val backupSub = if (uiState.settings.lastBackupEpochMs > 0L) {
                        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
                        "Last: ${fmt.format(Date(uiState.settings.lastBackupEpochMs))}"
                    } else {
                        "Export / Restore JSON"
                    }
                    DashboardActionTile(
                        title = "Backup",
                        subtitle = backupSub,
                        icon = Icons.Default.Backup,
                        accent = MaterialTheme.colorScheme.tertiary,
                        onClick = onNavigateToBackup,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_backup")
                    )
                }
            }
        }
    }

    if (showLegacyConfirm) {
        ConfirmDangerDialog(
            title = "Delete Old Exam Structure Marks?",
            message = "Permanently delete ${uiState.legacyMarksCount} legacy Note Book, Subject Enrich, and Periodic Test records? Your unified 10/30/60 marks will not be affected.",
            confirmLabel = "Delete Old Data",
            onConfirm = {
                onDeleteLegacyMarks()
                showLegacyConfirm = false
            },
            onDismiss = { showLegacyConfirm = false }
        )
    }
}

@Composable
private fun DashboardActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
