package com.example.data.cloud

import com.example.domain.AppModule
import com.example.domain.AuthorizedPickupPerson
import com.example.domain.GatePassRecord
import com.example.domain.MemberAccessProfile
import com.example.domain.OrgAccessEvaluator
import com.example.domain.ParentMeetingRecord
import com.example.domain.Phase19AccessEvaluator
import com.example.domain.Phase19RemarksPtmAndGateEngine
import com.example.domain.PtmCallStatus
import com.example.domain.PtmStudentCallEntry
import com.example.domain.SchoolRemarkCategoryConfig
import com.example.domain.StudentRemarkRecord
import com.example.domain.TeacherAccessScope
import com.example.domain.VisitorEntryRecord
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
import java.util.UUID

data class CloudPhase19Snapshot(
    val studentRemarks: List<StudentRemarkRecord> = emptyList(),
    val remarkCategoryConfigs: List<SchoolRemarkCategoryConfig> = emptyList(),
    val parentMeetings: List<ParentMeetingRecord> = emptyList(),
    val authorizedPickups: List<AuthorizedPickupPerson> = emptyList(),
    val gatePasses: List<GatePassRecord> = emptyList(),
    val visitorEntries: List<VisitorEntryRecord> = emptyList(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase19Slice<T>(
    val value: T,
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false
)

/**
 * Phase 19 Offline-First Repository:
 * - `studentRemarks` (sensitive visibility: only student's class teacher, owner, admins, or granted `studentRemarks` module users; no silent deletion — cancel with reason + audit log)
 * - `studentRemarkCategories` (editable categories per school + hide summary PDF toggle)
 * - `parentMeetings` (PTM dates, called students, attendance, discussion notes, decisions)
 * - `studentAuthorizedPickups` (authorised pickup persons per student stored in Student Register)
 * - `gatePasses` (early leave gate passes with offline-safe unique pass numbers per school & session)
 * - `visitorEntries` (visitor register with time-in and one-tap sign-out)
 */
class Phase19Repository(
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
            _syncError.value = friendlyFirestoreErrorMessage(
                e,
                "Failed to sync Student Remarks / PTM / Gate changes."
            )
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
            val schoolId = doc.getString("schoolId").orEmpty().ifBlank { doc.id }
            schoolId.isEmpty() || schoolId in allowed
        }
    }

    fun observePhase19Data(
        orgId: String,
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?
    ): Flow<CloudPhase19Snapshot> {
        if (orgId.isBlank() || member == null || !OrgAccessEvaluator.isActive(member)) {
            return flowOf(CloudPhase19Snapshot())
        }

        val hasBroadRemarkRead = OrgAccessEvaluator.isAdmin(member) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.STUDENT_REMARKS)

        val ownClassTeacherSections: List<Triple<String, String, String>> = teacherScope?.classTeacherSections
            ?.mapNotNull { key ->
                val parts = key.split("|")
                if (parts.size == 3) Triple(parts[0].trim(), parts[1].trim(), parts[2].trim()) else null
            }
            ?.filter { (schId, _, _) -> OrgAccessEvaluator.canAccessSchool(member, schId) }
            .orEmpty()

        val canReadAnyRemarks = hasBroadRemarkRead || ownClassTeacherSections.isNotEmpty()

        val canReadPickups = OrgAccessEvaluator.isAdmin(member) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.STUDENT_REGISTER) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.STUDENTS) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.GATE)

        val canReadGate = OrgAccessEvaluator.isAdmin(member) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.GATE)

        val isUnrestrictedSchools = OrgAccessEvaluator.isAdmin(member) || member.allSchools
        val allowedSchoolIds = member.allowedSchoolIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()

        fun <T> schoolScopedCollectionFlow(
            collectionName: String,
            mapper: (DocumentSnapshot) -> T?,
            sorter: (List<T>) -> List<T>
        ): Flow<Phase19Slice<List<T>>> {
            if (isUnrestrictedSchools) {
                return orgDoc(orgId).collection(collectionName)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val items = sorter(snap.documents.mapNotNull(mapper))
                        Phase19Slice(
                            value = items,
                            pendingCount = snap.documents.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase19Slice(emptyList()))
                    }
            }
            if (allowedSchoolIds.isEmpty()) return flowOf(Phase19Slice(emptyList()))
            val perSchoolFlows = allowedSchoolIds.map { schId ->
                orgDoc(orgId).collection(collectionName)
                    .whereEqualTo("schoolId", schId)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        Phase19Slice(
                            value = snap.documents.mapNotNull(mapper),
                            pendingCount = snap.documents.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase19Slice(emptyList()))
                    }
            }
            return combine(perSchoolFlows) { slices ->
                Phase19Slice(
                    value = sorter(slices.flatMap { it.value }),
                    pendingCount = slices.sumOf { it.pendingCount },
                    isFromCache = slices.all { it.isFromCache }
                )
            }
        }

        // 1. studentRemarks flow (school-scoped broad query for admins/granted users; per-section query for class teachers)
        val remarksFlow: Flow<Phase19Slice<List<StudentRemarkRecord>>> = if (hasBroadRemarkRead) {
            schoolScopedCollectionFlow(
                collectionName = "studentRemarks",
                mapper = { d -> StudentRemarkRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) },
                sorter = { list ->
                    list.distinctBy { it.id }.sortedWith(
                        compareByDescending<StudentRemarkRecord> { it.dateIso }
                            .thenByDescending { it.createdAtMs }
                    )
                }
            )
        } else if (ownClassTeacherSections.isNotEmpty()) {
            val sectionFlows = ownClassTeacherSections.map { (schId, clsName, sec) ->
                orgDoc(orgId).collection("studentRemarks")
                    .whereEqualTo("schoolId", schId)
                    .whereEqualTo("className", clsName)
                    .whereEqualTo("section", sec)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val items = snap.documents.mapNotNull { d ->
                            StudentRemarkRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites())
                        }
                        Phase19Slice(
                            value = items,
                            pendingCount = snap.documents.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase19Slice(emptyList()))
                    }
            }
            combine(sectionFlows) { slices ->
                Phase19Slice(
                    value = slices.flatMap { it.value }
                        .distinctBy { it.id }
                        .sortedWith(
                            compareByDescending<StudentRemarkRecord> { it.dateIso }
                                .thenByDescending { it.createdAtMs }
                        ),
                    pendingCount = slices.sumOf { it.pendingCount },
                    isFromCache = slices.all { it.isFromCache }
                )
            }
        } else {
            flowOf(Phase19Slice(emptyList()))
        }

        // 2. studentRemarkCategories flow
        val categoriesFlow: Flow<Phase19Slice<List<SchoolRemarkCategoryConfig>>> = if (!canReadAnyRemarks) {
            flowOf(Phase19Slice(emptyList()))
        } else if (isUnrestrictedSchools) {
            orgDoc(orgId).collection("studentRemarkCategories")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    Phase19Slice(
                        value = snap.documents.map { d -> SchoolRemarkCategoryConfig.fromMap(d.id, d.data) },
                        pendingCount = snap.documents.count { it.metadata.hasPendingWrites() },
                        isFromCache = snap.metadata.isFromCache
                    )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    emit(Phase19Slice(emptyList()))
                }
        } else if (allowedSchoolIds.isEmpty()) {
            flowOf(Phase19Slice(emptyList()))
        } else {
            val catDocFlows = allowedSchoolIds.map { schId ->
                orgDoc(orgId).collection("studentRemarkCategories").document(schId)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { d ->
                        if (d.exists()) {
                            Phase19Slice(
                                value = listOf(SchoolRemarkCategoryConfig.fromMap(d.id, d.data)),
                                pendingCount = if (d.metadata.hasPendingWrites()) 1 else 0,
                                isFromCache = d.metadata.isFromCache
                            )
                        } else {
                            Phase19Slice<List<SchoolRemarkCategoryConfig>>(emptyList(), 0, d.metadata.isFromCache)
                        }
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase19Slice(emptyList()))
                    }
            }
            combine(catDocFlows) { slices ->
                Phase19Slice(
                    value = slices.flatMap { it.value }.distinctBy { it.schoolId },
                    pendingCount = slices.sumOf { it.pendingCount },
                    isFromCache = slices.all { it.isFromCache }
                )
            }
        }

        // 3. parentMeetings flow (school-scoped for admins/granted users; class-section + whole-school queries for class teachers)
        val ptmFlow: Flow<Phase19Slice<List<ParentMeetingRecord>>> = if (!canReadAnyRemarks) {
            flowOf(Phase19Slice(emptyList()))
        } else if (hasBroadRemarkRead) {
            schoolScopedCollectionFlow(
                collectionName = "parentMeetings",
                mapper = { d -> ParentMeetingRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) },
                sorter = { list ->
                    list.distinctBy { it.id }.sortedWith(
                        compareByDescending<ParentMeetingRecord> { it.dateIso }
                            .thenByDescending { it.createdAtMs }
                    )
                }
            )
        } else if (ownClassTeacherSections.isNotEmpty()) {
            val sectionPtmFlows = ownClassTeacherSections.map { (schId, clsName, sec) ->
                orgDoc(orgId).collection("parentMeetings")
                    .whereEqualTo("schoolId", schId)
                    .whereEqualTo("className", clsName)
                    .whereEqualTo("section", sec)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        Phase19Slice(
                            value = snap.documents.mapNotNull { d ->
                                ParentMeetingRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites())
                            },
                            pendingCount = snap.documents.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase19Slice(emptyList()))
                    }
            }
            val wholeSchoolPtmFlows = ownClassTeacherSections.map { it.first }.distinct().map { schId ->
                orgDoc(orgId).collection("parentMeetings")
                    .whereEqualTo("schoolId", schId)
                    .whereEqualTo("className", "ALL")
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        Phase19Slice(
                            value = snap.documents.mapNotNull { d ->
                                ParentMeetingRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites())
                            },
                            pendingCount = snap.documents.count { it.metadata.hasPendingWrites() },
                            isFromCache = snap.metadata.isFromCache
                        )
                    }
                    .catch { e ->
                        if (e is CancellationException) throw e
                        emit(Phase19Slice(emptyList()))
                    }
            }
            combine(sectionPtmFlows + wholeSchoolPtmFlows) { slices ->
                Phase19Slice(
                    value = slices.flatMap { it.value }
                        .distinctBy { it.id }
                        .sortedWith(
                            compareByDescending<ParentMeetingRecord> { it.dateIso }
                                .thenByDescending { it.createdAtMs }
                        ),
                    pendingCount = slices.sumOf { it.pendingCount },
                    isFromCache = slices.all { it.isFromCache }
                )
            }
        } else {
            flowOf(Phase19Slice(emptyList()))
        }

        // 4. studentAuthorizedPickups flow
        val pickupsFlow: Flow<Phase19Slice<List<AuthorizedPickupPerson>>> = if (!canReadPickups) {
            flowOf(Phase19Slice(emptyList()))
        } else {
            schoolScopedCollectionFlow(
                collectionName = "studentAuthorizedPickups",
                mapper = { d -> AuthorizedPickupPerson.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) },
                sorter = { list ->
                    list.distinctBy { it.id }.sortedWith(
                        compareBy<AuthorizedPickupPerson> { it.studentName }
                            .thenBy { it.personName }
                    )
                }
            )
        }

        // 5. gatePasses flow
        val gatePassesFlow: Flow<Phase19Slice<List<GatePassRecord>>> = if (!canReadGate) {
            flowOf(Phase19Slice(emptyList()))
        } else {
            schoolScopedCollectionFlow(
                collectionName = "gatePasses",
                mapper = { d -> GatePassRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) },
                sorter = { list ->
                    list.distinctBy { it.id }.sortedWith(
                        compareByDescending<GatePassRecord> { it.dateIso }
                            .thenByDescending { it.createdAtMs }
                    )
                }
            )
        }

        // 6. visitorEntries flow
        val visitorsFlow: Flow<Phase19Slice<List<VisitorEntryRecord>>> = if (!canReadGate) {
            flowOf(Phase19Slice(emptyList()))
        } else {
            schoolScopedCollectionFlow(
                collectionName = "visitorEntries",
                mapper = { d -> VisitorEntryRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites()) },
                sorter = { list ->
                    list.distinctBy { it.id }.sortedWith(
                        compareByDescending<VisitorEntryRecord> { it.dateIso }
                            .thenByDescending { it.createdAtMs }
                    )
                }
            )
        }

        return combine(
            combine(remarksFlow, categoriesFlow, ptmFlow) { r, c, p -> Triple(r, c, p) },
            combine(pickupsFlow, gatePassesFlow, visitorsFlow) { pk, gp, v -> Triple(pk, gp, v) }
        ) { (remarks, categories, ptms), (pickups, passes, visitors) ->
            CloudPhase19Snapshot(
                studentRemarks = remarks.value,
                remarkCategoryConfigs = categories.value,
                parentMeetings = ptms.value,
                authorizedPickups = pickups.value,
                gatePasses = passes.value,
                visitorEntries = visitors.value,
                pendingWriteCount = remarks.pendingCount + categories.pendingCount + ptms.pendingCount +
                    pickups.pendingCount + passes.pendingCount + visitors.pendingCount,
                isFromCache = remarks.isFromCache || categories.isFromCache || ptms.isFromCache ||
                    pickups.isFromCache || passes.isFromCache || visitors.isFromCache
            )
        }
    }

    // =========================================================================
    // 1. STUDENT REMARKS OPERATIONS (CREATE, COMPLETE FOLLOW-UP, CANCEL WITH REASON)
    // =========================================================================

    fun addStudentRemark(
        orgId: String,
        schoolId: String,
        session: String,
        studentId: String,
        studentAdmNo: String,
        studentName: String,
        className: String,
        section: String,
        rollNo: Int,
        dateIso: String,
        category: String,
        note: String,
        followUpDateIso: String,
        actor: MemberAccessProfile,
        actorName: String,
        teacherScope: TeacherAccessScope?
    ): Result<StudentRemarkRecord> {
        return try {
            val uid = requireUid()
            val cleanSection = section.trim().uppercase().ifBlank { "A" }
            if (!Phase19AccessEvaluator.canEditStudentRemarksForClass(
                    member = actor,
                    teacherScope = teacherScope,
                    schoolId = schoolId,
                    className = className,
                    section = cleanSection
                )
            ) {
                return Result.failure(
                    IllegalAccessException("You do not have permission to add remarks for Class $className-$cleanSection.")
                )
            }
            val validationError = Phase19RemarksPtmAndGateEngine.validateNewRemark(
                studentId = studentId,
                dateIso = dateIso,
                category = category,
                note = note,
                followUpDateIso = followUpDateIso
            )
            if (validationError != null) {
                return Result.failure(IllegalArgumentException(validationError))
            }

            val nowMs = System.currentTimeMillis()
            val docId = "rmk_${nowMs}_${UUID.randomUUID().toString().take(8)}"
            val record = StudentRemarkRecord(
                id = docId,
                schoolId = schoolId.trim(),
                session = session.trim(),
                studentId = studentId.trim(),
                studentAdmNo = studentAdmNo.trim(),
                studentName = studentName.trim(),
                className = className.trim(),
                section = cleanSection,
                rollNo = rollNo,
                dateIso = dateIso.trim(),
                category = category.trim(),
                note = note.trim(),
                followUpDateIso = followUpDateIso.trim(),
                authorUid = uid,
                authorName = actorName.trim().ifBlank { "Teacher" },
                createdAtMs = nowMs,
                updatedAtMs = nowMs
            )

            val ref = orgDoc(orgId).collection("studentRemarks").document(docId)
            val ts = FieldValue.serverTimestamp()
            val payload = record.toFirestoreMap(ts, ts)
            val path = "organisations/$orgId/studentRemarks/$docId"
            attachSyncListeners(ref.set(payload), OperationType.CREATE, path) { ref.set(payload) }

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "STUDENT_REMARK_ADDED",
                targetType = "studentRemark",
                targetId = docId,
                summary = "Added [${record.category}] remark for ${record.studentName} (${record.classSectionLabel}) on ${record.dateIso}",
                schoolId = schoolId
            )
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remarks cannot be silently deleted: they are cancelled with a mandatory reason
     * and recorded in the audit log.
     */
    fun cancelStudentRemark(
        orgId: String,
        existing: StudentRemarkRecord,
        reason: String,
        actor: MemberAccessProfile,
        actorName: String,
        teacherScope: TeacherAccessScope?
    ): Result<StudentRemarkRecord> {
        return try {
            val uid = requireUid()
            if (!Phase19AccessEvaluator.canEditStudentRemarksForClass(
                    member = actor,
                    teacherScope = teacherScope,
                    schoolId = existing.schoolId,
                    className = existing.className,
                    section = existing.section
                )
            ) {
                return Result.failure(
                    IllegalAccessException("You do not have permission to cancel remarks for Class ${existing.classSectionLabel}.")
                )
            }
            val nowMs = System.currentTimeMillis()
            val (updated, auditMessage) = Phase19RemarksPtmAndGateEngine.cancelRemarkWithReason(
                existing = existing,
                reason = reason,
                actorUid = uid,
                actorName = actorName,
                nowMs = nowMs
            ).getOrThrow()

            val ref = orgDoc(orgId).collection("studentRemarks").document(existing.id)
            val updates = mapOf(
                "status" to updated.status.wireValue,
                "cancelledReason" to updated.cancelledReason,
                "cancelledByUid" to updated.cancelledByUid,
                "cancelledByName" to updated.cancelledByName,
                "cancelledAtMs" to updated.cancelledAtMs,
                "updatedAtMs" to updated.updatedAtMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val path = "organisations/$orgId/studentRemarks/${existing.id}"
            attachSyncListeners(ref.update(updates), OperationType.UPDATE, path) { ref.update(updates) }

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "STUDENT_REMARK_CANCELLED",
                targetType = "studentRemark",
                targetId = existing.id,
                summary = auditMessage.take(320),
                schoolId = existing.schoolId
            )
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun markRemarkFollowUpCompleted(
        orgId: String,
        existing: StudentRemarkRecord,
        completed: Boolean,
        completionNote: String,
        actor: MemberAccessProfile,
        teacherScope: TeacherAccessScope?
    ): Result<StudentRemarkRecord> {
        return try {
            requireUid()
            if (!Phase19AccessEvaluator.canEditStudentRemarksForClass(
                    member = actor,
                    teacherScope = teacherScope,
                    schoolId = existing.schoolId,
                    className = existing.className,
                    section = existing.section
                )
            ) {
                return Result.failure(
                    IllegalAccessException("You do not have permission to update follow-ups for Class ${existing.classSectionLabel}.")
                )
            }
            val nowMs = System.currentTimeMillis()
            val updated = existing.copy(
                followUpCompleted = completed,
                followUpCompletedNote = completionNote.trim().take(500),
                updatedAtMs = nowMs
            )
            val ref = orgDoc(orgId).collection("studentRemarks").document(existing.id)
            val updates = mapOf(
                "followUpCompleted" to updated.followUpCompleted,
                "followUpCompletedNote" to updated.followUpCompletedNote,
                "updatedAtMs" to updated.updatedAtMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val path = "organisations/$orgId/studentRemarks/${existing.id}"
            attachSyncListeners(ref.update(updates), OperationType.UPDATE, path) { ref.update(updates) }
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveSchoolRemarkCategoryConfig(
        orgId: String,
        config: SchoolRemarkCategoryConfig,
        actor: MemberAccessProfile,
        actorName: String
    ): Result<SchoolRemarkCategoryConfig> {
        return try {
            val uid = requireUid()
            if (!OrgAccessEvaluator.canAccessSchool(actor, config.schoolId) || !OrgAccessEvaluator.isAdmin(actor)) {
                return Result.failure(
                    IllegalAccessException("Only Owners and Admins can update remark categories or summary privacy settings.")
                )
            }
            val nowMs = System.currentTimeMillis()
            val updated = config.copy(
                updatedByUid = uid,
                updatedByName = actorName.trim().ifBlank { "Admin" },
                updatedAtMs = nowMs
            )
            val ref = orgDoc(orgId).collection("studentRemarkCategories").document(config.schoolId)
            val payload = updated.toFirestoreMap(FieldValue.serverTimestamp())
            val path = "organisations/$orgId/studentRemarkCategories/${config.schoolId}"
            attachSyncListeners(ref.set(payload), OperationType.WRITE, path) { ref.set(payload) }
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 2. PARENT MEETINGS (PTM) OPERATIONS
    // =========================================================================

    fun saveParentMeeting(
        orgId: String,
        meeting: ParentMeetingRecord,
        isNew: Boolean,
        actor: MemberAccessProfile,
        actorName: String,
        teacherScope: TeacherAccessScope?
    ): Result<ParentMeetingRecord> {
        return try {
            val uid = requireUid()
            if (!Phase19AccessEvaluator.canEditParentMeeting(
                    member = actor,
                    teacherScope = teacherScope,
                    schoolId = meeting.schoolId,
                    className = meeting.className,
                    section = meeting.section
                )
            ) {
                return Result.failure(
                    IllegalAccessException("You do not have permission to create or edit PTM for ${meeting.scopeLabel}.")
                )
            }
            val nowMs = System.currentTimeMillis()
            val docId = meeting.id.ifBlank { "ptm_${nowMs}_${UUID.randomUUID().toString().take(8)}" }
            val saved = meeting.copy(
                id = docId,
                createdByUid = if (isNew) uid else meeting.createdByUid.ifBlank { uid },
                createdByName = if (isNew) actorName.trim().ifBlank { "Teacher" } else meeting.createdByName,
                createdAtMs = if (isNew || meeting.createdAtMs <= 0L) nowMs else meeting.createdAtMs,
                updatedAtMs = nowMs
            )
            val ref = orgDoc(orgId).collection("parentMeetings").document(docId)
            val ts = FieldValue.serverTimestamp()
            val payload = saved.toFirestoreMap(ts, ts)
            val path = "organisations/$orgId/parentMeetings/$docId"
            attachSyncListeners(
                ref.set(payload),
                if (isNew) OperationType.CREATE else OperationType.UPDATE,
                path
            ) { ref.set(payload) }

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = if (isNew) "PTM_CREATED" else "PTM_UPDATED",
                targetType = "parentMeeting",
                targetId = docId,
                summary = "${if (isNew) "Created" else "Updated"} PTM '${saved.title}' on ${saved.dateIso} (${saved.scopeLabel}, ${saved.entries.size} student(s))",
                schoolId = saved.schoolId
            )
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updatePtmStudentOutcome(
        orgId: String,
        meeting: ParentMeetingRecord,
        studentId: String,
        status: PtmCallStatus,
        discussionNote: String,
        decision: String,
        actor: MemberAccessProfile,
        actorName: String,
        teacherScope: TeacherAccessScope?
    ): Result<ParentMeetingRecord> {
        val nowMs = System.currentTimeMillis()
        val uid = auth.currentUser?.uid.orEmpty()
        val updatedEntries = meeting.entries.map { entry ->
            if (entry.studentId == studentId) {
                entry.copy(
                    status = status,
                    discussionNote = discussionNote.trim().take(1000),
                    decision = decision.trim().take(600),
                    updatedByUid = uid,
                    updatedByName = actorName.trim().ifBlank { "Teacher" },
                    updatedAtMs = nowMs
                )
            } else {
                entry
            }
        }
        return saveParentMeeting(
            orgId = orgId,
            meeting = meeting.copy(entries = updatedEntries, updatedAtMs = nowMs),
            isNew = false,
            actor = actor,
            actorName = actorName,
            teacherScope = teacherScope
        )
    }

    // =========================================================================
    // 3. AUTHORISED PICKUP PERSONS (STORED IN STUDENT REGISTER)
    // =========================================================================

    fun saveAuthorizedPickupPerson(
        orgId: String,
        person: AuthorizedPickupPerson,
        actor: MemberAccessProfile,
        actorName: String
    ): Result<AuthorizedPickupPerson> {
        return try {
            val uid = requireUid()
            if (!Phase19AccessEvaluator.canEditAuthorizedPickups(actor, person.schoolId)) {
                return Result.failure(
                    IllegalAccessException("You need Student Register edit access to update authorised pickup persons.")
                )
            }
            if (person.personName.trim().length < 2) {
                return Result.failure(IllegalArgumentException("Please enter pickup person's name."))
            }
            val nowMs = System.currentTimeMillis()
            val docId = person.id.ifBlank { "pkp_${person.studentId}_${nowMs}_${UUID.randomUUID().toString().take(6)}" }
            val saved = person.copy(
                id = docId,
                updatedByUid = uid,
                updatedByName = actorName.trim().ifBlank { "Staff" },
                updatedAtMs = nowMs
            )
            val ref = orgDoc(orgId).collection("studentAuthorizedPickups").document(docId)
            val ts = FieldValue.serverTimestamp()
            val payload = saved.toFirestoreMap(ts, ts)
            val path = "organisations/$orgId/studentAuthorizedPickups/$docId"
            attachSyncListeners(ref.set(payload), OperationType.WRITE, path) { ref.set(payload) }

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "AUTHORIZED_PICKUP_SAVED",
                targetType = "studentAuthorizedPickup",
                targetId = docId,
                summary = "Saved authorised pickup person '${saved.personName}' (${saved.relation}) for ${saved.studentName}",
                schoolId = saved.schoolId
            )
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteAuthorizedPickupPerson(
        orgId: String,
        person: AuthorizedPickupPerson,
        actor: MemberAccessProfile
    ): Result<Unit> {
        return try {
            requireUid()
            if (!Phase19AccessEvaluator.canEditAuthorizedPickups(actor, person.schoolId)) {
                return Result.failure(
                    IllegalAccessException("You need Student Register edit access to remove authorised pickup persons.")
                )
            }
            val ref = orgDoc(orgId).collection("studentAuthorizedPickups").document(person.id)
            val path = "organisations/$orgId/studentAuthorizedPickups/${person.id}"
            attachSyncListeners(ref.delete(), OperationType.DELETE, path) { ref.delete() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. EARLY LEAVE GATE PASS & VISITOR REGISTER OPERATIONS
    // =========================================================================

    fun createGatePass(
        orgId: String,
        gatePass: GatePassRecord,
        actor: MemberAccessProfile,
        actorName: String
    ): Result<GatePassRecord> {
        return try {
            val uid = requireUid()
            if (!Phase19AccessEvaluator.canEditGateModule(actor, gatePass.schoolId)) {
                return Result.failure(
                    IllegalAccessException("You do not have Gate module edit access.")
                )
            }
            val validationError = Phase19RemarksPtmAndGateEngine.validateGatePassInput(
                studentId = gatePass.studentId,
                dateIso = gatePass.dateIso,
                leaveTime = gatePass.leaveTime,
                reason = gatePass.reason,
                pickupPersonName = gatePass.pickupPersonName,
                pickupRelation = gatePass.pickupRelation,
                isOneTimePickup = gatePass.isOneTimePickup,
                guardianPhoneConfirmed = gatePass.guardianPhoneConfirmed,
                approvedByName = gatePass.approvedByName
            )
            if (validationError != null) {
                return Result.failure(IllegalArgumentException(validationError))
            }

            val nowMs = System.currentTimeMillis()
            val docId = gatePass.id.ifBlank { "gp_${nowMs}_${UUID.randomUUID().toString().take(8)}" }
            val saved = gatePass.copy(
                id = docId,
                approvedByUid = gatePass.approvedByUid.ifBlank { uid },
                createdByUid = uid,
                createdByName = actorName.trim().ifBlank { "Gate" },
                createdAtMs = nowMs
            )

            val ref = orgDoc(orgId).collection("gatePasses").document(docId)
            val ts = FieldValue.serverTimestamp()
            val payload = saved.toFirestoreMap(ts, ts)
            val path = "organisations/$orgId/gatePasses/$docId"
            attachSyncListeners(ref.set(payload), OperationType.CREATE, path) { ref.set(payload) }

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "GATE_PASS_ISSUED",
                targetType = "gatePass",
                targetId = docId,
                summary = "Issued Gate Pass ${saved.passNumber} for ${saved.studentName} (${saved.classSectionLabel}) to ${saved.pickupPersonName} (${saved.pickupRelation}) at ${saved.leaveTime}",
                schoolId = saved.schoolId
            )
            Result.success(saved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun addVisitorEntry(
        orgId: String,
        schoolId: String,
        dateIso: String,
        visitorName: String,
        mobile: String,
        purpose: String,
        whomToMeet: String,
        timeIn: String,
        photoBase64: String,
        actor: MemberAccessProfile,
        actorName: String
    ): Result<VisitorEntryRecord> {
        return try {
            val uid = requireUid()
            if (!Phase19AccessEvaluator.canEditGateModule(actor, schoolId)) {
                return Result.failure(
                    IllegalAccessException("You do not have Gate module edit access to record visitors.")
                )
            }
            val validationError = Phase19RemarksPtmAndGateEngine.validateVisitorEntry(
                visitorName = visitorName,
                mobile = mobile,
                purpose = purpose,
                whomToMeet = whomToMeet,
                dateIso = dateIso,
                timeIn = timeIn
            )
            if (validationError != null) {
                return Result.failure(IllegalArgumentException(validationError))
            }

            val nowMs = System.currentTimeMillis()
            val docId = "vis_${nowMs}_${UUID.randomUUID().toString().take(8)}"
            val record = VisitorEntryRecord(
                id = docId,
                schoolId = schoolId.trim(),
                dateIso = dateIso.trim(),
                visitorName = visitorName.trim(),
                mobile = mobile.trim(),
                purpose = purpose.trim(),
                whomToMeet = whomToMeet.trim(),
                timeIn = timeIn.trim(),
                photoBase64 = photoBase64,
                recordedByUid = uid,
                recordedByName = actorName.trim().ifBlank { "Gate" },
                createdAtMs = nowMs,
                updatedAtMs = nowMs
            )
            val ref = orgDoc(orgId).collection("visitorEntries").document(docId)
            val ts = FieldValue.serverTimestamp()
            val payload = record.toFirestoreMap(ts, ts)
            val path = "organisations/$orgId/visitorEntries/$docId"
            attachSyncListeners(ref.set(payload), OperationType.CREATE, path) { ref.set(payload) }
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOutVisitor(
        orgId: String,
        existing: VisitorEntryRecord,
        timeOut: String,
        actor: MemberAccessProfile,
        actorName: String
    ): Result<VisitorEntryRecord> {
        return try {
            val uid = requireUid()
            if (!Phase19AccessEvaluator.canEditGateModule(actor, existing.schoolId)) {
                return Result.failure(
                    IllegalAccessException("You do not have Gate module edit access to sign out visitors.")
                )
            }
            val nowMs = System.currentTimeMillis()
            val updated = Phase19RemarksPtmAndGateEngine.signOutVisitor(
                existing = existing,
                timeOut = timeOut,
                actorUid = uid,
                actorName = actorName,
                nowMs = nowMs
            )
            val ref = orgDoc(orgId).collection("visitorEntries").document(existing.id)
            val updates = mapOf(
                "timeOut" to updated.timeOut,
                "status" to updated.status.wireValue,
                "signedOutByUid" to updated.signedOutByUid,
                "signedOutByName" to updated.signedOutByName,
                "updatedAtMs" to updated.updatedAtMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val path = "organisations/$orgId/visitorEntries/${existing.id}"
            attachSyncListeners(ref.update(updates), OperationType.UPDATE, path) { ref.update(updates) }
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
