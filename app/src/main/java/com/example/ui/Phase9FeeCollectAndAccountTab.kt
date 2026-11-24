package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.StudentEntity
import com.example.domain.ConcessionMode
import com.example.domain.ConcessionScope
import com.example.domain.CustomFeeChargeDraft
import com.example.domain.FeeCellState
import com.example.domain.FeeCollectionEngine
import com.example.domain.FeeHeadType
import com.example.domain.FeeMessageType
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeReceiptStatus
import com.example.domain.FeeReportsAndQueueEngine
import com.example.domain.PaymentMode
import com.example.domain.Phase6DateUtils
import com.example.domain.Phase9AccessEvaluator
import com.example.domain.PhoneNormalizer
import com.example.domain.PhoneValidationResult
import com.example.domain.SelectedOptionalSaleDraft
import com.example.domain.SessionMonthCode
import com.example.domain.StudentFeeGridCell
import com.example.domain.StudentRegisterEngine
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Phase9FeeCollectAndAccountTab(
    viewModel: SchoolViewModel,
    preselectedStudent: StudentEntity? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val schoolDocId = uiState.activeSchoolDocId
    val session = uiState.filter.session
    val canEditFees = Phase9AccessEvaluator.canEditFees(uiState.accessProfile, schoolDocId) || uiState.canEditFees

    val structure = remember(uiState.feeStructures, schoolDocId, session, uiState.activeSchoolClassNames) {
        uiState.feeStructureForSchool(schoolDocId, session)
    }

    val schoolStudents = remember(uiState.allStudents, uiState.activeSchool.id, session) {
        uiState.allStudents.filter {
            it.schoolId == uiState.activeSchool.id && it.session.equals(session, ignoreCase = true)
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedClassChip by remember { mutableStateOf<String?>(null) }
    var selectedStudentId by remember(preselectedStudent?.id, schoolStudents) {
        mutableStateOf(
            preselectedStudent?.id
                ?: uiState.selectedStudent?.id
                ?: schoolStudents.firstOrNull { it.isActiveStatus }?.id
                ?: schoolStudents.firstOrNull()?.id
        )
    }

    LaunchedEffect(preselectedStudent?.id) {
        if (preselectedStudent != null) {
            selectedStudentId = preselectedStudent.id
        }
    }

    val filteredPickerStudents = remember(schoolStudents, searchQuery, selectedClassChip) {
        val q = searchQuery.trim().lowercase()
        schoolStudents.filter { st ->
            val clsMatch = selectedClassChip == null || st.className.equals(selectedClassChip, ignoreCase = true)
            val qMatch = q.isBlank() ||
                st.name.lowercase().contains(q) ||
                st.admNo.lowercase().contains(q) ||
                st.mobile.lowercase().contains(q) ||
                st.secondMobile.lowercase().contains(q) ||
                st.fatherName.lowercase().contains(q)
            clsMatch && qMatch
        }.take(30)
    }

    val currentStudent = remember(schoolStudents, selectedStudentId) {
        schoolStudents.firstOrNull { it.id == selectedStudentId }
            ?: filteredPickerStudents.firstOrNull()
    }

    val account = remember(
        currentStudent,
        structure,
        uiState.studentFeeOverrides,
        uiState.feeReceipts
    ) {
        currentStudent?.let { uiState.computeStudentFeeAccount(it) }
    }

    // Selection states for Collect Fees
    val tickedCellKeys = remember(currentStudent?.id) { mutableStateListOf<String>() }
    val selectedOptionalItems = remember(currentStudent?.id) { mutableStateListOf<SelectedOptionalSaleDraft>() }
    val customCharges = remember(currentStudent?.id) { mutableStateListOf<CustomFeeChargeDraft>() }

    var customChargeName by remember(currentStudent?.id) { mutableStateOf("") }
    var customChargeAmountText by remember(currentStudent?.id) { mutableStateOf("") }
    var customChargeMonth by remember(currentStudent?.id, structure.monthOrder) {
        mutableStateOf(structure.monthOrder.firstOrNull() ?: "APR")
    }
    var customChargeAddToCatalogue by remember(currentStudent?.id) { mutableStateOf(false) }

    var concessionMode by remember(currentStudent?.id) { mutableStateOf(ConcessionMode.NONE) }
    var concessionInputText by remember(currentStudent?.id) { mutableStateOf("") }
    var concessionTargetLineId by remember(currentStudent?.id) { mutableStateOf("") }
    var concessionRemark by remember(currentStudent?.id) { mutableStateOf("") }
    var concessionGivenByName by remember(currentStudent?.id) {
        mutableStateOf(uiState.currentMember?.name?.ifBlank { uiState.currentMember?.email.orEmpty() }.orEmpty())
    }
    var concessionApprovedByAdminName by remember(currentStudent?.id) {
        mutableStateOf(if (uiState.isAdmin) uiState.currentMember?.name.orEmpty() else "")
    }
    var adminApprovalConfirmed by remember(currentStudent?.id) { mutableStateOf(uiState.isAdmin) }

    var partialPaymentCustomEnabled by remember(currentStudent?.id) { mutableStateOf(false) }
    var customAmountPaidText by remember(currentStudent?.id) { mutableStateOf("") }
    var paymentMode by remember(currentStudent?.id) { mutableStateOf(PaymentMode.CASH) }
    var paymentModeRef by remember(currentStudent?.id) { mutableStateOf("") }
    var receiptNote by remember(currentStudent?.id) { mutableStateOf("") }

    var cancelingReceiptTarget by remember { mutableStateOf<FeeReceiptRecord?>(null) }
    var pendingWhatsAppSentPrompt by remember { mutableStateOf<Triple<String, FeeMessageType, String>?>(null) }

    val draftResult = remember(
        account,
        tickedCellKeys.toList(),
        selectedOptionalItems.toList(),
        customCharges.toList(),
        concessionMode,
        concessionInputText,
        concessionTargetLineId,
        concessionRemark,
        partialPaymentCustomEnabled,
        customAmountPaidText,
        structure.maxConcessionPercentWithoutAdminApproval,
        uiState.accessProfile,
        uiState.isAdmin,
        adminApprovalConfirmed
    ) {
        if (account == null) null
        else {
            val selectedCells = account.allCells.filter { it.cellKey in tickedCellKeys }
            val customAmt = if (partialPaymentCustomEnabled) {
                customAmountPaidText.toIntOrNull()
            } else {
                null
            }
            val scope = if (concessionTargetLineId.isBlank()) ConcessionScope.WHOLE_RECEIPT else ConcessionScope.SINGLE_LINE
            FeeCollectionEngine.computePaymentDraft(
                selectedGridCells = selectedCells,
                selectedOptionalItems = selectedOptionalItems.toList(),
                customCharges = customCharges.toList(),
                concessionMode = concessionMode,
                concessionScope = scope,
                concessionTargetLineId = concessionTargetLineId,
                concessionInputValue = concessionInputText.toDoubleOrNull() ?: 0.0,
                concessionRemark = concessionRemark,
                amountPaidInput = customAmt,
                maxConcessionPercentWithoutAdmin = structure.maxConcessionPercentWithoutAdminApproval,
                actorMember = uiState.accessProfile,
                hasExplicitAdminApproval = uiState.isAdmin && adminApprovalConfirmed
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("phase9_fee_collect_tab"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Student Picker Card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select Student to Collect Fees or View Account",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by student name, admission no., or guardian mobile…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fee_collect_student_search")
                    )

                    // Class filter chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedClassChip == null,
                            onClick = { selectedClassChip = null },
                            label = { Text("All Classes") }
                        )
                        uiState.activeSchoolClassNames.forEach { cls ->
                            FilterChip(
                                selected = selectedClassChip.equals(cls, ignoreCase = true),
                                onClick = {
                                    selectedClassChip = if (selectedClassChip.equals(cls, ignoreCase = true)) null else cls
                                },
                                label = { Text(cls) }
                            )
                        }
                    }

                    // Student chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredPickerStudents.forEach { st ->
                            val isSelected = currentStudent?.id == st.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedStudentId = st.id },
                                label = {
                                    Text("${st.name} (${st.className}-${st.section} • Adm ${st.admNo})")
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("fee_student_chip_${st.admNo}")
                            )
                        }
                    }
                }
            }
        }

        if (currentStudent == null || account == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "No student selected. Add or pick a student to collect fees.",
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            val siblings = StudentRegisterEngine.findSiblingsForStudent(currentStudent, schoolStudents)

            // 2. Student Summary & Quick Dues Slip / SMS Strip
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${currentStudent.name} • Class ${currentStudent.className}-${currentStudent.section} (Adm ${currentStudent.admNo})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Father: ${currentStudent.fatherName.ifBlank { "—" }} • Guardian Mob: ${currentStudent.mobile.ifBlank { "—" }}" +
                                        if (currentStudent.usesTransport) {
                                            " • Transport (${currentStudent.transportRouteName.ifBlank { "Route" }}: Rs. ${currentStudent.transportMonthlyAmount}/mo)"
                                        } else {
                                            " • No Transport"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (siblings.isNotEmpty()) {
                                    Text(
                                        text = "Siblings in School: ${siblings.joinToString(", ") { "${it.name} (${it.className})" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Due: Rs. ${account.totalRemainingDue}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (account.totalRemainingDue > 0) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                                )
                                Text(
                                    text = "Paid: Rs. ${account.totalPaidOnGrid} / Scheduled: Rs. ${account.totalScheduledFee}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.generateStudentDuesSlipPdfPreview(currentStudent) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("fee_student_dues_slip_pdf_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dues Slip (A5 PDF)")
                            }

                            val smsMessage = remember(account, uiState.activeSchool.name, structure.messageTemplates) {
                                FeeReportsAndQueueEngine.fillMessageTemplate(
                                    template = structure.messageTemplates.feeDueTemplate,
                                    guardianName = currentStudent.fatherName.ifBlank { currentStudent.motherName },
                                    studentName = currentStudent.name,
                                    classSection = "${currentStudent.className}-${currentStudent.section}",
                                    amountRs = account.totalRemainingDue,
                                    monthsText = account.dueMonthsList.joinToString(", ").ifBlank { "Current Session" },
                                    schoolName = uiState.activeSchool.name
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val norm = PhoneNormalizer.normalizeIndianMobile(
                                        currentStudent.mobile.ifBlank { currentStudent.secondMobile }
                                    )
                                    val phoneTarget = (norm as? PhoneValidationResult.Valid)?.e164WithPlus ?: currentStudent.mobile
                                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("smsto:${Uri.encode(phoneTarget)}")
                                        putExtra("sms_body", smsMessage)
                                    }
                                    runCatching { context.startActivity(smsIntent) }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("fee_student_dues_sms_button")
                            ) {
                                Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SMS Dues")
                            }

                            OutlinedButton(
                                onClick = {
                                    when (val norm = PhoneNormalizer.normalizeIndianMobile(
                                        currentStudent.mobile.ifBlank { currentStudent.secondMobile }
                                    )) {
                                        is PhoneValidationResult.Invalid -> {
                                            viewModel.emitMessage(norm.reason)
                                        }
                                        is PhoneValidationResult.Valid -> {
                                            val url = PhoneNormalizer.buildWaMeLink(norm.waMeDigits, smsMessage)
                                            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            runCatching { context.startActivity(waIntent) }
                                            pendingWhatsAppSentPrompt = Triple(norm.e164WithPlus, FeeMessageType.FEE_DUE_REMINDER, smsMessage)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("fee_student_dues_whatsapp_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("WhatsApp Dues")
                            }
                        }
                    }
                }
            }

            // 3. Month × Fee Head Grid (Tuition | Transport [if usesTransport] | Exam Fee)
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                                    text = "Monthly Fee Grid (Tick any Due / Partly Paid cell to collect)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tuition and Transport can be ticked independently. Paid cells are locked.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (tickedCellKeys.isNotEmpty()) {
                                TextButton(onClick = { tickedCellKeys.clear() }) {
                                    Text("Clear Ticks (${tickedCellKeys.size})")
                                }
                            }
                        }

                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Month",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(64.dp)
                            )
                            Text(
                                text = "Tuition Fee",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (currentStudent.usesTransport) {
                                Text(
                                    text = "Transport",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = "Exam Fee",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val cellsByMonth = remember(account.allCells) {
                            account.allCells.groupBy { it.monthCode }
                        }

                        structure.monthOrder.forEach { mCode ->
                            val monthCells = cellsByMonth[mCode].orEmpty()
                            val tuitionCell = monthCells.firstOrNull { it.headType == FeeHeadType.TUITION }
                            val transportCell = monthCells.firstOrNull { it.headType == FeeHeadType.TRANSPORT }
                            val examCells = monthCells.filter { it.headType.isExamHead }

                            if (tuitionCell != null || transportCell != null || examCells.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Column(modifier = Modifier.width(64.dp)) {
                                        Text(
                                            text = mCode,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = SessionMonthCode.fullLabelFor(mCode),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Tuition column
                                    Box(modifier = Modifier.weight(1f)) {
                                        FeeGridTickableCell(
                                            cell = tuitionCell,
                                            isTicked = tuitionCell?.cellKey in tickedCellKeys,
                                            enabled = canEditFees,
                                            onToggle = { c ->
                                                if (c.cellKey in tickedCellKeys) {
                                                    tickedCellKeys.remove(c.cellKey)
                                                } else {
                                                    tickedCellKeys.add(c.cellKey)
                                                }
                                            }
                                        )
                                    }

                                    // Transport column (ONLY shown if student uses transport!)
                                    if (currentStudent.usesTransport) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            FeeGridTickableCell(
                                                cell = transportCell,
                                                isTicked = transportCell?.cellKey in tickedCellKeys,
                                                enabled = canEditFees,
                                                onToggle = { c ->
                                                    if (c.cellKey in tickedCellKeys) {
                                                        tickedCellKeys.remove(c.cellKey)
                                                    } else {
                                                        tickedCellKeys.add(c.cellKey)
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    // Exam column
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (examCells.isEmpty()) {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        "—",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        } else {
                                            examCells.forEach { exCell ->
                                                FeeGridTickableCell(
                                                    cell = exCell,
                                                    isTicked = exCell.cellKey in tickedCellKeys,
                                                    enabled = canEditFees,
                                                    showLabelPrefix = exCell.headType.shortLabel,
                                                    onToggle = { c ->
                                                        if (c.cellKey in tickedCellKeys) {
                                                            tickedCellKeys.remove(c.cellKey)
                                                        } else {
                                                            tickedCellKeys.add(c.cellKey)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }

            // 4. Optional Items Catalogue & Custom "Other Charges"
            if (canEditFees) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Optional Items & Custom Charges",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val visibleCatalogue = remember(structure.optionalCatalogue, currentStudent.className) {
                                structure.optionalCatalogue.filter { item ->
                                    !item.hidden && (item.classFilter.isEmpty() || item.classFilter.any {
                                        it.equals(currentStudent.className, ignoreCase = true)
                                    })
                                }
                            }

                            if (visibleCatalogue.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    visibleCatalogue.forEach { catItem ->
                                        val selectedEntry = selectedOptionalItems.firstOrNull { it.catalogueItem.id == catItem.id }
                                        FilterChip(
                                            selected = selectedEntry != null,
                                            onClick = {
                                                if (selectedEntry != null) {
                                                    selectedOptionalItems.remove(selectedEntry)
                                                } else {
                                                    selectedOptionalItems.add(
                                                        SelectedOptionalSaleDraft(
                                                            catalogueItem = catItem,
                                                            monthCode = structure.monthOrder.firstOrNull() ?: "APR",
                                                            quantity = 1,
                                                            unitPrice = catItem.price
                                                        )
                                                    )
                                                }
                                            },
                                            label = {
                                                Text("${catItem.name} (Rs. ${catItem.price})")
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("fee_tick_optional_${catItem.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            // Editable rows for ticked optional items
                            selectedOptionalItems.forEachIndexed { idx, sel ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = sel.catalogueItem.name,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    OutlinedTextField(
                                        value = sel.quantity.toString(),
                                        onValueChange = { txt ->
                                            val q = txt.filter { it.isDigit() }.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                            selectedOptionalItems[idx] = sel.copy(quantity = q)
                                        },
                                        label = { Text("Qty") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width(68.dp)
                                    )
                                    OutlinedTextField(
                                        value = sel.unitPrice.toString(),
                                        onValueChange = { txt ->
                                            val p = txt.filter { it.isDigit() }.toIntOrNull() ?: 0
                                            selectedOptionalItems[idx] = sel.copy(unitPrice = p)
                                        },
                                        label = { Text("Price (Rs.)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width(105.dp)
                                    )
                                    // Month chip cycle
                                    AssistChip(
                                        onClick = {
                                            val curIdx = structure.monthOrder.indexOf(sel.monthCode)
                                            val nextMonth = structure.monthOrder.getOrElse((curIdx + 1) % structure.monthOrder.size) { "APR" }
                                            selectedOptionalItems[idx] = sel.copy(monthCode = nextMonth)
                                        },
                                        label = { Text(sel.monthCode) }
                                    )
                                    IconButton(onClick = { selectedOptionalItems.removeAt(idx) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                                    }
                                }
                            }

                            HorizontalDivider()

                            // Other Custom Charges entry
                            Text(
                                text = "Other Charges (custom item not in catalogue)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customChargeName,
                                    onValueChange = { customChargeName = it },
                                    label = { Text("Charge Name") },
                                    placeholder = { Text("e.g., Sports Kit, ID Badge") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("fee_custom_charge_name_input")
                                )
                                OutlinedTextField(
                                    value = customChargeAmountText,
                                    onValueChange = { customChargeAmountText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Rs.") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .testTag("fee_custom_charge_amount_input")
                                )
                                AssistChip(
                                    onClick = {
                                        val curIdx = structure.monthOrder.indexOf(customChargeMonth)
                                        customChargeMonth = structure.monthOrder.getOrElse((curIdx + 1) % structure.monthOrder.size) { "APR" }
                                    },
                                    label = { Text(customChargeMonth) }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = customChargeAddToCatalogue,
                                        onCheckedChange = { customChargeAddToCatalogue = it },
                                        modifier = Modifier.testTag("fee_custom_charge_add_to_catalogue_checkbox")
                                    )
                                    Text(
                                        "Add this to the catalogue",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val cleanName = customChargeName.trim()
                                        val amt = customChargeAmountText.toIntOrNull()
                                        if (cleanName.isNotBlank() && amt != null && amt > 0) {
                                            customCharges.add(
                                                CustomFeeChargeDraft(
                                                    id = UUID.randomUUID().toString().take(8),
                                                    name = cleanName,
                                                    amount = amt,
                                                    monthCode = customChargeMonth,
                                                    addToCatalogue = customChargeAddToCatalogue
                                                )
                                            )
                                            customChargeName = ""
                                            customChargeAmountText = ""
                                            customChargeAddToCatalogue = false
                                        }
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("fee_custom_charge_add_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Line")
                                }
                            }

                            customCharges.forEachIndexed { idx, cc ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${cc.name} (${cc.monthCode}) • Rs. ${cc.amount}" +
                                            if (cc.addToCatalogue) " [+Catalogue]" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(onClick = { customCharges.removeAt(idx) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove custom charge")
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Concession, Partial Payment, Payment Mode & Save Receipt Card
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Concession & Payment Checkout",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            // Concession Mode Selector
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = concessionMode == ConcessionMode.NONE,
                                    onClick = {
                                        concessionMode = ConcessionMode.NONE
                                        concessionInputText = ""
                                    },
                                    label = { Text("No Concession") }
                                )
                                FilterChip(
                                    selected = concessionMode == ConcessionMode.RUPEES,
                                    onClick = { concessionMode = ConcessionMode.RUPEES },
                                    label = { Text("In Rupees (Rs.)") },
                                    modifier = Modifier.testTag("fee_concession_mode_rs")
                                )
                                FilterChip(
                                    selected = concessionMode == ConcessionMode.PERCENTAGE,
                                    onClick = { concessionMode = ConcessionMode.PERCENTAGE },
                                    label = { Text("Percentage (%)") },
                                    modifier = Modifier.testTag("fee_concession_mode_pct")
                                )
                            }

                            if (concessionMode != ConcessionMode.NONE) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = concessionInputText,
                                        onValueChange = { concessionInputText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                        label = {
                                            Text(if (concessionMode == ConcessionMode.PERCENTAGE) "Concession %" else "Concession Rs.")
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("fee_concession_value_input")
                                    )
                                    OutlinedTextField(
                                        value = concessionRemark,
                                        onValueChange = { concessionRemark = it },
                                        label = { Text("Mandatory Remark *") },
                                        placeholder = { Text("sibling / teacher's child") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1.4f)
                                            .testTag("fee_concession_remark_input")
                                    )
                                }

                                // Apply on Whole Receipt OR One Selected Line
                                val currentLines = draftResult?.draftLines.orEmpty()
                                if (currentLines.size > 1) {
                                    Text(
                                        "Apply Concession On:",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = concessionTargetLineId.isBlank(),
                                            onClick = { concessionTargetLineId = "" },
                                            label = { Text("Whole Receipt") }
                                        )
                                        currentLines.forEach { li ->
                                            FilterChip(
                                                selected = concessionTargetLineId == li.lineId,
                                                onClick = { concessionTargetLineId = li.lineId },
                                                label = { Text(li.title) }
                                            )
                                        }
                                    }
                                }

                                if (draftResult?.requiresAdminApprovalForConcession == true) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Admin Approval Required: Concession (${String.format("%.1f", draftResult.effectiveConcessionPercent)}%) exceeds the ${structure.maxConcessionPercentWithoutAdminApproval}% limit.",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE65100)
                                            )
                                            if (uiState.isAdmin) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(
                                                        checked = adminApprovalConfirmed,
                                                        onCheckedChange = { adminApprovalConfirmed = it }
                                                    )
                                                    Text(
                                                        "Approve as Admin (${uiState.currentMember?.name ?: "Admin"})",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = "Only an Owner or Admin can approve concessions above ${structure.maxConcessionPercentWithoutAdminApproval}%.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFFB71C1C)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()

                            // Totals Breakdown Box
                            val subtotal = draftResult?.totalBeforeConcession ?: 0
                            val concAmt = draftResult?.concessionAmount ?: 0
                            val netPayable = draftResult?.netPayableAmount ?: 0
                            val paidAmt = draftResult?.amountPaid ?: 0
                            val balAfter = draftResult?.balanceDueAfterPayment ?: 0

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Total Before Concession:", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Rs. $subtotal",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (concAmt > 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Less Concession (${concessionRemark.ifBlank { "No remark" }}):",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(0xFF1B5E20)
                                            )
                                            Text(
                                                "- Rs. $concAmt",
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1B5E20)
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Net Payable Amount:",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Rs. $netPayable",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (balAfter > 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Remaining Balance on Selected Cells (Stays Due):",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFB71C1C),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Rs. $balAfter",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFB71C1C),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Partial Payment toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = partialPaymentCustomEnabled,
                                    onCheckedChange = {
                                        partialPaymentCustomEnabled = it
                                        if (it && customAmountPaidText.isBlank()) {
                                            customAmountPaidText = netPayable.toString()
                                        }
                                    },
                                    modifier = Modifier.testTag("fee_partial_payment_checkbox")
                                )
                                Text(
                                    text = "Partial Payment (enter less than payable; balance stays due on cells)",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (partialPaymentCustomEnabled) {
                                OutlinedTextField(
                                    value = customAmountPaidText,
                                    onValueChange = { customAmountPaidText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Actual Amount Paid Now (Rs.) *") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("fee_partial_payment_amount_input")
                                )
                            }

                            // Payment Mode chips
                            Text("Payment Mode:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PaymentMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = paymentMode == mode,
                                        onClick = { paymentMode = mode },
                                        label = { Text(mode.label) },
                                        modifier = Modifier.testTag("fee_payment_mode_${mode.wireValue.lowercase()}")
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (paymentMode != PaymentMode.CASH) {
                                    OutlinedTextField(
                                        value = paymentModeRef,
                                        onValueChange = { paymentModeRef = it },
                                        label = { Text("${paymentMode.label} Ref / Cheque No.") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedTextField(
                                    value = receiptNote,
                                    onValueChange = { receiptNote = it },
                                    label = { Text("Receipt Note (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.2f)
                                )
                            }

                            if (draftResult?.validationError != null && (tickedCellKeys.isNotEmpty() || selectedOptionalItems.isNotEmpty() || customCharges.isNotEmpty())) {
                                Text(
                                    text = draftResult.validationError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val isDraftValid = draftResult != null &&
                                draftResult.validationError == null &&
                                draftResult.draftLines.isNotEmpty()
                            Button(
                                enabled = isDraftValid &&
                                    (!draftResult!!.requiresAdminApprovalForConcession || (uiState.isAdmin && adminApprovalConfirmed)),
                                onClick = {
                                    val draft = draftResult ?: return@Button
                                    val adminUid = if (draft.requiresAdminApprovalForConcession && uiState.isAdmin && adminApprovalConfirmed) {
                                        uiState.currentMember?.uid ?: viewModel.currentUserId
                                    } else {
                                        ""
                                    }
                                    val combinedNote = listOfNotNull(
                                        paymentModeRef.trim().takeIf { it.isNotBlank() }?.let { "Ref: $it" },
                                        receiptNote.trim().takeIf { it.isNotBlank() }
                                    ).joinToString(" | ")
                                    val concScope = if (concessionTargetLineId.isBlank()) {
                                        ConcessionScope.WHOLE_RECEIPT
                                    } else {
                                        ConcessionScope.SINGLE_LINE
                                    }
                                    viewModel.issueFeeReceipt(
                                        student = currentStudent,
                                        draft = draft,
                                        concessionMode = concessionMode,
                                        concessionScope = concScope,
                                        concessionTargetLineId = concessionTargetLineId,
                                        concessionInputValue = concessionInputText.toDoubleOrNull() ?: 0.0,
                                        concessionRemark = concessionRemark,
                                        concessionGivenByName = concessionGivenByName,
                                        concessionApprovedByAdminUid = adminUid,
                                        concessionApprovedByAdminName = concessionApprovedByAdminName,
                                        paymentMode = paymentMode,
                                        note = combinedNote,
                                        customChargesToCatalogue = customCharges.toList()
                                    ) { ok, err, issuedReceipt ->
                                        if (ok && issuedReceipt != null) {
                                            tickedCellKeys.clear()
                                            selectedOptionalItems.clear()
                                            customCharges.clear()
                                            concessionMode = ConcessionMode.NONE
                                            concessionInputText = ""
                                            concessionRemark = ""
                                            partialPaymentCustomEnabled = false
                                            customAmountPaidText = ""
                                            paymentModeRef = ""
                                            receiptNote = ""
                                            viewModel.generateFeeReceiptPdfPreview(issuedReceipt)
                                        } else if (!err.isNullOrBlank()) {
                                            viewModel.emitMessage(err)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                                    .testTag("fee_issue_receipt_button")
                            ) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save & Issue Receipt (Rs. $paidAmt)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 6. Student Account Receipts History (Issued & Cancelled)
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val issuedReceipts = remember(account.receipts) {
                            account.receipts.filter { !it.isCancelled }
                        }
                        val cancelledReceipts = remember(account.receipts) {
                            account.receipts.filter { it.isCancelled }
                        }
                        val allStudentReceipts = remember(account.receipts) {
                            account.receipts.sortedByDescending { it.issuedAtMs }
                        }
                        Text(
                            text = "Student Receipts History (${issuedReceipts.size} Issued, ${cancelledReceipts.size} Cancelled)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Receipts are never deleted or edited. If a correction is needed, cancel with a mandatory reason and issue a new receipt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (allStudentReceipts.isEmpty()) {
                            Text(
                                text = "No receipts issued for ${currentStudent.name} in session $session yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            allStudentReceipts.forEach { rcp ->
                                val isCancelled = rcp.status == FeeReceiptStatus.CANCELLED
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("fee_receipt_card_${rcp.receiptNumber}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCancelled) {
                                            Color(0xFFFFEBEE)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        }
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
                                                text = "${rcp.receiptNumber} • ${rcp.receiptDateIso}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                textDecoration = if (isCancelled) TextDecoration.LineThrough else null
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isCancelled) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                                            ) {
                                                Text(
                                                    text = if (isCancelled) {
                                                        "CANCELLED"
                                                    } else {
                                                        "Rs. ${rcp.amountPaid} (${rcp.paymentMode.label})"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = rcp.lineItems.joinToString(", ") {
                                                "${it.title}: Rs.${it.paidAllocated}"
                                            },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (rcp.concessionAmount > 0) {
                                            Text(
                                                text = "Concession: Rs. ${rcp.concessionAmount} (${rcp.concessionRemark}) by ${rcp.concessionGivenByName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF1B5E20)
                                            )
                                        }
                                        Text(
                                            text = "Received by: ${rcp.receivedByName}" +
                                                if (rcp.balanceDueOnReceipt > 0) {
                                                    " • Balance remaining on items: Rs. ${rcp.balanceDueOnReceipt}"
                                                } else {
                                                    ""
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isCancelled) {
                                            Text(
                                                text = "Cancelled by ${rcp.cancelledByName} • Reason: ${rcp.cancelReason}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB71C1C)
                                            )
                                        }

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            AssistChip(
                                                onClick = { viewModel.generateFeeReceiptPdfPreview(rcp) },
                                                label = { Text("A5 Receipt PDF") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                                },
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("fee_receipt_pdf_${rcp.receiptNumber}")
                                            )
                                            if (!isCancelled) {
                                                AssistChip(
                                                    onClick = {
                                                        val msg = FeeReportsAndQueueEngine.fillMessageTemplate(
                                                            template = structure.messageTemplates.feeReceiptTemplate,
                                                            guardianName = rcp.fatherName.ifBlank { currentStudent.fatherName.ifBlank { currentStudent.motherName } },
                                                            studentName = rcp.studentName,
                                                            classSection = "${rcp.className}-${rcp.section}",
                                                            amountRs = rcp.amountPaid,
                                                            monthsText = rcp.paidMonthsSummary(),
                                                            schoolName = uiState.activeSchool.name
                                                        )
                                                        when (val norm = PhoneNormalizer.normalizeIndianMobile(
                                                            currentStudent.mobile.ifBlank { currentStudent.secondMobile }
                                                        )) {
                                                            is PhoneValidationResult.Invalid -> {
                                                                viewModel.emitMessage(norm.reason)
                                                            }
                                                            is PhoneValidationResult.Valid -> {
                                                                val url = PhoneNormalizer.buildWaMeLink(norm.waMeDigits, msg)
                                                                runCatching {
                                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                                }
                                                                pendingWhatsAppSentPrompt = Triple(norm.e164WithPlus, FeeMessageType.FEE_RECEIPT, msg)
                                                            }
                                                        }
                                                    },
                                                    label = { Text("WhatsApp Receipt") },
                                                    leadingIcon = {
                                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    },
                                                    modifier = Modifier.heightIn(min = 48.dp)
                                                )
                                            }
                                            if (!isCancelled && canEditFees) {
                                                AssistChip(
                                                    onClick = { cancelingReceiptTarget = rcp },
                                                    label = { Text("Cancel Receipt") },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Default.Cancel,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("fee_cancel_receipt_${rcp.receiptNumber}")
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
    }

    // Cancel Receipt Mandatory Reason Dialog
    if (cancelingReceiptTarget != null) {
        val target = cancelingReceiptTarget!!
        var reason by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { cancelingReceiptTarget = null },
            title = { Text("Cancel Receipt ${target.receiptNumber}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!err.isNullOrBlank()) {
                        Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "Receipt ${target.receiptNumber} (Rs. ${target.amountPaid}) will stay visible as CANCELLED in the register and audit log, and its fee cells will become DUE again so you can issue a corrected receipt.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Mandatory Reason for Cancellation *") },
                        placeholder = { Text("e.g., Wrong month ticked / Cheque bounced / Wrong amount") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fee_cancel_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.cancelFeeReceipt(target, reason) { ok, e ->
                            if (ok) cancelingReceiptTarget = null else err = e
                        }
                    },
                    modifier = Modifier.testTag("fee_cancel_confirm_button")
                ) {
                    Text("Confirm Cancel Receipt")
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelingReceiptTarget = null }) { Text("Keep Receipt") }
            }
        )
    }

    // Post-WhatsApp "Sent?" Confirmation Dialog
    if (pendingWhatsAppSentPrompt != null && currentStudent != null) {
        val (e164, msgType, msgBody) = pendingWhatsAppSentPrompt!!
        val stuDocId = uiState.studentDocIdFor(currentStudent.id)
        val guardian = currentStudent.fatherName.ifBlank { currentStudent.motherName }.ifBlank { "Guardian" }
        AlertDialog(
            onDismissRequest = { pendingWhatsAppSentPrompt = null },
            title = { Text("Sent on WhatsApp?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Did you send the ${msgType.label} message to ${currentStudent.name}'s guardian ($e164)? Tap 'Mark Sent' to record the date and time."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (stuDocId.isNotBlank()) {
                            viewModel.markFeeQueueMessageSent(
                                schoolDocId = schoolDocId,
                                studentDocId = stuDocId,
                                studentName = currentStudent.name,
                                className = currentStudent.className,
                                section = currentStudent.section,
                                guardianName = guardian,
                                guardianMobileE164 = e164,
                                messageType = msgType,
                                contextKey = "${msgType.wireValue}_${Phase6DateUtils.todayIso()}",
                                messageText = msgBody
                            )
                        }
                        pendingWhatsAppSentPrompt = null
                    }
                ) {
                    Text("Yes, Mark Sent")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingWhatsAppSentPrompt = null }) {
                    Text("Not Sent")
                }
            }
        )
    }
}

@Composable
private fun FeeGridTickableCell(
    cell: StudentFeeGridCell?,
    isTicked: Boolean,
    enabled: Boolean,
    showLabelPrefix: String? = null,
    onToggle: (StudentFeeGridCell) -> Unit
) {
    if (cell == null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val isPaid = cell.state == FeeCellState.PAID
    val isPartlyPaid = cell.state == FeeCellState.PARTLY_PAID
    val canTick = enabled && cell.isTickable && !isPaid

    val bgColor = when {
        isPaid -> Color(0xFFE8F5E9)
        isTicked -> MaterialTheme.colorScheme.primaryContainer
        isPartlyPaid -> Color(0xFFFFF8E1)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isPaid -> Color(0xFF81C784)
        isTicked -> MaterialTheme.colorScheme.primary
        isPartlyPaid -> Color(0xFFFFB74D)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(if (isTicked) 2.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(enabled = canTick) { onToggle(cell) }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("fee_grid_cell_${cell.cellKey}"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!showLabelPrefix.isNullOrBlank()) {
                    Text(
                        text = showLabelPrefix,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (isPaid) {
                        "Rs. ${cell.scheduledAmount}"
                    } else {
                        "Rs. ${cell.remainingDue}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isPaid -> Color(0xFF1B5E20)
                        isPartlyPaid -> Color(0xFFE65100)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = when (cell.state) {
                        FeeCellState.PAID -> "Paid"
                        FeeCellState.PARTLY_PAID -> "Partly (Paid ${cell.paidAmount})"
                        FeeCellState.DUE -> if (cell.overrideRemark.isNotBlank()) "Due*" else "Due"
                        FeeCellState.NOT_APPLICABLE -> "N/A"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (cell.state) {
                        FeeCellState.PAID -> Color(0xFF1B5E20)
                        FeeCellState.PARTLY_PAID -> Color(0xFFE65100)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isPaid) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Paid",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Checkbox(
                    checked = isTicked,
                    enabled = canTick,
                    onCheckedChange = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
