package com.example.data.cloud

import com.example.data.StudentEntity
import com.example.domain.AppModule
import com.example.domain.BackupExportLogRecord
import com.example.domain.BackupExportScope
import com.example.domain.BulkImportExecutionReport
import com.example.domain.BulkImportLogRecord
import com.example.domain.BulkImportPreviewRow
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeStructureConfig
import com.example.domain.ImportRowAction
import com.example.domain.MemberAccessProfile
import com.example.domain.OrgAccessEvaluator
import com.example.domain.Phase14AccessEvaluator
import com.example.domain.PromotionBatchPlan
import com.example.domain.PromotionRecord
import com.example.domain.SchoolCalendarConfig
import com.example.domain.SchoolClassOrderConfig
import com.example.domain.SessionCopyOptions
import com.example.domain.SessionTransitionEngine
import com.example.domain.SessionTransitionRecord
import com.example.domain.SessionTransitionStatus
import com.example.domain.StudentFeeOverride
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID

data class CloudPhase14Snapshot(
    val schoolClassOrders: List<SchoolClassOrderConfig> = emptyList(),
    val sessionTransitions: List<SessionTransitionRecord> = emptyList(),
    val promotionRecords: List<PromotionRecord> = emptyList(),
    val bulkImportLogs: List<BulkImportLogRecord> = emptyList(),
    val backupExportLogs: List<BackupExportLogRecord> = emptyList(),
    // Shared fallback slices for delegated sessionPromotion users without Fees/Attendance modules
    val sharedFeeStructures: List<FeeStructureConfig> = emptyList(),
    val sharedFeeOverrides: List<StudentFeeOverride> = emptyList(),
    val sharedFeeReceipts: List<FeeReceiptRecord> = emptyList(),
    val sharedSchoolCalendars: List<SchoolCalendarConfig> = emptyList(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase14Slice<T>(
    val value: T,
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase14CoreBundle(
    val classOrders: Phase14Slice<List<SchoolClassOrderConfig>> = Phase14Slice(emptyList()),
    val transitions: Phase14Slice<List<SessionTransitionRecord>> = Phase14Slice(emptyList()),
    val promotionRecords: Phase14Slice<List<PromotionRecord>> = Phase14Slice(emptyList()),
    val bulkImportLogs: Phase14Slice<List<BulkImportLogRecord>> = Phase14Slice(emptyList()),
    val backupExportLogs: Phase14Slice<List<BackupExportLogRecord>> = Phase14Slice(emptyList())
)

private data class Phase14SharedBundle(
    val feeStructures: Phase14Slice<List<FeeStructureConfig>> = Phase14Slice(emptyList()),
    val feeOverrides: Phase14Slice<List<StudentFeeOverride>> = Phase14Slice(emptyList()),
    val feeReceipts: Phase14Slice<List<FeeReceiptRecord>> = Phase14Slice(emptyList()),
    val calendars: Phase14Slice<List<SchoolCalendarConfig>> = Phase14Slice(emptyList())
)

/**
 * Phase 14 Offline-First Repository:
 * - `schoolClassOrders`
 * - `sessionTransitions`
 * - `promotionRecords`
 * - `bulkImportLogs` (Owner & Admin ONLY)
 * - `backupExportLogs` (Owner & Admin ONLY)
 *
 * All writes enqueue immediately against Firestore's local cache with deterministic document IDs,
 * chunked atomic WriteBatch commits, resume/rollback support, and Owner-only 7-day Undo.
 */
class Phase14Repository(
    private val orgRepo: OrgCloudRepository,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val db: FirebaseFirestore get() = orgRepo.db

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    @Volatile
    private var lastRetryAction: (() -> Unit)? = null

    private fun orgDoc(orgId: String) = db.collection("organisations").document(orgId)

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Please sign in first.")

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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync Session/Import/Backup changes.")
            if (retryAction != null) lastRetryAction = retryAction
        }
    }

    fun clearSyncError() {
        _syncError.value = null
    }

    fun retrySync(): Boolean {
        val act = lastRetryAction ?: return false
        _syncError.value = null
        act.invoke()
        return true
    }

    private fun filterSchoolScopedDocs(
        docs: List<DocumentSnapshot>,
        member: MemberAccessProfile
    ): List<DocumentSnapshot> {
        if (OrgAccessEvaluator.isAdmin(member) || member.allSchools) return docs
        val allowed = member.allowedSchoolIds.toSet()
        return docs.filter { doc ->
            val schoolId = doc.getString("schoolId").orEmpty()
            schoolId.isEmpty() || schoolId in allowed
        }
    }

    fun observePhase14Data(
        orgId: String,
        member: MemberAccessProfile?
    ): Flow<CloudPhase14Snapshot> {
        if (orgId.isBlank() || member == null || !OrgAccessEvaluator.isActive(member)) {
            return flowOf(CloudPhase14Snapshot())
        }

        val canViewSessionPromo = Phase14AccessEvaluator.canViewSessionPromotion(member)
        val canViewPromHistory = canViewSessionPromo ||
            OrgAccessEvaluator.canViewModule(member, AppModule.STUDENTS) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.STUDENT_REGISTER)
        val canViewAdminLogs = Phase14AccessEvaluator.canUseBulkStudentImport(member)

        val classOrdersFlow: Flow<Phase14Slice<List<SchoolClassOrderConfig>>> = if (!canViewSessionPromo) {
            flowOf(Phase14Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("schoolClassOrders")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase14Slice(
                        value = docs.mapNotNull { d -> SchoolClassOrderConfig.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) },
                        pendingCount = docs.count { it.metadata.hasPendingWrites() },
                        isFromCache = snap.metadata.isFromCache
                    )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(Phase14Slice(emptyList()))
                }
        }

        val transitionsFlow: Flow<Phase14Slice<List<SessionTransitionRecord>>> = if (!canViewSessionPromo) {
            flowOf(Phase14Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("sessionTransitions")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase14Slice(
                        value = docs.mapNotNull { d -> SessionTransitionRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) }
                            .sortedByDescending { it.appliedAtMs },
                        pendingCount = docs.count { it.metadata.hasPendingWrites() },
                        isFromCache = snap.metadata.isFromCache
                    )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(Phase14Slice(emptyList()))
                }
        }

        val promotionsFlow: Flow<Phase14Slice<List<PromotionRecord>>> = if (!canViewPromHistory) {
            flowOf(Phase14Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("promotionRecords")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase14Slice(
                        value = docs.mapNotNull { d -> PromotionRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) }
                            .sortedWith(compareByDescending<PromotionRecord> { it.confirmedAtMs }.thenBy { it.studentName.lowercase(Locale.US) }),
                        pendingCount = docs.count { it.metadata.hasPendingWrites() },
                        isFromCache = snap.metadata.isFromCache
                    )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(Phase14Slice(emptyList()))
                }
        }

        val importLogsFlow: Flow<Phase14Slice<List<BulkImportLogRecord>>> = if (!canViewAdminLogs) {
            flowOf(Phase14Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("bulkImportLogs")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    Phase14Slice(
                        value = snap.documents.mapNotNull { d -> BulkImportLogRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) }
                            .sortedByDescending { it.importedAtMs },
                        pendingCount = snap.documents.count { it.metadata.hasPendingWrites() },
                        isFromCache = snap.metadata.isFromCache
                    )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(Phase14Slice(emptyList()))
                }
        }

        val backupLogsFlow: Flow<Phase14Slice<List<BackupExportLogRecord>>> = if (!canViewAdminLogs) {
            flowOf(Phase14Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("backupExportLogs")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    Phase14Slice(
                        value = snap.documents.mapNotNull { d -> BackupExportLogRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) }
                            .sortedByDescending { it.exportedAtMs },
                        pendingCount = snap.documents.count { it.metadata.hasPendingWrites() },
                        isFromCache = snap.metadata.isFromCache
                    )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(Phase14Slice(emptyList()))
                }
        }

        val coreFlow = combine(
            classOrdersFlow,
            transitionsFlow,
            promotionsFlow,
            importLogsFlow,
            backupLogsFlow
        ) { orders, transitions, proms, imports, backups ->
            Phase14CoreBundle(orders, transitions, proms, imports, backups)
        }

        // Only attach fallback fee/calendar listeners if user has sessionPromotion WITHOUT fees/attendance
        val needSharedFees = canViewSessionPromo && !OrgAccessEvaluator.canViewModule(member, AppModule.FEES)
        val needSharedCalendars = canViewSessionPromo && !OrgAccessEvaluator.canViewModule(member, AppModule.ATTENDANCE)

        val sharedFeeStructuresFlow: Flow<Phase14Slice<List<FeeStructureConfig>>> = if (!needSharedFees) {
            flowOf(Phase14Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("feeStructures")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase14Slice(docs.mapNotNull { d -> FeeStructureConfig.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) })
                }
                .catch { e -> if (e is CancellationException) throw e; emit(Phase14Slice(emptyList())) }
        }

        val sharedFeeOverridesFlow: Flow<Phase14Slice<List<StudentFeeOverride>>> = if (!needSharedFees) {
            flowOf(Phase14Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("studentFeeOverrides")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase14Slice(docs.mapNotNull { d -> StudentFeeOverride.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) })
                }
                .catch { e -> if (e is CancellationException) throw e; emit(Phase14Slice(emptyList())) }
        }

        val sharedFeeReceiptsFlow: Flow<Phase14Slice<List<FeeReceiptRecord>>> = if (!needSharedFees) {
            flowOf(Phase14Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("feeReceipts")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase14Slice(docs.mapNotNull { d -> FeeReceiptRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) })
                }
                .catch { e -> if (e is CancellationException) throw e; emit(Phase14Slice(emptyList())) }
        }

        val sharedCalendarsFlow: Flow<Phase14Slice<List<SchoolCalendarConfig>>> = if (!needSharedCalendars) {
            flowOf(Phase14Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("schoolCalendars")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase14Slice(docs.mapNotNull { d -> SchoolCalendarConfig.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) })
                }
                .catch { e -> if (e is CancellationException) throw e; emit(Phase14Slice(emptyList())) }
        }

        val sharedFlow = combine(
            sharedFeeStructuresFlow,
            sharedFeeOverridesFlow,
            sharedFeeReceiptsFlow,
            sharedCalendarsFlow
        ) { structs, overrides, receipts, cals ->
            Phase14SharedBundle(structs, overrides, receipts, cals)
        }

        return combine(coreFlow, sharedFlow) { core, shared ->
            CloudPhase14Snapshot(
                schoolClassOrders = core.classOrders.value,
                sessionTransitions = core.transitions.value,
                promotionRecords = core.promotionRecords.value,
                bulkImportLogs = core.bulkImportLogs.value,
                backupExportLogs = core.backupExportLogs.value,
                sharedFeeStructures = shared.feeStructures.value,
                sharedFeeOverrides = shared.feeOverrides.value,
                sharedFeeReceipts = shared.feeReceipts.value,
                sharedSchoolCalendars = shared.calendars.value,
                pendingWriteCount = core.classOrders.pendingCount +
                    core.transitions.pendingCount +
                    core.promotionRecords.pendingCount +
                    core.bulkImportLogs.pendingCount +
                    core.backupExportLogs.pendingCount,
                isFromCache = core.transitions.isFromCache
            )
        }
    }

    // =========================================================================
    // 1. SAVE CLASS ORDER PER SCHOOL
    // =========================================================================
    fun saveSchoolClassOrder(
        orgId: String,
        schoolId: String,
        orderedClassNames: List<String>,
        actorName: String
    ): Result<Unit> {
        val cleanList = orderedClassNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanList.isEmpty()) return Result.failure(IllegalArgumentException("Class order cannot be empty."))
        return try {
            val uid = requireUid()
            val cfg = SchoolClassOrderConfig(
                schoolId = schoolId,
                orderedClassNames = cleanList,
                updatedByUid = uid,
                updatedByName = actorName,
                clientEditedAtMs = System.currentTimeMillis()
            )
            val ref = orgDoc(orgId).collection("schoolClassOrders").document(schoolId)
            val payload = cfg.toFirestoreMap(uid, actorName, cfg.clientEditedAtMs, FieldValue.serverTimestamp())
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.WRITE, "organisations/$orgId/schoolClassOrders/$schoolId") {
                ref.set(payload)
            }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "CLASS_ORDER_UPDATED",
                targetType = "sessionTransition",
                targetId = schoolId,
                summary = "Updated class promotion order (${cleanList.joinToString(" -> ").take(180)})",
                schoolId = schoolId
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 2. BATCHED SESSION START & PROMOTION (RESUME-SAFE + ROLLBACK-SAFE)
    // =========================================================================
    fun applySessionStartAndPromotionBatched(
        orgId: String,
        schoolId: String,
        fromSession: String,
        toSession: String,
        startDateIso: String,
        endDateIso: String,
        copyOptions: SessionCopyOptions,
        plan: PromotionBatchPlan,
        copiedFeeStructure: FeeStructureConfig?,
        copiedCalendar: SchoolCalendarConfig?,
        classDocIdByClassName: Map<String, String>,
        currentSettings: CloudOrgSettings,
        actorName: String,
        startFromBatchIndex: Int = 0
    ): Result<SessionTransitionRecord> {
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val transitionRef = orgDoc(orgId).collection("sessionTransitions").document(plan.transitionId)

            val createdFeeStructId = if (copyOptions.copyFeeStructure && copiedFeeStructure != null) copiedFeeStructure.id else ""
            val createdCalId = if (copyOptions.copySchoolCalendar && copiedCalendar != null) copiedCalendar.id else ""

            val baseTransition = SessionTransitionRecord(
                id = plan.transitionId,
                schoolId = schoolId,
                fromSession = fromSession,
                toSession = toSession,
                startDateIso = startDateIso,
                endDateIso = endDateIso,
                status = SessionTransitionStatus.IN_PROGRESS,
                copyOptions = copyOptions,
                totalStudentsEvaluated = plan.totalStudentsEvaluated,
                promotedCount = plan.promotedCount,
                gracePromotedCount = plan.gracePromotedCount,
                detainedCount = plan.detainedCount,
                leftCount = plan.leftCount,
                passedOutCount = plan.passedOutCount,
                totalDuesCarriedForwardRs = plan.totalDuesCarriedForwardRs,
                totalBatches = plan.chunks.size,
                completedBatches = startFromBatchIndex.coerceIn(0, plan.chunks.size),
                createdStudentDocIds = plan.createdStudentDocIds,
                createdFeeOverrideDocIds = plan.createdFeeOverrideDocIds,
                createdPromotionRecordIds = plan.createdPromotionRecordIds,
                createdFeeStructureDocId = createdFeeStructId,
                createdCalendarDocId = createdCalId,
                previousCurrentSession = currentSettings.currentSession,
                previousSessionsCsv = currentSettings.sessionsCsv,
                appliedByUid = uid,
                appliedByName = actorName.ifBlank { "Admin" },
                appliedAtMs = nowMs,
                undoExpiresAtMs = nowMs + SessionTransitionEngine.UNDO_WINDOW_MS
            )

            // Batch 0: write IN_PROGRESS marker + copied FeeStructure + copied SchoolCalendar
            if (startFromBatchIndex == 0) {
                val initBatch = db.batch()
                initBatch.set(transitionRef, baseTransition.toFirestoreMap(FieldValue.serverTimestamp()))
                if (copyOptions.copyFeeStructure && copiedFeeStructure != null) {
                    val fRef = orgDoc(orgId).collection("feeStructures").document(copiedFeeStructure.id)
                    val fPayload = copiedFeeStructure.toFirestoreMap(uid, actorName, nowMs) +
                        mapOf("updatedAt" to FieldValue.serverTimestamp())
                    initBatch.set(fRef, fPayload)
                }
                if (copyOptions.copySchoolCalendar && copiedCalendar != null) {
                    val cRef = orgDoc(orgId).collection("schoolCalendars").document(copiedCalendar.id)
                    val cPayload = copiedCalendar.toFirestoreMap(
                        actorUid = uid,
                        createdAtValue = FieldValue.serverTimestamp(),
                        updatedAtValue = FieldValue.serverTimestamp()
                    )
                    initBatch.set(cRef, cPayload)
                }
                val t0 = initBatch.commit()
                attachSyncListeners(t0, OperationType.WRITE, "organisations/$orgId/sessionTransitions/${plan.transitionId}")
            }

            // Chunked student + carry-forward override + promotion record batches (<= ~75 ops per batch)
            val studentsCol = orgDoc(orgId).collection("schools").document(schoolId).collection("students")
            val overridesCol = orgDoc(orgId).collection("studentFeeOverrides")
            val promsCol = orgDoc(orgId).collection("promotionRecords")

            for (chunk in plan.chunks) {
                if (chunk.batchIndex < startFromBatchIndex) continue
                val batch = db.batch()
                for (item in chunk.items) {
                    val classDocId = classDocIdByClassName[item.student.className]
                        ?: classDocIdByClassName.entries.firstOrNull { it.key.equals(item.student.className, ignoreCase = true) }?.value
                        ?: ""
                    val stuPayload = buildStudentCreatePayload(
                        schoolDocId = schoolId,
                        classDocId = classDocId,
                        student = item.student
                    )
                    batch.set(studentsCol.document(item.newStudentDocId), stuPayload)

                    val ov = item.carriedForwardOverride
                    if (ov != null) {
                        val ovPayload = ov.toFirestoreMap(uid, actorName, nowMs) +
                            mapOf("updatedAt" to FieldValue.serverTimestamp())
                        batch.set(overridesCol.document(ov.id), ovPayload)
                    }

                    batch.set(
                        promsCol.document(item.promotionRecord.id),
                        item.promotionRecord.toFirestoreMap(FieldValue.serverTimestamp())
                    )
                }
                for (nonMoved in chunk.nonMovedPromotionRecords) {
                    batch.set(
                        promsCol.document(nonMoved.id),
                        nonMoved.toFirestoreMap(FieldValue.serverTimestamp())
                    )
                }
                val progressRec = baseTransition.copy(completedBatches = chunk.batchIndex + 1)
                batch.set(transitionRef, progressRec.toFirestoreMap(FieldValue.serverTimestamp()))
                val tChunk = batch.commit()
                attachSyncListeners(
                    tChunk,
                    OperationType.WRITE,
                    "organisations/$orgId/sessionTransitions/${plan.transitionId}/batch_${chunk.batchIndex}"
                )
            }

            // Final batch: update org settings (currentSession + sessionsCsv) and mark transition COMPLETED
            val finalBatch = db.batch()
            val updatedSessionsCsv = SessionTransitionEngine.appendSessionToCsv(currentSettings.sessionsCsv, toSession)
            val updatedSettings = currentSettings.copy(
                currentSession = toSession,
                sessionsCsv = updatedSessionsCsv
            )
            finalBatch.update(
                orgDoc(orgId),
                mapOf(
                    "settings" to updatedSettings.toMap(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val completedRecord = baseTransition.copy(
                status = SessionTransitionStatus.COMPLETED,
                completedBatches = plan.chunks.size
            )
            finalBatch.set(transitionRef, completedRecord.toFirestoreMap(FieldValue.serverTimestamp()))
            val tFinal = finalBatch.commit()
            attachSyncListeners(tFinal, OperationType.WRITE, "organisations/$orgId/sessionTransitions/${plan.transitionId}/complete")

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "SESSION_STARTED_AND_PROMOTED",
                targetType = "sessionTransition",
                targetId = plan.transitionId,
                summary = "Started $toSession from $fromSession: ${plan.promotedCount} promoted, ${plan.gracePromotedCount} grace, ${plan.detainedCount} detained, ${plan.passedOutCount} alumni, Rs.${plan.totalDuesCarriedForwardRs} dues C/F",
                schoolId = schoolId
            )
            Result.success(completedRecord)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. OWNER-ONLY 7-DAY UNDO OR IN-PROGRESS ROLLBACK
    // =========================================================================
    fun undoOrRollbackTransition(
        orgId: String,
        transition: SessionTransitionRecord,
        isOwnerUndo: Boolean,
        currentSettings: CloudOrgSettings,
        actorName: String
    ): Result<Unit> {
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val studentsCol = orgDoc(orgId).collection("schools").document(transition.schoolId).collection("students")
            val overridesCol = orgDoc(orgId).collection("studentFeeOverrides")
            val promsCol = orgDoc(orgId).collection("promotionRecords")

            // Delete only the documents created by this session transition in safe chunks (<= 150 ops/batch)
            val deleteRefs = buildList {
                transition.createdStudentDocIds.forEach { id ->
                    if (id.isNotBlank()) add(studentsCol.document(id))
                }
                transition.createdFeeOverrideDocIds.forEach { id ->
                    if (id.isNotBlank()) add(overridesCol.document(id))
                }
                transition.createdPromotionRecordIds.forEach { id ->
                    if (id.isNotBlank()) add(promsCol.document(id))
                }
                if (transition.createdFeeStructureDocId.isNotBlank()) {
                    add(orgDoc(orgId).collection("feeStructures").document(transition.createdFeeStructureDocId))
                }
                if (transition.createdCalendarDocId.isNotBlank()) {
                    add(orgDoc(orgId).collection("schoolCalendars").document(transition.createdCalendarDocId))
                }
            }

            deleteRefs.chunked(150).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { ref -> batch.delete(ref) }
                val task = batch.commit()
                attachSyncListeners(task, OperationType.DELETE, "organisations/$orgId/sessionTransitions/${transition.id}/undo")
            }

            // Restore previous session & update transition status
            val finalBatch = db.batch()
            val restoredSession = transition.previousCurrentSession.ifBlank { transition.fromSession }
            val restoredCsv = transition.previousSessionsCsv.ifBlank { currentSettings.sessionsCsv }
            val restoredSettings = currentSettings.copy(
                currentSession = restoredSession,
                sessionsCsv = restoredCsv
            )
            finalBatch.update(
                orgDoc(orgId),
                mapOf(
                    "settings" to restoredSettings.toMap(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val updatedTransition = transition.copy(
                status = if (isOwnerUndo) SessionTransitionStatus.UNDONE else SessionTransitionStatus.ROLLED_BACK,
                undoneByUid = uid,
                undoneByName = actorName.ifBlank { "Owner" },
                undoneAtMs = nowMs
            )
            finalBatch.set(
                orgDoc(orgId).collection("sessionTransitions").document(transition.id),
                updatedTransition.toFirestoreMap(FieldValue.serverTimestamp())
            )
            val tFinal = finalBatch.commit()
            attachSyncListeners(tFinal, OperationType.UPDATE, "organisations/$orgId/sessionTransitions/${transition.id}")

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = if (isOwnerUndo) "SESSION_START_UNDONE" else "SESSION_START_ROLLED_BACK",
                targetType = "sessionTransition",
                targetId = transition.id,
                summary = "${if (isOwnerUndo) "Undid" else "Rolled back"} session ${transition.toSession} and restored ${restoredSession}",
                schoolId = transition.schoolId
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. BULK STUDENT IMPORT (Owner & Admin ONLY)
    // =========================================================================
    fun commitBulkStudentImport(
        orgId: String,
        schoolId: String,
        schoolStableId: Long,
        schoolName: String,
        session: String,
        fileName: String,
        previewRows: List<BulkImportPreviewRow>,
        existingStudentsByDocId: Map<String, StudentEntity>,
        classDocIdByClassName: Map<String, String>,
        updateMatchingEnabled: Boolean,
        actorName: String
    ): Result<BulkImportExecutionReport> {
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val studentsCol = orgDoc(orgId).collection("schools").document(schoolId).collection("students")

            // Track next roll numbers per (class, section) for rows with blank rollNo
            val maxRollByClassSec = mutableMapOf<String, Int>()
            existingStudentsByDocId.values
                .filter { it.session.equals(session, ignoreCase = true) }
                .forEach { st ->
                    val key = "${st.className.lowercase(Locale.US)}|${st.section.uppercase(Locale.US)}"
                    maxRollByClassSec[key] = maxOf(maxRollByClassSec[key] ?: 0, st.rollNo)
                }

            val actionable = previewRows.filter { it.action == ImportRowAction.ADD || it.action == ImportRowAction.UPDATE }
            val sessionTag = session.filter { it.isDigit() }.take(4).ifBlank { "26" }

            actionable.chunked(120).forEach { chunk ->
                val batch = db.batch()
                for (row in chunk) {
                    val v = row.values
                    val classDocId = classDocIdByClassName[v.className]
                        ?: classDocIdByClassName.entries.firstOrNull { it.key.equals(v.className, ignoreCase = true) }?.value
                        ?: ""
                    val secKey = "${v.className.lowercase(Locale.US)}|${v.section.uppercase(Locale.US)}"
                    val parsedRoll = v.rollNo.toIntOrNull()?.takeIf { it in 1..9999 }
                    val resolvedRoll = parsedRoll ?: ((maxRollByClassSec[secKey] ?: 0) + 1)
                    maxRollByClassSec[secKey] = maxOf(maxRollByClassSec[secKey] ?: 0, resolvedRoll)

                    val resolvedAdmNo = v.admNo.trim().ifBlank {
                        "IMP-$sessionTag-${row.rowNumber}-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}"
                    }
                    val mobileToStore = row.normalizedMobile.ifBlank { v.mobile.trim() }
                    val secMobileToStore = row.normalizedSecondMobile.ifBlank { v.secondMobile.trim() }

                    if (row.action == ImportRowAction.UPDATE && !row.matchedExistingDocId.isNullOrBlank()) {
                        val existing = existingStudentsByDocId[row.matchedExistingDocId]
                        val merged = (existing ?: StudentEntity(
                            schoolId = schoolStableId,
                            session = session,
                            admNo = resolvedAdmNo,
                            name = v.name,
                            fatherName = v.fatherName,
                            motherName = v.motherName,
                            dob = v.dob,
                            mobile = mobileToStore,
                            address = v.address,
                            className = v.className,
                            section = v.section,
                            rollNo = resolvedRoll
                        )).copy(
                            name = v.name.ifBlank { existing?.name.orEmpty() },
                            className = v.className.ifBlank { existing?.className.orEmpty() },
                            section = v.section.ifBlank { existing?.section ?: "A" },
                            rollNo = parsedRoll ?: (existing?.rollNo ?: resolvedRoll),
                            fatherName = v.fatherName.ifBlank { existing?.fatherName.orEmpty() },
                            motherName = v.motherName.ifBlank { existing?.motherName.orEmpty() },
                            dob = v.dob.ifBlank { existing?.dob.orEmpty() },
                            mobile = mobileToStore.ifBlank { existing?.mobile.orEmpty() },
                            secondMobile = secMobileToStore.ifBlank { existing?.secondMobile.orEmpty() },
                            address = v.address.ifBlank { existing?.address.orEmpty() },
                            admissionDate = v.admissionDate.ifBlank { existing?.admissionDate.orEmpty() },
                            previousSchool = v.previousSchool.ifBlank { existing?.previousSchool.orEmpty() },
                            usesTransport = v.usesTransport,
                            transportMonthlyAmount = if (v.usesTransport) v.transportAmount else 0,
                            transportRouteName = if (v.usesTransport) v.transportRoute.ifBlank { existing?.transportRouteName.orEmpty() } else ""
                        )
                        batch.update(
                            studentsCol.document(row.matchedExistingDocId),
                            buildStudentUpdatePayload(classDocId = classDocId, student = merged)
                        )
                    } else {
                        val newDocId = "stu_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                        val entity = StudentEntity(
                            schoolId = schoolStableId,
                            session = session,
                            admNo = resolvedAdmNo,
                            name = v.name.trim(),
                            fatherName = v.fatherName.trim(),
                            motherName = v.motherName.trim(),
                            dob = v.dob.trim(),
                            mobile = mobileToStore,
                            secondMobile = secMobileToStore,
                            address = v.address.trim(),
                            className = v.className.trim(),
                            section = v.section.ifBlank { "A" },
                            rollNo = resolvedRoll,
                            admissionDate = v.admissionDate.trim(),
                            previousSchool = v.previousSchool.trim(),
                            usesTransport = v.usesTransport,
                            transportRouteName = if (v.usesTransport) v.transportRoute.trim() else "",
                            transportMonthlyAmount = if (v.usesTransport) v.transportAmount else 0,
                            status = "ACTIVE"
                        )
                        batch.set(
                            studentsCol.document(newDocId),
                            buildStudentCreatePayload(schoolDocId = schoolId, classDocId = classDocId, student = entity)
                        )
                    }
                }
                val t = batch.commit()
                attachSyncListeners(t, OperationType.WRITE, "organisations/$orgId/schools/$schoolId/students/bulkImport")
            }

            val addedCount = previewRows.count { it.action == ImportRowAction.ADD }
            val updatedCount = previewRows.count { it.action == ImportRowAction.UPDATE }
            val skippedCount = previewRows.count { it.action == ImportRowAction.SKIP }
            val errorCount = previewRows.count { it.action == ImportRowAction.ERROR }

            val logId = "imp_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val logRec = BulkImportLogRecord(
                id = logId,
                schoolId = schoolId,
                session = session,
                fileName = fileName,
                totalRows = previewRows.size,
                addedCount = addedCount,
                updatedCount = updatedCount,
                skippedCount = skippedCount,
                errorCount = errorCount,
                updateMatchingEnabled = updateMatchingEnabled,
                actorUid = uid,
                actorName = actorName.ifBlank { "Admin" },
                importedAtMs = nowMs
            )
            val logRef = orgDoc(orgId).collection("bulkImportLogs").document(logId)
            val logPayload = logRec.toFirestoreMap(FieldValue.serverTimestamp())
            attachSyncListeners(logRef.set(logPayload), OperationType.CREATE, "organisations/$orgId/bulkImportLogs/$logId")

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "BULK_STUDENT_IMPORT",
                targetType = "bulkImport",
                targetId = logId,
                summary = "Imported $fileName ($session): $addedCount added, $updatedCount updated, $skippedCount skipped, $errorCount errors",
                schoolId = schoolId
            )

            Result.success(
                BulkImportExecutionReport(
                    schoolName = schoolName,
                    session = session,
                    fileName = fileName,
                    totalRows = previewRows.size,
                    addedCount = addedCount,
                    updatedCount = updatedCount,
                    skippedCount = skippedCount,
                    errorCount = errorCount,
                    updateMatchingEnabled = updateMatchingEnabled,
                    actorName = actorName.ifBlank { "Admin" },
                    importedAtMs = nowMs,
                    rows = previewRows
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 5. RECORD BACKUP EXPORT & UPDATE lastBackupEpochMs (Owner & Admin ONLY)
    // =========================================================================
    fun recordBackupExport(
        orgId: String,
        scope: BackupExportScope,
        schoolId: String,
        fileName: String,
        includedSalaryCsv: Boolean,
        studentCount: Int,
        receiptCount: Int,
        actorName: String,
        actorRole: String,
        currentSettings: CloudOrgSettings,
        exportedAtMs: Long = System.currentTimeMillis()
    ): Result<Unit> {
        return try {
            val uid = requireUid()
            val logId = "bkp_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val logRec = BackupExportLogRecord(
                id = logId,
                scope = scope,
                schoolId = schoolId,
                fileName = fileName,
                includedSalaryCsv = includedSalaryCsv,
                studentCount = studentCount,
                receiptCount = receiptCount,
                actorUid = uid,
                actorName = actorName.ifBlank { "Admin" },
                actorRole = if (actorRole.equals("OWNER", ignoreCase = true)) "OWNER" else "ADMIN",
                exportedAtMs = exportedAtMs
            )

            val batch = db.batch()
            batch.set(
                orgDoc(orgId).collection("backupExportLogs").document(logId),
                logRec.toFirestoreMap(FieldValue.serverTimestamp())
            )
            val updatedSettings = currentSettings.copy(lastBackupEpochMs = exportedAtMs)
            batch.update(
                orgDoc(orgId),
                mapOf(
                    "settings" to updatedSettings.toMap(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            attachSyncListeners(batch.commit(), OperationType.WRITE, "organisations/$orgId/backupExportLogs/$logId")

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "BACKUP_ZIP_EXPORTED",
                targetType = "backupExport",
                targetId = logId,
                summary = "Exported ${scope.labelEn} backup ($fileName): $studentCount students, $receiptCount receipts${if (includedSalaryCsv) " (incl. salary)" else ""}",
                schoolId = schoolId.ifBlank { null }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildStudentCreatePayload(
        schoolDocId: String,
        classDocId: String,
        student: StudentEntity
    ): Map<String, Any> {
        val normalizedStatus = if (student.status.equals("LEFT", ignoreCase = true)) "LEFT" else "ACTIVE"
        return mapOf(
            "schoolId" to schoolDocId,
            "classId" to classDocId.take(128),
            "session" to student.session.trim().take(30),
            "admNo" to student.admNo.trim().ifBlank { "ADM" }.take(60),
            "name" to student.name.trim().take(120),
            "fatherName" to student.fatherName.trim().take(120),
            "motherName" to student.motherName.trim().take(120),
            "dob" to student.dob.trim().take(40),
            "mobile" to student.mobile.trim().take(40),
            "secondMobile" to student.secondMobile.trim().take(40),
            "address" to student.address.trim().take(300),
            "photoBase64" to student.photoBase64.take(215_000),
            "className" to student.className.trim().take(40),
            "section" to student.section.trim().uppercase(Locale.US).ifBlank { "A" }.take(10),
            "rollNo" to student.rollNo.coerceIn(1, 9999).toLong(),
            "admissionDate" to student.admissionDate.trim().take(40),
            "previousSchool" to student.previousSchool.trim().take(160),
            "siblingGroupId" to student.siblingGroupId.trim().take(80),
            "usesTransport" to student.usesTransport,
            "transportRouteId" to student.transportRouteId.trim().take(128),
            "transportRouteName" to student.transportRouteName.trim().take(120),
            "transportStopName" to student.transportStopName.trim().take(80),
            "transportMonthlyAmount" to student.transportMonthlyAmount.coerceIn(0, 500_000).toLong(),
            "status" to normalizedStatus,
            "leftDate" to (if (normalizedStatus == "LEFT") student.leftDate.trim().take(40) else ""),
            "leftReason" to (if (normalizedStatus == "LEFT") student.leftReason.trim().take(300) else ""),
            "sample" to student.isSample,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun buildStudentUpdatePayload(
        classDocId: String,
        student: StudentEntity
    ): Map<String, Any> {
        val normalizedStatus = if (student.status.equals("LEFT", ignoreCase = true)) "LEFT" else "ACTIVE"
        return mapOf(
            "classId" to classDocId.take(128),
            "session" to student.session.trim().take(30),
            "admNo" to student.admNo.trim().ifBlank { "ADM" }.take(60),
            "name" to student.name.trim().take(120),
            "fatherName" to student.fatherName.trim().take(120),
            "motherName" to student.motherName.trim().take(120),
            "dob" to student.dob.trim().take(40),
            "mobile" to student.mobile.trim().take(40),
            "secondMobile" to student.secondMobile.trim().take(40),
            "address" to student.address.trim().take(300),
            "photoBase64" to student.photoBase64.take(215_000),
            "className" to student.className.trim().take(40),
            "section" to student.section.trim().uppercase(Locale.US).ifBlank { "A" }.take(10),
            "rollNo" to student.rollNo.coerceIn(1, 9999).toLong(),
            "admissionDate" to student.admissionDate.trim().take(40),
            "previousSchool" to student.previousSchool.trim().take(160),
            "siblingGroupId" to student.siblingGroupId.trim().take(80),
            "usesTransport" to student.usesTransport,
            "transportRouteId" to student.transportRouteId.trim().take(128),
            "transportRouteName" to student.transportRouteName.trim().take(120),
            "transportStopName" to student.transportStopName.trim().take(80),
            "transportMonthlyAmount" to student.transportMonthlyAmount.coerceIn(0, 500_000).toLong(),
            "status" to normalizedStatus,
            "leftDate" to (if (normalizedStatus == "LEFT") student.leftDate.trim().take(40) else ""),
            "leftReason" to (if (normalizedStatus == "LEFT") student.leftReason.trim().take(300) else ""),
            "sample" to student.isSample,
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}
