package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.ClassComparisonMode
import com.example.domain.Phase17ResultAnalysisEngine
import com.example.domain.ProgressTrend
import com.example.domain.ReportCalculations
import com.example.domain.ResultAnalysisBundle
import com.example.domain.ResultAnalysisExamFilter
import com.example.domain.ResultStatus
import com.example.domain.StudentProgressReport
import com.example.domain.TieBreakRule
import com.example.domain.TopperScope
import com.example.domain.WeakFilterMode
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.util.ResultAnalysisPdfType
import java.util.Locale

private enum class ResultAnalysisTab(val en: String, val hi: String) {
    OVERVIEW("Overview", "सारांश"),
    TOPPERS("Toppers", "टॉपर्स"),
    SUBJECTS("Subjects", "विषयवार"),
    WEAK("Weak", "कमज़ोर छात्र"),
    PROGRESS("Progress", "प्रगति"),
    COMPARE("Compare", "तुलना"),
    MISSING("Missing", "अधूरे अंक")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Phase17ResultAnalysisScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val hi = uiState.phase10HindiLabels
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()

    val allowedSchools = remember(
        uiState.activeSchools,
        uiState.schoolDocIdByLongId,
        uiState.accessProfile,
        uiState.activeOrg
    ) {
        uiState.allowedSchoolsForResultAnalysis()
    }
    val activeSchool = uiState.activeSchool
    val activeSchoolDocId = uiState.activeSchoolDocId
    val sessions = remember(uiState.settings) { uiState.settings.sessionsList() }

    var selectedSession by remember(uiState.filter.session) { mutableStateOf(uiState.filter.session) }
    var selectedClass by remember(activeSchoolDocId, selectedSession) { mutableStateOf("ALL") }
    var selectedSection by remember(activeSchoolDocId, selectedSession) { mutableStateOf("ALL") }
    var selectedExam by remember { mutableStateOf(ResultAnalysisExamFilter.ALL) }
    var selectedTab by remember { mutableStateOf(ResultAnalysisTab.OVERVIEW) }
    var filtersExpanded by remember { mutableStateOf(true) }

    // Toppers state
    var topperScope by remember { mutableStateOf(TopperScope.CLASS) }
    var topNInput by remember { mutableStateOf("10") }
    var tieRule by remember { mutableStateOf(TieBreakRule.COMPETITION_INCLUDE_ALL_TIED) }

    // Weak students state
    var weakThresholdInput by remember { mutableStateOf("40") }
    var weakFilterMode by remember { mutableStateOf(WeakFilterMode.FAIL_OR_BELOW_THRESHOLD) }

    // Class comparison state
    var comparisonMode by remember { mutableStateOf(ClassComparisonMode.BY_SECTION) }

    // Student progress state
    var studentSearchQuery by remember { mutableStateOf("") }
    var selectedProgressStudentId by remember(activeSchoolDocId, selectedSession) { mutableStateOf<Long?>(null) }

    // Export dialogs
    var showPdfMenuDialog by remember { mutableStateOf(false) }
    var showCsvMenuDialog by remember { mutableStateOf(false) }

    val scopedData = remember(
        activeSchoolDocId,
        selectedSession,
        uiState.accessProfile,
        uiState.effectiveOwnTeacherScope,
        uiState.schoolClasses,
        uiState.subjects,
        uiState.allStudents,
        uiState.allMarks,
        uiState.schoolClassOrders
    ) {
        viewModel.buildScopedResultAnalysisData(selectedSession)
    }

    // Clamp class & section selections when school / teacher scope changes
    LaunchedEffect(scopedData.allowedClasses, scopedData.allowedSectionsByClass) {
        if (selectedClass != "ALL" && scopedData.allowedClasses.none { it.equals(selectedClass, ignoreCase = true) }) {
            selectedClass = if (scopedData.allowedClasses.size == 1 && scopedData.isTeacherRestricted) {
                scopedData.allowedClasses.first()
            } else {
                "ALL"
            }
        }
        if (selectedClass != "ALL") {
            val secs = scopedData.allowedSectionsByClass[selectedClass].orEmpty()
            if (selectedSection != "ALL" && secs.none { it.equals(selectedSection, ignoreCase = true) }) {
                selectedSection = "ALL"
            }
        } else {
            selectedSection = "ALL"
        }
    }

    val topN = topNInput.toIntOrNull()?.coerceIn(1, 200) ?: 10
    val weakThreshold = weakThresholdInput.toIntOrNull()?.coerceIn(1, 100) ?: 40
    val classOrder = remember(uiState.schoolClasses, uiState.schoolClassOrders, activeSchool.id, activeSchoolDocId) {
        viewModel.effectiveClassOrderForActiveSchool()
    }

    val bundle: ResultAnalysisBundle = remember(
        scopedData,
        activeSchoolDocId,
        selectedClass,
        selectedSection,
        selectedExam,
        uiState.settings.passPercent,
        topperScope,
        topN,
        tieRule,
        weakThreshold,
        weakFilterMode,
        comparisonMode,
        uiState.classAssignments,
        uiState.effectivePhase12Teachers,
        classOrder
    ) {
        Phase17ResultAnalysisEngine.computeBundle(
            scopedData = scopedData,
            schoolDocId = activeSchoolDocId,
            selectedClass = selectedClass,
            selectedSection = selectedSection,
            examFilter = selectedExam,
            passPercent = uiState.settings.passPercent,
            topperScope = topperScope,
            topN = topN,
            tieRule = tieRule,
            weakThresholdPercent = weakThreshold,
            weakFilterMode = weakFilterMode,
            comparisonMode = comparisonMode,
            classAssignments = uiState.classAssignments,
            teachers = uiState.effectivePhase12Teachers,
            classOrder = classOrder
        )
    }

    val candidateProgressStudents = remember(bundle.evaluatedStudents, studentSearchQuery) {
        val all = bundle.evaluatedStudents.map { it.student }
        val q = studentSearchQuery.trim()
        if (q.isBlank()) all
        else all.filter { st ->
            st.name.contains(q, ignoreCase = true) ||
                st.admNo.contains(q, ignoreCase = true) ||
                st.rollNo.toString() == q ||
                "${st.className}-${st.section}".contains(q, ignoreCase = true)
        }
    }

    val activeProgressStudent = remember(selectedProgressStudentId, candidateProgressStudents, bundle.evaluatedStudents) {
        val all = bundle.evaluatedStudents.map { it.student }
        all.firstOrNull { it.id == selectedProgressStudentId } ?: candidateProgressStudents.firstOrNull()
    }

    val studentProgressReport: StudentProgressReport? = remember(
        activeProgressStudent,
        scopedData,
        uiState.settings.passPercent
    ) {
        val stu = activeProgressStudent ?: return@remember null
        val marks = scopedData.scopedMarks.filter { it.studentId == stu.id }
        Phase17ResultAnalysisEngine.computeStudentProgress(
            student = stu,
            scopedData = scopedData,
            marksForStudent = marks,
            passPercent = uiState.settings.passPercent
        )
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportResultAnalysisCsvToUri(uri, selectedSession, bundle)
        }
    }

    if (pdfState.isGenerating || pdfState.generatedFile != null || pdfState.error != null) {
        TimetablePdfPreviewDialog(
            pdfState = pdfState,
            onSavePdfToUri = viewModel::saveTimetablePdfToUri,
            onSaveZipToUri = viewModel::saveTimetableZipToUri,
            onDismiss = viewModel::clearTimetablePdfPreview
        )
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Back + Title + Subtitle + Language toggle (never squeezed)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("phase17_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(hi, "रिज़ल्ट विश्लेषण", "Result Analysis"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("phase17_title")
                            )
                            Text(
                                text = "${activeSchool.name} • $selectedSession • ${selectedExam.labelEn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Phase10LanguageToggleChip(
                            isHindi = hi,
                            onToggle = { viewModel.togglePhase10HindiLabels() }
                        )
                    }

                    // Row 2: Equal-width action buttons (>= 48dp height)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showPdfMenuDialog = true },
                            enabled = uiState.canViewResultAnalysis,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("phase17_pdf_menu_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = p10Label(hi, "PDF रिपोर्ट", "PDF Report"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (uiState.canExportResultAnalysisCsv) {
                            OutlinedButton(
                                onClick = { showCsvMenuDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("phase17_csv_export_btn")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = p10Label(hi, "CSV एक्सपोर्ट", "Export CSV"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.canViewResultAnalysis) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = p10Label(
                            hi,
                            "आपके पास 'रिज़ल्ट विश्लेषण' देखने की अनुमति नहीं है। संस्था के Owner से संपर्क करें।",
                            "You do not have permission to view Result Analysis. Ask your Organisation Owner to enable this module."
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("phase17_no_access_msg")
                    )
                }
                return@Column
            }

            // Tabs
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 8.dp
            ) {
                ResultAnalysisTab.entries.forEach { tab ->
                    val label = if (tab == ResultAnalysisTab.MISSING && bundle.missingRows.isNotEmpty()) {
                        "${p10Label(hi, tab.hi, tab.en)} (${bundle.missingRows.size})"
                    } else {
                        p10Label(hi, tab.hi, tab.en)
                    }
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("phase17_tab_${tab.name.lowercase(Locale.US)}"),
                        text = {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            // Collapsible Filter Header Strip + Filter Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { filtersExpanded = !filtersExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${bundle.scopeLabel} • ${selectedExam.labelEn} • $selectedSession",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (filtersExpanded) "Collapse filters" else "Expand filters"
                        )
                    }

                    if (filtersExpanded) {
                        if (allowedSchools.size > 1) {
                            SimpleDropdownField(
                                label = p10Label(hi, "स्कूल चुनें", "School"),
                                selectedValue = activeSchool.name,
                                options = allowedSchools.map { it.name },
                                onSelected = { pickedName ->
                                    allowedSchools.firstOrNull { it.name == pickedName }?.let { sch ->
                                        viewModel.selectSchool(sch.id)
                                    }
                                },
                                testTag = "phase17_school_picker"
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                SimpleDropdownField(
                                    label = p10Label(hi, "सत्र", "Session"),
                                    selectedValue = selectedSession,
                                    options = sessions,
                                    onSelected = { selectedSession = it },
                                    testTag = "phase17_session_picker"
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                val classOptions = buildList {
                                    if (!scopedData.isTeacherRestricted || scopedData.allowedClasses.size > 1) {
                                        add(p10Label(hi, "सभी कक्षाएँ", "All Classes") to "ALL")
                                    }
                                    scopedData.allowedClasses.forEach { add(it to it) }
                                }
                                val selectedClassDisplay = classOptions.firstOrNull {
                                    it.second.equals(selectedClass, ignoreCase = true)
                                }?.first ?: selectedClass
                                SimpleDropdownField(
                                    label = p10Label(hi, "कक्षा", "Class"),
                                    selectedValue = selectedClassDisplay,
                                    options = classOptions.map { it.first },
                                    onSelected = { picked ->
                                        val code = classOptions.firstOrNull { it.first == picked }?.second ?: "ALL"
                                        selectedClass = code
                                        selectedSection = "ALL"
                                    },
                                    testTag = "phase17_class_picker"
                                )
                            }
                        }

                        if (selectedClass != "ALL") {
                            val secList = scopedData.allowedSectionsByClass[selectedClass].orEmpty()
                            if (secList.isNotEmpty()) {
                                val secOptions = buildList {
                                    if (!scopedData.isTeacherRestricted || secList.size > 1) {
                                        add(p10Label(hi, "सभी सेक्शन", "All Sections") to "ALL")
                                    }
                                    secList.forEach { add("Sec $it" to it) }
                                }
                                val selSecLabel = secOptions.firstOrNull {
                                    it.second.equals(selectedSection, ignoreCase = true)
                                }?.first ?: selectedSection
                                SimpleDropdownField(
                                    label = p10Label(hi, "सेक्शन", "Section"),
                                    selectedValue = selSecLabel,
                                    options = secOptions.map { it.first },
                                    onSelected = { picked ->
                                        selectedSection = secOptions.firstOrNull { it.first == picked }?.second ?: "ALL"
                                    },
                                    testTag = "phase17_section_picker"
                                )
                            }
                        }

                        // Exam selector chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ResultAnalysisExamFilter.entries.forEach { exam ->
                                FilterChip(
                                    selected = selectedExam == exam,
                                    onClick = { selectedExam = exam },
                                    label = {
                                        Text(
                                            text = if (hi) exam.labelHi else exam.labelEn,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("phase17_exam_${exam.key.lowercase(Locale.US)}")
                                )
                            }
                        }

                        if (scopedData.isTeacherRestricted) {
                            val scopeHint = if (scopedData.allowedClasses.isEmpty()) {
                                p10Label(
                                    hi,
                                    "आपको अभी कोई कक्षा या विषय असाइन नहीं है, इसलिए कोई डेटा नहीं दिख रहा है।",
                                    "No classes or subjects are assigned to you yet, so no results are shown."
                                )
                            } else {
                                p10Label(
                                    hi,
                                    "टीचर दायरा: केवल आपकी असाइन कक्षाओं व विषयों (${scopedData.allowedClasses.joinToString(", ")}) का विश्लेषण दिख रहा है।",
                                    "Teacher scope: Showing analysis only for your assigned classes and subjects (${scopedData.allowedClasses.joinToString(", ")})."
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = scopeHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Incomplete marks warning banner
            if (bundle.hasIncompleteMarks && selectedTab != ResultAnalysisTab.MISSING) {
                Surface(
                    color = AbsentAmberContainer,
                    border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("phase17_incomplete_marks_banner"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = AbsentAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = p10Label(
                                hi,
                                "अंक अधूरे हैं: ${bundle.missingEntries}/${bundle.totalExpectedEntries} प्रविष्टियाँ बाकी (${bundle.overview.pendingCount} छात्र बिना अंक)।",
                                "Marks incomplete: ${bundle.missingEntries}/${bundle.totalExpectedEntries} entries missing (${bundle.overview.pendingCount} student(s) pending)."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4E2D00),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { selectedTab = ResultAnalysisTab.MISSING },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(p10Label(hi, "देखें", "View Missing"), maxLines = 1)
                        }
                    }
                }
            }

            // Tab Contents
            when (selectedTab) {
                ResultAnalysisTab.OVERVIEW -> OverviewTabContent(
                    bundle = bundle,
                    hi = hi,
                    onExportPdf = {
                        viewModel.generateResultAnalysisPdfAndPreview(
                            type = ResultAnalysisPdfType.CLASS_SUMMARY,
                            session = selectedSession,
                            bundle = bundle
                        )
                    }
                )

                ResultAnalysisTab.TOPPERS -> ToppersTabContent(
                    bundle = bundle,
                    hi = hi,
                    topperScope = topperScope,
                    onScopeChange = { topperScope = it },
                    topNInput = topNInput,
                    onTopNInputChange = { topNInput = it },
                    tieRule = tieRule,
                    onTieRuleChange = { tieRule = it },
                    isTeacherRestricted = scopedData.isTeacherRestricted,
                    onExportPdf = {
                        viewModel.generateResultAnalysisPdfAndPreview(
                            type = ResultAnalysisPdfType.TOPPERS,
                            session = selectedSession,
                            bundle = bundle,
                            topperScope = topperScope,
                            topN = topN,
                            tieRule = tieRule
                        )
                    }
                )

                ResultAnalysisTab.SUBJECTS -> SubjectsTabContent(
                    bundle = bundle,
                    hi = hi,
                    onExportPdf = {
                        viewModel.generateResultAnalysisPdfAndPreview(
                            type = ResultAnalysisPdfType.SUBJECT_ANALYSIS,
                            session = selectedSession,
                            bundle = bundle
                        )
                    }
                )

                ResultAnalysisTab.WEAK -> WeakStudentsTabContent(
                    bundle = bundle,
                    hi = hi,
                    thresholdInput = weakThresholdInput,
                    onThresholdChange = { weakThresholdInput = it },
                    filterMode = weakFilterMode,
                    onFilterModeChange = { weakFilterMode = it },
                    onOpenStudentProgress = { stId ->
                        selectedProgressStudentId = stId
                        selectedTab = ResultAnalysisTab.PROGRESS
                    },
                    onExportPdf = {
                        viewModel.generateResultAnalysisPdfAndPreview(
                            type = ResultAnalysisPdfType.WEAK_STUDENTS,
                            session = selectedSession,
                            bundle = bundle,
                            weakThresholdPercent = weakThreshold,
                            weakFilterMode = weakFilterMode
                        )
                    }
                )

                ResultAnalysisTab.PROGRESS -> StudentProgressTabContent(
                    hi = hi,
                    schoolName = activeSchool.name,
                    session = selectedSession,
                    searchQuery = studentSearchQuery,
                    onSearchChange = { studentSearchQuery = it },
                    candidateStudents = candidateProgressStudents,
                    progress = studentProgressReport,
                    onSelectStudent = { selectedProgressStudentId = it },
                    onCopyMessage = { msg ->
                        clipboard.setText(AnnotatedString(msg))
                        Toast.makeText(
                            context,
                            p10Label(hi, "संदेश कॉपी हो गया", "Message copied"),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onShareMessage = { msg ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, msg)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                intent,
                                p10Label(hi, "अभिभावक संदेश शेयर करें", "Share Guardian Update")
                            )
                        )
                    },
                    onExportPdf = { rep ->
                        viewModel.generateResultAnalysisPdfAndPreview(
                            type = ResultAnalysisPdfType.STUDENT_PROGRESS,
                            session = selectedSession,
                            studentProgress = rep
                        )
                    }
                )

                ResultAnalysisTab.COMPARE -> ComparisonTabContent(
                    bundle = bundle,
                    hi = hi,
                    mode = comparisonMode,
                    onModeChange = { comparisonMode = it },
                    onExportPdf = {
                        viewModel.generateResultAnalysisPdfAndPreview(
                            type = ResultAnalysisPdfType.CLASS_SUMMARY,
                            session = selectedSession,
                            bundle = bundle
                        )
                    }
                )

                ResultAnalysisTab.MISSING -> MissingMarksTabContent(
                    bundle = bundle,
                    hi = hi
                )
            }
        }
    }

    // PDF Menu Dialog
    if (showPdfMenuDialog) {
        AlertDialog(
            onDismissRequest = { showPdfMenuDialog = false },
            title = { Text(p10Label(hi, "ऑफलाइन A4 PDF चुनें", "Choose A4 PDF Report")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfChoiceButton(
                        title = p10Label(hi, "कक्षा रिज़ल्ट सारांश PDF", "Class Result Summary PDF"),
                        onClick = {
                            showPdfMenuDialog = false
                            viewModel.generateResultAnalysisPdfAndPreview(
                                type = ResultAnalysisPdfType.CLASS_SUMMARY,
                                session = selectedSession,
                                bundle = bundle
                            )
                        }
                    )
                    PdfChoiceButton(
                        title = p10Label(hi, "टॉपर्स सूची PDF", "Topper List PDF"),
                        onClick = {
                            showPdfMenuDialog = false
                            viewModel.generateResultAnalysisPdfAndPreview(
                                type = ResultAnalysisPdfType.TOPPERS,
                                session = selectedSession,
                                bundle = bundle,
                                topperScope = topperScope,
                                topN = topN,
                                tieRule = tieRule
                            )
                        }
                    )
                    PdfChoiceButton(
                        title = p10Label(hi, "विषयवार विश्लेषण PDF", "Subject-Wise Analysis PDF"),
                        onClick = {
                            showPdfMenuDialog = false
                            viewModel.generateResultAnalysisPdfAndPreview(
                                type = ResultAnalysisPdfType.SUBJECT_ANALYSIS,
                                session = selectedSession,
                                bundle = bundle
                            )
                        }
                    )
                    PdfChoiceButton(
                        title = p10Label(hi, "कमज़ोर छात्रों की सूची PDF", "Weak Students List PDF"),
                        onClick = {
                            showPdfMenuDialog = false
                            viewModel.generateResultAnalysisPdfAndPreview(
                                type = ResultAnalysisPdfType.WEAK_STUDENTS,
                                session = selectedSession,
                                bundle = bundle,
                                weakThresholdPercent = weakThreshold,
                                weakFilterMode = weakFilterMode
                            )
                        }
                    )
                    if (studentProgressReport != null) {
                        PdfChoiceButton(
                            title = p10Label(
                                hi,
                                "छात्र प्रगति शीट: ${studentProgressReport.student.name}",
                                "Student Progress Sheet: ${studentProgressReport.student.name}"
                            ),
                            onClick = {
                                showPdfMenuDialog = false
                                viewModel.generateResultAnalysisPdfAndPreview(
                                    type = ResultAnalysisPdfType.STUDENT_PROGRESS,
                                    session = selectedSession,
                                    studentProgress = studentProgressReport
                                )
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPdfMenuDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(hi, "बंद करें", "Close"))
                }
            }
        )
    }

    // CSV Menu Dialog (Owner & Admins only)
    if (showCsvMenuDialog) {
        AlertDialog(
            onDismissRequest = { showCsvMenuDialog = false },
            title = { Text(p10Label(hi, "रिज़ल्ट विश्लेषण CSV एक्सपोर्ट", "Export Result Analysis CSV")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = p10Label(
                            hi,
                            "इसमें ओवरव्यू, ग्रेड वितरण, टॉपर्स, विषयवार विश्लेषण, कमज़ोर छात्र और तुलना तालिका शामिल होगी।",
                            "Includes Class Overview, Grade Distribution, Toppers, Subject Analysis, Weak Students, and Comparison."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showCsvMenuDialog = false
                            val safeSes = selectedSession.replace(Regex("[^A-Za-z0-9_-]"), "_")
                            saveCsvLauncher.launch("Result_Analysis_${safeSes}_${bundle.examFilter.key}.csv")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(p10Label(hi, "फ़ोन में सेव करें (.csv)", "Save CSV to Device"))
                    }
                    OutlinedButton(
                        onClick = {
                            showCsvMenuDialog = false
                            viewModel.shareResultAnalysisCsv(selectedSession, bundle)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(p10Label(hi, "CSV शेयर करें", "Share CSV"))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showCsvMenuDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(hi, "बंद करें", "Close"))
                }
            }
        )
    }
}

@Composable
private fun PdfChoiceButton(title: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SimpleDropdownField(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    testTag: String
) {
    CompactDropdownSelector(
        label = label,
        selectedValue = selectedValue,
        options = options,
        onSelect = onSelected,
        modifier = Modifier.fillMaxWidth(),
        testTag = testTag
    )
}

// =============================================================================
// TAB 1: CLASS OVERVIEW
// =============================================================================
@Composable
private fun OverviewTabContent(
    bundle: ResultAnalysisBundle,
    hi: Boolean,
    onExportPdf: () -> Unit
) {
    val ov = bundle.overview
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = p10Label(hi, "कक्षा परिणाम सारांश", "Class Overview"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = onExportPdf,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(p10Label(hi, "सारांश PDF", "Summary PDF"), maxLines = 1)
                }
            }
        }

        if (ov.totalStudents == 0) {
            item { EmptyAnalysisCard(p10Label(hi, "चयनित फ़िल्टर में कोई छात्र नहीं मिला।", "No students match the selected filters.")) }
            return@LazyColumn
        }

        // KPI Rows (2 per row so readable on 320dp)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewKpiCard(
                    title = p10Label(hi, "उपस्थित / कुल", "Appeared / Total"),
                    value = "${ov.appearedCount} / ${ov.totalStudents}",
                    subtitle = p10Label(hi, "बाकी: ${ov.pendingCount}", "Pending: ${ov.pendingCount}"),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                OverviewKpiCard(
                    title = p10Label(hi, "पास प्रतिशत", "Pass Rate"),
                    value = "${ReportCalculations.formatPercent(ov.passPercent)}%",
                    subtitle = p10Label(hi, "पास ${ov.passCount} • फेल ${ov.failCount}", "Pass ${ov.passCount} • Fail ${ov.failCount}"),
                    accent = Color(0xFF15803D),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewKpiCard(
                    title = p10Label(hi, "कक्षा औसत", "Class Average"),
                    value = "${ReportCalculations.formatPercent(ov.averagePercent)}%",
                    subtitle = p10Label(hi, "ग्रेड A1–E आधारित", "Pass rule >= 33%"),
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                OverviewKpiCard(
                    title = p10Label(hi, "पास / फेल", "Pass / Fail"),
                    value = "${ov.passCount} / ${ov.failCount}",
                    subtitle = p10Label(hi, "हर विषय >= 33%", "Each subject >= 33%"),
                    accent = if (ov.failCount > 0) FailRed else Color(0xFF15803D),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Highest & Lowest Marks Cards
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewKpiCard(
                    title = p10Label(hi, "सर्वोच्च अंक", "Highest Marks"),
                    value = if (ov.appearedCount > 0) "${ReportCalculations.formatPercent(ov.highestPercent)}%" else "—",
                    subtitle = if (ov.highestStudentName.isNotBlank()) {
                        "${ov.highestStudentName} (${ReportCalculations.formatNumber(ov.highestObtained)}/${ov.highestMax})"
                    } else p10Label(hi, "कोई अंक नहीं", "No marks yet"),
                    accent = Color(0xFF15803D),
                    modifier = Modifier.weight(1f)
                )
                OverviewKpiCard(
                    title = p10Label(hi, "न्यूनतम अंक", "Lowest Marks"),
                    value = if (ov.appearedCount > 0) "${ReportCalculations.formatPercent(ov.lowestPercent)}%" else "—",
                    subtitle = if (ov.lowestStudentName.isNotBlank()) {
                        "${ov.lowestStudentName} (${ReportCalculations.formatNumber(ov.lowestObtained)}/${ov.lowestMax})"
                    } else p10Label(hi, "कोई अंक नहीं", "No marks yet"),
                    accent = if (ov.lowestPercent < 33.0 && ov.appearedCount > 0) FailRed else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Grade Distribution Bars (A1 to E)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = p10Label(hi, "ग्रेड वितरण (A1 से E)", "Grade Distribution (A1 to E)"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ov.gradeDistribution.forEach { bucket ->
                        val fraction = (bucket.percentOfAppeared.toFloat() / 100f).coerceIn(0f, 1f)
                        val barColor = when (bucket.grade) {
                            "A1", "A2" -> Color(0xFF15803D)
                            "B1", "B2" -> MaterialTheme.colorScheme.primary
                            "C1", "C2" -> MaterialTheme.colorScheme.secondary
                            "D" -> AbsentAmber
                            else -> FailRed
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = bucket.grade,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (fraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .height(14.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                            Text(
                                text = "${bucket.count} (${ReportCalculations.formatPercent(bucket.percentOfAppeared)}%)",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(84.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewKpiCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// =============================================================================
// TAB 2: TOPPERS (SECTION / CLASS / SCHOOL + TOP N + TIE RULE)
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToppersTabContent(
    bundle: ResultAnalysisBundle,
    hi: Boolean,
    topperScope: TopperScope,
    onScopeChange: (TopperScope) -> Unit,
    topNInput: String,
    onTopNInputChange: (String) -> Unit,
    tieRule: TieBreakRule,
    onTieRuleChange: (TieBreakRule) -> Unit,
    isTeacherRestricted: Boolean,
    onExportPdf: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = p10Label(hi, "टॉपर्स सेटिंग्स", "Toppers Settings"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = onExportPdf,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(p10Label(hi, "टॉपर्स PDF", "Toppers PDF"), maxLines = 1)
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TopperScope.entries.forEach { sc ->
                            FilterChip(
                                selected = topperScope == sc,
                                onClick = { onScopeChange(sc) },
                                label = { Text(if (hi) sc.labelHi else sc.labelEn, maxLines = 1) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("phase17_topper_scope_${sc.name.lowercase(Locale.US)}")
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("3", "5", "10", "20").forEach { n ->
                            FilterChip(
                                selected = topNInput == n,
                                onClick = { onTopNInputChange(n) },
                                label = { Text("Top $n", maxLines = 1) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                        OutlinedTextField(
                            value = topNInput,
                            onValueChange = { raw -> onTopNInputChange(raw.filter { it.isDigit() }.take(3)) },
                            label = { Text("Top N", maxLines = 1) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .width(104.dp)
                                .testTag("phase17_top_n_input")
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TieBreakRule.entries.forEach { rule ->
                            FilterChip(
                                selected = tieRule == rule,
                                onClick = { onTieRuleChange(rule) },
                                label = { Text(if (hi) rule.labelHi else rule.labelEn, maxLines = 1) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("phase17_tie_rule_${rule.name.lowercase(Locale.US)}")
                            )
                        }
                    }
                    Text(
                        text = if (hi) tieRule.descriptionHi else tieRule.descriptionEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isTeacherRestricted) {
                        Text(
                            text = p10Label(
                                hi,
                                "नोट: टीचर के लिए रैंक केवल उनकी असाइन कक्षा/विषय पर आधारित है।",
                                "Note: For teachers, rankings include only your assigned classes and subjects."
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (bundle.toppers.isEmpty()) {
            item { EmptyAnalysisCard(p10Label(hi, "अभी कोई टॉपर डेटा उपलब्ध नहीं है।", "No topper marks found for this filter.")) }
        } else {
            items(bundle.toppers, key = { "${it.rank}_${it.student.id}" }) { entry ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when (entry.rank) {
                                1 -> Color(0xFFFEF3C7)
                                2 -> Color(0xFFE2E8F0)
                                3 -> Color(0xFFFED7AA)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "#${entry.rank}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = entry.student.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (entry.isTied) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = p10Label(hi, "बराबर", "TIED"),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Class ${entry.student.className}-${entry.student.section} • Roll ${entry.student.rollNo}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${ReportCalculations.formatNumber(entry.obtained)} / ${entry.maxMarks} • Grade ${entry.grade} • ${entry.resultStatus.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (entry.resultStatus == ResultStatus.PASS) Color(0xFF15803D) else FailRed
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${ReportCalculations.formatPercent(entry.percentage)}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = if (entry.rank <= 3) AbsentAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// TAB 3: SUBJECT-WISE ANALYSIS (SORTED WEAKEST FIRST)
// =============================================================================
@Composable
private fun SubjectsTabContent(
    bundle: ResultAnalysisBundle,
    hi: Boolean,
    onExportPdf: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = p10Label(hi, "विषयवार विश्लेषण", "Subject-Wise Analysis"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = p10Label(hi, "कमज़ोर विषय सबसे ऊपर दिखेंगे", "Sorted weakest subject first"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onExportPdf,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(p10Label(hi, "विषय PDF", "Subject PDF"), maxLines = 1)
                }
            }
        }

        if (bundle.subjectRows.isEmpty()) {
            item { EmptyAnalysisCard(p10Label(hi, "कोई विषय डेटा नहीं मिला।", "No subjects found for this filter.")) }
        } else {
            items(bundle.subjectRows, key = { it.subjectName }) { row ->
                val isWeak = row.appearedCount > 0 && (row.failCount > 0 || row.passPercent < 75.0)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (row.failCount > 0) FailRed.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.subjectName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (row.teacherLabel.isNotBlank()) {
                                        "${p10Label(hi, "शिक्षक", "Teacher")}: ${row.teacherLabel}"
                                    } else {
                                        p10Label(hi, "शिक्षक असाइन नहीं", "Teacher not assigned")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isWeak) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = FailRedContainer
                                ) {
                                    Text(
                                        text = p10Label(hi, "ध्यान दें", "Needs Focus"),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = FailRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStatPill(
                                label = p10Label(hi, "पास %", "Pass %"),
                                value = "${ReportCalculations.formatPercent(row.passPercent)}%",
                                color = if (row.failCount > 0) AbsentAmber else Color(0xFF15803D),
                                modifier = Modifier.weight(1f)
                            )
                            MiniStatPill(
                                label = p10Label(hi, "औसत", "Average"),
                                value = "${ReportCalculations.formatPercent(row.averagePercent)}%",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            MiniStatPill(
                                label = p10Label(hi, "फेल", "Failing"),
                                value = "${row.failCount} / ${row.appearedCount}",
                                color = if (row.failCount > 0) FailRed else Color(0xFF15803D),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "${p10Label(hi, "औसत अंक", "Avg")}: ${ReportCalculations.formatNumber(row.averageMarks)}/${row.maxMarksPerStudent} • " +
                                "${p10Label(hi, "अधिकतम", "High")}: ${ReportCalculations.formatNumber(row.highestMarks)} • " +
                                "${p10Label(hi, "न्यूनतम", "Low")}: ${ReportCalculations.formatNumber(row.lowestMarks)} • " +
                                "${p10Label(hi, "बाकी", "Pending")}: ${row.pendingCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
        }
    }
}

// =============================================================================
// TAB 4: WEAK STUDENTS
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeakStudentsTabContent(
    bundle: ResultAnalysisBundle,
    hi: Boolean,
    thresholdInput: String,
    onThresholdChange: (String) -> Unit,
    filterMode: WeakFilterMode,
    onFilterModeChange: (WeakFilterMode) -> Unit,
    onOpenStudentProgress: (Long) -> Unit,
    onExportPdf: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = p10Label(hi, "कमज़ोर छात्र फ़िल्टर (${bundle.weakStudents.size})", "Weak Students (${bundle.weakStudents.size})"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = onExportPdf,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(p10Label(hi, "सूची PDF", "List PDF"), maxLines = 1)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val cur = thresholdInput.toIntOrNull()?.coerceIn(1, 100) ?: 40
                        OutlinedButton(
                            onClick = { onThresholdChange((cur - 5).coerceAtLeast(10).toString()) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("-5%") }
                        OutlinedTextField(
                            value = thresholdInput,
                            onValueChange = { raw -> onThresholdChange(raw.filter { it.isDigit() }.take(3)) },
                            label = { Text(p10Label(hi, "न्यूनतम %", "Threshold %"), maxLines = 1) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("phase17_weak_threshold_input")
                        )
                        OutlinedButton(
                            onClick = { onThresholdChange((cur + 5).coerceAtMost(100).toString()) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("+5%") }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WeakFilterMode.entries.forEach { mode ->
                            FilterChip(
                                selected = filterMode == mode,
                                onClick = { onFilterModeChange(mode) },
                                label = { Text(if (hi) mode.labelHi else mode.labelEn, maxLines = 1) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
            }
        }

        if (bundle.weakStudents.isEmpty()) {
            item {
                EmptyAnalysisCard(
                    p10Label(hi, "इस मानदंड के अंतर्गत कोई कमज़ोर छात्र नहीं है।", "No students match this weak-student criteria.")
                )
            }
        } else {
            items(bundle.weakStudents, key = { it.student.id }) { row ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (row.failedSubjects.isNotEmpty()) FailRed.copy(alpha = 0.45f) else AbsentAmber.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStudentProgress(row.student.id) }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.student.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Class ${row.student.className}-${row.student.section} • Roll ${row.student.rollNo}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${ReportCalculations.formatPercent(row.percentage)}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.resultStatus == ResultStatus.FAIL) FailRed else AbsentAmber
                                )
                                Text(
                                    text = "${ReportCalculations.formatNumber(row.obtained)}/${row.maxMarks} • ${row.grade}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (row.failedSubjects.isNotEmpty()) {
                            Text(
                                text = "${p10Label(hi, "फेल विषय", "Failed subjects")}: ${row.failedSubjects.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = FailRed
                            )
                        }
                        if (row.lowScoreSubjects.isNotEmpty()) {
                            Text(
                                text = "${p10Label(hi, "कम अंक विषय", "Low score subjects")}: ${row.lowScoreSubjects.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// TAB 5: STUDENT PROGRESS & USER-INITIATED GUARDIAN MESSAGE
// =============================================================================
@Composable
private fun StudentProgressTabContent(
    hi: Boolean,
    schoolName: String,
    session: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    candidateStudents: List<com.example.data.StudentEntity>,
    progress: StudentProgressReport?,
    onSelectStudent: (Long) -> Unit,
    onCopyMessage: (String) -> Unit,
    onShareMessage: (String) -> Unit,
    onExportPdf: (StudentProgressReport) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        label = { Text(p10Label(hi, "छात्र खोजें (नाम, रोल, प्रवेश सं.)", "Search student (name, roll, adm no.)"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (candidateStudents.isNotEmpty()) {
                        val options = candidateStudents.map { "${it.name} (${it.className}-${it.section} #${it.rollNo})" }
                        val currentIdx = candidateStudents.indexOfFirst { it.id == progress?.student?.id }.coerceAtLeast(0)
                        SimpleDropdownField(
                            label = p10Label(hi, "छात्र चुनें", "Select Student"),
                            selectedValue = options.getOrElse(currentIdx) { "" },
                            options = options,
                            onSelected = { picked ->
                                val idx = options.indexOf(picked)
                                if (idx in candidateStudents.indices) {
                                    onSelectStudent(candidateStudents[idx].id)
                                }
                            },
                            testTag = "phase17_student_progress_picker"
                        )
                    }
                }
            }
        }

        if (progress == null) {
            item { EmptyAnalysisCard(p10Label(hi, "चयनित फ़िल्टर में कोई छात्र नहीं मिला।", "No student available for the current filter.")) }
            return@LazyColumn
        }

        // Student Summary + Trend + PDF button
        item {
            val st = progress.student
            Card(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = st.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Class ${st.className}-${st.section} • Roll ${st.rollNo} • Adm ${st.admNo.ifBlank { "—" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TrendBadge(trend = progress.overallTrend, hi = hi)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            ResultAnalysisExamFilter.UT1,
                            ResultAnalysisExamFilter.HALF_YEARLY,
                            ResultAnalysisExamFilter.UT2,
                            ResultAnalysisExamFilter.YEARLY
                        ).forEach { exam ->
                            val sum = progress.examSummaries[exam]
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = when (exam) {
                                            ResultAnalysisExamFilter.HALF_YEARLY -> "H.Y."
                                            else -> exam.labelEn
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (sum != null && sum.hasAnyMarks) "${ReportCalculations.formatPercent(sum.percentage)}%" else "—",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sum?.resultStatus == ResultStatus.FAIL) FailRed else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (sum != null && sum.hasAnyMarks) sum.grade else "—",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onExportPdf(progress) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("phase17_student_progress_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(p10Label(hi, "छात्र प्रगति शीट PDF", "Student Progress PDF"), maxLines = 1)
                    }
                }
            }
        }

        // Subject-by-subject progression cards
        items(progress.subjectRows, key = { it.subjectName }) { row ->
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = row.subjectName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TrendBadge(trend = row.trend, hi = hi)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExamScoreMiniBox("UT-I", row.ut1Obtained, row.ut1Max, row.ut1Percent, Modifier.weight(1f))
                        ExamScoreMiniBox("H.Y.", row.halfYearlyObtained, row.halfYearlyMax, row.halfYearlyPercent, Modifier.weight(1f))
                        ExamScoreMiniBox("UT-II", row.ut2Obtained, row.ut2Max, row.ut2Percent, Modifier.weight(1f))
                        ExamScoreMiniBox("Year", row.yearlyObtained, row.yearlyMax, row.yearlyPercent, Modifier.weight(1f))
                    }
                }
            }
        }

        // User-initiated Guardian Progress Message Card (NEVER automatic)
        item {
            val guardianMessage = remember(schoolName, session, progress, hi) {
                Phase17ResultAnalysisEngine.buildGuardianProgressMessage(
                    schoolName = schoolName,
                    session = session,
                    progress = progress,
                    isHindi = hi
                )
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = p10Label(hi, "अभिभावक को प्रगति संदेश (केवल आपके टैप पर)", "Guardian Progress Message (Manual Share Only)"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = guardianMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onCopyMessage(guardianMessage) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("phase17_copy_guardian_msg_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(p10Label(hi, "कॉपी करें", "Copy"), maxLines = 1)
                        }
                        Button(
                            onClick = { onShareMessage(guardianMessage) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("phase17_share_guardian_msg_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(p10Label(hi, "शेयर / WhatsApp", "Share / WhatsApp"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamScoreMiniBox(
    label: String,
    obtained: Double?,
    max: Int,
    percent: Double?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (obtained != null) "${ReportCalculations.formatNumber(obtained)}/$max" else "—",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = if (percent != null) "${ReportCalculations.formatPercent(percent)}%" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (percent != null && percent < 33.0) FailRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrendBadge(trend: ProgressTrend, hi: Boolean) {
    val (bg, fg, icon) = when (trend) {
        ProgressTrend.UP -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), Icons.Default.TrendingUp)
        ProgressTrend.DOWN -> Triple(FailRedContainer, FailRed, Icons.Default.TrendingDown)
        ProgressTrend.STEADY -> Triple(Color(0xFFE0F2FE), Color(0xFF0369A1), Icons.Default.TrendingFlat)
        ProgressTrend.SINGLE -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Assessment)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
            Text(
                text = if (hi) trend.labelHi else trend.labelEn,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fg
            )
        }
    }
}

// =============================================================================
// TAB 6: CLASS / SECTION COMPARISON
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComparisonTabContent(
    bundle: ResultAnalysisBundle,
    hi: Boolean,
    mode: ClassComparisonMode,
    onModeChange: (ClassComparisonMode) -> Unit,
    onExportPdf: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ClassComparisonMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { onModeChange(m) },
                            label = { Text(if (hi) m.labelHi else m.labelEn, maxLines = 1) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                OutlinedButton(
                    onClick = onExportPdf,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF", maxLines = 1)
                }
            }
        }

        if (bundle.comparisonRows.isEmpty()) {
            item { EmptyAnalysisCard(p10Label(hi, "तुलना के लिए कोई डेटा नहीं है।", "No groups available to compare.")) }
        } else {
            items(bundle.comparisonRows, key = { it.groupKey }) { row ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = row.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${row.appearedCount}/${row.totalStudents} • Pass ${row.passCount}, Fail ${row.failCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ComparisonBarRow(
                            label = p10Label(hi, "पास %", "Pass %"),
                            percent = row.passPercent,
                            barColor = Color(0xFF15803D)
                        )
                        ComparisonBarRow(
                            label = p10Label(hi, "औसत %", "Avg %"),
                            percent = row.averagePercent,
                            barColor = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "${p10Label(hi, "सर्वोच्च", "High")}: ${ReportCalculations.formatPercent(row.highestPercent)}%  •  " +
                                "${p10Label(hi, "न्यूनतम", "Low")}: ${ReportCalculations.formatPercent(row.lowestPercent)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonBarRow(label: String, percent: Double, barColor: Color) {
    val fraction = (percent.toFloat() / 100f).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(52.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(barColor)
                )
            }
        }
        Text(
            text = "${ReportCalculations.formatPercent(percent)}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(52.dp)
        )
    }
}

// =============================================================================
// TAB 7: MISSING MARKS
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingMarksTabContent(
    bundle: ResultAnalysisBundle,
    hi: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (bundle.hasIncompleteMarks) AbsentAmberContainer else Color(0xFFDCFCE7)
                ),
                border = BorderStroke(
                    1.dp,
                    if (bundle.hasIncompleteMarks) AbsentAmber.copy(alpha = 0.45f) else Color(0xFF15803D).copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (bundle.hasIncompleteMarks) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (bundle.hasIncompleteMarks) AbsentAmber else Color(0xFF15803D)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (bundle.hasIncompleteMarks) {
                                p10Label(
                                    hi,
                                    "${bundle.missingEntries} अंक प्रविष्टियाँ अधूरी हैं (${bundle.enteredEntries}/${bundle.totalExpectedEntries} दर्ज)",
                                    "${bundle.missingEntries} mark entries missing (${bundle.enteredEntries}/${bundle.totalExpectedEntries} entered)"
                                )
                            } else {
                                p10Label(hi, "इस फ़िल्टर के सभी अंक पूरे हैं!", "All marks for this filter are complete!")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = p10Label(
                                hi,
                                "${bundle.missingRows.size} विषय-प्रविष्टि पंक्तियाँ बाकी हैं",
                                "${bundle.missingRows.size} student-subject item(s) have missing components"
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        items(
            bundle.missingRows.take(200),
            key = { "${it.student.id}_${it.subjectName}" }
        ) { row ->
            Card(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${row.student.name} (${row.student.className}-${row.student.section} #${row.student.rollNo})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = row.subjectName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${row.missingCount}/${row.expectedCount} missing",
                            style = MaterialTheme.typography.labelSmall,
                            color = FailRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.missingComponentLabels.forEach { label ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = FailRedContainer
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FailRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAnalysisCard(message: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Analytics,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
