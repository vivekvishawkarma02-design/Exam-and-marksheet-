package com.example

import com.example.data.StudentEntity
import com.example.domain.AccessLevel
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionScope
import com.example.domain.CustomFeeChargeDraft
import com.example.domain.ExamFeeHeadConfig
import com.example.domain.FeeCellState
import com.example.domain.FeeCollectionEngine
import com.example.domain.FeeHeadType
import com.example.domain.FeeLineItemType
import com.example.domain.FeeMessageSentLog
import com.example.domain.FeeMessageTemplates
import com.example.domain.FeeMessageType
import com.example.domain.FeeReceiptLineItem
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeReceiptStatus
import com.example.domain.FeeReportsAndQueueEngine
import com.example.domain.FeeStructureConfig
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OptionalFeeCatalogueItem
import com.example.domain.OrgRole
import com.example.domain.PaymentMode
import com.example.domain.Phase9AccessEvaluator
import com.example.domain.PhoneNormalizer
import com.example.domain.PhoneValidationResult
import com.example.domain.SelectedOptionalSaleDraft
import com.example.domain.StudentFeeOverride
import com.example.domain.StudentRegisterEngine
import com.example.domain.StudentRegisterStatus
import com.example.domain.StudentRegisterStatusFilter
import com.example.domain.StudentTransportFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase9DomainAndAccessTest {

    private fun sampleStructure(): FeeStructureConfig = FeeStructureConfig(
        id = FeeStructureConfig.makeDocId("sch_1", "2026-2027"),
        schoolId = "sch_1",
        session = "2026-2027",
        monthOrder = listOf("APR", "MAY", "JUN"),
        chargedTuitionMonths = listOf("APR", "MAY", "JUN"),
        chargedTransportMonths = listOf("APR", "MAY", "JUN"),
        tuitionByClassAndMonth = mapOf(
            "3rd" to mapOf("APR" to 1000, "MAY" to 1000, "JUN" to 1200)
        ),
        examFees = listOf(
            ExamFeeHeadConfig(
                examHeadKey = FeeHeadType.EXAM_UT1.key,
                monthCode = "MAY",
                amountByClass = mapOf("3rd" to 250)
            )
        ),
        optionalCatalogue = listOf(
            OptionalFeeCatalogueItem(
                id = "opt_books",
                name = "Textbook Set",
                price = 600,
                classFilter = emptyList(),
                hidden = false
            )
        ),
        maxConcessionPercentWithoutAdminApproval = 20,
        messageTemplates = FeeMessageTemplates()
    )

    private fun sampleStudent(
        id: Long = 1L,
        admNo: String = "ADM-101",
        name: String = "Aarav Sharma",
        usesTransport: Boolean = true,
        transportAmt: Int = 500,
        status: String = StudentRegisterStatus.ACTIVE.code,
        siblingGroupId: String = ""
    ): StudentEntity = StudentEntity(
        id = id,
        schoolId = 1L,
        session = "2026-2027",
        admNo = admNo,
        name = name,
        fatherName = "Rajesh Sharma",
        motherName = "Sunita Sharma",
        dob = "10-05-2017",
        mobile = "9876543210",
        secondMobile = "9123456780",
        address = "123 Station Road, Demo Nagar",
        className = "3rd",
        section = "A",
        rollNo = id.toInt(),
        usesTransport = usesTransport,
        transportRouteName = if (usesTransport) "Route B" else "",
        transportMonthlyAmount = if (usesTransport) transportAmt else 0,
        status = status,
        siblingGroupId = siblingGroupId
    )

    @Test
    fun accessEvaluatorEnforcesFeesAndStudentRegisterPermissionsAndSchoolScope() {
        val clerk = MemberAccessProfile(
            uid = "u_clerk",
            role = OrgRole.OFFICE_OPERATOR,
            status = MemberStatus.ACTIVE,
            allSchools = false,
            allowedSchoolIds = listOf("sch_1"),
            permissions = MemberPermissions(
                fees = AccessLevel.EDIT,
                studentRegister = AccessLevel.VIEW
            )
        )
        val admin = MemberAccessProfile(
            uid = "u_admin",
            role = OrgRole.ADMIN,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions.presetFor(OrgRole.ADMIN)
        )

        assertTrue(Phase9AccessEvaluator.canEditFees(clerk, "sch_1"))
        assertFalse(Phase9AccessEvaluator.canEditFees(clerk, "sch_2"))
        assertTrue(Phase9AccessEvaluator.canViewStudentRegister(clerk, "sch_1"))
        assertFalse(Phase9AccessEvaluator.canEditStudentRegister(clerk, "sch_1"))

        assertTrue(Phase9AccessEvaluator.isConcessionWithinMemberLimit(clerk, 20.0, 20, false))
        assertFalse(Phase9AccessEvaluator.isConcessionWithinMemberLimit(clerk, 25.0, 20, false))
        assertTrue(Phase9AccessEvaluator.isConcessionWithinMemberLimit(clerk, 25.0, 20, true))
        assertTrue(Phase9AccessEvaluator.isConcessionWithinMemberLimit(admin, 50.0, 20, false))
    }

    @Test
    fun studentRegisterEngineFiltersByStatusTransportSearchAndSiblings() {
        val s1 = sampleStudent(1L, "ADM-101", "Aarav Sharma", usesTransport = true, siblingGroupId = "FAM_1")
        val s2 = sampleStudent(2L, "ADM-102", "Ananya Sharma", usesTransport = false, siblingGroupId = "FAM_1")
        val s3 = sampleStudent(3L, "ADM-103", "Kabir Verma", usesTransport = true, status = StudentRegisterStatus.LEFT.code)
        val all = listOf(s1, s2, s3)

        val activeOnly = StudentRegisterEngine.filterStudents(all, statusFilter = StudentRegisterStatusFilter.ACTIVE_ONLY)
        assertEquals(listOf("ADM-101", "ADM-102"), activeOnly.map { it.admNo })

        val leftOnly = StudentRegisterEngine.filterStudents(all, statusFilter = StudentRegisterStatusFilter.LEFT_ONLY)
        assertEquals(listOf("ADM-103"), leftOnly.map { it.admNo })

        val transportUsers = StudentRegisterEngine.filterStudents(
            all,
            statusFilter = StudentRegisterStatusFilter.ALL,
            transportFilter = StudentTransportFilter.TRANSPORT_USERS
        )
        assertEquals(listOf("ADM-101", "ADM-103"), transportUsers.map { it.admNo })

        val byRoute = StudentRegisterEngine.filterStudents(all, searchQuery = "route b", statusFilter = StudentRegisterStatusFilter.ALL)
        assertEquals(2, byRoute.size)

        val siblingsOfS1 = StudentRegisterEngine.findSiblingsForStudent(s1, all)
        assertEquals(listOf("ADM-102"), siblingsOfS1.map { it.admNo })

        val generatedGroup = StudentRegisterEngine.resolveOrGenerateSiblingGroupId(
            sampleStudent(4L, "ADM-201", "Rohan"),
            sampleStudent(5L, "ADM-202", "Riya"),
            nowMs = 123456L
        )
        assertTrue(generatedGroup.startsWith("FAM_"))
    }

    @Test
    fun feeLedgerComputesGridOverridesPartialPaymentAndCancelledReceiptReopening() {
        val structure = sampleStructure()
        val student = sampleStudent(usesTransport = true, transportAmt = 500)
        val override = StudentFeeOverride(
            id = StudentFeeOverride.makeDocId("sch_1", "2026-2027", "stu_1"),
            schoolId = "sch_1",
            session = "2026-2027",
            studentId = "stu_1",
            cellOverrides = mapOf("APR|TUITION" to 800),
            remark = "Staff ward concession"
        )

        val issuedReceipt = FeeReceiptRecord(
            id = "rcp_1",
            schoolId = "sch_1",
            session = "2026-2027",
            receiptNumber = "RCP-2627-A1B-0001",
            deviceNodeCode = "A1B",
            sequenceNo = 1,
            receiptDateIso = "2026-04-10",
            studentId = "stu_1",
            studentName = student.name,
            studentAdmNo = student.admNo,
            fatherName = student.fatherName,
            className = student.className,
            section = student.section,
            rollNo = student.rollNo,
            lineItems = listOf(
                FeeReceiptLineItem(
                    lineId = "CELL_APR_TUITION",
                    lineType = FeeLineItemType.GRID_CELL,
                    monthCode = "APR",
                    headKey = FeeHeadType.TUITION.key,
                    title = "Tuition Fee (Apr)",
                    quantity = 1,
                    unitPrice = 800,
                    lineGrossAmount = 800,
                    concessionAllocated = 100,
                    paidAllocated = 500
                )
            ),
            totalBeforeConcession = 800,
            concessionMode = ConcessionMode.RUPEES,
            concessionScope = ConcessionScope.WHOLE_RECEIPT,
            concessionAmount = 100,
            concessionRemark = "Early bird",
            netPayableAmount = 700,
            amountPaid = 500,
            balanceDueOnReceipt = 200,
            paymentMode = PaymentMode.CASH,
            status = FeeReceiptStatus.ISSUED,
            receivedByUid = "u_1",
            receivedByName = "Clerk",
            issuedAtMs = 1000L
        )

        val cancelledReceipt = issuedReceipt.copy(
            id = "rcp_2",
            receiptNumber = "RCP-2627-A1B-0002",
            sequenceNo = 2,
            lineItems = listOf(
                FeeReceiptLineItem(
                    lineId = "CELL_APR_TRANSPORT",
                    lineType = FeeLineItemType.GRID_CELL,
                    monthCode = "APR",
                    headKey = FeeHeadType.TRANSPORT.key,
                    title = "Transport Fee (Apr)",
                    quantity = 1,
                    unitPrice = 500,
                    lineGrossAmount = 500,
                    concessionAllocated = 0,
                    paidAllocated = 500
                )
            ),
            totalBeforeConcession = 500,
            concessionAmount = 0,
            netPayableAmount = 500,
            amountPaid = 500,
            balanceDueOnReceipt = 0,
            status = FeeReceiptStatus.CANCELLED,
            cancelReason = "Wrong student"
        )

        val account = FeeCollectionEngine.computeStudentFeeAccount(
            structure = structure,
            student = student,
            studentDocId = "stu_1",
            override = override,
            receiptsForStudent = listOf(issuedReceipt, cancelledReceipt)
        )

        val aprRow = account.monthRows.first { it.monthCode == "APR" }
        assertEquals(800, aprRow.tuitionCell.scheduledAmount)
        assertEquals(500, aprRow.tuitionCell.paidAmount)
        assertEquals(100, aprRow.tuitionCell.concessionAllocated)
        assertEquals(200, aprRow.tuitionCell.remainingDue)
        assertEquals(FeeCellState.PARTLY_PAID, aprRow.tuitionCell.state)

        // Cancelled transport receipt must NOT reduce April transport due
        val transportCell = aprRow.transportCell
        assertNotNull(transportCell)
        assertEquals(500, transportCell!!.remainingDue)
        assertEquals(FeeCellState.DUE, transportCell.state)

        // May includes UT1 exam fee
        val mayRow = account.monthRows.first { it.monthCode == "MAY" }
        assertEquals(1, mayRow.examCells.size)
        assertEquals(250, mayRow.examCells.first().scheduledAmount)
    }

    @Test
    fun structureChangePreservesPastUnpaidDuesWhenRequested() {
        val oldStruct = sampleStructure()
        val updated = oldStruct.copy(
            tuitionByClassAndMonth = mapOf("3rd" to mapOf("APR" to 1500, "MAY" to 1500, "JUN" to 1500))
        )
        val preserved = FeeCollectionEngine.applyStructureChangeWithPastDueChoice(
            previousStructure = oldStruct,
            updatedStructure = updated,
            classNames = listOf("3rd"),
            keepPastUnpaidDuesUnchanged = true,
            monthsToPreserveOldRates = listOf("APR")
        )
        assertEquals(1000, preserved.scheduledClassAmountForCell("3rd", "APR", FeeHeadType.TUITION))
        assertEquals(1500, preserved.scheduledClassAmountForCell("3rd", "MAY", FeeHeadType.TUITION))
    }

    @Test
    fun paymentDraftValidatesConcessionLimitOptionalItemsAndPartialAllocation() {
        val structure = sampleStructure()
        val student = sampleStudent(usesTransport = false)
        val account = FeeCollectionEngine.computeStudentFeeAccount(structure, student, "stu_1", null, emptyList())
        val aprCell = account.monthRows.first { it.monthCode == "APR" }.tuitionCell

        val clerk = MemberAccessProfile(
            uid = "u_clerk",
            role = OrgRole.OFFICE_OPERATOR,
            status = MemberStatus.ACTIVE,
            allSchools = false,
            allowedSchoolIds = listOf("sch_1"),
            permissions = MemberPermissions(fees = AccessLevel.EDIT)
        )

        val draftOverLimit = FeeCollectionEngine.computePaymentDraft(
            selectedGridCells = listOf(aprCell),
            selectedOptionalItems = listOf(
                SelectedOptionalSaleDraft(
                    catalogueItem = structure.optionalCatalogue.first(),
                    monthCode = "APR",
                    quantity = 1,
                    unitPrice = 600
                )
            ),
            customCharges = listOf(
                CustomFeeChargeDraft(id = "c1", name = "ID Card", amount = 100, monthCode = "APR")
            ),
            concessionMode = ConcessionMode.PERCENTAGE,
            concessionScope = ConcessionScope.WHOLE_RECEIPT,
            concessionInputValue = 30.0,
            concessionRemark = "Need based",
            amountPaidInput = 1000,
            maxConcessionPercentWithoutAdmin = 20,
            actorMember = clerk,
            hasExplicitAdminApproval = false
        )

        assertEquals(1700, draftOverLimit.totalBeforeConcession)
        assertEquals(510, draftOverLimit.concessionAmount)
        assertEquals(1190, draftOverLimit.netPayableAmount)
        assertEquals(1000, draftOverLimit.amountPaid)
        assertEquals(190, draftOverLimit.balanceDueAfterPayment)
        assertTrue(draftOverLimit.requiresAdminApprovalForConcession)
        assertNotNull(draftOverLimit.validationError)

        val draftApproved = FeeCollectionEngine.computePaymentDraft(
            selectedGridCells = listOf(aprCell),
            selectedOptionalItems = emptyList(),
            customCharges = emptyList(),
            concessionMode = ConcessionMode.RUPEES,
            concessionScope = ConcessionScope.WHOLE_RECEIPT,
            concessionInputValue = 150.0,
            concessionRemark = "Sibling discount",
            amountPaidInput = 850,
            maxConcessionPercentWithoutAdmin = 20,
            actorMember = clerk,
            hasExplicitAdminApproval = false
        )
        assertNull(draftApproved.validationError)
        assertFalse(draftApproved.requiresAdminApprovalForConcession)
        assertEquals(850, draftApproved.amountPaid)
    }

    @Test
    fun offlineReceiptNumberingIsDeterministicAndMonotonic() {
        val node = FeeCollectionEngine.deriveDeviceNodeCode("device-abc-99", "uid_clerk_1")
        assertTrue(node.length in 3..4)
        val (rcp1, seq1) = FeeCollectionEngine.generateNextReceiptNumber(
            session = "2026-2027",
            deviceNodeCode = node,
            existingReceiptsForSchoolAndSession = emptyList()
        )
        assertEquals(1, seq1)
        assertTrue(rcp1.startsWith("RCP-2627-"))
        assertTrue(rcp1.endsWith("0001"))
    }

    @Test
    fun phoneNormalizerAndReportsAndSendQueueWorkEndToEnd() {
        val valid1 = PhoneNormalizer.normalizeIndianMobile("+91 98765-43210")
        assertTrue(valid1 is PhoneValidationResult.Valid)
        assertEquals("919876543210", (valid1 as PhoneValidationResult.Valid).waMeDigits)

        val valid2 = PhoneNormalizer.normalizeIndianMobile("09876543210")
        assertTrue(valid2 is PhoneValidationResult.Valid)

        val invalid = PhoneNormalizer.normalizeIndianMobile("12345")
        assertTrue(invalid is PhoneValidationResult.Invalid)

        val waLink = PhoneNormalizer.buildWaMeLink(valid1.waMeDigits, "Hello Parent & Guardian")
        assertTrue(waLink.startsWith("https://wa.me/919876543210?text="))
        assertTrue(waLink.contains("%26"))

        val structure = sampleStructure()
        val student = sampleStudent(usesTransport = false)
        val dues = FeeReportsAndQueueEngine.buildDuesList(
            studentsWithDocIds = listOf("stu_1" to student),
            schoolDocId = "sch_1",
            schoolName = "DEMO MODEL SCHOOL",
            structure = structure,
            overridesByStudentDocId = emptyMap(),
            receipts = emptyList()
        )
        assertEquals(1, dues.size)
        assertEquals(3450, dues.first().totalDueAmount) // 1000 + 1000 + 250 + 1200

        val csv = FeeReportsAndQueueEngine.buildDuesListCsv(dues, "2026-2027")
        assertTrue(csv.contains("Aarav Sharma"))
        assertTrue(csv.contains("3450"))

        val sentLog = FeeMessageSentLog(
            id = "log_1",
            schoolId = "sch_1",
            session = "2026-2027",
            studentId = "stu_1",
            studentName = student.name,
            className = student.className,
            section = student.section,
            guardianName = student.fatherName,
            normalizedMobile = "+919876543210",
            messageType = FeeMessageType.FEE_DUE_REMINDER,
            contextKey = "FEE_DUE_REMINDER_2026-04_3450",
            messageText = "Dear Rajesh Sharma, fee due Rs. 3450",
            channel = "WHATSAPP",
            sentByUid = "u_1",
            sentByName = "Clerk",
            sentAtMs = 1000L
        )
        val queueAll = FeeReportsAndQueueEngine.buildSendQueue(
            duesRows = dues,
            schoolName = "DEMO MODEL SCHOOL",
            templates = structure.messageTemplates,
            messageType = FeeMessageType.FEE_DUE_REMINDER,
            contextDateOrMonthKey = "2026-04",
            sentLogs = listOf(sentLog),
            showPendingOnly = false
        )
        assertEquals(1, queueAll.size)
        assertNotNull(queueAll.first().sentLog)

        val queuePendingOnly = FeeReportsAndQueueEngine.buildSendQueue(
            duesRows = dues,
            schoolName = "DEMO MODEL SCHOOL",
            templates = structure.messageTemplates,
            messageType = FeeMessageType.FEE_DUE_REMINDER,
            contextDateOrMonthKey = "2026-04",
            sentLogs = listOf(sentLog),
            showPendingOnly = true
        )
        assertTrue(queuePendingOnly.isEmpty())
    }
}
