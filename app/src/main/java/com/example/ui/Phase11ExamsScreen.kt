package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.ExamCategoryType
import com.example.domain.ExamInvigilatorDutyRecord
import com.example.domain.ExamRoomRecord
import com.example.domain.ExamScheduleEngine
import com.example.domain.ExamScheduleRecord
import com.example.domain.ExamSeatAllocation
import com.example.domain.ExamSeatingRecord
import com.example.domain.Phase6DateUtils
import com.example.domain.SeatingAllocationMode
import com.example.domain.TeacherProfile
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import com.example.util.AdmitCardStudentItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase11ExamsScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    val isHindi = uiState.phase10HindiLabels
    val schoolDocId = uiState.activeSchoolDocId
    val session = uiState.filter.session
    val canEdit = uiState.canEditExams

    val schoolExams = remember(uiState.examSchedules, schoolDocId, session) {
        uiState.examSchedules
            .filter { it.schoolId == schoolDocId && (it.session.isBlank() || it.session.equals(session, ignoreCase = true)) }
            .sortedByDescending { it.clientEditedAtMs }
    }
    val schoolRooms = remember(uiState.examRooms, schoolDocId) {
        uiState.examRooms.filter { it.schoolId == schoolDocId && it.active }.sortedBy { it.roomName }
    }
    val classNames = remember(uiState.activeSchoolClasses) {
        uiState.activeSchoolClasses.map { it.name }.distinct()
    }

    var selectedExamId by remember(schoolExams) {
        mutableStateOf(schoolExams.firstOrNull()?.id.orEmpty())
    }
    val selectedExam = schoolExams.firstOrNull { it.id == selectedExamId } ?: schoolExams.firstOrNull()
    var selectedTab by remember { mutableIntStateOf(0) }

    var showCreateExamDialog by remember { mutableStateOf(false) }
    var showAddPaperDialog by remember { mutableStateOf(false) }
    var showAddRoomDialog by remember { mutableStateOf(false) }
    var editingRoom by remember { mutableStateOf<ExamRoomRecord?>(null) }
    var showAllocateSeatingDialog by remember { mutableStateOf(false) }
    var movingSeatStudent by remember { mutableStateOf<ExamSeatAllocation?>(null) }
    var changingDuty by remember { mutableStateOf<ExamInvigilatorDutyRecord?>(null) }

    val selectedSeating = remember(uiState.examSeatings, selectedExam?.id) {
        selectedExam?.let { ex -> uiState.examSeatings.firstOrNull { it.examId == ex.id } }
    }
    val selectedDuties = remember(uiState.examDuties, selectedExam?.id) {
        selectedExam?.let { ex ->
            uiState.examDuties
                .filter { it.examId == ex.id }
                .sortedWith(compareBy({ it.dateIso }, { it.startTime }, { it.roomName }))
        }.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = p10Label(isHindi, "परीक्षा व प्रवेश पत्र (Exams)", "Exams & Admit Cards"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.activeSchool.name} • $session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("exams_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Phase10LanguageToggleChip(
                        isHindi = isHindi,
                        onToggle = { viewModel.togglePhase10HindiLabels() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        if (!uiState.canViewExams) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = p10Label(isHindi, "आपको परीक्षा मॉड्यूल की अनुमति नहीं है।", "You do not have access to the Exams module."),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Exam selector strip + Create Exam CTA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canEdit) {
                    Button(
                        onClick = { showCreateExamDialog = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("exams_create_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "नई परीक्षा", "New Exam"))
                    }
                }
                schoolExams.forEach { ex ->
                    FilterChip(
                        selected = selectedExam?.id == ex.id,
                        onClick = { selectedExamId = ex.id },
                        label = { Text("${ex.title} (${ex.entries.size})") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            if (selectedExam == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = p10Label(
                                isHindi,
                                "इस सत्र ($session) में अभी कोई परीक्षा नहीं बनी है।",
                                "No exam schedule created for session $session yet."
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (canEdit) {
                            Button(
                                onClick = { showCreateExamDialog = true },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(p10Label(isHindi, "पहली परीक्षा बनाएँ (UT-I / अर्धवार्षिक)", "Create First Exam"))
                            }
                        }
                    }
                }
            } else {
                val tabs = listOf(
                    p10Label(isHindi, "समय-सारणी (Schedule)", "Schedule"),
                    p10Label(isHindi, "कक्ष व बैठक (Seating)", "Rooms & Seating"),
                    p10Label(isHindi, "कक्ष निरीक्षक (Duties)", "Invigilators"),
                    p10Label(isHindi, "प्रवेश पत्र (Admit Cards)", "Admit Cards")
                )
                PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(title, maxLines = 1) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("exams_tab_$idx")
                        )
                    }
                }

                when (selectedTab) {
                    0 -> ExamScheduleTab(
                        exam = selectedExam,
                        classNames = classNames,
                        canEdit = canEdit,
                        isHindi = isHindi,
                        onAddPaper = { showAddPaperDialog = true },
                        onRemovePaper = { viewModel.removeExamPaperEntry(selectedExam, it) },
                        onPrintClassTimetable = { cls -> viewModel.generateExamTimetablePdf(selectedExam, cls) },
                        onDeleteExam = { viewModel.deleteExamScheduleRecord(selectedExam) }
                    )

                    1 -> ExamRoomsAndSeatingTab(
                        exam = selectedExam,
                        rooms = schoolRooms,
                        seating = selectedSeating,
                        canEdit = canEdit,
                        isHindi = isHindi,
                        onAddRoom = { editingRoom = null; showAddRoomDialog = true },
                        onEditRoom = { editingRoom = it; showAddRoomDialog = true },
                        onDeleteRoom = { viewModel.deleteExamRoomRecord(it) },
                        onOpenAllocateDialog = { showAllocateSeatingDialog = true },
                        onMoveStudent = { movingSeatStudent = it },
                        onPrintRoomSeating = { room -> viewModel.generateRoomSeatingPdf(selectedExam, room) }
                    )

                    2 -> ExamInvigilatorDutiesTab(
                        exam = selectedExam,
                        duties = selectedDuties,
                        canEdit = canEdit,
                        isHindi = isHindi,
                        onAutoAssign = {
                            viewModel.autoAssignExamInvigilators(selectedExam) { ok, err ->
                                if (!ok && err != null) viewModel.emitMessage(err)
                            }
                        },
                        onChangeDutyTeacher = { changingDuty = it },
                        onPrintDutyList = { viewModel.generateExamDutyListPdf(selectedExam) }
                    )

                    3 -> ExamAdmitCardsTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        exam = selectedExam,
                        seating = selectedSeating,
                        classNames = classNames,
                        canEdit = canEdit,
                        isHindi = isHindi
                    )
                }
            }
        }
    }

    // ---- DIALOGS ----
    if (showCreateExamDialog) {
        CreateExamDialog(
            session = session,
            isHindi = isHindi,
            onDismiss = { showCreateExamDialog = false },
            onSave = { type, title ->
                viewModel.createOrUpdateExamSchedule(null, type, title, session) { ok, err, newId ->
                    if (ok) {
                        if (!newId.isNullOrBlank()) selectedExamId = newId
                        showCreateExamDialog = false
                    } else if (err != null) {
                        viewModel.emitMessage(err)
                    }
                }
            }
        )
    }

    if (showAddPaperDialog && selectedExam != null) {
        AddExamPaperDialog(
            exam = selectedExam,
            classNames = classNames,
            uiState = uiState,
            isHindi = isHindi,
            onDismiss = { showAddPaperDialog = false },
            onSave = { cls, subj, dateIso, start, end, maxMarks, setError ->
                viewModel.addOrUpdateExamPaperEntry(selectedExam, cls, subj, dateIso, start, end, maxMarks) { ok, err ->
                    if (ok) showAddPaperDialog = false
                    else setError(err ?: "Validation failed")
                }
            }
        )
    }

    if (showAddRoomDialog) {
        ExamRoomDialog(
            existing = editingRoom,
            isHindi = isHindi,
            onDismiss = { showAddRoomDialog = false },
            onSave = { name, cap, setError ->
                viewModel.saveExamRoomRecord(editingRoom, name, cap) { ok, err ->
                    if (ok) showAddRoomDialog = false else setError(err ?: "Error saving room")
                }
            }
        )
    }

    if (showAllocateSeatingDialog && selectedExam != null) {
        AllocateSeatingDialog(
            exam = selectedExam,
            classNames = classNames,
            isHindi = isHindi,
            onDismiss = { showAllocateSeatingDialog = false },
            onConfirm = { classes, mode ->
                viewModel.autoAllocateExamSeating(selectedExam, classes, mode) { ok, err ->
                    if (ok) showAllocateSeatingDialog = false
                    else if (err != null) viewModel.emitMessage(err)
                }
            }
        )
    }

    if (movingSeatStudent != null && selectedSeating != null) {
        val alloc = movingSeatStudent!!
        AlertDialog(
            onDismissRequest = { movingSeatStudent = null },
            title = { Text(p10Label(isHindi, "कक्ष बदलें: ${alloc.studentName}", "Move ${alloc.studentName}")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${alloc.className}-${alloc.section} (Roll ${alloc.rollNo}) • Current: ${alloc.roomName} Seat #${alloc.seatNumber}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    schoolRooms.filter { it.id != alloc.roomId }.forEach { room ->
                        OutlinedButton(
                            onClick = {
                                viewModel.moveStudentExamSeatRoom(selectedSeating, alloc.studentDocId, room)
                                movingSeatStudent = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("${room.roomName} (Capacity ${room.capacity})")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { movingSeatStudent = null }) { Text("Close") }
            }
        )
    }

    if (changingDuty != null) {
        val duty = changingDuty!!
        val teachers = uiState.effectivePhase11Teachers.filter { it.active }.sortedBy { it.name }
        AlertDialog(
            onDismissRequest = { changingDuty = null },
            title = { Text(p10Label(isHindi, "निरीक्षक बदलें", "Change Invigilator")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "${duty.dateIso} (${duty.startTime}-${duty.endTime}) • ${duty.roomName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    teachers.forEach { t ->
                        val onLeave = ExamScheduleEngine.isTeacherOnApprovedLeave(t.id, duty.dateIso, uiState.effectivePhase11TeacherLeaves)
                        OutlinedButton(
                            onClick = {
                                viewModel.changeInvigilatorTeacherManually(duty, t)
                                changingDuty = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = if (onLeave) "${t.name} (On Leave)" else t.name,
                                color = if (onLeave) FailRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { changingDuty = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ExamScheduleTab(
    exam: ExamScheduleRecord,
    classNames: List<String>,
    canEdit: Boolean,
    isHindi: Boolean,
    onAddPaper: () -> Unit,
    onRemovePaper: (String) -> Unit,
    onPrintClassTimetable: (String) -> Unit,
    onDeleteExam: () -> Unit
) {
    val classesInExam = remember(exam.entries, classNames) {
        (classNames + exam.entries.map { it.className }).distinct()
    }
    var selectedClass by remember(classesInExam) { mutableStateOf(classesInExam.firstOrNull().orEmpty()) }
    val filteredEntries = remember(exam.entries, selectedClass) {
        exam.entries
            .filter { selectedClass.isBlank() || it.className.equals(selectedClass, ignoreCase = true) }
            .sortedWith(compareBy({ it.dateIso }, { it.startTime }))
    }
    val (minDate, maxDate) = remember(exam.session) { ExamScheduleEngine.sessionDateBounds(exam.session) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exam.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "सत्र सीमा: $minDate से $maxDate • कुल पेपर: ${exam.entries.size}",
                                    "Session range: $minDate to $maxDate • Total papers: ${exam.entries.size}"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canEdit) {
                            IconButton(onClick = onDeleteExam, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete exam", tint = FailRed)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canEdit) {
                            Button(
                                onClick = onAddPaper,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("exams_add_paper_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(isHindi, "पेपर जोड़ें", "Add Paper"))
                            }
                        }
                        if (selectedClass.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onPrintClassTimetable(selectedClass) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("exams_print_timetable_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(isHindi, "टाइमटेबल PDF", "Timetable PDF"))
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                classesInExam.forEach { cls ->
                    FilterChip(
                        selected = selectedClass == cls,
                        onClick = { selectedClass = cls },
                        label = { Text("Class $cls") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            item {
                Text(
                    text = p10Label(
                        isHindi,
                        "कक्षा $selectedClass के लिए अभी कोई पेपर नहीं जुड़ा है। ऊपर 'पेपर जोड़ें' दबाएँ।",
                        "No papers scheduled for Class $selectedClass yet. Tap 'Add Paper' above."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(filteredEntries, key = { it.entryId }) { entry ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${entry.subjectName} • Class ${entry.className}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${entry.dateIso}  •  ${entry.startTime} - ${entry.endTime}  •  Max ${entry.maxMarks}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canEdit) {
                            IconButton(
                                onClick = { onRemovePaper(entry.entryId) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove paper", tint = FailRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamRoomsAndSeatingTab(
    exam: ExamScheduleRecord,
    rooms: List<ExamRoomRecord>,
    seating: ExamSeatingRecord?,
    canEdit: Boolean,
    isHindi: Boolean,
    onAddRoom: () -> Unit,
    onEditRoom: (ExamRoomRecord) -> Unit,
    onDeleteRoom: (ExamRoomRecord) -> Unit,
    onOpenAllocateDialog: () -> Unit,
    onMoveStudent: (ExamSeatAllocation) -> Unit,
    onPrintRoomSeating: (ExamRoomRecord) -> Unit
) {
    var selectedRoomId by remember(rooms) { mutableStateOf(rooms.firstOrNull()?.id.orEmpty()) }
    val selectedRoom = rooms.firstOrNull { it.id == selectedRoomId } ?: rooms.firstOrNull()
    val roomAllocations = remember(seating, selectedRoom?.id) {
        seating?.allocations
            ?.filter { it.roomId == selectedRoom?.id }
            ?.sortedBy { it.seatNumber }
            .orEmpty()
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canEdit) {
                    OutlinedButton(
                        onClick = onAddRoom,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("exams_add_room_button")
                    ) {
                        Icon(Icons.Default.MeetingRoom, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "कक्ष जोड़ें", "Add Room"))
                    }
                    Button(
                        onClick = onOpenAllocateDialog,
                        enabled = rooms.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("exams_allocate_seating_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "ऑटो सीटिंग", "Auto-Allocate"))
                    }
                }
            }
        }

        if (seating != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (seating.unallocatedStudentIds.isEmpty()) PassGreenContainer else AbsentAmberContainer
                ) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "बैठक योजना: ${seating.allocations.size} छात्र आवंटित (${seating.allocationMode.hindiLabel})" +
                                if (seating.unallocatedStudentIds.isNotEmpty()) " • ${seating.unallocatedStudentIds.size} बिना सीट" else "",
                            "Seating Plan: ${seating.allocations.size} allocated (${seating.allocationMode.englishLabel})" +
                                if (seating.unallocatedStudentIds.isNotEmpty()) " • ${seating.unallocatedStudentIds.size} unallocated" else ""
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }

        if (rooms.isEmpty()) {
            item {
                Text(
                    text = p10Label(isHindi, "कृपया पहले परीक्षा कक्ष और सीट क्षमता जोड़ें।", "Please define exam rooms and capacity first."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rooms.forEach { room ->
                        val count = seating?.allocations?.count { it.roomId == room.id } ?: 0
                        FilterChip(
                            selected = selectedRoom?.id == room.id,
                            onClick = { selectedRoomId = room.id },
                            label = { Text("${room.roomName} ($count/${room.capacity})") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            if (selectedRoom != null) {
                item {
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = selectedRoom.roomName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Capacity: ${selectedRoom.capacity} • Occupied: ${roomAllocations.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onPrintRoomSeating(selectedRoom) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("exams_print_seating_button")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (canEdit) {
                                    OutlinedButton(
                                        onClick = { onEditRoom(selectedRoom) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.EventSeat, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(
                                        onClick = { onDeleteRoom(selectedRoom) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete room", tint = FailRed)
                                    }
                                }
                            }
                        }
                    }
                }

                items(roomAllocations, key = { "${it.roomId}_${it.studentDocId}" }) { alloc ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Seat #${alloc.seatNumber} • ${alloc.studentName}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Class ${alloc.className}-${alloc.section} • Roll ${alloc.rollNo} • Adm ${alloc.studentAdmNo}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canEdit && rooms.size > 1) {
                                TextButton(
                                    onClick = { onMoveStudent(alloc) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(p10Label(isHindi, "बदलें", "Move"))
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
private fun ExamInvigilatorDutiesTab(
    exam: ExamScheduleRecord,
    duties: List<ExamInvigilatorDutyRecord>,
    canEdit: Boolean,
    isHindi: Boolean,
    onAutoAssign: () -> Unit,
    onChangeDutyTeacher: (ExamInvigilatorDutyRecord) -> Unit,
    onPrintDutyList: () -> Unit
) {
    var groupByTeacher by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canEdit) {
                    Button(
                        onClick = onAutoAssign,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("exams_auto_assign_duties_button")
                    ) {
                        Icon(Icons.Default.AssignmentInd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "ऑटो ड्यूटी लगाएँ", "Auto-Assign"))
                    }
                }
                OutlinedButton(
                    onClick = onPrintDutyList,
                    enabled = duties.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("exams_print_duties_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(p10Label(isHindi, "ड्यूटी सूची PDF", "Duty List PDF"))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !groupByTeacher,
                    onClick = { groupByTeacher = false },
                    label = { Text(p10Label(isHindi, "तिथि व कक्ष अनुसार", "By Date & Room")) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = groupByTeacher,
                    onClick = { groupByTeacher = true },
                    label = { Text(p10Label(isHindi, "शिक्षक अनुसार (${duties.map { it.teacherId }.distinct().size})", "By Teacher")) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        if (duties.isEmpty()) {
            item {
                Text(
                    text = p10Label(
                        isHindi,
                        "अभी कोई निरीक्षक ड्यूटी आवंटित नहीं हुई है। 'ऑटो ड्यूटी लगाएँ' दबाएँ — छुट्टी पर रहने वाले शिक्षक अपने आप छूट जाएँगे।",
                        "No invigilator duties assigned yet. Tap Auto-Assign to balance duties while automatically avoiding teachers on leave."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (!groupByTeacher) {
            items(duties, key = { it.id }) { duty ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                text = "${duty.dateIso} • ${duty.startTime}-${duty.endTime} • ${duty.roomName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = duty.teacherName + if (duty.assignedManually) " (Manual)" else "",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (canEdit) {
                            TextButton(
                                onClick = { onChangeDutyTeacher(duty) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(p10Label(isHindi, "बदलें", "Change"))
                            }
                        }
                    }
                }
            }
        } else {
            val grouped = duties.groupBy { it.teacherName }.entries.sortedBy { it.key }
            items(grouped, key = { it.key }) { (teacherName, list) ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "$teacherName (${list.size} duties)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        list.forEach { d ->
                            Text(
                                text = "• ${d.dateIso} (${d.startTime}-${d.endTime}) — ${d.roomName}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamAdmitCardsTab(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    exam: ExamScheduleRecord,
    seating: ExamSeatingRecord?,
    classNames: List<String>,
    canEdit: Boolean,
    isHindi: Boolean
) {
    val classesAvailable = remember(classNames, exam.entries) {
        (classNames + exam.entries.map { it.className }).distinct()
    }
    var selectedClass by remember(classesAvailable) { mutableStateOf(classesAvailable.firstOrNull().orEmpty()) }
    var thresholdText by remember(exam.id, exam.withholdAdmitCardAboveDuesRs) {
        mutableStateOf(if (exam.withholdAdmitCardAboveDuesRs > 0) exam.withholdAdmitCardAboveDuesRs.toString() else "")
    }

    val schoolLongId = uiState.schoolRecordsByDocId[exam.schoolId]?.stableId ?: uiState.activeSchool.id
    val classStudents = remember(uiState.allStudents, schoolLongId, selectedClass) {
        uiState.allStudents
            .filter { it.schoolId == schoolLongId && it.isActiveStatus && it.className.equals(selectedClass, ignoreCase = true) }
            .map { st -> uiState.studentDocIdFor(st.id).ifBlank { st.admNo } to st }
    }
    val duesMap = remember(uiState.effectivePhase11FeeReceipts, uiState.effectivePhase11FeeStructures, uiState.effectivePhase11FeeOverrides, exam.schoolId, exam.session, classStudents) {
        viewModel.computeStudentFeeDuesMapForSchool(exam.schoolId, exam.session)
    }
    val eligibilities = remember(exam, classStudents, duesMap, seating) {
        ExamScheduleEngine.evaluateAdmitCardEligibility(exam, classStudents, duesMap, seating)
    }
    val classEntries = remember(exam.entries, selectedClass) {
        exam.entries.filter { it.className.equals(selectedClass, ignoreCase = true) }
    }
    val printable = remember(eligibilities) { eligibilities.filter { it.canPrint } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = p10Label(isHindi, "फीस बकाया चेतावनी (वैकल्पिक)", "Fee Dues Withhold Warning (Optional)"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = thresholdText,
                            onValueChange = { thresholdText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(p10Label(isHindi, "बकाया सीमा ₹ (0 = बंद)", "Withhold above ₹ (0 = Off)")) },
                            singleLine = true,
                            enabled = canEdit,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        if (canEdit) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateExamDuesWithholdThreshold(exam, thresholdText.toIntOrNull() ?: 0)
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(p10Label(isHindi, "सेट करें", "Apply"))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        classesAvailable.forEach { cls ->
                            FilterChip(
                                selected = selectedClass == cls,
                                onClick = { selectedClass = cls },
                                label = { Text("Class $cls") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val items = printable.map { el ->
                                AdmitCardStudentItem(
                                    student = el.student,
                                    seat = el.seat,
                                    entries = classEntries
                                )
                            }
                            viewModel.generateClassAdmitCardsPdf(exam, selectedClass, items)
                        },
                        enabled = printable.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("exams_print_admit_cards_button")
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            p10Label(
                                isHindi,
                                "कक्षा $selectedClass के प्रवेश पत्र PDF (${printable.size}/${eligibilities.size})",
                                "Print Class $selectedClass Admit Cards (${printable.size}/${eligibilities.size})"
                            )
                        )
                    }
                }
            }
        }

        items(eligibilities, key = { it.studentDocId }) { el ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (el.isWithheldByDues && !el.isOverriddenByAdmin) FailRedContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (el.isWithheldByDues && !el.isOverriddenByAdmin) FailRed.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${el.student.name} (Roll ${el.student.rollNo})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val seatTxt = el.seat?.let { "${it.roomName} • Seat #${it.seatNumber}" } ?: "Room TBA"
                        Text(
                            text = "$seatTxt • Fee Due: ₹${el.totalFeeDueRs}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (el.isWithheldByDues) {
                            Text(
                                text = if (el.isOverriddenByAdmin) {
                                    p10Label(isHindi, "एडमिन अनुमति द्वारा स्वीकृत (Override)", "Allowed via Admin Override")
                                } else {
                                    p10Label(isHindi, "चेतावनी: बकाया ₹${el.totalFeeDueRs} > ₹${exam.withholdAdmitCardAboveDuesRs}", "Warning: Dues ₹${el.totalFeeDueRs} > ₹${exam.withholdAdmitCardAboveDuesRs}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (el.isOverriddenByAdmin) PassGreen else FailRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (el.isWithheldByDues && uiState.isAdmin) {
                        OutlinedButton(
                            onClick = { viewModel.toggleAdmitCardDuesOverride(exam, el.studentDocId) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(if (el.isOverriddenByAdmin) "Revoke" else "Override")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateExamDialog(
    session: String,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (ExamCategoryType, String) -> Unit
) {
    var selectedType by remember { mutableStateOf(ExamCategoryType.UT_I) }
    var customTitle by remember { mutableStateOf(ExamCategoryType.UT_I.englishLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "नई परीक्षा बनाएँ ($session)", "Create Exam ($session)")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExamCategoryType.entries.forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = {
                                selectedType = t
                                if (t != ExamCategoryType.CUSTOM) customTitle = t.englishLabel
                                else customTitle = ""
                            },
                            label = { Text(t.englishLabel) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text(p10Label(isHindi, "परीक्षा का नाम", "Exam Title")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedType, customTitle) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "बनाएँ", "Create"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddExamPaperDialog(
    exam: ExamScheduleRecord,
    classNames: List<String>,
    uiState: SchoolUiState,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Int, (String) -> Unit) -> Unit
) {
    var selectedClass by remember { mutableStateOf(classNames.firstOrNull().orEmpty()) }
    var subjectName by remember { mutableStateOf("") }
    var dateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("12:00") }
    var maxMarks by remember { mutableStateOf("100") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val quickSubjects = remember(uiState.subjects, uiState.activeSchool.id) {
        uiState.subjects.map { it.name }.distinct().take(12)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "परीक्षा पेपर जोड़ें", "Add Exam Paper")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    classNames.forEach { cls ->
                        FilterChip(
                            selected = selectedClass == cls,
                            onClick = { selectedClass = cls },
                            label = { Text("Class $cls") }
                        )
                    }
                }
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it; errorText = null },
                    label = { Text(p10Label(isHindi, "विषय (Subject)", "Subject")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (quickSubjects.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickSubjects.forEach { s ->
                            FilterChip(
                                selected = subjectName.equals(s, ignoreCase = true),
                                onClick = { subjectName = s; errorText = null },
                                label = { Text(s) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it; errorText = null },
                    label = { Text(p10Label(isHindi, "तिथि (YYYY-MM-DD)", "Date (YYYY-MM-DD)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it; errorText = null },
                        label = { Text("Start (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it; errorText = null },
                        label = { Text("End (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = maxMarks,
                    onValueChange = { maxMarks = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Max Marks") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Surface(color = FailRedContainer, shape = RoundedCornerShape(8.dp)) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = FailRed, modifier = Modifier.size(18.dp))
                            Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = FailRed)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedClass, subjectName, dateIso, startTime, endTime, maxMarks.toIntOrNull() ?: 100) {
                        errorText = it
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "सेव करें", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@Composable
private fun ExamRoomDialog(
    existing: ExamRoomRecord?,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int, (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(existing?.roomName.orEmpty()) }
    var cap by remember { mutableStateOf((existing?.capacity ?: 30).toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) p10Label(isHindi, "नया परीक्षा कक्ष", "Add Exam Room") else p10Label(isHindi, "कक्ष संपादित करें", "Edit Exam Room")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text(p10Label(isHindi, "कक्ष का नाम (जैसे Room 101)", "Room Name (e.g. Room 101)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cap,
                    onValueChange = { cap = it.filter { ch -> ch.isDigit() }; errorText = null },
                    label = { Text(p10Label(isHindi, "सीट क्षमता", "Seat Capacity")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(errorText!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, cap.toIntOrNull() ?: 0) { errorText = it } },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "सेव करें", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@Composable
private fun AllocateSeatingDialog(
    exam: ExamScheduleRecord,
    classNames: List<String>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>, SeatingAllocationMode) -> Unit
) {
    var mode by remember { mutableStateOf(SeatingAllocationMode.BY_ROLL_NUMBER) }
    val selected = remember {
        mutableStateListOf<String>().apply {
            val fromExam = exam.entries.map { it.className }.distinct()
            addAll(fromExam.ifEmpty { classNames })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "ऑटो सीटिंग आवंटन", "Auto-Allocate Seating")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(p10Label(isHindi, "बैठने का तरीका चुनें:", "Choose seating mode:"), fontWeight = FontWeight.SemiBold)
                SeatingAllocationMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(if (isHindi) m.hindiLabel else m.englishLabel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                }
                HorizontalDivider()
                Text(p10Label(isHindi, "शामिल कक्षाएँ:", "Include classes:"), fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    classNames.forEach { cls ->
                        val isSel = cls in selected
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                if (isSel) selected.remove(cls) else selected.add(cls)
                            },
                            label = { Text("Class $cls") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected.toList(), mode) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "सीटिंग बनाएँ", "Allocate"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}
