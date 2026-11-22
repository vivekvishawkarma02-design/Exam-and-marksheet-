package com.example

import com.example.domain.AccessLevel
import com.example.domain.AppModule
import com.example.domain.LocalPendingMarkEdit
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OfflineSyncEngine
import com.example.domain.OrgRole
import com.example.domain.ServerMarkObservation
import com.example.domain.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSyncEngineTest {

    @Test
    fun cacheSize_isAtLeast100MbAndExplicitly150Mb() {
        assertEquals(150, OfflineSyncEngine.FIRESTORE_CACHE_SIZE_MB)
        assertEquals(150L * 1024L * 1024L, OfflineSyncEngine.FIRESTORE_CACHE_SIZE_BYTES)
        assertTrue(OfflineSyncEngine.FIRESTORE_CACHE_SIZE_BYTES >= 100L * 1024L * 1024L)
    }

    @Test
    fun computeSyncUiState_handlesOnlineOfflineSyncingAndErrorPriority() {
        val online = OfflineSyncEngine.computeSyncUiState(
            isOnline = true,
            pendingCount = 0,
            syncErrorMessage = null
        )
        assertEquals(SyncStatus.ONLINE, online.status)
        assertEquals("Online", online.chipLabel)
        assertFalse(online.isActionable)

        val offlineEmpty = OfflineSyncEngine.computeSyncUiState(
            isOnline = false,
            pendingCount = 0,
            syncErrorMessage = null
        )
        assertEquals(SyncStatus.OFFLINE, offlineEmpty.status)
        assertEquals("Offline • Saved on phone", offlineEmpty.chipLabel)
        assertTrue(offlineEmpty.isActionable)

        val offlinePending = OfflineSyncEngine.computeSyncUiState(
            isOnline = false,
            pendingCount = 4,
            syncErrorMessage = null
        )
        assertEquals(SyncStatus.OFFLINE, offlinePending.status)
        assertEquals("Offline • 4 saved", offlinePending.chipLabel)

        val syncing = OfflineSyncEngine.computeSyncUiState(
            isOnline = true,
            pendingCount = 3,
            syncErrorMessage = null
        )
        assertEquals(SyncStatus.SYNCING, syncing.status)
        assertEquals("Syncing (3)", syncing.chipLabel)

        val erroredWhileOnline = OfflineSyncEngine.computeSyncUiState(
            isOnline = true,
            pendingCount = 2,
            syncErrorMessage = "Permission denied"
        )
        assertEquals(SyncStatus.ERROR, erroredWhileOnline.status)
        assertEquals("Sync error", erroredWhileOnline.chipLabel)
        assertEquals("Permission denied", erroredWhileOnline.errorMessage)
    }

    @Test
    fun detectMarkConflicts_notifiesLosingOfflineEditorWhenNewerEditWins() {
        val pending = listOf(
            LocalPendingMarkEdit(
                orgId = "org1",
                schoolDocId = "sch1",
                markDocId = "stu1_mathematics_hy_exam",
                studentDocId = "stu1",
                studentName = "Aarav Sharma",
                className = "5th",
                subjectName = "Mathematics",
                componentKey = "hy_exam",
                localValue = "48",
                clientEditedAtMs = 1_000L
            )
        )
        val server = listOf(
            ServerMarkObservation(
                markDocId = "stu1_mathematics_hy_exam",
                studentDocId = "stu1",
                rawValue = "54",
                updatedByUid = "teacher_b",
                updatedByName = "Ms. Kavita",
                clientEditedAtMs = 2_000L,
                hasPendingWrites = false,
                isFromCache = false
            )
        )

        val result = OfflineSyncEngine.detectMarkConflicts(
            pendingEdits = pending,
            serverMarks = server,
            currentUid = "teacher_a",
            nowMs = 3_000L
        )

        assertTrue(result.remainingPending.isEmpty())
        assertEquals(1, result.detectedConflicts.size)
        val notice = result.detectedConflicts.first()
        assertEquals("48", notice.yourValue)
        assertEquals("54", notice.winningValue)
        assertEquals("Ms. Kavita", notice.winnerName)
        assertEquals("Aarav Sharma", notice.studentName)
    }

    @Test
    fun detectMarkConflicts_ignoresCachedOrPendingSnapshotsAndClearsWhenOwnEditWins() {
        val edit = LocalPendingMarkEdit(
            orgId = "org1",
            schoolDocId = "sch1",
            markDocId = "stu1_hindi_hy_ut",
            studentDocId = "stu1",
            studentName = "Diya Verma",
            className = "5th",
            subjectName = "Hindi",
            componentKey = "hy_ut",
            localValue = "26",
            clientEditedAtMs = 1_000L
        )

        // While offline / from cache with pending writes, must NOT report a false conflict
        val cachedSnapshot = OfflineSyncEngine.detectMarkConflicts(
            pendingEdits = listOf(edit),
            serverMarks = listOf(
                ServerMarkObservation(
                    markDocId = edit.markDocId,
                    studentDocId = edit.studentDocId,
                    rawValue = "20",
                    updatedByUid = "teacher_b",
                    updatedByName = "Other",
                    clientEditedAtMs = 900L,
                    hasPendingWrites = true,
                    isFromCache = true
                )
            ),
            currentUid = "teacher_a",
            nowMs = 2_000L
        )
        assertTrue(cachedSnapshot.detectedConflicts.isEmpty())
        assertEquals(1, cachedSnapshot.remainingPending.size)

        // When our own confirmed edit lands on server, clears silently with zero conflicts
        val confirmedOwn = OfflineSyncEngine.detectMarkConflicts(
            pendingEdits = listOf(edit),
            serverMarks = listOf(
                ServerMarkObservation(
                    markDocId = edit.markDocId,
                    studentDocId = edit.studentDocId,
                    rawValue = "26",
                    updatedByUid = "teacher_a",
                    updatedByName = "Teacher A",
                    clientEditedAtMs = 1_000L,
                    hasPendingWrites = false,
                    isFromCache = false
                )
            ),
            currentUid = "teacher_a",
            nowMs = 2_500L
        )
        assertTrue(confirmedOwn.detectedConflicts.isEmpty())
        assertTrue(confirmedOwn.remainingPending.isEmpty())
    }

    @Test
    fun detectAccessReduction_catchesRevocationDemotionRemovedSchoolsAndLoweredPermissions() {
        val admin = MemberAccessProfile(
            uid = "u1",
            role = OrgRole.ADMIN,
            status = MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions.presetFor(OrgRole.ADMIN)
        )
        val teacherTwoSchools = MemberAccessProfile(
            uid = "u1",
            role = OrgRole.TEACHER,
            status = MemberStatus.ACTIVE,
            allSchools = false,
            allowedSchoolIds = listOf("sch_a", "sch_b"),
            permissions = MemberPermissions.presetFor(OrgRole.TEACHER)
        )
        val teacherOneSchool = teacherTwoSchools.copy(allowedSchoolIds = listOf("sch_a"))
        val teacherMarksViewOnly = teacherTwoSchools.copy(
            permissions = teacherTwoSchools.permissions.withModule(AppModule.MARKS, AccessLevel.VIEW)
        )
        val teacherNarrowedSubjects = teacherTwoSchools.copy(
            assignedClassSubjects = listOf("5th|Mathematics")
        )
        val teacherExpandedSchools = teacherOneSchool.copy(allowedSchoolIds = listOf("sch_a", "sch_b"))

        // Revocation (null or REMOVED) -> must wipe cache
        assertTrue(OfflineSyncEngine.detectAccessReduction(teacherTwoSchools, null))
        assertTrue(
            OfflineSyncEngine.detectAccessReduction(
                teacherTwoSchools,
                teacherTwoSchools.copy(status = MemberStatus.REMOVED)
            )
        )
        // Admin demoted to Teacher -> must wipe cache
        assertTrue(OfflineSyncEngine.detectAccessReduction(admin, teacherTwoSchools))
        // School removed from allowed list -> must wipe cache
        assertTrue(OfflineSyncEngine.detectAccessReduction(teacherTwoSchools, teacherOneSchool))
        // Module lowered (Marks EDIT -> VIEW) -> must wipe cache
        assertTrue(OfflineSyncEngine.detectAccessReduction(teacherTwoSchools, teacherMarksViewOnly))
        // Teacher scope narrowed from all subjects -> specific subject -> must wipe cache
        assertTrue(OfflineSyncEngine.detectAccessReduction(teacherTwoSchools, teacherNarrowedSubjects))

        // Equal or expanded access -> must NOT wipe cache
        assertFalse(OfflineSyncEngine.detectAccessReduction(null, teacherTwoSchools))
        assertFalse(OfflineSyncEngine.detectAccessReduction(teacherTwoSchools, teacherTwoSchools))
        assertFalse(OfflineSyncEngine.detectAccessReduction(teacherOneSchool, teacherExpandedSchools))
        assertFalse(OfflineSyncEngine.detectAccessReduction(teacherTwoSchools, admin))
    }

    @Test
    fun deterministicBackupIds_preventDuplicatesAcrossOfflineRestoreAndSync() {
        val sch1 = OfflineSyncEngine.deterministicBackupSchoolDocId("Sunrise Public School", 1L)
        val sch2 = OfflineSyncEngine.deterministicBackupSchoolDocId("  sunrise public school  ", 99L)
        assertEquals(sch1, sch2)
        assertEquals("bk_sch_sunrise_public_school", sch1)

        val stu1 = OfflineSyncEngine.deterministicBackupStudentDocId("2026-2027", "ADM/2026/001", 10L)
        val stu2 = OfflineSyncEngine.deterministicBackupStudentDocId("2026-2027", "adm/2026/001", 55L)
        assertEquals(stu1, stu2)
        assertEquals("bk_stu_2026_2027_adm_2026_001", stu1)
    }
}
