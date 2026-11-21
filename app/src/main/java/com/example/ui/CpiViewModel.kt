package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CoScholasticEntity
import com.example.data.FullBackupData
import com.example.data.MarkEntity
import com.example.data.MarkUndoSnapshot
import com.example.data.ReportCardRepository
import com.example.data.SchoolSettingsEntity
import com.example.data.StudentEntity
import com.example.data.SubjectEntity
import com.example.data.TermInfoEntity
import com.example.domain.AttendanceSummary
import com.example.domain.ClassGroup
import com.example.domain.DefaultSubjects
import com.example.domain.MarkComponent
import com.example.domain.MarkValidationResult
import com.example.domain.ReportCalculations
import com.example.domain.ReportType
import com.example.domain.ResolvedSchoolIdentity
import com.example.domain.SchoolClass
import com.example.domain.SchoolIdentityCode
import com.example.domain.SlipLayoutMode
import com.example.domain.StudentPerformanceReport
import com.example.util.BackupAndCsvManager
import com.example.util.ImageUtils
import com.example.util.MarksCsvIssue
import com.example.util.PdfExportHelper
import com.example.util.PdfReportGenerator
import com.example.util.StudentReportBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FilterSelection(
    val session: String = "2026-2027",
    val className: String = "2nd",
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

data class CpiUiState(
    val isInitialized: Boolean = false,
    val settings: SchoolSettingsEntity = SchoolSettingsEntity(),
    val filter: FilterSelection = FilterSelection(),
    val subjects: List<SubjectEntity> = emptyList(),
    val allStudents: List<StudentEntity> = emptyList(),
    val allMarks: List<MarkEntity> = emptyList(),
    val allCoScholastic: List<CoScholasticEntity> = emptyList(),
    val allTermInfos: List<TermInfoEntity> = emptyList(),
    val sampleStudentCount: Int = 0,
    val legacyMarksCount: Int = 0,
    val lastMarkUndo: MarkUndoSnapshot? = null
) {
    val activeSchoolIdentity: ResolvedSchoolIdentity
        get() = ReportCalculations.resolveSchoolIdentity(settings, filter.className)

    fun identityForClass(className: String): ResolvedSchoolIdentity =
        ReportCalculations.resolveSchoolIdentity(settings, className)

    val activeClassGroup: ClassGroup
        get() = SchoolClass.fromName(filter.className).group

    fun subjectsForClass(className: String): List<String> {
        val group = SchoolClass.fromName(className).group
        val configured = subjects.filter { it.classGroup == group.id }.sortedBy { it.sortOrder }.map { it.name }
        return configured.ifEmpty { DefaultSubjects.forGroup(group) }
    }

    fun subjectsForGroup(group: ClassGroup): List<SubjectEntity> =
        subjects.filter { it.classGroup == group.id }.sortedBy { it.sortOrder }

    val sessionStudents: List<StudentEntity>
        get() = allStudents.filter { it.session == filter.session }

    val filteredStudents: List<StudentEntity>
        get() = allStudents
            .filter {
                it.session == filter.session &&
                    it.className.equals(filter.className, ignoreCase = true) &&
                    it.section.equals(filter.section, ignoreCase = true)
            }
            .sortedBy { it.rollNo }

    val selectedStudent: StudentEntity?
        get() = filteredStudents.firstOrNull { it.id == filter.selectedStudentId }
            ?: filteredStudents.firstOrNull()

    val availableSections: List<String>
        get() {
            val fromDb = allStudents
                .filter { it.session == filter.session && it.className.equals(filter.className, ignoreCase = true) }
                .map { it.section }
            return (SchoolClass.defaultSections + fromDb).distinct().sorted()
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
        session: String,
        className: String,
        section: String
    ): Map<Long, StudentPerformanceReport> {
        val subs = subjectsForClass(className)
        val classStudents = allStudents.filter {
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

    val marksCompletionPercent: Int
        get() {
            val students = sessionStudents
            if (students.isEmpty()) return 0
            val activeKeys = MarkComponent.validKeys
            val compCount = activeKeys.size // 6 components (IA, UT, Exam x 2 terms)
            var expectedCells = 0
            var filledCells = 0
            val marksByStudent = allMarks.groupBy { it.studentId }

            students.forEach { st ->
                val subs = subjectsForClass(st.className)
                expectedCells += subs.size * compCount
                val stMarks = marksByStudent[st.id].orEmpty()
                filledCells += stMarks.count { it.componentKey in activeKeys && it.rawValue.isNotBlank() && it.subjectName in subs }
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

class CpiViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = ReportCardRepository(database.reportCardDao())

    private val _filter = MutableStateFlow(FilterSelection())
    private val _lastUndo = MutableStateFlow<MarkUndoSnapshot?>(null)
    private val _isInitialized = MutableStateFlow(false)

    private val _pdfState = MutableStateFlow(PdfPreviewState())
    val pdfState: StateFlow<PdfPreviewState> = _pdfState.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private val _pendingImportBackup = MutableStateFlow<FullBackupData?>(null)
    val pendingImportBackup: StateFlow<FullBackupData?> = _pendingImportBackup.asStateFlow()

    private val _pendingMarksCsvImport = MutableStateFlow<MarksCsvImportPreview?>(null)
    val pendingMarksCsvImport: StateFlow<MarksCsvImportPreview?> = _pendingMarksCsvImport.asStateFlow()

    val uiState: StateFlow<CpiUiState> = combine(
        combine(
            repository.settingsFlow,
            repository.subjectsFlow,
            repository.studentsFlow,
            repository.marksFlow
        ) { settings, subjects, students, marks ->
            Quad4(settings, subjects, students, marks)
        },
        combine(
            repository.coScholasticFlow,
            repository.termInfoFlow,
            repository.sampleStudentCountFlow,
            repository.legacyMarksCountFlow
        ) { co, termInfo, sampleCount, legacyCount ->
            Quad4(co, termInfo, sampleCount, legacyCount)
        },
        _filter,
        _lastUndo,
        _isInitialized
    ) { first, second, filter, undo, initialized ->
        val settings = first.first ?: SchoolSettingsEntity()
        CpiUiState(
            isInitialized = initialized,
            settings = settings,
            filter = filter,
            subjects = first.second,
            allStudents = first.third,
            allMarks = first.fourth,
            allCoScholastic = second.first,
            allTermInfos = second.second,
            sampleStudentCount = second.third,
            legacyMarksCount = second.fourth,
            lastMarkUndo = undo
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CpiUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.initializeIfNeeded()
            val settings = repository.getFullBackupSnapshot().settings
            _filter.value = _filter.value.copy(session = settings.currentSession)
            _isInitialized.value = true
        }
    }

    private fun emitMessage(message: String) {
        _snackbar.tryEmit(message)
    }

    // -------------------------------------------------------------------------
    // FILTER & SELECTION ACTIONS
    // -------------------------------------------------------------------------
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

    // -------------------------------------------------------------------------
    // ONE-TIME LEGACY DATA CLEANUP (Note Book / Subject Enrich / Periodic Test)
    // -------------------------------------------------------------------------
    fun deleteLegacyMarksData() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = repository.deleteLegacyStructureMarks()
            emitMessage("Removed $count legacy Note Book / Subject Enrich / Periodic Test record(s)")
        }
    }

    // -------------------------------------------------------------------------
    // STUDENTS CRUD & CSV IMPORT
    // -------------------------------------------------------------------------
    suspend fun suggestRollNo(session: String, className: String, section: String): Int =
        withContext(Dispatchers.IO) {
            repository.suggestNextRollNo(session, className, section)
        }

    fun saveStudent(student: StudentEntity, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.saveStudent(student) }
            result.onSuccess { newId ->
                val normalizedClass = SchoolClass.fromName(student.className).displayName
                _filter.value = _filter.value.copy(
                    session = student.session,
                    className = normalizedClass,
                    section = student.section.uppercase().ifEmpty { "A" },
                    selectedStudentId = newId
                )
                emitMessage(if (student.id == 0L) "Added ${student.name}" else "Updated ${student.name}")
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, err.message ?: "Unable to save student")
            }
        }
    }

    fun deleteStudent(student: StudentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteStudent(student)
            if (_filter.value.selectedStudentId == student.id) {
                _filter.value = _filter.value.copy(selectedStudentId = null)
            }
            emitMessage("Deleted ${student.name}")
        }
    }

    fun importStudentsFromCsvUri(uri: Uri) {
        viewModelScope.launch {
            val state = uiState.value
            val summary = withContext(Dispatchers.IO) {
                val csvText = PdfExportHelper.readTextFromUri(getApplication(), uri)
                    ?: return@withContext "Could not read CSV file."
                val startRoll = repository.suggestNextRollNo(
                    state.filter.session,
                    state.filter.className,
                    state.filter.section
                )
                val parsed = BackupAndCsvManager.parseStudentsCsv(
                    csvText = csvText,
                    defaultSession = state.filter.session,
                    defaultClass = state.filter.className,
                    defaultSection = state.filter.section,
                    startingRollNo = startRoll
                )
                var imported = 0
                var duplicates = 0
                parsed.parsedStudents.forEach { st ->
                    val res = repository.saveStudent(st)
                    if (res.isSuccess) imported++ else duplicates++
                }
                buildString {
                    append("CSV Import: $imported added")
                    if (duplicates > 0) append(", $duplicates duplicate Adm No. skipped")
                    if (parsed.skippedRows > 0) append(", ${parsed.skippedRows} invalid rows skipped")
                }
            }
            emitMessage(summary)
        }
    }

    // -------------------------------------------------------------------------
    // MARKS ENTRY + VALIDATION + UNDO
    // -------------------------------------------------------------------------
    fun saveMark(
        student: StudentEntity,
        subjectName: String,
        component: MarkComponent,
        rawInput: String
    ): MarkValidationResult {
        val validation = ReportCalculations.validateMarkInput(rawInput, component.maxMarks)
        if (validation is MarkValidationResult.Valid) {
            viewModelScope.launch(Dispatchers.IO) {
                val prev = repository.saveMarkWithPrevious(
                    studentId = student.id,
                    subjectName = subjectName,
                    componentKey = component.key,
                    normalizedValue = validation.normalized
                )
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
            }
        }
        return validation
    }

    fun undoLastMarkEdit() {
        val snapshot = _lastUndo.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveMarkWithPrevious(
                studentId = snapshot.studentId,
                subjectName = snapshot.subjectName,
                componentKey = snapshot.componentKey,
                normalizedValue = snapshot.previousRawValue
            )
            _lastUndo.value = null
            emitMessage("Undid mark edit for ${snapshot.studentName} (${snapshot.subjectName})")
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
                val subjects = state.subjectsForClass(state.filter.className)
                val existingByStudent = students.associate { st ->
                    st.id to state.marksMapForStudent(st.id)
                }
                val csv = BackupAndCsvManager.generateMarksCsvTemplate(
                    session = state.filter.session,
                    className = state.filter.className,
                    section = state.filter.section,
                    students = students,
                    subjects = subjects,
                    existingMarksByStudent = existingByStudent
                )
                PdfExportHelper.writeTextToUri(getApplication(), csv, destinationUri)
            }
            emitMessage(
                if (ok) {
                    "Saved marks CSV template for Class ${state.filter.className}-${state.filter.section}"
                } else {
                    "Could not save marks CSV template"
                }
            )
        }
    }

    fun prepareMarksCsvImportFromUri(uri: Uri) {
        val state = uiState.value
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                val csvText = PdfExportHelper.readTextFromUri(getApplication(), uri)
                    ?: return@withContext null
                val students = repository.getAllStudents()
                val subjectsByGroup = ClassGroup.entries.associate { group ->
                    val configured = repository.getAllSubjects()
                        .filter { it.classGroup == group.id }
                        .sortedBy { it.sortOrder }
                        .map { it.name }
                    group.id to configured.ifEmpty { DefaultSubjects.forGroup(group) }
                }
                val parsed = BackupAndCsvManager.parseMarksCsv(
                    csvText = csvText,
                    students = students,
                    subjectsByGroup = subjectsByGroup,
                    defaultSession = state.filter.session,
                    defaultClass = state.filter.className,
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
            if (preview == null) {
                emitMessage("Could not read marks CSV file.")
            } else {
                _pendingMarksCsvImport.value = preview
            }
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.saveMarksBatch(preview.validMarks)
            }
            val issueSuffix = if (preview.issues.isNotEmpty()) " (${preview.issues.size} warning(s) skipped)" else ""
            emitMessage(
                "Imported ${preview.validMarks.size} mark(s) for ${preview.matchedStudentCount} student(s)$issueSuffix"
            )
        }
    }

    // -------------------------------------------------------------------------
    // CO-SCHOLASTIC & ATTENDANCE
    // -------------------------------------------------------------------------
    fun saveCoScholasticGrade(studentId: Long, termNo: Int, areaCode: String, grade: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveCoScholastic(studentId, termNo, areaCode, grade)
        }
    }

    fun saveTermInfo(info: TermInfoEntity, onSaved: () -> Unit = {}) {
        val err = ReportCalculations.validateAttendance(info.daysPresent, info.workingDays)
        if (err != null) {
            emitMessage(err)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveTermInfo(info)
            withContext(Dispatchers.Main) {
                emitMessage("Saved Term-${info.termNo} attendance & remarks")
                onSaved()
            }
        }
    }

    // -------------------------------------------------------------------------
    // SETTINGS, DUAL IDENTITIES & SUBJECTS
    // -------------------------------------------------------------------------
    fun updateSettings(updated: SchoolSettingsEntity, message: String = "Settings saved") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSettings(updated)
            emitMessage(message)
        }
    }

    fun updateIdentityLogoFromUri(code: SchoolIdentityCode, uri: Uri) {
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                ImageUtils.encodeUriToBase64Under150Kb(getApplication(), uri)
            }
            if (encoded != null) {
                val cur = uiState.value.settings
                val updated = if (code == SchoolIdentityCode.CPS || cur.useSameLogoForBoth) {
                    cur.copy(cpsLogoBase64 = encoded, cpsLogoCleared = false)
                } else {
                    cur.copy(cpiLogoBase64 = encoded, cpiLogoCleared = false)
                }
                withContext(Dispatchers.IO) { repository.saveSettings(updated) }
                emitMessage("${code.code} logo updated")
            } else {
                emitMessage("Could not process logo image")
            }
        }
    }

    fun clearIdentityLogo(code: SchoolIdentityCode) {
        val cur = uiState.value.settings
        val updated = if (code == SchoolIdentityCode.CPS || cur.useSameLogoForBoth) {
            cur.copy(cpsLogoBase64 = "", cpsLogoCleared = true)
        } else {
            cur.copy(cpiLogoBase64 = "", cpiLogoCleared = true)
        }
        updateSettings(updated, "${code.code} logo cleared (placeholder box active)")
    }

    fun restoreBundledIdentityLogo(code: SchoolIdentityCode) {
        val cur = uiState.value.settings
        val updated = if (code == SchoolIdentityCode.CPS || cur.useSameLogoForBoth) {
            cur.copy(cpsLogoBase64 = "", cpsLogoCleared = false)
        } else {
            cur.copy(cpiLogoBase64 = "", cpiLogoCleared = false)
        }
        updateSettings(updated, "Restored official ${code.defaultName} logo")
    }

    fun updateClassIdentityMapping(className: String, target: SchoolIdentityCode) {
        val cur = uiState.value.settings
        val map = SchoolClass.parseIdentityMap(cur.classIdentityMapCsv).toMutableMap()
        map[className] = target
        val serialized = SchoolClass.serializeIdentityMap(map)
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSettings(cur.copy(classIdentityMapCsv = serialized))
        }
    }

    fun addSession(sessionName: String) {
        val trimmed = sessionName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addSession(trimmed)
            _filter.value = _filter.value.copy(session = trimmed, selectedStudentId = null)
            emitMessage("Switched to session $trimmed")
        }
    }

    fun addSubject(group: ClassGroup, name: String) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { repository.addSubject(group.id, name) }
            res.onSuccess { emitMessage("Added subject $name") }
                .onFailure { emitMessage(it.message ?: "Unable to add subject") }
        }
    }

    fun renameSubject(subject: SubjectEntity, newName: String) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { repository.renameSubject(subject, newName) }
            res.onSuccess { emitMessage("Renamed subject to $newName") }
                .onFailure { emitMessage(it.message ?: "Unable to rename subject") }
        }
    }

    fun moveSubject(group: ClassGroup, subjectId: Long, moveUp: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveSubject(group.id, subjectId, moveUp)
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSubject(subject)
            emitMessage("Removed subject ${subject.name}")
        }
    }

    fun resetGroupSubjects(group: ClassGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.resetSubjectsForGroup(group.id)
            emitMessage("Reset ${group.label} subjects to default")
        }
    }

    fun clearSampleData() {
        viewModelScope.launch(Dispatchers.IO) {
            val removed = repository.clearSampleData()
            emitMessage(if (removed > 0) "Cleared $removed sample student(s)" else "No sample data remaining")
        }
    }

    fun restoreSampleData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restoreSampleData()
            emitMessage("Sample students loaded (Nursery, 2nd, 6th)")
        }
    }

    // -------------------------------------------------------------------------
    // PDF & BATCH ZIP GENERATION
    // -------------------------------------------------------------------------
    private fun buildBundles(state: CpiUiState, targets: List<StudentEntity>): List<StudentReportBundle> {
        val perfMap = state.performanceWithRanksForClass(
            state.filter.session,
            state.filter.className,
            state.filter.section
        )
        val subs = state.subjectsForClass(state.filter.className)
        return targets.map { st ->
            val perf = perfMap[st.id] ?: ReportCalculations.calculateStudentPerformance(
                studentId = st.id,
                subjects = subs,
                marksBySubjectAndKey = state.marksMapForStudent(st.id),
                passPercent = state.settings.passPercent
            )
            StudentReportBundle(
                student = st,
                schoolIdentity = state.identityForClass(st.className),
                subjects = subs,
                marksBySubjectAndKey = state.marksMapForStudent(st.id),
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
            emitMessage("No students found in Class ${state.filter.className}-${state.filter.section}")
            return
        }

        viewModelScope.launch {
            _pdfState.value = PdfPreviewState(
                isGenerating = true,
                statusLabel = "Generating A4 PDF…",
                progressCurrent = 0,
                progressTotal = targets.size,
                reportType = reportType,
                batchScope = batchWholeClass
            )

            try {
                val result = withContext(Dispatchers.IO) {
                    val bundles = buildBundles(state, targets)
                    val pdfFile = PdfReportGenerator.generateReportPdf(
                        context = getApplication(),
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
                emitMessage("Generated ${result.second} A4 page(s)")
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
            emitMessage("No students in Class ${state.filter.className}-${state.filter.section}")
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
                        context = getApplication(),
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
                PdfExportHelper.copyFileToUri(getApplication(), file, destinationUri)
            }
            emitMessage(if (ok) "PDF saved successfully" else "Could not save PDF")
        }
    }

    fun saveGeneratedZipToUri(destinationUri: Uri) {
        val file = _pdfState.value.generatedZipFile ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                PdfExportHelper.copyFileToUri(getApplication(), file, destinationUri)
            }
            emitMessage(if (ok) "Class ZIP saved successfully" else "Could not save ZIP")
        }
    }

    // -------------------------------------------------------------------------
    // JSON BACKUP & RESTORE
    // -------------------------------------------------------------------------
    fun exportBackupToUri(destinationUri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val snapshot = repository.getFullBackupSnapshot()
                val json = BackupAndCsvManager.serializeBackupToJson(snapshot)
                val written = PdfExportHelper.writeTextToUri(getApplication(), json, destinationUri)
                if (written) {
                    repository.markBackupCompleted(System.currentTimeMillis())
                }
                written
            }
            emitMessage(if (ok) "JSON backup saved successfully" else "Failed to export backup")
        }
    }

    fun createShareableBackupFile(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val snapshot = repository.getFullBackupSnapshot()
                val json = BackupAndCsvManager.serializeBackupToJson(snapshot)
                val dir = File(getApplication<Application>().cacheDir, "backups").apply { mkdirs() }
                val out = File(dir, "CPI_CPS_Backup_${snapshot.settings.currentSession}.json")
                out.writeText(json, Charsets.UTF_8)
                repository.markBackupCompleted(System.currentTimeMillis())
                out
            }
            onReady(file)
        }
    }

    fun prepareBackupImportFromUri(uri: Uri) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val text = PdfExportHelper.readTextFromUri(getApplication(), uri)
                    ?: return@withContext Result.failure<FullBackupData>(IllegalArgumentException("Could not read file"))
                BackupAndCsvManager.deserializeBackupJson(text)
            }
            parsed.onSuccess { data ->
                _pendingImportBackup.value = data
            }.onFailure { err ->
                emitMessage(err.message ?: "Invalid backup JSON file")
            }
        }
    }

    fun cancelPendingBackupImport() {
        _pendingImportBackup.value = null
    }

    fun confirmBackupImport(replaceAll: Boolean) {
        val data = _pendingImportBackup.value ?: return
        _pendingImportBackup.value = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.restoreBackup(data, replaceAll = replaceAll)
            }
            _filter.value = _filter.value.copy(
                session = data.settings.currentSession,
                selectedStudentId = null
            )
            val mode = if (replaceAll) "Replaced all data" else "Merged backup"
            emitMessage("$mode (${data.students.size} students, ${data.marks.size} marks)")
        }
    }
}

private data class Quad4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
