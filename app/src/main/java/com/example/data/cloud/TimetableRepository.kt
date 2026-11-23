package com.example.data.cloud

import com.example.domain.AppModule
import com.example.domain.DayOfWeekCode
import com.example.domain.MemberAccessProfile
import com.example.domain.OrgAccessEvaluator
import com.example.domain.PeriodSlot
import com.example.domain.SchoolPeriodStructure
import com.example.domain.SlotType
import com.example.domain.TimetableCell
import com.example.domain.TimetableEngine
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

data class CloudTimetableSnapshot(
    val cells: List<TimetableCell> = emptyList(),
    val layoutsBySchool: Map<String, SchoolPeriodStructure> = emptyMap(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false,
    val hasConfirmedServerSnapshot: Boolean = false
)

private data class TimetableSlice<T>(
    val items: List<T> = emptyList(),
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false,
    val hasConfirmedServer: Boolean = false
)

/**
 * Phase 5 Offline-First Timetable Repository:
 * - Stores timetable cells under `organisations/{orgId}/timetableCells/{deterministicId}`
 *   so cross-school conflict checks, teacher week views, and free-period lists work across
 *   all schools in the organisation instantaneously, both online and offline.
 * - Uses deterministic document IDs (`TimetableEngine.makeCellDocId`) so two phones editing
 *   offline can never create duplicate cells for the same slot.
 * - Strictly gated by `AppModule.TIMETABLE` ("Timetable").
 * - All writes commit immediately against Firestore's 150 MB local cache (no `.await()`),
 *   feeding pending writes & sync errors directly into the Phase 3 live Status Chip.
 */
class TimetableRepository(
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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync Timetable changes.")
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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Timetable retry failed.")
            false
        }
    }

    // =========================================================================
    // 1. REAL-TIME OBSERVERS (Strictly gated by AppModule.TIMETABLE)
    // =========================================================================
    fun observeTimetable(orgId: String, member: MemberAccessProfile): Flow<CloudTimetableSnapshot> {
        if (!OrgAccessEvaluator.canViewModule(member, AppModule.TIMETABLE)) {
            return flowOf(CloudTimetableSnapshot())
        }
        val orgRef = db.collection("organisations").document(orgId)

        val cellsFlow: Flow<TimetableSlice<TimetableCell>> = orgRef
            .collection("timetableCells")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val fromCache = snap.metadata.isFromCache()
                TimetableSlice(
                    items = snap.documents.map { parseCell(it) },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = fromCache,
                    hasConfirmedServer = !fromCache && !hasPending
                )
            }

        val layoutsFlow: Flow<TimetableSlice<SchoolPeriodStructure>> = orgRef
            .collection("timetableLayouts")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                TimetableSlice(
                    items = snap.documents.map { parseLayout(it) },
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }

        return combine(cellsFlow, layoutsFlow) { cells, layouts ->
            CloudTimetableSnapshot(
                cells = cells.items,
                layoutsBySchool = layouts.items.associateBy { it.schoolId },
                pendingWriteCount = cells.pendingCount + layouts.pendingCount,
                isFromCache = cells.isFromCache && layouts.isFromCache,
                hasConfirmedServerSnapshot = cells.hasConfirmedServer
            )
        }
    }

    // =========================================================================
    // 2. CELL WRITES (Offline-First, Deterministic IDs, Non-Blocking)
    // =========================================================================
    fun saveCell(
        orgId: String,
        cell: TimetableCell,
        existsInCloud: Boolean,
        editorName: String,
        clientEditedAtMs: Long = System.currentTimeMillis()
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        val cleanSubject = cell.subjectName.trim()
        if (cleanSubject.isEmpty()) {
            return Result.failure(IllegalArgumentException("Subject is required."))
        }
        val cleanSection = cell.section.trim().uppercase().ifBlank { "A" }
        val docId = cell.id.takeIf { it.isNotBlank() }
            ?: TimetableEngine.makeCellDocId(
                schoolId = cell.schoolId,
                session = cell.session,
                className = cell.className,
                section = cleanSection,
                day = cell.day,
                slotId = cell.slotId
            )
        val path = "organisations/$orgId/timetableCells/$docId"
        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("timetableCells").document(docId)
            val baseFields = mapOf(
                "schoolId" to cell.schoolId,
                "session" to cell.session.trim().take(30),
                "className" to cell.className.trim().take(40),
                "section" to cleanSection.take(10),
                "day" to cell.day.wireValue,
                "slotId" to cell.slotId.trim().take(64),
                "subjectName" to cleanSubject.take(80),
                "teacherId" to cell.teacherId.trim().take(128),
                "teacherName" to cell.teacherName.trim().take(120),
                "locked" to cell.locked,
                "updatedByUid" to uid,
                "updatedByName" to editorName.trim().ifBlank { orgRepo.currentUserName() }.take(120),
                "clientEditedAtMs" to clientEditedAtMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (existsInCloud) {
                val updatePayload = mapOf(
                    "subjectName" to baseFields["subjectName"]!!,
                    "teacherId" to baseFields["teacherId"]!!,
                    "teacherName" to baseFields["teacherName"]!!,
                    "locked" to baseFields["locked"]!!,
                    "updatedByUid" to uid,
                    "updatedByName" to baseFields["updatedByName"]!!,
                    "clientEditedAtMs" to clientEditedAtMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                val task = ref.update(updatePayload)
                attachSyncListeners(task, OperationType.UPDATE, path) { ref.update(updatePayload) }
            } else {
                val fullPayload = baseFields + ("createdAt" to FieldValue.serverTimestamp())
                val task = ref.set(fullPayload)
                attachSyncListeners(task, OperationType.CREATE, path) { ref.set(fullPayload) }
            }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = if (existsInCloud) "TIMETABLE_CELL_UPDATED" else "TIMETABLE_CELL_SET",
                targetType = "timetable",
                targetId = docId,
                summary = "${cell.className}-$cleanSection ${cell.day.shortLabel}: $cleanSubject (${cell.teacherName.ifBlank { "No teacher" }})",
                schoolId = cell.schoolId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun toggleCellLock(
        orgId: String,
        cell: TimetableCell,
        newLocked: Boolean,
        editorName: String,
        clientEditedAtMs: Long = System.currentTimeMillis()
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        val path = "organisations/$orgId/timetableCells/${cell.id}"
        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("timetableCells").document(cell.id)
            val payload = mapOf(
                "locked" to newLocked,
                "updatedByUid" to uid,
                "updatedByName" to editorName.trim().ifBlank { orgRepo.currentUserName() }.take(120),
                "clientEditedAtMs" to clientEditedAtMs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
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

    fun clearCell(
        orgId: String,
        cell: TimetableCell
    ): Result<Unit> {
        val path = "organisations/$orgId/timetableCells/${cell.id}"
        return try {
            requireUid()
            val ref = db.collection("organisations").document(orgId)
                .collection("timetableCells").document(cell.id)
            val task = ref.delete()
            attachSyncListeners(task, OperationType.DELETE, path) { ref.delete() }
            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "TIMETABLE_CELL_CLEARED",
                targetType = "timetable",
                targetId = cell.id,
                summary = "Cleared ${cell.classSectionLabel} ${cell.day.shortLabel} (${cell.subjectName})",
                schoolId = cell.schoolId
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
     * Mirrors a school's period structure into `timetableLayouts/{schoolId}` so view-only Timetable
     * users without access to Phase 4's `Teachers and assignments` still render exact period timings.
     */
    fun mirrorSchoolLayoutNonBlocking(
        orgId: String,
        structure: SchoolPeriodStructure,
        existsInLayoutCollection: Boolean
    ) {
        val uid = auth.currentUser?.uid ?: return
        val schoolId = structure.schoolId
        if (schoolId.isBlank()) return
        val ref = db.collection("organisations").document(orgId)
            .collection("timetableLayouts").document(schoolId)
        val slotsMap = linkedMapOf<String, List<Map<String, Any>>>()
        structure.workingDays.sortedBy { it.sortOrder }.forEach { day ->
            slotsMap[day.wireValue] = structure.slotsFor(day).mapIndexed { idx, slot ->
                mapOf(
                    "id" to slot.id.ifBlank { "${day.wireValue}_slot_${idx + 1}" },
                    "type" to slot.type.wireValue,
                    "label" to slot.label.trim().ifBlank { slot.type.label }.take(60),
                    "startMinutes" to slot.startMinutes.toLong(),
                    "endMinutes" to slot.endMinutes.toLong()
                )
            }
        }
        val base = mapOf(
            "schoolId" to schoolId,
            "workingDays" to structure.workingDays.sortedBy { it.sortOrder }.map { it.wireValue },
            "slotsByDay" to slotsMap,
            "updatedByUid" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (existsInLayoutCollection) {
            ref.update(base).addOnFailureListener {
                // If doc did not exist yet, create it
                ref.set(base + ("createdAt" to FieldValue.serverTimestamp()))
            }
        } else {
            ref.set(base + ("createdAt" to FieldValue.serverTimestamp()))
        }
    }

    // =========================================================================
    // 3. PARSERS
    // =========================================================================
    private fun parseCell(doc: DocumentSnapshot): TimetableCell = TimetableCell(
        id = doc.id,
        schoolId = doc.getString("schoolId").orEmpty(),
        session = doc.getString("session").orEmpty(),
        className = doc.getString("className").orEmpty(),
        section = doc.getString("section").orEmpty().ifBlank { "A" },
        day = DayOfWeekCode.fromRaw(doc.getString("day")) ?: DayOfWeekCode.MON,
        slotId = doc.getString("slotId").orEmpty(),
        subjectName = doc.getString("subjectName").orEmpty(),
        teacherId = doc.getString("teacherId").orEmpty(),
        teacherName = doc.getString("teacherName").orEmpty(),
        locked = doc.getBoolean("locked") ?: false,
        updatedByUid = doc.getString("updatedByUid").orEmpty(),
        updatedByName = doc.getString("updatedByName").orEmpty(),
        clientEditedAtMs = (doc.get("clientEditedAtMs") as? Number)?.toLong() ?: 0L,
        hasPendingWrites = doc.metadata.hasPendingWrites(),
        isFromCache = doc.metadata.isFromCache()
    )

    private fun parseLayout(doc: DocumentSnapshot): SchoolPeriodStructure {
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
            slotsByDay[day] = rawSlots.mapIndexedNotNull { idx, item ->
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
        }
        return SchoolPeriodStructure(
            schoolId = schoolId,
            workingDays = workingDays,
            slotsByDay = slotsByDay,
            isCustomized = true
        )
    }
}
