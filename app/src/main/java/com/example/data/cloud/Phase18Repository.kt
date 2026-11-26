package com.example.data.cloud

import com.example.domain.AppModule
import com.example.domain.BirthdayOccurrenceItem
import com.example.domain.BirthdayPersonType
import com.example.domain.BirthdayWishRecord
import com.example.domain.ClassDiaryEntry
import com.example.domain.ClassDiaryHistoryRecord
import com.example.domain.MemberAccessProfile
import com.example.domain.OrgAccessEvaluator
import com.example.domain.Phase18AccessEvaluator
import com.example.domain.Phase18DiaryAndBirthdayEngine
import com.example.domain.SchoolBirthdayConfig
import com.example.domain.TeacherAccessScope
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

data class CloudPhase18Snapshot(
    val diaryEntries: List<ClassDiaryEntry> = emptyList(),
    val diaryHistory: List<ClassDiaryHistoryRecord> = emptyList(),
    val birthdayConfigs: List<SchoolBirthdayConfig> = emptyList(),
    val birthdayWishes: List<BirthdayWishRecord> = emptyList(),
    val pendingWriteCount: Int = 0,
    val isFromCache: Boolean = false
) {
    val classDiaryEntries: List<ClassDiaryEntry> get() = diaryEntries
    val classDiaryHistory: List<ClassDiaryHistoryRecord> get() = diaryHistory
    val schoolBirthdayConfigs: List<SchoolBirthdayConfig> get() = birthdayConfigs
}

private data class Phase18Slice<T>(
    val value: T,
    val pendingCount: Int = 0,
    val isFromCache: Boolean = false
)

/**
 * Phase 18 Offline-First Repository:
 * - `classDiaryEntries`
 * - `classDiaryHistory` (append-only log of CREATED, EDITED, DELETED diary entries)
 * - `schoolBirthdayConfigs` (per-school birthday toggle, templates, excluded students/staff, staff DOB overrides)
 * - `birthdayWishes` (per-person per-celebration-date wished mark so nobody is wished twice)
 */
class Phase18Repository(
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
            _syncError.value = friendlyFirestoreErrorMessage(e, "Failed to sync Class Diary / Birthdays changes.")
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

    fun observePhase18Data(
        orgId: String,
        member: MemberAccessProfile?,
        teacherScope: TeacherAccessScope?
    ): Flow<CloudPhase18Snapshot> {
        if (orgId.isBlank() || member == null || !OrgAccessEvaluator.isActive(member)) {
            return flowOf(CloudPhase18Snapshot())
        }

        val canReadDiary = OrgAccessEvaluator.isAdmin(member) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.CLASS_DIARY) ||
            Phase18AccessEvaluator.hasAnyTeacherDiaryAssignment(member, teacherScope)

        val canReadBirthdays = OrgAccessEvaluator.isAdmin(member) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.STUDENTS) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.STUDENT_REGISTER) ||
            OrgAccessEvaluator.canViewModule(member, AppModule.TEACHERS) ||
            (teacherScope?.classTeacherSections?.isNotEmpty() == true)

        val diaryEntriesFlow: Flow<Phase18Slice<List<ClassDiaryEntry>>> = if (!canReadDiary) {
            flowOf(Phase18Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("classDiaryEntries")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase18Slice(
                        value = docs.mapNotNull { d ->
                            ClassDiaryEntry.fromMap(d.id, d.data, d.metadata.hasPendingWrites())
                        }.sortedWith(
                            compareByDescending<ClassDiaryEntry> { it.dateIso }
                                .thenBy { it.className }
                                .thenBy { it.section }
                                .thenBy { it.subjectName }
                        ),
                        pendingCount = docs.count { it.metadata.hasPendingWrites() },
                        isFromCache = snap.metadata.isFromCache
                    )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "organisations/$orgId/classDiaryEntries")
                    emit(Phase18Slice(emptyList()))
                }
        }

        val diaryHistoryFlow: Flow<Phase18Slice<List<ClassDiaryHistoryRecord>>> = if (!canReadDiary) {
            flowOf(Phase18Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("classDiaryHistory")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase18Slice(
                        value = docs.mapNotNull { d ->
                            ClassDiaryHistoryRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites())
                        }.sortedByDescending { it.timestampMs },
                        pendingCount = docs.count { it.metadata.hasPendingWrites() },
                        isFromCache = snap.metadata.isFromCache
                    )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "organisations/$orgId/classDiaryHistory")
                    emit(Phase18Slice(emptyList()))
                }
        }

        val birthdayConfigsFlow: Flow<Phase18Slice<List<SchoolBirthdayConfig>>> = if (!canReadBirthdays) {
            flowOf(Phase18Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("schoolBirthdayConfigs")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase18Slice(
                        value = docs.mapNotNull { d ->
                            SchoolBirthdayConfig.fromMap(d.id, d.data, d.metadata.hasPendingWrites())
                        },
                        pendingCount = docs.count { it.metadata.hasPendingWrites() },
                        isFromCache = snap.metadata.isFromCache
                    )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "organisations/$orgId/schoolBirthdayConfigs")
                    emit(Phase18Slice(emptyList()))
                }
        }

        val birthdayWishesFlow: Flow<Phase18Slice<List<BirthdayWishRecord>>> = if (!canReadBirthdays) {
            flowOf(Phase18Slice(emptyList()))
        } else {
            orgDoc(orgId).collection("birthdayWishes")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val docs = filterSchoolScopedDocs(snap.documents, member)
                    Phase18Slice(
                        value = docs.mapNotNull { d ->
                            BirthdayWishRecord.fromMap(d.id, d.data, d.metadata.hasPendingWrites())
                        }.sortedByDescending { it.wishedAtMs },
                        pendingCount = docs.count { it.metadata.hasPendingWrites() },
                        isFromCache = snap.metadata.isFromCache
                    )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "organisations/$orgId/birthdayWishes")
                    emit(Phase18Slice(emptyList()))
                }
        }

        return combine(
            diaryEntriesFlow,
            diaryHistoryFlow,
            birthdayConfigsFlow,
            birthdayWishesFlow
        ) { entries, history, configs, wishes ->
            CloudPhase18Snapshot(
                diaryEntries = entries.value,
                diaryHistory = history.value,
                birthdayConfigs = configs.value,
                birthdayWishes = wishes.value,
                pendingWriteCount = entries.pendingCount + history.pendingCount + configs.pendingCount + wishes.pendingCount,
                isFromCache = entries.isFromCache || history.isFromCache || configs.isFromCache || wishes.isFromCache
            )
        }
    }

    /**
     * Creates or updates a class diary entry for a subject on a date, keeping full revision & history records.
     */
    fun saveDiaryEntry(
        orgId: String,
        schoolId: String,
        session: String,
        dateIso: String,
        className: String,
        section: String,
        subjectName: String,
        classwork: String,
        homework: String,
        note: String,
        photoBase64: String,
        existingEntry: ClassDiaryEntry?,
        actor: MemberAccessProfile,
        actorName: String,
        teacherScope: TeacherAccessScope?
    ): Result<ClassDiaryEntry> {
        return try {
            val uid = requireUid()
            val effectiveActor = if (actor.uid == uid) actor else actor.copy(uid = uid)
            val nowMs = System.currentTimeMillis()

            val (savedEntry, historyRecord) = if (existingEntry != null) {
                Phase18DiaryAndBirthdayEngine.editDiaryEntryWithHistory(
                    existing = existingEntry,
                    newClasswork = classwork,
                    newHomework = homework,
                    newNote = note,
                    newPhotoBase64 = photoBase64,
                    actor = effectiveActor,
                    actorName = actorName,
                    teacherScope = teacherScope,
                    nowMs = nowMs
                ).getOrThrow()
            } else {
                Phase18DiaryAndBirthdayEngine.createDiaryEntryWithHistory(
                    schoolId = schoolId,
                    session = session,
                    dateIso = dateIso,
                    className = className,
                    section = section,
                    subjectName = subjectName,
                    classwork = classwork,
                    homework = homework,
                    note = note,
                    photoBase64 = photoBase64,
                    actor = effectiveActor,
                    actorName = actorName,
                    teacherScope = teacherScope,
                    nowMs = nowMs
                ).getOrThrow()
            }

            val entryRef = orgDoc(orgId).collection("classDiaryEntries").document(savedEntry.id)
            val histRef = orgDoc(orgId).collection("classDiaryHistory").document(historyRecord.id)

            val batch = db.batch()
            val ts = FieldValue.serverTimestamp()
            if (existingEntry != null) {
                // Update existing entry preserving immutable keys (schoolId, session, dateIso, className, section, subjectName, authorUid, authorName, createdAtMs, createdAt)
                batch.update(
                    entryRef,
                    mapOf(
                        "classwork" to savedEntry.classwork.take(1500),
                        "homework" to savedEntry.homework.take(1500),
                        "note" to savedEntry.note.take(800),
                        "photoBase64" to savedEntry.photoBase64.take(260_000),
                        "lastEditedByUid" to savedEntry.lastEditedByUid,
                        "lastEditedByName" to savedEntry.lastEditedByName.take(120),
                        "editHistory" to savedEntry.editHistory.takeLast(25).map { it.toMap() },
                        "updatedAtMs" to savedEntry.updatedAtMs,
                        "updatedAt" to ts
                    )
                )
            } else {
                batch.set(entryRef, savedEntry.toFirestoreMap(ts, ts))
            }
            batch.set(histRef, historyRecord.toFirestoreMap(ts))

            val path = "organisations/$orgId/classDiaryEntries/${savedEntry.id}"
            val task = batch.commit()
            attachSyncListeners(task, if (existingEntry != null) OperationType.UPDATE else OperationType.CREATE, path)

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = if (existingEntry != null) "CLASS_DIARY_EDITED" else "CLASS_DIARY_CREATED",
                targetType = "classDiary",
                targetId = savedEntry.id,
                summary = "${if (existingEntry != null) "Edited" else "Added"} diary for ${savedEntry.className}-${savedEntry.section} (${savedEntry.subjectName}) on ${savedEntry.dateIso}",
                schoolId = schoolId
            )
            Result.success(savedEntry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes an existing diary entry (only allowed for original author or Admin)
     * while writing a permanent DELETED record to `classDiaryHistory`.
     */
    fun deleteDiaryEntry(
        orgId: String,
        existingEntry: ClassDiaryEntry,
        actor: MemberAccessProfile,
        actorName: String,
        teacherScope: TeacherAccessScope?
    ): Result<Unit> {
        return try {
            val uid = requireUid()
            val effectiveActor = if (actor.uid == uid) actor else actor.copy(uid = uid)
            val nowMs = System.currentTimeMillis()
            val historyRecord = Phase18DiaryAndBirthdayEngine.deleteDiaryEntryWithHistory(
                existing = existingEntry,
                actor = effectiveActor,
                actorName = actorName,
                teacherScope = teacherScope,
                nowMs = nowMs
            ).getOrThrow()

            val entryRef = orgDoc(orgId).collection("classDiaryEntries").document(existingEntry.id)
            val histRef = orgDoc(orgId).collection("classDiaryHistory").document(historyRecord.id)

            val batch = db.batch()
            batch.delete(entryRef)
            batch.set(histRef, historyRecord.toFirestoreMap(FieldValue.serverTimestamp()))

            val path = "organisations/$orgId/classDiaryEntries/${existingEntry.id}"
            attachSyncListeners(batch.commit(), OperationType.DELETE, path)

            orgRepo.logAuditNonBlocking(
                orgId = orgId,
                action = "CLASS_DIARY_DELETED",
                targetType = "classDiary",
                targetId = existingEntry.id,
                summary = "Deleted diary for ${existingEntry.className}-${existingEntry.section} (${existingEntry.subjectName}) on ${existingEntry.dateIso}",
                schoolId = existingEntry.schoolId
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves per-school birthday settings (on/off toggle, wish templates, excluded students/staff, staff DOB map).
     */
    fun saveSchoolBirthdayConfig(
        orgId: String,
        config: SchoolBirthdayConfig,
        actor: MemberAccessProfile,
        actorName: String
    ): Result<SchoolBirthdayConfig> {
        return try {
            val uid = requireUid()
            if (!Phase18AccessEvaluator.canManageBirthdaySettings(actor, config.schoolId)) {
                return Result.failure(IllegalAccessException("You do not have permission to change birthday settings for this school."))
            }
            val nowMs = System.currentTimeMillis()
            val cleanConfig = config.copy(
                updatedByUid = uid,
                updatedByName = actorName.trim().ifBlank { "Admin" },
                updatedAtMs = nowMs
            )
            val ref = orgDoc(orgId).collection("schoolBirthdayConfigs").document(cleanConfig.schoolId)
            val payload = cleanConfig.toFirestoreMap(FieldValue.serverTimestamp())
            val path = "organisations/$orgId/schoolBirthdayConfigs/${cleanConfig.schoolId}"
            attachSyncListeners(ref.set(payload), OperationType.WRITE, path) { ref.set(payload) }
            Result.success(cleanConfig)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Marks a student or staff member as "Wished" for their celebration date so nobody is wished twice.
     */
    fun markPersonWished(
        orgId: String,
        schoolId: String,
        item: BirthdayOccurrenceItem,
        channel: String,
        actor: MemberAccessProfile,
        actorName: String,
        teacherScope: TeacherAccessScope?
    ): Result<BirthdayWishRecord> {
        return try {
            val uid = requireUid()
            if (!Phase18AccessEvaluator.canViewBirthdays(actor, teacherScope, schoolId)) {
                return Result.failure(IllegalAccessException("You do not have access to birthdays for this school."))
            }
            val nowMs = System.currentTimeMillis()
            val wishId = Phase18DiaryAndBirthdayEngine.makeBirthdayWishDocId(
                schoolId = schoolId,
                personType = item.personType,
                personId = item.personId,
                celebrationDateIso = item.celebrationDateIso
            )
            val record = BirthdayWishRecord(
                id = wishId,
                schoolId = schoolId,
                personType = item.personType,
                personId = item.personId,
                personName = item.name,
                className = item.className,
                section = item.section,
                celebrationDateIso = item.celebrationDateIso,
                wishedByUid = uid,
                wishedByName = actorName.trim().ifBlank { "Staff" },
                channel = channel.trim().ifBlank { "WHATSAPP" },
                wishedAtMs = nowMs
            )
            val ref = orgDoc(orgId).collection("birthdayWishes").document(wishId)
            val ts = FieldValue.serverTimestamp()
            val payload = record.toFirestoreMap(ts, ts)
            val path = "organisations/$orgId/birthdayWishes/$wishId"
            attachSyncListeners(ref.set(payload), OperationType.WRITE, path) { ref.set(payload) }
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun unmarkPersonWished(
        orgId: String,
        schoolId: String,
        wishId: String,
        actor: MemberAccessProfile,
        teacherScope: TeacherAccessScope?
    ): Result<Unit> {
        return try {
            requireUid()
            if (!Phase18AccessEvaluator.canViewBirthdays(actor, teacherScope, schoolId)) {
                return Result.failure(IllegalAccessException("You do not have access to birthdays for this school."))
            }
            val ref = orgDoc(orgId).collection("birthdayWishes").document(wishId)
            val path = "organisations/$orgId/birthdayWishes/$wishId"
            attachSyncListeners(ref.delete(), OperationType.DELETE, path) { ref.delete() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
