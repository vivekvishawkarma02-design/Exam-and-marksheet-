package com.example.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.data.StudentEntity
import com.example.data.cloud.StableDocId
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionScope
import com.example.domain.CustomFeeChargeDraft
import com.example.domain.FeeCollectionEngine
import com.example.domain.FeeHeadType
import com.example.domain.FeeMessageSentLog
import com.example.domain.FeeMessageTemplates
import com.example.domain.FeeMessageType
import com.example.domain.FeePaymentDraftComputation
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeReceiptStatus
import com.example.domain.FeeReportsAndQueueEngine
import com.example.domain.FeeStructureConfig
import com.example.domain.OptionalFeeCatalogueItem
import com.example.domain.PaymentMode
import com.example.domain.Phase6DateUtils
import com.example.domain.Phase9AccessEvaluator
import com.example.domain.SchoolNoticeRecord
import com.example.domain.StudentDueCellBreakdown
import com.example.domain.StudentFeeOverride
import com.example.domain.StudentRegisterStatus
import com.example.util.AdmissionFormPdfBundle
import com.example.util.ConcessionReportPdfBundle
import com.example.util.DayCollectionPdfBundle
import com.example.util.DuesListPdfBundle
import com.example.util.FeeReceiptPdfBundle
import com.example.util.OptionalSalesPdfBundle
import com.example.util.PdfExportHelper
import com.example.util.Phase9PdfGenerator
import com.example.util.StudentDuesSlipPdfBundle
import com.example.util.StudentIdCardsPdfBundle
import com.example.util.friendlyFirestoreErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

// =============================================================================
// PART A: STUDENT REGISTER ACTIONS & PDFS
// =============================================================================

fun SchoolViewModel.changeStudentClassAndSection(
    student: StudentEntity,
    newClassName: String,
    newSection: String,
    newRollNo: Int,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val rec = activeSchoolRecord(student.schoolId) ?: run {
        onResult(false, "School not found.")
        return
    }
    if (!Phase9AccessEvaluator.canEditStudentRegister(state.accessProfile, rec.docId) && !state.canEditStudentRegister) {
        onResult(false, "You do not have permission to edit the Student Register for ${rec.name}.")
        return
    }
    val cleanCls = newClassName.trim()
    val cleanSec = newSection.trim().uppercase().ifBlank { "A" }
    if (cleanCls.isBlank()) {
        onResult(false, "Please select a valid class.")
        return
    }
    val clsEntity = state.classesForSchool(student.schoolId).firstOrNull {
        it.name.equals(cleanCls, ignoreCase = true)
    }
    val updated = student.copy(
        classId = clsEntity?.id ?: student.classId,
        className = clsEntity?.name ?: cleanCls,
        section = cleanSec,
        rollNo = newRollNo.coerceAtLeast(1)
    )
    saveStudent(updated) { ok, err ->
        if (ok) {
            emitMessage("Moved ${student.name} to ${updated.className}-$cleanSec (Roll ${updated.rollNo})")
        }
        onResult(ok, err)
    }
}

fun SchoolViewModel.changeStudentRegisterStatus(
    student: StudentEntity,
    newStatus: StudentRegisterStatus,
    leftDateIso: String,
    leftReason: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val rec = activeSchoolRecord(student.schoolId) ?: run {
        onResult(false, "School not found.")
        return
    }
    if (!Phase9AccessEvaluator.canEditStudentRegister(state.accessProfile, rec.docId) && !state.canEditStudentRegister) {
        onResult(false, "You do not have permission to change student status.")
        return
    }
    if (newStatus == StudentRegisterStatus.LEFT) {
        if (leftDateIso.trim().isBlank()) {
            onResult(false, "Please enter the leaving date.")
            return
        }
        if (leftReason.trim().isBlank()) {
            onResult(false, "Please enter the reason for leaving.")
            return
        }
    }
    val updated = if (newStatus == StudentRegisterStatus.LEFT) {
        student.copy(
            status = StudentRegisterStatus.LEFT.code,
            leftDate = leftDateIso.trim(),
            leftReason = leftReason.trim()
        )
    } else {
        student.copy(
            status = StudentRegisterStatus.ACTIVE.code,
            leftDate = "",
            leftReason = ""
        )
    }
    saveStudent(updated) { ok, err ->
        if (ok) {
            val msg = if (newStatus == StudentRegisterStatus.LEFT) {
                "Marked ${student.name} as Left (${leftDateIso.trim()})"
            } else {
                "Reactivated ${student.name}"
            }
            emitMessage(msg)
        }
        onResult(ok, err)
    }
}

fun SchoolViewModel.linkStudentsAsSiblings(
    primaryStudent: StudentEntity,
    siblingStudentIds: List<Long>,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val rec = activeSchoolRecord(primaryStudent.schoolId) ?: run {
        onResult(false, "School not found.")
        return
    }
    if (!Phase9AccessEvaluator.canEditStudentRegister(state.accessProfile, rec.docId) && !state.canEditStudentRegister) {
        onResult(false, "You do not have permission to link siblings.")
        return
    }
    val allTargets = (siblingStudentIds + primaryStudent.id).distinct()
        .mapNotNull { id -> state.allStudents.firstOrNull { it.id == id } }
    if (allTargets.size < 2) {
        onResult(false, "Select at least one sibling to link with ${primaryStudent.name}.")
        return
    }
    val groupId = allTargets.firstNotNullOfOrNull { it.siblingGroupId.trim().takeIf { g -> g.isNotBlank() } }
        ?: "FAM-${UUID.randomUUID().toString().take(8).uppercase()}"

    viewModelScope.launch {
        var allOk = true
        var firstErr: String? = null
        for (st in allTargets) {
            val stRec = activeSchoolRecord(st.schoolId) ?: rec
            val existingDocId = studentDocIdByLongId[st.id]?.first
            val clsDocId = stRec.classes.firstOrNull {
                it.name.equals(st.className.trim(), ignoreCase = true)
            }?.id.orEmpty()
            val res = academicRepo.saveStudent(
                orgId = orgId,
                schoolDocId = stRec.docId,
                existingDocId = existingDocId,
                student = st.copy(siblingGroupId = groupId),
                classDocId = clsDocId
            )
            if (res.isFailure) {
                allOk = false
                firstErr = friendlyFirestoreErrorMessage(res.exceptionOrNull() ?: Exception(), "Failed to link sibling.")
            }
        }
        if (allOk) {
            emitMessage("Linked ${allTargets.size} siblings under family group $groupId")
            onResult(true, null)
        } else {
            onResult(false, firstErr)
        }
    }
}

fun SchoolViewModel.unlinkStudentFromSiblings(
    student: StudentEntity,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    saveStudent(student.copy(siblingGroupId = ""), onResult)
}

fun SchoolViewModel.generateStudentAdmissionFormPdf(student: StudentEntity) {
    val state = uiState.value
    val school = state.schoolById(student.schoolId) ?: state.activeSchool
    val siblingsSummary = if (student.siblingGroupId.isNotBlank()) {
        state.allStudents.filter {
            it.id != student.id &&
                it.schoolId == student.schoolId &&
                it.siblingGroupId.equals(student.siblingGroupId, ignoreCase = true)
        }.joinToString(", ") { "${it.name} (${it.className}-${it.section})" }
    } else {
        ""
    }
    val bundle = AdmissionFormPdfBundle(
        school = school,
        session = student.session,
        student = student,
        siblingNamesSummary = siblingsSummary
    )
    launchPhase9PdfJob(
        title = "Admission Form • ${student.name}",
        sizeLabel = "A4 Portrait"
    ) {
        Phase9PdfGenerator.generateAdmissionFormPdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateStudentIdCardsPdf(students: List<StudentEntity>, scopeLabel: String = "Selected Students") {
    if (students.isEmpty()) {
        emitMessage("No students selected for ID cards.")
        return
    }
    val state = uiState.value
    val school = state.schoolById(students.first().schoolId) ?: state.activeSchool
    val bundle = StudentIdCardsPdfBundle(
        school = school,
        session = state.filter.session,
        students = students
    )
    launchPhase9PdfJob(
        title = "Student ID Cards • $scopeLabel (${students.size})",
        sizeLabel = "A4 Grid (8/page)"
    ) {
        Phase9PdfGenerator.generateStudentIdCardsPdf(appContext, bundle)
    }
}

// =============================================================================
// PART B: FEE STRUCTURE & STUDENT FEE OVERRIDES
// =============================================================================

fun SchoolViewModel.saveFeeStructure(
    updatedStructure: FeeStructureConfig,
    keepPastUnpaidDuesAtOldRates: Boolean,
    monthsToPreserveOldRates: List<String> = emptyList(),
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = updatedStructure.schoolId.ifBlank { state.activeSchoolDocId }
    if (schoolDocId.isBlank()) {
        onResult(false, "No active school selected.")
        return
    }
    if (!Phase9AccessEvaluator.canEditFees(state.accessProfile, schoolDocId) && !state.canEditFees) {
        onResult(false, "You do not have permission to edit the fee structure.")
        return
    }
    val oldStructure = state.feeStructureForSchool(schoolDocId, updatedStructure.session)
    val existsInCloud = state.feeStructures.any {
        it.schoolId == schoolDocId && it.session.equals(updatedStructure.session, ignoreCase = true)
    }

    val finalStructure = FeeCollectionEngine.applyStructureChangeWithPastDueChoice(
        previousStructure = oldStructure,
        updatedStructure = updatedStructure.copy(
            id = FeeStructureConfig.makeDocId(schoolDocId, updatedStructure.session),
            schoolId = schoolDocId
        ),
        classNames = state.activeSchoolClassNames,
        keepPastUnpaidDuesUnchanged = keepPastUnpaidDuesAtOldRates,
        monthsToPreserveOldRates = monthsToPreserveOldRates.ifEmpty { oldStructure.monthOrder }
    )

    viewModelScope.launch {
        val auditNote = if (keepPastUnpaidDuesAtOldRates) {
            "Kept past unpaid months at old rates"
        } else {
            "Updated unpaid dues to new rates"
        }
        val res = phase9Repo.saveFeeStructure(
            orgId = orgId,
            config = finalStructure,
            existsInCloud = existsInCloud,
            auditNote = auditNote
        )
        res.onSuccess {
            emitMessage("Saved Fee Structure for ${updatedStructure.session}")
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, friendlyFirestoreErrorMessage(err, "Unable to save fee structure."))
        }
    }
}

fun SchoolViewModel.saveStudentFeeOverride(
    student: StudentEntity,
    headType: FeeHeadType,
    monthCode: String,
    overrideAmountRs: Int,
    remark: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.schoolDocIdByLongId[student.schoolId] ?: state.activeSchoolDocId
    val studentDocId = state.studentDocIdFor(student.id)
    if (schoolDocId.isBlank() || studentDocId.isBlank()) {
        onResult(false, "Student record is not synced yet.")
        return
    }
    if (!Phase9AccessEvaluator.canEditFees(state.accessProfile, schoolDocId) && !state.canEditFees) {
        onResult(false, "You do not have permission to override student fees.")
        return
    }
    if (overrideAmountRs < 0) {
        onResult(false, "Override amount cannot be negative.")
        return
    }
    if (remark.trim().isBlank()) {
        onResult(false, "A remark is required for a student fee override.")
        return
    }
    val existing = state.feeOverrideForStudent(studentDocId, schoolDocId, student.session)
    val cellKey = "${monthCode.trim().uppercase(Locale.US).ifBlank { "ALL" }}|${headType.key}"
    val updatedCells = (existing?.cellOverrides ?: emptyMap()).toMutableMap().apply {
        put(cellKey, overrideAmountRs)
    }
    val docId = StudentFeeOverride.makeDocId(schoolDocId, student.session, studentDocId)
    val record = StudentFeeOverride(
        id = docId,
        schoolId = schoolDocId,
        session = student.session,
        studentId = studentDocId,
        studentName = student.name,
        cellOverrides = updatedCells,
        remark = remark.trim()
    )
    viewModelScope.launch {
        phase9Repo.saveStudentFeeOverride(orgId, record, existsInCloud = existing != null)
            .onSuccess {
                emitMessage("Saved fee override for ${student.name}")
                onResult(true, null)
            }
            .onFailure { err ->
                onResult(false, friendlyFirestoreErrorMessage(err, "Unable to save fee override."))
            }
    }
}

fun SchoolViewModel.deleteStudentFeeOverride(
    overrideRecord: StudentFeeOverride,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase9AccessEvaluator.canEditFees(state.accessProfile, overrideRecord.schoolId) && !state.canEditFees) {
        onResult(false, "You do not have permission to remove fee overrides.")
        return
    }
    viewModelScope.launch {
        phase9Repo.deleteStudentFeeOverride(
            orgId = orgId,
            schoolId = overrideRecord.schoolId,
            session = overrideRecord.session,
            studentDocId = overrideRecord.studentId,
            studentName = overrideRecord.studentName
        ).onSuccess {
            emitMessage("Removed fee override for ${overrideRecord.studentName}")
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, friendlyFirestoreErrorMessage(err, "Unable to remove fee override."))
        }
    }
}

fun SchoolViewModel.saveFeeMessageTemplates(
    schoolDocId: String,
    session: String,
    templates: FeeMessageTemplates,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val struct = state.feeStructureForSchool(schoolDocId, session)
    saveFeeStructure(
        updatedStructure = struct.copy(messageTemplates = templates),
        keepPastUnpaidDuesAtOldRates = false,
        onResult = onResult
    )
}

// =============================================================================
// PART C: COLLECT FEES, RECEIPT ISSUANCE, CANCELLATION & A5 PDFS
// =============================================================================

fun SchoolViewModel.issueFeeReceipt(
    student: StudentEntity,
    draft: FeePaymentDraftComputation,
    concessionMode: ConcessionMode,
    concessionScope: ConcessionScope,
    concessionTargetLineId: String,
    concessionInputValue: Double,
    concessionRemark: String,
    concessionGivenByName: String,
    concessionApprovedByAdminUid: String,
    concessionApprovedByAdminName: String,
    paymentMode: PaymentMode,
    note: String,
    customChargesToCatalogue: List<CustomFeeChargeDraft> = emptyList(),
    paymentDateIso: String = Phase6DateUtils.todayIso(),
    onResult: (Boolean, String?, FeeReceiptRecord?) -> Unit = { _, _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.schoolDocIdByLongId[student.schoolId] ?: state.activeSchoolDocId
    val studentDocId = state.studentDocIdFor(student.id)
    if (schoolDocId.isBlank() || studentDocId.isBlank()) {
        onResult(false, "Student record is not ready.", null)
        return
    }
    if (!Phase9AccessEvaluator.canEditFees(state.accessProfile, schoolDocId) && !state.canEditFees) {
        onResult(false, "View-only access: only members with Edit access to Fees can collect fees.", null)
        return
    }
    if (draft.validationError != null) {
        onResult(false, draft.validationError, null)
        return
    }
    if (draft.requiresAdminApprovalForConcession) {
        val isCurrentUserAdmin = state.isAdmin
        val hasAdminApproval = isCurrentUserAdmin || concessionApprovedByAdminUid.isNotBlank()
        if (!hasAdminApproval) {
            onResult(
                false,
                "Concession (${String.format(Locale.US, "%.1f", draft.effectiveConcessionPercent)}%) exceeds the limit and requires Admin approval.",
                null
            )
            return
        }
    }

    val uid = state.currentMember?.uid ?: currentUserId
    val uname = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Fee Clerk"
    val deviceNode = offlinePrefs.getOrCreateDeviceNodeCode(currentUserId)
    val localLastSeq = offlinePrefs.getLastReceiptSequence(orgId, schoolDocId, student.session, deviceNode)
    val existingReceiptsForSchoolSession = state.feeReceiptsForSchoolSession(schoolDocId, student.session)

    val (receiptNumber, nextSeq) = FeeCollectionEngine.generateNextReceiptNumber(
        session = student.session,
        deviceNodeCode = deviceNode,
        existingReceiptsForSchoolAndSession = existingReceiptsForSchoolSession,
        lastLocalSequenceForDevice = localLastSeq
    )
    val receiptId = FeeReceiptRecord.makeDocId(schoolDocId, student.session, receiptNumber)

    val effectiveAdminUid = if (draft.requiresAdminApprovalForConcession) {
        concessionApprovedByAdminUid.ifBlank { if (state.isAdmin) uid else "" }
    } else {
        concessionApprovedByAdminUid
    }
    val effectiveAdminName = if (draft.requiresAdminApprovalForConcession) {
        concessionApprovedByAdminName.ifBlank { if (state.isAdmin) uname else "" }
    } else {
        concessionApprovedByAdminName
    }

    val receipt = FeeReceiptRecord(
        id = receiptId,
        schoolId = schoolDocId,
        session = student.session,
        receiptNumber = receiptNumber,
        deviceNodeCode = deviceNode,
        sequenceNo = nextSeq,
        receiptDateIso = paymentDateIso.trim().ifBlank { Phase6DateUtils.todayIso() },
        studentId = studentDocId,
        studentName = student.name,
        studentAdmNo = student.admNo,
        fatherName = student.fatherName.ifBlank { student.motherName },
        className = student.className,
        section = student.section.uppercase().ifBlank { "A" },
        rollNo = student.rollNo.coerceAtLeast(1),
        lineItems = draft.allocatedLines,
        totalBeforeConcession = draft.totalBeforeConcession,
        concessionMode = concessionMode,
        concessionScope = concessionScope,
        concessionTargetLineId = concessionTargetLineId,
        concessionInputValue = concessionInputValue,
        concessionAmount = draft.concessionAmount,
        concessionRemark = concessionRemark.trim(),
        concessionGivenByUid = if (draft.concessionAmount > 0) uid else "",
        concessionGivenByName = if (draft.concessionAmount > 0) concessionGivenByName.ifBlank { uname } else "",
        concessionApprovedByAdminUid = effectiveAdminUid,
        concessionApprovedByAdminName = effectiveAdminName,
        netPayableAmount = draft.netPayableAmount,
        amountPaid = draft.amountPaid,
        balanceDueOnReceipt = draft.balanceDueAfterPayment,
        paymentMode = paymentMode,
        paymentNote = note.trim(),
        status = FeeReceiptStatus.ISSUED,
        receivedByUid = uid,
        receivedByName = uname,
        issuedAtMs = System.currentTimeMillis()
    )

    val newCatalogueItems = customChargesToCatalogue
        .filter { it.addToCatalogue && it.name.isNotBlank() && it.amount > 0 }
        .map { cc ->
            OptionalFeeCatalogueItem(
                id = "cat_${StableDocId.sanitizeSegment(cc.name).lowercase(Locale.US)}_${UUID.randomUUID().toString().take(4)}",
                name = cc.name.trim(),
                price = cc.amount
            )
        }
    val currentStructure = state.feeStructureForSchool(schoolDocId, student.session)
    val structureExistsInCloud = state.feeStructures.any {
        it.schoolId == schoolDocId && it.session.equals(student.session, ignoreCase = true)
    }

    viewModelScope.launch {
        offlinePrefs.setLastReceiptSequence(orgId, schoolDocId, student.session, deviceNode, nextSeq)
        val res = phase9Repo.issueFeeReceipt(
            orgId = orgId,
            receipt = receipt,
            newCatalogueItemsToSave = newCatalogueItems,
            existingStructure = currentStructure,
            structureExistsInCloud = structureExistsInCloud
        )
        res.onSuccess { finalized ->
            emitMessage("Issued Receipt ${finalized.receiptNumber} (Rs. ${finalized.amountPaid})")
            onResult(true, null, finalized)
        }.onFailure { err ->
            onResult(false, friendlyFirestoreErrorMessage(err, "Unable to issue fee receipt."), null)
        }
    }
}

fun SchoolViewModel.cancelFeeReceipt(
    receipt: FeeReceiptRecord,
    cancelReason: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase9AccessEvaluator.canEditFees(state.accessProfile, receipt.schoolId) && !state.canEditFees) {
        onResult(false, "View-only access: you do not have permission to cancel fee receipts.")
        return
    }
    if (receipt.isCancelled) {
        onResult(false, "Receipt ${receipt.receiptNumber} is already cancelled.")
        return
    }
    if (cancelReason.trim().isBlank()) {
        onResult(false, "A cancellation reason is mandatory.")
        return
    }

    viewModelScope.launch {
        phase9Repo.cancelFeeReceipt(
            orgId = orgId,
            receipt = receipt,
            cancelReason = cancelReason.trim()
        ).onSuccess {
            emitMessage("Cancelled Receipt ${receipt.receiptNumber}. Cells are due again.")
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, friendlyFirestoreErrorMessage(err, "Unable to cancel receipt."))
        }
    }
}

fun SchoolViewModel.generateFeeReceiptPdfPreview(receipt: FeeReceiptRecord) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[receipt.schoolId]?.toSchoolEntity() ?: state.activeSchool
    val bundle = FeeReceiptPdfBundle(
        school = school,
        receipt = receipt
    )
    launchPhase9PdfJob(
        title = "Fee Receipt • ${receipt.receiptNumber}",
        sizeLabel = "A5 Portrait"
    ) {
        Phase9PdfGenerator.generateFeeReceiptPdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateStudentDuesSlipPdfPreview(student: StudentEntity) {
    val state = uiState.value
    val school = state.schoolById(student.schoolId) ?: state.activeSchool
    val account = state.computeStudentFeeAccount(student)
    val breakdowns = account.allCells
        .filter { it.remainingDue > 0 }
        .map { cell ->
            StudentDueCellBreakdown(
                monthCode = cell.monthCode,
                headType = cell.headType,
                label = cell.label,
                dueAmount = cell.remainingDue
            )
        }
    val uname = state.currentMember?.name?.ifBlank { state.currentMember.email }.orEmpty()
    val bundle = StudentDuesSlipPdfBundle(
        school = school,
        session = student.session,
        slipDateIso = Phase6DateUtils.todayIso(),
        student = student,
        dueBreakdowns = breakdowns,
        totalDueAmount = account.totalRemainingDue,
        preparedByName = uname
    )
    launchPhase9PdfJob(
        title = "Dues Slip • ${student.name}",
        sizeLabel = "A5 Portrait"
    ) {
        Phase9PdfGenerator.generateStudentDuesSlipPdf(appContext, bundle)
    }
}

// =============================================================================
// PART D & PART D2: REPORTS, CSV EXPORT, SEND QUEUE & NOTICES
// =============================================================================

fun SchoolViewModel.generateDayCollectionRegisterPdfPreview(
    schoolDocId: String,
    startDateIso: String,
    endDateIso: String
) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
    val summary = FeeReportsAndQueueEngine.buildDayCollectionRegister(
        receipts = state.feeReceipts,
        schoolId = schoolDocId,
        session = state.filter.session,
        startDateIso = startDateIso,
        endDateIso = endDateIso
    )
    val bundle = DayCollectionPdfBundle(
        school = school,
        session = state.filter.session,
        summary = summary
    )
    launchPhase9PdfJob(
        title = "Day Collection • $startDateIso to $endDateIso",
        sizeLabel = "A4 Portrait"
    ) {
        Phase9PdfGenerator.generateDayCollectionPdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateDuesListPdfPreview(
    schoolDocId: String,
    classNameFilter: String?,
    sectionFilter: String?,
    monthCodeFilter: String?,
    headTypeFilter: FeeHeadType?
) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
    val schLongId = state.schoolRecordsByDocId[schoolDocId]?.stableId ?: state.activeSchool.id
    val struct = state.feeStructureForSchool(schoolDocId, state.filter.session)
    val studentsWithDocIds = state.allStudents
        .filter { it.schoolId == schLongId && it.session.equals(state.filter.session, ignoreCase = true) }
        .mapNotNull { st ->
            val docId = state.studentDocIdFor(st.id)
            if (docId.isBlank()) null else docId to st
        }
    val overridesMap = state.studentFeeOverrides
        .filter { it.schoolId == schoolDocId && it.session.equals(state.filter.session, ignoreCase = true) }
        .associateBy { it.studentId }

    val rows = FeeReportsAndQueueEngine.buildDuesList(
        studentsWithDocIds = studentsWithDocIds,
        schoolDocId = schoolDocId,
        schoolName = school.name,
        structure = struct,
        overridesByStudentDocId = overridesMap,
        receipts = state.feeReceipts,
        classNameFilter = classNameFilter,
        sectionFilter = sectionFilter,
        monthFilter = monthCodeFilter,
        feeHeadFilter = headTypeFilter
    )
    val filterTitle = buildList {
        if (!classNameFilter.isNullOrBlank()) add("Class $classNameFilter") else add("All Classes")
        if (!sectionFilter.isNullOrBlank()) add("Sec $sectionFilter")
        if (!monthCodeFilter.isNullOrBlank()) add("Month $monthCodeFilter")
        if (headTypeFilter != null) add(headTypeFilter.label)
    }.joinToString(" • ")

    val bundle = DuesListPdfBundle(
        school = school,
        session = state.filter.session,
        filterTitle = filterTitle,
        asOfDateIso = Phase6DateUtils.todayIso(),
        rows = rows
    )
    launchPhase9PdfJob(
        title = "Fee Dues List • $filterTitle",
        sizeLabel = "A4 Portrait"
    ) {
        Phase9PdfGenerator.generateDuesListPdf(appContext, bundle)
    }
}

fun SchoolViewModel.exportDuesListCsvToUri(
    destinationUri: Uri,
    schoolDocId: String,
    classNameFilter: String?,
    sectionFilter: String?,
    monthCodeFilter: String?,
    headTypeFilter: FeeHeadType?
) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
    val schLongId = state.schoolRecordsByDocId[schoolDocId]?.stableId ?: state.activeSchool.id
    val struct = state.feeStructureForSchool(schoolDocId, state.filter.session)
    val studentsWithDocIds = state.allStudents
        .filter { it.schoolId == schLongId && it.session.equals(state.filter.session, ignoreCase = true) }
        .mapNotNull { st ->
            val docId = state.studentDocIdFor(st.id)
            if (docId.isBlank()) null else docId to st
        }
    val overridesMap = state.studentFeeOverrides
        .filter { it.schoolId == schoolDocId && it.session.equals(state.filter.session, ignoreCase = true) }
        .associateBy { it.studentId }

    val rows = FeeReportsAndQueueEngine.buildDuesList(
        studentsWithDocIds = studentsWithDocIds,
        schoolDocId = schoolDocId,
        schoolName = school.name,
        structure = struct,
        overridesByStudentDocId = overridesMap,
        receipts = state.feeReceipts,
        classNameFilter = classNameFilter,
        sectionFilter = sectionFilter,
        monthFilter = monthCodeFilter,
        feeHeadFilter = headTypeFilter
    )
    viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            val csv = FeeReportsAndQueueEngine.buildDuesListCsv(rows, state.filter.session)
            PdfExportHelper.writeTextToUri(appContext, csv, destinationUri)
        }
        emitMessage(if (ok) "Exported Dues List CSV (${rows.size} student(s))" else "Failed to export Dues List CSV")
    }
}

fun SchoolViewModel.generateConcessionReportPdfPreview(
    schoolDocId: String,
    startDateIso: String? = null,
    endDateIso: String? = null
) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
    val rows = FeeReportsAndQueueEngine.buildConcessionReport(
        receipts = state.feeReceipts,
        schoolId = schoolDocId,
        session = state.filter.session,
        startDateIso = startDateIso,
        endDateIso = endDateIso
    )
    val bundle = ConcessionReportPdfBundle(
        school = school,
        session = state.filter.session,
        filterTitle = "All Concessions",
        rows = rows
    )
    launchPhase9PdfJob(
        title = "Concession Report • ${school.name}",
        sizeLabel = "A4 Portrait"
    ) {
        Phase9PdfGenerator.generateConcessionReportPdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateOptionalSalesReportPdfPreview(
    schoolDocId: String,
    startDateIso: String? = null,
    endDateIso: String? = null
) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
    val rows = FeeReportsAndQueueEngine.buildOptionalItemSalesReport(
        receipts = state.feeReceipts,
        schoolId = schoolDocId,
        session = state.filter.session,
        startDateIso = startDateIso,
        endDateIso = endDateIso
    )
    val bundle = OptionalSalesPdfBundle(
        school = school,
        session = state.filter.session,
        filterTitle = "Optional-Item & Custom Sales",
        rows = rows
    )
    launchPhase9PdfJob(
        title = "Optional-Item Sales • ${school.name}",
        sizeLabel = "A4 Portrait"
    ) {
        Phase9PdfGenerator.generateOptionalSalesPdf(appContext, bundle)
    }
}

fun SchoolViewModel.markFeeQueueMessageSent(
    schoolDocId: String,
    studentDocId: String,
    studentName: String,
    className: String,
    section: String,
    guardianName: String,
    guardianMobileE164: String,
    messageType: FeeMessageType,
    contextKey: String,
    messageText: String,
    channel: String = "WHATSAPP",
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase9AccessEvaluator.canEditFees(state.accessProfile, schoolDocId) && !state.canEditFees) {
        onResult(false, "You do not have permission to mark messages as sent.")
        return
    }
    val uid = state.currentMember?.uid ?: currentUserId
    val uname = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    val logId = FeeMessageSentLog.makeDeterministicId(
        schoolId = schoolDocId,
        session = state.filter.session,
        studentDocId = studentDocId,
        messageType = messageType,
        contextKey = contextKey
    )
    val log = FeeMessageSentLog(
        id = logId,
        schoolId = schoolDocId,
        session = state.filter.session,
        studentId = studentDocId,
        studentName = studentName,
        className = className,
        section = section,
        guardianName = guardianName,
        normalizedMobile = guardianMobileE164,
        messageType = messageType,
        contextKey = contextKey,
        messageText = messageText.take(600),
        channel = channel,
        sentByUid = uid,
        sentByName = uname,
        sentAtMs = System.currentTimeMillis()
    )
    viewModelScope.launch {
        phase9Repo.markMessageSent(orgId, log)
            .onSuccess {
                emitMessage("Marked sent for $studentName")
                onResult(true, null)
            }
            .onFailure { err ->
                onResult(false, friendlyFirestoreErrorMessage(err, "Unable to save sent log."))
            }
    }
}

fun SchoolViewModel.saveSchoolNotice(
    schoolDocId: String,
    title: String,
    bodyText: String,
    noticeDateIso: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase9AccessEvaluator.canEditFees(state.accessProfile, schoolDocId) &&
        !Phase9AccessEvaluator.canEditStudentRegister(state.accessProfile, schoolDocId) &&
        !state.canEditFees
    ) {
        onResult(false, "You do not have permission to save notices.")
        return
    }
    viewModelScope.launch {
        phase9Repo.saveSchoolNotice(
            orgId = orgId,
            schoolId = schoolDocId,
            title = title,
            bodyText = bodyText,
            noticeDateIso = noticeDateIso.trim().ifBlank { Phase6DateUtils.todayIso() }
        ).onSuccess { notice ->
            emitMessage("Saved notice '${notice.title}'")
            onResult(true, null)
        }.onFailure { err ->
            onResult(false, friendlyFirestoreErrorMessage(err, "Unable to save notice."))
        }
    }
}

fun SchoolViewModel.deleteSchoolNotice(
    noticeId: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    viewModelScope.launch {
        phase9Repo.deleteSchoolNotice(orgId, noticeId)
            .onSuccess {
                emitMessage("Deleted notice")
                onResult(true, null)
            }
            .onFailure { err ->
                onResult(false, friendlyFirestoreErrorMessage(err, "Unable to delete notice."))
            }
    }
}

internal fun SchoolViewModel.launchPhase9PdfJob(
    title: String,
    sizeLabel: String,
    generator: () -> File
) {
    _timetablePdfState.value = TimetablePdfPreviewState(
        isGenerating = true,
        title = title,
        statusLabel = "Generating $sizeLabel PDF…",
        isLandscape = false
    )
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val file = generator()
                val totalPages = PdfExportHelper.countPdfPages(file)
                val preview = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 10)
                Triple(file, totalPages, preview)
            }
        }.onSuccess { (file, totalPages, preview) ->
            _timetablePdfState.value = _timetablePdfState.value.copy(
                isGenerating = false,
                statusLabel = "Ready ($totalPages page(s), $sizeLabel)",
                generatedFile = file,
                totalPages = totalPages,
                previewPages = preview
            )
        }.onFailure { err ->
            _timetablePdfState.value = _timetablePdfState.value.copy(
                isGenerating = false,
                error = err.message ?: "Failed to generate PDF"
            )
        }
    }
}
