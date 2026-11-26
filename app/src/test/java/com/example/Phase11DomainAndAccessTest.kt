package com.example

import com.example.data.StudentEntity
import com.example.domain.AccessLevel
import com.example.domain.AppModule
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionScope
import com.example.domain.DayOfWeekCode
import com.example.domain.ExamCategoryType
import com.example.domain.ExamInvigilatorDutyRecord
import com.example.domain.ExamRoomRecord
import com.example.domain.ExamScheduleEngine
import com.example.domain.ExamScheduleEntry
import com.example.domain.ExamScheduleRecord
import com.example.domain.FeeLineItemType
import com.example.domain.FeeReceiptLineItem
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeReceiptStatus
import com.example.domain.LeaveStatus
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OptionalFeeCatalogueItem
import com.example.domain.OrgRole
import com.example.domain.PaymentMode
import com.example.domain.Phase11AccessEvaluator
import com.example.domain.SeatingAllocationMode
import com.example.domain.StockEngine
import com.example.domain.StockLedgerKind
import com.example.domain.StockTransactionRecord
import com.example.domain.StockTransactionType
import com.example.domain.StudentRegisterStatus
import com.example.domain.TeacherLeaveRecord
import com.example.domain.TeacherProfile
import com.example.domain.TransportEngine
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 Pure JVM Unit Tests:
 * 1. Three new access modules (exams, stock, transport) & missing key = NONE.
 * 2. Exam schedule clash validation, duplicate subject check, and session date bounds.
 * 3. Rooms & seating allocation (By Roll Number vs Mixed by Class, overflow, manual move).
 * 4. Invigilator duty assignment avoiding Phase 6 approved leaves & off-days, balancing load,
 *    and preserving manually pinned duties.
 * 5. Admit card optional fee-dues withholding threshold + Admin override.
 * 6. Unified Stock: opening stock, purchase stock-in, automatic stock-out on Phase 9 fee receipt,
 *    automatic stock return on cancelled receipt, manual adjustment validation, low-stock, below-zero warning.
 * 7. Transport routes: student assignment preserving existing Phase 9 transport amounts,
 *    route/stop fee fallback, seats used & over-capacity, expected monthly income.
 */
class Phase11DomainAndAccessTest {

    private fun makeMember(
        uid: String = "u1",
        role: OrgRole = OrgRole.OFFICE_OPERATOR,
        allSchools: Boolean = false,
        schoolIds: List<String> = listOf("sch_1"),
        permissions: MemberPermissions = MemberPermissions()
    ): MemberAccessProfile = MemberAccessProfile(
        uid = uid,
        role = role,
        status = MemberStatus.ACTIVE,
        allSchools = allSchools,
        allowedSchoolIds = schoolIds,
        permissions = permissions
    )

    private fun makeStudent(
        id: Long,
        name: String,
        className: String = "5th",
        section: String = "A",
        rollNo: Int = id.toInt(),
        status: String = StudentRegisterStatus.ACTIVE.code,
        usesTransport: Boolean = false,
        transportRouteId: String = "",
        transportStopName: String = "",
        transportRouteName: String = "",
        transportMonthlyAmount: Int = 0
    ): StudentEntity = StudentEntity(
        id = id,
        schoolId = 1L,
        classId = 5L,
        session = "2026-2027",
        admNo = "ADM-%03d".format(id),
        name = name,
        fatherName = "Father $name",
        motherName = "Mother $name",
        dob = "2016-05-10",
        className = className,
        section = section,
        rollNo = rollNo,
        mobile = "98765432%02d".format(id.toInt() % 100),
        address = "Main Road, Ghazipur",
        status = status,
        usesTransport = usesTransport,
        transportRouteId = transportRouteId,
        transportStopName = transportStopName,
        transportRouteName = transportRouteName,
        transportMonthlyAmount = transportMonthlyAmount
    )

    private fun makeOptionalSaleReceipt(
        id: String,
        itemId: String,
        itemName: String,
        qty: Int,
        unitPrice: Int,
        status: FeeReceiptStatus = FeeReceiptStatus.ISSUED,
        dateIso: String = "2026-04-18",
        cancelReason: String = ""
    ): FeeReceiptRecord {
        val gross = qty * unitPrice
        return FeeReceiptRecord(
            id = id,
            schoolId = "sch_1",
            session = "2026-2027",
            receiptNumber = "RCP-2627-A1B-$id",
            deviceNodeCode = "A1B",
            sequenceNo = 1,
            receiptDateIso = dateIso,
            studentId = "stu_1",
            studentName = "Aarav Kumar",
            studentAdmNo = "ADM-001",
            fatherName = "Rajesh Kumar",
            className = "5th",
            section = "A",
            rollNo = 1,
            lineItems = listOf(
                FeeReceiptLineItem(
                    lineId = "ln_$id",
                    lineType = FeeLineItemType.OPTIONAL_ITEM,
                    monthCode = "APR",
                    headKey = "OPTIONAL_$itemId",
                    title = itemName,
                    quantity = qty,
                    unitPrice = unitPrice,
                    lineGrossAmount = gross,
                    netPayableOnLine = gross,
                    paidAllocated = gross,
                    balanceDueAfterLine = 0,
                    catalogueItemId = itemId
                )
            ),
            totalBeforeConcession = gross,
            concessionMode = ConcessionMode.RUPEES,
            concessionScope = ConcessionScope.WHOLE_RECEIPT,
            concessionAmount = 0,
            netPayableAmount = gross,
            amountPaid = gross,
            balanceDueOnReceipt = 0,
            paymentMode = PaymentMode.CASH,
            status = status,
            cancelReason = cancelReason,
            receivedByUid = "u_clerk",
            receivedByName = "Fee Clerk",
            issuedAtMs = 1713420000000L,
            cancelledAtMs = if (status == FeeReceiptStatus.CANCELLED) 1713425000000L else 0L
        )
    }

    // =========================================================================
    // 1. PHASE 11 ACCESS MODULES (EXAMS, STOCK, TRANSPORT) & MISSING KEY = NONE
    // =========================================================================

    @Test
    fun phase11AccessModulesDefaultToNoAccessWhenMissingAndEnforceRoleAndSchoolScope() {
        // Legacy Phase 10 permissions map (14 keys, missing exams/stock/transport)
        val legacyPhase10Map = mapOf(
            "students" to "edit",
            "marks" to "edit",
            "coScholastic" to "edit",
            "reports" to "edit",
            "timetable" to "edit",
            "teachers" to "view",
            "settings" to "none",
            "members" to "none",
            "attendance" to "edit",
            "fees" to "edit",
            "studentRegister" to "edit",
            "certificates" to "edit",
            "cashBook" to "edit",
            "enquiry" to "edit"
        )
        val parsed = MemberPermissions.fromMap(legacyPhase10Map)
        assertEquals(21, AppModule.entries.size)
        assertEquals(21, parsed.toFirestoreMap().size)
        assertEquals(AccessLevel.NONE, parsed.exams)
        assertEquals(AccessLevel.NONE, parsed.stock)
        assertEquals(AccessLevel.NONE, parsed.transport)

        val legacyStaff = makeMember(uid = "u_legacy", role = OrgRole.OFFICE_OPERATOR, permissions = parsed)
        assertFalse(Phase11AccessEvaluator.canViewExams(legacyStaff, "sch_1"))
        assertFalse(Phase11AccessEvaluator.canEditExams(legacyStaff, "sch_1"))
        assertFalse(Phase11AccessEvaluator.canViewStock(legacyStaff, "sch_1"))
        assertFalse(Phase11AccessEvaluator.canEditStock(legacyStaff, "sch_1"))
        assertFalse(Phase11AccessEvaluator.canViewTransport(legacyStaff, "sch_1"))
        assertFalse(Phase11AccessEvaluator.canEditTransport(legacyStaff, "sch_1"))

        // Role presets: Office Operator gets EDIT; Teacher & Other get NONE
        val officePreset = MemberPermissions.presetFor(OrgRole.OFFICE_OPERATOR)
        assertEquals(AccessLevel.EDIT, officePreset.exams)
        assertEquals(AccessLevel.EDIT, officePreset.stock)
        assertEquals(AccessLevel.EDIT, officePreset.transport)

        val teacherPreset = MemberPermissions.presetFor(OrgRole.TEACHER)
        assertEquals(AccessLevel.NONE, teacherPreset.exams)
        assertEquals(AccessLevel.NONE, teacherPreset.stock)
        assertEquals(AccessLevel.NONE, teacherPreset.transport)

        // Owner and Admin always have full access across schools
        val owner = makeMember(uid = "u_owner", role = OrgRole.OWNER, allSchools = true)
        val admin = makeMember(uid = "u_admin", role = OrgRole.ADMIN, allSchools = true)
        assertTrue(Phase11AccessEvaluator.canEditExams(owner, "sch_42"))
        assertTrue(Phase11AccessEvaluator.canEditStock(owner, "sch_42"))
        assertTrue(Phase11AccessEvaluator.canEditTransport(owner, "sch_42"))
        assertTrue(Phase11AccessEvaluator.canEditExams(admin, "sch_42"))
        assertTrue(Phase11AccessEvaluator.canEditStock(admin, "sch_42"))
        assertTrue(Phase11AccessEvaluator.canEditTransport(admin, "sch_42"))

        // View-only vs Edit + School scoping
        val scopedMember = makeMember(
            uid = "u_scoped",
            role = OrgRole.OTHER,
            schoolIds = listOf("sch_1"),
            permissions = MemberPermissions().copy(
                exams = AccessLevel.VIEW,
                stock = AccessLevel.EDIT,
                transport = AccessLevel.VIEW
            )
        )
        assertTrue(Phase11AccessEvaluator.canViewExams(scopedMember, "sch_1"))
        assertFalse(Phase11AccessEvaluator.canEditExams(scopedMember, "sch_1"))
        assertFalse(Phase11AccessEvaluator.canViewExams(scopedMember, "sch_2"))
        assertTrue(Phase11AccessEvaluator.canEditStock(scopedMember, "sch_1"))
        assertFalse(Phase11AccessEvaluator.canEditStock(scopedMember, "sch_2"))
        assertTrue(Phase11AccessEvaluator.canViewTransport(scopedMember, "sch_1"))
        assertFalse(Phase11AccessEvaluator.canEditTransport(scopedMember, "sch_1"))
    }

    // =========================================================================
    // 2. EXAM SCHEDULE CLASH VALIDATION & SESSION DATE BOUNDS
    // =========================================================================

    @Test
    fun examScheduleValidatesSessionBoundsTimeClashesAndDuplicateSubjects() {
        val session = "2026-2027"
        assertEquals("2026-04-01" to "2027-03-31", ExamScheduleEngine.sessionDateBounds(session))
        assertTrue(ExamScheduleEngine.isDateWithinSession("2026-09-15", session))
        assertTrue(ExamScheduleEngine.isDateWithinSession("2027-03-20", session))
        assertFalse(ExamScheduleEngine.isDateWithinSession("2026-03-31", session))
        assertFalse(ExamScheduleEngine.isDateWithinSession("2027-04-01", session))

        val math = ExamScheduleEntry(
            entryId = "e1",
            className = "5th",
            subjectName = "Maths",
            dateIso = "2026-09-15",
            startTime = "09:00",
            endTime = "12:00"
        )
        assertNull(ExamScheduleEngine.validateEntry(math, session, emptyList()))

        // Out-of-session date rejected
        val outsideSession = math.copy(entryId = "e_out", subjectName = "Hindi", dateIso = "2027-05-10")
        assertNotNull(ExamScheduleEngine.validateEntry(outsideSession, session, listOf(math)))

        // End time <= start time rejected
        val invalidTime = math.copy(entryId = "e_badtime", subjectName = "Hindi", startTime = "12:00", endTime = "11:00")
        assertNotNull(ExamScheduleEngine.validateEntry(invalidTime, session, listOf(math)))

        // Same class, same day, overlapping time -> CLASH rejected
        val overlappingPaper = ExamScheduleEntry(
            entryId = "e2",
            className = "5th",
            subjectName = "Science",
            dateIso = "2026-09-15",
            startTime = "11:00",
            endTime = "13:30"
        )
        val clashErr = ExamScheduleEngine.validateEntry(overlappingPaper, session, listOf(math))
        assertNotNull(clashErr)
        assertTrue(clashErr!!.contains("Time clash", ignoreCase = true))

        // Same class, same day, back-to-back non-overlapping (12:00-14:00) -> ALLOWED
        val afternoonPaper = ExamScheduleEntry(
            entryId = "e3",
            className = "5th",
            subjectName = "Drawing",
            dateIso = "2026-09-15",
            startTime = "12:00",
            endTime = "14:00"
        )
        assertNull(ExamScheduleEngine.validateEntry(afternoonPaper, session, listOf(math)))

        // Different class at exact same date & time -> ALLOWED
        val otherClassPaper = ExamScheduleEntry(
            entryId = "e4",
            className = "6th",
            subjectName = "Maths",
            dateIso = "2026-09-15",
            startTime = "09:00",
            endTime = "12:00"
        )
        assertNull(ExamScheduleEngine.validateEntry(otherClassPaper, session, listOf(math)))

        // Duplicate subject for same class on a different date -> REJECTED
        val dupSubject = ExamScheduleEntry(
            entryId = "e5",
            className = "5th",
            subjectName = "Maths",
            dateIso = "2026-09-18",
            startTime = "09:00",
            endTime = "12:00"
        )
        assertNotNull(ExamScheduleEngine.validateEntry(dupSubject, session, listOf(math)))
    }

    // =========================================================================
    // 3. SEATING ALLOCATION (BY ROLL VS MIXED BY CLASS + MANUAL MOVE)
    // =========================================================================

    @Test
    fun seatingAllocationSupportsRollOrderMixedByClassOverflowAndManualMove() {
        val students = listOf(
            "s1" to makeStudent(1, "Aarav", className = "5th", rollNo = 1),
            "s2" to makeStudent(2, "Diya", className = "5th", rollNo = 2),
            "s3" to makeStudent(3, "Kabir", className = "5th", rollNo = 3),
            "s4" to makeStudent(4, "Meera", className = "6th", rollNo = 1),
            "s5" to makeStudent(5, "Rohan", className = "6th", rollNo = 2),
            "s6" to makeStudent(6, "LeftKid", className = "6th", rollNo = 3, status = StudentRegisterStatus.LEFT.code)
        )
        val rooms = listOf(
            ExamRoomRecord(id = "r1", schoolId = "sch_1", roomName = "Room 101", capacity = 2),
            ExamRoomRecord(id = "r2", schoolId = "sch_1", roomName = "Room 102", capacity = 2)
        )

        // Mode 1: BY_ROLL_NUMBER -> 5th (1,2) in Room 101, 5th (3) & 6th (1) in Room 102, 6th (2) overflow
        val byRoll = ExamScheduleEngine.allocateSeating(
            schoolId = "sch_1",
            session = "2026-2027",
            examId = "exam_1",
            studentsWithDocIds = students,
            rooms = rooms,
            mode = SeatingAllocationMode.BY_ROLL_NUMBER
        )
        assertEquals(4, byRoll.allocations.size)
        assertEquals(listOf("s5"), byRoll.unallocatedStudentIds) // s6 is LEFT so excluded
        assertEquals(listOf("5th", "5th", "5th", "6th"), byRoll.allocations.map { it.className })

        // Mode 2: MIXED_BY_CLASS -> interleaves 5th, 6th, 5th, 6th
        val mixed = ExamScheduleEngine.allocateSeating(
            schoolId = "sch_1",
            session = "2026-2027",
            examId = "exam_1",
            studentsWithDocIds = students,
            rooms = rooms,
            mode = SeatingAllocationMode.MIXED_BY_CLASS
        )
        assertEquals(4, mixed.allocations.size)
        assertEquals(listOf("5th", "6th", "5th", "6th"), mixed.allocations.map { it.className })
        assertEquals(listOf("s3"), mixed.unallocatedStudentIds)

        // Manual move: shift s1 from Room 101 to Room 102
        val moved = ExamScheduleEngine.moveStudentToRoom(mixed, "s1", rooms[1])
        val s1Seat = moved.allocations.first { it.studentDocId == "s1" }
        assertEquals("r2", s1Seat.roomId)
        assertEquals("Room 102", s1Seat.roomName)
        assertEquals(3, s1Seat.seatNumber) // seats 1 & 2 already occupied in Room 102
    }

    // =========================================================================
    // 4. INVIGILATOR DUTY ASSIGNMENT (AVOIDS LEAVE & OFF-DAYS, BALANCES LOAD)
    // =========================================================================

    @Test
    fun invigilatorAutoAssignAvoidsApprovedLeaveWeeklyOffBalancesLoadAndKeepsManualPins() {
        val exam = ExamScheduleRecord(
            id = "exam_hy",
            schoolId = "sch_1",
            session = "2026-2027",
            examType = ExamCategoryType.HALF_YEARLY,
            title = "Half-Yearly Exam",
            entries = listOf(
                // 2026-09-14 is Monday; 2026-09-15 is Tuesday
                ExamScheduleEntry("e1", "5th", "Maths", "2026-09-14", "09:00", "12:00"),
                ExamScheduleEntry("e2", "5th", "Science", "2026-09-15", "09:00", "12:00")
            )
        )
        val rooms = listOf(
            ExamRoomRecord("r1", "sch_1", "Room 101", 30),
            ExamRoomRecord("r2", "sch_1", "Room 102", 30)
        )
        val teachers = listOf(
            TeacherProfile(id = "t1", name = "Anjali Sharma", schoolIds = listOf("sch_1"), linkedMemberUid = "uid_t1"),
            TeacherProfile(id = "t2", name = "Bhavna Verma", schoolIds = listOf("sch_1"), linkedMemberUid = "uid_t2"),
            TeacherProfile(id = "t3", name = "Chetan Singh", schoolIds = listOf("sch_1"), weeklyOffDays = listOf(DayOfWeekCode.TUE), linkedMemberUid = "uid_t3"),
            TeacherProfile(id = "t4", name = "Deepak Gupta", schoolIds = listOf("sch_1"), linkedMemberUid = "uid_t4")
        )
        val leaves = listOf(
            // t1 has APPROVED leave on Monday 2026-09-14
            TeacherLeaveRecord(
                id = "lv1",
                teacherId = "t1",
                teacherName = "Anjali Sharma",
                startDate = "2026-09-14",
                endDate = "2026-09-14",
                status = LeaveStatus.APPROVED
            ),
            // t2 has only PENDING leave on Tuesday 2026-09-15 -> NOT blocked
            TeacherLeaveRecord(
                id = "lv2",
                teacherId = "t2",
                teacherName = "Bhavna Verma",
                startDate = "2026-09-15",
                endDate = "2026-09-15",
                status = LeaveStatus.PENDING
            )
        )
        // Manually pin t4 to Tuesday Room 102
        val manualPin = ExamInvigilatorDutyRecord(
            id = ExamInvigilatorDutyRecord.makeDocId("exam_hy", "2026-09-15", "09:00", "r2"),
            schoolId = "sch_1",
            session = "2026-2027",
            examId = "exam_hy",
            examTitle = "Half-Yearly Exam",
            dateIso = "2026-09-15",
            startTime = "09:00",
            endTime = "12:00",
            roomId = "r2",
            roomName = "Room 102",
            teacherId = "t4",
            teacherUid = "uid_t4",
            teacherName = "Deepak Gupta",
            assignedManually = true
        )

        val result = ExamScheduleEngine.autoAssignInvigilators(
            exam = exam,
            rooms = rooms,
            teachers = teachers,
            leaves = leaves,
            existingManualDuties = listOf(manualPin)
        )

        assertTrue("All 4 room-slots must be filled", result.unfilledSlots.isEmpty())
        assertEquals(4, result.duties.size)

        // Monday duties must NOT include t1 (approved leave) and must assign two distinct teachers
        val monDuties = result.duties.filter { it.dateIso == "2026-09-14" }
        assertTrue(monDuties.none { it.teacherId == "t1" })
        assertEquals(2, monDuties.map { it.teacherId }.toSet().size)

        // Tuesday duties must NOT include t3 (weekly off Tuesday) and must preserve manual pin for r2 -> t4
        val tueDuties = result.duties.filter { it.dateIso == "2026-09-15" }
        assertTrue(tueDuties.none { it.teacherId == "t3" })
        val tueR2 = tueDuties.first { it.roomId == "r2" }
        assertEquals("t4", tueR2.teacherId)
        assertTrue(tueR2.assignedManually)

        // Load balancing: every teacher gets at most 2 duties across 4 total slots, and t1 gets Tuesday Room 101 (0 prior duties)
        val countsByTeacher = result.duties.groupingBy { it.teacherId }.eachCount()
        assertEquals(1, countsByTeacher["t1"]) // picked first on Tuesday because load was 0
        assertTrue(countsByTeacher.values.all { it <= 2 })
    }

    // =========================================================================
    // 5. ADMIT CARD OPTIONAL DUES WITHHOLDING & ADMIN OVERRIDE
    // =========================================================================

    @Test
    fun admitCardWithholdingThresholdAndAdminOverrideWorkCorrectly() {
        val exam = ExamScheduleRecord(
            id = "exam_1",
            schoolId = "sch_1",
            session = "2026-2027",
            examType = ExamCategoryType.YEARLY,
            title = "Yearly Exam",
            withholdAdmitCardAboveDuesRs = 2000,
            admitCardOverrideStudentIds = listOf("stu_3")
        )
        val students = listOf(
            "stu_1" to makeStudent(1, "Clear Kid"),
            "stu_2" to makeStudent(2, "High Dues Kid"),
            "stu_3" to makeStudent(3, "Overridden Kid")
        )
        val dues = mapOf("stu_1" to 1500, "stu_2" to 3500, "stu_3" to 4200)

        val elig = ExamScheduleEngine.evaluateAdmitCardEligibility(exam, students, dues, seating = null)
        val e1 = elig.first { it.studentDocId == "stu_1" }
        val e2 = elig.first { it.studentDocId == "stu_2" }
        val e3 = elig.first { it.studentDocId == "stu_3" }

        assertFalse(e1.isWithheldByDues)
        assertTrue(e1.canPrint)

        assertTrue(e2.isWithheldByDues)
        assertFalse(e2.isOverriddenByAdmin)
        assertFalse(e2.canPrint)

        assertTrue(e3.isWithheldByDues)
        assertTrue(e3.isOverriddenByAdmin)
        assertTrue(e3.canPrint)
    }

    // =========================================================================
    // 6. STOCK: STOCK IN, AUTO SALE OUT ON FEE RECEIPT, CANCEL RETURN, LOW STOCK
    // =========================================================================

    @Test
    fun stockCalculatesOpeningPurchaseReceiptSaleCancelReturnAdjustmentAndLowStock() {
        val catalogue = listOf(
            OptionalFeeCatalogueItem(
                id = "cat_tie",
                name = "School Tie",
                price = 80,
                unit = "pcs",
                openingStock = 10,
                lowStockLevel = 8
            ),
            OptionalFeeCatalogueItem(
                id = "cat_belt",
                name = "School Belt",
                price = 120,
                unit = "pcs",
                openingStock = 5,
                lowStockLevel = 5
            )
        )

        val transactions = listOf(
            // Purchase +20 ties
            StockTransactionRecord(
                id = "tx_in_1",
                schoolId = "sch_1",
                session = "2026-2027",
                itemId = "cat_tie",
                itemName = "School Tie",
                txType = StockTransactionType.STOCK_IN,
                dateIso = "2026-04-10",
                quantityDelta = 20,
                unitCostRs = 50,
                totalCostRs = 1000,
                supplier = "City Uniforms",
                createdAtMs = 1000L
            ),
            // Manual damage adjustment -2 ties
            StockTransactionRecord(
                id = "tx_adj_1",
                schoolId = "sch_1",
                session = "2026-2027",
                itemId = "cat_tie",
                itemName = "School Tie",
                txType = StockTransactionType.ADJUSTMENT,
                dateIso = "2026-04-12",
                quantityDelta = -2,
                reason = "2 damaged in rain",
                createdAtMs = 2000L
            )
        )

        // Active receipt sells 22 ties -> remaining = 10 + 20 - 2 - 22 = 6 (<= lowStockLevel 8 -> LOW!)
        val activeReceipt = makeOptionalSaleReceipt(
            id = "r1",
            itemId = "cat_tie",
            itemName = "School Tie",
            qty = 22,
            unitPrice = 80,
            status = FeeReceiptStatus.ISSUED
        )
        // Cancelled receipt sold 4 ties -> cancelled returns stock automatically (excluded from active sold)
        val cancelledReceipt = makeOptionalSaleReceipt(
            id = "r2",
            itemId = "cat_tie",
            itemName = "School Tie",
            qty = 4,
            unitPrice = 80,
            status = FeeReceiptStatus.CANCELLED,
            cancelReason = "Wrong item selected"
        )

        val summariesBeforeCancel = StockEngine.computeStockSummary(
            schoolId = "sch_1",
            session = "2026-2027",
            catalogue = catalogue,
            transactions = transactions,
            feeReceipts = listOf(activeReceipt, cancelledReceipt.copy(status = FeeReceiptStatus.ISSUED))
        )
        val tieBeforeCancel = summariesBeforeCancel.first { it.item.id == "cat_tie" }
        assertEquals(26, tieBeforeCancel.soldOnReceipts)
        assertEquals(2, tieBeforeCancel.currentStock) // 10 + 20 - 2 - 26 = 2

        val summariesAfterCancel = StockEngine.computeStockSummary(
            schoolId = "sch_1",
            session = "2026-2027",
            catalogue = catalogue,
            transactions = transactions,
            feeReceipts = listOf(activeReceipt, cancelledReceipt)
        )
        val tieAfterCancel = summariesAfterCancel.first { it.item.id == "cat_tie" }
        assertEquals(22, tieAfterCancel.soldOnReceipts)
        assertEquals(4, tieAfterCancel.cancelledReturnCount)
        assertEquals(6, tieAfterCancel.currentStock) // 4 ties returned! 2 + 4 = 6
        assertTrue("6 <= 8 should trigger low-stock warning", tieAfterCancel.isLowStock)
        assertEquals(1000, tieAfterCancel.totalPurchaseSpendRs)
        assertEquals(480, tieAfterCancel.estimatedStockValueRs) // 6 * 80

        // Below-zero sale warning when trying to sell 8 ties while only 6 in stock
        val warnings = StockEngine.checkBelowZeroSaleWarnings(
            requestedQtyByItemId = mapOf("cat_tie" to 8, "cat_belt" to 3),
            summaries = summariesAfterCancel
        )
        assertEquals(1, warnings.size)
        assertEquals("cat_tie", warnings[0].itemId)
        assertEquals(6, warnings[0].availableStock)
        assertEquals(-2, warnings[0].projectedStock)

        // Ledger must record both SALE_OUT and CANCEL_RETURN events for cancelled receipt
        val ledger = StockEngine.buildUnifiedLedger(
            schoolId = "sch_1",
            session = "2026-2027",
            catalogue = catalogue,
            transactions = transactions,
            feeReceipts = listOf(activeReceipt, cancelledReceipt)
        )
        assertTrue(ledger.any { it.kind == StockLedgerKind.PURCHASE_IN && it.quantityDelta == 20 })
        assertTrue(ledger.any { it.kind == StockLedgerKind.RECEIPT_SALE_OUT && it.quantityDelta == -22 })
        assertTrue(ledger.any { it.kind == StockLedgerKind.RECEIPT_CANCEL_RETURN && it.quantityDelta == 4 })
        assertTrue(ledger.any { it.kind == StockLedgerKind.MANUAL_ADJUSTMENT && it.quantityDelta == -2 })

        // Validation rules: manual adjustment requires non-blank reason & non-zero delta
        assertNotNull(StockEngine.validateStockTransaction(StockTransactionType.STOCK_IN, 0, ""))
        assertNotNull(StockEngine.validateStockTransaction(StockTransactionType.ADJUSTMENT, 0, "Audit"))
        assertNotNull(StockEngine.validateStockTransaction(StockTransactionType.ADJUSTMENT, -1, "   "))
        assertNull(StockEngine.validateStockTransaction(StockTransactionType.ADJUSTMENT, -1, "Damaged piece"))
    }

    // =========================================================================
    // 7. TRANSPORT ROUTES: PRESERVE EXISTING AMOUNTS, SEATS USED & INCOME
    // =========================================================================

    @Test
    fun transportRouteAssignmentPreservesExistingFeesAndComputesSummaryAccurately() {
        val route = TransportRouteRecord(
            id = "troute_sch_1_north_bus",
            schoolId = "sch_1",
            routeName = "North Bus",
            vehicleNumber = "UP-61-T-1234",
            vehicleType = "Bus",
            seatCapacity = 2,
            driverName = "Ramesh Yadav",
            driverPhone = "9876500001",
            monthlyFeeRs = 600,
            stops = listOf(
                TransportStop("s_bazar", "Main Bazar", "07:30 AM", monthlyFeeRs = 500),
                TransportStop("s_station", "Railway Station", "07:45 AM", monthlyFeeRs = 750),
                TransportStop("s_chowk", "Gandhi Chowk", "08:00 AM", monthlyFeeRs = 0) // inherits route 600
            )
        )

        // Student 1 ALREADY had custom Phase 9 transport fee = 450 -> MUST be preserved!
        val existingCustomStudent = makeStudent(
            id = 1,
            name = "Aarav",
            usesTransport = true,
            transportRouteName = "Old Bus",
            transportMonthlyAmount = 450
        )
        val assignedExisting = TransportEngine.assignStudentToRoute(
            student = existingCustomStudent,
            route = route,
            stopName = "Railway Station",
            preserveExistingAmountIfPositive = true
        )
        assertTrue(assignedExisting.usesTransport)
        assertEquals(route.id, assignedExisting.transportRouteId)
        assertEquals("Railway Station", assignedExisting.transportStopName)
        assertEquals("North Bus - Railway Station", assignedExisting.transportRouteName)
        assertEquals(450, assignedExisting.transportMonthlyAmount) // Existing fee preserved!

        // Student 2 is newly added to Railway Station -> gets stop fee (750)
        val newStudentStopFee = TransportEngine.assignStudentToRoute(
            student = makeStudent(2, "Diya"),
            route = route,
            stopName = "Railway Station",
            preserveExistingAmountIfPositive = true
        )
        assertEquals(750, newStudentStopFee.transportMonthlyAmount)

        // Student 3 is newly added to Gandhi Chowk (stop fee = 0) -> inherits route fee (600)
        val newStudentRouteFee = TransportEngine.assignStudentToRoute(
            student = makeStudent(3, "Kabir"),
            route = route,
            stopName = "Gandhi Chowk",
            preserveExistingAmountIfPositive = true
        )
        assertEquals(600, newStudentRouteFee.transportMonthlyAmount)

        // Explicit opt-in to replace existing fee with route/stop fee works when requested
        val replacedFee = TransportEngine.assignStudentToRoute(
            student = existingCustomStudent,
            route = route,
            stopName = "Main Bazar",
            preserveExistingAmountIfPositive = false
        )
        assertEquals(500, replacedFee.transportMonthlyAmount)

        // Summary: 3 students on a 2-seat route -> seatsUsed=3, isOverCapacity=true, income=450+750+600=1800
        val legacyUnmatchedTransportStudent = makeStudent(
            id = 4,
            name = "Meera",
            usesTransport = true,
            transportRouteName = "East Van",
            transportMonthlyAmount = 550
        )
        val schoolSummary = TransportEngine.computeSchoolTransportSummary(
            routes = listOf(route),
            students = listOf(assignedExisting, newStudentStopFee, newStudentRouteFee, legacyUnmatchedTransportStudent)
        )
        assertEquals(1, schoolSummary.routeSummaries.size)
        val northSum = schoolSummary.routeSummaries[0]
        assertEquals(3, northSum.seatsUsed)
        assertEquals(0, northSum.seatsRemaining)
        assertTrue(northSum.isOverCapacity)
        assertEquals(1800, northSum.expectedMonthlyIncomeRs)
        assertEquals(1, schoolSummary.unassignedTransportStudents.size)
        assertEquals(4, schoolSummary.totalTransportStudents)
        assertEquals(2350, schoolSummary.totalExpectedMonthlyIncomeRs) // 1800 + 550

        // Removing student clears transport fields cleanly
        val removed = TransportEngine.removeStudentFromRoute(assignedExisting)
        assertFalse(removed.usesTransport)
        assertEquals("", removed.transportRouteId)
        assertEquals(0, removed.transportMonthlyAmount)
    }
}
