package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.FeeHeadType
import com.example.domain.FeeMessageTemplates
import com.example.domain.FeeMessageType
import com.example.domain.FeeReportsAndQueueEngine
import com.example.domain.PaymentMode
import com.example.domain.Phase6DateUtils
import com.example.domain.Phase9AccessEvaluator
import com.example.domain.PhoneNormalizer
import com.example.domain.PhoneValidationResult
import com.example.domain.SendQueueGuardianRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FeeReportSubView(val label: String) {
    DAY_COLLECTION("Day Collection"),
    DUES_LIST("Dues List"),
    CONCESSIONS("Concessions"),
    OPTIONAL_SALES("Optional Sales"),
    AUDIT_LOG("Audit Log")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Phase9FeeReportsTab(
    viewModel: SchoolViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val schoolDocId = uiState.activeSchoolDocId
    val session = uiState.filter.session
    val structure = remember(uiState.feeStructures, schoolDocId, session, uiState.activeSchoolClassNames) {
        uiState.feeStructureForSchool(schoolDocId, session)
    }

    var activeSubView by remember { mutableStateOf(FeeReportSubView.DAY_COLLECTION) }

    // Day Collection state
    var startDateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }
    var endDateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }

    // Dues List filter state
    var duesClassFilter by remember { mutableStateOf<String?>(null) }
    var duesSectionFilter by remember { mutableStateOf<String?>(null) }
    var duesMonthFilter by remember { mutableStateOf<String?>(null) }
    var duesHeadFilter by remember { mutableStateOf<FeeHeadType?>(null) }

    val exportDuesCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportDuesListCsvToUri(
                destinationUri = uri,
                schoolDocId = schoolDocId,
                classNameFilter = duesClassFilter,
                sectionFilter = duesSectionFilter,
                monthCodeFilter = duesMonthFilter,
                headTypeFilter = duesHeadFilter
            )
        }
    }

    val activeStudentsWithDocIds = remember(uiState.allStudents, uiState.studentDocIdByLongId, uiState.activeSchool.id, session) {
        uiState.allStudents
            .filter {
                it.schoolId == uiState.activeSchool.id &&
                    it.session.equals(session, ignoreCase = true) &&
                    it.isActiveStatus
            }
            .mapNotNull { st ->
                val docId = uiState.studentDocIdFor(st.id)
                if (docId.isBlank()) null else docId to st
            }
    }
    val overridesByStudentDocId = remember(uiState.studentFeeOverrides, schoolDocId, session) {
        uiState.studentFeeOverrides
            .filter { it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true) }
            .associateBy { it.studentId }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("phase9_fee_reports_tab"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sub-view switcher chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeeReportSubView.entries.forEach { sub ->
                    FilterChip(
                        selected = activeSubView == sub,
                        onClick = { activeSubView = sub },
                        label = { Text(sub.label) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("fee_report_sub_${sub.name.lowercase()}")
                    )
                }
            }
        }

        when (activeSubView) {
            FeeReportSubView.DAY_COLLECTION -> {
                val summary = FeeReportsAndQueueEngine.buildDayCollectionRegister(
                    receipts = uiState.feeReceipts,
                    schoolId = schoolDocId,
                    session = session,
                    startDateIso = startDateIso,
                    endDateIso = endDateIso
                )
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Day Collection Register",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Button(
                                    onClick = {
                                        viewModel.generateDayCollectionRegisterPdfPreview(
                                            schoolDocId = schoolDocId,
                                            startDateIso = startDateIso,
                                            endDateIso = endDateIso
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("fee_report_day_collection_pdf_button")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("A4 PDF Report", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = startDateIso,
                                    onValueChange = { startDateIso = it },
                                    label = { Text("From Date (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = endDateIso,
                                    onValueChange = { endDateIso = it },
                                    label = { Text("To Date (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // KPI Totals Box
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Total Collected: Rs. ${summary.totalNetCollected} (${summary.activeReceipts.size} Issued Receipts)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = PaymentMode.entries.joinToString(" • ") { mode ->
                                            "${mode.label}: Rs. ${summary.totalByMode[mode] ?: 0}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Total Concession Given: Rs. ${summary.totalConcessionGiven} • Cancelled Receipts: ${summary.cancelledReceipts.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text("Issued Receipts (${summary.activeReceipts.size}):", fontWeight = FontWeight.Bold)
                            if (summary.activeReceipts.isEmpty()) {
                                Text(
                                    "No issued receipts in this date range.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                summary.activeReceipts.forEach { r ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${r.receiptNumber} • ${r.studentName} (${r.className}-${r.section})",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                "${r.receiptDateIso} • ${r.paymentMode.label} • Conc: Rs.${r.concessionAmount} • by ${r.receivedByName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "Rs. ${r.amountPaid}",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1B5E20)
                                            )
                                            IconButton(onClick = { viewModel.generateFeeReceiptPdfPreview(r) }) {
                                                Icon(Icons.Default.PictureAsPdf, contentDescription = "A5 Receipt PDF")
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }

                            if (summary.cancelledReceipts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Cancelled Receipts Shown Separately (${summary.cancelledReceipts.size}):",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB71C1C)
                                )
                                summary.cancelledReceipts.forEach { r ->
                                    Text(
                                        text = "${r.receiptNumber} • ${r.studentName} (${r.className}-${r.section}) • Rs.${r.amountPaid} • Reason: ${r.cancelReason} (by ${r.cancelledByName})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB71C1C)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FeeReportSubView.DUES_LIST -> {
                val duesRows = FeeReportsAndQueueEngine.buildDuesList(
                    studentsWithDocIds = activeStudentsWithDocIds,
                    schoolDocId = schoolDocId,
                    schoolName = uiState.activeSchool.name,
                    structure = structure,
                    overridesByStudentDocId = overridesByStudentDocId,
                    receipts = uiState.feeReceipts,
                    classNameFilter = duesClassFilter,
                    sectionFilter = duesSectionFilter,
                    monthFilter = duesMonthFilter,
                    feeHeadFilter = duesHeadFilter
                )
                val grandTotalDue = duesRows.sumOf { it.totalDueAmount }

                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Dues List Report (${duesRows.size} Students)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Total Due: Rs. $grandTotalDue",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB71C1C),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            exportDuesCsvLauncher.launch("fee_dues_${session}.csv")
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("fee_report_dues_csv_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CSV", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.generateDuesListPdfPreview(
                                                schoolDocId = schoolDocId,
                                                classNameFilter = duesClassFilter,
                                                sectionFilter = duesSectionFilter,
                                                monthCodeFilter = duesMonthFilter,
                                                headTypeFilter = duesHeadFilter
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("fee_report_dues_pdf_button")
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("A4 PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }

                            // Class Filter
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = duesClassFilter == null,
                                    onClick = { duesClassFilter = null },
                                    label = { Text("All Classes") }
                                )
                                uiState.activeSchoolClassNames.forEach { cls ->
                                    FilterChip(
                                        selected = duesClassFilter.equals(cls, ignoreCase = true),
                                        onClick = {
                                            duesClassFilter = if (duesClassFilter.equals(cls, ignoreCase = true)) null else cls
                                        },
                                        label = { Text(cls) }
                                    )
                                }
                            }

                            // Month Filter
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = duesMonthFilter == null,
                                    onClick = { duesMonthFilter = null },
                                    label = { Text("All Months") }
                                )
                                structure.monthOrder.forEach { mCode ->
                                    FilterChip(
                                        selected = duesMonthFilter.equals(mCode, ignoreCase = true),
                                        onClick = {
                                            duesMonthFilter = if (duesMonthFilter.equals(mCode, ignoreCase = true)) null else mCode
                                        },
                                        label = { Text(mCode) }
                                    )
                                }
                            }

                            // Fee Head Filter
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = duesHeadFilter == null,
                                    onClick = { duesHeadFilter = null },
                                    label = { Text("All Fee Heads") }
                                )
                                FeeHeadType.entries.forEach { ht ->
                                    FilterChip(
                                        selected = duesHeadFilter == ht,
                                        onClick = { duesHeadFilter = if (duesHeadFilter == ht) null else ht },
                                        label = { Text(ht.shortLabel) }
                                    )
                                }
                            }

                            HorizontalDivider()

                            duesRows.forEach { row ->
                                val prevSessionDue = row.dueByHead[FeeHeadType.PREVIOUS_SESSION_DUE] ?: 0
                                val tuitionDue = row.dueByHead[FeeHeadType.TUITION] ?: 0
                                val transportDue = row.dueByHead[FeeHeadType.TRANSPORT] ?: 0
                                val examDue = FeeHeadType.examHeads.sumOf { row.dueByHead[it] ?: 0 }
                                val dueMonthsLabel = row.dueMonths.joinToString(", ").ifBlank { "Current Session" }
                                val msgText = FeeReportsAndQueueEngine.fillMessageTemplate(
                                    template = structure.messageTemplates.feeDueTemplate,
                                    guardianName = row.student.fatherName.ifBlank { row.student.motherName },
                                    studentName = row.student.name,
                                    classSection = "${row.student.className}-${row.student.section}",
                                    amountRs = row.totalDueAmount,
                                    monthsText = dueMonthsLabel,
                                    schoolName = uiState.activeSchool.name
                                )

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${row.student.name} (${row.student.className}-${row.student.section}, Adm ${row.student.admNo})",
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Due: Rs. ${row.totalDueAmount}",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB71C1C)
                                            )
                                        }
                                        val breakdownLine = buildString {
                                            if (prevSessionDue > 0) append("Prev. Session: Rs.$prevSessionDue • ")
                                            append("Tuition: Rs.$tuitionDue • Transport: Rs.$transportDue • Exam: Rs.$examDue")
                                        }
                                        Text(
                                            breakdownLine,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Months Due: $dueMonthsLabel • Guardian Mob: ${row.student.mobile.ifBlank { "—" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            AssistChip(
                                                onClick = { viewModel.generateStudentDuesSlipPdfPreview(row.student) },
                                                label = { Text("A5 Dues Slip") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            )
                                            AssistChip(
                                                onClick = {
                                                    copyTextToClipboard(context, "Fee Dues Reminder", msgText)
                                                    viewModel.emitMessage("Copied dues message for ${row.student.name}")
                                                },
                                                label = { Text("Copy Message") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            )
                                            AssistChip(
                                                onClick = {
                                                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                                        data = Uri.parse("smsto:${Uri.encode(row.student.mobile)}")
                                                        putExtra("sms_body", msgText)
                                                    }
                                                    runCatching { context.startActivity(smsIntent) }
                                                },
                                                label = { Text("SMS") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FeeReportSubView.CONCESSIONS -> {
                val concRows = FeeReportsAndQueueEngine.buildConcessionReport(
                    receipts = uiState.feeReceipts,
                    schoolId = schoolDocId,
                    session = session
                )
                val totalConc = concRows.sumOf { it.concessionAmount }

                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Concession Report (${concRows.size} Receipts)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total Concession Granted: Rs. $totalConc",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                                Button(
                                    onClick = { viewModel.generateConcessionReportPdfPreview(schoolDocId) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("fee_report_concession_pdf_button")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("A4 PDF")
                                }
                            }

                            concRows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${row.studentName} (${row.className}-${row.section}) • ${row.receiptNumber}",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "Remark: ${row.remark} • Given by: ${row.givenByName}" +
                                                if (row.approvedByAdminName.isNotBlank()) " • Approved by: ${row.approvedByAdminName}" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "Rs. ${row.concessionAmount}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20)
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            FeeReportSubView.OPTIONAL_SALES -> {
                val salesRows = FeeReportsAndQueueEngine.buildOptionalItemSalesReport(
                    receipts = uiState.feeReceipts,
                    schoolId = schoolDocId,
                    session = session
                )
                val totalRevenue = salesRows.sumOf { it.totalNetCollected }

                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Optional-Item Sales Report",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total Sales Revenue: Rs. $totalRevenue",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Button(
                                    onClick = { viewModel.generateOptionalSalesReportPdfPreview(schoolDocId) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("fee_report_optional_sales_pdf_button")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("A4 PDF")
                                }
                            }

                            salesRows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(row.itemName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Quantity Sold: ${row.totalQuantitySold} • Receipts: ${row.receiptCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "Rs. ${row.totalNetCollected}",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            FeeReportSubView.AUDIT_LOG -> {
                val logs = uiState.feeAuditLogs.filter { it.schoolId == schoolDocId }
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Fee Audit Log (${logs.size} Entries)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val fmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
                            logs.forEach { log ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${log.action} • ${log.summary}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val actorLabel = com.example.domain.AuthorDisplayHelper.resolveAuthorName(
                                        authorUid = log.actorUid,
                                        storedName = log.actorName,
                                        deletedUserUids = uiState.deletedUserUids
                                    )
                                    Text(
                                        text = "By $actorLabel at ${fmt.format(Date(log.clientCreatedAtMs))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// PART D2: SEND QUEUE & NOTICES TAB (WhatsApp from user's phone via wa.me)
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Phase9SendQueueAndNoticesTab(
    viewModel: SchoolViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val schoolDocId = uiState.activeSchoolDocId
    val session = uiState.filter.session
    val canEditFees = Phase9AccessEvaluator.canEditFees(uiState.accessProfile, schoolDocId) || uiState.canEditFees

    val structure = remember(uiState.feeStructures, schoolDocId, session, uiState.activeSchoolClassNames) {
        uiState.feeStructureForSchool(schoolDocId, session)
    }

    var showNoticesMode by remember { mutableStateOf(false) }

    // Send Queue Filters
    var filterClass by remember { mutableStateOf<String?>(null) }
    var filterMonth by remember { mutableStateOf<String?>(null) }
    var minDueAmountText by remember { mutableStateOf("1") }
    var showPendingOnly by remember { mutableStateOf(false) }
    var showEditTemplatesDialog by remember { mutableStateOf(false) }

    // Post-WhatsApp return prompt ("Sent?")
    var pendingReturnSentRow by remember { mutableStateOf<SendQueueGuardianRow?>(null) }

    // Notice Maker state
    var noticeTitle by remember { mutableStateOf("") }
    var noticeDateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }
    var noticeBody by remember { mutableStateOf("") }

    val activeStudentsWithDocIds = remember(uiState.allStudents, uiState.studentDocIdByLongId, uiState.activeSchool.id, session) {
        uiState.allStudents
            .filter {
                it.schoolId == uiState.activeSchool.id &&
                    it.session.equals(session, ignoreCase = true) &&
                    it.isActiveStatus
            }
            .mapNotNull { st ->
                val docId = uiState.studentDocIdFor(st.id)
                if (docId.isBlank()) null else docId to st
            }
    }
    val overridesByStudentDocId = remember(uiState.studentFeeOverrides, schoolDocId, session) {
        uiState.studentFeeOverrides
            .filter { it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true) }
            .associateBy { it.studentId }
    }

    val allQueueRowsUnfilteredBySent = remember(
        activeStudentsWithDocIds,
        overridesByStudentDocId,
        uiState.feeReceipts,
        structure,
        uiState.activeSchool.name,
        uiState.feeMessageSentLogs,
        filterClass,
        filterMonth,
        minDueAmountText
    ) {
        val minDue = (minDueAmountText.toIntOrNull() ?: 1).coerceAtLeast(1)
        val duesRows = FeeReportsAndQueueEngine.buildDuesList(
            studentsWithDocIds = activeStudentsWithDocIds,
            schoolDocId = schoolDocId,
            schoolName = uiState.activeSchool.name,
            structure = structure,
            overridesByStudentDocId = overridesByStudentDocId,
            receipts = uiState.feeReceipts,
            classNameFilter = filterClass,
            monthFilter = filterMonth,
            minimumDueAmount = minDue
        )
        val ctxKey = filterMonth ?: Phase6DateUtils.todayIso().take(7)
        FeeReportsAndQueueEngine.buildSendQueue(
            duesRows = duesRows,
            schoolName = uiState.activeSchool.name,
            templates = structure.messageTemplates,
            messageType = FeeMessageType.FEE_DUE_REMINDER,
            contextDateOrMonthKey = ctxKey,
            sentLogs = uiState.feeMessageSentLogs.filter {
                it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true)
            },
            showPendingOnly = false
        )
    }

    val displayedQueueRows = remember(allQueueRowsUnfilteredBySent, showPendingOnly) {
        if (showPendingOnly) allQueueRowsUnfilteredBySent.filter { !it.isSent } else allQueueRowsUnfilteredBySent
    }
    val totalMatchingCount = allQueueRowsUnfilteredBySent.size
    val sentCount = allQueueRowsUnfilteredBySent.count { it.isSent }
    val pendingCount = totalMatchingCount - sentCount
    val invalidPhoneCount = allQueueRowsUnfilteredBySent.count { it.phoneValidation is PhoneValidationResult.Invalid }

    val schoolNotices = remember(uiState.schoolNotices, schoolDocId) {
        uiState.schoolNotices.filter { it.schoolId == schoolDocId }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("phase9_send_queue_and_notices_tab"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showNoticesMode,
                    onClick = { showNoticesMode = false },
                    label = { Text("WhatsApp Send Queue ($pendingCount Pending / $sentCount Sent)") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("phase9_mode_send_queue")
                )
                FilterChip(
                    selected = showNoticesMode,
                    onClick = { showNoticesMode = true },
                    label = { Text("Notice Maker (${schoolNotices.size})") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("phase9_mode_notice_maker")
                )
            }
        }

        if (!showNoticesMode) {
            // Send Queue Header & Filters
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Guardian WhatsApp Send Queue",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Opens WhatsApp with the guardian's +91 number and prefilled message. Never sends automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canEditFees) {
                                OutlinedButton(
                                    onClick = { showEditTemplatesDialog = true },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("send_queue_edit_templates_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Templates")
                                }
                            }
                        }

                        // Sent / Pending / Invalid Phone KPI Banner
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total: $totalMatchingCount • Sent: $sentCount • Pending: $pendingCount" +
                                        if (invalidPhoneCount > 0) " • Invalid Mob: $invalidPhoneCount" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Pending Only", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = showPendingOnly,
                                        onCheckedChange = { showPendingOnly = it },
                                        modifier = Modifier.testTag("send_queue_pending_only_switch")
                                    )
                                }
                            }
                        }

                        // Filter by Class
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = filterClass == null,
                                onClick = { filterClass = null },
                                label = { Text("All Classes") }
                            )
                            uiState.activeSchoolClassNames.forEach { cls ->
                                FilterChip(
                                    selected = filterClass.equals(cls, ignoreCase = true),
                                    onClick = {
                                        filterClass = if (filterClass.equals(cls, ignoreCase = true)) null else cls
                                    },
                                    label = { Text(cls) }
                                )
                            }
                        }

                        // Filter by Month & Minimum Due Amount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1.4f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = filterMonth == null,
                                    onClick = { filterMonth = null },
                                    label = { Text("All Months") }
                                )
                                structure.monthOrder.forEach { mCode ->
                                    FilterChip(
                                        selected = filterMonth.equals(mCode, ignoreCase = true),
                                        onClick = {
                                            filterMonth = if (filterMonth.equals(mCode, ignoreCase = true)) null else mCode
                                        },
                                        label = { Text(mCode) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = minDueAmountText,
                                onValueChange = { minDueAmountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("Min Due (Rs.)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.8f)
                                    .testTag("send_queue_min_due_input")
                            )
                        }
                    }
                }
            }

            // Send Queue Guardian Rows
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    displayedQueueRows.forEach { row ->
                        val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }
                        val validPhone = row.phoneValidation as? PhoneValidationResult.Valid
                        val invalidPhone = row.phoneValidation as? PhoneValidationResult.Invalid
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_queue_row_${row.student.admNo}")
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
                                            text = "${row.student.name} (${row.student.className}-${row.student.section})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Guardian: ${row.guardianName} • Due: Rs. ${row.dueAmount} (${row.dueMonthsSummary})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB71C1C)
                                        )
                                    }
                                    if (row.isSent && row.sentLog != null) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFE8F5E9)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Sent",
                                                    tint = Color(0xFF1B5E20),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Sent ${fmt.format(Date(row.sentLog.sentAtMs))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF1B5E20),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Phone normalisation status
                                if (validPhone != null) {
                                    Text(
                                        text = "Mobile: ${validPhone.e164WithPlus}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = invalidPhone?.reason ?: "Invalid mobile number",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Text(
                                    text = row.messageText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AssistChip(
                                            onClick = {
                                                copyTextToClipboard(context, "Fee Reminder", row.messageText)
                                                viewModel.emitMessage("Copied message for ${row.student.name}")
                                            },
                                            label = { Text("Copy") },
                                            leadingIcon = {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        )
                                        AssistChip(
                                            onClick = {
                                                val targetMob = validPhone?.e164WithPlus ?: row.student.mobile
                                                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                                    data = Uri.parse("smsto:${Uri.encode(targetMob)}")
                                                    putExtra("sms_body", row.messageText)
                                                }
                                                runCatching { context.startActivity(smsIntent) }
                                            },
                                            label = { Text("SMS") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        )
                                    }

                                    Button(
                                        enabled = validPhone != null && !row.waMeUrl.isNullOrBlank(),
                                        onClick = {
                                            val url = row.waMeUrl ?: return@Button
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                            pendingReturnSentRow = row
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("send_queue_whatsapp_button_${row.student.admNo}")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (row.isSent) "Resend on WhatsApp" else "Send on WhatsApp")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Notice Maker Screen
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "School Notice Maker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Write a school notice to copy or share with class groups on WhatsApp, and keep a history of past notices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = noticeTitle,
                                onValueChange = { noticeTitle = it },
                                label = { Text("Notice Title *") },
                                placeholder = { Text("e.g., Holiday Notice / PTM Schedule") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("notice_maker_title_input")
                            )
                            OutlinedTextField(
                                value = noticeDateIso,
                                onValueChange = { noticeDateIso = it },
                                label = { Text("Date (optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        OutlinedTextField(
                            value = noticeBody,
                            onValueChange = { noticeBody = it },
                            label = { Text("Notice Text *") },
                            minLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notice_maker_body_input")
                        )

                        val formattedNoticeText = remember(uiState.activeSchool.name, noticeTitle, noticeDateIso, noticeBody) {
                            buildString {
                                appendLine("*${uiState.activeSchool.name.uppercase()}*")
                                if (noticeTitle.isNotBlank()) appendLine("*Notice: ${noticeTitle.trim()}*")
                                if (noticeDateIso.isNotBlank()) appendLine("Date: ${noticeDateIso.trim()}")
                                appendLine()
                                append(noticeBody.trim())
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                enabled = noticeBody.isNotBlank(),
                                onClick = {
                                    copyTextToClipboard(context, noticeTitle.ifBlank { "School Notice" }, formattedNoticeText)
                                    viewModel.emitMessage("Notice copied to clipboard")
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("notice_maker_copy_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Notice")
                            }

                            OutlinedButton(
                                enabled = noticeBody.isNotBlank(),
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, noticeTitle.ifBlank { "School Notice" })
                                        putExtra(Intent.EXTRA_TEXT, formattedNoticeText)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Notice"))
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("notice_maker_share_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Notice")
                            }

                            if (canEditFees) {
                                Button(
                                    enabled = noticeTitle.isNotBlank() && noticeBody.isNotBlank(),
                                    onClick = {
                                        viewModel.saveSchoolNotice(
                                            schoolDocId = schoolDocId,
                                            title = noticeTitle,
                                            bodyText = noticeBody,
                                            noticeDateIso = noticeDateIso
                                        ) { ok, _ ->
                                            if (ok) {
                                                noticeTitle = ""
                                                noticeBody = ""
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("notice_maker_save_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save to History")
                                }
                            }
                        }
                    }
                }
            }

            // Saved Notices History
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Past Notices History (${schoolNotices.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (schoolNotices.isEmpty()) {
                            Text(
                                "No saved notices yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            schoolNotices.forEach { notice ->
                                val fullText = "*${uiState.activeSchool.name.uppercase()}*\n*Notice: ${notice.title}*\nDate: ${notice.noticeDateIso}\n\n${notice.bodyText}"
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${notice.title} (${notice.noticeDateIso})",
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (canEditFees && uiState.isAdmin) {
                                                IconButton(onClick = { viewModel.deleteSchoolNotice(notice.id) }) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete notice",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                        Text(notice.bodyText, style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AssistChip(
                                                onClick = {
                                                    copyTextToClipboard(context, notice.title, fullText)
                                                    viewModel.emitMessage("Copied '${notice.title}'")
                                                },
                                                label = { Text("Copy") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            )
                                            AssistChip(
                                                onClick = {
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_SUBJECT, notice.title)
                                                        putExtra(Intent.EXTRA_TEXT, fullText)
                                                    }
                                                    runCatching {
                                                        context.startActivity(Intent.createChooser(sendIntent, "Share Notice"))
                                                    }
                                                },
                                                label = { Text("Share") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            )
                                            AssistChip(
                                                onClick = {
                                                    noticeTitle = notice.title
                                                    noticeDateIso = notice.noticeDateIso
                                                    noticeBody = notice.bodyText
                                                },
                                                label = { Text("Load in Editor") }
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
    }

    // Post-return "Sent?" confirmation dialog for Send Queue row
    if (pendingReturnSentRow != null) {
        val row = pendingReturnSentRow!!
        val e164 = (row.phoneValidation as? PhoneValidationResult.Valid)?.e164WithPlus ?: row.rawMobile
        AlertDialog(
            onDismissRequest = { pendingReturnSentRow = null },
            title = { Text("Sent?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Did you send the WhatsApp fee reminder to ${row.guardianName} ($e164) for ${row.student.name}? Mark as Sent to record the date and time."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.markFeeQueueMessageSent(
                            schoolDocId = schoolDocId,
                            studentDocId = row.studentDocId,
                            studentName = row.student.name,
                            className = row.student.className,
                            section = row.student.section,
                            guardianName = row.guardianName,
                            guardianMobileE164 = e164,
                            messageType = FeeMessageType.FEE_DUE_REMINDER,
                            contextKey = row.contextKey,
                            messageText = row.messageText
                        )
                        pendingReturnSentRow = null
                    },
                    modifier = Modifier.testTag("send_queue_confirm_sent_button")
                ) {
                    Text("Yes, Mark Sent")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReturnSentRow = null }) {
                    Text("Not Yet")
                }
            }
        )
    }

    // Edit Message Templates Dialog
    if (showEditTemplatesDialog) {
        var dueTpl by remember { mutableStateOf(structure.messageTemplates.feeDueTemplate) }
        var rcpTpl by remember { mutableStateOf(structure.messageTemplates.feeReceiptTemplate) }
        var genTpl by remember { mutableStateOf(structure.messageTemplates.generalTemplate) }

        AlertDialog(
            onDismissRequest = { showEditTemplatesDialog = false },
            title = { Text("Edit School Message Templates", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Placeholders: {guardian}, {student}, {class}, {amount}, {months}, {school}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = dueTpl,
                        onValueChange = { dueTpl = it },
                        label = { Text("Fee Due Reminder Template") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rcpTpl,
                        onValueChange = { rcpTpl = it },
                        label = { Text("Fee Receipt Text Template") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = genTpl,
                        onValueChange = { genTpl = it },
                        label = { Text("General Message Template") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveFeeMessageTemplates(
                            schoolDocId = schoolDocId,
                            session = session,
                            templates = FeeMessageTemplates(
                                feeDueTemplate = dueTpl.trim(),
                                feeReceiptTemplate = rcpTpl.trim(),
                                generalTemplate = genTpl.trim()
                            )
                        ) { ok, _ ->
                            if (ok) showEditTemplatesDialog = false
                        }
                    }
                ) {
                    Text("Save Templates")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTemplatesDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
