package com.example.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.domain.Phase17ResultAnalysisEngine
import com.example.domain.ResultAnalysisAccessEvaluator
import com.example.domain.ResultAnalysisBundle
import com.example.domain.ResultAnalysisScopedData
import com.example.domain.StudentProgressReport
import com.example.domain.TieBreakRule
import com.example.domain.TopperScope
import com.example.domain.WeakFilterMode
import com.example.util.PdfExportHelper
import com.example.util.Phase17PdfGenerator
import com.example.util.ResultAnalysisPdfRequest
import com.example.util.ResultAnalysisPdfType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// =============================================================================
// PHASE 17 VIEWMODEL EXTENSIONS: RESULT ANALYSIS (READ-ONLY)
// =============================================================================

fun SchoolViewModel.buildScopedResultAnalysisData(
    sessionOverride: String? = null
): ResultAnalysisScopedData {
    val state = uiState.value
    val schoolDocId = state.activeSchoolDocId
    val schoolStableId = state.activeSchool.id
    val session = sessionOverride ?: state.filter.session
    val orderedNames = effectiveClassOrderForActiveSchool()
    val schoolClasses = state.classesForSchool(schoolStableId)

    val classList = if (orderedNames.isNotEmpty()) {
        val fromClasses = schoolClasses.map { it.name }
        (orderedNames.filter { o -> fromClasses.any { it.equals(o, ignoreCase = true) } } +
            fromClasses.filter { c -> orderedNames.none { it.equals(c, ignoreCase = true) } })
            .distinct()
    } else {
        schoolClasses.map { it.name }
    }

    val subjectsByClass = linkedMapOf<String, List<String>>()
    for (cls in schoolClasses) {
        val subs = state.subjects
            .filter { it.classGroupId == cls.classGroupId }
            .sortedBy { it.sortOrder }
            .map { it.name }
        subjectsByClass[cls.name] = subs
    }

    return ResultAnalysisAccessEvaluator.scopeDataForMember(
        member = state.accessProfile,
        teacherScope = state.effectiveOwnTeacherScope,
        schoolDocId = schoolDocId,
        session = session,
        schoolStableId = schoolStableId,
        allClasses = classList,
        subjectsByClass = subjectsByClass,
        allStudents = state.allStudents,
        allMarks = state.allMarks
    )
}

fun SchoolViewModel.generateResultAnalysisPdfAndPreview(
    type: ResultAnalysisPdfType,
    session: String,
    bundle: ResultAnalysisBundle? = null,
    topperScope: TopperScope = TopperScope.CLASS,
    topN: Int = 10,
    tieRule: TieBreakRule = TieBreakRule.COMPETITION_INCLUDE_ALL_TIED,
    weakThresholdPercent: Int = 40,
    weakFilterMode: WeakFilterMode = WeakFilterMode.FAIL_OR_BELOW_THRESHOLD,
    studentProgress: StudentProgressReport? = null
) {
    val state = uiState.value
    if (!state.canViewResultAnalysis) {
        emitMessage("You do not have access to Result Analysis.")
        return
    }
    val school = state.activeSchoolCloudRecord?.toSchoolEntity() ?: state.activeSchool
    val actorName = state.currentMember?.name?.ifBlank { state.currentMember?.email.orEmpty() }.orEmpty()
    val request = ResultAnalysisPdfRequest(
        type = type,
        school = school,
        session = session,
        bundle = bundle,
        topperScope = topperScope,
        topN = topN,
        tieRule = tieRule,
        weakThresholdPercent = weakThresholdPercent,
        weakFilterMode = weakFilterMode,
        studentProgress = studentProgress,
        generatedByName = actorName
    )
    val titleSuffix = when (type) {
        ResultAnalysisPdfType.STUDENT_PROGRESS -> studentProgress?.student?.name ?: "Student"
        else -> bundle?.scopeLabel ?: session
    }
    launchPhase9PdfJob(
        title = "${type.titleEn} • $titleSuffix",
        sizeLabel = "A4 Portrait"
    ) {
        Phase17PdfGenerator.generatePdf(appContext, request)
    }
}

fun SchoolViewModel.exportResultAnalysisCsvToUri(
    destinationUri: Uri,
    session: String,
    bundle: ResultAnalysisBundle
) {
    val state = uiState.value
    if (!ResultAnalysisAccessEvaluator.canExportCsv(state.accessProfile)) {
        emitMessage("Only Owner and Admins can export Result Analysis CSV.")
        return
    }
    val schoolName = state.activeSchool.name.ifBlank { "School" }
    viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            val csv = Phase17ResultAnalysisEngine.buildAnalysisCsv(schoolName, session, bundle)
            PdfExportHelper.writeTextToUri(appContext, csv, destinationUri)
        }
        emitMessage(
            if (ok) "Result Analysis CSV saved successfully." else "Could not save CSV file."
        )
    }
}

fun SchoolViewModel.shareResultAnalysisCsv(
    session: String,
    bundle: ResultAnalysisBundle
) {
    val state = uiState.value
    if (!ResultAnalysisAccessEvaluator.canExportCsv(state.accessProfile)) {
        emitMessage("Only Owner and Admins can export Result Analysis CSV.")
        return
    }
    val schoolName = state.activeSchool.name.ifBlank { "School" }
    viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            val csv = Phase17ResultAnalysisEngine.buildAnalysisCsv(schoolName, session, bundle)
            val dir = File(appContext.cacheDir, "reports").apply { mkdirs() }
            val safeSes = session.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val out = File(dir, "Result_Analysis_${safeSes}_${bundle.examFilter.key}.csv")
            out.writeText(csv, Charsets.UTF_8)
            out
        }
        PdfExportHelper.shareFile(appContext, file, "text/csv", "Share Result Analysis CSV")
    }
}
