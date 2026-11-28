package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.BuildConfig
import com.example.domain.AppModule
import com.example.domain.OrgRole
import com.example.ui.designsystem.GlassBottomBarItem

enum class NavSection(
    val id: String,
    val route: String,
    val titleEn: String,
    val titleHi: String,
    val compactLabelEn: String,
    val compactLabelHi: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val navTestTag: String
) {
    HOME(
        id = "home",
        route = AppRoutes.ROUTE_DASHBOARD,
        titleEn = "Home",
        titleHi = "होम",
        compactLabelEn = "Home",
        compactLabelHi = "होम",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        navTestTag = "nav_home"
    ),
    STUDENTS(
        id = "students",
        route = AppRoutes.ROUTE_SECTION_STUDENTS,
        titleEn = "Students",
        titleHi = "छात्र",
        compactLabelEn = "Students",
        compactLabelHi = "छात्र",
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups,
        navTestTag = "nav_students"
    ),
    ACADEMICS(
        id = "academics",
        route = AppRoutes.ROUTE_SECTION_ACADEMICS,
        titleEn = "Academics",
        titleHi = "शैक्षणिक",
        compactLabelEn = "Academics",
        compactLabelHi = "शैक्षणिक",
        selectedIcon = Icons.Filled.School,
        unselectedIcon = Icons.Outlined.School,
        navTestTag = "nav_academics"
    ),
    FINANCE_OPS(
        id = "finance_ops",
        route = AppRoutes.ROUTE_SECTION_FINANCE,
        titleEn = "Finance & Ops",
        titleHi = "वित्त व संचालन",
        compactLabelEn = "Finance",
        compactLabelHi = "वित्त",
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet,
        navTestTag = "nav_finance"
    ),
    MANAGE(
        id = "manage",
        route = AppRoutes.ROUTE_SECTION_MANAGE,
        titleEn = "Manage",
        titleHi = "प्रबंधन",
        compactLabelEn = "Manage",
        compactLabelHi = "प्रबंधन",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        navTestTag = "nav_manage"
    )
}

enum class SectionCardGroup(
    val titleEn: String,
    val titleHi: String
) {
    STUDENTS_DIRECTORY("Directory & Admissions", "डायरेक्टरी व प्रवेश"),
    STUDENTS_RECORDS("Records, Communication & Campus", "दस्तावेज़, संपर्क व परिसर"),
    ACADEMICS_EXAMS("Exams & Marks", "परीक्षा व अंक"),
    ACADEMICS_REPORTS("Reports & Analysis", "रिपोर्ट व विश्लेषण"),
    ACADEMICS_DAILY("Attendance, Diary & Staff", "हाज़िरी, डायरी व शिक्षक"),
    FINANCE_ACCOUNTS("Fees & Accounts", "शुल्क व लेखा"),
    FINANCE_OPS("Campus Operations", "परिसर संचालन"),
    MANAGE_ORG("Organisation & Members", "संगठन व सदस्य"),
    MANAGE_DATA("Subscription & Backup", "सदस्यता व बैकअप"),
    MANAGE_TOOLS("Developer Tools", "डेवलपर टूल्स")
}

data class RouteInfo(
    val route: String,
    val parentSection: NavSection,
    val isTopLevelDestination: Boolean,
    val titleEn: String,
    val titleHi: String,
    val controllingModule: AppModule?,
    val needsSchool: Boolean,
    val group: SectionCardGroup? = null,
    val sortOrder: Int = 0,
    val icon: ImageVector = Icons.Default.School,
    val testTag: String = "route_$route",
    val viewOnlyDescriptionEn: String = "",
    val editDescriptionEn: String = "",
    val viewOnlyDescriptionHi: String = "",
    val editDescriptionHi: String = "",
    val canEditResolver: (SchoolUiState) -> Boolean = { state ->
        controllingModule != null && state.canEdit(controllingModule)
    },
    val canAccess: (SchoolUiState) -> Boolean,
    val statusBadge: (SchoolUiState) -> Pair<String, Boolean>? = { null }
)

object AppNavigationRegistry {

    val allRoutes: List<RouteInfo> = listOf(
        // ==================== TOP-LEVEL SECTION DESTINATIONS (5) ====================
        RouteInfo(
            route = AppRoutes.ROUTE_DASHBOARD,
            parentSection = NavSection.HOME,
            isTopLevelDestination = true,
            titleEn = "Home",
            titleHi = "होम",
            controllingModule = null,
            needsSchool = false,
            icon = Icons.Filled.Home,
            testTag = "nav_home",
            canAccess = { true }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_SECTION_STUDENTS,
            parentSection = NavSection.STUDENTS,
            isTopLevelDestination = true,
            titleEn = "Students",
            titleHi = "छात्र",
            controllingModule = null,
            needsSchool = false,
            icon = Icons.Filled.Groups,
            testTag = "section_home_students",
            canAccess = { state -> cardsForSection(NavSection.STUDENTS, state).isNotEmpty() }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_SECTION_ACADEMICS,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = true,
            titleEn = "Academics",
            titleHi = "शैक्षणिक",
            controllingModule = null,
            needsSchool = false,
            icon = Icons.Filled.School,
            testTag = "section_home_academics",
            canAccess = { state -> cardsForSection(NavSection.ACADEMICS, state).isNotEmpty() }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_SECTION_FINANCE,
            parentSection = NavSection.FINANCE_OPS,
            isTopLevelDestination = true,
            titleEn = "Finance & Ops",
            titleHi = "वित्त व संचालन",
            controllingModule = null,
            needsSchool = false,
            icon = Icons.Filled.AccountBalanceWallet,
            testTag = "section_home_finance",
            canAccess = { state -> cardsForSection(NavSection.FINANCE_OPS, state).isNotEmpty() }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_SECTION_MANAGE,
            parentSection = NavSection.MANAGE,
            isTopLevelDestination = true,
            titleEn = "Manage",
            titleHi = "प्रबंधन",
            controllingModule = null,
            needsSchool = false,
            icon = Icons.Filled.Tune,
            testTag = "section_home_manage",
            canAccess = { true }
        ),

        // ==================== STUDENTS SECTION SUB-SCREENS ====================
        RouteInfo(
            route = AppRoutes.ROUTE_STUDENT_REGISTER,
            parentSection = NavSection.STUDENTS,
            isTopLevelDestination = false,
            titleEn = "Student Register",
            titleHi = "छात्र रजिस्टर",
            controllingModule = AppModule.STUDENTS,
            needsSchool = true,
            group = SectionCardGroup.STUDENTS_DIRECTORY,
            sortOrder = 10,
            icon = Icons.Default.Badge,
            testTag = "section_card_student_register",
            viewOnlyDescriptionEn = "View student register, profiles & ID cards",
            editDescriptionEn = "Add, edit, transport, siblings & ID cards",
            viewOnlyDescriptionHi = "छात्र सूची, प्रोफाइल व आईडी कार्ड देखें",
            editDescriptionHi = "छात्र जोड़ें/संपादित करें, परिवहन, भाई-बहन व आईडी कार्ड",
            canEditResolver = { it.canEditStudentRegister || it.canEdit(AppModule.STUDENTS) },
            canAccess = { it.canViewStudentRegister || it.canView(AppModule.STUDENTS) },
            statusBadge = { state ->
                val activeCount = state.allStudents.count {
                    it.schoolId == state.activeSchool.id &&
                        it.session.equals(state.filter.session, ignoreCase = true) &&
                        it.isActiveStatus
                }
                if (activeCount > 0) "$activeCount active" to false else null
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_ENQUIRY,
            parentSection = NavSection.STUDENTS,
            isTopLevelDestination = false,
            titleEn = "Admission Enquiry",
            titleHi = "प्रवेश पूछताछ",
            controllingModule = AppModule.ENQUIRY,
            needsSchool = true,
            group = SectionCardGroup.STUDENTS_DIRECTORY,
            sortOrder = 20,
            icon = Icons.Default.ContactPhone,
            testTag = "section_card_enquiry",
            viewOnlyDescriptionEn = "View admission enquiries and follow-ups",
            editDescriptionEn = "Track leads, follow-ups, WhatsApp & admit",
            viewOnlyDescriptionHi = "प्रवेश पूछताछ व फॉलो-अप देखें",
            editDescriptionHi = "पूछताछ, फॉलो-अप, व्हाट्सएप व प्रवेश रूपांतरण",
            canEditResolver = { it.canEditEnquiry },
            canAccess = { it.canViewEnquiry },
            statusBadge = { state ->
                val count = state.admissionEnquiries.count {
                    it.schoolId == state.activeSchoolDocId &&
                        it.status.name in setOf("NEW", "FOLLOW_UP", "INTERESTED")
                }
                if (count > 0) "$count open" to false else null
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_BULK_IMPORT,
            parentSection = NavSection.STUDENTS,
            isTopLevelDestination = false,
            titleEn = "Bulk Student Import",
            titleHi = "बल्क छात्र इम्पोर्ट",
            controllingModule = AppModule.STUDENTS,
            needsSchool = true,
            group = SectionCardGroup.STUDENTS_DIRECTORY,
            sortOrder = 30,
            icon = Icons.Default.UploadFile,
            testTag = "section_card_bulk_import",
            viewOnlyDescriptionEn = "Import student lists via CSV or Excel",
            editDescriptionEn = "Import students from CSV or Excel (.xlsx)",
            viewOnlyDescriptionHi = "CSV या Excel से छात्र सूची इम्पोर्ट करें",
            editDescriptionHi = "CSV / Excel (.xlsx) से एक साथ छात्र जोड़ें",
            canEditResolver = { it.canUseBulkStudentImport },
            canAccess = { it.canUseBulkStudentImport }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_CERTIFICATES,
            parentSection = NavSection.STUDENTS,
            isTopLevelDestination = false,
            titleEn = "Certificates",
            titleHi = "प्रमाणपत्र",
            controllingModule = AppModule.CERTIFICATES,
            needsSchool = true,
            group = SectionCardGroup.STUDENTS_RECORDS,
            sortOrder = 40,
            icon = Icons.Default.Verified,
            testTag = "section_card_certificates",
            viewOnlyDescriptionEn = "View issued certificates register & PDFs",
            editDescriptionEn = "Issue TC, Bonafide & Character A4 PDFs",
            viewOnlyDescriptionHi = "जारी प्रमाणपत्र रजिस्टर व PDF देखें",
            editDescriptionHi = "TC, बोनाफाइड व चरित्र प्रमाणपत्र PDF बनाएँ",
            canEditResolver = { it.canEditCertificates },
            canAccess = { it.canViewCertificates }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_STUDENT_REMARKS,
            parentSection = NavSection.STUDENTS,
            isTopLevelDestination = false,
            titleEn = "Remarks & PTM",
            titleHi = "टिप्पणियाँ व PTM",
            controllingModule = AppModule.STUDENT_REMARKS,
            needsSchool = true,
            group = SectionCardGroup.STUDENTS_RECORDS,
            sortOrder = 50,
            icon = Icons.AutoMirrored.Filled.Assignment,
            testTag = "section_card_student_remarks",
            viewOnlyDescriptionEn = "View student remarks & parent meeting notes",
            editDescriptionEn = "Student remarks, follow-ups & Parent Meetings",
            viewOnlyDescriptionHi = "छात्र टिप्पणियाँ व PTM विवरण देखें",
            editDescriptionHi = "छात्र टिप्पणियाँ, फॉलो-अप व अभिभावक बैठक",
            canEditResolver = { it.canEdit(AppModule.STUDENT_REMARKS) },
            canAccess = { it.canOpenStudentRemarksModule }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_GATE_PASS,
            parentSection = NavSection.STUDENTS,
            isTopLevelDestination = false,
            titleEn = "Gate & Visitors",
            titleHi = "गेट पास व आगंतुक",
            controllingModule = AppModule.GATE,
            needsSchool = true,
            group = SectionCardGroup.STUDENTS_RECORDS,
            sortOrder = 60,
            icon = Icons.Default.Badge,
            testTag = "section_card_gate_pass",
            viewOnlyDescriptionEn = "View early leave passes & visitor register",
            editDescriptionEn = "A5 early leave pass, gate & visitor register",
            viewOnlyDescriptionHi = "गेट पास व आगंतुक रजिस्टर देखें",
            editDescriptionHi = "A5 छुट्टी पर्ची, गेट रजिस्टर व आगंतुक",
            canEditResolver = { it.canEditGateModule },
            canAccess = { it.canViewGateModule },
            statusBadge = { state ->
                val inside = state.visitorEntries.count {
                    it.schoolId == state.activeSchoolDocId && it.timeOut.isBlank()
                }
                if (inside > 0) "$inside inside" to false else null
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_BIRTHDAYS,
            parentSection = NavSection.STUDENTS,
            isTopLevelDestination = false,
            titleEn = "Birthdays",
            titleHi = "जन्मदिन",
            controllingModule = AppModule.STUDENTS,
            needsSchool = true,
            group = SectionCardGroup.STUDENTS_RECORDS,
            sortOrder = 70,
            icon = Icons.Default.Cake,
            testTag = "section_card_birthdays",
            viewOnlyDescriptionEn = "View today & upcoming 7 days birthdays",
            editDescriptionEn = "Today & next 7 days, WhatsApp wishes & cards",
            viewOnlyDescriptionHi = "आज व अगले 7 दिनों के जन्मदिन देखें",
            editDescriptionHi = "आज व अगले 7 दिन, व्हाट्सएप बधाई व कार्ड",
            canEditResolver = { it.canEdit(AppModule.STUDENTS) || it.canEdit(AppModule.CLASS_DIARY) },
            canAccess = { it.canViewBirthdays }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_SESSION_PROMOTION,
            parentSection = NavSection.STUDENTS,
            isTopLevelDestination = false,
            titleEn = "Session & Promotion",
            titleHi = "नया सत्र व प्रमोशन",
            controllingModule = AppModule.SESSION_PROMOTION,
            needsSchool = true,
            group = SectionCardGroup.STUDENTS_RECORDS,
            sortOrder = 80,
            icon = Icons.Default.School,
            testTag = "section_card_session_promotion",
            viewOnlyDescriptionEn = "View promotion register & alumni history",
            editDescriptionEn = "Start new session, promote students & alumni",
            viewOnlyDescriptionHi = "प्रमोशन रजिस्टर व पूर्व छात्र रिकॉर्ड देखें",
            editDescriptionHi = "नया सत्र, कक्षा प्रोन्नति व पूर्व छात्र इतिहास",
            canEditResolver = { it.canEditSessionPromotion },
            canAccess = { it.canViewSessionPromotion }
        ),

        // ==================== ACADEMICS SECTION SUB-SCREENS ====================
        RouteInfo(
            route = AppRoutes.ROUTE_MARKS,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Marks Entry",
            titleHi = "अंक प्रविष्टि",
            controllingModule = AppModule.MARKS,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_EXAMS,
            sortOrder = 10,
            icon = Icons.Default.EditNote,
            testTag = "section_card_marks",
            viewOnlyDescriptionEn = "View entered marks (IA 10, UT 30, Exam 60)",
            editDescriptionEn = "Enter marks: IA 10, UT 30, Exam 60",
            viewOnlyDescriptionHi = "दर्ज अंक देखें (IA 10, UT 30, Exam 60)",
            editDescriptionHi = "अंक दर्ज करें: IA 10, UT 30, Exam 60",
            canAccess = { it.canView(AppModule.MARKS) },
            statusBadge = { state ->
                if (state.marksCompletionPercent > 0) "${state.marksCompletionPercent}% done" to false else null
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_PENDING_MARKS,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Pending Marks",
            titleHi = "बकाया अंक",
            controllingModule = AppModule.MARKS,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_EXAMS,
            sortOrder = 20,
            icon = Icons.Default.AssignmentLate,
            testTag = "section_card_pending_marks",
            viewOnlyDescriptionEn = "View pending mark sheets by exam & class",
            editDescriptionEn = "Incomplete mark sheets by exam & reminders",
            viewOnlyDescriptionHi = "परीक्षा के अनुसार बकाया अंक सूची देखें",
            editDescriptionHi = "परीक्षा के अनुसार अधूरे अंक व अनुस्मारक",
            canAccess = { it.canView(AppModule.MARKS) }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_COSCHOLASTIC,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Co-Scholastic",
            titleHi = "सह-शैक्षिक ग्रेड",
            controllingModule = AppModule.CO_SCHOLASTIC,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_EXAMS,
            sortOrder = 30,
            icon = Icons.Default.FactCheck,
            testTag = "section_card_coscholastic",
            viewOnlyDescriptionEn = "View co-scholastic grades & attendance",
            editDescriptionEn = "Co-scholastic grades, traits & attendance",
            viewOnlyDescriptionHi = "सह-शैक्षिक ग्रेड व उपस्थिति देखें",
            editDescriptionHi = "सह-शैक्षिक ग्रेड व उपस्थिति दर्ज करें",
            canAccess = { it.canView(AppModule.CO_SCHOLASTIC) }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_EXAMS,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Exams & Admit Cards",
            titleHi = "परीक्षा व प्रवेश पत्र",
            controllingModule = AppModule.EXAMS,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_EXAMS,
            sortOrder = 40,
            icon = Icons.Default.EventNote,
            testTag = "section_card_exams",
            viewOnlyDescriptionEn = "View exam schedule, seating & admit cards",
            editDescriptionEn = "Schedule, rooms, seating, duties & admit PDF",
            viewOnlyDescriptionHi = "परीक्षा समय-सारिणी व प्रवेश पत्र देखें",
            editDescriptionHi = "परीक्षा कार्यक्रम, कक्ष, ड्यूटी व प्रवेश पत्र PDF",
            canEditResolver = { it.canEditExams },
            canAccess = { it.canViewExams }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_MY_EXAM_DUTIES,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "My Exam Duties",
            titleHi = "मेरी परीक्षा ड्यूटी",
            controllingModule = null,
            needsSchool = false,
            group = SectionCardGroup.ACADEMICS_EXAMS,
            sortOrder = 45,
            icon = Icons.Default.AssignmentInd,
            testTag = "section_card_my_exam_duties",
            viewOnlyDescriptionEn = "View your assigned invigilation room & timings",
            editDescriptionEn = "View your assigned invigilation room & timings",
            viewOnlyDescriptionHi = "अपनी कक्ष निरीक्षक ड्यूटी व समय देखें",
            editDescriptionHi = "अपनी कक्ष निरीक्षक ड्यूटी व समय देखें",
            canEditResolver = { false },
            canAccess = { state ->
                state.currentMember?.orgRole == OrgRole.TEACHER ||
                    state.resolveOwnLinkedTeacher() != null ||
                    state.ownExamDuties.isNotEmpty()
            },
            statusBadge = { state ->
                if (state.ownExamDuties.isNotEmpty()) {
                    "${state.ownExamDuties.size} duty" to false
                } else null
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_REPORTS,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Report Cards (A4 PDF)",
            titleHi = "रिपोर्ट कार्ड (A4 PDF)",
            controllingModule = AppModule.REPORTS,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_REPORTS,
            sortOrder = 50,
            icon = Icons.Default.Description,
            testTag = "section_card_reports",
            viewOnlyDescriptionEn = "Preview and export UT, Half-Yearly & Annual PDFs",
            editDescriptionEn = "UT slips, Half-Yearly, Annual & class ZIP",
            viewOnlyDescriptionHi = "UT, अर्धवार्षिक व वार्षिक PDF देखें",
            editDescriptionHi = "रिपोर्ट कार्ड, स्लिप्स व कक्षा ZIP निर्यात करें",
            canAccess = { it.canView(AppModule.REPORTS) }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_RESULT_ANALYSIS,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Result Analysis",
            titleHi = "रिज़ल्ट विश्लेषण",
            controllingModule = AppModule.RESULT_ANALYSIS,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_REPORTS,
            sortOrder = 60,
            icon = Icons.Default.Analytics,
            testTag = "section_card_result_analysis",
            viewOnlyDescriptionEn = "Toppers, subject averages, weak students & PDF",
            editDescriptionEn = "Toppers, subject averages, weak students & PDF",
            viewOnlyDescriptionHi = "टॉपर्स, विषय औसत, कमज़ोर छात्र व PDF",
            editDescriptionHi = "टॉपर्स, विषय औसत, कमज़ोर छात्र व PDF",
            canEditResolver = { false },
            canAccess = { it.canViewResultAnalysis }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_STUDENT_ATTENDANCE,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Student Attendance",
            titleHi = "छात्र उपस्थिति",
            controllingModule = AppModule.ATTENDANCE,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_DAILY,
            sortOrder = 70,
            icon = Icons.Default.FactCheck,
            testTag = "section_card_student_attendance",
            viewOnlyDescriptionEn = "View daily attendance, calendar & PDF reports",
            editDescriptionEn = "Mark daily roll, calendar & monthly PDF",
            viewOnlyDescriptionHi = "दैनिक उपस्थिति व मासिक रिपोर्ट देखें",
            editDescriptionHi = "दैनिक हाज़िरी दर्ज करें व PDF निकालें",
            canEditResolver = { it.canEdit(AppModule.ATTENDANCE) || (it.effectiveOwnTeacherScope?.classTeacherSections?.isNotEmpty() == true) },
            canAccess = { it.canAccessStudentAttendance }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_TIMETABLE,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Timetable & Substitutions",
            titleHi = "समय-सारिणी व प्रतिस्थापन",
            controllingModule = AppModule.TIMETABLE,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_DAILY,
            sortOrder = 80,
            icon = Icons.Default.CalendarMonth,
            testTag = "section_card_timetable",
            viewOnlyDescriptionEn = "View class/teacher schedules, leaves & A4 PDF",
            editDescriptionEn = "Edit schedules, clashes, substitutions & PDF",
            viewOnlyDescriptionHi = "कक्षा व शिक्षक समय-सारिणी देखें",
            editDescriptionHi = "समय-सारिणी, टकराव, प्रतिस्थापन व PDF",
            canAccess = { it.canView(AppModule.TIMETABLE) },
            statusBadge = { state ->
                val clashes = state.timetableClashes().size
                if (clashes > 0) "$clashes clash" to true else null
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_CLASS_DIARY,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Class Diary",
            titleHi = "कक्षा डायरी",
            controllingModule = AppModule.CLASS_DIARY,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_DAILY,
            sortOrder = 90,
            icon = Icons.Default.MenuBook,
            testTag = "section_card_class_diary",
            viewOnlyDescriptionEn = "View classwork, homework & weekly PDF",
            editDescriptionEn = "Classwork, homework, share & weekly PDF",
            viewOnlyDescriptionHi = "कक्षा कार्य, गृहकार्य व साप्ताहिक PDF देखें",
            editDescriptionHi = "कक्षा कार्य, गृहकार्य, व्हाट्सएप व साप्ताहिक PDF",
            canEditResolver = { it.canEdit(AppModule.CLASS_DIARY) || it.resolveOwnLinkedTeacher() != null },
            canAccess = { it.canViewClassDiary }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_TEACHER_ATTENDANCE,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Staff Attendance",
            titleHi = "स्टाफ़ उपस्थिति",
            controllingModule = AppModule.TIMETABLE,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_DAILY,
            sortOrder = 100,
            icon = Icons.Default.HowToReg,
            testTag = "section_card_teacher_attendance",
            viewOnlyDescriptionEn = "View daily staff attendance & monthly sheet",
            editDescriptionEn = "Daily staff roll, monthly register & A4 PDF",
            viewOnlyDescriptionHi = "दैनिक स्टाफ़ उपस्थिति व मासिक शीट देखें",
            editDescriptionHi = "दैनिक स्टाफ़ हाज़िरी व A4 PDF",
            canEditResolver = { it.canEdit(AppModule.TIMETABLE) },
            canAccess = { it.canView(AppModule.TIMETABLE) }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_TEACHERS,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Teachers & Setup",
            titleHi = "शिक्षक व असाइनमेंट",
            controllingModule = AppModule.TEACHERS,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_DAILY,
            sortOrder = 110,
            icon = Icons.Default.School,
            testTag = "section_card_teachers",
            viewOnlyDescriptionEn = "View teacher profiles, periods & subject load",
            editDescriptionEn = "Teachers, period structure & class assignments",
            viewOnlyDescriptionHi = "शिक्षक प्रोफाइल, पीरियड व विषय भार देखें",
            editDescriptionHi = "शिक्षक, पीरियड संरचना व कक्षा असाइनमेंट",
            canAccess = { it.canView(AppModule.TEACHERS) },
            statusBadge = { state ->
                val count = state.teachers.count { it.active }
                if (count > 0) "$count active" to false else null
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_WORKLOAD_REPORT,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "Workload Report",
            titleHi = "कार्यभार रिपोर्ट",
            controllingModule = AppModule.TIMETABLE,
            needsSchool = true,
            group = SectionCardGroup.ACADEMICS_DAILY,
            sortOrder = 120,
            icon = Icons.Default.Assessment,
            testTag = "section_card_workload_report",
            viewOnlyDescriptionEn = "Teacher periods, substitutions, PDF & CSV",
            editDescriptionEn = "Teacher periods, substitutions, PDF & CSV",
            viewOnlyDescriptionHi = "शिक्षक पीरियड, प्रतिस्थापन, PDF व CSV",
            editDescriptionHi = "शिक्षक पीरियड, प्रतिस्थापन, PDF व CSV",
            canEditResolver = { false },
            canAccess = { it.canView(AppModule.TIMETABLE) || it.canView(AppModule.TEACHERS) }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_TEACHER_PROFILE,
            parentSection = NavSection.ACADEMICS,
            isTopLevelDestination = false,
            titleEn = "My Teacher Profile",
            titleHi = "मेरा शिक्षक प्रोफाइल",
            controllingModule = null,
            needsSchool = false,
            group = SectionCardGroup.ACADEMICS_DAILY,
            sortOrder = 130,
            icon = Icons.Default.Person,
            testTag = "section_card_teacher_profile",
            viewOnlyDescriptionEn = "View or update your photo, contact & subjects",
            editDescriptionEn = "Update your photo, phone, classes & subjects",
            viewOnlyDescriptionHi = "अपना फोटो, मोबाइल, विषय व कक्षा अपडेट करें",
            editDescriptionHi = "अपना फोटो, मोबाइल, विषय व कक्षा अपडेट करें",
            canEditResolver = { true },
            canAccess = { state ->
                state.currentMember?.orgRole == OrgRole.TEACHER ||
                    state.resolveOwnLinkedTeacher() != null
            }
        ),

        // ==================== FINANCE & OPS SECTION SUB-SCREENS ====================
        RouteInfo(
            route = AppRoutes.ROUTE_FEE_MANAGEMENT,
            parentSection = NavSection.FINANCE_OPS,
            isTopLevelDestination = false,
            titleEn = "Fee Management",
            titleHi = "शुल्क प्रबंधन",
            controllingModule = AppModule.FEES,
            needsSchool = true,
            group = SectionCardGroup.FINANCE_ACCOUNTS,
            sortOrder = 10,
            icon = Icons.Default.AccountBalanceWallet,
            testTag = "section_card_fee_management",
            viewOnlyDescriptionEn = "View fee accounts, dues & receipt reports",
            editDescriptionEn = "Collect fees, receipts, structure & send queue",
            viewOnlyDescriptionHi = "शुल्क खाते, बकाया व रसीद रिपोर्ट देखें",
            editDescriptionHi = "शुल्क संग्रह, रसीद, बकाया व संदेश कतार",
            canEditResolver = { it.canEditFees },
            canAccess = { it.canViewFees }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_CASH_BOOK,
            parentSection = NavSection.FINANCE_OPS,
            isTopLevelDestination = false,
            titleEn = "Cash Book",
            titleHi = "रोकड़ बही",
            controllingModule = AppModule.CASH_BOOK,
            needsSchool = true,
            group = SectionCardGroup.FINANCE_ACCOUNTS,
            sortOrder = 20,
            icon = Icons.Default.AccountBalance,
            testTag = "section_card_cash_book",
            viewOnlyDescriptionEn = "View day book, fee income & closing reports",
            editDescriptionEn = "Expenses, fee income, day closing & PDF",
            viewOnlyDescriptionHi = "दैनिक रोकड़ बही व रिपोर्ट देखें",
            editDescriptionHi = "खर्च, शुल्क आय, दैनिक बंद व PDF",
            canEditResolver = { it.canEditCashBook },
            canAccess = { it.canViewCashBook }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_SALARY,
            parentSection = NavSection.FINANCE_OPS,
            isTopLevelDestination = false,
            titleEn = "Staff Salary",
            titleHi = "स्टाफ़ वेतन",
            controllingModule = AppModule.SALARY,
            needsSchool = true,
            group = SectionCardGroup.FINANCE_ACCOUNTS,
            sortOrder = 30,
            icon = Icons.Default.Payments,
            testTag = "section_card_salary",
            viewOnlyDescriptionEn = "View salary sheet, deductions & pay slips",
            editDescriptionEn = "Salary setup, monthly sheet, advances & slips",
            viewOnlyDescriptionHi = "वेतन शीट, कटौती व वेतन पर्ची देखें",
            editDescriptionHi = "वेतन सेटअप, मासिक शीट, अग्रिम व पर्ची",
            canEditResolver = { it.canEditSalary },
            canAccess = { it.canViewSalary }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_TRANSPORT,
            parentSection = NavSection.FINANCE_OPS,
            isTopLevelDestination = false,
            titleEn = "Transport",
            titleHi = "परिवहन",
            controllingModule = AppModule.TRANSPORT,
            needsSchool = true,
            group = SectionCardGroup.FINANCE_OPS,
            sortOrder = 40,
            icon = Icons.Default.DirectionsBus,
            testTag = "section_card_transport",
            viewOnlyDescriptionEn = "View routes, vehicles & student assignments",
            editDescriptionEn = "Routes, stops, vehicle docs & driver list",
            viewOnlyDescriptionHi = "रूट, वाहन व छात्र परिवहन सूची देखें",
            editDescriptionHi = "रूट, स्टॉप, वाहन कागज़ात व ड्राइवर सूची",
            canEditResolver = { it.canEditTransport },
            canAccess = { it.canViewTransport },
            statusBadge = { state ->
                val activeRoutes = state.transportRoutes.count {
                    it.schoolId == state.activeSchoolDocId && it.active
                }
                if (activeRoutes > 0) "$activeRoutes routes" to false else null
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_STOCK,
            parentSection = NavSection.FINANCE_OPS,
            isTopLevelDestination = false,
            titleEn = "Stock & Inventory",
            titleHi = "स्टॉक व सामग्री",
            controllingModule = AppModule.STOCK,
            needsSchool = true,
            group = SectionCardGroup.FINANCE_OPS,
            sortOrder = 50,
            icon = Icons.Default.Inventory2,
            testTag = "section_card_stock",
            viewOnlyDescriptionEn = "View stock summary & inventory PDF",
            editDescriptionEn = "Purchases, sales, issues & low-stock alerts",
            viewOnlyDescriptionHi = "स्टॉक सारांश व PDF देखें",
            editDescriptionHi = "खरीद, बिक्री व कम स्टॉक चेतावनी",
            canEditResolver = { it.canEditStock },
            canAccess = { it.canViewStock }
        ),

        // ==================== MANAGE SECTION SUB-SCREENS ====================
        RouteInfo(
            route = AppRoutes.ROUTE_MEMBERS,
            parentSection = NavSection.MANAGE,
            isTopLevelDestination = false,
            titleEn = "Members & Access",
            titleHi = "सदस्य व अनुमतियाँ",
            controllingModule = AppModule.MEMBERS,
            needsSchool = false,
            group = SectionCardGroup.MANAGE_ORG,
            sortOrder = 10,
            icon = Icons.Default.AdminPanelSettings,
            testTag = "section_card_members",
            viewOnlyDescriptionEn = "View member directory & role permissions",
            editDescriptionEn = "Approve join requests, invites, roles & logs",
            viewOnlyDescriptionHi = "सदस्य सूची व भूमिका अनुमतियाँ देखें",
            editDescriptionHi = "अनुरोध स्वीकृति, आमंत्रण कोड व भूमिकाएँ",
            canEditResolver = { it.canManageMembers() },
            canAccess = { it.canView(AppModule.MEMBERS) || it.isAdmin },
            statusBadge = { state ->
                if (state.pendingJoinRequestCount > 0) {
                    "${state.pendingJoinRequestCount} pending" to true
                } else {
                    val active = state.orgMembers.count { it.status == "active" }
                    if (active > 0) "$active members" to false else null
                }
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_SCHOOLS,
            parentSection = NavSection.MANAGE,
            isTopLevelDestination = false,
            titleEn = "Schools & Classes",
            titleHi = "स्कूल व कक्षाएँ",
            controllingModule = AppModule.SETTINGS,
            needsSchool = false,
            group = SectionCardGroup.MANAGE_ORG,
            sortOrder = 20,
            icon = Icons.Default.Apartment,
            testTag = "section_card_schools",
            viewOnlyDescriptionEn = "Manage school branches, logos & classes",
            editDescriptionEn = "Branches, logos, contact info & class setup",
            viewOnlyDescriptionHi = "स्कूल शाखाएँ, लोगो व कक्षाएँ",
            editDescriptionHi = "शाखाएँ, लोगो, संपर्क व कक्षा सेटअप",
            canEditResolver = { it.canEdit(AppModule.SETTINGS) },
            canAccess = { it.canEdit(AppModule.SETTINGS) },
            statusBadge = { state ->
                "${state.activeSchools.size} active" to false
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_SETTINGS,
            parentSection = NavSection.MANAGE,
            isTopLevelDestination = false,
            titleEn = "Settings & Account",
            titleHi = "सेटिंग्स व खाता",
            controllingModule = AppModule.SETTINGS,
            needsSchool = false,
            group = SectionCardGroup.MANAGE_ORG,
            sortOrder = 30,
            icon = Icons.Default.Settings,
            testTag = "section_card_settings",
            viewOnlyDescriptionEn = "Appearance, account, org switch & privacy",
            editDescriptionEn = "Sessions, pass %, subjects, theme & JSON backup",
            viewOnlyDescriptionHi = "थीम, खाता, संगठन बदलें व गोपनीयता",
            editDescriptionHi = "सत्र, उत्तीर्ण %, विषय, थीम व JSON बैकअप",
            canEditResolver = { it.canEdit(AppModule.SETTINGS) },
            canAccess = { true }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_SCHOOL_SUBSCRIPTION,
            parentSection = NavSection.MANAGE,
            isTopLevelDestination = false,
            titleEn = "Remove Ads / Subscription",
            titleHi = "विज्ञापन हटाएँ / सदस्यता",
            controllingModule = null,
            needsSchool = false,
            group = SectionCardGroup.MANAGE_DATA,
            sortOrder = 40,
            icon = Icons.Default.WorkspacePremium,
            testTag = "section_card_subscription",
            viewOnlyDescriptionEn = "View your school's ad-free subscription status",
            editDescriptionEn = "Ad-free school plans & billing status",
            viewOnlyDescriptionHi = "अपने स्कूल की विज्ञापन-मुक्त स्थिति देखें",
            editDescriptionHi = "विज्ञापन-मुक्त स्कूल प्लान व बिलिंग स्थिति",
            canEditResolver = { it.isOwner },
            canAccess = { true }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_BACKUP_EXPORT,
            parentSection = NavSection.MANAGE,
            isTopLevelDestination = false,
            titleEn = "Backup & Export (ZIP)",
            titleHi = "बैकअप व निर्यात (ZIP)",
            controllingModule = AppModule.SETTINGS,
            needsSchool = false,
            group = SectionCardGroup.MANAGE_DATA,
            sortOrder = 50,
            icon = Icons.Default.Backup,
            testTag = "section_card_backup_export",
            viewOnlyDescriptionEn = "Export full organisation ZIP backup",
            editDescriptionEn = "Full JSON / CSV ZIP backup & restore history",
            viewOnlyDescriptionHi = "संगठन डेटा ZIP बैकअप निर्यात करें",
            editDescriptionHi = "पूर्ण JSON / CSV ZIP बैकअप व इतिहास",
            canEditResolver = { it.canUseBackupExport },
            canAccess = { it.canUseBackupExport },
            statusBadge = { state ->
                val status = state.backupReminderStatus
                if (status.isOverdue30Days) {
                    "Overdue" to true
                } else {
                    null
                }
            }
        ),
        RouteInfo(
            route = AppRoutes.ROUTE_DESIGN_PREVIEW,
            parentSection = NavSection.MANAGE,
            isTopLevelDestination = false,
            titleEn = "Design Preview (Debug)",
            titleHi = "डिज़ाइन प्रीव्यू (Debug)",
            controllingModule = null,
            needsSchool = false,
            group = SectionCardGroup.MANAGE_TOOLS,
            sortOrder = 90,
            icon = Icons.Default.Palette,
            testTag = "section_card_design_preview",
            viewOnlyDescriptionEn = "Inspect D1 glass components, dark mode & 320dp layout",
            editDescriptionEn = "Inspect D1 glass components, dark mode & 320dp layout",
            viewOnlyDescriptionHi = "D1 ग्लास कंपोनेंट्स व लेआउट प्रीव्यू देखें",
            editDescriptionHi = "D1 ग्लास कंपोनेंट्स व लेआउट प्रीव्यू देखें",
            canEditResolver = { false },
            canAccess = { BuildConfig.DEBUG }
        )
    )

    private val byRoute: Map<String, RouteInfo> = allRoutes.associateBy { it.route }

    fun infoFor(route: String?): RouteInfo? = route?.let { byRoute[it] }

    fun isSubScreenRoute(route: String?): Boolean {
        val info = infoFor(route) ?: return true
        return !info.isTopLevelDestination
    }

    fun parentSectionFor(route: String?): NavSection =
        infoFor(route)?.parentSection ?: NavSection.HOME

    fun canAccessRoute(route: String, uiState: SchoolUiState): Boolean =
        infoFor(route)?.canAccess?.invoke(uiState) ?: false

    fun cardsForSection(section: NavSection, uiState: SchoolUiState): List<RouteInfo> =
        allRoutes
            .filter {
                !it.isTopLevelDestination &&
                    it.parentSection == section &&
                    it.canAccess(uiState)
            }
            .sortedBy { it.sortOrder }

    fun visibleSections(uiState: SchoolUiState): List<NavSection> =
        NavSection.entries.filter { section ->
            when (section) {
                NavSection.HOME -> true
                NavSection.MANAGE -> true
                NavSection.STUDENTS,
                NavSection.ACADEMICS,
                NavSection.FINANCE_OPS -> cardsForSection(section, uiState).isNotEmpty()
            }
        }

    fun buildBottomNavItems(
        uiState: SchoolUiState,
        isHindi: Boolean = uiState.phase10HindiLabels
    ): List<GlassBottomBarItem> {
        val canSeeMembersBadge = uiState.canView(AppModule.MEMBERS) || uiState.isAdmin
        return visibleSections(uiState).map { section ->
            val badge = if (section == NavSection.MANAGE && canSeeMembersBadge) {
                uiState.pendingJoinRequestCount
            } else {
                0
            }
            GlassBottomBarItem(
                id = section.route,
                label = if (isHindi) section.compactLabelHi else section.compactLabelEn,
                icon = section.unselectedIcon,
                selectedIcon = section.selectedIcon,
                contentDescription = if (isHindi) section.titleHi else section.titleEn,
                badgeCount = badge,
                testTag = section.navTestTag
            )
        }
    }
}
