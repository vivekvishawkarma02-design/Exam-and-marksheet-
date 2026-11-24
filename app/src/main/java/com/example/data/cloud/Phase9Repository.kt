package com.example.data.cloud

import com.example.domain.FeeAuditLogRecord
import com.example.domain.FeeMessageSentLog
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeReceiptStatus
import com.example.domain.FeeStructureConfig
import com.example.domain.MemberAccessProfile
import com.example.domain.OptionalFeeCatalogueItem
import com.example.domain.OrgAccessEvaluator
import com.example.domain.Phase9AccessEvaluator
import com.example.domain.SchoolNoticeRecord
import com.example.domain.StudentFeeOverride
import com.example.util.OperationType
import com.example.util.friendlyFirestoreErrorMessage
import com.example.util.handleFirestoreError
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
import java.util.UUID

data class CloudPhase9Snapshot(
    val feeStructures: List<FeeStructureConfig> = emptyList(),
    val studentFeeOverrides: List<StudentFeeOverride> = emptyList(),
    val feeReceipts: List<FeeReceiptRecord> = emptyList(),
    val messageSentLogs: List<FeeMessageSentLog> = emptyList(),
    val schoolNotices: List<SchoolNoticeRecord> = emptyList(),
    val feeAuditLogs: List<FeeAuditLogRecord> = emptyList(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase9Slice<T>(
    val value: T,
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false
)

/**
 * Phase 9 Offline-First Fee Management & Notice Repository:
 * - `organisations/{orgId}/feeStructures/{structureId}`: per-school + session fee structure
 * - `organisations/{orgId}/studentFeeOverrides/{overrideId}`: per-student fee overrides with mandatory remark
 * - `organisations/{orgId}/feeReceipts/{receiptId}`: issued receipts (immutable line items & amounts;
 *   only cancellation status fields can transition from ISSUED -> CANCELLED; never deleted)
 * - `organisations/{orgId}/feeMessageSentLogs/{logId}`: deterministic sent logs per message type & context
 * - `organisations/{orgId}/schoolNotices/{noticeId}`: school notices for WhatsApp group sharing
 * - `organisations/{orgId}/feeAuditLogs/{auditId}`: append-only fee audit trail
 */
class Phase9Repository(
    private val orgRepo: OrgCloudRepository,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val db: FirebaseFirestore get() = orgRepo.db

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    @Volatile
    private var lastRetryAction: (() -> Unit)? = null

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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync fee changes.")
            if (retryAction != null) lastRetryAction = retryAction
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

    private fun requireUid(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Must be signed in to perform fee operations.")

    // =========================================================================
    // 1. REAL-TIME OBSERVERS (Strictly gated by AppModule.FEES view access)
    // =========================================================================
    fun observePhase9Data(
        orgId: String,
        member: MemberAccessProfile,
        currentUid: String
    ): Flow<CloudPhase9Snapshot> {
        if (!OrgAccessEvaluator.isActive(member) || currentUid.isBlank()) {
            return flowOf(CloudPhase9Snapshot())
        }
        if (!Phase9AccessEvaluator.canViewFees(member)) {
            return flowOf(CloudPhase9Snapshot())
        }

        val orgRef = db.collection("organisations").document(orgId)

        val structuresFlow: Flow<Phase9Slice<List<FeeStructureConfig>>> = orgRef
            .collection("feeStructures")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val items = snap.documents.mapNotNull { doc ->
                    FeeStructureConfig.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                Phase9Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
            }
            .catch { e ->
                handleFlowError(e, "organisations/$orgId/feeStructures")
                emit(Phase9Slice(emptyList()))
            }

        val overridesFlow: Flow<Phase9Slice<List<StudentFeeOverride>>> = orgRef
            .collection("studentFeeOverrides")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val items = snap.documents.mapNotNull { doc ->
                    StudentFeeOverride.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                Phase9Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
            }
            .catch { e ->
                handleFlowError(e, "organisations/$orgId/studentFeeOverrides")
                emit(Phase9Slice(emptyList()))
            }

        val receiptsFlow: Flow<Phase9Slice<List<FeeReceiptRecord>>> = orgRef
            .collection("feeReceipts")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val items = snap.documents.mapNotNull { doc ->
                    FeeReceiptRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    .sortedWith(compareByDescending<FeeReceiptRecord> { it.issuedAtMs }.thenByDescending { it.sequenceNo })
                Phase9Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
            }
            .catch { e ->
                handleFlowError(e, "organisations/$orgId/feeReceipts")
                emit(Phase9Slice(emptyList()))
            }

        val sentLogsFlow: Flow<Phase9Slice<List<FeeMessageSentLog>>> = orgRef
            .collection("feeMessageSentLogs")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val items = snap.documents.mapNotNull { doc ->
                    FeeMessageSentLog.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    .sortedByDescending { it.sentAtMs }
                Phase9Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
            }
            .catch { e ->
                handleFlowError(e, "organisations/$orgId/feeMessageSentLogs")
                emit(Phase9Slice(emptyList()))
            }

        val noticesFlow: Flow<Phase9Slice<List<SchoolNoticeRecord>>> = orgRef
            .collection("schoolNotices")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val items = snap.documents.mapNotNull { doc ->
                    SchoolNoticeRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    .sortedByDescending { it.createdAtMs }
                Phase9Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
            }
            .catch { e ->
                handleFlowError(e, "organisations/$orgId/schoolNotices")
                emit(Phase9Slice(emptyList()))
            }

        val auditLogsFlow: Flow<Phase9Slice<List<FeeAuditLogRecord>>> = orgRef
            .collection("feeAuditLogs")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val items = snap.documents.mapNotNull { doc ->
                    FeeAuditLogRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    .sortedByDescending { it.clientCreatedAtMs }
                Phase9Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
            }
            .catch { e ->
                handleFlowError(e, "organisations/$orgId/feeAuditLogs")
                emit(Phase9Slice(emptyList()))
            }

        val leftCombo = combine(structuresFlow, overridesFlow, receiptsFlow) { s, o, r -> Triple(s, o, r) }
        val rightCombo = combine(sentLogsFlow, noticesFlow, auditLogsFlow) { m, n, a -> Triple(m, n, a) }

        return combine(leftCombo, rightCombo) { (st, ov, rcp), (msg, not, aud) ->
            val totalPending = st.pendingCount + ov.pendingCount + rcp.pendingCount +
                msg.pendingCount + not.pendingCount + aud.pendingCount
            val fromCache = st.isFromCache && rcp.isFromCache
            CloudPhase9Snapshot(
                feeStructures = st.value,
                studentFeeOverrides = ov.value,
                feeReceipts = rcp.value,
                messageSentLogs = msg.value,
                schoolNotices = not.value,
                feeAuditLogs = aud.value,
                pendingWriteCount = totalPending,
                isFromCache = fromCache
            )
        }
    }

    private fun handleFlowError(e: Throwable, path: String) {
        val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
        if (cause != null) {
            handleFirestoreError(cause, OperationType.LIST, path)
            return
        }
        if (e is CancellationException) throw e
    }

    // =========================================================================
    // 2. FEE STRUCTURE & STUDENT OVERRIDES CRUD (Offline-First)
    // =========================================================================
    fun saveFeeStructure(
        orgId: String,
        config: FeeStructureConfig,
        existsInCloud: Boolean,
        auditNote: String = ""
    ): Result<String> {
        val docId = config.id.ifBlank { FeeStructureConfig.makeDocId(config.schoolId, config.session) }
        val path = "organisations/$orgId/feeStructures/$docId"
        return try {
            val uid = requireUid()
            val editorName = orgRepo.currentEditorName().take(120)
            val nowMs = System.currentTimeMillis()
            val orgRef = db.collection("organisations").document(orgId)
            val ref = orgRef.collection("feeStructures").document(docId)
            val baseMap = config.toFirestoreMap(uid, editorName, nowMs)
            val auditId = "faud_${nowMs}_${UUID.randomUUID().toString().take(6)}"
            val auditRef = orgRef.collection("feeAuditLogs").document(auditId)
            val summary = "Updated fee structure for session ${config.session}${if (auditNote.isNotBlank()) " ($auditNote)" else ""}"

            val runWrite = {
                val batch = db.batch()
                if (existsInCloud) {
                    batch.update(ref, baseMap + ("updatedAt" to FieldValue.serverTimestamp()))
                } else {
                    batch.set(
                        ref,
                        baseMap + mapOf(
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.set(
                    auditRef,
                    buildFeeAuditPayload(
                        schoolId = config.schoolId,
                        session = config.session,
                        action = "FEE_STRUCTURE_SAVED",
                        targetId = docId,
                        studentId = "",
                        studentName = "",
                        summary = summary,
                        actorUid = uid,
                        actorName = editorName,
                        nowMs = nowMs
                    )
                )
                batch.commit()
            }
            val task = runWrite()
            attachSyncListeners(task, OperationType.WRITE, path) { runWrite() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "FEE_STRUCTURE_SAVED",
                targetType = "feeStructure",
                targetId = docId,
                summary = summary.take(300),
                schoolId = config.schoolId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun saveStudentFeeOverride(
        orgId: String,
        override: StudentFeeOverride,
        existsInCloud: Boolean
    ): Result<String> {
        val cleanRemark = override.remark.trim()
        if (cleanRemark.isEmpty()) {
            return Result.failure(IllegalArgumentException("A remark is required when overriding a student's fee amount."))
        }
        val docId = override.id.ifBlank {
            StudentFeeOverride.makeDocId(override.schoolId, override.session, override.studentId)
        }
        val path = "organisations/$orgId/studentFeeOverrides/$docId"
        return try {
            val uid = requireUid()
            val editorName = orgRepo.currentEditorName().take(120)
            val nowMs = System.currentTimeMillis()
            val orgRef = db.collection("organisations").document(orgId)
            val ref = orgRef.collection("studentFeeOverrides").document(docId)
            val baseMap = override.copy(remark = cleanRemark).toFirestoreMap(uid, editorName, nowMs)
            val auditId = "faud_${nowMs}_${UUID.randomUUID().toString().take(6)}"
            val auditRef = orgRef.collection("feeAuditLogs").document(auditId)
            val summary = "Fee override for ${override.studentName.ifBlank { override.studentId }}: $cleanRemark"

            val runWrite = {
                val batch = db.batch()
                if (existsInCloud) {
                    batch.update(ref, baseMap + ("updatedAt" to FieldValue.serverTimestamp()))
                } else {
                    batch.set(
                        ref,
                        baseMap + mapOf(
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.set(
                    auditRef,
                    buildFeeAuditPayload(
                        schoolId = override.schoolId,
                        session = override.session,
                        action = "STUDENT_FEE_OVERRIDE_SAVED",
                        targetId = docId,
                        studentId = override.studentId,
                        studentName = override.studentName,
                        summary = summary,
                        actorUid = uid,
                        actorName = editorName,
                        nowMs = nowMs
                    )
                )
                batch.commit()
            }
            val task = runWrite()
            attachSyncListeners(task, OperationType.WRITE, path) { runWrite() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "STUDENT_FEE_OVERRIDE_SAVED",
                targetType = "feeOverride",
                targetId = docId,
                summary = summary.take(300),
                schoolId = override.schoolId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun deleteStudentFeeOverride(
        orgId: String,
        schoolId: String,
        session: String,
        studentDocId: String,
        studentName: String
    ): Result<Unit> {
        val docId = StudentFeeOverride.makeDocId(schoolId, session, studentDocId)
        val path = "organisations/$orgId/studentFeeOverrides/$docId"
        return try {
            val uid = requireUid()
            val editorName = orgRepo.currentEditorName().take(120)
            val nowMs = System.currentTimeMillis()
            val orgRef = db.collection("organisations").document(orgId)
            val ref = orgRef.collection("studentFeeOverrides").document(docId)
            val auditId = "faud_${nowMs}_${UUID.randomUUID().toString().take(6)}"
            val auditRef = orgRef.collection("feeAuditLogs").document(auditId)
            val summary = "Removed fee override for ${studentName.ifBlank { studentDocId }}"

            val runWrite = {
                val batch = db.batch()
                batch.delete(ref)
                batch.set(
                    auditRef,
                    buildFeeAuditPayload(
                        schoolId = schoolId,
                        session = session,
                        action = "STUDENT_FEE_OVERRIDE_REMOVED",
                        targetId = docId,
                        studentId = studentDocId,
                        studentName = studentName,
                        summary = summary,
                        actorUid = uid,
                        actorName = editorName,
                        nowMs = nowMs
                    )
                )
                batch.commit()
            }
            val task = runWrite()
            attachSyncListeners(task, OperationType.DELETE, path) { runWrite() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. FEE RECEIPT ISSUANCE & CANCELLATION (Immutable Receipts + Audit Trail)
    // =========================================================================
    fun issueFeeReceipt(
        orgId: String,
        receipt: FeeReceiptRecord,
        newCatalogueItemsToSave: List<OptionalFeeCatalogueItem> = emptyList(),
        existingStructure: FeeStructureConfig? = null,
        structureExistsInCloud: Boolean = false
    ): Result<FeeReceiptRecord> {
        if (receipt.amountPaid <= 0 || receipt.totalBeforeConcession <= 0) {
            return Result.failure(IllegalArgumentException("Receipt amount must be positive."))
        }
        if (receipt.concessionAmount > 0 && receipt.concessionRemark.isBlank()) {
            return Result.failure(IllegalArgumentException("A remark is required for any concession."))
        }
        val docId = receipt.id.ifBlank {
            FeeReceiptRecord.makeDocId(receipt.schoolId, receipt.session, receipt.receiptNumber)
        }
        val path = "organisations/$orgId/feeReceipts/$docId"
        return try {
            val uid = requireUid()
            val editorName = orgRepo.currentEditorName().take(120)
            val nowMs = System.currentTimeMillis()
            val orgRef = db.collection("organisations").document(orgId)
            val receiptRef = orgRef.collection("feeReceipts").document(docId)
            val finalizedReceipt = receipt.copy(
                id = docId,
                receivedByUid = uid,
                receivedByName = editorName,
                concessionGivenByUid = if (receipt.concessionAmount > 0) uid else "",
                concessionGivenByName = if (receipt.concessionAmount > 0) editorName else "",
                status = FeeReceiptStatus.ISSUED,
                issuedAtMs = nowMs
            )
            val receiptPayload = finalizedReceipt.toCreateFirestoreMap() + mapOf(
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val auditId = "faud_${nowMs}_${UUID.randomUUID().toString().take(6)}"
            val auditRef = orgRef.collection("feeAuditLogs").document(auditId)
            val concPart = if (finalizedReceipt.concessionAmount > 0) {
                ", Concession Rs. ${finalizedReceipt.concessionAmount} (${finalizedReceipt.concessionRemark})"
            } else ""
            val summary = "Issued ${finalizedReceipt.receiptNumber} for ${finalizedReceipt.studentName} (${finalizedReceipt.className}-${finalizedReceipt.section}): Paid Rs. ${finalizedReceipt.amountPaid} via ${finalizedReceipt.paymentMode.label}$concPart"

            val runWrite = {
                val batch = db.batch()
                batch.set(receiptRef, receiptPayload)
                batch.set(
                    auditRef,
                    buildFeeAuditPayload(
                        schoolId = finalizedReceipt.schoolId,
                        session = finalizedReceipt.session,
                        action = "RECEIPT_ISSUED",
                        targetId = docId,
                        studentId = finalizedReceipt.studentId,
                        studentName = finalizedReceipt.studentName,
                        summary = summary,
                        actorUid = uid,
                        actorName = editorName,
                        nowMs = nowMs
                    )
                )
                // If user checked "Add this to the catalogue" on any custom charge, append to feeStructure
                if (newCatalogueItemsToSave.isNotEmpty() && existingStructure != null) {
                    val existingNames = existingStructure.optionalCatalogue.map { it.name.lowercase() }.toSet()
                    val toAdd = newCatalogueItemsToSave.filter { it.name.lowercase() !in existingNames }
                    if (toAdd.isNotEmpty()) {
                        val updatedStructure = existingStructure.copy(
                            optionalCatalogue = existingStructure.optionalCatalogue + toAdd
                        )
                        val strDocId = updatedStructure.id.ifBlank {
                            FeeStructureConfig.makeDocId(updatedStructure.schoolId, updatedStructure.session)
                        }
                        val strRef = orgRef.collection("feeStructures").document(strDocId)
                        val strMap = updatedStructure.toFirestoreMap(uid, editorName, nowMs)
                        if (structureExistsInCloud) {
                            batch.update(strRef, strMap + ("updatedAt" to FieldValue.serverTimestamp()))
                        } else {
                            batch.set(
                                strRef,
                                strMap + mapOf(
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                }
                batch.commit()
            }
            val task = runWrite()
            attachSyncListeners(task, OperationType.CREATE, path) { runWrite() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "FEE_RECEIPT_ISSUED",
                targetType = "feeReceipt",
                targetId = docId,
                summary = summary.take(300),
                schoolId = finalizedReceipt.schoolId
            )
            Result.success(finalizedReceipt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Cancels an issued receipt with a mandatory reason.
     * Never deletes or edits the receipt's amounts, line items, or receipt number.
     */
    fun cancelFeeReceipt(
        orgId: String,
        receipt: FeeReceiptRecord,
        cancelReason: String
    ): Result<Unit> {
        val cleanReason = cancelReason.trim()
        if (cleanReason.isEmpty()) {
            return Result.failure(IllegalArgumentException("A cancellation reason is required."))
        }
        if (receipt.isCancelled) {
            return Result.failure(IllegalStateException("Receipt ${receipt.receiptNumber} is already cancelled."))
        }
        val path = "organisations/$orgId/feeReceipts/${receipt.id}"
        return try {
            val uid = requireUid()
            val editorName = orgRepo.currentEditorName().take(120)
            val nowMs = System.currentTimeMillis()
            val orgRef = db.collection("organisations").document(orgId)
            val receiptRef = orgRef.collection("feeReceipts").document(receipt.id)
            val auditId = "faud_${nowMs}_${UUID.randomUUID().toString().take(6)}"
            val auditRef = orgRef.collection("feeAuditLogs").document(auditId)
            val summary = "Cancelled receipt ${receipt.receiptNumber} (Rs. ${receipt.amountPaid}, ${receipt.studentName}): $cleanReason"

            val cancelPayload = mapOf(
                "status" to FeeReceiptStatus.CANCELLED.wireValue,
                "cancelledAtMs" to nowMs,
                "cancelledByUid" to uid,
                "cancelledByName" to editorName,
                "cancelReason" to cleanReason.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val runWrite = {
                val batch = db.batch()
                batch.update(receiptRef, cancelPayload)
                batch.set(
                    auditRef,
                    buildFeeAuditPayload(
                        schoolId = receipt.schoolId,
                        session = receipt.session,
                        action = "RECEIPT_CANCELLED",
                        targetId = receipt.id,
                        studentId = receipt.studentId,
                        studentName = receipt.studentName,
                        summary = summary,
                        actorUid = uid,
                        actorName = editorName,
                        nowMs = nowMs
                    )
                )
                batch.commit()
            }
            val task = runWrite()
            attachSyncListeners(task, OperationType.UPDATE, path) { runWrite() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "FEE_RECEIPT_CANCELLED",
                targetType = "feeReceipt",
                targetId = receipt.id,
                summary = summary.take(300),
                schoolId = receipt.schoolId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. PART D2: SEND QUEUE SENT LOGS & NOTICE MAKER (Offline-First)
    // =========================================================================
    fun markMessageSent(
        orgId: String,
        log: FeeMessageSentLog
    ): Result<String> {
        val docId = log.id.ifBlank {
            FeeMessageSentLog.makeDeterministicId(
                schoolId = log.schoolId,
                session = log.session,
                studentDocId = log.studentId,
                messageType = log.messageType,
                contextKey = log.contextKey
            )
        }
        val path = "organisations/$orgId/feeMessageSentLogs/$docId"
        return try {
            val uid = requireUid()
            val editorName = orgRepo.currentEditorName().take(120)
            val nowMs = System.currentTimeMillis()
            val ref = db.collection("organisations").document(orgId)
                .collection("feeMessageSentLogs").document(docId)
            val finalized = log.copy(
                id = docId,
                sentByUid = uid,
                sentByName = editorName,
                sentAtMs = if (log.sentAtMs > 0L) log.sentAtMs else nowMs
            )
            val payload = finalized.toFirestoreMap() + mapOf(
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.CREATE, path) { ref.set(payload) }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun saveSchoolNotice(
        orgId: String,
        schoolId: String,
        title: String,
        bodyText: String,
        noticeDateIso: String
    ): Result<SchoolNoticeRecord> {
        val cleanTitle = title.trim()
        val cleanBody = bodyText.trim()
        if (cleanTitle.isEmpty() || cleanBody.isEmpty()) {
            return Result.failure(IllegalArgumentException("Notice title and message text are required."))
        }
        val nowMs = System.currentTimeMillis()
        val docId = "not_${nowMs}_${UUID.randomUUID().toString().take(6)}"
        val path = "organisations/$orgId/schoolNotices/$docId"
        return try {
            val uid = requireUid()
            val editorName = orgRepo.currentEditorName().take(120)
            val record = SchoolNoticeRecord(
                id = docId,
                schoolId = schoolId,
                title = cleanTitle.take(140),
                bodyText = cleanBody.take(2000),
                noticeDateIso = noticeDateIso.trim().take(20),
                createdByUid = uid,
                createdByName = editorName,
                createdAtMs = nowMs
            )
            val payload = record.toFirestoreMap() + mapOf(
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val ref = db.collection("organisations").document(orgId)
                .collection("schoolNotices").document(docId)
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.CREATE, path) { ref.set(payload) }
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun deleteSchoolNotice(
        orgId: String,
        noticeId: String
    ): Result<Unit> {
        val path = "organisations/$orgId/schoolNotices/$noticeId"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("schoolNotices").document(noticeId)
            val task = ref.delete()
            attachSyncListeners(task, OperationType.DELETE, path) { ref.delete() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun buildFeeAuditPayload(
        schoolId: String,
        session: String,
        action: String,
        targetId: String,
        studentId: String,
        studentName: String,
        summary: String,
        actorUid: String,
        actorName: String,
        nowMs: Long
    ): Map<String, Any> = mapOf(
        "schoolId" to schoolId,
        "session" to session.trim().take(30),
        "action" to action.trim().take(60),
        "targetId" to targetId.trim().take(128),
        "studentId" to studentId.trim().take(128),
        "studentName" to studentName.trim().take(120),
        "summary" to summary.trim().take(400),
        "actorUid" to actorUid,
        "actorName" to actorName.trim().take(120),
        "clientCreatedAtMs" to nowMs,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
