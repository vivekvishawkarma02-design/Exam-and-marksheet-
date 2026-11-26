package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudOrgSettings
import com.example.domain.BackupExportDataset
import com.example.domain.BackupExportScope
import com.example.domain.BackupExportZipEngine
import com.example.domain.BackupExportZipResult
import com.example.domain.BulkImportExecutionReport
import com.example.domain.BulkImportPreviewRow
import com.example.domain.BulkStudentImportEngine
import com.example.domain.CalendarHoliday
import com.example.domain.NewSessionDataGuardSummary
import com.example.domain.OrgAccessEvaluator
import com.example.domain.ParsedTabularFile
import com.example.domain.Phase14AccessEvaluator
import com.example.domain.PromotionEngine
import com.example.domain.PromotionRecord
import com.example.domain.SessionCopyOptions
import com.example.domain.SessionTransitionEngine
import com.example.domain.SessionTransitionRecord
import com.example.domain.StudentPromotionDraft
import com.example.domain.UndoEligibilityResult
import com.example.util.Phase14PdfGenerators
import com.example.util.PromotionRegisterPdfBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// =============================================================================
// PHASE 14 VIEWMODEL EXTENSIONS:
// NEW SESSION & PROMOTION, BULK STUDENT IMPORT (CSV/XLSX), BACKUP ZIP EXPORT
// =============================================================================

fun SchoolViewModel.effectiveClassOrderForActiveSchool(): List<String> {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    val schoolClasses = state.schoolClasses.filter { it.schoolId == state.activeSchool.id }
    val saved = state.schoolClassOrders.firstOrNull { it.schoolId == schoolDocId }
    return SessionTransitionEngine.effectiveClassOrder(schoolClasses, saved)
}

fun SchoolViewModel.saveClassOrderForActiveSchool(
    orderedNames: List<String>,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        emitMessage("Please select a school first.")
        onResult(false, "No active school")
        return
    }
    if (!Phase14AccessEvaluator.canEditSessionPromotion(state.accessProfile, schoolDocId)) {
        emitMessage("You do not have permission to edit Session & Promotion settings.")
        onResult(false, "Permission denied")
        return
    }
    val actorName = state.currentMember?.name?.ifBlank { "Admin" } ?: "Admin"
    val res = phase14Repo.saveSchoolClassOrder(
        orgId = orgId,
        schoolId = schoolDocId,
        orderedClassNames = orderedNames,
        actorName = actorName
    )
    res.onSuccess {
        emitMessage("Class promotion order saved.")
        onResult(true, "Saved")
    }.onFailure { err ->
        val msg = err.message ?: "Could not save class order."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.buildPromotionDraftsForActiveSchool(
    fromSession: String,
    classOrder: List<String> = effectiveClassOrderForActiveSchool()
): List<StudentPromotionDraft> {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) return emptyList()

    val schoolId = state.activeSchool.id
    val studentsInSession = state.allStudents
        .filter { it.schoolId == schoolId && it.session.equals(fromSession, ignoreCase = true) }
        .map { st ->
            val docId = state.studentDocIdByLongId[st.id] ?: "stu_${st.id}"
            docId to st
        }

    val subjectsByClass = state.schoolClasses
        .filter { it.schoolId == schoolId }
        .associate { cls ->
            val subs = state.subjects
                .filter { it.classGroupId == cls.classGroupId }
                .sortedBy { it.sortOrder }
                .map { it.name }
            cls.name to subs
        }

    val marksByStudentId = state.allMarks.groupBy { it.studentId }
    val oldFeeStructure = state.effectivePhase14FeeStructures.firstOrNull {
        it.schoolId == schoolDocId && it.session.equals(fromSession, ignoreCase = true)
    }
    val oldOverrides = state.effectivePhase14FeeOverrides
        .filter { it.schoolId == schoolDocId && it.session.equals(fromSession, ignoreCase = true) }
        .associateBy { it.studentId }
    val oldReceipts = state.effectivePhase14FeeReceipts
        .filter { it.schoolId == schoolDocId && it.session.equals(fromSession, ignoreCase = true) }
        .groupBy { it.studentId }

    return PromotionEngine.buildPromotionDraftsForSchool(
        schoolDocId = schoolDocId,
        fromSession = fromSession,
        studentsInFromSession = studentsInSession,
        subjectsByClassName = subjectsByClass,
        marksByStudentId = marksByStudentId,
        passPercent = state.settings.passPercent,
        classOrder = classOrder,
        oldFeeStructure = oldFeeStructure,
        oldFeeOverridesByStudentDocId = oldOverrides,
        oldReceiptsByStudentDocId = oldReceipts
    )
}

fun SchoolViewModel.applyNewSessionAndPromotion(
    fromSession: String,
    toSession: String,
    startDateIso: String,
    endDateIso: String,
    copyOptions: SessionCopyOptions,
    drafts: List<StudentPromotionDraft>,
    editedHolidays: List<CalendarHoliday>?,
    startFromBatchIndex: Int = 0,
    onResult: (Result<SessionTransitionRecord>) -> Unit
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    val schoolRecord = activeSchoolRecord(state.activeSchool.id)
    if (schoolDocId.isBlank() || schoolRecord == null) {
        val err = IllegalStateException("Please select an active school first.")
        emitMessage(err.message!!)
        onResult(Result.failure(err))
        return
    }
    if (!Phase14AccessEvaluator.canEditSessionPromotion(state.accessProfile, schoolDocId)) {
        val err = SecurityException("You do not have edit access for Session and Promotion.")
        emitMessage(err.message!!)
        onResult(Result.failure(err))
        return
    }
    val validationError = SessionTransitionEngine.validateSessionNameAndDates(fromSession, toSession, startDateIso, endDateIso)
    if (validationError != null) {
        emitMessage(validationError)
        onResult(Result.failure(IllegalArgumentException(validationError)))
        return
    }

    val actorUid = currentUserId
    val actorName = state.currentMember?.name?.ifBlank { "Admin" } ?: "Admin"
    val nowMs = System.currentTimeMillis()
    val classIdByName = state.schoolClasses
        .filter { it.schoolId == state.activeSchool.id }
        .associate { it.name to it.id }
    val classDocIdByName = schoolRecord.classes.associate { it.name to it.id }

    val planResult = PromotionEngine.buildPromotionBatches(
        drafts = drafts,
        schoolId = schoolDocId,
        schoolStableId = state.activeSchool.id,
        fromSession = fromSession,
        toSession = toSession,
        classIdByClassName = classIdByName,
        confirmedByUid = actorUid,
        confirmedByName = actorName,
        confirmedAtMs = nowMs,
        batchSize = 25
    )

    val plan = planResult.getOrElse { err ->
        emitMessage(err.message ?: "Cannot apply promotion yet.")
        onResult(Result.failure(err))
        return
    }

    val oldFeeStruct = state.effectivePhase14FeeStructures.firstOrNull {
        it.schoolId == schoolDocId && it.session.equals(fromSession, ignoreCase = true)
    }
    val copiedFeeStruct = if (copyOptions.copyFeeStructure) {
        SessionTransitionEngine.copyFeeStructureForNewSession(
            oldStructure = oldFeeStruct,
            schoolId = schoolDocId,
            toSession = toSession,
            classNames = schoolRecord.classes.map { it.name }
        )
    } else null

    val oldCal = state.effectivePhase14SchoolCalendars.firstOrNull {
        it.schoolId == schoolDocId && it.session.equals(fromSession, ignoreCase = true)
    }
    val copiedCal = if (copyOptions.copySchoolCalendar) {
        SessionTransitionEngine.copySchoolCalendarForNewSession(
            oldCalendar = oldCal,
            schoolId = schoolDocId,
            toSession = toSession,
            startDateIso = startDateIso,
            endDateIso = endDateIso,
            editedHolidays = editedHolidays
        )
    } else null

    val currentCloudSettings = state.activeOrg?.settings ?: CloudOrgSettings.fromEntity(state.settings)
    val applyResult = phase14Repo.applySessionStartAndPromotionBatched(
        orgId = orgId,
        schoolId = schoolDocId,
        fromSession = fromSession,
        toSession = toSession,
        startDateIso = startDateIso,
        endDateIso = endDateIso,
        copyOptions = copyOptions,
        plan = plan,
        copiedFeeStructure = copiedFeeStruct,
        copiedCalendar = copiedCal,
        classDocIdByClassName = classDocIdByName,
        currentSettings = currentCloudSettings,
        actorName = actorName,
        startFromBatchIndex = startFromBatchIndex
    )

    applyResult.onSuccess { rec ->
        _filter.value = _filter.value.copy(session = toSession, selectedStudentId = null)
        emitMessage("Session $toSession started! ${rec.promotedCount + rec.gracePromotedCount} promoted, ${rec.detainedCount} detained, ${rec.passedOutCount} alumni.")
    }.onFailure { err ->
        emitMessage(err.message ?: "Failed to apply new session.")
    }
    onResult(applyResult)
}

fun SchoolViewModel.evaluateUndoForTransition(transition: SessionTransitionRecord): UndoEligibilityResult {
    val state = uiState.value
    val isOwner = Phase14AccessEvaluator.canUndoSessionStart(state.accessProfile)
    val createdSet = transition.createdStudentDocIds.toSet()

    val studentsInNewSession = state.allStudents.filter {
        it.schoolId == state.activeSchool.id && it.session.equals(transition.toSession, ignoreCase = true)
    }
    val newSessionStudentIds = studentsInNewSession.map { it.id }.toSet()
    val marksCount = state.allMarks.count { it.studentId in newSessionStudentIds }
    val attCount = state.studentAttendanceSheets.count {
        it.schoolId == transition.schoolId && it.session.equals(transition.toSession, ignoreCase = true)
    }
    val receiptsCount = state.effectivePhase14FeeReceipts.count {
        it.schoolId == transition.schoolId && it.session.equals(transition.toSession, ignoreCase = true)
    }
    val manualStudentsCount = studentsInNewSession.count { st ->
        val docId = state.studentDocIdByLongId[st.id].orEmpty()
        docId.isNotBlank() && docId !in createdSet
    }
    val certsCount = state.schoolCertificates.count {
        it.schoolId == transition.schoolId && it.session.equals(transition.toSession, ignoreCase = true)
    }

    val guard = NewSessionDataGuardSummary(
        marksEnteredCount = marksCount,
        attendanceSheetsCount = attCount,
        feeReceiptsCount = receiptsCount,
        manuallyAddedStudentsCount = manualStudentsCount,
        certificatesCount = certsCount
    )
    return SessionTransitionEngine.evaluateUndoEligibility(
        transition = transition,
        nowMs = System.currentTimeMillis(),
        isOwner = isOwner,
        guardSummary = guard
    )
}

fun SchoolViewModel.undoSessionStartAsOwner(
    transition: SessionTransitionRecord,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val eligibility = evaluateUndoForTransition(transition)
    if (!eligibility.allowed) {
        emitMessage(eligibility.reasonEn)
        onResult(false, eligibility.reasonEn)
        return
    }
    val currentSettings = state.activeOrg?.settings ?: CloudOrgSettings.fromEntity(state.settings)
    val actorName = state.currentMember?.name?.ifBlank { "Owner" } ?: "Owner"
    val res = phase14Repo.undoOrRollbackTransition(
        orgId = orgId,
        transition = transition,
        isOwnerUndo = true,
        currentSettings = currentSettings,
        actorName = actorName
    )
    res.onSuccess {
        val restored = transition.previousCurrentSession.ifBlank { transition.fromSession }
        _filter.value = _filter.value.copy(session = restored, selectedStudentId = null)
        emitMessage("Session ${transition.toSession} undone. Restored $restored.")
        onResult(true, "Undone")
    }.onFailure { err ->
        val msg = err.message ?: "Could not undo session start."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.rollbackIncompleteTransition(
    transition: SessionTransitionRecord,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (!Phase14AccessEvaluator.canEditSessionPromotion(state.accessProfile, transition.schoolId)) {
        emitMessage("You do not have permission to roll back this session start.")
        onResult(false, "Permission denied")
        return
    }
    val currentSettings = state.activeOrg?.settings ?: CloudOrgSettings.fromEntity(state.settings)
    val actorName = state.currentMember?.name?.ifBlank { "Admin" } ?: "Admin"
    val res = phase14Repo.undoOrRollbackTransition(
        orgId = orgId,
        transition = transition,
        isOwnerUndo = false,
        currentSettings = currentSettings,
        actorName = actorName
    )
    res.onSuccess {
        emitMessage("Incomplete session start rolled back cleanly.")
        onResult(true, "Rolled back")
    }.onFailure { err ->
        emitMessage(err.message ?: "Rollback failed.")
        onResult(false, err.message ?: "Rollback failed")
    }
}

fun SchoolViewModel.generatePromotionRegisterPdfFile(
    context: Context,
    fromSession: String,
    toSession: String,
    className: String,
    records: List<PromotionRecord>,
    onResult: (Result<File>) -> Unit
) {
    val state = uiState.value
    val school = state.activeSchool
    val confirmer = records.firstOrNull()?.confirmedByName?.ifBlank { state.currentMember?.name.orEmpty() }
        ?: state.currentMember?.name.orEmpty()
    val confirmedAt = records.maxOfOrNull { it.confirmedAtMs } ?: System.currentTimeMillis()
    viewModelScope.launch {
        val res = withContext(Dispatchers.IO) {
            runCatching {
                Phase14PdfGenerators.generatePromotionRegisterPdf(
                    context = context,
                    bundle = PromotionRegisterPdfBundle(
                        school = school,
                        fromSession = fromSession,
                        toSession = toSession,
                        className = className,
                        records = records,
                        confirmedByName = confirmer,
                        confirmedAtMs = confirmedAt
                    )
                )
            }
        }
        onResult(res)
    }
}

// =============================================================================
// BULK STUDENT IMPORT (CSV & XLSX) — Owner & Admin ONLY
// =============================================================================

fun SchoolViewModel.parseStudentImportFileUri(
    context: Context,
    uri: Uri,
    displayName: String,
    onResult: (Result<ParsedTabularFile>) -> Unit
) {
    val state = uiState.value
    if (!Phase14AccessEvaluator.canUseBulkStudentImport(state.accessProfile)) {
        val err = SecurityException("Only the Owner or Admins can use Bulk Student Import.")
        emitMessage(err.message!!)
        onResult(Result.failure(err))
        return
    }
    viewModelScope.launch {
        val res = withContext(Dispatchers.IO) {
            runCatching {
                val lower = displayName.lowercase(Locale.US)
                val mime = context.contentResolver.getType(uri)?.lowercase(Locale.US).orEmpty()
                val isXlsx = lower.endsWith(".xlsx") || mime.contains("spreadsheetml")
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    if (isXlsx) {
                        BulkStudentImportEngine.parseXlsxStream(stream, displayName).getOrThrow()
                    } else {
                        val text = stream.bufferedReader(Charsets.UTF_8).readText()
                        BulkStudentImportEngine.parseCsvText(text, displayName).getOrThrow()
                    }
                } ?: throw IllegalArgumentException("Could not open the selected file.")
            }
        }
        onResult(res)
    }
}

fun SchoolViewModel.commitBulkStudentImportRows(
    session: String,
    fileName: String,
    previewRows: List<BulkImportPreviewRow>,
    updateMatchingEnabled: Boolean,
    onResult: (Result<BulkImportExecutionReport>) -> Unit
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    val schoolRecord = activeSchoolRecord(state.activeSchool.id)
    if (schoolDocId.isBlank() || schoolRecord == null) {
        val err = IllegalStateException("Please select a school first.")
        emitMessage(err.message!!)
        onResult(Result.failure(err))
        return
    }
    if (!Phase14AccessEvaluator.canUseBulkStudentImport(state.accessProfile)) {
        val err = SecurityException("Only the Owner or Admins can import students in bulk.")
        emitMessage(err.message!!)
        onResult(Result.failure(err))
        return
    }
    val existingByDocId = state.allStudents
        .filter { it.schoolId == state.activeSchool.id }
        .associateBy { state.studentDocIdByLongId[it.id] ?: "stu_${it.id}" }
    val classDocIdByName = schoolRecord.classes.associate { it.name to it.id }
    val actorName = state.currentMember?.name?.ifBlank { "Admin" } ?: "Admin"

    val res = phase14Repo.commitBulkStudentImport(
        orgId = orgId,
        schoolId = schoolDocId,
        schoolStableId = state.activeSchool.id,
        schoolName = state.activeSchool.name,
        session = session,
        fileName = fileName,
        previewRows = previewRows,
        existingStudentsByDocId = existingByDocId,
        classDocIdByClassName = classDocIdByName,
        updateMatchingEnabled = updateMatchingEnabled,
        actorName = actorName
    )
    res.onSuccess { rep ->
        emitMessage("Import complete: ${rep.addedCount} added, ${rep.updatedCount} updated, ${rep.skippedCount} skipped.")
    }.onFailure { err ->
        emitMessage(err.message ?: "Bulk student import failed.")
    }
    onResult(res)
}

fun SchoolViewModel.generateBulkImportReportPdfFile(
    context: Context,
    report: BulkImportExecutionReport,
    onResult: (Result<File>) -> Unit
) {
    viewModelScope.launch {
        val res = withContext(Dispatchers.IO) {
            runCatching { Phase14PdfGenerators.generateBulkImportReportPdf(context, report) }
        }
        onResult(res)
    }
}

// =============================================================================
// BACKUP & EXPORT ZIP (Owner & Admin ONLY; Salary CSV Owner ONLY)
// =============================================================================

fun SchoolViewModel.buildBackupExportZip(
    scope: BackupExportScope,
    onResult: (Result<BackupExportZipResult>) -> Unit
) {
    val state = uiState.value
    if (!Phase14AccessEvaluator.canUseBackupExport(state.accessProfile)) {
        val err = SecurityException("Only the Owner or Admins can export a data backup.")
        emitMessage(err.message!!)
        onResult(Result.failure(err))
        return
    }

    val activeSchoolId = state.activeSchool.id
    val activeSchoolDocId = state.activeSchoolDocId
    val selectedSchools = if (scope == BackupExportScope.ORGANISATION) {
        state.schools
    } else {
        state.schools.filter { it.id == activeSchoolId }
    }
    val selectedSchoolIds = selectedSchools.map { it.id }.toSet()
    val selectedSchoolDocIds = selectedSchools.mapNotNull { state.schoolDocIdByLongId[it.id] }.toSet()

    val studentsPairs = state.allStudents
        .filter { it.schoolId in selectedSchoolIds }
        .map { st -> (state.studentDocIdByLongId[st.id] ?: "stu_${st.id}") to st }
    val selectedStudentLongIds = studentsPairs.map { it.second.id }.toSet()

    val includeSalary = Phase14AccessEvaluator.canIncludeSalaryInBackup(state.accessProfile)

    val dataset = BackupExportDataset(
        orgName = state.activeOrg?.name ?: state.activeSchool.name,
        scope = scope,
        schools = selectedSchools,
        schoolDocIdByStableId = state.schoolDocIdByLongId,
        classGroups = state.classGroups.filter { it.schoolId in selectedSchoolIds },
        schoolClasses = state.schoolClasses.filter { it.schoolId in selectedSchoolIds },
        subjects = state.subjects.filter { it.schoolId in selectedSchoolIds },
        students = studentsPairs,
        marks = state.allMarks.filter { it.studentId in selectedStudentLongIds },
        feeStructures = state.effectivePhase14FeeStructures.filter { it.schoolId in selectedSchoolDocIds },
        feeOverrides = state.effectivePhase14FeeOverrides.filter { it.schoolId in selectedSchoolDocIds },
        feeReceipts = state.effectivePhase14FeeReceipts.filter { it.schoolId in selectedSchoolDocIds },
        attendanceSheets = state.studentAttendanceSheets.filter { it.schoolId in selectedSchoolDocIds },
        teachers = state.effectivePhase12Teachers.filter { t ->
            t.schoolIds.isEmpty() || t.schoolIds.any { it in selectedSchoolDocIds }
        },
        cashBookEntries = state.cashBookEntries.filter { it.schoolId in selectedSchoolDocIds },
        certificates = state.schoolCertificates.filter { it.schoolId in selectedSchoolDocIds },
        salaryMonthlyRecords = if (includeSalary) {
            state.salaryMonthlyRecords.filter { it.schoolId in selectedSchoolDocIds }
        } else emptyList(),
        activeSession = state.filter.session
    )

    viewModelScope.launch {
        val res = withContext(Dispatchers.IO) {
            BackupExportZipEngine.buildBackupZip(
                dataset = dataset,
                actorMember = state.accessProfile
            )
        }
        res.onSuccess { zipRes ->
            val currentSettings = state.activeOrg?.settings ?: CloudOrgSettings.fromEntity(state.settings)
            val actorName = state.currentMember?.name?.ifBlank { "Admin" } ?: "Admin"
            val actorRole = if (OrgAccessEvaluator.isOwner(state.accessProfile)) "OWNER" else "ADMIN"
            phase14Repo.recordBackupExport(
                orgId = orgId,
                scope = scope,
                schoolId = if (scope == BackupExportScope.SCHOOL) activeSchoolDocId else "",
                fileName = zipRes.fileName,
                includedSalaryCsv = zipRes.includedSalaryCsv,
                studentCount = zipRes.studentCount,
                receiptCount = zipRes.receiptCount,
                actorName = actorName,
                actorRole = actorRole,
                currentSettings = currentSettings
            )
        }
        onResult(res)
    }
}
