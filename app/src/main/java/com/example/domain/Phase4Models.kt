package com.example.domain

/**
 * Phase 4 Domain Models & Pure Validation / Warning Engine:
 * - Teachers (org-level, multi-school capable, linked to member or no-login record)
 * - Period Structure (per school, editable working days & ordered time slots)
 * - Class-Subject-Teacher Assignments & Class Teacher (per school, class & section)
 * - Subject Rules (optional per subject in a school; stored for Phases 5 & 6)
 *
 * Pure Kotlin with zero Android/Firebase dependencies for direct JUnit testing.
 */

enum class DayOfWeekCode(val wireValue: String, val shortLabel: String, val fullLabel: String, val sortOrder: Int) {
    MON("MON", "Mon", "Monday", 0),
    TUE("TUE", "Tue", "Tuesday", 1),
    WED("WED", "Wed", "Wednesday", 2),
    THU("THU", "Thu", "Thursday", 3),
    FRI("FRI", "Fri", "Friday", 4),
    SAT("SAT", "Sat", "Saturday", 5),
    SUN("SUN", "Sun", "Sunday", 6);

    companion object {
        val defaultWorkingDays: List<DayOfWeekCode> = listOf(MON, TUE, WED, THU, FRI, SAT)

        fun fromRaw(raw: String?): DayOfWeekCode? =
            entries.firstOrNull {
                it.wireValue.equals(raw?.trim(), ignoreCase = true) ||
                    it.fullLabel.equals(raw?.trim(), ignoreCase = true)
            }
    }
}

enum class SlotType(val wireValue: String, val label: String, val countsAsTeachingPeriod: Boolean) {
    PERIOD("PERIOD", "Period", true),
    ASSEMBLY("ASSEMBLY", "Assembly / Prayer", false),
    SHORT_BREAK("SHORT_BREAK", "Short Break", false),
    LUNCH("LUNCH", "Lunch Break", false);

    companion object {
        fun fromRaw(raw: String?): SlotType =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: PERIOD
    }
}

data class PeriodSlot(
    val id: String,
    val type: SlotType,
    val label: String,
    val startMinutes: Int, // 0..1439 (e.g., 08:00 = 480)
    val endMinutes: Int    // 1..1440 (e.g., 08:40 = 520)
) {
    val durationMinutes: Int get() = (endMinutes - startMinutes).coerceAtLeast(0)
    val startTimeFormatted: String get() = Phase4Validator.formatMinutes(startMinutes)
    val endTimeFormatted: String get() = Phase4Validator.formatMinutes(endMinutes)
}

data class SchoolPeriodStructure(
    val schoolId: String,
    val workingDays: List<DayOfWeekCode> = DayOfWeekCode.defaultWorkingDays,
    val slotsByDay: Map<DayOfWeekCode, List<PeriodSlot>> = emptyMap(),
    val isCustomized: Boolean = false
) {
    fun slotsFor(day: DayOfWeekCode): List<PeriodSlot> = slotsByDay[day].orEmpty()

    fun teachingPeriodsPerDay(day: DayOfWeekCode): Int =
        if (day in workingDays) slotsFor(day).count { it.type.countsAsTeachingPeriod } else 0

    fun totalTeachingPeriodsPerWeek(): Int =
        workingDays.sumOf { teachingPeriodsPerDay(it) }
}

data class TeacherProfile(
    val id: String,
    val name: String,
    val mobile: String = "",
    val email: String = "",
    val photoBase64: String = "",
    val subjects: List<String> = emptyList(),
    val schoolIds: List<String> = emptyList(),
    val maxPeriodsPerDay: Int = 7,
    val maxConsecutivePeriods: Int = 3,
    val weeklyOffDays: List<DayOfWeekCode> = emptyList(),
    val active: Boolean = true,
    val linkedMemberUid: String = "",
    val dob: String = ""
) {
    val hasLogin: Boolean get() = linkedMemberUid.isNotBlank()
}

data class SubjectTeacherAssignment(
    val subjectName: String,
    val teacherId: String = "",
    val periodsPerWeek: Int = 0
)

data class ClassSectionAssignment(
    val id: String,
    val schoolId: String,
    val className: String,
    val section: String,
    val classTeacherId: String = "",
    val subjectAssignments: List<SubjectTeacherAssignment> = emptyList()
) {
    val classSectionLabel: String get() = "$className-$section"
    val totalAssignedPeriods: Int get() = subjectAssignments.sumOf { it.periodsPerWeek.coerceAtLeast(0) }
}

enum class PreferredTimeOfDay(val wireValue: String, val label: String) {
    NO_PREFERENCE("NO_PREFERENCE", "No preference"),
    MORNING("MORNING", "Morning"),
    AFTERNOON("AFTERNOON", "Afternoon");

    companion object {
        fun fromRaw(raw: String?): PreferredTimeOfDay =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: NO_PREFERENCE
    }
}

data class SubjectRule(
    val id: String,
    val schoolId: String,
    val subjectName: String,
    val preferredTimeOfDay: PreferredTimeOfDay = PreferredTimeOfDay.NO_PREFERENCE,
    val allowDoublePeriod: Boolean = false,
    val maxOncePerDay: Boolean = false
)

sealed interface PeriodValidationResult {
    data object Valid : PeriodValidationResult
    data class Invalid(val errors: List<String>) : PeriodValidationResult
}

enum class AssignmentWarningType(val title: String) {
    CLASS_PERIOD_TOTAL_MISMATCH("Class period count mismatch"),
    TEACHER_OVER_WEEKLY_LIMIT("Teacher exceeds weekly limit"),
    TEACHER_SUBJECT_MISMATCH("Subject not in teacher's list"),
    CROSS_SCHOOL_TIME_OVERLAP("Cross-school timing overlap"),
    INACTIVE_TEACHER_ASSIGNED("Inactive teacher assigned")
}

data class AssignmentWarning(
    val type: AssignmentWarningType,
    val message: String,
    val schoolId: String? = null,
    val classSectionKey: String? = null,
    val teacherId: String? = null,
    val subjectName: String? = null
)

data class TeacherAssignmentItem(
    val schoolId: String,
    val schoolName: String,
    val className: String,
    val section: String,
    val subjectName: String,
    val periodsPerWeek: Int
)

data class TeacherWorkloadSummary(
    val teacher: TeacherProfile,
    val totalPeriodsPerWeek: Int,
    val weeklyPeriodLimit: Int,
    val effectiveWorkingDaysCount: Int,
    val assignments: List<TeacherAssignmentItem>,
    val classTeacherOf: List<Pair<String, String>>, // (schoolName, classSectionLabel)
    val warnings: List<AssignmentWarning>
) {
    val isOverLimit: Boolean get() = totalPeriodsPerWeek > weeklyPeriodLimit
    val hasAnyAssignment: Boolean get() = assignments.isNotEmpty() || classTeacherOf.isNotEmpty()
}

object Phase4Validator {

    const val DEFAULT_MAX_PERIODS_PER_DAY = 7
    const val DEFAULT_MAX_CONSECUTIVE_PERIODS = 3
    val DEFAULT_WORKING_DAYS: List<DayOfWeekCode> = DayOfWeekCode.defaultWorkingDays

    fun formatMinutes(totalMinutes: Int): String {
        val clamped = totalMinutes.coerceIn(0, 24 * 60)
        val hours = clamped / 60
        val mins = clamped % 60
        return "%02d:%02d".format(hours, mins)
    }

    fun parseTimeToMinutes(raw: String): Int? {
        val cleaned = raw.trim()
        val match = Regex("^([0-1]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(cleaned)
            ?: return if (cleaned == "24:00") 1440 else null
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        return h * 60 + m
    }

    /**
     * Default editable period structure template:
     * - Mon..Fri: Morning Assembly (08:00-08:15), 8 periods of 40 min,
     *   Short Break after Period 3 (10:15-10:30), Lunch after Period 5 (11:50-12:25).
     * - Saturday: Fewer periods (Morning Assembly + 5 periods of 40 min + Short Break after Period 3).
     */
    fun buildDefaultPeriodStructure(schoolId: String): SchoolPeriodStructure {
        val weekdaySlots = buildDefaultDaySlots(periodCount = 8, includeLunch = true)
        val saturdaySlots = buildDefaultDaySlots(periodCount = 5, includeLunch = false)
        val map = linkedMapOf<DayOfWeekCode, List<PeriodSlot>>()
        DayOfWeekCode.defaultWorkingDays.forEach { day ->
            val src = if (day == DayOfWeekCode.SAT) saturdaySlots else weekdaySlots
            map[day] = src.mapIndexed { idx, slot -> slot.copy(id = "${day.wireValue}_slot_${idx + 1}") }
        }
        return SchoolPeriodStructure(
            schoolId = schoolId,
            workingDays = DayOfWeekCode.defaultWorkingDays,
            slotsByDay = map,
            isCustomized = false
        )
    }

    fun buildDefaultDaySlots(
        periodCount: Int = 8,
        includeLunch: Boolean = true,
        startHour: Int = 8,
        startMinute: Int = 0,
        periodDurationMinutes: Int = 40
    ): List<PeriodSlot> {
        val slots = mutableListOf<PeriodSlot>()
        var cursor = startHour * 60 + startMinute
        var idx = 1

        fun add(type: SlotType, label: String, duration: Int) {
            val end = (cursor + duration).coerceAtMost(1440)
            slots.add(PeriodSlot(id = "slot_${idx++}", type = type, label = label, startMinutes = cursor, endMinutes = end))
            cursor = end
        }

        add(SlotType.ASSEMBLY, "Morning Assembly", 15)
        for (p in 1..periodCount.coerceIn(1, 14)) {
            add(SlotType.PERIOD, "Period $p", periodDurationMinutes)
            if (p == 3 && periodCount > 3) {
                add(SlotType.SHORT_BREAK, "Short Break", 15)
            }
            if (p == 5 && includeLunch && periodCount > 5) {
                add(SlotType.LUNCH, "Lunch Break", 35)
            }
        }
        return slots
    }

    /**
     * Copies one day's ordered slots to one or more target days, giving each target day fresh slot IDs.
     */
    fun copyDaySlots(
        structure: SchoolPeriodStructure,
        sourceDay: DayOfWeekCode,
        targetDays: Collection<DayOfWeekCode>
    ): SchoolPeriodStructure {
        val sourceSlots = structure.slotsFor(sourceDay)
        val updatedMap = structure.slotsByDay.toMutableMap()
        val updatedWorkingDays = structure.workingDays.toMutableList()

        targetDays.distinct().forEach { target ->
            if (target != sourceDay) {
                if (target !in updatedWorkingDays) {
                    updatedWorkingDays.add(target)
                }
                updatedMap[target] = sourceSlots.mapIndexed { idx, slot ->
                    slot.copy(id = "${target.wireValue}_slot_${idx + 1}")
                }
            }
        }
        return structure.copy(
            workingDays = updatedWorkingDays.sortedBy { it.sortOrder },
            slotsByDay = updatedMap,
            isCustomized = true
        )
    }

    /**
     * Validates a single day's slot list:
     * - Each slot must have valid start/end in 00:00..24:00 and end > start.
     * - Slots must be in ascending order with NO overlapping time intervals.
     */
    fun validateDaySlots(day: DayOfWeekCode, slots: List<PeriodSlot>): PeriodValidationResult {
        val errors = mutableListOf<String>()
        slots.forEachIndexed { idx, slot ->
            val label = slot.label.ifBlank { "${slot.type.label} #${idx + 1}" }
            if (slot.startMinutes < 0 || slot.startMinutes >= 1440) {
                errors.add("${day.shortLabel}: '$label' has an invalid start time.")
            }
            if (slot.endMinutes <= 0 || slot.endMinutes > 1440) {
                errors.add("${day.shortLabel}: '$label' has an invalid end time.")
            }
            if (slot.endMinutes <= slot.startMinutes) {
                errors.add(
                    "${day.shortLabel}: '$label' end time (${slot.endTimeFormatted}) must be after start time (${slot.startTimeFormatted})."
                )
            }
        }

        // Check chronological order and overlaps across consecutive and pairwise slots
        for (i in 0 until slots.size - 1) {
            val curr = slots[i]
            val next = slots[i + 1]
            val currName = curr.label.ifBlank { "${curr.type.label} #${i + 1}" }
            val nextName = next.label.ifBlank { "${next.type.label} #${i + 2}" }

            val overlaps = curr.startMinutes < next.endMinutes && next.startMinutes < curr.endMinutes
            if (overlaps) {
                errors.add(
                    "${day.shortLabel}: '$currName' (${curr.startTimeFormatted}–${curr.endTimeFormatted}) overlaps with '$nextName' (${next.startTimeFormatted}–${next.endTimeFormatted})."
                )
            } else if (next.startMinutes < curr.startMinutes) {
                errors.add(
                    "${day.shortLabel}: '$nextName' (${next.startTimeFormatted}) starts before '$currName' (${curr.startTimeFormatted}). Slots must be in chronological order."
                )
            }
        }

        // Also catch non-adjacent overlaps if list was out of order
        if (errors.isEmpty()) {
            for (i in slots.indices) {
                for (j in i + 1 until slots.size) {
                    val a = slots[i]
                    val b = slots[j]
                    if (a.startMinutes < b.endMinutes && b.startMinutes < a.endMinutes) {
                        errors.add(
                            "${day.shortLabel}: '${a.label}' (${a.startTimeFormatted}–${a.endTimeFormatted}) overlaps with '${b.label}' (${b.startTimeFormatted}–${b.endTimeFormatted})."
                        )
                    }
                }
            }
        }

        return if (errors.isEmpty()) PeriodValidationResult.Valid else PeriodValidationResult.Invalid(errors.distinct())
    }

    /**
     * Validates an entire school's period structure across all working days.
     */
    fun validatePeriodStructure(structure: SchoolPeriodStructure): PeriodValidationResult {
        val errors = mutableListOf<String>()
        if (structure.workingDays.isEmpty()) {
            errors.add("Select at least one working day.")
        }
        structure.workingDays.forEach { day ->
            val daySlots = structure.slotsFor(day)
            if (daySlots.isEmpty()) {
                errors.add("${day.fullLabel} has no slots configured.")
            } else {
                val res = validateDaySlots(day, daySlots)
                if (res is PeriodValidationResult.Invalid) {
                    errors.addAll(res.errors)
                }
            }
        }
        return if (errors.isEmpty()) PeriodValidationResult.Valid else PeriodValidationResult.Invalid(errors.distinct())
    }

    /**
     * Checks whether a teacher can be permanently deleted.
     * Requirement: NEVER delete a teacher who has assignments (either subject assignments or class teacher role).
     */
    fun canDeleteTeacher(
        teacherId: String,
        allAssignments: List<ClassSectionAssignment>
    ): String? {
        if (teacherId.isBlank()) return "Invalid teacher record."
        val subjectCount = allAssignments.sumOf { cls ->
            cls.subjectAssignments.count { it.teacherId == teacherId && it.periodsPerWeek > 0 }
        }
        val classTeacherCount = allAssignments.count { it.classTeacherId == teacherId }
        if (subjectCount > 0 || classTeacherCount > 0) {
            val parts = buildList {
                if (subjectCount > 0) add("$subjectCount subject assignment(s)")
                if (classTeacherCount > 0) add("class teacher of $classTeacherCount class(es)")
            }
            return "Cannot delete this teacher because they have ${parts.joinToString(" and ")}. Deactivate them instead or remove their assignments first."
        }
        return null
    }

    /**
     * Computes a teacher's effective working days per week and maximum weekly period limit.
     * Effective working days = distinct working days across the teacher's schools (or default Mon–Sat if none)
     * minus the teacher's weekly off day(s).
     */
    fun computeTeacherWeeklyLimit(
        teacher: TeacherProfile,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        assignedSchoolIds: Set<String> = emptySet()
    ): Pair<Int, Int> {
        val relevantSchools = (teacher.schoolIds + assignedSchoolIds).filter { it.isNotBlank() }.distinct()
        val unionWorkingDays = if (relevantSchools.isEmpty()) {
            DayOfWeekCode.defaultWorkingDays.toSet()
        } else {
            relevantSchools.flatMap { schId ->
                periodStructuresBySchool[schId]?.workingDays ?: DayOfWeekCode.defaultWorkingDays
            }.toSet()
        }
        val effectiveDays = unionWorkingDays.count { it !in teacher.weeklyOffDays }
        val weeklyLimit = teacher.maxPeriodsPerDay.coerceAtLeast(0) * effectiveDays
        return effectiveDays to weeklyLimit
    }

    /**
     * Checks whether two schools have overlapping teaching PERIOD slots on any common working day
     * (excluding any weekly off days of the teacher).
     * Returns list of human-readable overlap descriptions like "Mon (08:15–11:50)".
     */
    fun findCrossSchoolPeriodOverlaps(
        structureA: SchoolPeriodStructure,
        structureB: SchoolPeriodStructure,
        excludedDays: Set<DayOfWeekCode> = emptySet()
    ): List<String> {
        val commonDays = structureA.workingDays
            .filter { it in structureB.workingDays && it !in excludedDays }
            .sortedBy { it.sortOrder }

        val overlaps = mutableListOf<String>()
        for (day in commonDays) {
            val periodsA = structureA.slotsFor(day).filter { it.type.countsAsTeachingPeriod }
            val periodsB = structureB.slotsFor(day).filter { it.type.countsAsTeachingPeriod }
            var minOverlapStart = Int.MAX_VALUE
            var maxOverlapEnd = Int.MIN_VALUE
            var foundOverlap = false

            for (a in periodsA) {
                for (b in periodsB) {
                    val overlapStart = maxOf(a.startMinutes, b.startMinutes)
                    val overlapEnd = minOf(a.endMinutes, b.endMinutes)
                    if (overlapStart < overlapEnd) {
                        foundOverlap = true
                        minOverlapStart = minOf(minOverlapStart, overlapStart)
                        maxOverlapEnd = maxOf(maxOverlapEnd, overlapEnd)
                    }
                }
            }
            if (foundOverlap) {
                overlaps.add("${day.shortLabel} (${formatMinutes(minOverlapStart)}–${formatMinutes(maxOverlapEnd)})")
            }
        }
        return overlaps
    }

    /**
     * Evaluates ALL non-blocking Phase 4 warnings across the organisation:
     * 1. Class total assigned periods/week != available period slots in that school's week.
     * 2. Teacher's total periods/week across all schools exceed their weekly limit.
     * 3. Teacher assigned a subject not in their teachable subject list.
     * 4. Teacher assigned in two schools whose period times overlap.
     * 5. Inactive teacher still assigned to a class-subject or as class teacher.
     */
    fun evaluateAllWarnings(
        teachers: List<TeacherProfile>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        assignments: List<ClassSectionAssignment>,
        schoolNamesById: Map<String, String>
    ): List<AssignmentWarning> {
        val warnings = mutableListOf<AssignmentWarning>()
        val teachersById = teachers.associateBy { it.id }

        // 1. Class-level & Subject-level warnings
        for (cls in assignments) {
            val schoolName = schoolNamesById[cls.schoolId] ?: cls.schoolId
            val structure = periodStructuresBySchool[cls.schoolId] ?: buildDefaultPeriodStructure(cls.schoolId)
            val availablePeriods = structure.totalTeachingPeriodsPerWeek()
            val assignedPeriods = cls.totalAssignedPeriods
            val key = "${cls.schoolId}|${cls.className}|${cls.section}"

            if (assignedPeriods != availablePeriods && cls.subjectAssignments.isNotEmpty()) {
                val diff = assignedPeriods - availablePeriods
                val detail = if (diff > 0) "$diff over weekly capacity" else "${-diff} slot(s) unassigned"
                warnings.add(
                    AssignmentWarning(
                        type = AssignmentWarningType.CLASS_PERIOD_TOTAL_MISMATCH,
                        message = "${cls.classSectionLabel} ($schoolName): assigned $assignedPeriods periods/week vs $availablePeriods available period slots ($detail).",
                        schoolId = cls.schoolId,
                        classSectionKey = key
                    )
                )
            }

            // Inactive class teacher warning
            if (cls.classTeacherId.isNotBlank()) {
                val ct = teachersById[cls.classTeacherId]
                if (ct != null && !ct.active) {
                    warnings.add(
                        AssignmentWarning(
                            type = AssignmentWarningType.INACTIVE_TEACHER_ASSIGNED,
                            message = "${ct.name} is inactive but still set as Class Teacher for ${cls.classSectionLabel} ($schoolName).",
                            schoolId = cls.schoolId,
                            classSectionKey = key,
                            teacherId = ct.id
                        )
                    )
                }
            }

            // Per-subject assignment checks
            for (sa in cls.subjectAssignments) {
                if (sa.teacherId.isBlank() || sa.periodsPerWeek <= 0) continue
                val teacher = teachersById[sa.teacherId] ?: continue

                if (!teacher.active) {
                    warnings.add(
                        AssignmentWarning(
                            type = AssignmentWarningType.INACTIVE_TEACHER_ASSIGNED,
                            message = "${teacher.name} is inactive but assigned ${sa.subjectName} (${sa.periodsPerWeek}/wk) in ${cls.classSectionLabel}.",
                            schoolId = cls.schoolId,
                            classSectionKey = key,
                            teacherId = teacher.id,
                            subjectName = sa.subjectName
                        )
                    )
                }

                val teachesSubject = teacher.subjects.any {
                    it.trim().equals(sa.subjectName.trim(), ignoreCase = true)
                }
                if (!teachesSubject) {
                    warnings.add(
                        AssignmentWarning(
                            type = AssignmentWarningType.TEACHER_SUBJECT_MISMATCH,
                            message = "${teacher.name} is assigned '${sa.subjectName}' in ${cls.classSectionLabel}, which is not in their subject list.",
                            schoolId = cls.schoolId,
                            classSectionKey = key,
                            teacherId = teacher.id,
                            subjectName = sa.subjectName
                        )
                    )
                }
            }
        }

        // 2. Teacher-level aggregate warnings (weekly limit & cross-school timing overlap)
        val assignedItemsByTeacher = mutableMapOf<String, MutableList<Pair<String, Int>>>() // teacherId -> list of (schoolId, periods)
        for (cls in assignments) {
            for (sa in cls.subjectAssignments) {
                if (sa.teacherId.isNotBlank() && sa.periodsPerWeek > 0) {
                    assignedItemsByTeacher.getOrPut(sa.teacherId) { mutableListOf() }
                        .add(cls.schoolId to sa.periodsPerWeek)
                }
            }
        }

        for (teacher in teachers) {
            val items = assignedItemsByTeacher[teacher.id].orEmpty()
            if (items.isEmpty()) continue

            val totalPeriods = items.sumOf { it.second }
            val assignedSchools = items.map { it.first }.distinct()
            val (effectiveDays, weeklyLimit) = computeTeacherWeeklyLimit(
                teacher = teacher,
                periodStructuresBySchool = periodStructuresBySchool,
                assignedSchoolIds = assignedSchools.toSet()
            )

            if (totalPeriods > weeklyLimit) {
                warnings.add(
                    AssignmentWarning(
                        type = AssignmentWarningType.TEACHER_OVER_WEEKLY_LIMIT,
                        message = "${teacher.name} has $totalPeriods periods/week, exceeding their limit of $weeklyLimit (${teacher.maxPeriodsPerDay}/day × $effectiveDays day(s)).",
                        teacherId = teacher.id
                    )
                )
            }

            // Cross-school timing overlap check for every pair of schools where teacher has assignments
            if (assignedSchools.size >= 2) {
                val excluded = teacher.weeklyOffDays.toSet()
                for (i in 0 until assignedSchools.size) {
                    for (j in i + 1 until assignedSchools.size) {
                        val schA = assignedSchools[i]
                        val schB = assignedSchools[j]
                        val structA = periodStructuresBySchool[schA] ?: buildDefaultPeriodStructure(schA)
                        val structB = periodStructuresBySchool[schB] ?: buildDefaultPeriodStructure(schB)
                        val overlaps = findCrossSchoolPeriodOverlaps(structA, structB, excluded)
                        if (overlaps.isNotEmpty()) {
                            val nameA = schoolNamesById[schA] ?: schA
                            val nameB = schoolNamesById[schB] ?: schB
                            val previewDays = overlaps.take(3).joinToString(", ") +
                                if (overlaps.size > 3) " (+${overlaps.size - 3} more)" else ""
                            warnings.add(
                                AssignmentWarning(
                                    type = AssignmentWarningType.CROSS_SCHOOL_TIME_OVERLAP,
                                    message = "${teacher.name} is assigned in both $nameA and $nameB with overlapping period timings: $previewDays.",
                                    teacherId = teacher.id
                                )
                            )
                        }
                    }
                }
            }
        }

        return warnings
    }

    /**
     * Builds per-teacher workload summaries across all schools in the organisation.
     */
    fun buildTeacherWorkloads(
        teachers: List<TeacherProfile>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        assignments: List<ClassSectionAssignment>,
        schoolNamesById: Map<String, String>,
        allWarnings: List<AssignmentWarning>? = null
    ): List<TeacherWorkloadSummary> {
        val warnings = allWarnings ?: evaluateAllWarnings(
            teachers = teachers,
            periodStructuresBySchool = periodStructuresBySchool,
            assignments = assignments,
            schoolNamesById = schoolNamesById
        )
        val warningsByTeacher = warnings.filter { it.teacherId != null }.groupBy { it.teacherId!! }

        return teachers.sortedBy { it.name.lowercase() }.map { teacher ->
            val items = mutableListOf<TeacherAssignmentItem>()
            val classTeacherList = mutableListOf<Pair<String, String>>()

            for (cls in assignments) {
                val schoolName = schoolNamesById[cls.schoolId] ?: cls.schoolId
                if (cls.classTeacherId == teacher.id) {
                    classTeacherList.add(schoolName to cls.classSectionLabel)
                }
                for (sa in cls.subjectAssignments) {
                    if (sa.teacherId == teacher.id && sa.periodsPerWeek > 0) {
                        items.add(
                            TeacherAssignmentItem(
                                schoolId = cls.schoolId,
                                schoolName = schoolName,
                                className = cls.className,
                                section = cls.section,
                                subjectName = sa.subjectName,
                                periodsPerWeek = sa.periodsPerWeek
                            )
                        )
                    }
                }
            }

            val assignedSchools = items.map { it.schoolId }.toSet()
            val (effectiveDays, weeklyLimit) = computeTeacherWeeklyLimit(
                teacher = teacher,
                periodStructuresBySchool = periodStructuresBySchool,
                assignedSchoolIds = assignedSchools
            )

            TeacherWorkloadSummary(
                teacher = teacher,
                totalPeriodsPerWeek = items.sumOf { it.periodsPerWeek },
                weeklyPeriodLimit = weeklyLimit,
                effectiveWorkingDaysCount = effectiveDays,
                assignments = items.sortedWith(compareBy({ it.schoolName.lowercase() }, { it.className.lowercase() }, { it.section }, { it.subjectName.lowercase() })),
                classTeacherOf = classTeacherList,
                warnings = warningsByTeacher[teacher.id].orEmpty()
            )
        }
    }
}
