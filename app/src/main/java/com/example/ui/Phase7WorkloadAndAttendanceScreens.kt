package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.AppModule
import com.example.domain.Phase6DateUtils
import com.example.domain.TeacherAttendanceStatus
import com.example.domain.TeacherDailyAttendanceRow
import com.example.domain.TeacherMonthlyAttendanceSummaryRow
import com.example.domain.TeacherProfile
import com.example.domain.TeacherWorkloadReportRow
import com.example.domain.WorkloadReportPeriodMode
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

// =============================================================================
// 1. PHASE 7 WORKLOAD REPORT SCREEN (A4 PDF + CSV + MULTI-SCHOOL)
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkloadReportScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()
    var selectedSchoolDocId by remember { mutableStateOf("") } // "" = All Schools
    var selectedTeacherId by remember { mutableStateOf("") } // "" = All Teachers
    var periodMode by remember { mutableStateOf(WorkloadReportPeriodMode.WEEK) }
    var referenceDateIso by remember { mutableStateOf(Phase6DateUtils.todayIsoDate()) }

    val schoolOptions = remember(uiState.activeSchools, uiState.schoolDocIdByLongId) {
        listOf("" to "All Schools") + uiState.activeSchools.mapNotNull { sch ->
            uiState.schoolDocIdByLongId[sch.id]?.let { it to sch.name }
        }
    }
    val allTeachers = remember(uiState.teachers, uiState.timetableCells) {
        uiState.availableTimetableTeachers()
    }
    val teacherOptions = remember(allTeachers) {
        listOf("" to "All Teachers") + allTeachers.map { it.id to it.name }
    }

    val rows: List<TeacherWorkloadReportRow> = remember(
        referenceDateIso,
        selectedSchoolDocId,
        selectedTeacherId,
        uiState.filter.session,
        uiState.teachers,
        uiState.activeSessionTimetableCells,
        uiState.classAssignments,
        uiState.substitutions,
        uiState.effectivePeriodStructuresBySchool,
        uiState.schoolNamesByDocId
    ) {
        uiState.computeWorkloadReport(
            referenceDateIso = referenceDateIso,
            schoolIdFilter = selectedSchoolDocId.ifBlank { null },
            teacherIdFilter = selectedTeacherId.ifBlank { null }
        )
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportWorkloadCsvToUri(
                destinationUri = uri,
                referenceDateIso = referenceDateIso,
                periodMode = periodMode,
                schoolIdFilter = selectedSchoolDocId.ifBlank { null },
                teacherIdFilter = selectedTeacherId.ifBlank { null }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("workload_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Teacher Workload Report",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Session ${uiState.filter.session} • ${rows.size} teacher(s) • Periods, Free & Substitutions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Filters: School & Teacher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (schoolOptions.size > 2) {
                        Box(modifier = Modifier.weight(1f)) {
                            CompactDropdownSelector(
                                label = "School Scope",
                                selectedValue = schoolOptions.firstOrNull { it.first == selectedSchoolDocId }?.second ?: "All Schools",
                                options = schoolOptions.map { it.second },
                                onSelect = { picked ->
                                    selectedSchoolDocId = schoolOptions.firstOrNull { it.second == picked }?.first.orEmpty()
                                },
                                testTag = "workload_school_filter"
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CompactDropdownSelector(
                            label = "Teacher",
                            selectedValue = teacherOptions.firstOrNull { it.first == selectedTeacherId }?.second ?: "All Teachers",
                            options = teacherOptions.map { it.second },
                            onSelect = { picked ->
                                selectedTeacherId = teacherOptions.firstOrNull { it.second == picked }?.first.orEmpty()
                            },
                            testTag = "workload_teacher_filter"
                        )
                    }
                }

                // Period Mode + Reference Date Stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WorkloadReportPeriodMode.entries.forEach { mode ->
                            FilterChip(
                                selected = periodMode == mode,
                                onClick = { periodMode = mode },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("workload_mode_${mode.name}")
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val delta = if (periodMode == WorkloadReportPeriodMode.WEEK) -7 else -30
                                referenceDateIso = Phase6DateUtils.addDays(referenceDateIso, delta)
                            },
                            modifier = Modifier.testTag("workload_prev_period")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
                        }
                        Text(
                            text = referenceDateIso,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                val delta = if (periodMode == WorkloadReportPeriodMode.WEEK) 7 else 30
                                referenceDateIso = Phase6DateUtils.addDays(referenceDateIso, delta)
                            },
                            modifier = Modifier.testTag("workload_next_period")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
                        }
                    }
                }

                // Export Actions Row: A4 PDF & CSV
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.generateWorkloadReportPdf(
                                referenceDateIso = referenceDateIso,
                                periodMode = periodMode,
                                schoolIdFilter = selectedSchoolDocId.ifBlank { null },
                                teacherIdFilter = selectedTeacherId.ifBlank { null }
                            )
                        },
                        enabled = rows.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("workload_export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export A4 PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            exportCsvLauncher.launch("Workload_Report_${referenceDateIso}.csv")
                        },
                        enabled = rows.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("workload_export_csv_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("workload_report_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rows.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "No teachers match the selected filters yet. Add teachers or timetable periods to view workload.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(rows, key = { it.teacherId }) { row ->
                    WorkloadTeacherCard(
                        row = row,
                        periodMode = periodMode,
                        schoolNamesByDocId = uiState.schoolNamesByDocId
                    )
                }
            }
        }
    }

    if (pdfState.isGenerating || pdfState.generatedFile != null || pdfState.error != null) {
        TimetablePdfPreviewDialog(
            pdfState = pdfState,
            onSavePdfToUri = viewModel::saveTimetablePdfToUri,
            onSaveZipToUri = viewModel::saveTimetableZipToUri,
            onDismiss = viewModel::clearTimetablePdfPreview
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkloadTeacherCard(
    row: TeacherWorkloadReportRow,
    periodMode: WorkloadReportPeriodMode,
    schoolNamesByDocId: Map<String, String>
) {
    val totalLoad = if (periodMode == WorkloadReportPeriodMode.WEEK) row.totalLoadThisWeek else row.totalLoadThisMonth
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workload_card_${row.teacherId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.teacherName + if (!row.active) " (Inactive)" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (row.schoolNames.isNotEmpty()) {
                        Text(
                            text = row.schoolNames.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Total: $totalLoad",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WorkloadStatChip("Periods/wk", "${row.scheduledPeriodsPerWeek}/${row.maxWeeklyCapacity}", Modifier.weight(1f))
                WorkloadStatChip("Free/wk", "${row.freePeriodsPerWeek}", Modifier.weight(1f))
                WorkloadStatChip("Subs Wk", "${row.substitutionsThisWeek}", Modifier.weight(1f))
                WorkloadStatChip("Subs Mo", "${row.substitutionsThisMonth}", Modifier.weight(1f))
            }

            if (row.schoolIds.size > 1) {
                val perSchoolSummary = row.schoolIds.joinToString("  |  ") { schId ->
                    val sName = schoolNamesByDocId[schId] ?: schId
                    val p = row.perSchoolScheduledPeriods[schId] ?: 0
                    val sub = if (periodMode == WorkloadReportPeriodMode.WEEK) {
                        row.perSchoolSubsWeek[schId] ?: 0
                    } else {
                        row.perSchoolSubsMonth[schId] ?: 0
                    }
                    "$sName: ${p}p + ${sub} sub"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Multi-school: $perSchoolSummary",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (row.classSubjectBreakdown.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.classSubjectBreakdown.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "${item.className}-${item.section} ${item.subjectName} (${item.periodsPerWeek})",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkloadStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

// =============================================================================
// 2. PHASE 7 TEACHER ATTENDANCE SCREEN (DAILY SHEET + MONTHLY SUMMARY + A4 PDF)
// =============================================================================

@Composable
fun TeacherAttendanceScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit,
    onJumpToSubstitutionsForDate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val canEdit = uiState.canEdit(AppModule.TIMETABLE)
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Daily Sheet, 1 = Monthly Summary
    var selectedSchoolDocId by remember(uiState.activeSchoolDocId) {
        mutableStateOf(
            uiState.activeSchoolDocId.ifBlank {
                uiState.activeSchools.firstOrNull()?.let { uiState.schoolDocIdByLongId[it.id] }.orEmpty()
            }
        )
    }
    LaunchedEffect(uiState.activeSchools, uiState.schoolDocIdByLongId) {
        if (selectedSchoolDocId.isBlank()) {
            selectedSchoolDocId = uiState.activeSchoolDocId.ifBlank {
                uiState.activeSchools.firstOrNull()?.let { uiState.schoolDocIdByLongId[it.id] }.orEmpty()
            }
        }
    }

    var selectedDateIso by remember { mutableStateOf(Phase6DateUtils.todayIsoDate()) }
    val todayCal = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH) + 1) }

    val monthLabel = remember(selectedYear, selectedMonth) {
        val cal = GregorianCalendar(selectedYear, (selectedMonth - 1).coerceIn(0, 11), 1)
        SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    }

    val dailyRows: List<TeacherDailyAttendanceRow> = remember(
        selectedSchoolDocId,
        selectedDateIso,
        uiState.teachers,
        uiState.timetableCells,
        uiState.teacherAttendanceRecords,
        uiState.teacherLeaves
    ) {
        if (selectedSchoolDocId.isBlank()) emptyList()
        else uiState.buildDailyTeacherAttendance(selectedSchoolDocId, selectedDateIso)
    }

    val monthlyRows: List<TeacherMonthlyAttendanceSummaryRow> = remember(
        selectedSchoolDocId,
        selectedYear,
        selectedMonth,
        uiState.teachers,
        uiState.timetableCells,
        uiState.teacherAttendanceRecords,
        uiState.teacherLeaves,
        uiState.effectivePeriodStructuresBySchool
    ) {
        if (selectedSchoolDocId.isBlank()) emptyList()
        else uiState.buildMonthlyTeacherAttendance(selectedSchoolDocId, selectedYear, selectedMonth)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("teacher_att_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Teacher Attendance",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${uiState.schoolNameByDocId(selectedSchoolDocId)} • ${if (canEdit) "Timetable edit access" else "View only"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (uiState.activeSchools.size > 1) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                        CompactDropdownSelector(
                            label = "School",
                            selectedValue = uiState.schoolNameByDocId(selectedSchoolDocId),
                            options = uiState.activeSchools.map { it.name },
                            onSelect = { picked ->
                                uiState.activeSchools.firstOrNull { it.name == picked }?.let { s ->
                                    uiState.schoolDocIdByLongId[s.id]?.let { selectedSchoolDocId = it }
                                }
                            },
                            testTag = "teacher_att_school_picker"
                        )
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Daily Sheet") },
                        modifier = Modifier.testTag("teacher_att_tab_daily")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Monthly Summary") },
                        modifier = Modifier.testTag("teacher_att_tab_monthly")
                    )
                }
            }
        }

        if (selectedTab == 0) {
            // DAILY SHEET
            val presentCount = dailyRows.count { it.effectiveStatus == TeacherAttendanceStatus.PRESENT }
            val halfDayCount = dailyRows.count { it.effectiveStatus == TeacherAttendanceStatus.HALF_DAY }
            val leaveCount = dailyRows.count { it.effectiveStatus == TeacherAttendanceStatus.ON_LEAVE }
            val absentCount = dailyRows.count { it.effectiveStatus == TeacherAttendanceStatus.ABSENT }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("teacher_att_daily_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = { selectedDateIso = Phase6DateUtils.addDays(selectedDateIso, -1) },
                                    modifier = Modifier.testTag("teacher_att_prev_day")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val dow = Phase6DateUtils.dayOfWeekForDate(selectedDateIso)?.fullLabel.orEmpty()
                                    Text(
                                        text = "$dow, $selectedDateIso",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = { selectedDateIso = Phase6DateUtils.todayIsoDate() },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("Jump to Today")
                                    }
                                }
                                IconButton(
                                    onClick = { selectedDateIso = Phase6DateUtils.addDays(selectedDateIso, 1) },
                                    modifier = Modifier.testTag("teacher_att_next_day")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AttendanceBadgeChip("Present: $presentCount", PassGreenContainer, PassGreen, Modifier.weight(1f))
                                AttendanceBadgeChip("Half: $halfDayCount", AbsentAmberContainer, Color(0xFF4E2D00), Modifier.weight(1f))
                                AttendanceBadgeChip("Leave: $leaveCount", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f))
                                AttendanceBadgeChip("Absent: $absentCount", FailRedContainer, FailRed, Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (dailyRows.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text(
                                text = "No active teachers assigned to this school yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(dailyRows, key = { it.teacher.id }) { row ->
                        DailyTeacherAttendanceCard(
                            row = row,
                            canEdit = canEdit,
                            onSelectStatus = { newStatus ->
                                viewModel.saveTeacherAttendance(
                                    schoolDocId = selectedSchoolDocId,
                                    dateIso = selectedDateIso,
                                    teacher = row.teacher,
                                    status = newStatus,
                                    linkedLeaveId = row.approvedLeave?.id.orEmpty()
                                )
                            },
                            onCreateLeaveAndOpenSubs = {
                                viewModel.markTeacherAbsentAndCreateLeave(
                                    schoolDocId = selectedSchoolDocId,
                                    dateIso = selectedDateIso,
                                    teacher = row.teacher,
                                    onReadyForSubstitutions = { date ->
                                        onJumpToSubstitutionsForDate(date)
                                    }
                                )
                            },
                            onOpenExistingSubs = {
                                onJumpToSubstitutionsForDate(selectedDateIso)
                            }
                        )
                    }
                }
            }
        } else {
            // MONTHLY SUMMARY
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("teacher_att_monthly_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {
                                        if (selectedMonth == 1) {
                                            selectedMonth = 12
                                            selectedYear -= 1
                                        } else {
                                            selectedMonth -= 1
                                        }
                                    },
                                    modifier = Modifier.testTag("teacher_att_prev_month")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                                }
                                Text(
                                    text = monthLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        if (selectedMonth == 12) {
                                            selectedMonth = 1
                                            selectedYear += 1
                                        } else {
                                            selectedMonth += 1
                                        }
                                    },
                                    modifier = Modifier.testTag("teacher_att_next_month")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.generateMonthlyTeacherAttendancePdf(
                                        schoolDocId = selectedSchoolDocId,
                                        year = selectedYear,
                                        month1Based = selectedMonth,
                                        monthLabel = monthLabel
                                    )
                                },
                                enabled = monthlyRows.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("teacher_att_export_pdf_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Monthly Attendance A4 PDF")
                            }
                        }
                    }
                }

                items(monthlyRows, key = { it.teacherId }) { row ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = row.teacherName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                val pctText = String.format(Locale.US, "%.1f%%", row.attendancePercent)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = pctText,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                WorkloadStatChip("Present", "${row.presentDays}", Modifier.weight(1f))
                                WorkloadStatChip("Half-Day", "${row.halfDays}", Modifier.weight(1f))
                                WorkloadStatChip("Leave", "${row.onLeaveDays}", Modifier.weight(1f))
                                WorkloadStatChip("Absent", "${row.absentDays}", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (pdfState.isGenerating || pdfState.generatedFile != null || pdfState.error != null) {
        TimetablePdfPreviewDialog(
            pdfState = pdfState,
            onSavePdfToUri = viewModel::saveTimetablePdfToUri,
            onSaveZipToUri = viewModel::saveTimetableZipToUri,
            onDismiss = viewModel::clearTimetablePdfPreview
        )
    }
}

@Composable
private fun DailyTeacherAttendanceCard(
    row: TeacherDailyAttendanceRow,
    canEdit: Boolean,
    onSelectStatus: (TeacherAttendanceStatus) -> Unit,
    onCreateLeaveAndOpenSubs: () -> Unit,
    onOpenExistingSubs: () -> Unit
) {
    val statuses = listOf(
        TeacherAttendanceStatus.PRESENT,
        TeacherAttendanceStatus.HALF_DAY,
        TeacherAttendanceStatus.ON_LEAVE,
        TeacherAttendanceStatus.ABSENT
    )
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (row.effectiveStatus == TeacherAttendanceStatus.ABSENT) 1.5.dp else 1.dp,
            color = if (row.effectiveStatus == TeacherAttendanceStatus.ABSENT) FailRed.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("att_row_${row.teacher.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.teacher.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (row.isAutoFromApprovedLeave && row.approvedLeave != null) {
                        Text(
                            text = "Auto from approved leave (${row.approvedLeave.reasonType.label})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (row.effectiveStatus) {
                        TeacherAttendanceStatus.PRESENT -> PassGreenContainer
                        TeacherAttendanceStatus.HALF_DAY -> AbsentAmberContainer
                        TeacherAttendanceStatus.ON_LEAVE -> MaterialTheme.colorScheme.secondaryContainer
                        TeacherAttendanceStatus.ABSENT -> FailRedContainer
                        TeacherAttendanceStatus.UNMARKED -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = row.effectiveStatus.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (row.effectiveStatus) {
                            TeacherAttendanceStatus.PRESENT -> PassGreen
                            TeacherAttendanceStatus.HALF_DAY -> Color(0xFF4E2D00)
                            TeacherAttendanceStatus.ON_LEAVE -> MaterialTheme.colorScheme.onSecondaryContainer
                            TeacherAttendanceStatus.ABSENT -> FailRed
                            TeacherAttendanceStatus.UNMARKED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                statuses.forEach { st ->
                    val selected = row.effectiveStatus == st
                    val selectedBg = when (st) {
                        TeacherAttendanceStatus.PRESENT -> PassGreen
                        TeacherAttendanceStatus.HALF_DAY -> AbsentAmber
                        TeacherAttendanceStatus.ON_LEAVE -> MaterialTheme.colorScheme.secondary
                        TeacherAttendanceStatus.ABSENT -> FailRed
                        TeacherAttendanceStatus.UNMARKED -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { if (canEdit) onSelectStatus(st) },
                        enabled = canEdit,
                        label = {
                            Text(
                                text = st.shortCode,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = selectedBg,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("att_btn_${row.teacher.id}_${st.wireValue}")
                    )
                }
            }

            if (row.effectiveStatus == TeacherAttendanceStatus.ABSENT && canEdit) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (row.approvedLeave == null) {
                    Button(
                        onClick = onCreateLeaveAndOpenSubs,
                        colors = ButtonDefaults.buttonColors(containerColor = AbsentAmber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("att_create_leave_subs_${row.teacher.id}")
                    ) {
                        Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Approved Leave & Open Substitutions")
                    }
                } else {
                    OutlinedButton(
                        onClick = onOpenExistingSubs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("att_open_subs_${row.teacher.id}")
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Substitutions for ${row.date}")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceBadgeChip(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = container,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }
}
