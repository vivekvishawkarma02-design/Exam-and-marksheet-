package com.example

import com.example.domain.AccessLevel
import com.example.domain.AppModule
import com.example.domain.CashBookEngine
import com.example.domain.CashBookEntryRecord
import com.example.domain.CashBookEntryStatus
import com.example.domain.CashBookEntryType
import com.example.domain.CashBookSettingsConfig
import com.example.domain.DayOfWeekCode
import com.example.domain.LeaveStatus
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgAccessEvaluator
import com.example.domain.OrgRole
import com.example.domain.PaymentMode
import com.example.domain.Phase12AccessEvaluator
import com.example.domain.PulledStaffAttendanceCounts
import com.example.domain.SalaryAdvanceStatus
import com.example.domain.SalaryComponentItem
import com.example.domain.SalaryMonthlyRecord
import com.example.domain.SalaryPaymentStatus
import com.example.domain.SalaryPerDayBasis
import com.example.domain.SalaryRuleConfig
import com.example.domain.SchoolPeriodStructure
import com.example.domain.StaffSalaryAdvanceRecord
import com.example.domain.StaffSalaryEngine
import com.example.domain.StaffSalaryProfileRecord
import com.example.domain.TeacherAttendanceRecord
import com.example.domain.TeacherAttendanceStatus
import com.example.domain.TeacherLeaveRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12 Pure JVM Unit Tests:
 * 1. Sensitive "Salary" (18th module) access control:
 *    - By default ONLY Owner has access.
 *    - Admins do NOT automatically have access; Owner must explicitly grant VIEW or EDIT.
 *    - Only Owner can grant salary access or lock/unlock months.
 *    - Missing 'salary' key in permissions map defaults to NONE.
 *    - Teachers can see ONLY their own salary slips ONLY when Owner enables allowTeachersToViewOwnSlips.
 * 2. Salary calculation from Phase 7 teacher attendance:
 *    - Absences, half-days, approved leaves, free leaves per month, late-day penalties,
 *      FIXED_30_DAYS vs WORKING_DAYS_IN_MONTH vs fixed per-day override, and manual hand adjustment with mandatory remark.
 * 3. Advances & loans:
 *    - Issue advance, automatic monthly instalment deduction over multiple months, manual repayment, auto-close at 0 balance.
 * 4. Month locking:
 *    - Post-lock change validation requires mandatory override reason.
 * 5. Phase 10 Cash Book automatic integration:
 *    - Paid salaries automatically appear as "Salary" expenses in Cash Book without double entry.
 */
class Phase12DomainAndAccessTest {

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

    // =========================================================================
    // 1. SENSITIVE SALARY MODULE ACCESS CONTROL (OWNER-ONLY DEFAULT, ADMIN GRANT, TEACHER SELF-SLIP)
    // =========================================================================

    @Test
    fun salaryModuleIsOwnerOnlyByDefaultAndRequiresExplicitOwnerGrantForAdmins() {
        // Legacy 17-key map missing 'salary' -> defaults to AccessLevel.NONE
        val legacy17Map = mapOf(
            "students" to "edit",
            "marks" to "edit",
            "coScholastic" to "edit",
            "reports" to "edit",
            "timetable" to "edit",
            "teachers" to "edit",
            "settings" to "edit",
            "members" to "edit",
            "attendance" to "edit",
            "fees" to "edit",
            "studentRegister" to "edit",
            "certificates" to "edit",
            "cashBook" to "edit",
            "enquiry" to "edit",
            "exams" to "edit",
            "stock" to "edit",
            "transport" to "edit"
        )
        val parsedFromLegacy = MemberPermissions.fromMap(legacy17Map)
        assertEquals(AccessLevel.NONE, parsedFromLegacy.salary)
        assertEquals(23, AppModule.entries.size)
        assertEquals(23, parsedFromLegacy.toFirestoreMap().size)

        // Role presets: ONLY OWNER gets EDIT on salary by default; ADMIN gets NONE
        val ownerPreset = MemberPermissions.presetFor(OrgRole.OWNER)
        val adminPreset = MemberPermissions.presetFor(OrgRole.ADMIN)
        val officePreset = MemberPermissions.presetFor(OrgRole.OFFICE_OPERATOR)
        val teacherPreset = MemberPermissions.presetFor(OrgRole.TEACHER)
        assertEquals(AccessLevel.EDIT, ownerPreset.salary)
        assertEquals(AccessLevel.NONE, adminPreset.salary)
        assertEquals(AccessLevel.NONE, officePreset.salary)
        assertEquals(AccessLevel.NONE, teacherPreset.salary)

        // Owner has full salary access and can lock months / grant salary access
        val owner = makeMember(uid = "u_owner", role = OrgRole.OWNER, allSchools = true, permissions = ownerPreset)
        assertTrue(Phase12AccessEvaluator.canViewSalary(owner, "sch_1"))
        assertTrue(Phase12AccessEvaluator.canEditSalary(owner, "sch_1"))
        assertTrue(Phase12AccessEvaluator.canLockOrUnlockSalaryMonth(owner))
        assertTrue(Phase12AccessEvaluator.canGrantSalaryPermission(owner))
        assertTrue(OrgAccessEvaluator.canViewModule(owner, AppModule.SALARY))
        assertTrue(OrgAccessEvaluator.canEditModule(owner, AppModule.SALARY))
        assertTrue(OrgAccessEvaluator.canGrantSalaryAccess(owner))

        // Admin WITHOUT explicit salary grant sees NOTHING of salary, even though Admin has full access to other modules
        val adminWithoutGrant = makeMember(uid = "u_admin_default", role = OrgRole.ADMIN, allSchools = true, permissions = adminPreset)
        assertTrue(OrgAccessEvaluator.canEditModule(adminWithoutGrant, AppModule.FEES))
        assertTrue(OrgAccessEvaluator.canEditModule(adminWithoutGrant, AppModule.MEMBERS))
        assertFalse(Phase12AccessEvaluator.canViewSalary(adminWithoutGrant, "sch_1"))
        assertFalse(Phase12AccessEvaluator.canEditSalary(adminWithoutGrant, "sch_1"))
        assertFalse(OrgAccessEvaluator.canViewModule(adminWithoutGrant, AppModule.SALARY))
        assertFalse(OrgAccessEvaluator.canEditModule(adminWithoutGrant, AppModule.SALARY))
        assertFalse(Phase12AccessEvaluator.canLockOrUnlockSalaryMonth(adminWithoutGrant))
        assertFalse(Phase12AccessEvaluator.canGrantSalaryPermission(adminWithoutGrant))
        assertFalse(OrgAccessEvaluator.canGrantSalaryAccess(adminWithoutGrant))

        // Admin WITH explicit Owner grant (VIEW vs EDIT)
        val adminWithView = adminWithoutGrant.copy(
            permissions = adminPreset.withModule(AppModule.SALARY, AccessLevel.VIEW)
        )
        assertTrue(Phase12AccessEvaluator.canViewSalary(adminWithView, "sch_1"))
        assertFalse(Phase12AccessEvaluator.canEditSalary(adminWithView, "sch_1"))
        assertFalse(Phase12AccessEvaluator.canLockOrUnlockSalaryMonth(adminWithView))

        val adminWithEdit = adminWithoutGrant.copy(
            permissions = adminPreset.withModule(AppModule.SALARY, AccessLevel.EDIT)
        )
        assertTrue(Phase12AccessEvaluator.canViewSalary(adminWithEdit, "sch_1"))
        assertTrue(Phase12AccessEvaluator.canEditSalary(adminWithEdit, "sch_1"))
        // Even an Admin with salary:edit cannot lock months or grant salary access to others (Owner-only)
        assertFalse(Phase12AccessEvaluator.canLockOrUnlockSalaryMonth(adminWithEdit))
        assertFalse(Phase12AccessEvaluator.canGrantSalaryPermission(adminWithEdit))
    }

    @Test
    fun teacherCanViewOnlyOwnSalarySlipsOnlyWhenOwnerEnablesToggle() {
        val teacherA = makeMember(
            uid = "uid_tchr_a",
            role = OrgRole.TEACHER,
            schoolIds = listOf("sch_1"),
            permissions = MemberPermissions.presetFor(OrgRole.TEACHER)
        )
        val slipADisabled = SalaryMonthlyRecord(
            id = "salslip_sch_1_2026-10_prof_a",
            schoolId = "sch_1",
            yearMonth = "2026-10",
            staffId = "prof_a",
            staffUid = "uid_tchr_a",
            staffName = "Mrs. Sunita Verma",
            netPayableRs = 16400,
            teacherSelfViewEnabled = false
        )
        val slipAEnabled = slipADisabled.copy(teacherSelfViewEnabled = true)
        val slipBEnabled = slipAEnabled.copy(
            id = "salslip_sch_1_2026-10_prof_b",
            staffId = "prof_b",
            staffUid = "uid_tchr_b",
            staffName = "Mr. Rajesh Singh"
        )

        val ruleDisabled = SalaryRuleConfig(schoolId = "sch_1", allowTeachersToViewOwnSlips = false)
        val ruleEnabled = SalaryRuleConfig(schoolId = "sch_1", allowTeachersToViewOwnSlips = true)

        // When toggle is disabled by default -> Teacher A cannot see own slip
        assertFalse(Phase12AccessEvaluator.canTeacherViewOwnSalarySlip(teacherA, slipADisabled, ruleDisabled))

        // When Owner enables toggle -> Teacher A can see own slip, but NEVER Teacher B's slip
        assertTrue(Phase12AccessEvaluator.canTeacherViewOwnSalarySlip(teacherA, slipAEnabled, ruleEnabled))
        assertFalse(Phase12AccessEvaluator.canTeacherViewOwnSalarySlip(teacherA, slipBEnabled, ruleEnabled))
    }

    // =========================================================================
    // 2. SALARY CALCULATION FROM PHASE 7 TEACHER ATTENDANCE
    // =========================================================================

    @Test
    fun computeMonthlySalaryAppliesFreeLeavesHalfDaysLatePenaltiesAndHandAdjustment() {
        val profile = StaffSalaryProfileRecord(
            id = "salprof_t1",
            staffId = "t1",
            linkedMemberUid = "uid_t1",
            staffName = "Mrs. Sunita Verma",
            designation = "Assistant Teacher",
            isTeachingStaff = true,
            primarySchoolId = "sch_1",
            schoolIds = listOf("sch_1"),
            basicMonthlySalaryRs = 18000,
            allowances = listOf(
                SalaryComponentItem("a1", "HRA", 2000),
                SalaryComponentItem("a2", "DA", 1000)
            ),
            fixedDeductions = listOf(
                SalaryComponentItem("d1", "PF", 1200)
            )
        )

        // Rule: Basic / 30 (= Rs 600/day), 1 free leave/month,
        // half-day = 0.5 day (consumes free leave first), every 3 late days = 0.5 day penalty
        val rule = SalaryRuleConfig(
            schoolId = "sch_1",
            perDayBasis = SalaryPerDayBasis.FIXED_30_DAYS,
            freeLeavesPerMonth = 1,
            halfDayAbsenceWeight = 0.5,
            halfDayConsumesFreeLeave = true,
            lateDaysPerHalfDayPenalty = 3,
            fixedPerAbsentDayDeductionRs = 0
        )

        // Attendance in Oct 2026 (26 working days):
        // 21 Present, 2 Absent, 1 Leave, 2 Half-Days (= 1.0 day), 3 Late days (= 0.5 day penalty)
        // Leave + Half-day pool = 1.0 + 1.0 = 2.0 days; free leave covers 1.0 -> 1.0 unpaid leave unit
        // Total unpaid absence days = 2.0 (absent) + 1.0 (unpaid leave/half-day) + 0.5 (3 late days) = 3.5 days
        // Attendance deduction = 3.5 * Rs 600 = Rs 2100
        val attendance = PulledStaffAttendanceCounts(
            workingDaysInMonth = 26,
            presentDays = 21,
            absentDays = 2,
            onLeaveDays = 1,
            halfDays = 2,
            lateDays = 3
        )

        // Active advance: Rs 6000 principal, Rs 2000 monthly instalment, Rs 2000 repaid -> Rs 4000 remaining
        val activeAdvance = StaffSalaryAdvanceRecord(
            id = "adv_1",
            schoolId = "sch_1",
            staffId = profile.staffId,
            staffUid = profile.linkedMemberUid,
            staffName = profile.staffName,
            advanceDateIso = "2026-09-10",
            principalAmountRs = 6000,
            monthlyInstalmentRs = 2000,
            repaidAmountRs = 2000,
            status = SalaryAdvanceStatus.ACTIVE
        )

        val computedResult = StaffSalaryEngine.computeMonthlySalary(
            schoolId = "sch_1",
            yearMonth = "2026-10",
            profile = profile,
            ruleConfig = rule,
            attendanceCounts = attendance,
            activeAdvances = listOf(activeAdvance),
            manualAdjustmentRs = 500, // +Rs 500 festival bonus adjustment
            adjustmentRemark = "Diwali performance bonus"
        )
        val rec = computedResult.record

        assertEquals(600, rec.perDayRateRs)
        assertEquals(18000, rec.basicSalaryRs)
        assertEquals(3000, rec.totalAllowancesRs)
        assertEquals(21000, rec.grossEarningsRs)
        assertEquals(1200, rec.totalFixedDeductionsRs)
        assertEquals(1.0, rec.freeLeavesApplied, 0.001)
        assertEquals(3.5, rec.unpaidAbsenceDays, 0.001)
        assertEquals(2100, rec.attendanceDeductionRs)
        assertEquals(2000, rec.advanceDeductionRs)
        assertEquals(listOf("adv_1"), rec.linkedAdvanceIds)
        // Net = 21000 (gross) - 2100 (attendance) - 1200 (PF) - 2000 (advance) + 500 (manual) = 16200
        assertEquals(16200, rec.netPayableRs)

        // Manual adjustment remark validation: non-zero adjustment requires remark
        assertNotNull(StaffSalaryEngine.validateManualAdjustment(500, ""))
        assertNull(StaffSalaryEngine.validateManualAdjustment(500, "Diwali performance bonus"))
        assertNull(StaffSalaryEngine.validateManualAdjustment(0, ""))
    }

    @Test
    fun pullMonthlyAttendanceCountsReadsPhase7TeacherAttendanceAndApprovedLeaves() {
        val structure = SchoolPeriodStructure(
            schoolId = "sch_1",
            workingDays = listOf(
                DayOfWeekCode.MON, DayOfWeekCode.TUE, DayOfWeekCode.WED,
                DayOfWeekCode.THU, DayOfWeekCode.FRI, DayOfWeekCode.SAT
            )
        )
        val records = listOf(
            TeacherAttendanceRecord(
                id = "a1", date = "2026-04-01", schoolId = "sch_1",
                teacherId = "t1", teacherName = "Mrs. Sunita Verma",
                status = TeacherAttendanceStatus.PRESENT, note = "Late by 20 mins"
            ),
            TeacherAttendanceRecord(
                id = "a2", date = "2026-04-02", schoolId = "sch_1",
                teacherId = "t1", teacherName = "Mrs. Sunita Verma",
                status = TeacherAttendanceStatus.ABSENT
            ),
            TeacherAttendanceRecord(
                id = "a3", date = "2026-04-03", schoolId = "sch_1",
                teacherId = "t1", teacherName = "Mrs. Sunita Verma",
                status = TeacherAttendanceStatus.HALF_DAY
            )
        )
        val leaves = listOf(
            TeacherLeaveRecord(
                id = "lv1", teacherId = "t1", teacherName = "Mrs. Sunita Verma",
                startDate = "2026-04-04", endDate = "2026-04-04",
                status = LeaveStatus.APPROVED
            )
        )

        val pulled = StaffSalaryEngine.pullMonthlyAttendanceCounts(
            schoolId = "sch_1",
            yearMonth = "2026-04",
            staffId = "t1",
            staffName = "Mrs. Sunita Verma",
            periodStructure = structure,
            attendanceRecords = records,
            approvedLeaves = leaves
        )
        assertEquals(26, pulled.workingDaysInMonth) // April 2026 has 26 Mon-Sat working days
        assertEquals(1, pulled.presentDays)
        assertEquals(1, pulled.absentDays)
        assertEquals(1, pulled.halfDays)
        assertEquals(1, pulled.onLeaveDays)
        assertEquals(1, pulled.lateDays) // detected from "Late by 20 mins" note
    }

    // =========================================================================
    // 3. ADVANCES & LOANS: MULTI-MONTH INSTALMENT DEDUCTION & AUTO-CLOSE
    // =========================================================================

    @Test
    fun advanceRepaymentDeductsOverMonthsAndClosesAutomaticallyWhenBalanceReachesZero() {
        val advance0 = StaffSalaryAdvanceRecord(
            id = "adv_101",
            schoolId = "sch_1",
            staffId = "prof_1",
            staffUid = "uid_1",
            staffName = "Ramesh Kumar",
            advanceDateIso = "2026-04-05",
            principalAmountRs = 5000,
            monthlyInstalmentRs = 2000,
            repaidAmountRs = 0,
            status = SalaryAdvanceStatus.ACTIVE
        )

        // Month 1 (2026-04): deduct Rs 2000 -> remaining Rs 3000 (ACTIVE)
        val afterMonth1List = StaffSalaryEngine.applyAdvanceRepayment(
            advances = listOf(advance0),
            staffId = "prof_1",
            repaymentAmountRs = 2000,
            dateIso = "2026-04-30",
            yearMonth = "2026-04",
            mode = "SALARY_DEDUCTION",
            isAutoFromSalarySlip = true,
            note = "April 2026 salary deduction",
            updatedByUid = "u_owner"
        )
        assertEquals(1, afterMonth1List.size)
        val afterMonth1 = afterMonth1List.first()
        assertEquals(2000, afterMonth1.repaidAmountRs)
        assertEquals(3000, afterMonth1.remainingBalanceRs)
        assertEquals(SalaryAdvanceStatus.ACTIVE, afterMonth1.status)
        assertEquals(1, afterMonth1.repayments.size)

        // Idempotency: re-applying auto deduction for the same yearMonth ("2026-04") does NOT double-deduct
        val duplicateMonth1List = StaffSalaryEngine.applyAdvanceRepayment(
            advances = listOf(afterMonth1),
            staffId = "prof_1",
            repaymentAmountRs = 2000,
            dateIso = "2026-04-30",
            yearMonth = "2026-04",
            mode = "SALARY_DEDUCTION",
            isAutoFromSalarySlip = true,
            note = "Duplicate retry",
            updatedByUid = "u_owner"
        )
        assertTrue(duplicateMonth1List.isEmpty())

        // Month 2 (2026-05): deduct Rs 2000 -> remaining Rs 1000 (ACTIVE)
        val afterMonth2 = StaffSalaryEngine.applyAdvanceRepayment(
            advances = listOf(afterMonth1),
            staffId = "prof_1",
            repaymentAmountRs = 2000,
            dateIso = "2026-05-31",
            yearMonth = "2026-05",
            mode = "SALARY_DEDUCTION",
            isAutoFromSalarySlip = true,
            note = "May 2026 salary deduction",
            updatedByUid = "u_owner"
        ).first()
        assertEquals(4000, afterMonth2.repaidAmountRs)
        assertEquals(1000, afterMonth2.remainingBalanceRs)
        assertEquals(SalaryAdvanceStatus.ACTIVE, afterMonth2.status)

        // Month 3 (2026-06): instalment is 2000, but remaining balance is 1000 -> caps at 1000 and auto-closes!
        val afterMonth3 = StaffSalaryEngine.applyAdvanceRepayment(
            advances = listOf(afterMonth2),
            staffId = "prof_1",
            repaymentAmountRs = 2000,
            dateIso = "2026-06-30",
            yearMonth = "2026-06",
            mode = "SALARY_DEDUCTION",
            isAutoFromSalarySlip = true,
            note = "June 2026 final instalment",
            updatedByUid = "u_owner"
        ).first()
        assertEquals(5000, afterMonth3.repaidAmountRs)
        assertEquals(0, afterMonth3.remainingBalanceRs)
        assertEquals(SalaryAdvanceStatus.CLOSED, afterMonth3.status)
        assertEquals(3, afterMonth3.repayments.size)
    }

    // =========================================================================
    // 4. MONTH LOCKING & POST-LOCK OVERRIDE REASON VALIDATION
    // =========================================================================

    @Test
    fun lockedMonthRequiresMandatoryReasonForPostLockChanges() {
        assertNull(StaffSalaryEngine.validatePostLockChange(isMonthLocked = false, lockOverrideReason = ""))
        assertNotNull(StaffSalaryEngine.validatePostLockChange(isMonthLocked = true, lockOverrideReason = "  "))
        assertNull(
            StaffSalaryEngine.validatePostLockChange(
                isMonthLocked = true,
                lockOverrideReason = "Approved arrear adjustment after audit"
            )
        )
    }

    // =========================================================================
    // 5. PHASE 10 CASH BOOK AUTOMATIC EXPENSE INTEGRATION (NO DOUBLE ENTRY)
    // =========================================================================

    @Test
    fun paidSalariesAutomaticallyAppearInCashBookExpensesWithoutDoubleEntry() {
        val manualExpense = CashBookEntryRecord(
            id = "cb_exp_1",
            schoolId = "sch_1",
            dateIso = "2026-04-30",
            entryType = CashBookEntryType.EXPENSE,
            category = "Electricity",
            amount = 1500,
            paymentMode = PaymentMode.CASH,
            status = CashBookEntryStatus.ACTIVE
        )

        val pendingSalary = SalaryMonthlyRecord(
            id = "salrec_pending",
            schoolId = "sch_1",
            yearMonth = "2026-04",
            staffId = "prof_1",
            staffName = "Pending Staff",
            netPayableRs = 12000,
            status = SalaryPaymentStatus.PENDING
        )

        val paidSalary = SalaryMonthlyRecord(
            id = "salrec_paid_1",
            schoolId = "sch_1",
            yearMonth = "2026-04",
            staffId = "prof_2",
            staffName = "Mrs. Sunita Verma",
            designation = "Teacher",
            netPayableRs = 16500,
            status = SalaryPaymentStatus.PAID,
            paidDateIso = "2026-04-30",
            paymentMode = PaymentMode.CASH,
            updatedByUid = "u_owner",
            updatedByName = "Principal Owner"
        )

        // Merge twice to verify idempotency / no double entry
        val mergedOnce = StaffSalaryEngine.mergeCashBookEntriesWithPaidSalaries(
            schoolId = "sch_1",
            cashBookEntries = listOf(manualExpense),
            salaryRecords = listOf(pendingSalary, paidSalary)
        )
        val mergedTwice = StaffSalaryEngine.mergeCashBookEntriesWithPaidSalaries(
            schoolId = "sch_1",
            cashBookEntries = mergedOnce,
            salaryRecords = listOf(pendingSalary, paidSalary)
        )

        assertEquals(2, mergedOnce.size)
        assertEquals(2, mergedTwice.size) // No duplicate entry even if merged again

        val daySummary = CashBookEngine.computeDaySummary(
            schoolId = "sch_1",
            dateIso = "2026-04-30",
            feeReceipts = emptyList(),
            entries = mergedTwice,
            dayClosings = emptyList(),
            settings = CashBookSettingsConfig.buildDefault("sch_1").copy(defaultOpeningBalanceRs = 25000)
        )

        // Total expenses = Rs 1500 (Electricity) + Rs 16500 (Auto Salary) = Rs 18000
        assertEquals(18000, daySummary.totalMoneyOut)
        assertEquals(7000, daySummary.expectedClosingCash)
    }
}
