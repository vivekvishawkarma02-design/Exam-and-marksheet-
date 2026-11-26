package com.example.domain

import com.example.data.MarkEntity
import com.example.data.StudentEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

// =============================================================================
// PHASE 17 DOMAIN ENGINE: RESULT ANALYSIS (STRICTLY READ-ONLY)
// Reuses ReportCalculations grading (A1..E) and pass rules (>=33% per subject).
// Pure Kotlin (zero Android/Firebase imports) for fast unit testing.
// =============================================================================

enum class ResultAnalysisExamFilter(
    val key: String,
    val labelEn: String,
    val labelHi: String,
    val maxPerSubject: Int,
    val requiredComponents: List<MarkComponent>
) {
    UT1(
        key = "UT1",
        labelEn = "UT-I",
        labelHi = "UT-I (प्रथम इकाई)",
        maxPerSubject = 30,
        requiredComponents = listOf(MarkComponent.T1_UT)
    ),
    UT2(
        key = "UT2",
        labelEn = "UT-II",
        labelHi = "UT-II (द्वितीय इकाई)",
        maxPerSubject = 30,
        requiredComponents = listOf(MarkComponent.T2_UT)
    ),
    HALF_YEARLY(
        key = "HALF_YEARLY",
        labelEn = "Half-Yearly",
        labelHi = "अर्धवार्षिक (H.Y.)",
        maxPerSubject = 100,
        requiredComponents = MarkComponent.term1Components()
    ),
    YEARLY(
        key = "YEARLY",
        labelEn = "Yearly",
        labelHi = "वार्षिक (Yearly)",
        maxPerSubject = 100,
        requiredComponents = MarkComponent.term2Components()
    ),
    ALL(
        key = "ALL",
        labelEn = "All (Annual)",
        labelHi = "सभी (वार्षिक समग्र)",
        maxPerSubject = 200,
        requiredComponents = MarkComponent.entries
    )
}

enum class TopperScope(val labelEn: String, val labelHi: String) {
    SECTION("Per Section", "सेक्शन अनुसार"),
    CLASS("Per Class", "कक्षा अनुसार"),
    SCHOOL("School-wide", "पूरा स्कूल")
}

enum class TieBreakRule(
    val labelEn: String,
    val labelHi: String,
    val descriptionEn: String,
    val descriptionHi: String
) {
    COMPETITION_INCLUDE_ALL_TIED(
        labelEn = "1, 2, 2, 4 • Include all tied",
        labelHi = "1, 2, 2, 4 • बराबरी पर सभी",
        descriptionEn = "Tied students share the same rank; next rank skips (1, 2, 2, 4). Everyone tied at rank N is included.",
        descriptionHi = "समान अंक वालों को एक ही रैंक (1, 2, 2, 4) मिलेगा और रैंक N पर बराबरी वाले सभी छात्र दिखेंगे।"
    ),
    DENSE_INCLUDE_ALL_TIED(
        labelEn = "1, 2, 2, 3 • Dense + all tied",
        labelHi = "1, 2, 2, 3 • डेंस + सभी",
        descriptionEn = "Tied students share the same rank; next rank increases by 1 (1, 2, 2, 3). Everyone tied at rank N is included.",
        descriptionHi = "समान अंक वालों को एक ही रैंक (1, 2, 2, 3) मिलेगा और रैंक N पर बराबरी वाले सभी छात्र दिखेंगे।"
    ),
    COMPETITION_STRICT_CUTOFF(
        labelEn = "1, 2, 2, 4 • Strict top N",
        labelHi = "1, 2, 2, 4 • ठीक N छात्र",
        descriptionEn = "Tied students share the same rank (1, 2, 2, 4), capped strictly at N rows ordered by roll/name.",
        descriptionHi = "समान अंक वालों को एक ही रैंक मिलेगा, लेकिन सूची रोल नंबर/नाम के क्रम से ठीक N छात्रों पर रुकेगी।"
    )
}

enum class WeakFilterMode(val labelEn: String, val labelHi: String) {
    FAIL_OR_BELOW_THRESHOLD("Failing OR < %", "फेल या < %"),
    FAILING_ONLY("Failing only", "केवल फेल"),
    BELOW_THRESHOLD_ONLY("Below threshold %", "न्यूनतम % से कम")
}

enum class ClassComparisonMode(val labelEn: String, val labelHi: String) {
    BY_SECTION("By Section", "सेक्शन अनुसार"),
    BY_CLASS("By Class", "कक्षा अनुसार")
}

enum class ProgressTrend(val symbol: String, val labelEn: String, val labelHi: String) {
    UP("↑", "Improving", "सुधार"),
    DOWN("↓", "Dropping", "गिरावट"),
    STEADY("→", "Steady", "स्थिर"),
    SINGLE("•", "Only 1 exam", "केवल 1 परीक्षा")
}

data class GradeBucket(
    val grade: String,
    val count: Int,
    val percentOfAppeared: Double
)

data class EvaluatedSubjectMetric(
    val subjectName: String,
    val obtained: Double,
    val maxMarks: Int,
    val percentage: Double,
    val grade: String,
    val hasEntry: Boolean,
    val resultStatus: ResultStatus
)

data class EvaluatedStudentResult(
    val student: StudentEntity,
    val subjects: List<EvaluatedSubjectMetric>,
    val totalObtained: Double,
    val totalMax: Int,
    val percentage: Double,
    val grade: String,
    val resultStatus: ResultStatus,
    val failedSubjects: List<String>,
    val enteredEntriesCount: Int,
    val expectedEntriesCount: Int
) {
    val hasAnyMarks: Boolean get() = resultStatus != ResultStatus.PENDING
}

data class ClassResultOverview(
    val totalStudents: Int,
    val appearedCount: Int,
    val passCount: Int,
    val failCount: Int,
    val pendingCount: Int,
    val passPercent: Double,
    val averagePercent: Double,
    val highestStudentName: String,
    val highestObtained: Double,
    val highestMax: Int,
    val highestPercent: Double,
    val lowestStudentName: String,
    val lowestObtained: Double,
    val lowestMax: Int,
    val lowestPercent: Double,
    val gradeDistribution: List<GradeBucket>
)

data class TopperEntry(
    val rank: Int,
    val student: StudentEntity,
    val obtained: Double,
    val maxMarks: Int,
    val percentage: Double,
    val grade: String,
    val resultStatus: ResultStatus,
    val isTied: Boolean
)

data class SubjectAnalysisRow(
    val subjectName: String,
    val teacherLabel: String,
    val appearedCount: Int,
    val passCount: Int,
    val failCount: Int,
    val pendingCount: Int,
    val passPercent: Double,
    val averagePercent: Double,
    val averageMarks: Double,
    val maxMarksPerStudent: Int,
    val highestMarks: Double,
    val highestPercent: Double,
    val lowestMarks: Double,
    val lowestPercent: Double
)

data class WeakStudentRow(
    val student: StudentEntity,
    val obtained: Double,
    val maxMarks: Int,
    val percentage: Double,
    val grade: String,
    val resultStatus: ResultStatus,
    val failedSubjects: List<String>,
    val lowScoreSubjects: List<String>
)

data class ClassComparisonRow(
    val groupKey: String,
    val label: String,
    val totalStudents: Int,
    val appearedCount: Int,
    val passCount: Int,
    val failCount: Int,
    val passPercent: Double,
    val averagePercent: Double,
    val highestPercent: Double,
    val lowestPercent: Double
)

data class MissingMarkAnalysisRow(
    val student: StudentEntity,
    val subjectName: String,
    val missingComponentLabels: List<String>,
    val missingCount: Int,
    val expectedCount: Int
)

data class ExamSnapshotSummary(
    val examFilter: ResultAnalysisExamFilter,
    val totalObtained: Double,
    val totalMax: Int,
    val percentage: Double,
    val grade: String,
    val resultStatus: ResultStatus,
    val hasAnyMarks: Boolean
)

data class StudentSubjectProgressRow(
    val subjectName: String,
    val ut1Obtained: Double?,
    val ut1Max: Int,
    val ut1Percent: Double?,
    val ut2Obtained: Double?,
    val ut2Max: Int,
    val ut2Percent: Double?,
    val halfYearlyObtained: Double?,
    val halfYearlyMax: Int,
    val halfYearlyPercent: Double?,
    val yearlyObtained: Double?,
    val yearlyMax: Int,
    val yearlyPercent: Double?,
    val overallObtained: Double?,
    val overallMax: Int,
    val overallPercent: Double?,
    val trend: ProgressTrend
)

data class StudentProgressReport(
    val student: StudentEntity,
    val examSummaries: Map<ResultAnalysisExamFilter, ExamSnapshotSummary>,
    val subjectRows: List<StudentSubjectProgressRow>,
    val overallTrend: ProgressTrend,
    val failedOrWeakSubjectsLatest: List<String>
)

data class ResultAnalysisScopedData(
    val allowedClasses: List<String>,
    val allowedSectionsByClass: Map<String, List<String>>,
    val subjectsByClassSection: Map<Pair<String, String>, List<String>>,
    val scopedStudents: List<StudentEntity>,
    val scopedMarks: List<MarkEntity>,
    val isTeacherRestricted: Boolean
)

data class ResultAnalysisBundle(
    val examFilter: ResultAnalysisExamFilter,
    val scopeLabel: String,
    val totalExpectedEntries: Int,
    val enteredEntries: Int,
    val missingEntries: Int,
    val overview: ClassResultOverview,
    val toppers: List<TopperEntry>,
    val subjectRows: List<SubjectAnalysisRow>,
    val weakStudents: List<WeakStudentRow>,
    val comparisonRows: List<ClassComparisonRow>,
    val missingRows: List<MissingMarkAnalysisRow>,
    val evaluatedStudents: List<EvaluatedStudentResult>
) {
    val hasIncompleteMarks: Boolean get() = missingEntries > 0
}

// =============================================================================
// ACCESS & TEACHER SCOPE FILTER
// =============================================================================
object ResultAnalysisAccessEvaluator {

    fun canViewResultAnalysis(member: MemberAccessProfile?, schoolDocId: String = ""): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return false
        if (schoolDocId.isNotBlank() && !OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) {
            return false
        }
        return OrgAccessEvaluator.canViewModule(member, AppModule.RESULT_ANALYSIS)
    }

    fun canExportCsv(member: MemberAccessProfile?): Boolean =
        OrgAccessEvaluator.isAdmin(member)

    /**
     * Enforces data-access isolation:
     * - Non-teachers with `resultAnalysis: view` see all classes, sections & subjects in allowed schools.
     * - Teachers see ONLY the classes, sections & subjects assigned to them in TeacherAccessScope
     *   (or legacy member.assignedClassSubjects only when teacherScope is null).
     *   An unassigned teacher sees ZERO students, classes, subjects, or marks.
     */
    fun scopeDataForMember(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolDocId: String,
        session: String,
        schoolStableId: Long,
        allClasses: List<String>,
        subjectsByClass: Map<String, List<String>>,
        allStudents: List<StudentEntity>,
        allMarks: List<MarkEntity>
    ): ResultAnalysisScopedData {
        if (!canViewResultAnalysis(member, schoolDocId)) {
            return ResultAnalysisScopedData(
                allowedClasses = emptyList(),
                allowedSectionsByClass = emptyMap(),
                subjectsByClassSection = emptyMap(),
                scopedStudents = emptyList(),
                scopedMarks = emptyList(),
                isTeacherRestricted = true
            )
        }

        val sessionMarksByStudent = allMarks.groupBy { it.studentId }
        val sessionStudents = allStudents.filter { st ->
            st.schoolId == schoolStableId &&
                st.session.equals(session, ignoreCase = true) &&
                (st.isActiveStatus || !sessionMarksByStudent[st.id].isNullOrEmpty())
        }

        val isTeacher = Phase7AccessEvaluator.isTeacherRestricted(member)
        if (!isTeacher) {
            val sectionsByClass = linkedMapOf<String, List<String>>()
            val subjectsByClsSec = linkedMapOf<Pair<String, String>, List<String>>()
            for (cls in allClasses) {
                val secs = sessionStudents
                    .filter { it.className.equals(cls, ignoreCase = true) }
                    .map { it.section.trim().uppercase().ifBlank { "A" } }
                    .distinct()
                    .sorted()
                    .ifEmpty { listOf("A") }
                sectionsByClass[cls] = secs
                val subs = subjectsByClass[cls].orEmpty()
                secs.forEach { sec ->
                    subjectsByClsSec[cls.lowercase(Locale.US) to sec] = subs
                }
            }
            val scopedStudentIds = sessionStudents.map { it.id }.toSet()
            val scopedMarks = allMarks.filter { it.studentId in scopedStudentIds }
            return ResultAnalysisScopedData(
                allowedClasses = allClasses,
                allowedSectionsByClass = sectionsByClass,
                subjectsByClassSection = subjectsByClsSec,
                scopedStudents = sessionStudents,
                scopedMarks = scopedMarks,
                isTeacherRestricted = false
            )
        }

        val allowedClasses = Phase7AccessEvaluator.allowedClassesForMarks(
            member = member,
            teacherScope = teacherScope,
            schoolDocId = schoolDocId,
            allSchoolClasses = allClasses
        )
        val sectionsByClass = linkedMapOf<String, List<String>>()
        val subjectsByClsSec = linkedMapOf<Pair<String, String>, List<String>>()
        val allowedPairKeys = hashSetOf<Pair<String, String>>()

        for (cls in allowedClasses) {
            val rawSections = sessionStudents
                .filter { it.className.equals(cls, ignoreCase = true) }
                .map { it.section.trim().uppercase().ifBlank { "A" } }
                .distinct()
                .sorted()
                .ifEmpty { DefaultSchoolTemplate.defaultSections }
            val allowedSecs = Phase7AccessEvaluator.allowedSectionsForMarks(
                member = member,
                teacherScope = teacherScope,
                schoolDocId = schoolDocId,
                className = cls,
                allSections = rawSections
            )
            val nonEmptySecs = allowedSecs.filter { sec ->
                val subs = Phase7AccessEvaluator.allowedSubjectsForMarks(
                    member = member,
                    teacherScope = teacherScope,
                    schoolDocId = schoolDocId,
                    className = cls,
                    section = sec,
                    allSubjects = subjectsByClass[cls].orEmpty()
                )
                if (subs.isNotEmpty()) {
                    val key = cls.lowercase(Locale.US) to sec.uppercase(Locale.US)
                    subjectsByClsSec[key] = subs
                    allowedPairKeys.add(key)
                    true
                } else {
                    false
                }
            }
            if (nonEmptySecs.isNotEmpty()) {
                sectionsByClass[cls] = nonEmptySecs
            }
        }

        val finalClasses = allowedClasses.filter { it in sectionsByClass }
        val scopedStudents = sessionStudents.filter { st ->
            val key = st.className.trim().lowercase(Locale.US) to st.section.trim().uppercase(Locale.US).ifBlank { "A" }
            key in allowedPairKeys
        }
        val studentById = scopedStudents.associateBy { it.id }
        val scopedMarks = allMarks.filter { mark ->
            val st = studentById[mark.studentId] ?: return@filter false
            val key = st.className.trim().lowercase(Locale.US) to st.section.trim().uppercase(Locale.US).ifBlank { "A" }
            val allowedSubs = subjectsByClsSec[key].orEmpty()
            allowedSubs.any { it.equals(mark.subjectName.trim(), ignoreCase = true) }
        }

        return ResultAnalysisScopedData(
            allowedClasses = finalClasses,
            allowedSectionsByClass = sectionsByClass,
            subjectsByClassSection = subjectsByClsSec,
            scopedStudents = scopedStudents,
            scopedMarks = scopedMarks,
            isTeacherRestricted = true
        )
    }
}

// =============================================================================
// PURE KOTLIN ANALYTICS COMPUTATION ENGINE
// =============================================================================
object Phase17ResultAnalysisEngine {

    private val GRADE_ORDER = listOf("A1", "A2", "B1", "B2", "C1", "C2", "D", "E")

    fun subjectsForStudent(
        student: StudentEntity,
        scopedData: ResultAnalysisScopedData
    ): List<String> {
        val sec = student.section.trim().uppercase(Locale.US).ifBlank { "A" }
        return scopedData.subjectsByClassSection[student.className.trim().lowercase(Locale.US) to sec]
            .orEmpty()
    }

    fun evaluateStudentForExam(
        student: StudentEntity,
        subjects: List<String>,
        marksBySubjectAndKey: Map<Pair<String, String>, String>,
        examFilter: ResultAnalysisExamFilter,
        passPercent: Int
    ): EvaluatedStudentResult {
        if (subjects.isEmpty()) {
            return EvaluatedStudentResult(
                student = student,
                subjects = emptyList(),
                totalObtained = 0.0,
                totalMax = 0,
                percentage = 0.0,
                grade = "-",
                resultStatus = ResultStatus.PENDING,
                failedSubjects = emptyList(),
                enteredEntriesCount = 0,
                expectedEntriesCount = 0
            )
        }

        val requiredComps = examFilter.requiredComponents
        var enteredCount = 0
        val expectedCount = subjects.size * requiredComps.size
        for (subj in subjects) {
            for (comp in requiredComps) {
                if (!marksBySubjectAndKey[subj to comp.key].isNullOrBlank()) {
                    enteredCount++
                }
            }
        }

        val evaluatedSubjects: List<EvaluatedSubjectMetric>
        val totalObtained: Double
        val totalMax: Int
        val overallPct: Double
        val overallGrade: String
        val overallStatus: ResultStatus

        when (examFilter) {
            ResultAnalysisExamFilter.UT1, ResultAnalysisExamFilter.UT2 -> {
                val examType = if (examFilter == ResultAnalysisExamFilter.UT1) ExamType.UT1 else ExamType.UT2
                val single = ReportCalculations.calculateSingleExamReport(
                    examType = examType,
                    subjects = subjects,
                    marksBySubjectAndKey = marksBySubjectAndKey,
                    passPercent = passPercent
                )
                evaluatedSubjects = single.rows.map { r ->
                    val pct = ReportCalculations.calculatePercentage(r.obtained, r.maxMarks.toDouble())
                    val subStatus = ReportCalculations.evaluatePassStatus(
                        subjectTotalsAndMax = listOf(r.obtained to r.maxMarks.toDouble()),
                        hasAnyMarks = r.hasEntry,
                        passPercent = passPercent
                    )
                    EvaluatedSubjectMetric(
                        subjectName = r.subjectName,
                        obtained = r.obtained,
                        maxMarks = r.maxMarks,
                        percentage = pct,
                        grade = r.grade,
                        hasEntry = r.hasEntry,
                        resultStatus = subStatus
                    )
                }
                totalObtained = single.totalObtained
                totalMax = single.totalMax
                overallPct = single.percentage
                overallGrade = single.grade
                overallStatus = single.resultStatus
            }

            ResultAnalysisExamFilter.HALF_YEARLY -> {
                val perf = ReportCalculations.calculateStudentPerformance(
                    studentId = student.id,
                    subjects = subjects,
                    marksBySubjectAndKey = marksBySubjectAndKey,
                    passPercent = passPercent
                )
                evaluatedSubjects = perf.subjects.map { d ->
                    val pct = ReportCalculations.calculatePercentage(d.t1Total, d.t1Max.toDouble())
                    val subStatus = ReportCalculations.evaluatePassStatus(
                        subjectTotalsAndMax = listOf(d.t1Total to d.t1Max.toDouble()),
                        hasAnyMarks = d.t1HasAnyEntry,
                        passPercent = passPercent
                    )
                    EvaluatedSubjectMetric(
                        subjectName = d.subjectName,
                        obtained = d.t1Total,
                        maxMarks = d.t1Max,
                        percentage = pct,
                        grade = d.t1Grade,
                        hasEntry = d.t1HasAnyEntry,
                        resultStatus = subStatus
                    )
                }
                totalObtained = perf.term1Total
                totalMax = perf.term1Max
                overallPct = perf.term1Percentage
                overallGrade = perf.term1Grade
                overallStatus = perf.term1Result
            }

            ResultAnalysisExamFilter.YEARLY -> {
                val perf = ReportCalculations.calculateStudentPerformance(
                    studentId = student.id,
                    subjects = subjects,
                    marksBySubjectAndKey = marksBySubjectAndKey,
                    passPercent = passPercent
                )
                evaluatedSubjects = perf.subjects.map { d ->
                    val pct = ReportCalculations.calculatePercentage(d.t2Total, d.t2Max.toDouble())
                    val subStatus = ReportCalculations.evaluatePassStatus(
                        subjectTotalsAndMax = listOf(d.t2Total to d.t2Max.toDouble()),
                        hasAnyMarks = d.t2HasAnyEntry,
                        passPercent = passPercent
                    )
                    EvaluatedSubjectMetric(
                        subjectName = d.subjectName,
                        obtained = d.t2Total,
                        maxMarks = d.t2Max,
                        percentage = pct,
                        grade = d.t2Grade,
                        hasEntry = d.t2HasAnyEntry,
                        resultStatus = subStatus
                    )
                }
                totalObtained = perf.term2Total
                totalMax = perf.term2Max
                overallPct = perf.term2Percentage
                overallGrade = perf.term2Grade
                overallStatus = perf.term2Result
            }

            ResultAnalysisExamFilter.ALL -> {
                val perf = ReportCalculations.calculateStudentPerformance(
                    studentId = student.id,
                    subjects = subjects,
                    marksBySubjectAndKey = marksBySubjectAndKey,
                    passPercent = passPercent
                )
                evaluatedSubjects = perf.subjects.map { d ->
                    val hasAny = d.t1HasAnyEntry || d.t2HasAnyEntry
                    val pct = ReportCalculations.calculatePercentage(d.grandTotal, d.grandMax.toDouble())
                    val subStatus = ReportCalculations.evaluatePassStatus(
                        subjectTotalsAndMax = listOf(d.grandTotal to d.grandMax.toDouble()),
                        hasAnyMarks = hasAny,
                        passPercent = passPercent
                    )
                    EvaluatedSubjectMetric(
                        subjectName = d.subjectName,
                        obtained = d.grandTotal,
                        maxMarks = d.grandMax,
                        percentage = pct,
                        grade = d.grandGrade,
                        hasEntry = hasAny,
                        resultStatus = subStatus
                    )
                }
                totalObtained = perf.overallTotal
                totalMax = perf.overallMax
                overallPct = perf.overallPercentage
                overallGrade = perf.overallGrade
                overallStatus = perf.overallResult
            }
        }

        // Match report card failure semantics: if the student has appeared for the exam,
        // any subject whose score < passPercent (including 0 when unentered on other subjects) fails!
        val failedSubs = if (overallStatus == ResultStatus.PENDING) {
            emptyList()
        } else {
            evaluatedSubjects
                .filter { sub ->
                    ReportCalculations.evaluatePassStatus(
                        subjectTotalsAndMax = listOf(sub.obtained to sub.maxMarks.toDouble()),
                        hasAnyMarks = true,
                        passPercent = passPercent
                    ) == ResultStatus.FAIL
                }
                .map { it.subjectName }
        }

        return EvaluatedStudentResult(
            student = student,
            subjects = evaluatedSubjects,
            totalObtained = totalObtained,
            totalMax = totalMax,
            percentage = overallPct,
            grade = overallGrade,
            resultStatus = overallStatus,
            failedSubjects = failedSubs,
            enteredEntriesCount = enteredCount,
            expectedEntriesCount = expectedCount
        )
    }

    fun computeBundle(
        scopedData: ResultAnalysisScopedData,
        schoolDocId: String,
        selectedClass: String?,   // null or "ALL" = all allowed classes
        selectedSection: String?, // null or "ALL" = all allowed sections
        examFilter: ResultAnalysisExamFilter,
        passPercent: Int = 33,
        topperScope: TopperScope = TopperScope.CLASS,
        topN: Int = 10,
        tieRule: TieBreakRule = TieBreakRule.COMPETITION_INCLUDE_ALL_TIED,
        weakThresholdPercent: Int = 40,
        weakFilterMode: WeakFilterMode = WeakFilterMode.FAIL_OR_BELOW_THRESHOLD,
        comparisonMode: ClassComparisonMode = ClassComparisonMode.BY_SECTION,
        classAssignments: List<ClassSectionAssignment> = emptyList(),
        teachers: List<TeacherProfile> = emptyList(),
        classOrder: List<String> = emptyList()
    ): ResultAnalysisBundle {
        val cleanClass = selectedClass?.trim()?.takeIf { it.isNotEmpty() && !it.equals("ALL", ignoreCase = true) }
        val cleanSection = selectedSection?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotEmpty() && it != "ALL" }

        val marksByStudentLookup: Map<Long, Map<Pair<String, String>, String>> = scopedData.scopedMarks
            .groupBy { it.studentId }
            .mapValues { (_, list) -> list.associate { (it.subjectName to it.componentKey) to it.rawValue } }

        val filteredStudents = scopedData.scopedStudents
            .filter { st ->
                val clsOk = cleanClass == null || st.className.equals(cleanClass, ignoreCase = true)
                val secOk = cleanSection == null || st.section.trim().uppercase(Locale.US) == cleanSection
                clsOk && secOk
            }
            .sortedWith(compareBy<StudentEntity>({ classSortIndex(it.className, classOrder) }, { it.section }, { it.rollNo }, { it.name.lowercase(Locale.US) }))

        val evaluatedInFilter = filteredStudents.map { st ->
            evaluateStudentForExam(
                student = st,
                subjects = subjectsForStudent(st, scopedData),
                marksBySubjectAndKey = marksByStudentLookup[st.id].orEmpty(),
                examFilter = examFilter,
                passPercent = passPercent
            )
        }

        // All scoped students are also evaluated when topperScope == SCHOOL or comparison == BY_CLASS
        val evaluatedAllSchool = if (cleanClass == null && cleanSection == null) {
            evaluatedInFilter
        } else {
            scopedData.scopedStudents.map { st ->
                evaluateStudentForExam(
                    student = st,
                    subjects = subjectsForStudent(st, scopedData),
                    marksBySubjectAndKey = marksByStudentLookup[st.id].orEmpty(),
                    examFilter = examFilter,
                    passPercent = passPercent
                )
            }
        }

        val totalExpected = evaluatedInFilter.sumOf { it.expectedEntriesCount }
        val totalEntered = evaluatedInFilter.sumOf { it.enteredEntriesCount }
        val missingEntries = (totalExpected - totalEntered).coerceAtLeast(0)

        val overview = buildOverview(evaluatedInFilter)

        val topperPool = when (topperScope) {
            TopperScope.SECTION -> evaluatedInFilter
            TopperScope.CLASS -> if (cleanClass != null) {
                evaluatedAllSchool.filter { it.student.className.equals(cleanClass, ignoreCase = true) }
            } else {
                evaluatedInFilter
            }
            TopperScope.SCHOOL -> evaluatedAllSchool
        }
        val toppers = computeToppers(
            candidates = topperPool,
            topN = topN.coerceAtLeast(1),
            tieRule = tieRule
        )

        val subjectRows = computeSubjectAnalysis(
            evaluatedStudents = evaluatedInFilter,
            schoolDocId = schoolDocId,
            classAssignments = classAssignments,
            teachers = teachers,
            passPercent = passPercent
        )

        val weakStudents = computeWeakStudents(
            evaluatedStudents = evaluatedInFilter,
            thresholdPercent = weakThresholdPercent.coerceIn(1, 100),
            mode = weakFilterMode
        )

        val comparisonPool = when {
            comparisonMode == ClassComparisonMode.BY_CLASS && cleanClass == null -> evaluatedInFilter
            comparisonMode == ClassComparisonMode.BY_SECTION && cleanClass != null && cleanSection == null -> evaluatedInFilter
            else -> evaluatedAllSchool
        }
        val comparisonRows = computeComparisonRows(
            evaluatedStudents = comparisonPool,
            mode = comparisonMode,
            classOrder = classOrder
        )

        val missingRows = computeMissingMarksRows(
            students = filteredStudents,
            scopedData = scopedData,
            marksByStudentLookup = marksByStudentLookup,
            examFilter = examFilter
        )

        val scopeLabel = buildString {
            append(cleanClass ?: "All Classes")
            if (cleanClass != null) {
                append(" • ")
                append(if (cleanSection != null) "Sec $cleanSection" else "All Sections")
            }
        }

        return ResultAnalysisBundle(
            examFilter = examFilter,
            scopeLabel = scopeLabel,
            totalExpectedEntries = totalExpected,
            enteredEntries = totalEntered,
            missingEntries = missingEntries,
            overview = overview,
            toppers = toppers,
            subjectRows = subjectRows,
            weakStudents = weakStudents,
            comparisonRows = comparisonRows,
            missingRows = missingRows,
            evaluatedStudents = evaluatedInFilter
        )
    }

    private fun buildOverview(evaluated: List<EvaluatedStudentResult>): ClassResultOverview {
        val appeared = evaluated.filter { it.hasAnyMarks }
        val passCount = appeared.count { it.resultStatus == ResultStatus.PASS }
        val failCount = appeared.count { it.resultStatus == ResultStatus.FAIL }
        val pendingCount = evaluated.size - appeared.size

        val passPct = if (appeared.isEmpty()) 0.0 else ReportCalculations.calculatePercentage(passCount.toDouble(), appeared.size.toDouble())
        val avgPct = if (appeared.isEmpty()) {
            0.0
        } else {
            val sumPct = appeared.sumOf { it.percentage }
            kotlin.math.round((sumPct / appeared.size.toDouble()) * 100.0) / 100.0
        }

        val highest = appeared.maxWithOrNull(
            compareBy<EvaluatedStudentResult>({ it.percentage }, { it.totalObtained })
        )
        val lowest = appeared.minWithOrNull(
            compareBy<EvaluatedStudentResult>({ it.percentage }, { it.totalObtained })
        )

        val gradeCounts = GRADE_ORDER.associateWith { g ->
            appeared.count { it.grade.equals(g, ignoreCase = true) }
        }
        val distribution = GRADE_ORDER.map { g ->
            val cnt = gradeCounts[g] ?: 0
            val pct = if (appeared.isEmpty()) 0.0 else ReportCalculations.calculatePercentage(cnt.toDouble(), appeared.size.toDouble())
            GradeBucket(grade = g, count = cnt, percentOfAppeared = pct)
        }

        return ClassResultOverview(
            totalStudents = evaluated.size,
            appearedCount = appeared.size,
            passCount = passCount,
            failCount = failCount,
            pendingCount = pendingCount,
            passPercent = passPct,
            averagePercent = avgPct,
            highestStudentName = highest?.student?.name.orEmpty(),
            highestObtained = highest?.totalObtained ?: 0.0,
            highestMax = highest?.totalMax ?: 0,
            highestPercent = highest?.percentage ?: 0.0,
            lowestStudentName = lowest?.student?.name.orEmpty(),
            lowestObtained = lowest?.totalObtained ?: 0.0,
            lowestMax = lowest?.totalMax ?: 0,
            lowestPercent = lowest?.percentage ?: 0.0,
            gradeDistribution = distribution
        )
    }

    fun computeToppers(
        candidates: List<EvaluatedStudentResult>,
        topN: Int,
        tieRule: TieBreakRule
    ): List<TopperEntry> {
        val eligible = candidates
            .filter { it.hasAnyMarks && it.totalMax > 0 }
            .sortedWith(
                compareByDescending<EvaluatedStudentResult> { it.percentage }
                    .thenByDescending { it.totalObtained }
                    .thenBy { it.student.rollNo }
                    .thenBy { it.student.name.lowercase(Locale.US) }
            )
        if (eligible.isEmpty()) return emptyList()

        val scoreKey: (EvaluatedStudentResult) -> Long = { r ->
            (r.percentage * 100.0).roundToLong()
        }
        val countsByScore = eligible.groupingBy(scoreKey).eachCount()

        val ranked = mutableListOf<TopperEntry>()
        var lastScore: Long? = null
        var currentRank = 0
        eligible.forEachIndexed { index, r ->
            val sk = scoreKey(r)
            if (lastScore == null || sk != lastScore) {
                currentRank = when (tieRule) {
                    TieBreakRule.COMPETITION_INCLUDE_ALL_TIED,
                    TieBreakRule.COMPETITION_STRICT_CUTOFF -> index + 1
                    TieBreakRule.DENSE_INCLUDE_ALL_TIED -> currentRank + 1
                }
                lastScore = sk
            }
            val tied = (countsByScore[sk] ?: 0) > 1
            ranked.add(
                TopperEntry(
                    rank = currentRank,
                    student = r.student,
                    obtained = r.totalObtained,
                    maxMarks = r.totalMax,
                    percentage = r.percentage,
                    grade = r.grade,
                    resultStatus = r.resultStatus,
                    isTied = tied
                )
            )
        }

        return when (tieRule) {
            TieBreakRule.COMPETITION_STRICT_CUTOFF -> ranked.take(topN)
            TieBreakRule.COMPETITION_INCLUDE_ALL_TIED,
            TieBreakRule.DENSE_INCLUDE_ALL_TIED -> ranked.filter { it.rank <= topN }
        }
    }

    private fun computeSubjectAnalysis(
        evaluatedStudents: List<EvaluatedStudentResult>,
        schoolDocId: String,
        classAssignments: List<ClassSectionAssignment>,
        teachers: List<TeacherProfile>,
        passPercent: Int
    ): List<SubjectAnalysisRow> {
        if (evaluatedStudents.isEmpty()) return emptyList()
        val teacherById = teachers.associateBy { it.id }

        // Preserve subject display order using first encountered casing
        val canonicalNames = linkedMapOf<String, String>()
        val metricsBySubject = linkedMapOf<String, MutableList<Pair<StudentEntity, EvaluatedSubjectMetric>>>()

        for (stuRes in evaluatedStudents) {
            for (subMetric in stuRes.subjects) {
                val key = subMetric.subjectName.trim().lowercase(Locale.US)
                canonicalNames.putIfAbsent(key, subMetric.subjectName.trim())
                metricsBySubject.getOrPut(key) { mutableListOf() }.add(stuRes.student to subMetric)
            }
        }

        return metricsBySubject.entries.map { (subjKey, pairs) ->
            val displayName = canonicalNames[subjKey] ?: subjKey
            val appeared = pairs.filter { it.second.hasEntry }
            val pendingCount = pairs.size - appeared.size
            val passCount = appeared.count { it.second.resultStatus == ResultStatus.PASS }
            val failCount = appeared.count { it.second.resultStatus == ResultStatus.FAIL }

            val passPct = if (appeared.isEmpty()) 0.0 else ReportCalculations.calculatePercentage(passCount.toDouble(), appeared.size.toDouble())
            val avgPct = if (appeared.isEmpty()) 0.0 else kotlin.math.round((appeared.sumOf { it.second.percentage } / appeared.size.toDouble()) * 100.0) / 100.0
            val avgMarks = if (appeared.isEmpty()) 0.0 else kotlin.math.round((appeared.sumOf { it.second.obtained } / appeared.size.toDouble()) * 100.0) / 100.0

            val highItem = appeared.maxByOrNull { it.second.percentage }
            val lowItem = appeared.minByOrNull { it.second.percentage }
            val maxPerStu = appeared.firstOrNull()?.second?.maxMarks ?: pairs.firstOrNull()?.second?.maxMarks ?: 0

            val classSecPairsInSubject = pairs.map {
                it.first.className.trim() to it.first.section.trim().uppercase(Locale.US).ifBlank { "A" }
            }.distinct()
            val teacherLabel = resolveSubjectTeachersLabel(
                schoolDocId = schoolDocId,
                subjectName = displayName,
                classSections = classSecPairsInSubject,
                classAssignments = classAssignments,
                teacherById = teacherById
            )

            SubjectAnalysisRow(
                subjectName = displayName,
                teacherLabel = teacherLabel,
                appearedCount = appeared.size,
                passCount = passCount,
                failCount = failCount,
                pendingCount = pendingCount,
                passPercent = passPct,
                averagePercent = avgPct,
                averageMarks = avgMarks,
                maxMarksPerStudent = maxPerStu,
                highestMarks = highItem?.second?.obtained ?: 0.0,
                highestPercent = highItem?.second?.percentage ?: 0.0,
                lowestMarks = lowItem?.second?.obtained ?: 0.0,
                lowestPercent = lowItem?.second?.percentage ?: 0.0
            )
        }.sortedWith(
            // Sort weakest subject first! (Subjects with 0 appeared pushed after active subjects? Wait: active subjects weakest-first, pending-only at bottom)
            compareBy<SubjectAnalysisRow>(
                { it.appearedCount == 0 },
                { it.passPercent },
                { it.averagePercent },
                { -it.failCount },
                { it.subjectName.lowercase(Locale.US) }
            )
        )
    }

    private fun resolveSubjectTeachersLabel(
        schoolDocId: String,
        subjectName: String,
        classSections: List<Pair<String, String>>,
        classAssignments: List<ClassSectionAssignment>,
        teacherById: Map<String, TeacherProfile>
    ): String {
        val entries = linkedSetOf<Pair<String, String>>() // teacherName to "className-section"
        for ((cls, sec) in classSections) {
            val ca = classAssignments.firstOrNull {
                it.schoolId == schoolDocId &&
                    it.className.equals(cls, ignoreCase = true) &&
                    it.section.equals(sec, ignoreCase = true)
            } ?: continue
            val sa = ca.subjectAssignments.firstOrNull {
                it.subjectName.equals(subjectName, ignoreCase = true)
            } ?: continue
            val tName = (teacherById[sa.teacherId]?.name ?: sa.teacherId).trim()
            if (tName.isNotEmpty()) {
                entries.add(tName to "$cls-$sec")
            }
        }
        if (entries.isEmpty()) return ""
        val distinctNames = entries.map { it.first }.distinct()
        return if (distinctNames.size == 1) {
            distinctNames.first()
        } else {
            entries.groupBy({ it.first }, { it.second })
                .entries
                .joinToString(", ") { (name, secs) -> "$name (${secs.joinToString("/")})" }
        }
    }

    private fun computeWeakStudents(
        evaluatedStudents: List<EvaluatedStudentResult>,
        thresholdPercent: Int,
        mode: WeakFilterMode
    ): List<WeakStudentRow> {
        return evaluatedStudents
            .filter { it.hasAnyMarks }
            .mapNotNull { res ->
                val isFailing = res.failedSubjects.isNotEmpty() || res.resultStatus == ResultStatus.FAIL
                val isBelowOverall = res.percentage + 1e-6 < thresholdPercent.toDouble()
                val lowSubs = res.subjects
                    .filter { it.hasEntry && it.percentage + 1e-6 < thresholdPercent.toDouble() && it.subjectName !in res.failedSubjects }
                    .map { it.subjectName }

                val include = when (mode) {
                    WeakFilterMode.FAIL_OR_BELOW_THRESHOLD -> isFailing || isBelowOverall
                    WeakFilterMode.FAILING_ONLY -> isFailing
                    WeakFilterMode.BELOW_THRESHOLD_ONLY -> isBelowOverall
                }
                if (!include) return@mapNotNull null

                WeakStudentRow(
                    student = res.student,
                    obtained = res.totalObtained,
                    maxMarks = res.totalMax,
                    percentage = res.percentage,
                    grade = res.grade,
                    resultStatus = res.resultStatus,
                    failedSubjects = res.failedSubjects,
                    lowScoreSubjects = lowSubs
                )
            }
            .sortedWith(
                compareByDescending<WeakStudentRow> { it.failedSubjects.size }
                    .thenBy { it.percentage }
                    .thenBy { it.student.rollNo }
                    .thenBy { it.student.name.lowercase(Locale.US) }
            )
    }

    private fun computeComparisonRows(
        evaluatedStudents: List<EvaluatedStudentResult>,
        mode: ClassComparisonMode,
        classOrder: List<String>
    ): List<ClassComparisonRow> {
        val grouped = when (mode) {
            ClassComparisonMode.BY_SECTION -> evaluatedStudents.groupBy {
                val sec = it.student.section.trim().uppercase(Locale.US).ifBlank { "A" }
                "${it.student.className.trim()}|$sec"
            }
            ClassComparisonMode.BY_CLASS -> evaluatedStudents.groupBy {
                it.student.className.trim()
            }
        }
        return grouped.entries.map { (key, list) ->
            val label = when (mode) {
                ClassComparisonMode.BY_SECTION -> {
                    val parts = key.split("|")
                    "${parts.getOrElse(0) { "" }} - ${parts.getOrElse(1) { "A" }}"
                }
                ClassComparisonMode.BY_CLASS -> key
            }
            val appeared = list.filter { it.hasAnyMarks }
            val passCount = appeared.count { it.resultStatus == ResultStatus.PASS }
            val failCount = appeared.count { it.resultStatus == ResultStatus.FAIL }
            val passPct = if (appeared.isEmpty()) 0.0 else ReportCalculations.calculatePercentage(passCount.toDouble(), appeared.size.toDouble())
            val avgPct = if (appeared.isEmpty()) 0.0 else kotlin.math.round((appeared.sumOf { it.percentage } / appeared.size.toDouble()) * 100.0) / 100.0
            val highPct = appeared.maxOfOrNull { it.percentage } ?: 0.0
            val lowPct = appeared.minOfOrNull { it.percentage } ?: 0.0
            ClassComparisonRow(
                groupKey = key,
                label = label,
                totalStudents = list.size,
                appearedCount = appeared.size,
                passCount = passCount,
                failCount = failCount,
                passPercent = passPct,
                averagePercent = avgPct,
                highestPercent = highPct,
                lowestPercent = lowPct
            )
        }.sortedWith(
            compareBy<ClassComparisonRow>(
                { classSortIndex(it.groupKey.substringBefore("|"), classOrder) },
                { it.groupKey.substringAfter("|", "") }
            )
        )
    }

    private fun computeMissingMarksRows(
        students: List<StudentEntity>,
        scopedData: ResultAnalysisScopedData,
        marksByStudentLookup: Map<Long, Map<Pair<String, String>, String>>,
        examFilter: ResultAnalysisExamFilter
    ): List<MissingMarkAnalysisRow> {
        val required = examFilter.requiredComponents
        val rows = mutableListOf<MissingMarkAnalysisRow>()
        for (st in students) {
            val marksMap = marksByStudentLookup[st.id].orEmpty()
            val subjects = subjectsForStudent(st, scopedData)
            for (subj in subjects) {
                val missingComps = required.filter { comp ->
                    marksMap[subj to comp.key].isNullOrBlank()
                }
                if (missingComps.isNotEmpty()) {
                    rows.add(
                        MissingMarkAnalysisRow(
                            student = st,
                            subjectName = subj,
                            missingComponentLabels = missingComps.map { it.shortLabel },
                            missingCount = missingComps.size,
                            expectedCount = required.size
                        )
                    )
                }
            }
        }
        return rows
    }

    fun computeStudentProgress(
        student: StudentEntity,
        scopedData: ResultAnalysisScopedData,
        marksForStudent: List<MarkEntity>,
        passPercent: Int = 33
    ): StudentProgressReport {
        val subjects = subjectsForStudent(student, scopedData)
        val marksMap = marksForStudent.associate { (it.subjectName to it.componentKey) to it.rawValue }

        val evalByExam = ResultAnalysisExamFilter.entries.associateWith { exam ->
            evaluateStudentForExam(
                student = student,
                subjects = subjects,
                marksBySubjectAndKey = marksMap,
                examFilter = exam,
                passPercent = passPercent
            )
        }

        val summaries = evalByExam.mapValues { (exam, res) ->
            ExamSnapshotSummary(
                examFilter = exam,
                totalObtained = res.totalObtained,
                totalMax = res.totalMax,
                percentage = res.percentage,
                grade = res.grade,
                resultStatus = res.resultStatus,
                hasAnyMarks = res.hasAnyMarks
            )
        }

        val subjectRows = subjects.mapIndexed { idx, subj ->
            val u1 = evalByExam[ResultAnalysisExamFilter.UT1]?.subjects?.getOrNull(idx)
            val u2 = evalByExam[ResultAnalysisExamFilter.UT2]?.subjects?.getOrNull(idx)
            val hy = evalByExam[ResultAnalysisExamFilter.HALF_YEARLY]?.subjects?.getOrNull(idx)
            val yr = evalByExam[ResultAnalysisExamFilter.YEARLY]?.subjects?.getOrNull(idx)
            val ov = evalByExam[ResultAnalysisExamFilter.ALL]?.subjects?.getOrNull(idx)

            val seqPcts = listOfNotNull(
                u1?.takeIf { it.hasEntry }?.percentage,
                hy?.takeIf { it.hasEntry }?.percentage,
                u2?.takeIf { it.hasEntry }?.percentage,
                yr?.takeIf { it.hasEntry }?.percentage
            )
            val trend = computeTrend(seqPcts, thresholdDelta = 3.0)

            StudentSubjectProgressRow(
                subjectName = subj,
                ut1Obtained = u1?.takeIf { it.hasEntry }?.obtained,
                ut1Max = ResultAnalysisExamFilter.UT1.maxPerSubject,
                ut1Percent = u1?.takeIf { it.hasEntry }?.percentage,
                ut2Obtained = u2?.takeIf { it.hasEntry }?.obtained,
                ut2Max = ResultAnalysisExamFilter.UT2.maxPerSubject,
                ut2Percent = u2?.takeIf { it.hasEntry }?.percentage,
                halfYearlyObtained = hy?.takeIf { it.hasEntry }?.obtained,
                halfYearlyMax = ResultAnalysisExamFilter.HALF_YEARLY.maxPerSubject,
                halfYearlyPercent = hy?.takeIf { it.hasEntry }?.percentage,
                yearlyObtained = yr?.takeIf { it.hasEntry }?.obtained,
                yearlyMax = ResultAnalysisExamFilter.YEARLY.maxPerSubject,
                yearlyPercent = yr?.takeIf { it.hasEntry }?.percentage,
                overallObtained = ov?.takeIf { it.hasEntry }?.obtained,
                overallMax = ResultAnalysisExamFilter.ALL.maxPerSubject,
                overallPercent = ov?.takeIf { it.hasEntry }?.percentage,
                trend = trend
            )
        }

        val overallSequence = listOfNotNull(
            summaries[ResultAnalysisExamFilter.UT1]?.takeIf { it.hasAnyMarks }?.percentage,
            summaries[ResultAnalysisExamFilter.HALF_YEARLY]?.takeIf { it.hasAnyMarks }?.percentage,
            summaries[ResultAnalysisExamFilter.UT2]?.takeIf { it.hasAnyMarks }?.percentage,
            summaries[ResultAnalysisExamFilter.YEARLY]?.takeIf { it.hasAnyMarks }?.percentage
        )
        val overallTrend = computeTrend(overallSequence, thresholdDelta = 2.0)

        val latestEval = listOf(
            ResultAnalysisExamFilter.YEARLY,
            ResultAnalysisExamFilter.UT2,
            ResultAnalysisExamFilter.HALF_YEARLY,
            ResultAnalysisExamFilter.UT1
        ).firstOrNull { summaries[it]?.hasAnyMarks == true }?.let { evalByExam[it] }

        val weakLatest = latestEval?.subjects
            ?.filter { it.hasEntry && (it.resultStatus == ResultStatus.FAIL || it.percentage < 40.0) }
            ?.map { it.subjectName }
            .orEmpty()

        return StudentProgressReport(
            student = student,
            examSummaries = summaries,
            subjectRows = subjectRows,
            overallTrend = overallTrend,
            failedOrWeakSubjectsLatest = weakLatest
        )
    }

    private fun computeTrend(orderedPercentages: List<Double>, thresholdDelta: Double): ProgressTrend {
        if (orderedPercentages.size < 2) return ProgressTrend.SINGLE
        val prev = orderedPercentages[orderedPercentages.size - 2]
        val curr = orderedPercentages.last()
        val recentDiff = curr - prev
        val effectiveDiff = if (kotlin.math.abs(recentDiff) >= thresholdDelta || orderedPercentages.size == 2) {
            recentDiff
        } else {
            curr - orderedPercentages.first()
        }
        return when {
            effectiveDiff >= thresholdDelta -> ProgressTrend.UP
            effectiveDiff <= -thresholdDelta -> ProgressTrend.DOWN
            else -> ProgressTrend.STEADY
        }
    }

    /**
     * Plain-text guardian update message built strictly on explicit user tap.
     * Never sent automatically.
     */
    fun buildGuardianProgressMessage(
        schoolName: String,
        session: String,
        progress: StudentProgressReport,
        isHindi: Boolean
    ): String {
        val st = progress.student
        val examParts = listOf(
            ResultAnalysisExamFilter.UT1,
            ResultAnalysisExamFilter.HALF_YEARLY,
            ResultAnalysisExamFilter.UT2,
            ResultAnalysisExamFilter.YEARLY
        ).mapNotNull { exam ->
            val sum = progress.examSummaries[exam]?.takeIf { it.hasAnyMarks } ?: return@mapNotNull null
            "${exam.labelEn}: ${ReportCalculations.formatPercent(sum.percentage)}% (${sum.grade}, ${sum.resultStatus.label})"
        }
        val scoresLine = if (examParts.isEmpty()) {
            if (isHindi) "अभी कोई अंक दर्ज नहीं हैं।" else "No exam marks recorded yet."
        } else {
            examParts.joinToString(" | ")
        }

        val weakLine = if (progress.failedOrWeakSubjectsLatest.isNotEmpty()) {
            val listText = progress.failedOrWeakSubjectsLatest.joinToString(", ")
            if (isHindi) "\nविशेष ध्यान देने योग्य विषय: $listText" else "\nSubjects needing extra practice: $listText"
        } else ""

        return if (isHindi) {
            "नमस्ते अभिभावक महोदय,\n" +
                "${st.name} (कक्षा ${st.className}-${st.section}, सत्र $session) की परीक्षा प्रगति रिपोर्ट:\n" +
                "$scoresLine$weakLine\n" +
                "सादर,\n$schoolName"
        } else {
            "Dear Parent/Guardian,\n" +
                "Exam progress update for ${st.name} (Class ${st.className}-${st.section}, Session $session):\n" +
                "$scoresLine$weakLine\n" +
                "Regards,\n$schoolName"
        }
    }

    fun buildAnalysisCsv(
        schoolName: String,
        session: String,
        bundle: ResultAnalysisBundle
    ): String {
        val sb = StringBuilder()
        fun q(v: String): String = "\"${v.replace("\"", "\"\"")}\""

        sb.appendLine("School,Session,Scope,Exam,ExpectedEntries,EnteredEntries,MissingEntries")
        sb.appendLine(
            listOf(
                q(schoolName),
                q(session),
                q(bundle.scopeLabel),
                q(bundle.examFilter.labelEn),
                bundle.totalExpectedEntries.toString(),
                bundle.enteredEntries.toString(),
                bundle.missingEntries.toString()
            ).joinToString(",")
        )
        sb.appendLine()

        val ov = bundle.overview
        sb.appendLine("SECTION: CLASS OVERVIEW")
        sb.appendLine("TotalStudents,Appeared,Pass,Fail,Pending,PassPercent,AveragePercent,HighestStudent,HighestPercent,LowestStudent,LowestPercent")
        sb.appendLine(
            listOf(
                ov.totalStudents,
                ov.appearedCount,
                ov.passCount,
                ov.failCount,
                ov.pendingCount,
                ReportCalculations.formatPercent(ov.passPercent),
                ReportCalculations.formatPercent(ov.averagePercent),
                q(ov.highestStudentName),
                ReportCalculations.formatPercent(ov.highestPercent),
                q(ov.lowestStudentName),
                ReportCalculations.formatPercent(ov.lowestPercent)
            ).joinToString(",")
        )
        sb.appendLine()

        sb.appendLine("SECTION: GRADE DISTRIBUTION")
        sb.appendLine("Grade,Count,PercentOfAppeared")
        ov.gradeDistribution.forEach { g ->
            sb.appendLine("${q(g.grade)},${g.count},${ReportCalculations.formatPercent(g.percentOfAppeared)}")
        }
        sb.appendLine()

        sb.appendLine("SECTION: TOPPERS")
        sb.appendLine("Rank,Student,Class,Section,RollNo,AdmNo,Obtained,Max,Percent,Grade,Result,Tied")
        bundle.toppers.forEach { t ->
            sb.appendLine(
                listOf(
                    t.rank.toString(),
                    q(t.student.name),
                    q(t.student.className),
                    q(t.student.section),
                    t.student.rollNo.toString(),
                    q(t.student.admNo),
                    ReportCalculations.formatNumber(t.obtained),
                    t.maxMarks.toString(),
                    ReportCalculations.formatPercent(t.percentage),
                    q(t.grade),
                    q(t.resultStatus.label),
                    if (t.isTied) "YES" else "NO"
                ).joinToString(",")
            )
        }
        sb.appendLine()

        sb.appendLine("SECTION: SUBJECT ANALYSIS (WEAKEST FIRST)")
        sb.appendLine("Subject,SubjectTeacher,Appeared,Pass,Fail,Pending,PassPercent,AvgPercent,AvgMarks,Highest,Lowest")
        bundle.subjectRows.forEach { s ->
            sb.appendLine(
                listOf(
                    q(s.subjectName),
                    q(s.teacherLabel.ifBlank { "-" }),
                    s.appearedCount.toString(),
                    s.passCount.toString(),
                    s.failCount.toString(),
                    s.pendingCount.toString(),
                    ReportCalculations.formatPercent(s.passPercent),
                    ReportCalculations.formatPercent(s.averagePercent),
                    ReportCalculations.formatNumber(s.averageMarks),
                    ReportCalculations.formatNumber(s.highestMarks),
                    ReportCalculations.formatNumber(s.lowestMarks)
                ).joinToString(",")
            )
        }
        sb.appendLine()

        sb.appendLine("SECTION: WEAK STUDENTS")
        sb.appendLine("Student,Class,Section,RollNo,Obtained,Max,Percent,Grade,FailedSubjects,BelowThresholdSubjects")
        bundle.weakStudents.forEach { w ->
            sb.appendLine(
                listOf(
                    q(w.student.name),
                    q(w.student.className),
                    q(w.student.section),
                    w.student.rollNo.toString(),
                    ReportCalculations.formatNumber(w.obtained),
                    w.maxMarks.toString(),
                    ReportCalculations.formatPercent(w.percentage),
                    q(w.grade),
                    q(w.failedSubjects.joinToString("; ")),
                    q(w.lowScoreSubjects.joinToString("; "))
                ).joinToString(",")
            )
        }
        sb.appendLine()

        sb.appendLine("SECTION: CLASS / SECTION COMPARISON")
        sb.appendLine("Group,TotalStudents,Appeared,Pass,Fail,PassPercent,AveragePercent,HighestPercent,LowestPercent")
        bundle.comparisonRows.forEach { c ->
            sb.appendLine(
                listOf(
                    q(c.label),
                    c.totalStudents.toString(),
                    c.appearedCount.toString(),
                    c.passCount.toString(),
                    c.failCount.toString(),
                    ReportCalculations.formatPercent(c.passPercent),
                    ReportCalculations.formatPercent(c.averagePercent),
                    ReportCalculations.formatPercent(c.highestPercent),
                    ReportCalculations.formatPercent(c.lowestPercent)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun classSortIndex(className: String, classOrder: List<String>): Int {
        val idx = classOrder.indexOfFirst { it.equals(className.trim(), ignoreCase = true) }
        if (idx >= 0) return idx
        val defIdx = DefaultSchoolTemplate.allDefaultClasses.indexOfFirst { it.equals(className.trim(), ignoreCase = true) }
        return if (defIdx >= 0) defIdx + 100 else 1000
    }
}
