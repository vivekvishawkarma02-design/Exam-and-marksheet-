package com.example

import com.example.data.CoScholasticEntity
import com.example.data.MarkEntity
import com.example.data.StudentEntity
import com.example.data.TermInfoEntity
import com.example.domain.AppModule
import com.example.domain.ClassSectionAssignment
import com.example.domain.CoScholasticArea
import com.example.domain.DayOfWeekCode
import com.example.domain.ExamType
import com.example.domain.LeaveReason
import com.example.domain.LeaveStatus
import com.example.domain.MarkComponent
import com.example.domain.AccessLevel
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgRole
import com.example.domain.PendingMarksEngine
import com.example.domain.Phase4Validator
import com.example.domain.Phase7AccessEvaluator
import com.example.domain.ReportReadinessEngine
import com.example.domain.ReportType
import com.example.domain.SchoolAcademicBundleForPending
import com.example.domain.SubjectTeacherAssignment
import com.example.domain.SubstitutionRecord
import com.example.domain.TeacherAccessScope
import com.example.domain.TeacherAttendanceEngine
import com.example.domain.TeacherAttendanceRecord
import com.example.domain.TeacherAttendanceStatus
import com.example.domain.TeacherLeaveRecord
import com.example.domain.TeacherMyDayEngine
import com.example.domain.TeacherProfile
import com.example.domain.TimetableCell
import com.example.domain.WorkloadReportEngine
import com.example.domain.WorkloadReportPeriodMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7DomainAndAccessTest {

    private val teacherProfile = TeacherProfile(
        id = "t_sharma",
        name = "Mrs. Sharma",
        schoolIds = listOf("sch_1", "sch_2"),
        subjects = listOf("Maths", "Science"),
        maxPeriodsPerDay = 6,
        maxConsecutivePeriods = 3,
        weeklyOffDays = listOf(DayOfWeekCode.SAT),
        active = true,
        linkedMemberUid = "uid_sharma"
    )

    private val teacherProfile2 = TeacherProfile(
        id = "t_verma",
        name = "Mr. Verma",
        schoolIds = listOf("sch_1"),
        subjects = listOf("English"),
        maxPeriodsPerDay = 6,
        maxConsecutivePeriods = 3,
        weeklyOffDays = emptyList(),
        active = true,
        linkedMemberUid = "uid_verma"
    )

    private val assignments = listOf(
        ClassSectionAssignment(
            id = "ca_3A",
            schoolId = "sch_1",
            className = "3",
            section = "A",
            classTeacherId = "t_sharma",
            subjectAssignments = listOf(
                SubjectTeacherAssignment("Maths", "t_sharma", 6),
                SubjectTeacherAssignment("English", "t_verma", 5)
            )
        ),
        ClassSectionAssignment(
            id = "ca_3B",
            schoolId = "sch_1",
            className = "3",
            section = "B",
            classTeacherId = "t_verma",
            subjectAssignments = listOf(
                SubjectTeacherAssignment("Maths", "t_verma", 6),
                SubjectTeacherAssignment("Science", "t_sharma", 4)
            )
        )
    )

    @Test
    fun accessEvaluatorEnforcesTeacherScopeOpenExamsAndClassTeacherOnlyCoScholastic() {
        val scopes = Phase7AccessEvaluator.buildTeacherScopes(listOf(teacherProfile, teacherProfile2), assignments)
        val sharmaScope = scopes["uid_sharma"]!!
        val vermaScope = scopes["uid_verma"]!!

        val teacherMember = MemberAccessProfile(
            uid = "uid_sharma",
            role = OrgRole.TEACHER,
            status = MemberStatus.ACTIVE,
            allSchools = false,
            allowedSchoolIds = listOf("sch_1"),
            permissions = MemberPermissions(
                marks = AccessLevel.EDIT,
                coScholastic = AccessLevel.EDIT,
                students = AccessLevel.VIEW
            )
        )
        val adminMember = MemberAccessProfile(
            uid = "uid_admin",
            role = OrgRole.ADMIN,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions.presetFor(OrgRole.ADMIN)
        )
        val operatorMember = MemberAccessProfile(
            uid = "uid_op",
            role = OrgRole.OFFICE_OPERATOR,
            status = MemberStatus.ACTIVE,
            allSchools = false,
            allowedSchoolIds = listOf("sch_1"),
            permissions = MemberPermissions(
                marks = AccessLevel.EDIT,
                coScholastic = AccessLevel.EDIT
            )
        )

        val openExamsOnlyUt1 = listOf(ExamType.UT1.name)

        // Teacher CAN edit Maths for Class 3-A on UT1 (assigned + exam open)
        assertTrue(
            Phase7AccessEvaluator.canEditMarks(
                member = teacherMember,
                teacherScope = sharmaScope,
                schoolDocId = "sch_1",
                className = "3",
                section = "A",
                subjectName = "Maths",
                openExamKeys = openExamsOnlyUt1,
                componentKey = MarkComponent.T1_UT.key
            )
        )

        // Teacher CANNOT edit Maths for Class 3-A on Half-Yearly Exam when only UT1 is open
        assertFalse(
            Phase7AccessEvaluator.canEditMarks(
                member = teacherMember,
                teacherScope = sharmaScope,
                schoolDocId = "sch_1",
                className = "3",
                section = "A",
                subjectName = "Maths",
                openExamKeys = openExamsOnlyUt1,
                componentKey = MarkComponent.T1_EXAM.key
            )
        )

        // Teacher CANNOT edit Maths for Class 3-B (different section!) or English for 3-A (unassigned subject)
        assertFalse(
            Phase7AccessEvaluator.canEditMarks(
                member = teacherMember,
                teacherScope = sharmaScope,
                schoolDocId = "sch_1",
                className = "3",
                section = "B",
                subjectName = "Maths",
                openExamKeys = Phase7AccessEvaluator.ALL_EXAM_KEYS,
                componentKey = MarkComponent.T1_UT.key
            )
        )
        assertFalse(
            Phase7AccessEvaluator.canEditMarks(
                member = teacherMember,
                teacherScope = sharmaScope,
                schoolDocId = "sch_1",
                className = "3",
                section = "A",
                subjectName = "English",
                openExamKeys = Phase7AccessEvaluator.ALL_EXAM_KEYS,
                componentKey = MarkComponent.T1_UT.key
            )
        )

        // Admin and Office Operator keep FULL access even when exams are closed for teachers
        assertTrue(
            Phase7AccessEvaluator.canEditMarks(
                member = adminMember,
                teacherScope = null,
                schoolDocId = "sch_1",
                className = "3",
                section = "B",
                subjectName = "English",
                openExamKeys = emptyList(),
                componentKey = MarkComponent.T2_EXAM.key
            )
        )
        assertTrue(
            Phase7AccessEvaluator.canEditMarks(
                member = operatorMember,
                teacherScope = null,
                schoolDocId = "sch_1",
                className = "3",
                section = "B",
                subjectName = "English",
                openExamKeys = emptyList(),
                componentKey = MarkComponent.T2_EXAM.key
            )
        )

        // Class Teacher co-scholastic & termInfo: Mrs. Sharma is CT of 3-A ONLY (subject teacher in 3-B)
        assertTrue(Phase7AccessEvaluator.canEditCoScholasticOrTermInfo(teacherMember, sharmaScope, "sch_1", "3", "A"))
        assertFalse(Phase7AccessEvaluator.canEditCoScholasticOrTermInfo(teacherMember, sharmaScope, "sch_1", "3", "B"))
        // Subject teacher (not CT of 3-A) cannot edit 3-A co-scholastic
        val vermaMember = teacherMember.copy(uid = "uid_verma")
        assertFalse(Phase7AccessEvaluator.canEditCoScholasticOrTermInfo(vermaMember, vermaScope, "sch_1", "3", "A"))
        assertTrue(Phase7AccessEvaluator.canEditCoScholasticOrTermInfo(vermaMember, vermaScope, "sch_1", "3", "B"))

        // Allowed classes/sections/subjects filtering
        assertEquals(
            listOf("Maths"),
            Phase7AccessEvaluator.allowedSubjectsForMarks(teacherMember, sharmaScope, "sch_1", "3", "A", listOf("Maths", "English", "Science"))
        )
        assertEquals(
            listOf("Science"),
            Phase7AccessEvaluator.allowedSubjectsForMarks(teacherMember, sharmaScope, "sch_1", "3", "B", listOf("Maths", "English", "Science"))
        )
    }

    @Test
    fun pendingMarksEngineCountsIncompletePerExamScopesToTeacherAndFormatsWhatsAppReminder() {
        val s1 = StudentEntity(id = 1L, schoolId = 10L, session = "2026-2027", admNo = "A1", name = "Aarav", fatherName = "Rajesh", motherName = "Sunita", dob = "2017-01-01", mobile = "9999999991", address = "Lucknow", className = "3", section = "A", rollNo = 1)
        val s2 = StudentEntity(id = 2L, schoolId = 10L, session = "2026-2027", admNo = "A2", name = "Diya", fatherName = "Amit", motherName = "Priya", dob = "2017-02-02", mobile = "9999999992", address = "Lucknow", className = "3", section = "A", rollNo = 2)
        val marks = listOf(
            // s1 has UT1 Maths, s2 missing UT1 Maths -> 1 of 2 entered
            MarkEntity(studentId = 1L, subjectName = "Maths", componentKey = MarkComponent.T1_UT.key, rawValue = "25"),
            // Both s1 & s2 have UT1 English -> complete (should NOT appear in pending UT1!)
            MarkEntity(studentId = 1L, subjectName = "English", componentKey = MarkComponent.T1_UT.key, rawValue = "22"),
            MarkEntity(studentId = 2L, subjectName = "English", componentKey = MarkComponent.T1_UT.key, rawValue = "24")
        )
        val bundle = SchoolAcademicBundleForPending(
            schoolDocId = "sch_1",
            schoolName = "Sunrise Primary",
            students = listOf(s1, s2),
            marks = marks,
            subjectsByClass = mapOf("3" to listOf("Maths", "English"))
        )

        val adminUt1Pending = PendingMarksEngine.computePendingItems(
            bundles = listOf(bundle),
            session = "2026-2027",
            classAssignments = assignments,
            teachers = listOf(teacherProfile, teacherProfile2),
            examFilter = ExamType.UT1
        )
        assertEquals(1, adminUt1Pending.size)
        val item = adminUt1Pending.first()
        assertEquals("Maths", item.subjectName)
        assertEquals(1, item.enteredStudents)
        assertEquals(2, item.totalStudents)
        assertEquals("Mrs. Sharma", item.responsibleTeacherName)
        assertEquals(
            "Mrs. Sharma: Class 3-A Maths UT1 marks are pending, 1 of 2 entered.",
            PendingMarksEngine.buildReminderWhatsAppText(item)
        )

        // Summary across all 4 exams: UT1 has 1 pending (Maths), UT2/HY/Annual have 2 pending each (Maths + English)
        val allPending = PendingMarksEngine.computePendingItems(
            bundles = listOf(bundle),
            session = "2026-2027",
            classAssignments = assignments,
            teachers = listOf(teacherProfile, teacherProfile2)
        )
        val summaryByExam = PendingMarksEngine.summarizeByExam(allPending).associateBy { it.exam }
        assertEquals(1, summaryByExam[ExamType.UT1]?.pendingTaskCount)
        assertEquals(2, summaryByExam[ExamType.UT2]?.pendingTaskCount)
        assertEquals(2, summaryByExam[ExamType.HALF_YEARLY]?.pendingTaskCount)
        assertEquals(2, summaryByExam[ExamType.ANNUAL]?.pendingTaskCount)

        // Teacher-scoped pending list hides English (assigned to Mr. Verma)
        val sharmaScope = Phase7AccessEvaluator.buildTeacherScopes(listOf(teacherProfile), assignments)["uid_sharma"]!!
        val sharmaPending = PendingMarksEngine.computePendingItems(
            bundles = listOf(bundle),
            session = "2026-2027",
            classAssignments = assignments,
            teachers = listOf(teacherProfile, teacherProfile2),
            restrictToTeacherScope = sharmaScope
        )
        assertTrue(sharmaPending.isNotEmpty())
        assertTrue(sharmaPending.all { it.subjectName == "Maths" })
    }

    @Test
    fun teacherMyDayEngineCombinesTodayPeriodsSubstitutionsWeeklyMapAndPendingTasks() {
        val struct = Phase4Validator.buildDefaultPeriodStructure("sch_1")
        val monP1 = struct.slotsFor(DayOfWeekCode.MON).first { it.type.countsAsTeachingPeriod }
        val tueP1 = struct.slotsFor(DayOfWeekCode.TUE).first { it.type.countsAsTeachingPeriod }

        val cells = listOf(
            TimetableCell(
                id = "c1", schoolId = "sch_1", session = "2026-2027",
                className = "3", section = "A", day = DayOfWeekCode.MON,
                slotId = monP1.id, subjectName = "Maths", teacherId = "t_sharma", teacherName = "Mrs. Sharma"
            ),
            TimetableCell(
                id = "c2", schoolId = "sch_1", session = "2026-2027",
                className = "3", section = "B", day = DayOfWeekCode.TUE,
                slotId = tueP1.id, subjectName = "Science", teacherId = "t_sharma", teacherName = "Mrs. Sharma"
            )
        )
        val subs = listOf(
            SubstitutionRecord(
                id = "sub1", date = "2026-11-23", day = DayOfWeekCode.MON, schoolId = "sch_1",
                session = "2026-2027", className = "3", section = "A", slotId = "MON_p2", slotLabel = "Period 2",
                startMinutes = 535, endMinutes = 575, subjectName = "English",
                absentTeacherId = "t_verma", absentTeacherName = "Mr. Verma",
                leaveId = "lv1", substituteTeacherId = "t_sharma", substituteTeacherName = "Mrs. Sharma"
            )
        )
        val scope = Phase7AccessEvaluator.buildTeacherScopes(listOf(teacherProfile), assignments)["uid_sharma"]
        val myDay = TeacherMyDayEngine.buildTeacherMyDay(
            teacher = teacherProfile,
            teacherScope = scope,
            dateIso = "2026-11-23", // Monday
            session = "2026-2027",
            timetableCells = cells,
            substitutions = subs,
            periodStructuresBySchool = mapOf("sch_1" to struct),
            schoolNamesById = mapOf("sch_1" to "Sunrise Primary"),
            pendingMarksForTeacher = emptyList()
        )
        assertEquals(DayOfWeekCode.MON, myDay.dayOfWeek)
        assertEquals(1, myDay.todayRegularPeriods.size)
        assertEquals("Maths", myDay.todayRegularPeriods.first().subjectName)
        assertEquals(1, myDay.todaySubstitutions.size)
        assertEquals("Mr. Verma", myDay.todaySubstitutions.first().replacingTeacherName)
        assertEquals(1, myDay.weeklyPeriodsByDay[DayOfWeekCode.TUE]?.size)
        assertEquals(1, myDay.classTeacherSections.size)
        assertEquals(Triple("sch_1", "3", "A"), myDay.classTeacherSections.first())
    }

    @Test
    fun workloadReportEngineComputesMultiSchoolLoadFreePeriodsSubstitutionsAndCsv() {
        val struct1 = Phase4Validator.buildDefaultPeriodStructure("sch_1")
        val struct2 = Phase4Validator.buildDefaultPeriodStructure("sch_2")
        val monP1 = struct1.slotsFor(DayOfWeekCode.MON).first { it.type.countsAsTeachingPeriod }
        val monP2 = struct1.slotsFor(DayOfWeekCode.MON).filter { it.type.countsAsTeachingPeriod }[1]
        val tueP1 = struct2.slotsFor(DayOfWeekCode.TUE).first { it.type.countsAsTeachingPeriod }

        val cells = listOf(
            TimetableCell(id = "c1", schoolId = "sch_1", session = "2026-2027", className = "3", section = "A", day = DayOfWeekCode.MON, slotId = monP1.id, subjectName = "Maths", teacherId = "t_sharma", teacherName = "Mrs. Sharma"),
            TimetableCell(id = "c2", schoolId = "sch_1", session = "2026-2027", className = "3", section = "A", day = DayOfWeekCode.MON, slotId = monP2.id, subjectName = "Maths", teacherId = "t_sharma", teacherName = "Mrs. Sharma"),
            TimetableCell(id = "c3", schoolId = "sch_2", session = "2026-2027", className = "5", section = "A", day = DayOfWeekCode.TUE, slotId = tueP1.id, subjectName = "Science", teacherId = "t_sharma", teacherName = "Mrs. Sharma")
        )
        val subs = listOf(
            // Same week as 2026-11-23 (Mon Nov 23..Sun Nov 29)
            SubstitutionRecord(id = "s1", date = "2026-11-24", day = DayOfWeekCode.TUE, schoolId = "sch_1", session = "2026-2027", className = "4", section = "A", slotId = "p3", subjectName = "Maths", absentTeacherId = "t_verma", substituteTeacherId = "t_sharma"),
            // Earlier in same month (Nov 2026, different week)
            SubstitutionRecord(id = "s2", date = "2026-11-10", day = DayOfWeekCode.TUE, schoolId = "sch_2", session = "2026-2027", className = "5", section = "B", slotId = "p4", subjectName = "Science", absentTeacherId = "t_verma", substituteTeacherId = "t_sharma")
        )

        val rows = WorkloadReportEngine.computeWorkloadReport(
            teachers = listOf(teacherProfile),
            timetableCells = cells,
            classAssignments = assignments,
            substitutions = subs,
            periodStructuresBySchool = mapOf("sch_1" to struct1, "sch_2" to struct2),
            schoolNamesById = mapOf("sch_1" to "Primary", "sch_2" to "Secondary"),
            session = "2026-2027",
            referenceDateIso = "2026-11-23"
        )
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(3, row.scheduledPeriodsPerWeek) // 2 in sch_1 + 1 in sch_2
        assertEquals(2, row.perSchoolScheduledPeriods["sch_1"])
        assertEquals(1, row.perSchoolScheduledPeriods["sch_2"])
        assertEquals(1, row.substitutionsThisWeek)
        assertEquals(2, row.substitutionsThisMonth)
        assertEquals(4, row.totalLoadThisWeek) // 3 + 1
        assertEquals(14, row.totalLoadThisMonth) // (3*4) + 2
        assertEquals(30, row.maxWeeklyCapacity) // 5 non-off days * 6 max/day
        assertEquals(27, row.freePeriodsPerWeek) // 30 - 3

        val csv = WorkloadReportEngine.buildWorkloadCsv(rows, WorkloadReportPeriodMode.WEEK, "2026-11-23")
        assertTrue(csv.contains("Mrs. Sharma"))
        assertTrue(csv.contains("Primary; Secondary"))
        assertTrue(csv.contains("3-A Maths (2)"))
    }

    @Test
    fun teacherAttendanceEngineAutoLinksApprovedLeaveBuildsAbsenceLeaveAndMonthlySummary() {
        val approvedLeave = TeacherLeaveRecord(
            id = "lv_1",
            teacherId = "t_sharma",
            teacherName = "Mrs. Sharma",
            startDate = "2026-11-23",
            endDate = "2026-11-24",
            reasonType = LeaveReason.SICK,
            halfDay = false,
            status = LeaveStatus.APPROVED
        )
        val explicitRecords = listOf(
            TeacherAttendanceRecord(
                id = "tatt_1",
                date = "2026-11-23",
                schoolId = "sch_1",
                teacherId = "t_verma",
                teacherName = "Mr. Verma",
                status = TeacherAttendanceStatus.PRESENT
            ),
            TeacherAttendanceRecord(
                id = "tatt_2",
                date = "2026-11-25",
                schoolId = "sch_1",
                teacherId = "t_sharma",
                teacherName = "Mrs. Sharma",
                status = TeacherAttendanceStatus.PRESENT
            ),
            TeacherAttendanceRecord(
                id = "tatt_3",
                date = "2026-11-26",
                schoolId = "sch_1",
                teacherId = "t_sharma",
                teacherName = "Mrs. Sharma",
                status = TeacherAttendanceStatus.HALF_DAY
            )
        )

        val daily = TeacherAttendanceEngine.buildDailySheet(
            schoolId = "sch_1",
            dateIso = "2026-11-23",
            teachers = listOf(teacherProfile, teacherProfile2),
            attendanceRecords = explicitRecords,
            approvedLeaves = listOf(approvedLeave)
        )
        val sharmaRow = daily.first { it.teacher.id == "t_sharma" }
        assertEquals(TeacherAttendanceStatus.ON_LEAVE, sharmaRow.effectiveStatus)
        assertTrue(sharmaRow.isAutoFromApprovedLeave)
        assertEquals("lv_1", sharmaRow.approvedLeave?.id)

        val vermaRow = daily.first { it.teacher.id == "t_verma" }
        assertEquals(TeacherAttendanceStatus.PRESENT, vermaRow.effectiveStatus)
        assertFalse(vermaRow.isAutoFromApprovedLeave)

        // 1-tap absence -> approved leave builder
        val autoLeave = TeacherAttendanceEngine.buildApprovedLeaveFromAttendanceAbsence(
            teacher = teacherProfile2,
            dateIso = "2026-11-25",
            actorUid = "uid_admin",
            actorName = "Admin"
        )
        assertEquals(LeaveStatus.APPROVED, autoLeave.status)
        assertEquals("2026-11-25", autoLeave.startDate)
        assertEquals("2026-11-25", autoLeave.endDate)

        // Monthly summary for Nov 2026: Sharma has 1 Present, 1 Half-Day, 2 On Leave (23 & 24)
        val struct = Phase4Validator.buildDefaultPeriodStructure("sch_1")
        val monthly = TeacherAttendanceEngine.buildMonthlySummary(
            schoolId = "sch_1",
            year = 2026,
            month1Based = 11,
            teachers = listOf(teacherProfile),
            periodStructure = struct,
            attendanceRecords = explicitRecords,
            approvedLeaves = listOf(approvedLeave)
        )
        assertEquals(1, monthly.size)
        val mRow = monthly.first()
        assertEquals(1, mRow.presentDays)
        assertEquals(1, mRow.halfDays)
        assertEquals(2, mRow.onLeaveDays)
        assertEquals(0, mRow.absentDays)
        // Marked = 4 days, effective present = 1.5 -> 37.5%
        assertEquals(37.5, mRow.attendancePercent, 0.01)
    }

    @Test
    fun reportReadinessEngineDetectsMissingMarksAttendanceCoScholasticAndRemarks() {
        val s1 = StudentEntity(id = 1L, schoolId = 1L, session = "2026-2027", admNo = "101", name = "Ready Student", fatherName = "Father 1", motherName = "Mother 1", dob = "2017-01-01", mobile = "9999999991", address = "Lucknow", className = "3", section = "A", rollNo = 1)
        val s2 = StudentEntity(id = 2L, schoolId = 1L, session = "2026-2027", admNo = "102", name = "Incomplete Student", fatherName = "Father 2", motherName = "Mother 2", dob = "2017-02-02", mobile = "9999999992", address = "Lucknow", className = "3", section = "A", rollNo = 2)

        val subjects = listOf("Maths")
        val marks = listOf(
            // s1 complete for Half-Yearly (T1_IA, T1_UT, T1_EXAM)
            MarkEntity(studentId = 1L, subjectName = "Maths", componentKey = MarkComponent.T1_IA.key, rawValue = "9"),
            MarkEntity(studentId = 1L, subjectName = "Maths", componentKey = MarkComponent.T1_UT.key, rawValue = "25"),
            MarkEntity(studentId = 1L, subjectName = "Maths", componentKey = MarkComponent.T1_EXAM.key, rawValue = "50"),
            // s2 missing T1_EXAM
            MarkEntity(studentId = 2L, subjectName = "Maths", componentKey = MarkComponent.T1_IA.key, rawValue = "8"),
            MarkEntity(studentId = 2L, subjectName = "Maths", componentKey = MarkComponent.T1_UT.key, rawValue = "20")
        )
        val co = listOf(
            CoScholasticEntity(studentId = 1L, termNo = 1, areaCode = CoScholasticArea.ART_EDUCATION.code, grade = "A1"),
            CoScholasticEntity(studentId = 1L, termNo = 1, areaCode = CoScholasticArea.DISCIPLINE.code, grade = "A1")
        )
        val termInfos = listOf(
            TermInfoEntity(studentId = 1L, termNo = 1, daysPresent = 85, workingDays = 90, remarks = "Excellent work")
        )

        val hyResult = ReportReadinessEngine.checkClassReadiness(
            className = "3",
            section = "A",
            reportType = ReportType.HALF_YEARLY,
            students = listOf(s1, s2),
            subjects = subjects,
            marks = marks,
            coScholastic = co,
            termInfos = termInfos
        )
        assertFalse(hyResult.isReady)
        assertEquals(1, hyResult.readyStudents)
        assertEquals(1, hyResult.issues.size)
        val issue = hyResult.issues.first()
        assertEquals("Incomplete Student", issue.studentName)
        assertEquals(listOf("Maths"), issue.missingMarkSubjects)
        assertTrue(issue.missingAttendance)
        assertEquals(2, issue.missingCoScholasticAreas.size)
        assertTrue(issue.missingRemarks)
        assertTrue(issue.summaryLine().contains("Marks: Maths"))
        assertTrue(issue.summaryLine().contains("Attendance"))
        assertTrue(issue.summaryLine().contains("Remarks"))

        // UT1 check only requires T1_UT -> both students have T1_UT so UT1 is READY!
        val ut1Result = ReportReadinessEngine.checkClassReadiness(
            className = "3",
            section = "A",
            reportType = ReportType.UT1,
            students = listOf(s1, s2),
            subjects = subjects,
            marks = marks,
            coScholastic = emptyList(),
            termInfos = emptyList()
        )
        assertTrue(ut1Result.isReady)
    }
}
