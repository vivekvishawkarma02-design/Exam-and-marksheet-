package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.DayOfWeekCode
import com.example.domain.PeriodSlot
import com.example.domain.PeriodValidationResult
import com.example.domain.Phase4Validator
import com.example.domain.SchoolPeriodStructure
import com.example.domain.SlotType
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodStructureTab(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    canEdit: Boolean,
    modifier: Modifier = Modifier
) {
    val activeSchoolDocId = uiState.activeSchoolDocId
    if (activeSchoolDocId.isBlank()) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No active school selected.", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val savedStructure = remember(activeSchoolDocId, uiState.periodStructuresBySchool) {
        uiState.periodStructureForSchool(activeSchoolDocId)
    }

    var draft by remember(activeSchoolDocId, savedStructure) { mutableStateOf(savedStructure) }
    var selectedDay by remember(activeSchoolDocId) {
        mutableStateOf(savedStructure.workingDays.firstOrNull() ?: DayOfWeekCode.MON)
    }
    var hasLocalEdits by remember(activeSchoolDocId, savedStructure) { mutableStateOf(false) }

    LaunchedEffect(draft.workingDays) {
        if (selectedDay !in draft.workingDays && draft.workingDays.isNotEmpty()) {
            selectedDay = draft.workingDays.first()
        }
    }

    var editingSlotPair by remember { mutableStateOf<Pair<Int?, PeriodSlot>?>(null) } // index (null if new) to slot
    var showCopyDayDialog by remember { mutableStateOf(false) }

    val validation = remember(draft) { Phase4Validator.validatePeriodStructure(draft) }
    val daySlots = draft.slotsFor(selectedDay)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // School header & summary
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
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
                                text = "${uiState.activeSchool.name} — Schedule",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${draft.workingDays.size} working day(s) • ${draft.totalTeachingPeriodsPerWeek()} teaching periods/week" +
                                    if (!savedStructure.isCustomized) " (Default template)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canEdit) {
                            IconButton(
                                onClick = {
                                    draft = Phase4Validator.buildDefaultPeriodStructure(activeSchoolDocId)
                                    hasLocalEdits = true
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("period_reset_default_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Load default template"
                                )
                            }
                        }
                    }

                    Text(
                        text = "Working Days (switch school at top to edit another school's timings)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DayOfWeekCode.entries.forEach { day ->
                            val isWorking = day in draft.workingDays
                            FilterChip(
                                selected = isWorking,
                                onClick = {
                                    if (!canEdit) return@FilterChip
                                    val newDays = if (isWorking) {
                                        if (draft.workingDays.size <= 1) return@FilterChip
                                        draft.workingDays - day
                                    } else {
                                        (draft.workingDays + day).sortedBy { it.sortOrder }
                                    }
                                    val newSlotsMap = draft.slotsByDay.toMutableMap()
                                    if (!isWorking && newSlotsMap[day].isNullOrEmpty()) {
                                        val template = Phase4Validator.buildDefaultDaySlots(
                                            periodCount = if (day == DayOfWeekCode.SAT) 5 else 8,
                                            includeLunch = day != DayOfWeekCode.SAT
                                        ).mapIndexed { i, s -> s.copy(id = "${day.wireValue}_slot_${i + 1}") }
                                        newSlotsMap[day] = template
                                    }
                                    draft = draft.copy(workingDays = newDays, slotsByDay = newSlotsMap)
                                    hasLocalEdits = true
                                },
                                label = { Text(day.shortLabel) },
                                leadingIcon = if (isWorking) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                modifier = Modifier.testTag("working_day_${day.wireValue}")
                            )
                        }
                    }
                }
            }
        }

        // Validation error card (blocks saving overlapping or out-of-order slots)
        if (validation is PeriodValidationResult.Invalid) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FailRedContainer,
                    border = BorderStroke(1.dp, FailRed.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("period_validation_error_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = FailRed, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Fix Timing Errors Before Saving",
                                style = MaterialTheme.typography.titleSmall,
                                color = FailRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        validation.errors.take(4).forEach { err ->
                            Text(
                                text = "• $err",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5A1212)
                            )
                        }
                    }
                }
            }
        }

        // Day picker + Copy / Add controls
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    draft.workingDays.forEach { day ->
                        val pCount = draft.teachingPeriodsPerDay(day)
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = { Text("${day.shortLabel} ($pCount)") },
                            modifier = Modifier.testTag("select_day_${day.wireValue}")
                        )
                    }
                }

                if (canEdit) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val lastEnd = daySlots.lastOrNull()?.endMinutes ?: (8 * 60)
                                val nextEnd = (lastEnd + 40).coerceAtMost(24 * 60)
                                val nextPeriodNum = daySlots.count { it.type == SlotType.PERIOD } + 1
                                editingSlotPair = null to PeriodSlot(
                                    id = "${selectedDay.wireValue}_${UUID.randomUUID().toString().take(6)}",
                                    type = SlotType.PERIOD,
                                    label = "Period $nextPeriodNum",
                                    startMinutes = lastEnd.coerceAtMost(23 * 60 + 55),
                                    endMinutes = nextEnd.coerceAtLeast((lastEnd + 5).coerceAtMost(24 * 60))
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("period_add_slot_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Slot")
                        }
                        OutlinedButton(
                            onClick = { showCopyDayDialog = true },
                            enabled = draft.workingDays.size > 1,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("period_copy_day_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy This Day")
                        }
                    }
                }
            }
        }

        // Ordered slots for selected day
        if (daySlots.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No slots on ${selectedDay.fullLabel}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Tap 'Add Slot' or 'Copy This Day' from another day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(daySlots, key = { idx, s -> "${selectedDay.wireValue}_${s.id}_$idx" }) { index, slot ->
                PeriodSlotCard(
                    slot = slot,
                    canEdit = canEdit,
                    canMoveUp = index > 0,
                    canMoveDown = index < daySlots.lastIndex,
                    onMoveUp = {
                        val updated = daySlots.toMutableList()
                        val item = updated.removeAt(index)
                        updated.add(index - 1, item)
                        draft = draft.copy(slotsByDay = draft.slotsByDay + (selectedDay to updated))
                        hasLocalEdits = true
                    },
                    onMoveDown = {
                        val updated = daySlots.toMutableList()
                        val item = updated.removeAt(index)
                        updated.add(index + 1, item)
                        draft = draft.copy(slotsByDay = draft.slotsByDay + (selectedDay to updated))
                        hasLocalEdits = true
                    },
                    onEdit = { editingSlotPair = index to slot },
                    onDelete = {
                        val updated = daySlots.toMutableList().apply { removeAt(index) }
                        draft = draft.copy(slotsByDay = draft.slotsByDay + (selectedDay to updated))
                        hasLocalEdits = true
                    }
                )
            }
        }

        // Save button
        if (canEdit) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        viewModel.savePeriodStructure(draft) { ok, _ ->
                            if (ok) hasLocalEdits = false
                        }
                    },
                    enabled = validation is PeriodValidationResult.Valid && (hasLocalEdits || !savedStructure.isCustomized),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("save_period_structure_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!savedStructure.isCustomized) {
                            "Save Period Structure for ${uiState.activeSchool.name}"
                        } else if (hasLocalEdits) {
                            "Save Changes (${draft.totalTeachingPeriodsPerWeek()} periods/wk)"
                        } else {
                            "Saved (${draft.totalTeachingPeriodsPerWeek()} periods/wk)"
                        }
                    )
                }
            }
        }
    }

    // Add / Edit Slot Dialog
    editingSlotPair?.let { (slotIndex, initialSlot) ->
        PeriodSlotEditDialog(
            day = selectedDay,
            initialSlot = initialSlot,
            existingOtherSlots = daySlots.filterIndexed { idx, _ -> idx != slotIndex },
            onConfirm = { newSlot ->
                val updated = daySlots.toMutableList()
                if (slotIndex == null) {
                    updated.add(newSlot)
                    updated.sortBy { it.startMinutes }
                } else {
                    updated[slotIndex] = newSlot
                }
                draft = draft.copy(slotsByDay = draft.slotsByDay + (selectedDay to updated))
                hasLocalEdits = true
                editingSlotPair = null
            },
            onDismiss = { editingSlotPair = null }
        )
    }

    // Copy Day Dialog
    if (showCopyDayDialog) {
        CopyDayDialog(
            sourceDay = selectedDay,
            workingDays = draft.workingDays,
            onConfirm = { targets ->
                draft = Phase4Validator.copyDaySlots(draft, selectedDay, targets)
                hasLocalEdits = true
                showCopyDayDialog = false
            },
            onDismiss = { showCopyDayDialog = false }
        )
    }
}

@Composable
private fun PeriodSlotCard(
    slot: PeriodSlot,
    canEdit: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val badgeBg = when (slot.type) {
        SlotType.PERIOD -> MaterialTheme.colorScheme.primaryContainer
        SlotType.ASSEMBLY -> MaterialTheme.colorScheme.tertiaryContainer
        SlotType.SHORT_BREAK -> AbsentAmberContainer
        SlotType.LUNCH -> PassGreenContainer
    }
    val badgeFg = when (slot.type) {
        SlotType.PERIOD -> MaterialTheme.colorScheme.primary
        SlotType.ASSEMBLY -> MaterialTheme.colorScheme.onTertiaryContainer
        SlotType.SHORT_BREAK -> AbsentAmber
        SlotType.LUNCH -> PassGreen
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(shape = RoundedCornerShape(6.dp), color = badgeBg) {
                        Text(
                            text = slot.type.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeFg,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = slot.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${slot.startTimeFormatted} – ${slot.endTimeFormatted}  (${slot.durationMinutes} min)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (canEdit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit slot", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete slot", tint = FailRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodSlotEditDialog(
    day: DayOfWeekCode,
    initialSlot: PeriodSlot,
    existingOtherSlots: List<PeriodSlot>,
    onConfirm: (PeriodSlot) -> Unit,
    onDismiss: () -> Unit
) {
    var slotType by remember(initialSlot) { mutableStateOf(initialSlot.type) }
    var label by remember(initialSlot) { mutableStateOf(initialSlot.label) }
    var startText by remember(initialSlot) { mutableStateOf(initialSlot.startTimeFormatted) }
    var endText by remember(initialSlot) { mutableStateOf(initialSlot.endTimeFormatted) }

    val parsedStart = Phase4Validator.parseTimeToMinutes(startText)
    val parsedEnd = Phase4Validator.parseTimeToMinutes(endText)

    val liveError = remember(parsedStart, parsedEnd, slotType, label, existingOtherSlots) {
        when {
            parsedStart == null -> "Start time must be in 24-hour HH:mm format (e.g. 08:15)."
            parsedEnd == null -> "End time must be in 24-hour HH:mm format (e.g. 08:55)."
            parsedEnd <= parsedStart -> "End time (${Phase4Validator.formatMinutes(parsedEnd)}) must be after start time (${Phase4Validator.formatMinutes(parsedStart)})."
            else -> {
                val candidate = initialSlot.copy(
                    type = slotType,
                    label = label.trim().ifBlank { slotType.label },
                    startMinutes = parsedStart,
                    endMinutes = parsedEnd
                )
                val overlapping = existingOtherSlots.firstOrNull { other ->
                    candidate.startMinutes < other.endMinutes && other.startMinutes < candidate.endMinutes
                }
                if (overlapping != null) {
                    "Overlaps with '${overlapping.label}' (${overlapping.startTimeFormatted}–${overlapping.endTimeFormatted})."
                } else null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${day.fullLabel} Slot", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Slot Type", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SlotType.entries.forEach { t ->
                        FilterChip(
                            selected = slotType == t,
                            onClick = {
                                slotType = t
                                if (label.isBlank() || SlotType.entries.any { it.label == label } || label == "Morning Assembly") {
                                    label = if (t == SlotType.ASSEMBLY) "Morning Assembly" else t.label
                                }
                            },
                            label = { Text(t.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Slot Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("slot_label_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text("Start (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("slot_start_input")
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text("End (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("slot_end_input")
                    )
                }

                // Quick duration buttons
                if (parsedStart != null) {
                    Text("Quick Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 35, 40, 45).forEach { mins ->
                            OutlinedButton(
                                onClick = {
                                    val newEnd = (parsedStart + mins).coerceAtMost(1440)
                                    endText = Phase4Validator.formatMinutes(newEnd)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("${mins}m")
                            }
                        }
                    }
                }

                liveError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = FailRedContainer,
                        modifier = Modifier.fillMaxWidth().testTag("slot_overlap_error")
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = FailRed,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = liveError == null && parsedStart != null && parsedEnd != null,
                onClick = {
                    onConfirm(
                        initialSlot.copy(
                            type = slotType,
                            label = label.trim().ifBlank { slotType.label },
                            startMinutes = parsedStart!!,
                            endMinutes = parsedEnd!!
                        )
                    )
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("slot_dialog_save_button")
            ) {
                Text("Save Slot")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CopyDayDialog(
    sourceDay: DayOfWeekCode,
    workingDays: List<DayOfWeekCode>,
    onConfirm: (List<DayOfWeekCode>) -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = remember(workingDays, sourceDay) {
        workingDays.filter { it != sourceDay }
    }
    val selected = remember(candidates) {
        mutableStateListOf<DayOfWeekCode>().apply {
            addAll(candidates.filter { it != DayOfWeekCode.SAT })
            if (isEmpty()) addAll(candidates)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy ${sourceDay.fullLabel} to Other Days") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Select the target days whose slots should be replaced with ${sourceDay.fullLabel}'s slots:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                candidates.forEach { day ->
                    val checked = day in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable {
                                if (checked) selected.remove(day) else selected.add(day)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (day !in selected) selected.add(day)
                                } else {
                                    selected.remove(day)
                                }
                            }
                        )
                        Text(day.fullLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selected.isNotEmpty(),
                onClick = { onConfirm(selected.toList()) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_copy_day_button")
            ) {
                Text("Copy to ${selected.size} Day(s)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
