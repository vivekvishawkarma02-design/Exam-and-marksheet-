package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.Phase6DateUtils

private enum class StudentAttendanceTab(val label: String, val tag: String) {
    DAILY("Daily", "satt_tab_daily"),
    CALENDAR("Calendar", "satt_tab_calendar"),
    TERM_SUMMARY("Term Summary", "satt_tab_term_summary"),
    REPORTS("Reports & PDF", "satt_tab_reports")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase8StudentAttendanceScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit,
    initialSchoolDocId: String? = null,
    initialClassName: String? = null,
    initialSection: String? = null,
    initialDateIso: String? = null,
    initialTabIndex: Int? = null,
    onInitialJumpConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()

    val defaultCtSection = remember(uiState.effectiveOwnTeacherScope) {
        uiState.effectiveOwnTeacherScope?.classTeacherSections?.firstOrNull()?.split("|")
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedSchoolDocId by remember {
        mutableStateOf(
            initialSchoolDocId?.takeIf { it.isNotBlank() }
                ?: defaultCtSection?.getOrNull(0)?.takeIf { !uiState.canViewSectionAttendance(uiState.activeSchoolDocId, uiState.effectiveClassName, uiState.filter.section) }
                ?: uiState.activeSchoolDocId
        )
    }
    var selectedClassName by remember {
        mutableStateOf(
            initialClassName?.takeIf { it.isNotBlank() }
                ?: defaultCtSection?.getOrNull(1)?.takeIf { !uiState.canViewSectionAttendance(selectedSchoolDocId, uiState.effectiveClassName, uiState.filter.section) }
                ?: uiState.effectiveClassName
        )
    }
    var selectedSection by remember {
        mutableStateOf(
            initialSection?.takeIf { it.isNotBlank() }
                ?: defaultCtSection?.getOrNull(2)?.takeIf { !uiState.canViewSectionAttendance(selectedSchoolDocId, selectedClassName, uiState.filter.section) }
                ?: uiState.filter.section
        )
    }
    var selectedDateIso by remember {
        mutableStateOf(initialDateIso?.takeIf { Phase6DateUtils.isValidIsoDate(it) } ?: Phase6DateUtils.todayIso())
    }

    LaunchedEffect(initialSchoolDocId, initialClassName, initialSection, initialDateIso, initialTabIndex) {
        var consumed = false
        if (!initialSchoolDocId.isNullOrBlank()) {
            selectedSchoolDocId = initialSchoolDocId
            consumed = true
        }
        if (!initialClassName.isNullOrBlank()) {
            selectedClassName = initialClassName
            consumed = true
        }
        if (!initialSection.isNullOrBlank()) {
            selectedSection = initialSection
            consumed = true
        }
        if (!initialDateIso.isNullOrBlank() && Phase6DateUtils.isValidIsoDate(initialDateIso)) {
            selectedDateIso = initialDateIso
            consumed = true
        }
        if (initialTabIndex != null && initialTabIndex in StudentAttendanceTab.entries.indices) {
            selectedTabIndex = initialTabIndex
            consumed = true
        }
        if (consumed) onInitialJumpConsumed()
    }

    // Keep school valid if state initializes after screen opens
    LaunchedEffect(uiState.activeSchoolDocId) {
        if (selectedSchoolDocId.isBlank() && uiState.activeSchoolDocId.isNotBlank()) {
            selectedSchoolDocId = uiState.activeSchoolDocId
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("student_attendance_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Student Attendance",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val schName = uiState.schoolRecordsByDocId[selectedSchoolDocId]?.name ?: uiState.activeSchool.name
                        Text(
                            text = "$schName • Session ${uiState.filter.session}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 12.dp
                ) {
                    StudentAttendanceTab.entries.forEachIndexed { idx, tab ->
                        Tab(
                            selected = selectedTabIndex == idx,
                            onClick = { selectedTabIndex = idx },
                            text = { Text(tab.label, maxLines = 1) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        }

        when (StudentAttendanceTab.entries[selectedTabIndex]) {
            StudentAttendanceTab.DAILY -> Phase8DailyAttendanceTab(
                uiState = uiState,
                viewModel = viewModel,
                selectedSchoolDocId = selectedSchoolDocId,
                onSelectSchoolDocId = { selectedSchoolDocId = it },
                selectedClassName = selectedClassName,
                onSelectClassName = { selectedClassName = it },
                selectedSection = selectedSection,
                onSelectSection = { selectedSection = it },
                selectedDateIso = selectedDateIso,
                onSelectDateIso = { selectedDateIso = it }
            )

            StudentAttendanceTab.CALENDAR -> Phase8SchoolCalendarTab(
                uiState = uiState,
                viewModel = viewModel,
                selectedSchoolDocId = selectedSchoolDocId,
                onSelectSchoolDocId = { selectedSchoolDocId = it }
            )

            StudentAttendanceTab.TERM_SUMMARY -> Phase8TermSummaryTab(
                uiState = uiState,
                viewModel = viewModel,
                selectedSchoolDocId = selectedSchoolDocId,
                selectedClassName = selectedClassName,
                selectedSection = selectedSection
            )

            StudentAttendanceTab.REPORTS -> Phase8AttendanceReportsTab(
                uiState = uiState,
                viewModel = viewModel,
                selectedSchoolDocId = selectedSchoolDocId,
                selectedClassName = selectedClassName,
                selectedSection = selectedSection,
                selectedDateIso = selectedDateIso
            )
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
