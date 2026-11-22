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
import com.example.data.cloud.FirestoreProvider
import com.example.data.cloud.MigrationProgress
import com.example.data.cloud.NetworkConnectivityMonitor
import com.example.data.cloud.OfflinePrefsStore
import com.example.data.cloud.OrgAcademicRepository
import com.example.data.cloud.OrgCloudRepository
import com.example.data.cloud.Phase4Repository
import com.example.data.cloud.StableDocId
import com.example.domain.AppModule
import com.example.domain.AssignmentWarning
import com.example.domain.AttendanceSummary
import com.example.domain.ClassSectionAssignment
import com.example.domain.DefaultSchoolTemplate
import com.example.domain.DefaultSubjects
import com.example.domain.LocalPendingMarkEdit
import com.example.domain.MarkComponent
import com.example.domain.MarkConflictNotice
import com.example.domain.MarkValidationResult
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OfflineSyncEngine
import com.example.domain.OrgAccessEvaluator
import com.example.domain.OrgRole
import com.example.domain.Phase4Validator
import com.example.domain.ReportCalculations
import com.example.domain.ReportType
import com.example.domain.ResultStatus
import com.example.domain.SchoolPeriodStructure
import com.example.domain.ServerMarkObservation
import com.example.domain.SlipLayoutMode
import com.example.domain.StudentPerformanceReport
import com.example.domain.SubjectRule
import com.example.domain.SyncStatus
import com.example.domain.SyncUiState
import com.example.domain.TeacherProfile
import com.example.domain.TeacherWorkloadSummary
import com.example.util.BackupAndCsvManager
import com.example.util.MarksCsvIssue
import com.example.util.OperationType
import com.example.util.ParsedBackupResult
import com.example.util.PdfExportHelper
import com.example.util.PdfReportGenerator
import com.example.util.StudentReportBundle
import com.example.util.friendlyFirestoreErrorMessage
import com.example.util.handleFirestoreError
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val subjectRules: List<SubjectRule> = emptyList()
) {
    val activeSchoolDocId: String
        get() = schoolDocIdByLongId[activeSchool.id].orEmpty()

    val schoolNamesByDocId: Map<String, String>
        get() = schools.mapNotNull { sch -> schoolDocIdByLongId[sch.id]?.let { it to sch.name } }.toMap()

    fun schoolNameByDocId(schoolDocId: String): String =
        schoolNamesByDocId[schoolDocId] ?: schoolDocId

    fun periodStructureForSchool(schoolDocId: String): SchoolPeriodStructure =
        periodStructuresBySchool[schoolDocId] ?: Phase4Validator.buildDefaultPeriodStructure(schoolDocId)

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

    fun canEditMarkFor(student: StudentEntity, subjectName: String): Boolean {
        if (activeOrg == null) return true
        val schDocId = schoolDocIdByLongId[student.schoolId].orEmpty()
        return OrgAccessEvaluator.canEditMarksFor(accessProfile, schDocId, student.className, subjectName)
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
        val t1 = termInfoForStudent(studentId, 1)
        val t2 = termInfoForStudent(studentId, 2)
        return AttendanceSummary(
            t1Present = t1?.daysPresent ?: 0,
            t1Working = t1?.workingDays ?: 0,
            t2Present = t2?.daysPresent ?: 0,
            t2Working = t2?.workingDays ?: 0
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
    private val appContext: Application,
    val orgId: String,
    private val currentUserId: String,
    private val orgRepo: OrgCloudRepository,
    private val academicRepo: OrgAcademicRepository,
    private val phase4Repo: Phase4Repository,
    private val localRepo: ReportCardRepository,
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val offlinePrefs: OfflinePrefsStore
) : ViewModel() {

    private val _filter = MutableStateFlow(FilterSelection())
    private val _lastUndo = MutableStateFlow<MarkUndoSnapshot?>(null)
    private val _isInitialized = MutableStateFlow(false)
    private val _membershipRevoked = MutableStateFlow(false)
    private val _markConflicts = MutableStateFlow<List<MarkConflictNotice>>(emptyList())
    private val _observerRestartTick = MutableStateFlow(0)

    private val _pdfState = MutableStateFlow(PdfPreviewState())
    val pdfState: StateFlow<PdfPreviewState> = _pdfState.asStateFlow()

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
    private val _orgMembers = MutableStateFlow<List<CloudMember>>(emptyList())
    private val _joinRequests = MutableStateFlow<List<CloudJoinRequest>>(emptyList())
    private val _inviteCodes = MutableStateFlow<List<CloudInviteCode>>(emptyList())
    private val _auditLogs = MutableStateFlow<List<CloudAuditLog>>(emptyList())
    private val _isOnline = MutableStateFlow(connectivityMonitor.isCurrentlyOnline())

    // In-memory lookups from stable Long IDs back to Firestore doc IDs
    private var schoolRecordsByLongId: Map<Long, CloudSchoolRecord> = emptyMap()
    private var studentDocIdByLongId: Map<Long, Pair<String, StudentEntity>> = emptyMap()
    private var existingMarkValuesByDocId: Map<String, String> = emptyMap()
    private var existingMarkDocIds: Set<String> = emptySet()
    private var existingCoDocIds: Set<String> = emptySet()
    private var existingTermInfoDocIds: Set<String> = emptySet()
    private var sessionInitializedFromOrg = false
    private var seenActiveMemberOnce = false
    private var previousConfirmedProfile: MemberAccessProfile? = null
    private var isResettingCache = false

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
        academicRepo.syncError,
        phase4Repo.syncError
    ) { acadErr, p4Err -> acadErr ?: p4Err }

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
        _phase4Snapshot
    ) { members, reqs, invites, logs, p4 ->
        AdminAndPhase4Bundle(members, reqs, invites, logs, p4)
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
            admin.phase4.pendingWriteCount

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
            subjectRules = admin.phase4.subjectRules
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
                _filter.map { it.schoolId }
            ) { tick, profile, schools, selectedSchoolId ->
                val targetSchool = schools.firstOrNull { it.stableId == selectedSchoolId }
                    ?: schools.firstOrNull { it.active }
                    ?: schools.firstOrNull()
                Triple(tick, profile, targetSchool?.docId)
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile, schoolDocId) ->
                    if (profile == null || schoolDocId.isNullOrBlank() || !OrgAccessEvaluator.isActive(profile)) {
                        flowOf(null to CloudSchoolAcademicSnapshot())
                    } else {
                        academicRepo.observeSchoolAcademicData(orgId, schoolDocId, profile)
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
        // Strictly gated by AppModule.TEACHERS ("Teachers and assignments")
        viewModelScope.launch {
            combine(_observerRestartTick, _ownMember.map { it?.toAccessProfile() }) { tick, profile ->
                tick to profile
            }
                .distinctUntilChanged()
                .flatMapLatest { (_, profile) ->
                    if (profile == null || !OrgAccessEvaluator.canViewModule(profile, AppModule.TEACHERS)) {
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
            _observerRestartTick.value += 1
            isResettingCache = false
            emitMessage("Your access was updated by an admin. Cached data for removed access has been cleared.")
        }
    }

    fun dismissConflictBanner() {
        _markConflicts.value = emptyList()
    }

    fun retrySync() {
        if (!connectivityMonitor.isCurrentlyOnline()) {
            emitMessage("Still offline — changes are safely saved on this phone and will sync once online.")
            return
        }
        val retriedPhase4 = phase4Repo.retrySync()
        academicRepo.retrySync { msg ->
            emitMessage(if (retriedPhase4) "Retrying pending sync…" else msg)
        }
    }

    fun clearSyncError() {
        academicRepo.clearSyncError()
        phase4Repo.clearSyncError()
    }

    private fun handleListenerError(e: Throwable, sub: String) {
        val cause = (e as? FirebaseFirestoreException) ?: (e.cause as? FirebaseFirestoreException)
        if (cause != null) {
            handleFirestoreError(cause, OperationType.LIST, "organisations/$orgId/$sub")
            return
        }
        if (e is CancellationException) throw e
    }

    private fun emitMessage(message: String) {
        _snackbar.tryEmit(message)
    }

    private fun activeSchoolRecord(schoolId: Long = uiState.value.activeSchool.id): CloudSchoolRecord? =
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
        if (!uiState.value.canEdit(AppModule.STUDENTS)) {
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
        if (!uiState.value.canEdit(AppModule.STUDENTS)) {
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
        if (!uiState.value.canEdit(AppModule.STUDENTS)) {
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
        if (!uiState.value.canEditMarkFor(student, subjectName)) {
            emitMessage("You do not have permission to edit marks for ${student.className} • $subjectName.")
            return MarkValidationResult.Invalid("Read-only access")
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
        val rec = activeSchoolRecord() ?: return
        val infoMap = studentDocIdByLongId.mapValues {
            Triple(it.value.first, it.value.second.className, it.value.second.name)
        }
        viewModelScope.launch {
            val res = academicRepo.saveMarksBatch(
                orgId = orgId,
                schoolDocId = rec.docId,
                entries = preview.validMarks,
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
                emitMessage("Imported $count mark(s) for ${preview.matchedStudentCount} student(s)$issueSuffix")
            }.onFailure { err ->
                emitMessage(friendlyFirestoreErrorMessage(err, "Marks CSV import failed."))
            }
        }
    }

    // -------------------------------------------------------------------------
    // CO-SCHOLASTIC & ATTENDANCE
    // -------------------------------------------------------------------------
    fun saveCoScholasticGrade(studentId: Long, termNo: Int, areaCode: String, grade: String) {
        if (!uiState.value.canEdit(AppModule.CO_SCHOLASTIC)) {
            emitMessage("You only have view access to co-scholastic records.")
            return
        }
        val (stuDocId, student) = studentDocIdByLongId[studentId] ?: return
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
        if (!uiState.value.canEdit(AppModule.CO_SCHOLASTIC)) {
            emitMessage("You only have view access to attendance & remarks.")
            return
        }
        val err = ReportCalculations.validateAttendance(info.daysPresent, info.workingDays)
        if (err != null) {
            emitMessage(err)
            return
        }
        val (stuDocId, student) = studentDocIdByLongId[info.studentId] ?: return
        val rec = activeSchoolRecord(student.schoolId) ?: return
        val docId = OrgAcademicRepository.makeTermInfoDocId(stuDocId, info.termNo)
        viewModelScope.launch {
            academicRepo.saveTermInfo(
                orgId = orgId,
                schoolDocId = rec.docId,
                studentDocId = stuDocId,
                info = info,
                exists = docId in existingTermInfoDocIds
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
        res.onSuccess {
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
        phase4Repo.saveClassAssignment(orgId, assignment.copy(id = docId), existsInCloud)
            .onSuccess {
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
                term2Info = state.termInfoForStudent(st.id, 2)
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

    companion object {
        fun provideFactory(
            appContext: Application,
            orgId: String,
            currentUserId: String,
            orgRepo: OrgCloudRepository,
            academicRepo: OrgAcademicRepository,
            phase4Repo: Phase4Repository,
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
                    localRepo = localRepo,
                    connectivityMonitor = connectivityMonitor,
                    offlinePrefs = offlinePrefs
                )
            }
        }
    }
}

private data class AdminAndPhase4Bundle(
    val members: List<CloudMember>,
    val joinRequests: List<CloudJoinRequest>,
    val inviteCodes: List<CloudInviteCode>,
    val auditLogs: List<CloudAuditLog>,
    val phase4: CloudPhase4Snapshot
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
