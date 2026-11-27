package com.example.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.StudentEntity
import com.example.domain.CashBookEntryRecord
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeStructureConfig
import com.example.domain.Phase6DateUtils
import com.example.domain.StudentFeeOverride
import com.example.domain.TransportEngine
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportVehicleDocumentRecord
import com.example.domain.VehicleDocExpiryStatus
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class VehiclesTabSection(val labelEn: String, val labelHi: String) {
    ALL("All", "सभी"),
    DOCS("Vehicle Docs", "दस्तावेज़"),
    EXPENSES("Expenses", "खर्च"),
    SUMMARY("Monthly P&L", "मासिक लाभ/हानि");

    fun label(isHindi: Boolean): String = if (isHindi) labelHi else labelEn
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransportVehiclesTab(
    uiState: SchoolUiState,
    isHindi: Boolean,
    allRoutesForSchool: List<TransportRouteRecord>,
    studentsWithDocIds: List<Pair<String, StudentEntity>>,
    structure: FeeStructureConfig,
    overridesByStudentDocId: Map<String, StudentFeeOverride>,
    receipts: List<FeeReceiptRecord>,
    viewModel: SchoolViewModel,
    onPreviewPhotoBase64: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val schoolDocId = uiState.activeSchoolDocId
    val todayIso = remember { Phase6DateUtils.todayIso() }
    val canEditTransport = uiState.canEditTransport
    val canLogOrModifyExpense = canEditTransport || uiState.canEditCashBook
    val transportSettings = remember(uiState.transportSettingsList, schoolDocId) {
        uiState.transportSettingsForSchool(schoolDocId)
    }
    val cashBookSettings = remember(uiState.cashBookSettingsList, schoolDocId) {
        uiState.cashBookSettingsForSchool(schoolDocId)
    }

    var activeSection by remember { mutableStateOf(VehiclesTabSection.ALL) }
    var docFilter by remember { mutableStateOf<VehicleDocExpiryStatus?>(null) }

    var showDocDialog by remember { mutableStateOf(false) }
    var editingDoc by remember { mutableStateOf<TransportVehicleDocumentRecord?>(null) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<CashBookEntryRecord?>(null) }
    var cancelingExpenseTarget by remember { mutableStateOf<CashBookEntryRecord?>(null) }
    var showCategoriesDialog by remember { mutableStateOf(false) }

    val defaultMonth = remember(structure.monthOrder) {
        val cur = currentSessionMonthCode()
        if (structure.monthOrder.any { it.equals(cur, ignoreCase = true) }) cur
        else structure.monthOrder.firstOrNull() ?: "APR"
    }
    var summaryMonthCode by remember(defaultMonth) { mutableStateOf(defaultMonth) }

    val schoolDocs = remember(uiState.transportVehicleDocuments, schoolDocId, todayIso) {
        uiState.transportVehicleDocuments
            .filter { it.schoolId == schoolDocId }
            .sortedWith(compareBy<TransportVehicleDocumentRecord> {
                when (it.statusForDate(todayIso)) {
                    VehicleDocExpiryStatus.EXPIRED -> 0
                    VehicleDocExpiryStatus.EXPIRING_SOON -> 1
                    VehicleDocExpiryStatus.VALID -> 2
                }
            }.thenBy { it.expiryDateIso })
    }
    val filteredDocs = remember(schoolDocs, docFilter, todayIso) {
        if (docFilter == null) schoolDocs else schoolDocs.filter { it.statusForDate(todayIso) == docFilter }
    }

    val schoolTransportExpenses = remember(uiState.cashBookEntries, schoolDocId) {
        uiState.cashBookEntries
            .filter { it.schoolId == schoolDocId && it.isTransportExpense }
            .sortedWith(compareByDescending<CashBookEntryRecord> { it.dateIso }.thenByDescending { it.createdAtMs })
    }

    val yearMonthIso = remember(uiState.filter.session, summaryMonthCode) {
        TransportEngine.yearMonthForSessionMonth(uiState.filter.session, summaryMonthCode)
    }

    val monthlyReport = remember(
        schoolDocId,
        uiState.activeSchool.name,
        uiState.filter.session,
        summaryMonthCode,
        yearMonthIso,
        allRoutesForSchool,
        studentsWithDocIds,
        structure,
        overridesByStudentDocId,
        receipts,
        uiState.cashBookEntries
    ) {
        TransportEngine.computeMonthlyProfitLossReport(
            schoolId = schoolDocId,
            schoolName = uiState.activeSchool.name,
            session = uiState.filter.session,
            monthCode = summaryMonthCode,
            yearMonthIso = yearMonthIso,
            routes = allRoutesForSchool,
            studentsWithDocIds = studentsWithDocIds,
            structure = structure,
            overridesByStudentDocId = overridesByStudentDocId,
            receipts = receipts,
            cashBookEntries = uiState.cashBookEntries
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("transport_vehicles_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VehiclesTabSection.entries.forEach { sec ->
                    FilterChip(
                        selected = activeSection == sec,
                        onClick = { activeSection = sec },
                        label = { Text(sec.label(isHindi)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }

        // SECTION 1: VEHICLE DOCUMENTS & COMPLIANCE
        if (activeSection == VehiclesTabSection.ALL || activeSection == VehiclesTabSection.DOCS) {
            item {
                val expiredCnt = schoolDocs.count { it.statusForDate(todayIso) == VehicleDocExpiryStatus.EXPIRED }
                val expiringCnt = schoolDocs.count { it.statusForDate(todayIso) == VehicleDocExpiryStatus.EXPIRING_SOON }
                val validCnt = schoolDocs.count { it.statusForDate(todayIso) == VehicleDocExpiryStatus.VALID }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = p10Label(isHindi, "वाहन दस्तावेज़ व वैधता (Documents)", "Vehicle Documents & Compliance"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = docFilter == null,
                                onClick = { docFilter = null },
                                label = { Text("${p10Label(isHindi, "सभी", "All")} (${schoolDocs.size})") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                            FilterChip(
                                selected = docFilter == VehicleDocExpiryStatus.EXPIRED,
                                onClick = { docFilter = if (docFilter == VehicleDocExpiryStatus.EXPIRED) null else VehicleDocExpiryStatus.EXPIRED },
                                label = { Text("${VehicleDocExpiryStatus.EXPIRED.label(isHindi)} ($expiredCnt)") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                            FilterChip(
                                selected = docFilter == VehicleDocExpiryStatus.EXPIRING_SOON,
                                onClick = { docFilter = if (docFilter == VehicleDocExpiryStatus.EXPIRING_SOON) null else VehicleDocExpiryStatus.EXPIRING_SOON },
                                label = { Text("${VehicleDocExpiryStatus.EXPIRING_SOON.label(isHindi)} ($expiringCnt)") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                            FilterChip(
                                selected = docFilter == VehicleDocExpiryStatus.VALID,
                                onClick = { docFilter = if (docFilter == VehicleDocExpiryStatus.VALID) null else VehicleDocExpiryStatus.VALID },
                                label = { Text("${VehicleDocExpiryStatus.VALID.label(isHindi)} ($validCnt)") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                        if (canEditTransport) {
                            Button(
                                onClick = { editingDoc = null; showDocDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("transport_add_doc_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(isHindi, "वाहन दस्तावेज़ जोड़ें (Insurance/PUC/Fitness)", "Add Vehicle Document"))
                            }
                        }
                    }
                }
            }

            if (filteredDocs.isEmpty()) {
                item {
                    Text(
                        text = p10Label(isHindi, "कोई वाहन दस्तावेज़ दर्ज नहीं है।", "No vehicle documents in this filter."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredDocs, key = { it.id }) { doc ->
                    val status = doc.statusForDate(todayIso)
                    val daysLeft = doc.daysRemainingFrom(todayIso)
                    val linkedRoute = allRoutesForSchool.firstOrNull { it.id == doc.routeId }
                    val (bgCol, txtCol) = when (status) {
                        VehicleDocExpiryStatus.EXPIRED -> FailRedContainer to FailRed
                        VehicleDocExpiryStatus.EXPIRING_SOON -> AbsentAmberContainer to AbsentAmber
                        VehicleDocExpiryStatus.VALID -> PassGreenContainer to PassGreen
                    }
                    Card(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, txtCol.copy(alpha = 0.45f))) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${doc.docType.label(isHindi)} • ${doc.vehicleNumber.ifBlank { linkedRoute?.vehicleNumber ?: "-" }}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = buildString {
                                            if (linkedRoute != null) append("${linkedRoute.routeName} • ")
                                            append("Expiry: ${doc.expiryDateIso}")
                                            if (doc.documentNumber.isNotBlank()) append(" • No: ${doc.documentNumber}")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(color = bgCol, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        text = if (daysLeft < 0) "${status.label(isHindi)} (${-daysLeft}d ago)"
                                        else "${status.label(isHindi)} (${daysLeft}d)",
                                        color = txtCol,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (doc.note.isNotBlank()) {
                                Text(doc.note, style = MaterialTheme.typography.bodySmall)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (doc.photoBase64.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { onPreviewPhotoBase64(doc.docType.labelEn, doc.photoBase64) },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(p10Label(isHindi, "फोटो देखें", "View Photo"))
                                    }
                                }
                                if (canEditTransport) {
                                    OutlinedButton(
                                        onClick = { editingDoc = doc; showDocDialog = true },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(p10Label(isHindi, "बदलें", "Edit"))
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.deleteTransportVehicleDocument(doc) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FailRed),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(p10Label(isHindi, "हटाएँ", "Delete"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 2: CASH BOOK LINKED TRANSPORT EXPENSES
        if (activeSection == VehiclesTabSection.ALL || activeSection == VehiclesTabSection.EXPENSES) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = p10Label(isHindi, "वाहन खर्च (सीधे रोकड़ बही से जुड़े)", "Vehicle Expenses (Cash Book Linked)"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = p10Label(
                                isHindi,
                                "यहाँ दर्ज हर खर्च सीधे Cash Book में सेव होता है — दो बार लिखने या दोहरी गिनती की आवश्यकता नहीं।",
                                "Every entry writes once directly into Cash Book tagged with vehicle & category (zero duplicate records)."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (canLogOrModifyExpense) {
                                Button(
                                    onClick = { editingExpense = null; showExpenseDialog = true },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("transport_add_expense_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(p10Label(isHindi, "खर्च दर्ज करें", "Add Expense"))
                                }
                            }
                            if (canEditTransport) {
                                OutlinedButton(
                                    onClick = { showCategoriesDialog = true },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(p10Label(isHindi, "श्रेणियाँ (${transportSettings.expenseCategories.size})", "Categories (${transportSettings.expenseCategories.size})"))
                                }
                            }
                        }
                    }
                }
            }

            if (schoolTransportExpenses.isEmpty()) {
                item {
                    Text(
                        text = p10Label(isHindi, "अभी कोई वाहन खर्च दर्ज नहीं है।", "No transport expenses recorded yet."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(schoolTransportExpenses, key = { it.id }) { exp ->
                    val routeName = allRoutesForSchool.firstOrNull { it.id == exp.transportRouteId }?.routeName
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (exp.isCancelled) FailRedContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = (exp.transportCategory.ifBlank { exp.category }) + if (exp.isCancelled) " [CANCELLED]" else "",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (exp.isCancelled) FailRed else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${exp.dateIso} • ${exp.paymentMode.label}" +
                                            (routeName?.let { " • $it" }.orEmpty()) +
                                            (exp.vehicleNumber.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "₹${exp.amount}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (exp.isCancelled) MaterialTheme.colorScheme.onSurfaceVariant else FailRed
                                )
                            }
                            if (exp.note.isNotBlank()) Text(exp.note, style = MaterialTheme.typography.bodySmall)
                            if (exp.approvedByAdminName.isNotBlank()) {
                                Text("Admin Approved: ${exp.approvedByAdminName}", style = MaterialTheme.typography.labelSmall, color = PassGreen)
                            }
                            if (exp.isCancelled) {
                                Text("Cancel Reason: ${exp.cancelReason}", style = MaterialTheme.typography.labelSmall, color = FailRed)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (exp.billPhotoBase64.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { onPreviewPhotoBase64("Bill Photo (${exp.dateIso})", exp.billPhotoBase64) },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(p10Label(isHindi, "बिल फोटो", "Bill Photo"))
                                    }
                                }
                                if (canLogOrModifyExpense && !exp.isCancelled) {
                                    OutlinedButton(
                                        onClick = { editingExpense = exp; showExpenseDialog = true },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(p10Label(isHindi, "बदलें", "Edit"))
                                    }
                                    OutlinedButton(
                                        onClick = { cancelingExpenseTarget = exp },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FailRed),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(p10Label(isHindi, "रद्द करें", "Cancel"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 3: MONTHLY INCOME VS COST & PROFIT/LOSS
        if (activeSection == VehiclesTabSection.ALL || activeSection == VehiclesTabSection.SUMMARY) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = p10Label(isHindi, "मासिक आय बनाम खर्च सारांश", "Monthly Income vs Expense P&L"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            structure.monthOrder.forEach { m ->
                                FilterChip(
                                    selected = summaryMonthCode.equals(m, ignoreCase = true),
                                    onClick = { summaryMonthCode = m },
                                    label = { Text(m) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }

                        val netPos = monthlyReport.netProfitOrLossRs >= 0
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(p10Label(isHindi, "प्राप्त आय", "Collected"), style = MaterialTheme.typography.labelSmall)
                                Text("₹${monthlyReport.totalCollectedIncomeRs}", fontWeight = FontWeight.Bold, color = PassGreen)
                                Text("Exp: ₹${monthlyReport.totalExpectedIncomeRs} • Due: ₹${monthlyReport.totalDuesRs}", style = MaterialTheme.typography.labelSmall)
                            }
                            Column {
                                Text(p10Label(isHindi, "खर्च", "Expenses"), style = MaterialTheme.typography.labelSmall)
                                Text("₹${monthlyReport.totalExpenseRs}", fontWeight = FontWeight.Bold, color = FailRed)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(p10Label(isHindi, "शुद्ध लाभ/हानि", "Net P/L"), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = if (netPos) "+₹${monthlyReport.netProfitOrLossRs}" else "-₹${-monthlyReport.netProfitOrLossRs}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (netPos) PassGreen else FailRed
                                )
                            }
                        }

                        HorizontalDivider()
                        monthlyReport.vehicleRows.forEach { row ->
                            val rowPos = row.profitOrLossRs >= 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${row.routeName} (${row.vehicleNumber})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Collected ₹${row.collectedIncomeRs} (Due ₹${row.duesRs}) • Exp ₹${row.expenseRs}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (rowPos) "+₹${row.profitOrLossRs}" else "-₹${-row.profitOrLossRs}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (rowPos) PassGreen else FailRed
                                )
                            }
                            HorizontalDivider()
                        }
                        if (monthlyReport.unallocatedExpensesRs > 0) {
                            Text(
                                text = "General / Unallocated Vehicle Expenses: -₹${monthlyReport.unallocatedExpensesRs}",
                                style = MaterialTheme.typography.labelSmall,
                                color = FailRed
                            )
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { viewModel.generateTransportMonthlySummaryPdf(monthlyReport) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("transport_monthly_summary_pdf_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(isHindi, "मासिक सारांश A4 PDF", "Monthly Summary PDF"))
                            }
                            OutlinedButton(
                                onClick = {
                                    val csvText = TransportEngine.buildMonthlySummaryCsv(monthlyReport)
                                    val fileName = "Transport_${monthlyReport.monthCode}_${monthlyReport.yearMonthIso}.csv"
                                    scope.launch {
                                        val file = withContext(Dispatchers.IO) {
                                            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                            File(dir, fileName).apply { writeText(csvText) }
                                        }
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, fileName)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Transport CSV"))
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(isHindi, "CSV साझा करें", "Share CSV"))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDocDialog) {
        VehicleDocumentEditorDialog(
            existing = editingDoc,
            routes = allRoutesForSchool,
            isHindi = isHindi,
            onDismiss = { showDocDialog = false },
            onSave = { route, vehNo, docType, docNum, expiry, photo, note, setErr ->
                viewModel.saveTransportVehicleDocument(
                    existing = editingDoc,
                    route = route,
                    vehicleNumber = vehNo,
                    docType = docType,
                    documentNumber = docNum,
                    expiryDateIso = expiry,
                    photoBase64 = photo,
                    note = note
                ) { ok, err ->
                    if (ok) showDocDialog = false else setErr(err ?: "Unable to save document")
                }
            }
        )
    }

    if (showExpenseDialog) {
        val activeAdmins = remember(uiState.orgMembers) {
            uiState.orgMembers.filter {
                it.memberStatus == com.example.domain.MemberStatus.ACTIVE &&
                    (it.orgRole == com.example.domain.OrgRole.OWNER || it.orgRole == com.example.domain.OrgRole.ADMIN)
            }
        }
        TransportExpenseEditorDialog(
            existing = editingExpense,
            routes = allRoutesForSchool,
            categories = transportSettings.expenseCategories,
            isAdmin = uiState.isAdmin,
            approvalLimitRs = cashBookSettings.expenseApprovalLimitRs,
            activeAdmins = activeAdmins,
            isHindi = isHindi,
            onDismiss = { showExpenseDialog = false },
            onSave = { dateIso, route, vehNo, cat, amt, mode, note, photo, editReason, approverUid, approverName, approvalNote, setErr ->
                val curEdit = editingExpense
                if (curEdit == null) {
                    viewModel.addTransportExpenseToCashBook(
                        dateIso = dateIso,
                        route = route,
                        vehicleNumber = vehNo,
                        transportCategory = cat,
                        amount = amt,
                        paymentMode = mode,
                        note = note,
                        billPhotoBase64 = photo,
                        approvedByAdminUid = approverUid,
                        approvedByAdminName = approverName,
                        approvalNote = approvalNote
                    ) { ok, err ->
                        if (ok) showExpenseDialog = false else setErr(err ?: "Unable to save expense")
                    }
                } else {
                    viewModel.editTransportExpenseInCashBook(
                        original = curEdit,
                        route = route,
                        vehicleNumber = vehNo,
                        transportCategory = cat,
                        newAmount = amt,
                        newPaymentMode = mode,
                        newNote = note,
                        newBillPhotoBase64 = photo,
                        editReason = editReason,
                        approvedByAdminUid = approverUid,
                        approvedByAdminName = approverName,
                        approvalNote = approvalNote
                    ) { ok, err ->
                        if (ok) showExpenseDialog = false else setErr(err ?: "Unable to update expense")
                    }
                }
            }
        )
    }

    cancelingExpenseTarget?.let { target ->
        CancelTransportExpenseDialog(
            entry = target,
            isHindi = isHindi,
            onDismiss = { cancelingExpenseTarget = null },
            onConfirmCancel = { reason, setErr ->
                viewModel.cancelTransportExpenseInCashBook(target, reason) { ok, err ->
                    if (ok) cancelingExpenseTarget = null else setErr(err ?: "Unable to cancel")
                }
            }
        )
    }

    if (showCategoriesDialog) {
        TransportExpenseCategoriesDialog(
            currentCategories = transportSettings.expenseCategories,
            isHindi = isHindi,
            onDismiss = { showCategoriesDialog = false },
            onSave = { cats, setErr ->
                viewModel.saveTransportExpenseCategories(cats) { ok, err ->
                    if (ok) showCategoriesDialog = false else setErr(err ?: "Unable to save")
                }
            }
        )
    }
}
