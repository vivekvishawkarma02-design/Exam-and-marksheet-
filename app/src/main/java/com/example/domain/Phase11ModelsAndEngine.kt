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
// PART D: TRANSPORT MODULE (REBUILT & ORGANISED — SINGLE SOURCE OF TRUTH)
// ============================================================================

enum class TransportTripType(
    val wireValue: String,
    val labelEn: String,
    val labelHi: String
) {
    BOTH_WAYS("BOTH_WAYS", "Both ways", "आना-जाना"),
    PICKUP_ONLY("PICKUP_ONLY", "Pickup only", "केवल पिकअप"),
    DROP_ONLY("DROP_ONLY", "Drop only", "केवल ड्रॉप");

    fun label(isHindi: Boolean): String = if (isHindi) labelHi else labelEn

    companion object {
        fun fromRaw(raw: String?): TransportTripType =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: BOTH_WAYS
    }
}

enum class VehicleOwnershipType(
    val wireValue: String,
    val labelEn: String,
    val labelHi: String
) {
    OWN("OWN", "School Own", "स्कूल का वाहन"),
    RENTED("RENTED", "Rented / Contractor", "किराए का वाहन");

    fun label(isHindi: Boolean): String = if (isHindi) labelHi else labelEn

    companion object {
        fun fromRaw(raw: String?): VehicleOwnershipType =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: OWN
    }
}

enum class VehicleDocumentType(
    val wireValue: String,
    val labelEn: String,
    val labelHi: String
) {
    INSURANCE("INSURANCE", "Insurance", "बीमा (Insurance)"),
    FITNESS("FITNESS", "Fitness", "फिटनेस (Fitness)"),
    PERMIT("PERMIT", "Permit", "परमिट (Permit)"),
    PUC("PUC", "Pollution (PUC)", "प्रदूषण (PUC)"),
    ROAD_TAX("ROAD_TAX", "Road Tax", "रोड टैक्स (Road Tax)"),
    OTHER("OTHER", "Other Document", "अन्य दस्तावेज़");

    fun label(isHindi: Boolean): String = if (isHindi) labelHi else labelEn

    companion object {
        fun fromRaw(raw: String?): VehicleDocumentType =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: OTHER
    }
}

enum class VehicleDocExpiryStatus(
    val wireValue: String,
    val labelEn: String,
    val labelHi: String
) {
    VALID("VALID", "Valid", "मान्य"),
    EXPIRING_SOON("EXPIRING_SOON", "Expiring (≤30d)", "जल्द समाप्त"),
    EXPIRED("EXPIRED", "Expired", "समाप्त");

    fun label(isHindi: Boolean): String = if (isHindi) labelHi else labelEn
}

data class TransportStop(
    val stopId: String,
    val stopName: String,
    val pickupTime: String = "",
    val monthlyFeeRs: Int = 0, // 0 = inherit route monthlyFeeRs (Both ways)
    val pickupOnlyFeeRs: Int = 0, // 0 = same as Both ways (or route pickup fee if stop fee unset)
    val dropOnlyFeeRs: Int = 0 // 0 = same as Both ways (or route drop fee if stop fee unset)
) {
    fun feeForTripType(tripType: TransportTripType, route: TransportRouteRecord): Int {
        return when (tripType) {
            TransportTripType.BOTH_WAYS -> {
                if (monthlyFeeRs > 0) monthlyFeeRs else route.monthlyFeeRs.coerceAtLeast(0)
            }
            TransportTripType.PICKUP_ONLY -> {
                when {
                    pickupOnlyFeeRs > 0 -> pickupOnlyFeeRs
                    monthlyFeeRs > 0 -> monthlyFeeRs
                    route.pickupOnlyFeeRs > 0 -> route.pickupOnlyFeeRs
                    else -> route.monthlyFeeRs.coerceAtLeast(0)
                }
            }
            TransportTripType.DROP_ONLY -> {
                when {
                    dropOnlyFeeRs > 0 -> dropOnlyFeeRs
                    monthlyFeeRs > 0 -> monthlyFeeRs
                    route.dropOnlyFeeRs > 0 -> route.dropOnlyFeeRs
                    else -> route.monthlyFeeRs.coerceAtLeast(0)
                }
            }
        }.coerceAtLeast(0)
    }

    fun toMap(): Map<String, Any> = mapOf(
        "stopId" to stopId.trim().take(60),
        "stopName" to stopName.trim().take(80),
        "pickupTime" to pickupTime.trim().take(16),
        "monthlyFeeRs" to monthlyFeeRs.coerceIn(0, 500_000).toLong(),
        "pickupOnlyFeeRs" to pickupOnlyFeeRs.coerceIn(0, 500_000).toLong(),
        "dropOnlyFeeRs" to dropOnlyFeeRs.coerceIn(0, 500_000).toLong()
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
                monthlyFeeRs = (raw["monthlyFeeRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                pickupOnlyFeeRs = (raw["pickupOnlyFeeRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                dropOnlyFeeRs = (raw["dropOnlyFeeRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
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
    val monthlyFeeRs: Int = 0, // Both ways default fee
    val stops: List<TransportStop> = emptyList(),
    val active: Boolean = true,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false,
    // Transport Rebuild fields (defaults preserve full backward compatibility)
    val pickupOnlyFeeRs: Int = 0, // 0 = same as monthlyFeeRs (Both ways)
    val dropOnlyFeeRs: Int = 0, // 0 = same as monthlyFeeRs (Both ways)
    val vehicleOwnership: VehicleOwnershipType = VehicleOwnershipType.OWN,
    val ownerContractorName: String = "",
    val ownerContractorPhone: String = "",
    val monthlyRentRs: Int = 0,
    val driverLicenceNo: String = "",
    val driverLicenceExpiryIso: String = "",
    val conductorName: String = "",
    val conductorPhone: String = ""
) {
    fun routeFeeForTripType(tripType: TransportTripType): Int = when (tripType) {
        TransportTripType.BOTH_WAYS -> monthlyFeeRs.coerceAtLeast(0)
        TransportTripType.PICKUP_ONLY -> if (pickupOnlyFeeRs > 0) pickupOnlyFeeRs else monthlyFeeRs.coerceAtLeast(0)
        TransportTripType.DROP_ONLY -> if (dropOnlyFeeRs > 0) dropOnlyFeeRs else monthlyFeeRs.coerceAtLeast(0)
    }

    fun effectiveFeeForStop(stopName: String?): Int =
        effectiveFeeFor(stopId = null, stopName = stopName, tripType = TransportTripType.BOTH_WAYS)

    fun effectiveFeeFor(
        stopId: String?,
        stopName: String?,
        tripType: TransportTripType = TransportTripType.BOTH_WAYS
    ): Int {
        val stop = findStop(stopId = stopId, stopName = stopName)
        if (stop != null) return stop.feeForTripType(tripType, this)
        return routeFeeForTripType(tripType)
    }

    fun findStop(stopId: String?, stopName: String?): TransportStop? {
        val cleanId = stopId?.trim().orEmpty()
        if (cleanId.isNotEmpty()) {
            stops.firstOrNull { it.stopId == cleanId }?.let { return it }
        }
        val cleanName = stopName?.trim().orEmpty()
        if (cleanName.isNotEmpty()) {
            stops.firstOrNull { it.stopName.equals(cleanName, ignoreCase = true) }?.let { return it }
        }
        return null
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
        "pickupOnlyFeeRs" to pickupOnlyFeeRs.coerceIn(0, 500_000).toLong(),
        "dropOnlyFeeRs" to dropOnlyFeeRs.coerceIn(0, 500_000).toLong(),
        "vehicleOwnership" to vehicleOwnership.wireValue,
        "ownerContractorName" to ownerContractorName.trim().take(100),
        "ownerContractorPhone" to ownerContractorPhone.trim().take(24),
        "monthlyRentRs" to monthlyRentRs.coerceIn(0, 500_000).toLong(),
        "driverLicenceNo" to driverLicenceNo.trim().take(48),
        "driverLicenceExpiryIso" to driverLicenceExpiryIso.trim().take(16),
        "conductorName" to conductorName.trim().take(100),
        "conductorPhone" to conductorPhone.trim().take(24),
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
                hasPendingWrites = hasPendingWrites,
                pickupOnlyFeeRs = (data["pickupOnlyFeeRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                dropOnlyFeeRs = (data["dropOnlyFeeRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                vehicleOwnership = VehicleOwnershipType.fromRaw(data["vehicleOwnership"] as? String),
                ownerContractorName = (data["ownerContractorName"] as? String)?.trim().orEmpty(),
                ownerContractorPhone = (data["ownerContractorPhone"] as? String)?.trim().orEmpty(),
                monthlyRentRs = (data["monthlyRentRs"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                driverLicenceNo = (data["driverLicenceNo"] as? String)?.trim().orEmpty(),
                driverLicenceExpiryIso = (data["driverLicenceExpiryIso"] as? String)?.trim().orEmpty(),
                conductorName = (data["conductorName"] as? String)?.trim().orEmpty(),
                conductorPhone = (data["conductorPhone"] as? String)?.trim().orEmpty()
            )
        }
    }
}

data class TransportVehicleDocumentRecord(
    val id: String,
    val schoolId: String,
    val routeId: String,
    val vehicleNumber: String,
    val docType: VehicleDocumentType,
    val documentNumber: String = "",
    val expiryDateIso: String, // YYYY-MM-DD
    val photoBase64: String = "",
    val note: String = "",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun statusForDate(todayIso: String): VehicleDocExpiryStatus =
        TransportEngine.evaluateVehicleDocumentStatus(expiryDateIso, todayIso)

    fun daysRemainingFrom(todayIso: String): Long =
        TransportEngine.daysBetweenIso(todayIso, expiryDateIso)

    fun toFirestoreMap(uid: String, editorName: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "routeId" to routeId.trim().take(128),
        "vehicleNumber" to vehicleNumber.trim().take(40),
        "docType" to docType.wireValue,
        "documentNumber" to documentNumber.trim().take(64),
        "expiryDateIso" to expiryDateIso.trim().take(16),
        "photoBase64" to photoBase64.take(260_000),
        "note" to note.trim().take(240),
        "updatedByUid" to uid,
        "updatedByName" to editorName.trim().take(120),
        "clientEditedAtMs" to nowMs
    )

    companion object {
        fun makeDocId(schoolId: String, routeId: String, docType: VehicleDocumentType): String =
            "tvdoc_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(routeId.ifBlank { "veh" })}_${docType.wireValue.lowercase(Locale.US)}_${System.currentTimeMillis().toString(36)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): TransportVehicleDocumentRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val expiryDateIso = (data["expiryDateIso"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || expiryDateIso.isEmpty()) return null
            return TransportVehicleDocumentRecord(
                id = docId,
                schoolId = schoolId,
                routeId = (data["routeId"] as? String)?.trim().orEmpty(),
                vehicleNumber = (data["vehicleNumber"] as? String)?.trim().orEmpty(),
                docType = VehicleDocumentType.fromRaw(data["docType"] as? String),
                documentNumber = (data["documentNumber"] as? String)?.trim().orEmpty(),
                expiryDateIso = expiryDateIso,
                photoBase64 = (data["photoBase64"] as? String).orEmpty(),
                note = (data["note"] as? String)?.trim().orEmpty(),
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class TransportSettingsConfig(
    val id: String = "",
    val schoolId: String,
    val expenseCategories: List<String> = DEFAULT_TRANSPORT_EXPENSE_CATEGORIES,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(uid: String, editorName: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "expenseCategories" to expenseCategories.map { it.trim().take(60) }.filter { it.isNotEmpty() }.distinct().take(30)
            .ifEmpty { DEFAULT_TRANSPORT_EXPENSE_CATEGORIES },
        "updatedByUid" to uid,
        "updatedByName" to editorName.trim().take(120),
        "clientEditedAtMs" to nowMs
    )

    companion object {
        val DEFAULT_TRANSPORT_EXPENSE_CATEGORIES: List<String> = listOf(
            "Diesel",
            "Repair",
            "Tyre",
            "Insurance",
            "Driver Pay",
            "Rent",
            "Other"
        )

        fun makeDocId(schoolId: String): String = "tset_${StableDocId.sanitizeSegment(schoolId)}"

        fun buildDefault(schoolId: String): TransportSettingsConfig =
            TransportSettingsConfig(id = makeDocId(schoolId), schoolId = schoolId)

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): TransportSettingsConfig? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty()) return null
            val cats = (data["expenseCategories"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
                .ifEmpty { DEFAULT_TRANSPORT_EXPENSE_CATEGORIES }
            return TransportSettingsConfig(
                id = docId,
                schoolId = schoolId,
                expenseCategories = cats,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

enum class TransportStudentAttentionFlag(val labelEn: String, val labelHi: String) {
    NO_ROUTE("No route assigned", "रूट तय नहीं"),
    NO_START_MONTH("Start month not set", "शुरुआती महीना तय नहीं"),
    ZERO_AMOUNT("Fee amount is ₹0", "फीस ₹0 है"),
    STOP_NEEDS_UPDATE("Stop needs update", "स्टॉप अपडेट करें"),
    OVER_CAPACITY("Route over capacity", "सीट क्षमता से अधिक");

    fun label(isHindi: Boolean): String = if (isHindi) labelHi else labelEn
}

data class TransportResolvedDisplay(
    val route: TransportRouteRecord?,
    val routeName: String,
    val stop: TransportStop?,
    val stopName: String,
    val stopNeedsUpdate: Boolean,
    val tripType: TransportTripType,
    val computedRouteOrStopFeeRs: Int,
    val effectiveMonthlyFeeRs: Int,
    val effectiveStartMonth: String,
    val effectiveEndMonth: String,
    val attentionFlags: List<TransportStudentAttentionFlag>
) {
    val needsAttention: Boolean get() = attentionFlags.isNotEmpty()
}

data class TransportMigrationItem(
    val student: StudentEntity,
    val assignedStartMonth: String,
    val currentRouteName: String
)

data class TransportFeeApplyPreviewRow(
    val student: StudentEntity,
    val stopDisplayName: String,
    val tripType: TransportTripType,
    val oldMonthlyAmountRs: Int,
    val newMonthlyAmountRs: Int
) {
    val hasChange: Boolean get() = oldMonthlyAmountRs != newMonthlyAmountRs
}

data class TransportDeleteCheckResult(
    val canDeleteDirectly: Boolean,
    val assignedCount: Int,
    val assignedStudents: List<StudentEntity>
)

data class TransportMonthDueStudentRow(
    val student: StudentEntity,
    val studentDocId: String,
    val routeId: String,
    val routeName: String,
    val stopName: String,
    val tripType: TransportTripType,
    val monthCode: String,
    val scheduledRs: Int,
    val paidRs: Int,
    val dueRs: Int
)

data class TransportDuesSummary(
    val monthCode: String,
    val rows: List<TransportMonthDueStudentRow>,
    val totalStudentsDue: Int,
    val totalExpectedRs: Int,
    val totalCollectedRs: Int,
    val totalDueRs: Int,
    val dueByRouteName: List<Pair<String, Int>>
)

data class TransportVehicleCostSummary(
    val routeId: String,
    val routeName: String,
    val vehicleNumber: String,
    val vehicleType: String,
    val expectedIncomeRs: Int,
    val collectedIncomeRs: Int,
    val duesRs: Int,
    val expenseRs: Int,
    val profitOrLossRs: Int,
    val expenseCount: Int
)

data class TransportMonthlyProfitLossReport(
    val schoolName: String,
    val session: String,
    val monthCode: String,
    val yearMonthIso: String,
    val vehicleRows: List<TransportVehicleCostSummary>,
    val unallocatedExpensesRs: Int,
    val totalExpectedIncomeRs: Int,
    val totalCollectedIncomeRs: Int,
    val totalDuesRs: Int,
    val totalExpenseRs: Int,
    val netProfitOrLossRs: Int
)

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
     * Finds the matching route for [student]:
     * Always resolves by `transportRouteId` first, using stored `transportRouteName` only as a legacy fallback.
     */
    fun resolveRoute(student: StudentEntity, routes: List<TransportRouteRecord>): TransportRouteRecord? {
        if (!student.usesTransport) return null
        if (student.transportRouteId.isNotBlank()) {
            routes.firstOrNull { it.id == student.transportRouteId }?.let { return it }
        }
        val legacy = student.transportRouteName.trim()
        if (legacy.isEmpty()) return null
        return routes.firstOrNull { route ->
            legacy.equals(route.routeName, ignoreCase = true) ||
                legacy.startsWith("${route.routeName} -", ignoreCase = true) ||
                legacy.startsWith("${route.routeName} (", ignoreCase = true)
        }
    }

    fun resolveStop(student: StudentEntity, route: TransportRouteRecord?): TransportStop? {
        if (route == null) return null
        return route.findStop(student.transportStopId, student.transportStopName)
    }

    fun isStopMissing(student: StudentEntity, route: TransportRouteRecord?): Boolean {
        if (!student.usesTransport || route == null) return false
        val hadStop = student.transportStopId.isNotBlank() || student.transportStopName.isNotBlank()
        if (!hadStop) return false
        return resolveStop(student, route) == null
    }

    fun resolveDisplay(
        student: StudentEntity,
        routes: List<TransportRouteRecord>,
        overCapacityRouteIds: Set<String> = emptySet(),
        firstChargedMonthFallback: String = ""
    ): TransportResolvedDisplay {
        val route = resolveRoute(student, routes)
        val stop = resolveStop(student, route)
        val stopMissing = isStopMissing(student, route)
        val tripType = TransportTripType.fromRaw(student.transportTripType)
        val liveRouteName = route?.routeName
            ?: student.transportRouteName.substringBefore(" -").trim().ifBlank { "No Route" }
        val liveStopName = when {
            stop != null -> stop.stopName
            stopMissing -> student.transportStopName.ifBlank { "Removed stop" }
            student.transportStopName.isNotBlank() -> student.transportStopName
            else -> ""
        }
        val computedRouteOrStop = route?.effectiveFeeFor(
            stopId = stop?.stopId ?: student.transportStopId,
            stopName = liveStopName,
            tripType = tripType
        ) ?: student.transportMonthlyAmount.coerceAtLeast(0)

        val effectiveStart = student.transportStartMonth.trim().uppercase(Locale.US)
            .ifBlank { firstChargedMonthFallback.trim().uppercase(Locale.US) }
        val effectiveEnd = student.transportEndMonth.trim().uppercase(Locale.US)

        val flags = buildList {
            if (student.usesTransport && route == null) {
                add(TransportStudentAttentionFlag.NO_ROUTE)
            }
            if (student.usesTransport && student.transportStartMonth.isBlank()) {
                add(TransportStudentAttentionFlag.NO_START_MONTH)
            }
            if (student.usesTransport && student.transportMonthlyAmount <= 0) {
                add(TransportStudentAttentionFlag.ZERO_AMOUNT)
            }
            if (stopMissing) {
                add(TransportStudentAttentionFlag.STOP_NEEDS_UPDATE)
            }
            if (route != null && route.id in overCapacityRouteIds) {
                add(TransportStudentAttentionFlag.OVER_CAPACITY)
            }
        }

        return TransportResolvedDisplay(
            route = route,
            routeName = liveRouteName,
            stop = stop,
            stopName = liveStopName,
            stopNeedsUpdate = stopMissing,
            tripType = tripType,
            computedRouteOrStopFeeRs = computedRouteOrStop,
            effectiveMonthlyFeeRs = student.transportMonthlyAmount.coerceAtLeast(0),
            effectiveStartMonth = effectiveStart,
            effectiveEndMonth = effectiveEnd,
            attentionFlags = flags
        )
    }

    /**
     * Safely assigns [student] to [route], [stopName], [tripType], and [startMonth]:
     * - Sets `usesTransport = true`, links by `transportRouteId` & `transportStopId`, and keeps fallback names.
     * - Preserves existing `student.transportMonthlyAmount` when [preserveExistingAmountIfPositive] is true
     *   and `student.transportMonthlyAmount > 0`, UNLESS `explicitMonthlyFeeOverride` is specified or
     *   [preserveExistingAmountIfPositive] is false.
     */
    fun assignStudentToRoute(
        student: StudentEntity,
        route: TransportRouteRecord,
        stopName: String = "",
        preserveExistingAmountIfPositive: Boolean = true,
        explicitMonthlyFeeOverride: Int? = null,
        stopId: String? = null,
        tripType: TransportTripType = TransportTripType.fromRaw(student.transportTripType),
        startMonth: String? = null,
        endMonth: String? = null,
        overrideRemark: String = ""
    ): StudentEntity {
        val resolvedStop = route.findStop(stopId = stopId, stopName = stopName)
        val cleanStopName = resolvedStop?.stopName ?: stopName.trim()
        val resolvedStopId = resolvedStop?.stopId ?: stopId?.trim().orEmpty()
        val displayRoute = if (cleanStopName.isNotEmpty()) "${route.routeName} - $cleanStopName" else route.routeName
        val computedFee = route.effectiveFeeFor(
            stopId = resolvedStopId,
            stopName = cleanStopName,
            tripType = tripType
        )
        val hasExplicitOverride = explicitMonthlyFeeOverride != null &&
            (explicitMonthlyFeeOverride != computedFee || overrideRemark.isNotBlank())
        val finalAmount = when {
            explicitMonthlyFeeOverride != null -> explicitMonthlyFeeOverride.coerceAtLeast(0)
            preserveExistingAmountIfPositive && student.usesTransport && student.transportMonthlyAmount > 0 ->
                student.transportMonthlyAmount
            else -> computedFee
        }
        val finalOverridden = when {
            explicitMonthlyFeeOverride != null -> hasExplicitOverride
            preserveExistingAmountIfPositive && student.usesTransport && student.transportFeeOverridden -> true
            preserveExistingAmountIfPositive && student.usesTransport && student.transportMonthlyAmount > 0 &&
                student.transportMonthlyAmount != computedFee -> student.transportFeeOverridden
            else -> false
        }
        val finalRemark = when {
            explicitMonthlyFeeOverride != null -> if (hasExplicitOverride) overrideRemark.trim() else ""
            finalOverridden -> student.transportOverrideRemark
            else -> ""
        }
        val finalStart = startMonth?.trim()?.uppercase(Locale.US)
            ?: student.transportStartMonth.trim().uppercase(Locale.US)
        val finalEnd = endMonth?.trim()?.uppercase(Locale.US)
            ?: student.transportEndMonth.trim().uppercase(Locale.US)

        return student.copy(
            usesTransport = true,
            transportRouteId = route.id,
            transportStopId = resolvedStopId,
            transportStopName = cleanStopName,
            transportRouteName = displayRoute,
            transportTripType = tripType.wireValue,
            transportMonthlyAmount = finalAmount,
            transportStartMonth = finalStart,
            transportEndMonth = finalEnd,
            transportFeeOverridden = finalOverridden,
            transportOverrideRemark = finalRemark
        )
    }

    fun overrideStudentFee(
        student: StudentEntity,
        overrideAmountRs: Int,
        remark: String
    ): StudentEntity {
        val cleanRemark = remark.trim()
        require(cleanRemark.isNotEmpty()) { "Override remark is mandatory." }
        return student.copy(
            transportMonthlyAmount = overrideAmountRs.coerceAtLeast(0),
            transportFeeOverridden = true,
            transportOverrideRemark = cleanRemark,
            transportPriorMonthlyAmount = 0,
            transportFeeEffectiveFromMonth = ""
        )
    }

    fun resetStudentFeeToRoute(
        student: StudentEntity,
        routes: List<TransportRouteRecord>
    ): StudentEntity {
        val route = resolveRoute(student, routes) ?: return student.copy(
            transportFeeOverridden = false,
            transportOverrideRemark = ""
        )
        val trip = TransportTripType.fromRaw(student.transportTripType)
        val routeFee = route.effectiveFeeFor(
            stopId = student.transportStopId,
            stopName = student.transportStopName,
            tripType = trip
        )
        return student.copy(
            transportMonthlyAmount = routeFee,
            transportFeeOverridden = false,
            transportOverrideRemark = "",
            transportPriorMonthlyAmount = 0,
            transportFeeEffectiveFromMonth = ""
        )
    }

    fun changeStudentTripType(
        student: StudentEntity,
        tripType: TransportTripType,
        routes: List<TransportRouteRecord>
    ): StudentEntity {
        val route = resolveRoute(student, routes)
        val nextAmount = if (student.transportFeeOverridden || route == null) {
            student.transportMonthlyAmount
        } else {
            route.effectiveFeeFor(
                stopId = student.transportStopId,
                stopName = student.transportStopName,
                tripType = tripType
            )
        }
        return student.copy(
            transportTripType = tripType.wireValue,
            transportMonthlyAmount = nextAmount
        )
    }

    fun removeStudentFromRoute(student: StudentEntity): StudentEntity = student.copy(
        usesTransport = false,
        transportRouteId = "",
        transportStopId = "",
        transportStopName = "",
        transportRouteName = "",
        transportMonthlyAmount = 0,
        transportTripType = TransportTripType.BOTH_WAYS.wireValue,
        transportStartMonth = "",
        transportEndMonth = "",
        transportFeeOverridden = false,
        transportOverrideRemark = "",
        transportPriorMonthlyAmount = 0,
        transportFeeEffectiveFromMonth = ""
    )

    fun firstChargedTransportMonth(structure: FeeStructureConfig): String =
        structure.chargedTransportMonths
            .firstOrNull { m -> structure.monthOrder.any { it.equals(m, ignoreCase = true) } }
            ?: structure.monthOrder.firstOrNull()
            ?: "APR"

    /**
     * Migrates legacy transport students whose `transportStartMonth` is blank:
     * assigns them the first charged transport month of the session so existing dues & paid cells remain identical.
     * Returns only the students that need migration along with their updated entities for review.
     */
    fun findAndPrepareLegacyMigration(
        students: List<StudentEntity>,
        routes: List<TransportRouteRecord>,
        structure: FeeStructureConfig
    ): List<TransportMigrationItem> {
        val firstMonth = firstChargedTransportMonth(structure).uppercase(Locale.US)
        return students
            .filter { it.isActiveStatus && it.usesTransport && it.transportStartMonth.isBlank() }
            .map { st ->
                val resolvedRoute = resolveRoute(st, routes)
                val migrated = st.copy(transportStartMonth = firstMonth)
                TransportMigrationItem(
                    student = migrated,
                    assignedStartMonth = firstMonth,
                    currentRouteName = resolvedRoute?.routeName
                        ?: st.transportRouteName.ifBlank { "Unassigned Route" }
                )
            }
    }

    /**
     * Builds preview rows for "Apply updated route/stop fees to students":
     * - Excludes individually overridden students (`student.transportFeeOverridden == true`).
     * - Shows student, stop, trip type, old monthly rate, and new monthly rate under [updatedRoute].
     */
    fun buildApplyRouteFeePreview(
        students: List<StudentEntity>,
        updatedRoute: TransportRouteRecord
    ): Pair<List<TransportFeeApplyPreviewRow>, Int> {
        val routeStudents = students.filter { studentMatchesRoute(it, updatedRoute) }
        val excludedOverriddenCount = routeStudents.count { it.transportFeeOverridden }
        val previewRows = routeStudents
            .filter { !it.transportFeeOverridden }
            .map { st ->
                val tripType = TransportTripType.fromRaw(st.transportTripType)
                val resolvedStop = resolveStop(st, updatedRoute)
                val newAmt = updatedRoute.effectiveFeeFor(
                    stopId = resolvedStop?.stopId ?: st.transportStopId,
                    stopName = resolvedStop?.stopName ?: st.transportStopName,
                    tripType = tripType
                )
                TransportFeeApplyPreviewRow(
                    student = st,
                    stopDisplayName = resolvedStop?.stopName ?: st.transportStopName.ifBlank { "-" },
                    tripType = tripType,
                    oldMonthlyAmountRs = st.transportMonthlyAmount,
                    newMonthlyAmountRs = newAmt
                )
            }
        return previewRows to excludedOverriddenCount
    }

    /**
     * Applies [previewRows] starting from [effectiveFromMonth], preserving [student.transportMonthlyAmount]
     * in `transportPriorMonthlyAmount` for months strictly prior to [effectiveFromMonth] so already-paid
     * or earlier months never change silently.
     */
    fun buildUpdatedStudentsForFeeChange(
        previewRows: List<TransportFeeApplyPreviewRow>,
        updatedRoute: TransportRouteRecord,
        effectiveFromMonth: String,
        orderedSessionMonths: List<String>
    ): List<StudentEntity> {
        val cleanEff = effectiveFromMonth.trim().uppercase(Locale.US)
        val firstMonth = orderedSessionMonths.firstOrNull()?.uppercase(Locale.US) ?: "APR"
        val isFromSessionStart = cleanEff.isBlank() || cleanEff.equals(firstMonth, ignoreCase = true)
        return previewRows
            .filter { it.hasChange }
            .map { row ->
                val st = row.student
                val resolvedStop = resolveStop(st, updatedRoute)
                val cleanStopName = resolvedStop?.stopName ?: st.transportStopName
                val routeDisp = if (cleanStopName.isNotBlank()) "${updatedRoute.routeName} - $cleanStopName" else updatedRoute.routeName
                if (isFromSessionStart) {
                    st.copy(
                        transportRouteName = routeDisp,
                        transportMonthlyAmount = row.newMonthlyAmountRs,
                        transportPriorMonthlyAmount = 0,
                        transportFeeEffectiveFromMonth = ""
                    )
                } else {
                    st.copy(
                        transportRouteName = routeDisp,
                        transportMonthlyAmount = row.newMonthlyAmountRs,
                        transportPriorMonthlyAmount = row.oldMonthlyAmountRs,
                        transportFeeEffectiveFromMonth = cleanEff
                    )
                }
            }
    }

    fun checkRouteDeletion(
        route: TransportRouteRecord,
        students: List<StudentEntity>
    ): TransportDeleteCheckResult {
        val assigned = students.filter { studentMatchesRoute(it, route) }
        return TransportDeleteCheckResult(
            canDeleteDirectly = assigned.isEmpty(),
            assignedCount = assigned.size,
            assignedStudents = assigned
        )
    }

    fun willExceedCapacity(
        route: TransportRouteRecord,
        currentStudents: List<StudentEntity>,
        additionalStudentIds: Set<Long>
    ): Boolean {
        val existingOnRoute = currentStudents.count { studentMatchesRoute(it, route) }
        val alreadyOnRoute = currentStudents.count { it.id in additionalStudentIds && studentMatchesRoute(it, route) }
        val projected = existingOnRoute + (additionalStudentIds.size - alreadyOnRoute)
        return projected > route.seatCapacity
    }

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
                            val idx = route.stops.indexOfFirst {
                                (st.transportStopId.isNotBlank() && it.stopId == st.transportStopId) ||
                                    it.stopName.equals(st.transportStopName, ignoreCase = true)
                            }
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

    private fun parseIsoEpochDays(iso: String): Long? {
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(iso.trim()) ?: return null
        val y0 = m.groupValues[1].toInt()
        val m0 = m.groupValues[2].toInt()
        val d0 = m.groupValues[3].toInt()
        val y = if (m0 <= 2) y0 - 1 else y0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m0 + (if (m0 > 2) -3 else 9)) + 2) / 5 + d0 - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return (era * 146097 + doe - 719468).toLong()
    }

    fun daysBetweenIso(fromIso: String, toIso: String): Long {
        val fromDays = parseIsoEpochDays(fromIso) ?: return 9999L
        val toDays = parseIsoEpochDays(toIso) ?: return 9999L
        return toDays - fromDays
    }

    fun evaluateVehicleDocumentStatus(expiryDateIso: String, todayIso: String): VehicleDocExpiryStatus {
        val diff = daysBetweenIso(todayIso, expiryDateIso)
        return when {
            diff < 0 -> VehicleDocExpiryStatus.EXPIRED
            diff <= 30 -> VehicleDocExpiryStatus.EXPIRING_SOON
            else -> VehicleDocExpiryStatus.VALID
        }
    }

    fun computeTransportDuesForMonth(
        monthCode: String,
        studentsWithDocIds: List<Pair<String, StudentEntity>>,
        routes: List<TransportRouteRecord>,
        structure: FeeStructureConfig,
        overridesByStudentDocId: Map<String, StudentFeeOverride>,
        receipts: List<FeeReceiptRecord>,
        routeIdFilter: String? = null,
        classNameFilter: String? = null
    ): TransportDuesSummary {
        val cleanMonth = monthCode.trim().uppercase(Locale.US)
        val receiptsByStudent = receipts.groupBy { it.studentId }
        val rows = mutableListOf<TransportMonthDueStudentRow>()
        var expectedSum = 0
        var collectedSum = 0
        var dueSum = 0
        val dueByRoute = linkedMapOf<String, Int>()

        for ((stuDocId, student) in studentsWithDocIds) {
            if (!student.isActiveStatus || !student.usesTransport) continue
            if (!student.session.equals(structure.session, ignoreCase = true)) continue
            if (!classNameFilter.isNullOrBlank() && !student.className.equals(classNameFilter, ignoreCase = true)) continue

            val route = resolveRoute(student, routes)
            if (!routeIdFilter.isNullOrBlank()) {
                if (routeIdFilter == "__NO_ROUTE__") {
                    if (route != null) continue
                } else if (route?.id != routeIdFilter) {
                    continue
                }
            }

            val acct = FeeCollectionEngine.computeStudentFeeAccount(
                structure = structure,
                student = student,
                studentDocId = stuDocId,
                override = overridesByStudentDocId[stuDocId],
                receiptsForStudent = receiptsByStudent[stuDocId].orEmpty(),
                routes = routes
            )
            val tCell = acct.monthRows
                .firstOrNull { it.monthCode.equals(cleanMonth, ignoreCase = true) }
                ?.transportCell
                ?: continue
            if (tCell.state == FeeCellState.NOT_APPLICABLE && tCell.scheduledAmount == 0) continue

            expectedSum += tCell.scheduledAmount
            collectedSum += (tCell.paidAmount + tCell.concessionAllocated).coerceAtMost(tCell.scheduledAmount)
            if (tCell.remainingDue > 0) {
                dueSum += tCell.remainingDue
                val dispRoute = route?.routeName ?: student.transportRouteName.ifBlank { "No Route" }
                dueByRoute[dispRoute] = (dueByRoute[dispRoute] ?: 0) + tCell.remainingDue
                val resolvedStop = resolveStop(student, route)
                rows += TransportMonthDueStudentRow(
                    student = student,
                    studentDocId = stuDocId,
                    routeId = route?.id.orEmpty(),
                    routeName = dispRoute,
                    stopName = resolvedStop?.stopName ?: student.transportStopName.ifBlank { "-" },
                    tripType = TransportTripType.fromRaw(student.transportTripType),
                    monthCode = cleanMonth,
                    scheduledRs = tCell.scheduledAmount,
                    paidRs = tCell.paidAmount + tCell.concessionAllocated,
                    dueRs = tCell.remainingDue
                )
            }
        }

        return TransportDuesSummary(
            monthCode = cleanMonth,
            rows = rows.sortedWith(compareBy({ it.routeName }, { it.student.className }, { it.student.rollNo }, { it.student.name })),
            totalStudentsDue = rows.size,
            totalExpectedRs = expectedSum,
            totalCollectedRs = collectedSum,
            totalDueRs = dueSum,
            dueByRouteName = dueByRoute.entries.map { it.key to it.value }.sortedByDescending { it.second }
        )
    }

    /**
     * Maps session month code ("APR".."MAR") + session ("2026-2027") -> "YYYY-MM".
     */
    fun yearMonthForSessionMonth(session: String, monthCode: String): String {
        val mUpper = monthCode.trim().uppercase(Locale.US)
        val startYear = session.trim().take(4).toIntOrNull() ?: 2026
        val (calYear, mm) = when (mUpper) {
            "APR" -> startYear to "04"
            "MAY" -> startYear to "05"
            "JUN" -> startYear to "06"
            "JUL" -> startYear to "07"
            "AUG" -> startYear to "08"
            "SEP" -> startYear to "09"
            "OCT" -> startYear to "10"
            "NOV" -> startYear to "11"
            "DEC" -> startYear to "12"
            "JAN" -> (startYear + 1) to "01"
            "FEB" -> (startYear + 1) to "02"
            "MAR" -> (startYear + 1) to "03"
            else -> startYear to "04"
        }
        return "$calYear-$mm"
    }

    /**
     * Computes monthly Income vs Expense & Profit/Loss per route/vehicle and for the school.
     * Uses the unified `cashBookEntries` tagged with Transport (zero duplicate financial records).
     */
    fun computeMonthlyProfitLossReport(
        schoolId: String,
        schoolName: String,
        session: String,
        monthCode: String,
        yearMonthIso: String,
        routes: List<TransportRouteRecord>,
        studentsWithDocIds: List<Pair<String, StudentEntity>>,
        structure: FeeStructureConfig,
        overridesByStudentDocId: Map<String, StudentFeeOverride>,
        receipts: List<FeeReceiptRecord>,
        cashBookEntries: List<CashBookEntryRecord>
    ): TransportMonthlyProfitLossReport {
        val cleanMonth = monthCode.trim().uppercase(Locale.US)
        val schoolRoutes = routes.filter { it.schoolId == schoolId }.sortedBy { it.routeName.lowercase(Locale.US) }
        val schoolTransportExpenses = cashBookEntries.filter { entry ->
            entry.schoolId == schoolId &&
                !entry.isCancelled &&
                entry.isExpense &&
                entry.isTransportExpense &&
                entry.dateIso.startsWith(yearMonthIso)
        }

        val receiptsByStudent = receipts.filter {
            it.schoolId == schoolId && it.session.equals(session, ignoreCase = true) && !it.isCancelled
        }.groupBy { it.studentId }

        val routeExpected = mutableMapOf<String, Int>()
        val routeCollected = mutableMapOf<String, Int>()
        val routeDues = mutableMapOf<String, Int>()

        for ((stuDocId, student) in studentsWithDocIds) {
            if (!student.isActiveStatus) continue
            if (!student.session.equals(session, ignoreCase = true)) continue
            val route = resolveRoute(student, schoolRoutes) ?: continue
            val acct = FeeCollectionEngine.computeStudentFeeAccount(
                structure = structure,
                student = student,
                studentDocId = stuDocId,
                override = overridesByStudentDocId[stuDocId],
                receiptsForStudent = receiptsByStudent[stuDocId].orEmpty(),
                routes = schoolRoutes
            )
            val cell = acct.monthRows.firstOrNull { it.monthCode.equals(cleanMonth, ignoreCase = true) }?.transportCell
                ?: continue
            routeExpected[route.id] = (routeExpected[route.id] ?: 0) + cell.scheduledAmount
            routeCollected[route.id] = (routeCollected[route.id] ?: 0) + (cell.paidAmount + cell.concessionAllocated).coerceAtMost(cell.scheduledAmount)
            routeDues[route.id] = (routeDues[route.id] ?: 0) + cell.remainingDue
        }

        val matchedExpenseIds = mutableSetOf<String>()
        val vehicleRows = schoolRoutes.map { rt ->
            val matchingExp = schoolTransportExpenses.filter { e ->
                (e.transportRouteId.isNotBlank() && e.transportRouteId == rt.id) ||
                    (rt.vehicleNumber.isNotBlank() && e.vehicleNumber.equals(rt.vehicleNumber, ignoreCase = true))
            }
            matchedExpenseIds += matchingExp.map { it.id }
            val expSum = matchingExp.sumOf { it.amount }
            val expIncome = routeExpected[rt.id] ?: 0
            val colIncome = routeCollected[rt.id] ?: 0
            val dues = routeDues[rt.id] ?: 0
            TransportVehicleCostSummary(
                routeId = rt.id,
                routeName = rt.routeName,
                vehicleNumber = rt.vehicleNumber.ifBlank { "-" },
                vehicleType = rt.vehicleType,
                expectedIncomeRs = expIncome,
                collectedIncomeRs = colIncome,
                duesRs = dues,
                expenseRs = expSum,
                profitOrLossRs = colIncome - expSum,
                expenseCount = matchingExp.size
            )
        }

        val unallocatedExp = schoolTransportExpenses.filter { it.id !in matchedExpenseIds }.sumOf { it.amount }
        val totalExpected = vehicleRows.sumOf { it.expectedIncomeRs }
        val totalCollected = vehicleRows.sumOf { it.collectedIncomeRs }
        val totalDues = vehicleRows.sumOf { it.duesRs }
        val totalExp = vehicleRows.sumOf { it.expenseRs } + unallocatedExp
        return TransportMonthlyProfitLossReport(
            schoolName = schoolName,
            session = session,
            monthCode = cleanMonth,
            yearMonthIso = yearMonthIso,
            vehicleRows = vehicleRows,
            unallocatedExpensesRs = unallocatedExp,
            totalExpectedIncomeRs = totalExpected,
            totalCollectedIncomeRs = totalCollected,
            totalDuesRs = totalDues,
            totalExpenseRs = totalExp,
            netProfitOrLossRs = totalCollected - totalExp
        )
    }

    fun buildMonthlySummaryCsv(report: TransportMonthlyProfitLossReport): String {
        fun esc(v: String): String {
            val clean = v.replace("\"", "\"\"")
            return if (clean.contains(',') || clean.contains('"') || clean.contains('\n')) "\"$clean\"" else clean
        }
        val sb = StringBuilder()
        sb.appendLine("School,Session,Month,Route,Vehicle No,Vehicle Type,Expected Income (Rs),Collected (Rs),Dues (Rs),Expenses (Rs),Profit/Loss (Rs)")
        for (row in report.vehicleRows) {
            sb.appendLine(
                listOf(
                    esc(report.schoolName),
                    esc(report.session),
                    esc("${report.monthCode} (${report.yearMonthIso})"),
                    esc(row.routeName),
                    esc(row.vehicleNumber),
                    esc(row.vehicleType),
                    row.expectedIncomeRs.toString(),
                    row.collectedIncomeRs.toString(),
                    row.duesRs.toString(),
                    row.expenseRs.toString(),
                    row.profitOrLossRs.toString()
                ).joinToString(",")
            )
        }
        if (report.unallocatedExpensesRs > 0) {
            sb.appendLine("${esc(report.schoolName)},${esc(report.session)},${esc(report.monthCode)},Unallocated / General Vehicle,-,-,0,0,0,${report.unallocatedExpensesRs},-${report.unallocatedExpensesRs}")
        }
        sb.appendLine("${esc(report.schoolName)},${esc(report.session)},${esc(report.monthCode)},TOTAL,-,-,${report.totalExpectedIncomeRs},${report.totalCollectedIncomeRs},${report.totalDuesRs},${report.totalExpenseRs},${report.netProfitOrLossRs}")
        return sb.toString()
    }
}
