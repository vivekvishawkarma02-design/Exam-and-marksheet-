package com.example.data.cloud

import com.example.domain.AppModule
import com.example.domain.DayOfWeekCode
import com.example.domain.MemberAccessProfile
import com.example.domain.OrgAccessEvaluator
import com.example.domain.Phase4Validator
import com.example.domain.TeacherAccessScope
import com.example.domain.TeacherAttendanceRecord
import com.example.domain.TeacherProfile
import com.example.util.OperationType
import com.example.util.friendlyFirestoreErrorMessage
import com.example.util.handleFirestoreError
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class CloudPhase7Snapshot(
    val ownTeacherScope: TeacherAccessScope? = null,
    val allTeacherScopes: Map<String, TeacherAccessScope> = emptyMap(),
    val ownLinkedTeacherProfile: TeacherProfile? = null,
    val teacherAttendanceRecords: List<TeacherAttendanceRecord> = emptyList(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase7Slice<T>(
    val value: T,
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false
)

/**
 * Phase 7 Offline-First Repository:
 * - `organisations/{orgId}/teacherScopes/{memberUid}`: Server-trusted mirror of Phase 4
 *   assignments for linked teacher members, enforced directly in Firestore rules AND UI.
 * - `organisations/{orgId}/teacherAttendance/{attId}`: Optional daily teacher attendance sheet
 *   controlled by `Timetable: view and edit`.
 * - Also observes the signed-in teacher's own `teachers` doc (when `linkedMemberUid == uid`)
 *   so Teachers without `teachers: view` still get their full "My Day" home.
 */
class Phase7Repository(
    private val orgRepo: OrgCloudRepository,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val db: FirebaseFirestore get() = orgRepo.db

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    @Volatile
    private var lastRetryAction: (() -> Unit)? = null

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    private fun attachSyncListeners(
        task: Task<Void>,
        opType: OperationType,
        path: String,
        retryAction: (() -> Unit)? = null
    ) {
        task.addOnSuccessListener {
            if (_syncError.value != null) {
                _syncError.value = null
                lastRetryAction = null
            }
        }.addOnFailureListener { e ->
            handleFirestoreError(e, opType, path)
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync Phase 7 changes.")
            if (retryAction != null) {
                lastRetryAction = retryAction
            }
        }
    }

    fun clearSyncError() {
        _syncError.value = null
        lastRetryAction = null
    }

    fun retrySync(): Boolean {
        val retry = lastRetryAction ?: return false
        _syncError.value = null
        lastRetryAction = null
        return try {
            retry()
            true
        } catch (e: Exception) {
            _syncError.value = friendlyFirestoreErrorMessage(e, "Retry failed.")
            false
        }
    }

    // =========================================================================
    // 1. REAL-TIME OBSERVERS (Strictly scoped to member permissions)
    // =========================================================================
    fun observePhase7Data(
        orgId: String,
        member: MemberAccessProfile,
        currentUid: String
    ): Flow<CloudPhase7Snapshot> {
        if (!OrgAccessEvaluator.isActive(member) || currentUid.isBlank()) {
            return flowOf(CloudPhase7Snapshot())
        }
        val orgRef = db.collection("organisations").document(orgId)

        val ownScopeFlow: Flow<Phase7Slice<TeacherAccessScope?>> = orgRef
            .collection("teacherScopes")
            .document(currentUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { doc ->
                val scope = if (doc.exists()) TeacherAccessScope.fromMap(doc.id, doc.data) else null
                Phase7Slice(
                    value = scope,
                    pendingCount = if (doc.metadata.hasPendingWrites()) 1 else 0,
                    isFromCache = doc.metadata.isFromCache()
                )
            }

        val canListAllScopes = OrgAccessEvaluator.isAdmin(member) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.TEACHERS) ||
            (member.role != com.example.domain.OrgRole.TEACHER &&
                (OrgAccessEvaluator.canViewModule(member, AppModule.MARKS) ||
                    OrgAccessEvaluator.canViewModule(member, AppModule.REPORTS)))

        val allScopesFlow: Flow<Phase7Slice<Map<String, TeacherAccessScope>>> = if (canListAllScopes) {
            orgRef.collection("teacherScopes")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val mapped = snap.documents
                        .mapNotNull { TeacherAccessScope.fromMap(it.id, it.data) }
                        .associateBy { it.uid }
                    Phase7Slice(
                        value = mapped,
                        pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                        isFromCache = snap.metadata.isFromCache()
                    )
                }
        } else {
            flowOf(Phase7Slice(emptyMap(), 0, false))
        }

        val canReadOwnTeacher = OrgAccessEvaluator.canViewModule(member, AppModule.TIMETABLE) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.MARKS) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.TEACHERS)

        val ownTeacherFlow: Flow<Phase7Slice<TeacherProfile?>> = if (canReadOwnTeacher) {
            orgRef.collection("teachers")
                .whereEqualTo("linkedMemberUid", currentUid)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val doc = snap.documents.firstOrNull()
                    Phase7Slice(
                        value = doc?.let { parseTeacherDoc(it) },
                        pendingCount = if (snap.metadata.hasPendingWrites()) 1 else 0,
                        isFromCache = snap.metadata.isFromCache()
                    )
                }
        } else {
            flowOf(Phase7Slice(null, 0, false))
        }

        val canReadAttendance = OrgAccessEvaluator.canViewModule(member, AppModule.TIMETABLE)
        val attendanceFlow: Flow<Phase7Slice<List<TeacherAttendanceRecord>>> = if (canReadAttendance) {
            orgRef.collection("teacherAttendance")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        TeacherAttendanceRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }
                    Phase7Slice(
                        value = items,
                        pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                        isFromCache = snap.metadata.isFromCache()
                    )
                }
        } else {
            flowOf(Phase7Slice(emptyList(), 0, false))
        }

        return combine(ownScopeFlow, allScopesFlow, ownTeacherFlow, attendanceFlow) { own, all, tchr, att ->
            CloudPhase7Snapshot(
                ownTeacherScope = own.value,
                allTeacherScopes = all.value,
                ownLinkedTeacherProfile = tchr.value,
                teacherAttendanceRecords = att.value,
                pendingWriteCount = own.pendingCount + all.pendingCount + att.pendingCount,
                isFromCache = own.isFromCache && att.isFromCache
            )
        }
    }

    // =========================================================================
    // 2. SYNC TEACHER ACCESS SCOPES (Admin / Teachers-Editor ONLY, Offline-First)
    // =========================================================================
    fun syncTeacherScopes(
        orgId: String,
        scopesByUid: Map<String, TeacherAccessScope>,
        removedUids: Set<String> = emptySet()
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "organisations/$orgId/teacherScopes"
        return try {
            val col = db.collection("organisations").document(orgId).collection("teacherScopes")
            val writes = scopesByUid.entries.toList()
            val deletes = removedUids.toList()

            writes.chunked(350).forEach { chunk ->
                val batch = db.batch()
                for ((memberUid, scope) in chunk) {
                    if (memberUid.isBlank()) continue
                    batch.set(
                        col.document(memberUid),
                        scope.toFirestoreMap(
                            actorUid = uid,
                            updatedAtValue = FieldValue.serverTimestamp()
                        )
                    )
                }
                val task = batch.commit()
                attachSyncListeners(task, OperationType.WRITE, path)
            }

            deletes.chunked(350).forEach { chunk ->
                val batch = db.batch()
                for (memberUid in chunk) {
                    if (memberUid.isBlank()) continue
                    batch.delete(col.document(memberUid))
                }
                val task = batch.commit()
                attachSyncListeners(task, OperationType.DELETE, path)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. TEACHER ATTENDANCE CRUD (Timetable: Edit, Offline-First)
    // =========================================================================
    fun saveTeacherAttendance(
        orgId: String,
        record: TeacherAttendanceRecord,
        existsInCloud: Boolean
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val editorName = orgRepo.currentEditorName().take(120)
        val docId = record.id.takeIf { it.isNotBlank() }
            ?: TeacherAttendanceRecord.makeDocId(record.date, record.schoolId, record.teacherId)
        val path = "organisations/$orgId/teacherAttendance/$docId"
        val nowMs = System.currentTimeMillis()
        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("teacherAttendance").document(docId)

            val runWrite = {
                if (existsInCloud) {
                    val updateMap = mapOf(
                        "teacherName" to record.teacherName.trim().take(120),
                        "status" to record.status.wireValue,
                        "note" to record.note.trim().take(240),
                        "linkedLeaveId" to record.linkedLeaveId.trim().take(120),
                        "updatedByUid" to uid,
                        "updatedByName" to editorName,
                        "clientEditedAtMs" to nowMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    ref.update(updateMap)
                } else {
                    val createMap = record.toFirestoreMap(
                        actorUid = uid,
                        actorName = editorName,
                        editedAtMs = nowMs,
                        createdAtValue = FieldValue.serverTimestamp(),
                        updatedAtValue = FieldValue.serverTimestamp()
                    )
                    ref.set(createMap)
                }
            }
            val task = runWrite()
            attachSyncListeners(task, OperationType.WRITE, path) { runWrite() }

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "TEACHER_ATTENDANCE_SAVED",
                targetType = "teacherAttendance",
                targetId = docId,
                summary = "${record.teacherName} marked ${record.status.label} on ${record.date}",
                schoolId = record.schoolId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun parseTeacherDoc(doc: DocumentSnapshot): TeacherProfile {
        val rawSubjects = (doc.get("subjects") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val rawSchools = (doc.get("schoolIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val rawOff = (doc.get("weeklyOffDays") as? List<*>)
            ?.mapNotNull { DayOfWeekCode.fromRaw(it as? String) }
            ?: emptyList()
        return TeacherProfile(
            id = doc.id,
            name = doc.getString("name").orEmpty(),
            mobile = doc.getString("mobile").orEmpty(),
            email = doc.getString("email").orEmpty(),
            photoBase64 = doc.getString("photoBase64").orEmpty(),
            subjects = rawSubjects,
            schoolIds = rawSchools,
            maxPeriodsPerDay = ((doc.get("maxPeriodsPerDay") as? Number)?.toInt() ?: Phase4Validator.DEFAULT_MAX_PERIODS_PER_DAY)
                .coerceIn(1, 20),
            maxConsecutivePeriods = ((doc.get("maxConsecutivePeriods") as? Number)?.toInt() ?: Phase4Validator.DEFAULT_MAX_CONSECUTIVE_PERIODS)
                .coerceIn(1, 20),
            weeklyOffDays = rawOff.sortedBy { it.sortOrder },
            active = doc.getBoolean("active") ?: true,
            linkedMemberUid = doc.getString("linkedMemberUid").orEmpty()
        )
    }
}
