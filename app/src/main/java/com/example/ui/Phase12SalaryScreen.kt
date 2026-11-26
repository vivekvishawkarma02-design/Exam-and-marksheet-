package com.example.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.domain.PaymentMode
import com.example.domain.Phase6DateUtils
import com.example.domain.SalaryAdvanceStatus
import com.example.domain.SalaryAuditLogRecord
import com.example.domain.SalaryComponentItem
import com.example.domain.SalaryMonthlyRecord
import com.example.domain.SalaryPaymentStatus
import com.example.domain.SalaryPerDayBasis
import com.example.domain.SalaryRuleConfig
import com.example.domain.SalarySlipPageSize
import com.example.domain.StaffSalaryAdvanceRecord
import com.example.domain.StaffSalaryProfileRecord
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Phase12SalaryScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isHindi = uiState.phase10HindiLabels
    val schoolDocId = uiState.activeSchoolDocId
    val canView = uiState.canViewSalary
    val canEdit = uiState.canEditSalary
    val isOwner = uiState.isSalaryOwner

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedYearMonth by remember {
        mutableStateOf(Phase6DateUtils.todayIso().take(7).ifBlank { "2026-04" })
    }

    val ruleConfig = remember(uiState.salaryRuleConfigs, schoolDocId) {
        uiState.salaryRuleConfigForSchool(schoolDocId)
    }
    val isMonthLocked = remember(uiState.salaryMonthLocks, schoolDocId, selectedYearMonth) {
        uiState.isSalaryMonthLocked(schoolDocId, selectedYearMonth)
    }
    val profilesForSchool = remember(uiState.staffSalaryProfiles, schoolDocId) {
        uiState.staffSalaryProfiles.filter { it.appliesToSchool(schoolDocId) }
    }
    val monthlyRecordsForMonth = remember(uiState.salaryMonthlyRecords, schoolDocId, selectedYearMonth) {
        uiState.salaryMonthlyRecords
            .filter { it.schoolId == schoolDocId && it.yearMonth == selectedYearMonth }
            .sortedBy { it.staffName.lowercase() }
    }
    val advancesForSchool = remember(uiState.staffSalaryAdvances, schoolDocId) {
        uiState.staffSalaryAdvances.filter { it.schoolId == schoolDocId }
    }
    val auditLogsForSchool = remember(uiState.salaryAuditLogs, schoolDocId) {
        uiState.salaryAuditLogs.filter { it.schoolId == schoolDocId }
    }

    var editingProfile by remember { mutableStateOf<StaffSalaryProfileRecord?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var adjustingRecord by remember { mutableStateOf<SalaryMonthlyRecord?>(null) }
    var payingRecord by remember { mutableStateOf<SalaryMonthlyRecord?>(null) }
    var showIssueAdvanceDialog by remember { mutableStateOf(false) }
    var repayingAdvance by remember { mutableStateOf<StaffSalaryAdvanceRecord?>(null) }
    var showLockMonthDialog by remember { mutableStateOf(false) }
    var showComputeLockedMonthDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = p10Label(isHindi, "स्टाफ वेतन (Staff Salary)", "Staff Salary"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = p10Label(
                                isHindi,
                                "${uiState.activeSchool.name} • गोपनीय (Owner / अधिकृत एडमिन)",
                                "${uiState.activeSchool.name} • Sensitive Access"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("salary_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Phase10LanguageToggleChip(
                        isHindi = isHindi,
                        onToggle = { viewModel.togglePhase10HindiLabels() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        if (!canView) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = p10Label(
                        isHindi,
                        "वेतन मॉड्यूल गोपनीय है। केवल Owner या Owner द्वारा अनुमति प्राप्त एडमिन इसे देख सकते हैं।",
                        "Salary data is restricted. Only the Owner or an explicitly granted Admin has access."
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            p10Label(
                                isHindi,
                                "मासिक वेतन (${monthlyRecordsForMonth.size})",
                                "Monthly Sheet (${monthlyRecordsForMonth.size})"
                            )
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("salary_tab_monthly")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            p10Label(
                                isHindi,
                                "स्टाफ सेटअप (${profilesForSchool.size})",
                                "Staff Setup (${profilesForSchool.size})"
                            )
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("salary_tab_setup")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        val activeAdvCount = advancesForSchool.count { it.status == SalaryAdvanceStatus.ACTIVE }
                        Text(
                            p10Label(
                                isHindi,
                                "अग्रिम / लोन ($activeAdvCount)",
                                "Advances ($activeAdvCount)"
                            )
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("salary_tab_advances")
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = {
                        Text(p10Label(isHindi, "नियम व लॉग", "Rules & Audit"))
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("salary_tab_rules")
                )
            }

            when (selectedTab) {
                0 -> MonthlySalarySheetTab(
                    isHindi = isHindi,
                    canEdit = canEdit,
                    isOwner = isOwner,
                    selectedYearMonth = selectedYearMonth,
                    onChangeYearMonth = { selectedYearMonth = it },
                    isMonthLocked = isMonthLocked,
                    ruleConfig = ruleConfig,
                    records = monthlyRecordsForMonth,
                    profilesCount = profilesForSchool.count { it.active },
                    onPullAndCompute = {
                        if (isMonthLocked) {
                            showComputeLockedMonthDialog = true
                        } else {
                            viewModel.pullAttendanceAndComputeMonthlySalarySheet(selectedYearMonth)
                        }
                    },
                    onToggleLockClick = { showLockMonthDialog = true },
                    onEditRulesClick = { selectedTab = 3 },
                    onGoToSetupClick = { selectedTab = 1 },
                    onAdjustRecord = { adjustingRecord = it },
                    onMarkPaidRecord = { payingRecord = it },
                    onPrintSlip = { rec, size ->
                        viewModel.generateSalarySlipPdfAndPreview(rec, size)
                    },
                    onPrintMonthSummary = {
                        viewModel.generateSalaryMonthSummaryPdfAndPreview(selectedYearMonth)
                    },
                    onExportCsv = {
                        viewModel.exportSalaryMonthCsvFile(selectedYearMonth) { csvFile ->
                            runCatching {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    csvFile
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Salary CSV"))
                            }
                        }
                    }
                )

                1 -> StaffSalarySetupTab(
                    isHindi = isHindi,
                    canEdit = canEdit,
                    profiles = profilesForSchool,
                    onSyncTeachers = { viewModel.syncTeachersToSalaryProfiles() },
                    onAddStaff = {
                        editingProfile = null
                        showProfileDialog = true
                    },
                    onEditStaff = {
                        editingProfile = it
                        showProfileDialog = true
                    },
                    onDeleteStaff = { viewModel.deleteStaffSalaryProfileForActiveSchool(it) }
                )

                2 -> StaffSalaryAdvancesTab(
                    isHindi = isHindi,
                    canEdit = canEdit,
                    advances = advancesForSchool,
                    profiles = profilesForSchool,
                    onIssueAdvanceClick = { showIssueAdvanceDialog = true },
                    onRepayAdvanceClick = { repayingAdvance = it }
                )

                3 -> SalaryRulesAndAuditTab(
                    isHindi = isHindi,
                    canEdit = canEdit,
                    isOwner = isOwner,
                    ruleConfig = ruleConfig,
                    auditLogs = auditLogsForSchool,
                    deletedUserUids = uiState.deletedUserUids,
                    onSaveRuleConfig = { updated ->
                        viewModel.saveSalaryRuleConfigForActiveSchool(updated)
                    }
                )
            }
        }
    }

    if (showProfileDialog) {
        StaffSalaryProfileEditorDialog(
            existing = editingProfile,
            ruleConfig = ruleConfig,
            schoolDocId = schoolDocId,
            isHindi = isHindi,
            onDismiss = { showProfileDialog = false },
            onSave = { updatedProfile ->
                viewModel.saveStaffSalaryProfileForActiveSchool(updatedProfile) { ok, _ ->
                    if (ok) showProfileDialog = false
                }
            }
        )
    }

    if (adjustingRecord != null) {
        SalaryRecordAdjustDialog(
            record = adjustingRecord!!,
            isMonthLocked = isMonthLocked,
            isHindi = isHindi,
            onDismiss = { adjustingRecord = null },
            onSave = { p, a, lv, hd, lt, advDed, manAdj, remark, lockReason, setError ->
                viewModel.updateSingleStaffMonthlySalary(
                    existingRecord = adjustingRecord!!,
                    presentDays = p,
                    absentDays = a,
                    onLeaveDays = lv,
                    halfDays = hd,
                    lateDays = lt,
                    advanceDeductionRs = advDed,
                    manualAdjustmentRs = manAdj,
                    adjustmentRemark = remark,
                    lockOverrideReason = lockReason
                ) { ok, msg ->
                    if (ok) adjustingRecord = null else setError(msg)
                }
            }
        )
    }

    if (payingRecord != null) {
        SalaryMarkPaidDialog(
            record = payingRecord!!,
            isMonthLocked = isMonthLocked,
            isHindi = isHindi,
            onDismiss = { payingRecord = null },
            onConfirmPay = { dateIso, mode, ref, lockReason, setError ->
                viewModel.markStaffSalaryPaid(
                    record = payingRecord!!,
                    paidDateIso = dateIso,
                    paymentMode = mode,
                    paymentReference = ref,
                    lockOverrideReason = lockReason
                ) { ok, msg ->
                    if (ok) payingRecord = null else setError(msg)
                }
            }
        )
    }

    if (showIssueAdvanceDialog) {
        IssueSalaryAdvanceDialog(
            profiles = profilesForSchool,
            isHindi = isHindi,
            onDismiss = { showIssueAdvanceDialog = false },
            onSave = { staff, dateIso, principal, instalment, mode, note, setError ->
                viewModel.issueStaffSalaryAdvance(
                    staffId = staff.staffId,
                    staffName = staff.staffName,
                    staffUid = staff.linkedMemberUid,
                    advanceDateIso = dateIso,
                    principalAmountRs = principal,
                    monthlyInstalmentRs = instalment,
                    paymentMode = mode,
                    note = note
                ) { ok, msg ->
                    if (ok) showIssueAdvanceDialog = false else setError(msg)
                }
            }
        )
    }

    if (repayingAdvance != null) {
        ManualAdvanceRepaymentDialog(
            advance = repayingAdvance!!,
            isHindi = isHindi,
            onDismiss = { repayingAdvance = null },
            onSave = { amountRs, dateIso, mode, note, setError ->
                viewModel.recordStaffAdvanceManualRepayment(
                    advance = repayingAdvance!!,
                    repaymentAmountRs = amountRs,
                    dateIso = dateIso,
                    paymentMode = mode,
                    note = note
                ) { ok, msg ->
                    if (ok) repayingAdvance = null else setError(msg)
                }
            }
        )
    }

    if (showLockMonthDialog) {
        SalaryMonthLockDialog(
            yearMonth = selectedYearMonth,
            currentlyLocked = isMonthLocked,
            isHindi = isHindi,
            onDismiss = { showLockMonthDialog = false },
            onConfirm = { note, setError ->
                viewModel.setFinishedSalaryMonthLock(
                    yearMonth = selectedYearMonth,
                    locked = !isMonthLocked,
                    note = note
                ) { ok, msg ->
                    if (ok) showLockMonthDialog = false else setError(msg)
                }
            }
        )
    }

    if (showComputeLockedMonthDialog) {
        PostLockReasonPromptDialog(
            yearMonth = selectedYearMonth,
            isHindi = isHindi,
            onDismiss = { showComputeLockedMonthDialog = false },
            onConfirm = { reason ->
                viewModel.pullAttendanceAndComputeMonthlySalarySheet(
                    yearMonth = selectedYearMonth,
                    lockOverrideReason = reason
                ) { ok, _ ->
                    if (ok) showComputeLockedMonthDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthlySalarySheetTab(
    isHindi: Boolean,
    canEdit: Boolean,
    isOwner: Boolean,
    selectedYearMonth: String,
    onChangeYearMonth: (String) -> Unit,
    isMonthLocked: Boolean,
    ruleConfig: SalaryRuleConfig,
    records: List<SalaryMonthlyRecord>,
    profilesCount: Int,
    onPullAndCompute: () -> Unit,
    onToggleLockClick: () -> Unit,
    onEditRulesClick: () -> Unit,
    onGoToSetupClick: () -> Unit,
    onAdjustRecord: (SalaryMonthlyRecord) -> Unit,
    onMarkPaidRecord: (SalaryMonthlyRecord) -> Unit,
    onPrintSlip: (SalaryMonthlyRecord, SalarySlipPageSize) -> Unit,
    onPrintMonthSummary: () -> Unit,
    onExportCsv: () -> Unit
) {
    val totalNet = records.sumOf { it.netPayableRs }
    val totalPaid = records.filter { it.status == SalaryPaymentStatus.PAID }.sumOf { it.netPayableRs }
    val totalPending = (totalNet - totalPaid).coerceAtLeast(0)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month Selector + Owner Lock Control
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { onChangeYearMonth(stepYearMonth(selectedYearMonth, -1)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                            }
                            Text(
                                text = selectedYearMonth,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { onChangeYearMonth(stepYearMonth(selectedYearMonth, 1)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                            }
                        }

                        if (isOwner) {
                            OutlinedButton(
                                onClick = onToggleLockClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("salary_lock_month_button")
                            ) {
                                Icon(
                                    imageVector = if (isMonthLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isMonthLocked) FailRed else PassGreen
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isMonthLocked) p10Label(isHindi, "माह लॉक है", "Locked")
                                    else p10Label(isHindi, "माह लॉक करें", "Lock Month"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else if (isMonthLocked) {
                            Surface(
                                color = FailRedContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = FailRed, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = p10Label(isHindi, "माह लॉक है", "Month Locked"),
                                        color = FailRed,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Active Attendance Deduction Rule Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p10Label(
                                        isHindi,
                                        "सक्रिय उपस्थिति व कटौती नियम (Phase 7 से जुड़ा):",
                                        "Active Attendance Deduction Rule (linked to Phase 7):"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = ruleConfig.ruleSummaryText(isHindi),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(
                                onClick = onEditRulesClick,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(isHindi, "नियम", "Rule"))
                            }
                        }
                    }

                    // Monthly KPI Totals
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(p10Label(isHindi, "कुल शुद्ध वेतन", "Total Net"), style = MaterialTheme.typography.labelSmall)
                            Text("₹$totalNet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(p10Label(isHindi, "भुगतान पूर्ण (Cash Book में)", "Paid (in Cash Book)"), style = MaterialTheme.typography.labelSmall)
                            Text("₹$totalPaid", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PassGreen)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(p10Label(isHindi, "बकाया वेतन", "Pending"), style = MaterialTheme.typography.labelSmall)
                            Text("₹$totalPending", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (totalPending > 0) AbsentAmber else PassGreen)
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (canEdit) {
                            Button(
                                onClick = onPullAndCompute,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("salary_compute_sheet_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    p10Label(
                                        isHindi,
                                        "उपस्थिति से वेतन गणना करें",
                                        "Pull Attendance & Compute"
                                    )
                                )
                            }
                        }
                        if (records.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onPrintMonthSummary,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("salary_month_pdf_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(isHindi, "माह सारांश PDF", "Month PDF"))
                            }
                            OutlinedButton(
                                onClick = onExportCsv,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("salary_export_csv_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("CSV")
                            }
                        }
                    }
                }
            }
        }

        if (records.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (profilesCount == 0) {
                                p10Label(
                                    isHindi,
                                    "अभी किसी स्टाफ का वेतन सेटअप नहीं है। पहले 'स्टाफ सेटअप' टैब में शिक्षकों को इम्पोर्ट या जोड़ें।",
                                    "No staff salary profiles found yet. Import teachers or add staff in the Staff Setup tab first."
                                )
                            } else {
                                p10Label(
                                    isHindi,
                                    "$selectedYearMonth के लिए वेतन पत्रक तैयार करने हेतु ऊपर 'उपस्थिति से वेतन गणना करें' दबाएँ।",
                                    "Tap 'Pull Attendance & Compute' above to generate the salary sheet for $selectedYearMonth."
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (profilesCount == 0) {
                            Button(
                                onClick = onGoToSetupClick,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(p10Label(isHindi, "स्टाफ सेटअप खोलें", "Open Staff Setup"))
                            }
                        }
                    }
                }
            }
        } else {
            items(records, key = { it.id }) { rec ->
                StaffMonthlySalaryCard(
                    record = rec,
                    isHindi = isHindi,
                    canEdit = canEdit,
                    onAdjust = { onAdjustRecord(rec) },
                    onMarkPaid = { onMarkPaidRecord(rec) },
                    onPrintA4 = { onPrintSlip(rec, SalarySlipPageSize.A4) },
                    onPrintA5 = { onPrintSlip(rec, SalarySlipPageSize.A5) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffMonthlySalaryCard(
    record: SalaryMonthlyRecord,
    isHindi: Boolean,
    canEdit: Boolean,
    onAdjust: () -> Unit,
    onMarkPaid: () -> Unit,
    onPrintA4: () -> Unit,
    onPrintA5: () -> Unit
) {
    val isPaid = record.status == SalaryPaymentStatus.PAID
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isPaid) PassGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.staffName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${record.designation} • Basic ₹${record.basicSalaryRs} + Allowances ₹${record.totalAllowancesRs}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = if (isPaid) PassGreenContainer else AbsentAmberContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isPaid) p10Label(isHindi, "भुगतान पूर्ण", "PAID")
                        else p10Label(isHindi, "बकाया", "PENDING"),
                        color = if (isPaid) PassGreen else AbsentAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Attendance counts row
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = p10Label(
                        isHindi,
                        "उपस्थिति: उपस्थित ${record.presentDays} • अनुपस्थित ${record.absentDays} • छुट्टी ${record.onLeaveDays} (मुफ्त ${record.freeLeavesApplied}) • हाफ-डे ${record.halfDays} • देरी ${record.lateDays} • कटौती योग्य दिन: ${record.unpaidAbsenceDays}",
                        "Attendance: P ${record.presentDays} • A ${record.absentDays} • Leave ${record.onLeaveDays} (Free ${record.freeLeavesApplied}) • Half ${record.halfDays} • Late ${record.lateDays} • Unpaid Days: ${record.unpaidAbsenceDays}"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Earnings & Deductions summary
            Text(
                text = "Gross: ₹${record.grossEarningsRs}  |  Att. Ded: -₹${record.attendanceDeductionRs}  |  Fixed Ded: -₹${record.totalFixedDeductionsRs}  |  Advance Ded: -₹${record.advanceDeductionRs}",
                style = MaterialTheme.typography.bodySmall
            )
            if (record.manualAdjustmentRs != 0) {
                val sign = if (record.manualAdjustmentRs > 0) "+" else ""
                Text(
                    text = p10Label(
                        isHindi,
                        "हस्त समायोजन (Manual Adjustment): ${sign}₹${record.manualAdjustmentRs} • कारण: ${record.adjustmentRemark}",
                        "Manual Adjustment: ${sign}₹${record.manualAdjustmentRs} • Remark: ${record.adjustmentRemark}"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (record.lastLockOverrideReason.isNotBlank()) {
                Text(
                    text = p10Label(
                        isHindi,
                        "लॉक के बाद संशोधन कारण: ${record.lastLockOverrideReason}",
                        "Post-lock change reason: ${record.lastLockOverrideReason}"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = FailRed
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = p10Label(isHindi, "शुद्ध देय वेतन: ₹${record.netPayableRs}", "Net Salary: ₹${record.netPayableRs}"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isPaid) {
                    Text(
                        text = "${record.paidDateIso} • ${record.paymentMode.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PassGreen
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canEdit) {
                    OutlinedButton(
                        onClick = onAdjust,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("salary_adjust_${record.staffId}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "संशोधन", "Adjust"))
                    }
                    Button(
                        onClick = onMarkPaid,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("salary_pay_${record.staffId}")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (isPaid) p10Label(isHindi, "भुगतान बदलें", "Update Pay")
                            else p10Label(isHindi, "भुगतान करें", "Mark Paid")
                        )
                    }
                }
                OutlinedButton(
                    onClick = onPrintA4,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("salary_slip_a4_${record.staffId}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("A4 Slip")
                }
                OutlinedButton(
                    onClick = onPrintA5,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("salary_slip_a5_${record.staffId}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("A5 Slip")
                }
            }
        }
    }
}

@Composable
private fun StaffSalarySetupTab(
    isHindi: Boolean,
    canEdit: Boolean,
    profiles: List<StaffSalaryProfileRecord>,
    onSyncTeachers: () -> Unit,
    onAddStaff: () -> Unit,
    onEditStaff: (StaffSalaryProfileRecord) -> Unit,
    onDeleteStaff: (StaffSalaryProfileRecord) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (canEdit) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSyncTeachers,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("salary_sync_teachers_button")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(p10Label(isHindi, "शिक्षक इम्पोर्ट करें", "Import Teachers"))
                    }
                    Button(
                        onClick = onAddStaff,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("salary_add_staff_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(p10Label(isHindi, "नया स्टाफ जोड़ें", "Add Staff"))
                    }
                }
            }
        }

        if (profiles.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "शिक्षकों या गैर-शैक्षणिक स्टाफ (क्लर्क, ड्राइवर, चपरासी आदि) का वेतन सेटअप जोड़ने के लिए ऊपर बटन दबाएँ।",
                            "Tap 'Import Teachers' or 'Add Staff' above to configure basic salary, allowances, and deductions."
                        ),
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(profiles, key = { it.id }) { prof ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prof.staffName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${prof.designation} • " +
                                        (if (prof.allSchools) p10Label(isHindi, "सभी स्कूल", "All Schools") else p10Label(isHindi, "यह स्कूल", "This School")) +
                                        (if (prof.joiningDateIso.isNotBlank()) " • Joining: ${prof.joiningDateIso}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canEdit) {
                                Row {
                                    IconButton(
                                        onClick = { onEditStaff(prof) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit staff salary")
                                    }
                                    IconButton(
                                        onClick = { onDeleteStaff(prof) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete staff salary", tint = FailRed)
                                    }
                                }
                            }
                        }
                        Text(
                            text = "Basic: ₹${prof.basicMonthlySalaryRs}  |  Allowances: +₹${prof.totalAllowancesRs}  |  Fixed Deductions: -₹${prof.totalFixedDeductionsRs}  |  Gross: ₹${prof.grossMonthlySalaryRs}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffSalaryAdvancesTab(
    isHindi: Boolean,
    canEdit: Boolean,
    advances: List<StaffSalaryAdvanceRecord>,
    profiles: List<StaffSalaryProfileRecord>,
    onIssueAdvanceClick: () -> Unit,
    onRepayAdvanceClick: (StaffSalaryAdvanceRecord) -> Unit
) {
    val totalOutstanding = advances
        .filter { it.status == SalaryAdvanceStatus.ACTIVE }
        .sumOf { it.remainingBalanceRs }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(p10Label(isHindi, "कुल बकाया अग्रिम / लोन", "Total Outstanding Advances"), style = MaterialTheme.typography.labelSmall)
                        Text("₹$totalOutstanding", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    if (canEdit && profiles.isNotEmpty()) {
                        Button(
                            onClick = onIssueAdvanceClick,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("salary_issue_advance_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(p10Label(isHindi, "अग्रिम दें", "Give Advance"))
                        }
                    }
                }
            }
        }

        if (advances.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "कोई अग्रिम या लोन दर्ज नहीं है। अग्रिम देने पर हर महीने की वेतन पर्ची से किस्त अपने-आप कट सकती है।",
                            "No staff advances or loans recorded yet. Advances can deduct an automatic monthly instalment on the salary sheet."
                        ),
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(advances, key = { it.id }) { adv ->
                val isClosed = adv.status == SalaryAdvanceStatus.CLOSED || adv.remainingBalanceRs <= 0
                val progress = if (adv.principalAmountRs > 0) {
                    (adv.repaidAmountRs.toFloat() / adv.principalAmountRs.toFloat()).coerceIn(0f, 1f)
                } else 1f

                Card(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(adv.staffName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${adv.advanceDateIso} • ${adv.paymentMode.label}" +
                                        if (adv.note.isNotBlank()) " • ${adv.note}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = if (isClosed) PassGreenContainer else AbsentAmberContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isClosed) adv.status.hindiLabel else "Balance ₹${adv.remainingBalanceRs}",
                                    color = if (isClosed) PassGreen else AbsentAmber,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )

                        Text(
                            text = p10Label(
                                isHindi,
                                "मूल राशि: ₹${adv.principalAmountRs} • चुकाया गया: ₹${adv.repaidAmountRs} • शेष: ₹${adv.remainingBalanceRs} • मासिक किस्त: ₹${adv.monthlyInstalmentRs}",
                                "Principal: ₹${adv.principalAmountRs} • Repaid: ₹${adv.repaidAmountRs} • Balance: ₹${adv.remainingBalanceRs} • Monthly Instalment: ₹${adv.monthlyInstalmentRs}"
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (adv.repayments.isNotEmpty()) {
                            Text(
                                text = adv.repayments.takeLast(3).joinToString("  |  ") {
                                    "${it.dateIso}: ₹${it.amountRs} (${if (it.isAutoFromSalarySlip) "Auto Slip" else "Manual"})"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (canEdit && !isClosed) {
                            OutlinedButton(
                                onClick = { onRepayAdvanceClick(adv) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(p10Label(isHindi, "किस्त / वापसी दर्ज करें", "Record Repayment"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalaryRulesAndAuditTab(
    isHindi: Boolean,
    canEdit: Boolean,
    isOwner: Boolean,
    ruleConfig: SalaryRuleConfig,
    auditLogs: List<SalaryAuditLogRecord>,
    deletedUserUids: Set<String> = emptySet(),
    onSaveRuleConfig: (SalaryRuleConfig) -> Unit
) {
    var perDayBasis by remember(ruleConfig) { mutableStateOf(ruleConfig.perDayBasis) }
    var freeLeavesText by remember(ruleConfig) { mutableStateOf(ruleConfig.freeLeavesPerMonth.toString()) }
    var halfDayWeightText by remember(ruleConfig) { mutableStateOf(ruleConfig.halfDayAbsenceWeight.toString()) }
    var halfDayUsesFreeLeave by remember(ruleConfig) { mutableStateOf(ruleConfig.halfDayConsumesFreeLeave) }
    var lateDaysPenaltyText by remember(ruleConfig) { mutableStateOf(ruleConfig.lateDaysPerHalfDayPenalty.toString()) }
    var fixedPerDayText by remember(ruleConfig) { mutableStateOf(ruleConfig.fixedPerAbsentDayDeductionRs.toString()) }
    var allowTeacherSelfView by remember(ruleConfig) { mutableStateOf(ruleConfig.allowTeachersToViewOwnSlips) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "स्कूल वेतन व उपस्थिति कटौती नियम",
                            "Per-School Attendance & Salary Rules"
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SalaryPerDayBasis.entries.forEach { basis ->
                            FilterChip(
                                selected = perDayBasis == basis,
                                onClick = { if (canEdit) perDayBasis = basis },
                                label = { Text(if (isHindi) basis.hindiLabel else basis.englishLabel) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = freeLeavesText,
                            onValueChange = { if (canEdit) freeLeavesText = it.filter { c -> c.isDigit() } },
                            label = { Text(p10Label(isHindi, "मुफ्त छुट्टी / माह", "Free Leaves / Month")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = halfDayWeightText,
                            onValueChange = { if (canEdit) halfDayWeightText = it },
                            label = { Text(p10Label(isHindi, "हाफ-डे भार (0.5)", "Half-Day Weight")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lateDaysPenaltyText,
                            onValueChange = { if (canEdit) lateDaysPenaltyText = it.filter { c -> c.isDigit() } },
                            label = { Text(p10Label(isHindi, "कितनी देरी पर 0.5 दिन कटे (0=बंद)", "Lates per 0.5d Ded (0=Off)")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fixedPerDayText,
                            onValueChange = { if (canEdit) fixedPerDayText = it.filter { c -> c.isDigit() } },
                            label = { Text(p10Label(isHindi, "निश्चित ₹/दिन (0=ऑटो)", "Fixed ₹/Day (0=Auto)")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "हाफ-डे को पहले मुफ्त छुट्टी में समायोजित करें",
                                "Half-days consume remaining free leave balance first"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = halfDayUsesFreeLeave,
                            onCheckedChange = { if (canEdit) halfDayUsesFreeLeave = it }
                        )
                    }

                    HorizontalDivider()

                    // Owner-only teacher self-slip toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "शिक्षकों को अपनी वेतन पर्ची देखने की अनुमति दें (केवल Owner)",
                                    "Allow teachers to view their own salary slips (Owner only)"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "चालू करने पर शिक्षक केवल अपनी स्वयं की वेतन पर्ची देख और डाउनलोड कर सकेंगे।",
                                    "When enabled, teachers see only their own salary slips on Teacher Home."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowTeacherSelfView,
                            enabled = isOwner,
                            onCheckedChange = { if (isOwner) allowTeacherSelfView = it },
                            modifier = Modifier.testTag("salary_teacher_self_view_switch")
                        )
                    }

                    if (canEdit) {
                        Button(
                            onClick = {
                                onSaveRuleConfig(
                                    ruleConfig.copy(
                                        perDayBasis = perDayBasis,
                                        freeLeavesPerMonth = freeLeavesText.toIntOrNull()?.coerceIn(0, 31) ?: 1,
                                        halfDayAbsenceWeight = halfDayWeightText.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.5,
                                        halfDayConsumesFreeLeave = halfDayUsesFreeLeave,
                                        lateDaysPerHalfDayPenalty = lateDaysPenaltyText.toIntOrNull()?.coerceIn(0, 31) ?: 3,
                                        fixedPerAbsentDayDeductionRs = fixedPerDayText.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                        allowTeachersToViewOwnSlips = if (isOwner) allowTeacherSelfView else ruleConfig.allowTeachersToViewOwnSlips
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("salary_save_rules_button")
                        ) {
                            Text(p10Label(isHindi, "वेतन नियम सेव करें", "Save Salary Rules"))
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = p10Label(isHindi, "वेतन ऑडिट लॉग (${auditLogs.size})", "Salary Audit Log (${auditLogs.size})"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(auditLogs.take(40), key = { it.id }) { log ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(log.action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(log.yearMonth, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(log.summary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    if (log.reason.isNotBlank()) {
                        Text("Reason: ${log.reason}", style = MaterialTheme.typography.bodySmall)
                    }
                    val actorLabel = com.example.domain.AuthorDisplayHelper.resolveAuthorName(
                        authorUid = log.actorUid,
                        storedName = log.actorName,
                        deletedUserUids = deletedUserUids,
                        fallback = ""
                    )
                    if (actorLabel.isNotBlank()) {
                        Text("By: $actorLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Displayed on Teacher Home (`DashboardScreen.kt`) when the Owner enables teacher self-slip access
 * and the logged-in teacher has at least one salary slip.
 */
@Composable
fun TeacherOwnSalarySlipsCard(
    slips: List<SalaryMonthlyRecord>,
    isHindi: Boolean,
    onPrintSlip: (SalaryMonthlyRecord, SalarySlipPageSize) -> Unit,
    modifier: Modifier = Modifier
) {
    if (slips.isEmpty()) return
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("teacher_own_salary_slips_card"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = p10Label(isHindi, "मेरी वेतन पर्ची (My Salary Slips)", "My Salary Slips"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = p10Label(
                            isHindi,
                            "Owner द्वारा आपके लिए सक्षम • केवल आपकी स्वयं की पर्ची",
                            "Enabled by Owner • Your personal salary slips"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            slips.take(4).forEach { slip ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${slip.yearMonth}  •  Net ₹${slip.netPayableRs}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "P:${slip.presentDays} A:${slip.absentDays} Lv:${slip.onLeaveDays} • ${slip.status.wireValue}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPrintSlip(slip, SalarySlipPageSize.A4) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("A4 PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { onPrintSlip(slip, SalarySlipPageSize.A5) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("A5 PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffSalaryProfileEditorDialog(
    existing: StaffSalaryProfileRecord?,
    ruleConfig: SalaryRuleConfig,
    schoolDocId: String,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (StaffSalaryProfileRecord) -> Unit
) {
    var name by remember { mutableStateOf(existing?.staffName.orEmpty()) }
    var designation by remember { mutableStateOf(existing?.designation ?: "Teacher") }
    var isTeaching by remember { mutableStateOf(existing?.isTeachingStaff ?: true) }
    var joiningDate by remember { mutableStateOf(existing?.joiningDateIso.orEmpty()) }
    var basicSalary by remember { mutableStateOf((existing?.basicMonthlySalaryRs ?: 15000).toString()) }
    var allSchools by remember { mutableStateOf(existing?.allSchools ?: false) }

    val allowances = remember {
        mutableStateListOf<SalaryComponentItem>().apply {
            addAll(existing?.allowances?.takeIf { it.isNotEmpty() } ?: ruleConfig.defaultAllowances)
        }
    }
    val deductions = remember {
        mutableStateListOf<SalaryComponentItem>().apply {
            addAll(existing?.fixedDeductions?.takeIf { it.isNotEmpty() } ?: ruleConfig.defaultDeductions)
        }
    }
    var newAllowanceName by remember { mutableStateOf("") }
    var newDeductionName by remember { mutableStateOf("") }

    val roles = listOf("Teacher", "Clerk", "Accountant", "Driver", "Peon", "Guard")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) p10Label(isHindi, "नया स्टाफ वेतन सेटअप", "Add Staff Salary Setup")
                else p10Label(isHindi, "वेतन सेटअप संपादित करें", "Edit Staff Salary Setup")
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(p10Label(isHindi, "स्टाफ का नाम", "Staff Name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roles.forEach { role ->
                        FilterChip(
                            selected = designation.equals(role, ignoreCase = true),
                            onClick = {
                                designation = role
                                isTeaching = role == "Teacher"
                            },
                            label = { Text(role) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = basicSalary,
                        onValueChange = { basicSalary = it.filter { c -> c.isDigit() } },
                        label = { Text(p10Label(isHindi, "बेसिक मासिक वेतन ₹", "Basic Salary ₹")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = joiningDate,
                        onValueChange = { joiningDate = it },
                        label = { Text(p10Label(isHindi, "नियुक्ति तिथि (YYYY-MM-DD)", "Joining Date")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allSchools, onCheckedChange = { allSchools = it })
                    Text(p10Label(isHindi, "सभी स्कूलों में नियुक्त (All Schools)", "Assigned to All Schools"))
                }

                HorizontalDivider()
                Text(p10Label(isHindi, "भत्ते (Allowances ₹)", "Allowances (₹)"), fontWeight = FontWeight.Bold)
                allowances.forEachIndexed { idx, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = item.amountRs.toString(),
                            onValueChange = { v ->
                                allowances[idx] = item.copy(amountRs = v.filter { it.isDigit() }.toIntOrNull() ?: 0)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newAllowanceName,
                        onValueChange = { newAllowanceName = it },
                        label = { Text(p10Label(isHindi, "नया भत्ता जोड़ें", "New Allowance")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (newAllowanceName.isNotBlank()) {
                                allowances.add(SalaryComponentItem(name = newAllowanceName.trim(), amountRs = 0))
                                newAllowanceName = ""
                            }
                        }
                    ) { Text("+ Add") }
                }

                HorizontalDivider()
                Text(p10Label(isHindi, "निश्चित कटौती (Fixed Deductions ₹)", "Fixed Deductions (₹)"), fontWeight = FontWeight.Bold)
                deductions.forEachIndexed { idx, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = item.amountRs.toString(),
                            onValueChange = { v ->
                                deductions[idx] = item.copy(amountRs = v.filter { it.isDigit() }.toIntOrNull() ?: 0)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newDeductionName,
                        onValueChange = { newDeductionName = it },
                        label = { Text(p10Label(isHindi, "नई कटौती जोड़ें", "New Deduction")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (newDeductionName.isNotBlank()) {
                                deductions.add(SalaryComponentItem(name = newDeductionName.trim(), amountRs = 0))
                                newDeductionName = ""
                            }
                        }
                    ) { Text("+ Add") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = existing ?: StaffSalaryProfileRecord(primarySchoolId = schoolDocId)
                    onSave(
                        base.copy(
                            staffName = name.trim(),
                            designation = designation.trim().ifBlank { "Staff" },
                            isTeachingStaff = isTeaching,
                            joiningDateIso = joiningDate.trim(),
                            allSchools = allSchools,
                            primarySchoolId = schoolDocId,
                            basicMonthlySalaryRs = basicSalary.toIntOrNull() ?: 0,
                            allowances = allowances.toList(),
                            fixedDeductions = deductions.toList()
                        )
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "सेव करें", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SalaryRecordAdjustDialog(
    record: SalaryMonthlyRecord,
    isMonthLocked: Boolean,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Int, Int, Int, Int, String, String, (String) -> Unit) -> Unit
) {
    var present by remember { mutableStateOf(record.presentDays.toString()) }
    var absent by remember { mutableStateOf(record.absentDays.toString()) }
    var leave by remember { mutableStateOf(record.onLeaveDays.toString()) }
    var halfDay by remember { mutableStateOf(record.halfDays.toString()) }
    var late by remember { mutableStateOf(record.lateDays.toString()) }
    var advanceDed by remember { mutableStateOf(record.advanceDeductionRs.toString()) }
    var isBonus by remember { mutableStateOf(record.manualAdjustmentRs >= 0) }
    var manualAdjAbs by remember { mutableStateOf(kotlin.math.abs(record.manualAdjustmentRs).toString()) }
    var remark by remember { mutableStateOf(record.adjustmentRemark) }
    var lockReason by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "वेतन संशोधन: ${record.staffName}", "Adjust Salary: ${record.staffName}")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = present,
                        onValueChange = { present = it.filter { c -> c.isDigit() } },
                        label = { Text("Present") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = absent,
                        onValueChange = { absent = it.filter { c -> c.isDigit() } },
                        label = { Text("Absent") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = leave,
                        onValueChange = { leave = it.filter { c -> c.isDigit() } },
                        label = { Text("Leave") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = halfDay,
                        onValueChange = { halfDay = it.filter { c -> c.isDigit() } },
                        label = { Text("Half-Day") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = late,
                        onValueChange = { late = it.filter { c -> c.isDigit() } },
                        label = { Text("Late") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = advanceDed,
                        onValueChange = { advanceDed = it.filter { c -> c.isDigit() } },
                        label = { Text("Adv. Ded ₹") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider()
                Text(p10Label(isHindi, "हस्त समायोजन (Manual Adjustment)", "Hand Adjustment"), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isBonus,
                        onClick = { isBonus = true },
                        label = { Text(p10Label(isHindi, "+ बोनस / जोड़ें", "+ Bonus / Add")) }
                    )
                    FilterChip(
                        selected = !isBonus,
                        onClick = { isBonus = false },
                        label = { Text(p10Label(isHindi, "- अतिरिक्त कटौती", "- Extra Deduct")) }
                    )
                }
                OutlinedTextField(
                    value = manualAdjAbs,
                    onValueChange = { manualAdjAbs = it.filter { c -> c.isDigit() }; errorText = null },
                    label = { Text(p10Label(isHindi, "समायोजन राशि ₹", "Adjustment Amount ₹")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it; errorText = null },
                    label = { Text(p10Label(isHindi, "टिप्पणी / कारण (अनिवार्य यदि राशि > 0)", "Remark (Mandatory when adjusted)")) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isMonthLocked) {
                    OutlinedTextField(
                        value = lockReason,
                        onValueChange = { lockReason = it; errorText = null },
                        label = { Text(p10Label(isHindi, "लॉक माह में बदलाव का कारण (अनिवार्य)", "Reason for changing locked month (Required)")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorText != null) {
                    Text(errorText!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val adjAbs = manualAdjAbs.toIntOrNull() ?: 0
                    val signedAdj = if (isBonus) adjAbs else -adjAbs
                    onSave(
                        present.toIntOrNull() ?: 0,
                        absent.toIntOrNull() ?: 0,
                        leave.toIntOrNull() ?: 0,
                        halfDay.toIntOrNull() ?: 0,
                        late.toIntOrNull() ?: 0,
                        advanceDed.toIntOrNull() ?: 0,
                        signedAdj,
                        remark,
                        lockReason
                    ) { errorText = it }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "सेव करें", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalaryMarkPaidDialog(
    record: SalaryMonthlyRecord,
    isMonthLocked: Boolean,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirmPay: (String, PaymentMode, String, String, (String) -> Unit) -> Unit
) {
    var dateIso by remember { mutableStateOf(record.paidDateIso.ifBlank { Phase6DateUtils.todayIso() }) }
    var mode by remember { mutableStateOf(record.paymentMode) }
    var ref by remember { mutableStateOf(record.paymentReference) }
    var lockReason by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "वेतन भुगतान करें: ₹${record.netPayableRs}", "Pay Salary: ₹${record.netPayableRs}")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${record.staffName} (${record.yearMonth})" +
                        if (record.advanceDeductionRs > 0) " • Advance Instalment ₹${record.advanceDeductionRs} will be deducted automatically" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it; errorText = null },
                    label = { Text(p10Label(isHindi, "भुगतान तिथि (YYYY-MM-DD)", "Paid Date (YYYY-MM-DD)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMode.entries.forEach { pm ->
                        FilterChip(
                            selected = mode == pm,
                            onClick = { mode = pm },
                            label = { Text(pm.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text(p10Label(isHindi, "भुगतान संदर्भ / चेक / UPI नं.", "Reference / UTR / Cheque No.")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isMonthLocked) {
                    OutlinedTextField(
                        value = lockReason,
                        onValueChange = { lockReason = it; errorText = null },
                        label = { Text(p10Label(isHindi, "लॉक माह में बदलाव का कारण (अनिवार्य)", "Reason for locked month change")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = p10Label(
                        isHindi,
                        "नोट: यह भुगतान कैश बुक (Phase 10) में अपने-आप खर्च के रूप में दिखेगा (दोबारा दर्ज करने की आवश्यकता नहीं)।",
                        "Note: This salary payment automatically appears in the Phase 10 Cash Book as an expense without double entry."
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = PassGreen
                )
                if (errorText != null) {
                    Text(errorText!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPay(dateIso, mode, ref, lockReason) { errorText = it } },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(p10Label(isHindi, "भुगतान पूर्ण करें", "Confirm Paid"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueSalaryAdvanceDialog(
    profiles: List<StaffSalaryProfileRecord>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (StaffSalaryProfileRecord, String, Int, Int, PaymentMode, String, (String) -> Unit) -> Unit
) {
    var selectedStaff by remember { mutableStateOf(profiles.firstOrNull()) }
    var dateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }
    var principal by remember { mutableStateOf("") }
    var instalment by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var note by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "स्टाफ अग्रिम / लोन दें", "Give Staff Advance / Loan")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(p10Label(isHindi, "स्टाफ चुनें:", "Select Staff:"), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    profiles.forEach { prof ->
                        FilterChip(
                            selected = selectedStaff?.staffId == prof.staffId,
                            onClick = { selectedStaff = prof },
                            label = { Text(prof.staffName) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = principal,
                        onValueChange = { principal = it.filter { c -> c.isDigit() }; errorText = null },
                        label = { Text(p10Label(isHindi, "अग्रिम राशि ₹", "Advance Amount ₹")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = instalment,
                        onValueChange = { instalment = it.filter { c -> c.isDigit() } },
                        label = { Text(p10Label(isHindi, "मासिक किस्त ₹", "Monthly Ded. ₹")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMode.entries.forEach { pm ->
                        FilterChip(selected = mode == pm, onClick = { mode = pm }, label = { Text(pm.label) })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(p10Label(isHindi, "विवरण / कारण", "Note / Reason")) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(errorText!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val staff = selectedStaff
                    if (staff == null) {
                        errorText = "Select a staff member"
                        return@Button
                    }
                    onSave(
                        staff,
                        dateIso,
                        principal.toIntOrNull() ?: 0,
                        instalment.toIntOrNull() ?: 0,
                        mode,
                        note
                    ) { errorText = it }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "सेव करें", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualAdvanceRepaymentDialog(
    advance: StaffSalaryAdvanceRecord,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, String, PaymentMode, String, (String) -> Unit) -> Unit
) {
    var amount by remember { mutableStateOf(advance.suggestedCurrentMonthDeductionRs.toString()) }
    var dateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var note by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "अग्रिम वापसी: ${advance.staffName}", "Advance Repayment: ${advance.staffName}")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Remaining Balance: ₹${advance.remainingBalanceRs}", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() }; errorText = null },
                    label = { Text(p10Label(isHindi, "वापसी राशि ₹", "Repayment Amount ₹")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMode.entries.forEach { pm ->
                        FilterChip(selected = mode == pm, onClick = { mode = pm }, label = { Text(pm.label) })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(p10Label(isHindi, "टिप्पणी", "Note")) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(errorText!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(amount.toIntOrNull() ?: 0, dateIso, mode, note) { errorText = it }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "सेव करें", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@Composable
private fun SalaryMonthLockDialog(
    yearMonth: String,
    currentlyLocked: Boolean,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, (String) -> Unit) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (currentlyLocked) p10Label(isHindi, "वेतन माह अनलॉक करें ($yearMonth)", "Unlock Salary Month ($yearMonth)")
                else p10Label(isHindi, "वेतन माह लॉक करें ($yearMonth)", "Lock Finished Salary Month ($yearMonth)")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (currentlyLocked) {
                        p10Label(
                            isHindi,
                            "लॉक किए गए माह को अनलॉक करने के लिए कारण दर्ज करना अनिवार्य है। यह ऑडिट लॉग में दर्ज होगा।",
                            "Please enter a mandatory reason to unlock this finished month. This will be recorded in the salary audit log."
                        )
                    } else {
                        p10Label(
                            isHindi,
                            "माह लॉक करने के बाद किसी भी संशोधन के लिए कारण देना अनिवार्य होगा और हर बदलाव ऑडिट लॉग में दर्ज होगा।",
                            "Once locked by the Owner, any future changes to $yearMonth will require a mandatory reason and will be logged."
                        )
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; errorText = null },
                    label = {
                        Text(
                            if (currentlyLocked) p10Label(isHindi, "अनलॉक का कारण (अनिवार्य)", "Unlock Reason (Required)")
                            else p10Label(isHindi, "टिप्पणी (वैकल्पिक)", "Note (Optional)")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(errorText!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(note) { errorText = it } },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (currentlyLocked) "Unlock" else "Lock Month")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@Composable
private fun PostLockReasonPromptDialog(
    yearMonth: String,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "लॉक माह ($yearMonth) में बदलाव का कारण", "Reason for Locked Month ($yearMonth) Change")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    p10Label(
                        isHindi,
                        "यह माह लॉक है। पुनः गणना या बदलाव करने के लिए कारण लिखें (ऑडिट लॉग में दर्ज होगा)।",
                        "This month is locked. Please enter a reason to recompute and log this change."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; errorText = null },
                    label = { Text(p10Label(isHindi, "कारण (अनिवार्य)", "Reason (Mandatory)")) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(errorText!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.trim().isEmpty()) {
                        errorText = "Reason is required for locked month changes."
                    } else {
                        onConfirm(reason.trim())
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "पुष्टि करें", "Confirm & Log"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

private fun stepYearMonth(yearMonth: String, deltaMonths: Int): String {
    val parts = yearMonth.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val totalMonths = y * 12 + (m - 1) + deltaMonths
    val newY = totalMonths / 12
    val newM = (totalMonths % 12) + 1
    return String.format(Locale.US, "%04d-%02d", newY, newM)
}
