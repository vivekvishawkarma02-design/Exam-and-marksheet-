package com.example.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.StudentEntity
import com.example.domain.ExamFeeHeadConfig
import com.example.domain.FeeHeadType
import com.example.domain.FeeStructureConfig
import com.example.domain.OptionalFeeCatalogueItem
import com.example.domain.Phase9AccessEvaluator
import com.example.domain.SessionMonthCode
import com.example.domain.StudentFeeOverride
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Phase9FeeStructureTab(
    viewModel: SchoolViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val schoolDocId = uiState.activeSchoolDocId
    val session = uiState.filter.session
    val canEditFees = Phase9AccessEvaluator.canEditFees(uiState.accessProfile, schoolDocId) || uiState.canEditFees
    val savedStructure = remember(uiState.feeStructures, schoolDocId, session, uiState.activeSchoolClassNames) {
        uiState.feeStructureForSchool(schoolDocId, session)
    }

    var monthOrder by remember(savedStructure.id, savedStructure.clientEditedAtMs) {
        mutableStateOf(savedStructure.monthOrder.ifEmpty { SessionMonthCode.defaultAprilToMarchOrder })
    }
    var chargedMonths by remember(savedStructure.id, savedStructure.clientEditedAtMs) {
        mutableStateOf(savedStructure.chargedTuitionMonths.toSet())
    }
    var transportChargedMonths by remember(savedStructure.id, savedStructure.clientEditedAtMs) {
        mutableStateOf(savedStructure.chargedTransportMonths.toSet())
    }
    val tuitionByClassAndMonth = remember(savedStructure.id, savedStructure.clientEditedAtMs) {
        mutableStateMapOf<String, Map<String, Int>>().apply {
            putAll(savedStructure.tuitionByClassAndMonth)
        }
    }
    var examFeeHeads by remember(savedStructure.id, savedStructure.clientEditedAtMs) {
        mutableStateOf(savedStructure.examFees.ifEmpty { FeeStructureConfig.defaultExamFees(uiState.activeSchoolClassNames) })
    }
    val optionalCatalogue = remember(savedStructure.id, savedStructure.clientEditedAtMs) {
        mutableStateListOf<OptionalFeeCatalogueItem>().apply {
            addAll(savedStructure.optionalCatalogue)
        }
    }
    var concessionLimitText by remember(savedStructure.id, savedStructure.clientEditedAtMs) {
        mutableStateOf(savedStructure.maxConcessionPercentWithoutAdminApproval.toString())
    }

    var selectedTuitionClass by remember(uiState.activeSchoolClassNames) {
        mutableStateOf(uiState.activeSchoolClassNames.firstOrNull().orEmpty())
    }
    var uniformClassMonthlyText by remember(selectedTuitionClass, tuitionByClassAndMonth[selectedTuitionClass]) {
        val firstAmt = tuitionByClassAndMonth[selectedTuitionClass]?.values?.firstOrNull() ?: 0
        mutableStateOf(if (firstAmt > 0) firstAmt.toString() else "")
    }

    var showSavePastDueChoiceDialog by remember { mutableStateOf(false) }
    var editingCatalogueItem by remember { mutableStateOf<OptionalFeeCatalogueItem?>(null) }
    var showCatalogueDialog by remember { mutableStateOf(false) }
    var showAddOverrideDialog by remember { mutableStateOf(false) }

    val schoolStudents = remember(uiState.allStudents, uiState.activeSchool.id, session) {
        uiState.allStudents.filter {
            it.schoolId == uiState.activeSchool.id &&
                it.session.equals(session, ignoreCase = true) &&
                it.isActiveStatus
        }
    }
    val schoolOverrides = remember(uiState.studentFeeOverrides, schoolDocId, session) {
        uiState.studentFeeOverrides.filter {
            it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true)
        }
    }

    fun buildCandidateConfig(): FeeStructureConfig {
        val maxLimit = (concessionLimitText.toIntOrNull() ?: 20).coerceIn(0, 100)
        return savedStructure.copy(
            schoolId = schoolDocId,
            session = session,
            monthOrder = monthOrder,
            chargedTuitionMonths = monthOrder.filter { it in chargedMonths },
            chargedTransportMonths = monthOrder.filter { it in transportChargedMonths },
            tuitionByClassAndMonth = tuitionByClassAndMonth.toMap(),
            examFees = examFeeHeads,
            optionalCatalogue = optionalCatalogue.toList(),
            maxConcessionPercentWithoutAdminApproval = maxLimit
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("phase9_fee_structure_tab"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Save Banner
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fee Structure • ${uiState.activeSchool.name} ($session)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Receipts already issued never change when fee structure is updated.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canEditFees) {
                        Button(
                            onClick = { showSavePastDueChoiceDialog = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("fee_structure_save_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Structure")
                        }
                    }
                }
            }
        }

        // 1. Session Months & Charged Months
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1. Session Month Order & Charged Months",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Default order is April to March. Untick any vacation month that is not charged for Tuition or Transport.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    monthOrder.forEachIndexed { idx, mCode ->
                        val isTuitionCharged = mCode in chargedMonths
                        val isTransportCharged = mCode in transportChargedMonths
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${idx + 1}. ${SessionMonthCode.fullLabelFor(mCode)} ($mCode)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1.1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = isTuitionCharged,
                                    enabled = canEditFees,
                                    onClick = {
                                        chargedMonths = if (isTuitionCharged) chargedMonths - mCode else chargedMonths + mCode
                                    },
                                    label = { Text("Tuition") },
                                    modifier = Modifier.testTag("fee_month_tuition_toggle_$mCode")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FilterChip(
                                    selected = isTransportCharged,
                                    enabled = canEditFees,
                                    onClick = {
                                        transportChargedMonths = if (isTransportCharged) {
                                            transportChargedMonths - mCode
                                        } else {
                                            transportChargedMonths + mCode
                                        }
                                    },
                                    label = { Text("Transport") },
                                    modifier = Modifier.testTag("fee_month_transport_toggle_$mCode")
                                )
                                if (canEditFees) {
                                    IconButton(
                                        enabled = idx > 0,
                                        onClick = {
                                            val list = monthOrder.toMutableList()
                                            val tmp = list[idx - 1]
                                            list[idx - 1] = list[idx]
                                            list[idx] = tmp
                                            monthOrder = list
                                        }
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        enabled = idx < monthOrder.lastIndex,
                                        onClick = {
                                            val list = monthOrder.toMutableList()
                                            val tmp = list[idx + 1]
                                            list[idx + 1] = list[idx]
                                            list[idx] = tmp
                                            monthOrder = list
                                        }
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Monthly Tuition Fee per Class & Month
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "2. Monthly Tuition Fee per Class",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.activeSchoolClassNames.forEach { cls ->
                            FilterChip(
                                selected = selectedTuitionClass.equals(cls, ignoreCase = true),
                                onClick = { selectedTuitionClass = cls },
                                label = { Text("Class $cls") }
                            )
                        }
                    }

                    if (selectedTuitionClass.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = uniformClassMonthlyText,
                                onValueChange = { uniformClassMonthlyText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Set all charged months for Class $selectedTuitionClass (Rs.)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = canEditFees,
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("fee_tuition_uniform_input")
                            )
                            if (canEditFees) {
                                OutlinedButton(
                                    onClick = {
                                        val amt = uniformClassMonthlyText.toIntOrNull() ?: 0
                                        val updatedMap = monthOrder.associateWith { m ->
                                            if (m in chargedMonths) amt else 0
                                        }
                                        tuitionByClassAndMonth[selectedTuitionClass] = updatedMap
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("fee_tuition_apply_all_months_button")
                                ) {
                                    Text("Apply to All Months")
                                }
                            }
                        }

                        val currentClassMap = tuitionByClassAndMonth[selectedTuitionClass].orEmpty()
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            monthOrder.filter { it in chargedMonths }.forEach { mCode ->
                                val curVal = currentClassMap[mCode] ?: 0
                                var cellText by remember(selectedTuitionClass, mCode, curVal) {
                                    mutableStateOf(curVal.toString())
                                }
                                OutlinedTextField(
                                    value = cellText,
                                    onValueChange = { txt ->
                                        cellText = txt.filter { ch -> ch.isDigit() }
                                        val parsed = cellText.toIntOrNull() ?: 0
                                        val mapCopy = tuitionByClassAndMonth[selectedTuitionClass].orEmpty().toMutableMap()
                                        mapCopy[mCode] = parsed
                                        tuitionByClassAndMonth[selectedTuitionClass] = mapCopy
                                    },
                                    label = { Text("$mCode (Rs.)") },
                                    enabled = canEditFees,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Transport Fee: Automatically taken from each student's own monthly transport amount in the Student Register (students who do not use transport have no Transport column).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Exam Fees (UT-I, UT-II, Half-Yearly, Yearly)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "3. Exam Fees (UT-I, UT-II, Half-Yearly, Yearly)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Set the month in which each exam falls and the exam fee amount per class.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    examFeeHeads.forEachIndexed { headIdx, head ->
                        val headLabel = FeeHeadType.fromKey(head.examHeadKey)?.label ?: head.examHeadKey
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$headLabel (${head.examHeadKey})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (head.amountByClass.values.any { it > 0 }) "Active" else "Set 0 to skip",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Exam Month selector
                                Text("Falls in Month:", style = MaterialTheme.typography.labelSmall)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    monthOrder.forEach { mCode ->
                                        FilterChip(
                                            selected = head.monthCode.equals(mCode, ignoreCase = true),
                                            enabled = canEditFees,
                                            onClick = {
                                                val list = examFeeHeads.toMutableList()
                                                list[headIdx] = head.copy(monthCode = mCode)
                                                examFeeHeads = list
                                            },
                                            label = { Text(mCode) }
                                        )
                                    }
                                }

                                // Uniform amount helper for all classes
                                var allClassExamAmt by remember(head.examHeadKey) {
                                    val first = head.amountByClass.values.firstOrNull() ?: 0
                                    mutableStateOf(first.toString())
                                }
                                if (canEditFees) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = allClassExamAmt,
                                            onValueChange = { allClassExamAmt = it.filter { ch -> ch.isDigit() } },
                                            label = { Text("Set $headLabel for all classes (Rs.)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                val amt = allClassExamAmt.toIntOrNull() ?: 0
                                                val updatedAmounts = uiState.activeSchoolClassNames.associateWith { amt }
                                                val list = examFeeHeads.toMutableList()
                                                list[headIdx] = head.copy(amountByClass = updatedAmounts)
                                                examFeeHeads = list
                                            }
                                        ) {
                                            Text("Apply All")
                                        }
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    uiState.activeSchoolClassNames.forEach { cls ->
                                        val clsAmt = head.amountByClass[cls] ?: 0
                                        var clsText by remember(head.examHeadKey, cls, clsAmt) {
                                            mutableStateOf(clsAmt.toString())
                                        }
                                        OutlinedTextField(
                                            value = clsText,
                                            onValueChange = { txt ->
                                                clsText = txt.filter { ch -> ch.isDigit() }
                                                val parsed = clsText.toIntOrNull() ?: 0
                                                val mapCopy = head.amountByClass.toMutableMap()
                                                mapCopy[cls] = parsed
                                                val list = examFeeHeads.toMutableList()
                                                list[headIdx] = head.copy(amountByClass = mapCopy)
                                                examFeeHeads = list
                                            },
                                            label = { Text("$cls (Rs.)") },
                                            enabled = canEditFees,
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.width(110.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Optional Items Catalogue (Tie, Belt, Books, Diary, Dress, etc.)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "4. Optional Items Catalogue",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Items like Tie, Belt, Books, Diary, Dress. Nothing here is charged automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canEditFees) {
                            OutlinedButton(
                                onClick = {
                                    editingCatalogueItem = null
                                    showCatalogueDialog = true
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("fee_catalogue_add_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Item")
                            }
                        }
                    }

                    optionalCatalogue.forEachIndexed { idx, item ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${item.name} • Rs. ${item.price}" +
                                    if (item.hidden) " (Hidden)" else "",
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.hidden) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = if (item.classFilter.isEmpty()) {
                                    "All Classes"
                                } else {
                                    "Classes: ${item.classFilter.joinToString(", ")}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (canEditFees) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = {
                                            editingCatalogueItem = item
                                            showCatalogueDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit ${item.name}")
                                    }
                                    IconButton(
                                        onClick = {
                                            optionalCatalogue[idx] = item.copy(hidden = !item.hidden)
                                        }
                                    ) {
                                        Icon(
                                            if (item.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (item.hidden) "Unhide" else "Hide"
                                        )
                                    }
                                    IconButton(
                                        onClick = { optionalCatalogue.removeAt(idx) }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete ${item.name}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        // 5. Concession Limit & Per-Student Fee Overrides
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "5. Concession Approval Limit & Per-Student Overrides",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = concessionLimitText,
                        onValueChange = { concessionLimitText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Max Concession % without Admin Approval (default 20%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = canEditFees && uiState.isAdmin,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fee_concession_limit_input")
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Per-Student Fee Overrides (${schoolOverrides.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Override any fee head for an individual student with a mandatory remark.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canEditFees) {
                            OutlinedButton(
                                onClick = { showAddOverrideDialog = true },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("fee_add_student_override_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Override")
                            }
                        }
                    }

                    schoolOverrides.forEach { ov ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val cellsSummary = ov.cellOverrides.entries.joinToString(", ") { (k, v) ->
                                    "${k.replace("|", " ")} → Rs. $v"
                                }
                                Text(
                                    text = "${ov.studentName} • $cellsSummary",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                val updatedByLabel = com.example.domain.AuthorDisplayHelper.resolveAuthorName(
                                    authorUid = ov.updatedByUid,
                                    storedName = ov.updatedByName,
                                    deletedUserUids = uiState.deletedUserUids
                                )
                                Text(
                                    text = "Remark: ${ov.remark} • by $updatedByLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canEditFees) {
                                IconButton(onClick = { viewModel.deleteStudentFeeOverride(ov) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove override",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Past-Unpaid-Dues Choice Dialog when saving structure changes
    if (showSavePastDueChoiceDialog) {
        var keepPastUnpaidAtOldRate by remember { mutableStateOf(false) }
        var cutoffMonthIdx by remember { mutableStateOf((monthOrder.size / 2).coerceAtLeast(1)) }
        var saveErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSavePastDueChoiceDialog = false },
            title = { Text("Save Fee Structure Changes", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!saveErr.isNullOrBlank()) {
                        Text(saveErr!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "Already-issued receipts will NEVER be altered. How should existing unpaid dues be handled?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !keepPastUnpaidAtOldRate,
                            onClick = { keepPastUnpaidAtOldRate = false }
                        )
                        Text(
                            text = "Update unpaid dues to the new structure rates",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = keepPastUnpaidAtOldRate,
                            onClick = { keepPastUnpaidAtOldRate = true }
                        )
                        Text(
                            text = "Keep past unpaid months at old rates (apply new rates from selected month onward)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (keepPastUnpaidAtOldRate) {
                        Text("Apply new rates starting from:", style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            monthOrder.forEachIndexed { idx, mCode ->
                                FilterChip(
                                    selected = cutoffMonthIdx == idx,
                                    onClick = { cutoffMonthIdx = idx },
                                    label = { Text(mCode) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val candidate = buildCandidateConfig()
                        viewModel.saveFeeStructure(
                            updatedStructure = candidate,
                            keepPastUnpaidDuesAtOldRates = keepPastUnpaidAtOldRate,
                            monthsToPreserveOldRates = if (keepPastUnpaidAtOldRate) {
                                monthOrder.take(cutoffMonthIdx.coerceIn(0, monthOrder.size))
                            } else {
                                emptyList()
                            }
                        ) { ok, err ->
                            if (ok) {
                                showSavePastDueChoiceDialog = false
                            } else {
                                saveErr = err
                            }
                        }
                    },
                    modifier = Modifier.testTag("fee_structure_confirm_save_button")
                ) {
                    Text("Confirm & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePastDueChoiceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add/Edit Optional Catalogue Item Dialog
    if (showCatalogueDialog) {
        val existing = editingCatalogueItem
        var itemName by remember { mutableStateOf(existing?.name.orEmpty()) }
        var priceText by remember {
            mutableStateOf(existing?.price?.toString().orEmpty())
        }
        val selectedClasses = remember {
            mutableStateListOf<String>().apply {
                addAll(existing?.classFilter.orEmpty())
            }
        }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCatalogueDialog = false },
            title = { Text(if (existing == null) "Add Catalogue Item" else "Edit Catalogue Item", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!err.isNullOrBlank()) {
                        Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Item Name (e.g., Tie, Belt, Books, Diary, Dress) *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fee_catalogue_name_input")
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Default Price (Rs.) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fee_catalogue_price_input")
                    )
                    Text(
                        "Optional Class Filter (leave empty for All Classes):",
                        style = MaterialTheme.typography.labelSmall
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiState.activeSchoolClassNames.forEach { cls ->
                            val isSel = cls in selectedClasses
                            FilterChip(
                                selected = isSel,
                                onClick = { if (isSel) selectedClasses.remove(cls) else selectedClasses.add(cls) },
                                label = { Text(cls) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = itemName.trim()
                        val price = priceText.toIntOrNull()
                        if (cleanName.isBlank() || price == null || price < 0) {
                            err = "Enter a valid item name and non-negative price."
                            return@Button
                        }
                        val newItem = OptionalFeeCatalogueItem(
                            id = existing?.id ?: "cat_${UUID.randomUUID().toString().take(8)}",
                            name = cleanName,
                            price = price,
                            classFilter = selectedClasses.toList(),
                            hidden = existing?.hidden ?: false
                        )
                        val idx = optionalCatalogue.indexOfFirst { it.id == newItem.id }
                        if (idx >= 0) {
                            optionalCatalogue[idx] = newItem
                        } else {
                            optionalCatalogue.add(newItem)
                        }
                        showCatalogueDialog = false
                    },
                    modifier = Modifier.testTag("fee_catalogue_confirm_button")
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCatalogueDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Student Fee Override Dialog
    if (showAddOverrideDialog) {
        var studentQuery by remember { mutableStateOf("") }
        var pickedStudent by remember { mutableStateOf<StudentEntity?>(schoolStudents.firstOrNull()) }
        var headType by remember { mutableStateOf(FeeHeadType.TUITION) }
        var selectedMonthCode by remember { mutableStateOf("ALL") }
        var amountText by remember { mutableStateOf("") }
        var remark by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        val matchingStudents = remember(schoolStudents, studentQuery) {
            val q = studentQuery.trim().lowercase()
            schoolStudents.filter {
                q.isBlank() ||
                    it.name.lowercase().contains(q) ||
                    it.admNo.lowercase().contains(q) ||
                    it.mobile.lowercase().contains(q)
            }.take(15)
        }

        AlertDialog(
            onDismissRequest = { showAddOverrideDialog = false },
            title = { Text("Add Per-Student Fee Override", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!err.isNullOrBlank()) {
                        Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = studentQuery,
                        onValueChange = { studentQuery = it },
                        label = { Text("Search Student (Name / Adm No.)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        matchingStudents.forEach { st ->
                            FilterChip(
                                selected = pickedStudent?.id == st.id,
                                onClick = { pickedStudent = st },
                                label = { Text("${st.name} (${st.className}-${st.section})") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FeeHeadType.regularHeads.forEach { ht ->
                            FilterChip(
                                selected = headType == ht,
                                onClick = {
                                    headType = ht
                                    if (ht.isExamHead) {
                                        selectedMonthCode = examFeeHeads.firstOrNull {
                                            it.examHeadKey.equals(ht.key, ignoreCase = true)
                                        }?.monthCode ?: "ALL"
                                    }
                                },
                                label = { Text(ht.shortLabel) }
                            )
                        }
                    }

                    if (!headType.isExamHead) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = selectedMonthCode == "ALL",
                                onClick = { selectedMonthCode = "ALL" },
                                label = { Text("All Charged Months") }
                            )
                            monthOrder.forEach { mCode ->
                                FilterChip(
                                    selected = selectedMonthCode == mCode,
                                    onClick = { selectedMonthCode = mCode },
                                    label = { Text(mCode) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Overridden Amount (Rs.) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        label = { Text("Mandatory Remark (e.g., Merit Scholarship, Staff Ward) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val st = pickedStudent ?: run {
                            err = "Select a student."
                            return@Button
                        }
                        val amt = amountText.toIntOrNull() ?: run {
                            err = "Enter a valid amount."
                            return@Button
                        }
                        val effectiveMonth = if (headType.isExamHead) {
                            examFeeHeads.firstOrNull { it.examHeadKey.equals(headType.key, ignoreCase = true) }?.monthCode ?: "ALL"
                        } else {
                            selectedMonthCode.ifBlank { "ALL" }
                        }
                        viewModel.saveStudentFeeOverride(
                            student = st,
                            headType = headType,
                            monthCode = effectiveMonth,
                            overrideAmountRs = amt,
                            remark = remark
                        ) { ok, e ->
                            if (ok) showAddOverrideDialog = false else err = e
                        }
                    }
                ) {
                    Text("Save Override")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddOverrideDialog = false }) { Text("Cancel") }
            }
        )
    }
}
