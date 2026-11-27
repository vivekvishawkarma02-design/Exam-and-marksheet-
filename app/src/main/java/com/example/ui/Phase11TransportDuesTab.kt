package com.example.ui

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.StudentEntity
import com.example.domain.FeeReceiptRecord
import com.example.domain.FeeStructureConfig
import com.example.domain.StudentFeeOverride
import com.example.domain.TransportEngine
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportSchoolSummary
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.PassGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransportDuesTab(
    isHindi: Boolean,
    canViewFees: Boolean,
    allRoutesForSchool: List<TransportRouteRecord>,
    studentsWithDocIds: List<Pair<String, StudentEntity>>,
    schoolSummary: TransportSchoolSummary,
    classNames: List<String>,
    structure: FeeStructureConfig,
    overridesByStudentDocId: Map<String, StudentFeeOverride>,
    receipts: List<FeeReceiptRecord>,
    viewModel: SchoolViewModel,
    onJumpToNeedsAttentionStudents: () -> Unit,
    onOpenSendQueueForTransportDues: (monthCode: String, routeIdFilter: String?) -> Unit
) {
    val defaultMonth = remember(structure.monthOrder) {
        val cur = currentSessionMonthCode()
        if (structure.monthOrder.any { it.equals(cur, ignoreCase = true) }) cur
        else structure.monthOrder.firstOrNull() ?: "APR"
    }
    var selectedMonth by remember(defaultMonth) { mutableStateOf(defaultMonth) }
    var selectedRouteId by remember { mutableStateOf<String?>(null) }
    var selectedClass by remember { mutableStateOf<String?>(null) }

    val duesSummary = remember(
        selectedMonth,
        studentsWithDocIds,
        allRoutesForSchool,
        structure,
        overridesByStudentDocId,
        receipts,
        selectedRouteId,
        selectedClass
    ) {
        TransportEngine.computeTransportDuesForMonth(
            monthCode = selectedMonth,
            studentsWithDocIds = studentsWithDocIds,
            routes = allRoutesForSchool,
            structure = structure,
            overridesByStudentDocId = overridesByStudentDocId,
            receipts = receipts,
            routeIdFilter = selectedRouteId,
            classNameFilter = selectedClass
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("transport_dues_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p10Label(isHindi, "महीना चुनें:", "Select Month:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    structure.monthOrder.forEach { mCode ->
                        FilterChip(
                            selected = selectedMonth.equals(mCode, ignoreCase = true),
                            onClick = { selectedMonth = mCode },
                            label = { Text(mCode) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedRouteId == null,
                        onClick = { selectedRouteId = null },
                        label = { Text(p10Label(isHindi, "सभी रूट", "All Routes")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = selectedRouteId == "__NO_ROUTE__",
                        onClick = { selectedRouteId = if (selectedRouteId == "__NO_ROUTE__") null else "__NO_ROUTE__" },
                        label = { Text(p10Label(isHindi, "बिना रूट", "No Route")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    allRoutesForSchool.forEach { rt ->
                        FilterChip(
                            selected = selectedRouteId == rt.id,
                            onClick = { selectedRouteId = if (selectedRouteId == rt.id) null else rt.id },
                            label = { Text(rt.routeName) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedClass == null,
                        onClick = { selectedClass = null },
                        label = { Text(p10Label(isHindi, "सभी कक्षा", "All Classes")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    classNames.forEach { cls ->
                        FilterChip(
                            selected = selectedClass.equals(cls, ignoreCase = true),
                            onClick = { selectedClass = if (selectedClass.equals(cls, ignoreCase = true)) null else cls },
                            label = { Text(cls) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }

        // Totals Card & Actions
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = p10Label(isHindi, "${duesSummary.monthCode} बकाया सारांश", "${duesSummary.monthCode} Transport Dues"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(p10Label(isHindi, "अनुमानित", "Expected"), style = MaterialTheme.typography.labelSmall)
                            Text("₹${duesSummary.totalExpectedRs}", fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text(p10Label(isHindi, "जमा", "Collected"), style = MaterialTheme.typography.labelSmall)
                            Text("₹${duesSummary.totalCollectedRs}", fontWeight = FontWeight.Bold, color = PassGreen)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(p10Label(isHindi, "बकाया (${duesSummary.totalStudentsDue})", "Due (${duesSummary.totalStudentsDue})"), style = MaterialTheme.typography.labelSmall)
                            Text("₹${duesSummary.totalDueRs}", fontWeight = FontWeight.Bold, color = FailRed)
                        }
                    }

                    if (duesSummary.dueByRouteName.isNotEmpty()) {
                        HorizontalDivider()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            duesSummary.dueByRouteName.forEach { (rName, amt) ->
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                                    Text(
                                        text = "$rName: ₹$amt",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.generateTransportDuesReportPdf(
                                    duesSummary = duesSummary,
                                    unassignedStudents = schoolSummary.unassignedTransportStudents,
                                    routes = allRoutesForSchool
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("transport_dues_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(p10Label(isHindi, "बकाया व शुल्क PDF", "Dues & Fee Schedule PDF"))
                        }
                        Button(
                            onClick = {
                                if (canViewFees) {
                                    onOpenSendQueueForTransportDues(duesSummary.monthCode, selectedRouteId)
                                } else {
                                    viewModel.emitMessage(
                                        p10Label(
                                            isHindi,
                                            "WhatsApp कतार खोलने के लिए 'Fees' मॉड्यूल की अनुमति आवश्यक है। आप ऊपर से PDF साझा कर सकते हैं।",
                                            "Fees module access is needed to open the WhatsApp queue. You can export the Dues PDF instead."
                                        )
                                    )
                                }
                            },
                            enabled = duesSummary.rows.isNotEmpty(),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("transport_send_whatsapp_queue_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(p10Label(isHindi, "WhatsApp कतार में भेजें", "Send to WhatsApp Queue"))
                        }
                    }
                }
            }
        }

        if (schoolSummary.unassignedTransportStudents.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AbsentAmberContainer,
                    border = BorderStroke(1.dp, AbsentAmber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AbsentAmber, modifier = Modifier.size(18.dp))
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "${schoolSummary.unassignedTransportStudents.size} ट्रांसपोर्ट छात्रों का रूट तय नहीं है।",
                                    "${schoolSummary.unassignedTransportStudents.size} transport student(s) have no route assigned."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = onJumpToNeedsAttentionStudents,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(p10Label(isHindi, "छात्र टैब में ठीक करें", "Fix in Students Tab"))
                        }
                    }
                }
            }
        }

        if (duesSummary.rows.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = p10Label(
                            isHindi,
                            "${duesSummary.monthCode} के लिए कोई बकाया नहीं है।",
                            "No pending transport dues for ${duesSummary.monthCode}."
                        ),
                        modifier = Modifier.padding(16.dp),
                        color = PassGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            items(duesSummary.rows, key = { "${it.studentDocId}_${it.monthCode}" }) { row ->
                Card(shape = RoundedCornerShape(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${row.student.name} (${row.student.className}-${row.student.section})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${row.routeName} • ${row.stopName} • ${row.tripType.label(isHindi)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Mob: ${row.student.mobile.ifBlank { row.student.secondMobile.ifBlank { "-" } }} • Fee ₹${row.scheduledRs} / Paid ₹${row.paidRs}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "₹${row.dueRs}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FailRed
                        )
                    }
                }
            }
        }
    }
}
