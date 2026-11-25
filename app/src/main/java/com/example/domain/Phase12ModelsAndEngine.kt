package com.example.domain

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Phase 12: Staff Salary Setup, Monthly Salary Sheet (linked to Phase 7 Teacher Attendance),
 * Per-School Attendance Deduction Rules, Advances & Loans, A4/A5 Salary Slips,
 * Owner Month Locking with Post-Lock Audit Trail, and Phase 10 Cash Book Auto-Expense Integration.
 *
 * Pure Kotlin domain models and engine with zero Android/Firebase dependencies for direct JUnit testing.
 */
object Phase12AccessEvaluator {

    /**
     * Salary data is sensitive:
     * - By default ONLY the Owner has access.
     * - Admins get access ONLY if the Owner explicitly grants `salary: view` or `salary: edit`.
     * - Missing key = no access (`AccessLevel.NONE`).
     */
    fun canViewSalary(member: MemberAccessProfile?, schoolDocId: String = ""): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (schoolDocId.isNotBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) {
            return false
        }
        return OrgAccessEvaluator.canViewModule(member, AppModule.SALARY)
    }

    fun canEditSalary(member: MemberAccessProfile?, schoolDocId: String = ""): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (schoolDocId.isNotBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) {
            return false
        }
        return OrgAccessEvaluator.canEditModule(member, AppModule.SALARY)
    }

    /**
     * Only the Owner can grant or revoke the "Salary" access module on any member (including Admins)
     * or toggle teacher self-slip visibility.
     */
    fun canGrantSalaryPermission(actor: MemberAccessProfile?): Boolean =
        OrgAccessEvaluator.isOwner(actor)

    /**
     * A finished month can be locked (or unlocked) only by the Owner.
     */
    fun canLockOrUnlockSalaryMonth(actor: MemberAccessProfile?): Boolean =
        OrgAccessEvaluator.isOwner(actor)

    /**
     * Teachers (and linked staff members) can view ONLY their own salary slips
     * if and only if the Owner has enabled `allowTeachersToViewOwnSlips` for that school.
     */
    fun canTeacherViewOwnSalarySlip(
        member: MemberAccessProfile?,
        slip: SalaryMonthlyRecord,
        ruleConfig: SalaryRuleConfig?
    ): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        val m = member!!
        if (canViewSalary(m, slip.schoolId)) return true
        val ownerEnabled = (ruleConfig?.allowTeachersToViewOwnSlips == true) && slip.teacherSelfViewEnabled
        return ownerEnabled &&
            m.uid.isNotBlank() &&
            slip.staffUid == m.uid &&
            OrgAccessEvaluator.canAccessSchool(m, slip.schoolId)
    }
}

data class SalaryComponentItem(
    val id: String = "",
    val name: String = "",
    val amountRs: Int = 0
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id.ifBlank { name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_") },
        "name" to name.trim(),
        "amountRs" to amountRs.coerceAtLeast(0)
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): SalaryComponentItem? {
            if (raw == null) return null
            val name = (raw["name"] as? String)?.trim().orEmpty()
            if (name.isBlank()) return null
            val id = (raw["id"] as? String)?.trim().orEmpty()
                .ifBlank { name.lowercase().replace(Regex("[^a-z0-9]+"), "_") }
            val amount = (raw["amountRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            return SalaryComponentItem(
                id = id,
                name = name,
                amountRs = amount
            )
        }
    }
}

enum class SalaryPerDayBasis(
    val wireValue: String,
    val englishLabel: String,
    val hindiLabel: String
) {
    FIXED_30_DAYS("FIXED_30_DAYS", "Basic ÷ 30 Days", "बेसिक ÷ 30 दिन"),
    WORKING_DAYS_IN_MONTH("WORKING_DAYS_IN_MONTH", "Basic ÷ Working Days in Month", "बेसिक ÷ कार्य दिवस");

    companion object {
        fun fromRaw(raw: String?): SalaryPerDayBasis =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: FIXED_30_DAYS
    }
}

/**
 * Per-school salary rule configuration stored at:
 * `organisations/{orgId}/salaryRuleConfigs/{docId}` (docId = `salcfg_<schoolId>`)
 */
data class SalaryRuleConfig(
    val id: String = "",
    val schoolId: String = "",
    val perDayBasis: SalaryPerDayBasis = SalaryPerDayBasis.FIXED_30_DAYS,
    val freeLeavesPerMonth: Int = 1,
    val halfDayAbsenceWeight: Double = 0.5,
    val halfDayConsumesFreeLeave: Boolean = true,
    val lateDaysPerHalfDayPenalty: Int = 3,
    val fixedPerAbsentDayDeductionRs: Int = 0,
    val defaultAllowances: List<SalaryComponentItem> = DEFAULT_ALLOWANCE_TEMPLATES,
    val defaultDeductions: List<SalaryComponentItem> = DEFAULT_DEDUCTION_TEMPLATES,
    val allowTeachersToViewOwnSlips: Boolean = false,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L
) {
    fun ruleSummaryText(isHindi: Boolean): String {
        val basisText = if (fixedPerAbsentDayDeductionRs > 0) {
            if (isHindi) "प्रति अनुपस्थित दिन ₹$fixedPerAbsentDayDeductionRs कटौती"
            else "Fixed ₹$fixedPerAbsentDayDeductionRs per unpaid absent day"
        } else {
            if (isHindi) "प्रति दिन दर: ${perDayBasis.hindiLabel}"
            else "Per-day deduction: ${perDayBasis.englishLabel}"
        }
        val freeLeaveText = if (isHindi) {
            "मुफ्त छुट्टी: $freeLeavesPerMonth/माह"
        } else {
            "Free leaves: $freeLeavesPerMonth/month"
        }
        val halfDayText = if (isHindi) {
            "हाफ-डे = $halfDayAbsenceWeight दिन" +
                if (halfDayConsumesFreeLeave) " (मुफ्त छुट्टी में समायोज्य)" else ""
        } else {
            "Half-day = $halfDayAbsenceWeight day" +
                if (halfDayConsumesFreeLeave) " (uses free leave first)" else ""
        }
        val lateText = if (lateDaysPerHalfDayPenalty > 0) {
            if (isHindi) "हर $lateDaysPerHalfDayPenalty देरी (Late) = 0.5 दिन कटौती"
            else "Every $lateDaysPerHalfDayPenalty late days = 0.5 day deduction"
        } else {
            if (isHindi) "देरी (Late) पर कोई कटौती नहीं"
            else "No late-day deduction"
        }
        return "$freeLeaveText • $basisText • $halfDayText • $lateText"
    }

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "perDayBasis" to perDayBasis.wireValue,
        "freeLeavesPerMonth" to freeLeavesPerMonth.coerceIn(0, 31),
        "halfDayAbsenceWeight" to halfDayAbsenceWeight.coerceIn(0.0, 1.0),
        "halfDayConsumesFreeLeave" to halfDayConsumesFreeLeave,
        "lateDaysPerHalfDayPenalty" to lateDaysPerHalfDayPenalty.coerceIn(0, 31),
        "fixedPerAbsentDayDeductionRs" to fixedPerAbsentDayDeductionRs.coerceAtLeast(0),
        "defaultAllowances" to defaultAllowances.map { it.toFirestoreMap() },
        "defaultDeductions" to defaultDeductions.map { it.toFirestoreMap() },
        "allowTeachersToViewOwnSlips" to allowTeachersToViewOwnSlips,
        "updatedByUid" to updatedByUid,
        "updatedByName" to updatedByName,
        "clientEditedAtMs" to clientEditedAtMs
    )

    companion object {
        val DEFAULT_ALLOWANCE_TEMPLATES: List<SalaryComponentItem> = listOf(
            SalaryComponentItem("hra", "HRA / मकान भत्ता", 0),
            SalaryComponentItem("transport", "Transport / वाहन भत्ता", 0),
            SalaryComponentItem("medical", "Medical / चिकित्सा भत्ता", 0),
            SalaryComponentItem("special", "Special Allowance / विशेष भत्ता", 0)
        )

        val DEFAULT_DEDUCTION_TEMPLATES: List<SalaryComponentItem> = listOf(
            SalaryComponentItem("pf", "PF / भविष्य निधि", 0),
            SalaryComponentItem("pt", "Professional Tax / वृत्ति कर", 0),
            SalaryComponentItem("esi", "ESI / बीमा", 0),
            SalaryComponentItem("other_ded", "Other Deduction / अन्य कटौती", 0)
        )

        fun defaultdocId(schoolId: String): String = "salcfg_${schoolId.trim()}"

        fun fromMap(id: String, raw: Map<*, *>): SalaryRuleConfig {
            val allowances = (raw["defaultAllowances"] as? List<*>)
                ?.mapNotNull { SalaryComponentItem.fromMap(it as? Map<*, *>) }
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_ALLOWANCE_TEMPLATES
            val deductions = (raw["defaultDeductions"] as? List<*>)
                ?.mapNotNull { SalaryComponentItem.fromMap(it as? Map<*, *>) }
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_DEDUCTION_TEMPLATES
            return SalaryRuleConfig(
                id = id,
                schoolId = (raw["schoolId"] as? String)?.trim().orEmpty(),
                perDayBasis = SalaryPerDayBasis.fromRaw(raw["perDayBasis"] as? String),
                freeLeavesPerMonth = (raw["freeLeavesPerMonth"] as? Number)?.toInt()?.coerceIn(0, 31) ?: 1,
                halfDayAbsenceWeight = (raw["halfDayAbsenceWeight"] as? Number)?.toDouble()?.coerceIn(0.0, 1.0) ?: 0.5,
                halfDayConsumesFreeLeave = (raw["halfDayConsumesFreeLeave"] as? Boolean) ?: true,
                lateDaysPerHalfDayPenalty = (raw["lateDaysPerHalfDayPenalty"] as? Number)?.toInt()?.coerceIn(0, 31) ?: 3,
                fixedPerAbsentDayDeductionRs = (raw["fixedPerAbsentDayDeductionRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                defaultAllowances = allowances,
                defaultDeductions = deductions,
                allowTeachersToViewOwnSlips = (raw["allowTeachersToViewOwnSlips"] as? Boolean) ?: false,
                updatedByUid = (raw["updatedByUid"] as? String).orEmpty(),
                updatedByName = (raw["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (raw["clientEditedAtMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/**
 * Staff Salary Setup per staff member (both Teachers and Non-Teaching Staff).
 * Stored at `organisations/{orgId}/staffSalaryProfiles/{docId}` (docId = `salprof_<staffId>`).
 */
data class StaffSalaryProfileRecord(
    val id: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val designation: String = "Teacher",
    val isTeachingStaff: Boolean = true,
    val linkedMemberUid: String = "",
    val joiningDateIso: String = "",
    val allSchools: Boolean = false,
    val schoolIds: List<String> = emptyList(),
    val primarySchoolId: String = "",
    val basicMonthlySalaryRs: Int = 0,
    val allowances: List<SalaryComponentItem> = emptyList(),
    val fixedDeductions: List<SalaryComponentItem> = emptyList(),
    val active: Boolean = true,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L
) {
    val totalAllowancesRs: Int
        get() = allowances.sumOf { it.amountRs.coerceAtLeast(0) }

    val totalFixedDeductionsRs: Int
        get() = fixedDeductions.sumOf { it.amountRs.coerceAtLeast(0) }

    val grossMonthlySalaryRs: Int
        get() = basicMonthlySalaryRs.coerceAtLeast(0) + totalAllowancesRs

    fun appliesToSchool(schoolDocId: String): Boolean =
        allSchools || primarySchoolId == schoolDocId || schoolDocId in schoolIds

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "staffId" to staffId.trim(),
        "staffName" to staffName.trim(),
        "designation" to designation.trim().ifBlank { if (isTeachingStaff) "Teacher" else "Staff" },
        "isTeachingStaff" to isTeachingStaff,
        "linkedMemberUid" to linkedMemberUid.trim(),
        "joiningDateIso" to joiningDateIso.trim(),
        "allSchools" to allSchools,
        "schoolIds" to schoolIds.distinct(),
        "primarySchoolId" to primarySchoolId.trim(),
        "basicMonthlySalaryRs" to basicMonthlySalaryRs.coerceAtLeast(0),
        "allowances" to allowances.map { it.toFirestoreMap() },
        "fixedDeductions" to fixedDeductions.map { it.toFirestoreMap() },
        "active" to active,
        "updatedByUid" to updatedByUid,
        "updatedByName" to updatedByName,
        "clientEditedAtMs" to clientEditedAtMs
    )

    companion object {
        fun docIdForStaff(staffId: String): String = "salprof_${staffId.trim()}"

        fun fromMap(id: String, raw: Map<*, *>): StaffSalaryProfileRecord {
            val allowances = (raw["allowances"] as? List<*>)
                ?.mapNotNull { SalaryComponentItem.fromMap(it as? Map<*, *>) }
                ?: emptyList()
            val deductions = (raw["fixedDeductions"] as? List<*>)
                ?.mapNotNull { SalaryComponentItem.fromMap(it as? Map<*, *>) }
                ?: emptyList()
            val schoolIds = (raw["schoolIds"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                ?: emptyList()
            return StaffSalaryProfileRecord(
                id = id,
                staffId = (raw["staffId"] as? String)?.trim().orEmpty(),
                staffName = (raw["staffName"] as? String)?.trim().orEmpty(),
                designation = (raw["designation"] as? String)?.trim().orEmpty().ifBlank { "Teacher" },
                isTeachingStaff = (raw["isTeachingStaff"] as? Boolean) ?: true,
                linkedMemberUid = (raw["linkedMemberUid"] as? String)?.trim().orEmpty(),
                joiningDateIso = (raw["joiningDateIso"] as? String)?.trim().orEmpty(),
                allSchools = (raw["allSchools"] as? Boolean) ?: false,
                schoolIds = schoolIds,
                primarySchoolId = (raw["primarySchoolId"] as? String)?.trim().orEmpty(),
                basicMonthlySalaryRs = (raw["basicMonthlySalaryRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                allowances = allowances,
                fixedDeductions = deductions,
                active = (raw["active"] as? Boolean) ?: true,
                updatedByUid = (raw["updatedByUid"] as? String).orEmpty(),
                updatedByName = (raw["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (raw["clientEditedAtMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

enum class SalaryAdvanceStatus(
    val wireValue: String,
    val englishLabel: String,
    val hindiLabel: String
) {
    ACTIVE("ACTIVE", "Active", "सक्रिय"),
    CLOSED("CLOSED", "Closed / Repaid", "पूर्ण भुगतान");

    companion object {
        fun fromRaw(raw: String?): SalaryAdvanceStatus =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}

data class SalaryAdvanceRepaymentEntry(
    val id: String = "",
    val dateIso: String = "",
    val yearMonth: String = "",
    val amountRs: Int = 0,
    val mode: String = "SALARY_DEDUCTION",
    val isAutoFromSalarySlip: Boolean = true,
    val note: String = ""
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "dateIso" to dateIso,
        "yearMonth" to yearMonth,
        "amountRs" to amountRs.coerceAtLeast(0),
        "mode" to mode,
        "isAutoFromSalarySlip" to isAutoFromSalarySlip,
        "note" to note
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): SalaryAdvanceRepaymentEntry? {
            if (raw == null) return null
            val amount = (raw["amountRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: return null
            return SalaryAdvanceRepaymentEntry(
                id = (raw["id"] as? String).orEmpty(),
                dateIso = (raw["dateIso"] as? String).orEmpty(),
                yearMonth = (raw["yearMonth"] as? String).orEmpty(),
                amountRs = amount,
                mode = (raw["mode"] as? String).orEmpty().ifBlank { "SALARY_DEDUCTION" },
                isAutoFromSalarySlip = (raw["isAutoFromSalarySlip"] as? Boolean) ?: true,
                note = (raw["note"] as? String).orEmpty()
            )
        }
    }
}

/**
 * Staff Salary Advance / Loan record stored at:
 * `organisations/{orgId}/staffSalaryAdvances/{docId}`
 */
data class StaffSalaryAdvanceRecord(
    val id: String = "",
    val schoolId: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val staffUid: String = "",
    val advanceDateIso: String = "",
    val principalAmountRs: Int = 0,
    val monthlyInstalmentRs: Int = 0,
    val repaidAmountRs: Int = 0,
    val repayments: List<SalaryAdvanceRepaymentEntry> = emptyList(),
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val note: String = "",
    val status: SalaryAdvanceStatus = SalaryAdvanceStatus.ACTIVE,
    val createdByUid: String = "",
    val createdByName: String = "",
    val updatedByUid: String = "",
    val clientEditedAtMs: Long = 0L
) {
    val remainingBalanceRs: Int
        get() = (principalAmountRs - repaidAmountRs).coerceAtLeast(0)

    val suggestedCurrentMonthDeductionRs: Int
        get() = if (status != SalaryAdvanceStatus.ACTIVE || remainingBalanceRs <= 0) {
            0
        } else if (monthlyInstalmentRs > 0) {
            min(monthlyInstalmentRs, remainingBalanceRs)
        } else {
            remainingBalanceRs
        }

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "staffId" to staffId.trim(),
        "staffName" to staffName.trim(),
        "staffUid" to staffUid.trim(),
        "advanceDateIso" to advanceDateIso.trim(),
        "principalAmountRs" to principalAmountRs.coerceAtLeast(1),
        "monthlyInstalmentRs" to monthlyInstalmentRs.coerceAtLeast(0),
        "repaidAmountRs" to repaidAmountRs.coerceIn(0, principalAmountRs.coerceAtLeast(1)),
        "repayments" to repayments.map { it.toFirestoreMap() },
        "paymentMode" to paymentMode.wireValue,
        "note" to note.trim(),
        "status" to status.wireValue,
        "createdByUid" to createdByUid,
        "createdByName" to createdByName,
        "updatedByUid" to updatedByUid,
        "clientEditedAtMs" to clientEditedAtMs
    )

    companion object {
        fun fromMap(id: String, raw: Map<*, *>): StaffSalaryAdvanceRecord {
            val repayments = (raw["repayments"] as? List<*>)
                ?.mapNotNull { SalaryAdvanceRepaymentEntry.fromMap(it as? Map<*, *>) }
                ?: emptyList()
            val principal = (raw["principalAmountRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            val repaid = (raw["repaidAmountRs"] as? Number)?.toInt()?.coerceIn(0, principal.coerceAtLeast(0)) ?: 0
            return StaffSalaryAdvanceRecord(
                id = id,
                schoolId = (raw["schoolId"] as? String)?.trim().orEmpty(),
                staffId = (raw["staffId"] as? String)?.trim().orEmpty(),
                staffName = (raw["staffName"] as? String)?.trim().orEmpty(),
                staffUid = (raw["staffUid"] as? String)?.trim().orEmpty(),
                advanceDateIso = (raw["advanceDateIso"] as? String)?.trim().orEmpty(),
                principalAmountRs = principal,
                monthlyInstalmentRs = (raw["monthlyInstalmentRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                repaidAmountRs = repaid,
                repayments = repayments,
                paymentMode = PaymentMode.fromRaw(raw["paymentMode"] as? String),
                note = (raw["note"] as? String)?.trim().orEmpty(),
                status = SalaryAdvanceStatus.fromRaw(raw["status"] as? String),
                createdByUid = (raw["createdByUid"] as? String).orEmpty(),
                createdByName = (raw["createdByName"] as? String).orEmpty(),
                updatedByUid = (raw["updatedByUid"] as? String).orEmpty(),
                clientEditedAtMs = (raw["clientEditedAtMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

enum class SalaryPaymentStatus(
    val wireValue: String,
    val englishLabel: String,
    val hindiLabel: String
) {
    PENDING("PENDING", "Pending", "बकाया"),
    PAID("PAID", "Paid", "भुगतान पूर्ण");

    companion object {
        fun fromRaw(raw: String?): SalaryPaymentStatus =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

enum class SalarySlipPageSize(val wireValue: String, val label: String) {
    A4("A4", "A4 Full Page"),
    A5("A5", "A5 Compact Slip")
}

/**
 * Monthly salary sheet record per staff member stored at:
 * `organisations/{orgId}/salaryMonthlyRecords/{docId}`
 * (docId = `salslip_<schoolId>_<yearMonth>_<staffId>`)
 */
data class SalaryMonthlyRecord(
    val id: String = "",
    val schoolId: String = "",
    val yearMonth: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val designation: String = "Teacher",
    val staffUid: String = "",
    val joiningDateIso: String = "",
    val workingDaysInMonth: Int = 26,
    val presentDays: Int = 0,
    val absentDays: Int = 0,
    val onLeaveDays: Int = 0,
    val halfDays: Int = 0,
    val lateDays: Int = 0,
    val freeLeavesApplied: Double = 0.0,
    val unpaidAbsenceDays: Double = 0.0,
    val perDayRateRs: Int = 0,
    val basicSalaryRs: Int = 0,
    val allowances: List<SalaryComponentItem> = emptyList(),
    val totalAllowancesRs: Int = 0,
    val grossEarningsRs: Int = 0,
    val attendanceDeductionRs: Int = 0,
    val fixedDeductions: List<SalaryComponentItem> = emptyList(),
    val totalFixedDeductionsRs: Int = 0,
    val advanceDeductionRs: Int = 0,
    val linkedAdvanceIds: List<String> = emptyList(),
    val manualAdjustmentRs: Int = 0,
    val adjustmentRemark: String = "",
    val netPayableRs: Int = 0,
    val status: SalaryPaymentStatus = SalaryPaymentStatus.PENDING,
    val paidDateIso: String = "",
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val paymentReference: String = "",
    val teacherSelfViewEnabled: Boolean = false,
    val lastLockOverrideReason: String = "",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L
) {
    val totalDeductionsBeforeManualRs: Int
        get() = attendanceDeductionRs + totalFixedDeductionsRs + advanceDeductionRs

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "yearMonth" to yearMonth.trim(),
        "staffId" to staffId.trim(),
        "staffName" to staffName.trim(),
        "designation" to designation.trim().ifBlank { "Teacher" },
        "staffUid" to staffUid.trim(),
        "joiningDateIso" to joiningDateIso.trim(),
        "workingDaysInMonth" to workingDaysInMonth.coerceIn(1, 31),
        "presentDays" to presentDays.coerceAtLeast(0),
        "absentDays" to absentDays.coerceAtLeast(0),
        "onLeaveDays" to onLeaveDays.coerceAtLeast(0),
        "halfDays" to halfDays.coerceAtLeast(0),
        "lateDays" to lateDays.coerceAtLeast(0),
        "freeLeavesApplied" to freeLeavesApplied,
        "unpaidAbsenceDays" to unpaidAbsenceDays,
        "perDayRateRs" to perDayRateRs.coerceAtLeast(0),
        "basicSalaryRs" to basicSalaryRs.coerceAtLeast(0),
        "allowances" to allowances.map { it.toFirestoreMap() },
        "totalAllowancesRs" to totalAllowancesRs.coerceAtLeast(0),
        "grossEarningsRs" to grossEarningsRs.coerceAtLeast(0),
        "attendanceDeductionRs" to attendanceDeductionRs.coerceAtLeast(0),
        "fixedDeductions" to fixedDeductions.map { it.toFirestoreMap() },
        "totalFixedDeductionsRs" to totalFixedDeductionsRs.coerceAtLeast(0),
        "advanceDeductionRs" to advanceDeductionRs.coerceAtLeast(0),
        "linkedAdvanceIds" to linkedAdvanceIds.distinct(),
        "manualAdjustmentRs" to manualAdjustmentRs,
        "adjustmentRemark" to adjustmentRemark.trim(),
        "netPayableRs" to netPayableRs.coerceAtLeast(0),
        "status" to status.wireValue,
        "paidDateIso" to paidDateIso.trim(),
        "paymentMode" to paymentMode.wireValue,
        "paymentReference" to paymentReference.trim(),
        "teacherSelfViewEnabled" to teacherSelfViewEnabled,
        "lastLockOverrideReason" to lastLockOverrideReason.trim(),
        "updatedByUid" to updatedByUid,
        "updatedByName" to updatedByName,
        "clientEditedAtMs" to clientEditedAtMs
    )

    companion object {
        fun docIdFor(schoolId: String, yearMonth: String, staffId: String): String =
            "salslip_${schoolId.trim()}_${yearMonth.trim()}_${staffId.trim()}"

        fun fromMap(id: String, raw: Map<*, *>): SalaryMonthlyRecord {
            val allowances = (raw["allowances"] as? List<*>)
                ?.mapNotNull { SalaryComponentItem.fromMap(it as? Map<*, *>) }
                ?: emptyList()
            val fixedDeductions = (raw["fixedDeductions"] as? List<*>)
                ?.mapNotNull { SalaryComponentItem.fromMap(it as? Map<*, *>) }
                ?: emptyList()
            val linkedAdvanceIds = (raw["linkedAdvanceIds"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                ?: emptyList()
            return SalaryMonthlyRecord(
                id = id,
                schoolId = (raw["schoolId"] as? String)?.trim().orEmpty(),
                yearMonth = (raw["yearMonth"] as? String)?.trim().orEmpty(),
                staffId = (raw["staffId"] as? String)?.trim().orEmpty(),
                staffName = (raw["staffName"] as? String)?.trim().orEmpty(),
                designation = (raw["designation"] as? String)?.trim().orEmpty().ifBlank { "Teacher" },
                staffUid = (raw["staffUid"] as? String)?.trim().orEmpty(),
                joiningDateIso = (raw["joiningDateIso"] as? String)?.trim().orEmpty(),
                workingDaysInMonth = (raw["workingDaysInMonth"] as? Number)?.toInt()?.coerceIn(1, 31) ?: 26,
                presentDays = (raw["presentDays"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                absentDays = (raw["absentDays"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                onLeaveDays = (raw["onLeaveDays"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                halfDays = (raw["halfDays"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                lateDays = (raw["lateDays"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                freeLeavesApplied = (raw["freeLeavesApplied"] as? Number)?.toDouble() ?: 0.0,
                unpaidAbsenceDays = (raw["unpaidAbsenceDays"] as? Number)?.toDouble() ?: 0.0,
                perDayRateRs = (raw["perDayRateRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                basicSalaryRs = (raw["basicSalaryRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                allowances = allowances,
                totalAllowancesRs = (raw["totalAllowancesRs"] as? Number)?.toInt()?.coerceAtLeast(0)
                    ?: allowances.sumOf { it.amountRs },
                grossEarningsRs = (raw["grossEarningsRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                attendanceDeductionRs = (raw["attendanceDeductionRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                fixedDeductions = fixedDeductions,
                totalFixedDeductionsRs = (raw["totalFixedDeductionsRs"] as? Number)?.toInt()?.coerceAtLeast(0)
                    ?: fixedDeductions.sumOf { it.amountRs },
                advanceDeductionRs = (raw["advanceDeductionRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                linkedAdvanceIds = linkedAdvanceIds,
                manualAdjustmentRs = (raw["manualAdjustmentRs"] as? Number)?.toInt() ?: 0,
                adjustmentRemark = (raw["adjustmentRemark"] as? String)?.trim().orEmpty(),
                netPayableRs = (raw["netPayableRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                status = SalaryPaymentStatus.fromRaw(raw["status"] as? String),
                paidDateIso = (raw["paidDateIso"] as? String)?.trim().orEmpty(),
                paymentMode = PaymentMode.fromRaw(raw["paymentMode"] as? String),
                paymentReference = (raw["paymentReference"] as? String)?.trim().orEmpty(),
                teacherSelfViewEnabled = (raw["teacherSelfViewEnabled"] as? Boolean) ?: false,
                lastLockOverrideReason = (raw["lastLockOverrideReason"] as? String)?.trim().orEmpty(),
                updatedByUid = (raw["updatedByUid"] as? String).orEmpty(),
                updatedByName = (raw["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (raw["clientEditedAtMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/**
 * Finished month lock record (Owner-only lock/unlock) stored at:
 * `organisations/{orgId}/salaryMonthLocks/{docId}` (docId = `sallock_<schoolId>_<yearMonth>`)
 */
data class SalaryMonthLockRecord(
    val id: String = "",
    val schoolId: String = "",
    val yearMonth: String = "",
    val locked: Boolean = false,
    val lockedByUid: String = "",
    val lockedByName: String = "",
    val lockedAtMs: Long = 0L,
    val note: String = "",
    val updatedByUid: String = "",
    val clientEditedAtMs: Long = 0L
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "yearMonth" to yearMonth.trim(),
        "locked" to locked,
        "lockedByUid" to lockedByUid,
        "lockedByName" to lockedByName,
        "lockedAtMs" to lockedAtMs,
        "note" to note.trim(),
        "updatedByUid" to updatedByUid,
        "clientEditedAtMs" to clientEditedAtMs
    )

    companion object {
        fun docIdFor(schoolId: String, yearMonth: String): String =
            "sallock_${schoolId.trim()}_${yearMonth.trim()}"

        fun fromMap(id: String, raw: Map<*, *>): SalaryMonthLockRecord = SalaryMonthLockRecord(
            id = id,
            schoolId = (raw["schoolId"] as? String)?.trim().orEmpty(),
            yearMonth = (raw["yearMonth"] as? String)?.trim().orEmpty(),
            locked = (raw["locked"] as? Boolean) ?: false,
            lockedByUid = (raw["lockedByUid"] as? String).orEmpty(),
            lockedByName = (raw["lockedByName"] as? String).orEmpty(),
            lockedAtMs = (raw["lockedAtMs"] as? Number)?.toLong() ?: 0L,
            note = (raw["note"] as? String)?.trim().orEmpty(),
            updatedByUid = (raw["updatedByUid"] as? String).orEmpty(),
            clientEditedAtMs = (raw["clientEditedAtMs"] as? Number)?.toLong() ?: 0L
        )
    }
}

/**
 * Immutable audit trail for salary adjustments, payments, month locks, and post-lock edits.
 * Stored at `organisations/{orgId}/salaryAuditLogs/{docId}`.
 */
data class SalaryAuditLogRecord(
    val id: String = "",
    val schoolId: String = "",
    val yearMonth: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val action: String = "",
    val reason: String = "",
    val summary: String = "",
    val amountRs: Int = 0,
    val actorUid: String = "",
    val actorName: String = "",
    val clientCreatedAtMs: Long = 0L
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "yearMonth" to yearMonth.trim(),
        "staffId" to staffId.trim(),
        "staffName" to staffName.trim(),
        "action" to action.trim(),
        "reason" to reason.trim(),
        "summary" to summary.trim(),
        "amountRs" to amountRs,
        "actorUid" to actorUid,
        "actorName" to actorName,
        "clientCreatedAtMs" to clientCreatedAtMs
    )

    companion object {
        fun fromMap(id: String, raw: Map<*, *>): SalaryAuditLogRecord = SalaryAuditLogRecord(
            id = id,
            schoolId = (raw["schoolId"] as? String)?.trim().orEmpty(),
            yearMonth = (raw["yearMonth"] as? String)?.trim().orEmpty(),
            staffId = (raw["staffId"] as? String)?.trim().orEmpty(),
            staffName = (raw["staffName"] as? String)?.trim().orEmpty(),
            action = (raw["action"] as? String)?.trim().orEmpty(),
            reason = (raw["reason"] as? String)?.trim().orEmpty(),
            summary = (raw["summary"] as? String)?.trim().orEmpty(),
            amountRs = (raw["amountRs"] as? Number)?.toInt() ?: 0,
            actorUid = (raw["actorUid"] as? String).orEmpty(),
            actorName = (raw["actorName"] as? String).orEmpty(),
            clientCreatedAtMs = (raw["clientCreatedAtMs"] as? Number)?.toLong() ?: 0L
        )
    }
}

data class PulledStaffAttendanceCounts(
    val workingDaysInMonth: Int,
    val presentDays: Int,
    val absentDays: Int,
    val onLeaveDays: Int,
    val halfDays: Int,
    val lateDays: Int
)

data class SalaryComputationResult(
    val record: SalaryMonthlyRecord,
    val breakdownSummary: String
)

object StaffSalaryEngine {

    /**
     * Pulls present, absent, leave, half-day, and late days for a staff member in [yearMonth]
     * from Phase 7 teacher attendance & approved leaves.
     * - Late days are automatically detected when an attendance record's note contains
     *   "late", "देरी", or "[LATE]" (case-insensitive), and can also be edited on the sheet.
     */
    fun pullMonthlyAttendanceCounts(
        schoolId: String,
        yearMonth: String,
        staffId: String,
        staffName: String,
        periodStructure: SchoolPeriodStructure,
        attendanceRecords: List<TeacherAttendanceRecord>,
        approvedLeaves: List<TeacherLeaveRecord>
    ): PulledStaffAttendanceCounts {
        val parts = yearMonth.trim().split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month1Based = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12) ?: 1

        val dummyTeacher = TeacherProfile(
            id = staffId,
            name = staffName,
            schoolIds = listOf(schoolId),
            active = true
        )
        val summary = TeacherAttendanceEngine.buildMonthlySummary(
            schoolId = schoolId,
            year = year,
            month1Based = month1Based,
            teachers = listOf(dummyTeacher),
            periodStructure = periodStructure,
            attendanceRecords = attendanceRecords,
            approvedLeaves = approvedLeaves
        ).firstOrNull()

        val lateFromNotes = attendanceRecords.count { rec ->
            rec.schoolId == schoolId &&
                rec.teacherId == staffId &&
                rec.date.startsWith(yearMonth) &&
                isLateAttendanceNote(rec.note)
        }

        return if (summary != null) {
            PulledStaffAttendanceCounts(
                workingDaysInMonth = summary.totalWorkingDays.coerceAtLeast(1),
                presentDays = summary.presentDays,
                absentDays = summary.absentDays,
                onLeaveDays = summary.onLeaveDays,
                halfDays = summary.halfDays,
                lateDays = lateFromNotes
            )
        } else {
            val allDates = TeacherAttendanceEngine.datesInMonth(year, month1Based)
            val workingDaysSet = periodStructure.workingDays.toSet()
            val workingDays = allDates.count { d ->
                val dow = Phase6DateUtils.dayOfWeekForDate(d)
                dow != null && dow in workingDaysSet
            }.coerceAtLeast(1)
            PulledStaffAttendanceCounts(
                workingDaysInMonth = workingDays,
                presentDays = 0,
                absentDays = 0,
                onLeaveDays = 0,
                halfDays = 0,
                lateDays = lateFromNotes
            )
        }
    }

    fun isLateAttendanceNote(note: String): Boolean {
        val trimmed = note.trim().lowercase()
        if (trimmed.isEmpty()) return false
        return trimmed.contains("late") || trimmed.contains("देरी") || trimmed.contains("विलंब")
    }

    /**
     * Computes a staff member's monthly salary from profile, attendance counts, school rule config,
     * active advances, and optional manual adjustment.
     */
    fun computeMonthlySalary(
        schoolId: String,
        yearMonth: String,
        profile: StaffSalaryProfileRecord,
        ruleConfig: SalaryRuleConfig,
        attendanceCounts: PulledStaffAttendanceCounts,
        activeAdvances: List<StaffSalaryAdvanceRecord>,
        overrideAdvanceDeductionRs: Int? = null,
        manualAdjustmentRs: Int = 0,
        adjustmentRemark: String = "",
        existingRecord: SalaryMonthlyRecord? = null,
        updatedByUid: String = "",
        updatedByName: String = "",
        nowMs: Long = System.currentTimeMillis()
    ): SalaryComputationResult {
        val basic = profile.basicMonthlySalaryRs.coerceAtLeast(0)
        val allowances = profile.allowances
        val totalAllowances = allowances.sumOf { it.amountRs.coerceAtLeast(0) }
        val gross = basic + totalAllowances

        val fixedDeductions = profile.fixedDeductions
        val totalFixedDeductions = fixedDeductions.sumOf { it.amountRs.coerceAtLeast(0) }

        val workingDays = attendanceCounts.workingDaysInMonth.coerceIn(1, 31)
        val perDayRateRs = if (ruleConfig.fixedPerAbsentDayDeductionRs > 0) {
            ruleConfig.fixedPerAbsentDayDeductionRs
        } else {
            when (ruleConfig.perDayBasis) {
                SalaryPerDayBasis.FIXED_30_DAYS -> (basic / 30.0).roundToInt()
                SalaryPerDayBasis.WORKING_DAYS_IN_MONTH -> (basic / workingDays.toDouble()).roundToInt()
            }
        }

        // Leave & half-day calculation with free leaves per month:
        val halfDayUnits = attendanceCounts.halfDays * ruleConfig.halfDayAbsenceWeight.coerceIn(0.0, 1.0)
        val leavePoolToCover = if (ruleConfig.halfDayConsumesFreeLeave) {
            attendanceCounts.onLeaveDays.toDouble() + halfDayUnits
        } else {
            attendanceCounts.onLeaveDays.toDouble()
        }
        val freeLeavesCap = ruleConfig.freeLeavesPerMonth.coerceAtLeast(0).toDouble()
        val freeLeavesApplied = min(freeLeavesCap, leavePoolToCover)
        val unpaidLeaveUnits = (leavePoolToCover - freeLeavesApplied).coerceAtLeast(0.0)
        val separateHalfDayUnpaid = if (ruleConfig.halfDayConsumesFreeLeave) 0.0 else halfDayUnits

        // Late penalty calculation (e.g. every 3 late days = 0.5 unpaid day)
        val latePenaltyDays = if (ruleConfig.lateDaysPerHalfDayPenalty > 0) {
            (attendanceCounts.lateDays / ruleConfig.lateDaysPerHalfDayPenalty) * 0.5
        } else {
            0.0
        }

        val unpaidAbsenceDays = attendanceCounts.absentDays.toDouble() +
            unpaidLeaveUnits +
            separateHalfDayUnpaid +
            latePenaltyDays

        val attendanceDeductionRs = (unpaidAbsenceDays * perDayRateRs)
            .roundToInt()
            .coerceIn(0, gross)

        val staffAdvances = activeAdvances.filter {
            it.staffId == profile.staffId &&
                it.status == SalaryAdvanceStatus.ACTIVE &&
                it.remainingBalanceRs > 0
        }.sortedBy { it.advanceDateIso }

        val totalRemainingAdvanceBalance = staffAdvances.sumOf { it.remainingBalanceRs }
        val suggestedAdvanceInstalment = staffAdvances.sumOf { it.suggestedCurrentMonthDeductionRs }
        val advanceDeductionRs = (overrideAdvanceDeductionRs ?: suggestedAdvanceInstalment)
            .coerceIn(0, totalRemainingAdvanceBalance)

        val netBeforeFloor = gross -
            attendanceDeductionRs -
            totalFixedDeductions -
            advanceDeductionRs +
            manualAdjustmentRs
        val netPayableRs = netBeforeFloor.coerceAtLeast(0)

        val docId = existingRecord?.id?.takeIf { it.isNotBlank() }
            ?: SalaryMonthlyRecord.docIdFor(schoolId, yearMonth, profile.staffId)

        val record = SalaryMonthlyRecord(
            id = docId,
            schoolId = schoolId,
            yearMonth = yearMonth,
            staffId = profile.staffId,
            staffName = profile.staffName,
            designation = profile.designation,
            staffUid = profile.linkedMemberUid,
            joiningDateIso = profile.joiningDateIso,
            workingDaysInMonth = workingDays,
            presentDays = attendanceCounts.presentDays,
            absentDays = attendanceCounts.absentDays,
            onLeaveDays = attendanceCounts.onLeaveDays,
            halfDays = attendanceCounts.halfDays,
            lateDays = attendanceCounts.lateDays,
            freeLeavesApplied = freeLeavesApplied,
            unpaidAbsenceDays = unpaidAbsenceDays,
            perDayRateRs = perDayRateRs,
            basicSalaryRs = basic,
            allowances = allowances,
            totalAllowancesRs = totalAllowances,
            grossEarningsRs = gross,
            attendanceDeductionRs = attendanceDeductionRs,
            fixedDeductions = fixedDeductions,
            totalFixedDeductionsRs = totalFixedDeductions,
            advanceDeductionRs = advanceDeductionRs,
            linkedAdvanceIds = staffAdvances.map { it.id },
            manualAdjustmentRs = manualAdjustmentRs,
            adjustmentRemark = adjustmentRemark.trim(),
            netPayableRs = netPayableRs,
            status = existingRecord?.status ?: SalaryPaymentStatus.PENDING,
            paidDateIso = existingRecord?.paidDateIso.orEmpty(),
            paymentMode = existingRecord?.paymentMode ?: PaymentMode.CASH,
            paymentReference = existingRecord?.paymentReference.orEmpty(),
            teacherSelfViewEnabled = ruleConfig.allowTeachersToViewOwnSlips,
            lastLockOverrideReason = existingRecord?.lastLockOverrideReason.orEmpty(),
            updatedByUid = updatedByUid,
            updatedByName = updatedByName,
            clientEditedAtMs = nowMs
        )

        val summary = buildString {
            append("Gross ₹$gross (Basic ₹$basic + Allowances ₹$totalAllowances)")
            append(" | Unpaid days: $unpaidAbsenceDays (Free leave used: $freeLeavesApplied) -> Attendance Ded. ₹$attendanceDeductionRs")
            if (totalFixedDeductions > 0) append(" | Fixed Ded. ₹$totalFixedDeductions")
            if (advanceDeductionRs > 0) append(" | Advance Ded. ₹$advanceDeductionRs")
            if (manualAdjustmentRs != 0) append(" | Manual Adj. ₹$manualAdjustmentRs (${adjustmentRemark.trim()})")
            append(" => Net ₹$netPayableRs")
        }

        return SalaryComputationResult(record = record, breakdownSummary = summary)
    }

    /**
     * Hand adjustment requires a non-blank remark whenever [manualAdjustmentRs] != 0.
     */
    fun validateManualAdjustment(manualAdjustmentRs: Int, adjustmentRemark: String): String? {
        if (manualAdjustmentRs != 0 && adjustmentRemark.trim().isEmpty()) {
            return "Please enter a remark explaining the manual salary adjustment."
        }
        return null
    }

    /**
     * When a month is locked by the Owner, any modification requires a post-lock reason
     * and is logged in the salary audit log.
     */
    fun validatePostLockChange(isMonthLocked: Boolean, lockOverrideReason: String): String? {
        if (isMonthLocked && lockOverrideReason.trim().isEmpty()) {
            return "This month is locked. Please provide a reason to record and log this change."
        }
        return null
    }

    /**
     * Applies an automatic or manual advance repayment across a staff member's active advances
     * (oldest advance first), returning the updated advance records.
     */
    fun applyAdvanceRepayment(
        advances: List<StaffSalaryAdvanceRecord>,
        staffId: String,
        repaymentAmountRs: Int,
        dateIso: String,
        yearMonth: String,
        mode: String,
        isAutoFromSalarySlip: Boolean,
        note: String,
        updatedByUid: String,
        nowMs: Long = System.currentTimeMillis()
    ): List<StaffSalaryAdvanceRecord> {
        if (repaymentAmountRs <= 0) return emptyList()
        var remainingToApply = repaymentAmountRs
        val updated = mutableListOf<StaffSalaryAdvanceRecord>()

        val activeForStaff = advances
            .filter { it.staffId == staffId && it.status == SalaryAdvanceStatus.ACTIVE && it.remainingBalanceRs > 0 }
            .sortedBy { it.advanceDateIso }

        for (adv in activeForStaff) {
            if (remainingToApply <= 0) break
            // Avoid duplicate auto-deduction for the exact same yearMonth on the same advance
            val alreadyAutoDeductedThisMonth = isAutoFromSalarySlip &&
                adv.repayments.any { it.isAutoFromSalarySlip && it.yearMonth == yearMonth }
            if (alreadyAutoDeductedThisMonth) continue

            val applyHere = min(remainingToApply, adv.remainingBalanceRs)
            if (applyHere <= 0) continue

            val entry = SalaryAdvanceRepaymentEntry(
                id = "rep_${yearMonth.ifBlank { dateIso }}_${nowMs}_${adv.repayments.size + 1}",
                dateIso = dateIso,
                yearMonth = yearMonth,
                amountRs = applyHere,
                mode = mode,
                isAutoFromSalarySlip = isAutoFromSalarySlip,
                note = note.trim()
            )
            val newRepaid = (adv.repaidAmountRs + applyHere).coerceAtMost(adv.principalAmountRs)
            val newStatus = if (newRepaid >= adv.principalAmountRs) {
                SalaryAdvanceStatus.CLOSED
            } else {
                SalaryAdvanceStatus.ACTIVE
            }
            updated += adv.copy(
                repaidAmountRs = newRepaid,
                repayments = adv.repayments + entry,
                status = newStatus,
                updatedByUid = updatedByUid,
                clientEditedAtMs = nowMs
            )
            remainingToApply -= applyHere
        }
        return updated
    }

    /**
     * Converts PAID salary records into deterministic Phase 10 Cash Book expense entries
     * so that the Cash Book shows salary paid as an expense automatically, without double entry.
     */
    fun toAutoCashBookExpenseEntries(
        schoolId: String = "",
        salaryRecords: List<SalaryMonthlyRecord>
    ): List<CashBookEntryRecord> {
        return salaryRecords
            .filter {
                (schoolId.isBlank() || it.schoolId == schoolId) &&
                    it.status == SalaryPaymentStatus.PAID &&
                    it.netPayableRs > 0 &&
                    it.paidDateIso.isNotBlank()
            }
            .map { rec ->
                CashBookEntryRecord(
                    id = "auto_sal_${rec.id}",
                    schoolId = rec.schoolId,
                    dateIso = rec.paidDateIso,
                    entryType = CashBookEntryType.EXPENSE,
                    category = "Salary",
                    amount = rec.netPayableRs,
                    paymentMode = rec.paymentMode,
                    note = "Salary (${rec.yearMonth}) • ${rec.staffName} (${rec.designation})" +
                        if (rec.paymentReference.isNotBlank()) " • Ref: ${rec.paymentReference}" else "",
                    billPhotoBase64 = "",
                    status = CashBookEntryStatus.ACTIVE,
                    createdByUid = rec.updatedByUid,
                    createdByName = rec.updatedByName.ifBlank { "Salary Sheet" },
                    createdAtMs = rec.clientEditedAtMs,
                    updatedByUid = rec.updatedByUid,
                    updatedByName = rec.updatedByName.ifBlank { "Salary Sheet" },
                    clientEditedAtMs = rec.clientEditedAtMs
                )
            }
    }

    /**
     * Merges manual Cash Book entries with automatic paid-salary expenses without double entry.
     */
    fun mergeCashBookEntriesWithPaidSalaries(
        schoolId: String,
        cashBookEntries: List<CashBookEntryRecord>,
        salaryRecords: List<SalaryMonthlyRecord>
    ): List<CashBookEntryRecord> {
        val autoSalaryEntries = toAutoCashBookExpenseEntries(schoolId, salaryRecords)
        if (autoSalaryEntries.isEmpty()) return cashBookEntries
        val existingIds = cashBookEntries.mapTo(mutableSetOf()) { it.id }
        val nonDuplicateAuto = autoSalaryEntries.filter { it.id !in existingIds }
        return cashBookEntries + nonDuplicateAuto
    }

    /**
     * Builds a CSV export for the monthly salary sheet.
     */
    fun buildMonthlySalaryCsv(
        schoolName: String,
        yearMonth: String,
        records: List<SalaryMonthlyRecord>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("School,Month,Staff ID,Staff Name,Designation,Working Days,Present,Absent,Leave,Half-Day,Late,Free Leave Used,Unpaid Days,Basic (Rs),Allowances (Rs),Gross (Rs),Attendance Ded (Rs),Fixed Ded (Rs),Advance Ded (Rs),Manual Adj (Rs),Adjustment Remark,Net Salary (Rs),Status,Paid Date,Payment Mode")
        for (r in records.sortedBy { it.staffName.lowercase() }) {
            sb.appendLine(
                listOf(
                    escapeCsv(schoolName),
                    escapeCsv(yearMonth),
                    escapeCsv(r.staffId),
                    escapeCsv(r.staffName),
                    escapeCsv(r.designation),
                    r.workingDaysInMonth.toString(),
                    r.presentDays.toString(),
                    r.absentDays.toString(),
                    r.onLeaveDays.toString(),
                    r.halfDays.toString(),
                    r.lateDays.toString(),
                    r.freeLeavesApplied.toString(),
                    r.unpaidAbsenceDays.toString(),
                    r.basicSalaryRs.toString(),
                    r.totalAllowancesRs.toString(),
                    r.grossEarningsRs.toString(),
                    r.attendanceDeductionRs.toString(),
                    r.totalFixedDeductionsRs.toString(),
                    r.advanceDeductionRs.toString(),
                    r.manualAdjustmentRs.toString(),
                    escapeCsv(r.adjustmentRemark),
                    r.netPayableRs.toString(),
                    r.status.wireValue,
                    escapeCsv(r.paidDateIso),
                    r.paymentMode.wireValue
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val cleaned = value.replace("\"", "\"\"")
        return if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("\n")) {
            "\"$cleaned\""
        } else {
            cleaned
        }
    }
}
