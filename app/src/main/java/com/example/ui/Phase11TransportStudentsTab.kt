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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.StudentEntity
import com.example.domain.FeeStructureConfig
import com.example.domain.SessionMonthCode
import com.example.domain.TransportEngine
import com.example.domain.TransportResolvedDisplay
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportSchoolSummary
import com.example.domain.TransportStop
import com.example.domain.TransportTripType
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer

private enum class TransportStudentsFilterMode(val labelEn: String, val labelHi: String) {
    ALL_TRANSPORT("On Transport", "ट्रांसपोर्ट छात्र"),
    NEEDS_ATTENTION("Needs Attention", "सुधार आवश्यक"),
    ALL_STUDENTS("All School Students", "सभी छात्र");

    fun label(isHindi: Boolean): String = if (isHindi) labelHi else labelEn
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransportStudentsTab(
    isHindi: Boolean,
    canAssignStudents: Boolean,
    allRoutesForSchool: List<TransportRouteRecord>,
    schoolStudents: List<StudentEntity>,
    schoolSummary: TransportSchoolSummary,
    classNames: List<String>,
    structure: FeeStructureConfig,
    viewModel: SchoolViewModel,
    initialModeNeedsAttention: Boolean,
    initialPreselectedRouteId: String?,
    onConsumeStudentFilters: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(TransportStudentsFilterMode.ALL_TRANSPORT) }
    var routeFilterId by remember { mutableStateOf<String?>(null) } // null = All, "__NO_ROUTE__" = unassigned
    var classFilter by remember { mutableStateOf<String?>(null) }
    var sectionFilter by remember { mutableStateOf<String?>(null) }

    val selectedIds = remember { mutableStateListOf<Long>() }
    var showBulkAssignDialog by remember { mutableStateOf(false) }
    var bulkAssignTargetRouteDefault by remember { mutableStateOf<TransportRouteRecord?>(null) }
    var showBulkTripDialog by remember { mutableStateOf(false) }
    var showBulkMonthsDialog by remember { mutableStateOf(false) }
    var showBulkRemoveConfirm by remember { mutableStateOf(false) }
    var overrideTargetStudent by remember { mutableStateOf<Pair<StudentEntity, TransportResolvedDisplay>?>(null) }

    LaunchedEffect(initialModeNeedsAttention, initialPreselectedRouteId) {
        if (initialModeNeedsAttention) {
            filterMode = TransportStudentsFilterMode.NEEDS_ATTENTION
            routeFilterId = null
            onConsumeStudentFilters()
        } else if (!initialPreselectedRouteId.isNullOrBlank()) {
            filterMode = TransportStudentsFilterMode.ALL_STUDENTS
            routeFilterId = initialPreselectedRouteId
            onConsumeStudentFilters()
        }
    }

    val overCapacityRouteIds = remember(schoolSummary) {
        schoolSummary.routeSummaries.filter { it.isOverCapacity }.map { it.route.id }.toSet()
    }
    val firstChargedMonth = remember(structure) {
        TransportEngine.firstChargedTransportMonth(structure)
    }

    val resolvedStudents = remember(schoolStudents, allRoutesForSchool, overCapacityRouteIds, firstChargedMonth) {
        schoolStudents
            .sortedWith(compareBy({ it.className.lowercase() }, { it.section.lowercase() }, { it.rollNo }, { it.name.lowercase() }))
            .map { st ->
                st to TransportEngine.resolveDisplay(
                    student = st,
                    routes = allRoutesForSchool,
                    overCapacityRouteIds = overCapacityRouteIds,
                    firstChargedMonthFallback = firstChargedMonth
                )
            }
    }

    val sectionsForSelectedClass = remember(schoolStudents, classFilter) {
        if (classFilter.isNullOrBlank()) emptyList()
        else schoolStudents
            .filter { it.className.equals(classFilter, ignoreCase = true) }
            .map { it.section.uppercase().ifBlank { "A" } }
            .distinct()
            .sorted()
    }

    val filteredList = remember(resolvedStudents, searchQuery, filterMode, routeFilterId, classFilter, sectionFilter) {
        val q = searchQuery.trim().lowercase()
        resolvedStudents.filter { (st, disp) ->
            val modeOk = when (filterMode) {
                TransportStudentsFilterMode.ALL_TRANSPORT -> st.usesTransport
                TransportStudentsFilterMode.NEEDS_ATTENTION -> st.usesTransport && disp.needsAttention
                TransportStudentsFilterMode.ALL_STUDENTS -> true
            }
            val routeOk = when (routeFilterId) {
                null -> true
                "__NO_ROUTE__" -> st.usesTransport && disp.route == null
                else -> disp.route?.id == routeFilterId || st.transportRouteId == routeFilterId
            }
            val classOk = classFilter.isNullOrBlank() || st.className.equals(classFilter, ignoreCase = true)
            val secOk = sectionFilter.isNullOrBlank() || st.section.equals(sectionFilter, ignoreCase = true)
            val queryOk = q.isEmpty() ||
                st.name.lowercase().contains(q) ||
                st.admNo.lowercase().contains(q) ||
                st.mobile.lowercase().contains(q) ||
                disp.routeName.lowercase().contains(q) ||
                disp.stopName.lowercase().contains(q)
            modeOk && routeOk && classOk && secOk && queryOk
        }
    }

    val selectedStudents = remember(schoolStudents, selectedIds.toList()) {
        val set = selectedIds.toSet()
        schoolStudents.filter { it.id in set }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("transport_students_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(p10Label(isHindi, "नाम, प्रवेश क्र., मोबाइल, रूट या स्टॉप खोजें...", "Search student, adm no, mobile, route, stop..."))
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transport_students_search")
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TransportStudentsFilterMode.entries.forEach { m ->
                        FilterChip(
                            selected = filterMode == m,
                            onClick = { filterMode = m },
                            label = { Text(m.label(isHindi)) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("transport_filter_mode_${m.name.lowercase()}")
                        )
                    }
                }

                // Route Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = routeFilterId == null,
                        onClick = { routeFilterId = null },
                        label = { Text(p10Label(isHindi, "सभी रूट", "All Routes")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = routeFilterId == "__NO_ROUTE__",
                        onClick = { routeFilterId = if (routeFilterId == "__NO_ROUTE__") null else "__NO_ROUTE__" },
                        label = { Text(p10Label(isHindi, "बिना रूट (${schoolSummary.unassignedTransportStudents.size})", "No Route (${schoolSummary.unassignedTransportStudents.size})")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    allRoutesForSchool.forEach { rt ->
                        FilterChip(
                            selected = routeFilterId == rt.id,
                            onClick = { routeFilterId = if (routeFilterId == rt.id) null else rt.id },
                            label = { Text(rt.routeName) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                // Class & Section Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = classFilter == null,
                        onClick = { classFilter = null; sectionFilter = null },
                        label = { Text(p10Label(isHindi, "सभी कक्षा", "All Classes")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    classNames.forEach { cls ->
                        FilterChip(
                            selected = classFilter.equals(cls, ignoreCase = true),
                            onClick = {
                                classFilter = if (classFilter.equals(cls, ignoreCase = true)) null else cls
                                sectionFilter = null
                            },
                            label = { Text(cls) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                if (sectionsForSelectedClass.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = sectionFilter == null,
                            onClick = { sectionFilter = null },
                            label = { Text("All Sec") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        sectionsForSelectedClass.forEach { sec ->
                            FilterChip(
                                selected = sectionFilter.equals(sec, ignoreCase = true),
                                onClick = { sectionFilter = if (sectionFilter.equals(sec, ignoreCase = true)) null else sec },
                                label = { Text("Sec $sec") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }

                // Multi-Select & Bulk Action Bar (no 40-student cap)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "सूची में छात्र: ${filteredList.size} • चयनित: ${selectedStudents.size}",
                                "Showing ${filteredList.size} student(s) • Selected: ${selectedStudents.size}"
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (canAssignStudents) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val allFilteredIds = filteredList.map { it.first.id }
                                        selectedIds.clear()
                                        selectedIds.addAll(allFilteredIds)
                                    },
                                    enabled = filteredList.isNotEmpty(),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("transport_select_all_button")
                                ) {
                                    Text(p10Label(isHindi, "सभी चुनें (${filteredList.size})", "Select All (${filteredList.size})"))
                                }
                                if (selectedStudents.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { selectedIds.clear() },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(p10Label(isHindi, "चयन हटाएँ", "Clear"))
                                    }
                                    Button(
                                        onClick = {
                                            bulkAssignTargetRouteDefault = allRoutesForSchool.firstOrNull { it.active }
                                            showBulkAssignDialog = true
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("transport_bulk_assign_button")
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(p10Label(isHindi, "रूट/स्टॉप बदलें (${selectedStudents.size})", "Assign / Move (${selectedStudents.size})"))
                                    }
                                    OutlinedButton(
                                        onClick = { showBulkTripDialog = true },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(p10Label(isHindi, "यात्रा प्रकार", "Trip Type"))
                                    }
                                    OutlinedButton(
                                        onClick = { showBulkMonthsDialog = true },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(p10Label(isHindi, "महीने सेट करें", "Start/End Month"))
                                    }
                                    OutlinedButton(
                                        onClick = { showBulkRemoveConfirm = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FailRed),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(p10Label(isHindi, "ट्रांसपोर्ट हटाएँ", "Remove"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (filteredList.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = p10Label(isHindi, "इस फ़िल्टर में कोई छात्र नहीं मिला।", "No students match the selected filters."),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredList, key = { it.first.id }) { (student, display) ->
                val isSelected = student.id in selectedIds
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            display.needsAttention -> AbsentAmber
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.testTag("transport_student_card_${student.admNo}")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (canAssignStudents) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (student.id !in selectedIds) selectedIds.add(student.id)
                                        } else {
                                            selectedIds.remove(student.id)
                                        }
                                    },
                                    modifier = Modifier.testTag("transport_select_stu_${student.admNo}")
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${student.name} (${student.className}-${student.section} • Adm ${student.admNo})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (!student.usesTransport) {
                                        p10Label(isHindi, "ट्रांसपोर्ट बंद है", "Not on Transport")
                                    } else {
                                        buildString {
                                            append("${display.routeName}")
                                            if (display.stopName.isNotBlank()) append(" • ${display.stopName}")
                                            append(" • ${display.tripType.label(isHindi)}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (student.usesTransport) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (student.usesTransport) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "₹${display.effectiveMonthlyFeeRs}/mo",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (display.effectiveMonthlyFeeRs > 0) PassGreen else FailRed
                                    )
                                    val monthsText = if (student.transportStartMonth.isBlank()) {
                                        p10Label(isHindi, "महीना खाली", "No Start Mo")
                                    } else if (student.transportEndMonth.isNotBlank()) {
                                        "${student.transportStartMonth}–${student.transportEndMonth}"
                                    } else {
                                        "${p10Label(isHindi, "से", "From")} ${student.transportStartMonth}"
                                    }
                                    Text(
                                        text = monthsText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (student.transportFeeOverridden) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AbsentAmberContainer
                            ) {
                                Text(
                                    text = p10Label(
                                        isHindi,
                                        "ओवरराइड शुल्क (रूट दर ₹${display.computedRouteOrStopFeeRs}) • टिप्पणी: ${student.transportOverrideRemark.ifBlank { "-" }}",
                                        "Overridden (Route fee ₹${display.computedRouteOrStopFeeRs}) • ${student.transportOverrideRemark.ifBlank { "-" }}"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (display.attentionFlags.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                display.attentionFlags.forEach { fl ->
                                    Surface(shape = RoundedCornerShape(6.dp), color = FailRedContainer) {
                                        Text(
                                            text = "⚠ ${fl.label(isHindi)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = FailRed,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (canAssignStudents) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        selectedIds.clear()
                                        selectedIds.add(student.id)
                                        bulkAssignTargetRouteDefault = display.route ?: allRoutesForSchool.firstOrNull { it.active }
                                        showBulkAssignDialog = true
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.DirectionsBus, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (student.usesTransport) p10Label(isHindi, "रूट/स्टॉप बदलें", "Move / Route") else p10Label(isHindi, "रूट जोड़ें", "Assign Route"))
                                }
                                if (student.usesTransport) {
                                    OutlinedButton(
                                        onClick = { overrideTargetStudent = student to display },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(p10Label(isHindi, "शुल्क ओवरराइड", "Override ₹"))
                                    }
                                    if (student.transportFeeOverridden && display.route != null) {
                                        OutlinedButton(
                                            onClick = { viewModel.resetStudentTransportFeeToRoute(student) },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("transport_reset_fee_${student.admNo}")
                                        ) {
                                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(p10Label(isHindi, "रूट दर ₹${display.computedRouteOrStopFeeRs} रीसेट", "Reset to ₹${display.computedRouteOrStopFeeRs}"))
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.removeStudentFromTransportRoute(student) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FailRed),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(p10Label(isHindi, "हटाएँ", "Remove"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBulkAssignDialog && selectedStudents.isNotEmpty()) {
        BulkAssignMoveDialog(
            students = selectedStudents,
            routes = allRoutesForSchool,
            initialRoute = bulkAssignTargetRouteDefault,
            schoolStudents = schoolStudents,
            structure = structure,
            isHindi = isHindi,
            onDismiss = { showBulkAssignDialog = false },
            onConfirm = { route, stop, tripType, startMonth, endMonth, keepOverrides, setErr ->
                viewModel.bulkAssignOrMoveTransportStudents(
                    students = selectedStudents,
                    route = route,
                    stop = stop,
                    tripType = tripType,
                    startMonth = startMonth,
                    endMonth = endMonth,
                    keepCustomFeeOverrides = keepOverrides
                ) { ok, err ->
                    if (ok) {
                        showBulkAssignDialog = false
                        selectedIds.clear()
                    } else {
                        setErr(err ?: "Unable to assign route")
                    }
                }
            }
        )
    }

    if (showBulkTripDialog && selectedStudents.isNotEmpty()) {
        BulkTripTypeDialog(
            count = selectedStudents.size,
            isHindi = isHindi,
            onDismiss = { showBulkTripDialog = false },
            onConfirm = { trip, setErr ->
                viewModel.bulkChangeStudentTripType(selectedStudents, trip) { ok, err ->
                    if (ok) {
                        showBulkTripDialog = false
                        selectedIds.clear()
                    } else setErr(err ?: "Update failed")
                }
            }
        )
    }

    if (showBulkMonthsDialog && selectedStudents.isNotEmpty()) {
        BulkMonthsDialog(
            count = selectedStudents.size,
            orderedMonths = structure.monthOrder,
            isHindi = isHindi,
            onDismiss = { showBulkMonthsDialog = false },
            onConfirm = { startM, endM, setErr ->
                viewModel.bulkSetStudentTransportMonths(selectedStudents, startM, endM) { ok, err ->
                    if (ok) {
                        showBulkMonthsDialog = false
                        selectedIds.clear()
                    } else setErr(err ?: "Update failed")
                }
            }
        )
    }

    if (showBulkRemoveConfirm && selectedStudents.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBulkRemoveConfirm = false },
            title = { Text(p10Label(isHindi, "चयनित छात्रों से ट्रांसपोर्ट हटाएँ?", "Remove Transport (${selectedStudents.size})?")) },
            text = {
                Text(
                    p10Label(
                        isHindi,
                        "चयनित ${selectedStudents.size} छात्रों का ट्रांसपोर्ट बंद हो जाएगा। पहले कट चुकी रसीदें सुरक्षित रहेंगी।",
                        "Disable transport for ${selectedStudents.size} selected student(s)? Existing paid receipts remain intact."
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.bulkRemoveStudentsFromTransport(selectedStudents) { ok, _ ->
                            if (ok) {
                                showBulkRemoveConfirm = false
                                selectedIds.clear()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(p10Label(isHindi, "हटाएँ", "Remove"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkRemoveConfirm = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(p10Label(isHindi, "रद्द", "Cancel"))
                }
            }
        )
    }

    overrideTargetStudent?.let { (student, display) ->
        OverrideTransportFeeDialog(
            student = student,
            display = display,
            isHindi = isHindi,
            onDismiss = { overrideTargetStudent = null },
            onConfirmOverride = { newAmt, remark, setErr ->
                viewModel.overrideStudentTransportFee(student, newAmt, remark) { ok, err ->
                    if (ok) overrideTargetStudent = null else setErr(err ?: "Override failed")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkAssignMoveDialog(
    students: List<StudentEntity>,
    routes: List<TransportRouteRecord>,
    initialRoute: TransportRouteRecord?,
    schoolStudents: List<StudentEntity>,
    structure: FeeStructureConfig,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TransportRouteRecord, TransportStop?, TransportTripType, String, String, Boolean, (String) -> Unit) -> Unit
) {
    val selectableRoutes = remember(routes) { routes.filter { it.active }.ifEmpty { routes } }
    var selectedRouteId by remember { mutableStateOf(initialRoute?.id ?: selectableRoutes.firstOrNull()?.id.orEmpty()) }
    val pickedRoute = selectableRoutes.firstOrNull { it.id == selectedRouteId }
    var selectedStopId by remember(selectedRouteId) { mutableStateOf(pickedRoute?.stops?.firstOrNull()?.stopId.orEmpty()) }
    var tripType by remember {
        mutableStateOf(
            if (students.size == 1) TransportTripType.fromRaw(students.first().transportTripType)
            else TransportTripType.BOTH_WAYS
        )
    }
    val defaultMonth = remember(structure) { currentSessionMonthCode() }
    var startMonth by remember {
        mutableStateOf(
            if (students.size == 1 && students.first().transportStartMonth.isNotBlank()) {
                students.first().transportStartMonth
            } else defaultMonth
        )
    }
    var endMonth by remember {
        mutableStateOf(if (students.size == 1) students.first().transportEndMonth else "")
    }
    var keepCustomOverrides by remember { mutableStateOf(true) }
    var confirmOverCapacity by remember { mutableStateOf(false) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    val pickedStop = pickedRoute?.stops?.firstOrNull { it.stopId == selectedStopId }
    val selectedIdSet = remember(students) { students.map { it.id }.toSet() }
    val exceedsCapacity = remember(pickedRoute, schoolStudents, selectedIdSet) {
        pickedRoute != null && TransportEngine.willExceedCapacity(pickedRoute, schoolStudents, selectedIdSet)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                p10Label(isHindi, "रूट / स्टॉप असाइन (${students.size} छात्र)", "Assign / Move (${students.size})"),
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
                // Show current route(s) first so moving is explicit
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p10Label(isHindi, "वर्तमान स्थिति (Current Route):", "Current Assignment:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        students.take(5).forEach { st ->
                            val resolved = TransportEngine.resolveDisplay(st, routes)
                            Text(
                                text = "• ${st.name}: ${if (st.usesTransport) "${resolved.routeName} / ${resolved.stopName.ifBlank { "-" }}" else "Not on transport"}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (students.size > 5) {
                            Text("+ ${students.size - 5} more student(s)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (selectableRoutes.isEmpty()) {
                    Text(
                        p10Label(isHindi, "कोई रूट उपलब्ध नहीं है। पहले रूट्स टैब में रूट बनाएँ।", "No routes exist yet. Create a route in the Routes tab first."),
                        color = FailRed
                    )
                } else {
                    Text(p10Label(isHindi, "नया रूट चुनें:", "Destination Route:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        selectableRoutes.forEach { rt ->
                            FilterChip(
                                selected = selectedRouteId == rt.id,
                                onClick = { selectedRouteId = rt.id; confirmOverCapacity = false },
                                label = { Text("${rt.routeName} (₹${rt.monthlyFeeRs})") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    if (pickedRoute != null && pickedRoute.stops.isNotEmpty()) {
                        Text(p10Label(isHindi, "स्टॉप चुनें:", "Select Stop:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            pickedRoute.stops.forEach { st ->
                                val stFee = st.feeForTripType(tripType, pickedRoute)
                                FilterChip(
                                    selected = selectedStopId == st.stopId,
                                    onClick = { selectedStopId = st.stopId },
                                    label = { Text("${st.stopName} (₹$stFee)") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    Text(p10Label(isHindi, "यात्रा प्रकार:", "Trip Type:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TransportTripType.entries.forEach { t ->
                            FilterChip(
                                selected = tripType == t,
                                onClick = { tripType = t },
                                label = { Text(t.label(isHindi)) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    Text(p10Label(isHindi, "शुरुआती महीना (Start Month):", "Start Month:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        structure.monthOrder.forEach { m ->
                            FilterChip(
                                selected = startMonth.equals(m, ignoreCase = true),
                                onClick = { startMonth = m },
                                label = { Text(m) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    Text(p10Label(isHindi, "अंतिम महीना (वैकल्पिक):", "Optional End Month:"), style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = endMonth.isBlank(),
                            onClick = { endMonth = "" },
                            label = { Text(p10Label(isHindi, "पूरा सत्र", "Full Session")) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        structure.monthOrder.forEach { m ->
                            FilterChip(
                                selected = endMonth.equals(m, ignoreCase = true),
                                onClick = { endMonth = m },
                                label = { Text(m) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepCustomOverrides, onCheckedChange = { keepCustomOverrides = it })
                        Text(p10Label(isHindi, "ओवरराइड किए गए छात्रों की कस्टम फीस रखें", "Preserve custom overridden fees"), style = MaterialTheme.typography.bodySmall)
                    }

                    if (exceedsCapacity && pickedRoute != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = FailRedContainer,
                            border = BorderStroke(1.dp, FailRed)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = FailRed, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = p10Label(
                                            isHindi,
                                            "चेतावनी: '${pickedRoute.routeName}' की सीट क्षमता (${pickedRoute.seatCapacity}) भर जाएगी / अधिक होगी।",
                                            "Warning: Assigning to '${pickedRoute.routeName}' exceeds seat capacity (${pickedRoute.seatCapacity})."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FailRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = confirmOverCapacity,
                                        onCheckedChange = { confirmOverCapacity = it },
                                        modifier = Modifier.testTag("over_capacity_confirm_checkbox")
                                    )
                                    Text(
                                        p10Label(isHindi, "सीट से अधिक होने पर भी जोड़ें", "Continue with Over-Capacity flag"),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rt = pickedRoute ?: return@Button
                    onConfirm(rt, pickedStop, tripType, startMonth, endMonth, keepCustomOverrides) { errMsg = it }
                },
                enabled = pickedRoute != null && (!exceedsCapacity || confirmOverCapacity),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("bulk_assign_confirm_button")
            ) {
                Text(p10Label(isHindi, "असाइन करें (${students.size})", "Assign (${students.size})"))
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
private fun OverrideTransportFeeDialog(
    student: StudentEntity,
    display: TransportResolvedDisplay,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirmOverride: (Int, String, (String) -> Unit) -> Unit
) {
    var amountText by remember { mutableStateOf(student.transportMonthlyAmount.toString()) }
    var remark by remember { mutableStateOf(student.transportOverrideRemark) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "कस्टम शुल्क: ${student.name}", "Override Fee: ${student.name}"), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    p10Label(
                        isHindi,
                        "रूट / स्टॉप की सामान्य दर: ₹${display.computedRouteOrStopFeeRs}/माह",
                        "Computed route/stop rate: ₹${display.computedRouteOrStopFeeRs}/mo"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() }; errMsg = null },
                    label = { Text(p10Label(isHindi, "नया मासिक शुल्क ₹ *", "Custom Monthly Fee ₹ *")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it; errMsg = null },
                    label = { Text(p10Label(isHindi, "कारण / टिप्पणी (अनिवार्य) *", "Override Remark (Mandatory) *")) },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toIntOrNull()
                    if (amt == null) {
                        errMsg = "Please enter a valid monthly fee amount."
                    } else if (remark.trim().isEmpty()) {
                        errMsg = "Remark is mandatory for overriding a student's fee."
                    } else {
                        onConfirmOverride(amt, remark.trim()) { errMsg = it }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(p10Label(isHindi, "सेव करें", "Save Override"))
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
private fun BulkTripTypeDialog(
    count: Int,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TransportTripType, (String) -> Unit) -> Unit
) {
    var trip by remember { mutableStateOf(TransportTripType.BOTH_WAYS) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "यात्रा प्रकार बदलें ($count छात्र)", "Set Trip Type ($count)")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TransportTripType.entries.forEach { t ->
                        FilterChip(
                            selected = trip == t,
                            onClick = { trip = t },
                            label = { Text(t.label(isHindi)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(trip) { errMsg = it } }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(p10Label(isHindi, "लागू करें", "Apply"))
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
private fun BulkMonthsDialog(
    count: Int,
    orderedMonths: List<String>,
    isHindi: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, (String) -> Unit) -> Unit
) {
    val months = orderedMonths.ifEmpty { SessionMonthCode.defaultAprilToMarchOrder }
    var startM by remember { mutableStateOf(months.firstOrNull() ?: "APR") }
    var endM by remember { mutableStateOf("") }
    var errMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p10Label(isHindi, "शुरुआती व अंतिम महीना ($count छात्र)", "Set Start / End Month ($count)")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p10Label(isHindi, "शुरुआती महीना:", "Start Month:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    months.forEach { m ->
                        FilterChip(selected = startM == m, onClick = { startM = m }, label = { Text(m) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Text(p10Label(isHindi, "अंतिम महीना (वैकल्पिक):", "Optional End Month:"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = endM.isBlank(), onClick = { endM = "" }, label = { Text("Full Session") }, modifier = Modifier.heightIn(min = 48.dp))
                    months.forEach { m ->
                        FilterChip(selected = endM == m, onClick = { endM = m }, label = { Text(m) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                if (errMsg != null) Text(errMsg!!, color = FailRed, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(startM, endM) { errMsg = it } }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(p10Label(isHindi, "सेव करें", "Save Months"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(p10Label(isHindi, "रद्द", "Cancel"))
            }
        }
    )
}
