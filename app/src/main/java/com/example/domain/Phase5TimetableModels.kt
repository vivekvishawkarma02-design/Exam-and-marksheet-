package com.example.domain

/**
 * Phase 5 Pure Kotlin Domain Models & Timetable Engine:
 * - Deterministic TimetableCell document IDs (school + session + class + section + day + slot)
 * - Hard conflict checks (cross-school real-clock teacher overlap, class double-booking,
 *   weekly off day, max periods/day, inactive teacher)
 * - Soft warnings (same subject twice in one day, consecutive periods over teacher limit,
 *   subject weekly count mismatch, preferred time of day violation)
 * - Post-sync full Clash Report detection (no silent overwrites)
 * - Latest-edit-wins cell conflict detection
 * - Teacher timetable, Master timetable, and Free-periods view computations
 *
 * Pure Kotlin with zero Android/Firebase dependencies for direct JUnit testing.
 */

data class TimetableCell(
    val id: String,
    val schoolId: String,
    val session: String,
    val className: String,
    val section: String,
    val day: DayOfWeekCode,
    val slotId: String,
    val subjectName: String,
    val teacherId: String,
    val teacherName: String = "",
    val locked: Boolean = false,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false
) {
    val classSectionLabel: String get() = "$className-$section"
}

enum class HardConflictType(val label: String) {
    TEACHER_DOUBLE_BOOKED("Teacher double-booked"),
    CLASS_DOUBLE_BOOKED("Class slot conflict"),
    TEACHER_OFF_DAY("Teacher weekly off day"),
    TEACHER_OVER_DAILY_LIMIT("Exceeds daily period limit"),
    TEACHER_INACTIVE("Inactive teacher")
}

data class TimetableHardConflict(
    val type: HardConflictType,
    val message: String,
    val involvedCellIds: List<String> = emptyList(),
    val primaryCell: TimetableCell? = null,
    val conflictingCell: TimetableCell? = null,
    val teacherId: String? = null,
    val day: DayOfWeekCode? = null
)

enum class SoftWarningType(val label: String) {
    SAME_SUBJECT_TWICE_IN_DAY("Same subject twice in a day"),
    TEACHER_TOO_MANY_CONSECUTIVE("Too many consecutive periods"),
    SUBJECT_WEEKLY_COUNT_MISMATCH("Weekly period count mismatch"),
    PREFERRED_TIME_OF_DAY("Against preferred time of day")
}

data class TimetableSoftWarning(
    val type: SoftWarningType,
    val message: String
)

data class CellValidationOutcome(
    val hardConflicts: List<TimetableHardConflict>,
    val softWarnings: List<TimetableSoftWarning>
) {
    val canSave: Boolean get() = hardConflicts.isEmpty()
}

data class SubjectWeeklyProgress(
    val subjectName: String,
    val placedCount: Int,
    val assignedCount: Int
) {
    val label: String get() = if (assignedCount > 0) "$subjectName $placedCount/$assignedCount" else "$subjectName $placedCount"
    val isExactMatch: Boolean get() = assignedCount > 0 && placedCount == assignedCount
    val isOver: Boolean get() = assignedCount > 0 && placedCount > assignedCount
    val isUnder: Boolean get() = assignedCount > 0 && placedCount < assignedCount
}

enum class TeacherSlotAvailability {
    FREE,
    BUSY,
    OFF_DAY,
    DAILY_LIMIT_REACHED,
    INACTIVE
}

data class TeacherPickerOption(
    val teacher: TeacherProfile,
    val tier: Int, // 0 = Assigned to this class & subject, 1 = Can teach subject, 2 = Other teacher
    val availability: TeacherSlotAvailability,
    val statusDetail: String,
    val periodsToday: Int
)

data class FreeTeacherEntry(
    val teacher: TeacherProfile,
    val periodsToday: Int,
    val maxPeriodsPerDay: Int,
    val worksInSelectedSchool: Boolean
)

data class TeacherWeekSlotItem(
    val day: DayOfWeekCode,
    val startMinutes: Int,
    val endMinutes: Int,
    val slotLabel: String,
    val schoolId: String,
    val schoolName: String,
    val className: String,
    val section: String,
    val subjectName: String,
    val cell: TimetableCell
)

data class TeacherTimetableSummary(
    val teacherId: String,
    val teacherName: String,
    val totalPeriodsPerWeek: Int,
    val weeklyLimit: Int,
    val freePeriodsPerWeek: Int,
    val itemsByDay: Map<DayOfWeekCode, List<TeacherWeekSlotItem>>
)

/**
 * Tracks a timetable cell edit made on this phone while offline (or before server confirmation)
 * so we can notify the user if another phone's newer edit replaced it on reconnect.
 */
data class LocalPendingTimetableEdit(
    val orgId: String,
    val cellId: String,
    val schoolId: String,
    val session: String,
    val className: String,
    val section: String,
    val dayWire: String,
    val slotId: String,
    val localSubjectName: String,
    val localTeacherId: String,
    val localTeacherName: String,
    val localLocked: Boolean,
    val wasCleared: Boolean,
    val clientEditedAtMs: Long
)

data class TimetableConflictNotice(
    val cellId: String,
    val schoolId: String,
    val classSectionLabel: String,
    val day: DayOfWeekCode,
    val slotId: String,
    val yourSummary: String,
    val winningSummary: String,
    val winnerName: String,
    val detectedAtMs: Long
)

data class TimetableConflictDetectionResult(
    val remainingPending: List<LocalPendingTimetableEdit>,
    val detectedConflicts: List<TimetableConflictNotice>
)

/**
 * Canonical grid row representing one ordered row in a school's weekly timetable grid.
 * Because Saturday may have fewer slots than Monday–Friday, each row maps each day to
 * its exact PeriodSlot (or null if that day has no slot at this row).
 */
data class TimetableGridRow(
    val rowIndex: Int,
    val label: String,
    val timeLabel: String,
    val type: SlotType,
    val representativeStartMinutes: Int,
    val representativeEndMinutes: Int,
    val slotByDay: Map<DayOfWeekCode, PeriodSlot?>
) {
    val isEditablePeriodRow: Boolean get() = type.countsAsTeachingPeriod
}

object TimetableEngine {

    /**
     * Noon cutoff (12:00 = 720 min) used for Phase 4 SubjectRule preferredTimeOfDay:
     * - MORNING: startMinutes < 720
     * - AFTERNOON: startMinutes >= 720
     */
    const val AFTERNOON_START_MINUTES = 12 * 60

    /**
     * Maximum gap (minutes) between two consecutive teaching slots that still counts
     * as a continuous teaching streak (0 min back-to-back or a <= 20 min short break).
     * A lunch break (>= 25 min) or a free period resets the consecutive streak.
     */
    const val CONSECUTIVE_MAX_GAP_MINUTES = 20

    private const val STALE_PENDING_TTL_MS = 7L * 24L * 60L * 60L * 1000L

    /**
     * Deterministic Firestore document ID for a timetable cell:
     * (school + session + class + section + day + slot) -> never duplicated even offline.
     */
    fun makeCellDocId(
        schoolId: String,
        session: String,
        className: String,
        section: String,
        day: DayOfWeekCode,
        slotId: String
    ): String {
        val sch = sanitizePart(schoolId, 24)
        val ses = sanitizePart(session, 14)
        val cls = sanitizePart(className, 16)
        val sec = sanitizePart(section.uppercase(), 8)
        val slt = sanitizePart(slotId, 28)
        return "tt_${sch}_${ses}_${cls}_${sec}_${day.wireValue}_${slt}".take(126)
    }

    private fun sanitizePart(raw: String, maxLen: Int): String =
        raw.trim().replace(Regex("[^a-zA-Z0-9_-]+"), "_").trim('_').take(maxLen).ifEmpty { "x" }

    fun shortTeacherName(fullName: String): String {
        val cleaned = fullName.trim()
        if (cleaned.isEmpty()) return "—"
        val parts = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size == 1) return parts.first().take(10)
        val honorifics = setOf("mr", "mr.", "mrs", "mrs.", "ms", "ms.", "dr", "dr.", "shri", "smt", "smt.")
        val meaningful = parts.filterNot { it.lowercase() in honorifics }.ifEmpty { parts }
        return if (meaningful.size == 1) {
            meaningful.first().take(10)
        } else {
            val firstInitial = meaningful.first().first().uppercaseChar()
            val last = meaningful.last().take(8)
            "$firstInitial. $last"
        }
    }

    fun shortSubjectName(subjectName: String): String {
        val cleaned = subjectName.trim()
        if (cleaned.length <= 10) return cleaned
        val upper = cleaned.uppercase()
        return when {
            upper.startsWith("MATHEMATIC") -> "Maths"
            upper.startsWith("SOCIAL SC") || upper == "SOCIAL STUDIES" -> "S.St."
            upper.startsWith("GENERAL KN") -> "G.K."
            upper.startsWith("COMPUTER") -> "Comp."
            upper.startsWith("ENVIRON") -> "E.V.S."
            upper.startsWith("ENGLISH") -> "English"
            upper.startsWith("SCIENCE") -> "Science"
            upper.startsWith("PHYSICAL") -> "P.E."
            upper.startsWith("SANSKRIT") -> "Sanskrit"
            else -> cleaned.take(9) + "…"
        }
    }

    /**
     * Resolves a cell's real clock-time [PeriodSlot] from its school's period structure.
     * Falls back to default period structure if the school hasn't customized one yet.
     */
    fun resolveSlot(
        schoolId: String,
        day: DayOfWeekCode,
        slotId: String,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>
    ): PeriodSlot? {
        val structure = periodStructuresBySchool[schoolId] ?: Phase4Validator.buildDefaultPeriodStructure(schoolId)
        val daySlots = structure.slotsFor(day)
        daySlots.firstOrNull { it.id == slotId }?.let { return it }
        // Fallback match by suffix index if slot IDs were regenerated after a day copy
        val suffix = slotId.substringAfterLast('_', "")
        if (suffix.isNotEmpty()) {
            daySlots.firstOrNull { it.id.endsWith("_$suffix") }?.let { return it }
        }
        return null
    }

    /**
     * Builds canonical ordered rows for a school's timetable grid so every working day aligns cleanly
     * (including days like Saturday with fewer trailing periods).
     */
    fun buildGridRows(structure: SchoolPeriodStructure): List<TimetableGridRow> {
        val days = structure.workingDays.sortedBy { it.sortOrder }
        if (days.isEmpty()) return emptyList()

        // Pick the day with the most slots as the anchor template (typically Monday)
        val anchorDay = days.maxByOrNull { structure.slotsFor(it).size } ?: days.first()
        val anchorSlots = structure.slotsFor(anchorDay)
        if (anchorSlots.isEmpty()) return emptyList()

        return anchorSlots.mapIndexed { rowIdx, anchorSlot ->
            val mapped = linkedMapOf<DayOfWeekCode, PeriodSlot?>()
            for (day in days) {
                val daySlots = structure.slotsFor(day)
                // Prefer exact time + type match, else positional match if same type
                val exactMatch = daySlots.firstOrNull {
                    it.type == anchorSlot.type &&
                        it.startMinutes == anchorSlot.startMinutes &&
                        it.endMinutes == anchorSlot.endMinutes
                }
                val positionalMatch = daySlots.getOrNull(rowIdx)?.takeIf { it.type == anchorSlot.type }
                mapped[day] = exactMatch ?: positionalMatch
            }
            TimetableGridRow(
                rowIndex = rowIdx,
                label = anchorSlot.label,
                timeLabel = "${anchorSlot.startTimeFormatted}–${anchorSlot.endTimeFormatted}",
                type = anchorSlot.type,
                representativeStartMinutes = anchorSlot.startMinutes,
                representativeEndMinutes = anchorSlot.endMinutes,
                slotByDay = mapped
            )
        }
    }

    /**
     * Evaluates ALL hard conflicts (which block saving) and soft warnings (which allow saving)
     * for placing [candidate] into the organisation's timetable for [candidate.session].
     */
    fun validateCellPlacement(
        candidate: TimetableCell,
        existingCellsInSession: List<TimetableCell>,
        teachers: List<TeacherProfile>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        classAssignments: List<ClassSectionAssignment>,
        subjectRules: List<SubjectRule>,
        schoolNamesById: Map<String, String>,
        allowReplaceSameCellId: Boolean = true
    ): CellValidationOutcome {
        val hardConflicts = mutableListOf<TimetableHardConflict>()
        val softWarnings = mutableListOf<TimetableSoftWarning>()

        if (candidate.subjectName.isBlank()) {
            hardConflicts.add(
                TimetableHardConflict(
                    type = HardConflictType.CLASS_DOUBLE_BOOKED,
                    message = "Please select a subject before saving.",
                    primaryCell = candidate,
                    day = candidate.day
                )
            )
            return CellValidationOutcome(hardConflicts, softWarnings)
        }

        val teachersById = teachers.associateBy { it.id }
        val teacher = teachersById[candidate.teacherId]
        val teacherDisplayName = teacher?.name?.ifBlank { candidate.teacherName }
            ?: candidate.teacherName.ifBlank { "Selected teacher" }

        val candidateSlot = resolveSlot(
            schoolId = candidate.schoolId,
            day = candidate.day,
            slotId = candidate.slotId,
            periodStructuresBySchool = periodStructuresBySchool
        )

        if (candidateSlot != null && !candidateSlot.type.countsAsTeachingPeriod) {
            hardConflicts.add(
                TimetableHardConflict(
                    type = HardConflictType.CLASS_DOUBLE_BOOKED,
                    message = "${candidateSlot.label} is not an editable teaching period.",
                    primaryCell = candidate,
                    day = candidate.day
                )
            )
            return CellValidationOutcome(hardConflicts, softWarnings)
        }

        val otherCells = existingCellsInSession.filter { cell ->
            cell.session.equals(candidate.session, ignoreCase = true) &&
                (!allowReplaceSameCellId || cell.id != candidate.id)
        }

        // ---------------------------------------------------------------------
        // HARD CHECK 1: Teacher must be marked active
        // ---------------------------------------------------------------------
        if (teacher != null && !teacher.active) {
            hardConflicts.add(
                TimetableHardConflict(
                    type = HardConflictType.TEACHER_INACTIVE,
                    message = "$teacherDisplayName is marked inactive and cannot be scheduled.",
                    involvedCellIds = listOf(candidate.id),
                    primaryCell = candidate,
                    teacherId = teacher.id,
                    day = candidate.day
                )
            )
        }

        // ---------------------------------------------------------------------
        // HARD CHECK 2: Teacher on their weekly off day
        // ---------------------------------------------------------------------
        if (teacher != null && candidate.day in teacher.weeklyOffDays) {
            hardConflicts.add(
                TimetableHardConflict(
                    type = HardConflictType.TEACHER_OFF_DAY,
                    message = "$teacherDisplayName has ${candidate.day.fullLabel} marked as a weekly off day.",
                    involvedCellIds = listOf(candidate.id),
                    primaryCell = candidate,
                    teacherId = teacher.id,
                    day = candidate.day
                )
            )
        }

        // ---------------------------------------------------------------------
        // HARD CHECK 3: Class with two subjects in the same slot (or overlapping time)
        // ---------------------------------------------------------------------
        for (other in otherCells) {
            val sameClassSection = other.schoolId == candidate.schoolId &&
                other.className.equals(candidate.className, ignoreCase = true) &&
                other.section.equals(candidate.section, ignoreCase = true) &&
                other.day == candidate.day
            if (!sameClassSection) continue

            val sameSlotId = other.slotId == candidate.slotId
            val otherSlot = resolveSlot(other.schoolId, other.day, other.slotId, periodStructuresBySchool)
            val timeOverlaps = candidateSlot != null && otherSlot != null &&
                slotsOverlap(candidateSlot.startMinutes, candidateSlot.endMinutes, otherSlot.startMinutes, otherSlot.endMinutes)

            if (sameSlotId || timeOverlaps) {
                val timeText = candidateSlot?.let { "${it.startTimeFormatted}–${it.endTimeFormatted}" } ?: candidate.slotId
                hardConflicts.add(
                    TimetableHardConflict(
                        type = HardConflictType.CLASS_DOUBLE_BOOKED,
                        message = "Class ${candidate.classSectionLabel} already has ${other.subjectName} scheduled on ${candidate.day.fullLabel} ($timeText).",
                        involvedCellIds = listOf(candidate.id, other.id).distinct(),
                        primaryCell = candidate,
                        conflictingCell = other,
                        day = candidate.day
                    )
                )
            }
        }

        // ---------------------------------------------------------------------
        // HARD CHECK 4: Teacher in two places at the same time (ACROSS ALL SCHOOLS using real clock times)
        // ---------------------------------------------------------------------
        if (candidate.teacherId.isNotBlank()) {
            for (other in otherCells) {
                if (other.teacherId != candidate.teacherId || other.day != candidate.day) continue
                val otherSlot = resolveSlot(other.schoolId, other.day, other.slotId, periodStructuresBySchool)
                val overlaps = when {
                    candidateSlot != null && otherSlot != null ->
                        slotsOverlap(candidateSlot.startMinutes, candidateSlot.endMinutes, otherSlot.startMinutes, otherSlot.endMinutes)
                    else -> other.schoolId == candidate.schoolId && other.slotId == candidate.slotId
                }
                if (overlaps) {
                    val otherSchoolName = schoolNamesById[other.schoolId] ?: other.schoolId
                    val candTime = candidateSlot?.let { "${it.startTimeFormatted}–${it.endTimeFormatted}" } ?: candidate.slotId
                    val otherTime = otherSlot?.let { "${it.startTimeFormatted}–${it.endTimeFormatted}" } ?: other.slotId
                    val timeNote = if (candTime == otherTime) candTime else "$candTime overlaps $otherTime"
                    hardConflicts.add(
                        TimetableHardConflict(
                            type = HardConflictType.TEACHER_DOUBLE_BOOKED,
                            message = "$teacherDisplayName is already teaching ${other.subjectName} in ${other.classSectionLabel} ($otherSchoolName) on ${candidate.day.fullLabel} at $timeNote.",
                            involvedCellIds = listOf(candidate.id, other.id).distinct(),
                            primaryCell = candidate,
                            conflictingCell = other,
                            teacherId = candidate.teacherId,
                            day = candidate.day
                        )
                    )
                }
            }
        }

        // ---------------------------------------------------------------------
        // HARD CHECK 5: Teacher over their max periods per day (across all schools)
        // ---------------------------------------------------------------------
        if (teacher != null) {
            val teacherDayCount = otherCells.count {
                it.teacherId == teacher.id && it.day == candidate.day
            } + 1
            if (teacherDayCount > teacher.maxPeriodsPerDay) {
                hardConflicts.add(
                    TimetableHardConflict(
                        type = HardConflictType.TEACHER_OVER_DAILY_LIMIT,
                        message = "$teacherDisplayName would have $teacherDayCount periods on ${candidate.day.fullLabel}, exceeding their daily limit of ${teacher.maxPeriodsPerDay}.",
                        involvedCellIds = listOf(candidate.id),
                        primaryCell = candidate,
                        teacherId = teacher.id,
                        day = candidate.day
                    )
                )
            }
        }

        // ---------------------------------------------------------------------
        // SOFT WARNINGS (Allow save, show warning)
        // ---------------------------------------------------------------------
        val rule = subjectRules.firstOrNull {
            it.schoolId == candidate.schoolId &&
                it.subjectName.trim().equals(candidate.subjectName.trim(), ignoreCase = true)
        }

        // Soft 1: Same subject twice (or more) in one day for this class-section
        val sameClassDayCells = otherCells.filter {
            it.schoolId == candidate.schoolId &&
                it.className.equals(candidate.className, ignoreCase = true) &&
                it.section.equals(candidate.section, ignoreCase = true) &&
                it.day == candidate.day &&
                it.subjectName.trim().equals(candidate.subjectName.trim(), ignoreCase = true)
        }
        if (sameClassDayCells.isNotEmpty()) {
            val totalSameSubjectToday = sameClassDayCells.size + 1
            val allowAsDouble = rule?.allowDoublePeriod == true && totalSameSubjectToday == 2 &&
                arePeriodsAdjacentInSchool(
                    schoolId = candidate.schoolId,
                    day = candidate.day,
                    slotIdA = candidate.slotId,
                    slotIdB = sameClassDayCells.first().slotId,
                    periodStructuresBySchool = periodStructuresBySchool
                )
            if (!allowAsDouble) {
                val hint = if (rule?.allowDoublePeriod == true) {
                    " (Double period is allowed only for 2 consecutive periods.)"
                } else {
                    ""
                }
                softWarnings.add(
                    TimetableSoftWarning(
                        type = SoftWarningType.SAME_SUBJECT_TWICE_IN_DAY,
                        message = "${candidate.subjectName} appears $totalSameSubjectToday times on ${candidate.day.fullLabel} for ${candidate.classSectionLabel}.$hint"
                    )
                )
            }
        }

        // Soft 2: Too many consecutive periods for a teacher (over teacher.maxConsecutivePeriods)
        if (teacher != null && candidateSlot != null) {
            val teacherDaySlots = buildList {
                add(candidateSlot.startMinutes to candidateSlot.endMinutes)
                otherCells.filter { it.teacherId == teacher.id && it.day == candidate.day }
                    .forEach { other ->
                        resolveSlot(other.schoolId, other.day, other.slotId, periodStructuresBySchool)?.let { s ->
                            add(s.startMinutes to s.endMinutes)
                        }
                    }
            }
            val maxStreak = computeMaxConsecutiveStreak(teacherDaySlots)
            if (maxStreak > teacher.maxConsecutivePeriods) {
                softWarnings.add(
                    TimetableSoftWarning(
                        type = SoftWarningType.TEACHER_TOO_MANY_CONSECUTIVE,
                        message = "$teacherDisplayName would teach $maxStreak consecutive periods on ${candidate.day.fullLabel} (limit: ${teacher.maxConsecutivePeriods})."
                    )
                )
            }
        }

        // Soft 3: Subject weekly period count mismatch against Phase 4 class assignment
        val classAssignment = classAssignments.firstOrNull {
            it.schoolId == candidate.schoolId &&
                it.className.equals(candidate.className, ignoreCase = true) &&
                it.section.equals(candidate.section, ignoreCase = true)
        }
        val assignedWeekly = classAssignment?.subjectAssignments
            ?.firstOrNull { it.subjectName.trim().equals(candidate.subjectName.trim(), ignoreCase = true) }
            ?.periodsPerWeek ?: 0
        if (assignedWeekly > 0) {
            val projectedWeekly = otherCells.count {
                it.schoolId == candidate.schoolId &&
                    it.className.equals(candidate.className, ignoreCase = true) &&
                    it.section.equals(candidate.section, ignoreCase = true) &&
                    it.subjectName.trim().equals(candidate.subjectName.trim(), ignoreCase = true)
            } + 1
            if (projectedWeekly > assignedWeekly) {
                softWarnings.add(
                    TimetableSoftWarning(
                        type = SoftWarningType.SUBJECT_WEEKLY_COUNT_MISMATCH,
                        message = "${candidate.subjectName} will have $projectedWeekly/$assignedWeekly weekly periods in ${candidate.classSectionLabel} (exceeds assignment)."
                    )
                )
            }
        }

        // Soft 4: Preferred time of day from Phase 4 subject rules
        if (rule != null && candidateSlot != null) {
            when (rule.preferredTimeOfDay) {
                PreferredTimeOfDay.MORNING -> {
                    if (candidateSlot.startMinutes >= AFTERNOON_START_MINUTES) {
                        softWarnings.add(
                            TimetableSoftWarning(
                                type = SoftWarningType.PREFERRED_TIME_OF_DAY,
                                message = "${candidate.subjectName} prefers Morning (before 12:00), but this slot starts at ${candidateSlot.startTimeFormatted}."
                            )
                        )
                    }
                }
                PreferredTimeOfDay.AFTERNOON -> {
                    if (candidateSlot.startMinutes < AFTERNOON_START_MINUTES) {
                        softWarnings.add(
                            TimetableSoftWarning(
                                type = SoftWarningType.PREFERRED_TIME_OF_DAY,
                                message = "${candidate.subjectName} prefers Afternoon (12:00 onwards), but this slot starts at ${candidateSlot.startTimeFormatted}."
                            )
                        )
                    }
                }
                PreferredTimeOfDay.NO_PREFERENCE -> Unit
            }
        }

        return CellValidationOutcome(hardConflicts = hardConflicts, softWarnings = softWarnings)
    }

    /**
     * Post-sync full Clash Report across ALL cells of a session in the organisation.
     * Never silently overwrites anything — reports every hard clash so admins/editors can jump and fix.
     */
    fun detectAllClashes(
        cellsInSession: List<TimetableCell>,
        teachers: List<TeacherProfile>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        schoolNamesById: Map<String, String>
    ): List<TimetableHardConflict> {
        val clashes = mutableListOf<TimetableHardConflict>()
        val teachersById = teachers.associateBy { it.id }

        // 1. Pairwise teacher double-booking (across all schools using real clock times)
        //    and pairwise class-section double-booking
        val byDay = cellsInSession.groupBy { it.day }
        for ((day, dayCells) in byDay) {
            for (i in dayCells.indices) {
                val a = dayCells[i]
                val slotA = resolveSlot(a.schoolId, day, a.slotId, periodStructuresBySchool)
                for (j in i + 1 until dayCells.size) {
                    val b = dayCells[j]
                    val slotB = resolveSlot(b.schoolId, day, b.slotId, periodStructuresBySchool)

                    val overlaps = when {
                        slotA != null && slotB != null ->
                            slotsOverlap(slotA.startMinutes, slotA.endMinutes, slotB.startMinutes, slotB.endMinutes)
                        else -> a.schoolId == b.schoolId && a.slotId == b.slotId
                    }
                    if (!overlaps) continue

                    // Teacher double-booked across same or different schools
                    if (a.teacherId.isNotBlank() && a.teacherId == b.teacherId) {
                        val tName = teachersById[a.teacherId]?.name?.ifBlank { a.teacherName }
                            ?: a.teacherName.ifBlank { a.teacherId }
                        val schA = schoolNamesById[a.schoolId] ?: a.schoolId
                        val schB = schoolNamesById[b.schoolId] ?: b.schoolId
                        val timeA = slotA?.let { "${it.startTimeFormatted}–${it.endTimeFormatted}" } ?: a.slotId
                        val timeB = slotB?.let { "${it.startTimeFormatted}–${it.endTimeFormatted}" } ?: b.slotId
                        clashes.add(
                            TimetableHardConflict(
                                type = HardConflictType.TEACHER_DOUBLE_BOOKED,
                                message = "$tName double-booked on ${day.fullLabel}: ${a.classSectionLabel} ($schA, $timeA) & ${b.classSectionLabel} ($schB, $timeB).",
                                involvedCellIds = listOf(a.id, b.id),
                                primaryCell = a,
                                conflictingCell = b,
                                teacherId = a.teacherId,
                                day = day
                            )
                        )
                    }

                    // Class double-booked in overlapping slot
                    val sameClass = a.schoolId == b.schoolId &&
                        a.className.equals(b.className, ignoreCase = true) &&
                        a.section.equals(b.section, ignoreCase = true)
                    if (sameClass) {
                        val schName = schoolNamesById[a.schoolId] ?: a.schoolId
                        clashes.add(
                            TimetableHardConflict(
                                type = HardConflictType.CLASS_DOUBLE_BOOKED,
                                message = "${a.classSectionLabel} ($schName) has two overlapping subjects on ${day.fullLabel}: ${a.subjectName} & ${b.subjectName}.",
                                involvedCellIds = listOf(a.id, b.id),
                                primaryCell = a,
                                conflictingCell = b,
                                day = day
                            )
                        )
                    }
                }
            }

            // 2. Per-cell checks: inactive teacher, off day
            for (cell in dayCells) {
                val teacher = teachersById[cell.teacherId] ?: continue
                val schName = schoolNamesById[cell.schoolId] ?: cell.schoolId
                if (!teacher.active) {
                    clashes.add(
                        TimetableHardConflict(
                            type = HardConflictType.TEACHER_INACTIVE,
                            message = "${teacher.name} is inactive but scheduled for ${cell.subjectName} in ${cell.classSectionLabel} ($schName) on ${day.shortLabel}.",
                            involvedCellIds = listOf(cell.id),
                            primaryCell = cell,
                            teacherId = teacher.id,
                            day = day
                        )
                    )
                }
                if (day in teacher.weeklyOffDays) {
                    clashes.add(
                        TimetableHardConflict(
                            type = HardConflictType.TEACHER_OFF_DAY,
                            message = "${teacher.name} is scheduled on their off day (${day.fullLabel}) for ${cell.classSectionLabel} ($schName).",
                            involvedCellIds = listOf(cell.id),
                            primaryCell = cell,
                            teacherId = teacher.id,
                            day = day
                        )
                    )
                }
            }

            // 3. Per-teacher daily limit check
            val cellsByTeacher = dayCells.filter { it.teacherId.isNotBlank() }.groupBy { it.teacherId }
            for ((tId, tCells) in cellsByTeacher) {
                val teacher = teachersById[tId] ?: continue
                if (tCells.size > teacher.maxPeriodsPerDay) {
                    clashes.add(
                        TimetableHardConflict(
                            type = HardConflictType.TEACHER_OVER_DAILY_LIMIT,
                            message = "${teacher.name} has ${tCells.size} periods on ${day.fullLabel}, exceeding daily limit of ${teacher.maxPeriodsPerDay}.",
                            involvedCellIds = tCells.map { it.id },
                            primaryCell = tCells.first(),
                            teacherId = teacher.id,
                            day = day
                        )
                    )
                }
            }
        }
        return clashes
    }

    /**
     * Weekly subject progress chips ("Maths 4/6") for one class-section in a school & session.
     */
    fun buildClassSubjectProgress(
        schoolId: String,
        session: String,
        className: String,
        section: String,
        classSubjects: List<String>,
        cellsInSession: List<TimetableCell>,
        classAssignments: List<ClassSectionAssignment>
    ): List<SubjectWeeklyProgress> {
        val assignment = classAssignments.firstOrNull {
            it.schoolId == schoolId &&
                it.className.equals(className, ignoreCase = true) &&
                it.section.equals(section, ignoreCase = true)
        }
        val assignedMap = assignment?.subjectAssignments
            ?.associate { it.subjectName.trim().lowercase() to it.periodsPerWeek }
            .orEmpty()

        val classCells = cellsInSession.filter {
            it.schoolId == schoolId &&
                it.session.equals(session, ignoreCase = true) &&
                it.className.equals(className, ignoreCase = true) &&
                it.section.equals(section, ignoreCase = true)
        }
        val placedCounts = classCells.groupingBy { it.subjectName.trim().lowercase() }.eachCount()

        val allSubjects = buildList {
            classSubjects.forEach { add(it) }
            assignment?.subjectAssignments?.forEach { sa ->
                if (none { it.equals(sa.subjectName, ignoreCase = true) }) add(sa.subjectName)
            }
            classCells.forEach { c ->
                if (none { it.equals(c.subjectName, ignoreCase = true) }) add(c.subjectName)
            }
        }

        return allSubjects.map { sub ->
            val key = sub.trim().lowercase()
            SubjectWeeklyProgress(
                subjectName = sub,
                placedCount = placedCounts[key] ?: 0,
                assignedCount = assignedMap[key] ?: 0
            )
        }
    }

    /**
     * Orders teachers for the Cell Editor picker:
     * Tier 0: Teachers assigned to this exact (school, class, section, subject) in Phase 4
     * Tier 1: Other teachers who list this subject in their teachable subjects
     * Tier 2: Remaining teachers
     * Computes live Free / Busy / Off-day / Daily-limit / Inactive badge for each teacher.
     */
    fun buildTeacherPickerOptions(
        schoolId: String,
        session: String,
        className: String,
        section: String,
        day: DayOfWeekCode,
        slotId: String,
        subjectName: String,
        currentCellId: String,
        teachers: List<TeacherProfile>,
        cellsInSession: List<TimetableCell>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        classAssignments: List<ClassSectionAssignment>,
        schoolNamesById: Map<String, String>
    ): List<TeacherPickerOption> {
        val assignedTeacherIds = classAssignments
            .filter {
                it.schoolId == schoolId &&
                    it.className.equals(className, ignoreCase = true) &&
                    it.section.equals(section, ignoreCase = true)
            }
            .flatMap { it.subjectAssignments }
            .filter { it.subjectName.trim().equals(subjectName.trim(), ignoreCase = true) && it.teacherId.isNotBlank() }
            .map { it.teacherId }
            .toSet()

        val targetSlot = resolveSlot(schoolId, day, slotId, periodStructuresBySchool)
        val otherDayCells = cellsInSession.filter {
            it.session.equals(session, ignoreCase = true) &&
                it.day == day &&
                it.id != currentCellId
        }

        return teachers.map { teacher ->
            val tier = when {
                teacher.id in assignedTeacherIds -> 0
                subjectName.isNotBlank() && teacher.subjects.any { it.trim().equals(subjectName.trim(), ignoreCase = true) } -> 1
                else -> 2
            }
            val teacherDayCells = otherDayCells.filter { it.teacherId == teacher.id }
            val periodsToday = teacherDayCells.size

            val busyCell = teacherDayCells.firstOrNull { other ->
                val otherSlot = resolveSlot(other.schoolId, day, other.slotId, periodStructuresBySchool)
                when {
                    targetSlot != null && otherSlot != null ->
                        slotsOverlap(targetSlot.startMinutes, targetSlot.endMinutes, otherSlot.startMinutes, otherSlot.endMinutes)
                    else -> other.schoolId == schoolId && other.slotId == slotId
                }
            }

            val (availability, detail) = when {
                !teacher.active -> TeacherSlotAvailability.INACTIVE to "Inactive"
                day in teacher.weeklyOffDays -> TeacherSlotAvailability.OFF_DAY to "Weekly off day (${day.shortLabel})"
                busyCell != null -> {
                    val busySchool = schoolNamesById[busyCell.schoolId] ?: busyCell.schoolId
                    TeacherSlotAvailability.BUSY to "Busy: ${busyCell.classSectionLabel} ($busySchool)"
                }
                periodsToday >= teacher.maxPeriodsPerDay ->
                    TeacherSlotAvailability.DAILY_LIMIT_REACHED to "Daily limit reached ($periodsToday/${teacher.maxPeriodsPerDay})"
                else ->
                    TeacherSlotAvailability.FREE to "Free • $periodsToday/${teacher.maxPeriodsPerDay} today"
            }

            TeacherPickerOption(
                teacher = teacher,
                tier = tier,
                availability = availability,
                statusDetail = detail,
                periodsToday = periodsToday
            )
        }.sortedWith(
            compareBy<TeacherPickerOption>(
                { it.tier },
                { if (it.availability == TeacherSlotAvailability.FREE) 0 else 1 },
                { it.periodsToday },
                { it.teacher.name.lowercase() }
            )
        )
    }

    /**
     * Free-periods view: for a chosen (day, periodSlot) of [schoolId], lists all active teachers
     * who are free during that real clock-time window across ALL schools (and not on their off day),
     * together with their total periods count for that day.
     */
    fun findFreeTeachers(
        schoolId: String,
        session: String,
        day: DayOfWeekCode,
        slot: PeriodSlot,
        teachers: List<TeacherProfile>,
        cellsInSession: List<TimetableCell>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>
    ): List<FreeTeacherEntry> {
        val dayCells = cellsInSession.filter {
            it.session.equals(session, ignoreCase = true) && it.day == day
        }

        return teachers.filter { teacher ->
            if (!teacher.active) return@filter false
            if (day in teacher.weeklyOffDays) return@filter false

            val busy = dayCells.any { cell ->
                if (cell.teacherId != teacher.id) return@any false
                val cellSlot = resolveSlot(cell.schoolId, day, cell.slotId, periodStructuresBySchool)
                when {
                    cellSlot != null -> slotsOverlap(slot.startMinutes, slot.endMinutes, cellSlot.startMinutes, cellSlot.endMinutes)
                    else -> cell.schoolId == schoolId && cell.slotId == slot.id
                }
            }
            !busy
        }.map { teacher ->
            val todayCount = dayCells.count { it.teacherId == teacher.id }
            FreeTeacherEntry(
                teacher = teacher,
                periodsToday = todayCount,
                maxPeriodsPerDay = teacher.maxPeriodsPerDay,
                worksInSelectedSchool = teacher.schoolIds.isEmpty() || schoolId in teacher.schoolIds
            )
        }.sortedWith(
            compareBy<FreeTeacherEntry>(
                { if (it.worksInSelectedSchool) 0 else 1 },
                { it.periodsToday },
                { it.teacher.name.lowercase() }
            )
        )
    }

    /**
     * Teacher Timetable across ALL schools in the organisation for a given teacher & session.
     */
    fun buildTeacherTimetable(
        teacherId: String,
        fallbackTeacherName: String,
        teacherProfile: TeacherProfile?,
        session: String,
        cellsInSession: List<TimetableCell>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        schoolNamesById: Map<String, String>
    ): TeacherTimetableSummary {
        val teacherCells = cellsInSession.filter {
            it.session.equals(session, ignoreCase = true) && it.teacherId == teacherId
        }
        val itemsByDay = linkedMapOf<DayOfWeekCode, MutableList<TeacherWeekSlotItem>>()
        DayOfWeekCode.entries.forEach { itemsByDay[it] = mutableListOf() }

        for (cell in teacherCells) {
            val slot = resolveSlot(cell.schoolId, cell.day, cell.slotId, periodStructuresBySchool)
            val start = slot?.startMinutes ?: 0
            val end = slot?.endMinutes ?: 0
            val label = slot?.label ?: cell.slotId
            itemsByDay.getOrPut(cell.day) { mutableListOf() }.add(
                TeacherWeekSlotItem(
                    day = cell.day,
                    startMinutes = start,
                    endMinutes = end,
                    slotLabel = label,
                    schoolId = cell.schoolId,
                    schoolName = schoolNamesById[cell.schoolId] ?: cell.schoolId,
                    className = cell.className,
                    section = cell.section,
                    subjectName = cell.subjectName,
                    cell = cell
                )
            )
        }
        itemsByDay.values.forEach { list -> list.sortBy { it.startMinutes } }

        val assignedSchools = teacherCells.map { it.schoolId }.toSet()
        val weeklyLimit = if (teacherProfile != null) {
            Phase4Validator.computeTeacherWeeklyLimit(
                teacher = teacherProfile,
                periodStructuresBySchool = periodStructuresBySchool,
                assignedSchoolIds = assignedSchools
            ).second
        } else {
            Phase4Validator.DEFAULT_MAX_PERIODS_PER_DAY * 6
        }

        // Compute available teaching slots across the teacher's primary working school(s)
        val primarySchoolIds = teacherProfile?.schoolIds?.filter { it.isNotBlank() }.orEmpty()
            .ifEmpty { assignedSchools.toList() }
        val availableWeeklySlots = if (primarySchoolIds.isNotEmpty()) {
            val primaryStruct = periodStructuresBySchool[primarySchoolIds.first()]
                ?: Phase4Validator.buildDefaultPeriodStructure(primarySchoolIds.first())
            val offDays = teacherProfile?.weeklyOffDays?.toSet().orEmpty()
            primaryStruct.workingDays.filter { it !in offDays }.sumOf { primaryStruct.teachingPeriodsPerDay(it) }
        } else {
            weeklyLimit
        }

        val totalPlaced = teacherCells.size
        val freePeriods = (availableWeeklySlots - totalPlaced).coerceAtLeast(0)

        return TeacherTimetableSummary(
            teacherId = teacherId,
            teacherName = teacherProfile?.name?.ifBlank { fallbackTeacherName } ?: fallbackTeacherName,
            totalPeriodsPerWeek = totalPlaced,
            weeklyLimit = weeklyLimit,
            freePeriodsPerWeek = freePeriods,
            itemsByDay = itemsByDay.mapValues { it.value.toList() }
        )
    }

    /**
     * Latest-edit-wins conflict detector for Timetable cells (same pattern as Phase 3 Marks).
     * When a confirmed server snapshot arrives (!isFromCache && !hasPendingWrites), notifies the
     * user if another phone's newer edit replaced their offline edit.
     */
    fun detectTimetableCellConflicts(
        pendingEdits: List<LocalPendingTimetableEdit>,
        confirmedServerCells: List<TimetableCell>,
        hasAnyConfirmedSnapshot: Boolean,
        currentUid: String,
        nowMs: Long = System.currentTimeMillis()
    ): TimetableConflictDetectionResult {
        if (pendingEdits.isEmpty()) {
            return TimetableConflictDetectionResult(emptyList(), emptyList())
        }
        val byId = confirmedServerCells.associateBy { it.id }
        val remaining = mutableListOf<LocalPendingTimetableEdit>()
        val conflicts = mutableListOf<TimetableConflictNotice>()

        for (edit in pendingEdits) {
            if (nowMs - edit.clientEditedAtMs > STALE_PENDING_TTL_MS) continue
            val serverCell = byId[edit.cellId]
            val day = DayOfWeekCode.fromRaw(edit.dayWire) ?: DayOfWeekCode.MON

            if (serverCell == null) {
                if (!hasAnyConfirmedSnapshot) {
                    remaining.add(edit)
                    continue
                }
                if (edit.wasCleared) {
                    // Our delete succeeded cleanly
                    continue
                }
                // Our offline placement was cleared by someone else on the server
                conflicts.add(
                    TimetableConflictNotice(
                        cellId = edit.cellId,
                        schoolId = edit.schoolId,
                        classSectionLabel = "${edit.className}-${edit.section}",
                        day = day,
                        slotId = edit.slotId,
                        yourSummary = "${edit.localSubjectName} (${edit.localTeacherName.ifBlank { edit.localTeacherId }})",
                        winningSummary = "(Cleared)",
                        winnerName = "Another staff member",
                        detectedAtMs = nowMs
                    )
                )
                continue
            }

            if (serverCell.isFromCache || serverCell.hasPendingWrites) {
                remaining.add(edit)
                continue
            }

            val updatedBySelf = serverCell.updatedByUid == currentUid || serverCell.updatedByUid.isBlank()
            val sameContent = !edit.wasCleared &&
                serverCell.subjectName.trim().equals(edit.localSubjectName.trim(), ignoreCase = true) &&
                serverCell.teacherId == edit.localTeacherId &&
                serverCell.locked == edit.localLocked

            if (updatedBySelf || sameContent) {
                continue
            }

            val yourDesc = if (edit.wasCleared) {
                "(Cleared)"
            } else {
                "${edit.localSubjectName} (${edit.localTeacherName.ifBlank { edit.localTeacherId }})"
            }
            val winDesc = "${serverCell.subjectName} (${serverCell.teacherName.ifBlank { serverCell.teacherId }})"
            conflicts.add(
                TimetableConflictNotice(
                    cellId = edit.cellId,
                    schoolId = edit.schoolId,
                    classSectionLabel = "${edit.className}-${edit.section}",
                    day = day,
                    slotId = edit.slotId,
                    yourSummary = yourDesc,
                    winningSummary = winDesc,
                    winnerName = serverCell.updatedByName.ifBlank { "Another staff member" },
                    detectedAtMs = nowMs
                )
            )
        }

        return TimetableConflictDetectionResult(
            remainingPending = remaining,
            detectedConflicts = conflicts
        )
    }

    internal fun slotsOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean =
        startA < endB && startB < endA

    internal fun computeMaxConsecutiveStreak(intervals: List<Pair<Int, Int>>): Int {
        if (intervals.isEmpty()) return 0
        val sorted = intervals.sortedBy { it.first }
        var best = 1
        var current = 1
        for (i in 1 until sorted.size) {
            val prevEnd = sorted[i - 1].second
            val currStart = sorted[i].first
            val gap = currStart - prevEnd
            if (gap in 0..CONSECUTIVE_MAX_GAP_MINUTES) {
                current++
                if (current > best) best = current
            } else if (gap > CONSECUTIVE_MAX_GAP_MINUTES) {
                current = 1
            }
        }
        return best
    }

    private fun arePeriodsAdjacentInSchool(
        schoolId: String,
        day: DayOfWeekCode,
        slotIdA: String,
        slotIdB: String,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>
    ): Boolean {
        val structure = periodStructuresBySchool[schoolId] ?: Phase4Validator.buildDefaultPeriodStructure(schoolId)
        val teachingSlots = structure.slotsFor(day).filter { it.type.countsAsTeachingPeriod }
        val idxA = teachingSlots.indexOfFirst { it.id == slotIdA }
        val idxB = teachingSlots.indexOfFirst { it.id == slotIdB }
        return idxA >= 0 && idxB >= 0 && kotlin.math.abs(idxA - idxB) == 1
    }
}
