package com.example.data.cloud

import com.example.data.CoScholasticEntity
import com.example.data.FullBackupData
import com.example.data.MarkEntity
import com.example.data.SchoolSettingsEntity
import com.example.data.SchoolTemplateSource
import com.example.data.StudentEntity
import com.example.data.TermInfoEntity
import com.example.domain.AppModule
import com.example.domain.DefaultSubjects
import com.example.domain.MarkComponent
import com.example.domain.MemberAccessProfile
import com.example.domain.OfflineSyncEngine
import com.example.domain.OrgAccessEvaluator
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

data class CloudSchoolsSnapshot(
    val schools: List<CloudSchoolRecord> = emptyList(),
    val hasPendingWrites: Boolean = false,
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

data class CloudSchoolAcademicSnapshot(
    val students: List<CloudStudentRecord> = emptyList(),
    val marks: List<CloudMarkRecord> = emptyList(),
    val coScholastic: List<CloudCoScholasticRecord> = emptyList(),
    val termInfos: List<CloudTermInfoRecord> = emptyList(),
    val markEditLogs: List<CloudMarkEditLog> = emptyList(),
    val hasPendingWrites: Boolean = false,
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

/**
 * Phase 3 Offline-First Academic Repository:
 * - Single source of truth: Cloud Firestore with 150 MB local persistent cache.
 * - All day-to-day academic writes (marks, mark edit logs, students, attendance,
 *   co-scholastic, schools, settings, JSON backup restore) enqueue immediately
 *   against Firestore's local cache without blocking on server `.await()`.
 * - Local snapshot listeners (`MetadataChanges.INCLUDE`) reflect changes instantly
 *   and track exact pending-write counts for the Status Chip.
 * - Real server rejections (e.g. PERMISSION_DENIED) surface via `syncError` with
 *   a tap-to-retry action.
 */
class OrgAcademicRepository(
    private val orgRepo: OrgCloudRepository,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val db: FirebaseFirestore get() = orgRepo.db

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _lastSyncedAtMs = MutableStateFlow(0L)
    val lastSyncedAtMs: StateFlow<Long> = _lastSyncedAtMs.asStateFlow()

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
            _lastSyncedAtMs.value = System.currentTimeMillis()
            if (_syncError.value != null) {
                _syncError.value = null
                lastRetryAction = null
            }
        }.addOnFailureListener { e ->
            handleFirestoreError(e, opType, path)
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync changes to cloud.")
            if (retryAction != null) {
                lastRetryAction = retryAction
            }
        }
    }

    fun clearSyncError() {
        _syncError.value = null
        lastRetryAction = null
    }

    fun retrySync(onFeedback: (String) -> Unit = {}) {
        val retry = lastRetryAction
        _syncError.value = null
        lastRetryAction = null
        if (retry != null) {
            try {
                retry()
                onFeedback("Retrying pending sync…")
            } catch (e: Exception) {
                _syncError.value = friendlyFirestoreErrorMessage(e, "Retry failed.")
            }
        } else {
            db.waitForPendingWrites()
                .addOnSuccessListener {
                    _lastSyncedAtMs.value = System.currentTimeMillis()
                    onFeedback("All changes are synced.")
                }
                .addOnFailureListener { e ->
                    _syncError.value = friendlyFirestoreErrorMessage(e, "Sync retry failed.")
                }
        }
    }

    // =========================================================================
    // 1. REAL-TIME OBSERVERS (Scoped strictly to member's allowed schools & modules)
    // =========================================================================
    fun observeAllowedSchools(orgId: String, member: MemberAccessProfile): Flow<CloudSchoolsSnapshot> {
        if (!OrgAccessEvaluator.isActive(member)) return flowOf(CloudSchoolsSnapshot())
        val orgSchoolsRef = db.collection("organisations").document(orgId).collection("schools")
        return if (OrgAccessEvaluator.isAdmin(member) || member.allSchools) {
            orgSchoolsRef
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pendingDocs = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pendingDocs > 0
                    val count = if (hasPending) pendingDocs.coerceAtLeast(1) else 0
                    if (!hasPending && !snap.metadata.isFromCache()) {
                        _lastSyncedAtMs.value = System.currentTimeMillis()
                    }
                    CloudSchoolsSnapshot(
                        schools = snap.documents.mapNotNull { parseSchool(it) }.sortedBy { it.name.lowercase() },
                        hasPendingWrites = hasPending,
                        pendingWriteCount = count,
                        isFromCache = snap.metadata.isFromCache()
                    )
                }
        } else {
            val allowedIds = member.allowedSchoolIds.distinct().filter { it.isNotBlank() }
            if (allowedIds.isEmpty()) {
                flowOf(CloudSchoolsSnapshot())
            } else {
                val docFlows = allowedIds.map { schoolId ->
                    orgSchoolsRef.document(schoolId)
                        .snapshots(MetadataChanges.INCLUDE)
                        .map { doc ->
                            Triple(
                                if (doc.exists()) parseSchool(doc) else null,
                                doc.metadata.hasPendingWrites(),
                                doc.metadata.isFromCache()
                            )
                        }
                }
                combine(docFlows) { triples ->
                    val pendingCount = triples.count { it.second }
                    CloudSchoolsSnapshot(
                        schools = triples.mapNotNull { it.first }.sortedBy { it.name.lowercase() },
                        hasPendingWrites = pendingCount > 0,
                        pendingWriteCount = pendingCount,
                        isFromCache = triples.all { it.third }
                    )
                }
            }
        }
    }

    fun observeSchoolAcademicData(
        orgId: String,
        schoolDocId: String,
        member: MemberAccessProfile
    ): Flow<CloudSchoolAcademicSnapshot> {
        if (!OrgAccessEvaluator.canAccessSchool(member, schoolDocId)) {
            return flowOf(CloudSchoolAcademicSnapshot())
        }
        val schoolRef = db.collection("organisations").document(orgId)
            .collection("schools").document(schoolDocId)
        val stableSchoolId = StableDocId.toLong(schoolDocId)

        val canReadStudents = OrgAccessEvaluator.canViewModule(member, AppModule.STUDENTS) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.MARKS) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.CO_SCHOLASTIC) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.REPORTS)

        val canReadMarks = OrgAccessEvaluator.canViewModule(member, AppModule.MARKS) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.REPORTS)

        val canReadMarkLogs = OrgAccessEvaluator.isAdmin(member) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.MARKS)

        val canReadCo = OrgAccessEvaluator.canViewModule(member, AppModule.CO_SCHOLASTIC) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.REPORTS)

        val studentsFlow: Flow<CollectionSlice<CloudStudentRecord>> = if (canReadStudents) {
            schoolRef.collection("students").snapshots(MetadataChanges.INCLUDE).map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                CollectionSlice(
                    items = snap.documents.map { parseStudent(it, schoolDocId, stableSchoolId) },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }
        } else {
            flowOf(CollectionSlice(emptyList(), 0, false))
        }

        val marksFlow: Flow<CollectionSlice<CloudMarkRecord>> = if (canReadMarks) {
            schoolRef.collection("marks").snapshots(MetadataChanges.INCLUDE).map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                CollectionSlice(
                    items = snap.documents.map { parseMark(it, schoolDocId) },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }
        } else {
            flowOf(CollectionSlice(emptyList(), 0, false))
        }

        val coFlow: Flow<CollectionSlice<CloudCoScholasticRecord>> = if (canReadCo) {
            schoolRef.collection("coScholastic").snapshots(MetadataChanges.INCLUDE).map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                CollectionSlice(
                    items = snap.documents.map { parseCoScholastic(it, schoolDocId) },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }
        } else {
            flowOf(CollectionSlice(emptyList(), 0, false))
        }

        val termFlow: Flow<CollectionSlice<CloudTermInfoRecord>> = if (canReadCo) {
            schoolRef.collection("termInfo").snapshots(MetadataChanges.INCLUDE).map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                CollectionSlice(
                    items = snap.documents.map { parseTermInfo(it, schoolDocId) },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }
        } else {
            flowOf(CollectionSlice(emptyList(), 0, false))
        }

        val markLogsFlow: Flow<CollectionSlice<CloudMarkEditLog>> = if (canReadMarkLogs) {
            schoolRef.collection("markEditLogs").snapshots(MetadataChanges.INCLUDE).map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val logs = snap.documents
                    .map { parseMarkEditLog(it, schoolDocId) }
                    .sortedByDescending { it.clientEditedAtMs.takeIf { ms -> ms > 0L } ?: (it.createdAt?.toDate()?.time ?: 0L) }
                CollectionSlice(
                    items = logs,
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }
        } else {
            flowOf(CollectionSlice(emptyList(), 0, false))
        }

        return combine(studentsFlow, marksFlow, coFlow, termFlow, markLogsFlow) { st, mk, co, ti, logs ->
            val totalPending = st.pendingCount + mk.pendingCount + co.pendingCount + ti.pendingCount + logs.pendingCount
            val fromCache = st.isFromCache && mk.isFromCache
            if (totalPending == 0 && !fromCache) {
                _lastSyncedAtMs.value = System.currentTimeMillis()
            }
            CloudSchoolAcademicSnapshot(
                students = st.items,
                marks = mk.items,
                coScholastic = co.items,
                termInfos = ti.items,
                markEditLogs = logs.items,
                hasPendingWrites = totalPending > 0,
                pendingWriteCount = totalPending,
                isFromCache = fromCache
            )
        }
    }

    // =========================================================================
    // 2. SCHOOLS, CLASS GROUPS, CLASSES & SUBJECTS CRUD (Offline-First Enqueue)
    // =========================================================================
    fun createSchool(
        orgId: String,
        name: String,
        address: String,
        mobile: String,
        email: String,
        logoBase64: String,
        active: Boolean,
        templateSource: SchoolTemplateSource,
        existingSchools: List<CloudSchoolRecord>
    ): Result<String> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return Result.failure(IllegalArgumentException("School name is required."))
        val schoolDocId = "sch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "organisations/$orgId/schools/$schoolDocId"
        return try {
            requireUid()
            val (groups, classes) = when (templateSource) {
                is SchoolTemplateSource.CopyFromSchool -> {
                    val source = existingSchools.firstOrNull { it.stableId == templateSource.sourceSchoolId }
                    if (source != null) {
                        source.classGroups to source.classes
                    } else {
                        OrgCloudRepository.buildDefaultTemplateStructures()
                    }
                }
                SchoolTemplateSource.DefaultTemplate -> OrgCloudRepository.buildDefaultTemplateStructures()
            }
            val payload = mapOf(
                "schoolKey" to schoolDocId,
                "name" to cleanName.take(120),
                "address" to address.trim().take(300),
                "mobile" to mobile.trim().take(40),
                "email" to email.trim().take(160),
                "logoBase64" to logoBase64.take(215_000),
                "active" to active,
                "classGroups" to groups.map { it.toMap() },
                "classes" to classes.map { it.toMap() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val ref = db.collection("organisations").document(orgId)
                .collection("schools").document(schoolDocId)
            val task = ref.set(payload)
            attachSyncListeners(task, OperationType.CREATE, path) { ref.set(payload) }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "SCHOOL_CREATED",
                targetType = "school",
                targetId = schoolDocId,
                summary = "Created school '$cleanName'",
                schoolId = schoolDocId
            )
            Result.success(schoolDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun updateSchoolRecord(orgId: String, record: CloudSchoolRecord): Result<Unit> {
        val path = "organisations/$orgId/schools/${record.docId}"
        return try {
            requireUid()
            val payload = mapOf(
                "name" to record.name.trim().take(120),
                "address" to record.address.trim().take(300),
                "mobile" to record.mobile.trim().take(40),
                "email" to record.email.trim().take(160),
                "logoBase64" to record.logoBase64.take(215_000),
                "active" to record.active,
                "classGroups" to record.classGroups.map { it.toMap() },
                "classes" to record.classes.map { it.toMap() },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val ref = db.collection("organisations").document(orgId)
                .collection("schools").document(record.docId)
            val task = ref.update(payload)
            attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(payload) }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "SCHOOL_UPDATED",
                targetType = "school",
                targetId = record.docId,
                summary = "Updated school '${record.name.trim()}'",
                schoolId = record.docId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun deleteSchool(orgId: String, record: CloudSchoolRecord, studentCount: Int): Result<Unit> {
        if (studentCount > 0) {
            return Result.failure(IllegalStateException("Cannot delete '${record.name}' while it still has $studentCount student(s)."))
        }
        val path = "organisations/$orgId/schools/${record.docId}"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("schools").document(record.docId)
            val task = ref.delete()
            attachSyncListeners(task, OperationType.DELETE, path) { ref.delete() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "SCHOOL_DELETED",
                targetType = "school",
                targetId = record.docId,
                summary = "Deleted empty school '${record.name}'"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun updateOrgSettings(orgId: String, settings: SchoolSettingsEntity): Result<Unit> {
        val path = "organisations/$orgId"
        return try {
            requireUid()
            val payload = mapOf(
                "settings" to CloudOrgSettings.fromEntity(settings).toMap(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val ref = db.collection("organisations").document(orgId)
            val task = ref.update(payload)
            attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(payload) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. STUDENTS, MARKS (+ EDIT LOG), CO-SCHOLASTIC & TERM INFO (Offline-First!)
    // =========================================================================
    fun saveStudent(
        orgId: String,
        schoolDocId: String,
        existingDocId: String?,
        student: StudentEntity,
        classDocId: String
    ): Result<String> {
        val cleanName = student.name.trim()
        val cleanAdmNo = student.admNo.trim()
        if (cleanName.isEmpty() || cleanAdmNo.isEmpty()) {
            return Result.failure(IllegalArgumentException("Student name and Admission No. are required."))
        }
        val docId = existingDocId?.takeIf { it.isNotBlank() }
            ?: "stu_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val path = "organisations/$orgId/schools/$schoolDocId/students/$docId"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("schools").document(schoolDocId)
                .collection("students").document(docId)
            if (existingDocId.isNullOrBlank()) {
                val payload = mapOf(
                    "schoolId" to schoolDocId,
                    "classId" to classDocId.take(128),
                    "session" to student.session.trim().take(30),
                    "admNo" to cleanAdmNo.take(60),
                    "name" to cleanName.take(120),
                    "fatherName" to student.fatherName.trim().take(120),
                    "motherName" to student.motherName.trim().take(120),
                    "dob" to student.dob.trim().take(40),
                    "mobile" to student.mobile.trim().take(40),
                    "address" to student.address.trim().take(300),
                    "photoBase64" to student.photoBase64.take(215_000),
                    "className" to student.className.trim().take(40),
                    "section" to student.section.trim().uppercase().ifBlank { "A" }.take(10),
                    "rollNo" to student.rollNo.coerceIn(1, 9999).toLong(),
                    "sample" to student.isSample,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = ref.set(payload)
                attachSyncListeners(task, OperationType.CREATE, path) { ref.set(payload) }
            } else {
                val payload = mapOf(
                    "classId" to classDocId.take(128),
                    "session" to student.session.trim().take(30),
                    "admNo" to cleanAdmNo.take(60),
                    "name" to cleanName.take(120),
                    "fatherName" to student.fatherName.trim().take(120),
                    "motherName" to student.motherName.trim().take(120),
                    "dob" to student.dob.trim().take(40),
                    "mobile" to student.mobile.trim().take(40),
                    "address" to student.address.trim().take(300),
                    "photoBase64" to student.photoBase64.take(215_000),
                    "className" to student.className.trim().take(40),
                    "section" to student.section.trim().uppercase().ifBlank { "A" }.take(10),
                    "rollNo" to student.rollNo.coerceIn(1, 9999).toLong(),
                    "sample" to student.isSample,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = ref.update(payload)
                attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(payload) }
            }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = if (existingDocId.isNullOrBlank()) "STUDENT_ADDED" else "STUDENT_UPDATED",
                targetType = "student",
                targetId = docId,
                summary = "${if (existingDocId.isNullOrBlank()) "Added" else "Updated"} student '$cleanName' (${student.className}-${student.section}, Roll ${student.rollNo})",
                schoolId = schoolDocId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun deleteStudent(orgId: String, schoolDocId: String, studentDocId: String, studentName: String): Result<Unit> {
        val path = "organisations/$orgId/schools/$schoolDocId/students/$studentDocId"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("schools").document(schoolDocId)
                .collection("students").document(studentDocId)
            val task = ref.delete()
            attachSyncListeners(task, OperationType.DELETE, path) { ref.delete() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "STUDENT_DELETED",
                targetType = "student",
                targetId = studentDocId,
                summary = "Deleted student '$studentName'",
                schoolId = schoolDocId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    /**
     * Offline-first mark write:
     * Enqueues the mark update + append-only `markEditLogs` entry atomically in a single
     * Firestore WriteBatch against the local cache (zero waiting for server response).
     */
    fun saveMark(
        orgId: String,
        schoolDocId: String,
        studentDocId: String,
        studentName: String,
        className: String,
        subjectName: String,
        componentKey: String,
        oldValue: String,
        normalizedValue: String,
        existingMarkExists: Boolean,
        clientEditedAtMs: Long = System.currentTimeMillis()
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val editorName = orgRepo.currentEditorName().take(120)
        val markDocId = makeMarkDocId(studentDocId, subjectName, componentKey)
        val path = "organisations/$orgId/schools/$schoolDocId/marks/$markDocId"
        return try {
            val schoolRef = db.collection("organisations").document(orgId)
                .collection("schools").document(schoolDocId)
            val markRef = schoolRef.collection("marks").document(markDocId)
            val logId = "mlog_${clientEditedAtMs}_${UUID.randomUUID().toString().take(6)}"
            val logRef = schoolRef.collection("markEditLogs").document(logId)

            val runWrite = {
                val batch = db.batch()
                var changed = false
                if (normalizedValue.isBlank()) {
                    if (existingMarkExists) {
                        batch.delete(markRef)
                        changed = true
                    }
                } else if (existingMarkExists) {
                    batch.update(
                        markRef,
                        mapOf(
                            "className" to className.take(40),
                            "rawValue" to normalizedValue.take(16),
                            "updatedByUid" to uid,
                            "updatedByName" to editorName,
                            "clientEditedAtMs" to clientEditedAtMs,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    changed = true
                } else {
                    batch.set(
                        markRef,
                        mapOf(
                            "studentId" to studentDocId,
                            "schoolId" to schoolDocId,
                            "className" to className.take(40),
                            "subjectName" to subjectName.take(80),
                            "componentKey" to componentKey,
                            "rawValue" to normalizedValue.take(16),
                            "updatedByUid" to uid,
                            "updatedByName" to editorName,
                            "clientEditedAtMs" to clientEditedAtMs,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    changed = true
                }

                if (changed && oldValue.trim() != normalizedValue.trim()) {
                    batch.set(
                        logRef,
                        mapOf(
                            "markDocId" to markDocId,
                            "studentId" to studentDocId,
                            "studentName" to studentName.take(120),
                            "schoolId" to schoolDocId,
                            "className" to className.take(40),
                            "subjectName" to subjectName.take(80),
                            "componentKey" to componentKey,
                            "oldValue" to oldValue.trim().take(16),
                            "newValue" to normalizedValue.trim().take(16),
                            "editorUid" to uid,
                            "editorName" to editorName,
                            "clientEditedAtMs" to clientEditedAtMs,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (changed) {
                    batch.commit()
                } else null
            }

            runWrite()?.let { task ->
                attachSyncListeners(task, OperationType.WRITE, path) { runWrite() }
            }
            if (oldValue.trim() != normalizedValue.trim()) {
                orgRepo.logAuditNonBlocking(
                    orgId = orgId,
                    action = "MARK_UPDATED",
                    targetType = "mark",
                    targetId = markDocId,
                    summary = "$studentName ($className) $subjectName [$componentKey]: '${oldValue.ifBlank { "blank" }}' -> '${normalizedValue.ifBlank { "cleared" }}'",
                    schoolId = schoolDocId
                )
            }
            Result.success(markDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun saveMarksBatch(
        orgId: String,
        schoolDocId: String,
        entries: List<MarkEntity>,
        studentInfoByLongId: Map<Long, Triple<String, String, String>>, // studentLongId -> (docId, className, studentName)
        existingMarksByDocId: Map<String, String>,
        onBatchItemEnqueued: (markDocId: String, studentDocId: String, studentName: String, className: String, subjectName: String, componentKey: String, newValue: String, editedAtMs: Long) -> Unit = { _, _, _, _, _, _, _, _ -> }
    ): Result<Int> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val editorName = orgRepo.currentEditorName().take(120)
        val path = "organisations/$orgId/schools/$schoolDocId/marks"
        val nowMs = System.currentTimeMillis()
        return try {
            val schoolRef = db.collection("organisations").document(orgId)
                .collection("schools").document(schoolDocId)
            val marksCol = schoolRef.collection("marks")
            val logsCol = schoolRef.collection("markEditLogs")
            var written = 0
            // 150 entries max per batch (each writes 1 mark + 1 edit log = 300 ops < 500 Firestore batch limit)
            entries.chunked(150).forEach { chunk ->
                val batch = db.batch()
                var chunkOps = 0
                chunk.forEachIndexed { idx, m ->
                    val (stuDocId, clsName, stuName) = studentInfoByLongId[m.studentId] ?: return@forEachIndexed
                    val markDocId = makeMarkDocId(stuDocId, m.subjectName, m.componentKey)
                    val ref = marksCol.document(markDocId)
                    val exists = markDocId in existingMarksByDocId
                    val oldVal = existingMarksByDocId[markDocId].orEmpty()
                    val itemTs = nowMs + idx
                    var didWrite = false

                    if (m.rawValue.isBlank()) {
                        if (exists) {
                            batch.delete(ref)
                            didWrite = true
                        }
                    } else if (exists) {
                        batch.update(
                            ref,
                            mapOf(
                                "className" to clsName.take(40),
                                "rawValue" to m.rawValue.take(16),
                                "updatedByUid" to uid,
                                "updatedByName" to editorName,
                                "clientEditedAtMs" to itemTs,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        didWrite = true
                        written++
                    } else {
                        batch.set(
                            ref,
                            mapOf(
                                "studentId" to stuDocId,
                                "schoolId" to schoolDocId,
                                "className" to clsName.take(40),
                                "subjectName" to m.subjectName.take(80),
                                "componentKey" to m.componentKey,
                                "rawValue" to m.rawValue.take(16),
                                "updatedByUid" to uid,
                                "updatedByName" to editorName,
                                "clientEditedAtMs" to itemTs,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        didWrite = true
                        written++
                    }

                    if (didWrite) {
                        chunkOps++
                        onBatchItemEnqueued(markDocId, stuDocId, stuName, clsName, m.subjectName, m.componentKey, m.rawValue, itemTs)
                        if (oldVal.trim() != m.rawValue.trim()) {
                            val logId = "mlog_${itemTs}_${UUID.randomUUID().toString().take(6)}"
                            batch.set(
                                logsCol.document(logId),
                                mapOf(
                                    "markDocId" to markDocId,
                                    "studentId" to stuDocId,
                                    "studentName" to stuName.take(120),
                                    "schoolId" to schoolDocId,
                                    "className" to clsName.take(40),
                                    "subjectName" to m.subjectName.take(80),
                                    "componentKey" to m.componentKey,
                                    "oldValue" to oldVal.trim().take(16),
                                    "newValue" to m.rawValue.trim().take(16),
                                    "editorUid" to uid,
                                    "editorName" to editorName,
                                    "clientEditedAtMs" to itemTs,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                            chunkOps++
                        }
                    }
                }
                if (chunkOps > 0) {
                    val task = batch.commit()
                    attachSyncListeners(task, OperationType.WRITE, path)
                }
            }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "MARKS_BATCH_IMPORTED",
                targetType = "mark",
                targetId = schoolDocId,
                summary = "Saved $written mark(s) in batch CSV import",
                schoolId = schoolDocId
            )
            Result.success(written)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun saveCoScholastic(
        orgId: String,
        schoolDocId: String,
        studentDocId: String,
        termNo: Int,
        areaCode: String,
        grade: String,
        exists: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val coDocId = makeCoDocId(studentDocId, termNo, areaCode)
        val path = "organisations/$orgId/schools/$schoolDocId/coScholastic/$coDocId"
        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("schools").document(schoolDocId)
                .collection("coScholastic").document(coDocId)
            if (exists) {
                val payload = mapOf(
                    "grade" to grade.take(10),
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = ref.update(payload)
                attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(payload) }
            } else {
                val payload = mapOf(
                    "studentId" to studentDocId,
                    "schoolId" to schoolDocId,
                    "termNo" to termNo.coerceIn(1, 2).toLong(),
                    "areaCode" to areaCode.take(40),
                    "grade" to grade.take(10),
                    "updatedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = ref.set(payload)
                attachSyncListeners(task, OperationType.CREATE, path) { ref.set(payload) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun saveTermInfo(
        orgId: String,
        schoolDocId: String,
        studentDocId: String,
        info: TermInfoEntity,
        exists: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val tiDocId = makeTermInfoDocId(studentDocId, info.termNo)
        val path = "organisations/$orgId/schools/$schoolDocId/termInfo/$tiDocId"
        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("schools").document(schoolDocId)
                .collection("termInfo").document(tiDocId)
            if (exists) {
                val payload = mapOf(
                    "daysPresent" to info.daysPresent.coerceIn(0, 366).toLong(),
                    "workingDays" to info.workingDays.coerceIn(0, 366).toLong(),
                    "remarks" to info.remarks.take(400),
                    "promotedTo" to info.promotedTo.take(60),
                    "reportDate" to info.reportDate.take(40),
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = ref.update(payload)
                attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(payload) }
            } else {
                val payload = mapOf(
                    "studentId" to studentDocId,
                    "schoolId" to schoolDocId,
                    "termNo" to info.termNo.coerceIn(1, 2).toLong(),
                    "daysPresent" to info.daysPresent.coerceIn(0, 366).toLong(),
                    "workingDays" to info.workingDays.coerceIn(0, 366).toLong(),
                    "remarks" to info.remarks.take(400),
                    "promotedTo" to info.promotedTo.take(60),
                    "reportDate" to info.reportDate.take(40),
                    "updatedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = ref.set(payload)
                attachSyncListeners(task, OperationType.CREATE, path) { ref.set(payload) }
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
    // 4. OFFLINE-SAFE, IDEMPOTENT BACKUP RESTORE & LOCAL MIGRATION
    // =========================================================================
    /**
     * Enqueues all backup records directly against Firestore's local cache with
     * deterministic document IDs so:
     * - Restoring a JSON backup works 100% offline AND online without hanging.
     * - Re-running or syncing after an offline restore produces ZERO duplicate schools,
     *   students, marks, co-scholastic, or term info records.
     */
    fun uploadLocalBackupToOrganisation(
        orgId: String,
        snapshot: FullBackupData,
        existingSchoolDocIds: Set<String> = emptySet(),
        existingStudentDocIds: Set<String> = emptySet(),
        existingMarkDocIds: Set<String> = emptySet(),
        existingCoDocIds: Set<String> = emptySet(),
        existingTermInfoDocIds: Set<String> = emptySet(),
        onProgress: (completed: Int, total: Int, statusText: String) -> Unit
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val editorName = orgRepo.currentEditorName().take(120)
        val orgRef = db.collection("organisations").document(orgId)
        val validMarks = snapshot.marks.filter { it.componentKey in MarkComponent.validKeys && it.rawValue.isNotBlank() }
        val totalOps = (1 + snapshot.schools.size + snapshot.students.size +
            validMarks.size + snapshot.coScholastic.size + snapshot.termInfos.size).coerceAtLeast(1)
        var done = 0
        val nowMs = System.currentTimeMillis()

        return try {
            onProgress(0, totalOps, "Queuing global settings…")
            attachSyncListeners(
                orgRef.update(
                    mapOf(
                        "settings" to CloudOrgSettings.fromEntity(snapshot.settings).toMap(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                OperationType.UPDATE,
                "organisations/$orgId"
            )
            done++
            onProgress(done, totalOps, "Restoring ${snapshot.schools.size} school(s)…")

            // Deterministic doc IDs guarantee zero duplicates both offline and upon cloud sync
            val schoolDocIdByLocalId = mutableMapOf<Long, String>()
            val studentDocIdByLocalId = mutableMapOf<Long, Triple<String, String, StudentEntity>>()

            for (school in snapshot.schools) {
                val schDocId = OfflineSyncEngine.deterministicBackupSchoolDocId(school.name, school.id)
                schoolDocIdByLocalId[school.id] = schDocId
                val groups = snapshot.classGroups
                    .filter { it.schoolId == school.id }
                    .sortedBy { it.sortOrder }
                    .map { grp ->
                        val subs = snapshot.subjects
                            .filter { it.classGroupId == grp.id }
                            .sortedBy { it.sortOrder }
                            .map { it.name }
                            .ifEmpty { DefaultSubjects.forTemplateKey(grp.templateKey) }
                        CloudClassGroupItem(
                            id = "grp_${grp.id}",
                            name = grp.name,
                            shortLabel = grp.shortLabel,
                            sortOrder = grp.sortOrder,
                            templateKey = grp.templateKey,
                            subjects = subs
                        )
                    }
                val classes = snapshot.schoolClasses
                    .filter { it.schoolId == school.id }
                    .sortedBy { it.sortOrder }
                    .map { cls ->
                        CloudSchoolClassItem(
                            id = "cls_${cls.id}",
                            classGroupId = "grp_${cls.classGroupId}",
                            name = cls.name,
                            sortOrder = cls.sortOrder
                        )
                    }
                val schoolRef = orgRef.collection("schools").document(schDocId)
                val schTask = if (schDocId in existingSchoolDocIds) {
                    schoolRef.update(
                        mapOf(
                            "name" to school.name.take(120),
                            "address" to school.address.take(300),
                            "mobile" to school.mobile.take(40),
                            "email" to school.email.take(160),
                            "logoBase64" to school.logoBase64.take(215_000),
                            "active" to school.active,
                            "classGroups" to groups.map { it.toMap() },
                            "classes" to classes.map { it.toMap() },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    schoolRef.set(
                        mapOf(
                            "schoolKey" to schDocId,
                            "name" to school.name.take(120),
                            "address" to school.address.take(300),
                            "mobile" to school.mobile.take(40),
                            "email" to school.email.take(160),
                            "logoBase64" to school.logoBase64.take(215_000),
                            "active" to school.active,
                            "classGroups" to groups.map { it.toMap() },
                            "classes" to classes.map { it.toMap() },
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                attachSyncListeners(schTask, OperationType.WRITE, "organisations/$orgId/schools/$schDocId")
                done++
                onProgress(done, totalOps, "Restored school '${school.name}'")
            }

            // Students in batches of 200
            val studentsBySchool = snapshot.students.groupBy { it.schoolId }
            for ((localSchId, schStudents) in studentsBySchool) {
                val schDocId = schoolDocIdByLocalId[localSchId] ?: continue
                val col = orgRef.collection("schools").document(schDocId).collection("students")

                schStudents.chunked(200).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { st ->
                        val stuDocId = OfflineSyncEngine.deterministicBackupStudentDocId(
                            session = st.session,
                            admNo = st.admNo,
                            fallbackLocalId = st.id
                        )
                        studentDocIdByLocalId[st.id] = Triple(schDocId, stuDocId, st)
                        val ref = col.document(stuDocId)
                        if (stuDocId in existingStudentDocIds) {
                            batch.update(
                                ref,
                                mapOf(
                                    "classId" to "cls_${st.classId}",
                                    "session" to st.session.take(30),
                                    "admNo" to st.admNo.take(60),
                                    "name" to st.name.take(120),
                                    "fatherName" to st.fatherName.take(120),
                                    "motherName" to st.motherName.take(120),
                                    "dob" to st.dob.take(40),
                                    "mobile" to st.mobile.take(40),
                                    "address" to st.address.take(300),
                                    "photoBase64" to st.photoBase64.take(215_000),
                                    "className" to st.className.take(40),
                                    "section" to st.section.uppercase().ifBlank { "A" }.take(10),
                                    "rollNo" to st.rollNo.coerceIn(1, 9999).toLong(),
                                    "sample" to st.isSample,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        } else {
                            batch.set(
                                ref,
                                mapOf(
                                    "schoolId" to schDocId,
                                    "classId" to "cls_${st.classId}",
                                    "session" to st.session.take(30),
                                    "admNo" to st.admNo.take(60),
                                    "name" to st.name.take(120),
                                    "fatherName" to st.fatherName.take(120),
                                    "motherName" to st.motherName.take(120),
                                    "dob" to st.dob.take(40),
                                    "mobile" to st.mobile.take(40),
                                    "address" to st.address.take(300),
                                    "photoBase64" to st.photoBase64.take(215_000),
                                    "className" to st.className.take(40),
                                    "section" to st.section.uppercase().ifBlank { "A" }.take(10),
                                    "rollNo" to st.rollNo.coerceIn(1, 9999).toLong(),
                                    "sample" to st.isSample,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    attachSyncListeners(batch.commit(), OperationType.WRITE, "organisations/$orgId/schools/$schDocId/students")
                    done += chunk.size
                    onProgress(done, totalOps, "Restored $done / $totalOps records…")
                }
            }

            // Marks in batches of 200
            val marksBySchool = validMarks.groupBy { studentDocIdByLocalId[it.studentId]?.first }
            for ((schDocId, schMarks) in marksBySchool) {
                if (schDocId == null) continue
                val col = orgRef.collection("schools").document(schDocId).collection("marks")
                schMarks.chunked(200).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { m ->
                        val (_, stuDocId, st) = studentDocIdByLocalId[m.studentId] ?: return@forEach
                        val markDocId = makeMarkDocId(stuDocId, m.subjectName, m.componentKey)
                        val ref = col.document(markDocId)
                        if (markDocId in existingMarkDocIds) {
                            batch.update(
                                ref,
                                mapOf(
                                    "className" to st.className.take(40),
                                    "rawValue" to m.rawValue.take(16),
                                    "updatedByUid" to uid,
                                    "updatedByName" to editorName,
                                    "clientEditedAtMs" to nowMs,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        } else {
                            batch.set(
                                ref,
                                mapOf(
                                    "studentId" to stuDocId,
                                    "schoolId" to schDocId,
                                    "className" to st.className.take(40),
                                    "subjectName" to m.subjectName.take(80),
                                    "componentKey" to m.componentKey,
                                    "rawValue" to m.rawValue.take(16),
                                    "updatedByUid" to uid,
                                    "updatedByName" to editorName,
                                    "clientEditedAtMs" to nowMs,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    attachSyncListeners(batch.commit(), OperationType.WRITE, "organisations/$orgId/schools/$schDocId/marks")
                    done += chunk.size
                    onProgress(done, totalOps, "Restored $done / $totalOps records…")
                }
            }

            // Co-Scholastic in batches of 200
            val coBySchool = snapshot.coScholastic.groupBy { studentDocIdByLocalId[it.studentId]?.first }
            for ((schDocId, schCo) in coBySchool) {
                if (schDocId == null) continue
                val col = orgRef.collection("schools").document(schDocId).collection("coScholastic")
                schCo.chunked(200).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { c ->
                        val (_, stuDocId, _) = studentDocIdByLocalId[c.studentId] ?: return@forEach
                        val docId = makeCoDocId(stuDocId, c.termNo, c.areaCode)
                        val ref = col.document(docId)
                        if (docId in existingCoDocIds) {
                            batch.update(
                                ref,
                                mapOf(
                                    "grade" to c.grade.take(10),
                                    "updatedByUid" to uid,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        } else {
                            batch.set(
                                ref,
                                mapOf(
                                    "studentId" to stuDocId,
                                    "schoolId" to schDocId,
                                    "termNo" to c.termNo.coerceIn(1, 2).toLong(),
                                    "areaCode" to c.areaCode.take(40),
                                    "grade" to c.grade.take(10),
                                    "updatedByUid" to uid,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    attachSyncListeners(batch.commit(), OperationType.WRITE, "organisations/$orgId/schools/$schDocId/coScholastic")
                    done += chunk.size
                    onProgress(done, totalOps, "Restored $done / $totalOps records…")
                }
            }

            // Term Info in batches of 200
            val tiBySchool = snapshot.termInfos.groupBy { studentDocIdByLocalId[it.studentId]?.first }
            for ((schDocId, schTi) in tiBySchool) {
                if (schDocId == null) continue
                val col = orgRef.collection("schools").document(schDocId).collection("termInfo")
                schTi.chunked(200).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { t ->
                        val (_, stuDocId, _) = studentDocIdByLocalId[t.studentId] ?: return@forEach
                        val docId = makeTermInfoDocId(stuDocId, t.termNo)
                        val ref = col.document(docId)
                        if (docId in existingTermInfoDocIds) {
                            batch.update(
                                ref,
                                mapOf(
                                    "daysPresent" to t.daysPresent.coerceIn(0, 366).toLong(),
                                    "workingDays" to t.workingDays.coerceIn(0, 366).toLong(),
                                    "remarks" to t.remarks.take(400),
                                    "promotedTo" to t.promotedTo.take(60),
                                    "reportDate" to t.reportDate.take(40),
                                    "updatedByUid" to uid,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        } else {
                            batch.set(
                                ref,
                                mapOf(
                                    "studentId" to stuDocId,
                                    "schoolId" to schDocId,
                                    "termNo" to t.termNo.coerceIn(1, 2).toLong(),
                                    "daysPresent" to t.daysPresent.coerceIn(0, 366).toLong(),
                                    "workingDays" to t.workingDays.coerceIn(0, 366).toLong(),
                                    "remarks" to t.remarks.take(400),
                                    "promotedTo" to t.promotedTo.take(60),
                                    "reportDate" to t.reportDate.take(40),
                                    "updatedByUid" to uid,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    attachSyncListeners(batch.commit(), OperationType.WRITE, "organisations/$orgId/schools/$schDocId/termInfo")
                    done += chunk.size
                    onProgress(done, totalOps, "Restored $done / $totalOps records…")
                }
            }

            val suffix = if (orgRepo.isCurrentlyOnline()) "synced cleanly." else "saved offline — will sync automatically when online."
            val summary = "Restored ${snapshot.schools.size} school(s), ${snapshot.students.size} student(s), and ${validMarks.size} mark(s) ($suffix)"
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "LOCAL_DATA_MIGRATED",
                targetType = "migration",
                targetId = orgId,
                summary = summary.take(300)
            )
            onProgress(totalOps, totalOps, summary)
            Result.success(summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "organisations/$orgId/schools")
            Result.failure(e)
        }
    }

    // =========================================================================
    // SNAPSHOT PARSERS (Safe numeric coercion & ESTIMATE timestamps)
    // =========================================================================
    private fun parseSchool(doc: DocumentSnapshot): CloudSchoolRecord? {
        val name = doc.getString("name").orEmpty()
        if (name.isBlank()) return null
        val groups = (doc.get("classGroups") as? List<*>)
            ?.mapNotNull { CloudClassGroupItem.fromMap(it as? Map<*, *>) }
            ?.sortedBy { it.sortOrder }
            ?: emptyList()
        val classes = (doc.get("classes") as? List<*>)
            ?.mapNotNull { CloudSchoolClassItem.fromMap(it as? Map<*, *>) }
            ?.sortedBy { it.sortOrder }
            ?: emptyList()
        return CloudSchoolRecord(
            docId = doc.id,
            stableId = StableDocId.toLong(doc.id),
            name = name,
            address = doc.getString("address").orEmpty(),
            mobile = doc.getString("mobile").orEmpty(),
            email = doc.getString("email").orEmpty(),
            logoBase64 = doc.getString("logoBase64").orEmpty(),
            active = doc.getBoolean("active") ?: true,
            classGroups = groups,
            classes = classes,
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun parseStudent(doc: DocumentSnapshot, schoolDocId: String, stableSchoolId: Long): CloudStudentRecord {
        val classDocId = doc.getString("classId").orEmpty()
        val stableStudentId = StableDocId.toLong(doc.id)
        val stableClassId = if (classDocId.isNotBlank()) StableDocId.toLong("${schoolDocId}_cls_$classDocId") else 0L
        return CloudStudentRecord(
            docId = doc.id,
            schoolDocId = schoolDocId,
            entity = StudentEntity(
                id = stableStudentId,
                schoolId = stableSchoolId,
                classId = stableClassId,
                session = doc.getString("session").orEmpty().ifBlank { "2026-2027" },
                admNo = doc.getString("admNo").orEmpty(),
                name = doc.getString("name").orEmpty(),
                fatherName = doc.getString("fatherName").orEmpty(),
                motherName = doc.getString("motherName").orEmpty(),
                dob = doc.getString("dob").orEmpty(),
                mobile = doc.getString("mobile").orEmpty(),
                address = doc.getString("address").orEmpty(),
                photoBase64 = doc.getString("photoBase64").orEmpty(),
                className = doc.getString("className").orEmpty(),
                section = doc.getString("section").orEmpty().ifBlank { "A" },
                rollNo = (doc.getLong("rollNo") ?: 1L).toInt(),
                isSample = doc.getBoolean("sample") ?: false
            ),
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun parseMark(doc: DocumentSnapshot, schoolDocId: String): CloudMarkRecord {
        val studentDocId = doc.getString("studentId").orEmpty()
        return CloudMarkRecord(
            docId = doc.id,
            schoolDocId = schoolDocId,
            studentDocId = studentDocId,
            className = doc.getString("className").orEmpty(),
            entity = MarkEntity(
                studentId = StableDocId.toLong(studentDocId),
                subjectName = doc.getString("subjectName").orEmpty(),
                componentKey = doc.getString("componentKey").orEmpty(),
                rawValue = doc.getString("rawValue").orEmpty()
            ),
            updatedByUid = doc.getString("updatedByUid").orEmpty(),
            updatedByName = doc.getString("updatedByName").orEmpty(),
            clientEditedAtMs = doc.getLong("clientEditedAtMs") ?: 0L,
            hasPendingWrites = doc.metadata.hasPendingWrites(),
            isFromCache = doc.metadata.isFromCache(),
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun parseMarkEditLog(doc: DocumentSnapshot, schoolDocId: String): CloudMarkEditLog =
        CloudMarkEditLog(
            id = doc.id,
            markDocId = doc.getString("markDocId").orEmpty(),
            studentId = doc.getString("studentId").orEmpty(),
            studentName = doc.getString("studentName").orEmpty(),
            schoolId = doc.getString("schoolId").orEmpty().ifBlank { schoolDocId },
            className = doc.getString("className").orEmpty(),
            subjectName = doc.getString("subjectName").orEmpty(),
            componentKey = doc.getString("componentKey").orEmpty(),
            oldValue = doc.getString("oldValue").orEmpty(),
            newValue = doc.getString("newValue").orEmpty(),
            editorUid = doc.getString("editorUid").orEmpty(),
            editorName = doc.getString("editorName").orEmpty(),
            clientEditedAtMs = doc.getLong("clientEditedAtMs") ?: 0L,
            hasPendingWrites = doc.metadata.hasPendingWrites(),
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )

    private fun parseCoScholastic(doc: DocumentSnapshot, schoolDocId: String): CloudCoScholasticRecord {
        val studentDocId = doc.getString("studentId").orEmpty()
        return CloudCoScholasticRecord(
            docId = doc.id,
            schoolDocId = schoolDocId,
            studentDocId = studentDocId,
            entity = CoScholasticEntity(
                studentId = StableDocId.toLong(studentDocId),
                termNo = (doc.getLong("termNo") ?: 1L).toInt(),
                areaCode = doc.getString("areaCode").orEmpty(),
                grade = doc.getString("grade").orEmpty()
            ),
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun parseTermInfo(doc: DocumentSnapshot, schoolDocId: String): CloudTermInfoRecord {
        val studentDocId = doc.getString("studentId").orEmpty()
        return CloudTermInfoRecord(
            docId = doc.id,
            schoolDocId = schoolDocId,
            studentDocId = studentDocId,
            entity = TermInfoEntity(
                studentId = StableDocId.toLong(studentDocId),
                termNo = (doc.getLong("termNo") ?: 1L).toInt(),
                daysPresent = (doc.getLong("daysPresent") ?: 0L).toInt(),
                workingDays = (doc.getLong("workingDays") ?: 0L).toInt(),
                remarks = doc.getString("remarks").orEmpty(),
                promotedTo = doc.getString("promotedTo").orEmpty(),
                reportDate = doc.getString("reportDate").orEmpty()
            ),
            createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    companion object {
        fun makeMarkDocId(studentDocId: String, subjectName: String, componentKey: String): String =
            "${StableDocId.sanitizeSegment(studentDocId)}_${StableDocId.sanitizeSegment(subjectName)}_${StableDocId.sanitizeSegment(componentKey)}"

        fun makeCoDocId(studentDocId: String, termNo: Int, areaCode: String): String =
            "${StableDocId.sanitizeSegment(studentDocId)}_T${termNo}_${StableDocId.sanitizeSegment(areaCode)}"

        fun makeTermInfoDocId(studentDocId: String, termNo: Int): String =
            "${StableDocId.sanitizeSegment(studentDocId)}_T$termNo"
    }
}

private data class CollectionSlice<T>(
    val items: List<T>,
    val pendingCount: Int,
    val isFromCache: Boolean
)
