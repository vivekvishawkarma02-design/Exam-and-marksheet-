package com.example.domain

/**
 * Phase 3: Offline-First Sync State, Latest-Edit-Wins Conflict Detection,
 * Access-Reduction Cache Invalidation, and Deterministic Offline Backup IDs.
 *
 * Pure Kotlin domain logic with zero Android/Firebase dependencies for direct JUnit testing.
 */

data class SyncUiState(
    val status: SyncStatus = SyncStatus.ONLINE,
    val pendingCount: Int = 0,
    val errorMessage: String? = null,
    val lastSyncedAtMs: Long = 0L
) {
    val chipLabel: String
        get() = when (status) {
            SyncStatus.ERROR -> "Sync error"
            SyncStatus.OFFLINE -> if (pendingCount > 0) {
                "Offline • $pendingCount saved"
            } else {
                "Offline • Saved on phone"
            }
            SyncStatus.SYNCING -> if (pendingCount > 0) {
                "Syncing ($pendingCount)"
            } else {
                "Syncing…"
            }
            SyncStatus.ONLINE -> "Online"
        }

    val isActionable: Boolean
        get() = status == SyncStatus.ERROR || pendingCount > 0 || status == SyncStatus.OFFLINE
}

/**
 * Tracks a mark edit made on this phone while offline (or before server acknowledgement)
 * so we can detect if another phone's newer edit overwrote our value upon reconnection.
 */
data class LocalPendingMarkEdit(
    val orgId: String,
    val schoolDocId: String,
    val markDocId: String,
    val studentDocId: String,
    val studentName: String,
    val className: String,
    val subjectName: String,
    val componentKey: String,
    val localValue: String,
    val clientEditedAtMs: Long
)

data class ServerMarkObservation(
    val markDocId: String,
    val studentDocId: String,
    val rawValue: String,
    val updatedByUid: String,
    val updatedByName: String,
    val clientEditedAtMs: Long,
    val hasPendingWrites: Boolean,
    val isFromCache: Boolean
)

data class MarkConflictNotice(
    val markDocId: String,
    val studentDocId: String,
    val studentName: String,
    val className: String,
    val subjectName: String,
    val componentKey: String,
    val yourValue: String,
    val winningValue: String,
    val winnerName: String,
    val detectedAtMs: Long
)

data class ConflictDetectionResult(
    val remainingPending: List<LocalPendingMarkEdit>,
    val detectedConflicts: List<MarkConflictNotice>
)

object OfflineSyncEngine {
    /**
     * Explicit Firestore local disk cache size chosen for Phase 3: 150 MB.
     * Large enough to hold multi-school rosters, marks, logos (<150 KB each),
     * and student photos comfortably across multiple academic sessions offline.
     */
    const val FIRESTORE_CACHE_SIZE_BYTES: Long = 150L * 1024L * 1024L
    const val FIRESTORE_CACHE_SIZE_MB: Int = 150

    private const val STALE_PENDING_TTL_MS = 7L * 24L * 60L * 60L * 1000L

    fun computeSyncUiState(
        isOnline: Boolean,
        pendingCount: Int,
        syncErrorMessage: String?,
        lastSyncedAtMs: Long = 0L
    ): SyncUiState {
        val safePending = pendingCount.coerceAtLeast(0)
        val cleanError = syncErrorMessage?.trim()?.takeIf { it.isNotEmpty() }
        val status = when {
            cleanError != null -> SyncStatus.ERROR
            !isOnline -> SyncStatus.OFFLINE
            safePending > 0 -> SyncStatus.SYNCING
            else -> SyncStatus.ONLINE
        }
        return SyncUiState(
            status = status,
            pendingCount = safePending,
            errorMessage = cleanError,
            lastSyncedAtMs = lastSyncedAtMs
        )
    }

    /**
     * Latest-edit-wins conflict detector:
     * When a confirmed server snapshot arrives (!hasPendingWrites && !isFromCache) for a mark
     * this phone edited locally, checks whether:
     * - Our edit won (same updatedByUid or same rawValue) -> clear from pending silently.
     * - Another phone's edit won (different updatedByUid AND different rawValue) -> record a
     *   MarkConflictNotice so the losing phone sees a subtle banner with a link to the edit log.
     */
    fun detectMarkConflicts(
        pendingEdits: List<LocalPendingMarkEdit>,
        serverMarks: List<ServerMarkObservation>,
        currentUid: String,
        nowMs: Long = System.currentTimeMillis()
    ): ConflictDetectionResult {
        if (pendingEdits.isEmpty()) {
            return ConflictDetectionResult(emptyList(), emptyList())
        }
        val serverById = serverMarks.associateBy { it.markDocId }
        val remaining = mutableListOf<LocalPendingMarkEdit>()
        val conflicts = mutableListOf<MarkConflictNotice>()

        for (edit in pendingEdits) {
            if (nowMs - edit.clientEditedAtMs > STALE_PENDING_TTL_MS) {
                continue
            }
            val obs = serverById[edit.markDocId]
            if (obs == null) {
                // If local value was blank (delete) and server doc is gone on a confirmed snapshot, resolved!
                val anyConfirmedServerSnap = serverMarks.any { !it.isFromCache && !it.hasPendingWrites }
                if (edit.localValue.isBlank() && anyConfirmedServerSnap) {
                    continue
                }
                remaining.add(edit)
                continue
            }
            // Wait until the doc is confirmed by the server (not from local cache and no pending writes)
            if (obs.isFromCache || obs.hasPendingWrites) {
                remaining.add(edit)
                continue
            }

            val sameValue = obs.rawValue.trim().equals(edit.localValue.trim(), ignoreCase = true)
            val updatedBySelf = obs.updatedByUid == currentUid || obs.updatedByUid.isBlank()

            if (sameValue || updatedBySelf) {
                // Our edit succeeded (or converged to the exact same value) -> resolved cleanly
                continue
            }

            // Another user's edit is now the winning server value -> notify this user
            conflicts.add(
                MarkConflictNotice(
                    markDocId = edit.markDocId,
                    studentDocId = edit.studentDocId,
                    studentName = edit.studentName,
                    className = edit.className,
                    subjectName = edit.subjectName,
                    componentKey = edit.componentKey,
                    yourValue = edit.localValue.ifBlank { "(cleared)" },
                    winningValue = obs.rawValue.ifBlank { "(cleared)" },
                    winnerName = obs.updatedByName.ifBlank { "Another staff member" },
                    detectedAtMs = nowMs
                )
            )
        }
        return ConflictDetectionResult(
            remainingPending = remaining,
            detectedConflicts = conflicts
        )
    }

    /**
     * Returns true if a member's access was revoked OR reduced (demoted from admin,
     * removed from a school, lowered module permission, or narrowed teacher class-subject scope).
     * Triggers clearing Firestore's local offline cache so cached records for removed access
     * are wiped immediately when the phone reconnects.
     */
    fun detectAccessReduction(
        previous: MemberAccessProfile?,
        current: MemberAccessProfile?
    ): Boolean {
        if (previous == null) return false
        if (current == null || current.status != MemberStatus.ACTIVE) return true

        val wasAdmin = previous.role == OrgRole.OWNER || previous.role == OrgRole.ADMIN
        val isAdmin = current.role == OrgRole.OWNER || current.role == OrgRole.ADMIN
        if (wasAdmin && !isAdmin) return true

        val hadAllSchools = wasAdmin || previous.allSchools
        val hasAllSchools = isAdmin || current.allSchools
        if (hadAllSchools && !hasAllSchools) return true
        if (!hadAllSchools && !hasAllSchools) {
            val currentSet = current.allowedSchoolIds.toSet()
            if (previous.allowedSchoolIds.any { it !in currentSet }) return true
        }

        for (module in AppModule.entries) {
            val prevRank = effectiveModuleRank(previous, module)
            val currRank = effectiveModuleRank(current, module)
            if (currRank < prevRank) return true
        }

        if (previous.role == OrgRole.TEACHER && current.role == OrgRole.TEACHER) {
            if (previous.assignedClassSubjects.isEmpty() && current.assignedClassSubjects.isNotEmpty()) {
                return true
            }
            if (previous.assignedClassSubjects.isNotEmpty()) {
                val currScopes = current.assignedClassSubjects.map { it.lowercase() }.toSet()
                if (previous.assignedClassSubjects.any { it.lowercase() !in currScopes }) {
                    return true
                }
            }
        }
        return false
    }

    private fun effectiveModuleRank(profile: MemberAccessProfile, module: AppModule): Int {
        if (profile.status != MemberStatus.ACTIVE) return 0
        if (profile.role == OrgRole.OWNER || profile.role == OrgRole.ADMIN) return 2
        return when (profile.permissions.levelFor(module)) {
            AccessLevel.NONE -> 0
            AccessLevel.VIEW -> 1
            AccessLevel.EDIT -> 2
        }
    }

    /**
     * Deterministic school doc ID for JSON backup restore so restoring offline and later
     * syncing (or restoring twice) merges into the exact same document with zero duplicates.
     */
    fun deterministicBackupSchoolDocId(schoolName: String, fallbackLocalId: Long): String {
        val slug = sanitizeSlug(schoolName, maxLen = 40)
        return if (slug.isNotEmpty()) "bk_sch_$slug" else "bk_sch_${fallbackLocalId.coerceAtLeast(1L)}"
    }

    /**
     * Deterministic student doc ID scoped by session + admission number so offline JSON
     * restore & sync never creates duplicate student records.
     */
    fun deterministicBackupStudentDocId(
        session: String,
        admNo: String,
        fallbackLocalId: Long
    ): String {
        val sessSlug = sanitizeSlug(session, maxLen = 20)
        val admSlug = sanitizeSlug(admNo, maxLen = 40)
        return if (admSlug.isNotEmpty()) {
            "bk_stu_${sessSlug.ifEmpty { "s" }}_$admSlug"
        } else {
            "bk_stu_${fallbackLocalId.coerceAtLeast(1L)}"
        }
    }

    private fun sanitizeSlug(raw: String, maxLen: Int): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(maxLen)
}
