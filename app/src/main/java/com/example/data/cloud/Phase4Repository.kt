package com.example.data.cloud

import com.example.domain.AppModule
import com.example.domain.ClassSectionAssignment
import com.example.domain.DayOfWeekCode
import com.example.domain.MemberAccessProfile
import com.example.domain.OrgAccessEvaluator
import com.example.domain.PeriodSlot
import com.example.domain.PeriodValidationResult
import com.example.domain.Phase4Validator
import com.example.domain.PreferredTimeOfDay
import com.example.domain.SchoolPeriodStructure
import com.example.domain.SlotType
import com.example.domain.SubjectRule
import com.example.domain.SubjectTeacherAssignment
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
import java.util.UUID

data class CloudPhase4Snapshot(
    val teachers: List<TeacherProfile> = emptyList(),
    val periodStructuresBySchool: Map<String, SchoolPeriodStructure> = emptyMap(),
    val classAssignments: List<ClassSectionAssignment> = emptyList(),
    val subjectRules: List<SubjectRule> = emptyList(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase4Slice<T>(
    val items: List<T> = emptyList(),
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false
)

/**
 * Phase 4 Offline-First Repository:
 * Manages Teachers, Per-School Period Structures, Class-Subject-Teacher Assignments,
 * and Optional Subject Rules under `organisations/{orgId}/`.
 *
 * - Strictly gated by `AppModule.TEACHERS` ("Teachers and assignments").
 * - Uses `MetadataChanges.INCLUDE` so pending offline writes feed directly into
 *   the Phase 3 live Status Chip (`Online / Offline / Syncing / Sync error`).
 * - All writes enqueue immediately against Firestore's 150 MB local cache (no `.await()`),
 *   working seamlessly offline and syncing automatically when online.
 */
class Phase4Repository(
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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync Teachers & Assignments changes.")
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
    // 1. REAL-TIME OBSERVERS (Only active when member can view 'teachers')
    // =========================================================================
    fun observePhase4Data(orgId: String, member: MemberAccessProfile): Flow<CloudPhase4Snapshot> {
        if (!OrgAccessEvaluator.canViewModule(member, AppModule.TEACHERS)) {
            return flowOf(CloudPhase4Snapshot())
        }
        val orgRef = db.collection("organisations").document(orgId)

        val teachersFlow: Flow<Phase4Slice<TeacherProfile>> = orgRef
            .collection("teachers")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                Phase4Slice(
                    items = snap.documents.map { parseTeacher(it) }.sortedBy { it.name.lowercase() },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }

        val periodFlow: Flow<Phase4Slice<SchoolPeriodStructure>> = orgRef
            .collection("periodStructures")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                Phase4Slice(
                    items = snap.documents.map { parsePeriodStructure(it) },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }

        val assignmentsFlow: Flow<Phase4Slice<ClassSectionAssignment>> = orgRef
            .collection("classAssignments")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                Phase4Slice(
                    items = snap.documents.map { parseClassAssignment(it) },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }

        val rulesFlow: Flow<Phase4Slice<SubjectRule>> = orgRef
            .collection("subjectRules")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                Phase4Slice(
                    items = snap.documents.map { parseSubjectRule(it) }.sortedBy { it.subjectName.lowercase() },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }

        return combine(teachersFlow, periodFlow, assignmentsFlow, rulesFlow) { t, p, a, r ->
            CloudPhase4Snapshot(
                teachers = t.items,
                periodStructuresBySchool = p.items.associateBy { it.schoolId },
                classAssignments = a.items,
                subjectRules = r.items,
                pendingWriteCount = t.pendingCount + p.pendingCount + a.pendingCount + r.pendingCount,
                isFromCache = t.isFromCache && p.isFromCache
            )
        }
    }

    // =========================================================================
    // 2. TEACHERS CRUD (Offline-First)
    // =========================================================================
    fun saveTeacher(
        orgId: String,
        teacher: TeacherProfile,
        isUpdate: Boolean
    ): Result<String> {
        val cleanName = teacher.name.trim()
        if (cleanName.isEmpty()) return Result.failure(IllegalArgumentException("Teacher name is required."))
        val docId = teacher.id.takeIf { it.isNotBlank() }
            ?: if (teacher.linkedMemberUid.isNotBlank()) {
                makeMemberTeacherDocId(teacher.linkedMemberUid)
            } else {
                "tchr_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            }
        val path = "organisations/$orgId/teachers/$docId"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("teachers").document(docId)
            val cleanSubjects = teacher.subjects.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(40)
            val cleanSchools = teacher.schoolIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(50)
            val cleanOffDays = teacher.weeklyOffDays.map { it.wireValue }.distinct().take(7)

            val baseFields = mapOf(
                "name" to cleanName.take(120),
                "mobile" to teacher.mobile.trim().take(40),
                "email" to teacher.email.trim().take(160),
                "photoBase64" to teacher.photoBase64.take(215_000),
                "subjects" to cleanSubjects,
                "schoolIds" to cleanSchools,
                "maxPeriodsPerDay" to teacher.maxPeriodsPerDay.coerceIn(1, 20).toLong(),
                "maxConsecutivePeriods" to teacher.maxConsecutivePeriods.coerceIn(1, 20).toLong(),
                "weeklyOffDays" to cleanOffDays,
                "active" to teacher.active,
                "linkedMemberUid" to teacher.linkedMemberUid.trim().take(128),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (isUpdate) {
                val task = ref.update(baseFields)
                attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(baseFields) }
            } else {
                val fullPayload = baseFields + ("createdAt" to FieldValue.serverTimestamp())
                val task = ref.set(fullPayload)
                attachSyncListeners(task, OperationType.CREATE, path) { ref.set(fullPayload) }
            }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = if (isUpdate) "TEACHER_UPDATED" else "TEACHER_ADDED",
                targetType = "teacher",
                targetId = docId,
                summary = "${if (isUpdate) "Updated" else "Added"} teacher '$cleanName' (${cleanSchools.size} school(s), ${cleanSubjects.size} subject(s))"
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun setTeacherActive(
        orgId: String,
        teacherId: String,
        teacherName: String,
        active: Boolean
    ): Result<Unit> {
        val path = "organisations/$orgId/teachers/$teacherId"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("teachers").document(teacherId)
            val payload = mapOf(
                "active" to active,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = ref.update(payload)
            attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(payload) }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = if (active) "TEACHER_ACTIVATED" else "TEACHER_DEACTIVATED",
                targetType = "teacher",
                targetId = teacherId,
                summary = "${if (active) "Activated" else "Deactivated"} teacher '$teacherName'"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun deleteTeacher(
        orgId: String,
        teacher: TeacherProfile,
        allAssignments: List<ClassSectionAssignment>
    ): Result<Unit> {
        val guardError = Phase4Validator.canDeleteTeacher(teacher.id, allAssignments)
        if (guardError != null) {
            return Result.failure(IllegalStateException(guardError))
        }
        val path = "organisations/$orgId/teachers/${teacher.id}"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("teachers").document(teacher.id)
            val task = ref.delete()
            attachSyncListeners(task, OperationType.DELETE, path) { ref.delete() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "TEACHER_DELETED",
                targetType = "teacher",
                targetId = teacher.id,
                summary = "Deleted unassigned teacher '${teacher.name}'"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. PERIOD STRUCTURE CRUD (Per School, Validated, Offline-First)
    // =========================================================================
    fun savePeriodStructure(
        orgId: String,
        structure: SchoolPeriodStructure,
        existsInCloud: Boolean
    ): Result<Unit> {
        val validation = Phase4Validator.validatePeriodStructure(structure)
        if (validation is PeriodValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(validation.errors.first()))
        }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val schoolId = structure.schoolId
        val path = "organisations/$orgId/periodStructures/$schoolId"
        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("periodStructures").document(schoolId)

            val slotsMap = linkedMapOf<String, List<Map<String, Any>>>()
            structure.workingDays.sortedBy { it.sortOrder }.forEach { day ->
                val daySlots = structure.slotsFor(day).mapIndexed { idx, slot ->
                    mapOf(
                        "id" to slot.id.ifBlank { "${day.wireValue}_slot_${idx + 1}" },
                        "type" to slot.type.wireValue,
                        "label" to slot.label.trim().ifBlank { slot.type.label }.take(60),
                        "startMinutes" to slot.startMinutes.toLong(),
                        "endMinutes" to slot.endMinutes.toLong()
                    )
                }
                slotsMap[day.wireValue] = daySlots
            }

            val baseFields = mapOf(
                "schoolId" to schoolId,
                "workingDays" to structure.workingDays.sortedBy { it.sortOrder }.map { it.wireValue },
                "slotsByDay" to slotsMap,
                "updatedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (existsInCloud) {
                val task = ref.update(baseFields)
                attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(baseFields) }
            } else {
                val fullPayload = baseFields + ("createdAt" to FieldValue.serverTimestamp())
                val task = ref.set(fullPayload)
                attachSyncListeners(task, OperationType.CREATE, path) { ref.set(fullPayload) }
            }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "PERIOD_STRUCTURE_SAVED",
                targetType = "periodStructure",
                targetId = schoolId,
                summary = "Saved period structure (${structure.workingDays.size} day(s), ${structure.totalTeachingPeriodsPerWeek()} periods/week)",
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

    // =========================================================================
    // 4. CLASS-SUBJECT-TEACHER ASSIGNMENTS CRUD (Offline-First)
    // =========================================================================
    fun saveClassAssignment(
        orgId: String,
        assignment: ClassSectionAssignment,
        existsInCloud: Boolean
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanClass = assignment.className.trim()
        val cleanSection = assignment.section.trim().uppercase().ifBlank { "A" }
        if (cleanClass.isEmpty()) {
            return Result.failure(IllegalArgumentException("Class name is required."))
        }
        val docId = assignment.id.takeIf { it.isNotBlank() }
            ?: makeAssignmentDocId(assignment.schoolId, cleanClass, cleanSection)
        val path = "organisations/$orgId/classAssignments/$docId"
        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("classAssignments").document(docId)

            val subjectsPayload = assignment.subjectAssignments.take(40).map { sa ->
                mapOf(
                    "subjectName" to sa.subjectName.trim().take(80),
                    "teacherId" to sa.teacherId.trim().take(128),
                    "periodsPerWeek" to sa.periodsPerWeek.coerceIn(0, 60).toLong()
                )
            }

            val baseFields = mapOf(
                "schoolId" to assignment.schoolId,
                "className" to cleanClass.take(40),
                "section" to cleanSection.take(10),
                "classTeacherId" to assignment.classTeacherId.trim().take(128),
                "subjectAssignments" to subjectsPayload,
                "updatedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (existsInCloud) {
                val task = ref.update(baseFields)
                attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(baseFields) }
            } else {
                val fullPayload = baseFields + ("createdAt" to FieldValue.serverTimestamp())
                val task = ref.set(fullPayload)
                attachSyncListeners(task, OperationType.CREATE, path) { ref.set(fullPayload) }
            }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "CLASS_ASSIGNMENTS_SAVED",
                targetType = "assignment",
                targetId = docId,
                summary = "Saved assignments for $cleanClass-$cleanSection (${assignment.totalAssignedPeriods} periods/week)",
                schoolId = assignment.schoolId
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
    // 5. SUBJECT RULES CRUD (Optional per subject in a school, Offline-First)
    // =========================================================================
    fun saveSubjectRule(
        orgId: String,
        rule: SubjectRule,
        existsInCloud: Boolean
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanSubject = rule.subjectName.trim()
        if (cleanSubject.isEmpty()) {
            return Result.failure(IllegalArgumentException("Subject name is required."))
        }
        val docId = rule.id.takeIf { it.isNotBlank() }
            ?: makeSubjectRuleDocId(rule.schoolId, cleanSubject)
        val path = "organisations/$orgId/subjectRules/$docId"
        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("subjectRules").document(docId)

            val baseFields = mapOf(
                "schoolId" to rule.schoolId,
                "subjectName" to cleanSubject.take(80),
                "preferredTimeOfDay" to rule.preferredTimeOfDay.wireValue,
                "allowDoublePeriod" to rule.allowDoublePeriod,
                "maxOncePerDay" to rule.maxOncePerDay,
                "updatedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (existsInCloud) {
                val task = ref.update(baseFields)
                attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(baseFields) }
            } else {
                val fullPayload = baseFields + ("createdAt" to FieldValue.serverTimestamp())
                val task = ref.set(fullPayload)
                attachSyncListeners(task, OperationType.CREATE, path) { ref.set(fullPayload) }
            }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "SUBJECT_RULE_SAVED",
                targetType = "subjectRule",
                targetId = docId,
                summary = "Updated subject rules for '$cleanSubject' (${rule.preferredTimeOfDay.label})",
                schoolId = rule.schoolId
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
    // 6. PARSERS & DETERMINISTIC DOC IDS
    // =========================================================================
    private fun parseTeacher(doc: DocumentSnapshot): TeacherProfile {
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

    private fun parsePeriodStructure(doc: DocumentSnapshot): SchoolPeriodStructure {
        val schoolId = doc.getString("schoolId").orEmpty().ifBlank { doc.id }
        val workingDays = (doc.get("workingDays") as? List<*>)
            ?.mapNotNull { DayOfWeekCode.fromRaw(it as? String) }
            ?.distinct()
            ?.sortedBy { it.sortOrder }
            .orEmpty()
            .ifEmpty { DayOfWeekCode.defaultWorkingDays }

        val slotsByDay = linkedMapOf<DayOfWeekCode, List<PeriodSlot>>()
        val rawMap = doc.get("slotsByDay") as? Map<*, *>
        rawMap?.forEach { (k, v) ->
            val day = DayOfWeekCode.fromRaw(k as? String) ?: return@forEach
            val rawSlots = v as? List<*> ?: return@forEach
            val parsed = rawSlots.mapIndexedNotNull { idx, item ->
                val m = item as? Map<*, *> ?: return@mapIndexedNotNull null
                val start = (m["startMinutes"] as? Number)?.toInt() ?: return@mapIndexedNotNull null
                val end = (m["endMinutes"] as? Number)?.toInt() ?: return@mapIndexedNotNull null
                val type = SlotType.fromRaw(m["type"] as? String)
                PeriodSlot(
                    id = (m["id"] as? String).orEmpty().ifBlank { "${day.wireValue}_slot_${idx + 1}" },
                    type = type,
                    label = (m["label"] as? String).orEmpty().ifBlank { type.label },
                    startMinutes = start,
                    endMinutes = end
                )
            }
            slotsByDay[day] = parsed
        }
        return SchoolPeriodStructure(
            schoolId = schoolId,
            workingDays = workingDays,
            slotsByDay = slotsByDay,
            isCustomized = true
        )
    }

    private fun parseClassAssignment(doc: DocumentSnapshot): ClassSectionAssignment {
        val rawSubs = doc.get("subjectAssignments") as? List<*>
        val parsedSubs = rawSubs?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val subName = (m["subjectName"] as? String).orEmpty()
            if (subName.isBlank()) return@mapNotNull null
            SubjectTeacherAssignment(
                subjectName = subName,
                teacherId = (m["teacherId"] as? String).orEmpty(),
                periodsPerWeek = ((m["periodsPerWeek"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)
            )
        } ?: emptyList()

        return ClassSectionAssignment(
            id = doc.id,
            schoolId = doc.getString("schoolId").orEmpty(),
            className = doc.getString("className").orEmpty(),
            section = doc.getString("section").orEmpty().ifBlank { "A" },
            classTeacherId = doc.getString("classTeacherId").orEmpty(),
            subjectAssignments = parsedSubs
        )
    }

    private fun parseSubjectRule(doc: DocumentSnapshot): SubjectRule = SubjectRule(
        id = doc.id,
        schoolId = doc.getString("schoolId").orEmpty(),
        subjectName = doc.getString("subjectName").orEmpty(),
        preferredTimeOfDay = PreferredTimeOfDay.fromRaw(doc.getString("preferredTimeOfDay")),
        allowDoublePeriod = doc.getBoolean("allowDoublePeriod") ?: false,
        maxOncePerDay = doc.getBoolean("maxOncePerDay") ?: false
    )

    companion object {
        fun makeMemberTeacherDocId(uid: String): String = "tchr_member_${uid.trim()}"

        fun makeAssignmentDocId(schoolId: String, className: String, section: String): String =
            "assign_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(className)}_${StableDocId.sanitizeSegment(section.uppercase())}"

        fun makeSubjectRuleDocId(schoolId: String, subjectName: String): String =
            "srule_${StableDocId.sanitizeSegment(schoolId)}_${StableDocId.sanitizeSegment(subjectName.uppercase())}"
    }
}
