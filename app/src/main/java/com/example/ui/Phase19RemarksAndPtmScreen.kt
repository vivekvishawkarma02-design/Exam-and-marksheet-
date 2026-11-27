package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.StudentEntity
import com.example.domain.AuthorDisplayHelper
import com.example.domain.DefaultRemarkCategory
import com.example.domain.ParentMeetingRecord
import com.example.domain.Phase19RemarksPtmAndGateEngine
import com.example.domain.Phase6DateUtils
import com.example.domain.PtmCallStatus
import com.example.domain.PtmStudentCallEntry
import com.example.domain.SchoolRemarkCategoryConfig
import com.example.domain.StudentRemarkRecord
import com.example.domain.StudentRemarkStatus
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =============================================================================
// PHASE 19: STUDENT REMARKS & PARENT MEETINGS (PTM) SCREEN
// =============================================================================

private enum class RemarksAndPtmTab(val en: String, val hi: String) {
    REMARKS("Student Remarks", "छात्र टिप्पणियाँ"),
    FOLLOW_UPS("Follow-ups Due", "बकाया फॉलो-अप"),
    PTM("Parent Meetings (PTM)", "अभिभावक बैठक (PTM)")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Phase19RemarksAndPtmScreen(
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val hi = uiState.phase10HindiLabels

    if (!uiState.canOpenStudentRemarksModule) {
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
                        "छात्र टिप्पणियाँ (Student Remarks) देखने की अनुमति नहीं है",
                        "No access to Student Remarks & Parent Meetings"
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = p10Label(
                        hi,
                        "टिप्पणियाँ संवेदनशील होती हैं। केवल कक्षा शिक्षक (Class Teacher), स्कूल स्वामी (Owner) और एडमिन या जिन्हें अनुमति दी गई है वे ही इन्हें देख सकते हैं।",
                        "Remarks are sensitive: by default only the student's Class Teacher, Owner, and Admins can read remarks unless explicitly granted."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

    val categoryConfig = remember(uiState.remarkCategoryConfigs, activeSchoolDocId) {
        uiState.remarkCategoryConfigForSchool(activeSchoolDocId)
    }
    val allCategories = remember(categoryConfig) {
        categoryConfig.effectiveCategories
    }

    // Classes visible to the current actor for remarks
    val allSchoolClasses = remember(uiState.schoolClasses, activeSchoolLongId) {
        val raw = uiState.classesForSchool(activeSchoolLongId).map { it.name.trim() }.filter { it.isNotBlank() }.distinct()
        raw.ifEmpty { listOf("1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th", "9th", "10th") }
    }
    val visibleClasses = remember(
        allSchoolClasses,
        uiState.allStudents,
        activeSchoolDocId,
        activeSchoolLongId,
        uiState.accessProfile,
        uiState.effectiveOwnTeacherScope
    ) {
        allSchoolClasses.filter { cls ->
            val sections = uiState.allStudents
                .filter { it.schoolId == activeSchoolLongId && it.className.trim().equals(cls, ignoreCase = true) && it.isActiveStatus }
                .map { it.section.trim().uppercase(Locale.US).ifBlank { "A" } }
                .distinct()
                .ifEmpty { listOf("A") }
            sections.any { sec ->
                viewModel.canViewStudentRemarksForClassSection(activeSchoolDocId, cls, sec)
            }
        }
    }

    // Visible remarks strictly filtered through Phase19AccessEvaluator
    val visibleRemarks = remember(
        uiState.studentRemarks,
        activeSchoolDocId,
        uiState.accessProfile,
        uiState.effectiveOwnTeacherScope
    ) {
        viewModel.visibleStudentRemarksForActiveSchool(activeSchoolDocId)
    }

    val dueFollowUps = remember(visibleRemarks, todayIso) {
        Phase19RemarksPtmAndGateEngine.buildFollowUpsDueList(visibleRemarks)
    }

    var showCategoryConfigDialog by remember { mutableStateOf(false) }
    var remarkPendingCancel by remember { mutableStateOf<StudentRemarkRecord?>(null) }
    var cancelReasonText by remember { mutableStateOf("") }
    var remarkPendingFollowUpDone by remember { mutableStateOf<StudentRemarkRecord?>(null) }
    var followUpDoneNoteText by remember { mutableStateOf("") }

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
                                .testTag("phase19_remarks_back_btn")
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
                                    "छात्र टिप्पणियाँ व अभिभावक बैठक (PTM)",
                                    "Student Remarks & Parent Meetings"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$schoolName • $session",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (uiState.isAdmin) {
                            IconButton(
                                onClick = { showCategoryConfigDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("remarks_category_settings_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = p10Label(hi, "श्रेणी सेटिंग्स", "Remark Category Settings")
                                )
                            }
                        }
                        TextButton(
                            onClick = { viewModel.setPhase10HindiLabels(!hi) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("phase19_remarks_lang_toggle")
                        ) {
                            Text(
                                text = if (hi) "EN" else "हिंदी",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    SecondaryScrollableTabRow(selectedTabIndex = selectedTab) {
                        RemarksAndPtmTab.entries.forEachIndexed { idx, tab ->
                            val badgeSuffix = when (tab) {
                                RemarksAndPtmTab.FOLLOW_UPS -> if (dueFollowUps.isNotEmpty()) " (${dueFollowUps.size})" else ""
                                else -> ""
                            }
                            Tab(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("remarks_tab_${tab.name.lowercase()}"),
                                text = {
                                    Text(
                                        text = (if (hi) tab.hi else tab.en) + badgeSuffix,
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
        when (RemarksAndPtmTab.entries[selectedTab]) {
            RemarksAndPtmTab.REMARKS -> {
                StudentRemarksMainTab(
                    viewModel = viewModel,
                    uiState = uiState,
                    hi = hi,
                    activeSchoolDocId = activeSchoolDocId,
                    activeSchoolLongId = activeSchoolLongId,
                    session = session,
                    todayIso = todayIso,
                    visibleClasses = visibleClasses,
                    allCategories = allCategories,
                    categoryConfig = categoryConfig,
                    visibleRemarks = visibleRemarks,
                    onRequestCancelRemark = { remark ->
                        remarkPendingCancel = remark
                        cancelReasonText = ""
                    },
                    onRequestFollowUpToggle = { remark ->
                        remarkPendingFollowUpDone = remark
                        followUpDoneNoteText = remark.followUpCompletedNote
                    },
                    onOpenCategorySettings = { showCategoryConfigDialog = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            RemarksAndPtmTab.FOLLOW_UPS -> {
                FollowUpsDueTab(
                    hi = hi,
                    todayIso = todayIso,
                    dueFollowUps = dueFollowUps,
                    deletedUserUids = uiState.deletedUserUids,
                    canEditRemark = { remark ->
                        viewModel.canEditStudentRemarksForClassSection(
                            activeSchoolDocId,
                            remark.className,
                            remark.section
                        )
                    },
                    onMarkDoneClick = { remark ->
                        remarkPendingFollowUpDone = remark
                        followUpDoneNoteText = remark.followUpCompletedNote
                    },
                    onCancelClick = { remark ->
                        remarkPendingCancel = remark
                        cancelReasonText = ""
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            RemarksAndPtmTab.PTM -> {
                ParentMeetingsPtmTab(
                    viewModel = viewModel,
                    uiState = uiState,
                    hi = hi,
                    activeSchoolDocId = activeSchoolDocId,
                    activeSchoolLongId = activeSchoolLongId,
                    schoolName = schoolName,
                    session = session,
                    todayIso = todayIso,
                    visibleClasses = visibleClasses,
                    clipboard = clipboard,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }

    // Cancel Remark Dialog (Mandatory reason, no silent deletion)
    remarkPendingCancel?.let { remark ->
        AlertDialog(
            onDismissRequest = { remarkPendingCancel = null },
            title = {
                Text(
                    p10Label(
                        hi,
                        "टिप्पणी रद्द करें (ऑडिट लॉग में सुरक्षित)",
                        "Cancel Remark (Kept in Audit Log)"
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = p10Label(
                            hi,
                            "टिप्पणियाँ चुपचाप हटाई नहीं जा सकतीं। रद्द करने का कारण लिखें — यह ऑडिट लॉग में दर्ज रहेगा।",
                            "Remarks cannot be silently deleted. Enter a cancellation reason — it will remain in the audit log."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${remark.studentName} (${remark.className}-${remark.section}) • ${remark.category}: \"${remark.note}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = cancelReasonText,
                        onValueChange = { cancelReasonText = it },
                        label = {
                            Text(p10Label(hi, "रद्द करने का कारण (अनिवार्य)", "Reason for cancellation (required)"))
                        },
                        placeholder = {
                            Text(p10Label(hi, "उदा. गलत छात्र चुना गया / सुधार किया गया", "e.g. Entered on wrong student / resolved"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remark_cancel_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelStudentRemark(remark, cancelReasonText) { ok, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ok) {
                                remarkPendingCancel = null
                            }
                        }
                    },
                    enabled = cancelReasonText.trim().length >= 3,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("remark_confirm_cancel_btn")
                ) {
                    Text(p10Label(hi, "कारण सहित रद्द करें", "Cancel with Reason"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { remarkPendingCancel = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(hi, "वापस", "Close"))
                }
            }
        )
    }

    // Follow-up Done Dialog
    remarkPendingFollowUpDone?.let { remark ->
        val nextDone = !remark.followUpCompleted
        AlertDialog(
            onDismissRequest = { remarkPendingFollowUpDone = null },
            title = {
                Text(
                    if (nextDone) {
                        p10Label(hi, "फॉलो-अप पूर्ण चिह्नित करें", "Mark Follow-up Completed")
                    } else {
                        p10Label(hi, "फॉलो-अप पुनः खोलें", "Reopen Follow-up")
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${remark.studentName} (${remark.className}-${remark.section}) • ${remark.category}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = remark.note,
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = followUpDoneNoteText,
                        onValueChange = { followUpDoneNoteText = it },
                        label = {
                            Text(p10Label(hi, "फॉलो-अप नोट (वैकल्पिक)", "Follow-up outcome note (optional)"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remark_followup_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleRemarkFollowUpCompleted(
                            remark = remark,
                            completed = nextDone,
                            completionNote = followUpDoneNoteText
                        ) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            remarkPendingFollowUpDone = null
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("remark_followup_confirm_btn")
                ) {
                    Text(p10Label(hi, "सहेजें", "Save"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { remarkPendingFollowUpDone = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(hi, "बंद करें", "Close"))
                }
            }
        )
    }

    // Editable Category List & Summary Visibility Dialog (Owner / Admin)
    if (showCategoryConfigDialog && uiState.isAdmin) {
        var customCatText by remember(categoryConfig) {
            mutableStateOf(
                categoryConfig.categories
                    .filterNot { c -> DefaultRemarkCategory.defaultCategoryNames.any { it.equals(c, ignoreCase = true) } }
                    .joinToString(", ")
            )
        }
        var hidePdfFromNonAdmins by remember(categoryConfig) {
            mutableStateOf(categoryConfig.hideSummaryPdfFromNonAdmins)
        }
        AlertDialog(
            onDismissRequest = { showCategoryConfigDialog = false },
            title = {
                Text(p10Label(hi, "टिप्पणी श्रेणियाँ व PDF गोपनीयता", "Remark Categories & PDF Privacy"))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = p10Label(
                            hi,
                            "डिफ़ॉल्ट श्रेणियाँ: ${DefaultRemarkCategory.defaultCategoryNames.joinToString(", ")}",
                            "Default categories: ${DefaultRemarkCategory.defaultCategoryNames.joinToString(", ")}"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customCatText,
                        onValueChange = { customCatText = it },
                        label = {
                            Text(p10Label(hi, "अतिरिक्त श्रेणियाँ (कॉमा से अलग करें)", "Custom Categories (comma-separated)"))
                        },
                        placeholder = {
                            Text(p10Label(hi, "उदा. खेलकूद, यूनिफॉर्म, पुस्तकालय", "e.g. Sports, Uniform, Library"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remark_custom_categories_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(
                                    hi,
                                    "गैर-एडमिन से A4 टिप्पणी सारांश PDF छुपाएँ",
                                    "Hide A4 Remark Summary PDF from non-admins"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = p10Label(
                                    hi,
                                    "केवल स्कूल स्वामी (Owner) और एडमिन ही A4 सारांश PDF प्रिंट कर सकते हैं।",
                                    "Only Owner and Admins can print the A4 student remark summary."
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hidePdfFromNonAdmins,
                            onCheckedChange = { hidePdfFromNonAdmins = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val extras = customCatText.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                        val merged = (DefaultRemarkCategory.defaultCategoryNames + extras).distinct()
                        viewModel.saveSchoolRemarkCategoryConfig(
                            categoryConfig.copy(
                                schoolId = activeSchoolDocId,
                                categories = merged,
                                hideSummaryPdfFromNonAdmins = hidePdfFromNonAdmins
                            )
                        ) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            showCategoryConfigDialog = false
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("remark_save_categories_btn")
                ) {
                    Text(p10Label(hi, "सहेजें", "Save"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCategoryConfigDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(hi, "रद्द करें", "Cancel"))
                }
            }
        )
    }

    // Shared PDF Preview Dialog
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
// TAB 1: STUDENT REMARKS & HISTORY
// =============================================================================

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StudentRemarksMainTab(
    viewModel: SchoolViewModel,
    uiState: SchoolUiState,
    hi: Boolean,
    activeSchoolDocId: String,
    activeSchoolLongId: Long,
    session: String,
    todayIso: String,
    visibleClasses: List<String>,
    allCategories: List<String>,
    categoryConfig: SchoolRemarkCategoryConfig,
    visibleRemarks: List<StudentRemarkRecord>,
    onRequestCancelRemark: (StudentRemarkRecord) -> Unit,
    onRequestFollowUpToggle: (StudentRemarkRecord) -> Unit,
    onOpenCategorySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Quick Add Remark Form State
    var addClass by remember(visibleClasses) {
        mutableStateOf(visibleClasses.firstOrNull().orEmpty())
    }
    val sectionsForAddClass = remember(uiState.allStudents, activeSchoolLongId, addClass, activeSchoolDocId) {
        val raw = uiState.allStudents
            .filter {
                it.schoolId == activeSchoolLongId &&
                    it.className.trim().equals(addClass, ignoreCase = true) &&
                    it.isActiveStatus
            }
            .map { it.section.trim().uppercase(Locale.US).ifBlank { "A" } }
            .distinct()
            .sorted()
            .ifEmpty { listOf("A") }
        raw.filter { sec ->
            viewModel.canViewStudentRemarksForClassSection(activeSchoolDocId, addClass, sec)
        }.ifEmpty { raw }
    }
    var addSection by remember(sectionsForAddClass) {
        mutableStateOf(sectionsForAddClass.firstOrNull() ?: "A")
    }
    val studentsForAddClassSection = remember(
        uiState.allStudents,
        activeSchoolLongId,
        session,
        addClass,
        addSection
    ) {
        uiState.allStudents
            .filter {
                it.schoolId == activeSchoolLongId &&
                    it.session == session &&
                    it.isActiveStatus &&
                    it.className.trim().equals(addClass, ignoreCase = true) &&
                    it.section.trim().ifBlank { "A" }.equals(addSection, ignoreCase = true)
            }
            .sortedWith(compareBy({ if (it.rollNo > 0) it.rollNo else Int.MAX_VALUE }, { it.name.lowercase(Locale.US) }))
    }
    var selectedStudentId by remember(studentsForAddClassSection) {
        mutableStateOf(studentsForAddClassSection.firstOrNull()?.id ?: 0L)
    }
    val selectedStudent = remember(studentsForAddClassSection, selectedStudentId) {
        studentsForAddClassSection.firstOrNull { it.id == selectedStudentId }
            ?: studentsForAddClassSection.firstOrNull()
    }
    var remarkDateIso by remember { mutableStateOf(todayIso) }
    var selectedCategory by remember(allCategories) {
        mutableStateOf(allCategories.firstOrNull() ?: DefaultRemarkCategory.PRAISE.labelEn)
    }
    var remarkNote by remember { mutableStateOf("") }
    var followUpDateIso by remember { mutableStateOf("") }

    val canEditSelectedClassSection = remember(
        activeSchoolDocId,
        addClass,
        addSection,
        uiState.accessProfile,
        uiState.effectiveOwnTeacherScope
    ) {
        viewModel.canEditStudentRemarksForClassSection(activeSchoolDocId, addClass, addSection)
    }

    // Filter State
    var filterClass by remember { mutableStateOf("ALL") }
    var filterCategory by remember { mutableStateOf("ALL") }
    var filterStudentOnly by remember { mutableStateOf(false) }
    var filterFromDateIso by remember { mutableStateOf("") }
    var filterToDateIso by remember { mutableStateOf("") }
    var showCancelledInAudit by remember { mutableStateOf(true) }

    val filteredRemarks = remember(
        visibleRemarks,
        filterClass,
        filterCategory,
        filterStudentOnly,
        selectedStudent,
        filterFromDateIso,
        filterToDateIso,
        showCancelledInAudit,
        uiState.accessProfile,
        uiState.effectiveOwnTeacherScope
    ) {
        Phase19RemarksPtmAndGateEngine.filterRemarksForViewer(
            allRemarks = visibleRemarks,
            member = uiState.accessProfile,
            teacherScope = uiState.effectiveOwnTeacherScope,
            schoolId = activeSchoolDocId,
            session = session,
            studentIdFilter = if (filterStudentOnly && selectedStudent != null) uiState.resolveStudentDocKey(selectedStudent) else null,
            classFilter = filterClass,
            categoryFilter = filterCategory,
            fromDateIso = filterFromDateIso,
            toDateIso = filterToDateIso,
            includeCancelled = showCancelledInAudit
        )
    }

    val categoryCounts = remember(filteredRemarks, allCategories) {
        Phase19RemarksPtmAndGateEngine.computeCategoryCounts(filteredRemarks, allCategories)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Sensitive Remarks Access Banner
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RateReview,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = p10Label(
                            hi,
                            "गोपनीयता नियम: केवल उस छात्र के कक्षा शिक्षक (Class Teacher), स्कूल स्वामी (Owner) और एडमिन ही टिप्पणियाँ देख/लिख सकते हैं। कोई भी टिप्पणी चुपचाप हटाई नहीं जा सकती (कारण सहित रद्द होकर ऑडिट लॉग में रहती है)।",
                            "Privacy Rule: By default only the student's Class Teacher, Owner, and Admins can read/write remarks. Remarks cannot be silently deleted — cancelling requires a reason and stays in the audit log."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 2. Quick Add Student Remark Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = p10Label(hi, "नई छात्र टिप्पणी जोड़ें", "Add Student Remark in a Few Taps"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.isAdmin) {
                            TextButton(
                                onClick = onOpenCategorySettings,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(hi, "श्रेणियाँ बदलें", "Edit Categories"))
                            }
                        }
                    }

                    // Class Picker Chips
                    Text(
                        text = p10Label(hi, "कक्षा चुनें:", "Select Class:"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        visibleClasses.forEach { cls ->
                            FilterChip(
                                selected = addClass.equals(cls, ignoreCase = true),
                                onClick = { addClass = cls },
                                label = { Text(cls) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("remark_class_chip_$cls")
                            )
                        }
                    }

                    // Section Picker Chips
                    if (sectionsForAddClass.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = p10Label(hi, "सेक्शन:", "Section:"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            sectionsForAddClass.forEach { sec ->
                                FilterChip(
                                    selected = addSection.equals(sec, ignoreCase = true),
                                    onClick = { addSection = sec },
                                    label = { Text(sec) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    // Student Picker Chips
                    Text(
                        text = p10Label(
                            hi,
                            "छात्र चुनें (${studentsForAddClassSection.size}):",
                            "Select Student (${studentsForAddClassSection.size}):"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (studentsForAddClassSection.isEmpty()) {
                        Text(
                            text = p10Label(
                                hi,
                                "इस कक्षा में कोई सक्रिय छात्र नहीं मिला।",
                                "No active students found in this class-section."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            studentsForAddClassSection.forEach { st ->
                                val isSel = selectedStudent?.id == st.id
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedStudentId = st.id },
                                    label = {
                                        val rollPrefix = if (st.rollNo > 0) "#${st.rollNo} " else ""
                                        Text("$rollPrefix${st.name}")
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("remark_student_chip_${st.id}")
                                )
                            }
                        }
                    }

                    // Category Chips
                    Text(
                        text = p10Label(hi, "श्रेणी (Category):", "Category:"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        allCategories.forEach { cat ->
                            val hiLabel = DefaultRemarkCategory.entries.firstOrNull {
                                it.labelEn.equals(cat, ignoreCase = true)
                            }?.labelHi ?: cat
                            FilterChip(
                                selected = selectedCategory.equals(cat, ignoreCase = true),
                                onClick = { selectedCategory = cat },
                                label = { Text(if (hi) hiLabel else cat) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("remark_cat_chip_${cat.lowercase(Locale.US)}")
                            )
                        }
                    }

                    // Date & Optional Follow-up Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = remarkDateIso,
                            onValueChange = { remarkDateIso = it },
                            label = { Text(p10Label(hi, "तारीख (YYYY-MM-DD)", "Date (YYYY-MM-DD)")) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("remark_date_input")
                        )
                        OutlinedTextField(
                            value = followUpDateIso,
                            onValueChange = { followUpDateIso = it },
                            label = {
                                Text(p10Label(hi, "फॉलो-अप तारीख (वैकल्पिक)", "Follow-up Date (Optional)"))
                            },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("remark_followup_date_input")
                        )
                    }

                    // Short Note Input
                    OutlinedTextField(
                        value = remarkNote,
                        onValueChange = { remarkNote = it },
                        label = {
                            Text(p10Label(hi, "संक्षिप्त टिप्पणी (Short Note)", "Short Note"))
                        },
                        placeholder = {
                            Text(
                                p10Label(
                                    hi,
                                    "उदा. गणित टेस्ट में उत्कृष्ट प्रदर्शन / गृहकार्य अधूरा",
                                    "e.g. Excellent improvement in Maths / Homework incomplete twice"
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remark_note_input")
                    )

                    if (canEditSelectedClassSection) {
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
                                viewModel.addStudentRemark(
                                    schoolDocId = activeSchoolDocId,
                                    session = session,
                                    student = st,
                                    dateIso = remarkDateIso,
                                    category = selectedCategory,
                                    note = remarkNote,
                                    followUpDateIso = followUpDateIso
                                ) { ok, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (ok) {
                                        remarkNote = ""
                                        followUpDateIso = ""
                                    }
                                }
                            },
                            enabled = selectedStudent != null && remarkNote.trim().isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("remark_save_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                p10Label(
                                    hi,
                                    "टिप्पणी सहेजें (${selectedStudent?.name ?: ""})",
                                    "Save Remark (${selectedStudent?.name ?: ""})"
                                )
                            )
                        }
                    } else {
                        Text(
                            text = p10Label(
                                hi,
                                "केवल देखने की अनुमति (View Only) — टिप्पणी जोड़ने के लिए कक्षा शिक्षक या संपादन अनुमति आवश्यक है।",
                                "View Only — Class Teacher or Student Remarks edit permission is required to add remarks."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Filters, Category Counts & Owner/Admin Printable A4 Summary Button
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = p10Label(
                                hi,
                                "फ़िल्टर व श्रेणी गणना (${filteredRemarks.size})",
                                "Filters & Category Counts (${filteredRemarks.size})"
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        // Printable A4 Student Remark Summary ONLY for Owner and Admins
                        if (uiState.canPrintStudentRemarkSummaryPdf) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.previewStudentRemarkSummaryPdf(
                                        schoolDocId = activeSchoolDocId,
                                        session = session,
                                        student = if (filterStudentOnly) selectedStudent else null,
                                        classFilterLabel = if (filterClass == "ALL") "All Classes" else filterClass,
                                        remarks = filteredRemarks,
                                        configuredCategories = allCategories
                                    )
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("remark_print_a4_summary_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(p10Label(hi, "A4 सारांश PDF (एडमिन)", "A4 Summary PDF (Admin)"))
                            }
                        } else if (!categoryConfig.hideSummaryPdfFromNonAdmins) {
                            Text(
                                text = p10Label(
                                    hi,
                                    "A4 सारांश PDF केवल स्वामी/एडमिन के लिए",
                                    "A4 Summary PDF: Owner/Admin only"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Filter by Class
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = filterClass == "ALL",
                            onClick = { filterClass = "ALL" },
                            label = { Text(p10Label(hi, "सभी कक्षाएँ", "All Classes")) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        visibleClasses.forEach { cls ->
                            FilterChip(
                                selected = filterClass.equals(cls, ignoreCase = true),
                                onClick = { filterClass = cls },
                                label = { Text(cls) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    // Filter by Category + Counts per Category
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val totalActive = filteredRemarks.count { it.isActive }
                        FilterChip(
                            selected = filterCategory == "ALL",
                            onClick = { filterCategory = "ALL" },
                            label = {
                                Text(p10Label(hi, "सभी श्रेणियाँ ($totalActive)", "All Categories ($totalActive)"))
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        allCategories.forEach { cat ->
                            val cnt = categoryCounts[cat] ?: 0
                            FilterChip(
                                selected = filterCategory.equals(cat, ignoreCase = true),
                                onClick = { filterCategory = cat },
                                label = { Text("$cat ($cnt)") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    // Filter by selected student & date range & show cancelled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = filterFromDateIso,
                            onValueChange = { filterFromDateIso = it },
                            label = { Text(p10Label(hi, "से (YYYY-MM-DD)", "From Date")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = filterToDateIso,
                            onValueChange = { filterToDateIso = it },
                            label = { Text(p10Label(hi, "तक (YYYY-MM-DD)", "To Date")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilterChip(
                            selected = filterStudentOnly,
                            onClick = { filterStudentOnly = !filterStudentOnly },
                            label = {
                                Text(
                                    p10Label(
                                        hi,
                                        "केवल चयनित छात्र (${selectedStudent?.name ?: "-"})",
                                        "Selected Student Only (${selectedStudent?.name ?: "-"})"
                                    )
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        FilterChip(
                            selected = showCancelledInAudit,
                            onClick = { showCancelledInAudit = !showCancelledInAudit },
                            label = {
                                Text(p10Label(hi, "रद्द टिप्पणियाँ (ऑडिट)", "Show Cancelled (Audit)"))
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }

        // 4. Remark List Cards
        if (filteredRemarks.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = p10Label(
                            hi,
                            "चयनित फ़िल्टर के लिए कोई छात्र टिप्पणी नहीं मिली।",
                            "No student remarks found for the selected filters."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(filteredRemarks, key = { it.id }) { remark ->
                RemarkHistoryItemCard(
                    remark = remark,
                    hi = hi,
                    canEdit = viewModel.canEditStudentRemarksForClassSection(
                        activeSchoolDocId,
                        remark.className,
                        remark.section
                    ),
                    deletedUserUids = uiState.deletedUserUids,
                    onCancelClick = { onRequestCancelRemark(remark) },
                    onToggleFollowUpClick = { onRequestFollowUpToggle(remark) }
                )
            }
        }
    }
}

@Composable
internal fun RemarkHistoryItemCard(
    remark: StudentRemarkRecord,
    hi: Boolean,
    canEdit: Boolean,
    deletedUserUids: Set<String> = emptySet(),
    onCancelClick: () -> Unit,
    onToggleFollowUpClick: () -> Unit
) {
    val isCancelled = remark.status == StudentRemarkStatus.CANCELLED
    val isPraise = remark.category.equals(DefaultRemarkCategory.PRAISE.labelEn, ignoreCase = true)
    val badgeBg = when {
        isCancelled -> FailRedContainer
        isPraise -> PassGreenContainer
        else -> AbsentAmberContainer
    }
    val badgeFg = when {
        isCancelled -> FailRed
        isPraise -> PassGreen
        else -> AbsentAmber
    }
    val timeFmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val createdLabel = remember(remark.createdAtMs) {
        if (remark.createdAtMs > 0L) timeFmt.format(Date(remark.createdAtMs)) else remark.dateIso
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("remark_item_card_${remark.id}"),
        border = if (isCancelled) BorderStroke(1.dp, FailRed.copy(alpha = 0.5f)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${remark.studentName} (${remark.className}-${remark.section})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${p10Label(hi, "दिनांक", "Date")}: ${remark.dateIso}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (isCancelled) "${remark.category} • CANCELLED" else remark.category,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = remark.note,
                style = MaterialTheme.typography.bodyMedium
            )

            if (remark.followUpDateIso.isNotBlank()) {
                Surface(
                    color = if (remark.followUpCompleted) PassGreenContainer else AbsentAmberContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (remark.followUpCompleted) {
                                    p10Label(
                                        hi,
                                        "फॉलो-अप पूर्ण (${remark.followUpDateIso})",
                                        "Follow-up Completed (${remark.followUpDateIso})"
                                    )
                                } else {
                                    p10Label(
                                        hi,
                                        "फॉलो-अप देय: ${remark.followUpDateIso}",
                                        "Follow-up Due: ${remark.followUpDateIso}"
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (remark.followUpCompleted) PassGreen else AbsentAmber
                            )
                            if (remark.followUpCompletedNote.isNotBlank()) {
                                Text(
                                    text = remark.followUpCompletedNote,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (canEdit && !isCancelled) {
                            TextButton(
                                onClick = onToggleFollowUpClick,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    if (remark.followUpCompleted) {
                                        p10Label(hi, "पुनः खोलें", "Reopen")
                                    } else {
                                        p10Label(hi, "पूर्ण करें", "Mark Done")
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isCancelled) {
                Surface(
                    color = FailRedContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val cancelledAtStr = if (remark.cancelledAtMs > 0L) {
                        timeFmt.format(Date(remark.cancelledAtMs))
                    } else ""
                    Text(
                        text = p10Label(
                            hi,
                            "ऑडिट लॉग (रद्द): कारण = \"${remark.cancelledReason}\" • द्वारा: ${remark.cancelledByName} ($cancelledAtStr)",
                            "Audit Log (Cancelled): Reason = \"${remark.cancelledReason}\" • By: ${remark.cancelledByName} ($cancelledAtStr)"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = FailRed,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val authorStr = AuthorDisplayHelper.resolveAuthorName(
                    authorUid = remark.authorUid,
                    storedName = remark.authorName,
                    deletedUserUids = deletedUserUids,
                    fallback = "Teacher"
                )
                Text(
                    text = p10Label(
                        hi,
                        "लेखक: $authorStr • $createdLabel",
                        "By: $authorStr • $createdLabel"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                if (canEdit && !isCancelled) {
                    TextButton(
                        onClick = onCancelClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("remark_cancel_btn_${remark.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(hi, "रद्द करें (ऑडिट)", "Cancel (Audit)"))
                    }
                }
            }
        }
    }
}

// =============================================================================
// TAB 2: FOLLOW-UPS DUE LIST
// =============================================================================

@Composable
private fun FollowUpsDueTab(
    hi: Boolean,
    todayIso: String,
    dueFollowUps: List<StudentRemarkRecord>,
    deletedUserUids: Set<String> = emptySet(),
    canEditRemark: (StudentRemarkRecord) -> Boolean = { true },
    onMarkDoneClick: (StudentRemarkRecord) -> Unit,
    onCancelClick: (StudentRemarkRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var showOnlyDueUpToToday by remember { mutableStateOf(false) }
    val shownList = remember(dueFollowUps, showOnlyDueUpToToday, todayIso) {
        if (showOnlyDueUpToToday) {
            dueFollowUps.filter { it.followUpDateIso <= todayIso }
        } else {
            dueFollowUps
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p10Label(
                                hi,
                                "बकाया फॉलो-अप सूची (${shownList.size})",
                                "Follow-ups Due List (${shownList.size})"
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = p10Label(
                                hi,
                                "आज की तारीख: $todayIso",
                                "Today: $todayIso"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilterChip(
                        selected = showOnlyDueUpToToday,
                        onClick = { showOnlyDueUpToToday = !showOnlyDueUpToToday },
                        label = {
                            Text(p10Label(hi, "केवल आज तक देय", "Due Today / Overdue Only"))
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }

        if (shownList.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = p10Label(
                            hi,
                            "कोई बकाया फॉलो-अप नहीं है।",
                            "No pending follow-ups due."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(shownList, key = { it.id }) { remark ->
                RemarkHistoryItemCard(
                    remark = remark,
                    hi = hi,
                    canEdit = canEditRemark(remark),
                    deletedUserUids = deletedUserUids,
                    onCancelClick = { onCancelClick(remark) },
                    onToggleFollowUpClick = { onMarkDoneClick(remark) }
                )
            }
        }
    }
}

// =============================================================================
// TAB 3: PARENT MEETINGS (PTM)
// =============================================================================

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ParentMeetingsPtmTab(
    viewModel: SchoolViewModel,
    uiState: SchoolUiState,
    hi: Boolean,
    activeSchoolDocId: String,
    activeSchoolLongId: Long,
    schoolName: String,
    session: String,
    todayIso: String,
    visibleClasses: List<String>,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val schoolMeetings = remember(uiState.parentMeetings, activeSchoolDocId, session) {
        uiState.parentMeetings
            .filter { it.schoolId == activeSchoolDocId && it.session == session }
            .sortedByDescending { it.dateIso }
    }

    var selectedMeetingId by remember(schoolMeetings) {
        mutableStateOf(schoolMeetings.firstOrNull()?.id.orEmpty())
    }
    val activeMeeting = remember(schoolMeetings, selectedMeetingId) {
        schoolMeetings.firstOrNull { it.id == selectedMeetingId } ?: schoolMeetings.firstOrNull()
    }

    // Create / Edit PTM form state
    var showCreatePtmCard by remember { mutableStateOf(schoolMeetings.isEmpty()) }
    var ptmDateIso by remember { mutableStateOf(todayIso) }
    var ptmTimeLabel by remember { mutableStateOf("10:00 AM - 01:00 PM") }
    var ptmTitle by remember { mutableStateOf("Parent-Teacher Meeting (PTM)") }
    var ptmClass by remember(visibleClasses) {
        mutableStateOf(visibleClasses.firstOrNull() ?: "ALL")
    }
    var ptmSection by remember { mutableStateOf("A") }
    var ptmAgendaNote by remember { mutableStateOf("") }

    // Smart suggestions for the selected PTM class-section
    val ptmSuggestions = remember(
        activeSchoolDocId,
        session,
        ptmClass,
        ptmSection,
        uiState.allStudents,
        uiState.allMarks,
        uiState.studentAttendanceSheets,
        uiState.studentRemarks
    ) {
        viewModel.computePtmStudentSuggestionsForScope(
            schoolDocId = activeSchoolDocId,
            session = session,
            className = ptmClass,
            section = if (ptmClass == "ALL") "ALL" else ptmSection
        )
    }

    val classStudentsForPtm = remember(
        uiState.allStudents,
        activeSchoolLongId,
        session,
        ptmClass,
        ptmSection
    ) {
        uiState.allStudents.filter { st ->
            st.schoolId == activeSchoolLongId &&
                st.session == session &&
                st.isActiveStatus &&
                (ptmClass == "ALL" || st.className.trim().equals(ptmClass, ignoreCase = true)) &&
                (ptmClass == "ALL" || ptmSection == "ALL" || st.section.trim().ifBlank { "A" }.equals(ptmSection, ignoreCase = true))
        }.sortedWith(compareBy({ it.className }, { if (it.rollNo > 0) it.rollNo else Int.MAX_VALUE }, { it.name }))
    }

    var selectedStudentIdsForNewPtm by remember(ptmClass, ptmSection, ptmSuggestions) {
        mutableStateOf(ptmSuggestions.map { it.studentId }.toSet())
    }

    var entryBeingEdited by remember { mutableStateOf<Pair<ParentMeetingRecord, PtmStudentCallEntry>?>(null) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header & Toggle New PTM
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(
                                    hi,
                                    "अभिभावक बैठकें (PTM) (${schoolMeetings.size})",
                                    "Parent-Teacher Meetings (${schoolMeetings.size})"
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = p10Label(
                                    hi,
                                    "कमजोर छात्रों व कम उपस्थिति वाले छात्रों के सुझावों के साथ बैठक बनाएँ",
                                    "Create meetings with smart suggestions from Result Analysis & low attendance"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showCreatePtmCard = !showCreatePtmCard },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("ptm_toggle_create_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (showCreatePtmCard) {
                                    p10Label(hi, "फॉर्म छुपाएँ", "Hide Form")
                                } else {
                                    p10Label(hi, "नई PTM बनाएँ", "New PTM")
                                }
                            )
                        }
                    }

                    if (schoolMeetings.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            schoolMeetings.forEach { mtg ->
                                val scopeStr = if (mtg.className == "ALL") "School" else "${mtg.className}-${mtg.section}"
                                FilterChip(
                                    selected = activeMeeting?.id == mtg.id,
                                    onClick = { selectedMeetingId = mtg.id },
                                    label = { Text("${mtg.dateIso} • $scopeStr (${mtg.entries.size})") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Create PTM Card with Smart Suggestions
        if (showCreatePtmCard) {
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
                            text = p10Label(hi, "नई अभिभावक बैठक (PTM) बनाएँ", "Schedule Parent Meeting (PTM)"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = ptmDateIso,
                                onValueChange = { ptmDateIso = it },
                                label = { Text(p10Label(hi, "तारीख (YYYY-MM-DD)", "Meeting Date")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ptm_date_input")
                            )
                            OutlinedTextField(
                                value = ptmTimeLabel,
                                onValueChange = { ptmTimeLabel = it },
                                label = { Text(p10Label(hi, "समय", "Time")) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ptm_time_input")
                            )
                        }

                        OutlinedTextField(
                            value = ptmTitle,
                            onValueChange = { ptmTitle = it },
                            label = { Text(p10Label(hi, "बैठक का शीर्षक", "Meeting Title")) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ptm_title_input")
                        )

                        // Pick Class or Whole School
                        Text(
                            text = p10Label(hi, "कक्षा या पूरा स्कूल चुनें:", "Select Class or Whole School:"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (uiState.isAdmin) {
                                FilterChip(
                                    selected = ptmClass == "ALL",
                                    onClick = {
                                        ptmClass = "ALL"
                                        ptmSection = "ALL"
                                    },
                                    label = { Text(p10Label(hi, "पूरा स्कूल", "Whole School")) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                            visibleClasses.forEach { cls ->
                                FilterChip(
                                    selected = ptmClass.equals(cls, ignoreCase = true),
                                    onClick = {
                                        ptmClass = cls
                                        if (ptmSection == "ALL") ptmSection = "A"
                                    },
                                    label = { Text(cls) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }

                        // Smart Suggestions Banner & Quick Select Buttons
                        Surface(
                            color = AbsentAmberContainer.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = p10Label(
                                        hi,
                                        "स्मार्ट सुझाव (${ptmSuggestions.size}): परीक्षा परिणाम विश्लेषण (Weak Students), कम उपस्थिति (<75%) और टिप्पणियों से",
                                        "Smart Suggestions (${ptmSuggestions.size}): Weak students from Result Analysis, low attendance (<75%), & remarks"
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AbsentAmber
                                )
                                if (ptmSuggestions.isNotEmpty()) {
                                    ptmSuggestions.forEach { sug ->
                                        val isChecked = sug.studentId in selectedStudentIdsForNewPtm
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedStudentIdsForNewPtm = if (isChecked) {
                                                        selectedStudentIdsForNewPtm - sug.studentId
                                                    } else {
                                                        selectedStudentIdsForNewPtm + sug.studentId
                                                    }
                                                }
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedStudentIdsForNewPtm = if (checked) {
                                                        selectedStudentIdsForNewPtm + sug.studentId
                                                    } else {
                                                        selectedStudentIdsForNewPtm - sug.studentId
                                                    }
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${sug.studentName} (${sug.className}-${sug.section})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = sug.reasons.joinToString(" • "),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = FailRed
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            selectedStudentIdsForNewPtm = ptmSuggestions.map { it.studentId }.toSet()
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(p10Label(hi, "सभी सुझाए गए चुनें", "Select Suggested (${ptmSuggestions.size})"))
                                    }
                                    TextButton(
                                        onClick = {
                                            selectedStudentIdsForNewPtm = classStudentsForPtm.map { uiState.resolveStudentDocKey(it) }.toSet()
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(p10Label(hi, "पूरी कक्षा चुनें", "Select All (${classStudentsForPtm.size})"))
                                    }
                                }
                            }
                        }

                        // Additional Students Picker
                        Text(
                            text = p10Label(
                                hi,
                                "बुलाए जाने वाले छात्र (${selectedStudentIdsForNewPtm.size} चयनित):",
                                "Students to be Called (${selectedStudentIdsForNewPtm.size} selected):"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            classStudentsForPtm.forEach { st ->
                                val stDocKey = uiState.resolveStudentDocKey(st)
                                val isSel = stDocKey in selectedStudentIdsForNewPtm
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        selectedStudentIdsForNewPtm = if (isSel) {
                                            selectedStudentIdsForNewPtm - stDocKey
                                        } else {
                                            selectedStudentIdsForNewPtm + stDocKey
                                        }
                                    },
                                    label = { Text("${st.name} (${st.className}-${st.section})") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val chosenStudents = classStudentsForPtm.filter { uiState.resolveStudentDocKey(it) in selectedStudentIdsForNewPtm }
                                if (chosenStudents.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        p10Label(hi, "कृपया कम से कम एक छात्र चुनें", "Please pick at least one student to call"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@Button
                                }
                                val sugByStudent = ptmSuggestions.associateBy { it.studentId }
                                val entries = chosenStudents.map { st ->
                                    val docId = uiState.resolveStudentDocKey(st)
                                    val sug = sugByStudent[docId]
                                    PtmStudentCallEntry(
                                        studentId = docId,
                                        studentAdmNo = st.admNo.trim(),
                                        studentName = st.name.trim(),
                                        className = st.className.trim(),
                                        section = st.section.trim().uppercase(Locale.US).ifBlank { "A" },
                                        rollNo = st.rollNo.coerceAtLeast(0),
                                        guardianName = st.fatherName.ifBlank { st.motherName }.trim(),
                                        mobile = st.mobile.ifBlank { st.secondMobile }.trim(),
                                        suggestionReason = sug?.reasons?.joinToString("; ").orEmpty(),
                                        status = PtmCallStatus.CALLED
                                    )
                                }
                                val secCode = if (ptmClass == "ALL") "ALL" else ptmSection
                                val mtgId = "ptm_${activeSchoolDocId}_${session}_${ptmDateIso}_${ptmClass}_${secCode}"
                                    .replace(Regex("[^A-Za-z0-9_-]"), "_")
                                val newMeeting = ParentMeetingRecord(
                                    id = mtgId,
                                    schoolId = activeSchoolDocId,
                                    session = session,
                                    title = ptmTitle.ifBlank { "Parent-Teacher Meeting (PTM)" },
                                    dateIso = ptmDateIso,
                                    timeLabel = ptmTimeLabel,
                                    className = ptmClass,
                                    section = secCode,
                                    agenda = ptmAgendaNote,
                                    entries = entries,
                                    createdByUid = viewModel.currentUserId.ifBlank { uiState.currentMember?.uid.orEmpty() }.ifBlank { "local_user" },
                                    createdByName = uiState.currentMember?.name?.ifBlank { "Teacher" } ?: "Teacher"
                                )
                                viewModel.saveParentMeeting(newMeeting) { ok, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (ok) {
                                        selectedMeetingId = newMeeting.id
                                        showCreatePtmCard = false
                                    }
                                }
                            },
                            enabled = selectedStudentIdsForNewPtm.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("ptm_save_meeting_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                p10Label(
                                    hi,
                                    "PTM सहेजें (${selectedStudentIdsForNewPtm.size} छात्र)",
                                    "Save PTM (${selectedStudentIdsForNewPtm.size} Students Called)"
                                )
                            )
                        }
                    }
                }
            }
        }

        // Active PTM Tracking & Summary PDF
        if (activeMeeting != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${activeMeeting.title} • ${activeMeeting.dateIso}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                val scopeText = if (activeMeeting.className == "ALL") {
                                    p10Label(hi, "पूरा स्कूल", "Whole School")
                                } else {
                                    "${activeMeeting.className}-${activeMeeting.section}"
                                }
                                Text(
                                    text = "$scopeText • ${activeMeeting.timeLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.previewPtmSummaryPdf(activeMeeting) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("ptm_summary_pdf_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(p10Label(hi, "A4 PTM सारांश PDF", "A4 PTM Summary PDF"))
                            }
                        }

                        // Counts Bar: Called, Attended, Not Attended
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = AbsentAmberContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${activeMeeting.entries.size}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AbsentAmber
                                    )
                                    Text(
                                        text = p10Label(hi, "कुल बुलाए गए", "Total Called"),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Surface(
                                color = PassGreenContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${activeMeeting.attendedCount}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PassGreen
                                    )
                                    Text(
                                        text = p10Label(hi, "उपस्थित (Attended)", "Attended"),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Surface(
                                color = FailRedContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${activeMeeting.notAttendedCount}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = FailRed
                                    )
                                    Text(
                                        text = p10Label(hi, "अनुपस्थित", "Not Attended"),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(activeMeeting.entries, key = { "${activeMeeting.id}_${it.studentId}" }) { entry ->
                val inviteText = remember(activeMeeting, entry, schoolName, hi) {
                    Phase19RemarksPtmAndGateEngine.buildPtmInvitationMessage(
                        schoolName = schoolName,
                        meeting = activeMeeting,
                        entry = entry,
                        useHindi = hi
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ptm_entry_card_${entry.studentId}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${entry.studentName} (${entry.className}-${entry.section})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (entry.suggestionReason.isNotBlank()) {
                                    Text(
                                        text = "${p10Label(hi, "कारण", "Reason")}: ${entry.suggestionReason}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FailRed
                                    )
                                }
                            }
                        }

                        // Status Toggle Chips: Called, Attended, Not Attended
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PtmCallStatus.entries.forEach { st ->
                                val isSel = entry.status == st
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        viewModel.updatePtmStudentOutcome(
                                            meeting = activeMeeting,
                                            studentId = entry.studentId,
                                            status = st,
                                            discussionNote = entry.discussionNote,
                                            decision = entry.decision
                                        )
                                    },
                                    label = { Text(if (hi) st.labelHi else st.labelEn) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("ptm_status_chip_${entry.studentId}_${st.name.lowercase()}")
                                )
                            }
                        }

                        if (entry.discussionNote.isNotBlank() || entry.decision.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (entry.discussionNote.isNotBlank()) {
                                        Text(
                                            text = "${p10Label(hi, "चर्चा", "Discussed")}: ${entry.discussionNote}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (entry.decision.isNotBlank()) {
                                        Text(
                                            text = "${p10Label(hi, "निर्णय", "Decision")}: ${entry.decision}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Actions: Edit Discussion/Decision, One-tap WhatsApp Invitation, Copy
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { entryBeingEdited = activeMeeting to entry },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("ptm_edit_outcome_btn_${entry.studentId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(hi, "चर्चा व निर्णय लिखें", "Note & Decision"))
                            }

                            OutlinedButton(
                                onClick = {
                                    val waUri = Phase19RemarksPtmAndGateEngine.buildPtmWhatsAppUrl(
                                        rawMobile = entry.mobile,
                                        messageText = inviteText
                                    )
                                    val intent = if (waUri != null) {
                                        Intent(Intent.ACTION_VIEW, Uri.parse(waUri))
                                    } else {
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, inviteText)
                                        }
                                    }
                                    runCatching {
                                        context.startActivity(
                                            if (waUri != null) intent else Intent.createChooser(intent, "Share PTM Invite")
                                        )
                                    }.onFailure {
                                        clipboard.setText(AnnotatedString(inviteText))
                                        Toast.makeText(
                                            context,
                                            p10Label(hi, "आमंत्रण संदेश कॉपी किया गया", "Invitation copied to clipboard"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("ptm_whatsapp_invite_btn_${entry.studentId}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(hi, "WhatsApp आमंत्रण", "WhatsApp Invite"))
                            }

                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(inviteText))
                                    Toast.makeText(
                                        context,
                                        p10Label(hi, "आमंत्रण कॉपी किया गया", "Invitation text copied"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = p10Label(hi, "कॉपी करें", "Copy Invite")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Discussion Note & Decision Dialog
    entryBeingEdited?.let { (meeting, entry) ->
        var status by remember(entry) { mutableStateOf(entry.status) }
        var discussionText by remember(entry) { mutableStateOf(entry.discussionNote) }
        var decisionText by remember(entry) { mutableStateOf(entry.decision) }

        AlertDialog(
            onDismissRequest = { entryBeingEdited = null },
            title = {
                Text("${entry.studentName} (${entry.className}-${entry.section})")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PtmCallStatus.entries.forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(if (hi) st.labelHi else st.labelEn) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = discussionText,
                        onValueChange = { discussionText = it },
                        label = { Text(p10Label(hi, "क्या चर्चा हुई (Discussion Note)", "What was discussed")) },
                        placeholder = {
                            Text(p10Label(hi, "उदा. गणित और उपस्थिति पर चर्चा", "e.g. Discussed Maths practice & regular attendance"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ptm_discussion_input")
                    )
                    OutlinedTextField(
                        value = decisionText,
                        onValueChange = { decisionText = it },
                        label = { Text(p10Label(hi, "निर्णय / आगे का कदम (Decision)", "Decision / Next Step")) },
                        placeholder = {
                            Text(p10Label(hi, "उदा. रोज़ 1 घंटा अतिरिक्त अभ्यास व साप्ताहिक जाँच", "e.g. Daily 1 hr revision signed by parent"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ptm_decision_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updatePtmStudentOutcome(
                            meeting = meeting,
                            studentId = entry.studentId,
                            status = status,
                            discussionNote = discussionText,
                            decision = decisionText
                        ) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            entryBeingEdited = null
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ptm_save_outcome_btn")
                ) {
                    Text(p10Label(hi, "सहेजें", "Save"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { entryBeingEdited = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(hi, "रद्द करें", "Cancel"))
                }
            }
        )
    }
}
