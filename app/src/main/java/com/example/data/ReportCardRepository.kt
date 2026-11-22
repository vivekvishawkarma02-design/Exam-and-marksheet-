package com.example.data

import com.example.domain.DefaultSchoolTemplate
import com.example.domain.DefaultSubjects
import com.example.domain.MarkComponent
import kotlinx.coroutines.flow.Flow

data class MarkUndoSnapshot(
    val studentId: Long,
    val studentName: String,
    val subjectName: String,
    val componentKey: String,
    val componentLabel: String,
    val previousRawValue: String
)

sealed class SchoolTemplateSource {
    data object DefaultTemplate : SchoolTemplateSource()
    data class CopyFromSchool(val sourceSchoolId: Long) : SchoolTemplateSource()
}

data class LegacyBackupSubject(
    val legacyGroupId: Int,
    val name: String,
    val sortOrder: Int
)

data class LegacyBackupStudent(
    val oldId: Long,
    val session: String,
    val admNo: String,
    val name: String,
    val fatherName: String,
    val motherName: String,
    val dob: String,
    val mobile: String,
    val address: String,
    val photoBase64: String,
    val className: String,
    val section: String,
    val rollNo: Int,
    val isSample: Boolean
)

data class LegacySuggestedSchool(
    val suggestedName: String,
    val address: String,
    val mobile: String,
    val email: String,
    val logoBase64: String
)

data class LegacyBackupPayload(
    val settings: SchoolSettingsEntity,
    val suggestedSchools: List<LegacySuggestedSchool>,
    val subjects: List<LegacyBackupSubject>,
    val students: List<LegacyBackupStudent>,
    val marks: List<MarkEntity>,
    val coScholastic: List<CoScholasticEntity>,
    val termInfos: List<TermInfoEntity>
)

sealed class LegacyImportTarget {
    data class CreateNewSchool(
        val name: String,
        val address: String,
        val mobile: String,
        val email: String,
        val logoBase64: String
    ) : LegacyImportTarget()

    data class ExistingSchool(val schoolId: Long) : LegacyImportTarget()
}

data class FullBackupData(
    val settings: SchoolSettingsEntity,
    val schools: List<SchoolEntity>,
    val classGroups: List<ClassGroupEntity>,
    val schoolClasses: List<SchoolClassEntity>,
    val subjects: List<SubjectEntity>,
    val students: List<StudentEntity>,
    val marks: List<MarkEntity>,
    val coScholastic: List<CoScholasticEntity>,
    val termInfos: List<TermInfoEntity>
)

class ReportCardRepository(private val dao: ReportCardDao) {

    val schoolsFlow: Flow<List<SchoolEntity>> = dao.observeAllSchools()
    val classGroupsFlow: Flow<List<ClassGroupEntity>> = dao.observeAllClassGroups()
    val schoolClassesFlow: Flow<List<SchoolClassEntity>> = dao.observeAllSchoolClasses()
    val settingsFlow: Flow<SchoolSettingsEntity?> = dao.observeSettings()
    val subjectsFlow: Flow<List<SubjectEntity>> = dao.observeAllSubjects()
    val studentsFlow: Flow<List<StudentEntity>> = dao.observeAllStudents()
    val marksFlow: Flow<List<MarkEntity>> = dao.observeAllMarks()
    val coScholasticFlow: Flow<List<CoScholasticEntity>> = dao.observeAllCoScholastic()
    val termInfoFlow: Flow<List<TermInfoEntity>> = dao.observeAllTermInfo()
    val sampleStudentCountFlow: Flow<Int> = dao.observeSampleStudentCount()
    val legacyMarksCountFlow: Flow<Int> = dao.observeLegacyMarksCount(MarkComponent.validKeys.toList())

    suspend fun initializeIfNeeded() {
        AppDatabase.ensureSeeded(dao)
    }

    // -------------------------------------------------------------------------
    // Schools CRUD
    // -------------------------------------------------------------------------
    suspend fun getAllSchools(): List<SchoolEntity> = dao.getAllSchools()

    suspend fun createSchool(
        name: String,
        address: String,
        mobile: String,
        email: String,
        logoBase64: String,
        active: Boolean = true,
        templateSource: SchoolTemplateSource = SchoolTemplateSource.DefaultTemplate
    ): Result<Long> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("School name is required."))
        }
        val existing = dao.getAllSchools()
        if (existing.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("A school named '$trimmedName' already exists."))
        }
        val newId = dao.insertSchool(
            SchoolEntity(
                name = trimmedName,
                address = address.trim(),
                mobile = mobile.trim(),
                email = email.trim(),
                logoBase64 = logoBase64,
                active = active
            )
        )
        when (templateSource) {
            is SchoolTemplateSource.DefaultTemplate -> AppDatabase.seedDefaultTemplateForSchool(dao, newId)
            is SchoolTemplateSource.CopyFromSchool -> AppDatabase.copyStructureFromSchool(dao, templateSource.sourceSchoolId, newId)
        }
        return Result.success(newId)
    }

    suspend fun updateSchool(school: SchoolEntity): Result<Unit> {
        val trimmedName = school.name.trim()
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("School name is required."))
        }
        val existing = dao.getAllSchools()
        if (existing.any { it.id != school.id && it.name.equals(trimmedName, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("A school named '$trimmedName' already exists."))
        }
        dao.updateSchool(
            school.copy(
                name = trimmedName,
                address = school.address.trim(),
                mobile = school.mobile.trim(),
                email = school.email.trim()
            )
        )
        return Result.success(Unit)
    }

    suspend fun setSchoolActive(schoolId: Long, active: Boolean): Result<Unit> {
        val school = dao.getSchoolById(schoolId)
            ?: return Result.failure(IllegalArgumentException("School not found."))
        dao.updateSchool(school.copy(active = active))
        return Result.success(Unit)
    }

    /**
     * Deletes a school ONLY if it has zero students.
     */
    suspend fun deleteSchool(schoolId: Long): Result<Unit> {
        val school = dao.getSchoolById(schoolId)
            ?: return Result.failure(IllegalArgumentException("School not found."))
        val studentCount = dao.countStudentsInSchool(schoolId)
        if (studentCount > 0) {
            return Result.failure(
                IllegalStateException(
                    "Cannot delete '${school.name}' because it still has $studentCount student(s). " +
                        "Remove students first or deactivate the school."
                )
            )
        }
        dao.deleteSchool(school)
        return Result.success(Unit)
    }

    // -------------------------------------------------------------------------
    // Per-School Class Groups & Classes CRUD
    // -------------------------------------------------------------------------
    suspend fun getClassGroupsForSchool(schoolId: Long): List<ClassGroupEntity> =
        dao.getClassGroupsForSchool(schoolId)

    suspend fun getClassesForSchool(schoolId: Long): List<SchoolClassEntity> =
        dao.getClassesForSchool(schoolId)

    suspend fun addClassGroup(
        schoolId: Long,
        name: String,
        shortLabel: String,
        initialSubjects: List<String> = emptyList()
    ): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Group name cannot be empty."))
        val existing = dao.getClassGroupsForSchool(schoolId)
        if (existing.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Class group '$trimmed' already exists in this school."))
        }
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val label = shortLabel.trim().ifEmpty { trimmed.take(12) }
        val newGroupId = dao.insertClassGroup(
            ClassGroupEntity(
                schoolId = schoolId,
                name = trimmed,
                shortLabel = label,
                sortOrder = nextOrder,
                templateKey = ""
            )
        )
        val subs = initialSubjects.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (subs.isNotEmpty()) {
            dao.insertSubjects(
                subs.mapIndexed { idx, sName ->
                    SubjectEntity(schoolId = schoolId, classGroupId = newGroupId, name = sName, sortOrder = idx)
                }
            )
        }
        return Result.success(newGroupId)
    }

    suspend fun renameClassGroup(group: ClassGroupEntity, newName: String, newShortLabel: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Group name cannot be empty."))
        val siblings = dao.getClassGroupsForSchool(group.schoolId)
        if (siblings.any { it.id != group.id && it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Class group '$trimmed' already exists."))
        }
        dao.updateClassGroup(
            group.copy(
                name = trimmed,
                shortLabel = newShortLabel.trim().ifEmpty { trimmed.take(12) }
            )
        )
        return Result.success(Unit)
    }

    suspend fun deleteClassGroup(group: ClassGroupEntity): Result<Unit> {
        val attachedClasses = dao.getClassesForGroup(group.id)
        if (attachedClasses.isNotEmpty()) {
            return Result.failure(
                IllegalStateException("Cannot delete '${group.name}' while ${attachedClasses.size} class(es) use it. Reassign those classes first.")
            )
        }
        dao.deleteClassGroup(group)
        return Result.success(Unit)
    }

    suspend fun addSchoolClass(schoolId: Long, classGroupId: Long, name: String): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Class name cannot be empty."))
        val existing = dao.getClassesForSchool(schoolId)
        if (existing.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Class '$trimmed' already exists in this school."))
        }
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val newId = dao.insertSchoolClass(
            SchoolClassEntity(
                schoolId = schoolId,
                classGroupId = classGroupId,
                name = trimmed,
                sortOrder = nextOrder
            )
        )
        return Result.success(newId)
    }

    suspend fun updateSchoolClass(
        schoolClass: SchoolClassEntity,
        newName: String,
        newClassGroupId: Long
    ): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Class name cannot be empty."))
        val siblings = dao.getClassesForSchool(schoolClass.schoolId)
        if (siblings.any { it.id != schoolClass.id && it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Class '$trimmed' already exists in this school."))
        }
        val oldName = schoolClass.name
        dao.updateSchoolClass(schoolClass.copy(name = trimmed, classGroupId = newClassGroupId))
        if (!oldName.equals(trimmed, ignoreCase = false)) {
            dao.renameStudentsClass(schoolClass.schoolId, schoolClass.id, oldName, trimmed)
        }
        return Result.success(Unit)
    }

    suspend fun moveSchoolClass(schoolId: Long, classId: Long, moveUp: Boolean) {
        val list = dao.getClassesForSchool(schoolId).toMutableList()
        val index = list.indexOfFirst { it.id == classId }
        if (index == -1) return
        val target = if (moveUp) index - 1 else index + 1
        if (target !in list.indices) return
        val temp = list[index]
        list[index] = list[target]
        list[target] = temp
        list.forEachIndexed { idx, item ->
            dao.updateSchoolClass(item.copy(sortOrder = idx))
        }
    }

    suspend fun deleteSchoolClass(schoolClass: SchoolClassEntity): Result<Unit> {
        val count = dao.countStudentsInClass(schoolClass.schoolId, schoolClass.id, schoolClass.name)
        if (count > 0) {
            return Result.failure(
                IllegalStateException("Cannot delete class '${schoolClass.name}' because $count student(s) are enrolled in it.")
            )
        }
        dao.deleteSchoolClass(schoolClass)
        return Result.success(Unit)
    }

    // -------------------------------------------------------------------------
    // Global Settings
    // -------------------------------------------------------------------------
    suspend fun saveSettings(settings: SchoolSettingsEntity) {
        dao.upsertSettings(settings.copy(id = 1))
    }

    suspend fun addSession(newSession: String) {
        val current = dao.getSettings() ?: SchoolSettingsEntity()
        val trimmed = newSession.trim()
        if (trimmed.isEmpty()) return
        val updatedList = (current.sessionsList() + trimmed).distinct()
        dao.upsertSettings(
            current.copy(
                currentSession = trimmed,
                sessionsCsv = updatedList.joinToString(",")
            )
        )
    }

    suspend fun deleteLegacyStructureMarks(): Int {
        return dao.deleteLegacyMarks(MarkComponent.validKeys.toList())
    }

    // -------------------------------------------------------------------------
    // Students & Marks
    // -------------------------------------------------------------------------
    suspend fun suggestNextRollNo(schoolId: Long, session: String, className: String, section: String): Int {
        return (dao.getMaxRollNo(schoolId, session, className, section) ?: 0) + 1
    }

    suspend fun saveStudent(student: StudentEntity): Result<Long> {
        val existing = dao.getStudentByAdmNo(student.schoolId, student.session, student.admNo.trim())
        if (existing != null && existing.id != student.id) {
            return Result.failure(
                IllegalArgumentException("Admission No. '${student.admNo}' already exists in this school for session ${student.session}.")
            )
        }
        val schoolClasses = dao.getClassesForSchool(student.schoolId)
        val matchedClass = schoolClasses.firstOrNull { it.id == student.classId && student.classId > 0L }
            ?: schoolClasses.firstOrNull { it.name.equals(student.className.trim(), ignoreCase = true) }
            ?: schoolClasses.firstOrNull {
                it.name.equals(
                    DefaultSchoolTemplate.normalizeClassName(student.className, schoolClasses.map { c -> c.name }),
                    ignoreCase = true
                )
            }

        val resolvedClassName = matchedClass?.name ?: student.className.trim()
        val resolvedClassId = matchedClass?.id ?: student.classId

        val cleaned = student.copy(
            classId = resolvedClassId,
            admNo = student.admNo.trim(),
            name = student.name.trim(),
            fatherName = student.fatherName.trim(),
            motherName = student.motherName.trim(),
            dob = student.dob.trim(),
            mobile = student.mobile.trim(),
            address = student.address.trim(),
            className = resolvedClassName,
            section = student.section.trim().uppercase().ifEmpty { "A" }
        )
        return if (cleaned.id == 0L) {
            Result.success(dao.insertStudent(cleaned))
        } else {
            dao.updateStudent(cleaned)
            Result.success(cleaned.id)
        }
    }

    suspend fun deleteStudent(student: StudentEntity) {
        dao.deleteStudent(student)
    }

    suspend fun saveMarkWithPrevious(
        studentId: Long,
        subjectName: String,
        componentKey: String,
        normalizedValue: String
    ): String {
        val previous = dao.getMark(studentId, subjectName, componentKey)?.rawValue.orEmpty()
        if (normalizedValue.isEmpty()) {
            dao.deleteMark(studentId, subjectName, componentKey)
        } else {
            dao.upsertMark(MarkEntity(studentId, subjectName, componentKey, normalizedValue))
        }
        return previous
    }

    suspend fun saveMarksBatch(marks: List<MarkEntity>) {
        if (marks.isNotEmpty()) {
            dao.upsertMarks(marks)
        }
    }

    suspend fun getAllStudents(): List<StudentEntity> = dao.getAllStudents()

    suspend fun getStudentsForSchool(schoolId: Long): List<StudentEntity> = dao.getStudentsForSchool(schoolId)

    suspend fun getAllSubjects(): List<SubjectEntity> = dao.getAllSubjects()

    suspend fun saveCoScholastic(studentId: Long, termNo: Int, areaCode: String, grade: String) {
        dao.upsertCoScholastic(CoScholasticEntity(studentId, termNo, areaCode, grade))
    }

    suspend fun saveTermInfo(info: TermInfoEntity) {
        dao.upsertTermInfo(info)
    }

    // -------------------------------------------------------------------------
    // Subject Configuration (per school class group)
    // -------------------------------------------------------------------------
    suspend fun addSubject(schoolId: Long, classGroupId: Long, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Subject name cannot be empty."))
        val existing = dao.getSubjectsByGroup(classGroupId)
        if (existing.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Subject '$trimmed' already exists in this group."))
        }
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.insertSubject(
            SubjectEntity(
                schoolId = schoolId,
                classGroupId = classGroupId,
                name = trimmed,
                sortOrder = nextOrder
            )
        )
        return Result.success(Unit)
    }

    suspend fun renameSubject(subject: SubjectEntity, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Subject name cannot be empty."))
        val siblings = dao.getSubjectsByGroup(subject.classGroupId)
        if (siblings.any { it.id != subject.id && it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Subject '$trimmed' already exists in this group."))
        }
        val classNames = dao.getClassesForGroup(subject.classGroupId).map { it.name }
        if (classNames.isNotEmpty()) {
            dao.renameMarksSubjectForSchoolClasses(subject.schoolId, subject.name, trimmed, classNames)
        }
        dao.updateSubject(subject.copy(name = trimmed))
        return Result.success(Unit)
    }

    suspend fun moveSubject(classGroupId: Long, subjectId: Long, moveUp: Boolean) {
        val list = dao.getSubjectsByGroup(classGroupId).toMutableList()
        val index = list.indexOfFirst { it.id == subjectId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in list.indices) return
        val temp = list[index]
        list[index] = list[targetIndex]
        list[targetIndex] = temp
        list.forEachIndexed { idx, item ->
            dao.updateSubject(item.copy(sortOrder = idx))
        }
    }

    suspend fun deleteSubject(subject: SubjectEntity) {
        dao.deleteSubject(subject)
    }

    suspend fun resetSubjectsForGroup(group: ClassGroupEntity): Result<Unit> {
        val defaults = DefaultSubjects.forTemplateKey(group.templateKey)
        if (defaults.isEmpty()) {
            return Result.failure(IllegalStateException("Custom class group '${group.name}' has no built-in default subjects."))
        }
        dao.deleteSubjectsForGroup(group.id)
        val entities = defaults.mapIndexed { idx, name ->
            SubjectEntity(schoolId = group.schoolId, classGroupId = group.id, name = name, sortOrder = idx)
        }
        dao.insertSubjects(entities)
        return Result.success(Unit)
    }

    suspend fun clearSampleData(): Int = dao.deleteSampleStudents()

    suspend fun restoreSampleData(schoolId: Long) {
        AppDatabase.seedSampleStudents(dao, schoolId)
    }

    // -------------------------------------------------------------------------
    // Backup & Restore (v3 All Schools / Single School + Legacy Pre-Phase-1 Migration)
    // -------------------------------------------------------------------------
    suspend fun getFullBackupSnapshot(schoolIdFilter: Long? = null): FullBackupData {
        val settings = dao.getSettings() ?: SchoolSettingsEntity()
        if (schoolIdFilter == null) {
            return FullBackupData(
                settings = settings,
                schools = dao.getAllSchools(),
                classGroups = dao.getAllClassGroups(),
                schoolClasses = dao.getAllSchoolClasses(),
                subjects = dao.getAllSubjects(),
                students = dao.getAllStudents(),
                marks = dao.getAllMarks(),
                coScholastic = dao.getAllCoScholastic(),
                termInfos = dao.getAllTermInfo()
            )
        }
        val school = dao.getSchoolById(schoolIdFilter)
        val students = dao.getStudentsForSchool(schoolIdFilter)
        val studentIds = students.map { it.id }.toSet()
        return FullBackupData(
            settings = settings,
            schools = listOfNotNull(school),
            classGroups = dao.getClassGroupsForSchool(schoolIdFilter),
            schoolClasses = dao.getClassesForSchool(schoolIdFilter),
            subjects = dao.getSubjectsForSchool(schoolIdFilter),
            students = students,
            marks = dao.getAllMarks().filter { it.studentId in studentIds },
            coScholastic = dao.getAllCoScholastic().filter { it.studentId in studentIds },
            termInfos = dao.getAllTermInfo().filter { it.studentId in studentIds }
        )
    }

    suspend fun markBackupCompleted(timestampMs: Long) {
        val settings = dao.getSettings() ?: SchoolSettingsEntity()
        dao.upsertSettings(settings.copy(lastBackupEpochMs = timestampMs))
    }

    suspend fun restoreBackup(data: FullBackupData, replaceAll: Boolean) {
        if (replaceAll) {
            dao.replaceAllBackupData(data)
        } else {
            dao.mergeBackupData(data)
        }
    }

    /**
     * Imports a pre-Phase-1 (v1/v2) backup cleanly into either a newly created school or an
     * existing school, attaching all classes, subjects, students, marks, co-scholastic grades,
     * and term details without losing any data.
     */
    suspend fun importLegacyBackup(
        legacy: LegacyBackupPayload,
        target: LegacyImportTarget
    ): Result<Long> {
        val targetSchoolId = when (target) {
            is LegacyImportTarget.CreateNewSchool -> {
                val trimmed = target.name.trim()
                if (trimmed.isEmpty()) {
                    return Result.failure(IllegalArgumentException("School name cannot be empty."))
                }
                val existing = dao.getAllSchools().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
                if (existing != null) {
                    existing.id
                } else {
                    val newId = dao.insertSchool(
                        SchoolEntity(
                            name = trimmed,
                            address = target.address.trim(),
                            mobile = target.mobile.trim(),
                            email = target.email.trim(),
                            logoBase64 = target.logoBase64,
                            active = true
                        )
                    )
                    AppDatabase.seedDefaultTemplateForSchool(dao, newId)
                    newId
                }
            }
            is LegacyImportTarget.ExistingSchool -> {
                val found = dao.getSchoolById(target.schoolId)
                    ?: return Result.failure(IllegalArgumentException("Target school not found."))
                found.id
            }
        }

        // Merge sessions from legacy settings
        val currentSettings = dao.getSettings() ?: SchoolSettingsEntity()
        val mergedSessions = (currentSettings.sessionsList() + legacy.settings.sessionsList()).distinct()
        dao.upsertSettings(
            currentSettings.copy(
                sessionsCsv = mergedSessions.joinToString(","),
                passPercent = legacy.settings.passPercent.coerceIn(1, 100)
            )
        )

        // Ensure target school has groups corresponding to legacy groups 1, 2, 3
        var schoolGroups = dao.getClassGroupsForSchool(targetSchoolId)
        if (schoolGroups.isEmpty()) {
            AppDatabase.seedDefaultTemplateForSchool(dao, targetSchoolId)
            schoolGroups = dao.getClassGroupsForSchool(targetSchoolId)
        }
        val groupByLegacyId = mapOf(
            1 to (schoolGroups.firstOrNull { it.templateKey == DefaultSchoolTemplate.KEY_PRE_PRIMARY } ?: schoolGroups.first()),
            2 to (schoolGroups.firstOrNull { it.templateKey == DefaultSchoolTemplate.KEY_PRIMARY } ?: schoolGroups.first()),
            3 to (schoolGroups.firstOrNull { it.templateKey == DefaultSchoolTemplate.KEY_MIDDLE } ?: schoolGroups.last())
        )

        // Merge any custom subjects from legacy backup into the corresponding groups
        legacy.subjects.forEach { legacySub ->
            val targetGroup = groupByLegacyId[legacySub.legacyGroupId] ?: groupByLegacyId.getValue(2)
            val existingSubs = dao.getSubjectsByGroup(targetGroup.id)
            if (existingSubs.none { it.name.equals(legacySub.name, ignoreCase = true) }) {
                val nextOrder = (existingSubs.maxOfOrNull { it.sortOrder } ?: -1) + 1
                dao.insertSubject(
                    SubjectEntity(
                        schoolId = targetSchoolId,
                        classGroupId = targetGroup.id,
                        name = legacySub.name,
                        sortOrder = nextOrder
                    )
                )
            }
        }

        // Ensure every student's class exists in the target school, then upsert students & remap children
        val oldToNewStudentId = mutableMapOf<Long, Long>()
        legacy.students.forEach { st ->
            val schoolClasses = dao.getClassesForSchool(targetSchoolId)
            val normalizedClass = DefaultSchoolTemplate.normalizeClassName(st.className, schoolClasses.map { it.name })
            val schoolClass = schoolClasses.firstOrNull { it.name.equals(normalizedClass, ignoreCase = true) }
                ?: run {
                    val inferredKey = DefaultSchoolTemplate.inferDefaultGroupKeyForClass(normalizedClass)
                    val group = schoolGroups.firstOrNull { it.templateKey == inferredKey } ?: schoolGroups.last()
                    val nextOrder = (schoolClasses.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    val createdId = dao.insertSchoolClass(
                        SchoolClassEntity(
                            schoolId = targetSchoolId,
                            classGroupId = group.id,
                            name = normalizedClass,
                            sortOrder = nextOrder
                        )
                    )
                    SchoolClassEntity(createdId, targetSchoolId, group.id, normalizedClass, nextOrder)
                }

            val existing = dao.getStudentByAdmNo(targetSchoolId, st.session, st.admNo)
            val entity = StudentEntity(
                id = existing?.id ?: 0L,
                schoolId = targetSchoolId,
                classId = schoolClass.id,
                session = st.session,
                admNo = st.admNo,
                name = st.name,
                fatherName = st.fatherName,
                motherName = st.motherName,
                dob = st.dob,
                mobile = st.mobile,
                address = st.address,
                photoBase64 = st.photoBase64,
                className = schoolClass.name,
                section = st.section.ifBlank { "A" },
                rollNo = st.rollNo,
                isSample = st.isSample
            )
            val newId = if (existing != null) {
                dao.updateStudent(entity)
                existing.id
            } else {
                dao.upsertStudent(entity)
            }
            oldToNewStudentId[st.oldId] = newId
        }

        val remappedMarks = legacy.marks.mapNotNull { m ->
            val mappedId = oldToNewStudentId[m.studentId] ?: return@mapNotNull null
            m.copy(studentId = mappedId)
        }
        if (remappedMarks.isNotEmpty()) dao.upsertMarks(remappedMarks)

        val remappedCo = legacy.coScholastic.mapNotNull { c ->
            val mappedId = oldToNewStudentId[c.studentId] ?: return@mapNotNull null
            c.copy(studentId = mappedId)
        }
        if (remappedCo.isNotEmpty()) dao.upsertCoScholasticList(remappedCo)

        val remappedTermInfo = legacy.termInfos.mapNotNull { t ->
            val mappedId = oldToNewStudentId[t.studentId] ?: return@mapNotNull null
            t.copy(studentId = mappedId)
        }
        if (remappedTermInfo.isNotEmpty()) dao.upsertTermInfoList(remappedTermInfo)

        return Result.success(targetSchoolId)
    }
}
