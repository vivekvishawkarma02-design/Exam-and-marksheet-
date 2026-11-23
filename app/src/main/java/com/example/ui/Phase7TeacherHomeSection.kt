package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.AppModule
import com.example.domain.DayOfWeekCode
import com.example.domain.MyDayPeriodItem
import com.example.domain.PendingMarksItem
import com.example.domain.Phase4Validator
import com.example.domain.Phase6DateUtils
import com.example.domain.TeacherMyDaySummary
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer

/**
 * Phase 7 "My Day" home card for any member linked to a Phase 4 Teacher profile.
 * Shows:
 * - Today's timetable (times, class, subject, school)
 * - Today's substitutions assigned to this teacher
 * - Interactive Weekly Timetable switcher (Mon–Sat)
 * - Class Teacher section quick-jump (Co-Scholastic & Attendance)
 * - Own Pending Marks tasks with 1-tap jump to Marks Entry
 */
@Composable
fun TeacherMyDayHomeCard(
    uiState: SchoolUiState,
    onOpenTimetable: () -> Unit,
    onOpenPendingMarks: () -> Unit,
    onOpenMarksForItem: (PendingMarksItem) -> Unit,
    onOpenCoScholasticForSection: (schoolDocId: String, className: String, section: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayIso = remember { Phase6DateUtils.todayIsoDate() }
    val summary: TeacherMyDaySummary = remember(
        todayIso,
        uiState.filter.session,
        uiState.teachers,
        uiState.ownLinkedTeacher,
        uiState.ownTeacherScope,
        uiState.activeSessionTimetableCells,
        uiState.substitutions,
        uiState.effectivePeriodStructuresBySchool,
        uiState.schoolNamesByDocId,
        uiState.multiSchoolPendingBundles,
        uiState.allStudents,
        uiState.allMarks,
        uiState.classAssignments
    ) {
        uiState.buildTeacherMyDay(todayIso) ?: return@remember null
    } ?: return

    var showWeeklySchedule by remember { mutableStateOf(false) }
    var selectedWeekDay by remember(summary.dayOfWeek) { mutableStateOf(summary.dayOfWeek) }
    val isMultiSchoolTeacher = uiState.schools.size > 1
    val canViewMarks = uiState.canView(AppModule.MARKS)
    val canViewTimetable = uiState.canView(AppModule.TIMETABLE)
    val canViewCoScholastic = uiState.canView(AppModule.CO_SCHOLASTIC)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("teacher_my_day_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
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
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = "My Day",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Day • ${summary.dayOfWeek.fullLabel}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${summary.teacher.name}  •  ${summary.dateIso}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (canViewTimetable) {
                    TextButton(
                        onClick = onOpenTimetable,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("my_day_open_timetable_button")
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Full View")
                    }
                }
            }

            // Quick KPI Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MyDayMetricPill(
                    label = "Today",
                    value = "${summary.todayRegularPeriods.size} period(s)",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer
                )
                MyDayMetricPill(
                    label = "Substitutions",
                    value = "${summary.todaySubstitutions.size} today",
                    container = if (summary.todaySubstitutions.isNotEmpty()) AbsentAmberContainer else MaterialTheme.colorScheme.surfaceVariant,
                    content = if (summary.todaySubstitutions.isNotEmpty()) Color(0xFF4E2D00) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canViewMarks) {
                    MyDayMetricPill(
                        label = "Pending Marks",
                        value = "${summary.pendingMarksTasks.size} task(s)",
                        container = if (summary.pendingMarksTasks.isNotEmpty()) FailRedContainer else PassGreenContainer,
                        content = if (summary.pendingMarksTasks.isNotEmpty()) FailRed else PassGreen
                    )
                }
            }

            // 1. Today's Substitutions Assigned to Me
            if (summary.todaySubstitutions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AbsentAmberContainer.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("my_day_substitutions_section")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Substitutions",
                                tint = AbsentAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Today's Substitutions Assigned to You (${summary.todaySubstitutions.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4E2D00)
                            )
                        }
                        summary.todaySubstitutions.forEach { sub ->
                            MyDayPeriodRow(item = sub, showSchool = isMultiSchoolTeacher)
                        }
                    }
                }
            }

            // 2. Today's Timetable / Weekly Timetable Switcher
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showWeeklySchedule) "Weekly Timetable" else "Today's Timetable",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !showWeeklySchedule,
                            onClick = { showWeeklySchedule = false },
                            label = { Text("Today") },
                            modifier = Modifier.testTag("my_day_toggle_today")
                        )
                        FilterChip(
                            selected = showWeeklySchedule,
                            onClick = { showWeeklySchedule = true },
                            label = { Text("Weekly") },
                            modifier = Modifier.testTag("my_day_toggle_weekly")
                        )
                    }
                }

                if (showWeeklySchedule) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DayOfWeekCode.entries.filter { it != DayOfWeekCode.SUN }.forEach { day ->
                            val count = summary.weeklyPeriodsByDay[day].orEmpty().size
                            FilterChip(
                                selected = selectedWeekDay == day,
                                onClick = { selectedWeekDay = day },
                                label = { Text("${day.shortLabel} ($count)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("my_day_week_${day.wireValue}")
                            )
                        }
                    }
                }

                val periodsToDisplay = if (showWeeklySchedule) {
                    summary.weeklyPeriodsByDay[selectedWeekDay].orEmpty()
                } else {
                    summary.todayRegularPeriods
                }

                if (periodsToDisplay.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showWeeklySchedule) {
                                "No regular periods scheduled on ${selectedWeekDay.fullLabel}."
                            } else {
                                "No regular periods scheduled for today (${summary.dayOfWeek.fullLabel})."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        periodsToDisplay.forEach { period ->
                            MyDayPeriodRow(item = period, showSchool = isMultiSchoolTeacher)
                        }
                    }
                }
            }

            // 3. Class Teacher Sections (if any)
            if (summary.classTeacherSections.isNotEmpty() && canViewCoScholastic) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "My Class Teacher Section(s)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        summary.classTeacherSections.forEach { (schId, cls, sec) ->
                            OutlinedButton(
                                onClick = { onOpenCoScholasticForSection(schId, cls, sec) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("my_day_ct_section_${cls}_$sec")
                            ) {
                                Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Class $cls-$sec • Co-Scholastic & Att.")
                            }
                        }
                    }
                }
            }

            // 4. My Pending Marks Tasks
            if (canViewMarks) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "My Pending Marks (${summary.pendingMarksTasks.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = onOpenPendingMarks,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("my_day_view_all_pending_button")
                    ) {
                        Text("View All")
                    }
                }

                if (summary.pendingMarksTasks.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PassGreenContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PassGreen, modifier = Modifier.size(18.dp))
                            Text(
                                text = "All assigned class-subject marks are up to date!",
                                style = MaterialTheme.typography.bodySmall,
                                color = PassGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        summary.pendingMarksTasks.take(4).forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clickable { onOpenMarksForItem(item) }
                                    .testTag("my_day_pending_${item.className}_${item.section}_${item.subjectName}_${item.exam.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = "Enter marks",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Class ${item.className}-${item.section} • ${item.subjectName} (${item.exam.label})",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.enteredStudents} of ${item.totalStudents} entered" +
                                                if (isMultiSchoolTeacher) " • ${item.schoolName}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Enter",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (summary.pendingMarksTasks.size > 4) {
                            Text(
                                text = "+${summary.pendingMarksTasks.size - 4} more pending task(s). Tap View All to filter.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyDayPeriodRow(
    item: MyDayPeriodItem,
    showSchool: Boolean
) {
    val timeText = "${Phase4Validator.formatMinutes(item.startMinutes)}–${Phase4Validator.formatMinutes(item.endMinutes)}"
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (item.isSubstitution) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (item.isSubstitution) AbsentAmber.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = if (item.isSubstitution) AbsentAmber else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = item.slotLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Class ${item.className}-${item.section}  •  ${item.subjectName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = buildString {
                    if (showSchool) append(item.schoolName)
                    if (item.isSubstitution && item.replacingTeacherName.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append("Covering for ${item.replacingTeacherName}")
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isSubstitution) AbsentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MyDayMetricPill(
    label: String,
    value: String,
    container: Color,
    content: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.8f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = content
            )
        }
    }
}

/**
 * Phase 7 Admin / Operator Home Pending Summary Tile showing incomplete marks counts per exam
 * (UT1, UT2, Half-Yearly, Annual) with 1-tap navigation to the full Marks Pending List.
 */
@Composable
fun AdminPendingMarksSummaryCard(
    uiState: SchoolUiState,
    onOpenPendingMarks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val examSummaries = remember(
        uiState.filter.session,
        uiState.multiSchoolPendingBundles,
        uiState.allStudents,
        uiState.allMarks,
        uiState.classAssignments,
        uiState.teachers
    ) {
        uiState.pendingMarksExamSummaries()
    }
    val totalPendingTasks = remember(examSummaries) { examSummaries.sumOf { it.pendingTaskCount } }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (totalPendingTasks > 0) AbsentAmber.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("admin_pending_marks_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                    Icon(
                        imageVector = Icons.Default.AssignmentLate,
                        contentDescription = "Pending marks summary",
                        tint = if (totalPendingTasks > 0) AbsentAmber else PassGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Marks Pending by Exam",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (totalPendingTasks > 0) {
                                "$totalPendingTasks incomplete class-subject mark sheet(s) across ${uiState.filter.session}"
                            } else {
                                "All exam marks entered for ${uiState.filter.session}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                examSummaries.forEach { sum ->
                    val hasPending = sum.pendingTaskCount > 0
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (hasPending) AbsentAmberContainer else PassGreenContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenPendingMarks)
                            .testTag("admin_pending_tile_${sum.exam.name}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = sum.exam.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasPending) Color(0xFF4E2D00) else PassGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sum.pendingTaskCount.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (hasPending) AbsentAmber else PassGreen
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onOpenPendingMarks,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("admin_open_pending_marks_button")
            ) {
                Text("Open Marks Pending List & Reminders")
            }
        }
    }
}
