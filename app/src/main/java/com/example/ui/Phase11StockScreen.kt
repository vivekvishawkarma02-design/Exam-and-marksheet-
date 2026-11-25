package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.OptionalFeeCatalogueItem
import com.example.domain.Phase6DateUtils
import com.example.domain.StockEngine
import com.example.domain.StockItemSummary
import com.example.domain.StockTransactionType
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase11StockScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    val isHindi = uiState.phase10HindiLabels
    val schoolDocId = uiState.activeSchoolDocId
    val session = uiState.filter.session
    val canEdit = uiState.canEditStock

    val structure = remember(uiState.effectivePhase11FeeStructures, schoolDocId, session) {
        uiState.feeStructureForPhase11(schoolDocId, session)
    }
    val summaries = remember(structure, uiState.stockTransactions, uiState.effectivePhase11FeeReceipts, schoolDocId, session) {
        StockEngine.computeStockSummary(
            schoolId = schoolDocId,
            session = session,
            catalogue = structure.optionalCatalogue,
            transactions = uiState.stockTransactions,
            feeReceipts = uiState.effectivePhase11FeeReceipts
        )
    }
    val ledger = remember(structure, uiState.stockTransactions, uiState.effectivePhase11FeeReceipts, schoolDocId, session) {
        StockEngine.buildUnifiedLedger(
            schoolId = schoolDocId,
            session = session,
            catalogue = structure.optionalCatalogue,
            transactions = uiState.stockTransactions,
            feeReceipts = uiState.effectivePhase11FeeReceipts
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var lowStockOnly by remember { mutableStateOf(false) }
    var showItemDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<OptionalFeeCatalogueItem?>(null) }
    var stockInItem by remember { mutableStateOf<OptionalFeeCatalogueItem?>(null) }
    var adjustItem by remember { mutableStateOf<OptionalFeeCatalogueItem?>(null) }

    val lowStockCount = summaries.count { it.isLowStock }
    val totalStockValue = summaries.sumOf { it.estimatedStockValueRs }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = p10Label(isHindi, "स्टॉक रजिस्टर (Stock)", "School Stock"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = p10Label(
                                isHindi,
                                "फीस रसीद के साथ एकीकृत • $session",
                                "Unified with Fee Counter • $session"
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
                            .testTag("stock_back_button")
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
        if (!uiState.canViewStock) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(p10Label(isHindi, "आपको स्टॉक मॉड्यूल की अनुमति नहीं है।", "You do not have access to the Stock module."))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(p10Label(isHindi, "स्टॉक सारांश (${summaries.size})", "Summary (${summaries.size})")) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(p10Label(isHindi, "लेन-देन इतिहास (${ledger.size})", "History (${ledger.size})")) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }

            if (selectedTab == 0) {
                val displayItems = if (lowStockOnly) summaries.filter { it.isLowStock } else summaries
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(p10Label(isHindi, "कुल वस्तुएँ", "Total Items"), style = MaterialTheme.typography.labelSmall)
                                        Text("${summaries.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text(p10Label(isHindi, "कम स्टॉक", "Low Stock"), style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            "$lowStockCount",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (lowStockCount > 0) FailRed else PassGreen
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(p10Label(isHindi, "स्टॉक मूल्य", "Stock Value"), style = MaterialTheme.typography.labelSmall)
                                        Text("₹$totalStockValue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (canEdit) {
                                        Button(
                                            onClick = { editingItem = null; showItemDialog = true },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("stock_add_item_button")
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(p10Label(isHindi, "नया आइटम", "Add Item"))
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.generateStockSummaryReportPdf() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("stock_print_report_button")
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(p10Label(isHindi, "A4 स्टॉक रिपोर्ट", "A4 Stock PDF"))
                                    }
                                }
                            }
                        }
                    }

                    if (lowStockCount > 0) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AbsentAmberContainer,
                                border = BorderStroke(1.dp, AbsentAmber)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = AbsentAmber)
                                        Text(
                                            text = p10Label(
                                                isHindi,
                                                "$lowStockCount आइटम कम स्टॉक चेतावनी स्तर पर हैं।",
                                                "$lowStockCount item(s) at or below low-stock warning level."
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    FilterChip(
                                        selected = lowStockOnly,
                                        onClick = { lowStockOnly = !lowStockOnly },
                                        label = { Text(if (lowStockOnly) "All" else "Low Only") }
                                    )
                                }
                            }
                        }
                    }

                    items(displayItems, key = { it.item.id }) { s ->
                        StockItemCard(
                            summary = s,
                            canEdit = canEdit,
                            isHindi = isHindi,
                            onStockIn = { stockInItem = s.item },
                            onAdjust = { adjustItem = s.item },
                            onEdit = { editingItem = s.item; showItemDialog = true }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (ledger.isEmpty()) {
                        item {
                            Text(
                                text = p10Label(isHindi, "अभी तक कोई स्टॉक खरीद, बिक्री या सुधार नहीं हुआ है।", "No stock movements recorded yet."),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(ledger, key = { it.id }) { ev ->
                            val isPos = ev.quantityDelta >= 0
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${ev.itemName} • ${ev.kind.label}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${ev.dateIso} • ${ev.detail}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        color = if (isPos) PassGreenContainer else FailRedContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (isPos) "+${ev.quantityDelta}" else "${ev.quantityDelta}",
                                            color = if (isPos) PassGreen else FailRed,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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

    if (showItemDialog) {
        StockCatalogueItemDialog(
            existing = editingItem,
            isHindi = isHindi,
            onDismiss = { showItemDialog = false },
            onSave = { name, unit, price, openStock, lowLevel, setError ->
                viewModel.saveStockCatalogueItem(editingItem, name, unit, price, openStock, lowLevel) { ok, err ->
                    if (ok) showItemDialog = false else setError(err ?: "Unable to save item")
                }
            }
        )
    }

    if (stockInItem != null) {
        StockInPurchaseDialog(
            item = stockInItem!!,
            isHindi = isHindi,
            onDismiss = { stockInItem = null },
            onSave = { dateIso, qty, unitCost, supplier, setError ->
                viewModel.recordStockInOrAdjustment(
                    item = stockInItem!!,
                    txType = StockTransactionType.STOCK_IN,
                    dateIso = dateIso,
                    quantityDelta = qty,
                    unitCostRs = unitCost,
                    supplier = supplier
                ) { ok, err ->
                    if (ok) stockInItem = null else setError(err ?: "Unable to save purchase")
                }
            }
        )
    }

    if (adjustItem != null) {
        StockManualAdjustmentDialog(
            item = adjustItem!!,
            isHindi = isHindi,
            onDismiss = { adjustItem = null },
            onSave = { delta, reason, setError ->
                viewModel.recordStockInOrAdjustment(
                    item = adjustItem!!,
                    txType = StockTransactionType.ADJUSTMENT,
                    dateIso = Phase6DateUtils.todayIso(),
                    quantityDelta = delta,
                    reason = reason
                ) { ok, err ->
                    if (ok) adjustItem = null else setError(err ?: "Unable to save adjustment")
                }
            }
        )
    }
}

@Composable
private fun StockItemCard(
    summary: StockItemSummary,
    canEdit: Boolean,
    isHindi: Boolean,
    onStockIn: () -> Unit,
    onAdjust: () -> Unit,
    onEdit: () -> Unit
) {
    val badgeBg = when {
        summary.currentStock <= 0 -> FailRedContainer
        summary.isLowStock -> AbsentAmberContainer
        else -> PassGreenContainer
    }
    val badgeFg = when {
        summary.currentStock <= 0 -> FailRed
        summary.isLowStock -> AbsentAmber
        else -> PassGreen
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (summary.isLowStock) badgeFg.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(summary.item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "₹${summary.item.price} / ${summary.item.unit} • Min alert: ${summary.item.lowStockLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(color = badgeBg, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        text = "${summary.currentStock} ${summary.item.unit}",
                        color = badgeFg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = "Open: ${summary.openingStock}  •  In: +${summary.purchasedIn}  •  Fee Sales: -${summary.soldOnReceipts}" +
                    (if (summary.cancelledReturnCount > 0) " (Ret +${summary.cancelledReturnCount})" else "") +
                    (if (summary.manualAdjustmentsNet != 0) "  •  Adj: ${if (summary.manualAdjustmentsNet > 0) "+" else ""}${summary.manualAdjustmentsNet}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStockIn,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "खरीद (+)", "Stock In"))
                    }
                    OutlinedButton(
                        onClick = onAdjust,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "सुधार", "Adjust"))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit item")
                    }
                }
            }
        }
    }
}

@Composable
private fun StockCatalogueItemDialog(
    existing: OptionalFeeCatalogueItem?,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int, Int, (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var unit by remember { mutableStateOf(existing?.unit ?: "pcs") }
    var price by remember { mutableStateOf((existing?.price ?: 0).let { if (it > 0) it.toString() else "" }) }
    var opening by remember { mutableStateOf((existing?.openingStock ?: 0).toString()) }
    var lowLevel by remember { mutableStateOf((existing?.lowStockLevel ?: 5).toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) p10Label(isHindi, "नया स्टॉक आइटम", "New Stock Item")
                else p10Label(isHindi, "आइटम संपादित करें", "Edit Stock Item")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text(p10Label(isHindi, "आइटम नाम (टाई, बेल्ट, किताब...)", "Item Name (Tie, Belt, Books...)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(p10Label(isHindi, "इकाई (pcs/set)", "Unit (pcs/set)")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter { ch -> ch.isDigit() } },
                        label = { Text(p10Label(isHindi, "बिक्री मूल्य ₹", "Selling Price ₹")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = opening,
                        onValueChange = { opening = it.filter { ch -> ch.isDigit() } },
                        label = { Text(p10Label(isHindi, "ओपनिंग स्टॉक", "Opening Stock")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lowLevel,
                        onValueChange = { lowLevel = it.filter { ch -> ch.isDigit() } },
                        label = { Text(p10Label(isHindi, "कम स्टॉक सीमा", "Low Alert")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
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
                    onSave(
                        name,
                        unit,
                        price.toIntOrNull() ?: 0,
                        opening.toIntOrNull() ?: 0,
                        lowLevel.toIntOrNull() ?: 5
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

@Composable
private fun StockInPurchaseDialog(
    item: OptionalFeeCatalogueItem,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, String, (String) -> Unit) -> Unit
) {
    var dateIso by remember { mutableStateOf(Phase6DateUtils.todayIso()) }
    var qty by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "स्टॉक खरीद: ${item.name}", "Stock In: ${item.name}")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it.filter { ch -> ch.isDigit() }; errorText = null },
                        label = { Text(p10Label(isHindi, "मात्रा (${item.unit})", "Qty (${item.unit})")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitCost,
                        onValueChange = { unitCost = it.filter { ch -> ch.isDigit() } },
                        label = { Text(p10Label(isHindi, "खरीद दर ₹", "Cost / ${item.unit} ₹")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text(p10Label(isHindi, "सप्लायर / दुकान का नाम", "Supplier Name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) Text(errorText!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(dateIso, qty.toIntOrNull() ?: 0, unitCost.toIntOrNull() ?: 0, supplier) { errorText = it }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "स्टॉक जोड़ें", "Add Stock"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    )
}

@Composable
private fun StockManualAdjustmentDialog(
    item: OptionalFeeCatalogueItem,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, String, (String) -> Unit) -> Unit
) {
    var isIncrease by remember { mutableStateOf(false) }
    var qty by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "मैनुअल सुधार: ${item.name}", "Manual Adjustment: ${item.name}")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isIncrease,
                        onClick = { isIncrease = false },
                        label = { Text(p10Label(isHindi, "घटाएँ (-)", "Reduce (-)")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = isIncrease,
                        onClick = { isIncrease = true },
                        label = { Text(p10Label(isHindi, "बढ़ाएँ (+)", "Increase (+)")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it.filter { ch -> ch.isDigit() }; errorText = null },
                    label = { Text(p10Label(isHindi, "मात्रा", "Quantity")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; errorText = null },
                    label = { Text(p10Label(isHindi, "कारण (अनिवार्य — जैसे क्षतिग्रस्त / गिनती मिलान)", "Reason (Mandatory — e.g. Damaged / Audit)")) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) Text(errorText!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qty.toIntOrNull() ?: 0
                    val delta = if (isIncrease) q else -q
                    onSave(delta, reason) { errorText = it }
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
