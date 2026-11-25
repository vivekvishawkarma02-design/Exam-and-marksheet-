package com.example.data.cloud

import com.example.domain.AdmissionEnquiryRecord
import com.example.domain.CashBookAuditLogRecord
import com.example.domain.CashBookDayClosingRecord
import com.example.domain.CashBookEntryRecord
import com.example.domain.CashBookEntryStatus
import com.example.domain.CashBookSettingsConfig
import com.example.domain.CertificateStatus
import com.example.domain.EnquirySettingsConfig
import com.example.domain.EnquiryStatus
import com.example.domain.FeeReceiptRecord
import com.example.domain.MemberAccessProfile
import com.example.domain.OrgAccessEvaluator
import com.example.domain.PaymentMode
import com.example.domain.Phase10AccessEvaluator
import com.example.domain.SchoolCertificateRecord
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

data class CloudPhase10Snapshot(
    val certificates: List<SchoolCertificateRecord> = emptyList(),
    val cashBookSettings: List<CashBookSettingsConfig> = emptyList(),
    val cashBookEntries: List<CashBookEntryRecord> = emptyList(),
    val cashBookDayClosings: List<CashBookDayClosingRecord> = emptyList(),
    val cashBookAuditLogs: List<CashBookAuditLogRecord> = emptyList(),
    val cashBookFeeReceipts: List<FeeReceiptRecord> = emptyList(),
    val enquiries: List<AdmissionEnquiryRecord> = emptyList(),
    val enquirySettings: List<EnquirySettingsConfig> = emptyList(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase10Slice<T>(
    val value: T,
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase10CashBookSlice(
    val settings: Phase10Slice<List<CashBookSettingsConfig>> = Phase10Slice(emptyList()),
    val entries: Phase10Slice<List<CashBookEntryRecord>> = Phase10Slice(emptyList()),
    val closings: Phase10Slice<List<CashBookDayClosingRecord>> = Phase10Slice(emptyList()),
    val auditLogs: Phase10Slice<List<CashBookAuditLogRecord>> = Phase10Slice(emptyList()),
    val feeReceipts: Phase10Slice<List<FeeReceiptRecord>> = Phase10Slice(emptyList())
)

/**
 * Phase 10 Offline-First Repository for:
 * 1. Certificates (`organisations/{orgId}/schoolCertificates/{certId}`) — immutable after issue, cancel-only
 * 2. Cash Book (`cashBookSettings`, `cashBookEntries`, `cashBookDayClosings`, `cashBookAuditLogs` + read-only `feeReceipts`)
 * 3. Admission Enquiry (`admissionEnquiries`, `enquirySettings`)
 *
 * Every listener is strictly gated by its corresponding module permission so a user without
 * that module never attaches listeners and never triggers PERMISSION_DENIED.
 */
class Phase10Repository(
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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync Phase 10 changes.")
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
            ?: throw IllegalStateException("Must be signed in to perform this action.")

    private fun handleFlowError(e: Throwable, path: String) {
        if (e is CancellationException) throw e
        val ffe = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
        if (ffe != null) {
            handleFirestoreError(ffe, OperationType.LIST, path)
        }
    }

    // =========================================================================
    // 1. REAL-TIME OBSERVERS (Strictly gated per Phase 10 module)
    // =========================================================================
    fun observePhase10Data(
        orgId: String,
        member: MemberAccessProfile,
        currentUid: String
    ): Flow<CloudPhase10Snapshot> {
        if (!OrgAccessEvaluator.isActive(member) || currentUid.isBlank()) {
            return flowOf(CloudPhase10Snapshot())
        }
        val canCerts = Phase10AccessEvaluator.canViewCertificates(member)
        val canCash = Phase10AccessEvaluator.canViewCashBook(member)
        val canEnq = Phase10AccessEvaluator.canViewEnquiry(member)
        if (!canCerts && !canCash && !canEnq) {
            return flowOf(CloudPhase10Snapshot())
        }

        val orgRef = db.collection("organisations").document(orgId)

        val certsFlow: Flow<Phase10Slice<List<SchoolCertificateRecord>>> = if (canCerts) {
            orgRef.collection("schoolCertificates")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        SchoolCertificateRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase10Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/schoolCertificates")
                    emit(Phase10Slice(emptyList()))
                }
        } else {
            flowOf(Phase10Slice(emptyList()))
        }

        val cashBookFlow: Flow<Phase10CashBookSlice> = if (canCash) {
            val cbSettingsFlow = orgRef.collection("cashBookSettings")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        CashBookSettingsConfig.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase10Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/cashBookSettings")
                    emit(Phase10Slice(emptyList()))
                }

            val cbEntriesFlow = orgRef.collection("cashBookEntries")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        CashBookEntryRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase10Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/cashBookEntries")
                    emit(Phase10Slice(emptyList()))
                }

            val cbClosingsFlow = orgRef.collection("cashBookDayClosings")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        CashBookDayClosingRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase10Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/cashBookDayClosings")
                    emit(Phase10Slice(emptyList()))
                }

            val cbAuditFlow = orgRef.collection("cashBookAuditLogs")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        CashBookAuditLogRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase10Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/cashBookAuditLogs")
                    emit(Phase10Slice(emptyList()))
                }

            // Read Phase 9 feeReceipts directly for Cash Book income (works even if user has fees: none)
            val cbFeeReceiptsFlow = orgRef.collection("feeReceipts")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val items = snap.documents.mapNotNull { doc ->
                        FeeReceiptRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase10Slice(items, 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/feeReceipts")
                    emit(Phase10Slice(emptyList()))
                }

            combine(cbSettingsFlow, cbEntriesFlow, cbClosingsFlow, cbAuditFlow, cbFeeReceiptsFlow) { s, e, c, a, r ->
                Phase10CashBookSlice(s, e, c, a, r)
            }
        } else {
            flowOf(Phase10CashBookSlice())
        }

        val enquiriesFlow: Flow<Phase10Slice<List<AdmissionEnquiryRecord>>> = if (canEnq) {
            orgRef.collection("admissionEnquiries")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        AdmissionEnquiryRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase10Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/admissionEnquiries")
                    emit(Phase10Slice(emptyList()))
                }
        } else {
            flowOf(Phase10Slice(emptyList()))
        }

        val enquirySettingsFlow: Flow<Phase10Slice<List<EnquirySettingsConfig>>> = if (canEnq) {
            orgRef.collection("enquirySettings")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        EnquirySettingsConfig.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase10Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/enquirySettings")
                    emit(Phase10Slice(emptyList()))
                }
        } else {
            flowOf(Phase10Slice(emptyList()))
        }

        return combine(certsFlow, cashBookFlow, enquiriesFlow, enquirySettingsFlow) { certs, cb, enqs, enqSet ->
            CloudPhase10Snapshot(
                certificates = certs.value.sortedWith(compareByDescending<SchoolCertificateRecord> { it.issuedAtMs }.thenByDescending { it.sequenceNo }),
                cashBookSettings = cb.settings.value,
                cashBookEntries = cb.entries.value.sortedByDescending { it.createdAtMs },
                cashBookDayClosings = cb.closings.value.sortedByDescending { it.dateIso },
                cashBookAuditLogs = cb.auditLogs.value.sortedByDescending { it.clientCreatedAtMs },
                cashBookFeeReceipts = cb.feeReceipts.value,
                enquiries = enqs.value.sortedByDescending { it.createdAtMs },
                enquirySettings = enqSet.value,
                pendingWriteCount = certs.pendingCount +
                    cb.settings.pendingCount + cb.entries.pendingCount + cb.closings.pendingCount + cb.auditLogs.pendingCount +
                    enqs.pendingCount + enqSet.pendingCount,
                isFromCache = certs.isFromCache || cb.entries.isFromCache || enqs.isFromCache
            )
        }
    }

    // =========================================================================
    // 2. CERTIFICATES MUTATIONS (Create-only; Cancel-only; Never Edited/Deleted)
    // =========================================================================
    fun issueCertificate(
        orgId: String,
        record: SchoolCertificateRecord
    ): Result<SchoolCertificateRecord> = try {
        val uid = requireUid()
        val docId = record.id.ifBlank {
            SchoolCertificateRecord.makeDocId(record.schoolId, record.session, record.certificateNumber)
        }
        val finalRec = record.copy(id = docId, issuedByUid = uid, status = CertificateStatus.ISSUED)
        val path = "organisations/$orgId/schoolCertificates/$docId"
        val docRef = db.collection("organisations").document(orgId)
            .collection("schoolCertificates").document(docId)
        val payload = finalRec.toCreateFirestoreMap().toMutableMap().apply {
            this["createdAt"] = FieldValue.serverTimestamp()
            this["updatedAt"] = FieldValue.serverTimestamp()
        }
        val exec = {
            val t = docRef.set(payload)
            attachSyncListeners(t, OperationType.CREATE, path)
        }
        attachSyncListeners(docRef.set(payload), OperationType.CREATE, path, exec)
        Result.success(finalRec)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "organisations/$orgId/schoolCertificates")
        Result.failure(e)
    }

    fun cancelCertificate(
        orgId: String,
        certId: String,
        cancelledByName: String,
        cancelReason: String
    ): Result<Unit> = try {
        val uid = requireUid()
        val cleanReason = cancelReason.trim()
        require(cleanReason.isNotEmpty()) { "Cancellation reason is required." }
        val nowMs = System.currentTimeMillis()
        val path = "organisations/$orgId/schoolCertificates/$certId"
        val docRef = db.collection("organisations").document(orgId)
            .collection("schoolCertificates").document(certId)
        val updates = mapOf<String, Any>(
            "status" to CertificateStatus.CANCELLED.wireValue,
            "cancelledAtMs" to nowMs,
            "cancelledByUid" to uid,
            "cancelledByName" to cancelledByName.trim().ifBlank { "Staff" }.take(120),
            "cancelReason" to cleanReason.take(300),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val exec = {
            attachSyncListeners(docRef.update(updates), OperationType.UPDATE, path)
        }
        attachSyncListeners(docRef.update(updates), OperationType.UPDATE, path, exec)
        Result.success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "organisations/$orgId/schoolCertificates/$certId")
        Result.failure(e)
    }

    // =========================================================================
    // 3. CASH BOOK MUTATIONS (Settings, Entries, Edit/Cancel with Reason, Day Closing, Audit Log)
    // =========================================================================
    fun saveCashBookSettings(
        orgId: String,
        config: CashBookSettingsConfig,
        editorName: String,
        existsInCloud: Boolean
    ): Result<CashBookSettingsConfig> = try {
        val uid = requireUid()
        val nowMs = System.currentTimeMillis()
        val docId = config.id.ifBlank { CashBookSettingsConfig.makeDocId(config.schoolId) }
        val finalCfg = config.copy(id = docId, updatedByUid = uid, updatedByName = editorName, clientEditedAtMs = nowMs)
        val path = "organisations/$orgId/cashBookSettings/$docId"
        val docRef = db.collection("organisations").document(orgId)
            .collection("cashBookSettings").document(docId)
        val payload = finalCfg.toFirestoreMap(uid, editorName, nowMs).toMutableMap().apply {
            if (!existsInCloud) this["createdAt"] = FieldValue.serverTimestamp()
            this["updatedAt"] = FieldValue.serverTimestamp()
        }
        val op = if (existsInCloud) OperationType.UPDATE else OperationType.CREATE
        val task = if (existsInCloud) docRef.update(payload) else docRef.set(payload)
        attachSyncListeners(task, op, path)
        appendCashBookAuditLog(
            orgId = orgId,
            schoolId = config.schoolId,
            action = "SETTINGS_UPDATED",
            targetId = docId,
            dateIso = com.example.domain.Phase6DateUtils.todayIso(),
            amount = config.expenseApprovalLimitRs,
            reason = "Updated Cash Book settings",
            summary = "Approval limit ₹${config.expenseApprovalLimitRs}, ${config.expenseCategories.size} expense categories",
            actorName = editorName
        )
        Result.success(finalCfg)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "organisations/$orgId/cashBookSettings")
        Result.failure(e)
    }

    fun addCashBookEntry(
        orgId: String,
        entry: CashBookEntryRecord
    ): Result<CashBookEntryRecord> = try {
        val uid = requireUid()
        val nowMs = System.currentTimeMillis()
        val docId = entry.id.ifBlank { "cbe_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val finalEntry = entry.copy(
            id = docId,
            createdByUid = uid,
            updatedByUid = uid,
            createdAtMs = if (entry.createdAtMs > 0L) entry.createdAtMs else nowMs,
            clientEditedAtMs = nowMs,
            status = CashBookEntryStatus.ACTIVE
        )
        val path = "organisations/$orgId/cashBookEntries/$docId"
        val docRef = db.collection("organisations").document(orgId)
            .collection("cashBookEntries").document(docId)
        val payload = finalEntry.toCreateFirestoreMap().toMutableMap().apply {
            this["createdAt"] = FieldValue.serverTimestamp()
            this["updatedAt"] = FieldValue.serverTimestamp()
        }
        val exec = { attachSyncListeners(docRef.set(payload), OperationType.CREATE, path) }
        attachSyncListeners(docRef.set(payload), OperationType.CREATE, path, exec)

        val action = if (finalEntry.isExpense) "EXPENSE_ADDED" else "INCOME_ADDED"
        val approvalInfo = if (finalEntry.approvedByAdminName.isNotBlank()) {
            " (Approved by ${finalEntry.approvedByAdminName})"
        } else ""
        appendCashBookAuditLog(
            orgId = orgId,
            schoolId = finalEntry.schoolId,
            action = action,
            targetId = docId,
            dateIso = finalEntry.dateIso,
            amount = finalEntry.amount,
            reason = finalEntry.note.ifBlank { finalEntry.category },
            summary = "${finalEntry.entryType.englishLabel} ₹${finalEntry.amount} • ${finalEntry.category} (${finalEntry.paymentMode.label})$approvalInfo",
            actorName = finalEntry.createdByName
        )
        Result.success(finalEntry)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "organisations/$orgId/cashBookEntries")
        Result.failure(e)
    }

    fun editCashBookEntry(
        orgId: String,
        original: CashBookEntryRecord,
        newCategory: String,
        newAmount: Int,
        newPaymentMode: PaymentMode,
        newNote: String,
        newBillPhotoBase64: String,
        editReason: String,
        editorName: String,
        approvedByAdminUid: String = original.approvedByAdminUid,
        approvedByAdminName: String = original.approvedByAdminName,
        approvalNote: String = original.approvalNote
    ): Result<Unit> = try {
        val uid = requireUid()
        val cleanReason = editReason.trim()
        require(cleanReason.isNotEmpty()) { "Edit reason is required." }
        val nowMs = System.currentTimeMillis()
        val path = "organisations/$orgId/cashBookEntries/${original.id}"
        val docRef = db.collection("organisations").document(orgId)
            .collection("cashBookEntries").document(original.id)
        val updates = mapOf<String, Any>(
            "category" to newCategory.trim().take(80),
            "amount" to newAmount.coerceAtLeast(1).toLong(),
            "paymentMode" to newPaymentMode.wireValue,
            "note" to newNote.trim().take(300),
            "billPhotoBase64" to newBillPhotoBase64.take(260_000),
            "approvedByAdminUid" to approvedByAdminUid.trim().take(128),
            "approvedByAdminName" to approvedByAdminName.trim().take(120),
            "approvalNote" to approvalNote.trim().take(200),
            "updatedByUid" to uid,
            "updatedByName" to editorName.trim().ifBlank { "Staff" }.take(120),
            "lastEditReason" to cleanReason.take(300),
            "clientEditedAtMs" to nowMs,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val exec = { attachSyncListeners(docRef.update(updates), OperationType.UPDATE, path) }
        attachSyncListeners(docRef.update(updates), OperationType.UPDATE, path, exec)

        appendCashBookAuditLog(
            orgId = orgId,
            schoolId = original.schoolId,
            action = "ENTRY_EDITED",
            targetId = original.id,
            dateIso = original.dateIso,
            amount = newAmount,
            reason = cleanReason,
            summary = "Edited ${original.category} ₹${original.amount} → $newCategory ₹$newAmount (${newPaymentMode.label})",
            actorName = editorName
        )
        Result.success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "organisations/$orgId/cashBookEntries/${original.id}")
        Result.failure(e)
    }

    fun cancelCashBookEntry(
        orgId: String,
        original: CashBookEntryRecord,
        cancelReason: String,
        actorName: String
    ): Result<Unit> = try {
        val uid = requireUid()
        val cleanReason = cancelReason.trim()
        require(cleanReason.isNotEmpty()) { "Cancel reason is required." }
        val nowMs = System.currentTimeMillis()
        val path = "organisations/$orgId/cashBookEntries/${original.id}"
        val docRef = db.collection("organisations").document(orgId)
            .collection("cashBookEntries").document(original.id)
        val updates = mapOf<String, Any>(
            "status" to CashBookEntryStatus.CANCELLED.wireValue,
            "cancelledAtMs" to nowMs,
            "cancelledByUid" to uid,
            "cancelledByName" to actorName.trim().ifBlank { "Staff" }.take(120),
            "cancelReason" to cleanReason.take(300),
            "updatedByUid" to uid,
            "updatedByName" to actorName.trim().ifBlank { "Staff" }.take(120),
            "clientEditedAtMs" to nowMs,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val exec = { attachSyncListeners(docRef.update(updates), OperationType.UPDATE, path) }
        attachSyncListeners(docRef.update(updates), OperationType.UPDATE, path, exec)

        appendCashBookAuditLog(
            orgId = orgId,
            schoolId = original.schoolId,
            action = "ENTRY_CANCELLED",
            targetId = original.id,
            dateIso = original.dateIso,
            amount = original.amount,
            reason = cleanReason,
            summary = "Cancelled ${original.entryType.englishLabel} ₹${original.amount} (${original.category})",
            actorName = actorName
        )
        Result.success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "organisations/$orgId/cashBookEntries/${original.id}")
        Result.failure(e)
    }

    fun saveDayClosing(
        orgId: String,
        closing: CashBookDayClosingRecord,
        existsInCloud: Boolean
    ): Result<CashBookDayClosingRecord> = try {
        val uid = requireUid()
        val nowMs = System.currentTimeMillis()
        val docId = closing.id.ifBlank { CashBookDayClosingRecord.makeDocId(closing.schoolId, closing.dateIso) }
        val finalRec = closing.copy(id = docId, closedByUid = uid, closedAtMs = nowMs)
        val path = "organisations/$orgId/cashBookDayClosings/$docId"
        val docRef = db.collection("organisations").document(orgId)
            .collection("cashBookDayClosings").document(docId)
        val payload = finalRec.toFirestoreMap().toMutableMap().apply {
            if (!existsInCloud) this["createdAt"] = FieldValue.serverTimestamp()
            this["updatedAt"] = FieldValue.serverTimestamp()
        }
        val op = if (existsInCloud) OperationType.UPDATE else OperationType.CREATE
        val task = if (existsInCloud) docRef.update(payload) else docRef.set(payload)
        attachSyncListeners(task, op, path)

        appendCashBookAuditLog(
            orgId = orgId,
            schoolId = finalRec.schoolId,
            action = "DAY_CLOSED",
            targetId = docId,
            dateIso = finalRec.dateIso,
            amount = finalRec.countedCash,
            reason = finalRec.note.ifBlank { "Day closing recorded" },
            summary = "Day ${finalRec.dateIso}: Expected ₹${finalRec.expectedClosingCash}, Counted ₹${finalRec.countedCash}, Diff ₹${finalRec.cashDifference}",
            actorName = finalRec.closedByName
        )
        Result.success(finalRec)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "organisations/$orgId/cashBookDayClosings")
        Result.failure(e)
    }

    private fun appendCashBookAuditLog(
        orgId: String,
        schoolId: String,
        action: String,
        targetId: String,
        dateIso: String,
        amount: Int,
        reason: String,
        summary: String,
        actorName: String
    ) {
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return
        val nowMs = System.currentTimeMillis()
        val logId = "cbal_${nowMs}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val logRec = CashBookAuditLogRecord(
            id = logId,
            schoolId = schoolId,
            action = action,
            targetId = targetId,
            dateIso = dateIso,
            amount = amount,
            reason = reason,
            summary = summary,
            actorUid = uid,
            actorName = actorName.ifBlank { "Staff" },
            clientCreatedAtMs = nowMs
        )
        val path = "organisations/$orgId/cashBookAuditLogs/$logId"
        val payload = logRec.toFirestoreMap().toMutableMap().apply {
            this["createdAt"] = FieldValue.serverTimestamp()
        }
        val docRef = db.collection("organisations").document(orgId)
            .collection("cashBookAuditLogs").document(logId)
        attachSyncListeners(docRef.set(payload), OperationType.CREATE, path)
    }

    // =========================================================================
    // 4. ADMISSION ENQUIRY MUTATIONS
    // =========================================================================
    fun saveEnquirySettings(
        orgId: String,
        config: EnquirySettingsConfig,
        editorName: String,
        existsInCloud: Boolean
    ): Result<EnquirySettingsConfig> = try {
        val uid = requireUid()
        val nowMs = System.currentTimeMillis()
        val docId = config.id.ifBlank { EnquirySettingsConfig.makeDocId(config.schoolId) }
        val finalCfg = config.copy(id = docId, updatedByUid = uid, updatedByName = editorName, clientEditedAtMs = nowMs)
        val path = "organisations/$orgId/enquirySettings/$docId"
        val docRef = db.collection("organisations").document(orgId)
            .collection("enquirySettings").document(docId)
        val payload = finalCfg.toFirestoreMap(uid, editorName, nowMs).toMutableMap().apply {
            if (!existsInCloud) this["createdAt"] = FieldValue.serverTimestamp()
            this["updatedAt"] = FieldValue.serverTimestamp()
        }
        val op = if (existsInCloud) OperationType.UPDATE else OperationType.CREATE
        val task = if (existsInCloud) docRef.update(payload) else docRef.set(payload)
        attachSyncListeners(task, op, path)
        Result.success(finalCfg)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "organisations/$orgId/enquirySettings")
        Result.failure(e)
    }

    fun saveEnquiry(
        orgId: String,
        enquiry: AdmissionEnquiryRecord,
        existsInCloud: Boolean
    ): Result<AdmissionEnquiryRecord> = try {
        val uid = requireUid()
        val nowMs = System.currentTimeMillis()
        val docId = enquiry.id.ifBlank { "enq_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val finalRec = enquiry.copy(
            id = docId,
            createdByUid = if (existsInCloud) enquiry.createdByUid.ifBlank { uid } else uid,
            createdAtMs = if (existsInCloud && enquiry.createdAtMs > 0L) enquiry.createdAtMs else nowMs,
            updatedByUid = uid,
            clientEditedAtMs = nowMs
        )
        val path = "organisations/$orgId/admissionEnquiries/$docId"
        val docRef = db.collection("organisations").document(orgId)
            .collection("admissionEnquiries").document(docId)
        val payload = finalRec.toFirestoreMap().toMutableMap().apply {
            if (!existsInCloud) this["createdAt"] = FieldValue.serverTimestamp()
            this["updatedAt"] = FieldValue.serverTimestamp()
        }
        val op = if (existsInCloud) OperationType.UPDATE else OperationType.CREATE
        val task = if (existsInCloud) docRef.update(payload) else docRef.set(payload)
        attachSyncListeners(task, op, path)
        Result.success(finalRec)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "organisations/$orgId/admissionEnquiries")
        Result.failure(e)
    }

    fun markEnquiryConverted(
        orgId: String,
        enquiryId: String,
        studentDocId: String,
        studentAdmNo: String,
        actorName: String
    ): Result<Unit> = try {
        val uid = requireUid()
        val nowMs = System.currentTimeMillis()
        val path = "organisations/$orgId/admissionEnquiries/$enquiryId"
        val docRef = db.collection("organisations").document(orgId)
            .collection("admissionEnquiries").document(enquiryId)
        val updates = mapOf<String, Any>(
            "status" to EnquiryStatus.ADMITTED.wireValue,
            "lostReason" to "",
            "convertedStudentDocId" to studentDocId.trim().take(128),
            "convertedStudentAdmNo" to studentAdmNo.trim().take(60),
            "convertedAtMs" to nowMs,
            "updatedByUid" to uid,
            "updatedByName" to actorName.trim().ifBlank { "Staff" }.take(120),
            "clientEditedAtMs" to nowMs,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val exec = { attachSyncListeners(docRef.update(updates), OperationType.UPDATE, path) }
        attachSyncListeners(docRef.update(updates), OperationType.UPDATE, path, exec)
        Result.success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "organisations/$orgId/admissionEnquiries/$enquiryId")
        Result.failure(e)
    }
}
