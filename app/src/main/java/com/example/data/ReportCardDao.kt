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

    // Settings
    @Query("SELECT * FROM school_settings WHERE id = 1")
    fun observeSettings(): Flow<SchoolSettingsEntity?>

    @Query("SELECT * FROM school_settings WHERE id = 1")
    suspend fun getSettings(): SchoolSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: SchoolSettingsEntity)

    // Subjects
    @Query("SELECT * FROM subjects ORDER BY classGroup ASC, sortOrder ASC, id ASC")
    fun observeAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY classGroup ASC, sortOrder ASC, id ASC")
    suspend fun getAllSubjects(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE classGroup = :classGroup ORDER BY sortOrder ASC, id ASC")
    suspend fun getSubjectsByGroup(classGroup: Int): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>)

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE classGroup = :classGroup")
    suspend fun deleteSubjectsForGroup(classGroup: Int)

    @Query("DELETE FROM subjects")
    suspend fun deleteAllSubjects()

    @Query("UPDATE marks SET subjectName = :newName WHERE subjectName = :oldName AND studentId IN (SELECT id FROM students WHERE className IN (:classNames))")
    suspend fun renameMarksSubjectForClasses(oldName: String, newName: String, classNames: List<String>)

    // Students
    @Query("SELECT * FROM students ORDER BY session DESC, className ASC, section ASC, rollNo ASC")
    fun observeAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY id ASC")
    suspend fun getAllStudents(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE session = :session AND admNo = :admNo LIMIT 1")
    suspend fun getStudentByAdmNo(session: String, admNo: String): StudentEntity?

    @Query("SELECT MAX(rollNo) FROM students WHERE session = :session AND className = :className AND section = :section")
    suspend fun getMaxRollNo(session: String, className: String, section: String): Int?

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

    // Marks
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

    // Legacy Note Book / Subject Enrich / Periodic Test cleanup
    @Query("SELECT COUNT(*) FROM marks WHERE componentKey NOT IN (:validKeys)")
    fun observeLegacyMarksCount(validKeys: List<String>): Flow<Int>

    @Query("DELETE FROM marks WHERE componentKey NOT IN (:validKeys)")
    suspend fun deleteLegacyMarks(validKeys: List<String>): Int

    // Co-Scholastic
    @Query("SELECT * FROM co_scholastic")
    fun observeAllCoScholastic(): Flow<List<CoScholasticEntity>>

    @Query("SELECT * FROM co_scholastic")
    suspend fun getAllCoScholastic(): List<CoScholasticEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCoScholastic(entry: CoScholasticEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCoScholasticList(entries: List<CoScholasticEntity>)

    // Term Info
    @Query("SELECT * FROM term_info")
    fun observeAllTermInfo(): Flow<List<TermInfoEntity>>

    @Query("SELECT * FROM term_info")
    suspend fun getAllTermInfo(): List<TermInfoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTermInfo(info: TermInfoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTermInfoList(entries: List<TermInfoEntity>)

    @Transaction
    suspend fun replaceAllBackupData(
        settings: SchoolSettingsEntity,
        subjects: List<SubjectEntity>,
        students: List<StudentEntity>,
        marks: List<MarkEntity>,
        coScholastic: List<CoScholasticEntity>,
        termInfos: List<TermInfoEntity>
    ) {
        deleteAllStudents() // Cascades to marks, co_scholastic, term_info
        deleteAllSubjects()

        upsertSettings(settings)
        if (subjects.isNotEmpty()) insertSubjects(subjects)

        val oldToNewId = mutableMapOf<Long, Long>()
        students.forEach { st ->
            val newId = upsertStudent(st.copy(id = 0))
            oldToNewId[st.id] = newId
        }

        val remappedMarks = marks.mapNotNull { m ->
            val mappedId = oldToNewId[m.studentId] ?: return@mapNotNull null
            m.copy(studentId = mappedId)
        }
        if (remappedMarks.isNotEmpty()) upsertMarks(remappedMarks)

        val remappedCo = coScholastic.mapNotNull { c ->
            val mappedId = oldToNewId[c.studentId] ?: return@mapNotNull null
            c.copy(studentId = mappedId)
        }
        if (remappedCo.isNotEmpty()) upsertCoScholasticList(remappedCo)

        val remappedTermInfo = termInfos.mapNotNull { t ->
            val mappedId = oldToNewId[t.studentId] ?: return@mapNotNull null
            t.copy(studentId = mappedId)
        }
        if (remappedTermInfo.isNotEmpty()) upsertTermInfoList(remappedTermInfo)
    }

    @Transaction
    suspend fun mergeBackupData(
        settings: SchoolSettingsEntity?,
        subjects: List<SubjectEntity>,
        students: List<StudentEntity>,
        marks: List<MarkEntity>,
        coScholastic: List<CoScholasticEntity>,
        termInfos: List<TermInfoEntity>
    ) {
        if (settings != null && getSettings() == null) {
            upsertSettings(settings)
        }
        val existingSubjects = getAllSubjects()
        if (existingSubjects.isEmpty() && subjects.isNotEmpty()) {
            insertSubjects(subjects.map { it.copy(id = 0) })
        } else {
            val existingKeys = existingSubjects.map { it.classGroup to it.name.uppercase() }.toSet()
            subjects.forEach { sub ->
                if ((sub.classGroup to sub.name.uppercase()) !in existingKeys) {
                    insertSubject(sub.copy(id = 0))
                }
            }
        }

        val oldToNewId = mutableMapOf<Long, Long>()
        students.forEach { st ->
            val existing = getStudentByAdmNo(st.session, st.admNo)
            if (existing != null) {
                updateStudent(st.copy(id = existing.id))
                oldToNewId[st.id] = existing.id
            } else {
                val newId = upsertStudent(st.copy(id = 0))
                oldToNewId[st.id] = newId
            }
        }

        val remappedMarks = marks.mapNotNull { m ->
            val mappedId = oldToNewId[m.studentId] ?: return@mapNotNull null
            m.copy(studentId = mappedId)
        }
        if (remappedMarks.isNotEmpty()) upsertMarks(remappedMarks)

        val remappedCo = coScholastic.mapNotNull { c ->
            val mappedId = oldToNewId[c.studentId] ?: return@mapNotNull null
            c.copy(studentId = mappedId)
        }
        if (remappedCo.isNotEmpty()) upsertCoScholasticList(remappedCo)

        val remappedTermInfo = termInfos.mapNotNull { t ->
            val mappedId = oldToNewId[t.studentId] ?: return@mapNotNull null
            t.copy(studentId = mappedId)
        }
        if (remappedTermInfo.isNotEmpty()) upsertTermInfoList(remappedTermInfo)
    }
}
