package com.example.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CalendarHoliday
import com.example.domain.PromotionDecision
import com.example.domain.PromotionEngine
import com.example.domain.PromotionRecord
import com.example.domain.RollAssignmentMode
import com.example.domain.SessionCopyOptions
import com.example.domain.SessionTransitionEngine
import com.example.domain.SessionTransitionRecord
import com.example.domain.SessionTransitionStatus
import com.example.domain.StudentPromotionDraft
import com.example.util.PdfExportHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Phase14SessionTab(val hi: String, val en: String) {
    WIZARD("नया सत्र शुरू करें", "Start New Session"),
    REGISTER("प्रमोशन रजिस्टर", "Promotion Register"),
    CLASS_ORDER("कक्षा क्रम", "Class Order"),
    ALUMNI("पूर्व छात्र / इतिहास", "Alumni & History")
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Phase14SessionPromotionScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    val hi = uiState.phase10HindiLabels
    val context = LocalContext.current
    val canEdit = uiState.canEditSessionPromotion
    val canUndo = uiState.canUndoSessionStart
    val activeSchool = uiState.activeSchool
    val schoolDocId = uiState.activeSchoolDocId
    val fromSession = uiState.filter.session

    var selectedTab by remember { mutableStateOf(Phase14SessionTab.WIZARD) }
    var wizardStep by remember { mutableIntStateOf(1) } // 1..4

    val suggested = remember(fromSession) { SessionTransitionEngine.suggestNextSession(fromSession) }
    var newSessionName by remember(fromSession) { mutableStateOf(suggested.sessionName) }
    var startDateIso by remember(fromSession) { mutableStateOf(suggested.startDateIso) }
    var endDateIso by remember(fromSession) { mutableStateOf(suggested.endDateIso) }
    var copyOptions by remember { mutableStateOf(SessionCopyOptions()) }
    var rollMode by remember { mutableStateOf(RollAssignmentMode.KEEP_PREVIOUS_ROLL) }

    val effectiveClassOrder = remember(uiState.schoolClasses, uiState.schoolClassOrders, activeSchool.id, schoolDocId) {
        viewModel.effectiveClassOrderForActiveSchool()
    }
    var editableClassOrder by remember(effectiveClassOrder) { mutableStateOf(effectiveClassOrder) }

    // Editable holidays copied from old calendar
    val oldCalendar = remember(uiState.effectivePhase14SchoolCalendars, schoolDocId, fromSession) {
        uiState.effectivePhase14SchoolCalendars.firstOrNull {
            it.schoolId == schoolDocId && it.session.equals(fromSession, ignoreCase = true)
        }
    }
    var editableHolidays by remember(oldCalendar, newSessionName) {
        val shifted = SessionTransitionEngine.copySchoolCalendarForNewSession(
            oldCalendar = oldCalendar,
            schoolId = schoolDocId,
            toSession = newSessionName,
            startDateIso = startDateIso,
            endDateIso = endDateIso
        ).holidays
        mutableStateOf(shifted)
    }

    var drafts by remember { mutableStateOf<List<StudentPromotionDraft>>(emptyList()) }
    var selectedReviewClass by remember { mutableStateOf("ALL") }
    var graceDialogDraft by remember { mutableStateOf<StudentPromotionDraft?>(null) }
    var rollDialogDraft by remember { mutableStateOf<StudentPromotionDraft?>(null) }
    var confirmApplyDialogOpen by remember { mutableStateOf(false) }
    var appliedSummaryRecord by remember { mutableStateOf<SessionTransitionRecord?>(null) }
    var undoDialogTransition by remember { mutableStateOf<SessionTransitionRecord?>(null) }
    var bulkExceptionsBanner by remember { mutableStateOf<String?>(null) }
    var alumniQuery by remember { mutableStateOf("") }

    // Rebuild drafts when entering step 3 or when source session/students change
    LaunchedEffect(schoolDocId, fromSession, uiState.allStudents.size, uiState.allMarks.size, effectiveClassOrder) {
        if (schoolDocId.isNotBlank()) {
            drafts = viewModel.buildPromotionDraftsForActiveSchool(fromSession, effectiveClassOrder)
        }
    }

    val schoolTransitions = remember(uiState.sessionTransitions, schoolDocId) {
        uiState.sessionTransitions.filter { it.schoolId == schoolDocId }
    }
    val schoolPromotions = remember(uiState.promotionRecords, schoolDocId) {
        uiState.promotionRecords.filter { it.schoolId == schoolDocId }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Row 1: Back + Title + Subtitle (full width, never squeezed)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("phase14_session_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(hi, "नया सत्र और प्रमोशन", "New Session & Promotion"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("phase14_session_title")
                            )
                            Text(
                                text = "${activeSchool.name} • ${p10Label(hi, "वर्तमान सत्र", "Session")}: $fromSession",
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
                    // Row 2: Session Switcher chips (past sessions stay viewable!)
                    val sessions = uiState.settings.sessionsList()
                    if (sessions.size > 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            sessions.forEach { ses ->
                                FilterChip(
                                    selected = ses.equals(fromSession, ignoreCase = true),
                                    onClick = { viewModel.selectSession(ses) },
                                    label = {
                                        Text(
                                            text = if (ses.equals(uiState.settings.currentSession, ignoreCase = true)) {
                                                "$ses (${p10Label(hi, "सक्रिय", "Current")})"
                                            } else {
                                                "$ses (${p10Label(hi, "पुराना", "Past")})"
                                            },
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
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
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 8.dp
            ) {
                Phase14SessionTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("phase14_tab_${tab.name.lowercase(Locale.US)}"),
                        text = {
                            Text(
                                text = p10Label(hi, tab.hi, tab.en),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            if (!uiState.canViewSessionPromotion) {
                BoxNoAccessPhase14(
                    message = p10Label(
                        hi,
                        "आपके पास 'सत्र और प्रमोशन' देखने की अनुमति नहीं है। संस्था के स्वामी (Owner) से संपर्क करें।",
                        "You do not have access to Session and Promotion. Ask the Organisation Owner to grant access."
                    )
                )
                return@Column
            }

            when (selectedTab) {
                Phase14SessionTab.WIZARD -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Incomplete IN_PROGRESS banner (Resume or Rollback)
                        val inProgress = schoolTransitions.firstOrNull { it.status == SessionTransitionStatus.IN_PROGRESS }
                        if (inProgress != null && canEdit) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = p10Label(
                                                hi,
                                                "अधूरा सत्र प्रारंभ मिला: ${inProgress.fromSession} → ${inProgress.toSession} (${inProgress.completedBatches}/${inProgress.totalBatches} बैच)",
                                                "Incomplete session start found: ${inProgress.fromSession} → ${inProgress.toSession} (${inProgress.completedBatches}/${inProgress.totalBatches} batches)"
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                onClick = {
                                                    viewModel.applyNewSessionAndPromotion(
                                                        fromSession = inProgress.fromSession,
                                                        toSession = inProgress.toSession,
                                                        startDateIso = inProgress.startDateIso,
                                                        endDateIso = inProgress.endDateIso,
                                                        copyOptions = inProgress.copyOptions,
                                                        drafts = drafts,
                                                        editedHolidays = editableHolidays,
                                                        startFromBatchIndex = inProgress.completedBatches
                                                    ) { res -> res.onSuccess { appliedSummaryRecord = it } }
                                                },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                            ) {
                                                Text(p10Label(hi, "जारी रखें", "Resume"))
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.rollbackIncompleteTransition(inProgress) },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                            ) {
                                                Text(p10Label(hi, "रोलबैक करें", "Roll Back"))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Applied summary card (after confirmation)
                        val summaryRec = appliedSummaryRecord
                        if (summaryRec != null) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = p10Label(
                                                    hi,
                                                    "नया सत्र ${summaryRec.toSession} सफलतापूर्वक शुरू हो गया!",
                                                    "New session ${summaryRec.toSession} started successfully!"
                                                ),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "Promoted: ${summaryRec.promotedCount}  •  Grace: ${summaryRec.gracePromotedCount}  •  Detained: ${summaryRec.detainedCount}  •  Passed Out: ${summaryRec.passedOutCount}  •  Left: ${summaryRec.leftCount}"
                                        )
                                        Text(
                                            text = p10Label(
                                                hi,
                                                "पिछले सत्र का बकाया आगे बढ़ाया गया: ₹${summaryRec.totalDuesCarriedForwardRs}",
                                                "Previous session dues carried forward: Rs.${summaryRec.totalDuesCarriedForwardRs}"
                                            ),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        OutlinedButton(
                                            onClick = { selectedTab = Phase14SessionTab.REGISTER },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(p10Label(hi, "प्रमोशन रजिस्टर PDF देखें", "Open Promotion Register PDF"))
                                        }
                                    }
                                }
                            }
                        }

                        // Owner 7-day Undo card for latest completed transition
                        val latestCompleted = schoolTransitions.firstOrNull { it.status == SessionTransitionStatus.COMPLETED }
                        if (latestCompleted != null && canUndo) {
                            val undoCheck = viewModel.evaluateUndoForTransition(latestCompleted)
                            if (undoCheck.allowed) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = p10Label(
                                                    hi,
                                                    "सत्र ${latestCompleted.toSession} वापस लें (${undoCheck.daysRemaining} दिन शेष — केवल Owner)",
                                                    "Undo session ${latestCompleted.toSession} start (${undoCheck.daysRemaining}d left — Owner only)"
                                                ),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = p10Label(hi, undoCheck.reasonHi, undoCheck.reasonEn),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            OutlinedButton(
                                                onClick = { undoDialogTransition = latestCompleted },
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("phase14_undo_session_btn")
                                            ) {
                                                Icon(Icons.Default.Undo, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text(p10Label(hi, "इस सत्र प्रारंभ को वापस लें", "Undo this session start"))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Step Progress Indicator (1 -> 4)
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    1 to p10Label(hi, "1. सत्र व तिथि", "1. Dates"),
                                    2 to p10Label(hi, "2. कॉपी चुनें", "2. Copy Setup"),
                                    3 to p10Label(hi, "3. प्रमोशन", "3. Promotion"),
                                    4 to p10Label(hi, "4. पुष्टि करें", "4. Confirm")
                                ).forEach { (stepNo, label) ->
                                    FilterChip(
                                        selected = wizardStep == stepNo,
                                        onClick = { wizardStep = stepNo },
                                        label = { Text(label) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("phase14_wizard_step_$stepNo")
                                    )
                                }
                            }
                        }

                        // Step Contents
                        when (wizardStep) {
                            1 -> item {
                                Card {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = p10Label(hi, "चरण 1: नया सत्र (अप्रैल से मार्च)", "Step 1: Choose New Session (April to March)"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = p10Label(
                                                hi,
                                                "पुराने सत्र ($fromSession) के सभी अंक, उपस्थिति, शुल्क और रसीदें पूरी तरह सुरक्षित और देखने योग्य रहेंगी।",
                                                "All marks, attendance, fees, and receipts from $fromSession remain untouched and fully viewable."
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = newSessionName,
                                            onValueChange = { newSessionName = it },
                                            label = { Text(p10Label(hi, "नया सत्र नाम (जैसे 2027-2028)", "New Session Name (e.g. 2027-2028)")) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("phase14_new_session_input")
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(
                                                value = startDateIso,
                                                onValueChange = { startDateIso = it },
                                                label = { Text(p10Label(hi, "प्रारंभ तिथि", "Start Date")) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = endDateIso,
                                                onValueChange = { endDateIso = it },
                                                label = { Text(p10Label(hi, "समाप्ति तिथि", "End Date")) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        val err = SessionTransitionEngine.validateSessionNameAndDates(fromSession, newSessionName, startDateIso, endDateIso)
                                        if (err != null) {
                                            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Button(
                                            onClick = { if (err == null) wizardStep = 2 },
                                            enabled = err == null,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        ) {
                                            Text(p10Label(hi, "आगे बढ़ें: कॉपी विकल्प", "Next: Copy Setup Options"))
                                        }
                                    }
                                }
                            }

                            2 -> item {
                                Card {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = p10Label(hi, "चरण 2: पिछले सत्र से क्या कॉपी करें?", "Step 2: Copy Structure From $fromSession"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        CopyCheckboxRow(
                                            checked = copyOptions.copyClassAndSections,
                                            onCheckedChange = { copyOptions = copyOptions.copy(copyClassAndSections = it) },
                                            label = p10Label(hi, "कक्षा और सेक्शन संरचना", "Class & section structure")
                                        )
                                        CopyCheckboxRow(
                                            checked = copyOptions.copySubjectsPerClass,
                                            onCheckedChange = { copyOptions = copyOptions.copy(copySubjectsPerClass = it) },
                                            label = p10Label(hi, "प्रति कक्षा विषय सूची", "Subjects per class")
                                        )
                                        CopyCheckboxRow(
                                            checked = copyOptions.copyFeeStructure,
                                            onCheckedChange = { copyOptions = copyOptions.copy(copyFeeStructure = it) },
                                            label = p10Label(hi, "फीस संरचना (ट्यूशन, परीक्षा शुल्क, वैकल्पिक मदें)", "Fee structure (tuition, exam fees, optional items)")
                                        )
                                        CopyCheckboxRow(
                                            checked = copyOptions.copyPeriodAndTimetableSettings,
                                            onCheckedChange = { copyOptions = copyOptions.copy(copyPeriodAndTimetableSettings = it) },
                                            label = p10Label(hi, "पीरियड संरचना और टाइमटेबल सेटिंग्स", "Period structure & timetable settings")
                                        )
                                        CopyCheckboxRow(
                                            checked = copyOptions.copySchoolCalendar,
                                            onCheckedChange = { copyOptions = copyOptions.copy(copySchoolCalendar = it) },
                                            label = p10Label(hi, "स्कूल कैलेंडर टेम्पलेट (${editableHolidays.size} छुट्टियाँ संपादन योग्य)", "School calendar template (${editableHolidays.size} holidays editable)")
                                        )
                                        CopyCheckboxRow(
                                            checked = copyOptions.copyTransportRoutes,
                                            onCheckedChange = { copyOptions = copyOptions.copy(copyTransportRoutes = it) },
                                            label = p10Label(hi, "वाहन रूट और किराया", "Transport routes")
                                        )
                                        CopyCheckboxRow(
                                            checked = copyOptions.copyStaffList,
                                            onCheckedChange = { copyOptions = copyOptions.copy(copyStaffList = it) },
                                            label = p10Label(hi, "शिक्षक व स्टाफ सूची", "Staff & teacher list")
                                        )

                                        if (copyOptions.copySchoolCalendar && editableHolidays.isNotEmpty()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            Text(
                                                text = p10Label(hi, "नए सत्र की छुट्टियाँ (हटाने के लिए X दबाएँ):", "New session holidays (tap X to remove):"),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                editableHolidays.take(12).forEach { h ->
                                                    AssistChip(
                                                        onClick = { editableHolidays = editableHolidays.filterNot { it.id == h.id } },
                                                        label = { Text("${h.name} (${h.startDate})", maxLines = 1) },
                                                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove holiday", modifier = Modifier.size(16.dp)) }
                                                    )
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { wizardStep = 1 },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                            ) { Text(p10Label(hi, "पीछे", "Back")) }
                                            Button(
                                                onClick = { wizardStep = 3 },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                            ) { Text(p10Label(hi, "आगे: प्रमोशन", "Next: Promotion")) }
                                        }
                                    }
                                }
                            }

                            3 -> {
                                item {
                                    Card {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = p10Label(hi, "चरण 3: कक्षावार प्रमोशन समीक्षा", "Step 3: Review Promotion Per Class"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = p10Label(
                                                    hi,
                                                    "रिपोर्ट कार्ड के समान नियम (हर विषय ≥ ${uiState.settings.passPercent}%) से निर्णय तैयार है।",
                                                    "Proposed decisions use the exact report card pass rule (>= ${uiState.settings.passPercent}% in each subject)."
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            // Class filter chips
                                            val classesPresent = remember(drafts) { drafts.map { it.student.className }.distinct() }
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                FilterChip(
                                                    selected = selectedReviewClass == "ALL",
                                                    onClick = { selectedReviewClass = "ALL" },
                                                    label = { Text("${p10Label(hi, "सभी", "All")} (${drafts.size})") },
                                                    modifier = Modifier.heightIn(min = 48.dp)
                                                )
                                                classesPresent.forEach { cls ->
                                                    val cnt = drafts.count { it.student.className == cls }
                                                    FilterChip(
                                                        selected = selectedReviewClass == cls,
                                                        onClick = { selectedReviewClass = cls },
                                                        label = { Text("$cls ($cnt)") },
                                                        modifier = Modifier.heightIn(min = 48.dp)
                                                    )
                                                }
                                            }
                                            // Roll assignment order chips
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                RollAssignmentMode.entries.forEach { mode ->
                                                    FilterChip(
                                                        selected = rollMode == mode,
                                                        onClick = {
                                                            rollMode = mode
                                                            drafts = PromotionEngine.assignSectionsAndRollNumbers(drafts, mode)
                                                        },
                                                        label = { Text(p10Label(hi, mode.labelHi, mode.labelEn), maxLines = 1) },
                                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                                    )
                                                }
                                            }
                                            // Bulk Promote All Passed button
                                            Button(
                                                onClick = {
                                                    val res = PromotionEngine.applyBulkPromoteAllPassed(drafts, effectiveClassOrder)
                                                    drafts = PromotionEngine.assignSectionsAndRollNumbers(res.updatedDrafts, rollMode)
                                                    bulkExceptionsBanner = if (res.exceptions.isNotEmpty()) {
                                                        p10Label(
                                                            hi,
                                                            "${res.promotedCount + res.passedOutCount} उत्तीर्ण छात्र तैयार। ${res.exceptions.size} अपवाद (जाँच/रोके गए/छोड़े गए) ऊपर दिखाए गए हैं।",
                                                            "${res.promotedCount + res.passedOutCount} passed students confirmed. ${res.exceptions.size} exception(s) listed first below."
                                                        )
                                                    } else {
                                                        p10Label(
                                                            hi,
                                                            "सभी ${res.promotedCount + res.passedOutCount} उत्तीर्ण छात्र प्रमोट करने के लिए तैयार हैं।",
                                                            "All ${res.promotedCount + res.passedOutCount} passed students confirmed with no exceptions."
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .testTag("phase14_bulk_promote_passed_btn")
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text(p10Label(hi, "सभी उत्तीर्ण छात्रों को प्रमोट करें", "Promote All Passed"))
                                            }
                                            if (bulkExceptionsBanner != null) {
                                                Text(
                                                    text = bulkExceptionsBanner!!,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Show exceptions (NEEDS_REVIEW, DETAINED, LEFT) FIRST per requirements!
                                val filteredDrafts = drafts
                                    .filter { selectedReviewClass == "ALL" || it.student.className == selectedReviewClass }
                                    .sortedWith(
                                        compareBy<StudentPromotionDraft> {
                                            when (it.decision) {
                                                PromotionDecision.NEEDS_REVIEW -> 0
                                                PromotionDecision.DETAINED -> 1
                                                PromotionDecision.LEFT -> 2
                                                PromotionDecision.PROMOTED_BY_GRACE -> 3
                                                else -> 4
                                            }
                                        }.thenBy { it.student.rollNo }
                                    )

                                if (filteredDrafts.isEmpty()) {
                                    item {
                                        Card {
                                            Text(
                                                text = p10Label(
                                                    hi,
                                                    "सत्र $fromSession में कोई छात्र नहीं मिला।",
                                                    "No students found in session $fromSession."
                                                ),
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    items(filteredDrafts, key = { it.oldStudentDocId }) { draft ->
                                        PromotionStudentDraftCard(
                                            draft = draft,
                                            hi = hi,
                                            canEdit = canEdit,
                                            onSelectDecision = { dec ->
                                                if (dec == PromotionDecision.PROMOTED_BY_GRACE) {
                                                    graceDialogDraft = draft
                                                } else {
                                                    drafts = PromotionEngine.assignSectionsAndRollNumbers(
                                                        drafts.map {
                                                            if (it.oldStudentDocId == draft.oldStudentDocId) {
                                                                PromotionEngine.updateSingleDraftDecision(it, dec, effectiveClassOrder)
                                                            } else it
                                                        },
                                                        rollMode
                                                    )
                                                }
                                            },
                                            onEditSectionRoll = { rollDialogDraft = draft }
                                        )
                                    }
                                }

                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { wizardStep = 2 },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        ) { Text(p10Label(hi, "पीछे", "Back")) }
                                        Button(
                                            onClick = { wizardStep = 4 },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        ) { Text(p10Label(hi, "आगे: पूर्वावलोकन", "Next: Preview")) }
                                    }
                                }
                            }

                            4 -> item {
                                val unresolved = drafts.count { it.decision == PromotionDecision.NEEDS_REVIEW }
                                val promoted = drafts.count { it.decision == PromotionDecision.PROMOTED }
                                val grace = drafts.count { it.decision == PromotionDecision.PROMOTED_BY_GRACE }
                                val detained = drafts.count { it.decision == PromotionDecision.DETAINED }
                                val passedOut = drafts.count { it.decision == PromotionDecision.PASSED_OUT }
                                val left = drafts.count { it.decision == PromotionDecision.LEFT }
                                val totalCarryDue = drafts
                                    .filter { it.decision.createsNewSessionStudent }
                                    .sumOf { it.carriedForwardDueRs }

                                Card {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = p10Label(hi, "चरण 4: पूर्वावलोकन और पुष्टि", "Step 4: Final Preview & Confirm"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("${p10Label(hi, "विद्यालय", "School")}: ${activeSchool.name}")
                                        Text("${p10Label(hi, "सत्र परिवर्तन", "Transition")}: $fromSession → $newSessionName ($startDateIso to $endDateIso)")
                                        HorizontalDivider()
                                        Text("• ${p10Label(hi, "प्रमोटेड", "Promoted")}: $promoted")
                                        Text("• ${p10Label(hi, "ग्रेस से प्रमोट", "Promoted by Grace")}: $grace")
                                        Text("• ${p10Label(hi, "उसी कक्षा में रोके", "Detained (Same Class)")}: $detained")
                                        Text("• ${p10Label(hi, "उत्तीर्ण / पूर्व छात्र", "Passed Out / Alumni")}: $passedOut")
                                        Text("• ${p10Label(hi, "विद्यालय छोड़ा", "Left")}: $left")
                                        HorizontalDivider()
                                        Text(
                                            text = p10Label(
                                                hi,
                                                "नए सत्र में आगे बढ़ाया जाने वाला कुल बकाया शुल्क: ₹$totalCarryDue",
                                                "Total unpaid dues carried forward to $newSessionName: Rs.$totalCarryDue"
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = p10Label(
                                                hi,
                                                "ऑफ़लाइन सुरक्षा: प्रमोशन सुरक्षित बैच (25 छात्र/बैच) और निश्चित ID के साथ लागू होता है, इसलिए दो लोग एक ही कक्षा को प्रमोट करें तो भी डुप्लीकेट छात्र या बकाया नहीं बनता।",
                                                "Offline conflict protection: Promotion runs in safe 25-student batches using deterministic IDs, so even if two people promote the same class offline, no duplicate students or duplicate dues are created."
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (unresolved > 0) {
                                            Text(
                                                text = p10Label(
                                                    hi,
                                                    "$unresolved छात्र अभी भी 'जाँच आवश्यक' में हैं। कृपया चरण 3 में निर्णय चुनें।",
                                                    "$unresolved student(s) are still marked 'Needs Review'. Please choose a decision in Step 3."
                                                ),
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { wizardStep = 3 },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                            ) { Text(p10Label(hi, "पीछे", "Back")) }
                                            Button(
                                                onClick = { confirmApplyDialogOpen = true },
                                                enabled = canEdit && unresolved == 0,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .testTag("phase14_confirm_start_session_btn")
                                            ) {
                                                Text(p10Label(hi, "पुष्टि करें और लागू करें", "Confirm & Apply"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Phase14SessionTab.REGISTER -> {
                    val groupedByClass = remember(schoolPromotions) {
                        schoolPromotions.groupBy { "${it.fromSession} -> ${it.toSession} | ${it.fromClassName}" }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (groupedByClass.isEmpty()) {
                            item {
                                Card {
                                    Text(
                                        text = p10Label(
                                            hi,
                                            "अभी तक कोई प्रमोशन रजिस्टर नहीं बना है। नया सत्र शुरू करने पर प्रति कक्षा PDF यहाँ उपलब्ध होगा।",
                                            "No promotion registers yet. Start a new session to generate per-class A4 Promotion Register PDFs."
                                        ),
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            groupedByClass.forEach { (key, recs) ->
                                item(key = key) {
                                    val first = recs.first()
                                    Card {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "${p10Label(hi, "कक्षा", "Class")} ${first.fromClassName} (${first.fromSession} → ${first.toSession})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Promoted: ${recs.count { it.decision == PromotionDecision.PROMOTED }} • Grace: ${recs.count { it.decision == PromotionDecision.PROMOTED_BY_GRACE }} • Detained: ${recs.count { it.decision == PromotionDecision.DETAINED }} • Passed Out: ${recs.count { it.decision == PromotionDecision.PASSED_OUT }} • Left: ${recs.count { it.decision == PromotionDecision.LEFT }}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            val dateTxt = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(first.confirmedAtMs))
                                            Text(
                                                text = "${p10Label(hi, "पुष्टिकर्ता", "Confirmed by")}: ${first.confirmedByName} ($dateTxt)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                Button(
                                                    onClick = {
                                                        viewModel.generatePromotionRegisterPdfFile(
                                                            context = context,
                                                            fromSession = first.fromSession,
                                                            toSession = first.toSession,
                                                            className = first.fromClassName,
                                                            records = recs
                                                        ) { res ->
                                                            res.onSuccess { file ->
                                                                PdfExportHelper.shareFile(context, file, "application/pdf", "Promotion Register PDF")
                                                            }.onFailure { viewModel.emitMessage(it.message ?: "PDF error") }
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                                ) {
                                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(p10Label(hi, "PDF शेयर करें", "Share PDF"))
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        viewModel.generatePromotionRegisterPdfFile(
                                                            context = context,
                                                            fromSession = first.fromSession,
                                                            toSession = first.toSession,
                                                            className = first.fromClassName,
                                                            records = recs
                                                        ) { res ->
                                                            res.onSuccess { file ->
                                                                PdfExportHelper.printPdfFile(context, file, "Promotion Register ${first.fromClassName}", false)
                                                            }.onFailure { viewModel.emitMessage(it.message ?: "Print error") }
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                                ) {
                                                    Text(p10Label(hi, "प्रिंट (A4)", "Print A4"))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Phase14SessionTab.CLASS_ORDER -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = p10Label(
                                    hi,
                                    "प्रमोशन का कक्षा क्रम संपादित करें। अंतिम कक्षा के छात्र 'उत्तीर्ण / पूर्व छात्र (Alumni)' बन जाते हैं।",
                                    "Edit promotion class order for ${activeSchool.name}. The last class promotes to Passed Out / Alumni."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        itemsIndexed(editableClassOrder) { idx, clsName ->
                            Card {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${idx + 1}. $clsName",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (idx == editableClassOrder.lastIndex) {
                                                p10Label(hi, "→ उत्तीर्ण / पूर्व छात्र (Alumni)", "→ Passed Out / Alumni")
                                            } else {
                                                "→ ${editableClassOrder[idx + 1]}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (canEdit) {
                                        IconButton(
                                            enabled = idx > 0,
                                            onClick = {
                                                val copy = editableClassOrder.toMutableList()
                                                val item = copy.removeAt(idx)
                                                copy.add(idx - 1, item)
                                                editableClassOrder = copy
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up") }
                                        IconButton(
                                            enabled = idx < editableClassOrder.lastIndex,
                                            onClick = {
                                                val copy = editableClassOrder.toMutableList()
                                                val item = copy.removeAt(idx)
                                                copy.add(idx + 1, item)
                                                editableClassOrder = copy
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move down") }
                                    }
                                }
                            }
                        }
                        if (canEdit) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { editableClassOrder = SessionTransitionEngine.DEFAULT_CLASS_ORDER },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text(p10Label(hi, "डिफ़ॉल्ट", "Reset Default"))
                                    }
                                    Button(
                                        onClick = { viewModel.saveClassOrderForActiveSchool(editableClassOrder) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("phase14_save_class_order_btn")
                                    ) {
                                        Text(p10Label(hi, "क्रम सहेजें", "Save Order"))
                                    }
                                }
                            }
                        }
                    }
                }

                Phase14SessionTab.ALUMNI -> {
                    val alumniList = remember(schoolPromotions, alumniQuery) {
                        PromotionEngine.searchAlumni(schoolPromotions, alumniQuery)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = alumniQuery,
                                onValueChange = { alumniQuery = it },
                                label = { Text(p10Label(hi, "पूर्व छात्र खोजें (नाम, प्रवेश सं., मोबाइल)", "Search alumni (name, adm no., mobile)")) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (alumniQuery.isNotEmpty()) {
                                        IconButton(onClick = { alumniQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (alumniList.isEmpty()) {
                            item {
                                Card {
                                    Text(
                                        text = p10Label(
                                            hi,
                                            "कोई उत्तीर्ण / पूर्व छात्र रिकॉर्ड नहीं मिला। अंतिम कक्षा (जैसे 8th) प्रमोट होने पर छात्र यहाँ सुरक्षित रहते हैं।",
                                            "No alumni records found yet. Students promoted from the final class appear here and remain searchable."
                                        ),
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(alumniList, key = { it.id }) { al ->
                                Card {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(al.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = "Adm: ${al.admNo.ifBlank { "—" }} • ${p10Label(hi, "पिता", "Father")}: ${al.fatherName.ifBlank { "—" }} • ${al.mobile.ifBlank { "—" }}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "${p10Label(hi, "उत्तीर्ण कक्षा", "Passed Out")}: ${al.fromClassName}-${al.fromSection} (${al.fromSession}) • ${String.format(Locale.US, "%.1f", al.annualPercent)}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Grace Promotion Dialog (mandatory remark + approver recorded)
    val graceTarget = graceDialogDraft
    if (graceTarget != null) {
        var remarkText by remember(graceTarget.oldStudentDocId) { mutableStateOf(graceTarget.graceRemark) }
        AlertDialog(
            onDismissRequest = { graceDialogDraft = null },
            title = { Text(p10Label(hi, "ग्रेस से प्रमोट करें", "Promote by Grace")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${graceTarget.student.name} (${graceTarget.student.className}-${graceTarget.student.section})")
                    OutlinedTextField(
                        value = remarkText,
                        onValueChange = { remarkText = it },
                        label = { Text(p10Label(hi, "अनिवार्य टिप्पणी / कारण", "Mandatory Remark / Reason")) },
                        placeholder = { Text("e.g. Approved by Principal on medical grounds") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${p10Label(hi, "स्वीकृतकर्ता", "Approved by")}: ${uiState.currentMember?.name ?: "Admin"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = remarkText.trim().isNotEmpty(),
                    onClick = {
                        val approverName = uiState.currentMember?.name?.ifBlank { "Admin" } ?: "Admin"
                        val approverUid = uiState.currentMember?.uid?.ifBlank { "owner" } ?: "owner"
                        drafts = PromotionEngine.assignSectionsAndRollNumbers(
                            drafts.map {
                                if (it.oldStudentDocId == graceTarget.oldStudentDocId) {
                                    PromotionEngine.updateSingleDraftDecision(
                                        draft = it,
                                        newDecision = PromotionDecision.PROMOTED_BY_GRACE,
                                        classOrder = effectiveClassOrder,
                                        graceRemark = remarkText,
                                        approverUid = approverUid,
                                        approverName = approverName
                                    )
                                } else it
                            },
                            rollMode
                        )
                        graceDialogDraft = null
                    }
                ) { Text(p10Label(hi, "स्वीकृत करें", "Approve")) }
            },
            dismissButton = {
                TextButton(onClick = { graceDialogDraft = null }) { Text(p10Label(hi, "रद्द करें", "Cancel")) }
            }
        )
    }

    // Section & Roll No Override Dialog
    val rollTarget = rollDialogDraft
    if (rollTarget != null) {
        var secInput by remember(rollTarget.oldStudentDocId) { mutableStateOf(rollTarget.toSection.ifBlank { "A" }) }
        var rollInput by remember(rollTarget.oldStudentDocId) { mutableStateOf(rollTarget.toRollNo.takeIf { it > 0 }?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { rollDialogDraft = null },
            title = { Text(p10Label(hi, "नया सेक्शन व रोल नं.", "New Section & Roll No.")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${rollTarget.student.name} → ${rollTarget.toClassName}")
                    OutlinedTextField(
                        value = secInput,
                        onValueChange = { secInput = it.uppercase(Locale.US).take(5) },
                        label = { Text(p10Label(hi, "सेक्शन", "Section")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rollInput,
                        onValueChange = { rollInput = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text(p10Label(hi, "रोल नं.", "Roll No.")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val rollOverride = rollInput.toIntOrNull()?.takeIf { it > 0 }
                    drafts = PromotionEngine.assignSectionsAndRollNumbers(
                        drafts = drafts,
                        mode = rollMode,
                        sectionOverrideByStudentDocId = mapOf(rollTarget.oldStudentDocId to secInput.ifBlank { "A" }),
                        manualRollByStudentDocId = if (rollOverride != null) mapOf(rollTarget.oldStudentDocId to rollOverride) else emptyMap()
                    )
                    rollDialogDraft = null
                }) { Text(p10Label(hi, "सहेजें", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { rollDialogDraft = null }) { Text(p10Label(hi, "रद्द करें", "Cancel")) }
            }
        )
    }

    // Confirm Apply Dialog
    if (confirmApplyDialogOpen) {
        AlertDialog(
            onDismissRequest = { confirmApplyDialogOpen = false },
            title = { Text(p10Label(hi, "नया सत्र $newSessionName लागू करें?", "Start Session $newSessionName?")) },
            text = {
                Text(
                    p10Label(
                        hi,
                        "नए सत्र के छात्र, बकाया शुल्क और चयनित सेटअप सुरक्षित बैच में लागू होंगे। पिछले सत्र ($fromSession) का कोई रिकॉर्ड नहीं बदलेगा।",
                        "New-session students, carried-forward dues, and selected setup copies will be applied in safe batches. No record in $fromSession will be altered."
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmApplyDialogOpen = false
                    viewModel.applyNewSessionAndPromotion(
                        fromSession = fromSession,
                        toSession = newSessionName,
                        startDateIso = startDateIso,
                        endDateIso = endDateIso,
                        copyOptions = copyOptions,
                        drafts = drafts,
                        editedHolidays = editableHolidays
                    ) { res ->
                        res.onSuccess { rec ->
                            appliedSummaryRecord = rec
                            wizardStep = 1
                        }
                    }
                }) { Text(p10Label(hi, "हाँ, शुरू करें", "Yes, Start Session")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmApplyDialogOpen = false }) { Text(p10Label(hi, "रद्द करें", "Cancel")) }
            }
        )
    }

    // Owner 7-Day Undo Warning Dialog
    val undoTarget = undoDialogTransition
    if (undoTarget != null) {
        val check = viewModel.evaluateUndoForTransition(undoTarget)
        AlertDialog(
            onDismissRequest = { undoDialogTransition = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(p10Label(hi, "सत्र ${undoTarget.toSession} प्रारंभ वापस लें?", "Undo Session ${undoTarget.toSession} Start?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p10Label(hi, check.reasonHi, check.reasonEn))
                    if (check.requiresNewDataWarning) {
                        Text(
                            text = p10Label(
                                hi,
                                "चेतावनी: इस नए सत्र में पहले से दर्ज नया डेटा (${check.guardSummary.warningItemsEn().joinToString(", ")}) सुरक्षित रहेगा और हटाया नहीं जाएगा। केवल विज़ार्ड द्वारा बनाए गए प्रमोटेड छात्र हटाए जाएंगे।",
                                "Warning: New data already entered in ${undoTarget.toSession} (${check.guardSummary.warningItemsEn().joinToString(", ")}) will NOT be deleted silently — only wizard-created promotion docs are reverted."
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.undoSessionStartAsOwner(undoTarget)
                        undoDialogTransition = null
                    }
                ) { Text(p10Label(hi, "हाँ, वापस लें", "Yes, Undo")) }
            },
            dismissButton = {
                TextButton(onClick = { undoDialogTransition = null }) { Text(p10Label(hi, "रद्द करें", "Cancel")) }
            }
        )
    }
}

@Composable
private fun CopyCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromotionStudentDraftCard(
    draft: StudentPromotionDraft,
    hi: Boolean,
    canEdit: Boolean,
    onSelectDecision: (PromotionDecision) -> Unit,
    onEditSectionRoll: () -> Unit
) {
    val containerColor = when (draft.decision) {
        PromotionDecision.NEEDS_REVIEW -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        PromotionDecision.DETAINED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${draft.student.name} (${draft.student.className}-${draft.student.section} #${draft.student.rollNo})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Adm: ${draft.student.admNo} • ${p10Label(hi, "वार्षिक", "Annual")}: ${if (draft.hasAnyAnnualMarks) "${String.format(Locale.US, "%.1f", draft.annualPercent)}% (${draft.annualResultStatus.label})" else p10Label(hi, "अंक दर्ज नहीं", "No annual marks")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (draft.carriedForwardDueRs > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${p10Label(hi, "बकाया", "Due")} ₹${draft.carriedForwardDueRs}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (draft.decision.createsNewSessionStudent && draft.toClassName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "→ ${draft.toClassName}-${draft.toSection} (${p10Label(hi, "रोल", "Roll")} #${draft.toRollNo})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (canEdit) {
                        TextButton(onClick = onEditSectionRoll, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(p10Label(hi, "सेक्शन/रोल बदलें", "Edit Sec/Roll"))
                        }
                    }
                }
            } else if (draft.decision == PromotionDecision.PASSED_OUT) {
                Text(
                    text = "→ ${p10Label(hi, "उत्तीर्ण / पूर्व छात्र (Alumni)", "Passed Out / Alumni")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (draft.decision == PromotionDecision.PROMOTED_BY_GRACE && draft.graceRemark.isNotBlank()) {
                Text(
                    text = "${p10Label(hi, "ग्रेस टिप्पणी", "Grace")}: ${draft.graceRemark} (${draft.graceApprovedByName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (canEdit) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        PromotionDecision.PROMOTED,
                        PromotionDecision.PROMOTED_BY_GRACE,
                        PromotionDecision.DETAINED,
                        PromotionDecision.LEFT,
                        PromotionDecision.PASSED_OUT
                    ).forEach { opt ->
                        FilterChip(
                            selected = draft.decision == opt,
                            onClick = { onSelectDecision(opt) },
                            label = { Text(p10Label(hi, opt.labelHi, opt.labelEn), maxLines = 1) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxNoAccessPhase14(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
