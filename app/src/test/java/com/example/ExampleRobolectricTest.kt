package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ClassGroupEntity
import com.example.data.CoScholasticEntity
import com.example.data.FullBackupData
import com.example.data.LegacyImportTarget
import com.example.data.MarkEntity
import com.example.data.ReportCardRepository
import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.SchoolSettingsEntity
import com.example.data.SchoolTemplateSource
import com.example.data.StudentEntity
import com.example.data.SubjectEntity
import com.example.data.TermInfoEntity
import com.example.domain.AttendanceSummary
import com.example.domain.DefaultSchoolTemplate
import com.example.domain.DefaultSubjects
import com.example.domain.MarkComponent
import com.example.domain.ReportCalculations
import com.example.domain.ReportType
import com.example.domain.SlipLayoutMode
import com.example.ui.FilterSelection
import com.example.ui.SchoolUiState
import com.example.ui.StudentAcademicStatus
import com.example.util.BackupAndCsvManager
import com.example.util.ImageUtils
import com.example.util.ParsedBackupResult
import com.example.util.PdfReportGenerator
import com.example.util.StudentReportBundle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: ReportCardRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ReportCardRepository(db.reportCardDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun makeSolidLogoBase64(bgColor: Int, stripeColor: Int): String {
        val bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bgColor)
        val p = Paint().apply { color = stripeColor }
        c.drawRect(30f, 30f, 130f, 130f, p)
        val encoded = ImageUtils.encodeBitmapToBase64Under200Kb(bmp) ?: ""
        bmp.recycle()
        return encoded
    }

    @Test
    fun `app name matches School Report Card`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("School Report Card", appName)
    }

    @Test
    fun `fresh database seeds exactly one neutral Sample School with default template`() = runBlocking {
        repo.initializeIfNeeded()
        val schools = repo.getAllSchools()
        assertEquals(1, schools.size)
        assertEquals(AppDatabase.SAMPLE_SCHOOL_NAME, schools.first().name)
        assertTrue(schools.first().active)

        val schoolId = schools.first().id
        val groups = repo.getClassGroupsForSchool(schoolId)
        val classes = repo.getClassesForSchool(schoolId)
        assertEquals(3, groups.size)
        assertEquals(11, classes.size)
        assertEquals(3, repo.getStudentsForSchool(schoolId).size)
    }

    @Test
    fun `two schools with different names and logos render reports with distinct headers and logos`() = runBlocking {
        val logoSts = makeSolidLogoBase64(Color.rgb(200, 30, 30), Color.WHITE)
        val logoAbc = makeSolidLogoBase64(Color.rgb(20, 140, 60), Color.YELLOW)

        assertTrue("STS logo must be under 200 KB", ImageUtils.estimateDecodedBytes(logoSts) < 200 * 1024)
        assertTrue("ABC logo must be under 200 KB", ImageUtils.estimateDecodedBytes(logoAbc) < 200 * 1024)
        assertNotNull(ImageUtils.resolveSchoolLogoBitmap(logoSts))
        assertNotNull(ImageUtils.resolveSchoolLogoBitmap(logoAbc))

        val stsId = repo.createSchool(
            name = "STS Public School",
            address = "12 Station Road, Lucknow",
            mobile = "9000000001",
            email = "sts@example.org",
            logoBase64 = logoSts,
            templateSource = SchoolTemplateSource.DefaultTemplate
        ).getOrThrow()

        val abcId = repo.createSchool(
            name = "ABC Academy",
            address = "45 Park Avenue, Varanasi",
            mobile = "9000000002",
            email = "abc@example.org",
            logoBase64 = logoAbc,
            templateSource = SchoolTemplateSource.CopyFromSchool(stsId)
        ).getOrThrow()

        val stsSchool = repo.getAllSchools().first { it.id == stsId }
        val abcSchool = repo.getAllSchools().first { it.id == abcId }
        assertEquals(11, repo.getClassesForSchool(abcId).size)

        val settings = SchoolSettingsEntity(showPhotoAndAdmOnAnnual = false)
        val subjects = DefaultSubjects.forTemplateKey(DefaultSchoolTemplate.KEY_PRIMARY)
        val marks = subjects.flatMap { sub ->
            listOf(
                (sub to MarkComponent.T1_IA.key) to "8",
                (sub to MarkComponent.T1_UT.key) to "25",
                (sub to MarkComponent.T1_EXAM.key) to "50",
                (sub to MarkComponent.T2_IA.key) to "9",
                (sub to MarkComponent.T2_UT.key) to "26",
                (sub to MarkComponent.T2_EXAM.key) to "52"
            )
        }.toMap()

        fun bundleFor(school: SchoolEntity, id: Long): StudentReportBundle {
            val perf = ReportCalculations.calculateStudentPerformance(id, subjects, marks, 33)
            return StudentReportBundle(
                student = StudentEntity(
                    id = id,
                    schoolId = school.id,
                    session = "2026-2027",
                    admNo = "ADM-$id",
                    name = "Riya Sharma",
                    fatherName = "Anil Sharma",
                    motherName = "Meera Sharma",
                    dob = "10-05-2016",
                    mobile = "9800000001",
                    address = "Main Road",
                    className = "2nd",
                    section = "A",
                    rollNo = 1
                ),
                school = school,
                subjects = subjects,
                marksBySubjectAndKey = marks,
                performance = perf,
                attendance = AttendanceSummary(84, 111, 82, 97),
                coScholasticT1 = mapOf("ART_ED" to "A1", "DISCIPLINE" to "A1"),
                coScholasticT2 = mapOf("SKILL" to "A1", "WORK_ED" to "A2", "ART_ED" to "A1", "HEALTH_PE" to "A1", "DISCIPLINE" to "A1"),
                term1Info = TermInfoEntity(id, 1, 84, 111, "Good", "", "25/10/26"),
                term2Info = TermInfoEntity(id, 2, 82, 97, "Very Good", "3rd", "25/03/27")
            )
        }

        val stsBundle = bundleFor(stsSchool, 1L)
        val abcBundle = bundleFor(abcSchool, 2L)

        // Verify school-specific PDF filenames (without invoking native PdfDocument which Robolectric stubs out)
        val stsFileName = PdfReportGenerator.buildPdfFileName(ReportType.ANNUAL, listOf(stsBundle))
        val abcFileName = PdfReportGenerator.buildPdfFileName(ReportType.ANNUAL, listOf(abcBundle))
        assertTrue(stsFileName.startsWith("STS_Public_School_"))
        assertTrue(abcFileName.startsWith("ABC_Academy_"))
        assertTrue(stsFileName.endsWith(".pdf") && abcFileName.endsWith(".pdf"))

        // Render bitmaps and assert both fit 1 A4 page and have distinct rendered logo/header pixels
        val stsPages = PdfReportGenerator.renderPagesToBitmapsForVerification(context, settings, ReportType.ANNUAL, listOf(stsBundle))
        val abcPages = PdfReportGenerator.renderPagesToBitmapsForVerification(context, settings, ReportType.ANNUAL, listOf(abcBundle))
        assertEquals(1, stsPages.size)
        assertEquals(1, abcPages.size)
        assertEquals(PdfReportGenerator.PORTRAIT_W, stsPages.first().width)
        assertEquals(PdfReportGenerator.PORTRAIT_H, stsPages.first().height)

        // Compare top-left logo box pixels on Annual report card (logoRect is ~39.7..99.7 in both x and y)
        var diffLogoPixels = 0
        for (x in 42..96 step 2) {
            for (y in 42..96 step 2) {
                if (stsPages.first().getPixel(x, y) != abcPages.first().getPixel(x, y)) {
                    diffLogoPixels++
                }
            }
        }
        assertTrue("Distinct school logos must render distinctly in header", diffLogoPixels > 20)
        stsPages.forEach { it.recycle() }
        abcPages.forEach { it.recycle() }

        // Verify Landscape A4 Half-Yearly 2-per-page layout still works identically
        val hyPages = PdfReportGenerator.renderPagesToBitmapsForVerification(
            context = context,
            settings = settings,
            reportType = ReportType.HALF_YEARLY,
            bundles = listOf(stsBundle, stsBundle.copy(student = stsBundle.student.copy(id = 3L, rollNo = 2, name = "आयुष यादव"))),
            slipLayoutMode = SlipLayoutMode.TWO_PER_PAGE
        )
        assertEquals(1, hyPages.size)
        assertEquals(PdfReportGenerator.LANDSCAPE_W, hyPages.first().width)
        assertEquals(PdfReportGenerator.LANDSCAPE_H, hyPages.first().height)
        hyPages.forEach { it.recycle() }
    }

    @Test
    fun `custom class names like 9th and 10th work end-to-end with custom group subjects and reports`() = runBlocking {
        val schoolId = repo.createSchool(
            name = "Modern High School",
            address = "Civil Lines",
            mobile = "9111111111",
            email = "info@modernhigh.edu",
            logoBase64 = ""
        ).getOrThrow()

        val highSchoolSubjects = listOf("ENGLISH", "HINDI", "MATHEMATICS", "SCIENCE", "SOCIAL SCIENCE", "IT")
        val group9_10Id = repo.addClassGroup(
            schoolId = schoolId,
            name = "9th – 10th",
            shortLabel = "9th–10th",
            initialSubjects = highSchoolSubjects
        ).getOrThrow()

        val class9Id = repo.addSchoolClass(schoolId, group9_10Id, "9th").getOrThrow()
        val class10Id = repo.addSchoolClass(schoolId, group9_10Id, "10th").getOrThrow()
        assertTrue(class9Id > 0L && class10Id > 0L)

        val studentId = repo.saveStudent(
            StudentEntity(
                schoolId = schoolId,
                classId = class9Id,
                session = "2026-2027",
                admNo = "MHS-09-001",
                name = "Kabir Verma",
                fatherName = "Suresh Verma",
                motherName = "Anita Verma",
                dob = "02-02-2012",
                mobile = "9123456789",
                address = "Civil Lines",
                className = "9th",
                section = "A",
                rollNo = 1
            )
        ).getOrThrow()

        val marks = mutableMapOf<Pair<String, String>, String>()
        highSchoolSubjects.forEach { sub ->
            repo.saveMarkWithPrevious(studentId, sub, MarkComponent.T1_IA.key, "9")
            repo.saveMarkWithPrevious(studentId, sub, MarkComponent.T1_UT.key, "27")
            repo.saveMarkWithPrevious(studentId, sub, MarkComponent.T1_EXAM.key, "54")
            marks[sub to MarkComponent.T1_IA.key] = "9"
            marks[sub to MarkComponent.T1_UT.key] = "27"
            marks[sub to MarkComponent.T1_EXAM.key] = "54"
        }

        val snapshot = repo.getFullBackupSnapshot(schoolId)
        val uiState = SchoolUiState(
            schools = snapshot.schools,
            classGroups = snapshot.classGroups,
            schoolClasses = snapshot.schoolClasses,
            settings = snapshot.settings,
            filter = FilterSelection(schoolId = schoolId, session = "2026-2027", className = "9th", section = "A"),
            subjects = snapshot.subjects,
            allStudents = snapshot.students,
            allMarks = snapshot.marks
        )

        assertEquals(highSchoolSubjects, uiState.subjectsForClass("9th"))
        assertEquals(1, uiState.filteredStudents.size)
        val summaries = uiState.allSessionStudentSummaries()
        assertEquals(1, summaries.size)
        assertEquals("9th", summaries.first().student.className)
        assertEquals(StudentAcademicStatus.PASS, summaries.first().status)

        val st = uiState.filteredStudents.first()
        val perf = ReportCalculations.calculateStudentPerformance(st.id, highSchoolSubjects, marks, 33)
        val bundle = StudentReportBundle(
            student = st,
            school = uiState.activeSchool,
            subjects = highSchoolSubjects,
            marksBySubjectAndKey = marks,
            performance = perf,
            attendance = AttendanceSummary(95, 100, 0, 0),
            coScholasticT1 = emptyMap(),
            coScholasticT2 = emptyMap(),
            term1Info = TermInfoEntity(st.id, 1, 95, 100, "Excellent", "10th", "15/10/26"),
            term2Info = null
        )
        val pages = PdfReportGenerator.renderPagesToBitmapsForVerification(
            context = context,
            settings = snapshot.settings,
            reportType = ReportType.ANNUAL,
            bundles = listOf(bundle)
        )
        assertEquals(1, pages.size)
        pages.forEach { it.recycle() }
    }

    @Test
    fun `deleting a school with students is blocked while deleting an empty school succeeds`() = runBlocking {
        val populatedSchoolId = repo.createSchool(
            name = "Active Campus",
            address = "Block A",
            mobile = "",
            email = "",
            logoBase64 = ""
        ).getOrThrow()

        val emptySchoolId = repo.createSchool(
            name = "Temporary Branch",
            address = "Block B",
            mobile = "",
            email = "",
            logoBase64 = ""
        ).getOrThrow()

        val cls = repo.getClassesForSchool(populatedSchoolId).first()
        repo.saveStudent(
            StudentEntity(
                schoolId = populatedSchoolId,
                classId = cls.id,
                session = "2026-2027",
                admNo = "AC-001",
                name = "Neha Gupta",
                fatherName = "Rakesh Gupta",
                motherName = "Pooja Gupta",
                dob = "01-01-2018",
                mobile = "9000011111",
                address = "Block A",
                className = cls.name,
                section = "A",
                rollNo = 1
            )
        ).getOrThrow()

        // Deleting a school that still has students MUST be blocked
        val blockedResult = repo.deleteSchool(populatedSchoolId)
        assertTrue("Deleting school with students must fail", blockedResult.isFailure)
        assertTrue(repo.getAllSchools().any { it.id == populatedSchoolId })

        // Deactivating a school with students is allowed
        assertTrue(repo.setSchoolActive(populatedSchoolId, false).isSuccess)
        assertFalse(repo.getAllSchools().first { it.id == populatedSchoolId }.active)

        // Deleting a school with 0 students MUST succeed
        val deleteEmptyResult = repo.deleteSchool(emptySchoolId)
        assertTrue("Deleting empty school must succeed", deleteEmptyResult.isSuccess)
        assertFalse(repo.getAllSchools().any { it.id == emptySchoolId })
    }

    @Test
    fun `old pre-Phase-1 backup imports cleanly into a new school without losing any data`() = runBlocking {
        repo.initializeIfNeeded()

        // Simulated v2 backup from before Phase 1 multi-school upgrade
        val legacyJson = """
            {
              "app": "Report Card Backup",
              "version": 2,
              "settings": {
                "cpsName": "Legacy Primary School",
                "cpsAddress": "Old Campus Road",
                "cpsMobile": "9839000111",
                "cpsEmail": "primary@legacy.in",
                "cpiName": "Legacy Inter School",
                "cpiAddress": "Old Campus Road",
                "cpiMobile": "9839000222",
                "cpiEmail": "inter@legacy.in",
                "currentSession": "2026-2027",
                "sessionsCsv": "2025-2026,2026-2027",
                "passPercent": 33
              },
              "subjects": [
                {"id": 1, "classGroup": 2, "name": "MORAL SCIENCE", "sortOrder": 6}
              ],
              "students": [
                {
                  "id": 77,
                  "session": "2026-2027",
                  "admNo": "LEG-26-007",
                  "name": "Vivek Kumar",
                  "fatherName": "Rajesh Kumar",
                  "motherName": "Sunita Devi",
                  "dob": "12-03-2017",
                  "mobile": "9839123456",
                  "address": "Old Campus Road",
                  "className": "II",
                  "section": "A",
                  "rollNo": 7,
                  "isSample": false
                }
              ],
              "marks": [
                {"studentId": 77, "subjectName": "ENGLISH", "componentKey": "T1_IA", "rawValue": "9"},
                {"studentId": 77, "subjectName": "ENGLISH", "componentKey": "T1_UT", "rawValue": "28"},
                {"studentId": 77, "subjectName": "ENGLISH", "componentKey": "T1_EXAM", "rawValue": "55"}
              ],
              "coScholastic": [
                {"studentId": 77, "termNo": 1, "areaCode": "ART_ED", "grade": "A1"}
              ],
              "termInfos": [
                {"studentId": 77, "termNo": 1, "daysPresent": 90, "workingDays": 100, "remarks": "Diligent", "promotedTo": "", "reportDate": "20/10/26"}
              ]
            }
        """.trimIndent()

        val parsed = BackupAndCsvManager.deserializeBackupJson(legacyJson).getOrThrow()
        assertTrue("Pre-Phase-1 backup must be detected as LegacyBackup", parsed is ParsedBackupResult.LegacyBackup)
        val payload = (parsed as ParsedBackupResult.LegacyBackup).payload
        assertEquals(2, payload.suggestedSchools.size)
        assertEquals("Legacy Primary School", payload.suggestedSchools.first().suggestedName)
        assertEquals(1, payload.students.size)
        assertEquals("2nd", payload.students.first().className)

        // Attach legacy data by creating a school from the migration dialog choice
        val targetSchoolId = repo.importLegacyBackup(
            legacy = payload,
            target = LegacyImportTarget.CreateNewSchool(
                name = "Legacy Primary School",
                address = "Old Campus Road",
                mobile = "9839000111",
                email = "primary@legacy.in",
                logoBase64 = ""
            )
        ).getOrThrow()

        val importedSnapshot = repo.getFullBackupSnapshot(targetSchoolId)
        assertEquals(1, importedSnapshot.schools.size)
        assertEquals("Legacy Primary School", importedSnapshot.schools.first().name)
        assertEquals(1, importedSnapshot.students.size)
        val importedStudent = importedSnapshot.students.first()
        assertEquals("LEG-26-007", importedStudent.admNo)
        assertEquals("Vivek Kumar", importedStudent.name)
        assertNotEquals(0L, importedStudent.classId)
        assertEquals(3, importedSnapshot.marks.size)
        assertTrue(importedSnapshot.marks.all { it.studentId == importedStudent.id })
        assertEquals(1, importedSnapshot.coScholastic.size)
        assertEquals("A1", importedSnapshot.coScholastic.first().grade)
        assertEquals(1, importedSnapshot.termInfos.size)
        assertEquals(90, importedSnapshot.termInfos.first().daysPresent)
        assertTrue(importedSnapshot.subjects.any { it.name == "MORAL SCIENCE" })
    }

    @Test
    fun `modern v3 multi-school backup round trip preserves schools logos custom classes and marks`() = runBlocking {
        val logoA = makeSolidLogoBase64(Color.BLUE, Color.WHITE)
        val original = FullBackupData(
            settings = SchoolSettingsEntity(
                currentSession = "2026-2027",
                sessionsCsv = "2025-2026,2026-2027",
                passPercent = 35,
                showPhotoAndAdmOnAnnual = true,
                showClassRank = false
            ),
            schools = listOf(
                SchoolEntity(10L, "STS School", "Main St", "9000001111", "sts@mail.com", logoA, true),
                SchoolEntity(20L, "Bright Kids", "North Rd", "9000002222", "bk@mail.com", "", false)
            ),
            classGroups = listOf(
                ClassGroupEntity(100L, 10L, "9th – 10th", "9th–10th", 0, ""),
                ClassGroupEntity(200L, 20L, "Pre-Primary", "Nur–UKG", 0, DefaultSchoolTemplate.KEY_PRE_PRIMARY)
            ),
            schoolClasses = listOf(
                SchoolClassEntity(1000L, 10L, 100L, "9th", 0),
                SchoolClassEntity(2000L, 20L, 200L, "Nursery", 0)
            ),
            subjects = listOf(
                SubjectEntity(500L, 10L, 100L, "PHYSICS", 0),
                SubjectEntity(600L, 20L, 200L, "HINDI", 0)
            ),
            students = listOf(
                StudentEntity(
                    id = 42L,
                    schoolId = 10L,
                    classId = 1000L,
                    session = "2026-2027",
                    admNo = "STS-09-01",
                    name = "Rohan Kushwaha",
                    fatherName = "Mahesh",
                    motherName = "Rekha",
                    dob = "09-11-2011",
                    mobile = "9839100003",
                    address = "Main St",
                    className = "9th",
                    section = "A",
                    rollNo = 1
                )
            ),
            marks = listOf(MarkEntity(42L, "PHYSICS", "T1_IA", "9")),
            coScholastic = listOf(CoScholasticEntity(42L, 1, "SKILL", "A1")),
            termInfos = listOf(TermInfoEntity(42L, 1, 84, 111, "Good", "10th", "25/03/27"))
        )

        val json = BackupAndCsvManager.serializeBackupToJson(original)
        val parsed = BackupAndCsvManager.deserializeBackupJson(json).getOrThrow()
        assertTrue(parsed is ParsedBackupResult.ModernBackup)
        val data = (parsed as ParsedBackupResult.ModernBackup).data

        assertEquals(2, data.schools.size)
        assertEquals("STS School", data.schools[0].name)
        assertEquals(logoA, data.schools[0].logoBase64)
        assertFalse(data.schools[1].active)
        assertEquals(35, data.settings.passPercent)
        assertTrue(data.settings.showPhotoAndAdmOnAnnual)
        assertEquals("9th", data.schoolClasses[0].name)
        assertEquals("PHYSICS", data.subjects[0].name)

        // Restore into Room and verify ID remapping preserves relationships cleanly
        repo.restoreBackup(data, replaceAll = true)
        val after = repo.getFullBackupSnapshot()
        assertEquals(2, after.schools.size)
        assertEquals(1, after.students.size)
        assertEquals("9th", after.students.first().className)
        assertEquals(1, after.marks.size)
        assertEquals("9", after.marks.first().rawValue)
    }

    @Test
    fun `standardized marks csv parser works with per-school custom classes`() {
        val classes = listOf(
            SchoolClassEntity(id = 10L, schoolId = 1L, classGroupId = 2L, name = "2nd", sortOrder = 0),
            SchoolClassEntity(id = 11L, schoolId = 1L, classGroupId = 3L, name = "9th", sortOrder = 1)
        )
        val students = listOf(
            StudentEntity(
                id = 101L,
                schoolId = 1L,
                classId = 10L,
                session = "2026-2027",
                admNo = "STS-26-001",
                name = "Aarav Verma",
                fatherName = "Rajesh Verma",
                motherName = "Sunita Devi",
                dob = "10-05-2018",
                mobile = "9839000001",
                address = "Main Street",
                className = "2nd",
                section = "A",
                rollNo = 1
            ),
            StudentEntity(
                id = 102L,
                schoolId = 1L,
                classId = 11L,
                session = "2026-2027",
                admNo = "STS-26-002",
                name = "Diya Sharma",
                fatherName = "Anil Sharma",
                motherName = "Meera Sharma",
                dob = "15-08-2011",
                mobile = "9839000002",
                address = "Park Road",
                className = "9th",
                section = "A",
                rollNo = 1
            )
        )
        val subjectsByGroup = mapOf(
            2L to listOf("ENGLISH", "HINDI", "MATH"),
            3L to listOf("PHYSICS", "CHEMISTRY")
        )

        val csvInput = "\uFEFF" + """
            session,class,section,rollNo,admNo,studentName,subject,T1_IA,T1_UT,T1_EXAM,T2_IA,T2_UT,T2_EXAM
            2026-2027,2nd,A,1,STS-26-001,Aarav Verma,English,9,26,52,ab,28,55
            2026-2027,9th,A,1,,Diya Sharma,Physics,8,25,,12,24,65
            2026-2027,2nd,A,99,STS-UNKNOWN,Ghost,HINDI,8,20,40,8,20,40
        """.trimIndent()

        val parsed = BackupAndCsvManager.parseMarksCsv(
            csvText = csvInput,
            students = students,
            schoolClasses = classes,
            subjectsByGroupId = subjectsByGroup,
            defaultSession = "2026-2027",
            defaultClass = "2nd",
            defaultSection = "A"
        )

        assertEquals(9, parsed.validMarks.size)
        assertEquals(setOf(101L, 102L), parsed.matchedStudentIds)
        assertEquals(3, parsed.processedRowCount)
        assertEquals(1, parsed.skippedEmptyCells)
        assertEquals(3, parsed.issues.size)

        val aaravT2Ia = parsed.validMarks.first { it.studentId == 101L && it.subjectName == "ENGLISH" && it.componentKey == MarkComponent.T2_IA.key }
        assertEquals("AB", aaravT2Ia.rawValue)

        val diyaPhysicsT1Ut = parsed.validMarks.first { it.studentId == 102L && it.subjectName == "PHYSICS" && it.componentKey == MarkComponent.T1_UT.key }
        assertEquals("25", diyaPhysicsT1Ut.rawValue)
    }
}
