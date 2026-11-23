package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.AppModule
import com.example.domain.ExamType
import com.example.domain.PendingMarksEngine
import com.example.domain.PendingMarksItem
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer

/**
 * Phase 7 Marks Pending List screen.
 * - Shows incomplete class-section-subject marks per exam (UT1, UT2, Half-Yearly, Annual).
 * - Filters by school, class, exam, and teacher.
 * - Teachers see ONLY their own pending items; Admins/non-restricted members see all and can tap
 *   "Remind" to share a ready WhatsApp/SMS message.
 */
@Composable
fun PendingMarksScreen(
    uiState: SchoolUiState,
    onBack: () -> Unit,
    onOpenMarksForItem: (PendingMarksItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val isTeacherRestricted = uiState.isTeacherRestricted
    val canEditMarks = uiState.canEdit(AppModule.MARKS)

    var selectedSchoolDocId by remember { mutableStateOf("") } // "" = All Schools
    var selectedClassFilter by remember { mutableStateOf("") } // "" = All Classes
    var selectedExamFilter by remember { mutableStateOf<ExamType?>(null) } // null = All Exams
    var selectedTeacherId by remember { mutableStateOf("") } // "" = All Teachers

    // Base unfiltered list (respecting teacher restriction!) to populate dynamic Class & Teacher filter dropdowns
    val allVisibleItems = remember(
        uiState.filter.session,
        uiState.multiSchoolPendingBundles,
        uiState.allStudents,
        uiState.allMarks,
        uiState.classAssignments,
        uiState.teachers,
        uiState.ownTeacherScope,
        isTeacherRestricted
    ) {
        uiState.computePendingMarksItems(forceOwnTeacherOnly = isTeacherRestricted)
    }

    val filteredItems = remember(
        allVisibleItems,
        selectedSchoolDocId,
        selectedClassFilter,
        selectedExamFilter,
        selectedTeacherId
    ) {
        allVisibleItems.filter { item ->
            (selectedSchoolDocId.isBlank() || item.schoolDocId == selectedSchoolDocId) &&
                (selectedClassFilter.isBlank() || item.className.equals(selectedClassFilter, ignoreCase = true)) &&
                (selectedExamFilter == null || item.exam == selectedExamFilter) &&
                (selectedTeacherId.isBlank() || item.responsibleTeacherId == selectedTeacherId)
        }
    }

    val schoolOptions = remember(uiState.activeSchools, uiState.schoolDocIdByLongId) {
        listOf("" to "All Schools") + uiState.activeSchools.mapNotNull { sch ->
            uiState.schoolDocIdByLongId[sch.id]?.let { docId -> docId to sch.name }
        }
    }
    val classOptions = remember(allVisibleItems, selectedSchoolDocId) {
        val classes = allVisibleItems
            .filter { selectedSchoolDocId.isBlank() || it.schoolDocId == selectedSchoolDocId }
            .map { it.className }
            .distinct()
            .sorted()
        listOf("" to "All Classes") + classes.map { it to "Class $it" }
    }
    val teacherOptions = remember(allVisibleItems, uiState.teachers) {
        val fromItems = allVisibleItems
            .filter { it.responsibleTeacherId.isNotBlank() }
            .map { it.responsibleTeacherId to it.responsibleTeacherName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        listOf("" to "All Teachers") + fromItems
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("pending_marks_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isTeacherRestricted) "My Pending Marks" else "Marks Pending List",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isTeacherRestricted) {
                                "Session ${uiState.filter.session} • Your assigned class-subjects only"
                            } else {
                                "Session ${uiState.filter.session} • ${filteredItems.size} pending item(s)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Exam Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedExamFilter == null,
                        onClick = { selectedExamFilter = null },
                        label = { Text("All Exams (${allVisibleItems.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("pending_exam_ALL")
                    )
                    ExamType.entries.forEach { exam ->
                        val count = allVisibleItems.count { it.exam == exam }
                        FilterChip(
                            selected = selectedExamFilter == exam,
                            onClick = { selectedExamFilter = exam },
                            label = { Text("${exam.label} ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("pending_exam_${exam.name}")
                        )
                    }
                }

                // Dropdown Filters: School, Class, Teacher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (schoolOptions.size > 2) {
                        Box(modifier = Modifier.weight(1f)) {
                            CompactDropdownSelector(
                                label = "School",
                                selectedValue = schoolOptions.firstOrNull { it.first == selectedSchoolDocId }?.second ?: "All Schools",
                                options = schoolOptions.map { it.second },
                                onSelect = { picked ->
                                    selectedSchoolDocId = schoolOptions.firstOrNull { it.second == picked }?.first.orEmpty()
                                },
                                testTag = "pending_school_filter"
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        CompactDropdownSelector(
                            label = "Class",
                            selectedValue = classOptions.firstOrNull { it.first == selectedClassFilter }?.second ?: "All Classes",
                            options = classOptions.map { it.second },
                            onSelect = { picked ->
                                selectedClassFilter = classOptions.firstOrNull { it.second == picked }?.first.orEmpty()
                            },
                            testTag = "pending_class_filter"
                        )
                    }
                    if (!isTeacherRestricted && teacherOptions.size > 1) {
                        Box(modifier = Modifier.weight(1f)) {
                            CompactDropdownSelector(
                                label = "Teacher",
                                selectedValue = teacherOptions.firstOrNull { it.first == selectedTeacherId }?.second ?: "All Teachers",
                                options = teacherOptions.map { it.second },
                                onSelect = { picked ->
                                    selectedTeacherId = teacherOptions.firstOrNull { it.second == picked }?.first.orEmpty()
                                },
                                testTag = "pending_teacher_filter"
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pending_marks_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filteredItems.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PassGreenContainer.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, PassGreen.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "No pending marks",
                                tint = PassGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "No Pending Marks Found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PassGreen
                                )
                                Text(
                                    text = if (isTeacherRestricted) {
                                        "All of your assigned subjects for the selected filter are complete."
                                    } else {
                                        "All marks for the selected school, class, exam, and teacher filter are complete."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                items(
                    items = filteredItems,
                    key = { "${it.schoolDocId}_${it.className}_${it.section}_${it.subjectName}_${it.exam.name}" }
                ) { item ->
                    PendingMarksItemCard(
                        item = item,
                        showSchoolName = uiState.activeSchools.size > 1,
                        showRemindButton = !isTeacherRestricted,
                        canEnterMarks = canEditMarks,
                        onRemind = {
                            val reminderText = PendingMarksEngine.buildReminderWhatsAppText(item)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, reminderText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Send Marks Reminder via WhatsApp / SMS"))
                        },
                        onEnterMarks = { onOpenMarksForItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingMarksItemCard(
    item: PendingMarksItem,
    showSchoolName: Boolean,
    showRemindButton: Boolean,
    canEnterMarks: Boolean,
    onRemind: () -> Unit,
    onEnterMarks: () -> Unit
) {
    val ratio = if (item.totalStudents > 0) {
        (item.enteredStudents.toFloat() / item.totalStudents.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_item_${item.className}_${item.section}_${item.subjectName}_${item.exam.name}")
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Class ${item.className}-${item.section}  •  ${item.subjectName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("Teacher: ${item.responsibleTeacherName}")
                            if (showSchoolName) append("  •  ${item.schoolName}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AbsentAmberContainer
                ) {
                    Text(
                        text = item.exam.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4E2D00),
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
                    text = "${item.enteredStudents} of ${item.totalStudents} students entered",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${item.missingStudents} pending",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AbsentAmber
                )
            }

            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showRemindButton) {
                    OutlinedButton(
                        onClick = onRemind,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("pending_remind_${item.className}_${item.section}_${item.subjectName}_${item.exam.name}")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remind")
                    }
                }
                if (canEnterMarks) {
                    Button(
                        onClick = onEnterMarks,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("pending_enter_${item.className}_${item.section}_${item.subjectName}_${item.exam.name}")
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enter Marks")
                    }
                }
            }
        }
    }
}
