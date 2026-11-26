package com.example

import com.example.domain.AccessLevel
import com.example.domain.AdmissionEnquiryEngine
import com.example.domain.AdmissionEnquiryRecord
import com.example.domain.AppModule
import com.example.domain.CashBookDayClosingRecord
import com.example.domain.CashBookEngine
import com.example.domain.CashBookEntryRecord
import com.example.domain.CashBookEntryStatus
import com.example.domain.CashBookEntryType
import com.example.domain.CashBookSettingsConfig
import com.example.domain.CertificateEngine
import com.example.domain.CertificateStatus
import com.example.domain.CertificateType
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionScope
import com.example.domain.EnquirySettingsConfig
import com.example.domain.EnquiryStatus
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeReceiptStatus
import com.example.domain.FollowUpUrgency
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgRole
import com.example.domain.PaymentMode
import com.example.domain.Phase10AccessEvaluator
import com.example.domain.PhoneValidationResult
import com.example.domain.SchoolCertificateRecord
import com.example.domain.StudentRegisterStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 10 Pure JVM Unit Tests:
 * 1. Three new access modules (certificates, cashBook, enquiry) & missing-key = NONE.
 * 2. Offline-safe certificate numbering without duplicates across two phones, cancel & re-issue.
 * 3. Cash book day/month totals (auto Phase 9 fee receipts + other income - expenses),
 *    day closing difference, opening cash carry-forward, and large-expense admin approval limit.
 * 4. Admission enquiry follow-up ordering (overdue first), WhatsApp +91 normalisation,
 *    monthly conversion percentage, and enquiry-to-student register conversion.
 */
class Phase10DomainAndAccessTest {

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

    private fun makeReceipt(
        id: String,
        dateIso: String = "2026-04-15",
        amountPaid: Int,
        mode: PaymentMode = PaymentMode.CASH,
        status: FeeReceiptStatus = FeeReceiptStatus.ISSUED,
        schoolId: String = "sch_1"
    ): FeeReceiptRecord = FeeReceiptRecord(
        id = id,
        schoolId = schoolId,
        session = "2026-2027",
        receiptNumber = "RCP-2627-A1B-$id",
        deviceNodeCode = "A1B",
        sequenceNo = 1,
        receiptDateIso = dateIso,
        studentId = "stu_$id",
        studentName = "Student $id",
        studentAdmNo = "ADM-$id",
        fatherName = "Father $id",
        className = "3rd",
        section = "A",
        rollNo = 1,
        lineItems = emptyList(),
        totalBeforeConcession = amountPaid,
        concessionMode = ConcessionMode.RUPEES,
        concessionScope = ConcessionScope.WHOLE_RECEIPT,
        concessionAmount = 0,
        netPayableAmount = amountPaid,
        amountPaid = amountPaid,
        balanceDueOnReceipt = 0,
        paymentMode = mode,
        status = status,
        receivedByUid = "u_clerk",
        receivedByName = "Fee Clerk",
        issuedAtMs = 1713160000000L
    )

    private fun makeCashEntry(
        id: String,
        dateIso: String = "2026-04-15",
        type: CashBookEntryType = CashBookEntryType.EXPENSE,
        category: String = "Electricity",
        amount: Int = 500,
        mode: PaymentMode = PaymentMode.CASH,
        status: CashBookEntryStatus = CashBookEntryStatus.ACTIVE,
        schoolId: String = "sch_1"
    ): CashBookEntryRecord = CashBookEntryRecord(
        id = id,
        schoolId = schoolId,
        dateIso = dateIso,
        entryType = type,
        category = category,
        amount = amount,
        paymentMode = mode,
        status = status,
        createdByUid = "u_clerk",
        createdByName = "Cash Clerk",
        createdAtMs = 1713165000000L
    )

    // =========================================================================
    // 1. THREE NEW ACCESS MODULES & MISSING KEY = NO ACCESS
    // =========================================================================

    @Test
    fun missingPhase10KeysDefaultToNoAccessAndOwnerAdminHaveFullAccess() {
        // Legacy Phase 9 permissions map without certificates / cashBook / enquiry keys
        val legacyMap = mapOf(
            "students" to "edit",
            "marks" to "edit",
            "coScholastic" to "edit",
            "reports" to "edit",
            "timetable" to "view",
            "teachers" to "view",
            "settings" to "none",
            "members" to "none",
            "attendance" to "edit",
            "fees" to "edit",
            "studentRegister" to "edit"
        )
        val parsed = MemberPermissions.fromMap(legacyMap)
        assertEquals(AccessLevel.NONE, parsed.certificates)
        assertEquals(AccessLevel.NONE, parsed.cashBook)
        assertEquals(AccessLevel.NONE, parsed.enquiry)
        assertEquals(21, AppModule.entries.size)
        assertEquals(21, parsed.toFirestoreMap().size)

        val legacyStaff = makeMember(uid = "u_legacy", role = OrgRole.OFFICE_OPERATOR, permissions = parsed)
        assertFalse(Phase10AccessEvaluator.canViewCertificates(legacyStaff, "sch_1"))
        assertFalse(Phase10AccessEvaluator.canEditCertificates(legacyStaff, "sch_1"))
        assertFalse(Phase10AccessEvaluator.canViewCashBook(legacyStaff, "sch_1"))
        assertFalse(Phase10AccessEvaluator.canEditCashBook(legacyStaff, "sch_1"))
        assertFalse(Phase10AccessEvaluator.canViewEnquiry(legacyStaff, "sch_1"))
        assertFalse(Phase10AccessEvaluator.canEditEnquiry(legacyStaff, "sch_1"))

        val owner = makeMember(uid = "u_owner", role = OrgRole.OWNER, allSchools = true, permissions = MemberPermissions())
        val admin = makeMember(uid = "u_admin", role = OrgRole.ADMIN, allSchools = true, permissions = MemberPermissions())
        assertTrue(Phase10AccessEvaluator.canEditCertificates(owner, "sch_99"))
        assertTrue(Phase10AccessEvaluator.canEditCashBook(owner, "sch_99"))
        assertTrue(Phase10AccessEvaluator.canEditEnquiry(owner, "sch_99"))
        assertTrue(Phase10AccessEvaluator.canEditCertificates(admin, "sch_99"))
        assertTrue(Phase10AccessEvaluator.canEditCashBook(admin, "sch_99"))
        assertTrue(Phase10AccessEvaluator.canEditEnquiry(admin, "sch_99"))

        // View-only vs Edit + School scope enforcement
        val viewer = makeMember(
            uid = "u_viewer",
            role = OrgRole.OTHER,
            schoolIds = listOf("sch_1"),
            permissions = MemberPermissions().copy(
                certificates = AccessLevel.VIEW,
                cashBook = AccessLevel.VIEW,
                enquiry = AccessLevel.EDIT
            )
        )
        assertTrue(Phase10AccessEvaluator.canViewCertificates(viewer, "sch_1"))
        assertFalse(Phase10AccessEvaluator.canEditCertificates(viewer, "sch_1"))
        assertFalse(Phase10AccessEvaluator.canViewCertificates(viewer, "sch_2")) // school scope enforced
        assertTrue(Phase10AccessEvaluator.canViewCashBook(viewer, "sch_1"))
        assertFalse(Phase10AccessEvaluator.canEditCashBook(viewer, "sch_1"))
        assertTrue(Phase10AccessEvaluator.canEditEnquiry(viewer, "sch_1"))
        assertFalse(Phase10AccessEvaluator.canEditEnquiry(viewer, "sch_2"))
    }

    // =========================================================================
    // 2. CERTIFICATES: OFFLINE DUPLICATE-FREE NUMBERING, CANCEL & RE-ISSUE
    // =========================================================================

    @Test
    fun certificateNumberingIsSequentialPerSchoolSessionTypeAndCollisionFreeOffline() {
        val existing = mutableListOf<SchoolCertificateRecord>()

        // Phone A (deviceNodeCode = "A1B") offline issues TC #1 and TC #2
        val (tcA1, seqA1) = CertificateEngine.generateNextCertificateNumber(
            schoolId = "sch_1",
            session = "2026-2027",
            certificateType = CertificateType.TC,
            deviceNodeCode = "A1B",
            existingCertificates = existing,
            lastLocalSequenceForType = 0
        )
        assertEquals("TC-2627-A1B-0001", tcA1)
        assertEquals(1, seqA1)

        // Phone B (deviceNodeCode = "K4M") offline at the exact same moment issues TC #1
        val (tcB1, seqB1) = CertificateEngine.generateNextCertificateNumber(
            schoolId = "sch_1",
            session = "2026-2027",
            certificateType = CertificateType.TC,
            deviceNodeCode = "K4M",
            existingCertificates = emptyList(), // Phone B hasn't synced with Phone A yet
            lastLocalSequenceForType = 0
        )
        assertEquals("TC-2627-K4M-0001", tcB1)
        assertEquals(1, seqB1)
        assertNotEquals("Two offline phones must never collide", tcA1, tcB1)

        // Add both to synced register, then Phone A issues another TC + a Bonafide + a Character
        val certA1 = SchoolCertificateRecord(
            id = SchoolCertificateRecord.makeDocId("sch_1", "2026-2027", tcA1),
            schoolId = "sch_1",
            session = "2026-2027",
            certificateType = CertificateType.TC,
            certificateNumber = tcA1,
            deviceNodeCode = "A1B",
            sequenceNo = seqA1,
            issueDateIso = "2026-04-15",
            studentId = "stu_1",
            studentName = "Aarav Kumar",
            studentAdmNo = "ADM-001",
            fatherName = "Rajesh Kumar",
            motherName = "Sunita Devi",
            dob = "2015-05-10",
            className = "5th",
            section = "A",
            rollNo = 1,
            issuedByUid = "u1",
            issuedByName = "Clerk A",
            issuedAtMs = 1000L
        )
        val certB1 = certA1.copy(
            id = SchoolCertificateRecord.makeDocId("sch_1", "2026-2027", tcB1),
            certificateNumber = tcB1,
            deviceNodeCode = "K4M",
            sequenceNo = seqB1,
            issuedAtMs = 1005L
        )
        existing += certA1
        existing += certB1
        assertNotEquals(certA1.id, certB1.id)

        // Phone A next TC must be 0002 (not confused by Phone B)
        val (tcA2, seqA2) = CertificateEngine.generateNextCertificateNumber(
            schoolId = "sch_1",
            session = "2026-2027",
            certificateType = CertificateType.TC,
            deviceNodeCode = "A1B",
            existingCertificates = existing,
            lastLocalSequenceForType = seqA1
        )
        assertEquals("TC-2627-A1B-0002", tcA2)
        assertEquals(2, seqA2)

        // Independent sequence per certificate type (Bonafide starts at 0001 on Phone A)
        val (bonA1, seqBon1) = CertificateEngine.generateNextCertificateNumber(
            schoolId = "sch_1",
            session = "2026-2027",
            certificateType = CertificateType.BONAFIDE,
            deviceNodeCode = "A1B",
            existingCertificates = existing,
            lastLocalSequenceForType = 0
        )
        assertEquals("BON-2627-A1B-0001", bonA1)
        assertEquals(1, seqBon1)

        // Independent sequence per session (2027-2028 TC starts at 0001)
        val (tcNextSession, seqNextSession) = CertificateEngine.generateNextCertificateNumber(
            schoolId = "sch_1",
            session = "2027-2028",
            certificateType = CertificateType.TC,
            deviceNodeCode = "A1B",
            existingCertificates = existing,
            lastLocalSequenceForType = 0
        )
        assertEquals("TC-2728-A1B-0001", tcNextSession)
        assertEquals(1, seqNextSession)
    }

    @Test
    fun certificateCancelRequiresReasonAndReissueCreatesNewLinkedNumber() {
        val issued = SchoolCertificateRecord(
            id = "cert_1",
            schoolId = "sch_1",
            session = "2026-2027",
            certificateType = CertificateType.TC,
            certificateNumber = "TC-2627-A1B-0001",
            deviceNodeCode = "A1B",
            sequenceNo = 1,
            issueDateIso = "2026-04-15",
            studentId = "stu_1",
            studentName = "Aarav Kumar",
            studentAdmNo = "ADM-001",
            fatherName = "Rajesh Kumar",
            motherName = "Sunita Devi",
            dob = "2015-05-10",
            className = "5th",
            section = "A",
            rollNo = 1,
            reasonForLeaving = "Relocation",
            conduct = "Good",
            issuedByUid = "u1",
            issuedByName = "Office Clerk",
            issuedAtMs = 1000L
        )

        // Blank cancel reason rejected
        assertNotNull(CertificateEngine.buildCancelUpdateValidation(issued, "   "))
        // Valid cancel reason accepted
        assertNull(CertificateEngine.buildCancelUpdateValidation(issued, "Typo in father's name"))

        val cancelled = issued.copy(
            status = CertificateStatus.CANCELLED,
            cancelReason = "Typo in father's name",
            cancelledByUid = "u1",
            cancelledByName = "Office Clerk",
            cancelledAtMs = 2000L
        )
        // Already cancelled certificate cannot be cancelled again
        assertNotNull(CertificateEngine.buildCancelUpdateValidation(cancelled, "Another reason"))

        // Re-issue allocates the next running number while preserving link to cancelled certificate
        val (reissueNo, reissueSeq) = CertificateEngine.generateNextCertificateNumber(
            schoolId = "sch_1",
            session = "2026-2027",
            certificateType = CertificateType.TC,
            deviceNodeCode = "A1B",
            existingCertificates = listOf(cancelled),
            lastLocalSequenceForType = 1
        )
        assertEquals("TC-2627-A1B-0002", reissueNo)
        assertEquals(2, reissueSeq)

        val reissued = issued.copy(
            id = SchoolCertificateRecord.makeDocId("sch_1", "2026-2027", reissueNo),
            certificateNumber = reissueNo,
            sequenceNo = reissueSeq,
            fatherName = "Rajesh Kumar Verma",
            reissuedFromCertId = cancelled.id,
            reissuedFromCertNumber = cancelled.certificateNumber,
            issuedAtMs = 3000L
        )
        assertEquals("cert_1", reissued.reissuedFromCertId)
        assertEquals("TC-2627-A1B-0001", reissued.reissuedFromCertNumber)
        assertFalse(reissued.isCancelled)

        // Filter register: includeCancelled = false hides cancelled original and shows re-issued
        val activeOnly = CertificateEngine.filterRegister(
            certificates = listOf(cancelled, reissued),
            schoolId = "sch_1",
            includeCancelled = false
        )
        assertEquals(listOf(reissued.id), activeOnly.map { it.id })
    }

    // =========================================================================
    // 3. CASH BOOK: TOTALS, AUTO FEE INCOME, DAY CLOSING DIFFERENCE & APPROVAL LIMIT
    // =========================================================================

    @Test
    fun cashBookDaySummaryReadsPhase9FeeReceiptsComputesBalancesAndClosingDifference() {
        val settings = CashBookSettingsConfig.buildDefault("sch_1").copy(defaultOpeningBalanceRs = 2000)

        // Prior day closing had countedCash = 3500 (carried forward as today's opening cash)
        val priorClosing = CashBookDayClosingRecord(
            id = CashBookDayClosingRecord.makeDocId("sch_1", "2026-04-14"),
            schoolId = "sch_1",
            dateIso = "2026-04-14",
            openingCashBalance = 2000,
            cashInTotal = 2000,
            cashOutTotal = 500,
            expectedClosingCash = 3500,
            countedCash = 3450, // actual counted cash carried forward
            cashDifference = -50
        )

        val receipts = listOf(
            makeReceipt("r1", dateIso = "2026-04-15", amountPaid = 1500, mode = PaymentMode.CASH),
            makeReceipt("r2", dateIso = "2026-04-15", amountPaid = 2500, mode = PaymentMode.UPI),
            makeReceipt("r3", dateIso = "2026-04-15", amountPaid = 900, mode = PaymentMode.CASH, status = FeeReceiptStatus.CANCELLED),
            makeReceipt("r4", dateIso = "2026-04-14", amountPaid = 1000, mode = PaymentMode.CASH) // prior day
        )

        val entries = listOf(
            makeCashEntry("e1", dateIso = "2026-04-15", type = CashBookEntryType.OTHER_INCOME, category = "Admission Form Sale", amount = 300, mode = PaymentMode.CASH),
            makeCashEntry("e2", dateIso = "2026-04-15", type = CashBookEntryType.EXPENSE, category = "Electricity", amount = 800, mode = PaymentMode.CASH),
            makeCashEntry("e3", dateIso = "2026-04-15", type = CashBookEntryType.EXPENSE, category = "Stationery", amount = 400, mode = PaymentMode.UPI),
            makeCashEntry("e4", dateIso = "2026-04-15", type = CashBookEntryType.EXPENSE, category = "Repairs", amount = 1200, mode = PaymentMode.CASH, status = CashBookEntryStatus.CANCELLED)
        )

        val summary = CashBookEngine.computeDaySummary(
            schoolId = "sch_1",
            dateIso = "2026-04-15",
            feeReceipts = receipts,
            entries = entries,
            dayClosings = listOf(priorClosing),
            settings = settings
        )

        assertEquals(3450, summary.openingBalance)
        assertEquals(4000, summary.feeIncomeTotal) // 1500 cash + 2500 UPI (cancelled r3 ignored)
        assertEquals(300, summary.otherIncomeTotal)
        assertEquals(4300, summary.totalMoneyIn)
        assertEquals(1200, summary.totalMoneyOut) // 800 cash + 400 UPI (cancelled e4 ignored)
        assertEquals(3100, summary.netDayBalance)
        assertEquals(6550, summary.closingBalance) // 3450 + 3100

        // Cash drawer reconciliation: 3450 opening + (1500 + 300) cash in - 800 cash out = 4450
        assertEquals(1800, summary.totalCashIn)
        assertEquals(800, summary.totalCashOut)
        assertEquals(4450, summary.expectedClosingCash)
        assertEquals(2500, summary.nonCashInTotal)
        assertEquals(400, summary.nonCashOutTotal)

        // Day closing difference checks
        assertEquals(0, CashBookEngine.computeDayClosingDifference(4450, 4450))
        assertEquals(-150, CashBookEngine.computeDayClosingDifference(4450, 4300))
        assertEquals(200, CashBookEngine.computeDayClosingDifference(4450, 4650))

        // Month summary category aggregation
        val month = CashBookEngine.computeMonthSummary("sch_1", "2026-04", receipts, entries)
        assertEquals(5000, month.totalFeeIncome) // r1(1500) + r2(2500) + r4(1000)
        assertEquals(300, month.totalOtherIncome)
        assertEquals(1200, month.totalMoneyOut)
        assertEquals(2, month.categoryTotals.size)
        assertEquals("Electricity", month.categoryTotals[0].category)
        assertEquals(800, month.categoryTotals[0].totalAmount)
        assertEquals("Stationery", month.categoryTotals[1].category)
        assertEquals(400, month.categoryTotals[1].totalAmount)
    }

    @Test
    fun largeExpenseApprovalLimitEnforcedForStaffAndBypassedByAdminOrApproval() {
        val clerk = makeMember(
            uid = "u_clerk",
            role = OrgRole.OFFICE_OPERATOR,
            permissions = MemberPermissions().copy(cashBook = AccessLevel.EDIT)
        )
        val admin = makeMember(uid = "u_admin", role = OrgRole.ADMIN, allSchools = true)

        // Within default 5000 limit: allowed without admin approval
        assertTrue(Phase10AccessEvaluator.isExpenseWithinMemberLimit(clerk, 5000, 5000, hasExplicitAdminApproval = false))
        assertNull(
            CashBookEngine.validateNewOrEditedEntry(
                entryType = CashBookEntryType.EXPENSE,
                category = "Repairs",
                amount = 5000,
                member = clerk,
                approvalLimitRs = 5000,
                approvedByAdminUid = ""
            )
        )

        // Above 5000 limit: blocked for non-admin without approval
        assertFalse(Phase10AccessEvaluator.isExpenseWithinMemberLimit(clerk, 5001, 5000, hasExplicitAdminApproval = false))
        assertNotNull(
            CashBookEngine.validateNewOrEditedEntry(
                entryType = CashBookEntryType.EXPENSE,
                category = "Salary",
                amount = 8500,
                member = clerk,
                approvalLimitRs = 5000,
                approvedByAdminUid = ""
            )
        )

        // Above 5000 limit: allowed once admin approver is provided
        assertTrue(Phase10AccessEvaluator.isExpenseWithinMemberLimit(clerk, 8500, 5000, hasExplicitAdminApproval = true))
        assertNull(
            CashBookEngine.validateNewOrEditedEntry(
                entryType = CashBookEntryType.EXPENSE,
                category = "Salary",
                amount = 8500,
                member = clerk,
                approvalLimitRs = 5000,
                approvedByAdminUid = "u_admin"
            )
        )

        // Admin can record above limit directly without extra approver
        assertTrue(Phase10AccessEvaluator.isExpenseWithinMemberLimit(admin, 25000, 5000, hasExplicitAdminApproval = false))
        assertNull(
            CashBookEngine.validateNewOrEditedEntry(
                entryType = CashBookEntryType.EXPENSE,
                category = "Salary",
                amount = 25000,
                member = admin,
                approvalLimitRs = 5000,
                approvedByAdminUid = ""
            )
        )

        // Other Income above 5000 never requires expense approval
        assertNull(
            CashBookEngine.validateNewOrEditedEntry(
                entryType = CashBookEntryType.OTHER_INCOME,
                category = "Donation",
                amount = 20000,
                member = clerk,
                approvalLimitRs = 5000,
                approvedByAdminUid = ""
            )
        )

        // Editing an entry requires a non-blank edit reason
        assertNotNull(
            CashBookEngine.validateNewOrEditedEntry(
                entryType = CashBookEntryType.EXPENSE,
                category = "Stationery",
                amount = 400,
                member = clerk,
                approvalLimitRs = 5000,
                approvedByAdminUid = "",
                editReason = "   "
            )
        )
    }

    // =========================================================================
    // 4. ADMISSION ENQUIRY: FOLLOW-UP ORDERING, WHATSAPP +91 & CONVERT TO STUDENT
    // =========================================================================

    @Test
    fun enquiryFollowUpOrdersOverdueFirstAndComputesMonthlyConversion() {
        val enqs = listOf(
            AdmissionEnquiryRecord(
                id = "enq_today",
                schoolId = "sch_1",
                session = "2026-2027",
                childName = "Riya Sharma",
                classWanted = "1st",
                guardianName = "Anil Sharma",
                mobile = "9876543210",
                enquiryDateIso = "2026-04-15",
                nextFollowUpDateIso = "2026-04-15",
                status = EnquiryStatus.NEW,
                createdAtMs = 3000L
            ),
            AdmissionEnquiryRecord(
                id = "enq_overdue_recent",
                schoolId = "sch_1",
                session = "2026-2027",
                childName = "Kabir Verma",
                classWanted = "2nd",
                guardianName = "Manoj Verma",
                mobile = "+91 98765-00112",
                enquiryDateIso = "2026-04-10",
                nextFollowUpDateIso = "2026-04-13",
                status = EnquiryStatus.CONTACTED,
                createdAtMs = 2000L
            ),
            AdmissionEnquiryRecord(
                id = "enq_overdue_oldest",
                schoolId = "sch_1",
                session = "2026-2027",
                childName = "Meera Patel",
                classWanted = "Nursery",
                guardianName = "Ramesh Patel",
                mobile = "09811122233",
                enquiryDateIso = "2026-04-05",
                nextFollowUpDateIso = "2026-04-08",
                status = EnquiryStatus.VISIT_PLANNED,
                createdAtMs = 1000L
            ),
            AdmissionEnquiryRecord(
                id = "enq_upcoming",
                schoolId = "sch_1",
                session = "2026-2027",
                childName = "Dev Singh",
                classWanted = "4th",
                guardianName = "Vikram Singh",
                mobile = "9812345678",
                enquiryDateIso = "2026-04-15",
                nextFollowUpDateIso = "2026-04-20",
                status = EnquiryStatus.NEW,
                createdAtMs = 4000L
            ),
            AdmissionEnquiryRecord(
                id = "enq_admitted",
                schoolId = "sch_1",
                session = "2026-2027",
                childName = "Ananya Gupta",
                classWanted = "3rd",
                guardianName = "Suresh Gupta",
                mobile = "9876511122",
                enquiryDateIso = "2026-04-03",
                nextFollowUpDateIso = "2026-04-04",
                status = EnquiryStatus.ADMITTED,
                convertedStudentDocId = "stu_99",
                convertedStudentAdmNo = "ADM-099",
                createdAtMs = 500L
            ),
            AdmissionEnquiryRecord(
                id = "enq_lost",
                schoolId = "sch_1",
                session = "2026-2027",
                childName = "Rohan Das",
                classWanted = "5th",
                guardianName = "Prakash Das",
                mobile = "9876522233",
                enquiryDateIso = "2026-04-06",
                nextFollowUpDateIso = "2026-04-07",
                status = EnquiryStatus.LOST,
                lostReason = "Shifted to another city",
                createdAtMs = 600L
            )
        )

        val queue = AdmissionEnquiryEngine.buildFollowUpQueue(
            enquiries = enqs,
            schoolId = "sch_1",
            schoolName = "C.P.S. School",
            todayIso = "2026-04-15",
            template = EnquirySettingsConfig.DEFAULT_WHATSAPP_TEMPLATE,
            includeUpcoming = false
        )

        // Must contain ONLY overdue + today, ordered oldest overdue -> newer overdue -> today
        assertEquals(
            listOf("enq_overdue_oldest", "enq_overdue_recent", "enq_today"),
            queue.map { it.enquiry.id }
        )
        assertEquals(FollowUpUrgency.OVERDUE, queue[0].urgency)
        assertEquals(7, queue[0].daysOverdue)
        assertEquals(FollowUpUrgency.OVERDUE, queue[1].urgency)
        assertEquals(2, queue[1].daysOverdue)
        assertEquals(FollowUpUrgency.DUE_TODAY, queue[2].urgency)
        assertEquals(0, queue[2].daysOverdue)

        // WhatsApp wa.me link uses +91 normalisation and fills editable template
        assertTrue(queue[0].phoneValidation is PhoneValidationResult.Valid)
        assertTrue(queue[0].waMeUrl!!.startsWith("https://wa.me/919811122233?text="))
        assertTrue(queue[0].whatsappMessageText.contains("Ramesh Patel"))
        assertTrue(queue[0].whatsappMessageText.contains("Meera Patel"))
        assertTrue(queue[0].whatsappMessageText.contains("Nursery"))
        assertTrue(queue[0].whatsappMessageText.contains("C.P.S. School"))

        // Monthly summary: 6 enquiries in 2026-04, 1 admitted -> 16.7%
        val summary = AdmissionEnquiryEngine.computeMonthlySummary(enqs, "sch_1", todayIso = "2026-04-15")
        assertEquals(6, summary.totalEnquiriesThisMonth)
        assertEquals(1, summary.admittedCount)
        assertEquals(1, summary.lostCount)
        assertEquals(4, summary.openCount)
        assertEquals(2, summary.overdueCount)
        assertEquals(1, summary.dueTodayCount)
        assertEquals(16.7, summary.conversionPercent, 0.01)
    }

    @Test
    fun convertEnquiryToStudentPreservesDetailsAndNormalizesMobile() {
        val enquiry = AdmissionEnquiryRecord(
            id = "enq_42",
            schoolId = "sch_1",
            session = "2026-2027",
            childName = "  Aarav Singh  ",
            classWanted = "3rd",
            guardianName = "  Dinesh Singh ",
            mobile = "+91 98765-43210",
            source = "Parent Referral",
            notes = "Needs transport",
            enquiryDateIso = "2026-04-10",
            nextFollowUpDateIso = "2026-04-12",
            status = EnquiryStatus.VISIT_PLANNED
        )

        val student = AdmissionEnquiryEngine.buildStudentFromEnquiry(
            enquiry = enquiry,
            schoolLongId = 1L,
            classId = 3L,
            session = "2026-2027",
            admNo = "ADM-2026-042",
            section = "b",
            rollNo = 14,
            admissionDateIso = "2026-04-15"
        )

        assertEquals("Aarav Singh", student.name)
        assertEquals("Dinesh Singh", student.fatherName)
        assertEquals("3rd", student.className)
        assertEquals("B", student.section)
        assertEquals(14, student.rollNo)
        assertEquals("ADM-2026-042", student.admNo)
        assertEquals("9876543210", student.mobile)
        assertEquals("2026-04-15", student.admissionDate)
        assertEquals(StudentRegisterStatus.ACTIVE.code, student.status)
    }
}
