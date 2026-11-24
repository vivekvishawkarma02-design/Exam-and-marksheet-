package com.example.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.StudentEntity
import com.example.domain.DayCalendarStatus
import com.example.domain.Phase6DateUtils
import com.example.domain.SchoolCalendarEngine
import com.example.domain.StudentAttendanceAccessEvaluator
import com.example.domain.StudentAttendanceEngine
import com.example.domain.StudentAttendanceEntry
import com.example.domain.StudentAttendanceSheet
import com.example.domain.StudentAttendanceStatus
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Phase8DailyAttendanceTab(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    selectedSchoolDocId: String,
    onSelectSchoolDocId: (String) -> Unit,
    selectedClassName: String,
    onSelectClassName: (String) -> Unit,
    selectedSection: String,
    onSelectSection: (String) -> Unit,
    selectedDateIso: String,
    onSelectDateIso: (String) -> Unit
) {
    val context = LocalContext.current
    val todayIso = remember { Phase6DateUtils.todayIso() }
    val selectedSchoolRecord = uiState.schoolRecordsByDocId[selectedSchoolDocId]
    val selectedSchoolLongId = selectedSchoolRecord?.stableId ?: uiState.activeSchool.id
    val selectedSchoolName = selectedSchoolRecord?.name ?: uiState.activeSchool.name

    // Filter schools/classes/sections visible to this user
    val visibleSchools = remember(uiState.schools, uiState.schoolDocIdByLongId, uiState.accessProfile, uiState.effectiveOwnTeacherScope) {
        uiState.schools.filter { sch ->
            val docId = uiState.schoolDocIdByLongId[sch.id].orEmpty()
            docId.isNotBlank() && (
                uiState.canEditSectionAttendance(docId, "", "") ||
                    uiState.canViewSectionAttendance(docId, "", "") ||
                    uiState.effectiveOwnTeacherScope?.classTeacherSections?.any { it.startsWith("$docId|") } == true
                )
        }
    }

    val schoolStudents = remember(uiState.allStudents, selectedSchoolLongId, uiState.filter.session) {
        uiState.allStudents.filter {
            it.schoolId == selectedSchoolLongId && it.session == uiState.filter.session
        }
    }

    val availableSectionsList = remember(
        schoolStudents,
        uiState.schoolClasses,
        selectedSchoolLongId,
        selectedSchoolDocId,
        uiState.accessProfile,
        uiState.effectiveOwnTeacherScope
    ) {
        val fromStudents = schoolStudents.map { it.className to it.section.uppercase().ifBlank { "A" } }
        val fromClasses = uiState.schoolClasses
            .filter { it.schoolId == selectedSchoolLongId }
            .map { it.name to "A" }
        val fromCtScope = uiState.effectiveOwnTeacherScope?.classTeacherSections
            ?.mapNotNull { key ->
                val p = key.split("|")
                if (p.size == 3 && p[0] == selectedSchoolDocId) p[1] to p[2] else null
            }
            .orEmpty()
        (fromStudents + fromCtScope + fromClasses).distinct()
            .filter { (cls, sec) -> uiState.canViewSectionAttendance(selectedSchoolDocId, cls, sec) }
            .sortedWith(compareBy({ it.first.lowercase() }, { it.second }))
    }

    val visibleClassNames = remember(availableSectionsList) {
        availableSectionsList.map { it.first }.distinct()
    }
    val effectiveClass = if (selectedClassName in visibleClassNames) selectedClassName
        else visibleClassNames.firstOrNull().orEmpty()
    val sectionsForClass = remember(availableSectionsList, effectiveClass) {
        availableSectionsList.filter { it.first.equals(effectiveClass, ignoreCase = true) }.map { it.second }.distinct()
    }
    val effectiveSection = if (selectedSection in sectionsForClass) selectedSection
        else sectionsForClass.firstOrNull() ?: "A"

    LaunchedEffect(effectiveClass, effectiveSection) {
        if (effectiveClass.isNotBlank() && effectiveClass != selectedClassName) onSelectClassName(effectiveClass)
        if (effectiveSection.isNotBlank() && effectiveSection != selectedSection) onSelectSection(effectiveSection)
    }

    val sectionStudents: List<Pair<String, StudentEntity>> = remember(
        schoolStudents,
        effectiveClass,
        effectiveSection,
        uiState.studentDocIdByLongId
    ) {
        schoolStudents
            .filter {
                it.className.equals(effectiveClass, ignoreCase = true) &&
                    it.section.equals(effectiveSection, ignoreCase = true)
            }
            .sortedWith(compareBy<StudentEntity> { it.rollNo }.thenBy { it.name.lowercase() })
            .mapNotNull { st ->
                val docId = uiState.studentDocIdFor(st.id)
                if (docId.isBlank()) null else docId to st
            }
    }

    val calendar = uiState.calendarForSchool(selectedSchoolDocId, uiState.filter.session)
    val dayStatus = remember(calendar, selectedDateIso) {
        SchoolCalendarEngine.dayStatus(calendar, selectedDateIso)
    }
    val isNonWorkingDay = dayStatus is DayCalendarStatus.NonWorking

    val sheetDocId = remember(selectedSchoolDocId, uiState.filter.session, effectiveClass, effectiveSection, selectedDateIso) {
        StudentAttendanceSheet.makeDocId(
            selectedSchoolDocId,
            uiState.filter.session,
            effectiveClass,
            effectiveSection,
            selectedDateIso
        )
    }
    val savedSheet = remember(uiState.studentAttendanceSheets, sheetDocId) {
        uiState.studentAttendanceSheets.firstOrNull { it.id == sheetDocId }
    }

    val daysBackLimit = selectedSchoolRecord?.classTeacherAttendanceEditDaysBack
        ?: StudentAttendanceAccessEvaluator.DEFAULT_CLASS_TEACHER_DAYS_BACK
    val editDateError = remember(
        uiState.accessProfile,
        uiState.effectiveOwnTeacherScope,
        selectedSchoolDocId,
        effectiveClass,
        effectiveSection,
        selectedDateIso,
        todayIso,
        daysBackLimit
    ) {
        StudentAttendanceAccessEvaluator.validateEditDateLimit(
            member = uiState.accessProfile,
            teacherScope = uiState.effectiveOwnTeacherScope,
            schoolId = selectedSchoolDocId,
            className = effectiveClass,
            section = effectiveSection,
            targetDateIso = selectedDateIso,
            todayIso = todayIso,
            classTeacherDaysBackLimit = daysBackLimit
        )
    }
    val canEditThisSheet = editDateError == null && !isNonWorkingDay && sectionStudents.isNotEmpty()

    // Draft attendance map: every student starts as PRESENT when no sheet is saved yet
    val draftEntries = remember { mutableStateMapOf<String, StudentAttendanceEntry>() }
    var hasLocalChanges by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showEditHistoryDialog by remember { mutableStateOf(false) }
    var showAdminTodayStatusDialog by remember { mutableStateOf(false) }
    var showExceptionDialog by remember { mutableStateOf(false) }
    var exceptionNoteInput by remember { mutableStateOf("Extra working day / Make-up class") }
    var activeStudentChooser by remember { mutableStateOf<Pair<String, StudentEntity>?>(null) }

    // Reload draft when switching school/class/section/date or when savedSheet updates from server
    LaunchedEffect(sheetDocId, savedSheet?.clientEditedAtMs, sectionStudents.map { it.first }) {
        draftEntries.clear()
        for ((docId, _) in sectionStudents) {
            val existing = savedSheet?.entries?.get(docId)
            draftEntries[docId] = existing ?: StudentAttendanceEntry(StudentAttendanceStatus.PRESENT)
        }
        hasLocalChanges = false
    }

    // Live counts
    var presentCount = 0
    var lateCount = 0
    var halfDayCount = 0
    var leaveCount = 0
    var absentCount = 0
    for ((docId, _) in sectionStudents) {
        when ((draftEntries[docId] ?: StudentAttendanceEntry()).status) {
            StudentAttendanceStatus.PRESENT -> presentCount++
            StudentAttendanceStatus.LATE -> lateCount++
            StudentAttendanceStatus.HALF_DAY -> halfDayCount++
            StudentAttendanceStatus.LEAVE -> leaveCount++
            StudentAttendanceStatus.ABSENT -> absentCount++
        }
    }

    val filteredStudents = remember(sectionStudents, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) sectionStudents
        else sectionStudents.filter { (_, st) ->
            st.name.lowercase().contains(q) || st.rollNo.toString().contains(q) || st.admNo.lowercase().contains(q)
        }
    }

    val absentStudents = remember(sectionStudents, draftEntries.toMap()) {
        sectionStudents.mapNotNull { (docId, st) ->
            val entry = draftEntries[docId]
            if (entry?.status == StudentAttendanceStatus.ABSENT) st else null
        }
    }

    val sectionEditLogs = remember(uiState.studentAttendanceEditLogs, sheetDocId) {
        uiState.studentAttendanceEditLogs.filter { it.attendanceDocId == sheetDocId }
    }

    fun sharePlainText(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("phase8_daily_attendance_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Conflict notice banner (matches Phase 3 latest-edit-wins notice)
        if (uiState.attendanceConflictNotices.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Latest-Edit-Wins Sync Notice",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            val n = uiState.attendanceConflictNotices.last()
                            Text(
                                text = "${n.sectionLabel} (${n.date}) was updated by ${n.winningEditorName}. Showing latest saved attendance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        TextButton(onClick = { viewModel.dismissAttendanceConflictNotices() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        // School + Class + Section + Date Picker Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // School chips (only if multiple schools visible)
                    if (visibleSchools.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            visibleSchools.forEach { sch ->
                                val docId = uiState.schoolDocIdByLongId[sch.id].orEmpty()
                                FilterChip(
                                    selected = docId == selectedSchoolDocId,
                                    onClick = {
                                        onSelectSchoolDocId(docId)
                                        viewModel.selectSchool(sch.id)
                                    },
                                    label = { Text(sch.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    // Class chips
                    if (visibleClassNames.isNotEmpty()) {
                        Text(
                            text = "Class & Section",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            visibleClassNames.forEach { cls ->
                                FilterChip(
                                    selected = cls.equals(effectiveClass, ignoreCase = true),
                                    onClick = { onSelectClassName(cls) },
                                    label = { Text(cls) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                        if (sectionsForClass.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                sectionsForClass.forEach { sec ->
                                    FilterChip(
                                        selected = sec.equals(effectiveSection, ignoreCase = true),
                                        onClick = { onSelectSection(sec) },
                                        label = { Text("Sec $sec") },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Date stepper row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { onSelectDateIso(Phase6DateUtils.addDays(selectedDateIso, -1)) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("attendance_prev_day_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val dow = Phase6DateUtils.dayOfWeekForDate(selectedDateIso)?.fullLabel.orEmpty()
                            Text(
                                text = "${SchoolCalendarEngine.formatShortHumanDate(selectedDateIso)} • $dow",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedDateIso,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (selectedDateIso != todayIso) {
                                    Text(
                                        text = "Jump to Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { onSelectDateIso(todayIso) }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                val next = Phase6DateUtils.addDays(selectedDateIso, 1)
                                if (next <= todayIso) onSelectDateIso(next)
                            },
                            enabled = selectedDateIso < todayIso,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("attendance_next_day_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                        }
                    }

                    // Status indicator + Quick utility actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (savedSheet != null) PassGreenContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (savedSheet != null) Icons.Filled.CheckCircle else Icons.Filled.EditNote,
                                    contentDescription = null,
                                    tint = if (savedSheet != null) PassGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (savedSheet != null) {
                                        if (hasLocalChanges) "Unsaved changes" else "Saved by ${savedSheet.updatedByName.ifBlank { "Staff" }}"
                                    } else {
                                        "Not saved yet"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (savedSheet != null) PassGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (uiState.isAdmin || uiState.canView(com.example.domain.AppModule.ATTENDANCE)) {
                            OutlinedButton(
                                onClick = { showAdminTodayStatusDialog = true },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("All Sections Status")
                            }
                        }

                        if (sectionEditLogs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showEditHistoryDialog = true },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Log (${sectionEditLogs.size})")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (effectiveClass.isNotBlank()) {
                                    viewModel.generateDailyAttendancePdf(
                                        schoolDocId = selectedSchoolDocId,
                                        className = effectiveClass,
                                        section = effectiveSection,
                                        dateIso = selectedDateIso
                                    )
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Daily PDF")
                        }
                    }
                }
            }
        }

        // Half Working Day Info Banner
        if (dayStatus is DayCalendarStatus.Working && (dayStatus.isHalfWorkingDay || dayStatus.isExceptionOverride)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = buildString {
                            if (dayStatus.isHalfWorkingDay) {
                                append("Half Working Day")
                                if (dayStatus.halfDayName.isNotBlank()) append(": ${dayStatus.halfDayName}")
                            }
                            if (dayStatus.isExceptionOverride) {
                                if (isNotEmpty()) append(" • ")
                                append("Admin Working-Day Exception")
                                if (dayStatus.exceptionNote.isNotBlank()) append(": ${dayStatus.exceptionNote}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Holiday / Non-Working Day Blocker + Admin Exception Button
        if (isNonWorkingDay) {
            val reason = (dayStatus as DayCalendarStatus.NonWorking).reasonTitle
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("holiday_blocker_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Filled.EventBusy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Attendance Blocked — $reason",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = "This date is marked as a non-working day on the school calendar. Daily student attendance cannot be entered on holidays or off days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        if (uiState.canEditSchoolCalendar(selectedSchoolDocId)) {
                            Button(
                                onClick = { showExceptionDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("add_working_day_exception_btn")
                            ) {
                                Text("It is a working day — add exception")
                            }
                        }
                    }
                }
            }
        }

        // Days-Back Limit or Read-Only Notice
        if (!isNonWorkingDay && editDateError != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = editDateError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Live Counts Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CountMiniPill("Present", presentCount + lateCount, PassGreen, Modifier.weight(1f))
                CountMiniPill("Absent", absentCount, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                CountMiniPill("Leave", leaveCount, Color(0xFF1D4ED8), Modifier.weight(1f))
                CountMiniPill("Half", halfDayCount, Color(0xFF6D28D9), Modifier.weight(1f))
            }
        }

        // Big Mobile-First Action Buttons ("Mark All Present" + "Save")
        if (canEditThisSheet) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            for ((docId, _) in sectionStudents) {
                                draftEntries[docId] = StudentAttendanceEntry(StudentAttendanceStatus.PRESENT)
                            }
                            hasLocalChanges = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("mark_all_present_btn")
                    ) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mark All Present", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = {
                            val mapToSave = sectionStudents.associate { (docId, _) ->
                                docId to (draftEntries[docId] ?: StudentAttendanceEntry(StudentAttendanceStatus.PRESENT))
                            }
                            viewModel.saveDailyStudentAttendance(
                                schoolDocId = selectedSchoolDocId,
                                className = effectiveClass,
                                section = effectiveSection,
                                dateIso = selectedDateIso,
                                entries = mapToSave
                            ) {
                                hasLocalChanges = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("save_daily_attendance_btn")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Batch Share All Absent Students button (WhatsApp / SMS text only, never auto-sent)
        if (absentStudents.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = {
                        val msg = StudentAttendanceEngine.buildBatchAbsentMessage(
                            schoolName = selectedSchoolName,
                            className = effectiveClass,
                            section = effectiveSection,
                            dateIso = selectedDateIso,
                            absentStudents = absentStudents
                        )
                        sharePlainText(msg, "Share Absentee List")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("share_all_absent_btn")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share All Absent (${absentStudents.size}) via WhatsApp / SMS")
                }
            }
        }

        // Search box
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search student by name or roll number") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("attendance_student_search")
            )
        }

        // Empty state or student list by roll number
        if (filteredStudents.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Text(
                        text = if (sectionStudents.isEmpty()) {
                            "No students found in Class $effectiveClass-$effectiveSection for session ${uiState.filter.session}."
                        } else {
                            "No students match \"$searchQuery\"."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(filteredStudents, key = { it.first }) { (docId, student) ->
                val entry = draftEntries[docId] ?: StudentAttendanceEntry(StudentAttendanceStatus.PRESENT)
                StudentAttendanceRowCard(
                    student = student,
                    entry = entry,
                    canEdit = canEditThisSheet,
                    onCycleStatus = {
                        val next = entry.status.nextCycle()
                        draftEntries[docId] = entry.copy(
                            status = next,
                            reason = if (next == StudentAttendanceStatus.LEAVE || next == StudentAttendanceStatus.ABSENT) entry.reason else ""
                        )
                        hasLocalChanges = true
                    },
                    onOpenChooser = { activeStudentChooser = docId to student },
                    onShareAbsentMsg = {
                        val msg = StudentAttendanceEngine.buildGuardianAbsentMessage(
                            studentName = student.name,
                            className = student.className,
                            section = student.section,
                            dateIso = selectedDateIso,
                            schoolName = selectedSchoolName
                        )
                        sharePlainText(msg, "Share Guardian Absence Message")
                    }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // Quick Chooser & Reason Dialog
    activeStudentChooser?.let { (docId, student) ->
        val current = draftEntries[docId] ?: StudentAttendanceEntry(StudentAttendanceStatus.PRESENT)
        var pickedStatus by remember(docId) { mutableStateOf(current.status) }
        var reasonText by remember(docId) { mutableStateOf(current.reason) }

        AlertDialog(
            onDismissRequest = { activeStudentChooser = null },
            title = { Text("Roll ${student.rollNo} • ${student.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select status:", style = MaterialTheme.typography.labelLarge)
                    StudentAttendanceStatus.entries.forEach { st ->
                        val selected = pickedStatus == st
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { pickedStatus = st }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${st.code} — ${st.label}",
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = "Weight ${st.weight}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Optional reason / note (e.g. fever, family trip)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        draftEntries[docId] = StudentAttendanceEntry(pickedStatus, reasonText.trim())
                        hasLocalChanges = true
                        activeStudentChooser = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeStudentChooser = null }) { Text("Cancel") }
            }
        )
    }

    // Admin Working Day Exception Dialog
    if (showExceptionDialog) {
        AlertDialog(
            onDismissRequest = { showExceptionDialog = false },
            title = { Text("Add Working Day Exception") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Mark $selectedDateIso as a working day for $selectedSchoolName so teachers can take attendance.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = exceptionNoteInput,
                        onValueChange = { exceptionNoteInput = it },
                        label = { Text("Reason / Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addWorkingDayException(selectedSchoolDocId, selectedDateIso, exceptionNoteInput)
                        showExceptionDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Add Exception")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExceptionDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Post-Day / Edit History Dialog
    if (showEditHistoryDialog) {
        val df = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }
        AlertDialog(
            onDismissRequest = { showEditHistoryDialog = false },
            title = { Text("Edit Log • $effectiveClass-$effectiveSection ($selectedDateIso)") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sectionEditLogs, key = { it.id }) { log ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${log.editorName} • ${df.format(Date(log.clientEditedAtMs))}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = log.summary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEditHistoryDialog = false }) { Text("Close") }
            }
        )
    }

    // Admin All-Sections Status Dialog for selectedDateIso
    if (showAdminTodayStatusDialog) {
        val summaries = uiState.computeTodayAttendanceSummaries(selectedDateIso)
        val currentSummary = summaries.firstOrNull { it.schoolDocId == selectedSchoolDocId }
        AlertDialog(
            onDismissRequest = { showAdminTodayStatusDialog = false },
            title = { Text("Section Status • ${SchoolCalendarEngine.formatShortHumanDate(selectedDateIso)}") },
            text = {
                if (currentSummary == null || currentSummary.sectionStatuses.isEmpty()) {
                    Text("No sections found in $selectedSchoolName.")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(currentSummary.sectionStatuses) { sec ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectClassName(sec.className)
                                        onSelectSection(sec.section)
                                        showAdminTodayStatusDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Class ${sec.className}-${sec.section}", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (sec.taken) "Present ${sec.presentEquivalent}/${sec.totalStudents} (${sec.presentPercent}%)" else "Not taken yet",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (sec.taken) PassGreen else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Text(
                                        text = if (sec.taken) "TAKEN" else "PENDING",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sec.taken) PassGreen else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdminTodayStatusDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun CountMiniPill(label: String, count: Int, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StudentAttendanceRowCard(
    student: StudentEntity,
    entry: StudentAttendanceEntry,
    canEdit: Boolean,
    onCycleStatus: () -> Unit,
    onOpenChooser: () -> Unit,
    onShareAbsentMsg: () -> Unit
) {
    val (bgColor, fgColor) = when (entry.status) {
        StudentAttendanceStatus.PRESENT -> PassGreenContainer to PassGreen
        StudentAttendanceStatus.ABSENT -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        StudentAttendanceStatus.LEAVE -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
        StudentAttendanceStatus.LATE -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        StudentAttendanceStatus.HALF_DAY -> Color(0xFFEDE9FE) to Color(0xFF6D28D9)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canEdit) { onOpenChooser() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Roll badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = student.rollNo.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subText = buildString {
                    append("Adm ${student.admNo}")
                    if (entry.reason.isNotBlank()) append(" • ${entry.reason}")
                }
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry.status == StudentAttendanceStatus.ABSENT) {
                IconButton(
                    onClick = onShareAbsentMsg,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("share_guardian_absent_${student.rollNo}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share absent message for ${student.name}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 1-tap cycle status pill (>= 48dp touch target)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                border = BorderStroke(1.dp, fgColor.copy(alpha = 0.45f)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(enabled = canEdit) { onCycleStatus() }
                    .testTag("status_pill_roll_${student.rollNo}")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${entry.status.code} • ${entry.status.label}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = fgColor
                    )
                }
            }
        }
    }
}
