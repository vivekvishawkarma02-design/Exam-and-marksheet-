package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ClassGroupEntity
import com.example.data.MarkEntity
import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.data.SubjectEntity
import com.example.domain.AccessLevel
import com.example.domain.BackupExportDataset
import com.example.domain.BackupExportScope
import com.example.domain.BackupExportZipEngine
import com.example.domain.BackupReminderEngine
import com.example.domain.BulkImportExecutionReport
import com.example.domain.BulkStudentImportEngine
import com.example.domain.CalendarHoliday
import com.example.domain.CashBookEntryRecord
import com.example.domain.CashBookEntryStatus
import com.example.domain.CashBookEntryType
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionScope
import com.example.domain.FeeCollectionEngine
import com.example.domain.FeeHeadType
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeReceiptStatus
import com.example.domain.FeeReportsAndQueueEngine
import com.example.domain.FeeStructureConfig
import com.example.domain.ImportRowAction
import com.example.domain.ImportRowProblemKind
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.NewSessionDataGuardSummary
import com.example.domain.OrgRole
import com.example.domain.PaymentMode
import com.example.domain.Phase14AccessEvaluator
import com.example.domain.PromotionDecision
import com.example.domain.PromotionEngine
import com.example.domain.ReportCalculations
import com.example.domain.RollAssignmentMode
import com.example.domain.SalaryMonthlyRecord
import com.example.domain.SalaryPaymentStatus
import com.example.domain.SchoolCalendarConfig
import com.example.domain.SessionCopyOptions
import com.example.domain.SessionMonthCode
import com.example.domain.SessionTransitionEngine
import com.example.domain.SessionTransitionRecord
import com.example.domain.SessionTransitionStatus
import com.example.domain.StudentImportField
import com.example.domain.TeacherProfile
import com.example.util.Phase14PdfGenerators
import com.example.util.PromotionRegisterPdfBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Comprehensive Phase 14 JVM & Robolectric test suite covering:
 * - Access control ('sessionPromotion' missing key = none; Bulk Import & Backup = Owner/Admin only;
 *   Salary CSV in backup = Owner only; 7-day Undo = Owner only)
 * - New Session suggestion, validation, copy options, calendar +1 year holiday shift
 * - Promotion engine reusing ReportCalculations (exact 33% per-subject rule), final class -> Alumni,
 *   Needs Review when marks missing, Grace remark validation, Bulk Promote All Passed exceptions-first,
 *   Roll assignment (previous roll / alphabetical / manual override), full carry-over of student fields
 * - Unpaid fee dues carry-forward into "Previous session dues" (collectable, Dues List & Send Queue)
 * - Safe chunked batching & deterministic IDs for offline conflict-free multi-device promotion
 * - 7-day Owner Undo eligibility & new-session data guard warning
 * - Student session history & Alumni search
 * - Bulk Student Import (CSV + real in-memory .xlsx ZIP/XML fixture, Hindi+English auto-map,
 *   validation problems, +91 normalization, no-overwrite default vs update-matching diff preview)
 * - Manual Backup ZIP export (entry verification, Owner-only staff_salary.csv, 30-day reminder)
 * - Offline A4 PDF generation (Promotion Register & Bulk Import Report)
 * - Past-session regression check: old session marks, report calculations & fee accounts untouched
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase14SessionPromotionImportBackupTest {

    private val schoolId = "sch_primary_01"
    private val schoolStableId = 101L
    private val oldSession = "2026-2027"
    private val newSession = "2027-2028"
    private val defaultOrder = SessionTransitionEngine.DEFAULT_CLASS_ORDER

    private fun makeMember(
        uid: String,
        role: OrgRole,
        allSchools: Boolean = true,
        schools: List<String> = listOf(schoolId),
        perms: MemberPermissions = MemberPermissions.presetFor(role)
    ): MemberAccessProfile = MemberAccessProfile(
        uid = uid,
        role = role,
        status = MemberStatus.ACTIVE,
        allSchools = allSchools,
        allowedSchoolIds = schools,
        permissions = perms
    )

    private fun makeStudent(
        id: Long,
        admNo: String,
        name: String,
        className: String,
        section: String = "A",
        rollNo: Int = 1,
        session: String = oldSession,
        status: String = "ACTIVE",
        usesTransport: Boolean = false,
        transportAmt: Int = 0
    ): StudentEntity = StudentEntity(
        id = id,
        schoolId = schoolStableId,
        classId = 1L,
        session = session,
        className = className,
        section = section,
        rollNo = rollNo,
        name = name,
        fatherName = "Father of $name",
        motherName = "Mother of $name",
        dob = "2016-05-10",
        admNo = admNo,
        mobile = "+919876543210",
        address = "Main Street",
        photoBase64 = "BASE64_PHOTO_$admNo",
        siblingGroupId = "sib_01",
        usesTransport = usesTransport,
        transportMonthlyAmount = transportAmt,
        transportRouteId = if (usesTransport) "rt_1" else "",
        transportRouteName = if (usesTransport) "Route 1" else "",
        status = status
    )

    /** Creates full annual marks across all 6 components (T1+T2 = 200 per subject) for a given percent. */
    private fun makeUniformAnnualMarks(studentId: Long, subjects: List<String>, percentPerSubject: Map<String, Int>): List<MarkEntity> {
        val out = mutableListOf<MarkEntity>()
        for (sub in subjects) {
            val pct = percentPerSubject[sub] ?: 60
            val ia = minOf(10, pct)
            val rem1 = (pct - ia).coerceAtLeast(0)
            val ut = minOf(30, rem1)
            val ex = (rem1 - ut).coerceAtLeast(0)
            listOf(
                "T1_IA" to ia,
                "T1_UT" to ut,
                "T1_EXAM" to ex,
                "T2_IA" to ia,
                "T2_UT" to ut,
                "T2_EXAM" to ex
            ).forEach { (key, value) ->
                out += MarkEntity(studentId = studentId, subjectName = sub, componentKey = key, rawValue = value.toString())
            }
        }
        return out
    }

    private fun makeSimpleFeeStructure(session: String = oldSession): FeeStructureConfig {
        val allMonths = SessionMonthCode.defaultAprilToMarchOrder
        return FeeStructureConfig(
            id = FeeStructureConfig.makeDocId(schoolId, session),
            schoolId = schoolId,
            session = session,
            tuitionByClassAndMonth = mapOf(
                "5th" to allMonths.associateWith { 500 },
                "6th" to allMonths.associateWith { 600 },
                "8th" to allMonths.associateWith { 800 }
            )
        )
    }

    // =========================================================================
    // 1. RBAC & MISSING-KEY ACCESS RULES
    // =========================================================================

    @Test
    fun `access evaluator enforces sessionPromotion missing key, Owner-Admin gates, Owner-only salary and undo`() {
        val owner = makeMember("u_owner", OrgRole.OWNER)
        val admin = makeMember("u_admin", OrgRole.ADMIN)
        val legacyPerms = MemberPermissions.fromMap(
            mapOf("students" to "edit", "marks" to "edit") // intentionally missing 'sessionPromotion'
        )
        assertEquals(AccessLevel.NONE, legacyPerms.sessionPromotion)

        val legacyMember = makeMember("u_legacy", OrgRole.OFFICE_OPERATOR, perms = legacyPerms)
        val sessionViewer = makeMember(
            "u_view",
            OrgRole.OFFICE_OPERATOR,
            allSchools = false,
            schools = listOf(schoolId),
            perms = MemberPermissions(sessionPromotion = AccessLevel.VIEW)
        )
        val sessionEditor = makeMember(
            "u_edit",
            OrgRole.OFFICE_OPERATOR,
            allSchools = false,
            schools = listOf(schoolId),
            perms = MemberPermissions(sessionPromotion = AccessLevel.EDIT)
        )

        // Missing key -> no access
        assertFalse(Phase14AccessEvaluator.canViewSessionPromotion(legacyMember, schoolId))
        assertFalse(Phase14AccessEvaluator.canEditSessionPromotion(legacyMember, schoolId))

        // Viewer -> view only on assigned school
        assertTrue(Phase14AccessEvaluator.canViewSessionPromotion(sessionViewer, schoolId))
        assertFalse(Phase14AccessEvaluator.canViewSessionPromotion(sessionViewer, "sch_other"))
        assertFalse(Phase14AccessEvaluator.canEditSessionPromotion(sessionViewer, schoolId))

        // Editor -> view & edit on assigned school, NO bulk import / backup / undo
        assertTrue(Phase14AccessEvaluator.canEditSessionPromotion(sessionEditor, schoolId))
        assertFalse(Phase14AccessEvaluator.canEditSessionPromotion(sessionEditor, "sch_other"))
        assertFalse(Phase14AccessEvaluator.canUseBulkStudentImport(sessionEditor))
        assertFalse(Phase14AccessEvaluator.canUseBackupExport(sessionEditor))
        assertFalse(Phase14AccessEvaluator.canUndoSessionStart(sessionEditor))
        assertFalse(Phase14AccessEvaluator.canIncludeSalaryInBackup(sessionEditor))

        // Admin -> full sessionPromotion, bulk import & backup, NO salary CSV, NO 7-day Undo
        assertTrue(Phase14AccessEvaluator.canEditSessionPromotion(admin, schoolId))
        assertTrue(Phase14AccessEvaluator.canUseBulkStudentImport(admin))
        assertTrue(Phase14AccessEvaluator.canUseBackupExport(admin))
        assertFalse(Phase14AccessEvaluator.canIncludeSalaryInBackup(admin))
        assertFalse(Phase14AccessEvaluator.canUndoSessionStart(admin))

        // Owner -> full access to everything including salary CSV and 7-day Undo
        assertTrue(Phase14AccessEvaluator.canEditSessionPromotion(owner, schoolId))
        assertTrue(Phase14AccessEvaluator.canUseBulkStudentImport(owner))
        assertTrue(Phase14AccessEvaluator.canUseBackupExport(owner))
        assertTrue(Phase14AccessEvaluator.canIncludeSalaryInBackup(owner))
        assertTrue(Phase14AccessEvaluator.canUndoSessionStart(owner))
    }

    // =========================================================================
    // 2. NEW SESSION SUGGESTION, VALIDATION & COPY HELPERS
    // =========================================================================

    @Test
    fun `session suggestion, validation, fee structure copy and calendar holiday shift work cleanly`() {
        val suggested = SessionTransitionEngine.suggestNextSession("2026-2027")
        assertEquals("2027-2028", suggested.sessionName)
        assertEquals("2027-04-01", suggested.startDateIso)
        assertEquals("2028-03-31", suggested.endDateIso)

        assertNull(SessionTransitionEngine.validateSessionNameAndDates("2026-2027", "2027-2028", "2027-04-01", "2028-03-31"))
        assertNotNull(SessionTransitionEngine.validateSessionNameAndDates("2026-2027", "2026-2027", "2026-04-01", "2027-03-31"))
        assertNotNull(SessionTransitionEngine.validateSessionNameAndDates("2026-2027", "2027-2029", "2027-04-01", "2028-03-31"))
        assertNotNull(SessionTransitionEngine.validateSessionNameAndDates("2026-2027", "2027-2028", "2028-04-01", "2028-03-31"))

        // Copy fee structure clears frozen past cells while preserving schedules
        val oldFee = makeSimpleFeeStructure(oldSession).copy(
            frozenPastCellAmountsByClass = mapOf("5th|APR|TUITION" to 450)
        )
        val copiedFee = SessionTransitionEngine.copyFeeStructureForNewSession(oldFee, schoolId, newSession, listOf("5th", "6th"))
        assertEquals(newSession, copiedFee.session)
        assertEquals(500, copiedFee.tuitionByClassAndMonth["5th"]?.get("APR"))
        assertTrue(copiedFee.frozenPastCellAmountsByClass.isEmpty())

        // Copy school calendar shifts holiday dates by +1 year
        val oldCal = SchoolCalendarConfig(
            id = SchoolCalendarConfig.makeDocId(schoolId, oldSession),
            schoolId = schoolId,
            session = oldSession,
            holidays = listOf(CalendarHoliday(id = "h1", name = "Independence Day", startDate = "2026-08-15", endDate = "2026-08-15"))
        )
        val copiedCal = SessionTransitionEngine.copySchoolCalendarForNewSession(
            oldCalendar = oldCal,
            schoolId = schoolId,
            toSession = newSession,
            startDateIso = "2027-04-01",
            endDateIso = "2028-03-31"
        )
        assertEquals(newSession, copiedCal.session)
        assertEquals(1, copiedCal.holidays.size)
        assertEquals("2027-08-15", copiedCal.holidays.first().startDate)
        assertEquals("2027-08-15", copiedCal.holidays.first().endDate)
    }

    // =========================================================================
    // 3. PROMOTION ENGINE, 33% RULE REUSE, GRACE, ROLLS, CARRY-OVER & DUES
    // =========================================================================

    @Test
    fun `promotion reuses exact 33 percent report card rule, handles Alumni, Grace, Bulk Promote, Roll order, Carry-Over and Dues`() {
        val subjects = listOf("Hindi", "English", "Maths")
        val sPass = makeStudent(1L, "ADM-01", "Zoya Khan", "5th", "A", 1, usesTransport = true, transportAmt = 300)
        val sFail = makeStudent(2L, "ADM-02", "Aarav Verma", "5th", "A", 2)
        val sNoMarks = makeStudent(3L, "ADM-03", "Bhanu Singh", "5th", "A", 3)
        val sLeft = makeStudent(4L, "ADM-04", "Chandan Roy", "5th", "A", 4, status = "LEFT")
        val sEighthPass = makeStudent(5L, "ADM-05", "Divya Patel", "8th", "A", 1)

        val marksByStudent = mapOf(
            // Exact 33% boundary -> PASS -> PROMOTED
            1L to makeUniformAnnualMarks(1L, subjects, mapOf("Hindi" to 33, "English" to 33, "Maths" to 33)),
            // One subject 32% -> FAIL -> DETAINED
            2L to makeUniformAnnualMarks(2L, subjects, mapOf("Hindi" to 80, "English" to 80, "Maths" to 32)),
            // No marks for student 3 -> NEEDS_REVIEW
            // 8th pass -> PASSED_OUT (Alumni)
            5L to makeUniformAnnualMarks(5L, subjects, mapOf("Hindi" to 70, "English" to 75, "Maths" to 80))
        )

        val oldFeeStructure = makeSimpleFeeStructure(oldSession)
        val oldAccount1 = FeeCollectionEngine.computeStudentFeeAccount(oldFeeStructure, sPass, "stu_old_1", null, emptyList())
        assertTrue(oldAccount1.totalRemainingDue > 0)

        val drafts = PromotionEngine.buildPromotionDraftsForSchool(
            schoolDocId = schoolId,
            fromSession = oldSession,
            studentsInFromSession = listOf(
                "stu_old_1" to sPass,
                "stu_old_2" to sFail,
                "stu_old_3" to sNoMarks,
                "stu_old_4" to sLeft,
                "stu_old_5" to sEighthPass
            ),
            subjectsByClassName = mapOf("5th" to subjects, "8th" to subjects),
            marksByStudentId = marksByStudent,
            passPercent = 33,
            classOrder = defaultOrder,
            oldFeeStructure = oldFeeStructure,
            oldFeeOverridesByStudentDocId = emptyMap(),
            oldReceiptsByStudentDocId = emptyMap()
        )

        val byAdm = drafts.associateBy { it.student.admNo }
        assertEquals(PromotionDecision.PROMOTED, byAdm["ADM-01"]?.proposedDecision)
        assertEquals("6th", byAdm["ADM-01"]?.toClassName)
        assertEquals(oldAccount1.totalRemainingDue, byAdm["ADM-01"]?.carriedForwardDueRs)

        assertEquals(PromotionDecision.DETAINED, byAdm["ADM-02"]?.proposedDecision)
        assertEquals("5th", byAdm["ADM-02"]?.toClassName)

        assertEquals(PromotionDecision.NEEDS_REVIEW, byAdm["ADM-03"]?.proposedDecision)
        assertEquals(PromotionDecision.LEFT, byAdm["ADM-04"]?.proposedDecision)
        assertEquals(PromotionDecision.PASSED_OUT, byAdm["ADM-05"]?.proposedDecision)

        // Bulk "Promote all passed" returns exceptions first (NEEDS_REVIEW, DETAINED, LEFT)
        val bulkRes = PromotionEngine.applyBulkPromoteAllPassed(drafts, defaultOrder)
        assertEquals(1, bulkRes.promotedCount)
        assertEquals(1, bulkRes.passedOutCount)
        assertEquals(3, bulkRes.exceptions.size)
        assertEquals(PromotionDecision.NEEDS_REVIEW, bulkRes.exceptions.first().decision)

        // Grace promotion requires non-empty remark + approver
        assertNotNull(PromotionEngine.validateGracePromotion("", "u_owner", "Principal"))
        assertNull(PromotionEngine.validateGracePromotion("Approved on medical grounds", "u_owner", "Principal"))

        // Resolve ADM-02 as Grace Promoted to 6th and ADM-03 as Detained in 5th
        val resolved = bulkRes.updatedDrafts.map { d ->
            when (d.student.admNo) {
                "ADM-02" -> PromotionEngine.updateSingleDraftDecision(
                    d, PromotionDecision.PROMOTED_BY_GRACE, defaultOrder,
                    graceRemark = "Medical leave approved", approverUid = "u_owner", approverName = "Principal"
                )
                "ADM-03" -> PromotionEngine.updateSingleDraftDecision(d, PromotionDecision.DETAINED, defaultOrder)
                else -> d
            }
        }

        // Alphabetical roll numbering in 6th-A: Aarav Verma (ADM-02) = Roll 1, Zoya Khan (ADM-01) = Roll 2
        val alphaRolls = PromotionEngine.assignSectionsAndRollNumbers(resolved, RollAssignmentMode.ALPHABETICAL_BY_NAME)
        val alphaByAdm = alphaRolls.associateBy { it.student.admNo }
        assertEquals(1, alphaByAdm["ADM-02"]?.toRollNo)
        assertEquals(2, alphaByAdm["ADM-01"]?.toRollNo)

        // Build deterministic chunked batches
        val planRes = PromotionEngine.buildPromotionBatches(
            drafts = alphaRolls,
            schoolId = schoolId,
            schoolStableId = schoolStableId,
            fromSession = oldSession,
            toSession = newSession,
            classIdByClassName = mapOf("5th" to 5L, "6th" to 6L),
            confirmedByUid = "u_owner",
            confirmedByName = "Principal",
            confirmedAtMs = 1_800_000_000_000L,
            batchSize = 5
        )
        assertTrue(planRes.isSuccess)
        val plan = planRes.getOrThrow()
        assertEquals(1, plan.promotedCount)
        assertEquals(1, plan.gracePromotedCount)
        assertEquals(1, plan.detainedCount)
        assertEquals(1, plan.leftCount)
        assertEquals(1, plan.passedOutCount)

        // Verify carry-over of admNo, photo, guardian details, sibling group, and transport assignment
        val zoyaPlan = plan.chunks.flatMap { it.items }.first { it.student.admNo == "ADM-01" }
        assertEquals(newSession, zoyaPlan.student.session)
        assertEquals("6th", zoyaPlan.student.className)
        assertEquals("BASE64_PHOTO_ADM-01", zoyaPlan.student.photoBase64)
        assertEquals("sib_01", zoyaPlan.student.siblingGroupId)
        assertTrue(zoyaPlan.student.usesTransport)
        assertEquals(300, zoyaPlan.student.transportMonthlyAmount)
        assertEquals("Route 1", zoyaPlan.student.transportRouteName)
        assertNotNull(zoyaPlan.carriedForwardOverride)
        assertEquals(oldAccount1.totalRemainingDue, zoyaPlan.carriedForwardOverride!!.previousSessionDueAmount)

        // Verify Previous Session Dues line shows on new-session account, is collectable, and shows in Dues List!
        val newFeeStructure = makeSimpleFeeStructure(newSession)
        val newAccountBeforePay = FeeCollectionEngine.computeStudentFeeAccount(
            structure = newFeeStructure,
            student = zoyaPlan.student,
            studentDocId = zoyaPlan.newStudentDocId,
            override = zoyaPlan.carriedForwardOverride,
            receiptsForStudent = emptyList()
        )
        val prevDueCell = newAccountBeforePay.previousSessionDueCell
        assertNotNull(prevDueCell)
        assertEquals(FeeHeadType.PREVIOUS_SESSION_DUE, prevDueCell!!.headType)
        assertEquals("PREV", prevDueCell.monthCode)
        assertEquals(oldAccount1.totalRemainingDue, prevDueCell.remainingDue)

        // Collect the previous session due via FeeCollectionEngine.computePaymentDraft
        val payDraft = FeeCollectionEngine.computePaymentDraft(
            selectedGridCells = listOf(prevDueCell),
            selectedOptionalItems = emptyList(),
            customCharges = emptyList(),
            concessionMode = ConcessionMode.NONE,
            concessionScope = ConcessionScope.WHOLE_RECEIPT
        )
        assertEquals(oldAccount1.totalRemainingDue, payDraft.netPayableAmount)

        val receipt = FeeReceiptRecord(
            id = "rcp_prev_1",
            schoolId = schoolId,
            session = newSession,
            receiptNumber = "RCP-2728-A1B-0001",
            deviceNodeCode = "A1B",
            sequenceNo = 1,
            receiptDateIso = "2027-04-05",
            studentId = zoyaPlan.newStudentDocId,
            studentName = zoyaPlan.student.name,
            studentAdmNo = zoyaPlan.student.admNo,
            fatherName = zoyaPlan.student.fatherName,
            className = zoyaPlan.student.className,
            section = zoyaPlan.student.section,
            rollNo = zoyaPlan.student.rollNo,
            lineItems = payDraft.allocatedLines,
            totalBeforeConcession = payDraft.totalBeforeConcession,
            concessionMode = ConcessionMode.NONE,
            concessionScope = ConcessionScope.WHOLE_RECEIPT,
            netPayableAmount = payDraft.netPayableAmount,
            amountPaid = payDraft.amountPaid,
            balanceDueOnReceipt = 0,
            paymentMode = PaymentMode.CASH,
            status = FeeReceiptStatus.ISSUED,
            receivedByUid = "u_owner",
            receivedByName = "Principal",
            issuedAtMs = 1_800_100_000_000L
        )
        val newAccountAfterPay = FeeCollectionEngine.computeStudentFeeAccount(
            structure = newFeeStructure,
            student = zoyaPlan.student,
            studentDocId = zoyaPlan.newStudentDocId,
            override = zoyaPlan.carriedForwardOverride,
            receiptsForStudent = listOf(receipt)
        )
        assertEquals(0, newAccountAfterPay.previousSessionDueCell?.remainingDue)

        // Before paying, Dues List includes Previous Session Dues!
        val duesRows = FeeReportsAndQueueEngine.buildDuesList(
            studentsWithDocIds = listOf(zoyaPlan.newStudentDocId to zoyaPlan.student),
            schoolDocId = schoolId,
            schoolName = "Demo School",
            structure = newFeeStructure,
            overridesByStudentDocId = mapOf(zoyaPlan.newStudentDocId to zoyaPlan.carriedForwardOverride),
            receipts = emptyList()
        )
        assertEquals(1, duesRows.size)
        assertEquals(oldAccount1.totalRemainingDue, duesRows.first().dueByHead[FeeHeadType.PREVIOUS_SESSION_DUE])

        // Offline multi-device determinism: second device running buildPromotionBatches gets IDENTICAL doc IDs!
        val planDevice2 = PromotionEngine.buildPromotionBatches(
            drafts = alphaRolls,
            schoolId = schoolId,
            schoolStableId = schoolStableId,
            fromSession = oldSession,
            toSession = newSession,
            classIdByClassName = mapOf("5th" to 5L, "6th" to 6L),
            confirmedByUid = "u_admin_2",
            confirmedByName = "Admin B",
            confirmedAtMs = 1_800_000_500_000L,
            batchSize = 5
        ).getOrThrow()
        assertEquals(plan.transitionId, planDevice2.transitionId)
        assertEquals(plan.createdStudentDocIds, planDevice2.createdStudentDocIds)
        assertEquals(plan.createdFeeOverrideDocIds, planDevice2.createdFeeOverrideDocIds)
        assertEquals(plan.createdPromotionRecordIds, planDevice2.createdPromotionRecordIds)

        // Student Session History & Alumni search
        val allProms = plan.chunks.flatMap { it.items.map { i -> i.promotionRecord } + it.nonMovedPromotionRecords }
        val history = PromotionEngine.buildStudentSessionHistory(
            admNo = "ADM-01",
            studentName = "Zoya Khan",
            fatherName = sPass.fatherName,
            allStudentsInSchool = listOf(sPass, zoyaPlan.student),
            promotionRecordsInSchool = allProms
        )
        assertEquals(2, history.size)
        assertEquals("5th", history[0].className)
        assertEquals("6th", history[1].className)

        val alumniMatches = PromotionEngine.searchAlumni(allProms, "Divya")
        assertEquals(1, alumniMatches.size)
        assertEquals("ADM-05", alumniMatches.first().admNo)
    }

    // =========================================================================
    // 4. OWNER 7-DAY UNDO ELIGIBILITY & NEW-DATA WARNING GUARD
    // =========================================================================

    @Test
    fun `7-day undo is Owner-only, enforces 7-day expiry, and warns when new-session data exists`() {
        val appliedAt = 1_800_000_000_000L
        val transition = SessionTransitionRecord(
            id = "strans_1",
            schoolId = schoolId,
            fromSession = oldSession,
            toSession = newSession,
            startDateIso = "2027-04-01",
            endDateIso = "2028-03-31",
            status = SessionTransitionStatus.COMPLETED,
            copyOptions = SessionCopyOptions(),
            appliedByUid = "u_owner",
            appliedByName = "Principal",
            appliedAtMs = appliedAt,
            undoExpiresAtMs = appliedAt + SessionTransitionEngine.UNDO_WINDOW_MS
        )

        // Admin cannot undo
        val adminCheck = SessionTransitionEngine.evaluateUndoEligibility(
            transition = transition,
            nowMs = appliedAt + 1000L,
            isOwner = false,
            guardSummary = NewSessionDataGuardSummary()
        )
        assertFalse(adminCheck.allowed)

        // Owner within 7 days, no new data -> allowed, no warning
        val ownerClean = SessionTransitionEngine.evaluateUndoEligibility(
            transition = transition,
            nowMs = appliedAt + 2 * 86_400_000L,
            isOwner = true,
            guardSummary = NewSessionDataGuardSummary()
        )
        assertTrue(ownerClean.allowed)
        assertFalse(ownerClean.requiresNewDataWarning)

        // Owner within 7 days, new receipts entered -> allowed WITH mandatory new-data warning
        val ownerWithNewData = SessionTransitionEngine.evaluateUndoEligibility(
            transition = transition,
            nowMs = appliedAt + 2 * 86_400_000L,
            isOwner = true,
            guardSummary = NewSessionDataGuardSummary(feeReceiptsCount = 3, attendanceSheetsCount = 1)
        )
        assertTrue(ownerWithNewData.allowed)
        assertTrue(ownerWithNewData.requiresNewDataWarning)

        // Owner after 7 days -> expired
        val ownerExpired = SessionTransitionEngine.evaluateUndoEligibility(
            transition = transition,
            nowMs = appliedAt + 8 * 86_400_000L,
            isOwner = true,
            guardSummary = NewSessionDataGuardSummary()
        )
        assertFalse(ownerExpired.allowed)
    }

    // =========================================================================
    // 5. BULK STUDENT IMPORT (CSV + REAL XLSX, +91, VALIDATION, UPDATE DIFF)
    // =========================================================================

    @Test
    fun `bulk import parses bilingual CSV and real XLSX, normalizes +91, detects problems, and previews update diffs`() {
        val sampleCsv = BulkStudentImportEngine.buildSampleCsv()
        val parsedCsv = BulkStudentImportEngine.parseCsvText(sampleCsv, "sample.csv").getOrThrow()
        assertEquals(2, parsedCsv.rows.size)
        val autoMap = BulkStudentImportEngine.autoMapColumns(parsedCsv.headers)
        assertTrue(autoMap.containsKey(StudentImportField.NAME))
        assertTrue(autoMap.containsKey(StudentImportField.CLASS_NAME))
        assertTrue(autoMap.containsKey(StudentImportField.MOBILE))
        assertTrue(autoMap.containsKey(StudentImportField.ADM_NO))

        val existingStudent = makeStudent(99L, "ADM-101", "Aarav Sharma", "1st", "A", 1, session = newSession)
        val messyCsv = """
            Student Name,Class,Section,Roll No,Admission No,Father Name,Guardian Mobile,Address,Uses Transport,Transport Amount
            Aarav Sharma,1st,A,1,ADM-101,Rajesh Sharma,9839100001,New Colony,Yes,450
            ,1st,A,2,ADM-201,Mohan,9839100002,Town,No,0
            Riya Gupta,99th,A,3,ADM-202,Suresh,12345,Town,No,0
            Karan Singh,2nd,A,4,ADM-301,Vijay,09839100003,Village,Yes,350
            Duplicate File Adm,2nd,A,5,ADM-301,Ram,9839100004,Village,No,0
        """.trimIndent()
        val messyParsed = BulkStudentImportEngine.parseCsvText(messyCsv, "messy.csv").getOrThrow()
        val messyMap = BulkStudentImportEngine.autoMapColumns(messyParsed.headers)

        val previewNoOverwrite = BulkStudentImportEngine.validateAndPreviewRows(
            rawRows = messyParsed.rows,
            mapping = messyMap,
            validClassNames = listOf("1st", "2nd", "3rd"),
            existingStudentsInSession = listOf("stu_existing_101" to existingStudent),
            updateMatchingAdmNo = false
        )
        assertEquals(1, previewNoOverwrite.addCount)
        assertEquals(0, previewNoOverwrite.updateCount)
        assertEquals(4, previewNoOverwrite.errorCount)
        assertTrue(previewNoOverwrite.rows[0].problems.any { it.kind == ImportRowProblemKind.EXISTING_ADM_CONFLICT })
        assertTrue(previewNoOverwrite.rows[1].problems.any { it.kind == ImportRowProblemKind.MISSING_NAME })
        assertTrue(previewNoOverwrite.rows[2].problems.any { it.kind == ImportRowProblemKind.INVALID_CLASS })
        assertTrue(previewNoOverwrite.rows[2].problems.any { it.kind == ImportRowProblemKind.INVALID_MOBILE })
        assertEquals("+919839100003", previewNoOverwrite.rows[3].normalizedMobile)
        assertTrue(previewNoOverwrite.rows[4].problems.any { it.kind == ImportRowProblemKind.DUPLICATE_ADM_IN_FILE })

        val previewWithUpdate = BulkStudentImportEngine.validateAndPreviewRows(
            rawRows = messyParsed.rows,
            mapping = messyMap,
            validClassNames = listOf("1st", "2nd", "3rd"),
            existingStudentsInSession = listOf("stu_existing_101" to existingStudent),
            updateMatchingAdmNo = true
        )
        assertEquals(ImportRowAction.UPDATE, previewWithUpdate.rows[0].action)
        assertTrue(previewWithUpdate.rows[0].updateDiffs.any { it.fieldLabel == "Address" && it.newValue == "New Colony" })
        assertTrue(previewWithUpdate.rows[0].updateDiffs.any { it.fieldLabel == "Transport" && it.newValue == "Yes" })

        val xlsxBytes = buildMinimalXlsxBytes(
            sharedStrings = listOf("छात्र का नाम", "कक्षा", "अभिभावक मोबाइल", "प्रवेश संख्या", "Meera Devi", "3rd", "ADM-500"),
            sheetXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>2</v></c>
                      <c r="D1" t="s"><v>3</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>4</v></c>
                      <c r="B2" t="s"><v>5</v></c>
                      <c r="C2"><v>9876543210.0</v></c>
                      <c r="D2" t="s"><v>6</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )
        val xlsxParsed = BulkStudentImportEngine.parseXlsxStream(ByteArrayInputStream(xlsxBytes), "admissions.xlsx").getOrThrow()
        assertEquals("XLSX", xlsxParsed.format)
        assertEquals(1, xlsxParsed.rows.size)
        assertEquals("Meera Devi", xlsxParsed.rows[0][0])
        assertEquals("9876543210", xlsxParsed.rows[0][2])
        val hindiMap = BulkStudentImportEngine.autoMapColumns(xlsxParsed.headers)
        assertEquals(0, hindiMap[StudentImportField.NAME])
        assertEquals(1, hindiMap[StudentImportField.CLASS_NAME])
        assertEquals(2, hindiMap[StudentImportField.MOBILE])
        assertEquals(3, hindiMap[StudentImportField.ADM_NO])
    }

    private fun buildMinimalXlsxBytes(sharedStrings: List<String>, sheetXml: String): ByteArray {
        val ssXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            for (s in sharedStrings) {
                append("<si><t>").append(s).append("</t></si>")
            }
            append("</sst>")
        }
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zos.write(ssXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sheetXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    // =========================================================================
    // 6. MANUAL BACKUP ZIP (OWNER-ONLY SALARY CSV) & 30-DAY REMINDER
    // =========================================================================

    @Test
    fun `backup ZIP includes README and CSVs, includes staff_salary only for Owner, excludes for Admin, and rejects Staff`() {
        val school = SchoolEntity(id = schoolStableId, name = "Saraswati Vidya Mandir")
        val st = makeStudent(1L, "ADM-01", "Aarav", "5th")
        val dataset = BackupExportDataset(
            orgName = "SVM Group",
            scope = BackupExportScope.ORGANISATION,
            schools = listOf(school),
            schoolDocIdByStableId = mapOf(schoolStableId to schoolId),
            classGroups = listOf(ClassGroupEntity(id = 1L, schoolId = schoolStableId, name = "Primary", shortLabel = "Pri", sortOrder = 1)),
            schoolClasses = listOf(SchoolClassEntity(id = 1L, schoolId = schoolStableId, classGroupId = 1L, name = "5th", sortOrder = 5)),
            subjects = listOf(SubjectEntity(id = 1L, schoolId = schoolStableId, classGroupId = 1L, name = "Maths", sortOrder = 1)),
            students = listOf("stu_1" to st),
            marks = listOf(MarkEntity(studentId = 1L, subjectName = "Maths", componentKey = "t1_exam", rawValue = "55")),
            feeStructures = listOf(makeSimpleFeeStructure(oldSession)),
            feeOverrides = emptyList(),
            feeReceipts = emptyList(),
            attendanceSheets = emptyList(),
            teachers = listOf(TeacherProfile(id = "t1", name = "Mrs. Sharma", schoolIds = listOf(schoolId), subjects = listOf("Maths"))),
            cashBookEntries = listOf(
                CashBookEntryRecord(
                    id = "cb1", schoolId = schoolId, dateIso = "2026-09-10",
                    entryType = CashBookEntryType.EXPENSE, category = "Chalk",
                    amount = 250, paymentMode = PaymentMode.CASH, status = CashBookEntryStatus.ACTIVE
                )
            ),
            certificates = emptyList(),
            salaryMonthlyRecords = listOf(
                SalaryMonthlyRecord(
                    id = "sal_1", schoolId = schoolId, yearMonth = "2026-09",
                    staffId = "t1", staffName = "Mrs. Sharma", basicSalaryRs = 15000,
                    grossEarningsRs = 15000, netPayableRs = 15000, status = SalaryPaymentStatus.PAID, paidDateIso = "2026-10-01"
                )
            ),
            activeSession = oldSession
        )

        val owner = makeMember("u_owner", OrgRole.OWNER)
        val admin = makeMember("u_admin", OrgRole.ADMIN)
        val operator = makeMember("u_op", OrgRole.OFFICE_OPERATOR)

        // Operator rejected
        assertTrue(BackupExportZipEngine.buildBackupZip(dataset, operator).isFailure)

        // Admin succeeds WITHOUT staff_salary.csv
        val adminZip = BackupExportZipEngine.buildBackupZip(dataset, admin).getOrThrow()
        assertFalse(adminZip.includedSalaryCsv)
        val adminEntries = readZipEntryNames(adminZip.zipBytes)
        assertTrue("README.txt" in adminEntries)
        assertTrue("students.csv" in adminEntries)
        assertTrue("fee_receipts.csv" in adminEntries)
        assertTrue("marks.csv" in adminEntries)
        assertFalse("staff_salary.csv" in adminEntries)

        // Owner succeeds WITH staff_salary.csv
        val ownerZip = BackupExportZipEngine.buildBackupZip(dataset, owner).getOrThrow()
        assertTrue(ownerZip.includedSalaryCsv)
        val ownerEntries = readZipEntryNames(ownerZip.zipBytes)
        assertTrue("README.txt" in ownerEntries)
        assertTrue("staff_salary.csv" in ownerEntries)

        // 30-day reminder check
        val now = 1_800_000_000_000L
        assertTrue(BackupReminderEngine.computeStatus(0L, now).isOverdue30Days)
        assertFalse(BackupReminderEngine.computeStatus(now - 10L * 86_400_000L, now).isOverdue30Days)
        assertTrue(BackupReminderEngine.computeStatus(now - 31L * 86_400_000L, now).isOverdue30Days)
    }

    private fun readZipEntryNames(bytes: ByteArray): Set<String> {
        val names = linkedSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                names += e.name
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
        return names
    }

    // =========================================================================
    // 7. ROBOLECTRIC A4 PDF GENERATION & PAST-SESSION REGRESSION CHECK
    // =========================================================================

    @Test
    fun `offline A4 Promotion Register and Bulk Import Report PDFs generate valid PDF files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val school = SchoolEntity(id = schoolStableId, name = "Saraswati Vidya Mandir")

        val sPass = makeStudent(1L, "ADM-01", "Aarav Sharma", "5th", "A", 1)
        val marks = mapOf(1L to makeUniformAnnualMarks(1L, listOf("Maths", "Hindi"), mapOf("Maths" to 72, "Hindi" to 68)))
        val drafts = PromotionEngine.buildPromotionDraftsForSchool(
            schoolDocId = schoolId,
            fromSession = oldSession,
            studentsInFromSession = listOf("stu_old_1" to sPass),
            subjectsByClassName = mapOf("5th" to listOf("Maths", "Hindi")),
            marksByStudentId = marks,
            passPercent = 33,
            classOrder = defaultOrder,
            oldFeeStructure = makeSimpleFeeStructure(oldSession),
            oldFeeOverridesByStudentDocId = emptyMap(),
            oldReceiptsByStudentDocId = emptyMap()
        )
        val plan = PromotionEngine.buildPromotionBatches(
            drafts = drafts,
            schoolId = schoolId,
            schoolStableId = schoolStableId,
            fromSession = oldSession,
            toSession = newSession,
            classIdByClassName = mapOf("6th" to 6L),
            confirmedByUid = "u_owner",
            confirmedByName = "Principal",
            confirmedAtMs = 1_800_000_000_000L
        ).getOrThrow()

        val promPages = Phase14PdfGenerators.renderPromotionRegisterPreviewBitmaps(
            bundle = PromotionRegisterPdfBundle(
                school = school,
                fromSession = oldSession,
                toSession = newSession,
                className = "5th",
                records = plan.chunks.flatMap { it.items.map { i -> i.promotionRecord } },
                confirmedByName = "Principal",
                confirmedAtMs = 1_800_000_000_000L
            )
        )
        assertEquals(1, promPages.size)
        assertEquals(Phase14PdfGenerators.A4_WIDTH, promPages[0].width)
        assertEquals(Phase14PdfGenerators.A4_HEIGHT, promPages[0].height)
        assertTrue("Promotion Register page should render non-white ink", hasNonWhiteInk(promPages[0]))

        val sampleCsv = BulkStudentImportEngine.buildSampleCsv()
        val parsed = BulkStudentImportEngine.parseCsvText(sampleCsv, "sample.csv").getOrThrow()
        val preview = BulkStudentImportEngine.validateAndPreviewRows(
            rawRows = parsed.rows,
            mapping = BulkStudentImportEngine.autoMapColumns(parsed.headers),
            validClassNames = listOf("1st", "2nd"),
            existingStudentsInSession = emptyList()
        )
        val reportPages = Phase14PdfGenerators.renderBulkImportReportPreviewBitmaps(
            report = BulkImportExecutionReport(
                schoolName = school.name,
                session = newSession,
                fileName = "sample.csv",
                totalRows = preview.rows.size,
                addedCount = preview.addCount,
                updatedCount = 0,
                skippedCount = 0,
                errorCount = 0,
                updateMatchingEnabled = false,
                actorName = "Principal",
                importedAtMs = 1_800_000_000_000L,
                rows = preview.rows
            )
        )
        assertEquals(1, reportPages.size)
        assertEquals(Phase14PdfGenerators.A4_WIDTH, reportPages[0].width)
        assertEquals(Phase14PdfGenerators.A4_HEIGHT, reportPages[0].height)
        assertTrue("Bulk Import Report page should render non-white ink", hasNonWhiteInk(reportPages[0]))
        assertNotNull(context)
    }

    private fun hasNonWhiteInk(bmp: android.graphics.Bitmap): Boolean {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return pixels.any { it != android.graphics.Color.WHITE }
    }

    @Test
    fun `past-session regression - report calculations and old fee account remain 100 percent unchanged after promotion`() {
        val subjects = listOf("Hindi", "English", "Maths")
        val oldStudent = makeStudent(10L, "ADM-10", "Ananya", "5th", "A", 1, session = oldSession)
        val oldMarks = makeUniformAnnualMarks(10L, subjects, mapOf("Hindi" to 70, "English" to 60, "Maths" to 50))
        val marksMap = oldMarks.associate { (it.subjectName to it.componentKey) to it.rawValue }

        val perfBefore = ReportCalculations.calculateStudentPerformance(10L, subjects, marksMap, 33)
        val oldFeeStruct = makeSimpleFeeStructure(oldSession)
        val oldAcctBefore = FeeCollectionEngine.computeStudentFeeAccount(oldFeeStruct, oldStudent, "stu_old_10", null, emptyList())

        // Run full promotion + carry-forward into 2027-2028
        val drafts = PromotionEngine.buildPromotionDraftsForSchool(
            schoolDocId = schoolId,
            fromSession = oldSession,
            studentsInFromSession = listOf("stu_old_10" to oldStudent),
            subjectsByClassName = mapOf("5th" to subjects),
            marksByStudentId = mapOf(10L to oldMarks),
            passPercent = 33,
            classOrder = defaultOrder,
            oldFeeStructure = oldFeeStruct,
            oldFeeOverridesByStudentDocId = emptyMap(),
            oldReceiptsByStudentDocId = emptyMap()
        )
        val plan = PromotionEngine.buildPromotionBatches(
            drafts = drafts,
            schoolId = schoolId,
            schoolStableId = schoolStableId,
            fromSession = oldSession,
            toSession = newSession,
            classIdByClassName = mapOf("6th" to 6L),
            confirmedByUid = "u_owner",
            confirmedByName = "Principal",
            confirmedAtMs = 1_800_000_000_000L
        ).getOrThrow()

        val promotedStudent = plan.chunks.first().items.first().student
        assertEquals(newSession, promotedStudent.session)
        assertEquals("6th", promotedStudent.className)

        // Old session performance & fee account MUST be byte-for-byte identical!
        val perfAfter = ReportCalculations.calculateStudentPerformance(10L, subjects, marksMap, 33)
        val oldAcctAfter = FeeCollectionEngine.computeStudentFeeAccount(oldFeeStruct, oldStudent, "stu_old_10", null, emptyList())

        assertEquals(perfBefore.overallPercentage, perfAfter.overallPercentage, 0.0001)
        assertEquals(perfBefore.overallResult, perfAfter.overallResult)
        assertEquals(perfBefore.overallGrade, perfAfter.overallGrade)
        assertEquals(oldAcctBefore.totalScheduledFee, oldAcctAfter.totalScheduledFee)
        assertEquals(oldAcctBefore.totalRemainingDue, oldAcctAfter.totalRemainingDue)
        assertNull(oldAcctAfter.previousSessionDueCell)
    }
}
