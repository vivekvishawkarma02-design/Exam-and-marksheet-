package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.AuthorizedPickupPerson
import com.example.domain.GatePassRecord
import com.example.domain.Phase19RemarksPtmAndGateEngine
import com.example.domain.Phase6DateUtils
import com.example.domain.VisitorEntryRecord
import com.example.domain.VisitorStatus
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import com.example.util.ImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =============================================================================
// PHASE 19: GATE PASS, DAILY GATE REGISTER & VISITOR REGISTER SCREEN
// =============================================================================

private enum class GateAndVisitorTab(val en: String, val hi: String) {
    ISSUE_PASS("Early Leave Pass", "गेट पास (जल्दी छुट्टी)"),
    GATE_REGISTER("Daily Gate Register", "दैनिक गेट रजिस्टर"),
    VISITOR_REGISTER("Visitor Register", "आगंतुक रजिस्टर")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Phase19GateAndVisitorScreen(
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()
    val hi = uiState.phase10HindiLabels

    if (!uiState.canViewGateModule) {
        Scaffold { inner ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = p10Label(
                        hi,
                        "गेट पास व आगंतुक रजिस्टर देखने की अनुमति नहीं है",
                        "No access to Gate Pass & Visitor Register"
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(hi, "वापस जाएँ", "Go Back"))
                }
            }
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val activeSchoolDocId = uiState.activeSchoolDocId
    val activeSchoolLongId = uiState.activeSchool.id
    val schoolName = uiState.activeSchool.name
    val session = uiState.filter.session
    val todayIso = remember { Phase6DateUtils.todayIso() }
    val todaySummary = remember(uiState.gatePasses, uiState.visitorEntries, activeSchoolDocId, todayIso) {
        uiState.todayGateSummaryForActiveSchool(
            todayIso = todayIso,
            schoolDocId = activeSchoolDocId
        )
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("phase19_gate_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = p10Label(hi, "वापस", "Back")
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(
                                    hi,
                                    "गेट पास, पिकअप व आगंतुक रजिस्टर",
                                    "Gate Pass, Pickup & Visitor Register"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = p10Label(
                                    hi,
                                    "$schoolName • आज गेट पास: ${todaySummary.todayEarlyLeavesCount}, आगंतुक: ${todaySummary.todayVisitorsTotal}",
                                    "$schoolName • Today Early Leaves: ${todaySummary.todayEarlyLeavesCount}, Visitors: ${todaySummary.todayVisitorsTotal}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { viewModel.setPhase10HindiLabels(!hi) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("phase19_gate_lang_toggle")
                        ) {
                            Text(
                                text = if (hi) "EN" else "हिंदी",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    SecondaryScrollableTabRow(selectedTabIndex = selectedTab) {
                        GateAndVisitorTab.entries.forEachIndexed { idx, tab ->
                            val badge = when (tab) {
                                GateAndVisitorTab.GATE_REGISTER -> if (todaySummary.todayEarlyLeavesCount > 0) " (${todaySummary.todayEarlyLeavesCount})" else ""
                                GateAndVisitorTab.VISITOR_REGISTER -> if (todaySummary.todayVisitorsInside > 0) " (${todaySummary.todayVisitorsInside} Inside)" else ""
                                else -> ""
                            }
                            Tab(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("gate_tab_${tab.name.lowercase()}"),
                                text = {
                                    Text(
                                        text = (if (hi) tab.hi else tab.en) + badge,
                                        fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        when (GateAndVisitorTab.entries[selectedTab]) {
            GateAndVisitorTab.ISSUE_PASS -> {
                IssueEarlyLeavePassTab(
                    viewModel = viewModel,
                    uiState = uiState,
                    hi = hi,
                    activeSchoolDocId = activeSchoolDocId,
                    activeSchoolLongId = activeSchoolLongId,
                    session = session,
                    todayIso = todayIso,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            GateAndVisitorTab.GATE_REGISTER -> {
                DailyGateRegisterTab(
                    viewModel = viewModel,
                    uiState = uiState,
                    hi = hi,
                    activeSchoolDocId = activeSchoolDocId,
                    session = session,
                    todayIso = todayIso,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            GateAndVisitorTab.VISITOR_REGISTER -> {
                VisitorRegisterTab(
                    viewModel = viewModel,
                    uiState = uiState,
                    hi = hi,
                    activeSchoolDocId = activeSchoolDocId,
                    session = session,
                    todayIso = todayIso,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    // Shared PDF Preview Dialog (A5 Gate Pass Slip)
    if (pdfState.isGenerating || pdfState.generatedFile != null || pdfState.error != null) {
        TimetablePdfPreviewDialog(
            pdfState = pdfState,
            onSavePdfToUri = viewModel::saveTimetablePdfToUri,
            onSaveZipToUri = viewModel::saveTimetableZipToUri,
            onDismiss = viewModel::clearTimetablePdfPreview
        )
    }
}

// =============================================================================
// TAB 1: ISSUE EARLY LEAVE PASS (A5 Pass Slip + Authorised Pickup / One-Time)
// =============================================================================

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun IssueEarlyLeavePassTab(
    viewModel: SchoolViewModel,
    uiState: SchoolUiState,
    hi: Boolean,
    activeSchoolDocId: String,
    activeSchoolLongId: Long,
    session: String,
    todayIso: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allClasses = remember(uiState.schoolClasses, activeSchoolLongId) {
        val raw = uiState.classesForSchool(activeSchoolLongId).map { it.name.trim() }.filter { it.isNotBlank() }.distinct()
        raw.ifEmpty { listOf("1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th", "9th", "10th") }
    }
    var selectedClass by remember(allClasses) { mutableStateOf(allClasses.firstOrNull().orEmpty()) }
    val sectionsForClass = remember(uiState.allStudents, activeSchoolLongId, selectedClass) {
        uiState.allStudents
            .filter { it.schoolId == activeSchoolLongId && it.className.trim().equals(selectedClass, ignoreCase = true) && it.isActiveStatus }
            .map { it.section.trim().uppercase(Locale.US).ifBlank { "A" } }
            .distinct()
            .sorted()
            .ifEmpty { listOf("A") }
    }
    var selectedSection by remember(sectionsForClass) { mutableStateOf(sectionsForClass.firstOrNull() ?: "A") }

    val classStudents = remember(uiState.allStudents, activeSchoolLongId, session, selectedClass, selectedSection) {
        uiState.allStudents
            .filter {
                it.schoolId == activeSchoolLongId &&
                    it.session == session &&
                    it.isActiveStatus &&
                    it.className.trim().equals(selectedClass, ignoreCase = true) &&
                    it.section.trim().ifBlank { "A" }.equals(selectedSection, ignoreCase = true)
            }
            .sortedWith(compareBy({ if (it.rollNo > 0) it.rollNo else Int.MAX_VALUE }, { it.name.lowercase(Locale.US) }))
    }
    var selectedStudentId by remember(classStudents) {
        mutableStateOf(classStudents.firstOrNull()?.id ?: 0L)
    }
    val selectedStudent = remember(classStudents, selectedStudentId) {
        classStudents.firstOrNull { it.id == selectedStudentId } ?: classStudents.firstOrNull()
    }

    val authorizedListForStudent = remember(uiState.authorizedPickups, activeSchoolDocId, selectedStudent) {
        val st = selectedStudent
        if (st == null) {
            emptyList()
        } else {
            val stDocKey = uiState.resolveStudentDocKey(st)
            val legacyKey = st.id.toString()
            uiState.authorizedPickups.filter {
                it.schoolId == activeSchoolDocId &&
                    (it.studentId == stDocKey || it.studentId == legacyKey)
            }
        }
    }

    var passDateIso by remember { mutableStateOf(todayIso) }
    var leaveTimeLabel by remember {
        mutableStateOf(SimpleDateFormat("hh:mm a", Locale.US).format(Date()))
    }
    var leaveReason by remember { mutableStateOf("") }
    var useAuthorizedList by remember(authorizedListForStudent) {
        mutableStateOf(authorizedListForStudent.isNotEmpty())
    }
    var selectedAuthorizedId by remember(authorizedListForStudent) {
        mutableStateOf(authorizedListForStudent.firstOrNull()?.id.orEmpty())
    }
    val selectedAuthorizedPerson = remember(authorizedListForStudent, selectedAuthorizedId) {
        authorizedListForStudent.firstOrNull { it.id == selectedAuthorizedId }
            ?: authorizedListForStudent.firstOrNull()
    }

    // One-time person fields
    var oneTimeName by remember { mutableStateOf("") }
    var oneTimeRelation by remember { mutableStateOf("Father") }
    var oneTimeMobile by remember { mutableStateOf("") }
    var oneTimeNote by remember { mutableStateOf("") }
    var guardianPhoneConfirmed by remember { mutableStateOf(false) }

    // Pre-fill one-time person from student's guardian when student changes and no authorised pickup exists
    LaunchedEffect(selectedStudent, authorizedListForStudent) {
        if (authorizedListForStudent.isEmpty() && selectedStudent != null) {
            useAuthorizedList = false
            if (oneTimeName.isBlank()) {
                oneTimeName = selectedStudent.fatherName.ifBlank { selectedStudent.motherName }
                oneTimeRelation = if (selectedStudent.fatherName.isNotBlank()) "Father" else "Guardian"
                oneTimeMobile = selectedStudent.mobile.ifBlank { selectedStudent.secondMobile }
            }
        }
    }

    val defaultApprover = remember(uiState.currentMember) {
        uiState.currentMember?.name?.ifBlank { "Principal / Class Teacher" } ?: "Principal / Class Teacher"
    }
    var approvedByName by remember(defaultApprover) { mutableStateOf(defaultApprover) }

    val canMarkHalfDay = remember(
        activeSchoolDocId,
        selectedClass,
        selectedSection,
        uiState.accessProfile,
        uiState.effectiveOwnTeacherScope
    ) {
        viewModel.canMarkHalfDayAttendanceFromGatePass(activeSchoolDocId, selectedClass, selectedSection)
    }
    var markHalfDayAttendance by remember(canMarkHalfDay) { mutableStateOf(canMarkHalfDay) }

    var showAddAuthorizedPickupDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = p10Label(
                            hi,
                            "जल्दी छुट्टी गेट पास बनाएँ (A5 पर्ची)",
                            "Issue Early Leave Gate Pass (A5 Slip)"
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Class Picker
                    Text(
                        text = p10Label(hi, "कक्षा चुनें:", "Select Class:"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        allClasses.forEach { cls ->
                            FilterChip(
                                selected = selectedClass.equals(cls, ignoreCase = true),
                                onClick = { selectedClass = cls },
                                label = { Text(cls) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("gate_pass_class_chip_$cls")
                            )
                        }
                    }

                    // Section Picker
                    if (sectionsForClass.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = p10Label(hi, "सेक्शन:", "Section:"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            sectionsForClass.forEach { sec ->
                                FilterChip(
                                    selected = selectedSection.equals(sec, ignoreCase = true),
                                    onClick = { selectedSection = sec },
                                    label = { Text(sec) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    // Student Picker
                    Text(
                        text = p10Label(
                            hi,
                            "छात्र चुनें (${classStudents.size}):",
                            "Select Student (${classStudents.size}):"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (classStudents.isEmpty()) {
                        Text(
                            text = p10Label(hi, "इस कक्षा में कोई छात्र नहीं है।", "No students in this class."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            classStudents.forEach { st ->
                                FilterChip(
                                    selected = selectedStudent?.id == st.id,
                                    onClick = { selectedStudentId = st.id },
                                    label = {
                                        val r = if (st.rollNo > 0) "#${st.rollNo} " else ""
                                        Text("$r${st.name}")
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("gate_pass_student_chip_${st.id}")
                                )
                            }
                        }
                    }

                    // Date & Time & Reason
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = passDateIso,
                            onValueChange = { passDateIso = it },
                            label = { Text(p10Label(hi, "तारीख (YYYY-MM-DD)", "Date")) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gate_pass_date_input")
                        )
                        OutlinedTextField(
                            value = leaveTimeLabel,
                            onValueChange = { leaveTimeLabel = it },
                            label = { Text(p10Label(hi, "समय", "Leave Time")) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gate_pass_time_input")
                        )
                    }

                    OutlinedTextField(
                        value = leaveReason,
                        onValueChange = { leaveReason = it },
                        label = { Text(p10Label(hi, "जल्दी जाने का कारण (Reason)", "Reason for Early Leave")) },
                        placeholder = {
                            Text(p10Label(hi, "उदा. डॉक्टर की अपॉइंटमेंट / बुखार / पारिवारिक कार्य", "e.g. Doctor appointment / Fever / Family function"))
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gate_pass_reason_input")
                    )

                    HorizontalDivider()

                    // Pickup Person Selection: Authorised List vs One-Time Person
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = p10Label(hi, "बच्चे को कौन ले जा रहा है?", "Who is taking the child?"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.canEditAuthorizedPickups && selectedStudent != null) {
                            TextButton(
                                onClick = { showAddAuthorizedPickupDialog = true },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("gate_pass_add_auth_pickup_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(hi, "अधिकृत व्यक्ति जोड़ें", "Add Authorised Person"))
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = useAuthorizedList,
                            onClick = { useAuthorizedList = true },
                            label = {
                                Text(
                                    p10Label(
                                        hi,
                                        "अधिकृत सूची (${authorizedListForStudent.size})",
                                        "Authorised List (${authorizedListForStudent.size})"
                                    )
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("gate_pass_mode_authorized")
                        )
                        FilterChip(
                            selected = !useAuthorizedList,
                            onClick = { useAuthorizedList = false },
                            label = {
                                Text(p10Label(hi, "एक-बारगी व्यक्ति (फ़ोन पुष्टि)", "One-Time Person (Phone Confirm)"))
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("gate_pass_mode_onetime")
                        )
                    }

                    if (useAuthorizedList) {
                        if (authorizedListForStudent.isEmpty()) {
                            Surface(
                                color = AbsentAmberContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = p10Label(
                                        hi,
                                        "इस छात्र के लिए कोई अधिकृत पिकअप व्यक्ति दर्ज नहीं है। ऊपर '+ अधिकृत व्यक्ति जोड़ें' दबाएँ या 'एक-बारगी व्यक्ति' चुनें।",
                                        "No authorised pickup persons saved for this student yet. Tap '+ Add Authorised Person' or switch to 'One-Time Person'."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AbsentAmber,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            authorizedListForStudent.forEach { person ->
                                val isSel = selectedAuthorizedPerson?.id == person.id
                                Surface(
                                    color = if (isSel) PassGreenContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedAuthorizedId = person.id }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (person.photoBase64.isNotBlank()) {
                                            val bmp = remember(person.photoBase64) {
                                                ImageUtils.decodeBase64ToBitmap(person.photoBase64)
                                            }
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = person.personName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${person.personName} (${person.relation})",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = person.mobile.ifBlank { "No mobile" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = if (isSel) PassGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // One-time person inputs + guardian phone confirmation tick
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = oneTimeName,
                                onValueChange = { oneTimeName = it },
                                label = { Text(p10Label(hi, "ले जाने वाले का नाम", "Pickup Person Name")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("gate_pass_onetime_name_input")
                            )
                            OutlinedTextField(
                                value = oneTimeRelation,
                                onValueChange = { oneTimeRelation = it },
                                label = { Text(p10Label(hi, "संबंध (Relation)", "Relation")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("gate_pass_onetime_relation_input")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = oneTimeMobile,
                                onValueChange = { oneTimeMobile = it },
                                label = { Text(p10Label(hi, "मोबाइल नंबर", "Mobile Number")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("gate_pass_onetime_mobile_input")
                            )
                            OutlinedTextField(
                                value = oneTimeNote,
                                onValueChange = { oneTimeNote = it },
                                label = { Text(p10Label(hi, "पहचान / नोट (Note)", "ID / Note")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("gate_pass_onetime_note_input")
                            )
                        }

                        Surface(
                            color = if (guardianPhoneConfirmed) PassGreenContainer else AbsentAmberContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { guardianPhoneConfirmed = !guardianPhoneConfirmed }
                                .testTag("gate_pass_guardian_phone_confirm_row")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = guardianPhoneConfirmed,
                                    onCheckedChange = { guardianPhoneConfirmed = it },
                                    modifier = Modifier.testTag("gate_pass_guardian_phone_confirm_cb")
                                )
                                Icon(
                                    imageVector = Icons.Default.PhoneCallback,
                                    contentDescription = null,
                                    tint = if (guardianPhoneConfirmed) PassGreen else AbsentAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = p10Label(
                                        hi,
                                        "अभिभावक से फ़ोन पर पुष्टि की गई (अनिवार्य टिक)",
                                        "Confirmed with guardian by phone (Required for one-time pickup)"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Approved By
                    OutlinedTextField(
                        value = approvedByName,
                        onValueChange = { approvedByName = it },
                        label = { Text(p10Label(hi, "किसने अनुमति दी (Approved By)", "Approved By")) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gate_pass_approved_by_input")
                    )

                    // Optional Half-Day Attendance Marking
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = markHalfDayAttendance && canMarkHalfDay,
                                    onCheckedChange = { if (canMarkHalfDay) markHalfDayAttendance = it },
                                    enabled = canMarkHalfDay,
                                    modifier = Modifier.testTag("gate_pass_half_day_cb")
                                )
                                Text(
                                    text = p10Label(
                                        hi,
                                        "उपस्थिति रजिस्टर में आज की उपस्थिति 'अर्ध-दिवस (Half-Day)' दर्ज करें",
                                        "Also mark today's attendance as Half-Day for this student"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (!canMarkHalfDay) {
                                Text(
                                    text = p10Label(
                                        hi,
                                        "नोट: इस कक्षा की उपस्थिति बदलने की अनुमति आपके पास नहीं है, इसलिए केवल गेट पास बनेगा (उपस्थिति कक्षा शिक्षक दर्ज करेंगे)।",
                                        "Note: You do not have Attendance edit access for this class-section; gate pass will be created without modifying attendance."
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }

                    if (uiState.canEditGateModule) {
                        Button(
                            onClick = {
                                val st = selectedStudent
                                if (st == null) {
                                    Toast.makeText(
                                        context,
                                        p10Label(hi, "कृपया छात्र चुनें", "Please select a student"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                                viewModel.issueEarlyLeaveGatePass(
                                    schoolDocId = activeSchoolDocId,
                                    session = session,
                                    student = st,
                                    dateIso = passDateIso,
                                    leaveTime = leaveTimeLabel,
                                    reason = leaveReason,
                                    pickupFromAuthorizedList = useAuthorizedList,
                                    authorizedPickupPerson = selectedAuthorizedPerson,
                                    oneTimePersonName = oneTimeName,
                                    oneTimePersonRelation = oneTimeRelation,
                                    oneTimePersonMobile = oneTimeMobile,
                                    oneTimeNote = oneTimeNote,
                                    guardianPhoneConfirmed = guardianPhoneConfirmed,
                                    approvedByName = approvedByName,
                                    markHalfDayAttendance = markHalfDayAttendance,
                                    autoPreviewPdf = true
                                ) { ok, msg, _ ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (ok) {
                                        leaveReason = ""
                                        oneTimeNote = ""
                                    }
                                }
                            },
                            enabled = selectedStudent != null && leaveReason.trim().isNotBlank() &&
                                ((useAuthorizedList && selectedAuthorizedPerson != null) ||
                                    (!useAuthorizedList && oneTimeName.trim().isNotBlank() && guardianPhoneConfirmed)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("gate_pass_issue_btn")
                        ) {
                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                p10Label(
                                    hi,
                                    "गेट पास जारी करें व A5 पर्ची देखें",
                                    "Issue Gate Pass & Preview A5 Slip"
                                )
                            )
                        }
                    } else {
                        Text(
                            text = p10Label(
                                hi,
                                "केवल देखने की अनुमति (View Only)",
                                "View Only — Gate edit permission is required to issue passes."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Add Authorised Pickup Person Dialog
    if (showAddAuthorizedPickupDialog && selectedStudent != null) {
        AddAuthorizedPickupPersonDialog(
            studentName = selectedStudent.name,
            hi = hi,
            onDismiss = { showAddAuthorizedPickupDialog = false },
            onSave = { pName, pRel, pMob, pPhoto ->
                viewModel.saveAuthorizedPickupPerson(
                    schoolDocId = activeSchoolDocId,
                    student = selectedStudent,
                    personName = pName,
                    relation = pRel,
                    mobile = pMob,
                    photoBase64 = pPhoto
                ) { ok, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (ok) {
                        useAuthorizedList = true
                        showAddAuthorizedPickupDialog = false
                    }
                }
            }
        )
    }
}

@Composable
internal fun AddAuthorizedPickupPersonDialog(
    studentName: String,
    hi: Boolean,
    onDismiss: () -> Unit,
    onSave: (personName: String, relation: String, mobile: String, photoBase64: String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Father") }
    var mobile by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf("") }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val encoded = ImageUtils.encodeUriToBase64Under150Kb(context, uri)
            if (!encoded.isNullOrBlank()) {
                photoBase64 = encoded
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                p10Label(
                    hi,
                    "अधिकृत पिकअप व्यक्ति जोड़ें ($studentName)",
                    "Add Authorised Pickup Person ($studentName)"
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(p10Label(hi, "नाम (Name)", "Full Name")) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_pickup_name_input")
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text(p10Label(hi, "संबंध (पिता/माता/चाचा आदि)", "Relation (Father/Mother/Uncle etc.)")) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_pickup_relation_input")
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text(p10Label(hi, "मोबाइल नंबर", "Mobile Number")) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_pickup_mobile_input")
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (photoBase64.isBlank()) {
                                p10Label(hi, "फोटो जोड़ें (वैकल्पिक)", "Add Photo (Optional)")
                            } else {
                                p10Label(hi, "फोटो बदलें", "Change Photo")
                            }
                        )
                    }
                    if (photoBase64.isNotBlank()) {
                        val bmp = remember(photoBase64) { ImageUtils.decodeBase64ToBitmap(photoBase64) }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, relation, mobile, photoBase64) },
                enabled = name.trim().isNotBlank() && relation.trim().isNotBlank(),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("auth_pickup_save_btn")
            ) {
                Text(p10Label(hi, "सहेजें", "Save"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(hi, "रद्द करें", "Cancel"))
            }
        }
    )
}

// =============================================================================
// TAB 2: DAILY GATE REGISTER (Date / Date Range + CSV Export for Owner & Admins)
// =============================================================================

@Composable
private fun DailyGateRegisterTab(
    viewModel: SchoolViewModel,
    uiState: SchoolUiState,
    hi: Boolean,
    activeSchoolDocId: String,
    session: String,
    todayIso: String,
    modifier: Modifier = Modifier
) {
    var startDateIso by remember { mutableStateOf(todayIso) }
    var endDateIso by remember { mutableStateOf(todayIso) }

    val filteredPasses = remember(
        uiState.gatePasses,
        activeSchoolDocId,
        session,
        startDateIso,
        endDateIso
    ) {
        Phase19RemarksPtmAndGateEngine.filterGatePasses(
            passes = uiState.gatePasses,
            schoolId = activeSchoolDocId,
            session = session,
            fromDateIso = startDateIso,
            toDateIso = endDateIso
        )
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportGateRegisterCsvToUri(
                destinationUri = uri,
                passes = filteredPasses
            )
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = p10Label(
                            hi,
                            "दैनिक गेट रजिस्टर (${filteredPasses.size} पास)",
                            "Daily Gate Register (${filteredPasses.size} passes)"
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startDateIso,
                            onValueChange = { startDateIso = it },
                            label = { Text(p10Label(hi, "प्रारंभ तिथि (YYYY-MM-DD)", "From Date")) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gate_reg_from_date_input")
                        )
                        OutlinedTextField(
                            value = endDateIso,
                            onValueChange = { endDateIso = it },
                            label = { Text(p10Label(hi, "अंतिम तिथि (YYYY-MM-DD)", "To Date")) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gate_reg_to_date_input")
                        )
                    }

                    if (uiState.canExportGateRegisterCsv) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    saveCsvLauncher.launch("Gate_Register_${startDateIso}_to_${endDateIso}.csv")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("gate_reg_save_csv_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(p10Label(hi, "CSV सहेजें", "Save CSV"))
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.shareGateRegisterCsv(
                                        passes = filteredPasses,
                                        startDateIso = startDateIso,
                                        endDateIso = endDateIso
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("gate_reg_share_csv_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(p10Label(hi, "CSV साझा करें", "Share CSV"))
                            }
                        }
                    }
                }
            }
        }

        if (filteredPasses.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = p10Label(
                            hi,
                            "चयनित तिथि सीमा में कोई गेट पास नहीं मिला।",
                            "No early leave gate passes found in the selected date range."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(filteredPasses, key = { it.id }) { pass ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gate_pass_card_${pass.passNumber}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${pass.studentName} (${pass.className}-${pass.section})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${pass.dateIso} • ${pass.leaveTime}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = PassGreenContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = pass.passNumber,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PassGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = "${p10Label(hi, "कारण", "Reason")}: ${pass.reason}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        val modeStr = if (!pass.isOneTimePickup) {
                            p10Label(hi, "अधिकृत सूची", "Authorised List")
                        } else {
                            p10Label(hi, "एक-बारगी (फ़ोन पुष्टि ✓)", "One-Time (Phone Confirmed ✓)")
                        }
                        Text(
                            text = "${p10Label(hi, "ले जाने वाले", "Taken By")}: ${pass.pickupPersonName} (${pass.pickupRelation}) • ${pass.pickupMobile} [$modeStr]",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${p10Label(hi, "अनुमति", "Approved By")}: ${pass.approvedByName}" +
                                    if (pass.markedHalfDayAttendance) " • Half-Day ✓" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { viewModel.previewGatePassA5Pdf(pass) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("gate_pass_reprint_btn_${pass.passNumber}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(hi, "A5 पर्ची PDF", "A5 Slip PDF"))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// TAB 3: VISITOR REGISTER (Check-In + One-Tap Sign Out)
// =============================================================================

@Composable
private fun VisitorRegisterTab(
    viewModel: SchoolViewModel,
    uiState: SchoolUiState,
    hi: Boolean,
    activeSchoolDocId: String,
    session: String,
    todayIso: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var filterDateIso by remember { mutableStateOf(todayIso) }

    var visitorName by remember { mutableStateOf("") }
    var visitorMobile by remember { mutableStateOf("") }
    var visitorPurpose by remember { mutableStateOf("") }
    var whomToMeet by remember { mutableStateOf("Principal / Office") }
    var timeInLabel by remember {
        mutableStateOf(SimpleDateFormat("hh:mm a", Locale.US).format(Date()))
    }
    var visitorPhotoBase64 by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val encoded = ImageUtils.encodeUriToBase64Under150Kb(context, uri)
            if (!encoded.isNullOrBlank()) {
                visitorPhotoBase64 = encoded
            }
        }
    }

    val visitorsForDate = remember(uiState.visitorEntries, activeSchoolDocId, filterDateIso) {
        val cleanDate = filterDateIso.trim()
        uiState.visitorEntries
            .filter { it.schoolId == activeSchoolDocId && (cleanDate.isBlank() || it.dateIso == cleanDate) }
            .sortedByDescending { it.createdAtMs }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.canEditGateModule) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = p10Label(hi, "नया आगंतुक प्रवेश (Visitor Check-In)", "New Visitor Entry"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = visitorName,
                                onValueChange = { visitorName = it },
                                label = { Text(p10Label(hi, "आगंतुक का नाम", "Visitor Name")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("visitor_name_input")
                            )
                            OutlinedTextField(
                                value = visitorMobile,
                                onValueChange = { visitorMobile = it },
                                label = { Text(p10Label(hi, "मोबाइल नंबर", "Mobile")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("visitor_mobile_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = visitorPurpose,
                                onValueChange = { visitorPurpose = it },
                                label = { Text(p10Label(hi, "आने का उद्देश्य (Purpose)", "Purpose")) },
                                placeholder = { Text(p10Label(hi, "उदा. प्रवेश पूछताछ / शुल्क", "e.g. Admission Enquiry")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("visitor_purpose_input")
                            )
                            OutlinedTextField(
                                value = whomToMeet,
                                onValueChange = { whomToMeet = it },
                                label = { Text(p10Label(hi, "किससे मिलना है", "Whom to Meet")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("visitor_whom_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = timeInLabel,
                                onValueChange = { timeInLabel = it },
                                label = { Text(p10Label(hi, "प्रवेश समय (Time In)", "Time In")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("visitor_time_in_input")
                            )
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("visitor_photo_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (visitorPhotoBase64.isBlank()) {
                                        p10Label(hi, "फोटो (वैकल्पिक)", "Photo (Optional)")
                                    } else {
                                        p10Label(hi, "फोटो जुड़ा ✓", "Photo Added ✓")
                                    }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.addVisitorEntry(
                                    schoolDocId = activeSchoolDocId,
                                    dateIso = filterDateIso,
                                    visitorName = visitorName,
                                    mobile = visitorMobile,
                                    purpose = visitorPurpose,
                                    whomToMeet = whomToMeet,
                                    timeIn = timeInLabel,
                                    photoBase64 = visitorPhotoBase64
                                ) { ok, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (ok) {
                                        visitorName = ""
                                        visitorMobile = ""
                                        visitorPurpose = ""
                                        visitorPhotoBase64 = ""
                                        timeInLabel = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
                                    }
                                }
                            },
                            enabled = visitorName.trim().isNotBlank() && visitorPurpose.trim().isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("visitor_checkin_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Badge, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(p10Label(hi, "आगंतुक प्रवेश दर्ज करें", "Record Visitor Check-In"))
                        }
                    }
                }
            }
        }

        // Date Filter & Count
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p10Label(
                                hi,
                                "आगंतुक सूची (${visitorsForDate.size})",
                                "Visitors on Date (${visitorsForDate.size})"
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val insideCount = visitorsForDate.count { it.isInside }
                        Text(
                            text = p10Label(
                                hi,
                                "अंदर: $insideCount • बाहर गए: ${visitorsForDate.size - insideCount}",
                                "Inside: $insideCount • Signed Out: ${visitorsForDate.size - insideCount}"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = filterDateIso,
                        onValueChange = { filterDateIso = it },
                        label = { Text(p10Label(hi, "तिथि", "Date")) },
                        singleLine = true,
                        modifier = Modifier.width(150.dp)
                    )
                }
            }
        }

        if (visitorsForDate.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = p10Label(
                            hi,
                            "इस तिथि पर कोई आगंतुक प्रविष्टि नहीं है।",
                            "No visitor entries recorded for this date."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(visitorsForDate, key = { it.id }) { visitor ->
                val isInside = visitor.status == VisitorStatus.IN
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("visitor_card_${visitor.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (visitor.photoBase64.isNotBlank()) {
                            val bmp = remember(visitor.photoBase64) {
                                ImageUtils.decodeBase64ToBitmap(visitor.photoBase64)
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = visitor.visitorName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = visitor.visitorName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = if (isInside) AbsentAmberContainer else PassGreenContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (hi) visitor.status.labelHi else visitor.status.labelEn,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isInside) AbsentAmber else PassGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${p10Label(hi, "उद्देश्य", "Purpose")}: ${visitor.purpose} • ${p10Label(hi, "मिलना", "Meet")}: ${visitor.whomToMeet}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            val timeSummary = if (visitor.timeOut.isNotBlank()) {
                                "In: ${visitor.timeIn} • Out: ${visitor.timeOut}"
                            } else {
                                "In: ${visitor.timeIn}"
                            }
                            Text(
                                text = "${visitor.mobile.ifBlank { "No phone" }} • $timeSummary",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isInside && uiState.canEditGateModule) {
                            Button(
                                onClick = {
                                    viewModel.signOutVisitorEntry(visitor) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("visitor_signout_btn_${visitor.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(hi, "बाहर गए (Sign Out)", "Sign Out"))
                            }
                        }
                    }
                }
            }
        }
    }
}
