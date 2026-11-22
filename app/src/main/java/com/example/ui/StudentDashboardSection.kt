package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.StudentEntity
import com.example.domain.ReportCalculations
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer

enum class DashboardStatusFilter(val label: String) {
    ALL("All"),
    PASS("Pass"),
    NEEDS_ATTENTION("Needs Attention"),
    PENDING("Pending")
}

@Composable
fun StudentDashboardHeaderControls(
    totalCount: Int,
    passCount: Int,
    needsAttentionCount: Int,
    pendingCount: Int,
    activeClassLabel: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    statusFilter: DashboardStatusFilter,
    onStatusFilterSelected: (DashboardStatusFilter) -> Unit,
    onlyActiveClass: Boolean,
    onToggleOnlyActiveClass: () -> Unit,
    onManageAllStudents: () -> Unit,
    showManageButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Student Directory & Status",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tap any student to jump directly to marks, attendance, or A4 report",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showManageButton) {
                TextButton(
                    onClick = onManageAllStudents,
                    modifier = Modifier.testTag("dashboard_manage_students_button")
                ) {
                    Text("Manage", fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search student, class, roll no., or Adm No.") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear student search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_student_search")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = onlyActiveClass,
                onClick = onToggleOnlyActiveClass,
                label = {
                    Text(
                        text = if (onlyActiveClass) "Class $activeClassLabel Only" else "All Classes",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.testTag("dashboard_scope_class_chip")
            )

            DashboardStatusFilter.entries.forEach { filter ->
                val count = when (filter) {
                    DashboardStatusFilter.ALL -> totalCount
                    DashboardStatusFilter.PASS -> passCount
                    DashboardStatusFilter.NEEDS_ATTENTION -> needsAttentionCount
                    DashboardStatusFilter.PENDING -> pendingCount
                }
                val selected = statusFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { onStatusFilterSelected(filter) },
                    label = {
                        Text(
                            text = "${filter.label} ($count)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("dashboard_status_chip_${filter.name}")
                )
            }
        }
    }
}

@Composable
fun StudentSummaryDashboardCard(
    summary: StudentDashboardSummary,
    isCurrentlySelected: Boolean,
    onOpenMarks: (StudentEntity) -> Unit,
    onOpenCoScholastic: (StudentEntity) -> Unit,
    onOpenReport: (StudentEntity) -> Unit,
    canOpenMarks: Boolean = true,
    canOpenCoScholastic: Boolean = true,
    canOpenReport: Boolean = true,
    modifier: Modifier = Modifier
) {
    val st = summary.student
    val (statusBg, statusFg, statusIcon, statusText) = when (summary.status) {
        StudentAcademicStatus.PASS -> QuadStatusStyle(
            bg = PassGreenContainer,
            fg = PassGreen,
            icon = Icons.Default.CheckCircle,
            label = "PASS  •  ${ReportCalculations.formatPercent(summary.overallPercent)}% (${summary.overallGrade})"
        )
        StudentAcademicStatus.NEEDS_ATTENTION -> QuadStatusStyle(
            bg = FailRedContainer,
            fg = FailRed,
            icon = Icons.Default.ErrorOutline,
            label = "FAIL  •  ${ReportCalculations.formatPercent(summary.overallPercent)}% (${summary.overallGrade})"
        )
        StudentAcademicStatus.PENDING -> QuadStatusStyle(
            bg = AbsentAmberContainer,
            fg = AbsentAmber,
            icon = Icons.Default.HourglassEmpty,
            label = "PENDING  •  No Marks"
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isCurrentlySelected) 1.5.dp else 1.dp,
            color = if (isCurrentlySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(
                when {
                    canOpenReport -> Modifier.clickable { onOpenReport(st) }
                    canOpenMarks -> Modifier.clickable { onOpenMarks(st) }
                    canOpenCoScholastic -> Modifier.clickable { onOpenCoScholastic(st) }
                    else -> Modifier
                }
            )
            .testTag("dashboard_student_card_${st.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Class & Section Pill Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${st.className}-${st.section}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Roll ${st.rollNo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Name + Metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = st.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val metaText = buildString {
                        if (summary.classGroupLabel.isNotBlank()) {
                            append(summary.classGroupLabel)
                        } else {
                            append(summary.schoolName)
                        }
                        if (st.admNo.isNotBlank()) append("  •  Adm ${st.admNo}")
                        if (summary.classRank != null) append("  •  Rank #${summary.classRank}")
                    }
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Overall Academic Status Pill
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = statusBg,
                border = BorderStroke(1.dp, statusFg.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = summary.status.label,
                        tint = statusFg,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (canOpenMarks || canOpenCoScholastic || canOpenReport) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

                // Quick Navigation Action Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canOpenMarks) {
                        StudentQuickNavChip(
                            label = "Marks",
                            icon = Icons.Default.EditNote,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = { onOpenMarks(st) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_student_marks_${st.id}")
                        )
                    }
                    if (canOpenCoScholastic) {
                        StudentQuickNavChip(
                            label = "Attendance",
                            icon = Icons.Default.FactCheck,
                            tint = MaterialTheme.colorScheme.secondary,
                            onClick = { onOpenCoScholastic(st) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_student_coschol_${st.id}")
                        )
                    }
                    if (canOpenReport) {
                        StudentQuickNavChip(
                            label = "Report",
                            icon = Icons.Default.Description,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = { onOpenReport(st) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_student_report_${st.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentQuickNavChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.09f),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StudentDashboardEmptyCard(
    hasAnySessionStudents: Boolean,
    onClearFilters: () -> Unit,
    onNavigateToStudents: () -> Unit,
    canManageStudents: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        EmptyStatePanel(
            icon = Icons.Default.Groups,
            title = if (hasAnySessionStudents) "No matching students" else "No students in this session yet",
            message = if (hasAnySessionStudents) {
                "Try clearing your search or switching the status/class filter."
            } else if (canManageStudents) {
                "Add students or import a class CSV roster to start tracking academic status."
            } else {
                "No students have been added to this session yet."
            },
            actionLabel = when {
                hasAnySessionStudents -> "Reset Filters"
                canManageStudents -> "Add Students"
                else -> null
            },
            onAction = when {
                hasAnySessionStudents -> onClearFilters
                canManageStudents -> onNavigateToStudents
                else -> null
            }
        )
    }
}

private data class QuadStatusStyle(
    val bg: Color,
    val fg: Color,
    val icon: ImageVector,
    val label: String
)
