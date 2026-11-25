package com.example.data.cloud

import com.example.domain.DayOfWeekCode
import com.example.domain.MemberAccessProfile
import com.example.domain.OrgAccessEvaluator
import com.example.domain.PaymentMode
import com.example.domain.Phase12AccessEvaluator
import com.example.domain.SalaryAdvanceStatus
import com.example.domain.SalaryAuditLogRecord
import com.example.domain.SalaryMonthLockRecord
import com.example.domain.SalaryMonthlyRecord
import com.example.domain.SalaryPaymentStatus
import com.example.domain.SalaryRuleConfig
import com.example.domain.StaffSalaryAdvanceRecord
import com.example.domain.StaffSalaryEngine
import com.example.domain.StaffSalaryProfileRecord
import com.example.domain.TeacherAttendanceRecord
import com.example.domain.TeacherLeaveRecord
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

data class CloudPhase12Snapshot(
    val salaryRuleConfigs: List<SalaryRuleConfig> = emptyList(),
    val staffSalaryProfiles: List<StaffSalaryProfileRecord> = emptyList(),
    val salaryMonthlyRecords: List<SalaryMonthlyRecord> = emptyList(),
    val staffSalaryAdvances: List<StaffSalaryAdvanceRecord> = emptyList(),
    val salaryMonthLocks: List<SalaryMonthLockRecord> = emptyList(),
    val salaryAuditLogs: List<SalaryAuditLogRecord> = emptyList(),
    val ownTeacherSalarySlips: List<SalaryMonthlyRecord> = emptyList(),
    val sharedTeachers: List<TeacherProfile> = emptyList(),
    val sharedTeacherAttendance: List<TeacherAttendanceRecord> = emptyList(),
    val sharedTeacherLeaves: List<TeacherLeaveRecord> = emptyList(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase12Slice<T>(
    val value: T,
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase12CoreSalaryBundle(
    val ruleConfigs: Phase12Slice<List<SalaryRuleConfig>> = Phase12Slice(emptyList()),
    val profiles: Phase12Slice<List<StaffSalaryProfileRecord>> = Phase12Slice(emptyList()),
    val monthlyRecords: Phase12Slice<List<SalaryMonthlyRecord>> = Phase12Slice(emptyList()),
    val advances: Phase12Slice<List<StaffSalaryAdvanceRecord>> = Phase12Slice(emptyList()),
    val locks: Phase12Slice<List<SalaryMonthLockRecord>> = Phase12Slice(emptyList()),
    val auditLogs: Phase12Slice<List<SalaryAuditLogRecord>> = Phase12Slice(emptyList())
)

private data class Phase12SharedAttendanceBundle(
    val teachers: Phase12Slice<List<TeacherProfile>> = Phase12Slice(emptyList()),
    val attendance: Phase12Slice<List<TeacherAttendanceRecord>> = Phase12Slice(emptyList()),
    val leaves: Phase12Slice<List<TeacherLeaveRecord>> = Phase12Slice(emptyList())
)

/**
 * Phase 12 Offline-First Repository for Staff Salary:
 * - `salaryRuleConfigs`
 * - `staffSalaryProfiles`
 * - `salaryMonthlyRecords`
 * - `staffSalaryAdvances`
 * - `salaryMonthLocks`
 * - `salaryAuditLogs`
 *
 * Strictly permission-gated:
 * - Only the Owner (or an Admin/member explicitly granted `salary` view/edit by the Owner)
 *   attaches listeners to the salary collections.
 * - Other active members (e.g. Teachers) only attach a self-filtered listener on
 *   `salaryMonthlyRecords` where `staffUid == currentUid && teacherSelfViewEnabled == true`
 *   so they can see their own salary slips when enabled by the Owner, and cannot read
 *   any other salary document.
 */
class Phase12Repository(
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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync Phase 12 salary changes.")
            if (retryAction != null) lastRetryAction = retryAction
        }
    }

    fun retrySync(): Boolean {
        val action = lastRetryAction ?: return false
        _syncError.value = null
        action.invoke()
        return true
    }

    fun clearSyncError() {
        _syncError.value = null
    }

    private fun orgDoc(orgId: String) = db.collection("organisations").document(orgId)

    private fun filterSchoolScopedDocs(
        docs: List<DocumentSnapshot>,
        member: MemberAccessProfile
    ): List<DocumentSnapshot> {
        if (OrgAccessEvaluator.isAdmin(member) || member.allSchools) return docs
        val allowed = member.allowedSchoolIds.toSet()
        return docs.filter { doc ->
            val schoolId = (doc.getString("schoolId") ?: doc.getString("primarySchoolId")).orEmpty()
            val allSchoolsFlag = doc.getBoolean("allSchools") == true
            val schoolIdsList = (doc.get("schoolIds") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            allSchoolsFlag || schoolId.isEmpty() || schoolId in allowed || schoolIdsList.any { it in allowed }
        }
    }

    fun observePhase12Data(
        orgId: String,
        member: MemberAccessProfile?
    ): Flow<CloudPhase12Snapshot> {
        if (orgId.isBlank() || member == null || !OrgAccessEvaluator.isActive(member)) {
            return flowOf(CloudPhase12Snapshot())
        }

        val canViewSalary = Phase12AccessEvaluator.canViewSalary(member)

        val coreSalaryFlow: Flow<Phase12CoreSalaryBundle> = if (!canViewSalary) {
            flowOf(Phase12CoreSalaryBundle())
        } else {
            val rulesFlow: Flow<Phase12Slice<List<SalaryRuleConfig>>> =
                orgDoc(orgId).collection("salaryRuleConfigs")
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val docs = filterSchoolScopedDocs(snap.documents, member)
                        val list = docs.mapNotNull { doc ->
                            doc.data?.let { SalaryRuleConfig.fromMap(doc.id, it) }
                        }
                        Phase12Slice(
                            value = list,
                            pendingCount = docs.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase12Slice(emptyList()))
                    }

            val profilesFlow: Flow<Phase12Slice<List<StaffSalaryProfileRecord>>> =
                orgDoc(orgId).collection("staffSalaryProfiles")
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val docs = filterSchoolScopedDocs(snap.documents, member)
                        val list = docs.mapNotNull { doc ->
                            doc.data?.let { StaffSalaryProfileRecord.fromMap(doc.id, it) }
                        }.sortedBy { it.staffName.lowercase() }
                        Phase12Slice(
                            value = list,
                            pendingCount = docs.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase12Slice(emptyList()))
                    }

            val monthlyFlow: Flow<Phase12Slice<List<SalaryMonthlyRecord>>> =
                orgDoc(orgId).collection("salaryMonthlyRecords")
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val docs = filterSchoolScopedDocs(snap.documents, member)
                        val list = docs.mapNotNull { doc ->
                            doc.data?.let { SalaryMonthlyRecord.fromMap(doc.id, it) }
                        }.sortedWith(compareByDescending<SalaryMonthlyRecord> { it.yearMonth }.thenBy { it.staffName.lowercase() })
                        Phase12Slice(
                            value = list,
                            pendingCount = docs.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase12Slice(emptyList()))
                    }

            val advancesFlow: Flow<Phase12Slice<List<StaffSalaryAdvanceRecord>>> =
                orgDoc(orgId).collection("staffSalaryAdvances")
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val docs = filterSchoolScopedDocs(snap.documents, member)
                        val list = docs.mapNotNull { doc ->
                            doc.data?.let { StaffSalaryAdvanceRecord.fromMap(doc.id, it) }
                        }.sortedByDescending { it.advanceDateIso }
                        Phase12Slice(
                            value = list,
                            pendingCount = docs.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase12Slice(emptyList()))
                    }

            val locksFlow: Flow<Phase12Slice<List<SalaryMonthLockRecord>>> =
                orgDoc(orgId).collection("salaryMonthLocks")
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val docs = filterSchoolScopedDocs(snap.documents, member)
                        val list = docs.mapNotNull { doc ->
                            doc.data?.let { SalaryMonthLockRecord.fromMap(doc.id, it) }
                        }
                        Phase12Slice(
                            value = list,
                            pendingCount = docs.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase12Slice(emptyList()))
                    }

            val auditFlow: Flow<Phase12Slice<List<SalaryAuditLogRecord>>> =
                orgDoc(orgId).collection("salaryAuditLogs")
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val docs = filterSchoolScopedDocs(snap.documents, member)
                        val list = docs.mapNotNull { doc ->
                            doc.data?.let { SalaryAuditLogRecord.fromMap(doc.id, it) }
                        }.sortedByDescending { it.clientCreatedAtMs }
                        Phase12Slice(
                            value = list,
                            pendingCount = docs.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase12Slice(emptyList()))
                    }

            val firstThree = combine(rulesFlow, profilesFlow, monthlyFlow) { r, p, m ->
                Triple(r, p, m)
            }
            val secondThree = combine(advancesFlow, locksFlow, auditFlow) { a, l, au ->
                Triple(a, l, au)
            }
            combine(firstThree, secondThree) { (r, p, m), (a, l, au) ->
                Phase12CoreSalaryBundle(
                    ruleConfigs = r,
                    profiles = p,
                    monthlyRecords = m,
                    advances = a,
                    locks = l,
                    auditLogs = au
                )
            }
        }

        // Cross-module read-only slices for Phase 7 Teacher Attendance when viewer has `salary` view
        val sharedAttendanceFlow: Flow<Phase12SharedAttendanceBundle> = if (!canViewSalary) {
            flowOf(Phase12SharedAttendanceBundle())
        } else {
            val teachersFlow: Flow<Phase12Slice<List<TeacherProfile>>> =
                orgDoc(orgId).collection("teachers")
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val docs = filterSchoolScopedDocs(snap.documents, member)
                        val list = docs.map { parseTeacherDoc(it) }
                        Phase12Slice(list, docs.count { it.metadata.hasPendingWrites() }, snap.metadata.isFromCache)
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase12Slice(emptyList()))
                    }

            val attendanceFlow: Flow<Phase12Slice<List<TeacherAttendanceRecord>>> =
                orgDoc(orgId).collection("teacherAttendance")
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val docs = filterSchoolScopedDocs(snap.documents, member)
                        val list = docs.mapNotNull { doc ->
                            TeacherAttendanceRecord.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                        }
                        Phase12Slice(list, docs.count { it.metadata.hasPendingWrites() }, snap.metadata.isFromCache)
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase12Slice(emptyList()))
                    }

            val leavesFlow: Flow<Phase12Slice<List<TeacherLeaveRecord>>> =
                orgDoc(orgId).collection("teacherLeaves")
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val docs = filterSchoolScopedDocs(snap.documents, member)
                        val list = docs.mapNotNull { doc ->
                            TeacherLeaveRecord.fromMap(
                                id = doc.id,
                                raw = doc.data,
                                hasPendingWrites = doc.metadata.hasPendingWrites(),
                                isFromCache = doc.metadata.isFromCache
                            )
                        }
                        Phase12Slice(list, docs.count { it.metadata.hasPendingWrites() }, snap.metadata.isFromCache)
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase12Slice(emptyList()))
                    }

            combine(teachersFlow, attendanceFlow, leavesFlow) { t, att, lv ->
                Phase12SharedAttendanceBundle(t, att, lv)
            }
        }

        // Self-filtered salary slip listener for teachers/staff without full `salary` module access:
        // Only queries documents where `staffUid == member.uid` and `teacherSelfViewEnabled == true`
        val ownSlipsFlow: Flow<Phase12Slice<List<SalaryMonthlyRecord>>> =
            if (canViewSalary || member.uid.isBlank()) {
                flowOf(Phase12Slice(emptyList()))
            } else {
                orgDoc(orgId).collection("salaryMonthlyRecords")
                    .whereEqualTo("staffUid", member.uid)
                    .whereEqualTo("teacherSelfViewEnabled", true)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val docs = filterSchoolScopedDocs(snap.documents, member)
                        val list = docs.mapNotNull { doc ->
                            doc.data?.let { SalaryMonthlyRecord.fromMap(doc.id, it) }
                        }.sortedByDescending { it.yearMonth }
                        Phase12Slice(
                            value = list,
                            pendingCount = docs.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase12Slice(emptyList()))
                    }
            }

        return combine(coreSalaryFlow, sharedAttendanceFlow, ownSlipsFlow) { core, shared, ownSlips ->
            val effectiveOwnSlips = if (canViewSalary) {
                core.monthlyRecords.value.filter { it.staffUid.isNotBlank() && it.staffUid == member.uid }
            } else {
                ownSlips.value
            }
            val totalPending = core.ruleConfigs.pendingCount +
                core.profiles.pendingCount +
                core.monthlyRecords.pendingCount +
                core.advances.pendingCount +
                core.locks.pendingCount +
                core.auditLogs.pendingCount +
                ownSlips.pendingCount

            val anyCache = core.ruleConfigs.isFromCache ||
                core.profiles.isFromCache ||
                core.monthlyRecords.isFromCache ||
                core.advances.isFromCache

            CloudPhase12Snapshot(
                salaryRuleConfigs = core.ruleConfigs.value,
                staffSalaryProfiles = core.profiles.value,
                salaryMonthlyRecords = core.monthlyRecords.value,
                staffSalaryAdvances = core.advances.value,
                salaryMonthLocks = core.locks.value,
                salaryAuditLogs = core.auditLogs.value,
                ownTeacherSalarySlips = effectiveOwnSlips,
                sharedTeachers = shared.teachers.value,
                sharedTeacherAttendance = shared.attendance.value,
                sharedTeacherLeaves = shared.leaves.value,
                pendingWriteCount = totalPending,
                isFromCache = anyCache
            )
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

    /**
     * Saves per-school salary rule config.
     * When `allowTeachersToViewOwnSlips` is updated by the Owner, also updates `teacherSelfViewEnabled`
     * on existing `salaryMonthlyRecords` for that school in the same batch so teacher self-queries
     * immediately reflect the Owner's setting.
     */
    fun saveSalaryRuleConfig(
        orgId: String,
        config: SalaryRuleConfig,
        existingSchoolMonthlyRecords: List<SalaryMonthlyRecord>
    ) {
        val uid = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email.orEmpty()
        val now = System.currentTimeMillis()
        val docId = config.id.ifBlank { SalaryRuleConfig.defaultdocId(config.schoolId) }
        val updated = config.copy(
            id = docId,
            updatedByUid = uid,
            updatedByName = userName,
            clientEditedAtMs = now
        )
        val batch = db.batch()
        val cfgRef = orgDoc(orgId).collection("salaryRuleConfigs").document(docId)
        val payload = updated.toFirestoreMap().toMutableMap()
        payload["updatedAt"] = FieldValue.serverTimestamp()
        batch.set(cfgRef, payload)

        existingSchoolMonthlyRecords
            .filter { it.schoolId == config.schoolId && it.teacherSelfViewEnabled != config.allowTeachersToViewOwnSlips }
            .forEach { slip ->
                val slipRef = orgDoc(orgId).collection("salaryMonthlyRecords").document(slip.id)
                batch.update(
                    slipRef,
                    mapOf(
                        "teacherSelfViewEnabled" to config.allowTeachersToViewOwnSlips,
                        "updatedByUid" to uid,
                        "updatedByName" to userName,
                        "clientEditedAtMs" to now,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

        attachSyncListeners(
            task = batch.commit(),
            opType = OperationType.WRITE,
            path = "organisations/$orgId/salaryRuleConfigs/$docId",
            retryAction = { saveSalaryRuleConfig(orgId, config, existingSchoolMonthlyRecords) }
        )
    }

    fun saveStaffSalaryProfile(
        orgId: String,
        profile: StaffSalaryProfileRecord
    ) {
        val uid = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email.orEmpty()
        val now = System.currentTimeMillis()
        val staffId = profile.staffId.ifBlank { "nt_${UUID.randomUUID().toString().take(8)}" }
        val docId = profile.id.ifBlank { StaffSalaryProfileRecord.docIdForStaff(staffId) }
        val updated = profile.copy(
            id = docId,
            staffId = staffId,
            updatedByUid = uid,
            updatedByName = userName,
            clientEditedAtMs = now
        )
        val ref = orgDoc(orgId).collection("staffSalaryProfiles").document(docId)
        val payload = updated.toFirestoreMap().toMutableMap()
        payload["updatedAt"] = FieldValue.serverTimestamp()
        attachSyncListeners(
            task = ref.set(payload),
            opType = OperationType.WRITE,
            path = ref.path,
            retryAction = { saveStaffSalaryProfile(orgId, profile) }
        )
    }

    fun deleteStaffSalaryProfile(orgId: String, profileId: String) {
        if (profileId.isBlank()) return
        val ref = orgDoc(orgId).collection("staffSalaryProfiles").document(profileId)
        attachSyncListeners(
            task = ref.delete(),
            opType = OperationType.DELETE,
            path = ref.path,
            retryAction = { deleteStaffSalaryProfile(orgId, profileId) }
        )
    }

    /**
     * Saves one or more computed monthly salary records.
     * If the month is locked, requires [lockOverrideReason] and appends an immutable audit log entry.
     */
    fun saveSalaryMonthlyRecordsBatch(
        orgId: String,
        records: List<SalaryMonthlyRecord>,
        isMonthLocked: Boolean,
        lockOverrideReason: String = "",
        auditAction: String = "SHEET_SAVED"
    ) {
        if (records.isEmpty()) return
        val uid = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email.orEmpty()
        val now = System.currentTimeMillis()
        val batch = db.batch()

        for (rec in records) {
            val docId = rec.id.ifBlank {
                SalaryMonthlyRecord.docIdFor(rec.schoolId, rec.yearMonth, rec.staffId)
            }
            val effectiveReason = if (isMonthLocked) lockOverrideReason.trim() else rec.lastLockOverrideReason
            val updated = rec.copy(
                id = docId,
                lastLockOverrideReason = effectiveReason,
                updatedByUid = uid,
                updatedByName = userName,
                clientEditedAtMs = now
            )
            val ref = orgDoc(orgId).collection("salaryMonthlyRecords").document(docId)
            val payload = updated.toFirestoreMap().toMutableMap()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            batch.set(ref, payload)

            if (isMonthLocked || rec.manualAdjustmentRs != 0 || auditAction != "SHEET_SAVED") {
                val logId = "sallog_${now}_${UUID.randomUUID().toString().take(6)}"
                val logRef = orgDoc(orgId).collection("salaryAuditLogs").document(logId)
                val actionLabel = if (isMonthLocked) "LOCKED_MONTH_EDITED" else auditAction
                val reasonText = when {
                    isMonthLocked -> lockOverrideReason.trim()
                    rec.manualAdjustmentRs != 0 -> rec.adjustmentRemark.trim()
                    else -> "Monthly salary sheet updated"
                }
                val log = SalaryAuditLogRecord(
                    id = logId,
                    schoolId = rec.schoolId,
                    yearMonth = rec.yearMonth,
                    staffId = rec.staffId,
                    staffName = rec.staffName,
                    action = actionLabel,
                    reason = reasonText,
                    summary = "${rec.staffName} (${rec.yearMonth}): Net ₹${rec.netPayableRs}" +
                        if (rec.manualAdjustmentRs != 0) " • Adj ₹${rec.manualAdjustmentRs}" else "",
                    amountRs = rec.netPayableRs,
                    actorUid = uid,
                    actorName = userName,
                    clientCreatedAtMs = now
                )
                val logMap = log.toFirestoreMap().toMutableMap()
                logMap["createdAt"] = FieldValue.serverTimestamp()
                batch.set(logRef, logMap)
            }
        }

        attachSyncListeners(
            task = batch.commit(),
            opType = OperationType.WRITE,
            path = "organisations/$orgId/salaryMonthlyRecords",
            retryAction = {
                saveSalaryMonthlyRecordsBatch(orgId, records, isMonthLocked, lockOverrideReason, auditAction)
            }
        )
    }

    /**
     * Marks a staff member's monthly salary record as PAID with date and payment mode,
     * applies automatic advance instalment deduction to active advances, and records an audit log.
     */
    fun markSalaryRecordPaid(
        orgId: String,
        record: SalaryMonthlyRecord,
        paidDateIso: String,
        paymentMode: PaymentMode,
        paymentReference: String,
        activeAdvances: List<StaffSalaryAdvanceRecord>,
        isMonthLocked: Boolean,
        lockOverrideReason: String = ""
    ) {
        val uid = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email.orEmpty()
        val now = System.currentTimeMillis()
        val batch = db.batch()

        val docId = record.id.ifBlank {
            SalaryMonthlyRecord.docIdFor(record.schoolId, record.yearMonth, record.staffId)
        }
        val paidRecord = record.copy(
            id = docId,
            status = SalaryPaymentStatus.PAID,
            paidDateIso = paidDateIso.trim(),
            paymentMode = paymentMode,
            paymentReference = paymentReference.trim(),
            lastLockOverrideReason = if (isMonthLocked) lockOverrideReason.trim() else record.lastLockOverrideReason,
            updatedByUid = uid,
            updatedByName = userName,
            clientEditedAtMs = now
        )
        val slipRef = orgDoc(orgId).collection("salaryMonthlyRecords").document(docId)
        val slipPayload = paidRecord.toFirestoreMap().toMutableMap()
        slipPayload["updatedAt"] = FieldValue.serverTimestamp()
        batch.set(slipRef, slipPayload)

        // Apply automatic advance instalment repayment if advanceDeductionRs > 0
        if (paidRecord.advanceDeductionRs > 0) {
            val updatedAdvances = StaffSalaryEngine.applyAdvanceRepayment(
                advances = activeAdvances,
                staffId = paidRecord.staffId,
                repaymentAmountRs = paidRecord.advanceDeductionRs,
                dateIso = paidDateIso.trim(),
                yearMonth = paidRecord.yearMonth,
                mode = "SALARY_SLIP_${paymentMode.wireValue}",
                isAutoFromSalarySlip = true,
                note = "Auto instalment deducted from ${paidRecord.yearMonth} salary slip",
                updatedByUid = uid,
                nowMs = now
            )
            for (adv in updatedAdvances) {
                val advRef = orgDoc(orgId).collection("staffSalaryAdvances").document(adv.id)
                val advPayload = adv.toFirestoreMap().toMutableMap()
                advPayload["updatedAt"] = FieldValue.serverTimestamp()
                batch.set(advRef, advPayload)
            }
        }

        // Write audit log
        val logId = "sallog_${now}_${UUID.randomUUID().toString().take(6)}"
        val logRef = orgDoc(orgId).collection("salaryAuditLogs").document(logId)
        val log = SalaryAuditLogRecord(
            id = logId,
            schoolId = paidRecord.schoolId,
            yearMonth = paidRecord.yearMonth,
            staffId = paidRecord.staffId,
            staffName = paidRecord.staffName,
            action = if (isMonthLocked) "LOCKED_MONTH_EDITED" else "SALARY_PAID",
            reason = if (isMonthLocked) lockOverrideReason.trim() else "Salary marked paid (${paymentMode.label})",
            summary = "Paid ₹${paidRecord.netPayableRs} to ${paidRecord.staffName} on ${paidDateIso.trim()} via ${paymentMode.label}" +
                if (paidRecord.advanceDeductionRs > 0) " (Advance deducted: ₹${paidRecord.advanceDeductionRs})" else "",
            amountRs = paidRecord.netPayableRs,
            actorUid = uid,
            actorName = userName,
            clientCreatedAtMs = now
        )
        val logMap = log.toFirestoreMap().toMutableMap()
        logMap["createdAt"] = FieldValue.serverTimestamp()
        batch.set(logRef, logMap)

        attachSyncListeners(
            task = batch.commit(),
            opType = OperationType.WRITE,
            path = slipRef.path,
            retryAction = {
                markSalaryRecordPaid(
                    orgId,
                    record,
                    paidDateIso,
                    paymentMode,
                    paymentReference,
                    activeAdvances,
                    isMonthLocked,
                    lockOverrideReason
                )
            }
        )
    }

    /**
     * Creates a new staff salary advance / loan and logs it in `salaryAuditLogs`.
     */
    fun createSalaryAdvance(
        orgId: String,
        advance: StaffSalaryAdvanceRecord
    ) {
        val uid = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email.orEmpty()
        val now = System.currentTimeMillis()
        val docId = advance.id.ifBlank { "saladv_${now}_${UUID.randomUUID().toString().take(6)}" }
        val updated = advance.copy(
            id = docId,
            status = if (advance.remainingBalanceRs <= 0) SalaryAdvanceStatus.CLOSED else SalaryAdvanceStatus.ACTIVE,
            createdByUid = uid,
            createdByName = userName,
            updatedByUid = uid,
            clientEditedAtMs = now
        )
        val batch = db.batch()
        val advRef = orgDoc(orgId).collection("staffSalaryAdvances").document(docId)
        val payload = updated.toFirestoreMap().toMutableMap()
        payload["createdAt"] = FieldValue.serverTimestamp()
        payload["updatedAt"] = FieldValue.serverTimestamp()
        batch.set(advRef, payload)

        val logId = "sallog_${now}_${UUID.randomUUID().toString().take(6)}"
        val logRef = orgDoc(orgId).collection("salaryAuditLogs").document(logId)
        val log = SalaryAuditLogRecord(
            id = logId,
            schoolId = updated.schoolId,
            yearMonth = updated.advanceDateIso.take(7),
            staffId = updated.staffId,
            staffName = updated.staffName,
            action = "ADVANCE_GIVEN",
            reason = updated.note.ifBlank { "Staff salary advance issued" },
            summary = "Advance ₹${updated.principalAmountRs} given to ${updated.staffName} (Monthly instalment: ₹${updated.monthlyInstalmentRs})",
            amountRs = updated.principalAmountRs,
            actorUid = uid,
            actorName = userName,
            clientCreatedAtMs = now
        )
        val logMap = log.toFirestoreMap().toMutableMap()
        logMap["createdAt"] = FieldValue.serverTimestamp()
        batch.set(logRef, logMap)

        attachSyncListeners(
            task = batch.commit(),
            opType = OperationType.CREATE,
            path = advRef.path,
            retryAction = { createSalaryAdvance(orgId, advance) }
        )
    }

    /**
     * Records a manual repayment against an existing salary advance.
     */
    fun recordManualAdvanceRepayment(
        orgId: String,
        advance: StaffSalaryAdvanceRecord,
        repaymentAmountRs: Int,
        dateIso: String,
        paymentMode: PaymentMode,
        note: String
    ) {
        if (repaymentAmountRs <= 0) return
        val uid = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email.orEmpty()
        val now = System.currentTimeMillis()
        val updatedList = StaffSalaryEngine.applyAdvanceRepayment(
            advances = listOf(advance),
            staffId = advance.staffId,
            repaymentAmountRs = repaymentAmountRs,
            dateIso = dateIso,
            yearMonth = dateIso.take(7),
            mode = paymentMode.wireValue,
            isAutoFromSalarySlip = false,
            note = note.ifBlank { "Manual advance repayment" },
            updatedByUid = uid,
            nowMs = now
        )
        val updatedAdvance = updatedList.firstOrNull() ?: return
        val batch = db.batch()
        val advRef = orgDoc(orgId).collection("staffSalaryAdvances").document(updatedAdvance.id)
        val advPayload = updatedAdvance.toFirestoreMap().toMutableMap()
        advPayload["updatedAt"] = FieldValue.serverTimestamp()
        batch.set(advRef, advPayload)

        val logId = "sallog_${now}_${UUID.randomUUID().toString().take(6)}"
        val logRef = orgDoc(orgId).collection("salaryAuditLogs").document(logId)
        val log = SalaryAuditLogRecord(
            id = logId,
            schoolId = updatedAdvance.schoolId,
            yearMonth = dateIso.take(7),
            staffId = updatedAdvance.staffId,
            staffName = updatedAdvance.staffName,
            action = "ADVANCE_REPAID",
            reason = note.ifBlank { "Manual repayment" },
            summary = "Repaid ₹$repaymentAmountRs for ${updatedAdvance.staffName} (Remaining balance: ₹${updatedAdvance.remainingBalanceRs})",
            amountRs = repaymentAmountRs,
            actorUid = uid,
            actorName = userName,
            clientCreatedAtMs = now
        )
        val logMap = log.toFirestoreMap().toMutableMap()
        logMap["createdAt"] = FieldValue.serverTimestamp()
        batch.set(logRef, logMap)

        attachSyncListeners(
            task = batch.commit(),
            opType = OperationType.UPDATE,
            path = advRef.path,
            retryAction = {
                recordManualAdvanceRepayment(orgId, advance, repaymentAmountRs, dateIso, paymentMode, note)
            }
        )
    }

    /**
     * Owner-only: locks or unlocks a finished salary month and writes an immutable audit log entry.
     */
    fun setSalaryMonthLock(
        orgId: String,
        schoolId: String,
        yearMonth: String,
        locked: Boolean,
        note: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email.orEmpty()
        val now = System.currentTimeMillis()
        val docId = SalaryMonthLockRecord.docIdFor(schoolId, yearMonth)
        val lockRecord = SalaryMonthLockRecord(
            id = docId,
            schoolId = schoolId,
            yearMonth = yearMonth,
            locked = locked,
            lockedByUid = uid,
            lockedByName = userName,
            lockedAtMs = now,
            note = note.trim(),
            updatedByUid = uid,
            clientEditedAtMs = now
        )
        val batch = db.batch()
        val lockRef = orgDoc(orgId).collection("salaryMonthLocks").document(docId)
        val lockPayload = lockRecord.toFirestoreMap().toMutableMap()
        lockPayload["updatedAt"] = FieldValue.serverTimestamp()
        batch.set(lockRef, lockPayload)

        val logId = "sallog_${now}_${UUID.randomUUID().toString().take(6)}"
        val logRef = orgDoc(orgId).collection("salaryAuditLogs").document(logId)
        val log = SalaryAuditLogRecord(
            id = logId,
            schoolId = schoolId,
            yearMonth = yearMonth,
            staffId = "ALL",
            staffName = "Month $yearMonth",
            action = if (locked) "MONTH_LOCKED" else "MONTH_UNLOCKED",
            reason = note.ifBlank { if (locked) "Finished month locked by Owner" else "Month unlocked by Owner" },
            summary = if (locked) "Salary month $yearMonth locked by $userName" else "Salary month $yearMonth unlocked by $userName",
            amountRs = 0,
            actorUid = uid,
            actorName = userName,
            clientCreatedAtMs = now
        )
        val logMap = log.toFirestoreMap().toMutableMap()
        logMap["createdAt"] = FieldValue.serverTimestamp()
        batch.set(logRef, logMap)

        attachSyncListeners(
            task = batch.commit(),
            opType = OperationType.WRITE,
            path = lockRef.path,
            retryAction = { setSalaryMonthLock(orgId, schoolId, yearMonth, locked, note) }
        )
    }
}
