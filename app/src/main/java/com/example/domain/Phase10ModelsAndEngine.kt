package com.example.domain

import com.example.data.StudentEntity
import com.example.data.cloud.StableDocId
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================================
// PHASE 10: CERTIFICATES, CASH BOOK & ADMISSION ENQUIRY — DOMAIN MODELS & ENGINES
// Pure Kotlin logic (zero Android/Firebase dependencies) for direct unit testing.
// ============================================================================

// ============================================================================
// PART A: PHASE 10 ACCESS EVALUATOR (3 new modules: certificates, cashBook, enquiry)
// Missing key in MemberPermissions = AccessLevel.NONE (no access).
// Owner & Admin have full access across all schools.
// ============================================================================

object Phase10AccessEvaluator {
    fun canViewCertificates(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canViewModule(member, AppModule.CERTIFICATES)
    }

    fun canEditCertificates(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canEditModule(member, AppModule.CERTIFICATES)
    }

    fun canViewCashBook(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canViewModule(member, AppModule.CASH_BOOK)
    }

    fun canEditCashBook(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canEditModule(member, AppModule.CASH_BOOK)
    }

    fun canViewEnquiry(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canViewModule(member, AppModule.ENQUIRY)
    }

    fun canEditEnquiry(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canEditModule(member, AppModule.ENQUIRY)
    }

    /**
     * Checks whether an expense of [amountRs] can be recorded without explicit admin approval.
     * - Admins & Owners are always allowed.
     * - Expenses <= [approvalLimitRs] (or when approvalLimitRs <= 0) do not require approval.
     * - Non-admin staff must obtain explicit admin approval when [amountRs] > [approvalLimitRs].
     */
    fun isExpenseWithinMemberLimit(
        member: MemberAccessProfile?,
        amountRs: Int,
        approvalLimitRs: Int = CashBookSettingsConfig.DEFAULT_APPROVAL_LIMIT_RS,
        hasExplicitAdminApproval: Boolean = false
    ): Boolean {
        if (amountRs <= 0) return false
        if (approvalLimitRs <= 0) return true
        if (OrgAccessEvaluator.isAdmin(member)) return true
        if (hasExplicitAdminApproval) return true
        return amountRs <= approvalLimitRs
    }
}

// ============================================================================
// PART B: CERTIFICATES (TC, BONAFIDE, CHARACTER)
// ============================================================================

enum class CertificateType(
    val wireValue: String,
    val numberPrefix: String,
    val englishTitle: String,
    val hindiTitle: String,
    val shortLabel: String
) {
    TC("TC", "TC", "Transfer Certificate", "स्थानांतरण प्रमाण पत्र (TC)", "TC"),
    BONAFIDE("BONAFIDE", "BON", "Bonafide Certificate", "अध्ययन प्रमाण पत्र (Bonafide)", "Bonafide"),
    CHARACTER("CHARACTER", "CHR", "Character Certificate", "चरित्र प्रमाण पत्र (Character)", "Character");

    companion object {
        fun fromRaw(raw: String?): CertificateType =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: BONAFIDE
    }
}

enum class CertificateStatus(val wireValue: String, val label: String) {
    ISSUED("ISSUED", "Issued"),
    CANCELLED("CANCELLED", "Cancelled");

    companion object {
        fun fromRaw(raw: String?): CertificateStatus =
            if (raw.equals("CANCELLED", ignoreCase = true)) CANCELLED else ISSUED
    }
}

data class SchoolCertificateRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val certificateType: CertificateType,
    val certificateNumber: String,
    val deviceNodeCode: String,
    val sequenceNo: Int,
    val issueDateIso: String,
    val studentId: String,
    val studentName: String,
    val studentAdmNo: String,
    val fatherName: String,
    val motherName: String,
    val dob: String,
    val className: String,
    val section: String,
    val rollNo: Int,
    val admissionDate: String = "",
    val address: String = "",
    val reasonForLeaving: String = "",
    val lastAttendanceDateIso: String = "",
    val conduct: String = "Good",
    val promotionStatus: String = "",
    val feeDuesStatus: String = "All school dues cleared",
    val remarks: String = "",
    val status: CertificateStatus = CertificateStatus.ISSUED,
    val issuedByUid: String,
    val issuedByName: String,
    val issuedAtMs: Long,
    val cancelledAtMs: Long = 0L,
    val cancelledByUid: String = "",
    val cancelledByName: String = "",
    val cancelReason: String = "",
    val reissuedFromCertId: String = "",
    val reissuedFromCertNumber: String = "",
    val hasPendingWrites: Boolean = false
) {
    val isCancelled: Boolean get() = status == CertificateStatus.CANCELLED

    fun toCreateFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "certificateType" to certificateType.wireValue,
        "certificateNumber" to certificateNumber.trim().take(60),
        "deviceNodeCode" to deviceNodeCode.trim().take(12),
        "sequenceNo" to sequenceNo.coerceAtLeast(1).toLong(),
        "issueDateIso" to issueDateIso.trim().take(10),
        "studentId" to studentId.trim().take(128),
        "studentName" to studentName.trim().take(120),
        "studentAdmNo" to studentAdmNo.trim().take(60),
        "fatherName" to fatherName.trim().take(120),
        "motherName" to motherName.trim().take(120),
        "dob" to dob.trim().take(30),
        "className" to className.trim().take(40),
        "section" to section.trim().uppercase(Locale.US).ifBlank { "A" }.take(10),
        "rollNo" to rollNo.coerceAtLeast(1).toLong(),
        "admissionDate" to admissionDate.trim().take(30),
        "address" to address.trim().take(300),
        "reasonForLeaving" to reasonForLeaving.trim().take(300),
        "lastAttendanceDateIso" to lastAttendanceDateIso.trim().take(20),
        "conduct" to conduct.trim().ifBlank { "Good" }.take(80),
        "promotionStatus" to promotionStatus.trim().take(120),
        "feeDuesStatus" to feeDuesStatus.trim().take(120),
        "remarks" to remarks.trim().take(300),
        "status" to CertificateStatus.ISSUED.wireValue,
        "issuedByUid" to issuedByUid,
        "issuedByName" to issuedByName.trim().take(120),
        "issuedAtMs" to issuedAtMs,
        "cancelledAtMs" to 0L,
        "cancelledByUid" to "",
        "cancelledByName" to "",
        "cancelReason" to "",
        "reissuedFromCertId" to reissuedFromCertId.trim().take(128),
        "reissuedFromCertNumber" to reissuedFromCertNumber.trim().take(60)
    )

    companion object {
        fun makeDocId(schoolId: String, session: String, certificateNumber: String): String =
            "cert_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(session)}_${StableDocId.sanitizeSegment(certificateNumber)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): SchoolCertificateRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val certNo = (data["certificateNumber"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || certNo.isEmpty()) return null
            return SchoolCertificateRecord(
                id = docId,
                schoolId = schoolId,
                session = (data["session"] as? String)?.trim().orEmpty(),
                certificateType = CertificateType.fromRaw(data["certificateType"] as? String),
                certificateNumber = certNo,
                deviceNodeCode = (data["deviceNodeCode"] as? String)?.trim().orEmpty(),
                sequenceNo = (data["sequenceNo"] as? Number)?.toInt() ?: 1,
                issueDateIso = (data["issueDateIso"] as? String)?.trim().orEmpty(),
                studentId = (data["studentId"] as? String)?.trim().orEmpty(),
                studentName = (data["studentName"] as? String)?.trim().orEmpty(),
                studentAdmNo = (data["studentAdmNo"] as? String)?.trim().orEmpty(),
                fatherName = (data["fatherName"] as? String)?.trim().orEmpty(),
                motherName = (data["motherName"] as? String)?.trim().orEmpty(),
                dob = (data["dob"] as? String)?.trim().orEmpty(),
                className = (data["className"] as? String)?.trim().orEmpty(),
                section = (data["section"] as? String)?.trim().orEmpty().ifBlank { "A" },
                rollNo = (data["rollNo"] as? Number)?.toInt() ?: 1,
                admissionDate = (data["admissionDate"] as? String)?.trim().orEmpty(),
                address = (data["address"] as? String)?.trim().orEmpty(),
                reasonForLeaving = (data["reasonForLeaving"] as? String)?.trim().orEmpty(),
                lastAttendanceDateIso = (data["lastAttendanceDateIso"] as? String)?.trim().orEmpty(),
                conduct = (data["conduct"] as? String)?.trim().orEmpty().ifBlank { "Good" },
                promotionStatus = (data["promotionStatus"] as? String)?.trim().orEmpty(),
                feeDuesStatus = (data["feeDuesStatus"] as? String)?.trim().orEmpty(),
                remarks = (data["remarks"] as? String)?.trim().orEmpty(),
                status = CertificateStatus.fromRaw(data["status"] as? String),
                issuedByUid = (data["issuedByUid"] as? String).orEmpty(),
                issuedByName = (data["issuedByName"] as? String).orEmpty(),
                issuedAtMs = (data["issuedAtMs"] as? Number)?.toLong() ?: 0L,
                cancelledAtMs = (data["cancelledAtMs"] as? Number)?.toLong() ?: 0L,
                cancelledByUid = (data["cancelledByUid"] as? String).orEmpty(),
                cancelledByName = (data["cancelledByName"] as? String).orEmpty(),
                cancelReason = (data["cancelReason"] as? String).orEmpty(),
                reissuedFromCertId = (data["reissuedFromCertId"] as? String).orEmpty(),
                reissuedFromCertNumber = (data["reissuedFromCertNumber"] as? String).orEmpty(),
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

object CertificateEngine {

    val DEFAULT_CONDUCT_OPTIONS: List<String> = listOf(
        "Good",
        "Very Good",
        "Excellent",
        "Satisfactory"
    )

    val DEFAULT_LEAVING_REASONS: List<String> = listOf(
        "Parent's request / family relocation",
        "Completed highest class in school",
        "Change of school",
        "Personal / domestic reason"
    )

    /**
     * Generates a collision-free running certificate number scoped per
     * `(schoolId, session, certificateType, deviceNodeCode)`.
     *
     * How duplicates are prevented even with 2+ phones offline:
     * 1. Each device/user installation derives a deterministic 3-char `deviceNodeCode` (e.g. `A1B`, `K4M`).
     * 2. Each certificate type (`TC`, `BON`, `CHR`) maintains its own independent sequence per
     *    `(schoolId, session, certificateType, deviceNodeCode)`:
     *    `nextSeq = max(highestObservedSeqForSchoolSessionTypeDevice, lastLocalSeqForType) + 1`.
     * 3. Formatted number: `{TYPE}-{SESSION_SHORT}-{DEVICE_NODE}-{SEQ_4_DIGIT}` (e.g. `TC-2627-A1B-0001`).
     * 4. Because every phone increments inside its own device namespace, two phones offline at the
     *    exact same time will issue `TC-2627-A1B-0001` and `TC-2627-K4M-0001` with zero collisions
     *    when they later sync to Firestore.
     */
    fun generateNextCertificateNumber(
        schoolId: String,
        session: String,
        certificateType: CertificateType,
        deviceNodeCode: String,
        existingCertificates: List<SchoolCertificateRecord>,
        lastLocalSequenceForType: Int = 0
    ): Pair<String, Int> {
        val cleanNode = deviceNodeCode.trim().uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]"), "")
            .take(4)
            .ifEmpty { "A1" }
        val digits = session.filter { it.isDigit() }
        val sessionShort = when {
            digits.length >= 8 -> digits.substring(2, 4) + digits.substring(6, 8) // "2026-2027" -> "2627"
            digits.length >= 4 -> digits.takeLast(4)
            else -> "2627"
        }
        val maxObserved = existingCertificates
            .filter {
                it.schoolId == schoolId &&
                    it.session.equals(session, ignoreCase = true) &&
                    it.certificateType == certificateType &&
                    it.deviceNodeCode.equals(cleanNode, ignoreCase = true)
            }
            .maxOfOrNull { it.sequenceNo } ?: 0
        val nextSeq = maxOf(maxObserved, lastLocalSequenceForType) + 1
        val padded = String.format(Locale.US, "%04d", nextSeq)
        return "${certificateType.numberPrefix}-$sessionShort-$cleanNode-$padded" to nextSeq
    }

    fun filterRegister(
        certificates: List<SchoolCertificateRecord>,
        schoolId: String,
        session: String? = null,
        typeFilter: CertificateType? = null,
        includeCancelled: Boolean = true,
        searchQuery: String = ""
    ): List<SchoolCertificateRecord> {
        val q = searchQuery.trim().lowercase(Locale.US)
        return certificates
            .filter { cert ->
                cert.schoolId == schoolId &&
                    (session.isNullOrBlank() || cert.session.equals(session, ignoreCase = true)) &&
                    (typeFilter == null || cert.certificateType == typeFilter) &&
                    (includeCancelled || !cert.isCancelled) &&
                    (q.isEmpty() ||
                        cert.certificateNumber.lowercase(Locale.US).contains(q) ||
                        cert.studentName.lowercase(Locale.US).contains(q) ||
                        cert.studentAdmNo.lowercase(Locale.US).contains(q) ||
                        cert.className.lowercase(Locale.US).contains(q) ||
                        cert.issuedByName.lowercase(Locale.US).contains(q))
            }
            .sortedWith(compareByDescending<SchoolCertificateRecord> { it.issuedAtMs }.thenByDescending { it.sequenceNo })
    }

    fun buildCancelUpdateValidation(cert: SchoolCertificateRecord, cancelReason: String): String? {
        if (cert.isCancelled) return "This certificate is already cancelled."
        if (cancelReason.trim().isEmpty()) return "Please enter a reason to cancel this certificate."
        return null
    }
}

// ============================================================================
// PART C: CASH BOOK (DAILY EXPENSES, AUTO FEE INCOME, DAY CLOSING, AUDIT LOG)
// ============================================================================

enum class CashBookEntryType(val wireValue: String, val englishLabel: String, val hindiLabel: String) {
    EXPENSE("EXPENSE", "Expense", "खर्च"),
    OTHER_INCOME("OTHER_INCOME", "Other Income", "अन्य आय");

    companion object {
        fun fromRaw(raw: String?): CashBookEntryType =
            if (raw.equals("OTHER_INCOME", ignoreCase = true)) OTHER_INCOME else EXPENSE
    }
}

enum class CashBookEntryStatus(val wireValue: String, val label: String) {
    ACTIVE("ACTIVE", "Active"),
    CANCELLED("CANCELLED", "Cancelled");

    companion object {
        fun fromRaw(raw: String?): CashBookEntryStatus =
            if (raw.equals("CANCELLED", ignoreCase = true)) CANCELLED else ACTIVE
    }
}

data class CashBookSettingsConfig(
    val id: String = "",
    val schoolId: String,
    val expenseCategories: List<String> = DEFAULT_EXPENSE_CATEGORIES,
    val otherIncomeCategories: List<String> = DEFAULT_OTHER_INCOME_CATEGORIES,
    val expenseApprovalLimitRs: Int = DEFAULT_APPROVAL_LIMIT_RS,
    val defaultOpeningBalanceRs: Int = 0,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(uid: String, editorName: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "expenseCategories" to expenseCategories.map { it.trim().take(60) }.filter { it.isNotEmpty() }.distinct().ifEmpty { DEFAULT_EXPENSE_CATEGORIES },
        "otherIncomeCategories" to otherIncomeCategories.map { it.trim().take(60) }.filter { it.isNotEmpty() }.distinct().ifEmpty { DEFAULT_OTHER_INCOME_CATEGORIES },
        "expenseApprovalLimitRs" to expenseApprovalLimitRs.coerceIn(0, 10_000_000).toLong(),
        "defaultOpeningBalanceRs" to defaultOpeningBalanceRs.coerceIn(-10_000_000, 100_000_000).toLong(),
        "updatedByUid" to uid,
        "updatedByName" to editorName.trim().take(120),
        "clientEditedAtMs" to nowMs
    )

    companion object {
        const val DEFAULT_APPROVAL_LIMIT_RS = 5000

        val DEFAULT_EXPENSE_CATEGORIES: List<String> = listOf(
            "Electricity",
            "Stationery",
            "Repairs",
            "Salary",
            "Transport Fuel",
            "Maintenance",
            "Refreshments",
            "Other"
        )

        val DEFAULT_OTHER_INCOME_CATEGORIES: List<String> = listOf(
            "Donation",
            "Admission Form Sale",
            "Book/Uniform Misc",
            "Hall/Rent",
            "Other Income"
        )

        fun makeDocId(schoolId: String): String = "cbset_${StableDocId.sanitizeSegment(schoolId)}"

        fun buildDefault(schoolId: String): CashBookSettingsConfig =
            CashBookSettingsConfig(id = makeDocId(schoolId), schoolId = schoolId)

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): CashBookSettingsConfig? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty()) return null
            val expCats = (data["expenseCategories"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
                .ifEmpty { DEFAULT_EXPENSE_CATEGORIES }
            val incCats = (data["otherIncomeCategories"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
                .ifEmpty { DEFAULT_OTHER_INCOME_CATEGORIES }
            return CashBookSettingsConfig(
                id = docId,
                schoolId = schoolId,
                expenseCategories = expCats,
                otherIncomeCategories = incCats,
                expenseApprovalLimitRs = (data["expenseApprovalLimitRs"] as? Number)?.toInt() ?: DEFAULT_APPROVAL_LIMIT_RS,
                defaultOpeningBalanceRs = (data["defaultOpeningBalanceRs"] as? Number)?.toInt() ?: 0,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class CashBookEntryRecord(
    val id: String,
    val schoolId: String,
    val dateIso: String, // YYYY-MM-DD
    val entryType: CashBookEntryType,
    val category: String,
    val amount: Int,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val note: String = "",
    val billPhotoBase64: String = "",
    val status: CashBookEntryStatus = CashBookEntryStatus.ACTIVE,
    val approvedByAdminUid: String = "",
    val approvedByAdminName: String = "",
    val approvalNote: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val createdAtMs: Long = 0L,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val lastEditReason: String = "",
    val clientEditedAtMs: Long = 0L,
    val cancelledAtMs: Long = 0L,
    val cancelledByUid: String = "",
    val cancelledByName: String = "",
    val cancelReason: String = "",
    val hasPendingWrites: Boolean = false,
    val sourceModule: String = "", // "" for general Cash Book, "TRANSPORT" for Transport expenses
    val transportRouteId: String = "",
    val vehicleNumber: String = "",
    val transportCategory: String = ""
) {
    val isCancelled: Boolean get() = status == CashBookEntryStatus.CANCELLED
    val isExpense: Boolean get() = entryType == CashBookEntryType.EXPENSE
    val isTransportExpense: Boolean get() =
        sourceModule.equals("TRANSPORT", ignoreCase = true) ||
            transportRouteId.isNotBlank() ||
            vehicleNumber.isNotBlank() ||
            transportCategory.isNotBlank()

    fun toCreateFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "dateIso" to dateIso.trim().take(10),
        "entryType" to entryType.wireValue,
        "category" to category.trim().take(80),
        "amount" to amount.coerceAtLeast(1).toLong(),
        "paymentMode" to paymentMode.wireValue,
        "note" to note.trim().take(300),
        "billPhotoBase64" to billPhotoBase64.take(260_000),
        "status" to CashBookEntryStatus.ACTIVE.wireValue,
        "approvedByAdminUid" to approvedByAdminUid.trim().take(128),
        "approvedByAdminName" to approvedByAdminName.trim().take(120),
        "approvalNote" to approvalNote.trim().take(200),
        "createdByUid" to createdByUid,
        "createdByName" to createdByName.trim().take(120),
        "createdAtMs" to createdAtMs,
        "updatedByUid" to updatedByUid.ifBlank { createdByUid },
        "updatedByName" to updatedByName.ifBlank { createdByName }.trim().take(120),
        "lastEditReason" to "",
        "clientEditedAtMs" to createdAtMs,
        "cancelledAtMs" to 0L,
        "cancelledByUid" to "",
        "cancelledByName" to "",
        "cancelReason" to "",
        "sourceModule" to sourceModule.trim().uppercase(Locale.US).take(24),
        "transportRouteId" to transportRouteId.trim().take(128),
        "vehicleNumber" to vehicleNumber.trim().take(40),
        "transportCategory" to transportCategory.trim().take(80)
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): CashBookEntryRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val dateIso = (data["dateIso"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || dateIso.isEmpty()) return null
            return CashBookEntryRecord(
                id = docId,
                schoolId = schoolId,
                dateIso = dateIso,
                entryType = CashBookEntryType.fromRaw(data["entryType"] as? String),
                category = (data["category"] as? String)?.trim().orEmpty().ifBlank { "Other" },
                amount = (data["amount"] as? Number)?.toInt() ?: 0,
                paymentMode = PaymentMode.fromRaw(data["paymentMode"] as? String),
                note = (data["note"] as? String).orEmpty(),
                billPhotoBase64 = (data["billPhotoBase64"] as? String).orEmpty(),
                status = CashBookEntryStatus.fromRaw(data["status"] as? String),
                approvedByAdminUid = (data["approvedByAdminUid"] as? String).orEmpty(),
                approvedByAdminName = (data["approvedByAdminName"] as? String).orEmpty(),
                approvalNote = (data["approvalNote"] as? String).orEmpty(),
                createdByUid = (data["createdByUid"] as? String).orEmpty(),
                createdByName = (data["createdByName"] as? String).orEmpty(),
                createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: 0L,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                lastEditReason = (data["lastEditReason"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                cancelledAtMs = (data["cancelledAtMs"] as? Number)?.toLong() ?: 0L,
                cancelledByUid = (data["cancelledByUid"] as? String).orEmpty(),
                cancelledByName = (data["cancelledByName"] as? String).orEmpty(),
                cancelReason = (data["cancelReason"] as? String).orEmpty(),
                hasPendingWrites = hasPendingWrites,
                sourceModule = (data["sourceModule"] as? String)?.trim().orEmpty(),
                transportRouteId = (data["transportRouteId"] as? String)?.trim().orEmpty(),
                vehicleNumber = (data["vehicleNumber"] as? String)?.trim().orEmpty(),
                transportCategory = (data["transportCategory"] as? String)?.trim().orEmpty()
            )
        }
    }
}

data class CashBookDayClosingRecord(
    val id: String,
    val schoolId: String,
    val dateIso: String, // YYYY-MM-DD
    val openingCashBalance: Int,
    val cashInTotal: Int,
    val cashOutTotal: Int,
    val expectedClosingCash: Int,
    val countedCash: Int,
    val cashDifference: Int, // countedCash - expectedClosingCash
    val nonCashInTotal: Int = 0,
    val nonCashOutTotal: Int = 0,
    val note: String = "",
    val closedByUid: String = "",
    val closedByName: String = "",
    val closedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "dateIso" to dateIso.trim().take(10),
        "openingCashBalance" to openingCashBalance.toLong(),
        "cashInTotal" to cashInTotal.coerceAtLeast(0).toLong(),
        "cashOutTotal" to cashOutTotal.coerceAtLeast(0).toLong(),
        "expectedClosingCash" to expectedClosingCash.toLong(),
        "countedCash" to countedCash.toLong(),
        "cashDifference" to cashDifference.toLong(),
        "nonCashInTotal" to nonCashInTotal.coerceAtLeast(0).toLong(),
        "nonCashOutTotal" to nonCashOutTotal.coerceAtLeast(0).toLong(),
        "note" to note.trim().take(300),
        "closedByUid" to closedByUid,
        "closedByName" to closedByName.trim().take(120),
        "closedAtMs" to closedAtMs
    )

    companion object {
        fun makeDocId(schoolId: String, dateIso: String): String =
            "cbcls_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(dateIso)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): CashBookDayClosingRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val dateIso = (data["dateIso"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || dateIso.isEmpty()) return null
            return CashBookDayClosingRecord(
                id = docId,
                schoolId = schoolId,
                dateIso = dateIso,
                openingCashBalance = (data["openingCashBalance"] as? Number)?.toInt() ?: 0,
                cashInTotal = (data["cashInTotal"] as? Number)?.toInt() ?: 0,
                cashOutTotal = (data["cashOutTotal"] as? Number)?.toInt() ?: 0,
                expectedClosingCash = (data["expectedClosingCash"] as? Number)?.toInt() ?: 0,
                countedCash = (data["countedCash"] as? Number)?.toInt() ?: 0,
                cashDifference = (data["cashDifference"] as? Number)?.toInt() ?: 0,
                nonCashInTotal = (data["nonCashInTotal"] as? Number)?.toInt() ?: 0,
                nonCashOutTotal = (data["nonCashOutTotal"] as? Number)?.toInt() ?: 0,
                note = (data["note"] as? String).orEmpty(),
                closedByUid = (data["closedByUid"] as? String).orEmpty(),
                closedByName = (data["closedByName"] as? String).orEmpty(),
                closedAtMs = (data["closedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class CashBookAuditLogRecord(
    val id: String,
    val schoolId: String,
    val action: String, // EXPENSE_ADDED, INCOME_ADDED, ENTRY_EDITED, ENTRY_CANCELLED, DAY_CLOSED, SETTINGS_UPDATED
    val targetId: String,
    val dateIso: String,
    val amount: Int,
    val reason: String,
    val summary: String,
    val actorUid: String,
    val actorName: String,
    val clientCreatedAtMs: Long,
    val hasPendingWrites: Boolean = false,
    val sourceModule: String = ""
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "action" to action.trim().take(60),
        "targetId" to targetId.trim().take(128),
        "dateIso" to dateIso.trim().take(10),
        "amount" to amount.toLong(),
        "reason" to reason.trim().take(300),
        "summary" to summary.trim().take(400),
        "actorUid" to actorUid,
        "actorName" to actorName.trim().take(120),
        "clientCreatedAtMs" to clientCreatedAtMs,
        "sourceModule" to sourceModule.trim().uppercase(Locale.US).take(24)
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): CashBookAuditLogRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val action = (data["action"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || action.isEmpty()) return null
            return CashBookAuditLogRecord(
                id = docId,
                schoolId = schoolId,
                action = action,
                targetId = (data["targetId"] as? String).orEmpty(),
                dateIso = (data["dateIso"] as? String).orEmpty(),
                amount = (data["amount"] as? Number)?.toInt() ?: 0,
                reason = (data["reason"] as? String).orEmpty(),
                summary = (data["summary"] as? String).orEmpty(),
                actorUid = (data["actorUid"] as? String).orEmpty(),
                actorName = (data["actorName"] as? String).orEmpty(),
                clientCreatedAtMs = (data["clientCreatedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites,
                sourceModule = (data["sourceModule"] as? String)?.trim().orEmpty()
            )
        }
    }
}

data class CategoryExpenseTotal(
    val category: String,
    val totalAmount: Int,
    val entryCount: Int,
    val sharePercent: Double
)

data class CashBookDaySummary(
    val dateIso: String,
    val openingBalance: Int,
    val feeIncomeReceipts: List<FeeReceiptRecord>,
    val activeOtherIncomes: List<CashBookEntryRecord>,
    val activeExpenses: List<CashBookEntryRecord>,
    val cancelledEntries: List<CashBookEntryRecord>,
    val feeIncomeTotal: Int,
    val otherIncomeTotal: Int,
    val totalMoneyIn: Int,
    val totalMoneyOut: Int,
    val netDayBalance: Int,
    val closingBalance: Int,
    val cashFeeIncome: Int,
    val cashOtherIncome: Int,
    val totalCashIn: Int,
    val totalCashOut: Int,
    val expectedClosingCash: Int,
    val nonCashInTotal: Int,
    val nonCashOutTotal: Int,
    val savedClosing: CashBookDayClosingRecord?
)

data class CashBookDailyRollupRow(
    val dateIso: String,
    val feeIncome: Int,
    val otherIncome: Int,
    val totalIn: Int,
    val totalOut: Int,
    val netDay: Int
)

data class CashBookMonthSummary(
    val yearMonth: String, // YYYY-MM
    val totalFeeIncome: Int,
    val totalOtherIncome: Int,
    val totalMoneyIn: Int,
    val totalMoneyOut: Int,
    val netMonthBalance: Int,
    val categoryTotals: List<CategoryExpenseTotal>,
    val dailyRows: List<CashBookDailyRollupRow>,
    val activeEntries: List<CashBookEntryRecord>,
    val activeReceiptsCount: Int
)

object CashBookEngine {

    /**
     * Resolves the opening balance for [dateIso]:
     * 1. If a saved closing exists for [dateIso], returns its recorded openingCashBalance.
     * 2. Otherwise finds the latest saved day closing strictly before [dateIso] and uses its
     *    countedCash (actual physical cash carried forward) if recorded, else expectedClosingCash.
     * 3. If no prior day closing exists, starts from [settings.defaultOpeningBalanceRs] plus net
     *    cash flows of all prior dates strictly before [dateIso] (so balances never lose track).
     */
    fun resolveOpeningCashBalance(
        schoolId: String,
        dateIso: String,
        feeReceipts: List<FeeReceiptRecord>,
        entries: List<CashBookEntryRecord>,
        dayClosings: List<CashBookDayClosingRecord>,
        settings: CashBookSettingsConfig
    ): Int {
        val exactClosing = dayClosings.firstOrNull { it.schoolId == schoolId && it.dateIso == dateIso }
        if (exactClosing != null) return exactClosing.openingCashBalance

        val latestPriorClosing = dayClosings
            .filter { it.schoolId == schoolId && it.dateIso < dateIso }
            .maxByOrNull { it.dateIso }

        val anchorDate = latestPriorClosing?.dateIso
        val baseCash = latestPriorClosing?.countedCash ?: settings.defaultOpeningBalanceRs

        val intermediateCashInFees = feeReceipts
            .filter {
                it.schoolId == schoolId &&
                    !it.isCancelled &&
                    it.paymentMode == PaymentMode.CASH &&
                    it.receiptDateIso < dateIso &&
                    (anchorDate == null || it.receiptDateIso > anchorDate)
            }
            .sumOf { it.amountPaid }

        val intermediateEntries = entries.filter {
            it.schoolId == schoolId &&
                !it.isCancelled &&
                it.paymentMode == PaymentMode.CASH &&
                it.dateIso < dateIso &&
                (anchorDate == null || it.dateIso > anchorDate)
        }
        val intermediateOtherCashIn = intermediateEntries.filter { !it.isExpense }.sumOf { it.amount }
        val intermediateCashOut = intermediateEntries.filter { it.isExpense }.sumOf { it.amount }

        return baseCash + intermediateCashInFees + intermediateOtherCashIn - intermediateCashOut
    }

    fun computeDaySummary(
        schoolId: String,
        dateIso: String,
        feeReceipts: List<FeeReceiptRecord>,
        entries: List<CashBookEntryRecord>,
        dayClosings: List<CashBookDayClosingRecord>,
        settings: CashBookSettingsConfig,
        openingBalanceOverride: Int? = null
    ): CashBookDaySummary {
        val dayReceipts = feeReceipts
            .filter { it.schoolId == schoolId && !it.isCancelled && it.receiptDateIso == dateIso }
            .sortedBy { it.issuedAtMs }

        val dayEntries = entries
            .filter { it.schoolId == schoolId && it.dateIso == dateIso }
            .sortedByDescending { it.createdAtMs }

        val activeExpenses = dayEntries.filter { !it.isCancelled && it.isExpense }
        val activeOtherIncomes = dayEntries.filter { !it.isCancelled && !it.isExpense }
        val cancelledEntries = dayEntries.filter { it.isCancelled }

        val feeIncomeTotal = dayReceipts.sumOf { it.amountPaid }
        val otherIncomeTotal = activeOtherIncomes.sumOf { it.amount }
        val totalMoneyIn = feeIncomeTotal + otherIncomeTotal
        val totalMoneyOut = activeExpenses.sumOf { it.amount }
        val netDayBalance = totalMoneyIn - totalMoneyOut

        val cashFeeIncome = dayReceipts.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.amountPaid }
        val cashOtherIncome = activeOtherIncomes.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.amount }
        val totalCashIn = cashFeeIncome + cashOtherIncome
        val totalCashOut = activeExpenses.filter { it.paymentMode == PaymentMode.CASH }.sumOf { it.amount }

        val nonCashIn = totalMoneyIn - totalCashIn
        val nonCashOut = totalMoneyOut - totalCashOut

        val opening = openingBalanceOverride ?: resolveOpeningCashBalance(
            schoolId = schoolId,
            dateIso = dateIso,
            feeReceipts = feeReceipts,
            entries = entries,
            dayClosings = dayClosings,
            settings = settings
        )
        val expectedClosingCash = opening + totalCashIn - totalCashOut
        val closingBalance = opening + netDayBalance
        val savedClosing = dayClosings.firstOrNull { it.schoolId == schoolId && it.dateIso == dateIso }

        return CashBookDaySummary(
            dateIso = dateIso,
            openingBalance = opening,
            feeIncomeReceipts = dayReceipts,
            activeOtherIncomes = activeOtherIncomes,
            activeExpenses = activeExpenses,
            cancelledEntries = cancelledEntries,
            feeIncomeTotal = feeIncomeTotal,
            otherIncomeTotal = otherIncomeTotal,
            totalMoneyIn = totalMoneyIn,
            totalMoneyOut = totalMoneyOut,
            netDayBalance = netDayBalance,
            closingBalance = closingBalance,
            cashFeeIncome = cashFeeIncome,
            cashOtherIncome = cashOtherIncome,
            totalCashIn = totalCashIn,
            totalCashOut = totalCashOut,
            expectedClosingCash = expectedClosingCash,
            nonCashInTotal = nonCashIn,
            nonCashOutTotal = nonCashOut,
            savedClosing = savedClosing
        )
    }

    fun computeDayClosingDifference(expectedClosingCash: Int, countedCash: Int): Int =
        countedCash - expectedClosingCash

    fun computeMonthSummary(
        schoolId: String,
        yearMonth: String, // YYYY-MM
        feeReceipts: List<FeeReceiptRecord>,
        entries: List<CashBookEntryRecord>
    ): CashBookMonthSummary {
        val prefix = "$yearMonth-"
        val monthReceipts = feeReceipts.filter {
            it.schoolId == schoolId && !it.isCancelled && it.receiptDateIso.startsWith(prefix)
        }
        val monthActiveEntries = entries.filter {
            it.schoolId == schoolId && !it.isCancelled && it.dateIso.startsWith(prefix)
        }.sortedByDescending { it.dateIso }

        val monthExpenses = monthActiveEntries.filter { it.isExpense }
        val monthOtherIncomes = monthActiveEntries.filter { !it.isExpense }

        val totalFeeIncome = monthReceipts.sumOf { it.amountPaid }
        val totalOtherIncome = monthOtherIncomes.sumOf { it.amount }
        val totalIn = totalFeeIncome + totalOtherIncome
        val totalOut = monthExpenses.sumOf { it.amount }

        val categoryTotals = monthExpenses
            .groupBy { it.category.trim().ifBlank { "Other" } }
            .map { (cat, list) ->
                val sum = list.sumOf { it.amount }
                val pct = if (totalOut > 0) (sum * 100.0) / totalOut else 0.0
                CategoryExpenseTotal(
                    category = cat,
                    totalAmount = sum,
                    entryCount = list.size,
                    sharePercent = (pct * 10.0).roundToInt() / 10.0
                )
            }
            .sortedByDescending { it.totalAmount }

        val allDates = (monthReceipts.map { it.receiptDateIso } + monthActiveEntries.map { it.dateIso })
            .distinct()
            .sorted()

        val dailyRows = allDates.map { d ->
            val fIn = monthReceipts.filter { it.receiptDateIso == d }.sumOf { it.amountPaid }
            val oIn = monthOtherIncomes.filter { it.dateIso == d }.sumOf { it.amount }
            val out = monthExpenses.filter { it.dateIso == d }.sumOf { it.amount }
            CashBookDailyRollupRow(
                dateIso = d,
                feeIncome = fIn,
                otherIncome = oIn,
                totalIn = fIn + oIn,
                totalOut = out,
                netDay = (fIn + oIn) - out
            )
        }

        return CashBookMonthSummary(
            yearMonth = yearMonth,
            totalFeeIncome = totalFeeIncome,
            totalOtherIncome = totalOtherIncome,
            totalMoneyIn = totalIn,
            totalMoneyOut = totalOut,
            netMonthBalance = totalIn - totalOut,
            categoryTotals = categoryTotals,
            dailyRows = dailyRows,
            activeEntries = monthActiveEntries,
            activeReceiptsCount = monthReceipts.size
        )
    }

    fun validateNewOrEditedEntry(
        entryType: CashBookEntryType,
        category: String,
        amount: Int,
        member: MemberAccessProfile?,
        approvalLimitRs: Int,
        approvedByAdminUid: String,
        editReason: String? = null // non-null when editing existing entry
    ): String? {
        if (category.trim().isEmpty()) return "Please choose a category."
        if (amount <= 0) return "Amount must be greater than ₹0."
        if (editReason != null && editReason.trim().isEmpty()) {
            return "Please enter a reason for editing this entry."
        }
        if (entryType == CashBookEntryType.EXPENSE) {
            val hasApproval = approvedByAdminUid.trim().isNotEmpty()
            if (!Phase10AccessEvaluator.isExpenseWithinMemberLimit(member, amount, approvalLimitRs, hasApproval)) {
                return "Expenses above ₹$approvalLimitRs require Admin approval."
            }
        }
        return null
    }

    fun buildDayBookCsv(schoolName: String, summary: CashBookDaySummary): String {
        fun esc(v: String): String {
            val c = v.replace("\"", "\"\"")
            return if (c.contains(',') || c.contains('"') || c.contains('\n')) "\"$c\"" else c
        }
        val sb = StringBuilder()
        sb.appendLine("School,Date,Type,Category/Receipt,Mode,Money In (Rs),Money Out (Rs),Note,Recorded By")
        for (rcp in summary.feeIncomeReceipts) {
            sb.appendLine(
                listOf(
                    esc(schoolName), summary.dateIso, "Fee Receipt", esc(rcp.receiptNumber),
                    rcp.paymentMode.label, rcp.amountPaid.toString(), "0",
                    esc("${rcp.studentName} (${rcp.className}-${rcp.section})"), esc(rcp.receivedByName)
                ).joinToString(",")
            )
        }
        for (inc in summary.activeOtherIncomes) {
            sb.appendLine(
                listOf(
                    esc(schoolName), summary.dateIso, "Other Income", esc(inc.category),
                    inc.paymentMode.label, inc.amount.toString(), "0",
                    esc(inc.note), esc(inc.createdByName)
                ).joinToString(",")
            )
        }
        for (exp in summary.activeExpenses) {
            sb.appendLine(
                listOf(
                    esc(schoolName), summary.dateIso, "Expense", esc(exp.category),
                    exp.paymentMode.label, "0", exp.amount.toString(),
                    esc(exp.note), esc(exp.createdByName)
                ).joinToString(",")
            )
        }
        sb.appendLine(
            listOf(
                esc(schoolName), summary.dateIso, "SUMMARY", "Totals", "",
                summary.totalMoneyIn.toString(), summary.totalMoneyOut.toString(),
                "Opening=${summary.openingBalance}; ClosingCash=${summary.expectedClosingCash}", ""
            ).joinToString(",")
        )
        return sb.toString()
    }

    fun buildMonthCategoryCsv(schoolName: String, summary: CashBookMonthSummary): String {
        fun esc(v: String): String {
            val c = v.replace("\"", "\"\"")
            return if (c.contains(',') || c.contains('"') || c.contains('\n')) "\"$c\"" else c
        }
        val sb = StringBuilder()
        sb.appendLine("School,Month,Category,Entries,Amount (Rs),Share (%)")
        for (row in summary.categoryTotals) {
            sb.appendLine(
                listOf(
                    esc(schoolName), summary.yearMonth, esc(row.category),
                    row.entryCount.toString(), row.totalAmount.toString(), row.sharePercent.toString()
                ).joinToString(",")
            )
        }
        sb.appendLine(
            listOf(
                esc(schoolName), summary.yearMonth, "TOTAL EXPENSES",
                summary.categoryTotals.sumOf { it.entryCount }.toString(),
                summary.totalMoneyOut.toString(), "100.0"
            ).joinToString(",")
        )
        return sb.toString()
    }
}

// ============================================================================
// PART D: ADMISSION ENQUIRY (FOLLOW-UPS, WHATSAPP/CALL, CONVERT TO STUDENT)
// ============================================================================

enum class EnquiryStatus(val wireValue: String, val englishLabel: String, val hindiLabel: String) {
    NEW("NEW", "New", "नया"),
    CONTACTED("CONTACTED", "Contacted", "बात हुई"),
    VISIT_PLANNED("VISIT_PLANNED", "Visit Planned", "विजिट तय"),
    ADMITTED("ADMITTED", "Admitted", "प्रवेश हो गया"),
    LOST("LOST", "Lost", "रद्द / नहीं आए");

    val isOpenForFollowUp: Boolean get() = this == NEW || this == CONTACTED || this == VISIT_PLANNED

    companion object {
        fun fromRaw(raw: String?): EnquiryStatus =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: NEW
    }
}

data class EnquirySettingsConfig(
    val id: String = "",
    val schoolId: String,
    val sources: List<String> = DEFAULT_SOURCES,
    val whatsappTemplate: String = DEFAULT_WHATSAPP_TEMPLATE,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(uid: String, editorName: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "sources" to sources.map { it.trim().take(60) }.filter { it.isNotEmpty() }.distinct().ifEmpty { DEFAULT_SOURCES },
        "whatsappTemplate" to whatsappTemplate.trim().ifBlank { DEFAULT_WHATSAPP_TEMPLATE }.take(500),
        "updatedByUid" to uid,
        "updatedByName" to editorName.trim().take(120),
        "clientEditedAtMs" to nowMs
    )

    companion object {
        val DEFAULT_SOURCES: List<String> = listOf(
            "Walk-in",
            "Parent Referral",
            "Phone Call",
            "WhatsApp",
            "Banner / Pamphlet",
            "Social Media",
            "Other"
        )

        const val DEFAULT_WHATSAPP_TEMPLATE =
            "Namaste {guardian} ji, thank you for your admission enquiry for {child} (Class {class}) at {school}. Please visit the school office or reply here for admission details."

        fun makeDocId(schoolId: String): String = "enqset_${StableDocId.sanitizeSegment(schoolId)}"

        fun buildDefault(schoolId: String): EnquirySettingsConfig =
            EnquirySettingsConfig(id = makeDocId(schoolId), schoolId = schoolId)

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): EnquirySettingsConfig? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty()) return null
            val parsedSources = (data["sources"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
                .ifEmpty { DEFAULT_SOURCES }
            val tpl = (data["whatsappTemplate"] as? String)?.trim().orEmpty().ifBlank { DEFAULT_WHATSAPP_TEMPLATE }
            return EnquirySettingsConfig(
                id = docId,
                schoolId = schoolId,
                sources = parsedSources,
                whatsappTemplate = tpl,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class AdmissionEnquiryRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val childName: String,
    val classWanted: String,
    val guardianName: String,
    val mobile: String,
    val source: String = "Walk-in",
    val notes: String = "",
    val enquiryDateIso: String, // YYYY-MM-DD
    val nextFollowUpDateIso: String, // YYYY-MM-DD
    val status: EnquiryStatus = EnquiryStatus.NEW,
    val lostReason: String = "",
    val convertedStudentDocId: String = "",
    val convertedStudentAdmNo: String = "",
    val convertedAtMs: Long = 0L,
    val createdByUid: String = "",
    val createdByName: String = "",
    val createdAtMs: Long = 0L,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    val isConverted: Boolean get() = convertedStudentDocId.isNotBlank() || status == EnquiryStatus.ADMITTED

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "childName" to childName.trim().take(120),
        "classWanted" to classWanted.trim().take(40),
        "guardianName" to guardianName.trim().take(120),
        "mobile" to mobile.trim().take(24),
        "source" to source.trim().ifBlank { "Walk-in" }.take(60),
        "notes" to notes.trim().take(400),
        "enquiryDateIso" to enquiryDateIso.trim().take(10),
        "nextFollowUpDateIso" to nextFollowUpDateIso.trim().take(10),
        "status" to status.wireValue,
        "lostReason" to lostReason.trim().take(240),
        "convertedStudentDocId" to convertedStudentDocId.trim().take(128),
        "convertedStudentAdmNo" to convertedStudentAdmNo.trim().take(60),
        "convertedAtMs" to convertedAtMs,
        "createdByUid" to createdByUid,
        "createdByName" to createdByName.trim().take(120),
        "createdAtMs" to createdAtMs,
        "updatedByUid" to updatedByUid.ifBlank { createdByUid },
        "updatedByName" to updatedByName.ifBlank { createdByName }.trim().take(120),
        "clientEditedAtMs" to clientEditedAtMs
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): AdmissionEnquiryRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val childName = (data["childName"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || childName.isEmpty()) return null
            return AdmissionEnquiryRecord(
                id = docId,
                schoolId = schoolId,
                session = (data["session"] as? String)?.trim().orEmpty(),
                childName = childName,
                classWanted = (data["classWanted"] as? String)?.trim().orEmpty(),
                guardianName = (data["guardianName"] as? String)?.trim().orEmpty(),
                mobile = (data["mobile"] as? String)?.trim().orEmpty(),
                source = (data["source"] as? String)?.trim().orEmpty().ifBlank { "Walk-in" },
                notes = (data["notes"] as? String).orEmpty(),
                enquiryDateIso = (data["enquiryDateIso"] as? String)?.trim().orEmpty(),
                nextFollowUpDateIso = (data["nextFollowUpDateIso"] as? String)?.trim().orEmpty(),
                status = EnquiryStatus.fromRaw(data["status"] as? String),
                lostReason = (data["lostReason"] as? String).orEmpty(),
                convertedStudentDocId = (data["convertedStudentDocId"] as? String).orEmpty(),
                convertedStudentAdmNo = (data["convertedStudentAdmNo"] as? String).orEmpty(),
                convertedAtMs = (data["convertedAtMs"] as? Number)?.toLong() ?: 0L,
                createdByUid = (data["createdByUid"] as? String).orEmpty(),
                createdByName = (data["createdByName"] as? String).orEmpty(),
                createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: 0L,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

enum class FollowUpUrgency(val priorityRank: Int, val label: String) {
    OVERDUE(0, "Overdue"),
    DUE_TODAY(1, "Today"),
    UPCOMING(2, "Upcoming")
}

data class EnquiryFollowUpRow(
    val enquiry: AdmissionEnquiryRecord,
    val urgency: FollowUpUrgency,
    val daysOverdue: Int,
    val phoneValidation: PhoneValidationResult,
    val whatsappMessageText: String,
    val waMeUrl: String?
)

data class EnquiryMonthlySummary(
    val yearMonth: String,
    val totalEnquiriesThisMonth: Int,
    val admittedCount: Int,
    val lostCount: Int,
    val openCount: Int,
    val overdueCount: Int,
    val dueTodayCount: Int,
    val conversionPercent: Double
)

object AdmissionEnquiryEngine {

    fun fillEnquiryTemplate(
        template: String,
        guardianName: String,
        childName: String,
        classWanted: String,
        schoolName: String
    ): String {
        val safeGuardian = guardianName.trim().ifBlank { "Parent" }
        return template
            .replace("{guardian}", safeGuardian, ignoreCase = true)
            .replace("{child}", childName.trim(), ignoreCase = true)
            .replace("{student}", childName.trim(), ignoreCase = true)
            .replace("{class}", classWanted.trim().ifBlank { "-" }, ignoreCase = true)
            .replace("{school}", schoolName.trim(), ignoreCase = true)
    }

    private fun isoToEpochDay(isoDate: String): Long? {
        val parts = isoDate.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        val cal = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(y, m - 1, d, 0, 0, 0)
        }
        return cal.timeInMillis / 86_400_000L
    }

    /**
     * Builds the "Follow up today" queue with OVERDUE enquiries strictly first (oldest overdue at top),
     * followed by DUE TODAY enquiries, and optionally UPCOMING enquiries.
     * Excludes closed statuses (`ADMITTED`, `LOST`).
     */
    fun buildFollowUpQueue(
        enquiries: List<AdmissionEnquiryRecord>,
        schoolId: String,
        schoolName: String,
        todayIso: String,
        template: String,
        includeUpcoming: Boolean = false
    ): List<EnquiryFollowUpRow> {
        val todayDay = isoToEpochDay(todayIso) ?: 0L
        return enquiries
            .filter { it.schoolId == schoolId && it.status.isOpenForFollowUp }
            .mapNotNull { enq ->
                val followDate = enq.nextFollowUpDateIso.ifBlank { enq.enquiryDateIso }
                val followDay = isoToEpochDay(followDate) ?: todayDay
                val diff = (todayDay - followDay).toInt()
                val urgency = when {
                    diff > 0 -> FollowUpUrgency.OVERDUE
                    diff == 0 -> FollowUpUrgency.DUE_TODAY
                    else -> FollowUpUrgency.UPCOMING
                }
                if (!includeUpcoming && urgency == FollowUpUrgency.UPCOMING) return@mapNotNull null

                val phoneVal = PhoneNormalizer.normalizeIndianMobile(enq.mobile)
                val msg = fillEnquiryTemplate(
                    template = template,
                    guardianName = enq.guardianName,
                    childName = enq.childName,
                    classWanted = enq.classWanted,
                    schoolName = schoolName
                )
                val waUrl = (phoneVal as? PhoneValidationResult.Valid)?.let { v ->
                    PhoneNormalizer.buildWaMeLink(v.waMeDigits, msg)
                }
                EnquiryFollowUpRow(
                    enquiry = enq,
                    urgency = urgency,
                    daysOverdue = diff.coerceAtLeast(0),
                    phoneValidation = phoneVal,
                    whatsappMessageText = msg,
                    waMeUrl = waUrl
                )
            }
            .sortedWith(
                compareBy<EnquiryFollowUpRow> { it.urgency.priorityRank }
                    .thenBy { it.enquiry.nextFollowUpDateIso.ifBlank { it.enquiry.enquiryDateIso } }
                    .thenByDescending { it.enquiry.createdAtMs }
            )
    }

    fun computeMonthlySummary(
        enquiries: List<AdmissionEnquiryRecord>,
        schoolId: String,
        todayIso: String,
        yearMonth: String = todayIso.take(7)
    ): EnquiryMonthlySummary {
        val schoolEnqs = enquiries.filter { it.schoolId == schoolId }
        val prefix = "$yearMonth-"
        val thisMonth = schoolEnqs.filter { it.enquiryDateIso.startsWith(prefix) }
        val admitted = thisMonth.count { it.status == EnquiryStatus.ADMITTED }
        val lost = thisMonth.count { it.status == EnquiryStatus.LOST }
        val open = thisMonth.count { it.status.isOpenForFollowUp }
        val convPct = if (thisMonth.isNotEmpty()) {
            ((admitted * 100.0) / thisMonth.size * 10.0).roundToInt() / 10.0
        } else 0.0

        val todayDay = isoToEpochDay(todayIso) ?: 0L
        var overdueCount = 0
        var dueTodayCount = 0
        for (enq in schoolEnqs) {
            if (!enq.status.isOpenForFollowUp) continue
            val d = isoToEpochDay(enq.nextFollowUpDateIso.ifBlank { enq.enquiryDateIso }) ?: todayDay
            when {
                d < todayDay -> overdueCount++
                d == todayDay -> dueTodayCount++
            }
        }

        return EnquiryMonthlySummary(
            yearMonth = yearMonth,
            totalEnquiriesThisMonth = thisMonth.size,
            admittedCount = admitted,
            lostCount = lost,
            openCount = open,
            overdueCount = overdueCount,
            dueTodayCount = dueTodayCount,
            conversionPercent = convPct
        )
    }

    /**
     * Converts an [AdmissionEnquiryRecord] into a Student Register [StudentEntity],
     * preserving the enquiry's child name, class wanted, guardian name, mobile, and admission date.
     */
    fun buildStudentFromEnquiry(
        enquiry: AdmissionEnquiryRecord,
        schoolLongId: Long,
        classId: Long,
        session: String,
        admNo: String,
        section: String,
        rollNo: Int,
        admissionDateIso: String
    ): StudentEntity {
        val normalizedPhone = when (val p = PhoneNormalizer.normalizeIndianMobile(enquiry.mobile)) {
            is PhoneValidationResult.Valid -> p.tenDigitNational
            is PhoneValidationResult.Invalid -> enquiry.mobile.trim()
        }
        return StudentEntity(
            id = 0L,
            schoolId = schoolLongId,
            classId = classId,
            session = session.trim(),
            admNo = admNo.trim(),
            name = enquiry.childName.trim(),
            fatherName = enquiry.guardianName.trim(),
            motherName = "",
            dob = "",
            mobile = normalizedPhone,
            secondMobile = "",
            address = "",
            className = enquiry.classWanted.trim(),
            section = section.trim().uppercase(Locale.US).ifBlank { "A" },
            rollNo = rollNo.coerceAtLeast(1),
            admissionDate = admissionDateIso.trim(),
            previousSchool = "",
            status = StudentRegisterStatus.ACTIVE.code
        )
    }
}
