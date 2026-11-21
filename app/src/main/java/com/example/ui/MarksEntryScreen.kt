package com.example.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudentEntity
import com.example.domain.ExamType
import com.example.domain.MarkComponent
import com.example.domain.MarkValidationResult
import com.example.domain.ReportCalculations
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer

private enum class MarksEntryMode(val label: String) {
    CLASS_GRID("Class Grid"),
    SINGLE_STUDENT("One Student")
}

@Composable
fun MarksEntryScreen(
    uiState: CpiUiState,
    viewModel: CpiViewModel,
    onNavigateToStudents: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedExam by remember { mutableStateOf(ExamType.HALF_YEARLY) }
    var entryMode by remember { mutableStateOf(MarksEntryMode.CLASS_GRID) }
    var showMarksCsvDialog by remember { mutableStateOf(false) }
    val subjects = remember(uiState.filter.className, uiState.subjects) {
        uiState.subjectsForClass(uiState.filter.className)
    }
    var selectedSubject by remember { mutableStateOf(subjects.firstOrNull().orEmpty()) }

    LaunchedEffect(subjects) {
        if (selectedSubject !in subjects) {
            selectedSubject = subjects.firstOrNull().orEmpty()
        }
    }

    val activeComponents = remember(selectedExam) {
        MarkComponent.componentsForExam(selectedExam)
    }
    val students = uiState.filteredStudents
    val selectedStudent = uiState.selectedStudent

    val cellErrors = remember { mutableStateMapOf<String, String>() }
    val invalidDrafts = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(uiState.filter, selectedExam, selectedSubject, entryMode) {
        cellErrors.clear()
        invalidDrafts.clear()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enter Marks",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${uiState.activeSchoolIdentity.schoolName}  •  IA (10) + UT (30) + Exam (60)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (uiState.lastMarkUndo != null) {
                            TextButton(
                                onClick = viewModel::undoLastMarkEdit,
                                modifier = Modifier.testTag("undo_mark_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo last mark edit",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Undo", fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = { showMarksCsvDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("import_marks_csv_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Import Marks CSV",
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

                // Exam Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExamType.entries.forEach { exam ->
                        FilterChip(
                            selected = selectedExam == exam,
                            onClick = { selectedExam = exam },
                            label = { Text(exam.label, style = MaterialTheme.typography.labelLarge) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("exam_chip_${exam.name}")
                        )
                    }
                }

                // Mode Switch + Subject or Student Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MarksEntryMode.entries.forEach { mode ->
                            val selected = entryMode == mode
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable { entryMode = mode }
                                    .testTag("mode_${mode.name}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (entryMode == MarksEntryMode.CLASS_GRID) {
                        CompactDropdownSelector(
                            label = "Subject (${subjects.size})",
                            selectedValue = selectedSubject.ifEmpty { "-" },
                            options = subjects,
                            onSelect = { selectedSubject = it },
                            modifier = Modifier.weight(1f),
                            testTag = "marks_subject_picker"
                        )
                    } else {
                        val stOptions = students.map { "R${it.rollNo} • ${it.name}" }
                        val currentLabel = selectedStudent?.let { "R${it.rollNo} • ${it.name}" } ?: "No students"
                        CompactDropdownSelector(
                            label = "Student",
                            selectedValue = currentLabel,
                            options = stOptions.ifEmpty { listOf("No students") },
                            onSelect = { chosen ->
                                val idx = stOptions.indexOf(chosen)
                                if (idx in students.indices) viewModel.selectStudent(students[idx].id)
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "marks_student_picker"
                        )
                    }
                }
            }
        }

        if (students.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                EmptyStatePanel(
                    icon = Icons.Default.EditNote,
                    title = "No students to enter marks for",
                    message = "Add students in Class ${uiState.filter.className}-${uiState.filter.section} first.",
                    actionLabel = "Go to Students",
                    onAction = onNavigateToStudents
                )
            }
        } else if (entryMode == MarksEntryMode.CLASS_GRID) {
            ClassMarksGrid(
                students = students,
                subjectName = selectedSubject,
                components = activeComponents,
                uiState = uiState,
                cellErrors = cellErrors,
                invalidDrafts = invalidDrafts,
                onSaveCell = { student, comp, text ->
                    val cellKey = "${student.id}_${selectedSubject}_${comp.key}"
                    val res = viewModel.saveMark(student, selectedSubject, comp, text)
                    when (res) {
                        is MarkValidationResult.Valid -> {
                            cellErrors.remove(cellKey)
                            invalidDrafts.remove(cellKey)
                        }
                        is MarkValidationResult.Invalid -> {
                            cellErrors[cellKey] = res.message
                            invalidDrafts[cellKey] = text
                        }
                    }
                }
            )
        } else if (selectedStudent != null) {
            SingleStudentMarksList(
                student = selectedStudent,
                subjects = subjects,
                components = activeComponents,
                uiState = uiState,
                cellErrors = cellErrors,
                invalidDrafts = invalidDrafts,
                onSaveCell = { subjectName, comp, text ->
                    val cellKey = "${selectedStudent.id}_${subjectName}_${comp.key}"
                    val res = viewModel.saveMark(selectedStudent, subjectName, comp, text)
                    when (res) {
                        is MarkValidationResult.Valid -> {
                            cellErrors.remove(cellKey)
                            invalidDrafts.remove(cellKey)
                        }
                        is MarkValidationResult.Invalid -> {
                            cellErrors[cellKey] = res.message
                            invalidDrafts[cellKey] = text
                        }
                    }
                }
            )
        }
    }

    MarksCsvImportController(
        uiState = uiState,
        viewModel = viewModel,
        showSetupDialog = showMarksCsvDialog,
        onDismissSetupDialog = { showMarksCsvDialog = false }
    )
}

@Composable
private fun ClassMarksGrid(
    students: List<StudentEntity>,
    subjectName: String,
    components: List<MarkComponent>,
    uiState: CpiUiState,
    cellErrors: Map<String, String>,
    invalidDrafts: Map<String, String>,
    onSaveCell: (StudentEntity, MarkComponent, String) -> Unit
) {
    val maxSum = components.sumOf { it.maxMarks }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$subjectName  •  ${components.joinToString(" + ") { "${it.shortLabel} (${it.maxMarks})" }}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Max $maxSum",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        itemsIndexed(students, key = { _, st -> st.id }) { _, student ->
            val stMarks = uiState.marksMapForStudent(student.id)
            val rowTotal = components.sumOf { comp ->
                ReportCalculations.numericMarkValue(stMarks[subjectName to comp.key])
            }
            val rowAny = components.any { comp ->
                !stMarks[subjectName to comp.key].isNullOrBlank()
            }
            val rowPct = ReportCalculations.calculatePercentage(rowTotal, maxSum.toDouble())
            val rowGrade = if (rowAny) ReportCalculations.calculateGrade(rowPct).grade else "-"

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "#${student.rollNo}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = student.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (rowAny) "${ReportCalculations.formatNumber(rowTotal)}/$maxSum ($rowGrade)" else "—/$maxSum",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        components.forEach { comp ->
                            val cellKey = "${student.id}_${subjectName}_${comp.key}"
                            val stored = stMarks[subjectName to comp.key].orEmpty()
                            MarkInputCell(
                                label = "${comp.shortLabel} (${comp.maxMarks})",
                                storedValue = stored,
                                invalidDraft = invalidDrafts[cellKey],
                                errorText = cellErrors[cellKey],
                                onCommit = { text -> onSaveCell(student, comp, text) },
                                modifier = Modifier.weight(1f),
                                testTag = "mark_cell_${student.id}_${comp.key}"
                            )
                        }
                    }

                    val rowErrors = components.mapNotNull { comp ->
                        val k = "${student.id}_${subjectName}_${comp.key}"
                        cellErrors[k]?.let { "${comp.shortLabel}: $it" }
                    }
                    if (rowErrors.isNotEmpty()) {
                        Text(
                            text = rowErrors.joinToString("  •  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = FailRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleStudentMarksList(
    student: StudentEntity,
    subjects: List<String>,
    components: List<MarkComponent>,
    uiState: CpiUiState,
    cellErrors: Map<String, String>,
    invalidDrafts: Map<String, String>,
    onSaveCell: (String, MarkComponent, String) -> Unit
) {
    val stMarks = uiState.marksMapForStudent(student.id)
    val maxPerSub = components.sumOf { it.maxMarks }
    val grandMax = maxPerSub * subjects.size
    val grandObt = subjects.sumOf { sub ->
        components.sumOf { comp -> ReportCalculations.numericMarkValue(stMarks[sub to comp.key]) }
    }
    val grandPct = ReportCalculations.calculatePercentage(grandObt, grandMax.toDouble())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${student.name} (Roll ${student.rollNo})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${ReportCalculations.formatNumber(grandObt)}/$grandMax (${ReportCalculations.formatPercent(grandPct)}%)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        itemsIndexed(subjects, key = { _, sub -> sub }) { _, subject ->
            val subTotal = components.sumOf { comp ->
                ReportCalculations.numericMarkValue(stMarks[subject to comp.key])
            }
            val subAny = components.any { !stMarks[subject to it.key].isNullOrBlank() }
            val subGrade = if (subAny) {
                ReportCalculations.calculateGrade(
                    ReportCalculations.calculatePercentage(subTotal, maxPerSub.toDouble())
                ).grade
            } else "-"

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = subject,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (subAny) "${ReportCalculations.formatNumber(subTotal)}/$maxPerSub ($subGrade)" else "—/$maxPerSub",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        components.forEach { comp ->
                            val cellKey = "${student.id}_${subject}_${comp.key}"
                            val stored = stMarks[subject to comp.key].orEmpty()
                            MarkInputCell(
                                label = "${comp.shortLabel} (${comp.maxMarks})",
                                storedValue = stored,
                                invalidDraft = invalidDrafts[cellKey],
                                errorText = cellErrors[cellKey],
                                onCommit = { text -> onSaveCell(subject, comp, text) },
                                modifier = Modifier.weight(1f),
                                testTag = "solo_cell_${subject}_${comp.key}"
                            )
                        }
                    }

                    val rowErrors = components.mapNotNull { comp ->
                        val k = "${student.id}_${subject}_${comp.key}"
                        cellErrors[k]?.let { "${comp.shortLabel}: $it" }
                    }
                    if (rowErrors.isNotEmpty()) {
                        Text(
                            text = rowErrors.joinToString("  •  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = FailRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkInputCell(
    label: String,
    storedValue: String,
    invalidDraft: String?,
    errorText: String?,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val focusManager = LocalFocusManager.current
    var text by remember(storedValue, invalidDraft) { mutableStateOf(invalidDraft ?: storedValue) }
    var isFocused by remember { mutableStateOf(false) }

    val isAbsent = text.trim().equals("AB", ignoreCase = true)
    val hasError = errorText != null

    val borderColor = when {
        hasError -> FailRed
        isAbsent -> AbsentAmber
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val bgColor = when {
        hasError -> FailRedContainer
        isAbsent -> AbsentAmberContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = bgColor,
            border = BorderStroke(if (isFocused || hasError) 1.5.dp else 1.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { updated ->
                        text = updated.take(6)
                        onCommit(text)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = when {
                            hasError -> FailRed
                            isAbsent -> AbsentAmber
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            onCommit(text)
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                        .onFocusChanged { state ->
                            if (isFocused && !state.isFocused) {
                                onCommit(text)
                            }
                            isFocused = state.isFocused
                        }
                )
                if (text.isEmpty()) {
                    Text(
                        text = "—",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isAbsent) AbsentAmber else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val nextVal = if (isAbsent) "" else "AB"
                    text = nextVal
                    onCommit(nextVal)
                }
                .testTag("${testTag}_ab")
        ) {
            Text(
                text = "AB",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (isAbsent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
