package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import com.example.domain.AccessLevel
import com.example.domain.AppModule
import com.example.domain.BirthdayOccurrenceItem
import com.example.domain.BirthdayPersonType
import com.example.domain.BirthdayWishRecord
import com.example.domain.ClassDiaryEntry
import com.example.domain.ClassSectionAssignment
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgRole
import com.example.domain.Phase18AccessEvaluator
import com.example.domain.Phase18DiaryAndBirthdayEngine
import com.example.domain.SchoolBirthdayConfig
import com.example.domain.SubjectTeacherAssignment
import com.example.domain.TeacherAccessScope
import com.example.domain.TeacherProfile
import com.example.util.BirthdayCardRequest
import com.example.util.Phase18PdfGenerator
import com.example.util.WeeklyDiaryPdfRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase18DiaryAndBirthdayTest {

    private val schoolId = "sch_main"
    private val session = "2026-2027"

    private fun makeOwnerMember(): MemberAccessProfile = MemberAccessProfile(
        uid = "uid_owner",
        role = OrgRole.OWNER,
        status = MemberStatus.ACTIVE,
        allSchools = true,
        allowedSchoolIds = emptyList(),
        permissions = MemberPermissions.presetFor(OrgRole.OWNER)
    )

    private fun makeAdminMember(): MemberAccessProfile = MemberAccessProfile(
        uid = "uid_admin",
        role = OrgRole.ADMIN,
        status = MemberStatus.ACTIVE,
        allSchools = true,
        allowedSchoolIds = emptyList(),
        permissions = MemberPermissions.presetFor(OrgRole.ADMIN)
    )

    private fun makeTeacherMember(
        uid: String,
        classDiaryAccess: AccessLevel = AccessLevel.NONE,
        studentsAccess: AccessLevel = AccessLevel.NONE,
        studentRegisterAccess: AccessLevel = AccessLevel.NONE,
        teachersAccess: AccessLevel = AccessLevel.NONE
    ): MemberAccessProfile = MemberAccessProfile(
        uid = uid,
        role = OrgRole.TEACHER,
        status = MemberStatus.ACTIVE,
        allSchools = false,
        allowedSchoolIds = listOf(schoolId),
        permissions = MemberPermissions(
            students = studentsAccess,
            studentRegister = studentRegisterAccess,
            teachers = teachersAccess,
            classDiary = classDiaryAccess
        )
    )

    private fun makeStudent(
        id: Long,
        admNo: String,
        name: String,
        fatherName: String = "Parent",
        dob: String = "2016-05-10",
        mobile: String = "9876543210",
        className: String = "5th",
        section: String = "A",
        status: String = "ACTIVE"
    ): StudentEntity = StudentEntity(
        id = id,
        schoolId = 1L,
        session = session,
        admNo = admNo,
        name = name,
        fatherName = fatherName,
        motherName = "Mother",
        dob = dob,
        mobile = mobile,
        address = "Civil Lines",
        className = className,
        section = section,
        rollNo = id.toInt(),
        status = status
    )

    // =========================================================================
    // 1. ACCESS MODULE ("Class Diary") & AUTOMATIC TEACHER SCOPE
    // =========================================================================

    @Test
    fun `missing classDiary key defaults to NONE while Owner and Admin get EDIT`() {
        val legacyMap = mapOf(
            "students" to "view",
            "marks" to "edit",
            "reports" to "view"
        )
        val parsed = MemberPermissions.fromMap(legacyMap)
        assertEquals(AccessLevel.NONE, parsed.levelFor(AppModule.CLASS_DIARY))
        assertEquals(AccessLevel.EDIT, MemberPermissions.presetFor(OrgRole.OWNER).classDiary)
        assertEquals(AccessLevel.EDIT, MemberPermissions.presetFor(OrgRole.ADMIN).classDiary)
        assertEquals(AccessLevel.NONE, MemberPermissions.presetFor(OrgRole.TEACHER).classDiary)
        assertEquals(AccessLevel.NONE, MemberPermissions.presetFor(OrgRole.OFFICE_OPERATOR).classDiary)
    }

    @Test
    fun `subject teacher and class teacher can write diary for own class and subject without extra setup`() {
        // Teacher with classDiary = NONE (missing key), assigned ONLY to 5th-A Maths
        val mathsTeacher = makeTeacherMember(uid = "uid_t_maths", classDiaryAccess = AccessLevel.NONE)
        val mathsScope = TeacherAccessScope(
            uid = "uid_t_maths",
            teacherId = "t_maths",
            teacherName = "Mr. Sharma",
            assignedClassSectionSubjects = listOf("$schoolId|5th|A|Maths"),
            classTeacherSections = emptyList()
        )

        // Can view Class Diary for schoolId and 5th-A
        assertTrue(Phase18AccessEvaluator.canViewClassDiary(mathsTeacher, mathsScope, schoolId))
        assertTrue(Phase18AccessEvaluator.canViewClassSectionDiary(mathsTeacher, mathsScope, schoolId, "5th", "A"))
        assertFalse(Phase18AccessEvaluator.canViewClassSectionDiary(mathsTeacher, mathsScope, schoolId, "6th", "A"))

        // Can write 5th-A Maths, CANNOT write 5th-A Science or 5th-B Maths
        assertTrue(Phase18AccessEvaluator.canWriteDiaryEntryFor(mathsTeacher, mathsScope, schoolId, "5th", "A", "Maths"))
        assertFalse(Phase18AccessEvaluator.canWriteDiaryEntryFor(mathsTeacher, mathsScope, schoolId, "5th", "A", "Science"))
        assertFalse(Phase18AccessEvaluator.canWriteDiaryEntryFor(mathsTeacher, mathsScope, schoolId, "5th", "B", "Maths"))

        // Class Teacher of 5th-A (with classDiary = NONE) can write ANY subject in 5th-A, but not 6th-A
        val classTeacher = makeTeacherMember(uid = "uid_ct_5a", classDiaryAccess = AccessLevel.NONE)
        val ctScope = TeacherAccessScope(
            uid = "uid_ct_5a",
            teacherId = "t_ct",
            teacherName = "Mrs. Verma",
            assignedClassSectionSubjects = listOf("$schoolId|5th|A|Hindi"),
            classTeacherSections = listOf("$schoolId|5th|A")
        )
        assertTrue(Phase18AccessEvaluator.canWriteDiaryEntryFor(classTeacher, ctScope, schoolId, "5th", "A", "Hindi"))
        assertTrue(Phase18AccessEvaluator.canWriteDiaryEntryFor(classTeacher, ctScope, schoolId, "5th", "A", "Science"))
        assertFalse(Phase18AccessEvaluator.canWriteDiaryEntryFor(classTeacher, ctScope, schoolId, "6th", "A", "Hindi"))

        // Unassigned teacher with classDiary = NONE has no access
        val unassignedTeacher = makeTeacherMember(uid = "uid_unassigned", classDiaryAccess = AccessLevel.NONE)
        assertFalse(Phase18AccessEvaluator.canViewClassDiary(unassignedTeacher, null, schoolId))
        assertFalse(Phase18AccessEvaluator.canWriteDiaryEntryFor(unassignedTeacher, null, schoolId, "5th", "A", "Maths"))
    }

    @Test
    fun `availableSubjectsForDiary prioritises teachers own assigned subjects and filters to writable`() {
        val mathsTeacher = makeTeacherMember(uid = "uid_t_maths")
        val mathsScope = TeacherAccessScope(
            uid = "uid_t_maths",
            teacherId = "t_maths",
            teacherName = "Mr. Sharma",
            assignedClassSectionSubjects = listOf("$schoolId|5th|A|Maths"),
            classTeacherSections = emptyList()
        )
        val phase3Subjects = listOf("English", "Hindi", "Maths", "Science")

        // Subject teacher sees only their writable subject (Maths)
        val subjList = Phase18DiaryAndBirthdayEngine.availableSubjectsForDiary(
            member = mathsTeacher,
            teacherScope = mathsScope,
            schoolId = schoolId,
            className = "5th",
            section = "A",
            classSubjects = phase3Subjects
        )
        assertEquals(listOf("Maths"), subjList)

        // Class teacher of 5th-A who teaches Science sees Science first, followed by all other class subjects
        val ctMember = makeTeacherMember(uid = "uid_ct_5a")
        val ctScope = TeacherAccessScope(
            uid = "uid_ct_5a",
            teacherId = "t_ct",
            teacherName = "Mrs. Verma",
            assignedClassSectionSubjects = listOf("$schoolId|5th|A|Science"),
            classTeacherSections = listOf("$schoolId|5th|A")
        )
        val ctList = Phase18DiaryAndBirthdayEngine.availableSubjectsForDiary(
            member = ctMember,
            teacherScope = ctScope,
            schoolId = schoolId,
            className = "5th",
            section = "A",
            classSubjects = phase3Subjects
        )
        assertEquals("Science", ctList.first())
        assertEquals(4, ctList.size)
    }

    // =========================================================================
    // 2. DIARY ENTRY RULES: AUTHOR-ONLY EDIT & DELETE, ADMIN OVERRIDE & HISTORY KEPT
    // =========================================================================

    @Test
    fun `diary edit and delete are allowed only by author or admin and revision history is preserved`() {
        val teacherA = makeTeacherMember(uid = "uid_teacher_a")
        val scopeA = TeacherAccessScope(
            uid = "uid_teacher_a",
            teacherId = "t_a",
            teacherName = "Mr. Author",
            assignedClassSectionSubjects = listOf("$schoolId|5th|A|Maths"),
            classTeacherSections = emptyList()
        )
        // Teacher B is the Class Teacher of 5th-A, so Teacher B can write new entries in 5th-A,
        // but CANNOT edit or delete Teacher A's entry!
        val teacherB = makeTeacherMember(uid = "uid_teacher_b")
        val scopeB = TeacherAccessScope(
            uid = "uid_teacher_b",
            teacherId = "t_b",
            teacherName = "Mrs. ClassTeacher",
            assignedClassSectionSubjects = listOf("$schoolId|5th|A|Hindi"),
            classTeacherSections = listOf("$schoolId|5th|A")
        )
        val admin = makeAdminMember()

        // 1. Teacher A creates entry
        val createResult = Phase18DiaryAndBirthdayEngine.createDiaryEntryWithHistory(
            schoolId = schoolId,
            session = session,
            dateIso = "2026-08-18",
            className = "5th",
            section = "A",
            subjectName = "Maths",
            classwork = "Chapter 4 Fractions Intro",
            homework = "Exercise 4.1 Q1-Q5",
            note = "Bring graph notebook",
            actor = teacherA,
            actorName = "Mr. Author",
            teacherScope = scopeA,
            nowMs = 1_000L
        )
        assertTrue(createResult.isSuccess)
        val (createdEntry, createHistory) = createResult.getOrThrow()
        assertEquals("CREATED", createHistory.action)
        assertEquals(0, createdEntry.editHistory.size)

        // 2. Teacher B (different teacher) is DENIED editing or deleting Teacher A's entry
        val otherEditAttempt = Phase18DiaryAndBirthdayEngine.editDiaryEntryWithHistory(
            existing = createdEntry,
            newClasswork = "Tampered by B",
            newHomework = "None",
            actor = teacherB,
            actorName = "Mrs. ClassTeacher",
            teacherScope = scopeB,
            nowMs = 2_000L
        )
        assertTrue(otherEditAttempt.isFailure)

        val otherDeleteAttempt = Phase18DiaryAndBirthdayEngine.deleteDiaryEntryWithHistory(
            existing = createdEntry,
            actor = teacherB,
            actorName = "Mrs. ClassTeacher",
            teacherScope = scopeB,
            nowMs = 2_500L
        )
        assertTrue(otherDeleteAttempt.isFailure)

        // 3. Author (Teacher A) CAN edit their own entry; previous values are kept in editHistory
        val authorEditResult = Phase18DiaryAndBirthdayEngine.editDiaryEntryWithHistory(
            existing = createdEntry,
            newClasswork = "Chapter 4 Fractions Proper & Improper",
            newHomework = "Exercise 4.1 Q1-Q8",
            newNote = "Test on Friday",
            actor = teacherA,
            actorName = "Mr. Author",
            teacherScope = scopeA,
            nowMs = 3_000L
        )
        assertTrue(authorEditResult.isSuccess)
        val (afterAuthorEdit, authorEditHist) = authorEditResult.getOrThrow()
        assertEquals(1, afterAuthorEdit.editHistory.size)
        assertEquals("Chapter 4 Fractions Intro", afterAuthorEdit.editHistory[0].previousClasswork)
        assertEquals("Exercise 4.1 Q1-Q5", afterAuthorEdit.editHistory[0].previousHomework)
        assertEquals("EDITED", authorEditHist.action)

        // 4. Admin CAN edit Teacher A's entry (Admin override) and history grows to 2 revisions
        val adminEditResult = Phase18DiaryAndBirthdayEngine.editDiaryEntryWithHistory(
            existing = afterAuthorEdit,
            newClasswork = "Chapter 4 Fractions (Admin Corrected)",
            newHomework = "Exercise 4.1 Q1-Q10",
            actor = admin,
            actorName = "Principal Admin",
            teacherScope = null,
            nowMs = 4_000L
        )
        assertTrue(adminEditResult.isSuccess)
        val (afterAdminEdit, adminEditHist) = adminEditResult.getOrThrow()
        assertEquals(2, afterAdminEdit.editHistory.size)
        assertEquals("uid_teacher_a", afterAdminEdit.authorUid) // original author preserved
        assertEquals("uid_admin", afterAdminEdit.lastEditedByUid)
        assertEquals("EDITED", adminEditHist.action)

        // 5. Admin CAN delete Teacher A's entry and a permanent DELETED history record is returned
        val adminDeleteResult = Phase18DiaryAndBirthdayEngine.deleteDiaryEntryWithHistory(
            existing = afterAdminEdit,
            actor = admin,
            actorName = "Principal Admin",
            teacherScope = null,
            nowMs = 5_000L
        )
        assertTrue(adminDeleteResult.isSuccess)
        val deletedHistory = adminDeleteResult.getOrThrow()
        assertEquals("DELETED", deletedHistory.action)
        assertEquals("Chapter 4 Fractions (Admin Corrected)", deletedHistory.classworkSnapshot)
        assertEquals("Exercise 4.1 Q1-Q10", deletedHistory.homeworkSnapshot)
    }

    // =========================================================================
    // 3. SHARE TEXT FORMAT, "DIARY NOT WRITTEN TODAY" LIST & WEEKLY VIEW
    // =========================================================================

    @Test
    fun `buildClassDiaryShareText formats date, class, subjects with classwork and homework, and school name`() {
        val entries = listOf(
            ClassDiaryEntry(
                id = "e1",
                schoolId = schoolId,
                session = session,
                dateIso = "2026-08-18",
                className = "5th",
                section = "A",
                subjectName = "Maths",
                classwork = "Decimals addition",
                homework = "Page 42 Q1-Q6",
                note = "Bring ruler",
                photoBase64 = "base64_dummy_photo",
                authorUid = "u1",
                authorName = "Mr. Sharma"
            ),
            ClassDiaryEntry(
                id = "e2",
                schoolId = schoolId,
                session = session,
                dateIso = "2026-08-18",
                className = "5th",
                section = "A",
                subjectName = "English",
                classwork = "Poem recitation",
                homework = "Learn word meanings",
                note = "",
                photoBase64 = "",
                authorUid = "u2",
                authorName = "Mrs. Roy"
            )
        )

        val enText = Phase18DiaryAndBirthdayEngine.buildClassDiaryShareText(
            schoolName = "Sunrise Public School",
            className = "5th",
            section = "A",
            dateIso = "2026-08-18",
            entries = entries,
            subjectOrder = listOf("English", "Maths"),
            isHindi = false
        )
        assertTrue(enText.contains("Class: 5th-A | Date: 2026-08-18"))
        assertTrue(enText.contains("1. English:"))
        assertTrue(enText.contains("Classwork: Poem recitation"))
        assertTrue(enText.contains("Homework: Learn word meanings"))
        assertTrue(enText.contains("2. Maths:"))
        assertTrue(enText.contains("Note: Bring ruler"))
        assertTrue(enText.contains("[Blackboard/Homework Photo Attached]"))
        assertTrue(enText.contains("School: Sunrise Public School"))

        val hiText = Phase18DiaryAndBirthdayEngine.buildClassDiaryShareText(
            schoolName = "सनराइज पब्लिक स्कूल",
            className = "5th",
            section = "A",
            dateIso = "2026-08-18",
            entries = entries,
            subjectOrder = listOf("English", "Maths"),
            isHindi = true
        )
        assertTrue(hiText.contains("कक्षा: 5th-A | दिनांक: 2026-08-18"))
        assertTrue(hiText.contains("कक्षा कार्य (CW): Poem recitation"))
        assertTrue(hiText.contains("गृहकार्य (HW): Page 42 Q1-Q6"))
        assertTrue(hiText.contains("विद्यालय: सनराइज पब्लिक स्कूल"))
    }

    @Test
    fun `computeDiaryNotWrittenToday lists only active class sections with no diary entry today`() {
        val students = listOf(
            makeStudent(id = 1, admNo = "A1", name = "S1", className = "3rd", section = "A", status = "ACTIVE"),
            makeStudent(id = 2, admNo = "A2", name = "S2", className = "3rd", section = "B", status = "ACTIVE"),
            makeStudent(id = 3, admNo = "A3", name = "S3", className = "4th", section = "A", status = "ACTIVE"),
            // Left student in 5th-A should not create an active section by itself
            makeStudent(id = 4, admNo = "A4", name = "S4", className = "5th", section = "A", status = "LEFT")
        )
        val teachers = listOf(
            TeacherProfile(id = "t1", name = "Mrs. Kavita"),
            TeacherProfile(id = "t2", name = "Mr. Rajesh")
        )
        val assignments = listOf(
            ClassSectionAssignment(
                id = "ca1",
                schoolId = schoolId,
                className = "3rd",
                section = "B",
                classTeacherId = "t1",
                subjectAssignments = listOf(SubjectTeacherAssignment("Maths", "t1", 6))
            ),
            ClassSectionAssignment(
                id = "ca2",
                schoolId = schoolId,
                className = "4th",
                section = "A",
                classTeacherId = "t2",
                subjectAssignments = emptyList()
            )
        )
        // Diary written today ONLY for 3rd-A
        val entries = listOf(
            ClassDiaryEntry(
                id = "d_3a",
                schoolId = schoolId,
                session = session,
                dateIso = "2026-08-18",
                className = "3rd",
                section = "A",
                subjectName = "Maths",
                classwork = "Tables 2 to 10",
                homework = "Write in copy",
                authorUid = "uid_owner",
                authorName = "Owner"
            )
        )

        val missing = Phase18DiaryAndBirthdayEngine.computeDiaryNotWrittenToday(
            schoolId = schoolId,
            session = session,
            todayIso = "2026-08-18",
            activeStudents = students,
            configuredClasses = listOf("3rd", "4th"),
            classAssignments = assignments,
            teachers = teachers,
            subjectsByClass = mapOf("3rd" to listOf("Maths", "Hindi"), "4th" to listOf("English")),
            allDiaryEntries = entries,
            classOrder = listOf("3rd", "4th")
        )

        assertEquals(2, missing.size)
        assertEquals("3rd-B", missing[0].label)
        assertEquals("Mrs. Kavita", missing[0].classTeacherName)
        assertEquals(2, missing[0].expectedSubjectCount)
        assertEquals("4th-A", missing[1].label)
        assertEquals("Mr. Rajesh", missing[1].classTeacherName)

        // Only Owner and Admin can view the "Diary not written today" list
        assertTrue(Phase18AccessEvaluator.canViewDiaryNotWrittenTodayList(makeOwnerMember()))
        assertTrue(Phase18AccessEvaluator.canViewDiaryNotWrittenTodayList(makeAdminMember()))
        assertFalse(Phase18AccessEvaluator.canViewDiaryNotWrittenTodayList(makeTeacherMember("uid_t")))
    }

    @Test
    fun `buildWeeklyDiaryBundle groups Monday to Sunday entries accurately`() {
        val entries = listOf(
            ClassDiaryEntry(
                id = "d_mon",
                schoolId = schoolId,
                session = session,
                dateIso = "2026-08-17", // Monday
                className = "5th",
                section = "A",
                subjectName = "Maths",
                classwork = "CW Mon",
                homework = "HW Mon",
                authorUid = "u1",
                authorName = "T1"
            ),
            ClassDiaryEntry(
                id = "d_Thu",
                schoolId = schoolId,
                session = session,
                dateIso = "2026-08-20", // Thursday
                className = "5th",
                section = "A",
                subjectName = "Science",
                classwork = "CW Thu",
                homework = "HW Thu",
                authorUid = "u1",
                authorName = "T1"
            )
        )

        val bundle = Phase18DiaryAndBirthdayEngine.buildWeeklyDiaryBundle(
            schoolId = schoolId,
            schoolName = "Sunrise Public School",
            session = session,
            className = "5th",
            section = "A",
            anchorDateIso = "2026-08-19", // Wednesday in that week
            allDiaryEntries = entries
        )

        assertEquals("2026-08-17", bundle.weekStartIso)
        assertEquals("2026-08-23", bundle.weekEndIso)
        assertEquals(7, bundle.days.size)
        assertEquals(2, bundle.totalEntriesCount)
        assertEquals(2, bundle.daysWrittenCount)
        assertEquals(1, bundle.days[0].entries.size) // Monday
        assertEquals(1, bundle.days[3].entries.size) // Thursday
    }

    // =========================================================================
    // 4. BIRTHDAYS: 29 FEB, YEAR-END ROLLOVER, 7-DAY WINDOW, WISHED MARK & TEMPLATES
    // =========================================================================

    @Test
    fun `birthday calculation handles 29 February in non-leap year vs leap year`() {
        val leapChild = makeStudent(
            id = 101L,
            admNo = "ADM-101",
            name = "Aarav Leap",
            fatherName = "Rakesh Verma",
            dob = "2016-02-29", // Born on 29 Feb 2016 (leap year)
            mobile = "9876543210",
            className = "4th",
            section = "A",
            status = "ACTIVE"
        )

        // In 2026 (non-leap year), 29 Feb birthday is celebrated on 28 Feb (2026-02-28)
        val nonLeapSummaryOnFeb25 = Phase18DiaryAndBirthdayEngine.computeBirthdaySummary(
            schoolId = schoolId,
            session = session,
            todayIso = "2026-02-25",
            students = listOf(leapChild),
            teachers = emptyList()
        )
        assertEquals(1, nonLeapSummaryOnFeb25.allWindowItems.size)
        val item2026 = nonLeapSummaryOnFeb25.allWindowItems.first()
        assertTrue(item2026.isBornOnFeb29)
        assertEquals("2026-02-28", item2026.celebrationDateIso)
        assertEquals(3, item2026.daysUntil)
        assertEquals(10, item2026.turningAge)
        assertEquals("+919876543210", item2026.normalizedWhatsAppMobile)

        // On 2026-02-28 itself, it appears in todayItems (daysUntil == 0)
        val nonLeapToday = Phase18DiaryAndBirthdayEngine.computeBirthdaySummary(
            schoolId = schoolId,
            session = session,
            todayIso = "2026-02-28",
            students = listOf(leapChild),
            teachers = emptyList()
        )
        assertEquals(1, nonLeapToday.todayCount)
        assertEquals(0, nonLeapToday.upcoming7DaysCount)

        // In 2028 (leap year), 29 Feb birthday is celebrated on 29 Feb (2028-02-29)
        val leapSummaryOnFeb25 = Phase18DiaryAndBirthdayEngine.computeBirthdaySummary(
            schoolId = schoolId,
            session = session,
            todayIso = "2028-02-25",
            students = listOf(leapChild),
            teachers = emptyList()
        )
        assertEquals(1, leapSummaryOnFeb25.allWindowItems.size)
        val item2028 = leapSummaryOnFeb25.allWindowItems.first()
        assertEquals("2028-02-29", item2028.celebrationDateIso)
        assertEquals(4, item2028.daysUntil)
        assertEquals(12, item2028.turningAge)
    }

    @Test
    fun `birthday calculation handles year change rollover, 7-day window, wished mark, exclusions and left students`() {
        val dec29TodayStudent = makeStudent(
            id = 1L,
            admNo = "ADM-1",
            name = "Priya Today",
            fatherName = "Suresh",
            dob = "29/12/2018", // dd/MM/yyyy format -> Today (Dec 29)
            mobile = "09811122233",
            className = "2nd",
            section = "A",
            status = "ACTIVE"
        )
        val jan03RolloverStudent = makeStudent(
            id = 2L,
            admNo = "ADM-2",
            name = "Kabir NewYear",
            fatherName = "Mahesh",
            dob = "2017-01-03", // 5 days after Dec 29 across year boundary -> 2027-01-03
            mobile = "919822233344",
            className = "3rd",
            section = "A",
            status = "ACTIVE"
        )
        val jan06OutsideWindowStudent = makeStudent(
            id = 3L,
            admNo = "ADM-3",
            name = "Outside 8Days",
            fatherName = "Naresh",
            dob = "2017-01-06", // 8 days after Dec 29 -> outside 7-day window
            mobile = "9833344455",
            className = "3rd",
            section = "A",
            status = "ACTIVE"
        )
        val leftStudent = makeStudent(
            id = 4L,
            admNo = "ADM-4",
            name = "Left Student",
            fatherName = "Dinesh",
            dob = "2018-12-29",
            mobile = "9844455566",
            className = "2nd",
            section = "A",
            status = "LEFT"
        )
        val excludedStudent = makeStudent(
            id = 5L,
            admNo = "ADM-5",
            name = "Excluded Student",
            fatherName = "Ganesh",
            dob = "2018-12-30",
            mobile = "9855566677",
            className = "2nd",
            section = "A",
            status = "ACTIVE"
        )
        val staffJan01 = TeacherProfile(
            id = "t_jan1",
            name = "Mrs. Anita Sharma",
            mobile = "9877788899",
            dob = "1990-01-01", // 3 days after Dec 29 across year boundary
            schoolIds = listOf(schoolId),
            active = true
        )

        val config = SchoolBirthdayConfig(
            schoolId = schoolId,
            birthdayWishesEnabled = true,
            excludedStudentKeys = listOf("adm:ADM-5")
        )
        val existingWishes = listOf(
            BirthdayWishRecord(
                id = Phase18DiaryAndBirthdayEngine.makeBirthdayWishDocId(
                    schoolId = schoolId,
                    personType = BirthdayPersonType.STUDENT,
                    personId = "ADM-1",
                    celebrationDateIso = "2026-12-29"
                ),
                schoolId = schoolId,
                personType = BirthdayPersonType.STUDENT,
                personId = "ADM-1",
                personName = "Priya Today",
                className = "2nd",
                section = "A",
                celebrationDateIso = "2026-12-29",
                wishedByUid = "uid_owner",
                wishedByName = "Principal",
                channel = "WHATSAPP",
                wishedAtMs = 1000L
            )
        )

        val summary = Phase18DiaryAndBirthdayEngine.computeBirthdaySummary(
            schoolId = schoolId,
            session = session,
            todayIso = "2026-12-29",
            students = listOf(
                dec29TodayStudent,
                jan03RolloverStudent,
                jan06OutsideWindowStudent,
                leftStudent,
                excludedStudent
            ),
            teachers = listOf(staffJan01),
            config = config,
            wishes = existingWishes,
            classOrder = listOf("2nd", "3rd")
        )

        // Today: Priya Today (Dec 29); Upcoming 7 days: Mrs. Anita Sharma (Jan 1, +3d) & Kabir NewYear (Jan 3, +5d)
        assertEquals(1, summary.todayCount)
        assertEquals(2, summary.upcoming7DaysCount)
        assertEquals(3, summary.totalWindowCount)
        assertEquals(1, summary.excludedStudentsCount)

        val priya = summary.todayItems.first()
        assertEquals("Priya Today", priya.name)
        assertTrue(priya.isToday)
        assertTrue(priya.isWished)
        assertEquals("+919811122233", priya.normalizedWhatsAppMobile)

        val anita = summary.upcoming7DayItems[0]
        assertEquals("Mrs. Anita Sharma", anita.name)
        assertEquals("2027-01-01", anita.celebrationDateIso)
        assertEquals(3, anita.daysUntil)
        assertEquals(37, anita.turningAge)
        assertFalse(anita.isWished)

        val kabir = summary.upcoming7DayItems[1]
        assertEquals("Kabir NewYear", kabir.name)
        assertEquals("2027-01-03", kabir.celebrationDateIso)
        assertEquals(5, kabir.daysUntil)
        assertEquals(10, kabir.turningAge)

        // Grouped by class order: "Class 2nd", "Class 3rd", "Staff / Teachers"
        assertEquals(listOf("Class 2nd", "Class 3rd", "Staff / Teachers"), summary.groupedByClass.keys.toList())

        // Turning off per-school birthday wishes hides all items
        val disabledSummary = Phase18DiaryAndBirthdayEngine.computeBirthdaySummary(
            schoolId = schoolId,
            session = session,
            todayIso = "2026-12-29",
            students = listOf(dec29TodayStudent),
            teachers = listOf(staffJan01),
            config = config.copy(birthdayWishesEnabled = false)
        )
        assertFalse(disabledSummary.isBirthdayWishesEnabled)
        assertEquals(0, disabledSummary.totalWindowCount)
    }

    @Test
    fun `formatBirthdayWishMessage and buildWhatsAppWishUrl include child name, school name, and +91 normalized mobile`() {
        val msg = Phase18DiaryAndBirthdayEngine.formatBirthdayWishMessage(
            template = "Happy Birthday dear {name} (Class {class})! Best wishes from {school}.",
            personName = "Ananya Tiwari",
            schoolName = "Sunrise Public School",
            className = "5th",
            turningAge = 10,
            isHindi = false
        )
        assertEquals("Happy Birthday dear Ananya Tiwari (Class 5th)! Best wishes from Sunrise Public School.", msg)

        // Even if a custom template omits {name} or {school}, both are guaranteed to be included
        val fallbackMsg = Phase18DiaryAndBirthdayEngine.formatBirthdayWishMessage(
            template = "Wishing you a very happy birthday!",
            personName = "Ananya Tiwari",
            schoolName = "Sunrise Public School",
            isHindi = false
        )
        assertTrue(fallbackMsg.contains("Ananya Tiwari"))
        assertTrue(fallbackMsg.contains("Sunrise Public School"))

        val waUrl = Phase18DiaryAndBirthdayEngine.buildWhatsAppWishUrl("9839100001", msg)
        assertNotNull(waUrl)
        assertTrue(waUrl!!.startsWith("https://wa.me/919839100001?text="))
        assertTrue(waUrl.contains("Ananya%20Tiwari"))

        // Invalid phone returns null
        assertNull(Phase18DiaryAndBirthdayEngine.buildWhatsAppWishUrl("12345", msg))
    }

    // =========================================================================
    // 5. ROBOLECTRIC PDF & BIRTHDAY CARD IMAGE GENERATION
    // =========================================================================

    @Test
    fun `Phase18PdfGenerator generates non-empty Weekly Diary PDF and Birthday Card PNG and PDF`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val school = SchoolEntity(
            id = 1L,
            name = "Sunrise Public School",
            address = "Civil Lines, Lucknow"
        )
        val entries = listOf(
            ClassDiaryEntry(
                id = "e1",
                schoolId = schoolId,
                session = session,
                dateIso = "2026-08-17",
                className = "5th",
                section = "A",
                subjectName = "Maths",
                classwork = "Fractions Exercise 4.1",
                homework = "Solve Q1-Q6",
                note = "Bring geometry box",
                authorUid = "u1",
                authorName = "Mr. Sharma"
            )
        )
        val weeklyBundle = Phase18DiaryAndBirthdayEngine.buildWeeklyDiaryBundle(
            schoolId = schoolId,
            schoolName = school.name,
            session = session,
            className = "5th",
            section = "A",
            anchorDateIso = "2026-08-17",
            allDiaryEntries = entries
        )

        val weeklyReq = WeeklyDiaryPdfRequest(
            school = school,
            weeklyBundle = weeklyBundle,
            generatedByName = "Principal"
        )
        val weeklyPdf = Phase18PdfGenerator.generateWeeklyDiaryPdf(context, weeklyReq)
        assertTrue(weeklyPdf.exists())
        assertTrue(weeklyPdf.length() > 100L)
        val previewBitmaps = Phase18PdfGenerator.renderWeeklyDiaryPreviewBitmaps(weeklyReq)
        assertTrue(previewBitmaps.isNotEmpty())
        assertEquals(Phase18PdfGenerator.A4_WIDTH, previewBitmaps.first().width)

        val bdayItem = BirthdayOccurrenceItem(
            personType = BirthdayPersonType.STUDENT,
            personId = "ADM-101",
            studentEntityId = 101L,
            name = "Ananya Tiwari",
            fatherOrSubtitle = "Manoj Tiwari",
            className = "5th",
            section = "A",
            admNoOrRole = "ADM-101",
            rawDob = "2016-08-18",
            birthYear = 2016,
            birthMonth = 8,
            birthDay = 18,
            celebrationDateIso = "2026-08-18",
            daysUntil = 0,
            turningAge = 10,
            rawMobile = "9839100001",
            normalizedWhatsAppMobile = "+919839100001"
        )
        val cardReq = BirthdayCardRequest(
            school = school,
            item = bdayItem,
            wishMessage = "Warmest birthday wishes to Ananya Tiwari from Sunrise Public School!",
            generatedByName = "Principal"
        )

        val cardPng = Phase18PdfGenerator.generateBirthdayCardPng(context, cardReq)
        assertTrue(cardPng.exists())
        assertTrue(cardPng.length() > 500L)

        val cardPdf = Phase18PdfGenerator.generateBirthdayCardPdf(context, cardReq)
        assertTrue(cardPdf.exists())
        assertTrue(cardPdf.length() > 100L)
    }
}
