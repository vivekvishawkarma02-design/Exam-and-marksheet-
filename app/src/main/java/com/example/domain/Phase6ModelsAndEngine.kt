package com.example.domain

import com.example.data.cloud.StableDocId
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.Random
import kotlin.math.abs

// =============================================================================
// 1. PURE DATE UTILITIES (minSdk 24 safe — no java.time dependency)
// =============================================================================
object Phase6DateUtils {
    private val ISO_DATE_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

    fun isValidIsoDate(raw: String): Boolean {
        val m = ISO_DATE_REGEX.matchEntire(raw.trim()) ?: return false
        val year = m.groupValues[1].toIntOrNull() ?: return false
        val month = m.groupValues[2].toIntOrNull() ?: return false
        val day = m.groupValues[3].toIntOrNull() ?: return false
        if (year !in 2000..2100 || month !in 1..12 || day !in 1..31) return false
        val cal = GregorianCalendar(year, month - 1, 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return day <= maxDay
    }

    fun todayIsoDate(nowMs: Long = System.currentTimeMillis()): String {
        val cal = GregorianCalendar().apply { timeInMillis = nowMs }
        return formatCalendar(cal)
    }

    fun todayIso(nowMs: Long = System.currentTimeMillis()): String = todayIsoDate(nowMs)

    fun monthPrefix(isoDate: String): String {
        val clean = isoDate.trim()
        return if (clean.length >= 7) clean.substring(0, 7) else clean
    }

    fun dayOfWeekForDate(isoDate: String): DayOfWeekCode? {
        val cal = parseToCalendar(isoDate) ?: return null
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> DayOfWeekCode.MON
            Calendar.TUESDAY -> DayOfWeekCode.TUE
            Calendar.WEDNESDAY -> DayOfWeekCode.WED
            Calendar.THURSDAY -> DayOfWeekCode.THU
            Calendar.FRIDAY -> DayOfWeekCode.FRI
            Calendar.SATURDAY -> DayOfWeekCode.SAT
            Calendar.SUNDAY -> DayOfWeekCode.SUN
            else -> null
        }
    }

    fun addDays(isoDate: String, deltaDays: Int): String {
        val cal = parseToCalendar(isoDate) ?: return isoDate
        cal.add(Calendar.DAY_OF_MONTH, deltaDays)
        return formatCalendar(cal)
    }

    /**
     * Returns Monday..Sunday ISO dates for the calendar week containing [isoDate].
     */
    fun weekDateRangeFor(isoDate: String): Pair<String, String> {
        val cal = parseToCalendar(isoDate) ?: return isoDate to isoDate
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val offsetFromMonday = if (dow == Calendar.SUNDAY) -6 else (Calendar.MONDAY - dow)
        cal.add(Calendar.DAY_OF_MONTH, offsetFromMonday)
        val monday = formatCalendar(cal)
        cal.add(Calendar.DAY_OF_MONTH, 6)
        val sunday = formatCalendar(cal)
        return monday to sunday
    }

    fun isDateWithinRange(date: String, startDate: String, endDate: String): Boolean {
        val d = date.trim()
        val s = startDate.trim()
        val e = endDate.trim()
        if (!isValidIsoDate(d) || !isValidIsoDate(s) || !isValidIsoDate(e)) return false
        return d >= s && d <= e
    }

    fun enumerateDateRange(startDate: String, endDate: String, maxDays: Int = 90): List<String> {
        val startCal = parseToCalendar(startDate) ?: return emptyList()
        val endCal = parseToCalendar(endDate) ?: return emptyList()
        if (startCal.timeInMillis > endCal.timeInMillis) return emptyList()
        val out = mutableListOf<String>()
        val cur = startCal.clone() as GregorianCalendar
        while (cur.timeInMillis <= endCal.timeInMillis && out.size < maxDays) {
            out.add(formatCalendar(cur))
            cur.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    fun formatDisplayDate(isoDate: String): String {
        val dow = dayOfWeekForDate(isoDate)?.shortLabel ?: ""
        return if (dow.isNotEmpty()) "$isoDate ($dow)" else isoDate
    }

    private fun parseToCalendar(isoDate: String): GregorianCalendar? {
        if (!isValidIsoDate(isoDate)) return null
        val m = ISO_DATE_REGEX.matchEntire(isoDate.trim()) ?: return null
        val year = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val day = m.groupValues[3].toInt()
        return GregorianCalendar(year, month - 1, day, 12, 0, 0)
    }

    private fun formatCalendar(cal: Calendar): String =
        String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
}

// =============================================================================
// 2. AUTO-GENERATE MODELS & UNDO SNAPSHOT
// =============================================================================
enum class AutoGenerateScopeMode(val label: String) {
    WHOLE_SCHOOL("Whole school (all classes & sections)"),
    SINGLE_CLASS_SECTION("Single class & section")
}

data class AutoGenerateConfig(
    val scopeMode: AutoGenerateScopeMode = AutoGenerateScopeMode.WHOLE_SCHOOL,
    val schoolId: String = "",
    val className: String = "",
    val section: String = "A",
    val session: String = "2026-2027",
    val keepExistingUnlockedCells: Boolean = false,
    val timeLimitSeconds: Int = 20,
    val seed: Long = System.currentTimeMillis()
)

data class AutoGenerateProgress(
    val isRunning: Boolean = false,
    val statusText: String = "",
    val placedCount: Int = 0,
    val totalDemandCount: Int = 0,
    val restartAttempt: Int = 0,
    val elapsedMs: Long = 0L,
    val bestSoftPenalty: Int = 0
)

data class UnplacedDemandItem(
    val schoolId: String,
    val className: String,
    val section: String,
    val subjectName: String,
    val teacherId: String,
    val teacherName: String,
    val requestedPeriods: Int,
    val placedPeriods: Int,
    val missingPeriods: Int,
    val reason: String
)

data class AutoGenerateResult(
    val config: AutoGenerateConfig,
    val generatedCells: List<TimetableCell>,
    val untouchedLockedOrKeptCells: List<TimetableCell>,
    val cellsToDeleteIds: List<String>,
    val totalRequestedPeriods: Int,
    val totalPlacedPeriods: Int,
    val newlyPlacedPeriods: Int,
    val unplacedItems: List<UnplacedDemandItem>,
    val softPenaltyScore: Int,
    val restartsCompleted: Int,
    val elapsedMs: Long,
    val wasCancelled: Boolean,
    val undoSnapshot: TimetableUndoSnapshot
) {
    val isComplete: Boolean get() = unplacedItems.isEmpty() && !wasCancelled
}

/**
 * Snapshot saved before an auto-generate run is committed so "Undo auto-generate" restores
 * the exact previous timetable state in 1 tap (re-writing previously existing cells in scope
 * and deleting newly created cells that did not exist before).
 */
data class TimetableUndoSnapshot(
    val id: String = "",
    val schoolId: String = "",
    val session: String = "2026-2027",
    val scopeLabel: String = "",
    val classNameFilter: String = "",
    val sectionFilter: String = "",
    val seedUsed: Long = 0L,
    val previousCells: List<TimetableCell> = emptyList(),
    val affectedCellIds: List<String> = emptyList(),
    val createdByUid: String = "",
    val createdByName: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun toFirestoreMap(
        actorUid: String,
        actorName: String,
        createdAtValue: Any,
        updatedAtValue: Any
    ): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim().take(80),
        "session" to session.trim().take(32),
        "scopeLabel" to scopeLabel.trim().take(120),
        "classNameFilter" to classNameFilter.trim().take(40),
        "sectionFilter" to sectionFilter.trim().take(20),
        "seedUsed" to seedUsed,
        "previousCells" to previousCells.take(1500).map { cell ->
            mapOf(
                "id" to cell.id,
                "schoolId" to cell.schoolId,
                "session" to cell.session,
                "className" to cell.className,
                "section" to cell.section,
                "day" to cell.day.wireValue,
                "slotId" to cell.slotId,
                "subjectName" to cell.subjectName,
                "teacherId" to cell.teacherId,
                "teacherName" to cell.teacherName,
                "locked" to cell.locked
            )
        },
        "affectedCellIds" to affectedCellIds.take(1800),
        "createdByUid" to actorUid.take(128),
        "createdByName" to actorName.trim().take(120),
        "createdAtMs" to createdAtMs,
        "createdAt" to createdAtValue,
        "updatedAt" to updatedAtValue
    )

    companion object {
        fun makeUndoDocId(schoolId: String, session: String): String =
            "ttundo_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(session)}"

        fun fromMap(id: String, raw: Map<*, *>?): TimetableUndoSnapshot? {
            if (raw == null) return null
            val schoolId = (raw["schoolId"] as? String)?.trim().orEmpty()
            val session = (raw["session"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || session.isEmpty()) return null
            val prevRaw = (raw["previousCells"] as? List<*>).orEmpty()
            val prevCells = prevRaw.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val cid = (m["id"] as? String)?.trim().orEmpty()
                val sId = (m["schoolId"] as? String)?.trim().orEmpty()
                val sess = (m["session"] as? String)?.trim().orEmpty()
                val cls = (m["className"] as? String)?.trim().orEmpty()
                val sec = (m["section"] as? String)?.trim().orEmpty()
                val day = DayOfWeekCode.fromRaw(m["day"] as? String) ?: return@mapNotNull null
                val slId = (m["slotId"] as? String)?.trim().orEmpty()
                val subj = (m["subjectName"] as? String)?.trim().orEmpty()
                if (sId.isEmpty() || cls.isEmpty() || slId.isEmpty() || subj.isEmpty()) return@mapNotNull null
                TimetableCell(
                    id = cid.ifEmpty { TimetableEngine.makeCellDocId(sId, sess, cls, sec, day, slId) },
                    schoolId = sId,
                    session = sess.ifBlank { session },
                    className = cls,
                    section = sec.ifBlank { "A" },
                    day = day,
                    slotId = slId,
                    subjectName = subj,
                    teacherId = (m["teacherId"] as? String)?.trim().orEmpty(),
                    teacherName = (m["teacherName"] as? String)?.trim().orEmpty(),
                    locked = (m["locked"] as? Boolean) ?: false
                )
            }
            val affectedIds = (raw["affectedCellIds"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .orEmpty()
            return TimetableUndoSnapshot(
                id = id,
                schoolId = schoolId,
                session = session,
                scopeLabel = (raw["scopeLabel"] as? String)?.trim().orEmpty(),
                classNameFilter = (raw["classNameFilter"] as? String)?.trim().orEmpty(),
                sectionFilter = (raw["sectionFilter"] as? String)?.trim().orEmpty(),
                seedUsed = (raw["seedUsed"] as? Number)?.toLong() ?: 0L,
                previousCells = prevCells,
                affectedCellIds = affectedIds,
                createdByUid = (raw["createdByUid"] as? String)?.trim().orEmpty(),
                createdByName = (raw["createdByName"] as? String)?.trim().orEmpty(),
                createdAtMs = (raw["createdAtMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

// =============================================================================
// 3. TEACHER LEAVE & SUBSTITUTION MODELS
// =============================================================================
enum class LeaveReason(val wireValue: String, val label: String) {
    CASUAL_LEAVE("CASUAL_LEAVE", "Casual Leave"),
    SICK("SICK", "Sick Leave"),
    DUTY("DUTY", "Official Duty"),
    OTHER("OTHER", "Other");

    companion object {
        fun fromRaw(raw: String?): LeaveReason =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: CASUAL_LEAVE
    }
}

enum class LeaveStatus(val wireValue: String, val label: String) {
    PENDING("PENDING", "Requested (Pending)"),
    APPROVED("APPROVED", "Approved"),
    REJECTED("REJECTED", "Rejected"),
    CANCELLED("CANCELLED", "Cancelled");

    companion object {
        fun fromRaw(raw: String?): LeaveStatus =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: PENDING
    }
}

data class TeacherLeaveRecord(
    val id: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val reasonType: LeaveReason = LeaveReason.CASUAL_LEAVE,
    val reasonNote: String = "",
    val halfDay: Boolean = false,
    val halfDaySlotIds: List<String> = emptyList(),
    val status: LeaveStatus = LeaveStatus.APPROVED,
    val requestedByUid: String = "",
    val requestedByName: String = "",
    val approvedByUid: String = "",
    val approvedByName: String = "",
    val approvedAtMs: Long = 0L,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false
) {
    /**
     * Returns true if this leave is APPROVED and covers [isoDate] and [slotId] (or [slotLabel]).
     */
    fun coversApprovedPeriod(isoDate: String, slotId: String, slotLabel: String = ""): Boolean {
        if (status != LeaveStatus.APPROVED) return false
        if (!Phase6DateUtils.isDateWithinRange(isoDate, startDate, endDate)) return false
        if (!halfDay || halfDaySlotIds.isEmpty()) return true
        return halfDaySlotIds.any {
            it.equals(slotId, ignoreCase = true) ||
                (slotLabel.isNotBlank() && it.equals(slotLabel, ignoreCase = true))
        }
    }

    fun toFirestoreMap(
        actorUid: String,
        actorName: String,
        editedAtMs: Long,
        createdAtValue: Any,
        updatedAtValue: Any
    ): Map<String, Any> = mapOf(
        "teacherId" to teacherId.trim().take(80),
        "teacherName" to teacherName.trim().take(120),
        "startDate" to startDate.trim().take(16),
        "endDate" to endDate.trim().take(16),
        "reasonType" to reasonType.wireValue,
        "reasonNote" to reasonNote.trim().take(240),
        "halfDay" to halfDay,
        "halfDaySlotIds" to halfDaySlotIds.map { it.trim().take(40) }.filter { it.isNotEmpty() }.distinct().take(20),
        "status" to status.wireValue,
        "requestedByUid" to requestedByUid.ifBlank { actorUid }.take(128),
        "requestedByName" to requestedByName.ifBlank { actorName }.trim().take(120),
        "approvedByUid" to approvedByUid.take(128),
        "approvedByName" to approvedByName.trim().take(120),
        "approvedAtMs" to approvedAtMs,
        "updatedByUid" to actorUid.take(128),
        "updatedByName" to actorName.trim().take(120),
        "clientEditedAtMs" to editedAtMs,
        "createdAt" to createdAtValue,
        "updatedAt" to updatedAtValue
    )

    companion object {
        fun fromMap(
            id: String,
            raw: Map<*, *>?,
            hasPendingWrites: Boolean = false,
            isFromCache: Boolean = false
        ): TeacherLeaveRecord? {
            if (raw == null) return null
            val teacherId = (raw["teacherId"] as? String)?.trim().orEmpty()
            val startDate = (raw["startDate"] as? String)?.trim().orEmpty()
            val endDate = (raw["endDate"] as? String)?.trim().orEmpty()
            if (teacherId.isEmpty() || startDate.isEmpty() || endDate.isEmpty()) return null
            val slotIds = (raw["halfDaySlotIds"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .orEmpty()
            return TeacherLeaveRecord(
                id = id,
                teacherId = teacherId,
                teacherName = (raw["teacherName"] as? String)?.trim().orEmpty(),
                startDate = startDate,
                endDate = endDate,
                reasonType = LeaveReason.fromRaw(raw["reasonType"] as? String),
                reasonNote = (raw["reasonNote"] as? String)?.trim().orEmpty(),
                halfDay = (raw["halfDay"] as? Boolean) ?: false,
                halfDaySlotIds = slotIds,
                status = LeaveStatus.fromRaw(raw["status"] as? String),
                requestedByUid = (raw["requestedByUid"] as? String)?.trim().orEmpty(),
                requestedByName = (raw["requestedByName"] as? String)?.trim().orEmpty(),
                approvedByUid = (raw["approvedByUid"] as? String)?.trim().orEmpty(),
                approvedByName = (raw["approvedByName"] as? String)?.trim().orEmpty(),
                approvedAtMs = (raw["approvedAtMs"] as? Number)?.toLong() ?: 0L,
                updatedByUid = (raw["updatedByUid"] as? String)?.trim().orEmpty(),
                updatedByName = (raw["updatedByName"] as? String)?.trim().orEmpty(),
                clientEditedAtMs = (raw["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites,
                isFromCache = isFromCache
            )
        }
    }
}

/**
 * Date-scoped substitution assignment. Stored per date, school, class, section, and slot —
 * NEVER alters the weekly `timetableCells`.
 */
data class SubstitutionRecord(
    val id: String = "",
    val date: String = "",
    val day: DayOfWeekCode = DayOfWeekCode.MON,
    val schoolId: String = "",
    val session: String = "2026-2027",
    val className: String = "",
    val section: String = "A",
    val slotId: String = "",
    val slotLabel: String = "",
    val startMinutes: Int = 480,
    val endMinutes: Int = 520,
    val subjectName: String = "",
    val absentTeacherId: String = "",
    val absentTeacherName: String = "",
    val leaveId: String = "",
    val substituteTeacherId: String = "",
    val substituteTeacherName: String = "",
    val overrideUsed: Boolean = false,
    val overrideReason: String = "",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false
) {
    fun toFirestoreMap(
        actorUid: String,
        actorName: String,
        editedAtMs: Long,
        createdAtValue: Any,
        updatedAtValue: Any
    ): Map<String, Any> = mapOf(
        "date" to date.trim().take(16),
        "day" to day.wireValue,
        "schoolId" to schoolId.trim().take(80),
        "session" to session.trim().take(32),
        "className" to className.trim().take(40),
        "section" to section.trim().take(20),
        "slotId" to slotId.trim().take(40),
        "slotLabel" to slotLabel.trim().take(40),
        "startMinutes" to startMinutes.coerceIn(0, 1440),
        "endMinutes" to endMinutes.coerceIn(0, 1440),
        "subjectName" to subjectName.trim().take(80),
        "absentTeacherId" to absentTeacherId.trim().take(80),
        "absentTeacherName" to absentTeacherName.trim().take(120),
        "leaveId" to leaveId.trim().take(120),
        "substituteTeacherId" to substituteTeacherId.trim().take(80),
        "substituteTeacherName" to substituteTeacherName.trim().take(120),
        "overrideUsed" to overrideUsed,
        "overrideReason" to overrideReason.trim().take(200),
        "updatedByUid" to actorUid.take(128),
        "updatedByName" to actorName.trim().take(120),
        "clientEditedAtMs" to editedAtMs,
        "createdAt" to createdAtValue,
        "updatedAt" to updatedAtValue
    )

    companion object {
        fun makeSubstitutionDocId(
            date: String,
            schoolId: String,
            className: String,
            section: String,
            slotId: String
        ): String {
            val d = StableDocId.sanitizeSegment(date)
            val s = StableDocId.sanitizeSegment(schoolId)
            val c = StableDocId.sanitizeSegment(className)
            val sec = StableDocId.sanitizeSegment(section.ifBlank { "A" })
            val sl = StableDocId.sanitizeSegment(slotId)
            return "sub_${d}_${s}_${c}_${sec}_${sl}"
        }

        fun fromMap(
            id: String,
            raw: Map<*, *>?,
            hasPendingWrites: Boolean = false,
            isFromCache: Boolean = false
        ): SubstitutionRecord? {
            if (raw == null) return null
            val date = (raw["date"] as? String)?.trim().orEmpty()
            val schoolId = (raw["schoolId"] as? String)?.trim().orEmpty()
            val className = (raw["className"] as? String)?.trim().orEmpty()
            val section = (raw["section"] as? String)?.trim().orEmpty()
            val slotId = (raw["slotId"] as? String)?.trim().orEmpty()
            val subId = (raw["substituteTeacherId"] as? String)?.trim().orEmpty()
            if (date.isEmpty() || schoolId.isEmpty() || className.isEmpty() || slotId.isEmpty() || subId.isEmpty()) {
                return null
            }
            val day = DayOfWeekCode.fromRaw(raw["day"] as? String)
                ?: Phase6DateUtils.dayOfWeekForDate(date)
                ?: DayOfWeekCode.MON
            return SubstitutionRecord(
                id = id,
                date = date,
                day = day,
                schoolId = schoolId,
                session = (raw["session"] as? String)?.trim().orEmpty().ifBlank { "2026-2027" },
                className = className,
                section = section.ifBlank { "A" },
                slotId = slotId,
                slotLabel = (raw["slotLabel"] as? String)?.trim().orEmpty(),
                startMinutes = (raw["startMinutes"] as? Number)?.toInt() ?: 480,
                endMinutes = (raw["endMinutes"] as? Number)?.toInt() ?: 520,
                subjectName = (raw["subjectName"] as? String)?.trim().orEmpty(),
                absentTeacherId = (raw["absentTeacherId"] as? String)?.trim().orEmpty(),
                absentTeacherName = (raw["absentTeacherName"] as? String)?.trim().orEmpty(),
                leaveId = (raw["leaveId"] as? String)?.trim().orEmpty(),
                substituteTeacherId = subId,
                substituteTeacherName = (raw["substituteTeacherName"] as? String)?.trim().orEmpty(),
                overrideUsed = (raw["overrideUsed"] as? Boolean) ?: false,
                overrideReason = (raw["overrideReason"] as? String)?.trim().orEmpty(),
                updatedByUid = (raw["updatedByUid"] as? String)?.trim().orEmpty(),
                updatedByName = (raw["updatedByName"] as? String)?.trim().orEmpty(),
                clientEditedAtMs = (raw["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites,
                isFromCache = isFromCache
            )
        }
    }
}

data class SubstituteCandidate(
    val teacher: TeacherProfile,
    val isRecommendedEligible: Boolean,
    val teachesSameSubject: Boolean,
    val teachesThatClassAlready: Boolean,
    val monthlySubstitutionCount: Int,
    val totalDayLoadIncludingSubs: Int,
    val regularPeriodsThatDay: Int,
    val substitutionsThatDay: Int,
    val reasonBadges: List<String>,
    val overrideRequired: Boolean,
    val overrideWarningReason: String
)

data class SubstitutionNeedItem(
    val date: String,
    val day: DayOfWeekCode,
    val schoolId: String,
    val schoolName: String,
    val session: String,
    val className: String,
    val section: String,
    val slotId: String,
    val slotNumber: Int,
    val slotLabel: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val subjectName: String,
    val absentTeacherId: String,
    val absentTeacherName: String,
    val leaveId: String,
    val leaveReason: LeaveReason,
    val existingSubstitution: SubstitutionRecord?,
    val assignedSubstituteAlsoAbsent: Boolean,
    val assignedSubstituteInactive: Boolean,
    val needsSubstitute: Boolean,
    val statusNotice: String,
    val recommendedCandidates: List<SubstituteCandidate>,
    val manualOverrideCandidates: List<SubstituteCandidate>
)

data class TeacherSubstitutionWorkloadSummary(
    val teacherId: String,
    val teacherName: String,
    val regularWeeklyPeriods: Int,
    val substitutionsThisWeek: Int,
    val totalWeeklyLoadWithSubs: Int,
    val substitutionsThisMonth: Int,
    val weeklyLimit: Int
)

/**
 * Local pending tracking for Leave & Substitution concurrent offline edits so two phones
 * editing leave or substitutions at the same time never silently overwrite each other.
 */
data class LocalPendingPhase6Edit(
    val kind: String, // "LEAVE" or "SUB"
    val docId: String,
    val targetSummary: String,
    val localValueSummary: String,
    val wasDeleted: Boolean,
    val clientEditedAtMs: Long
)

data class Phase6ConflictNotice(
    val kind: String,
    val docId: String,
    val targetSummary: String,
    val yourSummary: String,
    val winningSummary: String,
    val winnerName: String,
    val detectedAtMs: Long
)

data class Phase6ConflictDetectionResult(
    val remainingPending: List<LocalPendingPhase6Edit>,
    val detectedConflicts: List<Phase6ConflictNotice>
)

// =============================================================================
// 4. AUTO-GENERATE SOLVER ENGINE (Randomised MRV Constraint Search + Restarts)
// =============================================================================
object AutoGenerateEngine {

    private data class DemandUnit(
        val id: Int,
        val schoolId: String,
        val className: String,
        val section: String,
        val subjectName: String,
        val teacherId: String,
        val teacherName: String,
        val totalRequestedForSubject: Int,
        val allowDoublePeriod: Boolean,
        val maxOncePerDay: Boolean,
        val preferredTimeOfDay: PreferredTimeOfDay,
        val isHeavyOrMorningPreferred: Boolean
    )

    private data class CandidateSlot(
        val day: DayOfWeekCode,
        val slotId: String,
        val slotIndexInTeachingDay: Int,
        val startMinutes: Int,
        val endMinutes: Int
    )

    private data class TeacherSlotBooking(
        val schoolId: String,
        val className: String,
        val section: String,
        val day: DayOfWeekCode,
        val slotId: String,
        val startMinutes: Int,
        val endMinutes: Int
    )

    /**
     * Runs the Phase 6 constraint solver cleanly on the caller's dispatcher.
     * - Deterministic for a given [config.seed].
     * - Respects all Hard Rules across ALL schools in the organisation using real clock times.
     * - Optimizes Soft Goals in the requested priority order.
     * - Produces a complete Undo snapshot + clear Unplaced diagnostics when 100% placement is impossible.
     */
    fun generateTimetable(
        config: AutoGenerateConfig,
        existingCellsInSession: List<TimetableCell>,
        teachers: List<TeacherProfile>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        classAssignments: List<ClassSectionAssignment>,
        subjectRules: List<SubjectRule>,
        onProgress: (AutoGenerateProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): AutoGenerateResult {
        val startMs = System.currentTimeMillis()
        val timeLimitMs = (config.timeLimitSeconds.coerceIn(2, 120)) * 1000L
        val deadlineMs = startMs + timeLimitMs

        val schoolId = config.schoolId
        val schoolStructure = periodStructuresBySchool[schoolId]
            ?: Phase4Validator.buildDefaultPeriodStructure(schoolId)
        val teachersById = teachers.associateBy { it.id }

        fun matchesTargetScope(cellSchoolId: String, cls: String, sec: String): Boolean {
            if (cellSchoolId != schoolId) return false
            return when (config.scopeMode) {
                AutoGenerateScopeMode.WHOLE_SCHOOL -> true
                AutoGenerateScopeMode.SINGLE_CLASS_SECTION ->
                    cls.equals(config.className, ignoreCase = true) &&
                        sec.equals(config.section, ignoreCase = true)
            }
        }

        // 1. Partition existing cells into:
        //    - cells in target scope (saved in Undo snapshot)
        //    - fixed/kept cells (all cells outside target scope + locked cells in scope + unlocked cells if keepExistingUnlockedCells)
        val existingInScope = existingCellsInSession.filter {
            it.session == config.session && matchesTargetScope(it.schoolId, it.className, it.section)
        }
        val fixedInScope = existingInScope.filter { cell ->
            cell.locked || config.keepExistingUnlockedCells
        }
        val fixedOutsideScope = existingCellsInSession.filter {
            it.session == config.session && !matchesTargetScope(it.schoolId, it.className, it.section)
        }
        val allFixedCells = fixedOutsideScope + fixedInScope

        // 2. Scope assignments to place
        val targetAssignments = classAssignments.filter { assign ->
            matchesTargetScope(assign.schoolId, assign.className, assign.section)
        }.sortedWith(compareBy({ it.className.lowercase() }, { it.section.lowercase() }))

        // Build subject rule lookup per school and subject
        fun resolveRule(subj: String): SubjectRule? {
            return subjectRules.firstOrNull {
                it.schoolId == schoolId && it.subjectName.equals(subj, ignoreCase = true)
            }
        }

        // Pre-calculate candidate teaching slots for the target school
        val candidateSlots = mutableListOf<CandidateSlot>()
        for (day in schoolStructure.workingDays) {
            val teachingSlots = schoolStructure.slotsFor(day).filter { it.type.countsAsTeachingPeriod }
            teachingSlots.forEachIndexed { idx, slot ->
                candidateSlots.add(
                    CandidateSlot(
                        day = day,
                        slotId = slot.id,
                        slotIndexInTeachingDay = idx,
                        startMinutes = slot.startMinutes,
                        endMinutes = slot.endMinutes
                    )
                )
            }
        }

        // 3. Build demand units after subtracting already fixed/kept cells in scope
        val demandUnits = mutableListOf<DemandUnit>()
        val preFailedDemands = mutableListOf<UnplacedDemandItem>()
        var unitCounter = 0

        for (assign in targetAssignments) {
            val fixedForClass = fixedInScope.filter {
                it.className.equals(assign.className, ignoreCase = true) &&
                    it.section.equals(assign.section, ignoreCase = true)
            }
            for (row in assign.subjectAssignments) {
                if (row.periodsPerWeek <= 0) continue
                val alreadyPlacedCount = fixedForClass.count {
                    it.subjectName.equals(row.subjectName, ignoreCase = true)
                }
                val remainingNeeded = (row.periodsPerWeek - alreadyPlacedCount).coerceAtLeast(0)
                if (remainingNeeded == 0) continue

                val teacher = teachersById[row.teacherId]
                if (row.teacherId.isBlank() || teacher == null) {
                    preFailedDemands.add(
                        UnplacedDemandItem(
                            schoolId = schoolId,
                            className = assign.className,
                            section = assign.section,
                            subjectName = row.subjectName,
                            teacherId = row.teacherId,
                            teacherName = "Unassigned",
                            requestedPeriods = row.periodsPerWeek,
                            placedPeriods = alreadyPlacedCount,
                            missingPeriods = remainingNeeded,
                            reason = "No teacher is assigned to ${row.subjectName} in ${assign.className}-${assign.section} (Phase 4)."
                        )
                    )
                    continue
                }
                if (!teacher.active) {
                    preFailedDemands.add(
                        UnplacedDemandItem(
                            schoolId = schoolId,
                            className = assign.className,
                            section = assign.section,
                            subjectName = row.subjectName,
                            teacherId = teacher.id,
                            teacherName = teacher.name,
                            requestedPeriods = row.periodsPerWeek,
                            placedPeriods = alreadyPlacedCount,
                            missingPeriods = remainingNeeded,
                            reason = "Assigned teacher ${teacher.name} is marked Inactive."
                        )
                    )
                    continue
                }

                val rule = resolveRule(row.subjectName)
                val prefTime = rule?.preferredTimeOfDay ?: PreferredTimeOfDay.NO_PREFERENCE
                val allowDouble = rule?.allowDoublePeriod ?: false
                val maxOnce = rule?.maxOncePerDay ?: true
                val isHeavy = prefTime == PreferredTimeOfDay.MORNING || row.periodsPerWeek >= 5

                repeat(remainingNeeded) {
                    demandUnits.add(
                        DemandUnit(
                            id = unitCounter++,
                            schoolId = schoolId,
                            className = assign.className,
                            section = assign.section,
                            subjectName = row.subjectName,
                            teacherId = teacher.id,
                            teacherName = teacher.name,
                            totalRequestedForSubject = row.periodsPerWeek,
                            allowDoublePeriod = allowDouble,
                            maxOncePerDay = maxOnce,
                            preferredTimeOfDay = prefTime,
                            isHeavyOrMorningPreferred = isHeavy
                        )
                    )
                }
            }
        }

        val totalRequested = targetAssignments.sumOf { a ->
            a.subjectAssignments.sumOf { it.periodsPerWeek.coerceAtLeast(0) }
        }
        val fixedPlacedCount = totalRequested - demandUnits.size - preFailedDemands.sumOf { it.missingPeriods }

        // Pre-populate base bookings from allFixedCells (across ALL schools!)
        val baseTeacherBookings = mutableMapOf<String, MutableList<TeacherSlotBooking>>()
        val baseClassOccupied = mutableSetOf<String>() // "className|section|day|slotId"
        val baseClassDaySubjectSlots = mutableMapOf<String, MutableList<Int>>() // "class|sec|day|subj" -> slotIndices

        for (cell in allFixedCells) {
            val struct = periodStructuresBySchool[cell.schoolId]
                ?: Phase4Validator.buildDefaultPeriodStructure(cell.schoolId)
            val slot = struct.slotsFor(cell.day).firstOrNull { it.id == cell.slotId } ?: continue
            baseTeacherBookings.getOrPut(cell.teacherId) { mutableListOf() }.add(
                TeacherSlotBooking(
                    schoolId = cell.schoolId,
                    className = cell.className,
                    section = cell.section,
                    day = cell.day,
                    slotId = cell.slotId,
                    startMinutes = slot.startMinutes,
                    endMinutes = slot.endMinutes
                )
            )
            if (cell.schoolId == schoolId) {
                val cKey = classSlotKey(cell.className, cell.section, cell.day, cell.slotId)
                baseClassOccupied.add(cKey)
                val teachIdx = struct.slotsFor(cell.day)
                    .filter { it.type.countsAsTeachingPeriod }
                    .indexOfFirst { it.id == cell.slotId }
                if (teachIdx >= 0) {
                    val sKey = classDaySubjectKey(cell.className, cell.section, cell.day, cell.subjectName)
                    baseClassDaySubjectSlots.getOrPut(sKey) { mutableListOf() }.add(teachIdx)
                }
            }
        }

        var bestPlacement: Map<DemandUnit, CandidateSlot> = emptyMap()
        var bestPlacedUnits = -1
        var bestSoftPenalty = Int.MAX_VALUE
        var restartAttempt = 0
        var cancelled = false

        val masterRng = Random(config.seed)

        // If there are demand units to solve, run randomised constraint search with restarts
        if (demandUnits.isNotEmpty() && candidateSlots.isNotEmpty()) {
            val maxRestarts = 45
            while (restartAttempt < maxRestarts) {
                if (isCancelled()) {
                    cancelled = true
                    break
                }
                val now = System.currentTimeMillis()
                if (restartAttempt > 0 && now >= deadlineMs) {
                    break
                }

                val attemptSeed = config.seed + restartAttempt * 1_000_003L + masterRng.nextLong()
                val rng = Random(attemptSeed)
                restartAttempt++

                // Clone mutable state for this attempt
                val teacherBookings = baseTeacherBookings.mapValues { it.value.toMutableList() }.toMutableMap()
                val classOccupied = baseClassOccupied.toMutableSet()
                val classDaySubjectSlots = baseClassDaySubjectSlots.mapValues { it.value.toMutableList() }.toMutableMap()
                val placedInAttempt = LinkedHashMap<DemandUnit, CandidateSlot>(demandUnits.size)
                var attemptPenalty = 0

                // Pre-compute teacher total demand scarcity for MRV tie-breaking
                val teacherRemainingDemand = demandUnits.groupingBy { it.teacherId }.eachCount().toMutableMap()
                val unplacedPool = demandUnits.toMutableList()
                // Shuffle pool with attempt's deterministic RNG so tie-breaks explore new neighbourhoods
                unplacedPool.shuffle(rng)

                while (unplacedPool.isNotEmpty()) {
                    if (isCancelled()) {
                        cancelled = true
                        break
                    }

                    // Select next unit using Most Constrained Variable (MRV) + teacher tightness heuristic.
                    // Cache hard-legal count per (className, section, teacherId) within this step for O(1) dedup.
                    var chosenIdx = -1
                    var minLegalCount = Int.MAX_VALUE
                    var maxScarcityScore = Int.MIN_VALUE
                    val legalCountCache = HashMap<String, Int>(64)

                    for (i in unplacedPool.indices) {
                        val unit = unplacedPool[i]
                        val teacher = teachersById[unit.teacherId] ?: continue
                        val cacheKey = "${unit.className}|${unit.section}|${unit.teacherId}"
                        val count = legalCountCache.getOrPut(cacheKey) {
                            countHardLegalSlots(
                                className = unit.className,
                                section = unit.section,
                                teacher = teacher,
                                candidateSlots = candidateSlots,
                                teacherBookings = teacherBookings[teacher.id].orEmpty(),
                                classOccupied = classOccupied
                            )
                        }
                        val scarcity = (teacherRemainingDemand[unit.teacherId] ?: 0) * 10 +
                            (if (unit.allowDoublePeriod && unit.totalRequestedForSubject >= 2) 15 else 0) +
                            (if (unit.isHeavyOrMorningPreferred) 8 else 0)

                        if (count < minLegalCount || (count == minLegalCount && scarcity > maxScarcityScore)) {
                            minLegalCount = count
                            maxScarcityScore = scarcity
                            chosenIdx = i
                            if (count == 0) break
                        }
                    }

                    if (chosenIdx < 0) break
                    val unit = unplacedPool.removeAt(chosenIdx)
                    teacherRemainingDemand[unit.teacherId] = (teacherRemainingDemand[unit.teacherId] ?: 1) - 1

                    if (minLegalCount <= 0) {
                        // Cannot place this unit in this pass; continue placing remaining units (partial results allowed)
                        continue
                    }

                    val chosenTeacher = teachersById[unit.teacherId] ?: continue
                    val chosenLegalSlots = evaluateLegalSlotsForUnit(
                        unit = unit,
                        teacher = chosenTeacher,
                        candidateSlots = candidateSlots,
                        teacherBookings = teacherBookings[chosenTeacher.id].orEmpty(),
                        classOccupied = classOccupied,
                        classDaySubjectSlots = classDaySubjectSlots,
                        rng = rng
                    )
                    if (chosenLegalSlots.isEmpty()) {
                        continue
                    }

                    // Pick slot with lowest soft penalty (already includes small deterministic jitter)
                    val (bestSlot, slotPenalty) = chosenLegalSlots.minByOrNull { it.second }!!
                    placedInAttempt[unit] = bestSlot
                    attemptPenalty += slotPenalty

                    // Commit booking to attempt state
                    teacherBookings.getOrPut(unit.teacherId) { mutableListOf() }.add(
                        TeacherSlotBooking(
                            schoolId = unit.schoolId,
                            className = unit.className,
                            section = unit.section,
                            day = bestSlot.day,
                            slotId = bestSlot.slotId,
                            startMinutes = bestSlot.startMinutes,
                            endMinutes = bestSlot.endMinutes
                        )
                    )
                    classOccupied.add(classSlotKey(unit.className, unit.section, bestSlot.day, bestSlot.slotId))
                    val sKey = classDaySubjectKey(unit.className, unit.section, bestSlot.day, unit.subjectName)
                    classDaySubjectSlots.getOrPut(sKey) { mutableListOf() }.add(bestSlot.slotIndexInTeachingDay)
                }

                // Local 1-swap repair pass if any units were left unplaced in this attempt
                if (!cancelled && placedInAttempt.size < demandUnits.size) {
                    val missingUnits = demandUnits.filter { it !in placedInAttempt }
                    for (missing in missingUnits) {
                        if (isCancelled() || System.currentTimeMillis() >= deadlineMs) break
                        val mTeacher = teachersById[missing.teacherId] ?: continue
                        // Try displacing an existing placed unit in the same class-section whose slot is legal for [missing]
                        // and which itself can move to another legal slot in that class-section
                        val sameClassPlaced = placedInAttempt.entries
                            .filter { it.key.className == missing.className && it.key.section == missing.section }
                            .shuffled(rng)

                        var repaired = false
                        for ((otherUnit, occupiedSlot) in sameClassPlaced) {
                            val oTeacher = teachersById[otherUnit.teacherId] ?: continue
                            // Could missing Teacher take occupiedSlot?
                            if (!isHardLegalForTeacher(mTeacher, occupiedSlot, teacherBookings[mTeacher.id].orEmpty())) {
                                continue
                            }
                            // Temporarily free occupiedSlot from otherUnit
                            val cKey = classSlotKey(otherUnit.className, otherUnit.section, occupiedSlot.day, occupiedSlot.slotId)
                            classOccupied.remove(cKey)
                            val oBookings = teacherBookings[oTeacher.id]
                            val removedBookingIdx = oBookings?.indexOfFirst {
                                it.schoolId == otherUnit.schoolId && it.day == occupiedSlot.day && it.slotId == occupiedSlot.slotId
                            } ?: -1
                            val removedBooking = if (removedBookingIdx >= 0) oBookings?.removeAt(removedBookingIdx) else null
                            val oSubjKey = classDaySubjectKey(otherUnit.className, otherUnit.section, occupiedSlot.day, otherUnit.subjectName)
                            classDaySubjectSlots[oSubjKey]?.remove(occupiedSlot.slotIndexInTeachingDay)

                            // Block occupiedSlot so otherUnit must find a DIFFERENT slot
                            classOccupied.add(cKey)
                            val altSlots = evaluateLegalSlotsForUnit(
                                unit = otherUnit,
                                teacher = oTeacher,
                                candidateSlots = candidateSlots,
                                teacherBookings = oBookings.orEmpty(),
                                classOccupied = classOccupied,
                                classDaySubjectSlots = classDaySubjectSlots,
                                rng = rng
                            )
                            if (altSlots.isNotEmpty()) {
                                val (altSlot, altPenalty) = altSlots.minByOrNull { it.second }!!
                                // Commit otherUnit -> altSlot and missing -> occupiedSlot
                                placedInAttempt[otherUnit] = altSlot
                                oBookings?.add(
                                    TeacherSlotBooking(
                                        schoolId = otherUnit.schoolId,
                                        className = otherUnit.className,
                                        section = otherUnit.section,
                                        day = altSlot.day,
                                        slotId = altSlot.slotId,
                                        startMinutes = altSlot.startMinutes,
                                        endMinutes = altSlot.endMinutes
                                    )
                                )
                                classOccupied.add(classSlotKey(otherUnit.className, otherUnit.section, altSlot.day, altSlot.slotId))
                                classDaySubjectSlots.getOrPut(
                                    classDaySubjectKey(otherUnit.className, otherUnit.section, altSlot.day, otherUnit.subjectName)
                                ) { mutableListOf() }.add(altSlot.slotIndexInTeachingDay)

                                placedInAttempt[missing] = occupiedSlot
                                teacherBookings.getOrPut(mTeacher.id) { mutableListOf() }.add(
                                    TeacherSlotBooking(
                                        schoolId = missing.schoolId,
                                        className = missing.className,
                                        section = missing.section,
                                        day = occupiedSlot.day,
                                        slotId = occupiedSlot.slotId,
                                        startMinutes = occupiedSlot.startMinutes,
                                        endMinutes = occupiedSlot.endMinutes
                                    )
                                )
                                classDaySubjectSlots.getOrPut(
                                    classDaySubjectKey(missing.className, missing.section, occupiedSlot.day, missing.subjectName)
                                ) { mutableListOf() }.add(occupiedSlot.slotIndexInTeachingDay)
                                attemptPenalty += altPenalty
                                repaired = true
                                break
                            } else {
                                // Rollback temporary removal
                                if (removedBooking != null) oBookings?.add(removedBooking)
                                classDaySubjectSlots.getOrPut(oSubjKey) { mutableListOf() }.add(occupiedSlot.slotIndexInTeachingDay)
                            }
                        }
                        if (repaired) continue
                    }
                }

                if (placedInAttempt.size > bestPlacedUnits ||
                    (placedInAttempt.size == bestPlacedUnits && attemptPenalty < bestSoftPenalty)
                ) {
                    bestPlacedUnits = placedInAttempt.size
                    bestSoftPenalty = attemptPenalty
                    bestPlacement = placedInAttempt.toMap()
                }

                onProgress(
                    AutoGenerateProgress(
                        isRunning = true,
                        statusText = "Searching timetable… (Pass $restartAttempt, placed ${fixedPlacedCount + bestPlacedUnits}/$totalRequested)",
                        placedCount = fixedPlacedCount + bestPlacedUnits,
                        totalDemandCount = totalRequested,
                        restartAttempt = restartAttempt,
                        elapsedMs = System.currentTimeMillis() - startMs,
                        bestSoftPenalty = if (bestSoftPenalty == Int.MAX_VALUE) 0 else bestSoftPenalty
                    )
                )

                // Early exit if 100% placed with zero soft penalty, or 100% placed after at least 4 passes
                if (bestPlacedUnits == demandUnits.size && (bestSoftPenalty == 0 || restartAttempt >= 4)) {
                    break
                }
            }
        } else {
            bestPlacedUnits = 0
            bestSoftPenalty = 0
        }

        // 4. Convert bestPlacement into deterministic TimetableCells
        val newlyGeneratedCells = bestPlacement.map { (unit, slot) ->
            val cellId = TimetableEngine.makeCellDocId(
                schoolId = unit.schoolId,
                session = config.session,
                className = unit.className,
                section = unit.section,
                day = slot.day,
                slotId = slot.slotId
            )
            TimetableCell(
                id = cellId,
                schoolId = unit.schoolId,
                session = config.session,
                className = unit.className,
                section = unit.section,
                day = slot.day,
                slotId = slot.slotId,
                subjectName = unit.subjectName,
                teacherId = unit.teacherId,
                teacherName = unit.teacherName,
                locked = false
            )
        }.sortedBy { it.id }

        // 5. Diagnose any unplaced units with clear, human-readable reasons
        val finalAllCellsInScope = fixedInScope + newlyGeneratedCells
        val finalAllCellsSession = fixedOutsideScope + finalAllCellsInScope
        val unplacedItems = mutableListOf<UnplacedDemandItem>()
        unplacedItems.addAll(preFailedDemands)

        val placedUnitIds = bestPlacement.keys.map { it.id }.toSet()
        val unplacedByGroup = demandUnits
            .filter { it.id !in placedUnitIds }
            .groupBy { "${it.className}|${it.section}|${it.subjectName}|${it.teacherId}" }

        for ((_, missingUnits) in unplacedByGroup) {
            val sample = missingUnits.first()
            val missingCount = missingUnits.size
            val placedCount = sample.totalRequestedForSubject - missingCount
            val reason = diagnoseUnplacedReason(
                sample = sample,
                teacher = teachersById[sample.teacherId],
                candidateSlots = candidateSlots,
                allPlacedCellsInSession = finalAllCellsSession,
                periodStructuresBySchool = periodStructuresBySchool
            )
            unplacedItems.add(
                UnplacedDemandItem(
                    schoolId = sample.schoolId,
                    className = sample.className,
                    section = sample.section,
                    subjectName = sample.subjectName,
                    teacherId = sample.teacherId,
                    teacherName = sample.teacherName,
                    requestedPeriods = sample.totalRequestedForSubject,
                    placedPeriods = placedCount,
                    missingPeriods = missingCount,
                    reason = reason
                )
            )
        }

        // 6. Determine which old unlocked cells in scope should be deleted (were previously filled, now empty)
        val newAndKeptIds = (fixedInScope.map { it.id } + newlyGeneratedCells.map { it.id }).toSet()
        val cellsToDeleteIds = existingInScope
            .map { it.id }
            .filter { it !in newAndKeptIds }

        val scopeLabel = when (config.scopeMode) {
            AutoGenerateScopeMode.WHOLE_SCHOOL -> "Whole school"
            AutoGenerateScopeMode.SINGLE_CLASS_SECTION -> "Class ${config.className}-${config.section}"
        }
        val undoSnapshot = TimetableUndoSnapshot(
            id = TimetableUndoSnapshot.makeUndoDocId(schoolId, config.session),
            schoolId = schoolId,
            session = config.session,
            scopeLabel = scopeLabel,
            classNameFilter = if (config.scopeMode == AutoGenerateScopeMode.SINGLE_CLASS_SECTION) config.className else "",
            sectionFilter = if (config.scopeMode == AutoGenerateScopeMode.SINGLE_CLASS_SECTION) config.section else "",
            seedUsed = config.seed,
            previousCells = existingInScope,
            affectedCellIds = (existingInScope.map { it.id } + newlyGeneratedCells.map { it.id }).distinct()
        )

        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        return AutoGenerateResult(
            config = config,
            generatedCells = newlyGeneratedCells,
            untouchedLockedOrKeptCells = fixedInScope,
            cellsToDeleteIds = cellsToDeleteIds,
            totalRequestedPeriods = totalRequested,
            totalPlacedPeriods = fixedPlacedCount + newlyGeneratedCells.size,
            newlyPlacedPeriods = newlyGeneratedCells.size,
            unplacedItems = unplacedItems,
            softPenaltyScore = if (bestSoftPenalty == Int.MAX_VALUE) 0 else bestSoftPenalty,
            restartsCompleted = restartAttempt,
            elapsedMs = elapsed,
            wasCancelled = cancelled,
            undoSnapshot = undoSnapshot
        )
    }

    private fun isHardLegalForTeacher(
        teacher: TeacherProfile,
        slot: CandidateSlot,
        existingBookings: List<TeacherSlotBooking>
    ): Boolean {
        if (!teacher.active) return false
        if (slot.day in teacher.weeklyOffDays) return false
        var dayCount = 0
        for (i in existingBookings.indices) {
            val b = existingBookings[i]
            if (b.day == slot.day) {
                dayCount++
                if (dayCount >= teacher.maxPeriodsPerDay) return false
                if (TimetableEngine.slotsOverlap(slot.startMinutes, slot.endMinutes, b.startMinutes, b.endMinutes)) {
                    return false
                }
            }
        }
        return true
    }

    private fun countHardLegalSlots(
        className: String,
        section: String,
        teacher: TeacherProfile,
        candidateSlots: List<CandidateSlot>,
        teacherBookings: List<TeacherSlotBooking>,
        classOccupied: Set<String>
    ): Int {
        if (!teacher.active) return 0
        var legal = 0
        for (i in candidateSlots.indices) {
            val slot = candidateSlots[i]
            if (classSlotKey(className, section, slot.day, slot.slotId) in classOccupied) continue
            if (isHardLegalForTeacher(teacher, slot, teacherBookings)) {
                legal++
            }
        }
        return legal
    }

    /**
     * Evaluates all candidate slots for [unit], enforcing ALL Hard Rules strictly and scoring
     * Soft Goals in the requested priority order:
     * 1. Spread each subject across the week (not twice in one day unless double is allowed)
     * 2. Heavy subjects in the morning if preferred
     * 3. Respect preferred time of day
     * 4. Limit consecutive periods (<= maxConsecutivePeriods)
     * 5. Keep each teacher's daily load balanced
     */
    private fun evaluateLegalSlotsForUnit(
        unit: DemandUnit,
        teacher: TeacherProfile,
        candidateSlots: List<CandidateSlot>,
        teacherBookings: List<TeacherSlotBooking>,
        classOccupied: Set<String>,
        classDaySubjectSlots: Map<String, List<Int>>,
        rng: Random
    ): List<Pair<CandidateSlot, Int>> {
        val result = mutableListOf<Pair<CandidateSlot, Int>>()
        for (slot in candidateSlots) {
            // HARD RULE 1: Class cannot have two subjects in the same slot
            val cKey = classSlotKey(unit.className, unit.section, slot.day, slot.slotId)
            if (cKey in classOccupied) continue

            // HARD RULE 2–5: Teacher active, off-days, maxPeriodsPerDay, and real clock overlap across ALL schools
            if (!isHardLegalForTeacher(teacher, slot, teacherBookings)) continue

            // SOFT GOAL SCORING (Strict priority hierarchy via weight tiers)
            var penalty = 0

            // Priority 1 (Weight 10_000): Spread subject across the week (not twice in one day unless double period allowed)
            val sKey = classDaySubjectKey(unit.className, unit.section, slot.day, unit.subjectName)
            val existingSubjIndicesOnDay = classDaySubjectSlots[sKey].orEmpty()
            if (existingSubjIndicesOnDay.isNotEmpty()) {
                val isAdjacentSecond = unit.allowDoublePeriod &&
                    existingSubjIndicesOnDay.size == 1 &&
                    abs(existingSubjIndicesOnDay.first() - slot.slotIndexInTeachingDay) == 1
                if (isAdjacentSecond) {
                    // Bonus (negative penalty or 0) for completing an allowed double period pair
                    penalty += 0
                } else if (unit.maxOncePerDay && !unit.allowDoublePeriod) {
                    penalty += 12_000 * existingSubjIndicesOnDay.size
                } else {
                    penalty += 8_000 * existingSubjIndicesOnDay.size
                }
            } else if (unit.allowDoublePeriod && unit.totalRequestedForSubject >= 2) {
                // Slight preference for slots that have an adjacent free slot when starting a double period
                penalty += 20
            }

            // Priority 2 (Weight 2_000): Heavy subjects in the morning if preferred
            if (unit.isHeavyOrMorningPreferred && slot.startMinutes >= TimetableEngine.AFTERNOON_START_MINUTES) {
                penalty += 2_000 + ((slot.startMinutes - TimetableEngine.AFTERNOON_START_MINUTES) / 10)
            } else if (unit.isHeavyOrMorningPreferred) {
                // Earlier morning slots slightly preferred for heavy subjects
                penalty += (slot.slotIndexInTeachingDay * 15)
            }

            // Priority 3 (Weight 1_000): Respect preferred time of day
            when (unit.preferredTimeOfDay) {
                PreferredTimeOfDay.MORNING -> {
                    if (slot.startMinutes >= TimetableEngine.AFTERNOON_START_MINUTES) penalty += 1_200
                }
                PreferredTimeOfDay.AFTERNOON -> {
                    if (slot.startMinutes < TimetableEngine.AFTERNOON_START_MINUTES) penalty += 1_200
                }
                PreferredTimeOfDay.NO_PREFERENCE -> Unit
            }

            // Priority 4 (Weight 400): Limit consecutive periods (<= teacher.maxConsecutivePeriods)
            val dayBookings = teacherBookings.filter { it.day == slot.day }
            val intervalsWithCandidate = dayBookings.map { it.startMinutes to it.endMinutes } +
                (slot.startMinutes to slot.endMinutes)
            val streak = TimetableEngine.computeMaxConsecutiveStreak(intervalsWithCandidate)
            if (streak > teacher.maxConsecutivePeriods) {
                penalty += 450 * (streak - teacher.maxConsecutivePeriods)
            } else if (streak == teacher.maxConsecutivePeriods && streak >= 3) {
                penalty += 40
            }

            // Priority 5 (Weight 30): Keep each teacher's daily load balanced across working days
            penalty += dayBookings.size * 35

            // Small deterministic jitter (0..11) to diversify equal-tier choices across restarts
            penalty += rng.nextInt(12)

            result.add(slot to penalty.coerceAtLeast(0))
        }
        return result
    }

    private fun diagnoseUnplacedReason(
        sample: DemandUnit,
        teacher: TeacherProfile?,
        candidateSlots: List<CandidateSlot>,
        allPlacedCellsInSession: List<TimetableCell>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>
    ): String {
        if (teacher == null) {
            return "No teacher assigned to ${sample.subjectName}."
        }
        if (!teacher.active) {
            return "Teacher ${teacher.name} is marked Inactive."
        }

        val freeClassSlots = candidateSlots.filter { slot ->
            allPlacedCellsInSession.none { c ->
                c.schoolId == sample.schoolId &&
                    c.className.equals(sample.className, ignoreCase = true) &&
                    c.section.equals(sample.section, ignoreCase = true) &&
                    c.day == slot.day &&
                    c.slotId == slot.slotId
            }
        }
        if (freeClassSlots.isEmpty()) {
            return "Class ${sample.className}-${sample.section} has no free period slots remaining in the week."
        }

        val teacherCells = allPlacedCellsInSession.filter { it.teacherId == teacher.id }
        var offDayBlocks = 0
        var maxPerDayBlocks = 0
        var crossSchoolBusyBlocks = 0
        var sameSchoolBusyBlocks = 0

        for (slot in freeClassSlots) {
            if (slot.day in teacher.weeklyOffDays) {
                offDayBlocks++
                continue
            }
            val dayCells = teacherCells.filter { it.day == slot.day }
            if (dayCells.size >= teacher.maxPeriodsPerDay) {
                maxPerDayBlocks++
                continue
            }
            val overlapping = dayCells.firstOrNull { c ->
                val struct = periodStructuresBySchool[c.schoolId]
                    ?: Phase4Validator.buildDefaultPeriodStructure(c.schoolId)
                val otherSlot = struct.slotsFor(c.day).firstOrNull { it.id == c.slotId }
                otherSlot != null && TimetableEngine.slotsOverlap(
                    slot.startMinutes,
                    slot.endMinutes,
                    otherSlot.startMinutes,
                    otherSlot.endMinutes
                )
            }
            if (overlapping != null) {
                if (overlapping.schoolId != sample.schoolId) {
                    crossSchoolBusyBlocks++
                } else {
                    sameSchoolBusyBlocks++
                }
            }
        }

        val reasons = mutableListOf<String>()
        if (sameSchoolBusyBlocks + crossSchoolBusyBlocks > 0) {
            if (crossSchoolBusyBlocks > 0) {
                reasons.add("${teacher.name} is already teaching at overlapping clock times (including another school) in ${sameSchoolBusyBlocks + crossSchoolBusyBlocks} remaining free slot(s)")
            } else {
                reasons.add("${teacher.name} is already teaching another class in $sameSchoolBusyBlocks remaining free slot(s)")
            }
        }
        if (maxPerDayBlocks > 0) {
            reasons.add("reached daily limit (${teacher.maxPeriodsPerDay} periods/day) on $maxPerDayBlocks slot(s)")
        }
        if (offDayBlocks > 0) {
            reasons.add("weekly off-day on $offDayBlocks slot(s)")
        }
        return if (reasons.isNotEmpty()) {
            reasons.joinToString("; ") + "."
        } else {
            "Constraint conflict prevented placing ${sample.subjectName} for ${teacher.name}."
        }
    }

    private fun classSlotKey(className: String, section: String, day: DayOfWeekCode, slotId: String): String =
        "${className.trim().lowercase()}|${section.trim().lowercase()}|${day.wireValue}|$slotId"

    private fun classDaySubjectKey(className: String, section: String, day: DayOfWeekCode, subjectName: String): String =
        "${className.trim().lowercase()}|${section.trim().lowercase()}|${day.wireValue}|${subjectName.trim().lowercase()}"
}

// =============================================================================
// 5. TEACHER LEAVE & SUBSTITUTION ENGINE
// =============================================================================
object SubstitutionEngine {

    /**
     * Lists every period of every absent teacher on [date] using that weekday's timetable
     * across ALL allowed schools of the organisation (filtered by [schoolIdFilter] if non-blank).
     *
     * Also detects when an already-assigned substitute teacher subsequently becomes absent
     * (approved leave covering that date & slot) or inactive, marking that period again as
     * [SubstitutionNeedItem.needsSubstitute] = true!
     */
    fun computeDailySubstitutionNeeds(
        date: String,
        session: String,
        schoolIdFilter: String = "",
        teachers: List<TeacherProfile>,
        leaves: List<TeacherLeaveRecord>,
        timetableCellsInSession: List<TimetableCell>,
        substitutions: List<SubstitutionRecord>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        classAssignments: List<ClassSectionAssignment>,
        schoolNamesById: Map<String, String> = emptyMap()
    ): List<SubstitutionNeedItem> {
        val dayOfWeek = Phase6DateUtils.dayOfWeekForDate(date) ?: return emptyList()
        val teachersById = teachers.associateBy { it.id }
        val approvedLeavesForDate = leaves.filter {
            it.status == LeaveStatus.APPROVED && Phase6DateUtils.isDateWithinRange(date, it.startDate, it.endDate)
        }
        if (approvedLeavesForDate.isEmpty() && substitutions.none { it.date == date }) {
            return emptyList()
        }

        val subsForDateByDocId = substitutions
            .filter { it.date == date && it.session == session }
            .associateBy {
                SubstitutionRecord.makeSubstitutionDocId(
                    date = date,
                    schoolId = it.schoolId,
                    className = it.className,
                    section = it.section,
                    slotId = it.slotId
                )
            }

        // All cells on that weekday in the current session
        val dayCells = timetableCellsInSession.filter {
            it.session == session && it.day == dayOfWeek
        }

        val needs = mutableListOf<SubstitutionNeedItem>()

        for (cell in dayCells) {
            if (schoolIdFilter.isNotBlank() && cell.schoolId != schoolIdFilter) continue

            val structure = periodStructuresBySchool[cell.schoolId]
                ?: Phase4Validator.buildDefaultPeriodStructure(cell.schoolId)
            val teachingSlots = structure.slotsFor(dayOfWeek).filter { it.type.countsAsTeachingPeriod }
            val slotIdx = teachingSlots.indexOfFirst { it.id == cell.slotId }
            val slotDef = teachingSlots.getOrNull(slotIdx)
                ?: structure.slotsFor(dayOfWeek).firstOrNull { it.id == cell.slotId }
                ?: continue

            val coveringLeave = approvedLeavesForDate.firstOrNull { leave ->
                leave.teacherId == cell.teacherId &&
                    leave.coversApprovedPeriod(date, cell.slotId, slotDef.label)
            }

            val subDocId = SubstitutionRecord.makeSubstitutionDocId(
                date = date,
                schoolId = cell.schoolId,
                className = cell.className,
                section = cell.section,
                slotId = cell.slotId
            )
            val existingSub = subsForDateByDocId[subDocId]

            if (coveringLeave == null && existingSub == null) continue

            // Check if an assigned substitute teacher is NOW absent or inactive!
            val assignedSubTeacher = existingSub?.let { teachersById[it.substituteTeacherId] }
            val subTeacherAbsentLeave = existingSub?.let { sub ->
                approvedLeavesForDate.firstOrNull { leave ->
                    leave.teacherId == sub.substituteTeacherId &&
                        leave.coversApprovedPeriod(date, cell.slotId, slotDef.label)
                }
            }
            val subAlsoAbsent = existingSub != null && subTeacherAbsentLeave != null
            val subInactive = existingSub != null && (assignedSubTeacher != null && !assignedSubTeacher.active)
            val needsSub = existingSub == null || subAlsoAbsent || subInactive

            val statusNotice = when {
                subAlsoAbsent -> "Substitute ${existingSub!!.substituteTeacherName} is also absent (${subTeacherAbsentLeave!!.reasonType.label}) — needs a new substitute"
                subInactive -> "Substitute ${existingSub!!.substituteTeacherName} is inactive — needs a new substitute"
                existingSub != null && existingSub.overrideUsed -> "Assigned to ${existingSub.substituteTeacherName} (Override: ${existingSub.overrideReason.ifBlank { "Manual" }})"
                existingSub != null -> "Assigned to ${existingSub.substituteTeacherName}"
                else -> "Needs substitute"
            }

            // Rank candidates for this period
            val (recommended, overrides) = rankSubstituteCandidates(
                date = date,
                dayOfWeek = dayOfWeek,
                targetSchoolId = cell.schoolId,
                targetClassName = cell.className,
                targetSection = cell.section,
                targetSlotId = cell.slotId,
                targetSlotLabel = slotDef.label,
                targetStartMinutes = slotDef.startMinutes,
                targetEndMinutes = slotDef.endMinutes,
                targetSubjectName = cell.subjectName,
                absentTeacherId = cell.teacherId,
                teachers = teachers,
                approvedLeavesForDate = approvedLeavesForDate,
                allDayCellsAcrossSchools = dayCells,
                allSubstitutionsInSession = substitutions.filter { it.session == session },
                periodStructuresBySchool = periodStructuresBySchool,
                classAssignments = classAssignments
            )

            val absentTeacherName = teachersById[cell.teacherId]?.name?.ifBlank { cell.teacherName }
                ?: cell.teacherName.ifBlank { cell.teacherId }

            needs.add(
                SubstitutionNeedItem(
                    date = date,
                    day = dayOfWeek,
                    schoolId = cell.schoolId,
                    schoolName = schoolNamesById[cell.schoolId].orEmpty().ifBlank { cell.schoolId },
                    session = session,
                    className = cell.className,
                    section = cell.section,
                    slotId = cell.slotId,
                    slotNumber = if (slotIdx >= 0) slotIdx + 1 else 0,
                    slotLabel = slotDef.label,
                    startMinutes = slotDef.startMinutes,
                    endMinutes = slotDef.endMinutes,
                    subjectName = cell.subjectName,
                    absentTeacherId = cell.teacherId,
                    absentTeacherName = absentTeacherName,
                    leaveId = coveringLeave?.id ?: existingSub?.leaveId.orEmpty(),
                    leaveReason = coveringLeave?.reasonType ?: LeaveReason.CASUAL_LEAVE,
                    existingSubstitution = existingSub,
                    assignedSubstituteAlsoAbsent = subAlsoAbsent,
                    assignedSubstituteInactive = subInactive,
                    needsSubstitute = needsSub,
                    statusNotice = statusNotice,
                    recommendedCandidates = recommended,
                    manualOverrideCandidates = overrides
                )
            )
        }

        return needs.sortedWith(
            compareBy(
                { it.schoolId },
                { it.startMinutes },
                { it.className.lowercase() },
                { it.section.lowercase() }
            )
        )
    }

    /**
     * Ranks candidate substitutes for a specific period on [date].
     *
     * Eligibility (Recommended list):
     * - Active
     * - Not the absent teacher, and not on approved leave covering [date] & [targetSlotId]
     * - Not on their weekly off-day
     * - Free at `[targetStartMinutes, targetEndMinutes)` across ALL schools (checking BOTH regular
     *   timetable of non-absent teachers AND already-assigned substitutions on [date] for other slots/classes)
     * - Not over `maxPeriodsPerDay` (`regularActivePeriodsToday + assignedSubsToday < maxPeriodsPerDay`)
     *
     * Ranking order (strict):
     * 1. Teaches the same subject
     * 2. Teaches that class already (via Phase 4 assignment or weekly timetable cell)
     * 3. Lowest substitution count this month (`yyyy-MM`)
     * 4. Lowest total load that day (regular + substitutions)
     */
    fun rankSubstituteCandidates(
        date: String,
        dayOfWeek: DayOfWeekCode,
        targetSchoolId: String,
        targetClassName: String,
        targetSection: String,
        targetSlotId: String,
        targetSlotLabel: String,
        targetStartMinutes: Int,
        targetEndMinutes: Int,
        targetSubjectName: String,
        absentTeacherId: String,
        teachers: List<TeacherProfile>,
        approvedLeavesForDate: List<TeacherLeaveRecord>,
        allDayCellsAcrossSchools: List<TimetableCell>,
        allSubstitutionsInSession: List<SubstitutionRecord>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        classAssignments: List<ClassSectionAssignment>
    ): Pair<List<SubstituteCandidate>, List<SubstituteCandidate>> {
        val monthKey = Phase6DateUtils.monthPrefix(date)
        val currentSubDocId = SubstitutionRecord.makeSubstitutionDocId(
            date = date,
            schoolId = targetSchoolId,
            className = targetClassName,
            section = targetSection,
            slotId = targetSlotId
        )

        // Active substitutions on this date (excluding the target cell itself & excluding substitutes who are themselves absent)
        val otherSubsOnDate = allSubstitutionsInSession.filter {
            it.date == date && it.id != currentSubDocId
        }

        // Monthly substitution counts per teacher (in the same yyyy-MM month)
        val monthlySubCountByTeacher = allSubstitutionsInSession
            .filter { Phase6DateUtils.monthPrefix(it.date) == monthKey && it.id != currentSubDocId }
            .groupingBy { it.substituteTeacherId }
            .eachCount()

        val recommended = mutableListOf<SubstituteCandidate>()
        val manualOverrides = mutableListOf<SubstituteCandidate>()

        for (teacher in teachers) {
            if (teacher.id == absentTeacherId) continue

            // Check if teacher teaches the same subject (from TeacherProfile.subjects OR Phase 4 assignments)
            val teachesSubjectInProfile = teacher.subjects.any {
                it.equals(targetSubjectName, ignoreCase = true)
            }
            val teachesSubjectInAssignments = classAssignments.any { ca ->
                ca.subjectAssignments.any {
                    it.teacherId == teacher.id && it.subjectName.equals(targetSubjectName, ignoreCase = true)
                }
            }
            val teachesSameSubject = teachesSubjectInProfile || teachesSubjectInAssignments

            // Check if teacher already teaches that class (in Phase 4 assignments or weekly timetable)
            val teachesClassInAssignment = classAssignments.any { ca ->
                ca.schoolId == targetSchoolId &&
                    ca.className.equals(targetClassName, ignoreCase = true) &&
                    ca.section.equals(targetSection, ignoreCase = true) &&
                    (ca.classTeacherId == teacher.id || ca.subjectAssignments.any { it.teacherId == teacher.id })
            }
            val teachesClassInTimetable = allDayCellsAcrossSchools.any { c ->
                c.schoolId == targetSchoolId &&
                    c.className.equals(targetClassName, ignoreCase = true) &&
                    c.section.equals(targetSection, ignoreCase = true) &&
                    c.teacherId == teacher.id
            }
            val teachesThatClass = teachesClassInAssignment || teachesClassInTimetable

            // Regular periods on this day across all schools where this teacher is NOT absent
            val teacherRegularDayCells = allDayCellsAcrossSchools.filter { it.teacherId == teacher.id }
            val activeRegularDayCells = teacherRegularDayCells.filter { cell ->
                val st = periodStructuresBySchool[cell.schoolId]
                    ?: Phase4Validator.buildDefaultPeriodStructure(cell.schoolId)
                val sl = st.slotsFor(dayOfWeek).firstOrNull { it.id == cell.slotId }
                val onLeaveForCell = approvedLeavesForDate.any { lv ->
                    lv.teacherId == teacher.id && lv.coversApprovedPeriod(date, cell.slotId, sl?.label.orEmpty())
                }
                !onLeaveForCell
            }

            val teacherSubsToday = otherSubsOnDate.filter { it.substituteTeacherId == teacher.id }
            val regularLoadToday = activeRegularDayCells.size
            val subLoadToday = teacherSubsToday.size
            val totalDayLoad = regularLoadToday + subLoadToday
            val monthlySubs = monthlySubCountByTeacher[teacher.id] ?: 0

            // Check disqualifying conditions
            val disqualifications = mutableListOf<String>()
            if (!teacher.active) {
                disqualifications.add("Inactive")
            }
            val leaveAtTargetSlot = approvedLeavesForDate.firstOrNull { lv ->
                lv.teacherId == teacher.id && lv.coversApprovedPeriod(date, targetSlotId, targetSlotLabel)
            }
            if (leaveAtTargetSlot != null) {
                disqualifications.add("On leave (${leaveAtTargetSlot.reasonType.label})")
            }
            if (dayOfWeek in teacher.weeklyOffDays) {
                disqualifications.add("Weekly off-day (${dayOfWeek.shortLabel})")
            }

            // Real clock time overlap check across ALL schools (regular timetable + other substitutions on that date)
            val overlappingRegular = activeRegularDayCells.firstOrNull { cell ->
                val st = periodStructuresBySchool[cell.schoolId]
                    ?: Phase4Validator.buildDefaultPeriodStructure(cell.schoolId)
                val sl = st.slotsFor(dayOfWeek).firstOrNull { it.id == cell.slotId }
                sl != null && TimetableEngine.slotsOverlap(
                    targetStartMinutes,
                    targetEndMinutes,
                    sl.startMinutes,
                    sl.endMinutes
                )
            }
            if (overlappingRegular != null) {
                disqualifications.add("Busy teaching ${overlappingRegular.className}-${overlappingRegular.section} (${overlappingRegular.subjectName})")
            }

            val overlappingSub = teacherSubsToday.firstOrNull { sub ->
                TimetableEngine.slotsOverlap(
                    targetStartMinutes,
                    targetEndMinutes,
                    sub.startMinutes,
                    sub.endMinutes
                )
            }
            if (overlappingSub != null) {
                disqualifications.add("Already substituting in ${overlappingSub.className}-${overlappingSub.section}")
            }

            if (totalDayLoad >= teacher.maxPeriodsPerDay) {
                disqualifications.add("At daily limit ($totalDayLoad/${teacher.maxPeriodsPerDay} periods)")
            }

            // Build human-readable reason badges
            val badges = mutableListOf<String>()
            if (teachesSameSubject) badges.add("Same subject ($targetSubjectName)")
            if (teachesThatClass) badges.add("Teaches $targetClassName-$targetSection")
            badges.add("$monthlySubs sub(s) this month")
            badges.add("$totalDayLoad/${teacher.maxPeriodsPerDay} periods today")

            val isEligible = disqualifications.isEmpty()
            val candidate = SubstituteCandidate(
                teacher = teacher,
                isRecommendedEligible = isEligible,
                teachesSameSubject = teachesSameSubject,
                teachesThatClassAlready = teachesThatClass,
                monthlySubstitutionCount = monthlySubs,
                totalDayLoadIncludingSubs = totalDayLoad,
                regularPeriodsThatDay = regularLoadToday,
                substitutionsThatDay = subLoadToday,
                reasonBadges = badges,
                overrideRequired = !isEligible,
                overrideWarningReason = disqualifications.joinToString(" • ")
            )

            if (isEligible) {
                recommended.add(candidate)
            } else {
                manualOverrides.add(candidate)
            }
        }

        val comparator = Comparator<SubstituteCandidate> { a, b ->
            // 1. Teaches the same subject (true first)
            val subjCmp = b.teachesSameSubject.compareTo(a.teachesSameSubject)
            if (subjCmp != 0) return@Comparator subjCmp
            // 2. Teaches that class already (true first)
            val clsCmp = b.teachesThatClassAlready.compareTo(a.teachesThatClassAlready)
            if (clsCmp != 0) return@Comparator clsCmp
            // 3. Lowest substitution count this month
            val monthCmp = a.monthlySubstitutionCount.compareTo(b.monthlySubstitutionCount)
            if (monthCmp != 0) return@Comparator monthCmp
            // 4. Lowest total load that day (regular + substitutions)
            val dayCmp = a.totalDayLoadIncludingSubs.compareTo(b.totalDayLoadIncludingSubs)
            if (dayCmp != 0) return@Comparator dayCmp
            a.teacher.name.lowercase().compareTo(b.teacher.name.lowercase())
        }

        return recommended.sortedWith(comparator) to manualOverrides.sortedWith(comparator)
    }

    /**
     * Counts assigned substitutions in each teacher's workload for the week containing [referenceDate]
     * and the month (`yyyy-MM`) of [referenceDate].
     */
    fun computeTeacherWorkloadsWithSubstitutions(
        referenceDate: String,
        session: String,
        teachers: List<TeacherProfile>,
        timetableCellsInSession: List<TimetableCell>,
        substitutionsInSession: List<SubstitutionRecord>
    ): List<TeacherSubstitutionWorkloadSummary> {
        val (weekStart, weekEnd) = Phase6DateUtils.weekDateRangeFor(referenceDate)
        val monthKey = Phase6DateUtils.monthPrefix(referenceDate)

        val cellsForSession = timetableCellsInSession.filter { it.session == session }
        val subsForSession = substitutionsInSession.filter { it.session == session }

        return teachers.map { teacher ->
            val regularWeekly = cellsForSession.count { it.teacherId == teacher.id }
            val subsThisWeek = subsForSession.count {
                it.substituteTeacherId == teacher.id &&
                    Phase6DateUtils.isDateWithinRange(it.date, weekStart, weekEnd)
            }
            val subsThisMonth = subsForSession.count {
                it.substituteTeacherId == teacher.id &&
                    Phase6DateUtils.monthPrefix(it.date) == monthKey
            }
            TeacherSubstitutionWorkloadSummary(
                teacherId = teacher.id,
                teacherName = teacher.name,
                regularWeeklyPeriods = regularWeekly,
                substitutionsThisWeek = subsThisWeek,
                totalWeeklyLoadWithSubs = regularWeekly + subsThisWeek,
                substitutionsThisMonth = subsThisMonth,
                weeklyLimit = teacher.maxPeriodsPerDay * (7 - teacher.weeklyOffDays.size).coerceAtLeast(1)
            )
        }.sortedBy { it.teacherName.lowercase() }
    }

    /**
     * Detects when two phones edit leave or substitutions concurrently so neither silently
     * overwrites the other without a clear user notice.
     */
    fun detectPhase6Conflicts(
        pendingEdits: List<LocalPendingPhase6Edit>,
        confirmedLeaves: List<TeacherLeaveRecord>,
        confirmedSubstitutions: List<SubstitutionRecord>,
        hasConfirmedServerSnapshot: Boolean,
        currentUid: String,
        nowMs: Long = System.currentTimeMillis()
    ): Phase6ConflictDetectionResult {
        if (pendingEdits.isEmpty()) {
            return Phase6ConflictDetectionResult(emptyList(), emptyList())
        }
        val leavesById = confirmedLeaves.associateBy { it.id }
        val subsById = confirmedSubstitutions.associateBy { it.id }
        val remaining = mutableListOf<LocalPendingPhase6Edit>()
        val conflicts = mutableListOf<Phase6ConflictNotice>()
        val ttlMs = 7L * 24L * 60L * 60L * 1000L

        for (edit in pendingEdits) {
            if (nowMs - edit.clientEditedAtMs > ttlMs) continue
            if (edit.kind == "LEAVE") {
                val serverLeave = leavesById[edit.docId]
                if (serverLeave == null) {
                    if (!hasConfirmedServerSnapshot) {
                        remaining.add(edit)
                    } else if (!edit.wasDeleted) {
                        conflicts.add(
                            Phase6ConflictNotice(
                                kind = "LEAVE",
                                docId = edit.docId,
                                targetSummary = edit.targetSummary,
                                yourSummary = edit.localValueSummary,
                                winningSummary = "(Deleted)",
                                winnerName = "Another staff member",
                                detectedAtMs = nowMs
                            )
                        )
                    }
                    continue
                }
                if (serverLeave.isFromCache || serverLeave.hasPendingWrites) {
                    remaining.add(edit)
                    continue
                }
                val serverSummary = "${serverLeave.status.label} (${serverLeave.startDate}..${serverLeave.endDate})"
                val bySelf = serverLeave.updatedByUid == currentUid || serverLeave.updatedByUid.isBlank()
                if (!bySelf && !serverSummary.equals(edit.localValueSummary, ignoreCase = true)) {
                    conflicts.add(
                        Phase6ConflictNotice(
                            kind = "LEAVE",
                            docId = edit.docId,
                            targetSummary = edit.targetSummary,
                            yourSummary = edit.localValueSummary,
                            winningSummary = serverSummary,
                            winnerName = serverLeave.updatedByName.ifBlank { "Another staff member" },
                            detectedAtMs = nowMs
                        )
                    )
                }
            } else if (edit.kind == "SUB") {
                val serverSub = subsById[edit.docId]
                if (serverSub == null) {
                    if (!hasConfirmedServerSnapshot) {
                        remaining.add(edit)
                    } else if (!edit.wasDeleted) {
                        conflicts.add(
                            Phase6ConflictNotice(
                                kind = "SUB",
                                docId = edit.docId,
                                targetSummary = edit.targetSummary,
                                yourSummary = edit.localValueSummary,
                                winningSummary = "(Cleared)",
                                winnerName = "Another staff member",
                                detectedAtMs = nowMs
                            )
                        )
                    }
                    continue
                }
                if (serverSub.isFromCache || serverSub.hasPendingWrites) {
                    remaining.add(edit)
                    continue
                }
                val serverSummary = serverSub.substituteTeacherName.ifBlank { serverSub.substituteTeacherId }
                val bySelf = serverSub.updatedByUid == currentUid || serverSub.updatedByUid.isBlank()
                if (!bySelf && !serverSummary.equals(edit.localValueSummary, ignoreCase = true)) {
                    conflicts.add(
                        Phase6ConflictNotice(
                            kind = "SUB",
                            docId = edit.docId,
                            targetSummary = edit.targetSummary,
                            yourSummary = edit.localValueSummary,
                            winningSummary = serverSummary,
                            winnerName = serverSub.updatedByName.ifBlank { "Another staff member" },
                            detectedAtMs = nowMs
                        )
                    )
                }
            }
        }

        return Phase6ConflictDetectionResult(
            remainingPending = remaining,
            detectedConflicts = conflicts
        )
    }

    /**
     * Builds notification-ready plain text for sharing the daily substitution plan via WhatsApp/SMS/Email.
     */
    fun buildDailySubstitutionShareText(
        schoolName: String,
        date: String,
        items: List<SubstitutionNeedItem>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("SUBSTITUTION SLIP — ${schoolName.ifBlank { "School" }}")
        sb.appendLine("Date: ${Phase6DateUtils.formatDisplayDate(date)}")
        sb.appendLine("----------------------------------------")
        if (items.isEmpty()) {
            sb.appendLine("No teacher absences or substitutions for this date.")
        } else {
            for (item in items) {
                val timeRange = "${Phase4Validator.formatMinutes(item.startMinutes)}-${Phase4Validator.formatMinutes(item.endMinutes)}"
                val subLabel = when {
                    item.needsSubstitute -> "NEEDS SUBSTITUTE"
                    item.existingSubstitution?.overrideUsed == true ->
                        "${item.existingSubstitution.substituteTeacherName} [OVERRIDE]"
                    else -> item.existingSubstitution?.substituteTeacherName.orEmpty()
                }
                sb.appendLine(
                    "• ${item.slotLabel} ($timeRange) | Class ${item.className}-${item.section} | ${item.subjectName}"
                )
                sb.appendLine(
                    "  Absent: ${item.absentTeacherName} -> Sub: $subLabel"
                )
            }
        }
        return sb.toString().trim()
    }
}
