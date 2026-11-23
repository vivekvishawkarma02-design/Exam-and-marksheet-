package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.SchoolEntity
import com.example.data.SchoolSettingsEntity
import com.example.data.StudentEntity
import com.example.data.TermInfoEntity
import com.example.domain.AttendanceSummary
import com.example.domain.MarkComponent
import com.example.domain.ReportCalculations
import com.example.domain.ReportType
import com.example.domain.SlipLayoutMode
import com.example.domain.TeacherClassSubjectLoad
import com.example.domain.TeacherMonthlyAttendanceSummaryRow
import com.example.domain.TeacherWorkloadReportRow
import com.example.domain.WorkloadReportPeriodMode
import com.example.util.PdfReportGenerator
import com.example.util.StudentReportBundle
import com.example.util.TeacherAttendanceMonthlyPrintBundle
import com.example.util.TeacherAttendancePdfGenerator
import com.example.util.WorkloadReportPdfGenerator
import com.example.util.WorkloadReportPrintBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase7PdfRobolectricTest {

    private fun countNonWhitePixels(bmp: Bitmap, yStart: Int = 0, yEnd: Int = bmp.height): Int {
        var count = 0
        for (y in yStart until yEnd step 2) {
            for (x in 0 until bmp.width step 2) {
                if (bmp.getPixel(x, y) != Color.WHITE) count++
            }
        }
        return count
    }

    private val sampleSchool = SchoolEntity(
        id = 1L,
        name = "Sunrise Public Academy",
        address = "12 Civil Lines, Lucknow",
        mobile = "9876543210",
        email = "info@sunrise.edu",
        active = true
    )

    @Test
    fun workloadReportPdfRendersA4LandscapeSingleAndMultiPage() {
        val baseRow = TeacherWorkloadReportRow(
            teacherId = "t1",
            teacherName = "Mrs. Sunita Sharma",
            active = true,
            schoolIds = listOf("sch_1", "sch_2"),
            schoolNames = listOf("Primary Wing", "Secondary Wing"),
            classSubjectBreakdown = listOf(
                TeacherClassSubjectLoad("sch_1", "Primary Wing", "3", "A", "Maths", 6),
                TeacherClassSubjectLoad("sch_2", "Secondary Wing", "6", "B", "Science", 5)
            ),
            scheduledPeriodsPerWeek = 11,
            maxWeeklyCapacity = 30,
            freePeriodsPerWeek = 19,
            substitutionsThisWeek = 2,
            substitutionsThisMonth = 5,
            totalLoadThisWeek = 13,
            totalLoadThisMonth = 49,
            perSchoolScheduledPeriods = mapOf("sch_1" to 6, "sch_2" to 5),
            perSchoolSubsWeek = mapOf("sch_1" to 1, "sch_2" to 1),
            perSchoolSubsMonth = mapOf("sch_1" to 3, "sch_2" to 2)
        )

        val singlePages = WorkloadReportPdfGenerator.renderWorkloadPagesToBitmapsForVerification(
            WorkloadReportPrintBundle(
                headerSchool = sampleSchool,
                scopeTitle = "All Schools (2)",
                session = "2026-2027",
                referenceDateIso = "2026-11-23",
                periodMode = WorkloadReportPeriodMode.WEEK,
                rows = listOf(baseRow)
            )
        )
        assertEquals(1, singlePages.size)
        assertEquals(WorkloadReportPdfGenerator.PAGE_W, singlePages[0].width)
        assertEquals(WorkloadReportPdfGenerator.PAGE_H, singlePages[0].height)
        assertTrue("Workload page must render visible header & table", countNonWhitePixels(singlePages[0]) > 2000)
        singlePages.forEach { it.recycle() }

        val manyRows = (1..15).map { i -> baseRow.copy(teacherId = "t$i", teacherName = "Teacher $i") }
        val multiPages = WorkloadReportPdfGenerator.renderWorkloadPagesToBitmapsForVerification(
            WorkloadReportPrintBundle(
                headerSchool = sampleSchool,
                scopeTitle = "All Schools (2)",
                session = "2026-2027",
                referenceDateIso = "2026-11-23",
                periodMode = WorkloadReportPeriodMode.MONTH,
                rows = manyRows
            )
        )
        assertEquals(2, multiPages.size)
        multiPages.forEach { bmp ->
            assertTrue("Multi-page workload PDF must render visible pixels", countNonWhitePixels(bmp) > 1500)
            bmp.recycle()
        }
    }

    @Test
    fun monthlyTeacherAttendancePdfRendersA4PortraitCleanly() {
        val rows = (1..25).map { i ->
            TeacherMonthlyAttendanceSummaryRow(
                teacherId = "t$i",
                teacherName = "Staff Member $i",
                presentDays = 20,
                halfDays = 1,
                onLeaveDays = 2,
                absentDays = 1,
                unmarkedWorkingDays = 0,
                totalWorkingDays = 24
            )
        }
        val pages = TeacherAttendancePdfGenerator.renderMonthlyAttendancePagesToBitmapsForVerification(
            TeacherAttendanceMonthlyPrintBundle(
                school = sampleSchool,
                session = "2026-2027",
                monthLabel = "November 2026",
                year = 2026,
                month1Based = 11,
                rows = rows
            )
        )
        assertEquals(2, pages.size) // 22 rows per page -> 2 pages for 25 teachers
        pages.forEach { bmp ->
            assertEquals(TeacherAttendancePdfGenerator.PAGE_W, bmp.width)
            assertEquals(TeacherAttendancePdfGenerator.PAGE_H, bmp.height)
            assertTrue("Attendance PDF page must draw visible pixels", countNonWhitePixels(bmp) > 1500)
            bmp.recycle()
        }
    }

    @Test
    fun optionalClassTeacherNameSwitchAddsSignatureSubLabelOnlyWhenEnabledOnUtHyAndAnnual() {
        val student = StudentEntity(
            id = 1L,
            schoolId = 1L,
            session = "2026-2027",
            admNo = "ADM-101",
            name = "Aarav Sharma",
            fatherName = "Rajesh Sharma",
            motherName = "Meena Sharma",
            dob = "15-04-2016",
            mobile = "9876543210",
            address = "12 Civil Lines, Lucknow",
            className = "3rd",
            section = "A",
            rollNo = 1
        )
        val subjects = listOf("English", "Hindi", "Maths", "EVS")
        val marksMap = subjects.flatMap { sub ->
            listOf(
                (sub to MarkComponent.T1_IA.key) to "9",
                (sub to MarkComponent.T1_UT.key) to "26",
                (sub to MarkComponent.T1_EXAM.key) to "52",
                (sub to MarkComponent.T2_IA.key) to "9",
                (sub to MarkComponent.T2_UT.key) to "27",
                (sub to MarkComponent.T2_EXAM.key) to "54"
            )
        }.toMap()
        val perf = ReportCalculations.calculateStudentPerformance(1L, subjects, marksMap, 33)
        val settings = SchoolSettingsEntity()

        val baseBundle = StudentReportBundle(
            school = sampleSchool,
            student = student,
            subjects = subjects,
            marksBySubjectAndKey = marksMap,
            performance = perf,
            coScholasticT1 = mapOf("ART_EDUCATION" to "A1", "DISCIPLINE" to "A1"),
            coScholasticT2 = mapOf("ART_EDUCATION" to "A1", "DISCIPLINE" to "A1"),
            term1Info = TermInfoEntity(1L, 1, 88, 90, "Good progress", "", "10/10/26"),
            term2Info = TermInfoEntity(1L, 2, 90, 92, "Promoted with distinction", "4th", "25/03/27"),
            attendance = AttendanceSummary(88, 90, 90, 92),
            classTeacherName = ""
        )

        listOf(ReportType.UT1, ReportType.HALF_YEARLY, ReportType.ANNUAL).forEach { reportType ->
            val offBmp = PdfReportGenerator.renderReportFirstPageBitmapForVerification(
                settings = settings,
                reportType = reportType,
                bundle = baseBundle
            )
            val onBmp = PdfReportGenerator.renderReportFirstPageBitmapForVerification(
                settings = settings,
                reportType = reportType,
                bundle = baseBundle.copy(classTeacherName = "Mrs. Sunita Sharma")
            )
            val sigBandStart = (offBmp.height * 0.78).toInt()
            val offPixels = countNonWhitePixels(offBmp, yStart = sigBandStart, yEnd = offBmp.height)
            val onPixels = countNonWhitePixels(onBmp, yStart = sigBandStart, yEnd = onBmp.height)
            assertTrue(
                "Turning ON printClassTeacherName for $reportType should draw teacher name under CLASS TEACHER'S SIGN (off=$offPixels, on=$onPixels)",
                onPixels > offPixels
            )
            offBmp.recycle()
            onBmp.recycle()
        }
    }
}
