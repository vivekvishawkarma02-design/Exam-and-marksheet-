package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "schools")
data class SchoolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String = "",
    val mobile: String = "",
    val email: String = "",
    val logoBase64: String = "",
    val active: Boolean = true
)

@Entity(
    tableName = "class_groups",
    foreignKeys = [
        ForeignKey(
            entity = SchoolEntity::class,
            parentColumns = ["id"],
            childColumns = ["schoolId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["schoolId", "sortOrder"])
    ]
)
data class ClassGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schoolId: Long,
    val name: String,
    val shortLabel: String,
    val sortOrder: Int,
    val templateKey: String = ""
)

@Entity(
    tableName = "school_classes",
    foreignKeys = [
        ForeignKey(
            entity = SchoolEntity::class,
            parentColumns = ["id"],
            childColumns = ["schoolId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ClassGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["classGroupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["schoolId", "sortOrder"]),
        Index(value = ["schoolId", "name"], unique = true),
        Index(value = ["classGroupId"])
    ]
)
data class SchoolClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schoolId: Long,
    val classGroupId: Long,
    val name: String,
    val sortOrder: Int
)

/**
 * Global application settings only (session, pass percent, report display flags, backup timestamp).
 * Per-school name, address, mobile, email, logo, and classes live in `schools` and `school_classes`.
 */
@Entity(tableName = "school_settings")
data class SchoolSettingsEntity(
    @PrimaryKey val id: Int = 1,
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
    foreignKeys = [
        ForeignKey(
            entity = SchoolEntity::class,
            parentColumns = ["id"],
            childColumns = ["schoolId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ClassGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["classGroupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["classGroupId", "sortOrder"]),
        Index(value = ["schoolId"])
    ]
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schoolId: Long,
    val classGroupId: Long,
    val name: String,
    val sortOrder: Int
)

@Entity(
    tableName = "students",
    foreignKeys = [
        ForeignKey(
            entity = SchoolEntity::class,
            parentColumns = ["id"],
            childColumns = ["schoolId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["schoolId", "session", "admNo"], unique = true),
        Index(value = ["schoolId", "session", "className", "section", "rollNo"]),
        Index(value = ["classId"])
    ]
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schoolId: Long,
    val classId: Long = 0L,
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
    val isSample: Boolean = false,
    // Phase 9: Extended Student Register & Transport fields (defaults preserve full backward compatibility)
    val secondMobile: String = "",
    val admissionDate: String = "",
    val previousSchool: String = "",
    val siblingGroupId: String = "",
    val usesTransport: Boolean = false,
    val transportRouteName: String = "",
    val transportMonthlyAmount: Int = 0,
    // Phase 11: Optional route & stop linkage (defaults keep 100% Phase 9 compatibility)
    val transportRouteId: String = "",
    val transportStopName: String = "",
    val status: String = "ACTIVE", // "ACTIVE" or "LEFT"
    val leftDate: String = "",
    val leftReason: String = ""
) {
    val isActiveStatus: Boolean
        get() = !status.equals("LEFT", ignoreCase = true)
}

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
