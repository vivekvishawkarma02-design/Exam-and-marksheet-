package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.ClassGroup
import com.example.domain.CoScholasticArea
import com.example.domain.DefaultSubjects
import com.example.domain.MarkComponent
import com.example.domain.SchoolClass

@Database(
    entities = [
        SchoolSettingsEntity::class,
        SubjectEntity::class,
        StudentEntity::class,
        MarkEntity::class,
        CoScholasticEntity::class,
        TermInfoEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportCardDao(): ReportCardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val defaultMap = SchoolClass.serializeIdentityMap(SchoolClass.defaultClassIdentityMap())
                // 1. Rebuild school_settings with dual CPS/CPI identities
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `school_settings_new` (
                        `id` INTEGER NOT NULL,
                        `cpsName` TEXT NOT NULL,
                        `cpsAddress` TEXT NOT NULL,
                        `cpsMobile` TEXT NOT NULL,
                        `cpsEmail` TEXT NOT NULL,
                        `cpsLogoBase64` TEXT NOT NULL,
                        `cpsLogoCleared` INTEGER NOT NULL,
                        `cpiName` TEXT NOT NULL,
                        `cpiAddress` TEXT NOT NULL,
                        `cpiMobile` TEXT NOT NULL,
                        `cpiEmail` TEXT NOT NULL,
                        `cpiLogoBase64` TEXT NOT NULL,
                        `cpiLogoCleared` INTEGER NOT NULL,
                        `useSameLogoForBoth` INTEGER NOT NULL,
                        `classIdentityMapCsv` TEXT NOT NULL,
                        `currentSession` TEXT NOT NULL,
                        `sessionsCsv` TEXT NOT NULL,
                        `passPercent` INTEGER NOT NULL,
                        `showPhotoAndAdmOnAnnual` INTEGER NOT NULL,
                        `showClassRank` INTEGER NOT NULL,
                        `lastBackupEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `school_settings_new` (
                        `id`, `cpsName`, `cpsAddress`, `cpsMobile`, `cpsEmail`, `cpsLogoBase64`, `cpsLogoCleared`,
                        `cpiName`, `cpiAddress`, `cpiMobile`, `cpiEmail`, `cpiLogoBase64`, `cpiLogoCleared`,
                        `useSameLogoForBoth`, `classIdentityMapCsv`, `currentSession`, `sessionsCsv`,
                        `passPercent`, `showPhotoAndAdmOnAnnual`, `showClassRank`, `lastBackupEpochMs`
                    )
                    SELECT
                        `id`,
                        'C.P.S. SCHOOL',
                        'Harikaranpur Bijaura Ghazipur (U.P.)',
                        `mobile`,
                        '',
                        '',
                        0,
                        `schoolName`,
                        `address`,
                        `mobile`,
                        '',
                        `logoBase64`,
                        0,
                        0,
                        '$defaultMap',
                        `currentSession`,
                        `sessionsCsv`,
                        `passPercent`,
                        0,
                        `showClassRank`,
                        `lastBackupEpochMs`
                    FROM `school_settings`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `school_settings`")
                db.execSQL("ALTER TABLE `school_settings_new` RENAME TO `school_settings`")

                // 2. Remove old term_config (Scheme A/B) table
                db.execSQL("DROP TABLE IF EXISTS `term_config`")

                // 3. Migrate Roman class names (I..VIII) to ordinal (1st..8th)
                val classRenames = listOf(
                    "I" to "1st", "II" to "2nd", "III" to "3rd", "IV" to "4th",
                    "V" to "5th", "VI" to "6th", "VII" to "7th", "VIII" to "8th"
                )
                classRenames.forEach { (oldCls, newCls) ->
                    db.execSQL("UPDATE `students` SET `className` = '$newCls' WHERE `className` = '$oldCls'")
                }

                // 4. Update Group 2 subject defaults if still using old names
                db.execSQL("UPDATE `subjects` SET `name` = 'MATH' WHERE `classGroup` = 2 AND `name` = 'MATHS'")
                db.execSQL("UPDATE `subjects` SET `name` = 'SCIENCE' WHERE `classGroup` = 2 AND `name` = 'SCIENCE / EVS'")

                // 5. Migrate valid Term-I 10/30/60 keys cleanly; leave legacy NB/SE/PT/80 keys so UI can show the one-time warning before deleting them
                db.execSQL("UPDATE OR REPLACE `marks` SET `componentKey` = 'T1_IA' WHERE `componentKey` = 'T1_A_IA'")
                db.execSQL("UPDATE OR REPLACE `marks` SET `componentKey` = 'T1_UT' WHERE `componentKey` = 'T1_A_UT1'")
                db.execSQL("UPDATE OR REPLACE `marks` SET `componentKey` = 'T1_EXAM' WHERE `componentKey` = 'T1_A_HY'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cpi_report_card.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        suspend fun ensureSeeded(dao: ReportCardDao) {
            if (dao.getSettings() == null) {
                dao.upsertSettings(
                    SchoolSettingsEntity(
                        id = 1,
                        cpsName = "C.P.S. SCHOOL",
                        cpsAddress = "Harikaranpur Bijaura Ghazipur (U.P.)",
                        cpsMobile = "",
                        cpsEmail = "",
                        cpiName = "C.P.I. SCHOOL",
                        cpiAddress = "Harikaranpur Bijaura Ghazipur (U.P.)",
                        cpiMobile = "",
                        cpiEmail = "",
                        useSameLogoForBoth = false,
                        classIdentityMapCsv = SchoolClass.serializeIdentityMap(SchoolClass.defaultClassIdentityMap()),
                        currentSession = "2026-2027",
                        sessionsCsv = "2026-2027",
                        passPercent = 33,
                        showPhotoAndAdmOnAnnual = false,
                        showClassRank = true,
                        lastBackupEpochMs = 0L
                    )
                )
            }

            if (dao.getAllSubjects().isEmpty()) {
                seedDefaultSubjects(dao)
            }

            if (dao.getAllStudents().isEmpty()) {
                seedSampleStudents(dao)
            }
        }

        suspend fun seedDefaultSubjects(dao: ReportCardDao) {
            val all = mutableListOf<SubjectEntity>()
            ClassGroup.entries.forEach { group ->
                DefaultSubjects.forGroup(group).forEachIndexed { idx, name ->
                    all.add(SubjectEntity(classGroup = group.id, name = name, sortOrder = idx))
                }
            }
            dao.insertSubjects(all)
        }

        suspend fun seedSampleStudents(dao: ReportCardDao) {
            val session = dao.getSettings()?.currentSession ?: "2026-2027"
            val sampleList = listOf(
                // Group 1: Nursery (C.P.S. SCHOOL, 7 subjects)
                StudentEntity(
                    session = session,
                    admNo = "CPS-26-001",
                    name = "Aarav Yadav (आरव यादव)",
                    fatherName = "Rajesh Kumar Yadav",
                    motherName = "Sunita Devi",
                    dob = "14/05/2021",
                    mobile = "9839100001",
                    address = "Harikaranpur Bijaura, Ghazipur (U.P.)",
                    className = "Nursery",
                    section = "A",
                    rollNo = 1,
                    isSample = true
                ),
                // Group 2: 2nd (C.P.S. SCHOOL, 6 subjects — exact real-world example: 224/600 + 282/600 = 506/1200, 42.17% C2 PASS, Attendance 84/111 + 82/97 = 166/208)
                StudentEntity(
                    session = session,
                    admNo = "CPS-26-102",
                    name = "Ananya Verma (अनन्या वर्मा)",
                    fatherName = "Santosh Kumar Verma",
                    motherName = "Meera Verma",
                    dob = "22/08/2018",
                    mobile = "9839100002",
                    address = "Bijaura Bazar, Ghazipur (U.P.)",
                    className = "2nd",
                    section = "A",
                    rollNo = 1,
                    isSample = true
                ),
                // Group 3: 6th (C.P.I. SCHOOL, 8 subjects)
                StudentEntity(
                    session = session,
                    admNo = "CPI-26-201",
                    name = "Rohan Kushwaha",
                    fatherName = "Mahesh Kushwaha",
                    motherName = "Rekha Devi",
                    dob = "09/11/2014",
                    mobile = "9839100003",
                    address = "Harikaranpur, Ghazipur (U.P.)",
                    className = "6th",
                    section = "A",
                    rollNo = 1,
                    isSample = true
                )
            )

            val marks = mutableListOf<MarkEntity>()
            val coList = mutableListOf<CoScholasticEntity>()
            val infoList = mutableListOf<TermInfoEntity>()

            // Exact Term-I and Term-II splits for Class 2nd real example:
            // Term-I totals: 39, 36, 40, 36, 35, 38 (sum = 224/600)
            // Term-II totals: 42, 56, 56, 42, 42, 44 (sum = 282/600)
            val class2T1 = listOf(
                Triple(6, 13, 20), // ENGLISH = 39
                Triple(6, 12, 18), // HINDI = 36
                Triple(7, 13, 20), // MATH = 40
                Triple(6, 12, 18), // SCIENCE = 36
                Triple(6, 11, 18), // COMPUTER = 35
                Triple(7, 12, 19)  // G.K. = 38
            )
            val class2T2 = listOf(
                Triple(7, 13, 22), // ENGLISH = 42
                Triple(8, 18, 30), // HINDI = 56
                Triple(8, 18, 30), // MATH = 56
                Triple(7, 13, 22), // SCIENCE = 42
                Triple(7, 13, 22), // COMPUTER = 42
                Triple(7, 14, 23)  // G.K. = 44
            )

            sampleList.forEachIndexed { index, st ->
                val existing = dao.getStudentByAdmNo(st.session, st.admNo)
                val studentId = existing?.id ?: dao.upsertStudent(st)

                val group = SchoolClass.fromName(st.className).group
                val subjects = dao.getSubjectsByGroup(group.id).map { it.name }
                    .ifEmpty { DefaultSubjects.forGroup(group) }

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

                // Co-scholastic grades (A1 / A2 matching real printed cards)
                CoScholasticArea.entries.forEachIndexed { areaIdx, area ->
                    val g1 = if ((areaIdx + index) % 2 == 0) "A1" else "A2"
                    val g2 = if ((areaIdx + index) % 3 == 0) "A2" else "A1"
                    coList += CoScholasticEntity(studentId, 1, area.code, g1)
                    coList += CoScholasticEntity(studentId, 2, area.code, g2)
                }

                val promoted = SchoolClass.fromName(st.className).nextClassName()
                val (p1, w1, p2, w2) = when (index) {
                    0 -> listOf(96, 111, 88, 97)
                    1 -> listOf(84, 111, 82, 97) // Exact example: 84/111 + 82/97 = 166/208
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

            dao.upsertMarks(marks)
            dao.upsertCoScholasticList(coList)
            dao.upsertTermInfoList(infoList)
        }
    }
}
