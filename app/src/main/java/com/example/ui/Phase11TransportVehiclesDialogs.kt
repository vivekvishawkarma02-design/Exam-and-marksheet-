package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.cloud.CloudMember
import com.example.domain.CashBookEntryRecord
import com.example.domain.PaymentMode
import com.example.domain.Phase6DateUtils
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportVehicleDocumentRecord
import com.example.domain.VehicleDocumentType
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VehicleDocumentEditorDialog(
    existing: TransportVehicleDocumentRecord?,
    routes: List<TransportRouteRecord>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        route: TransportRouteRecord?,
        vehicleNumber: String,
        docType: VehicleDocumentType,
        docNumber: String,
        expiryDateIso: String,
        photoBase64: String,
        note: String,
        setError: (String) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedRouteId by remember { mutableStateOf(existing?.routeId ?: routes.firstOrNull()?.id.orEmpty()) }
    val selectedRoute = routes.firstOrNull { it.id == selectedRouteId }
    var vehicleNumber by remember { mutableStateOf(existing?.vehicleNumber ?: selectedRoute?.vehicleNumber.orEmpty()) }
    var docType by remember { mutableStateOf(existing?.docType ?: VehicleDocumentType.INSURANCE) }
    var docNumber by remember { mutableStateOf(existing?.documentNumber.orEmpty()) }
    var expiryDateIso by remember { mutableStateOf(existing?.expiryDateIso.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var photoBase64 by remember { mutableStateOf(existing?.photoBase64.orEmpty()) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    ImageUtils.encodeUriToBase64Under150Kb(context, uri)
                }
                if (!encoded.isNullOrBlank()) photoBase64 = encoded
                else errMsg = "Could not compress photo under 150 KB. Try another image."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) p10Label(isHindi, "वाहन दस्तावेज़ जोड़ें", "Add Vehicle Document")
                else p10Label(isHindi, "दस्तावेज़ संपादित करें", "Edit Vehicle Document"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (routes.isNotEmpty()) {
                    Text(p10Label(isHindi, "रूट चुनें (वैकल्पिक):", "Route / Vehicle:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        routes.forEach { rt ->
                            FilterChip(
                                selected = selectedRouteId == rt.id,
                                onClick = {
                                    selectedRouteId = rt.id
                                    if (rt.vehicleNumber.isNotBlank()) vehicleNumber = rt.vehicleNumber
                                },
                                label = { Text("${rt.routeName} (${rt.vehicleNumber.ifBlank { "-" }})") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = vehicleNumber,
                    onValueChange = { vehicleNumber = it },
                    label = { Text(p10Label(isHindi, "गाड़ी नंबर *", "Vehicle Number *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(p10Label(isHindi, "दस्तावेज़ प्रकार:", "Document Type:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VehicleDocumentType.entries.forEach { t ->
                        FilterChip(
                            selected = docType == t,
                            onClick = { docType = t },
                            label = { Text(t.label(isHindi)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = docNumber,
                    onValueChange = { docNumber = it },
                    label = { Text(p10Label(isHindi, "पॉलिसी / दस्तावेज़ नं.", "Policy / Document No.")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = expiryDateIso,
                    onValueChange = { expiryDateIso = it; errMsg = null },
                    label = { Text(p10Label(isHindi, "समाप्ति तिथि (YYYY-MM-DD) *", "Expiry Date (YYYY-MM-DD) *")) },
                    placeholder = { Text("2026-08-15") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(p10Label(isHindi, "नोट", "Note (optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (photoBase64.isBlank()) p10Label(isHindi, "फोटो जोड़ें", "Attach Photo") else p10Label(isHindi, "फोटो बदलें ✓", "Change Photo ✓"))
                    }
                    if (photoBase64.isNotBlank()) {
                        TextButton(onClick = { photoBase64 = "" }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(p10Label(isHindi, "फोटो हटाएँ", "Remove Photo"))
                        }
                    }
                }
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedRoute, vehicleNumber, docType, docNumber, expiryDateIso, photoBase64, note) { errMsg = it }
                },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransportExpenseEditorDialog(
    existing: CashBookEntryRecord?,
    routes: List<TransportRouteRecord>,
    categories: List<String>,
    isAdmin: Boolean,
    approvalLimitRs: Int,
    activeAdmins: List<CloudMember>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        dateIso: String,
        route: TransportRouteRecord?,
        vehicleNumber: String,
        transportCategory: String,
        amount: Int,
        paymentMode: PaymentMode,
        note: String,
        billPhotoBase64: String,
        editReason: String,
        approverUid: String,
        approverName: String,
        approvalNote: String,
        setError: (String) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dateIso by remember { mutableStateOf(existing?.dateIso ?: Phase6DateUtils.todayIso()) }
    var selectedRouteId by remember { mutableStateOf(existing?.transportRouteId ?: routes.firstOrNull()?.id.orEmpty()) }
    val selectedRoute = routes.firstOrNull { it.id == selectedRouteId }
    var vehicleNumber by remember { mutableStateOf(existing?.vehicleNumber ?: selectedRoute?.vehicleNumber.orEmpty()) }
    var category by remember { mutableStateOf(existing?.transportCategory?.ifBlank { categories.firstOrNull() ?: "Diesel" } ?: (categories.firstOrNull() ?: "Diesel")) }
    var amountText by remember { mutableStateOf(if ((existing?.amount ?: 0) > 0) existing?.amount.toString() else "") }
    var paymentMode by remember { mutableStateOf(existing?.paymentMode ?: PaymentMode.CASH) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var billPhotoBase64 by remember { mutableStateOf(existing?.billPhotoBase64.orEmpty()) }
    var editReason by remember { mutableStateOf("") }
    var approverUid by remember { mutableStateOf(existing?.approvedByAdminUid.orEmpty()) }
    var approverName by remember { mutableStateOf(existing?.approvedByAdminName.orEmpty()) }
    var approvalNote by remember { mutableStateOf(existing?.approvalNote.orEmpty()) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    val parsedAmount = amountText.toIntOrNull() ?: 0
    val needsApprover = !isAdmin && approvalLimitRs > 0 && parsedAmount > approvalLimitRs

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    ImageUtils.encodeUriToBase64Under150Kb(context, uri)
                }
                if (!encoded.isNullOrBlank()) billPhotoBase64 = encoded
                else errMsg = "Could not compress bill photo under 150 KB."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) p10Label(isHindi, "वाहन खर्च जोड़ें (Cash Book)", "Log Vehicle Expense")
                else p10Label(isHindi, "खर्च संपादित करें", "Edit Vehicle Expense"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dateIso,
                    onValueChange = { dateIso = it },
                    label = { Text(p10Label(isHindi, "दिनांक (YYYY-MM-DD)", "Date (YYYY-MM-DD)")) },
                    singleLine = true,
                    enabled = existing == null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (routes.isNotEmpty()) {
                    Text(p10Label(isHindi, "रूट / वाहन:", "Route / Vehicle:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedRouteId.isBlank(),
                            onClick = { selectedRouteId = "" },
                            label = { Text(p10Label(isHindi, "सामान्य", "General")) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        routes.forEach { rt ->
                            FilterChip(
                                selected = selectedRouteId == rt.id,
                                onClick = {
                                    selectedRouteId = rt.id
                                    if (rt.vehicleNumber.isNotBlank()) vehicleNumber = rt.vehicleNumber
                                },
                                label = { Text("${rt.routeName} (${rt.vehicleNumber.ifBlank { "-" }})") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = vehicleNumber,
                    onValueChange = { vehicleNumber = it },
                    label = { Text(p10Label(isHindi, "गाड़ी नंबर (वैकल्पिक)", "Vehicle No (optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(p10Label(isHindi, "खर्च श्रेणी:", "Category:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true),
                            onClick = { category = cat },
                            label = { Text(cat) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() }; errMsg = null },
                    label = { Text(p10Label(isHindi, "राशि ₹ *", "Amount ₹ *")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(p10Label(isHindi, "भुगतान माध्यम:", "Payment Mode:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PaymentMode.entries.forEach { pm ->
                        FilterChip(
                            selected = paymentMode == pm,
                            onClick = { paymentMode = pm },
                            label = { Text(pm.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(p10Label(isHindi, "विवरण / नोट", "Note (e.g. 20L diesel, driver slip)")) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (needsApprover) {
                    Surface(color = AbsentAmberContainer, shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "₹$approvalLimitRs से अधिक खर्च के लिए एडमिन स्वीकृति चुनें:",
                                    "Expense above ₹$approvalLimitRs requires Admin approval:"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                activeAdmins.forEach { adm ->
                                    val admLabel = adm.name.ifBlank { adm.email }
                                    FilterChip(
                                        selected = approverUid == adm.uid,
                                        onClick = { approverUid = adm.uid; approverName = admLabel },
                                        label = { Text(admLabel) },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = approvalNote,
                                onValueChange = { approvalNote = it },
                                label = { Text(p10Label(isHindi, "स्वीकृति नोट", "Approval Note")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (existing != null) {
                    OutlinedTextField(
                        value = editReason,
                        onValueChange = { editReason = it; errMsg = null },
                        label = { Text(p10Label(isHindi, "बदलाव का कारण (अनिवार्य) *", "Edit Reason (Mandatory) *")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (billPhotoBase64.isBlank()) p10Label(isHindi, "बिल फोटो जोड़ें", "Attach Bill Photo") else p10Label(isHindi, "बिल फोटो बदलें ✓", "Change Bill Photo ✓"))
                }
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        dateIso,
                        selectedRoute,
                        vehicleNumber,
                        category,
                        parsedAmount,
                        paymentMode,
                        note,
                        billPhotoBase64,
                        editReason,
                        approverUid,
                        approverName,
                        approvalNote
                    ) { errMsg = it }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "रोकड़ बही में सेव करें", "Save to Cash Book"))
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
internal fun CancelTransportExpenseDialog(
    entry: CashBookEntryRecord,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirmCancel: (String, (String) -> Unit) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var errMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "खर्च रद्द करें (₹${entry.amount})", "Cancel Expense (₹${entry.amount})?"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    p10Label(
                        isHindi,
                        "खर्च डिलीट नहीं होगा — Cash Book में ऑडिट कारण के साथ Cancelled दिखेगा।",
                        "Nothing is silently deleted. This marks the Cash Book entry cancelled with your reason."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; errMsg = null },
                    label = { Text(p10Label(isHindi, "रद्द करने का कारण *", "Cancellation Reason *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCancel(reason) { errMsg = it } },
                colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "खर्च रद्द करें", "Cancel Entry"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(p10Label(isHindi, "बंद करें", "Close"))
            }
        }
    )
}

@Composable
internal fun TransportExpenseCategoriesDialog(
    currentCategories: List<String>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<String>, (String) -> Unit) -> Unit
) {
    val cats = remember { mutableStateListOf<String>().apply { addAll(currentCategories) } }
    var newCat by remember { mutableStateOf("") }
    var errMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "ट्रांसपोर्ट खर्च श्रेणियाँ", "Transport Expense Categories"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                cats.forEachIndexed { idx, c ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(c, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { if (cats.size > 1) cats.removeAt(idx) },
                            enabled = cats.size > 1,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove category $c", tint = FailRed)
                        }
                    }
                    HorizontalDivider()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCat,
                        onValueChange = { newCat = it },
                        label = { Text(p10Label(isHindi, "नई श्रेणी जोड़ें", "New Category")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val clean = newCat.trim()
                            if (clean.isNotEmpty() && cats.none { it.equals(clean, ignoreCase = true) }) {
                                cats.add(clean)
                                newCat = ""
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add category")
                    }
                }
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(cats.toList()) { errMsg = it } }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(p10Label(isHindi, "सेव करें", "Save Categories"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(p10Label(isHindi, "रद्द", "Cancel"))
            }
        }
    )
}
