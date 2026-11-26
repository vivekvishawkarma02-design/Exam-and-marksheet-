package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.domain.CashBookAuditLogRecord
import com.example.domain.CashBookEngine
import com.example.domain.CashBookEntryRecord
import com.example.domain.CashBookEntryType
import com.example.domain.CashBookSettingsConfig
import com.example.domain.MemberStatus
import com.example.domain.OrgRole
import com.example.domain.PaymentMode
import com.example.domain.Phase10AccessEvaluator
import com.example.domain.Phase6DateUtils
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Phase10CashBookScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()

    val isHindi = uiState.phase10HindiLabels
    val activeSchool = uiState.activeSchool
    val activeSchoolDocId = uiState.activeSchoolDocId
    val canEdit = Phase10AccessEvaluator.canEditCashBook(uiState.accessProfile, activeSchoolDocId) ||
        uiState.canEditCashBook
    val isAdmin = uiState.isAdmin

    val settings = remember(uiState.cashBookSettingsList, activeSchoolDocId) {
        uiState.cashBookSettingsForSchool(activeSchoolDocId)
    }

    val activeAdmins = remember(uiState.orgMembers) {
        uiState.orgMembers.filter {
            it.memberStatus == MemberStatus.ACTIVE && (it.orgRole == OrgRole.OWNER || it.orgRole == OrgRole.ADMIN)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Today, 1=Month, 2=Day Closing, 3=Reports & Setup
    var selectedDateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }
    var selectedYearMonth by remember { mutableStateOf(Phase6DateUtils.todayIso().take(7)) }

    // Dialog states
    var showEntryDialog by remember { mutableStateOf(false) }
    var entryDialogType by remember { mutableStateOf(CashBookEntryType.EXPENSE) }
    var editingEntry by remember { mutableStateOf<CashBookEntryRecord?>(null) }
    var cancelTargetEntry by remember { mutableStateOf<CashBookEntryRecord?>(null) }
    var cancelReasonInput by remember { mutableStateOf("") }
    var cancelError by remember { mutableStateOf<String?>(null) }
    var previewPhotoBase64 by remember { mutableStateOf<String?>(null) }
    var showAuditLogDialog by remember { mutableStateOf(false) }

    val daySummary = remember(
        activeSchoolDocId,
        selectedDateIso,
        uiState.effectiveCashBookFeeReceipts,
        uiState.cashBookEntries,
        uiState.cashBookDayClosings,
        settings
    ) {
        CashBookEngine.computeDaySummary(
            schoolId = activeSchoolDocId,
            dateIso = selectedDateIso,
            feeReceipts = uiState.effectiveCashBookFeeReceipts,
            entries = uiState.cashBookEntries,
            dayClosings = uiState.cashBookDayClosings,
            settings = settings
        )
    }

    val monthSummary = remember(
        activeSchoolDocId,
        selectedYearMonth,
        uiState.effectiveCashBookFeeReceipts,
        uiState.cashBookEntries
    ) {
        CashBookEngine.computeMonthSummary(
            schoolId = activeSchoolDocId,
            yearMonth = selectedYearMonth,
            feeReceipts = uiState.effectiveCashBookFeeReceipts,
            entries = uiState.cashBookEntries
        )
    }

    val schoolAuditLogs = remember(uiState.cashBookAuditLogs, activeSchoolDocId) {
        uiState.cashBookAuditLogs
            .filter { it.schoolId == activeSchoolDocId }
            .sortedByDescending { it.clientCreatedAtMs }
    }

    fun shareCsvText(fileName: String, csvContent: String) {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                File(dir, fileName).apply { writeText(csvContent) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share CSV"))
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("phase10_cashbook_screen")
    ) {
        // Two-row responsive Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("cashbook_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = p10Label(isHindi, "रोकड़ बही (Cash Book)", "Cash Book"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("cashbook_header_title")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showAuditLogDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("cashbook_audit_log_button")
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = p10Label(isHindi, "ऑडिट लॉग", "Audit Log"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Phase10LanguageToggleChip(
                    isHindi = isHindi,
                    onToggle = viewModel::togglePhase10HindiLabels
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        PrimaryScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(p10Label(isHindi, "आज (Today)", "Today"), fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("cashbook_tab_today")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(p10Label(isHindi, "महीना (Month)", "Month"), fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("cashbook_tab_month")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(p10Label(isHindi, "दिन बंद (Closing)", "Day Closing"), fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("cashbook_tab_closing")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text(p10Label(isHindi, "रिपोर्ट व सेटिंग", "Reports & Setup"), fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("cashbook_tab_reports")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                // ======================== TAB 0: TODAY / DAY BOOK ========================
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Date Switcher
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = { selectedDateIso = Phase6DateUtils.addDays(selectedDateIso, -1) },
                                    modifier = Modifier.testTag("cashbook_prev_day_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = Phase6DateUtils.formatDisplayDate(selectedDateIso),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (selectedDateIso != Phase6DateUtils.todayIso()) {
                                        Text(
                                            text = p10Label(isHindi, "आज पर जाएँ", "Tap for Today"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { selectedDateIso = Phase6DateUtils.todayIso() }
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { selectedDateIso = Phase6DateUtils.addDays(selectedDateIso, 1) },
                                    modifier = Modifier.testTag("cashbook_next_day_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                                }
                            }
                        }
                    }

                    // KPI Cards: Money In / Money Out / Closing Balance
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CashBookKpiCard(
                                label = p10Label(isHindi, "कुल आवक (In)", "Money In"),
                                value = "₹${daySummary.totalMoneyIn}",
                                sub = p10Label(
                                    isHindi,
                                    "फीस ₹${daySummary.feeIncomeTotal} + अन्य ₹${daySummary.otherIncomeTotal}",
                                    "Fees ₹${daySummary.feeIncomeTotal} + Other ₹${daySummary.otherIncomeTotal}"
                                ),
                                containerColor = Color(0xFFE8F5E9),
                                accentColor = Color(0xFF1B5E20),
                                modifier = Modifier.weight(1f)
                            )
                            CashBookKpiCard(
                                label = p10Label(isHindi, "कुल खर्च (Out)", "Money Out"),
                                value = "₹${daySummary.totalMoneyOut}",
                                sub = "${daySummary.activeExpenses.size} ${p10Label(isHindi, "एंट्री", "entries")}",
                                containerColor = Color(0xFFFFEBEE),
                                accentColor = Color(0xFFB71C1C),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        CashBookKpiCard(
                            label = p10Label(isHindi, "दिन का बैलेंस (Closing Balance)", "Closing Balance (Opening + In - Out)"),
                            value = "₹${daySummary.closingBalance}",
                            sub = p10Label(
                                isHindi,
                                "शुरुआती: ₹${daySummary.openingBalance} • नकद शेष: ₹${daySummary.expectedClosingCash}",
                                "Opening: ₹${daySummary.openingBalance} • Expected Cash: ₹${daySummary.expectedClosingCash}"
                            ),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Primary Action Buttons
                    if (canEdit) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        editingEntry = null
                                        entryDialogType = CashBookEntryType.EXPENSE
                                        showEntryDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("cashbook_add_expense_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(p10Label(isHindi, "खर्च जोड़ें", "Add Expense"), fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        editingEntry = null
                                        entryDialogType = CashBookEntryType.OTHER_INCOME
                                        showEntryDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("cashbook_add_income_button")
                                ) {
                                    Icon(Icons.Default.AttachMoney, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(p10Label(isHindi, "अन्य आय", "Other Income"), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Section 1: Auto Fee Receipts (from Phase 9)
                    item {
                        Text(
                            text = p10Label(
                                isHindi,
                                "फीस रसीदों से आय (ऑटो) — ${daySummary.feeIncomeReceipts.size}",
                                "Fee Receipts Income (Auto from Phase 9) — ${daySummary.feeIncomeReceipts.size}"
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    }

                    if (daySummary.feeIncomeReceipts.isEmpty()) {
                        item {
                            Text(
                                text = p10Label(isHindi, "इस तिथि को कोई फीस रसीद नहीं।", "No active fee receipts on this date."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(daySummary.feeIncomeReceipts, key = { "fee_${it.id}" }) { rcp ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF1B5E20), modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "${rcp.studentName} (${rcp.className}-${rcp.section})",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = "${rcp.receiptNumber} • ${rcp.paymentMode.label} • ${p10Label(isHindi, "फीस से ऑटो", "Auto from Fees")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "+₹${rcp.amountPaid}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                            }
                        }
                    }

                    // Section 2: Manual Other Income
                    if (daySummary.activeOtherIncomes.isNotEmpty()) {
                        item {
                            Text(
                                text = p10Label(isHindi, "अन्य आय (Manual) — ${daySummary.activeOtherIncomes.size}", "Other Income — ${daySummary.activeOtherIncomes.size}"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                        }
                        items(daySummary.activeOtherIncomes, key = { it.id }) { entry ->
                            CashBookEntryCard(
                                entry = entry,
                                deletedUserUids = uiState.deletedUserUids,
                                isHindi = isHindi,
                                canEdit = canEdit,
                                onEdit = {
                                    editingEntry = entry
                                    entryDialogType = entry.entryType
                                    showEntryDialog = true
                                },
                                onCancel = {
                                    cancelTargetEntry = entry
                                    cancelReasonInput = ""
                                    cancelError = null
                                },
                                onViewPhoto = { previewPhotoBase64 = entry.billPhotoBase64 }
                            )
                        }
                    }

                    // Section 3: Daily Expenses
                    item {
                        Text(
                            text = p10Label(isHindi, "दैनिक खर्च (Expenses) — ${daySummary.activeExpenses.size}", "Daily Expenses — ${daySummary.activeExpenses.size}"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB71C1C)
                        )
                    }
                    if (daySummary.activeExpenses.isEmpty()) {
                        item {
                            Text(
                                text = p10Label(isHindi, "इस तिथि को कोई खर्च दर्ज नहीं है।", "No expenses recorded on this date."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(daySummary.activeExpenses, key = { it.id }) { entry ->
                            CashBookEntryCard(
                                entry = entry,
                                deletedUserUids = uiState.deletedUserUids,
                                isHindi = isHindi,
                                canEdit = canEdit,
                                onEdit = {
                                    editingEntry = entry
                                    entryDialogType = entry.entryType
                                    showEntryDialog = true
                                },
                                onCancel = {
                                    cancelTargetEntry = entry
                                    cancelReasonInput = ""
                                    cancelError = null
                                },
                                onViewPhoto = { previewPhotoBase64 = entry.billPhotoBase64 }
                            )
                        }
                    }

                    // Section 4: Cancelled Entries (Audit transparency — never silently deleted)
                    if (daySummary.cancelledEntries.isNotEmpty()) {
                        item {
                            Text(
                                text = p10Label(isHindi, "रद्द की गई एंट्री (${daySummary.cancelledEntries.size})", "Cancelled Entries (${daySummary.cancelledEntries.size})"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(daySummary.cancelledEntries, key = { it.id }) { entry ->
                            CashBookEntryCard(
                                entry = entry,
                                deletedUserUids = uiState.deletedUserUids,
                                isHindi = isHindi,
                                canEdit = false,
                                onEdit = {},
                                onCancel = {},
                                onViewPhoto = { previewPhotoBase64 = entry.billPhotoBase64 }
                            )
                        }
                    }
                }
            }

            1 -> {
                // ======================== TAB 1: MONTH VIEW ========================
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(onClick = { selectedYearMonth = shiftYearMonth(selectedYearMonth, -1) }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                                }
                                Text(
                                    text = selectedYearMonth,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { selectedYearMonth = shiftYearMonth(selectedYearMonth, 1) }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                                }
                            }
                        }
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CashBookKpiCard(
                                label = p10Label(isHindi, "मासिक आय", "Month In"),
                                value = "₹${monthSummary.totalMoneyIn}",
                                sub = "Fees ₹${monthSummary.totalFeeIncome} + Other ₹${monthSummary.totalOtherIncome}",
                                containerColor = Color(0xFFE8F5E9),
                                accentColor = Color(0xFF1B5E20),
                                modifier = Modifier.weight(1f)
                            )
                            CashBookKpiCard(
                                label = p10Label(isHindi, "मासिक खर्च", "Month Out"),
                                value = "₹${monthSummary.totalMoneyOut}",
                                sub = "${monthSummary.categoryTotals.sumOf { it.entryCount }} ${p10Label(isHindi, "खर्च", "expenses")}",
                                containerColor = Color(0xFFFFEBEE),
                                accentColor = Color(0xFFB71C1C),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        CashBookKpiCard(
                            label = p10Label(isHindi, "शुद्ध मासिक शेष (Net)", "Net Month Balance"),
                            value = "₹${monthSummary.netMonthBalance}",
                            sub = "${monthSummary.activeReceiptsCount} ${p10Label(isHindi, "फीस रसीदें", "fee receipts")}",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Text(
                            text = p10Label(isHindi, "श्रेणी के अनुसार खर्च (Category Totals)", "Category-Wise Expenses"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (monthSummary.categoryTotals.isEmpty()) {
                        item {
                            Text(
                                text = p10Label(isHindi, "इस महीने कोई खर्च नहीं है।", "No expenses recorded in this month."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(monthSummary.categoryTotals, key = { it.category }) { cat ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(cat.category, fontWeight = FontWeight.Bold)
                                        Text("₹${cat.totalAmount} (${cat.sharePercent}%)", fontWeight = FontWeight.Bold, color = Color(0xFFB71C1C))
                                    }
                                    LinearProgressIndicator(
                                        progress = { (cat.sharePercent / 100.0).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Text(
                                        text = "${cat.entryCount} ${p10Label(isHindi, "एंट्री", "entries")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // ======================== TAB 2: DAY CLOSING ========================
                DayClosingTabContent(
                    selectedDateIso = selectedDateIso,
                    onShiftDate = { delta -> selectedDateIso = Phase6DateUtils.addDays(selectedDateIso, delta) },
                    daySummary = daySummary,
                    isHindi = isHindi,
                    canEdit = canEdit,
                    onSaveClosing = { opening, counted, note, cb ->
                        viewModel.saveCashBookDayClosing(selectedDateIso, opening, counted, note, cb)
                    }
                )
            }

            3 -> {
                // ======================== TAB 3: REPORTS, CSV & SETTINGS ========================
                CashBookReportsAndSettingsTab(
                    isHindi = isHindi,
                    canEdit = canEdit,
                    isAdmin = isAdmin,
                    settings = settings,
                    daySummary = daySummary,
                    monthSummary = monthSummary,
                    schoolName = activeSchool.name,
                    onPrintDayBookPdf = { viewModel.generateCashBookDayBookPdf(daySummary) },
                    onPrintMonthSummaryPdf = { viewModel.generateCashBookMonthSummaryPdf(monthSummary) },
                    onPrintCategoryPdf = { viewModel.generateCashBookCategoryExpensePdf(monthSummary) },
                    onExportDayCsv = {
                        shareCsvText(
                            "DayBook_${daySummary.dateIso}.csv",
                            CashBookEngine.buildDayBookCsv(activeSchool.name, daySummary)
                        )
                    },
                    onExportMonthCsv = {
                        shareCsvText(
                            "CategoryExpenses_${monthSummary.yearMonth}.csv",
                            CashBookEngine.buildMonthCategoryCsv(activeSchool.name, monthSummary)
                        )
                    },
                    onSaveSettings = { expCats, incCats, limit, defOpening ->
                        viewModel.saveCashBookSettings(expCats, incCats, limit, defOpening)
                    }
                )
            }
        }
    }

    // Dialog: Add or Edit Cash Book Entry
    if (showEntryDialog) {
        CashBookAddEditEntryDialog(
            isHindi = isHindi,
            dateIso = selectedDateIso,
            entryType = entryDialogType,
            editingEntry = editingEntry,
            settings = settings,
            isAdmin = isAdmin,
            activeAdmins = activeAdmins,
            onDismiss = { showEntryDialog = false },
            onSaveNew = { cat, amt, mode, note, photoB64, adminUid, adminName, apprNote, cb ->
                viewModel.addCashBookEntry(
                    dateIso = selectedDateIso,
                    entryType = entryDialogType,
                    category = cat,
                    amount = amt,
                    paymentMode = mode,
                    note = note,
                    billPhotoBase64 = photoB64,
                    approvedByAdminUid = adminUid,
                    approvedByAdminName = adminName,
                    approvalNote = apprNote,
                    onResult = cb
                )
            },
            onSaveEdit = { orig, cat, amt, mode, note, photoB64, editReason, adminUid, adminName, apprNote, cb ->
                viewModel.editCashBookEntry(
                    original = orig,
                    newCategory = cat,
                    newAmount = amt,
                    newPaymentMode = mode,
                    newNote = note,
                    newBillPhotoBase64 = photoB64,
                    editReason = editReason,
                    approvedByAdminUid = adminUid,
                    approvedByAdminName = adminName,
                    approvalNote = apprNote,
                    onResult = cb
                )
            }
        )
    }

    // Dialog: Cancel Entry with mandatory reason
    if (cancelTargetEntry != null) {
        val target = cancelTargetEntry!!
        AlertDialog(
            onDismissRequest = { cancelTargetEntry = null },
            icon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(p10Label(isHindi, "एंट्री रद्द करें?", "Cancel Cash Book Entry?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${target.category} • ₹${target.amount} (${target.paymentMode.label})", fontWeight = FontWeight.Bold)
                    Text(
                        text = p10Label(
                            isHindi,
                            "एंट्री हटाई नहीं जाएगी — यह रद्द दिखेगी और ऑडिट लॉग में दर्ज होगी।",
                            "Nothing is silently deleted — this entry will be marked Cancelled and recorded in the audit log."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = cancelReasonInput,
                        onValueChange = {
                            cancelReasonInput = it
                            cancelError = null
                        },
                        label = { Text(p10Label(isHindi, "रद्द करने का कारण (अनिवार्य)", "Cancellation reason (required)")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cashbook_cancel_reason_input")
                    )
                    if (cancelError != null) {
                        Text(cancelError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelCashBookEntry(target, cancelReasonInput) { ok, err ->
                            if (ok) cancelTargetEntry = null else cancelError = err
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("cashbook_confirm_cancel_button")
                ) {
                    Text(p10Label(isHindi, "रद्द करें", "Cancel Entry"))
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelTargetEntry = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(p10Label(isHindi, "वापस", "Close"))
                }
            }
        )
    }

    // Dialog: Bill Photo Preview
    if (!previewPhotoBase64.isNullOrBlank()) {
        val bmp = remember(previewPhotoBase64) { ImageUtils.decodeBase64ToBitmap(previewPhotoBase64) }
        AlertDialog(
            onDismissRequest = { previewPhotoBase64 = null },
            title = { Text(p10Label(isHindi, "बिल की फोटो", "Bill Photo")) },
            text = {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Bill photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Text("Unable to load photo.")
                }
            },
            confirmButton = {
                TextButton(onClick = { previewPhotoBase64 = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(p10Label(isHindi, "बंद करें", "Close"))
                }
            }
        )
    }

    // Dialog: Audit Log
    if (showAuditLogDialog) {
        AlertDialog(
            onDismissRequest = { showAuditLogDialog = false },
            title = { Text(p10Label(isHindi, "रोकड़ बही ऑडिट लॉग", "Cash Book Audit Log")) },
            text = {
                if (schoolAuditLogs.isEmpty()) {
                    Text(p10Label(isHindi, "अभी कोई ऑडिट लॉग नहीं।", "No audit events recorded yet."))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(schoolAuditLogs.take(60), key = { it.id }) { log ->
                            CashBookAuditRow(log, uiState.deletedUserUids)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuditLogDialog = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(p10Label(isHindi, "बंद करें", "Close"))
                }
            }
        )
    }
}

@Composable
private fun CashBookKpiCard(
    label: String,
    value: String,
    sub: String,
    containerColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = accentColor, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accentColor, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashBookEntryCard(
    entry: CashBookEntryRecord,
    deletedUserUids: Set<String> = emptySet(),
    isHindi: Boolean,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onViewPhoto: () -> Unit
) {
    val accent = if (entry.isExpense) Color(0xFFB71C1C) else Color(0xFF1B5E20)
    val creatorLabel = com.example.domain.AuthorDisplayHelper.resolveAuthorName(
        authorUid = entry.createdByUid,
        storedName = entry.createdByName,
        deletedUserUids = deletedUserUids
    )
    val editorLabel = com.example.domain.AuthorDisplayHelper.resolveAuthorName(
        authorUid = entry.updatedByUid,
        storedName = entry.updatedByName,
        deletedUserUids = deletedUserUids
    )
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cashbook_entry_card_${entry.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (entry.isCancelled) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Text(
                        text = "${entry.paymentMode.label} • $creatorLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${if (entry.isExpense) "-" else "+"}₹${entry.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (entry.isCancelled) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                    textDecoration = if (entry.isCancelled) TextDecoration.LineThrough else TextDecoration.None
                )
            }

            if (entry.note.isNotBlank()) {
                Text(entry.note, style = MaterialTheme.typography.bodySmall)
            }

            if (entry.approvedByAdminName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text(
                        text = "${p10Label(isHindi, "एडमिन स्वीकृति", "Approved by Admin")}: ${entry.approvedByAdminName}" +
                            if (entry.approvalNote.isNotBlank()) " (${entry.approvalNote})" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (entry.lastEditReason.isNotBlank() && !entry.isCancelled) {
                Text(
                    text = "${p10Label(isHindi, "संपादन कारण", "Edited")}: ${entry.lastEditReason} ($editorLabel)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (entry.isCancelled) {
                Text(
                    text = "${p10Label(isHindi, "रद्द कारण", "Cancelled")}: ${entry.cancelReason} (${entry.cancelledByName})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB71C1C)
                )
            }

            if (entry.billPhotoBase64.isNotBlank() || (canEdit && !entry.isCancelled)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (entry.billPhotoBase64.isNotBlank()) {
                        AssistChip(
                            onClick = onViewPhoto,
                            label = { Text(p10Label(isHindi, "बिल फोटो", "Bill Photo")) },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                    if (canEdit && !entry.isCancelled) {
                        AssistChip(
                            onClick = onEdit,
                            label = { Text(p10Label(isHindi, "बदलें", "Edit")) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        AssistChip(
                            onClick = onCancel,
                            label = { Text(p10Label(isHindi, "रद्द करें", "Cancel")) },
                            leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayClosingTabContent(
    selectedDateIso: String,
    onShiftDate: (Int) -> Unit,
    daySummary: com.example.domain.CashBookDaySummary,
    isHindi: Boolean,
    canEdit: Boolean,
    onSaveClosing: (Int, Int, String, (Boolean, String?) -> Unit) -> Unit
) {
    var openingText by remember(selectedDateIso, daySummary.openingBalance) {
        mutableStateOf(daySummary.openingBalance.toString())
    }
    var countedText by remember(selectedDateIso, daySummary.savedClosing, daySummary.expectedClosingCash) {
        mutableStateOf((daySummary.savedClosing?.countedCash ?: daySummary.expectedClosingCash).toString())
    }
    var noteText by remember(selectedDateIso, daySummary.savedClosing) {
        mutableStateOf(daySummary.savedClosing?.note.orEmpty())
    }
    var saveErr by remember { mutableStateOf<String?>(null) }

    val openingVal = openingText.toIntOrNull() ?: daySummary.openingBalance
    val expectedClosing = openingVal + daySummary.totalCashIn - daySummary.totalCashOut
    val countedVal = countedText.toIntOrNull() ?: 0
    val difference = CashBookEngine.computeDayClosingDifference(expectedClosing, countedVal)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onShiftDate(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                }
                Text(
                    text = "${p10Label(isHindi, "दिन बंद", "Day Closing")} • ${Phase6DateUtils.formatDisplayDate(selectedDateIso)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onShiftDate(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = p10Label(isHindi, "नकद हिसाब (Cash Drawer)", "Cash Drawer Calculation"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = openingText,
                    onValueChange = { openingText = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text(p10Label(isHindi, "शुरुआती नकद बैलेंस (Opening Cash ₹)", "Opening Cash Balance (₹)")) },
                    enabled = canEdit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashbook_opening_balance_input")
                )

                ClosingLineRow(p10Label(isHindi, "+ आज नकद आय (फीस + अन्य)", "+ Cash Money In (Fees + Other)"), "₹${daySummary.totalCashIn}", Color(0xFF1B5E20))
                ClosingLineRow(p10Label(isHindi, "- आज नकद खर्च", "- Cash Money Out (Expenses)"), "₹${daySummary.totalCashOut}", Color(0xFFB71C1C))
                HorizontalDivider()
                ClosingLineRow(
                    p10Label(isHindi, "= अनुमानित अंतिम नकद (Expected Closing)", "= Expected Closing Cash"),
                    "₹$expectedClosing",
                    MaterialTheme.colorScheme.primary,
                    bold = true
                )

                if (daySummary.nonCashInTotal > 0 || daySummary.nonCashOutTotal > 0) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "नॉन-कैश (UPI/बैंक/चेक): आवक ₹${daySummary.nonCashInTotal} • खर्च ₹${daySummary.nonCashOutTotal}",
                            "Non-cash (UPI/Bank/Cheque): In ₹${daySummary.nonCashInTotal} • Out ₹${daySummary.nonCashOutTotal}"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = countedText,
                    onValueChange = { countedText = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text(p10Label(isHindi, "गिने हुए नकद रुपये (Actual Cash Counted ₹)", "Actual Cash Counted (₹)")) },
                    enabled = canEdit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashbook_counted_cash_input")
                )

                val diffBg = when {
                    difference == 0 -> Color(0xFFE8F5E9)
                    difference > 0 -> Color(0xFFFFF8E1)
                    else -> Color(0xFFFFEBEE)
                }
                val diffFg = when {
                    difference == 0 -> Color(0xFF1B5E20)
                    difference > 0 -> Color(0xFFE65100)
                    else -> Color(0xFFB71C1C)
                }
                Surface(
                    color = diffBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = p10Label(isHindi, "अंतर (Counted - Expected)", "Difference (Counted - Expected)"),
                            fontWeight = FontWeight.Bold,
                            color = diffFg
                        )
                        Text(
                            text = when {
                                difference == 0 -> p10Label(isHindi, "₹0 (मिलान सही)", "₹0 (Matched)")
                                difference > 0 -> "+₹$difference (${p10Label(isHindi, "अधिक", "Excess")})"
                                else -> "-₹${-difference} (${p10Label(isHindi, "कम", "Short")})"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = diffFg
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(p10Label(isHindi, "टिप्पणी (यदि कोई अंतर है तो लिखें)", "Closing Note (explain any difference)")) },
                    enabled = canEdit,
                    modifier = Modifier.fillMaxWidth()
                )

                if (daySummary.savedClosing != null) {
                    Text(
                        text = "${p10Label(isHindi, "अंतिम सेव", "Last closed by")}: ${daySummary.savedClosing.closedByName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (saveErr != null) {
                    Text(saveErr!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (canEdit) {
                    Button(
                        onClick = {
                            onSaveClosing(openingVal, countedVal, noteText) { ok, err ->
                                saveErr = if (ok) null else err
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("cashbook_save_day_closing_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(p10Label(isHindi, "दिन बंद सेव करें", "Save Day Closing"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ClosingLineRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontWeight = FontWeight.Bold, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashBookReportsAndSettingsTab(
    isHindi: Boolean,
    canEdit: Boolean,
    isAdmin: Boolean,
    settings: CashBookSettingsConfig,
    daySummary: com.example.domain.CashBookDaySummary,
    monthSummary: com.example.domain.CashBookMonthSummary,
    schoolName: String,
    onPrintDayBookPdf: () -> Unit,
    onPrintMonthSummaryPdf: () -> Unit,
    onPrintCategoryPdf: () -> Unit,
    onExportDayCsv: () -> Unit,
    onExportMonthCsv: () -> Unit,
    onSaveSettings: (List<String>, List<String>, Int, Int) -> Unit
) {
    var expenseCats by remember(settings.expenseCategories) { mutableStateOf(settings.expenseCategories) }
    var newCategoryText by remember { mutableStateOf("") }
    var limitText by remember(settings.expenseApprovalLimitRs) { mutableStateOf(settings.expenseApprovalLimitRs.toString()) }
    var defaultOpeningText by remember(settings.defaultOpeningBalanceRs) { mutableStateOf(settings.defaultOpeningBalanceRs.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = p10Label(isHindi, "A4 PDF रिपोर्ट (प्रिंट / सेव / शेयर)", "A4 PDF Reports (Print / Save / Share)"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onPrintDayBookPdf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("cashbook_pdf_daybook_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(p10Label(isHindi, "दैनिक रोकड़ बही PDF (${daySummary.dateIso})", "Day Book A4 PDF (${daySummary.dateIso})"))
                }
                Button(
                    onClick = onPrintMonthSummaryPdf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("cashbook_pdf_month_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(p10Label(isHindi, "मासिक सारांश PDF (${monthSummary.yearMonth})", "Month Summary A4 PDF (${monthSummary.yearMonth})"))
                }
                Button(
                    onClick = onPrintCategoryPdf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("cashbook_pdf_category_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(p10Label(isHindi, "श्रेणी-वार खर्च PDF (${monthSummary.yearMonth})", "Category-Wise Expense A4 PDF (${monthSummary.yearMonth})"))
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onExportDayCsv,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cashbook_csv_day_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(p10Label(isHindi, "दिन CSV", "Day CSV"))
                    }
                    OutlinedButton(
                        onClick = onExportMonthCsv,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cashbook_csv_month_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(p10Label(isHindi, "माह CSV", "Month CSV"))
                    }
                }
            }
        }

        // Settings Card: Editable Categories + Large Expense Approval Limit
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = p10Label(isHindi, "खर्च श्रेणियाँ व स्वीकृति सीमा", "Expense Categories & Approval Limit"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter(Char::isDigit) },
                    label = {
                        Text(
                            p10Label(
                                isHindi,
                                "बड़े खर्च की एडमिन स्वीकृति सीमा (₹) — केवल Admin बदल सकते हैं",
                                "Large Expense Admin Approval Limit (₹) — Admin/Owner only"
                            )
                        )
                    },
                    enabled = isAdmin,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashbook_approval_limit_input")
                )

                OutlinedTextField(
                    value = defaultOpeningText,
                    onValueChange = { defaultOpeningText = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text(p10Label(isHindi, "डिफ़ॉल्ट शुरुआती बैलेंस (₹)", "Default Opening Balance (₹)")) },
                    enabled = canEdit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = p10Label(isHindi, "खर्च श्रेणियाँ (हटाने के लिए X दबाएँ)", "Expense Categories"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    expenseCats.forEach { cat ->
                        AssistChip(
                            onClick = {
                                if (canEdit && expenseCats.size > 1) {
                                    expenseCats = expenseCats.filterNot { it == cat }
                                }
                            },
                            label = { Text(cat) },
                            trailingIcon = if (canEdit && expenseCats.size > 1) {
                                { Icon(Icons.Default.Close, contentDescription = "Remove $cat", modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                if (canEdit) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newCategoryText,
                            onValueChange = { newCategoryText = it },
                            label = { Text(p10Label(isHindi, "नई श्रेणी जोड़ें", "Add new category")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val clean = newCategoryText.trim()
                                if (clean.isNotEmpty() && expenseCats.none { it.equals(clean, ignoreCase = true) }) {
                                    expenseCats = expenseCats + clean
                                    newCategoryText = ""
                                }
                            },
                            modifier = Modifier.heightIn(min = 52.dp)
                        ) {
                            Text(p10Label(isHindi, "जोड़ें", "Add"))
                        }
                    }

                    Button(
                        onClick = {
                            onSaveSettings(
                                expenseCats,
                                settings.otherIncomeCategories,
                                limitText.toIntOrNull() ?: settings.expenseApprovalLimitRs,
                                defaultOpeningText.toIntOrNull() ?: settings.defaultOpeningBalanceRs
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("cashbook_save_settings_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(p10Label(isHindi, "सेटिंग सेव करें", "Save Cash Book Settings"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashBookAddEditEntryDialog(
    isHindi: Boolean,
    dateIso: String,
    entryType: CashBookEntryType,
    editingEntry: CashBookEntryRecord?,
    settings: CashBookSettingsConfig,
    isAdmin: Boolean,
    activeAdmins: List<com.example.data.cloud.CloudMember>,
    onDismiss: () -> Unit,
    onSaveNew: (String, Int, PaymentMode, String, String, String, String, String, (Boolean, String?) -> Unit) -> Unit,
    onSaveEdit: (CashBookEntryRecord, String, Int, PaymentMode, String, String, String, String, String, String, (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories = if (entryType == CashBookEntryType.EXPENSE) settings.expenseCategories else settings.otherIncomeCategories

    var selectedCategory by remember { mutableStateOf(editingEntry?.category ?: categories.firstOrNull().orEmpty()) }
    var amountText by remember { mutableStateOf(editingEntry?.amount?.toString().orEmpty()) }
    var selectedMode by remember { mutableStateOf(editingEntry?.paymentMode ?: PaymentMode.CASH) }
    var noteText by remember { mutableStateOf(editingEntry?.note.orEmpty()) }
    var billPhotoBase64 by remember { mutableStateOf(editingEntry?.billPhotoBase64.orEmpty()) }
    var editReasonText by remember { mutableStateOf("") }
    var selectedAdminUid by remember { mutableStateOf(editingEntry?.approvedByAdminUid.orEmpty()) }
    var selectedAdminName by remember { mutableStateOf(editingEntry?.approvedByAdminName.orEmpty()) }
    var approvalNote by remember { mutableStateOf(editingEntry?.approvalNote.orEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val amountInt = amountText.toIntOrNull() ?: 0
    val needsApproval = entryType == CashBookEntryType.EXPENSE &&
        !isAdmin &&
        settings.expenseApprovalLimitRs > 0 &&
        amountInt > settings.expenseApprovalLimitRs

    // Zero-permission Android Photo Picker for optional bill photo
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    ImageUtils.encodeUriToBase64Under150Kb(context, uri)
                }
                if (encoded != null) {
                    billPhotoBase64 = encoded
                } else {
                    errorMsg = "Could not load selected photo."
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    editingEntry != null -> p10Label(isHindi, "एंट्री संपादित करें", "Edit Cash Book Entry")
                    entryType == CashBookEntryType.EXPENSE -> p10Label(isHindi, "नया खर्च जोड़ें ($dateIso)", "Add Expense ($dateIso)")
                    else -> p10Label(isHindi, "अन्य आय जोड़ें ($dateIso)", "Add Other Income ($dateIso)")
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = p10Label(isHindi, "श्रेणी चुनें (Category)", "Select Category"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                errorMsg = null
                            },
                            label = { Text(cat) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it.filter(Char::isDigit)
                        errorMsg = null
                    },
                    label = { Text(p10Label(isHindi, "राशि (₹)", "Amount (₹)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashbook_entry_amount_input")
                )

                Text(
                    text = p10Label(isHindi, "भुगतान माध्यम (Mode)", "Payment Mode"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PaymentMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(p10Label(isHindi, "विवरण / नोट (वैकल्पिक)", "Note / Bill details (optional)")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cashbook_entry_note_input")
                )

                // Optional Bill Photo (Android Photo Picker - zero storage permissions)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("cashbook_pick_bill_photo_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (billPhotoBase64.isBlank()) {
                                p10Label(isHindi, "बिल फोटो जोड़ें (वैकल्पिक)", "Attach Bill Photo (optional)")
                            } else {
                                p10Label(isHindi, "बिल फोटो बदलें ✓", "Bill Photo Attached ✓")
                            }
                        )
                    }
                    if (billPhotoBase64.isNotBlank()) {
                        IconButton(onClick = { billPhotoBase64 = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove bill photo")
                        }
                    }
                }

                // Large Expense Admin Approval section
                if (needsApproval) {
                    Surface(
                        color = Color(0xFFFFF8E1),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFE65100))
                                Text(
                                    text = p10Label(
                                        isHindi,
                                        "₹${settings.expenseApprovalLimitRs} से अधिक खर्च के लिए एडमिन स्वीकृति आवश्यक है।",
                                        "Expenses above ₹${settings.expenseApprovalLimitRs} require Admin approval."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100)
                                )
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                activeAdmins.forEach { admin ->
                                    val label = admin.name.ifBlank { admin.email }
                                    FilterChip(
                                        selected = selectedAdminUid == admin.uid,
                                        onClick = {
                                            selectedAdminUid = admin.uid
                                            selectedAdminName = label
                                            errorMsg = null
                                        },
                                        label = { Text(label) },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = approvalNote,
                                onValueChange = { approvalNote = it },
                                label = { Text(p10Label(isHindi, "स्वीकृति टिप्पणी", "Approval note (e.g. Approved on phone)")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Mandatory Edit Reason when editing existing entry
                if (editingEntry != null) {
                    OutlinedTextField(
                        value = editReasonText,
                        onValueChange = {
                            editReasonText = it
                            errorMsg = null
                        },
                        label = { Text(p10Label(isHindi, "बदलाव का कारण (अनिवार्य)", "Reason for editing (required)")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cashbook_edit_reason_input")
                    )
                }

                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toIntOrNull() ?: 0
                    if (editingEntry == null) {
                        onSaveNew(
                            selectedCategory, amt, selectedMode, noteText, billPhotoBase64,
                            selectedAdminUid, selectedAdminName, approvalNote
                        ) { ok, err ->
                            if (ok) onDismiss() else errorMsg = err
                        }
                    } else {
                        onSaveEdit(
                            editingEntry, selectedCategory, amt, selectedMode, noteText, billPhotoBase64,
                            editReasonText, selectedAdminUid, selectedAdminName, approvalNote
                        ) { ok, err ->
                            if (ok) onDismiss() else errorMsg = err
                        }
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cashbook_entry_save_button")
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

@Composable
private fun CashBookAuditRow(log: CashBookAuditLogRecord, deletedUserUids: Set<String> = emptySet()) {
    val actorLabel = com.example.domain.AuthorDisplayHelper.resolveAuthorName(
        authorUid = log.actorUid,
        storedName = log.actorName,
        deletedUserUids = deletedUserUids
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(log.action, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(log.dateIso, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(log.summary, style = MaterialTheme.typography.bodySmall)
            if (log.reason.isNotBlank()) {
                Text("Reason: ${log.reason}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Text("By: $actorLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shiftYearMonth(yearMonth: String, deltaMonths: Int): String {
    val parts = yearMonth.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val total = (y * 12 + (m - 1)) + deltaMonths
    val ny = total / 12
    val nm = (total % 12) + 1
    return String.format(Locale.US, "%04d-%02d", ny, nm)
}
