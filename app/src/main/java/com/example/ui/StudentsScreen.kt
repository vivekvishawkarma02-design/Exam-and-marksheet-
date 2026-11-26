package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SchoolClassEntity
import com.example.data.StudentEntity
import com.example.domain.AppModule
import com.example.domain.ReportCalculations
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StudentsScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onOpenCoScholastic: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var deletingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var historyStudent by remember { mutableStateOf<StudentEntity?>(null) }
    val canEditStudents = uiState.canEdit(AppModule.STUDENTS)
    val canViewCoScholastic = uiState.canView(AppModule.CO_SCHOLASTIC)
    val canViewMarksHistory = uiState.isAdmin

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importStudentsFromCsvUri(uri)
        }
    }

    val activeClassName = uiState.effectiveClassName
    val perfMap = remember(
        uiState.activeSchool.id,
        uiState.allStudents,
        uiState.allMarks,
        uiState.filter,
        uiState.subjects,
        activeClassName
    ) {
        uiState.performanceWithRanksForClass(
            schoolId = uiState.activeSchool.id,
            session = uiState.filter.session,
            className = activeClassName,
            section = uiState.filter.section
        )
    }

    val displayedStudents = remember(uiState.filteredStudents, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) uiState.filteredStudents
        else uiState.filteredStudents.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.admNo.contains(q, ignoreCase = true) ||
                it.fatherName.contains(q, ignoreCase = true) ||
                it.rollNo.toString() == q
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Filters & Search Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Students",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${uiState.filteredStudents.size} in Class $activeClassName-${uiState.filter.section}  •  ${uiState.sessionStudents.size} in ${uiState.activeSchool.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (canEditStudents) {
                            OutlinedButton(
                                onClick = { csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("import_csv_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = "Import CSV",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CSV", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    SessionClassSectionBar(
                        uiState = uiState,
                        onSessionSelected = viewModel::selectSession,
                        onClassSelected = viewModel::selectClass,
                        onSectionSelected = viewModel::selectSection
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search name, Adm No, father, or roll no.") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_search_input")
                    )
                }
            }

            if (displayedStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    EmptyStatePanel(
                        icon = Icons.Default.Groups,
                        title = if (searchQuery.isNotEmpty()) "No matching students" else "No students in Class $activeClassName-${uiState.filter.section}",
                        message = if (searchQuery.isNotEmpty())
                            "Try clearing the search text or switching class/section."
                        else if (canEditStudents)
                            "Tap the + button to add a student, or import a class roster from CSV."
                        else
                            "No students found in this class and section (view-only access).",
                        actionLabel = if (searchQuery.isEmpty() && canEditStudents) "Add First Student" else null,
                        onAction = if (searchQuery.isEmpty() && canEditStudents) {
                            {
                                editingStudent = null
                                showAddEditDialog = true
                            }
                        } else null
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedStudents, key = { it.id }) { student ->
                        val perf = perfMap[student.id]
                        StudentCard(
                            student = student,
                            percentText = perf?.takeIf { it.hasAnyAnnualMarks }?.let {
                                "${ReportCalculations.formatPercent(it.overallPercentage)}% (${it.overallGrade})"
                            },
                            resultStatus = perf?.overallResult,
                            canEdit = canEditStudents,
                            canViewCoScholastic = canViewCoScholastic,
                            canViewMarksHistory = canViewMarksHistory,
                            onMarksHistory = { historyStudent = student },
                            onEdit = {
                                editingStudent = student
                                showAddEditDialog = true
                            },
                            onCoScholastic = {
                                viewModel.selectStudent(student.id)
                                onOpenCoScholastic(student.id)
                            },
                            onDelete = { deletingStudent = student }
                        )
                    }
                }
            }
        }

        if (canEditStudents) {
            FloatingActionButton(
                onClick = {
                    editingStudent = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_student_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Student")
            }
        }
    }

    if (showAddEditDialog) {
        StudentFormDialog(
            existing = editingStudent,
            schoolId = uiState.activeSchool.id,
            schoolClasses = uiState.activeSchoolClasses,
            defaultSession = uiState.filter.session,
            defaultClass = activeClassName,
            defaultSection = uiState.filter.section,
            sessions = uiState.settings.sessionsList(),
            viewModel = viewModel,
            onDismiss = { showAddEditDialog = false }
        )
    }

    deletingStudent?.let { st ->
        ConfirmDangerDialog(
            title = "Delete Student?",
            message = "Delete ${st.name} (Adm No. ${st.admNo}) along with all their marks, attendance, and co-scholastic records? This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteStudent(st)
                deletingStudent = null
            },
            onDismiss = { deletingStudent = null }
        )
    }

    historyStudent?.let { st ->
        StudentMarksHistoryDialog(
            title = "${st.name} — Marks History",
            logs = uiState.markEditLogsForStudent(st.id),
            deletedUserUids = uiState.deletedUserUids,
            onDismiss = { historyStudent = null }
        )
    }
}

@Composable
private fun StudentCard(
    student: StudentEntity,
    percentText: String?,
    resultStatus: com.example.domain.ResultStatus?,
    canEdit: Boolean,
    canViewCoScholastic: Boolean,
    canViewMarksHistory: Boolean,
    onMarksHistory: () -> Unit,
    onEdit: () -> Unit,
    onCoScholastic: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_card_${student.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudentPhotoThumbnail(
                    photoBase64 = student.photoBase64,
                    studentName = student.name,
                    size = 48.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Roll ${student.rollNo}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "Adm: ${student.admNo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (student.isSample) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "SAMPLE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = student.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (student.fatherName.isNotBlank()) {
                        Text(
                            text = "S/O or D/O ${student.fatherName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (resultStatus != null) {
                        ResultStatusBadge(status = resultStatus)
                    }
                    if (percentText != null) {
                        Text(
                            text = percentText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (canViewMarksHistory) {
                        IconButton(
                            onClick = onMarksHistory,
                            modifier = Modifier.testTag("student_marks_history_${student.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Marks edit history",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    if (canViewCoScholastic) {
                        IconButton(onClick = onCoScholastic) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = "Co-Scholastic and Attendance",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    if (canEdit) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit student",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete student",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentFormDialog(
    existing: StudentEntity?,
    schoolId: Long,
    schoolClasses: List<SchoolClassEntity>,
    defaultSession: String,
    defaultClass: String,
    defaultSection: String,
    sessions: List<String>,
    viewModel: SchoolViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val availableClassNames = remember(schoolClasses, defaultClass) {
        schoolClasses.map { it.name }.ifEmpty { listOf(defaultClass.ifBlank { "1st" }) }
    }

    var session by remember { mutableStateOf(existing?.session ?: defaultSession) }
    var className by remember { mutableStateOf(existing?.className ?: defaultClass.ifBlank { availableClassNames.first() }) }
    var section by remember { mutableStateOf(existing?.section ?: defaultSection) }
    var rollNoText by remember { mutableStateOf(existing?.rollNo?.toString() ?: "") }
    var admNo by remember { mutableStateOf(existing?.admNo ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var fatherName by remember { mutableStateOf(existing?.fatherName ?: "") }
    var motherName by remember { mutableStateOf(existing?.motherName ?: "") }
    var dob by remember { mutableStateOf(existing?.dob ?: "") }
    var mobile by remember { mutableStateOf(existing?.mobile ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var photoBase64 by remember { mutableStateOf(existing?.photoBase64 ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-suggest next roll number when adding a new student
    LaunchedEffect(existing, schoolId, session, className, section) {
        if (existing == null) {
            val suggested = viewModel.suggestRollNo(schoolId, session, className, section)
            rollNoText = suggested.toString()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    ImageUtils.encodeUriToBase64Under200Kb(context, uri)
                }
                if (encoded != null) {
                    photoBase64 = encoded
                } else {
                    errorMessage = "Could not compress selected image."
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "Add Student" else "Edit Student",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Student Photo Picker row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudentPhotoThumbnail(
                        photoBase64 = photoBase64,
                        studentName = name.ifBlank { "?" },
                        size = 60.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (photoBase64.isEmpty()) "Pick Photo (<200 KB)" else "Change Photo")
                        }
                        if (photoBase64.isNotEmpty()) {
                            TextButton(onClick = { photoBase64 = "" }) {
                                Text("Remove Photo", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactDropdownSelector(
                        label = "Session",
                        selectedValue = session,
                        options = sessions,
                        onSelect = { session = it },
                        modifier = Modifier.weight(1.2f)
                    )
                    CompactDropdownSelector(
                        label = "Class",
                        selectedValue = className,
                        options = availableClassNames,
                        onSelect = { className = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it.uppercase().take(4) },
                        label = { Text("Section*") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rollNoText,
                        onValueChange = { rollNoText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Roll No.*") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = admNo,
                    onValueChange = { admNo = it },
                    label = { Text("Admission No.*") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_adm_input")
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student Name* (English / Hindi)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_name_input")
                )

                OutlinedTextField(
                    value = fatherName,
                    onValueChange = { fatherName = it },
                    label = { Text("Father's Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = motherName,
                    onValueChange = { motherName = it },
                    label = { Text("Mother's Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { dob = it },
                        label = { Text("DOB (DD-MM-YYYY)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val roll = rollNoText.toIntOrNull()
                    when {
                        admNo.isBlank() -> errorMessage = "Admission No. is required."
                        name.isBlank() -> errorMessage = "Student Name is required."
                        roll == null || roll <= 0 -> errorMessage = "Enter a valid positive Roll No."
                        else -> {
                            errorMessage = null
                            val matchedClassId = schoolClasses.firstOrNull {
                                it.name.equals(className, ignoreCase = true)
                            }?.id ?: existing?.classId ?: 0L
                            val entity = StudentEntity(
                                id = existing?.id ?: 0L,
                                schoolId = existing?.schoolId ?: schoolId,
                                classId = matchedClassId,
                                session = session,
                                admNo = admNo.trim(),
                                name = name.trim(),
                                fatherName = fatherName.trim(),
                                motherName = motherName.trim(),
                                dob = dob.trim(),
                                mobile = mobile.trim(),
                                address = address.trim(),
                                photoBase64 = photoBase64,
                                className = className,
                                section = section.trim().uppercase().ifEmpty { "A" },
                                rollNo = roll,
                                isSample = false
                            )
                            viewModel.saveStudent(entity) { ok, err ->
                                if (ok) onDismiss() else errorMessage = err
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("save_student_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
