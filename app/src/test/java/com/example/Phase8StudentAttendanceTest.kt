package com.example

import com.example.data.StudentEntity
import com.example.data.TermInfoEntity
import com.example.domain.AccessLevel
import com.example.domain.AppModule
import com.example.domain.CalendarHoliday
import com.example.domain.DayCalendarStatus
import com.example.domain.DayOfWeekCode
import com.example.domain.HalfWorkingDayEntry
import com.example.domain.HolidayKind
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.OrgRole
import com.example.domain.SchoolCalendarConfig
import com.example.domain.SchoolCalendarEngine
import com.example.domain.StudentAttendanceAccessEvaluator
import com.example.domain.StudentAttendanceEngine
import com.example.domain.StudentAttendanceEntry
import com.example.domain.StudentAttendanceSheet
import com.example.domain.StudentAttendanceStatus
import com.example.domain.TeacherAccessScope
import com.example.domain.WorkingDayExceptionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase8StudentAttendanceTest {

    private fun baseCalendar(): SchoolCalendarConfig = SchoolCalendarConfig(
        id = "cal_sch1_2026_2027",
        schoolId = "sch1",
        session = "2026-2027",
        workingDays = listOf(
            DayOfWeekCode.MON,
            DayOfWeekCode.TUE,
            DayOfWeekCode.WED,
            DayOfWeekCode.THU,
            DayOfWeekCode.FRI,
            DayOfWeekCode.SAT
        ),
        holidays = listOf(
            CalendarHoliday("h1", "Independence Day", "2026-08-15", "2026-08-15", HolidayKind.HOLIDAY),
            CalendarHoliday("h2", "Diwali Break", "2026-11-08", "2026-11-11", HolidayKind.HOLIDAY),
            CalendarHoliday("h3", "Heavy Rain Off", "2026-08-18", "2026-08-18", HolidayKind.DECLARED_OFF)
        ),
        halfWorkingDays = listOf(
            HalfWorkingDayEntry("hd1", "2026-08-14", "PTM Half Day")
        ),
        workingDayExceptions = listOf(
            WorkingDayExceptionEntry("wex1", "2026-08-16", "Special Sunday Extra Class")
        ),
        term1StartDate = "2026-04-01",
        term1EndDate = "2026-09-30",
        term2StartDate = "2026-10-01",
        term2EndDate = "2027-03-31"
    )

    @Test
    fun workingDayCalculationHandlesWeekdaysHolidaysHalfDaysAndExceptions() {
        val cal = baseCalendar()
        // Range: 2026-08-14 (Fri, Half Working Day) to 2026-08-19 (Wed) = 6 calendar days:
        // 14 Fri: Working (half day)
        // 15 Sat: Holiday (Independence Day)
        // 16 Sun: Working (Admin exception overrides Sunday off)
        // 17 Mon: Working
        // 18 Tue: Declared Off (Heavy Rain Off)
        // 19 Wed: Working
        val stats = SchoolCalendarEngine.countWorkingDaysInRange(cal, "2026-08-14", "2026-08-19")
        assertEquals(6, stats.totalCalendarDays)
        assertEquals(4, stats.workingDays)
        assertEquals(1, stats.halfWorkingDays)
        assertEquals(2, stats.holidayDays)
        assertEquals(0, stats.weeklyOffDays)

        val friStatus = SchoolCalendarEngine.dayStatus(cal, "2026-08-14")
        assertTrue(friStatus is DayCalendarStatus.Working && friStatus.isHalfWorkingDay)

        val satStatus = SchoolCalendarEngine.dayStatus(cal, "2026-08-15")
        assertTrue(satStatus is DayCalendarStatus.NonWorking && satStatus.isHoliday)

        val sunStatus = SchoolCalendarEngine.dayStatus(cal, "2026-08-16")
        assertTrue(sunStatus is DayCalendarStatus.Working && sunStatus.isExceptionOverride)

        val tueStatus = SchoolCalendarEngine.dayStatus(cal, "2026-08-18")
        assertTrue(tueStatus is DayCalendarStatus.NonWorking && tueStatus.isHoliday)
    }

    @Test
    fun termDateValidationRejectsOverlapAndOutOfSessionDates() {
        assertNull(
            SchoolCalendarEngine.validateTermDates(
                session = "2026-2027",
                term1Start = "2026-04-01",
                term1End = "2026-09-30",
                term2Start = "2026-10-01",
                term2End = "2027-03-31"
            )
        )
        // Overlapping
        val overlapErr = SchoolCalendarEngine.validateTermDates(
            session = "2026-2027",
            term1Start = "2026-04-01",
            term1End = "2026-10-15",
            term2Start = "2026-10-01",
            term2End = "2027-03-31"
        )
        assertNotNull(overlapErr)
        assertTrue(overlapErr!!.contains("overlap", ignoreCase = true))

        // Outside session
        val outErr = SchoolCalendarEngine.validateTermDates(
            session = "2026-2027",
            term1Start = "2025-04-01",
            term1End = "2026-09-30",
            term2Start = "2026-10-01",
            term2End = "2027-03-31"
        )
        assertNotNull(outErr)
        assertTrue(outErr!!.contains("inside session", ignoreCase = true))
    }

    @Test
    fun weightedPresenceAndAutoFillSwitchPreserveManualValuesAndLeaveOffUnchanged() {
        val cal = SchoolCalendarConfig(
            id = "cal_sch1",
            schoolId = "sch1",
            session = "2026-2027",
            workingDays = listOf(DayOfWeekCode.MON, DayOfWeekCode.TUE, DayOfWeekCode.WED, DayOfWeekCode.THU, DayOfWeekCode.FRI),
            term1StartDate = "2026-04-06", // Mon
            term1EndDate = "2026-04-10",   // Fri -> 5 working days
            term2StartDate = "2026-10-05",
            term2EndDate = "2026-10-09"
        )
        // P (1.0) + Lt (1.0) + H (0.5) + L (0.0) + A (0.0) = 2.5 -> rounded half-up = 3
        val datesAndStatuses = listOf(
            "2026-04-06" to StudentAttendanceStatus.PRESENT,
            "2026-04-07" to StudentAttendanceStatus.LATE,
            "2026-04-08" to StudentAttendanceStatus.HALF_DAY,
            "2026-04-09" to StudentAttendanceStatus.LEAVE,
            "2026-04-10" to StudentAttendanceStatus.ABSENT
        )
        val sheets = datesAndStatuses.map { (d, st) ->
            StudentAttendanceSheet(
                id = StudentAttendanceSheet.makeDocId("sch1", "2026-2027", "3rd", "A", d),
                schoolId = "sch1",
                session = "2026-2027",
                className = "3rd",
                section = "A",
                date = d,
                entries = mapOf("stu_1" to StudentAttendanceEntry(st, if (st == StudentAttendanceStatus.LEAVE) "Fever" else ""))
            )
        }

        val comp = StudentAttendanceEngine.computeTermAttendance("stu_1", "sch1", "2026-2027", 1, cal, sheets)
        assertEquals(2.5, comp.rawDaysPresent, 0.0001)
        assertEquals(3, comp.roundedDaysPresent)
        assertEquals(5, comp.totalWorkingDays)
        assertEquals(1, comp.presentCount)
        assertEquals(1, comp.lateCount)
        assertEquals(1, comp.halfDayCount)
        assertEquals(1, comp.leaveCount)
        assertEquals(1, comp.absentCount)

        // Switch OFF -> stored manual TermInfo unchanged (or 0/0 if none)
        val storedManual = TermInfoEntity(1L, 1, daysPresent = 84, workingDays = 111)
        val offRes = StudentAttendanceEngine.resolveEffectiveTermAttendance(
            autoFillSwitchOn = false,
            storedTermInfo = storedManual,
            storedManualFlag = null,
            dailyComputation = comp
        )
        assertEquals(84, offRes.daysPresent)
        assertEquals(111, offRes.workingDays)
        assertFalse(offRes.isAutoFilled)

        // Switch ON + no manual override -> auto-filled from daily computation (3/5)
        val onAutoRes = StudentAttendanceEngine.resolveEffectiveTermAttendance(
            autoFillSwitchOn = true,
            storedTermInfo = null,
            storedManualFlag = null,
            dailyComputation = comp
        )
        assertEquals(3, onAutoRes.daysPresent)
        assertEquals(5, onAutoRes.workingDays)
        assertTrue(onAutoRes.isAutoFilled)
        assertFalse(onAutoRes.isManual)

        // Switch ON + explicit manual override -> protected & marked Manual
        val onManualProtected = StudentAttendanceEngine.resolveEffectiveTermAttendance(
            autoFillSwitchOn = true,
            storedTermInfo = storedManual,
            storedManualFlag = true,
            dailyComputation = comp
        )
        assertEquals(84, onManualProtected.daysPresent)
        assertEquals(111, onManualProtected.workingDays)
        assertTrue(onManualProtected.isManual)
        assertFalse(onManualProtected.isAutoFilled)

        // Switch ON + legacy non-zero TermInfo with null flag -> safely protected as Manual
        val onLegacyProtected = StudentAttendanceEngine.resolveEffectiveTermAttendance(
            autoFillSwitchOn = true,
            storedTermInfo = storedManual,
            storedManualFlag = null,
            dailyComputation = comp
        )
        assertEquals(84, onLegacyProtected.daysPresent)
        assertTrue(onLegacyProtected.isManual)
    }

    @Test
    fun legacy8KeyPermissionsMigrationAndClassTeacherOwnSectionAndDaysBackLimit() {
        // Legacy 8-key Firestore map (no 'attendance' key)
        val legacyMap = mapOf(
            "students" to "view",
            "marks" to "edit",
            "coScholastic" to "edit",
            "reports" to "view",
            "timetable" to "view",
            "teachers" to "none",
            "settings" to "none",
            "members" to "none"
        )
        val perms = MemberPermissions.fromMap(legacyMap)
        assertEquals(AccessLevel.NONE, perms.levelFor(AppModule.ATTENDANCE))

        val ctMember = MemberAccessProfile(
            uid = "u_ct",
            role = OrgRole.TEACHER,
            status = com.example.domain.MemberStatus.ACTIVE,
            allSchools = false,
            allowedSchoolIds = listOf("sch1"),
            permissions = perms
        )
        val ctScope = TeacherAccessScope(
            uid = "u_ct",
            teacherId = "t1",
            teacherName = "Mrs. Sharma",
            assignedClassSectionSubjects = listOf("sch1|3rd|A|Maths"),
            classTeacherSections = listOf("sch1|3rd|A")
        )

        // Class teacher gets automatic access to own section even with missing attendance key
        assertTrue(StudentAttendanceAccessEvaluator.canAccessStudentAttendanceModule(ctMember, ctScope))
        assertTrue(StudentAttendanceAccessEvaluator.canEditSectionAttendance(ctMember, ctScope, "sch1", "3rd", "A"))
        assertFalse(StudentAttendanceAccessEvaluator.canEditSectionAttendance(ctMember, ctScope, "sch1", "3rd", "B"))

        // Days-back limit: 7 days allowed, 8 days blocked, future blocked
        assertNull(
            StudentAttendanceAccessEvaluator.validateEditDateLimit(
                ctMember, ctScope, "sch1", "3rd", "A",
                targetDateIso = "2026-08-10",
                todayIso = "2026-08-17",
                classTeacherDaysBackLimit = 7
            )
        )
        assertNotNull(
            StudentAttendanceAccessEvaluator.validateEditDateLimit(
                ctMember, ctScope, "sch1", "3rd", "A",
                targetDateIso = "2026-08-09",
                todayIso = "2026-08-17",
                classTeacherDaysBackLimit = 7
            )
        )
        assertNotNull(
            StudentAttendanceAccessEvaluator.validateEditDateLimit(
                ctMember, ctScope, "sch1", "3rd", "A",
                targetDateIso = "2026-08-18",
                todayIso = "2026-08-17",
                classTeacherDaysBackLimit = 7
            )
        )

        // Admin has unlimited past-date access (still blocked for future dates)
        val adminMember = MemberAccessProfile(
            uid = "u_admin",
            role = OrgRole.ADMIN,
            status = com.example.domain.MemberStatus.ACTIVE,
            allSchools = true,
            allowedSchoolIds = emptyList(),
            permissions = MemberPermissions.presetFor(OrgRole.ADMIN)
        )
        assertNull(
            StudentAttendanceAccessEvaluator.validateEditDateLimit(
                adminMember, null, "sch1", "3rd", "B",
                targetDateIso = "2026-04-02",
                todayIso = "2026-08-17",
                classTeacherDaysBackLimit = 7
            )
        )
    }

    @Test
    fun editLogDiffLowAttendanceCsvAndGuardianShareMessagesWorkCleanly() {
        val stu1 = StudentEntity(
            id = 1L, schoolId = 1L, session = "2026-2027", admNo = "A01",
            name = "Aarav Sharma", fatherName = "Rajesh", motherName = "Sunita",
            dob = "01-01-2016", mobile = "9999999999", address = "Lucknow",
            className = "3rd", section = "A", rollNo = 1
        )
        val stu2 = stu1.copy(id = 2L, admNo = "A02", name = "Diya Verma", rollNo = 2)

        val prevSheet = StudentAttendanceSheet(
            id = "satt_1",
            schoolId = "sch1",
            session = "2026-2027",
            className = "3rd",
            section = "A",
            date = "2026-08-10",
            entries = mapOf(
                "stu_1" to StudentAttendanceEntry(StudentAttendanceStatus.PRESENT),
                "stu_2" to StudentAttendanceEntry(StudentAttendanceStatus.PRESENT)
            )
        )
        val nextEntries = mapOf(
            "stu_1" to StudentAttendanceEntry(StudentAttendanceStatus.ABSENT),
            "stu_2" to StudentAttendanceEntry(StudentAttendanceStatus.LEAVE, "Medical")
        )
        val changes = StudentAttendanceEngine.computeEditChanges(
            previousSheet = prevSheet,
            newEntries = nextEntries,
            studentsByDocId = mapOf("stu_1" to stu1, "stu_2" to stu2)
        )
        assertEquals(2, changes.size)
        assertTrue(
            StudentAttendanceEngine.shouldWritePostDayEditLog(
                sheetDateIso = "2026-08-10",
                todayIso = "2026-08-12",
                sheetAlreadyExisted = true,
                changes = changes
            )
        )

        val singleMsg = StudentAttendanceEngine.buildGuardianAbsentMessage(
            studentName = "Aarav Sharma",
            className = "3rd",
            section = "A",
            dateIso = "2026-08-10",
            schoolName = "Sunrise Public Academy"
        )
        assertTrue(singleMsg.contains("Aarav Sharma"))
        assertTrue(singleMsg.contains("3rd-A"))
        assertTrue(singleMsg.contains("absent", ignoreCase = true))

        val batchMsg = StudentAttendanceEngine.buildBatchAbsentMessage(
            schoolName = "Sunrise Public Academy",
            className = "3rd",
            section = "A",
            dateIso = "2026-08-10",
            absentStudents = listOf(stu1)
        )
        assertTrue(batchMsg.contains("Aarav Sharma"))
        assertTrue(batchMsg.contains("Roll 1"))
    }
}
