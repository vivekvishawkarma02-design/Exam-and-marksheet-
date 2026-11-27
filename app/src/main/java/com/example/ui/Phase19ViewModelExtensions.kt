package com.example.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.data.StudentEntity
import com.example.domain.AuthorizedPickupPerson
import com.example.domain.GatePassRecord
import com.example.domain.MemberAccessProfile
import com.example.domain.MemberPermissions
import com.example.domain.MemberStatus
import com.example.domain.OrgRole
import com.example.domain.ParentMeetingRecord
import com.example.domain.Phase17ResultAnalysisEngine
import com.example.domain.Phase19AccessEvaluator
import com.example.domain.Phase19RemarksPtmAndGateEngine
import com.example.domain.Phase6DateUtils
import com.example.domain.PtmCallStatus
import com.example.domain.PtmStudentSuggestion
import com.example.domain.ResultAnalysisExamFilter
import com.example.domain.ResultAnalysisScopedData
import com.example.domain.SchoolRemarkCategoryConfig
import com.example.domain.StudentAttendanceAccessEvaluator
import com.example.domain.StudentAttendanceEngine
import com.example.domain.StudentAttendanceEntry
import com.example.domain.StudentAttendanceSheet
import com.example.domain.StudentAttendanceStatus
import com.example.domain.StudentRemarkRecord
import com.example.domain.VisitorEntryRecord
import com.example.util.GatePassA5PdfRequest
import com.example.util.PdfExportHelper
import com.example.util.Phase19PdfGenerator
import com.example.util.PtmSummaryPdfRequest
import com.example.util.StudentRemarkSummaryPdfRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =============================================================================
// PHASE 19 VIEWMODEL EXTENSIONS:
// 1. STUDENT REMARKS (Sensitive visibility, cancel-with-reason + audit log,
//    follow-ups due, printable A4 summary for Owner/Admins only)
// 2. PARENT MEETINGS (PTM) (Smart suggestions from Result Analysis & Attendance,
//    per-student status/notes/decisions, WhatsApp invite, A4 PTM Summary PDF)
// 3. GATE PASS & VISITOR REGISTER (Authorised pickup persons, offline-safe unique
//    pass numbers, A5 slip PDF, daily gate register + CSV export, optional
//    half-day attendance mark, visitor check-in & one-tap sign-out)
// =============================================================================

private fun SchoolUiState.effectivePhase19ActorProfile(
    fallbackUid: String,
    schoolDocId: String
): MemberAccessProfile {
    return accessProfile ?: MemberAccessProfile(
        uid = fallbackUid.ifBlank { "local_owner" },
        role = OrgRole.OWNER,
        status = MemberStatus.ACTIVE,
        allSchools = true,
        allowedSchoolIds = listOf(schoolDocId),
        permissions = MemberPermissions.presetFor(OrgRole.OWNER)
    )
}

fun SchoolUiState.resolveStudentDocKey(student: StudentEntity): String =
    studentDocIdFor(student.id).ifBlank { "stu_${student.id}" }

fun SchoolViewModel.canViewStudentRemarksForClassSection(
    schoolDocId: String,
    className: String,
    section: String
): Boolean {
    val state = uiState.value
    if (state.activeOrg == null) return true
    return Phase19AccessEvaluator.canViewStudentRemarksForClass(
        member = state.accessProfile,
        teacherScope = state.effectiveOwnTeacherScope,
        schoolId = schoolDocId,
        className = className,
        section = section
    )
}

fun SchoolViewModel.canEditStudentRemarksForClassSection(
    schoolDocId: String,
    className: String,
    section: String
): Boolean {
    val state = uiState.value
    if (state.activeOrg == null) return true
    return Phase19AccessEvaluator.canEditStudentRemarksForClass(
        member = state.accessProfile,
        teacherScope = state.effectiveOwnTeacherScope,
        schoolId = schoolDocId,
        className = className,
        section = section
    )
}

fun SchoolViewModel.visibleStudentRemarksForActiveSchool(
    schoolDocId: String = uiState.value.activeSchoolDocId
): List<StudentRemarkRecord> {
    val state = uiState.value
    val raw = state.studentRemarks.filter { it.schoolId == schoolDocId }
    if (state.activeOrg == null) return raw
    return Phase19RemarksPtmAndGateEngine.filterRemarksForViewer(
        allRemarks = raw,
        member = state.accessProfile,
        teacherScope = state.effectiveOwnTeacherScope,
        schoolId = schoolDocId
    )
}

// =============================================================================
// 1. STUDENT REMARKS ACTIONS
// =============================================================================

fun SchoolViewModel.addStudentRemark(
    schoolDocId: String,
    session: String,
    student: StudentEntity,
    dateIso: String,
    category: String,
    note: String,
    followUpDateIso: String = "",
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (schoolDocId.isBlank()) {
        val msg = "Please select a school first."
        emitMessage(msg)
        onResult(false, msg)
        return
    }
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank {
        state.resolveOwnLinkedTeacher()?.name.orEmpty()
    }?.ifBlank { "Teacher" } ?: "Teacher"
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, schoolDocId)
    val studentDocId = state.resolveStudentDocKey(student)
    val normSec = student.section.trim().uppercase(Locale.US).ifBlank { "A" }

    phase19Repo.addStudentRemark(
        orgId = orgId,
        schoolId = schoolDocId,
        session = session,
        studentId = studentDocId,
        studentAdmNo = student.admNo,
        studentName = student.name,
        className = student.className,
        section = normSec,
        rollNo = student.rollNo.coerceAtLeast(0),
        dateIso = dateIso,
        category = category,
        note = note,
        followUpDateIso = followUpDateIso,
        actor = actorProfile,
        actorName = actorName,
        teacherScope = state.effectiveOwnTeacherScope
    ).onSuccess { record ->
        val msg = "Added ${record.category} remark for ${record.studentName}."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not add student remark."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.cancelStudentRemark(
    remark: StudentRemarkRecord,
    reason: String,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank {
        state.resolveOwnLinkedTeacher()?.name.orEmpty()
    }?.ifBlank { "Teacher" } ?: "Teacher"
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, remark.schoolId)

    phase19Repo.cancelStudentRemark(
        orgId = orgId,
        existing = remark,
        reason = reason,
        actor = actorProfile,
        actorName = actorName,
        teacherScope = state.effectiveOwnTeacherScope
    ).onSuccess {
        val msg = "Cancelled remark for ${remark.studentName} (recorded in audit log)."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not cancel remark."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.toggleRemarkFollowUpCompleted(
    remark: StudentRemarkRecord,
    completed: Boolean,
    completionNote: String = remark.followUpCompletedNote,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, remark.schoolId)

    phase19Repo.markRemarkFollowUpCompleted(
        orgId = orgId,
        existing = remark,
        completed = completed,
        completionNote = completionNote,
        actor = actorProfile,
        teacherScope = state.effectiveOwnTeacherScope
    ).onSuccess {
        val msg = if (completed) {
            "Marked follow-up completed for ${remark.studentName}."
        } else {
            "Reopened follow-up for ${remark.studentName}."
        }
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not update follow-up status."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.saveSchoolRemarkCategoryConfig(
    config: SchoolRemarkCategoryConfig,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (config.schoolId.isBlank()) {
        val msg = "Please select a school first."
        emitMessage(msg)
        onResult(false, msg)
        return
    }
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank { "Admin" } ?: "Admin"
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, config.schoolId)

    phase19Repo.saveSchoolRemarkCategoryConfig(
        orgId = orgId,
        config = config,
        actor = actorProfile,
        actorName = actorName
    ).onSuccess {
        val msg = "Saved remark categories & settings."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not save remark categories."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.previewStudentRemarkSummaryPdf(
    schoolDocId: String,
    session: String,
    student: StudentEntity?,
    classFilterLabel: String,
    remarks: List<StudentRemarkRecord>,
    configuredCategories: List<String> = uiState.value.remarkCategoryConfigForSchool(schoolDocId).effectiveCategories
) {
    val state = uiState.value
    if (state.activeOrg != null && !Phase19AccessEvaluator.canPrintStudentRemarkSummaryPdf(state.accessProfile, schoolDocId)) {
        emitMessage("Only the School Owner and Admins can print the Student Remark Summary PDF.")
        return
    }
    val school = state.schoolRecordsByDocId[schoolDocId]?.toSchoolEntity() ?: state.activeSchool
    val actorName = state.currentMember?.name?.ifBlank { "Admin" } ?: "Admin"
    val titleTarget = student?.name ?: classFilterLabel.ifBlank { "All Classes" }
    val titleLabel = if (student != null) {
        "Student Remark Summary — ${student.name} (${student.className}-${student.section})"
    } else {
        "Student Remark Summary — $titleTarget"
    }
    val classSectionLabel = if (student != null) {
        "${student.className}-${student.section}"
    } else {
        classFilterLabel.ifBlank { "All Classes" }
    }
    val request = StudentRemarkSummaryPdfRequest(
        school = school,
        session = session,
        titleLabel = titleLabel,
        studentName = student?.name.orEmpty(),
        classSectionLabel = classSectionLabel,
        admissionNo = student?.admNo.orEmpty(),
        remarks = remarks,
        categoryCounts = Phase19RemarksPtmAndGateEngine.computeCategoryCounts(remarks, configuredCategories),
        generatedByName = actorName
    )
    _timetablePdfState.value = TimetablePdfPreviewState(
        isGenerating = true,
        title = titleLabel,
        statusLabel = "Generating A4 Student Remark Summary PDF…",
        isLandscape = false
    )
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val file = Phase19PdfGenerator.generateStudentRemarkSummaryPdf(appContext, request)
                val rendered = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 10)
                val preview = rendered.ifEmpty {
                    Phase19PdfGenerator.renderStudentRemarkSummaryBitmaps(request)
                }
                val totalPages = PdfExportHelper.countPdfPages(file).takeIf { it > 0 } ?: preview.size.coerceAtLeast(1)
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
                error = err.message ?: "Failed to generate Student Remark Summary PDF"
            )
        }
    }
}

// =============================================================================
// 2. PARENT MEETINGS (PTM) ACTIONS
// =============================================================================

fun SchoolViewModel.computePtmStudentSuggestionsForScope(
    schoolDocId: String,
    session: String,
    className: String,
    section: String
): List<PtmStudentSuggestion> {
    val state = uiState.value
    val schLongId = state.schoolRecordsByDocId[schoolDocId]?.stableId ?: state.activeSchool.id
    val cleanClass = className.trim()
    val cleanSec = section.trim().uppercase(Locale.US)
    val isAllClasses = cleanClass.isBlank() || cleanClass.equals("ALL", ignoreCase = true)
    val isAllSections = cleanSec.isBlank() || cleanSec == "ALL"

    val classStudents = state.allStudents.filter { st ->
        st.schoolId == schLongId &&
            st.session == session &&
            st.isActiveStatus &&
            (isAllClasses || st.className.trim().equals(cleanClass, ignoreCase = true)) &&
            (isAllSections || st.section.trim().ifBlank { "A" }.equals(cleanSec, ignoreCase = true))
    }

    // 1. Weak students from Phase 17 Result Analysis Engine (ALL exams combined, threshold 40%)
    val schoolClasses = state.classesForSchool(schLongId)
    val subjectsByClassSection = linkedMapOf<Pair<String, String>, List<String>>()
    val allowedSectionsByClass = linkedMapOf<String, MutableList<String>>()
    for (st in classStudents) {
        val cName = st.className.trim()
        val sName = st.section.trim().uppercase(Locale.US).ifBlank { "A" }
        allowedSectionsByClass.getOrPut(cName) { mutableListOf() }.let { list ->
            if (sName !in list) list.add(sName)
        }
        val key = cName to sName
        if (!subjectsByClassSection.containsKey(key)) {
            val clsEntity = schoolClasses.firstOrNull { it.name.equals(cName, ignoreCase = true) }
            val subs = if (clsEntity != null) {
                state.subjects.filter { it.classGroupId == clsEntity.classGroupId }.sortedBy { it.sortOrder }.map { it.name }
            } else {
                emptyList()
            }
            subjectsByClassSection[key] = subs
        }
    }
    val studentIds = classStudents.map { it.id }.toSet()
    val scopedData = ResultAnalysisScopedData(
        allowedClasses = allowedSectionsByClass.keys.toList(),
        allowedSectionsByClass = allowedSectionsByClass,
        subjectsByClassSection = subjectsByClassSection,
        scopedStudents = classStudents,
        scopedMarks = state.allMarks.filter { it.studentId in studentIds },
        isTeacherRestricted = false
    )
    val analysisBundle = Phase17ResultAnalysisEngine.computeBundle(
        scopedData = scopedData,
        schoolDocId = schoolDocId,
        selectedClass = if (isAllClasses) "ALL" else cleanClass,
        selectedSection = if (isAllSections) "ALL" else cleanSec,
        examFilter = ResultAnalysisExamFilter.ALL,
        weakThresholdPercent = 40
    )

    // 2. Low attendance students (< 75%) from Phase 8 StudentAttendanceEngine
    val schName = state.schoolRecordsByDocId[schoolDocId]?.name ?: state.activeSchool.name
    val studentPairs = classStudents.map { st ->
        state.resolveStudentDocKey(st) to st
    }
    val studentTriples = studentPairs.map { (docId, st) ->
        Triple(schoolDocId, schName, docId to st)
    }
    val calMap = mapOf(schoolDocId to state.calendarForSchool(schoolDocId, session))
    val schoolSheets = state.studentAttendanceSheets.filter {
        it.schoolId == schoolDocId && it.session == session
    }
    val todayIso = Phase6DateUtils.todayIso()
    val earliestSheetDate = schoolSheets.minOfOrNull { it.date } ?: "${todayIso.take(4)}-04-01"
    val lowAttendanceRows = StudentAttendanceEngine.buildLowAttendanceList(
        students = studentTriples,
        session = session,
        calendarsBySchool = calMap,
        sheets = schoolSheets,
        startDate = earliestSheetDate,
        endDate = todayIso,
        thresholdPercent = 75,
        schoolIdFilter = schoolDocId,
        classNameFilter = if (isAllClasses) null else cleanClass,
        sectionFilter = if (isAllSections) null else cleanSec
    )

    // 3. Visible remarks for this school
    val visibleRemarks = visibleStudentRemarksForActiveSchool(schoolDocId)

    return Phase19RemarksPtmAndGateEngine.buildPtmStudentSuggestions(
        students = studentPairs,
        weakStudents = analysisBundle.weakStudents,
        lowAttendanceRows = lowAttendanceRows,
        visibleRemarks = visibleRemarks,
        classFilter = if (isAllClasses) null else cleanClass,
        sectionFilter = if (isAllSections) null else cleanSec
    )
}

fun SchoolViewModel.saveParentMeeting(
    meeting: ParentMeetingRecord,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (meeting.schoolId.isBlank()) {
        val msg = "Please select a school first."
        emitMessage(msg)
        onResult(false, msg)
        return
    }
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank {
        state.resolveOwnLinkedTeacher()?.name.orEmpty()
    }?.ifBlank { "Teacher" } ?: "Teacher"
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, meeting.schoolId)
    val isNew = meeting.id.isBlank() || state.parentMeetings.none { it.id == meeting.id }

    phase19Repo.saveParentMeeting(
        orgId = orgId,
        meeting = meeting,
        isNew = isNew,
        actor = actorProfile,
        actorName = actorName,
        teacherScope = state.effectiveOwnTeacherScope
    ).onSuccess { saved ->
        val msg = "Saved PTM '${saved.title}' (${saved.entries.size} student(s) called)."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not save Parent Meeting."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.updatePtmStudentOutcome(
    meeting: ParentMeetingRecord,
    studentId: String,
    status: PtmCallStatus,
    discussionNote: String,
    decision: String,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank {
        state.resolveOwnLinkedTeacher()?.name.orEmpty()
    }?.ifBlank { "Teacher" } ?: "Teacher"
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, meeting.schoolId)

    phase19Repo.updatePtmStudentOutcome(
        orgId = orgId,
        meeting = meeting,
        studentId = studentId,
        status = status,
        discussionNote = discussionNote,
        decision = decision,
        actor = actorProfile,
        actorName = actorName,
        teacherScope = state.effectiveOwnTeacherScope
    ).onSuccess {
        val msg = "Updated PTM record for student."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not update PTM outcome."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.previewPtmSummaryPdf(
    meeting: ParentMeetingRecord
) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[meeting.schoolId]?.toSchoolEntity() ?: state.activeSchool
    val actorName = state.currentMember?.name?.ifBlank { "Teacher" } ?: "Teacher"
    val request = PtmSummaryPdfRequest(
        school = school,
        meeting = meeting,
        generatedByName = actorName
    )
    val title = "PTM Summary • ${meeting.dateIso} (${meeting.scopeLabel})"
    _timetablePdfState.value = TimetablePdfPreviewState(
        isGenerating = true,
        title = title,
        statusLabel = "Generating A4 PTM Summary PDF…",
        isLandscape = false
    )
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val file = Phase19PdfGenerator.generatePtmSummaryPdf(appContext, request)
                val rendered = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 10)
                val preview = rendered.ifEmpty {
                    Phase19PdfGenerator.renderPtmSummaryBitmaps(request)
                }
                val totalPages = PdfExportHelper.countPdfPages(file).takeIf { it > 0 } ?: preview.size.coerceAtLeast(1)
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
                error = err.message ?: "Failed to generate PTM Summary PDF"
            )
        }
    }
}

// =============================================================================
// 3. AUTHORISED PICKUP PERSONS (Stored in Student Register)
// =============================================================================

fun SchoolViewModel.authorizedPickupsForStudent(
    student: StudentEntity,
    schoolDocId: String = uiState.value.activeSchoolDocId
): List<AuthorizedPickupPerson> {
    val state = uiState.value
    val studentDocKey = state.resolveStudentDocKey(student)
    val legacyKey = student.id.toString()
    return state.authorizedPickups.filter {
        it.schoolId == schoolDocId &&
            (it.studentId == studentDocKey || it.studentId == legacyKey)
    }
}

fun SchoolViewModel.saveAuthorizedPickupPerson(
    schoolDocId: String,
    student: StudentEntity,
    personName: String,
    relation: String,
    mobile: String,
    photoBase64: String = "",
    existingId: String = "",
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (schoolDocId.isBlank()) {
        val msg = "Please select a school first."
        emitMessage(msg)
        onResult(false, msg)
        return
    }
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank { "Staff" } ?: "Staff"
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, schoolDocId)
    val studentDocId = state.resolveStudentDocKey(student)

    val person = AuthorizedPickupPerson(
        id = existingId,
        schoolId = schoolDocId,
        studentId = studentDocId,
        studentAdmNo = student.admNo.trim(),
        studentName = student.name.trim(),
        className = student.className.trim(),
        section = student.section.trim().uppercase(Locale.US).ifBlank { "A" },
        personName = personName.trim(),
        relation = relation.trim(),
        mobile = mobile.trim(),
        photoBase64 = photoBase64
    )

    phase19Repo.saveAuthorizedPickupPerson(
        orgId = orgId,
        person = person,
        actor = actorProfile,
        actorName = actorName
    ).onSuccess { saved ->
        val msg = "Saved authorised pickup person '${saved.personName}' (${saved.relation}) for ${saved.studentName}."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not save authorised pickup person."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.deleteAuthorizedPickupPerson(
    person: AuthorizedPickupPerson,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, person.schoolId)

    phase19Repo.deleteAuthorizedPickupPerson(
        orgId = orgId,
        person = person,
        actor = actorProfile
    ).onSuccess {
        val msg = "Removed '${person.personName}' from authorised pickup list."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not remove authorised pickup person."
        emitMessage(msg)
        onResult(false, msg)
    }
}

// =============================================================================
// 4. EARLY LEAVE GATE PASS & DAILY GATE REGISTER
// =============================================================================

fun SchoolViewModel.canMarkHalfDayAttendanceFromGatePass(
    schoolDocId: String,
    className: String,
    section: String
): Boolean {
    val state = uiState.value
    if (state.activeOrg == null) return true
    return Phase19AccessEvaluator.canMarkHalfDayFromGatePass(
        member = state.accessProfile,
        teacherScope = state.effectiveOwnTeacherScope,
        schoolId = schoolDocId,
        className = className,
        section = section
    )
}

fun SchoolViewModel.issueEarlyLeaveGatePass(
    schoolDocId: String,
    session: String,
    student: StudentEntity,
    dateIso: String,
    leaveTime: String,
    reason: String,
    pickupFromAuthorizedList: Boolean,
    authorizedPickupPerson: AuthorizedPickupPerson?,
    oneTimePersonName: String,
    oneTimePersonRelation: String,
    oneTimePersonMobile: String,
    oneTimeNote: String,
    guardianPhoneConfirmed: Boolean,
    approvedByName: String,
    markHalfDayAttendance: Boolean,
    autoPreviewPdf: Boolean = true,
    onResult: (Boolean, String, GatePassRecord?) -> Unit = { _, _, _ -> }
) {
    val state = uiState.value
    if (schoolDocId.isBlank()) {
        val msg = "Please select a school first."
        emitMessage(msg)
        onResult(false, msg, null)
        return
    }
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank {
        state.resolveOwnLinkedTeacher()?.name.orEmpty()
    }?.ifBlank { "Gate Staff" } ?: "Gate Staff"
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, schoolDocId)
    val studentDocId = state.resolveStudentDocKey(student)
    val normSec = student.section.trim().uppercase(Locale.US).ifBlank { "A" }

    val existingPassNumbers = state.gatePasses
        .filter { it.schoolId == schoolDocId && it.session == session }
        .map { it.passNumber }

    val uniquePassNumber = Phase19RemarksPtmAndGateEngine.generateUniqueGatePassNumber(
        schoolId = schoolDocId,
        session = session,
        dateIso = dateIso,
        deviceOrActorKey = actorUid,
        existingPassNumbersInSchoolSession = existingPassNumbers
    )

    val usingAuthorized = pickupFromAuthorizedList && authorizedPickupPerson != null
    val pName = if (usingAuthorized) authorizedPickupPerson!!.personName else oneTimePersonName.trim()
    val pRel = if (usingAuthorized) authorizedPickupPerson!!.relation else oneTimePersonRelation.trim()
    val pMob = if (usingAuthorized) authorizedPickupPerson!!.mobile else oneTimePersonMobile.trim()

    val canMarkAttendance = canMarkHalfDayAttendanceFromGatePass(
        schoolDocId = schoolDocId,
        className = student.className.trim(),
        section = normSec
    )
    val effectiveMarkHalfDay = markHalfDayAttendance && canMarkAttendance

    val record = GatePassRecord(
        id = "",
        passNumber = uniquePassNumber,
        schoolId = schoolDocId,
        session = session,
        dateIso = dateIso.trim(),
        leaveTime = leaveTime.trim(),
        studentId = studentDocId,
        studentAdmNo = student.admNo.trim(),
        studentName = student.name.trim(),
        className = student.className.trim(),
        section = normSec,
        rollNo = student.rollNo.coerceAtLeast(0),
        reason = reason.trim(),
        pickupPersonId = if (usingAuthorized) authorizedPickupPerson!!.id else "",
        pickupPersonName = pName,
        pickupRelation = pRel,
        pickupMobile = pMob,
        isOneTimePickup = !usingAuthorized,
        oneTimeNote = if (usingAuthorized) "" else oneTimeNote.trim(),
        guardianPhoneConfirmed = if (usingAuthorized) true else guardianPhoneConfirmed,
        approvedByUid = actorUid,
        approvedByName = approvedByName.trim().ifBlank { actorName },
        markedHalfDayAttendance = effectiveMarkHalfDay,
        attendanceNote = if (effectiveMarkHalfDay) "Marked Half-Day in Attendance" else "",
        createdByUid = actorUid,
        createdByName = actorName
    )

    phase19Repo.createGatePass(
        orgId = orgId,
        gatePass = record,
        actor = actorProfile,
        actorName = actorName
    ).onSuccess { savedPass ->
        if (effectiveMarkHalfDay) {
            markStudentHalfDayForGatePass(
                schoolDocId = schoolDocId,
                session = session,
                student = student,
                studentDocId = studentDocId,
                dateIso = dateIso.trim()
            )
        }
        val halfDaySuffix = if (effectiveMarkHalfDay) " & marked Half-Day attendance" else ""
        val msg = "Issued Gate Pass ${savedPass.passNumber} for ${savedPass.studentName}$halfDaySuffix."
        emitMessage(msg)
        if (autoPreviewPdf) {
            previewGatePassA5Pdf(savedPass)
        }
        onResult(true, msg, savedPass)
    }.onFailure { err ->
        val msg = err.message ?: "Could not issue early leave gate pass."
        emitMessage(msg)
        onResult(false, msg, null)
    }
}

private fun SchoolViewModel.markStudentHalfDayForGatePass(
    schoolDocId: String,
    session: String,
    student: StudentEntity,
    studentDocId: String,
    dateIso: String
) {
    val state = uiState.value
    val className = student.className.trim()
    val normSec = student.section.trim().uppercase(Locale.US).ifBlank { "A" }
    val todayIso = Phase6DateUtils.todayIso()
    val daysBackLimit = state.schoolRecordsByDocId[schoolDocId]?.classTeacherAttendanceEditDaysBack
        ?: StudentAttendanceAccessEvaluator.DEFAULT_CLASS_TEACHER_DAYS_BACK

    val dateErr = StudentAttendanceAccessEvaluator.validateEditDateLimit(
        member = state.accessProfile,
        teacherScope = state.effectiveOwnTeacherScope,
        schoolId = schoolDocId,
        className = className,
        section = normSec,
        targetDateIso = dateIso,
        todayIso = todayIso,
        classTeacherDaysBackLimit = daysBackLimit
    )
    if (dateErr != null) return

    val docId = StudentAttendanceSheet.makeDocId(schoolDocId, session, className, normSec, dateIso)
    val existingSheet = state.studentAttendanceSheets.firstOrNull { it.id == docId }
    val schLongId = state.schoolRecordsByDocId[schoolDocId]?.stableId ?: state.activeSchool.id
    val classStudents = state.allStudents.filter { st ->
        st.schoolId == schLongId &&
            st.session == session &&
            st.isActiveStatus &&
            st.className.trim().equals(className, ignoreCase = true) &&
            st.section.trim().ifBlank { "A" }.equals(normSec, ignoreCase = true)
    }

    val updatedEntries = linkedMapOf<String, StudentAttendanceEntry>()
    if (existingSheet != null) {
        updatedEntries.putAll(existingSheet.entries)
    } else {
        for (st in classStudents) {
            updatedEntries[state.resolveStudentDocKey(st)] = StudentAttendanceEntry(
                status = StudentAttendanceStatus.PRESENT
            )
        }
    }
    updatedEntries[studentDocId] = StudentAttendanceEntry(
        status = StudentAttendanceStatus.HALF_DAY,
        reason = "Early Leave Gate Pass"
    )

    val studentsByDocId = classStudents.associateBy { st ->
        state.resolveStudentDocKey(st)
    }
    val changes = StudentAttendanceEngine.computeEditChanges(existingSheet, updatedEntries, studentsByDocId)
    val writeEditLog = StudentAttendanceEngine.shouldWritePostDayEditLog(
        sheetDateIso = dateIso,
        todayIso = todayIso,
        sheetAlreadyExisted = existingSheet != null,
        changes = changes
    )
    val sheet = StudentAttendanceSheet(
        id = docId,
        schoolId = schoolDocId,
        session = session,
        className = className,
        section = normSec,
        date = dateIso,
        entries = updatedEntries
    ).withRecomputedCounts()

    phase8Repo.saveStudentAttendanceSheet(
        orgId = orgId,
        sheet = sheet,
        existsInCloud = existingSheet != null,
        writeEditLog = writeEditLog,
        changes = changes
    )
}

fun SchoolViewModel.previewGatePassA5Pdf(
    gatePass: GatePassRecord
) {
    val state = uiState.value
    val school = state.schoolRecordsByDocId[gatePass.schoolId]?.toSchoolEntity() ?: state.activeSchool
    val request = GatePassA5PdfRequest(
        school = school,
        gatePass = gatePass
    )
    val title = "Gate Pass (A5) • ${gatePass.passNumber} (${gatePass.studentName})"
    _timetablePdfState.value = TimetablePdfPreviewState(
        isGenerating = true,
        title = title,
        statusLabel = "Generating A5 Early Leave Pass Slip…",
        isLandscape = false
    )
    viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val file = Phase19PdfGenerator.generateGatePassA5Pdf(appContext, request)
                val rendered = PdfExportHelper.renderPdfPages(file, maxPagesToPreview = 2)
                val preview = rendered.ifEmpty {
                    listOf(Phase19PdfGenerator.renderGatePassA5Bitmap(request))
                }
                val totalPages = PdfExportHelper.countPdfPages(file).takeIf { it > 0 } ?: 1
                Triple(file, totalPages, preview)
            }
        }.onSuccess { (file, totalPages, preview) ->
            _timetablePdfState.value = _timetablePdfState.value.copy(
                isGenerating = false,
                statusLabel = "Ready ($totalPages page, A5 Slip)",
                generatedFile = file,
                totalPages = totalPages,
                previewPages = preview
            )
        }.onFailure { err ->
            _timetablePdfState.value = _timetablePdfState.value.copy(
                isGenerating = false,
                error = err.message ?: "Failed to generate A5 Gate Pass Slip"
            )
        }
    }
}

fun SchoolViewModel.exportGateRegisterCsvToUri(
    destinationUri: Uri,
    passes: List<GatePassRecord>
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (state.activeOrg != null && !Phase19AccessEvaluator.canExportGateRegisterCsv(state.accessProfile, schoolDocId)) {
        emitMessage("Only the School Owner and Admins can export the Gate Register CSV.")
        return
    }
    val schoolName = state.activeSchool.name.ifBlank { "School" }
    viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            val csv = Phase19RemarksPtmAndGateEngine.buildGateRegisterCsv(
                passes = passes,
                schoolName = schoolName
            )
            PdfExportHelper.writeTextToUri(appContext, csv, destinationUri)
        }
        emitMessage(
            if (ok) "Gate Register CSV saved successfully." else "Could not save Gate Register CSV."
        )
    }
}

fun SchoolViewModel.shareGateRegisterCsv(
    passes: List<GatePassRecord>,
    startDateIso: String,
    endDateIso: String
) {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    if (state.activeOrg != null && !Phase19AccessEvaluator.canExportGateRegisterCsv(state.accessProfile, schoolDocId)) {
        emitMessage("Only the School Owner and Admins can export the Gate Register CSV.")
        return
    }
    val schoolName = state.activeSchool.name.ifBlank { "School" }
    viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            val csv = Phase19RemarksPtmAndGateEngine.buildGateRegisterCsv(
                passes = passes,
                schoolName = schoolName
            )
            val dir = File(appContext.cacheDir, "reports").apply { mkdirs() }
            val safeRange = "${startDateIso}_to_${endDateIso}".replace(Regex("[^A-Za-z0-9_-]"), "_")
            val out = File(dir, "Gate_Register_${safeRange}.csv")
            out.writeText(csv, Charsets.UTF_8)
            out
        }
        PdfExportHelper.shareFile(appContext, file, "text/csv", "Share Gate Register CSV")
    }
}

// =============================================================================
// 5. VISITOR REGISTER ACTIONS
// =============================================================================

fun SchoolViewModel.addVisitorEntry(
    schoolDocId: String,
    dateIso: String,
    visitorName: String,
    mobile: String,
    purpose: String,
    whomToMeet: String,
    timeIn: String,
    photoBase64: String = "",
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    if (schoolDocId.isBlank()) {
        val msg = "Please select a school first."
        emitMessage(msg)
        onResult(false, msg)
        return
    }
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank { "Gate Staff" } ?: "Gate Staff"
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, schoolDocId)

    phase19Repo.addVisitorEntry(
        orgId = orgId,
        schoolId = schoolDocId,
        dateIso = dateIso,
        visitorName = visitorName,
        mobile = mobile,
        purpose = purpose,
        whomToMeet = whomToMeet,
        timeIn = timeIn,
        photoBase64 = photoBase64,
        actor = actorProfile,
        actorName = actorName
    ).onSuccess { saved ->
        val msg = "Checked in visitor '${saved.visitorName}' (${saved.timeIn})."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not add visitor entry."
        emitMessage(msg)
        onResult(false, msg)
    }
}

fun SchoolViewModel.signOutVisitorEntry(
    entry: VisitorEntryRecord,
    timeOut: String = SimpleDateFormat("hh:mm a", Locale.US).format(Date()),
    onResult: (Boolean, String) -> Unit = { _, _ -> }
) {
    val state = uiState.value
    val actorUid = currentUserId.ifBlank { state.currentMember?.uid.orEmpty() }.ifBlank { "local_user" }
    val actorName = state.currentMember?.name?.ifBlank { "Gate Staff" } ?: "Gate Staff"
    val actorProfile = state.effectivePhase19ActorProfile(actorUid, entry.schoolId)

    phase19Repo.signOutVisitor(
        orgId = orgId,
        existing = entry,
        timeOut = timeOut.trim(),
        actor = actorProfile,
        actorName = actorName
    ).onSuccess { updated ->
        val msg = "Signed out visitor '${updated.visitorName}' at ${updated.timeOut}."
        emitMessage(msg)
        onResult(true, msg)
    }.onFailure { err ->
        val msg = err.message ?: "Could not sign out visitor."
        emitMessage(msg)
        onResult(false, msg)
    }
}
