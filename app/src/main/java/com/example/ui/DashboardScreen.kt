package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.StudentEntity
import com.example.domain.AppModule
import com.example.domain.OrgRole
import com.example.domain.PendingMarksItem
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SteelSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class QuickActionSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(
    uiState: SchoolUiState,
    onSessionSelected: (String) -> Unit,
    onClassSelected: (String) -> Unit,
    onSectionSelected: (String) -> Unit,
    onStudentSelected: (Long?) -> Unit,
    onDeleteLegacyMarks: () -> Unit,
    onNavigateToSchools: () -> Unit,
    onNavigateToStudents: () -> Unit,
    onNavigateToMarks: () -> Unit,
    onNavigateToCoScholastic: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onOpenStudentMarks: (StudentEntity) -> Unit,
    onOpenStudentCoScholastic: (StudentEntity) -> Unit,
    onOpenStudentReport: (StudentEntity) -> Unit,
    onNavigateToMembers: () -> Unit = {},
    onNavigateToTeachers: () -> Unit = {},
    onNavigateToTimetable: () -> Unit = {},
    onNavigateToPendingMarks: () -> Unit = {},
    onNavigateToWorkloadReport: () -> Unit = {},
    onNavigateToTeacherAttendance: () -> Unit = {},
    onNavigateToStudentAttendance: (String?, String?) -> Unit = { _, _ -> },
    onTakeStudentAttendanceSection: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onNavigateToStudentRegister: () -> Unit = {},
    onNavigateToFeeManagement: () -> Unit = {},
    onNavigateToCertificates: () -> Unit = {},
    onNavigateToCashBook: () -> Unit = {},
    onNavigateToEnquiry: () -> Unit = {},
    onNavigateToExams: () -> Unit = {},
    onNavigateToStock: () -> Unit = {},
    onNavigateToTransport: () -> Unit = {},
    onOpenPendingMarksItem: (PendingMarksItem) -> Unit = {},
    onOpenCoScholasticSection: (String, String, String) -> Unit = { _, _, _ -> },
    onEditOwnTeacherProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filtered = uiState.filteredStudents
    val selectedStudent = uiState.selectedStudent
    val activeSchool = uiState.activeSchool
    val activeClassName = uiState.effectiveClassName
    var showLegacyConfirm by remember { mutableStateOf(false) }
    var studentSearchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(DashboardStatusFilter.ALL) }
    var onlyActiveClass by remember { mutableStateOf(false) }

    val canViewStudents = uiState.canView(AppModule.STUDENTS)
    val canViewMarks = uiState.canView(AppModule.MARKS)
    val canViewCoScholastic = uiState.canView(AppModule.CO_SCHOLASTIC)
    val canViewReports = uiState.canView(AppModule.REPORTS)
    val canViewTeachers = uiState.canView(AppModule.TEACHERS)
    val canViewTimetable = uiState.canView(AppModule.TIMETABLE)
    val canViewSettings = uiState.canView(AppModule.SETTINGS)
    val canEditSettings = uiState.canEdit(AppModule.SETTINGS)
    val canViewMembers = uiState.canView(AppModule.MEMBERS) || uiState.isAdmin
    val canViewAcademicDirectory = canViewStudents || canViewMarks || canViewReports

    val timetableClashCount = remember(
        canViewTimetable,
        uiState.activeSessionTimetableCells,
        uiState.teachers,
        uiState.effectivePeriodStructuresBySchool,
        uiState.schoolNamesByDocId
    ) {
        if (canViewTimetable) uiState.timetableClashes().size else 0
    }

    val allSessionSummaries = remember(
        uiState.schools,
        uiState.schoolClasses,
        uiState.classGroups,
        uiState.allStudents,
        uiState.allMarks,
        uiState.subjects,
        uiState.filter.schoolId,
        uiState.filter.session,
        uiState.settings
    ) {
        uiState.allSessionStudentSummaries()
    }

    val scopedSummaries = remember(allSessionSummaries, onlyActiveClass, activeClassName, uiState.filter.section) {
        if (!onlyActiveClass) {
            allSessionSummaries
        } else {
            allSessionSummaries.filter {
                it.student.className.equals(activeClassName, ignoreCase = true) &&
                    it.student.section.equals(uiState.filter.section, ignoreCase = true)
            }
        }
    }

    val passCount = remember(scopedSummaries) {
        scopedSummaries.count { it.status == StudentAcademicStatus.PASS }
    }
    val needsAttentionCount = remember(scopedSummaries) {
        scopedSummaries.count { it.status == StudentAcademicStatus.NEEDS_ATTENTION }
    }
    val pendingCount = remember(scopedSummaries) {
        scopedSummaries.count { it.status == StudentAcademicStatus.PENDING }
    }

    val displayedSummaries = remember(scopedSummaries, statusFilter, studentSearchQuery) {
        val q = studentSearchQuery.trim()
        scopedSummaries.filter { item ->
            val statusMatches = when (statusFilter) {
                DashboardStatusFilter.ALL -> true
                DashboardStatusFilter.PASS -> item.status == StudentAcademicStatus.PASS
                DashboardStatusFilter.NEEDS_ATTENTION -> item.status == StudentAcademicStatus.NEEDS_ATTENTION
                DashboardStatusFilter.PENDING -> item.status == StudentAcademicStatus.PENDING
            }
            val queryMatches = q.isEmpty() ||
                item.student.name.contains(q, ignoreCase = true) ||
                item.student.className.contains(q, ignoreCase = true) ||
                "${item.student.className}-${item.student.section}".contains(q, ignoreCase = true) ||
                item.student.admNo.contains(q, ignoreCase = true) ||
                item.student.rollNo.toString() == q ||
                item.schoolName.contains(q, ignoreCase = true)
            statusMatches && queryMatches
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    val quickActions = buildList {
        if (canViewMarks) {
            add(
                QuickActionSpec(
                    title = if (uiState.canEdit(AppModule.MARKS)) "Enter Marks" else "View Marks",
                    subtitle = "IA 10, UT 30, Exam 60",
                    icon = Icons.Default.EditNote,
                    accent = primaryColor,
                    testTag = "tile_enter_marks",
                    onClick = onNavigateToMarks
                )
            )
        }
        if (canViewStudents) {
            add(
                QuickActionSpec(
                    title = "Students",
                    subtitle = if (uiState.canEdit(AppModule.STUDENTS)) "Add, edit, CSV import" else "View class roster",
                    icon = Icons.Default.Groups,
                    accent = secondaryColor,
                    testTag = "tile_students",
                    onClick = onNavigateToStudents
                )
            )
        }
        if (uiState.canViewFees) {
            add(
                QuickActionSpec(
                    title = "Fee Management",
                    subtitle = if (uiState.canEditFees) "Collect, receipts, dues & queue" else "View fee accounts & reports",
                    icon = Icons.Default.AccountBalanceWallet,
                    accent = primaryColor,
                    testTag = "tile_fee_management",
                    onClick = onNavigateToFeeManagement
                )
            )
        }
        if (uiState.canViewStudentRegister) {
            add(
                QuickActionSpec(
                    title = "Student Register",
                    subtitle = if (uiState.canEditStudentRegister) "Transport, siblings, ID cards" else "View register & ID cards",
                    icon = Icons.Default.Badge,
                    accent = secondaryColor,
                    testTag = "tile_student_register",
                    onClick = onNavigateToStudentRegister
                )
            )
        }
        if (uiState.canViewCertificates) {
            add(
                QuickActionSpec(
                    title = "Certificates",
                    subtitle = if (uiState.canEditCertificates) "TC, Bonafide, Character PDF" else "View issued register & PDF",
                    icon = Icons.Default.Verified,
                    accent = primaryColor,
                    testTag = "tile_certificates",
                    onClick = onNavigateToCertificates
                )
            )
        }
        if (uiState.canViewCashBook) {
            add(
                QuickActionSpec(
                    title = "Cash Book",
                    subtitle = if (uiState.canEditCashBook) "Expenses, fee income & closing" else "View day book & reports",
                    icon = Icons.Default.AccountBalance,
                    accent = secondaryColor,
                    testTag = "tile_cash_book",
                    onClick = onNavigateToCashBook
                )
            )
        }
        if (uiState.canViewEnquiry) {
            add(
                QuickActionSpec(
                    title = "Enquiry",
                    subtitle = if (uiState.canEditEnquiry) "Follow-ups, WhatsApp & admit" else "View admission enquiries",
                    icon = Icons.Default.ContactPhone,
                    accent = tertiaryColor,
                    testTag = "tile_enquiry",
                    onClick = onNavigateToEnquiry
                )
            )
        }
        if (uiState.canViewExams) {
            add(
                QuickActionSpec(
                    title = "Exams & Admit",
                    subtitle = if (uiState.canEditExams) "Schedule, seating, duties & PDF" else "View exams & admit cards",
                    icon = Icons.Default.EventNote,
                    accent = primaryColor,
                    testTag = "tile_exams",
                    onClick = onNavigateToExams
                )
            )
        }
        if (uiState.canViewStock) {
            add(
                QuickActionSpec(
                    title = "Stock",
                    subtitle = if (uiState.canEditStock) "Purchases, sales & low alerts" else "View stock summary & PDF",
                    icon = Icons.Default.Inventory2,
                    accent = secondaryColor,
                    testTag = "tile_stock",
                    onClick = onNavigateToStock
                )
            )
        }
        if (uiState.canViewTransport) {
            add(
                QuickActionSpec(
                    title = "Transport",
                    subtitle = if (uiState.canEditTransport) "Routes, stops & driver list" else "View routes & students",
                    icon = Icons.Default.DirectionsBus,
                    accent = tertiaryColor,
                    testTag = "tile_transport",
                    onClick = onNavigateToTransport
                )
            )
        }
        if (uiState.canAccessStudentAttendance) {
            add(
                QuickActionSpec(
                    title = "Student Attendance",
                    subtitle = "Daily roll, calendar & PDF",
                    icon = Icons.Default.FactCheck,
                    accent = primaryColor,
                    testTag = "tile_student_attendance",
                    onClick = { onNavigateToStudentAttendance(uiState.activeSchoolDocId, null) }
                )
            )
        }
        if (canViewCoScholastic) {
            add(
                QuickActionSpec(
                    title = "Co-Scholastic",
                    subtitle = "Grades & attendance",
                    icon = Icons.Default.FactCheck,
                    accent = secondaryColor,
                    testTag = "tile_coscholastic",
                    onClick = onNavigateToCoScholastic
                )
            )
        }
        if (canViewReports) {
            add(
                QuickActionSpec(
                    title = "A4 Reports",
                    subtitle = "Slips, Annual & ZIP",
                    icon = Icons.Default.Description,
                    accent = primaryColor,
                    testTag = "tile_reports",
                    onClick = onNavigateToReports
                )
            )
        }
        if (canViewTeachers) {
            val activeTeachers = uiState.teachers.count { it.active }
            add(
                QuickActionSpec(
                    title = "Teachers & Setup",
                    subtitle = "$activeTeachers active • periods & load",
                    icon = Icons.Default.School,
                    accent = secondaryColor,
                    testTag = "tile_teachers_assignments",
                    onClick = onNavigateToTeachers
                )
            )
        }
        if (canViewTimetable) {
            val ttSub = when {
                timetableClashCount > 0 -> "$timetableClashCount clash(es) to fix"
                uiState.canEdit(AppModule.TIMETABLE) -> "Edit, clashes & A4 PDF"
                else -> "View schedules & PDF"
            }
            add(
                QuickActionSpec(
                    title = "Timetable",
                    subtitle = ttSub,
                    icon = Icons.Default.CalendarMonth,
                    accent = if (timetableClashCount > 0) FailRed else primaryColor,
                    testTag = "tile_timetable",
                    onClick = onNavigateToTimetable
                )
            )
            add(
                QuickActionSpec(
                    title = "Staff Attendance",
                    subtitle = if (uiState.canEdit(AppModule.TIMETABLE)) "Daily sheet & A4 PDF" else "View staff attendance",
                    icon = Icons.Default.HowToReg,
                    accent = secondaryColor,
                    testTag = "tile_teacher_attendance",
                    onClick = onNavigateToTeacherAttendance
                )
            )
        }
        if (canViewMarks) {
            add(
                QuickActionSpec(
                    title = "Pending Marks",
                    subtitle = if (uiState.isTeacherRestricted) "My incomplete marks" else "By exam & reminders",
                    icon = Icons.Default.AssignmentLate,
                    accent = AbsentAmber,
                    testTag = "tile_pending_marks",
                    onClick = onNavigateToPendingMarks
                )
            )
        }
        if (canViewTimetable || canViewTeachers) {
            add(
                QuickActionSpec(
                    title = "Workload Report",
                    subtitle = "Periods, subs, PDF/CSV",
                    icon = Icons.Default.Assessment,
                    accent = primaryColor,
                    testTag = "tile_workload_report",
                    onClick = onNavigateToWorkloadReport
                )
            )
        }
        if (canViewMembers) {
            val reqSub = if (uiState.pendingJoinRequestCount > 0) {
                "${uiState.pendingJoinRequestCount} pending request(s)"
            } else {
                "${uiState.orgMembers.count { it.status == "active" }} active member(s)"
            }
            add(
                QuickActionSpec(
                    title = "Members",
                    subtitle = reqSub,
                    icon = Icons.Default.AdminPanelSettings,
                    accent = tertiaryColor,
                    testTag = "tile_members",
                    onClick = onNavigateToMembers
                )
            )
        }
        if (canEditSettings) {
            add(
                QuickActionSpec(
                    title = "Schools",
                    subtitle = "${uiState.activeSchools.size} active • logos & classes",
                    icon = Icons.Default.Apartment,
                    accent = primaryColor,
                    testTag = "tile_schools",
                    onClick = onNavigateToSchools
                )
            )
            val backupSub = if (uiState.settings.lastBackupEpochMs > 0L) {
                val fmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
                "Last: ${fmt.format(Date(uiState.settings.lastBackupEpochMs))}"
            } else {
                "Export / Restore JSON"
            }
            add(
                QuickActionSpec(
                    title = "Backup",
                    subtitle = backupSub,
                    icon = Icons.Default.Backup,
                    accent = tertiaryColor,
                    testTag = "tile_backup",
                    onClick = onNavigateToBackup
                )
            )
        }
        add(
            QuickActionSpec(
                title = "Settings",
                subtitle = if (canViewSettings) "Session, pass %, account" else "Account & organisation",
                icon = Icons.Default.Settings,
                accent = mutedColor,
                testTag = "tile_settings",
                onClick = onNavigateToSettings
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active School Header Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NavyDark, NavyPrimary, SteelSecondary)
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SchoolLogoAvatar(
                        school = activeSchool,
                        size = 60.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeSchool.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activeSchool.address.isNotBlank()) {
                            Text(
                                text = activeSchool.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Class ${activeClassName.ifBlank { "—" }}-${uiState.filter.section}  •  Session ${uiState.filter.session}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Phase 7: Teacher "My Day" Home Card (for any member linked to a Phase 4 Teacher profile)
        if (uiState.resolveOwnLinkedTeacher() != null) {
            item {
                TeacherMyDayHomeCard(
                    uiState = uiState,
                    onOpenTimetable = onNavigateToTimetable,
                    onOpenPendingMarks = onNavigateToPendingMarks,
                    onOpenMarksForItem = onOpenPendingMarksItem,
                    onOpenCoScholasticForSection = onOpenCoScholasticSection
                )
            }
        }

        // Phase 11: Teacher "My Exam Duties" Home Card (visible whenever this teacher has invigilator duties)
        if (uiState.ownExamDuties.isNotEmpty()) {
            item {
                TeacherMyExamDutiesHomeCard(
                    duties = uiState.ownExamDuties,
                    isHindi = uiState.phase10HindiLabels
                )
            }
        }

        // Phase 8: Class Teacher Student Attendance Home Card (1-tap "Take Attendance" for own section)
        if (uiState.effectiveOwnTeacherScope?.classTeacherSections?.isNotEmpty() == true) {
            item {
                ClassTeacherAttendanceHomeCard(
                    uiState = uiState,
                    onTakeAttendance = onTakeStudentAttendanceSection
                )
            }
        }

        // Phase 8: Admin / Attendance Viewer Today's Student Attendance Summary per School
        if (uiState.canAccessStudentAttendance && (uiState.isAdmin || uiState.canView(AppModule.ATTENDANCE))) {
            item {
                AdminStudentAttendanceSummaryCard(
                    uiState = uiState,
                    onOpenStudentAttendance = { schDocId, dateIso ->
                        onNavigateToStudentAttendance(schDocId, dateIso)
                    }
                )
            }
        }

        // Phase 7: Admin / Non-restricted Pending Marks Summary Tile by Exam
        if (canViewMarks && !uiState.isTeacherRestricted) {
            item {
                AdminPendingMarksSummaryCard(
                    uiState = uiState,
                    onOpenPendingMarks = onNavigateToPendingMarks
                )
            }
        }

        // Teacher Own Profile Summary Card (visible to Teachers so they can view/update their own profile)
        val member = uiState.currentMember
        if (member != null && member.orgRole == OrgRole.TEACHER) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.testTag("teacher_profile_dashboard_card")
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
                                photoBase64 = member.teacherPhotoBase64,
                                studentName = member.name.ifBlank { "T" },
                                size = 46.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "My Teacher Profile",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = member.name.ifBlank { member.email },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val subSummary = member.requestedSubjects.joinToString(", ").ifBlank { "No subjects listed" }
                                val clsSummary = member.requestedClasses.joinToString(", ").ifBlank { "No classes listed" }
                                Text(
                                    text = "Classes: $clsSummary  •  Subjects: $subSummary",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (member.assignedClassSubjects.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = "Marks editing restricted to ${member.assignedClassSubjects.size} assigned class-subject scope(s).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onEditOwnTeacherProfile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_teacher_profile_button")
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update My Teacher Profile")
                        }
                    }
                }
            }
        }

        // One-time Legacy Exam Structure Warning (only for admins / settings editors)
        if (uiState.legacyMarksCount > 0 && canEditSettings) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FailRedContainer,
                    border = BorderStroke(1.dp, FailRed.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("legacy_marks_warning_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = FailRed)
                            Text(
                                text = "Old Exam Structure Data Detected (${uiState.legacyMarksCount} entries)",
                                style = MaterialTheme.typography.titleSmall,
                                color = FailRed
                            )
                        }
                        Text(
                            text = "This app now uses one unified 10 + 30 + 60 exam structure for both terms (no Note Book, Subject Enrich, or Periodic Test). Delete the old-structure records now to keep calculations clean.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5A1212)
                        )
                        Button(
                            onClick = { showLegacyConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                            modifier = Modifier.testTag("delete_legacy_marks_button")
                        ) {
                            Text("Review & Delete Old Marks")
                        }
                    }
                }
            }
        }

        // Backup 30-day reminder banner (only for settings editors)
        if (uiState.isBackupOverdue && canEditSettings) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AbsentAmberContainer,
                    border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_backup_warning")
                        .clickable { onNavigateToBackup() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Backup reminder",
                            tint = AbsentAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.settings.lastBackupEpochMs == 0L)
                                    "No JSON backup exported yet"
                                else
                                    "JSON backup is over 30 days old",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF4E2D00)
                            )
                            Text(
                                text = "Tap here to save an offline JSON backup of your organisation's cloud data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B4200)
                            )
                        }
                    }
                }
            }
        }

        // Quick Context Pickers (shown when member can view academic modules)
        if (canViewAcademicDirectory) {
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Class & Student",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "10 / 30 / 60  •  ${uiState.subjectsForClass(activeClassName).size} Subjects",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        SessionClassSectionBar(
                            uiState = uiState,
                            onSessionSelected = onSessionSelected,
                            onClassSelected = onClassSelected,
                            onSectionSelected = onSectionSelected
                        )
                        val studentOptions = if (filtered.isEmpty()) {
                            listOf("No students in Class $activeClassName-${uiState.filter.section}")
                        } else {
                            filtered.map { "Roll ${it.rollNo} • ${it.name}" }
                        }
                        val currentLabel = selectedStudent?.let { "Roll ${it.rollNo} • ${it.name}" }
                            ?: studentOptions.first()

                        CompactDropdownSelector(
                            label = "Selected Student (${filtered.size} in section)",
                            selectedValue = currentLabel,
                            options = studentOptions,
                            onSelect = { chosen ->
                                val idx = studentOptions.indexOf(chosen)
                                if (idx in filtered.indices) {
                                    onStudentSelected(filtered[idx].id)
                                }
                            },
                            testTag = "picker_student"
                        )
                    }
                }
            }

            // KPI Summary Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Students (${uiState.filter.session})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.sessionStudents.size.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${filtered.size} in Class $activeClassName-${uiState.filter.section}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Marks Entered",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.marksCompletionPercent}%",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { uiState.marksCompletionPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    }
                }
            }
        } else if (uiState.canView(AppModule.TIMETABLE)) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Timetable Access Enabled",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (uiState.canEdit(AppModule.TIMETABLE)) {
                                        "View and edit class, teacher, and master timetables, check conflicts, and export offline A4 PDFs."
                                    } else {
                                        "View class, teacher, and master timetables, check free teachers, and print offline A4 PDFs."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = onNavigateToTimetable,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_timetable_card_button")
                        ) {
                            Text("Open Timetable")
                        }
                    }
                }
            }
        }

        // Quick Action Tiles (strictly filtered to modules this member can view)
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                quickActions.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { spec ->
                            DashboardActionTile(
                                title = spec.title,
                                subtitle = spec.subtitle,
                                icon = spec.icon,
                                accent = spec.accent,
                                onClick = spec.onClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(spec.testTag)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Student Dashboard Summary List (only shown if member has Students, Marks, or Reports access)
        if (canViewAcademicDirectory) {
            item {
                StudentDashboardHeaderControls(
                    totalCount = scopedSummaries.size,
                    passCount = passCount,
                    needsAttentionCount = needsAttentionCount,
                    pendingCount = pendingCount,
                    activeClassLabel = "$activeClassName-${uiState.filter.section}",
                    searchQuery = studentSearchQuery,
                    onSearchQueryChange = { studentSearchQuery = it },
                    statusFilter = statusFilter,
                    onStatusFilterSelected = { statusFilter = it },
                    onlyActiveClass = onlyActiveClass,
                    onToggleOnlyActiveClass = { onlyActiveClass = !onlyActiveClass },
                    onManageAllStudents = onNavigateToStudents,
                    showManageButton = canViewStudents
                )
            }

            if (displayedSummaries.isEmpty()) {
                item {
                    StudentDashboardEmptyCard(
                        hasAnySessionStudents = allSessionSummaries.isNotEmpty(),
                        onClearFilters = {
                            studentSearchQuery = ""
                            statusFilter = DashboardStatusFilter.ALL
                            onlyActiveClass = false
                        },
                        onNavigateToStudents = onNavigateToStudents,
                        canManageStudents = uiState.canEdit(AppModule.STUDENTS)
                    )
                }
            } else {
                items(displayedSummaries, key = { "dash_st_${it.student.id}" }) { summary ->
                    StudentSummaryDashboardCard(
                        summary = summary,
                        isCurrentlySelected = selectedStudent?.id == summary.student.id,
                        onOpenMarks = onOpenStudentMarks,
                        onOpenCoScholastic = onOpenStudentCoScholastic,
                        onOpenReport = onOpenStudentReport,
                        canOpenMarks = canViewMarks,
                        canOpenCoScholastic = canViewCoScholastic,
                        canOpenReport = canViewReports
                    )
                }
            }
        }
    }

    if (showLegacyConfirm) {
        ConfirmDangerDialog(
            title = "Delete Old Exam Structure Marks?",
            message = "Permanently delete ${uiState.legacyMarksCount} legacy Note Book, Subject Enrich, and Periodic Test records? Your unified 10/30/60 marks will not be affected.",
            confirmLabel = "Delete Old Data",
            onConfirm = {
                onDeleteLegacyMarks()
                showLegacyConfirm = false
            },
            onDismiss = { showLegacyConfirm = false }
        )
    }
}

@Composable
private fun DashboardActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
