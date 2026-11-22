package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.cloud.Phase4Repository
import com.example.domain.AssignmentWarning
import com.example.domain.AssignmentWarningType
import com.example.domain.ClassSectionAssignment
import com.example.domain.DefaultSchoolTemplate
import com.example.domain.Phase4Validator
import com.example.domain.PreferredTimeOfDay
import com.example.domain.SubjectRule
import com.example.domain.SubjectTeacherAssignment
import com.example.domain.TeacherProfile
import com.example.domain.TeacherWorkloadSummary
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer

// =============================================================================
// TAB 3: CLASS-SUBJECT-TEACHER ASSIGNMENTS & CLASS TEACHER
// =============================================================================
@Composable
fun ClassAssignmentsTab(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    canEdit: Boolean,
    allWarnings: List<AssignmentWarning>,
    workloadsByTeacherId: Map<String, TeacherWorkloadSummary>,
    modifier: Modifier = Modifier
) {
    val activeSchoolDocId = uiState.activeSchoolDocId
    val classes = uiState.activeSchoolClassNames
    var selectedClass by remember(activeSchoolDocId, classes) {
        mutableStateOf(uiState.effectiveClassName.ifBlank { classes.firstOrNull().orEmpty() })
    }
    val sections = remember(uiState.availableSections, uiState.classAssignments, activeSchoolDocId, selectedClass) {
        val fromAssign = uiState.classAssignments
            .filter { it.schoolId == activeSchoolDocId && it.className.equals(selectedClass, ignoreCase = true) }
            .map { it.section }
        (DefaultSchoolTemplate.defaultSections + uiState.availableSections + fromAssign).distinct().sorted()
    }
    var selectedSection by remember(activeSchoolDocId, selectedClass) {
        mutableStateOf(uiState.filter.section.ifBlank { "A" })
    }

    val subjectsForClass = remember(activeSchoolDocId, selectedClass, uiState.subjects, uiState.schoolClasses, uiState.classGroups) {
        if (selectedClass.isBlank()) emptyList() else uiState.subjectsForClass(selectedClass, uiState.activeSchool.id)
    }

    val savedAssignment = remember(activeSchoolDocId, selectedClass, selectedSection, uiState.classAssignments) {
        val docId = Phase4Repository.makeAssignmentDocId(activeSchoolDocId, selectedClass, selectedSection)
        uiState.classAssignments.firstOrNull {
            it.id == docId || (
                it.schoolId == activeSchoolDocId &&
                    it.className.equals(selectedClass, ignoreCase = true) &&
                    it.section.equals(selectedSection, ignoreCase = true)
                )
        }
    }

    var classTeacherId by remember(savedAssignment, activeSchoolDocId, selectedClass, selectedSection) {
        mutableStateOf(savedAssignment?.classTeacherId.orEmpty())
    }
    val draftBySubject = remember(savedAssignment, subjectsForClass, activeSchoolDocId, selectedClass, selectedSection) {
        mutableStateMapOf<String, Pair<String, Int>>().apply {
            val savedMap = savedAssignment?.subjectAssignments?.associateBy { it.subjectName.lowercase() }.orEmpty()
            subjectsForClass.forEach { sub ->
                val found = savedMap[sub.lowercase()]
                put(sub, (found?.teacherId.orEmpty()) to (found?.periodsPerWeek ?: 0))
            }
        }
    }
    var hasUnsavedEdits by remember(savedAssignment, activeSchoolDocId, selectedClass, selectedSection) {
        mutableStateOf(false)
    }

    // Build live draft ClassSectionAssignment so warnings update immediately BEFORE saving!
    val liveDraftAssignment = remember(
        activeSchoolDocId,
        selectedClass,
        selectedSection,
        classTeacherId,
        draftBySubject.toMap(),
        subjectsForClass,
        savedAssignment
    ) {
        ClassSectionAssignment(
            id = savedAssignment?.id ?: Phase4Repository.makeAssignmentDocId(activeSchoolDocId, selectedClass, selectedSection),
            schoolId = activeSchoolDocId,
            className = selectedClass,
            section = selectedSection,
            classTeacherId = classTeacherId,
            subjectAssignments = subjectsForClass.map { sub ->
                val (tid, pds) = draftBySubject[sub] ?: ("" to 0)
                SubjectTeacherAssignment(subjectName = sub, teacherId = tid, periodsPerWeek = pds)
            }
        )
    }

    // Evaluate live warnings including this unsaved class draft!
    val liveWarningsForClass = remember(
        liveDraftAssignment,
        uiState.teachers,
        uiState.periodStructuresBySchool,
        uiState.classAssignments,
        uiState.schools,
        uiState.schoolDocIdByLongId
    ) {
        val mergedAssignments = uiState.classAssignments
            .filterNot { it.id == liveDraftAssignment.id } + liveDraftAssignment
        val allLive = Phase4Validator.evaluateAllWarnings(
            teachers = uiState.teachers,
            periodStructuresBySchool = uiState.periodStructuresBySchool,
            assignments = mergedAssignments,
            schoolNamesById = uiState.schoolNamesByDocId
        )
        val key = "${liveDraftAssignment.schoolId}|${liveDraftAssignment.className}|${liveDraftAssignment.section}"
        val assignedTeacherIds = (liveDraftAssignment.subjectAssignments.mapNotNull { it.teacherId.takeIf { id -> id.isNotBlank() } } +
            listOfNotNull(liveDraftAssignment.classTeacherId.takeIf { it.isNotBlank() })).toSet()
        allLive.filter { w ->
            w.classSectionKey == key || (w.teacherId != null && w.teacherId in assignedTeacherIds)
        }.distinctBy { "${it.type}_${it.message}" }
    }

    val schoolStructure = uiState.periodStructureForSchool(activeSchoolDocId)
    val availablePeriodsPerWeek = schoolStructure.totalTeachingPeriodsPerWeek()
    val totalAssigned = liveDraftAssignment.totalAssignedPeriods

    // Sort teachers: active teachers working in activeSchool first, then other active teachers, then inactive
    val orderedTeachers = remember(uiState.teachers, activeSchoolDocId) {
        uiState.teachers.sortedWith(
            compareByDescending<TeacherProfile> { it.active }
                .thenByDescending { activeSchoolDocId in it.schoolIds }
                .thenBy { it.name.lowercase() }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Class & Section Selector + Class Teacher Card
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
                        text = "${uiState.activeSchool.name} — Class Assignments",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1.4f)) {
                            CompactDropdownSelector(
                                label = "Class",
                                selectedValue = selectedClass.ifBlank { "No classes" },
                                options = classes.ifEmpty { listOf("No classes") },
                                onSelect = { chosen ->
                                    if (chosen in classes) selectedClass = chosen
                                },
                                testTag = "assign_class_picker"
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CompactDropdownSelector(
                                label = "Section",
                                selectedValue = selectedSection,
                                options = sections,
                                onSelect = { selectedSection = it },
                                testTag = "assign_section_picker"
                            )
                        }
                    }

                    // Class Teacher Selector
                    val ctOptions = listOf("Not assigned") + orderedTeachers.map { t ->
                        val suffix = buildList {
                            if (activeSchoolDocId !in t.schoolIds) add("other school")
                            if (!t.active) add("inactive")
                        }.joinToString(", ")
                        if (suffix.isNotEmpty()) "${t.name} ($suffix)" else t.name
                    }
                    val selectedCtLabel = if (classTeacherId.isBlank()) {
                        "Not assigned"
                    } else {
                        val idx = orderedTeachers.indexOfFirst { it.id == classTeacherId }
                        if (idx >= 0) ctOptions[idx + 1] else "Not assigned"
                    }

                    if (canEdit) {
                        CompactDropdownSelector(
                            label = "Class Teacher (${selectedClass.ifBlank { "-" }}-$selectedSection)",
                            selectedValue = selectedCtLabel,
                            options = ctOptions,
                            onSelect = { chosen ->
                                val idx = ctOptions.indexOf(chosen)
                                classTeacherId = if (idx <= 0) "" else orderedTeachers[idx - 1].id
                                hasUnsavedEdits = true
                            },
                            testTag = "assign_class_teacher_picker"
                        )
                    } else {
                        Text(
                            text = "Class Teacher: $selectedCtLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Period budget meter
                    val matched = totalAssigned == availablePeriodsPerWeek
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (matched) PassGreenContainer else AbsentAmberContainer,
                        modifier = Modifier.fillMaxWidth().testTag("assign_period_budget_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (matched) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (matched) PassGreen else AbsentAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Assigned: $totalAssigned / $availablePeriodsPerWeek school period slots/week",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (matched) PassGreen else Color(0xFF5A3200),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Live Non-blocking Warnings Card
        if (liveWarningsForClass.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AbsentAmberContainer),
                    border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("assign_warnings_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AbsentAmber, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Warnings (${liveWarningsForClass.size}) — Non-blocking",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF5A3200),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        liveWarningsForClass.forEach { w ->
                            Text(
                                text = "• ${w.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4E2D00)
                            )
                        }
                    }
                }
            }
        }

        // Subject Assignment Rows
        if (subjectsForClass.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "No subjects configured for this class group yet. Add subjects under Schools & Class Groups.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(subjectsForClass, key = { "sub_assign_$it" }) { subjectName ->
                val (assignedTid, periodsPerWeek) = draftBySubject[subjectName] ?: ("" to 0)
                val assignedTeacher = orderedTeachers.firstOrNull { it.id == assignedTid }
                val subjectMismatch = assignedTeacher != null && periodsPerWeek > 0 &&
                    assignedTeacher.subjects.none { it.equals(subjectName, ignoreCase = true) }

                SubjectAssignmentRowCard(
                    subjectName = subjectName,
                    assignedTeacher = assignedTeacher,
                    periodsPerWeek = periodsPerWeek,
                    orderedTeachers = orderedTeachers,
                    activeSchoolDocId = activeSchoolDocId,
                    workloadsByTeacherId = workloadsByTeacherId,
                    subjectMismatch = subjectMismatch,
                    canEdit = canEdit,
                    onTeacherSelected = { newTid ->
                        val nextPeriods = if (newTid.isNotBlank() && periodsPerWeek == 0) 5 else periodsPerWeek
                        draftBySubject[subjectName] = newTid to nextPeriods
                        hasUnsavedEdits = true
                    },
                    onPeriodsChanged = { newPeriods ->
                        draftBySubject[subjectName] = assignedTid to newPeriods
                        hasUnsavedEdits = true
                    }
                )
            }
        }

        if (canEdit && selectedClass.isNotBlank()) {
            item {
                Button(
                    onClick = {
                        viewModel.saveClassAssignment(liveDraftAssignment) { ok, _ ->
                            if (ok) hasUnsavedEdits = false
                        }
                    },
                    enabled = hasUnsavedEdits || savedAssignment == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("save_class_assignments_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasUnsavedEdits || savedAssignment == null) {
                            "Save Assignments for $selectedClass-$selectedSection"
                        } else {
                            "Assignments Saved ($selectedClass-$selectedSection)"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectAssignmentRowCard(
    subjectName: String,
    assignedTeacher: TeacherProfile?,
    periodsPerWeek: Int,
    orderedTeachers: List<TeacherProfile>,
    activeSchoolDocId: String,
    workloadsByTeacherId: Map<String, TeacherWorkloadSummary>,
    subjectMismatch: Boolean,
    canEdit: Boolean,
    onTeacherSelected: (String) -> Unit,
    onPeriodsChanged: (Int) -> Unit
) {
    // Prioritize teachers who teach this subject in the dropdown display
    val sortedForSubject = remember(orderedTeachers, subjectName) {
        orderedTeachers.sortedWith(
            compareByDescending<TeacherProfile> { it.active }
                .thenByDescending { t -> t.subjects.any { it.equals(subjectName, ignoreCase = true) } }
                .thenByDescending { activeSchoolDocId in it.schoolIds }
                .thenBy { it.name.lowercase() }
        )
    }

    val options = remember(sortedForSubject, workloadsByTeacherId, subjectName) {
        listOf("Unassigned") + sortedForSubject.map { t ->
            val load = workloadsByTeacherId[t.id]
            val loadStr = if (load != null) "${load.totalPeriodsPerWeek}/${load.weeklyPeriodLimit}" else "0/${t.maxPeriodsPerDay * 6}"
            val teaches = t.subjects.any { it.equals(subjectName, ignoreCase = true) }
            val tag = if (teaches) "✓" else "!"
            "${t.name} [$tag • $loadStr]"
        }
    }
    val selectedLabel = if (assignedTeacher == null) {
        "Unassigned"
    } else {
        val idx = sortedForSubject.indexOfFirst { it.id == assignedTeacher.id }
        if (idx >= 0) options[idx + 1] else assignedTeacher.name
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (subjectMismatch) AbsentAmber.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        )
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
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { if (periodsPerWeek > 0) onPeriodsChanged(periodsPerWeek - 1) },
                        enabled = canEdit && periodsPerWeek > 0,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("assign_minus_${subjectName}")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease periods for $subjectName")
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$periodsPerWeek/wk",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = { if (periodsPerWeek < 30) onPeriodsChanged(periodsPerWeek + 1) },
                        enabled = canEdit && periodsPerWeek < 30,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("assign_plus_${subjectName}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase periods for $subjectName")
                    }
                }
            }

            if (canEdit) {
                CompactDropdownSelector(
                    label = "Teacher (✓ = in subject list)",
                    selectedValue = selectedLabel,
                    options = options,
                    onSelect = { chosen ->
                        val idx = options.indexOf(chosen)
                        onTeacherSelected(if (idx <= 0) "" else sortedForSubject[idx - 1].id)
                    },
                    testTag = "assign_teacher_${subjectName}"
                )
            } else {
                Text(
                    text = "Teacher: ${assignedTeacher?.name ?: "Unassigned"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (subjectMismatch) {
                Text(
                    text = "Note: '$subjectName' is not listed in ${assignedTeacher?.name}'s teachable subjects.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7A4500)
                )
            }
        }
    }
}

// =============================================================================
// TAB 4: TEACHER WORKLOAD OVERVIEW
// =============================================================================
@Composable
fun TeacherWorkloadTab(
    uiState: SchoolUiState,
    workloads: List<TeacherWorkloadSummary>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterWarningsOnly by remember { mutableStateOf(false) }

    val filtered = remember(workloads, searchQuery, filterWarningsOnly) {
        val q = searchQuery.trim()
        workloads.filter { w ->
            val warnOk = !filterWarningsOnly || w.warnings.isNotEmpty() || w.isOverLimit
            val queryOk = q.isEmpty() ||
                w.teacher.name.contains(q, ignoreCase = true) ||
                w.assignments.any {
                    it.subjectName.contains(q, ignoreCase = true) ||
                        it.schoolName.contains(q, ignoreCase = true) ||
                        it.className.contains(q, ignoreCase = true)
                }
            warnOk && queryOk
        }.sortedWith(
            compareByDescending<TeacherWorkloadSummary> { it.warnings.isNotEmpty() }
                .thenByDescending { it.totalPeriodsPerWeek }
                .thenBy { it.teacher.name.lowercase() }
        )
    }

    val totalWarnings = remember(workloads) { workloads.sumOf { it.warnings.size } }
    val overLimitCount = remember(workloads) { workloads.count { it.isOverLimit } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
                        text = "Teacher Weekly Workload (All Schools)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${workloads.size} teacher(s) • $overLimitCount over limit • $totalWarnings teacher warning(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search teacher, school, class, or subject") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workload_search_input")
                    )
                    FilterChip(
                        selected = filterWarningsOnly,
                        onClick = { filterWarningsOnly = !filterWarningsOnly },
                        label = { Text("Show only teachers with warnings") },
                        modifier = Modifier.testTag("workload_filter_warnings")
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = if (workloads.isEmpty()) "Add teachers and assign subjects to see weekly workloads here." else "No teachers match the current filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(filtered, key = { "wl_${it.teacher.id}" }) { summary ->
                TeacherWorkloadCard(summary = summary, uiState = uiState)
            }
        }
    }
}

@Composable
private fun TeacherWorkloadCard(
    summary: TeacherWorkloadSummary,
    uiState: SchoolUiState
) {
    val ratio = if (summary.weeklyPeriodLimit > 0) {
        (summary.totalPeriodsPerWeek.toFloat() / summary.weeklyPeriodLimit.toFloat()).coerceIn(0f, 1f)
    } else if (summary.totalPeriodsPerWeek > 0) 1f else 0f

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            when {
                summary.isOverLimit -> FailRed.copy(alpha = 0.55f)
                summary.warnings.isNotEmpty() -> AbsentAmber.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth().testTag("workload_card_${summary.teacher.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StudentPhotoThumbnail(
                    photoBase64 = summary.teacher.photoBase64,
                    studentName = summary.teacher.name.ifBlank { "T" },
                    size = 42.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.teacher.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val schoolNames = summary.teacher.schoolIds.map { uiState.schoolNameByDocId(it) }.joinToString(", ")
                    Text(
                        text = schoolNames.ifBlank { "No school selected" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (summary.isOverLimit) FailRedContainer else PassGreenContainer
                ) {
                    Text(
                        text = "${summary.totalPeriodsPerWeek} / ${summary.weeklyPeriodLimit} pds/wk",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.isOverLimit) FailRed else PassGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (summary.isOverLimit) FailRed else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = "Limit: ${summary.teacher.maxPeriodsPerDay}/day × ${summary.effectiveWorkingDaysCount} working day(s) • Max ${summary.teacher.maxConsecutivePeriods} consecutive",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (summary.classTeacherOf.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    val ctText = summary.classTeacherOf.joinToString(", ") { "${it.second} (${it.first})" }
                    Text(
                        text = "Class Teacher: $ctText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (summary.warnings.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (summary.isOverLimit) FailRedContainer else AbsentAmberContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        summary.warnings.forEach { w ->
                            Text(
                                text = "⚠ ${w.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (w.type == AssignmentWarningType.TEACHER_OVER_WEEKLY_LIMIT) FailRed else Color(0xFF5A3200)
                            )
                        }
                    }
                }
            }

            if (summary.assignments.isEmpty()) {
                Text(
                    text = "No class-subject periods assigned yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                summary.assignments.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.className}-${item.section} • ${item.subjectName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = item.schoolName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${item.periodsPerWeek} / wk",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// TAB 5: OPTIONAL SUBJECT RULES (Per Subject in Active School)
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectRulesTab(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    canEdit: Boolean,
    modifier: Modifier = Modifier
) {
    val activeSchoolDocId = uiState.activeSchoolDocId
    val schoolRulesByLower = remember(uiState.subjectRules, activeSchoolDocId) {
        uiState.subjectRules
            .filter { it.schoolId == activeSchoolDocId }
            .associateBy { it.subjectName.lowercase() }
    }
    val subjectsList = remember(uiState.subjects, uiState.activeSchool.id, schoolRulesByLower) {
        (uiState.configuredSubjectsForSchool(uiState.activeSchool.id) + schoolRulesByLower.values.map { it.subjectName })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Optional Subject Rules — ${uiState.activeSchool.name}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "These preferences are saved per school now and will guide timetable creation in Phases 5 and 6.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (subjectsList.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "No subjects configured in ${uiState.activeSchool.name} yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(subjectsList, key = { "srule_${activeSchoolDocId}_$it" }) { subjectName ->
                val rule = schoolRulesByLower[subjectName.lowercase()] ?: SubjectRule(
                    id = Phase4Repository.makeSubjectRuleDocId(activeSchoolDocId, subjectName),
                    schoolId = activeSchoolDocId,
                    subjectName = subjectName
                )
                SubjectRuleCard(
                    rule = rule,
                    canEdit = canEdit,
                    onUpdateRule = { updated -> viewModel.saveSubjectRule(updated) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectRuleCard(
    rule: SubjectRule,
    canEdit: Boolean,
    onUpdateRule: (SubjectRule) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("subject_rule_card_${rule.subjectName}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = rule.subjectName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Preferred Time of Day",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PreferredTimeOfDay.entries.forEach { pref ->
                    FilterChip(
                        selected = rule.preferredTimeOfDay == pref,
                        onClick = {
                            if (canEdit && rule.preferredTimeOfDay != pref) {
                                onUpdateRule(rule.copy(preferredTimeOfDay = pref))
                            }
                        },
                        label = { Text(pref.label) },
                        enabled = canEdit || rule.preferredTimeOfDay == pref
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Can be a double period", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = rule.allowDoublePeriod,
                    enabled = canEdit,
                    onCheckedChange = { onUpdateRule(rule.copy(allowDoublePeriod = it)) },
                    modifier = Modifier.testTag("rule_double_${rule.subjectName}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Maximum once per day", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = rule.maxOncePerDay,
                    enabled = canEdit,
                    onCheckedChange = { onUpdateRule(rule.copy(maxOncePerDay = it)) },
                    modifier = Modifier.testTag("rule_once_day_${rule.subjectName}")
                )
            }
        }
    }
}
