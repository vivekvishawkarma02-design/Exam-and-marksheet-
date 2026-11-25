package com.example.ui

import androidx.lifecycle.viewModelScope
import com.example.data.StudentEntity
import com.example.domain.AdmissionEnquiryEngine
import com.example.domain.AdmissionEnquiryRecord
import com.example.domain.CashBookDayClosingRecord
import com.example.domain.CashBookDaySummary
import com.example.domain.CashBookEngine
import com.example.domain.CashBookEntryRecord
import com.example.domain.CashBookEntryType
import com.example.domain.CashBookMonthSummary
import com.example.domain.CashBookSettingsConfig
import com.example.domain.CertificateEngine
import com.example.domain.CertificateStatus
import com.example.domain.CertificateType
import com.example.domain.EnquirySettingsConfig
import com.example.domain.EnquiryStatus
import com.example.domain.PaymentMode
import com.example.domain.Phase10AccessEvaluator
import com.example.domain.Phase6DateUtils
import com.example.domain.SchoolCertificateRecord
import com.example.util.CashBookCategoryExpensePdfBundle
import com.example.util.CashBookDayBookPdfBundle
import com.example.util.CashBookMonthSummaryPdfBundle
import com.example.util.CertificatePdfBundle
import com.example.util.Phase10PdfGenerator
import com.example.util.friendlyFirestoreErrorMessage
import kotlinx.coroutines.launch

// =============================================================================
// PHASE 10 VIEWMODEL EXTENSIONS: CERTIFICATES, CASH BOOK, ADMISSION ENQUIRY
// =============================================================================

fun SchoolViewModel.togglePhase10HindiLabels() {
    val next = !_phase10HindiLabels.value
    _phase10HindiLabels.value = next
    offlinePrefs.setHindiLabelsEnabled(next)
}

fun SchoolViewModel.setPhase10HindiLabels(useHindi: Boolean) {
    _phase10HindiLabels.value = useHindi
    offlinePrefs.setHindiLabelsEnabled(useHindi)
}

fun SchoolViewModel.setFirstTimeGuideSkipped(skipped: Boolean) {
    _firstTimeGuideSkipped.value = skipped
    offlinePrefs.setFirstTimeGuideSkipped(currentUserId, orgId, skipped)
}

// =============================================================================
// 1. CERTIFICATES ACTIONS
// =============================================================================

fun SchoolViewModel.issueSchoolCertificate(
    student: StudentEntity,
    certificateType: CertificateType,
    issueDateIso: String,
    reasonForLeaving: String,
    lastAttendanceDateIso: String,
    conduct: String,
    promotionStatus: String,
    feeDuesStatus: String,
    remarks: String,
    reissuedFromCert: SchoolCertificateRecord? = null,
    onResult: (Boolean, String?, SchoolCertificateRecord?) -> Unit = { _, _, _ -> }
) {
    val state = uiState.value
    val rec = activeSchoolRecord(student.schoolId) ?: activeSchoolRecord() ?: run {
        onResult(false, "School not found.", null)
        return
    }
    if (!Phase10AccessEvaluator.canEditCertificates(state.accessProfile, rec.docId) && !state.canEditCertificates) {
        onResult(false, "View-only access: only members with Certificates Edit access can issue certificates.", null)
        return
    }
    val session = student.session.ifBlank { state.filter.session }
    val nodeCode = state.deviceNodeCode.ifBlank { offlinePrefs.getOrCreateDeviceNodeCode(currentUserId) }
    val studentDocId = state.studentDocIdFor(student.id).ifBlank { student.admNo }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Office Staff"
    val (certNo, seqNo) = CertificateEngine.generateNextCertificateNumber(
        schoolId = rec.docId,
        session = session,
        certificateType = certificateType,
        deviceNodeCode = nodeCode,
        existingCertificates = state.schoolCertificates
    )
    val docId = SchoolCertificateRecord.makeDocId(rec.docId, session, certNo)
    val nowMs = System.currentTimeMillis()
    val record = SchoolCertificateRecord(
        id = docId,
        schoolId = rec.docId,
        session = session,
        certificateType = certificateType,
        certificateNumber = certNo,
        deviceNodeCode = nodeCode,
        sequenceNo = seqNo,
        issueDateIso = issueDateIso.trim().ifBlank { Phase6DateUtils.todayIso() },
        studentId = studentDocId,
        studentName = student.name,
        studentAdmNo = student.admNo,
        fatherName = student.fatherName,
        motherName = student.motherName,
        dob = student.dob,
        className = student.className,
        section = student.section,
        rollNo = student.rollNo,
        admissionDate = student.admissionDate,
        address = student.address,
        reasonForLeaving = reasonForLeaving.trim(),
        lastAttendanceDateIso = lastAttendanceDateIso.trim(),
        conduct = conduct.trim().ifBlank { "Good" },
        promotionStatus = promotionStatus.trim(),
        feeDuesStatus = feeDuesStatus.trim(),
        remarks = remarks.trim(),
        status = CertificateStatus.ISSUED,
        issuedByUid = currentUserId,
        issuedByName = actorName,
        issuedAtMs = nowMs,
        reissuedFromCertId = reissuedFromCert?.id.orEmpty(),
        reissuedFromCertNumber = reissuedFromCert?.certificateNumber.orEmpty()
    )
    viewModelScope.launch {
        phase10Repo.issueCertificate(orgId, record)
            .onSuccess { saved ->
                emitMessage("Issued ${certificateType.shortLabel} $certNo for ${student.name}")
                onResult(true, null, saved)
            }
            .onFailure { err ->
                onResult(false, friendlyFirestoreErrorMessage(err, "Unable to issue certificate."), null)
            }
    }
}

fun SchoolViewModel.cancelSchoolCertificate(
    cert: SchoolCertificateRecord,
    cancelReason: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase10AccessEvaluator.canEditCertificates(state.accessProfile, cert.schoolId) && !state.canEditCertificates) {
        onResult(false, "You do not have permission to cancel certificates.")
        return
    }
    val err = CertificateEngine.buildCancelUpdateValidation(cert, cancelReason)
    if (err != null) {
        onResult(false, err)
        return
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Office Staff"
    viewModelScope.launch {
        phase10Repo.cancelCertificate(orgId, cert.id, actorName, cancelReason.trim())
            .onSuccess {
                emitMessage("Cancelled ${cert.certificateNumber}")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to cancel certificate."))
            }
    }
}

fun SchoolViewModel.markStudentLeftAfterTc(
    student: StudentEntity,
    leftDateIso: String,
    leftReason: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val rec = activeSchoolRecord(student.schoolId) ?: run {
        onResult(false, "School not found.")
        return
    }
    val studentDocId = state.studentDocIdFor(student.id)
    if (studentDocId.isBlank()) {
        onResult(false, "Student record not synced yet.")
        return
    }
    if (!state.canEditCertificates && !state.canEditStudentRegister) {
        onResult(false, "You do not have permission to mark this student left.")
        return
    }
    viewModelScope.launch {
        academicRepo.markStudentLeftFromCertificate(
            orgId = orgId,
            schoolDocId = rec.docId,
            studentDocId = studentDocId,
            studentName = student.name,
            leftDateIso = leftDateIso.trim().ifBlank { Phase6DateUtils.todayIso() },
            leftReason = leftReason.trim().ifBlank { "Transfer Certificate issued" }
        ).onSuccess {
            emitMessage("Marked ${student.name} as Left School")
            onResult(true, null)
        }.onFailure { e ->
            onResult(false, friendlyFirestoreErrorMessage(e, "Unable to mark student left."))
        }
    }
}

fun SchoolViewModel.generateCertificateA4Pdf(cert: SchoolCertificateRecord) {
    val state = uiState.value
    val schoolLongId = state.schoolRecordsByDocId[cert.schoolId]?.stableId ?: state.activeSchool.id
    val school = state.schoolById(schoolLongId) ?: state.activeSchool
    val bundle = CertificatePdfBundle(school = school, certificate = cert)
    launchPhase9PdfJob(
        title = "${cert.certificateType.englishTitle} • ${cert.certificateNumber}",
        sizeLabel = "A4 Portrait"
    ) {
        Phase10PdfGenerator.generateCertificatePdf(appContext, bundle)
    }
}

// =============================================================================
// 2. CASH BOOK ACTIONS
// =============================================================================

fun SchoolViewModel.saveCashBookSettings(
    expenseCategories: List<String>,
    otherIncomeCategories: List<String>,
    expenseApprovalLimitRs: Int,
    defaultOpeningBalanceRs: Int,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        onResult(false, "Active school not found.")
        return
    }
    if (!Phase10AccessEvaluator.canEditCashBook(state.accessProfile, schoolDocId) && !state.canEditCashBook) {
        onResult(false, "You do not have permission to edit Cash Book settings.")
        return
    }
    val existing = state.cashBookSettingsList.firstOrNull { it.schoolId == schoolDocId }
    val effectiveLimit = if (state.isAdmin) {
        expenseApprovalLimitRs.coerceIn(0, 10_000_000)
    } else {
        existing?.expenseApprovalLimitRs ?: CashBookSettingsConfig.DEFAULT_APPROVAL_LIMIT_RS
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    val config = CashBookSettingsConfig(
        id = existing?.id ?: CashBookSettingsConfig.makeDocId(schoolDocId),
        schoolId = schoolDocId,
        expenseCategories = expenseCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .ifEmpty { CashBookSettingsConfig.DEFAULT_EXPENSE_CATEGORIES },
        otherIncomeCategories = otherIncomeCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .ifEmpty { CashBookSettingsConfig.DEFAULT_OTHER_INCOME_CATEGORIES },
        expenseApprovalLimitRs = effectiveLimit,
        defaultOpeningBalanceRs = defaultOpeningBalanceRs
    )
    viewModelScope.launch {
        phase10Repo.saveCashBookSettings(orgId, config, actorName, existsInCloud = existing != null)
            .onSuccess {
                emitMessage("Saved Cash Book settings")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save Cash Book settings."))
            }
    }
}

fun SchoolViewModel.addCashBookEntry(
    dateIso: String,
    entryType: CashBookEntryType,
    category: String,
    amount: Int,
    paymentMode: PaymentMode,
    note: String,
    billPhotoBase64: String,
    approvedByAdminUid: String = "",
    approvedByAdminName: String = "",
    approvalNote: String = "",
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        onResult(false, "Active school not found.")
        return
    }
    if (!Phase10AccessEvaluator.canEditCashBook(state.accessProfile, schoolDocId) && !state.canEditCashBook) {
        onResult(false, "View-only access: only Cash Book editors can add entries.")
        return
    }
    val settings = state.cashBookSettingsForSchool(schoolDocId)
    val err = CashBookEngine.validateNewOrEditedEntry(
        entryType = entryType,
        category = category,
        amount = amount,
        member = state.accessProfile,
        approvalLimitRs = settings.expenseApprovalLimitRs,
        approvedByAdminUid = approvedByAdminUid
    )
    if (err != null) {
        onResult(false, err)
        return
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    val nowMs = System.currentTimeMillis()
    val entry = CashBookEntryRecord(
        id = "",
        schoolId = schoolDocId,
        dateIso = dateIso.trim().ifBlank { Phase6DateUtils.todayIso() },
        entryType = entryType,
        category = category.trim(),
        amount = amount,
        paymentMode = paymentMode,
        note = note.trim(),
        billPhotoBase64 = billPhotoBase64,
        approvedByAdminUid = approvedByAdminUid.trim(),
        approvedByAdminName = approvedByAdminName.trim(),
        approvalNote = approvalNote.trim(),
        createdByUid = currentUserId,
        createdByName = actorName,
        createdAtMs = nowMs,
        updatedByUid = currentUserId,
        updatedByName = actorName,
        clientEditedAtMs = nowMs
    )
    viewModelScope.launch {
        phase10Repo.addCashBookEntry(orgId, entry)
            .onSuccess {
                emitMessage("Saved ${entryType.englishLabel} ₹$amount ($category)")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save entry."))
            }
    }
}

fun SchoolViewModel.editCashBookEntry(
    original: CashBookEntryRecord,
    newCategory: String,
    newAmount: Int,
    newPaymentMode: PaymentMode,
    newNote: String,
    newBillPhotoBase64: String,
    editReason: String,
    approvedByAdminUid: String = original.approvedByAdminUid,
    approvedByAdminName: String = original.approvedByAdminName,
    approvalNote: String = original.approvalNote,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase10AccessEvaluator.canEditCashBook(state.accessProfile, original.schoolId) && !state.canEditCashBook) {
        onResult(false, "You do not have permission to edit Cash Book entries.")
        return
    }
    if (original.isCancelled) {
        onResult(false, "Cancelled entries cannot be edited.")
        return
    }
    val settings = state.cashBookSettingsForSchool(original.schoolId)
    val err = CashBookEngine.validateNewOrEditedEntry(
        entryType = original.entryType,
        category = newCategory,
        amount = newAmount,
        member = state.accessProfile,
        approvalLimitRs = settings.expenseApprovalLimitRs,
        approvedByAdminUid = approvedByAdminUid,
        editReason = editReason
    )
    if (err != null) {
        onResult(false, err)
        return
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    viewModelScope.launch {
        phase10Repo.editCashBookEntry(
            orgId = orgId,
            original = original,
            newCategory = newCategory,
            newAmount = newAmount,
            newPaymentMode = newPaymentMode,
            newNote = newNote,
            newBillPhotoBase64 = newBillPhotoBase64,
            editReason = editReason,
            editorName = actorName,
            approvedByAdminUid = approvedByAdminUid,
            approvedByAdminName = approvedByAdminName,
            approvalNote = approvalNote
        ).onSuccess {
            emitMessage("Updated entry (₹$newAmount) and recorded audit log")
            onResult(true, null)
        }.onFailure { e ->
            onResult(false, friendlyFirestoreErrorMessage(e, "Unable to edit entry."))
        }
    }
}

fun SchoolViewModel.cancelCashBookEntry(
    original: CashBookEntryRecord,
    cancelReason: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase10AccessEvaluator.canEditCashBook(state.accessProfile, original.schoolId) && !state.canEditCashBook) {
        onResult(false, "You do not have permission to cancel Cash Book entries.")
        return
    }
    if (original.isCancelled) {
        onResult(false, "Entry is already cancelled.")
        return
    }
    if (cancelReason.trim().isEmpty()) {
        onResult(false, "Please enter a reason to cancel this entry.")
        return
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    viewModelScope.launch {
        phase10Repo.cancelCashBookEntry(orgId, original, cancelReason.trim(), actorName)
            .onSuccess {
                emitMessage("Cancelled entry and logged reason")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to cancel entry."))
            }
    }
}

fun SchoolViewModel.saveCashBookDayClosing(
    dateIso: String,
    openingCashBalance: Int,
    countedCash: Int,
    note: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        onResult(false, "Active school not found.")
        return
    }
    if (!Phase10AccessEvaluator.canEditCashBook(state.accessProfile, schoolDocId) && !state.canEditCashBook) {
        onResult(false, "You do not have permission to save Day Closing.")
        return
    }
    val settings = state.cashBookSettingsForSchool(schoolDocId)
    val summary = CashBookEngine.computeDaySummary(
        schoolId = schoolDocId,
        dateIso = dateIso,
        feeReceipts = state.effectiveCashBookFeeReceipts,
        entries = state.cashBookEntries,
        dayClosings = state.cashBookDayClosings,
        settings = settings,
        openingBalanceOverride = openingCashBalance
    )
    val diff = CashBookEngine.computeDayClosingDifference(summary.expectedClosingCash, countedCash)
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    val existing = state.cashBookDayClosings.firstOrNull { it.schoolId == schoolDocId && it.dateIso == dateIso }
    val rec = CashBookDayClosingRecord(
        id = existing?.id ?: CashBookDayClosingRecord.makeDocId(schoolDocId, dateIso),
        schoolId = schoolDocId,
        dateIso = dateIso,
        openingCashBalance = openingCashBalance,
        cashInTotal = summary.totalCashIn,
        cashOutTotal = summary.totalCashOut,
        expectedClosingCash = summary.expectedClosingCash,
        countedCash = countedCash,
        cashDifference = diff,
        nonCashInTotal = summary.nonCashInTotal,
        nonCashOutTotal = summary.nonCashOutTotal,
        note = note.trim(),
        closedByUid = currentUserId,
        closedByName = actorName,
        closedAtMs = System.currentTimeMillis()
    )
    viewModelScope.launch {
        phase10Repo.saveDayClosing(orgId, rec, existsInCloud = existing != null)
            .onSuccess {
                emitMessage("Saved Day Closing for $dateIso (Diff: ₹$diff)")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save Day Closing."))
            }
    }
}

fun SchoolViewModel.generateCashBookDayBookPdf(summary: CashBookDaySummary) {
    val school = uiState.value.activeSchool
    val bundle = CashBookDayBookPdfBundle(school = school, summary = summary)
    launchPhase9PdfJob(
        title = "Day Book • ${summary.dateIso}",
        sizeLabel = "A4 Portrait"
    ) {
        Phase10PdfGenerator.generateCashBookDayBookPdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateCashBookMonthSummaryPdf(summary: CashBookMonthSummary) {
    val school = uiState.value.activeSchool
    val bundle = CashBookMonthSummaryPdfBundle(school = school, summary = summary)
    launchPhase9PdfJob(
        title = "Month Cash Summary • ${summary.yearMonth}",
        sizeLabel = "A4 Portrait"
    ) {
        Phase10PdfGenerator.generateCashBookMonthSummaryPdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateCashBookCategoryExpensePdf(summary: CashBookMonthSummary) {
    val school = uiState.value.activeSchool
    val bundle = CashBookCategoryExpensePdfBundle(school = school, summary = summary)
    launchPhase9PdfJob(
        title = "Category Expenses • ${summary.yearMonth}",
        sizeLabel = "A4 Portrait"
    ) {
        Phase10PdfGenerator.generateCashBookCategoryExpensePdf(appContext, bundle)
    }
}

// =============================================================================
// 3. ADMISSION ENQUIRY ACTIONS
// =============================================================================

fun SchoolViewModel.saveEnquirySettings(
    sources: List<String>,
    whatsappTemplate: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        onResult(false, "Active school not found.")
        return
    }
    if (!Phase10AccessEvaluator.canEditEnquiry(state.accessProfile, schoolDocId) && !state.canEditEnquiry) {
        onResult(false, "You do not have permission to edit Enquiry settings.")
        return
    }
    val existing = state.enquirySettingsList.firstOrNull { it.schoolId == schoolDocId }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    val cfg = EnquirySettingsConfig(
        id = existing?.id ?: EnquirySettingsConfig.makeDocId(schoolDocId),
        schoolId = schoolDocId,
        sources = sources.map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { EnquirySettingsConfig.DEFAULT_SOURCES },
        whatsappTemplate = whatsappTemplate.trim().ifBlank { EnquirySettingsConfig.DEFAULT_WHATSAPP_TEMPLATE }
    )
    viewModelScope.launch {
        phase10Repo.saveEnquirySettings(orgId, cfg, actorName, existsInCloud = existing != null)
            .onSuccess {
                emitMessage("Saved Enquiry settings")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save Enquiry settings."))
            }
    }
}

fun SchoolViewModel.saveAdmissionEnquiry(
    existing: AdmissionEnquiryRecord?,
    childName: String,
    classWanted: String,
    guardianName: String,
    mobile: String,
    source: String,
    notes: String,
    nextFollowUpDateIso: String,
    status: EnquiryStatus = existing?.status ?: EnquiryStatus.NEW,
    lostReason: String = existing?.lostReason.orEmpty(),
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = existing?.schoolId?.ifBlank { state.activeSchoolDocId } ?: state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        onResult(false, "Active school not found.")
        return
    }
    if (!Phase10AccessEvaluator.canEditEnquiry(state.accessProfile, schoolDocId) && !state.canEditEnquiry) {
        onResult(false, "View-only access: only Enquiry editors can save enquiries.")
        return
    }
    if (childName.trim().isEmpty()) {
        onResult(false, "Child name is required.")
        return
    }
    if (guardianName.trim().isEmpty()) {
        onResult(false, "Guardian name is required.")
        return
    }
    if (status == EnquiryStatus.LOST && lostReason.trim().isEmpty()) {
        onResult(false, "Please enter a reason when marking enquiry as Lost.")
        return
    }
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    val today = Phase6DateUtils.todayIso()
    val nowMs = System.currentTimeMillis()
    val rec = AdmissionEnquiryRecord(
        id = existing?.id.orEmpty(),
        schoolId = schoolDocId,
        session = existing?.session?.ifBlank { state.filter.session } ?: state.filter.session,
        childName = childName.trim(),
        classWanted = classWanted.trim(),
        guardianName = guardianName.trim(),
        mobile = mobile.trim(),
        source = source.trim().ifBlank { "Walk-in" },
        notes = notes.trim(),
        enquiryDateIso = existing?.enquiryDateIso?.ifBlank { today } ?: today,
        nextFollowUpDateIso = nextFollowUpDateIso.trim().ifBlank { today },
        status = status,
        lostReason = if (status == EnquiryStatus.LOST) lostReason.trim() else "",
        convertedStudentDocId = existing?.convertedStudentDocId.orEmpty(),
        convertedStudentAdmNo = existing?.convertedStudentAdmNo.orEmpty(),
        convertedAtMs = existing?.convertedAtMs ?: 0L,
        createdByUid = existing?.createdByUid.orEmpty().ifBlank { currentUserId },
        createdByName = existing?.createdByName.orEmpty().ifBlank { actorName },
        createdAtMs = existing?.createdAtMs?.takeIf { it > 0L } ?: nowMs,
        updatedByUid = currentUserId,
        updatedByName = actorName,
        clientEditedAtMs = nowMs
    )
    viewModelScope.launch {
        phase10Repo.saveEnquiry(orgId, rec, existsInCloud = existing != null)
            .onSuccess {
                emitMessage(if (existing == null) "Added enquiry for ${rec.childName}" else "Updated enquiry for ${rec.childName}")
                onResult(true, null)
            }
            .onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Unable to save enquiry."))
            }
    }
}

fun SchoolViewModel.convertEnquiryToStudent(
    enquiry: AdmissionEnquiryRecord,
    admNo: String,
    className: String,
    section: String,
    rollNo: Int,
    admissionDateIso: String,
    onResult: (Boolean, String?) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val rec = activeSchoolRecord(state.activeSchool.id) ?: run {
        onResult(false, "Active school not found.")
        return
    }
    if (!Phase10AccessEvaluator.canEditEnquiry(state.accessProfile, rec.docId) && !state.canEditEnquiry) {
        onResult(false, "You do not have permission to convert enquiries.")
        return
    }
    val cleanAdmNo = admNo.trim()
    if (cleanAdmNo.isEmpty()) {
        onResult(false, "Admission No. is required.")
        return
    }
    val session = enquiry.session.ifBlank { state.filter.session }
    val dup = state.allStudents.any {
        it.schoolId == rec.stableId &&
            it.session.equals(session, ignoreCase = true) &&
            it.admNo.equals(cleanAdmNo, ignoreCase = true)
    }
    if (dup) {
        onResult(false, "Admission No. '$cleanAdmNo' already exists in this session.")
        return
    }
    val clsEntity = state.classesForSchool(rec.stableId).firstOrNull {
        it.name.equals(className.trim(), ignoreCase = true)
    }
    val clsDocId = rec.classes.firstOrNull { it.name.equals(className.trim(), ignoreCase = true) }?.id.orEmpty()
    val student = AdmissionEnquiryEngine.buildStudentFromEnquiry(
        enquiry = enquiry.copy(classWanted = className.trim()),
        schoolLongId = rec.stableId,
        classId = clsEntity?.id ?: 0L,
        session = session,
        admNo = cleanAdmNo,
        section = section,
        rollNo = rollNo,
        admissionDateIso = admissionDateIso.ifBlank { Phase6DateUtils.todayIso() }
    )
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember.email } ?: "Staff"
    viewModelScope.launch {
        val stuRes = academicRepo.saveStudent(
            orgId = orgId,
            schoolDocId = rec.docId,
            existingDocId = null,
            student = student,
            classDocId = clsDocId
        )
        stuRes.onSuccess { newStudentDocId ->
            phase10Repo.markEnquiryConverted(
                orgId = orgId,
                enquiryId = enquiry.id,
                studentDocId = newStudentDocId,
                studentAdmNo = cleanAdmNo,
                actorName = actorName
            ).onSuccess {
                emitMessage("Converted ${enquiry.childName} to Student ($cleanAdmNo) in Student Register!")
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, friendlyFirestoreErrorMessage(e, "Student created, but failed to update enquiry link."))
            }
        }.onFailure { e ->
            onResult(false, friendlyFirestoreErrorMessage(e, "Unable to create student from enquiry."))
        }
    }
}
