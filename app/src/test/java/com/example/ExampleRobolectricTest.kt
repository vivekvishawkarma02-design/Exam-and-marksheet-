package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CoScholasticEntity
import com.example.data.FullBackupData
import com.example.data.MarkEntity
import com.example.data.SchoolSettingsEntity
import com.example.data.StudentEntity
import com.example.data.SubjectEntity
import com.example.data.TermInfoEntity
import com.example.domain.AttendanceSummary
import com.example.domain.DefaultSubjects
import com.example.domain.MarkComponent
import com.example.domain.ReportCalculations
import com.example.domain.ReportType
import com.example.domain.SchoolClass
import com.example.domain.SlipLayoutMode
import com.example.util.BackupAndCsvManager
import com.example.util.ImageUtils
import com.example.util.PdfReportGenerator
import com.example.util.StudentReportBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches CPI-CPS Report Card`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CPI-CPS Report Card", appName)
    }

    @Test
    fun `acceptance_g report layouts render cleanly for 6, 7 and 8 subjects with CPS and CPI logos`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SchoolSettingsEntity(
            cpsMobile = "9839000001",
            cpsEmail = "cps@example.com",
            cpiMobile = "9839000002",
            cpiEmail = "cpi@example.com",
            showPhotoAndAdmOnAnnual = false
        )

        // Verify bundled official CPS & CPI logos resolve properly and cleared mode falls back to placeholder
        val cpsIdentity = ReportCalculations.resolveSchoolIdentity(settings, "2nd")
        val cpiIdentity = ReportCalculations.resolveSchoolIdentity(settings, "6th")
        val cpsBmp = ImageUtils.resolveSchoolLogoBitmap(context, cpsIdentity)
        val cpiBmp = ImageUtils.resolveSchoolLogoBitmap(context, cpiIdentity)
        assertNotNull("Bundled CPS logo should decode", cpsBmp)
        assertNotNull("Bundled CPI logo should decode", cpiBmp)
        assertTrue((cpsBmp?.width ?: 0) > 0)
        assertTrue((cpiBmp?.width ?: 0) > 0)
        assertNull(ImageUtils.resolveSchoolLogoBitmap(context, cpsIdentity.copy(logoCleared = true)))

        fun makeBundle(id: Long, roll: Int, name: String, className: String): StudentReportBundle {
            val sc = SchoolClass.fromName(className)
            val subjects = DefaultSubjects.forGroup(sc.group)
            val marks = mutableMapOf<Pair<String, String>, String>()
            subjects.forEach { sub ->
                marks[sub to MarkComponent.T1_IA.key] = "8"
                marks[sub to MarkComponent.T1_UT.key] = "24"
                marks[sub to MarkComponent.T1_EXAM.key] = "48"
                marks[sub to MarkComponent.T2_IA.key] = "9"
                marks[sub to MarkComponent.T2_UT.key] = "25"
                marks[sub to MarkComponent.T2_EXAM.key] = "50"
            }
            val perf = ReportCalculations.calculateStudentPerformance(id, subjects, marks, 33)
            return StudentReportBundle(
                student = StudentEntity(
                    id = id,
                    session = "2026-2027",
                    admNo = "ADM-$id",
                    name = name,
                    fatherName = "Father $id",
                    motherName = "Mother $id",
                    dob = "10-05-2016",
                    mobile = "983900000$roll",
                    address = "Harikaranpur Bijaura",
                    className = className,
                    section = "A",
                    rollNo = roll
                ),
                schoolIdentity = ReportCalculations.resolveSchoolIdentity(settings, className),
                subjects = subjects,
                marksBySubjectAndKey = marks,
                performance = perf,
                attendance = AttendanceSummary(84, 111, 82, 97),
                coScholasticT1 = mapOf("ART_ED" to "A1", "DISCIPLINE" to "A1"),
                coScholasticT2 = mapOf("SKILL" to "A1", "WORK_ED" to "A2", "ART_ED" to "A1", "HEALTH_PE" to "A1", "DISCIPLINE" to "A1"),
                term1Info = TermInfoEntity(id, 1, 84, 111, "Good", "", "25/10/26"),
                term2Info = TermInfoEntity(id, 2, 82, 97, "Very Good", sc.nextClassName(), "25/03/27")
            )
        }

        // Verify Portrait A4 Annual Card (595 x 842) for Nursery (7), 2nd (6), and 6th (8)
        listOf("Nursery", "2nd", "6th").forEachIndexed { idx, cls ->
            val bundle = makeBundle(idx + 1L, 1, "Student $cls", cls)
            val pages = PdfReportGenerator.renderPagesToBitmapsForVerification(
                context = context,
                settings = settings,
                reportType = ReportType.ANNUAL,
                bundles = listOf(bundle)
            )
            assertEquals("Annual card for $cls must fit on exactly 1 A4 page", 1, pages.size)
            assertEquals(PdfReportGenerator.PORTRAIT_W, pages.first().width)
            assertEquals(PdfReportGenerator.PORTRAIT_H, pages.first().height)
            pages.forEach { it.recycle() }
        }

        // Verify Landscape A4 Slips (842 x 595): 2 per page, 1 per page, and Pair (UT1 + H.Y.)
        val classBundles = listOf(
            makeBundle(10L, 1, "Aarav Verma", "2nd"),
            makeBundle(11L, 2, "आयुष यादव", "2nd")
        )

        val twoPerPage = PdfReportGenerator.renderPagesToBitmapsForVerification(
            context = context,
            settings = settings,
            reportType = ReportType.HALF_YEARLY,
            bundles = classBundles,
            slipLayoutMode = SlipLayoutMode.TWO_PER_PAGE
        )
        assertEquals(1, twoPerPage.size)
        assertEquals(PdfReportGenerator.LANDSCAPE_W, twoPerPage.first().width)
        assertEquals(PdfReportGenerator.LANDSCAPE_H, twoPerPage.first().height)
        twoPerPage.forEach { it.recycle() }

        val pairPages = PdfReportGenerator.renderPagesToBitmapsForVerification(
            context = context,
            settings = settings,
            reportType = ReportType.HALF_YEARLY,
            bundles = listOf(classBundles.first()),
            slipLayoutMode = SlipLayoutMode.PAIR_UT_HY
        )
        assertEquals(1, pairPages.size)
        assertEquals(PdfReportGenerator.LANDSCAPE_W, pairPages.first().width)
        assertEquals(PdfReportGenerator.LANDSCAPE_H, pairPages.first().height)
        pairPages.forEach { it.recycle() }

        // Verify page count and roll-number-ordered ZIP entry naming
        assertEquals(2, PdfReportGenerator.calculateExpectedPageCount(ReportType.HALF_YEARLY, 3, SlipLayoutMode.TWO_PER_PAGE))
        assertEquals(3, PdfReportGenerator.calculateExpectedPageCount(ReportType.HALF_YEARLY, 3, SlipLayoutMode.ONE_PER_PAGE))
        assertEquals(3, PdfReportGenerator.calculateExpectedPageCount(ReportType.HALF_YEARLY, 3, SlipLayoutMode.PAIR_UT_HY))
        assertEquals(3, PdfReportGenerator.calculateExpectedPageCount(ReportType.ANNUAL, 3, SlipLayoutMode.TWO_PER_PAGE))

        val zipEntry1 = PdfReportGenerator.buildBatchZipEntryName(classBundles[0].student, ReportType.ANNUAL)
        val zipEntry2 = PdfReportGenerator.buildBatchZipEntryName(classBundles[1].student, ReportType.ANNUAL)
        assertTrue(zipEntry1.startsWith("01_Aarav_Verma_2nd_A_ANNUAL.pdf"))
        assertTrue(zipEntry2.startsWith("02_") && zipEntry2.endsWith("_2nd_A_ANNUAL.pdf"))
    }

    @Test
    fun `acceptance_h backup json round trip preserves dual CPS and CPI settings and records`() {
        val original = FullBackupData(
            settings = SchoolSettingsEntity(
                cpsName = "C.P.S. SCHOOL",
                cpsAddress = "Harikaranpur Bijaura Ghazipur (U.P.)",
                cpsMobile = "9839000111",
                cpsEmail = "cps@school.in",
                cpiName = "C.P.I. SCHOOL",
                cpiAddress = "Harikaranpur Bijaura Ghazipur (U.P.)",
                cpiMobile = "9839000222",
                cpiEmail = "cpi@school.in",
                useSameLogoForBoth = true,
                currentSession = "2026-2027",
                passPercent = 33,
                showPhotoAndAdmOnAnnual = true,
                showClassRank = false
            ),
            subjects = listOf(SubjectEntity(1, 2, "MATH", 2)),
            students = listOf(
                StudentEntity(
                    id = 42L,
                    session = "2026-2027",
                    admNo = "CPI-26-999",
                    name = "Rohan Kushwaha",
                    fatherName = "Mahesh",
                    motherName = "Rekha",
                    dob = "09-11-2014",
                    mobile = "9839100003",
                    address = "Ghazipur",
                    className = "6th",
                    section = "A",
                    rollNo = 1
                )
            ),
            marks = listOf(MarkEntity(42L, "ENGLISH", "T1_IA", "9")),
            coScholastic = listOf(CoScholasticEntity(42L, 1, "SKILL", "A1")),
            termInfos = listOf(TermInfoEntity(42L, 1, 84, 111, "Good", "7th", "25/03/27"))
        )

        val json = BackupAndCsvManager.serializeBackupToJson(original)
        val restored = BackupAndCsvManager.deserializeBackupJson(json)
        assertTrue(restored.isSuccess)
        val data = restored.getOrThrow()
        assertEquals("C.P.S. SCHOOL", data.settings.cpsName)
        assertEquals("C.P.I. SCHOOL", data.settings.cpiName)
        assertEquals("cps@school.in", data.settings.cpsEmail)
        assertEquals("cpi@school.in", data.settings.cpiEmail)
        assertTrue(data.settings.useSameLogoForBoth)
        assertTrue(data.settings.showPhotoAndAdmOnAnnual)
        assertEquals(1, data.students.size)
        assertEquals("6th", data.students.first().className)
        assertEquals("9", data.marks.first().rawValue)
        assertEquals("A1", data.coScholastic.first().grade)
        assertEquals(84, data.termInfos.first().daysPresent)
    }

    @Test
    fun `csv importer normalizes legacy Roman classes and parses student rows`() {
        val csv = """
            admNo,name,fatherName,motherName,dob,mobile,address,class,section,rollNo
            CPI-26-501,Priya Singh,Ramesh Singh,Kavita Singh,01-04-2015,9839000111,Ghazipur,VI,A,5
            CPS-26-502,Amit Yadav,Suresh Yadav,Geeta Devi,12-09-2017,9839000222,Bijaura,2nd,A,6
        """.trimIndent()

        val result = BackupAndCsvManager.parseStudentsCsv(
            csvText = csv,
            defaultSession = "2026-2027",
            defaultClass = "6th",
            defaultSection = "A",
            startingRollNo = 1
        )
        assertEquals(2, result.parsedStudents.size)
        assertEquals(0, result.skippedRows)
        assertEquals("6th", result.parsedStudents[0].className)
        assertEquals("2nd", result.parsedStudents[1].className)
    }

    @Test
    fun `standardized marks csv template and validated parser support admNo and roll fallback`() {
        val students = listOf(
            StudentEntity(
                id = 101L,
                session = "2026-2027",
                admNo = "CPS-26-001",
                name = "Aarav Verma",
                fatherName = "Rajesh Verma",
                motherName = "Sunita Devi",
                dob = "10-05-2018",
                mobile = "9839000001",
                address = "Harikaranpur",
                className = "2nd",
                section = "A",
                rollNo = 1
            ),
            StudentEntity(
                id = 102L,
                session = "2026-2027",
                admNo = "CPS-26-002",
                name = "Diya Sharma",
                fatherName = "Anil Sharma",
                motherName = "Meera Sharma",
                dob = "15-08-2018",
                mobile = "9839000002",
                address = "Bijaura",
                className = "2nd",
                section = "A",
                rollNo = 2
            )
        )
        val subjects = listOf("ENGLISH", "HINDI", "MATH")
        val existingMarks = mapOf(
            101L to mapOf(("ENGLISH" to MarkComponent.T1_IA.key) to "9")
        )

        // 1. Verify standardized CSV template generation
        val template = BackupAndCsvManager.generateMarksCsvTemplate(
            session = "2026-2027",
            className = "2nd",
            section = "A",
            students = students,
            subjects = subjects,
            existingMarksByStudent = existingMarks
        )
        val templateLines = template.trim().lines()
        assertEquals(BackupAndCsvManager.MARKS_CSV_HEADER, templateLines.first())
        assertEquals(1 + students.size * subjects.size, templateLines.size)
        assertTrue(templateLines[1].contains("CPS-26-001") && templateLines[1].contains(",ENGLISH,9,"))

        // 2. Verify CSV parsing with UTF-8 BOM, AdmNo match, Roll fallback, AB handling, blank skip & invalid warnings
        val csvInput = "\uFEFF" + """
            session,class,section,rollNo,admNo,studentName,subject,T1_IA,T1_UT,T1_EXAM,T2_IA,T2_UT,T2_EXAM
            2026-2027,2nd,A,1,CPS-26-001,Aarav Verma,English,9,26,52,ab,28,55
            2026-2027,II,A,2,,Diya Sharma,MATH,8,25,,12,24,65
            2026-2027,2nd,A,99,CPS-UNKNOWN,Ghost,HINDI,8,20,40,8,20,40
        """.trimIndent()

        val parsed = BackupAndCsvManager.parseMarksCsv(
            csvText = csvInput,
            students = students,
            subjectsByGroup = mapOf(2 to subjects),
            defaultSession = "2026-2027",
            defaultClass = "2nd",
            defaultSection = "A"
        )

        // Row 1: 6 valid marks (including "ab" -> "AB", "English" -> "ENGLISH")
        // Row 2: 3 valid marks (T1_IA=8, T1_UT=25, T2_UT=24), 1 blank skipped (T1_EXAM), 2 invalid (T2_IA=12>10, T2_EXAM=65>60)
        // Row 3: 1 unmatched student issue
        assertEquals(9, parsed.validMarks.size)
        assertEquals(setOf(101L, 102L), parsed.matchedStudentIds)
        assertEquals(3, parsed.processedRowCount)
        assertEquals(1, parsed.skippedEmptyCells)
        assertEquals(3, parsed.issues.size)

        val aaravT2Ia = parsed.validMarks.first { it.studentId == 101L && it.subjectName == "ENGLISH" && it.componentKey == MarkComponent.T2_IA.key }
        assertEquals("AB", aaravT2Ia.rawValue)

        val diyaMathT1Ut = parsed.validMarks.first { it.studentId == 102L && it.subjectName == "MATH" && it.componentKey == MarkComponent.T1_UT.key }
        assertEquals("25", diyaMathT1Ut.rawValue)
    }
}
