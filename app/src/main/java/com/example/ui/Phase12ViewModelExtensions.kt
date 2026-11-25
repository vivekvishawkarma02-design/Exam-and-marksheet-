package com.example.ui

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.example.domain.OrgAccessEvaluator
import com.example.domain.PaymentMode
import com.example.domain.Phase12AccessEvaluator
import com.example.domain.PulledStaffAttendanceCounts
import com.example.domain.SalaryMonthlyRecord
import com.example.domain.SalaryRuleConfig
import com.example.domain.SalarySlipPageSize
import com.example.domain.StaffSalaryAdvanceRecord
import com.example.domain.StaffSalaryEngine
import com.example.domain.StaffSalaryProfileRecord
import com.example.util.Phase12PdfGenerator
import com.example.util.SalaryMonthSummaryPdfBundle
import com.example.util.SalarySlipPdfBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// =============================================================================
// PHASE 12 VIEWMODEL EXTENSIONS: STAFF SALARY
// =============================================================================

fun SchoolViewModel.saveSalaryRuleConfigForActiveSchool(
    updatedConfig: SalaryRuleConfig,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        emitMessage("Please select an active school first.")
        onResult(false, "No active school")
        return
    }
    if (!Phase12AccessEvaluator.canEditSalary(state.accessProfile, schoolDocId)) {
        emitMessage("You do not have permission to edit Salary settings.")
        onResult(false, "Permission denied")
        return
    }
    val previousConfig = state.salaryRuleConfigForSchool(schoolDocId)
    if (updatedConfig.allowTeachersToViewOwnSlips != previousConfig.allowTeachersToViewOwnSlips &&
        !OrgAccessEvaluator.isOwner(state.accessProfile)
    ) {
        emitMessage("Only the Organisation Owner can enable or disable teacher salary slip visibility.")
        onResult(false, "Owner required")
        return
    }
    val normalized = updatedConfig.copy(
        id = SalaryRuleConfig.defaultdocId(schoolDocId),
        schoolId = schoolDocId
    )
    phase12Repo.saveSalaryRuleConfig(
        orgId = orgId,
        config = normalized,
        existingSchoolMonthlyRecords = state.salaryMonthlyRecords.filter { it.schoolId == schoolDocId }
    )
    emitMessage("Salary rules updated.")
    onResult(true, "Saved")
}

/**
 * 1-tap sync of active Phase 4 teachers into `staffSalaryProfiles` so staff setup takes seconds.
 */
fun SchoolViewModel.syncTeachersToSalaryProfiles(
    defaultBasicRs: Int = 15000,
    onResult: (Int) -> Unit = {}
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        emitMessage("Please select an active school first.")
        return
    }
    if (!Phase12AccessEvaluator.canEditSalary(state.accessProfile, schoolDocId)) {
        emitMessage("You do not have permission to edit Staff Salary setup.")
        return
    }
    val rule = state.salaryRuleConfigForSchool(schoolDocId)
    val existingByStaffId = state.staffSalaryProfiles.associateBy { it.staffId }
    val teachersForSchool = state.effectivePhase12Teachers.filter {
        it.active && (it.schoolIds.isEmpty() || schoolDocId in it.schoolIds)
    }
    var addedCount = 0
    for (teacher in teachersForSchool) {
        val existing = existingByStaffId[teacher.id]
        if (existing == null) {
            val profile = StaffSalaryProfileRecord(
                id = StaffSalaryProfileRecord.docIdForStaff(teacher.id),
                staffId = teacher.id,
                staffName = teacher.name,
                designation = "Teacher",
                isTeachingStaff = true,
                linkedMemberUid = teacher.linkedMemberUid,
                joiningDateIso = "",
                allSchools = teacher.schoolIds.size > 1,
                schoolIds = if (teacher.schoolIds.isEmpty()) listOf(schoolDocId) else teacher.schoolIds,
                primarySchoolId = schoolDocId,
                basicMonthlySalaryRs = defaultBasicRs.coerceAtLeast(0),
                allowances = rule.defaultAllowances,
                fixedDeductions = rule.defaultDeductions,
                active = true
            )
            phase12Repo.saveStaffSalaryProfile(orgId, profile)
            addedCount++
        } else if (existing.linkedMemberUid != teacher.linkedMemberUid && teacher.linkedMemberUid.isNotBlank()) {
            phase12Repo.saveStaffSalaryProfile(
                orgId,
                existing.copy(linkedMemberUid = teacher.linkedMemberUid, staffName = teacher.name)
            )
        }
    }
    if (addedCount > 0) {
        emitMessage("Imported $addedCount teacher(s) into Staff Salary Setup.")
    } else {
        emitMessage("All active teachers for this school are already in Staff Salary Setup.")
    }
    onResult(addedCount)
}

fun SchoolViewModel.saveStaffSalaryProfileForActiveSchool(
    profile: StaffSalaryProfileRecord,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        emitMessage("Please select an active school first.")
        onResult(false, "No active school")
        return
    }
    if (!Phase12AccessEvaluator.canEditSalary(state.accessProfile, schoolDocId)) {
        emitMessage("You do not have permission to edit Staff Salary setup.")
        onResult(false, "Permission denied")
        return
    }
    if (profile.staffName.trim().isEmpty()) {
        emitMessage("Staff name is required.")
        onResult(false, "Name required")
        return
    }
    val schoolIds = if (profile.allSchools) {
        state.schoolDocIdByLongId.values.toList().ifEmpty { listOf(schoolDocId) }
    } else {
        profile.schoolIds.ifEmpty { listOf(schoolDocId) }
    }
    val normalized = profile.copy(
        primarySchoolId = profile.primarySchoolId.ifBlank { schoolDocId },
        schoolIds = schoolIds
    )
    phase12Repo.saveStaffSalaryProfile(orgId, normalized)
    emitMessage("Saved salary setup for ${normalized.staffName}.")
    onResult(true, "Saved")
}

fun SchoolViewModel.deleteStaffSalaryProfileForActiveSchool(profile: StaffSalaryProfileRecord) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (!Phase12AccessEvaluator.canEditSalary(state.accessProfile, schoolDocId)) {
        emitMessage("You do not have permission to delete Staff Salary setup.")
        return
    }
    phase12Repo.deleteStaffSalaryProfile(orgId, profile.id)
    emitMessage("Removed salary profile for ${profile.staffName}.")
}

/**
 * Pulls present, absent, leave, half-day, and late days from Phase 7 teacher attendance
 * for all active staff profiles assigned to the active school and computes/saves the monthly sheet.
 */
fun SchoolViewModel.pullAttendanceAndComputeMonthlySalarySheet(
    yearMonth: String,
    lockOverrideReason: String = "",
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (schoolDocId.isBlank()) {
        emitMessage("Please select an active school first.")
        onResult(false, "No active school")
        return
    }
    if (!Phase12AccessEvaluator.canEditSalary(state.accessProfile, schoolDocId)) {
        emitMessage("You do not have permission to edit the monthly salary sheet.")
        onResult(false, "Permission denied")
        return
    }
    val isLocked = state.isSalaryMonthLocked(schoolDocId, yearMonth)
    val lockErr = StaffSalaryEngine.validatePostLockChange(isLocked, lockOverrideReason)
    if (lockErr != null) {
        emitMessage(lockErr)
        onResult(false, lockErr)
        return
    }

    val profiles = state.staffSalaryProfiles.filter { it.active && it.appliesToSchool(schoolDocId) }
    if (profiles.isEmpty()) {
        emitMessage("No staff salary profiles found. Add or sync staff in the Staff Setup tab first.")
        onResult(false, "No staff profiles")
        return
    }

    val ruleConfig = state.salaryRuleConfigForSchool(schoolDocId)
    val periodStructure = state.periodStructureForSchool(schoolDocId)
    val existingByStaffId = state.salaryMonthlyRecords
        .filter { it.schoolId == schoolDocId && it.yearMonth == yearMonth }
        .associateBy { it.staffId }
    val activeAdvances = state.staffSalaryAdvances.filter { it.schoolId == schoolDocId }

    val computedRecords = profiles.map { prof ->
        val existing = existingByStaffId[prof.staffId]
        val pulledCounts = StaffSalaryEngine.pullMonthlyAttendanceCounts(
            schoolId = schoolDocId,
            yearMonth = yearMonth,
            staffId = prof.staffId,
            staffName = prof.staffName,
            periodStructure = periodStructure,
            attendanceRecords = state.effectivePhase12TeacherAttendance,
            approvedLeaves = state.effectivePhase12TeacherLeaves
        )
        // If non-teaching staff has no Phase 7 attendance rows marked yet, default presentDays to workingDays
        // unless existing record already has attendance edited
        val totalMarked = pulledCounts.presentDays + pulledCounts.absentDays + pulledCounts.onLeaveDays + pulledCounts.halfDays
        val effectiveCounts = when {
            totalMarked > 0 -> pulledCounts
            existing != null -> PulledStaffAttendanceCounts(
                workingDaysInMonth = pulledCounts.workingDaysInMonth,
                presentDays = existing.presentDays,
                absentDays = existing.absentDays,
                onLeaveDays = existing.onLeaveDays,
                halfDays = existing.halfDays,
                lateDays = existing.lateDays
            )
            !prof.isTeachingStaff -> pulledCounts.copy(presentDays = pulledCounts.workingDaysInMonth)
            else -> pulledCounts
        }

        StaffSalaryEngine.computeMonthlySalary(
            schoolId = schoolDocId,
            yearMonth = yearMonth,
            profile = prof,
            ruleConfig = ruleConfig,
            attendanceCounts = effectiveCounts,
            activeAdvances = activeAdvances,
            overrideAdvanceDeductionRs = existing?.advanceDeductionRs,
            manualAdjustmentRs = existing?.manualAdjustmentRs ?: 0,
            adjustmentRemark = existing?.adjustmentRemark.orEmpty(),
            existingRecord = existing
        ).record
    }

    phase12Repo.saveSalaryMonthlyRecordsBatch(
        orgId = orgId,
        records = computedRecords,
        isMonthLocked = isLocked,
        lockOverrideReason = lockOverrideReason,
        auditAction = "SHEET_COMPUTED"
    )
    emitMessage("Computed monthly salary sheet for ${computedRecords.size} staff member(s) ($yearMonth).")
    onResult(true, "Computed ${computedRecords.size} records")
}

/**
 * Updates a single staff member's monthly salary sheet (attendance counts, advance deduction override,
 * and hand adjustment with mandatory remark).
 */
fun SchoolViewModel.updateSingleStaffMonthlySalary(
    existingRecord: SalaryMonthlyRecord,
    presentDays: Int,
    absentDays: Int,
    onLeaveDays: Int,
    halfDays: Int,
    lateDays: Int,
    advanceDeductionRs: Int,
    manualAdjustmentRs: Int,
    adjustmentRemark: String,
    lockOverrideReason: String = "",
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = existingRecord.schoolId.ifBlank { state.activeSchoolDocId }
    if (!Phase12AccessEvaluator.canEditSalary(state.accessProfile, schoolDocId)) {
        emitMessage("You do not have permission to edit salary records.")
        onResult(false, "Permission denied")
        return
    }
    val adjErr = StaffSalaryEngine.validateManualAdjustment(manualAdjustmentRs, adjustmentRemark)
    if (adjErr != null) {
        emitMessage(adjErr)
        onResult(false, adjErr)
        return
    }
    val isLocked = state.isSalaryMonthLocked(schoolDocId, existingRecord.yearMonth)
    val lockErr = StaffSalaryEngine.validatePostLockChange(isLocked, lockOverrideReason)
    if (lockErr != null) {
        emitMessage(lockErr)
        onResult(false, lockErr)
        return
    }

    val ruleConfig = state.salaryRuleConfigForSchool(schoolDocId)
    val profile = state.staffSalaryProfiles.firstOrNull { it.staffId == existingRecord.staffId }
        ?: StaffSalaryProfileRecord(
            id = StaffSalaryProfileRecord.docIdForStaff(existingRecord.staffId),
            staffId = existingRecord.staffId,
            staffName = existingRecord.staffName,
            designation = existingRecord.designation,
            linkedMemberUid = existingRecord.staffUid,
            joiningDateIso = existingRecord.joiningDateIso,
            primarySchoolId = schoolDocId,
            basicMonthlySalaryRs = existingRecord.basicSalaryRs,
            allowances = existingRecord.allowances,
            fixedDeductions = existingRecord.fixedDeductions
        )

    val counts = PulledStaffAttendanceCounts(
        workingDaysInMonth = existingRecord.workingDaysInMonth.coerceAtLeast(1),
        presentDays = presentDays.coerceAtLeast(0),
        absentDays = absentDays.coerceAtLeast(0),
        onLeaveDays = onLeaveDays.coerceAtLeast(0),
        halfDays = halfDays.coerceAtLeast(0),
        lateDays = lateDays.coerceAtLeast(0)
    )
    val activeAdvances = state.staffSalaryAdvances.filter { it.schoolId == schoolDocId }

    val computed = StaffSalaryEngine.computeMonthlySalary(
        schoolId = schoolDocId,
        yearMonth = existingRecord.yearMonth,
        profile = profile,
        ruleConfig = ruleConfig,
        attendanceCounts = counts,
        activeAdvances = activeAdvances,
        overrideAdvanceDeductionRs = advanceDeductionRs.coerceAtLeast(0),
        manualAdjustmentRs = manualAdjustmentRs,
        adjustmentRemark = adjustmentRemark,
        existingRecord = existingRecord
    ).record

    val auditAction = if (manualAdjustmentRs != 0) "MANUAL_ADJUSTMENT" else "SHEET_SAVED"
    phase12Repo.saveSalaryMonthlyRecordsBatch(
        orgId = orgId,
        records = listOf(computed),
        isMonthLocked = isLocked,
        lockOverrideReason = lockOverrideReason,
        auditAction = auditAction
    )
    emitMessage("Updated salary for ${computed.staffName} (Net ₹${computed.netPayableRs}).")
    onResult(true, "Saved")
}

fun SchoolViewModel.markStaffSalaryPaid(
    record: SalaryMonthlyRecord,
    paidDateIso: String,
    paymentMode: PaymentMode,
    paymentReference: String,
    lockOverrideReason: String = "",
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = record.schoolId.ifBlank { state.activeSchoolDocId }
    if (!Phase12AccessEvaluator.canEditSalary(state.accessProfile, schoolDocId)) {
        emitMessage("You do not have permission to mark salary paid.")
        onResult(false, "Permission denied")
        return
    }
    if (paidDateIso.trim().isEmpty()) {
        emitMessage("Please enter a valid payment date.")
        onResult(false, "Payment date required")
        return
    }
    val isLocked = state.isSalaryMonthLocked(schoolDocId, record.yearMonth)
    val lockErr = StaffSalaryEngine.validatePostLockChange(isLocked, lockOverrideReason)
    if (lockErr != null) {
        emitMessage(lockErr)
        onResult(false, lockErr)
        return
    }
    val activeAdvances = state.staffSalaryAdvances.filter { it.schoolId == schoolDocId }
    phase12Repo.markSalaryRecordPaid(
        orgId = orgId,
        record = record,
        paidDateIso = paidDateIso,
        paymentMode = paymentMode,
        paymentReference = paymentReference,
        activeAdvances = activeAdvances,
        isMonthLocked = isLocked,
        lockOverrideReason = lockOverrideReason
    )
    emitMessage("Marked ${record.staffName}'s salary (₹${record.netPayableRs}) as Paid. Added automatically to Cash Book.")
    onResult(true, "Paid")
}

fun SchoolViewModel.issueStaffSalaryAdvance(
    staffId: String,
    staffName: String,
    staffUid: String,
    advanceDateIso: String,
    principalAmountRs: Int,
    monthlyInstalmentRs: Int,
    paymentMode: PaymentMode,
    note: String,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (!Phase12AccessEvaluator.canEditSalary(state.accessProfile, schoolDocId)) {
        emitMessage("You do not have permission to issue salary advances.")
        onResult(false, "Permission denied")
        return
    }
    if (staffId.isBlank() || staffName.isBlank()) {
        emitMessage("Please select a staff member.")
        onResult(false, "Staff required")
        return
    }
    if (principalAmountRs <= 0) {
        emitMessage("Advance amount must be greater than ₹0.")
        onResult(false, "Invalid amount")
        return
    }
    val advance = StaffSalaryAdvanceRecord(
        schoolId = schoolDocId,
        staffId = staffId,
        staffName = staffName,
        staffUid = staffUid,
        advanceDateIso = advanceDateIso.trim(),
        principalAmountRs = principalAmountRs,
        monthlyInstalmentRs = monthlyInstalmentRs.coerceIn(0, principalAmountRs),
        repaidAmountRs = 0,
        paymentMode = paymentMode,
        note = note.trim()
    )
    phase12Repo.createSalaryAdvance(orgId, advance)
    emitMessage("Issued advance of ₹$principalAmountRs to $staffName.")
    onResult(true, "Advance issued")
}

fun SchoolViewModel.recordStaffAdvanceManualRepayment(
    advance: StaffSalaryAdvanceRecord,
    repaymentAmountRs: Int,
    dateIso: String,
    paymentMode: PaymentMode,
    note: String,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = advance.schoolId.ifBlank { state.activeSchoolDocId }
    if (!Phase12AccessEvaluator.canEditSalary(state.accessProfile, schoolDocId)) {
        emitMessage("You do not have permission to record advance repayments.")
        onResult(false, "Permission denied")
        return
    }
    if (repaymentAmountRs <= 0 || repaymentAmountRs > advance.remainingBalanceRs) {
        emitMessage("Repayment amount must be between ₹1 and ₹${advance.remainingBalanceRs}.")
        onResult(false, "Invalid amount")
        return
    }
    phase12Repo.recordManualAdvanceRepayment(
        orgId = orgId,
        advance = advance,
        repaymentAmountRs = repaymentAmountRs,
        dateIso = dateIso,
        paymentMode = paymentMode,
        note = note
    )
    emitMessage("Recorded repayment of ₹$repaymentAmountRs for ${advance.staffName}.")
    onResult(true, "Repayment recorded")
}

fun SchoolViewModel.setFinishedSalaryMonthLock(
    yearMonth: String,
    locked: Boolean,
    note: String,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (!Phase12AccessEvaluator.canLockOrUnlockSalaryMonth(state.accessProfile)) {
        emitMessage("Only the Organisation Owner can lock or unlock a salary month.")
        onResult(false, "Owner required")
        return
    }
    if (!locked && note.trim().isEmpty()) {
        emitMessage("Please enter a reason to unlock this salary month.")
        onResult(false, "Reason required")
        return
    }
    phase12Repo.setSalaryMonthLock(
        orgId = orgId,
        schoolId = schoolDocId,
        yearMonth = yearMonth,
        locked = locked,
        note = note
    )
    emitMessage(
        if (locked) "Locked salary month $yearMonth."
        else "Unlocked salary month $yearMonth (logged in audit trail)."
    )
    onResult(true, if (locked) "Locked" else "Unlocked")
}

fun SchoolViewModel.generateSalarySlipPdfAndPreview(
    record: SalaryMonthlyRecord,
    pageSize: SalarySlipPageSize = SalarySlipPageSize.A4
) {
    val state = uiState.value
    val schoolDocId = record.schoolId.ifBlank { state.activeSchoolDocId }
    val schoolRecord = state.schoolRecordsByDocId[schoolDocId]
    val schoolEntity = schoolRecord?.toSchoolEntity() ?: state.activeSchool
    val ruleConfig = state.salaryRuleConfigForSchool(schoolDocId)
    val remainingAdvance = state.staffSalaryAdvances
        .filter { it.schoolId == schoolDocId && it.staffId == record.staffId }
        .sumOf { it.remainingBalanceRs }

    val bundle = SalarySlipPdfBundle(
        school = schoolEntity,
        record = record,
        ruleConfig = ruleConfig,
        remainingAdvanceBalanceRs = remainingAdvance,
        pageSize = pageSize,
        schoolLogoBase64 = schoolEntity.logoBase64
    )
    launchPhase9PdfJob(
        title = "${record.staffName} • Salary Slip (${record.yearMonth}, ${pageSize.wireValue})",
        sizeLabel = pageSize.label
    ) {
        Phase12PdfGenerator.generateSalarySlipPdf(appContext, bundle)
    }
}

fun SchoolViewModel.generateSalaryMonthSummaryPdfAndPreview(
    yearMonth: String
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    val schoolRecord = state.schoolRecordsByDocId[schoolDocId]
    val schoolEntity = schoolRecord?.toSchoolEntity() ?: state.activeSchool
    val ruleConfig = state.salaryRuleConfigForSchool(schoolDocId)
    val records = state.salaryMonthlyRecords
        .filter { it.schoolId == schoolDocId && it.yearMonth == yearMonth }
    val isLocked = state.isSalaryMonthLocked(schoolDocId, yearMonth)

    val bundle = SalaryMonthSummaryPdfBundle(
        school = schoolEntity,
        yearMonth = yearMonth,
        records = records,
        ruleConfig = ruleConfig,
        isLocked = isLocked,
        schoolLogoBase64 = schoolEntity.logoBase64
    )
    launchPhase9PdfJob(
        title = "Monthly Salary Summary ($yearMonth)",
        sizeLabel = "A4 Portrait"
    ) {
        Phase12PdfGenerator.generateSalaryMonthSummaryPdf(appContext, bundle)
    }
}

fun SchoolViewModel.exportSalaryMonthCsvFile(
    yearMonth: String,
    onReady: (File) -> Unit
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    val schoolName = state.activeSchool.name.ifBlank { "School" }
    val records = state.salaryMonthlyRecords
        .filter { it.schoolId == schoolDocId && it.yearMonth == yearMonth }
    viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            val csvText = StaffSalaryEngine.buildMonthlySalaryCsv(schoolName, yearMonth, records)
            val dir = File(appContext.cacheDir, "reports").apply { mkdirs() }
            val out = File(dir, "Salary_Sheet_${yearMonth}.csv")
            out.writeText(csvText)
            out
        }
        onReady(file)
    }
}
