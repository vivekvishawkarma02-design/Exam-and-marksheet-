package com.example.domain

import com.example.data.StudentEntity
import com.example.data.cloud.StableDocId
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

// ============================================================================
// PHASE 11: EXAM SCHEDULE & ADMIT CARDS, STOCK, AND TRANSPORT ROUTES
// Pure Kotlin models & engines (zero Android/Firebase dependencies) for direct unit testing.
// ============================================================================

// ============================================================================
// PART A: PHASE 11 ACCESS EVALUATOR (3 new modules: exams, stock, transport)
// Missing key = AccessLevel.NONE (no access). Owner & Admin have full access.
// ============================================================================

object Phase11AccessEvaluator {
    fun canViewExams(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canViewModule(member, AppModule.EXAMS)
    }

    fun canEditExams(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canEditModule(member, AppModule.EXAMS)
    }

    fun canViewStock(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canViewModule(member, AppModule.STOCK)
    }

    fun canEditStock(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canEditModule(member, AppModule.STOCK)
    }

    fun canViewTransport(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canViewModule(member, AppModule.TRANSPORT)
    }

    fun canEditTransport(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canEditModule(member, AppModule.TRANSPORT)
    }
}

// ============================================================================
// PART B: EXAM SCHEDULE, ROOMS & SEATING, INVIGILATOR DUTIES, ADMIT CARDS
// ============================================================================

enum class ExamCategoryType(val wireValue: String, val englishLabel: String, val hindiLabel: String) {
    UT_I("UT_I", "UT-I", "प्रथम इकाई परीक्षा (UT-I)"),
    UT_II("UT_II", "UT-II", "द्वितीय इकाई परीक्षा (UT-II)"),
    HALF_YEARLY("HALF_YEARLY", "Half-Yearly", "अर्धवार्षिक परीक्षा (Half-Yearly)"),
    YEARLY("YEARLY", "Yearly", "वार्षिक परीक्षा (Yearly)"),
    CUSTOM("CUSTOM", "Custom", "अन्य परीक्षा (Custom)");

    companion object {
        fun fromRaw(raw: String?): ExamCategoryType =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: CUSTOM
    }
}

data class ExamScheduleEntry(
    val entryId: String,
    val className: String,
    val subjectName: String,
    val dateIso: String, // YYYY-MM-DD
    val startTime: String, // HH:mm
    val endTime: String, // HH:mm
    val maxMarks: Int = 100
) {
    fun toMap(): Map<String, Any> = mapOf(
        "entryId" to entryId.trim().take(80),
        "className" to className.trim().take(40),
        "subjectName" to subjectName.trim().take(80),
        "dateIso" to dateIso.trim().take(10),
        "startTime" to startTime.trim().take(8),
        "endTime" to endTime.trim().take(8),
        "maxMarks" to maxMarks.coerceIn(1, 1000).toLong()
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): ExamScheduleEntry? {
            if (raw == null) return null
            val id = (raw["entryId"] as? String)?.trim().orEmpty()
            val cls = (raw["className"] as? String)?.trim().orEmpty()
            val subj = (raw["subjectName"] as? String)?.trim().orEmpty()
            val date = (raw["dateIso"] as? String)?.trim().orEmpty()
            val start = (raw["startTime"] as? String)?.trim().orEmpty()
            val end = (raw["endTime"] as? String)?.trim().orEmpty()
            if (cls.isEmpty() || subj.isEmpty() || date.isEmpty()) return null
            return ExamScheduleEntry(
                entryId = id.ifEmpty { "${cls}_${subj}_${date}" },
                className = cls,
                subjectName = subj,
                dateIso = date,
                startTime = start.ifEmpty { "09:00" },
                endTime = end.ifEmpty { "12:00" },
                maxMarks = (raw["maxMarks"] as? Number)?.toInt()?.coerceIn(1, 1000) ?: 100
            )
        }
    }
}

data class ExamScheduleRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val examType: ExamCategoryType,
    val title: String,
    val entries: List<ExamScheduleEntry> = emptyList(),
    val withholdAdmitCardAboveDuesRs: Int = 0, // 0 = disabled (optional feature)
    val admitCardOverrideStudentIds: List<String> = emptyList(),
    val admitCardInstructions: String = DEFAULT_ADMIT_INSTRUCTIONS,
    val createdByUid: String = "",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(uid: String, editorName: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "examType" to examType.wireValue,
        "title" to title.trim().take(120),
        "entries" to entries.take(300).map { it.toMap() },
        "withholdAdmitCardAboveDuesRs" to withholdAdmitCardAboveDuesRs.coerceIn(0, 10_000_000).toLong(),
        "admitCardOverrideStudentIds" to admitCardOverrideStudentIds.distinct().take(500),
        "admitCardInstructions" to admitCardInstructions.trim().take(500),
        "createdByUid" to createdByUid.ifBlank { uid },
        "updatedByUid" to uid,
        "updatedByName" to editorName.trim().take(120),
        "clientEditedAtMs" to nowMs
    )

    companion object {
        const val DEFAULT_ADMIT_INSTRUCTIONS =
            "1. Bring this Admit Card daily to the examination hall.\n2. Report 15 minutes before the scheduled start time.\n3. Electronic gadgets and unfair means are strictly prohibited."

        fun makeDocId(schoolId: String, session: String, slug: String): String =
            "exam_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(session)}_${StableDocId.sanitizeSegment(slug)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): ExamScheduleRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val title = (data["title"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || title.isEmpty()) return null
            val parsedEntries = (data["entries"] as? List<*>)
                ?.mapNotNull { ExamScheduleEntry.fromMap(it as? Map<*, *>) }
                .orEmpty()
            val overrides = (data["admitCardOverrideStudentIds"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            return ExamScheduleRecord(
                id = docId,
                schoolId = schoolId,
                session = (data["session"] as? String)?.trim().orEmpty(),
                examType = ExamCategoryType.fromRaw(data["examType"] as? String),
                title = title,
                entries = parsedEntries,
                withholdAdmitCardAboveDuesRs = (data["withholdAdmitCardAboveDuesRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                admitCardOverrideStudentIds = overrides,
                admitCardInstructions = (data["admitCardInstructions"] as? String)?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_ADMIT_INSTRUCTIONS,
                createdByUid = (data["createdByUid"] as? String).orEmpty(),
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class ExamRoomRecord(
    val id: String,
    val schoolId: String,
    val roomName: String,
    val capacity: Int,
    val active: Boolean = true,
    val updatedByUid: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(uid: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "roomName" to roomName.trim().take(60),
        "capacity" to capacity.coerceIn(1, 500).toLong(),
        "active" to active,
        "updatedByUid" to uid,
        "clientEditedAtMs" to nowMs
    )

    companion object {
        fun makeDocId(schoolId: String, roomName: String): String =
            "eroom_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(roomName)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): ExamRoomRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val roomName = (data["roomName"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || roomName.isEmpty()) return null
            return ExamRoomRecord(
                id = docId,
                schoolId = schoolId,
                roomName = roomName,
                capacity = (data["capacity"] as? Number)?.toInt()?.coerceIn(1, 500) ?: 30,
                active = (data["active"] as? Boolean) ?: true,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

enum class SeatingAllocationMode(val wireValue: String, val englishLabel: String, val hindiLabel: String) {
    BY_ROLL_NUMBER("BY_ROLL_NUMBER", "By Roll Number", "रोल नंबर अनुसार"),
    MIXED_BY_CLASS("MIXED_BY_CLASS", "Mixed by Class", "कक्षा मिलाकर (Mixed)");

    companion object {
        fun fromRaw(raw: String?): SeatingAllocationMode =
            if (raw.equals("MIXED_BY_CLASS", ignoreCase = true)) MIXED_BY_CLASS else BY_ROLL_NUMBER
    }
}

data class ExamSeatAllocation(
    val roomId: String,
    val roomName: String,
    val seatNumber: Int,
    val studentDocId: String,
    val studentName: String,
    val studentAdmNo: String,
    val className: String,
    val section: String,
    val rollNo: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "roomId" to roomId,
        "roomName" to roomName.take(60),
        "seatNumber" to seatNumber.coerceAtLeast(1).toLong(),
        "studentDocId" to studentDocId,
        "studentName" to studentName.take(120),
        "studentAdmNo" to studentAdmNo.take(60),
        "className" to className.take(40),
        "section" to section.take(10),
        "rollNo" to rollNo.coerceAtLeast(1).toLong()
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): ExamSeatAllocation? {
            if (raw == null) return null
            val roomId = (raw["roomId"] as? String)?.trim().orEmpty()
            val stuId = (raw["studentDocId"] as? String)?.trim().orEmpty()
            if (roomId.isEmpty() || stuId.isEmpty()) return null
            return ExamSeatAllocation(
                roomId = roomId,
                roomName = (raw["roomName"] as? String).orEmpty(),
                seatNumber = (raw["seatNumber"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1,
                studentDocId = stuId,
                studentName = (raw["studentName"] as? String).orEmpty(),
                studentAdmNo = (raw["studentAdmNo"] as? String).orEmpty(),
                className = (raw["className"] as? String).orEmpty(),
                section = (raw["section"] as? String).orEmpty().ifBlank { "A" },
                rollNo = (raw["rollNo"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1
            )
        }
    }
}

data class ExamSeatingRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val examId: String,
    val allocationMode: SeatingAllocationMode = SeatingAllocationMode.BY_ROLL_NUMBER,
    val includedClasses: List<String> = emptyList(),
    val roomIds: List<String> = emptyList(),
    val allocations: List<ExamSeatAllocation> = emptyList(),
    val unallocatedStudentIds: List<String> = emptyList(),
    val updatedByUid: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(uid: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "examId" to examId,
        "allocationMode" to allocationMode.wireValue,
        "includedClasses" to includedClasses.distinct().take(60),
        "roomIds" to roomIds.distinct().take(100),
        "allocations" to allocations.take(2500).map { it.toMap() },
        "unallocatedStudentIds" to unallocatedStudentIds.distinct().take(1000),
        "updatedByUid" to uid,
        "clientEditedAtMs" to nowMs
    )

    companion object {
        fun makeDocId(examId: String): String = "seat_${StableDocId.sanitizeSegment(examId)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): ExamSeatingRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val examId = (data["examId"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || examId.isEmpty()) return null
            return ExamSeatingRecord(
                id = docId,
                schoolId = schoolId,
                session = (data["session"] as? String)?.trim().orEmpty(),
                examId = examId,
                allocationMode = SeatingAllocationMode.fromRaw(data["allocationMode"] as? String),
                includedClasses = (data["includedClasses"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                roomIds = (data["roomIds"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                allocations = (data["allocations"] as? List<*>)?.mapNotNull { ExamSeatAllocation.fromMap(it as? Map<*, *>) }.orEmpty(),
                unallocatedStudentIds = (data["unallocatedStudentIds"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class ExamInvigilatorDutyRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val examId: String,
    val examTitle: String,
    val dateIso: String,
    val startTime: String,
    val endTime: String,
    val roomId: String,
    val roomName: String,
    val teacherId: String,
    val teacherUid: String = "", // Linked member UID so teachers see "My exam duties" on Home
    val teacherName: String,
    val assignedManually: Boolean = false,
    val updatedByUid: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(uid: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "examId" to examId,
        "examTitle" to examTitle.trim().take(120),
        "dateIso" to dateIso.trim().take(10),
        "startTime" to startTime.trim().take(8),
        "endTime" to endTime.trim().take(8),
        "roomId" to roomId,
        "roomName" to roomName.trim().take(60),
        "teacherId" to teacherId,
        "teacherUid" to teacherUid.trim().take(128),
        "teacherName" to teacherName.trim().take(120),
        "assignedManually" to assignedManually,
        "updatedByUid" to uid,
        "clientEditedAtMs" to nowMs
    )

    companion object {
        fun makeDocId(examId: String, dateIso: String, startTime: String, roomId: String): String =
            "duty_${StableDocId.sanitizeSegment(examId)}_${StableDocId.sanitizeSegment(dateIso)}_${StableDocId.sanitizeSegment(startTime)}_${StableDocId.sanitizeSegment(roomId)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): ExamInvigilatorDutyRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val examId = (data["examId"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || examId.isEmpty()) return null
            return ExamInvigilatorDutyRecord(
                id = docId,
                schoolId = schoolId,
                session = (data["session"] as? String)?.trim().orEmpty(),
                examId = examId,
                examTitle = (data["examTitle"] as? String).orEmpty(),
                dateIso = (data["dateIso"] as? String).orEmpty(),
                startTime = (data["startTime"] as? String).orEmpty(),
                endTime = (data["endTime"] as? String).orEmpty(),
                roomId = (data["roomId"] as? String).orEmpty(),
                roomName = (data["roomName"] as? String).orEmpty(),
                teacherId = (data["teacherId"] as? String).orEmpty(),
                teacherUid = (data["teacherUid"] as? String).orEmpty(),
                teacherName = (data["teacherName"] as? String).orEmpty(),
                assignedManually = (data["assignedManually"] as? Boolean) ?: false,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class ExamSlotKey(
    val dateIso: String,
    val startTime: String,
    val endTime: String
)

data class ExamDutyAssignmentResult(
    val duties: List<ExamInvigilatorDutyRecord>,
    val unfilledSlots: List<Pair<ExamSlotKey, ExamRoomRecord>>
)

data class ExamAdmitCardEligibility(
    val student: StudentEntity,
    val studentDocId: String,
    val totalFeeDueRs: Int,
    val isWithheldByDues: Boolean,
    val isOverriddenByAdmin: Boolean,
    val seat: ExamSeatAllocation?
) {
    val canPrint: Boolean get() = !isWithheldByDues || isOverriddenByAdmin
}

object ExamScheduleEngine {

    /**
     * Parses session string like `"2026-2027"` into inclusive date bounds `("2026-04-01", "2027-03-31")`.
     * Supports `"2026-27"` or `"2026"` as well.
     */
    fun sessionDateBounds(session: String): Pair<String, String> {
        val nums = Regex("\\d{2,4}").findAll(session).map { it.value }.toList()
        val startYear = nums.firstOrNull()?.toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: 2026
        val endYear = nums.getOrNull(1)?.toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: (startYear + 1)
        val safeEndYear = if (endYear >= startYear) endYear else startYear + 1
        return String.format(Locale.US, "%04d-04-01", startYear) to String.format(Locale.US, "%04d-03-31", safeEndYear)
    }

    fun isDateWithinSession(dateIso: String, session: String): Boolean {
        if (!Phase6DateUtils.isValidIsoDate(dateIso)) return false
        val (minDate, maxDate) = sessionDateBounds(session)
        return dateIso >= minDate && dateIso <= maxDate
    }

    fun parseTimeMinutes(hhmm: String): Int? {
        val parts = hhmm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun timesOverlap(startA: String, endA: String, startB: String, endB: String): Boolean {
        val sA = parseTimeMinutes(startA) ?: return true
        val eA = parseTimeMinutes(endA) ?: return true
        val sB = parseTimeMinutes(startB) ?: return true
        val eB = parseTimeMinutes(endB) ?: return true
        return sA < eB && sB < eA
    }

    /**
     * Validates a single exam paper entry against session date bounds, valid times,
     * duplicate subject for the same class, and time-clash on the same day for the same class.
     */
    fun validateEntry(
        candidate: ExamScheduleEntry,
        session: String,
        existingEntries: List<ExamScheduleEntry>
    ): String? {
        if (candidate.className.trim().isEmpty()) return "Please choose a class."
        if (candidate.subjectName.trim().isEmpty()) return "Please choose a subject."
        if (!Phase6DateUtils.isValidIsoDate(candidate.dateIso)) return "Please enter a valid date (YYYY-MM-DD)."
        if (!isDateWithinSession(candidate.dateIso, session)) {
            val (minD, maxD) = sessionDateBounds(session)
            return "Exam date ${candidate.dateIso} must fall within session $session ($minD to $maxD)."
        }
        val startMin = parseTimeMinutes(candidate.startTime) ?: return "Invalid start time (use HH:mm, e.g. 09:00)."
        val endMin = parseTimeMinutes(candidate.endTime) ?: return "Invalid end time (use HH:mm, e.g. 12:00)."
        if (endMin <= startMin) return "End time (${candidate.endTime}) must be after start time (${candidate.startTime})."

        val peers = existingEntries.filter { it.entryId != candidate.entryId }
        val sameSubject = peers.firstOrNull {
            it.className.equals(candidate.className, ignoreCase = true) &&
                it.subjectName.equals(candidate.subjectName, ignoreCase = true)
        }
        if (sameSubject != null) {
            return "${candidate.subjectName} is already scheduled for Class ${candidate.className} on ${sameSubject.dateIso}."
        }
        val clash = peers.firstOrNull {
            it.className.equals(candidate.className, ignoreCase = true) &&
                it.dateIso == candidate.dateIso &&
                timesOverlap(candidate.startTime, candidate.endTime, it.startTime, it.endTime)
        }
        if (clash != null) {
            return "Time clash for Class ${candidate.className} on ${candidate.dateIso}: ${clash.subjectName} (${clash.startTime}-${clash.endTime}) overlaps with ${candidate.startTime}-${candidate.endTime}."
        }
        return null
    }

    fun validateAllEntries(entries: List<ExamScheduleEntry>, session: String): List<String> {
        val errors = mutableListOf<String>()
        entries.forEachIndexed { idx, entry ->
            val err = validateEntry(entry, session, entries.take(idx))
            if (err != null) errors += err
        }
        return errors
    }

    /**
     * Auto-allocates students across [rooms] according to [mode]:
     * - [SeatingAllocationMode.BY_ROLL_NUMBER]: fills seats sequentially grouped by class, section, rollNo.
     * - [SeatingAllocationMode.MIXED_BY_CLASS]: interleaves students round-robin across classes so adjacent
     *   seats belong to different classes whenever multiple classes are included.
     */
    fun allocateSeating(
        schoolId: String,
        session: String,
        examId: String,
        studentsWithDocIds: List<Pair<String, StudentEntity>>,
        rooms: List<ExamRoomRecord>,
        mode: SeatingAllocationMode
    ): ExamSeatingRecord {
        val activeRooms = rooms.filter { it.active && it.capacity > 0 }.sortedBy { it.roomName.lowercase(Locale.US) }
        val activeStudents = studentsWithDocIds.filter { it.second.isActiveStatus }

        val orderedStudents: List<Pair<String, StudentEntity>> = when (mode) {
            SeatingAllocationMode.BY_ROLL_NUMBER -> activeStudents.sortedWith(
                compareBy<Pair<String, StudentEntity>>(
                    { it.second.className.lowercase(Locale.US) },
                    { it.second.section.lowercase(Locale.US) },
                    { it.second.rollNo },
                    { it.second.name.lowercase(Locale.US) }
                )
            )
            SeatingAllocationMode.MIXED_BY_CLASS -> {
                val buckets = activeStudents
                    .groupBy { it.second.className.trim().lowercase(Locale.US) }
                    .toSortedMap()
                    .values
                    .map { bucket ->
                        ArrayDeque(
                            bucket.sortedWith(
                                compareBy<Pair<String, StudentEntity>>(
                                    { it.second.section.lowercase(Locale.US) },
                                    { it.second.rollNo },
                                    { it.second.name.lowercase(Locale.US) }
                                )
                            )
                        )
                    }
                val interleaved = mutableListOf<Pair<String, StudentEntity>>()
                var progressed = true
                while (progressed) {
                    progressed = false
                    for (q in buckets) {
                        if (q.isNotEmpty()) {
                            interleaved += q.removeFirst()
                            progressed = true
                        }
                    }
                }
                interleaved
            }
        }

        val allocations = mutableListOf<ExamSeatAllocation>()
        var idx = 0
        for (room in activeRooms) {
            for (seatNo in 1..room.capacity) {
                if (idx >= orderedStudents.size) break
                val (docId, st) = orderedStudents[idx++]
                allocations += ExamSeatAllocation(
                    roomId = room.id,
                    roomName = room.roomName,
                    seatNumber = seatNo,
                    studentDocId = docId,
                    studentName = st.name,
                    studentAdmNo = st.admNo,
                    className = st.className,
                    section = st.section,
                    rollNo = st.rollNo
                )
            }
        }
        val unallocated = orderedStudents.drop(idx).map { it.first }
        return ExamSeatingRecord(
            id = ExamSeatingRecord.makeDocId(examId),
            schoolId = schoolId,
            session = session,
            examId = examId,
            allocationMode = mode,
            includedClasses = activeStudents.map { it.second.className }.distinct().sorted(),
            roomIds = activeRooms.map { it.id },
            allocations = allocations,
            unallocatedStudentIds = unallocated
        )
    }

    /**
     * Manual seating adjustment: moves a student to [targetRoom] at next free seat (or swaps if occupied).
     */
    fun moveStudentToRoom(
        seating: ExamSeatingRecord,
        studentDocId: String,
        targetRoom: ExamRoomRecord
    ): ExamSeatingRecord {
        val current = seating.allocations.firstOrNull { it.studentDocId == studentDocId } ?: return seating
        if (current.roomId == targetRoom.id) return seating

        val targetExisting = seating.allocations.filter { it.roomId == targetRoom.id }
        val usedSeats = targetExisting.map { it.seatNumber }.toSet()
        val freeSeat = (1..targetRoom.capacity).firstOrNull { it !in usedSeats }
            ?: (targetExisting.maxOfOrNull { it.seatNumber } ?: 0) + 1

        val updated = seating.allocations.map { alloc ->
            if (alloc.studentDocId == studentDocId) {
                alloc.copy(roomId = targetRoom.id, roomName = targetRoom.roomName, seatNumber = freeSeat)
            } else alloc
        }
        return seating.copy(allocations = updated)
    }

    fun uniqueExamSlots(exam: ExamScheduleRecord): List<ExamSlotKey> =
        exam.entries
            .map { ExamSlotKey(it.dateIso, it.startTime, it.endTime) }
            .distinct()
            .sortedWith(compareBy<ExamSlotKey>({ it.dateIso }, { it.startTime }, { it.endTime }))

    fun isTeacherOnApprovedLeave(
        teacherId: String,
        dateIso: String,
        leaves: List<TeacherLeaveRecord>
    ): Boolean = leaves.any { lv ->
        lv.teacherId == teacherId &&
            lv.status == LeaveStatus.APPROVED &&
            dateIso >= lv.startDate &&
            dateIso <= lv.endDate
    }

    /**
     * Auto-assigns invigilators to each `(slot, room)` for [exam]:
     * - Avoids inactive teachers and teachers not working at this school.
     * - Avoids teachers on Phase 6 APPROVED leave on `slot.dateIso`.
     * - Avoids teacher's weekly off-day on `slot.dateIso`.
     * - Avoids double-booking a teacher in overlapping exam slots on the same day.
     * - Balances total duty load (picks teacher with fewest total duties, then fewest duties on that day).
     */
    fun autoAssignInvigilators(
        exam: ExamScheduleRecord,
        rooms: List<ExamRoomRecord>,
        teachers: List<TeacherProfile>,
        leaves: List<TeacherLeaveRecord>,
        existingManualDuties: List<ExamInvigilatorDutyRecord> = emptyList()
    ): ExamDutyAssignmentResult {
        val slots = uniqueExamSlots(exam)
        val activeRooms = rooms.filter { it.active }.sortedBy { it.roomName.lowercase(Locale.US) }
        val eligibleTeachers = teachers.filter { t ->
            t.active && (t.schoolIds.isEmpty() || exam.schoolId in t.schoolIds)
        }.sortedBy { it.name.lowercase(Locale.US) }

        val result = mutableListOf<ExamInvigilatorDutyRecord>()
        val unfilled = mutableListOf<Pair<ExamSlotKey, ExamRoomRecord>>()
        val totalDutyCount = mutableMapOf<String, Int>()
        val dayDutyCount = mutableMapOf<Pair<String, String>, Int>()

        // Preserve manually pinned duties if valid
        val manualByKey = existingManualDuties
            .filter { it.examId == exam.id && it.assignedManually }
            .associateBy { "${it.dateIso}|${it.startTime}|${it.endTime}|${it.roomId}" }

        for (slot in slots) {
            val dow = Phase6DateUtils.dayOfWeekForDate(slot.dateIso)
            for (room in activeRooms) {
                val key = "${slot.dateIso}|${slot.startTime}|${slot.endTime}|${room.id}"
                val pinned = manualByKey[key]
                if (pinned != null && !isTeacherOnApprovedLeave(pinned.teacherId, slot.dateIso, leaves)) {
                    result += pinned
                    totalDutyCount[pinned.teacherId] = (totalDutyCount[pinned.teacherId] ?: 0) + 1
                    dayDutyCount[pinned.teacherId to slot.dateIso] =
                        (dayDutyCount[pinned.teacherId to slot.dateIso] ?: 0) + 1
                    continue
                }

                val candidate = eligibleTeachers
                    .filter { t ->
                        val onLeave = isTeacherOnApprovedLeave(t.id, slot.dateIso, leaves)
                        val isOffDay = dow != null && dow in t.weeklyOffDays
                        val busyInOverlap = result.any { d ->
                            d.teacherId == t.id &&
                                d.dateIso == slot.dateIso &&
                                timesOverlap(d.startTime, d.endTime, slot.startTime, slot.endTime)
                        }
                        !onLeave && !isOffDay && !busyInOverlap
                    }
                    .minWithOrNull(
                        compareBy<TeacherProfile>(
                            { totalDutyCount[it.id] ?: 0 },
                            { dayDutyCount[it.id to slot.dateIso] ?: 0 },
                            { it.name.lowercase(Locale.US) }
                        )
                    )

                if (candidate != null) {
                    val duty = ExamInvigilatorDutyRecord(
                        id = ExamInvigilatorDutyRecord.makeDocId(exam.id, slot.dateIso, slot.startTime, room.id),
                        schoolId = exam.schoolId,
                        session = exam.session,
                        examId = exam.id,
                        examTitle = exam.title,
                        dateIso = slot.dateIso,
                        startTime = slot.startTime,
                        endTime = slot.endTime,
                        roomId = room.id,
                        roomName = room.roomName,
                        teacherId = candidate.id,
                        teacherUid = candidate.linkedMemberUid,
                        teacherName = candidate.name,
                        assignedManually = false
                    )
                    result += duty
                    totalDutyCount[candidate.id] = (totalDutyCount[candidate.id] ?: 0) + 1
                    dayDutyCount[candidate.id to slot.dateIso] =
                        (dayDutyCount[candidate.id to slot.dateIso] ?: 0) + 1
                } else {
                    unfilled += slot to room
                }
            }
        }
        return ExamDutyAssignmentResult(duties = result, unfilledSlots = unfilled)
    }

    fun evaluateAdmitCardEligibility(
        exam: ExamScheduleRecord,
        studentsWithDocIds: List<Pair<String, StudentEntity>>,
        duesByStudentDocId: Map<String, Int>,
        seating: ExamSeatingRecord?
    ): List<ExamAdmitCardEligibility> {
        val seatByStudent = seating?.allocations?.associateBy { it.studentDocId }.orEmpty()
        val threshold = exam.withholdAdmitCardAboveDuesRs
        val overrideSet = exam.admitCardOverrideStudentIds.toSet()
        return studentsWithDocIds
            .filter { it.second.isActiveStatus }
            .sortedWith(compareBy<Pair<String, StudentEntity>>({ it.second.section }, { it.second.rollNo }, { it.second.name }))
            .map { (docId, st) ->
                val due = duesByStudentDocId[docId] ?: 0
                val withheld = threshold > 0 && due > threshold
                ExamAdmitCardEligibility(
                    student = st,
                    studentDocId = docId,
                    totalFeeDueRs = due,
                    isWithheldByDues = withheld,
                    isOverriddenByAdmin = docId in overrideSet,
                    seat = seatByStudent[docId]
                )
            }
    }
}

// ============================================================================
// PART C: STOCK (UNIFIED WITH PHASE 9 OPTIONAL FEE ITEMS)
// ============================================================================

enum class StockTransactionType(val wireValue: String, val englishLabel: String, val hindiLabel: String) {
    STOCK_IN("STOCK_IN", "Purchase (Stock In)", "खरीद (Stock In)"),
    ADJUSTMENT("ADJUSTMENT", "Manual Adjustment", "मैनुअल सुधार");

    companion object {
        fun fromRaw(raw: String?): StockTransactionType =
            if (raw.equals("ADJUSTMENT", ignoreCase = true)) ADJUSTMENT else STOCK_IN
    }
}

data class StockTransactionRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val itemId: String,
    val itemName: String,
    val txType: StockTransactionType,
    val dateIso: String, // YYYY-MM-DD
    val quantityDelta: Int, // > 0 for STOCK_IN; non-zero (+/-) for ADJUSTMENT
    val unitCostRs: Int = 0,
    val totalCostRs: Int = 0,
    val supplier: String = "",
    val reason: String = "", // mandatory for ADJUSTMENT
    val createdByUid: String = "",
    val createdByName: String = "",
    val createdAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "itemId" to itemId.trim().take(80),
        "itemName" to itemName.trim().take(100),
        "txType" to txType.wireValue,
        "dateIso" to dateIso.trim().take(10),
        "quantityDelta" to quantityDelta.toLong(),
        "unitCostRs" to unitCostRs.coerceAtLeast(0).toLong(),
        "totalCostRs" to totalCostRs.coerceAtLeast(0).toLong(),
        "supplier" to supplier.trim().take(120),
        "reason" to reason.trim().take(300),
        "createdByUid" to createdByUid,
        "createdByName" to createdByName.trim().take(120),
        "createdAtMs" to createdAtMs
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): StockTransactionRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val itemId = (data["itemId"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || itemId.isEmpty()) return null
            return StockTransactionRecord(
                id = docId,
                schoolId = schoolId,
                session = (data["session"] as? String)?.trim().orEmpty(),
                itemId = itemId,
                itemName = (data["itemName"] as? String)?.trim().orEmpty(),
                txType = StockTransactionType.fromRaw(data["txType"] as? String),
                dateIso = (data["dateIso"] as? String)?.trim().orEmpty(),
                quantityDelta = (data["quantityDelta"] as? Number)?.toInt() ?: 0,
                unitCostRs = (data["unitCostRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                totalCostRs = (data["totalCostRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                supplier = (data["supplier"] as? String).orEmpty(),
                reason = (data["reason"] as? String).orEmpty(),
                createdByUid = (data["createdByUid"] as? String).orEmpty(),
                createdByName = (data["createdByName"] as? String).orEmpty(),
                createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

enum class StockLedgerKind(val label: String) {
    PURCHASE_IN("Stock In"),
    RECEIPT_SALE_OUT("Fee Receipt Sale"),
    RECEIPT_CANCEL_RETURN("Receipt Cancelled (Returned)"),
    MANUAL_ADJUSTMENT("Manual Adjustment")
}

data class StockLedgerEvent(
    val id: String,
    val dateIso: String,
    val timestampMs: Long,
    val itemId: String,
    val itemName: String,
    val kind: StockLedgerKind,
    val quantityDelta: Int,
    val detail: String,
    val actorName: String
)

data class StockItemSummary(
    val item: OptionalFeeCatalogueItem,
    val openingStock: Int,
    val purchasedIn: Int,
    val soldOnReceipts: Int,
    val cancelledReturnCount: Int,
    val manualAdjustmentsNet: Int,
    val currentStock: Int,
    val isLowStock: Boolean,
    val totalPurchaseSpendRs: Int,
    val estimatedStockValueRs: Int
)

data class StockBelowZeroWarning(
    val itemId: String,
    val itemName: String,
    val availableStock: Int,
    val requestedQty: Int,
    val projectedStock: Int
)

object StockEngine {

    /**
     * Matches a Phase 9 [FeeReceiptLineItem] to an [OptionalFeeCatalogueItem] by `catalogueItemId`
     * (primary) or by normalized name fallback (for legacy receipts created before catalogueItemId).
     */
    private fun matchCatalogueItemId(
        line: FeeReceiptLineItem,
        catalogue: List<OptionalFeeCatalogueItem>
    ): String? {
        if (line.lineType != FeeLineItemType.OPTIONAL_ITEM) return null
        if (line.catalogueItemId.isNotBlank() && catalogue.any { it.id == line.catalogueItemId }) {
            return line.catalogueItemId
        }
        val cleanTitle = line.title.trim()
        return catalogue.firstOrNull {
            cleanTitle.equals(it.name, ignoreCase = true) ||
                cleanTitle.startsWith("${it.name} (", ignoreCase = true)
        }?.id
    }

    /**
     * Computes live stock balances for every item in the unified Phase 9 `optionalCatalogue`:
     * `currentStock = openingStock + purchasedIn + manualAdjustmentsNet - activeReceiptSoldQty`.
     * Cancelled Phase 9 receipts automatically return their sold quantity (they are excluded from active sales).
     */
    fun computeStockSummary(
        schoolId: String,
        session: String,
        catalogue: List<OptionalFeeCatalogueItem>,
        transactions: List<StockTransactionRecord>,
        feeReceipts: List<FeeReceiptRecord>
    ): List<StockItemSummary> {
        val scopedTx = transactions.filter {
            it.schoolId == schoolId && (it.session.isBlank() || it.session.equals(session, ignoreCase = true))
        }
        val scopedReceipts = feeReceipts.filter {
            it.schoolId == schoolId && (it.session.isBlank() || it.session.equals(session, ignoreCase = true))
        }

        val activeSoldByItem = mutableMapOf<String, Int>()
        val cancelledReturnedByItem = mutableMapOf<String, Int>()
        for (rcp in scopedReceipts) {
            for (line in rcp.lineItems) {
                val itemId = matchCatalogueItemId(line, catalogue) ?: continue
                val qty = line.quantity.coerceAtLeast(1)
                if (rcp.isCancelled) {
                    cancelledReturnedByItem[itemId] = (cancelledReturnedByItem[itemId] ?: 0) + qty
                } else {
                    activeSoldByItem[itemId] = (activeSoldByItem[itemId] ?: 0) + qty
                }
            }
        }

        return catalogue.filter { !it.hidden }.map { item ->
            val itemTx = scopedTx.filter { it.itemId == item.id }
            val purchasedIn = itemTx.filter { it.txType == StockTransactionType.STOCK_IN }.sumOf { it.quantityDelta.coerceAtLeast(0) }
            val purchaseSpend = itemTx.filter { it.txType == StockTransactionType.STOCK_IN }.sumOf { it.totalCostRs.coerceAtLeast(0) }
            val adjNet = itemTx.filter { it.txType == StockTransactionType.ADJUSTMENT }.sumOf { it.quantityDelta }
            val soldActive = activeSoldByItem[item.id] ?: 0
            val cancelledRet = cancelledReturnedByItem[item.id] ?: 0
            val current = item.openingStock + purchasedIn + adjNet - soldActive
            StockItemSummary(
                item = item,
                openingStock = item.openingStock,
                purchasedIn = purchasedIn,
                soldOnReceipts = soldActive,
                cancelledReturnCount = cancelledRet,
                manualAdjustmentsNet = adjNet,
                currentStock = current,
                isLowStock = current <= item.lowStockLevel,
                totalPurchaseSpendRs = purchaseSpend,
                estimatedStockValueRs = (current.coerceAtLeast(0)) * item.price.coerceAtLeast(0)
            )
        }
    }

    fun buildUnifiedLedger(
        schoolId: String,
        session: String,
        catalogue: List<OptionalFeeCatalogueItem>,
        transactions: List<StockTransactionRecord>,
        feeReceipts: List<FeeReceiptRecord>
    ): List<StockLedgerEvent> {
        val events = mutableListOf<StockLedgerEvent>()
        val itemNames = catalogue.associate { it.id to it.name }

        transactions
            .filter { it.schoolId == schoolId && (it.session.isBlank() || it.session.equals(session, ignoreCase = true)) }
            .forEach { tx ->
                events += StockLedgerEvent(
                    id = "tx_${tx.id}",
                    dateIso = tx.dateIso,
                    timestampMs = tx.createdAtMs,
                    itemId = tx.itemId,
                    itemName = itemNames[tx.itemId] ?: tx.itemName,
                    kind = if (tx.txType == StockTransactionType.STOCK_IN) StockLedgerKind.PURCHASE_IN else StockLedgerKind.MANUAL_ADJUSTMENT,
                    quantityDelta = tx.quantityDelta,
                    detail = if (tx.txType == StockTransactionType.STOCK_IN) {
                        buildString {
                            if (tx.supplier.isNotBlank()) append("Supplier: ${tx.supplier}")
                            if (tx.totalCostRs > 0) {
                                if (isNotEmpty()) append(" • ")
                                append("Cost: ₹${tx.totalCostRs}")
                            }
                            if (tx.reason.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(tx.reason)
                            }
                        }.ifBlank { "Purchase" }
                    } else {
                        tx.reason
                    },
                    actorName = tx.createdByName
                )
            }

        feeReceipts
            .filter { it.schoolId == schoolId && (it.session.isBlank() || it.session.equals(session, ignoreCase = true)) }
            .forEach { rcp ->
                rcp.lineItems.forEachIndexed { idx, line ->
                    val itemId = matchCatalogueItemId(line, catalogue) ?: return@forEachIndexed
                    val qty = line.quantity.coerceAtLeast(1)
                    val iName = itemNames[itemId] ?: line.title
                    events += StockLedgerEvent(
                        id = "sale_${rcp.id}_$idx",
                        dateIso = rcp.receiptDateIso,
                        timestampMs = rcp.issuedAtMs,
                        itemId = itemId,
                        itemName = iName,
                        kind = StockLedgerKind.RECEIPT_SALE_OUT,
                        quantityDelta = -qty,
                        detail = "Receipt ${rcp.receiptNumber} • ${rcp.studentName} (${rcp.className}-${rcp.section})",
                        actorName = rcp.receivedByName
                    )
                    if (rcp.isCancelled) {
                        events += StockLedgerEvent(
                            id = "ret_${rcp.id}_$idx",
                            dateIso = rcp.receiptDateIso,
                            timestampMs = if (rcp.cancelledAtMs > 0L) rcp.cancelledAtMs else rcp.issuedAtMs + 1L,
                            itemId = itemId,
                            itemName = iName,
                            kind = StockLedgerKind.RECEIPT_CANCEL_RETURN,
                            quantityDelta = +qty,
                            detail = "Cancelled ${rcp.receiptNumber} (${rcp.cancelReason})",
                            actorName = rcp.cancelledByName
                        )
                    }
                }
            }

        return events.sortedWith(compareByDescending<StockLedgerEvent> { it.dateIso }.thenByDescending { it.timestampMs })
    }

    /**
     * Checks if selling [requestedQtyByItemId] on a Phase 9 fee receipt would take any item below 0 stock.
     */
    fun checkBelowZeroSaleWarnings(
        requestedQtyByItemId: Map<String, Int>,
        summaries: List<StockItemSummary>
    ): List<StockBelowZeroWarning> {
        val byId = summaries.associateBy { it.item.id }
        return requestedQtyByItemId.mapNotNull { (itemId, reqQty) ->
            if (reqQty <= 0) return@mapNotNull null
            val sum = byId[itemId] ?: return@mapNotNull null
            val projected = sum.currentStock - reqQty
            if (projected < 0) {
                StockBelowZeroWarning(
                    itemId = itemId,
                    itemName = sum.item.name,
                    availableStock = sum.currentStock,
                    requestedQty = reqQty,
                    projectedStock = projected
                )
            } else null
        }
    }

    fun validateStockTransaction(
        txType: StockTransactionType,
        quantityDelta: Int,
        reason: String
    ): String? {
        if (txType == StockTransactionType.STOCK_IN && quantityDelta <= 0) {
            return "Purchase quantity must be greater than 0."
        }
        if (txType == StockTransactionType.ADJUSTMENT) {
            if (quantityDelta == 0) return "Adjustment quantity cannot be 0."
            if (reason.trim().isEmpty()) return "Please enter a reason for manual stock adjustment."
        }
        return null
    }
}

// ============================================================================
// PART D: TRANSPORT ROUTES, STOPS & STUDENT ASSIGNMENTS
// ============================================================================

data class TransportStop(
    val stopId: String,
    val stopName: String,
    val pickupTime: String = "",
    val monthlyFeeRs: Int = 0 // 0 = inherit route monthlyFeeRs
) {
    fun toMap(): Map<String, Any> = mapOf(
        "stopId" to stopId.trim().take(60),
        "stopName" to stopName.trim().take(80),
        "pickupTime" to pickupTime.trim().take(16),
        "monthlyFeeRs" to monthlyFeeRs.coerceIn(0, 500_000).toLong()
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): TransportStop? {
            if (raw == null) return null
            val name = (raw["stopName"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return null
            val id = (raw["stopId"] as? String)?.trim().orEmpty()
                .ifEmpty { "stop_${StableDocId.sanitizeSegment(name)}" }
            return TransportStop(
                stopId = id,
                stopName = name,
                pickupTime = (raw["pickupTime"] as? String)?.trim().orEmpty(),
                monthlyFeeRs = (raw["monthlyFeeRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            )
        }
    }
}

data class TransportRouteRecord(
    val id: String,
    val schoolId: String,
    val routeName: String,
    val vehicleNumber: String = "",
    val vehicleType: String = "Bus",
    val seatCapacity: Int = 30,
    val driverName: String = "",
    val driverPhone: String = "",
    val monthlyFeeRs: Int = 0,
    val stops: List<TransportStop> = emptyList(),
    val active: Boolean = true,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun effectiveFeeForStop(stopName: String?): Int {
        if (!stopName.isNullOrBlank()) {
            val stop = stops.firstOrNull { it.stopName.equals(stopName.trim(), ignoreCase = true) }
            if (stop != null && stop.monthlyFeeRs > 0) return stop.monthlyFeeRs
        }
        return monthlyFeeRs.coerceAtLeast(0)
    }

    fun toFirestoreMap(uid: String, editorName: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "routeName" to routeName.trim().take(80),
        "vehicleNumber" to vehicleNumber.trim().take(40),
        "vehicleType" to vehicleType.trim().ifBlank { "Bus" }.take(24),
        "seatCapacity" to seatCapacity.coerceIn(1, 200).toLong(),
        "driverName" to driverName.trim().take(100),
        "driverPhone" to driverPhone.trim().take(24),
        "monthlyFeeRs" to monthlyFeeRs.coerceIn(0, 500_000).toLong(),
        "stops" to stops.take(60).map { it.toMap() },
        "active" to active,
        "updatedByUid" to uid,
        "updatedByName" to editorName.trim().take(120),
        "clientEditedAtMs" to nowMs
    )

    companion object {
        fun makeDocId(schoolId: String, routeName: String): String =
            "troute_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(routeName)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): TransportRouteRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val routeName = (data["routeName"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || routeName.isEmpty()) return null
            return TransportRouteRecord(
                id = docId,
                schoolId = schoolId,
                routeName = routeName,
                vehicleNumber = (data["vehicleNumber"] as? String)?.trim().orEmpty(),
                vehicleType = (data["vehicleType"] as? String)?.trim().orEmpty().ifBlank { "Bus" },
                seatCapacity = (data["seatCapacity"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 30,
                driverName = (data["driverName"] as? String)?.trim().orEmpty(),
                driverPhone = (data["driverPhone"] as? String)?.trim().orEmpty(),
                monthlyFeeRs = (data["monthlyFeeRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                stops = (data["stops"] as? List<*>)?.mapNotNull { TransportStop.fromMap(it as? Map<*, *>) }.orEmpty(),
                active = (data["active"] as? Boolean) ?: true,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class TransportRouteSummary(
    val route: TransportRouteRecord,
    val assignedStudents: List<StudentEntity>,
    val seatsUsed: Int,
    val seatsRemaining: Int,
    val isOverCapacity: Boolean,
    val expectedMonthlyIncomeRs: Int
)

data class TransportSchoolSummary(
    val routeSummaries: List<TransportRouteSummary>,
    val unassignedTransportStudents: List<StudentEntity>,
    val totalTransportStudents: Int,
    val totalSeatsUsed: Int,
    val totalCapacity: Int,
    val totalExpectedMonthlyIncomeRs: Int
)

object TransportEngine {

    /**
     * Safely assigns [student] to [route] and optional [stopName]:
     * - Sets `usesTransport = true`, `transportRouteId = route.id`, `transportStopName`, and formatted `transportRouteName`.
     * - **Preserves existing `student.transportMonthlyAmount`** when [preserveExistingAmountIfPositive] is true
     *   and `student.transportMonthlyAmount > 0` (so existing Phase 9 per-student custom fees are never broken!).
     * - Otherwise feeds the stop's monthly fee (if > 0) or the route's default monthly fee.
     */
    fun assignStudentToRoute(
        student: StudentEntity,
        route: TransportRouteRecord,
        stopName: String = "",
        preserveExistingAmountIfPositive: Boolean = true,
        explicitMonthlyFeeOverride: Int? = null
    ): StudentEntity {
        val cleanStop = stopName.trim()
        val displayRoute = if (cleanStop.isNotEmpty()) "${route.routeName} - $cleanStop" else route.routeName
        val routeOrStopFee = route.effectiveFeeForStop(cleanStop)
        val finalAmount = when {
            explicitMonthlyFeeOverride != null -> explicitMonthlyFeeOverride.coerceAtLeast(0)
            preserveExistingAmountIfPositive && student.usesTransport && student.transportMonthlyAmount > 0 ->
                student.transportMonthlyAmount
            else -> routeOrStopFee
        }
        return student.copy(
            usesTransport = true,
            transportRouteId = route.id,
            transportStopName = cleanStop,
            transportRouteName = displayRoute,
            transportMonthlyAmount = finalAmount
        )
    }

    fun removeStudentFromRoute(student: StudentEntity): StudentEntity = student.copy(
        usesTransport = false,
        transportRouteId = "",
        transportStopName = "",
        transportRouteName = "",
        transportMonthlyAmount = 0
    )

    fun studentMatchesRoute(student: StudentEntity, route: TransportRouteRecord): Boolean {
        if (!student.isActiveStatus || !student.usesTransport) return false
        if (student.transportRouteId.isNotBlank()) return student.transportRouteId == route.id
        // Fallback match for Phase 9 students whose transportRouteName matches the route name
        return student.transportRouteName.equals(route.routeName, ignoreCase = true) ||
            student.transportRouteName.startsWith("${route.routeName} -", ignoreCase = true) ||
            student.transportRouteName.startsWith("${route.routeName} (", ignoreCase = true)
    }

    fun computeSchoolTransportSummary(
        routes: List<TransportRouteRecord>,
        students: List<StudentEntity>
    ): TransportSchoolSummary {
        val activeTransportStudents = students.filter { it.isActiveStatus && it.usesTransport }
        val matchedStudentIds = mutableSetOf<Long>()
        val summaries = routes.sortedBy { it.routeName.lowercase(Locale.US) }.map { route ->
            val assigned = activeTransportStudents
                .filter { studentMatchesRoute(it, route) }
                .sortedWith(
                    compareBy<StudentEntity>(
                        { st ->
                            val idx = route.stops.indexOfFirst { it.stopName.equals(st.transportStopName, ignoreCase = true) }
                            if (idx >= 0) idx else Int.MAX_VALUE
                        },
                        { it.className.lowercase(Locale.US) },
                        { it.section.lowercase(Locale.US) },
                        { it.rollNo },
                        { it.name.lowercase(Locale.US) }
                    )
                )
            matchedStudentIds += assigned.map { it.id }
            val seatsUsed = assigned.size
            val expectedIncome = assigned.sumOf { it.transportMonthlyAmount.coerceAtLeast(0) }
            TransportRouteSummary(
                route = route,
                assignedStudents = assigned,
                seatsUsed = seatsUsed,
                seatsRemaining = (route.seatCapacity - seatsUsed).coerceAtLeast(0),
                isOverCapacity = seatsUsed > route.seatCapacity,
                expectedMonthlyIncomeRs = expectedIncome
            )
        }
        val unassigned = activeTransportStudents.filter { it.id !in matchedStudentIds }
        return TransportSchoolSummary(
            routeSummaries = summaries,
            unassignedTransportStudents = unassigned,
            totalTransportStudents = activeTransportStudents.size,
            totalSeatsUsed = summaries.sumOf { it.seatsUsed },
            totalCapacity = summaries.filter { it.route.active }.sumOf { it.route.seatCapacity },
            totalExpectedMonthlyIncomeRs = activeTransportStudents.sumOf { it.transportMonthlyAmount.coerceAtLeast(0) }
        )
    }
}
