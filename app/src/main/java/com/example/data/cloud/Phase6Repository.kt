package com.example.data.cloud

import com.example.domain.AppModule
import com.example.domain.DayOfWeekCode
import com.example.domain.LeaveReason
import com.example.domain.LeaveStatus
import com.example.domain.MemberAccessProfile
import com.example.domain.OrgAccessEvaluator
import com.example.domain.SubstitutionRecord
import com.example.domain.TeacherLeaveRecord
import com.example.domain.TeacherProfile
import com.example.domain.TimetableCell
import com.example.domain.TimetableEngine
import com.example.domain.TimetableUndoSnapshot
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
import java.util.UUID

data class CloudPhase6Snapshot(
    val leaves: List<TeacherLeaveRecord> = emptyList(),
    val substitutions: List<SubstitutionRecord> = emptyList(),
    val undoSnapshots: List<TimetableUndoSnapshot> = emptyList(),
    val ownLinkedTeacher: TeacherProfile? = null,
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false,
    val hasConfirmedServerSnapshot: Boolean = false
)

private data class Phase6Slice<T>(
    val items: List<T> = emptyList(),
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false,
    val hasConfirmedServer: Boolean = false
)

/**
 * Phase 6 Offline-First Cloud Repository:
 * - Batch commits auto-generated timetable cells + pre-run Undo snapshot (`timetableUndoSnapshots`).
 * - Restores Undo snapshots cleanly (re-writes prior cells, deletes newly added cells).
 * - Manages Teacher Leaves (`teacherLeaves`) with self-request & admin approval/rejection/cancel workflow.
 * - Manages Date-scoped Substitutions (`substitutions`) with deterministic IDs that NEVER alter weekly `timetableCells`.
 * - Works 100% offline via Firestore local cache + `MetadataChanges.INCLUDE`, feeding pending writes & sync errors
 *   into the existing Phase 3 live Status Chip.
 */
class Phase6Repository(
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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync Leave / Substitution changes.")
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
    // 1. REAL-TIME OBSERVERS (Gated by AppModule.TIMETABLE)
    // =========================================================================
    fun observePhase6Data(
        orgId: String,
        member: MemberAccessProfile,
        currentUid: String
    ): Flow<CloudPhase6Snapshot> {
        if (!OrgAccessEvaluator.canViewModule(member, AppModule.TIMETABLE)) {
            return flowOf(CloudPhase6Snapshot())
        }
        val orgRef = db.collection("organisations").document(orgId)
        val canEditTimetable = OrgAccessEvaluator.canEditModule(member, AppModule.TIMETABLE)
        val canReadAllTeachers = OrgAccessEvaluator.canViewModule(member, AppModule.TEACHERS) || canEditTimetable

        val leavesFlow: Flow<Phase6Slice<TeacherLeaveRecord>> = orgRef
            .collection("teacherLeaves")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val fromCache = snap.metadata.isFromCache()
                Phase6Slice(
                    items = snap.documents.mapNotNull { doc ->
                        TeacherLeaveRecord.fromMap(
                            id = doc.id,
                            raw = doc.data,
                            hasPendingWrites = doc.metadata.hasPendingWrites(),
                            isFromCache = doc.metadata.isFromCache()
                        )
                    }.sortedByDescending { it.startDate },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = fromCache,
                    hasConfirmedServer = !fromCache && !hasPending
                )
            }

        val subsFlow: Flow<Phase6Slice<SubstitutionRecord>> = orgRef
            .collection("substitutions")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val fromCache = snap.metadata.isFromCache()
                Phase6Slice(
                    items = snap.documents.mapNotNull { doc ->
                        SubstitutionRecord.fromMap(
                            id = doc.id,
                            raw = doc.data,
                            hasPendingWrites = doc.metadata.hasPendingWrites(),
                            isFromCache = doc.metadata.isFromCache()
                        )
                    },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = fromCache,
                    hasConfirmedServer = !fromCache && !hasPending
                )
            }

        val undoFlow: Flow<Phase6Slice<TimetableUndoSnapshot>> = if (!canEditTimetable) {
            flowOf(Phase6Slice())
        } else {
            orgRef.collection("timetableUndoSnapshots")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    Phase6Slice(
                        items = snap.documents.mapNotNull { doc ->
                            TimetableUndoSnapshot.fromMap(doc.id, doc.data)
                        }.sortedByDescending { it.createdAtMs },
                        pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                        isFromCache = snap.metadata.isFromCache()
                    )
                }
        }

        // For Timetable view-only teachers without Teachers module read access, query ONLY their own linked teacher doc
        val ownTeacherFlow: Flow<TeacherProfile?> = if (canReadAllTeachers || currentUid.isBlank()) {
            flowOf(null)
        } else {
            orgRef.collection("teachers")
                .whereEqualTo("linkedMemberUid", currentUid)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap -> snap.documents.firstOrNull()?.let { parseTeacherDoc(it) } }
        }

        return combine(leavesFlow, subsFlow, undoFlow, ownTeacherFlow) { leaves, subs, undos, ownTeacher ->
            CloudPhase6Snapshot(
                leaves = leaves.items,
                substitutions = subs.items,
                undoSnapshots = undos.items,
                ownLinkedTeacher = ownTeacher,
                pendingWriteCount = leaves.pendingCount + subs.pendingCount + undos.pendingCount,
                isFromCache = leaves.isFromCache && subs.isFromCache,
                hasConfirmedServerSnapshot = leaves.hasConfirmedServer && subs.hasConfirmedServer
            )
        }
    }

    // =========================================================================
    // 2. AUTO-GENERATE BATCH COMMIT & ONE-TAP UNDO RESTORE
    // =========================================================================
    fun commitAutoGeneratedCells(
        orgId: String,
        generatedCells: List<TimetableCell>,
        cellsToDeleteIds: List<String>,
        existingCellIdsInCloud: Set<String>,
        undoSnapshot: TimetableUndoSnapshot,
        editorName: String,
        clientEditedAtMs: Long = System.currentTimeMillis()
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        val safeEditor = editorName.trim().ifBlank { orgRepo.currentUserName() }.take(120)
        val orgRef = db.collection("organisations").document(orgId)
        val cellsCol = orgRef.collection("timetableCells")
        val undoRef = orgRef.collection("timetableUndoSnapshots").document(undoSnapshot.id)
        val path = "organisations/$orgId/timetableCells"

        return try {
            // Save Undo snapshot first so Undo is guaranteed available
            val undoPayload = undoSnapshot.toFirestoreMap(
                actorUid = uid,
                actorName = safeEditor,
                createdAtValue = FieldValue.serverTimestamp(),
                updatedAtValue = FieldValue.serverTimestamp()
            )
            val undoTask = undoRef.set(undoPayload)
            attachSyncListeners(undoTask, OperationType.WRITE, "organisations/$orgId/timetableUndoSnapshots/${undoSnapshot.id}") {
                undoRef.set(undoPayload)
            }

            // Build chunked operations (<= 420 per Firestore WriteBatch)
            val ops = mutableListOf<Pair<String, TimetableCell?>>()
            cellsToDeleteIds.distinct().forEach { id -> ops.add(id to null) }
            generatedCells.forEach { cell -> ops.add(cell.id to cell) }

            ops.chunked(420).forEach { chunk ->
                val batch = db.batch()
                for ((docId, cell) in chunk) {
                    val ref = cellsCol.document(docId)
                    if (cell == null) {
                        batch.delete(ref)
                    } else if (docId in existingCellIdsInCloud) {
                        batch.update(
                            ref,
                            mapOf(
                                "subjectName" to cell.subjectName.trim().take(80),
                                "teacherId" to cell.teacherId.trim().take(128),
                                "teacherName" to cell.teacherName.trim().take(120),
                                "locked" to cell.locked,
                                "updatedByUid" to uid,
                                "updatedByName" to safeEditor,
                                "clientEditedAtMs" to clientEditedAtMs,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        batch.set(
                            ref,
                            mapOf(
                                "schoolId" to cell.schoolId,
                                "session" to cell.session.trim().take(30),
                                "className" to cell.className.trim().take(40),
                                "section" to cell.section.trim().uppercase().ifBlank { "A" }.take(10),
                                "day" to cell.day.wireValue,
                                "slotId" to cell.slotId.trim().take(64),
                                "subjectName" to cell.subjectName.trim().take(80),
                                "teacherId" to cell.teacherId.trim().take(128),
                                "teacherName" to cell.teacherName.trim().take(120),
                                "locked" to cell.locked,
                                "updatedByUid" to uid,
                                "updatedByName" to safeEditor,
                                "clientEditedAtMs" to clientEditedAtMs,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                val task = batch.commit()
                attachSyncListeners(task, OperationType.WRITE, path)
            }

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "TIMETABLE_AUTO_GENERATED",
                targetType = "timetable",
                targetId = undoSnapshot.id,
                summary = "Auto-generated ${undoSnapshot.scopeLabel} (${generatedCells.size} placed, seed ${undoSnapshot.seedUsed})",
                schoolId = undoSnapshot.schoolId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun restoreUndoSnapshot(
        orgId: String,
        snapshot: TimetableUndoSnapshot,
        currentCellIdsInCloud: Set<String>,
        editorName: String,
        clientEditedAtMs: Long = System.currentTimeMillis()
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        val safeEditor = editorName.trim().ifBlank { orgRepo.currentUserName() }.take(120)
        val orgRef = db.collection("organisations").document(orgId)
        val cellsCol = orgRef.collection("timetableCells")
        val path = "organisations/$orgId/timetableCells"

        return try {
            val previousById = snapshot.previousCells.associateBy {
                it.id.ifBlank {
                    TimetableEngine.makeCellDocId(it.schoolId, it.session, it.className, it.section, it.day, it.slotId)
                }
            }
            val idsToDelete = snapshot.affectedCellIds
                .filter { it !in previousById && it in currentCellIdsInCloud }
                .distinct()

            val ops = mutableListOf<Pair<String, TimetableCell?>>()
            idsToDelete.forEach { ops.add(it to null) }
            previousById.forEach { (id, cell) -> ops.add(id to cell) }

            ops.chunked(420).forEach { chunk ->
                val batch = db.batch()
                for ((docId, cell) in chunk) {
                    val ref = cellsCol.document(docId)
                    if (cell == null) {
                        batch.delete(ref)
                    } else if (docId in currentCellIdsInCloud) {
                        batch.update(
                            ref,
                            mapOf(
                                "subjectName" to cell.subjectName.trim().take(80),
                                "teacherId" to cell.teacherId.trim().take(128),
                                "teacherName" to cell.teacherName.trim().take(120),
                                "locked" to cell.locked,
                                "updatedByUid" to uid,
                                "updatedByName" to safeEditor,
                                "clientEditedAtMs" to clientEditedAtMs,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    } else {
                        batch.set(
                            ref,
                            mapOf(
                                "schoolId" to cell.schoolId,
                                "session" to cell.session.trim().take(30),
                                "className" to cell.className.trim().take(40),
                                "section" to cell.section.trim().uppercase().ifBlank { "A" }.take(10),
                                "day" to cell.day.wireValue,
                                "slotId" to cell.slotId.trim().take(64),
                                "subjectName" to cell.subjectName.trim().take(80),
                                "teacherId" to cell.teacherId.trim().take(128),
                                "teacherName" to cell.teacherName.trim().take(120),
                                "locked" to cell.locked,
                                "updatedByUid" to uid,
                                "updatedByName" to safeEditor,
                                "clientEditedAtMs" to clientEditedAtMs,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                val task = batch.commit()
                attachSyncListeners(task, OperationType.WRITE, path)
            }

            // Remove consumed undo snapshot
            val undoRef = orgRef.collection("timetableUndoSnapshots").document(snapshot.id)
            attachSyncListeners(
                undoRef.delete(),
                OperationType.DELETE,
                "organisations/$orgId/timetableUndoSnapshots/${snapshot.id}"
            )

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "TIMETABLE_AUTO_GENERATE_UNDONE",
                targetType = "timetableUndo",
                targetId = snapshot.id,
                summary = "Undid auto-generate for ${snapshot.scopeLabel} (restored ${snapshot.previousCells.size} cells)",
                schoolId = snapshot.schoolId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. TEACHER LEAVE CRUD (Request, Direct Mark, Approve, Reject, Edit, Cancel)
    // =========================================================================
    fun saveTeacherLeave(
        orgId: String,
        leave: TeacherLeaveRecord,
        existsInCloud: Boolean,
        editorName: String,
        auditAction: String,
        clientEditedAtMs: Long = System.currentTimeMillis()
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        val safeEditor = editorName.trim().ifBlank { orgRepo.currentUserName() }.take(120)
        val docId = leave.id.takeIf { it.isNotBlank() }
            ?: "lv_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "organisations/$orgId/teacherLeaves/$docId"

        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("teacherLeaves").document(docId)
            if (existsInCloud) {
                val updatePayload = mapOf(
                    "teacherName" to leave.teacherName.trim().take(120),
                    "startDate" to leave.startDate.trim().take(16),
                    "endDate" to leave.endDate.trim().take(16),
                    "reasonType" to leave.reasonType.wireValue,
                    "reasonNote" to leave.reasonNote.trim().take(240),
                    "halfDay" to leave.halfDay,
                    "halfDaySlotIds" to leave.halfDaySlotIds.map { it.trim().take(40) }.filter { it.isNotEmpty() }.distinct().take(20),
                    "status" to leave.status.wireValue,
                    "approvedByUid" to leave.approvedByUid.take(128),
                    "approvedByName" to leave.approvedByName.trim().take(120),
                    "approvedAtMs" to leave.approvedAtMs,
                    "updatedByUid" to uid,
                    "updatedByName" to safeEditor,
                    "clientEditedAtMs" to clientEditedAtMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = ref.update(updatePayload)
                attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(updatePayload) }
            } else {
                val fullPayload = leave.copy(id = docId).toFirestoreMap(
                    actorUid = uid,
                    actorName = safeEditor,
                    editedAtMs = clientEditedAtMs,
                    createdAtValue = FieldValue.serverTimestamp(),
                    updatedAtValue = FieldValue.serverTimestamp()
                )
                val task = ref.set(fullPayload)
                attachSyncListeners(task, OperationType.CREATE, path) { ref.set(fullPayload) }
            }

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = auditAction,
                targetType = "leave",
                targetId = docId,
                summary = "${leave.teacherName.ifBlank { leave.teacherId }}: ${leave.startDate}..${leave.endDate} (${leave.reasonType.label}, ${leave.status.label})"
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. SUBSTITUTION CRUD (Deterministic ID per date/school/class/sec/slot)
    // =========================================================================
    fun saveSubstitution(
        orgId: String,
        record: SubstitutionRecord,
        existsInCloud: Boolean,
        editorName: String,
        clientEditedAtMs: Long = System.currentTimeMillis()
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        val safeEditor = editorName.trim().ifBlank { orgRepo.currentUserName() }.take(120)
        val docId = record.id.takeIf { it.isNotBlank() }
            ?: SubstitutionRecord.makeSubstitutionDocId(
                date = record.date,
                schoolId = record.schoolId,
                className = record.className,
                section = record.section,
                slotId = record.slotId
            )
        val path = "organisations/$orgId/substitutions/$docId"

        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("substitutions").document(docId)
            if (existsInCloud) {
                val updatePayload = mapOf(
                    "slotLabel" to record.slotLabel.trim().take(40),
                    "startMinutes" to record.startMinutes.coerceIn(0, 1440),
                    "endMinutes" to record.endMinutes.coerceIn(0, 1440),
                    "subjectName" to record.subjectName.trim().take(80),
                    "absentTeacherId" to record.absentTeacherId.trim().take(128),
                    "absentTeacherName" to record.absentTeacherName.trim().take(120),
                    "leaveId" to record.leaveId.trim().take(128),
                    "substituteTeacherId" to record.substituteTeacherId.trim().take(128),
                    "substituteTeacherName" to record.substituteTeacherName.trim().take(120),
                    "overrideUsed" to record.overrideUsed,
                    "overrideReason" to record.overrideReason.trim().take(200),
                    "updatedByUid" to uid,
                    "updatedByName" to safeEditor,
                    "clientEditedAtMs" to clientEditedAtMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = ref.update(updatePayload)
                attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(updatePayload) }
            } else {
                val fullPayload = record.copy(id = docId).toFirestoreMap(
                    actorUid = uid,
                    actorName = safeEditor,
                    editedAtMs = clientEditedAtMs,
                    createdAtValue = FieldValue.serverTimestamp(),
                    updatedAtValue = FieldValue.serverTimestamp()
                )
                val task = ref.set(fullPayload)
                attachSyncListeners(task, OperationType.CREATE, path) { ref.set(fullPayload) }
            }

            val overrideSuffix = if (record.overrideUsed) " [OVERRIDE]" else ""
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = if (existsInCloud) "SUBSTITUTION_UPDATED" else "SUBSTITUTION_ASSIGNED",
                targetType = "substitution",
                targetId = docId,
                summary = "${record.date} ${record.className}-${record.section} ${record.slotLabel}: ${record.substituteTeacherName} for ${record.absentTeacherName}$overrideSuffix",
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

    fun deleteSubstitution(
        orgId: String,
        record: SubstitutionRecord
    ): Result<Unit> {
        val path = "organisations/$orgId/substitutions/${record.id}"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("substitutions").document(record.id)
            val task = ref.delete()
            attachSyncListeners(task, OperationType.DELETE, path) { ref.delete() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "SUBSTITUTION_CLEARED",
                targetType = "substitution",
                targetId = record.id,
                summary = "Cleared substitution ${record.date} ${record.className}-${record.section} ${record.slotLabel}",
                schoolId = record.schoolId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun parseTeacherDoc(doc: DocumentSnapshot): TeacherProfile {
        val offDays = (doc.get("weeklyOffDays") as? List<*>)
            ?.mapNotNull { DayOfWeekCode.fromRaw(it as? String) }
            ?: emptyList()
        return TeacherProfile(
            id = doc.id,
            name = doc.getString("name").orEmpty(),
            mobile = doc.getString("mobile").orEmpty(),
            email = doc.getString("email").orEmpty(),
            photoBase64 = doc.getString("photoBase64").orEmpty(),
            subjects = (doc.get("subjects") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            schoolIds = (doc.get("schoolIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            maxPeriodsPerDay = (doc.get("maxPeriodsPerDay") as? Number)?.toInt() ?: 7,
            maxConsecutivePeriods = (doc.get("maxConsecutivePeriods") as? Number)?.toInt() ?: 3,
            weeklyOffDays = offDays,
            active = doc.getBoolean("active") ?: true,
            linkedMemberUid = doc.getString("linkedMemberUid").orEmpty()
        )
    }
}
