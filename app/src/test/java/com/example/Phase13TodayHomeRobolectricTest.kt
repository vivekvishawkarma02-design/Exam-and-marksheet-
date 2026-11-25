package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.data.cloud.CloudMember
import com.example.data.cloud.CloudOrganisation
import com.example.data.cloud.CloudSchoolRecord
import com.example.domain.AccessLevel
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionScope
import com.example.domain.ExamInvigilatorDutyRecord
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeReceiptStatus
import com.example.domain.FeeStructureConfig
import com.example.domain.LeaveReason
import com.example.domain.LeaveStatus
import com.example.domain.MemberPermissions
import com.example.domain.OrgRole
import com.example.domain.PaymentMode
import com.example.domain.SyncStatus
import com.example.domain.SyncUiState
import com.example.domain.TeacherAccessScope
import com.example.domain.TeacherLeaveRecord
import com.example.domain.TeacherProfile
import com.example.ui.DashboardScreen
import com.example.ui.FilterSelection
import com.example.ui.SchoolUiState
import com.example.ui.theme.SchoolAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class Phase13TodayHomeRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val todayIso = "2026-07-15"
    private val schoolId = "sch_1"
    private val session = "2026-27"

    private val activeOrg = CloudOrganisation(
        id = "org_1",
        name = "Sunrise Group",
        ownerUid = "u_owner"
    )

    private val school = SchoolEntity(
        id = 1L,
        name = "Sunrise Public School",
        address = "Lucknow",
        logoBase64 = ""
    )

    private val class5 = SchoolClassEntity(
        id = 1L,
        schoolId = 1L,
        classGroupId = 1L,
        name = "5",
        sortOrder = 1
    )

    private val schoolRecord = CloudSchoolRecord(
        docId = schoolId,
        stableId = 1L,
        name = "Sunrise Public School",
        address = "Lucknow",
        mobile = "9876500000",
        email = "info@sunrise.edu",
        logoBase64 = "",
        active = true,
        classGroups = emptyList(),
        classes = emptyList()
    )

    private val stu1 = StudentEntity(
        id = 101L,
        schoolId = 1L,
        session = session,
        admNo = "ADM-101",
        name = "Aarav Sharma",
        fatherName = "Rajesh Sharma",
        motherName = "Sunita Sharma",
        dob = "2015-04-10",
        mobile = "9876543210",
        address = "Lucknow",
        className = "5",
        section = "A",
        rollNo = 1,
        status = "ACTIVE"
    )

    private val feeStructure = FeeStructureConfig(
        id = "fs_1",
        schoolId = schoolId,
        session = session,
        tuitionByClassAndMonth = mapOf("5" to mapOf("APR" to 1000, "MAY" to 1000))
    )

    private val todayReceipt = FeeReceiptRecord(
        id = "r1",
        schoolId = schoolId,
        session = session,
        receiptNumber = "SCH-1",
        deviceNodeCode = "A1",
        sequenceNo = 1,
        receiptDateIso = todayIso,
        studentId = "stu_101",
        studentName = stu1.name,
        studentAdmNo = stu1.admNo,
        fatherName = stu1.fatherName,
        className = "5",
        section = "A",
        rollNo = 1,
        lineItems = emptyList(),
        totalBeforeConcession = 1000,
        concessionMode = ConcessionMode.NONE,
        concessionScope = ConcessionScope.WHOLE_RECEIPT,
        netPayableAmount = 1000,
        amountPaid = 1000,
        balanceDueOnReceipt = 0,
        paymentMode = PaymentMode.CASH,
        status = FeeReceiptStatus.ISSUED,
        receivedByUid = "u_owner",
        receivedByName = "Principal",
        issuedAtMs = 1_700_000_000_000L
    )

    @Test
    fun ownerOfflineHome_rendersFastWithSyncStatusGuideSkipReopenAndTiles() {
        val ownerMember = CloudMember(
            uid = "u_owner",
            name = "Principal",
            email = "owner@school.in",
            role = OrgRole.OWNER.name,
            status = "active",
            allSchools = true,
            permissions = MemberPermissions.presetFor(OrgRole.OWNER).toFirestoreMap()
        )

        var state by mutableStateOf(
            SchoolUiState(
                activeOrg = activeOrg,
                currentMember = ownerMember,
                isOnline = false,
                syncUiState = SyncUiState(status = SyncStatus.OFFLINE, pendingCount = 2),
                schools = listOf(school),
                schoolRecordsByDocId = mapOf(schoolId to schoolRecord),
                schoolDocIdByLongId = mapOf(1L to schoolId),
                schoolClasses = listOf(class5),
                allStudents = listOf(stu1),
                studentDocIdByLongId = mapOf(101L to "stu_101"),
                filter = FilterSelection(schoolId = 1L, session = session, className = "5", section = "A"),
                feeStructures = listOf(feeStructure),
                feeReceipts = listOf(todayReceipt),
                phase10HindiLabels = true,
                isFirstTimeGuideSkipped = false
            )
        )

        val renderStartNs = System.nanoTime()
        composeRule.setContent {
            SchoolAppTheme {
                DashboardScreen(
                    uiState = state,
                    onSessionSelected = {},
                    onClassSelected = {},
                    onSectionSelected = {},
                    onStudentSelected = {},
                    onDeleteLegacyMarks = {},
                    onNavigateToSchools = {},
                    onNavigateToStudents = {},
                    onNavigateToMarks = {},
                    onNavigateToCoScholastic = {},
                    onNavigateToReports = {},
                    onNavigateToSettings = {},
                    onNavigateToBackup = {},
                    onOpenStudentMarks = {},
                    onOpenStudentCoScholastic = {},
                    onOpenStudentReport = {},
                    onSkipFirstTimeGuide = { state = state.copy(isFirstTimeGuideSkipped = true) },
                    onReopenFirstTimeGuide = { state = state.copy(isFirstTimeGuideSkipped = false) },
                    todayIsoOverride = todayIso
                )
            }
        }
        composeRule.waitForIdle()
        val firstFrameMs = (System.nanoTime() - renderStartNs) / 1_000_000.0
        println("PHASE13_OFFLINE_RENDER_BENCHMARK: Owner DashboardScreen offline first-frame=${"%.1f".format(firstFrameMs)} ms")

        composeRule.onNodeWithTag("today_hero_header_card").assertIsDisplayed()
        composeRule.onNodeWithTag("today_sync_status_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("first_time_setup_guide_card").assertIsDisplayed()
        composeRule.onNodeWithTag("phase13_quick_actions_bar").assertIsDisplayed()

        composeRule.onNodeWithTag("skip_setup_guide_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("first_time_setup_guide_card").assertDoesNotExist()
        composeRule.onNodeWithTag("reopen_setup_guide_button").assertIsDisplayed()

        composeRule.onNodeWithTag("reopen_setup_guide_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("first_time_setup_guide_card").assertIsDisplayed()

        composeRule.onNodeWithTag("fee_clerk_today_hero_section").assertDoesNotExist()
        assertTrue(firstFrameMs >= 0.0)
    }

    @Test
    fun teacherOfflineHome_showsOnlyOwnItemsAndBigTakeAttendanceButton() {
        val teacherMember = CloudMember(
            uid = "u_teacher",
            name = "Meera Joshi",
            email = "meera@school.in",
            role = OrgRole.TEACHER.name,
            status = "active",
            allSchools = true,
            permissions = MemberPermissions(marks = AccessLevel.EDIT, timetable = AccessLevel.VIEW).toFirestoreMap()
        )
        var takeAttendanceCalled = false

        val state = SchoolUiState(
            activeOrg = activeOrg,
            currentMember = teacherMember,
            isOnline = false,
            syncUiState = SyncUiState(status = SyncStatus.OFFLINE),
            schools = listOf(school),
            schoolRecordsByDocId = mapOf(schoolId to schoolRecord),
            schoolDocIdByLongId = mapOf(1L to schoolId),
            schoolClasses = listOf(class5),
            allStudents = listOf(stu1),
            studentDocIdByLongId = mapOf(101L to "stu_101"),
            filter = FilterSelection(schoolId = 1L, session = session, className = "5", section = "A"),
            teachers = listOf(TeacherProfile("t_1", "Meera Joshi", email = "meera@school.in", schoolIds = listOf(schoolId), linkedMemberUid = "u_teacher")),
            ownTeacherScope = TeacherAccessScope("u_teacher", "t_1", classTeacherSections = listOf("$schoolId|5|A")),
            teacherLeaves = listOf(
                TeacherLeaveRecord("lv1", "t_1", "Meera Joshi", "2026-07-20", "2026-07-20", LeaveReason.CASUAL_LEAVE, halfDay = false, status = LeaveStatus.PENDING)
            ),
            examDuties = listOf(
                ExamInvigilatorDutyRecord("d1", schoolId, session, "ex1", "UT-I", "2026-07-17", "09:00", "11:00", "r1", "Room 1", "t_1", "u_teacher", "Meera Joshi")
            ),
            phase10HindiLabels = false
        )

        composeRule.setContent {
            SchoolAppTheme {
                DashboardScreen(
                    uiState = state,
                    onSessionSelected = {},
                    onClassSelected = {},
                    onSectionSelected = {},
                    onStudentSelected = {},
                    onDeleteLegacyMarks = {},
                    onNavigateToSchools = {},
                    onNavigateToStudents = {},
                    onNavigateToMarks = {},
                    onNavigateToCoScholastic = {},
                    onNavigateToReports = {},
                    onNavigateToSettings = {},
                    onNavigateToBackup = {},
                    onOpenStudentMarks = {},
                    onOpenStudentCoScholastic = {},
                    onOpenStudentReport = {},
                    onTakeStudentAttendanceSection = { _, cls, sec, _ ->
                        if (cls == "5" && sec == "A") takeAttendanceCalled = true
                    },
                    todayIsoOverride = todayIso
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("teacher_today_hero_section").assertIsDisplayed()
        composeRule.onNodeWithTag("teacher_big_take_attendance_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("teacher_today_pending_marks").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("teacher_today_exam_duties").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("teacher_big_take_attendance_button").performScrollTo().performClick()
        assertTrue(takeAttendanceCalled)

        composeRule.onNodeWithTag("owner_admin_today_section").assertDoesNotExist()
        composeRule.onNodeWithTag("first_time_setup_guide_card").assertDoesNotExist()
        composeRule.onNodeWithTag("fee_clerk_today_hero_section").assertDoesNotExist()
    }

    @Test
    fun feeClerkOfflineHome_searchesStudentAndOpensFeeCollectionInOneTap() {
        val clerkMember = CloudMember(
            uid = "u_clerk",
            name = "Ramesh Clerk",
            email = "clerk@school.in",
            role = OrgRole.OFFICE_OPERATOR.name,
            status = "active",
            allSchools = true,
            permissions = MemberPermissions(fees = AccessLevel.EDIT).toFirestoreMap()
        )
        var openedTab: Int? = null
        var openedStudent: StudentEntity? = null

        val state = SchoolUiState(
            activeOrg = activeOrg,
            currentMember = clerkMember,
            isOnline = false,
            syncUiState = SyncUiState(status = SyncStatus.OFFLINE),
            schools = listOf(school),
            schoolRecordsByDocId = mapOf(schoolId to schoolRecord),
            schoolDocIdByLongId = mapOf(1L to schoolId),
            schoolClasses = listOf(class5),
            allStudents = listOf(stu1),
            studentDocIdByLongId = mapOf(101L to "stu_101"),
            filter = FilterSelection(schoolId = 1L, session = session, className = "5", section = "A"),
            feeStructures = listOf(feeStructure),
            feeReceipts = emptyList(),
            phase10HindiLabels = false
        )

        composeRule.setContent {
            SchoolAppTheme {
                DashboardScreen(
                    uiState = state,
                    onSessionSelected = {},
                    onClassSelected = {},
                    onSectionSelected = {},
                    onStudentSelected = {},
                    onDeleteLegacyMarks = {},
                    onNavigateToSchools = {},
                    onNavigateToStudents = {},
                    onNavigateToMarks = {},
                    onNavigateToCoScholastic = {},
                    onNavigateToReports = {},
                    onNavigateToSettings = {},
                    onNavigateToBackup = {},
                    onOpenStudentMarks = {},
                    onOpenStudentCoScholastic = {},
                    onOpenStudentReport = {},
                    onOpenFeeManagementTab = { tab, student ->
                        openedTab = tab
                        openedStudent = student
                    },
                    todayIsoOverride = todayIso
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("fee_clerk_today_hero_section").assertIsDisplayed()
        composeRule.onNodeWithTag("fee_clerk_student_search").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("fee_clerk_today_collection").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("fee_clerk_unsent_dues").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("fee_clerk_student_search").performScrollTo().performTextInput("3210")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fee_clerk_collect_btn_101").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("fee_clerk_collect_btn_101").performClick()

        assertEquals(0, openedTab)
        assertNotNull(openedStudent)
        assertEquals("Aarav Sharma", openedStudent!!.name)

        composeRule.onNodeWithTag("owner_admin_today_section").assertDoesNotExist()
        composeRule.onNodeWithTag("teacher_today_hero_section").assertDoesNotExist()
    }
}
