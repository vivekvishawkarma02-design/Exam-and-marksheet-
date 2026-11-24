package com.example.domain

import com.example.data.CoScholasticEntity
import com.example.data.MarkEntity
import com.example.data.StudentEntity
import com.example.data.TermInfoEntity
import com.example.data.cloud.StableDocId
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

// =============================================================================
// 1. TEACHER ACCESS SCOPE (SERVER-VERIFIED PHASE 4 ASSIGNMENT MIRROR)
// =============================================================================
/**
 * Stored at `organisations/{orgId}/teacherScopes/{memberUid}` and synced by Admins / Teachers-editors
 * whenever Phase 4 `classAssignments` or `teachers` change.
 * Enforced in BOTH UI and Firestore Security Rules so teachers can ONLY edit marks for their assigned
 * class-section-subject combinations (and open exams), and ONLY class teachers can edit co-scholastic,
 * attendance, remarks, and promoted-to for their assigned section.
 */
data class TeacherAccessScope(
    val uid: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val assignedClassSectionSubjects: List<String> = emptyList(), // "schoolId|className|section|subjectName"
    val classTeacherSections: List<String> = emptyList(),         // "schoolId|className|section"
    val updatedByUid: String = ""
) {
    fun toFirestoreMap(actorUid: String, updatedAtValue: Any): Map<String, Any> = mapOf(
        "uid" to uid.trim().take(128),
        "teacherId" to teacherId.trim().take(128),
        "teacherName" to teacherName.trim().take(120),
        "assignedClassSectionSubjects" to assignedClassSectionSubjects.distinct().take(200),
        "classTeacherSections" to classTeacherSections.distinct().take(50),
        "updatedByUid" to actorUid.take(128),
        "updatedAt" to updatedAtValue
    )

    fun isClassTeacherOf(schoolId: String, className: String, section: String): Boolean {
        val key = makeClassTeacherSectionKey(schoolId, className, section)
        return classTeacherSections.any { it.equals(key, ignoreCase = true) }
    }

    companion object {
        fun makeSubjectScopeKey(
            schoolId: String,
            className: String,
            section: String,
            subjectName: String
        ): String = "${schoolId.trim()}|${className.trim()}|${section.trim().uppercase()}|${subjectName.trim()}"

        fun makeClassTeacherSectionKey(
            schoolId: String,
            className: String,
            section: String
        ): String = "${schoolId.trim()}|${className.trim()}|${section.trim().uppercase()}"

        fun fromMap(docId: String, raw: Map<*, *>?): TeacherAccessScope? {
            if (raw == null) return null
            val uid = (raw["uid"] as? String)?.trim().orEmpty().ifBlank { docId }
            if (uid.isEmpty()) return null
            val subjs = (raw["assignedClassSectionSubjects"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .orEmpty()
            val ctSecs = (raw["classTeacherSections"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                .orEmpty()
            return TeacherAccessScope(
                uid = uid,
                teacherId = (raw["teacherId"] as? String)?.trim().orEmpty(),
                teacherName = (raw["teacherName"] as? String)?.trim().orEmpty(),
                assignedClassSectionSubjects = subjs,
                classTeacherSections = ctSecs,
                updatedByUid = (raw["updatedByUid"] as? String)?.trim().orEmpty()
            )
        }
    }
}

object Phase7AccessEvaluator {

    val ALL_EXAM_KEYS: List<String> = ExamType.entries.map { it.name }

    /**
     * Derives the authoritative per-member [TeacherAccessScope] from Phase 4 teachers & classAssignments.
     */
    fun buildTeacherScopes(
        teachers: List<TeacherProfile>,
        classAssignments: List<ClassSectionAssignment>
    ): Map<String, TeacherAccessScope> {
        val result = linkedMapOf<String, TeacherAccessScope>()
        for (teacher in teachers) {
            val uid = teacher.linkedMemberUid.trim()
            if (uid.isEmpty()) continue

            val subjectKeys = linkedSetOf<String>()
            val ctKeys = linkedSetOf<String>()

            for (ca in classAssignments) {
                val sec = ca.section.trim().uppercase().ifBlank { "A" }
                if (ca.classTeacherId == teacher.id) {
                    ctKeys.add(TeacherAccessScope.makeClassTeacherSectionKey(ca.schoolId, ca.className, sec))
                }
                for (sa in ca.subjectAssignments) {
                    if (sa.teacherId == teacher.id && sa.subjectName.isNotBlank()) {
                        subjectKeys.add(
                            TeacherAccessScope.makeSubjectScopeKey(ca.schoolId, ca.className, sec, sa.subjectName)
                        )
                    }
                }
            }

            result[uid] = TeacherAccessScope(
                uid = uid,
                teacherId = teacher.id,
                teacherName = teacher.name,
                assignedClassSectionSubjects = subjectKeys.toList(),
                classTeacherSections = ctKeys.toList()
            )
        }
        return result
    }

    /**
     * Builds a map of `"className|SECTION"` -> Class Teacher Name for a specific school
     * (used to print Class Teacher name under "Class Teacher's Sign" when school switch is ON).
     */
    fun buildClassTeacherNamesBySection(
        schoolId: String,
        teachers: List<TeacherProfile>,
        classAssignments: List<ClassSectionAssignment>
    ): Map<String, String> {
        val teacherById = teachers.associateBy { it.id }
        val result = linkedMapOf<String, String>()
        for (ca in classAssignments) {
            if (ca.schoolId != schoolId) continue
            val ct = teacherById[ca.classTeacherId] ?: continue
            if (ct.name.isNotBlank()) {
                val key = "${ca.className.trim()}|${ca.section.trim().uppercase().ifBlank { "A" }}"
                result[key] = ct.name.trim()
            }
        }
        return result
    }

    fun isTeacherRestricted(member: MemberAccessProfile?): Boolean {
        if (!OrgAccessEvaluator.isActive(member)) return true
        return member!!.role == OrgRole.TEACHER
    }

    fun isExamOpenForTeacher(exam: ExamType, openExamKeys: List<String>): Boolean {
        if (openExamKeys.isEmpty()) return false
        return openExamKeys.any { it.equals(exam.name, ignoreCase = true) }
    }

    fun isComponentOpenForTeacher(componentKey: String, openExamKeys: List<String>): Boolean {
        val normalized = openExamKeys.map { it.uppercase(Locale.US) }.toSet()
        return when (componentKey) {
            MarkComponent.T1_UT.key -> "UT1" in normalized || "HALF_YEARLY" in normalized
            MarkComponent.T2_UT.key -> "UT2" in normalized || "ANNUAL" in normalized
            MarkComponent.T1_IA.key, MarkComponent.T1_EXAM.key -> "HALF_YEARLY" in normalized
            MarkComponent.T2_IA.key, MarkComponent.T2_EXAM.key -> "ANNUAL" in normalized
            else -> false
        }
    }

    /**
     * Authoritative Phase 7 marks edit check mirroring `firestore.rules`:
     * - Admins/Owners keep full access.
     * - Non-teacher roles with `marks: edit` keep full access for their allowed schools.
     * - Teachers with `marks: edit` can ONLY edit marks for their Phase 4 assigned
     *   (school, class, section, subject) AND exams opened by admin.
     */
    fun canEditMarks(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolDocId: String,
        className: String,
        section: String,
        subjectName: String,
        openExamKeys: List<String>,
        componentKey: String? = null
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        if (!OrgAccessEvaluator.canEditModule(member, AppModule.MARKS)) return false
        val m = member!!
        if (!isTeacherRestricted(m)) return true

        if (componentKey != null && !isComponentOpenForTeacher(componentKey, openExamKeys)) {
            return false
        }

        val normSec = section.trim().uppercase().ifBlank { "A" }
        if (teacherScope != null) {
            val exactKey = TeacherAccessScope.makeSubjectScopeKey(schoolDocId, className, normSec, subjectName)
            return teacherScope.assignedClassSectionSubjects.any { it.equals(exactKey, ignoreCase = true) }
        }
        // Fallback to Phase 2 legacy assignedClassSubjects ONLY when non-empty
        if (m.assignedClassSubjects.isEmpty()) return false
        val legacyKey = OrgAccessEvaluator.makeTeacherScopeKey(schoolDocId, className, subjectName)
        return m.assignedClassSubjects.any { it.equals(legacyKey, ignoreCase = true) }
    }

    /**
     * Authoritative Phase 7 co-scholastic / attendance / remarks / promoted-to edit check mirroring `firestore.rules`:
     * - Admins/Owners and non-teacher roles with `coScholastic: edit` keep full school-scoped access.
     * - Teachers with `coScholastic: edit` can ONLY edit for section(s) where they are the assigned Class Teacher.
     * - Subject teachers (not Class Teacher of that section) are refused.
     */
    fun canEditCoScholasticOrTermInfo(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolDocId: String,
        className: String,
        section: String
    ): Boolean {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) return false
        if (!OrgAccessEvaluator.canEditModule(member, AppModule.CO_SCHOLASTIC)) return false
        val m = member!!
        if (!isTeacherRestricted(m)) return true

        val scope = teacherScope ?: return false
        val ctKey = TeacherAccessScope.makeClassTeacherSectionKey(schoolDocId, className, section)
        return scope.classTeacherSections.any { it.equals(ctKey, ignoreCase = true) }
    }

    fun assignedSubjectTuplesForTeacher(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolDocId: String
    ): List<Triple<String, String, String>> { // (className, section, subjectName)
        if (member == null || !isTeacherRestricted(member)) return emptyList()
        if (teacherScope != null) {
            return teacherScope.assignedClassSectionSubjects.mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size >= 4 && parts[0] == schoolDocId) {
                    Triple(parts[1], parts[2].uppercase(), parts[3])
                } else null
            }
        }
        return member.assignedClassSubjects.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 3 && parts[0] == schoolDocId) {
                Triple(parts[1], "", parts[2])
            } else null
        }
    }

    fun allowedClassesForMarks(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolDocId: String,
        allSchoolClasses: List<String>
    ): List<String> {
        if (member == null || !isTeacherRestricted(member)) return allSchoolClasses
        val assigned = assignedSubjectTuplesForTeacher(member, teacherScope, schoolDocId)
        val allowedSet = assigned.map { it.first.lowercase(Locale.US) }.toSet()
        return allSchoolClasses.filter { it.lowercase(Locale.US) in allowedSet }
    }

    fun allowedSectionsForMarks(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolDocId: String,
        className: String,
        allSections: List<String>
    ): List<String> {
        if (member == null || !isTeacherRestricted(member)) return allSections
        val assigned = assignedSubjectTuplesForTeacher(member, teacherScope, schoolDocId)
            .filter { it.first.equals(className, ignoreCase = true) }
        if (assigned.any { it.second.isBlank() }) return allSections // legacy class-only scope
        val allowedSet = assigned.map { it.second.uppercase(Locale.US) }.toSet()
        return allSections.filter { it.uppercase(Locale.US) in allowedSet }
    }

    fun allowedSubjectsForMarks(
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?,
        schoolDocId: String,
        className: String,
        section: String,
        allSubjects: List<String>
    ): List<String> {
        if (member == null || !isTeacherRestricted(member)) return allSubjects
        val normSec = section.trim().uppercase()
        val assigned = assignedSubjectTuplesForTeacher(member, teacherScope, schoolDocId)
            .filter {
                it.first.equals(className, ignoreCase = true) &&
                    (it.second.isBlank() || it.second.equals(normSec, ignoreCase = true))
            }
        val allowedSet = assigned.map { it.third.lowercase(Locale.US) }.toSet()
        return allSubjects.filter { it.lowercase(Locale.US) in allowedSet }
    }

    fun classTeacherSectionsForSchool(
        teacherScope: TeacherAccessScope?,
        schoolDocId: String
    ): List<Pair<String, String>> {
        if (teacherScope == null) return emptyList()
        return teacherScope.classTeacherSections.mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 3 && parts[0] == schoolDocId) {
                parts[1] to parts[2].uppercase()
            } else null
        }
    }
}

// =============================================================================
// 2. MARKS PENDING LIST ENGINE & WHATSAPP/SMS REMINDER BUILDER
// =============================================================================
data class SchoolAcademicBundleForPending(
    val schoolDocId: String,
    val schoolName: String,
    val students: List<StudentEntity>,
    val marks: List<MarkEntity>,
    val subjectsByClass: Map<String, List<String>>
)

data class PendingMarksItem(
    val schoolDocId: String,
    val schoolName: String,
    val className: String,
    val section: String,
    val subjectName: String,
    val exam: ExamType,
    val totalStudents: Int,
    val enteredStudents: Int,
    val responsibleTeacherId: String,
    val responsibleTeacherName: String
) {
    val missingStudents: Int get() = (totalStudents - enteredStudents).coerceAtLeast(0)
    val isComplete: Boolean get() = totalStudents > 0 && enteredStudents >= totalStudents
}

data class PendingMarksExamSummary(
    val exam: ExamType,
    val pendingTaskCount: Int,
    val totalMissingStudents: Int
)

object PendingMarksEngine {

    /**
     * Computes incomplete class-section-subject marks entries for each exam (UT1, UT2, Half-Yearly, Annual).
     * A student is counted as "entered" for `(subject, exam)` when ALL components for that exam
     * (e.g. T1_UT for UT1; T1_IA + T1_UT + T1_EXAM for Half-Yearly) have non-blank values.
     */
    fun computePendingItems(
        bundles: List<SchoolAcademicBundleForPending>,
        session: String,
        classAssignments: List<ClassSectionAssignment>,
        teachers: List<TeacherProfile>,
        schoolFilter: String? = null,
        classFilter: String? = null,
        examFilter: ExamType? = null,
        teacherIdFilter: String? = null,
        restrictToTeacherScope: TeacherAccessScope? = null
    ): List<PendingMarksItem> {
        val teachersById = teachers.associateBy { it.id }
        val assignmentByKey = classAssignments.associateBy {
            "${it.schoolId}|${it.className.trim().lowercase(Locale.US)}|${it.section.trim().uppercase(Locale.US)}"
        }
        val scopeSubjectSet = restrictToTeacherScope?.assignedClassSectionSubjects
            ?.map { it.lowercase(Locale.US) }
            ?.toSet()

        val examsToCheck = if (examFilter != null) listOf(examFilter) else ExamType.entries
        val results = mutableListOf<PendingMarksItem>()

        for (bundle in bundles) {
            if (!schoolFilter.isNullOrBlank() && bundle.schoolDocId != schoolFilter) continue

            val sessionStudents = bundle.students.filter { it.session.equals(session, ignoreCase = true) }
            if (sessionStudents.isEmpty()) continue

            // Index non-blank marks: (studentId, subjectLower, componentKey) -> true
            val filledMarkKeys = HashSet<String>(bundle.marks.size * 2)
            for (m in bundle.marks) {
                if (m.rawValue.trim().isNotEmpty()) {
                    filledMarkKeys.add("${m.studentId}|${m.subjectName.trim().lowercase(Locale.US)}|${m.componentKey}")
                }
            }

            val byClassSection = sessionStudents.groupBy {
                it.className.trim() to it.section.trim().uppercase(Locale.US).ifBlank { "A" }
            }.toSortedMap(compareBy<Pair<String, String>>({ it.first }, { it.second }))

            for ((classSec, stuList) in byClassSection) {
                val (cls, sec) = classSec
                if (!classFilter.isNullOrBlank() && !cls.equals(classFilter, ignoreCase = true)) continue

                val assignment = assignmentByKey["${bundle.schoolDocId}|${cls.lowercase(Locale.US)}|$sec"]
                val subjects = bundle.subjectsByClass[cls]
                    ?: bundle.subjectsByClass.entries.firstOrNull { it.key.equals(cls, ignoreCase = true) }?.value
                    ?: assignment?.subjectAssignments?.map { it.subjectName }.orEmpty()

                for (subj in subjects.distinct()) {
                    val subjectKeyLower = "${bundle.schoolDocId}|${cls}|$sec|${subj}".lowercase(Locale.US)
                    if (scopeSubjectSet != null && subjectKeyLower !in scopeSubjectSet) continue

                    val assignedSubj = assignment?.subjectAssignments?.firstOrNull {
                        it.subjectName.equals(subj, ignoreCase = true)
                    }
                    val respTeacherId = assignedSubj?.teacherId.orEmpty()
                    val respTeacherName = teachersById[respTeacherId]?.name?.ifBlank { null }
                        ?: if (respTeacherId.isBlank()) "Unassigned" else respTeacherId

                    if (!teacherIdFilter.isNullOrBlank() && respTeacherId != teacherIdFilter) continue

                    val subjLower = subj.trim().lowercase(Locale.US)
                    for (exam in examsToCheck) {
                        val components = MarkComponent.componentsForExam(exam)
                        var enteredCount = 0
                        for (st in stuList) {
                            val allPresent = components.all { comp ->
                                "${st.id}|$subjLower|${comp.key}" in filledMarkKeys
                            }
                            if (allPresent) enteredCount++
                        }
                        if (enteredCount < stuList.size) {
                            results.add(
                                PendingMarksItem(
                                    schoolDocId = bundle.schoolDocId,
                                    schoolName = bundle.schoolName,
                                    className = cls,
                                    section = sec,
                                    subjectName = subj,
                                    exam = exam,
                                    totalStudents = stuList.size,
                                    enteredStudents = enteredCount,
                                    responsibleTeacherId = respTeacherId,
                                    responsibleTeacherName = respTeacherName
                                )
                            )
                        }
                    }
                }
            }
        }

        return results.sortedWith(
            compareBy<PendingMarksItem>(
                { it.exam.ordinal },
                { it.schoolName.lowercase(Locale.US) },
                { it.className },
                { it.section },
                { it.subjectName.lowercase(Locale.US) }
            )
        )
    }

    fun summarizeByExam(items: List<PendingMarksItem>): List<PendingMarksExamSummary> {
        return ExamType.entries.map { exam ->
            val matched = items.filter { it.exam == exam }
            PendingMarksExamSummary(
                exam = exam,
                pendingTaskCount = matched.size,
                totalMissingStudents = matched.sumOf { it.missingStudents }
            )
        }
    }

    /**
     * Generates a ready-to-share WhatsApp/SMS reminder string:
     * e.g. "Mrs. Sharma: Class 3-A Maths UT1 marks are pending, 12 of 40 entered."
     */
    fun buildReminderWhatsAppText(item: PendingMarksItem): String {
        val teacherPrefix = if (item.responsibleTeacherName.isNotBlank() && item.responsibleTeacherName != "Unassigned") {
            "${item.responsibleTeacherName}: "
        } else {
            ""
        }
        return "${teacherPrefix}Class ${item.className}-${item.section} ${item.subjectName} ${item.exam.label} marks are pending, ${item.enteredStudents} of ${item.totalStudents} entered."
    }
}

// =============================================================================
// 3. TEACHER "MY DAY" HOME ENGINE
// =============================================================================
data class MyDayPeriodItem(
    val schoolId: String,
    val schoolName: String,
    val className: String,
    val section: String,
    val subjectName: String,
    val day: DayOfWeekCode,
    val slotId: String,
    val slotLabel: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val isSubstitution: Boolean = false,
    val replacingTeacherName: String = ""
)

data class TeacherMyDaySummary(
    val teacher: TeacherProfile,
    val dateIso: String,
    val dayOfWeek: DayOfWeekCode,
    val todayRegularPeriods: List<MyDayPeriodItem>,
    val todaySubstitutions: List<MyDayPeriodItem>,
    val weeklyPeriodsByDay: Map<DayOfWeekCode, List<MyDayPeriodItem>>,
    val pendingMarksTasks: List<PendingMarksItem>,
    val classTeacherSections: List<Triple<String, String, String>> // (schoolId, className, section)
)

object TeacherMyDayEngine {

    fun buildTeacherMyDay(
        teacher: TeacherProfile,
        teacherScope: TeacherAccessScope?,
        dateIso: String,
        session: String,
        timetableCells: List<TimetableCell>,
        substitutions: List<SubstitutionRecord>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        schoolNamesById: Map<String, String>,
        pendingMarksForTeacher: List<PendingMarksItem>
    ): TeacherMyDaySummary {
        val day = Phase6DateUtils.dayOfWeekForDate(dateIso) ?: DayOfWeekCode.MON
        val teacherCells = timetableCells.filter {
            it.session.equals(session, ignoreCase = true) && it.teacherId == teacher.id
        }

        val allWeeklyItems = teacherCells.map { cell ->
            val struct = periodStructuresBySchool[cell.schoolId] ?: Phase4Validator.buildDefaultPeriodStructure(cell.schoolId)
            val slot = struct.slotsFor(cell.day).firstOrNull { it.id == cell.slotId }
            MyDayPeriodItem(
                schoolId = cell.schoolId,
                schoolName = schoolNamesById[cell.schoolId] ?: cell.schoolId,
                className = cell.className,
                section = cell.section,
                subjectName = cell.subjectName,
                day = cell.day,
                slotId = cell.slotId,
                slotLabel = slot?.label ?: cell.slotId,
                startMinutes = slot?.startMinutes ?: 480,
                endMinutes = slot?.endMinutes ?: 520,
                isSubstitution = false
            )
        }

        val weeklyByDay = allWeeklyItems
            .groupBy { it.day }
            .mapValues { (_, list) -> list.sortedBy { it.startMinutes } }

        val todayRegular = weeklyByDay[day].orEmpty()

        val todaySubs = substitutions
            .filter { it.date == dateIso && it.substituteTeacherId == teacher.id }
            .map { sub ->
                MyDayPeriodItem(
                    schoolId = sub.schoolId,
                    schoolName = schoolNamesById[sub.schoolId] ?: sub.schoolId,
                    className = sub.className,
                    section = sub.section,
                    subjectName = sub.subjectName,
                    day = sub.day,
                    slotId = sub.slotId,
                    slotLabel = sub.slotLabel.ifBlank { sub.slotId },
                    startMinutes = sub.startMinutes,
                    endMinutes = sub.endMinutes,
                    isSubstitution = true,
                    replacingTeacherName = sub.absentTeacherName
                )
            }
            .sortedBy { it.startMinutes }

        val ctSections = teacherScope?.classTeacherSections.orEmpty().mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 3) Triple(parts[0], parts[1], parts[2]) else null
        }

        return TeacherMyDaySummary(
            teacher = teacher,
            dateIso = dateIso,
            dayOfWeek = day,
            todayRegularPeriods = todayRegular,
            todaySubstitutions = todaySubs,
            weeklyPeriodsByDay = weeklyByDay,
            pendingMarksTasks = pendingMarksForTeacher,
            classTeacherSections = ctSections
        )
    }
}

// =============================================================================
// 4. WORKLOAD REPORT ENGINE (PER SCHOOL & MULTI-SCHOOL + CSV)
// =============================================================================
enum class WorkloadReportPeriodMode(val label: String) {
    WEEK("This Week"),
    MONTH("This Month")
}

data class TeacherClassSubjectLoad(
    val schoolId: String,
    val schoolName: String,
    val className: String,
    val section: String,
    val subjectName: String,
    val periodsPerWeek: Int
)

data class TeacherWorkloadReportRow(
    val teacherId: String,
    val teacherName: String,
    val active: Boolean,
    val schoolIds: List<String>,
    val schoolNames: List<String>,
    val classSubjectBreakdown: List<TeacherClassSubjectLoad>,
    val scheduledPeriodsPerWeek: Int,
    val maxWeeklyCapacity: Int,
    val freePeriodsPerWeek: Int,
    val substitutionsThisWeek: Int,
    val substitutionsThisMonth: Int,
    val totalLoadThisWeek: Int,
    val totalLoadThisMonth: Int,
    val perSchoolScheduledPeriods: Map<String, Int>,
    val perSchoolSubsWeek: Map<String, Int>,
    val perSchoolSubsMonth: Map<String, Int>
)

object WorkloadReportEngine {

    /**
     * Computes full workload analytics per teacher:
     * - Periods/week by class, section & subject (from weekly timetableCells, falling back to Phase 4 assignments if timetable empty for a teacher)
     * - Free periods/week (`(workingDaysNotOff * maxPeriodsPerDay - scheduledPeriodsPerWeek).coerceAtLeast(0)` or based on school structure capacity)
     * - Substitutions taken this week & this month (filtered by [schoolIdFilter] when set, or across ALL schools when null)
     * - Per-school breakdown for multi-school teachers
     */
    fun computeWorkloadReport(
        teachers: List<TeacherProfile>,
        timetableCells: List<TimetableCell>,
        classAssignments: List<ClassSectionAssignment>,
        substitutions: List<SubstitutionRecord>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        schoolNamesById: Map<String, String>,
        session: String,
        referenceDateIso: String,
        schoolIdFilter: String? = null,
        teacherIdFilter: String? = null
    ): List<TeacherWorkloadReportRow> {
        val (weekStart, weekEnd) = Phase6DateUtils.weekDateRangeFor(referenceDateIso)
        val monthPrefix = referenceDateIso.take(7) // "YYYY-MM"

        val sessionCells = timetableCells.filter {
            it.session.equals(session, ignoreCase = true) &&
                (schoolIdFilter.isNullOrBlank() || it.schoolId == schoolIdFilter)
        }
        val filteredAssignments = classAssignments.filter {
            schoolIdFilter.isNullOrBlank() || it.schoolId == schoolIdFilter
        }
        val filteredSubs = substitutions.filter {
            schoolIdFilter.isNullOrBlank() || it.schoolId == schoolIdFilter
        }

        val targetTeachers = teachers.filter { t ->
            val matchesTeacher = teacherIdFilter.isNullOrBlank() || t.id == teacherIdFilter
            val matchesSchool = schoolIdFilter.isNullOrBlank() ||
                schoolIdFilter in t.schoolIds ||
                sessionCells.any { it.teacherId == t.id && it.schoolId == schoolIdFilter } ||
                filteredSubs.any { it.substituteTeacherId == t.id && it.schoolId == schoolIdFilter }
            matchesTeacher && matchesSchool
        }.sortedBy { it.name.lowercase(Locale.US) }

        return targetTeachers.map { teacher ->
            val tCells = sessionCells.filter { it.teacherId == teacher.id }

            // Group scheduled periods by (schoolId, className, section, subjectName)
            val breakdown: List<TeacherClassSubjectLoad> = if (tCells.isNotEmpty()) {
                tCells.groupBy { "${it.schoolId}|${it.className}|${it.section}|${it.subjectName}" }
                    .map { (_, group) ->
                        val sample = group.first()
                        TeacherClassSubjectLoad(
                            schoolId = sample.schoolId,
                            schoolName = schoolNamesById[sample.schoolId] ?: sample.schoolId,
                            className = sample.className,
                            section = sample.section,
                            subjectName = sample.subjectName,
                            periodsPerWeek = group.size
                        )
                    }
                    .sortedWith(compareBy({ it.schoolName.lowercase(Locale.US) }, { it.className }, { it.section }, { it.subjectName }))
            } else {
                // Fallback to Phase 4 class-subject assignments if weekly timetable has not yet placed this teacher
                filteredAssignments.flatMap { ca ->
                    ca.subjectAssignments
                        .filter { it.teacherId == teacher.id && it.periodsPerWeek > 0 }
                        .map { sa ->
                            TeacherClassSubjectLoad(
                                schoolId = ca.schoolId,
                                schoolName = schoolNamesById[ca.schoolId] ?: ca.schoolId,
                                className = ca.className,
                                section = ca.section,
                                subjectName = sa.subjectName,
                                periodsPerWeek = sa.periodsPerWeek
                            )
                        }
                }.sortedWith(compareBy({ it.schoolName.lowercase(Locale.US) }, { it.className }, { it.section }, { it.subjectName }))
            }

            val scheduledPerWeek = breakdown.sumOf { it.periodsPerWeek }

            // Compute available teaching slots per week from primary school's period structure minus teacher off-days
            val primarySchoolId = schoolIdFilter?.takeIf { it.isNotBlank() }
                ?: teacher.schoolIds.firstOrNull()
                ?: breakdown.firstOrNull()?.schoolId
                ?: periodStructuresBySchool.keys.firstOrNull().orEmpty()
            val struct = periodStructuresBySchool[primarySchoolId]
                ?: Phase4Validator.buildDefaultPeriodStructure(primarySchoolId.ifBlank { "default" })

            val availableWorkingDays = struct.workingDays.filter { it !in teacher.weeklyOffDays }
            val maxCapacityFromSchool = availableWorkingDays.sumOf { day ->
                val dayTeachingSlots = struct.slotsFor(day).count { it.type.countsAsTeachingPeriod }
                minOf(dayTeachingSlots, teacher.maxPeriodsPerDay)
            }
            val freePeriods = (maxCapacityFromSchool - scheduledPerWeek).coerceAtLeast(0)

            val subsWeekList = filteredSubs.filter {
                it.substituteTeacherId == teacher.id &&
                    Phase6DateUtils.isDateWithinRange(it.date, weekStart, weekEnd)
            }
            val subsMonthList = filteredSubs.filter {
                it.substituteTeacherId == teacher.id &&
                    it.date.startsWith(monthPrefix)
            }

            val perSchoolScheduled = breakdown
                .groupBy { it.schoolId }
                .mapValues { (_, list) -> list.sumOf { it.periodsPerWeek } }
            val perSchoolSubsWeek = subsWeekList.groupingBy { it.schoolId }.eachCount()
            val perSchoolSubsMonth = subsMonthList.groupingBy { it.schoolId }.eachCount()

            val involvedSchoolIds = (teacher.schoolIds + perSchoolScheduled.keys + perSchoolSubsMonth.keys)
                .filter { it.isNotBlank() && (schoolIdFilter.isNullOrBlank() || it == schoolIdFilter) }
                .distinct()

            TeacherWorkloadReportRow(
                teacherId = teacher.id,
                teacherName = teacher.name,
                active = teacher.active,
                schoolIds = involvedSchoolIds,
                schoolNames = involvedSchoolIds.map { schoolNamesById[it] ?: it },
                classSubjectBreakdown = breakdown,
                scheduledPeriodsPerWeek = scheduledPerWeek,
                maxWeeklyCapacity = maxCapacityFromSchool,
                freePeriodsPerWeek = freePeriods,
                substitutionsThisWeek = subsWeekList.size,
                substitutionsThisMonth = subsMonthList.size,
                totalLoadThisWeek = scheduledPerWeek + subsWeekList.size,
                totalLoadThisMonth = (scheduledPerWeek * 4) + subsMonthList.size,
                perSchoolScheduledPeriods = perSchoolScheduled,
                perSchoolSubsWeek = perSchoolSubsWeek,
                perSchoolSubsMonth = perSchoolSubsMonth
            )
        }
    }

    fun buildWorkloadCsv(
        rows: List<TeacherWorkloadReportRow>,
        periodMode: WorkloadReportPeriodMode,
        referenceDateIso: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Teacher,Schools,Scheduled/Week,Free/Week,Subs This Week,Subs This Month,Total Load (${periodMode.label}),Reference Date,Class-Subject Breakdown")
        for (r in rows) {
            val schools = r.schoolNames.joinToString("; ")
            val breakdown = r.classSubjectBreakdown.joinToString("; ") {
                "${it.className}-${it.section} ${it.subjectName} (${it.periodsPerWeek})"
            }
            val totalLoad = if (periodMode == WorkloadReportPeriodMode.WEEK) r.totalLoadThisWeek else r.totalLoadThisMonth
            sb.appendLine(
                listOf(
                    csvEscape(r.teacherName),
                    csvEscape(schools),
                    r.scheduledPeriodsPerWeek.toString(),
                    r.freePeriodsPerWeek.toString(),
                    r.substitutionsThisWeek.toString(),
                    r.substitutionsThisMonth.toString(),
                    totalLoad.toString(),
                    csvEscape(referenceDateIso),
                    csvEscape(breakdown)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun csvEscape(raw: String): String {
        val needsQuotes = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
        return if (needsQuotes) "\"${raw.replace("\"", "\"\"")}\"" else raw
    }
}

// =============================================================================
// 5. TEACHER ATTENDANCE (AUTO-LINKED TO PHASE 6 APPROVED LEAVE)
// =============================================================================
enum class TeacherAttendanceStatus(val wireValue: String, val label: String, val shortCode: String) {
    PRESENT("PRESENT", "Present", "P"),
    ABSENT("ABSENT", "Absent", "A"),
    ON_LEAVE("ON_LEAVE", "On Leave", "L"),
    HALF_DAY("HALF_DAY", "Half-Day", "HD"),
    UNMARKED("UNMARKED", "Not Marked", "—");

    companion object {
        fun fromRaw(raw: String?): TeacherAttendanceStatus =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: UNMARKED
    }
}

data class TeacherAttendanceRecord(
    val id: String = "",
    val date: String = "",
    val schoolId: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val status: TeacherAttendanceStatus = TeacherAttendanceStatus.PRESENT,
    val note: String = "",
    val linkedLeaveId: String = "",
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false
) {
    fun toFirestoreMap(
        actorUid: String,
        actorName: String,
        editedAtMs: Long,
        createdAtValue: Any,
        updatedAtValue: Any
    ): Map<String, Any> = mapOf(
        "date" to date.trim().take(16),
        "schoolId" to schoolId.trim().take(80),
        "teacherId" to teacherId.trim().take(80),
        "teacherName" to teacherName.trim().take(120),
        "status" to status.wireValue,
        "note" to note.trim().take(240),
        "linkedLeaveId" to linkedLeaveId.trim().take(120),
        "updatedByUid" to actorUid.take(128),
        "updatedByName" to actorName.trim().take(120),
        "clientEditedAtMs" to editedAtMs,
        "createdAt" to createdAtValue,
        "updatedAt" to updatedAtValue
    )

    companion object {
        fun makeDocId(date: String, schoolId: String, teacherId: String): String =
            "tatt_${StableDocId.sanitizeSegment(date)}_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(teacherId)}"

        fun fromMap(id: String, raw: Map<*, *>?, hasPendingWrites: Boolean = false): TeacherAttendanceRecord? {
            if (raw == null) return null
            val date = (raw["date"] as? String)?.trim().orEmpty()
            val schoolId = (raw["schoolId"] as? String)?.trim().orEmpty()
            val teacherId = (raw["teacherId"] as? String)?.trim().orEmpty()
            if (date.isEmpty() || schoolId.isEmpty() || teacherId.isEmpty()) return null
            return TeacherAttendanceRecord(
                id = id,
                date = date,
                schoolId = schoolId,
                teacherId = teacherId,
                teacherName = (raw["teacherName"] as? String)?.trim().orEmpty(),
                status = TeacherAttendanceStatus.fromRaw(raw["status"] as? String),
                note = (raw["note"] as? String)?.trim().orEmpty(),
                linkedLeaveId = (raw["linkedLeaveId"] as? String)?.trim().orEmpty(),
                updatedByUid = (raw["updatedByUid"] as? String)?.trim().orEmpty(),
                updatedByName = (raw["updatedByName"] as? String)?.trim().orEmpty(),
                clientEditedAtMs = (raw["clientEditedAtMs"] as? Number)?.toLong() ?: 0L,
                hasPendingWrites = hasPendingWrites
            )
        }
    }
}

data class TeacherDailyAttendanceRow(
    val teacher: TeacherProfile,
    val date: String,
    val schoolId: String,
    val effectiveStatus: TeacherAttendanceStatus,
    val isAutoFromApprovedLeave: Boolean,
    val approvedLeave: TeacherLeaveRecord?,
    val explicitRecord: TeacherAttendanceRecord?
)

data class TeacherMonthlyAttendanceSummaryRow(
    val teacherId: String,
    val teacherName: String,
    val presentDays: Int,
    val halfDays: Int,
    val onLeaveDays: Int,
    val absentDays: Int,
    val unmarkedWorkingDays: Int,
    val totalWorkingDays: Int
) {
    val attendancePercent: Double
        get() {
            val marked = presentDays + halfDays + onLeaveDays + absentDays
            if (marked <= 0) return 0.0
            val effectivePresent = presentDays + (halfDays * 0.5)
            return (effectivePresent * 100.0) / marked
        }
}

object TeacherAttendanceEngine {

    /**
     * Builds the daily attendance sheet for [schoolId] on [dateIso].
     * Approved leaves from Phase 6 automatically appear as [TeacherAttendanceStatus.ON_LEAVE]
     * (or [TeacherAttendanceStatus.HALF_DAY] if it is a half-day approved leave) unless an explicit
     * override record is stored.
     */
    fun buildDailySheet(
        schoolId: String,
        dateIso: String,
        teachers: List<TeacherProfile>,
        attendanceRecords: List<TeacherAttendanceRecord>,
        approvedLeaves: List<TeacherLeaveRecord>
    ): List<TeacherDailyAttendanceRow> {
        val schoolTeachers = teachers
            .filter { it.active && (it.schoolIds.isEmpty() || schoolId in it.schoolIds) }
            .sortedBy { it.name.lowercase(Locale.US) }

        val recordByTeacher = attendanceRecords
            .filter { it.schoolId == schoolId && it.date == dateIso }
            .associateBy { it.teacherId }

        val approvedLeaveByTeacher = approvedLeaves
            .filter { it.status == LeaveStatus.APPROVED && Phase6DateUtils.isDateWithinRange(dateIso, it.startDate, it.endDate) }
            .associateBy { it.teacherId }

        return schoolTeachers.map { teacher ->
            val explicit = recordByTeacher[teacher.id]
            val leave = approvedLeaveByTeacher[teacher.id]

            val (status, fromLeave) = when {
                leave != null && explicit == null -> {
                    val autoStatus = if (leave.halfDay) TeacherAttendanceStatus.HALF_DAY else TeacherAttendanceStatus.ON_LEAVE
                    autoStatus to true
                }
                leave != null && explicit != null && explicit.status == TeacherAttendanceStatus.ON_LEAVE -> {
                    TeacherAttendanceStatus.ON_LEAVE to true
                }
                explicit != null -> explicit.status to false
                else -> TeacherAttendanceStatus.UNMARKED to false
            }

            TeacherDailyAttendanceRow(
                teacher = teacher,
                date = dateIso,
                schoolId = schoolId,
                effectiveStatus = status,
                isAutoFromApprovedLeave = fromLeave,
                approvedLeave = leave,
                explicitRecord = explicit
            )
        }
    }

    /**
     * Creates a Phase 6 approved single-day [TeacherLeaveRecord] when a teacher is marked Absent
     * on the Attendance sheet and the coordinator taps "Create Leave & Substitutions".
     */
    fun buildApprovedLeaveFromAttendanceAbsence(
        teacher: TeacherProfile,
        dateIso: String,
        actorUid: String,
        actorName: String,
        note: String = "Marked absent on daily attendance sheet"
    ): TeacherLeaveRecord = TeacherLeaveRecord(
        id = "",
        teacherId = teacher.id,
        teacherName = teacher.name,
        startDate = dateIso,
        endDate = dateIso,
        reasonType = LeaveReason.CASUAL_LEAVE,
        reasonNote = note,
        halfDay = false,
        halfDaySlotIds = emptyList(),
        status = LeaveStatus.APPROVED,
        requestedByUid = actorUid,
        requestedByName = actorName,
        approvedByUid = actorUid,
        approvedByName = actorName,
        approvedAtMs = System.currentTimeMillis()
    )

    fun datesInMonth(year: Int, month1Based: Int): List<String> {
        val cal = GregorianCalendar(year, (month1Based - 1).coerceIn(0, 11), 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (1..maxDay).map { day ->
            String.format(Locale.US, "%04d-%02d-%02d", year, month1Based, day)
        }
    }

    fun buildMonthlySummary(
        schoolId: String,
        year: Int,
        month1Based: Int,
        teachers: List<TeacherProfile>,
        periodStructure: SchoolPeriodStructure,
        attendanceRecords: List<TeacherAttendanceRecord>,
        approvedLeaves: List<TeacherLeaveRecord>
    ): List<TeacherMonthlyAttendanceSummaryRow> {
        val allDates = datesInMonth(year, month1Based)
        val workingDaysSet = periodStructure.workingDays.toSet()
        val workingDates = allDates.filter { d ->
            val dow = Phase6DateUtils.dayOfWeekForDate(d)
            dow != null && dow in workingDaysSet
        }

        val schoolTeachers = teachers
            .filter { it.active && (it.schoolIds.isEmpty() || schoolId in it.schoolIds) }
            .sortedBy { it.name.lowercase(Locale.US) }

        return schoolTeachers.map { teacher ->
            var present = 0
            var halfDay = 0
            var onLeave = 0
            var absent = 0
            var unmarked = 0

            for (dateIso in workingDates) {
                val dow = Phase6DateUtils.dayOfWeekForDate(dateIso)
                if (dow != null && dow in teacher.weeklyOffDays) continue

                val explicit = attendanceRecords.firstOrNull {
                    it.schoolId == schoolId && it.date == dateIso && it.teacherId == teacher.id
                }
                val leave = approvedLeaves.firstOrNull {
                    it.teacherId == teacher.id &&
                        it.status == LeaveStatus.APPROVED &&
                        Phase6DateUtils.isDateWithinRange(dateIso, it.startDate, it.endDate)
                }

                val status = when {
                    leave != null && explicit == null ->
                        if (leave.halfDay) TeacherAttendanceStatus.HALF_DAY else TeacherAttendanceStatus.ON_LEAVE
                    explicit != null -> explicit.status
                    else -> TeacherAttendanceStatus.UNMARKED
                }

                when (status) {
                    TeacherAttendanceStatus.PRESENT -> present++
                    TeacherAttendanceStatus.HALF_DAY -> halfDay++
                    TeacherAttendanceStatus.ON_LEAVE -> onLeave++
                    TeacherAttendanceStatus.ABSENT -> absent++
                    TeacherAttendanceStatus.UNMARKED -> unmarked++
                }
            }

            TeacherMonthlyAttendanceSummaryRow(
                teacherId = teacher.id,
                teacherName = teacher.name,
                presentDays = present,
                halfDays = halfDay,
                onLeaveDays = onLeave,
                absentDays = absent,
                unmarkedWorkingDays = unmarked,
                totalWorkingDays = present + halfDay + onLeave + absent + unmarked
            )
        }
    }
}

// =============================================================================
// 6. REPORT CARDS READY CHECK (OPTIONAL PER-SCHOOL PRE-BATCH WARNING)
// =============================================================================
data class StudentReportReadinessIssue(
    val studentId: Long,
    val rollNo: Int,
    val studentName: String,
    val missingMarkSubjects: List<String>,
    val missingAttendance: Boolean,
    val missingCoScholasticAreas: List<String>,
    val missingRemarks: Boolean
) {
    val hasIssues: Boolean
        get() = missingMarkSubjects.isNotEmpty() || missingAttendance || missingCoScholasticAreas.isNotEmpty() || missingRemarks

    fun summaryLine(): String {
        val parts = mutableListOf<String>()
        if (missingMarkSubjects.isNotEmpty()) {
            val preview = missingMarkSubjects.take(3).joinToString(", ") +
                if (missingMarkSubjects.size > 3) " +${missingMarkSubjects.size - 3} more" else ""
            parts.add("Marks: $preview")
        }
        if (missingAttendance) parts.add("Attendance")
        if (missingCoScholasticAreas.isNotEmpty()) parts.add("Co-Scholastic (${missingCoScholasticAreas.size})")
        if (missingRemarks) parts.add("Remarks")
        return parts.joinToString(" • ")
    }
}

data class ClassReportReadinessResult(
    val className: String,
    val section: String,
    val reportType: ReportType,
    val totalStudents: Int,
    val issues: List<StudentReportReadinessIssue>
) {
    val isReady: Boolean get() = issues.isEmpty()
    val readyStudents: Int get() = (totalStudents - issues.size).coerceAtLeast(0)
}

object ReportReadinessEngine {

    /**
     * Checks every student in a class-section before batch PDF/ZIP export.
     * - UT1 / UT2: checks marks for the UT component (`T1_UT` or `T2_UT`).
     * - HALF_YEARLY: checks Term-I marks (`T1_IA`, `T1_UT`, `T1_EXAM`), Term-I attendance, Art & Discipline co-scholastic, and Term-I remarks.
     * - ANNUAL: checks Term-I + Term-II marks, Term-I & Term-II attendance, all 5 co-scholastic areas (Term-II or Term-I), and Term-II remarks.
     */
    fun checkClassReadiness(
        className: String,
        section: String,
        reportType: ReportType,
        students: List<StudentEntity>,
        subjects: List<String>,
        marks: List<MarkEntity>,
        coScholastic: List<CoScholasticEntity>,
        termInfos: List<TermInfoEntity>
    ): ClassReportReadinessResult {
        val marksByStudent = marks.groupBy { it.studentId }
        val coByStudent = coScholastic.groupBy { it.studentId }
        val termByStudent = termInfos.groupBy { it.studentId }

        val requiredComponents = when (reportType) {
            ReportType.UT1 -> listOf(MarkComponent.T1_UT)
            ReportType.UT2 -> listOf(MarkComponent.T2_UT)
            ReportType.HALF_YEARLY -> MarkComponent.term1Components()
            ReportType.ANNUAL -> MarkComponent.term1Components() + MarkComponent.term2Components()
        }

        val issues = mutableListOf<StudentReportReadinessIssue>()

        for (stu in students.sortedBy { it.rollNo }) {
            val stuMarks = marksByStudent[stu.id].orEmpty()
            val filledKeys = stuMarks
                .filter { it.rawValue.trim().isNotEmpty() }
                .map { "${it.subjectName.trim().lowercase(Locale.US)}|${it.componentKey}" }
                .toSet()

            val missingSubjects = subjects.filter { subj ->
                val sl = subj.trim().lowercase(Locale.US)
                requiredComponents.any { comp -> "$sl|${comp.key}" !in filledKeys }
            }

            val stuTerms = termByStudent[stu.id].orEmpty()
            val t1 = stuTerms.firstOrNull { it.termNo == 1 }
            val t2 = stuTerms.firstOrNull { it.termNo == 2 }
            val stuCo = coByStudent[stu.id].orEmpty()

            val missingAttendance = when (reportType) {
                ReportType.UT1, ReportType.UT2 -> false
                ReportType.HALF_YEARLY -> t1 == null || t1.workingDays <= 0
                ReportType.ANNUAL -> (t1 == null || t1.workingDays <= 0) && (t2 == null || t2.workingDays <= 0)
            }

            val missingCo = when (reportType) {
                ReportType.UT1, ReportType.UT2 -> emptyList()
                ReportType.HALF_YEARLY -> {
                    val requiredAreas = listOf(CoScholasticArea.ART_EDUCATION, CoScholasticArea.DISCIPLINE)
                    requiredAreas.filter { area ->
                        stuCo.none { it.termNo == 1 && it.areaCode == area.code && it.grade.trim().isNotEmpty() }
                    }.map { it.shortLabel }
                }
                ReportType.ANNUAL -> {
                    CoScholasticArea.entries.filter { area ->
                        stuCo.none { (it.termNo == 1 || it.termNo == 2) && it.areaCode == area.code && it.grade.trim().isNotEmpty() }
                    }.map { it.shortLabel }
                }
            }

            val missingRemarks = when (reportType) {
                ReportType.UT1, ReportType.UT2 -> false
                ReportType.HALF_YEARLY -> t1?.remarks?.trim().isNullOrEmpty()
                ReportType.ANNUAL -> t2?.remarks?.trim().isNullOrEmpty() && t1?.remarks?.trim().isNullOrEmpty()
            }

            val issue = StudentReportReadinessIssue(
                studentId = stu.id,
                rollNo = stu.rollNo,
                studentName = stu.name,
                missingMarkSubjects = missingSubjects,
                missingAttendance = missingAttendance,
                missingCoScholasticAreas = missingCo,
                missingRemarks = missingRemarks
            )
            if (issue.hasIssues) {
                issues.add(issue)
            }
        }

        return ClassReportReadinessResult(
            className = className,
            section = section,
            reportType = reportType,
            totalStudents = students.size,
            issues = issues
        )
    }
}
