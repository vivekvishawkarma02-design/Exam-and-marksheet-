package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.domain.AccessLevel
import com.example.domain.AppModule
import com.example.domain.DefaultRemarkCategory
import com.example.domain.GatePassRecord
import com.example.domain.LowAttendanceStudentRow
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgRole
import com.example.domain.ParentMeetingRecord
import com.example.domain.Phase19AccessEvaluator
import com.example.domain.Phase19RemarksPtmAndGateEngine
import com.example.domain.PtmCallStatus
import com.example.domain.PtmStudentCallEntry
import com.example.domain.ResultStatus
import com.example.domain.StudentRemarkRecord
import com.example.domain.StudentRemarkStatus
import com.example.domain.TeacherAccessScope
import com.example.domain.VisitorEntryRecord
import com.example.domain.VisitorStatus
import com.example.domain.WeakStudentRow
import com.example.util.GatePassA5PdfRequest
import com.example.util.Phase19PdfGenerator
import com.example.util.PtmSummaryPdfRequest
import com.example.util.StudentRemarkSummaryPdfRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase19RemarksPtmAndGateTest {

    private val school1Id = "sch_1"
    private val session = "2026-2027"

    private fun ownerProfile() = MemberAccessProfile(
        uid = "uid_owner",
        role = OrgRole.OWNER,
        status = MemberStatus.ACTIVE,
        allSchools = true,
        allowedSchoolIds = listOf(school1Id),
        permissions = MemberPermissions.presetFor(OrgRole.OWNER)
    )

    private fun adminProfile() = MemberAccessProfile(
        uid = "uid_admin",
        role = OrgRole.ADMIN,
        status = MemberStatus.ACTIVE,
        allSchools = false,
        allowedSchoolIds = listOf(school1Id),
        permissions = MemberPermissions.presetFor(OrgRole.ADMIN)
    )

    private fun teacherProfile(
        uid: String = "uid_teacher_1",
        remarksAccess: AccessLevel = AccessLevel.NONE,
        gateAccess: AccessLevel = AccessLevel.NONE,
        attendanceAccess: AccessLevel = AccessLevel.NONE
    ) = MemberAccessProfile(
        uid = uid,
        role = OrgRole.TEACHER,
        status = MemberStatus.ACTIVE,
        allSchools = false,
        allowedSchoolIds = listOf(school1Id),
        permissions = MemberPermissions.presetFor(OrgRole.TEACHER).copy(
            studentRemarks = remarksAccess,
            gate = gateAccess,
            attendance = attendanceAccess
        )
    )

    @Test
    fun `1 - Access modules Student Remarks and Gate default to NONE when missing and EDIT for Owner and Admin`() {
        val parsedFromEmpty = MemberPermissions.fromMap(emptyMap<String, Any>())
        assertEquals(AccessLevel.NONE, parsedFromEmpty.levelFor(AppModule.STUDENT_REMARKS))
        assertEquals(AccessLevel.NONE, parsedFromEmpty.levelFor(AppModule.GATE))

        val ownerPerms = MemberPermissions.presetFor(OrgRole.OWNER)
        assertEquals(AccessLevel.EDIT, ownerPerms.levelFor(AppModule.STUDENT_REMARKS))
        assertEquals(AccessLevel.EDIT, ownerPerms.levelFor(AppModule.GATE))

        val adminPerms = MemberPermissions.presetFor(OrgRole.ADMIN)
        assertEquals(AccessLevel.EDIT, adminPerms.levelFor(AppModule.STUDENT_REMARKS))
        assertEquals(AccessLevel.EDIT, adminPerms.levelFor(AppModule.GATE))

        val teacherPerms = MemberPermissions.presetFor(OrgRole.TEACHER)
        assertEquals(AccessLevel.NONE, teacherPerms.levelFor(AppModule.STUDENT_REMARKS))
        assertEquals(AccessLevel.NONE, teacherPerms.levelFor(AppModule.GATE))
    }

    @Test
    fun `2 - Sensitive Remark Visibility - only Class Teacher, Owner, and Admins see remarks by default`() {
        val ct10a = teacherProfile(uid = "ct_10a", remarksAccess = AccessLevel.NONE)
        val ct10aScope = TeacherAccessScope(
            uid = "ct_10a",
            teacherId = "t1",
            teacherName = "Ramesh Sir",
            classTeacherSections = listOf("$school1Id|10th|A"),
            assignedClassSectionSubjects = listOf("$school1Id|10th|A|Maths", "$school1Id|9th|B|Maths")
        )

        val otherTeacher = teacherProfile(uid = "t_other", remarksAccess = AccessLevel.NONE)
        val otherTeacherScope = TeacherAccessScope(
            uid = "t_other",
            teacherId = "t2",
            teacherName = "Suresh Sir",
            classTeacherSections = listOf("$school1Id|9th|B"),
            assignedClassSectionSubjects = listOf("$school1Id|10th|A|Science")
        )

        val grantedTeacher = teacherProfile(uid = "t_granted", remarksAccess = AccessLevel.VIEW)

        // Class teacher of 10th-A can view & edit 10th-A remarks, but CANNOT view 9th-B remarks
        assertTrue(Phase19AccessEvaluator.canViewStudentRemarksForClass(ct10a, ct10aScope, school1Id, "10th", "A"))
        assertTrue(Phase19AccessEvaluator.canEditStudentRemarksForClass(ct10a, ct10aScope, school1Id, "10th", "A"))
        assertFalse(Phase19AccessEvaluator.canViewStudentRemarksForClass(ct10a, ct10aScope, school1Id, "9th", "B"))

        // Subject teacher of 10th-A Science who is NOT 10th-A class teacher sees NOTHING for 10th-A
        assertFalse(Phase19AccessEvaluator.canViewStudentRemarksForClass(otherTeacher, otherTeacherScope, school1Id, "10th", "A"))
        assertFalse(Phase19AccessEvaluator.canEditStudentRemarksForClass(otherTeacher, otherTeacherScope, school1Id, "10th", "A"))

        // Explicitly granted viewer can view 10th-A remarks (view-only)
        assertTrue(Phase19AccessEvaluator.canViewStudentRemarksForClass(grantedTeacher, null, school1Id, "10th", "A"))
        assertFalse(Phase19AccessEvaluator.canEditStudentRemarksForClass(grantedTeacher, null, school1Id, "10th", "A"))

        // Owner and Admin have full access and are the ONLY ones who can print A4 Student Remark Summary PDF
        assertTrue(Phase19AccessEvaluator.canPrintStudentRemarkSummaryPdf(ownerProfile(), school1Id))
        assertTrue(Phase19AccessEvaluator.canPrintStudentRemarkSummaryPdf(adminProfile(), school1Id))
        assertFalse(Phase19AccessEvaluator.canPrintStudentRemarkSummaryPdf(ct10a, school1Id))
        assertFalse(Phase19AccessEvaluator.canPrintStudentRemarkSummaryPdf(grantedTeacher, school1Id))

        val remark10a = StudentRemarkRecord(
            id = "rem_1",
            schoolId = school1Id,
            session = session,
            studentId = "stu_101",
            studentAdmNo = "A101",
            studentName = "Aarav",
            className = "10th",
            section = "A",
            rollNo = 1,
            dateIso = "2026-08-10",
            category = DefaultRemarkCategory.ACADEMIC.labelEn,
            note = "Needs revision in Algebra",
            authorUid = "ct_10a",
            authorName = "Ramesh Sir"
        )
        val remark9b = StudentRemarkRecord(
            id = "rem_2",
            schoolId = school1Id,
            session = session,
            studentId = "stu_202",
            studentAdmNo = "A202",
            studentName = "Riya",
            className = "9th",
            section = "B",
            rollNo = 2,
            dateIso = "2026-08-10",
            category = DefaultRemarkCategory.PRAISE.labelEn,
            note = "Top score in Hindi",
            authorUid = "t_other",
            authorName = "Suresh Sir"
        )
        val allRemarks = listOf(remark10a, remark9b)

        val visibleForCt10a = Phase19RemarksPtmAndGateEngine.filterRemarksForViewer(
            allRemarks = allRemarks,
            member = ct10a,
            teacherScope = ct10aScope,
            schoolId = school1Id,
            session = session
        )
        assertEquals(listOf("rem_1"), visibleForCt10a.map { it.id })

        val visibleForOther = Phase19RemarksPtmAndGateEngine.filterRemarksForViewer(
            allRemarks = allRemarks,
            member = otherTeacher,
            teacherScope = otherTeacherScope,
            schoolId = school1Id,
            session = session
        )
        assertEquals(listOf("rem_2"), visibleForOther.map { it.id })

        val visibleForAdmin = Phase19RemarksPtmAndGateEngine.filterRemarksForViewer(
            allRemarks = allRemarks,
            member = adminProfile(),
            teacherScope = null,
            schoolId = school1Id,
            session = session
        )
        assertEquals(2, visibleForAdmin.size)
    }

    @Test
    fun `3 - Cancel Remark with mandatory reason and audit log - no silent deletion`() {
        val activeRemark = StudentRemarkRecord(
            id = "rem_101",
            schoolId = school1Id,
            session = session,
            studentId = "stu_101",
            studentAdmNo = "A101",
            studentName = "Aarav Sharma",
            className = "10th",
            section = "A",
            rollNo = 1,
            dateIso = "2026-08-10",
            category = DefaultRemarkCategory.DISCIPLINE.labelEn,
            note = "Late to morning assembly",
            authorUid = "ct_10a",
            authorName = "Ramesh Sir"
        )

        // Blank or too-short cancellation reason is rejected
        val emptyCancel = Phase19RemarksPtmAndGateEngine.cancelRemarkWithReason(
            existing = activeRemark,
            reason = "",
            actorUid = "uid_admin",
            actorName = "Principal Verma",
            nowMs = 1750000000000L
        )
        assertTrue(emptyCancel.isFailure)

        val shortCancel = Phase19RemarksPtmAndGateEngine.cancelRemarkWithReason(
            existing = activeRemark,
            reason = "ok",
            actorUid = "uid_admin",
            actorName = "Principal Verma",
            nowMs = 1750000000000L
        )
        assertTrue(shortCancel.isFailure)

        val (cancelled, auditLogSummary) = Phase19RemarksPtmAndGateEngine.cancelRemarkWithReason(
            existing = activeRemark,
            reason = "Entered on wrong student",
            actorUid = "uid_admin",
            actorName = "Principal Verma",
            nowMs = 1750000000000L
        ).getOrThrow()

        assertEquals(StudentRemarkStatus.CANCELLED, cancelled.status)
        assertFalse(cancelled.isActive)
        assertEquals("Entered on wrong student", cancelled.cancelledReason)
        assertEquals("uid_admin", cancelled.cancelledByUid)
        assertEquals("Principal Verma", cancelled.cancelledByName)
        // Original remark content and author are preserved in the cancelled record
        assertEquals("Late to morning assembly", cancelled.note)
        assertEquals("Ramesh Sir", cancelled.authorName)
        assertTrue(auditLogSummary.contains("Cancelled student remark"))
        assertTrue(auditLogSummary.contains("Entered on wrong student"))

        // Cannot cancel an already cancelled remark
        val secondCancel = Phase19RemarksPtmAndGateEngine.cancelRemarkWithReason(
            existing = cancelled,
            reason = "Second cancel attempt",
            actorUid = "uid_admin",
            actorName = "Principal Verma",
            nowMs = 1750000001000L
        )
        assertTrue(secondCancel.isFailure)
    }

    @Test
    fun `4 - Follow-ups due list and category counts`() {
        val r1 = StudentRemarkRecord(
            id = "r1",
            schoolId = school1Id,
            session = session,
            studentId = "stu_1",
            studentAdmNo = "A1",
            studentName = "Student 1",
            className = "8th",
            section = "A",
            rollNo = 1,
            dateIso = "2026-08-01",
            category = "Academic",
            note = "Note 1",
            followUpDateIso = "2026-08-10",
            followUpCompleted = false,
            authorUid = "ct_8a",
            authorName = "Teacher"
        )
        val r2 = StudentRemarkRecord(
            id = "r2",
            schoolId = school1Id,
            session = session,
            studentId = "stu_2",
            studentAdmNo = "A2",
            studentName = "Student 2",
            className = "8th",
            section = "A",
            rollNo = 2,
            dateIso = "2026-08-02",
            category = "Academic",
            note = "Note 2",
            followUpDateIso = "2026-08-05",
            followUpCompleted = false,
            authorUid = "ct_8a",
            authorName = "Teacher"
        )
        val r3Done = r1.copy(id = "r3", followUpDateIso = "2026-08-03", followUpCompleted = true, category = "Praise")
        val r4Cancelled = r1.copy(id = "r4", followUpDateIso = "2026-08-04", status = StudentRemarkStatus.CANCELLED)

        val all = listOf(r1, r2, r3Done, r4Cancelled)
        val dueList = Phase19RemarksPtmAndGateEngine.buildFollowUpsDueList(visibleRemarks = all)
        // Sorted earliest follow-up date first (r2 then r1); completed & cancelled excluded
        assertEquals(listOf("r2", "r1"), dueList.map { it.id })

        val counts = Phase19RemarksPtmAndGateEngine.computeCategoryCounts(visibleRemarks = all)
        assertEquals(2, counts["Academic"])
        assertEquals(1, counts["Praise"])
    }

    @Test
    fun `5 - PTM suggestions from weak students, low attendance, and remarks plus WhatsApp invitation format`() {
        val st1 = StudentEntity(
            id = 10L, schoolId = 1L, session = session, admNo = "A1", name = "Aarav",
            fatherName = "Rajesh", motherName = "Sunita", dob = "2011-05-10", mobile = "9876543210",
            address = "Civil Lines", className = "10th", section = "A", rollNo = 1
        )
        val st2 = StudentEntity(
            id = 20L, schoolId = 1L, session = session, admNo = "A2", name = "Vivaan",
            fatherName = "Suresh", motherName = "Meena", dob = "2011-06-12", mobile = "9123456789",
            address = "Station Road", className = "10th", section = "A", rollNo = 2
        )
        val st3 = StudentEntity(
            id = 30L, schoolId = 1L, session = session, admNo = "A3", name = "Diya",
            fatherName = "Mahesh", motherName = "Kavita", dob = "2011-07-15", mobile = "9988776655",
            address = "Main Bazar", className = "10th", section = "A", rollNo = 3
        )

        val weakRows = listOf(
            WeakStudentRow(
                student = st1,
                obtained = 110.0,
                maxMarks = 400,
                percentage = 27.5,
                grade = "E",
                resultStatus = ResultStatus.FAIL,
                failedSubjects = listOf("Maths", "Science"),
                lowScoreSubjects = listOf("Maths", "Science")
            )
        )
        val lowAttRows = listOf(
            LowAttendanceStudentRow(
                schoolDocId = school1Id,
                schoolName = "Sunrise Public School",
                studentDocId = "stu_20",
                student = st2,
                weightedDaysPresent = 30.0,
                totalWorkingDays = 50,
                attendancePercent = 60.0,
                absentCount = 20,
                leaveCount = 0
            )
        )
        val concernRemarks = listOf(
            StudentRemarkRecord(
                id = "rem_10",
                schoolId = school1Id,
                session = session,
                studentId = "stu_10",
                studentAdmNo = "A1",
                studentName = "Aarav",
                className = "10th",
                section = "A",
                rollNo = 1,
                dateIso = "2026-08-05",
                category = "Discipline",
                note = "Homework incomplete",
                authorUid = "ct_10a",
                authorName = "Ramesh Sir"
            )
        )

        val suggestions = Phase19RemarksPtmAndGateEngine.buildPtmStudentSuggestions(
            students = listOf("stu_10" to st1, "stu_20" to st2, "stu_30" to st3),
            weakStudents = weakRows,
            lowAttendanceRows = lowAttRows,
            visibleRemarks = concernRemarks
        )

        // st1 has both academic + remark reasons; st2 has low attendance; st3 has no concern
        assertEquals(2, suggestions.size)
        assertEquals("stu_10", suggestions[0].studentId)
        assertTrue(suggestions[0].reasons.any { it.contains("Maths") })
        assertTrue(suggestions[0].reasons.any { it.contains("Discipline") })
        assertEquals("stu_20", suggestions[1].studentId)
        assertTrue(suggestions[1].reasons.any { it.contains("Low Attendance", ignoreCase = true) })

        val sampleEntry = PtmStudentCallEntry(
            studentId = "stu_10",
            studentAdmNo = "A1",
            studentName = "Aarav",
            className = "10th",
            section = "A",
            rollNo = 1,
            guardianName = "Rajesh",
            mobile = "9876543210",
            suggestionReason = "Failed in Maths"
        )
        val meeting = ParentMeetingRecord(
            id = "ptm_1",
            schoolId = school1Id,
            session = session,
            title = "Class 10-A PTM",
            dateIso = "2026-08-20",
            timeLabel = "10:00 AM",
            className = "10th",
            section = "A",
            agenda = "Half-Yearly progress review",
            entries = listOf(sampleEntry),
            createdByUid = "ct_10a",
            createdByName = "Ramesh Sir"
        )

        val inviteEn = Phase19RemarksPtmAndGateEngine.buildPtmInvitationMessage(
            schoolName = "Sunrise Public School",
            meeting = meeting,
            entry = sampleEntry,
            useHindi = false
        )
        assertTrue(inviteEn.contains("Sunrise Public School"))
        assertTrue(inviteEn.contains("Aarav"))
        assertTrue(inviteEn.contains("10th-A"))
        assertTrue(inviteEn.contains("2026-08-20"))

        val waUrl = Phase19RemarksPtmAndGateEngine.buildPtmWhatsAppUrl("9876543210", inviteEn)
        assertNotNull(waUrl)
        assertTrue(waUrl!!.startsWith("https://wa.me/919876543210?text="))
    }

    @Test
    fun `6 - Gate Pass authorised pickup vs one-time person phone confirmation tick and collision-free offline pass numbers`() {
        // One-time person WITHOUT guardian phone confirmation tick must fail validation
        val errUnconfirmed = Phase19RemarksPtmAndGateEngine.validateGatePassInput(
            studentId = "stu_10",
            dateIso = "2026-08-10",
            leaveTime = "11:30 AM",
            reason = "Fever",
            pickupPersonName = "Uncle Manoj",
            pickupRelation = "Uncle",
            isOneTimePickup = true,
            guardianPhoneConfirmed = false,
            approvedByName = "Principal"
        )
        assertNotNull(errUnconfirmed)
        assertTrue(errUnconfirmed!!.contains("guardian phone confirmation", ignoreCase = true))

        // One-time person WITH guardianPhoneConfirmed = true succeeds
        assertNull(
            Phase19RemarksPtmAndGateEngine.validateGatePassInput(
                studentId = "stu_10",
                dateIso = "2026-08-10",
                leaveTime = "11:30 AM",
                reason = "Fever",
                pickupPersonName = "Uncle Manoj",
                pickupRelation = "Uncle",
                isOneTimePickup = true,
                guardianPhoneConfirmed = true,
                approvedByName = "Principal"
            )
        )

        // Authorised pickup person succeeds without phone confirmation tick
        assertNull(
            Phase19RemarksPtmAndGateEngine.validateGatePassInput(
                studentId = "stu_10",
                dateIso = "2026-08-10",
                leaveTime = "11:30 AM",
                reason = "Doctor visit",
                pickupPersonName = "Rajesh Kumar",
                pickupRelation = "Father",
                isOneTimePickup = false,
                guardianPhoneConfirmed = false,
                approvedByName = "Principal"
            )
        )

        // Two offline phones generating passes concurrently for the same school & session never collide
        val phoneAPass1 = Phase19RemarksPtmAndGateEngine.generateUniqueGatePassNumber(
            schoolId = school1Id,
            session = session,
            dateIso = "2026-08-10",
            deviceOrActorKey = "phone_office_a",
            existingPassNumbersInSchoolSession = emptyList(),
            timestampMs = 1750000100000L
        )
        val phoneBPass1 = Phase19RemarksPtmAndGateEngine.generateUniqueGatePassNumber(
            schoolId = school1Id,
            session = session,
            dateIso = "2026-08-10",
            deviceOrActorKey = "phone_gate_b",
            existingPassNumbersInSchoolSession = emptyList(),
            timestampMs = 1750000100000L
        )
        assertFalse("Offline pass numbers from two phones must not collide", phoneAPass1 == phoneBPass1)

        // Sequential pass on Phone A increments sequence number
        val phoneAPass2 = Phase19RemarksPtmAndGateEngine.generateUniqueGatePassNumber(
            schoolId = school1Id,
            session = session,
            dateIso = "2026-08-10",
            deviceOrActorKey = "phone_office_a",
            existingPassNumbersInSchoolSession = listOf(phoneAPass1),
            timestampMs = 1750000100000L
        )
        assertTrue(phoneAPass1.endsWith("-001"))
        assertTrue(phoneAPass2.endsWith("-002"))
    }

    @Test
    fun `7 - Optional half-day attendance mark allowed only when user has Attendance edit access and CSV restricted to Admin`() {
        val gateOnlyStaff = teacherProfile(
            uid = "gate_guard",
            gateAccess = AccessLevel.EDIT,
            attendanceAccess = AccessLevel.NONE
        )
        val gateAndAttendanceStaff = teacherProfile(
            uid = "office_clerk",
            gateAccess = AccessLevel.EDIT,
            attendanceAccess = AccessLevel.EDIT
        )

        assertFalse(
            Phase19AccessEvaluator.canMarkHalfDayFromGatePass(
                member = gateOnlyStaff,
                teacherScope = null,
                schoolId = school1Id,
                className = "10th",
                section = "A"
            )
        )
        assertTrue(
            Phase19AccessEvaluator.canMarkHalfDayFromGatePass(
                member = gateAndAttendanceStaff,
                teacherScope = null,
                schoolId = school1Id,
                className = "10th",
                section = "A"
            )
        )

        // CSV export strictly for Owner & Admins
        assertFalse(Phase19AccessEvaluator.canExportGateRegisterCsv(gateOnlyStaff, school1Id))
        assertTrue(Phase19AccessEvaluator.canExportGateRegisterCsv(ownerProfile(), school1Id))
        assertTrue(Phase19AccessEvaluator.canExportGateRegisterCsv(adminProfile(), school1Id))
    }

    @Test
    fun `8 - Visitor sign-in and one-tap sign-out and Today gate summary`() {
        val validationError = Phase19RemarksPtmAndGateEngine.validateVisitorEntry(
            visitorName = "Rakesh Gupta",
            mobile = "9876543210",
            purpose = "Fee enquiry",
            whomToMeet = "Office",
            dateIso = "2026-08-10",
            timeIn = "10:15 AM"
        )
        assertNull(validationError)

        val v1 = VisitorEntryRecord(
            id = "v1",
            schoolId = school1Id,
            dateIso = "2026-08-10",
            visitorName = "Rakesh Gupta",
            mobile = "9876543210",
            purpose = "Fee enquiry",
            whomToMeet = "Office",
            timeIn = "10:15 AM",
            status = VisitorStatus.IN,
            recordedByUid = "gate_1",
            recordedByName = "Gate Guard"
        )

        val signedOut = Phase19RemarksPtmAndGateEngine.signOutVisitor(
            existing = v1,
            timeOut = "10:45 AM",
            actorUid = "gate_1",
            actorName = "Gate Guard",
            nowMs = 1750000500000L
        )
        assertEquals(VisitorStatus.OUT, signedOut.status)
        assertEquals("10:45 AM", signedOut.timeOut)

        val v2Inside = v1.copy(id = "v2", visitorName = "Meena Devi")
        val passToday = GatePassRecord(
            id = "gp1",
            passNumber = "GP-2627-0810-GATE-001",
            schoolId = school1Id,
            session = session,
            dateIso = "2026-08-10",
            leaveTime = "12:00 PM",
            studentId = "stu_10",
            studentAdmNo = "A1",
            studentName = "Aarav",
            className = "10th",
            section = "A",
            rollNo = 1,
            reason = "Doctor visit",
            pickupPersonName = "Rajesh",
            pickupRelation = "Father",
            pickupMobile = "9876543210",
            isOneTimePickup = false,
            approvedByUid = "uid_admin",
            approvedByName = "Principal",
            createdByUid = "gate_1",
            createdByName = "Gate Guard"
        )

        val summary = Phase19RemarksPtmAndGateEngine.buildTodayGateSummary(
            schoolId = school1Id,
            todayIso = "2026-08-10",
            visitorEntries = listOf(signedOut, v2Inside),
            gatePasses = listOf(passToday)
        )
        assertEquals(1, summary.todayEarlyLeavesCount)
        assertEquals(2, summary.todayVisitorsTotal)
        assertEquals(1, summary.todayVisitorsInside)
        assertEquals(1, summary.todayVisitorsSignedOut)

        val csv = Phase19RemarksPtmAndGateEngine.buildGateRegisterCsv(
            passes = listOf(passToday),
            schoolName = "Sunrise Public School"
        )
        assertTrue(csv.contains("GP-2627-0810-GATE-001"))
        assertTrue(csv.contains("Sunrise Public School"))
    }

    @Test
    fun `9 - Offline PDF generators produce non-empty A4 Remark Summary, A4 PTM Summary, and A5 Gate Pass Slip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val school = SchoolEntity(id = 1L, name = "Sunrise Public School", address = "Civil Lines")

        // 1. A4 Student Remark Summary PDF
        val remarkRequest = StudentRemarkSummaryPdfRequest(
            school = school,
            session = session,
            titleLabel = "Class 10th-A Remark Summary",
            studentName = "Aarav",
            classSectionLabel = "10th-A",
            admissionNo = "A1",
            remarks = listOf(
                StudentRemarkRecord(
                    id = "r1",
                    schoolId = school1Id,
                    session = session,
                    studentId = "stu_10",
                    studentAdmNo = "A1",
                    studentName = "Aarav",
                    className = "10th",
                    section = "A",
                    rollNo = 1,
                    dateIso = "2026-08-10",
                    category = "Praise",
                    note = "Won science quiz",
                    authorUid = "ct_10a",
                    authorName = "Ramesh Sir"
                )
            ),
            generatedByName = "Principal"
        )
        val remarkFile = Phase19PdfGenerator.generateStudentRemarkSummaryPdf(context = context, request = remarkRequest)
        assertTrue(remarkFile.exists() && remarkFile.length() > 100L)
        val remarkPages = Phase19PdfGenerator.renderStudentRemarkSummaryBitmaps(remarkRequest)
        assertEquals(1, remarkPages.size)
        assertEquals(Phase19PdfGenerator.A4_WIDTH, remarkPages[0].width)
        assertEquals(Phase19PdfGenerator.A4_HEIGHT, remarkPages[0].height)

        // 2. A4 PTM Summary PDF
        val ptmRequest = PtmSummaryPdfRequest(
            school = school,
            meeting = ParentMeetingRecord(
                id = "ptm_1",
                schoolId = school1Id,
                session = session,
                title = "Half-Yearly PTM",
                dateIso = "2026-08-20",
                timeLabel = "10:00 AM",
                className = "10th",
                section = "A",
                entries = listOf(
                    PtmStudentCallEntry(
                        studentId = "stu_10",
                        studentAdmNo = "A1",
                        studentName = "Aarav",
                        className = "10th",
                        section = "A",
                        rollNo = 1,
                        status = PtmCallStatus.ATTENDED,
                        discussionNote = "Discussed Maths scores",
                        decision = "Daily 1 hour practice"
                    )
                ),
                createdByUid = "ct_10a",
                createdByName = "Ramesh Sir"
            ),
            generatedByName = "Ramesh Sir"
        )
        val ptmFile = Phase19PdfGenerator.generatePtmSummaryPdf(context = context, request = ptmRequest)
        assertTrue(ptmFile.exists() && ptmFile.length() > 100L)
        val ptmPages = Phase19PdfGenerator.renderPtmSummaryBitmaps(ptmRequest)
        assertEquals(1, ptmPages.size)
        assertEquals(Phase19PdfGenerator.A4_WIDTH, ptmPages[0].width)
        assertEquals(Phase19PdfGenerator.A4_HEIGHT, ptmPages[0].height)

        // 3. A5 Early Leave Gate Pass Slip PDF
        val gateRequest = GatePassA5PdfRequest(
            school = school,
            gatePass = GatePassRecord(
                id = "gp_1",
                passNumber = "GP-2627-0810-GATE-001",
                schoolId = school1Id,
                session = session,
                dateIso = "2026-08-10",
                leaveTime = "12:15 PM",
                studentId = "stu_10",
                studentAdmNo = "ADM101",
                studentName = "Aarav",
                className = "10th",
                section = "A",
                rollNo = 1,
                reason = "Medical appointment",
                pickupPersonName = "Rajesh Kumar",
                pickupRelation = "Father",
                pickupMobile = "9876543210",
                isOneTimePickup = false,
                approvedByUid = "uid_owner",
                approvedByName = "Principal",
                createdByUid = "gate_1",
                createdByName = "Gate Guard"
            )
        )
        val passFile = Phase19PdfGenerator.generateGatePassA5Pdf(context = context, request = gateRequest)
        assertTrue(passFile.exists() && passFile.length() > 100L)
        val passBmp = Phase19PdfGenerator.renderGatePassA5Bitmap(gateRequest)
        assertEquals(Phase19PdfGenerator.A5_WIDTH, passBmp.width)
        assertEquals(Phase19PdfGenerator.A5_HEIGHT, passBmp.height)
    }
}
