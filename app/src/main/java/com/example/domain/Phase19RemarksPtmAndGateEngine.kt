package com.example.domain

import com.example.data.StudentEntity
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

// =============================================================================
// PHASE 19: STUDENT REMARKS, PARENT MEETINGS (PTM) AND GATE PASS
// Pure Kotlin domain models, access evaluator, and business logic engine.
// =============================================================================

enum class DefaultRemarkCategory(
    val key: String,
    val labelEn: String,
    val labelHi: String
) {
    PRAISE("PRAISE", "Praise", "प्रशंसा (Praise)"),
    ACADEMIC("ACADEMIC", "Academic", "पढ़ाई (Academic)"),
    DISCIPLINE("DISCIPLINE", "Discipline", "अनुशासन (Discipline)"),
    ATTENDANCE("ATTENDANCE", "Attendance", "उपस्थिति (Attendance)"),
    HEALTH("HEALTH", "Health", "स्वास्थ्य (Health)"),
    OTHER("OTHER", "Other", "अन्य (Other)");

    fun displayLabel(useHindi: Boolean = true): String =
        if (useHindi) labelHi else labelEn

    companion object {
        val defaultCategoryNames: List<String> = entries.map { it.labelEn }

        fun fromRawOrCustom(raw: String?): String {
            val cleaned = raw?.trim().orEmpty()
            if (cleaned.isBlank()) return OTHER.labelEn
            val matched = entries.firstOrNull {
                it.key.equals(cleaned, ignoreCase = true) ||
                    it.labelEn.equals(cleaned, ignoreCase = true) ||
                    it.labelHi.equals(cleaned, ignoreCase = true)
            }
            return matched?.labelEn ?: cleaned.take(40)
        }
    }
}

data class SchoolRemarkCategoryConfig(
    val schoolId: String,
    val categories: List<String> = DefaultRemarkCategory.defaultCategoryNames,
    val hideSummaryPdfFromNonAdmins: Boolean = true,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val updatedAtMs: Long = 0L
) {
    val effectiveCategories: List<String>
        get() = (DefaultRemarkCategory.defaultCategoryNames + categories)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.US) }

    fun toFirestoreMap(updatedAtTimestamp: Any): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "categories" to effectiveCategories.take(30),
        "hideSummaryPdfFromNonAdmins" to hideSummaryPdfFromNonAdmins,
        "updatedByUid" to updatedByUid.trim().take(128),
        "updatedByName" to updatedByName.trim().ifBlank { "Admin" }.take(120),
        "updatedAtMs" to updatedAtMs,
        "updatedAt" to updatedAtTimestamp
    )

    companion object {
        fun fromMap(schoolId: String, raw: Map<*, *>?): SchoolRemarkCategoryConfig {
            if (raw == null) return SchoolRemarkCategoryConfig(schoolId = schoolId)
            val rawList = (raw["categories"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotBlank() } }
                .orEmpty()
            val merged = (DefaultRemarkCategory.defaultCategoryNames + rawList)
                .distinctBy { it.lowercase(Locale.US) }
            return SchoolRemarkCategoryConfig(
                schoolId = (raw["schoolId"] as? String)?.trim()?.ifBlank { schoolId } ?: schoolId,
                categories = merged,
                hideSummaryPdfFromNonAdmins = (raw["hideSummaryPdfFromNonAdmins"] as? Boolean) ?: true,
                updatedByUid = (raw["updatedByUid"] as? String).orEmpty(),
                updatedByName = (raw["updatedByName"] as? String).orEmpty(),
                updatedAtMs = (raw["updatedAtMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

enum class StudentRemarkStatus(val wireValue: String, val labelEn: String, val labelHi: String) {
    ACTIVE("ACTIVE", "Active", "सक्रिय (Active)"),
    CANCELLED("CANCELLED", "Cancelled", "रद्द (Cancelled)");

    companion object {
        fun fromRaw(raw: String?): StudentRemarkStatus =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: ACTIVE
    }
}

data class StudentRemarkRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val studentId: String,
    val studentAdmNo: String,
    val studentName: String,
    val className: String,
    val section: String,
    val rollNo: Int = 0,
    val dateIso: String,
    val category: String,
    val note: String,
    val followUpDateIso: String = "",
    val followUpCompleted: Boolean = false,
    val followUpCompletedNote: String = "",
    val status: StudentRemarkStatus = StudentRemarkStatus.ACTIVE,
    val cancelledReason: String = "",
    val cancelledByUid: String = "",
    val cancelledByName: String = "",
    val cancelledAtMs: Long = 0L,
    val authorUid: String,
    val authorName: String,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val isPendingSync: Boolean = false
) {
    val isActive: Boolean get() = status == StudentRemarkStatus.ACTIVE
    val isCancelled: Boolean get() = status == StudentRemarkStatus.CANCELLED
    val hasFollowUpDue: Boolean
        get() = isActive && followUpDateIso.isNotBlank() && !followUpCompleted
    val classSectionLabel: String
        get() = "${className.trim()}-${section.trim().ifBlank { "A" }}"

    fun isFollowUpOverdue(todayIso: String): Boolean =
        hasFollowUpDue && followUpDateIso < todayIso

    fun toFirestoreMap(
        createdAtTimestamp: Any,
        updatedAtTimestamp: Any
    ): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "session" to session.trim(),
        "studentId" to studentId.trim(),
        "studentAdmNo" to studentAdmNo.trim().take(40),
        "studentName" to studentName.trim().take(120),
        "className" to className.trim().take(40),
        "section" to section.trim().uppercase(Locale.US).ifBlank { "A" }.take(16),
        "rollNo" to rollNo,
        "dateIso" to dateIso.trim().take(16),
        "category" to category.trim().ifBlank { DefaultRemarkCategory.OTHER.labelEn }.take(40),
        "note" to note.trim().take(1000),
        "followUpDateIso" to followUpDateIso.trim().take(16),
        "followUpCompleted" to followUpCompleted,
        "followUpCompletedNote" to followUpCompletedNote.trim().take(500),
        "status" to status.wireValue,
        "cancelledReason" to cancelledReason.trim().take(500),
        "cancelledByUid" to cancelledByUid.trim().take(128),
        "cancelledByName" to cancelledByName.trim().take(120),
        "cancelledAtMs" to cancelledAtMs,
        "authorUid" to authorUid.trim().take(128),
        "authorName" to authorName.trim().ifBlank { "Teacher" }.take(120),
        "createdAtMs" to createdAtMs,
        "updatedAtMs" to updatedAtMs,
        "createdAt" to createdAtTimestamp,
        "updatedAt" to updatedAtTimestamp
    )

    companion object {
        fun fromMap(docId: String, raw: Map<*, *>?, isPendingSync: Boolean = false): StudentRemarkRecord? {
            if (raw == null) return null
            val schoolId = (raw["schoolId"] as? String)?.trim().orEmpty()
            val studentId = (raw["studentId"] as? String)?.trim().orEmpty()
            val dateIso = (raw["dateIso"] as? String)?.trim().orEmpty()
            if (schoolId.isBlank() || studentId.isBlank() || dateIso.isBlank()) return null
            return StudentRemarkRecord(
                id = docId,
                schoolId = schoolId,
                session = (raw["session"] as? String)?.trim().orEmpty(),
                studentId = studentId,
                studentAdmNo = (raw["studentAdmNo"] as? String)?.trim().orEmpty(),
                studentName = (raw["studentName"] as? String)?.trim().orEmpty(),
                className = (raw["className"] as? String)?.trim().orEmpty(),
                section = (raw["section"] as? String)?.trim().orEmpty().ifBlank { "A" },
                rollNo = (raw["rollNo"] as? Number)?.toInt() ?: 0,
                dateIso = dateIso,
                category = DefaultRemarkCategory.fromRawOrCustom(raw["category"] as? String),
                note = (raw["note"] as? String)?.trim().orEmpty(),
                followUpDateIso = (raw["followUpDateIso"] as? String)?.trim().orEmpty(),
                followUpCompleted = (raw["followUpCompleted"] as? Boolean) ?: false,
                followUpCompletedNote = (raw["followUpCompletedNote"] as? String)?.trim().orEmpty(),
                status = StudentRemarkStatus.fromRaw(raw["status"] as? String),
                cancelledReason = (raw["cancelledReason"] as? String)?.trim().orEmpty(),
                cancelledByUid = (raw["cancelledByUid"] as? String)?.trim().orEmpty(),
                cancelledByName = (raw["cancelledByName"] as? String)?.trim().orEmpty(),
                cancelledAtMs = (raw["cancelledAtMs"] as? Number)?.toLong() ?: 0L,
                authorUid = (raw["authorUid"] as? String)?.trim().orEmpty(),
                authorName = (raw["authorName"] as? String)?.trim().orEmpty().ifBlank { "Teacher" },
                createdAtMs = (raw["createdAtMs"] as? Number)?.toLong() ?: 0L,
                updatedAtMs = (raw["updatedAtMs"] as? Number)?.toLong() ?: 0L,
                isPendingSync = isPendingSync
            )
        }
    }
}

// =============================================================================
// 2. PARENT MEETINGS (PTM) MODELS
// =============================================================================

enum class PtmCallStatus(val wireValue: String, val labelEn: String, val labelHi: String) {
    CALLED("CALLED", "Called", "बुलाया गया (Called)"),
    ATTENDED("ATTENDED", "Attended", "आए / उपस्थित (Attended)"),
    NOT_ATTENDED("NOT_ATTENDED", "Not Attended", "नहीं आए (Not Attended)");

    companion object {
        fun fromRaw(raw: String?): PtmCallStatus =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: CALLED
    }
}

data class PtmStudentSuggestion(
    val studentId: String,
    val studentAdmNo: String,
    val studentName: String,
    val className: String,
    val section: String,
    val rollNo: Int,
    val guardianName: String,
    val mobile: String,
    val reasons: List<String>,
    val isWeakInResult: Boolean = false,
    val isLowAttendance: Boolean = false,
    val hasConcernRemarks: Boolean = false
) {
    val combinedReason: String
        get() = reasons.joinToString(" • ").ifBlank { "Suggested for PTM" }
}

data class PtmStudentCallEntry(
    val studentId: String,
    val studentAdmNo: String = "",
    val studentName: String,
    val className: String,
    val section: String,
    val rollNo: Int = 0,
    val guardianName: String = "",
    val mobile: String = "",
    val suggestionReason: String = "",
    val status: PtmCallStatus = PtmCallStatus.CALLED,
    val discussionNote: String = "",
    val decision: String = "",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val updatedAtMs: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "studentId" to studentId.trim().take(128),
        "studentAdmNo" to studentAdmNo.trim().take(40),
        "studentName" to studentName.trim().take(120),
        "className" to className.trim().take(40),
        "section" to section.trim().uppercase(Locale.US).ifBlank { "A" }.take(16),
        "rollNo" to rollNo,
        "guardianName" to guardianName.trim().take(120),
        "mobile" to mobile.trim().take(24),
        "suggestionReason" to suggestionReason.trim().take(240),
        "status" to status.wireValue,
        "discussionNote" to discussionNote.trim().take(1000),
        "decision" to decision.trim().take(600),
        "updatedByUid" to updatedByUid.trim().take(128),
        "updatedByName" to updatedByName.trim().take(120),
        "updatedAtMs" to updatedAtMs
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): PtmStudentCallEntry? {
            if (raw == null) return null
            val studentId = (raw["studentId"] as? String)?.trim().orEmpty()
            val studentName = (raw["studentName"] as? String)?.trim().orEmpty()
            if (studentId.isBlank() || studentName.isBlank()) return null
            return PtmStudentCallEntry(
                studentId = studentId,
                studentAdmNo = (raw["studentAdmNo"] as? String)?.trim().orEmpty(),
                studentName = studentName,
                className = (raw["className"] as? String)?.trim().orEmpty(),
                section = (raw["section"] as? String)?.trim().orEmpty().ifBlank { "A" },
                rollNo = (raw["rollNo"] as? Number)?.toInt() ?: 0,
                guardianName = (raw["guardianName"] as? String)?.trim().orEmpty(),
                mobile = (raw["mobile"] as? String)?.trim().orEmpty(),
                suggestionReason = (raw["suggestionReason"] as? String)?.trim().orEmpty(),
                status = PtmCallStatus.fromRaw(raw["status"] as? String),
                discussionNote = (raw["discussionNote"] as? String)?.trim().orEmpty(),
                decision = (raw["decision"] as? String)?.trim().orEmpty(),
                updatedByUid = (raw["updatedByUid"] as? String)?.trim().orEmpty(),
                updatedByName = (raw["updatedByName"] as? String)?.trim().orEmpty(),
                updatedAtMs = (raw["updatedAtMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

data class ParentMeetingRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val title: String,
    val dateIso: String,
    val timeLabel: String = "10:00 AM - 1:00 PM",
    val className: String = "ALL",
    val section: String = "ALL",
    val agenda: String = "",
    val entries: List<PtmStudentCallEntry> = emptyList(),
    val createdByUid: String,
    val createdByName: String,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val isPendingSync: Boolean = false
) {
    val isWholeSchool: Boolean
        get() = className.equals("ALL", ignoreCase = true) || className.isBlank()

    val scopeLabel: String
        get() = if (isWholeSchool) {
            "Whole School (पूरा स्कूल)"
        } else if (section.equals("ALL", ignoreCase = true) || section.isBlank()) {
            "Class $className (All Sections)"
        } else {
            "Class $className-$section"
        }

    val totalCalledCount: Int get() = entries.size
    val attendedCount: Int get() = entries.count { it.status == PtmCallStatus.ATTENDED }
    val notAttendedCount: Int get() = entries.count { it.status == PtmCallStatus.NOT_ATTENDED }
    val pendingCalledCount: Int get() = entries.count { it.status == PtmCallStatus.CALLED }

    fun toFirestoreMap(
        createdAtTimestamp: Any,
        updatedAtTimestamp: Any
    ): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "session" to session.trim(),
        "title" to title.trim().ifBlank { "Parent-Teacher Meeting (PTM)" }.take(120),
        "dateIso" to dateIso.trim().take(16),
        "timeLabel" to timeLabel.trim().ifBlank { "10:00 AM - 1:00 PM" }.take(60),
        "className" to className.trim().ifBlank { "ALL" }.take(40),
        "section" to section.trim().uppercase(Locale.US).ifBlank { "ALL" }.take(16),
        "agenda" to agenda.trim().take(600),
        "entries" to entries.take(250).map { it.toMap() },
        "createdByUid" to createdByUid.trim().take(128),
        "createdByName" to createdByName.trim().ifBlank { "Teacher" }.take(120),
        "createdAtMs" to createdAtMs,
        "updatedAtMs" to updatedAtMs,
        "createdAt" to createdAtTimestamp,
        "updatedAt" to updatedAtTimestamp
    )

    companion object {
        fun fromMap(docId: String, raw: Map<*, *>?, isPendingSync: Boolean = false): ParentMeetingRecord? {
            if (raw == null) return null
            val schoolId = (raw["schoolId"] as? String)?.trim().orEmpty()
            val dateIso = (raw["dateIso"] as? String)?.trim().orEmpty()
            if (schoolId.isBlank() || dateIso.isBlank()) return null
            val parsedEntries = (raw["entries"] as? List<*>)
                ?.mapNotNull { PtmStudentCallEntry.fromMap(it as? Map<*, *>) }
                .orEmpty()
            return ParentMeetingRecord(
                id = docId,
                schoolId = schoolId,
                session = (raw["session"] as? String)?.trim().orEmpty(),
                title = (raw["title"] as? String)?.trim().orEmpty().ifBlank { "Parent-Teacher Meeting" },
                dateIso = dateIso,
                timeLabel = (raw["timeLabel"] as? String)?.trim().orEmpty().ifBlank { "10:00 AM - 1:00 PM" },
                className = (raw["className"] as? String)?.trim().orEmpty().ifBlank { "ALL" },
                section = (raw["section"] as? String)?.trim().orEmpty().ifBlank { "ALL" },
                agenda = (raw["agenda"] as? String)?.trim().orEmpty(),
                entries = parsedEntries,
                createdByUid = (raw["createdByUid"] as? String)?.trim().orEmpty(),
                createdByName = (raw["createdByName"] as? String)?.trim().orEmpty().ifBlank { "Teacher" },
                createdAtMs = (raw["createdAtMs"] as? Number)?.toLong() ?: 0L,
                updatedAtMs = (raw["updatedAtMs"] as? Number)?.toLong() ?: 0L,
                isPendingSync = isPendingSync
            )
        }
    }
}

// =============================================================================
// 3. GATE PASS, AUTHORISED PICKUP PERSONS & VISITOR REGISTER MODELS
// =============================================================================

data class AuthorizedPickupPerson(
    val id: String,
    val schoolId: String,
    val studentId: String,
    val studentAdmNo: String = "",
    val studentName: String,
    val className: String,
    val section: String,
    val personName: String,
    val relation: String,
    val mobile: String,
    val photoBase64: String = "",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val updatedAtMs: Long = 0L,
    val isPendingSync: Boolean = false
) {
    val hasPhoto: Boolean get() = photoBase64.isNotBlank()

    fun toFirestoreMap(
        createdAtTimestamp: Any,
        updatedAtTimestamp: Any
    ): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "studentId" to studentId.trim(),
        "studentAdmNo" to studentAdmNo.trim().take(40),
        "studentName" to studentName.trim().take(120),
        "className" to className.trim().take(40),
        "section" to section.trim().uppercase(Locale.US).ifBlank { "A" }.take(16),
        "personName" to personName.trim().take(120),
        "relation" to relation.trim().take(60),
        "mobile" to mobile.trim().take(24),
        "photoBase64" to photoBase64.take(260_000),
        "updatedByUid" to updatedByUid.trim().take(128),
        "updatedByName" to updatedByName.trim().ifBlank { "Staff" }.take(120),
        "updatedAtMs" to updatedAtMs,
        "createdAt" to createdAtTimestamp,
        "updatedAt" to updatedAtTimestamp
    )

    companion object {
        fun fromMap(docId: String, raw: Map<*, *>?, isPendingSync: Boolean = false): AuthorizedPickupPerson? {
            if (raw == null) return null
            val schoolId = (raw["schoolId"] as? String)?.trim().orEmpty()
            val studentId = (raw["studentId"] as? String)?.trim().orEmpty()
            val personName = (raw["personName"] as? String)?.trim().orEmpty()
            if (schoolId.isBlank() || studentId.isBlank() || personName.isBlank()) return null
            return AuthorizedPickupPerson(
                id = docId,
                schoolId = schoolId,
                studentId = studentId,
                studentAdmNo = (raw["studentAdmNo"] as? String)?.trim().orEmpty(),
                studentName = (raw["studentName"] as? String)?.trim().orEmpty(),
                className = (raw["className"] as? String)?.trim().orEmpty(),
                section = (raw["section"] as? String)?.trim().orEmpty().ifBlank { "A" },
                personName = personName,
                relation = (raw["relation"] as? String)?.trim().orEmpty().ifBlank { "Guardian" },
                mobile = (raw["mobile"] as? String)?.trim().orEmpty(),
                photoBase64 = (raw["photoBase64"] as? String).orEmpty(),
                updatedByUid = (raw["updatedByUid"] as? String)?.trim().orEmpty(),
                updatedByName = (raw["updatedByName"] as? String)?.trim().orEmpty(),
                updatedAtMs = (raw["updatedAtMs"] as? Number)?.toLong() ?: 0L,
                isPendingSync = isPendingSync
            )
        }
    }
}

data class GatePassRecord(
    val id: String,
    val passNumber: String,
    val schoolId: String,
    val session: String,
    val dateIso: String,
    val leaveTime: String,
    val studentId: String,
    val studentAdmNo: String,
    val studentName: String,
    val className: String,
    val section: String,
    val rollNo: Int = 0,
    val reason: String,
    val pickupPersonId: String = "",
    val pickupPersonName: String,
    val pickupRelation: String,
    val pickupMobile: String,
    val isOneTimePickup: Boolean = false,
    val oneTimeNote: String = "",
    val guardianPhoneConfirmed: Boolean = false,
    val approvedByUid: String,
    val approvedByName: String,
    val markedHalfDayAttendance: Boolean = false,
    val attendanceNote: String = "",
    val createdByUid: String,
    val createdByName: String,
    val createdAtMs: Long = 0L,
    val isPendingSync: Boolean = false
) {
    val classSectionLabel: String
        get() = "${className.trim()}-${section.trim().ifBlank { "A" }}"

    val pickupVerificationBadge: String
        get() = if (isOneTimePickup) {
            "One-Time (Guardian Phone Confirmed)"
        } else {
            "Authorised Pickup List"
        }

    fun toFirestoreMap(
        createdAtTimestamp: Any,
        updatedAtTimestamp: Any
    ): Map<String, Any> = mapOf(
        "passNumber" to passNumber.trim().take(48),
        "schoolId" to schoolId.trim(),
        "session" to session.trim(),
        "dateIso" to dateIso.trim().take(16),
        "leaveTime" to leaveTime.trim().take(24),
        "studentId" to studentId.trim(),
        "studentAdmNo" to studentAdmNo.trim().take(40),
        "studentName" to studentName.trim().take(120),
        "className" to className.trim().take(40),
        "section" to section.trim().uppercase(Locale.US).ifBlank { "A" }.take(16),
        "rollNo" to rollNo,
        "reason" to reason.trim().take(400),
        "pickupPersonId" to pickupPersonId.trim().take(128),
        "pickupPersonName" to pickupPersonName.trim().take(120),
        "pickupRelation" to pickupRelation.trim().take(60),
        "pickupMobile" to pickupMobile.trim().take(24),
        "isOneTimePickup" to isOneTimePickup,
        "oneTimeNote" to oneTimeNote.trim().take(300),
        "guardianPhoneConfirmed" to guardianPhoneConfirmed,
        "approvedByUid" to approvedByUid.trim().take(128),
        "approvedByName" to approvedByName.trim().ifBlank { "Principal / Office" }.take(120),
        "markedHalfDayAttendance" to markedHalfDayAttendance,
        "attendanceNote" to attendanceNote.trim().take(240),
        "createdByUid" to createdByUid.trim().take(128),
        "createdByName" to createdByName.trim().ifBlank { "Staff" }.take(120),
        "createdAtMs" to createdAtMs,
        "createdAt" to createdAtTimestamp,
        "updatedAt" to updatedAtTimestamp
    )

    companion object {
        fun fromMap(docId: String, raw: Map<*, *>?, isPendingSync: Boolean = false): GatePassRecord? {
            if (raw == null) return null
            val schoolId = (raw["schoolId"] as? String)?.trim().orEmpty()
            val passNumber = (raw["passNumber"] as? String)?.trim().orEmpty()
            val studentId = (raw["studentId"] as? String)?.trim().orEmpty()
            val dateIso = (raw["dateIso"] as? String)?.trim().orEmpty()
            if (schoolId.isBlank() || passNumber.isBlank() || studentId.isBlank() || dateIso.isBlank()) return null
            return GatePassRecord(
                id = docId,
                passNumber = passNumber,
                schoolId = schoolId,
                session = (raw["session"] as? String)?.trim().orEmpty(),
                dateIso = dateIso,
                leaveTime = (raw["leaveTime"] as? String)?.trim().orEmpty().ifBlank { "12:00 PM" },
                studentId = studentId,
                studentAdmNo = (raw["studentAdmNo"] as? String)?.trim().orEmpty(),
                studentName = (raw["studentName"] as? String)?.trim().orEmpty(),
                className = (raw["className"] as? String)?.trim().orEmpty(),
                section = (raw["section"] as? String)?.trim().orEmpty().ifBlank { "A" },
                rollNo = (raw["rollNo"] as? Number)?.toInt() ?: 0,
                reason = (raw["reason"] as? String)?.trim().orEmpty(),
                pickupPersonId = (raw["pickupPersonId"] as? String)?.trim().orEmpty(),
                pickupPersonName = (raw["pickupPersonName"] as? String)?.trim().orEmpty(),
                pickupRelation = (raw["pickupRelation"] as? String)?.trim().orEmpty(),
                pickupMobile = (raw["pickupMobile"] as? String)?.trim().orEmpty(),
                isOneTimePickup = (raw["isOneTimePickup"] as? Boolean) ?: false,
                oneTimeNote = (raw["oneTimeNote"] as? String)?.trim().orEmpty(),
                guardianPhoneConfirmed = (raw["guardianPhoneConfirmed"] as? Boolean) ?: false,
                approvedByUid = (raw["approvedByUid"] as? String)?.trim().orEmpty(),
                approvedByName = (raw["approvedByName"] as? String)?.trim().orEmpty().ifBlank { "Office" },
                markedHalfDayAttendance = (raw["markedHalfDayAttendance"] as? Boolean) ?: false,
                attendanceNote = (raw["attendanceNote"] as? String)?.trim().orEmpty(),
                createdByUid = (raw["createdByUid"] as? String)?.trim().orEmpty(),
                createdByName = (raw["createdByName"] as? String)?.trim().orEmpty().ifBlank { "Staff" },
                createdAtMs = (raw["createdAtMs"] as? Number)?.toLong() ?: 0L,
                isPendingSync = isPendingSync
            )
        }
    }
}

enum class VisitorStatus(val wireValue: String, val labelEn: String, val labelHi: String) {
    IN("IN", "Inside", "अंदर हैं (Inside)"),
    OUT("OUT", "Signed Out", "बाहर गए (Signed Out)");

    companion object {
        fun fromRaw(raw: String?): VisitorStatus =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: IN
    }
}

data class VisitorEntryRecord(
    val id: String,
    val schoolId: String,
    val dateIso: String,
    val visitorName: String,
    val mobile: String,
    val purpose: String,
    val whomToMeet: String,
    val timeIn: String,
    val timeOut: String = "",
    val status: VisitorStatus = VisitorStatus.IN,
    val photoBase64: String = "",
    val recordedByUid: String,
    val recordedByName: String,
    val signedOutByUid: String = "",
    val signedOutByName: String = "",
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val isPendingSync: Boolean = false
) {
    val isInside: Boolean get() = status == VisitorStatus.IN
    val hasPhoto: Boolean get() = photoBase64.isNotBlank()

    fun toFirestoreMap(
        createdAtTimestamp: Any,
        updatedAtTimestamp: Any
    ): Map<String, Any> = mapOf(
        "schoolId" to schoolId.trim(),
        "dateIso" to dateIso.trim().take(16),
        "visitorName" to visitorName.trim().take(120),
        "mobile" to mobile.trim().take(24),
        "purpose" to purpose.trim().take(300),
        "whomToMeet" to whomToMeet.trim().take(120),
        "timeIn" to timeIn.trim().take(24),
        "timeOut" to timeOut.trim().take(24),
        "status" to status.wireValue,
        "photoBase64" to photoBase64.take(260_000),
        "recordedByUid" to recordedByUid.trim().take(128),
        "recordedByName" to recordedByName.trim().ifBlank { "Gate" }.take(120),
        "signedOutByUid" to signedOutByUid.trim().take(128),
        "signedOutByName" to signedOutByName.trim().take(120),
        "createdAtMs" to createdAtMs,
        "updatedAtMs" to updatedAtMs,
        "createdAt" to createdAtTimestamp,
        "updatedAt" to updatedAtTimestamp
    )

    companion object {
        fun fromMap(docId: String, raw: Map<*, *>?, isPendingSync: Boolean = false): VisitorEntryRecord? {
            if (raw == null) return null
            val schoolId = (raw["schoolId"] as? String)?.trim().orEmpty()
            val dateIso = (raw["dateIso"] as? String)?.trim().orEmpty()
            val visitorName = (raw["visitorName"] as? String)?.trim().orEmpty()
            if (schoolId.isBlank() || dateIso.isBlank() || visitorName.isBlank()) return null
            return VisitorEntryRecord(
                id = docId,
                schoolId = schoolId,
                dateIso = dateIso,
                visitorName = visitorName,
                mobile = (raw["mobile"] as? String)?.trim().orEmpty(),
                purpose = (raw["purpose"] as? String)?.trim().orEmpty(),
                whomToMeet = (raw["whomToMeet"] as? String)?.trim().orEmpty(),
                timeIn = (raw["timeIn"] as? String)?.trim().orEmpty(),
                timeOut = (raw["timeOut"] as? String)?.trim().orEmpty(),
                status = VisitorStatus.fromRaw(raw["status"] as? String),
                photoBase64 = (raw["photoBase64"] as? String).orEmpty(),
                recordedByUid = (raw["recordedByUid"] as? String)?.trim().orEmpty(),
                recordedByName = (raw["recordedByName"] as? String)?.trim().orEmpty().ifBlank { "Gate" },
                signedOutByUid = (raw["signedOutByUid"] as? String)?.trim().orEmpty(),
                signedOutByName = (raw["signedOutByName"] as? String)?.trim().orEmpty(),
                createdAtMs = (raw["createdAtMs"] as? Number)?.toLong() ?: 0L,
                updatedAtMs = (raw["updatedAtMs"] as? Number)?.toLong() ?: 0L,
                isPendingSync = isPendingSync
            )
        }
    }
}

data class Phase19TodayGateSummary(
    val dateIso: String,
    val todayVisitorsTotal: Int,
    val todayVisitorsInside: Int,
    val todayVisitorsSignedOut: Int,
    val todayEarlyLeavesCount: Int
)

// =============================================================================
// 4. PHASE 19 ACCESS EVALUATOR (SENSITIVE REMARKS, PTM, GATE & PICKUP)
// =============================================================================

object Phase19AccessEvaluator {

    /**
     * Can the user open the Student Remarks & PTM module screen for [schoolId]?
     * - Owner and Admin: YES
     * - Member explicitly granted `studentRemarks` VIEW or EDIT: YES
     * - Class Teacher of any class-section in [schoolId] (even if `studentRemarks` is NONE): YES (for their class!)
     * - Other teachers / staff with `studentRemarks == NONE`: NO
     */
    fun canOpenStudentRemarksModule(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        if (OrgAccessEvaluator.canViewModule(member, AppModule.STUDENT_REMARKS)) return true
        val prefix = "${schoolId.trim()}|"
        return teacherScope?.classTeacherSections?.any { it.startsWith(prefix, ignoreCase = true) } == true
    }

    /**
     * Sensitive remark read rule:
     * By default ONLY:
     * 1. The Owner and Admins,
     * 2. The Class Teacher of that student's class-section (`classTeacherSections` contains `schoolId|className|section`),
     * 3. A user explicitly granted `studentRemarks` (`VIEW` or `EDIT`) in `MemberPermissions`
     * can read a student's remarks.
     * Other teachers (such as subject teachers of that class or class teachers of other classes) see NOTHING unless granted.
     */
    fun canViewStudentRemarksForClass(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        if (OrgAccessEvaluator.canViewModule(member, AppModule.STUDENT_REMARKS)) return true
        if (className.isBlank()) return false
        val cleanSection = section.trim().ifBlank { "A" }
        return teacherScope?.isClassTeacherOf(schoolId, className, cleanSection) == true
    }

    /**
     * Can the user add, complete follow-up on, or cancel a remark for a student in `(schoolId, className, section)`?
     * - Owner / Admin: YES
     * - Member explicitly granted `studentRemarks == EDIT`: YES
     * - Class Teacher of `(schoolId, className, section)` when their `studentRemarks` is NOT restricted to `VIEW`: YES
     * - Everyone else: NO
     */
    fun canEditStudentRemarksForClass(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        val level = member?.permissions?.levelFor(AppModule.STUDENT_REMARKS) ?: AccessLevel.NONE
        if (level == AccessLevel.EDIT) return true
        if (level == AccessLevel.VIEW) return false
        if (className.isBlank()) return false
        val cleanSection = section.trim().ifBlank { "A" }
        return teacherScope?.isClassTeacherOf(schoolId, className, cleanSection) == true
    }

    /**
     * Printable A4 Student Remark Summary PDF is strictly for Owner and Admins only.
     */
    fun canPrintStudentRemarkSummaryPdf(
        member: MemberAccessProfile?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        return OrgAccessEvaluator.isAdmin(member)
    }

    /**
     * Whether the UI should show the Printable A4 Student Remark Summary controls.
     * Owner and Admins always see it (and can toggle [hideFromNonAdmins]); non-admins never get print access,
     * and when [hideFromNonAdmins] is true, the card/action is completely hidden from non-admins.
     */
    fun shouldShowRemarkSummaryUi(
        member: MemberAccessProfile?,
        schoolId: String,
        hideFromNonAdmins: Boolean = true
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        return !hideFromNonAdmins
    }

    /**
     * Can the user view a PTM record?
     * - Owner / Admin / explicitly granted `studentRemarks` VIEW or EDIT: all PTMs in [schoolId].
     * - Class Teacher: PTMs for their own class-section or whole-school PTMs.
     */
    fun canViewParentMeeting(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        if (OrgAccessEvaluator.canViewModule(member, AppModule.STUDENT_REMARKS)) return true
        if (className.equals("ALL", ignoreCase = true)) {
            val prefix = "${schoolId.trim()}|"
            return teacherScope?.classTeacherSections?.any { it.startsWith(prefix, ignoreCase = true) } == true
        }
        return canViewStudentRemarksForClass(member, teacherScope, schoolId, className, section)
    }

    fun canEditParentMeeting(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        val level = member?.permissions?.levelFor(AppModule.STUDENT_REMARKS) ?: AccessLevel.NONE
        if (level == AccessLevel.EDIT) return true
        if (level == AccessLevel.VIEW) return false
        if (className.equals("ALL", ignoreCase = true)) return false
        return canEditStudentRemarksForClass(member, teacherScope, schoolId, className, section)
    }

    /**
     * Gate Module Access (`AppModule.GATE`):
     * - Controls Early Leave Gate Passes, Daily Gate Register, and Visitor Register.
     */
    fun canViewGateModule(
        member: MemberAccessProfile?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        return OrgAccessEvaluator.canViewModule(member, AppModule.GATE)
    }

    fun canEditGateModule(
        member: MemberAccessProfile?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        return OrgAccessEvaluator.canEditModule(member, AppModule.GATE)
    }

    /**
     * CSV export of the Daily Gate Register is restricted to Owner and Admins only.
     */
    fun canExportGateRegisterCsv(
        member: MemberAccessProfile?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        return OrgAccessEvaluator.isAdmin(member)
    }

    /**
     * Authorised Pickup Persons per student:
     * Stored in the Student Register (view and edit as that module allows) and readable by Gate module users
     * when issuing an early leave gate pass.
     */
    fun canViewAuthorizedPickups(
        member: MemberAccessProfile?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        return OrgAccessEvaluator.canViewModule(member, AppModule.STUDENT_REGISTER) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.STUDENTS) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.GATE)
    }

    fun canEditAuthorizedPickups(
        member: MemberAccessProfile?,
        schoolId: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolId)) return false
        if (OrgAccessEvaluator.isAdmin(member)) return true
        return OrgAccessEvaluator.canEditModule(member, AppModule.STUDENT_REGISTER)
    }

    /**
     * Optional link from Early Leave Gate Pass to mark the student Half-Day in Attendance:
     * Allowed ONLY if the current user has Attendance edit access for that student's class-section.
     */
    fun canMarkHalfDayFromGatePass(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        className: String,
        section: String
    ): Boolean = StudentAttendanceAccessEvaluator.canEditSectionAttendance(
        member = member,
        teacherScope = teacherScope,
        schoolId = schoolId,
        className = className,
        section = section
    )
}

// =============================================================================
// 5. PHASE 19 CORE ENGINE (REMARKS, PTM, GATE PASS NUMBERING, CSV & VISITORS)
// =============================================================================

object Phase19RemarksPtmAndGateEngine {

    // -------------------------------------------------------------------------
    // A. STUDENT REMARKS VALIDATION, CANCELLATION & FILTERING
    // -------------------------------------------------------------------------

    fun validateNewRemark(
        studentId: String,
        dateIso: String,
        category: String,
        note: String,
        followUpDateIso: String
    ): String? {
        if (studentId.isBlank()) return "Please select a student (कृपया विद्यार्थी चुनें)."
        if (!Phase6DateUtils.isValidIsoDate(dateIso)) return "Please enter a valid date (YYYY-MM-DD)."
        if (category.isBlank()) return "Please choose a remark category (श्रेणी चुनें)."
        if (note.trim().length < 2) return "Please write a short remark note (टिप्पणी लिखें)."
        if (followUpDateIso.isNotBlank() && !Phase6DateUtils.isValidIsoDate(followUpDateIso)) {
            return "Invalid follow-up date format (YYYY-MM-DD)."
        }
        if (followUpDateIso.isNotBlank() && followUpDateIso < dateIso) {
            return "Follow-up date cannot be earlier than the remark date."
        }
        return null
    }

    /**
     * Remarks cannot be silently deleted: they are cancelled with a mandatory reason
     * and stay in the history and audit log.
     */
    fun cancelRemarkWithReason(
        existing: StudentRemarkRecord,
        reason: String,
        actorUid: String,
        actorName: String,
        nowMs: Long
    ): Result<Pair<StudentRemarkRecord, String>> {
        if (existing.isCancelled) {
            return Result.failure(IllegalStateException("This remark is already cancelled."))
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < 3) {
            return Result.failure(
                IllegalArgumentException("Please enter a cancellation reason (रद्द करने का कारण लिखें).")
            )
        }
        val updated = existing.copy(
            status = StudentRemarkStatus.CANCELLED,
            cancelledReason = cleanReason.take(500),
            cancelledByUid = actorUid.trim(),
            cancelledByName = actorName.trim().ifBlank { "Staff" },
            cancelledAtMs = nowMs,
            updatedAtMs = nowMs
        )
        val auditMessage = "Cancelled student remark for ${existing.studentName} (${existing.classSectionLabel}, " +
            "Adm ${existing.studentAdmNo.ifBlank { existing.studentId }}) [${existing.category} on ${existing.dateIso}]. " +
            "Reason: $cleanReason"
        return Result.success(updated to auditMessage)
    }

    /**
     * Filters remarks so the viewer only ever receives remarks they have permission to read,
     * and applies optional UI filters (student, class, section, category, date range, cancelled toggle).
     */
    fun filterRemarksForViewer(
        allRemarks: List<StudentRemarkRecord>,
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolId: String,
        session: String? = null,
        studentIdFilter: String? = null,
        classFilter: String? = null,
        sectionFilter: String? = null,
        categoryFilter: String? = null,
        fromDateIso: String? = null,
        toDateIso: String? = null,
        includeCancelled: Boolean = true
    ): List<StudentRemarkRecord> {
        return allRemarks
            .filter { r ->
                r.schoolId == schoolId &&
                    (session.isNullOrBlank() || r.session.isBlank() || r.session.equals(session, ignoreCase = true)) &&
                    Phase19AccessEvaluator.canViewStudentRemarksForClass(
                        member = member,
                        teacherScope = teacherScope,
                        schoolId = r.schoolId,
                        className = r.className,
                        section = r.section
                    ) &&
                    (includeCancelled || r.isActive) &&
                    (studentIdFilter.isNullOrBlank() || r.studentId == studentIdFilter) &&
                    (classFilter.isNullOrBlank() || classFilter.equals("ALL", ignoreCase = true) ||
                        r.className.equals(classFilter.trim(), ignoreCase = true)) &&
                    (sectionFilter.isNullOrBlank() || sectionFilter.equals("ALL", ignoreCase = true) ||
                        r.section.equals(sectionFilter.trim(), ignoreCase = true)) &&
                    (categoryFilter.isNullOrBlank() || categoryFilter.equals("ALL", ignoreCase = true) ||
                        r.category.equals(categoryFilter.trim(), ignoreCase = true)) &&
                    (fromDateIso.isNullOrBlank() || r.dateIso >= fromDateIso.trim()) &&
                    (toDateIso.isNullOrBlank() || r.dateIso <= toDateIso.trim())
            }
            .sortedWith(
                compareByDescending<StudentRemarkRecord> { it.dateIso }
                    .thenByDescending { it.createdAtMs }
            )
    }

    /**
     * Returns active remarks that have a follow-up date set and not yet marked completed,
     * sorted with earliest / overdue follow-up dates first.
     */
    fun buildFollowUpsDueList(
        visibleRemarks: List<StudentRemarkRecord>
    ): List<StudentRemarkRecord> {
        return visibleRemarks
            .filter { it.hasFollowUpDue }
            .sortedWith(
                compareBy<StudentRemarkRecord> { it.followUpDateIso }
                    .thenBy { it.className }
                    .thenBy { it.section }
                    .thenBy { it.studentName.lowercase(Locale.US) }
            )
    }

    /**
     * Counts ACTIVE remarks per category for quick summary chips.
     */
    fun computeCategoryCounts(
        visibleRemarks: List<StudentRemarkRecord>,
        configuredCategories: List<String> = DefaultRemarkCategory.defaultCategoryNames
    ): Map<String, Int> {
        val active = visibleRemarks.filter { it.isActive }
        val result = linkedMapOf<String, Int>()
        configuredCategories.forEach { cat ->
            result[cat] = 0
        }
        active.forEach { r ->
            val matchingKey = result.keys.firstOrNull { it.equals(r.category, ignoreCase = true) } ?: r.category
            result[matchingKey] = (result[matchingKey] ?: 0) + 1
        }
        return result
    }

    // -------------------------------------------------------------------------
    // B. PARENT MEETINGS (PTM) SUGGESTIONS, TRACKING & WHATSAPP INVITATION
    // -------------------------------------------------------------------------

    /**
     * Builds smart PTM suggestions combining:
     * 1. Weak students from Result Analysis (`WeakStudentRow` list where student failed 1+ subject),
     * 2. Low attendance students (`LowAttendanceStudentRow` list where attendance < threshold),
     * 3. Students with active remarks in Discipline, Academic, Attendance, Health or due follow-ups.
     */
    fun buildPtmStudentSuggestions(
        students: List<Pair<String, StudentEntity>>, // (studentDocId, student)
        weakStudents: List<WeakStudentRow> = emptyList(),
        lowAttendanceRows: List<LowAttendanceStudentRow> = emptyList(),
        visibleRemarks: List<StudentRemarkRecord> = emptyList(),
        classFilter: String? = null,
        sectionFilter: String? = null
    ): List<PtmStudentSuggestion> {
        val weakByStudentLongId = weakStudents.associateBy { it.student.id }
        val weakByRollAndClass = weakStudents.associateBy {
            "${it.student.className.trim().lowercase(Locale.US)}|${it.student.section.trim().ifBlank { "A" }.uppercase(Locale.US)}|${it.student.rollNo}"
        }
        val lowAttByDocId = lowAttendanceRows.associateBy { it.studentDocId }
        val lowAttByLongId = lowAttendanceRows.associateBy { it.student.id }
        val activeRemarksByStudent = visibleRemarks
            .filter { it.isActive }
            .groupBy { it.studentId }

        return students
            .filter { (_, st) ->
                (classFilter.isNullOrBlank() || classFilter.equals("ALL", ignoreCase = true) ||
                    st.className.equals(classFilter.trim(), ignoreCase = true)) &&
                    (sectionFilter.isNullOrBlank() || sectionFilter.equals("ALL", ignoreCase = true) ||
                        st.section.equals(sectionFilter.trim(), ignoreCase = true))
            }
            .mapNotNull { (docId, st) ->
                val reasons = mutableListOf<String>()
                val sectionKey = "${st.className.trim().lowercase(Locale.US)}|${st.section.trim().ifBlank { "A" }.uppercase(Locale.US)}|${st.rollNo}"
                val weakRow = weakByStudentLongId[st.id] ?: weakByRollAndClass[sectionKey]
                val isWeak = weakRow != null && (weakRow.failedSubjects.isNotEmpty() || weakRow.lowScoreSubjects.isNotEmpty())
                if (weakRow != null) {
                    if (weakRow.failedSubjects.isNotEmpty()) {
                        val failedNames = weakRow.failedSubjects.take(3).joinToString(", ")
                        reasons.add(
                            "Weak Result: Failed ${weakRow.failedSubjects.size} subj [$failedNames] (${String.format(Locale.US, "%.1f%%", weakRow.percentage)})"
                        )
                    } else if (weakRow.lowScoreSubjects.isNotEmpty()) {
                        val lowNames = weakRow.lowScoreSubjects.take(3).joinToString(", ")
                        reasons.add(
                            "Low Score: [$lowNames] (${String.format(Locale.US, "%.1f%%", weakRow.percentage)})"
                        )
                    }
                }

                val lowAtt = lowAttByDocId[docId] ?: lowAttByLongId[st.id]
                val isLowAtt = lowAtt != null
                if (lowAtt != null) {
                    reasons.add(
                        "Low Attendance: ${String.format(Locale.US, "%.1f%%", lowAtt.attendancePercent)} " +
                            "(${lowAtt.absentCount} absent)"
                    )
                }

                val studentRemarks = activeRemarksByStudent[docId].orEmpty()
                val concernRemarks = studentRemarks.filter { r ->
                    !r.category.equals(DefaultRemarkCategory.PRAISE.labelEn, ignoreCase = true)
                }
                val hasConcern = concernRemarks.isNotEmpty()
                if (concernRemarks.isNotEmpty()) {
                    val cats = concernRemarks.map { it.category }.distinct().joinToString("/")
                    reasons.add("${concernRemarks.size} Remark(s) ($cats)")
                }

                if (reasons.isEmpty()) return@mapNotNull null

                PtmStudentSuggestion(
                    studentId = docId,
                    studentAdmNo = st.admNo,
                    studentName = st.name,
                    className = st.className,
                    section = st.section.ifBlank { "A" },
                    rollNo = st.rollNo,
                    guardianName = st.fatherName.ifBlank { st.motherName },
                    mobile = st.mobile,
                    reasons = reasons,
                    isWeakInResult = isWeak,
                    isLowAttendance = isLowAtt,
                    hasConcernRemarks = hasConcern
                )
            }
            .sortedWith(
                compareByDescending<PtmStudentSuggestion> {
                    (if (it.isWeakInResult) 4 else 0) +
                        (if (it.isLowAttendance) 2 else 0) +
                        (if (it.hasConcernRemarks) 1 else 0)
                }
                    .thenBy { it.className }
                    .thenBy { it.section }
                    .thenBy { it.rollNo }
            )
    }

    /**
     * Builds a clean, polite, ready-to-share PTM invitation text message (never sent automatically).
     */
    fun buildPtmInvitationMessage(
        schoolName: String,
        meeting: ParentMeetingRecord,
        entry: PtmStudentCallEntry,
        useHindi: Boolean = true
    ): String {
        val cleanSchool = schoolName.trim().ifBlank { "School" }
        val humanDate = SchoolCalendarEngine.formatShortHumanDate(meeting.dateIso)
        val classSec = "${entry.className.trim()}-${entry.section.trim().ifBlank { "A" }}"
        val guardianGreeting = if (entry.guardianName.isNotBlank()) {
            "आदरणीय ${entry.guardianName.trim()} जी / Dear ${entry.guardianName.trim()},"
        } else {
            "आदरणीय अभिभावक / Dear Parent,"
        }
        return if (useHindi) {
            buildString {
                appendLine("*$cleanSchool — अभिभावक-शिक्षक बैठक (PTM) आमंत्रण*")
                appendLine(guardianGreeting)
                appendLine(
                    "आपके बच्चे *${entry.studentName.trim()}* (कक्षा $classSec) की प्रगति पर चर्चा हेतु विद्यालय में अभिभावक बैठक रखी गई है।"
                )
                appendLine("• दिनांक (Date): *$humanDate* (${meeting.dateIso})")
                appendLine("• समय (Time): *${meeting.timeLabel.trim()}*")
                if (meeting.agenda.isNotBlank()) {
                    appendLine("• विषय (Agenda): ${meeting.agenda.trim()}")
                }
                append("कृपया समय पर विद्यालय पधारने का कष्ट करें। धन्यवाद — $cleanSchool")
            }
        } else {
            buildString {
                appendLine("*$cleanSchool — Parent-Teacher Meeting (PTM) Invitation*")
                appendLine("Dear Parent${if (entry.guardianName.isNotBlank()) " (${entry.guardianName.trim()})" else ""},")
                appendLine(
                    "You are cordially invited to attend the Parent-Teacher Meeting for your ward *${entry.studentName.trim()}* (Class $classSec)."
                )
                appendLine("• Date: *$humanDate* (${meeting.dateIso})")
                appendLine("• Time: *${meeting.timeLabel.trim()}*")
                if (meeting.agenda.isNotBlank()) {
                    appendLine("• Agenda: ${meeting.agenda.trim()}")
                }
                append("Please attend on time. Thank you — $cleanSchool")
            }
        }
    }

    /**
     * Builds a user-initiated WhatsApp deep link (`https://wa.me/91XXXXXXXXXX?text=...`).
     * Never sends anything automatically.
     */
    fun buildPtmWhatsAppUrl(
        rawMobile: String,
        messageText: String
    ): String? {
        val digits = rawMobile.filter { it.isDigit() }
        val normalizedPhone = when {
            digits.length == 10 -> "91$digits"
            digits.length == 12 && digits.startsWith("91") -> digits
            digits.length in 11..15 -> digits
            else -> return null
        }
        val encodedText = try {
            URLEncoder.encode(messageText, "UTF-8")
        } catch (_: Exception) {
            messageText.replace(" ", "%20")
        }
        return "https://wa.me/$normalizedPhone?text=$encodedText"
    }

    // -------------------------------------------------------------------------
    // C. GATE PASS VALIDATION, OFFLINE-SAFE UNIQUE PASS NUMBER & CSV EXPORT
    // -------------------------------------------------------------------------

    fun validateGatePassInput(
        studentId: String,
        dateIso: String,
        leaveTime: String,
        reason: String,
        pickupPersonName: String,
        pickupRelation: String,
        isOneTimePickup: Boolean,
        guardianPhoneConfirmed: Boolean,
        approvedByName: String
    ): String? {
        if (studentId.isBlank()) return "Please select a student (विद्यार्थी चुनें)."
        if (!Phase6DateUtils.isValidIsoDate(dateIso)) return "Please enter a valid date (YYYY-MM-DD)."
        if (leaveTime.trim().isBlank()) return "Please enter the leave time (जाने का समय लिखें)."
        if (reason.trim().length < 2) return "Please enter the reason for early leave (छुट्टी का कारण लिखें)."
        if (pickupPersonName.trim().length < 2) {
            return "Please select or enter the person taking the child (ले जाने वाले व्यक्ति का नाम लिखें)."
        }
        if (pickupRelation.trim().isBlank()) {
            return "Please enter relation with the student (रिश्ता लिखें)."
        }
        if (isOneTimePickup && !guardianPhoneConfirmed) {
            return "For a one-time pickup person, you must confirm guardian phone confirmation (अभिभावक से फोन पर पुष्टि अनिवार्य है)."
        }
        if (approvedByName.trim().isBlank()) {
            return "Please enter who approved this gate pass (अनुमति देने वाले का नाम)."
        }
        return null
    }

    /**
     * Generates a unique gate pass number per school and session, guaranteed not to collide
     * even when two phones are completely offline at the same time.
     *
     * Format: `GP-<SES4>-<MMDD>-<SHARD4>-<SEQ3>`
     * Example: `GP-2627-0818-K9F2-001`
     *
     * - `<SES4>`: compact session code (e.g. "2026-2027" -> "2627").
     * - `<MMDD>`: month and day from [dateIso] (e.g. "2026-08-18" -> "0818").
     * - `<SHARD4>`: 4-char base-36 shard derived from `(schoolId + deviceOrActorKey + sub-second entropy)`
     *   so two different offline phones always produce distinct shards.
     * - `<SEQ3>`: 3-digit monotonic sequence number incremented past any existing pass numbers
     *   known on the device for that school & session.
     */
    fun generateUniqueGatePassNumber(
        schoolId: String,
        session: String,
        dateIso: String,
        deviceOrActorKey: String,
        existingPassNumbersInSchoolSession: Collection<String> = emptyList(),
        timestampMs: Long = System.currentTimeMillis(),
        localSequenceHint: Int = 0
    ): String {
        val (startYr, endYr) = SchoolCalendarEngine.parseSessionYears(session)
        val sesCode = String.format(Locale.US, "%02d%02d", startYr % 100, endYr % 100)
        val cleanDate = dateIso.trim()
        val mmDd = if (cleanDate.length >= 10 && cleanDate[4] == '-' && cleanDate[7] == '-') {
            cleanDate.substring(5, 7) + cleanDate.substring(8, 10)
        } else {
            "0101"
        }

        // Deterministic 4-char device/actor + sub-day entropy shard in Base36
        val deviceClean = deviceOrActorKey.trim().ifBlank { "DEV0" }
        var hash = 2166136261L
        val seedInput = "${schoolId.trim()}|$deviceClean|${timestampMs % 1_000_000L}"
        for (ch in seedInput) {
            hash = hash xor ch.code.toLong()
            hash = (hash * 16777619L) and 0x7FFFFFFFL
        }
        val shard4 = abs(hash)
            .toString(36)
            .uppercase(Locale.US)
            .padStart(4, '0')
            .takeLast(4)

        val existingUpper = existingPassNumbersInSchoolSession
            .map { it.trim().uppercase(Locale.US) }
            .toSet()

        var seq = (existingUpper.size + 1 + localSequenceHint.coerceAtLeast(0)).coerceAtLeast(1)
        while (true) {
            val seqStr = String.format(Locale.US, "%03d", seq)
            val candidate = "GP-$sesCode-$mmDd-$shard4-$seqStr"
            if (candidate !in existingUpper) {
                return candidate
            }
            seq++
        }
    }

    fun filterGatePasses(
        passes: List<GatePassRecord>,
        schoolId: String,
        session: String? = null,
        fromDateIso: String? = null,
        toDateIso: String? = null,
        classFilter: String? = null,
        sectionFilter: String? = null
    ): List<GatePassRecord> {
        return passes
            .filter { p ->
                p.schoolId == schoolId &&
                    (session.isNullOrBlank() || p.session.isBlank() || p.session.equals(session, ignoreCase = true)) &&
                    (fromDateIso.isNullOrBlank() || p.dateIso >= fromDateIso.trim()) &&
                    (toDateIso.isNullOrBlank() || p.dateIso <= toDateIso.trim()) &&
                    (classFilter.isNullOrBlank() || classFilter.equals("ALL", ignoreCase = true) ||
                        p.className.equals(classFilter.trim(), ignoreCase = true)) &&
                    (sectionFilter.isNullOrBlank() || sectionFilter.equals("ALL", ignoreCase = true) ||
                        p.section.equals(sectionFilter.trim(), ignoreCase = true))
            }
            .sortedWith(
                compareByDescending<GatePassRecord> { it.dateIso }
                    .thenByDescending { it.createdAtMs }
            )
    }

    /**
     * Builds RFC-4180 CSV export of the Daily Gate Register (restricted to Owner and Admins).
     */
    fun buildGateRegisterCsv(
        passes: List<GatePassRecord>,
        schoolName: String
    ): String {
        fun esc(v: String): String {
            val clean = v.replace("\"", "\"\"")
            return if (clean.contains(',') || clean.contains('"') || clean.contains('\n') || clean.contains('\r')) {
                "\"$clean\""
            } else {
                clean
            }
        }
        val sb = StringBuilder()
        sb.appendLine(
            "School,Pass Number,Date,Leave Time,Admission No,Student Name,Class,Section,Reason,Pickup Person,Relation,Mobile,Pickup Verification,Guardian Phone Confirmed,Approved By,Half-Day Attendance Marked"
        )
        for (p in passes) {
            val pickupType = if (p.isOneTimePickup) "One-Time Pickup" else "Authorised List"
            val phoneConfirmed = if (p.isOneTimePickup) {
                if (p.guardianPhoneConfirmed) "Yes" else "No"
            } else {
                "Pre-Authorised"
            }
            sb.appendLine(
                listOf(
                    esc(schoolName),
                    esc(p.passNumber),
                    esc(p.dateIso),
                    esc(p.leaveTime),
                    esc(p.studentAdmNo),
                    esc(p.studentName),
                    esc(p.className),
                    esc(p.section),
                    esc(p.reason),
                    esc(p.pickupPersonName),
                    esc(p.pickupRelation),
                    esc(p.pickupMobile),
                    esc(pickupType),
                    esc(phoneConfirmed),
                    esc(p.approvedByName),
                    if (p.markedHalfDayAttendance) "Yes (Half-Day)" else "No"
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // D. VISITOR REGISTER & TODAY HOME SUMMARY TILE
    // -------------------------------------------------------------------------

    fun validateVisitorEntry(
        visitorName: String,
        mobile: String,
        purpose: String,
        whomToMeet: String,
        dateIso: String,
        timeIn: String
    ): String? {
        if (visitorName.trim().length < 2) return "Please enter visitor name (आगंतुक का नाम लिखें)."
        val digits = mobile.filter { it.isDigit() }
        if (digits.isNotEmpty() && digits.length < 10) {
            return "Please enter a valid 10-digit mobile number."
        }
        if (purpose.trim().length < 2) return "Please enter purpose of visit (आने का कारण लिखें)."
        if (whomToMeet.trim().isBlank()) return "Please enter whom to meet (किससे मिलना है)."
        if (!Phase6DateUtils.isValidIsoDate(dateIso)) return "Invalid date format (YYYY-MM-DD)."
        if (timeIn.trim().isBlank()) return "Please enter time in (आने का समय)."
        return null
    }

    fun signOutVisitor(
        existing: VisitorEntryRecord,
        timeOut: String,
        actorUid: String,
        actorName: String,
        nowMs: Long
    ): VisitorEntryRecord {
        return existing.copy(
            timeOut = timeOut.trim().ifBlank { "Signed Out" },
            status = VisitorStatus.OUT,
            signedOutByUid = actorUid.trim(),
            signedOutByName = actorName.trim().ifBlank { "Gate" },
            updatedAtMs = nowMs
        )
    }

    fun buildTodayGateSummary(
        schoolId: String,
        todayIso: String,
        visitorEntries: List<VisitorEntryRecord>,
        gatePasses: List<GatePassRecord>
    ): Phase19TodayGateSummary {
        val todayVisitors = visitorEntries.filter { it.schoolId == schoolId && it.dateIso == todayIso }
        val inside = todayVisitors.count { it.status == VisitorStatus.IN }
        val signedOut = todayVisitors.count { it.status == VisitorStatus.OUT }
        val todayPasses = gatePasses.count { it.schoolId == schoolId && it.dateIso == todayIso }
        return Phase19TodayGateSummary(
            dateIso = todayIso,
            todayVisitorsTotal = todayVisitors.size,
            todayVisitorsInside = inside,
            todayVisitorsSignedOut = signedOut,
            todayEarlyLeavesCount = todayPasses
        )
    }
}
