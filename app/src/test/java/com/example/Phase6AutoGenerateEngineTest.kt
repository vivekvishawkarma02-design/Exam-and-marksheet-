package com.example

import com.example.domain.AutoGenerateConfig
import com.example.domain.AutoGenerateEngine
import com.example.domain.AutoGenerateScopeMode
import com.example.domain.ClassSectionAssignment
import com.example.domain.DayOfWeekCode
import com.example.domain.PeriodSlot
import com.example.domain.Phase4Validator
import com.example.domain.PreferredTimeOfDay
import com.example.domain.SchoolPeriodStructure
import com.example.domain.SlotType
import com.example.domain.SubjectRule
import com.example.domain.SubjectTeacherAssignment
import com.example.domain.TeacherProfile
import com.example.domain.TimetableCell
import com.example.domain.TimetableEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase6AutoGenerateEngineTest {

    private fun simpleStructure(schoolId: String, startHour: Int = 8, startMin: Int = 0, periods: Int = 6): SchoolPeriodStructure {
        val days = listOf(DayOfWeekCode.MON, DayOfWeekCode.TUE, DayOfWeekCode.WED, DayOfWeekCode.THU, DayOfWeekCode.FRI)
        val slotsByDay = days.associateWith { day ->
            val list = mutableListOf<PeriodSlot>()
            var cur = startHour * 60 + startMin
            for (p in 1..periods) {
                list.add(
                    PeriodSlot(
                        id = "${day.wireValue}_p$p",
                        type = SlotType.PERIOD,
                        label = "Period $p",
                        startMinutes = cur,
                        endMinutes = cur + 40
                    )
                )
                cur += 40
                if (p == 3) {
                    list.add(
                        PeriodSlot(
                            id = "${day.wireValue}_lunch",
                            type = SlotType.LUNCH,
                            label = "Lunch",
                            startMinutes = cur,
                            endMinutes = cur + 30
                        )
                    )
                    cur += 30
                }
            }
            list
        }
        return SchoolPeriodStructure(schoolId = schoolId, workingDays = days, slotsByDay = slotsByDay)
    }

    @Test
    fun crossSchoolRealClockOverlapHardRuleIsNeverBroken() {
        // School A starts at 08:00 (P1 = 08:00..08:40)
        // School B starts at 08:20 (P1 = 08:20..09:00) -> overlaps School A P1 and P2!
        val structA = simpleStructure("sch_a", startHour = 8, startMin = 0, periods = 4)
        val structB = simpleStructure("sch_b", startHour = 8, startMin = 20, periods = 4)

        val sharedTeacher = TeacherProfile(
            id = "t_shared",
            name = "Shared Teacher",
            subjects = listOf("MATH"),
            schoolIds = listOf("sch_a", "sch_b"),
            maxPeriodsPerDay = 6,
            maxConsecutivePeriods = 3,
            active = true
        )

        // Shared teacher is ALREADY teaching in School B at MON P1 (08:20..09:00) and TUE P1 (08:20..09:00)
        val existingInSchoolB = listOf(
            TimetableCell(
                id = TimetableEngine.makeCellDocId("sch_b", "2026-2027", "6th", "A", DayOfWeekCode.MON, "MON_p1"),
                schoolId = "sch_b",
                session = "2026-2027",
                className = "6th",
                section = "A",
                day = DayOfWeekCode.MON,
                slotId = "MON_p1",
                subjectName = "MATH",
                teacherId = "t_shared",
                teacherName = "Shared Teacher",
                locked = true
            ),
            TimetableCell(
                id = TimetableEngine.makeCellDocId("sch_b", "2026-2027", "6th", "A", DayOfWeekCode.TUE, "TUE_p1"),
                schoolId = "sch_b",
                session = "2026-2027",
                className = "6th",
                section = "A",
                day = DayOfWeekCode.TUE,
                slotId = "TUE_p1",
                subjectName = "MATH",
                teacherId = "t_shared",
                teacherName = "Shared Teacher",
                locked = true
            )
        )

        val assignments = listOf(
            ClassSectionAssignment(
                id = "ca_a_5A",
                schoolId = "sch_a",
                className = "5th",
                section = "A",
                subjectAssignments = listOf(
                    SubjectTeacherAssignment("MATH", "t_shared", periodsPerWeek = 5)
                )
            )
        )

        val result = AutoGenerateEngine.generateTimetable(
            config = AutoGenerateConfig(
                scopeMode = AutoGenerateScopeMode.WHOLE_SCHOOL,
                schoolId = "sch_a",
                session = "2026-2027",
                seed = 42L
            ),
            existingCellsInSession = existingInSchoolB,
            teachers = listOf(sharedTeacher),
            periodStructuresBySchool = mapOf("sch_a" to structA, "sch_b" to structB),
            classAssignments = assignments,
            subjectRules = emptyList()
        )

        assertEquals(5, result.generatedCells.size)
        // Verify ZERO clashes across BOTH schools using Phase 5's authoritative clash detector!
        val combined = existingInSchoolB + result.generatedCells
        val clashes = TimetableEngine.detectAllClashes(
            cellsInSession = combined,
            teachers = listOf(sharedTeacher),
            periodStructuresBySchool = mapOf("sch_a" to structA, "sch_b" to structB),
            schoolNamesById = mapOf("sch_a" to "School A", "sch_b" to "School B")
        )
        assertTrue("Expected zero cross-school clashes, got: $clashes", clashes.isEmpty())

        // Specifically verify MON_p1 (08:00..08:40) and MON_p2 (08:40..09:20) were NOT assigned to sharedTeacher
        val monSlots = result.generatedCells.filter { it.day == DayOfWeekCode.MON }.map { it.slotId }
        assertFalse("MON_p1 overlaps School B 08:20..09:00", "MON_p1" in monSlots)
        assertFalse("MON_p2 overlaps School B 08:20..09:00", "MON_p2" in monSlots)
    }

    @Test
    fun lockedCellsAreNeverChangedAndSameSeedIsDeterministic() {
        val struct = simpleStructure("sch_1", periods = 6)
        val t1 = TeacherProfile("t1", "Teacher 1", subjects = listOf("MATH", "SCI"), schoolIds = listOf("sch_1"))
        val t2 = TeacherProfile("t2", "Teacher 2", subjects = listOf("ENG", "HINDI"), schoolIds = listOf("sch_1"))

        val lockedCell = TimetableCell(
            id = TimetableEngine.makeCellDocId("sch_1", "2026-2027", "5th", "A", DayOfWeekCode.MON, "MON_p1"),
            schoolId = "sch_1",
            session = "2026-2027",
            className = "5th",
            section = "A",
            day = DayOfWeekCode.MON,
            slotId = "MON_p1",
            subjectName = "MATH",
            teacherId = "t1",
            teacherName = "Teacher 1",
            locked = true
        )
        val unlockedExisting = TimetableCell(
            id = TimetableEngine.makeCellDocId("sch_1", "2026-2027", "5th", "A", DayOfWeekCode.FRI, "FRI_p6"),
            schoolId = "sch_1",
            session = "2026-2027",
            className = "5th",
            section = "A",
            day = DayOfWeekCode.FRI,
            slotId = "FRI_p6",
            subjectName = "OLD_SUBJ",
            teacherId = "t2",
            teacherName = "Teacher 2",
            locked = false
        )

        val assignment = ClassSectionAssignment(
            id = "ca_5A",
            schoolId = "sch_1",
            className = "5th",
            section = "A",
            subjectAssignments = listOf(
                SubjectTeacherAssignment("MATH", "t1", 5),
                SubjectTeacherAssignment("SCI", "t1", 4),
                SubjectTeacherAssignment("ENG", "t2", 5),
                SubjectTeacherAssignment("HINDI", "t2", 4)
            )
        )

        val cfg = AutoGenerateConfig(
            scopeMode = AutoGenerateScopeMode.SINGLE_CLASS_SECTION,
            schoolId = "sch_1",
            className = "5th",
            section = "A",
            session = "2026-2027",
            keepExistingUnlockedCells = false,
            seed = 123456L
        )

        val run1 = AutoGenerateEngine.generateTimetable(
            config = cfg,
            existingCellsInSession = listOf(lockedCell, unlockedExisting),
            teachers = listOf(t1, t2),
            periodStructuresBySchool = mapOf("sch_1" to struct),
            classAssignments = listOf(assignment),
            subjectRules = listOf(
                SubjectRule("r1", "sch_1", "MATH", PreferredTimeOfDay.MORNING, allowDoublePeriod = false, maxOncePerDay = true)
            )
        )
        val run2 = AutoGenerateEngine.generateTimetable(
            config = cfg,
            existingCellsInSession = listOf(lockedCell, unlockedExisting),
            teachers = listOf(t1, t2),
            periodStructuresBySchool = mapOf("sch_1" to struct),
            classAssignments = listOf(assignment),
            subjectRules = listOf(
                SubjectRule("r1", "sch_1", "MATH", PreferredTimeOfDay.MORNING, allowDoublePeriod = false, maxOncePerDay = true)
            )
        )

        // Exact deterministic reproducibility for identical seed
        assertEquals(run1.generatedCells, run2.generatedCells)
        assertEquals(run1.softPenaltyScore, run2.softPenaltyScore)

        // Locked cell untouched & not overwritten in generatedCells
        assertTrue(run1.untouchedLockedOrKeptCells.any { it.id == lockedCell.id && it.locked })
        assertTrue(run1.generatedCells.none { it.id == lockedCell.id })
        assertEquals(18, run1.totalPlacedPeriods)
        assertTrue(run1.unplacedItems.isEmpty())

        // Undo snapshot captures BOTH previously existing cells (locked + unlockedExisting)
        assertEquals(2, run1.undoSnapshot.previousCells.size)
        assertTrue(run1.undoSnapshot.previousCells.any { it.id == unlockedExisting.id && it.subjectName == "OLD_SUBJ" })
        // Simulating undo restores exact prior map:
        val afterApplyMap = (run1.untouchedLockedOrKeptCells + run1.generatedCells).associateBy { it.id }.toMutableMap()
        assertNotEquals(2, afterApplyMap.size)
        run1.undoSnapshot.affectedCellIds.forEach { afterApplyMap.remove(it) }
        run1.undoSnapshot.previousCells.forEach { afterApplyMap[it.id] = it }
        assertEquals(setOf(lockedCell.id, unlockedExisting.id), afterApplyMap.keys)
        assertEquals("OLD_SUBJ", afterApplyMap[unlockedExisting.id]?.subjectName)
    }

    @Test
    fun impossibleDemandProducesPartialResultAndClearUnplacedReport() {
        val struct = simpleStructure("sch_1", periods = 4) // 5 days * 4 = 20 slots total
        val overloadedTeacher = TeacherProfile(
            id = "t_over",
            name = "Ms. Kapoor",
            subjects = listOf("MATH"),
            schoolIds = listOf("sch_1"),
            maxPeriodsPerDay = 2, // max 10 periods/week across 5 days!
            weeklyOffDays = listOf(DayOfWeekCode.FRI), // only 4 working days -> max 8 periods/week!
            active = true
        )
        val inactiveTeacher = TeacherProfile(
            id = "t_off",
            name = "Mr. Inactive",
            subjects = listOf("ART"),
            schoolIds = listOf("sch_1"),
            active = false
        )

        val assign = ClassSectionAssignment(
            id = "ca_1",
            schoolId = "sch_1",
            className = "5th",
            section = "A",
            subjectAssignments = listOf(
                SubjectTeacherAssignment("MATH", "t_over", periodsPerWeek = 12), // impossible: teacher max is 8
                SubjectTeacherAssignment("ART", "t_off", periodsPerWeek = 2) // impossible: inactive
            )
        )

        val res = AutoGenerateEngine.generateTimetable(
            config = AutoGenerateConfig(
                scopeMode = AutoGenerateScopeMode.WHOLE_SCHOOL,
                schoolId = "sch_1",
                session = "2026-2027",
                seed = 99L,
                timeLimitSeconds = 5
            ),
            existingCellsInSession = emptyList(),
            teachers = listOf(overloadedTeacher, inactiveTeacher),
            periodStructuresBySchool = mapOf("sch_1" to struct),
            classAssignments = listOf(assign),
            subjectRules = emptyList()
        )

        // Partial result keeps the 8 valid MATH placements
        assertEquals(8, res.generatedCells.size)
        assertEquals(2, res.unplacedItems.size)
        val mathUnplaced = res.unplacedItems.first { it.subjectName == "MATH" }
        assertEquals(4, mathUnplaced.missingPeriods)
        assertTrue(mathUnplaced.reason.contains("daily limit") || mathUnplaced.reason.contains("off-day"))

        val artUnplaced = res.unplacedItems.first { it.subjectName == "ART" }
        assertEquals(2, artUnplaced.missingPeriods)
        assertTrue(artUnplaced.reason.contains("Inactive", ignoreCase = true))

        // Zero hard rule clashes in the partial result
        val clashes = TimetableEngine.detectAllClashes(
            cellsInSession = res.generatedCells,
            teachers = listOf(overloadedTeacher, inactiveTeacher),
            periodStructuresBySchool = mapOf("sch_1" to struct),
            schoolNamesById = mapOf("sch_1" to "School 1")
        )
        assertTrue(clashes.isEmpty())
    }

    @Test
    fun benchmarkThreeClassesThreeSectionsTenTeachersRunsFastWithZeroClashes() {
        // 3 classes x 3 sections = 9 class-sections, 6 days (45 periods/week default or 30 periods/week), 10 teachers
        val struct = Phase4Validator.buildDefaultPeriodStructure("sch_bench") // Mon..Fri 8 periods + Sat 5 = 45 periods
        val subjects = listOf("MATH", "ENGLISH", "SCIENCE", "HINDI", "SST", "COMPUTER", "GK", "ART")
        val teachers = (1..10).map { i ->
            TeacherProfile(
                id = "t_$i",
                name = "Teacher $i",
                subjects = listOf(subjects[(i - 1) % subjects.size], subjects[i % subjects.size]),
                schoolIds = listOf("sch_bench"),
                maxPeriodsPerDay = 7,
                maxConsecutivePeriods = 3,
                weeklyOffDays = emptyList(),
                active = true
            )
        }

        // 9 class-sections * 35 periods = 315 periods across 10 teachers (~31.5 periods/teacher, well within 42 limit)
        val classes = listOf("6th", "7th", "8th")
        val sections = listOf("A", "B", "C")
        val assignments = mutableListOf<ClassSectionAssignment>()
        var idx = 0
        for (cls in classes) {
            for (sec in sections) {
                assignments.add(
                    ClassSectionAssignment(
                        id = "ca_${cls}_$sec",
                        schoolId = "sch_bench",
                        className = cls,
                        section = sec,
                        subjectAssignments = listOf(
                            SubjectTeacherAssignment("MATH", "t_${(idx % 2) + 1}", 6),
                            SubjectTeacherAssignment("ENGLISH", "t_${(idx % 2) + 3}", 6),
                            SubjectTeacherAssignment("SCIENCE", "t_${(idx % 2) + 5}", 6),
                            SubjectTeacherAssignment("HINDI", "t_${(idx % 2) + 7}", 5),
                            SubjectTeacherAssignment("SST", "t_${(idx % 2) + 9}", 5),
                            SubjectTeacherAssignment("COMPUTER", "t_${((idx + 1) % 5) + 1}", 4),
                            SubjectTeacherAssignment("ART", "t_${((idx + 2) % 5) + 6}", 3)
                        )
                    )
                )
                idx++
            }
        }

        val rules = listOf(
            SubjectRule("r_math", "sch_bench", "MATH", PreferredTimeOfDay.MORNING, allowDoublePeriod = false, maxOncePerDay = true),
            SubjectRule("r_sci", "sch_bench", "SCIENCE", PreferredTimeOfDay.MORNING, allowDoublePeriod = true, maxOncePerDay = false),
            SubjectRule("r_art", "sch_bench", "ART", PreferredTimeOfDay.AFTERNOON, allowDoublePeriod = false, maxOncePerDay = true)
        )

        val result = AutoGenerateEngine.generateTimetable(
            config = AutoGenerateConfig(
                scopeMode = AutoGenerateScopeMode.WHOLE_SCHOOL,
                schoolId = "sch_bench",
                session = "2026-2027",
                timeLimitSeconds = 15,
                seed = 20261123L
            ),
            existingCellsInSession = emptyList(),
            teachers = teachers,
            periodStructuresBySchool = mapOf("sch_bench" to struct),
            classAssignments = assignments,
            subjectRules = rules
        )

        println("Phase 6 Benchmark (3 classes x 3 sections = 9 sections, 10 teachers, 315 periods): placed=${result.totalPlacedPeriods}/${result.totalRequestedPeriods}, elapsed=${result.elapsedMs}ms, passes=${result.restartsCompleted}, penalty=${result.softPenaltyScore}")
        assertEquals(315, result.totalRequestedPeriods)
        assertEquals("Expected all 315 periods placed; unplaced=${result.unplacedItems}", 315, result.totalPlacedPeriods)
        assertTrue("Expected solver to finish in under 5 seconds, took ${result.elapsedMs}ms", result.elapsedMs < 5_000L)

        val clashes = TimetableEngine.detectAllClashes(
            cellsInSession = result.generatedCells,
            teachers = teachers,
            periodStructuresBySchool = mapOf("sch_bench" to struct),
            schoolNamesById = mapOf("sch_bench" to "Benchmark School")
        )
        assertTrue("Generated timetable must have 0 hard clashes: $clashes", clashes.isEmpty())
    }
}
