package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.StudentEntity
import com.example.domain.TransportEngine
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportRouteSummary
import com.example.domain.TransportStop
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase11TransportScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isHindi = uiState.phase10HindiLabels
    val schoolDocId = uiState.activeSchoolDocId
    val canEdit = uiState.canEditTransport

    val schoolRoutes = remember(uiState.transportRoutes, schoolDocId) {
        uiState.transportRoutes.filter { it.schoolId == schoolDocId && it.active }
    }
    val schoolStudents = remember(uiState.allStudents, uiState.activeSchool.id) {
        uiState.allStudents.filter { it.schoolId == uiState.activeSchool.id && it.isActiveStatus }
    }
    val schoolSummary = remember(schoolRoutes, schoolStudents) {
        TransportEngine.computeSchoolTransportSummary(schoolRoutes, schoolStudents)
    }

    var showRouteDialog by remember { mutableStateOf(false) }
    var editingRoute by remember { mutableStateOf<TransportRouteRecord?>(null) }
    var assignToRoute by remember { mutableStateOf<TransportRouteRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = p10Label(isHindi, "वाहन व रूट (Transport)", "Transport Routes"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.activeSchool.name} • ${schoolRoutes.size} route(s)",
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
                            .testTag("transport_back_button")
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
        if (!uiState.canViewTransport) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(p10Label(isHindi, "आपको ट्रांसपोर्ट मॉड्यूल की अनुमति नहीं है।", "You do not have access to the Transport module."))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary KPI Card
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
                                Text(p10Label(isHindi, "कुल छात्र", "Students"), style = MaterialTheme.typography.labelSmall)
                                Text("${schoolSummary.totalTransportStudents}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(p10Label(isHindi, "सीट उपयोग", "Seats Used"), style = MaterialTheme.typography.labelSmall)
                                Text("${schoolSummary.totalSeatsUsed}/${schoolSummary.totalCapacity}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(p10Label(isHindi, "अनुमानित मासिक आय", "Expected / Mo"), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "₹${schoolSummary.totalExpectedMonthlyIncomeRs}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = PassGreen
                                )
                            }
                        }
                        if (canEdit) {
                            Button(
                                onClick = { editingRoute = null; showRouteDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("transport_add_route_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(p10Label(isHindi, "नया रूट / बस जोड़ें", "Add Route / Vehicle"))
                            }
                        }
                    }
                }
            }

            if (schoolSummary.unassignedTransportStudents.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AbsentAmberContainer,
                        border = BorderStroke(1.dp, AbsentAmber)
                    ) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "${schoolSummary.unassignedTransportStudents.size} छात्र ट्रांसपोर्ट का उपयोग कर रहे हैं पर अभी किसी रूट से नहीं जुड़े हैं।",
                                "${schoolSummary.unassignedTransportStudents.size} transport student(s) have not yet been linked to a defined route."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }

            if (schoolSummary.routeSummaries.isEmpty()) {
                item {
                    Text(
                        text = p10Label(isHindi, "अभी कोई वाहन रूट नहीं बना है। ऊपर 'नया रूट जोड़ें' दबाएँ।", "No transport routes added yet. Tap 'Add Route / Vehicle' above."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(schoolSummary.routeSummaries, key = { it.route.id }) { rSum ->
                    TransportRouteCard(
                        summary = rSum,
                        canEdit = canEdit,
                        isHindi = isHindi,
                        onCallDriver = { phone ->
                            if (phone.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                            }
                        },
                        onEdit = { editingRoute = rSum.route; showRouteDialog = true },
                        onDelete = { viewModel.deleteTransportRouteRecord(rSum.route) },
                        onAssignStudents = { assignToRoute = rSum.route },
                        onRemoveStudent = { st -> viewModel.removeStudentFromTransportRoute(st) },
                        onPrintRouteList = { viewModel.generateTransportRouteListPdf(rSum) },
                        onPrintDriverList = { viewModel.generateTransportDriverListPdf(rSum) }
                    )
                }
            }
        }
    }

    if (showRouteDialog) {
        TransportRouteEditorDialog(
            existing = editingRoute,
            isHindi = isHindi,
            onDismiss = { showRouteDialog = false },
            onSave = { name, vehNo, vehType, cap, driver, phone, fee, stops, setError ->
                viewModel.saveTransportRouteRecord(editingRoute, name, vehNo, vehType, cap, driver, phone, fee, stops) { ok, err ->
                    if (ok) showRouteDialog = false else setError(err ?: "Unable to save route")
                }
            }
        )
    }

    if (assignToRoute != null) {
        AssignStudentToRouteDialog(
            route = assignToRoute!!,
            allStudents = schoolStudents,
            isHindi = isHindi,
            onDismiss = { assignToRoute = null },
            onAssign = { student, stopName, keepExistingAmount ->
                viewModel.assignStudentToTransportRoute(
                    student = student,
                    route = assignToRoute!!,
                    stopName = stopName,
                    preserveExistingAmountIfPositive = keepExistingAmount
                ) { ok, err ->
                    if (!ok && err != null) viewModel.emitMessage(err)
                }
            }
        )
    }
}

@Composable
private fun TransportRouteCard(
    summary: TransportRouteSummary,
    canEdit: Boolean,
    isHindi: Boolean,
    onCallDriver: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAssignStudents: () -> Unit,
    onRemoveStudent: (StudentEntity) -> Unit,
    onPrintRouteList: () -> Unit,
    onPrintDriverList: () -> Unit
) {
    val route = summary.route
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (summary.isOverCapacity) FailRed.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        )
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
                    Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(route.routeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${route.vehicleType} ${route.vehicleNumber.ifBlank { "-" }} • ₹${route.monthlyFeeRs}/mo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    color = if (summary.isOverCapacity) FailRedContainer else PassGreenContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${summary.seatsUsed}/${route.seatCapacity} seats",
                        color = if (summary.isOverCapacity) FailRed else PassGreen,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Driver: ${route.driverName.ifBlank { "-" }} (${route.driverPhone.ifBlank { "-" }})",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Expected Income: ₹${summary.expectedMonthlyIncomeRs}/mo • Stops: ${route.stops.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (route.driverPhone.isNotBlank() || canEdit) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (route.driverPhone.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onCallDriver(route.driverPhone) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, tint = PassGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(isHindi, "कॉल", "Call"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (canEdit) {
                            OutlinedButton(
                                onClick = onEdit,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(isHindi, "बदलें", "Edit"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete route", tint = FailRed)
                            }
                        }
                    }
                }
            }

            if (route.stops.isNotEmpty()) {
                Text(
                    text = "Stops: " + route.stops.joinToString(" → ") {
                        val feePart = if (it.monthlyFeeRs > 0) " (₹${it.monthlyFeeRs})" else ""
                        "${it.stopName}$feePart"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPrintRouteList,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("transport_print_route_pdf_${route.id}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = p10Label(isHindi, "रूट PDF", "Route PDF"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onPrintDriverList,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("transport_print_driver_pdf_${route.id}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = p10Label(isHindi, "ड्राइवर PDF", "Driver PDF"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        if (expanded) p10Label(isHindi, "छात्र छिपाएँ", "Hide Students")
                        else p10Label(isHindi, "छात्र देखें (${summary.assignedStudents.size})", "View Students (${summary.assignedStudents.size})")
                    )
                }
                if (canEdit) {
                    Button(
                        onClick = onAssignStudents,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "छात्र जोड़ें", "Assign"))
                    }
                }
            }

            if (expanded) {
                HorizontalDivider()
                if (summary.assignedStudents.isEmpty()) {
                    Text(
                        text = p10Label(isHindi, "इस रूट पर अभी कोई छात्र नहीं है।", "No students assigned to this route yet."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    summary.assignedStudents.forEach { st ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${st.name} (${st.className}-${st.section})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Stop: ${st.transportStopName.ifBlank { "-" }} • ₹${st.transportMonthlyAmount}/mo • Mob: ${st.mobile.ifBlank { "-" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (canEdit) {
                                IconButton(
                                    onClick = { onRemoveStudent(st) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.PersonRemove, contentDescription = "Remove from route", tint = FailRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportRouteEditorDialog(
    existing: TransportRouteRecord?,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, String, String, Int, List<TransportStop>, (String) -> Unit) -> Unit
) {
    var routeName by remember { mutableStateOf(existing?.routeName.orEmpty()) }
    var vehicleNumber by remember { mutableStateOf(existing?.vehicleNumber.orEmpty()) }
    var vehicleType by remember { mutableStateOf(existing?.vehicleType ?: "Bus") }
    var seatCapacity by remember { mutableStateOf((existing?.seatCapacity ?: 30).toString()) }
    var driverName by remember { mutableStateOf(existing?.driverName.orEmpty()) }
    var driverPhone by remember { mutableStateOf(existing?.driverPhone.orEmpty()) }
    var monthlyFee by remember { mutableStateOf((existing?.monthlyFeeRs ?: 600).toString()) }
    val stops = remember {
        mutableStateListOf<TransportStop>().apply {
            if (existing != null) addAll(existing.stops)
        }
    }
    var newStopName by remember { mutableStateOf("") }
    var newStopFee by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) p10Label(isHindi, "नया रूट जोड़ें", "Add Transport Route") else p10Label(isHindi, "रूट संपादित करें", "Edit Transport Route")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it; errorText = null },
                    label = { Text(p10Label(isHindi, "रूट नाम (जैसे Route 1 - स्टेशन रोड)", "Route Name (e.g. Route 1 - Station Road)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = vehicleType == "Bus",
                        onClick = { vehicleType = "Bus" },
                        label = { Text("Bus") }
                    )
                    FilterChip(
                        selected = vehicleType == "Van",
                        onClick = { vehicleType = "Van" },
                        label = { Text("Van") }
                    )
                    FilterChip(
                        selected = vehicleType == "Auto",
                        onClick = { vehicleType = "Auto" },
                        label = { Text("Auto") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vehicleNumber,
                        onValueChange = { vehicleNumber = it },
                        label = { Text(p10Label(isHindi, "गाड़ी नंबर", "Vehicle No")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seatCapacity,
                        onValueChange = { seatCapacity = it.filter { ch -> ch.isDigit() } },
                        label = { Text(p10Label(isHindi, "सीट क्षमता", "Seats")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.7f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = driverName,
                        onValueChange = { driverName = it },
                        label = { Text(p10Label(isHindi, "ड्राइवर नाम", "Driver Name")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = driverPhone,
                        onValueChange = { driverPhone = it },
                        label = { Text(p10Label(isHindi, "ड्राइवर फोन", "Phone")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = monthlyFee,
                    onValueChange = { monthlyFee = it.filter { ch -> ch.isDigit() } },
                    label = { Text(p10Label(isHindi, "डिफ़ॉल्ट मासिक शुल्क ₹", "Default Monthly Fee ₹")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                Text(p10Label(isHindi, "स्टॉप सूची (वैकल्पिक स्टॉप शुल्क):", "Stops (optional per-stop fee):"), fontWeight = FontWeight.SemiBold)
                stops.forEachIndexed { idx, st ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${idx + 1}. ${st.stopName}" + if (st.monthlyFeeRs > 0) " (₹${st.monthlyFeeRs})" else " (Route Fee)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { stops.removeAt(idx) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove stop", tint = FailRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newStopName,
                        onValueChange = { newStopName = it },
                        label = { Text(p10Label(isHindi, "स्टॉप नाम", "Stop Name")) },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = newStopFee,
                        onValueChange = { newStopFee = it.filter { ch -> ch.isDigit() } },
                        label = { Text("₹") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.6f)
                    )
                    IconButton(
                        onClick = {
                            if (newStopName.isNotBlank()) {
                                stops.add(
                                    TransportStop(
                                        stopId = "stop_${UUID.randomUUID().toString().replace("-", "").take(6)}",
                                        stopName = newStopName.trim(),
                                        monthlyFeeRs = newStopFee.toIntOrNull() ?: 0
                                    )
                                )
                                newStopName = ""
                                newStopFee = ""
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add stop")
                    }
                }
                if (errorText != null) Text(errorText!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        routeName,
                        vehicleNumber,
                        vehicleType,
                        seatCapacity.toIntOrNull() ?: 30,
                        driverName,
                        driverPhone,
                        monthlyFee.toIntOrNull() ?: 0,
                        stops.toList()
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
private fun AssignStudentToRouteDialog(
    route: TransportRouteRecord,
    allStudents: List<StudentEntity>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onAssign: (StudentEntity, String, Boolean) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedStop by remember { mutableStateOf(route.stops.firstOrNull()?.stopName.orEmpty()) }
    var keepExistingAmount by remember { mutableStateOf(true) }

    val filtered = remember(allStudents, search) {
        val q = search.trim().lowercase()
        allStudents
            .filter {
                q.isEmpty() ||
                    it.name.lowercase().contains(q) ||
                    it.className.lowercase().contains(q) ||
                    it.admNo.lowercase().contains(q)
            }
            .sortedWith(compareBy({ it.className }, { it.rollNo }))
            .take(40)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "रूट में छात्र जोड़ें: ${route.routeName}", "Assign to ${route.routeName}")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (route.stops.isNotEmpty()) {
                    Text(p10Label(isHindi, "स्टॉप चुनें:", "Select Stop:"), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        route.stops.forEach { st ->
                            FilterChip(
                                selected = selectedStop == st.stopName,
                                onClick = { selectedStop = st.stopName },
                                label = { Text("${st.stopName} (₹${route.effectiveFeeForStop(st.stopName)})") }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keepExistingAmount, onCheckedChange = { keepExistingAmount = it })
                    Text(
                        text = p10Label(
                            isHindi,
                            "छात्र की मौजूदा ट्रांसपोर्ट फीस सुरक्षित रखें (यदि पहले से दर्ज हो)",
                            "Preserve student's existing transport amount if already set"
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(p10Label(isHindi, "छात्र खोजें...", "Search student...")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                filtered.forEach { st ->
                    val alreadyOnThis = TransportEngine.studentMatchesRoute(st, route)
                    val targetFee = if (keepExistingAmount && st.usesTransport && st.transportMonthlyAmount > 0) {
                        st.transportMonthlyAmount
                    } else route.effectiveFeeForStop(selectedStop)
                    OutlinedButton(
                        onClick = { onAssign(st, selectedStop, keepExistingAmount) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "${st.name} (${st.className}-${st.section}) • ₹$targetFee/mo" + if (alreadyOnThis) " ✓" else "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") }
        }
    )
}
