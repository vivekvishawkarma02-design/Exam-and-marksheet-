package com.example

import com.example.domain.AccessLevel
import com.example.domain.AppModule
import com.example.domain.AssignmentWarningType
import com.example.domain.ClassSectionAssignment
import com.example.domain.DayOfWeekCode
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgAccessEvaluator
import com.example.domain.OrgRole
import com.example.domain.PeriodSlot
import com.example.domain.PeriodValidationResult
import com.example.domain.Phase4Validator
import com.example.domain.SchoolPeriodStructure
import com.example.domain.SlotType
import com.example.domain.SubjectTeacherAssignment
import com.example.domain.TeacherProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 Unit Tests:
 * - Period structure validation (overlaps, out-of-order, invalid bounds, adjacent slots, copy day)
 * - Class-subject-teacher assignment warnings (period total mismatch, teacher over weekly limit,
 *   subject not in teacher's list, cross-school period-time overlap, inactive teacher assigned)
 * - Teacher deletion guard (never delete teacher with subject assignments or class teacher role)
 * - Teacher workload summary computation
 * - Module access matrix gating for "Teachers and assignments"
 */
class Phase4TimetableSetupValidatorTest {

    // -------------------------------------------------------------------------
    // 1. Period Structure Validation & Copy Day
    // -------------------------------------------------------------------------

    @Test
    fun defaultPeriodStructure_isValid_andHasExpectedCounts() {
        val structure = Phase4Validator.buildDefaultPeriodStructure("sch_1")
        assertTrue(Phase4Validator.validatePeriodStructure(structure) is PeriodValidationResult.Valid)
        // Mon..Fri = 8 periods each (40), Sat = 5 periods => 45 total periods/week
        assertEquals(8, structure.teachingPeriodsPerDay(DayOfWeekCode.MON))
        assertEquals(5, structure.teachingPeriodsPerDay(DayOfWeekCode.SAT))
        assertEquals(0, structure.teachingPeriodsPerDay(DayOfWeekCode.SUN))
        assertEquals(45, structure.totalTeachingPeriodsPerWeek())

        // Verify Morning Assembly, Short Break after Period 3, Lunch after Period 5 on Monday
        val mon = structure.slotsFor(DayOfWeekCode.MON)
        assertEquals(SlotType.ASSEMBLY, mon.first().type)
        assertTrue(mon.any { it.type == SlotType.SHORT_BREAK })
        assertTrue(mon.any { it.type == SlotType.LUNCH })
    }

    @Test
    fun validateDaySlots_acceptsTouchingAdjacentSlots() {
        val slots = listOf(
            PeriodSlot("s1", SlotType.ASSEMBLY, "Assembly", 480, 495),     // 08:00-08:15
            PeriodSlot("s2", SlotType.PERIOD, "Period 1", 495, 535),       // 08:15-08:55
            PeriodSlot("s3", SlotType.PERIOD, "Period 2", 535, 575),       // 08:55-09:35
            PeriodSlot("s4", SlotType.SHORT_BREAK, "Break", 575, 590),     // 09:35-09:50
            PeriodSlot("s5", SlotType.PERIOD, "Period 3", 600, 640)        // 10:00-10:40 (10m gap ok)
        )
        assertTrue(Phase4Validator.validateDaySlots(DayOfWeekCode.MON, slots) is PeriodValidationResult.Valid)
    }

    @Test
    fun validateDaySlots_rejectsPartialOverlap_andNestedOverlap() {
        val partialOverlap = listOf(
            PeriodSlot("s1", SlotType.PERIOD, "Period 1", 480, 520), // 08:00-08:40
            PeriodSlot("s2", SlotType.PERIOD, "Period 2", 510, 550)  // 08:30-09:10 (overlaps 10 min)
        )
        val res1 = Phase4Validator.validateDaySlots(DayOfWeekCode.TUE, partialOverlap)
        assertTrue(res1 is PeriodValidationResult.Invalid)
        assertTrue((res1 as PeriodValidationResult.Invalid).errors.any { it.contains("overlaps", ignoreCase = true) })

        val nestedOverlap = listOf(
            PeriodSlot("s1", SlotType.PERIOD, "Long Slot", 480, 600), // 08:00-10:00
            PeriodSlot("s2", SlotType.SHORT_BREAK, "Break", 500, 520) // 08:20-08:40
        )
        val res2 = Phase4Validator.validateDaySlots(DayOfWeekCode.WED, nestedOverlap)
        assertTrue(res2 is PeriodValidationResult.Invalid)
        assertTrue((res2 as PeriodValidationResult.Invalid).errors.any { it.contains("overlaps", ignoreCase = true) })
    }

    @Test
    fun validateDaySlots_rejectsOutOfOrderTimes_andEndBeforeStart() {
        val outOfOrder = listOf(
            PeriodSlot("s1", SlotType.PERIOD, "Period 2", 540, 580), // 09:00-09:40
            PeriodSlot("s2", SlotType.PERIOD, "Period 1", 480, 520)  // 08:00-08:40
        )
        val res1 = Phase4Validator.validateDaySlots(DayOfWeekCode.THU, outOfOrder)
        assertTrue(res1 is PeriodValidationResult.Invalid)
        assertTrue((res1 as PeriodValidationResult.Invalid).errors.any { it.contains("order", ignoreCase = true) })

        val reversedSlot = listOf(
            PeriodSlot("s1", SlotType.PERIOD, "Bad Period", 540, 540), // 09:00-09:00
            PeriodSlot("s2", SlotType.PERIOD, "Worse Period", 600, 580) // 10:00-09:40
        )
        val res2 = Phase4Validator.validateDaySlots(DayOfWeekCode.FRI, reversedSlot)
        assertTrue(res2 is PeriodValidationResult.Invalid)
        assertTrue((res2 as PeriodValidationResult.Invalid).errors.size >= 2)
    }

    @Test
    fun copyDaySlots_copiesToTargetDays_withDistinctSlotIds_andAddsToWorkingDays() {
        val base = Phase4Validator.buildDefaultPeriodStructure("sch_1")
        val customMon = listOf(
            PeriodSlot("m1", SlotType.PERIOD, "P1", 540, 585),
            PeriodSlot("m2", SlotType.PERIOD, "P2", 585, 630)
        )
        val edited = base.copy(slotsByDay = base.slotsByDay + (DayOfWeekCode.MON to customMon))
        val copied = Phase4Validator.copyDaySlots(
            structure = edited,
            sourceDay = DayOfWeekCode.MON,
            targetDays = listOf(DayOfWeekCode.TUE, DayOfWeekCode.SAT)
        )

        assertEquals(2, copied.slotsFor(DayOfWeekCode.TUE).size)
        assertEquals(2, copied.slotsFor(DayOfWeekCode.SAT).size)
        assertEquals(540, copied.slotsFor(DayOfWeekCode.SAT)[0].startMinutes)
        assertTrue(copied.slotsFor(DayOfWeekCode.TUE)[0].id.startsWith("TUE_"))
        assertTrue(copied.slotsFor(DayOfWeekCode.SAT)[0].id.startsWith("SAT_"))
        assertTrue(Phase4Validator.validatePeriodStructure(copied) is PeriodValidationResult.Valid)
    }

    // -------------------------------------------------------------------------
    // 2. Class-Subject-Teacher Assignment Warnings
    // -------------------------------------------------------------------------

    @Test
    fun evaluateWarnings_flagsClassPeriodMismatch_andClearsWhenExactMatch() {
        val structure = SchoolPeriodStructure(
            schoolId = "sch_1",
            workingDays = listOf(DayOfWeekCode.MON, DayOfWeekCode.TUE),
            slotsByDay = mapOf(
                DayOfWeekCode.MON to listOf(
                    PeriodSlot("m1", SlotType.PERIOD, "P1", 480, 520),
                    PeriodSlot("m2", SlotType.PERIOD, "P2", 520, 560)
                ),
                DayOfWeekCode.TUE to listOf(
                    PeriodSlot("t1", SlotType.PERIOD, "P1", 480, 520),
                    PeriodSlot("t2", SlotType.PERIOD, "P2", 520, 560)
                )
            )
        ) // 4 available periods/week

        val teacher = TeacherProfile(
            id = "t1",
            name = "Asha Sharma",
            subjects = listOf("Mathematics", "Science"),
            schoolIds = listOf("sch_1"),
            maxPeriodsPerDay = 7
        )

        // Under-assigned (3 vs 4) -> warning
        val underAssigned = ClassSectionAssignment(
            id = "a1",
            schoolId = "sch_1",
            className = "5th",
            section = "A",
            subjectAssignments = listOf(
                SubjectTeacherAssignment("Mathematics", "t1", 2),
                SubjectTeacherAssignment("Science", "t1", 1)
            )
        )
        val warningsUnder = Phase4Validator.evaluateAllWarnings(
            teachers = listOf(teacher),
            periodStructuresBySchool = mapOf("sch_1" to structure),
            assignments = listOf(underAssigned),
            schoolNamesById = mapOf("sch_1" to "Main School")
        )
        assertTrue(warningsUnder.any { it.type == AssignmentWarningType.CLASS_PERIOD_TOTAL_MISMATCH })

        // Exact match (4 vs 4) -> no mismatch warning
        val exactMatch = underAssigned.copy(
            subjectAssignments = listOf(
                SubjectTeacherAssignment("Mathematics", "t1", 2),
                SubjectTeacherAssignment("Science", "t1", 2)
            )
        )
        val warningsExact = Phase4Validator.evaluateAllWarnings(
            teachers = listOf(teacher),
            periodStructuresBySchool = mapOf("sch_1" to structure),
            assignments = listOf(exactMatch),
            schoolNamesById = mapOf("sch_1" to "Main School")
        )
        assertFalse(warningsExact.any { it.type == AssignmentWarningType.CLASS_PERIOD_TOTAL_MISMATCH })
    }

    @Test
    fun evaluateWarnings_flagsTeacherOverWeeklyLimit_respectingWeeklyOffDays() {
        val structure = Phase4Validator.buildDefaultPeriodStructure("sch_1") // 6 working days Mon..Sat
        val teacher = TeacherProfile(
            id = "t1",
            name = "Rajesh Verma",
            subjects = listOf("Mathematics"),
            schoolIds = listOf("sch_1"),
            maxPeriodsPerDay = 5,
            weeklyOffDays = listOf(DayOfWeekCode.SAT) // Effective days = 5 -> weekly limit = 25
        )
        val (effectiveDays, weeklyLimit) = Phase4Validator.computeTeacherWeeklyLimit(
            teacher = teacher,
            periodStructuresBySchool = mapOf("sch_1" to structure)
        )
        assertEquals(5, effectiveDays)
        assertEquals(25, weeklyLimit)

        val assignment = ClassSectionAssignment(
            id = "a1",
            schoolId = "sch_1",
            className = "8th",
            section = "A",
            subjectAssignments = listOf(
                SubjectTeacherAssignment("Mathematics", "t1", 26) // Exceeds 25
            )
        )
        val warnings = Phase4Validator.evaluateAllWarnings(
            teachers = listOf(teacher),
            periodStructuresBySchool = mapOf("sch_1" to structure),
            assignments = listOf(assignment),
            schoolNamesById = mapOf("sch_1" to "Main School")
        )
        assertTrue(warnings.any { it.type == AssignmentWarningType.TEACHER_OVER_WEEKLY_LIMIT && it.teacherId == "t1" })
    }

    @Test
    fun evaluateWarnings_flagsSubjectNotInTeacherList_andAllowsCaseInsensitiveMatch() {
        val structure = Phase4Validator.buildDefaultPeriodStructure("sch_1")
        val teacher = TeacherProfile(
            id = "t1",
            name = "Meera Nair",
            subjects = listOf("English", "Hindi"),
            schoolIds = listOf("sch_1")
        )

        val assignment = ClassSectionAssignment(
            id = "a1",
            schoolId = "sch_1",
            className = "6th",
            section = "A",
            subjectAssignments = listOf(
                SubjectTeacherAssignment("english", "t1", 6), // case-insensitive OK
                SubjectTeacherAssignment("Mathematics", "t1", 6) // mismatch!
            )
        )

        val warnings = Phase4Validator.evaluateAllWarnings(
            teachers = listOf(teacher),
            periodStructuresBySchool = mapOf("sch_1" to structure),
            assignments = listOf(assignment),
            schoolNamesById = mapOf("sch_1" to "Main School")
        )
        val subjectMismatches = warnings.filter { it.type == AssignmentWarningType.TEACHER_SUBJECT_MISMATCH }
        assertEquals(1, subjectMismatches.size)
        assertEquals("Mathematics", subjectMismatches.first().subjectName)
    }

    @Test
    fun evaluateWarnings_flagsCrossSchoolPeriodOverlap_andClearsForNonOverlappingShifts() {
        val morningSchool = SchoolPeriodStructure(
            schoolId = "sch_morning",
            workingDays = listOf(DayOfWeekCode.MON, DayOfWeekCode.TUE),
            slotsByDay = mapOf(
                DayOfWeekCode.MON to listOf(PeriodSlot("m1", SlotType.PERIOD, "P1", 480, 540)), // 08:00-09:00
                DayOfWeekCode.TUE to listOf(PeriodSlot("t1", SlotType.PERIOD, "P1", 480, 540))
            )
        )
        val overlappingSchool = SchoolPeriodStructure(
            schoolId = "sch_overlap",
            workingDays = listOf(DayOfWeekCode.MON, DayOfWeekCode.TUE),
            slotsByDay = mapOf(
                DayOfWeekCode.MON to listOf(PeriodSlot("m1", SlotType.PERIOD, "P1", 510, 570)), // 08:30-09:30 overlaps!
                DayOfWeekCode.TUE to listOf(PeriodSlot("t1", SlotType.PERIOD, "P1", 840, 900))  // 14:00-15:00 ok
            )
        )
        val afternoonSchool = SchoolPeriodStructure(
            schoolId = "sch_afternoon",
            workingDays = listOf(DayOfWeekCode.MON, DayOfWeekCode.TUE),
            slotsByDay = mapOf(
                DayOfWeekCode.MON to listOf(PeriodSlot("m1", SlotType.PERIOD, "P1", 840, 900)), // 14:00-15:00
                DayOfWeekCode.TUE to listOf(PeriodSlot("t1", SlotType.PERIOD, "P1", 840, 900))
            )
        )

        val sharedTeacher = TeacherProfile(
            id = "t_shared",
            name = "Vikram Rao",
            subjects = listOf("Science"),
            schoolIds = listOf("sch_morning", "sch_overlap", "sch_afternoon")
        )

        // Case 1: Assigned in morningSchool + overlappingSchool -> overlap warning on Mon
        val warningsOverlap = Phase4Validator.evaluateAllWarnings(
            teachers = listOf(sharedTeacher),
            periodStructuresBySchool = mapOf("sch_morning" to morningSchool, "sch_overlap" to overlappingSchool),
            assignments = listOf(
                ClassSectionAssignment("a1", "sch_morning", "7th", "A", subjectAssignments = listOf(SubjectTeacherAssignment("Science", "t_shared", 2))),
                ClassSectionAssignment("a2", "sch_overlap", "8th", "A", subjectAssignments = listOf(SubjectTeacherAssignment("Science", "t_shared", 2)))
            ),
            schoolNamesById = mapOf("sch_morning" to "North Campus", "sch_overlap" to "South Campus")
        )
        assertTrue(warningsOverlap.any { it.type == AssignmentWarningType.CROSS_SCHOOL_TIME_OVERLAP && it.teacherId == "t_shared" })

        // Case 2: Assigned in morningSchool + afternoonSchool -> NO overlap warning
        val warningsClean = Phase4Validator.evaluateAllWarnings(
            teachers = listOf(sharedTeacher),
            periodStructuresBySchool = mapOf("sch_morning" to morningSchool, "sch_afternoon" to afternoonSchool),
            assignments = listOf(
                ClassSectionAssignment("a1", "sch_morning", "7th", "A", subjectAssignments = listOf(SubjectTeacherAssignment("Science", "t_shared", 2))),
                ClassSectionAssignment("a3", "sch_afternoon", "9th", "A", subjectAssignments = listOf(SubjectTeacherAssignment("Science", "t_shared", 2)))
            ),
            schoolNamesById = mapOf("sch_morning" to "North Campus", "sch_afternoon" to "Evening Wing")
        )
        assertFalse(warningsClean.any { it.type == AssignmentWarningType.CROSS_SCHOOL_TIME_OVERLAP })

        // Case 3: Overlap only on Monday, but teacher's weeklyOffDays includes Monday -> NO overlap warning
        val offMondayTeacher = sharedTeacher.copy(weeklyOffDays = listOf(DayOfWeekCode.MON))
        val warningsOffDay = Phase4Validator.evaluateAllWarnings(
            teachers = listOf(offMondayTeacher),
            periodStructuresBySchool = mapOf("sch_morning" to morningSchool, "sch_overlap" to overlappingSchool),
            assignments = listOf(
                ClassSectionAssignment("a1", "sch_morning", "7th", "A", subjectAssignments = listOf(SubjectTeacherAssignment("Science", "t_shared", 2))),
                ClassSectionAssignment("a2", "sch_overlap", "8th", "A", subjectAssignments = listOf(SubjectTeacherAssignment("Science", "t_shared", 2)))
            ),
            schoolNamesById = mapOf("sch_morning" to "North Campus", "sch_overlap" to "South Campus")
        )
        assertFalse(warningsOffDay.any { it.type == AssignmentWarningType.CROSS_SCHOOL_TIME_OVERLAP })
    }

    // -------------------------------------------------------------------------
    // 3. Teacher Deletion Guard & Workload Summary
    // -------------------------------------------------------------------------

    @Test
    fun canDeleteTeacher_blocksWhenAssignedSubjectOrClassTeacher_andAllowsWhenUnassigned() {
        val assignments = listOf(
            ClassSectionAssignment(
                id = "a1",
                schoolId = "sch_1",
                className = "4th",
                section = "A",
                classTeacherId = "t_ct",
                subjectAssignments = listOf(
                    SubjectTeacherAssignment("Hindi", "t_sub", 5),
                    SubjectTeacherAssignment("Art", "t_zero", 0)
                )
            )
        )
        assertNotNull(Phase4Validator.canDeleteTeacher("t_sub", assignments))
        assertNotNull(Phase4Validator.canDeleteTeacher("t_ct", assignments))
        assertNull(Phase4Validator.canDeleteTeacher("t_zero", assignments))
        assertNull(Phase4Validator.canDeleteTeacher("t_free", assignments))
    }

    @Test
    fun buildTeacherWorkloads_aggregatesAcrossSchoolsAndIncludesClassTeacherRoles() {
        val t1 = TeacherProfile(
            id = "t1",
            name = "Priya Menon",
            subjects = listOf("English"),
            schoolIds = listOf("sch_1", "sch_2"),
            maxPeriodsPerDay = 6
        )
        val assignments = listOf(
            ClassSectionAssignment(
                id = "a1",
                schoolId = "sch_1",
                className = "5th",
                section = "A",
                classTeacherId = "t1",
                subjectAssignments = listOf(SubjectTeacherAssignment("English", "t1", 6))
            ),
            ClassSectionAssignment(
                id = "a2",
                schoolId = "sch_2",
                className = "6th",
                section = "B",
                subjectAssignments = listOf(SubjectTeacherAssignment("English", "t1", 5))
            )
        )
        val workloads = Phase4Validator.buildTeacherWorkloads(
            teachers = listOf(t1),
            periodStructuresBySchool = emptyMap(),
            assignments = assignments,
            schoolNamesById = mapOf("sch_1" to "Primary Wing", "sch_2" to "Middle Wing")
        )
        assertEquals(1, workloads.size)
        val summary = workloads.first()
        assertEquals(11, summary.totalPeriodsPerWeek)
        assertEquals(2, summary.assignments.size)
        assertEquals(listOf("Primary Wing" to "5th-A"), summary.classTeacherOf)
        assertTrue(summary.hasAnyAssignment)
    }

    // -------------------------------------------------------------------------
    // 4. Module Access Matrix Enforcement ("Teachers and assignments")
    // -------------------------------------------------------------------------

    @Test
    fun teachersAndAssignmentsModule_respectsNoneViewEditAccessLevels() {
        assertEquals("Teachers and assignments", AppModule.TEACHERS.label)

        val noAccess = MemberAccessProfile(
            uid = "u_none",
            role = OrgRole.TEACHER,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions(teachers = AccessLevel.NONE)
        )
        val viewOnly = MemberAccessProfile(
            uid = "u_view",
            role = OrgRole.OFFICE_OPERATOR,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions(teachers = AccessLevel.VIEW)
        )
        val canEdit = MemberAccessProfile(
            uid = "u_edit",
            role = OrgRole.OFFICE_OPERATOR,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions(teachers = AccessLevel.EDIT)
        )

        assertFalse(OrgAccessEvaluator.canViewModule(noAccess, AppModule.TEACHERS))
        assertFalse(OrgAccessEvaluator.canEditModule(noAccess, AppModule.TEACHERS))

        assertTrue(OrgAccessEvaluator.canViewModule(viewOnly, AppModule.TEACHERS))
        assertFalse(OrgAccessEvaluator.canEditModule(viewOnly, AppModule.TEACHERS))

        assertTrue(OrgAccessEvaluator.canViewModule(canEdit, AppModule.TEACHERS))
        assertTrue(OrgAccessEvaluator.canEditModule(canEdit, AppModule.TEACHERS))
    }
}
