package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.SchoolClass

@Entity(tableName = "school_settings")
data class SchoolSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val cpsName: String = "C.P.S. SCHOOL",
    val cpsAddress: String = "Harikaranpur Bijaura Ghazipur (U.P.)",
    val cpsMobile: String = "",
    val cpsEmail: String = "",
    val cpsLogoBase64: String = "",
    val cpsLogoCleared: Boolean = false,
    val cpiName: String = "C.P.I. SCHOOL",
    val cpiAddress: String = "Harikaranpur Bijaura Ghazipur (U.P.)",
    val cpiMobile: String = "",
    val cpiEmail: String = "",
    val cpiLogoBase64: String = "",
    val cpiLogoCleared: Boolean = false,
    val useSameLogoForBoth: Boolean = false,
    val classIdentityMapCsv: String = SchoolClass.serializeIdentityMap(SchoolClass.defaultClassIdentityMap()),
    val currentSession: String = "2026-2027",
    val sessionsCsv: String = "2026-2027",
    val passPercent: Int = 33,
    val showPhotoAndAdmOnAnnual: Boolean = false,
    val showClassRank: Boolean = true,
    val lastBackupEpochMs: Long = 0L
) {
    fun sessionsList(): List<String> =
        sessionsCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .ifEmpty { listOf(currentSession) }
}

@Entity(
    tableName = "subjects",
    indices = [Index(value = ["classGroup", "sortOrder"])]
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classGroup: Int,
    val name: String,
    val sortOrder: Int
)

@Entity(
    tableName = "students",
    indices = [
        Index(value = ["session", "admNo"], unique = true),
        Index(value = ["session", "className", "section", "rollNo"])
    ]
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session: String,
    val admNo: String,
    val name: String,
    val fatherName: String,
    val motherName: String,
    val dob: String,
    val mobile: String,
    val address: String,
    val photoBase64: String = "",
    val className: String,
    val section: String,
    val rollNo: Int,
    val isSample: Boolean = false
)

@Entity(
    tableName = "marks",
    primaryKeys = ["studentId", "subjectName", "componentKey"],
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["studentId"])]
)
data class MarkEntity(
    val studentId: Long,
    val subjectName: String,
    val componentKey: String,
    val rawValue: String
)

@Entity(
    tableName = "co_scholastic",
    primaryKeys = ["studentId", "termNo", "areaCode"],
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["studentId"])]
)
data class CoScholasticEntity(
    val studentId: Long,
    val termNo: Int,
    val areaCode: String,
    val grade: String
)

@Entity(
    tableName = "term_info",
    primaryKeys = ["studentId", "termNo"],
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["studentId"])]
)
data class TermInfoEntity(
    val studentId: Long,
    val termNo: Int,
    val daysPresent: Int = 0,
    val workingDays: Int = 0,
    val remarks: String = "",
    val promotedTo: String = "",
    val reportDate: String = ""
)
