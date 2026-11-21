package com.example.domain

import com.example.data.SchoolSettingsEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

sealed class MarkValidationResult {
    data class Valid(val normalized: String) : MarkValidationResult()
    data class Invalid(val message: String) : MarkValidationResult()
}

data class GradeInfo(
    val grade: String,
    val remark: String
)

enum class ResultStatus(val label: String) {
    PASS("PASS"),
    FAIL("FAIL"),
    PENDING("Pending")
}

data class AttendanceSummary(
    val t1Present: Int,
    val t1Working: Int,
    val t2Present: Int,
    val t2Working: Int
) {
    val totalPresent: Int get() = t1Present + t2Present
    val totalWorking: Int get() = t1Working + t2Working

    fun formatTerm1(): String = if (t1Working > 0 || t1Present > 0) "$t1Present / $t1Working" else "-"
    fun formatTerm2(): String = if (t2Working > 0 || t2Present > 0) "$t2Present / $t2Working" else "-"
    fun formatGrandTotal(): String =
        if (totalWorking > 0 || totalPresent > 0) "$totalPresent / $totalWorking" else "-"

    fun formatCompactTerm1(): String = if (t1Working > 0 || t1Present > 0) "$t1Present/$t1Working" else "-"
    fun formatCompactTerm2(): String = if (t2Working > 0 || t2Present > 0) "$t2Present/$t2Working" else "-"
    fun formatCompactGrandTotal(): String =
        if (totalWorking > 0 || totalPresent > 0) "$totalPresent/$totalWorking" else "-"
}

data class SubjectExamRow(
    val subjectName: String,
    val rawValue: String,
    val obtained: Double,
    val maxMarks: Int,
    val grade: String,
    val hasEntry: Boolean
)

data class SingleExamReport(
    val examType: ExamType,
    val rows: List<SubjectExamRow>,
    val totalObtained: Double,
    val totalMax: Int,
    val percentage: Double,
    val grade: String,
    val resultStatus: ResultStatus
)

data class SubjectTermDetail(
    val subjectName: String,
    val t1Components: Map<MarkComponent, String>,
    val t1Total: Double,
    val t1Max: Int,
    val t1Grade: String,
    val t1HasAnyEntry: Boolean,
    val t2Components: Map<MarkComponent, String>,
    val t2Total: Double,
    val t2Max: Int,
    val t2Grade: String,
    val t2HasAnyEntry: Boolean,
    val grandTotal: Double,
    val grandMax: Int,
    val grandGrade: String
)

data class StudentPerformanceReport(
    val studentId: Long,
    val subjects: List<SubjectTermDetail>,
    val term1Total: Double,
    val term1Max: Int,
    val term1Percentage: Double,
    val term1Grade: String,
    val term1Result: ResultStatus,
    val term2Total: Double,
    val term2Max: Int,
    val term2Percentage: Double,
    val term2Grade: String,
    val term2Result: ResultStatus,
    val overallTotal: Double,
    val overallMax: Int,
    val overallPercentage: Double,
    val overallGrade: String,
    val overallResult: ResultStatus,
    val hasAnyAnnualMarks: Boolean,
    val classRank: Int? = null
)

object ReportCalculations {

    val gradingScaleRows: List<Pair<String, String>> = listOf(
        "91 - 100" to "A1",
        "81 - 90" to "A2",
        "71 - 80" to "B1",
        "61 - 70" to "B2",
        "51 - 60" to "C1",
        "41 - 50" to "C2",
        "33 - 40" to "D",
        "32 & Below" to "E (Need improvement)"
    )

    fun resolveSchoolIdentity(
        settings: SchoolSettingsEntity,
        className: String
    ): ResolvedSchoolIdentity {
        val normalizedClass = SchoolClass.fromName(className).displayName
        val map = SchoolClass.parseIdentityMap(settings.classIdentityMapCsv)
        val code = map[normalizedClass] ?: SchoolClass.fromName(className).defaultIdentity

        return if (code == SchoolIdentityCode.CPS) {
            ResolvedSchoolIdentity(
                code = SchoolIdentityCode.CPS,
                schoolName = settings.cpsName.ifBlank { SchoolIdentityCode.CPS.defaultName },
                address = settings.cpsAddress,
                mobile = settings.cpsMobile,
                email = settings.cpsEmail,
                logoBase64 = settings.cpsLogoBase64,
                logoCleared = settings.cpsLogoCleared
            )
        } else {
            val logo = if (settings.useSameLogoForBoth) settings.cpsLogoBase64 else settings.cpiLogoBase64
            val cleared = if (settings.useSameLogoForBoth) settings.cpsLogoCleared else settings.cpiLogoCleared
            ResolvedSchoolIdentity(
                code = SchoolIdentityCode.CPI,
                schoolName = settings.cpiName.ifBlank { SchoolIdentityCode.CPI.defaultName },
                address = settings.cpiAddress,
                mobile = settings.cpiMobile,
                email = settings.cpiEmail,
                logoBase64 = logo,
                logoCleared = cleared
            )
        }
    }

    fun validateMarkInput(raw: String, maxMarks: Int): MarkValidationResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return MarkValidationResult.Valid("")
        }
        if (trimmed.equals("AB", ignoreCase = true)) {
            return MarkValidationResult.Valid("AB")
        }
        val parsed = trimmed.toDoubleOrNull()
            ?: return MarkValidationResult.Invalid("Enter 0–$maxMarks or AB")
        if (parsed.isNaN() || parsed.isInfinite()) {
            return MarkValidationResult.Invalid("Enter 0–$maxMarks or AB")
        }
        if (parsed < 0.0) {
            return MarkValidationResult.Invalid("Mark cannot be negative")
        }
        if (parsed > maxMarks.toDouble()) {
            return MarkValidationResult.Invalid("Max allowed is $maxMarks")
        }
        return MarkValidationResult.Valid(formatNumber(parsed))
    }

    fun validateAttendance(present: Int, working: Int): String? {
        if (present < 0 || working < 0) return "Attendance days cannot be negative"
        if (present > working) return "Days present ($present) cannot exceed working days ($working)"
        return null
    }

    fun numericMarkValue(raw: String?): Double {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("AB", ignoreCase = true)) return 0.0
        return trimmed.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    }

    fun displayMarkValue(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return "-"
        if (trimmed.equals("AB", ignoreCase = true)) return "AB"
        val number = trimmed.toDoubleOrNull() ?: return trimmed
        return formatNumber(number)
    }

    fun formatNumber(value: Double): String {
        val rounded = value.roundToInt()
        return if (abs(value - rounded) < 0.0001) {
            rounded.toString()
        } else {
            val formatted = String.format(Locale.US, "%.2f", value)
            formatted.trimEnd('0').trimEnd('.')
        }
    }

    fun formatPercent(percentage: Double): String =
        String.format(Locale.US, "%.2f", percentage)

    fun calculatePercentage(obtained: Double, max: Double): Double {
        if (max <= 0.0) return 0.0
        val raw = (obtained / max) * 100.0
        return kotlin.math.round(raw * 100.0) / 100.0
    }

    /**
     * 8-point grading scale applied to percentage rounded to the nearest whole number.
     */
    fun calculateGrade(percentage: Double): GradeInfo {
        val rounded = percentage.roundToInt().coerceIn(0, 100)
        return when {
            rounded >= 91 -> GradeInfo("A1", "Outstanding")
            rounded >= 81 -> GradeInfo("A2", "Excellent")
            rounded >= 71 -> GradeInfo("B1", "Very Good")
            rounded >= 61 -> GradeInfo("B2", "Good")
            rounded >= 51 -> GradeInfo("C1", "Satisfactory")
            rounded >= 41 -> GradeInfo("C2", "Average")
            rounded >= 33 -> GradeInfo("D", "Pass")
            else -> GradeInfo("E", "Need improvement")
        }
    }

    fun evaluatePassStatus(
        subjectTotalsAndMax: List<Pair<Double, Double>>,
        hasAnyMarks: Boolean,
        passPercent: Int = 33
    ): ResultStatus {
        if (!hasAnyMarks || subjectTotalsAndMax.isEmpty()) return ResultStatus.PENDING
        val allPassed = subjectTotalsAndMax.all { (obtained, max) ->
            if (max <= 0.0) true
            else {
                val pct = (obtained / max) * 100.0
                pct + 1e-6 >= passPercent.toDouble()
            }
        }
        return if (allPassed) ResultStatus.PASS else ResultStatus.FAIL
    }

    fun calculateSingleExamReport(
        examType: ExamType,
        subjects: List<String>,
        marksBySubjectAndKey: Map<Pair<String, String>, String>,
        passPercent: Int = 33
    ): SingleExamReport {
        val component = when (examType) {
            ExamType.UT1 -> MarkComponent.T1_UT
            ExamType.UT2 -> MarkComponent.T2_UT
            else -> MarkComponent.T1_UT
        }
        var totalObtained = 0.0
        var totalMax = 0
        var anyEntered = false

        val rows = subjects.map { subject ->
            val raw = marksBySubjectAndKey[subject to component.key].orEmpty().trim()
            val hasEntry = raw.isNotEmpty()
            if (hasEntry) anyEntered = true
            val obtained = numericMarkValue(raw)
            val max = component.maxMarks
            totalObtained += obtained
            totalMax += max
            val pct = calculatePercentage(obtained, max.toDouble())
            val grade = if (hasEntry) calculateGrade(pct).grade else "-"
            SubjectExamRow(
                subjectName = subject,
                rawValue = raw,
                obtained = obtained,
                maxMarks = max,
                grade = grade,
                hasEntry = hasEntry
            )
        }

        val overallPct = calculatePercentage(totalObtained, totalMax.toDouble())
        val grade = if (anyEntered) calculateGrade(overallPct).grade else "-"
        val result = evaluatePassStatus(
            subjectTotalsAndMax = rows.map { it.obtained to it.maxMarks.toDouble() },
            hasAnyMarks = anyEntered,
            passPercent = passPercent
        )

        return SingleExamReport(
            examType = examType,
            rows = rows,
            totalObtained = totalObtained,
            totalMax = totalMax,
            percentage = overallPct,
            grade = grade,
            resultStatus = result
        )
    }

    fun calculateStudentPerformance(
        studentId: Long,
        subjects: List<String>,
        marksBySubjectAndKey: Map<Pair<String, String>, String>,
        passPercent: Int = 33
    ): StudentPerformanceReport {
        val t1ComponentsList = MarkComponent.term1Components()
        val t2ComponentsList = MarkComponent.term2Components()

        var term1Sum = 0.0
        var term1MaxSum = 0
        var term1Any = false

        var term2Sum = 0.0
        var term2MaxSum = 0
        var term2Any = false

        val subjectDetails = subjects.map { subject ->
            val t1Map = t1ComponentsList.associateWith { comp ->
                marksBySubjectAndKey[subject to comp.key].orEmpty().trim()
            }
            val t1Has = t1Map.values.any { it.isNotEmpty() }
            if (t1Has) term1Any = true
            val t1Total = t1Map.values.sumOf { numericMarkValue(it) }
            val t1Max = t1ComponentsList.sumOf { it.maxMarks }
            val t1Pct = calculatePercentage(t1Total, t1Max.toDouble())
            val t1Grade = if (t1Has) calculateGrade(t1Pct).grade else "-"

            val t2Map = t2ComponentsList.associateWith { comp ->
                marksBySubjectAndKey[subject to comp.key].orEmpty().trim()
            }
            val t2Has = t2Map.values.any { it.isNotEmpty() }
            if (t2Has) term2Any = true
            val t2Total = t2Map.values.sumOf { numericMarkValue(it) }
            val t2Max = t2ComponentsList.sumOf { it.maxMarks }
            val t2Pct = calculatePercentage(t2Total, t2Max.toDouble())
            val t2Grade = if (t2Has) calculateGrade(t2Pct).grade else "-"

            val grandTotal = t1Total + t2Total
            val grandMax = t1Max + t2Max
            val grandPct = calculatePercentage(grandTotal, grandMax.toDouble())
            val grandGrade = if (t1Has || t2Has) calculateGrade(grandPct).grade else "-"

            term1Sum += t1Total
            term1MaxSum += t1Max
            term2Sum += t2Total
            term2MaxSum += t2Max

            SubjectTermDetail(
                subjectName = subject,
                t1Components = t1Map,
                t1Total = t1Total,
                t1Max = t1Max,
                t1Grade = t1Grade,
                t1HasAnyEntry = t1Has,
                t2Components = t2Map,
                t2Total = t2Total,
                t2Max = t2Max,
                t2Grade = t2Grade,
                t2HasAnyEntry = t2Has,
                grandTotal = grandTotal,
                grandMax = grandMax,
                grandGrade = grandGrade
            )
        }

        val t1Pct = calculatePercentage(term1Sum, term1MaxSum.toDouble())
        val t2Pct = calculatePercentage(term2Sum, term2MaxSum.toDouble())
        val overallSum = term1Sum + term2Sum
        val overallMax = term1MaxSum + term2MaxSum
        val overallPct = calculatePercentage(overallSum, overallMax.toDouble())
        val hasAnyAnnual = term1Any || term2Any

        val t1Result = evaluatePassStatus(
            subjectTotalsAndMax = subjectDetails.map { it.t1Total to it.t1Max.toDouble() },
            hasAnyMarks = term1Any,
            passPercent = passPercent
        )
        val t2Result = evaluatePassStatus(
            subjectTotalsAndMax = subjectDetails.map { it.t2Total to it.t2Max.toDouble() },
            hasAnyMarks = term2Any,
            passPercent = passPercent
        )
        val overallResult = evaluatePassStatus(
            subjectTotalsAndMax = subjectDetails.map { it.grandTotal to it.grandMax.toDouble() },
            hasAnyMarks = hasAnyAnnual,
            passPercent = passPercent
        )

        return StudentPerformanceReport(
            studentId = studentId,
            subjects = subjectDetails,
            term1Total = term1Sum,
            term1Max = term1MaxSum,
            term1Percentage = t1Pct,
            term1Grade = if (term1Any) calculateGrade(t1Pct).grade else "-",
            term1Result = t1Result,
            term2Total = term2Sum,
            term2Max = term2MaxSum,
            term2Percentage = t2Pct,
            term2Grade = if (term2Any) calculateGrade(t2Pct).grade else "-",
            term2Result = t2Result,
            overallTotal = overallSum,
            overallMax = overallMax,
            overallPercentage = overallPct,
            overallGrade = if (hasAnyAnnual) calculateGrade(overallPct).grade else "-",
            overallResult = overallResult,
            hasAnyAnnualMarks = hasAnyAnnual
        )
    }

    fun calculateClassRanks(reports: List<StudentPerformanceReport>): Map<Long, Int> {
        val eligible = reports
            .filter { it.hasAnyAnnualMarks }
            .sortedByDescending { it.overallTotal }

        val rankMap = mutableMapOf<Long, Int>()
        var lastTotal: Double? = null
        var currentRank = 0
        eligible.forEachIndexed { index, report ->
            if (lastTotal == null || abs(report.overallTotal - lastTotal!!) > 0.0001) {
                currentRank = index + 1
                lastTotal = report.overallTotal
            }
            rankMap[report.studentId] = currentRank
        }
        return rankMap
    }
}
