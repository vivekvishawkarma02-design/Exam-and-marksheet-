package com.example

import com.example.domain.AttendanceSummary
import com.example.domain.DefaultSchoolTemplate
import com.example.domain.DefaultSubjects
import com.example.domain.ExamType
import com.example.domain.MarkComponent
import com.example.domain.MarkValidationResult
import com.example.domain.ReportCalculations
import com.example.domain.ResultStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCalculationsTest {

    @Test
    fun `acceptance_a unified 10-30-60 term and annual totals match example`() {
        val marks = mapOf(
            ("MATH" to MarkComponent.T1_IA.key) to "8",
            ("MATH" to MarkComponent.T1_UT.key) to "25",
            ("MATH" to MarkComponent.T1_EXAM.key) to "50",
            ("MATH" to MarkComponent.T2_IA.key) to "8",
            ("MATH" to MarkComponent.T2_UT.key) to "26",
            ("MATH" to MarkComponent.T2_EXAM.key) to "54"
        )

        val report = ReportCalculations.calculateStudentPerformance(
            studentId = 1L,
            subjects = listOf("MATH"),
            marksBySubjectAndKey = marks,
            passPercent = 33
        )

        val math = report.subjects.first()
        assertEquals(83.0, math.t1Total, 0.001)
        assertEquals("A2", math.t1Grade)
        assertEquals(88.0, math.t2Total, 0.001)
        assertEquals("A2", math.t2Grade)
        assertEquals(171.0, math.grandTotal, 0.001)
        assertEquals(200, math.grandMax)
        assertEquals(85.5, report.overallPercentage, 0.001)
        assertEquals("A2", math.grandGrade)
        assertEquals("A2", report.overallGrade)
        assertEquals(ResultStatus.PASS, report.overallResult)
    }

    @Test
    fun `acceptance_b validation rejects over-max and accepts 0 and AB case-insensitively`() {
        assertTrue(ReportCalculations.validateMarkInput("11", MarkComponent.T1_IA.maxMarks) is MarkValidationResult.Invalid)
        assertTrue(ReportCalculations.validateMarkInput("31", MarkComponent.T1_UT.maxMarks) is MarkValidationResult.Invalid)
        assertTrue(ReportCalculations.validateMarkInput("61", MarkComponent.T1_EXAM.maxMarks) is MarkValidationResult.Invalid)
        assertTrue(ReportCalculations.validateMarkInput("-1", 10) is MarkValidationResult.Invalid)
        assertTrue(ReportCalculations.validateMarkInput("xyz", 30) is MarkValidationResult.Invalid)

        assertEquals(MarkValidationResult.Valid("0"), ReportCalculations.validateMarkInput("0", 10))
        assertEquals(MarkValidationResult.Valid("10"), ReportCalculations.validateMarkInput("10", 10))
        assertEquals(MarkValidationResult.Valid("30"), ReportCalculations.validateMarkInput("30", 30))
        assertEquals(MarkValidationResult.Valid("60"), ReportCalculations.validateMarkInput("60", 60))

        assertEquals(MarkValidationResult.Valid("AB"), ReportCalculations.validateMarkInput("ab", 60))
        assertEquals(MarkValidationResult.Valid("AB"), ReportCalculations.validateMarkInput(" Ab ", 30))
        assertEquals(0.0, ReportCalculations.numericMarkValue("AB"), 0.0001)
        assertEquals("AB", ReportCalculations.displayMarkValue("AB"))
    }

    @Test
    fun `acceptance_c default template preserves exact 7-6-8 subject lists and order`() {
        assertEquals(
            listOf(
                "HINDI",
                "HINDI (ORAL)",
                "ENGLISH",
                "ENGLISH (ORAL)",
                "MATHS",
                "MATHS( ORAL)",
                "RHYNES"
            ),
            DefaultSubjects.forTemplateKey(DefaultSchoolTemplate.KEY_PRE_PRIMARY)
        )
        assertEquals(
            listOf("ENGLISH", "HINDI", "MATH", "SCIENCE", "COMPUTER", "G.K."),
            DefaultSubjects.forTemplateKey(DefaultSchoolTemplate.KEY_PRIMARY)
        )
        assertEquals(
            listOf("ENGLISH", "HINDI", "MATHS", "SCIENCE / EVS", "COMPUTER", "S.ST.", "G.K.", "SANSKRIT"),
            DefaultSubjects.forTemplateKey(DefaultSchoolTemplate.KEY_MIDDLE)
        )
    }

    @Test
    fun `acceptance_d custom class names normalize and suggest next class dynamically`() {
        val customSchoolClasses = listOf("Nursery", "LKG", "UKG", "1st", "2nd", "8th", "9th", "10th")
        assertEquals("9th", DefaultSchoolTemplate.normalizeClassName("IX", customSchoolClasses))
        assertEquals("10th", DefaultSchoolTemplate.normalizeClassName("10", customSchoolClasses))
        assertEquals("10th", DefaultSchoolTemplate.suggestNextClassName("9th", customSchoolClasses))
        assertEquals("11th", DefaultSchoolTemplate.suggestNextClassName("10th", customSchoolClasses))
        assertEquals("Playgroup", DefaultSchoolTemplate.normalizeClassName("Playgroup", listOf("Playgroup", "Nursery")))
    }

    @Test
    fun `acceptance_e real class 2nd student example matches exact totals percentage grade and attendance`() {
        val subjects = DefaultSubjects.forTemplateKey(DefaultSchoolTemplate.KEY_PRIMARY)
        val marks = mapOf(
            ("ENGLISH" to MarkComponent.T1_IA.key) to "7",
            ("ENGLISH" to MarkComponent.T1_UT.key) to "10",
            ("ENGLISH" to MarkComponent.T1_EXAM.key) to "22",
            ("ENGLISH" to MarkComponent.T2_IA.key) to "8",
            ("ENGLISH" to MarkComponent.T2_UT.key) to "14",
            ("ENGLISH" to MarkComponent.T2_EXAM.key) to "20",
            ("HINDI" to MarkComponent.T1_IA.key) to "6",
            ("HINDI" to MarkComponent.T1_UT.key) to "10",
            ("HINDI" to MarkComponent.T1_EXAM.key) to "20",
            ("HINDI" to MarkComponent.T2_IA.key) to "9",
            ("HINDI" to MarkComponent.T2_UT.key) to "17",
            ("HINDI" to MarkComponent.T2_EXAM.key) to "30",
            ("MATH" to MarkComponent.T1_IA.key) to "7",
            ("MATH" to MarkComponent.T1_UT.key) to "12",
            ("MATH" to MarkComponent.T1_EXAM.key) to "21",
            ("MATH" to MarkComponent.T2_IA.key) to "8",
            ("MATH" to MarkComponent.T2_UT.key) to "18",
            ("MATH" to MarkComponent.T2_EXAM.key) to "30",
            ("SCIENCE" to MarkComponent.T1_IA.key) to "6",
            ("SCIENCE" to MarkComponent.T1_UT.key) to "10",
            ("SCIENCE" to MarkComponent.T1_EXAM.key) to "20",
            ("SCIENCE" to MarkComponent.T2_IA.key) to "7",
            ("SCIENCE" to MarkComponent.T2_UT.key) to "15",
            ("SCIENCE" to MarkComponent.T2_EXAM.key) to "20",
            ("COMPUTER" to MarkComponent.T1_IA.key) to "6",
            ("COMPUTER" to MarkComponent.T1_UT.key) to "9",
            ("COMPUTER" to MarkComponent.T1_EXAM.key) to "20",
            ("COMPUTER" to MarkComponent.T2_IA.key) to "7",
            ("COMPUTER" to MarkComponent.T2_UT.key) to "15",
            ("COMPUTER" to MarkComponent.T2_EXAM.key) to "20",
            ("G.K." to MarkComponent.T1_IA.key) to "7",
            ("G.K." to MarkComponent.T1_UT.key) to "11",
            ("G.K." to MarkComponent.T1_EXAM.key) to "20",
            ("G.K." to MarkComponent.T2_IA.key) to "8",
            ("G.K." to MarkComponent.T2_UT.key) to "16",
            ("G.K." to MarkComponent.T2_EXAM.key) to "20"
        )

        val report = ReportCalculations.calculateStudentPerformance(
            studentId = 2L,
            subjects = subjects,
            marksBySubjectAndKey = marks,
            passPercent = 33
        )

        assertEquals(224.0, report.term1Total, 0.001)
        assertEquals(600, report.term1Max)
        assertEquals(282.0, report.term2Total, 0.001)
        assertEquals(600, report.term2Max)
        assertEquals(listOf(81.0, 92.0, 96.0, 78.0, 77.0, 82.0), report.subjects.map { it.grandTotal })
        assertEquals(506.0, report.overallTotal, 0.001)
        assertEquals(1200, report.overallMax)
        assertEquals(42.17, report.overallPercentage, 0.01)
        assertEquals("C2", report.overallGrade)
        assertEquals(ResultStatus.PASS, report.overallResult)

        val attendance = AttendanceSummary(84, 111, 82, 97)
        assertEquals("84 / 111", attendance.formatTerm1())
        assertEquals("82 / 97", attendance.formatTerm2())
        assertEquals("166 / 208", attendance.formatGrandTotal())
        assertEquals("166/208", attendance.formatCompactGrandTotal())
        assertNull(ReportCalculations.validateAttendance(84, 111))
        assertNotNull(ReportCalculations.validateAttendance(112, 111))
    }

    @Test
    fun `acceptance_f single subject below 33 percent gives FAIL even with high overall percentage`() {
        val subjects = DefaultSubjects.forTemplateKey(DefaultSchoolTemplate.KEY_PRIMARY)
        val marks = mutableMapOf<Pair<String, String>, String>()
        subjects.forEachIndexed { idx, sub ->
            if (idx == 0) {
                marks[sub to MarkComponent.T1_EXAM.key] = "20"
                marks[sub to MarkComponent.T2_EXAM.key] = "20"
            } else {
                marks[sub to MarkComponent.T1_IA.key] = "10"
                marks[sub to MarkComponent.T1_UT.key] = "30"
                marks[sub to MarkComponent.T1_EXAM.key] = "60"
                marks[sub to MarkComponent.T2_IA.key] = "10"
                marks[sub to MarkComponent.T2_UT.key] = "30"
                marks[sub to MarkComponent.T2_EXAM.key] = "60"
            }
        }

        val report = ReportCalculations.calculateStudentPerformance(3L, subjects, marks, passPercent = 33)
        assertEquals(86.67, report.overallPercentage, 0.01)
        assertEquals(ResultStatus.FAIL, report.overallResult)
    }

    @Test
    fun `grading scale boundaries include E Need improvement and division by zero is safe`() {
        assertEquals("A1", ReportCalculations.calculateGrade(91.0).grade)
        assertEquals("A1", ReportCalculations.calculateGrade(90.6).grade)
        assertEquals("A2", ReportCalculations.calculateGrade(90.4).grade)
        assertEquals("A2", ReportCalculations.calculateGrade(81.0).grade)
        assertEquals("B1", ReportCalculations.calculateGrade(71.0).grade)
        assertEquals("B2", ReportCalculations.calculateGrade(61.0).grade)
        assertEquals("C1", ReportCalculations.calculateGrade(51.0).grade)
        assertEquals("C2", ReportCalculations.calculateGrade(41.0).grade)
        assertEquals("D", ReportCalculations.calculateGrade(33.0).grade)
        val eGrade = ReportCalculations.calculateGrade(32.0)
        assertEquals("E", eGrade.grade)
        assertEquals("Need improvement", eGrade.remark)
        assertEquals("E (Need improvement)", ReportCalculations.gradingScaleRows.last().second)

        assertEquals(0.0, ReportCalculations.calculatePercentage(50.0, 0.0), 0.0001)
        assertEquals("0.00", ReportCalculations.formatPercent(ReportCalculations.calculatePercentage(0.0, 0.0)))
    }

    @Test
    fun `class ranks handle ties and UT1 single exam report handles AB`() {
        val marksHigh = mapOf(("ENGLISH" to MarkComponent.T1_EXAM.key) to "55")
        val marksMid = mapOf(("ENGLISH" to MarkComponent.T1_EXAM.key) to "40")

        val r1 = ReportCalculations.calculateStudentPerformance(1L, listOf("ENGLISH"), marksHigh)
        val r2 = ReportCalculations.calculateStudentPerformance(2L, listOf("ENGLISH"), marksHigh)
        val r3 = ReportCalculations.calculateStudentPerformance(3L, listOf("ENGLISH"), marksMid)
        val r4 = ReportCalculations.calculateStudentPerformance(4L, listOf("ENGLISH"), emptyMap())

        val ranks = ReportCalculations.calculateClassRanks(listOf(r1, r2, r3, r4))
        assertEquals(1, ranks[1L])
        assertEquals(1, ranks[2L])
        assertEquals(3, ranks[3L])
        assertNull(ranks[4L])

        val utMarks = mapOf(
            ("ENGLISH" to MarkComponent.T1_UT.key) to "24",
            ("HINDI" to MarkComponent.T1_UT.key) to "AB"
        )
        val ut1 = ReportCalculations.calculateSingleExamReport(
            examType = ExamType.UT1,
            subjects = listOf("ENGLISH", "HINDI"),
            marksBySubjectAndKey = utMarks
        )
        assertEquals(24.0, ut1.totalObtained, 0.001)
        assertEquals(60, ut1.totalMax)
        assertEquals("AB", ut1.rows[1].rawValue)
        assertEquals(0.0, ut1.rows[1].obtained, 0.001)
        assertEquals(ResultStatus.FAIL, ut1.resultStatus)
    }

    @Test
    fun `exact 33 percent pass boundaries and AB inside 10-30-60 term row behave accurately`() {
        assertEquals(ResultStatus.PASS, ReportCalculations.evaluatePassStatus(listOf(33.0 to 100.0), hasAnyMarks = true, passPercent = 33))
        assertEquals(ResultStatus.FAIL, ReportCalculations.evaluatePassStatus(listOf(32.0 to 100.0), hasAnyMarks = true, passPercent = 33))
        assertEquals(ResultStatus.PASS, ReportCalculations.evaluatePassStatus(listOf(66.0 to 200.0), hasAnyMarks = true, passPercent = 33))
        assertEquals(ResultStatus.FAIL, ReportCalculations.evaluatePassStatus(listOf(65.0 to 200.0), hasAnyMarks = true, passPercent = 33))
        assertEquals(ResultStatus.PENDING, ReportCalculations.evaluatePassStatus(listOf(0.0 to 100.0), hasAnyMarks = false, passPercent = 33))

        val marks = mapOf(
            ("HINDI" to MarkComponent.T1_IA.key) to "8",
            ("HINDI" to MarkComponent.T1_UT.key) to "AB",
            ("HINDI" to MarkComponent.T1_EXAM.key) to "42"
        )
        val perf = ReportCalculations.calculateStudentPerformance(99L, listOf("HINDI"), marks, passPercent = 33)
        val hindi = perf.subjects.first()
        assertEquals(50.0, hindi.t1Total, 0.001)
        assertEquals("C2", hindi.t1Grade)
        assertEquals("AB", ReportCalculations.displayMarkValue(hindi.t1Components[MarkComponent.T1_UT]))
        assertEquals(ResultStatus.PASS, perf.term1Result)
    }
}
