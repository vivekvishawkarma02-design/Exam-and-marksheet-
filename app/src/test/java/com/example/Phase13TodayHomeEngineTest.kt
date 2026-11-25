package com.example

import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.domain.AccessLevel
import com.example.domain.AdmissionEnquiryRecord
import com.example.domain.CashBookEntryRecord
import com.example.domain.CashBookEntryType
import com.example.domain.CashBookSettingsConfig
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionScope
import com.example.domain.DayCalendarStatus
import com.example.domain.DayOfWeekCode
import com.example.domain.EnquirySettingsConfig
import com.example.domain.EnquiryStatus
import com.example.domain.ExamCategoryType
import com.example.domain.ExamInvigilatorDutyRecord
import com.example.domain.ExamScheduleEntry
import com.example.domain.ExamScheduleRecord
import com.example.domain.FeeLineItemType
import com.example.domain.FeeMessageSentLog
import com.example.domain.FeeMessageType
import com.example.domain.FeeReceiptLineItem
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeReceiptStatus
import com.example.domain.FeeStructureConfig
import com.example.domain.FirstTimeGuideStepId
import com.example.domain.LeaveReason
import com.example.domain.LeaveStatus
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OptionalFeeCatalogueItem
import com.example.domain.OrgRole
import com.example.domain.PaymentMode
import com.example.domain.Phase13TodayHomeEngine
import com.example.domain.Phase4Validator
import com.example.domain.SalaryMonthlyRecord
import com.example.domain.SalaryPaymentStatus
import com.example.domain.SchoolTodayAttendanceSummary
import com.example.domain.SectionDailyAttendanceStatus
import com.example.domain.StockTransactionRecord
import com.example.domain.SubstitutionRecord
import com.example.domain.TeacherAccessScope
import com.example.domain.TeacherLeaveRecord
import com.example.domain.TeacherProfile
import com.example.domain.TimetableCell
import com.example.domain.TodayHomePermissions
import com.example.domain.TodayHomePersona
import com.example.domain.TodayQuickActionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase13TodayHomeEngineTest {

    private val todayIso = "2026-07-15" // Wednesday
    private val schoolId = "sch_1"
    private val session = "2026-27"

    private val school = SchoolEntity(
        id = 1L,
        name = "Sunrise Public School",
        address = "Lucknow",
        logoBase64 = "iVBORw0KGgo="
    )

    private val class5 = SchoolClassEntity(
        id = 1L,
        schoolId = 1L,
        classGroupId = 1L,
        name = "5",
        sortOrder = 1
    )

    private val ownerProfile = MemberAccessProfile(
        uid = "u_owner",
        role = OrgRole.OWNER,
        status = MemberStatus.ACTIVE,
        allSchools = true,
        allowedSchoolIds = emptyList(),
        permissions = MemberPermissions.presetFor(OrgRole.OWNER)
    )

    private val ownerPerms = TodayHomePermissions(
        isOwnerOrAdmin = true,
        isTeacher = false,
        canAccessStudentAttendance = true,
        canViewAttendanceOverview = true,
        canViewFees = true,
        canEditFees = true,
        canViewCashBook = true,
        canViewTimetable = true,
        canViewTeachers = true,
        canViewEnquiry = true,
        canViewStock = true,
        canViewExams = true,
        canViewMarks = true,
        canEditMarks = true,
        canViewStudents = true,
        canEditStudents = true,
        canViewStudentRegister = true,
        canViewCertificates = true,
        canEditSettings = true
    )

    private val feeStructure = FeeStructureConfig(
        id = "fs_1",
        schoolId = schoolId,
        session = session,
        chargedTuitionMonths = listOf("APR", "MAY", "JUN", "JUL"),
        tuitionByClassAndMonth = mapOf(
            "5" to mapOf("APR" to 1000, "MAY" to 1000, "JUN" to 1000, "JUL" to 1000)
        ),
        optionalCatalogue = listOf(
            OptionalFeeCatalogueItem(
                id = "item_tie",
                name = "School Tie",
                price = 120,
                openingStock = 5,
                lowStockLevel = 4
            ),
            OptionalFeeCatalogueItem(
                id = "item_belt",
                name = "School Belt",
                price = 150,
                openingStock = 30,
                lowStockLevel = 5
            )
        ),
        updatedByUid = "u_owner",
        clientEditedAtMs = 1_700_000_000_000L
    )

    private fun makeStudent(
        id: Long,
        name: String,
        admNo: String,
        rollNo: Int,
        mobile: String,
        fatherName: String = "Parent $id",
        section: String = "A",
        status: String = "ACTIVE"
    ) = StudentEntity(
        id = id,
        schoolId = 1L,
        session = session,
        admNo = admNo,
        name = name,
        fatherName = fatherName,
        motherName = "Mother $id",
        dob = "2015-04-10",
        mobile = mobile,
        address = "Lucknow",
        className = "5",
        section = section,
        rollNo = rollNo,
        status = status
    )

    private fun makeReceipt(
        id: String,
        receiptNumber: String,
        studentId: String,
        studentName: String,
        dateIso: String,
        amountPaid: Int,
        lineItems: List<FeeReceiptLineItem> = emptyList(),
        cancelled: Boolean = false
    ) = FeeReceiptRecord(
        id = id,
        schoolId = schoolId,
        session = session,
        receiptNumber = receiptNumber,
        deviceNodeCode = "A1",
        sequenceNo = 1,
        receiptDateIso = dateIso,
        studentId = studentId,
        studentName = studentName,
        studentAdmNo = "ADM-101",
        fatherName = "Guardian",
        className = "5",
        section = "A",
        rollNo = 1,
        lineItems = lineItems,
        totalBeforeConcession = amountPaid,
        concessionMode = ConcessionMode.NONE,
        concessionScope = ConcessionScope.WHOLE_RECEIPT,
        netPayableAmount = amountPaid,
        amountPaid = amountPaid,
        balanceDueOnReceipt = 0,
        paymentMode = PaymentMode.CASH,
        status = if (cancelled) FeeReceiptStatus.CANCELLED else FeeReceiptStatus.ISSUED,
        receivedByUid = "u_owner",
        receivedByName = "Owner",
        issuedAtMs = 1_700_000_000_000L
    )

    private fun makeDuty(
        id: String,
        dateIso: String,
        teacherId: String,
        teacherName: String,
        teacherUid: String = ""
    ) = ExamInvigilatorDutyRecord(
        id = id,
        schoolId = schoolId,
        session = session,
        examId = "ex_1",
        examTitle = "UT-I",
        dateIso = dateIso,
        startTime = "09:00",
        endTime = "11:00",
        roomId = "r1",
        roomName = "Room 1",
        teacherId = teacherId,
        teacherName = teacherName,
        teacherUid = teacherUid
    )

    private val stu1 = makeStudent(101L, "Aarav Sharma", "ADM-101", 1, "9876543210", "Rajesh Sharma")
    private val stu2 = makeStudent(102L, "Ananya Verma", "ADM-102", 2, "9123456789", "Suresh Verma")
    private val stuLeft = makeStudent(103L, "Old Student", "ADM-103", 3, "9000000000", status = "LEFT")

    @Test
    fun ownerTiles_matchUnderlyingDataAcrossAllPhases() {
        val periodStructure = Phase4Validator.buildDefaultPeriodStructure(schoolId)
        val teachingSlots = periodStructure.slotsFor(DayOfWeekCode.WED).filter { it.type.countsAsTeachingPeriod }
        val slot1 = teachingSlots[0].id
        val slot2 = teachingSlots[1].id

        val teacherA = TeacherProfile(id = "t_1", name = "Meera Joshi", schoolIds = listOf(schoolId), active = true)
        val teacherB = TeacherProfile(id = "t_2", name = "Rohan Gupta", schoolIds = listOf(schoolId), active = true)

        val attSummary = SchoolTodayAttendanceSummary(
            schoolDocId = schoolId,
            schoolName = school.name,
            date = todayIso,
            dayStatus = DayCalendarStatus.Working(),
            totalSections = 2,
            sectionsTaken = 1,
            totalMarkedStudents = 20,
            weightedPresentStudents = 16.0,
            sectionStatuses = listOf(
                SectionDailyAttendanceStatus(schoolId, school.name, "5", "A", todayIso, taken = true, totalStudents = 20, presentEquivalent = 16, halfDayCount = 0, absentCount = 4, leaveCount = 0),
                SectionDailyAttendanceStatus(schoolId, school.name, "6", "A", todayIso, taken = false, totalStudents = 18, presentEquivalent = 0, halfDayCount = 0, absentCount = 0, leaveCount = 0)
            )
        )

        val receipts = listOf(
            makeReceipt(
                id = "rcp_1",
                receiptNumber = "SCH-2026-A1-0001",
                studentId = "stu_101",
                studentName = stu1.name,
                dateIso = todayIso,
                amountPaid = 1240,
                lineItems = listOf(
                    FeeReceiptLineItem(
                        lineId = "l1",
                        lineType = FeeLineItemType.GRID_CELL,
                        monthCode = "APR",
                        headKey = "TUITION",
                        title = "Apr Tuition",
                        quantity = 1,
                        unitPrice = 1000,
                        lineGrossAmount = 1000
                    ),
                    FeeReceiptLineItem(
                        lineId = "l2",
                        lineType = FeeLineItemType.OPTIONAL_ITEM,
                        monthCode = "JUL",
                        headKey = "OPTIONAL_item_tie",
                        title = "School Tie",
                        quantity = 2,
                        unitPrice = 120,
                        lineGrossAmount = 240,
                        catalogueItemId = "item_tie"
                    )
                ),
                cancelled = false
            ),
            makeReceipt(
                id = "rcp_cancelled",
                receiptNumber = "SCH-2026-A1-0002",
                studentId = "stu_102",
                studentName = stu2.name,
                dateIso = todayIso,
                amountPaid = 2000,
                cancelled = true
            )
        )

        val cashBookEntries = listOf(
            CashBookEntryRecord(
                id = "cb_exp_1",
                schoolId = schoolId,
                dateIso = todayIso,
                entryType = CashBookEntryType.EXPENSE,
                category = "Electricity",
                amount = 500,
                paymentMode = PaymentMode.CASH
            )
        )
        val paidSalaries = listOf(
            SalaryMonthlyRecord(
                id = "sal_2026-07_t_2",
                schoolId = schoolId,
                yearMonth = "2026-07",
                staffId = "t_2",
                staffName = "Rohan Gupta",
                netPayableRs = 15000,
                status = SalaryPaymentStatus.PAID,
                paidDateIso = todayIso,
                paymentMode = PaymentMode.UPI
            )
        )

        val leaves = listOf(
            TeacherLeaveRecord(
                id = "lv_1",
                teacherId = "t_1",
                teacherName = "Meera Joshi",
                startDate = todayIso,
                endDate = todayIso,
                reasonType = LeaveReason.SICK,
                halfDay = false,
                status = LeaveStatus.APPROVED
            )
        )
        val ttCells = listOf(
            TimetableCell(id = "c1", schoolId = schoolId, session = session, className = "5", section = "A", day = DayOfWeekCode.WED, slotId = slot1, subjectName = "Maths", teacherId = "t_1"),
            TimetableCell(id = "c2", schoolId = schoolId, session = session, className = "6", section = "A", day = DayOfWeekCode.WED, slotId = slot2, subjectName = "Science", teacherId = "t_1")
        )
        val subs = listOf(
            SubstitutionRecord(
                id = SubstitutionRecord.makeSubstitutionDocId(todayIso, schoolId, "5", "A", slot1),
                date = todayIso,
                schoolId = schoolId,
                session = session,
                className = "5",
                section = "A",
                day = DayOfWeekCode.WED,
                slotId = slot1,
                subjectName = "Maths",
                absentTeacherId = "t_1",
                substituteTeacherId = "t_2",
                substituteTeacherName = "Rohan Gupta"
            )
        )

        val enquiries = listOf(
            AdmissionEnquiryRecord("e1", schoolId, session, "Kabir", "5", "Amit", "9811111111", enquiryDateIso = "2026-07-10", nextFollowUpDateIso = "2026-07-13", status = EnquiryStatus.CONTACTED),
            AdmissionEnquiryRecord("e2", schoolId, session, "Diya", "5", "Neha", "9822222222", enquiryDateIso = "2026-07-12", nextFollowUpDateIso = todayIso, status = EnquiryStatus.NEW),
            AdmissionEnquiryRecord("e3", schoolId, session, "Riya", "5", "Vikas", "9833333333", enquiryDateIso = "2026-07-14", nextFollowUpDateIso = "2026-07-20", status = EnquiryStatus.NEW)
        )

        val examSchedules = listOf(
            ExamScheduleRecord(
                id = "ex_1",
                schoolId = schoolId,
                session = session,
                examType = ExamCategoryType.UT_I,
                title = "UT-I",
                entries = listOf(
                    ExamScheduleEntry("e1", "5", "Maths", "2026-07-16", "09:00", "11:00"),
                    ExamScheduleEntry("e2", "5", "English", "2026-07-18", "09:00", "11:00"),
                    ExamScheduleEntry("e3", "5", "Hindi", "2026-08-05", "09:00", "11:00")
                )
            )
        )
        val examDuties = listOf(
            makeDuty("d1", "2026-07-16", "t_2", "Rohan Gupta"),
            makeDuty("d2", "2026-08-05", "t_2", "Rohan Gupta")
        )

        val snap = Phase13TodayHomeEngine.buildSnapshot(
            todayIso = todayIso,
            member = ownerProfile,
            permissions = ownerPerms,
            schools = listOf(school),
            activeSchool = school,
            activeSchoolDocId = schoolId,
            schoolNamesByDocId = mapOf(schoolId to school.name),
            session = session,
            schoolClassesForActiveSchool = listOf(class5),
            sessionStudentsForActiveSchool = listOf(stu1, stu2, stuLeft),
            studentsWithDocIdsForActiveSchool = listOf("stu_101" to stu1, "stu_102" to stu2, "stu_103" to stuLeft),
            todayAttendanceSummaries = listOf(attSummary),
            feeStructures = listOf(feeStructure),
            activeFeeStructure = feeStructure,
            studentFeeOverrides = emptyList(),
            feeReceipts = receipts,
            feeMessageSentLogs = emptyList(),
            cashBookEntries = cashBookEntries,
            cashBookDayClosings = emptyList(),
            cashBookSettings = CashBookSettingsConfig.buildDefault(schoolId),
            salaryMonthlyRecords = paidSalaries,
            admissionEnquiries = enquiries,
            enquirySettings = EnquirySettingsConfig.buildDefault(schoolId),
            teachers = listOf(teacherA, teacherB),
            teacherLeaves = leaves,
            teacherAttendanceRecords = emptyList(),
            timetableCells = ttCells,
            substitutions = subs,
            periodStructuresBySchool = mapOf(schoolId to periodStructure),
            classAssignments = emptyList(),
            ownLinkedTeacher = null,
            ownTeacherScope = null,
            ownPendingMarksCount = 0,
            ownExamDuties = emptyList(),
            ownSalarySlipsCount = 0,
            examSchedules = examSchedules,
            examDuties = examDuties,
            stockTransactions = emptyList<StockTransactionRecord>(),
            isFirstTimeGuideSkipped = false
        )

        assertEquals(TodayHomePersona.OWNER_ADMIN, snap.persona)
        val t = snap.ownerAdminTiles
        assertTrue(t.showAttendanceTile)
        assertEquals(1, t.totalSectionsTakenAcrossSchools)
        assertEquals(2, t.totalSectionsAcrossSchools)
        assertEquals(1, t.missingAttendanceSections.size)
        assertEquals("6", t.missingAttendanceSections.first().className)

        assertTrue(t.showFeeCollectionTile)
        assertEquals(1240, t.todayFeeCollectionRs)
        assertEquals(1, t.todayFeeReceiptCount)

        assertTrue(t.showExpensesTile)
        assertEquals(15500, t.todayExpensesRs)
        assertEquals(2, t.todayExpenseCount)

        assertTrue(t.showGuardiansDuesTile)
        assertEquals(2, t.guardiansWithDuesCount)

        assertTrue(t.showTeacherAbsenceTile)
        assertEquals(1, t.absentTeachersTodayCount)
        assertEquals(1, t.pendingSubstitutionsTodayCount)

        assertTrue(t.showEnquiryFollowUpTile)
        assertEquals(2, t.enquiryFollowUpsDueCount)
        assertEquals(1, t.enquiryOverdueCount)
        assertEquals(1, t.enquiryDueTodayCount)

        assertTrue(t.showLowStockTile)
        assertEquals(1, t.lowStockItemCount)
        assertTrue(t.lowStockTopNames.first().contains("School Tie (3)"))

        assertTrue(t.showUpcomingExamsTile)
        assertEquals(2, t.upcomingExamEntriesThisWeek.size)
        assertEquals(1, t.upcomingExamDutiesThisWeekCount)

        assertEquals(3, snap.quickActions.size)
        assertTrue(snap.firstTimeGuide.isAllDone)
    }

    @Test
    fun roleBasedVisibility_hidesUnauthorizedTilesForRestrictedAdminTeacherAndFeeClerk() {
        val restrictedAdmin = MemberAccessProfile(
            uid = "u_admin_limited",
            role = OrgRole.ADMIN,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions(fees = AccessLevel.EDIT, attendance = AccessLevel.VIEW)
        )
        val limitedPerms = TodayHomePermissions(
            isOwnerOrAdmin = true,
            isTeacher = false,
            canAccessStudentAttendance = true,
            canViewAttendanceOverview = true,
            canViewFees = true,
            canEditFees = true,
            canViewCashBook = false,
            canViewTimetable = false,
            canViewTeachers = false,
            canViewEnquiry = false,
            canViewStock = false,
            canViewExams = false,
            canViewMarks = false,
            canEditMarks = false,
            canViewStudents = false,
            canEditStudents = false,
            canViewStudentRegister = false,
            canViewCertificates = false,
            canEditSettings = false
        )
        val limitedSnap = Phase13TodayHomeEngine.buildSnapshot(
            todayIso = todayIso,
            member = restrictedAdmin,
            permissions = limitedPerms,
            schools = listOf(school),
            activeSchool = school,
            activeSchoolDocId = schoolId,
            schoolNamesByDocId = mapOf(schoolId to school.name),
            session = session,
            schoolClassesForActiveSchool = emptyList(),
            sessionStudentsForActiveSchool = listOf(stu1),
            studentsWithDocIdsForActiveSchool = listOf("stu_101" to stu1),
            todayAttendanceSummaries = emptyList(),
            feeStructures = listOf(feeStructure),
            activeFeeStructure = feeStructure,
            studentFeeOverrides = emptyList(),
            feeReceipts = emptyList(),
            feeMessageSentLogs = emptyList(),
            cashBookEntries = listOf(CashBookEntryRecord("cb1", schoolId, todayIso, CashBookEntryType.EXPENSE, "Rent", 5000)),
            cashBookDayClosings = emptyList(),
            cashBookSettings = CashBookSettingsConfig.buildDefault(schoolId),
            salaryMonthlyRecords = emptyList(),
            admissionEnquiries = listOf(AdmissionEnquiryRecord("e1", schoolId, session, "Child", "5", "Parent", "9999999999", enquiryDateIso = todayIso, nextFollowUpDateIso = todayIso)),
            enquirySettings = EnquirySettingsConfig.buildDefault(schoolId),
            teachers = emptyList(),
            teacherLeaves = emptyList(),
            teacherAttendanceRecords = emptyList(),
            timetableCells = emptyList(),
            substitutions = emptyList(),
            periodStructuresBySchool = emptyMap(),
            classAssignments = emptyList(),
            ownLinkedTeacher = null,
            ownTeacherScope = null,
            ownPendingMarksCount = 0,
            ownExamDuties = emptyList(),
            ownSalarySlipsCount = 0,
            examSchedules = emptyList(),
            examDuties = emptyList(),
            stockTransactions = emptyList(),
            isFirstTimeGuideSkipped = false
        )
        assertTrue(limitedSnap.ownerAdminTiles.showAttendanceTile)
        assertTrue(limitedSnap.ownerAdminTiles.showFeeCollectionTile)
        assertFalse(limitedSnap.ownerAdminTiles.showExpensesTile)
        assertEquals(0, limitedSnap.ownerAdminTiles.todayExpensesRs)
        assertFalse(limitedSnap.ownerAdminTiles.showEnquiryFollowUpTile)
        assertEquals(0, limitedSnap.ownerAdminTiles.enquiryFollowUpsDueCount)
        assertFalse(limitedSnap.ownerAdminTiles.showLowStockTile)
        assertFalse(limitedSnap.ownerAdminTiles.showUpcomingExamsTile)
        assertFalse(limitedSnap.ownerAdminTiles.showTeacherAbsenceTile)

        val teacherMember = MemberAccessProfile(
            uid = "u_teacher",
            role = OrgRole.TEACHER,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions(marks = AccessLevel.EDIT, timetable = AccessLevel.VIEW)
        )
        val teacherPerms = limitedPerms.copy(
            isOwnerOrAdmin = false,
            isTeacher = true,
            canViewAttendanceOverview = false,
            canViewFees = false,
            canEditFees = false,
            canViewMarks = true,
            canEditMarks = true,
            canViewTimetable = true
        )
        val teacherSnap = Phase13TodayHomeEngine.buildSnapshot(
            todayIso = todayIso,
            member = teacherMember,
            permissions = teacherPerms,
            schools = listOf(school),
            activeSchool = school,
            activeSchoolDocId = schoolId,
            schoolNamesByDocId = mapOf(schoolId to school.name),
            session = session,
            schoolClassesForActiveSchool = emptyList(),
            sessionStudentsForActiveSchool = listOf(stu1),
            studentsWithDocIdsForActiveSchool = listOf("stu_101" to stu1),
            todayAttendanceSummaries = emptyList(),
            feeStructures = listOf(feeStructure),
            activeFeeStructure = feeStructure,
            studentFeeOverrides = emptyList(),
            feeReceipts = emptyList(),
            feeMessageSentLogs = emptyList(),
            cashBookEntries = emptyList(),
            cashBookDayClosings = emptyList(),
            cashBookSettings = CashBookSettingsConfig.buildDefault(schoolId),
            salaryMonthlyRecords = emptyList(),
            admissionEnquiries = emptyList(),
            enquirySettings = EnquirySettingsConfig.buildDefault(schoolId),
            teachers = listOf(TeacherProfile("t_1", "Meera Joshi", schoolIds = listOf(schoolId))),
            teacherLeaves = listOf(
                TeacherLeaveRecord("lv1", "t_1", "Meera Joshi", "2026-07-20", "2026-07-21", LeaveReason.CASUAL_LEAVE, halfDay = false, status = LeaveStatus.PENDING),
                TeacherLeaveRecord("lv_other", "t_99", "Other Teacher", "2026-07-20", "2026-07-21", LeaveReason.SICK, halfDay = false, status = LeaveStatus.APPROVED)
            ),
            teacherAttendanceRecords = emptyList(),
            timetableCells = emptyList(),
            substitutions = emptyList(),
            periodStructuresBySchool = emptyMap(),
            classAssignments = emptyList(),
            ownLinkedTeacher = TeacherProfile("t_1", "Meera Joshi", schoolIds = listOf(schoolId)),
            ownTeacherScope = TeacherAccessScope("u_teacher", "t_1", classTeacherSections = listOf("$schoolId|5|A")),
            ownPendingMarksCount = 3,
            ownExamDuties = listOf(makeDuty("d1", "2026-07-17", "t_1", "Meera Joshi", "u_teacher")),
            ownSalarySlipsCount = 1,
            examSchedules = emptyList(),
            examDuties = emptyList(),
            stockTransactions = emptyList(),
            isFirstTimeGuideSkipped = false
        )
        assertEquals(TodayHomePersona.TEACHER, teacherSnap.persona)
        assertFalse(teacherSnap.ownerAdminTiles.showFeeCollectionTile)
        assertFalse(teacherSnap.ownerAdminTiles.showExpensesTile)
        assertNull(teacherSnap.feeClerkData)
        assertNotNull(teacherSnap.teacherData)
        val tData = teacherSnap.teacherData!!
        assertEquals(1, tData.classAttendanceTargets.size)
        assertEquals(3, tData.pendingMarksCount)
        assertEquals(1, tData.upcomingExamDutiesCount)
        assertEquals(1, tData.leaveStatus.pendingCount)
        assertEquals(0, tData.leaveStatus.approvedCount)
        assertFalse(teacherSnap.firstTimeGuide.isEligibleForRole)
        assertEquals(TodayQuickActionId.TAKE_STUDENT_ATTENDANCE, teacherSnap.quickActions.first().id)

        val feeClerkMember = MemberAccessProfile(
            uid = "u_clerk",
            role = OrgRole.OFFICE_OPERATOR,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions(fees = AccessLevel.EDIT)
        )
        val clerkPerms = limitedPerms.copy(
            isOwnerOrAdmin = false,
            canAccessStudentAttendance = false,
            canViewAttendanceOverview = false,
            canViewFees = true,
            canEditFees = true
        )
        val sentLog = FeeMessageSentLog(
            id = "log_1",
            schoolId = schoolId,
            session = session,
            studentId = "stu_101",
            studentName = stu1.name,
            className = stu1.className,
            section = stu1.section,
            guardianName = stu1.fatherName,
            normalizedMobile = "919876543210",
            messageType = FeeMessageType.FEE_DUE_REMINDER,
            contextKey = "${FeeMessageType.FEE_DUE_REMINDER.wireValue}_2026-07_4000",
            messageText = "Fee reminder",
            channel = "WHATSAPP",
            sentByUid = "u_clerk",
            sentByName = "Clerk",
            sentAtMs = 1_700_000_000_000L
        )
        val clerkSnap = Phase13TodayHomeEngine.buildSnapshot(
            todayIso = todayIso,
            member = feeClerkMember,
            permissions = clerkPerms,
            schools = listOf(school),
            activeSchool = school,
            activeSchoolDocId = schoolId,
            schoolNamesByDocId = mapOf(schoolId to school.name),
            session = session,
            schoolClassesForActiveSchool = emptyList(),
            sessionStudentsForActiveSchool = listOf(stu1, stu2),
            studentsWithDocIdsForActiveSchool = listOf("stu_101" to stu1, "stu_102" to stu2),
            todayAttendanceSummaries = emptyList(),
            feeStructures = listOf(feeStructure),
            activeFeeStructure = feeStructure,
            studentFeeOverrides = emptyList(),
            feeReceipts = listOf(makeReceipt("rcp_today", "SCH-01", "stu_101", stu1.name, todayIso, 1000)),
            feeMessageSentLogs = listOf(sentLog),
            cashBookEntries = emptyList(),
            cashBookDayClosings = emptyList(),
            cashBookSettings = CashBookSettingsConfig.buildDefault(schoolId),
            salaryMonthlyRecords = emptyList(),
            admissionEnquiries = emptyList(),
            enquirySettings = EnquirySettingsConfig.buildDefault(schoolId),
            teachers = emptyList(),
            teacherLeaves = emptyList(),
            teacherAttendanceRecords = emptyList(),
            timetableCells = emptyList(),
            substitutions = emptyList(),
            periodStructuresBySchool = emptyMap(),
            classAssignments = emptyList(),
            ownLinkedTeacher = null,
            ownTeacherScope = null,
            ownPendingMarksCount = 0,
            ownExamDuties = emptyList(),
            ownSalarySlipsCount = 0,
            examSchedules = emptyList(),
            examDuties = emptyList(),
            stockTransactions = emptyList(),
            isFirstTimeGuideSkipped = false
        )
        assertEquals(TodayHomePersona.FEE_CLERK, clerkSnap.persona)
        assertNotNull(clerkSnap.feeClerkData)
        val cData = clerkSnap.feeClerkData!!
        assertEquals(1000, cData.todayCollectionRs)
        assertEquals(1, cData.todayReceiptCount)
        assertEquals(1, cData.unsentDuesQueueCount)
        assertFalse(clerkSnap.ownerAdminTiles.showAttendanceTile)
        assertFalse(clerkSnap.ownerAdminTiles.showEnquiryFollowUpTile)
        assertEquals(TodayQuickActionId.COLLECT_FEE, clerkSnap.quickActions[0].id)
        assertEquals(TodayQuickActionId.SEND_FEE_QUEUE, clerkSnap.quickActions[1].id)
    }

    @Test
    fun feeClerkSearch_findsByNameAdmissionNumberAndMobile_excludesLeftStudents() {
        val list = listOf(stu1, stu2, stuLeft)

        assertEquals(listOf(stu1), Phase13TodayHomeEngine.searchStudentsForFeeClerk(list, "Aarav"))
        assertEquals(listOf(stu2), Phase13TodayHomeEngine.searchStudentsForFeeClerk(list, "adm-102"))
        assertEquals(listOf(stu1), Phase13TodayHomeEngine.searchStudentsForFeeClerk(list, "3210"))
        assertTrue(Phase13TodayHomeEngine.searchStudentsForFeeClerk(list, "Old Student").isEmpty())
    }

    @Test
    fun firstTimeChecklist_autoTicksEveryStepFromZeroToEight_andSupportsSkipReopen() {
        val s0 = Phase13TodayHomeEngine.buildFirstTimeGuide(
            schools = emptyList(),
            activeSchool = null,
            activeSchoolDocId = "",
            session = session,
            schoolClassesForActiveSchool = emptyList(),
            sessionStudentsForActiveSchool = emptyList(),
            feeStructures = emptyList(),
            teachers = emptyList(),
            timetableCells = emptyList(),
            feeReceipts = emptyList(),
            permissions = ownerPerms,
            isSkipped = false
        )
        assertEquals(0, s0.completedCount)
        assertEquals(8, s0.totalCount)
        assertTrue(s0.shouldShowExpandedCard)
        assertEquals(FirstTimeGuideStepId.CREATE_SCHOOL, s0.nextPendingStep?.id)

        val s0Skipped = s0.copy(isSkipped = true)
        assertFalse(s0Skipped.shouldShowExpandedCard)
        assertTrue(s0Skipped.shouldShowReopenChip)

        val schoolNoLogo = school.copy(logoBase64 = "")
        val s1 = Phase13TodayHomeEngine.buildFirstTimeGuide(
            schools = listOf(schoolNoLogo),
            activeSchool = schoolNoLogo,
            activeSchoolDocId = schoolId,
            session = session,
            schoolClassesForActiveSchool = emptyList(),
            sessionStudentsForActiveSchool = emptyList(),
            feeStructures = emptyList(),
            teachers = emptyList(),
            timetableCells = emptyList(),
            feeReceipts = emptyList(),
            permissions = ownerPerms,
            isSkipped = false
        )
        assertEquals(1, s1.completedCount)
        assertEquals(FirstTimeGuideStepId.ADD_LOGO, s1.nextPendingStep?.id)

        val s2 = Phase13TodayHomeEngine.buildFirstTimeGuide(
            schools = listOf(school),
            activeSchool = school,
            activeSchoolDocId = schoolId,
            session = session,
            schoolClassesForActiveSchool = emptyList(),
            sessionStudentsForActiveSchool = emptyList(),
            feeStructures = emptyList(),
            teachers = emptyList(),
            timetableCells = emptyList(),
            feeReceipts = emptyList(),
            permissions = ownerPerms,
            isSkipped = false
        )
        assertEquals(2, s2.completedCount)
        assertEquals(FirstTimeGuideStepId.CREATE_CLASSES_SECTIONS, s2.nextPendingStep?.id)

        val s8 = Phase13TodayHomeEngine.buildFirstTimeGuide(
            schools = listOf(school),
            activeSchool = school,
            activeSchoolDocId = schoolId,
            session = session,
            schoolClassesForActiveSchool = listOf(class5),
            sessionStudentsForActiveSchool = listOf(stu1),
            feeStructures = listOf(feeStructure),
            teachers = listOf(TeacherProfile("t1", "Teacher 1", schoolIds = listOf(schoolId), active = true)),
            timetableCells = listOf(TimetableCell("c1", schoolId, session, "5", "A", DayOfWeekCode.MON, "p1", "Maths", "t1")),
            feeReceipts = listOf(makeReceipt("r1", "SCH-1", "stu_101", stu1.name, todayIso, 1000)),
            permissions = ownerPerms,
            isSkipped = false
        )
        assertEquals(8, s8.completedCount)
        assertTrue(s8.isAllDone)
        assertFalse(s8.shouldShowExpandedCard)
        assertFalse(s8.shouldShowReopenChip)
    }

    @Test
    fun offlineHomeSnapshotComputation_isFastUnderRealisticSchoolLoad() {
        val periodStructure = Phase4Validator.buildDefaultPeriodStructure(schoolId)
        val manyStudents = (1..300).map { i ->
            makeStudent(
                id = i.toLong(),
                name = "Student $i",
                admNo = "ADM-${1000 + i}",
                rollNo = i,
                mobile = "98765${10000 + i}",
                section = if (i % 2 == 0) "A" else "B"
            )
        }
        val manyStudentsWithIds = manyStudents.map { "stu_${it.id}" to it }
        val manyReceipts = (1..150).map { i ->
            makeReceipt(
                id = "rcp_$i",
                receiptNumber = "SCH-$i",
                studentId = "stu_$i",
                studentName = "Student $i",
                dateIso = if (i <= 20) todayIso else "2026-07-10",
                amountPaid = 1000
            )
        }
        val manyTeachers = (1..20).map { i ->
            TeacherProfile(id = "t_$i", name = "Teacher $i", schoolIds = listOf(schoolId), active = true)
        }
        val manyEnquiries = (1..30).map { i ->
            AdmissionEnquiryRecord(
                id = "enq_$i",
                schoolId = schoolId,
                session = session,
                childName = "Child $i",
                classWanted = "5",
                guardianName = "Parent $i",
                mobile = "98123${10000 + i}",
                enquiryDateIso = "2026-07-01",
                nextFollowUpDateIso = if (i <= 10) todayIso else "2026-07-25"
            )
        }

        // Warmup JVM JIT
        repeat(2) {
            Phase13TodayHomeEngine.buildSnapshot(
                todayIso = todayIso,
                member = ownerProfile,
                permissions = ownerPerms,
                schools = listOf(school),
                activeSchool = school,
                activeSchoolDocId = schoolId,
                schoolNamesByDocId = mapOf(schoolId to school.name),
                session = session,
                schoolClassesForActiveSchool = listOf(class5),
                sessionStudentsForActiveSchool = manyStudents,
                studentsWithDocIdsForActiveSchool = manyStudentsWithIds,
                todayAttendanceSummaries = emptyList(),
                feeStructures = listOf(feeStructure),
                activeFeeStructure = feeStructure,
                studentFeeOverrides = emptyList(),
                feeReceipts = manyReceipts,
                feeMessageSentLogs = emptyList(),
                cashBookEntries = emptyList(),
                cashBookDayClosings = emptyList(),
                cashBookSettings = CashBookSettingsConfig.buildDefault(schoolId),
                salaryMonthlyRecords = emptyList(),
                admissionEnquiries = manyEnquiries,
                enquirySettings = EnquirySettingsConfig.buildDefault(schoolId),
                teachers = manyTeachers,
                teacherLeaves = emptyList(),
                teacherAttendanceRecords = emptyList(),
                timetableCells = emptyList(),
                substitutions = emptyList(),
                periodStructuresBySchool = mapOf(schoolId to periodStructure),
                classAssignments = emptyList(),
                ownLinkedTeacher = null,
                ownTeacherScope = null,
                ownPendingMarksCount = 0,
                ownExamDuties = emptyList(),
                ownSalarySlipsCount = 0,
                examSchedules = emptyList(),
                examDuties = emptyList(),
                stockTransactions = emptyList(),
                isFirstTimeGuideSkipped = false
            )
        }

        val durationsMs = (1..5).map {
            val startNs = System.nanoTime()
            val snap = Phase13TodayHomeEngine.buildSnapshot(
                todayIso = todayIso,
                member = ownerProfile,
                permissions = ownerPerms,
                schools = listOf(school),
                activeSchool = school,
                activeSchoolDocId = schoolId,
                schoolNamesByDocId = mapOf(schoolId to school.name),
                session = session,
                schoolClassesForActiveSchool = listOf(class5),
                sessionStudentsForActiveSchool = manyStudents,
                studentsWithDocIdsForActiveSchool = manyStudentsWithIds,
                todayAttendanceSummaries = emptyList(),
                feeStructures = listOf(feeStructure),
                activeFeeStructure = feeStructure,
                studentFeeOverrides = emptyList(),
                feeReceipts = manyReceipts,
                feeMessageSentLogs = emptyList(),
                cashBookEntries = emptyList(),
                cashBookDayClosings = emptyList(),
                cashBookSettings = CashBookSettingsConfig.buildDefault(schoolId),
                salaryMonthlyRecords = emptyList(),
                admissionEnquiries = manyEnquiries,
                enquirySettings = EnquirySettingsConfig.buildDefault(schoolId),
                teachers = manyTeachers,
                teacherLeaves = emptyList(),
                teacherAttendanceRecords = emptyList(),
                timetableCells = emptyList(),
                substitutions = emptyList(),
                periodStructuresBySchool = mapOf(schoolId to periodStructure),
                classAssignments = emptyList(),
                ownLinkedTeacher = null,
                ownTeacherScope = null,
                ownPendingMarksCount = 0,
                ownExamDuties = emptyList(),
                ownSalarySlipsCount = 0,
                examSchedules = emptyList(),
                examDuties = emptyList(),
                stockTransactions = emptyList(),
                isFirstTimeGuideSkipped = false
            )
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
            assertEquals(20_000, snap.ownerAdminTiles.todayFeeCollectionRs)
            assertEquals(10, snap.ownerAdminTiles.enquiryFollowUpsDueCount)
            elapsedMs
        }

        val avgMs = durationsMs.average()
        println("PHASE13_BENCHMARK: buildSnapshot(300 students, 150 receipts, 20 teachers, 30 enquiries) avg=${"%.2f".format(avgMs)} ms, runs=$durationsMs")
        assertTrue("Expected offline Today home snapshot computation < 250ms, got ${avgMs}ms", avgMs < 250.0)
    }
}
