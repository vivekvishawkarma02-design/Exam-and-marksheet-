package com.example.domain

import com.example.data.StudentEntity
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// =============================================================================
// PHASE 18: CLASS DIARY AND BIRTHDAYS — PURE KOTLIN DOMAIN MODELS & ENGINE
// =============================================================================

data class ClassDiaryRevision(
    val revisionNo: Int = 1,
    val previousClasswork: String = "",
    val previousHomework: String = "",
    val previousNote: String = "",
    val hadPhoto: Boolean = false,
    val editedByUid: String = "",
    val editedByName: String = "",
    val editedAtMs: Long = 0L,
    val action: String = "EDITED"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "revisionNo" to revisionNo,
        "previousClasswork" to previousClasswork.take(1500),
        "previousHomework" to previousHomework.take(1500),
        "previousNote" to previousNote.take(800),
        "hadPhoto" to hadPhoto,
        "editedByUid" to editedByUid.take(128),
        "editedByName" to editedByName.take(120),
        "editedAtMs" to editedAtMs,
        "action" to action.take(24)
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): ClassDiaryRevision? {
            if (raw == null) return null
            return ClassDiaryRevision(
                revisionNo = ((raw["revisionNo"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
                previousClasswork = (raw["previousClasswork"] as? String).orEmpty(),
                previousHomework = (raw["previousHomework"] as? String).orEmpty(),
                previousNote = (raw["previousNote"] as? String).orEmpty(),
                hadPhoto = (raw["hadPhoto"] as? Boolean) ?: false,
                editedByUid = (raw["editedByUid"] as? String).orEmpty(),
                editedByName = (raw["editedByName"] as? String).orEmpty(),
                editedAtMs = (raw["editedAtMs"] as? Number)?.toLong() ?: 0L,
                action = (raw["action"] as? String).orEmpty().ifBlank { "EDITED" }
            )
        }
    }
}

data class ClassDiaryEntry(
    val id: String,
    val schoolId: String,
    val session: String,
    val dateIso: String,
    val className: String,
    val section: String,
    val subjectName: String,
    val classwork: String,
    val homework: String,
    val note: String = "",
    val photoBase64: String = "",
    val authorUid: String,
    val authorName: String,
    val lastEditedByUid: String = authorUid,
    val lastEditedByName: String = authorName,
    val editHistory: List<ClassDiaryRevision> = emptyList(),
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val isPendingSync: Boolean = false
) {
    val hasPhoto: Boolean get() = photoBase64.isNotBlank()
    val classSectionLabel: String get() = "${className.trim()}-${section.trim().ifBlank { "A" }}"

    fun toFirestoreMap(
        createdAtTimestamp: Any,
        updatedAtTimestamp: Any
    ): Map<String, Any> {
        val cleanEditorUid = lastEditedByUid.trim().ifBlank { authorUid.trim() }
        val cleanEditorName = lastEditedByName.trim().ifBlank { authorName.trim().ifBlank { "Teacher" } }.take(120)
        return mapOf(
            "schoolId" to schoolId.trim(),
            "session" to session.trim(),
            "dateIso" to dateIso.trim(),
            "className" to className.trim(),
            "section" to section.trim().uppercase(Locale.US).ifBlank { "A" },
            "subjectName" to subjectName.trim(),
            "classwork" to classwork.trim().take(1500),
            "homework" to homework.trim().take(1500),
            "note" to note.trim().take(800),
            "photoBase64" to photoBase64.take(260_000),
            "authorUid" to authorUid.trim(),
            "authorName" to authorName.trim().ifBlank { "Teacher" }.take(120),
            "lastEditedByUid" to cleanEditorUid,
            "lastEditedByName" to cleanEditorName,
            "updatedByUid" to cleanEditorUid,
            "updatedByName" to cleanEditorName,
            "editHistory" to editHistory.takeLast(25).map { it.toMap() },
            "createdAtMs" to createdAtMs,
            "updatedAtMs" to updatedAtMs,
            "createdAt" to createdAtTimestamp,
            "updatedAt" to updatedAtTimestamp
        )
    }

    companion object {
        fun buildId(
            schoolId: String,
            session: String,
            dateIso: String,
            className: String,
            section: String,
            subjectName: String
        ): String = Phase18DiaryAndBirthdayEngine.makeDiaryEntryId(
            schoolId = schoolId,
            session = session,
            dateIso = dateIso,
            className = className,
            section = section,
            subjectName = subjectName
        )

        fun fromMap(id: String, raw: Map<*, *>?, isPendingSync: Boolean = false): ClassDiaryEntry? {
            if (raw == null) return null
            val schoolId = (raw["schoolId"] as? String).orEmpty().trim()
            val dateIso = (raw["dateIso"] as? String).orEmpty().trim()
            val className = (raw["className"] as? String).orEmpty().trim()
            val subjectName = (raw["subjectName"] as? String).orEmpty().trim()
            if (schoolId.isBlank() || dateIso.isBlank() || className.isBlank() || subjectName.isBlank()) return null
            val revisions = (raw["editHistory"] as? List<*>)
                ?.mapNotNull { ClassDiaryRevision.fromMap(it as? Map<*, *>) }
                .orEmpty()
            val authorUid = (raw["authorUid"] as? String).orEmpty().trim()
            val authorName = (raw["authorName"] as? String).orEmpty().trim().ifBlank { "Teacher" }
            val editorUid = (raw["lastEditedByUid"] as? String).orEmpty()
                .ifBlank { (raw["updatedByUid"] as? String).orEmpty() }
                .ifBlank { authorUid }
            val editorName = (raw["lastEditedByName"] as? String).orEmpty()
                .ifBlank { (raw["updatedByName"] as? String).orEmpty() }
                .ifBlank { authorName }
            return ClassDiaryEntry(
                id = id,
                schoolId = schoolId,
                session = (raw["session"] as? String).orEmpty().trim(),
                dateIso = dateIso,
                className = className,
                section = (raw["section"] as? String).orEmpty().trim().uppercase(Locale.US).ifBlank { "A" },
                subjectName = subjectName,
                classwork = (raw["classwork"] as? String).orEmpty(),
                homework = (raw["homework"] as? String).orEmpty(),
                note = (raw["note"] as? String).orEmpty(),
                photoBase64 = (raw["photoBase64"] as? String).orEmpty(),
                authorUid = authorUid,
                authorName = authorName,
                lastEditedByUid = editorUid,
                lastEditedByName = editorName,
                editHistory = revisions,
                createdAtMs = (raw["createdAtMs"] as? Number)?.toLong() ?: 0L,
                updatedAtMs = (raw["updatedAtMs"] as? Number)?.toLong() ?: 0L,
                isPendingSync = isPendingSync
            )
        }
    }
}

data class ClassDiaryHistoryRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val entryId: String,
    val dateIso: String,
    val className: String,
    val section: String,
    val subjectName: String,
    val action: String, // "CREATED", "EDITED", "DELETED"
    val classworkSnapshot: String,
    val homeworkSnapshot: String,
    val noteSnapshot: String = "",
    val authorUid: String,
    val authorName: String,
    val actorUid: String,
    val actorName: String,
    val timestampMs: Long,
    val previousClasswork: String = "",
    val previousHomework: String = "",
    val previousNote: String = "",
    val isPendingSync: Boolean = false
) {
    val classwork: String get() = classworkSnapshot
    val homework: String get() = homeworkSnapshot
    val note: String get() = noteSnapshot

    fun toFirestoreMap(createdAtTimestamp: Any): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "session" to session.trim(),
        "entryId" to entryId.trim(),
        "dateIso" to dateIso.trim(),
        "className" to className.trim(),
        "section" to section.trim().uppercase(Locale.US).ifBlank { "A" },
        "subjectName" to subjectName.trim(),
        "action" to action.trim(),
        "classwork" to classworkSnapshot.take(1500),
        "homework" to homeworkSnapshot.take(1500),
        "note" to noteSnapshot.take(800),
        "previousClasswork" to "",
        "previousHomework" to "",
        "previousNote" to "",
        "classworkSnapshot" to classworkSnapshot.take(1500),
        "homeworkSnapshot" to homeworkSnapshot.take(1500),
        "noteSnapshot" to noteSnapshot.take(800),
        "authorUid" to authorUid.trim(),
        "authorName" to authorName.trim().ifBlank { "Teacher" }.take(120),
        "actorUid" to actorUid.trim(),
        "actorName" to actorName.trim().ifBlank { "Staff" }.take(120),
        "timestampMs" to timestampMs,
        "createdAt" to createdAtTimestamp
    )

    companion object {
        fun fromMap(id: String, raw: Map<*, *>?, isPendingSync: Boolean = false): ClassDiaryHistoryRecord? {
            if (raw == null) return null
            val schoolId = (raw["schoolId"] as? String).orEmpty().trim()
            val entryId = (raw["entryId"] as? String).orEmpty().trim()
            val dateIso = (raw["dateIso"] as? String).orEmpty().trim()
            if (schoolId.isBlank() || dateIso.isBlank()) return null
            val cw = (raw["classworkSnapshot"] as? String).orEmpty()
                .ifBlank { (raw["classwork"] as? String).orEmpty() }
            val hw = (raw["homeworkSnapshot"] as? String).orEmpty()
                .ifBlank { (raw["homework"] as? String).orEmpty() }
            val nt = (raw["noteSnapshot"] as? String).orEmpty()
                .ifBlank { (raw["note"] as? String).orEmpty() }
            val actorUid = (raw["actorUid"] as? String).orEmpty().trim()
            val actorName = (raw["actorName"] as? String).orEmpty().trim()
            return ClassDiaryHistoryRecord(
                id = id,
                schoolId = schoolId,
                session = (raw["session"] as? String).orEmpty().trim(),
                entryId = entryId,
                dateIso = dateIso,
                className = (raw["className"] as? String).orEmpty().trim(),
                section = (raw["section"] as? String).orEmpty().trim().uppercase(Locale.US).ifBlank { "A" },
                subjectName = (raw["subjectName"] as? String).orEmpty().trim(),
                action = (raw["action"] as? String).orEmpty().trim().ifBlank { "EDITED" },
                classworkSnapshot = cw,
                homeworkSnapshot = hw,
                noteSnapshot = nt,
                authorUid = (raw["authorUid"] as? String).orEmpty().trim().ifBlank { actorUid },
                authorName = (raw["authorName"] as? String).orEmpty().trim().ifBlank { actorName },
                actorUid = actorUid,
                actorName = actorName,
                timestampMs = (raw["timestampMs"] as? Number)?.toLong() ?: 0L,
                isPendingSync = isPendingSync
            )
        }
    }
}

data class DiaryMissingClassSection(
    val schoolId: String,
    val className: String,
    val section: String,
    val classTeacherName: String = "",
    val expectedSubjectCount: Int = 0,
    val activeStudentCount: Int = 0
) {
    val label: String get() = "${className.trim()}-${section.trim().ifBlank { "A" }}"
}

data class WeeklyDiaryDay(
    val dateIso: String,
    val dayShortLabelEn: String,
    val dayShortLabelHi: String,
    val entries: List<ClassDiaryEntry>
)

data class WeeklyDiaryBundle(
    val schoolId: String,
    val schoolName: String,
    val session: String,
    val className: String,
    val section: String,
    val weekStartIso: String,
    val weekEndIso: String,
    val days: List<WeeklyDiaryDay>
) {
    val totalEntriesCount: Int get() = days.sumOf { it.entries.size }
    val totalEntries: Int get() = totalEntriesCount
    val daysWrittenCount: Int get() = days.count { it.entries.isNotEmpty() }
    val daysByDate: Map<String, List<ClassDiaryEntry>> get() = days.associate { it.dateIso to it.entries }
}

typealias ClassDiaryEntryRecord = ClassDiaryEntry
typealias WeeklyClassDiaryBundle = WeeklyDiaryBundle

// =============================================================================
// BIRTHDAY MODELS
// =============================================================================

enum class BirthdayPersonType(val wireValue: String, val labelEn: String, val labelHi: String) {
    STUDENT("STUDENT", "Student", "छात्र"),
    TEACHER("TEACHER", "Staff / Teacher", "शिक्षक / स्टाफ");

    companion object {
        fun fromRaw(raw: String?): BirthdayPersonType =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: STUDENT
    }
}

data class SchoolBirthdayConfig(
    val schoolId: String,
    val birthdayWishesEnabled: Boolean = true,
    val templateEn: String = Phase18DiaryAndBirthdayEngine.DEFAULT_BIRTHDAY_TEMPLATE_EN,
    val templateHi: String = Phase18DiaryAndBirthdayEngine.DEFAULT_BIRTHDAY_TEMPLATE_HI,
    val excludedStudentKeys: List<String> = emptyList(),
    val excludedTeacherIds: List<String> = emptyList(),
    val staffDobByTeacherId: Map<String, String> = emptyMap(),
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val updatedAtMs: Long = 0L,
    val isPendingSync: Boolean = false
) {
    val id: String get() = schoolId
    val wishTemplateEn: String get() = templateEn
    val wishTemplateHi: String get() = templateHi
    fun isStudentExcluded(student: StudentEntity): Boolean {
        val idKey = "id:${student.id}"
        val rawId = student.id.toString()
        val admKey = "adm:${student.admNo.trim()}"
        return excludedStudentKeys.any { key ->
            key.equals(idKey, ignoreCase = true) ||
                key.equals(rawId, ignoreCase = true) ||
                (student.admNo.isNotBlank() && key.equals(admKey, ignoreCase = true)) ||
                (student.admNo.isNotBlank() && key.equals(student.admNo.trim(), ignoreCase = true))
        }
    }

    fun isTeacherExcluded(teacherId: String): Boolean =
        excludedTeacherIds.any { it.equals(teacherId.trim(), ignoreCase = true) }

    fun toFirestoreMap(updatedAtTimestamp: Any): Map<String, Any> {
        val cleanEn = templateEn.trim().ifBlank { Phase18DiaryAndBirthdayEngine.DEFAULT_BIRTHDAY_TEMPLATE_EN }.take(600)
        val cleanHi = templateHi.trim().ifBlank { Phase18DiaryAndBirthdayEngine.DEFAULT_BIRTHDAY_TEMPLATE_HI }.take(600)
        return mapOf(
            "schoolId" to schoolId.trim(),
            "birthdayWishesEnabled" to birthdayWishesEnabled,
            "templateEn" to cleanEn,
            "templateHi" to cleanHi,
            "wishTemplateEn" to cleanEn,
            "wishTemplateHi" to cleanHi,
            "excludedStudentKeys" to excludedStudentKeys.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(500),
            "excludedTeacherIds" to excludedTeacherIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(200),
            "staffDobByTeacherId" to staffDobByTeacherId.entries
                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                .take(200)
                .associate { it.key.trim() to it.value.trim().take(32) },
            "updatedByUid" to updatedByUid.trim().take(128),
            "updatedByName" to updatedByName.trim().take(120),
            "updatedAtMs" to updatedAtMs,
            "updatedAt" to updatedAtTimestamp
        )
    }

    companion object {
        fun defaultForSchool(schoolId: String): SchoolBirthdayConfig =
            SchoolBirthdayConfig(schoolId = schoolId)

        fun fromMap(id: String, raw: Map<*, *>?, isPendingSync: Boolean = false): SchoolBirthdayConfig? {
            if (raw == null) return null
            val schoolId = (raw["schoolId"] as? String).orEmpty().trim().ifBlank { id }
            if (schoolId.isBlank()) return null
            val exStudents = (raw["excludedStudentKeys"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val exTeachers = (raw["excludedTeacherIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val rawStaffDob = raw["staffDobByTeacherId"] as? Map<*, *>
            val staffDobs = linkedMapOf<String, String>()
            rawStaffDob?.forEach { (k, v) ->
                val ks = (k as? String)?.trim().orEmpty()
                val vs = (v as? String)?.trim().orEmpty()
                if (ks.isNotEmpty() && vs.isNotEmpty()) staffDobs[ks] = vs
            }
            val tplEn = (raw["templateEn"] as? String).orEmpty()
                .ifBlank { (raw["wishTemplateEn"] as? String).orEmpty() }
                .ifBlank { Phase18DiaryAndBirthdayEngine.DEFAULT_BIRTHDAY_TEMPLATE_EN }
            val tplHi = (raw["templateHi"] as? String).orEmpty()
                .ifBlank { (raw["wishTemplateHi"] as? String).orEmpty() }
                .ifBlank { Phase18DiaryAndBirthdayEngine.DEFAULT_BIRTHDAY_TEMPLATE_HI }
            return SchoolBirthdayConfig(
                schoolId = schoolId,
                birthdayWishesEnabled = (raw["birthdayWishesEnabled"] as? Boolean) ?: true,
                templateEn = tplEn,
                templateHi = tplHi,
                excludedStudentKeys = exStudents,
                excludedTeacherIds = exTeachers,
                staffDobByTeacherId = staffDobs,
                updatedByUid = (raw["updatedByUid"] as? String).orEmpty(),
                updatedByName = (raw["updatedByName"] as? String).orEmpty(),
                updatedAtMs = (raw["updatedAtMs"] as? Number)?.toLong() ?: 0L,
                isPendingSync = isPendingSync
            )
        }
    }
}

data class BirthdayWishRecord(
    val id: String,
    val schoolId: String,
    val personType: BirthdayPersonType,
    val personId: String,
    val personName: String,
    val className: String,
    val section: String,
    val celebrationDateIso: String,
    val wishedByUid: String,
    val wishedByName: String,
    val channel: String = "WHATSAPP",
    val wishedAtMs: Long = 0L,
    val isPendingSync: Boolean = false
) {
    fun toFirestoreMap(
        createdAtTimestamp: Any,
        updatedAtTimestamp: Any
    ): Map<String, Any> {
        val celebYear = celebrationDateIso.trim().take(4).toIntOrNull()?.coerceIn(2000, 2100) ?: 2026
        return mapOf(
            "schoolId" to schoolId.trim(),
            "personType" to personType.wireValue,
            "personId" to personId.trim().take(120),
            "personName" to personName.trim().take(120),
            "className" to className.trim().take(40),
            "section" to section.trim().take(20),
            "celebrationDateIso" to celebrationDateIso.trim(),
            "celebrationYear" to celebYear,
            "wishedByUid" to wishedByUid.trim(),
            "wishedByName" to wishedByName.trim().ifBlank { "Staff" }.take(120),
            "channel" to channel.trim().ifBlank { "WHATSAPP" }.take(24),
            "wishedAtMs" to wishedAtMs,
            "createdAt" to createdAtTimestamp,
            "updatedAt" to updatedAtTimestamp
        )
    }

    companion object {
        fun fromMap(id: String, raw: Map<*, *>?, isPendingSync: Boolean = false): BirthdayWishRecord? {
            if (raw == null) return null
            val schoolId = (raw["schoolId"] as? String).orEmpty().trim()
            val personId = (raw["personId"] as? String).orEmpty().trim()
            val celebrationDateIso = (raw["celebrationDateIso"] as? String).orEmpty().trim()
            if (schoolId.isBlank() || personId.isBlank() || celebrationDateIso.isBlank()) return null
            return BirthdayWishRecord(
                id = id,
                schoolId = schoolId,
                personType = BirthdayPersonType.fromRaw(raw["personType"] as? String),
                personId = personId,
                personName = (raw["personName"] as? String).orEmpty(),
                className = (raw["className"] as? String).orEmpty(),
                section = (raw["section"] as? String).orEmpty(),
                celebrationDateIso = celebrationDateIso,
                wishedByUid = (raw["wishedByUid"] as? String).orEmpty(),
                wishedByName = (raw["wishedByName"] as? String).orEmpty().ifBlank { "Staff" },
                channel = (raw["channel"] as? String).orEmpty().ifBlank { "WHATSAPP" },
                wishedAtMs = (raw["wishedAtMs"] as? Number)?.toLong() ?: 0L,
                isPendingSync = isPendingSync
            )
        }
    }
}

data class ParsedBirthDate(
    val year: Int?,
    val month: Int, // 1..12
    val day: Int    // 1..31
)

data class BirthdayOccurrenceItem(
    val personType: BirthdayPersonType,
    val personId: String,
    val studentEntityId: Long? = null,
    val name: String,
    val fatherOrSubtitle: String,
    val className: String,
    val section: String,
    val admNoOrRole: String,
    val rawDob: String,
    val birthYear: Int?,
    val birthMonth: Int,
    val birthDay: Int,
    val celebrationDateIso: String,
    val daysUntil: Int,
    val turningAge: Int?,
    val rawMobile: String,
    val normalizedWhatsAppMobile: String,
    val wishedRecord: BirthdayWishRecord? = null,
    val formattedWishMessage: String = ""
) {
    val isToday: Boolean get() = daysUntil == 0
    val isWished: Boolean get() = wishedRecord != null
    val isBornOnFeb29: Boolean get() = birthMonth == 2 && birthDay == 29
    val isFeb29CelebratedOnFeb28: Boolean get() = isBornOnFeb29 && celebrationDateIso.endsWith("-02-28")
    val classGroupHeader: String
        get() = if (personType == BirthdayPersonType.TEACHER) "Staff / Teachers" else "Class ${className.trim()}"
    val classGroupLabel: String
        get() = if (personType == BirthdayPersonType.TEACHER) "Staff / Teachers" else "${className.trim()}-${section.trim().ifBlank { "A" }}"
    val admissionNo: String get() = if (personType == BirthdayPersonType.STUDENT) admNoOrRole else ""
    val guardianName: String get() = if (personType == BirthdayPersonType.STUDENT) fatherOrSubtitle else ""
    val dobIso: String get() = rawDob
    val mobile: String get() = rawMobile
    val normalizedMobile: String get() = normalizedWhatsAppMobile
    val wishRecord: BirthdayWishRecord? get() = wishedRecord
    val celebrationYear: Int get() = celebrationDateIso.take(4).toIntOrNull() ?: 2026
}

data class BirthdaySummaryBundle(
    val schoolId: String,
    val todayIso: String,
    val isBirthdayWishesEnabled: Boolean,
    val canViewStudents: Boolean,
    val canViewTeachers: Boolean,
    val todayItems: List<BirthdayOccurrenceItem>,
    val upcoming7DayItems: List<BirthdayOccurrenceItem>,
    val allWindowItems: List<BirthdayOccurrenceItem>,
    val groupedByClass: Map<String, List<BirthdayOccurrenceItem>>,
    val excludedStudentsCount: Int,
    val excludedTeachersCount: Int
) {
    val todayCount: Int get() = todayItems.size
    val upcoming7DaysCount: Int get() = upcoming7DayItems.size
    val upcoming7DaysItems: List<BirthdayOccurrenceItem> get() = upcoming7DayItems
    val totalWindowCount: Int get() = allWindowItems.size
}

// =============================================================================
// ACCESS EVALUATOR FOR CLASS DIARY AND BIRTHDAYS
// =============================================================================

object Phase18AccessEvaluator {

    fun isClassTeacherOfSection(
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String
    ): Boolean {
        if (teacherScope == null || schoolId.isBlank() || className.isBlank()) return false
        val target = "${schoolId.trim()}|${className.trim()}|${section.trim().ifBlank { "A" }}"
        return teacherScope.classTeacherSections.any { it.equals(target, ignoreCase = true) }
    }

    fun isSubjectTeacherOf(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String,
        subjectName: String
    ): Boolean {
        if (schoolId.isBlank() || className.isBlank() || subjectName.isBlank()) return false
        val sec = section.trim().ifBlank { "A" }
        if (teacherScope != null && teacherScope.assignedClassSectionSubjects.isNotEmpty()) {
            val target = TeacherAccessScope.makeSubjectScopeKey(schoolId, className, sec, subjectName)
            if (teacherScope.assignedClassSectionSubjects.any { it.equals(target, ignoreCase = true) }) {
                return true
            }
        }
        val legacyList = member?.assignedClassSubjects.orEmpty()
        if (legacyList.isNotEmpty()) {
            val legacyTarget = OrgAccessEvaluator.makeTeacherScopeKey(schoolId, className, subjectName)
            if (legacyList.any { it.equals(legacyTarget, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    fun hasTeacherDiaryAssignmentInSchool(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String
    ): Boolean {
        if (schoolId.isBlank()) return false
        val prefix = "${schoolId.trim()}|"
        val hasCt = teacherScope?.classTeacherSections?.any {
            it.startsWith(prefix, ignoreCase = true)
        } == true
        if (hasCt) return true
        val hasSub = teacherScope?.assignedClassSectionSubjects?.any {
            it.startsWith(prefix, ignoreCase = true)
        } == true
        if (hasSub) return true
        val hasLegacy = member?.assignedClassSubjects?.any {
            it.startsWith(prefix, ignoreCase = true)
        } == true
        return hasLegacy
    }

    fun hasAnyTeacherDiaryAssignment(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?
    ): Boolean {
        if (teacherScope?.classTeacherSections?.isNotEmpty() == true) return true
        if (teacherScope?.assignedClassSectionSubjects?.isNotEmpty() == true) return true
        if (member?.assignedClassSubjects?.isNotEmpty() == true) return true
        return false
    }

    fun hasAnyTeacherDiaryAssignment(
        teacherScope: TeacherAccessScope?
    ): Boolean = hasAnyTeacherDiaryAssignment(null, teacherScope)

    /**
     * Who can view the Class Diary screen for [schoolId]:
     * - Owner and Admin: YES
     * - Any member with `classDiary` in `[VIEW, EDIT]` for [schoolId]: YES
     * - Any Class Teacher or Subject Teacher assigned in [schoolId] (without extra setup): YES
     * - Anyone else (missing key / NONE and no teacher assignment in [schoolId]): NO
     */
    fun canViewClassDiary(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        if (OrgAccessEvaluator.canViewModule(member, AppModule.CLASS_DIARY)) return true
        return hasTeacherDiaryAssignmentInSchool(member, teacherScope, schoolId)
    }

    /**
     * Who can view a specific class-section's diary:
     * - Owner and Admin: YES
     * - Non-teacher with `classDiary` in `[VIEW, EDIT]`: YES
     * - Class Teacher of `(schoolId, className, section)`: YES
     * - Subject Teacher assigned to at least one subject in `(schoolId, className, section)`: YES
     * - Teacher with explicit `classDiary` in `[VIEW, EDIT]` and no specific teacherScope configured: YES
     */
    fun canViewClassSectionDiary(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String
    ): Boolean {
        if (!canViewClassDiary(member, teacherScope, schoolId)) return false
        val m = member ?: return false
        if (OrgAccessEvaluator.isAdmin(m)) return true
        if (m.role != OrgRole.TEACHER && OrgAccessEvaluator.canViewModule(m, AppModule.CLASS_DIARY)) return true
        if (isClassTeacherOfSection(teacherScope, schoolId, className, section)) return true
        val sec = section.trim().ifBlank { "A" }
        val sectionPrefix = "${schoolId.trim()}|${className.trim()}|$sec|"
        val hasSubjectInSection = teacherScope?.assignedClassSectionSubjects?.any {
            it.startsWith(sectionPrefix, ignoreCase = true)
        } == true
        if (hasSubjectInSection) return true
        val legacyPrefix = "${schoolId.trim()}|${className.trim()}|"
        val hasLegacyInClass = m.assignedClassSubjects.any {
            it.startsWith(legacyPrefix, ignoreCase = true)
        }
        if (hasLegacyInClass) return true
        return OrgAccessEvaluator.canViewModule(m, AppModule.CLASS_DIARY) &&
            !hasAnyTeacherDiaryAssignment(m, teacherScope)
    }

    /**
     * Who can write (create) a diary entry for `(schoolId, className, section, subjectName)`:
     * - Owner and Admin: YES (all classes & subjects)
     * - Non-teacher member with `classDiary == EDIT`: YES
     * - Class Teacher of `(schoolId, className, section)`: YES (without extra setup, for their class-section)
     * - Subject Teacher of `(schoolId, className, section, subjectName)`: YES (without extra setup, for their own class & subject)
     * - Teacher with explicit `classDiary == EDIT` and no scope restrictions configured: YES
     */
    fun canWriteDiaryEntryFor(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String,
        subjectName: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        val m = member ?: return false
        if (OrgAccessEvaluator.isAdmin(m)) return true
        if (m.role != OrgRole.TEACHER && OrgAccessEvaluator.canEditModule(m, AppModule.CLASS_DIARY)) return true
        if (isClassTeacherOfSection(teacherScope, schoolId, className, section)) return true
        if (isSubjectTeacherOf(m, teacherScope, schoolId, className, section, subjectName)) return true
        return m.role == OrgRole.TEACHER &&
            OrgAccessEvaluator.canEditModule(m, AppModule.CLASS_DIARY) &&
            !hasAnyTeacherDiaryAssignment(m, teacherScope)
    }

    /**
     * "Edit and delete only by the author or an admin, with the history kept."
     */
    fun canEditOrDeleteDiaryEntry(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        entry: ClassDiaryEntry
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, entry.schoolId)) return false
        val m = member ?: return false
        if (OrgAccessEvaluator.isAdmin(m)) return true
        if (m.uid.isBlank() || entry.authorUid != m.uid) return false
        return canWriteDiaryEntryFor(
            member = m,
            teacherScope = teacherScope,
            schoolId = entry.schoolId,
            className = entry.className,
            section = entry.section,
            subjectName = entry.subjectName
        )
    }

    /**
     * "Diary not written today" list is exclusively for Owner and Admins.
     */
    fun canViewDiaryNotWrittenTodayList(member: MemberAccessProfile?): Boolean =
        OrgAccessEvaluator.isAdmin(member)

    /**
     * "Birthdays use the Student Register and teacher data, and are visible to anyone with access to those."
     */
    fun canViewStudentBirthdays(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        if (OrgAccessEvaluator.canViewModule(member, AppModule.STUDENTS)) return true
        if (OrgAccessEvaluator.canViewModule(member, AppModule.STUDENT_REGISTER)) return true
        val prefix = "${schoolId.trim()}|"
        return teacherScope?.classTeacherSections?.any { it.startsWith(prefix, ignoreCase = true) } == true
    }

    fun canViewTeacherBirthdays(
        member: MemberAccessProfile?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        return OrgAccessEvaluator.canViewModule(member, AppModule.TEACHERS)
    }

    fun canViewBirthdays(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String
    ): Boolean = canViewStudentBirthdays(member, teacherScope, schoolId) ||
        canViewTeacherBirthdays(member, schoolId)

    fun canManageBirthdaySettings(
        member: MemberAccessProfile?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        return OrgAccessEvaluator.canEditModule(member, AppModule.SETTINGS) ||
            OrgAccessEvaluator.canEditModule(member, AppModule.STUDENT_REGISTER) ||
            OrgAccessEvaluator.canEditModule(member, AppModule.STUDENTS)
    }
}

// =============================================================================
// PURE COMPUTATION ENGINE FOR CLASS DIARY & BIRTHDAYS
// =============================================================================

object Phase18DiaryAndBirthdayEngine {

    const val DEFAULT_BIRTHDAY_TEMPLATE_EN =
        "Dear Parent, Warmest birthday wishes to {name} from the entire family of {school}! May the year ahead be filled with joy, good health, and great learning."

    const val DEFAULT_BIRTHDAY_TEMPLATE_HI =
        "नमस्ते अभिभावक, {school} परिवार की ओर से {name} को जन्मदिन की हार्दिक शुभकामनाएँ! हम उनके उज्ज्वल भविष्य और उत्तम स्वास्थ्य की कामना करते हैं।"

    private fun sanitizeIdPart(raw: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "NA" }

    fun makeDiaryEntryId(
        schoolId: String,
        session: String,
        dateIso: String,
        className: String,
        section: String,
        subjectName: String
    ): String {
        val sec = section.trim().uppercase(Locale.US).ifBlank { "A" }
        return "diary_${sanitizeIdPart(schoolId)}_${sanitizeIdPart(session)}_${sanitizeIdPart(dateIso)}_${sanitizeIdPart(className)}_${sanitizeIdPart(sec)}_${sanitizeIdPart(subjectName.uppercase(Locale.US))}"
    }

    fun makeBirthdayWishDocId(
        schoolId: String,
        personType: BirthdayPersonType,
        personId: String,
        celebrationDateIso: String
    ): String {
        return "wish_${sanitizeIdPart(schoolId)}_${personType.wireValue}_${sanitizeIdPart(personId)}_${sanitizeIdPart(celebrationDateIso)}"
    }

    /**
     * Builds the subject list for a class-section from:
     * 1) Phase 3 class subjects (`classSubjects`)
     * 2) Phase 4 class-section assignments (`classAssignments`)
     * 3) The teacher's own assignments (`teacherScope` / `member.assignedClassSubjects`)
     *
     * If the viewer is a subject teacher (and not Class Teacher / Admin / unrestricted office editor),
     * returns only the subjects they are allowed to write for in that class-section.
     * Orders the teacher's own assigned subjects first for 1-tap selection.
     */
    fun availableSubjectsForDiary(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String,
        classSubjects: List<String>,
        classAssignments: List<ClassSectionAssignment> = emptyList()
    ): List<String> {
        val sec = section.trim().uppercase(Locale.US).ifBlank { "A" }
        val assignmentSubjects = classAssignments
            .filter {
                it.schoolId == schoolId &&
                    it.className.equals(className.trim(), ignoreCase = true) &&
                    it.section.equals(sec, ignoreCase = true)
            }
            .flatMap { it.subjectAssignments.map { sa -> sa.subjectName.trim() } }

        val scopePrefix = "${schoolId.trim()}|${className.trim()}|$sec|"
        val ownScopedSubjects = teacherScope?.assignedClassSectionSubjects
            .orEmpty()
            .filter { it.startsWith(scopePrefix, ignoreCase = true) }
            .map { it.substringAfterLast("|").trim() }
            .filter { it.isNotEmpty() }

        val legacyPrefix = "${schoolId.trim()}|${className.trim()}|"
        val ownLegacySubjects = member?.assignedClassSubjects
            .orEmpty()
            .filter { it.startsWith(legacyPrefix, ignoreCase = true) }
            .map { it.substringAfterLast("|").trim() }
            .filter { it.isNotEmpty() }

        val ownAssignedSet = (ownScopedSubjects + ownLegacySubjects).map { it.lowercase(Locale.US) }.toSet()

        val combined = linkedSetOf<String>()
        (ownScopedSubjects + ownLegacySubjects + classSubjects + assignmentSubjects)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { subj ->
                if (combined.none { it.equals(subj, ignoreCase = true) }) {
                    combined.add(subj)
                }
            }

        val writable = combined.filter { subj ->
            Phase18AccessEvaluator.canWriteDiaryEntryFor(
                member = member,
                teacherScope = teacherScope,
                schoolId = schoolId,
                className = className,
                section = sec,
                subjectName = subj
            )
        }

        val baseList = if (writable.isNotEmpty()) writable else combined.toList()
        return baseList.sortedWith(
            compareByDescending<String> { it.lowercase(Locale.US) in ownAssignedSet }
                .thenBy { baseList.indexOf(it) }
        )
    }

    /**
     * Creates a new diary entry + its initial "CREATED" history record.
     */
    fun createDiaryEntryWithHistory(
        schoolId: String,
        session: String,
        dateIso: String,
        className: String,
        section: String,
        subjectName: String,
        classwork: String,
        homework: String,
        note: String = "",
        photoBase64: String = "",
        actor: MemberAccessProfile,
        actorName: String,
        teacherScope: TeacherAccessScope?,
        nowMs: Long = System.currentTimeMillis()
    ): Result<Pair<ClassDiaryEntry, ClassDiaryHistoryRecord>> {
        if (!Phase18AccessEvaluator.canWriteDiaryEntryFor(actor, teacherScope, schoolId, className, section, subjectName)) {
            return Result.failure(IllegalAccessException("You do not have permission to write the diary for $className-$section ($subjectName)."))
        }
        val cleanClasswork = classwork.trim()
        val cleanHomework = homework.trim()
        if (cleanClasswork.isEmpty() && cleanHomework.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter classwork or homework."))
        }
        val sec = section.trim().uppercase(Locale.US).ifBlank { "A" }
        val entryId = makeDiaryEntryId(schoolId, session, dateIso, className, sec, subjectName)
        val cleanActorName = actorName.trim().ifBlank { "Teacher" }
        val entry = ClassDiaryEntry(
            id = entryId,
            schoolId = schoolId.trim(),
            session = session.trim(),
            dateIso = dateIso.trim(),
            className = className.trim(),
            section = sec,
            subjectName = subjectName.trim(),
            classwork = cleanClasswork,
            homework = cleanHomework,
            note = note.trim(),
            photoBase64 = photoBase64,
            authorUid = actor.uid,
            authorName = cleanActorName,
            lastEditedByUid = actor.uid,
            lastEditedByName = cleanActorName,
            editHistory = emptyList(),
            createdAtMs = nowMs,
            updatedAtMs = nowMs
        )
        val historyRecord = ClassDiaryHistoryRecord(
            id = "${entryId}_hist_create_$nowMs",
            schoolId = entry.schoolId,
            session = entry.session,
            entryId = entry.id,
            dateIso = entry.dateIso,
            className = entry.className,
            section = entry.section,
            subjectName = entry.subjectName,
            action = "CREATED",
            classworkSnapshot = entry.classwork,
            homeworkSnapshot = entry.homework,
            noteSnapshot = entry.note,
            authorUid = entry.authorUid,
            authorName = entry.authorName,
            actorUid = actor.uid,
            actorName = cleanActorName,
            timestampMs = nowMs
        )
        return Result.success(entry to historyRecord)
    }

    /**
     * Edits an existing diary entry:
     * - Enforces that ONLY the original author or an Admin (Owner/Admin) can edit.
     * - Preserves the previous content in `entry.editHistory` AND creates an append-only `ClassDiaryHistoryRecord`.
     */
    fun editDiaryEntryWithHistory(
        existing: ClassDiaryEntry,
        newClasswork: String,
        newHomework: String,
        newNote: String = existing.note,
        newPhotoBase64: String = existing.photoBase64,
        actor: MemberAccessProfile,
        actorName: String,
        teacherScope: TeacherAccessScope?,
        nowMs: Long = System.currentTimeMillis()
    ): Result<Pair<ClassDiaryEntry, ClassDiaryHistoryRecord>> {
        if (!Phase18AccessEvaluator.canEditOrDeleteDiaryEntry(actor, teacherScope, existing)) {
            return Result.failure(IllegalAccessException("Only the original author (${existing.authorName}) or an Admin can edit this diary entry."))
        }
        val cleanClasswork = newClasswork.trim()
        val cleanHomework = newHomework.trim()
        if (cleanClasswork.isEmpty() && cleanHomework.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter classwork or homework."))
        }
        val cleanActorName = actorName.trim().ifBlank { "Teacher" }
        val revision = ClassDiaryRevision(
            revisionNo = existing.editHistory.size + 1,
            previousClasswork = existing.classwork,
            previousHomework = existing.homework,
            previousNote = existing.note,
            hadPhoto = existing.hasPhoto,
            editedByUid = actor.uid,
            editedByName = cleanActorName,
            editedAtMs = nowMs,
            action = "EDITED"
        )
        val updated = existing.copy(
            classwork = cleanClasswork,
            homework = cleanHomework,
            note = newNote.trim(),
            photoBase64 = newPhotoBase64,
            lastEditedByUid = actor.uid,
            lastEditedByName = cleanActorName,
            editHistory = existing.editHistory + revision,
            updatedAtMs = nowMs
        )
        val historyRecord = ClassDiaryHistoryRecord(
            id = "${existing.id}_hist_edit_${updated.editHistory.size}_$nowMs",
            schoolId = existing.schoolId,
            session = existing.session,
            entryId = existing.id,
            dateIso = existing.dateIso,
            className = existing.className,
            section = existing.section,
            subjectName = existing.subjectName,
            action = "EDITED",
            classworkSnapshot = updated.classwork,
            homeworkSnapshot = updated.homework,
            noteSnapshot = updated.note,
            authorUid = existing.authorUid,
            authorName = existing.authorName,
            actorUid = actor.uid,
            actorName = cleanActorName,
            timestampMs = nowMs
        )
        return Result.success(updated to historyRecord)
    }

    /**
     * Deletes an existing diary entry:
     * - Enforces that ONLY the original author or an Admin (Owner/Admin) can delete.
     * - Produces a permanent `ClassDiaryHistoryRecord(action = "DELETED")` so history is kept even after deletion.
     */
    fun deleteDiaryEntryWithHistory(
        existing: ClassDiaryEntry,
        actor: MemberAccessProfile,
        actorName: String,
        teacherScope: TeacherAccessScope?,
        nowMs: Long = System.currentTimeMillis()
    ): Result<ClassDiaryHistoryRecord> {
        if (!Phase18AccessEvaluator.canEditOrDeleteDiaryEntry(actor, teacherScope, existing)) {
            return Result.failure(IllegalAccessException("Only the original author (${existing.authorName}) or an Admin can delete this diary entry."))
        }
        val cleanActorName = actorName.trim().ifBlank { "Admin" }
        val historyRecord = ClassDiaryHistoryRecord(
            id = "${existing.id}_hist_delete_$nowMs",
            schoolId = existing.schoolId,
            session = existing.session,
            entryId = existing.id,
            dateIso = existing.dateIso,
            className = existing.className,
            section = existing.section,
            subjectName = existing.subjectName,
            action = "DELETED",
            classworkSnapshot = existing.classwork,
            homeworkSnapshot = existing.homework,
            noteSnapshot = existing.note,
            authorUid = existing.authorUid,
            authorName = existing.authorName,
            actorUid = actor.uid,
            actorName = cleanActorName,
            timestampMs = nowMs
        )
        return Result.success(historyRecord)
    }

    /**
     * One-tap "Share as message": builds a clean, readable text for the whole class
     * (date, class, then each subject with classwork and homework, and a school name line)
     * to copy or share to the class WhatsApp group through the Android share sheet.
     * Never sends anything automatically.
     */
    fun buildClassDiaryShareText(
        schoolName: String,
        className: String,
        section: String,
        dateIso: String,
        entries: List<ClassDiaryEntry>,
        subjectOrder: List<String> = emptyList(),
        isHindi: Boolean = false
    ): String {
        val cleanSchool = schoolName.trim().ifBlank { "School" }
        val sec = section.trim().uppercase(Locale.US).ifBlank { "A" }
        val classLabel = "${className.trim()}-$sec"
        val sortedEntries = entries.sortedWith(
            compareBy<ClassDiaryEntry> { entry ->
                val idx = subjectOrder.indexOfFirst { it.equals(entry.subjectName, ignoreCase = true) }
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenBy { it.subjectName.lowercase(Locale.US) }
        )

        return buildString {
            if (isHindi) {
                appendLine("📘 कक्षा डायरी (Class Diary)")
                appendLine("कक्षा: $classLabel | दिनांक: $dateIso")
                appendLine("--------------------------------")
                if (sortedEntries.isEmpty()) {
                    appendLine("आज के लिए कोई डायरी प्रविष्टि दर्ज नहीं है।")
                } else {
                    sortedEntries.forEachIndexed { index, entry ->
                        appendLine("${index + 1}. ${entry.subjectName}:")
                        if (entry.classwork.isNotBlank()) {
                            appendLine("   • कक्षा कार्य (CW): ${entry.classwork}")
                        }
                        if (entry.homework.isNotBlank()) {
                            appendLine("   • गृहकार्य (HW): ${entry.homework}")
                        }
                        if (entry.note.isNotBlank()) {
                            appendLine("   • नोट: ${entry.note}")
                        }
                        if (entry.hasPhoto) {
                            appendLine("   • [ब्लैकबोर्ड/गृहकार्य फोटो संलग्न]")
                        }
                    }
                }
                appendLine("--------------------------------")
                append("विद्यालय: $cleanSchool")
            } else {
                appendLine("📘 Class Diary")
                appendLine("Class: $classLabel | Date: $dateIso")
                appendLine("--------------------------------")
                if (sortedEntries.isEmpty()) {
                    appendLine("No diary entries recorded for today.")
                } else {
                    sortedEntries.forEachIndexed { index, entry ->
                        appendLine("${index + 1}. ${entry.subjectName}:")
                        if (entry.classwork.isNotBlank()) {
                            appendLine("   • Classwork: ${entry.classwork}")
                        }
                        if (entry.homework.isNotBlank()) {
                            appendLine("   • Homework: ${entry.homework}")
                        }
                        if (entry.note.isNotBlank()) {
                            appendLine("   • Note: ${entry.note}")
                        }
                        if (entry.hasPhoto) {
                            appendLine("   • [Blackboard/Homework Photo Attached]")
                        }
                    }
                }
                appendLine("--------------------------------")
                append("School: $cleanSchool")
            }
        }
    }

    fun entriesForClassAndDate(
        allEntries: List<ClassDiaryEntry>,
        schoolId: String,
        session: String,
        dateIso: String,
        className: String,
        section: String
    ): List<ClassDiaryEntry> {
        val sec = section.trim().uppercase(Locale.US).ifBlank { "A" }
        return allEntries.filter {
            it.schoolId == schoolId &&
                (session.isBlank() || it.session == session) &&
                it.dateIso == dateIso &&
                it.className.equals(className.trim(), ignoreCase = true) &&
                it.section.equals(sec, ignoreCase = true)
        }.sortedBy { it.subjectName.lowercase(Locale.US) }
    }

    fun buildShareMessageForClassDate(
        schoolName: String,
        className: String,
        section: String,
        dateIso: String,
        entries: List<ClassDiaryEntry>,
        hindiLabels: Boolean = false
    ): String = buildClassDiaryShareText(
        schoolName = schoolName,
        className = className,
        section = section,
        dateIso = dateIso,
        entries = entries,
        isHindi = hindiLabels
    )

    fun dayOfWeekShortLabel(dateIso: String, isHindi: Boolean = false): String {
        val (y, m, d) = parseIsoYmd(dateIso) ?: return ""
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(y, m - 1, d)
        }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> if (isHindi) "सोम" else "Mon"
            Calendar.TUESDAY -> if (isHindi) "मंगल" else "Tue"
            Calendar.WEDNESDAY -> if (isHindi) "बुध" else "Wed"
            Calendar.THURSDAY -> if (isHindi) "गुरु" else "Thu"
            Calendar.FRIDAY -> if (isHindi) "शुक्र" else "Fri"
            Calendar.SATURDAY -> if (isHindi) "शनि" else "Sat"
            Calendar.SUNDAY -> if (isHindi) "रवि" else "Sun"
            else -> ""
        }
    }

    fun studentExclusionKey(student: StudentEntity): String =
        if (student.admNo.isNotBlank()) "adm:${student.admNo.trim().uppercase(Locale.US)}" else "id:${student.id}"

    fun isStudentExcluded(student: StudentEntity, config: SchoolBirthdayConfig): Boolean =
        config.isStudentExcluded(student)

    /**
     * Computes the "Diary not written today" list for Owner and Admins:
     * returns all active class-sections in [schoolId] where NO diary entry exists on [todayIso].
     */
    fun computeDiaryNotWrittenToday(
        schoolId: String,
        session: String,
        todayIso: String,
        activeStudents: List<StudentEntity>,
        configuredClasses: List<String> = emptyList(),
        classAssignments: List<ClassSectionAssignment> = emptyList(),
        teachers: List<TeacherProfile> = emptyList(),
        subjectsByClass: Map<String, List<String>> = emptyMap(),
        allDiaryEntries: List<ClassDiaryEntry>,
        classOrder: List<String> = emptyList()
    ): List<DiaryMissingClassSection> {
        val pairSet = linkedSetOf<Pair<String, String>>()

        // 1. Active students in this session
        activeStudents
            .filter { it.isActiveStatus && (session.isBlank() || it.session == session) && it.className.isNotBlank() }
            .forEach { st ->
                pairSet.add(st.className.trim() to st.section.trim().uppercase(Locale.US).ifBlank { "A" })
            }

        // 2. Class assignments in this school
        classAssignments
            .filter { it.schoolId == schoolId && it.className.isNotBlank() }
            .forEach { ca ->
                pairSet.add(ca.className.trim() to ca.section.trim().uppercase(Locale.US).ifBlank { "A" })
            }

        // 3. Configured school classes (only add default section 'A' if that class has no section in pairSet yet)
        configuredClasses
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { cls ->
                if (pairSet.none { it.first.equals(cls, ignoreCase = true) }) {
                    pairSet.add(cls to "A")
                }
            }

        val writtenPairsToday = allDiaryEntries
            .filter {
                it.schoolId == schoolId &&
                    (session.isBlank() || it.session == session) &&
                    it.dateIso == todayIso
            }
            .map { "${it.className.trim().lowercase(Locale.US)}|${it.section.trim().uppercase(Locale.US).ifBlank { "A" }}" }
            .toSet()

        val teacherById = teachers.associateBy { it.id }

        val orderList = classOrder.ifEmpty { configuredClasses }
        return pairSet
            .filter { (cls, sec) ->
                "${cls.lowercase(Locale.US)}|$sec" !in writtenPairsToday
            }
            .map { (cls, sec) ->
                val ca = classAssignments.firstOrNull {
                    it.schoolId == schoolId &&
                        it.className.equals(cls, ignoreCase = true) &&
                        it.section.equals(sec, ignoreCase = true)
                }
                val ctName = ca?.classTeacherId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { teacherById[it]?.name ?: "" }
                    .orEmpty()
                val subCount = subjectsByClass.entries
                    .firstOrNull { it.key.equals(cls, ignoreCase = true) }
                    ?.value?.size
                    ?: ca?.subjectAssignments?.size
                    ?: 0
                val stuCount = activeStudents.count {
                    it.isActiveStatus &&
                        (session.isBlank() || it.session == session) &&
                        it.className.equals(cls, ignoreCase = true) &&
                        it.section.trim().uppercase(Locale.US).ifBlank { "A" }.equals(sec, ignoreCase = true)
                }
                DiaryMissingClassSection(
                    schoolId = schoolId,
                    className = cls,
                    section = sec,
                    classTeacherName = ctName,
                    expectedSubjectCount = subCount,
                    activeStudentCount = stuCount
                )
            }
            .sortedWith(
                compareBy<DiaryMissingClassSection> { item ->
                    val idx = orderList.indexOfFirst { it.equals(item.className, ignoreCase = true) }
                    if (idx >= 0) idx else Int.MAX_VALUE
                }
                    .thenBy { it.className.lowercase(Locale.US) }
                    .thenBy { it.section }
            )
    }

    /**
     * Builds the 7-day Monday..Sunday weekly diary view for a class-section containing [anchorDateIso].
     */
    fun buildWeeklyDiaryBundle(
        schoolId: String,
        schoolName: String,
        session: String,
        className: String,
        section: String,
        anchorDateIso: String,
        allDiaryEntries: List<ClassDiaryEntry>,
        subjectOrder: List<String> = emptyList()
    ): WeeklyDiaryBundle {
        val sec = section.trim().uppercase(Locale.US).ifBlank { "A" }
        val weekDates = weekDatesMonToSun(anchorDateIso)
        val dayLabelsEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dayLabelsHi = listOf("सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि", "रवि")

        val classEntries = allDiaryEntries.filter {
            it.schoolId == schoolId &&
                (session.isBlank() || it.session == session) &&
                it.className.equals(className.trim(), ignoreCase = true) &&
                it.section.equals(sec, ignoreCase = true)
        }
        val byDate = classEntries.groupBy { it.dateIso }

        val days = weekDates.mapIndexed { idx, dateIso ->
            val dayEntries = byDate[dateIso].orEmpty().sortedWith(
                compareBy<ClassDiaryEntry> { entry ->
                    val sIdx = subjectOrder.indexOfFirst { it.equals(entry.subjectName, ignoreCase = true) }
                    if (sIdx >= 0) sIdx else Int.MAX_VALUE
                }.thenBy { it.subjectName.lowercase(Locale.US) }
            )
            WeeklyDiaryDay(
                dateIso = dateIso,
                dayShortLabelEn = dayLabelsEn.getOrElse(idx) { "" },
                dayShortLabelHi = dayLabelsHi.getOrElse(idx) { "" },
                entries = dayEntries
            )
        }

        return WeeklyDiaryBundle(
            schoolId = schoolId,
            schoolName = schoolName.ifBlank { "School" },
            session = session,
            className = className.trim(),
            section = sec,
            weekStartIso = weekDates.first(),
            weekEndIso = weekDates.last(),
            days = days
        )
    }

    fun weekDatesMonToSun(anchorDateIso: String): List<String> {
        val (y, m, d) = parseIsoYmd(anchorDateIso) ?: Triple(2026, 11, 25)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(y, m - 1, d)
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK) // SUN=1, MON=2..SAT=7
        val offsetFromMon = if (dow == Calendar.SUNDAY) -6 else (Calendar.MONDAY - dow)
        cal.add(Calendar.DAY_OF_MONTH, offsetFromMon)
        return (0 until 7).map { i ->
            if (i > 0) cal.add(Calendar.DAY_OF_MONTH, 1)
            formatIsoYmd(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }
    }

    fun shiftDateIso(dateIso: String, deltaDays: Int): String {
        val (y, m, d) = parseIsoYmd(dateIso) ?: return dateIso
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(y, m - 1, d)
            add(Calendar.DAY_OF_MONTH, deltaDays)
        }
        return formatIsoYmd(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // =========================================================================
    // BIRTHDAY DATE PARSING, 29 FEB & YEAR-END ROLLOVER, WHATSAPP & TEMPLATES
    // =========================================================================

    fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun maxDaysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 0
    }

    fun parseIsoYmd(iso: String): Triple<Int, Int, Int>? {
        val clean = iso.trim()
        val parts = clean.split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (y !in 1900..2200 || m !in 1..12 || d !in 1..maxDaysInMonth(y, m)) return null
        return Triple(y, m, d)
    }

    fun formatIsoYmd(year: Int, month: Int, day: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    /**
     * Parses a stored DOB string in common Indian school formats:
     * - `yyyy-MM-dd` or `yyyy/MM/dd` (e.g. `2016-02-29`)
     * - `dd-MM-yyyy` or `dd/MM/yyyy` or `d/M/yyyy` (e.g. `29/02/2016`, `5-1-2015`)
     * - `dd.MM.yyyy`
     */
    fun parseDobMonthDayYear(rawDob: String): ParsedBirthDate? {
        val clean = rawDob.trim()
        if (clean.isEmpty()) return null
        val tokens = clean.split(Regex("[-/.\\s]+")).filter { it.isNotEmpty() }
        if (tokens.size != 3) return null

        val p0 = tokens[0].toIntOrNull() ?: return null
        val p1 = tokens[1].toIntOrNull() ?: return null
        val p2 = tokens[2].toIntOrNull() ?: return null

        val (year, month, day) = when {
            tokens[0].length == 4 || p0 >= 1900 -> Triple(p0, p1, p2)
            tokens[2].length == 4 || p2 >= 1900 -> Triple(p2, p1, p0)
            else -> return null
        }

        if (year !in 1920..2100 || month !in 1..12) return null
        val maxDay = maxDaysInMonth(year, month)
        if (day !in 1..maxDay) return null
        return ParsedBirthDate(year = year, month = month, day = day)
    }

    /**
     * Resolves the celebration date `(year, month, day)` in [targetYear].
     * For someone born on 29 February:
     * - In a leap year (e.g. 2028), celebrated on 29 February (`targetYear-02-29`).
     * - In a non-leap year (e.g. 2026, 2027), celebrated on 28 February (`targetYear-02-28`).
     */
    fun celebrationDateInYear(birthMonth: Int, birthDay: Int, targetYear: Int): Triple<Int, Int, Int> {
        return if (birthMonth == 2 && birthDay == 29 && !isLeapYear(targetYear)) {
            Triple(targetYear, 2, 28)
        } else {
            Triple(targetYear, birthMonth, birthDay)
        }
    }

    private fun epochDayUtc(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }
        return cal.timeInMillis / 86_400_000L
    }

    fun daysBetweenIso(fromIso: String, toIso: String): Int? {
        val (fy, fm, fd) = parseIsoYmd(fromIso) ?: return null
        val (ty, tm, td) = parseIsoYmd(toIso) ?: return null
        return (epochDayUtc(ty, tm, td) - epochDayUtc(fy, fm, fd)).toInt()
    }

    /**
     * Normalizes an Indian mobile number to `+91XXXXXXXXXX`.
     * Returns `""` if the input does not contain a valid 10-digit mobile number.
     */
    fun normalizeIndianMobileForWhatsApp(rawMobile: String): String {
        val digitsOnly = rawMobile.filter { it.isDigit() }
        val tenDigits = when {
            digitsOnly.length == 10 -> digitsOnly
            digitsOnly.length == 11 && digitsOnly.startsWith("0") -> digitsOnly.drop(1)
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> digitsOnly.drop(2)
            digitsOnly.length > 10 && digitsOnly.takeLast(10).first() in '6'..'9' -> digitsOnly.takeLast(10)
            else -> ""
        }
        if (tenDigits.length != 10 || tenDigits.first() !in '6'..'9') return ""
        return "+91$tenDigits"
    }

    /**
     * Renders the editable birthday wish template with `{name}`, `{school}`, `{class}`, `{age}` placeholders.
     * Guarantees that both the child/person name and the school name appear in the final message.
     */
    fun formatBirthdayWishMessage(
        template: String,
        personName: String,
        schoolName: String,
        className: String = "",
        turningAge: Int? = null,
        isHindi: Boolean = false
    ): String {
        val cleanName = personName.trim().ifBlank { if (isHindi) "प्रिय छात्र" else "Dear Student" }
        val cleanSchool = schoolName.trim().ifBlank { "School" }
        val cleanClass = className.trim()
        val baseTemplate = template.trim().ifBlank {
            if (isHindi) DEFAULT_BIRTHDAY_TEMPLATE_HI else DEFAULT_BIRTHDAY_TEMPLATE_EN
        }

        var msg = baseTemplate
            .replace("{name}", cleanName, ignoreCase = true)
            .replace("{childName}", cleanName, ignoreCase = true)
            .replace("{studentName}", cleanName, ignoreCase = true)
            .replace("{school}", cleanSchool, ignoreCase = true)
            .replace("{schoolName}", cleanSchool, ignoreCase = true)
            .replace("{class}", cleanClass, ignoreCase = true)
            .replace("{className}", cleanClass, ignoreCase = true)
            .replace("{age}", turningAge?.toString().orEmpty(), ignoreCase = true)
            .trim()

        if (!msg.contains(cleanName, ignoreCase = true)) {
            msg = "$cleanName — $msg"
        }
        if (!msg.contains(cleanSchool, ignoreCase = true)) {
            msg = "$msg\n— $cleanSchool"
        }
        return msg
    }

    /**
     * Builds the `https://wa.me/91XXXXXXXXXX?text=...` link for one-tap WhatsApp wishes.
     * Never called automatically; only invoked when the user taps "Send wishes on WhatsApp".
     */
    fun buildWhatsAppWishUrl(
        rawOrNormalizedMobile: String,
        message: String
    ): String? {
        val normalized = if (rawOrNormalizedMobile.startsWith("+91") && rawOrNormalizedMobile.length == 13) {
            rawOrNormalizedMobile
        } else {
            normalizeIndianMobileForWhatsApp(rawOrNormalizedMobile)
        }
        if (normalized.isBlank()) return null
        val waDigits = normalized.removePrefix("+")
        val encodedText = runCatching {
            URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        }.getOrElse { "" }
        return "https://wa.me/$waDigits?text=$encodedText"
    }

    /**
     * Computes today's birthdays and the next [windowDays] (default 7) birthdays for students and staff:
     * - Excludes students who have left (`status == "LEFT"` / `!student.isActiveStatus`)
     * - Excludes students/teachers excluded in [config] or [extraExcludedStudentIds]
     * - Handles 29 February (celebrated on 28 Feb in non-leap years, 29 Feb in leap years)
     * - Handles December-to-January year rollover cleanly
     * - Groups results by class in [classOrder] with Staff at the end
     */
    fun computeBirthdaySummary(
        schoolId: String,
        session: String = "",
        todayIso: String,
        students: List<StudentEntity>,
        teachers: List<TeacherProfile>,
        config: SchoolBirthdayConfig = SchoolBirthdayConfig(schoolId = schoolId),
        wishes: List<BirthdayWishRecord> = emptyList(),
        member: MemberAccessProfile? = null,
        teacherScope: TeacherAccessScope? = null,
        classOrder: List<String> = emptyList(),
        windowDays: Int = 7,
        extraExcludedStudentIds: Set<Long> = emptySet(),
        schoolName: String = "School",
        useHindiTemplate: Boolean = false
    ): BirthdaySummaryBundle {
        val canViewStudents = if (member == null) true else Phase18AccessEvaluator.canViewStudentBirthdays(member, teacherScope, schoolId)
        val canViewTeachers = if (member == null) true else Phase18AccessEvaluator.canViewTeacherBirthdays(member, schoolId)
        val activeTemplate = if (useHindiTemplate) config.templateHi else config.templateEn

        val excludedStudentsCount = students.count {
            it.isActiveStatus && (session.isBlank() || it.session == session) &&
                (config.isStudentExcluded(it) || it.id in extraExcludedStudentIds)
        }
        val excludedTeachersCount = teachers.count {
            it.active && config.isTeacherExcluded(it.id)
        }

        if (!config.birthdayWishesEnabled || (!canViewStudents && !canViewTeachers)) {
            return BirthdaySummaryBundle(
                schoolId = schoolId,
                todayIso = todayIso,
                isBirthdayWishesEnabled = config.birthdayWishesEnabled,
                canViewStudents = canViewStudents,
                canViewTeachers = canViewTeachers,
                todayItems = emptyList(),
                upcoming7DayItems = emptyList(),
                allWindowItems = emptyList(),
                groupedByClass = emptyMap(),
                excludedStudentsCount = excludedStudentsCount,
                excludedTeachersCount = excludedTeachersCount
            )
        }

        val (todayYear, _, _) = parseIsoYmd(todayIso) ?: Triple(2026, 11, 25)
        val wishLookup = wishes
            .filter { it.schoolId == schoolId }
            .associateBy { "${it.personType.wireValue}|${it.personId}|${it.celebrationDateIso}" }

        val occurrences = mutableListOf<BirthdayOccurrenceItem>()

        // 1. Students (active only, non-left, non-excluded)
        if (canViewStudents) {
            students
                .filter { st ->
                    st.isActiveStatus &&
                        (session.isBlank() || st.session == session) &&
                        !config.isStudentExcluded(st) &&
                        st.id !in extraExcludedStudentIds
                }
                .forEach { st ->
                    val parsedDob = parseDobMonthDayYear(st.dob) ?: return@forEach
                    val match = resolveNextWindowOccurrence(
                        todayIso = todayIso,
                        todayYear = todayYear,
                        parsedDob = parsedDob,
                        windowDays = windowDays
                    ) ?: return@forEach

                    val (celebIso, daysUntil, celebYear) = match
                    val personId = st.admNo.trim().ifBlank { st.id.toString() }
                    val wish = wishLookup["${BirthdayPersonType.STUDENT.wireValue}|$personId|$celebIso"]
                        ?: wishLookup["${BirthdayPersonType.STUDENT.wireValue}|${st.id}|$celebIso"]
                    val primaryMobile = st.mobile.trim().ifBlank { st.secondMobile.trim() }
                    val normalizedMobile = normalizeIndianMobileForWhatsApp(primaryMobile)
                        .ifBlank { normalizeIndianMobileForWhatsApp(st.secondMobile) }

                    val turningAge = parsedDob.year?.let { (celebYear - it).coerceAtLeast(0) }
                    val sec = st.section.trim().uppercase(Locale.US).ifBlank { "A" }
                    val formattedMsg = formatBirthdayWishMessage(
                        template = activeTemplate,
                        personName = st.name.trim(),
                        schoolName = schoolName,
                        className = "${st.className.trim()}-$sec",
                        turningAge = turningAge,
                        isHindi = useHindiTemplate
                    )

                    occurrences.add(
                        BirthdayOccurrenceItem(
                            personType = BirthdayPersonType.STUDENT,
                            personId = personId,
                            studentEntityId = st.id,
                            name = st.name.trim(),
                            fatherOrSubtitle = st.fatherName.trim(),
                            className = st.className.trim(),
                            section = sec,
                            admNoOrRole = st.admNo.trim(),
                            rawDob = st.dob.trim(),
                            birthYear = parsedDob.year,
                            birthMonth = parsedDob.month,
                            birthDay = parsedDob.day,
                            celebrationDateIso = celebIso,
                            daysUntil = daysUntil,
                            turningAge = turningAge,
                            rawMobile = primaryMobile,
                            normalizedWhatsAppMobile = normalizedMobile,
                            wishedRecord = wish,
                            formattedWishMessage = formattedMsg
                        )
                    )
                }
        }

        // 2. Staff / Teachers (active only, non-excluded, belonging to school or org-wide)
        if (canViewTeachers) {
            teachers
                .filter { t ->
                    t.active &&
                        (t.schoolIds.isEmpty() || schoolId in t.schoolIds) &&
                        !config.isTeacherExcluded(t.id)
                }
                .forEach { t ->
                    val rawDob = t.dob.trim().ifBlank { config.staffDobByTeacherId[t.id].orEmpty().trim() }
                    val parsedDob = parseDobMonthDayYear(rawDob) ?: return@forEach
                    val match = resolveNextWindowOccurrence(
                        todayIso = todayIso,
                        todayYear = todayYear,
                        parsedDob = parsedDob,
                        windowDays = windowDays
                    ) ?: return@forEach

                    val (celebIso, daysUntil, celebYear) = match
                    val wish = wishLookup["${BirthdayPersonType.TEACHER.wireValue}|${t.id}|$celebIso"]
                    val normalizedMobile = normalizeIndianMobileForWhatsApp(t.mobile)
                    val turningAge = parsedDob.year?.let { (celebYear - it).coerceAtLeast(0) }
                    val formattedMsg = formatBirthdayWishMessage(
                        template = activeTemplate,
                        personName = t.name.trim(),
                        schoolName = schoolName,
                        className = "Staff",
                        turningAge = turningAge,
                        isHindi = useHindiTemplate
                    )

                    occurrences.add(
                        BirthdayOccurrenceItem(
                            personType = BirthdayPersonType.TEACHER,
                            personId = t.id,
                            studentEntityId = null,
                            name = t.name.trim(),
                            fatherOrSubtitle = t.subjects.joinToString(", ").ifBlank { "Staff" },
                            className = "Staff",
                            section = "",
                            admNoOrRole = "Teacher",
                            rawDob = rawDob,
                            birthYear = parsedDob.year,
                            birthMonth = parsedDob.month,
                            birthDay = parsedDob.day,
                            celebrationDateIso = celebIso,
                            daysUntil = daysUntil,
                            turningAge = turningAge,
                            rawMobile = t.mobile.trim(),
                            normalizedWhatsAppMobile = normalizedMobile,
                            wishedRecord = wish,
                            formattedWishMessage = formattedMsg
                        )
                    )
                }
        }

        val sortedAll = occurrences.sortedWith(
            compareBy<BirthdayOccurrenceItem> { it.daysUntil }
                .thenBy { item ->
                    if (item.personType == BirthdayPersonType.TEACHER) Int.MAX_VALUE
                    else {
                        val idx = classOrder.indexOfFirst { it.equals(item.className, ignoreCase = true) }
                        if (idx >= 0) idx else Int.MAX_VALUE - 1
                    }
                }
                .thenBy { it.className.lowercase(Locale.US) }
                .thenBy { it.section }
                .thenBy { it.name.lowercase(Locale.US) }
        )

        val todayItems = sortedAll.filter { it.daysUntil == 0 }
        val upcomingItems = sortedAll.filter { it.daysUntil in 1..windowDays }

        val grouped = linkedMapOf<String, MutableList<BirthdayOccurrenceItem>>()
        sortedAll
            .sortedWith(
                compareBy<BirthdayOccurrenceItem> { item ->
                    if (item.personType == BirthdayPersonType.TEACHER) Int.MAX_VALUE
                    else {
                        val idx = classOrder.indexOfFirst { it.equals(item.className, ignoreCase = true) }
                        if (idx >= 0) idx else Int.MAX_VALUE - 1
                    }
                }
                    .thenBy { it.className.lowercase(Locale.US) }
                    .thenBy { it.daysUntil }
                    .thenBy { it.section }
                    .thenBy { it.name.lowercase(Locale.US) }
            )
            .forEach { item ->
                grouped.getOrPut(item.classGroupHeader) { mutableListOf() }.add(item)
            }

        return BirthdaySummaryBundle(
            schoolId = schoolId,
            todayIso = todayIso,
            isBirthdayWishesEnabled = true,
            canViewStudents = canViewStudents,
            canViewTeachers = canViewTeachers,
            todayItems = todayItems,
            upcoming7DayItems = upcomingItems,
            allWindowItems = sortedAll,
            groupedByClass = grouped,
            excludedStudentsCount = excludedStudentsCount,
            excludedTeachersCount = excludedTeachersCount
        )
    }

    private fun resolveNextWindowOccurrence(
        todayIso: String,
        todayYear: Int,
        parsedDob: ParsedBirthDate,
        windowDays: Int
    ): Triple<String, Int, Int>? {
        for (candidateYear in listOf(todayYear, todayYear + 1)) {
            val (cy, cm, cd) = celebrationDateInYear(parsedDob.month, parsedDob.day, candidateYear)
            val celebIso = formatIsoYmd(cy, cm, cd)
            val diff = daysBetweenIso(todayIso, celebIso) ?: continue
            if (diff in 0..windowDays) {
                return Triple(celebIso, diff, cy)
            }
        }
        return null
    }
}
