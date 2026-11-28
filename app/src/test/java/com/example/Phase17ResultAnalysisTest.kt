package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.MarkEntity
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.domain.AccessLevel
import com.example.domain.AppModule
import com.example.domain.ClassComparisonMode
import com.example.domain.ClassSectionAssignment
import com.example.domain.ExamType
import com.example.domain.MarkComponent
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgRole
import com.example.domain.Phase17ResultAnalysisEngine
import com.example.domain.ProgressTrend
import com.example.domain.ReportCalculations
import com.example.domain.ResultAnalysisAccessEvaluator
import com.example.domain.ResultAnalysisExamFilter
import com.example.domain.ResultStatus
import com.example.domain.SubjectTeacherAssignment
import com.example.domain.TeacherAccessScope
import com.example.domain.TeacherProfile
import com.example.domain.TieBreakRule
import com.example.domain.TopperScope
import com.example.domain.WeakFilterMode
import com.example.util.Phase17PdfGenerator
import com.example.util.ResultAnalysisPdfRequest
import com.example.util.ResultAnalysisPdfType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * Comprehensive Phase 17 Result Analysis unit & Robolectric tests:
 * 1. Exact manual math verification of Class Overview (students appeared, pass/fail, pass %,
 *    class average %, highest & lowest, grade distribution A1..E).
 * 2. Exact parity with ReportCalculations (reuse of 33% pass rule per subject and A1..E grades;
 *    proving existing ReportCalculations outputs remain 100% untouched).
 * 3. Weak students list (failing 1+ subject OR below editable threshold %, default 40%).
 * 4. Toppers list (Per Section, Per Class, School-wide) and all 3 editable tie-break rules
 *    (1,2,2,4 include all tied vs 1,2,2,3 dense vs 1,2,2,4 strict top N).
 * 5. Subject-wise analysis (weakest-first ordering, pass %, avg %, high/low, fail count, assigned teacher).
 * 6. Student progress over the year (UT-I, UT-II, Half-Yearly, Yearly side-by-side + trend ↑/↓/→ + manual guardian message).
 * 7. Class & section comparison + Missing marks warning and list.
 * 8. Access control & Teacher scope isolation (missing key = no access; Owner/Admin full + CSV;
 *    Teacher sees ONLY assigned classes, sections, and subjects; unassigned teacher sees empty data).
 * 9. Offline A4 PDF generation & bitmap rendering for all 5 Result Analysis PDF types.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase17ResultAnalysisTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun samplePngBase64(color: Int): String {
        val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 90, baos)
        bmp.recycle()
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun makeSchool(): SchoolEntity = SchoolEntity(
        id = 1L,
        name = "DEMO MODEL SCHOOL",
        address = "123 Station Road, Demo Nagar",
        mobile = "9876543210",
        email = "office@demoschool.example",
        logoBase64 = samplePngBase64(Color.rgb(22, 49, 92)),
        active = true
    )

    private fun makeStudent(
        id: Long,
        name: String,
        className: String = "5th",
        section: String = "A",
        rollNo: Int = id.toInt(),
        session: String = "2026-2027"
    ): StudentEntity = StudentEntity(
        id = id,
        schoolId = 1L,
        classId = 10L,
        session = session,
        admNo = "ADM-$id",
        name = name,
        fatherName = "Parent $id",
        motherName = "Mother $id",
        dob = "2015-05-10",
        mobile = "900000000$id",
        address = "Lucknow",
        photoBase64 = "",
        className = className,
        section = section,
        rollNo = rollNo
    )

    private fun makeTerm1Marks(
        studentId: Long,
        subject: String,
        ia: String,
        ut1: String,
        hyExam: String
    ): List<MarkEntity> = listOf(
        MarkEntity(studentId = studentId, subjectName = subject, componentKey = MarkComponent.T1_IA.key, rawValue = ia),
        MarkEntity(studentId = studentId, subjectName = subject, componentKey = MarkComponent.T1_UT.key, rawValue = ut1),
        MarkEntity(studentId = studentId, subjectName = subject, componentKey = MarkComponent.T1_EXAM.key, rawValue = hyExam)
    )

    private fun makeTerm2Marks(
        studentId: Long,
        subject: String,
        ia: String,
        ut2: String,
        yrExam: String
    ): List<MarkEntity> = listOf(
        MarkEntity(studentId = studentId, subjectName = subject, componentKey = MarkComponent.T2_IA.key, rawValue = ia),
        MarkEntity(studentId = studentId, subjectName = subject, componentKey = MarkComponent.T2_UT.key, rawValue = ut2),
        MarkEntity(studentId = studentId, subjectName = subject, componentKey = MarkComponent.T2_EXAM.key, rawValue = yrExam)
    )

    private fun ownerMember(): MemberAccessProfile = MemberAccessProfile(
        uid = "uid_owner",
        role = OrgRole.OWNER,
        status = MemberStatus.ACTIVE,
        allSchools = true,
        allowedSchoolIds = emptyList(),
        permissions = MemberPermissions.presetFor(OrgRole.OWNER)
    )

    // -------------------------------------------------------------------------
    // 1. ACCESS CONTROL & TEACHER SCOPE ISOLATION
    // -------------------------------------------------------------------------
    @Test
    fun accessAndTeacherScope_enforcesMissingKeyDenialAndStrictTeacherIsolation() {
        // 1. Missing 'resultAnalysis' key in Firestore map -> AccessLevel.NONE -> denied
        val permsWithoutKey = MemberPermissions.fromMap(mapOf("students" to "view", "marks" to "view"))
        assertEquals(AccessLevel.NONE, permsWithoutKey.levelFor(AppModule.RESULT_ANALYSIS))

        val memberMissingKey = MemberAccessProfile(
            uid = "u_no_key",
            role = OrgRole.OFFICE_OPERATOR,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = permsWithoutKey
        )
        assertFalse(ResultAnalysisAccessEvaluator.canViewResultAnalysis(memberMissingKey, "sch_1"))
        assertFalse(ResultAnalysisAccessEvaluator.canExportCsv(memberMissingKey))

        // 2. Owner and Admin get VIEW preset & CSV export
        val owner = ownerMember()
        val admin = MemberAccessProfile(
            uid = "u_admin",
            role = OrgRole.ADMIN,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions.presetFor(OrgRole.ADMIN)
        )
        assertTrue(ResultAnalysisAccessEvaluator.canViewResultAnalysis(owner, "sch_1"))
        assertTrue(ResultAnalysisAccessEvaluator.canExportCsv(owner))
        assertTrue(ResultAnalysisAccessEvaluator.canViewResultAnalysis(admin, "sch_1"))
        assertTrue(ResultAnalysisAccessEvaluator.canExportCsv(admin))

        // 3. Teacher with resultAnalysis = VIEW is restricted to ONLY assigned class-section-subject
        val teacherMember = MemberAccessProfile(
            uid = "u_teacher",
            role = OrgRole.TEACHER,
            status = MemberStatus.ACTIVE,
            allSchools = false,
            allowedSchoolIds = listOf("sch_1"),
            permissions = MemberPermissions(resultAnalysis = AccessLevel.VIEW)
        )
        assertTrue(ResultAnalysisAccessEvaluator.canViewResultAnalysis(teacherMember, "sch_1"))
        assertFalse(ResultAnalysisAccessEvaluator.canViewResultAnalysis(teacherMember, "sch_2"))
        assertFalse(ResultAnalysisAccessEvaluator.canExportCsv(teacherMember))

        val s5a = makeStudent(1L, "Aarav 5A", className = "5th", section = "A")
        val s5b = makeStudent(2L, "Bhavya 5B", className = "5th", section = "B")
        val s6a = makeStudent(3L, "Chirag 6A", className = "6th", section = "A")
        val allStudents = listOf(s5a, s5b, s6a)

        val allMarks = buildList {
            addAll(makeTerm1Marks(1L, "Maths", "9", "27", "55"))
            addAll(makeTerm1Marks(1L, "English", "8", "24", "50"))
            addAll(makeTerm1Marks(2L, "Maths", "7", "21", "45"))
            addAll(makeTerm1Marks(3L, "Maths", "10", "28", "58"))
        }
        val subjectsByClass = mapOf(
            "5th" to listOf("Maths", "English"),
            "6th" to listOf("Maths", "Science")
        )

        // Unassigned teacher (empty TeacherAccessScope) sees 0 classes, 0 students, 0 marks
        val emptyScope = TeacherAccessScope(uid = "u_teacher", assignedClassSectionSubjects = emptyList())
        val unassignedScoped = ResultAnalysisAccessEvaluator.scopeDataForMember(
            member = teacherMember,
            teacherScope = emptyScope,
            schoolDocId = "sch_1",
            session = "2026-2027",
            schoolStableId = 1L,
            allClasses = listOf("5th", "6th"),
            subjectsByClass = subjectsByClass,
            allStudents = allStudents,
            allMarks = allMarks
        )
        assertTrue(unassignedScoped.isTeacherRestricted)
        assertTrue(unassignedScoped.allowedClasses.isEmpty())
        assertTrue(unassignedScoped.scopedStudents.isEmpty())
        assertTrue(unassignedScoped.scopedMarks.isEmpty())

        // Teacher assigned ONLY to sch_1|5th|A|Maths
        val maths5aScope = TeacherAccessScope(
            uid = "u_teacher",
            assignedClassSectionSubjects = listOf("sch_1|5th|A|Maths")
        )
        val teacherScoped = ResultAnalysisAccessEvaluator.scopeDataForMember(
            member = teacherMember,
            teacherScope = maths5aScope,
            schoolDocId = "sch_1",
            session = "2026-2027",
            schoolStableId = 1L,
            allClasses = listOf("5th", "6th"),
            subjectsByClass = subjectsByClass,
            allStudents = allStudents,
            allMarks = allMarks
        )
        assertTrue(teacherScoped.isTeacherRestricted)
        assertEquals(listOf("5th"), teacherScoped.allowedClasses)
        assertEquals(listOf("A"), teacherScoped.allowedSectionsByClass["5th"])
        assertEquals(listOf("Maths"), teacherScoped.subjectsByClassSection["5th" to "A"])
        assertEquals(listOf(1L), teacherScoped.scopedStudents.map { it.id })
        assertTrue(teacherScoped.scopedMarks.all { it.studentId == 1L && it.subjectName == "Maths" })
    }

    // -------------------------------------------------------------------------
    // 2. EXACT CLASS OVERVIEW, GRADE BARS, PASS/FAIL BOUNDARY & WEAK STUDENTS
    // -------------------------------------------------------------------------
    @Test
    fun classOverviewAndWeakStudents_matchesManualCalculationsAndReportCalculationsParity() {
        val subjects = listOf("Maths", "English")
        // 5 students in 5th-A + 1 student with no marks (pending):
        // Stu 1 (A1 topper): Maths 95/100 (9+28+58), English 91/100 (9+27+55) => 186/200 = 93.0% (A1, PASS)
        // Stu 2 (A2 pass):   Maths 82/100 (8+24+50), English 84/100 (8+26+50) => 166/200 = 83.0% (A2, PASS)
        // Stu 3 (Boundary 33% pass): Maths 33/100 (3+10+20), English 35/100 (5+10+20) => 68/200 = 34.0% (D, PASS, < 40% weak)
        // Stu 4 (High avg but fails 1 subject at 32%): Maths 90/100 (10+25+55), English 32/100 (2+10+20) => 122/200 = 61.0% (B2, FAIL in English)
        // Stu 5 (Fails both <33%): Maths 25/100 (5+8+12), English 29/100 (4+10+15) => 54/200 = 27.0% (E, FAIL in Maths & English)
        // Stu 6 (Pending - no marks entered)
        val students = listOf(
            makeStudent(1L, "Aarav", rollNo = 1),
            makeStudent(2L, "Diya", rollNo = 2),
            makeStudent(3L, "Kabir", rollNo = 3),
            makeStudent(4L, "Meera", rollNo = 4),
            makeStudent(5L, "Rohan", rollNo = 5),
            makeStudent(6L, "PendingKid", rollNo = 6)
        )
        val marks = buildList {
            addAll(makeTerm1Marks(1L, "Maths", "9", "28", "58"))
            addAll(makeTerm1Marks(1L, "English", "9", "27", "55"))

            addAll(makeTerm1Marks(2L, "Maths", "8", "24", "50"))
            addAll(makeTerm1Marks(2L, "English", "8", "26", "50"))

            addAll(makeTerm1Marks(3L, "Maths", "3", "10", "20"))
            addAll(makeTerm1Marks(3L, "English", "5", "10", "20"))

            addAll(makeTerm1Marks(4L, "Maths", "10", "25", "55"))
            addAll(makeTerm1Marks(4L, "English", "2", "10", "20"))

            addAll(makeTerm1Marks(5L, "Maths", "5", "8", "12"))
            addAll(makeTerm1Marks(5L, "English", "4", "10", "15"))
        }

        val scoped = ResultAnalysisAccessEvaluator.scopeDataForMember(
            member = ownerMember(),
            teacherScope = null,
            schoolDocId = "sch_1",
            session = "2026-2027",
            schoolStableId = 1L,
            allClasses = listOf("5th"),
            subjectsByClass = mapOf("5th" to subjects),
            allStudents = students,
            allMarks = marks
        )

        val bundle = Phase17ResultAnalysisEngine.computeBundle(
            scopedData = scoped,
            schoolDocId = "sch_1",
            selectedClass = "5th",
            selectedSection = "A",
            examFilter = ResultAnalysisExamFilter.HALF_YEARLY,
            passPercent = 33,
            weakThresholdPercent = 40,
            weakFilterMode = WeakFilterMode.FAIL_OR_BELOW_THRESHOLD
        )

        // Verify parity with ReportCalculations for each student
        for (eval in bundle.evaluatedStudents) {
            val stuMarksMap = marks.filter { it.studentId == eval.student.id }
                .associate { (it.subjectName to it.componentKey) to it.rawValue }
            val reportPerf = ReportCalculations.calculateStudentPerformance(
                studentId = eval.student.id,
                subjects = subjects,
                marksBySubjectAndKey = stuMarksMap,
                passPercent = 33
            )
            assertEquals(reportPerf.term1Total, eval.totalObtained, 0.001)
            assertEquals(reportPerf.term1Percentage, eval.percentage, 0.001)
            assertEquals(reportPerf.term1Grade, eval.grade)
            assertEquals(reportPerf.term1Result, eval.resultStatus)
        }

        // Verify Class Overview numbers against manual math:
        // Total = 6, Appeared = 5, Pending = 1, Pass = 3 (Aarav, Diya, Kabir), Fail = 2 (Meera, Rohan)
        // Pass % = 3 / 5 = 60.0%
        // Average % = (93.0 + 83.0 + 34.0 + 61.0 + 27.0) / 5 = 298.0 / 5 = 59.6%
        val ov = bundle.overview
        assertEquals(6, ov.totalStudents)
        assertEquals(5, ov.appearedCount)
        assertEquals(1, ov.pendingCount)
        assertEquals(3, ov.passCount)
        assertEquals(2, ov.failCount)
        assertEquals(60.0, ov.passPercent, 0.01)
        assertEquals(59.6, ov.averagePercent, 0.01)
        assertEquals("Aarav", ov.highestStudentName)
        assertEquals(93.0, ov.highestPercent, 0.01)
        assertEquals("Rohan", ov.lowestStudentName)
        assertEquals(27.0, ov.lowestPercent, 0.01)

        // Grade distribution A1..E counts: A1=1, A2=1, B1=0, B2=1 (Meera 61%), C1=0, C2=0, D=1 (Kabir 34%), E=1 (Rohan 27%)
        val gradeMap = ov.gradeDistribution.associate { it.grade to it.count }
        assertEquals(1, gradeMap["A1"])
        assertEquals(1, gradeMap["A2"])
        assertEquals(0, gradeMap["B1"])
        assertEquals(1, gradeMap["B2"])
        assertEquals(0, gradeMap["C1"])
        assertEquals(0, gradeMap["C2"])
        assertEquals(1, gradeMap["D"])
        assertEquals(1, gradeMap["E"])

        // Weak Students (default 40%, FAIL_OR_BELOW_THRESHOLD):
        // Should include Rohan (2 failed: Maths, English), Meera (1 failed: English despite 61% avg), Kabir (0 failed, but 34% < 40%)
        assertEquals(listOf("Rohan", "Meera", "Kabir"), bundle.weakStudents.map { it.student.name })
        assertEquals(listOf("Maths", "English"), bundle.weakStudents[0].failedSubjects)
        assertEquals(listOf("English"), bundle.weakStudents[1].failedSubjects)
        assertTrue(bundle.weakStudents[2].failedSubjects.isEmpty())
        assertEquals(listOf("Maths", "English"), bundle.weakStudents[2].lowScoreSubjects)

        // Missing marks detection (PendingKid has 2 subjects * 3 T1 components = 6 missing entries)
        assertTrue(bundle.hasIncompleteMarks)
        assertEquals(36, bundle.totalExpectedEntries)
        assertEquals(30, bundle.enteredEntries)
        assertEquals(6, bundle.missingEntries)
        assertEquals(2, bundle.missingRows.size)
    }

    // -------------------------------------------------------------------------
    // 3. TOPPERS & TIE-BREAKING RULES + SUBJECT ANALYSIS WEAKEST-FIRST
    // -------------------------------------------------------------------------
    @Test
    fun toppersAndSubjects_handlesTiesConsistentlyAndSortsWeakestSubjectFirst() {
        val s1 = makeStudent(1L, "TopOne", className = "5th", section = "A", rollNo = 1)
        val s2 = makeStudent(2L, "TieTwoA", className = "5th", section = "A", rollNo = 2)
        val s3 = makeStudent(3L, "TieTwoB", className = "5th", section = "B", rollNo = 1)
        val s4 = makeStudent(4L, "FourthPlace", className = "5th", section = "B", rollNo = 2)

        // UT1 exam (max 30 per subject, 2 subjects = 60 max):
        // s1: Maths 29, Science 25 => 54/60 = 90.0%
        // s2: Maths 24, Science 24 => 48/60 = 80.0% (Tied for 2nd)
        // s3: Maths 26, Science 22 => 48/60 = 80.0% (Tied for 2nd)
        // s4: Maths 21, Science 9  => 30/60 = 50.0% (Science fails: 9/30 = 30% < 33%)
        val marks = listOf(
            MarkEntity(studentId = 1L, subjectName = "Maths", componentKey = MarkComponent.T1_UT.key, rawValue = "29"),
            MarkEntity(studentId = 1L, subjectName = "Science", componentKey = MarkComponent.T1_UT.key, rawValue = "25"),
            MarkEntity(studentId = 2L, subjectName = "Maths", componentKey = MarkComponent.T1_UT.key, rawValue = "24"),
            MarkEntity(studentId = 2L, subjectName = "Science", componentKey = MarkComponent.T1_UT.key, rawValue = "24"),
            MarkEntity(studentId = 3L, subjectName = "Maths", componentKey = MarkComponent.T1_UT.key, rawValue = "26"),
            MarkEntity(studentId = 3L, subjectName = "Science", componentKey = MarkComponent.T1_UT.key, rawValue = "22"),
            MarkEntity(studentId = 4L, subjectName = "Maths", componentKey = MarkComponent.T1_UT.key, rawValue = "21"),
            MarkEntity(studentId = 4L, subjectName = "Science", componentKey = MarkComponent.T1_UT.key, rawValue = "9")
        )

        val scoped = ResultAnalysisAccessEvaluator.scopeDataForMember(
            member = ownerMember(),
            teacherScope = null,
            schoolDocId = "sch_1",
            session = "2026-2027",
            schoolStableId = 1L,
            allClasses = listOf("5th"),
            subjectsByClass = mapOf("5th" to listOf("Maths", "Science")),
            allStudents = listOf(s1, s2, s3, s4),
            allMarks = marks
        )

        val teachers = listOf(
            TeacherProfile(id = "t1", name = "Mr. Sinha"),
            TeacherProfile(id = "t2", name = "Ms. Gupta")
        )

        val assignments = listOf(
            ClassSectionAssignment(
                id = "ca_5a",
                schoolId = "sch_1",
                className = "5th",
                section = "A",
                subjectAssignments = listOf(
                    SubjectTeacherAssignment("Maths", "t1", 6),
                    SubjectTeacherAssignment("Science", "t2", 6)
                )
            ),
            ClassSectionAssignment(
                id = "ca_5b",
                schoolId = "sch_1",
                className = "5th",
                section = "B",
                subjectAssignments = listOf(
                    SubjectTeacherAssignment("Maths", "t1", 6),
                    SubjectTeacherAssignment("Science", "t2", 6)
                )
            )
        )

        // Rule A: COMPETITION_INCLUDE_ALL_TIED with topN = 2 -> ranks 1, 2, 2 (both tied at rank 2 included)
        val bCompAll = Phase17ResultAnalysisEngine.computeBundle(
            scopedData = scoped,
            schoolDocId = "sch_1",
            selectedClass = "5th",
            selectedSection = "ALL",
            examFilter = ResultAnalysisExamFilter.UT1,
            topperScope = TopperScope.CLASS,
            topN = 2,
            tieRule = TieBreakRule.COMPETITION_INCLUDE_ALL_TIED,
            classAssignments = assignments,
            teachers = teachers
        )
        assertEquals(3, bCompAll.toppers.size)
        assertEquals(listOf(1, 2, 2), bCompAll.toppers.map { it.rank })
        assertTrue(bCompAll.toppers[1].isTied && bCompAll.toppers[2].isTied)

        // With topN = 4 under COMPETITION_INCLUDE_ALL_TIED -> ranks are 1, 2, 2, 4
        val bComp4 = Phase17ResultAnalysisEngine.computeBundle(
            scopedData = scoped,
            schoolDocId = "sch_1",
            selectedClass = "5th",
            selectedSection = "ALL",
            examFilter = ResultAnalysisExamFilter.UT1,
            topperScope = TopperScope.CLASS,
            topN = 4,
            tieRule = TieBreakRule.COMPETITION_INCLUDE_ALL_TIED
        )
        assertEquals(listOf(1, 2, 2, 4), bComp4.toppers.map { it.rank })

        // Rule B: DENSE_INCLUDE_ALL_TIED with topN = 3 -> ranks are 1, 2, 2, 3
        val bDense = Phase17ResultAnalysisEngine.computeBundle(
            scopedData = scoped,
            schoolDocId = "sch_1",
            selectedClass = "5th",
            selectedSection = "ALL",
            examFilter = ResultAnalysisExamFilter.UT1,
            topperScope = TopperScope.CLASS,
            topN = 3,
            tieRule = TieBreakRule.DENSE_INCLUDE_ALL_TIED
        )
        assertEquals(listOf(1, 2, 2, 3), bDense.toppers.map { it.rank })

        // Rule C: COMPETITION_STRICT_CUTOFF with topN = 2 -> strictly 2 rows (ranks 1, 2)
        val bStrict = Phase17ResultAnalysisEngine.computeBundle(
            scopedData = scoped,
            schoolDocId = "sch_1",
            selectedClass = "5th",
            selectedSection = "ALL",
            examFilter = ResultAnalysisExamFilter.UT1,
            topperScope = TopperScope.CLASS,
            topN = 2,
            tieRule = TieBreakRule.COMPETITION_STRICT_CUTOFF
        )
        assertEquals(2, bStrict.toppers.size)
        assertEquals(listOf(1, 2), bStrict.toppers.map { it.rank })

        // Subject-Wise Analysis: Science has 1 fail (75% pass) vs Maths 0 fails (100% pass) -> Science sorted first!
        assertEquals(2, bCompAll.subjectRows.size)
        val weakestSub = bCompAll.subjectRows[0]
        val strongestSub = bCompAll.subjectRows[1]
        assertEquals("Science", weakestSub.subjectName)
        assertEquals("Ms. Gupta", weakestSub.teacherLabel)
        assertEquals(1, weakestSub.failCount)
        assertEquals(75.0, weakestSub.passPercent, 0.01)

        assertEquals("Maths", strongestSub.subjectName)
        assertEquals("Mr. Sinha", strongestSub.teacherLabel)
        assertEquals(0, strongestSub.failCount)
        assertEquals(100.0, strongestSub.passPercent, 0.01)

        // Section comparison (5th - A vs 5th - B)
        assertEquals(2, bCompAll.comparisonRows.size)
        assertEquals("5th - A", bCompAll.comparisonRows[0].label)
        assertEquals(100.0, bCompAll.comparisonRows[0].passPercent, 0.01)
        assertEquals("5th - B", bCompAll.comparisonRows[1].label)
        assertEquals(50.0, bCompAll.comparisonRows[1].passPercent, 0.01)
    }

    // -------------------------------------------------------------------------
    // 4. STUDENT PROGRESS ACROSS THE YEAR & GUARDIAN MESSAGE
    // -------------------------------------------------------------------------
    @Test
    fun studentProgress_computesSideBySideExamsTrendsAndManualGuardianMessage() {
        val student = makeStudent(10L, "Ananya Sharma", className = "6th", section = "A", rollNo = 4)
        val marks = buildList {
            // Maths improves from 50% in T1 to 85% in T2 -> UP
            addAll(makeTerm1Marks(10L, "Maths", "5", "15", "30")) // UT1=15/30 (50%), HY=50/100 (50%)
            addAll(makeTerm2Marks(10L, "Maths", "9", "24", "52")) // UT2=24/30 (80%), YR=85/100 (85%)

            // English drops from 80% in T1 to 30% in T2 -> DOWN & fails in Yearly
            addAll(makeTerm1Marks(10L, "English", "8", "24", "48")) // UT1=24/30 (80%), HY=80/100 (80%)
            addAll(makeTerm2Marks(10L, "English", "3", "9", "18"))  // UT2=9/30 (30%), YR=30/100 (30%)
        }

        val scoped = ResultAnalysisAccessEvaluator.scopeDataForMember(
            member = ownerMember(),
            teacherScope = null,
            schoolDocId = "sch_1",
            session = "2026-2027",
            schoolStableId = 1L,
            allClasses = listOf("6th"),
            subjectsByClass = mapOf("6th" to listOf("Maths", "English")),
            allStudents = listOf(student),
            allMarks = marks
        )

        val progress = Phase17ResultAnalysisEngine.computeStudentProgress(
            student = student,
            scopedData = scoped,
            marksForStudent = marks,
            passPercent = 33
        )

        assertEquals(65.0, progress.examSummaries[ResultAnalysisExamFilter.UT1]!!.percentage, 0.01)
        assertEquals(65.0, progress.examSummaries[ResultAnalysisExamFilter.HALF_YEARLY]!!.percentage, 0.01)
        assertEquals(55.0, progress.examSummaries[ResultAnalysisExamFilter.UT2]!!.percentage, 0.01)
        assertEquals(57.5, progress.examSummaries[ResultAnalysisExamFilter.YEARLY]!!.percentage, 0.01)

        val mathsRow = progress.subjectRows.first { it.subjectName == "Maths" }
        val engRow = progress.subjectRows.first { it.subjectName == "English" }
        assertEquals(ProgressTrend.UP, mathsRow.trend)
        assertEquals(ProgressTrend.DOWN, engRow.trend)
        assertEquals(listOf("English"), progress.failedOrWeakSubjectsLatest)

        val msgEn = Phase17ResultAnalysisEngine.buildGuardianProgressMessage(
            schoolName = "DEMO MODEL SCHOOL",
            session = "2026-2027",
            progress = progress,
            isHindi = false
        )
        assertTrue(msgEn.contains("Ananya Sharma"))
        assertTrue(msgEn.contains("UT-I: 65.00%"))
        assertTrue(msgEn.contains("English"))

        val msgHi = Phase17ResultAnalysisEngine.buildGuardianProgressMessage(
            schoolName = "DEMO MODEL SCHOOL",
            session = "2026-2027",
            progress = progress,
            isHindi = true
        )
        assertTrue(msgHi.contains("Ananya Sharma"))
        assertTrue(msgHi.contains("विशेष ध्यान देने योग्य विषय: English"))
    }

    // -------------------------------------------------------------------------
    // 5. OFFLINE A4 PDF GENERATION & BITMAP PREVIEW FOR ALL 5 PDF REPORTS
    // -------------------------------------------------------------------------
    @Test
    fun offlinePdfGenerator_generatesAllFiveResultAnalysisPdfsAndRendersBitmaps() {
        val school = makeSchool()
        val s1 = makeStudent(1L, "Aarav Verma", className = "5th", section = "A", rollNo = 1)
        val s2 = makeStudent(2L, "Rohan Gupta", className = "5th", section = "A", rollNo = 2)
        val marks = buildList {
            addAll(makeTerm1Marks(1L, "Maths", "9", "28", "56"))
            addAll(makeTerm1Marks(1L, "English", "8", "26", "52"))
            addAll(makeTerm1Marks(2L, "Maths", "3", "8", "14"))
            addAll(makeTerm1Marks(2L, "English", "4", "11", "22"))
        }
        val scoped = ResultAnalysisAccessEvaluator.scopeDataForMember(
            member = ownerMember(),
            teacherScope = null,
            schoolDocId = "sch_1",
            session = "2026-2027",
            schoolStableId = 1L,
            allClasses = listOf("5th"),
            subjectsByClass = mapOf("5th" to listOf("Maths", "English")),
            allStudents = listOf(s1, s2),
            allMarks = marks
        )
        val bundle = Phase17ResultAnalysisEngine.computeBundle(
            scopedData = scoped,
            schoolDocId = "sch_1",
            selectedClass = "5th",
            selectedSection = "A",
            examFilter = ResultAnalysisExamFilter.HALF_YEARLY
        )
        val progress = Phase17ResultAnalysisEngine.computeStudentProgress(
            student = s1,
            scopedData = scoped,
            marksForStudent = marks.filter { it.studentId == 1L }
        )

        for (type in ResultAnalysisPdfType.entries) {
            val req = ResultAnalysisPdfRequest(
                type = type,
                school = school,
                session = "2026-2027",
                bundle = bundle,
                studentProgress = progress,
                generatedByName = "Principal"
            )
            val pdfFile = Phase17PdfGenerator.generatePdf(context, req)
            assertTrue("Expected PDF file to exist for $type", pdfFile.exists())
            assertTrue("Expected non-empty PDF for $type", pdfFile.length() > 800L)

            val bitmaps = Phase17PdfGenerator.renderPreviewBitmaps(req)
            assertTrue("Expected at least 1 preview page for $type", bitmaps.isNotEmpty())
            val firstBmp = bitmaps.first()
            assertEquals(Phase17PdfGenerator.A4_WIDTH, firstBmp.width)
            assertEquals(Phase17PdfGenerator.A4_HEIGHT, firstBmp.height)
            bitmaps.forEach { it.recycle() }
        }

        // Also verify CSV export text contains all key sections
        val csv = Phase17ResultAnalysisEngine.buildAnalysisCsv(school.name, "2026-2027", bundle)
        assertTrue(csv.contains("SECTION: CLASS OVERVIEW"))
        assertTrue(csv.contains("SECTION: GRADE DISTRIBUTION"))
        assertTrue(csv.contains("SECTION: TOPPERS"))
        assertTrue(csv.contains("SECTION: SUBJECT ANALYSIS (WEAKEST FIRST)"))
        assertTrue(csv.contains("SECTION: WEAK STUDENTS"))
        assertTrue(csv.contains("SECTION: CLASS / SECTION COMPARISON"))
    }
}
