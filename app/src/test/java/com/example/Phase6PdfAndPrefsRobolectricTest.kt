package com.example

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.SchoolEntity
import com.example.data.cloud.OfflinePrefsStore
import com.example.domain.DayOfWeekCode
import com.example.domain.LeaveReason
import com.example.domain.LocalPendingPhase6Edit
import com.example.domain.SubstitutionNeedItem
import com.example.domain.SubstitutionRecord
import com.example.util.SubstitutionPdfGenerator
import com.example.util.SubstitutionSlipPrintBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Phase6PdfAndPrefsRobolectricTest {

    private fun countNonWhitePixels(bmp: Bitmap): Int {
        var count = 0
        for (y in 0 until bmp.height step 4) {
            for (x in 0 until bmp.width step 4) {
                if (bmp.getPixel(x, y) != Color.WHITE) count++
            }
        }
        return count
    }

    @Test
    fun substitutionSlipPdfGeneratesValidA4PdfWithMultipleRows() {
        val school = SchoolEntity(
            id = 1L,
            name = "Sunrise Public Academy",
            address = "12 Civil Lines, Lucknow",
            mobile = "9876543210",
            email = "info@sunrise.edu",
            active = true
        )

        val assignedSub = SubstitutionRecord(
            id = "sub_1",
            date = "2026-11-23",
            day = DayOfWeekCode.MON,
            schoolId = "sch_1",
            session = "2026-2027",
            className = "5th",
            section = "A",
            slotId = "MON_p1",
            slotLabel = "Period 1",
            startMinutes = 480,
            endMinutes = 520,
            subjectName = "MATHEMATICS",
            absentTeacherId = "t1",
            absentTeacherName = "Mr. Sharma",
            leaveId = "lv1",
            substituteTeacherId = "t2",
            substituteTeacherName = "Ms. Verma",
            overrideUsed = false
        )
        val overrideSub = assignedSub.copy(
            id = "sub_2",
            slotId = "MON_p2",
            slotLabel = "Period 2",
            startMinutes = 520,
            endMinutes = 560,
            className = "6th",
            subjectName = "SCIENCE",
            substituteTeacherName = "Mr. Khan",
            overrideUsed = true,
            overrideReason = "At daily limit"
        )

        val items = listOf(
            SubstitutionNeedItem(
                date = "2026-11-23",
                day = DayOfWeekCode.MON,
                schoolId = "sch_1",
                schoolName = school.name,
                session = "2026-2027",
                className = "5th",
                section = "A",
                slotId = "MON_p1",
                slotNumber = 1,
                slotLabel = "Period 1",
                startMinutes = 480,
                endMinutes = 520,
                subjectName = "MATHEMATICS",
                absentTeacherId = "t1",
                absentTeacherName = "Mr. Sharma",
                leaveId = "lv1",
                leaveReason = LeaveReason.SICK,
                existingSubstitution = assignedSub,
                assignedSubstituteAlsoAbsent = false,
                assignedSubstituteInactive = false,
                needsSubstitute = false,
                statusNotice = "Assigned",
                recommendedCandidates = emptyList(),
                manualOverrideCandidates = emptyList()
            ),
            SubstitutionNeedItem(
                date = "2026-11-23",
                day = DayOfWeekCode.MON,
                schoolId = "sch_1",
                schoolName = school.name,
                session = "2026-2027",
                className = "6th",
                section = "A",
                slotId = "MON_p2",
                slotNumber = 2,
                slotLabel = "Period 2",
                startMinutes = 520,
                endMinutes = 560,
                subjectName = "SCIENCE",
                absentTeacherId = "t1",
                absentTeacherName = "Mr. Sharma",
                leaveId = "lv1",
                leaveReason = LeaveReason.SICK,
                existingSubstitution = overrideSub,
                assignedSubstituteAlsoAbsent = false,
                assignedSubstituteInactive = false,
                needsSubstitute = false,
                statusNotice = "Override",
                recommendedCandidates = emptyList(),
                manualOverrideCandidates = emptyList()
            ),
            SubstitutionNeedItem(
                date = "2026-11-23",
                day = DayOfWeekCode.MON,
                schoolId = "sch_1",
                schoolName = school.name,
                session = "2026-2027",
                className = "7th",
                section = "B",
                slotId = "MON_p3",
                slotNumber = 3,
                slotLabel = "Period 3",
                startMinutes = 560,
                endMinutes = 600,
                subjectName = "ENGLISH",
                absentTeacherId = "t1",
                absentTeacherName = "Mr. Sharma",
                leaveId = "lv1",
                leaveReason = LeaveReason.SICK,
                existingSubstitution = null,
                assignedSubstituteAlsoAbsent = false,
                assignedSubstituteInactive = false,
                needsSubstitute = true,
                statusNotice = "Needs substitute",
                recommendedCandidates = emptyList(),
                manualOverrideCandidates = emptyList()
            )
        )

        // 1. Verify single-page A4 Portrait rendering (3 rows: assigned, override, pending)
        val pages = SubstitutionPdfGenerator.renderSlipPagesToBitmapsForVerification(
            bundle = SubstitutionSlipPrintBundle(
                school = school,
                session = "2026-2027",
                date = "2026-11-23",
                items = items
            )
        )
        assertEquals(1, pages.size)
        assertEquals(SubstitutionPdfGenerator.PAGE_W, pages[0].width)
        assertEquals(SubstitutionPdfGenerator.PAGE_H, pages[0].height)
        assertTrue("Slip page must draw visible header & table pixels", countNonWhitePixels(pages[0]) > 500)
        pages.forEach { it.recycle() }

        // 2. Verify multi-page A4 Portrait pagination (18 rows -> 2 pages at 16 rows/page)
        val manyItems = (1..18).map { i ->
            items[i % items.size].copy(slotId = "MON_p$i", slotNumber = i, slotLabel = "Period $i")
        }
        val multiPages = SubstitutionPdfGenerator.renderSlipPagesToBitmapsForVerification(
            bundle = SubstitutionSlipPrintBundle(
                school = school,
                session = "2026-2027",
                date = "2026-11-23",
                items = manyItems
            )
        )
        assertEquals(2, multiPages.size)
        multiPages.forEach { bmp ->
            assertEquals(SubstitutionPdfGenerator.PAGE_W, bmp.width)
            assertEquals(SubstitutionPdfGenerator.PAGE_H, bmp.height)
            assertTrue("Multi-page slip must draw visible pixels", countNonWhitePixels(bmp) > 400)
            bmp.recycle()
        }
    }

    @Test
    fun offlinePrefsPersistsPhase6PendingEditsAcrossReloads() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val prefs = OfflinePrefsStore(context)
        prefs.clearAll()

        val edit = LocalPendingPhase6Edit(
            kind = "LEAVE",
            docId = "lv_101",
            targetSummary = "Leave: Ms. Verma (2026-11-23..2026-11-23)",
            localValueSummary = "Approved (2026-11-23..2026-11-23)",
            wasDeleted = false,
            clientEditedAtMs = 123456789L
        )
        prefs.upsertPendingPhase6Edit("uid_1", "org_1", edit)

        val reloaded = OfflinePrefsStore(context).loadPendingPhase6Edits("uid_1", "org_1")
        assertEquals(1, reloaded.size)
        assertEquals("lv_101", reloaded.first().docId)
        assertEquals("LEAVE", reloaded.first().kind)

        prefs.clearPendingPhase6EditsForOrg("uid_1", "org_1")
        assertTrue(prefs.loadPendingPhase6Edits("uid_1", "org_1").isEmpty())
    }
}
