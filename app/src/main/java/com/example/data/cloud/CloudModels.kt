package com.example.data.cloud

import androidx.annotation.Keep
import com.example.data.ClassGroupEntity
import com.example.data.CoScholasticEntity
import com.example.data.MarkEntity
import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.SchoolSettingsEntity
import com.example.data.StudentEntity
import com.example.data.SubjectEntity
import com.example.data.TermInfoEntity
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgMembershipRef
import com.example.domain.OrgRole
import com.example.domain.Phase7AccessEvaluator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudPendingRequestRef(
    val orgId: String = "",
    val orgName: String = "",
    val orgCode: String = "",
    val requestedRole: String = OrgRole.TEACHER.name,
    val status: String = "pending"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "orgName" to orgName,
        "orgCode" to orgCode,
        "requestedRole" to requestedRole,
        "status" to status
    )

    companion object {
        fun fromMap(map: Map<*, *>?): CloudPendingRequestRef? {
            if (map == null) return null
            val orgId = (map["orgId"] as? String).orEmpty()
            if (orgId.isBlank()) return null
            return CloudPendingRequestRef(
                orgId = orgId,
                orgName = (map["orgName"] as? String).orEmpty(),
                orgCode = (map["orgCode"] as? String).orEmpty(),
                requestedRole = (map["requestedRole"] as? String).orEmpty().ifBlank { OrgRole.TEACHER.name },
                status = (map["status"] as? String).orEmpty().ifBlank { "pending" }
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class CloudMembershipEntry(
    val orgId: String = "",
    val orgName: String = "",
    val orgCode: String = "",
    val role: String = OrgRole.TEACHER.name,
    val status: String = MemberStatus.ACTIVE.wireValue
) {
    fun toDomain(): OrgMembershipRef = OrgMembershipRef(
        orgId = orgId,
        orgName = orgName,
        orgCode = orgCode,
        role = OrgRole.fromRaw(role),
        status = MemberStatus.fromRaw(status)
    )

    fun toMap(): Map<String, Any> = mapOf(
        "orgId" to orgId,
        "orgName" to orgName,
        "orgCode" to orgCode,
        "role" to role,
        "status" to status
    )

    companion object {
        fun fromMap(map: Map<*, *>?): CloudMembershipEntry? {
            if (map == null) return null
            val orgId = (map["orgId"] as? String).orEmpty()
            if (orgId.isBlank()) return null
            return CloudMembershipEntry(
                orgId = orgId,
                orgName = (map["orgName"] as? String).orEmpty(),
                orgCode = (map["orgCode"] as? String).orEmpty(),
                role = (map["role"] as? String).orEmpty().ifBlank { OrgRole.OTHER.name },
                status = (map["status"] as? String).orEmpty().ifBlank { MemberStatus.ACTIVE.wireValue }
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class CloudUserAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val memberships: List<CloudMembershipEntry> = emptyList(),
    val pendingRequests: List<CloudPendingRequestRef> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudOrgCodeLookup(
    @DocumentId val code: String = "",
    val orgId: String = "",
    val name: String = "",
    val ownerUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudOrgSettings(
    val currentSession: String = "2026-2027",
    val sessionsCsv: String = "2026-2027",
    val passPercent: Long = 33L,
    val showPhotoAndAdmOnAnnual: Boolean = false,
    val showClassRank: Boolean = true,
    val lastBackupEpochMs: Long = 0L
) {
    fun toEntity(): SchoolSettingsEntity = SchoolSettingsEntity(
        id = 1,
        currentSession = currentSession.ifBlank { "2026-2027" },
        sessionsCsv = sessionsCsv.ifBlank { currentSession.ifBlank { "2026-2027" } },
        passPercent = passPercent.toInt().coerceIn(1, 100),
        showPhotoAndAdmOnAnnual = showPhotoAndAdmOnAnnual,
        showClassRank = showClassRank,
        lastBackupEpochMs = lastBackupEpochMs.coerceAtLeast(0L)
    )

    fun toMap(): Map<String, Any> = mapOf(
        "currentSession" to currentSession.ifBlank { "2026-2027" },
        "sessionsCsv" to sessionsCsv.ifBlank { currentSession.ifBlank { "2026-2027" } },
        "passPercent" to passPercent.coerceIn(1L, 100L),
        "showPhotoAndAdmOnAnnual" to showPhotoAndAdmOnAnnual,
        "showClassRank" to showClassRank,
        "lastBackupEpochMs" to lastBackupEpochMs.coerceAtLeast(0L)
    )

    companion object {
        fun fromEntity(e: SchoolSettingsEntity): CloudOrgSettings = CloudOrgSettings(
            currentSession = e.currentSession,
            sessionsCsv = e.sessionsCsv,
            passPercent = e.passPercent.toLong(),
            showPhotoAndAdmOnAnnual = e.showPhotoAndAdmOnAnnual,
            showClassRank = e.showClassRank,
            lastBackupEpochMs = e.lastBackupEpochMs
        )

        fun fromMap(map: Map<*, *>?): CloudOrgSettings {
            if (map == null) return CloudOrgSettings()
            return CloudOrgSettings(
                currentSession = (map["currentSession"] as? String).orEmpty().ifBlank { "2026-2027" },
                sessionsCsv = (map["sessionsCsv"] as? String).orEmpty().ifBlank { "2026-2027" },
                passPercent = (map["passPercent"] as? Number)?.toLong() ?: 33L,
                showPhotoAndAdmOnAnnual = (map["showPhotoAndAdmOnAnnual"] as? Boolean) ?: false,
                showClassRank = (map["showClassRank"] as? Boolean) ?: true,
                lastBackupEpochMs = (map["lastBackupEpochMs"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class CloudOrganisation(
    @DocumentId val id: String = "",
    val name: String = "",
    val code: String = "",
    val ownerUid: String = "",
    val contactMobile: String = "",
    val contactEmail: String = "",
    val settings: CloudOrgSettings = CloudOrgSettings(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val mobile: String = "",
    val role: String = OrgRole.TEACHER.name,
    val status: String = MemberStatus.ACTIVE.wireValue,
    val allSchools: Boolean = false,
    val schools: List<String> = emptyList(),
    val permissions: Map<String, String> = MemberPermissions.presetFor(OrgRole.TEACHER).toFirestoreMap(),
    val teacherProfileCompleted: Boolean = false,
    val teacherPhotoBase64: String = "",
    val requestedSubjects: List<String> = emptyList(),
    val requestedClasses: List<String> = emptyList(),
    val assignedClassSubjects: List<String> = emptyList(),
    val inviteCodeUsed: String? = null,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val orgRole: OrgRole get() = OrgRole.fromRaw(role)
    val memberStatus: MemberStatus get() = MemberStatus.fromRaw(status)
    val parsedPermissions: MemberPermissions get() = MemberPermissions.fromMap(permissions)

    fun toAccessProfile(): MemberAccessProfile = MemberAccessProfile(
        uid = uid.ifBlank { id },
        role = orgRole,
        status = memberStatus,
        allSchools = allSchools,
        allowedSchoolIds = schools,
        permissions = parsedPermissions,
        teacherProfileCompleted = teacherProfileCompleted,
        assignedClassSubjects = assignedClassSubjects
    )
}

@Keep
@IgnoreExtraProperties
data class CloudJoinRequest(
    @DocumentId val id: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val message: String = "",
    val requestedRole: String = OrgRole.TEACHER.name,
    val status: String = "pending",
    val reviewedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val roleEnum: OrgRole get() = OrgRole.fromRaw(requestedRole)
}

@Keep
@IgnoreExtraProperties
data class CloudInviteCode(
    @DocumentId val id: String = "",
    val code: String = "",
    val orgId: String = "",
    val orgName: String = "",
    val presetRole: String = OrgRole.TEACHER.name,
    val allSchools: Boolean = false,
    val schools: List<String> = emptyList(),
    val permissions: Map<String, String> = MemberPermissions.presetFor(OrgRole.TEACHER).toFirestoreMap(),
    val createdByUid: String = "",
    val expiresAt: Timestamp? = null,
    val usedByUid: String = "",
    val usedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val roleEnum: OrgRole get() = OrgRole.fromRaw(presetRole)
    val parsedPermissions: MemberPermissions get() = MemberPermissions.fromMap(permissions)
    val used: Boolean get() = usedByUid.isNotBlank()
    fun expiredNow(nowMs: Long = System.currentTimeMillis()): Boolean {
        val expMs = expiresAt?.toDate()?.time ?: return true
        return expMs <= nowMs
    }
}

@Keep
@IgnoreExtraProperties
data class CloudAuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val summary: String = "",
    val schoolId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class CloudClassGroupItem(
    val id: String,
    val name: String,
    val shortLabel: String,
    val sortOrder: Int,
    val templateKey: String,
    val subjects: List<String>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "shortLabel" to shortLabel,
        "sortOrder" to sortOrder.toLong(),
        "templateKey" to templateKey,
        "subjects" to subjects
    )

    companion object {
        fun fromMap(map: Map<*, *>?): CloudClassGroupItem? {
            if (map == null) return null
            val id = (map["id"] as? String).orEmpty()
            val name = (map["name"] as? String).orEmpty()
            if (id.isBlank() || name.isBlank()) return null
            val rawSubjects = (map["subjects"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            return CloudClassGroupItem(
                id = id,
                name = name,
                shortLabel = (map["shortLabel"] as? String).orEmpty().ifBlank { name },
                sortOrder = (map["sortOrder"] as? Number)?.toInt() ?: 0,
                templateKey = (map["templateKey"] as? String).orEmpty(),
                subjects = rawSubjects
            )
        }
    }
}

data class CloudSchoolClassItem(
    val id: String,
    val classGroupId: String,
    val name: String,
    val sortOrder: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "classGroupId" to classGroupId,
        "name" to name,
        "sortOrder" to sortOrder.toLong()
    )

    companion object {
        fun fromMap(map: Map<*, *>?): CloudSchoolClassItem? {
            if (map == null) return null
            val id = (map["id"] as? String).orEmpty()
            val name = (map["name"] as? String).orEmpty()
            if (id.isBlank() || name.isBlank()) return null
            return CloudSchoolClassItem(
                id = id,
                classGroupId = (map["classGroupId"] as? String).orEmpty(),
                name = name,
                sortOrder = (map["sortOrder"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

data class CloudSchoolRecord(
    val docId: String,
    val stableId: Long,
    val name: String,
    val address: String,
    val mobile: String,
    val email: String,
    val logoBase64: String,
    val active: Boolean,
    val classGroups: List<CloudClassGroupItem>,
    val classes: List<CloudSchoolClassItem>,
    val createdAt: Timestamp? = null,
    val printClassTeacherNameOnReports: Boolean = false,
    val checkReportCardsReadyBeforeBatch: Boolean = false,
    val openExamsForTeachers: List<String> = Phase7AccessEvaluator.ALL_EXAM_KEYS,
    val classTeacherNamesBySection: Map<String, String> = emptyMap()
) {
    fun toSchoolEntity(): SchoolEntity = SchoolEntity(
        id = stableId,
        name = name,
        address = address,
        mobile = mobile,
        email = email,
        logoBase64 = logoBase64,
        active = active
    )

    fun toClassGroupEntities(): List<ClassGroupEntity> = classGroups.map { grp ->
        ClassGroupEntity(
            id = StableDocId.toLong("${docId}_grp_${grp.id}"),
            schoolId = stableId,
            name = grp.name,
            shortLabel = grp.shortLabel,
            sortOrder = grp.sortOrder,
            templateKey = grp.templateKey
        )
    }

    fun toSchoolClassEntities(): List<SchoolClassEntity> = classes.map { cls ->
        SchoolClassEntity(
            id = StableDocId.toLong("${docId}_cls_${cls.id}"),
            schoolId = stableId,
            classGroupId = StableDocId.toLong("${docId}_grp_${cls.classGroupId}"),
            name = cls.name,
            sortOrder = cls.sortOrder
        )
    }

    fun toSubjectEntities(): List<SubjectEntity> = classGroups.flatMap { grp ->
        val grpLongId = StableDocId.toLong("${docId}_grp_${grp.id}")
        grp.subjects.mapIndexed { idx, subName ->
            SubjectEntity(
                id = StableDocId.toLong("${docId}_sub_${grp.id}_$idx"),
                schoolId = stableId,
                classGroupId = grpLongId,
                name = subName,
                sortOrder = idx
            )
        }
    }
}

data class CloudStudentRecord(
    val docId: String,
    val schoolDocId: String,
    val entity: StudentEntity,
    val createdAt: Timestamp? = null
)

data class CloudMarkRecord(
    val docId: String,
    val schoolDocId: String,
    val studentDocId: String,
    val className: String,
    val entity: MarkEntity,
    val updatedByUid: String = "",
    val updatedByName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false,
    val isFromCache: Boolean = false,
    val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudMarkEditLog(
    @DocumentId val id: String = "",
    val markDocId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val schoolId: String = "",
    val className: String = "",
    val subjectName: String = "",
    val componentKey: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val editorUid: String = "",
    val editorName: String = "",
    val clientEditedAtMs: Long = 0L,
    val hasPendingWrites: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class CloudCoScholasticRecord(
    val docId: String,
    val schoolDocId: String,
    val studentDocId: String,
    val entity: CoScholasticEntity,
    val createdAt: Timestamp? = null
)

data class CloudTermInfoRecord(
    val docId: String,
    val schoolDocId: String,
    val studentDocId: String,
    val entity: TermInfoEntity,
    val createdAt: Timestamp? = null
)

data class LocalMigrationSummary(
    val schoolCount: Int = 0,
    val studentCount: Int = 0,
    val markCount: Int = 0,
    val hasDataToUpload: Boolean = false
)

data class MigrationProgress(
    val isRunning: Boolean = false,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val statusText: String = "",
    val finished: Boolean = false,
    val error: String? = null
)

/**
 * Deterministic positive 63-bit ID mapper between Firestore string doc IDs and Phase 1 Long IDs
 * so all existing Phase 1 calculations, UI filters, and PDF report layouts remain 100% unchanged.
 */
object StableDocId {
    fun toLong(docId: String): Long {
        var h = 1125899906842597L // prime FNV-like seed
        for (ch in docId) {
            h = 31L * h + ch.code.toLong()
        }
        val positive = h and Long.MAX_VALUE
        return if (positive == 0L) 1L else positive
    }

    fun sanitizeSegment(raw: String): String =
        raw.trim().replace(Regex("[^a-zA-Z0-9_-]+"), "_").trim('_').take(48).ifEmpty { "item" }
}
