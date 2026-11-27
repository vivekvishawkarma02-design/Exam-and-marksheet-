package com.example.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.StudentEntity
import com.example.domain.FeeStructureConfig
import com.example.domain.TransportDeleteCheckResult
import com.example.domain.TransportEngine
import com.example.domain.TransportMigrationItem
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportRouteSummary
import com.example.domain.TransportSchoolSummary
import com.example.domain.VehicleOwnershipType
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransportRoutesTab(
    isHindi: Boolean,
    canEdit: Boolean,
    allRoutesForSchool: List<TransportRouteRecord>,
    schoolStudents: List<StudentEntity>,
    schoolSummary: TransportSchoolSummary,
    structure: FeeStructureConfig,
    viewModel: SchoolViewModel,
    onJumpToNeedsAttentionStudents: () -> Unit,
    onJumpToAssignRouteStudents: (String) -> Unit
) {
    val context = LocalContext.current
    var showInactiveRoutes by remember { mutableStateOf(true) }
    var showRouteDialog by remember { mutableStateOf(false) }
    var editingRoute by remember { mutableStateOf<TransportRouteRecord?>(null) }
    var deleteDialogTarget by remember { mutableStateOf<Pair<TransportRouteRecord, TransportDeleteCheckResult>?>(null) }
    var applyFeeTargetRoute by remember { mutableStateOf<TransportRouteRecord?>(null) }

    val displayedSummaries = remember(schoolSummary.routeSummaries, showInactiveRoutes) {
        if (showInactiveRoutes) schoolSummary.routeSummaries
        else schoolSummary.routeSummaries.filter { it.route.active }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("transport_routes_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canEdit) {
                    Button(
                        onClick = { editingRoute = null; showRouteDialog = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("transport_add_route_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(p10Label(isHindi, "नया रूट जोड़ें", "Add Route / Vehicle"))
                    }
                }
                FilterChip(
                    selected = showInactiveRoutes,
                    onClick = { showInactiveRoutes = !showInactiveRoutes },
                    label = {
                        Text(
                            if (showInactiveRoutes) p10Label(isHindi, "बंद रूट भी दिख रहे हैं", "Showing Inactive")
                            else p10Label(isHindi, "केवल सक्रिय रूट", "Active Only")
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        if (schoolSummary.unassignedTransportStudents.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AbsentAmberContainer,
                    border = BorderStroke(1.dp, AbsentAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "${schoolSummary.unassignedTransportStudents.size} छात्र ट्रांसपोर्ट पर हैं पर किसी रूट से नहीं जुड़े हैं।",
                                "${schoolSummary.unassignedTransportStudents.size} transport student(s) have no route linked yet."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedButton(
                            onClick = onJumpToNeedsAttentionStudents,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("transport_fix_unassigned_button")
                        ) {
                            Text(p10Label(isHindi, "छात्र टैब में ठीक करें", "Review in Students Tab"))
                        }
                    }
                }
            }
        }

        if (displayedSummaries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "अभी कोई वाहन रूट नहीं बना है। ऊपर 'नया रूट जोड़ें' दबाएँ।",
                            "No transport routes configured yet. Tap 'Add Route / Vehicle' above."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(displayedSummaries, key = { it.route.id }) { summary ->
                TransportRouteCard(
                    summary = summary,
                    canEdit = canEdit,
                    isHindi = isHindi,
                    onCallDriver = { phone ->
                        if (phone.isNotBlank()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                            }
                        }
                    },
                    onEdit = { editingRoute = summary.route; showRouteDialog = true },
                    onToggleActive = {
                        viewModel.toggleTransportRouteActive(summary.route, !summary.route.active)
                    },
                    onDeleteRequest = {
                        val check = TransportEngine.checkRouteDeletion(summary.route, schoolStudents)
                        deleteDialogTarget = summary.route to check
                    },
                    onApplyFeesToStudents = { applyFeeTargetRoute = summary.route },
                    onAssignStudents = { onJumpToAssignRouteStudents(summary.route.id) },
                    onPrintRouteList = { viewModel.generateTransportRouteListPdf(summary) },
                    onPrintDriverList = { viewModel.generateTransportDriverListPdf(summary) }
                )
            }
        }
    }

    if (showRouteDialog) {
        TransportRouteEditorDialog(
            existing = editingRoute,
            isHindi = isHindi,
            onDismiss = { showRouteDialog = false },
            onSave = { name, vehNo, vehType, cap, driver, phone, fee, stops, pickupFee, dropFee, ownership, contractor, contractorPhone, rent, licNo, licExp, condName, condPhone, setError ->
                viewModel.saveTransportRouteRecord(
                    existing = editingRoute,
                    routeName = name,
                    vehicleNumber = vehNo,
                    vehicleType = vehType,
                    seatCapacity = cap,
                    driverName = driver,
                    driverPhone = phone,
                    monthlyFeeRs = fee,
                    stops = stops,
                    pickupOnlyFeeRs = pickupFee,
                    dropOnlyFeeRs = dropFee,
                    vehicleOwnership = ownership,
                    ownerContractorName = contractor,
                    ownerContractorPhone = contractorPhone,
                    monthlyRentRs = rent,
                    driverLicenceNo = licNo,
                    driverLicenceExpiryIso = licExp,
                    conductorName = condName,
                    conductorPhone = condPhone,
                    active = editingRoute?.active ?: true
                ) { ok, err ->
                    if (ok) showRouteDialog = false else setError(err ?: "Unable to save route")
                }
            }
        )
    }

    deleteDialogTarget?.let { (route, check) ->
        BlockedOrConfirmDeleteRouteDialog(
            route = route,
            checkResult = check,
            alternativeRoutes = allRoutesForSchool.filter { it.id != route.id && it.active },
            isHindi = isHindi,
            onDismiss = { deleteDialogTarget = null },
            onDeactivate = {
                viewModel.toggleTransportRouteActive(route, false) { ok, _ ->
                    if (ok) deleteDialogTarget = null
                }
            },
            onMoveAllStudents = { targetRoute, preserveOverrides, setErr ->
                viewModel.moveAllStudentsFromRoute(
                    sourceRoute = route,
                    targetRoute = targetRoute,
                    preserveOverriddenFees = preserveOverrides
                ) { ok, err ->
                    if (ok) deleteDialogTarget = null else setErr(err ?: "Move failed")
                }
            },
            onConfirmDeleteEmpty = {
                viewModel.deleteTransportRouteRecord(route) { ok, _ ->
                    if (ok) deleteDialogTarget = null
                }
            }
        )
    }

    applyFeeTargetRoute?.let { rt ->
        ApplyRouteFeePreviewDialog(
            route = rt,
            schoolStudents = schoolStudents,
            structure = structure,
            isHindi = isHindi,
            onDismiss = { applyFeeTargetRoute = null },
            onConfirmApply = { rows, effMonth, setErr ->
                viewModel.applyRouteFeeUpdateToStudents(
                    updatedRoute = rt,
                    previewRows = rows,
                    effectiveFromMonth = effMonth,
                    orderedSessionMonths = structure.monthOrder
                ) { ok, err ->
                    if (ok) applyFeeTargetRoute = null else setErr(err ?: "Update failed")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransportRouteCard(
    summary: TransportRouteSummary,
    canEdit: Boolean,
    isHindi: Boolean,
    onCallDriver: (String) -> Unit,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit,
    onDeleteRequest: () -> Unit,
    onApplyFeesToStudents: () -> Unit,
    onAssignStudents: () -> Unit,
    onPrintRouteList: () -> Unit,
    onPrintDriverList: () -> Unit
) {
    val route = summary.route
    var showStopsTable by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            when {
                !route.active -> MaterialTheme.colorScheme.outlineVariant
                summary.isOverCapacity -> FailRed.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (route.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.testTag("transport_route_card_${route.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DirectionsBus,
                    contentDescription = null,
                    tint = if (route.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = route.routeName + if (!route.active) " (Inactive)" else "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${route.vehicleType} ${route.vehicleNumber.ifBlank { "-" }} • ${route.vehicleOwnership.label(isHindi)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = if (summary.isOverCapacity) FailRedContainer else PassGreenContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${summary.seatsUsed}/${route.seatCapacity}",
                        color = if (summary.isOverCapacity) FailRed else PassGreen,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (summary.isOverCapacity) {
                Text(
                    text = p10Label(isHindi, "⚠ सीट क्षमता से अधिक छात्र हैं", "⚠ Over capacity (${summary.seatsUsed}/${route.seatCapacity})"),
                    style = MaterialTheme.typography.labelSmall,
                    color = FailRed,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = buildString {
                    append("${p10Label(isHindi, "ड्राइवर", "Driver")}: ${route.driverName.ifBlank { "-" }}")
                    if (route.driverPhone.isNotBlank()) append(" (${route.driverPhone})")
                    if (route.conductorName.isNotBlank()) append(" • Cond: ${route.conductorName}")
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (route.vehicleOwnership == VehicleOwnershipType.RENTED && (route.ownerContractorName.isNotBlank() || route.monthlyRentRs > 0)) {
                Text(
                    text = "Contractor: ${route.ownerContractorName.ifBlank { "-" }} • Rent: ₹${route.monthlyRentRs}/mo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val pickupDisp = if (route.pickupOnlyFeeRs > 0) "₹${route.pickupOnlyFeeRs}" else "₹${route.monthlyFeeRs}"
            val dropDisp = if (route.dropOnlyFeeRs > 0) "₹${route.dropOnlyFeeRs}" else "₹${route.monthlyFeeRs}"
            Text(
                text = "Both ₹${route.monthlyFeeRs} • Pick $pickupDisp • Drop $dropDisp • ${p10Label(isHindi, "अनुमानित आय", "Expected")}: ₹${summary.expectedMonthlyIncomeRs}/mo",
                style = MaterialTheme.typography.bodySmall,
                color = PassGreen,
                fontWeight = FontWeight.SemiBold
            )

            if (route.stops.isNotEmpty()) {
                TextButton(
                    onClick = { showStopsTable = !showStopsTable },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        if (showStopsTable) p10Label(isHindi, "स्टॉप तालिका छुपाएँ (${route.stops.size})", "Hide Stop Table (${route.stops.size})")
                        else p10Label(isHindi, "स्टॉप व शुल्क तालिका देखें (${route.stops.size})", "View Stop Fee Table (${route.stops.size})")
                    )
                }
                if (showStopsTable) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            route.stops.forEachIndexed { i, st ->
                                val both = if (st.monthlyFeeRs > 0) st.monthlyFeeRs else route.monthlyFeeRs
                                val pick = st.feeForTripType(com.example.domain.TransportTripType.PICKUP_ONLY, route)
                                val drop = st.feeForTripType(com.example.domain.TransportTripType.DROP_ONLY, route)
                                Text(
                                    text = "${i + 1}. ${st.stopName}" +
                                        (if (st.pickupTime.isNotBlank()) " (${st.pickupTime})" else "") +
                                        " — Both ₹$both • Pick ₹$pick • Drop ₹$drop",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Responsive action buttons (all >= 48dp, FlowRow wraps cleanly at 320dp + 1.5x font)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (route.driverPhone.isNotBlank()) {
                    OutlinedButton(
                        onClick = { onCallDriver(route.driverPhone) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = PassGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "कॉल", "Call"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(
                    onClick = onPrintRouteList,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("transport_print_route_pdf_${route.id}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(p10Label(isHindi, "रूट PDF", "Route PDF"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onPrintDriverList,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("transport_print_driver_pdf_${route.id}")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(p10Label(isHindi, "ड्राइवर PDF", "Driver PDF"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onAssignStudents,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(p10Label(isHindi, "छात्र (${summary.seatsUsed})", "Students (${summary.seatsUsed})"), maxLines = 1)
                }
                if (canEdit) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("route_edit_btn_${route.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "बदलें", "Edit"), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onApplyFeesToStudents,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("route_apply_fee_btn_${route.id}")
                    ) {
                        Icon(Icons.Default.PriceChange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "शुल्क लागू करें", "Apply Fees"), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onToggleActive,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            if (route.active) Icons.Default.Block else Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (route.active) p10Label(isHindi, "बंद करें", "Deactivate")
                            else p10Label(isHindi, "चालू करें", "Activate"),
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = onDeleteRequest,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FailRed),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("route_delete_btn_${route.id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(p10Label(isHindi, "हटाएँ", "Delete"), maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockedOrConfirmDeleteRouteDialog(
    route: TransportRouteRecord,
    checkResult: TransportDeleteCheckResult,
    alternativeRoutes: List<TransportRouteRecord>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onDeactivate: () -> Unit,
    onMoveAllStudents: (TransportRouteRecord, Boolean, (String) -> Unit) -> Unit,
    onConfirmDeleteEmpty: () -> Unit
) {
    var selectedTargetId by remember { mutableStateOf(alternativeRoutes.firstOrNull()?.id.orEmpty()) }
    var preserveOverrides by remember { mutableStateOf(true) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (checkResult.canDeleteDirectly) {
                    p10Label(isHindi, "रूट हटाने की पुष्टि", "Delete Route?")
                } else {
                    p10Label(isHindi, "रूट हटाना अवरुद्ध", "Cannot Delete Route Directly")
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (checkResult.canDeleteDirectly) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "'${route.routeName}' पर कोई छात्र नहीं है। क्या आप इसे स्थायी रूप से हटाना चाहते हैं?",
                            "'${route.routeName}' has 0 assigned students. Delete this route permanently?"
                        )
                    )
                } else {
                    Surface(
                        color = FailRedContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = FailRed)
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "इस रूट पर ${checkResult.assignedCount} छात्र हैं। पहले सभी छात्रों को दूसरे रूट पर भेजें या रूट को बंद (Deactivate) करें।",
                                    "${checkResult.assignedCount} student(s) are on '${route.routeName}'. Move all students to another route or deactivate the route instead."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = FailRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (alternativeRoutes.isNotEmpty()) {
                        Text(
                            text = p10Label(isHindi, "सभी छात्रों को इस रूट पर भेजें:", "Move all ${checkResult.assignedCount} students to:"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            alternativeRoutes.forEach { alt ->
                                FilterChip(
                                    selected = selectedTargetId == alt.id,
                                    onClick = { selectedTargetId = alt.id },
                                    label = { Text(alt.routeName) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = preserveOverrides, onCheckedChange = { preserveOverrides = it })
                            Text(
                                text = p10Label(isHindi, "कस्टम शुल्क (Overridden) सुरक्षित रखें", "Keep individually overridden fees"),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                val tgt = alternativeRoutes.firstOrNull { it.id == selectedTargetId }
                                if (tgt == null) {
                                    errMsg = "Please choose a destination route."
                                } else {
                                    onMoveAllStudents(tgt, preserveOverrides) { errMsg = it }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("blocked_delete_move_all_button")
                        ) {
                            Text(p10Label(isHindi, "सभी ${checkResult.assignedCount} छात्र भेजें", "Move All ${checkResult.assignedCount} Students"))
                        }
                    } else {
                        Text(
                            text = p10Label(isHindi, "कोई दूसरा सक्रिय रूट उपलब्ध नहीं है।", "No other active route available to move students into."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onDeactivate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("blocked_delete_deactivate_button")
                    ) {
                        Text(p10Label(isHindi, "रूट बंद करें (Deactivate Route)", "Deactivate Route Instead"))
                    }
                }
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (checkResult.canDeleteDirectly) {
                Button(
                    onClick = onConfirmDeleteEmpty,
                    colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(isHindi, "हटाएँ", "Delete Route"))
                }
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
private fun ApplyRouteFeePreviewDialog(
    route: TransportRouteRecord,
    schoolStudents: List<StudentEntity>,
    structure: FeeStructureConfig,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirmApply: (List<com.example.domain.TransportFeeApplyPreviewRow>, String, (String) -> Unit) -> Unit
) {
    val (previewRows, excludedOverridden) = remember(route, schoolStudents) {
        TransportEngine.buildApplyRouteFeePreview(schoolStudents, route)
    }
    var effectiveFromMonth by remember(structure.monthOrder) {
        mutableStateOf(structure.monthOrder.firstOrNull() ?: "APR")
    }
    var errMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = p10Label(isHindi, "नया रूट शुल्क लागू करें: ${route.routeName}", "Apply Fees: ${route.routeName}"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (excludedOverridden > 0) {
                    Surface(color = AbsentAmberContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "$excludedOverridden ओवरराइड किए गए छात्र बाहर रखे गए हैं (उनका शुल्क नहीं बदलेगा)।",
                                "$excludedOverridden overridden student(s) excluded automatically."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Text(
                    text = p10Label(
                        isHindi,
                        "किस महीने से नया शुल्क लागू हो? (पिछले व भुगतान किए जा चुके महीने सुरक्षित रहेंगे)",
                        "Effective from month (already paid & prior months remain unchanged):"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    structure.monthOrder.forEach { mCode ->
                        FilterChip(
                            selected = effectiveFromMonth.equals(mCode, ignoreCase = true),
                            onClick = { effectiveFromMonth = mCode },
                            label = { Text(mCode) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                HorizontalDivider()
                val changedRows = previewRows.filter { it.hasChange }
                Text(
                    text = "${p10Label(isHindi, "पूर्वावलोकन", "Preview")}: ${changedRows.size} / ${previewRows.size} ${p10Label(isHindi, "छात्रों में बदलाव", "will update")}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                if (previewRows.isEmpty()) {
                    Text(p10Label(isHindi, "इस रूट पर कोई गैर-ओवरराइड छात्र नहीं है।", "No non-overridden students on this route."))
                } else {
                    previewRows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${row.student.name} (${row.student.className})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("${row.stopDisplayName} • ${row.tripType.label(isHindi)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "₹${row.oldMonthlyAmountRs} → ₹${row.newMonthlyAmountRs}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (row.hasChange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                }
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            val changedCount = previewRows.count { it.hasChange }
            Button(
                onClick = { onConfirmApply(previewRows, effectiveFromMonth) { errMsg = it } },
                enabled = changedCount > 0,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("apply_fee_preview_confirm_button")
            ) {
                Text(p10Label(isHindi, "लागू करें ($changedCount)", "Apply ($changedCount)"))
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
internal fun LegacyTransportMigrationDialog(
    items: List<TransportMigrationItem>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirmMigrate: ((String) -> Unit) -> Unit
) {
    var errMsg by remember { mutableStateOf<String?>(null) }
    val firstMonth = items.firstOrNull()?.assignedStartMonth ?: "APR"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                p10Label(isHindi, "पुराने छात्रों का शुरुआती महीना सेट करें", "Migrate Start Month (${items.size})"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = p10Label(
                        isHindi,
                        "इन छात्रों के ट्रांसपोर्ट खाते में शुरुआती महीना खाली है। सभी को सत्र के पहले ट्रांसपोर्ट महीने ($firstMonth) पर सेट करने से कोई पुरानी रसीद या बकाया नहीं बदलेगा।",
                        "These students have no start month saved. Setting $firstMonth keeps all existing paid cells and dues identical."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${item.student.name} (${item.student.className}-${item.student.section})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(item.currentRouteName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("→ ${item.assignedStartMonth}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = PassGreen)
                    }
                    HorizontalDivider()
                }
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmMigrate { errMsg = it } },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_legacy_migration_button")
            ) {
                Text(p10Label(isHindi, "पुष्टि करें (${items.size})", "Migrate (${items.size})"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(p10Label(isHindi, "बाद में", "Later"))
            }
        }
    )
}
