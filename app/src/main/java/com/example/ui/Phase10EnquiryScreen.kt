package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.AdmissionEnquiryEngine
import com.example.domain.AdmissionEnquiryRecord
import com.example.domain.EnquiryFollowUpRow
import com.example.domain.EnquirySettingsConfig
import com.example.domain.EnquiryStatus
import com.example.domain.FollowUpUrgency
import com.example.domain.Phase10AccessEvaluator
import com.example.domain.Phase6DateUtils
import com.example.domain.PhoneNormalizer
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.domain.PhoneValidationResult

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Phase10EnquiryScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val isHindi = uiState.phase10HindiLabels
    val activeSchool = uiState.activeSchool
    val activeSchoolDocId = uiState.activeSchoolDocId
    val todayIso = remember { Phase6DateUtils.todayIso() }
    val canEdit = Phase10AccessEvaluator.canEditEnquiry(uiState.accessProfile, activeSchoolDocId) ||
        uiState.canEditEnquiry

    val settings = remember(uiState.enquirySettingsList, activeSchoolDocId) {
        uiState.enquirySettingsForSchool(activeSchoolDocId)
    }

    val availableClasses = remember(uiState.schoolClasses, activeSchool.id) {
        val fromSchool = uiState.classesForSchool(activeSchool.id).map { it.name }.distinct()
        fromSchool.ifEmpty { listOf("Nursery", "LKG", "UKG", "1", "2", "3", "4", "5", "6", "7", "8") }
    }

    val monthlySummary = remember(uiState.admissionEnquiries, activeSchoolDocId, todayIso) {
        AdmissionEnquiryEngine.computeMonthlySummary(
            enquiries = uiState.admissionEnquiries,
            schoolId = activeSchoolDocId,
            todayIso = todayIso
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Follow-up Today, 1 = All Enquiries
    var includeUpcoming by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<EnquiryStatus?>(null) }

    // Dialog states
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingEnquiry by remember { mutableStateOf<AdmissionEnquiryRecord?>(null) }
    var whatsappDialogTarget by remember { mutableStateOf<AdmissionEnquiryRecord?>(null) }
    var convertTarget by remember { mutableStateOf<AdmissionEnquiryRecord?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val followUpRows = remember(
        uiState.admissionEnquiries,
        activeSchoolDocId,
        activeSchool.name,
        todayIso,
        settings.whatsappTemplate,
        includeUpcoming
    ) {
        AdmissionEnquiryEngine.buildFollowUpQueue(
            enquiries = uiState.admissionEnquiries,
            schoolId = activeSchoolDocId,
            schoolName = activeSchool.name,
            todayIso = todayIso,
            template = settings.whatsappTemplate,
            includeUpcoming = includeUpcoming
        )
    }

    val allSchoolEnquiries = remember(uiState.admissionEnquiries, activeSchoolDocId, searchQuery, statusFilter) {
        val q = searchQuery.trim().lowercase()
        uiState.admissionEnquiries
            .filter { enq ->
                enq.schoolId == activeSchoolDocId &&
                    (statusFilter == null || enq.status == statusFilter) &&
                    (q.isEmpty() ||
                        enq.childName.lowercase().contains(q) ||
                        enq.guardianName.lowercase().contains(q) ||
                        enq.mobile.contains(q) ||
                        enq.classWanted.lowercase().contains(q) ||
                        enq.source.lowercase().contains(q))
            }
            .sortedByDescending { it.createdAtMs }
    }

    fun launchDialer(rawPhone: String) {
        val digits = when (val v = PhoneNormalizer.normalizeIndianMobile(rawPhone)) {
            is PhoneValidationResult.Valid -> v.e164WithPlus
            is PhoneValidationResult.Invalid -> rawPhone.trim()
        }
        if (digits.isNotBlank()) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
            context.startActivity(intent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("phase10_enquiry_screen")
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
                    modifier = Modifier.testTag("enquiry_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = p10Label(isHindi, "प्रवेश पूछताछ (Enquiry)", "Admission Enquiry"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeSchool.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canEdit) {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("enquiry_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Enquiry Settings")
                    }
                }
                Phase10LanguageToggleChip(
                    isHindi = isHindi,
                    onToggle = viewModel::togglePhase10HindiLabels
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Monthly Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("enquiry_monthly_summary_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStatColumn(
                    label = p10Label(isHindi, "इस माह पूछताछ", "This Month"),
                    value = "${monthlySummary.totalEnquiriesThisMonth}",
                    color = MaterialTheme.colorScheme.primary
                )
                SummaryStatColumn(
                    label = p10Label(isHindi, "प्रवेश (Admitted)", "Admitted"),
                    value = "${monthlySummary.admittedCount}",
                    color = Color(0xFF1B5E20)
                )
                SummaryStatColumn(
                    label = p10Label(isHindi, "रूपांतरण %", "Conversion"),
                    value = "${monthlySummary.conversionPercent}%",
                    color = MaterialTheme.colorScheme.tertiary
                )
                SummaryStatColumn(
                    label = p10Label(isHindi, "बाकी / आज", "Overdue/Today"),
                    value = "${monthlySummary.overdueCount}/${monthlySummary.dueTodayCount}",
                    color = if (monthlySummary.overdueCount > 0) Color(0xFFB71C1C) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (canEdit) {
            Button(
                onClick = {
                    editingEnquiry = null
                    showAddEditDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("enquiry_add_button")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(p10Label(isHindi, "नई प्रवेश पूछताछ जोड़ें (< 1 मिनट)", "Add New Admission Enquiry"), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        p10Label(isHindi, "आज का फॉलो-अप (${followUpRows.size})", "Follow Up Today (${followUpRows.size})"),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                modifier = Modifier.testTag("enquiry_tab_followup")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        p10Label(isHindi, "सभी पूछताछ (${allSchoolEnquiries.size})", "All Enquiries (${allSchoolEnquiries.size})"),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                modifier = Modifier.testTag("enquiry_tab_all")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedTab == 0) {
            // ======================== FOLLOW UP TODAY (OVERDUE FIRST) ========================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = p10Label(isHindi, "देरी वाले सबसे ऊपर (Overdue first)", "Overdue first, then Due Today"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = includeUpcoming,
                    onClick = { includeUpcoming = !includeUpcoming },
                    label = { Text(p10Label(isHindi, "आगामी दिखाएँ", "Show Upcoming")) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (followUpRows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = p10Label(isHindi, "आज के लिए कोई फॉलो-अप बाकी नहीं है!", "All caught up! No follow-ups due today."),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(followUpRows, key = { it.enquiry.id }) { row ->
                        EnquiryCard(
                            enquiry = row.enquiry,
                            urgencyBadge = row,
                            isHindi = isHindi,
                            canEdit = canEdit,
                            onWhatsAppClick = { whatsappDialogTarget = row.enquiry },
                            onCallClick = { launchDialer(row.enquiry.mobile) },
                            onEditClick = {
                                editingEnquiry = row.enquiry
                                showAddEditDialog = true
                            },
                            onConvertClick = { convertTarget = row.enquiry }
                        )
                    }
                }
            }
        } else {
            // ======================== ALL ENQUIRIES ========================
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(p10Label(isHindi, "बच्चे, अभिभावक या मोबाइल से खोजें", "Search child, guardian, mobile, or class")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("enquiry_search_input")
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text(p10Label(isHindi, "सभी", "All")) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                EnquiryStatus.entries.forEach { st ->
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = if (statusFilter == st) null else st },
                        label = { Text(if (isHindi) st.hindiLabel else st.englishLabel) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (allSchoolEnquiries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = p10Label(isHindi, "कोई पूछताछ नहीं मिली।", "No enquiries found."),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(allSchoolEnquiries, key = { it.id }) { enq ->
                        EnquiryCard(
                            enquiry = enq,
                            urgencyBadge = null,
                            isHindi = isHindi,
                            canEdit = canEdit,
                            onWhatsAppClick = { whatsappDialogTarget = enq },
                            onCallClick = { launchDialer(enq.mobile) },
                            onEditClick = {
                                editingEnquiry = enq
                                showAddEditDialog = true
                            },
                            onConvertClick = { convertTarget = enq }
                        )
                    }
                }
            }
        }
    }

    // Dialog 1: Add / Edit Enquiry (fast < 1 min form)
    if (showAddEditDialog) {
        EnquiryAddEditDialog(
            isHindi = isHindi,
            existing = editingEnquiry,
            availableClasses = availableClasses,
            sources = settings.sources,
            todayIso = todayIso,
            onDismiss = { showAddEditDialog = false },
            onSave = { child, cls, guardian, mobile, source, notes, nextDate, status, lostReason, cb ->
                viewModel.saveAdmissionEnquiry(
                    existing = editingEnquiry,
                    childName = child,
                    classWanted = cls,
                    guardianName = guardian,
                    mobile = mobile,
                    source = source,
                    notes = notes,
                    nextFollowUpDateIso = nextDate,
                    status = status,
                    lostReason = lostReason,
                    onResult = cb
                )
            }
        )
    }

    // Dialog 2: Send on WhatsApp (editable message preview -> wa.me link, never automatic)
    if (whatsappDialogTarget != null) {
        val enq = whatsappDialogTarget!!
        val phoneVal = remember(enq.mobile) { PhoneNormalizer.normalizeIndianMobile(enq.mobile) }
        var messageText by remember(enq.id) {
            mutableStateOf(
                AdmissionEnquiryEngine.fillEnquiryTemplate(
                    template = settings.whatsappTemplate,
                    guardianName = enq.guardianName,
                    childName = enq.childName,
                    classWanted = enq.classWanted,
                    schoolName = activeSchool.name
                )
            )
        }
        AlertDialog(
            onDismissRequest = { whatsappDialogTarget = null },
            title = { Text(p10Label(isHindi, "WhatsApp संदेश भेजें", "Send on WhatsApp")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${enq.guardianName} (${enq.childName} • Class ${enq.classWanted})",
                        fontWeight = FontWeight.Bold
                    )
                    when (phoneVal) {
                        is PhoneValidationResult.Valid -> {
                            Text(
                                text = "${p10Label(isHindi, "सत्यापित नंबर", "Verified number")}: ${phoneVal.e164WithPlus}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF1B5E20)
                            )
                        }
                        is PhoneValidationResult.Invalid -> {
                            Text(
                                text = "${p10Label(isHindi, "अमान्य मोबाइल नंबर", "Invalid mobile number")}: ${phoneVal.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text(p10Label(isHindi, "संदेश संपादित करें", "Edit message before opening WhatsApp")) },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enquiry_whatsapp_message_input")
                    )
                    Text(
                        text = p10Label(
                            isHindi,
                            "नोट: यह आपके फ़ोन पर WhatsApp चैट खोलेगा। संदेश अपने आप नहीं भेजा जाता।",
                            "Note: Opens WhatsApp chat on this phone via wa.me. Messages are never sent automatically."
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val valid = phoneVal as? PhoneValidationResult.Valid ?: return@Button
                        val waUrl = PhoneNormalizer.buildWaMeLink(valid.waMeDigits, messageText)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                        context.startActivity(intent)
                        whatsappDialogTarget = null
                    },
                    enabled = phoneVal is PhoneValidationResult.Valid && messageText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("enquiry_whatsapp_launch_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(p10Label(isHindi, "WhatsApp खोलें", "Open WhatsApp"))
                }
            },
            dismissButton = {
                TextButton(onClick = { whatsappDialogTarget = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(p10Label(isHindi, "बंद करें", "Cancel"))
                }
            }
        )
    }

    // Dialog 3: Convert to Admission -> creates student in Student Register & keeps link
    if (convertTarget != null) {
        val enq = convertTarget!!
        var admNoText by remember(enq.id) { mutableStateOf("") }
        var classText by remember(enq.id) { mutableStateOf(enq.classWanted.ifBlank { availableClasses.firstOrNull().orEmpty() }) }
        var sectionText by remember(enq.id) { mutableStateOf("A") }
        var rollNoText by remember(enq.id) { mutableStateOf("1") }
        var admDateText by remember(enq.id) { mutableStateOf(todayIso) }
        var convErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { convertTarget = null },
            icon = { Icon(Icons.Default.HowToReg, contentDescription = null, tint = Color(0xFF1B5E20)) },
            title = { Text(p10Label(isHindi, "प्रवेश में बदलें (Convert to Admission)", "Convert to Admission")) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "${enq.childName} (अभिभावक: ${enq.guardianName}) को Student Register में जोड़ें और लिंक सुरक्षित रखें।",
                            "Creates ${enq.childName} (Guardian: ${enq.guardianName}) in Student Register and keeps the link."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = admNoText,
                        onValueChange = {
                            admNoText = it
                            convErr = null
                        },
                        label = { Text(p10Label(isHindi, "प्रवेश संख्या (Admission No.) *", "Admission No. *")) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("enquiry_convert_admno_input")
                    )
                    OutlinedTextField(
                        value = classText,
                        onValueChange = { classText = it },
                        label = { Text(p10Label(isHindi, "कक्षा (Class)", "Class")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sectionText,
                            onValueChange = { sectionText = it.take(4) },
                            label = { Text(p10Label(isHindi, "सेक्शन", "Section")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = rollNoText,
                            onValueChange = { rollNoText = it.filter(Char::isDigit) },
                            label = { Text(p10Label(isHindi, "रोल नं.", "Roll No.")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = admDateText,
                        onValueChange = { admDateText = it },
                        label = { Text(p10Label(isHindi, "प्रवेश तिथि (YYYY-MM-DD)", "Admission Date (YYYY-MM-DD)")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (convErr != null) {
                        Text(convErr!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.convertEnquiryToStudent(
                            enquiry = enq,
                            admNo = admNoText,
                            className = classText,
                            section = sectionText.ifBlank { "A" },
                            rollNo = rollNoText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            admissionDateIso = admDateText
                        ) { ok, err ->
                            if (ok) convertTarget = null else convErr = err
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("enquiry_convert_confirm_button")
                ) {
                    Text(p10Label(isHindi, "प्रवेश बनाएँ", "Create Student"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { convertTarget = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(p10Label(isHindi, "रद्द", "Cancel"))
                }
            }
        )
    }

    // Dialog 4: Enquiry Settings (Editable Sources + WhatsApp Template)
    if (showSettingsDialog) {
        EnquirySettingsDialog(
            isHindi = isHindi,
            settings = settings,
            onDismiss = { showSettingsDialog = false },
            onSave = { srcs, tpl ->
                viewModel.saveEnquirySettings(srcs, tpl) { ok, _ ->
                    if (ok) showSettingsDialog = false
                }
            }
        )
    }
}

@Composable
private fun SummaryStatColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnquiryCard(
    enquiry: AdmissionEnquiryRecord,
    urgencyBadge: EnquiryFollowUpRow?,
    isHindi: Boolean,
    canEdit: Boolean,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onEditClick: () -> Unit,
    onConvertClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("enquiry_card_${enquiry.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${enquiry.childName} • Class ${enquiry.classWanted.ifBlank { "—" }}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${p10Label(isHindi, "अभिभावक", "Guardian")}: ${enquiry.guardianName} • ${enquiry.mobile}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status or Urgency chip
                val (badgeBg, badgeFg, badgeLabel) = when {
                    urgencyBadge?.urgency == FollowUpUrgency.OVERDUE -> Triple(
                        Color(0xFFFFEBEE),
                        Color(0xFFB71C1C),
                        p10Label(isHindi, "${urgencyBadge.daysOverdue} दिन देरी", "Overdue ${urgencyBadge.daysOverdue}d")
                    )
                    urgencyBadge?.urgency == FollowUpUrgency.DUE_TODAY -> Triple(
                        Color(0xFFFFF8E1),
                        Color(0xFFE65100),
                        p10Label(isHindi, "आज फॉलो-अप", "Due Today")
                    )
                    enquiry.status == EnquiryStatus.ADMITTED -> Triple(
                        Color(0xFFE8F5E9),
                        Color(0xFF1B5E20),
                        if (isHindi) enquiry.status.hindiLabel else enquiry.status.englishLabel
                    )
                    enquiry.status == EnquiryStatus.LOST -> Triple(
                        Color(0xFFFFEBEE),
                        Color(0xFFB71C1C),
                        if (isHindi) enquiry.status.hindiLabel else enquiry.status.englishLabel
                    )
                    else -> Triple(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        if (isHindi) enquiry.status.hindiLabel else enquiry.status.englishLabel
                    )
                }
                Surface(color = badgeBg, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "${p10Label(isHindi, "स्रोत", "Source")}: ${enquiry.source} • ${p10Label(isHindi, "अगला फॉलो-अप", "Next Follow-up")}: ${Phase6DateUtils.formatDisplayDate(enquiry.nextFollowUpDateIso)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (enquiry.notes.isNotBlank()) {
                Text(enquiry.notes, style = MaterialTheme.typography.bodySmall)
            }

            if (enquiry.status == EnquiryStatus.LOST && enquiry.lostReason.isNotBlank()) {
                Text(
                    text = "${p10Label(isHindi, "रद्द कारण", "Lost reason")}: ${enquiry.lostReason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB71C1C)
                )
            }

            if (enquiry.isConverted) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1B5E20), modifier = Modifier.size(16.dp))
                        Text(
                            text = p10Label(
                                isHindi,
                                "Student Register से जुड़ा • Adm No: ${enquiry.convertedStudentAdmNo}",
                                "Linked in Student Register • Adm No: ${enquiry.convertedStudentAdmNo}"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }
            }

            HorizontalDivider()

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = onWhatsAppClick,
                    label = { Text(p10Label(isHindi, "WhatsApp भेजें", "Send on WhatsApp")) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color(0xFF1B5E20), modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("enquiry_whatsapp_button_${enquiry.id}")
                )
                AssistChip(
                    onClick = onCallClick,
                    label = { Text(p10Label(isHindi, "कॉल करें", "Call")) },
                    leadingIcon = { Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("enquiry_call_button_${enquiry.id}")
                )
                if (canEdit) {
                    AssistChip(
                        onClick = onEditClick,
                        label = { Text(p10Label(isHindi, "स्थिति / संपादित", "Status / Edit")) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("enquiry_edit_button_${enquiry.id}")
                    )
                    if (!enquiry.isConverted) {
                        AssistChip(
                            onClick = onConvertClick,
                            label = { Text(p10Label(isHindi, "प्रवेश में बदलें", "Convert to Admission")) },
                            leadingIcon = { Icon(Icons.Default.HowToReg, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("enquiry_convert_button_${enquiry.id}")
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnquiryAddEditDialog(
    isHindi: Boolean,
    existing: AdmissionEnquiryRecord?,
    availableClasses: List<String>,
    sources: List<String>,
    todayIso: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, EnquiryStatus, String, (Boolean, String?) -> Unit) -> Unit
) {
    var childName by remember { mutableStateOf(existing?.childName.orEmpty()) }
    var classWanted by remember { mutableStateOf(existing?.classWanted.orEmpty().ifBlank { availableClasses.firstOrNull().orEmpty() }) }
    var guardianName by remember { mutableStateOf(existing?.guardianName.orEmpty()) }
    var mobile by remember { mutableStateOf(existing?.mobile.orEmpty()) }
    var source by remember { mutableStateOf(existing?.source.orEmpty().ifBlank { sources.firstOrNull() ?: "Walk-in" }) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var nextFollowUp by remember { mutableStateOf(existing?.nextFollowUpDateIso.orEmpty().ifBlank { Phase6DateUtils.addDays(todayIso, 1) }) }
    var status by remember { mutableStateOf(existing?.status ?: EnquiryStatus.NEW) }
    var lostReason by remember { mutableStateOf(existing?.lostReason.orEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val phoneCheck = remember(mobile) { PhoneNormalizer.normalizeIndianMobile(mobile) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) {
                    p10Label(isHindi, "नई प्रवेश पूछताछ", "New Admission Enquiry")
                } else {
                    p10Label(isHindi, "पूछताछ अपडेट करें", "Update Enquiry")
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = childName,
                    onValueChange = {
                        childName = it
                        errorMsg = null
                    },
                    label = { Text(p10Label(isHindi, "बच्चे का नाम *", "Child Name *")) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enquiry_child_name_input")
                )

                Text(
                    text = p10Label(isHindi, "कक्षा (Class Wanted)", "Class Wanted"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    availableClasses.forEach { cls ->
                        FilterChip(
                            selected = classWanted.equals(cls, ignoreCase = true),
                            onClick = { classWanted = cls },
                            label = { Text(cls) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = guardianName,
                    onValueChange = {
                        guardianName = it
                        errorMsg = null
                    },
                    label = { Text(p10Label(isHindi, "अभिभावक का नाम *", "Guardian Name *")) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enquiry_guardian_name_input")
                )

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text(p10Label(isHindi, "मोबाइल नंबर (10 अंक)", "Mobile Number (10 digits)")) },
                    supportingText = {
                        if (mobile.isNotBlank()) {
                            when (phoneCheck) {
                                is PhoneValidationResult.Valid -> Text("WhatsApp ready: ${phoneCheck.e164WithPlus}", color = Color(0xFF1B5E20))
                                is PhoneValidationResult.Invalid -> Text(phoneCheck.reason, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enquiry_mobile_input")
                )

                Text(
                    text = p10Label(isHindi, "स्रोत (Source)", "Enquiry Source"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sources.forEach { src ->
                        FilterChip(
                            selected = source == src,
                            onClick = { source = src },
                            label = { Text(src) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                // Quick Follow-up date chips
                Text(
                    text = p10Label(isHindi, "अगला फॉलो-अप तिथि", "Next Follow-up Date"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val quickDates = listOf(
                        p10Label(isHindi, "आज", "Today") to todayIso,
                        p10Label(isHindi, "कल", "Tomorrow") to Phase6DateUtils.addDays(todayIso, 1),
                        "+3d" to Phase6DateUtils.addDays(todayIso, 3),
                        "+7d" to Phase6DateUtils.addDays(todayIso, 7)
                    )
                    quickDates.forEach { (lbl, iso) ->
                        FilterChip(
                            selected = nextFollowUp == iso,
                            onClick = { nextFollowUp = iso },
                            label = { Text(lbl) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = nextFollowUp,
                    onValueChange = { nextFollowUp = it },
                    label = { Text(p10Label(isHindi, "फॉलो-अप तिथि (YYYY-MM-DD)", "Follow-up Date (YYYY-MM-DD)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Status selector
                if (existing != null && !existing.isConverted) {
                    Text(
                        text = p10Label(isHindi, "स्थिति (Status)", "Status"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(EnquiryStatus.NEW, EnquiryStatus.CONTACTED, EnquiryStatus.VISIT_PLANNED, EnquiryStatus.LOST).forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(if (isHindi) st.hindiLabel else st.englishLabel) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                    if (status == EnquiryStatus.LOST) {
                        OutlinedTextField(
                            value = lostReason,
                            onValueChange = {
                                lostReason = it
                                errorMsg = null
                            },
                            label = { Text(p10Label(isHindi, "नहीं आने का कारण (अनिवार्य)", "Lost reason (required)")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enquiry_lost_reason_input")
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(p10Label(isHindi, "नोट्स (वैकल्पिक)", "Notes (optional)")) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(childName, classWanted, guardianName, mobile, source, notes, nextFollowUp, status, lostReason) { ok, err ->
                        if (ok) onDismiss() else errorMsg = err
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("enquiry_save_button")
            ) {
                Text(p10Label(isHindi, "सेव करें", "Save"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(p10Label(isHindi, "रद्द", "Cancel"))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnquirySettingsDialog(
    isHindi: Boolean,
    settings: EnquirySettingsConfig,
    onDismiss: () -> Unit,
    onSave: (List<String>, String) -> Unit
) {
    var sourceList by remember(settings.sources) { mutableStateOf(settings.sources) }
    var newSource by remember { mutableStateOf("") }
    var templateText by remember(settings.whatsappTemplate) { mutableStateOf(settings.whatsappTemplate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "पूछताछ सेटिंग्स", "Enquiry Settings")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(p10Label(isHindi, "पूछताछ स्रोत (Sources)", "Enquiry Sources"), fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sourceList.forEach { src ->
                        AssistChip(
                            onClick = {
                                if (sourceList.size > 1) sourceList = sourceList.filterNot { it == src }
                            },
                            label = { Text(src) },
                            trailingIcon = if (sourceList.size > 1) {
                                { Icon(Icons.Default.Close, contentDescription = "Remove $src", modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSource,
                        onValueChange = { newSource = it },
                        label = { Text(p10Label(isHindi, "नया स्रोत", "New source")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val clean = newSource.trim()
                            if (clean.isNotEmpty() && sourceList.none { it.equals(clean, ignoreCase = true) }) {
                                sourceList = sourceList + clean
                                newSource = ""
                            }
                        },
                        modifier = Modifier.heightIn(min = 50.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }

                OutlinedTextField(
                    value = templateText,
                    onValueChange = { templateText = it },
                    label = { Text(p10Label(isHindi, "WhatsApp टेम्पलेट", "WhatsApp Message Template")) },
                    supportingText = { Text("Placeholders: {guardian}, {child}, {class}, {school}") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(sourceList, templateText) },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "सेव करें", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(p10Label(isHindi, "रद्द", "Cancel"))
            }
        }
    )
}
