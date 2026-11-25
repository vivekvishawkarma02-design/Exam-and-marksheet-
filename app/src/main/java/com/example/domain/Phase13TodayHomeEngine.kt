package com.example.domain

import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.StudentEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ============================================================================
// PHASE 13: "TODAY" HOME SCREEN & FIRST-TIME SETUP GUIDE ENGINE
// Pure Kotlin domain engine — zero Android framework dependencies, fast on
// offline cached data, strictly reuses Phases 1–12 calculation engines.
// ============================================================================

enum class TodayHomePersona {
    OWNER_ADMIN,
    TEACHER,
    FEE_CLERK,
    GENERAL_STAFF
}

enum class TodayQuickActionId {
    TAKE_STUDENT_ATTENDANCE,
    COLLECT_FEE,
    SEND_FEE_QUEUE,
    ENTER_MARKS,
    PENDING_MARKS,
    TIMETABLE,
    TEACHER_LEAVE,
    CASH_BOOK,
    ENQUIRY,
    STOCK,
    EXAMS,
    STUDENT_REGISTER,
    CERTIFICATES,
    SCHOOLS
}

data class TodayQuickAction(
    val id: TodayQuickActionId,
    val titleHi: String,
    val titleEn: String,
    val subtitleHi: String,
    val subtitleEn: String,
    val testTag: String
)

enum class FirstTimeGuideStepId {
    CREATE_SCHOOL,
    ADD_LOGO,
    CREATE_CLASSES_SECTIONS,
    ADD_STUDENTS,
    SET_FEE_STRUCTURE,
    ADD_TEACHERS,
    SET_TIMETABLE,
    ADD_FIRST_FEE_RECEIPT
}

data class FirstTimeGuideStep(
    val id: FirstTimeGuideStepId,
    val stepNumber: Int,
    val titleHi: String,
    val titleEn: String,
    val hintHi: String,
    val hintEn: String,
    val isCompleted: Boolean,
    val canOpen: Boolean
)

data class FirstTimeGuideState(
    val steps: List<FirstTimeGuideStep>,
    val isSkipped: Boolean,
    val isEligibleForRole: Boolean
) {
    val completedCount: Int get() = steps.count { it.isCompleted }
    val totalCount: Int get() = steps.size
    val isAllDone: Boolean get() = steps.isNotEmpty() && completedCount == totalCount
    val shouldShowExpandedCard: Boolean get() = isEligibleForRole && !isAllDone && !isSkipped
    val shouldShowReopenChip: Boolean get() = isEligibleForRole && !isAllDone && isSkipped
    val nextPendingStep: FirstTimeGuideStep? get() = steps.firstOrNull { !it.isCompleted }
}

data class MissingAttendanceSectionItem(
    val schoolDocId: String,
    val schoolName: String,
    val className: String,
    val section: String,
    val studentCount: Int
)

data class UpcomingExamItem(
    val examId: String,
    val examTitle: String,
    val className: String,
    val subjectName: String,
    val dateIso: String,
    val startTime: String,
    val endTime: String
)

data class TodayHomePermissions(
    val isOwnerOrAdmin: Boolean,
    val isTeacher: Boolean,
    val canAccessStudentAttendance: Boolean,
    val canViewAttendanceOverview: Boolean,
    val canViewFees: Boolean,
    val canEditFees: Boolean,
    val canViewCashBook: Boolean,
    val canViewTimetable: Boolean,
    val canViewTeachers: Boolean,
    val canViewEnquiry: Boolean,
    val canViewStock: Boolean,
    val canViewExams: Boolean,
    val canViewMarks: Boolean,
    val canEditMarks: Boolean,
    val canViewStudents: Boolean,
    val canEditStudents: Boolean,
    val canViewStudentRegister: Boolean,
    val canViewCertificates: Boolean,
    val canEditSettings: Boolean
)

data class OwnerAdminTodayTiles(
    // Attendance
    val showAttendanceTile: Boolean = false,
    val schoolAttendanceSummaries: List<SchoolTodayAttendanceSummary> = emptyList(),
    val missingAttendanceSections: List<MissingAttendanceSectionItem> = emptyList(),
    val totalSectionsAcrossSchools: Int = 0,
    val totalSectionsTakenAcrossSchools: Int = 0,
    // Fees & Expenses
    val showFeeCollectionTile: Boolean = false,
    val todayFeeCollectionRs: Int = 0,
    val todayFeeReceiptCount: Int = 0,
    val showExpensesTile: Boolean = false,
    val todayExpensesRs: Int = 0,
    val todayExpenseCount: Int = 0,
    val showGuardiansDuesTile: Boolean = false,
    val guardiansWithDuesCount: Int = 0,
    val totalDuesAmountRs: Int = 0,
    // Teachers & Substitutions
    val showTeacherAbsenceTile: Boolean = false,
    val absentTeachersTodayCount: Int = 0,
    val absentTeacherNames: List<String> = emptyList(),
    val pendingSubstitutionsTodayCount: Int = 0,
    // Enquiry
    val showEnquiryFollowUpTile: Boolean = false,
    val enquiryFollowUpsDueCount: Int = 0,
    val enquiryOverdueCount: Int = 0,
    val enquiryDueTodayCount: Int = 0,
    // Stock
    val showLowStockTile: Boolean = false,
    val lowStockItemCount: Int = 0,
    val lowStockTopNames: List<String> = emptyList(),
    // Exams & Duties This Week
    val showUpcomingExamsTile: Boolean = false,
    val upcomingExamEntriesThisWeek: List<UpcomingExamItem> = emptyList(),
    val upcomingExamDutiesThisWeekCount: Int = 0
)

data class TeacherClassAttendanceTarget(
    val schoolDocId: String,
    val schoolName: String,
    val className: String,
    val section: String,
    val studentCount: Int,
    val alreadyTakenToday: Boolean,
    val presentCount: Int
)

data class TeacherLeaveStatusSummary(
    val pendingCount: Int = 0,
    val approvedCount: Int = 0,
    val rejectedCount: Int = 0,
    val recentLeaves: List<TeacherLeaveRecord> = emptyList()
) {
    val totalCount: Int get() = pendingCount + approvedCount + rejectedCount
}

data class TeacherTodayData(
    val teacherName: String = "",
    val classAttendanceTargets: List<TeacherClassAttendanceTarget> = emptyList(),
    val pendingMarksCount: Int = 0,
    val upcomingExamDutiesCount: Int = 0,
    val leaveStatus: TeacherLeaveStatusSummary = TeacherLeaveStatusSummary(),
    val hasSalarySlips: Boolean = false
)

data class FeeClerkTodayData(
    val todayCollectionRs: Int = 0,
    val todayReceiptCount: Int = 0,
    val unsentDuesQueueCount: Int = 0,
    val unsentDuesTotalRs: Int = 0,
    val totalActiveStudentsInSchool: Int = 0
)

data class TodayHomeSnapshot(
    val dateIso: String,
    val persona: TodayHomePersona,
    val ownerAdminTiles: OwnerAdminTodayTiles = OwnerAdminTodayTiles(),
    val teacherData: TeacherTodayData? = null,
    val feeClerkData: FeeClerkTodayData? = null,
    val quickActions: List<TodayQuickAction> = emptyList(),
    val firstTimeGuide: FirstTimeGuideState
)

object Phase13TodayHomeEngine {

    fun resolvePersona(
        member: MemberAccessProfile?,
        hasLinkedTeacher: Boolean,
        permissions: TodayHomePermissions
    ): TodayHomePersona {
        if (member == null || permissions.isOwnerOrAdmin) {
            return TodayHomePersona.OWNER_ADMIN
        }
        if (member.role == OrgRole.TEACHER || permissions.isTeacher) {
            return TodayHomePersona.TEACHER
        }
        // Fee clerk: Office operator / staff member who has Fees access (and is not Owner/Admin/Teacher)
        if (permissions.canViewFees) {
            return TodayHomePersona.FEE_CLERK
        }
        if (hasLinkedTeacher) {
            return TodayHomePersona.TEACHER
        }
        return TodayHomePersona.GENERAL_STAFF
    }

    /**
     * Fast in-memory student search for Fee Clerk: matches name, admission number,
     * primary/secondary mobile (normalised digits), or roll number.
     */
    fun searchStudentsForFeeClerk(
        students: List<StudentEntity>,
        query: String,
        limit: Int = 8
    ): List<StudentEntity> {
        val trimmed = query.trim()
        val activeStudents = students.filter { it.isActiveStatus }
        if (trimmed.isEmpty()) return emptyList()
        val digitsOnly = trimmed.filter { it.isDigit() }
        val lower = trimmed.lowercase(Locale.US)

        return activeStudents
            .asSequence()
            .mapNotNull { st ->
                val nameLower = st.name.lowercase(Locale.US)
                val admLower = st.admNo.lowercase(Locale.US)
                val mobDigits = st.mobile.filter { it.isDigit() }
                val secMobDigits = st.secondMobile.filter { it.isDigit() }
                val fatherLower = st.fatherName.lowercase(Locale.US)

                val score = when {
                    admLower == lower -> 100
                    digitsOnly.length >= 4 && (mobDigits.endsWith(digitsOnly) || secMobDigits.endsWith(digitsOnly)) -> 95
                    nameLower.startsWith(lower) -> 90
                    admLower.startsWith(lower) -> 85
                    nameLower.contains(lower) -> 75
                    digitsOnly.length >= 3 && (mobDigits.contains(digitsOnly) || secMobDigits.contains(digitsOnly)) -> 70
                    fatherLower.contains(lower) -> 60
                    st.rollNo.toString() == trimmed -> 55
                    else -> 0
                }
                if (score > 0) score to st else null
            }
            .sortedWith(compareByDescending<Pair<Int, StudentEntity>> { it.first }.thenBy { it.second.name })
            .take(limit.coerceAtLeast(1))
            .map { it.second }
            .toList()
    }

    fun addDaysToIso(dateIso: String, days: Int): String {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fmt.isLenient = false
            val parsed = fmt.parse(dateIso) ?: return dateIso
            val cal = Calendar.getInstance()
            cal.time = parsed
            cal.add(Calendar.DAY_OF_MONTH, days)
            fmt.format(cal.time)
        } catch (_: Exception) {
            dateIso
        }
    }

    fun buildFirstTimeGuide(
        schools: List<SchoolEntity>,
        activeSchool: SchoolEntity?,
        activeSchoolDocId: String,
        session: String,
        schoolClassesForActiveSchool: List<SchoolClassEntity>,
        sessionStudentsForActiveSchool: List<StudentEntity>,
        feeStructures: List<FeeStructureConfig>,
        teachers: List<TeacherProfile>,
        timetableCells: List<TimetableCell>,
        feeReceipts: List<FeeReceiptRecord>,
        permissions: TodayHomePermissions,
        isSkipped: Boolean
    ): FirstTimeGuideState {
        val hasSchool = schools.any { it.active }
        val hasLogo = activeSchool != null && activeSchool.logoBase64.isNotBlank()
        val hasClasses = schoolClassesForActiveSchool.isNotEmpty()
        val hasStudents = sessionStudentsForActiveSchool.any { it.isActiveStatus }

        val savedStructure = feeStructures.firstOrNull {
            it.schoolId == activeSchoolDocId && it.session.equals(session, ignoreCase = true)
        }
        val hasFeeStructure = savedStructure != null && (
            savedStructure.tuitionByClassAndMonth.values.any { monthMap -> monthMap.values.any { amt -> amt > 0 } } ||
                savedStructure.examFees.any { head -> head.amountByClass.values.any { amt -> amt > 0 } } ||
                savedStructure.clientEditedAtMs > 0L ||
                savedStructure.updatedByUid.isNotBlank()
        )

        val hasTeachers = teachers.any { t ->
            t.active && (activeSchoolDocId.isBlank() || t.schoolIds.isEmpty() || activeSchoolDocId in t.schoolIds)
        }

        val hasTimetable = timetableCells.any {
            it.schoolId == activeSchoolDocId && it.session.equals(session, ignoreCase = true)
        }

        val hasFirstReceipt = feeReceipts.any {
            it.schoolId == activeSchoolDocId &&
                it.session.equals(session, ignoreCase = true) &&
                !it.isCancelled &&
                it.amountPaid > 0
        }

        val steps = listOf(
            FirstTimeGuideStep(
                id = FirstTimeGuideStepId.CREATE_SCHOOL,
                stepNumber = 1,
                titleHi = "स्कूल बनाएँ",
                titleEn = "Create school",
                hintHi = "स्कूल का नाम, पता और सेशन जोड़ें",
                hintEn = "Add your school name, address, and session",
                isCompleted = hasSchool,
                canOpen = permissions.canEditSettings
            ),
            FirstTimeGuideStep(
                id = FirstTimeGuideStepId.ADD_LOGO,
                stepNumber = 2,
                titleHi = "स्कूल लोगो जोड़ें",
                titleEn = "Add school logo",
                hintHi = "रिपोर्ट कार्ड, रसीद और एडमिट कार्ड पर लोगो दिखेगा",
                hintEn = "Shown on report cards, fee receipts, and admit cards",
                isCompleted = hasLogo,
                canOpen = permissions.canEditSettings
            ),
            FirstTimeGuideStep(
                id = FirstTimeGuideStepId.CREATE_CLASSES_SECTIONS,
                stepNumber = 3,
                titleHi = "कक्षा और सेक्शन बनाएँ",
                titleEn = "Create classes and sections",
                hintHi = "जैसे Nursery–10 और सेक्शन A, B",
                hintEn = "Set up classes (e.g. Nursery–10) and sections (A, B)",
                isCompleted = hasClasses,
                canOpen = permissions.canEditSettings
            ),
            FirstTimeGuideStep(
                id = FirstTimeGuideStepId.ADD_STUDENTS,
                stepNumber = 4,
                titleHi = "छात्र जोड़ें",
                titleEn = "Add students",
                hintHi = "एक-एक करके या CSV से छात्रों को जोड़ें",
                hintEn = "Add students one by one or import from CSV",
                isCompleted = hasStudents,
                canOpen = permissions.canViewStudents
            ),
            FirstTimeGuideStep(
                id = FirstTimeGuideStepId.SET_FEE_STRUCTURE,
                stepNumber = 5,
                titleHi = "फीस स्ट्रक्चर सेट करें",
                titleEn = "Set fee structure",
                hintHi = "हर कक्षा की मासिक ट्यूशन और अन्य फीस तय करें",
                hintEn = "Configure monthly tuition and fee heads per class",
                isCompleted = hasFeeStructure,
                canOpen = permissions.canViewFees
            ),
            FirstTimeGuideStep(
                id = FirstTimeGuideStepId.ADD_TEACHERS,
                stepNumber = 6,
                titleHi = "शिक्षक जोड़ें",
                titleEn = "Add teachers",
                hintHi = "शिक्षकों के नाम, विषय और कक्षाएँ जोड़ें",
                hintEn = "Add teachers with subjects and teaching load",
                isCompleted = hasTeachers,
                canOpen = permissions.canViewTeachers
            ),
            FirstTimeGuideStep(
                id = FirstTimeGuideStepId.SET_TIMETABLE,
                stepNumber = 7,
                titleHi = "टाइमटेबल बनाएँ",
                titleEn = "Set timetable",
                hintHi = "पीरियड सेट करें या ऑटो-जनरेट चलाएँ",
                hintEn = "Assign periods or run auto-generate timetable",
                isCompleted = hasTimetable,
                canOpen = permissions.canViewTimetable
            ),
            FirstTimeGuideStep(
                id = FirstTimeGuideStepId.ADD_FIRST_FEE_RECEIPT,
                stepNumber = 8,
                titleHi = "पहली फीस रसीद काटें",
                titleEn = "Add a first fee receipt",
                hintHi = "किसी छात्र की फीस जमा करके पहली रसीद बनाएँ",
                hintEn = "Collect fees from a student and generate your first receipt",
                isCompleted = hasFirstReceipt,
                canOpen = permissions.canViewFees
            )
        )

        val eligible = permissions.isOwnerOrAdmin || permissions.canEditSettings
        return FirstTimeGuideState(
            steps = steps,
            isSkipped = isSkipped,
            isEligibleForRole = eligible
        )
    }

    fun buildQuickActions(
        persona: TodayHomePersona,
        permissions: TodayHomePermissions,
        hasClassTeacherSection: Boolean
    ): List<TodayQuickAction> {
        val candidates = mutableListOf<TodayQuickAction>()

        fun addUnique(action: TodayQuickAction) {
            if (candidates.none { it.id == action.id } && candidates.size < 3) {
                candidates += action
            }
        }

        val takeAttendanceAction = TodayQuickAction(
            id = TodayQuickActionId.TAKE_STUDENT_ATTENDANCE,
            titleHi = "हाज़िरी लें",
            titleEn = "Attendance",
            subtitleHi = "आज की उपस्थिति",
            subtitleEn = "Daily roll",
            testTag = "quick_bar_attendance"
        )
        val collectFeeAction = TodayQuickAction(
            id = TodayQuickActionId.COLLECT_FEE,
            titleHi = "फीस लें",
            titleEn = "Collect Fee",
            subtitleHi = "नई रसीद काटें",
            subtitleEn = "New receipt",
            testTag = "quick_bar_collect_fee"
        )
        val sendQueueAction = TodayQuickAction(
            id = TodayQuickActionId.SEND_FEE_QUEUE,
            titleHi = "बकाया भेजें",
            titleEn = "Send Queue",
            subtitleHi = "व्हाट्सऐप रिमाइंडर",
            subtitleEn = "Due notices",
            testTag = "quick_bar_send_queue"
        )
        val enterMarksAction = TodayQuickAction(
            id = TodayQuickActionId.ENTER_MARKS,
            titleHi = "अंक भरें",
            titleEn = "Enter Marks",
            subtitleHi = "परीक्षा अंक",
            subtitleEn = "Exam marks",
            testTag = "quick_bar_enter_marks"
        )
        val pendingMarksAction = TodayQuickAction(
            id = TodayQuickActionId.PENDING_MARKS,
            titleHi = "बकाया अंक",
            titleEn = "Pending Marks",
            subtitleHi = "अधूरे अंक देखें",
            subtitleEn = "Incomplete",
            testTag = "quick_bar_pending_marks"
        )
        val timetableAction = TodayQuickAction(
            id = TodayQuickActionId.TIMETABLE,
            titleHi = "टाइमटेबल",
            titleEn = "Timetable",
            subtitleHi = "आज के पीरियड",
            subtitleEn = "My periods",
            testTag = "quick_bar_timetable"
        )
        val cashBookAction = TodayQuickAction(
            id = TodayQuickActionId.CASH_BOOK,
            titleHi = "रोकड़ बही",
            titleEn = "Cash Book",
            subtitleHi = "आय और खर्च",
            subtitleEn = "Expenses",
            testTag = "quick_bar_cash_book"
        )
        val enquiryAction = TodayQuickAction(
            id = TodayQuickActionId.ENQUIRY,
            titleHi = "पूछताछ",
            titleEn = "Enquiry",
            subtitleHi = "प्रवेश फ़ॉलो-अप",
            subtitleEn = "Follow-ups",
            testTag = "quick_bar_enquiry"
        )
        val registerAction = TodayQuickAction(
            id = TodayQuickActionId.STUDENT_REGISTER,
            titleHi = "छात्र रजिस्टर",
            titleEn = "Register",
            subtitleHi = "छात्र विवरण",
            subtitleEn = "Students",
            testTag = "quick_bar_register"
        )
        val stockAction = TodayQuickAction(
            id = TodayQuickActionId.STOCK,
            titleHi = "स्टॉक",
            titleEn = "Stock",
            subtitleHi = "सामान व खरीद",
            subtitleEn = "Inventory",
            testTag = "quick_bar_stock"
        )
        val examsAction = TodayQuickAction(
            id = TodayQuickActionId.EXAMS,
            titleHi = "परीक्षा",
            titleEn = "Exams",
            subtitleHi = "शेड्यूल व एडमिट",
            subtitleEn = "Admit cards",
            testTag = "quick_bar_exams"
        )
        val certificatesAction = TodayQuickAction(
            id = TodayQuickActionId.CERTIFICATES,
            titleHi = "प्रमाणपत्र",
            titleEn = "Certificates",
            subtitleHi = "TC व बोनाफाइड",
            subtitleEn = "TC / Bonafide",
            testTag = "quick_bar_certificates"
        )

        when (persona) {
            TodayHomePersona.OWNER_ADMIN -> {
                if (permissions.canAccessStudentAttendance) addUnique(takeAttendanceAction)
                if (permissions.canViewFees) addUnique(collectFeeAction)
                if (permissions.canViewCashBook) addUnique(cashBookAction)
                if (permissions.canViewMarks) addUnique(enterMarksAction)
                if (permissions.canViewEnquiry) addUnique(enquiryAction)
                if (permissions.canViewTimetable) addUnique(timetableAction)
                if (permissions.canViewExams) addUnique(examsAction)
                if (permissions.canViewStock) addUnique(stockAction)
            }
            TodayHomePersona.TEACHER -> {
                if (hasClassTeacherSection && permissions.canAccessStudentAttendance) {
                    addUnique(takeAttendanceAction)
                }
                if (permissions.canViewMarks) addUnique(enterMarksAction)
                if (permissions.canViewTimetable) addUnique(timetableAction)
                if (permissions.canViewMarks) addUnique(pendingMarksAction)
                if (permissions.canAccessStudentAttendance) addUnique(takeAttendanceAction)
            }
            TodayHomePersona.FEE_CLERK -> {
                if (permissions.canViewFees) addUnique(collectFeeAction)
                if (permissions.canViewFees) addUnique(sendQueueAction)
                if (permissions.canViewCashBook) addUnique(cashBookAction)
                if (permissions.canViewStudentRegister) addUnique(registerAction)
                if (permissions.canViewCertificates) addUnique(certificatesAction)
                if (permissions.canViewEnquiry) addUnique(enquiryAction)
                if (permissions.canViewStock) addUnique(stockAction)
            }
            TodayHomePersona.GENERAL_STAFF -> {
                if (permissions.canAccessStudentAttendance) addUnique(takeAttendanceAction)
                if (permissions.canViewEnquiry) addUnique(enquiryAction)
                if (permissions.canViewCashBook) addUnique(cashBookAction)
                if (permissions.canViewStock) addUnique(stockAction)
                if (permissions.canViewExams) addUnique(examsAction)
                if (permissions.canViewStudentRegister) addUnique(registerAction)
                if (permissions.canViewCertificates) addUnique(certificatesAction)
                if (permissions.canViewMarks) addUnique(enterMarksAction)
                if (permissions.canViewTimetable) addUnique(timetableAction)
            }
        }
        return candidates
    }

    fun buildSnapshot(
        todayIso: String,
        member: MemberAccessProfile?,
        permissions: TodayHomePermissions,
        schools: List<SchoolEntity>,
        activeSchool: SchoolEntity?,
        activeSchoolDocId: String,
        schoolNamesByDocId: Map<String, String>,
        session: String,
        schoolClassesForActiveSchool: List<SchoolClassEntity>,
        sessionStudentsForActiveSchool: List<StudentEntity>,
        studentsWithDocIdsForActiveSchool: List<Pair<String, StudentEntity>>,
        // Phase 8 attendance summaries (already computed per allowed school)
        todayAttendanceSummaries: List<SchoolTodayAttendanceSummary>,
        // Phase 9 Fees
        feeStructures: List<FeeStructureConfig>,
        activeFeeStructure: FeeStructureConfig,
        studentFeeOverrides: List<StudentFeeOverride>,
        feeReceipts: List<FeeReceiptRecord>,
        feeMessageSentLogs: List<FeeMessageSentLog>,
        // Phase 10 Cash Book & Enquiry
        cashBookEntries: List<CashBookEntryRecord>,
        cashBookDayClosings: List<CashBookDayClosingRecord>,
        cashBookSettings: CashBookSettingsConfig,
        salaryMonthlyRecords: List<SalaryMonthlyRecord>,
        admissionEnquiries: List<AdmissionEnquiryRecord>,
        enquirySettings: EnquirySettingsConfig,
        // Phase 6 & 7 Teachers, Leaves, Timetable, Substitutions, Attendance, Pending Marks
        teachers: List<TeacherProfile>,
        teacherLeaves: List<TeacherLeaveRecord>,
        teacherAttendanceRecords: List<TeacherAttendanceRecord>,
        timetableCells: List<TimetableCell>,
        substitutions: List<SubstitutionRecord>,
        periodStructuresBySchool: Map<String, SchoolPeriodStructure>,
        classAssignments: List<ClassSectionAssignment>,
        ownLinkedTeacher: TeacherProfile?,
        ownTeacherScope: TeacherAccessScope?,
        ownPendingMarksCount: Int,
        ownExamDuties: List<ExamInvigilatorDutyRecord>,
        ownSalarySlipsCount: Int,
        // Phase 11 Exams & Stock
        examSchedules: List<ExamScheduleRecord>,
        examDuties: List<ExamInvigilatorDutyRecord>,
        stockTransactions: List<StockTransactionRecord>,
        // Guide pref
        isFirstTimeGuideSkipped: Boolean
    ): TodayHomeSnapshot {
        val persona = resolvePersona(
            member = member,
            hasLinkedTeacher = ownLinkedTeacher != null,
            permissions = permissions
        )

        val activeSchoolName = activeSchool?.name ?: schoolNamesByDocId[activeSchoolDocId].orEmpty()

        // Pre-compute dues rows only if user can view Fees (avoid unnecessary work for Teachers/others)
        val duesRows: List<StudentDuesListRow> = if (permissions.canViewFees && activeSchoolDocId.isNotBlank()) {
            val overridesByStudentId = studentFeeOverrides
                .filter { it.schoolId == activeSchoolDocId && it.session.equals(session, ignoreCase = true) }
                .associateBy { it.studentId }
            val schoolReceipts = feeReceipts.filter {
                it.schoolId == activeSchoolDocId && it.session.equals(session, ignoreCase = true)
            }
            FeeReportsAndQueueEngine.buildDuesList(
                studentsWithDocIds = studentsWithDocIdsForActiveSchool,
                schoolDocId = activeSchoolDocId,
                schoolName = activeSchoolName,
                structure = activeFeeStructure,
                overridesByStudentDocId = overridesByStudentId,
                receipts = schoolReceipts,
                minimumDueAmount = 1,
                includeLeftStudents = false
            )
        } else {
            emptyList()
        }

        // Today's fee collection for active school
        val todaySchoolReceipts = if (permissions.canViewFees || permissions.canViewCashBook) {
            feeReceipts.filter {
                it.schoolId == activeSchoolDocId && !it.isCancelled && it.receiptDateIso == todayIso
            }
        } else {
            emptyList()
        }
        val todayFeeCollectionRs = todaySchoolReceipts.sumOf { it.amountPaid }
        val todayFeeReceiptCount = todaySchoolReceipts.size

        // Owner/Admin or General Staff Today tiles (strictly permission-gated!)
        val ownerAdminTiles = if (persona == TodayHomePersona.OWNER_ADMIN || persona == TodayHomePersona.GENERAL_STAFF) {
            // 1. Attendance per school & missing sections
            val showAttendance = permissions.canAccessStudentAttendance && permissions.canViewAttendanceOverview
            val missingSections = if (showAttendance) {
                todayAttendanceSummaries.flatMap { sch ->
                    sch.sectionStatuses
                        .filter { !it.taken && it.totalStudents > 0 }
                        .map { sec ->
                            MissingAttendanceSectionItem(
                                schoolDocId = sch.schoolDocId,
                                schoolName = sch.schoolName,
                                className = sec.className,
                                section = sec.section,
                                studentCount = sec.totalStudents
                            )
                        }
                }
            } else {
                emptyList()
            }

            // 2. Today's Expenses (auto-merged with paid Phase 12 salaries, zero double entry)
            val showExpenses = permissions.canViewCashBook
            val mergedCashBookEntries = if (showExpenses) {
                StaffSalaryEngine.mergeCashBookEntriesWithPaidSalaries(
                    schoolId = activeSchoolDocId,
                    cashBookEntries = cashBookEntries,
                    salaryRecords = salaryMonthlyRecords
                )
            } else {
                emptyList()
            }
            val cashDaySummary = if (showExpenses && activeSchoolDocId.isNotBlank()) {
                CashBookEngine.computeDaySummary(
                    schoolId = activeSchoolDocId,
                    dateIso = todayIso,
                    feeReceipts = feeReceipts,
                    entries = mergedCashBookEntries,
                    dayClosings = cashBookDayClosings,
                    settings = cashBookSettings
                )
            } else {
                null
            }

            // 3. Teachers absent today & substitutions pending
            val showTeacherAbsence = permissions.canViewTimetable
            val activeTeachersById = teachers.filter { it.active }.associateBy { it.id }
            val absentTeacherIds = if (showTeacherAbsence) {
                buildSet {
                    // From Teacher Attendance sheet (Phase 7)
                    teacherAttendanceRecords
                        .filter {
                            it.date == todayIso &&
                                (activeSchoolDocId.isBlank() || it.schoolId == activeSchoolDocId) &&
                                (it.status == TeacherAttendanceStatus.ABSENT || it.status == TeacherAttendanceStatus.ON_LEAVE)
                        }
                        .forEach { add(it.teacherId) }
                    // From Approved Leave (Phase 6)
                    teacherLeaves
                        .filter {
                            it.status == LeaveStatus.APPROVED &&
                                Phase6DateUtils.isDateWithinRange(todayIso, it.startDate, it.endDate)
                        }
                        .forEach { leave ->
                            val t = activeTeachersById[leave.teacherId]
                            if (t != null && (activeSchoolDocId.isBlank() || t.schoolIds.isEmpty() || activeSchoolDocId in t.schoolIds)) {
                                add(leave.teacherId)
                            }
                        }
                }
            } else {
                emptySet()
            }
            val absentTeacherNames = absentTeacherIds
                .mapNotNull { activeTeachersById[it]?.name }
                .sorted()

            val pendingSubsCount = if (showTeacherAbsence) {
                SubstitutionEngine.computeDailySubstitutionNeeds(
                    date = todayIso,
                    session = session,
                    schoolIdFilter = activeSchoolDocId,
                    teachers = teachers,
                    leaves = teacherLeaves,
                    timetableCellsInSession = timetableCells,
                    substitutions = substitutions,
                    periodStructuresBySchool = periodStructuresBySchool,
                    classAssignments = classAssignments,
                    schoolNamesById = schoolNamesByDocId
                ).count { it.needsSubstitute }
            } else {
                0
            }

            // 4. Enquiry follow-ups due today (overdue + due today)
            val showEnquiry = permissions.canViewEnquiry
            val enquiryQueue = if (showEnquiry && activeSchoolDocId.isNotBlank()) {
                AdmissionEnquiryEngine.buildFollowUpQueue(
                    enquiries = admissionEnquiries,
                    schoolId = activeSchoolDocId,
                    schoolName = activeSchoolName,
                    todayIso = todayIso,
                    template = enquirySettings.whatsappTemplate,
                    includeUpcoming = false
                )
            } else {
                emptyList()
            }
            val overdueEnquiries = enquiryQueue.count { it.urgency == FollowUpUrgency.OVERDUE }
            val dueTodayEnquiries = enquiryQueue.count { it.urgency == FollowUpUrgency.DUE_TODAY }

            // 5. Low-stock items
            val showStock = permissions.canViewStock
            val stockSummaries = if (showStock && activeSchoolDocId.isNotBlank()) {
                StockEngine.computeStockSummary(
                    schoolId = activeSchoolDocId,
                    session = session,
                    catalogue = activeFeeStructure.optionalCatalogue,
                    transactions = stockTransactions,
                    feeReceipts = feeReceipts
                )
            } else {
                emptyList()
            }
            val lowStockItems = stockSummaries.filter { it.isLowStock }

            // 6. Upcoming exams & duties this week (today .. today + 6 days)
            val showExams = permissions.canViewExams
            val weekEndIso = addDaysToIso(todayIso, 6)
            val upcomingEntries = if (showExams && activeSchoolDocId.isNotBlank()) {
                examSchedules
                    .filter { it.schoolId == activeSchoolDocId && (it.session.isBlank() || it.session.equals(session, ignoreCase = true)) }
                    .flatMap { sched ->
                        sched.entries
                            .filter { e -> e.dateIso in todayIso..weekEndIso }
                            .map { e ->
                                UpcomingExamItem(
                                    examId = sched.id,
                                    examTitle = sched.title,
                                    className = e.className,
                                    subjectName = e.subjectName,
                                    dateIso = e.dateIso,
                                    startTime = e.startTime,
                                    endTime = e.endTime
                                )
                            }
                    }
                    .sortedWith(compareBy({ it.dateIso }, { it.startTime }, { it.className }))
            } else {
                emptyList()
            }
            val upcomingDutiesCount = if (showExams && activeSchoolDocId.isNotBlank()) {
                examDuties.count {
                    it.schoolId == activeSchoolDocId && it.dateIso in todayIso..weekEndIso
                }
            } else {
                0
            }

            OwnerAdminTodayTiles(
                showAttendanceTile = showAttendance,
                schoolAttendanceSummaries = if (showAttendance) todayAttendanceSummaries else emptyList(),
                missingAttendanceSections = missingSections,
                totalSectionsAcrossSchools = if (showAttendance) todayAttendanceSummaries.sumOf { it.totalSections } else 0,
                totalSectionsTakenAcrossSchools = if (showAttendance) todayAttendanceSummaries.sumOf { it.sectionsTaken } else 0,
                showFeeCollectionTile = permissions.canViewFees,
                todayFeeCollectionRs = if (permissions.canViewFees) todayFeeCollectionRs else 0,
                todayFeeReceiptCount = if (permissions.canViewFees) todayFeeReceiptCount else 0,
                showExpensesTile = showExpenses,
                todayExpensesRs = cashDaySummary?.totalMoneyOut ?: 0,
                todayExpenseCount = cashDaySummary?.activeExpenses?.size ?: 0,
                showGuardiansDuesTile = permissions.canViewFees,
                guardiansWithDuesCount = if (permissions.canViewFees) duesRows.size else 0,
                totalDuesAmountRs = if (permissions.canViewFees) duesRows.sumOf { it.totalDueAmount } else 0,
                showTeacherAbsenceTile = showTeacherAbsence,
                absentTeachersTodayCount = absentTeacherIds.size,
                absentTeacherNames = absentTeacherNames,
                pendingSubstitutionsTodayCount = pendingSubsCount,
                showEnquiryFollowUpTile = showEnquiry,
                enquiryFollowUpsDueCount = enquiryQueue.size,
                enquiryOverdueCount = overdueEnquiries,
                enquiryDueTodayCount = dueTodayEnquiries,
                showLowStockTile = showStock,
                lowStockItemCount = lowStockItems.size,
                lowStockTopNames = lowStockItems.take(3).map { "${it.item.name} (${it.currentStock})" },
                showUpcomingExamsTile = showExams,
                upcomingExamEntriesThisWeek = upcomingEntries,
                upcomingExamDutiesThisWeekCount = upcomingDutiesCount
            )
        } else {
            OwnerAdminTodayTiles()
        }

        // Teacher Today Data (for Teacher persona OR linked teacher)
        val classTeacherTargets = if (ownTeacherScope != null && ownTeacherScope.classTeacherSections.isNotEmpty()) {
            ownTeacherScope.classTeacherSections.mapNotNull { key ->
                val parts = key.split("|")
                if (parts.size < 3) return@mapNotNull null
                val schId = parts[0]
                val cls = parts[1]
                val sec = parts[2]
                val schSummary = todayAttendanceSummaries.firstOrNull { it.schoolDocId == schId }
                val secStatus = schSummary?.sectionStatuses?.firstOrNull {
                    it.className.equals(cls, ignoreCase = true) && it.section.equals(sec, ignoreCase = true)
                }
                TeacherClassAttendanceTarget(
                    schoolDocId = schId,
                    schoolName = schoolNamesByDocId[schId] ?: activeSchoolName,
                    className = cls,
                    section = sec,
                    studentCount = secStatus?.totalStudents ?: 0,
                    alreadyTakenToday = secStatus?.taken == true,
                    presentCount = secStatus?.presentEquivalent ?: 0
                )
            }
        } else {
            emptyList()
        }

        val teacherData = if (persona == TodayHomePersona.TEACHER || ownLinkedTeacher != null) {
            val myTeacherId = ownLinkedTeacher?.id.orEmpty()
            val myLeaves = if (myTeacherId.isNotBlank()) {
                teacherLeaves
                    .filter { it.teacherId == myTeacherId }
                    .sortedByDescending { it.startDate }
            } else {
                emptyList()
            }
            val upcomingDuties = ownExamDuties.filter { it.dateIso >= todayIso }
            TeacherTodayData(
                teacherName = ownLinkedTeacher?.name.orEmpty(),
                classAttendanceTargets = classTeacherTargets,
                pendingMarksCount = ownPendingMarksCount,
                upcomingExamDutiesCount = upcomingDuties.size,
                leaveStatus = TeacherLeaveStatusSummary(
                    pendingCount = myLeaves.count { it.status == LeaveStatus.PENDING },
                    approvedCount = myLeaves.count { it.status == LeaveStatus.APPROVED },
                    rejectedCount = myLeaves.count { it.status == LeaveStatus.REJECTED },
                    recentLeaves = myLeaves.take(3)
                ),
                hasSalarySlips = ownSalarySlipsCount > 0
            )
        } else {
            null
        }

        // Fee Clerk Today Data
        val feeClerkData = if (persona == TodayHomePersona.FEE_CLERK && permissions.canViewFees) {
            val monthKey = todayIso.take(7) // YYYY-MM
            val unsentQueue = FeeReportsAndQueueEngine.buildSendQueue(
                duesRows = duesRows,
                schoolName = activeSchoolName,
                templates = activeFeeStructure.messageTemplates,
                messageType = FeeMessageType.FEE_DUE_REMINDER,
                contextDateOrMonthKey = monthKey,
                sentLogs = feeMessageSentLogs,
                showPendingOnly = true
            )
            FeeClerkTodayData(
                todayCollectionRs = todayFeeCollectionRs,
                todayReceiptCount = todayFeeReceiptCount,
                unsentDuesQueueCount = unsentQueue.size,
                unsentDuesTotalRs = unsentQueue.sumOf { it.dueAmount },
                totalActiveStudentsInSchool = sessionStudentsForActiveSchool.count { it.isActiveStatus }
            )
        } else {
            null
        }

        val quickActions = buildQuickActions(
            persona = persona,
            permissions = permissions,
            hasClassTeacherSection = classTeacherTargets.isNotEmpty()
        )

        val firstTimeGuide = buildFirstTimeGuide(
            schools = schools,
            activeSchool = activeSchool,
            activeSchoolDocId = activeSchoolDocId,
            session = session,
            schoolClassesForActiveSchool = schoolClassesForActiveSchool,
            sessionStudentsForActiveSchool = sessionStudentsForActiveSchool,
            feeStructures = feeStructures,
            teachers = teachers,
            timetableCells = timetableCells,
            feeReceipts = feeReceipts,
            permissions = permissions,
            isSkipped = isFirstTimeGuideSkipped
        )

        return TodayHomeSnapshot(
            dateIso = todayIso,
            persona = persona,
            ownerAdminTiles = ownerAdminTiles,
            teacherData = teacherData,
            feeClerkData = feeClerkData,
            quickActions = quickActions,
            firstTimeGuide = firstTimeGuide
        )
    }
}
