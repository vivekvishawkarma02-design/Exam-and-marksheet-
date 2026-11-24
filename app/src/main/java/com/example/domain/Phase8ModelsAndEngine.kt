package com.example.domain

import com.example.data.StudentEntity
import com.example.data.TermInfoEntity
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.floor

/**
 * Phase 8: Student Attendance Module — Pure Kotlin Domain Models & Engines.
 * Zero Android/Firebase dependencies so every rule is fast-tested with plain JUnit.
 * Strictly separate from Phase 7 Teacher Attendance (`TeacherAttendanceStatus`).
 */

// =============================================================================
// 1. STUDENT ATTENDANCE STATUS (P / A / L / Lt / H)
// =============================================================================
enum class StudentAttendanceStatus(
    val code: String,
    val label: String,
    val weight: Double
) {
    PRESENT("P", "Present", 1.0),
    ABSENT("A", "Absent", 0.0),
    LEAVE("L", "Leave", 0.0),
    LATE("Lt", "Late", 1.0),
    HALF_DAY("H", "Half-day", 0.5);

    fun nextCycle(): StudentAttendanceStatus = when (this) {
        PRESENT -> ABSENT
        ABSENT -> LEAVE
        LEAVE -> LATE
        LATE -> HALF_DAY
        HALF_DAY -> PRESENT
    }

    companion object {
        fun fromCode(raw: String?): StudentAttendanceStatus =
            entries.firstOrNull {
                it.code.equals(raw?.trim(), ignoreCase = true) ||
                    it.name.equals(raw?.trim(), ignoreCase = true)
            } ?: PRESENT
    }
}

// =============================================================================
// 2. SCHOOL CALENDAR MODELS & ENGINE (PER SCHOOL & SESSION)
// =============================================================================
enum class HolidayKind(val wireValue: String, val label: String) {
    HOLIDAY("HOLIDAY", "Holiday"),
    DECLARED_OFF("DECLARED_OFF", "Declared Off");

    companion object {
        fun fromRaw(raw: String?): HolidayKind =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: HOLIDAY
    }
}

data class CalendarHoliday(
    val id: String,
    val name: String,
    val startDate: String,
    val endDate: String = startDate,
    val kind: HolidayKind = HolidayKind.HOLIDAY
) {
    fun coversDate(dateIso: String): Boolean =
        Phase6DateUtils.isDateWithinRange(dateIso, startDate, endDate)

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id.take(64),
        "name" to name.trim().take(120),
        "startDate" to startDate.trim().take(16),
        "endDate" to endDate.trim().take(16),
        "kind" to kind.wireValue
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): CalendarHoliday? {
            if (raw == null) return null
            val start = (raw["startDate"] as? String)?.trim().orEmpty()
            val end = (raw["endDate"] as? String)?.trim().orEmpty().ifBlank { start }
            val name = (raw["name"] as? String)?.trim().orEmpty()
            if (!Phase6DateUtils.isValidIsoDate(start) || !Phase6DateUtils.isValidIsoDate(end) || name.isBlank()) {
                return null
            }
            val id = (raw["id"] as? String)?.trim().orEmpty().ifBlank { "hol_${start.replace("-", "_")}" }
            return CalendarHoliday(
                id = id,
                name = name,
                startDate = start,
                endDate = if (end >= start) end else start,
                kind = HolidayKind.fromRaw(raw["kind"] as? String)
            )
        }
    }
}

data class HalfWorkingDayEntry(
    val id: String,
    val date: String,
    val name: String = "Half Working Day"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id.take(64),
        "date" to date.trim().take(16),
        "name" to name.trim().take(120)
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): HalfWorkingDayEntry? {
            if (raw == null) return null
            val date = (raw["date"] as? String)?.trim().orEmpty()
            if (!Phase6DateUtils.isValidIsoDate(date)) return null
            val name = (raw["name"] as? String)?.trim().orEmpty().ifBlank { "Half Working Day" }
            val id = (raw["id"] as? String)?.trim().orEmpty().ifBlank { "half_${date.replace("-", "_")}" }
            return HalfWorkingDayEntry(id = id, date = date, name = name)
        }
    }
}

data class WorkingDayExceptionEntry(
    val id: String,
    val date: String,
    val note: String = "Working day exception"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id.take(64),
        "date" to date.trim().take(16),
        "note" to note.trim().take(160)
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): WorkingDayExceptionEntry? {
            if (raw == null) return null
            val date = (raw["date"] as? String)?.trim().orEmpty()
            if (!Phase6DateUtils.isValidIsoDate(date)) return null
            val note = (raw["note"] as? String)?.trim().orEmpty().ifBlank { "Working day exception" }
            val id = (raw["id"] as? String)?.trim().orEmpty().ifBlank { "wex_${date.replace("-", "_")}" }
            return WorkingDayExceptionEntry(id = id, date = date, note = note)
        }
    }
}

data class SchoolCalendarConfig(
    val id: String = "",
    val schoolId: String,
    val session: String,
    val workingDays: List<DayOfWeekCode> = Phase4Validator.DEFAULT_WORKING_DAYS,
    val holidays: List<CalendarHoliday> = emptyList(),
    val halfWorkingDays: List<HalfWorkingDayEntry> = emptyList(),
    val workingDayExceptions: List<WorkingDayExceptionEntry> = emptyList(),
    val term1StartDate: String = "",
    val term1EndDate: String = "",
    val term2StartDate: String = "",
    val term2EndDate: String = "",
    val updatedByUid: String = "",
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(
        actorUid: String,
        createdAtValue: Any? = null,
        updatedAtValue: Any
    ): Map<String, Any> = buildMap {
        put("schoolId", schoolId)
        put("session", session.trim().take(30))
        put("workingDays", workingDays.sortedBy { it.sortOrder }.map { it.wireValue })
        put("holidays", holidays.take(120).map { it.toMap() })
        put("halfWorkingDays", halfWorkingDays.take(60).map { it.toMap() })
        put("workingDayExceptions", workingDayExceptions.take(60).map { it.toMap() })
        put("term1StartDate", term1StartDate.trim().take(16))
        put("term1EndDate", term1EndDate.trim().take(16))
        put("term2StartDate", term2StartDate.trim().take(16))
        put("term2EndDate", term2EndDate.trim().take(16))
        put("updatedByUid", actorUid)
        if (createdAtValue != null) put("createdAt", createdAtValue)
        put("updatedAt", updatedAtValue)
    }

    companion object {
        fun makeDocId(schoolId: String, session: String): String {
            val s = schoolId.trim().replace(Regex("[^a-zA-Z0-9_-]+"), "_").trim('_').take(48).ifEmpty { "sch" }
            val ses = session.trim().replace(Regex("[^a-zA-Z0-9_-]+"), "_").trim('_').take(24).ifEmpty { "ses" }
            return "cal_${s}_$ses"
        }

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): SchoolCalendarConfig? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val session = (data["session"] as? String)?.trim().orEmpty()
            if (schoolId.isBlank() || session.isBlank()) return null
            val days = (data["workingDays"] as? List<*>)
                ?.mapNotNull { DayOfWeekCode.fromRaw(it as? String) }
                ?.distinct()
                ?.sortedBy { it.sortOrder }
                .orEmpty()
                .ifEmpty { Phase4Validator.DEFAULT_WORKING_DAYS }
            val holidays = (data["holidays"] as? List<*>)
                ?.mapNotNull { CalendarHoliday.fromMap(it as? Map<*, *>) }
                ?: emptyList()
            val halfDays = (data["halfWorkingDays"] as? List<*>)
                ?.mapNotNull { HalfWorkingDayEntry.fromMap(it as? Map<*, *>) }
                ?: emptyList()
            val exceptions = (data["workingDayExceptions"] as? List<*>)
                ?.mapNotNull { WorkingDayExceptionEntry.fromMap(it as? Map<*, *>) }
                ?: emptyList()
            val defaults = SchoolCalendarEngine.defaultTermDatesForSession(session)
            return SchoolCalendarConfig(
                id = docId,
                schoolId = schoolId,
                session = session,
                workingDays = days,
                holidays = holidays,
                halfWorkingDays = halfDays,
                workingDayExceptions = exceptions,
                term1StartDate = (data["term1StartDate"] as? String)?.trim().orEmpty().ifBlank { defaults.first.first },
                term1EndDate = (data["term1EndDate"] as? String)?.trim().orEmpty().ifBlank { defaults.first.second },
                term2StartDate = (data["term2StartDate"] as? String)?.trim().orEmpty().ifBlank { defaults.second.first },
                term2EndDate = (data["term2EndDate"] as? String)?.trim().orEmpty().ifBlank { defaults.second.second },
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

sealed interface DayCalendarStatus {
    data class Working(
        val isHalfWorkingDay: Boolean = false,
        val halfDayName: String = "",
        val isExceptionOverride: Boolean = false,
        val exceptionNote: String = ""
    ) : DayCalendarStatus

    data class NonWorking(
        val reasonTitle: String,
        val isHoliday: Boolean
    ) : DayCalendarStatus
}

data class WorkingDayRangeStats(
    val startDate: String,
    val endDate: String,
    val totalCalendarDays: Int,
    val workingDays: Int,
    val halfWorkingDays: Int,
    val holidayDays: Int,
    val weeklyOffDays: Int
)

object SchoolCalendarEngine {

    /**
     * Parses session year bounds from standard session strings like "2026-2027" or "2026-27".
     * Returns (startYear, endYear). Defaults safely to (2026, 2027).
     */
    fun parseSessionYears(session: String): Pair<Int, Int> {
        val nums = Regex("\\d{2,4}").findAll(session).map { it.value }.toList()
        val startYear = nums.firstOrNull()?.toIntOrNull()?.let { y ->
            if (y < 100) 2000 + y else y
        } ?: 2026
        val endYear = nums.getOrNull(1)?.toIntOrNull()?.let { y ->
            if (y < 100) (startYear / 100) * 100 + y else y
        } ?: (startYear + 1)
        val safeEnd = if (endYear >= startYear) endYear else startYear + 1
        return startYear to safeEnd
    }

    /**
     * Valid session boundary dates: Jan 1 of startYear through Dec 31 of endYear,
     * covering both April–March and June–May academic calendars safely.
     */
    fun sessionDateBounds(session: String): Pair<String, String> {
        val (startYear, endYear) = parseSessionYears(session)
        return String.format(Locale.US, "%04d-01-01", startYear) to
            String.format(Locale.US, "%04d-12-31", endYear)
    }

    /**
     * Default Indian academic year terms: Term-I Apr 1 – Sep 30, Term-II Oct 1 – Mar 31.
     */
    fun defaultTermDatesForSession(session: String): Pair<Pair<String, String>, Pair<String, String>> {
        val (startYear, endYear) = parseSessionYears(session)
        val t1 = String.format(Locale.US, "%04d-04-01", startYear) to
            String.format(Locale.US, "%04d-09-30", startYear)
        val t2 = String.format(Locale.US, "%04d-10-01", startYear) to
            String.format(Locale.US, "%04d-03-31", endYear)
        return t1 to t2
    }

    fun buildDefaultCalendar(
        schoolId: String,
        session: String,
        periodStructure: SchoolPeriodStructure? = null
    ): SchoolCalendarConfig {
        val (t1, t2) = defaultTermDatesForSession(session)
        val defaultWorking = periodStructure?.workingDays
            ?.takeIf { it.isNotEmpty() }
            ?: Phase4Validator.DEFAULT_WORKING_DAYS
        return SchoolCalendarConfig(
            id = SchoolCalendarConfig.makeDocId(schoolId, session),
            schoolId = schoolId,
            session = session,
            workingDays = defaultWorking,
            term1StartDate = t1.first,
            term1EndDate = t1.second,
            term2StartDate = t2.first,
            term2EndDate = t2.second
        )
    }

    fun validateTermDates(
        session: String,
        term1Start: String,
        term1End: String,
        term2Start: String,
        term2End: String
    ): String? {
        if (!Phase6DateUtils.isValidIsoDate(term1Start) || !Phase6DateUtils.isValidIsoDate(term1End)) {
            return "Enter valid YYYY-MM-DD dates for Term-I."
        }
        if (!Phase6DateUtils.isValidIsoDate(term2Start) || !Phase6DateUtils.isValidIsoDate(term2End)) {
            return "Enter valid YYYY-MM-DD dates for Term-II."
        }
        if (term1End < term1Start) {
            return "Term-I end date cannot be before Term-I start date."
        }
        if (term2End < term2Start) {
            return "Term-II end date cannot be before Term-II start date."
        }
        val (minBound, maxBound) = sessionDateBounds(session)
        val allDates = listOf(
            "Term-I start" to term1Start,
            "Term-I end" to term1End,
            "Term-II start" to term2Start,
            "Term-II end" to term2End
        )
        for ((label, d) in allDates) {
            if (d < minBound || d > maxBound) {
                return "$label ($d) must fall inside session $session ($minBound to $maxBound)."
            }
        }
        val overlaps = !(term1End < term2Start || term2End < term1Start)
        if (overlaps) {
            return "Term-I ($term1Start to $term1End) and Term-II ($term2Start to $term2End) cannot overlap."
        }
        return null
    }

    fun dayStatus(calendar: SchoolCalendarConfig, dateIso: String): DayCalendarStatus {
        val exception = calendar.workingDayExceptions.firstOrNull { it.date == dateIso }
        val half = calendar.halfWorkingDays.firstOrNull { it.date == dateIso }
        if (exception != null) {
            return DayCalendarStatus.Working(
                isHalfWorkingDay = half != null,
                halfDayName = half?.name.orEmpty(),
                isExceptionOverride = true,
                exceptionNote = exception.note
            )
        }
        val holiday = calendar.holidays.firstOrNull { it.coversDate(dateIso) }
        if (holiday != null) {
            val prefix = if (holiday.kind == HolidayKind.DECLARED_OFF) "Declared Off" else "Holiday"
            return DayCalendarStatus.NonWorking(
                reasonTitle = "$prefix: ${holiday.name}",
                isHoliday = true
            )
        }
        val dow = Phase6DateUtils.dayOfWeekForDate(dateIso)
        if (dow == null || dow !in calendar.workingDays) {
            val dayName = dow?.fullLabel ?: "Non-working day"
            return DayCalendarStatus.NonWorking(
                reasonTitle = "Non-working day ($dayName)",
                isHoliday = false
            )
        }
        return DayCalendarStatus.Working(
            isHalfWorkingDay = half != null,
            halfDayName = half?.name.orEmpty(),
            isExceptionOverride = false
        )
    }

    fun isWorkingDay(calendar: SchoolCalendarConfig, dateIso: String): Boolean =
        dayStatus(calendar, dateIso) is DayCalendarStatus.Working

    fun countWorkingDaysInRange(
        calendar: SchoolCalendarConfig,
        startDate: String,
        endDate: String
    ): WorkingDayRangeStats {
        if (!Phase6DateUtils.isValidIsoDate(startDate) || !Phase6DateUtils.isValidIsoDate(endDate) || endDate < startDate) {
            return WorkingDayRangeStats(startDate, endDate, 0, 0, 0, 0, 0)
        }
        val dates = enumerateDatesUnbounded(startDate, endDate, maxDays = 400)
        var working = 0
        var halfDays = 0
        var holidayDays = 0
        var weeklyOff = 0
        for (d in dates) {
            when (val status = dayStatus(calendar, d)) {
                is DayCalendarStatus.Working -> {
                    working++
                    if (status.isHalfWorkingDay) halfDays++
                }
                is DayCalendarStatus.NonWorking -> {
                    if (status.isHoliday) holidayDays++ else weeklyOff++
                }
            }
        }
        return WorkingDayRangeStats(
            startDate = startDate,
            endDate = endDate,
            totalCalendarDays = dates.size,
            workingDays = working,
            halfWorkingDays = halfDays,
            holidayDays = holidayDays,
            weeklyOffDays = weeklyOff
        )
    }

    fun termDateRange(calendar: SchoolCalendarConfig, termNo: Int): Pair<String, String> =
        if (termNo == 1) calendar.term1StartDate to calendar.term1EndDate
        else calendar.term2StartDate to calendar.term2EndDate

    fun enumerateDatesUnbounded(startDate: String, endDate: String, maxDays: Int = 400): List<String> {
        if (!Phase6DateUtils.isValidIsoDate(startDate) || !Phase6DateUtils.isValidIsoDate(endDate) || endDate < startDate) {
            return emptyList()
        }
        val out = mutableListOf<String>()
        var cur = startDate
        var guard = 0
        while (cur <= endDate && guard < maxDays) {
            out.add(cur)
            cur = Phase6DateUtils.addDays(cur, 1)
            guard++
        }
        return out
    }

    fun epochDayFromIso(dateIso: String): Int {
        if (!Phase6DateUtils.isValidIsoDate(dateIso)) return 0
        val parts = dateIso.split("-")
        val y = parts[0].toIntOrNull() ?: return 0
        val m = (parts[1].toIntOrNull() ?: return 0) - 1
        val d = parts[2].toIntOrNull() ?: return 0
        val cal = GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(y, m, d, 0, 0, 0)
        }
        return (cal.timeInMillis / 86_400_000L).toInt()
    }

    fun daysBetween(olderIso: String, newerIso: String): Int =
        epochDayFromIso(newerIso) - epochDayFromIso(olderIso)

    fun formatShortHumanDate(isoDate: String): String {
        if (!Phase6DateUtils.isValidIsoDate(isoDate)) return isoDate
        val parts = isoDate.split("-")
        val monthIdx = (parts[1].toIntOrNull() ?: 1) - 1
        val day = parts[2].toIntOrNull() ?: 1
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val mName = months.getOrElse(monthIdx) { "Jan" }
        return "$day $mName"
    }

    fun daysInMonth(year: Int, month1Based: Int): Int {
        val cal = GregorianCalendar(year, (month1Based - 1).coerceIn(0, 11), 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun datesOfMonth(year: Int, month1Based: Int): List<String> {
        val count = daysInMonth(year, month1Based)
        return (1..count).map { d ->
            String.format(Locale.US, "%04d-%02d-%02d", year, month1Based, d)
        }
    }
}

// =============================================================================
// 3. STUDENT ATTENDANCE SHEET, ENTRIES & EDIT LOG MODELS
// =============================================================================
data class StudentAttendanceEntry(
    val status: StudentAttendanceStatus = StudentAttendanceStatus.PRESENT,
    val reason: String = ""
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("status", status.code)
        if (reason.isNotBlank()) put("reason", reason.trim().take(160))
    }

    companion object {
        fun fromMap(raw: Any?): StudentAttendanceEntry {
            return when (raw) {
                is Map<*, *> -> StudentAttendanceEntry(
                    status = StudentAttendanceStatus.fromCode(raw["status"] as? String),
                    reason = (raw["reason"] as? String)?.trim().orEmpty()
                )
                is String -> StudentAttendanceEntry(status = StudentAttendanceStatus.fromCode(raw))
                else -> StudentAttendanceEntry()
            }
        }
    }
}

data class StudentAttendanceSheet(
    val id: String = "",
    val schoolId: String,
    val session: String,
    val className: String,
    val section: String,
    val date: String,
    val dateEpochDay: Int = SchoolCalendarEngine.epochDayFromIso(date),
    val entries: Map<String, StudentAttendanceEntry> = emptyMap(), // studentDocId -> entry
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val leaveCount: Int = 0,
    val lateCount: Int = 0,
    val halfDayCount: Int = 0,
    val totalStudents: Int = entries.size,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false
) {
    fun withRecomputedCounts(): StudentAttendanceSheet {
        var p = 0
        var a = 0
        var l = 0
        var lt = 0
        var h = 0
        for (entry in entries.values) {
            when (entry.status) {
                StudentAttendanceStatus.PRESENT -> p++
                StudentAttendanceStatus.ABSENT -> a++
                StudentAttendanceStatus.LEAVE -> l++
                StudentAttendanceStatus.LATE -> lt++
                StudentAttendanceStatus.HALF_DAY -> h++
            }
        }
        return copy(
            presentCount = p,
            absentCount = a,
            leaveCount = l,
            lateCount = lt,
            halfDayCount = h,
            totalStudents = entries.size,
            dateEpochDay = SchoolCalendarEngine.epochDayFromIso(date)
        )
    }

    fun toFirestoreMap(
        actorUid: String,
        actorName: String,
        editedAtMs: Long,
        createdAtValue: Any? = null,
        updatedAtValue: Any
    ): Map<String, Any> {
        val normalized = withRecomputedCounts()
        return buildMap {
            put("schoolId", normalized.schoolId)
            put("session", normalized.session.trim().take(30))
            put("className", normalized.className.trim().take(40))
            put("section", normalized.section.trim().uppercase().take(10))
            put("date", normalized.date.trim().take(16))
            put("dateEpochDay", normalized.dateEpochDay.toLong())
            put("entries", normalized.entries.mapValues { (_, v) -> v.toMap() })
            put("presentCount", normalized.presentCount.toLong())
            put("absentCount", normalized.absentCount.toLong())
            put("leaveCount", normalized.leaveCount.toLong())
            put("lateCount", normalized.lateCount.toLong())
            put("halfDayCount", normalized.halfDayCount.toLong())
            put("totalStudents", normalized.totalStudents.toLong())
            put("updatedByUid", actorUid)
            put("updatedByName", actorName.trim().take(120))
            put("clientEditedAtMs", editedAtMs.coerceAtLeast(0L))
            if (createdAtValue != null) put("createdAt", createdAtValue)
            put("updatedAt", updatedAtValue)
        }
    }

    companion object {
        fun makeDocId(
            schoolId: String,
            session: String,
            className: String,
            section: String,
            dateIso: String
        ): String {
            fun seg(v: String, max: Int) =
                v.trim().replace(Regex("[^a-zA-Z0-9_-]+"), "_").trim('_').take(max).ifEmpty { "x" }
            val d = dateIso.trim().replace("-", "_")
            return "satt_${seg(schoolId, 28)}_${seg(session, 16)}_${seg(className, 16)}_${seg(section.uppercase(), 8)}_$d"
        }

        fun fromMap(
            docId: String,
            data: Map<String, Any>?,
            hasPendingWrites: Boolean = false,
            isFromCache: Boolean = false
        ): StudentAttendanceSheet? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val session = (data["session"] as? String)?.trim().orEmpty()
            val className = (data["className"] as? String)?.trim().orEmpty()
            val section = (data["section"] as? String)?.trim().orEmpty().ifBlank { "A" }
            val date = (data["date"] as? String)?.trim().orEmpty()
            if (schoolId.isBlank() || className.isBlank() || !Phase6DateUtils.isValidIsoDate(date)) return null
            val rawEntries = data["entries"] as? Map<*, *>
            val parsedEntries = buildMap {
                rawEntries?.forEach { (k, v) ->
                    val key = (k as? String)?.trim().orEmpty()
                    if (key.isNotBlank()) put(key, StudentAttendanceEntry.fromMap(v))
                }
            }
            return StudentAttendanceSheet(
                id = docId,
                schoolId = schoolId,
                session = session,
                className = className,
                section = section,
                date = date,
                dateEpochDay = (data["dateEpochDay"] as? Number)?.toInt()
                    ?: SchoolCalendarEngine.epochDayFromIso(date),
                entries = parsedEntries,
                presentCount = (data["presentCount"] as? Number)?.toInt() ?: 0,
                absentCount = (data["absentCount"] as? Number)?.toInt() ?: 0,
                leaveCount = (data["leaveCount"] as? Number)?.toInt() ?: 0,
                lateCount = (data["lateCount"] as? Number)?.toInt() ?: 0,
                halfDayCount = (data["halfDayCount"] as? Number)?.toInt() ?: 0,
                totalStudents = (data["totalStudents"] as? Number)?.toInt() ?: parsedEntries.size,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites,
                isFromCache = isFromCache
            ).withRecomputedCounts()
        }
    }
}

data class StudentAttendanceChangeItem(
    val studentId: String,
    val studentName: String,
    val rollNo: Int,
    val oldStatus: String,
    val newStatus: String,
    val reason: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "studentId" to studentId.take(128),
        "studentName" to studentName.take(120),
        "rollNo" to rollNo.toLong(),
        "oldStatus" to oldStatus.take(8),
        "newStatus" to newStatus.take(8),
        "reason" to reason.take(160)
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): StudentAttendanceChangeItem? {
            if (raw == null) return null
            val sid = (raw["studentId"] as? String).orEmpty()
            if (sid.isBlank()) return null
            return StudentAttendanceChangeItem(
                studentId = sid,
                studentName = (raw["studentName"] as? String).orEmpty(),
                rollNo = (raw["rollNo"] as? Number)?.toInt() ?: 0,
                oldStatus = (raw["oldStatus"] as? String).orEmpty(),
                newStatus = (raw["newStatus"] as? String).orEmpty(),
                reason = (raw["reason"] as? String).orEmpty()
            )
        }
    }
}

data class StudentAttendanceEditLog(
    val id: String = "",
    val attendanceDocId: String = "",
    val schoolId: String = "",
    val session: String = "",
    val className: String = "",
    val section: String = "",
    val date: String = "",
    val editorUid: String = "",
    val editorName: String = "",
    val summary: String = "",
    val changes: List<StudentAttendanceChangeItem> = emptyList(),
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(createdAtValue: Any): Map<String, Any> = mapOf(
        "attendanceDocId" to attendanceDocId.take(128),
        "schoolId" to schoolId.take(128),
        "session" to session.take(30),
        "className" to className.take(40),
        "section" to section.uppercase().take(10),
        "date" to date.take(16),
        "editorUid" to editorUid.take(128),
        "editorName" to editorName.trim().ifBlank { "Staff" }.take(120),
        "summary" to summary.trim().take(350),
        "changes" to changes.take(80).map { it.toMap() },
        "clientEditedAtMs" to clientEditedAtMs.coerceAtLeast(0L),
        "createdAt" to createdAtValue
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): StudentAttendanceEditLog? {
            if (data == null) return null
            val attDocId = (data["attendanceDocId"] as? String).orEmpty()
            if (attDocId.isBlank()) return null
            val changes = (data["changes"] as? List<*>)
                ?.mapNotNull { StudentAttendanceChangeItem.fromMap(it as? Map<*, *>) }
                ?: emptyList()
            return StudentAttendanceEditLog(
                id = docId,
                attendanceDocId = attDocId,
                schoolId = (data["schoolId"] as? String).orEmpty(),
                session = (data["session"] as? String).orEmpty(),
                className = (data["className"] as? String).orEmpty(),
                section = (data["section"] as? String).orEmpty(),
                date = (data["date"] as? String).orEmpty(),
                editorUid = (data["editorUid"] as? String).orEmpty(),
                editorName = (data["editorName"] as? String).orEmpty(),
                summary = (data["summary"] as? String).orEmpty(),
                changes = changes,
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class AttendanceConflictNotice(
    val id: String,
    val sectionLabel: String,
    val date: String,
    val winningEditorName: String,
    val detectedAtMs: Long
)

// =============================================================================
// 4. PHASE 8 ACCESS EVALUATOR (9TH MODULE + CLASS TEACHER OWN-SECTION AUTO-ACCESS)
// =============================================================================
object StudentAttendanceAccessEvaluator {
    const val DEFAULT_CLASS_TEACHER_DAYS_BACK = 7

    fun isClassTeacherOfSection(
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String
    ): Boolean {
        if (teacherScope == null) return false
        return teacherScope.isClassTeacherOf(schoolId, className, section)
    }

    fun hasAnyClassTeacherSection(teacherScope: TeacherAccessScope?): Boolean =
        teacherScope != null && teacherScope.classTeacherSections.isNotEmpty()

    /**
     * Can the member open the Student Attendance module at all?
     * - Owner / Admin: YES
     * - Member with `attendance` == VIEW or EDIT: YES
     * - Class Teacher (even if `attendance` key is missing / NONE): YES (for own section!)
     */
    fun canAccessStudentAttendanceModule(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?
    ): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        if (OrgAccessEvaluator.canViewModule(member, AppModule.ATTENDANCE)) return true
        return hasAnyClassTeacherSection(teacherScope)
    }

    /**
     * Can the member view attendance for a specific school & section?
     */
    fun canViewSectionAttendance(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        if (OrgAccessEvaluator.canViewModule(member, AppModule.ATTENDANCE)) return true
        return isClassTeacherOfSection(teacherScope, schoolId, className, section)
    }

    /**
     * Can the member edit attendance for a specific school & section?
     * - Owner / Admin: YES (all sections)
     * - Member with `attendance == EDIT` in allowed school: YES
     * - Class Teacher of this exact section (even if `attendance == NONE` or missing key): YES
     * - Everyone else (including view-only members and subject teachers of other sections): NO
     */
    fun canEditSectionAttendance(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        if (OrgAccessEvaluator.canEditModule(member, AppModule.ATTENDANCE)) return true
        return isClassTeacherOfSection(teacherScope, schoolId, className, section)
    }

    /**
     * Checks whether a member is allowed to edit attendance on [targetDateIso] given [todayIso]
     * and the school's [classTeacherDaysBackLimit] (default 7).
     * - Future dates are blocked for everyone.
     * - Admins and non-teacher members with explicit `attendance: edit` have UNLIMITED past-date access.
     * - Class Teachers (and teachers editing via section access) are limited to `0..classTeacherDaysBackLimit` days back.
     * Returns `null` if allowed, or a user-facing error reason if blocked.
     */
    fun validateEditDateLimit(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String,
        targetDateIso: String,
        todayIso: String,
        classTeacherDaysBackLimit: Int = DEFAULT_CLASS_TEACHER_DAYS_BACK
    ): String? {
        if (!canEditSectionAttendance(member, teacherScope, schoolId, className, section)) {
            return "You do not have permission to edit attendance for Class $className-$section."
        }
        if (!Phase6DateUtils.isValidIsoDate(targetDateIso)) {
            return "Invalid date format."
        }
        val deltaDays = SchoolCalendarEngine.daysBetween(targetDateIso, todayIso)
        if (deltaDays < 0) {
            return "Cannot mark attendance for a future date ($targetDateIso)."
        }
        val m = member!!
        val isUnlimitedEditor = OrgAccessEvaluator.isAdmin(m) ||
            (m.role != OrgRole.TEACHER && OrgAccessEvaluator.canEditModule(m, AppModule.ATTENDANCE))
        if (isUnlimitedEditor) return null

        val safeLimit = classTeacherDaysBackLimit.coerceIn(0, 365)
        if (deltaDays > safeLimit) {
            return "Class teachers can edit attendance up to $safeLimit day(s) back. Ask an admin to update older dates."
        }
        return null
    }

    fun canEditSchoolCalendar(member: MemberAccessProfile?, schoolId: String): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        val m = member!!
        if (OrgAccessEvaluator.canEditModule(m, AppModule.SETTINGS)) return true
        return m.role != OrgRole.TEACHER && OrgAccessEvaluator.canEditModule(m, AppModule.ATTENDANCE)
    }
}

// =============================================================================
// 5. TERM ATTENDANCE, REPORT CARD AUTO-FILL, SUMMARY, LOW ATTENDANCE & MESSAGES
// =============================================================================
data class StudentTermAttendanceComputation(
    val termNo: Int,
    val rawDaysPresent: Double,
    val roundedDaysPresent: Int,
    val totalWorkingDays: Int,
    val markedDays: Int,
    val presentCount: Int,
    val lateCount: Int,
    val halfDayCount: Int,
    val leaveCount: Int,
    val absentCount: Int
) {
    val attendancePercent: Double
        get() = if (totalWorkingDays <= 0) 0.0 else (rawDaysPresent * 100.0 / totalWorkingDays.toDouble())

    fun formattedRawDaysPresent(): String {
        val asInt = rawDaysPresent.toInt()
        return if (kotlin.math.abs(rawDaysPresent - asInt.toDouble()) < 0.001) {
            asInt.toString()
        } else {
            String.format(Locale.US, "%.1f", rawDaysPresent)
        }
    }
}

data class EffectiveTermAttendance(
    val daysPresent: Int,
    val workingDays: Int,
    val isManual: Boolean,
    val isAutoFilled: Boolean
)

data class StudentRangeAttendanceSummaryRow(
    val studentDocId: String,
    val student: StudentEntity,
    val presentCount: Int,
    val lateCount: Int,
    val halfDayCount: Int,
    val leaveCount: Int,
    val absentCount: Int,
    val weightedDaysPresent: Double,
    val totalWorkingDays: Int
) {
    val attendancePercent: Double
        get() = if (totalWorkingDays <= 0) 0.0 else (weightedDaysPresent * 100.0 / totalWorkingDays.toDouble())

    fun formattedWeightedPresent(): String {
        val asInt = weightedDaysPresent.toInt()
        return if (kotlin.math.abs(weightedDaysPresent - asInt.toDouble()) < 0.001) {
            asInt.toString()
        } else {
            String.format(Locale.US, "%.1f", weightedDaysPresent)
        }
    }
}

data class LowAttendanceStudentRow(
    val schoolDocId: String,
    val schoolName: String,
    val studentDocId: String,
    val student: StudentEntity,
    val weightedDaysPresent: Double,
    val totalWorkingDays: Int,
    val attendancePercent: Double,
    val absentCount: Int,
    val leaveCount: Int
)

data class SectionDailyAttendanceStatus(
    val schoolDocId: String,
    val schoolName: String,
    val className: String,
    val section: String,
    val date: String,
    val taken: Boolean,
    val totalStudents: Int,
    val presentEquivalent: Int, // Present + Late
    val halfDayCount: Int,
    val absentCount: Int,
    val leaveCount: Int,
    val updatedByName: String = ""
) {
    val presentPercent: Int
        get() {
            if (!taken || totalStudents <= 0) return 0
            val weighted = presentEquivalent.toDouble() + (halfDayCount * 0.5)
            return floor((weighted * 100.0 / totalStudents.toDouble()) + 0.5).toInt().coerceIn(0, 100)
        }
}

data class SchoolTodayAttendanceSummary(
    val schoolDocId: String,
    val schoolName: String,
    val date: String,
    val dayStatus: DayCalendarStatus,
    val totalSections: Int,
    val sectionsTaken: Int,
    val totalMarkedStudents: Int,
    val weightedPresentStudents: Double,
    val sectionStatuses: List<SectionDailyAttendanceStatus>
) {
    val presentPercent: Int
        get() = if (totalMarkedStudents <= 0) 0
        else floor((weightedPresentStudents * 100.0 / totalMarkedStudents.toDouble()) + 0.5).toInt().coerceIn(0, 100)
}

object StudentAttendanceEngine {

    /**
     * Half-up rounding for converting weighted present days (e.g. 84.5 -> 85, 84.0 -> 84)
     * into the integer `TermInfoEntity.daysPresent` field used by Phase 1-7 report cards.
     */
    fun roundWeightedDays(raw: Double, maxWorkingDays: Int): Int {
        val rounded = floor(raw + 0.5).toInt().coerceAtLeast(0)
        return if (maxWorkingDays > 0) rounded.coerceAtMost(maxWorkingDays) else rounded
    }

    /**
     * Computes student attendance over any `[startDate..endDate]` range using the school calendar
     * for total working days and the daily attendance sheets for weighted presence:
     * Present = 1.0, Late = 1.0, Half-day = 0.5, Absent = 0.0, Leave = 0.0.
     */
    fun computeStudentAttendanceForRange(
        studentDocId: String,
        schoolId: String,
        session: String,
        calendar: SchoolCalendarConfig,
        sheets: List<StudentAttendanceSheet>,
        startDate: String,
        endDate: String,
        termNo: Int = 1
    ): StudentTermAttendanceComputation {
        val rangeStats = SchoolCalendarEngine.countWorkingDaysInRange(calendar, startDate, endDate)
        val relevantSheets = sheets.filter { sheet ->
            sheet.schoolId == schoolId &&
                sheet.session.equals(session, ignoreCase = true) &&
                Phase6DateUtils.isDateWithinRange(sheet.date, startDate, endDate) &&
                SchoolCalendarEngine.isWorkingDay(calendar, sheet.date) &&
                studentDocId in sheet.entries
        }
        var p = 0
        var lt = 0
        var h = 0
        var l = 0
        var a = 0
        var weighted = 0.0
        for (sheet in relevantSheets) {
            val entry = sheet.entries[studentDocId] ?: continue
            when (entry.status) {
                StudentAttendanceStatus.PRESENT -> p++
                StudentAttendanceStatus.LATE -> lt++
                StudentAttendanceStatus.HALF_DAY -> h++
                StudentAttendanceStatus.LEAVE -> l++
                StudentAttendanceStatus.ABSENT -> a++
            }
            weighted += entry.status.weight
        }
        val rounded = roundWeightedDays(weighted, rangeStats.workingDays)
        return StudentTermAttendanceComputation(
            termNo = termNo,
            rawDaysPresent = weighted,
            roundedDaysPresent = rounded,
            totalWorkingDays = rangeStats.workingDays,
            markedDays = relevantSheets.size,
            presentCount = p,
            lateCount = lt,
            halfDayCount = h,
            leaveCount = l,
            absentCount = a
        )
    }

    fun computeTermAttendance(
        studentDocId: String,
        schoolId: String,
        session: String,
        termNo: Int,
        calendar: SchoolCalendarConfig,
        sheets: List<StudentAttendanceSheet>
    ): StudentTermAttendanceComputation {
        val (start, end) = SchoolCalendarEngine.termDateRange(calendar, termNo)
        return computeStudentAttendanceForRange(
            studentDocId = studentDocId,
            schoolId = schoolId,
            session = session,
            calendar = calendar,
            sheets = sheets,
            startDate = start,
            endDate = end,
            termNo = termNo
        )
    }

    /**
     * Resolves the effective report card attendance for a student + term, enforcing:
     * 1. When [autoFillSwitchOn] is FALSE: returns stored [storedTermInfo] verbatim (ZERO changes to Phase 1-7!).
     * 2. When [autoFillSwitchOn] is TRUE:
     *    - If [isManualOverride] is TRUE (explicitly typed by teacher, OR pre-existing non-zero TermInfo where
     *      manual flag is null): protects and returns the manual values with `isManual = true`.
     *    - Otherwise fills `(daysPresent, workingDays)` from daily attendance computation!
     */
    fun resolveEffectiveTermAttendance(
        autoFillSwitchOn: Boolean,
        storedTermInfo: TermInfoEntity?,
        storedManualFlag: Boolean?,
        dailyComputation: StudentTermAttendanceComputation
    ): EffectiveTermAttendance {
        val storedPresent = storedTermInfo?.daysPresent ?: 0
        val storedWorking = storedTermInfo?.workingDays ?: 0
        val hasPreExistingManualValues = storedManualFlag == null && (storedPresent > 0 || storedWorking > 0)
        val isManualProtected = storedManualFlag == true || hasPreExistingManualValues

        if (!autoFillSwitchOn) {
            return EffectiveTermAttendance(
                daysPresent = storedPresent,
                workingDays = storedWorking,
                isManual = storedPresent > 0 || storedWorking > 0,
                isAutoFilled = false
            )
        }
        if (isManualProtected) {
            return EffectiveTermAttendance(
                daysPresent = storedPresent,
                workingDays = storedWorking,
                isManual = true,
                isAutoFilled = false
            )
        }
        return EffectiveTermAttendance(
            daysPresent = dailyComputation.roundedDaysPresent,
            workingDays = dailyComputation.totalWorkingDays,
            isManual = false,
            isAutoFilled = true
        )
    }

    fun buildEffectiveAttendanceSummary(
        autoFillSwitchOn: Boolean,
        term1Stored: TermInfoEntity?,
        term1ManualFlag: Boolean?,
        term2Stored: TermInfoEntity?,
        term2ManualFlag: Boolean?,
        term1Daily: StudentTermAttendanceComputation,
        term2Daily: StudentTermAttendanceComputation
    ): AttendanceSummary {
        val eff1 = resolveEffectiveTermAttendance(autoFillSwitchOn, term1Stored, term1ManualFlag, term1Daily)
        val eff2 = resolveEffectiveTermAttendance(autoFillSwitchOn, term2Stored, term2ManualFlag, term2Daily)
        return AttendanceSummary(
            t1Present = eff1.daysPresent,
            t1Working = eff1.workingDays,
            t2Present = eff2.daysPresent,
            t2Working = eff2.workingDays
        )
    }

    fun buildSectionRangeSummary(
        students: List<Pair<String, StudentEntity>>, // (studentDocId, entity)
        schoolId: String,
        session: String,
        calendar: SchoolCalendarConfig,
        sheets: List<StudentAttendanceSheet>,
        startDate: String,
        endDate: String
    ): List<StudentRangeAttendanceSummaryRow> {
        return students
            .sortedBy { it.second.rollNo }
            .map { (docId, student) ->
                val comp = computeStudentAttendanceForRange(
                    studentDocId = docId,
                    schoolId = schoolId,
                    session = session,
                    calendar = calendar,
                    sheets = sheets,
                    startDate = startDate,
                    endDate = endDate
                )
                StudentRangeAttendanceSummaryRow(
                    studentDocId = docId,
                    student = student,
                    presentCount = comp.presentCount,
                    lateCount = comp.lateCount,
                    halfDayCount = comp.halfDayCount,
                    leaveCount = comp.leaveCount,
                    absentCount = comp.absentCount,
                    weightedDaysPresent = comp.rawDaysPresent,
                    totalWorkingDays = comp.totalWorkingDays
                )
            }
    }

    fun buildLowAttendanceList(
        students: List<Triple<String, String, Pair<String, StudentEntity>>>, // (schoolDocId, schoolName, (studentDocId, student))
        session: String,
        calendarsBySchool: Map<String, SchoolCalendarConfig>,
        sheets: List<StudentAttendanceSheet>,
        startDate: String,
        endDate: String,
        thresholdPercent: Int = 75,
        schoolIdFilter: String? = null,
        classNameFilter: String? = null,
        sectionFilter: String? = null
    ): List<LowAttendanceStudentRow> {
        val safeThreshold = thresholdPercent.coerceIn(1, 100).toDouble()
        return students
            .filter { (schId, _, pair) ->
                val st = pair.second
                st.session.equals(session, ignoreCase = true) &&
                    (schoolIdFilter.isNullOrBlank() || schId == schoolIdFilter) &&
                    (classNameFilter.isNullOrBlank() || st.className.equals(classNameFilter, ignoreCase = true)) &&
                    (sectionFilter.isNullOrBlank() || st.section.equals(sectionFilter, ignoreCase = true))
            }
            .mapNotNull { (schId, schName, pair) ->
                val (stuDocId, student) = pair
                val cal = calendarsBySchool[schId] ?: SchoolCalendarEngine.buildDefaultCalendar(schId, session)
                val comp = computeStudentAttendanceForRange(
                    studentDocId = stuDocId,
                    schoolId = schId,
                    session = session,
                    calendar = cal,
                    sheets = sheets,
                    startDate = startDate,
                    endDate = endDate
                )
                if (comp.totalWorkingDays <= 0 || comp.markedDays <= 0) return@mapNotNull null
                if (comp.attendancePercent < safeThreshold) {
                    LowAttendanceStudentRow(
                        schoolDocId = schId,
                        schoolName = schName,
                        studentDocId = stuDocId,
                        student = student,
                        weightedDaysPresent = comp.rawDaysPresent,
                        totalWorkingDays = comp.totalWorkingDays,
                        attendancePercent = comp.attendancePercent,
                        absentCount = comp.absentCount,
                        leaveCount = comp.leaveCount
                    )
                } else null
            }
            .sortedWith(
                compareBy<LowAttendanceStudentRow> { it.attendancePercent }
                    .thenBy { it.schoolName.lowercase(Locale.US) }
                    .thenBy { it.student.className.lowercase(Locale.US) }
                    .thenBy { it.student.section.uppercase(Locale.US) }
                    .thenBy { it.student.rollNo }
            )
    }

    fun buildLowAttendanceCsv(
        rows: List<LowAttendanceStudentRow>,
        thresholdPercent: Int,
        startDate: String,
        endDate: String
    ): String {
        fun esc(v: String): String {
            val clean = v.replace("\"", "\"\"")
            return if (clean.contains(',') || clean.contains('"') || clean.contains('\n')) "\"$clean\"" else clean
        }
        val sb = StringBuilder()
        sb.appendLine("School,Class,Section,Roll No,Student Name,Guardian Mobile,Days Present,Working Days,Attendance %,Absent,Leave,Threshold %,Range")
        for (r in rows) {
            val presentStr = String.format(Locale.US, "%.1f", r.weightedDaysPresent).removeSuffix(".0")
            val pctStr = String.format(Locale.US, "%.1f", r.attendancePercent)
            sb.appendLine(
                listOf(
                    esc(r.schoolName),
                    esc(r.student.className),
                    esc(r.student.section),
                    r.student.rollNo.toString(),
                    esc(r.student.name),
                    esc(r.student.mobile),
                    presentStr,
                    r.totalWorkingDays.toString(),
                    pctStr,
                    r.absentCount.toString(),
                    r.leaveCount.toString(),
                    thresholdPercent.toString(),
                    "$startDate to $endDate"
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    /**
     * Computes exact changes between [previousSheet] and [newEntries] for edit logging.
     */
    fun computeEditChanges(
        previousSheet: StudentAttendanceSheet?,
        newEntries: Map<String, StudentAttendanceEntry>,
        studentsByDocId: Map<String, StudentEntity>
    ): List<StudentAttendanceChangeItem> {
        val oldMap = previousSheet?.entries.orEmpty()
        val allIds = (oldMap.keys + newEntries.keys).distinct()
        return allIds.mapNotNull { sid ->
            val oldEntry = oldMap[sid]
            val newEntry = newEntries[sid] ?: return@mapNotNull null
            val oldCode = oldEntry?.status?.code ?: "-"
            val newCode = newEntry.status.code
            val oldReason = oldEntry?.reason.orEmpty().trim()
            val newReason = newEntry.reason.trim()
            if (oldCode == newCode && oldReason == newReason) return@mapNotNull null
            val stu = studentsByDocId[sid]
            StudentAttendanceChangeItem(
                studentId = sid,
                studentName = stu?.name ?: sid,
                rollNo = stu?.rollNo ?: 0,
                oldStatus = oldCode,
                newStatus = newCode,
                reason = newReason
            )
        }.sortedBy { it.rollNo }
    }

    fun shouldWritePostDayEditLog(
        sheetDateIso: String,
        todayIso: String,
        sheetAlreadyExisted: Boolean,
        changes: List<StudentAttendanceChangeItem>
    ): Boolean {
        if (changes.isEmpty()) return false
        return sheetDateIso < todayIso || sheetAlreadyExisted
    }

    fun formatEditChangesSummary(changes: List<StudentAttendanceChangeItem>): String {
        if (changes.isEmpty()) return "Attendance saved"
        val preview = changes.take(4).joinToString("; ") { c ->
            val rollPart = if (c.rollNo > 0) "R${c.rollNo} " else ""
            val reasonPart = if (c.reason.isNotBlank()) " (${c.reason})" else ""
            "$rollPart${c.studentName}: ${c.oldStatus}→${c.newStatus}$reasonPart"
        }
        val extra = if (changes.size > 4) " (+${changes.size - 4} more)" else ""
        return (preview + extra).take(350)
    }

    /**
     * Guardian WhatsApp / SMS text for a single absent student (never sent automatically).
     */
    fun buildGuardianAbsentMessage(
        studentName: String,
        className: String,
        section: String,
        dateIso: String,
        schoolName: String = ""
    ): String {
        val humanDate = SchoolCalendarEngine.formatShortHumanDate(dateIso)
        val base = "$studentName (Class $className-$section) was absent on $humanDate. Please send a note to the class teacher."
        return if (schoolName.isNotBlank()) "$base - $schoolName" else base
    }

    /**
     * Ready-to-share text listing all absent students of a section for a date.
     */
    fun buildBatchAbsentMessage(
        schoolName: String,
        className: String,
        section: String,
        dateIso: String,
        absentStudents: List<StudentEntity>
    ): String {
        val humanDate = SchoolCalendarEngine.formatShortHumanDate(dateIso)
        if (absentStudents.isEmpty()) {
            return "$schoolName — Class $className-$section ($humanDate): 100% present (no absent students)."
        }
        val sb = StringBuilder()
        sb.appendLine("Absentee List — $schoolName")
        sb.appendLine("Class $className-$section • $humanDate (${absentStudents.size} absent):")
        absentStudents.sortedBy { it.rollNo }.forEach { st ->
            val phonePart = if (st.mobile.isNotBlank()) " [${st.mobile}]" else ""
            sb.appendLine("• Roll ${st.rollNo}: ${st.name}$phonePart")
        }
        sb.append("Please send a note to the class teacher.")
        return sb.toString()
    }
}
