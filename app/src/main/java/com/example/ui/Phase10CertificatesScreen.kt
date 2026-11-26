package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.StudentEntity
import com.example.domain.CertificateEngine
import com.example.domain.CertificateType
import com.example.domain.Phase10AccessEvaluator
import com.example.domain.Phase6DateUtils
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.domain.SchoolCertificateRecord

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Phase10CertificatesScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    initialStudentId: Long? = null,
    onInitialStudentConsumed: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()
    val isHindi = uiState.phase10HindiLabels
    val activeSchool = uiState.activeSchool
    val activeSchoolDocId = uiState.activeSchoolDocId
    val session = uiState.filter.session
    val canEdit = Phase10AccessEvaluator.canEditCertificates(uiState.accessProfile, activeSchoolDocId) ||
        uiState.canEditCertificates

    val schoolStudents = remember(uiState.allStudents, activeSchool.id, session) {
        uiState.allStudents
            .filter { it.schoolId == activeSchool.id && it.session.equals(session, ignoreCase = true) }
            .sortedWith(compareBy<StudentEntity>({ !it.isActiveStatus }, { it.className }, { it.section }, { it.rollNo }, { it.name }))
    }

    // Tabs: 0 = Prepare (if canEdit), 1 = Register (view-only users only see Register)
    var selectedTab by remember(canEdit) { mutableIntStateOf(if (canEdit) 0 else 1) }

    // Prepare form state
    var selectedStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var studentSearchQuery by remember { mutableStateOf("") }
    var selectedCertType by remember { mutableStateOf(CertificateType.TC) }
    var issueDateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }
    var reasonForLeaving by remember { mutableStateOf(CertificateEngine.DEFAULT_LEAVING_REASONS.first()) }
    var lastAttendanceDateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }
    var conduct by remember { mutableStateOf("Good") }
    var promotionStatus by remember { mutableStateOf("Promoted to next class") }
    var feeDuesStatus by remember { mutableStateOf("All school dues cleared") }
    var remarks by remember { mutableStateOf("") }
    var reissuingFromCert by remember { mutableStateOf<SchoolCertificateRecord?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var isIssuing by remember { mutableStateOf(false) }

    // Offer to mark student left after TC
    var markLeftPromptTarget by remember { mutableStateOf<Pair<StudentEntity, SchoolCertificateRecord>?>(null) }
    var markLeftDateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }
    var markLeftReason by remember { mutableStateOf("") }

    // Register filter & cancel dialog state
    var registerSearch by remember { mutableStateOf("") }
    var registerTypeFilter by remember { mutableStateOf<CertificateType?>(null) }
    var includeCancelled by remember { mutableStateOf(true) }
    var cancelTargetCert by remember { mutableStateOf<SchoolCertificateRecord?>(null) }
    var cancelReasonText by remember { mutableStateOf("") }
    var cancelError by remember { mutableStateOf<String?>(null) }

    // Pre-select student if jumped from Student Register
    LaunchedEffect(initialStudentId, schoolStudents) {
        if (initialStudentId != null) {
            val found = schoolStudents.firstOrNull { it.id == initialStudentId }
            if (found != null) {
                selectedStudent = found
                if (canEdit) selectedTab = 0
                onInitialStudentConsumed()
            }
        }
    }

    val registerList = remember(
        uiState.schoolCertificates,
        activeSchoolDocId,
        session,
        registerTypeFilter,
        includeCancelled,
        registerSearch
    ) {
        CertificateEngine.filterRegister(
            certificates = uiState.schoolCertificates,
            schoolId = activeSchoolDocId,
            session = session,
            typeFilter = registerTypeFilter,
            includeCancelled = includeCancelled,
            searchQuery = registerSearch
        )
    }

    val nextPreviewNumber = remember(
        activeSchoolDocId,
        session,
        selectedCertType,
        uiState.deviceNodeCode,
        uiState.schoolCertificates
    ) {
        CertificateEngine.generateNextCertificateNumber(
            schoolId = activeSchoolDocId,
            session = session,
            certificateType = selectedCertType,
            deviceNodeCode = uiState.deviceNodeCode,
            existingCertificates = uiState.schoolCertificates
        ).first
    }

    if (pdfState.isGenerating || pdfState.generatedFile != null || pdfState.error != null) {
        TimetablePdfPreviewDialog(
            pdfState = pdfState,
            onSavePdfToUri = viewModel::saveTimetablePdfToUri,
            onSaveZipToUri = viewModel::saveTimetableZipToUri,
            onDismiss = viewModel::clearTimetablePdfPreview
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("phase10_certificates_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("cert_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = p10Label(isHindi, "प्रमाण पत्र (Certificates)", "Certificates"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeSchool.name} • $session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Phase10LanguageToggleChip(
                isHindi = isHindi,
                onToggle = viewModel::togglePhase10HindiLabels
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Tabs
        if (canEdit) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            p10Label(isHindi, "प्रमाण पत्र बनाएँ", "Prepare Certificate"),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier.testTag("cert_tab_prepare")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            p10Label(isHindi, "रजिस्टर (${registerList.size})", "Register (${registerList.size})"),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier.testTag("cert_tab_register")
                )
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = p10Label(
                        isHindi,
                        "केवल देखने की अनुमति — आप जारी प्रमाण पत्र देख और PDF प्रिंट कर सकते हैं।",
                        "View-only access — you can view the register and print/share A4 PDFs."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTab == 0 && canEdit) {
            // ======================== PREPARE TAB ========================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (reissuingFromCert != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p10Label(
                                        isHindi,
                                        "पुनः जारी (Re-issue): ${reissuingFromCert!!.certificateNumber}",
                                        "Re-issuing cancelled cert: ${reissuingFromCert!!.certificateNumber}"
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = p10Label(
                                        isHindi,
                                        "नया प्रमाण पत्र नंबर अपने आप बनेगा और पुराने से जुड़ा रहेगा।",
                                        "A new unique number will be generated and linked to the cancelled certificate."
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { reissuingFromCert = null }) {
                                Text(p10Label(isHindi, "हटाएँ", "Clear"))
                            }
                        }
                    }
                }

                // Step 1: Certificate Type (3 big 48dp+ buttons)
                Text(
                    text = p10Label(isHindi, "1. प्रमाण पत्र चुनें", "1. Choose Certificate Type"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CertificateType.entries.forEach { type ->
                        val active = selectedCertType == type
                        val labelText = when (type) {
                            CertificateType.TC -> p10Label(isHindi, "TC\nस्थानांतरण", "TC\nTransfer")
                            CertificateType.BONAFIDE -> p10Label(isHindi, "Bonafide\nअध्ययन", "Bonafide\nStudy")
                            CertificateType.CHARACTER -> p10Label(isHindi, "Character\nचरित्र", "Character\nConduct")
                        }
                        Surface(
                            onClick = {
                                selectedCertType = type
                                formError = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 64.dp)
                                .testTag("cert_type_${type.wireValue.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Running Number preview chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(isHindi, "अगला प्रमाण पत्र नंबर (ऑफ़लाइन-सुरक्षित)", "Next Certificate No. (Offline-safe)"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = nextPreviewNumber,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Step 2: Select Student from Student Register
                Text(
                    text = p10Label(isHindi, "2. छात्र चुनें (Student Register से)", "2. Select Student (from Student Register)"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (selectedStudent != null) {
                    val stu = selectedStudent!!
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cert_selected_student_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stu.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Adm: ${stu.admNo} • Class ${stu.className}-${stu.section} • Roll ${stu.rollNo}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${p10Label(isHindi, "पिता", "Father")}: ${stu.fatherName.ifBlank { "—" }} • DOB: ${stu.dob.ifBlank { "—" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { selectedStudent = null },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("cert_change_student_button")
                            ) {
                                Text(p10Label(isHindi, "बदलें", "Change"))
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = studentSearchQuery,
                        onValueChange = { studentSearchQuery = it },
                        label = {
                            Text(p10Label(isHindi, "छात्र का नाम, Adm No या कक्षा खोजें", "Search student name, Adm No, or class"))
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cert_student_search_input")
                    )
                    val matchingStudents = remember(schoolStudents, studentSearchQuery) {
                        val q = studentSearchQuery.trim()
                        if (q.isEmpty()) schoolStudents.take(15)
                        else schoolStudents.filter {
                            it.name.contains(q, ignoreCase = true) ||
                                it.admNo.contains(q, ignoreCase = true) ||
                                it.className.contains(q, ignoreCase = true) ||
                                it.fatherName.contains(q, ignoreCase = true)
                        }.take(20)
                    }
                    if (matchingStudents.isEmpty()) {
                        Text(
                            text = p10Label(isHindi, "कोई छात्र नहीं मिला। पहले Student Register में छात्र जोड़ें।", "No matching students found in Student Register."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            matchingStudents.forEach { stu ->
                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedStudent = stu
                                            formError = null
                                        }
                                        .testTag("cert_pick_student_${stu.admNo}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stu.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Adm ${stu.admNo} • ${stu.className}-${stu.section} • ${stu.fatherName.ifBlank { "—" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!stu.isActiveStatus) {
                                            Surface(
                                                color = Color(0xFFFFEBEE),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = p10Label(isHindi, "छोड़ दिया", "Left"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFB71C1C),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 3: Review / Edit Fields
                if (selectedStudent != null) {
                    Text(
                        text = p10Label(isHindi, "3. विवरण जाँचें / संपादित करें", "3. Review / Edit Details"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = issueDateIso,
                        onValueChange = { issueDateIso = it },
                        label = { Text(p10Label(isHindi, "जारी तिथि (YYYY-MM-DD)", "Issue Date (YYYY-MM-DD)")) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cert_issue_date_input")
                    )

                    if (selectedCertType == CertificateType.TC) {
                        OutlinedTextField(
                            value = reasonForLeaving,
                            onValueChange = { reasonForLeaving = it },
                            label = { Text(p10Label(isHindi, "विद्यालय छोड़ने का कारण", "Reason for Leaving")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cert_reason_input")
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CertificateEngine.DEFAULT_LEAVING_REASONS.forEach { preset ->
                                FilterChip(
                                    selected = reasonForLeaving == preset,
                                    onClick = { reasonForLeaving = preset },
                                    label = { Text(preset, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = lastAttendanceDateIso,
                            onValueChange = { lastAttendanceDateIso = it },
                            label = { Text(p10Label(isHindi, "अंतिम उपस्थिति तिथि (YYYY-MM-DD)", "Last Attendance Date (YYYY-MM-DD)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = promotionStatus,
                            onValueChange = { promotionStatus = it },
                            label = { Text(p10Label(isHindi, "पदोन्नति स्थिति (Promotion Status)", "Promotion Status")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = feeDuesStatus,
                            onValueChange = { feeDuesStatus = it },
                            label = { Text(p10Label(isHindi, "फीस बकाया स्थिति (Fee Dues Status)", "Fee Dues Status")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Conduct chips (TC & Character)
                    if (selectedCertType == CertificateType.TC || selectedCertType == CertificateType.CHARACTER) {
                        Text(
                            text = p10Label(isHindi, "आचरण / चरित्र (Conduct)", "Conduct & Character"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CertificateEngine.DEFAULT_CONDUCT_OPTIONS.forEach { option ->
                                FilterChip(
                                    selected = conduct.equals(option, ignoreCase = true),
                                    onClick = { conduct = option },
                                    label = { Text(option) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = conduct,
                            onValueChange = { conduct = it },
                            label = { Text(p10Label(isHindi, "आचरण लिखें", "Conduct")) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cert_conduct_input")
                        )
                    }

                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text(p10Label(isHindi, "टिप्पणी / विशेष विवरण (वैकल्पिक)", "Remarks (Optional)")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cert_remarks_input")
                    )

                    if (formError != null) {
                        Text(
                            text = formError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "नोट: जारी होने के बाद प्रमाण पत्र संपादित नहीं किया जा सकता। गलती होने पर कारण लिखकर रद्द (Cancel) और पुनः जारी (Re-issue) करें।",
                                "Note: Certificates are locked after issue. If there is a mistake, cancel with a reason and re-issue."
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Button(
                        onClick = {
                            val stu = selectedStudent ?: return@Button
                            if (selectedCertType == CertificateType.TC && reasonForLeaving.trim().isEmpty()) {
                                formError = p10Label(isHindi, "कृपया विद्यालय छोड़ने का कारण लिखें।", "Please enter reason for leaving.")
                                return@Button
                            }
                            isIssuing = true
                            formError = null
                            viewModel.issueSchoolCertificate(
                                student = stu,
                                certificateType = selectedCertType,
                                issueDateIso = issueDateIso,
                                reasonForLeaving = reasonForLeaving,
                                lastAttendanceDateIso = lastAttendanceDateIso,
                                conduct = conduct,
                                promotionStatus = promotionStatus,
                                feeDuesStatus = feeDuesStatus,
                                remarks = remarks,
                                reissuedFromCert = reissuingFromCert
                            ) { ok, err, savedCert ->
                                isIssuing = false
                                if (ok && savedCert != null) {
                                    reissuingFromCert = null
                                    // Immediately generate A4 PDF preview (with print/save/share)
                                    viewModel.generateCertificateA4Pdf(savedCert)
                                    // If TC and student is still Active, offer to mark as Left
                                    if (savedCert.certificateType == CertificateType.TC && stu.isActiveStatus) {
                                        markLeftDateIso = savedCert.issueDateIso.ifBlank { Phase6DateUtils.todayIso() }
                                        markLeftReason = savedCert.reasonForLeaving.ifBlank { "TC ${savedCert.certificateNumber} issued" }
                                        markLeftPromptTarget = stu to savedCert
                                    }
                                    selectedTab = 1
                                } else {
                                    formError = err
                                }
                            }
                        },
                        enabled = !isIssuing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("cert_issue_and_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isIssuing) {
                                p10Label(isHindi, "जारी हो रहा है...", "Issuing...")
                            } else {
                                p10Label(isHindi, "जारी करें और A4 PDF बनाएँ", "Issue & Generate A4 PDF")
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            // ======================== REGISTER TAB ========================
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = registerSearch,
                    onValueChange = { registerSearch = it },
                    label = { Text(p10Label(isHindi, "नंबर, छात्र या जारीकर्ता खोजें", "Search number, student, or issuer")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cert_register_search_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = registerTypeFilter == null,
                        onClick = { registerTypeFilter = null },
                        label = { Text(p10Label(isHindi, "सभी", "All")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    CertificateType.entries.forEach { t ->
                        FilterChip(
                            selected = registerTypeFilter == t,
                            onClick = { registerTypeFilter = if (registerTypeFilter == t) null else t },
                            label = { Text(t.shortLabel) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                    FilterChip(
                        selected = includeCancelled,
                        onClick = { includeCancelled = !includeCancelled },
                        label = { Text(p10Label(isHindi, "रद्द दिखाएँ", "Show Cancelled")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (registerList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = p10Label(isHindi, "अभी कोई प्रमाण पत्र जारी नहीं हुआ है।", "No certificates issued yet."),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(registerList, key = { it.id }) { cert ->
                            CertificateRegisterCard(
                                cert = cert,
                                isHindi = isHindi,
                                canEdit = canEdit,
                                deletedUserUids = uiState.deletedUserUids,
                                onOpenPdf = { viewModel.generateCertificateA4Pdf(cert) },
                                onCancelClick = {
                                    cancelTargetCert = cert
                                    cancelReasonText = ""
                                    cancelError = null
                                },
                                onReissueClick = {
                                    val stu = schoolStudents.firstOrNull {
                                        uiState.studentDocIdFor(it.id) == cert.studentId ||
                                            it.admNo.equals(cert.studentAdmNo, ignoreCase = true)
                                    }
                                    selectedStudent = stu
                                    selectedCertType = cert.certificateType
                                    issueDateIso = Phase6DateUtils.todayIso()
                                    reasonForLeaving = cert.reasonForLeaving
                                    lastAttendanceDateIso = cert.lastAttendanceDateIso
                                    conduct = cert.conduct
                                    promotionStatus = cert.promotionStatus
                                    feeDuesStatus = cert.feeDuesStatus
                                    remarks = cert.remarks
                                    reissuingFromCert = cert
                                    selectedTab = 0
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog 1: Cancel Certificate with mandatory reason
    if (cancelTargetCert != null) {
        val cert = cancelTargetCert!!
        AlertDialog(
            onDismissRequest = { cancelTargetCert = null },
            icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(p10Label(isHindi, "प्रमाण पत्र रद्द करें?", "Cancel Certificate?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${cert.certificateNumber} — ${cert.studentName} (${cert.certificateType.shortLabel})",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = p10Label(
                            isHindi,
                            "प्रमाण पत्र हटाया नहीं जाएगा। यह रजिस्टर में 'रद्द (Cancelled)' दिखेगा और आप नया प्रमाण पत्र पुनः जारी कर सकेंगे।",
                            "The certificate will remain in the register marked Cancelled, and you can then Re-issue a new one."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = cancelReasonText,
                        onValueChange = {
                            cancelReasonText = it
                            cancelError = null
                        },
                        label = { Text(p10Label(isHindi, "रद्द करने का कारण (अनिवार्य)", "Reason for cancellation (required)")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cert_cancel_reason_input")
                    )
                    if (cancelError != null) {
                        Text(cancelError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelSchoolCertificate(cert, cancelReasonText) { ok, err ->
                            if (ok) cancelTargetCert = null else cancelError = err
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cert_confirm_cancel_button")
                ) {
                    Text(p10Label(isHindi, "रद्द करें", "Cancel Certificate"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { cancelTargetCert = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(isHindi, "वापस", "Close"))
                }
            }
        )
    }

    // Dialog 2: Offer to mark student as "Left" after issuing TC
    if (markLeftPromptTarget != null) {
        val (stu, cert) = markLeftPromptTarget!!
        AlertDialog(
            onDismissRequest = { markLeftPromptTarget = null },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(p10Label(isHindi, "छात्र को 'विद्यालय छोड़ा (Left)' चिह्नित करें?", "Mark Student as Left School?"))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "${stu.name} के लिए TC (${cert.certificateNumber}) जारी हो गया है। क्या आप Student Register में इन्हें 'Left' मार्क करना चाहते हैं?",
                            "TC (${cert.certificateNumber}) has been issued for ${stu.name}. Would you like to mark them as 'Left' in the Student Register?"
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = markLeftDateIso,
                        onValueChange = { markLeftDateIso = it },
                        label = { Text(p10Label(isHindi, "छोड़ने की तिथि (YYYY-MM-DD)", "Left Date (YYYY-MM-DD)")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = markLeftReason,
                        onValueChange = { markLeftReason = it },
                        label = { Text(p10Label(isHindi, "कारण", "Reason")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.markStudentLeftAfterTc(stu, markLeftDateIso, markLeftReason) { _, _ ->
                            markLeftPromptTarget = null
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cert_mark_student_left_confirm")
                ) {
                    Text(p10Label(isHindi, "हाँ, Left मार्क करें", "Yes, Mark Left"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { markLeftPromptTarget = null },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cert_mark_student_left_skip")
                ) {
                    Text(p10Label(isHindi, "अभी नहीं", "Not Now"))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CertificateRegisterCard(
    cert: SchoolCertificateRecord,
    isHindi: Boolean,
    canEdit: Boolean,
    deletedUserUids: Set<String> = emptySet(),
    onOpenPdf: () -> Unit,
    onCancelClick: () -> Unit,
    onReissueClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cert_register_card_${cert.certificateNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = cert.certificateType.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = cert.certificateNumber,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (cert.isCancelled) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
                Surface(
                    color = if (cert.isCancelled) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (cert.isCancelled) {
                            p10Label(isHindi, "रद्द", "CANCELLED")
                        } else {
                            p10Label(isHindi, "जारी", "ISSUED")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (cert.isCancelled) Color(0xFFB71C1C) else Color(0xFF1B5E20),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "${cert.studentName} • Adm ${cert.studentAdmNo} • Class ${cert.className}-${cert.section}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            val issuedByLabel = com.example.domain.AuthorDisplayHelper.resolveAuthorName(
                authorUid = cert.issuedByUid,
                storedName = cert.issuedByName,
                deletedUserUids = deletedUserUids
            )
            Text(
                text = "${p10Label(isHindi, "तिथि", "Date")}: ${Phase6DateUtils.formatDisplayDate(cert.issueDateIso)} • ${p10Label(isHindi, "जारीकर्ता", "Issued by")}: $issuedByLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (cert.reissuedFromCertNumber.isNotBlank()) {
                Text(
                    text = "${p10Label(isHindi, "पुनः जारी मूल नंबर", "Re-issued from")}: ${cert.reissuedFromCertNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (cert.isCancelled) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${p10Label(isHindi, "रद्द कारण", "Cancel Reason")}: ${cert.cancelReason} (${cert.cancelledByName})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB71C1C),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            HorizontalDivider()

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = onOpenPdf,
                    label = { Text(p10Label(isHindi, "A4 PDF देखें / प्रिंट", "A4 PDF / Print / Share")) },
                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cert_pdf_button_${cert.certificateNumber}")
                )
                if (canEdit && !cert.isCancelled) {
                    AssistChip(
                        onClick = onCancelClick,
                        label = { Text(p10Label(isHindi, "रद्द करें", "Cancel")) },
                        leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("cert_cancel_button_${cert.certificateNumber}")
                    )
                }
                if (canEdit && cert.isCancelled) {
                    AssistChip(
                        onClick = onReissueClick,
                        label = { Text(p10Label(isHindi, "पुनः जारी करें", "Re-issue")) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("cert_reissue_button_${cert.certificateNumber}")
                    )
                }
            }
        }
    }
}
