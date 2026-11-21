package com.example.data

import com.example.domain.ClassGroup
import com.example.domain.DefaultSubjects
import com.example.domain.MarkComponent
import com.example.domain.SchoolClass
import kotlinx.coroutines.flow.Flow

data class MarkUndoSnapshot(
    val studentId: Long,
    val studentName: String,
    val subjectName: String,
    val componentKey: String,
    val componentLabel: String,
    val previousRawValue: String
)

class ReportCardRepository(private val dao: ReportCardDao) {

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

    suspend fun saveSettings(settings: SchoolSettingsEntity) {
        dao.upsertSettings(settings)
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

    suspend fun suggestNextRollNo(session: String, className: String, section: String): Int {
        return (dao.getMaxRollNo(session, className, section) ?: 0) + 1
    }

    suspend fun saveStudent(student: StudentEntity): Result<Long> {
        val existing = dao.getStudentByAdmNo(student.session, student.admNo.trim())
        if (existing != null && existing.id != student.id) {
            return Result.failure(
                IllegalArgumentException("Admission No. '${student.admNo}' already exists in session ${student.session}.")
            )
        }
        val normalizedClass = SchoolClass.fromName(student.className).displayName
        val cleaned = student.copy(
            admNo = student.admNo.trim(),
            name = student.name.trim(),
            fatherName = student.fatherName.trim(),
            motherName = student.motherName.trim(),
            dob = student.dob.trim(),
            mobile = student.mobile.trim(),
            address = student.address.trim(),
            className = normalizedClass,
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

    suspend fun getAllSubjects(): List<SubjectEntity> = dao.getAllSubjects()

    suspend fun saveCoScholastic(studentId: Long, termNo: Int, areaCode: String, grade: String) {
        dao.upsertCoScholastic(CoScholasticEntity(studentId, termNo, areaCode, grade))
    }

    suspend fun saveTermInfo(info: TermInfoEntity) {
        dao.upsertTermInfo(info)
    }

    // Subject configuration operations
    suspend fun addSubject(classGroup: Int, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Subject name cannot be empty."))
        val existing = dao.getSubjectsByGroup(classGroup)
        if (existing.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Subject '$trimmed' already exists in this group."))
        }
        val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.insertSubject(SubjectEntity(classGroup = classGroup, name = trimmed, sortOrder = nextOrder))
        return Result.success(Unit)
    }

    suspend fun renameSubject(subject: SubjectEntity, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Subject name cannot be empty."))
        val siblings = dao.getSubjectsByGroup(subject.classGroup)
        if (siblings.any { it.id != subject.id && it.name.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Subject '$trimmed' already exists in this group."))
        }
        val classNames = SchoolClass.entries
            .filter { it.group.id == subject.classGroup }
            .map { it.displayName }
        dao.renameMarksSubjectForClasses(subject.name, trimmed, classNames)
        dao.updateSubject(subject.copy(name = trimmed))
        return Result.success(Unit)
    }

    suspend fun moveSubject(classGroup: Int, subjectId: Long, moveUp: Boolean) {
        val list = dao.getSubjectsByGroup(classGroup).toMutableList()
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

    suspend fun resetSubjectsForGroup(classGroup: Int) {
        dao.deleteSubjectsForGroup(classGroup)
        val defaults = DefaultSubjects.forGroup(ClassGroup.fromId(classGroup))
        val entities = defaults.mapIndexed { idx, name ->
            SubjectEntity(classGroup = classGroup, name = name, sortOrder = idx)
        }
        dao.insertSubjects(entities)
    }

    suspend fun clearSampleData(): Int = dao.deleteSampleStudents()

    suspend fun restoreSampleData() {
        AppDatabase.seedSampleStudents(dao)
    }

    suspend fun getFullBackupSnapshot(): FullBackupData {
        val settings = dao.getSettings() ?: SchoolSettingsEntity()
        return FullBackupData(
            settings = settings,
            subjects = dao.getAllSubjects(),
            students = dao.getAllStudents(),
            marks = dao.getAllMarks(),
            coScholastic = dao.getAllCoScholastic(),
            termInfos = dao.getAllTermInfo()
        )
    }

    suspend fun markBackupCompleted(timestampMs: Long) {
        val settings = dao.getSettings() ?: SchoolSettingsEntity()
        dao.upsertSettings(settings.copy(lastBackupEpochMs = timestampMs))
    }

    suspend fun restoreBackup(data: FullBackupData, replaceAll: Boolean) {
        if (replaceAll) {
            dao.replaceAllBackupData(
                settings = data.settings,
                subjects = data.subjects,
                students = data.students,
                marks = data.marks,
                coScholastic = data.coScholastic,
                termInfos = data.termInfos
            )
        } else {
            dao.mergeBackupData(
                settings = data.settings,
                subjects = data.subjects,
                students = data.students,
                marks = data.marks,
                coScholastic = data.coScholastic,
                termInfos = data.termInfos
            )
        }
    }
}

data class FullBackupData(
    val settings: SchoolSettingsEntity,
    val subjects: List<SubjectEntity>,
    val students: List<StudentEntity>,
    val marks: List<MarkEntity>,
    val coScholastic: List<CoScholasticEntity>,
    val termInfos: List<TermInfoEntity>
)
