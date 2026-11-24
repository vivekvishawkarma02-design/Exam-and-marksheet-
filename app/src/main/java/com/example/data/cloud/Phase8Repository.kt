package com.example.data.cloud

import com.example.domain.AppModule
import com.example.domain.AttendanceConflictNotice
import com.example.domain.MemberAccessProfile
import com.example.domain.OrgAccessEvaluator
import com.example.domain.SchoolCalendarConfig
import com.example.domain.StudentAttendanceAccessEvaluator
import com.example.domain.StudentAttendanceChangeItem
import com.example.domain.StudentAttendanceEditLog
import com.example.domain.StudentAttendanceEngine
import com.example.domain.StudentAttendanceSheet
import com.example.domain.TeacherAccessScope
import com.example.util.OperationType
import com.example.util.friendlyFirestoreErrorMessage
import com.example.util.handleFirestoreError
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
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
import java.util.concurrent.ConcurrentHashMap

data class CloudPhase8Snapshot(
    val schoolCalendars: List<SchoolCalendarConfig> = emptyList(),
    val studentAttendanceSheets: List<StudentAttendanceSheet> = emptyList(),
    val attendanceEditLogs: List<StudentAttendanceEditLog> = emptyList(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class Phase8Slice<T>(
    val value: T,
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false
)

private data class LocalAttendanceEditTracker(
    val docId: String,
    val sectionLabel: String,
    val date: String,
    val localEditedAtMs: Long
)

/**
 * Phase 8 Offline-First Student Attendance Repository:
 * - `organisations/{orgId}/schoolCalendars/{calId}`: per-school + session working days,
 *   holidays, declared off days, half working days, working day exceptions, and Term-I/Term-II dates.
 * - `organisations/{orgId}/studentAttendance/{sattId}`: deterministic one-doc-per-section-per-date
 *   holding all students' statuses (`P`, `A`, `L`, `Lt`, `H`) and reasons. Syncs offline with
 *   latest-edit-wins conflict notices matching Phase 3.
 * - `organisations/{orgId}/studentAttendanceEditLogs/{logId}`: append-only edit history
 *   recording who, when, and exact student status diffs for post-day / subsequent edits.
 */
class Phase8Repository(
    private val orgRepo: OrgCloudRepository,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val db: FirebaseFirestore get() = orgRepo.db

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _conflictNotices = MutableStateFlow<List<AttendanceConflictNotice>>(emptyList())
    val conflictNotices: StateFlow<List<AttendanceConflictNotice>> = _conflictNotices.asStateFlow()

    private val recentLocalEdits = ConcurrentHashMap<String, LocalAttendanceEditTracker>()

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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync student attendance changes.")
            if (retryAction != null) lastRetryAction = retryAction
        }
    }

    fun clearSyncError() {
        _syncError.value = null
        lastRetryAction = null
    }

    fun dismissConflictNotice(noticeId: String) {
        _conflictNotices.value = _conflictNotices.value.filterNot { it.id == noticeId }
    }

    fun clearConflictNotices() {
        _conflictNotices.value = emptyList()
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
    // 1. REAL-TIME OBSERVERS (Strictly scoped to member permissions + CT scope)
    // =========================================================================
    fun observePhase8Data(
        orgId: String,
        member: MemberAccessProfile,
        teacherScope: TeacherAccessScope?,
        currentUid: String
    ): Flow<CloudPhase8Snapshot> {
        if (!OrgAccessEvaluator.isActive(member) || currentUid.isBlank()) {
            return flowOf(CloudPhase8Snapshot())
        }
        val canAccessAttendance = StudentAttendanceAccessEvaluator.canAccessStudentAttendanceModule(member, teacherScope)
        val canReadForReportsOrCo = OrgAccessEvaluator.canViewModule(member, AppModule.REPORTS) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.CO_SCHOLASTIC)

        if (!canAccessAttendance && !canReadForReportsOrCo) {
            return flowOf(CloudPhase8Snapshot())
        }

        val orgRef = db.collection("organisations").document(orgId)

        val calendarsFlow: Flow<Phase8Slice<List<SchoolCalendarConfig>>> = orgRef
            .collection("schoolCalendars")
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                val items = snap.documents.mapNotNull { doc ->
                    SchoolCalendarConfig.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                }.filter { cal -> OrgAccessEvaluator.canAccessSchool(member, cal.schoolId) }
                Phase8Slice(
                    value = items,
                    pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                    isFromCache = snap.metadata.isFromCache()
                )
            }

        val hasBroadRead = OrgAccessEvaluator.canViewModule(member, AppModule.ATTENDANCE) || canReadForReportsOrCo
        val ownSections: List<Triple<String, String, String>> = teacherScope?.classTeacherSections
            ?.mapNotNull { key ->
                val parts = key.split("|")
                if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else null
            }
            ?.filter { (sch, _, _) -> OrgAccessEvaluator.canAccessSchool(member, sch) }
            .orEmpty()

        val sheetsFlow: Flow<Phase8Slice<List<StudentAttendanceSheet>>> = if (hasBroadRead) {
            orgRef.collection("studentAttendance")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        StudentAttendanceSheet.fromMap(
                            docId = doc.id,
                            data = doc.data,
                            hasPendingWrites = doc.metadata.hasPendingWrites(),
                            isFromCache = doc.metadata.isFromCache()
                        )
                    }.filter { sheet -> OrgAccessEvaluator.canAccessSchool(member, sheet.schoolId) }

                    processSheetConflicts(items, snap.metadata.hasPendingWrites(), currentUid)
                    Phase8Slice(
                        value = items,
                        pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                        isFromCache = snap.metadata.isFromCache()
                    )
                }
        } else if (ownSections.isNotEmpty()) {
            val sectionFlows = ownSections.map { (schId, clsName, sec) ->
                orgRef.collection("studentAttendance")
                    .whereEqualTo("schoolId", schId)
                    .whereEqualTo("className", clsName)
                    .whereEqualTo("section", sec)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                        val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                        val items = snap.documents.mapNotNull { doc ->
                            StudentAttendanceSheet.fromMap(
                                docId = doc.id,
                                data = doc.data,
                                hasPendingWrites = doc.metadata.hasPendingWrites(),
                                isFromCache = doc.metadata.isFromCache()
                            )
                        }
                        processSheetConflicts(items, snap.metadata.hasPendingWrites(), currentUid)
                        Phase8Slice(
                            value = items,
                            pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                            isFromCache = snap.metadata.isFromCache()
                        )
                    }
            }
            combine(sectionFlows) { slices ->
                Phase8Slice(
                    value = slices.flatMap { it.value }.distinctBy { it.id },
                    pendingCount = slices.sumOf { it.pendingCount },
                    isFromCache = slices.all { it.isFromCache }
                )
            }
        } else {
            flowOf(Phase8Slice(emptyList(), 0, false))
        }

        val logsFlow: Flow<Phase8Slice<List<StudentAttendanceEditLog>>> = if (!canAccessAttendance) {
            flowOf(Phase8Slice(emptyList(), 0, false))
        } else if (OrgAccessEvaluator.canViewModule(member, AppModule.ATTENDANCE)) {
            orgRef.collection("studentAttendanceEditLogs")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                    val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                    val items = snap.documents.mapNotNull { doc ->
                        StudentAttendanceEditLog.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                    }.filter { log -> OrgAccessEvaluator.canAccessSchool(member, log.schoolId) }
                        .sortedByDescending { it.clientEditedAtMs }
                    Phase8Slice(
                        value = items,
                        pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                        isFromCache = snap.metadata.isFromCache()
                    )
                }
        } else if (ownSections.isNotEmpty()) {
            val logFlows = ownSections.map { (schId, clsName, sec) ->
                orgRef.collection("studentAttendanceEditLogs")
                    .whereEqualTo("schoolId", schId)
                    .whereEqualTo("className", clsName)
                    .whereEqualTo("section", sec)
                    .snapshots(MetadataChanges.INCLUDE)
                    .map { snap ->
                        val pCount = snap.documents.count { it.metadata.hasPendingWrites() }
                        val hasPending = snap.metadata.hasPendingWrites() || pCount > 0
                        val items = snap.documents.mapNotNull { doc ->
                            StudentAttendanceEditLog.fromMap(doc.id, doc.data, doc.metadata.hasPendingWrites())
                        }
                        Phase8Slice(
                            value = items,
                            pendingCount = if (hasPending) pCount.coerceAtLeast(1) else 0,
                            isFromCache = snap.metadata.isFromCache()
                        )
                    }
            }
            combine(logFlows) { slices ->
                Phase8Slice(
                    value = slices.flatMap { it.value }.distinctBy { it.id }.sortedByDescending { it.clientEditedAtMs },
                    pendingCount = slices.sumOf { it.pendingCount },
                    isFromCache = slices.all { it.isFromCache }
                )
            }
        } else {
            flowOf(Phase8Slice(emptyList(), 0, false))
        }

        return combine(calendarsFlow, sheetsFlow, logsFlow) { cals, sheets, logs ->
            CloudPhase8Snapshot(
                schoolCalendars = cals.value,
                studentAttendanceSheets = sheets.value,
                attendanceEditLogs = logs.value,
                pendingWriteCount = cals.pendingCount + sheets.pendingCount + logs.pendingCount,
                isFromCache = cals.isFromCache && sheets.isFromCache
            )
        }
    }

    private fun processSheetConflicts(
        items: List<StudentAttendanceSheet>,
        hasPendingWrites: Boolean,
        currentUid: String
    ) {
        if (hasPendingWrites) return
        for (sheet in items) {
            val tracked = recentLocalEdits[sheet.id] ?: continue
            if (sheet.updatedByUid.isNotBlank() &&
                sheet.updatedByUid != currentUid &&
                sheet.clientEditedAtMs != tracked.localEditedAtMs
            ) {
                recentLocalEdits.remove(sheet.id)
                val noticeId = "att_conf_${sheet.id}_${sheet.clientEditedAtMs}"
                if (_conflictNotices.value.none { it.id == noticeId }) {
                    _conflictNotices.value = (_conflictNotices.value + AttendanceConflictNotice(
                        id = noticeId,
                        sectionLabel = tracked.sectionLabel,
                        date = tracked.date,
                        winningEditorName = sheet.updatedByName.ifBlank { "Another user" },
                        detectedAtMs = System.currentTimeMillis()
                    )).takeLast(10)
                }
            } else if (sheet.updatedByUid == currentUid && sheet.clientEditedAtMs >= tracked.localEditedAtMs) {
                recentLocalEdits.remove(sheet.id)
            }
        }
    }

    // =========================================================================
    // 2. SAVE SCHOOL CALENDAR (OFFLINE-FIRST)
    // =========================================================================
    fun saveSchoolCalendar(
        orgId: String,
        calendar: SchoolCalendarConfig,
        existsInCloud: Boolean
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docId = calendar.id.takeIf { it.isNotBlank() }
            ?: SchoolCalendarConfig.makeDocId(calendar.schoolId, calendar.session)
        val path = "organisations/$orgId/schoolCalendars/$docId"
        return try {
            val ref = db.collection("organisations").document(orgId)
                .collection("schoolCalendars").document(docId)

            val runWrite = {
                if (existsInCloud) {
                    ref.update(
                        mapOf(
                            "workingDays" to calendar.workingDays.sortedBy { it.sortOrder }.map { it.wireValue },
                            "holidays" to calendar.holidays.take(120).map { it.toMap() },
                            "halfWorkingDays" to calendar.halfWorkingDays.take(60).map { it.toMap() },
                            "workingDayExceptions" to calendar.workingDayExceptions.take(60).map { it.toMap() },
                            "term1StartDate" to calendar.term1StartDate.trim().take(16),
                            "term1EndDate" to calendar.term1EndDate.trim().take(16),
                            "term2StartDate" to calendar.term2StartDate.trim().take(16),
                            "term2EndDate" to calendar.term2EndDate.trim().take(16),
                            "updatedByUid" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    ref.set(
                        calendar.toFirestoreMap(
                            actorUid = uid,
                            createdAtValue = FieldValue.serverTimestamp(),
                            updatedAtValue = FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            val task = runWrite()
            attachSyncListeners(task, OperationType.WRITE, path) { runWrite() }

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "SCHOOL_CALENDAR_SAVED",
                targetType = "schoolCalendar",
                targetId = docId,
                summary = "Updated calendar for session ${calendar.session} (${calendar.holidays.size} holiday(s), Term-I ${calendar.term1StartDate}..${calendar.term1EndDate})",
                schoolId = calendar.schoolId
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
    // 3. SAVE DAILY STUDENT ATTENDANCE SHEET + OPTIONAL EDIT LOG (OFFLINE-FIRST)
    // =========================================================================
    fun saveStudentAttendanceSheet(
        orgId: String,
        sheet: StudentAttendanceSheet,
        existsInCloud: Boolean,
        writeEditLog: Boolean,
        changes: List<StudentAttendanceChangeItem>,
        clientEditedAtMs: Long = System.currentTimeMillis()
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val editorName = orgRepo.currentEditorName().take(120)
        val docId = sheet.id.takeIf { it.isNotBlank() }
            ?: StudentAttendanceSheet.makeDocId(sheet.schoolId, sheet.session, sheet.className, sheet.section, sheet.date)
        val path = "organisations/$orgId/studentAttendance/$docId"
        val normalized = sheet.copy(id = docId).withRecomputedCounts()

        return try {
            val orgRef = db.collection("organisations").document(orgId)
            val sheetRef = orgRef.collection("studentAttendance").document(docId)
            val logId = "sattlog_${clientEditedAtMs}_${UUID.randomUUID().toString().take(6)}"
            val logRef = orgRef.collection("studentAttendanceEditLogs").document(logId)

            recentLocalEdits[docId] = LocalAttendanceEditTracker(
                docId = docId,
                sectionLabel = "Class ${normalized.className}-${normalized.section}",
                date = normalized.date,
                localEditedAtMs = clientEditedAtMs
            )

            val runWrite = {
                val batch = db.batch()
                if (existsInCloud) {
                    batch.update(
                        sheetRef,
                        mapOf(
                            "entries" to normalized.entries.mapValues { (_, v) -> v.toMap() },
                            "presentCount" to normalized.presentCount.toLong(),
                            "absentCount" to normalized.absentCount.toLong(),
                            "leaveCount" to normalized.leaveCount.toLong(),
                            "lateCount" to normalized.lateCount.toLong(),
                            "halfDayCount" to normalized.halfDayCount.toLong(),
                            "totalStudents" to normalized.totalStudents.toLong(),
                            "updatedByUid" to uid,
                            "updatedByName" to editorName,
                            "clientEditedAtMs" to clientEditedAtMs,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        sheetRef,
                        normalized.toFirestoreMap(
                            actorUid = uid,
                            actorName = editorName,
                            editedAtMs = clientEditedAtMs,
                            createdAtValue = FieldValue.serverTimestamp(),
                            updatedAtValue = FieldValue.serverTimestamp()
                        )
                    )
                }

                if (writeEditLog && changes.isNotEmpty()) {
                    val editLog = StudentAttendanceEditLog(
                        id = logId,
                        attendanceDocId = docId,
                        schoolId = normalized.schoolId,
                        session = normalized.session,
                        className = normalized.className,
                        section = normalized.section,
                        date = normalized.date,
                        editorUid = uid,
                        editorName = editorName,
                        summary = StudentAttendanceEngine.formatEditChangesSummary(changes),
                        changes = changes,
                        clientEditedAtMs = clientEditedAtMs
                    )
                    batch.set(logRef, editLog.toFirestoreMap(FieldValue.serverTimestamp()))
                }
                batch.commit()
            }

            val task = runWrite()
            attachSyncListeners(task, OperationType.WRITE, path) { runWrite() }

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = if (existsInCloud) "STUDENT_ATTENDANCE_UPDATED" else "STUDENT_ATTENDANCE_TAKEN",
                targetType = "studentAttendance",
                targetId = docId,
                summary = "Class ${normalized.className}-${normalized.section} (${normalized.date}): P=${normalized.presentCount + normalized.lateCount}, A=${normalized.absentCount}, L=${normalized.leaveCount}, H=${normalized.halfDayCount}",
                schoolId = normalized.schoolId
            )
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
