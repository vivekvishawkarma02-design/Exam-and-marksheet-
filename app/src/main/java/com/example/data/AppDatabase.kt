package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.domain.CoScholasticArea
import com.example.domain.DefaultSchoolTemplate
import com.example.domain.MarkComponent

@Database(
    entities = [
        SchoolEntity::class,
        ClassGroupEntity::class,
        SchoolClassEntity::class,
        SchoolSettingsEntity::class,
        SubjectEntity::class,
        StudentEntity::class,
        MarkEntity::class,
        CoScholasticEntity::class,
        TermInfoEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportCardDao(): ReportCardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        const val SAMPLE_SCHOOL_NAME = "Sample School"
        const val SAMPLE_SCHOOL_ADDRESS = "123 Education Lane, Model Town"

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "school_report_card.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        suspend fun ensureSeeded(dao: ReportCardDao) {
            if (dao.getSettings() == null) {
                dao.upsertSettings(
                    SchoolSettingsEntity(
                        id = 1,
                        currentSession = "2026-2027",
                        sessionsCsv = "2026-2027",
                        passPercent = 33,
                        showPhotoAndAdmOnAnnual = false,
                        showClassRank = true,
                        lastBackupEpochMs = 0L
                    )
                )
            }

            if (dao.getAllSchools().isEmpty()) {
                val sampleSchoolId = dao.insertSchool(
                    SchoolEntity(
                        name = SAMPLE_SCHOOL_NAME,
                        address = SAMPLE_SCHOOL_ADDRESS,
                        mobile = "",
                        email = "",
                        logoBase64 = "",
                        active = true
                    )
                )
                seedDefaultTemplateForSchool(dao, sampleSchoolId)
                seedSampleStudents(dao, sampleSchoolId)
            }
        }

        /**
         * Seeds a school with the built-in default template:
         * - Nursery–UKG (7 subjects)
         * - 1st–5th (6 subjects)
         * - 6th–8th (8 subjects)
         */
        suspend fun seedDefaultTemplateForSchool(
            dao: ReportCardDao,
            schoolId: Long
        ): Map<String, Long> {
            val groupIdsByKey = mutableMapOf<String, Long>()
            var classOrder = 0
            DefaultSchoolTemplate.groups.forEachIndexed { grpIdx, template ->
                val groupId = dao.insertClassGroup(
                    ClassGroupEntity(
                        schoolId = schoolId,
                        name = template.name,
                        shortLabel = template.shortLabel,
                        sortOrder = grpIdx,
                        templateKey = template.key
                    )
                )
                groupIdsByKey[template.key] = groupId

                template.defaultClasses.forEach { className ->
                    dao.insertSchoolClass(
                        SchoolClassEntity(
                            schoolId = schoolId,
                            classGroupId = groupId,
                            name = className,
                            sortOrder = classOrder++
                        )
                    )
                }

                val subjects = template.defaultSubjects.mapIndexed { subIdx, subName ->
                    SubjectEntity(
                        schoolId = schoolId,
                        classGroupId = groupId,
                        name = subName,
                        sortOrder = subIdx
                    )
                }
                dao.insertSubjects(subjects)
            }
            return groupIdsByKey
        }

        /**
         * Copies all class groups, classes, and subject lists from [sourceSchoolId] to [targetSchoolId].
         */
        suspend fun copyStructureFromSchool(
            dao: ReportCardDao,
            sourceSchoolId: Long,
            targetSchoolId: Long
        ) {
            val sourceGroups = dao.getClassGroupsForSchool(sourceSchoolId)
            if (sourceGroups.isEmpty()) {
                seedDefaultTemplateForSchool(dao, targetSchoolId)
                return
            }
            val groupMap = mutableMapOf<Long, Long>()
            sourceGroups.forEach { srcGroup ->
                val newGroupId = dao.insertClassGroup(
                    srcGroup.copy(id = 0, schoolId = targetSchoolId)
                )
                groupMap[srcGroup.id] = newGroupId

                val srcSubjects = dao.getSubjectsByGroup(srcGroup.id)
                if (srcSubjects.isNotEmpty()) {
                    dao.insertSubjects(
                        srcSubjects.map { sub ->
                            sub.copy(id = 0, schoolId = targetSchoolId, classGroupId = newGroupId)
                        }
                    )
                }
            }

            val sourceClasses = dao.getClassesForSchool(sourceSchoolId)
            sourceClasses.forEach { srcCls ->
                val mappedGroupId = groupMap[srcCls.classGroupId] ?: return@forEach
                dao.insertSchoolClass(
                    srcCls.copy(id = 0, schoolId = targetSchoolId, classGroupId = mappedGroupId)
                )
            }
        }

        suspend fun seedSampleStudents(dao: ReportCardDao, schoolId: Long) {
            val session = dao.getSettings()?.currentSession ?: "2026-2027"
            val classes = dao.getClassesForSchool(schoolId)
            val classByName = classes.associateBy { it.name.lowercase() }
            val orderedClassNames = classes.sortedBy { it.sortOrder }.map { it.name }

            val nurseryClass = classByName["nursery"]
            val secondClass = classByName["2nd"]
            val sixthClass = classByName["6th"]

            val sampleSpecs = listOfNotNull(
                nurseryClass?.let { cls ->
                    Triple(
                        cls,
                        StudentEntity(
                            schoolId = schoolId,
                            classId = cls.id,
                            session = session,
                            admNo = "SMP-26-001",
                            name = "Aarav Yadav (आरव यादव)",
                            fatherName = "Rajesh Kumar Yadav",
                            motherName = "Sunita Devi",
                            dob = "14/05/2021",
                            mobile = "9839100001",
                            address = "Model Town, Sector 2",
                            className = cls.name,
                            section = "A",
                            rollNo = 1,
                            isSample = true
                        ),
                        0
                    )
                },
                secondClass?.let { cls ->
                    Triple(
                        cls,
                        StudentEntity(
                            schoolId = schoolId,
                            classId = cls.id,
                            session = session,
                            admNo = "SMP-26-102",
                            name = "Ananya Verma (अनन्या वर्मा)",
                            fatherName = "Santosh Kumar Verma",
                            motherName = "Meera Verma",
                            dob = "22/08/2018",
                            mobile = "9839100002",
                            address = "Model Town, Sector 4",
                            className = cls.name,
                            section = "A",
                            rollNo = 1,
                            isSample = true
                        ),
                        1
                    )
                },
                sixthClass?.let { cls ->
                    Triple(
                        cls,
                        StudentEntity(
                            schoolId = schoolId,
                            classId = cls.id,
                            session = session,
                            admNo = "SMP-26-201",
                            name = "Rohan Kushwaha",
                            fatherName = "Mahesh Kushwaha",
                            motherName = "Rekha Devi",
                            dob = "09/11/2014",
                            mobile = "9839100003",
                            address = "Model Town, Sector 6",
                            className = cls.name,
                            section = "A",
                            rollNo = 1,
                            isSample = true
                        ),
                        2
                    )
                }
            )

            val marks = mutableListOf<MarkEntity>()
            val coList = mutableListOf<CoScholasticEntity>()
            val infoList = mutableListOf<TermInfoEntity>()

            val class2T1 = listOf(
                Triple(6, 13, 20),
                Triple(6, 12, 18),
                Triple(7, 13, 20),
                Triple(6, 12, 18),
                Triple(6, 11, 18),
                Triple(7, 12, 19)
            )
            val class2T2 = listOf(
                Triple(7, 13, 22),
                Triple(8, 18, 30),
                Triple(8, 18, 30),
                Triple(7, 13, 22),
                Triple(7, 13, 22),
                Triple(7, 14, 23)
            )

            sampleSpecs.forEach { (schoolCls, st, index) ->
                val existing = dao.getStudentByAdmNo(schoolId, st.session, st.admNo)
                val studentId = existing?.id ?: dao.upsertStudent(st)
                val subjects = dao.getSubjectsByGroup(schoolCls.classGroupId).map { it.name }

                subjects.forEachIndexed { subIdx, subject ->
                    if (index == 1 && subIdx < class2T1.size) {
                        val (ia1, ut1, ex1) = class2T1[subIdx]
                        val (ia2, ut2, ex2) = class2T2[subIdx]
                        marks += MarkEntity(studentId, subject, MarkComponent.T1_IA.key, ia1.toString())
                        marks += MarkEntity(studentId, subject, MarkComponent.T1_UT.key, ut1.toString())
                        marks += MarkEntity(studentId, subject, MarkComponent.T1_EXAM.key, ex1.toString())
                        marks += MarkEntity(studentId, subject, MarkComponent.T2_IA.key, ia2.toString())
                        marks += MarkEntity(studentId, subject, MarkComponent.T2_UT.key, ut2.toString())
                        marks += MarkEntity(studentId, subject, MarkComponent.T2_EXAM.key, ex2.toString())
                    } else {
                        val ia1 = (8 + (subIdx % 3)).coerceAtMost(10)
                        val ut1 = (23 + (subIdx % 6)).coerceAtMost(30)
                        val ex1 = (46 + (subIdx * 2 % 12)).coerceAtMost(60)
                        val ia2 = (8 + ((subIdx + 1) % 3)).coerceAtMost(10)
                        val ut2 = (24 + ((subIdx + 1) % 6)).coerceAtMost(30)
                        val ex2 = (48 + (subIdx * 3 % 11)).coerceAtMost(60)

                        marks += MarkEntity(studentId, subject, MarkComponent.T1_IA.key, ia1.toString())
                        marks += MarkEntity(studentId, subject, MarkComponent.T1_UT.key, ut1.toString())
                        marks += MarkEntity(studentId, subject, MarkComponent.T1_EXAM.key, ex1.toString())
                        marks += MarkEntity(studentId, subject, MarkComponent.T2_IA.key, ia2.toString())
                        marks += MarkEntity(studentId, subject, MarkComponent.T2_UT.key, ut2.toString())
                        marks += MarkEntity(studentId, subject, MarkComponent.T2_EXAM.key, ex2.toString())
                    }
                }

                CoScholasticArea.entries.forEachIndexed { areaIdx, area ->
                    val g1 = if ((areaIdx + index) % 2 == 0) "A1" else "A2"
                    val g2 = if ((areaIdx + index) % 3 == 0) "A2" else "A1"
                    coList += CoScholasticEntity(studentId, 1, area.code, g1)
                    coList += CoScholasticEntity(studentId, 2, area.code, g2)
                }

                val promoted = DefaultSchoolTemplate.suggestNextClassName(st.className, orderedClassNames)
                val (p1, w1, p2, w2) = when (index) {
                    0 -> listOf(96, 111, 88, 97)
                    1 -> listOf(84, 111, 82, 97)
                    else -> listOf(102, 111, 90, 97)
                }
                infoList += TermInfoEntity(
                    studentId = studentId,
                    termNo = 1,
                    daysPresent = p1,
                    workingDays = w1,
                    remarks = "Good effort. Keep improving!",
                    promotedTo = "",
                    reportDate = "28/10/26"
                )
                infoList += TermInfoEntity(
                    studentId = studentId,
                    termNo = 2,
                    daysPresent = p2,
                    workingDays = w2,
                    remarks = "Good progress. Promoted to next class.",
                    promotedTo = promoted,
                    reportDate = "25/03/27"
                )
            }

            if (marks.isNotEmpty()) dao.upsertMarks(marks)
            if (coList.isNotEmpty()) dao.upsertCoScholasticList(coList)
            if (infoList.isNotEmpty()) dao.upsertTermInfoList(infoList)
        }
    }
}
