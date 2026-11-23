package com.example

import com.example.domain.ClassSectionAssignment
import com.example.domain.DayOfWeekCode
import com.example.domain.HardConflictType
import com.example.domain.LocalPendingTimetableEdit
import com.example.domain.PeriodSlot
import com.example.domain.Phase4Validator
import com.example.domain.PreferredTimeOfDay
import com.example.domain.SchoolPeriodStructure
import com.example.domain.SlotType
import com.example.domain.SoftWarningType
import com.example.domain.SubjectTeacherAssignment
import com.example.domain.SubjectRule
import com.example.domain.TeacherProfile
import com.example.domain.TeacherSlotAvailability
import com.example.domain.TimetableCell
import com.example.domain.TimetableEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5TimetableEngineTest {

    private fun makeSchoolStructure(
        schoolId: String,
        monSlots: List<PeriodSlot>
    ): SchoolPeriodStructure {
        val slotsByDay = DayOfWeekCode.defaultWorkingDays.associateWith { day ->
            monSlots.map { s -> s.copy(id = "${day.wireValue.lowercase()}_${s.id.substringAfter('_', s.id)}") }
        }
        return SchoolPeriodStructure(
            schoolId = schoolId,
            workingDays = DayOfWeekCode.defaultWorkingDays,
            slotsByDay = slotsByDay
        )
    }

    private val schoolAStruct = makeSchoolStructure(
        schoolId = "sch_alpha",
        monSlots = listOf(
            PeriodSlot("mon_asm", SlotType.ASSEMBLY, "Assembly", 8 * 60, 8 * 60 + 15),
            PeriodSlot("mon_p1", SlotType.PERIOD, "Period 1", 8 * 60 + 15, 8 * 60 + 55),
            PeriodSlot("mon_p2", SlotType.PERIOD, "Period 2", 8 * 60 + 55, 9 * 60 + 35),
            PeriodSlot("mon_p3", SlotType.PERIOD, "Period 3", 9 * 60 + 35, 10 * 60 + 15),
            PeriodSlot("mon_sb", SlotType.SHORT_BREAK, "Short Break", 10 * 60 + 15, 10 * 60 + 30),
            PeriodSlot("mon_p4", SlotType.PERIOD, "Period 4", 10 * 60 + 30, 11 * 60 + 10),
            PeriodSlot("mon_p5", SlotType.PERIOD, "Period 5", 11 * 60 + 10, 11 * 60 + 50),
            PeriodSlot("mon_lunch", SlotType.LUNCH, "Lunch", 11 * 60 + 50, 12 * 60 + 25),
            PeriodSlot("mon_p6", SlotType.PERIOD, "Period 6", 12 * 60 + 25, 13 * 60 + 5)
        )
    )

    // School B has shifted timings: P1 09:00-09:40 (overlaps School A P2 08:55-09:35 AND P3 09:35-10:15!)
    // and P2 09:40-10:20 (does NOT overlap School A P2 08:55-09:35, touches P1 at 09:40 cleanly)
    private val schoolBStruct = makeSchoolStructure(
        schoolId = "sch_beta",
        monSlots = listOf(
            PeriodSlot("mon_b1", SlotType.PERIOD, "Period 1", 9 * 60, 9 * 60 + 40),
            PeriodSlot("mon_b2", SlotType.PERIOD, "Period 2", 9 * 60 + 40, 10 * 60 + 20),
            PeriodSlot("mon_b3", SlotType.PERIOD, "Period 3", 10 * 60 + 20, 11 * 60)
        )
    )

    private val structures = mapOf(
        "sch_alpha" to schoolAStruct,
        "sch_beta" to schoolBStruct
    )

    private val schoolNames = mapOf(
        "sch_alpha" to "Alpha Public School",
        "sch_beta" to "Beta Academy"
    )

    private val teacherAnil = TeacherProfile(
        id = "t_anil",
        name = "Anil Sharma",
        subjects = listOf("Mathematics", "Physics"),
        schoolIds = listOf("sch_alpha", "sch_beta"),
        maxPeriodsPerDay = 3,
        maxConsecutivePeriods = 2,
        weeklyOffDays = listOf(DayOfWeekCode.SAT),
        active = true
    )

    private val teacherMeera = TeacherProfile(
        id = "t_meera",
        name = "Meera Verma",
        subjects = listOf("Mathematics", "Science"),
        schoolIds = listOf("sch_alpha"),
        maxPeriodsPerDay = 6,
        maxConsecutivePeriods = 3,
        weeklyOffDays = emptyList(),
        active = true
    )

    private val teacherInactive = TeacherProfile(
        id = "t_old",
        name = "Retired Sir",
        subjects = listOf("English"),
        schoolIds = listOf("sch_alpha"),
        active = false
    )

    @Test
    fun `deterministic cell doc id is stable and normalizes section case`() {
        val id1 = TimetableEngine.makeCellDocId("sch_alpha", "2026-2027", "5th", "a", DayOfWeekCode.MON, "mon_p1")
        val id2 = TimetableEngine.makeCellDocId("sch_alpha", "2026-2027", "5th", "A", DayOfWeekCode.MON, "mon_p1")
        assertEquals(id1, id2)
        assertTrue(id1.startsWith("tt_"))
    }

    @Test
    fun `hard conflict blocks cross-school teacher double-booking using real clock times`() {
        val existingCellInAlpha = TimetableCell(
            id = TimetableEngine.makeCellDocId("sch_alpha", "2026-2027", "6th", "A", DayOfWeekCode.MON, "mon_p2"),
            schoolId = "sch_alpha",
            session = "2026-2027",
            className = "6th",
            section = "A",
            day = DayOfWeekCode.MON,
            slotId = "mon_p2", // 08:55-09:35
            subjectName = "Mathematics",
            teacherId = teacherAnil.id,
            teacherName = teacherAnil.name
        )

        // Placing Anil in Beta Academy at mon_b1 (09:00-09:40) overlaps 08:55-09:35 -> BLOCKED
        val overlappingCandidate = TimetableCell(
            id = TimetableEngine.makeCellDocId("sch_beta", "2026-2027", "8th", "B", DayOfWeekCode.MON, "mon_b1"),
            schoolId = "sch_beta",
            session = "2026-2027",
            className = "8th",
            section = "B",
            day = DayOfWeekCode.MON,
            slotId = "mon_b1", // 09:00-09:40
            subjectName = "Physics",
            teacherId = teacherAnil.id,
            teacherName = teacherAnil.name
        )
        val blocked = TimetableEngine.validateCellPlacement(
            candidate = overlappingCandidate,
            existingCellsInSession = listOf(existingCellInAlpha),
            teachers = listOf(teacherAnil, teacherMeera),
            periodStructuresBySchool = structures,
            classAssignments = emptyList(),
            subjectRules = emptyList(),
            schoolNamesById = schoolNames
        )
        assertFalse(blocked.canSave)
        val conflict = blocked.hardConflicts.firstOrNull { it.type == HardConflictType.TEACHER_DOUBLE_BOOKED }
        assertTrue("Expected teacher double-booking conflict", conflict != null)
        assertTrue(conflict!!.message.contains("Anil Sharma"))
        assertTrue(conflict.message.contains("Monday"))
        assertTrue(conflict.message.contains("6th-A"))
        assertTrue(conflict.message.contains("Alpha Public School"))

        // Placing Anil in Beta Academy at mon_b2 (09:40-10:20) does NOT overlap 08:55-09:35 -> ALLOWED
        val nonOverlappingCandidate = overlappingCandidate.copy(
            id = TimetableEngine.makeCellDocId("sch_beta", "2026-2027", "8th", "B", DayOfWeekCode.MON, "mon_b2"),
            slotId = "mon_b2"
        )
        val allowed = TimetableEngine.validateCellPlacement(
            candidate = nonOverlappingCandidate,
            existingCellsInSession = listOf(existingCellInAlpha),
            teachers = listOf(teacherAnil, teacherMeera),
            periodStructuresBySchool = structures,
            classAssignments = emptyList(),
            subjectRules = emptyList(),
            schoolNamesById = schoolNames
        )
        assertTrue("Non-overlapping cross-school slot should be allowed", allowed.canSave)
    }

    @Test
    fun `hard conflicts block weekly off day, inactive teacher, class double-booking, and daily limit`() {
        // 1. Weekly off day (Saturday for Anil)
        val offDayCandidate = TimetableCell(
            id = TimetableEngine.makeCellDocId("sch_alpha", "2026-2027", "5th", "A", DayOfWeekCode.SAT, "sat_p1"),
            schoolId = "sch_alpha",
            session = "2026-2027",
            className = "5th",
            section = "A",
            day = DayOfWeekCode.SAT,
            slotId = "sat_p1",
            subjectName = "Mathematics",
            teacherId = teacherAnil.id,
            teacherName = teacherAnil.name
        )
        val offRes = TimetableEngine.validateCellPlacement(
            candidate = offDayCandidate,
            existingCellsInSession = emptyList(),
            teachers = listOf(teacherAnil),
            periodStructuresBySchool = structures,
            classAssignments = emptyList(),
            subjectRules = emptyList(),
            schoolNamesById = schoolNames
        )
        assertFalse(offRes.canSave)
        assertTrue(offRes.hardConflicts.any { it.type == HardConflictType.TEACHER_OFF_DAY })

        // 2. Inactive teacher
        val inactiveRes = TimetableEngine.validateCellPlacement(
            candidate = offDayCandidate.copy(day = DayOfWeekCode.MON, slotId = "mon_p1", teacherId = teacherInactive.id, teacherName = teacherInactive.name),
            existingCellsInSession = emptyList(),
            teachers = listOf(teacherInactive),
            periodStructuresBySchool = structures,
            classAssignments = emptyList(),
            subjectRules = emptyList(),
            schoolNamesById = schoolNames
        )
        assertFalse(inactiveRes.canSave)
        assertTrue(inactiveRes.hardConflicts.any { it.type == HardConflictType.TEACHER_INACTIVE })

        // 3. Class double-booked when allowReplaceSameCellId = false (or different slot IDs overlapping)
        val existingClassCell = TimetableCell(
            id = "legacy_dup_id",
            schoolId = "sch_alpha",
            session = "2026-2027",
            className = "5th",
            section = "A",
            day = DayOfWeekCode.MON,
            slotId = "mon_p1",
            subjectName = "English",
            teacherId = teacherMeera.id,
            teacherName = teacherMeera.name
        )
        val sameSlotCand = existingClassCell.copy(
            id = TimetableEngine.makeCellDocId("sch_alpha", "2026-2027", "5th", "A", DayOfWeekCode.MON, "mon_p1"),
            subjectName = "Mathematics",
            teacherId = teacherAnil.id,
            teacherName = teacherAnil.name
        )
        val clsRes = TimetableEngine.validateCellPlacement(
            candidate = sameSlotCand,
            existingCellsInSession = listOf(existingClassCell),
            teachers = listOf(teacherAnil, teacherMeera),
            periodStructuresBySchool = structures,
            classAssignments = emptyList(),
            subjectRules = emptyList(),
            schoolNamesById = schoolNames
        )
        assertFalse(clsRes.canSave)
        assertTrue(clsRes.hardConflicts.any { it.type == HardConflictType.CLASS_DOUBLE_BOOKED })

        // 4. Teacher daily limit exceeded (Anil maxPeriodsPerDay = 3; placing 4th period on MON across schools)
        val threePeriodsForAnil = listOf(
            TimetableCell("c1", "sch_alpha", "2026-2027", "5th", "A", DayOfWeekCode.MON, "mon_p1", "Maths", teacherAnil.id, teacherAnil.name),
            TimetableCell("c2", "sch_alpha", "2026-2027", "6th", "A", DayOfWeekCode.MON, "mon_p2", "Maths", teacherAnil.id, teacherAnil.name),
            TimetableCell("c3", "sch_beta", "2026-2027", "7th", "A", DayOfWeekCode.MON, "mon_b2", "Maths", teacherAnil.id, teacherAnil.name)
        )
        val fourthCandidate = TimetableCell(
            id = "c4",
            schoolId = "sch_alpha",
            session = "2026-2027",
            className = "8th",
            section = "A",
            day = DayOfWeekCode.MON,
            slotId = "mon_p6",
            subjectName = "Physics",
            teacherId = teacherAnil.id,
            teacherName = teacherAnil.name
        )
        val limitRes = TimetableEngine.validateCellPlacement(
            candidate = fourthCandidate,
            existingCellsInSession = threePeriodsForAnil,
            teachers = listOf(teacherAnil),
            periodStructuresBySchool = structures,
            classAssignments = emptyList(),
            subjectRules = emptyList(),
            schoolNamesById = schoolNames
        )
        assertFalse(limitRes.canSave)
        assertTrue(limitRes.hardConflicts.any { it.type == HardConflictType.TEACHER_OVER_DAILY_LIMIT })
    }

    @Test
    fun `soft warnings allow save and respect double-period rule, consecutive streak, weekly count, and preferred time`() {
        val rules = listOf(
            SubjectRule(
                id = "r_math",
                schoolId = "sch_alpha",
                subjectName = "Mathematics",
                preferredTimeOfDay = PreferredTimeOfDay.MORNING,
                allowDoublePeriod = true,
                maxOncePerDay = false
            )
        )
        val assignments = listOf(
            ClassSectionAssignment(
                id = "asg_5a",
                schoolId = "sch_alpha",
                className = "5th",
                section = "A",
                classTeacherId = teacherMeera.id,
                subjectAssignments = listOf(
                    SubjectTeacherAssignment("Mathematics", teacherMeera.id, periodsPerWeek = 1)
                )
            )
        )

        // Existing: 5th-A Maths at mon_p1 (08:15-08:55) by Meera
        val p1 = TimetableCell("c_p1", "sch_alpha", "2026-2027", "5th", "A", DayOfWeekCode.MON, "mon_p1", "Mathematics", teacherMeera.id, teacherMeera.name)

        // Placing adjacent mon_p2 (08:55-09:35) with allowDoublePeriod=true -> NO SAME_SUBJECT_TWICE_IN_DAY warning,
        // but YES SUBJECT_WEEKLY_COUNT_MISMATCH (2/1) and canSave == true!
        val p2Adj = TimetableCell("c_p2", "sch_alpha", "2026-2027", "5th", "A", DayOfWeekCode.MON, "mon_p2", "Mathematics", teacherMeera.id, teacherMeera.name)
        val adjOutcome = TimetableEngine.validateCellPlacement(
            candidate = p2Adj,
            existingCellsInSession = listOf(p1),
            teachers = listOf(teacherMeera),
            periodStructuresBySchool = structures,
            classAssignments = assignments,
            subjectRules = rules,
            schoolNamesById = schoolNames
        )
        assertTrue(adjOutcome.canSave)
        assertFalse(
            "Adjacent double period should suppress same-subject-twice warning when allowDoublePeriod=true",
            adjOutcome.softWarnings.any { it.type == SoftWarningType.SAME_SUBJECT_TWICE_IN_DAY }
        )
        assertTrue(adjOutcome.softWarnings.any { it.type == SoftWarningType.SUBJECT_WEEKLY_COUNT_MISMATCH })

        // Placing non-adjacent afternoon mon_p6 (12:25-13:05) -> warns SAME_SUBJECT_TWICE_IN_DAY + PREFERRED_TIME_OF_DAY (MORNING)
        val p6Afternoon = TimetableCell("c_p6", "sch_alpha", "2026-2027", "5th", "A", DayOfWeekCode.MON, "mon_p6", "Mathematics", teacherMeera.id, teacherMeera.name)
        val afternoonOutcome = TimetableEngine.validateCellPlacement(
            candidate = p6Afternoon,
            existingCellsInSession = listOf(p1),
            teachers = listOf(teacherMeera),
            periodStructuresBySchool = structures,
            classAssignments = assignments,
            subjectRules = rules,
            schoolNamesById = schoolNames
        )
        assertTrue(afternoonOutcome.canSave)
        assertTrue(afternoonOutcome.softWarnings.any { it.type == SoftWarningType.SAME_SUBJECT_TWICE_IN_DAY })
        assertTrue(afternoonOutcome.softWarnings.any { it.type == SoftWarningType.PREFERRED_TIME_OF_DAY })

        // Consecutive periods warning: Anil maxConsecutivePeriods = 2; placing P1, P2, P3 back-to-back triggers warning
        val anilP1 = TimetableCell("a1", "sch_alpha", "2026-2027", "5th", "A", DayOfWeekCode.MON, "mon_p1", "Physics", teacherAnil.id, teacherAnil.name)
        val anilP2 = TimetableCell("a2", "sch_alpha", "2026-2027", "6th", "A", DayOfWeekCode.MON, "mon_p2", "Physics", teacherAnil.id, teacherAnil.name)
        val anilP3 = TimetableCell("a3", "sch_alpha", "2026-2027", "7th", "A", DayOfWeekCode.MON, "mon_p3", "Physics", teacherAnil.id, teacherAnil.name)
        val consecOutcome = TimetableEngine.validateCellPlacement(
            candidate = anilP3,
            existingCellsInSession = listOf(anilP1, anilP2),
            teachers = listOf(teacherAnil),
            periodStructuresBySchool = structures,
            classAssignments = emptyList(),
            subjectRules = emptyList(),
            schoolNamesById = schoolNames
        )
        assertTrue(consecOutcome.canSave)
        assertTrue(consecOutcome.softWarnings.any { it.type == SoftWarningType.TEACHER_TOO_MANY_CONSECUTIVE })

        // Lunch break (35 min > 20 min) resets consecutive streak: P4, P5, Lunch, P6 -> max streak = 2 (no warning for Anil)
        val anilP4 = TimetableCell("a4", "sch_alpha", "2026-2027", "5th", "A", DayOfWeekCode.MON, "mon_p4", "Physics", teacherAnil.id, teacherAnil.name)
        val anilP5 = TimetableCell("a5", "sch_alpha", "2026-2027", "6th", "A", DayOfWeekCode.MON, "mon_p5", "Physics", teacherAnil.id, teacherAnil.name)
        val anilP6 = TimetableCell("a6", "sch_alpha", "2026-2027", "7th", "A", DayOfWeekCode.MON, "mon_p6", "Physics", teacherAnil.id, teacherAnil.name)
        val afterLunchOutcome = TimetableEngine.validateCellPlacement(
            candidate = anilP6,
            existingCellsInSession = listOf(anilP4, anilP5),
            teachers = listOf(teacherAnil),
            periodStructuresBySchool = structures,
            classAssignments = emptyList(),
            subjectRules = emptyList(),
            schoolNamesById = schoolNames
        )
        assertTrue(afterLunchOutcome.canSave)
        assertFalse(
            "Lunch break should reset consecutive streak",
            afterLunchOutcome.softWarnings.any { it.type == SoftWarningType.TEACHER_TOO_MANY_CONSECUTIVE }
        )
    }

    @Test
    fun `post-sync clash detector catches cross-school double booking without silently overwriting`() {
        val syncedCells = listOf(
            TimetableCell("cell_alpha", "sch_alpha", "2026-2027", "6th", "A", DayOfWeekCode.MON, "mon_p2", "Mathematics", teacherAnil.id, teacherAnil.name),
            TimetableCell("cell_beta", "sch_beta", "2026-2027", "8th", "A", DayOfWeekCode.MON, "mon_b1", "Physics", teacherAnil.id, teacherAnil.name)
        )
        val clashes = TimetableEngine.detectAllClashes(
            cellsInSession = syncedCells,
            teachers = listOf(teacherAnil),
            periodStructuresBySchool = structures,
            schoolNamesById = schoolNames
        )
        assertEquals(1, clashes.size)
        assertEquals(HardConflictType.TEACHER_DOUBLE_BOOKED, clashes.first().type)
        assertEquals(listOf("cell_alpha", "cell_beta"), clashes.first().involvedCellIds)
        assertTrue(clashes.first().message.contains("Alpha Public School"))
        assertTrue(clashes.first().message.contains("Beta Academy"))
    }

    @Test
    fun `teacher picker prioritizes assigned teachers then subject teachers with live availability`() {
        val assignment = ClassSectionAssignment(
            id = "asg_6a",
            schoolId = "sch_alpha",
            className = "6th",
            section = "A",
            classTeacherId = "",
            subjectAssignments = listOf(SubjectTeacherAssignment("Mathematics", teacherMeera.id, 6))
        )
        val anilBusyElsewhere = TimetableCell(
            id = "cell_b1",
            schoolId = "sch_beta",
            session = "2026-2027",
            className = "8th",
            section = "A",
            day = DayOfWeekCode.MON,
            slotId = "mon_b1", // 09:00-09:40 overlaps mon_p2 (08:55-09:35)
            subjectName = "Physics",
            teacherId = teacherAnil.id,
            teacherName = teacherAnil.name
        )

        val options = TimetableEngine.buildTeacherPickerOptions(
            schoolId = "sch_alpha",
            session = "2026-2027",
            className = "6th",
            section = "A",
            day = DayOfWeekCode.MON,
            slotId = "mon_p2",
            subjectName = "Mathematics",
            currentCellId = "new_cell",
            teachers = listOf(teacherAnil, teacherMeera, teacherInactive),
            cellsInSession = listOf(anilBusyElsewhere),
            periodStructuresBySchool = structures,
            classAssignments = listOf(assignment),
            schoolNamesById = schoolNames
        )

        assertEquals(teacherMeera.id, options[0].teacher.id)
        assertEquals(0, options[0].tier)
        assertEquals(TeacherSlotAvailability.FREE, options[0].availability)

        assertEquals(teacherAnil.id, options[1].teacher.id)
        assertEquals(1, options[1].tier)
        assertEquals(TeacherSlotAvailability.BUSY, options[1].availability)
        assertTrue(options[1].statusDetail.contains("Beta Academy"))

        assertEquals(teacherInactive.id, options[2].teacher.id)
        assertEquals(TeacherSlotAvailability.INACTIVE, options[2].availability)
    }

    @Test
    fun `free teachers view excludes teachers busy in another school at overlapping clock time`() {
        val monP2 = schoolAStruct.slotsFor(DayOfWeekCode.MON).first { it.id == "mon_p2" } // 08:55-09:35
        val anilBusyInBeta = TimetableCell(
            id = "cell_b1",
            schoolId = "sch_beta",
            session = "2026-2027",
            className = "8th",
            section = "A",
            day = DayOfWeekCode.MON,
            slotId = "mon_b1", // 09:00-09:40 overlaps 08:55-09:35
            subjectName = "Physics",
            teacherId = teacherAnil.id,
            teacherName = teacherAnil.name
        )
        val meeraOtherSlot = TimetableCell(
            id = "cell_a1",
            schoolId = "sch_alpha",
            session = "2026-2027",
            className = "5th",
            section = "A",
            day = DayOfWeekCode.MON,
            slotId = "mon_p1", // 08:15-08:55 does NOT overlap 08:55-09:35
            subjectName = "Science",
            teacherId = teacherMeera.id,
            teacherName = teacherMeera.name
        )

        val free = TimetableEngine.findFreeTeachers(
            schoolId = "sch_alpha",
            session = "2026-2027",
            day = DayOfWeekCode.MON,
            slot = monP2,
            teachers = listOf(teacherAnil, teacherMeera, teacherInactive),
            cellsInSession = listOf(anilBusyInBeta, meeraOtherSlot),
            periodStructuresBySchool = structures
        )

        assertEquals(1, free.size)
        assertEquals(teacherMeera.id, free.first().teacher.id)
        assertEquals(1, free.first().periodsToday)
    }

    @Test
    fun `teacher timetable combines multiple schools and reports total and free periods`() {
        val cells = listOf(
            TimetableCell("c1", "sch_alpha", "2026-2027", "6th", "A", DayOfWeekCode.MON, "mon_p1", "Mathematics", teacherAnil.id, teacherAnil.name),
            TimetableCell("c2", "sch_beta", "2026-2027", "8th", "B", DayOfWeekCode.MON, "mon_b2", "Physics", teacherAnil.id, teacherAnil.name)
        )
        val summary = TimetableEngine.buildTeacherTimetable(
            teacherId = teacherAnil.id,
            fallbackTeacherName = teacherAnil.name,
            teacherProfile = teacherAnil,
            session = "2026-2027",
            cellsInSession = cells,
            periodStructuresBySchool = structures,
            schoolNamesById = schoolNames
        )
        assertEquals(2, summary.totalPeriodsPerWeek)
        val mondayItems = summary.itemsByDay[DayOfWeekCode.MON].orEmpty()
        assertEquals(2, mondayItems.size)
        assertEquals("Alpha Public School", mondayItems[0].schoolName)
        assertEquals("Beta Academy", mondayItems[1].schoolName)
        assertTrue(summary.freePeriodsPerWeek > 0)
    }

    @Test
    fun `latest-edit-wins conflict detector notifies when offline edit is replaced by another user`() {
        val cellId = TimetableEngine.makeCellDocId("sch_alpha", "2026-2027", "5th", "A", DayOfWeekCode.MON, "mon_p1")
        val pending = listOf(
            LocalPendingTimetableEdit(
                orgId = "org_1",
                cellId = cellId,
                schoolId = "sch_alpha",
                session = "2026-2027",
                className = "5th",
                section = "A",
                dayWire = DayOfWeekCode.MON.wireValue,
                slotId = "mon_p1",
                localSubjectName = "Mathematics",
                localTeacherId = teacherAnil.id,
                localTeacherName = teacherAnil.name,
                localLocked = false,
                wasCleared = false,
                clientEditedAtMs = 1_000_000L
            )
        )
        val serverCells = listOf(
            TimetableCell(
                id = cellId,
                schoolId = "sch_alpha",
                session = "2026-2027",
                className = "5th",
                section = "A",
                day = DayOfWeekCode.MON,
                slotId = "mon_p1",
                subjectName = "Science",
                teacherId = teacherMeera.id,
                teacherName = teacherMeera.name,
                updatedByUid = "other_admin_uid",
                updatedByName = "Principal Rao",
                hasPendingWrites = false,
                isFromCache = false
            )
        )

        val result = TimetableEngine.detectTimetableCellConflicts(
            pendingEdits = pending,
            confirmedServerCells = serverCells,
            hasAnyConfirmedSnapshot = true,
            currentUid = "my_uid",
            nowMs = 1_005_000L
        )
        assertTrue(result.remainingPending.isEmpty())
        assertEquals(1, result.detectedConflicts.size)
        assertEquals("Principal Rao", result.detectedConflicts.first().winnerName)
        assertTrue(result.detectedConflicts.first().yourSummary.contains("Mathematics"))
        assertTrue(result.detectedConflicts.first().winningSummary.contains("Science"))
    }
}
