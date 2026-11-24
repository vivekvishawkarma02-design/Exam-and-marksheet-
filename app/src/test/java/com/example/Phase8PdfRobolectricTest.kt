package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.SchoolEntity
import com.example.data.SchoolSettingsEntity
import com.example.data.StudentEntity
import com.example.data.TermInfoEntity
import com.example.domain.AttendanceSummary
import com.example.domain.CalendarHoliday
import com.example.domain.DayCalendarStatus
import com.example.domain.DayOfWeekCode
import com.example.domain.HalfWorkingDayEntry
import com.example.domain.HolidayKind
import com.example.domain.LowAttendanceStudentRow
import com.example.domain.MarkComponent
import com.example.domain.ReportCalculations
import com.example.domain.ReportType
import com.example.domain.SchoolCalendarConfig
import com.example.domain.StudentAttendanceEngine
import com.example.domain.StudentAttendanceEntry
import com.example.domain.StudentAttendanceSheet
import com.example.domain.StudentAttendanceStatus
import com.example.domain.StudentRangeAttendanceSummaryRow
import com.example.util.DailyAttendancePdfBundle
import com.example.util.LowAttendancePdfBundle
import com.example.util.MonthlyRegisterPdfBundle
import com.example.util.PdfReportGenerator
import com.example.util.StudentAttendancePdfGenerator
import com.example.util.StudentReportBundle
import com.example.util.StudentSummaryRangePdfBundle
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
class Phase8PdfRobolectricTest {

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

    private fun makeStudents(count: Int): List<Pair<String, StudentEntity>> = (1..count).map { i ->
        "stu_$i" to StudentEntity(
            id = i.toLong(),
            schoolId = 1L,
            session = "2026-2027",
            admNo = "ADM-%03d".format(i),
            name = "Student Full Name %02d".format(i),
            fatherName = "Father $i",
            motherName = "Mother $i",
            dob = "15-04-2016",
            mobile = "98765432%02d".format(i % 100),
            address = "Lucknow",
            className = "3rd",
            section = "A",
            rollNo = i
        )
    }

    @Test
    fun monthlyRegisterPdfHandles31DaysAnd52StudentsAcrossMultipleLandscapePages() {
        val students = makeStudents(52)
        val cal = SchoolCalendarConfig(
            id = "cal_1",
            schoolId = "sch_1",
            session = "2026-2027",
            workingDays = listOf(
                DayOfWeekCode.MON, DayOfWeekCode.TUE, DayOfWeekCode.WED,
                DayOfWeekCode.THU, DayOfWeekCode.FRI, DayOfWeekCode.SAT
            ),
            holidays = listOf(
                CalendarHoliday("h1", "Republic Day", "2027-01-26", "2027-01-26", HolidayKind.HOLIDAY)
            ),
            halfWorkingDays = listOf(
                HalfWorkingDayEntry("hd1", "2027-01-15", "PTM Half Day")
            ),
            term1StartDate = "2026-04-01",
            term1EndDate = "2026-09-30",
            term2StartDate = "2026-10-01",
            term2EndDate = "2027-03-31"
        )

        // Populate sheets across a full 31-day month (January 2027)
        val statuses = StudentAttendanceStatus.entries
        val sheetsByDate = (1..31).associate { day ->
            val iso = "2027-01-%02d".format(day)
            val entries = students.associateIndexed { idx, (docId, _) ->
                docId to StudentAttendanceEntry(statuses[(idx + day) % statuses.size])
            }
            iso to StudentAttendanceSheet(
                id = "satt_$iso",
                schoolId = "sch_1",
                session = "2026-2027",
                className = "3rd",
                section = "A",
                date = iso,
                entries = entries
            ).withRecomputedCounts()
        }

        val pages = StudentAttendancePdfGenerator.renderMonthlyRegisterPagesToBitmapsForVerification(
            MonthlyRegisterPdfBundle(
                school = sampleSchool,
                session = "2026-2027",
                className = "3rd",
                section = "A",
                year = 2027,
                month1Based = 1,
                monthLabel = "January 2027",
                classTeacherName = "Mrs. Sunita Sharma",
                calendar = cal,
                students = students,
                sheetsByDate = sheetsByDate
            )
        )

        // 52 students at 24 students per page -> 3 pages
        assertEquals(3, pages.size)
        pages.forEachIndexed { idx, bmp ->
            assertEquals("Page $idx must be A4 Landscape width", StudentAttendancePdfGenerator.LANDSCAPE_W, bmp.width)
            assertEquals("Page $idx must be A4 Landscape height", StudentAttendancePdfGenerator.LANDSCAPE_H, bmp.height)
            assertTrue("Page $idx must render visible header band", countNonWhitePixels(bmp, 0, 120) > 400)
            assertTrue("Page $idx must render visible table grid & codes", countNonWhitePixels(bmp) > 3000)
            bmp.recycle()
        }
    }

    @Test
    fun dailySummaryAndLowAttendancePdfsRenderA4PortraitCleanly() {
        val students = makeStudents(18)
        val entries = students.associateIndexed { idx, (docId, _) ->
            val st = when (idx % 5) {
                0 -> StudentAttendanceStatus.PRESENT
                1 -> StudentAttendanceStatus.ABSENT
                2 -> StudentAttendanceStatus.LEAVE
                3 -> StudentAttendanceStatus.LATE
                else -> StudentAttendanceStatus.HALF_DAY
            }
            docId to StudentAttendanceEntry(st, if (st == StudentAttendanceStatus.LEAVE) "Family event" else "")
        }
        val sheet = StudentAttendanceSheet(
            id = "satt_d1",
            schoolId = "sch_1",
            session = "2026-2027",
            className = "3rd",
            section = "A",
            date = "2026-08-14",
            entries = entries
        ).withRecomputedCounts()

        val dailyPages = StudentAttendancePdfGenerator.renderDailyPagesToBitmapsForVerification(
            DailyAttendancePdfBundle(
                school = sampleSchool,
                session = "2026-2027",
                className = "3rd",
                section = "A",
                dateIso = "2026-08-14",
                classTeacherName = "Mrs. Sunita Sharma",
                dayStatus = DayCalendarStatus.Working(isHalfWorkingDay = true, halfDayName = "PTM Half Day"),
                students = students,
                sheet = sheet
            )
        )
        assertTrue(dailyPages.isNotEmpty())
        dailyPages.forEach { bmp ->
            assertEquals(StudentAttendancePdfGenerator.PORTRAIT_W, bmp.width)
            assertEquals(StudentAttendancePdfGenerator.PORTRAIT_H, bmp.height)
            assertTrue(countNonWhitePixels(bmp) > 2000)
            bmp.recycle()
        }

        val summaryRows = students.map { (docId, st) ->
            StudentRangeAttendanceSummaryRow(
                studentDocId = docId,
                student = st,
                presentCount = 80,
                lateCount = 4,
                halfDayCount = 2,
                leaveCount = 3,
                absentCount = 11,
                weightedDaysPresent = 85.0,
                totalWorkingDays = 100
            )
        }
        val summaryPages = StudentAttendancePdfGenerator.renderStudentSummaryPagesToBitmapsForVerification(
            StudentSummaryRangePdfBundle(
                school = sampleSchool,
                session = "2026-2027",
                scopeTitle = "Class 3rd-A",
                startDateIso = "2026-04-01",
                endDateIso = "2026-09-30",
                calendarWorkingDays = 100,
                rows = summaryRows
            )
        )
        assertTrue(summaryPages.isNotEmpty())
        summaryPages.forEach { bmp ->
            assertEquals(StudentAttendancePdfGenerator.PORTRAIT_W, bmp.width)
            assertEquals(StudentAttendancePdfGenerator.PORTRAIT_H, bmp.height)
            assertTrue(countNonWhitePixels(bmp) > 2000)
            bmp.recycle()
        }

        val lowRows = students.take(6).map { (docId, st) ->
            LowAttendanceStudentRow(
                schoolDocId = "sch_1",
                schoolName = sampleSchool.name,
                studentDocId = docId,
                student = st,
                weightedDaysPresent = 62.5,
                totalWorkingDays = 100,
                attendancePercent = 62.5,
                absentCount = 30,
                leaveCount = 7
            )
        }
        val lowPages = StudentAttendancePdfGenerator.renderLowAttendancePagesToBitmapsForVerification(
            LowAttendancePdfBundle(
                school = sampleSchool,
                session = "2026-2027",
                scopeTitle = "Whole School",
                startDateIso = "2026-04-01",
                endDateIso = "2026-09-30",
                thresholdPercent = 75.0,
                rows = lowRows
            )
        )
        assertTrue(lowPages.isNotEmpty())
        lowPages.forEach { bmp ->
            assertEquals(StudentAttendancePdfGenerator.PORTRAIT_W, bmp.width)
            assertEquals(StudentAttendancePdfGenerator.PORTRAIT_H, bmp.height)
            assertTrue(countNonWhitePixels(bmp) > 1500)
            bmp.recycle()
        }
    }

    @Test
    fun reportCardRemainsPixelIdenticalWhenAutoFillSwitchIsOff() {
        val student = makeStudents(1).first().second
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
        val perf = ReportCalculations.calculateStudentPerformance(student.id, subjects, marksMap, 33)
        val settings = SchoolSettingsEntity()
        val t1Stored = TermInfoEntity(student.id, 1, 84, 111, "Good progress", "", "10/10/26")
        val t2Stored = TermInfoEntity(student.id, 2, 82, 97, "Promoted", "4th", "25/03/27")

        val baselineAttendance = AttendanceSummary(84, 111, 82, 97)
        val cal = SchoolCalendarConfig(
            id = "cal_1",
            schoolId = "sch_1",
            session = "2026-2027",
            term1StartDate = "2026-04-01",
            term1EndDate = "2026-09-30",
            term2StartDate = "2026-10-01",
            term2EndDate = "2027-03-31"
        )
        val dailyT1 = StudentAttendanceEngine.computeTermAttendance("stu_1", "sch_1", "2026-2027", 1, cal, emptyList())
        val dailyT2 = StudentAttendanceEngine.computeTermAttendance("stu_1", "sch_1", "2026-2027", 2, cal, emptyList())

        // Switch OFF -> must yield identical AttendanceSummary (84/111 + 82/97 = 166/208)
        val offSummary = StudentAttendanceEngine.buildEffectiveAttendanceSummary(
            autoFillSwitchOn = false,
            term1Stored = t1Stored,
            term1ManualFlag = null,
            term2Stored = t2Stored,
            term2ManualFlag = null,
            term1Daily = dailyT1,
            term2Daily = dailyT2
        )
        assertEquals(baselineAttendance, offSummary)
        assertEquals("166/208", offSummary.formatCompactGrandTotal())

        val baseBundle = StudentReportBundle(
            school = sampleSchool,
            student = student,
            subjects = subjects,
            marksBySubjectAndKey = marksMap,
            performance = perf,
            coScholasticT1 = mapOf("ART_EDUCATION" to "A1", "DISCIPLINE" to "A1"),
            coScholasticT2 = mapOf("ART_EDUCATION" to "A1", "DISCIPLINE" to "A1"),
            term1Info = t1Stored,
            term2Info = t2Stored,
            attendance = baselineAttendance
        )
        val phase8OffBundle = baseBundle.copy(attendance = offSummary)

        val bmpBaseline = PdfReportGenerator.renderReportFirstPageBitmapForVerification(settings, ReportType.ANNUAL, baseBundle)
        val bmpPhase8Off = PdfReportGenerator.renderReportFirstPageBitmapForVerification(settings, ReportType.ANNUAL, phase8OffBundle)
        assertTrue("Annual report card must be pixel-identical when auto-fill switch is OFF", bmpBaseline.sameAs(bmpPhase8Off))
        bmpBaseline.recycle()
        bmpPhase8Off.recycle()
    }

    private inline fun <T, K, V> List<T>.associateIndexed(transform: (Int, T) -> Pair<K, V>): Map<K, V> {
        val out = LinkedHashMap<K, V>(size)
        forEachIndexed { idx, item ->
            val (k, v) = transform(idx, item)
            out[k] = v
        }
        return out
    }
}
