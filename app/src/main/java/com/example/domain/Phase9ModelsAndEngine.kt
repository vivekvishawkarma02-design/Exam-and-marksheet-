package com.example.domain

import com.example.data.StudentEntity
import com.example.data.cloud.StableDocId
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * PHASE 9: STUDENT REGISTER AND FEE MANAGEMENT
 * Pure Kotlin domain models, calculation engines, receipt numbering, due engines,
 * WhatsApp/SMS template builders, and access evaluators (zero Android UI / Firebase SDK coupling
 * for fast, deterministic unit testing).
 */

// ============================================================================
// PART A: STUDENT REGISTER MODELS & HELPERS
// ============================================================================

enum class StudentRegisterStatus(val code: String, val label: String) {
    ACTIVE("ACTIVE", "Active"),
    LEFT("LEFT", "Left School");

    companion object {
        fun fromRaw(raw: String?): StudentRegisterStatus =
            if (raw.equals("LEFT", ignoreCase = true)) LEFT else ACTIVE
    }
}

enum class StudentRegisterStatusFilter(val label: String) {
    ACTIVE_ONLY("Active Only"),
    LEFT_ONLY("Left Only"),
    ALL("All Students")
}

enum class StudentTransportFilter(val label: String) {
    ALL("All"),
    TRANSPORT_USERS("Uses Transport"),
    NON_TRANSPORT("No Transport")
}

// ============================================================================
// PART B: FEE STRUCTURE MODELS (Per School and Session)
// ============================================================================

enum class SessionMonthCode(val code: String, val shortLabel: String, val fullLabel: String) {
    APR("APR", "Apr", "April"),
    MAY("MAY", "May", "May"),
    JUN("JUN", "Jun", "June"),
    JUL("JUL", "Jul", "July"),
    AUG("AUG", "Aug", "August"),
    SEP("SEP", "Sep", "September"),
    OCT("OCT", "Oct", "October"),
    NOV("NOV", "Nov", "November"),
    DEC("DEC", "Dec", "December"),
    JAN("JAN", "Jan", "January"),
    FEB("FEB", "Feb", "February"),
    MAR("MAR", "Mar", "March");

    companion object {
        val defaultAprilToMarchOrder: List<String> = entries.map { it.code }
        val allCodesSet: Set<String> = entries.map { it.code }.toSet()

        fun fromCode(raw: String?): SessionMonthCode? =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) }

        fun shortLabelFor(code: String): String =
            fromCode(code)?.shortLabel ?: code

        fun fullLabelFor(code: String): String =
            fromCode(code)?.fullLabel ?: code
    }
}

enum class FeeHeadType(val key: String, val label: String, val shortLabel: String) {
    TUITION("TUITION", "Tuition Fee", "Tuition"),
    TRANSPORT("TRANSPORT", "Transport Fee", "Transport"),
    EXAM_UT1("EXAM_UT1", "UT-I Exam Fee", "UT-I"),
    EXAM_UT2("EXAM_UT2", "UT-II Exam Fee", "UT-II"),
    EXAM_HY("EXAM_HY", "Half-Yearly Exam Fee", "Half-Yearly"),
    EXAM_YEARLY("EXAM_YEARLY", "Yearly Exam Fee", "Yearly");

    val isExamHead: Boolean
        get() = this == EXAM_UT1 || this == EXAM_UT2 || this == EXAM_HY || this == EXAM_YEARLY

    companion object {
        val examHeads: List<FeeHeadType> = listOf(EXAM_UT1, EXAM_UT2, EXAM_HY, EXAM_YEARLY)

        fun fromKey(raw: String?): FeeHeadType? =
            entries.firstOrNull { it.key.equals(raw?.trim(), ignoreCase = true) }
    }
}

data class ExamFeeHeadConfig(
    val examHeadKey: String, // EXAM_UT1, EXAM_UT2, EXAM_HY, EXAM_YEARLY
    val monthCode: String,   // Month in which this exam fee falls (e.g. JUL, OCT, DEC, FEB)
    val amountByClass: Map<String, Int> = emptyMap()
) {
    fun amountForClass(className: String): Int {
        val direct = amountByClass[className]
        if (direct != null) return direct.coerceAtLeast(0)
        val matchedKey = amountByClass.keys.firstOrNull { it.equals(className, ignoreCase = true) }
        return (matchedKey?.let { amountByClass[it] } ?: 0).coerceAtLeast(0)
    }

    fun toMap(): Map<String, Any> = mapOf(
        "examHeadKey" to examHeadKey,
        "monthCode" to monthCode,
        "amountByClass" to amountByClass.mapValues { it.value.coerceAtLeast(0).toLong() }
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): ExamFeeHeadConfig? {
            if (raw == null) return null
            val key = (raw["examHeadKey"] as? String)?.trim().orEmpty()
            if (FeeHeadType.fromKey(key) == null) return null
            val month = (raw["monthCode"] as? String)?.trim()?.uppercase(Locale.US).orEmpty().ifBlank { "JUL" }
            val rawAmounts = raw["amountByClass"] as? Map<*, *>
            val parsedAmounts = rawAmounts?.entries?.mapNotNull { (k, v) ->
                val cls = (k as? String)?.trim().orEmpty()
                val amt = (v as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
                if (cls.isNotEmpty()) cls to amt else null
            }?.toMap().orEmpty()
            return ExamFeeHeadConfig(
                examHeadKey = key,
                monthCode = month,
                amountByClass = parsedAmounts
            )
        }
    }
}

data class OptionalFeeCatalogueItem(
    val id: String,
    val name: String,
    val price: Int,
    val classFilter: List<String> = emptyList(), // Empty = applies to all classes
    val hidden: Boolean = false,
    // Phase 11 unified Stock fields (optional defaults keep 100% Phase 9 backward compatibility)
    val unit: String = "pcs",
    val openingStock: Int = 0,
    val lowStockLevel: Int = 5
) {
    fun appliesToClass(className: String): Boolean {
        if (classFilter.isEmpty()) return true
        return classFilter.any { it.equals(className, ignoreCase = true) }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name.trim().take(80),
        "price" to price.coerceAtLeast(0).toLong(),
        "classFilter" to classFilter.map { it.trim().take(40) }.filter { it.isNotEmpty() },
        "hidden" to hidden,
        "unit" to unit.trim().ifBlank { "pcs" }.take(24),
        "openingStock" to openingStock.coerceAtLeast(0).toLong(),
        "lowStockLevel" to lowStockLevel.coerceAtLeast(0).toLong()
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): OptionalFeeCatalogueItem? {
            if (raw == null) return null
            val id = (raw["id"] as? String)?.trim().orEmpty()
            val name = (raw["name"] as? String)?.trim().orEmpty()
            if (id.isEmpty() || name.isEmpty()) return null
            val price = (raw["price"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            val classes = (raw["classFilter"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val hidden = (raw["hidden"] as? Boolean) ?: false
            val unit = (raw["unit"] as? String)?.trim().orEmpty().ifBlank { "pcs" }
            val openingStock = (raw["openingStock"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            val lowStockLevel = (raw["lowStockLevel"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 5
            return OptionalFeeCatalogueItem(
                id = id,
                name = name,
                price = price,
                classFilter = classes,
                hidden = hidden,
                unit = unit,
                openingStock = openingStock,
                lowStockLevel = lowStockLevel
            )
        }
    }
}

data class FeeMessageTemplates(
    val feeDueTemplate: String = DEFAULT_FEE_DUE_TEMPLATE,
    val feeReceiptTemplate: String = DEFAULT_FEE_RECEIPT_TEMPLATE,
    val generalTemplate: String = DEFAULT_GENERAL_TEMPLATE
) {
    fun toMap(): Map<String, Any> = mapOf(
        "feeDueTemplate" to feeDueTemplate.trim().ifBlank { DEFAULT_FEE_DUE_TEMPLATE }.take(500),
        "feeReceiptTemplate" to feeReceiptTemplate.trim().ifBlank { DEFAULT_FEE_RECEIPT_TEMPLATE }.take(500),
        "generalTemplate" to generalTemplate.trim().ifBlank { DEFAULT_GENERAL_TEMPLATE }.take(500)
    )

    companion object {
        const val DEFAULT_FEE_DUE_TEMPLATE =
            "Dear {guardian}, fee of Rs. {amount} for {student} (Class {class}) for {months} is due at {school}. Kindly pay at the earliest."
        const val DEFAULT_FEE_RECEIPT_TEMPLATE =
            "Dear {guardian}, received Rs. {amount} for {student} (Class {class}) towards {months} at {school}. Thank you."
        const val DEFAULT_GENERAL_TEMPLATE =
            "Dear {guardian}, important update for {student} (Class {class}) from {school}."

        fun fromMap(raw: Map<*, *>?): FeeMessageTemplates {
            if (raw == null) return FeeMessageTemplates()
            return FeeMessageTemplates(
                feeDueTemplate = (raw["feeDueTemplate"] as? String)?.trim().orEmpty().ifBlank { DEFAULT_FEE_DUE_TEMPLATE },
                feeReceiptTemplate = (raw["feeReceiptTemplate"] as? String)?.trim().orEmpty().ifBlank { DEFAULT_FEE_RECEIPT_TEMPLATE },
                generalTemplate = (raw["generalTemplate"] as? String)?.trim().orEmpty().ifBlank { DEFAULT_GENERAL_TEMPLATE }
            )
        }
    }
}

/**
 * Fee Structure per school and session (`organisations/{orgId}/feeStructures/{schoolId}_{session}`).
 *
 * - [monthOrder]: ordered list of session months (default April to March).
 * - [chargedTuitionMonths]: months in which tuition is charged (allows skipping vacation month).
 * - [chargedTransportMonths]: months in which transport is charged (allows skipping vacation month).
 * - [tuitionByClassAndMonth]: className -> (monthCode -> amount in Rs). Also supports a class base rate via `"ALL"` key.
 * - [examFees]: list of 4 exam fee configs (`EXAM_UT1`, `EXAM_UT2`, `EXAM_HY`, `EXAM_YEARLY`).
 * - [optionalCatalogue]: optional items (Tie, Belt, Books, Diary, Dress, etc.) — never charged automatically.
 * - [frozenPastCellAmountsByClass]: when the fee structure is changed mid-session and the user chooses
 *   "Keep past unpaid dues unchanged", previous class amounts for those `"${className}|${monthCode}|${headKey}"`
 *   keys are preserved here so past unpaid dues keep their old amount while future months use the new structure.
 * - [maxConcessionPercentWithoutAdminApproval]: default 20%.
 */
data class FeeStructureConfig(
    val id: String = "",
    val schoolId: String,
    val session: String,
    val monthOrder: List<String> = SessionMonthCode.defaultAprilToMarchOrder,
    val chargedTuitionMonths: List<String> = SessionMonthCode.defaultAprilToMarchOrder,
    val chargedTransportMonths: List<String> = SessionMonthCode.defaultAprilToMarchOrder,
    val tuitionByClassAndMonth: Map<String, Map<String, Int>> = emptyMap(),
    val examFees: List<ExamFeeHeadConfig> = defaultExamFees(),
    val optionalCatalogue: List<OptionalFeeCatalogueItem> = defaultOptionalCatalogue(),
    val frozenPastCellAmountsByClass: Map<String, Int> = emptyMap(),
    val maxConcessionPercentWithoutAdminApproval: Int = 20,
    val messageTemplates: FeeMessageTemplates = FeeMessageTemplates(),
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun rawClassTuitionForMonth(className: String, monthCode: String): Int {
        val classMap = tuitionByClassAndMonth[className]
            ?: tuitionByClassAndMonth.entries.firstOrNull { it.key.equals(className, ignoreCase = true) }?.value
            ?: return 0
        val specific = classMap[monthCode.uppercase(Locale.US)]
        if (specific != null) return specific.coerceAtLeast(0)
        return (classMap["DEFAULT"] ?: classMap.values.firstOrNull() ?: 0).coerceAtLeast(0)
    }

    fun frozenKey(className: String, monthCode: String, headType: FeeHeadType): String =
        "${className.trim().lowercase(Locale.US)}|${monthCode.trim().uppercase(Locale.US)}|${headType.key}"

    fun scheduledClassAmountForCell(className: String, monthCode: String, headType: FeeHeadType): Int {
        val mCode = monthCode.trim().uppercase(Locale.US)
        val fKey = frozenKey(className, mCode, headType)
        val frozen = frozenPastCellAmountsByClass[fKey]
        if (frozen != null) return frozen.coerceAtLeast(0)

        return when (headType) {
            FeeHeadType.TUITION -> {
                if (chargedTuitionMonths.none { it.equals(mCode, ignoreCase = true) }) 0
                else rawClassTuitionForMonth(className, mCode)
            }
            FeeHeadType.TRANSPORT -> 0 // Transport comes from student's own transportMonthlyAmount
            FeeHeadType.EXAM_UT1, FeeHeadType.EXAM_UT2, FeeHeadType.EXAM_HY, FeeHeadType.EXAM_YEARLY -> {
                val cfg = examFees.firstOrNull { it.examHeadKey.equals(headType.key, ignoreCase = true) }
                if (cfg != null && cfg.monthCode.equals(mCode, ignoreCase = true)) {
                    cfg.amountForClass(className)
                } else 0
            }
        }
    }

    fun toFirestoreMap(uid: String, editorName: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "monthOrder" to monthOrder.map { it.uppercase(Locale.US) }.filter { it in SessionMonthCode.allCodesSet }
            .ifEmpty { SessionMonthCode.defaultAprilToMarchOrder },
        "chargedTuitionMonths" to chargedTuitionMonths.map { it.uppercase(Locale.US) }.filter { it in SessionMonthCode.allCodesSet },
        "chargedTransportMonths" to chargedTransportMonths.map { it.uppercase(Locale.US) }.filter { it in SessionMonthCode.allCodesSet },
        "tuitionByClassAndMonth" to tuitionByClassAndMonth.mapValues { (_, mMap) ->
            mMap.mapValues { (_, amt) -> amt.coerceAtLeast(0).toLong() }
        },
        "examFees" to examFees.map { it.toMap() },
        "optionalCatalogue" to optionalCatalogue.take(150).map { it.toMap() },
        "frozenPastCellAmountsByClass" to frozenPastCellAmountsByClass.mapValues { it.value.coerceAtLeast(0).toLong() },
        "maxConcessionPercentWithoutAdminApproval" to maxConcessionPercentWithoutAdminApproval.coerceIn(0, 100).toLong(),
        "messageTemplates" to messageTemplates.toMap(),
        "updatedByUid" to uid,
        "updatedByName" to editorName.take(120),
        "clientEditedAtMs" to nowMs
    )

    companion object {
        fun makeDocId(schoolId: String, session: String): String =
            "fstr_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(session)}"

        fun defaultExamFees(classes: List<String> = emptyList()): List<ExamFeeHeadConfig> {
            val zeroMap = classes.associateWith { 0 }
            return listOf(
                ExamFeeHeadConfig(FeeHeadType.EXAM_UT1.key, "JUL", zeroMap),
                ExamFeeHeadConfig(FeeHeadType.EXAM_HY.key, "OCT", zeroMap),
                ExamFeeHeadConfig(FeeHeadType.EXAM_UT2.key, "DEC", zeroMap),
                ExamFeeHeadConfig(FeeHeadType.EXAM_YEARLY.key, "FEB", zeroMap)
            )
        }

        fun defaultOptionalCatalogue(): List<OptionalFeeCatalogueItem> = listOf(
            OptionalFeeCatalogueItem(id = "cat_tie", name = "Tie", price = 60),
            OptionalFeeCatalogueItem(id = "cat_belt", name = "Belt", price = 80),
            OptionalFeeCatalogueItem(id = "cat_diary", name = "School Diary", price = 100),
            OptionalFeeCatalogueItem(id = "cat_idcard", name = "ID Card", price = 50),
            OptionalFeeCatalogueItem(id = "cat_books", name = "Book Set", price = 1200),
            OptionalFeeCatalogueItem(id = "cat_dress", name = "Uniform / Dress", price = 850)
        )

        fun buildDefault(schoolId: String, session: String, classNames: List<String> = emptyList()): FeeStructureConfig {
            val tuitionMap = classNames.associateWith {
                SessionMonthCode.defaultAprilToMarchOrder.associateWith { 500 }
            }
            return FeeStructureConfig(
                id = makeDocId(schoolId, session),
                schoolId = schoolId,
                session = session,
                tuitionByClassAndMonth = tuitionMap,
                examFees = defaultExamFees(classNames),
                optionalCatalogue = defaultOptionalCatalogue()
            )
        }

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): FeeStructureConfig? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val session = (data["session"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || session.isEmpty()) return null

            val monthOrder = (data["monthOrder"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.uppercase(Locale.US) }
                ?.filter { it in SessionMonthCode.allCodesSet }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
                ?: SessionMonthCode.defaultAprilToMarchOrder

            val chargedTuition = (data["chargedTuitionMonths"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.uppercase(Locale.US) }
                ?.filter { it in SessionMonthCode.allCodesSet }
                ?.distinct()
                ?: monthOrder

            val chargedTransport = (data["chargedTransportMonths"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.uppercase(Locale.US) }
                ?.filter { it in SessionMonthCode.allCodesSet }
                ?.distinct()
                ?: monthOrder

            val rawTuition = data["tuitionByClassAndMonth"] as? Map<*, *>
            val parsedTuition = rawTuition?.entries?.mapNotNull { (clsKey, mVal) ->
                val cls = (clsKey as? String)?.trim().orEmpty()
                val mMap = (mVal as? Map<*, *>)?.entries?.mapNotNull { (mk, mv) ->
                    val mc = (mk as? String)?.trim()?.uppercase(Locale.US).orEmpty()
                    val amt = (mv as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
                    if (mc.isNotEmpty()) mc to amt else null
                }?.toMap().orEmpty()
                if (cls.isNotEmpty()) cls to mMap else null
            }?.toMap().orEmpty()

            val parsedExams = (data["examFees"] as? List<*>)
                ?.mapNotNull { ExamFeeHeadConfig.fromMap(it as? Map<*, *>) }
                ?.takeIf { it.isNotEmpty() }
                ?: defaultExamFees()

            val parsedCatalogue = (data["optionalCatalogue"] as? List<*>)
                ?.mapNotNull { OptionalFeeCatalogueItem.fromMap(it as? Map<*, *>) }
                ?: defaultOptionalCatalogue()

            val rawFrozen = data["frozenPastCellAmountsByClass"] as? Map<*, *>
            val parsedFrozen = rawFrozen?.entries?.mapNotNull { (k, v) ->
                val ks = (k as? String)?.trim().orEmpty()
                val amt = (v as? Number)?.toInt()?.coerceAtLeast(0)
                if (ks.isNotEmpty() && amt != null) ks to amt else null
            }?.toMap().orEmpty()

            val maxConc = ((data["maxConcessionPercentWithoutAdminApproval"] as? Number)?.toInt() ?: 20).coerceIn(0, 100)
            val templates = FeeMessageTemplates.fromMap(data["messageTemplates"] as? Map<*, *>)

            return FeeStructureConfig(
                id = docId,
                schoolId = schoolId,
                session = session,
                monthOrder = monthOrder,
                chargedTuitionMonths = chargedTuition,
                chargedTransportMonths = chargedTransport,
                tuitionByClassAndMonth = parsedTuition,
                examFees = parsedExams,
                optionalCatalogue = parsedCatalogue,
                frozenPastCellAmountsByClass = parsedFrozen,
                maxConcessionPercentWithoutAdminApproval = maxConc,
                messageTemplates = templates,
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

/**
 * Per-student fee amount overrides with mandatory remark (`organisations/{orgId}/studentFeeOverrides/{id}`).
 * - [cellOverrides]: key = `"${monthCode}|${headKey}"` (e.g. `"JUL|TUITION"` or `"ALL|TUITION"` or `"ALL|TRANSPORT"`) -> overridden amount in Rs (`>= 0`).
 * - [remark]: mandatory explanation (e.g. "Staff ward tuition rate", "Custom transport point").
 */
data class StudentFeeOverride(
    val id: String = "",
    val schoolId: String,
    val session: String,
    val studentId: String,
    val studentName: String = "",
    val cellOverrides: Map<String, Int> = emptyMap(),
    val remark: String = "",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun overrideFor(monthCode: String, headType: FeeHeadType): Int? {
        val m = monthCode.trim().uppercase(Locale.US)
        val specificKey = "$m|${headType.key}"
        val allMonthsKey = "ALL|${headType.key}"
        return cellOverrides[specificKey] ?: cellOverrides[allMonthsKey]
    }

    fun toFirestoreMap(uid: String, editorName: String, nowMs: Long): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "studentId" to studentId,
        "studentName" to studentName.trim().take(120),
        "cellOverrides" to cellOverrides.mapValues { it.value.coerceAtLeast(0).toLong() },
        "remark" to remark.trim().take(300),
        "updatedByUid" to uid,
        "updatedByName" to editorName.take(120),
        "clientEditedAtMs" to nowMs
    )

    companion object {
        fun makeDocId(schoolId: String, session: String, studentDocId: String): String =
            "fovr_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(session)}_${StableDocId.sanitizeSegment(studentDocId)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): StudentFeeOverride? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val session = (data["session"] as? String)?.trim().orEmpty()
            val studentId = (data["studentId"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || session.isEmpty() || studentId.isEmpty()) return null
            val rawCells = data["cellOverrides"] as? Map<*, *>
            val parsedCells = rawCells?.entries?.mapNotNull { (k, v) ->
                val ks = (k as? String)?.trim()?.uppercase(Locale.US).orEmpty()
                val amt = (v as? Number)?.toInt()?.coerceAtLeast(0)
                if (ks.isNotEmpty() && amt != null) ks to amt else null
            }?.toMap().orEmpty()
            return StudentFeeOverride(
                id = docId,
                schoolId = schoolId,
                session = session,
                studentId = studentId,
                studentName = (data["studentName"] as? String).orEmpty(),
                cellOverrides = parsedCells,
                remark = (data["remark"] as? String).orEmpty(),
                updatedByUid = (data["updatedByUid"] as? String).orEmpty(),
                updatedByName = (data["updatedByName"] as? String).orEmpty(),
                clientEditedAtMs = (data["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

// ============================================================================
// PART C: COLLECT FEES, GRID CELLS, RECEIPTS & CANCELLATION MODELS
// ============================================================================

enum class FeeCellState(val label: String) {
    PAID("Paid"),
    PARTLY_PAID("Partly Paid"),
    DUE("Due"),
    NOT_APPLICABLE("N/A")
}

data class StudentFeeGridCell(
    val monthCode: String,
    val headType: FeeHeadType,
    val label: String,
    val scheduledAmount: Int,
    val paidAmount: Int,
    val concessionAllocated: Int,
    val remainingDue: Int,
    val state: FeeCellState,
    val isOverriddenForStudent: Boolean = false,
    val overrideRemark: String = ""
) {
    val cellKey: String get() = "${monthCode.uppercase(Locale.US)}|${headType.key}"
    val isTickable: Boolean get() = (state == FeeCellState.DUE || state == FeeCellState.PARTLY_PAID) && remainingDue > 0
}

data class StudentFeeMonthRow(
    val monthCode: String,
    val monthLabel: String,
    val tuitionCell: StudentFeeGridCell,
    val transportCell: StudentFeeGridCell?, // null when student does not use transport
    val examCells: List<StudentFeeGridCell>
) {
    fun allApplicableCells(): List<StudentFeeGridCell> = buildList {
        if (tuitionCell.state != FeeCellState.NOT_APPLICABLE) add(tuitionCell)
        if (transportCell != null && transportCell.state != FeeCellState.NOT_APPLICABLE) add(transportCell)
        addAll(examCells.filter { it.state != FeeCellState.NOT_APPLICABLE })
    }
}

enum class FeeLineItemType(val wireValue: String) {
    GRID_CELL("GRID_CELL"),
    OPTIONAL_ITEM("OPTIONAL_ITEM"),
    CUSTOM_CHARGE("CUSTOM_CHARGE");

    companion object {
        fun fromRaw(raw: String?): FeeLineItemType =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: GRID_CELL
    }
}

data class FeeReceiptLineItem(
    val lineId: String,
    val lineType: FeeLineItemType,
    val monthCode: String,
    val headKey: String, // e.g. TUITION, TRANSPORT, EXAM_UT1, OPTIONAL_cat_tie, CUSTOM
    val title: String,
    val quantity: Int = 1,
    val unitPrice: Int,
    val lineGrossAmount: Int, // quantity * unitPrice (or due amount being settled on this cell)
    val concessionAllocated: Int = 0,
    val netPayableOnLine: Int = (lineGrossAmount - concessionAllocated).coerceAtLeast(0),
    val paidAllocated: Int = netPayableOnLine,
    val balanceDueAfterLine: Int = (netPayableOnLine - paidAllocated).coerceAtLeast(0),
    val catalogueItemId: String = "",
    val saveToCatalogue: Boolean = false
) {
    val gridCellKey: String?
        get() = if (lineType == FeeLineItemType.GRID_CELL) "${monthCode.uppercase(Locale.US)}|$headKey" else null

    fun toMap(): Map<String, Any> = mapOf(
        "lineId" to lineId,
        "lineType" to lineType.wireValue,
        "monthCode" to monthCode.uppercase(Locale.US),
        "headKey" to headKey,
        "title" to title.trim().take(120),
        "quantity" to quantity.coerceAtLeast(1).toLong(),
        "unitPrice" to unitPrice.coerceAtLeast(0).toLong(),
        "lineGrossAmount" to lineGrossAmount.coerceAtLeast(0).toLong(),
        "concessionAllocated" to concessionAllocated.coerceAtLeast(0).toLong(),
        "netPayableOnLine" to netPayableOnLine.coerceAtLeast(0).toLong(),
        "paidAllocated" to paidAllocated.coerceAtLeast(0).toLong(),
        "balanceDueAfterLine" to balanceDueAfterLine.coerceAtLeast(0).toLong(),
        "catalogueItemId" to catalogueItemId.take(64),
        "saveToCatalogue" to saveToCatalogue
    )

    companion object {
        fun fromMap(raw: Map<*, *>?): FeeReceiptLineItem? {
            if (raw == null) return null
            val lineId = (raw["lineId"] as? String)?.trim().orEmpty()
            val title = (raw["title"] as? String)?.trim().orEmpty()
            if (lineId.isEmpty() || title.isEmpty()) return null
            val lineType = FeeLineItemType.fromRaw(raw["lineType"] as? String)
            val monthCode = (raw["monthCode"] as? String)?.trim()?.uppercase(Locale.US).orEmpty().ifBlank { "APR" }
            val headKey = (raw["headKey"] as? String)?.trim().orEmpty().ifBlank { "TUITION" }
            val qty = ((raw["quantity"] as? Number)?.toInt() ?: 1).coerceAtLeast(1)
            val unitPrice = ((raw["unitPrice"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
            val gross = ((raw["lineGrossAmount"] as? Number)?.toInt() ?: (qty * unitPrice)).coerceAtLeast(0)
            val conc = ((raw["concessionAllocated"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
            val net = ((raw["netPayableOnLine"] as? Number)?.toInt() ?: (gross - conc).coerceAtLeast(0)).coerceAtLeast(0)
            val paid = ((raw["paidAllocated"] as? Number)?.toInt() ?: net).coerceAtLeast(0)
            val bal = ((raw["balanceDueAfterLine"] as? Number)?.toInt() ?: (net - paid).coerceAtLeast(0)).coerceAtLeast(0)
            return FeeReceiptLineItem(
                lineId = lineId,
                lineType = lineType,
                monthCode = monthCode,
                headKey = headKey,
                title = title,
                quantity = qty,
                unitPrice = unitPrice,
                lineGrossAmount = gross,
                concessionAllocated = conc,
                netPayableOnLine = net,
                paidAllocated = paid,
                balanceDueAfterLine = bal,
                catalogueItemId = (raw["catalogueItemId"] as? String).orEmpty(),
                saveToCatalogue = (raw["saveToCatalogue"] as? Boolean) ?: false
            )
        }
    }
}

enum class ConcessionMode(val wireValue: String, val label: String) {
    NONE("NONE", "No Concession"),
    RUPEES("RUPEES", "Amount (₹)"),
    PERCENTAGE("PERCENTAGE", "Percent (%)");

    companion object {
        fun fromRaw(raw: String?): ConcessionMode =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class ConcessionScope(val wireValue: String, val label: String) {
    WHOLE_RECEIPT("WHOLE_RECEIPT", "On Whole Receipt"),
    SINGLE_LINE("SINGLE_LINE", "On Selected Line");

    companion object {
        fun fromRaw(raw: String?): ConcessionScope =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: WHOLE_RECEIPT
    }
}

enum class PaymentMode(val wireValue: String, val label: String) {
    CASH("CASH", "Cash"),
    UPI("UPI", "UPI"),
    CHEQUE("CHEQUE", "Cheque"),
    OTHER("OTHER", "Other");

    companion object {
        fun fromRaw(raw: String?): PaymentMode =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: CASH
    }
}

enum class FeeReceiptStatus(val wireValue: String, val label: String) {
    ISSUED("ISSUED", "Issued"),
    CANCELLED("CANCELLED", "Cancelled");

    companion object {
        fun fromRaw(raw: String?): FeeReceiptStatus =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: ISSUED
    }
}

data class FeeReceiptRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val receiptNumber: String,
    val deviceNodeCode: String,
    val sequenceNo: Int,
    val receiptDateIso: String, // YYYY-MM-DD
    val studentId: String,
    val studentName: String,
    val studentAdmNo: String,
    val fatherName: String,
    val className: String,
    val section: String,
    val rollNo: Int,
    val lineItems: List<FeeReceiptLineItem>,
    val totalBeforeConcession: Int,
    val concessionMode: ConcessionMode,
    val concessionScope: ConcessionScope,
    val concessionTargetLineId: String = "",
    val concessionInputValue: Double = 0.0,
    val concessionAmount: Int = 0,
    val concessionRemark: String = "",
    val concessionGivenByUid: String = "",
    val concessionGivenByName: String = "",
    val concessionApprovedByAdminUid: String = "",
    val concessionApprovedByAdminName: String = "",
    val netPayableAmount: Int,
    val amountPaid: Int,
    val balanceDueOnReceipt: Int,
    val paymentMode: PaymentMode,
    val paymentNote: String = "",
    val status: FeeReceiptStatus = FeeReceiptStatus.ISSUED,
    val receivedByUid: String,
    val receivedByName: String,
    val issuedAtMs: Long,
    val cancelledAtMs: Long = 0L,
    val cancelledByUid: String = "",
    val cancelledByName: String = "",
    val cancelReason: String = "",
    val hasPendingWrites: Boolean = false
) {
    val isCancelled: Boolean get() = status == FeeReceiptStatus.CANCELLED

    fun paidMonthsSummary(): String {
        val months = lineItems.map { SessionMonthCode.shortLabelFor(it.monthCode) }.distinct()
        return months.joinToString(", ").ifBlank { "-" }
    }

    fun toCreateFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "receiptNumber" to receiptNumber.trim().take(60),
        "deviceNodeCode" to deviceNodeCode.trim().take(12),
        "sequenceNo" to sequenceNo.coerceAtLeast(1).toLong(),
        "receiptDateIso" to receiptDateIso.trim().take(10),
        "studentId" to studentId,
        "studentName" to studentName.trim().take(120),
        "studentAdmNo" to studentAdmNo.trim().take(60),
        "fatherName" to fatherName.trim().take(120),
        "className" to className.trim().take(40),
        "section" to section.trim().uppercase(Locale.US).ifBlank { "A" }.take(10),
        "rollNo" to rollNo.coerceIn(1, 9999).toLong(),
        "lineItems" to lineItems.map { it.toMap() },
        "totalBeforeConcession" to totalBeforeConcession.coerceAtLeast(0).toLong(),
        "concessionMode" to concessionMode.wireValue,
        "concessionScope" to concessionScope.wireValue,
        "concessionTargetLineId" to concessionTargetLineId.take(64),
        "concessionInputValue" to concessionInputValue.coerceAtLeast(0.0),
        "concessionAmount" to concessionAmount.coerceAtLeast(0).toLong(),
        "concessionRemark" to concessionRemark.trim().take(300),
        "concessionGivenByUid" to concessionGivenByUid.take(128),
        "concessionGivenByName" to concessionGivenByName.trim().take(120),
        "concessionApprovedByAdminUid" to concessionApprovedByAdminUid.take(128),
        "concessionApprovedByAdminName" to concessionApprovedByAdminName.trim().take(120),
        "netPayableAmount" to netPayableAmount.coerceAtLeast(0).toLong(),
        "amountPaid" to amountPaid.coerceAtLeast(0).toLong(),
        "balanceDueOnReceipt" to balanceDueOnReceipt.coerceAtLeast(0).toLong(),
        "paymentMode" to paymentMode.wireValue,
        "paymentNote" to paymentNote.trim().take(240),
        "status" to FeeReceiptStatus.ISSUED.wireValue,
        "receivedByUid" to receivedByUid,
        "receivedByName" to receivedByName.trim().take(120),
        "issuedAtMs" to issuedAtMs.coerceAtLeast(0L),
        "cancelledAtMs" to 0L,
        "cancelledByUid" to "",
        "cancelledByName" to "",
        "cancelReason" to ""
    )

    companion object {
        fun makeDocId(schoolId: String, session: String, receiptNumber: String): String =
            "frcp_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(session)}_${StableDocId.sanitizeSegment(receiptNumber)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): FeeReceiptRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val session = (data["session"] as? String)?.trim().orEmpty()
            val receiptNumber = (data["receiptNumber"] as? String)?.trim().orEmpty()
            val studentId = (data["studentId"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || session.isEmpty() || receiptNumber.isEmpty() || studentId.isEmpty()) return null

            val lines = (data["lineItems"] as? List<*>)
                ?.mapNotNull { FeeReceiptLineItem.fromMap(it as? Map<*, *>) }
                .orEmpty()

            return FeeReceiptRecord(
                id = docId,
                schoolId = schoolId,
                session = session,
                receiptNumber = receiptNumber,
                deviceNodeCode = (data["deviceNodeCode"] as? String).orEmpty().ifBlank { "A1" },
                sequenceNo = ((data["sequenceNo"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
                receiptDateIso = (data["receiptDateIso"] as? String).orEmpty().ifBlank { "2026-04-01" },
                studentId = studentId,
                studentName = (data["studentName"] as? String).orEmpty(),
                studentAdmNo = (data["studentAdmNo"] as? String).orEmpty(),
                fatherName = (data["fatherName"] as? String).orEmpty(),
                className = (data["className"] as? String).orEmpty(),
                section = (data["section"] as? String).orEmpty().ifBlank { "A" },
                rollNo = ((data["rollNo"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
                lineItems = lines,
                totalBeforeConcession = ((data["totalBeforeConcession"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                concessionMode = ConcessionMode.fromRaw(data["concessionMode"] as? String),
                concessionScope = ConcessionScope.fromRaw(data["concessionScope"] as? String),
                concessionTargetLineId = (data["concessionTargetLineId"] as? String).orEmpty(),
                concessionInputValue = (data["concessionInputValue"] as? Number)?.toDouble() ?: 0.0,
                concessionAmount = ((data["concessionAmount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                concessionRemark = (data["concessionRemark"] as? String).orEmpty(),
                concessionGivenByUid = (data["concessionGivenByUid"] as? String).orEmpty(),
                concessionGivenByName = (data["concessionGivenByName"] as? String).orEmpty(),
                concessionApprovedByAdminUid = (data["concessionApprovedByAdminUid"] as? String).orEmpty(),
                concessionApprovedByAdminName = (data["concessionApprovedByAdminName"] as? String).orEmpty(),
                netPayableAmount = ((data["netPayableAmount"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                amountPaid = ((data["amountPaid"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                balanceDueOnReceipt = ((data["balanceDueOnReceipt"] as? Number)?.toInt() ?: 0).coerceAtLeast(0),
                paymentMode = PaymentMode.fromRaw(data["paymentMode"] as? String),
                paymentNote = (data["paymentNote"] as? String).orEmpty(),
                status = FeeReceiptStatus.fromRaw(data["status"] as? String),
                receivedByUid = (data["receivedByUid"] as? String).orEmpty(),
                receivedByName = (data["receivedByName"] as? String).orEmpty(),
                issuedAtMs = (data["issuedAtMs"] as? Number)?.toLong() ?: 0L,
                cancelledAtMs = (data["cancelledAtMs"] as? Number)?.toLong() ?: 0L,
                cancelledByUid = (data["cancelledByUid"] as? String).orEmpty(),
                cancelledByName = (data["cancelledByName"] as? String).orEmpty(),
                cancelReason = (data["cancelReason"] as? String).orEmpty(),
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class StudentFeeAccountSummary(
    val studentDocId: String,
    val student: StudentEntity,
    val usesTransport: Boolean,
    val monthRows: List<StudentFeeMonthRow>,
    val allCells: List<StudentFeeGridCell>,
    val totalScheduledFee: Int,
    val totalPaidOnGrid: Int,
    val totalConcessionOnGrid: Int,
    val totalRemainingDue: Int,
    val dueMonthsList: List<String>,
    val receipts: List<FeeReceiptRecord>,
    val activeOverride: StudentFeeOverride?
)

data class SelectedOptionalSaleDraft(
    val catalogueItem: OptionalFeeCatalogueItem,
    val monthCode: String,
    val quantity: Int = 1,
    val unitPrice: Int = catalogueItem.price
) {
    val totalAmount: Int get() = (quantity.coerceAtLeast(1) * unitPrice.coerceAtLeast(0)).coerceAtLeast(0)
}

data class CustomFeeChargeDraft(
    val id: String,
    val name: String,
    val amount: Int,
    val monthCode: String,
    val addToCatalogue: Boolean = false
)

data class FeePaymentDraftComputation(
    val draftLines: List<FeeReceiptLineItem>,
    val totalBeforeConcession: Int,
    val concessionAmount: Int,
    val effectiveConcessionPercent: Double,
    val requiresAdminApprovalForConcession: Boolean,
    val netPayableAmount: Int,
    val amountPaid: Int,
    val balanceDueAfterPayment: Int,
    val allocatedLines: List<FeeReceiptLineItem>,
    val validationError: String? = null
)

// ============================================================================
// PART D & PART D2: REPORTS, SEND QUEUE, PHONE NORMALISATION & NOTICES MODELS
// ============================================================================

data class DayCollectionRegisterSummary(
    val startDateIso: String,
    val endDateIso: String,
    val activeReceipts: List<FeeReceiptRecord>,
    val cancelledReceipts: List<FeeReceiptRecord>,
    val totalByMode: Map<PaymentMode, Int>,
    val totalGrossBeforeConcession: Int,
    val totalConcessionGiven: Int,
    val totalNetCollected: Int
)

data class StudentDueCellBreakdown(
    val monthCode: String,
    val headType: FeeHeadType,
    val label: String,
    val dueAmount: Int
)

data class StudentDuesListRow(
    val schoolDocId: String,
    val schoolName: String,
    val studentDocId: String,
    val student: StudentEntity,
    val dueBreakdowns: List<StudentDueCellBreakdown>,
    val dueByHead: Map<FeeHeadType, Int>,
    val dueMonths: List<String>,
    val totalDueAmount: Int
)

data class ConcessionReportRow(
    val receiptId: String,
    val receiptNumber: String,
    val receiptDateIso: String,
    val studentName: String,
    val studentAdmNo: String,
    val className: String,
    val section: String,
    val totalBeforeConcession: Int,
    val concessionAmount: Int,
    val concessionMode: ConcessionMode,
    val concessionScope: ConcessionScope,
    val remark: String,
    val givenByName: String,
    val approvedByAdminName: String
)

data class OptionalItemSalesRow(
    val itemKey: String,
    val itemName: String,
    val totalQuantitySold: Int,
    val totalGrossAmount: Int,
    val totalNetCollected: Int,
    val receiptCount: Int
)

sealed interface PhoneValidationResult {
    data class Valid(
        val e164WithPlus: String, // e.g. "+919839100001"
        val waMeDigits: String,   // e.g. "919839100001"
        val tenDigitNational: String // e.g. "9839100001"
    ) : PhoneValidationResult

    data class Invalid(val reason: String) : PhoneValidationResult
}

enum class FeeMessageType(val wireValue: String, val label: String) {
    FEE_DUE_REMINDER("FEE_DUE_REMINDER", "Fee Due Reminder"),
    FEE_RECEIPT("FEE_RECEIPT", "Fee Receipt Text"),
    GENERAL("GENERAL", "General Message");

    companion object {
        fun fromRaw(raw: String?): FeeMessageType =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: FEE_DUE_REMINDER
    }
}

data class FeeMessageSentLog(
    val id: String,
    val schoolId: String,
    val session: String,
    val studentId: String,
    val studentName: String,
    val className: String,
    val section: String,
    val guardianName: String,
    val normalizedMobile: String,
    val messageType: FeeMessageType,
    val contextKey: String, // e.g. "DUE_2026-11" or "RCP_RCP-2627-A1-0001" or "GEN_2026-11-23"
    val messageText: String,
    val channel: String, // "WHATSAPP" or "SMS"
    val sentByUid: String,
    val sentByName: String,
    val sentAtMs: Long,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "studentId" to studentId,
        "studentName" to studentName.trim().take(120),
        "className" to className.trim().take(40),
        "section" to section.trim().uppercase(Locale.US).ifBlank { "A" }.take(10),
        "guardianName" to guardianName.trim().take(120),
        "normalizedMobile" to normalizedMobile.trim().take(24),
        "messageType" to messageType.wireValue,
        "contextKey" to contextKey.trim().take(80),
        "messageText" to messageText.trim().take(600),
        "channel" to channel.trim().take(20),
        "sentByUid" to sentByUid,
        "sentByName" to sentByName.trim().take(120),
        "sentAtMs" to sentAtMs.coerceAtLeast(0L)
    )

    companion object {
        fun makeDeterministicId(
            schoolId: String,
            session: String,
            studentDocId: String,
            messageType: FeeMessageType,
            contextKey: String
        ): String =
            "fmsg_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(session)}_${StableDocId.sanitizeSegment(studentDocId)}_${messageType.wireValue}_${StableDocId.sanitizeSegment(contextKey)}"

        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): FeeMessageSentLog? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val session = (data["session"] as? String)?.trim().orEmpty()
            val studentId = (data["studentId"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || session.isEmpty() || studentId.isEmpty()) return null
            return FeeMessageSentLog(
                id = docId,
                schoolId = schoolId,
                session = session,
                studentId = studentId,
                studentName = (data["studentName"] as? String).orEmpty(),
                className = (data["className"] as? String).orEmpty(),
                section = (data["section"] as? String).orEmpty().ifBlank { "A" },
                guardianName = (data["guardianName"] as? String).orEmpty(),
                normalizedMobile = (data["normalizedMobile"] as? String).orEmpty(),
                messageType = FeeMessageType.fromRaw(data["messageType"] as? String),
                contextKey = (data["contextKey"] as? String).orEmpty(),
                messageText = (data["messageText"] as? String).orEmpty(),
                channel = (data["channel"] as? String).orEmpty().ifBlank { "WHATSAPP" },
                sentByUid = (data["sentByUid"] as? String).orEmpty(),
                sentByName = (data["sentByName"] as? String).orEmpty(),
                sentAtMs = (data["sentAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class SendQueueGuardianRow(
    val schoolDocId: String,
    val schoolName: String,
    val studentDocId: String,
    val student: StudentEntity,
    val guardianName: String,
    val rawMobile: String,
    val phoneValidation: PhoneValidationResult,
    val dueAmount: Int,
    val dueMonthsSummary: String,
    val messageType: FeeMessageType,
    val contextKey: String,
    val messageText: String,
    val waMeUrl: String?,
    val sentLog: FeeMessageSentLog?
) {
    val isSent: Boolean get() = sentLog != null
}

data class SchoolNoticeRecord(
    val id: String,
    val schoolId: String,
    val title: String,
    val bodyText: String,
    val noticeDateIso: String,
    val createdByUid: String,
    val createdByName: String,
    val createdAtMs: Long,
    val hasPendingWrites: Boolean = false
) {
    fun formattedShareText(schoolName: String): String = buildString {
        if (schoolName.isNotBlank()) {
            appendLine("*$schoolName*")
        }
        appendLine("*Notice: ${title.trim()}*")
        if (noticeDateIso.isNotBlank()) {
            appendLine("Date: $noticeDateIso")
        }
        appendLine()
        append(bodyText.trim())
    }

    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "title" to title.trim().take(140),
        "bodyText" to bodyText.trim().take(2000),
        "noticeDateIso" to noticeDateIso.trim().take(20),
        "createdByUid" to createdByUid,
        "createdByName" to createdByName.trim().take(120),
        "createdAtMs" to createdAtMs.coerceAtLeast(0L)
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): SchoolNoticeRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val title = (data["title"] as? String)?.trim().orEmpty()
            val bodyText = (data["bodyText"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || title.isEmpty()) return null
            return SchoolNoticeRecord(
                id = docId,
                schoolId = schoolId,
                title = title,
                bodyText = bodyText,
                noticeDateIso = (data["noticeDateIso"] as? String).orEmpty(),
                createdByUid = (data["createdByUid"] as? String).orEmpty(),
                createdByName = (data["createdByName"] as? String).orEmpty(),
                createdAtMs = (data["createdAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class FeeAuditLogRecord(
    val id: String,
    val schoolId: String,
    val session: String,
    val action: String, // e.g. RECEIPT_ISSUED, RECEIPT_CANCELLED, STRUCTURE_UPDATED, STUDENT_OVERRIDE_SAVED
    val targetId: String,
    val studentId: String = "",
    val studentName: String = "",
    val summary: String,
    val actorUid: String,
    val actorName: String,
    val clientCreatedAtMs: Long,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "action" to action.trim().take(60),
        "targetId" to targetId.trim().take(128),
        "studentId" to studentId.trim().take(128),
        "studentName" to studentName.trim().take(120),
        "summary" to summary.trim().take(400),
        "actorUid" to actorUid,
        "actorName" to actorName.trim().take(120),
        "clientCreatedAtMs" to clientCreatedAtMs.coerceAtLeast(0L)
    )

    companion object {
        fun fromMap(docId: String, data: Map<String, Any>?, hasPendingWrites: Boolean = false): FeeAuditLogRecord? {
            if (data == null) return null
            val schoolId = (data["schoolId"] as? String)?.trim().orEmpty()
            val action = (data["action"] as? String)?.trim().orEmpty()
            if (schoolId.isEmpty() || action.isEmpty()) return null
            return FeeAuditLogRecord(
                id = docId,
                schoolId = schoolId,
                session = (data["session"] as? String).orEmpty(),
                action = action,
                targetId = (data["targetId"] as? String).orEmpty(),
                studentId = (data["studentId"] as? String).orEmpty(),
                studentName = (data["studentName"] as? String).orEmpty(),
                summary = (data["summary"] as? String).orEmpty(),
                actorUid = (data["actorUid"] as? String).orEmpty(),
                actorName = (data["actorName"] as? String).orEmpty(),
                clientCreatedAtMs = (data["clientCreatedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

// ============================================================================
// PART E: ACCESS EVALUATOR FOR STUDENT REGISTER & FEES
// ============================================================================

object Phase9AccessEvaluator {
    fun canViewFees(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canViewModule(member, AppModule.FEES)
    }

    fun canEditFees(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canEditModule(member, AppModule.FEES)
    }

    fun canViewStudentRegister(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canViewModule(member, AppModule.STUDENT_REGISTER)
    }

    fun canEditStudentRegister(member: MemberAccessProfile?, schoolDocId: String? = null): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (!schoolDocId.isNullOrBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        return OrgAccessEvaluator.canEditModule(member, AppModule.STUDENT_REGISTER)
    }

    fun isConcessionWithinMemberLimit(
        member: MemberAccessProfile?,
        effectiveConcessionPercent: Double,
        maxPercentWithoutAdmin: Int,
        hasExplicitAdminApproval: Boolean = false
    ): Boolean {
        if (effectiveConcessionPercent <= 0.0) return true
        if (OrgAccessEvaluator.isAdmin(member)) return true
        if (hasExplicitAdminApproval) return true
        return effectiveConcessionPercent <= maxPercentWithoutAdmin.toDouble() + 0.0001
    }
}

// ============================================================================
// CORE FEE & STUDENT REGISTER ENGINES
// ============================================================================

object StudentRegisterEngine {

    fun filterStudents(
        students: List<StudentEntity>,
        searchQuery: String = "",
        classNameFilter: String? = null,
        sectionFilter: String? = null,
        statusFilter: StudentRegisterStatusFilter = StudentRegisterStatusFilter.ACTIVE_ONLY,
        transportFilter: StudentTransportFilter = StudentTransportFilter.ALL,
        siblingGroupIdFilter: String? = null
    ): List<StudentEntity> {
        val q = searchQuery.trim().lowercase(Locale.US)
        return students.filter { st ->
            val matchesClass = classNameFilter.isNullOrBlank() || st.className.equals(classNameFilter, ignoreCase = true)
            val matchesSection = sectionFilter.isNullOrBlank() || st.section.equals(sectionFilter, ignoreCase = true)
            val matchesStatus = when (statusFilter) {
                StudentRegisterStatusFilter.ACTIVE_ONLY -> st.isActiveStatus
                StudentRegisterStatusFilter.LEFT_ONLY -> !st.isActiveStatus
                StudentRegisterStatusFilter.ALL -> true
            }
            val matchesTransport = when (transportFilter) {
                StudentTransportFilter.ALL -> true
                StudentTransportFilter.TRANSPORT_USERS -> st.usesTransport
                StudentTransportFilter.NON_TRANSPORT -> !st.usesTransport
            }
            val matchesSibling = siblingGroupIdFilter.isNullOrBlank() ||
                (st.siblingGroupId.isNotBlank() && st.siblingGroupId.equals(siblingGroupIdFilter, ignoreCase = true))

            val matchesSearch = q.isEmpty() ||
                st.name.lowercase(Locale.US).contains(q) ||
                st.admNo.lowercase(Locale.US).contains(q) ||
                st.fatherName.lowercase(Locale.US).contains(q) ||
                st.motherName.lowercase(Locale.US).contains(q) ||
                st.mobile.lowercase(Locale.US).contains(q) ||
                st.secondMobile.lowercase(Locale.US).contains(q) ||
                st.transportRouteName.lowercase(Locale.US).contains(q) ||
                st.rollNo.toString() == q

            matchesClass && matchesSection && matchesStatus && matchesTransport && matchesSibling && matchesSearch
        }.sortedWith(
            compareBy<StudentEntity> { !it.isActiveStatus }
                .thenBy { it.className.lowercase(Locale.US) }
                .thenBy { it.section.uppercase(Locale.US) }
                .thenBy { it.rollNo }
        )
    }

    fun findSiblingsForStudent(student: StudentEntity, allSchoolStudents: List<StudentEntity>): List<StudentEntity> {
        if (student.siblingGroupId.isNotBlank()) {
            return allSchoolStudents.filter {
                it.id != student.id && it.siblingGroupId.equals(student.siblingGroupId, ignoreCase = true)
            }
        }
        return emptyList()
    }

    fun resolveOrGenerateSiblingGroupId(
        student: StudentEntity,
        targetSibling: StudentEntity,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        if (targetSibling.siblingGroupId.isNotBlank()) return targetSibling.siblingGroupId
        if (student.siblingGroupId.isNotBlank()) return student.siblingGroupId
        val cleanAdm = StableDocId.sanitizeSegment(targetSibling.admNo.ifBlank { targetSibling.name })
        return "FAM_${cleanAdm}_${(nowMs % 100000)}"
    }
}

object FeeCollectionEngine {

    /**
     * Computes the full fee grid and account summary for one student from:
     * - [structure]: school + session fee structure
     * - [student]: student record (class, usesTransport, transportMonthlyAmount)
     * - [studentDocId]: Firestore student document ID
     * - [override]: optional per-student fee override
     * - [receiptsForStudent]: all receipts for this student in this school + session.
     *   Only `ISSUED` receipts count toward paid/concession on grid cells; `CANCELLED` receipts
     *   remain in `receipts` history as cancelled while their cells immediately become due again!
     */
    fun computeStudentFeeAccount(
        structure: FeeStructureConfig,
        student: StudentEntity,
        studentDocId: String,
        override: StudentFeeOverride?,
        receiptsForStudent: List<FeeReceiptRecord>
    ): StudentFeeAccountSummary {
        val activeReceipts = receiptsForStudent.filter {
            it.schoolId == structure.schoolId &&
                it.session.equals(structure.session, ignoreCase = true) &&
                it.studentId == studentDocId &&
                it.status == FeeReceiptStatus.ISSUED
        }

        // Sum paidAllocated and concessionAllocated per grid cell key ("MONTH|HEAD")
        val paidByCellKey = mutableMapOf<String, Int>()
        val concByCellKey = mutableMapOf<String, Int>()
        for (rcp in activeReceipts) {
            for (line in rcp.lineItems) {
                val cellKey = line.gridCellKey ?: continue
                paidByCellKey[cellKey] = (paidByCellKey[cellKey] ?: 0) + line.paidAllocated
                concByCellKey[cellKey] = (concByCellKey[cellKey] ?: 0) + line.concessionAllocated
            }
        }

        val orderedMonths = structure.monthOrder.ifEmpty { SessionMonthCode.defaultAprilToMarchOrder }
        val usesTransport = student.usesTransport && (
            student.transportMonthlyAmount > 0 ||
                override?.cellOverrides?.keys?.any { it.endsWith("|${FeeHeadType.TRANSPORT.key}") } == true
            )

        val monthRows = orderedMonths.map { mCode ->
            val monthUpper = mCode.uppercase(Locale.US)
            val tuitionCell = buildGridCell(
                structure = structure,
                student = student,
                override = override,
                monthCode = monthUpper,
                headType = FeeHeadType.TUITION,
                paidByCellKey = paidByCellKey,
                concByCellKey = concByCellKey
            )
            val transportCell = if (usesTransport) {
                buildGridCell(
                    structure = structure,
                    student = student,
                    override = override,
                    monthCode = monthUpper,
                    headType = FeeHeadType.TRANSPORT,
                    paidByCellKey = paidByCellKey,
                    concByCellKey = concByCellKey
                )
            } else null

            val examCells = FeeHeadType.examHeads.mapNotNull { examHead ->
                val cell = buildGridCell(
                    structure = structure,
                    student = student,
                    override = override,
                    monthCode = monthUpper,
                    headType = examHead,
                    paidByCellKey = paidByCellKey,
                    concByCellKey = concByCellKey
                )
                if (cell.state != FeeCellState.NOT_APPLICABLE) cell else null
            }

            StudentFeeMonthRow(
                monthCode = monthUpper,
                monthLabel = SessionMonthCode.shortLabelFor(monthUpper),
                tuitionCell = tuitionCell,
                transportCell = transportCell,
                examCells = examCells
            )
        }

        val allApplicableCells = monthRows.flatMap { it.allApplicableCells() }
        val totalScheduled = allApplicableCells.sumOf { it.scheduledAmount }
        val totalPaid = allApplicableCells.sumOf { it.paidAmount }
        val totalConcession = allApplicableCells.sumOf { it.concessionAllocated }
        val totalRemainingDue = allApplicableCells.sumOf { it.remainingDue }
        val dueMonths = monthRows
            .filter { row -> row.allApplicableCells().any { it.remainingDue > 0 } }
            .map { SessionMonthCode.shortLabelFor(it.monthCode) }

        val sortedReceipts = receiptsForStudent
            .filter {
                it.schoolId == structure.schoolId &&
                    it.session.equals(structure.session, ignoreCase = true) &&
                    it.studentId == studentDocId
            }
            .sortedWith(compareByDescending<FeeReceiptRecord> { it.issuedAtMs }.thenByDescending { it.sequenceNo })

        return StudentFeeAccountSummary(
            studentDocId = studentDocId,
            student = student,
            usesTransport = usesTransport,
            monthRows = monthRows,
            allCells = allApplicableCells,
            totalScheduledFee = totalScheduled,
            totalPaidOnGrid = totalPaid,
            totalConcessionOnGrid = totalConcession,
            totalRemainingDue = totalRemainingDue,
            dueMonthsList = dueMonths,
            receipts = sortedReceipts,
            activeOverride = override
        )
    }

    private fun buildGridCell(
        structure: FeeStructureConfig,
        student: StudentEntity,
        override: StudentFeeOverride?,
        monthCode: String,
        headType: FeeHeadType,
        paidByCellKey: Map<String, Int>,
        concByCellKey: Map<String, Int>
    ): StudentFeeGridCell {
        val cellKey = "$monthCode|${headType.key}"
        val paid = (paidByCellKey[cellKey] ?: 0).coerceAtLeast(0)
        val conc = (concByCellKey[cellKey] ?: 0).coerceAtLeast(0)
        val settledSoFar = paid + conc

        val isChargedMonth = when (headType) {
            FeeHeadType.TUITION -> structure.chargedTuitionMonths.any { it.equals(monthCode, ignoreCase = true) }
            FeeHeadType.TRANSPORT -> student.usesTransport &&
                structure.chargedTransportMonths.any { it.equals(monthCode, ignoreCase = true) }
            FeeHeadType.EXAM_UT1, FeeHeadType.EXAM_UT2, FeeHeadType.EXAM_HY, FeeHeadType.EXAM_YEARLY -> {
                val cfg = structure.examFees.firstOrNull { it.examHeadKey.equals(headType.key, ignoreCase = true) }
                cfg != null && cfg.monthCode.equals(monthCode, ignoreCase = true) && cfg.amountForClass(student.className) > 0
            }
        }

        val overriddenAmount = override?.overrideFor(monthCode, headType)
        val isOverridden = overriddenAmount != null && isChargedMonth

        val baseScheduled = when {
            !isChargedMonth -> 0
            overriddenAmount != null -> overriddenAmount.coerceAtLeast(0)
            headType == FeeHeadType.TRANSPORT -> student.transportMonthlyAmount.coerceAtLeast(0)
            else -> structure.scheduledClassAmountForCell(student.className, monthCode, headType)
        }

        // If a cell already had payment/concession recorded before a structure edit, scheduled is at least what was settled
        val effectiveScheduled = if (settledSoFar > 0 && baseScheduled == 0) settledSoFar else baseScheduled

        if (effectiveScheduled <= 0 && settledSoFar <= 0) {
            return StudentFeeGridCell(
                monthCode = monthCode,
                headType = headType,
                label = "${SessionMonthCode.shortLabelFor(monthCode)} ${headType.shortLabel}",
                scheduledAmount = 0,
                paidAmount = 0,
                concessionAllocated = 0,
                remainingDue = 0,
                state = FeeCellState.NOT_APPLICABLE
            )
        }

        val remaining = (effectiveScheduled - settledSoFar).coerceAtLeast(0)
        val state = when {
            remaining == 0 -> FeeCellState.PAID
            settledSoFar > 0 -> FeeCellState.PARTLY_PAID
            else -> FeeCellState.DUE
        }

        return StudentFeeGridCell(
            monthCode = monthCode,
            headType = headType,
            label = "${SessionMonthCode.shortLabelFor(monthCode)} ${headType.shortLabel}",
            scheduledAmount = effectiveScheduled,
            paidAmount = paid,
            concessionAllocated = conc,
            remainingDue = remaining,
            state = state,
            isOverriddenForStudent = isOverridden,
            overrideRemark = if (isOverridden) override?.remark.orEmpty() else ""
        )
    }

    /**
     * When a fee structure is edited mid-session and the user chooses
     * [keepPastUnpaidDuesUnchanged] == true for [monthsToPreserveOldRates],
     * snapshots the old structure's class rates for those months into `frozenPastCellAmountsByClass`
     * so unpaid dues in those months retain their previous amount.
     * When [keepPastUnpaidDuesUnchanged] == false, clears frozen snapshots for those months so
     * unpaid dues update to the new structure amounts.
     * Note: Already-issued receipts store their own immutable line items and are NEVER altered.
     */
    fun applyStructureChangeWithPastDueChoice(
        previousStructure: FeeStructureConfig,
        updatedStructure: FeeStructureConfig,
        classNames: List<String>,
        keepPastUnpaidDuesUnchanged: Boolean,
        monthsToPreserveOldRates: List<String> = previousStructure.monthOrder
    ): FeeStructureConfig {
        if (!keepPastUnpaidDuesUnchanged) {
            return updatedStructure.copy(frozenPastCellAmountsByClass = emptyMap())
        }
        val newFrozen = updatedStructure.frozenPastCellAmountsByClass.toMutableMap()
        val allClasses = (classNames + previousStructure.tuitionByClassAndMonth.keys + updatedStructure.tuitionByClassAndMonth.keys).distinct()
        val targetMonths = monthsToPreserveOldRates.map { it.uppercase(Locale.US) }.toSet()

        for (cls in allClasses) {
            for (mCode in targetMonths) {
                for (head in listOf(FeeHeadType.TUITION, FeeHeadType.EXAM_UT1, FeeHeadType.EXAM_UT2, FeeHeadType.EXAM_HY, FeeHeadType.EXAM_YEARLY)) {
                    val oldAmt = previousStructure.scheduledClassAmountForCell(cls, mCode, head)
                    val newAmt = updatedStructure.copy(frozenPastCellAmountsByClass = emptyMap())
                        .scheduledClassAmountForCell(cls, mCode, head)
                    if (oldAmt != newAmt) {
                        val key = updatedStructure.frozenKey(cls, mCode, head)
                        newFrozen[key] = oldAmt
                    }
                }
            }
        }
        return updatedStructure.copy(frozenPastCellAmountsByClass = newFrozen)
    }

    /**
     * Builds the draft payment lines, calculates concession (in Rupees or Percent, whole-receipt or single-line),
     * checks the admin-approval threshold, and allocates partial payment across lines so any unpaid balance
     * stays due on the remaining cells.
     */
    fun computePaymentDraft(
        selectedGridCells: List<StudentFeeGridCell>,
        selectedOptionalItems: List<SelectedOptionalSaleDraft>,
        customCharges: List<CustomFeeChargeDraft>,
        concessionMode: ConcessionMode,
        concessionScope: ConcessionScope,
        concessionTargetLineId: String = "",
        concessionInputValue: Double = 0.0,
        concessionRemark: String = "",
        amountPaidInput: Int? = null, // null = pay full netPayableAmount
        maxConcessionPercentWithoutAdmin: Int = 20,
        actorMember: MemberAccessProfile? = null,
        hasExplicitAdminApproval: Boolean = false
    ): FeePaymentDraftComputation {
        val rawLines = mutableListOf<FeeReceiptLineItem>()

        for (cell in selectedGridCells) {
            if (!cell.isTickable) continue
            val lineId = "CELL_${cell.monthCode}_${cell.headType.key}"
            rawLines += FeeReceiptLineItem(
                lineId = lineId,
                lineType = FeeLineItemType.GRID_CELL,
                monthCode = cell.monthCode,
                headKey = cell.headType.key,
                title = "${cell.headType.label} (${SessionMonthCode.shortLabelFor(cell.monthCode)})",
                quantity = 1,
                unitPrice = cell.remainingDue,
                lineGrossAmount = cell.remainingDue
            )
        }

        for (opt in selectedOptionalItems) {
            val qty = opt.quantity.coerceAtLeast(1)
            val price = opt.unitPrice.coerceAtLeast(0)
            val gross = qty * price
            if (gross <= 0) continue
            val lineId = "OPT_${opt.catalogueItem.id}_${opt.monthCode.uppercase(Locale.US)}"
            rawLines += FeeReceiptLineItem(
                lineId = lineId,
                lineType = FeeLineItemType.OPTIONAL_ITEM,
                monthCode = opt.monthCode.uppercase(Locale.US),
                headKey = "OPTIONAL_${opt.catalogueItem.id}",
                title = "${opt.catalogueItem.name} (${SessionMonthCode.shortLabelFor(opt.monthCode)})",
                quantity = qty,
                unitPrice = price,
                lineGrossAmount = gross,
                catalogueItemId = opt.catalogueItem.id
            )
        }

        for (cust in customCharges) {
            val cleanName = cust.name.trim()
            val amt = cust.amount.coerceAtLeast(0)
            if (cleanName.isEmpty() || amt <= 0) continue
            val lineId = "CUST_${cust.id}_${cust.monthCode.uppercase(Locale.US)}"
            rawLines += FeeReceiptLineItem(
                lineId = lineId,
                lineType = FeeLineItemType.CUSTOM_CHARGE,
                monthCode = cust.monthCode.uppercase(Locale.US),
                headKey = "CUSTOM_${StableDocId.sanitizeSegment(cleanName)}",
                title = "$cleanName (${SessionMonthCode.shortLabelFor(cust.monthCode)})",
                quantity = 1,
                unitPrice = amt,
                lineGrossAmount = amt,
                saveToCatalogue = cust.addToCatalogue
            )
        }

        val totalBeforeConcession = rawLines.sumOf { it.lineGrossAmount }
        if (rawLines.isEmpty() || totalBeforeConcession <= 0) {
            return FeePaymentDraftComputation(
                draftLines = emptyList(),
                totalBeforeConcession = 0,
                concessionAmount = 0,
                effectiveConcessionPercent = 0.0,
                requiresAdminApprovalForConcession = false,
                netPayableAmount = 0,
                amountPaid = 0,
                balanceDueAfterPayment = 0,
                allocatedLines = emptyList(),
                validationError = "Tick at least one due fee cell, optional item, or custom charge."
            )
        }

        val targetLine = if (concessionScope == ConcessionScope.SINGLE_LINE) {
            rawLines.firstOrNull { it.lineId == concessionTargetLineId } ?: rawLines.first()
        } else null

        val concessionBase = if (concessionScope == ConcessionScope.SINGLE_LINE && targetLine != null) {
            targetLine.lineGrossAmount
        } else {
            totalBeforeConcession
        }

        val safeInput = concessionInputValue.coerceAtLeast(0.0)
        val rawConcession = when (concessionMode) {
            ConcessionMode.NONE -> 0
            ConcessionMode.RUPEES -> safeInput.roundToInt().coerceIn(0, concessionBase)
            ConcessionMode.PERCENTAGE -> {
                val pct = safeInput.coerceIn(0.0, 100.0)
                floor((concessionBase.toDouble() * pct / 100.0) + 0.5).toInt().coerceIn(0, concessionBase)
            }
        }

        val effectiveConcessionPercent = if (totalBeforeConcession <= 0) 0.0
        else (rawConcession.toDouble() * 100.0 / totalBeforeConcession.toDouble())

        val requiresAdminApproval = rawConcession > 0 &&
            effectiveConcessionPercent > maxConcessionPercentWithoutAdmin.toDouble() + 0.0001

        // Allocate concession across lines
        val linesWithConcession = if (rawConcession <= 0) {
            rawLines
        } else if (concessionScope == ConcessionScope.SINGLE_LINE && targetLine != null) {
            rawLines.map { line ->
                if (line.lineId == targetLine.lineId) {
                    val c = rawConcession.coerceAtMost(line.lineGrossAmount)
                    val net = (line.lineGrossAmount - c).coerceAtLeast(0)
                    line.copy(concessionAllocated = c, netPayableOnLine = net)
                } else line
            }
        } else {
            // Proportional distribution across all lines with remainder on last eligible line
            var remainingConc = rawConcession
            rawLines.mapIndexed { idx, line ->
                val c = if (idx == rawLines.lastIndex) {
                    remainingConc.coerceIn(0, line.lineGrossAmount)
                } else {
                    val share = floor((rawConcession.toDouble() * line.lineGrossAmount.toDouble() / totalBeforeConcession.toDouble()) + 0.5).toInt()
                        .coerceIn(0, minOf(remainingConc, line.lineGrossAmount))
                    share
                }
                remainingConc -= c
                val net = (line.lineGrossAmount - c).coerceAtLeast(0)
                line.copy(concessionAllocated = c, netPayableOnLine = net)
            }
        }

        val netPayable = (totalBeforeConcession - rawConcession).coerceAtLeast(0)
        val resolvedAmountPaid = (amountPaidInput ?: netPayable).coerceIn(0, netPayable)
        val balanceAfterPayment = (netPayable - resolvedAmountPaid).coerceAtLeast(0)

        // Allocate resolvedAmountPaid across lines in order so partial payment leaves the remaining amount due on trailing cells
        var remainingCashToAllocate = resolvedAmountPaid
        val allocatedLines = linesWithConcession.map { line ->
            val paidOnThisLine = minOf(remainingCashToAllocate, line.netPayableOnLine)
            remainingCashToAllocate -= paidOnThisLine
            val balOnLine = (line.netPayableOnLine - paidOnThisLine).coerceAtLeast(0)
            line.copy(
                paidAllocated = paidOnThisLine,
                balanceDueAfterLine = balOnLine
            )
        }

        val validationError = when {
            rawConcession > 0 && concessionRemark.trim().isEmpty() ->
                "Please enter a mandatory remark for the concession (for example: Sibling or Teacher's child)."
            requiresAdminApproval && !Phase9AccessEvaluator.isConcessionWithinMemberLimit(
                member = actorMember,
                effectiveConcessionPercent = effectiveConcessionPercent,
                maxPercentWithoutAdmin = maxConcessionPercentWithoutAdmin,
                hasExplicitAdminApproval = hasExplicitAdminApproval
            ) ->
                "Concession of ${String.format(Locale.US, "%.1f", effectiveConcessionPercent)}% exceeds the ${maxConcessionPercentWithoutAdmin}% limit and requires Admin approval."
            netPayable > 0 && resolvedAmountPaid <= 0 ->
                "Amount paid must be greater than Rs. 0."
            amountPaidInput != null && amountPaidInput > netPayable ->
                "Amount paid (Rs. $amountPaidInput) cannot exceed payable amount (Rs. $netPayable)."
            else -> null
        }

        return FeePaymentDraftComputation(
            draftLines = rawLines,
            totalBeforeConcession = totalBeforeConcession,
            concessionAmount = rawConcession,
            effectiveConcessionPercent = effectiveConcessionPercent,
            requiresAdminApprovalForConcession = requiresAdminApproval,
            netPayableAmount = netPayable,
            amountPaid = resolvedAmountPaid,
            balanceDueAfterPayment = balanceAfterPayment,
            allocatedLines = allocatedLines,
            validationError = validationError
        )
    }

    /**
     * Generates a running receipt number per school and session that is 100% collision-free
     * even when two or more phones collect fees offline simultaneously.
     *
     * How clashes are avoided offline:
     * 1. Each device/clerk installation has a short deterministic 2-to-4 char [deviceNodeCode]
     *    (e.g. `A1`, `B2`, derived from device installation ID + user UID).
     * 2. For a given `(schoolId, session, deviceNodeCode)`, we take:
     *    `nextSeq = max(highestObservedSequenceForThisDeviceInCache, localDeviceCounter) + 1`.
     * 3. The formatted receipt number is `RCP-{SESSION_SHORT}-{DEVICE_CODE}-{SEQ_4_DIGIT}`
     *    (e.g. `RCP-2627-A1-0001` on Phone 1 and `RCP-2627-B2-0001` on Phone 2).
     *    Because each phone increments its own device-scoped sequence namespace, two phones used
     *    offline never generate the same receipt number or Firestore document ID.
     */
    fun generateNextReceiptNumber(
        session: String,
        deviceNodeCode: String,
        existingReceiptsForSchoolAndSession: List<FeeReceiptRecord>,
        lastLocalSequenceForDevice: Int = 0
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
        val maxInReceiptsForNode = existingReceiptsForSchoolAndSession
            .filter { it.deviceNodeCode.equals(cleanNode, ignoreCase = true) }
            .maxOfOrNull { it.sequenceNo } ?: 0
        val nextSeq = maxOf(maxInReceiptsForNode, lastLocalSequenceForDevice) + 1
        val padded = String.format(Locale.US, "%04d", nextSeq)
        val receiptNo = "RCP-$sessionShort-$cleanNode-$padded"
        return receiptNo to nextSeq
    }

    fun deriveDeviceNodeCode(deviceUuid: String, userUid: String): String {
        val seed = "${deviceUuid.trim()}_${userUid.trim()}"
        val hash = StableDocId.toLong(seed)
        val letters = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val digits = "123456789"
        val c1 = letters[(hash % letters.length).toInt()]
        val c2 = digits[((hash / letters.length) % digits.length).toInt()]
        val c3 = letters[((hash / (letters.length * digits.length)) % letters.length).toInt()]
        return "$c1$c2$c3"
    }
}

// ============================================================================
// PART D & PART D2: REPORTS, PHONE NORMALISATION & SEND QUEUE ENGINE
// ============================================================================

object PhoneNormalizer {
    /**
     * Normalises an Indian mobile number to `+91XXXXXXXXXX` (and `91XXXXXXXXXX` for `wa.me` links).
     * Accepts:
     * - `9839100001` (10 digits starting with 6, 7, 8, or 9)
     * - `09839100001` (11 digits with leading 0)
     * - `919839100001` or `+91 98391-00001` (12 digits starting with 91)
     * Rejects empty, <10 digits, >12 digits, or invalid leading digit.
     */
    fun normalizeIndianMobile(raw: String?): PhoneValidationResult {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return PhoneValidationResult.Invalid("Mobile number is missing")
        }
        val digitsOnly = trimmed.filter { it.isDigit() }
        val tenDigits = when {
            digitsOnly.length == 10 -> digitsOnly
            digitsOnly.length == 11 && digitsOnly.startsWith("0") -> digitsOnly.substring(1)
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> digitsOnly.substring(2)
            digitsOnly.length == 13 && digitsOnly.startsWith("091") -> digitsOnly.substring(3)
            else -> return PhoneValidationResult.Invalid("Must be a 10-digit Indian mobile number (found ${digitsOnly.length} digits)")
        }
        if (tenDigits.first() !in charArrayOf('6', '7', '8', '9')) {
            return PhoneValidationResult.Invalid("Indian mobile number must start with 6, 7, 8, or 9")
        }
        return PhoneValidationResult.Valid(
            e164WithPlus = "+91$tenDigits",
            waMeDigits = "91$tenDigits",
            tenDigitNational = tenDigits
        )
    }

    fun buildWaMeLink(waMeDigits: String, messageText: String): String {
        val encoded = buildPercentEncodedText(messageText)
        return "https://wa.me/$waMeDigits?text=$encoded"
    }

    private fun buildPercentEncodedText(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val ch = b.toInt() and 0xFF
            val isUnreserved = (ch in 'A'.code..'Z'.code) ||
                (ch in 'a'.code..'z'.code) ||
                (ch in '0'.code..'9'.code) ||
                ch == '-'.code || ch == '_'.code || ch == '.'.code || ch == '~'.code
            if (isUnreserved) {
                sb.append(ch.toChar())
            } else {
                sb.append('%')
                sb.append(String.format(Locale.US, "%02X", ch))
            }
        }
        return sb.toString()
    }
}

object FeeReportsAndQueueEngine {

    fun buildDayCollectionRegister(
        receipts: List<FeeReceiptRecord>,
        schoolId: String,
        session: String,
        startDateIso: String,
        endDateIso: String
    ): DayCollectionRegisterSummary {
        val inRange = receipts.filter { rcp ->
            rcp.schoolId == schoolId &&
                rcp.session.equals(session, ignoreCase = true) &&
                Phase6DateUtils.isDateWithinRange(rcp.receiptDateIso, startDateIso, endDateIso)
        }.sortedWith(compareBy<FeeReceiptRecord> { it.receiptDateIso }.thenBy { it.sequenceNo })

        val active = inRange.filter { !it.isCancelled }
        val cancelled = inRange.filter { it.isCancelled }

        val byMode = PaymentMode.entries.associateWith { mode ->
            active.filter { it.paymentMode == mode }.sumOf { it.amountPaid }
        }

        return DayCollectionRegisterSummary(
            startDateIso = startDateIso,
            endDateIso = endDateIso,
            activeReceipts = active,
            cancelledReceipts = cancelled,
            totalByMode = byMode,
            totalGrossBeforeConcession = active.sumOf { it.totalBeforeConcession },
            totalConcessionGiven = active.sumOf { it.concessionAmount },
            totalNetCollected = active.sumOf { it.amountPaid }
        )
    }

    fun buildDuesList(
        studentsWithDocIds: List<Pair<String, StudentEntity>>,
        schoolDocId: String,
        schoolName: String,
        structure: FeeStructureConfig,
        overridesByStudentDocId: Map<String, StudentFeeOverride>,
        receipts: List<FeeReceiptRecord>,
        classNameFilter: String? = null,
        sectionFilter: String? = null,
        monthFilter: String? = null,
        feeHeadFilter: FeeHeadType? = null,
        minimumDueAmount: Int = 1,
        includeLeftStudents: Boolean = false
    ): List<StudentDuesListRow> {
        val receiptsByStudent = receipts.groupBy { it.studentId }
        return studentsWithDocIds
            .filter { (_, st) ->
                st.session.equals(structure.session, ignoreCase = true) &&
                    (includeLeftStudents || st.isActiveStatus) &&
                    (classNameFilter.isNullOrBlank() || st.className.equals(classNameFilter, ignoreCase = true)) &&
                    (sectionFilter.isNullOrBlank() || st.section.equals(sectionFilter, ignoreCase = true))
            }
            .mapNotNull { (stuDocId, student) ->
                val account = FeeCollectionEngine.computeStudentFeeAccount(
                    structure = structure,
                    student = student,
                    studentDocId = stuDocId,
                    override = overridesByStudentDocId[stuDocId],
                    receiptsForStudent = receiptsByStudent[stuDocId].orEmpty()
                )
                val matchingCells = account.allCells.filter { cell ->
                    cell.remainingDue > 0 &&
                        (monthFilter.isNullOrBlank() || cell.monthCode.equals(monthFilter, ignoreCase = true)) &&
                        (feeHeadFilter == null || cell.headType == feeHeadFilter)
                }
                val totalDue = matchingCells.sumOf { it.remainingDue }
                if (totalDue < minimumDueAmount.coerceAtLeast(1)) return@mapNotNull null

                val breakdowns = matchingCells.map { cell ->
                    StudentDueCellBreakdown(
                        monthCode = cell.monthCode,
                        headType = cell.headType,
                        label = cell.label,
                        dueAmount = cell.remainingDue
                    )
                }
                val dueByHead = FeeHeadType.entries.associateWith { h ->
                    matchingCells.filter { it.headType == h }.sumOf { it.remainingDue }
                }.filterValues { it > 0 }
                val dueMonths = matchingCells.map { SessionMonthCode.shortLabelFor(it.monthCode) }.distinct()

                StudentDuesListRow(
                    schoolDocId = schoolDocId,
                    schoolName = schoolName,
                    studentDocId = stuDocId,
                    student = student,
                    dueBreakdowns = breakdowns,
                    dueByHead = dueByHead,
                    dueMonths = dueMonths,
                    totalDueAmount = totalDue
                )
            }
            .sortedWith(
                compareBy<StudentDuesListRow> { it.student.className.lowercase(Locale.US) }
                    .thenBy { it.student.section.uppercase(Locale.US) }
                    .thenBy { it.student.rollNo }
            )
    }

    fun buildDuesListCsv(rows: List<StudentDuesListRow>, session: String): String {
        fun esc(v: String): String {
            val clean = v.replace("\"", "\"\"")
            return if (clean.contains(',') || clean.contains('"') || clean.contains('\n')) "\"$clean\"" else clean
        }
        val sb = StringBuilder()
        sb.appendLine("School,Session,Class,Section,Roll No,Admission No,Student Name,Guardian Name,Mobile,Due Months,Tuition Due,Transport Due,Exam Due,Total Due (Rs)")
        for (r in rows) {
            val tuitionDue = r.dueByHead[FeeHeadType.TUITION] ?: 0
            val transportDue = r.dueByHead[FeeHeadType.TRANSPORT] ?: 0
            val examDue = FeeHeadType.examHeads.sumOf { r.dueByHead[it] ?: 0 }
            sb.appendLine(
                listOf(
                    esc(r.schoolName),
                    esc(session),
                    esc(r.student.className),
                    esc(r.student.section),
                    r.student.rollNo.toString(),
                    esc(r.student.admNo),
                    esc(r.student.name),
                    esc(r.student.fatherName.ifBlank { r.student.motherName }),
                    esc(r.student.mobile),
                    esc(r.dueMonths.joinToString(" ")),
                    tuitionDue.toString(),
                    transportDue.toString(),
                    examDue.toString(),
                    r.totalDueAmount.toString()
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun buildConcessionReport(
        receipts: List<FeeReceiptRecord>,
        schoolId: String,
        session: String,
        startDateIso: String? = null,
        endDateIso: String? = null
    ): List<ConcessionReportRow> {
        return receipts
            .filter { rcp ->
                !rcp.isCancelled &&
                    rcp.schoolId == schoolId &&
                    rcp.session.equals(session, ignoreCase = true) &&
                    rcp.concessionAmount > 0 &&
                    (startDateIso.isNullOrBlank() || endDateIso.isNullOrBlank() ||
                        Phase6DateUtils.isDateWithinRange(rcp.receiptDateIso, startDateIso, endDateIso))
            }
            .sortedWith(compareByDescending<FeeReceiptRecord> { it.receiptDateIso }.thenByDescending { it.sequenceNo })
            .map { rcp ->
                ConcessionReportRow(
                    receiptId = rcp.id,
                    receiptNumber = rcp.receiptNumber,
                    receiptDateIso = rcp.receiptDateIso,
                    studentName = rcp.studentName,
                    studentAdmNo = rcp.studentAdmNo,
                    className = rcp.className,
                    section = rcp.section,
                    totalBeforeConcession = rcp.totalBeforeConcession,
                    concessionAmount = rcp.concessionAmount,
                    concessionMode = rcp.concessionMode,
                    concessionScope = rcp.concessionScope,
                    remark = rcp.concessionRemark,
                    givenByName = rcp.concessionGivenByName.ifBlank { rcp.receivedByName },
                    approvedByAdminName = rcp.concessionApprovedByAdminName
                )
            }
    }

    fun buildOptionalItemSalesReport(
        receipts: List<FeeReceiptRecord>,
        schoolId: String,
        session: String,
        startDateIso: String? = null,
        endDateIso: String? = null
    ): List<OptionalItemSalesRow> {
        val activeReceipts = receipts.filter { rcp ->
            !rcp.isCancelled &&
                rcp.schoolId == schoolId &&
                rcp.session.equals(session, ignoreCase = true) &&
                (startDateIso.isNullOrBlank() || endDateIso.isNullOrBlank() ||
                    Phase6DateUtils.isDateWithinRange(rcp.receiptDateIso, startDateIso, endDateIso))
        }

        data class Acc(
            val key: String,
            val name: String,
            var qty: Int = 0,
            var gross: Int = 0,
            var netPaid: Int = 0,
            val receiptIds: MutableSet<String> = mutableSetOf()
        )

        val map = linkedMapOf<String, Acc>()
        for (rcp in activeReceipts) {
            for (line in rcp.lineItems) {
                if (line.lineType != FeeLineItemType.OPTIONAL_ITEM && line.lineType != FeeLineItemType.CUSTOM_CHARGE) continue
                val cleanDisplayName = line.title.substringBeforeLast(" (").trim().ifBlank { line.title }
                val key = line.catalogueItemId.ifBlank { cleanDisplayName.lowercase(Locale.US) }
                val acc = map.getOrPut(key) { Acc(key = key, name = cleanDisplayName) }
                acc.qty += line.quantity
                acc.gross += line.lineGrossAmount
                acc.netPaid += line.paidAllocated
                acc.receiptIds.add(rcp.id)
            }
        }
        return map.values.map { acc ->
            OptionalItemSalesRow(
                itemKey = acc.key,
                itemName = acc.name,
                totalQuantitySold = acc.qty,
                totalGrossAmount = acc.gross,
                totalNetCollected = acc.netPaid,
                receiptCount = acc.receiptIds.size
            )
        }.sortedByDescending { it.totalGrossAmount }
    }

    /**
     * Fills `{guardian}`, `{student}`, `{class}`, `{amount}`, `{months}`, `{school}` in a message template.
     */
    fun fillMessageTemplate(
        template: String,
        guardianName: String,
        studentName: String,
        classSection: String,
        amountRs: Int,
        monthsText: String,
        schoolName: String
    ): String {
        val safeGuardian = guardianName.trim().ifBlank { "Parent/Guardian" }
        val safeMonths = monthsText.trim().ifBlank { "current session" }
        return template
            .replace("{guardian}", safeGuardian, ignoreCase = true)
            .replace("{student}", studentName.trim(), ignoreCase = true)
            .replace("{class}", classSection.trim(), ignoreCase = true)
            .replace("{amount}", amountRs.toString(), ignoreCase = true)
            .replace("{months}", safeMonths, ignoreCase = true)
            .replace("{school}", schoolName.trim(), ignoreCase = true)
    }

    /**
     * Builds the WhatsApp/SMS Send Queue rows with +91 phone normalisation, prefilled template text,
     * `wa.me` URL, and duplicate-prevention sent log matching.
     */
    fun buildSendQueue(
        duesRows: List<StudentDuesListRow>,
        schoolName: String,
        templates: FeeMessageTemplates,
        messageType: FeeMessageType = FeeMessageType.FEE_DUE_REMINDER,
        customGeneralText: String = "",
        contextDateOrMonthKey: String,
        sentLogs: List<FeeMessageSentLog>,
        showPendingOnly: Boolean = false
    ): List<SendQueueGuardianRow> {
        val sentByKey = sentLogs
            .filter { it.messageType == messageType }
            .associateBy { "${it.studentId}|${it.contextKey}" }

        return duesRows.mapNotNull { row ->
            val st = row.student
            val guardian = st.fatherName.ifBlank { st.motherName }.ifBlank { "Guardian" }
            val rawMobile = st.mobile.ifBlank { st.secondMobile }
            val phoneVal = PhoneNormalizer.normalizeIndianMobile(rawMobile)
            val classSec = "${st.className}-${st.section}"
            val monthsStr = row.dueMonths.joinToString(", ").ifBlank { "Pending Months" }

            val rawTemplate = when (messageType) {
                FeeMessageType.FEE_DUE_REMINDER -> templates.feeDueTemplate
                FeeMessageType.FEE_RECEIPT -> templates.feeReceiptTemplate
                FeeMessageType.GENERAL -> customGeneralText.ifBlank { templates.generalTemplate }
            }

            val msgText = fillMessageTemplate(
                template = rawTemplate,
                guardianName = guardian,
                studentName = st.name,
                classSection = classSec,
                amountRs = row.totalDueAmount,
                monthsText = monthsStr,
                schoolName = schoolName
            )

            val contextKey = "${messageType.wireValue}_${contextDateOrMonthKey}_${row.totalDueAmount}"
            val existingSent = sentByKey["${row.studentDocId}|$contextKey"]
            if (showPendingOnly && existingSent != null) return@mapNotNull null

            val waUrl = (phoneVal as? PhoneValidationResult.Valid)?.let { valid ->
                PhoneNormalizer.buildWaMeLink(valid.waMeDigits, msgText)
            }

            SendQueueGuardianRow(
                schoolDocId = row.schoolDocId,
                schoolName = schoolName,
                studentDocId = row.studentDocId,
                student = st,
                guardianName = guardian,
                rawMobile = rawMobile,
                phoneValidation = phoneVal,
                dueAmount = row.totalDueAmount,
                dueMonthsSummary = monthsStr,
                messageType = messageType,
                contextKey = contextKey,
                messageText = msgText,
                waMeUrl = waUrl,
                sentLog = existingSent
            )
        }
    }
}
