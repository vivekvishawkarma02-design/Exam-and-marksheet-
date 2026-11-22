package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportCardDao {

    // -------------------------------------------------------------------------
    // Schools
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM schools ORDER BY active DESC, id ASC")
    fun observeAllSchools(): Flow<List<SchoolEntity>>

    @Query("SELECT * FROM schools ORDER BY active DESC, id ASC")
    suspend fun getAllSchools(): List<SchoolEntity>

    @Query("SELECT * FROM schools WHERE id = :schoolId LIMIT 1")
    suspend fun getSchoolById(schoolId: Long): SchoolEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchool(school: SchoolEntity): Long

    @Update
    suspend fun updateSchool(school: SchoolEntity)

    @Delete
    suspend fun deleteSchool(school: SchoolEntity)

    @Query("DELETE FROM schools")
    suspend fun deleteAllSchools()

    @Query("SELECT COUNT(*) FROM students WHERE schoolId = :schoolId")
    suspend fun countStudentsInSchool(schoolId: Long): Int

    // -------------------------------------------------------------------------
    // Class Groups (per school)
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM class_groups ORDER BY schoolId ASC, sortOrder ASC, id ASC")
    fun observeAllClassGroups(): Flow<List<ClassGroupEntity>>

    @Query("SELECT * FROM class_groups ORDER BY schoolId ASC, sortOrder ASC, id ASC")
    suspend fun getAllClassGroups(): List<ClassGroupEntity>

    @Query("SELECT * FROM class_groups WHERE schoolId = :schoolId ORDER BY sortOrder ASC, id ASC")
    suspend fun getClassGroupsForSchool(schoolId: Long): List<ClassGroupEntity>

    @Query("SELECT * FROM class_groups WHERE id = :groupId LIMIT 1")
    suspend fun getClassGroupById(groupId: Long): ClassGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassGroup(group: ClassGroupEntity): Long

    @Update
    suspend fun updateClassGroup(group: ClassGroupEntity)

    @Delete
    suspend fun deleteClassGroup(group: ClassGroupEntity)

    @Query("DELETE FROM class_groups")
    suspend fun deleteAllClassGroups()

    // -------------------------------------------------------------------------
    // School Classes (per school)
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM school_classes ORDER BY schoolId ASC, sortOrder ASC, id ASC")
    fun observeAllSchoolClasses(): Flow<List<SchoolClassEntity>>

    @Query("SELECT * FROM school_classes ORDER BY schoolId ASC, sortOrder ASC, id ASC")
    suspend fun getAllSchoolClasses(): List<SchoolClassEntity>

    @Query("SELECT * FROM school_classes WHERE schoolId = :schoolId ORDER BY sortOrder ASC, id ASC")
    suspend fun getClassesForSchool(schoolId: Long): List<SchoolClassEntity>

    @Query("SELECT * FROM school_classes WHERE classGroupId = :groupId ORDER BY sortOrder ASC, id ASC")
    suspend fun getClassesForGroup(groupId: Long): List<SchoolClassEntity>

    @Query("SELECT * FROM school_classes WHERE schoolId = :schoolId AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getSchoolClassByName(schoolId: Long, name: String): SchoolClassEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchoolClass(schoolClass: SchoolClassEntity): Long

    @Update
    suspend fun updateSchoolClass(schoolClass: SchoolClassEntity)

    @Delete
    suspend fun deleteSchoolClass(schoolClass: SchoolClassEntity)

    @Query("DELETE FROM school_classes")
    suspend fun deleteAllSchoolClasses()

    @Query("SELECT COUNT(*) FROM students WHERE schoolId = :schoolId AND (classId = :classId OR LOWER(className) = LOWER(:className))")
    suspend fun countStudentsInClass(schoolId: Long, classId: Long, className: String): Int

    @Query("UPDATE students SET className = :newName, classId = :classId WHERE schoolId = :schoolId AND (classId = :classId OR LOWER(className) = LOWER(:oldName))")
    suspend fun renameStudentsClass(schoolId: Long, classId: Long, oldName: String, newName: String)

    // -------------------------------------------------------------------------
    // Global Settings
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM school_settings WHERE id = 1")
    fun observeSettings(): Flow<SchoolSettingsEntity?>

    @Query("SELECT * FROM school_settings WHERE id = 1")
    suspend fun getSettings(): SchoolSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: SchoolSettingsEntity)

    // -------------------------------------------------------------------------
    // Subjects (per class group)
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM subjects ORDER BY schoolId ASC, classGroupId ASC, sortOrder ASC, id ASC")
    fun observeAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY schoolId ASC, classGroupId ASC, sortOrder ASC, id ASC")
    suspend fun getAllSubjects(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE classGroupId = :classGroupId ORDER BY sortOrder ASC, id ASC")
    suspend fun getSubjectsByGroup(classGroupId: Long): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE schoolId = :schoolId ORDER BY classGroupId ASC, sortOrder ASC, id ASC")
    suspend fun getSubjectsForSchool(schoolId: Long): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>)

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE classGroupId = :classGroupId")
    suspend fun deleteSubjectsForGroup(classGroupId: Long)

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects()

    @Query(
        """
        UPDATE marks SET subjectName = :newName
        WHERE subjectName = :oldName
          AND studentId IN (
            SELECT id FROM students WHERE schoolId = :schoolId AND className IN (:classNames)
          )
        """
    )
    suspend fun renameMarksSubjectForSchoolClasses(
        schoolId: Long,
        oldName: String,
        newName: String,
        classNames: List<String>
    )

    // -------------------------------------------------------------------------
    // Students
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM students ORDER BY schoolId ASC, session DESC, className ASC, section ASC, rollNo ASC")
    fun observeAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY id ASC")
    suspend fun getAllStudents(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId ORDER BY id ASC")
    suspend fun getStudentsForSchool(schoolId: Long): List<StudentEntity>

    @Query("SELECT * FROM students WHERE schoolId = :schoolId AND session = :session AND admNo = :admNo LIMIT 1")
    suspend fun getStudentByAdmNo(schoolId: Long, session: String, admNo: String): StudentEntity?

    @Query("SELECT MAX(rollNo) FROM students WHERE schoolId = :schoolId AND session = :session AND LOWER(className) = LOWER(:className) AND section = :section")
    suspend fun getMaxRollNo(schoolId: Long, session: String, className: String, section: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStudent(student: StudentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudent(student: StudentEntity): Long

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Delete
    suspend fun deleteStudent(student: StudentEntity)

    @Query("DELETE FROM students WHERE isSample = 1")
    suspend fun deleteSampleStudents(): Int

    @Query("SELECT COUNT(*) FROM students WHERE isSample = 1")
    fun observeSampleStudentCount(): Flow<Int>

    @Query("DELETE FROM students")
    suspend fun deleteAllStudents()

    // -------------------------------------------------------------------------
    // Marks
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM marks")
    fun observeAllMarks(): Flow<List<MarkEntity>>

    @Query("SELECT * FROM marks")
    suspend fun getAllMarks(): List<MarkEntity>

    @Query("SELECT * FROM marks WHERE studentId = :studentId AND subjectName = :subjectName AND componentKey = :componentKey LIMIT 1")
    suspend fun getMark(studentId: Long, subjectName: String, componentKey: String): MarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMark(mark: MarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMarks(marks: List<MarkEntity>)

    @Query("DELETE FROM marks WHERE studentId = :studentId AND subjectName = :subjectName AND componentKey = :componentKey")
    suspend fun deleteMark(studentId: Long, subjectName: String, componentKey: String)

    @Query("SELECT COUNT(*) FROM marks WHERE componentKey NOT IN (:validKeys)")
    fun observeLegacyMarksCount(validKeys: List<String>): Flow<Int>

    @Query("DELETE FROM marks WHERE componentKey NOT IN (:validKeys)")
    suspend fun deleteLegacyMarks(validKeys: List<String>): Int

    // -------------------------------------------------------------------------
    // Co-Scholastic
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM co_scholastic")
    fun observeAllCoScholastic(): Flow<List<CoScholasticEntity>>

    @Query("SELECT * FROM co_scholastic")
    suspend fun getAllCoScholastic(): List<CoScholasticEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCoScholastic(entry: CoScholasticEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCoScholasticList(entries: List<CoScholasticEntity>)

    // -------------------------------------------------------------------------
    // Term Info
    // -------------------------------------------------------------------------
    @Query("SELECT * FROM term_info")
    fun observeAllTermInfo(): Flow<List<TermInfoEntity>>

    @Query("SELECT * FROM term_info")
    suspend fun getAllTermInfo(): List<TermInfoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTermInfo(info: TermInfoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTermInfoList(entries: List<TermInfoEntity>)

    // -------------------------------------------------------------------------
    // Multi-school Backup Restore Transactions (v3)
    // -------------------------------------------------------------------------
    @Transaction
    suspend fun replaceAllBackupData(data: FullBackupData) {
        deleteAllStudents() // Cascades to marks, co_scholastic, term_info
        deleteAllSubjects()
        deleteAllSchoolClasses()
        deleteAllClassGroups()
        deleteAllSchools()

        upsertSettings(data.settings.copy(id = 1))

        val schoolIdMap = mutableMapOf<Long, Long>()
        data.schools.forEach { school ->
            val newId = insertSchool(school.copy(id = 0))
            schoolIdMap[school.id] = newId
        }

        val groupIdMap = mutableMapOf<Long, Long>()
        data.classGroups.forEach { grp ->
            val mappedSchoolId = schoolIdMap[grp.schoolId] ?: return@forEach
            val newId = insertClassGroup(grp.copy(id = 0, schoolId = mappedSchoolId))
            groupIdMap[grp.id] = newId
        }

        val classIdMap = mutableMapOf<Long, Long>()
        data.schoolClasses.forEach { sc ->
            val mappedSchoolId = schoolIdMap[sc.schoolId] ?: return@forEach
            val mappedGroupId = groupIdMap[sc.classGroupId] ?: return@forEach
            val newId = insertSchoolClass(sc.copy(id = 0, schoolId = mappedSchoolId, classGroupId = mappedGroupId))
            classIdMap[sc.id] = newId
        }

        val remappedSubjects = data.subjects.mapNotNull { sub ->
            val mappedSchoolId = schoolIdMap[sub.schoolId] ?: return@mapNotNull null
            val mappedGroupId = groupIdMap[sub.classGroupId] ?: return@mapNotNull null
            sub.copy(id = 0, schoolId = mappedSchoolId, classGroupId = mappedGroupId)
        }
        if (remappedSubjects.isNotEmpty()) insertSubjects(remappedSubjects)

        val studentIdMap = mutableMapOf<Long, Long>()
        data.students.forEach { st ->
            val mappedSchoolId = schoolIdMap[st.schoolId] ?: return@forEach
            val mappedClassId = classIdMap[st.classId]
                ?: getSchoolClassByName(mappedSchoolId, st.className)?.id
                ?: 0L
            val newId = upsertStudent(st.copy(id = 0, schoolId = mappedSchoolId, classId = mappedClassId))
            studentIdMap[st.id] = newId
        }

        val remappedMarks = data.marks.mapNotNull { m ->
            val mappedId = studentIdMap[m.studentId] ?: return@mapNotNull null
            m.copy(studentId = mappedId)
        }
        if (remappedMarks.isNotEmpty()) upsertMarks(remappedMarks)

        val remappedCo = data.coScholastic.mapNotNull { c ->
            val mappedId = studentIdMap[c.studentId] ?: return@mapNotNull null
            c.copy(studentId = mappedId)
        }
        if (remappedCo.isNotEmpty()) upsertCoScholasticList(remappedCo)

        val remappedTermInfo = data.termInfos.mapNotNull { t ->
            val mappedId = studentIdMap[t.studentId] ?: return@mapNotNull null
            t.copy(studentId = mappedId)
        }
        if (remappedTermInfo.isNotEmpty()) upsertTermInfoList(remappedTermInfo)
    }

    @Transaction
    suspend fun mergeBackupData(data: FullBackupData) {
        val currentSettings = getSettings()
        if (currentSettings == null) {
            upsertSettings(data.settings.copy(id = 1))
        } else {
            val mergedSessions = (currentSettings.sessionsList() + data.settings.sessionsList()).distinct()
            upsertSettings(currentSettings.copy(sessionsCsv = mergedSessions.joinToString(",")))
        }

        val existingSchools = getAllSchools()
        val schoolIdMap = mutableMapOf<Long, Long>()
        data.schools.forEach { incoming ->
            val existing = existingSchools.firstOrNull { it.name.equals(incoming.name, ignoreCase = true) }
            if (existing != null) {
                schoolIdMap[incoming.id] = existing.id
            } else {
                val newId = insertSchool(incoming.copy(id = 0))
                schoolIdMap[incoming.id] = newId
            }
        }

        val groupIdMap = mutableMapOf<Long, Long>()
        data.classGroups.forEach { incoming ->
            val mappedSchoolId = schoolIdMap[incoming.schoolId] ?: return@forEach
            val existingGroups = getClassGroupsForSchool(mappedSchoolId)
            val match = existingGroups.firstOrNull { it.name.equals(incoming.name, ignoreCase = true) }
            if (match != null) {
                groupIdMap[incoming.id] = match.id
            } else {
                val newId = insertClassGroup(incoming.copy(id = 0, schoolId = mappedSchoolId))
                groupIdMap[incoming.id] = newId
            }
        }

        val classIdMap = mutableMapOf<Long, Long>()
        data.schoolClasses.forEach { incoming ->
            val mappedSchoolId = schoolIdMap[incoming.schoolId] ?: return@forEach
            val mappedGroupId = groupIdMap[incoming.classGroupId] ?: return@forEach
            val existingClass = getSchoolClassByName(mappedSchoolId, incoming.name)
            if (existingClass != null) {
                classIdMap[incoming.id] = existingClass.id
            } else {
                val newId = insertSchoolClass(incoming.copy(id = 0, schoolId = mappedSchoolId, classGroupId = mappedGroupId))
                classIdMap[incoming.id] = newId
            }
        }

        data.subjects.forEach { incoming ->
            val mappedSchoolId = schoolIdMap[incoming.schoolId] ?: return@forEach
            val mappedGroupId = groupIdMap[incoming.classGroupId] ?: return@forEach
            val existingSubs = getSubjectsByGroup(mappedGroupId)
            if (existingSubs.none { it.name.equals(incoming.name, ignoreCase = true) }) {
                insertSubject(incoming.copy(id = 0, schoolId = mappedSchoolId, classGroupId = mappedGroupId))
            }
        }

        val studentIdMap = mutableMapOf<Long, Long>()
        data.students.forEach { incoming ->
            val mappedSchoolId = schoolIdMap[incoming.schoolId] ?: return@forEach
            val mappedClassId = classIdMap[incoming.classId]
                ?: getSchoolClassByName(mappedSchoolId, incoming.className)?.id
                ?: 0L
            val existing = getStudentByAdmNo(mappedSchoolId, incoming.session, incoming.admNo)
            if (existing != null) {
                updateStudent(incoming.copy(id = existing.id, schoolId = mappedSchoolId, classId = mappedClassId))
                studentIdMap[incoming.id] = existing.id
            } else {
                val newId = upsertStudent(incoming.copy(id = 0, schoolId = mappedSchoolId, classId = mappedClassId))
                studentIdMap[incoming.id] = newId
            }
        }

        val remappedMarks = data.marks.mapNotNull { m ->
            val mappedId = studentIdMap[m.studentId] ?: return@mapNotNull null
            m.copy(studentId = mappedId)
        }
        if (remappedMarks.isNotEmpty()) upsertMarks(remappedMarks)

        val remappedCo = data.coScholastic.mapNotNull { c ->
            val mappedId = studentIdMap[c.studentId] ?: return@mapNotNull null
            c.copy(studentId = mappedId)
        }
        if (remappedCo.isNotEmpty()) upsertCoScholasticList(remappedCo)

        val remappedTermInfo = data.termInfos.mapNotNull { t ->
            val mappedId = studentIdMap[t.studentId] ?: return@mapNotNull null
            t.copy(studentId = mappedId)
        }
        if (remappedTermInfo.isNotEmpty()) upsertTermInfoList(remappedTermInfo)
    }
}
