package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AppDatabase
import com.example.data.ClassGroupEntity
import com.example.data.CoScholasticEntity
import com.example.data.FullBackupData
import com.example.data.LegacyBackupPayload
import com.example.data.LegacyImportTarget
import com.example.data.MarkEntity
import com.example.data.MarkUndoSnapshot
import com.example.data.ReportCardRepository
import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.SchoolSettingsEntity
import com.example.data.SchoolTemplateSource
import com.example.data.StudentEntity
import com.example.data.SubjectEntity
import com.example.data.TermInfoEntity
import com.example.data.cloud.CloudAuditLog
import com.example.data.cloud.CloudClassGroupItem
import com.example.data.cloud.CloudInviteCode
import com.example.data.cloud.CloudJoinRequest
import com.example.data.cloud.CloudMarkEditLog
import com.example.data.cloud.CloudMember
import com.example.data.cloud.CloudOrganisation
import com.example.data.cloud.CloudPhase4Snapshot
import com.example.data.cloud.CloudSchoolAcademicSnapshot
import com.example.data.cloud.CloudSchoolClassItem
import com.example.data.cloud.CloudSchoolRecord
import com.example.data.cloud.CloudSchoolsSnapshot
import com.example.data.cloud.CloudTimetableSnapshot
import com.example.data.cloud.FirestoreProvider
import com.example.data.cloud.MigrationProgress
import com.example.data.cloud.NetworkConnectivityMonitor
import com.example.data.cloud.OfflinePrefsStore
import com.example.data.cloud.OrgAcademicRepository
import com.example.data.cloud.OrgCloudRepository
import com.example.data.cloud.CloudPhase6Snapshot
import com.example.data.cloud.CloudPhase7Snapshot
import com.example.data.cloud.CloudPhase8Snapshot
import com.example.data.cloud.CloudPhase9Snapshot
import com.example.data.cloud.CloudPhase10Snapshot
import com.example.data.cloud.CloudPhase11Snapshot
import com.example.data.cloud.CloudPhase12Snapshot
import com.example.data.cloud.Phase4Repository
import com.example.data.cloud.Phase6Repository
import com.example.data.cloud.Phase7Repository
import com.example.data.cloud.Phase8Repository
import com.example.data.cloud.Phase9Repository
import com.example.data.cloud.Phase10Repository
import com.example.data.cloud.Phase11Repository
import com.example.data.cloud.Phase12Repository
import com.example.data.cloud.StableDocId
import com.example.data.cloud.TermAttendanceRecalcItem
import com.example.data.cloud.TimetableRepository
import com.example.domain.AdmissionEnquiryRecord
import com.example.domain.AppModule
import com.example.domain.CashBookAuditLogRecord
import com.example.domain.CashBookDayClosingRecord
import com.example.domain.CashBookEntryRecord
import com.example.domain.CashBookSettingsConfig
import com.example.domain.EnquirySettingsConfig
import com.example.domain.ExamInvigilatorDutyRecord
import com.example.domain.ExamRoomRecord
import com.example.domain.ExamScheduleRecord
import com.example.domain.ExamSeatingRecord
import com.example.domain.Phase10AccessEvaluator
import com.example.domain.Phase11AccessEvaluator
import com.example.domain.Phase12AccessEvaluator
import com.example.domain.Phase13TodayHomeEngine
import com.example.domain.TodayHomePermissions
import com.example.domain.TodayHomeSnapshot
import com.example.domain.SalaryAuditLogRecord
import com.example.domain.SalaryMonthLockRecord
import com.example.domain.SalaryMonthlyRecord
import com.example.domain.SalaryRuleConfig
import com.example.domain.SchoolCertificateRecord
import com.example.domain.StaffSalaryAdvanceRecord
import com.example.domain.StaffSalaryEngine
import com.example.domain.StaffSalaryProfileRecord
import com.example.domain.StockEngine
import com.example.domain.StockItemSummary
import com.example.domain.StockTransactionRecord
import com.example.domain.TransportRouteRecord
import com.example.domain.AssignmentWarning
import com.example.domain.AttendanceConflictNotice
import com.example.domain.AttendanceSummary
import com.example.domain.AutoGenerateConfig
import com.example.domain.AutoGenerateEngine
import com.example.domain.AutoGenerateProgress
import com.example.domain.AutoGenerateResult
import com.example.domain.ClassReportReadinessResult
import com.example.domain.ClassSectionAssignment
import com.example.domain.DayOfWeekCode
import com.example.domain.DefaultSchoolTemplate
import com.example.domain.DefaultSubjects
import com.example.domain.ExamType
import com.example.domain.LeaveReason
import com.example.domain.LeaveStatus
import com.example.domain.LocalPendingMarkEdit
import com.example.domain.LocalPendingPhase6Edit
import com.example.domain.LocalPendingTimetableEdit
import com.example.domain.MarkComponent
import com.example.domain.MarkConflictNotice
import com.example.domain.MarkValidationResult
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OfflineSyncEngine
import com.example.domain.OrgAccessEvaluator
import com.example.domain.OrgRole
import com.example.domain.PendingMarksEngine
import com.example.domain.PendingMarksExamSummary
import com.example.domain.PendingMarksItem
import com.example.domain.Phase4Validator
import com.example.domain.Phase6ConflictNotice
import com.example.domain.Phase6DateUtils
import com.example.domain.Phase7AccessEvaluator
import com.example.domain.ReportCalculations
import com.example.domain.ReportReadinessEngine
import com.example.domain.ReportType
import com.example.domain.ResultStatus
import com.example.domain.SchoolAcademicBundleForPending
import com.example.domain.SchoolPeriodStructure
import com.example.domain.ServerMarkObservation
import com.example.domain.SlipLayoutMode
import com.example.domain.StudentPerformanceReport
import com.example.domain.SubjectRule
import com.example.domain.SubstituteCandidate
import com.example.domain.SubstitutionEngine
import com.example.domain.SubstitutionNeedItem
import com.example.domain.SubstitutionRecord
import com.example.domain.SyncStatus
import com.example.domain.SyncUiState
import com.example.domain.TeacherAccessScope
import com.example.domain.TeacherAttendanceEngine
import com.example.domain.TeacherAttendanceRecord
import com.example.domain.TeacherAttendanceStatus
import com.example.domain.TeacherDailyAttendanceRow
import com.example.domain.TeacherLeaveRecord
import com.example.domain.TeacherMonthlyAttendanceSummaryRow
import com.example.domain.TeacherMyDayEngine
import com.example.domain.TeacherMyDaySummary
import com.example.domain.TeacherProfile
import com.example.domain.TeacherWorkloadReportRow
import com.example.domain.TeacherWorkloadSummary
import com.example.domain.TimetableCell
import com.example.domain.TimetableConflictNotice
import com.example.domain.TimetableEngine
import com.example.domain.TimetableHardConflict
import com.example.domain.TimetableUndoSnapshot
import com.example.domain.WorkloadReportEngine
import com.example.domain.WorkloadReportPeriodMode
import com.example.domain.DayCalendarStatus
import com.example.domain.EffectiveTermAttendance
import com.example.domain.LowAttendanceStudentRow
import com.example.domain.SchoolCalendarConfig
import com.example.domain.SchoolCalendarEngine
import com.example.domain.SchoolTodayAttendanceSummary
import com.example.domain.SectionDailyAttendanceStatus
import com.example.domain.StudentAttendanceAccessEvaluator
import com.example.domain.StudentAttendanceEditLog
import com.example.domain.StudentAttendanceEngine
import com.example.domain.StudentAttendanceEntry
import com.example.domain.StudentAttendanceSheet
import com.example.domain.StudentAttendanceStatus
import com.example.domain.StudentRangeAttendanceSummaryRow
import com.example.domain.StudentTermAttendanceComputation
import com.example.domain.WorkingDayExceptionEntry
import com.example.domain.FeeAuditLogRecord
import com.example.domain.FeeCollectionEngine
import com.example.domain.FeeMessageSentLog
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeStructureConfig
import com.example.domain.Phase9AccessEvaluator
import com.example.domain.SchoolNoticeRecord
import com.example.domain.StudentFeeAccountSummary
import com.example.domain.StudentFeeOverride
import com.example.util.BackupAndCsvManager
import com.example.util.ClassTimetablePrintBundle
import com.example.util.DailyAttendancePdfBundle
import com.example.util.LowAttendancePdfBundle
import com.example.util.MarksCsvIssue
import com.example.util.MasterClassSectionRow
import com.example.util.MasterTimetablePrintBundle
import com.example.util.MonthlyRegisterPdfBundle
import com.example.util.OperationType
import com.example.util.ParsedBackupResult
import com.example.util.PdfExportHelper
import com.example.util.PdfReportGenerator
import com.example.util.StudentAttendancePdfGenerator
import com.example.util.StudentReportBundle
import com.example.util.StudentSummaryRangePdfBundle
import com.example.util.SubstitutionPdfGenerator
import com.example.util.SubstitutionSlipPrintBundle
import com.example.util.TeacherAttendanceMonthlyPrintBundle
import com.example.util.TeacherAttendancePdfGenerator
import com.example.util.TeacherTimetablePrintBundle
import com.example.util.TimetablePdfGenerator
import com.example.util.TimetablePrintOrientation
import com.example.util.WorkloadReportPdfGenerator
import com.example.util.WorkloadReportPrintBundle
import com.example.util.friendlyFirestoreErrorMessage
import com.example.util.handleFirestoreError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class FilterSelection(
    val schoolId: Long = 0L,
    val session: String = "2026-2027",
    val className: String = "",
    val section: String = "A",
    val selectedStudentId: Long? = null
)

data class PdfPreviewState(
    val isGenerating: Boolean = false,
    val statusLabel: String = "",
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val generatedFile: File? = null,
    val generatedZipFile: File? = null,
    val totalPages: Int = 0,
    val previewPages: List<Bitmap> = emptyList(),
    val reportType: ReportType = ReportType.ANNUAL,
    val batchScope: Boolean = false,
    val error: String? = null
)

data class TimetablePdfPreviewState(
    val isGenerating: Boolean = false,
    val title: String = "",
    val statusLabel: String = "",
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val generatedFile: File? = null,
    val generatedZipFile: File? = null,
    val totalPages: Int = 0,
    val previewPages: List<Bitmap> = emptyList(),
    val isLandscape: Boolean = true,
    val error: String? = null
)

data class MarksCsvImportPreview(
    val validMarks: List<MarkEntity>,
    val matchedStudentCount: Int,
    val processedRowCount: Int,
    val skippedEmptyCells: Int,
    val issues: List<MarksCsvIssue>
)

enum class StudentAcademicStatus(val label: String) {
    PASS("Pass"),
    NEEDS_ATTENTION("Needs Attention"),
    PENDING("Pending")
}

data class StudentDashboardSummary(
    val student: StudentEntity,
    val schoolName: String,
    val classGroupLabel: String,
    val overallPercent: Double,
    val overallGrade: String,
    val status: StudentAcademicStatus,
    val hasMarks: Boolean,
    val classRank: Int?
)

data class SchoolUiState(
    val isInitialized: Boolean = false,
    val schools: List<SchoolEntity> = emptyList(),
    val classGroups: List<ClassGroupEntity> = emptyList(),
    val schoolClasses: List<SchoolClassEntity> = emptyList(),
    val settings: SchoolSettingsEntity = SchoolSettingsEntity(),
    val filter: FilterSelection = FilterSelection(),
    val subjects: List<SubjectEntity> = emptyList(),
    val allStudents: List<StudentEntity> = emptyList(),
    val allMarks: List<MarkEntity> = emptyList(),
    val allCoScholastic: List<CoScholasticEntity> = emptyList(),
    val allTermInfos: List<TermInfoEntity> = emptyList(),
    val sampleStudentCount: Int = 0,
    val legacyMarksCount: Int = 0,
    val lastMarkUndo: MarkUndoSnapshot? = null,
    // Phase 2 Multi-Tenant & Cloud State (defaults keep Phase 1 unit tests 100% compatible)
    val activeOrg: CloudOrganisation? = null,
    val currentMember: CloudMember? = null,
    val orgMembers: List<CloudMember> = emptyList(),
    val joinRequests: List<CloudJoinRequest> = emptyList(),
    val inviteCodes: List<CloudInviteCode> = emptyList(),
    val auditLogs: List<CloudAuditLog> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.ONLINE,
    val syncUiState: SyncUiState = SyncUiState(),
    val isOnline: Boolean = true,
    val markEditLogs: List<CloudMarkEditLog> = emptyList(),
    val markConflicts: List<MarkConflictNotice> = emptyList(),
    val membershipRevoked: Boolean = false,
    val schoolDocIdByLongId: Map<Long, String> = emptyMap(),
    // Phase 4: Teachers, Period Structure, Class-Subject-Teacher Assignments, Subject Rules
    val teachers: List<TeacherProfile> = emptyList(),
    val periodStructuresBySchool: Map<String, SchoolPeriodStructure> = emptyMap(),
    val classAssignments: List<ClassSectionAssignment> = emptyList(),
    val subjectRules: List<SubjectRule> = emptyList(),
    // Phase 5: Timetable cells, mirrored layouts, and latest-edit-wins cell conflicts
    val timetableCells: List<TimetableCell> = emptyList(),
    val timetableLayoutsBySchool: Map<String, SchoolPeriodStructure> = emptyMap(),
    val timetableConflicts: List<TimetableConflictNotice> = emptyList(),
    // Phase 6: Auto-generate undo snapshots, teacher leaves, substitutions, and leave/sub conflicts
    val teacherLeaves: List<TeacherLeaveRecord> = emptyList(),
    val substitutions: List<SubstitutionRecord> = emptyList(),
    val undoSnapshots: List<TimetableUndoSnapshot> = emptyList(),
    val ownLinkedTeacher: TeacherProfile? = null,
    val phase6Conflicts: List<Phase6ConflictNotice> = emptyList(),
    // Phase 7: Teacher access scopes, multi-school pending marks, teacher attendance & school report links
    val ownTeacherScope: TeacherAccessScope? = null,
    val allTeacherScopes: Map<String, TeacherAccessScope> = emptyMap(),
    val teacherAttendanceRecords: List<TeacherAttendanceRecord> = emptyList(),
    val multiSchoolPendingBundles: List<SchoolAcademicBundleForPending> = emptyList(),
    val schoolRecordsByDocId: Map<String, CloudSchoolRecord> = emptyMap(),
    // Phase 8: School Calendar & Student Attendance (strictly separate from Teacher Attendance)
    val schoolCalendars: List<SchoolCalendarConfig> = emptyList(),
    val studentAttendanceSheets: List<StudentAttendanceSheet> = emptyList(),
    val studentAttendanceEditLogs: List<StudentAttendanceEditLog> = emptyList(),
    val attendanceConflictNotices: List<AttendanceConflictNotice> = emptyList(),
    val termInfoManualOverrideByStudentTerm: Map<Pair<Long, Int>, Boolean?> = emptyMap(),
    val studentDocIdByLongId: Map<Long, String> = emptyMap(),
    // Phase 9: Student Register & Fee Management
    val feeStructures: List<FeeStructureConfig> = emptyList(),
    val studentFeeOverrides: List<StudentFeeOverride> = emptyList(),
    val feeReceipts: List<FeeReceiptRecord> = emptyList(),
    val feeMessageSentLogs: List<FeeMessageSentLog> = emptyList(),
    val schoolNotices: List<SchoolNoticeRecord> = emptyList(),
    val feeAuditLogs: List<FeeAuditLogRecord> = emptyList(),
    val deviceNodeCode: String = "A1",
    // Phase 10: Certificates, Cash Book & Admission Enquiry
    val schoolCertificates: List<SchoolCertificateRecord> = emptyList(),
    val cashBookSettingsList: List<CashBookSettingsConfig> = emptyList(),
    val cashBookEntries: List<CashBookEntryRecord> = emptyList(),
    val cashBookDayClosings: List<CashBookDayClosingRecord> = emptyList(),
    val cashBookAuditLogs: List<CashBookAuditLogRecord> = emptyList(),
    val cashBookFeeReceipts: List<FeeReceiptRecord> = emptyList(),
    val admissionEnquiries: List<AdmissionEnquiryRecord> = emptyList(),
    val enquirySettingsList: List<EnquirySettingsConfig> = emptyList(),
    val phase10HindiLabels: Boolean = true,
    // Phase 11: Exam Schedule & Admit Cards, Stock, Transport Routes
    val examSchedules: List<ExamScheduleRecord> = emptyList(),
    val examRooms: List<ExamRoomRecord> = emptyList(),
    val examSeatings: List<ExamSeatingRecord> = emptyList(),
    val examDuties: List<ExamInvigilatorDutyRecord> = emptyList(),
    val stockTransactions: List<StockTransactionRecord> = emptyList(),
    val transportRoutes: List<TransportRouteRecord> = emptyList(),
    val phase11FeeStructures: List<FeeStructureConfig> = emptyList(),
    val phase11FeeReceipts: List<FeeReceiptRecord> = emptyList(),
    val phase11FeeOverrides: List<StudentFeeOverride> = emptyList(),
    val phase11Teachers: List<TeacherProfile> = emptyList(),
    val phase11TeacherLeaves: List<TeacherLeaveRecord> = emptyList(),
    // Phase 12: Staff Salary (Owner-only by default; explicit Owner grant for Admin; optional self-slip view for Teachers)
    val salaryRuleConfigs: List<SalaryRuleConfig> = emptyList(),
    val staffSalaryProfiles: List<StaffSalaryProfileRecord> = emptyList(),
    val salaryMonthlyRecords: List<SalaryMonthlyRecord> = emptyList(),
    val staffSalaryAdvances: List<StaffSalaryAdvanceRecord> = emptyList(),
    val salaryMonthLocks: List<SalaryMonthLockRecord> = emptyList(),
    val salaryAuditLogs: List<SalaryAuditLogRecord> = emptyList(),
    val ownTeacherSalarySlips: List<SalaryMonthlyRecord> = emptyList(),
    val phase12Teachers: List<TeacherProfile> = emptyList(),
    val phase12TeacherAttendance: List<TeacherAttendanceRecord> = emptyList(),
    val phase12TeacherLeaves: List<TeacherLeaveRecord> = emptyList(),
    // Phase 13: "Today" Home Screen & First-Time Setup Guide
    val isFirstTimeGuideSkipped: Boolean = false
) {
    val canViewFees: Boolean
        get() = activeOrg == null || Phase9AccessEvaluator.canViewFees(accessProfile)

    val canEditFees: Boolean
        get() = activeOrg == null || Phase9AccessEvaluator.canEditFees(accessProfile)

    val canViewStudentRegister: Boolean
        get() = activeOrg == null || Phase9AccessEvaluator.canViewStudentRegister(accessProfile)

    val canEditStudentRegister: Boolean
        get() = activeOrg == null || Phase9AccessEvaluator.canEditStudentRegister(accessProfile)

    val canViewCertificates: Boolean
        get() = activeOrg == null || Phase10AccessEvaluator.canViewCertificates(accessProfile)

    val canEditCertificates: Boolean
        get() = activeOrg == null || Phase10AccessEvaluator.canEditCertificates(accessProfile)

    val canViewCashBook: Boolean
        get() = activeOrg == null || Phase10AccessEvaluator.canViewCashBook(accessProfile)

    val canEditCashBook: Boolean
        get() = activeOrg == null || Phase10AccessEvaluator.canEditCashBook(accessProfile)

    val canViewEnquiry: Boolean
        get() = activeOrg == null || Phase10AccessEvaluator.canViewEnquiry(accessProfile)

    val canEditEnquiry: Boolean
        get() = activeOrg == null || Phase10AccessEvaluator.canEditEnquiry(accessProfile)

    val canViewExams: Boolean
        get() = activeOrg == null || Phase11AccessEvaluator.canViewExams(accessProfile)

    val canEditExams: Boolean
        get() = activeOrg == null || Phase11AccessEvaluator.canEditExams(accessProfile)

    val canViewStock: Boolean
        get() = activeOrg == null || Phase11AccessEvaluator.canViewStock(accessProfile)

    val canEditStock: Boolean
        get() = activeOrg == null || Phase11AccessEvaluator.canEditStock(accessProfile)

    val canViewTransport: Boolean
        get() = activeOrg == null || Phase11AccessEvaluator.canViewTransport(accessProfile)

    val canEditTransport: Boolean
        get() = activeOrg == null || Phase11AccessEvaluator.canEditTransport(accessProfile)

    val canViewSalary: Boolean
        get() = activeOrg == null || Phase12AccessEvaluator.canViewSalary(accessProfile)

    val canEditSalary: Boolean
        get() = activeOrg == null || Phase12AccessEvaluator.canEditSalary(accessProfile)

    val isSalaryOwner: Boolean
        get() = activeOrg == null || OrgAccessEvaluator.isOwner(accessProfile)

    val effectiveCashBookFeeReceipts: List<FeeReceiptRecord>
        get() = (feeReceipts + cashBookFeeReceipts).distinctBy { it.id }

    val effectivePhase11FeeStructures: List<FeeStructureConfig>
        get() = (feeStructures + phase11FeeStructures).distinctBy { it.id }

    val effectivePhase11FeeReceipts: List<FeeReceiptRecord>
        get() = (feeReceipts + cashBookFeeReceipts + phase11FeeReceipts).distinctBy { it.id }

    val effectivePhase11FeeOverrides: List<StudentFeeOverride>
        get() = (studentFeeOverrides + phase11FeeOverrides).distinctBy { it.id }

    val effectivePhase11Teachers: List<TeacherProfile>
        get() = (teachers + phase11Teachers).distinctBy { it.id }

    val effectivePhase11TeacherLeaves: List<TeacherLeaveRecord>
        get() = (teacherLeaves + phase11TeacherLeaves).distinctBy { it.id }

    val effectivePhase12Teachers: List<TeacherProfile>
        get() = (teachers + phase11Teachers + phase12Teachers).distinctBy { it.id }

    val effectivePhase12TeacherAttendance: List<TeacherAttendanceRecord>
        get() = (teacherAttendanceRecords + phase12TeacherAttendance).distinctBy { it.id }

    val effectivePhase12TeacherLeaves: List<TeacherLeaveRecord>
        get() = (teacherLeaves + phase11TeacherLeaves + phase12TeacherLeaves).distinctBy { it.id }

    fun salaryRuleConfigForSchool(schoolDocId: String = activeSchoolDocId): SalaryRuleConfig =
        salaryRuleConfigs.firstOrNull { it.schoolId == schoolDocId }
            ?: SalaryRuleConfig(
                id = SalaryRuleConfig.defaultdocId(schoolDocId),
                schoolId = schoolDocId
            )

    fun isSalaryMonthLocked(schoolDocId: String = activeSchoolDocId, yearMonth: String): Boolean =
        salaryMonthLocks.firstOrNull { it.schoolId == schoolDocId && it.yearMonth == yearMonth }?.locked == true

    val ownExamDuties: List<ExamInvigilatorDutyRecord>
        get() {
            val myUid = currentMember?.uid.orEmpty()
            val myTeacherId = ownLinkedTeacher?.id.orEmpty()
            if (myUid.isBlank() && myTeacherId.isBlank()) return emptyList()
            return examDuties
                .filter { d ->
                    (myUid.isNotBlank() && d.teacherUid == myUid) ||
                        (myTeacherId.isNotBlank() && d.teacherId == myTeacherId)
                }
                .sortedWith(compareBy({ it.dateIso }, { it.startTime }, { it.roomName }))
        }

    fun feeStructureForPhase11(
        schoolDocId: String = activeSchoolDocId,
        session: String = filter.session
    ): FeeStructureConfig {
        val saved = effectivePhase11FeeStructures.firstOrNull {
            it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true)
        }
        return saved ?: feeStructureForSchool(schoolDocId, session)
    }

    fun stockSummariesForSchool(
        schoolDocId: String = activeSchoolDocId,
        session: String = filter.session
    ): List<StockItemSummary> {
        val structure = feeStructureForPhase11(schoolDocId, session)
        return StockEngine.computeStockSummary(
            schoolId = schoolDocId,
            session = session,
            catalogue = structure.optionalCatalogue,
            transactions = stockTransactions,
            feeReceipts = effectivePhase11FeeReceipts
        )
    }

    /**
     * Phase 13: Builds the role-aware Today Home snapshot purely from cached in-memory state.
     * Reuses Phases 6–12 engines directly without modifying any calculation or issuing network calls.
     */
    fun buildTodayHomeSnapshot(todayIso: String = Phase6DateUtils.todayIso()): TodayHomeSnapshot {
        val activeSchDocId = activeSchoolDocId
        val activeSch = activeSchool
        val activeCls = classesForSchool(activeSch.id)
        val activeStu = sessionStudents
        val activeStuWithDocIds = activeStu.map { st -> studentDocIdFor(st.id) to st }

        val permissions = TodayHomePermissions(
            isOwnerOrAdmin = activeOrg == null || isAdmin,
            isTeacher = currentMember?.orgRole == OrgRole.TEACHER,
            canAccessStudentAttendance = canAccessStudentAttendance,
            canViewAttendanceOverview = activeOrg == null || isAdmin || canView(AppModule.ATTENDANCE),
            canViewFees = canViewFees,
            canEditFees = canEditFees,
            canViewCashBook = canViewCashBook,
            canViewTimetable = canView(AppModule.TIMETABLE),
            canViewTeachers = canView(AppModule.TEACHERS),
            canViewEnquiry = canViewEnquiry,
            canViewStock = canViewStock,
            canViewExams = canViewExams,
            canViewMarks = canView(AppModule.MARKS),
            canEditMarks = canEdit(AppModule.MARKS),
            canViewStudents = canView(AppModule.STUDENTS),
            canEditStudents = canEdit(AppModule.STUDENTS),
            canViewStudentRegister = canViewStudentRegister,
            canViewCertificates = canViewCertificates,
            canEditSettings = canEdit(AppModule.SETTINGS)
        )

        val attendanceSummaries = if (permissions.canAccessStudentAttendance) {
            computeTodayAttendanceSummaries(todayIso)
        } else {
            emptyList()
        }

        val ownTeacher = resolveOwnLinkedTeacher()
        val ownScope = effectiveOwnTeacherScope
        val ownPendingCount = if (permissions.canViewMarks && (permissions.isTeacher || ownTeacher != null)) {
            computePendingMarksItems().size
        } else {
            0
        }

        return Phase13TodayHomeEngine.buildSnapshot(
            todayIso = todayIso,
            member = accessProfile,
            permissions = permissions,
            schools = schools,
            activeSchool = if (schools.isNotEmpty()) activeSch else null,
            activeSchoolDocId = activeSchDocId,
            schoolNamesByDocId = schoolNamesByDocId,
            session = filter.session,
            schoolClassesForActiveSchool = activeCls,
            sessionStudentsForActiveSchool = activeStu,
            studentsWithDocIdsForActiveSchool = activeStuWithDocIds,
            todayAttendanceSummaries = attendanceSummaries,
            feeStructures = effectivePhase11FeeStructures,
            activeFeeStructure = feeStructureForPhase11(activeSchDocId, filter.session),
            studentFeeOverrides = effectivePhase11FeeOverrides,
            feeReceipts = effectivePhase11FeeReceipts,
            feeMessageSentLogs = feeMessageSentLogs,
            cashBookEntries = cashBookEntries,
            cashBookDayClosings = cashBookDayClosings,
            cashBookSettings = cashBookSettingsForSchool(activeSchDocId),
            salaryMonthlyRecords = salaryMonthlyRecords,
            admissionEnquiries = admissionEnquiries,
            enquirySettings = enquirySettingsForSchool(activeSchDocId),
            teachers = effectivePhase12Teachers,
            teacherLeaves = effectivePhase12TeacherLeaves,
            teacherAttendanceRecords = effectivePhase12TeacherAttendance,
            timetableCells = timetableCells,
            substitutions = substitutions,
            periodStructuresBySchool = effectivePeriodStructuresBySchool,
            classAssignments = classAssignments,
            ownLinkedTeacher = ownTeacher,
            ownTeacherScope = ownScope,
            ownPendingMarksCount = ownPendingCount,
            ownExamDuties = ownExamDuties,
            ownSalarySlipsCount = ownTeacherSalarySlips.size,
            examSchedules = examSchedules,
            examDuties = examDuties,
            stockTransactions = stockTransactions,
            isFirstTimeGuideSkipped = isFirstTimeGuideSkipped
        )
    }

    fun cashBookSettingsForSchool(schoolDocId: String = activeSchoolDocId): CashBookSettingsConfig =
        cashBookSettingsList.firstOrNull { it.schoolId == schoolDocId }
            ?: CashBookSettingsConfig.buildDefault(schoolDocId)

    fun enquirySettingsForSchool(schoolDocId: String = activeSchoolDocId): EnquirySettingsConfig =
        enquirySettingsList.firstOrNull { it.schoolId == schoolDocId }
            ?: EnquirySettingsConfig.buildDefault(schoolDocId)

    fun feeStructureForSchool(
        schoolDocId: String = activeSchoolDocId,
        session: String = filter.session
    ): FeeStructureConfig {
        val saved = feeStructures.firstOrNull {
            it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true)
        }
        if (saved != null) return saved
        val schLongId = schoolRecordsByDocId[schoolDocId]?.stableId ?: activeSchool.id
        val clsNames = classesForSchool(schLongId).map { it.name }
        return FeeStructureConfig.buildDefault(schoolDocId, session, clsNames)
    }

    fun feeOverrideForStudent(
        studentDocId: String,
        schoolDocId: String = activeSchoolDocId,
        session: String = filter.session
    ): StudentFeeOverride? = studentFeeOverrides.firstOrNull {
        it.schoolId == schoolDocId &&
            it.session.equals(session, ignoreCase = true) &&
            it.studentId == studentDocId
    }

    fun feeReceiptsForSchoolSession(
        schoolDocId: String = activeSchoolDocId,
        session: String = filter.session
    ): List<FeeReceiptRecord> = feeReceipts.filter {
        it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true)
    }

    fun computeStudentFeeAccount(student: StudentEntity): StudentFeeAccountSummary {
        val schDocId = schoolDocIdByLongId[student.schoolId] ?: activeSchoolDocId
        val stuDocId = studentDocIdFor(student.id)
        val struct = feeStructureForSchool(schDocId, student.session)
        val override = feeOverrideForStudent(stuDocId, schDocId, student.session)
        return FeeCollectionEngine.computeStudentFeeAccount(
            structure = struct,
            student = student,
            studentDocId = stuDocId,
            override = override,
            receiptsForStudent = feeReceipts
        )
    }

    val activeSchoolDocId: String
        get() = schoolDocIdByLongId[activeSchool.id].orEmpty()

    val activeSchoolCloudRecord: CloudSchoolRecord?
        get() = schoolRecordsByDocId[activeSchoolDocId]

    val activeSchoolPrintClassTeacherName: Boolean
        get() = activeSchoolCloudRecord?.printClassTeacherNameOnReports ?: false

    val activeSchoolCheckReportReady: Boolean
        get() = activeSchoolCloudRecord?.checkReportCardsReadyBeforeBatch ?: false

    val activeSchoolOpenExams: List<String>
        get() = activeSchoolCloudRecord?.openExamsForTeachers ?: Phase7AccessEvaluator.ALL_EXAM_KEYS

    val activeSchoolAutoFillAttendance: Boolean
        get() = activeSchoolCloudRecord?.autoFillReportAttendance ?: false

    val activeSchoolClassTeacherDaysBack: Int
        get() = activeSchoolCloudRecord?.classTeacherAttendanceEditDaysBack
            ?: StudentAttendanceAccessEvaluator.DEFAULT_CLASS_TEACHER_DAYS_BACK

    val canAccessStudentAttendance: Boolean
        get() = activeOrg == null || StudentAttendanceAccessEvaluator.canAccessStudentAttendanceModule(
            member = accessProfile,
            teacherScope = effectiveOwnTeacherScope
        )

    fun canEditSchoolCalendar(schoolDocId: String): Boolean =
        activeOrg == null || StudentAttendanceAccessEvaluator.canEditSchoolCalendar(accessProfile, schoolDocId)

    fun canViewSectionAttendance(schoolDocId: String, className: String, section: String): Boolean =
        activeOrg == null || StudentAttendanceAccessEvaluator.canViewSectionAttendance(
            member = accessProfile,
            teacherScope = effectiveOwnTeacherScope,
            schoolId = schoolDocId,
            className = className,
            section = section
        )

    fun canEditSectionAttendance(schoolDocId: String, className: String, section: String): Boolean =
        activeOrg == null || StudentAttendanceAccessEvaluator.canEditSectionAttendance(
            member = accessProfile,
            teacherScope = effectiveOwnTeacherScope,
            schoolId = schoolDocId,
            className = className,
            section = section
        )

    fun hasSavedCalendarForSchool(schoolDocId: String, session: String = filter.session): Boolean =
        schoolCalendars.any { it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true) }

    fun calendarForSchool(schoolDocId: String, session: String = filter.session): SchoolCalendarConfig {
        val saved = schoolCalendars.firstOrNull {
            it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true)
        }
        if (saved != null) return saved
        val ps = periodStructuresBySchool[schoolDocId] ?: timetableLayoutsBySchool[schoolDocId]
        return SchoolCalendarEngine.buildDefaultCalendar(schoolDocId, session, ps)
    }

    fun studentDocIdFor(studentId: Long): String = studentDocIdByLongId[studentId].orEmpty()

    fun computeStudentTermAttendance(studentId: Long, termNo: Int): StudentTermAttendanceComputation {
        val student = allStudents.firstOrNull { it.id == studentId }
        val schDocId = student?.let { schoolDocIdByLongId[it.schoolId] } ?: activeSchoolDocId
        val ses = student?.session ?: filter.session
        val cal = calendarForSchool(schDocId, ses)
        val stuDocId = studentDocIdFor(studentId)
        if (stuDocId.isBlank() || schDocId.isBlank()) {
            val (s, e) = SchoolCalendarEngine.termDateRange(cal, termNo)
            val stats = SchoolCalendarEngine.countWorkingDaysInRange(cal, s, e)
            return StudentTermAttendanceComputation(
                termNo = termNo,
                rawDaysPresent = 0.0,
                roundedDaysPresent = 0,
                totalWorkingDays = stats.workingDays,
                markedDays = 0,
                presentCount = 0,
                lateCount = 0,
                halfDayCount = 0,
                leaveCount = 0,
                absentCount = 0
            )
        }
        return StudentAttendanceEngine.computeTermAttendance(
            studentDocId = stuDocId,
            schoolId = schDocId,
            session = ses,
            termNo = termNo,
            calendar = cal,
            sheets = studentAttendanceSheets
        )
    }

    fun effectiveTermAttendanceForStudent(studentId: Long, termNo: Int): EffectiveTermAttendance {
        val student = allStudents.firstOrNull { it.id == studentId }
        val schDocId = student?.let { schoolDocIdByLongId[it.schoolId] } ?: activeSchoolDocId
        val autoFillOn = schoolRecordsByDocId[schDocId]?.autoFillReportAttendance ?: false
        val stored = termInfoForStudent(studentId, termNo)
        val manualFlag = termInfoManualOverrideByStudentTerm[studentId to termNo]
        val daily = computeStudentTermAttendance(studentId, termNo)
        return StudentAttendanceEngine.resolveEffectiveTermAttendance(
            autoFillSwitchOn = autoFillOn,
            storedTermInfo = stored,
            storedManualFlag = manualFlag,
            dailyComputation = daily
        )
    }

    fun computeTodayAttendanceSummaries(todayIso: String = Phase6DateUtils.todayIso()): List<SchoolTodayAttendanceSummary> {
        val visibleSchools = schools.filter { sch ->
            val docId = schoolDocIdByLongId[sch.id].orEmpty()
            docId.isNotBlank() && OrgAccessEvaluator.canAccessSchool(accessProfile, docId)
        }
        return visibleSchools.map { sch ->
            val schDocId = schoolDocIdByLongId[sch.id].orEmpty()
            val cal = calendarForSchool(schDocId, filter.session)
            val dayStatus = SchoolCalendarEngine.dayStatus(cal, todayIso)
            val schStudents = allStudents.filter { it.schoolId == sch.id && it.session == filter.session }
            val sectionsFromStudents = schStudents
                .map { it.className to it.section.uppercase().ifBlank { "A" } }
                .distinct()
            val sectionsFromAssignments = classAssignments
                .filter { it.schoolId == schDocId }
                .map { it.className to it.section.uppercase().ifBlank { "A" } }
                .distinct()
            val allSections = (sectionsFromStudents + sectionsFromAssignments).distinct()
                .sortedWith(compareBy({ it.first.lowercase() }, { it.second }))

            val sectionStatuses = allSections.map { (cls, sec) ->
                val secStudentsCount = schStudents.count {
                    it.className.equals(cls, ignoreCase = true) && it.section.equals(sec, ignoreCase = true)
                }
                val sheet = studentAttendanceSheets.firstOrNull {
                    it.schoolId == schDocId &&
                        it.session.equals(filter.session, ignoreCase = true) &&
                        it.className.equals(cls, ignoreCase = true) &&
                        it.section.equals(sec, ignoreCase = true) &&
                        it.date == todayIso
                }
                SectionDailyAttendanceStatus(
                    schoolDocId = schDocId,
                    schoolName = sch.name,
                    className = cls,
                    section = sec,
                    date = todayIso,
                    taken = sheet != null,
                    totalStudents = sheet?.totalStudents?.takeIf { it > 0 } ?: secStudentsCount,
                    presentEquivalent = (sheet?.presentCount ?: 0) + (sheet?.lateCount ?: 0),
                    halfDayCount = sheet?.halfDayCount ?: 0,
                    absentCount = sheet?.absentCount ?: 0,
                    leaveCount = sheet?.leaveCount ?: 0,
                    updatedByName = sheet?.updatedByName.orEmpty()
                )
            }
            val takenList = sectionStatuses.filter { it.taken }
            val totalMarked = takenList.sumOf { it.totalStudents }
            val weightedPresent = takenList.sumOf { it.presentEquivalent.toDouble() + it.halfDayCount * 0.5 }
            SchoolTodayAttendanceSummary(
                schoolDocId = schDocId,
                schoolName = sch.name,
                date = todayIso,
                dayStatus = dayStatus,
                totalSections = sectionStatuses.size,
                sectionsTaken = takenList.size,
                totalMarkedStudents = totalMarked,
                weightedPresentStudents = weightedPresent,
                sectionStatuses = sectionStatuses
            )
        }
    }

    fun computeClassTeacherTodayCards(todayIso: String = Phase6DateUtils.todayIso()): List<SectionDailyAttendanceStatus> {
        val scope = effectiveOwnTeacherScope ?: return emptyList()
        return scope.classTeacherSections.mapNotNull { key ->
            val parts = key.split("|")
            if (parts.size != 3) return@mapNotNull null
            val (schDocId, cls, sec) = parts
            if (!OrgAccessEvaluator.canAccessSchool(accessProfile, schDocId)) return@mapNotNull null
            val schName = schoolRecordsByDocId[schDocId]?.name ?: activeSchool.name
            val schLongId = schoolRecordsByDocId[schDocId]?.stableId ?: activeSchool.id
            val secStudentsCount = allStudents.count {
                it.schoolId == schLongId &&
                    it.session == filter.session &&
                    it.className.equals(cls, ignoreCase = true) &&
                    it.section.equals(sec, ignoreCase = true)
            }
            val sheet = studentAttendanceSheets.firstOrNull {
                it.schoolId == schDocId &&
                    it.session.equals(filter.session, ignoreCase = true) &&
                    it.className.equals(cls, ignoreCase = true) &&
                    it.section.equals(sec, ignoreCase = true) &&
                    it.date == todayIso
            }
            SectionDailyAttendanceStatus(
                schoolDocId = schDocId,
                schoolName = schName,
                className = cls,
                section = sec,
                date = todayIso,
                taken = sheet != null,
                totalStudents = sheet?.totalStudents?.takeIf { it > 0 } ?: secStudentsCount,
                presentEquivalent = (sheet?.presentCount ?: 0) + (sheet?.lateCount ?: 0),
                halfDayCount = sheet?.halfDayCount ?: 0,
                absentCount = sheet?.absentCount ?: 0,
                leaveCount = sheet?.leaveCount ?: 0,
                updatedByName = sheet?.updatedByName.orEmpty()
            )
        }
    }

    fun computeSectionRangeSummary(
        schoolDocId: String,
        className: String,
        section: String,
        startDate: String,
        endDate: String
    ): List<StudentRangeAttendanceSummaryRow> {
        val schLongId = schoolRecordsByDocId[schoolDocId]?.stableId ?: activeSchool.id
        val cal = calendarForSchool(schoolDocId, filter.session)
        val students = allStudents
            .filter {
                it.schoolId == schLongId &&
                    it.session == filter.session &&
                    it.className.equals(className, ignoreCase = true) &&
                    it.section.equals(section, ignoreCase = true)
            }
            .mapNotNull { st ->
                val docId = studentDocIdFor(st.id)
                if (docId.isBlank()) null else docId to st
            }
        return StudentAttendanceEngine.buildSectionRangeSummary(
            students = students,
            schoolId = schoolDocId,
            session = filter.session,
            calendar = cal,
            sheets = studentAttendanceSheets,
            startDate = startDate,
            endDate = endDate
        )
    }

    fun computeLowAttendanceRows(
        schoolDocIdFilter: String?,
        classNameFilter: String?,
        sectionFilter: String?,
        startDate: String,
        endDate: String,
        thresholdPercent: Int
    ): List<LowAttendanceStudentRow> {
        val targetSchoolDocId = schoolDocIdFilter ?: activeSchoolDocId
        val schName = schoolRecordsByDocId[targetSchoolDocId]?.name ?: activeSchool.name
        val schLongId = schoolRecordsByDocId[targetSchoolDocId]?.stableId ?: activeSchool.id
        val students = allStudents
            .filter { it.schoolId == schLongId && it.session == filter.session }
            .mapNotNull { st ->
                val docId = studentDocIdFor(st.id)
                if (docId.isBlank()) null else Triple(targetSchoolDocId, schName, docId to st)
            }
        val calMap = mapOf(targetSchoolDocId to calendarForSchool(targetSchoolDocId, filter.session))
        return StudentAttendanceEngine.buildLowAttendanceList(
            students = students,
            session = filter.session,
            calendarsBySchool = calMap,
            sheets = studentAttendanceSheets,
            startDate = startDate,
            endDate = endDate,
            thresholdPercent = thresholdPercent,
            schoolIdFilter = targetSchoolDocId,
            classNameFilter = classNameFilter,
            sectionFilter = sectionFilter
        )
    }

    val isTeacherRestricted: Boolean
        get() = activeOrg != null && Phase7AccessEvaluator.isTeacherRestricted(accessProfile)

    val effectiveOwnTeacherScope: TeacherAccessScope?
        get() {
            if (ownTeacherScope != null) return ownTeacherScope
            val uid = currentMember?.uid.orEmpty()
            if (uid.isBlank() || teachers.isEmpty()) return null
            return Phase7AccessEvaluator.buildTeacherScopes(teachers, classAssignments)[uid]
        }

    val pendingLeaveRequestCount: Int
        get() = teacherLeaves.count { it.status == LeaveStatus.PENDING }

    fun latestUndoSnapshotForSchool(schoolDocId: String): TimetableUndoSnapshot? =
        undoSnapshots.firstOrNull {
            it.schoolId == schoolDocId && it.session.equals(filter.session, ignoreCase = true)
        }

    fun resolveOwnLinkedTeacher(): TeacherProfile? {
        val uid = currentMember?.uid.orEmpty()
        if (uid.isBlank()) return ownLinkedTeacher
        return teachers.firstOrNull { it.linkedMemberUid == uid } ?: ownLinkedTeacher
    }

    fun classTeacherNameForSection(schoolDocId: String, className: String, section: String): String {
        val normSec = section.trim().uppercase().ifBlank { "A" }
        val assignment = classAssignments.firstOrNull {
            it.schoolId == schoolDocId &&
                it.className.equals(className.trim(), ignoreCase = true) &&
                it.section.equals(normSec, ignoreCase = true)
        }
        val fromPhase4 = assignment?.classTeacherId?.takeIf { it.isNotBlank() }?.let { ctId ->
            teachers.firstOrNull { it.id == ctId }?.name?.trim()
        }
        if (!fromPhase4.isNullOrBlank()) return fromPhase4
        val map = schoolRecordsByDocId[schoolDocId]?.classTeacherNamesBySection.orEmpty()
        return map["${className.trim()}|$normSec"]
            ?: map.entries.firstOrNull { it.key.equals("${className.trim()}|$normSec", ignoreCase = true) }?.value
            ?: ""
    }

    fun isExamOpenForCurrentSchool(exam: ExamType): Boolean {
        if (!isTeacherRestricted) return true
        return Phase7AccessEvaluator.isExamOpenForTeacher(exam, activeSchoolOpenExams)
    }

    fun allowedClassesForMarks(): List<String> = Phase7AccessEvaluator.allowedClassesForMarks(
        member = accessProfile,
        teacherScope = effectiveOwnTeacherScope,
        schoolDocId = activeSchoolDocId,
        allSchoolClasses = activeSchoolClassNames
    )

    fun allowedSectionsForMarks(className: String): List<String> = Phase7AccessEvaluator.allowedSectionsForMarks(
        member = accessProfile,
        teacherScope = effectiveOwnTeacherScope,
        schoolDocId = activeSchoolDocId,
        className = className,
        allSections = availableSections
    )

    fun allowedSubjectsForMarks(className: String, section: String): List<String> = Phase7AccessEvaluator.allowedSubjectsForMarks(
        member = accessProfile,
        teacherScope = effectiveOwnTeacherScope,
        schoolDocId = activeSchoolDocId,
        className = className,
        section = section,
        allSubjects = subjectsForClass(className)
    )

    fun canEditCoScholasticFor(student: StudentEntity): Boolean {
        if (activeOrg == null) return true
        val schDocId = schoolDocIdByLongId[student.schoolId].orEmpty()
        return Phase7AccessEvaluator.canEditCoScholasticOrTermInfo(
            member = accessProfile,
            teacherScope = effectiveOwnTeacherScope,
            schoolDocId = schDocId,
            className = student.className,
            section = student.section
        )
    }

    fun canEditCoScholasticForSection(className: String, section: String): Boolean {
        if (activeOrg == null) return true
        return Phase7AccessEvaluator.canEditCoScholasticOrTermInfo(
            member = accessProfile,
            teacherScope = effectiveOwnTeacherScope,
            schoolDocId = activeSchoolDocId,
            className = className,
            section = section
        )
    }

    private fun effectivePendingBundles(): List<SchoolAcademicBundleForPending> {
        if (multiSchoolPendingBundles.isNotEmpty()) return multiSchoolPendingBundles
        if (activeSchoolDocId.isBlank()) return emptyList()
        val subsByClass = activeSchoolClasses.associate { cls ->
            cls.name to subjectsForClass(cls.name, activeSchool.id)
        }
        return listOf(
            SchoolAcademicBundleForPending(
                schoolDocId = activeSchoolDocId,
                schoolName = activeSchool.name,
                students = allStudents.filter { it.schoolId == activeSchool.id },
                marks = allMarks,
                subjectsByClass = subsByClass
            )
        )
    }

    fun computePendingMarksItems(
        schoolFilter: String? = null,
        classFilter: String? = null,
        examFilter: ExamType? = null,
        teacherIdFilter: String? = null,
        forceOwnTeacherOnly: Boolean = isTeacherRestricted
    ): List<PendingMarksItem> {
        val teacherScopeForFilter = if (forceOwnTeacherOnly) {
            effectiveOwnTeacherScope ?: TeacherAccessScope(uid = currentMember?.uid.orEmpty())
        } else {
            null
        }
        return PendingMarksEngine.computePendingItems(
            bundles = effectivePendingBundles(),
            session = filter.session,
            classAssignments = classAssignments,
            teachers = teachers,
            schoolFilter = schoolFilter,
            classFilter = classFilter,
            examFilter = examFilter,
            teacherIdFilter = teacherIdFilter,
            restrictToTeacherScope = teacherScopeForFilter
        )
    }

    fun pendingMarksExamSummaries(): List<PendingMarksExamSummary> =
        PendingMarksEngine.summarizeByExam(computePendingMarksItems())

    fun buildTeacherMyDay(dateIso: String): TeacherMyDaySummary? {
        val teacher = resolveOwnLinkedTeacher() ?: return null
        val scope = effectiveOwnTeacherScope
        val ownPending = if (canView(AppModule.MARKS)) {
            PendingMarksEngine.computePendingItems(
                bundles = effectivePendingBundles(),
                session = filter.session,
                classAssignments = classAssignments,
                teachers = teachers.ifEmpty { listOf(teacher) },
                restrictToTeacherScope = scope ?: TeacherAccessScope(uid = currentMember?.uid.orEmpty())
            )
        } else {
            emptyList()
        }
        return TeacherMyDayEngine.buildTeacherMyDay(
            teacher = teacher,
            teacherScope = scope,
            dateIso = dateIso,
            session = filter.session,
            timetableCells = activeSessionTimetableCells,
            substitutions = substitutions,
            periodStructuresBySchool = effectivePeriodStructuresBySchool,
            schoolNamesById = schoolNamesByDocId,
            pendingMarksForTeacher = ownPending
        )
    }

    fun computeWorkloadReport(
        referenceDateIso: String,
        schoolIdFilter: String? = null,
        teacherIdFilter: String? = null
    ): List<TeacherWorkloadReportRow> = WorkloadReportEngine.computeWorkloadReport(
        teachers = availableTimetableTeachers(),
        timetableCells = activeSessionTimetableCells,
        classAssignments = classAssignments,
        substitutions = substitutions,
        periodStructuresBySchool = effectivePeriodStructuresBySchool,
        schoolNamesById = schoolNamesByDocId,
        session = filter.session,
        referenceDateIso = referenceDateIso,
        schoolIdFilter = schoolIdFilter,
        teacherIdFilter = teacherIdFilter
    )

    fun buildDailyTeacherAttendance(schoolDocId: String, dateIso: String): List<TeacherDailyAttendanceRow> =
        TeacherAttendanceEngine.buildDailySheet(
            schoolId = schoolDocId,
            dateIso = dateIso,
            teachers = availableTimetableTeachers(),
            attendanceRecords = teacherAttendanceRecords,
            approvedLeaves = teacherLeaves
        )

    fun buildMonthlyTeacherAttendance(schoolDocId: String, year: Int, month1Based: Int): List<TeacherMonthlyAttendanceSummaryRow> =
        TeacherAttendanceEngine.buildMonthlySummary(
            schoolId = schoolDocId,
            year = year,
            month1Based = month1Based,
            teachers = availableTimetableTeachers(),
            periodStructure = periodStructureForSchool(schoolDocId),
            attendanceRecords = teacherAttendanceRecords,
            approvedLeaves = teacherLeaves
        )

    fun checkCurrentClassReportReadiness(reportType: ReportType): ClassReportReadinessResult {
        val cls = effectiveClassName
        val sec = filter.section
        val students = filteredStudents
        val subs = subjectsForClass(cls)
        return ReportReadinessEngine.checkClassReadiness(
            className = cls,
            section = sec,
            reportType = reportType,
            students = students,
            subjects = subs,
            marks = allMarks,
            coScholastic = allCoScholastic,
            termInfos = allTermInfos
        )
    }

    val schoolNamesByDocId: Map<String, String>
        get() = schools.mapNotNull { sch -> schoolDocIdByLongId[sch.id]?.let { it to sch.name } }.toMap()

    fun schoolNameByDocId(schoolDocId: String): String =
        schoolNamesByDocId[schoolDocId] ?: schoolDocId

    val effectivePeriodStructuresBySchool: Map<String, SchoolPeriodStructure>
        get() = timetableLayoutsBySchool + periodStructuresBySchool

    fun periodStructureForSchool(schoolDocId: String): SchoolPeriodStructure =
        effectivePeriodStructuresBySchool[schoolDocId] ?: Phase4Validator.buildDefaultPeriodStructure(schoolDocId)

    val activeSessionTimetableCells: List<TimetableCell>
        get() = timetableCells.filter { it.session.equals(filter.session, ignoreCase = true) }

    fun timetableClashes(): List<TimetableHardConflict> = TimetableEngine.detectAllClashes(
        cellsInSession = activeSessionTimetableCells,
        teachers = teachers,
        periodStructuresBySchool = effectivePeriodStructuresBySchool,
        schoolNamesById = schoolNamesByDocId
    )

    fun availableTimetableTeachers(): List<TeacherProfile> {
        val byId = linkedMapOf<String, TeacherProfile>()
        teachers.forEach { byId[it.id] = it }
        timetableCells.forEach { cell ->
            if (cell.teacherId.isNotBlank() && cell.teacherId !in byId) {
                byId[cell.teacherId] = TeacherProfile(
                    id = cell.teacherId,
                    name = cell.teacherName.ifBlank { cell.teacherId },
                    schoolIds = listOf(cell.schoolId),
                    active = true
                )
            }
        }
        return byId.values.sortedBy { it.name.lowercase() }
    }

    fun sectionsForSchoolClass(schoolId: Long, className: String): List<String> {
        val schoolDocId = schoolDocIdByLongId[schoolId].orEmpty()
        val found = linkedSetOf("A")
        allStudents.filter { it.schoolId == schoolId && it.className.equals(className, ignoreCase = true) }
            .forEach { if (it.section.isNotBlank()) found.add(it.section.uppercase()) }
        classAssignments.filter { it.schoolId == schoolDocId && it.className.equals(className, ignoreCase = true) }
            .forEach { if (it.section.isNotBlank()) found.add(it.section.uppercase()) }
        activeSessionTimetableCells.filter { it.schoolId == schoolDocId && it.className.equals(className, ignoreCase = true) }
            .forEach { if (it.section.isNotBlank()) found.add(it.section.uppercase()) }
        return found.sorted()
    }

    val allConfiguredSubjectsAcrossOrg: List<String>
        get() = subjects.map { it.name }.distinct().sortedBy { it.lowercase() }

    fun configuredSubjectsForSchool(schoolId: Long = activeSchool.id): List<String> =
        subjects.filter { it.schoolId == schoolId }.map { it.name }.distinct().sortedBy { it.lowercase() }

    fun phase4Warnings(): List<AssignmentWarning> = Phase4Validator.evaluateAllWarnings(
        teachers = teachers,
        periodStructuresBySchool = periodStructuresBySchool,
        assignments = classAssignments,
        schoolNamesById = schoolNamesByDocId
    )

    fun teacherWorkloads(warnings: List<AssignmentWarning>? = null): List<TeacherWorkloadSummary> =
        Phase4Validator.buildTeacherWorkloads(
            teachers = teachers,
            periodStructuresBySchool = periodStructuresBySchool,
            assignments = classAssignments,
            schoolNamesById = schoolNamesByDocId,
            allWarnings = warnings
        )

    fun markEditLogsForStudent(studentId: Long): List<CloudMarkEditLog> =
        markEditLogs.filter { StableDocId.toLong(it.studentId) == studentId }
    val accessProfile: MemberAccessProfile?
        get() = currentMember?.toAccessProfile()

    val isOwner: Boolean
        get() = if (activeOrg == null) true else OrgAccessEvaluator.isOwner(accessProfile)

    val isAdmin: Boolean
        get() = if (activeOrg == null) true else OrgAccessEvaluator.isAdmin(accessProfile)

    val needsTeacherProfile: Boolean
        get() = OrgAccessEvaluator.needsTeacherProfileOnboarding(accessProfile)

    val pendingJoinRequestCount: Int
        get() = joinRequests.count { it.status == "pending" }

    fun canView(module: AppModule): Boolean =
        if (activeOrg == null) true else OrgAccessEvaluator.canViewModule(accessProfile, module)

    fun canEdit(module: AppModule): Boolean =
        if (activeOrg == null) true else OrgAccessEvaluator.canEditModule(accessProfile, module)

    fun canManageMembers(): Boolean =
        if (activeOrg == null) true else OrgAccessEvaluator.canManageMembers(accessProfile)

    fun canEditMarkFor(student: StudentEntity, subjectName: String, componentKey: String? = null): Boolean {
        if (activeOrg == null) return true
        val schDocId = schoolDocIdByLongId[student.schoolId].orEmpty()
        val openExams = schoolRecordsByDocId[schDocId]?.openExamsForTeachers ?: Phase7AccessEvaluator.ALL_EXAM_KEYS
        return Phase7AccessEvaluator.canEditMarks(
            member = accessProfile,
            teacherScope = effectiveOwnTeacherScope,
            schoolDocId = schDocId,
            className = student.className,
            section = student.section,
            subjectName = subjectName,
            openExamKeys = openExams,
            componentKey = componentKey
        )
    }

    val activeSchools: List<SchoolEntity>
        get() = schools.filter { it.active }

    val activeSchool: SchoolEntity
        get() = schools.firstOrNull { it.id == filter.schoolId }
            ?: activeSchools.firstOrNull()
            ?: schools.firstOrNull()
            ?: SchoolEntity(id = 0L, name = AppDatabase.SAMPLE_SCHOOL_NAME)

    fun schoolById(schoolId: Long): SchoolEntity? = schools.firstOrNull { it.id == schoolId }

    val activeSchoolGroups: List<ClassGroupEntity>
        get() = classGroups.filter { it.schoolId == activeSchool.id }.sortedBy { it.sortOrder }

    fun classGroupsForSchool(schoolId: Long): List<ClassGroupEntity> =
        classGroups.filter { it.schoolId == schoolId }.sortedBy { it.sortOrder }

    val activeSchoolClasses: List<SchoolClassEntity>
        get() = schoolClasses.filter { it.schoolId == activeSchool.id }.sortedBy { it.sortOrder }

    fun classesForSchool(schoolId: Long): List<SchoolClassEntity> =
        schoolClasses.filter { it.schoolId == schoolId }.sortedBy { it.sortOrder }

    val activeSchoolClassNames: List<String>
        get() = activeSchoolClasses.map { it.name }

    val effectiveClassName: String
        get() = activeSchoolClasses.firstOrNull { it.name.equals(filter.className, ignoreCase = true) }?.name
            ?: activeSchoolClasses.firstOrNull()?.name
            ?: filter.className

    fun classEntityForName(className: String, schoolId: Long = activeSchool.id): SchoolClassEntity? =
        schoolClasses.firstOrNull { it.schoolId == schoolId && it.name.equals(className, ignoreCase = true) }

    fun subjectsForGroupId(classGroupId: Long): List<SubjectEntity> =
        subjects.filter { it.classGroupId == classGroupId }.sortedBy { it.sortOrder }

    fun subjectsForClass(className: String, schoolId: Long = activeSchool.id): List<String> {
        val schoolClass = classEntityForName(className, schoolId)
        if (schoolClass != null) {
            val configured = subjectsForGroupId(schoolClass.classGroupId).map { it.name }
            if (configured.isNotEmpty()) return configured
            val group = classGroups.firstOrNull { it.id == schoolClass.classGroupId }
            if (group != null && group.templateKey.isNotEmpty()) {
                return DefaultSubjects.forTemplateKey(group.templateKey)
            }
        }
        return DefaultSubjects.forTemplateKey(DefaultSchoolTemplate.inferDefaultGroupKeyForClass(className))
    }

    fun studentCountForSchool(schoolId: Long): Int =
        allStudents.count { it.schoolId == schoolId }

    fun classCountForSchool(schoolId: Long): Int =
        schoolClasses.count { it.schoolId == schoolId }

    fun studentCountForClass(schoolClass: SchoolClassEntity): Int =
        allStudents.count {
            it.schoolId == schoolClass.schoolId &&
                (it.classId == schoolClass.id || it.className.equals(schoolClass.name, ignoreCase = true))
        }

    val sessionStudents: List<StudentEntity>
        get() = allStudents.filter {
            it.schoolId == activeSchool.id && it.session == filter.session
        }

    val filteredStudents: List<StudentEntity>
        get() = sessionStudents
            .filter {
                it.className.equals(effectiveClassName, ignoreCase = true) &&
                    it.section.equals(filter.section, ignoreCase = true)
            }
            .sortedBy { it.rollNo }

    val selectedStudent: StudentEntity?
        get() = filteredStudents.firstOrNull { it.id == filter.selectedStudentId }
            ?: filteredStudents.firstOrNull()

    val availableSections: List<String>
        get() {
            val fromDb = sessionStudents
                .filter { it.className.equals(effectiveClassName, ignoreCase = true) }
                .map { it.section }
            return (DefaultSchoolTemplate.defaultSections + fromDb).distinct().sorted()
        }

    fun marksMapForStudent(studentId: Long): Map<Pair<String, String>, String> =
        allMarks
            .asSequence()
            .filter { it.studentId == studentId && it.componentKey in MarkComponent.validKeys }
            .associate { (it.subjectName to it.componentKey) to it.rawValue }

    fun coScholasticMapForStudent(studentId: Long, termNo: Int): Map<String, String> =
        allCoScholastic
            .asSequence()
            .filter { it.studentId == studentId && it.termNo == termNo }
            .associate { it.areaCode to it.grade }

    fun termInfoForStudent(studentId: Long, termNo: Int): TermInfoEntity? =
        allTermInfos.firstOrNull { it.studentId == studentId && it.termNo == termNo }

    fun attendanceForStudent(studentId: Long): AttendanceSummary {
        val student = allStudents.firstOrNull { it.id == studentId }
        val schDocId = student?.let { schoolDocIdByLongId[it.schoolId] } ?: activeSchoolDocId
        val autoFillOn = schoolRecordsByDocId[schDocId]?.autoFillReportAttendance ?: false
        val t1 = termInfoForStudent(studentId, 1)
        val t2 = termInfoForStudent(studentId, 2)
        if (!autoFillOn) {
            return AttendanceSummary(
                t1Present = t1?.daysPresent ?: 0,
                t1Working = t1?.workingDays ?: 0,
                t2Present = t2?.daysPresent ?: 0,
                t2Working = t2?.workingDays ?: 0
            )
        }
        return StudentAttendanceEngine.buildEffectiveAttendanceSummary(
            autoFillSwitchOn = true,
            term1Stored = t1,
            term1ManualFlag = termInfoManualOverrideByStudentTerm[studentId to 1],
            term2Stored = t2,
            term2ManualFlag = termInfoManualOverrideByStudentTerm[studentId to 2],
            term1Daily = computeStudentTermAttendance(studentId, 1),
            term2Daily = computeStudentTermAttendance(studentId, 2)
        )
    }

    fun performanceWithRanksForClass(
        schoolId: Long,
        session: String,
        className: String,
        section: String
    ): Map<Long, StudentPerformanceReport> {
        val subs = subjectsForClass(className, schoolId)
        val classStudents = allStudents.filter {
            it.schoolId == schoolId &&
                it.session == session &&
                it.className.equals(className, ignoreCase = true) &&
                it.section.equals(section, ignoreCase = true)
        }
        val rawReports = classStudents.map { st ->
            ReportCalculations.calculateStudentPerformance(
                studentId = st.id,
                subjects = subs,
                marksBySubjectAndKey = marksMapForStudent(st.id),
                passPercent = settings.passPercent
            )
        }
        val ranks = ReportCalculations.calculateClassRanks(rawReports)
        return rawReports.associate { rep ->
            rep.studentId to rep.copy(classRank = ranks[rep.studentId])
        }
    }

    fun allSessionStudentSummaries(): List<StudentDashboardSummary> {
        val students = sessionStudents
        if (students.isEmpty()) return emptyList()

        val school = activeSchool
        val classesOrder = activeSchoolClasses.withIndex().associate { (idx, c) -> c.name.lowercase() to idx }
        val groupById = activeSchoolGroups.associateBy { it.id }

        val grouped = students.groupBy {
            it.className.lowercase() to it.section.uppercase()
        }
        val perfByStudentId = mutableMapOf<Long, StudentPerformanceReport>()
        grouped.forEach { (_, groupStudents) ->
            val repClassName = groupStudents.first().className
            val subs = subjectsForClass(repClassName, school.id)
            val rawReports = groupStudents.map { st ->
                ReportCalculations.calculateStudentPerformance(
                    studentId = st.id,
                    subjects = subs,
                    marksBySubjectAndKey = marksMapForStudent(st.id),
                    passPercent = settings.passPercent
                )
            }
            val ranks = ReportCalculations.calculateClassRanks(rawReports)
            rawReports.forEach { rep ->
                perfByStudentId[rep.studentId] = rep.copy(classRank = ranks[rep.studentId])
            }
        }

        return students
            .sortedWith(
                compareBy<StudentEntity>(
                    { classesOrder[it.className.lowercase()] ?: Int.MAX_VALUE },
                    { it.className.lowercase() },
                    { it.section.uppercase() },
                    { it.rollNo },
                    { it.name.lowercase() }
                )
            )
            .map { st ->
                val perf = perfByStudentId[st.id]
                val hasMarks = perf?.hasAnyAnnualMarks == true
                val status = when {
                    !hasMarks -> StudentAcademicStatus.PENDING
                    perf?.overallResult == ResultStatus.PASS -> StudentAcademicStatus.PASS
                    else -> StudentAcademicStatus.NEEDS_ATTENTION
                }
                val clsEntity = classEntityForName(st.className, school.id)
                val grpLabel = clsEntity?.let { groupById[it.classGroupId]?.shortLabel }.orEmpty()
                StudentDashboardSummary(
                    student = st,
                    schoolName = school.name,
                    classGroupLabel = grpLabel,
                    overallPercent = if (hasMarks) perf?.overallPercentage ?: 0.0 else 0.0,
                    overallGrade = if (hasMarks) perf?.overallGrade ?: "-" else "-",
                    status = status,
                    hasMarks = hasMarks,
                    classRank = if (hasMarks) perf?.classRank else null
                )
            }
    }

    val marksCompletionPercent: Int
        get() {
            val students = sessionStudents
            if (students.isEmpty()) return 0
            val activeKeys = MarkComponent.validKeys
            val compCount = activeKeys.size
            var expectedCells = 0
            var filledCells = 0
            val marksByStudent = allMarks.groupBy { it.studentId }

            students.forEach { st ->
                val subs = subjectsForClass(st.className, st.schoolId)
                expectedCells += subs.size * compCount
                val stMarks = marksByStudent[st.id].orEmpty()
                filledCells += stMarks.count {
                    it.componentKey in activeKeys && it.rawValue.isNotBlank() && it.subjectName in subs
                }
            }
            if (expectedCells == 0) return 0
            return ((filledCells.toDouble() / expectedCells.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        }

    val isBackupOverdue: Boolean
        get() {
            if (settings.lastBackupEpochMs <= 0L) return true
            val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
            return (System.currentTimeMillis() - settings.lastBackupEpochMs) > thirtyDaysMs
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SchoolViewModel(
    internal val appContext: Application,
    val orgId: String,
    internal val currentUserId: String,
    internal val orgRepo: OrgCloudRepository,
    internal val academicRepo: OrgAcademicRepository,
    private val phase4Repo: Phase4Repository,
    private val timetableRepo: TimetableRepository,
    private val phase6Repo: Phase6Repository,
    private val phase7Repo: Phase7Repository,
    private val phase8Repo: Phase8Repository,
    private val localRepo: ReportCardRepository,
    internal val connectivityMonitor: NetworkConnectivityMonitor,
    internal val offlinePrefs: OfflinePrefsStore,
    internal val phase9Repo: Phase9Repository = Phase9Repository(orgRepo),
    internal val phase10Repo: Phase10Repository = Phase10Repository(orgRepo),
    internal val phase11Repo: Phase11Repository = Phase11Repository(orgRepo),
    internal val phase12Repo: Phase12Repository = Phase12Repository(orgRepo)
) : ViewModel() {

    internal val _filter = MutableStateFlow(FilterSelection())
    private val _lastUndo = MutableStateFlow<MarkUndoSnapshot?>(null)
    private val _isInitialized = MutableStateFlow(false)
    private val _membershipRevoked = MutableStateFlow(false)
    private val _markConflicts = MutableStateFlow<List<MarkConflictNotice>>(emptyList())
    private val _timetableConflicts = MutableStateFlow<List<TimetableConflictNotice>>(emptyList())
    private val _phase6Conflicts = MutableStateFlow<List<Phase6ConflictNotice>>(emptyList())
    private val _observerRestartTick = MutableStateFlow(0)

    private val _pdfState = MutableStateFlow(PdfPreviewState())
    val pdfState: StateFlow<PdfPreviewState> = _pdfState.asStateFlow()

    internal val _timetablePdfState = MutableStateFlow(TimetablePdfPreviewState())
    val timetablePdfState: StateFlow<TimetablePdfPreviewState> = _timetablePdfState.asStateFlow()

    private val _autoGenerateProgress = MutableStateFlow(AutoGenerateProgress())
    val autoGenerateProgress: StateFlow<AutoGenerateProgress> = _autoGenerateProgress.asStateFlow()

    private val _autoGenerateResult = MutableStateFlow<AutoGenerateResult?>(null)
    val autoGenerateResult: StateFlow<AutoGenerateResult?> = _autoGenerateResult.asStateFlow()

    private var autoGenerateJob: Job? = null
    private val autoGenerateCancelFlag = AtomicBoolean(false)

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private val _pendingImportBackup = MutableStateFlow<FullBackupData?>(null)
    val pendingImportBackup: StateFlow<FullBackupData?> = _pendingImportBackup.asStateFlow()

    private val _pendingLegacyBackup = MutableStateFlow<LegacyBackupPayload?>(null)
    val pendingLegacyBackup: StateFlow<LegacyBackupPayload?> = _pendingLegacyBackup.asStateFlow()

    private val _pendingMarksCsvImport = MutableStateFlow<MarksCsvImportPreview?>(null)
    val pendingMarksCsvImport: StateFlow<MarksCsvImportPreview?> = _pendingMarksCsvImport.asStateFlow()

    private val _migrationProgress = MutableStateFlow(MigrationProgress())
    val migrationProgress: StateFlow<MigrationProgress> = _migrationProgress.asStateFlow()

    private val _deleteOrgBackupExported = MutableStateFlow(false)
    val deleteOrgBackupExported: StateFlow<Boolean> = _deleteOrgBackupExported.asStateFlow()

    private val _orgState = MutableStateFlow<CloudOrganisation?>(null)
    private val _ownMember = MutableStateFlow<CloudMember?>(null)
    private val _schoolsSnapshot = MutableStateFlow(CloudSchoolsSnapshot())
    private val _academicSnapshot = MutableStateFlow(CloudSchoolAcademicSnapshot())
    private val _phase4Snapshot = MutableStateFlow(CloudPhase4Snapshot())
    private val _timetableSnapshot = MutableStateFlow(CloudTimetableSnapshot())
    private val _phase6Snapshot = MutableStateFlow(CloudPhase6Snapshot())
    private val _phase7Snapshot = MutableStateFlow(CloudPhase7Snapshot())
    private val _phase8Snapshot = MutableStateFlow(CloudPhase8Snapshot())
    private val _phase9Snapshot = MutableStateFlow(CloudPhase9Snapshot())
    private val _phase10Snapshot = MutableStateFlow(CloudPhase10Snapshot())
    private val _phase11Snapshot = MutableStateFlow(CloudPhase11Snapshot())
    private val _phase12Snapshot = MutableStateFlow(CloudPhase12Snapshot())
    internal val _phase10HindiLabels = MutableStateFlow(offlinePrefs.getHindiLabelsEnabled(true))
    internal val _firstTimeGuideSkipped = MutableStateFlow(offlinePrefs.isFirstTimeGuideSkipped(currentUserId, orgId))
    private val _attendanceConflicts = MutableStateFlow<List<AttendanceConflictNotice>>(emptyList())
    private val _multiSchoolPendingBundles = MutableStateFlow<List<SchoolAcademicBundleForPending>>(emptyList())
    private val _orgMembers = MutableStateFlow<List<CloudMember>>(emptyList())
    private val _joinRequests = MutableStateFlow<List<CloudJoinRequest>>(emptyList())
    private val _inviteCodes = MutableStateFlow<List<CloudInviteCode>>(emptyList())
    private val _auditLogs = MutableStateFlow<List<CloudAuditLog>>(emptyList())
    private val _isOnline = MutableStateFlow(connectivityMonitor.isCurrentlyOnline())

    // In-memory lookups from stable Long IDs back to Firestore doc IDs
    internal var schoolRecordsByLongId: Map<Long, CloudSchoolRecord> = emptyMap()
    internal var studentDocIdByLongId: Map<Long, Pair<String, StudentEntity>> = emptyMap()
    private var existingMarkValuesByDocId: Map<String, String> = emptyMap()
    private var existingMarkDocIds: Set<String> = emptySet()
    private var existingCoDocIds: Set<String> = emptySet()
    private var existingTermInfoDocIds: Set<String> = emptySet()
    private var sessionInitializedFromOrg = false
    private var seenActiveMemberOnce = false
    private var previousConfirmedProfile: MemberAccessProfile? = null
    private var isResettingCache = false
    private var lastSyncedScopeSignature: String = ""

    private val coreCloudFlow = combine(
        _orgState,
        _ownMember,
        _schoolsSnapshot,
        _academicSnapshot,
        _isOnline
    ) { org, member, schoolsSnap, academicSnap, online ->
        CloudCoreBundle(org, member, schoolsSnap, academicSnap, online)
    }

    private val combinedSyncErrorFlow = combine(
        combine(academicRepo.syncError, phase4Repo.syncError, timetableRepo.syncError) { a, b, c -> a ?: b ?: c },
        combine(phase6Repo.syncError, phase7Repo.syncError, phase8Repo.syncError) { d, e, f -> d ?: e ?: f },
        combine(phase9Repo.syncError, phase10Repo.syncError, phase11Repo.syncError, phase12Repo.syncError) { p9, p10, p11, p12 -> p9 ?: p10 ?: p11 ?: p12 }
    ) { left, right, p912Err -> left ?: right ?: p912Err }

    private val phase4To6Flow = combine(
        _phase4Snapshot,
        _timetableSnapshot,
        _timetableConflicts,
        _phase6Snapshot,
        _phase6Conflicts
    ) { p4, tt, ttConflicts, p6, p6Conflicts ->
        Phase4And5Bundle(p4, tt, ttConflicts, p6, p6Conflicts)
    }

    private val phase10And11Flow = combine(
        _phase10Snapshot,
        _phase11Snapshot,
        _phase12Snapshot,
        _phase10HindiLabels,
        _firstTimeGuideSkipped
    ) { p10, p11, p12, hindiLabels, guideSkipped ->
        Phase10To12Slice(p10, p11, p12, hindiLabels, guideSkipped)
    }

    private val phase8To10Flow = combine(
        _phase8Snapshot,
        _attendanceConflicts,
        _phase9Snapshot,
        phase10And11Flow
    ) { p8, attConflicts, p9, p1012 ->
        Phase8To10Bundle(p8, attConflicts, p9, p1012.phase10, p1012.phase11, p1012.phase12, p1012.phase10HindiLabels, p1012.firstTimeGuideSkipped)
    }

    private val phase4To8Flow = combine(
        phase4To6Flow,
        _phase7Snapshot,
        _multiSchoolPendingBundles,
        phase8To10Flow
    ) { p46, p7, pendingBundles, p810 ->
        Phase7And8Bundle(
            p46 = p46,
            phase7 = p7,
            multiSchoolPendingBundles = pendingBundles,
            phase8 = p810.phase8,
            attendanceConflicts = p810.attendanceConflicts,
            phase9 = p810.phase9,
            phase10 = p810.phase10,
            phase11 = p810.phase11,
            phase12 = p810.phase12,
            phase10HindiLabels = p810.phase10HindiLabels,
            firstTimeGuideSkipped = p810.firstTimeGuideSkipped
        )
    }

    private val syncExtrasFlow = combine(
        combinedSyncErrorFlow,
        academicRepo.lastSyncedAtMs,
        _markConflicts,
        _isInitialized,
        _membershipRevoked
    ) { syncErr, lastSynced, conflicts, init, rev ->
        SyncExtrasBundle(syncErr, lastSynced, conflicts, init, rev)
    }

    private val adminCollectionsFlow = combine(
        _orgMembers,
        _joinRequests,
        _inviteCodes,
        _auditLogs,
        phase4To8Flow
    ) { members, reqs, invites, logs, p78 ->
        AdminAndPhase4Bundle(
            members = members,
            joinRequests = reqs,
            inviteCodes = invites,
            auditLogs = logs,
            phase4 = p78.p46.phase4,
            timetable = p78.p46.timetable,
            timetableConflicts = p78.p46.timetableConflicts,
            phase6 = p78.p46.phase6,
            phase6Conflicts = p78.p46.phase6Conflicts,
            phase7 = p78.phase7,
            multiSchoolPendingBundles = p78.multiSchoolPendingBundles,
            phase8 = p78.phase8,
            attendanceConflicts = p78.attendanceConflicts,
            phase9 = p78.phase9,
            phase10 = p78.phase10,
            phase11 = p78.phase11,
            phase12 = p78.phase12,
            phase10HindiLabels = p78.phase10HindiLabels,
            firstTimeGuideSkipped = p78.firstTimeGuideSkipped
        )
    }

    val uiState: StateFlow<SchoolUiState> = combine(
        coreCloudFlow,
        adminCollectionsFlow,
        _filter,
        _lastUndo,
        syncExtrasFlow
    ) { core, admin, filter, undo, extras ->
        val schoolsRecords = core.schoolsSnap.schools
        val schools = schoolsRecords.map { it.toSchoolEntity() }
        val groups = schoolsRecords.flatMap { it.toClassGroupEntities() }
        val classes = schoolsRecords.flatMap { it.toSchoolClassEntities() }
        val subjects = schoolsRecords.flatMap { it.toSubjectEntities() }
        val settings = core.org?.settings?.toEntity() ?: SchoolSettingsEntity()

        val resolvedSchool = schools.firstOrNull { it.id == filter.schoolId }
            ?: schools.firstOrNull { it.active }
            ?: schools.firstOrNull()
        val resolvedSchoolId = resolvedSchool?.id ?: filter.schoolId

        val classesForResolved = classes
            .filter { it.schoolId == resolvedSchoolId }
            .sortedBy { it.sortOrder }
        val resolvedClassName = classesForResolved
            .firstOrNull { it.name.equals(filter.className, ignoreCase = true) }?.name
            ?: classesForResolved.firstOrNull()?.name
            ?: filter.className

        val students = core.academicSnap.students.map { it.entity }
        val marks = core.academicSnap.marks.map { it.entity }
        val coScholastic = core.academicSnap.coScholastic.map { it.entity }
        val termInfos = core.academicSnap.termInfos.map { it.entity }
        val totalPendingCount = core.schoolsSnap.pendingWriteCount +
            core.academicSnap.pendingWriteCount +
            admin.phase4.pendingWriteCount +
            admin.timetable.pendingWriteCount +
            admin.phase6.pendingWriteCount +
            admin.phase7.pendingWriteCount +
            admin.phase8.pendingWriteCount +
            admin.phase9.pendingWriteCount +
            admin.phase10.pendingWriteCount +
            admin.phase11.pendingWriteCount +
            admin.phase12.pendingWriteCount

        val syncUi = OfflineSyncEngine.computeSyncUiState(
            isOnline = core.isOnline,
            pendingCount = totalPendingCount,
            syncErrorMessage = extras.syncError,
            lastSyncedAtMs = extras.lastSyncedAtMs
        )

        SchoolUiState(
            isInitialized = extras.isInitialized,
            schools = schools,
            classGroups = groups,
            schoolClasses = classes,
            settings = settings,
            filter = filter.copy(
                schoolId = resolvedSchoolId,
                className = resolvedClassName
            ),
            subjects = subjects,
            allStudents = students,
            allMarks = marks,
            allCoScholastic = coScholastic,
            allTermInfos = termInfos,
            sampleStudentCount = students.count { it.isSample },
            legacyMarksCount = marks.count { it.componentKey !in MarkComponent.validKeys },
            lastMarkUndo = undo,
            activeOrg = core.org,
            currentMember = core.member,
            orgMembers = admin.members,
            joinRequests = admin.joinRequests,
            inviteCodes = admin.inviteCodes,
            auditLogs = admin.auditLogs,
            syncStatus = syncUi.status,
            syncUiState = syncUi,
            isOnline = core.isOnline,
            markEditLogs = core.academicSnap.markEditLogs,
            markConflicts = extras.conflicts,
            membershipRevoked = extras.membershipRevoked,
            schoolDocIdByLongId = schoolsRecords.associate { it.stableId to it.docId },
            teachers = admin.phase4.teachers,
            periodStructuresBySchool = admin.phase4.periodStructuresBySchool,
            classAssignments = admin.phase4.classAssignments,
            subjectRules = admin.phase4.subjectRules,
            timetableCells = admin.timetable.cells,
            timetableLayoutsBySchool = admin.timetable.layoutsBySchool,
            timetableConflicts = admin.timetableConflicts,
            teacherLeaves = admin.phase6.leaves,
            substitutions = admin.phase6.substitutions,
            undoSnapshots = admin.phase6.undoSnapshots,
            ownLinkedTeacher = admin.phase6.ownLinkedTeacher ?: admin.phase7.ownLinkedTeacherProfile,
            phase6Conflicts = admin.phase6Conflicts,
            ownTeacherScope = admin.phase7.ownTeacherScope,
            allTeacherScopes = admin.phase7.allTeacherScopes,
            teacherAttendanceRecords = admin.phase7.teacherAttendanceRecords,
            multiSchoolPendingBundles = admin.multiSchoolPendingBundles,
            schoolRecordsByDocId = schoolsRecords.associateBy { it.docId },
            schoolCalendars = admin.phase8.schoolCalendars,
            studentAttendanceSheets = admin.phase8.studentAttendanceSheets,
            studentAttendanceEditLogs = admin.phase8.attendanceEditLogs,
            attendanceConflictNotices = admin.attendanceConflicts,
            termInfoManualOverrideByStudentTerm = core.academicSnap.termInfos.associate {
                (it.entity.studentId to it.entity.termNo) to it.attendanceManualOverride
            },
            studentDocIdByLongId = core.academicSnap.students.associate { it.entity.id to it.docId },
            feeStructures = admin.phase9.feeStructures,
            studentFeeOverrides = admin.phase9.studentFeeOverrides,
            feeReceipts = admin.phase9.feeReceipts,
            feeMessageSentLogs = admin.phase9.messageSentLogs,
            schoolNotices = admin.phase9.schoolNotices,
            feeAuditLogs = admin.phase9.feeAuditLogs,
            deviceNodeCode = offlinePrefs.getOrCreateDeviceNodeCode(currentUserId),
            schoolCertificates = admin.phase10.certificates,
            cashBookSettingsList = admin.phase10.cashBookSettings,
            cashBookEntries = StaffSalaryEngine.mergeCashBookEntriesWithPaidSalaries(
                schoolId = "",
                cashBookEntries = admin.phase10.cashBookEntries,
                salaryRecords = admin.phase12.salaryMonthlyRecords
            ),
            cashBookDayClosings = admin.phase10.cashBookDayClosings,
            cashBookAuditLogs = admin.phase10.cashBookAuditLogs,
            cashBookFeeReceipts = admin.phase10.cashBookFeeReceipts,
            admissionEnquiries = admin.phase10.enquiries,
            enquirySettingsList = admin.phase10.enquirySettings,
            phase10HindiLabels = admin.phase10HindiLabels,
            examSchedules = admin.phase11.examSchedules,
            examRooms = admin.phase11.examRooms,
            examSeatings = admin.phase11.examSeatings,
            examDuties = admin.phase11.examDuties,
            stockTransactions = admin.phase11.stockTransactions,
            transportRoutes = admin.phase11.transportRoutes,
            phase11FeeStructures = admin.phase11.sharedFeeStructures,
            phase11FeeReceipts = admin.phase11.sharedFeeReceipts,
            phase11FeeOverrides = admin.phase11.sharedFeeOverrides,
            phase11Teachers = admin.phase11.sharedTeachers,
            phase11TeacherLeaves = admin.phase11.sharedTeacherLeaves,
            salaryRuleConfigs = admin.phase12.salaryRuleConfigs,
            staffSalaryProfiles = admin.phase12.staffSalaryProfiles,
            salaryMonthlyRecords = admin.phase12.salaryMonthlyRecords,
            staffSalaryAdvances = admin.phase12.staffSalaryAdvances,
            salaryMonthLocks = admin.phase12.salaryMonthLocks,
            salaryAuditLogs = admin.phase12.salaryAuditLogs,
            ownTeacherSalarySlips = admin.phase12.ownTeacherSalarySlips,
            phase12Teachers = admin.phase12.sharedTeachers,
            phase12TeacherAttendance = admin.phase12.sharedTeacherAttendance,
            phase12TeacherLeaves = admin.phase12.sharedTeacherLeaves,
            isFirstTimeGuideSkipped = admin.firstTimeGuideSkipped
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SchoolUiState()
    )

    init {
        startObservers()
    }

    private fun startObservers() {
        // 1. Connectivity
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online ->
                _isOnline.value = online
            }
        }

        // 2. Own member doc (detects revocation OR access reduction on reconnect & clears removed local cache)
        viewModelScope.launch {
            _observerRestartTick
                .flatMapLatest {
                    orgRepo.observeOwnMemberWithMeta(orgId, currentUserId)
                        .catch { e ->
                            val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                            if (cause != null) {
                                handleFirestoreError(cause, OperationType.GET, "organisations/$orgId/members/$currentUserId")
                                if (cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                    handleAccessRevokedAndWipeCache()
                                }
                                return@catch
                            }
                            if (e is CancellationException) throw e
                        }
                }
                .collect { meta ->
                    val member = meta.member
                    val newProfile = member?.toAccessProfile()
                    _ownMember.value = member

                    if (member != null && member.memberStatus == MemberStatus.ACTIVE) {
                        if (!seenActiveMemberOnce) {
                            seenActiveMemberOnce = true
                            orgRepo.touchLastActive(orgId)
                        }
                        _isInitialized.value = true

                        if (!meta.isFromCache) {
                            val prev = previousConfirmedProfile
                            if (prev != null && OfflineSyncEngine.detectAccessReduction(prev, newProfile)) {
                                previousConfirmedProfile = newProfile
                                handleAccessReducedAndWipeCache()
                            } else {
                                previousConfirmedProfile = newProfile
                            }
                        } else if (previousConfirmedProfile == null) {
                            previousConfirmedProfile = newProfile
                        }
                    } else if (
                        (!meta.isFromCache && seenActiveMemberOnce) ||
                        (member != null && member.memberStatus != MemberStatus.ACTIVE)
                    ) {
                        handleAccessRevokedAndWipeCache()
                    }
                }
        }

        // 3. Organisation doc
        viewModelScope.launch {
            _observerRestartTick
                .flatMapLatest {
                    orgRepo.observeOrganisation(orgId)
                        .catch { e ->
                            val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                            if (cause != null) {
                                handleFirestoreError(cause, OperationType.GET, "organisations/$orgId")
                                if (cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                    handleAccessRevokedAndWipeCache()
                                }
                                return@catch
                            }
                            if (e is CancellationException) throw e
                        }
                }
                .collect { org ->
                    _orgState.value = org
                    if (org != null && !sessionInitializedFromOrg) {
                        sessionInitializedFromOrg = true
                        _filter.value = _filter.value.copy(session = org.settings.currentSession)
                    }
                }
        }

        // 4. Allowed schools (scoped strictly to current member's access profile)
        viewModelScope.launch {
            combine(_observerRestartTick, _ownMember.map { it?.toAccessProfile() }) { tick, profile ->
                tick to profile
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile) ->
                    if (profile == null || !OrgAccessEvaluator.isActive(profile)) {
                        flowOf(CloudSchoolsSnapshot())
                    } else {
                        academicRepo.observeAllowedSchools(orgId, profile)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/schools")
                                    emit(CloudSchoolsSnapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { snap ->
                    schoolRecordsByLongId = snap.schools.associateBy { it.stableId }
                    _schoolsSnapshot.value = snap
                    val currentId = _filter.value.schoolId
                    if (snap.schools.isNotEmpty() && snap.schools.none { it.stableId == currentId }) {
                        val first = snap.schools.firstOrNull { it.active } ?: snap.schools.first()
                        _filter.value = _filter.value.copy(
                            schoolId = first.stableId,
                            className = first.classes.firstOrNull()?.name.orEmpty(),
                            selectedStudentId = null
                        )
                    }
                }
        }

        // 5. Active school's academic subcollections (students, marks, markEditLogs, coScholastic, termInfo)
        viewModelScope.launch {
            combine(
                _observerRestartTick,
                _ownMember.map { it?.toAccessProfile() },
                _schoolsSnapshot.map { it.schools },
                _filter.map { it.schoolId },
                _phase7Snapshot.map { StudentAttendanceAccessEvaluator.hasAnyClassTeacherSection(it.ownTeacherScope) }
            ) { tick, profile, schools, selectedSchoolId, isCt ->
                val targetSchool = schools.firstOrNull { it.stableId == selectedSchoolId }
                    ?: schools.firstOrNull { it.active }
                    ?: schools.firstOrNull()
                AcademicObserverKey(tick, profile, targetSchool?.docId, isCt)
            }
                .distinctUntilChanged()
                .flatMapLatest { key ->
                    val profile = key.profile
                    val schoolDocId = key.schoolDocId
                    if (profile == null || schoolDocId.isNullOrBlank() || !OrgAccessEvaluator.isActive(profile)) {
                        flowOf(null to CloudSchoolAcademicSnapshot())
                    } else {
                        academicRepo.observeSchoolAcademicData(orgId, schoolDocId, profile, isClassTeacher = key.isClassTeacher)
                            .map { schoolDocId to it }
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/schools/$schoolDocId")
                                    emit(schoolDocId to CloudSchoolAcademicSnapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { (schoolDocId, snap) ->
                    studentDocIdByLongId = snap.students.associate { it.entity.id to (it.docId to it.entity) }
                    existingMarkValuesByDocId = snap.marks.associate { it.docId to it.entity.rawValue }
                    existingMarkDocIds = existingMarkValuesByDocId.keys
                    existingCoDocIds = snap.coScholastic.map { it.docId }.toSet()
                    existingTermInfoDocIds = snap.termInfos.map { it.docId }.toSet()
                    _academicSnapshot.value = snap

                    if (!schoolDocId.isNullOrBlank()) {
                        evaluateMarkConflicts(schoolDocId, snap.marks)
                    }
                }
        }

        // 6. Members, Join Requests & Invite Codes (ONLY if allowed to view Members)
        viewModelScope.launch {
            combine(_observerRestartTick, _ownMember) { tick, member ->
                val p = member?.toAccessProfile()
                tick to (p != null && (OrgAccessEvaluator.isAdmin(p) || OrgAccessEvaluator.canViewModule(p, AppModule.MEMBERS)))
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, canViewMembers) ->
                    if (!canViewMembers) {
                        flowOf(Triple(emptyList<CloudMember>(), emptyList<CloudJoinRequest>(), emptyList<CloudInviteCode>()))
                    } else {
                        combine(
                            orgRepo.observeOrgMembers(orgId).catch { handleListenerError(it, "members"); emit(emptyList()) },
                            orgRepo.observeJoinRequests(orgId).catch { handleListenerError(it, "joinRequests"); emit(emptyList()) },
                            orgRepo.observeInviteCodes(orgId).catch { handleListenerError(it, "inviteCodes"); emit(emptyList()) }
                        ) { m, r, i -> Triple(m, r, i) }
                    }
                }
                .collect { (members, reqs, invites) ->
                    _orgMembers.value = members
                    _joinRequests.value = reqs
                    _inviteCodes.value = invites
                }
        }

        // 7. Audit Log (Admins ONLY)
        viewModelScope.launch {
            combine(_observerRestartTick, _ownMember) { tick, member ->
                tick to OrgAccessEvaluator.isAdmin(member?.toAccessProfile())
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, isAdmin) ->
                    if (!isAdmin) flowOf(emptyList())
                    else orgRepo.observeAuditLogs(orgId).catch { handleListenerError(it, "auditLog"); emit(emptyList()) }
                }
                .collect { _auditLogs.value = it }
        }

        // 8. Phase 4: Teachers, Period Structures, Class Assignments & Subject Rules
        // Active for AppModule.TEACHERS viewers/editors OR AppModule.TIMETABLE editors (for conflict checks)
        viewModelScope.launch {
            combine(_observerRestartTick, _ownMember.map { it?.toAccessProfile() }) { tick, profile ->
                tick to profile
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile) ->
                    val canReadPhase4 = profile != null && (
                        OrgAccessEvaluator.canViewModule(profile, AppModule.TEACHERS) ||
                            OrgAccessEvaluator.canEditModule(profile, AppModule.TIMETABLE)
                        )
                    if (!canReadPhase4) {
                        flowOf(CloudPhase4Snapshot())
                    } else {
                        phase4Repo.observePhase4Data(orgId, profile)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/teachers")
                                    emit(CloudPhase4Snapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { snap ->
                    _phase4Snapshot.value = snap
                }
        }

        // 9. Phase 5: Timetable Cells & Layout Mirror (strictly gated by AppModule.TIMETABLE)
        viewModelScope.launch {
            combine(_observerRestartTick, _ownMember.map { it?.toAccessProfile() }) { tick, profile ->
                tick to profile
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile) ->
                    if (profile == null || !OrgAccessEvaluator.canViewModule(profile, AppModule.TIMETABLE)) {
                        flowOf(CloudTimetableSnapshot())
                    } else {
                        timetableRepo.observeTimetable(orgId, profile)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/timetableCells")
                                    emit(CloudTimetableSnapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { snap ->
                    _timetableSnapshot.value = snap
                    evaluateTimetableConflicts(snap)
                }
        }

        // 10. Phase 6: Teacher Leaves, Substitutions, Auto-Generate Undo Snapshots
        viewModelScope.launch {
            combine(_observerRestartTick, _ownMember.map { it?.toAccessProfile() }) { tick, profile ->
                tick to profile
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile) ->
                    if (profile == null || !OrgAccessEvaluator.canViewModule(profile, AppModule.TIMETABLE)) {
                        flowOf(CloudPhase6Snapshot())
                    } else {
                        phase6Repo.observePhase6Data(orgId, profile, currentUserId)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/teacherLeaves")
                                    emit(CloudPhase6Snapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { snap ->
                    _phase6Snapshot.value = snap
                    evaluatePhase6Conflicts(snap)
                }
        }

        // 11. Phase 7: Teacher access scopes, own linked teacher profile, and teacher attendance
        viewModelScope.launch {
            combine(_observerRestartTick, _ownMember.map { it?.toAccessProfile() }) { tick, profile ->
                tick to profile
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile) ->
                    if (profile == null || !OrgAccessEvaluator.isActive(profile)) {
                        flowOf(CloudPhase7Snapshot())
                    } else {
                        phase7Repo.observePhase7Data(orgId, profile, currentUserId)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/teacherScopes")
                                    emit(CloudPhase7Snapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { snap ->
                    _phase7Snapshot.value = snap
                }
        }

        // 12. Phase 7: Multi-school pending marks observer across all allowed schools
        viewModelScope.launch {
            combine(
                _observerRestartTick,
                _ownMember.map { it?.toAccessProfile() },
                _schoolsSnapshot.map { it.schools }
            ) { tick, profile, schools ->
                Triple(tick, profile, schools)
            }
                .distinctUntilChanged { a, b ->
                    a.first == b.first && a.second == b.second && a.third.map { it.docId } == b.third.map { it.docId }
                }
                .flatMapLatest { (_, profile, schools) ->
                    if (profile == null || schools.isEmpty() || !OrgAccessEvaluator.isActive(profile)) {
                        flowOf(emptyList())
                    } else {
                        academicRepo.observeMultiSchoolPendingBundles(orgId, schools, profile)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/schools")
                                    emit(emptyList())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { bundles ->
                    _multiSchoolPendingBundles.value = bundles
                }
        }

        // 13. Phase 7: Auto-heal server-verified teacherScopes whenever Admin/Teachers-editor loads Phase 4 data
        viewModelScope.launch {
            combine(
                _ownMember.map { it?.toAccessProfile() },
                _phase4Snapshot
            ) { profile, p4 -> profile to p4 }
                .collect { (profile, p4) ->
                    if (profile == null) return@collect
                    val canWriteScopes = OrgAccessEvaluator.isAdmin(profile) ||
                        OrgAccessEvaluator.canEditModule(profile, AppModule.TEACHERS)
                    if (!canWriteScopes || p4.teachers.isEmpty()) return@collect
                    val derived = Phase7AccessEvaluator.buildTeacherScopes(p4.teachers, p4.classAssignments)
                    if (derived.isEmpty()) return@collect
                    val sig = derived.entries.sortedBy { it.key }.joinToString("||") { (uid, s) ->
                        "$uid:${s.teacherId}:${s.assignedClassSectionSubjects.sorted().joinToString(",")}:${s.classTeacherSections.sorted().joinToString(",")}"
                    }
                    if (sig != lastSyncedScopeSignature) {
                        val existingScopes = _phase7Snapshot.value.allTeacherScopes
                        val needsSync = derived.any { (uid, scope) ->
                            val ex = existingScopes[uid]
                            ex == null ||
                                ex.assignedClassSectionSubjects.toSet() != scope.assignedClassSectionSubjects.toSet() ||
                                ex.classTeacherSections.toSet() != scope.classTeacherSections.toSet()
                        }
                        if (needsSync) {
                            lastSyncedScopeSignature = sig
                            phase7Repo.syncTeacherScopes(orgId, derived)
                        } else {
                            lastSyncedScopeSignature = sig
                        }
                    }
                }
        }

        // 14. Phase 8: School calendars, daily student attendance sheets & edit logs
        viewModelScope.launch {
            combine(
                _observerRestartTick,
                _ownMember.map { it?.toAccessProfile() },
                _phase7Snapshot.map { it.ownTeacherScope }
            ) { tick, profile, scope ->
                Triple(tick, profile, scope)
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile, scope) ->
                    if (profile == null || !OrgAccessEvaluator.isActive(profile)) {
                        flowOf(CloudPhase8Snapshot())
                    } else {
                        phase8Repo.observePhase8Data(orgId, profile, scope, currentUserId)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/studentAttendance")
                                    emit(CloudPhase8Snapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { snap ->
                    _phase8Snapshot.value = snap
                }
        }

        // 15. Phase 8: Latest-edit-wins student attendance conflict notices
        viewModelScope.launch {
            phase8Repo.conflictNotices.collect { notices ->
                _attendanceConflicts.value = notices
            }
        }

        // 16. Phase 9: Fee structures, overrides, receipts, sent logs, notices & audit logs
        viewModelScope.launch {
            combine(
                _observerRestartTick,
                _ownMember.map { it?.toAccessProfile() }
            ) { tick, profile ->
                tick to profile
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile) ->
                    if (profile == null || !OrgAccessEvaluator.isActive(profile)) {
                        flowOf(CloudPhase9Snapshot())
                    } else {
                        phase9Repo.observePhase9Data(orgId, profile, currentUserId)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/feeReceipts")
                                    emit(CloudPhase9Snapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { snap ->
                    _phase9Snapshot.value = snap
                }
        }

        // 17. Phase 10: Certificates, Cash Book & Admission Enquiry
        viewModelScope.launch {
            combine(
                _observerRestartTick,
                _ownMember.map { it?.toAccessProfile() }
            ) { tick, profile ->
                tick to profile
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile) ->
                    if (profile == null || !OrgAccessEvaluator.isActive(profile)) {
                        flowOf(CloudPhase10Snapshot())
                    } else {
                        phase10Repo.observePhase10Data(orgId, profile, currentUserId)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/schoolCertificates")
                                    emit(CloudPhase10Snapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { snap ->
                    _phase10Snapshot.value = snap
                }
        }

        // 18. Observe Phase 11 (Exams, Stock, Transport & teacher self-duties)
        viewModelScope.launch {
            combine(
                _observerRestartTick,
                _ownMember.map { it?.toAccessProfile() }
            ) { tick, profile ->
                tick to profile
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile) ->
                    if (profile == null || !OrgAccessEvaluator.isActive(profile)) {
                        flowOf(CloudPhase11Snapshot())
                    } else {
                        phase11Repo.observePhase11Data(orgId, profile, currentUserId)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/examSchedules")
                                    emit(CloudPhase11Snapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { snap ->
                    _phase11Snapshot.value = snap
                }
        }

        // 13. Phase 12 Staff Salary (Owner-only by default; explicit grant for Admin; optional self-slips for Teachers)
        viewModelScope.launch {
            combine(_observerRestartTick, _ownMember.map { it?.toAccessProfile() }) { tick, profile ->
                tick to profile
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile) ->
                    if (profile == null || !OrgAccessEvaluator.isActive(profile)) {
                        flowOf(CloudPhase12Snapshot())
                    } else {
                        phase12Repo.observePhase12Data(orgId, profile)
                            .catch { e ->
                                val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
                                if (cause != null) {
                                    handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/salaryMonthlyRecords")
                                    emit(CloudPhase12Snapshot())
                                    return@catch
                                }
                                if (e is CancellationException) throw e
                            }
                    }
                }
                .collect { snap ->
                    _phase12Snapshot.value = snap
                }
        }
    }

    private fun evaluatePhase6Conflicts(snap: CloudPhase6Snapshot) {
        val pendingEdits = offlinePrefs.loadPendingPhase6Edits(currentUserId, orgId)
        if (pendingEdits.isEmpty()) return
        val res = SubstitutionEngine.detectPhase6Conflicts(
            pendingEdits = pendingEdits,
            confirmedLeaves = snap.leaves,
            confirmedSubstitutions = snap.substitutions,
            hasConfirmedServerSnapshot = snap.hasConfirmedServerSnapshot,
            currentUid = currentUserId
        )
        if (res.remainingPending.size != pendingEdits.size) {
            offlinePrefs.replacePendingPhase6EditsForOrg(currentUserId, orgId, res.remainingPending)
        }
        if (res.detectedConflicts.isNotEmpty()) {
            val merged = (_phase6Conflicts.value + res.detectedConflicts)
                .distinctBy { "${it.kind}_${it.docId}_${it.winningSummary}_${it.yourSummary}" }
            _phase6Conflicts.value = merged
        }
    }

    private fun evaluateTimetableConflicts(snap: CloudTimetableSnapshot) {
        val pendingEdits = offlinePrefs.loadPendingTimetableEdits(currentUserId, orgId)
        if (pendingEdits.isEmpty()) return
        val res = TimetableEngine.detectTimetableCellConflicts(
            pendingEdits = pendingEdits,
            confirmedServerCells = snap.cells,
            hasAnyConfirmedSnapshot = snap.hasConfirmedServerSnapshot,
            currentUid = currentUserId
        )
        if (res.remainingPending.size != pendingEdits.size) {
            offlinePrefs.replacePendingTimetableEditsForOrg(currentUserId, orgId, res.remainingPending)
        }
        if (res.detectedConflicts.isNotEmpty()) {
            val merged = (_timetableConflicts.value + res.detectedConflicts)
                .distinctBy { "${it.cellId}_${it.winningSummary}_${it.yourSummary}" }
            _timetableConflicts.value = merged
        }
    }

    private fun evaluateMarkConflicts(
        schoolDocId: String,
        cloudMarks: List<com.example.data.cloud.CloudMarkRecord>
    ) {
        val pendingEdits = offlinePrefs.loadPendingMarkEdits(currentUserId, orgId, schoolDocId)
        if (pendingEdits.isEmpty()) return
        val observations = cloudMarks.map { m ->
            ServerMarkObservation(
                markDocId = m.docId,
                studentDocId = m.studentDocId,
                rawValue = m.entity.rawValue,
                updatedByUid = m.updatedByUid,
                updatedByName = m.updatedByName,
                clientEditedAtMs = m.clientEditedAtMs,
                hasPendingWrites = m.hasPendingWrites,
                isFromCache = m.isFromCache
            )
        }
        val res = OfflineSyncEngine.detectMarkConflicts(
            pendingEdits = pendingEdits,
            serverMarks = observations,
            currentUid = currentUserId
        )
        if (res.remainingPending.size != pendingEdits.size) {
            offlinePrefs.replacePendingMarkEditsForSchool(currentUserId, orgId, schoolDocId, res.remainingPending)
        }
        if (res.detectedConflicts.isNotEmpty()) {
            val existing = _markConflicts.value
            val merged = (existing + res.detectedConflicts)
                .distinctBy { "${it.markDocId}_${it.winningValue}_${it.yourValue}" }
            _markConflicts.value = merged
        }
    }

    private fun handleAccessRevokedAndWipeCache() {
        if (_membershipRevoked.value) return
        _membershipRevoked.value = true
        viewModelScope.launch(Dispatchers.IO) {
            offlinePrefs.clearPendingMarkEditsForOrg(currentUserId, orgId)
            offlinePrefs.clearPendingTimetableEditsForOrg(currentUserId, orgId)
            offlinePrefs.clearPendingPhase6EditsForOrg(currentUserId, orgId)
            offlinePrefs.setLastSelectedOrgId(currentUserId, null)
            FirestoreProvider.resetLocalCacheAndReinit(appContext)
        }
    }

    private fun handleAccessReducedAndWipeCache() {
        if (isResettingCache) return
        isResettingCache = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                FirestoreProvider.resetLocalCacheAndReinit(appContext)
            }
            _schoolsSnapshot.value = CloudSchoolsSnapshot()
            _academicSnapshot.value = CloudSchoolAcademicSnapshot()
            _phase4Snapshot.value = CloudPhase4Snapshot()
            _timetableSnapshot.value = CloudTimetableSnapshot()
            _phase6Snapshot.value = CloudPhase6Snapshot()
            _phase7Snapshot.value = CloudPhase7Snapshot()
            _multiSchoolPendingBundles.value = emptyList()
            _observerRestartTick.value += 1
            isResettingCache = false
            emitMessage("Your access was updated by an admin. Cached data for removed access has been cleared.")
        }
    }

    fun dismissConflictBanner() {
        _markConflicts.value = emptyList()
    }

    fun dismissTimetableConflictBanner() {
        _timetableConflicts.value = emptyList()
    }

    fun dismissPhase6ConflictBanner() {
        _phase6Conflicts.value = emptyList()
    }

    fun retrySync() {
        if (!connectivityMonitor.isCurrentlyOnline()) {
            emitMessage("Still offline — changes are safely saved on this phone and will sync once online.")
            return
        }
        val retriedPhase4 = phase4Repo.retrySync()
        val retriedPhase5 = timetableRepo.retrySync()
        val retriedPhase6 = phase6Repo.retrySync()
        val retriedPhase7 = phase7Repo.retrySync()
        val retriedPhase8 = phase8Repo.retrySync()
        val retriedPhase9 = phase9Repo.retrySync()
        val retriedPhase10 = phase10Repo.retrySync()
        val retriedPhase11 = phase11Repo.retrySync()
        val retriedPhase12 = phase12Repo.retrySync()
        academicRepo.retrySync { msg ->
            emitMessage(
                if (retriedPhase4 || retriedPhase5 || retriedPhase6 || retriedPhase7 || retriedPhase8 || retriedPhase9 || retriedPhase10 || retriedPhase11 || retriedPhase12) {
                    "Retrying pending sync…"
                } else {
                    msg
                }
            )
        }
    }

    fun clearSyncError() {
        academicRepo.clearSyncError()
        phase4Repo.clearSyncError()
        timetableRepo.clearSyncError()
        phase6Repo.clearSyncError()
        phase7Repo.clearSyncError()
        phase8Repo.clearSyncError()
        phase9Repo.clearSyncError()
        phase10Repo.clearSyncError()
        phase11Repo.clearSyncError()
        phase12Repo.clearSyncError()
    }

    private fun handleListenerError(e: Throwable, sub: String) {
        val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
        if (cause != null) {
            handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/$sub")
            return
        }
        if (e is CancellationException) throw e
    }

    internal fun emitMessage(message: String) {
        _snackbar.tryEmit(message)
    }

    internal fun activeSchoolRecord(schoolId: Long = uiState.value.activeSchool.id): CloudSchoolRecord? =
        schoolRecordsByLongId[schoolId]

    // -------------------------------------------------------------------------
    // SCHOOL SWITCHER & FILTER ACTIONS
    // -------------------------------------------------------------------------
    fun selectSchool(schoolId: Long) {
        val state = uiState.value
        val targetSchool = state.schools.firstOrNull { it.id == schoolId } ?: return
        val targetClasses = state.classesForSchool(schoolId)
        val matchingClass = targetClasses.firstOrNull {
            it.name.equals(state.effectiveClassName, ignoreCase = true)
        }?.name ?: targetClasses.firstOrNull()?.name ?: ""

        _filter.value = _filter.value.copy(
            schoolId = targetSchool.id,
            className = matchingClass,
            selectedStudentId = null
        )
        clearPdfPreview()
    }

    fun selectSession(session: String) {
        _filter.value = _filter.value.copy(session = session, selectedStudentId = null)
    }

    fun selectClass(className: String) {
        _filter.value = _filter.value.copy(className = className, selectedStudentId = null)
    }

    fun selectSection(section: String) {
        _filter.value = _filter.value.copy(section = section, selectedStudentId = null)
    }

    fun selectStudent(studentId: Long?) {
        _filter.value = _filter.value.copy(selectedStudentId = studentId)
    }

    fun focusStudent(student: StudentEntity) {
        _filter.value = FilterSelection(
            schoolId = student.schoolId,
            session = student.session,
            className = student.className,
            section = student.section.uppercase().ifEmpty { "A" },
            selectedStudentId = student.id
        )
    }

    private fun clearPdfPreview() {
        val oldPages = _pdfState.value.previewPages
        _pdfState.value = PdfPreviewState()
        oldPages.forEach { if (!it.isRecycled) it.recycle() }
    }

    // -------------------------------------------------------------------------
    // SCHOOLS CRUD (Cloud Firestore)
    // -------------------------------------------------------------------------
    fun createSchool(
        name: String,
        address: String,
        mobile: String,
        email: String,
        logoBase64: String,
        active: Boolean,
        templateSource: SchoolTemplateSource,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        if (!uiState.value.canEdit(AppModule.SETTINGS)) {
            onResult(false, "You do not have permission to create schools.")
            return
        }
        viewModelScope.launch {
            val res = academicRepo.createSchool(
                orgId = orgId,
                name = name,
                address = address,
                mobile = mobile,
                email = email,
                logoBase64 = logoBase64,
                active = active,
                templateSource = templateSource,
                existingSchools = _schoolsSnapshot.value.schools
            )
            res.onSuccess { newDocId ->
                if (active) {
                    _filter.value = _filter.value.copy(
                        schoolId = StableDocId.toLong(newDocId),
                        selectedStudentId = null
                    )
                    clearPdfPreview()
                }
                emitMessage("Created school '${name.trim()}'")
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, friendlyFirestoreErrorMessage(err, "Unable to create school."))
            }
        }
    }

    fun updateSchool(
        school: SchoolEntity,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val rec = activeSchoolRecord(school.id) ?: run {
            onResult(false, "School not found.")
            return
        }
        viewModelScope.launch {
            val updated = rec.copy(
                name = school.name.trim(),
                address = school.address.trim(),
                mobile = school.mobile.trim(),
                email = school.email.trim(),
                logoBase64 = school.logoBase64,
                active = school.active
            )
            val res = academicRepo.updateSchoolRecord(orgId, updated)
            res.onSuccess {
                emitMessage("Updated '${school.name.trim()}'")
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, friendlyFirestoreErrorMessage(err, "Unable to update school."))
            }
        }
    }

    fun setSchoolActive(school: SchoolEntity, active: Boolean) {
        val rec = activeSchoolRecord(school.id) ?: return
        viewModelScope.launch {
            val res = academicRepo.updateSchoolRecord(orgId, rec.copy(active = active))
            res.onSuccess {
                emitMessage(if (active) "Activated '${school.name}'" else "Deactivated '${school.name}'")
                if (!active && _filter.value.schoolId == school.id) {
                    val fallback = uiState.value.schools.firstOrNull { it.id != school.id && it.active }
                    if (fallback != null) selectSchool(fallback.id)
                }
            }.onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Unable to update school status."))
            }
        }
    }

    fun deleteSchool(school: SchoolEntity) {
        val rec = activeSchoolRecord(school.id) ?: return
        val count = uiState.value.studentCountForSchool(school.id)
        viewModelScope.launch {
            val res = academicRepo.deleteSchool(orgId, rec, count)
            res.onSuccess {
                emitMessage("Deleted school '${school.name}'")
            }.onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Unable to delete school."))
            }
        }
    }

    // -------------------------------------------------------------------------
    // PER-SCHOOL CLASS GROUPS, CLASSES & SUBJECTS CRUD
    // -------------------------------------------------------------------------
    fun addClassGroup(
        schoolId: Long,
        name: String,
        shortLabel: String,
        initialSubjects: List<String>,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val rec = activeSchoolRecord(schoolId) ?: run {
            onResult(false, "School not found.")
            return
        }
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            onResult(false, "Class group name is required.")
            return
        }
        val nextOrder = (rec.classGroups.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val newItem = CloudClassGroupItem(
            id = "grp_${UUID.randomUUID().toString().take(8)}",
            name = cleanName,
            shortLabel = shortLabel.trim().ifBlank { cleanName },
            sortOrder = nextOrder,
            templateKey = "",
            subjects = initialSubjects.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        )
        viewModelScope.launch {
            val res = academicRepo.updateSchoolRecord(orgId, rec.copy(classGroups = rec.classGroups + newItem))
            res.onSuccess {
                emitMessage("Added class group '$cleanName'")
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, friendlyFirestoreErrorMessage(err, "Unable to add class group."))
            }
        }
    }

    fun renameClassGroup(group: ClassGroupEntity, newName: String, newShortLabel: String) {
        val rec = activeSchoolRecord(group.schoolId) ?: return
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return
        val updatedGroups = rec.classGroups.map { item ->
            if (StableDocId.toLong("${rec.docId}_grp_${item.id}") == group.id) {
                item.copy(name = cleanName, shortLabel = newShortLabel.trim().ifBlank { cleanName })
            } else item
        }
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classGroups = updatedGroups))
                .onSuccess { emitMessage("Updated class group '$cleanName'") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Unable to rename class group.")) }
        }
    }

    fun deleteClassGroup(group: ClassGroupEntity) {
        val rec = activeSchoolRecord(group.schoolId) ?: return
        val target = rec.classGroups.firstOrNull { StableDocId.toLong("${rec.docId}_grp_${it.id}") == group.id } ?: return
        if (rec.classes.any { it.classGroupId == target.id }) {
            emitMessage("Reassign or remove classes in '${group.name}' first.")
            return
        }
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classGroups = rec.classGroups.filterNot { it.id == target.id }))
                .onSuccess { emitMessage("Deleted class group '${group.name}'") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Unable to delete class group.")) }
        }
    }

    fun addSchoolClass(
        schoolId: Long,
        classGroupId: Long,
        name: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val rec = activeSchoolRecord(schoolId) ?: run {
            onResult(false, "School not found.")
            return
        }
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            onResult(false, "Class name is required.")
            return
        }
        if (rec.classes.any { it.name.equals(cleanName, ignoreCase = true) }) {
            onResult(false, "Class '$cleanName' already exists in ${rec.name}.")
            return
        }
        val groupDocId = rec.classGroups.firstOrNull {
            StableDocId.toLong("${rec.docId}_grp_${it.id}") == classGroupId
        }?.id ?: rec.classGroups.firstOrNull()?.id ?: ""
        val nextOrder = (rec.classes.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val newClass = CloudSchoolClassItem(
            id = "cls_${StableDocId.sanitizeSegment(cleanName).lowercase()}_${UUID.randomUUID().toString().take(4)}",
            classGroupId = groupDocId,
            name = cleanName,
            sortOrder = nextOrder
        )
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classes = rec.classes + newClass))
                .onSuccess {
                    emitMessage("Added class '$cleanName'")
                    onResult(true, null)
                }
                .onFailure { err ->
                    onResult(false, friendlyFirestoreErrorMessage(err, "Unable to add class."))
                }
        }
    }

    fun updateSchoolClass(
        schoolClass: SchoolClassEntity,
        newName: String,
        newClassGroupId: Long,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val rec = activeSchoolRecord(schoolClass.schoolId) ?: return
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) {
            onResult(false, "Class name is required.")
            return
        }
        val groupDocId = rec.classGroups.firstOrNull {
            StableDocId.toLong("${rec.docId}_grp_${it.id}") == newClassGroupId
        }?.id ?: rec.classGroups.firstOrNull()?.id ?: ""
        val updatedClasses = rec.classes.map { item ->
            if (StableDocId.toLong("${rec.docId}_cls_${item.id}") == schoolClass.id) {
                item.copy(name = cleanName, classGroupId = groupDocId)
            } else item
        }
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classes = updatedClasses))
                .onSuccess {
                    if (_filter.value.schoolId == schoolClass.schoolId &&
                        _filter.value.className.equals(schoolClass.name, ignoreCase = true)
                    ) {
                        _filter.value = _filter.value.copy(className = cleanName)
                    }
                    emitMessage("Updated class '$cleanName'")
                    onResult(true, null)
                }
                .onFailure { err ->
                    onResult(false, friendlyFirestoreErrorMessage(err, "Unable to update class."))
                }
        }
    }

    fun moveSchoolClass(schoolId: Long, classId: Long, moveUp: Boolean) {
        val rec = activeSchoolRecord(schoolId) ?: return
        val sorted = rec.classes.sortedBy { it.sortOrder }.toMutableList()
        val idx = sorted.indexOfFirst { StableDocId.toLong("${rec.docId}_cls_${it.id}") == classId }
        if (idx < 0) return
        val swapIdx = if (moveUp) idx - 1 else idx + 1
        if (swapIdx !in sorted.indices) return
        val tmp = sorted[idx]
        sorted[idx] = sorted[swapIdx]
        sorted[swapIdx] = tmp
        val reindexed = sorted.mapIndexed { i, item -> item.copy(sortOrder = i) }
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classes = reindexed))
        }
    }

    fun deleteSchoolClass(schoolClass: SchoolClassEntity) {
        val rec = activeSchoolRecord(schoolClass.schoolId) ?: return
        if (uiState.value.studentCountForClass(schoolClass) > 0) {
            emitMessage("Cannot remove class '${schoolClass.name}' while students are assigned to it.")
            return
        }
        val remaining = rec.classes.filterNot { StableDocId.toLong("${rec.docId}_cls_${it.id}") == schoolClass.id }
            .mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classes = remaining))
                .onSuccess { emitMessage("Removed class '${schoolClass.name}'") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Unable to remove class.")) }
        }
    }

    fun addSubject(schoolId: Long, classGroupId: Long, name: String) {
        val rec = activeSchoolRecord(schoolId) ?: return
        val clean = name.trim().uppercase()
        if (clean.isEmpty()) return
        val updatedGroups = rec.classGroups.map { grp ->
            if (StableDocId.toLong("${rec.docId}_grp_${grp.id}") == classGroupId) {
                if (grp.subjects.any { it.equals(clean, ignoreCase = true) }) grp
                else grp.copy(subjects = grp.subjects + clean)
            } else grp
        }
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classGroups = updatedGroups))
                .onSuccess { emitMessage("Added subject $clean") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Unable to add subject.")) }
        }
    }

    fun renameSubject(subject: SubjectEntity, newName: String) {
        val rec = activeSchoolRecord(subject.schoolId) ?: return
        val clean = newName.trim().uppercase()
        if (clean.isEmpty()) return
        val updatedGroups = rec.classGroups.map { grp ->
            if (StableDocId.toLong("${rec.docId}_grp_${grp.id}") == subject.classGroupId) {
                grp.copy(subjects = grp.subjects.map { if (it == subject.name) clean else it })
            } else grp
        }
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classGroups = updatedGroups))
                .onSuccess { emitMessage("Renamed subject to $clean") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Unable to rename subject.")) }
        }
    }

    fun moveSubject(classGroupId: Long, subjectId: Long, moveUp: Boolean) {
        val state = uiState.value
        val rec = activeSchoolRecord(state.activeSchool.id) ?: return
        val subEntity = state.subjects.firstOrNull { it.id == subjectId } ?: return
        val updatedGroups = rec.classGroups.map { grp ->
            if (StableDocId.toLong("${rec.docId}_grp_${grp.id}") == classGroupId) {
                val list = grp.subjects.toMutableList()
                val idx = list.indexOf(subEntity.name)
                val swapIdx = if (moveUp) idx - 1 else idx + 1
                if (idx >= 0 && swapIdx in list.indices) {
                    val tmp = list[idx]
                    list[idx] = list[swapIdx]
                    list[swapIdx] = tmp
                }
                grp.copy(subjects = list)
            } else grp
        }
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classGroups = updatedGroups))
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        val rec = activeSchoolRecord(subject.schoolId) ?: return
        val updatedGroups = rec.classGroups.map { grp ->
            if (StableDocId.toLong("${rec.docId}_grp_${grp.id}") == subject.classGroupId) {
                grp.copy(subjects = grp.subjects.filterNot { it == subject.name })
            } else grp
        }
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classGroups = updatedGroups))
                .onSuccess { emitMessage("Removed subject ${subject.name}") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Unable to remove subject.")) }
        }
    }

    fun resetGroupSubjects(group: ClassGroupEntity) {
        val rec = activeSchoolRecord(group.schoolId) ?: return
        if (group.templateKey.isBlank()) {
            emitMessage("No default template for this custom group.")
            return
        }
        val defaults = DefaultSubjects.forTemplateKey(group.templateKey)
        val updatedGroups = rec.classGroups.map { grp ->
            if (StableDocId.toLong("${rec.docId}_grp_${grp.id}") == group.id) {
                grp.copy(subjects = defaults)
            } else grp
        }
        viewModelScope.launch {
            academicRepo.updateSchoolRecord(orgId, rec.copy(classGroups = updatedGroups))
                .onSuccess { emitMessage("Reset ${group.name} subjects to default") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Unable to reset subjects.")) }
        }
    }

    fun deleteLegacyMarksData() {
        emitMessage("Cloud records enforce the unified 10 / 30 / 60 structure automatically.")
    }

    // -------------------------------------------------------------------------
    // STUDENTS CRUD & CSV IMPORT (Cloud Firestore)
    // -------------------------------------------------------------------------
    suspend fun suggestRollNo(schoolId: Long, session: String, className: String, section: String): Int {
        val existingMax = uiState.value.allStudents
            .filter {
                it.schoolId == schoolId &&
                    it.session == session &&
                    it.className.equals(className, ignoreCase = true) &&
                    it.section.equals(section, ignoreCase = true)
            }
            .maxOfOrNull { it.rollNo } ?: 0
        return existingMax + 1
    }

    fun saveStudent(student: StudentEntity, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (!uiState.value.canEdit(AppModule.STUDENTS) && !uiState.value.canEditStudentRegister) {
            onResult(false, "You only have view access to students.")
            return
        }
        val rec = activeSchoolRecord(student.schoolId) ?: activeSchoolRecord() ?: run {
            onResult(false, "Active school not found.")
            return
        }
        val existingDocId = if (student.id != 0L) studentDocIdByLongId[student.id]?.first else null
        val dup = uiState.value.allStudents.any {
            it.schoolId == rec.stableId &&
                it.session == student.session &&
                it.admNo.equals(student.admNo.trim(), ignoreCase = true) &&
                it.id != student.id
        }
        if (dup) {
            onResult(false, "Admission No. '${student.admNo.trim()}' already exists in this session.")
            return
        }
        val clsDocId = rec.classes.firstOrNull {
            it.name.equals(student.className.trim(), ignoreCase = true)
        }?.id.orEmpty()

        viewModelScope.launch {
            val res = academicRepo.saveStudent(
                orgId = orgId,
                schoolDocId = rec.docId,
                existingDocId = existingDocId,
                student = student,
                classDocId = clsDocId
            )
            res.onSuccess { docId ->
                _filter.value = _filter.value.copy(
                    schoolId = rec.stableId,
                    session = student.session,
                    className = student.className,
                    section = student.section.uppercase().ifEmpty { "A" },
                    selectedStudentId = StableDocId.toLong(docId)
                )
                emitMessage(if (existingDocId == null) "Added ${student.name}" else "Updated ${student.name}")
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, friendlyFirestoreErrorMessage(err, "Unable to save student."))
            }
        }
    }

    fun deleteStudent(student: StudentEntity) {
        if (!uiState.value.canEdit(AppModule.STUDENTS) && !uiState.value.canEditStudentRegister) {
            emitMessage("You do not have permission to delete students.")
            return
        }
        val rec = activeSchoolRecord(student.schoolId) ?: return
        val docId = studentDocIdByLongId[student.id]?.first ?: return
        viewModelScope.launch {
            academicRepo.deleteStudent(orgId, rec.docId, docId, student.name)
                .onSuccess {
                    if (_filter.value.selectedStudentId == student.id) {
                        _filter.value = _filter.value.copy(selectedStudentId = null)
                    }
                    emitMessage("Deleted ${student.name}")
                }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Unable to delete student.")) }
        }
    }

    fun importStudentsFromCsvUri(uri: Uri) {
        if (!uiState.value.canEdit(AppModule.STUDENTS) && !uiState.value.canEditStudentRegister) {
            emitMessage("You do not have permission to import students.")
            return
        }
        val state = uiState.value
        val rec = activeSchoolRecord(state.activeSchool.id) ?: return
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                val csvText = PdfExportHelper.readTextFromUri(appContext, uri)
                    ?: return@withContext "Could not read CSV file."
                val startRoll = suggestRollNo(
                    rec.stableId,
                    state.filter.session,
                    state.effectiveClassName,
                    state.filter.section
                )
                val parsed = BackupAndCsvManager.parseStudentsCsv(
                    csvText = csvText,
                    schoolId = rec.stableId,
                    schoolClasses = state.activeSchoolClasses,
                    defaultSession = state.filter.session,
                    defaultClass = state.effectiveClassName,
                    defaultSection = state.filter.section,
                    startingRollNo = startRoll
                )
                var imported = 0
                var duplicates = 0
                val existingAdms = state.allStudents
                    .filter { it.schoolId == rec.stableId && it.session == state.filter.session }
                    .map { it.admNo.trim().lowercase() }
                    .toMutableSet()
                for (st in parsed.parsedStudents) {
                    val key = st.admNo.trim().lowercase()
                    if (key in existingAdms) {
                        duplicates++
                        continue
                    }
                    val clsDocId = rec.classes.firstOrNull {
                        it.name.equals(st.className, ignoreCase = true)
                    }?.id.orEmpty()
                    val r = academicRepo.saveStudent(orgId, rec.docId, null, st, clsDocId)
                    if (r.isSuccess) {
                        existingAdms.add(key)
                        imported++
                    } else {
                        duplicates++
                    }
                }
                buildString {
                    append("CSV Import (${rec.name}): $imported added")
                    if (duplicates > 0) append(", $duplicates skipped")
                    if (parsed.skippedRows > 0) append(", ${parsed.skippedRows} invalid skipped")
                }
            }
            emitMessage(summary)
        }
    }

    // -------------------------------------------------------------------------
    // MARKS ENTRY + VALIDATION + UNDO (Cloud Firestore)
    // -------------------------------------------------------------------------
    fun saveMark(
        student: StudentEntity,
        subjectName: String,
        component: MarkComponent,
        rawInput: String
    ): MarkValidationResult {
        if (!uiState.value.canEditMarkFor(student, subjectName, component.key)) {
            emitMessage("You do not have permission to edit ${component.shortLabel} marks for ${student.className}-${student.section} • $subjectName.")
            return MarkValidationResult.Invalid("Access restricted")
        }
        val validation = ReportCalculations.validateMarkInput(rawInput, component.maxMarks)
        if (validation is MarkValidationResult.Valid) {
            val rec = activeSchoolRecord(student.schoolId) ?: return validation
            val stuDocId = studentDocIdByLongId[student.id]?.first ?: return validation
            val prev = uiState.value.marksMapForStudent(student.id)[subjectName to component.key].orEmpty()
            val markDocId = OrgAcademicRepository.makeMarkDocId(stuDocId, subjectName, component.key)
            val exists = markDocId in existingMarkDocIds
            val nowMs = System.currentTimeMillis()

            if (prev != validation.normalized) {
                offlinePrefs.upsertPendingMarkEdit(
                    uid = currentUserId,
                    edit = LocalPendingMarkEdit(
                        orgId = orgId,
                        schoolDocId = rec.docId,
                        markDocId = markDocId,
                        studentDocId = stuDocId,
                        studentName = student.name,
                        className = student.className,
                        subjectName = subjectName,
                        componentKey = component.key,
                        localValue = validation.normalized,
                        clientEditedAtMs = nowMs
                    )
                )
            }

            val res = academicRepo.saveMark(
                orgId = orgId,
                schoolDocId = rec.docId,
                studentDocId = stuDocId,
                studentName = student.name,
                className = student.className,
                subjectName = subjectName,
                componentKey = component.key,
                oldValue = prev,
                normalizedValue = validation.normalized,
                existingMarkExists = exists,
                clientEditedAtMs = nowMs
            )
            res.onSuccess {
                if (prev != validation.normalized) {
                    _lastUndo.value = MarkUndoSnapshot(
                        studentId = student.id,
                        studentName = student.name,
                        subjectName = subjectName,
                        componentKey = component.key,
                        componentLabel = component.shortLabel,
                        previousRawValue = prev
                    )
                }
            }.onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Unable to save mark."))
            }
        }
        return validation
    }

    fun undoLastMarkEdit() {
        val snapshot = _lastUndo.value ?: return
        val (_, student) = studentDocIdByLongId[snapshot.studentId] ?: return
        val rec = activeSchoolRecord(student.schoolId) ?: return
        val stuDocId = studentDocIdByLongId[snapshot.studentId]?.first ?: return
        val markDocId = OrgAcademicRepository.makeMarkDocId(stuDocId, snapshot.subjectName, snapshot.componentKey)
        val exists = markDocId in existingMarkDocIds
        val currentVal = uiState.value.marksMapForStudent(student.id)[snapshot.subjectName to snapshot.componentKey].orEmpty()
        val nowMs = System.currentTimeMillis()

        offlinePrefs.upsertPendingMarkEdit(
            uid = currentUserId,
            edit = LocalPendingMarkEdit(
                orgId = orgId,
                schoolDocId = rec.docId,
                markDocId = markDocId,
                studentDocId = stuDocId,
                studentName = snapshot.studentName,
                className = student.className,
                subjectName = snapshot.subjectName,
                componentKey = snapshot.componentKey,
                localValue = snapshot.previousRawValue,
                clientEditedAtMs = nowMs
            )
        )

        academicRepo.saveMark(
            orgId = orgId,
            schoolDocId = rec.docId,
            studentDocId = stuDocId,
            studentName = snapshot.studentName,
            className = student.className,
            subjectName = snapshot.subjectName,
            componentKey = snapshot.componentKey,
            oldValue = currentVal,
            normalizedValue = snapshot.previousRawValue,
            existingMarkExists = exists,
            clientEditedAtMs = nowMs
        ).onSuccess {
            _lastUndo.value = null
            emitMessage("Undid mark edit for ${snapshot.studentName} (${snapshot.subjectName})")
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Unable to undo mark edit."))
        }
    }

    // -------------------------------------------------------------------------
    // STANDARDIZED MARKS CSV TEMPLATE EXPORT & IMPORT
    // -------------------------------------------------------------------------
    fun exportMarksCsvTemplateToUri(destinationUri: Uri) {
        val state = uiState.value
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val students = state.filteredStudents
                val subjects = state.subjectsForClass(state.effectiveClassName)
                val existingByStudent = students.associate { st ->
                    st.id to state.marksMapForStudent(st.id)
                }
                val csv = BackupAndCsvManager.generateMarksCsvTemplate(
                    session = state.filter.session,
                    className = state.effectiveClassName,
                    section = state.filter.section,
                    students = students,
                    subjects = subjects,
                    existingMarksByStudent = existingByStudent
                )
                PdfExportHelper.writeTextToUri(appContext, csv, destinationUri)
            }
            emitMessage(
                if (ok) "Saved marks CSV template for ${state.activeSchool.name} Class ${state.effectiveClassName}-${state.filter.section}"
                else "Could not save marks CSV template"
            )
        }
    }

    fun prepareMarksCsvImportFromUri(uri: Uri) {
        val state = uiState.value
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                val csvText = PdfExportHelper.readTextFromUri(appContext, uri)
                    ?: return@withContext null
                val subjectsByGroupId = state.activeSchoolGroups.associate { grp ->
                    val configured = state.subjectsForGroupId(grp.id).map { it.name }
                    grp.id to configured.ifEmpty { DefaultSubjects.forTemplateKey(grp.templateKey) }
                }
                val parsed = BackupAndCsvManager.parseMarksCsv(
                    csvText = csvText,
                    students = state.allStudents.filter { it.schoolId == state.activeSchool.id },
                    schoolClasses = state.activeSchoolClasses,
                    subjectsByGroupId = subjectsByGroupId,
                    defaultSession = state.filter.session,
                    defaultClass = state.effectiveClassName,
                    defaultSection = state.filter.section
                )
                MarksCsvImportPreview(
                    validMarks = parsed.validMarks,
                    matchedStudentCount = parsed.matchedStudentIds.size,
                    processedRowCount = parsed.processedRowCount,
                    skippedEmptyCells = parsed.skippedEmptyCells,
                    issues = parsed.issues
                )
            }
            if (preview == null) emitMessage("Could not read marks CSV file.")
            else _pendingMarksCsvImport.value = preview
        }
    }

    fun cancelPendingMarksCsvImport() {
        _pendingMarksCsvImport.value = null
    }

    fun confirmMarksCsvImport() {
        val preview = _pendingMarksCsvImport.value ?: return
        _pendingMarksCsvImport.value = null
        if (preview.validMarks.isEmpty()) {
            emitMessage("No valid marks to import.")
            return
        }
        val state = uiState.value
        val rec = activeSchoolRecord() ?: return
        val allowedEntries = preview.validMarks.filter { mark ->
            val stu = studentDocIdByLongId[mark.studentId]?.second ?: return@filter false
            state.canEditMarkFor(stu, mark.subjectName, mark.componentKey)
        }
        val skippedForAccess = preview.validMarks.size - allowedEntries.size
        if (allowedEntries.isEmpty()) {
            emitMessage("No imported marks are within your assigned class-section-subjects and open exams.")
            return
        }
        val infoMap = studentDocIdByLongId.mapValues {
            Triple(it.value.first, it.value.second.className, it.value.second.name)
        }
        viewModelScope.launch {
            val res = academicRepo.saveMarksBatch(
                orgId = orgId,
                schoolDocId = rec.docId,
                entries = allowedEntries,
                studentInfoByLongId = infoMap,
                existingMarksByDocId = existingMarkValuesByDocId,
                onBatchItemEnqueued = { markDocId, stuDocId, stuName, clsName, subName, compKey, newVal, editedAtMs ->
                    offlinePrefs.upsertPendingMarkEdit(
                        uid = currentUserId,
                        edit = LocalPendingMarkEdit(
                            orgId = orgId,
                            schoolDocId = rec.docId,
                            markDocId = markDocId,
                            studentDocId = stuDocId,
                            studentName = stuName,
                            className = clsName,
                            subjectName = subName,
                            componentKey = compKey,
                            localValue = newVal,
                            clientEditedAtMs = editedAtMs
                        )
                    )
                }
            )
            res.onSuccess { count ->
                val issueSuffix = if (preview.issues.isNotEmpty()) " (${preview.issues.size} warning(s) skipped)" else ""
                val accessSuffix = if (skippedForAccess > 0) " ($skippedForAccess outside your assigned scope skipped)" else ""
                emitMessage("Imported $count mark(s)$issueSuffix$accessSuffix")
            }.onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Marks CSV import failed."))
            }
        }
    }

    // -------------------------------------------------------------------------
    // CO-SCHOLASTIC & ATTENDANCE
    // -------------------------------------------------------------------------
    fun saveCoScholasticGrade(studentId: Long, termNo: Int, areaCode: String, grade: String) {
        val (stuDocId, student) = studentDocIdByLongId[studentId] ?: return
        if (!uiState.value.canEditCoScholasticFor(student)) {
            emitMessage("Only the Class Teacher of ${student.className}-${student.section} (or Admin) can edit co-scholastic grades.")
            return
        }
        val rec = activeSchoolRecord(student.schoolId) ?: return
        val docId = OrgAcademicRepository.makeCoDocId(stuDocId, termNo, areaCode)
        viewModelScope.launch {
            academicRepo.saveCoScholastic(
                orgId = orgId,
                schoolDocId = rec.docId,
                studentDocId = stuDocId,
                termNo = termNo,
                areaCode = areaCode,
                grade = grade,
                exists = docId in existingCoDocIds
            ).onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Unable to save co-scholastic grade."))
            }
        }
    }

    fun saveTermInfo(info: TermInfoEntity, onSaved: () -> Unit = {}) {
        val (stuDocId, student) = studentDocIdByLongId[info.studentId] ?: return
        if (!uiState.value.canEditCoScholasticFor(student)) {
            emitMessage("Only the Class Teacher of ${student.className}-${student.section} (or Admin) can edit attendance, remarks, and promotion.")
            return
        }
        val err = ReportCalculations.validateAttendance(info.daysPresent, info.workingDays)
        if (err != null) {
            emitMessage(err)
            return
        }
        val rec = activeSchoolRecord(student.schoolId) ?: return
        val docId = OrgAcademicRepository.makeTermInfoDocId(stuDocId, info.termNo)
        // Phase 8: Protect manually typed attendance values when auto-fill switch is ON
        val existingEffective = uiState.value.effectiveTermAttendanceForStudent(info.studentId, info.termNo)
        val attendanceChangedOrManual = existingEffective.isManual ||
            info.daysPresent != existingEffective.daysPresent ||
            info.workingDays != existingEffective.workingDays
        val manualFlagToWrite: Boolean? = if (rec.autoFillReportAttendance) {
            attendanceChangedOrManual
        } else {
            null
        }
        viewModelScope.launch {
            academicRepo.saveTermInfo(
                orgId = orgId,
                schoolDocId = rec.docId,
                studentDocId = stuDocId,
                info = info,
                exists = docId in existingTermInfoDocIds,
                attendanceManualOverride = manualFlagToWrite
            ).onSuccess {
                emitMessage("Saved Term-${info.termNo} attendance & remarks")
                onSaved()
            }.onFailure { ex ->
                emitMessage(friendlyFirestoreErrorMessage(ex, "Unable to save attendance & remarks."))
            }
        }
    }

    // -------------------------------------------------------------------------
    // GLOBAL ORGANISATION SETTINGS & SAMPLE DATA
    // -------------------------------------------------------------------------
    fun updateSettings(updated: SchoolSettingsEntity, message: String = "Settings saved") {
        if (!uiState.value.canEdit(AppModule.SETTINGS)) {
            emitMessage("You do not have permission to edit settings.")
            return
        }
        viewModelScope.launch {
            academicRepo.updateOrgSettings(orgId, updated)
                .onSuccess { emitMessage(message) }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Unable to save settings.")) }
        }
    }

    fun addSession(sessionName: String) {
        val trimmed = sessionName.trim()
        if (trimmed.isEmpty()) return
        val cur = uiState.value.settings
        val list = (cur.sessionsList() + trimmed).distinct()
        val updated = cur.copy(currentSession = trimmed, sessionsCsv = list.joinToString(","))
        _filter.value = _filter.value.copy(session = trimmed, selectedStudentId = null)
        updateSettings(updated, "Switched to session $trimmed")
    }

    fun clearSampleData() {
        val rec = activeSchoolRecord() ?: return
        val sampleStudents = uiState.value.allStudents.filter { it.schoolId == rec.stableId && it.isSample }
        if (sampleStudents.isEmpty()) {
            emitMessage("No sample data remaining")
            return
        }
        viewModelScope.launch {
            var removed = 0
            sampleStudents.forEach { st ->
                val docId = studentDocIdByLongId[st.id]?.first ?: return@forEach
                if (academicRepo.deleteStudent(orgId, rec.docId, docId, st.name).isSuccess) removed++
            }
            emitMessage("Cleared $removed sample student(s)")
        }
    }

    fun restoreSampleData() {
        val state = uiState.value
        val rec = activeSchoolRecord(state.activeSchool.id) ?: return
        if (!state.canEdit(AppModule.STUDENTS)) {
            emitMessage("You do not have permission to add students.")
            return
        }
        viewModelScope.launch {
            val sampleList = listOf(
                Triple("Nursery", "SAMP-01", "Aarav Sharma"),
                Triple("2nd", "SAMP-02", "Ananya Verma"),
                Triple("6th", "SAMP-03", "Rohan Gupta")
            )
            var added = 0
            sampleList.forEachIndexed { index, (cls, adm, name) ->
                val matchedCls = rec.classes.firstOrNull { it.name.equals(cls, ignoreCase = true) }
                    ?: rec.classes.firstOrNull()
                    ?: return@forEachIndexed
                val student = StudentEntity(
                    id = 0L,
                    schoolId = rec.stableId,
                    classId = StableDocId.toLong("${rec.docId}_cls_${matchedCls.id}"),
                    session = state.filter.session,
                    admNo = adm,
                    name = name,
                    fatherName = "Sample Father",
                    motherName = "Sample Mother",
                    dob = "10-05-2017",
                    mobile = "900000000${index + 1}",
                    address = rec.address.ifBlank { "Sample Address" },
                    className = matchedCls.name,
                    section = "A",
                    rollNo = index + 1,
                    isSample = true
                )
                if (academicRepo.saveStudent(orgId, rec.docId, null, student, matchedCls.id).isSuccess) added++
            }
            emitMessage("Loaded $added sample student(s) into ${rec.name}")
        }
    }

    // -------------------------------------------------------------------------
    // PHASE 2: MEMBERS, JOIN REQUESTS, INVITE CODES, TEACHER PROFILE & ORG LIFECYCLE
    // -------------------------------------------------------------------------
    fun approveJoinRequest(
        request: CloudJoinRequest,
        assignedRole: OrgRole,
        allSchools: Boolean,
        allowedSchoolIds: List<String>,
        permissions: MemberPermissions
    ) {
        viewModelScope.launch {
            orgRepo.approveJoinRequest(
                orgId = orgId,
                request = request,
                assignedRole = assignedRole,
                allSchools = allSchools,
                allowedSchoolIds = allowedSchoolIds,
                permissions = permissions
            ).onSuccess {
                emitMessage("Approved ${request.name} as ${assignedRole.label}")
            }.onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Could not approve join request."))
            }
        }
    }

    fun rejectJoinRequest(request: CloudJoinRequest) {
        viewModelScope.launch {
            orgRepo.rejectJoinRequest(orgId, request)
                .onSuccess { emitMessage("Rejected join request from ${request.name}") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Could not reject request.")) }
        }
    }

    fun updateMemberAccess(
        targetMember: CloudMember,
        newRole: OrgRole,
        allSchools: Boolean,
        allowedSchoolIds: List<String>,
        permissions: MemberPermissions,
        assignedClassSubjects: List<String> = targetMember.assignedClassSubjects,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val state = uiState.value
        val guardError = OrgAccessEvaluator.canDemoteRole(
            actor = state.accessProfile,
            target = targetMember.toAccessProfile(),
            newRole = newRole,
            allActiveMembers = state.orgMembers.map { it.toAccessProfile() }
        )
        if (guardError != null) {
            onResult(false, guardError)
            emitMessage(guardError)
            return
        }
        viewModelScope.launch {
            orgRepo.updateMemberAccess(
                orgId = orgId,
                targetMember = targetMember,
                newRole = newRole,
                allSchools = allSchools,
                allowedSchoolIds = allowedSchoolIds,
                permissions = permissions,
                assignedClassSubjects = assignedClassSubjects
            ).onSuccess {
                emitMessage("Updated access for ${targetMember.name}")
                onResult(true, null)
            }.onFailure { err ->
                val msg = friendlyFirestoreErrorMessage(err, "Could not update member access.")
                emitMessage(msg)
                onResult(false, msg)
            }
        }
    }

    fun removeMember(targetMember: CloudMember) {
        val state = uiState.value
        val guardError = OrgAccessEvaluator.canRemoveMember(
            actor = state.accessProfile,
            target = targetMember.toAccessProfile(),
            allActiveMembers = state.orgMembers.map { it.toAccessProfile() }
        )
        if (guardError != null) {
            emitMessage(guardError)
            return
        }
        viewModelScope.launch {
            orgRepo.removeMember(orgId, targetMember)
                .onSuccess { emitMessage("Removed ${targetMember.name} from organisation") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Could not remove member.")) }
        }
    }

    fun createInviteCode(
        presetRole: OrgRole,
        allSchools: Boolean,
        schoolIds: List<String>,
        permissions: MemberPermissions,
        expiresInHours: Int,
        onCreated: (CloudInviteCode) -> Unit = {}
    ) {
        val orgName = uiState.value.activeOrg?.name ?: return
        viewModelScope.launch {
            orgRepo.createInviteCode(
                orgId = orgId,
                orgName = orgName,
                presetRole = presetRole,
                allSchools = allSchools,
                schoolIds = schoolIds,
                permissions = permissions,
                expiresInHours = expiresInHours
            ).onSuccess { created ->
                emitMessage("Created one-time invite code: ${created.code}")
                onCreated(created)
            }.onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Could not create invite code."))
            }
        }
    }

    fun deleteInviteCode(inviteCode: String) {
        viewModelScope.launch {
            orgRepo.deleteInviteCode(orgId, inviteCode)
                .onSuccess { emitMessage("Revoked invite code $inviteCode") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Could not revoke invite code.")) }
        }
    }

    fun completeTeacherProfile(
        name: String,
        mobile: String,
        photoBase64: String,
        requestedSubjects: List<String>,
        requestedClasses: List<String>,
        onCompleted: () -> Unit = {}
    ) {
        val currentMember = uiState.value.currentMember
        val assignedSchoolIds = when {
            currentMember == null -> emptyList()
            currentMember.allSchools -> uiState.value.schoolDocIdByLongId.values.toList()
            else -> currentMember.schools
        }
        val wasAlreadyCompleted = currentMember?.teacherProfileCompleted == true
        viewModelScope.launch {
            orgRepo.completeTeacherProfile(
                orgId = orgId,
                name = name,
                mobile = mobile,
                photoBase64 = photoBase64,
                requestedSubjects = requestedSubjects,
                requestedClasses = requestedClasses,
                assignedSchoolIds = assignedSchoolIds,
                wasAlreadyCompleted = wasAlreadyCompleted
            ).onSuccess {
                emitMessage("Teacher profile saved")
                onCompleted()
            }.onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Could not save teacher profile."))
            }
        }
    }

    // -------------------------------------------------------------------------
    // PHASE 4: TEACHERS, PERIOD STRUCTURE, ASSIGNMENTS & SUBJECT RULES
    // -------------------------------------------------------------------------
    fun saveTeacher(
        teacher: TeacherProfile,
        isUpdate: Boolean,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        if (!uiState.value.canEdit(AppModule.TEACHERS)) {
            onResult(false, "You do not have edit access to Teachers and assignments.")
            return
        }
        val res = phase4Repo.saveTeacher(orgId, teacher, isUpdate)
        res.onSuccess { savedId ->
            val updatedTeachers = uiState.value.teachers.filterNot { it.id == savedId } + teacher.copy(id = savedId)
            syncTeacherScopesAndClassTeachers(updatedTeachers, uiState.value.classAssignments)
            emitMessage(if (isUpdate) "Updated teacher '${teacher.name.trim()}'" else "Added teacher '${teacher.name.trim()}'")
            onResult(true, null)
        }.onFailure { err ->
            val msg = friendlyFirestoreErrorMessage(err, "Could not save teacher.")
            emitMessage(msg)
            onResult(false, msg)
        }
    }

    fun syncTeacherMemberToDirectory(member: CloudMember) {
        if (!uiState.value.canEdit(AppModule.TEACHERS)) {
            emitMessage("You do not have edit access to Teachers and assignments.")
            return
        }
        val state = uiState.value
        val docId = Phase4Repository.makeMemberTeacherDocId(member.uid)
        val exists = state.teachers.any { it.id == docId }
        val schoolIds = if (member.allSchools) {
            state.schoolDocIdByLongId.values.toList()
        } else {
            member.schools
        }
        val profile = TeacherProfile(
            id = docId,
            name = member.name.ifBlank { member.email.substringBefore("@") },
            mobile = member.mobile,
            email = member.email,
            photoBase64 = member.teacherPhotoBase64,
            subjects = member.requestedSubjects,
            schoolIds = schoolIds,
            maxPeriodsPerDay = Phase4Validator.DEFAULT_MAX_PERIODS_PER_DAY,
            maxConsecutivePeriods = Phase4Validator.DEFAULT_MAX_CONSECUTIVE_PERIODS,
            weeklyOffDays = emptyList(),
            active = true,
            linkedMemberUid = member.uid
        )
        saveTeacher(profile, isUpdate = exists)
    }

    fun setTeacherActive(teacher: TeacherProfile, active: Boolean) {
        if (!uiState.value.canEdit(AppModule.TEACHERS)) {
            emitMessage("You do not have edit access to Teachers and assignments.")
            return
        }
        phase4Repo.setTeacherActive(orgId, teacher.id, teacher.name, active)
            .onSuccess {
                emitMessage(if (active) "Activated ${teacher.name}" else "Deactivated ${teacher.name}")
            }
            .onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Could not update teacher status."))
            }
    }

    fun deleteTeacher(
        teacher: TeacherProfile,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TEACHERS)) {
            onResult(false, "You do not have edit access to Teachers and assignments.")
            return
        }
        phase4Repo.deleteTeacher(orgId, teacher, state.classAssignments)
            .onSuccess {
                emitMessage("Deleted teacher '${teacher.name}'")
                onResult(true, null)
            }
            .onFailure { err ->
                val msg = err.message ?: friendlyFirestoreErrorMessage(err, "Could not delete teacher.")
                emitMessage(msg)
                onResult(false, msg)
            }
    }

    fun savePeriodStructure(
        structure: SchoolPeriodStructure,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TEACHERS)) {
            onResult(false, "You do not have edit access to Teachers and assignments.")
            return
        }
        val existsInCloud = structure.schoolId in state.periodStructuresBySchool
        phase4Repo.savePeriodStructure(orgId, structure, existsInCloud)
            .onSuccess {
                val schoolName = state.schoolNameByDocId(structure.schoolId)
                emitMessage("Saved period structure for $schoolName (${structure.totalTeachingPeriodsPerWeek()} periods/week)")
                onResult(true, null)
            }
            .onFailure { err ->
                val msg = err.message ?: friendlyFirestoreErrorMessage(err, "Could not save period structure.")
                emitMessage(msg)
                onResult(false, msg)
            }
    }

    fun saveClassAssignment(
        assignment: ClassSectionAssignment,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TEACHERS)) {
            onResult(false, "You do not have edit access to Teachers and assignments.")
            return
        }
        val docId = assignment.id.ifBlank {
            Phase4Repository.makeAssignmentDocId(assignment.schoolId, assignment.className, assignment.section)
        }
        val existsInCloud = state.classAssignments.any { it.id == docId }
        val savedAssignment = assignment.copy(id = docId)
        phase4Repo.saveClassAssignment(orgId, savedAssignment, existsInCloud)
            .onSuccess {
                val updatedAssignments = state.classAssignments.filterNot { it.id == docId } + savedAssignment
                syncTeacherScopesAndClassTeachers(state.teachers, updatedAssignments, onlySchoolId = assignment.schoolId)
                emitMessage("Saved assignments for ${assignment.classSectionLabel}")
                onResult(true, null)
            }
            .onFailure { err ->
                val msg = friendlyFirestoreErrorMessage(err, "Could not save class assignments.")
                emitMessage(msg)
                onResult(false, msg)
            }
    }

    fun saveSubjectRule(
        rule: SubjectRule,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TEACHERS)) {
            onResult(false, "You do not have edit access to Teachers and assignments.")
            return
        }
        val docId = rule.id.ifBlank {
            Phase4Repository.makeSubjectRuleDocId(rule.schoolId, rule.subjectName)
        }
        val existsInCloud = state.subjectRules.any { it.id == docId }
        phase4Repo.saveSubjectRule(orgId, rule.copy(id = docId), existsInCloud)
            .onSuccess {
                emitMessage("Saved rule for ${rule.subjectName}")
                onResult(true, null)
            }
            .onFailure { err ->
                val msg = friendlyFirestoreErrorMessage(err, "Could not save subject rule.")
                emitMessage(msg)
                onResult(false, msg)
            }
    }

    fun leaveOrganisation(onLeft: () -> Unit) {
        val state = uiState.value
        val guardError = OrgAccessEvaluator.canLeaveOrganisation(
            member = state.accessProfile,
            allActiveMembers = state.orgMembers.map { it.toAccessProfile() }
        )
        if (guardError != null) {
            emitMessage(guardError)
            return
        }
        viewModelScope.launch {
            orgRepo.leaveOrganisation(orgId)
                .onSuccess {
                    emitMessage("Left ${state.activeOrg?.name ?: "organisation"}")
                    onLeft()
                }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Could not leave organisation.")) }
        }
    }

    fun transferOwnership(targetMember: CloudMember, onTransferred: () -> Unit = {}) {
        val state = uiState.value
        val org = state.activeOrg ?: return
        val guardError = OrgAccessEvaluator.canTransferOwnership(state.accessProfile, targetMember.toAccessProfile())
        if (guardError != null) {
            emitMessage(guardError)
            return
        }
        viewModelScope.launch {
            orgRepo.transferOwnership(org, targetMember)
                .onSuccess {
                    emitMessage("Transferred ownership to ${targetMember.name}. You are now an Admin.")
                    onTransferred()
                }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Could not transfer ownership.")) }
        }
    }

    fun resetDeleteOrgBackupFlag() {
        _deleteOrgBackupExported.value = false
    }

    fun deleteOrganisation(typedCode: String, onDeleted: () -> Unit) {
        val state = uiState.value
        val org = state.activeOrg ?: return
        val guardError = OrgAccessEvaluator.canDeleteOrganisation(
            actor = state.accessProfile,
            typedCode = typedCode,
            expectedOrgCode = org.code,
            backupExported = _deleteOrgBackupExported.value
        )
        if (guardError != null) {
            emitMessage(guardError)
            return
        }
        viewModelScope.launch {
            orgRepo.deleteOrganisation(org)
                .onSuccess {
                    emitMessage("Deleted organisation '${org.name}'")
                    onDeleted()
                }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Could not delete organisation.")) }
        }
    }

    // -------------------------------------------------------------------------
    // PHASE 2: IDEMPOTENT LOCAL ROOM -> CLOUD MIGRATION
    // -------------------------------------------------------------------------
    fun uploadLocalRoomDataToCloud() {
        if (_migrationProgress.value.isRunning) return
        viewModelScope.launch {
            _migrationProgress.value = MigrationProgress(
                isRunning = true,
                completedSteps = 0,
                totalSteps = 1,
                statusText = "Reading this phone's local database…"
            )
            val snapshot = withContext(Dispatchers.IO) { localRepo.getFullBackupSnapshot() }
            if (snapshot.schools.isEmpty() && snapshot.students.isEmpty()) {
                _migrationProgress.value = MigrationProgress(
                    isRunning = false,
                    finished = true,
                    statusText = "No local data found on this phone to upload."
                )
                return@launch
            }
            val result = academicRepo.uploadLocalBackupToOrganisation(
                orgId = orgId,
                snapshot = snapshot,
                existingSchoolDocIds = schoolRecordsByLongId.values.map { it.docId }.toSet(),
                existingStudentDocIds = studentDocIdByLongId.values.map { it.first }.toSet(),
                existingMarkDocIds = existingMarkDocIds,
                existingCoDocIds = existingCoDocIds,
                existingTermInfoDocIds = existingTermInfoDocIds,
                onProgress = { done, total, status ->
                    _migrationProgress.value = MigrationProgress(
                        isRunning = done < total,
                        completedSteps = done,
                        totalSteps = total.coerceAtLeast(1),
                        statusText = status
                    )
                }
            )
            result.onSuccess { summary ->
                _migrationProgress.value = MigrationProgress(
                    isRunning = false,
                    completedSteps = _migrationProgress.value.totalSteps.coerceAtLeast(1),
                    totalSteps = _migrationProgress.value.totalSteps.coerceAtLeast(1),
                    statusText = summary,
                    finished = true
                )
                emitMessage(summary)
            }.onFailure { err ->
                val msg = friendlyFirestoreErrorMessage(err, "Upload paused or failed. You can safely run it again.")
                _migrationProgress.value = _migrationProgress.value.copy(
                    isRunning = false,
                    error = msg
                )
                emitMessage(msg)
            }
        }
    }

    fun resetMigrationProgress() {
        _migrationProgress.value = MigrationProgress()
    }

    // -------------------------------------------------------------------------
    // PDF & BATCH ZIP GENERATION (100% UNCHANGED PHASE 1 REPORT LAYOUTS!)
    // -------------------------------------------------------------------------
    private fun buildBundles(state: SchoolUiState, targets: List<StudentEntity>): List<StudentReportBundle> {
        val school = state.activeSchool
        val activeClassName = state.effectiveClassName
        val subjects = state.subjectsForClass(activeClassName, school.id)
        val perfMap = state.performanceWithRanksForClass(
            schoolId = school.id,
            session = state.filter.session,
            className = activeClassName,
            section = state.filter.section
        )
        return targets.map { st ->
            val stSchool = state.schoolById(st.schoolId) ?: school
            val stSubjects = state.subjectsForClass(st.className, stSchool.id).ifEmpty { subjects }
            val marks = state.marksMapForStudent(st.id)
            val perf = perfMap[st.id] ?: ReportCalculations.calculateStudentPerformance(
                studentId = st.id,
                subjects = stSubjects,
                marksBySubjectAndKey = marks,
                passPercent = state.settings.passPercent
            )
            val schDocId = state.schoolDocIdByLongId[stSchool.id].orEmpty()
            val schCloudRec = state.schoolRecordsByDocId[schDocId]
            val ctNameForPrint = if (schCloudRec?.printClassTeacherNameOnReports == true) {
                state.classTeacherNameForSection(schDocId, st.className, st.section)
            } else {
                ""
            }
            StudentReportBundle(
                student = st,
                school = stSchool,
                subjects = stSubjects,
                marksBySubjectAndKey = marks,
                performance = perf,
                attendance = state.attendanceForStudent(st.id),
                coScholasticT1 = state.coScholasticMapForStudent(st.id, 1),
                coScholasticT2 = state.coScholasticMapForStudent(st.id, 2),
                term1Info = state.termInfoForStudent(st.id, 1),
                term2Info = state.termInfoForStudent(st.id, 2),
                classTeacherName = ctNameForPrint
            )
        }
    }

    fun generatePdfReport(
        reportType: ReportType,
        batchWholeClass: Boolean,
        slipLayoutMode: SlipLayoutMode
    ) {
        val state = uiState.value
        val targets = if (batchWholeClass) state.filteredStudents else listOfNotNull(state.selectedStudent)
        if (targets.isEmpty()) {
            emitMessage("No students in Class ${state.effectiveClassName}-${state.filter.section}")
            return
        }

        viewModelScope.launch {
            _pdfState.value = PdfPreviewState(
                isGenerating = true,
                statusLabel = "Generating ${reportType.label} PDF…",
                progressCurrent = 0,
                progressTotal = targets.size,
                reportType = reportType,
                batchScope = batchWholeClass
            )
            try {
                val result = withContext(Dispatchers.IO) {
                    val bundles = buildBundles(state, targets)
                    val pdfFile = PdfReportGenerator.generateReportPdf(
                        context = appContext,
                        settings = state.settings,
                        reportType = reportType,
                        bundles = bundles,
                        slipLayoutMode = slipLayoutMode,
                        onProgress = { cur, tot ->
                            _pdfState.value = _pdfState.value.copy(
                                progressCurrent = cur,
                                progressTotal = tot
                            )
                        }
                    )
                    val totalPages = PdfExportHelper.countPdfPages(pdfFile)
                    val bitmaps = PdfExportHelper.renderPdfPages(pdfFile, maxPagesToPreview = 15)
                    Triple(pdfFile, totalPages, bitmaps)
                }

                val oldPages = _pdfState.value.previewPages
                _pdfState.value = PdfPreviewState(
                    isGenerating = false,
                    progressCurrent = targets.size,
                    progressTotal = targets.size,
                    generatedFile = result.first,
                    generatedZipFile = null,
                    totalPages = result.second,
                    previewPages = result.third,
                    reportType = reportType,
                    batchScope = batchWholeClass
                )
                oldPages.forEach { if (!it.isRecycled) it.recycle() }
                emitMessage("Generated ${result.second} A4 page(s) for ${state.activeSchool.name}")
            } catch (e: Exception) {
                _pdfState.value = _pdfState.value.copy(
                    isGenerating = false,
                    error = e.message ?: "Failed to generate PDF"
                )
                emitMessage("PDF error: ${e.message}")
            }
        }
    }

    fun generateBatchClassZip(
        reportType: ReportType,
        slipLayoutMode: SlipLayoutMode,
        onReady: (File) -> Unit
    ) {
        val state = uiState.value
        val targets = state.filteredStudents
        if (targets.isEmpty()) {
            emitMessage("No students in Class ${state.effectiveClassName}-${state.filter.section}")
            return
        }

        viewModelScope.launch {
            _pdfState.value = _pdfState.value.copy(
                isGenerating = true,
                statusLabel = "Packing Class ZIP (1 PDF per student)…",
                progressCurrent = 0,
                progressTotal = targets.size
            )
            try {
                val zipFile = withContext(Dispatchers.IO) {
                    val bundles = buildBundles(state, targets)
                    PdfReportGenerator.generateBatchClassZip(
                        context = appContext,
                        settings = state.settings,
                        reportType = reportType,
                        bundles = bundles,
                        slipLayoutMode = slipLayoutMode,
                        onProgress = { cur, tot ->
                            _pdfState.value = _pdfState.value.copy(
                                progressCurrent = cur,
                                progressTotal = tot
                            )
                        }
                    )
                }
                _pdfState.value = _pdfState.value.copy(
                    isGenerating = false,
                    generatedZipFile = zipFile
                )
                emitMessage("Packed ${targets.size} individual PDF(s) into ZIP")
                onReady(zipFile)
            } catch (e: Exception) {
                _pdfState.value = _pdfState.value.copy(isGenerating = false)
                emitMessage("ZIP error: ${e.message}")
            }
        }
    }

    fun saveGeneratedPdfToUri(destinationUri: Uri) {
        val file = _pdfState.value.generatedFile ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                PdfExportHelper.copyFileToUri(appContext, file, destinationUri)
            }
            emitMessage(if (ok) "PDF saved successfully" else "Could not save PDF")
        }
    }

    fun saveGeneratedZipToUri(destinationUri: Uri) {
        val file = _pdfState.value.generatedZipFile ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                PdfExportHelper.copyFileToUri(appContext, file, destinationUri)
            }
            emitMessage(if (ok) "Class ZIP saved successfully" else "Could not save ZIP")
        }
    }

    // -------------------------------------------------------------------------
    // JSON BACKUP & RESTORE (Exports cloud data + imports into current cloud org)
    // -------------------------------------------------------------------------
    fun suggestedBackupFileName(allSchools: Boolean): String {
        val state = uiState.value
        val orgSlug = state.activeOrg?.code?.ifBlank { "Org" } ?: "Org"
        val scopeSlug = if (allSchools) {
            "${orgSlug}_All_Schools"
        } else {
            state.activeSchool.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "School" }
        }
        return "SchoolReportCard_${scopeSlug}_${state.filter.session}.json"
    }

    private fun buildCurrentCloudBackupSnapshot(allSchools: Boolean): FullBackupData {
        val state = uiState.value
        val schools = if (allSchools) state.schools else listOf(state.activeSchool)
        val schoolIds = schools.map { it.id }.toSet()
        val students = state.allStudents.filter { it.schoolId in schoolIds }
        val studentIds = students.map { it.id }.toSet()
        return FullBackupData(
            settings = state.settings,
            schools = schools,
            classGroups = state.classGroups.filter { it.schoolId in schoolIds },
            schoolClasses = state.schoolClasses.filter { it.schoolId in schoolIds },
            subjects = state.subjects.filter { it.schoolId in schoolIds },
            students = students,
            marks = state.allMarks.filter { it.studentId in studentIds },
            coScholastic = state.allCoScholastic.filter { it.studentId in studentIds },
            termInfos = state.allTermInfos.filter { it.studentId in studentIds }
        )
    }

    fun exportBackupToUri(destinationUri: Uri, allSchools: Boolean = true) {
        val state = uiState.value
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val snapshot = buildCurrentCloudBackupSnapshot(allSchools)
                val json = BackupAndCsvManager.serializeBackupToJson(snapshot)
                PdfExportHelper.writeTextToUri(appContext, json, destinationUri)
            }
            if (ok) {
                _deleteOrgBackupExported.value = true
                if (state.canEdit(AppModule.SETTINGS)) {
                    academicRepo.updateOrgSettings(
                        orgId,
                        state.settings.copy(lastBackupEpochMs = System.currentTimeMillis())
                    )
                }
            }
            val scopeText = if (allSchools) "All schools" else state.activeSchool.name
            emitMessage(if (ok) "JSON backup saved ($scopeText)" else "Failed to export backup")
        }
    }

    fun createShareableBackupFile(allSchools: Boolean = true, onReady: (File) -> Unit) {
        val state = uiState.value
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val snapshot = buildCurrentCloudBackupSnapshot(allSchools)
                val json = BackupAndCsvManager.serializeBackupToJson(snapshot)
                val dir = File(appContext.cacheDir, "backups").apply { mkdirs() }
                val out = File(dir, suggestedBackupFileName(allSchools))
                out.writeText(json, Charsets.UTF_8)
                out
            }
            _deleteOrgBackupExported.value = true
            if (state.canEdit(AppModule.SETTINGS)) {
                academicRepo.updateOrgSettings(
                    orgId,
                    state.settings.copy(lastBackupEpochMs = System.currentTimeMillis())
                )
            }
            onReady(file)
        }
    }

    fun prepareBackupImportFromUri(uri: Uri) {
        if (!uiState.value.canEdit(AppModule.SETTINGS)) {
            emitMessage("You do not have permission to restore backups.")
            return
        }
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val text = PdfExportHelper.readTextFromUri(appContext, uri)
                    ?: return@withContext Result.failure<ParsedBackupResult>(IllegalArgumentException("Could not read file"))
                BackupAndCsvManager.deserializeBackupJson(text)
            }
            parsed.onSuccess { result ->
                when (result) {
                    is ParsedBackupResult.ModernBackup -> _pendingImportBackup.value = result.data
                    is ParsedBackupResult.LegacyBackup -> _pendingLegacyBackup.value = result.payload
                }
            }.onFailure { err ->
                emitMessage(err.message ?: "Invalid backup JSON file")
            }
        }
    }

    fun cancelPendingBackupImport() {
        _pendingImportBackup.value = null
    }

    fun confirmBackupImport(@Suppress("UNUSED_PARAMETER") replaceAll: Boolean) {
        val data = _pendingImportBackup.value ?: return
        _pendingImportBackup.value = null
        viewModelScope.launch {
            val res = academicRepo.uploadLocalBackupToOrganisation(
                orgId = orgId,
                snapshot = data,
                existingSchoolDocIds = schoolRecordsByLongId.values.map { it.docId }.toSet(),
                existingStudentDocIds = studentDocIdByLongId.values.map { it.first }.toSet(),
                existingMarkDocIds = existingMarkDocIds,
                existingCoDocIds = existingCoDocIds,
                existingTermInfoDocIds = existingTermInfoDocIds,
                onProgress = { _, _, _ -> }
            )
            res.onSuccess { summary ->
                emitMessage(summary)
            }.onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Backup restore failed."))
            }
        }
    }

    fun cancelPendingLegacyBackupImport() {
        _pendingLegacyBackup.value = null
    }

    fun confirmLegacyBackupImport(target: LegacyImportTarget) {
        val payload = _pendingLegacyBackup.value ?: return
        _pendingLegacyBackup.value = null
        viewModelScope.launch {
            // Stage legacy backup through local Room converter and enqueue cleanly to Firestore cache
            val converted = withContext(Dispatchers.IO) {
                val targetSchoolId = localRepo.importLegacyBackup(payload, target).getOrNull() ?: return@withContext null
                localRepo.getFullBackupSnapshot(schoolIdFilter = targetSchoolId)
            }
            if (converted == null) {
                emitMessage("Could not parse legacy backup.")
                return@launch
            }
            academicRepo.uploadLocalBackupToOrganisation(
                orgId = orgId,
                snapshot = converted,
                existingSchoolDocIds = schoolRecordsByLongId.values.map { it.docId }.toSet(),
                existingStudentDocIds = studentDocIdByLongId.values.map { it.first }.toSet(),
                existingMarkDocIds = existingMarkDocIds,
                existingCoDocIds = existingCoDocIds,
                existingTermInfoDocIds = existingTermInfoDocIds
            ) { _, _, _ -> }
                .onSuccess { emitMessage("Imported legacy backup (${payload.students.size} student(s))") }
                .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Legacy backup import failed.")) }
        }
    }

    // -------------------------------------------------------------------------
    // PHASE 5: TIMETABLE MANUAL EDITING, COPY, LOCK/CLEAR & OFFLINE PDF EXPORT
    // -------------------------------------------------------------------------
    fun saveTimetableCell(
        schoolDocId: String,
        className: String,
        section: String,
        day: DayOfWeekCode,
        slotId: String,
        subjectName: String,
        teacherId: String,
        locked: Boolean,
        onSaved: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("You have view-only access to Timetable.")
            onSaved(false, "View-only access.")
            return
        }
        val session = state.filter.session
        val cleanSection = section.trim().uppercase().ifBlank { "A" }
        val cellId = TimetableEngine.makeCellDocId(schoolDocId, session, className, cleanSection, day, slotId)
        val existingCell = state.timetableCells.firstOrNull { it.id == cellId }
        if (existingCell?.locked == true && locked) {
            // Allow saving if only content changed AFTER unlocking, block accidental overwrite if still locked & content differs
            val contentChanged = !existingCell.subjectName.equals(subjectName.trim(), ignoreCase = true) ||
                existingCell.teacherId != teacherId
            if (contentChanged) {
                val msg = "This cell is locked. Unlock it first to change the subject or teacher."
                emitMessage(msg)
                onSaved(false, msg)
                return
            }
        }
        val teacher = state.availableTimetableTeachers().firstOrNull { it.id == teacherId }
        val teacherName = teacher?.name.orEmpty()
        val editorName = state.currentMember?.name?.ifBlank { orgRepo.currentUserName() } ?: orgRepo.currentUserName()
        val nowMs = System.currentTimeMillis()

        val candidate = TimetableCell(
            id = cellId,
            schoolId = schoolDocId,
            session = session,
            className = className.trim(),
            section = cleanSection,
            day = day,
            slotId = slotId,
            subjectName = subjectName.trim(),
            teacherId = teacherId.trim(),
            teacherName = teacherName,
            locked = locked,
            updatedByUid = currentUserId,
            updatedByName = editorName,
            clientEditedAtMs = nowMs
        )

        val validation = TimetableEngine.validateCellPlacement(
            candidate = candidate,
            existingCellsInSession = state.activeSessionTimetableCells,
            teachers = state.teachers,
            periodStructuresBySchool = state.effectivePeriodStructuresBySchool,
            classAssignments = state.classAssignments,
            subjectRules = state.subjectRules,
            schoolNamesById = state.schoolNamesByDocId,
            allowReplaceSameCellId = true
        )
        if (!validation.canSave) {
            val errMsg = validation.hardConflicts.first().message
            emitMessage(errMsg)
            onSaved(false, errMsg)
            return
        }

        offlinePrefs.upsertPendingTimetableEdit(
            uid = currentUserId,
            edit = LocalPendingTimetableEdit(
                orgId = orgId,
                cellId = cellId,
                schoolId = schoolDocId,
                session = session,
                className = candidate.className,
                section = cleanSection,
                dayWire = day.wireValue,
                slotId = slotId,
                localSubjectName = candidate.subjectName,
                localTeacherId = candidate.teacherId,
                localTeacherName = candidate.teacherName,
                localLocked = locked,
                wasCleared = false,
                clientEditedAtMs = nowMs
            )
        )

        timetableRepo.saveCell(
            orgId = orgId,
            cell = candidate,
            existsInCloud = existingCell != null,
            editorName = editorName,
            clientEditedAtMs = nowMs
        ).onSuccess {
            // Ensure school period layout is mirrored for view-only Timetable users
            val struct = state.periodStructuresBySchool[schoolDocId]
            if (struct != null && schoolDocId !in state.timetableLayoutsBySchool) {
                timetableRepo.mirrorSchoolLayoutNonBlocking(orgId, struct, existsInLayoutCollection = false)
            }
            val warnSuffix = if (validation.softWarnings.isNotEmpty()) {
                " (${validation.softWarnings.size} soft warning(s))"
            } else ""
            emitMessage("Saved ${candidate.subjectName} for ${candidate.classSectionLabel}$warnSuffix")
            onSaved(true, null)
        }.onFailure { err ->
            val msg = friendlyFirestoreErrorMessage(err, "Could not save timetable cell.")
            emitMessage(msg)
            onSaved(false, msg)
        }
    }

    fun toggleTimetableCellLock(cell: TimetableCell) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("You have view-only access to Timetable.")
            return
        }
        val newLocked = !cell.locked
        val editorName = state.currentMember?.name?.ifBlank { orgRepo.currentUserName() } ?: orgRepo.currentUserName()
        val nowMs = System.currentTimeMillis()
        offlinePrefs.upsertPendingTimetableEdit(
            uid = currentUserId,
            edit = LocalPendingTimetableEdit(
                orgId = orgId,
                cellId = cell.id,
                schoolId = cell.schoolId,
                session = cell.session,
                className = cell.className,
                section = cell.section,
                dayWire = cell.day.wireValue,
                slotId = cell.slotId,
                localSubjectName = cell.subjectName,
                localTeacherId = cell.teacherId,
                localTeacherName = cell.teacherName,
                localLocked = newLocked,
                wasCleared = false,
                clientEditedAtMs = nowMs
            )
        )
        timetableRepo.toggleCellLock(orgId, cell, newLocked, editorName, nowMs)
            .onSuccess { emitMessage(if (newLocked) "Cell locked" else "Cell unlocked") }
            .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Could not update lock.")) }
    }

    fun clearTimetableCell(cell: TimetableCell, onCleared: () -> Unit = {}) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("You have view-only access to Timetable.")
            return
        }
        if (cell.locked) {
            emitMessage("Unlock this cell first before clearing it.")
            return
        }
        offlinePrefs.upsertPendingTimetableEdit(
            uid = currentUserId,
            edit = LocalPendingTimetableEdit(
                orgId = orgId,
                cellId = cell.id,
                schoolId = cell.schoolId,
                session = cell.session,
                className = cell.className,
                section = cell.section,
                dayWire = cell.day.wireValue,
                slotId = cell.slotId,
                localSubjectName = "",
                localTeacherId = "",
                localTeacherName = "",
                localLocked = false,
                wasCleared = true,
                clientEditedAtMs = System.currentTimeMillis()
            )
        )
        timetableRepo.clearCell(orgId, cell)
            .onSuccess {
                emitMessage("Cleared ${cell.classSectionLabel} ${cell.day.shortLabel}")
                onCleared()
            }
            .onFailure { emitMessage(friendlyFirestoreErrorMessage(it, "Could not clear cell.")) }
    }

    fun copyTimetableCellToDays(
        sourceSchoolDocId: String,
        className: String,
        section: String,
        rowIndex: Int,
        subjectName: String,
        teacherId: String,
        targetDays: Collection<DayOfWeekCode>,
        onCompleted: (Int, List<String>) -> Unit = { _, _ -> }
    ) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("You have view-only access to Timetable.")
            return
        }
        val structure = state.periodStructureForSchool(sourceSchoolDocId)
        val gridRows = TimetableEngine.buildGridRows(structure)
        val targetRow = gridRows.getOrNull(rowIndex)
        if (targetRow == null || !targetRow.isEditablePeriodRow) {
            emitMessage("Invalid period row for copying.")
            return
        }
        val session = state.filter.session
        val cleanSection = section.trim().uppercase().ifBlank { "A" }
        val teacher = state.availableTimetableTeachers().firstOrNull { it.id == teacherId }
        val teacherName = teacher?.name.orEmpty()
        val editorName = state.currentMember?.name?.ifBlank { orgRepo.currentUserName() } ?: orgRepo.currentUserName()

        val workingCells = state.activeSessionTimetableCells.toMutableList()
        var copiedCount = 0
        val blockedReasons = mutableListOf<String>()

        for (day in targetDays.distinct().sortedBy { it.sortOrder }) {
            val targetSlot = targetRow.slotByDay[day]
            if (targetSlot == null || !targetSlot.type.countsAsTeachingPeriod) {
                blockedReasons.add("${day.shortLabel}: no matching teaching period slot.")
                continue
            }
            val cellId = TimetableEngine.makeCellDocId(sourceSchoolDocId, session, className, cleanSection, day, targetSlot.id)
            val existing = workingCells.firstOrNull { it.id == cellId }
            if (existing?.locked == true) {
                blockedReasons.add("${day.shortLabel}: target cell is locked.")
                continue
            }
            val nowMs = System.currentTimeMillis()
            val candidate = TimetableCell(
                id = cellId,
                schoolId = sourceSchoolDocId,
                session = session,
                className = className.trim(),
                section = cleanSection,
                day = day,
                slotId = targetSlot.id,
                subjectName = subjectName.trim(),
                teacherId = teacherId.trim(),
                teacherName = teacherName,
                locked = false,
                updatedByUid = currentUserId,
                updatedByName = editorName,
                clientEditedAtMs = nowMs
            )
            val check = TimetableEngine.validateCellPlacement(
                candidate = candidate,
                existingCellsInSession = workingCells,
                teachers = state.teachers,
                periodStructuresBySchool = state.effectivePeriodStructuresBySchool,
                classAssignments = state.classAssignments,
                subjectRules = state.subjectRules,
                schoolNamesById = state.schoolNamesByDocId,
                allowReplaceSameCellId = true
            )
            if (!check.canSave) {
                blockedReasons.add("${day.shortLabel}: ${check.hardConflicts.first().message}")
                continue
            }
            offlinePrefs.upsertPendingTimetableEdit(
                uid = currentUserId,
                edit = LocalPendingTimetableEdit(
                    orgId = orgId,
                    cellId = cellId,
                    schoolId = sourceSchoolDocId,
                    session = session,
                    className = candidate.className,
                    section = cleanSection,
                    dayWire = day.wireValue,
                    slotId = targetSlot.id,
                    localSubjectName = candidate.subjectName,
                    localTeacherId = candidate.teacherId,
                    localTeacherName = candidate.teacherName,
                    localLocked = false,
                    wasCleared = false,
                    clientEditedAtMs = nowMs
                )
            )
            val res = timetableRepo.saveCell(orgId, candidate, existsInCloud = existing != null, editorName = editorName, clientEditedAtMs = nowMs)
            if (res.isSuccess) {
                workingCells.removeAll { it.id == cellId }
                workingCells.add(candidate)
                copiedCount++
            } else {
                blockedReasons.add("${day.shortLabel}: could not save.")
            }
        }

        val summary = buildString {
            append("Copied to $copiedCount day(s)")
            if (blockedReasons.isNotEmpty()) append(" • ${blockedReasons.size} skipped")
        }
        emitMessage(summary)
        onCompleted(copiedCount, blockedReasons)
    }

    // --- Timetable Offline PDF & Batch ZIP Export ---
    fun clearTimetablePdfPreview() {
        _timetablePdfState.value.previewPages.forEach { if (!it.isRecycled) it.recycle() }
        _timetablePdfState.value = TimetablePdfPreviewState()
    }

    private fun buildClassPrintBundle(
        state: SchoolUiState,
        schoolDocId: String,
        className: String,
        section: String
    ): ClassTimetablePrintBundle? {
        val schoolLongId = state.schoolDocIdByLongId.entries.firstOrNull { it.value == schoolDocId }?.key ?: state.activeSchool.id
        val school = state.schools.firstOrNull { it.id == schoolLongId } ?: state.activeSchool
        val session = state.filter.session
        val cleanSec = section.trim().uppercase().ifBlank { "A" }
        val assignment = state.classAssignments.firstOrNull {
            it.schoolId == schoolDocId &&
                it.className.equals(className, ignoreCase = true) &&
                it.section.equals(cleanSec, ignoreCase = true)
        }
        val classTeacherName = assignment?.classTeacherId?.takeIf { it.isNotBlank() }?.let { ctId ->
            state.availableTimetableTeachers().firstOrNull { it.id == ctId }?.name.orEmpty()
        }.orEmpty()
        val cells = state.activeSessionTimetableCells.filter {
            it.schoolId == schoolDocId &&
                it.className.equals(className, ignoreCase = true) &&
                it.section.equals(cleanSec, ignoreCase = true)
        }
        val subjects = state.subjectsForClass(className, school.id)
        val progress = TimetableEngine.buildClassSubjectProgress(
            schoolId = schoolDocId,
            session = session,
            className = className,
            section = cleanSec,
            classSubjects = subjects,
            cellsInSession = state.activeSessionTimetableCells,
            classAssignments = state.classAssignments
        )
        return ClassTimetablePrintBundle(
            school = school,
            session = session,
            className = className,
            section = cleanSec,
            classTeacherName = classTeacherName,
            periodStructure = state.periodStructureForSchool(schoolDocId),
            cells = cells,
            subjectProgress = progress
        )
    }

    fun generateClassTimetablePdf(
        schoolDocId: String,
        className: String,
        section: String,
        orientation: TimetablePrintOrientation
    ) {
        val state = uiState.value
        val bundle = buildClassPrintBundle(state, schoolDocId, className, section) ?: return
        clearTimetablePdfPreview()
        _timetablePdfState.value = TimetablePdfPreviewState(
            isGenerating = true,
            title = "Class $className-$section Timetable",
            statusLabel = "Generating A4 ${orientation.label}…",
            isLandscape = orientation == TimetablePrintOrientation.LANDSCAPE
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = TimetablePdfGenerator.generateClassTimetablePdf(appContext, listOf(bundle), orientation)
                    val totalPages = PdfExportHelper.countPdfPages(file)
                    val preview = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 10)
                    Triple(file, totalPages, preview)
                }
            }.onSuccess { (file, totalPages, preview) ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    statusLabel = "Ready ($totalPages page)",
                    generatedFile = file,
                    totalPages = totalPages,
                    previewPages = preview
                )
            }.onFailure { err ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    error = err.message ?: "Failed to generate PDF"
                )
            }
        }
    }

    fun generateBatchAllClassesTimetable(
        schoolDocId: String,
        orientation: TimetablePrintOrientation,
        includeZip: Boolean
    ) {
        val state = uiState.value
        val schoolLongId = state.schoolDocIdByLongId.entries.firstOrNull { it.value == schoolDocId }?.key ?: state.activeSchool.id
        val classes = state.classesForSchool(schoolLongId)
        val bundles = classes.flatMap { cls ->
            state.sectionsForSchoolClass(schoolLongId, cls.name).mapNotNull { sec ->
                buildClassPrintBundle(state, schoolDocId, cls.name, sec)
            }
        }
        if (bundles.isEmpty()) {
            emitMessage("No classes found to export.")
            return
        }
        clearTimetablePdfPreview()
        _timetablePdfState.value = TimetablePdfPreviewState(
            isGenerating = true,
            title = "All Classes (${bundles.size}) — ${state.schoolNameByDocId(schoolDocId)}",
            statusLabel = "Generating ${bundles.size} class timetable(s)…",
            progressTotal = bundles.size,
            isLandscape = orientation == TimetablePrintOrientation.LANDSCAPE
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val pdfFile = TimetablePdfGenerator.generateClassTimetablePdf(
                        context = appContext,
                        bundles = bundles,
                        orientation = orientation
                    ) { cur, tot ->
                        _timetablePdfState.value = _timetablePdfState.value.copy(progressCurrent = cur, progressTotal = tot)
                    }
                    val zipFile = if (includeZip) {
                        TimetablePdfGenerator.generateBatchClassesZip(appContext, bundles, orientation)
                    } else null
                    val totalPages = PdfExportHelper.countPdfPages(pdfFile)
                    val preview = PdfExportHelper.renderPdfPages(pdfFile, maxPagesToPreview = 12)
                    listOf(pdfFile, zipFile, totalPages, preview)
                }
            }.onSuccess { res ->
                val pdfFile = res[0] as File
                val zipFile = res[1] as File?
                val totalPages = res[2] as Int
                @Suppress("UNCHECKED_CAST")
                val preview = res[3] as List<Bitmap>
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    statusLabel = "Ready ($totalPages page(s)${if (zipFile != null) " + ZIP" else ""})",
                    generatedFile = pdfFile,
                    generatedZipFile = zipFile,
                    totalPages = totalPages,
                    previewPages = preview
                )
            }.onFailure { err ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    error = err.message ?: "Batch export failed"
                )
            }
        }
    }

    private fun buildTeacherPrintBundle(state: SchoolUiState, teacherId: String): TeacherTimetablePrintBundle? {
        val teacher = state.availableTimetableTeachers().firstOrNull { it.id == teacherId } ?: return null
        val summary = TimetableEngine.buildTeacherTimetable(
            teacherId = teacher.id,
            fallbackTeacherName = teacher.name,
            teacherProfile = state.teachers.firstOrNull { it.id == teacher.id },
            session = state.filter.session,
            cellsInSession = state.activeSessionTimetableCells,
            periodStructuresBySchool = state.effectivePeriodStructuresBySchool,
            schoolNamesById = state.schoolNamesByDocId
        )
        val primarySchoolDocId = teacher.schoolIds.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: state.activeSchoolDocId
        val primarySchoolLongId = state.schoolDocIdByLongId.entries.firstOrNull { it.value == primarySchoolDocId }?.key
            ?: state.activeSchool.id
        val primarySchool = state.schools.firstOrNull { it.id == primarySchoolLongId } ?: state.activeSchool
        return TeacherTimetablePrintBundle(
            primarySchool = primarySchool,
            session = state.filter.session,
            summary = summary,
            periodStructureForRows = state.periodStructureForSchool(primarySchoolDocId)
        )
    }

    fun generateTeacherTimetablePdf(teacherId: String) {
        val state = uiState.value
        val bundle = buildTeacherPrintBundle(state, teacherId) ?: return
        clearTimetablePdfPreview()
        _timetablePdfState.value = TimetablePdfPreviewState(
            isGenerating = true,
            title = "Teacher Timetable — ${bundle.summary.teacherName}",
            statusLabel = "Generating A4 Landscape…",
            isLandscape = true
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = TimetablePdfGenerator.generateTeacherTimetablePdf(appContext, listOf(bundle))
                    val totalPages = PdfExportHelper.countPdfPages(file)
                    val preview = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 6)
                    Triple(file, totalPages, preview)
                }
            }.onSuccess { (file, totalPages, preview) ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    statusLabel = "Ready ($totalPages page)",
                    generatedFile = file,
                    totalPages = totalPages,
                    previewPages = preview
                )
            }.onFailure { err ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    error = err.message ?: "Failed to generate Teacher PDF"
                )
            }
        }
    }

    fun generateBatchAllTeachersTimetable(includeZip: Boolean) {
        val state = uiState.value
        val bundles = state.availableTimetableTeachers()
            .filter { it.active }
            .mapNotNull { buildTeacherPrintBundle(state, it.id) }
        if (bundles.isEmpty()) {
            emitMessage("No active teachers available to export.")
            return
        }
        clearTimetablePdfPreview()
        _timetablePdfState.value = TimetablePdfPreviewState(
            isGenerating = true,
            title = "All Teachers (${bundles.size}) — Timetables",
            statusLabel = "Generating ${bundles.size} teacher timetable(s)…",
            progressTotal = bundles.size,
            isLandscape = true
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val pdfFile = TimetablePdfGenerator.generateTeacherTimetablePdf(appContext, bundles) { cur, tot ->
                        _timetablePdfState.value = _timetablePdfState.value.copy(progressCurrent = cur, progressTotal = tot)
                    }
                    val zipFile = if (includeZip) {
                        TimetablePdfGenerator.generateBatchTeachersZip(appContext, bundles)
                    } else null
                    val totalPages = PdfExportHelper.countPdfPages(pdfFile)
                    val preview = PdfExportHelper.renderPdfPages(pdfFile, maxPagesToPreview = 12)
                    listOf(pdfFile, zipFile, totalPages, preview)
                }
            }.onSuccess { res ->
                val pdfFile = res[0] as File
                val zipFile = res[1] as File?
                val totalPages = res[2] as Int
                @Suppress("UNCHECKED_CAST")
                val preview = res[3] as List<Bitmap>
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    statusLabel = "Ready ($totalPages page(s)${if (zipFile != null) " + ZIP" else ""})",
                    generatedFile = pdfFile,
                    generatedZipFile = zipFile,
                    totalPages = totalPages,
                    previewPages = preview
                )
            }.onFailure { err ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    error = err.message ?: "Batch teacher export failed"
                )
            }
        }
    }

    fun generateMasterTimetablePdf(schoolDocId: String) {
        val state = uiState.value
        val schoolLongId = state.schoolDocIdByLongId.entries.firstOrNull { it.value == schoolDocId }?.key ?: state.activeSchool.id
        val school = state.schools.firstOrNull { it.id == schoolLongId } ?: state.activeSchool
        val groupsById = state.classGroupsForSchool(school.id).associateBy { it.id }
        val classes = state.classesForSchool(school.id)
        val teachersById = state.availableTimetableTeachers().associateBy { it.id }

        val rows = classes.flatMap { cls ->
            val grpName = groupsById[cls.classGroupId]?.name ?: "Classes"
            state.sectionsForSchoolClass(school.id, cls.name).map { sec ->
                val assignment = state.classAssignments.firstOrNull {
                    it.schoolId == schoolDocId &&
                        it.className.equals(cls.name, ignoreCase = true) &&
                        it.section.equals(sec, ignoreCase = true)
                }
                val ctShort = assignment?.classTeacherId?.takeIf { it.isNotBlank() }?.let { id ->
                    TimetableEngine.shortTeacherName(teachersById[id]?.name.orEmpty())
                }.orEmpty()
                val cellMap = state.activeSessionTimetableCells
                    .filter {
                        it.schoolId == schoolDocId &&
                            it.className.equals(cls.name, ignoreCase = true) &&
                            it.section.equals(sec, ignoreCase = true)
                    }
                    .associateBy { it.day to it.slotId }
                MasterClassSectionRow(
                    classGroupName = grpName,
                    className = cls.name,
                    section = sec,
                    classTeacherShort = ctShort,
                    cellsByDayAndSlot = cellMap
                )
            }
        }
        val bundle = MasterTimetablePrintBundle(
            school = school,
            session = state.filter.session,
            periodStructure = state.periodStructureForSchool(schoolDocId),
            rows = rows
        )
        clearTimetablePdfPreview()
        _timetablePdfState.value = TimetablePdfPreviewState(
            isGenerating = true,
            title = "Master Timetable — ${school.name}",
            statusLabel = "Generating A4 Landscape Master Timetable…",
            isLandscape = true
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = TimetablePdfGenerator.generateMasterTimetablePdf(appContext, bundle)
                    val totalPages = PdfExportHelper.countPdfPages(file)
                    val preview = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 10)
                    Triple(file, totalPages, preview)
                }
            }.onSuccess { (file, totalPages, preview) ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    statusLabel = "Ready ($totalPages page(s), A4 Landscape)",
                    generatedFile = file,
                    totalPages = totalPages,
                    previewPages = preview
                )
            }.onFailure { err ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    error = err.message ?: "Failed to generate Master PDF"
                )
            }
        }
    }

    fun saveTimetablePdfToUri(destinationUri: Uri) {
        val file = _timetablePdfState.value.generatedFile ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { PdfExportHelper.copyFileToUri(appContext, file, destinationUri) }
            emitMessage(if (ok) "Timetable PDF saved successfully" else "Could not save timetable PDF")
        }
    }

    fun saveTimetableZipToUri(destinationUri: Uri) {
        val file = _timetablePdfState.value.generatedZipFile ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { PdfExportHelper.copyFileToUri(appContext, file, destinationUri) }
            emitMessage(if (ok) "Timetable ZIP saved successfully" else "Could not save timetable ZIP")
        }
    }

    // =========================================================================
    // PHASE 6: AUTO-GENERATE TIMETABLE, TEACHER LEAVE & SUBSTITUTIONS
    // =========================================================================
    fun startAutoGenerate(config: AutoGenerateConfig) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("Timetable view & edit access is required to auto-generate.")
            return
        }
        if (config.schoolId.isBlank()) {
            emitMessage("Please select a school first.")
            return
        }
        autoGenerateJob?.cancel()
        autoGenerateCancelFlag.set(false)
        _autoGenerateResult.value = null
        _autoGenerateProgress.value = AutoGenerateProgress(
            isRunning = true,
            statusText = "Preparing constraints…",
            placedCount = 0,
            totalDemandCount = 0,
            restartAttempt = 0
        )

        autoGenerateJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val res = AutoGenerateEngine.generateTimetable(
                    config = config.copy(session = state.filter.session),
                    existingCellsInSession = state.activeSessionTimetableCells,
                    teachers = state.teachers,
                    periodStructuresBySchool = state.effectivePeriodStructuresBySchool,
                    classAssignments = state.classAssignments,
                    subjectRules = state.subjectRules,
                    onProgress = { progress ->
                        _autoGenerateProgress.value = progress
                    },
                    isCancelled = { autoGenerateCancelFlag.get() }
                )
                _autoGenerateProgress.value = AutoGenerateProgress(isRunning = false)
                _autoGenerateResult.value = res
            } catch (e: CancellationException) {
                _autoGenerateProgress.value = AutoGenerateProgress(isRunning = false)
                throw e
            } catch (e: Exception) {
                _autoGenerateProgress.value = AutoGenerateProgress(isRunning = false)
                emitMessage(e.message ?: "Auto-generate failed.")
            }
        }
    }

    fun cancelAutoGenerate() {
        autoGenerateCancelFlag.set(true)
        autoGenerateJob?.cancel()
        _autoGenerateProgress.value = AutoGenerateProgress(isRunning = false)
        emitMessage("Auto-generate cancelled.")
    }

    fun dismissAutoGenerateResult() {
        _autoGenerateResult.value = null
    }

    fun applyAutoGenerateResult(result: AutoGenerateResult) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("Timetable view & edit access is required.")
            return
        }
        val existingIds = state.timetableCells.map { it.id }.toSet()
        val editorName = state.currentMember?.name?.ifBlank { orgRepo.currentUserName() } ?: orgRepo.currentUserName()
        phase6Repo.commitAutoGeneratedCells(
            orgId = orgId,
            generatedCells = result.generatedCells,
            cellsToDeleteIds = result.cellsToDeleteIds,
            existingCellIdsInCloud = existingIds,
            undoSnapshot = result.undoSnapshot,
            editorName = editorName
        ).onSuccess {
            _autoGenerateResult.value = null
            val unplacedNote = if (result.unplacedItems.isNotEmpty()) {
                " (${result.unplacedItems.sumOf { it.missingPeriods }} unplaced)"
            } else ""
            emitMessage("Applied auto-generated timetable: ${result.newlyPlacedPeriods} cells written$unplacedNote. Undo is available.")
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Failed to apply generated timetable."))
        }
    }

    fun undoAutoGenerate(schoolDocId: String) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("Timetable view & edit access is required to undo.")
            return
        }
        val snapshot = state.latestUndoSnapshotForSchool(schoolDocId)
        if (snapshot == null) {
            emitMessage("No auto-generate undo snapshot available for this school.")
            return
        }
        val currentIds = state.timetableCells.map { it.id }.toSet()
        val editorName = state.currentMember?.name?.ifBlank { orgRepo.currentUserName() } ?: orgRepo.currentUserName()
        phase6Repo.restoreUndoSnapshot(
            orgId = orgId,
            snapshot = snapshot,
            currentCellIdsInCloud = currentIds,
            editorName = editorName
        ).onSuccess {
            emitMessage("Restored previous timetable (${snapshot.previousCells.size} cells restored).")
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Failed to undo auto-generate."))
        }
    }

    fun saveTeacherLeave(
        existingLeave: TeacherLeaveRecord?,
        teacherId: String,
        teacherName: String,
        startDate: String,
        endDate: String,
        reasonType: LeaveReason,
        reasonNote: String,
        halfDay: Boolean,
        halfDaySlotIds: List<String>,
        isSelfRequest: Boolean
    ) {
        val state = uiState.value
        val canEdit = state.canEdit(AppModule.TIMETABLE)
        val canView = state.canView(AppModule.TIMETABLE)
        if (!canView) {
            emitMessage("Timetable access is required.")
            return
        }
        if (!canEdit && !isSelfRequest) {
            emitMessage("Only Timetable editors can mark leave for other teachers.")
            return
        }
        if (!Phase6DateUtils.isValidIsoDate(startDate) || !Phase6DateUtils.isValidIsoDate(endDate)) {
            emitMessage("Please enter valid dates in YYYY-MM-DD format.")
            return
        }
        if (endDate < startDate) {
            emitMessage("End date cannot be before start date.")
            return
        }
        val editorName = state.currentMember?.name?.ifBlank { orgRepo.currentUserName() } ?: orgRepo.currentUserName()
        val nowMs = System.currentTimeMillis()
        val status = when {
            existingLeave != null -> existingLeave.status
            isSelfRequest && !canEdit -> LeaveStatus.PENDING
            else -> LeaveStatus.APPROVED
        }
        val leave = TeacherLeaveRecord(
            id = existingLeave?.id.orEmpty(),
            teacherId = teacherId,
            teacherName = teacherName,
            startDate = startDate.trim(),
            endDate = endDate.trim(),
            reasonType = reasonType,
            reasonNote = reasonNote.trim(),
            halfDay = halfDay,
            halfDaySlotIds = if (halfDay) halfDaySlotIds else emptyList(),
            status = status,
            requestedByUid = existingLeave?.requestedByUid?.ifBlank { currentUserId } ?: currentUserId,
            requestedByName = existingLeave?.requestedByName?.ifBlank { editorName } ?: editorName,
            approvedByUid = if (status == LeaveStatus.APPROVED) (existingLeave?.approvedByUid?.ifBlank { currentUserId } ?: currentUserId) else "",
            approvedByName = if (status == LeaveStatus.APPROVED) (existingLeave?.approvedByName?.ifBlank { editorName } ?: editorName) else "",
            approvedAtMs = if (status == LeaveStatus.APPROVED) (existingLeave?.approvedAtMs?.takeIf { it > 0L } ?: nowMs) else 0L
        )
        val auditAction = when {
            existingLeave != null -> "LEAVE_UPDATED"
            status == LeaveStatus.PENDING -> "LEAVE_REQUESTED"
            else -> "LEAVE_RECORDED"
        }
        phase6Repo.saveTeacherLeave(
            orgId = orgId,
            leave = leave,
            existsInCloud = existingLeave != null,
            editorName = editorName,
            auditAction = auditAction,
            clientEditedAtMs = nowMs
        ).onSuccess { docId ->
            offlinePrefs.upsertPendingPhase6Edit(
                uid = currentUserId,
                orgId = orgId,
                edit = LocalPendingPhase6Edit(
                    kind = "LEAVE",
                    docId = docId,
                    targetSummary = "Leave: $teacherName ($startDate..$endDate)",
                    localValueSummary = "${leave.status.label} ($startDate..$endDate)",
                    wasDeleted = false,
                    clientEditedAtMs = nowMs
                )
            )
            emitMessage(
                when {
                    existingLeave != null -> "Leave updated for $teacherName."
                    status == LeaveStatus.PENDING -> "Leave request submitted for approval."
                    else -> "Leave recorded for $teacherName."
                }
            )
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Could not save leave."))
        }
    }

    fun approveOrRejectLeave(leave: TeacherLeaveRecord, approve: Boolean) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("Timetable view & edit access is required to approve or reject leave.")
            return
        }
        val editorName = state.currentMember?.name?.ifBlank { orgRepo.currentUserName() } ?: orgRepo.currentUserName()
        val nowMs = System.currentTimeMillis()
        val updated = leave.copy(
            status = if (approve) LeaveStatus.APPROVED else LeaveStatus.REJECTED,
            approvedByUid = currentUserId,
            approvedByName = editorName,
            approvedAtMs = nowMs
        )
        phase6Repo.saveTeacherLeave(
            orgId = orgId,
            leave = updated,
            existsInCloud = true,
            editorName = editorName,
            auditAction = if (approve) "LEAVE_APPROVED" else "LEAVE_REJECTED",
            clientEditedAtMs = nowMs
        ).onSuccess { docId ->
            offlinePrefs.upsertPendingPhase6Edit(
                uid = currentUserId,
                orgId = orgId,
                edit = LocalPendingPhase6Edit(
                    kind = "LEAVE",
                    docId = docId,
                    targetSummary = "Leave: ${leave.teacherName} (${leave.startDate}..${leave.endDate})",
                    localValueSummary = "${updated.status.label} (${leave.startDate}..${leave.endDate})",
                    wasDeleted = false,
                    clientEditedAtMs = nowMs
                )
            )
            emitMessage(if (approve) "Approved leave for ${leave.teacherName}." else "Rejected leave for ${leave.teacherName}.")
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Failed to update leave status."))
        }
    }

    fun cancelTeacherLeave(leave: TeacherLeaveRecord) {
        val state = uiState.value
        val canEdit = state.canEdit(AppModule.TIMETABLE)
        val isOwnPending = leave.requestedByUid == currentUserId && leave.status == LeaveStatus.PENDING
        if (!canEdit && !isOwnPending) {
            emitMessage("You cannot cancel this leave.")
            return
        }
        val editorName = state.currentMember?.name?.ifBlank { orgRepo.currentUserName() } ?: orgRepo.currentUserName()
        val nowMs = System.currentTimeMillis()
        val updated = leave.copy(
            status = LeaveStatus.CANCELLED,
            approvedByUid = if (canEdit) leave.approvedByUid else "",
            approvedByName = if (canEdit) leave.approvedByName else "",
            approvedAtMs = if (canEdit) leave.approvedAtMs else 0L
        )
        phase6Repo.saveTeacherLeave(
            orgId = orgId,
            leave = updated,
            existsInCloud = true,
            editorName = editorName,
            auditAction = "LEAVE_CANCELLED",
            clientEditedAtMs = nowMs
        ).onSuccess { docId ->
            offlinePrefs.upsertPendingPhase6Edit(
                uid = currentUserId,
                orgId = orgId,
                edit = LocalPendingPhase6Edit(
                    kind = "LEAVE",
                    docId = docId,
                    targetSummary = "Leave: ${leave.teacherName} (${leave.startDate}..${leave.endDate})",
                    localValueSummary = "${updated.status.label} (${leave.startDate}..${leave.endDate})",
                    wasDeleted = false,
                    clientEditedAtMs = nowMs
                )
            )
            emitMessage("Leave cancelled for ${leave.teacherName}.")
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Failed to cancel leave."))
        }
    }

    fun assignSubstitution(item: SubstitutionNeedItem, candidate: SubstituteCandidate) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("Timetable view & edit access is required to assign substitutions.")
            return
        }
        val docId = SubstitutionRecord.makeSubstitutionDocId(
            date = item.date,
            schoolId = item.schoolId,
            className = item.className,
            section = item.section,
            slotId = item.slotId
        )
        val exists = state.substitutions.any { it.id == docId }
        val editorName = state.currentMember?.name?.ifBlank { orgRepo.currentUserName() } ?: orgRepo.currentUserName()
        val nowMs = System.currentTimeMillis()
        val record = SubstitutionRecord(
            id = docId,
            date = item.date,
            day = item.day,
            schoolId = item.schoolId,
            session = item.session,
            className = item.className,
            section = item.section,
            slotId = item.slotId,
            slotLabel = item.slotLabel,
            startMinutes = item.startMinutes,
            endMinutes = item.endMinutes,
            subjectName = item.subjectName,
            absentTeacherId = item.absentTeacherId,
            absentTeacherName = item.absentTeacherName,
            leaveId = item.leaveId,
            substituteTeacherId = candidate.teacher.id,
            substituteTeacherName = candidate.teacher.name,
            overrideUsed = candidate.overrideRequired,
            overrideReason = if (candidate.overrideRequired) candidate.overrideWarningReason else ""
        )
        phase6Repo.saveSubstitution(
            orgId = orgId,
            record = record,
            existsInCloud = exists,
            editorName = editorName,
            clientEditedAtMs = nowMs
        ).onSuccess {
            offlinePrefs.upsertPendingPhase6Edit(
                uid = currentUserId,
                orgId = orgId,
                edit = LocalPendingPhase6Edit(
                    kind = "SUB",
                    docId = docId,
                    targetSummary = "Sub: ${item.date} ${item.className}-${item.section} ${item.slotLabel}",
                    localValueSummary = candidate.teacher.name,
                    wasDeleted = false,
                    clientEditedAtMs = nowMs
                )
            )
            val tag = if (candidate.overrideRequired) " (Override)" else ""
            emitMessage("Assigned ${candidate.teacher.name}$tag for ${item.className}-${item.section} (${item.slotLabel}).")
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Failed to assign substitute."))
        }
    }

    fun clearSubstitution(record: SubstitutionRecord) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("Timetable view & edit access is required.")
            return
        }
        val nowMs = System.currentTimeMillis()
        phase6Repo.deleteSubstitution(orgId, record).onSuccess {
            offlinePrefs.upsertPendingPhase6Edit(
                uid = currentUserId,
                orgId = orgId,
                edit = LocalPendingPhase6Edit(
                    kind = "SUB",
                    docId = record.id,
                    targetSummary = "Sub: ${record.date} ${record.className}-${record.section} ${record.slotLabel}",
                    localValueSummary = "(Cleared)",
                    wasDeleted = true,
                    clientEditedAtMs = nowMs
                )
            )
            emitMessage("Cleared substitution for ${record.className}-${record.section} (${record.slotLabel}).")
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Failed to clear substitution."))
        }
    }

    fun generateSubstitutionSlipPdf(
        schoolDocId: String,
        date: String,
        items: List<SubstitutionNeedItem>
    ) {
        val state = uiState.value
        val schoolLongId = state.schoolDocIdByLongId.entries.firstOrNull { it.value == schoolDocId }?.key
        val school = state.schools.firstOrNull { it.id == schoolLongId } ?: state.activeSchool
        val scopedItems = if (schoolDocId.isNotBlank()) items.filter { it.schoolId == schoolDocId } else items
        val bundle = SubstitutionSlipPrintBundle(
            school = school,
            session = state.filter.session,
            date = date,
            items = scopedItems
        )
        clearTimetablePdfPreview()
        _timetablePdfState.value = TimetablePdfPreviewState(
            isGenerating = true,
            title = "Substitution Slip — ${school.name} ($date)",
            statusLabel = "Generating A4 Portrait Substitution Slip…",
            isLandscape = false
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = SubstitutionPdfGenerator.generateSubstitutionSlipPdf(appContext, bundle)
                    val totalPages = PdfExportHelper.countPdfPages(file)
                    val preview = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 10)
                    Triple(file, totalPages, preview)
                }
            }.onSuccess { (file, totalPages, preview) ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    statusLabel = "Ready ($totalPages page(s), A4 Portrait)",
                    generatedFile = file,
                    totalPages = totalPages,
                    previewPages = preview
                )
            }.onFailure { err ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    error = err.message ?: "Failed to generate Substitution Slip PDF"
                )
            }
        }
    }

    // =========================================================================
    // PHASE 7: TEACHER SCOPES SYNC, REPORT CARD LINKS, WORKLOAD & ATTENDANCE
    // =========================================================================
    private fun syncTeacherScopesAndClassTeachers(
        teachers: List<TeacherProfile>,
        assignments: List<ClassSectionAssignment>,
        onlySchoolId: String? = null
    ) {
        val state = uiState.value
        val canSyncScopes = state.isAdmin || state.canEdit(AppModule.TEACHERS) || state.canManageMembers()
        if (canSyncScopes) {
            val scopes = Phase7AccessEvaluator.buildTeacherScopes(teachers, assignments)
            val removedUids = state.allTeacherScopes.keys - scopes.keys
            phase7Repo.syncTeacherScopes(orgId, scopes, removedUids)
        }
        val targetSchoolIds = if (!onlySchoolId.isNullOrBlank()) {
            listOf(onlySchoolId)
        } else {
            state.schoolRecordsByDocId.keys.toList()
        }
        for (schId in targetSchoolIds) {
            if (schId in state.schoolRecordsByDocId) {
                val namesMap = Phase7AccessEvaluator.buildClassTeacherNamesBySection(schId, teachers, assignments)
                academicRepo.updateSchoolClassTeacherNames(orgId, schId, namesMap)
            }
        }
    }

    fun focusSchoolClassSection(schoolDocId: String, className: String, section: String) {
        val state = uiState.value
        val targetSchool = state.schoolRecordsByDocId[schoolDocId]
        val schoolLongId = targetSchool?.stableId ?: state.activeSchool.id
        _filter.value = _filter.value.copy(
            schoolId = schoolLongId,
            className = className,
            section = section.trim().uppercase().ifBlank { "A" },
            selectedStudentId = null
        )
    }

    fun updateSchoolReportCardLinks(
        schoolId: Long,
        printClassTeacherNameOnReports: Boolean,
        checkReportCardsReadyBeforeBatch: Boolean,
        openExamsForTeachers: List<String>
    ) {
        if (!uiState.value.canEdit(AppModule.SETTINGS)) {
            emitMessage("You do not have permission to change school report settings.")
            return
        }
        val rec = activeSchoolRecord(schoolId) ?: return
        academicRepo.updateSchoolReportCardLinks(
            orgId = orgId,
            schoolDocId = rec.docId,
            schoolName = rec.name,
            printClassTeacherNameOnReports = printClassTeacherNameOnReports,
            checkReportCardsReadyBeforeBatch = checkReportCardsReadyBeforeBatch,
            openExamsForTeachers = openExamsForTeachers
        ).onSuccess {
            emitMessage("Saved report card links & open exams for ${rec.name}")
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Could not save school report settings."))
        }
    }

    fun saveTeacherAttendance(
        schoolDocId: String,
        dateIso: String,
        teacher: TeacherProfile,
        status: TeacherAttendanceStatus,
        note: String = "",
        linkedLeaveId: String = ""
    ) {
        if (!uiState.value.canEdit(AppModule.TIMETABLE)) {
            emitMessage("Timetable edit access is required to mark teacher attendance.")
            return
        }
        if (status == TeacherAttendanceStatus.UNMARKED) return
        val docId = TeacherAttendanceRecord.makeDocId(dateIso, schoolDocId, teacher.id)
        val exists = uiState.value.teacherAttendanceRecords.any { it.id == docId }
        val record = TeacherAttendanceRecord(
            id = docId,
            date = dateIso,
            schoolId = schoolDocId,
            teacherId = teacher.id,
            teacherName = teacher.name,
            status = status,
            note = note,
            linkedLeaveId = linkedLeaveId
        )
        phase7Repo.saveTeacherAttendance(orgId, record, exists)
            .onSuccess {
                emitMessage("${teacher.name}: ${status.label} ($dateIso)")
            }
            .onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Could not save attendance."))
            }
    }

    fun markTeacherAbsentAndCreateLeave(
        schoolDocId: String,
        dateIso: String,
        teacher: TeacherProfile,
        onReadyForSubstitutions: (String) -> Unit
    ) {
        val state = uiState.value
        if (!state.canEdit(AppModule.TIMETABLE)) {
            emitMessage("Timetable edit access is required to create leave and manage substitutions.")
            return
        }
        val actorName = state.currentMember?.name?.ifBlank { orgRepo.currentEditorName() }
            ?: orgRepo.currentEditorName()
        val leave = TeacherAttendanceEngine.buildApprovedLeaveFromAttendanceAbsence(
            teacher = teacher,
            dateIso = dateIso,
            actorUid = currentUserId,
            actorName = actorName
        )
        phase6Repo.saveTeacherLeave(
            orgId = orgId,
            leave = leave,
            existsInCloud = false,
            editorName = actorName,
            auditAction = "TEACHER_LEAVE_CREATED_FROM_ATTENDANCE"
        )
            .onSuccess { leaveId ->
                saveTeacherAttendance(
                    schoolDocId = schoolDocId,
                    dateIso = dateIso,
                    teacher = teacher,
                    status = TeacherAttendanceStatus.ABSENT,
                    note = "Approved leave created from daily attendance",
                    linkedLeaveId = leaveId
                )
                emitMessage("Created approved leave for ${teacher.name} on $dateIso")
                onReadyForSubstitutions(dateIso)
            }
            .onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Could not create leave for ${teacher.name}."))
            }
    }

    fun generateWorkloadReportPdf(
        referenceDateIso: String,
        periodMode: WorkloadReportPeriodMode,
        schoolIdFilter: String?,
        teacherIdFilter: String?
    ) {
        val state = uiState.value
        val rows = state.computeWorkloadReport(
            referenceDateIso = referenceDateIso,
            schoolIdFilter = schoolIdFilter,
            teacherIdFilter = teacherIdFilter
        )
        val headerSchool = if (!schoolIdFilter.isNullOrBlank()) {
            state.schoolRecordsByDocId[schoolIdFilter]?.toSchoolEntity() ?: state.activeSchool
        } else {
            state.activeSchool.copy(
                name = state.activeOrg?.name?.ifBlank { state.activeSchool.name } ?: state.activeSchool.name
            )
        }
        val scopeTitle = if (!schoolIdFilter.isNullOrBlank()) {
            state.schoolNameByDocId(schoolIdFilter)
        } else {
            "All Schools (${state.schools.size})"
        }
        val bundle = WorkloadReportPrintBundle(
            headerSchool = headerSchool,
            scopeTitle = scopeTitle,
            session = state.filter.session,
            referenceDateIso = referenceDateIso,
            periodMode = periodMode,
            rows = rows
        )
        _timetablePdfState.value = TimetablePdfPreviewState(
            isGenerating = true,
            title = "Workload Report • $scopeTitle",
            statusLabel = "Generating A4 Landscape Workload Report PDF…",
            isLandscape = true
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = WorkloadReportPdfGenerator.generateWorkloadPdf(appContext, bundle)
                    val totalPages = PdfExportHelper.countPdfPages(file)
                    val preview = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 10)
                    Triple(file, totalPages, preview)
                }
            }.onSuccess { (file, totalPages, preview) ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    statusLabel = "Ready ($totalPages page(s), A4 Landscape)",
                    generatedFile = file,
                    totalPages = totalPages,
                    previewPages = preview
                )
            }.onFailure { err ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    error = err.message ?: "Failed to generate Workload Report PDF"
                )
            }
        }
    }

    fun exportWorkloadCsvToUri(
        destinationUri: Uri,
        referenceDateIso: String,
        periodMode: WorkloadReportPeriodMode,
        schoolIdFilter: String?,
        teacherIdFilter: String?
    ) {
        val state = uiState.value
        val rows = state.computeWorkloadReport(referenceDateIso, schoolIdFilter, teacherIdFilter)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val csv = WorkloadReportEngine.buildWorkloadCsv(rows, periodMode, referenceDateIso)
                PdfExportHelper.writeTextToUri(appContext, csv, destinationUri)
            }
            emitMessage(if (ok) "Exported Workload Report CSV (${rows.size} teacher(s))" else "Could not export Workload CSV")
        }
    }

    fun generateMonthlyTeacherAttendancePdf(
        schoolDocId: String,
        year: Int,
        month1Based: Int,
        monthLabel: String
    ) {
        val state = uiState.value
        val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
        val rows = state.buildMonthlyTeacherAttendance(schoolDocId, year, month1Based)
        val bundle = TeacherAttendanceMonthlyPrintBundle(
            school = school,
            session = state.filter.session,
            monthLabel = monthLabel,
            year = year,
            month1Based = month1Based,
            rows = rows
        )
        _timetablePdfState.value = TimetablePdfPreviewState(
            isGenerating = true,
            title = "Teacher Attendance • $monthLabel",
            statusLabel = "Generating A4 Portrait Attendance Summary PDF…",
            isLandscape = false
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = TeacherAttendancePdfGenerator.generateMonthlyAttendancePdf(appContext, bundle)
                    val totalPages = PdfExportHelper.countPdfPages(file)
                    val preview = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 10)
                    Triple(file, totalPages, preview)
                }
            }.onSuccess { (file, totalPages, preview) ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    statusLabel = "Ready ($totalPages page(s), A4 Portrait)",
                    generatedFile = file,
                    totalPages = totalPages,
                    previewPages = preview
                )
            }.onFailure { err ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    error = err.message ?: "Failed to generate Attendance Summary PDF"
                )
            }
        }
    }

    // =========================================================================
    // PHASE 8: STUDENT ATTENDANCE, SCHOOL CALENDAR, TERM AUTO-FILL & PDF/CSV
    // =========================================================================
    fun saveSchoolCalendar(calendar: SchoolCalendarConfig, onSaved: () -> Unit = {}) {
        val state = uiState.value
        if (!state.canEditSchoolCalendar(calendar.schoolId)) {
            emitMessage("Only Admins or Settings/Attendance editors can update the school calendar.")
            return
        }
        val validationError = SchoolCalendarEngine.validateTermDates(
            session = calendar.session,
            term1Start = calendar.term1StartDate,
            term1End = calendar.term1EndDate,
            term2Start = calendar.term2StartDate,
            term2End = calendar.term2EndDate
        )
        if (validationError != null) {
            emitMessage(validationError)
            return
        }
        if (calendar.workingDays.isEmpty()) {
            emitMessage("Select at least one working weekday.")
            return
        }
        val existsInCloud = state.hasSavedCalendarForSchool(calendar.schoolId, calendar.session)
        phase8Repo.saveSchoolCalendar(orgId, calendar, existsInCloud)
            .onSuccess {
                emitMessage("Saved school calendar for ${calendar.session}")
                onSaved()
            }
            .onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Unable to save school calendar."))
            }
    }

    fun addWorkingDayException(
        schoolDocId: String,
        dateIso: String,
        note: String = "Declared working day"
    ) {
        val state = uiState.value
        if (!state.canEditSchoolCalendar(schoolDocId)) {
            emitMessage("Only Admins or Settings editors can add a working day exception.")
            return
        }
        val cal = state.calendarForSchool(schoolDocId, state.filter.session)
        val updatedExceptions = (cal.workingDayExceptions.filterNot { it.date == dateIso } +
            WorkingDayExceptionEntry(
                id = "wex_${dateIso.replace("-", "_")}",
                date = dateIso,
                note = note.trim().ifBlank { "Working day override" }
            ))
            .sortedBy { it.date }
        saveSchoolCalendar(cal.copy(workingDayExceptions = updatedExceptions))
    }

    fun saveDailyStudentAttendance(
        schoolDocId: String,
        className: String,
        section: String,
        dateIso: String,
        entries: Map<String, StudentAttendanceEntry>,
        onSaved: () -> Unit = {}
    ) {
        val state = uiState.value
        val todayIso = Phase6DateUtils.todayIso()
        val daysBackLimit = state.schoolRecordsByDocId[schoolDocId]?.classTeacherAttendanceEditDaysBack
            ?: StudentAttendanceAccessEvaluator.DEFAULT_CLASS_TEACHER_DAYS_BACK

        val dateAccessError = StudentAttendanceAccessEvaluator.validateEditDateLimit(
            member = state.accessProfile,
            teacherScope = state.effectiveOwnTeacherScope,
            schoolId = schoolDocId,
            className = className,
            section = section,
            targetDateIso = dateIso,
            todayIso = todayIso,
            classTeacherDaysBackLimit = daysBackLimit
        )
        if (dateAccessError != null) {
            emitMessage(dateAccessError)
            return
        }

        val cal = state.calendarForSchool(schoolDocId, state.filter.session)
        val dayStatus = SchoolCalendarEngine.dayStatus(cal, dateIso)
        if (dayStatus is DayCalendarStatus.NonWorking) {
            emitMessage("Blocked: ${dayStatus.reasonTitle}. Use Admin exception if school was open.")
            return
        }

        val normSec = section.trim().uppercase().ifBlank { "A" }
        val docId = StudentAttendanceSheet.makeDocId(schoolDocId, state.filter.session, className, normSec, dateIso)
        val existingSheet = state.studentAttendanceSheets.firstOrNull { it.id == docId }

        val studentsByDocId = state.allStudents.mapNotNull { st ->
            val sid = state.studentDocIdFor(st.id)
            if (sid.isBlank()) null else sid to st
        }.toMap()

        val changes = StudentAttendanceEngine.computeEditChanges(existingSheet, entries, studentsByDocId)
        val writeEditLog = StudentAttendanceEngine.shouldWritePostDayEditLog(
            sheetDateIso = dateIso,
            todayIso = todayIso,
            sheetAlreadyExisted = existingSheet != null,
            changes = changes
        )

        val sheet = StudentAttendanceSheet(
            id = docId,
            schoolId = schoolDocId,
            session = state.filter.session,
            className = className,
            section = normSec,
            date = dateIso,
            entries = entries
        ).withRecomputedCounts()

        phase8Repo.saveStudentAttendanceSheet(
            orgId = orgId,
            sheet = sheet,
            existsInCloud = existingSheet != null,
            writeEditLog = writeEditLog,
            changes = changes
        ).onSuccess {
            val pTotal = sheet.presentCount + sheet.lateCount
            emitMessage("Saved Class $className-$normSec ($dateIso): $pTotal Present, ${sheet.absentCount} Absent")
            onSaved()
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Unable to save daily student attendance."))
        }
    }

    fun updateSchoolAttendanceSettings(
        schoolDocId: String,
        autoFillReportAttendance: Boolean,
        classTeacherDaysBack: Int
    ) {
        val state = uiState.value
        if (!state.canEdit(AppModule.SETTINGS) && !state.isAdmin) {
            emitMessage("Settings edit access is required to change attendance preferences.")
            return
        }
        val schName = state.schoolRecordsByDocId[schoolDocId]?.name ?: state.activeSchool.name
        academicRepo.updateSchoolAttendanceSettings(
            orgId = orgId,
            schoolDocId = schoolDocId,
            schoolName = schName,
            autoFillReportAttendance = autoFillReportAttendance,
            classTeacherAttendanceEditDaysBack = classTeacherDaysBack
        ).onSuccess {
            emitMessage("Saved student attendance settings for $schName")
        }.onFailure { err ->
            emitMessage(friendlyFirestoreErrorMessage(err, "Could not save student attendance settings."))
        }
    }

    fun recalculateStudentTermAttendance(studentId: Long, termNo: Int) {
        val (stuDocId, student) = studentDocIdByLongId[studentId] ?: return
        val rec = activeSchoolRecord(student.schoolId) ?: return
        val state = uiState.value
        val canRecalc = state.canEditSectionAttendance(rec.docId, student.className, student.section) ||
            state.canEditCoScholasticFor(student)
        if (!canRecalc) {
            emitMessage("You do not have permission to recalculate attendance for ${student.className}-${student.section}.")
            return
        }
        val comp = state.computeStudentTermAttendance(studentId, termNo)
        val existing = state.termInfoForStudent(studentId, termNo)
        val tiDocId = OrgAcademicRepository.makeTermInfoDocId(stuDocId, termNo)
        val item = TermAttendanceRecalcItem(
            studentDocId = stuDocId,
            termNo = termNo,
            daysPresent = comp.roundedDaysPresent,
            workingDays = comp.totalWorkingDays,
            existsInCloud = tiDocId in existingTermInfoDocIds,
            existingRemarks = existing?.remarks.orEmpty(),
            existingPromotedTo = existing?.promotedTo.orEmpty(),
            existingReportDate = existing?.reportDate.orEmpty()
        )
        academicRepo.recalculateTermAttendanceBatch(orgId, rec.docId, listOf(item))
            .onSuccess {
                emitMessage("Recalculated Term-$termNo for ${student.name}: ${comp.roundedDaysPresent}/${comp.totalWorkingDays}")
            }
            .onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Unable to recalculate attendance."))
            }
    }

    fun recalculateSectionTermAttendance(
        schoolDocId: String,
        className: String,
        section: String,
        termNo: Int
    ) {
        val state = uiState.value
        val canRecalc = state.canEditSectionAttendance(schoolDocId, className, section) ||
            state.canEditCoScholasticForSection(className, section)
        if (!canRecalc) {
            emitMessage("You do not have permission to recalculate attendance for Class $className-$section.")
            return
        }
        val schLongId = state.schoolRecordsByDocId[schoolDocId]?.stableId ?: state.activeSchool.id
        val secStudents = state.allStudents.filter {
            it.schoolId == schLongId &&
                it.session == state.filter.session &&
                it.className.equals(className, ignoreCase = true) &&
                it.section.equals(section, ignoreCase = true)
        }
        val updates = secStudents.mapNotNull { st ->
            val stuDocId = state.studentDocIdFor(st.id)
            if (stuDocId.isBlank()) return@mapNotNull null
            val comp = state.computeStudentTermAttendance(st.id, termNo)
            val existing = state.termInfoForStudent(st.id, termNo)
            val tiDocId = OrgAcademicRepository.makeTermInfoDocId(stuDocId, termNo)
            TermAttendanceRecalcItem(
                studentDocId = stuDocId,
                termNo = termNo,
                daysPresent = comp.roundedDaysPresent,
                workingDays = comp.totalWorkingDays,
                existsInCloud = tiDocId in existingTermInfoDocIds,
                existingRemarks = existing?.remarks.orEmpty(),
                existingPromotedTo = existing?.promotedTo.orEmpty(),
                existingReportDate = existing?.reportDate.orEmpty()
            )
        }
        if (updates.isEmpty()) {
            emitMessage("No students found in Class $className-$section.")
            return
        }
        academicRepo.recalculateTermAttendanceBatch(orgId, schoolDocId, updates)
            .onSuccess { count ->
                emitMessage("Recalculated Term-$termNo attendance from daily records for $count student(s)")
            }
            .onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Unable to recalculate section attendance."))
            }
    }

    fun dismissAttendanceConflictNotices() {
        phase8Repo.clearConflictNotices()
    }

    fun generateDailyAttendancePdf(
        schoolDocId: String,
        className: String,
        section: String,
        dateIso: String
    ) {
        val state = uiState.value
        val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
        val schLongId = state.schoolRecordsByDocId[schoolDocId]?.stableId ?: state.activeSchool.id
        val normSec = section.trim().uppercase().ifBlank { "A" }
        val students = state.allStudents
            .filter {
                it.schoolId == schLongId &&
                    it.session == state.filter.session &&
                    it.className.equals(className, ignoreCase = true) &&
                    it.section.equals(normSec, ignoreCase = true)
            }
            .mapNotNull { st ->
                val docId = state.studentDocIdFor(st.id)
                if (docId.isBlank()) null else docId to st
            }
        val sheetId = StudentAttendanceSheet.makeDocId(schoolDocId, state.filter.session, className, normSec, dateIso)
        val sheet = state.studentAttendanceSheets.firstOrNull { it.id == sheetId }
        val cal = state.calendarForSchool(schoolDocId, state.filter.session)
        val bundle = DailyAttendancePdfBundle(
            school = school,
            session = state.filter.session,
            className = className,
            section = normSec,
            dateIso = dateIso,
            classTeacherName = state.classTeacherNameForSection(schoolDocId, className, normSec),
            dayStatus = SchoolCalendarEngine.dayStatus(cal, dateIso),
            students = students,
            sheet = sheet
        )
        launchAttendancePdfJob(
            title = "Daily Attendance • $className-$normSec ($dateIso)",
            isLandscape = false
        ) {
            StudentAttendancePdfGenerator.generateDailyAttendancePdf(appContext, bundle)
        }
    }

    fun generateMonthlyRegisterPdf(
        schoolDocId: String,
        className: String,
        section: String,
        year: Int,
        month1Based: Int,
        monthLabel: String
    ) {
        val state = uiState.value
        val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
        val schLongId = state.schoolRecordsByDocId[schoolDocId]?.stableId ?: state.activeSchool.id
        val normSec = section.trim().uppercase().ifBlank { "A" }
        val students = state.allStudents
            .filter {
                it.schoolId == schLongId &&
                    it.session == state.filter.session &&
                    it.className.equals(className, ignoreCase = true) &&
                    it.section.equals(normSec, ignoreCase = true)
            }
            .mapNotNull { st ->
                val docId = state.studentDocIdFor(st.id)
                if (docId.isBlank()) null else docId to st
            }
        val prefix = String.format(java.util.Locale.US, "%04d-%02d-", year, month1Based)
        val sheetsByDate = state.studentAttendanceSheets
            .filter {
                it.schoolId == schoolDocId &&
                    it.session.equals(state.filter.session, ignoreCase = true) &&
                    it.className.equals(className, ignoreCase = true) &&
                    it.section.equals(normSec, ignoreCase = true) &&
                    it.date.startsWith(prefix)
            }
            .associateBy { it.date }
        val bundle = MonthlyRegisterPdfBundle(
            school = school,
            session = state.filter.session,
            className = className,
            section = normSec,
            year = year,
            month1Based = month1Based,
            monthLabel = monthLabel,
            classTeacherName = state.classTeacherNameForSection(schoolDocId, className, normSec),
            calendar = state.calendarForSchool(schoolDocId, state.filter.session),
            students = students,
            sheetsByDate = sheetsByDate
        )
        launchAttendancePdfJob(
            title = "Monthly Register • $className-$normSec ($monthLabel)",
            isLandscape = true
        ) {
            StudentAttendancePdfGenerator.generateMonthlyRegisterPdf(appContext, bundle)
        }
    }

    fun generateStudentAttendanceSummaryPdf(
        schoolDocId: String,
        className: String,
        section: String,
        startDate: String,
        endDate: String
    ) {
        val state = uiState.value
        val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
        val normSec = section.trim().uppercase().ifBlank { "A" }
        val rows = state.computeSectionRangeSummary(schoolDocId, className, normSec, startDate, endDate)
        val cal = state.calendarForSchool(schoolDocId, state.filter.session)
        val workingDays = SchoolCalendarEngine.countWorkingDaysInRange(cal, startDate, endDate).workingDays
        val bundle = StudentSummaryRangePdfBundle(
            school = school,
            session = state.filter.session,
            scopeTitle = "Class $className-$normSec",
            startDateIso = startDate,
            endDateIso = endDate,
            calendarWorkingDays = workingDays,
            rows = rows
        )
        launchAttendancePdfJob(
            title = "Attendance Summary • $className-$normSec",
            isLandscape = false
        ) {
            StudentAttendancePdfGenerator.generateStudentSummaryPdf(appContext, bundle)
        }
    }

    fun generateLowAttendancePdf(
        schoolDocId: String,
        classNameFilter: String?,
        sectionFilter: String?,
        startDate: String,
        endDate: String,
        thresholdPercent: Int
    ) {
        val state = uiState.value
        val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
        val rows = state.computeLowAttendanceRows(
            schoolDocIdFilter = schoolDocId,
            classNameFilter = classNameFilter,
            sectionFilter = sectionFilter,
            startDate = startDate,
            endDate = endDate,
            thresholdPercent = thresholdPercent
        )
        val scopeTitle = if (!classNameFilter.isNullOrBlank()) {
            val secPart = if (!sectionFilter.isNullOrBlank()) "-$sectionFilter" else ""
            "Class $classNameFilter$secPart"
        } else {
            "Whole School"
        }
        val bundle = LowAttendancePdfBundle(
            school = school,
            session = state.filter.session,
            scopeTitle = scopeTitle,
            startDateIso = startDate,
            endDateIso = endDate,
            thresholdPercent = thresholdPercent.toDouble(),
            rows = rows
        )
        launchAttendancePdfJob(
            title = "Low Attendance (<$thresholdPercent%) • $scopeTitle",
            isLandscape = false
        ) {
            StudentAttendancePdfGenerator.generateLowAttendancePdf(appContext, bundle)
        }
    }

    fun exportLowAttendanceCsvToUri(
        destinationUri: Uri,
        schoolDocId: String,
        classNameFilter: String?,
        sectionFilter: String?,
        startDate: String,
        endDate: String,
        thresholdPercent: Int
    ) {
        val state = uiState.value
        val rows = state.computeLowAttendanceRows(
            schoolDocIdFilter = schoolDocId,
            classNameFilter = classNameFilter,
            sectionFilter = sectionFilter,
            startDate = startDate,
            endDate = endDate,
            thresholdPercent = thresholdPercent
        )
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val csv = StudentAttendanceEngine.buildLowAttendanceCsv(rows, thresholdPercent, startDate, endDate)
                PdfExportHelper.writeTextToUri(appContext, csv, destinationUri)
            }
            emitMessage(if (ok) "Exported Low Attendance CSV (${rows.size} student(s))" else "Could not export Low Attendance CSV")
        }
    }

    private fun launchAttendancePdfJob(
        title: String,
        isLandscape: Boolean,
        generator: () -> File
    ) {
        val orientLabel = if (isLandscape) "A4 Landscape" else "A4 Portrait"
        _timetablePdfState.value = TimetablePdfPreviewState(
            isGenerating = true,
            title = title,
            statusLabel = "Generating $orientLabel PDF…",
            isLandscape = isLandscape
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val file = generator()
                    val totalPages = PdfExportHelper.countPdfPages(file)
                    val preview = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 10)
                    Triple(file, totalPages, preview)
                }
            }.onSuccess { (file, totalPages, preview) ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    statusLabel = "Ready ($totalPages page(s), $orientLabel)",
                    generatedFile = file,
                    totalPages = totalPages,
                    previewPages = preview
                )
            }.onFailure { err ->
                _timetablePdfState.value = _timetablePdfState.value.copy(
                    isGenerating = false,
                    error = err.message ?: "Failed to generate Attendance PDF"
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            appContext: Application,
            orgId: String,
            currentUserId: String,
            orgRepo: OrgCloudRepository,
            academicRepo: OrgAcademicRepository,
            phase4Repo: Phase4Repository,
            timetableRepo: TimetableRepository,
            phase6Repo: Phase6Repository,
            phase7Repo: Phase7Repository,
            phase8Repo: Phase8Repository,
            localRepo: ReportCardRepository,
            connectivityMonitor: NetworkConnectivityMonitor,
            offlinePrefs: OfflinePrefsStore
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SchoolViewModel(
                    appContext = appContext,
                    orgId = orgId,
                    currentUserId = currentUserId,
                    orgRepo = orgRepo,
                    academicRepo = academicRepo,
                    phase4Repo = phase4Repo,
                    timetableRepo = timetableRepo,
                    phase6Repo = phase6Repo,
                    phase7Repo = phase7Repo,
                    phase8Repo = phase8Repo,
                    localRepo = localRepo,
                    connectivityMonitor = connectivityMonitor,
                    offlinePrefs = offlinePrefs
                )
            }
        }
    }
}

private data class AcademicObserverKey(
    val tick: Int,
    val profile: MemberAccessProfile?,
    val schoolDocId: String?,
    val isClassTeacher: Boolean
)

private data class Phase4And5Bundle(
    val phase4: CloudPhase4Snapshot,
    val timetable: CloudTimetableSnapshot,
    val timetableConflicts: List<TimetableConflictNotice>,
    val phase6: CloudPhase6Snapshot,
    val phase6Conflicts: List<Phase6ConflictNotice>
)

private data class Phase10To12Slice(
    val phase10: CloudPhase10Snapshot,
    val phase11: CloudPhase11Snapshot,
    val phase12: CloudPhase12Snapshot,
    val phase10HindiLabels: Boolean,
    val firstTimeGuideSkipped: Boolean
)

private data class Phase8To10Bundle(
    val phase8: CloudPhase8Snapshot,
    val attendanceConflicts: List<AttendanceConflictNotice>,
    val phase9: CloudPhase9Snapshot,
    val phase10: CloudPhase10Snapshot,
    val phase11: CloudPhase11Snapshot,
    val phase12: CloudPhase12Snapshot,
    val phase10HindiLabels: Boolean,
    val firstTimeGuideSkipped: Boolean
)

private data class Phase7And8Bundle(
    val p46: Phase4And5Bundle,
    val phase7: CloudPhase7Snapshot,
    val multiSchoolPendingBundles: List<SchoolAcademicBundleForPending>,
    val phase8: CloudPhase8Snapshot,
    val attendanceConflicts: List<AttendanceConflictNotice>,
    val phase9: CloudPhase9Snapshot,
    val phase10: CloudPhase10Snapshot,
    val phase11: CloudPhase11Snapshot,
    val phase12: CloudPhase12Snapshot,
    val phase10HindiLabels: Boolean,
    val firstTimeGuideSkipped: Boolean
)

private data class AdminAndPhase4Bundle(
    val members: List<CloudMember>,
    val joinRequests: List<CloudJoinRequest>,
    val inviteCodes: List<CloudInviteCode>,
    val auditLogs: List<CloudAuditLog>,
    val phase4: CloudPhase4Snapshot,
    val timetable: CloudTimetableSnapshot,
    val timetableConflicts: List<TimetableConflictNotice>,
    val phase6: CloudPhase6Snapshot,
    val phase6Conflicts: List<Phase6ConflictNotice>,
    val phase7: CloudPhase7Snapshot,
    val multiSchoolPendingBundles: List<SchoolAcademicBundleForPending>,
    val phase8: CloudPhase8Snapshot,
    val attendanceConflicts: List<AttendanceConflictNotice>,
    val phase9: CloudPhase9Snapshot,
    val phase10: CloudPhase10Snapshot,
    val phase11: CloudPhase11Snapshot,
    val phase12: CloudPhase12Snapshot,
    val phase10HindiLabels: Boolean,
    val firstTimeGuideSkipped: Boolean
)

private data class CloudCoreBundle(
    val org: CloudOrganisation?,
    val member: CloudMember?,
    val schoolsSnap: CloudSchoolsSnapshot,
    val academicSnap: CloudSchoolAcademicSnapshot,
    val isOnline: Boolean
)

private data class SyncExtrasBundle(
    val syncError: String?,
    val lastSyncedAtMs: Long,
    val conflicts: List<MarkConflictNotice>,
    val isInitialized: Boolean,
    val membershipRevoked: Boolean
)
