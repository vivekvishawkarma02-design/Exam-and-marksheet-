package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.SchoolEntity
import com.example.domain.DayOfWeekCode
import com.example.domain.Phase4Validator
import com.example.domain.SubjectWeeklyProgress
import com.example.domain.TeacherProfile
import com.example.domain.TimetableCell
import com.example.domain.TimetableEngine
import com.example.util.ClassTimetablePrintBundle
import com.example.util.MasterClassSectionRow
import com.example.util.MasterTimetablePrintBundle
import com.example.util.TeacherTimetablePrintBundle
import com.example.util.TimetablePdfGenerator
import com.example.util.TimetablePrintOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase5TimetablePdfRobolectricTest {

    private lateinit var context: Context

    private val schoolAlpha = SchoolEntity(
        id = 1L,
        name = "Alpha Public School",
        address = "12 Knowledge Park, Lucknow",
        mobile = "9876543210",
        email = "alpha@school.org",
        logoBase64 = ""
    )

    private val schoolBeta = SchoolEntity(
        id = 2L,
        name = "Beta Academy",
        address = "44 Civic Road, Kanpur",
        mobile = "9876500000",
        email = "beta@school.org",
        logoBase64 = ""
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun countNonWhitePixels(bmp: Bitmap): Int {
        var count = 0
        for (y in 0 until bmp.height step 4) {
            for (x in 0 until bmp.width step 4) {
                if (bmp.getPixel(x, y) != Color.WHITE) count++
            }
        }
        return count
    }

    @Test
    fun `class timetable renders A4 Landscape and Portrait bitmaps cleanly`() {
        val struct = Phase4Validator.buildDefaultPeriodStructure("sch_alpha")
        val monP1 = struct.slotsFor(DayOfWeekCode.MON).first { it.type.countsAsTeachingPeriod }
        val cell = TimetableCell(
            id = TimetableEngine.makeCellDocId("sch_alpha", "2026-2027", "6th", "A", DayOfWeekCode.MON, monP1.id),
            schoolId = "sch_alpha",
            session = "2026-2027",
            className = "6th",
            section = "A",
            day = DayOfWeekCode.MON,
            slotId = monP1.id,
            subjectName = "Mathematics",
            teacherId = "t1",
            teacherName = "Anil Sharma"
        )
        val bundle = ClassTimetablePrintBundle(
            school = schoolAlpha,
            session = "2026-2027",
            className = "6th",
            section = "A",
            classTeacherName = "Meera Verma",
            periodStructure = struct,
            cells = listOf(cell),
            subjectProgress = listOf(SubjectWeeklyProgress("Mathematics", 1, 6))
        )

        val landscapePages = TimetablePdfGenerator.renderClassPagesToBitmapsForVerification(
            bundles = listOf(bundle),
            orientation = TimetablePrintOrientation.LANDSCAPE
        )
        assertEquals(1, landscapePages.size)
        assertEquals(TimetablePdfGenerator.LANDSCAPE_W, landscapePages[0].width)
        assertEquals(TimetablePdfGenerator.LANDSCAPE_H, landscapePages[0].height)
        assertTrue("Landscape timetable must draw visible content", countNonWhitePixels(landscapePages[0]) > 500)
        landscapePages.forEach { it.recycle() }

        val portraitPages = TimetablePdfGenerator.renderClassPagesToBitmapsForVerification(
            bundles = listOf(bundle),
            orientation = TimetablePrintOrientation.PORTRAIT
        )
        assertEquals(1, portraitPages.size)
        assertEquals(TimetablePdfGenerator.PORTRAIT_W, portraitPages[0].width)
        assertEquals(TimetablePdfGenerator.PORTRAIT_H, portraitPages[0].height)
        assertTrue("Portrait timetable must draw visible content", countNonWhitePixels(portraitPages[0]) > 500)
        portraitPages.forEach { it.recycle() }
    }

    @Test
    fun `teacher timetable renders multi-school A4 Landscape bitmap`() {
        val structAlpha = Phase4Validator.buildDefaultPeriodStructure("sch_alpha")
        val structBeta = Phase4Validator.buildDefaultPeriodStructure("sch_beta")
        val p1 = structAlpha.slotsFor(DayOfWeekCode.MON).first { it.type.countsAsTeachingPeriod }
        val p2 = structBeta.slotsFor(DayOfWeekCode.TUE).first { it.type.countsAsTeachingPeriod }
        val teacher = TeacherProfile(
            id = "t_anil",
            name = "Anil Sharma",
            schoolIds = listOf("sch_alpha", "sch_beta")
        )
        val cells = listOf(
            TimetableCell("c1", "sch_alpha", "2026-2027", "6th", "A", DayOfWeekCode.MON, p1.id, "Mathematics", teacher.id, teacher.name),
            TimetableCell("c2", "sch_beta", "2026-2027", "8th", "B", DayOfWeekCode.TUE, p2.id, "Physics", teacher.id, teacher.name)
        )
        val summary = TimetableEngine.buildTeacherTimetable(
            teacherId = teacher.id,
            fallbackTeacherName = teacher.name,
            teacherProfile = teacher,
            session = "2026-2027",
            cellsInSession = cells,
            periodStructuresBySchool = mapOf("sch_alpha" to structAlpha, "sch_beta" to structBeta),
            schoolNamesById = mapOf("sch_alpha" to schoolAlpha.name, "sch_beta" to schoolBeta.name)
        )
        val bundle = TeacherTimetablePrintBundle(
            primarySchool = schoolAlpha,
            session = "2026-2027",
            summary = summary,
            periodStructureForRows = structAlpha
        )

        val pages = TimetablePdfGenerator.renderTeacherPagesToBitmapsForVerification(listOf(bundle))
        assertEquals(1, pages.size)
        assertEquals(TimetablePdfGenerator.LANDSCAPE_W, pages[0].width)
        assertEquals(TimetablePdfGenerator.LANDSCAPE_H, pages[0].height)
        assertTrue("Teacher timetable must draw visible content", countNonWhitePixels(pages[0]) > 500)
        pages.forEach { it.recycle() }
    }

    @Test
    fun `master timetable auto-splits by class group when rows exceed one landscape page`() {
        val struct = Phase4Validator.buildDefaultPeriodStructure("sch_alpha")
        val monP1 = struct.slotsFor(DayOfWeekCode.MON).first { it.type.countsAsTeachingPeriod }

        // 24 class-sections across 3 Class Groups exceeds 1 single A4 Landscape page -> splits by Class Group!
        val rows = buildList {
            for (i in 1..8) {
                add(MasterClassSectionRow("Primary", "Class $i", "A", "A. Sharma", mapOf((DayOfWeekCode.MON to monP1.id) to TimetableCell("p_$i", "sch_alpha", "2026-2027", "Class $i", "A", DayOfWeekCode.MON, monP1.id, "Maths", "t1", "Anil Sharma"))))
            }
            for (i in 1..8) {
                add(MasterClassSectionRow("Middle", "Class $i", "B", "M. Verma", emptyMap()))
            }
            for (i in 1..8) {
                add(MasterClassSectionRow("Senior", "Class $i", "C", "R. Gupta", emptyMap()))
            }
        }

        val plans = TimetablePdfGenerator.planMasterPages(rows)
        assertEquals("Should split cleanly into 3 class-group pages", 3, plans.size)
        assertEquals("Primary", plans[0].groupTitle)
        assertEquals("Middle", plans[1].groupTitle)
        assertEquals("Senior", plans[2].groupTitle)

        val bundle = MasterTimetablePrintBundle(
            school = schoolAlpha,
            session = "2026-2027",
            periodStructure = struct,
            rows = rows
        )
        val pages = TimetablePdfGenerator.renderMasterPagesToBitmapsForVerification(bundle)
        assertEquals(3, pages.size)
        pages.forEach { bmp ->
            assertEquals(TimetablePdfGenerator.LANDSCAPE_W, bmp.width)
            assertEquals(TimetablePdfGenerator.LANDSCAPE_H, bmp.height)
            assertTrue("Master page must draw visible table content", countNonWhitePixels(bmp) > 500)
            bmp.recycle()
        }
    }

    @Test
    fun `batch export filenames and multi-class pages render cleanly`() {
        val struct = Phase4Validator.buildDefaultPeriodStructure("sch_alpha")
        val bundles = listOf("5th", "6th").map { cls ->
            ClassTimetablePrintBundle(
                school = schoolAlpha,
                session = "2026-2027",
                className = cls,
                section = "A",
                classTeacherName = "Meera Verma",
                periodStructure = struct,
                cells = emptyList(),
                subjectProgress = listOf(SubjectWeeklyProgress("Mathematics", 0, 6))
            )
        }

        assertEquals(
            "Alpha_Public_School_Timetable_5th_A_2026_2027.pdf",
            TimetablePdfGenerator.buildClassPdfFileName(listOf(bundles.first()))
        )
        assertEquals(
            "Alpha_Public_School_All_Class_Timetables_2026_2027.pdf",
            TimetablePdfGenerator.buildClassPdfFileName(bundles)
        )

        val renderedPages = TimetablePdfGenerator.renderClassPagesToBitmapsForVerification(
            bundles = bundles,
            orientation = TimetablePrintOrientation.LANDSCAPE
        )
        assertEquals(2, renderedPages.size)
        renderedPages.forEach { bmp ->
            assertEquals(TimetablePdfGenerator.LANDSCAPE_W, bmp.width)
            assertEquals(TimetablePdfGenerator.LANDSCAPE_H, bmp.height)
            assertTrue("Each batch page must draw visible timetable content", countNonWhitePixels(bmp) > 500)
            bmp.recycle()
        }
    }
}
