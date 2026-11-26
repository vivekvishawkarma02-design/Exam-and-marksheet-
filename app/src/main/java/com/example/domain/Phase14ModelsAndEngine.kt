package com.example.domain

import com.example.data.ClassGroupEntity
import com.example.data.MarkEntity
import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.data.SubjectEntity
import com.example.data.cloud.StableDocId
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * PHASE 14: NEW SESSION & PROMOTION, BULK STUDENT IMPORT (CSV & XLSX), BACKUP ZIP EXPORT
 * Pure Kotlin domain models and engines (zero Firebase/Android UI coupling so all rules,
 * calculations, promotion batches, XLSX/CSV parsing, and ZIP backups are fast-tested with JUnit).
 *
 * CRITICAL INVARIANTS:
 * - Reuses [ReportCalculations.calculateStudentPerformance] directly for promotion decisions
 *   (exact 33% per-subject rule; never duplicates or alters grading logic).
 * - Reuses [FeeCollectionEngine.computeStudentFeeAccount] directly for unpaid dues carry-forward.
 * - Reuses [PhoneNormalizer.normalizeIndianMobile] for +91 mobile normalisation.
 * - Never modifies past-session student, mark, attendance, or receipt records.
 * - Bulk Import and Backup Export are Owner & Admin ONLY; Salary CSV inside backup is Owner ONLY;
 *   7-day Undo of session start is Owner ONLY.
 */

// ============================================================================
// PART A: ACCESS EVALUATOR (Phase 14 RBAC)
// ============================================================================

object Phase14AccessEvaluator {

    fun canViewSessionPromotion(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canViewModule(member, AppModule.SESSION_PROMOTION)
    }

    fun canEditSessionPromotion(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canEditModule(member, AppModule.SESSION_PROMOTION)
    }

    /** Bulk Student Import is restricted strictly to Owner and Admins. */
    fun canUseBulkStudentImport(member: MemberAccessProfile?): Boolean =
        OrgAccessEvaluator.isAdmin(member)

    /** Backup & Export ZIP is restricted strictly to Owner and Admins. */
    fun canUseBackupExport(member: MemberAccessProfile?): Boolean =
        OrgAccessEvaluator.isAdmin(member)

    /** Staff Salary CSV in backup ZIP is restricted strictly to the Organisation Owner. */
    fun canIncludeSalaryInBackup(member: MemberAccessProfile?): Boolean =
        OrgAccessEvaluator.isOwner(member)

    /** 7-day Undo of a session start is restricted strictly to the Organisation Owner. */
    fun canUndoSessionStart(member: MemberAccessProfile?): Boolean =
        OrgAccessEvaluator.isOwner(member)
}

// ============================================================================
// PART B: SESSION, CLASS ORDER & PROMOTION MODELS
// ============================================================================

data class SessionDateRange(
    val sessionName: String,
    val startDateIso: String,
    val endDateIso: String
)

data class SessionCopyOptions(
    val copyClassAndSections: Boolean = true,
    val copySubjectsPerClass: Boolean = true,
    val copyFeeStructure: Boolean = true,
    val copyPeriodAndTimetableSettings: Boolean = true,
    val copySchoolCalendar: Boolean = true,
    val copyTransportRoutes: Boolean = true,
    val copyStaffList: Boolean = true
) {
    fun toMap(): Map<String, Any> = mapOf(
        "copyClassAndSections" to copyClassAndSections,
        "copySubjectsPerClass" to copySubjectsPerClass,
        "copyFeeStructure" to copyFeeStructure,
        "copyPeriodAndTimetableSettings" to copyPeriodAndTimetableSettings,
        "copySchoolCalendar" to copySchoolCalendar,
        "copyTransportRoutes" to copyTransportRoutes,
        "copyStaffList" to copyStaffList
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): SessionCopyOptions {
            if (raw == null) return SessionCopyOptions()
            return SessionCopyOptions(
                copyClassAndSections = (raw["copyClassAndSections"] as? Boolean) ?: true,
                copySubjectsPerClass = (raw["copySubjectsPerClass"] as? Boolean) ?: true,
                copyFeeStructure = (raw["copyFeeStructure"] as? Boolean) ?: true,
                copyPeriodAndTimetableSettings = (raw["copyPeriodAndTimetableSettings"] as? Boolean) ?: true,
                copySchoolCalendar = (raw["copySchoolCalendar"] as? Boolean) ?: true,
                copyTransportRoutes = (raw["copyTransportRoutes"] as? Boolean) ?: true,
                copyStaffList = (raw["copyStaffList"] as? Boolean) ?: true
            )
        }
    }
}

data class SchoolClassOrderConfig(
    val schoolId: String,
    val orderedClassNames: List<String>,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(actorUid: String, actorName: String, nowMs: Long, updatedAtValue: Any): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "orderedClassNames" to orderedClassNames.map { it.trim().take(40) }.filter { it.isNotEmpty() }.distinct().take(40),
        "updatedByUid" to actorUid,
        "updatedByName" to actorName.trim().take(120),
        "clientEditedAtMs" to nowMs.coerceAtLeast(0L),
        "updatedAt" to updatedAtValue
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): SchoolClassOrderConfig? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty().ifBlank { docId }
            val names = (data["orderedClassNames"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                .orEmpty()
            if (schoolId.isEmpty() || names.isEmpty()) return null
            return SchoolClassOrderConfig(
                schoolId = schoolId,
                orderedClassNames = names,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

enum class SessionTransitionStatus(val wireValue: String, val labelEn: String, val labelHi: String) {
    IN_PROGRESS("IN_PROGRESS", "In Progress (Resumable)", "प्रगति में (फिर से शुरू करें)"),
    COMPLETED("COMPLETED", "Completed", "पूर्ण"),
    ROLLED_BACK("ROLLED_BACK", "Rolled Back", "रोलबैक किया गया"),
    UNDONE("UNDONE", "Undone", "वापस लिया गया");

    companion object {
        fun fromRaw(raw: String?): SessionTransitionStatus =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: COMPLETED
    }
}

data class SessionTransitionRecord(
    val id: String,
    val schoolId: String,
    val fromSession: String,
    val toSession: String,
    val startDateIso: String,
    val endDateIso: String,
    val status: SessionTransitionStatus,
    val copyOptions: SessionCopyOptions,
    val totalStudentsEvaluated: Int = 0,
    val promotedCount: Int = 0,
    val gracePromotedCount: Int = 0,
    val detainedCount: Int = 0,
    val leftCount: Int = 0,
    val passedOutCount: Int = 0,
    val totalDuesCarriedForwardRs: Int = 0,
    val totalBatches: Int = 0,
    val completedBatches: Int = 0,
    val createdStudentDocIds: List<String> = emptyList(),
    val createdFeeOverrideDocIds: List<String> = emptyList(),
    val createdPromotionRecordIds: List<String> = emptyList(),
    val createdFeeStructureDocId: String = "",
    val createdCalendarDocId: String = "",
    val previousCurrentSession: String = "",
    val previousSessionsCsv: String = "",
    val appliedByUid: String = "",
    val appliedByName: String = "",
    val appliedAtMs: Long = 0L,
    val undoExpiresAtMs: Long = 0L,
    val undoneByUid: String = "",
    val undoneByName: String = "",
    val undoneAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(updatedAtValue: Any): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "fromSession" to fromSession.trim().take(30),
        "toSession" to toSession.trim().take(30),
        "startDateIso" to startDateIso.trim().take(16),
        "endDateIso" to endDateIso.trim().take(16),
        "status" to status.wireValue,
        "copyOptions" to copyOptions.toMap(),
        "totalStudentsEvaluated" to totalStudentsEvaluated.coerceAtLeast(0).toLong(),
        "promotedCount" to promotedCount.coerceAtLeast(0).toLong(),
        "gracePromotedCount" to gracePromotedCount.coerceAtLeast(0).toLong(),
        "detainedCount" to detainedCount.coerceAtLeast(0).toLong(),
        "leftCount" to leftCount.coerceAtLeast(0).toLong(),
        "passedOutCount" to passedOutCount.coerceAtLeast(0).toLong(),
        "totalDuesCarriedForwardRs" to totalDuesCarriedForwardRs.coerceAtLeast(0).toLong(),
        "totalBatches" to totalBatches.coerceAtLeast(0).toLong(),
        "completedBatches" to completedBatches.coerceAtLeast(0).toLong(),
        "createdStudentDocIds" to createdStudentDocIds.distinct().take(3000),
        "createdFeeOverrideDocIds" to createdFeeOverrideDocIds.distinct().take(3000),
        "createdPromotionRecordIds" to createdPromotionRecordIds.distinct().take(3000),
        "createdFeeStructureDocId" to createdFeeStructureDocId.trim().take(128),
        "createdCalendarDocId" to createdCalendarDocId.trim().take(128),
        "previousCurrentSession" to previousCurrentSession.trim().take(30),
        "previousSessionsCsv" to previousSessionsCsv.trim().take(500),
        "appliedByUid" to appliedByUid.trim().take(128),
        "appliedByName" to appliedByName.trim().take(120),
        "appliedAtMs" to appliedAtMs.coerceAtLeast(0L),
        "undoExpiresAtMs" to undoExpiresAtMs.coerceAtLeast(0L),
        "undoneByUid" to undoneByUid.trim().take(128),
        "undoneByName" to undoneByName.trim().take(120),
        "undoneAtMs" to undoneAtMs.coerceAtLeast(0L),
        "updatedAt" to updatedAtValue
    )

    companion object {
        fun makeDocId(schoolId: String, fromSession: String, toSession: String): String =
            "strans_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(fromSession)}_${StableDocId.sanitizeSegment(toSession)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): SessionTransitionRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val fromSession = (data["fromSession"] as? String)?.trim().orEmpty()
            val toSession = (data["toSession"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || fromSession.isEmpty() || toSession.isEmpty()) return null
            return SessionTransitionRecord(
                id = docId,
                schoolId = schoolId,
                fromSession = fromSession,
                toSession = toSession,
                startDateIso = (data["startDateIso"] as? String).orEmpty(),
                endDateIso = (data["endDateIso"] as? String).orEmpty(),
                status = SessionTransitionStatus.fromRaw(data["status"] as? String),
                copyOptions = SessionCopyOptions.fromMap(data["copyOptions"] as? Map<*, *>),
                totalStudentsEvaluated = (data["totalStudentsEvaluated"] as? Number)?.toInt() ?: 0,
                promotedCount = (data["promotedCount"] as? Number)?.toInt() ?: 0,
                gracePromotedCount = (data["gracePromotedCount"] as? Number)?.toInt() ?: 0,
                detainedCount = (data["detainedCount"] as? Number)?.toInt() ?: 0,
                leftCount = (data["leftCount"] as? Number)?.toInt() ?: 0,
                passedOutCount = (data["passedOutCount"] as? Number)?.toInt() ?: 0,
                totalDuesCarriedForwardRs = (data["totalDuesCarriedForwardRs"] as? Number)?.toInt() ?: 0,
                totalBatches = (data["totalBatches"] as? Number)?.toInt() ?: 0,
                completedBatches = (data["completedBatches"] as? Number)?.toInt() ?: 0,
                createdStudentDocIds = (data["createdStudentDocIds"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                createdFeeOverrideDocIds = (data["createdFeeOverrideDocIds"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                createdPromotionRecordIds = (data["createdPromotionRecordIds"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                createdFeeStructureDocId = (data["createdFeeStructureDocId"] as? String).orEmpty(),
                createdCalendarDocId = (data["createdCalendarDocId"] as? String).orEmpty(),
                previousCurrentSession = (data["previousCurrentSession"] as? String).orEmpty(),
                previousSessionsCsv = (data["previousSessionsCsv"] as? String).orEmpty(),
                appliedByUid = (data["appliedByUid"] as? String).orEmpty(),
                appliedByName = (data["appliedByName"] as? String).orEmpty(),
                appliedAtMs = (data["appliedAtMs"] as? Number)?.toLong() ?: 0L,
                undoExpiresAtMs = (data["undoExpiresAtMs"] as? Number)?.toLong() ?: 0L,
                undoneByUid = (data["undoneByUid"] as? String).orEmpty(),
                undoneByName = (data["undoneByName"] as? String).orEmpty(),
                undoneAtMs = (data["undoneAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

enum class PromotionDecision(
    val wireValue: String,
    val labelEn: String,
    val labelHi: String,
    val createsNewSessionStudent: Boolean
) {
    PROMOTED("PROMOTED", "Promoted", "प्रमोटेड (अगली कक्षा)", true),
    PROMOTED_BY_GRACE("PROMOTED_BY_GRACE", "Promoted by Grace", "ग्रेस से प्रमोट", true),
    DETAINED("DETAINED", "Detained (Same Class)", "उसी कक्षा में रोका", true),
    LEFT("LEFT", "Left School", "विद्यालय छोड़ा", false),
    PASSED_OUT("PASSED_OUT", "Passed Out / Alumni", "उत्तीर्ण / पूर्व छात्र", false),
    NEEDS_REVIEW("NEEDS_REVIEW", "Needs Review", "जाँच आवश्यक", false);

    val isFinalDecision: Boolean
        get() = this != NEEDS_REVIEW

    companion object {
        fun fromRaw(raw: String?): PromotionDecision =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: NEEDS_REVIEW
    }
}

enum class RollAssignmentMode(val labelEn: String, val labelHi: String) {
    KEEP_PREVIOUS_ROLL("By Previous Roll No.", "पिछले रोल नं. के अनुसार"),
    ALPHABETICAL_BY_NAME("Alphabetical (A–Z)", "नाम के अनुसार (A–Z)")
}

data class StudentPromotionDraft(
    val schoolDocId: String,
    val oldStudentDocId: String,
    val student: StudentEntity,
    val annualPercent: Double,
    val annualResultStatus: ResultStatus,
    val hasAnyAnnualMarks: Boolean,
    val proposedDecision: PromotionDecision,
    val decision: PromotionDecision,
    val toClassName: String,
    val toSection: String,
    val toRollNo: Int,
    val manualRollOverride: Boolean = false,
    val graceRemark: String = "",
    val graceApprovedByUid: String = "",
    val graceApprovedByName: String = "",
    val carriedForwardDueRs: Int = 0
)

data class PromotionRecord(
    val id: String,
    val schoolId: String,
    val transitionId: String,
    val fromSession: String,
    val toSession: String,
    val oldStudentDocId: String,
    val newStudentDocId: String,
    val admNo: String,
    val studentName: String,
    val fatherName: String,
    val mobile: String,
    val fromClassName: String,
    val fromSection: String,
    val fromRollNo: Int,
    val toClassName: String,
    val toSection: String,
    val toRollNo: Int,
    val annualPercent: Double,
    val annualResultStatus: ResultStatus,
    val decision: PromotionDecision,
    val isAlumni: Boolean,
    val graceRemark: String = "",
    val graceApprovedByUid: String = "",
    val graceApprovedByName: String = "",
    val carriedForwardDueRs: Int = 0,
    val confirmedByUid: String = "",
    val confirmedByName: String = "",
    val confirmedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(updatedAtValue: Any): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "transitionId" to transitionId,
        "fromSession" to fromSession.trim().take(30),
        "toSession" to toSession.trim().take(30),
        "oldStudentDocId" to oldStudentDocId,
        "newStudentDocId" to newStudentDocId.trim().take(128),
        "admNo" to admNo.trim().take(60),
        "studentName" to studentName.trim().take(120),
        "fatherName" to fatherName.trim().take(120),
        "mobile" to mobile.trim().take(40),
        "fromClassName" to fromClassName.trim().take(40),
        "fromSection" to fromSection.trim().ifBlank { "A" }.take(10),
        "fromRollNo" to fromRollNo.coerceIn(1, 9999).toLong(),
        "toClassName" to toClassName.trim().take(40),
        "toSection" to toSection.trim().take(10),
        "toRollNo" to toRollNo.coerceIn(0, 9999).toLong(),
        "annualPercent" to annualPercent.coerceIn(0.0, 100.0),
        "annualResultStatus" to annualResultStatus.name,
        "decision" to decision.wireValue,
        "isAlumni" to isAlumni,
        "graceRemark" to graceRemark.trim().take(300),
        "graceApprovedByUid" to graceApprovedByUid.trim().take(128),
        "graceApprovedByName" to graceApprovedByName.trim().take(120),
        "carriedForwardDueRs" to carriedForwardDueRs.coerceAtLeast(0).toLong(),
        "confirmedByUid" to confirmedByUid,
        "confirmedByName" to confirmedByName.trim().ifBlank { "Staff" }.take(120),
        "confirmedAtMs" to confirmedAtMs.coerceAtLeast(0L),
        "updatedAt" to updatedAtValue
    )

    companion object {
        fun makeDocId(schoolId: String, fromSession: String, toSession: String, oldStudentDocId: String): String =
            "prom_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(fromSession)}_${StableDocId.sanitizeSegment(toSession)}_${StableDocId.sanitizeSegment(oldStudentDocId)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): PromotionRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val studentName = (data["studentName"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || studentName.isEmpty()) return null
            val rawResult = (data["annualResultStatus"] as? String).orEmpty()
            val resultStatus = ResultStatus.entries.firstOrNull { it.name.equals(rawResult, ignoreCase = true) }
                ?: ResultStatus.PENDING
            return PromotionRecord(
                id = docId,
                schoolId = schoolId,
                transitionId = (data["transitionId"] as? String).orEmpty(),
                fromSession = (data["fromSession"] as? String).orEmpty(),
                toSession = (data["toSession"] as? String).orEmpty(),
                oldStudentDocId = (data["oldStudentDocId"] as? String).orEmpty(),
                newStudentDocId = (data["newStudentDocId"] as? String).orEmpty(),
                admNo = (data["admNo"] as? String).orEmpty(),
                studentName = studentName,
                fatherName = (data["fatherName"] as? String).orEmpty(),
                mobile = (data["mobile"] as? String).orEmpty(),
                fromClassName = (data["fromClassName"] as? String).orEmpty(),
                fromSection = (data["fromSection"] as? String).orEmpty().ifBlank { "A" },
                fromRollNo = (data["fromRollNo"] as? Number)?.toInt() ?: 1,
                toClassName = (data["toClassName"] as? String).orEmpty(),
                toSection = (data["toSection"] as? String).orEmpty(),
                toRollNo = (data["toRollNo"] as? Number)?.toInt() ?: 0,
                annualPercent = (data["annualPercent"] as? Number)?.toDouble() ?: 0.0,
                annualResultStatus = resultStatus,
                decision = PromotionDecision.fromRaw(data["decision"] as? String),
                isAlumni = (data["isAlumni"] as? Boolean) ?: false,
                graceRemark = (data["graceRemark"] as? String).orEmpty(),
                graceApprovedByUid = (data["graceApprovedByUid"] as? String).orEmpty(),
                graceApprovedByName = (data["graceApprovedByName"] as? String).orEmpty(),
                carriedForwardDueRs = (data["carriedForwardDueRs"] as? Number)?.toInt() ?: 0,
                confirmedByUid = (data["confirmedByUid"] as? String).orEmpty(),
                confirmedByName = (data["confirmedByName"] as? String).orEmpty(),
                confirmedAtMs = (data["confirmedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class StudentSessionHistoryEntry(
    val session: String,
    val className: String,
    val section: String,
    val rollNo: Int,
    val statusLabel: String,
    val decision: PromotionDecision? = null,
    val graceRemark: String = "",
    val carriedForwardDueRs: Int = 0
)

data class NewSessionDataGuardSummary(
    val marksEnteredCount: Int = 0,
    val attendanceSheetsCount: Int = 0,
    val feeReceiptsCount: Int = 0,
    val manuallyAddedStudentsCount: Int = 0,
    val certificatesCount: Int = 0
) {
    val hasAnyNewData: Boolean
        get() = marksEnteredCount > 0 || attendanceSheetsCount > 0 || feeReceiptsCount > 0 ||
            manuallyAddedStudentsCount > 0 || certificatesCount > 0

    fun warningItemsEn(): List<String> = buildList {
        if (marksEnteredCount > 0) add("$marksEnteredCount mark entries")
        if (attendanceSheetsCount > 0) add("$attendanceSheetsCount attendance sheets")
        if (feeReceiptsCount > 0) add("$feeReceiptsCount fee receipts")
        if (manuallyAddedStudentsCount > 0) add("$manuallyAddedStudentsCount newly added students")
        if (certificatesCount > 0) add("$certificatesCount certificates")
    }
}

data class UndoEligibilityResult(
    val allowed: Boolean,
    val reasonEn: String,
    val reasonHi: String,
    val daysRemaining: Int = 0,
    val requiresNewDataWarning: Boolean = false,
    val guardSummary: NewSessionDataGuardSummary = NewSessionDataGuardSummary()
)

data class NewSessionStudentPlan(
    val newStudentDocId: String,
    val oldStudentDocId: String,
    val student: StudentEntity,
    val carriedForwardOverride: StudentFeeOverride?,
    val promotionRecord: PromotionRecord
)

data class PromotionBatchChunk(
    val batchIndex: Int,
    val items: List<NewSessionStudentPlan>,
    val nonMovedPromotionRecords: List<PromotionRecord>
)

data class PromotionBatchPlan(
    val transitionId: String,
    val schoolId: String,
    val fromSession: String,
    val toSession: String,
    val chunks: List<PromotionBatchChunk>,
    val totalStudentsEvaluated: Int,
    val promotedCount: Int,
    val gracePromotedCount: Int,
    val detainedCount: Int,
    val leftCount: Int,
    val passedOutCount: Int,
    val totalDuesCarriedForwardRs: Int,
    val createdStudentDocIds: List<String>,
    val createdFeeOverrideDocIds: List<String>,
    val createdPromotionRecordIds: List<String>
)

data class BulkPromotePassedResult(
    val updatedDrafts: List<StudentPromotionDraft>,
    val promotedCount: Int,
    val passedOutCount: Int,
    val exceptions: List<StudentPromotionDraft>
)

// ============================================================================
// PART C: SESSION TRANSITION & PROMOTION ENGINES
// ============================================================================

object SessionTransitionEngine {

    const val UNDO_WINDOW_MS: Long = 7L * 24L * 60L * 60L * 1000L

    val DEFAULT_CLASS_ORDER: List<String> = listOf(
        "Nursery", "LKG", "UKG",
        "1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th"
    )

    private val SESSION_REGEX = Regex("^(\\d{4})-(\\d{4})$")

    fun suggestNextSession(fromSession: String): SessionDateRange {
        val m = SESSION_REGEX.matchEntire(fromSession.trim())
        val nextStartYear = if (m != null) {
            (m.groupValues[2].toIntOrNull() ?: 2027)
        } else {
            val cal = Calendar.getInstance()
            val y = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            if (month >= 4) y + 1 else y
        }
        val nextEndYear = nextStartYear + 1
        return SessionDateRange(
            sessionName = "$nextStartYear-$nextEndYear",
            startDateIso = "$nextStartYear-04-01",
            endDateIso = "$nextEndYear-03-31"
        )
    }

    fun validateSessionNameAndDates(
        fromSession: String,
        toSession: String,
        startDateIso: String,
        endDateIso: String
    ): String? {
        val cleanFrom = fromSession.trim()
        val cleanTo = toSession.trim()
        if (cleanTo.isEmpty()) return "Please enter the new session name (for example 2027-2028)."
        val match = SESSION_REGEX.matchEntire(cleanTo)
            ?: return "Session name must be in YYYY-YYYY format (for example 2027-2028)."
        val y1 = match.groupValues[1].toIntOrNull() ?: 0
        val y2 = match.groupValues[2].toIntOrNull() ?: 0
        if (y2 != y1 + 1) return "Academic session must span consecutive years (e.g. $y1-${y1 + 1})."
        if (cleanTo.equals(cleanFrom, ignoreCase = true)) {
            return "New session must be different from the current session ($cleanFrom)."
        }
        if (!Phase6DateUtils.isValidIsoDate(startDateIso)) {
            return "Start date must be a valid YYYY-MM-DD date (e.g. $y1-04-01)."
        }
        if (!Phase6DateUtils.isValidIsoDate(endDateIso)) {
            return "End date must be a valid YYYY-MM-DD date (e.g. $y2-03-31)."
        }
        if (endDateIso < startDateIso) {
            return "End date cannot be earlier than start date."
        }
        return null
    }

    fun effectiveClassOrder(
        schoolClasses: List<SchoolClassEntity>,
        savedOrder: SchoolClassOrderConfig?
    ): List<String> {
        val base = savedOrder?.orderedClassNames?.filter { it.isNotBlank() }.orEmpty()
        val schoolNames = schoolClasses.sortedBy { it.sortOrder }.map { it.name.trim() }.filter { it.isNotEmpty() }
        val seed = when {
            base.isNotEmpty() -> base
            schoolNames.isNotEmpty() -> {
                // Sort school's existing classes using DEFAULT_CLASS_ORDER rank first, then original sortOrder
                val defaultRank = DEFAULT_CLASS_ORDER.mapIndexed { idx, name ->
                    name.lowercase(Locale.US) to idx
                }.toMap()
                schoolNames.sortedWith(
                    compareBy<String> { defaultRank[it.lowercase(Locale.US)] ?: Int.MAX_VALUE }
                        .thenBy { schoolNames.indexOf(it) }
                )
            }
            else -> DEFAULT_CLASS_ORDER
        }
        val result = seed.toMutableList()
        val seen = result.map { it.lowercase(Locale.US) }.toMutableSet()
        for (name in schoolNames) {
            if (seen.add(name.lowercase(Locale.US))) {
                result.add(name)
            }
        }
        return result
    }

    /** Returns the next class in order, or `null` if [currentClass] is the final class (Passed Out / Alumni). */
    fun nextClassInOrder(currentClass: String, classOrder: List<String>): String? {
        val idx = classOrder.indexOfFirst { it.equals(currentClass.trim(), ignoreCase = true) }
        if (idx < 0) return null
        return if (idx + 1 < classOrder.size) classOrder[idx + 1] else null
    }

    fun isFinalClass(currentClass: String, classOrder: List<String>): Boolean {
        val idx = classOrder.indexOfFirst { it.equals(currentClass.trim(), ignoreCase = true) }
        return idx >= 0 && idx == classOrder.lastIndex
    }

    fun copyFeeStructureForNewSession(
        oldStructure: FeeStructureConfig?,
        schoolId: String,
        toSession: String,
        classNames: List<String>
    ): FeeStructureConfig {
        val source = oldStructure ?: FeeStructureConfig.buildDefault(schoolId, toSession, classNames)
        return source.copy(
            id = FeeStructureConfig.makeDocId(schoolId, toSession),
            schoolId = schoolId,
            session = toSession,
            frozenPastCellAmountsByClass = emptyMap(),
            hasPendingWrites = false
        )
    }

    fun shiftIsoDateByYears(iso: String, yearsDelta: Int): String {
        if (!Phase6DateUtils.isValidIsoDate(iso)) return iso
        val parts = iso.split("-")
        val y = (parts[0].toIntOrNull() ?: return iso) + yearsDelta
        val m = parts[1].toIntOrNull() ?: return iso
        val d = parts[2].toIntOrNull() ?: return iso
        val cal = GregorianCalendar(y, (m - 1).coerceIn(0, 11), 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val safeDay = d.coerceIn(1, maxDay)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, safeDay)
    }

    fun copySchoolCalendarForNewSession(
        oldCalendar: SchoolCalendarConfig?,
        schoolId: String,
        toSession: String,
        startDateIso: String,
        endDateIso: String,
        editedHolidays: List<CalendarHoliday>? = null
    ): SchoolCalendarConfig {
        val defaults = SchoolCalendarEngine.defaultTermDatesForSession(toSession)
        val baseHolidays = editedHolidays ?: oldCalendar?.holidays?.map { h ->
            val newStart = shiftIsoDateByYears(h.startDate, 1)
            val newEnd = shiftIsoDateByYears(h.endDate, 1)
            h.copy(
                id = "hol_${newStart.replace("-", "_")}_${StableDocId.sanitizeSegment(h.name).take(16)}",
                startDate = newStart,
                endDate = if (newEnd >= newStart) newEnd else newStart
            )
        }.orEmpty()

        return SchoolCalendarConfig(
            id = SchoolCalendarConfig.makeDocId(schoolId, toSession),
            schoolId = schoolId,
            session = toSession,
            workingDays = oldCalendar?.workingDays ?: Phase4Validator.DEFAULT_WORKING_DAYS,
            holidays = baseHolidays,
            halfWorkingDays = emptyList(),
            workingDayExceptions = emptyList(),
            term1StartDate = startDateIso.ifBlank { defaults.first.first },
            term1EndDate = defaults.first.second,
            term2StartDate = defaults.second.first,
            term2EndDate = endDateIso.ifBlank { defaults.second.second }
        )
    }

    fun evaluateUndoEligibility(
        transition: SessionTransitionRecord,
        nowMs: Long,
        isOwner: Boolean,
        guardSummary: NewSessionDataGuardSummary
    ): UndoEligibilityResult {
        if (!isOwner) {
            return UndoEligibilityResult(
                allowed = false,
                reasonEn = "Only the Organisation Owner can undo a session start.",
                reasonHi = "केवल संस्था के स्वामी (Owner) ही सत्र की शुरुआत वापस ले सकते हैं।",
                guardSummary = guardSummary
            )
        }
        if (transition.status != SessionTransitionStatus.COMPLETED) {
            return UndoEligibilityResult(
                allowed = false,
                reasonEn = "This session start cannot be undone (status: ${transition.status.labelEn}).",
                reasonHi = "यह सत्र प्रारंभ वापस नहीं लिया जा सकता (स्थिति: ${transition.status.labelHi})।",
                guardSummary = guardSummary
            )
        }
        val expiresAt = if (transition.undoExpiresAtMs > 0L) transition.undoExpiresAtMs else transition.appliedAtMs + UNDO_WINDOW_MS
        if (nowMs > expiresAt) {
            return UndoEligibilityResult(
                allowed = false,
                reasonEn = "The 7-day undo window has expired. Past sessions remain viewable anytime.",
                reasonHi = "7 दिन की वापस लेने की समय-सीमा समाप्त हो गई है। पुराने सत्र हमेशा देखे जा सकते हैं।",
                guardSummary = guardSummary
            )
        }
        val remainingMs = (expiresAt - nowMs).coerceAtLeast(0L)
        val daysLeft = ((remainingMs + 86_399_999L) / 86_400_000L).toInt().coerceIn(1, 7)
        val warn = guardSummary.hasAnyNewData
        return UndoEligibilityResult(
            allowed = true,
            reasonEn = if (warn) {
                "New data exists in ${transition.toSession} (${guardSummary.warningItemsEn().joinToString(", ")}). Undo will NOT delete that data unless you confirm."
            } else {
                "Safe to undo ($daysLeft day(s) remaining). Previous session ${transition.fromSession} will be restored."
            },
            reasonHi = if (warn) {
                "नए सत्र ${transition.toSession} में नया डेटा दर्ज है। आपकी पुष्टि के बिना इसे नहीं हटाया जाएगा।"
            } else {
                "वापस लेने के लिए सुरक्षित ($daysLeft दिन शेष)। पिछला सत्र ${transition.fromSession} बहाल होगा।"
            },
            daysRemaining = daysLeft,
            requiresNewDataWarning = warn,
            guardSummary = guardSummary
        )
    }

    fun appendSessionToCsv(existingCsv: String, newSession: String): String {
        val list = existingCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (list.none { it.equals(newSession.trim(), ignoreCase = true) }) {
            list.add(newSession.trim())
        }
        return list.joinToString(",")
    }
}

object PromotionEngine {

    /**
     * Builds proposed promotion drafts for all students in [fromSession] for one school.
     * Reuses [ReportCalculations.calculateStudentPerformance] (exact 33% per-subject rule)
     * and [FeeCollectionEngine.computeStudentFeeAccount] (exact unpaid fee balance).
     */
    fun buildPromotionDraftsForSchool(
        schoolDocId: String,
        fromSession: String,
        studentsInFromSession: List<Pair<String, StudentEntity>>, // (studentDocId, entity)
        subjectsByClassName: Map<String, List<String>>,
        marksByStudentId: Map<Long, List<MarkEntity>>,
        passPercent: Int = 33,
        classOrder: List<String>,
        oldFeeStructure: FeeStructureConfig?,
        oldFeeOverridesByStudentDocId: Map<String, StudentFeeOverride>,
        oldReceiptsByStudentDocId: Map<String, List<FeeReceiptRecord>>
    ): List<StudentPromotionDraft> {
        val classRank = classOrder.mapIndexed { i, c -> c.lowercase(Locale.US) to i }.toMap()

        val rawDrafts = studentsInFromSession
            .filter { (_, st) -> st.session.equals(fromSession, ignoreCase = true) }
            .sortedWith(
                compareBy<Pair<String, StudentEntity>> { classRank[it.second.className.lowercase(Locale.US)] ?: Int.MAX_VALUE }
                    .thenBy { it.second.className.lowercase(Locale.US) }
                    .thenBy { it.second.section.uppercase(Locale.US) }
                    .thenBy { it.second.rollNo }
                    .thenBy { it.second.name.lowercase(Locale.US) }
            )
            .map { (docId, st) ->
                val subjects = subjectsByClassName[st.className]
                    ?: subjectsByClassName.entries.firstOrNull { it.key.equals(st.className, ignoreCase = true) }?.value
                    ?: defaultSubjectsForClassFallback(st.className)

                val studentMarks = marksByStudentId[st.id].orEmpty()
                val marksMap = studentMarks.associate { (it.subjectName to it.componentKey) to it.rawValue }

                // Reuse the exact report card pass calculation (zero duplication!)
                val perf = ReportCalculations.calculateStudentPerformance(
                    studentId = st.id,
                    subjects = subjects,
                    marksBySubjectAndKey = marksMap,
                    passPercent = passPercent
                )

                val nextClass = SessionTransitionEngine.nextClassInOrder(st.className, classOrder)
                val proposed = when {
                    !st.isActiveStatus -> PromotionDecision.LEFT
                    !perf.hasAnyAnnualMarks -> PromotionDecision.NEEDS_REVIEW
                    perf.overallResult == ResultStatus.PASS -> {
                        if (nextClass == null) PromotionDecision.PASSED_OUT else PromotionDecision.PROMOTED
                    }
                    perf.overallResult == ResultStatus.FAIL -> PromotionDecision.DETAINED
                    else -> PromotionDecision.NEEDS_REVIEW
                }

                val targetClass = when (proposed) {
                    PromotionDecision.PROMOTED, PromotionDecision.PROMOTED_BY_GRACE -> nextClass ?: ""
                    PromotionDecision.DETAINED -> st.className
                    PromotionDecision.LEFT, PromotionDecision.PASSED_OUT, PromotionDecision.NEEDS_REVIEW -> ""
                }

                val unpaidDue = if (oldFeeStructure != null) {
                    FeeCollectionEngine.computeStudentFeeAccount(
                        structure = oldFeeStructure,
                        student = st,
                        studentDocId = docId,
                        override = oldFeeOverridesByStudentDocId[docId],
                        receiptsForStudent = oldReceiptsByStudentDocId[docId].orEmpty()
                    ).totalRemainingDue.coerceAtLeast(0)
                } else 0

                StudentPromotionDraft(
                    schoolDocId = schoolDocId,
                    oldStudentDocId = docId,
                    student = st,
                    annualPercent = perf.overallPercentage,
                    annualResultStatus = perf.overallResult,
                    hasAnyAnnualMarks = perf.hasAnyAnnualMarks,
                    proposedDecision = proposed,
                    decision = proposed,
                    toClassName = targetClass,
                    toSection = if (proposed.createsNewSessionStudent) st.section.ifBlank { "A" } else "",
                    toRollNo = if (proposed.createsNewSessionStudent) st.rollNo.coerceAtLeast(1) else 0,
                    carriedForwardDueRs = unpaidDue
                )
            }

        return assignSectionsAndRollNumbers(rawDrafts, RollAssignmentMode.KEEP_PREVIOUS_ROLL)
    }

    fun validateGracePromotion(remark: String, approverUid: String, approverName: String): String? {
        if (remark.trim().isEmpty()) {
            return "A remark is required for Promoted by Grace (for example: Approved by Principal)."
        }
        if (approverUid.isBlank() || approverName.trim().isEmpty()) {
            return "Approver details are required for Promoted by Grace."
        }
        return null
    }

    fun updateSingleDraftDecision(
        draft: StudentPromotionDraft,
        newDecision: PromotionDecision,
        classOrder: List<String>,
        graceRemark: String = "",
        approverUid: String = "",
        approverName: String = ""
    ): StudentPromotionDraft {
        val nextClass = SessionTransitionEngine.nextClassInOrder(draft.student.className, classOrder)
        val effectiveDecision = if (
            (newDecision == PromotionDecision.PROMOTED || newDecision == PromotionDecision.PROMOTED_BY_GRACE) &&
            nextClass == null
        ) {
            // Final class (e.g. 8th) promotes to Passed Out / Alumni
            PromotionDecision.PASSED_OUT
        } else {
            newDecision
        }
        val toClass = when (effectiveDecision) {
            PromotionDecision.PROMOTED, PromotionDecision.PROMOTED_BY_GRACE -> nextClass.orEmpty()
            PromotionDecision.DETAINED -> draft.student.className
            PromotionDecision.LEFT, PromotionDecision.PASSED_OUT, PromotionDecision.NEEDS_REVIEW -> ""
        }
        val toSec = if (effectiveDecision.createsNewSessionStudent) {
            draft.toSection.ifBlank { draft.student.section.ifBlank { "A" } }
        } else ""
        return draft.copy(
            decision = effectiveDecision,
            toClassName = toClass,
            toSection = toSec,
            toRollNo = if (effectiveDecision.createsNewSessionStudent) draft.toRollNo.coerceAtLeast(1) else 0,
            graceRemark = if (effectiveDecision == PromotionDecision.PROMOTED_BY_GRACE) graceRemark.trim() else "",
            graceApprovedByUid = if (effectiveDecision == PromotionDecision.PROMOTED_BY_GRACE) approverUid else "",
            graceApprovedByName = if (effectiveDecision == PromotionDecision.PROMOTED_BY_GRACE) approverName.trim() else ""
        )
    }

    /**
     * Bulk action "Promote all passed": confirms every student whose annual result is PASS
     * to PROMOTED (or PASSED_OUT for final class) and returns all exceptions (Needs Review,
     * Detained, Left) first so the operator reviews them before confirming.
     */
    fun applyBulkPromoteAllPassed(
        drafts: List<StudentPromotionDraft>,
        classOrder: List<String>
    ): BulkPromotePassedResult {
        var promotedCount = 0
        var passedOutCount = 0
        val updated = drafts.map { d ->
            if (d.student.isActiveStatus && d.hasAnyAnnualMarks && d.annualResultStatus == ResultStatus.PASS) {
                val nextClass = SessionTransitionEngine.nextClassInOrder(d.student.className, classOrder)
                if (nextClass == null) {
                    passedOutCount++
                    updateSingleDraftDecision(d, PromotionDecision.PASSED_OUT, classOrder)
                } else {
                    promotedCount++
                    updateSingleDraftDecision(d, PromotionDecision.PROMOTED, classOrder)
                }
            } else d
        }
        val exceptions = updated.filter {
            it.decision == PromotionDecision.NEEDS_REVIEW ||
                it.decision == PromotionDecision.DETAINED ||
                it.decision == PromotionDecision.LEFT
        }.sortedWith(
            compareBy<StudentPromotionDraft> {
                when (it.decision) {
                    PromotionDecision.NEEDS_REVIEW -> 0
                    PromotionDecision.DETAINED -> 1
                    else -> 2
                }
            }.thenBy { it.student.rollNo }
        )
        return BulkPromotePassedResult(
            updatedDrafts = updated,
            promotedCount = promotedCount,
            passedOutCount = passedOutCount,
            exceptions = exceptions
        )
    }

    /**
     * Assigns roll numbers per `(toClassName, toSection)` using either previous roll order
     * or alphabetical by student name, while preserving explicit manual roll overrides.
     */
    fun assignSectionsAndRollNumbers(
        drafts: List<StudentPromotionDraft>,
        mode: RollAssignmentMode,
        sectionOverrideByStudentDocId: Map<String, String> = emptyMap(),
        manualRollByStudentDocId: Map<String, Int> = emptyMap()
    ): List<StudentPromotionDraft> {
        val withSections = drafts.map { d ->
            if (!d.decision.createsNewSessionStudent) {
                d.copy(toSection = "", toRollNo = 0)
            } else {
                val sec = sectionOverrideByStudentDocId[d.oldStudentDocId]?.trim()?.uppercase(Locale.US)
                    ?.takeIf { it.isNotEmpty() }
                    ?: d.toSection.ifBlank { d.student.section.ifBlank { "A" } }.uppercase(Locale.US)
                val manualRoll = manualRollByStudentDocId[d.oldStudentDocId]
                d.copy(
                    toSection = sec,
                    toRollNo = manualRoll ?: d.toRollNo,
                    manualRollOverride = manualRoll != null || d.manualRollOverride
                )
            }
        }

        val groupedMoving = withSections
            .filter { it.decision.createsNewSessionStudent && it.toClassName.isNotBlank() }
            .groupBy { it.toClassName.lowercase(Locale.US) to it.toSection.uppercase(Locale.US) }

        val assignedRollByDocId = mutableMapOf<String, Int>()
        for ((_, group) in groupedMoving) {
            val sorted = when (mode) {
                RollAssignmentMode.ALPHABETICAL_BY_NAME -> group.sortedWith(
                    compareBy<StudentPromotionDraft> { it.student.name.lowercase(Locale.US) }
                        .thenBy { it.student.admNo.lowercase(Locale.US) }
                )
                RollAssignmentMode.KEEP_PREVIOUS_ROLL -> group.sortedWith(
                    compareBy<StudentPromotionDraft> { it.student.rollNo }
                        .thenBy { it.student.name.lowercase(Locale.US) }
                )
            }
            val takenRolls = sorted.filter { it.manualRollOverride && it.toRollNo > 0 }.map { it.toRollNo }.toMutableSet()
            var nextRoll = 1
            for (item in sorted) {
                if (item.manualRollOverride && item.toRollNo > 0) {
                    assignedRollByDocId[item.oldStudentDocId] = item.toRollNo
                } else {
                    while (nextRoll in takenRolls) nextRoll++
                    assignedRollByDocId[item.oldStudentDocId] = nextRoll
                    takenRolls.add(nextRoll)
                    nextRoll++
                }
            }
        }

        return withSections.map { d ->
            if (!d.decision.createsNewSessionStudent) d
            else d.copy(toRollNo = assignedRollByDocId[d.oldStudentDocId] ?: d.toRollNo.coerceAtLeast(1))
        }
    }

    fun makeNewSessionStudentDocId(schoolId: String, toSession: String, admNo: String, oldStudentDocId: String): String {
        val key = admNo.trim().ifBlank { oldStudentDocId }
        return "stu_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(toSession)}_${StableDocId.sanitizeSegment(key)}"
    }

    /**
     * Validates all drafts and compiles deterministic chunked batches so a failure halfway can
     * be resumed or rolled back safely, and two offline devices promoting the same class converge
     * onto identical document IDs without creating duplicate students or duplicate carry-forward dues.
     */
    fun buildPromotionBatches(
        drafts: List<StudentPromotionDraft>,
        schoolId: String,
        schoolStableId: Long,
        fromSession: String,
        toSession: String,
        classIdByClassName: Map<String, Long>,
        confirmedByUid: String,
        confirmedByName: String,
        confirmedAtMs: Long,
        batchSize: Int = 25
    ): Result<PromotionBatchPlan> {
        val unresolved = drafts.filter { it.decision == PromotionDecision.NEEDS_REVIEW }
        if (unresolved.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "${unresolved.size} student(s) still marked 'Needs Review'. Please choose Promoted, Detained, Grace, or Left before confirming."
                )
            )
        }
        val badGrace = drafts.firstOrNull {
            it.decision == PromotionDecision.PROMOTED_BY_GRACE && it.graceRemark.trim().isEmpty()
        }
        if (badGrace != null) {
            return Result.failure(
                IllegalArgumentException(
                    "Please enter a grace remark for ${badGrace.student.name} before confirming."
                )
            )
        }

        val transitionId = SessionTransitionRecord.makeDocId(schoolId, fromSession, toSession)
        val safeBatchSize = batchSize.coerceIn(5, 80)

        val movingPlans = mutableListOf<NewSessionStudentPlan>()
        val nonMovingRecords = mutableListOf<PromotionRecord>()

        var promotedCount = 0
        var graceCount = 0
        var detainedCount = 0
        var leftCount = 0
        var passedOutCount = 0
        var totalDues = 0

        for (d in drafts) {
            when (d.decision) {
                PromotionDecision.PROMOTED -> promotedCount++
                PromotionDecision.PROMOTED_BY_GRACE -> graceCount++
                PromotionDecision.DETAINED -> detainedCount++
                PromotionDecision.LEFT -> leftCount++
                PromotionDecision.PASSED_OUT -> passedOutCount++
                PromotionDecision.NEEDS_REVIEW -> Unit
            }

            val promDocId = PromotionRecord.makeDocId(schoolId, fromSession, toSession, d.oldStudentDocId)
            val isAlumni = d.decision == PromotionDecision.PASSED_OUT

            if (d.decision.createsNewSessionStudent && d.toClassName.isNotBlank()) {
                val newStuDocId = makeNewSessionStudentDocId(schoolId, toSession, d.student.admNo, d.oldStudentDocId)
                val targetClassId = classIdByClassName[d.toClassName]
                    ?: classIdByClassName.entries.firstOrNull { it.key.equals(d.toClassName, ignoreCase = true) }?.value
                    ?: 0L

                // Carry over admission number, photo, guardian details, sibling group, and transport assignment!
                val newStudent = d.student.copy(
                    id = StableDocId.toLong(newStuDocId),
                    schoolId = schoolStableId,
                    classId = targetClassId,
                    session = toSession,
                    className = d.toClassName,
                    section = d.toSection.ifBlank { "A" },
                    rollNo = d.toRollNo.coerceAtLeast(1),
                    status = "ACTIVE",
                    leftDate = "",
                    leftReason = ""
                )

                val carryDue = d.carriedForwardDueRs.coerceAtLeast(0)
                totalDues += carryDue

                val override = if (carryDue > 0) {
                    StudentFeeOverride(
                        id = StudentFeeOverride.makeDocId(schoolId, toSession, newStuDocId),
                        schoolId = schoolId,
                        session = toSession,
                        studentId = newStuDocId,
                        studentName = newStudent.name,
                        cellOverrides = emptyMap(),
                        remark = "Carried forward from session $fromSession",
                        previousSessionDueAmount = carryDue,
                        previousSessionDueLabel = "Previous session dues ($fromSession)",
                        updatedByUid = confirmedByUid,
                        updatedByName = confirmedByName,
                        clientEditedAtMs = confirmedAtMs
                    )
                } else null

                val promRec = PromotionRecord(
                    id = promDocId,
                    schoolId = schoolId,
                    transitionId = transitionId,
                    fromSession = fromSession,
                    toSession = toSession,
                    oldStudentDocId = d.oldStudentDocId,
                    newStudentDocId = newStuDocId,
                    admNo = d.student.admNo,
                    studentName = d.student.name,
                    fatherName = d.student.fatherName,
                    mobile = d.student.mobile,
                    fromClassName = d.student.className,
                    fromSection = d.student.section.ifBlank { "A" },
                    fromRollNo = d.student.rollNo.coerceAtLeast(1),
                    toClassName = d.toClassName,
                    toSection = d.toSection.ifBlank { "A" },
                    toRollNo = d.toRollNo.coerceAtLeast(1),
                    annualPercent = d.annualPercent,
                    annualResultStatus = d.annualResultStatus,
                    decision = d.decision,
                    isAlumni = false,
                    graceRemark = d.graceRemark,
                    graceApprovedByUid = d.graceApprovedByUid,
                    graceApprovedByName = d.graceApprovedByName,
                    carriedForwardDueRs = carryDue,
                    confirmedByUid = confirmedByUid,
                    confirmedByName = confirmedByName,
                    confirmedAtMs = confirmedAtMs
                )

                movingPlans += NewSessionStudentPlan(
                    newStudentDocId = newStuDocId,
                    oldStudentDocId = d.oldStudentDocId,
                    student = newStudent,
                    carriedForwardOverride = override,
                    promotionRecord = promRec
                )
            } else {
                val promRec = PromotionRecord(
                    id = promDocId,
                    schoolId = schoolId,
                    transitionId = transitionId,
                    fromSession = fromSession,
                    toSession = toSession,
                    oldStudentDocId = d.oldStudentDocId,
                    newStudentDocId = "",
                    admNo = d.student.admNo,
                    studentName = d.student.name,
                    fatherName = d.student.fatherName,
                    mobile = d.student.mobile,
                    fromClassName = d.student.className,
                    fromSection = d.student.section.ifBlank { "A" },
                    fromRollNo = d.student.rollNo.coerceAtLeast(1),
                    toClassName = if (isAlumni) "Alumni" else "",
                    toSection = "",
                    toRollNo = 0,
                    annualPercent = d.annualPercent,
                    annualResultStatus = d.annualResultStatus,
                    decision = d.decision,
                    isAlumni = isAlumni,
                    graceRemark = d.graceRemark,
                    graceApprovedByUid = d.graceApprovedByUid,
                    graceApprovedByName = d.graceApprovedByName,
                    carriedForwardDueRs = d.carriedForwardDueRs.coerceAtLeast(0),
                    confirmedByUid = confirmedByUid,
                    confirmedByName = confirmedByName,
                    confirmedAtMs = confirmedAtMs
                )
                nonMovingRecords += promRec
            }
        }

        val chunks = mutableListOf<PromotionBatchChunk>()
        val movingChunked = movingPlans.chunked(safeBatchSize)
        val nonMovingChunked = nonMovingRecords.chunked(safeBatchSize * 2)
        val totalChunks = maxOf(movingChunked.size, nonMovingChunked.size)
        for (idx in 0 until totalChunks) {
            chunks += PromotionBatchChunk(
                batchIndex = idx,
                items = movingChunked.getOrNull(idx).orEmpty(),
                nonMovedPromotionRecords = nonMovingChunked.getOrNull(idx).orEmpty()
            )
        }

        return Result.success(
            PromotionBatchPlan(
                transitionId = transitionId,
                schoolId = schoolId,
                fromSession = fromSession,
                toSession = toSession,
                chunks = chunks,
                totalStudentsEvaluated = drafts.size,
                promotedCount = promotedCount,
                gracePromotedCount = graceCount,
                detainedCount = detainedCount,
                leftCount = leftCount,
                passedOutCount = passedOutCount,
                totalDuesCarriedForwardRs = totalDues,
                createdStudentDocIds = movingPlans.map { it.newStudentDocId },
                createdFeeOverrideDocIds = movingPlans.mapNotNull { it.carriedForwardOverride?.id },
                createdPromotionRecordIds = movingPlans.map { it.promotionRecord.id } + nonMovingRecords.map { it.id }
            )
        )
    }

    /** Builds chronological multi-session class history for a student (by admission number or name+father). */
    fun buildStudentSessionHistory(
        admNo: String,
        studentName: String,
        fatherName: String,
        allStudentsInSchool: List<StudentEntity>,
        promotionRecordsInSchool: List<PromotionRecord>
    ): List<StudentSessionHistoryEntry> {
        val cleanAdm = admNo.trim()
        val matchedStudents = allStudentsInSchool.filter { st ->
            if (cleanAdm.isNotEmpty() && st.admNo.isNotBlank()) {
                st.admNo.equals(cleanAdm, ignoreCase = true)
            } else {
                st.name.equals(studentName.trim(), ignoreCase = true) &&
                    (fatherName.isBlank() || st.fatherName.equals(fatherName.trim(), ignoreCase = true))
            }
        }
        val matchedProms = promotionRecordsInSchool.filter { pr ->
            if (cleanAdm.isNotEmpty() && pr.admNo.isNotBlank()) {
                pr.admNo.equals(cleanAdm, ignoreCase = true)
            } else {
                pr.studentName.equals(studentName.trim(), ignoreCase = true) &&
                    (fatherName.isBlank() || pr.fatherName.equals(fatherName.trim(), ignoreCase = true))
            }
        }

        val bySession = linkedMapOf<String, StudentSessionHistoryEntry>()
        for (st in matchedStudents.sortedBy { it.session }) {
            val outgoingProm = matchedProms.firstOrNull { it.fromSession.equals(st.session, ignoreCase = true) }
            val statusLabel = when {
                outgoingProm != null -> outgoingProm.decision.labelEn
                !st.isActiveStatus -> "Left School"
                else -> "Active"
            }
            bySession[st.session] = StudentSessionHistoryEntry(
                session = st.session,
                className = st.className,
                section = st.section,
                rollNo = st.rollNo,
                statusLabel = statusLabel,
                decision = outgoingProm?.decision,
                graceRemark = outgoingProm?.graceRemark.orEmpty(),
                carriedForwardDueRs = outgoingProm?.carriedForwardDueRs ?: 0
            )
        }
        for (pr in matchedProms.sortedBy { it.fromSession }) {
            if (!bySession.containsKey(pr.fromSession)) {
                bySession[pr.fromSession] = StudentSessionHistoryEntry(
                    session = pr.fromSession,
                    className = pr.fromClassName,
                    section = pr.fromSection,
                    rollNo = pr.fromRollNo,
                    statusLabel = pr.decision.labelEn,
                    decision = pr.decision,
                    graceRemark = pr.graceRemark,
                    carriedForwardDueRs = pr.carriedForwardDueRs
                )
            }
            if (pr.isAlumni) {
                val alumniKey = "${pr.toSession} (Alumni)"
                bySession[alumniKey] = StudentSessionHistoryEntry(
                    session = pr.toSession,
                    className = "Passed Out / Alumni (from ${pr.fromClassName})",
                    section = "",
                    rollNo = 0,
                    statusLabel = PromotionDecision.PASSED_OUT.labelEn,
                    decision = PromotionDecision.PASSED_OUT
                )
            }
        }
        return bySession.values.sortedBy { it.session }
    }

    fun searchAlumni(
        promotionRecords: List<PromotionRecord>,
        query: String
    ): List<PromotionRecord> {
        val alumni = promotionRecords.filter { it.isAlumni || it.decision == PromotionDecision.PASSED_OUT }
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return alumni.sortedWith(compareByDescending<PromotionRecord> { it.fromSession }.thenBy { it.studentName.lowercase(Locale.US) })
        val digits = q.filter { it.isDigit() }
        return alumni.filter { rec ->
            rec.studentName.lowercase(Locale.US).contains(q) ||
                rec.admNo.lowercase(Locale.US).contains(q) ||
                rec.fatherName.lowercase(Locale.US).contains(q) ||
                rec.fromSession.lowercase(Locale.US).contains(q) ||
                (digits.length >= 3 && rec.mobile.filter { it.isDigit() }.contains(digits))
        }.sortedWith(compareByDescending<PromotionRecord> { it.fromSession }.thenBy { it.studentName.lowercase(Locale.US) })
    }
}

// ============================================================================
// PART D: BULK STUDENT IMPORT ENGINE (CSV + PURE-KOTLIN XLSX)
// ============================================================================

enum class StudentImportField(
    val key: String,
    val labelEn: String,
    val labelHi: String,
    val isRequired: Boolean = false
) {
    NAME("name", "Student Name", "छात्र का नाम", isRequired = true),
    CLASS_NAME("className", "Class", "कक्षा", isRequired = true),
    SECTION("section", "Section", "सेक्शन"),
    ROLL_NO("rollNo", "Roll Number", "रोल नंबर"),
    ADM_NO("admNo", "Admission No.", "प्रवेश संख्या"),
    FATHER_NAME("fatherName", "Father Name", "पिता का नाम"),
    MOTHER_NAME("motherName", "Mother Name", "माता का नाम"),
    MOBILE("mobile", "Guardian Mobile", "अभिभावक मोबाइल"),
    SECOND_MOBILE("secondMobile", "Second Mobile", "दूसरा मोबाइल"),
    DOB("dob", "Date of Birth", "जन्म तिथि"),
    ADDRESS("address", "Address", "पता"),
    USES_TRANSPORT("usesTransport", "Uses Transport (Yes/No)", "वाहन (हाँ/नहीं)"),
    TRANSPORT_AMOUNT("transportAmount", "Transport Monthly Rs.", "वाहन शुल्क"),
    TRANSPORT_ROUTE("transportRoute", "Transport Route", "वाहन रूट"),
    ADMISSION_DATE("admissionDate", "Admission Date", "प्रवेश तिथि"),
    PREVIOUS_SCHOOL("previousSchool", "Previous School", "पिछला स्कूल")
}

data class ParsedTabularFile(
    val fileName: String,
    val format: String, // "CSV" or "XLSX"
    val headers: List<String>,
    val rows: List<List<String>>
)

enum class ImportRowProblemKind(val labelEn: String, val labelHi: String) {
    MISSING_NAME("Missing student name", "छात्र का नाम खाली है"),
    INVALID_CLASS("Invalid class name", "अमान्य कक्षा"),
    INVALID_MOBILE("Invalid mobile number", "अमान्य मोबाइल नंबर"),
    DUPLICATE_ADM_IN_FILE("Duplicate admission no. in file", "फ़ाइल में प्रवेश संख्या दोहराई गई"),
    EXISTING_ADM_CONFLICT("Admission no. already exists", "प्रवेश संख्या पहले से मौजूद है"),
    DUPLICATE_STUDENT("Possible duplicate student", "समान छात्र पहले से मौजूद है")
}

data class ImportRowProblem(
    val kind: ImportRowProblemKind,
    val message: String,
    val isBlocking: Boolean = true
)

data class ImportFieldDiff(
    val fieldLabel: String,
    val oldValue: String,
    val newValue: String
)

enum class ImportRowAction { ADD, UPDATE, SKIP, ERROR }

data class ImportRowEditableValues(
    val name: String = "",
    val className: String = "",
    val section: String = "A",
    val rollNo: String = "",
    val admNo: String = "",
    val fatherName: String = "",
    val motherName: String = "",
    val mobile: String = "",
    val secondMobile: String = "",
    val dob: String = "",
    val address: String = "",
    val usesTransport: Boolean = false,
    val transportAmount: Int = 0,
    val transportRoute: String = "",
    val admissionDate: String = "",
    val previousSchool: String = ""
)

data class BulkImportPreviewRow(
    val rowNumber: Int, // 1-based data row number (row 2 in spreadsheet)
    val values: ImportRowEditableValues,
    val normalizedMobile: String,
    val normalizedSecondMobile: String,
    val problems: List<ImportRowProblem>,
    val action: ImportRowAction,
    val matchedExistingDocId: String? = null,
    val updateDiffs: List<ImportFieldDiff> = emptyList(),
    val isSkippedByUser: Boolean = false
) {
    val hasBlockingErrors: Boolean get() = problems.any { it.isBlocking }
}

data class BulkImportPreviewSummary(
    val rows: List<BulkImportPreviewRow>,
    val addCount: Int,
    val updateCount: Int,
    val skipCount: Int,
    val errorCount: Int
)

data class BulkImportExecutionReport(
    val schoolName: String,
    val session: String,
    val fileName: String,
    val totalRows: Int,
    val addedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val updateMatchingEnabled: Boolean,
    val actorName: String,
    val importedAtMs: Long,
    val rows: List<BulkImportPreviewRow>
)

data class BulkImportLogRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val fileName: String,
    val totalRows: Int,
    val addedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val updateMatchingEnabled: Boolean,
    val actorUid: String,
    val actorName: String,
    val importedAtMs: Long,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(createdAtValue: Any): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "fileName" to fileName.trim().take(160),
        "totalRows" to totalRows.coerceAtLeast(0).toLong(),
        "addedCount" to addedCount.coerceAtLeast(0).toLong(),
        "updatedCount" to updatedCount.coerceAtLeast(0).toLong(),
        "skippedCount" to skippedCount.coerceAtLeast(0).toLong(),
        "errorCount" to errorCount.coerceAtLeast(0).toLong(),
        "updateMatchingEnabled" to updateMatchingEnabled,
        "actorUid" to actorUid,
        "actorName" to actorName.trim().ifBlank { "Admin" }.take(120),
        "importedAtMs" to importedAtMs.coerceAtLeast(0L),
        "createdAt" to createdAtValue
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): BulkImportLogRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val session = (data["session"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || session.isEmpty()) return null
            return BulkImportLogRecord(
                id = docId,
                schoolId = schoolId,
                session = session,
                fileName = (data["fileName"] as? String).orEmpty(),
                totalRows = (data["totalRows"] as? Number)?.toInt() ?: 0,
                addedCount = (data["addedCount"] as? Number)?.toInt() ?: 0,
                updatedCount = (data["updatedCount"] as? Number)?.toInt() ?: 0,
                skippedCount = (data["skippedCount"] as? Number)?.toInt() ?: 0,
                errorCount = (data["errorCount"] as? Number)?.toInt() ?: 0,
                updateMatchingEnabled = (data["updateMatchingEnabled"] as? Boolean) ?: false,
                actorUid = (data["actorUid"] as? String).orEmpty(),
                actorName = (data["actorName"] as? String).orEmpty(),
                importedAtMs = (data["importedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

object BulkStudentImportEngine {

    /** Bilingual Hindi + English sample CSV ready to open in Excel or Google Sheets. */
    fun buildSampleCsv(): String {
        val header = listOf(
            "Student Name / छात्र का नाम",
            "Class / कक्षा",
            "Section / सेक्शन",
            "Roll No / रोल नं.",
            "Admission No / प्रवेश संख्या",
            "Father Name / पिता का नाम",
            "Mother Name / माता का नाम",
            "Guardian Mobile / अभिभावक मोबाइल",
            "Second Mobile / दूसरा मोबाइल",
            "DOB / जन्म तिथि",
            "Address / पता",
            "Uses Transport / वाहन (Yes/No)",
            "Transport Amount / वाहन शुल्क",
            "Transport Route / रूट",
            "Admission Date / प्रवेश तिथि",
            "Previous School / पिछला स्कूल"
        ).joinToString(",") { csvEscape(it) }

        val row1 = listOf(
            "Aarav Sharma", "1st", "A", "1", "ADM-101",
            "Rajesh Sharma", "Sunita Sharma", "9839100001", "",
            "2019-05-14", "Civil Lines, Ward 4", "Yes", "400", "Bus Route 1", "2026-04-02", ""
        ).joinToString(",") { csvEscape(it) }

        val row2 = listOf(
            "Ananya Verma", "2nd", "A", "2", "ADM-102",
            "Mahesh Verma", "Kavita Verma", "+91 9839100002", "9839100099",
            "2018-09-20", "Station Road", "No", "0", "", "2026-04-03", "Bal Vidya Mandir"
        ).joinToString(",") { csvEscape(it) }

        return "\uFEFF$header\n$row1\n$row2\n"
    }

    /** Full RFC-4180 CSV parser supporting BOM, quoted commas, escaped quotes, and multiline cells. */
    fun parseCsvText(csvText: String, fileName: String = "students.csv"): Result<ParsedTabularFile> {
        val clean = csvText.removePrefix("\uFEFF")
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("The CSV file is empty."))
        }
        val records = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < clean.length && clean[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(cell.toString().trim())
                        cell.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < clean.length && clean[i + 1] == '\n') i++
                        currentRow.add(cell.toString().trim())
                        cell.setLength(0)
                        if (currentRow.any { it.isNotEmpty() }) records.add(currentRow.toList())
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow.add(cell.toString().trim())
                        cell.setLength(0)
                        if (currentRow.any { it.isNotEmpty() }) records.add(currentRow.toList())
                        currentRow.clear()
                    }
                    else -> cell.append(ch)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString().trim())
            if (currentRow.any { it.isNotEmpty() }) records.add(currentRow.toList())
        }
        if (records.isEmpty()) {
            return Result.failure(IllegalArgumentException("No rows found in CSV file."))
        }
        val headers = records.first()
        val dataRows = records.drop(1)
        return Result.success(ParsedTabularFile(fileName = fileName, format = "CSV", headers = headers, rows = dataRows))
    }

    /**
     * Lightweight pure-Kotlin Excel `.xlsx` reader using only JDK/Android built-in
     * `ZipInputStream` + XXE-hardened `DocumentBuilderFactory` (zero third-party POI bloat!).
     * Reads `xl/sharedStrings.xml` and the first worksheet `xl/worksheets/sheet1.xml`.
     */
    fun parseXlsxStream(inputStream: InputStream, fileName: String = "students.xlsx"): Result<ParsedTabularFile> {
        return try {
            var sharedStringsBytes: ByteArray? = null
            var sheetBytes: ByteArray? = null
            var firstWorksheetName: String? = null

            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (!entry.isDirectory) {
                        when {
                            name.equals("xl/sharedStrings.xml", ignoreCase = true) -> {
                                sharedStringsBytes = zis.readBytes()
                            }
                            name.equals("xl/worksheets/sheet1.xml", ignoreCase = true) -> {
                                sheetBytes = zis.readBytes()
                                firstWorksheetName = name
                            }
                            sheetBytes == null && name.startsWith("xl/worksheets/sheet", ignoreCase = true) && name.endsWith(".xml", ignoreCase = true) -> {
                                sheetBytes = zis.readBytes()
                                firstWorksheetName = name
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val rawSheet = sheetBytes
                ?: return Result.failure(IllegalArgumentException("Could not find a worksheet inside this Excel (.xlsx) file."))

            val dbf = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val builder = dbf.newDocumentBuilder()

            val sharedStrings = mutableListOf<String>()
            if (sharedStringsBytes != null) {
                val ssDoc = builder.parse(sharedStringsBytes!!.inputStream())
                val siNodes = ssDoc.getElementsByTagName("si")
                for (idx in 0 until siNodes.length) {
                    val si = siNodes.item(idx) as? Element ?: continue
                    val tNodes = si.getElementsByTagName("t")
                    val sb = StringBuilder()
                    for (j in 0 until tNodes.length) {
                        sb.append(tNodes.item(j).textContent.orEmpty())
                    }
                    sharedStrings.add(sb.toString().trim())
                }
            }

            val sheetDoc = builder.parse(rawSheet.inputStream())
            val rowNodes = sheetDoc.getElementsByTagName("row")
            val parsedRows = mutableListOf<List<String>>()
            var maxCols = 0

            for (rIdx in 0 until rowNodes.length) {
                val rowEl = rowNodes.item(rIdx) as? Element ?: continue
                val cellNodes = rowEl.getElementsByTagName("c")
                val rowMap = sortedMapOf<Int, String>()
                for (cIdx in 0 until cellNodes.length) {
                    val cEl = cellNodes.item(cIdx) as? Element ?: continue
                    val ref = cEl.getAttribute("r").orEmpty()
                    val colIdx = columnRefToZeroBasedIndex(ref).takeIf { it >= 0 } ?: cIdx
                    val cellType = cEl.getAttribute("t").orEmpty()
                    val value = when (cellType) {
                        "s" -> {
                            val vText = firstChildTagText(cEl, "v").trim()
                            val ssIdx = vText.toIntOrNull()
                            if (ssIdx != null && ssIdx in sharedStrings.indices) sharedStrings[ssIdx] else ""
                        }
                        "inlineStr" -> {
                            val tNodes = cEl.getElementsByTagName("t")
                            val sb = StringBuilder()
                            for (k in 0 until tNodes.length) sb.append(tNodes.item(k).textContent.orEmpty())
                            sb.toString().trim()
                        }
                        "b" -> if (firstChildTagText(cEl, "v").trim() == "1") "Yes" else "No"
                        else -> {
                            val raw = firstChildTagText(cEl, "v").trim()
                            // Clean trailing ".0" on integer numeric cells like Roll No or Mobile
                            if (raw.endsWith(".0") && raw.dropLast(2).all { it.isDigit() }) raw.dropLast(2) else raw
                        }
                    }
                    if (value.isNotEmpty()) {
                        rowMap[colIdx] = value
                        if (colIdx + 1 > maxCols) maxCols = colIdx + 1
                    }
                }
                if (rowMap.isNotEmpty()) {
                    val list = MutableList(maxOf(maxCols, (rowMap.lastKey() + 1))) { idx -> rowMap[idx].orEmpty() }
                    parsedRows.add(list)
                }
            }

            if (parsedRows.isEmpty()) {
                return Result.failure(IllegalArgumentException("Worksheet (${firstWorksheetName ?: "sheet1"}) has no rows."))
            }
            val normalizedRows = parsedRows.map { row ->
                if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row
            }
            Result.success(
                ParsedTabularFile(
                    fileName = fileName,
                    format = "XLSX",
                    headers = normalizedRows.first(),
                    rows = normalizedRows.drop(1)
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException("Could not read Excel (.xlsx) file: ${e.message ?: "invalid format"}. You can also save as CSV and import.")
            )
        }
    }

    private fun firstChildTagText(parent: Element, tagName: String): String {
        val nodes = parent.getElementsByTagName(tagName)
        return if (nodes.length > 0) nodes.item(0).textContent.orEmpty() else ""
    }

    private fun columnRefToZeroBasedIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase(Locale.US)
        if (letters.isEmpty()) return -1
        var col = 0
        for (ch in letters) {
            col = col * 26 + (ch - 'A' + 1)
        }
        return col - 1
    }

    /** Auto-maps CSV/XLSX column indices to [StudentImportField] supporting English & Hindi headers. */
    fun autoMapColumns(headers: List<String>): Map<StudentImportField, Int> {
        val mapped = linkedMapOf<StudentImportField, Int>()
        val usedIndices = mutableSetOf<Int>()

        fun matchFirst(field: StudentImportField, predicate: (String) -> Boolean) {
            val idx = headers.indexOfFirst { h ->
                val norm = h.trim().lowercase(Locale.US)
                norm.isNotEmpty() && predicate(norm)
            }
            if (idx >= 0 && idx !in usedIndices) {
                mapped[field] = idx
                usedIndices.add(idx)
            }
        }

        matchFirst(StudentImportField.NAME) {
            (it.contains("student") && it.contains("name")) || it == "name" || it.contains("छात्र") || it.contains("विद्यार्थी")
        }
        matchFirst(StudentImportField.CLASS_NAME) {
            it == "class" || it.startsWith("class") || it.contains("कक्षा") || it.contains("grade")
        }
        matchFirst(StudentImportField.SECTION) {
            it.contains("section") || it == "sec" || it.contains("सेक्शन") || it.contains("वर्ग")
        }
        matchFirst(StudentImportField.ROLL_NO) {
            it.contains("roll") || it.contains("रोल") || it.contains("अनुक्रमांक")
        }
        matchFirst(StudentImportField.ADM_NO) {
            it.contains("adm") && !it.contains("date") || it.contains("प्रवेश संख्या") || it.contains("sr no") || it.contains("reg")
        }
        matchFirst(StudentImportField.FATHER_NAME) {
            it.contains("father") || it.contains("पिता")
        }
        matchFirst(StudentImportField.MOTHER_NAME) {
            it.contains("mother") || it.contains("माता")
        }
        matchFirst(StudentImportField.SECOND_MOBILE) {
            (it.contains("second") || it.contains("alt") || it.contains("दूसरा")) && (it.contains("mobile") || it.contains("phone") || it.contains("मोबाइल"))
        }
        matchFirst(StudentImportField.MOBILE) {
            it.contains("mobile") || it.contains("phone") || it.contains("guardian") || it.contains("मोबाइल") || it.contains("फोन")
        }
        matchFirst(StudentImportField.DOB) {
            it.contains("dob") || it.contains("birth") || it.contains("जन्म")
        }
        matchFirst(StudentImportField.ADDRESS) {
            it.contains("address") || it.contains("पता")
        }
        matchFirst(StudentImportField.TRANSPORT_AMOUNT) {
            (it.contains("transport") || it.contains("वाहन") || it.contains("bus")) && (it.contains("amount") || it.contains("fee") || it.contains("शुल्क") || it.contains("rs"))
        }
        matchFirst(StudentImportField.TRANSPORT_ROUTE) {
            (it.contains("transport") || it.contains("वाहन") || it.contains("bus") || it.contains("route") || it.contains("रूट")) && (it.contains("route") || it.contains("रूट"))
        }
        matchFirst(StudentImportField.USES_TRANSPORT) {
            it.contains("transport") || it.contains("वाहन") || it.contains("bus")
        }
        matchFirst(StudentImportField.ADMISSION_DATE) {
            (it.contains("admission") || it.contains("प्रवेश")) && (it.contains("date") || it.contains("तिथि"))
        }
        matchFirst(StudentImportField.PREVIOUS_SCHOOL) {
            it.contains("previous") || it.contains("prev") || it.contains("पिछला")
        }
        return mapped
    }

    private fun parseYesNo(raw: String): Boolean {
        val v = raw.trim().lowercase(Locale.US)
        return v in setOf("yes", "y", "true", "1", "हाँ", "हा", "haan", "ha")
    }

    fun extractEditableRow(
        rawRow: List<String>,
        mapping: Map<StudentImportField, Int>
    ): ImportRowEditableValues {
        fun get(f: StudentImportField): String {
            val idx = mapping[f] ?: return ""
            return rawRow.getOrNull(idx)?.trim().orEmpty()
        }
        val transportAmt = get(StudentImportField.TRANSPORT_AMOUNT).filter { it.isDigit() }.toIntOrNull() ?: 0
        val usesTransport = parseYesNo(get(StudentImportField.USES_TRANSPORT)) || transportAmt > 0 || get(StudentImportField.TRANSPORT_ROUTE).isNotBlank()
        return ImportRowEditableValues(
            name = get(StudentImportField.NAME),
            className = get(StudentImportField.CLASS_NAME),
            section = get(StudentImportField.SECTION).uppercase(Locale.US).ifBlank { "A" }.take(10),
            rollNo = get(StudentImportField.ROLL_NO).filter { it.isDigit() },
            admNo = get(StudentImportField.ADM_NO),
            fatherName = get(StudentImportField.FATHER_NAME),
            motherName = get(StudentImportField.MOTHER_NAME),
            mobile = get(StudentImportField.MOBILE),
            secondMobile = get(StudentImportField.SECOND_MOBILE),
            dob = get(StudentImportField.DOB),
            address = get(StudentImportField.ADDRESS),
            usesTransport = usesTransport,
            transportAmount = if (usesTransport) transportAmt else 0,
            transportRoute = if (usesTransport) get(StudentImportField.TRANSPORT_ROUTE) else "",
            admissionDate = get(StudentImportField.ADMISSION_DATE),
            previousSchool = get(StudentImportField.PREVIOUS_SCHOOL)
        )
    }

    /**
     * Validates every row, normalizes Indian mobile numbers to `+91` form via [PhoneNormalizer],
     * detects missing names, invalid classes, invalid mobiles, duplicate admission numbers (in file
     * and in database), duplicate students, and computes field-by-field update diffs when
     * [updateMatchingAdmNo] is enabled.
     */
    fun validateAndPreviewRows(
        rawRows: List<List<String>>,
        mapping: Map<StudentImportField, Int>,
        validClassNames: List<String>,
        existingStudentsInSession: List<Pair<String, StudentEntity>>, // (docId, entity)
        updateMatchingAdmNo: Boolean = false,
        editedOverrides: Map<Int, ImportRowEditableValues> = emptyMap(),
        skippedRowNumbers: Set<Int> = emptySet()
    ): BulkImportPreviewSummary {
        val validClassLookup = validClassNames.associateBy { it.trim().lowercase(Locale.US) }
        val existingByAdmLower = existingStudentsInSession
            .filter { it.second.admNo.isNotBlank() }
            .associateBy { it.second.admNo.trim().lowercase(Locale.US) }

        val existingByIdentityKey = existingStudentsInSession.associateBy { (_, st) ->
            "${st.name.trim().lowercase(Locale.US)}|${st.fatherName.trim().lowercase(Locale.US)}|${st.className.trim().lowercase(Locale.US)}"
        }

        // Count admission numbers inside file to detect file-internal duplicates
        val extracted = rawRows.mapIndexed { idx, row ->
            val rowNum = idx + 1
            val vals = editedOverrides[rowNum] ?: extractEditableRow(row, mapping)
            rowNum to vals
        }
        val admOccurrencesInFile = mutableMapOf<String, Int>()
        for ((rowNum, vals) in extracted) {
            if (rowNum in skippedRowNumbers) continue
            val admKey = vals.admNo.trim().lowercase(Locale.US)
            if (admKey.isNotEmpty()) {
                admOccurrencesInFile[admKey] = (admOccurrencesInFile[admKey] ?: 0) + 1
            }
        }
        val seenAdmInFile = mutableSetOf<String>()

        val previewRows = extracted.map { (rowNum, rawVals) ->
            val isSkipped = rowNum in skippedRowNumbers
            val problems = mutableListOf<ImportRowProblem>()

            val canonicalClass = validClassLookup[rawVals.className.trim().lowercase(Locale.US)]
            val resolvedVals = if (canonicalClass != null) rawVals.copy(className = canonicalClass) else rawVals

            if (!isSkipped) {
                if (resolvedVals.name.trim().isEmpty()) {
                    problems += ImportRowProblem(ImportRowProblemKind.MISSING_NAME, "Student name is required.")
                }
                if (resolvedVals.className.trim().isEmpty() || (validClassLookup.isNotEmpty() && canonicalClass == null)) {
                    problems += ImportRowProblem(
                        ImportRowProblemKind.INVALID_CLASS,
                        "Class '${resolvedVals.className}' does not match school classes (${validClassNames.joinToString(", ")})."
                    )
                }
            }

            var normMobile = ""
            if (resolvedVals.mobile.trim().isNotEmpty()) {
                when (val res = PhoneNormalizer.normalizeIndianMobile(resolvedVals.mobile)) {
                    is PhoneValidationResult.Valid -> normMobile = res.e164WithPlus
                    is PhoneValidationResult.Invalid -> if (!isSkipped) {
                        problems += ImportRowProblem(
                            ImportRowProblemKind.INVALID_MOBILE,
                            "Mobile '${resolvedVals.mobile}': ${res.reason}"
                        )
                    }
                }
            }

            var normSecondMobile = ""
            if (resolvedVals.secondMobile.trim().isNotEmpty()) {
                when (val res = PhoneNormalizer.normalizeIndianMobile(resolvedVals.secondMobile)) {
                    is PhoneValidationResult.Valid -> normSecondMobile = res.e164WithPlus
                    is PhoneValidationResult.Invalid -> if (!isSkipped) {
                        problems += ImportRowProblem(
                            ImportRowProblemKind.INVALID_MOBILE,
                            "Second mobile '${resolvedVals.secondMobile}': ${res.reason}"
                        )
                    }
                }
            }

            val admKey = resolvedVals.admNo.trim().lowercase(Locale.US)
            var matchedExistingDocId: String? = null
            val updateDiffs = mutableListOf<ImportFieldDiff>()

            if (!isSkipped && admKey.isNotEmpty()) {
                if ((admOccurrencesInFile[admKey] ?: 0) > 1 && !seenAdmInFile.add(admKey)) {
                    problems += ImportRowProblem(
                        ImportRowProblemKind.DUPLICATE_ADM_IN_FILE,
                        "Admission no. '${resolvedVals.admNo}' appears multiple times in this file."
                    )
                } else {
                    seenAdmInFile.add(admKey)
                }

                val existingMatch = existingByAdmLower[admKey]
                if (existingMatch != null) {
                    matchedExistingDocId = existingMatch.first
                    if (!updateMatchingAdmNo) {
                        problems += ImportRowProblem(
                            ImportRowProblemKind.EXISTING_ADM_CONFLICT,
                            "Admission no. '${resolvedVals.admNo}' already belongs to ${existingMatch.second.name} (${existingMatch.second.className}). Enable 'Update matching admission numbers' or skip row."
                        )
                    } else {
                        val ex = existingMatch.second
                        fun diff(label: String, oldV: String, newV: String) {
                            if (newV.isNotBlank() && !oldV.trim().equals(newV.trim(), ignoreCase = false)) {
                                updateDiffs += ImportFieldDiff(label, oldV.ifBlank { "—" }, newV)
                            }
                        }
                        diff("Name", ex.name, resolvedVals.name)
                        diff("Class", ex.className, resolvedVals.className)
                        diff("Section", ex.section, resolvedVals.section)
                        diff("Father", ex.fatherName, resolvedVals.fatherName)
                        diff("Mother", ex.motherName, resolvedVals.motherName)
                        diff("Mobile", ex.mobile, normMobile)
                        diff("Address", ex.address, resolvedVals.address)
                        if (ex.usesTransport != resolvedVals.usesTransport) {
                            updateDiffs += ImportFieldDiff("Transport", if (ex.usesTransport) "Yes" else "No", if (resolvedVals.usesTransport) "Yes" else "No")
                        }
                        if (ex.transportMonthlyAmount != resolvedVals.transportAmount) {
                            updateDiffs += ImportFieldDiff("Transport Fee", "Rs.${ex.transportMonthlyAmount}", "Rs.${resolvedVals.transportAmount}")
                        }
                    }
                }
            }

            if (!isSkipped && matchedExistingDocId == null && resolvedVals.name.isNotBlank() && resolvedVals.className.isNotBlank()) {
                val idKey = "${resolvedVals.name.trim().lowercase(Locale.US)}|${resolvedVals.fatherName.trim().lowercase(Locale.US)}|${resolvedVals.className.trim().lowercase(Locale.US)}"
                val dup = existingByIdentityKey[idKey]
                if (dup != null) {
                    problems += ImportRowProblem(
                        ImportRowProblemKind.DUPLICATE_STUDENT,
                        "Student '${dup.second.name}' (Father: ${dup.second.fatherName.ifBlank { "—" }}) already exists in ${dup.second.className}."
                    )
                }
            }

            val action = when {
                isSkipped -> ImportRowAction.SKIP
                problems.any { it.isBlocking } -> ImportRowAction.ERROR
                matchedExistingDocId != null && updateMatchingAdmNo -> ImportRowAction.UPDATE
                else -> ImportRowAction.ADD
            }

            BulkImportPreviewRow(
                rowNumber = rowNum,
                values = resolvedVals,
                normalizedMobile = normMobile,
                normalizedSecondMobile = normSecondMobile,
                problems = problems,
                action = action,
                matchedExistingDocId = matchedExistingDocId,
                updateDiffs = updateDiffs,
                isSkippedByUser = isSkipped
            )
        }

        return BulkImportPreviewSummary(
            rows = previewRows,
            addCount = previewRows.count { it.action == ImportRowAction.ADD },
            updateCount = previewRows.count { it.action == ImportRowAction.UPDATE },
            skipCount = previewRows.count { it.action == ImportRowAction.SKIP },
            errorCount = previewRows.count { it.action == ImportRowAction.ERROR }
        )
    }

    fun buildImportResultCsv(report: BulkImportExecutionReport): String {
        val sb = StringBuilder()
        sb.appendLine("Row,Action,Student Name,Class,Section,Roll No,Admission No,Father Name,Mobile (+91),Problems / Changes")
        for (r in report.rows) {
            val detail = when (r.action) {
                ImportRowAction.ERROR -> r.problems.joinToString("; ") { it.message }
                ImportRowAction.UPDATE -> r.updateDiffs.joinToString("; ") { "${it.fieldLabel}: ${it.oldValue} -> ${it.newValue}" }
                ImportRowAction.SKIP -> "Skipped by user"
                ImportRowAction.ADD -> "Added"
            }
            sb.appendLine(
                listOf(
                    r.rowNumber.toString(),
                    r.action.name,
                    r.values.name,
                    r.values.className,
                    r.values.section,
                    r.values.rollNo,
                    r.values.admNo,
                    r.values.fatherName,
                    r.normalizedMobile.ifBlank { r.values.mobile },
                    detail
                ).joinToString(",") { csvEscape(it) }
            )
        }
        return sb.toString()
    }
}

// ============================================================================
// PART E: BACKUP & EXPORT ZIP ENGINE + 30-DAY REMINDER
// ============================================================================

enum class BackupExportScope(val wireValue: String, val labelEn: String, val labelHi: String) {
    SCHOOL("SCHOOL", "Active School Only", "केवल वर्तमान विद्यालय"),
    ORGANISATION("ORGANISATION", "Whole Organisation (All Schools)", "पूरी संस्था (सभी विद्यालय)")
}

data class BackupExportDataset(
    val orgName: String,
    val scope: BackupExportScope,
    val schools: List<SchoolEntity>,
    val schoolDocIdByStableId: Map<Long, String>,
    val classGroups: List<ClassGroupEntity>,
    val schoolClasses: List<SchoolClassEntity>,
    val subjects: List<SubjectEntity>,
    val students: List<Pair<String, StudentEntity>>, // (docId, entity)
    val marks: List<MarkEntity>,
    val feeStructures: List<FeeStructureConfig>,
    val feeOverrides: List<StudentFeeOverride>,
    val feeReceipts: List<FeeReceiptRecord>,
    val attendanceSheets: List<StudentAttendanceSheet>,
    val teachers: List<TeacherProfile>,
    val cashBookEntries: List<CashBookEntryRecord>,
    val certificates: List<SchoolCertificateRecord>,
    val salaryMonthlyRecords: List<SalaryMonthlyRecord> = emptyList(),
    val activeSession: String
)

data class BackupExportZipResult(
    val fileName: String,
    val zipBytes: ByteArray,
    val includedFiles: List<String>,
    val includedSalaryCsv: Boolean,
    val studentCount: Int,
    val receiptCount: Int
)

data class BackupExportLogRecord(
    val id: String,
    val scope: BackupExportScope,
    val schoolId: String,
    val fileName: String,
    val includedSalaryCsv: Boolean,
    val studentCount: Int,
    val receiptCount: Int,
    val actorUid: String,
    val actorName: String,
    val actorRole: String,
    val exportedAtMs: Long,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(createdAtValue: Any): Map<String, Any> = mapOf(
        "scope" to scope.wireValue,
        "schoolId" to schoolId.trim().take(128),
        "fileName" to fileName.trim().take(160),
        "includedSalaryCsv" to includedSalaryCsv,
        "studentCount" to studentCount.coerceAtLeast(0).toLong(),
        "receiptCount" to receiptCount.coerceAtLeast(0).toLong(),
        "actorUid" to actorUid,
        "actorName" to actorName.trim().ifBlank { "Admin" }.take(120),
        "actorRole" to if (actorRole.equals("OWNER", ignoreCase = true)) "OWNER" else "ADMIN",
        "exportedAtMs" to exportedAtMs.coerceAtLeast(0L),
        "createdAt" to createdAtValue
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): BackupExportLogRecord? {
            if (data == null) return null
            val fileName = (data["fileName"] as? String)?.trim().orEmpty()
            if (fileName.isEmpty()) return null
            val scopeRaw = (data["scope"] as? String).orEmpty()
            val scope = BackupExportScope.entries.firstOrNull { it.wireValue.equals(scopeRaw, ignoreCase = true) }
                ?: BackupExportScope.SCHOOL
            return BackupExportLogRecord(
                id = docId,
                scope = scope,
                schoolId = (data["schoolId"] as? String).orEmpty(),
                fileName = fileName,
                includedSalaryCsv = (data["includedSalaryCsv"] as? Boolean) ?: false,
                studentCount = (data["studentCount"] as? Number)?.toInt() ?: 0,
                receiptCount = (data["receiptCount"] as? Number)?.toInt() ?: 0,
                actorUid = (data["actorUid"] as? String).orEmpty(),
                actorName = (data["actorName"] as? String).orEmpty(),
                actorRole = (data["actorRole"] as? String).orEmpty().ifBlank { "ADMIN" },
                exportedAtMs = (data["exportedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class BackupReminderStatus(
    val lastBackupEpochMs: Long,
    val lastBackupDateDisplay: String,
    val daysSinceBackup: Int?,
    val isOverdue30Days: Boolean,
    val headlineEn: String,
    val headlineHi: String,
    val subtextEn: String,
    val subtextHi: String
)

object BackupReminderEngine {
    const val REMINDER_THRESHOLD_DAYS = 30
    private const val DAY_MS = 86_400_000L

    fun computeStatus(lastBackupEpochMs: Long, nowMs: Long = System.currentTimeMillis()): BackupReminderStatus {
        if (lastBackupEpochMs <= 0L) {
            return BackupReminderStatus(
                lastBackupEpochMs = 0L,
                lastBackupDateDisplay = "Never",
                daysSinceBackup = null,
                isOverdue30Days = true,
                headlineEn = "No manual backup taken yet",
                headlineHi = "अभी तक मैनुअल बैकअप नहीं लिया गया है",
                subtextEn = "Firebase keeps your cloud copy automatically. Take a monthly ZIP copy for your own records.",
                subtextHi = "Firebase आपकी क्लाउड कॉपी सुरक्षित रखता है। अपने रिकॉर्ड के लिए महीने में एक बार ZIP बैकअप लें।"
            )
        }
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val dateText = fmt.format(Date(lastBackupEpochMs))
        val diffDays = ((nowMs - lastBackupEpochMs).coerceAtLeast(0L) / DAY_MS).toInt()
        val overdue = diffDays >= REMINDER_THRESHOLD_DAYS
        return BackupReminderStatus(
            lastBackupEpochMs = lastBackupEpochMs,
            lastBackupDateDisplay = dateText,
            daysSinceBackup = diffDays,
            isOverdue30Days = overdue,
            headlineEn = if (overdue) "Last backup: $dateText ($diffDays days ago — backup due)" else "Last backup: $dateText ($diffDays day(s) ago)",
            headlineHi = if (overdue) "अंतिम बैकअप: $dateText ($diffDays दिन पहले — बैकअप लें)" else "अंतिम बैकअप: $dateText ($diffDays दिन पहले)",
            subtextEn = "Manual export only — Firebase automatically keeps your cloud copy.",
            subtextHi = "यह मैनुअल एक्सपोर्ट है — Firebase आपकी क्लाउड कॉपी सुरक्षित रखता है।"
        )
    }
}

object BackupExportZipEngine {

    /**
     * Generates a clean, Excel-ready `.zip` backup containing `README.txt` + CSV tables.
     * SECURITY RULE: `staff_salary.csv` is included ONLY when `actorMember` is `OrgRole.OWNER`.
     * Admins receive all other tables but NEVER `staff_salary.csv`.
     */
    fun buildBackupZip(
        dataset: BackupExportDataset,
        actorMember: MemberAccessProfile?,
        exportedAtMs: Long = System.currentTimeMillis()
    ): Result<BackupExportZipResult> {
        if (!Phase14AccessEvaluator.canUseBackupExport(actorMember)) {
            return Result.failure(SecurityException("Only the Owner or an Admin can export a backup."))
        }
        val includeSalary = Phase14AccessEvaluator.canIncludeSalaryInBackup(actorMember)
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(exportedAtMs))
        val scopeSlug = if (dataset.scope == BackupExportScope.ORGANISATION) {
            StableDocId.sanitizeSegment(dataset.orgName.ifBlank { "Org" })
        } else {
            StableDocId.sanitizeSegment(dataset.schools.firstOrNull()?.name ?: "School")
        }
        val zipFileName = "Backup_${scopeSlug}_$dateStamp.zip"

        val schoolNameByStableId = dataset.schools.associate { it.id to it.name }
        val schoolNameByDocId = dataset.schools.associate { (dataset.schoolDocIdByStableId[it.id] ?: it.id.toString()) to it.name }
        val studentById = dataset.students.associate { it.second.id to it.second }

        val files = linkedMapOf<String, String>()
        files["students.csv"] = buildStudentsCsv(dataset.students, schoolNameByStableId)
        files["guardians.csv"] = buildGuardiansCsv(dataset.students, schoolNameByStableId)
        files["class_structure.csv"] = buildClassStructureCsv(dataset.schools, dataset.classGroups, dataset.schoolClasses, dataset.subjects)
        files["fee_receipts.csv"] = buildFeeReceiptsCsv(dataset.feeReceipts, schoolNameByDocId)
        files["fee_dues.csv"] = buildFeeDuesCsv(dataset, schoolNameByDocId)
        files["attendance_summary.csv"] = buildAttendanceSummaryCsv(dataset.attendanceSheets, schoolNameByDocId)
        files["marks.csv"] = buildMarksCsv(dataset.marks, studentById, schoolNameByStableId)
        files["teachers.csv"] = buildTeachersCsv(dataset.teachers, schoolNameByDocId)
        files["cash_book.csv"] = buildCashBookCsv(dataset.cashBookEntries, schoolNameByDocId)
        files["certificates_register.csv"] = buildCertificatesCsv(dataset.certificates, schoolNameByDocId)
        if (includeSalary) {
            files["staff_salary.csv"] = buildSalaryCsv(dataset.salaryMonthlyRecords, schoolNameByDocId)
        }

        val readme = buildReadmeTxt(
            dataset = dataset,
            exportedAtMs = exportedAtMs,
            includedFileNames = files.keys.toList(),
            includedSalaryCsv = includeSalary
        )

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun addEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
            addEntry("README.txt", readme)
            for ((name, csv) in files) {
                addEntry(name, "\uFEFF$csv")
            }
        }

        return Result.success(
            BackupExportZipResult(
                fileName = zipFileName,
                zipBytes = baos.toByteArray(),
                includedFiles = listOf("README.txt") + files.keys.toList(),
                includedSalaryCsv = includeSalary,
                studentCount = dataset.students.size,
                receiptCount = dataset.feeReceipts.size
            )
        )
    }

    private fun buildReadmeTxt(
        dataset: BackupExportDataset,
        exportedAtMs: Long,
        includedFileNames: List<String>,
        includedSalaryCsv: Boolean
    ): String = buildString {
        val dateText = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(exportedAtMs))
        appendLine("SCHOOL DATA BACKUP (MANUAL EXPORT)")
        appendLine("==================================")
        appendLine("Organisation : ${dataset.orgName.ifBlank { "School Organisation" }}")
        appendLine("Scope        : ${dataset.scope.labelEn}")
        appendLine("Schools      : ${dataset.schools.joinToString(", ") { it.name }}")
        appendLine("Exported On  : $dateText")
        appendLine("Note         : This is a manual backup copy for your own records. Cloud data remains safely stored in Firebase.")
        appendLine()
        appendLine("FILES INCLUDED:")
        includedFileNames.forEach { appendLine(" - $it") }
        if (!includedSalaryCsv) {
            appendLine()
            appendLine("Note: staff_salary.csv is included only in Owner exports.")
        }
        appendLine()
        appendLine("All CSV files are UTF-8 (with BOM) and open directly in Microsoft Excel or Google Sheets.")
    }

    private fun buildStudentsCsv(
        students: List<Pair<String, StudentEntity>>,
        schoolNameByStableId: Map<Long, String>
    ): String = buildString {
        appendLine("School,Session,Class,Section,Roll No,Admission No,Student Name,Father Name,Mother Name,DOB,Mobile,Second Mobile,Address,Uses Transport,Transport Route,Transport Monthly Rs,Status,Left Date,Left Reason")
        for ((_, st) in students.sortedWith(compareBy<Pair<String, StudentEntity>> { it.second.session }.thenBy { it.second.className }.thenBy { it.second.section }.thenBy { it.second.rollNo })) {
            appendLine(
                listOf(
                    schoolNameByStableId[st.schoolId].orEmpty(),
                    st.session, st.className, st.section, st.rollNo.toString(), st.admNo,
                    st.name, st.fatherName, st.motherName, st.dob, st.mobile, st.secondMobile,
                    st.address, if (st.usesTransport) "Yes" else "No", st.transportRouteName,
                    st.transportMonthlyAmount.toString(), st.status, st.leftDate, st.leftReason
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }

    private fun buildGuardiansCsv(
        students: List<Pair<String, StudentEntity>>,
        schoolNameByStableId: Map<Long, String>
    ): String = buildString {
        appendLine("School,Session,Father Name,Mother Name,Primary Mobile,Second Mobile,Address,Sibling Group,Student Name,Class,Admission No")
        for ((_, st) in students) {
            appendLine(
                listOf(
                    schoolNameByStableId[st.schoolId].orEmpty(),
                    st.session, st.fatherName, st.motherName, st.mobile, st.secondMobile,
                    st.address, st.siblingGroupId, st.name, "${st.className}-${st.section}", st.admNo
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }

    private fun buildClassStructureCsv(
        schools: List<SchoolEntity>,
        classGroups: List<ClassGroupEntity>,
        schoolClasses: List<SchoolClassEntity>,
        subjects: List<SubjectEntity>
    ): String = buildString {
        appendLine("School,Class Group,Class Name,Sort Order,Subjects")
        val schoolMap = schools.associateBy { it.id }
        val groupMap = classGroups.associateBy { it.id }
        val subjectsByGroup = subjects.groupBy { it.classGroupId }
        for (cls in schoolClasses.sortedWith(compareBy<SchoolClassEntity> { it.schoolId }.thenBy { it.sortOrder })) {
            val schName = schoolMap[cls.schoolId]?.name.orEmpty()
            val grp = groupMap[cls.classGroupId]
            val subs = subjectsByGroup[cls.classGroupId].orEmpty().sortedBy { it.sortOrder }.joinToString("; ") { it.name }
            appendLine(listOf(schName, grp?.name.orEmpty(), cls.name, cls.sortOrder.toString(), subs).joinToString(",") { csvEscape(it) })
        }
    }

    private fun buildFeeReceiptsCsv(
        receipts: List<FeeReceiptRecord>,
        schoolNameByDocId: Map<String, String>
    ): String = buildString {
        appendLine("School,Session,Receipt No,Date,Student Name,Admission No,Class,Section,Before Concession,Concession,Net Payable,Amount Paid,Payment Mode,Status,Received By")
        for (r in receipts.sortedByDescending { it.receiptDateIso }) {
            appendLine(
                listOf(
                    schoolNameByDocId[r.schoolId] ?: r.schoolId,
                    r.session, r.receiptNumber, r.receiptDateIso, r.studentName, r.studentAdmNo,
                    r.className, r.section, r.totalBeforeConcession.toString(), r.concessionAmount.toString(),
                    r.netPayableAmount.toString(), r.amountPaid.toString(), r.paymentMode.label,
                    r.status.wireValue, r.receivedByName
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }

    private fun buildFeeDuesCsv(
        dataset: BackupExportDataset,
        schoolNameByDocId: Map<String, String>
    ): String = buildString {
        appendLine("School,Session,Student Name,Admission No,Class,Section,Mobile,Previous Session Due,Total Scheduled,Total Paid,Total Concession,Remaining Due,Due Months")
        val structureByKey = dataset.feeStructures.associateBy { "${it.schoolId}|${it.session.lowercase(Locale.US)}" }
        val overrideByKey = dataset.feeOverrides.associateBy { "${it.schoolId}|${it.session.lowercase(Locale.US)}|${it.studentId}" }
        val receiptsByKey = dataset.feeReceipts.groupBy { "${it.schoolId}|${it.session.lowercase(Locale.US)}|${it.studentId}" }

        for ((stuDocId, st) in dataset.students) {
            if (!st.isActiveStatus) continue
            val schDocId = dataset.schoolDocIdByStableId[st.schoolId] ?: continue
            val struct = structureByKey["$schDocId|${st.session.lowercase(Locale.US)}"] ?: continue
            val ov = overrideByKey["$schDocId|${st.session.lowercase(Locale.US)}|$stuDocId"]
            val rcps = receiptsByKey["$schDocId|${st.session.lowercase(Locale.US)}|$stuDocId"].orEmpty()
            val acct = FeeCollectionEngine.computeStudentFeeAccount(struct, st, stuDocId, ov, rcps)
            if (acct.totalRemainingDue > 0) {
                appendLine(
                    listOf(
                        schoolNameByDocId[schDocId] ?: schDocId,
                        st.session, st.name, st.admNo, st.className, st.section, st.mobile,
                        (acct.previousSessionDueCell?.remainingDue ?: 0).toString(),
                        acct.totalScheduledFee.toString(),
                        acct.totalPaidOnGrid.toString(),
                        acct.totalConcessionOnGrid.toString(),
                        acct.totalRemainingDue.toString(),
                        acct.dueMonthsList.joinToString(" ")
                    ).joinToString(",") { csvEscape(it) }
                )
            }
        }
    }

    private fun buildAttendanceSummaryCsv(
        sheets: List<StudentAttendanceSheet>,
        schoolNameByDocId: Map<String, String>
    ): String = buildString {
        appendLine("School,Session,Date,Class,Section,Total Marked,Present,Absent,Leave,Late,Half Day,Taken By")
        for (sh in sheets.sortedByDescending { it.date }) {
            val entries = sh.entries.values
            appendLine(
                listOf(
                    schoolNameByDocId[sh.schoolId] ?: sh.schoolId,
                    sh.session, sh.date, sh.className, sh.section,
                    entries.size.toString(),
                    entries.count { it.status == StudentAttendanceStatus.PRESENT }.toString(),
                    entries.count { it.status == StudentAttendanceStatus.ABSENT }.toString(),
                    entries.count { it.status == StudentAttendanceStatus.LEAVE }.toString(),
                    entries.count { it.status == StudentAttendanceStatus.LATE }.toString(),
                    entries.count { it.status == StudentAttendanceStatus.HALF_DAY }.toString(),
                    sh.updatedByName
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }

    private fun buildMarksCsv(
        marks: List<MarkEntity>,
        studentById: Map<Long, StudentEntity>,
        schoolNameByStableId: Map<Long, String>
    ): String = buildString {
        appendLine("School,Session,Class,Section,Roll No,Admission No,Student Name,Subject,Component,Marks")
        for (m in marks) {
            val st = studentById[m.studentId] ?: continue
            appendLine(
                listOf(
                    schoolNameByStableId[st.schoolId].orEmpty(),
                    st.session, st.className, st.section, st.rollNo.toString(), st.admNo,
                    st.name, m.subjectName, m.componentKey, m.rawValue
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }

    private fun buildTeachersCsv(
        teachers: List<TeacherProfile>,
        schoolNameByDocId: Map<String, String>
    ): String = buildString {
        appendLine("Schools,Name,Mobile,Email,Subjects,Max Periods/Day,Has Login,Active")
        for (t in teachers.sortedBy { it.name.lowercase(Locale.US) }) {
            val schoolLabels = t.schoolIds.joinToString("; ") { schoolNameByDocId[it] ?: it }
            appendLine(
                listOf(
                    schoolLabels,
                    t.name, t.mobile, t.email,
                    t.subjects.joinToString("; "),
                    t.maxPeriodsPerDay.toString(),
                    if (t.hasLogin) "Yes" else "No",
                    if (t.active) "Yes" else "No"
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }

    private fun buildCashBookCsv(
        entries: List<CashBookEntryRecord>,
        schoolNameByDocId: Map<String, String>
    ): String = buildString {
        appendLine("School,Date,Type,Category,Amount Rs,Payment Mode,Note,Status,Entered By")
        for (e in entries.sortedByDescending { it.dateIso }) {
            appendLine(
                listOf(
                    schoolNameByDocId[e.schoolId] ?: e.schoolId,
                    e.dateIso, e.entryType.wireValue, e.category,
                    e.amount.toString(), e.paymentMode.label, e.note,
                    e.status.wireValue, e.createdByName
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }

    private fun buildCertificatesCsv(
        certs: List<SchoolCertificateRecord>,
        schoolNameByDocId: Map<String, String>
    ): String = buildString {
        appendLine("School,Certificate No,Type,Issue Date,Student Name,Admission No,Class,Status,Issued By")
        for (c in certs.sortedByDescending { it.issueDateIso }) {
            appendLine(
                listOf(
                    schoolNameByDocId[c.schoolId] ?: c.schoolId,
                    c.certificateNumber, c.certificateType.wireValue, c.issueDateIso,
                    c.studentName, c.studentAdmNo, "${c.className}-${c.section}",
                    c.status.wireValue, c.issuedByName
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }

    private fun buildSalaryCsv(
        records: List<SalaryMonthlyRecord>,
        schoolNameByDocId: Map<String, String>
    ): String = buildString {
        appendLine("School,Year-Month,Staff Name,Designation,Basic Monthly Rs,Present Days,Gross Earnings,Total Deductions,Advance Deducted,Net Salary Rs,Status,Paid Date")
        for (r in records.sortedWith(compareByDescending<SalaryMonthlyRecord> { it.yearMonth }.thenBy { it.staffName.lowercase(Locale.US) })) {
            appendLine(
                listOf(
                    schoolNameByDocId[r.schoolId] ?: r.schoolId,
                    r.yearMonth, r.staffName, r.designation,
                    r.basicSalaryRs.toString(),
                    r.presentDays.toString(),
                    r.grossEarningsRs.toString(),
                    r.totalDeductionsBeforeManualRs.toString(),
                    r.advanceDeductionRs.toString(),
                    r.netPayableRs.toString(),
                    r.status.wireValue,
                    r.paidDateIso
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }
}

private fun csvEscape(raw: String): String {
    val needsQuotes = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
    return if (needsQuotes) "\"${raw.replace("\"", "\"\"")}\"" else raw
}

private fun defaultSubjectsForClassFallback(className: String): List<String> {
    val norm = className.trim().lowercase(Locale.US)
    return when {
        norm.contains("nurs") || norm.contains("lkg") || norm.contains("ukg") || norm.contains("kg") ->
            DefaultSubjects.group1PrePrimary
        norm.startsWith("6") || norm.startsWith("7") || norm.startsWith("8") || norm.startsWith("9") || norm.startsWith("10") ->
            DefaultSubjects.group3Middle
        else -> DefaultSubjects.group2Primary
    }
}

