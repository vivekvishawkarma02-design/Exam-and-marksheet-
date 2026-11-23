package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.AppModule
import com.example.domain.DayOfWeekCode
import com.example.domain.PeriodSlot
import com.example.domain.SubjectWeeklyProgress
import com.example.domain.TeacherPickerOption
import com.example.domain.TeacherSlotAvailability
import com.example.domain.TimetableCell
import com.example.domain.TimetableConflictNotice
import com.example.domain.TimetableEngine
import com.example.domain.TimetableGridRow
import com.example.domain.TimetableHardConflict
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import com.example.ui.theme.SurfaceMist

private enum class TimetableTab(val label: String, val tag: String) {
    CLASS("Class", "tt_tab_class"),
    TEACHER("Teacher", "tt_tab_teacher"),
    MASTER("Master", "tt_tab_master"),
    FREE("Free", "tt_tab_free"),
    LEAVE_SUBS("Leave & Subs", "tt_tab_leave_subs")
}

internal data class CellEditTarget(
    val schoolDocId: String,
    val className: String,
    val section: String,
    val day: DayOfWeekCode,
    val slot: PeriodSlot,
    val rowIndex: Int,
    val existingCell: TimetableCell?
)

@Composable
fun TimetableScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit,
    initialTabIndex: Int? = null,
    initialSubsDate: String? = null,
    onInitialJumpConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val canEdit = uiState.canEdit(AppModule.TIMETABLE)
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()
    val autoGenProgress by viewModel.autoGenerateProgress.collectAsStateWithLifecycle()
    val autoGenResult by viewModel.autoGenerateResult.collectAsStateWithLifecycle()

    var activeTab by remember {
        mutableStateOf(
            TimetableTab.entries.getOrNull(initialTabIndex ?: 0) ?: TimetableTab.CLASS
        )
    }
    var pendingSubDate by remember { mutableStateOf(initialSubsDate) }

    LaunchedEffect(initialTabIndex, initialSubsDate) {
        if (initialTabIndex != null) {
            activeTab = TimetableTab.entries.getOrNull(initialTabIndex) ?: TimetableTab.CLASS
            if (!initialSubsDate.isNullOrBlank()) {
                pendingSubDate = initialSubsDate
            }
            onInitialJumpConsumed()
        }
    }

    // School / Class / Section / Teacher selection scoped for Timetable
    val accessibleSchools = remember(uiState.activeSchools) { uiState.activeSchools }
    var selectedSchoolDocId by remember(uiState.activeSchoolDocId) {
        mutableStateOf(
            uiState.activeSchoolDocId.ifBlank {
                accessibleSchools.firstOrNull()?.let { uiState.schoolDocIdByLongId[it.id] }.orEmpty()
            }
        )
    }
    LaunchedEffect(accessibleSchools, uiState.schoolDocIdByLongId) {
        if (selectedSchoolDocId.isBlank() || accessibleSchools.none { uiState.schoolDocIdByLongId[it.id] == selectedSchoolDocId }) {
            selectedSchoolDocId = uiState.activeSchoolDocId.ifBlank {
                accessibleSchools.firstOrNull()?.let { uiState.schoolDocIdByLongId[it.id] }.orEmpty()
            }
        }
    }

    val selectedSchoolLongId = remember(selectedSchoolDocId, uiState.schoolDocIdByLongId) {
        uiState.schoolDocIdByLongId.entries.firstOrNull { it.value == selectedSchoolDocId }?.key
            ?: uiState.activeSchool.id
    }
    val classesInSchool = remember(selectedSchoolLongId, uiState.schoolClasses) {
        uiState.classesForSchool(selectedSchoolLongId)
    }
    var selectedClassName by remember(selectedSchoolDocId, classesInSchool) {
        mutableStateOf(
            classesInSchool.firstOrNull { it.name.equals(uiState.effectiveClassName, ignoreCase = true) }?.name
                ?: classesInSchool.firstOrNull()?.name.orEmpty()
        )
    }
    LaunchedEffect(classesInSchool) {
        if (classesInSchool.isNotEmpty() && classesInSchool.none { it.name.equals(selectedClassName, ignoreCase = true) }) {
            selectedClassName = classesInSchool.first().name
        }
    }

    val sectionsForClass = remember(selectedSchoolLongId, selectedClassName, uiState.allStudents, uiState.classAssignments, uiState.timetableCells) {
        uiState.sectionsForSchoolClass(selectedSchoolLongId, selectedClassName).ifEmpty { listOf("A") }
    }
    var selectedSection by remember(selectedSchoolDocId, selectedClassName) {
        mutableStateOf(
            sectionsForClass.firstOrNull { it.equals(uiState.filter.section, ignoreCase = true) }
                ?: sectionsForClass.firstOrNull()
                ?: "A"
        )
    }
    LaunchedEffect(sectionsForClass) {
        if (selectedSection !in sectionsForClass) {
            selectedSection = sectionsForClass.firstOrNull() ?: "A"
        }
    }

    val availableTeachers = remember(uiState.teachers, uiState.timetableCells) {
        uiState.availableTimetableTeachers()
    }
    var selectedTeacherId by remember(availableTeachers) {
        mutableStateOf(availableTeachers.firstOrNull()?.id.orEmpty())
    }
    LaunchedEffect(availableTeachers) {
        if (selectedTeacherId.isBlank() || availableTeachers.none { it.id == selectedTeacherId }) {
            selectedTeacherId = availableTeachers.firstOrNull()?.id.orEmpty()
        }
    }

    // Live Post-Sync Clash Report across ALL schools of the org for this session
    val allClashes = remember(
        uiState.activeSessionTimetableCells,
        uiState.teachers,
        uiState.effectivePeriodStructuresBySchool,
        uiState.schoolNamesByDocId
    ) {
        uiState.timetableClashes()
    }
    val clashCellIds = remember(allClashes) {
        allClashes.flatMap { it.involvedCellIds }.toSet()
    }

    var editingTarget by remember { mutableStateOf<CellEditTarget?>(null) }
    var showClashReportDialog by remember { mutableStateOf(false) }
    var showConflictNoticeDialog by remember { mutableStateOf(false) }
    var showPrintHubDialog by remember { mutableStateOf(false) }
    var showAutoGenerateConfigDialog by remember { mutableStateOf(false) }

    fun jumpToCell(cell: TimetableCell, openEditorImmediately: Boolean) {
        selectedSchoolDocId = cell.schoolId
        selectedClassName = cell.className
        selectedSection = cell.section
        activeTab = TimetableTab.CLASS
        if (openEditorImmediately) {
            val struct = uiState.periodStructureForSchool(cell.schoolId)
            val rows = TimetableEngine.buildGridRows(struct)
            val rowIdx = rows.indexOfFirst { row -> row.slotByDay[cell.day]?.id == cell.slotId }
            val resolvedSlot = if (rowIdx >= 0) {
                rows[rowIdx].slotByDay[cell.day]
            } else {
                TimetableEngine.resolveSlot(cell.schoolId, cell.day, cell.slotId, uiState.effectivePeriodStructuresBySchool)
            }
            if (resolvedSlot != null) {
                editingTarget = CellEditTarget(
                    schoolDocId = cell.schoolId,
                    className = cell.className,
                    section = cell.section,
                    day = cell.day,
                    slot = resolvedSlot,
                    rowIndex = rowIdx.coerceAtLeast(0),
                    existingCell = cell
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("tt_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Timetable",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Session ${uiState.filter.session} • ${if (canEdit) "Edit mode" else "View only"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Clash Report Badge Button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (allClashes.isNotEmpty()) FailRedContainer else PassGreenContainer,
                        border = BorderStroke(
                            1.dp,
                            if (allClashes.isNotEmpty()) FailRed.copy(alpha = 0.55f) else PassGreen.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tt_clash_report_button")
                            .clickable { showClashReportDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (allClashes.isNotEmpty()) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                                contentDescription = "Clash report",
                                tint = if (allClashes.isNotEmpty()) FailRed else PassGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (allClashes.isNotEmpty()) "${allClashes.size} Clash${if (allClashes.size > 1) "es" else ""}" else "0 Clashes",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (allClashes.isNotEmpty()) FailRed else PassGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Print / Export PDF Hub Button
                    IconButton(
                        onClick = { showPrintHubDialog = true },
                        modifier = Modifier.testTag("tt_print_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print or export timetable PDF",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Latest-edit-wins notice banner (offline sync overwrite alert)
                if (uiState.timetableConflicts.isNotEmpty()) {
                    Surface(
                        color = AbsentAmberContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tt_conflict_notice_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Sync overwrite notice",
                                tint = AbsentAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Some of your timetable changes were replaced by newer ones (${uiState.timetableConflicts.size}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4E2D00),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showConflictNoticeDialog = true },
                                modifier = Modifier.testTag("tt_conflict_view_button")
                            ) {
                                Text("Details", color = AbsentAmber, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = viewModel::dismissTimetableConflictBanner,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("tt_conflict_dismiss_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss notice",
                                    tint = AbsentAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 5 Timetable View Tabs (Scrollable for clean mobile fit)
                ScrollableTabRow(
                    selectedTabIndex = activeTab.ordinal,
                    edgePadding = 8.dp
                ) {
                    TimetableTab.entries.forEach { tab ->
                        val tabLabel = if (tab == TimetableTab.LEAVE_SUBS && canEdit && uiState.pendingLeaveRequestCount > 0) {
                            "${tab.label} (${uiState.pendingLeaveRequestCount})"
                        } else {
                            tab.label
                        }
                        Tab(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            text = { Text(tabLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }

                // Phase 6 Auto-Generate & 1-Tap Undo Bar (shown for Timetable editors on Class & Master tabs)
                if (canEdit && (activeTab == TimetableTab.CLASS || activeTab == TimetableTab.MASTER)) {
                    val undoSnap = uiState.latestUndoSnapshotForSchool(selectedSchoolDocId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showAutoGenerateConfigDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("tt_autogen_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-generate", maxLines = 1)
                        }
                        if (undoSnap != null) {
                            OutlinedButton(
                                onClick = { viewModel.undoAutoGenerate(selectedSchoolDocId) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("tt_undo_autogen_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Undo", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Active Tab Body
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                TimetableTab.CLASS -> {
                    ClassTimetableTab(
                        uiState = uiState,
                        canEdit = canEdit,
                        selectedSchoolDocId = selectedSchoolDocId,
                        onSelectSchoolDocId = { selectedSchoolDocId = it },
                        selectedClassName = selectedClassName,
                        onSelectClassName = { selectedClassName = it },
                        selectedSection = selectedSection,
                        onSelectSection = { selectedSection = it },
                        sectionsForClass = sectionsForClass,
                        clashCellIds = clashCellIds,
                        onCellClick = { target -> editingTarget = target }
                    )
                }

                TimetableTab.TEACHER -> {
                    TeacherTimetableTab(
                        uiState = uiState,
                        selectedTeacherId = selectedTeacherId,
                        onSelectTeacherId = { selectedTeacherId = it },
                        clashCellIds = clashCellIds,
                        onJumpToCell = { cell -> jumpToCell(cell, openEditorImmediately = canEdit) },
                        onPrintTeacher = {
                            if (selectedTeacherId.isNotBlank()) {
                                viewModel.generateTeacherTimetablePdf(selectedTeacherId)
                            }
                        }
                    )
                }

                TimetableTab.MASTER -> {
                    MasterTimetableTab(
                        uiState = uiState,
                        selectedSchoolDocId = selectedSchoolDocId,
                        onSelectSchoolDocId = { selectedSchoolDocId = it },
                        clashCellIds = clashCellIds,
                        onJumpToClassSection = { cls, sec ->
                            selectedClassName = cls
                            selectedSection = sec
                            activeTab = TimetableTab.CLASS
                        },
                        onJumpToCell = { cell -> jumpToCell(cell, openEditorImmediately = canEdit) },
                        onPrintMaster = {
                            if (selectedSchoolDocId.isNotBlank()) {
                                viewModel.generateMasterTimetablePdf(selectedSchoolDocId)
                            }
                        }
                    )
                }

                TimetableTab.FREE -> {
                    FreePeriodsTab(
                        uiState = uiState,
                        selectedSchoolDocId = selectedSchoolDocId,
                        onSelectSchoolDocId = { selectedSchoolDocId = it }
                    )
                }

                TimetableTab.LEAVE_SUBS -> {
                    Phase6LeaveAndSubsTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        canEdit = canEdit,
                        selectedSchoolDocId = selectedSchoolDocId,
                        onSelectSchoolDocId = { selectedSchoolDocId = it },
                        initialDateIso = pendingSubDate,
                        onInitialDateConsumed = { pendingSubDate = null }
                    )
                }
            }
        }
    }

    // Phase 6 Auto-Generate Dialogs
    if (showAutoGenerateConfigDialog && canEdit) {
        val lockedCount = uiState.activeSessionTimetableCells.count {
            it.schoolId == selectedSchoolDocId && it.locked
        }
        AutoGenerateConfigDialog(
            schoolDocId = selectedSchoolDocId,
            schoolName = uiState.schoolNameByDocId(selectedSchoolDocId),
            selectedClassName = selectedClassName,
            selectedSection = selectedSection,
            session = uiState.filter.session,
            lockedCellsCount = lockedCount,
            onStart = { config ->
                showAutoGenerateConfigDialog = false
                viewModel.startAutoGenerate(config)
            },
            onDismiss = { showAutoGenerateConfigDialog = false }
        )
    }

    if (autoGenProgress.isRunning) {
        AutoGenerateProgressDialog(
            progress = autoGenProgress,
            onCancel = viewModel::cancelAutoGenerate
        )
    }

    autoGenResult?.let { result ->
        AutoGenerateResultDialog(
            result = result,
            onApply = { viewModel.applyAutoGenerateResult(result) },
            onRegenerateNewSeed = { newCfg -> viewModel.startAutoGenerate(newCfg) },
            onDismiss = viewModel::dismissAutoGenerateResult
        )
    }

    // Cell Editor / Read-only Detail Dialog
    editingTarget?.let { target ->
        val freshCell = uiState.activeSessionTimetableCells.firstOrNull {
            it.schoolId == target.schoolDocId &&
                it.className.equals(target.className, ignoreCase = true) &&
                it.section.equals(target.section, ignoreCase = true) &&
                it.day == target.day &&
                it.slotId == target.slot.id
        }
        TimetableCellEditorDialog(
            uiState = uiState,
            target = target.copy(existingCell = freshCell),
            canEdit = canEdit,
            onSave = { subjectName, teacherId, locked ->
                viewModel.saveTimetableCell(
                    schoolDocId = target.schoolDocId,
                    className = target.className,
                    section = target.section,
                    day = target.day,
                    slotId = target.slot.id,
                    subjectName = subjectName,
                    teacherId = teacherId,
                    locked = locked
                ) { ok, _ ->
                    if (ok) editingTarget = null
                }
            },
            onToggleLock = { cell ->
                viewModel.toggleTimetableCellLock(cell)
            },
            onClear = { cell ->
                viewModel.clearTimetableCell(cell) {
                    editingTarget = null
                }
            },
            onCopyToDays = { subjectName, teacherId, targetDays, onDone ->
                viewModel.copyTimetableCellToDays(
                    sourceSchoolDocId = target.schoolDocId,
                    className = target.className,
                    section = target.section,
                    rowIndex = target.rowIndex,
                    subjectName = subjectName,
                    teacherId = teacherId,
                    targetDays = targetDays,
                    onCompleted = onDone
                )
            },
            onDismiss = { editingTarget = null }
        )
    }

    // Clash Report Dialog
    if (showClashReportDialog) {
        TimetableClashReportDialog(
            clashes = allClashes,
            canEdit = canEdit,
            onJumpToCell = { cell ->
                showClashReportDialog = false
                jumpToCell(cell, openEditorImmediately = canEdit)
            },
            onDismiss = { showClashReportDialog = false }
        )
    }

    // Latest-edit-wins notice details dialog
    if (showConflictNoticeDialog) {
        TimetableConflictNoticeDialog(
            conflicts = uiState.timetableConflicts,
            schoolNamesByDocId = uiState.schoolNamesByDocId,
            onDismiss = { showConflictNoticeDialog = false },
            onClearAll = {
                viewModel.dismissTimetableConflictBanner()
                showConflictNoticeDialog = false
            }
        )
    }

    // Print & Batch Export Hub Dialog
    if (showPrintHubDialog) {
        TimetablePrintHubDialog(
            schoolName = uiState.schoolNameByDocId(selectedSchoolDocId),
            className = selectedClassName,
            section = selectedSection,
            selectedTeacherName = availableTeachers.firstOrNull { it.id == selectedTeacherId }?.name.orEmpty(),
            hasSelectedTeacher = selectedTeacherId.isNotBlank(),
            onPrintClass = { orientation ->
                showPrintHubDialog = false
                viewModel.generateClassTimetablePdf(selectedSchoolDocId, selectedClassName, selectedSection, orientation)
            },
            onBatchAllClasses = { orientation, includeZip ->
                showPrintHubDialog = false
                viewModel.generateBatchAllClassesTimetable(selectedSchoolDocId, orientation, includeZip)
            },
            onPrintTeacher = {
                showPrintHubDialog = false
                if (selectedTeacherId.isNotBlank()) {
                    viewModel.generateTeacherTimetablePdf(selectedTeacherId)
                }
            },
            onBatchAllTeachers = { includeZip ->
                showPrintHubDialog = false
                viewModel.generateBatchAllTeachersTimetable(includeZip)
            },
            onPrintMaster = {
                showPrintHubDialog = false
                viewModel.generateMasterTimetablePdf(selectedSchoolDocId)
            },
            onDismiss = { showPrintHubDialog = false }
        )
    }

    // Live PDF Preview & Offline Print/Save/Share Dialog
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
private fun ClassTimetableTab(
    uiState: SchoolUiState,
    canEdit: Boolean,
    selectedSchoolDocId: String,
    onSelectSchoolDocId: (String) -> Unit,
    selectedClassName: String,
    onSelectClassName: (String) -> Unit,
    selectedSection: String,
    onSelectSection: (String) -> Unit,
    sectionsForClass: List<String>,
    clashCellIds: Set<String>,
    onCellClick: (CellEditTarget) -> Unit
) {
    val schoolLongId = uiState.schoolDocIdByLongId.entries.firstOrNull { it.value == selectedSchoolDocId }?.key
        ?: uiState.activeSchool.id
    val classesInSchool = remember(schoolLongId, uiState.schoolClasses) { uiState.classesForSchool(schoolLongId) }
    val periodStructure = remember(selectedSchoolDocId, uiState.effectivePeriodStructuresBySchool) {
        uiState.periodStructureForSchool(selectedSchoolDocId)
    }
    val workingDays = remember(periodStructure) {
        periodStructure.workingDays.sortedBy { it.sortOrder }.ifEmpty { DayOfWeekCode.defaultWorkingDays }
    }
    val gridRows = remember(periodStructure) { TimetableEngine.buildGridRows(periodStructure) }

    val classCells = remember(
        uiState.activeSessionTimetableCells,
        selectedSchoolDocId,
        selectedClassName,
        selectedSection
    ) {
        uiState.activeSessionTimetableCells.filter {
            it.schoolId == selectedSchoolDocId &&
                it.className.equals(selectedClassName, ignoreCase = true) &&
                it.section.equals(selectedSection, ignoreCase = true)
        }
    }
    val cellLookup = remember(classCells) {
        classCells.associateBy { it.day to it.slotId }
    }

    val classSubjects = remember(schoolLongId, selectedClassName, uiState.subjects, uiState.schoolClasses, uiState.classGroups) {
        uiState.subjectsForClass(selectedClassName, schoolLongId)
    }
    val progressList = remember(
        selectedSchoolDocId,
        uiState.filter.session,
        selectedClassName,
        selectedSection,
        classSubjects,
        uiState.activeSessionTimetableCells,
        uiState.classAssignments
    ) {
        TimetableEngine.buildClassSubjectProgress(
            schoolId = selectedSchoolDocId,
            session = uiState.filter.session,
            className = selectedClassName,
            section = selectedSection,
            classSubjects = classSubjects,
            cellsInSession = uiState.activeSessionTimetableCells,
            classAssignments = uiState.classAssignments
        )
    }

    val assignment = remember(uiState.classAssignments, selectedSchoolDocId, selectedClassName, selectedSection) {
        uiState.classAssignments.firstOrNull {
            it.schoolId == selectedSchoolDocId &&
                it.className.equals(selectedClassName, ignoreCase = true) &&
                it.section.equals(selectedSection, ignoreCase = true)
        }
    }
    val classTeacherName = remember(assignment, uiState.teachers) {
        assignment?.classTeacherId?.takeIf { it.isNotBlank() }?.let { ctId ->
            uiState.availableTimetableTeachers().firstOrNull { it.id == ctId }?.name
        }.orEmpty()
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Controls & Weekly Progress Summary
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.activeSchools.size > 1) {
                        val schoolOptions = uiState.activeSchools.map { it.name }
                        val currentSchoolName = uiState.schoolNameByDocId(selectedSchoolDocId)
                        Box(modifier = Modifier.weight(1.3f)) {
                            CompactDropdownSelector(
                                label = "School",
                                selectedValue = currentSchoolName,
                                options = schoolOptions,
                                onSelect = { picked ->
                                    val sch = uiState.activeSchools.firstOrNull { it.name == picked }
                                    sch?.let { s -> uiState.schoolDocIdByLongId[s.id]?.let(onSelectSchoolDocId) }
                                },
                                testTag = "tt_school_picker"
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CompactDropdownSelector(
                            label = "Class",
                            selectedValue = selectedClassName.ifBlank { "—" },
                            options = classesInSchool.map { it.name }.ifEmpty { listOf("—") },
                            onSelect = onSelectClassName,
                            testTag = "tt_class_picker"
                        )
                    }
                    Box(modifier = Modifier.weight(0.75f)) {
                        CompactDropdownSelector(
                            label = "Sec",
                            selectedValue = selectedSection,
                            options = sectionsForClass,
                            onSelect = onSelectSection,
                            testTag = "tt_section_picker"
                        )
                    }
                }

                // Class Teacher + Zoom bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = buildString {
                            append("Class $selectedClassName-$selectedSection")
                            if (classTeacherName.isNotBlank()) append(" • CT: $classTeacherName")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TimetableZoomControls(
                        zoomScale = zoomScale,
                        onZoomChange = { zoomScale = it }
                    )
                }

                // Weekly Subject Summary Chips ("Maths 4/6")
                if (progressList.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        progressList.forEach { prog ->
                            SubjectProgressPill(progress = prog)
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        if (selectedClassName.isBlank() || gridRows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Configure classes and period structure to view or edit the timetable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            return
        }

        // 2D Scrollable + Zoomable Grid with Sticky Top Header Row & Sticky Left Column
        val timeColWidth = (88f * zoomScale).dp
        val dayColWidth = (116f * zoomScale).dp
        val headerHeight = 44.dp
        val periodRowHeight = (66f * zoomScale).coerceAtLeast(52f).dp
        val breakRowHeight = (36f * zoomScale).coerceAtLeast(30f).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            zoomScale = (zoomScale * zoom).coerceIn(0.75f, 1.5f)
                        }
                    }
                }
                .testTag("tt_class_grid")
        ) {
            // Sticky Header Row (Corner + Horizontally Scrolled Day Headers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyPrimary)
            ) {
                Box(
                    modifier = Modifier
                        .width(timeColWidth)
                        .height(headerHeight)
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Period",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                ) {
                    workingDays.forEach { day ->
                        Box(
                            modifier = Modifier
                                .width(dayColWidth)
                                .height(headerHeight)
                                .border(0.5.dp, Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.shortLabel.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Vertically Scrolled Body (Sticky Left Time Column + Horizontally Scrolled Cells)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(vScroll)
            ) {
                // Sticky Left Period/Time Column
                Column(modifier = Modifier.width(timeColWidth)) {
                    gridRows.forEach { row ->
                        val rowH = if (row.isEditablePeriodRow) periodRowHeight else breakRowHeight
                        val bg = if (row.isEditablePeriodRow) SurfaceMist else MaterialTheme.colorScheme.surfaceVariant
                        Box(
                            modifier = Modifier
                                .width(timeColWidth)
                                .height(rowH)
                                .background(bg)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = row.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = (11f * zoomScale.coerceIn(0.85f, 1.2f)).sp
                                )
                                Text(
                                    text = row.timeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = (9.5f * zoomScale.coerceIn(0.85f, 1.15f)).sp
                                )
                            }
                        }
                    }
                }

                // Horizontally Scrolled Day Cells
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                ) {
                    gridRows.forEach { row ->
                        val rowH = if (row.isEditablePeriodRow) periodRowHeight else breakRowHeight
                        if (!row.isEditablePeriodRow) {
                            // Fixed non-editable shaded assembly / break / lunch row
                            Box(
                                modifier = Modifier
                                    .width(dayColWidth * workingDays.size)
                                    .height(rowH)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${row.label}  (${row.timeLabel})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Row {
                                workingDays.forEach { day ->
                                    val daySlot = row.slotByDay[day]
                                    val cell = daySlot?.let { cellLookup[day to it.id] }
                                    val hasClash = cell != null && cell.id in clashCellIds
                                    ClassTimetableGridCell(
                                        daySlot = daySlot,
                                        cell = cell,
                                        hasClash = hasClash,
                                        canEdit = canEdit,
                                        zoomScale = zoomScale,
                                        width = dayColWidth,
                                        height = rowH,
                                        testTag = "tt_cell_${day.wireValue}_${row.rowIndex}",
                                        onClick = {
                                            if (daySlot != null) {
                                                onCellClick(
                                                    CellEditTarget(
                                                        schoolDocId = selectedSchoolDocId,
                                                        className = selectedClassName,
                                                        section = selectedSection,
                                                        day = day,
                                                        slot = daySlot,
                                                        rowIndex = row.rowIndex,
                                                        existingCell = cell
                                                    )
                                                )
                                            }
                                        }
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

@Composable
private fun ClassTimetableGridCell(
    daySlot: PeriodSlot?,
    cell: TimetableCell?,
    hasClash: Boolean,
    canEdit: Boolean,
    zoomScale: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    testTag: String,
    onClick: () -> Unit
) {
    val bgColor = when {
        daySlot == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        hasClash -> FailRedContainer
        cell != null -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.background
    }
    val borderColor = when {
        hasClash -> FailRed
        cell?.locked == true -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (hasClash || cell?.locked == true) 1.2.dp else 0.5.dp

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(bgColor)
            .border(borderWidth, borderColor)
            .then(
                if (daySlot != null) {
                    Modifier
                        .testTag(testTag)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        when {
            daySlot == null -> {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            cell == null -> {
                Text(
                    text = if (canEdit) "+ Add" else "Free",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = cell.subjectName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (hasClash) FailRed else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = (12f * zoomScale.coerceIn(0.85f, 1.25f)).sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = cell.teacherName.ifBlank { TimetableEngine.shortTeacherName(cell.teacherId) },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasClash) FailRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = (10.5f * zoomScale.coerceIn(0.85f, 1.2f)).sp,
                        textAlign = TextAlign.Center
                    )
                }
                if (cell.locked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(13.dp)
                            .align(Alignment.TopEnd)
                    )
                }
                if (hasClash) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Clash detected",
                        tint = FailRed,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.TopStart)
                    )
                } else if (cell.hasPendingWrites) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AbsentAmber)
                            .align(Alignment.TopStart)
                    )
                }
            }
        }
    }
}

@Composable
internal fun TimetableZoomControls(
    zoomScale: Float,
    onZoomChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = { onZoomChange((zoomScale - 0.15f).coerceIn(0.75f, 1.5f)) },
            modifier = Modifier
                .size(44.dp)
                .testTag("tt_zoom_out")
        ) {
            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out", modifier = Modifier.size(20.dp))
        }
        Text(
            text = "${(zoomScale * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { onZoomChange(1f) }
                .padding(horizontal = 4.dp)
        )
        IconButton(
            onClick = { onZoomChange((zoomScale + 0.15f).coerceIn(0.75f, 1.5f)) },
            modifier = Modifier
                .size(44.dp)
                .testTag("tt_zoom_in")
        ) {
            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SubjectProgressPill(progress: SubjectWeeklyProgress) {
    val (bg, fg) = when {
        progress.isExactMatch -> PassGreenContainer to PassGreen
        progress.isOver -> FailRedContainer to FailRed
        progress.assignedCount > 0 && progress.placedCount > 0 -> AbsentAmberContainer to AbsentAmber
        else -> SurfaceMist to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(0.8.dp, fg.copy(alpha = 0.35f))
    ) {
        Text(
            text = progress.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimetableCellEditorDialog(
    uiState: SchoolUiState,
    target: CellEditTarget,
    canEdit: Boolean,
    onSave: (subjectName: String, teacherId: String, locked: Boolean) -> Unit,
    onToggleLock: (TimetableCell) -> Unit,
    onClear: (TimetableCell) -> Unit,
    onCopyToDays: (subjectName: String, teacherId: String, targetDays: Collection<DayOfWeekCode>, onDone: (Int, List<String>) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val existing = target.existingCell
    val schoolLongId = uiState.schoolDocIdByLongId.entries.firstOrNull { it.value == target.schoolDocId }?.key
        ?: uiState.activeSchool.id
    val classSubjects = remember(schoolLongId, target.className, existing?.subjectName, uiState.subjects, uiState.classAssignments) {
        buildList {
            uiState.subjectsForClass(target.className, schoolLongId).forEach { add(it) }
            uiState.classAssignments.firstOrNull {
                it.schoolId == target.schoolDocId &&
                    it.className.equals(target.className, ignoreCase = true) &&
                    it.section.equals(target.section, ignoreCase = true)
            }?.subjectAssignments?.forEach { sa ->
                if (none { it.equals(sa.subjectName, ignoreCase = true) }) add(sa.subjectName)
            }
            existing?.subjectName?.takeIf { it.isNotBlank() }?.let { exSub ->
                if (none { it.equals(exSub, ignoreCase = true) }) add(exSub)
            }
        }
    }

    var selectedSubject by remember(existing?.id) {
        mutableStateOf(existing?.subjectName?.ifBlank { classSubjects.firstOrNull().orEmpty() } ?: classSubjects.firstOrNull().orEmpty())
    }
    var selectedTeacherId by remember(existing?.id) {
        mutableStateOf(existing?.teacherId.orEmpty())
    }
    var lockedFlag by remember(existing?.id, existing?.locked) {
        mutableStateOf(existing?.locked == true)
    }
    var showCopyDialog by remember { mutableStateOf(false) }

    val cellId = remember(target, uiState.filter.session) {
        TimetableEngine.makeCellDocId(
            schoolId = target.schoolDocId,
            session = uiState.filter.session,
            className = target.className,
            section = target.section,
            day = target.day,
            slotId = target.slot.id
        )
    }

    val teacherOptions = remember(
        target,
        selectedSubject,
        uiState.teachers,
        uiState.activeSessionTimetableCells,
        uiState.effectivePeriodStructuresBySchool,
        uiState.classAssignments,
        uiState.schoolNamesByDocId
    ) {
        TimetableEngine.buildTeacherPickerOptions(
            schoolId = target.schoolDocId,
            session = uiState.filter.session,
            className = target.className,
            section = target.section,
            day = target.day,
            slotId = target.slot.id,
            subjectName = selectedSubject,
            currentCellId = cellId,
            teachers = uiState.availableTimetableTeachers(),
            cellsInSession = uiState.activeSessionTimetableCells,
            periodStructuresBySchool = uiState.effectivePeriodStructuresBySchool,
            classAssignments = uiState.classAssignments,
            schoolNamesById = uiState.schoolNamesByDocId
        )
    }

    // Auto-select assigned teacher when picking a subject in an empty cell
    LaunchedEffect(selectedSubject) {
        if (existing == null && selectedTeacherId.isBlank()) {
            val assignedFree = teacherOptions.firstOrNull {
                it.tier == 0 && it.availability == TeacherSlotAvailability.FREE
            } ?: teacherOptions.firstOrNull { it.tier == 0 }
            if (assignedFree != null) {
                selectedTeacherId = assignedFree.teacher.id
            }
        }
    }

    // Live Hard Conflict & Soft Warning check
    val selectedTeacherName = remember(selectedTeacherId, teacherOptions) {
        teacherOptions.firstOrNull { it.teacher.id == selectedTeacherId }?.teacher?.name.orEmpty()
    }
    val validation = remember(
        cellId,
        target,
        selectedSubject,
        selectedTeacherId,
        selectedTeacherName,
        lockedFlag,
        uiState.activeSessionTimetableCells,
        uiState.teachers,
        uiState.effectivePeriodStructuresBySchool,
        uiState.classAssignments,
        uiState.subjectRules,
        uiState.schoolNamesByDocId
    ) {
        TimetableEngine.validateCellPlacement(
            candidate = TimetableCell(
                id = cellId,
                schoolId = target.schoolDocId,
                session = uiState.filter.session,
                className = target.className,
                section = target.section,
                day = target.day,
                slotId = target.slot.id,
                subjectName = selectedSubject,
                teacherId = selectedTeacherId,
                teacherName = selectedTeacherName,
                locked = lockedFlag
            ),
            existingCellsInSession = uiState.activeSessionTimetableCells,
            teachers = uiState.teachers,
            periodStructuresBySchool = uiState.effectivePeriodStructuresBySchool,
            classAssignments = uiState.classAssignments,
            subjectRules = uiState.subjectRules,
            schoolNamesById = uiState.schoolNamesByDocId,
            allowReplaceSameCellId = true
        )
    }

    val isLockedForEdit = existing?.locked == true && lockedFlag

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "${target.className}-${target.section} • ${target.day.fullLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${target.slot.label} (${target.slot.startTimeFormatted}–${target.slot.endTimeFormatted})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!canEdit) {
                    if (existing == null) {
                        Text("This period is currently free.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Subject: ${existing.subjectName}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Teacher: ${existing.teacherName.ifBlank { "Unassigned" }}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (existing.locked) {
                            Text("Status: Locked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    return@Column
                }

                // Lock toggle when editing an existing cell
                if (existing != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceMist,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (lockedFlag) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (lockedFlag) "Locked (unlock to change)" else "Unlocked",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Switch(
                                checked = lockedFlag,
                                onCheckedChange = { newLocked ->
                                    lockedFlag = newLocked
                                    onToggleLock(existing)
                                },
                                modifier = Modifier.testTag("tt_editor_lock_switch")
                            )
                        }
                    }
                }

                // Step 1: Subject Picker
                Text(
                    text = "1. Select Subject",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (classSubjects.isEmpty()) {
                    Text(
                        text = "No subjects configured for Class ${target.className}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FailRed
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        classSubjects.forEach { subj ->
                            FilterChip(
                                selected = selectedSubject.equals(subj, ignoreCase = true),
                                enabled = !isLockedForEdit,
                                onClick = {
                                    selectedSubject = subj
                                    val assigned = teacherOptions.firstOrNull {
                                        it.tier == 0 && it.availability == TeacherSlotAvailability.FREE
                                    }
                                    if (existing == null && assigned != null) {
                                        selectedTeacherId = assigned.teacher.id
                                    }
                                },
                                label = { Text(subj, maxLines = 1) }
                            )
                        }
                    }
                }

                // Step 2: Teacher Picker (Assigned first -> Can teach subject -> Others, with Free/Busy/Off-day badge)
                Text(
                    text = "2. Select Teacher (${teacherOptions.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (teacherOptions.isEmpty()) {
                    Text(
                        text = "No teachers found yet. Add teachers in Teachers & Setup first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        var lastTier = -1
                        teacherOptions.forEach { opt ->
                            if (opt.tier != lastTier) {
                                lastTier = opt.tier
                                val sectionTitle = when (opt.tier) {
                                    0 -> "Assigned to ${target.className}-${target.section} • $selectedSubject"
                                    1 -> "Others who can teach $selectedSubject"
                                    else -> "Other teachers"
                                }
                                Text(
                                    text = sectionTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            TeacherPickerRow(
                                option = opt,
                                selected = selectedTeacherId == opt.teacher.id,
                                enabled = !isLockedForEdit,
                                onClick = { selectedTeacherId = opt.teacher.id }
                            )
                        }
                    }
                }

                // Hard Conflicts (Blocks Save)
                if (validation.hardConflicts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = FailRedContainer,
                        border = BorderStroke(1.dp, FailRed.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tt_editor_hard_conflict_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = FailRed, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Hard Conflict — Cannot Save",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = FailRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            validation.hardConflicts.forEach { hc ->
                                Text(
                                    text = "• ${hc.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF5A1212)
                                )
                            }
                        }
                    }
                }

                // Soft Warnings (Allows Save)
                if (validation.softWarnings.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AbsentAmberContainer,
                        border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tt_editor_soft_warning_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AbsentAmber, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Soft Warning (Save Allowed)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AbsentAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            validation.softWarnings.forEach { sw ->
                                Text(
                                    text = "• ${sw.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF5A3700)
                                )
                            }
                        }
                    }
                }

                // Secondary Actions: Copy to other days & Clear Cell
                if (existing != null || (selectedSubject.isNotBlank() && validation.canSave)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCopyDialog = true },
                            enabled = selectedSubject.isNotBlank() && validation.canSave,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tt_editor_copy_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Days", maxLines = 1)
                        }
                        if (existing != null) {
                            OutlinedButton(
                                onClick = { onClear(existing) },
                                enabled = !existing.locked,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = FailRed),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tt_editor_clear_button")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Cell", maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (canEdit) {
                Button(
                    onClick = { onSave(selectedSubject, selectedTeacherId, lockedFlag) },
                    enabled = !isLockedForEdit && selectedSubject.isNotBlank() && validation.canSave,
                    modifier = Modifier.testTag("tt_editor_save_button")
                ) {
                    Text(if (validation.softWarnings.isNotEmpty()) "Save Anyway" else "Save")
                }
            } else {
                Button(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (canEdit) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (showCopyDialog) {
        val structure = uiState.periodStructureForSchool(target.schoolDocId)
        val otherDays = structure.workingDays.sortedBy { it.sortOrder }.filter { it != target.day }
        CopyCellToDaysDialog(
            sourceDay = target.day,
            candidateDays = otherDays,
            subjectName = selectedSubject,
            teacherName = selectedTeacherName,
            onConfirmCopy = { chosenDays ->
                onCopyToDays(selectedSubject, selectedTeacherId, chosenDays) { _, _ ->
                    showCopyDialog = false
                }
            },
            onDismiss = { showCopyDialog = false }
        )
    }
}

@Composable
private fun TeacherPickerRow(
    option: TeacherPickerOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val (badgeBg, badgeFg) = when (option.availability) {
        TeacherSlotAvailability.FREE -> PassGreenContainer to PassGreen
        TeacherSlotAvailability.BUSY -> FailRedContainer to FailRed
        TeacherSlotAvailability.OFF_DAY -> AbsentAmberContainer to AbsentAmber
        TeacherSlotAvailability.DAILY_LIMIT_REACHED -> FailRedContainer to FailRed
        TeacherSlotAvailability.INACTIVE -> SurfaceMist to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.8.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.teacher.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = option.statusDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeFg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeBg
            ) {
                Text(
                    text = when (option.availability) {
                        TeacherSlotAvailability.FREE -> "Free"
                        TeacherSlotAvailability.BUSY -> "Busy"
                        TeacherSlotAvailability.OFF_DAY -> "Off Day"
                        TeacherSlotAvailability.DAILY_LIMIT_REACHED -> "Limit"
                        TeacherSlotAvailability.INACTIVE -> "Inactive"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeFg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun CopyCellToDaysDialog(
    sourceDay: DayOfWeekCode,
    candidateDays: List<DayOfWeekCode>,
    subjectName: String,
    teacherName: String,
    onConfirmCopy: (Set<DayOfWeekCode>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDays by remember { mutableStateOf(emptySet<DayOfWeekCode>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy Cell to Other Days") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Copy $subjectName${if (teacherName.isNotBlank()) " ($teacherName)" else ""} from ${sourceDay.fullLabel} to the same period slot on:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                candidateDays.forEach { day ->
                    val checked = day in selectedDays
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable {
                                selectedDays = if (checked) selectedDays - day else selectedDays + day
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selectedDays = if (it) selectedDays + day else selectedDays - day
                            }
                        )
                        Text(day.fullLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    text = "Days with hard conflicts or locked cells will be skipped automatically.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCopy(selectedDays) },
                enabled = selectedDays.isNotEmpty(),
                modifier = Modifier.testTag("tt_copy_confirm_button")
            ) {
                Text("Copy (${selectedDays.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TimetableClashReportDialog(
    clashes: List<TimetableHardConflict>,
    canEdit: Boolean,
    onJumpToCell: (TimetableCell) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (clashes.isNotEmpty()) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (clashes.isNotEmpty()) FailRed else PassGreen
                )
                Text(
                    text = if (clashes.isNotEmpty()) "Clash Report (${clashes.size})" else "No Timetable Clashes",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            if (clashes.isEmpty()) {
                Text(
                    text = "All scheduled periods across every school in this organisation pass hard conflict checks (no double-booked teachers, no class overlaps, no off-day or daily-limit violations).",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(clashes) { clash ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = FailRedContainer),
                            border = BorderStroke(1.dp, FailRed.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = clash.type.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = FailRed,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = clash.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF5A1212)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    clash.primaryCell?.let { cellA ->
                                        OutlinedButton(
                                            onClick = { onJumpToCell(cellA) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (canEdit) "Fix ${cellA.classSectionLabel}" else "View ${cellA.classSectionLabel}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    clash.conflictingCell?.let { cellB ->
                                        OutlinedButton(
                                            onClick = { onJumpToCell(cellB) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (canEdit) "Fix ${cellB.classSectionLabel}" else "View ${cellB.classSectionLabel}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun TimetableConflictNoticeDialog(
    conflicts: List<TimetableConflictNotice>,
    schoolNamesByDocId: Map<String, String>,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replaced Offline Timetable Edits") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conflicts) { c ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = AbsentAmberContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "${c.classSectionLabel} (${schoolNamesByDocId[c.schoolId] ?: c.schoolId}) • ${c.day.shortLabel}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4E2D00)
                            )
                            Text(
                                text = "Your offline edit: ${c.yourSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5A3700)
                            )
                            Text(
                                text = "Newer edit kept (${c.winnerName}): ${c.winningSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF3E2200)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClearAll) { Text("Acknowledge & Clear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
