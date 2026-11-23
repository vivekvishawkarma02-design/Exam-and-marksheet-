package com.example

import com.example.domain.ClassSectionAssignment
import com.example.domain.DayOfWeekCode
import com.example.domain.LeaveReason
import com.example.domain.LeaveStatus
import com.example.domain.LocalPendingPhase6Edit
import com.example.domain.PeriodSlot
import com.example.domain.Phase6DateUtils
import com.example.domain.SchoolPeriodStructure
import com.example.domain.SlotType
import com.example.domain.SubjectTeacherAssignment
import com.example.domain.SubstitutionEngine
import com.example.domain.SubstitutionRecord
import com.example.domain.TeacherLeaveRecord
import com.example.domain.TeacherProfile
import com.example.domain.TimetableCell
import com.example.domain.TimetableEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6SubstitutionEngineTest {

    // 2026-11-23 is a Monday!
    private val testMonday = "2026-11-23"

    private fun buildStructure(schoolId: String): SchoolPeriodStructure {
        val slots = listOf(
            PeriodSlot("MON_p1", SlotType.PERIOD, "Period 1", 480, 520), // 08:00-08:40
            PeriodSlot("MON_p2", SlotType.PERIOD, "Period 2", 520, 560), // 08:40-09:20
            PeriodSlot("MON_p3", SlotType.PERIOD, "Period 3", 560, 600)  // 09:20-10:00
        )
        return SchoolPeriodStructure(
            schoolId = schoolId,
            workingDays = listOf(DayOfWeekCode.MON),
            slotsByDay = mapOf(DayOfWeekCode.MON to slots)
        )
    }

    @Test
    fun dateUtilsWorkWithoutJavaTime() {
        assertTrue(Phase6DateUtils.isValidIsoDate("2026-11-23"))
        assertFalse(Phase6DateUtils.isValidIsoDate("2026-02-30"))
        assertFalse(Phase6DateUtils.isValidIsoDate("23-11-2026"))
        assertEquals(DayOfWeekCode.MON, Phase6DateUtils.dayOfWeekForDate("2026-11-23"))
        assertEquals("2026-11-24", Phase6DateUtils.addDays("2026-11-23", 1))
        assertEquals("2026-11", Phase6DateUtils.monthPrefix("2026-11-23"))
        assertEquals("2026-11-23" to "2026-11-29", Phase6DateUtils.weekDateRangeFor("2026-11-25"))
        assertEquals(3, Phase6DateUtils.enumerateDateRange("2026-11-23", "2026-11-25").size)
    }

    @Test
    fun candidateRankingEnforcesExactFourTierOrderAndExcludesIneligibleTeachers() {
        val struct = buildStructure("sch_1")
        val absentTeacher = TeacherProfile("t_abs", "Absent Sharma", subjects = listOf("MATH"), schoolIds = listOf("sch_1"))
        val tBest = TeacherProfile("t_best", "Best Match", subjects = listOf("MATH"), schoolIds = listOf("sch_1"), maxPeriodsPerDay = 6)
        val tSameSubjMoreSubs = TeacherProfile("t_subj_hi", "Same Subj High Monthly", subjects = listOf("MATH"), schoolIds = listOf("sch_1"), maxPeriodsPerDay = 6)
        val tSameClassNotSubj = TeacherProfile("t_cls_only", "Same Class Only", subjects = listOf("ENG"), schoolIds = listOf("sch_1"), maxPeriodsPerDay = 6)
        val tLowDayLoad = TeacherProfile("t_free_low", "Other Free Low Load", subjects = listOf("ART"), schoolIds = listOf("sch_1"), maxPeriodsPerDay = 6)
        val tHighDayLoad = TeacherProfile("t_free_high", "Other Free High Load", subjects = listOf("ART"), schoolIds = listOf("sch_1"), maxPeriodsPerDay = 6)

        // Ineligible teachers (must be excluded from recommended and appear only in manualOverrideCandidates)
        val tBusy = TeacherProfile("t_busy", "Busy At P1", subjects = listOf("MATH"), schoolIds = listOf("sch_1"))
        val tAlsoAbsent = TeacherProfile("t_abs2", "Also Absent", subjects = listOf("MATH"), schoolIds = listOf("sch_1"))
        val tInactive = TeacherProfile("t_inact", "Inactive Teacher", subjects = listOf("MATH"), schoolIds = listOf("sch_1"), active = false)
        val tOverLimit = TeacherProfile("t_limit", "At Daily Limit", subjects = listOf("MATH"), schoolIds = listOf("sch_1"), maxPeriodsPerDay = 1)
        val tOffDay = TeacherProfile("t_off", "Monday Off", subjects = listOf("MATH"), schoolIds = listOf("sch_1"), weeklyOffDays = listOf(DayOfWeekCode.MON))

        val teachers = listOf(
            absentTeacher, tBest, tSameSubjMoreSubs, tSameClassNotSubj,
            tLowDayLoad, tHighDayLoad, tBusy, tAlsoAbsent, tInactive, tOverLimit, tOffDay
        )

        val cells = listOf(
            // Absent teacher's period needing cover: 5th-A MON P1 MATH
            TimetableCell("c1", "sch_1", "2026-2027", "5th", "A", DayOfWeekCode.MON, "MON_p1", "MATH", "t_abs", "Absent Sharma"),
            // tBusy is busy at MON P1 in another class
            TimetableCell("c2", "sch_1", "2026-2027", "6th", "A", DayOfWeekCode.MON, "MON_p1", "MATH", "t_busy", "Busy At P1"),
            // tOverLimit already has 1 period at P2 (maxPeriodsPerDay = 1)
            TimetableCell("c3", "sch_1", "2026-2027", "6th", "A", DayOfWeekCode.MON, "MON_p2", "MATH", "t_limit", "At Daily Limit"),
            // tHighDayLoad has 2 periods later in the day vs tLowDayLoad has 0
            TimetableCell("c4", "sch_1", "2026-2027", "6th", "A", DayOfWeekCode.MON, "MON_p2", "ART", "t_free_high", "Other Free High Load"),
            TimetableCell("c5", "sch_1", "2026-2027", "6th", "A", DayOfWeekCode.MON, "MON_p3", "ART", "t_free_high", "Other Free High Load")
        )
        val originalCellsCopy = cells.toList()

        val assignments = listOf(
            ClassSectionAssignment(
                id = "ca_5A",
                schoolId = "sch_1",
                className = "5th",
                section = "A",
                subjectAssignments = listOf(
                    SubjectTeacherAssignment("MATH", "t_abs", 5),
                    SubjectTeacherAssignment("ENG", "t_cls_only", 5),
                    SubjectTeacherAssignment("MATH_LAB", "t_best", 2) // t_best also teaches class 5th-A!
                )
            )
        )

        val leaves = listOf(
            TeacherLeaveRecord("lv1", "t_abs", "Absent Sharma", testMonday, testMonday, LeaveReason.SICK, status = LeaveStatus.APPROVED),
            TeacherLeaveRecord("lv2", "t_abs2", "Also Absent", testMonday, testMonday, LeaveReason.CASUAL_LEAVE, status = LeaveStatus.APPROVED)
        )

        // Prior monthly substitutions in 2026-11: t_subj_hi already did 3 subs this month
        val existingSubs = listOf(
            SubstitutionRecord("s1", "2026-11-10", DayOfWeekCode.TUE, "sch_1", "2026-2027", "7th", "A", "TUE_p1", "Period 1", 480, 520, "MATH", "t_abs", "Absent", "lv0", "t_subj_hi", "Same Subj High Monthly"),
            SubstitutionRecord("s2", "2026-11-12", DayOfWeekCode.THU, "sch_1", "2026-2027", "7th", "A", "THU_p1", "Period 1", 480, 520, "MATH", "t_abs", "Absent", "lv0", "t_subj_hi", "Same Subj High Monthly"),
            SubstitutionRecord("s3", "2026-11-14", DayOfWeekCode.SAT, "sch_1", "2026-2027", "7th", "A", "SAT_p1", "Period 1", 480, 520, "MATH", "t_abs", "Absent", "lv0", "t_subj_hi", "Same Subj High Monthly")
        )

        val needs = SubstitutionEngine.computeDailySubstitutionNeeds(
            date = testMonday,
            session = "2026-2027",
            teachers = teachers,
            leaves = leaves,
            timetableCellsInSession = cells,
            substitutions = existingSubs,
            periodStructuresBySchool = mapOf("sch_1" to struct),
            classAssignments = assignments
        )

        assertEquals(1, needs.size)
        val need = needs.first()
        assertTrue(need.needsSubstitute)

        // Check strict ranking order of recommended candidates:
        // 1. t_best (same subject + same class)
        // 2. t_subj_hi (same subject, not same class)
        // 3. t_cls_only (not same subject, teaches same class)
        // 4. t_free_low (neither, 0 load today)
        // 5. t_free_high (neither, 2 load today)
        val rankedIds = need.recommendedCandidates.map { it.teacher.id }
        assertEquals(
            listOf("t_best", "t_subj_hi", "t_cls_only", "t_free_low", "t_free_high"),
            rankedIds
        )

        // Check ALL 5 ineligible teachers were excluded from recommended & placed in manualOverrideCandidates with reasons
        val overrideIds = need.manualOverrideCandidates.map { it.teacher.id }.toSet()
        assertEquals(setOf("t_busy", "t_abs2", "t_inact", "t_limit", "t_off"), overrideIds)
        assertTrue(need.manualOverrideCandidates.all { it.overrideRequired && it.overrideWarningReason.isNotBlank() })

        // Verify weekly timetable cells were NEVER mutated
        assertEquals(originalCellsCopy, cells)
    }

    @Test
    fun substituteLaterBecomesAbsentReflagsPeriodAsNeedsSubstituteAndHalfDayScopesPeriods() {
        val struct = buildStructure("sch_1")
        val tA = TeacherProfile("t_a", "Teacher A", subjects = listOf("MATH"), schoolIds = listOf("sch_1"))
        val tSub = TeacherProfile("t_sub", "Teacher Sub", subjects = listOf("MATH"), schoolIds = listOf("sch_1"))
        val tBackup = TeacherProfile("t_bak", "Teacher Backup", subjects = listOf("MATH"), schoolIds = listOf("sch_1"))

        val cells = listOf(
            TimetableCell("c1", "sch_1", "2026-2027", "5th", "A", DayOfWeekCode.MON, "MON_p1", "MATH", "t_a", "Teacher A"),
            TimetableCell("c2", "sch_1", "2026-2027", "5th", "A", DayOfWeekCode.MON, "MON_p2", "MATH", "t_a", "Teacher A")
        )

        // Teacher A has half-day leave ONLY for Period 1!
        val leaveA = TeacherLeaveRecord(
            id = "lv_a",
            teacherId = "t_a",
            teacherName = "Teacher A",
            startDate = testMonday,
            endDate = testMonday,
            reasonType = LeaveReason.DUTY,
            halfDay = true,
            halfDaySlotIds = listOf("Period 1"),
            status = LeaveStatus.APPROVED
        )

        // Sub was already assigned to cover MON_p1
        val subId = SubstitutionRecord.makeSubstitutionDocId(testMonday, "sch_1", "5th", "A", "MON_p1")
        val existingSub = SubstitutionRecord(
            id = subId,
            date = testMonday,
            day = DayOfWeekCode.MON,
            schoolId = "sch_1",
            session = "2026-2027",
            className = "5th",
            section = "A",
            slotId = "MON_p1",
            slotLabel = "Period 1",
            startMinutes = 480,
            endMinutes = 520,
            subjectName = "MATH",
            absentTeacherId = "t_a",
            absentTeacherName = "Teacher A",
            leaveId = "lv_a",
            substituteTeacherId = "t_sub",
            substituteTeacherName = "Teacher Sub"
        )

        // Before t_sub takes leave: 1 period affected (half-day excludes P2!), already covered
        val beforeNeeds = SubstitutionEngine.computeDailySubstitutionNeeds(
            date = testMonday,
            session = "2026-2027",
            teachers = listOf(tA, tSub, tBackup),
            leaves = listOf(leaveA),
            timetableCellsInSession = cells,
            substitutions = listOf(existingSub),
            periodStructuresBySchool = mapOf("sch_1" to struct),
            classAssignments = emptyList()
        )
        assertEquals(1, beforeNeeds.size)
        assertEquals("MON_p1", beforeNeeds.first().slotId)
        assertFalse(beforeNeeds.first().needsSubstitute)

        // Now t_sub ALSO becomes absent on the same Monday!
        val leaveSub = TeacherLeaveRecord(
            id = "lv_sub",
            teacherId = "t_sub",
            teacherName = "Teacher Sub",
            startDate = testMonday,
            endDate = testMonday,
            reasonType = LeaveReason.SICK,
            status = LeaveStatus.APPROVED
        )
        val afterNeeds = SubstitutionEngine.computeDailySubstitutionNeeds(
            date = testMonday,
            session = "2026-2027",
            teachers = listOf(tA, tSub, tBackup),
            leaves = listOf(leaveA, leaveSub),
            timetableCellsInSession = cells,
            substitutions = listOf(existingSub),
            periodStructuresBySchool = mapOf("sch_1" to struct),
            classAssignments = emptyList()
        )
        assertEquals(1, afterNeeds.size)
        assertTrue("Period must be re-flagged as needsSubstitute", afterNeeds.first().needsSubstitute)
        assertTrue(afterNeeds.first().assignedSubstituteAlsoAbsent)
        assertEquals(listOf("t_bak"), afterNeeds.first().recommendedCandidates.map { it.teacher.id })
    }

    @Test
    fun workloadCountsIncludeWeeklyAndMonthlySubstitutionsAndConflictDetectorWorks() {
        val t1 = TeacherProfile("t1", "Teacher One", maxPeriodsPerDay = 6)
        val cells = listOf(
            TimetableCell("c1", "sch_1", "2026-2027", "5th", "A", DayOfWeekCode.MON, "MON_p1", "MATH", "t1", "Teacher One"),
            TimetableCell("c2", "sch_1", "2026-2027", "5th", "A", DayOfWeekCode.TUE, "TUE_p1", "MATH", "t1", "Teacher One")
        )
        val subs = listOf(
            // Same week & same month (2026-11-23 is Monday)
            SubstitutionRecord("s1", "2026-11-24", DayOfWeekCode.TUE, "sch_1", "2026-2027", "6th", "A", "TUE_p2", "P2", 520, 560, "MATH", "t2", "T2", "l1", "t1", "Teacher One"),
            // Earlier in same month, different week
            SubstitutionRecord("s2", "2026-11-05", DayOfWeekCode.THU, "sch_1", "2026-2027", "6th", "A", "THU_p2", "P2", 520, 560, "MATH", "t2", "T2", "l2", "t1", "Teacher One")
        )

        val workloads = SubstitutionEngine.computeTeacherWorkloadsWithSubstitutions(
            referenceDate = testMonday,
            session = "2026-2027",
            teachers = listOf(t1),
            timetableCellsInSession = cells,
            substitutionsInSession = subs
        )
        assertEquals(1, workloads.size)
        val w = workloads.first()
        assertEquals(2, w.regularWeeklyPeriods)
        assertEquals(1, w.substitutionsThisWeek)
        assertEquals(3, w.totalWeeklyLoadWithSubs)
        assertEquals(2, w.substitutionsThisMonth)

        // Concurrent offline edit detection on substitution
        val pending = listOf(
            LocalPendingPhase6Edit(
                kind = "SUB",
                docId = "s1",
                targetSummary = "Sub 6th-A P2",
                localValueSummary = "Teacher Two",
                wasDeleted = false,
                clientEditedAtMs = 1_000L
            )
        )
        val serverSub = subs.first().copy(updatedByUid = "other_uid", updatedByName = "Principal")
        val conflictRes = SubstitutionEngine.detectPhase6Conflicts(
            pendingEdits = pending,
            confirmedLeaves = emptyList(),
            confirmedSubstitutions = listOf(serverSub),
            hasConfirmedServerSnapshot = true,
            currentUid = "my_uid",
            nowMs = 2_000L
        )
        assertEquals(1, conflictRes.detectedConflicts.size)
        assertEquals("Principal", conflictRes.detectedConflicts.first().winnerName)
        assertEquals("Teacher One", conflictRes.detectedConflicts.first().winningSummary)
    }
}
