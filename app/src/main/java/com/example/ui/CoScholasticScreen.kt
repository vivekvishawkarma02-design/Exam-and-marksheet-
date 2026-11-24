package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.TermInfoEntity
import com.example.domain.AttendanceSummary
import com.example.domain.CoScholasticArea
import com.example.domain.DefaultSchoolTemplate
import com.example.domain.Phase7AccessEvaluator
import com.example.domain.ReportCalculations
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer

@Composable
fun CoScholasticScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedTerm by remember { mutableIntStateOf(1) }
    val students = uiState.filteredStudents
    val student = uiState.selectedStudent
    val isTeacherRestricted = uiState.isTeacherRestricted
    val myClassTeacherSections = remember(uiState.activeSchoolDocId, uiState.ownTeacherScope, uiState.classAssignments, uiState.teachers) {
        Phase7AccessEvaluator.classTeacherSectionsForSchool(uiState.effectiveOwnTeacherScope, uiState.activeSchoolDocId)
    }
    val canEditCoScholastic = if (student != null) {
        uiState.canEditCoScholasticFor(student)
    } else {
        uiState.canEditCoScholasticForSection(uiState.effectiveClassName, uiState.filter.section)
    }

    var daysPresentText by remember { mutableStateOf("") }
    var workingDaysText by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var promotedTo by remember { mutableStateOf("") }
    var reportDate by remember { mutableStateOf("") }
    var attendanceError by remember { mutableStateOf<String?>(null) }
    var confirmOverwriteManual by remember { mutableStateOf(false) }

    val existingInfo = student?.let { uiState.termInfoForStudent(it.id, selectedTerm) }
    val effectiveAtt = student?.let { uiState.effectiveTermAttendanceForStudent(it.id, selectedTerm) }

    LaunchedEffect(student?.id, selectedTerm, existingInfo, effectiveAtt) {
        val initPresent = if (uiState.activeSchoolAutoFillAttendance && effectiveAtt != null) {
            effectiveAtt.daysPresent
        } else {
            existingInfo?.daysPresent ?: 0
        }
        val initWorking = if (uiState.activeSchoolAutoFillAttendance && effectiveAtt != null) {
            effectiveAtt.workingDays
        } else {
            existingInfo?.workingDays ?: 0
        }
        daysPresentText = initPresent.takeIf { it > 0 }?.toString() ?: ""
        workingDaysText = initWorking.takeIf { it > 0 }?.toString() ?: ""
        remarks = existingInfo?.remarks ?: ""
        val suggestedNext = if (selectedTerm == 2 && student != null) {
            DefaultSchoolTemplate.suggestNextClassName(student.className, uiState.activeSchoolClassNames)
        } else ""
        promotedTo = existingInfo?.promotedTo?.ifBlank { suggestedNext } ?: suggestedNext
        reportDate = existingInfo?.reportDate ?: ""
        attendanceError = null
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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("coscholastic_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Co-Scholastic & Attendance",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Accepts A1–E & A–C • Art & Discipline flow to Half-Yearly slip",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SessionClassSectionBar(
                    uiState = uiState,
                    onSessionSelected = viewModel::selectSession,
                    onClassSelected = viewModel::selectClass,
                    onSectionSelected = viewModel::selectSection
                )

                val stOptions = students.map { "Roll ${it.rollNo} • ${it.name}" }
                val currentLabel = student?.let { "Roll ${it.rollNo} • ${it.name}" } ?: "No students"
                CompactDropdownSelector(
                    label = "Student",
                    selectedValue = currentLabel,
                    options = stOptions.ifEmpty { listOf("No students") },
                    onSelect = { chosen ->
                        val idx = stOptions.indexOf(chosen)
                        if (idx in students.indices) viewModel.selectStudent(students[idx].id)
                    }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Term-I (Half-Yearly)", 2 to "Term-II (Annual)").forEach { (termNo, label) ->
                        FilterChip(
                            selected = selectedTerm == termNo,
                            onClick = { selectedTerm = termNo },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("term_chip_$termNo")
                        )
                    }
                }

                if (isTeacherRestricted) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (canEditCoScholastic) PassGreenContainer else AbsentAmberContainer,
                        border = BorderStroke(
                            1.dp,
                            if (canEditCoScholastic) PassGreen.copy(alpha = 0.4f) else AbsentAmber.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("coscholastic_teacher_scope_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (canEditCoScholastic) {
                                    "Class Teacher access: You can edit Co-Scholastic grades, attendance, remarks, and promotion for Class ${uiState.effectiveClassName}-${uiState.filter.section}."
                                } else {
                                    "View-only: Only the assigned Class Teacher of Class ${uiState.effectiveClassName}-${uiState.filter.section} (or Admin) can edit co-scholastic grades, attendance, remarks, and promoted-to."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (canEditCoScholastic) PassGreen else MaterialTheme.colorScheme.onSurface
                            )
                            if (myClassTeacherSections.isNotEmpty() && !canEditCoScholastic) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    myClassTeacherSections.forEach { (cls, sec) ->
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                viewModel.selectClass(cls)
                                                viewModel.selectSection(sec)
                                            },
                                            label = { Text("Jump to my section: $cls-$sec") },
                                            modifier = Modifier.testTag("coscholastic_jump_ct_${cls}_$sec")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (student == null) {
            Box(modifier = Modifier.padding(16.dp)) {
                EmptyStatePanel(
                    icon = Icons.Default.FactCheck,
                    title = "No student selected",
                    message = "Add students in this class and section first."
                )
            }
        } else {
            val coMap = uiState.coScholasticMapForStudent(student.id, selectedTerm)
            val otherTermNo = if (selectedTerm == 1) 2 else 1
            val otherTermEff = uiState.effectiveTermAttendanceForStudent(student.id, otherTermNo)
            val curPresent = daysPresentText.toIntOrNull() ?: 0
            val curWorking = workingDaysText.toIntOrNull() ?: 0
            val liveAttendance = if (selectedTerm == 1) {
                AttendanceSummary(curPresent, curWorking, otherTermEff.daysPresent, otherTermEff.workingDays)
            } else {
                AttendanceSummary(otherTermEff.daysPresent, otherTermEff.workingDays, curPresent, curWorking)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 5 Co-Scholastic Areas accepting A1, A2, B1, B2, C1, C2, D, E, A, B, C
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
                                text = "Co-Scholastic Areas (Term-$selectedTerm)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Tap a grade (A1, A2, B1, B2, C1, C2, D, E, A, B, or C). Tap again to clear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            CoScholasticArea.entries.forEachIndexed { idx, area ->
                                val selectedGrade = coMap[area.code].orEmpty()
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = area.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (selectedGrade.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = selectedGrade,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CoScholasticArea.validGrades.forEach { grade ->
                                            val isSelected = selectedGrade == grade
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .clickable(enabled = canEditCoScholastic) {
                                                        viewModel.saveCoScholasticGrade(
                                                            studentId = student.id,
                                                            termNo = selectedTerm,
                                                            areaCode = area.code,
                                                            grade = if (isSelected) "" else grade
                                                        )
                                                    }
                                                    .testTag("co_${area.code}_$grade")
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(width = 44.dp, height = 40.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = grade,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (idx < CoScholasticArea.entries.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }

                // Attendance & Remarks Form
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Attendance & Term-$selectedTerm Details",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (uiState.activeSchoolAutoFillAttendance && effectiveAtt != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (effectiveAtt.isManual) AbsentAmberContainer else PassGreenContainer,
                                        modifier = Modifier.testTag("coscholastic_attendance_source_badge")
                                    ) {
                                        Text(
                                            text = if (effectiveAtt.isManual) "Manual" else "Auto from Daily",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (effectiveAtt.isManual) AbsentAmber else PassGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            if (canEditCoScholastic && uiState.canAccessStudentAttendance) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val dailyComp = uiState.computeStudentTermAttendance(student.id, selectedTerm)
                                    Text(
                                        text = "Daily attendance total: ${dailyComp.formattedRawDaysPresent()} / ${dailyComp.totalWorkingDays}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            if (effectiveAtt?.isManual == true) {
                                                confirmOverwriteManual = true
                                            } else {
                                                viewModel.recalculateStudentTermAttendance(student.id, selectedTerm)
                                            }
                                        },
                                        modifier = Modifier.testTag("coscholastic_recalc_attendance_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Recalculate")
                                    }
                                }
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
                                    AttendancePill("Term-I", liveAttendance.formatCompactTerm1())
                                    AttendancePill("Term-II", liveAttendance.formatCompactTerm2())
                                    AttendancePill("Grand Total", liveAttendance.formatCompactGrandTotal(), highlight = true)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = daysPresentText,
                                    onValueChange = {
                                        daysPresentText = it.filter { ch -> ch.isDigit() }
                                        attendanceError = ReportCalculations.validateAttendance(
                                            daysPresentText.toIntOrNull() ?: 0,
                                            workingDaysText.toIntOrNull() ?: 0
                                        )
                                    },
                                    readOnly = !canEditCoScholastic,
                                    label = { Text("Days Present") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = attendanceError != null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("days_present_input")
                                )
                                OutlinedTextField(
                                    value = workingDaysText,
                                    onValueChange = {
                                        workingDaysText = it.filter { ch -> ch.isDigit() }
                                        attendanceError = ReportCalculations.validateAttendance(
                                            daysPresentText.toIntOrNull() ?: 0,
                                            workingDaysText.toIntOrNull() ?: 0
                                        )
                                    },
                                    readOnly = !canEditCoScholastic,
                                    label = { Text("Working Days") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = attendanceError != null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("working_days_input")
                                )
                            }

                            if (attendanceError != null) {
                                Text(
                                    text = attendanceError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FailRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            OutlinedTextField(
                                value = remarks,
                                onValueChange = { remarks = it },
                                readOnly = !canEditCoScholastic,
                                label = { Text("Class Teacher's Remarks") },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = promotedTo,
                                    onValueChange = { promotedTo = it },
                                    readOnly = !canEditCoScholastic,
                                    label = { Text("Promoted to Class") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                if (canEditCoScholastic) {
                                    TextButton(
                                        onClick = {
                                            promotedTo = DefaultSchoolTemplate.suggestNextClassName(
                                                student.className,
                                                uiState.activeSchoolClassNames
                                            )
                                        }
                                    ) {
                                        Text("Suggest")
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = reportDate,
                                onValueChange = { reportDate = it },
                                readOnly = !canEditCoScholastic,
                                label = { Text("Date (dd/mm/yy, e.g. 25/03/27)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (canEditCoScholastic) {
                                Button(
                                    onClick = {
                                        val p = daysPresentText.toIntOrNull() ?: 0
                                        val w = workingDaysText.toIntOrNull() ?: 0
                                        val err = ReportCalculations.validateAttendance(p, w)
                                        if (err != null) {
                                            attendanceError = err
                                        } else {
                                            viewModel.saveTermInfo(
                                                TermInfoEntity(
                                                    studentId = student.id,
                                                    termNo = selectedTerm,
                                                    daysPresent = p,
                                                    workingDays = w,
                                                    remarks = remarks.trim(),
                                                    promotedTo = promotedTo.trim(),
                                                    reportDate = reportDate.trim()
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("save_term_info_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Term-$selectedTerm Details")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmOverwriteManual && student != null) {
        AlertDialog(
            onDismissRequest = { confirmOverwriteManual = false },
            title = { Text("Overwrite Manual Attendance?") },
            text = {
                Text("${student.name} has a manually entered Term-$selectedTerm attendance value. Replace it with the calculated daily attendance total?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmOverwriteManual = false
                        viewModel.recalculateStudentTermAttendance(student.id, selectedTerm)
                    }
                ) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOverwriteManual = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AttendancePill(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
