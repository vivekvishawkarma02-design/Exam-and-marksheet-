package com.example.data.cloud

import com.example.domain.CashBookAuditLogRecord
import com.example.domain.CashBookEntryRecord
import com.example.domain.DayOfWeekCode
import com.example.domain.ExamInvigilatorDutyRecord
import com.example.domain.ExamRoomRecord
import com.example.domain.ExamScheduleRecord
import com.example.domain.ExamSeatingRecord
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeStructureConfig
import com.example.domain.MemberAccessProfile
import com.example.domain.OptionalFeeCatalogueItem
import com.example.domain.OrgAccessEvaluator
import com.example.domain.Phase11AccessEvaluator
import com.example.domain.StockTransactionRecord
import com.example.domain.StudentFeeOverride
import com.example.domain.TeacherLeaveRecord
import com.example.domain.TeacherProfile
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportSettingsConfig
import com.example.domain.TransportVehicleDocumentRecord
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

data class CloudPhase11Snapshot(
    val examSchedules: List<ExamScheduleRecord> = emptyList(),
    val examRooms: List<ExamRoomRecord> = emptyList(),
    val examSeatings: List<ExamSeatingRecord> = emptyList(),
    val examDuties: List<ExamInvigilatorDutyRecord> = emptyList(),
    val stockTransactions: List<StockTransactionRecord> = emptyList(),
    val transportRoutes: List<TransportRouteRecord> = emptyList(),
    val transportVehicleDocuments: List<TransportVehicleDocumentRecord> = emptyList(),
    val transportSettings: List<TransportSettingsConfig> = emptyList(),
    val transportCashBookEntries: List<CashBookEntryRecord> = emptyList(),
    val transportCashBookAuditLogs: List<CashBookAuditLogRecord> = emptyList(),
    // Cross-module read-only slices so Exams/Stock/Transport screens work even if user lacks Fees/Teachers/Leave/CashBook modules
    val sharedFeeStructures: List<FeeStructureConfig> = emptyList(),
    val sharedFeeReceipts: List<FeeReceiptRecord> = emptyList(),
    val sharedFeeOverrides: List<StudentFeeOverride> = emptyList(),
    val sharedTeachers: List<TeacherProfile> = emptyList(),
    val sharedTeacherLeaves: List<TeacherLeaveRecord> = emptyList(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase11Slice<T>(
    val value: T,
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase11ExamBundleSlice(
    val schedules: Phase11Slice<List<ExamScheduleRecord>> = Phase11Slice(emptyList()),
    val rooms: Phase11Slice<List<ExamRoomRecord>> = Phase11Slice(emptyList()),
    val seatings: Phase11Slice<List<ExamSeatingRecord>> = Phase11Slice(emptyList()),
    val duties: Phase11Slice<List<ExamInvigilatorDutyRecord>> = Phase11Slice(emptyList()),
    val teachers: Phase11Slice<List<TeacherProfile>> = Phase11Slice(emptyList()),
    val leaves: Phase11Slice<List<TeacherLeaveRecord>> = Phase11Slice(emptyList())
)

private data class Phase11FeeReadSlice(
    val structures: Phase11Slice<List<FeeStructureConfig>> = Phase11Slice(emptyList()),
    val receipts: Phase11Slice<List<FeeReceiptRecord>> = Phase11Slice(emptyList()),
    val overrides: Phase11Slice<List<StudentFeeOverride>> = Phase11Slice(emptyList())
)

private data class Phase11TransportBundleSlice(
    val routes: Phase11Slice<List<TransportRouteRecord>> = Phase11Slice(emptyList()),
    val vehicleDocs: Phase11Slice<List<TransportVehicleDocumentRecord>> = Phase11Slice(emptyList()),
    val settings: Phase11Slice<List<TransportSettingsConfig>> = Phase11Slice(emptyList()),
    val transportCashEntries: Phase11Slice<List<CashBookEntryRecord>> = Phase11Slice(emptyList()),
    val transportCashAuditLogs: Phase11Slice<List<CashBookAuditLogRecord>> = Phase11Slice(emptyList())
)

/**
 * Phase 11 Offline-First Repository for:
 * 1. Exam Schedules, Rooms, Seating & Invigilator Duties (`examSchedules`, `examRooms`, `examSeatings`, `examInvigilatorDuties`)
 * 2. Stock (`stockTransactions` + shared `feeStructures.optionalCatalogue` & read-only `feeReceipts`)
 * 3. Transport Routes (`transportRoutes`)
 *
 * Every listener is strictly permission-gated. Active teachers without `exams` access only attach
 * a self-filtered query `examInvigilatorDuties.whereEqualTo("teacherUid", currentUid)` so they see
 * "My exam duties" on Teacher Home while zero-trust Firestore rules block all other exam data.
 */
class Phase11Repository(
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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync Phase 11 changes.")
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
    // 1. REAL-TIME OBSERVERS (Strictly permission-gated)
    // =========================================================================
    fun observePhase11Data(
        orgId: String,
        member: MemberAccessProfile,
        currentUid: String
    ): Flow<CloudPhase11Snapshot> {
        if (!OrgAccessEvaluator.isActive(member) || currentUid.isBlank()) {
            return flowOf(CloudPhase11Snapshot())
        }
        val canExams = Phase11AccessEvaluator.canViewExams(member)
        val canStock = Phase11AccessEvaluator.canViewStock(member)
        val canTransport = Phase11AccessEvaluator.canViewTransport(member)
        val orgRef = db.collection("organisations").document(orgId)

        // --- A. Exams + Teacher self-duties ---
        val examSchedulesFlow: Flow<Phase11Slice<List<ExamScheduleRecord>>> = if (canExams) {
            orgRef.collection("examSchedules")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        ExamScheduleRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/examSchedules")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val examRoomsFlow: Flow<Phase11Slice<List<ExamRoomRecord>>> = if (canExams) {
            orgRef.collection("examRooms")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        ExamRoomRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/examRooms")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val examSeatingsFlow: Flow<Phase11Slice<List<ExamSeatingRecord>>> = if (canExams) {
            orgRef.collection("examSeatings")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        ExamSeatingRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/examSeatings")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        // Duties: full collection if `exams: view`, else strictly own `teacherUid == currentUid` for Teacher Home
        val examDutiesFlow: Flow<Phase11Slice<List<ExamInvigilatorDutyRecord>>> = if (canExams) {
            orgRef.collection("examInvigilatorDuties")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        ExamInvigilatorDutyRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/examInvigilatorDuties")
                    emit(Phase11Slice(emptyList()))
                }
        } else {
            orgRef.collection("examInvigilatorDuties")
                .whereEqualTo("teacherUid", currentUid)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val items = snap.documents.mapNotNull { doc ->
                        ExamInvigilatorDutyRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/examInvigilatorDuties(self)")
                    emit(Phase11Slice(emptyList()))
                }
        }

        val examTeachersFlow: Flow<Phase11Slice<List<TeacherProfile>>> = if (canExams) {
            orgRef.collection("teachers")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val items = snap.documents.map { parseTeacherDoc(it) }
                    Phase11Slice(items, 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/teachers")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val examLeavesFlow: Flow<Phase11Slice<List<TeacherLeaveRecord>>> = if (canExams) {
            orgRef.collection("teacherLeaves")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val items = snap.documents.mapNotNull { doc ->
                        TeacherLeaveRecord.fromMap(
                            id = doc.id,
                            raw = doc.data,
                            hasPendingWrites = doc.metadata.hasPendingWrites(),
                            isFromCache = doc.metadata.isFromCache()
                        )
                    }
                    Phase11Slice(items, 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/teacherLeaves")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val examBundleFlow: Flow<Phase11ExamBundleSlice> = combine(
            combine(examSchedulesFlow, examRoomsFlow, examSeatingsFlow) { s, r, st -> Triple(s, r, st) },
            combine(examDutiesFlow, examTeachersFlow, examLeavesFlow) { d, t, l -> Triple(d, t, l) }
        ) { a, b ->
            Phase11ExamBundleSlice(
                schedules = a.first,
                rooms = a.second,
                seatings = a.third,
                duties = b.first,
                teachers = b.second,
                leaves = b.third
            )
        }

        // --- B. Stock Transactions & Shared Fee Read Slices ---
        val stockTxFlow: Flow<Phase11Slice<List<StockTransactionRecord>>> = if (canStock) {
            orgRef.collection("stockTransactions")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        StockTransactionRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/stockTransactions")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val needFeeStructuresAndReceipts = canStock || canExams || canTransport
        val sharedFeeStructuresFlow: Flow<Phase11Slice<List<FeeStructureConfig>>> = if (needFeeStructuresAndReceipts) {
            orgRef.collection("feeStructures")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val items = snap.documents.mapNotNull { doc ->
                        FeeStructureConfig.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/feeStructures")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val sharedFeeReceiptsFlow: Flow<Phase11Slice<List<FeeReceiptRecord>>> = if (needFeeStructuresAndReceipts) {
            orgRef.collection("feeReceipts")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val items = snap.documents.mapNotNull { doc ->
                        FeeReceiptRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/feeReceipts")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val sharedFeeOverridesFlow: Flow<Phase11Slice<List<StudentFeeOverride>>> = if (canExams) {
            orgRef.collection("studentFeeOverrides")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val items = snap.documents.mapNotNull { doc ->
                        StudentFeeOverride.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/studentFeeOverrides")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val feeReadFlow: Flow<Phase11FeeReadSlice> = combine(
            sharedFeeStructuresFlow,
            sharedFeeReceiptsFlow,
            sharedFeeOverridesFlow
        ) { fs, fr, fo ->
            Phase11FeeReadSlice(structures = fs, receipts = fr, overrides = fo)
        }

        // --- C. Transport Routes, Vehicle Documents, Settings & Linked Cash Book Expenses ---
        val transportRoutesFlow: Flow<Phase11Slice<List<TransportRouteRecord>>> = if (canTransport) {
            orgRef.collection("transportRoutes")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        TransportRouteRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/transportRoutes")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val transportVehicleDocsFlow: Flow<Phase11Slice<List<TransportVehicleDocumentRecord>>> = if (canTransport) {
            orgRef.collection("transportVehicleDocuments")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        TransportVehicleDocumentRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/transportVehicleDocuments")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val transportSettingsFlow: Flow<Phase11Slice<List<TransportSettingsConfig>>> = if (canTransport) {
            orgRef.collection("transportSettings")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        TransportSettingsConfig.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/transportSettings")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val transportCashEntriesFlow: Flow<Phase11Slice<List<CashBookEntryRecord>>> = if (canTransport) {
            orgRef.collection("cashBookEntries")
                .whereEqualTo("sourceModule", "TRANSPORT")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        CashBookEntryRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, if (hasPending) pCount.coerceAtLeast(1) else 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/cashBookEntries(TRANSPORT)")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val transportCashAuditFlow: Flow<Phase11Slice<List<CashBookAuditLogRecord>>> = if (canTransport) {
            orgRef.collection("cashBookAuditLogs")
                .whereEqualTo("sourceModule", "TRANSPORT")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val items = snap.documents.mapNotNull { doc ->
                        CashBookAuditLogRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { OrgAccessEvaluator.canAccessSchool(member, it.schoolId) }
                    Phase11Slice(items, 0, snap.metadata.isFromCache())
                }
                .catch { e ->
                    handleFlowError(e, "organisations/$orgId/cashBookAuditLogs(TRANSPORT)")
                    emit(Phase11Slice(emptyList()))
                }
        } else flowOf(Phase11Slice(emptyList()))

        val transportBundleFlow: Flow<Phase11TransportBundleSlice> = combine(
            transportRoutesFlow,
            transportVehicleDocsFlow,
            transportSettingsFlow,
            transportCashEntriesFlow,
            transportCashAuditFlow
        ) { r, vd, st, ce, ca ->
            Phase11TransportBundleSlice(r, vd, st, ce, ca)
        }

        return combine(
            examBundleFlow,
            stockTxFlow,
            feeReadFlow,
            transportBundleFlow
        ) { exams, stockTx, fees, transport ->
            val pending = exams.schedules.pendingCount +
                exams.rooms.pendingCount +
                exams.seatings.pendingCount +
                exams.duties.pendingCount +
                stockTx.pendingCount +
                transport.routes.pendingCount +
                transport.vehicleDocs.pendingCount +
                transport.settings.pendingCount +
                transport.transportCashEntries.pendingCount
            val fromCache = exams.schedules.isFromCache ||
                exams.rooms.isFromCache ||
                exams.seatings.isFromCache ||
                exams.duties.isFromCache ||
                stockTx.isFromCache ||
                transport.routes.isFromCache ||
                transport.vehicleDocs.isFromCache ||
                transport.transportCashEntries.isFromCache
            CloudPhase11Snapshot(
                examSchedules = exams.schedules.value,
                examRooms = exams.rooms.value,
                examSeatings = exams.seatings.value,
                examDuties = exams.duties.value,
                stockTransactions = stockTx.value,
                transportRoutes = transport.routes.value,
                transportVehicleDocuments = transport.vehicleDocs.value,
                transportSettings = transport.settings.value,
                transportCashBookEntries = transport.transportCashEntries.value.sortedByDescending { it.createdAtMs },
                transportCashBookAuditLogs = transport.transportCashAuditLogs.value.sortedByDescending { it.clientCreatedAtMs },
                sharedFeeStructures = fees.structures.value,
                sharedFeeReceipts = fees.receipts.value,
                sharedFeeOverrides = fees.overrides.value,
                sharedTeachers = exams.teachers.value,
                sharedTeacherLeaves = exams.leaves.value,
                pendingWriteCount = pending,
                isFromCache = fromCache
            )
        }
    }

    // =========================================================================
    // 2. EXAMS MUTATIONS
    // =========================================================================

    fun saveExamSchedule(
        orgId: String,
        schedule: ExamScheduleRecord,
        editorName: String
    ): Result<String> {
        val docId = schedule.id.ifBlank {
            ExamScheduleRecord.makeDocId(
                schedule.schoolId,
                schedule.session,
                "${schedule.examType.wireValue}_${UUID.randomUUID().toString().replace("-", "").take(6)}"
            )
        }
        val path = "organisations/$orgId/examSchedules/$docId"
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val ref = db.collection("organisations").document(orgId)
                .collection("examSchedules").document(docId)
            val payload = schedule.copy(id = docId).toFirestoreMap(uid, editorName, nowMs) + mapOf(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.WRITE, path) { ref.set(payload) }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "EXAM_SCHEDULE_SAVED",
                targetType = "examSchedule",
                targetId = docId,
                summary = "Saved exam schedule '${schedule.title}' (${schedule.entries.size} papers)",
                schoolId = schedule.schoolId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun deleteExamSchedule(orgId: String, schoolId: String, examId: String, title: String): Result<Unit> {
        val path = "organisations/$orgId/examSchedules/$examId"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("examSchedules").document(examId)
            val task = ref.delete()
            attachSyncListeners(task, OperationType.DELETE, path) { ref.delete() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "EXAM_SCHEDULE_DELETED",
                targetType = "examSchedule",
                targetId = examId,
                summary = "Deleted exam schedule '$title'",
                schoolId = schoolId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun saveExamRoom(orgId: String, room: ExamRoomRecord): Result<String> {
        val docId = room.id.ifBlank { ExamRoomRecord.makeDocId(room.schoolId, room.roomName) }
        val path = "organisations/$orgId/examRooms/$docId"
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val ref = db.collection("organisations").document(orgId)
                .collection("examRooms").document(docId)
            val payload = room.copy(id = docId).toFirestoreMap(uid, nowMs) + mapOf(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.WRITE, path) { ref.set(payload) }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun deleteExamRoom(orgId: String, roomId: String): Result<Unit> {
        val path = "organisations/$orgId/examRooms/$roomId"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("examRooms").document(roomId)
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

    fun saveExamSeating(orgId: String, seating: ExamSeatingRecord): Result<String> {
        val docId = seating.id.ifBlank { ExamSeatingRecord.makeDocId(seating.examId) }
        val path = "organisations/$orgId/examSeatings/$docId"
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val ref = db.collection("organisations").document(orgId)
                .collection("examSeatings").document(docId)
            val payload = seating.copy(id = docId).toFirestoreMap(uid, nowMs) + mapOf(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.WRITE, path) { ref.set(payload) }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "EXAM_SEATING_SAVED",
                targetType = "examSeating",
                targetId = docId,
                summary = "Saved seating plan (${seating.allocations.size} students across ${seating.roomIds.size} rooms)",
                schoolId = seating.schoolId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun replaceExamDuties(
        orgId: String,
        schoolId: String,
        examId: String,
        existingDutiesForExam: List<ExamInvigilatorDutyRecord>,
        newDuties: List<ExamInvigilatorDutyRecord>
    ): Result<Unit> {
        val path = "organisations/$orgId/examInvigilatorDuties"
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val col = db.collection("organisations").document(orgId)
                .collection("examInvigilatorDuties")
            val newIds = newDuties.map { it.id }.toSet()
            val batch = db.batch()
            existingDutiesForExam
                .filter { it.examId == examId && it.id !in newIds }
                .forEach { stale -> batch.delete(col.document(stale.id)) }
            newDuties.forEach { duty ->
                val payload = duty.toFirestoreMap(uid, nowMs) + mapOf("updatedAt" to FieldValue.serverTimestamp())
                batch.set(col.document(duty.id), payload)
            }
            val task = batch.commit()
            attachSyncListeners(task, OperationType.WRITE, path)
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "EXAM_DUTIES_ASSIGNED",
                targetType = "examSchedule",
                targetId = examId,
                summary = "Assigned ${newDuties.size} invigilator duties",
                schoolId = schoolId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun saveSingleExamDuty(orgId: String, duty: ExamInvigilatorDutyRecord): Result<Unit> {
        val path = "organisations/$orgId/examInvigilatorDuties/${duty.id}"
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val ref = db.collection("organisations").document(orgId)
                .collection("examInvigilatorDuties").document(duty.id)
            val payload = duty.toFirestoreMap(uid, nowMs) + mapOf("updatedAt" to FieldValue.serverTimestamp())
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.WRITE, path) { ref.set(payload) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. STOCK MUTATIONS (Unified with Phase 9 `feeStructures.optionalCatalogue`)
    // =========================================================================

    /**
     * Updates ONLY `optionalCatalogue` (+ audit metadata) on the school's Phase 9 `feeStructures` doc
     * if it already exists, or creates a default `FeeStructureConfig` with the new catalogue if none exists.
     * Matches the zero-trust Firestore rule allowing `stock: edit` to manage the unified item list
     * without ever modifying tuition or exam fee heads.
     */
    fun saveUnifiedStockCatalogue(
        orgId: String,
        schoolId: String,
        session: String,
        existingStructure: FeeStructureConfig?,
        updatedCatalogue: List<OptionalFeeCatalogueItem>,
        editorName: String
    ): Result<Unit> {
        val docId = existingStructure?.id?.takeIf { it.isNotBlank() }
            ?: FeeStructureConfig.makeDocId(schoolId, session)
        val path = "organisations/$orgId/feeStructures/$docId"
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val ref = db.collection("organisations").document(orgId)
                .collection("feeStructures").document(docId)
            val task = if (existingStructure != null) {
                val updates = mapOf<String, Any>(
                    "optionalCatalogue" to updatedCatalogue.take(150).map { it.toMap() },
                    "updatedByUid" to uid,
                    "updatedByName" to editorName.trim().take(120),
                    "clientEditedAtMs" to nowMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.update(updates).also { t ->
                    attachSyncListeners(t, OperationType.UPDATE, path) { ref.update(updates) }
                }
            } else {
                val base = FeeStructureConfig.buildDefault(schoolId, session)
                    .copy(optionalCatalogue = updatedCatalogue.take(150))
                val payload = base.toFirestoreMap(uid, editorName, nowMs) + mapOf(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).also { t ->
                    attachSyncListeners(t, OperationType.CREATE, path) { ref.set(payload) }
                }
            }
            @Suppress("UNUSED_VARIABLE")
            val ignored = task
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "STOCK_CATALOGUE_UPDATED",
                targetType = "feeStructure",
                targetId = docId,
                summary = "Updated unified stock/optional items catalogue (${updatedCatalogue.size} items)",
                schoolId = schoolId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun recordStockTransaction(
        orgId: String,
        record: StockTransactionRecord
    ): Result<String> {
        val docId = record.id.ifBlank {
            "stk_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        }
        val path = "organisations/$orgId/stockTransactions/$docId"
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val finalized = record.copy(
                id = docId,
                createdByUid = uid,
                createdAtMs = if (record.createdAtMs > 0L) record.createdAtMs else nowMs
            )
            val ref = db.collection("organisations").document(orgId)
                .collection("stockTransactions").document(docId)
            val payload = finalized.toFirestoreMap() + mapOf("createdAt" to FieldValue.serverTimestamp())
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.CREATE, path) { ref.set(payload) }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "STOCK_${finalized.txType.wireValue}",
                targetType = "stockTransaction",
                targetId = docId,
                summary = "${finalized.txType.englishLabel}: ${finalized.itemName} (${if (finalized.quantityDelta >= 0) "+${finalized.quantityDelta}" else "${finalized.quantityDelta}"})",
                schoolId = finalized.schoolId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. TRANSPORT ROUTES MUTATIONS
    // =========================================================================

    fun saveTransportRoute(
        orgId: String,
        route: TransportRouteRecord,
        editorName: String
    ): Result<String> {
        val docId = route.id.ifBlank {
            "troute_${StableDocId.sanitizeSegment(route.schoolId)}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        }
        val path = "organisations/$orgId/transportRoutes/$docId"
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val ref = db.collection("organisations").document(orgId)
                .collection("transportRoutes").document(docId)
            val payload = route.copy(id = docId).toFirestoreMap(uid, editorName, nowMs) + mapOf(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.WRITE, path) { ref.set(payload) }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "TRANSPORT_ROUTE_SAVED",
                targetType = "transportRoute",
                targetId = docId,
                summary = "Saved transport route '${route.routeName}' (${route.stops.size} stops, ₹${route.monthlyFeeRs}/mo)",
                schoolId = route.schoolId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun deleteTransportRoute(
        orgId: String,
        schoolId: String,
        routeId: String,
        routeName: String
    ): Result<Unit> {
        val path = "organisations/$orgId/transportRoutes/$routeId"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("transportRoutes").document(routeId)
            val task = ref.delete()
            attachSyncListeners(task, OperationType.DELETE, path) { ref.delete() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "TRANSPORT_ROUTE_DELETED",
                targetType = "transportRoute",
                targetId = routeId,
                summary = "Deleted transport route '$routeName'",
                schoolId = schoolId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun saveTransportVehicleDocument(
        orgId: String,
        docRecord: TransportVehicleDocumentRecord,
        editorName: String
    ): Result<String> {
        val docId = docRecord.id.ifBlank {
            TransportVehicleDocumentRecord.makeDocId(docRecord.schoolId, docRecord.routeId, docRecord.docType)
        }
        val path = "organisations/$orgId/transportVehicleDocuments/$docId"
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val ref = db.collection("organisations").document(orgId)
                .collection("transportVehicleDocuments").document(docId)
            val payload = docRecord.copy(id = docId).toFirestoreMap(uid, editorName, nowMs) + mapOf(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.WRITE, path) { ref.set(payload) }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "TRANSPORT_DOC_SAVED",
                targetType = "transportVehicleDocument",
                targetId = docId,
                summary = "Saved ${docRecord.docType.labelEn} for ${docRecord.vehicleNumber.ifBlank { docRecord.routeId }} (exp ${docRecord.expiryDateIso})",
                schoolId = docRecord.schoolId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun deleteTransportVehicleDocument(
        orgId: String,
        schoolId: String,
        docId: String,
        label: String
    ): Result<Unit> {
        val path = "organisations/$orgId/transportVehicleDocuments/$docId"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("transportVehicleDocuments").document(docId)
            val task = ref.delete()
            attachSyncListeners(task, OperationType.DELETE, path) { ref.delete() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "TRANSPORT_DOC_DELETED",
                targetType = "transportVehicleDocument",
                targetId = docId,
                summary = "Removed vehicle document '$label'",
                schoolId = schoolId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun saveTransportSettings(
        orgId: String,
        config: TransportSettingsConfig,
        editorName: String
    ): Result<TransportSettingsConfig> {
        val docId = config.id.ifBlank { TransportSettingsConfig.makeDocId(config.schoolId) }
        val path = "organisations/$orgId/transportSettings/$docId"
        return try {
            val uid = requireUid()
            val nowMs = System.currentTimeMillis()
            val finalCfg = config.copy(id = docId, updatedByUid = uid, updatedByName = editorName, clientEditedAtMs = nowMs)
            val ref = db.collection("organisations").document(orgId)
                .collection("transportSettings").document(docId)
            val payload = finalCfg.toFirestoreMap(uid, editorName, nowMs) + mapOf(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.WRITE, path) { ref.set(payload) }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "TRANSPORT_SETTINGS_SAVED",
                targetType = "transportSettings",
                targetId = docId,
                summary = "Updated transport expense categories (${finalCfg.expenseCategories.size})",
                schoolId = config.schoolId
            )
            Result.success(finalCfg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
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
            maxPeriodsPerDay = (doc.get("maxPeriodsPerDay") ?: 7L).let { (it as? Number)?.toInt() ?: 7 },
            maxConsecutivePeriods = (doc.get("maxConsecutivePeriods") ?: 3L).let { (it as? Number)?.toInt() ?: 3 },
            weeklyOffDays = offDays,
            active = doc.getBoolean("active") ?: true,
            linkedMemberUid = doc.getString("linkedMemberUid").orEmpty()
        )
    }
}
