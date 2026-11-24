package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.StudentEntity
import com.example.domain.CalendarHoliday
import com.example.domain.DayOfWeekCode
import com.example.domain.HalfWorkingDayEntry
import com.example.domain.HolidayKind
import com.example.domain.Phase6DateUtils
import com.example.domain.SchoolCalendarConfig
import com.example.domain.SchoolCalendarEngine
import com.example.domain.StudentAttendanceEngine
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import java.util.Locale

private val MONTH_LABELS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

// =============================================================================
// TAB 2: SCHOOL CALENDAR (PER SCHOOL & SESSION)
// =============================================================================
@Composable
fun Phase8SchoolCalendarTab(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    selectedSchoolDocId: String,
    onSelectSchoolDocId: (String) -> Unit
) {
    val session = uiState.filter.session
    val savedCal = remember(uiState.schoolCalendars, selectedSchoolDocId, session, uiState.periodStructuresBySchool) {
        uiState.calendarForSchool(selectedSchoolDocId, session)
    }
    val canEditCal = uiState.canEditSchoolCalendar(selectedSchoolDocId)
    val schoolName = uiState.schoolRecordsByDocId[selectedSchoolDocId]?.name ?: uiState.activeSchool.name

    var workingDays by remember(selectedSchoolDocId, session, savedCal) { mutableStateOf(savedCal.workingDays) }
    var term1Start by remember(selectedSchoolDocId, session, savedCal) { mutableStateOf(savedCal.term1StartDate) }
    var term1End by remember(selectedSchoolDocId, session, savedCal) { mutableStateOf(savedCal.term1EndDate) }
    var term2Start by remember(selectedSchoolDocId, session, savedCal) { mutableStateOf(savedCal.term2StartDate) }
    var term2End by remember(selectedSchoolDocId, session, savedCal) { mutableStateOf(savedCal.term2EndDate) }
    var holidays by remember(selectedSchoolDocId, session, savedCal) { mutableStateOf(savedCal.holidays) }
    var halfWorkingDays by remember(selectedSchoolDocId, session, savedCal) { mutableStateOf(savedCal.halfWorkingDays) }
    var workingDayExceptions by remember(selectedSchoolDocId, session, savedCal) { mutableStateOf(savedCal.workingDayExceptions) }

    var showAddHolidayDialog by remember { mutableStateOf(false) }
    var showAddHalfDayDialog by remember { mutableStateOf(false) }

    // Live Range Calculator inputs
    var calcStart by remember(selectedSchoolDocId, session, savedCal) { mutableStateOf(savedCal.term1StartDate) }
    var calcEnd by remember(selectedSchoolDocId, session, savedCal) { mutableStateOf(savedCal.term1EndDate) }

    val draftConfig = remember(
        selectedSchoolDocId, session, workingDays, holidays, halfWorkingDays, workingDayExceptions,
        term1Start, term1End, term2Start, term2End
    ) {
        SchoolCalendarConfig(
            id = SchoolCalendarConfig.makeDocId(selectedSchoolDocId, session),
            schoolId = selectedSchoolDocId,
            session = session,
            workingDays = workingDays.ifEmpty { listOf(DayOfWeekCode.MON) },
            holidays = holidays,
            halfWorkingDays = halfWorkingDays,
            workingDayExceptions = workingDayExceptions,
            term1StartDate = term1Start.trim(),
            term1EndDate = term1End.trim(),
            term2StartDate = term2Start.trim(),
            term2EndDate = term2End.trim()
        )
    }

    val termValidationError = remember(session, term1Start, term1End, term2Start, term2End) {
        SchoolCalendarEngine.validateTermDates(
            session = session,
            term1Start = term1Start.trim(),
            term1End = term1End.trim(),
            term2Start = term2Start.trim(),
            term2End = term2End.trim()
        )
    }

    val t1Stats = remember(draftConfig, term1Start, term1End) {
        SchoolCalendarEngine.countWorkingDaysInRange(draftConfig, term1Start.trim(), term1End.trim())
    }
    val t2Stats = remember(draftConfig, term2Start, term2End) {
        SchoolCalendarEngine.countWorkingDaysInRange(draftConfig, term2Start.trim(), term2End.trim())
    }
    val customRangeStats = remember(draftConfig, calcStart, calcEnd) {
        SchoolCalendarEngine.countWorkingDaysInRange(draftConfig, calcStart.trim(), calcEnd.trim())
    }

    val visibleSchools = remember(uiState.schools, uiState.schoolDocIdByLongId) {
        uiState.schools.mapNotNull { sch ->
            val docId = uiState.schoolDocIdByLongId[sch.id].orEmpty()
            if (docId.isBlank()) null else docId to sch.name
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (visibleSchools.size > 1) {
            item {
                CompactDropdownSelector(
                    label = "School ($session)",
                    selectedValue = schoolName,
                    options = visibleSchools.map { it.second },
                    onSelect = { chosen ->
                        visibleSchools.firstOrNull { it.second == chosen }?.let { onSelectSchoolDocId(it.first) }
                    },
                    testTag = "calendar_school_picker"
                )
            }
        }

        // 1. Working Days of Week
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Working Days of the Week",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Defaulted from $schoolName's period structure; toggle to override for session $session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DayOfWeekCode.entries.forEach { day ->
                            val selected = day in workingDays
                            FilterChip(
                                selected = selected,
                                enabled = canEditCal,
                                onClick = {
                                    workingDays = if (selected && workingDays.size > 1) {
                                        workingDays - day
                                    } else if (!selected) {
                                        (workingDays + day).sortedBy { it.sortOrder }
                                    } else {
                                        workingDays
                                    }
                                },
                                label = { Text(day.shortLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("cal_day_${day.wireValue}")
                            )
                        }
                    }
                }
            }
        }

        // 2. Term-I and Term-II Date Ranges
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
                    Text(
                        text = "Term Dates (Session $session)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Format: YYYY-MM-DD. Term-I and Term-II cannot overlap and must stay inside session $session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Term-I  •  ${t1Stats.workingDays} working day(s) (${t1Stats.holidayDays} holiday)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = term1Start,
                            onValueChange = { term1Start = it },
                            readOnly = !canEditCal,
                            label = { Text("Term-I Start") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cal_t1_start")
                        )
                        OutlinedTextField(
                            value = term1End,
                            onValueChange = { term1End = it },
                            readOnly = !canEditCal,
                            label = { Text("Term-I End") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cal_t1_end")
                        )
                    }

                    Text(
                        text = "Term-II  •  ${t2Stats.workingDays} working day(s) (${t2Stats.holidayDays} holiday)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = term2Start,
                            onValueChange = { term2Start = it },
                            readOnly = !canEditCal,
                            label = { Text("Term-II Start") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cal_t2_start")
                        )
                        OutlinedTextField(
                            value = term2End,
                            onValueChange = { term2End = it },
                            readOnly = !canEditCal,
                            label = { Text("Term-II End") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cal_t2_end")
                        )
                    }

                    if (termValidationError != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = FailRedContainer,
                            border = BorderStroke(1.dp, FailRed.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = termValidationError,
                                style = MaterialTheme.typography.bodySmall,
                                color = FailRed,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Holidays & Declared Off Days
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Holidays & Declared Off (${holidays.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (canEditCal) {
                            TextButton(
                                onClick = { showAddHolidayDialog = true },
                                modifier = Modifier.testTag("cal_add_holiday_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    }

                    if (holidays.isEmpty()) {
                        Text(
                            text = "No holidays added yet. Add single dates (e.g. Independence Day), date ranges (e.g. Diwali Break), or declared-off days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        holidays.sortedBy { it.startDate }.forEachIndexed { idx, hol ->
                            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hol.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val rangeLabel = if (hol.startDate == hol.endDate) hol.startDate else "${hol.startDate} to ${hol.endDate}"
                                    Text(
                                        text = "${hol.kind.label}  •  $rangeLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (canEditCal) {
                                    IconButton(
                                        onClick = { holidays = holidays.filterNot { it.id == hol.id } }
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Remove holiday",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Optional Half Working Days & Working-Day Exceptions
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = AbsentAmber)
                            Text(
                                text = "Half Working Days (${halfWorkingDays.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (canEditCal) {
                            TextButton(
                                onClick = { showAddHalfDayDialog = true },
                                modifier = Modifier.testTag("cal_add_halfday_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                    }
                    Text(
                        text = "Half working days still count as 1 working day in report card totals and are highlighted on daily sheets and the monthly register.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    halfWorkingDays.sortedBy { it.date }.forEachIndexed { idx, hd ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(hd.name, style = MaterialTheme.typography.titleSmall)
                                Text(hd.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (canEditCal) {
                                IconButton(onClick = { halfWorkingDays = halfWorkingDays.filterNot { it.id == hd.id } }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove half working day", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (workingDayExceptions.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "Admin Working-Day Exceptions (${workingDayExceptions.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        workingDayExceptions.sortedBy { it.date }.forEach { ex ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${ex.date} — ${ex.note}", style = MaterialTheme.typography.bodyMedium)
                                }
                                if (canEditCal) {
                                    IconButton(onClick = { workingDayExceptions = workingDayExceptions.filterNot { it.date == ex.date } }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove working day exception", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Live Working-Days Calculator (Any Date Range)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.testTag("cal_working_days_calculator")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Live Working-Days Calculator",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = calcStart,
                            onValueChange = { calcStart = it },
                            label = { Text("From (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = calcEnd,
                            onValueChange = { calcEnd = it },
                            label = { Text("To (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CalcStatPill("Working", "${customRangeStats.workingDays}", highlight = true)
                            CalcStatPill("Half-Days", "${customRangeStats.halfWorkingDays}")
                            CalcStatPill("Holidays", "${customRangeStats.holidayDays}")
                            CalcStatPill("Weekly Off", "${customRangeStats.weeklyOffDays}")
                        }
                    }
                }
            }
        }

        // 6. Save Calendar Button
        if (canEditCal) {
            item {
                Button(
                    onClick = { viewModel.saveSchoolCalendar(draftConfig) },
                    enabled = termValidationError == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("cal_save_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save School Calendar ($schoolName)")
                }
            }
        }
    }

    if (showAddHolidayDialog) {
        AddHolidayDialog(
            defaultDate = Phase6DateUtils.todayIso(),
            onAdd = { hol ->
                holidays = (holidays + hol).sortedBy { it.startDate }
                showAddHolidayDialog = false
            },
            onDismiss = { showAddHolidayDialog = false }
        )
    }

    if (showAddHalfDayDialog) {
        AddHalfWorkingDayDialog(
            defaultDate = Phase6DateUtils.todayIso(),
            onAdd = { hd ->
                halfWorkingDays = (halfWorkingDays.filterNot { it.date == hd.date } + hd).sortedBy { it.date }
                showAddHalfDayDialog = false
            },
            onDismiss = { showAddHalfDayDialog = false }
        )
    }
}

@Composable
private fun CalcStatPill(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun AddHolidayDialog(
    defaultDate: String,
    onAdd: (CalendarHoliday) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(defaultDate) }
    var endDate by remember { mutableStateOf(defaultDate) }
    var kind by remember { mutableStateOf(HolidayKind.HOLIDAY) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Holiday / Declared Off") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Name (e.g. Diwali Break)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HolidayKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = startDate,
                    onValueChange = {
                        startDate = it
                        if (endDate.isBlank() || endDate < it) endDate = it
                        error = null
                    },
                    label = { Text("Start Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it; error = null },
                    label = { Text("End Date (same for single day)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = FailRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = startDate.trim()
                    val e = endDate.trim().ifBlank { s }
                    when {
                        name.trim().isEmpty() -> error = "Enter a holiday name."
                        !Phase6DateUtils.isValidIsoDate(s) || !Phase6DateUtils.isValidIsoDate(e) ->
                            error = "Use valid YYYY-MM-DD dates."
                        e < s -> error = "End date cannot be before start date."
                        else -> onAdd(
                            CalendarHoliday(
                                id = "hol_${s.replace("-", "_")}_${System.currentTimeMillis() % 10000}",
                                name = name.trim(),
                                startDate = s,
                                endDate = e,
                                kind = kind
                            )
                        )
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddHalfWorkingDayDialog(
    defaultDate: String,
    onAdd: (HalfWorkingDayEntry) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("Half Working Day") }
    var date by remember { mutableStateOf(defaultDate) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Half Working Day") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it; error = null },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Label (e.g. PTM Half Day)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = FailRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val d = date.trim()
                    if (!Phase6DateUtils.isValidIsoDate(d)) {
                        error = "Use a valid YYYY-MM-DD date."
                    } else {
                        onAdd(
                            HalfWorkingDayEntry(
                                id = "half_${d.replace("-", "_")}",
                                date = d,
                                name = name.trim().ifBlank { "Half Working Day" }
                            )
                        )
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// =============================================================================
// TAB 3: TERM SUMMARY & REPORT CARD AUTO-FILL / RECALCULATE
// =============================================================================
@Composable
fun Phase8TermSummaryTab(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    selectedSchoolDocId: String,
    selectedClassName: String,
    selectedSection: String
) {
    var selectedTerm by remember { mutableIntStateOf(1) }
    var confirmSectionRecalc by remember { mutableStateOf(false) }
    var confirmStudentRecalc by remember { mutableStateOf<StudentEntity?>(null) }

    val schoolRec = uiState.schoolRecordsByDocId[selectedSchoolDocId]
    val schLongId = schoolRec?.stableId ?: uiState.activeSchool.id
    val autoFillOn = schoolRec?.autoFillReportAttendance ?: false
    val normSec = selectedSection.trim().uppercase().ifBlank { "A" }
    val canRecalc = uiState.canEditSectionAttendance(selectedSchoolDocId, selectedClassName, normSec) ||
        uiState.canEditCoScholasticForSection(selectedClassName, normSec)

    val cal = uiState.calendarForSchool(selectedSchoolDocId, uiState.filter.session)
    val (termStart, termEnd) = SchoolCalendarEngine.termDateRange(cal, selectedTerm)
    val termWorkingDays = SchoolCalendarEngine.countWorkingDaysInRange(cal, termStart, termEnd).workingDays

    val students = remember(uiState.allStudents, schLongId, uiState.filter.session, selectedClassName, normSec) {
        uiState.allStudents
            .filter {
                it.schoolId == schLongId &&
                    it.session == uiState.filter.session &&
                    it.className.equals(selectedClassName, ignoreCase = true) &&
                    it.section.equals(normSec, ignoreCase = true)
            }
            .sortedWith(compareBy({ it.rollNo }, { it.name.lowercase() }))
    }

    val manualCountInSection = remember(students, selectedTerm, uiState.allTermInfos, uiState.termInfoManualOverrideByStudentTerm, autoFillOn) {
        students.count { st ->
            uiState.effectiveTermAttendanceForStudent(st.id, selectedTerm).isManual
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    Text(
                        text = "Term Attendance — Class $selectedClassName-$normSec",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1 to "Term-I", 2 to "Term-II").forEach { (termNo, label) ->
                            FilterChip(
                                selected = selectedTerm == termNo,
                                onClick = { selectedTerm = termNo },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("term_summary_chip_$termNo")
                            )
                        }
                    }

                    Text(
                        text = "Range: $termStart to $termEnd  •  Calendar Working Days: $termWorkingDays",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (autoFillOn) PassGreenContainer else AbsentAmberContainer
                    ) {
                        Text(
                            text = if (autoFillOn) {
                                "Report Card Auto-Fill is ON for ${schoolRec?.name ?: uiState.activeSchool.name}. Daily totals (P=1, Lt=1, H=0.5, A/L=0) fill report cards automatically while manually typed values stay protected."
                            } else {
                                "Report Card Auto-Fill is OFF (default). Report cards use manually typed attendance until you enable the switch in Settings or tap Recalculate below."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (autoFillOn) PassGreen else Color(0xFF4E2D00),
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    if (canRecalc && students.isNotEmpty()) {
                        Button(
                            onClick = { confirmSectionRecalc = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("recalc_section_term_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Recalculate Section Term-$selectedTerm from Daily Attendance")
                        }
                    }
                }
            }
        }

        if (students.isEmpty()) {
            item {
                Text(
                    text = "No students in Class $selectedClassName-$normSec for session ${uiState.filter.session}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(students, key = { "ts_${it.id}_$selectedTerm" }) { st ->
                val comp = uiState.computeStudentTermAttendance(st.id, selectedTerm)
                val eff = uiState.effectiveTermAttendanceForStudent(st.id, selectedTerm)
                val pctStr = String.format(Locale.US, "%.1f%%", comp.attendancePercent)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Roll ${st.rollNo} • ${st.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Daily: ${comp.formattedRawDaysPresent()}/${comp.totalWorkingDays} ($pctStr)  •  P:${comp.presentCount} Lt:${comp.lateCount} H:${comp.halfDayCount} L:${comp.leaveCount} A:${comp.absentCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    eff.isManual -> AbsentAmberContainer
                                    eff.isAutoFilled -> PassGreenContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = when {
                                        eff.isManual -> "Manual"
                                        eff.isAutoFilled -> "Auto"
                                        else -> "Saved"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        eff.isManual -> AbsentAmber
                                        eff.isAutoFilled -> PassGreen
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Report Card Value: ${eff.daysPresent} / ${eff.workingDays}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (canRecalc) {
                                TextButton(
                                    onClick = {
                                        if (eff.isManual) {
                                            confirmStudentRecalc = st
                                        } else {
                                            viewModel.recalculateStudentTermAttendance(st.id, selectedTerm)
                                        }
                                    },
                                    modifier = Modifier.testTag("recalc_student_${st.id}")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Recalculate")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmSectionRecalc) {
        AlertDialog(
            onDismissRequest = { confirmSectionRecalc = false },
            title = { Text("Recalculate Term-$selectedTerm Attendance?") },
            text = {
                val manualWarning = if (manualCountInSection > 0) {
                    " This will overwrite manually typed attendance values for $manualCountInSection student(s) in Class $selectedClassName-$normSec."
                } else ""
                Text("Update Term-$selectedTerm report card attendance for all ${students.size} student(s) from daily attendance sheets?$manualWarning")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmSectionRecalc = false
                        viewModel.recalculateSectionTermAttendance(
                            schoolDocId = selectedSchoolDocId,
                            className = selectedClassName,
                            section = normSec,
                            termNo = selectedTerm
                        )
                    }
                ) { Text("Recalculate All") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSectionRecalc = false }) { Text("Cancel") }
            }
        )
    }

    confirmStudentRecalc?.let { st ->
        AlertDialog(
            onDismissRequest = { confirmStudentRecalc = null },
            title = { Text("Overwrite Manual Attendance?") },
            text = {
                Text("${st.name} has a manually entered Term-$selectedTerm attendance value. Replace it with the calculated daily attendance total?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmStudentRecalc = null
                        viewModel.recalculateStudentTermAttendance(st.id, selectedTerm)
                    }
                ) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStudentRecalc = null }) { Text("Cancel") }
            }
        )
    }
}

// =============================================================================
// TAB 4: REPORTS, OFFLINE A4 PDFS & LOW ATTENDANCE CSV
// =============================================================================
@Composable
fun Phase8AttendanceReportsTab(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    selectedSchoolDocId: String,
    selectedClassName: String,
    selectedSection: String,
    selectedDateIso: String
) {
    val normSec = selectedSection.trim().uppercase().ifBlank { "A" }
    val cal = uiState.calendarForSchool(selectedSchoolDocId, uiState.filter.session)
    val todayParts = selectedDateIso.split("-")
    var selectedYear by remember(selectedDateIso) { mutableIntStateOf(todayParts.getOrNull(0)?.toIntOrNull() ?: 2026) }
    var selectedMonth by remember(selectedDateIso) { mutableIntStateOf(todayParts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 4) }

    var summaryStart by remember(selectedSchoolDocId, uiState.filter.session) { mutableStateOf(cal.term1StartDate) }
    var summaryEnd by remember(selectedSchoolDocId, uiState.filter.session) { mutableStateOf(cal.term1EndDate) }

    var lowScopeWholeSchool by remember { mutableStateOf(false) }
    var lowThresholdText by remember { mutableStateOf("75") }
    val lowThreshold = (lowThresholdText.toIntOrNull() ?: 75).coerceIn(1, 100)

    val lowRows = remember(
        uiState.studentAttendanceSheets, uiState.allStudents, selectedSchoolDocId,
        selectedClassName, normSec, lowScopeWholeSchool, summaryStart, summaryEnd, lowThreshold
    ) {
        uiState.computeLowAttendanceRows(
            schoolDocIdFilter = selectedSchoolDocId,
            classNameFilter = if (lowScopeWholeSchool) null else selectedClassName,
            sectionFilter = if (lowScopeWholeSchool) null else normSec,
            startDate = summaryStart.trim(),
            endDate = summaryEnd.trim(),
            thresholdPercent = lowThreshold
        )
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLowAttendanceCsvToUri(
                destinationUri = uri,
                schoolDocId = selectedSchoolDocId,
                classNameFilter = if (lowScopeWholeSchool) null else selectedClassName,
                sectionFilter = if (lowScopeWholeSchool) null else normSec,
                startDate = summaryStart.trim(),
                endDate = summaryEnd.trim(),
                thresholdPercent = lowThreshold
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Daily Attendance Sheet PDF
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1. Daily Attendance Sheet (A4 Portrait PDF)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Section: Class $selectedClassName-$normSec  •  Date: $selectedDateIso",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            viewModel.generateDailyAttendancePdf(
                                schoolDocId = selectedSchoolDocId,
                                className = selectedClassName,
                                section = normSec,
                                dateIso = selectedDateIso
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("report_daily_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Preview & Print Daily Sheet PDF")
                    }
                }
            }
        }

        // 2. Monthly Attendance Register PDF (A4 Landscape, 31 days + 50+ students)
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
                    Text(
                        text = "2. Monthly Attendance Register (A4 Landscape PDF)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Class $selectedClassName-$normSec • Up to 31 days across, shaded holidays, P/A/L/Lt/H codes, per-student and per-day totals, paginated for 50+ students.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactDropdownSelector(
                            label = "Month",
                            selectedValue = MONTH_LABELS[(selectedMonth - 1).coerceIn(0, 11)],
                            options = MONTH_LABELS,
                            onSelect = { chosen ->
                                val idx = MONTH_LABELS.indexOf(chosen)
                                if (idx >= 0) selectedMonth = idx + 1
                            },
                            modifier = Modifier.weight(1.3f),
                            testTag = "report_month_picker"
                        )
                        val (sy, ey) = SchoolCalendarEngine.parseSessionYears(uiState.filter.session)
                        val years = listOf(sy, ey).distinct().map { it.toString() }
                        CompactDropdownSelector(
                            label = "Year",
                            selectedValue = selectedYear.toString(),
                            options = years,
                            onSelect = { chosen -> chosen.toIntOrNull()?.let { selectedYear = it } },
                            modifier = Modifier.weight(0.7f),
                            testTag = "report_year_picker"
                        )
                    }
                    Button(
                        onClick = {
                            val label = "${MONTH_LABELS[(selectedMonth - 1).coerceIn(0, 11)]} $selectedYear"
                            viewModel.generateMonthlyRegisterPdf(
                                schoolDocId = selectedSchoolDocId,
                                className = selectedClassName,
                                section = normSec,
                                year = selectedYear,
                                month1Based = selectedMonth,
                                monthLabel = label
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("report_monthly_pdf_button")
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Preview & Print Monthly Register PDF")
                    }
                }
            }
        }

        // 3. Date Range Selector + Student-wise Summary PDF
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
                    Text(
                        text = "3. Student-Wise Summary (Date Range)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = summaryStart == cal.term1StartDate && summaryEnd == cal.term1EndDate,
                            onClick = { summaryStart = cal.term1StartDate; summaryEnd = cal.term1EndDate },
                            label = { Text("Term-I") }
                        )
                        FilterChip(
                            selected = summaryStart == cal.term2StartDate && summaryEnd == cal.term2EndDate,
                            onClick = { summaryStart = cal.term2StartDate; summaryEnd = cal.term2EndDate },
                            label = { Text("Term-II") }
                        )
                        FilterChip(
                            selected = summaryStart == cal.term1StartDate && summaryEnd == cal.term2EndDate,
                            onClick = { summaryStart = cal.term1StartDate; summaryEnd = cal.term2EndDate },
                            label = { Text("Full Session") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = summaryStart,
                            onValueChange = { summaryStart = it },
                            label = { Text("From") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = summaryEnd,
                            onValueChange = { summaryEnd = it },
                            label = { Text("To") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.generateStudentAttendanceSummaryPdf(
                                schoolDocId = selectedSchoolDocId,
                                className = selectedClassName,
                                section = normSec,
                                startDate = summaryStart.trim(),
                                endDate = summaryEnd.trim()
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("report_summary_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Preview & Print Student Summary PDF")
                    }
                }
            }
        }

        // 4. Low Attendance List (Section or Whole School, configurable threshold %, PDF + CSV)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.testTag("report_low_attendance_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = FailRed)
                        Text(
                            text = "4. Low Attendance List (< $lowThreshold%)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !lowScopeWholeSchool,
                            onClick = { lowScopeWholeSchool = false },
                            label = { Text("Class $selectedClassName-$normSec") },
                            modifier = Modifier.testTag("low_scope_section")
                        )
                        FilterChip(
                            selected = lowScopeWholeSchool,
                            onClick = { lowScopeWholeSchool = true },
                            label = { Text("Whole School") },
                            modifier = Modifier.testTag("low_scope_school")
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedTextField(
                            value = lowThresholdText,
                            onValueChange = { lowThresholdText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Min %") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .width(84.dp)
                                .testTag("low_threshold_input")
                        )
                    }

                    Text(
                        text = "${lowRows.size} student(s) below $lowThreshold% between $summaryStart and $summaryEnd.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lowRows.isNotEmpty()) FailRed else PassGreen,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.generateLowAttendancePdf(
                                    schoolDocId = selectedSchoolDocId,
                                    classNameFilter = if (lowScopeWholeSchool) null else selectedClassName,
                                    sectionFilter = if (lowScopeWholeSchool) null else normSec,
                                    startDate = summaryStart.trim(),
                                    endDate = summaryEnd.trim(),
                                    thresholdPercent = lowThreshold
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("report_low_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("A4 PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                val scopeSlug = if (lowScopeWholeSchool) "WholeSchool" else "${selectedClassName}_$normSec"
                                exportCsvLauncher.launch("LowAttendance_${scopeSlug}_below${lowThreshold}pct.csv")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("report_low_csv_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export CSV")
                        }
                    }

                    if (lowRows.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        lowRows.take(25).forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${row.student.className}-${row.student.section} • Roll ${row.student.rollNo} • ${row.student.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Present: ${String.format(Locale.US, "%.1f", row.weightedDaysPresent)}/${row.totalWorkingDays}  •  Absent: ${row.absentCount}  •  Leave: ${row.leaveCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", row.attendancePercent),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = FailRed
                                )
                            }
                        }
                        if (lowRows.size > 25) {
                            Text(
                                text = "Showing first 25 of ${lowRows.size}. Export PDF or CSV for the full list.",
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
