package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportStop
import com.example.domain.VehicleOwnershipType
import com.example.ui.theme.FailRed
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransportRouteEditorDialog(
    existing: TransportRouteRecord?,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        routeName: String,
        vehicleNumber: String,
        vehicleType: String,
        seatCapacity: Int,
        driverName: String,
        driverPhone: String,
        monthlyFeeRs: Int,
        stops: List<TransportStop>,
        pickupOnlyFeeRs: Int,
        dropOnlyFeeRs: Int,
        vehicleOwnership: VehicleOwnershipType,
        ownerContractorName: String,
        ownerContractorPhone: String,
        monthlyRentRs: Int,
        driverLicenceNo: String,
        driverLicenceExpiryIso: String,
        conductorName: String,
        conductorPhone: String,
        setError: (String) -> Unit
    ) -> Unit
) {
    var routeName by remember { mutableStateOf(existing?.routeName.orEmpty()) }
    var vehicleType by remember { mutableStateOf(existing?.vehicleType ?: "Bus") }
    var vehicleNumber by remember { mutableStateOf(existing?.vehicleNumber.orEmpty()) }
    var seatCapacity by remember { mutableStateOf((existing?.seatCapacity ?: 30).toString()) }

    var vehicleOwnership by remember { mutableStateOf(existing?.vehicleOwnership ?: VehicleOwnershipType.OWN) }
    var contractorName by remember { mutableStateOf(existing?.ownerContractorName.orEmpty()) }
    var contractorPhone by remember { mutableStateOf(existing?.ownerContractorPhone.orEmpty()) }
    var monthlyRentText by remember {
        mutableStateOf(if ((existing?.monthlyRentRs ?: 0) > 0) existing?.monthlyRentRs.toString() else "")
    }

    var driverName by remember { mutableStateOf(existing?.driverName.orEmpty()) }
    var driverPhone by remember { mutableStateOf(existing?.driverPhone.orEmpty()) }
    var driverLicenceNo by remember { mutableStateOf(existing?.driverLicenceNo.orEmpty()) }
    var driverLicenceExpiry by remember { mutableStateOf(existing?.driverLicenceExpiryIso.orEmpty()) }

    var conductorName by remember { mutableStateOf(existing?.conductorName.orEmpty()) }
    var conductorPhone by remember { mutableStateOf(existing?.conductorPhone.orEmpty()) }

    var monthlyFee by remember { mutableStateOf((existing?.monthlyFeeRs ?: 600).toString()) }
    var pickupFee by remember {
        mutableStateOf(if ((existing?.pickupOnlyFeeRs ?: 0) > 0) existing?.pickupOnlyFeeRs.toString() else "")
    }
    var dropFee by remember {
        mutableStateOf(if ((existing?.dropOnlyFeeRs ?: 0) > 0) existing?.dropOnlyFeeRs.toString() else "")
    }

    val stops = remember {
        mutableStateListOf<TransportStop>().apply {
            if (existing != null) addAll(existing.stops)
        }
    }
    var newStopName by remember { mutableStateOf("") }
    var newStopTime by remember { mutableStateOf("") }
    var newStopBothFee by remember { mutableStateOf("") }
    var newStopPickupFee by remember { mutableStateOf("") }
    var newStopDropFee by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) {
                    p10Label(isHindi, "नया रूट / वाहन जोड़ें", "Add Route / Vehicle")
                } else {
                    p10Label(isHindi, "रूट व वाहन संपादित करें", "Edit Route / Vehicle")
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = FailRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it; errorText = null },
                    label = { Text(p10Label(isHindi, "रूट का नाम *", "Route Name *")) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("route_dialog_name_input")
                )

                Text(
                    text = p10Label(isHindi, "वाहन प्रकार व विवरण", "Vehicle Type & Details"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Bus", "Van", "Auto").forEach { vType ->
                        FilterChip(
                            selected = vehicleType.equals(vType, ignoreCase = true),
                            onClick = { vehicleType = vType },
                            label = { Text(vType) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vehicleNumber,
                        onValueChange = { vehicleNumber = it.uppercase() },
                        label = { Text(p10Label(isHindi, "गाड़ी नंबर", "Vehicle No")) },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = seatCapacity,
                        onValueChange = { seatCapacity = it.filter(Char::isDigit) },
                        label = { Text(p10Label(isHindi, "सीटें", "Seats")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Text(
                    text = p10Label(isHindi, "वाहन स्वामित्व (Ownership)", "Vehicle Ownership"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VehicleOwnershipType.entries.forEach { own ->
                        FilterChip(
                            selected = vehicleOwnership == own,
                            onClick = { vehicleOwnership = own },
                            label = { Text(own.label(isHindi)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                if (vehicleOwnership == VehicleOwnershipType.RENTED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = contractorName,
                            onValueChange = { contractorName = it },
                            label = { Text(p10Label(isHindi, "मालिक / ठेकेदार", "Owner / Contractor")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = contractorPhone,
                            onValueChange = { contractorPhone = it },
                            label = { Text(p10Label(isHindi, "फोन", "Phone")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = monthlyRentText,
                        onValueChange = { monthlyRentText = it.filter(Char::isDigit) },
                        label = { Text(p10Label(isHindi, "मासिक किराया ₹", "Monthly Rent ₹")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()
                Text(
                    text = p10Label(isHindi, "ड्राइवर और कंडक्टर", "Driver & Conductor"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = driverLicenceNo,
                        onValueChange = { driverLicenceNo = it.uppercase() },
                        label = { Text(p10Label(isHindi, "लाइसेंस नं.", "Licence No")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = driverLicenceExpiry,
                        onValueChange = { driverLicenceExpiry = it },
                        label = { Text(p10Label(isHindi, "लाइसेंस समाप्ति", "Lic. Expiry")) },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = conductorName,
                        onValueChange = { conductorName = it },
                        label = { Text(p10Label(isHindi, "कंडक्टर (वैकल्पिक)", "Conductor (opt)")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = conductorPhone,
                        onValueChange = { conductorPhone = it },
                        label = { Text(p10Label(isHindi, "कंडक्टर फोन", "Phone")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider()
                Text(
                    text = p10Label(isHindi, "रूट शुल्क (मासिक ₹)", "Route Monthly Fee (₹)"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = p10Label(
                        isHindi,
                        "केवल पिकअप / केवल ड्रॉप खाली छोड़ने पर आना-जाना शुल्क ही लागू होगा।",
                        "Pickup-only / Drop-only default to Both-ways fee if left blank."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = monthlyFee,
                    onValueChange = { monthlyFee = it.filter(Char::isDigit) },
                    label = { Text(p10Label(isHindi, "आना-जाना शुल्क ₹ (Both ways) *", "Both Ways Fee ₹ *")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("route_dialog_both_fee_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pickupFee,
                        onValueChange = { pickupFee = it.filter(Char::isDigit) },
                        label = { Text(p10Label(isHindi, "पिकअप ₹", "Pickup ₹")) },
                        placeholder = { Text(monthlyFee.ifBlank { "Same" }) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("route_dialog_pickup_fee_input")
                    )
                    OutlinedTextField(
                        value = dropFee,
                        onValueChange = { dropFee = it.filter(Char::isDigit) },
                        label = { Text(p10Label(isHindi, "ड्रॉप ₹", "Drop ₹")) },
                        placeholder = { Text(monthlyFee.ifBlank { "Same" }) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("route_dialog_drop_fee_input")
                    )
                }

                HorizontalDivider()
                Text(
                    text = p10Label(isHindi, "स्टॉप तालिका (क्रम से, वैकल्पिक स्टॉप शुल्क)", "Stops Table (ordered, optional fees)"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                stops.forEachIndexed { idx, st ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${idx + 1}. ${st.stopName}" +
                                        if (st.pickupTime.isNotBlank()) " (${st.pickupTime})" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                val feeSummary = buildString {
                                    append(if (st.monthlyFeeRs > 0) "Both ₹${st.monthlyFeeRs}" else "Route fee")
                                    if (st.pickupOnlyFeeRs > 0) append(" • Pick ₹${st.pickupOnlyFeeRs}")
                                    if (st.dropOnlyFeeRs > 0) append(" • Drop ₹${st.dropOnlyFeeRs}")
                                }
                                Text(
                                    text = feeSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (idx > 0) {
                                IconButton(
                                    onClick = {
                                        val item = stops.removeAt(idx)
                                        stops.add(idx - 1, item)
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                                }
                            }
                            if (idx < stops.lastIndex) {
                                IconButton(
                                    onClick = {
                                        val item = stops.removeAt(idx)
                                        stops.add(idx + 1, item)
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(
                                onClick = { stops.removeAt(idx) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove stop", tint = FailRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Add stop composer
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = newStopName,
                                onValueChange = { newStopName = it },
                                label = { Text(p10Label(isHindi, "स्टॉप नाम", "Stop Name")) },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = newStopTime,
                                onValueChange = { newStopTime = it },
                                label = { Text(p10Label(isHindi, "समय", "Time")) },
                                placeholder = { Text("07:30") },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = newStopBothFee,
                                onValueChange = { newStopBothFee = it.filter(Char::isDigit) },
                                label = { Text("Both ₹") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = newStopPickupFee,
                                onValueChange = { newStopPickupFee = it.filter(Char::isDigit) },
                                label = { Text("Pick ₹") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = newStopDropFee,
                                onValueChange = { newStopDropFee = it.filter(Char::isDigit) },
                                label = { Text("Drop ₹") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val cleanName = newStopName.trim()
                                if (cleanName.isNotEmpty()) {
                                    stops.add(
                                        TransportStop(
                                            stopId = "stop_${UUID.randomUUID().toString().replace("-", "").take(6)}",
                                            stopName = cleanName,
                                            pickupTime = newStopTime.trim(),
                                            monthlyFeeRs = newStopBothFee.toIntOrNull() ?: 0,
                                            pickupOnlyFeeRs = newStopPickupFee.toIntOrNull() ?: 0,
                                            dropOnlyFeeRs = newStopDropFee.toIntOrNull() ?: 0
                                        )
                                    )
                                    newStopName = ""
                                    newStopTime = ""
                                    newStopBothFee = ""
                                    newStopPickupFee = ""
                                    newStopDropFee = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(p10Label(isHindi, "स्टॉप जोड़ें", "Add Stop to Table"))
                        }
                    }
                }
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
                        stops.toList(),
                        pickupFee.toIntOrNull() ?: 0,
                        dropFee.toIntOrNull() ?: 0,
                        vehicleOwnership,
                        contractorName,
                        contractorPhone,
                        monthlyRentText.toIntOrNull() ?: 0,
                        driverLicenceNo,
                        driverLicenceExpiry,
                        conductorName,
                        conductorPhone
                    ) { errorText = it }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("route_dialog_save_button")
            ) {
                Text(p10Label(isHindi, "सेव करें", "Save"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "रद्द", "Cancel"))
            }
        }
    )
}
