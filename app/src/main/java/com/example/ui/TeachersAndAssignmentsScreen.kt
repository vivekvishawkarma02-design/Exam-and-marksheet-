package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.cloud.CloudMember
import com.example.domain.AppModule
import com.example.domain.DayOfWeekCode
import com.example.domain.MemberStatus
import com.example.domain.OrgRole
import com.example.domain.Phase4Validator
import com.example.domain.TeacherProfile
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import com.example.util.ImageUtils

@Composable
fun TeachersAndAssignmentsScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val canEdit = uiState.canEdit(AppModule.TEACHERS)
    val allWarnings = remember(
        uiState.teachers,
        uiState.periodStructuresBySchool,
        uiState.classAssignments,
        uiState.schools,
        uiState.schoolDocIdByLongId
    ) {
        uiState.phase4Warnings()
    }
    val workloads = remember(
        uiState.teachers,
        uiState.periodStructuresBySchool,
        uiState.classAssignments,
        uiState.schools,
        uiState.schoolDocIdByLongId,
        allWarnings
    ) {
        uiState.teacherWorkloads(allWarnings)
    }

    val tabs = remember(uiState.teachers.size, allWarnings.size) {
        listOf(
            "Teachers (${uiState.teachers.size})",
            "Period Structure",
            if (allWarnings.isNotEmpty()) "Assignments (${allWarnings.size})" else "Assignments",
            "Workload",
            "Subject Rules"
        )
    }
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("teachers_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Teachers & Assignments",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.teachers.count { it.active }} active teacher(s) • ${uiState.activeSchool.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!canEdit) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "View only",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "View only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 12.dp
                ) {
                    tabs.forEachIndexed { idx, label ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = { Text(label, maxLines = 1) },
                            modifier = Modifier.testTag("phase4_tab_$idx")
                        )
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> TeachersDirectoryTab(
                uiState = uiState,
                viewModel = viewModel,
                canEdit = canEdit,
                workloadsByTeacherId = workloads.associateBy { it.teacher.id },
                modifier = Modifier.weight(1f)
            )
            1 -> PeriodStructureTab(
                uiState = uiState,
                viewModel = viewModel,
                canEdit = canEdit,
                modifier = Modifier.weight(1f)
            )
            2 -> ClassAssignmentsTab(
                uiState = uiState,
                viewModel = viewModel,
                canEdit = canEdit,
                allWarnings = allWarnings,
                workloadsByTeacherId = workloads.associateBy { it.teacher.id },
                modifier = Modifier.weight(1f)
            )
            3 -> TeacherWorkloadTab(
                uiState = uiState,
                workloads = workloads,
                modifier = Modifier.weight(1f)
            )
            4 -> SubjectRulesTab(
                uiState = uiState,
                viewModel = viewModel,
                canEdit = canEdit,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeachersDirectoryTab(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    canEdit: Boolean,
    workloadsByTeacherId: Map<String, com.example.domain.TeacherWorkloadSummary>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSchoolFilter by remember { mutableStateOf("ALL") } // "ALL" or schoolDocId
    var selectedSubjectFilter by remember { mutableStateOf("ALL") } // "ALL" or subjectName
    var showInactive by remember { mutableStateOf(true) }

    var editingTeacher by remember { mutableStateOf<TeacherProfile?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    val schoolItems = remember(uiState.schools, uiState.schoolDocIdByLongId) {
        uiState.schools.mapNotNull { sch ->
            val docId = uiState.schoolDocIdByLongId[sch.id] ?: return@mapNotNull null
            docId to sch.name
        }
    }

    val allSubjectsForFilter = remember(uiState.allConfiguredSubjectsAcrossOrg, uiState.teachers) {
        (uiState.allConfiguredSubjectsAcrossOrg + uiState.teachers.flatMap { it.subjects })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    // Approved TEACHER members who completed their Phase 2 profile and aren't linked to any TeacherProfile yet
    val linkedMemberUids = remember(uiState.teachers) {
        uiState.teachers.mapNotNull { it.linkedMemberUid.takeIf { uid -> uid.isNotBlank() } }.toSet()
    }
    val unsyncedTeacherMembers = remember(uiState.orgMembers, linkedMemberUids) {
        uiState.orgMembers.filter {
            it.memberStatus == MemberStatus.ACTIVE &&
                it.orgRole == OrgRole.TEACHER &&
                it.teacherProfileCompleted &&
                it.uid !in linkedMemberUids
        }
    }

    val filteredTeachers = remember(
        uiState.teachers,
        searchQuery,
        selectedSchoolFilter,
        selectedSubjectFilter,
        showInactive
    ) {
        val q = searchQuery.trim()
        uiState.teachers.filter { t ->
            val activeOk = showInactive || t.active
            val schoolOk = selectedSchoolFilter == "ALL" || selectedSchoolFilter in t.schoolIds
            val subjectOk = selectedSubjectFilter == "ALL" ||
                t.subjects.any { it.equals(selectedSubjectFilter, ignoreCase = true) }
            val queryOk = q.isEmpty() ||
                t.name.contains(q, ignoreCase = true) ||
                t.mobile.contains(q, ignoreCase = true) ||
                t.email.contains(q, ignoreCase = true) ||
                t.subjects.any { it.contains(q, ignoreCase = true) }
            activeOk && schoolOk && subjectOk && queryOk
        }.sortedWith(compareByDescending<TeacherProfile> { it.active }.thenBy { it.name.lowercase() })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Unsynced approved teacher members from Phase 2
        if (canEdit && unsyncedTeacherMembers.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Approved Teacher Profiles Ready (${unsyncedTeacherMembers.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "These approved members completed their teacher profile. Add them to the teachers directory in one tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        unsyncedTeacherMembers.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.name.ifBlank { member.email },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val subText = member.requestedSubjects.joinToString(", ").ifBlank { "No subjects listed" }
                                    Text(
                                        text = subText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = { viewModel.syncTeacherMemberToDirectory(member) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("sync_teacher_member_${member.uid}")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Import")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search & Filter Card
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
                            text = "Teacher Directory",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (canEdit) {
                            Button(
                                onClick = {
                                    isCreatingNew = true
                                    editingTeacher = TeacherProfile(
                                        id = "",
                                        name = "",
                                        schoolIds = listOfNotNull(uiState.activeSchoolDocId.takeIf { it.isNotBlank() }),
                                        maxPeriodsPerDay = Phase4Validator.DEFAULT_MAX_PERIODS_PER_DAY,
                                        maxConsecutivePeriods = Phase4Validator.DEFAULT_MAX_CONSECUTIVE_PERIODS,
                                        active = true
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("add_teacher_no_login_button")
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Teacher")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search name, subject, mobile, email") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("teachers_search_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val schoolOptions = listOf("All Schools") + schoolItems.map { it.second }
                        val selectedSchoolLabel = if (selectedSchoolFilter == "ALL") {
                            "All Schools"
                        } else {
                            schoolItems.firstOrNull { it.first == selectedSchoolFilter }?.second ?: "All Schools"
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CompactDropdownSelector(
                                label = "Filter by School",
                                selectedValue = selectedSchoolLabel,
                                options = schoolOptions,
                                onSelect = { chosen ->
                                    selectedSchoolFilter = if (chosen == "All Schools") {
                                        "ALL"
                                    } else {
                                        schoolItems.firstOrNull { it.second == chosen }?.first ?: "ALL"
                                    }
                                },
                                testTag = "teachers_filter_school"
                            )
                        }
                        val subjectOptions = listOf("All Subjects") + allSubjectsForFilter
                        Box(modifier = Modifier.weight(1f)) {
                            CompactDropdownSelector(
                                label = "Filter by Subject",
                                selectedValue = if (selectedSubjectFilter == "ALL") "All Subjects" else selectedSubjectFilter,
                                options = subjectOptions,
                                onSelect = { chosen ->
                                    selectedSubjectFilter = if (chosen == "All Subjects") "ALL" else chosen
                                },
                                testTag = "teachers_filter_subject"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Showing ${filteredTeachers.size} of ${uiState.teachers.size} teacher(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilterChip(
                            selected = !showInactive,
                            onClick = { showInactive = !showInactive },
                            label = { Text("Active only") },
                            modifier = Modifier.testTag("teachers_filter_active_only")
                        )
                    }
                }
            }
        }

        if (filteredTeachers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (uiState.teachers.isEmpty()) "No teachers added yet" else "No teachers match your filters",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (uiState.teachers.isEmpty()) {
                                "Approved teachers appear here when they complete their profile, or you can add a timetable-only teacher record with no login yet."
                            } else {
                                "Try clearing the search query or changing the school/subject filter."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredTeachers, key = { it.id }) { teacher ->
                val workload = workloadsByTeacherId[teacher.id]
                TeacherDirectoryCard(
                    teacher = teacher,
                    schoolNames = teacher.schoolIds.map { uiState.schoolNameByDocId(it) },
                    assignedPeriods = workload?.totalPeriodsPerWeek ?: 0,
                    weeklyLimit = workload?.weeklyPeriodLimit ?: (teacher.maxPeriodsPerDay * 6),
                    warningCount = workload?.warnings?.size ?: 0,
                    onClick = {
                        isCreatingNew = false
                        editingTeacher = teacher
                    }
                )
            }
        }
    }

    editingTeacher?.let { target ->
        TeacherDetailEditDialog(
            initialTeacher = target,
            isNew = isCreatingNew,
            canEdit = canEdit,
            schoolItems = schoolItems,
            allKnownSubjects = allSubjectsForFilter,
            orgMembers = uiState.orgMembers,
            alreadyLinkedUids = linkedMemberUids - target.linkedMemberUid,
            deleteBlockedReason = if (isCreatingNew) null else Phase4Validator.canDeleteTeacher(target.id, uiState.classAssignments),
            onSave = { updated ->
                viewModel.saveTeacher(updated, isUpdate = !isCreatingNew) { ok, _ ->
                    if (ok) editingTeacher = null
                }
            },
            onToggleActive = { active ->
                viewModel.setTeacherActive(target, active)
                editingTeacher = target.copy(active = active)
            },
            onDelete = {
                viewModel.deleteTeacher(target) { ok, _ ->
                    if (ok) editingTeacher = null
                }
            },
            onDismiss = { editingTeacher = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeacherDirectoryCard(
    teacher: TeacherProfile,
    schoolNames: List<String>,
    assignedPeriods: Int,
    weeklyLimit: Int,
    warningCount: Int,
    onClick: () -> Unit
) {
    val isOver = assignedPeriods > weeklyLimit
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (teacher.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                warningCount > 0 -> AbsentAmber.copy(alpha = 0.55f)
                !teacher.active -> MaterialTheme.colorScheme.outlineVariant
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag("teacher_card_${teacher.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudentPhotoThumbnail(
                    photoBase64 = teacher.photoBase64,
                    studentName = teacher.name.ifBlank { "T" },
                    size = 48.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = teacher.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (teacher.active) PassGreenContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (teacher.active) "Active" else "Inactive",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (teacher.active) PassGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    val contactLine = listOf(teacher.mobile, teacher.email).filter { it.isNotBlank() }.joinToString(" • ")
                    if (contactLine.isNotBlank()) {
                        Text(
                            text = contactLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (teacher.hasLogin) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = if (teacher.hasLogin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (teacher.hasLogin) "Linked member account" else "No login yet (timetable only)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (teacher.hasLogin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOver) FailRedContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$assignedPeriods / $weeklyLimit",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOver) FailRed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "pds/wk",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Schools & Limits summary
            Text(
                text = "Schools: ${schoolNames.joinToString(", ").ifBlank { "None assigned" }}  •  Max ${teacher.maxPeriodsPerDay}/day, ${teacher.maxConsecutivePeriods} consecutive" +
                    if (teacher.weeklyOffDays.isNotEmpty()) "  •  Off: ${teacher.weeklyOffDays.joinToString(",") { it.shortLabel }}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (teacher.subjects.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    teacher.subjects.take(8).forEach { sub ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (teacher.subjects.size > 8) {
                        Text(
                            text = "+${teacher.subjects.size - 8} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (warningCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Warnings",
                        tint = AbsentAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$warningCount active assignment warning(s) — see Workload tab",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7A4500),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeacherDetailEditDialog(
    initialTeacher: TeacherProfile,
    isNew: Boolean,
    canEdit: Boolean,
    schoolItems: List<Pair<String, String>>,
    allKnownSubjects: List<String>,
    orgMembers: List<CloudMember>,
    alreadyLinkedUids: Set<String>,
    deleteBlockedReason: String?,
    onSave: (TeacherProfile) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(initialTeacher) { mutableStateOf(initialTeacher.name) }
    var mobile by remember(initialTeacher) { mutableStateOf(initialTeacher.mobile) }
    var email by remember(initialTeacher) { mutableStateOf(initialTeacher.email) }
    var photoBase64 by remember(initialTeacher) { mutableStateOf(initialTeacher.photoBase64) }
    val selectedSchools = remember(initialTeacher) {
        mutableStateListOf<String>().apply { addAll(initialTeacher.schoolIds) }
    }
    val selectedSubjects = remember(initialTeacher) {
        mutableStateListOf<String>().apply { addAll(initialTeacher.subjects) }
    }
    var customSubjectInput by remember { mutableStateOf("") }
    var maxPerDay by remember(initialTeacher) { mutableIntStateOf(initialTeacher.maxPeriodsPerDay) }
    var maxConsecutive by remember(initialTeacher) { mutableIntStateOf(initialTeacher.maxConsecutivePeriods) }
    val selectedOffDays = remember(initialTeacher) {
        mutableStateListOf<DayOfWeekCode>().apply { addAll(initialTeacher.weeklyOffDays) }
    }
    var active by remember(initialTeacher) { mutableStateOf(initialTeacher.active) }
    var linkedMemberUid by remember(initialTeacher) { mutableStateOf(initialTeacher.linkedMemberUid) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = ImageUtils.encodeUriToBase64Under150Kb(context, uri)
            if (encoded != null) {
                photoBase64 = encoded
            } else {
                errorMsg = "Could not load selected image."
            }
        }
    }

    val linkableMembers = remember(orgMembers, alreadyLinkedUids) {
        orgMembers.filter {
            it.memberStatus == MemberStatus.ACTIVE && it.uid !in alreadyLinkedUids
        }.sortedBy { it.name.lowercase() }
    }

    val allSubjectChips = remember(allKnownSubjects, selectedSubjects.toList()) {
        (allKnownSubjects + selectedSubjects).map { it.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    !canEdit -> "Teacher Details"
                    isNew -> "Add Teacher (No Login)"
                    else -> "Edit Teacher"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Photo row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudentPhotoThumbnail(
                        photoBase64 = photoBase64,
                        studentName = name.ifBlank { "T" },
                        size = 56.dp
                    )
                    if (canEdit) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("teacher_pick_photo_button")
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (photoBase64.isBlank()) "Add Photo" else "Change Photo")
                            }
                            if (photoBase64.isNotBlank()) {
                                TextButton(onClick = { photoBase64 = "" }) {
                                    Text("Remove photo", color = FailRed)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (canEdit) name = it },
                    readOnly = !canEdit,
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("teacher_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { if (canEdit) mobile = it },
                        readOnly = !canEdit,
                        label = { Text("Mobile") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("teacher_mobile_input")
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { if (canEdit) email = it },
                        readOnly = !canEdit,
                        label = { Text("E-mail") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("teacher_email_input")
                    )
                }

                // Schools they work in (one or many)
                Text(
                    text = "Schools They Work In (one or many)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    schoolItems.forEach { (schDocId, schName) ->
                        val checked = schDocId in selectedSchools
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable(enabled = canEdit) {
                                    if (checked) selectedSchools.remove(schDocId) else selectedSchools.add(schDocId)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = if (canEdit) {
                                    { isChecked ->
                                        if (isChecked) {
                                            if (schDocId !in selectedSchools) selectedSchools.add(schDocId)
                                        } else {
                                            selectedSchools.remove(schDocId)
                                        }
                                    }
                                } else null
                            )
                            Text(text = schName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Teachable subjects
                Text(
                    text = "Subjects They Can Teach",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allSubjectChips.forEach { sub ->
                        val chosen = selectedSubjects.any { it.equals(sub, ignoreCase = true) }
                        FilterChip(
                            selected = chosen,
                            onClick = {
                                if (canEdit) {
                                    if (chosen) {
                                        selectedSubjects.removeAll { it.equals(sub, ignoreCase = true) }
                                    } else {
                                        selectedSubjects.add(sub)
                                    }
                                }
                            },
                            label = { Text(sub) },
                            leadingIcon = if (chosen) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                if (canEdit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = customSubjectInput,
                            onValueChange = { customSubjectInput = it },
                            label = { Text("Add custom subject") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val clean = customSubjectInput.trim()
                                if (clean.isNotEmpty() && selectedSubjects.none { it.equals(clean, ignoreCase = true) }) {
                                    selectedSubjects.add(clean)
                                }
                                customSubjectInput = ""
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Add")
                        }
                    }
                }

                // Period limits
                Text(
                    text = "Period Limits",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                StepperRow(
                    label = "Max periods per day",
                    value = maxPerDay,
                    min = 1,
                    max = 16,
                    enabled = canEdit,
                    onValueChange = { maxPerDay = it },
                    testTagPrefix = "teacher_max_day"
                )
                StepperRow(
                    label = "Max consecutive periods",
                    value = maxConsecutive,
                    min = 1,
                    max = 10,
                    enabled = canEdit,
                    onValueChange = { maxConsecutive = it },
                    testTagPrefix = "teacher_max_consec"
                )

                // Weekly off days
                Text(
                    text = "Weekly Off Day(s)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DayOfWeekCode.entries.forEach { day ->
                        val isOff = day in selectedOffDays
                        FilterChip(
                            selected = isOff,
                            onClick = {
                                if (canEdit) {
                                    if (isOff) selectedOffDays.remove(day) else selectedOffDays.add(day)
                                }
                            },
                            label = { Text(day.shortLabel) }
                        )
                    }
                }

                // Link to member account
                if (canEdit && linkableMembers.isNotEmpty()) {
                    val memberOptions = listOf("No linked login (timetable only)") +
                        linkableMembers.map { "${it.name.ifBlank { it.email }} (${it.orgRole.shortLabel})" }
                    val currentMemberLabel = if (linkedMemberUid.isBlank()) {
                        memberOptions.first()
                    } else {
                        val m = linkableMembers.firstOrNull { it.uid == linkedMemberUid }
                        if (m != null) "${m.name.ifBlank { m.email }} (${m.orgRole.shortLabel})" else "Linked UID: $linkedMemberUid"
                    }
                    CompactDropdownSelector(
                        label = "Linked Member Login",
                        selectedValue = currentMemberLabel,
                        options = memberOptions,
                        onSelect = { chosen ->
                            val idx = memberOptions.indexOf(chosen)
                            linkedMemberUid = if (idx <= 0) {
                                ""
                            } else {
                                val m = linkableMembers[idx - 1]
                                if (email.isBlank() && m.email.isNotBlank()) email = m.email
                                if (mobile.isBlank() && m.mobile.isNotBlank()) mobile = m.mobile
                                m.uid
                            }
                        },
                        testTag = "teacher_link_member_picker"
                    )
                }

                // Active / Inactive toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Teacher", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (active) "Available for class-subject assignments" else "Inactive (kept for historical records)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = active,
                        enabled = canEdit,
                        onCheckedChange = { newVal ->
                            active = newVal
                            if (!isNew) onToggleActive(newVal)
                        },
                        modifier = Modifier.testTag("teacher_active_switch")
                    )
                }

                // Delete section (existing teacher only)
                if (canEdit && !isNew) {
                    HorizontalDivider()
                    if (deleteBlockedReason != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AbsentAmberContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = deleteBlockedReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5A3200),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FailRed),
                            border = BorderStroke(1.dp, FailRed.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("teacher_delete_button")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Teacher Record")
                        }
                    }
                }

                errorMsg?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = FailRed
                    )
                }
            }
        },
        confirmButton = {
            if (canEdit) {
                Button(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            errorMsg = "Please enter the teacher's name."
                            return@Button
                        }
                        errorMsg = null
                        onSave(
                            initialTeacher.copy(
                                name = name.trim(),
                                mobile = mobile.trim(),
                                email = email.trim(),
                                photoBase64 = photoBase64,
                                subjects = selectedSubjects.toList(),
                                schoolIds = selectedSchools.toList(),
                                maxPeriodsPerDay = maxPerDay,
                                maxConsecutivePeriods = maxConsecutive,
                                weeklyOffDays = selectedOffDays.sortedBy { it.sortOrder },
                                active = active,
                                linkedMemberUid = linkedMemberUid
                            )
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("teacher_save_button")
                ) {
                    Text(if (isNew) "Add Teacher" else "Save Changes")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (canEdit) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (showDeleteConfirm) {
        ConfirmDangerDialog(
            title = "Delete '${initialTeacher.name}'?",
            message = "This teacher has no active assignments and will be permanently removed from the teachers directory.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
internal fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    testTagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { if (value > min) onValueChange(value - 1) },
                enabled = enabled && value > min,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("${testTagPrefix}_minus")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(
                onClick = { if (value < max) onValueChange(value + 1) },
                enabled = enabled && value < max,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("${testTagPrefix}_plus")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label")
            }
        }
    }
}
