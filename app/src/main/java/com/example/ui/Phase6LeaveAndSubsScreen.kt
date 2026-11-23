package com.example.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.LeaveReason
import com.example.domain.LeaveStatus
import com.example.domain.Phase4Validator
import com.example.domain.Phase6DateUtils
import com.example.domain.SubstituteCandidate
import com.example.domain.SubstitutionEngine
import com.example.domain.SubstitutionNeedItem
import com.example.domain.TeacherLeaveRecord
import com.example.domain.TeacherProfile
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer

private enum class LeaveSubMode(val label: String, val tag: String) {
    SUBSTITUTIONS("Substitutions", "p6_mode_subs"),
    LEAVES("Teacher Leave", "p6_mode_leaves"),
    WORKLOAD("Workload", "p6_mode_workload")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Phase6LeaveAndSubsTab(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    canEdit: Boolean,
    selectedSchoolDocId: String,
    onSelectSchoolDocId: (String) -> Unit,
    initialDateIso: String? = null,
    onInitialDateConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(LeaveSubMode.SUBSTITUTIONS) }
    var selectedDate by remember { mutableStateOf(initialDateIso?.takeIf { it.isNotBlank() } ?: Phase6DateUtils.todayIsoDate()) }
    androidx.compose.runtime.LaunchedEffect(initialDateIso) {
        if (!initialDateIso.isNullOrBlank()) {
            selectedDate = initialDateIso
            mode = LeaveSubMode.SUBSTITUTIONS
            onInitialDateConsumed()
        }
    }
    var filterAllSchools by remember { mutableStateOf(true) }
    var showDateInputDialog by remember { mutableStateOf(false) }
    var editingLeaveDialog by remember { mutableStateOf<Pair<TeacherLeaveRecord?, Boolean>?>(null) } // (existing, isSelfRequest)
    var manualPickerTarget by remember { mutableStateOf<SubstitutionNeedItem?>(null) }
    var showConflictDetailDialog by remember { mutableStateOf(false) }

    val ownLinkedTeacher = remember(uiState.teachers, uiState.ownLinkedTeacher, uiState.currentMember) {
        uiState.resolveOwnLinkedTeacher()
    }
    val effectiveTeachers = remember(uiState.teachers, uiState.ownLinkedTeacher, uiState.timetableCells) {
        val base = uiState.availableTimetableTeachers()
        if (ownLinkedTeacher != null && base.none { it.id == ownLinkedTeacher.id }) {
            (base + ownLinkedTeacher).sortedBy { it.name.lowercase() }
        } else {
            base
        }
    }

    val dailyNeeds = remember(
        selectedDate,
        uiState.filter.session,
        filterAllSchools,
        selectedSchoolDocId,
        effectiveTeachers,
        uiState.teacherLeaves,
        uiState.activeSessionTimetableCells,
        uiState.substitutions,
        uiState.effectivePeriodStructuresBySchool,
        uiState.classAssignments,
        uiState.schoolNamesByDocId
    ) {
        SubstitutionEngine.computeDailySubstitutionNeeds(
            date = selectedDate,
            session = uiState.filter.session,
            schoolIdFilter = if (filterAllSchools) "" else selectedSchoolDocId,
            teachers = effectiveTeachers,
            leaves = uiState.teacherLeaves,
            timetableCellsInSession = uiState.activeSessionTimetableCells,
            substitutions = uiState.substitutions,
            periodStructuresBySchool = uiState.effectivePeriodStructuresBySchool,
            classAssignments = uiState.classAssignments,
            schoolNamesById = uiState.schoolNamesByDocId
        )
    }

    val workloads = remember(
        selectedDate,
        uiState.filter.session,
        effectiveTeachers,
        uiState.activeSessionTimetableCells,
        uiState.substitutions
    ) {
        SubstitutionEngine.computeTeacherWorkloadsWithSubstitutions(
            referenceDate = selectedDate,
            session = uiState.filter.session,
            teachers = effectiveTeachers,
            timetableCellsInSession = uiState.activeSessionTimetableCells,
            substitutionsInSession = uiState.substitutions
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Concurrent-edit conflict notice banner for Leaves & Substitutions
        if (uiState.phase6Conflicts.isNotEmpty()) {
            Surface(
                color = AbsentAmberContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("p6_conflict_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Leave or substitution conflict",
                        tint = AbsentAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Some leave/substitution edits were replaced by newer ones (${uiState.phase6Conflicts.size}).",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showConflictDetailDialog = true }) {
                        Text("Details", fontWeight = FontWeight.Bold, color = AbsentAmber)
                    }
                    IconButton(onClick = viewModel::dismissPhase6ConflictBanner) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss conflict banner", tint = AbsentAmber)
                    }
                }
            }
        }

        // Sub-mode switcher chips (Substitutions | Teacher Leave | Workload)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val pendingCount = uiState.pendingLeaveRequestCount
            LeaveSubMode.entries.forEach { m ->
                val label = if (m == LeaveSubMode.LEAVES && pendingCount > 0 && canEdit) {
                    "${m.label} ($pendingCount)"
                } else {
                    m.label
                }
                FilterChip(
                    selected = mode == m,
                    onClick = { mode = m },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(m.tag)
                )
            }
        }

        when (mode) {
            LeaveSubMode.SUBSTITUTIONS -> {
                DailySubstitutionsView(
                    uiState = uiState,
                    canEdit = canEdit,
                    selectedDate = selectedDate,
                    onDateChange = { selectedDate = it },
                    onOpenDateDialog = { showDateInputDialog = true },
                    filterAllSchools = filterAllSchools,
                    onToggleAllSchools = { filterAllSchools = it },
                    selectedSchoolDocId = selectedSchoolDocId,
                    onSelectSchoolDocId = onSelectSchoolDocId,
                    dailyNeeds = dailyNeeds,
                    onQuickAssign = { item, cand -> viewModel.assignSubstitution(item, cand) },
                    onOpenManualPicker = { manualPickerTarget = it },
                    onClearSub = { viewModel.clearSubstitution(it) },
                    onPrintSlip = {
                        val targetSchool = if (filterAllSchools) {
                            dailyNeeds.firstOrNull()?.schoolId ?: selectedSchoolDocId
                        } else {
                            selectedSchoolDocId
                        }
                        viewModel.generateSubstitutionSlipPdf(
                            schoolDocId = targetSchool,
                            date = selectedDate,
                            items = dailyNeeds
                        )
                    },
                    onShareText = {
                        val schoolLabel = if (filterAllSchools) {
                            uiState.activeOrg?.name.orEmpty().ifBlank { uiState.activeSchool.name }
                        } else {
                            uiState.schoolNameByDocId(selectedSchoolDocId)
                        }
                        val shareBody = SubstitutionEngine.buildDailySubstitutionShareText(
                            schoolName = schoolLabel,
                            date = selectedDate,
                            items = dailyNeeds
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Substitution Slip — $selectedDate")
                            putExtra(Intent.EXTRA_TEXT, shareBody)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Substitution Plan"))
                    }
                )
            }

            LeaveSubMode.LEAVES -> {
                TeacherLeavesView(
                    uiState = uiState,
                    canEdit = canEdit,
                    ownLinkedTeacher = ownLinkedTeacher,
                    onAddLeave = { isSelf -> editingLeaveDialog = (null to isSelf) },
                    onEditLeave = { lv -> editingLeaveDialog = (lv to (!canEdit)) },
                    onApprove = { viewModel.approveOrRejectLeave(it, approve = true) },
                    onReject = { viewModel.approveOrRejectLeave(it, approve = false) },
                    onCancelLeave = { viewModel.cancelTeacherLeave(it) }
                )
            }

            LeaveSubMode.WORKLOAD -> {
                WorkloadWithSubsView(
                    referenceDate = selectedDate,
                    workloads = workloads
                )
            }
        }
    }

    // Manual ISO Date Dialog
    if (showDateInputDialog) {
        var input by remember(selectedDate) { mutableStateOf(selectedDate) }
        val isValid = Phase6DateUtils.isValidIsoDate(input)
        AlertDialog(
            onDismissRequest = { showDateInputDialog = false },
            title = { Text("Pick Date (YYYY-MM-DD)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.trim().take(10) },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        isError = !isValid,
                        supportingText = {
                            if (!isValid) Text("Use YYYY-MM-DD format, e.g. 2026-11-23")
                            else Text(Phase6DateUtils.formatDisplayDate(input))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("p6_date_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = isValid,
                    onClick = {
                        selectedDate = input
                        showDateInputDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDateInputDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add / Edit / Self-Request Leave Dialog
    editingLeaveDialog?.let { (existing, isSelfRequest) ->
        TeacherLeaveEditorDialog(
            existing = existing,
            isSelfRequest = isSelfRequest,
            ownLinkedTeacher = ownLinkedTeacher,
            allTeachers = effectiveTeachers,
            sampleSlots = uiState.periodStructureForSchool(selectedSchoolDocId)
                .slotsFor(Phase6DateUtils.dayOfWeekForDate(selectedDate) ?: com.example.domain.DayOfWeekCode.MON)
                .filter { it.type.countsAsTeachingPeriod }
                .ifEmpty {
                    uiState.periodStructureForSchool(selectedSchoolDocId)
                        .slotsFor(com.example.domain.DayOfWeekCode.MON)
                        .filter { it.type.countsAsTeachingPeriod }
                },
            defaultDate = selectedDate,
            onSave = { teacherId, teacherName, start, end, reason, note, halfDay, slotIds ->
                viewModel.saveTeacherLeave(
                    existingLeave = existing,
                    teacherId = teacherId,
                    teacherName = teacherName,
                    startDate = start,
                    endDate = end,
                    reasonType = reason,
                    reasonNote = note,
                    halfDay = halfDay,
                    halfDaySlotIds = slotIds,
                    isSelfRequest = isSelfRequest
                )
                editingLeaveDialog = null
            },
            onDismiss = { editingLeaveDialog = null }
        )
    }

    // Full Substitute Picker (Recommended + Manual Override with explicit warning)
    manualPickerTarget?.let { item ->
        SubstitutePickerDialog(
            item = item,
            onSelectCandidate = { cand ->
                viewModel.assignSubstitution(item, cand)
                manualPickerTarget = null
            },
            onDismiss = { manualPickerTarget = null }
        )
    }

    if (showConflictDetailDialog) {
        AlertDialog(
            onDismissRequest = { showConflictDetailDialog = false },
            title = { Text("Concurrent Leave / Substitution Updates") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.phase6Conflicts.forEach { c ->
                        Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(c.targetSummary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Your offline edit: ${c.yourSummary}", style = MaterialTheme.typography.bodySmall)
                                Text("Replaced by ${c.winnerName}: ${c.winningSummary}", style = MaterialTheme.typography.bodySmall, color = AbsentAmber)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showConflictDetailDialog = false }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailySubstitutionsView(
    uiState: SchoolUiState,
    canEdit: Boolean,
    selectedDate: String,
    onDateChange: (String) -> Unit,
    onOpenDateDialog: () -> Unit,
    filterAllSchools: Boolean,
    onToggleAllSchools: (Boolean) -> Unit,
    selectedSchoolDocId: String,
    onSelectSchoolDocId: (String) -> Unit,
    dailyNeeds: List<SubstitutionNeedItem>,
    onQuickAssign: (SubstitutionNeedItem, SubstituteCandidate) -> Unit,
    onOpenManualPicker: (SubstitutionNeedItem) -> Unit,
    onClearSub: (com.example.domain.SubstitutionRecord) -> Unit,
    onPrintSlip: () -> Unit,
    onShareText: () -> Unit
) {
    val needsSubCount = dailyNeeds.count { it.needsSubstitute }
    val assignedCount = dailyNeeds.count { !it.needsSubstitute }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            // Date & School Controls Card
            Card(
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
                            onClick = { onDateChange(Phase6DateUtils.addDays(selectedDate, -1)) },
                            modifier = Modifier.testTag("p6_prev_day_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("p6_date_picker_btn")
                                .clickable { onOpenDateDialog() }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = Phase6DateUtils.formatDisplayDate(selectedDate),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Tap to pick date",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDateChange(Phase6DateUtils.addDays(selectedDate, 1)) },
                            modifier = Modifier.testTag("p6_next_day_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                        }
                    }

                    // School scope filter chips
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filterAllSchools,
                            onClick = { onToggleAllSchools(true) },
                            label = { Text("All schools") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        uiState.activeSchools.forEach { school ->
                            val docId = uiState.schoolDocIdByLongId[school.id].orEmpty()
                            if (docId.isNotBlank()) {
                                FilterChip(
                                    selected = !filterAllSchools && selectedSchoolDocId == docId,
                                    onClick = {
                                        onToggleAllSchools(false)
                                        onSelectSchoolDocId(docId)
                                    },
                                    label = { Text(school.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    // Action row: Today + Print Substitution Slip + Share Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onDateChange(Phase6DateUtils.todayIsoDate()) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Today")
                        }
                        Button(
                            onClick = onPrintSlip,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("p6_print_slip_btn")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Slip PDF", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onShareText,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("p6_share_text_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share substitution plan text", modifier = Modifier.size(18.dp))
                        }
                    }

                    // Summary status pills
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (needsSubCount > 0) FailRedContainer else PassGreenContainer
                        ) {
                            Text(
                                text = "Needs Sub: $needsSubCount",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (needsSubCount > 0) FailRed else PassGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PassGreenContainer
                        ) {
                            Text(
                                text = "Assigned: $assignedCount",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PassGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        if (dailyNeeds.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "No substitutions needed",
                            tint = PassGreen,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No teacher absences on ${Phase6DateUtils.formatDisplayDate(selectedDate)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Mark a teacher absent in the 'Teacher Leave' tab to view affected periods and suggested substitutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = dailyNeeds,
                key = { "${it.schoolId}_${it.className}_${it.section}_${it.slotId}" }
            ) { item ->
                SubstitutionNeedCard(
                    item = item,
                    canEdit = canEdit,
                    onQuickAssign = { cand -> onQuickAssign(item, cand) },
                    onOpenManualPicker = { onOpenManualPicker(item) },
                    onClearSub = { item.existingSubstitution?.let(onClearSub) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubstitutionNeedCard(
    item: SubstitutionNeedItem,
    canEdit: Boolean,
    onQuickAssign: (SubstituteCandidate) -> Unit,
    onOpenManualPicker: () -> Unit,
    onClearSub: () -> Unit
) {
    val timeRange = "${Phase4Validator.formatMinutes(item.startMinutes)}–${Phase4Validator.formatMinutes(item.endMinutes)}"
    val borderColor = when {
        item.needsSubstitute -> FailRed.copy(alpha = 0.6f)
        item.existingSubstitution?.overrideUsed == true -> AbsentAmber
        else -> PassGreen.copy(alpha = 0.5f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("p6_sub_card_${item.slotId}")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.slotLabel} ($timeRange) • Class ${item.className}-${item.section}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.subjectName} • ${item.schoolName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Absent: ${item.absentTeacherName} (${item.leaveReason.label})",
                        style = MaterialTheme.typography.bodySmall,
                        color = FailRed
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = when {
                        item.needsSubstitute -> FailRedContainer
                        item.existingSubstitution?.overrideUsed == true -> AbsentAmberContainer
                        else -> PassGreenContainer
                    }
                ) {
                    Text(
                        text = when {
                            item.needsSubstitute -> "Needs Sub"
                            item.existingSubstitution?.overrideUsed == true -> "Override"
                            else -> "Covered"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            item.needsSubstitute -> FailRed
                            item.existingSubstitution?.overrideUsed == true -> AbsentAmber
                            else -> PassGreen
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Status Banner (especially when assigned substitute ALSO became absent!)
            if (item.assignedSubstituteAlsoAbsent || item.assignedSubstituteInactive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FailRedContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = FailRed, modifier = Modifier.size(18.dp))
                        Text(
                            text = item.statusNotice,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = FailRed
                        )
                    }
                }
            } else if (item.existingSubstitution != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.existingSubstitution.overrideUsed) AbsentAmberContainer else PassGreenContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Substitute: ${item.existingSubstitution.substituteTeacherName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (item.existingSubstitution.overrideUsed) {
                                Text(
                                    text = "OVERRIDE: ${item.existingSubstitution.overrideReason.ifBlank { "Manual assignment" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AbsentAmber,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (canEdit) {
                            IconButton(
                                onClick = onClearSub,
                                modifier = Modifier.testTag("p6_clear_sub_${item.slotId}")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear substitution", tint = FailRed)
                            }
                        }
                    }
                }
            }

            // 1-Tap Suggested Substitutes (when editable)
            if (canEdit) {
                if (item.recommendedCandidates.isNotEmpty() && item.needsSubstitute) {
                    Text(
                        text = "Top suggested substitutes (1-tap assign):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    item.recommendedCandidates.take(3).forEachIndexed { idx, cand ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${idx + 1}. ${cand.teacher.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = cand.reasonBadges.joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { onQuickAssign(cand) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("p6_quick_assign_${item.slotId}_$idx")
                                ) {
                                    Text("Assign")
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenManualPicker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("p6_manual_pick_${item.slotId}")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (item.needsSubstitute) {
                            "Choose from all teachers (${item.recommendedCandidates.size} free, ${item.manualOverrideCandidates.size} override)"
                        } else {
                            "Change substitute"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubstitutePickerDialog(
    item: SubstitutionNeedItem,
    onSelectCandidate: (SubstituteCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Substitute — ${item.className}-${item.section} (${item.slotLabel})") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${item.subjectName} • Absent: ${item.absentTeacherName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Recommended (Free & Within Limits)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = PassGreen
                )
                if (item.recommendedCandidates.isEmpty()) {
                    Text(
                        text = "No free teachers within daily limits for this slot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    item.recommendedCandidates.forEach { cand ->
                        Card(
                            border = BorderStroke(1.dp, PassGreen.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCandidate(cand) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cand.teacher.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = cand.reasonBadges.joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.PersonAdd, contentDescription = "Assign ${cand.teacher.name}", tint = PassGreen)
                            }
                        }
                    }
                }

                if (item.manualOverrideCandidates.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Manual Override (Busy / Over Limit / Off-Day / Absent)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = FailRed
                    )
                    item.manualOverrideCandidates.forEach { cand ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AbsentAmberContainer.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, FailRed.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCandidate(cand) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(cand.teacher.name, fontWeight = FontWeight.Bold)
                                        Surface(shape = RoundedCornerShape(4.dp), color = FailRedContainer) {
                                            Text(
                                                text = "OVERRIDE",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = FailRed,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = cand.overrideWarningReason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FailRed
                                    )
                                }
                                Icon(Icons.Default.WarningAmber, contentDescription = "Assign with override", tint = FailRed)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
        }
    )
}

@Composable
private fun TeacherLeavesView(
    uiState: SchoolUiState,
    canEdit: Boolean,
    ownLinkedTeacher: TeacherProfile?,
    onAddLeave: (Boolean) -> Unit,
    onEditLeave: (TeacherLeaveRecord) -> Unit,
    onApprove: (TeacherLeaveRecord) -> Unit,
    onReject: (TeacherLeaveRecord) -> Unit,
    onCancelLeave: (TeacherLeaveRecord) -> Unit
) {
    val currentUid = uiState.currentMember?.uid.orEmpty()
    val pendingLeaves = remember(uiState.teacherLeaves) {
        uiState.teacherLeaves.filter { it.status == LeaveStatus.PENDING }
    }
    val otherLeaves = remember(uiState.teacherLeaves) {
        uiState.teacherLeaves.filter { it.status != LeaveStatus.PENDING }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            if (canEdit) {
                Button(
                    onClick = { onAddLeave(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("p6_mark_absent_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mark Teacher Absent / Add Leave")
                }
            } else if (ownLinkedTeacher != null) {
                Button(
                    onClick = { onAddLeave(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("p6_request_self_leave_btn")
                ) {
                    Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Request Leave (${ownLinkedTeacher.name})")
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "View-only access. Ask an admin to link your account to your teacher profile if you need to request leave.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (pendingLeaves.isNotEmpty()) {
            item {
                Text(
                    text = "Pending Leave Requests (${pendingLeaves.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AbsentAmber
                )
            }
            items(pendingLeaves, key = { it.id }) { leave ->
                LeaveRecordCard(
                    leave = leave,
                    canEdit = canEdit,
                    isOwnPending = leave.requestedByUid == currentUid && leave.status == LeaveStatus.PENDING,
                    onApprove = { onApprove(leave) },
                    onReject = { onReject(leave) },
                    onEdit = { onEditLeave(leave) },
                    onCancel = { onCancelLeave(leave) }
                )
            }
        }

        item {
            Text(
                text = "Recorded Leaves (${otherLeaves.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (otherLeaves.isEmpty()) {
            item {
                Text(
                    text = "No approved, rejected, or cancelled leaves recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(otherLeaves, key = { it.id }) { leave ->
                LeaveRecordCard(
                    leave = leave,
                    canEdit = canEdit,
                    isOwnPending = false,
                    onApprove = { onApprove(leave) },
                    onReject = { onReject(leave) },
                    onEdit = { onEditLeave(leave) },
                    onCancel = { onCancelLeave(leave) }
                )
            }
        }
    }
}

@Composable
private fun LeaveRecordCard(
    leave: TeacherLeaveRecord,
    canEdit: Boolean,
    isOwnPending: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    val (badgeBg, badgeFg) = when (leave.status) {
        LeaveStatus.APPROVED -> PassGreenContainer to PassGreen
        LeaveStatus.PENDING -> AbsentAmberContainer to AbsentAmber
        LeaveStatus.REJECTED -> FailRedContainer to FailRed
        LeaveStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("p6_leave_card_${leave.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = leave.teacherName.ifBlank { leave.teacherId },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (leave.startDate == leave.endDate) {
                            Phase6DateUtils.formatDisplayDate(leave.startDate)
                        } else {
                            "${leave.startDate} to ${leave.endDate}"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(shape = RoundedCornerShape(12.dp), color = badgeBg) {
                    Text(
                        text = leave.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            val halfDayStr = if (leave.halfDay && leave.halfDaySlotIds.isNotEmpty()) {
                " • Half-day (${leave.halfDaySlotIds.size} slot(s))"
            } else if (leave.halfDay) {
                " • Half-day"
            } else {
                " • Full day"
            }
            Text(
                text = "${leave.reasonType.label}$halfDayStr${if (leave.reasonNote.isNotBlank()) " — ${leave.reasonNote}" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val metaLine = buildString {
                if (leave.requestedByName.isNotBlank()) append("By ${leave.requestedByName}")
                if (leave.approvedByName.isNotBlank() && leave.status in listOf(LeaveStatus.APPROVED, LeaveStatus.REJECTED)) {
                    if (isNotEmpty()) append(" • ")
                    append("${if (leave.status == LeaveStatus.APPROVED) "Approved" else "Rejected"} by ${leave.approvedByName}")
                }
            }
            if (metaLine.isNotEmpty()) {
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canEdit && leave.status == LeaveStatus.PENDING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = PassGreen),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("p6_approve_leave_${leave.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve")
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("p6_reject_leave_${leave.id}")
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = FailRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject", color = FailRed)
                    }
                }
            }

            if (canEdit || isOwnPending) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (leave.status != LeaveStatus.CANCELLED) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Cancel Leave", color = FailRed)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeacherLeaveEditorDialog(
    existing: TeacherLeaveRecord?,
    isSelfRequest: Boolean,
    ownLinkedTeacher: TeacherProfile?,
    allTeachers: List<TeacherProfile>,
    sampleSlots: List<com.example.domain.PeriodSlot>,
    defaultDate: String,
    onSave: (String, String, String, String, LeaveReason, String, Boolean, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val activeTeachers = remember(allTeachers) { allTeachers.filter { it.active }.ifEmpty { allTeachers } }
    var selectedTeacherId by remember {
        mutableStateOf(
            existing?.teacherId
                ?: if (isSelfRequest) ownLinkedTeacher?.id.orEmpty() else activeTeachers.firstOrNull()?.id.orEmpty()
        )
    }
    var startDate by remember { mutableStateOf(existing?.startDate ?: defaultDate) }
    var endDate by remember { mutableStateOf(existing?.endDate ?: defaultDate) }
    var reason by remember { mutableStateOf(existing?.reasonType ?: LeaveReason.CASUAL_LEAVE) }
    var note by remember { mutableStateOf(existing?.reasonNote.orEmpty()) }
    var halfDay by remember { mutableStateOf(existing?.halfDay ?: false) }
    var selectedSlotIds by remember { mutableStateOf(existing?.halfDaySlotIds?.toSet() ?: emptySet()) }

    val selectedTeacherName = remember(selectedTeacherId, activeTeachers, ownLinkedTeacher, existing) {
        activeTeachers.firstOrNull { it.id == selectedTeacherId }?.name
            ?: ownLinkedTeacher?.takeIf { it.id == selectedTeacherId }?.name
            ?: existing?.teacherName.orEmpty()
    }
    val validDates = Phase6DateUtils.isValidIsoDate(startDate) &&
        Phase6DateUtils.isValidIsoDate(endDate) &&
        endDate >= startDate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    existing != null -> "Edit Teacher Leave"
                    isSelfRequest -> "Request My Leave"
                    else -> "Mark Teacher Absent"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isSelfRequest) {
                    Text(
                        text = "Teacher: $selectedTeacherName (will be submitted as Pending for approval)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (existing != null) {
                    Text(
                        text = "Teacher: ${existing.teacherName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text("Select Teacher", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        activeTeachers.forEach { t ->
                            FilterChip(
                                selected = selectedTeacherId == t.id,
                                onClick = { selectedTeacherId = t.id },
                                label = { Text(t.name) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it.trim().take(10) },
                        label = { Text("Start (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("p6_leave_start_input")
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it.trim().take(10) },
                        label = { Text("End (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("p6_leave_end_input")
                    )
                }

                Text("Reason", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LeaveReason.entries.forEach { r ->
                        FilterChip(
                            selected = reason == r,
                            onClick = { reason = r },
                            label = { Text(r.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(240) },
                    label = { Text("Optional note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Half-day (specific periods only)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = halfDay, onCheckedChange = { halfDay = it })
                }

                if (halfDay && sampleSlots.isNotEmpty()) {
                    Text(
                        text = "Select absent periods:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sampleSlots.forEach { slot ->
                            val isSelected = slot.label in selectedSlotIds || slot.id in selectedSlotIds
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    // Store slot label (e.g., "Period 1") so it matches across Mon..Sat slot IDs cleanly!
                                    selectedSlotIds = if (isSelected) {
                                        selectedSlotIds - slot.label - slot.id
                                    } else {
                                        selectedSlotIds + slot.label
                                    }
                                },
                                label = { Text(slot.label) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedTeacherId.isNotBlank() && validDates && (!halfDay || selectedSlotIds.isNotEmpty()),
                onClick = {
                    onSave(
                        selectedTeacherId,
                        selectedTeacherName,
                        startDate,
                        endDate,
                        reason,
                        note,
                        halfDay,
                        selectedSlotIds.toList()
                    )
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("p6_save_leave_btn")
            ) {
                Text(if (isSelfRequest && existing == null) "Submit Request" else "Save Leave")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@Composable
private fun WorkloadWithSubsView(
    referenceDate: String,
    workloads: List<com.example.domain.TeacherSubstitutionWorkloadSummary>
) {
    val (weekStart, weekEnd) = remember(referenceDate) { Phase6DateUtils.weekDateRangeFor(referenceDate) }
    val monthKey = remember(referenceDate) { Phase6DateUtils.monthPrefix(referenceDate) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Teacher Workload Including Substitutions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Week: $weekStart to $weekEnd • Month: $monthKey",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (workloads.isEmpty()) {
            item {
                Text(
                    text = "No teachers found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(workloads, key = { it.teacherId }) { item ->
                val over = item.totalWeeklyLoadWithSubs > item.weeklyLimit
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (over) FailRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.teacherName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Regular: ${item.regularWeeklyPeriods}/wk • Subs this week: +${item.substitutionsThisWeek}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${item.totalWeeklyLoadWithSubs} / ${item.weeklyLimit} wk",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (over) FailRed else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${item.substitutionsThisMonth} sub(s) in $monthKey",
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
