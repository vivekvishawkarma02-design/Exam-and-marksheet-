package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.DayOfWeekCode
import com.example.domain.FreeTeacherEntry
import com.example.domain.Phase4Validator
import com.example.domain.TimetableCell
import com.example.domain.TimetableEngine
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import com.example.ui.theme.SteelSecondary
import com.example.ui.theme.SurfaceMist
import com.example.util.PdfExportHelper
import com.example.util.TimetablePrintOrientation

// =============================================================================
// 1. TEACHER TIMETABLE TAB (All schools combined)
// =============================================================================

@Composable
internal fun TeacherTimetableTab(
    uiState: SchoolUiState,
    selectedTeacherId: String,
    onSelectTeacherId: (String) -> Unit,
    clashCellIds: Set<String>,
    onJumpToCell: (TimetableCell) -> Unit,
    onPrintTeacher: () -> Unit
) {
    val teachers = remember(uiState.teachers, uiState.timetableCells) {
        uiState.availableTimetableTeachers()
    }
    val selectedTeacher = teachers.firstOrNull { it.id == selectedTeacherId } ?: teachers.firstOrNull()

    if (selectedTeacher == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No teachers available yet. Add teachers in Teachers & Setup or schedule timetable periods.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val summary = remember(
        selectedTeacher.id,
        uiState.filter.session,
        uiState.activeSessionTimetableCells,
        uiState.effectivePeriodStructuresBySchool,
        uiState.schoolNamesByDocId,
        uiState.teachers
    ) {
        TimetableEngine.buildTeacherTimetable(
            teacherId = selectedTeacher.id,
            fallbackTeacherName = selectedTeacher.name,
            teacherProfile = uiState.teachers.firstOrNull { it.id == selectedTeacher.id },
            session = uiState.filter.session,
            cellsInSession = uiState.activeSessionTimetableCells,
            periodStructuresBySchool = uiState.effectivePeriodStructuresBySchool,
            schoolNamesById = uiState.schoolNamesByDocId
        )
    }

    val primarySchoolDocId = selectedTeacher.schoolIds.firstOrNull()?.takeIf { it.isNotBlank() }
        ?: uiState.activeSchoolDocId
    val primaryStructure = remember(primarySchoolDocId, uiState.effectivePeriodStructuresBySchool) {
        uiState.periodStructureForSchool(primarySchoolDocId)
    }
    val gridRows = remember(primaryStructure) { TimetableEngine.buildGridRows(primaryStructure) }
    val workingDays = remember(primaryStructure) {
        primaryStructure.workingDays.sortedBy { it.sortOrder }.ifEmpty { DayOfWeekCode.defaultWorkingDays }
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    val usedCellIds = remember(summary, gridRows, workingDays) {
        val matched = mutableSetOf<String>()
        gridRows.filter { it.isEditablePeriodRow }.forEach { row ->
            workingDays.forEach { day ->
                val daySlot = row.slotByDay[day] ?: return@forEach
                val item = summary.itemsByDay[day].orEmpty().firstOrNull { candidate ->
                    candidate.cell.id !in matched && (
                        candidate.cell.slotId == daySlot.id ||
                            TimetableEngine.slotsOverlap(candidate.startMinutes, candidate.endMinutes, daySlot.startMinutes, daySlot.endMinutes)
                        )
                }
                if (item != null) matched.add(item.cell.id)
            }
        }
        matched
    }
    val extraShiftItems = remember(summary, usedCellIds) {
        summary.itemsByDay.values.flatten().filter { it.cell.id !in usedCellIds }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Teacher selector & KPI strip
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        CompactDropdownSelector(
                            label = "Teacher (all schools combined)",
                            selectedValue = selectedTeacher.name,
                            options = teachers.map { it.name },
                            onSelect = { name ->
                                teachers.firstOrNull { it.name == name }?.let { onSelectTeacherId(it.id) }
                            },
                            testTag = "tt_teacher_picker"
                        )
                    }
                    OutlinedButton(
                        onClick = onPrintTeacher,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tt_teacher_print_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF")
                    }
                }

                // KPI chips + Zoom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val loadOver = summary.totalPeriodsPerWeek > summary.weeklyLimit
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (loadOver) FailRedContainer else PassGreenContainer
                        ) {
                            Text(
                                text = "Load: ${summary.totalPeriodsPerWeek}/${summary.weeklyLimit}/wk",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (loadOver) FailRed else PassGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceMist
                        ) {
                            Text(
                                text = "Free: ${summary.freePeriodsPerWeek}/wk",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    TimetableZoomControls(zoomScale = zoomScale, onZoomChange = { zoomScale = it })
                }
            }
        }

        HorizontalDivider()

        val timeColWidth = (86f * zoomScale).dp
        val dayColWidth = (128f * zoomScale).dp
        val headerHeight = 42.dp
        val periodRowHeight = (74f * zoomScale).coerceAtLeast(58f).dp
        val breakRowHeight = (34f * zoomScale).coerceAtLeast(28f).dp

        Column(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) zoomScale = (zoomScale * zoom).coerceIn(0.75f, 1.5f)
                    }
                }
                .testTag("tt_teacher_grid")
        ) {
            // Sticky Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyPrimary)
            ) {
                Box(
                    modifier = Modifier
                        .width(timeColWidth)
                        .height(headerHeight)
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Period", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                ) {
                    workingDays.forEach { day ->
                        val isOff = day in selectedTeacher.weeklyOffDays
                        Box(
                            modifier = Modifier
                                .width(dayColWidth)
                                .height(headerHeight)
                                .background(if (isOff) Color(0xFF4E342E) else NavyPrimary)
                                .border(0.5.dp, Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isOff) "${day.shortLabel.uppercase()} (OFF)" else day.shortLabel.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Vertically Scrolled Body
            val renderedIds = mutableSetOf<String>()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(vScroll)
            ) {
                // Sticky Left Time Column
                Column(modifier = Modifier.width(timeColWidth)) {
                    gridRows.forEach { row ->
                        val rowH = if (row.isEditablePeriodRow) periodRowHeight else breakRowHeight
                        Box(
                            modifier = Modifier
                                .width(timeColWidth)
                                .height(rowH)
                                .background(if (row.isEditablePeriodRow) SurfaceMist else MaterialTheme.colorScheme.surfaceVariant)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(row.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(row.timeLabel, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }

                // Horizontally Scrolled Cells
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                ) {
                    gridRows.forEach { row ->
                        val rowH = if (row.isEditablePeriodRow) periodRowHeight else breakRowHeight
                        if (!row.isEditablePeriodRow) {
                            Box(
                                modifier = Modifier
                                    .width(dayColWidth * workingDays.size)
                                    .height(rowH)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${row.label} (${row.timeLabel})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Row {
                                workingDays.forEach { day ->
                                    val daySlot = row.slotByDay[day]
                                    val matched = if (daySlot != null) {
                                        summary.itemsByDay[day].orEmpty().firstOrNull { item ->
                                            item.cell.id !in renderedIds && (
                                                item.cell.slotId == daySlot.id ||
                                                    TimetableEngine.slotsOverlap(item.startMinutes, item.endMinutes, daySlot.startMinutes, daySlot.endMinutes)
                                                )
                                        }
                                    } else null
                                    if (matched != null) renderedIds.add(matched.cell.id)
                                    val hasClash = matched != null && matched.cell.id in clashCellIds
                                    val isOff = day in selectedTeacher.weeklyOffDays

                                    Box(
                                        modifier = Modifier
                                            .width(dayColWidth)
                                            .height(rowH)
                                            .background(
                                                when {
                                                    hasClash -> FailRedContainer
                                                    isOff -> AbsentAmberContainer.copy(alpha = 0.5f)
                                                    matched != null -> MaterialTheme.colorScheme.surface
                                                    else -> MaterialTheme.colorScheme.background
                                                }
                                            )
                                            .border(
                                                width = if (hasClash) 1.2.dp else 0.5.dp,
                                                color = if (hasClash) FailRed else MaterialTheme.colorScheme.outlineVariant
                                            )
                                            .then(
                                                if (matched != null) Modifier.clickable { onJumpToCell(matched.cell) } else Modifier
                                            )
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (matched != null) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(1.dp)
                                            ) {
                                                Text(
                                                    text = "${matched.className}-${matched.section} • ${TimetableEngine.shortSubjectName(matched.subjectName)}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (hasClash) FailRed else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = matched.schoolName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${Phase4Validator.formatMinutes(matched.startMinutes)}–${Phase4Validator.formatMinutes(matched.endMinutes)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = if (isOff) "Off" else if (daySlot == null) "—" else "Free",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
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

        // Extra non-overlapping school shift items (if any)
        if (extraShiftItems.isNotEmpty()) {
            Surface(color = SurfaceMist, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Additional School Shift Periods (${extraShiftItems.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    extraShiftItems.forEach { item ->
                        Text(
                            text = "• ${item.day.shortLabel} ${Phase4Validator.formatMinutes(item.startMinutes)}–${Phase4Validator.formatMinutes(item.endMinutes)} • ${item.className}-${item.section} ${item.subjectName} (${item.schoolName})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { onJumpToCell(item.cell) }
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 2. MASTER TIMETABLE TAB (One school: rows = class-section, columns = day x period)
// =============================================================================

@Composable
internal fun MasterTimetableTab(
    uiState: SchoolUiState,
    selectedSchoolDocId: String,
    onSelectSchoolDocId: (String) -> Unit,
    clashCellIds: Set<String>,
    onJumpToClassSection: (String, String) -> Unit,
    onJumpToCell: (TimetableCell) -> Unit,
    onPrintMaster: () -> Unit
) {
    val schoolLongId = uiState.schoolDocIdByLongId.entries.firstOrNull { it.value == selectedSchoolDocId }?.key
        ?: uiState.activeSchool.id
    val periodStructure = remember(selectedSchoolDocId, uiState.effectivePeriodStructuresBySchool) {
        uiState.periodStructureForSchool(selectedSchoolDocId)
    }
    val workingDays = remember(periodStructure) {
        periodStructure.workingDays.sortedBy { it.sortOrder }.ifEmpty { DayOfWeekCode.defaultWorkingDays }
    }
    val dayColumns = remember(periodStructure, workingDays) {
        workingDays.map { day ->
            day to periodStructure.slotsFor(day).filter { it.type.countsAsTeachingPeriod }
        }.filter { it.second.isNotEmpty() }
    }
    val flatCols = remember(dayColumns) {
        dayColumns.flatMap { (day, slots) ->
            slots.mapIndexed { idx, slot -> Triple(day, idx + 1, slot) }
        }
    }

    val classSectionRows = remember(
        schoolLongId,
        selectedSchoolDocId,
        uiState.schoolClasses,
        uiState.allStudents,
        uiState.classAssignments,
        uiState.activeSessionTimetableCells
    ) {
        val classes = uiState.classesForSchool(schoolLongId)
        classes.flatMap { cls ->
            uiState.sectionsForSchoolClass(schoolLongId, cls.name).map { sec ->
                val map = uiState.activeSessionTimetableCells
                    .filter {
                        it.schoolId == selectedSchoolDocId &&
                            it.className.equals(cls.name, ignoreCase = true) &&
                            it.section.equals(sec, ignoreCase = true)
                    }
                    .associateBy { it.day to it.slotId }
                Triple(cls.name, sec, map)
            }
        }
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.activeSchools.size > 1) {
                        Box(modifier = Modifier.weight(1f)) {
                            CompactDropdownSelector(
                                label = "School",
                                selectedValue = uiState.schoolNameByDocId(selectedSchoolDocId),
                                options = uiState.activeSchools.map { it.name },
                                onSelect = { picked ->
                                    uiState.activeSchools.firstOrNull { it.name == picked }?.let { s ->
                                        uiState.schoolDocIdByLongId[s.id]?.let(onSelectSchoolDocId)
                                    }
                                },
                                testTag = "tt_master_school_picker"
                            )
                        }
                    } else {
                        Text(
                            text = uiState.schoolNameByDocId(selectedSchoolDocId),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(
                        onClick = onPrintMaster,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tt_master_print_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Master PDF")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${classSectionRows.size} class-sections • ${flatCols.size} periods/week",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TimetableZoomControls(zoomScale = zoomScale, onZoomChange = { zoomScale = it })
                }
            }
        }

        HorizontalDivider()

        if (classSectionRows.isEmpty() || flatCols.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No classes or teaching periods configured for this school.", style = MaterialTheme.typography.bodyMedium)
            }
            return
        }

        val classColWidth = (80f * zoomScale).dp
        val cellColWidth = (74f * zoomScale).coerceAtLeast(56f).dp
        val dayHdrH = 24.dp
        val periodHdrH = 24.dp
        val rowH = (56f * zoomScale).coerceAtLeast(48f).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) zoomScale = (zoomScale * zoom).coerceIn(0.75f, 1.5f)
                    }
                }
                .testTag("tt_master_grid")
        ) {
            // Sticky 2-Tier Header (Class corner + Day/Period columns)
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(classColWidth)
                        .height(dayHdrH + periodHdrH)
                        .background(NavyPrimary)
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Class", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                ) {
                    Row {
                        dayColumns.forEach { (day, slots) ->
                            Box(
                                modifier = Modifier
                                    .width(cellColWidth * slots.size)
                                    .height(dayHdrH)
                                    .background(NavyPrimary)
                                    .border(0.7.dp, Color.White.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(day.shortLabel.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                    Row {
                        flatCols.forEach { (_, pNum, _) ->
                            Box(
                                modifier = Modifier
                                    .width(cellColWidth)
                                    .height(periodHdrH)
                                    .background(SteelSecondary)
                                    .border(0.5.dp, Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("P$pNum", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Vertically Scrolled Body (Sticky Class-Section Column + Cells)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(vScroll)
            ) {
                Column(modifier = Modifier.width(classColWidth)) {
                    classSectionRows.forEach { (cls, sec, _) ->
                        Box(
                            modifier = Modifier
                                .width(classColWidth)
                                .height(rowH)
                                .background(SurfaceMist)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .clickable { onJumpToClassSection(cls, sec) }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$cls-$sec",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                ) {
                    classSectionRows.forEach { (cls, sec, cellMap) ->
                        Row {
                            flatCols.forEach { (day, _, slot) ->
                                val cell = cellMap[day to slot.id]
                                val hasClash = cell != null && cell.id in clashCellIds
                                Box(
                                    modifier = Modifier
                                        .width(cellColWidth)
                                        .height(rowH)
                                        .background(
                                            when {
                                                hasClash -> FailRedContainer
                                                cell != null -> MaterialTheme.colorScheme.surface
                                                else -> MaterialTheme.colorScheme.background
                                            }
                                        )
                                        .border(
                                            width = if (hasClash) 1.1.dp else 0.5.dp,
                                            color = if (hasClash) FailRed else MaterialTheme.colorScheme.outlineVariant
                                        )
                                        .clickable {
                                            if (cell != null) onJumpToCell(cell)
                                            else onJumpToClassSection(cls, sec)
                                        }
                                        .padding(horizontal = 3.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cell != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = TimetableEngine.shortSubjectName(cell.subjectName),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (hasClash) FailRed else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = (10.5f * zoomScale.coerceIn(0.85f, 1.2f)).sp
                                            )
                                            Text(
                                                text = TimetableEngine.shortTeacherName(cell.teacherName.ifBlank { cell.teacherId }),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (hasClash) FailRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = (9.5f * zoomScale.coerceIn(0.85f, 1.15f)).sp
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

// =============================================================================
// 3. FREE PERIODS TAB (Pick day & period -> list free active teachers across schools)
// =============================================================================

@Composable
internal fun FreePeriodsTab(
    uiState: SchoolUiState,
    selectedSchoolDocId: String,
    onSelectSchoolDocId: (String) -> Unit
) {
    val structure = remember(selectedSchoolDocId, uiState.effectivePeriodStructuresBySchool) {
        uiState.periodStructureForSchool(selectedSchoolDocId)
    }
    val workingDays = remember(structure) {
        structure.workingDays.sortedBy { it.sortOrder }.ifEmpty { DayOfWeekCode.defaultWorkingDays }
    }
    var selectedDay by remember(workingDays) { mutableStateOf(workingDays.first()) }
    LaunchedEffect(workingDays) {
        if (selectedDay !in workingDays) selectedDay = workingDays.first()
    }

    val teachingSlots = remember(structure, selectedDay) {
        structure.slotsFor(selectedDay).filter { it.type.countsAsTeachingPeriod }
    }
    var selectedSlotId by remember(teachingSlots) { mutableStateOf(teachingSlots.firstOrNull()?.id.orEmpty()) }
    LaunchedEffect(teachingSlots) {
        if (teachingSlots.none { it.id == selectedSlotId }) {
            selectedSlotId = teachingSlots.firstOrNull()?.id.orEmpty()
        }
    }
    val selectedSlot = teachingSlots.firstOrNull { it.id == selectedSlotId } ?: teachingSlots.firstOrNull()

    val freeTeachers: List<FreeTeacherEntry> = remember(
        selectedSchoolDocId,
        uiState.filter.session,
        selectedDay,
        selectedSlot,
        uiState.teachers,
        uiState.activeSessionTimetableCells,
        uiState.effectivePeriodStructuresBySchool
    ) {
        if (selectedSlot == null) emptyList()
        else TimetableEngine.findFreeTeachers(
            schoolId = selectedSchoolDocId,
            session = uiState.filter.session,
            day = selectedDay,
            slot = selectedSlot,
            teachers = uiState.availableTimetableTeachers(),
            cellsInSession = uiState.activeSessionTimetableCells,
            periodStructuresBySchool = uiState.effectivePeriodStructuresBySchool
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.activeSchools.size > 1) {
                    CompactDropdownSelector(
                        label = "School Timing Reference",
                        selectedValue = uiState.schoolNameByDocId(selectedSchoolDocId),
                        options = uiState.activeSchools.map { it.name },
                        onSelect = { picked ->
                            uiState.activeSchools.firstOrNull { it.name == picked }?.let { s ->
                                uiState.schoolDocIdByLongId[s.id]?.let(onSelectSchoolDocId)
                            }
                        },
                        testTag = "tt_free_school_picker"
                    )
                }

                // Day chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    workingDays.forEach { day ->
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = { Text(day.shortLabel) },
                            modifier = Modifier.testTag("tt_free_day_${day.wireValue}")
                        )
                    }
                }

                // Period slot selector
                if (teachingSlots.isNotEmpty()) {
                    val slotOptions = teachingSlots.map { "${it.label} (${it.startTimeFormatted}–${it.endTimeFormatted})" }
                    val curLabel = selectedSlot?.let { "${it.label} (${it.startTimeFormatted}–${it.endTimeFormatted})" } ?: slotOptions.first()
                    CompactDropdownSelector(
                        label = "Period Slot",
                        selectedValue = curLabel,
                        options = slotOptions,
                        onSelect = { picked ->
                            val idx = slotOptions.indexOf(picked)
                            if (idx in teachingSlots.indices) selectedSlotId = teachingSlots[idx].id
                        },
                        testTag = "tt_free_slot_picker"
                    )
                }
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("tt_free_teachers_list"),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val timeDesc = selectedSlot?.let { "${it.startTimeFormatted}–${it.endTimeFormatted}" }.orEmpty()
                Text(
                    text = "${freeTeachers.size} free teacher(s) on ${selectedDay.fullLabel} • $timeDesc (checked across all schools)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (freeTeachers.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "No active teachers are free during this slot.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(freeTeachers, key = { it.teacher.id }) { entry ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StudentPhotoThumbnail(
                                photoBase64 = entry.teacher.photoBase64,
                                studentName = entry.teacher.name,
                                size = 42.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.teacher.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subLine = entry.teacher.subjects.joinToString(", ").ifBlank { "No subjects listed" }
                                Text(
                                    text = subLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (entry.worksInSelectedSchool) "Works in this school" else "Other school in organisation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (entry.worksInSelectedSchool) PassGreen else MaterialTheme.colorScheme.secondary
                                )
                            }
                            val nearLimit = entry.periodsToday >= entry.maxPeriodsPerDay
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (nearLimit) AbsentAmberContainer else PassGreenContainer
                            ) {
                                Text(
                                    text = "${entry.periodsToday}/${entry.maxPeriodsPerDay} today",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (nearLimit) AbsentAmber else PassGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 4. OFFLINE A4 PDF PRINT & BATCH EXPORT HUB + LIVE PREVIEW
// =============================================================================

@Composable
internal fun TimetablePrintHubDialog(
    schoolName: String,
    className: String,
    section: String,
    selectedTeacherName: String,
    hasSelectedTeacher: Boolean,
    onPrintClass: (TimetablePrintOrientation) -> Unit,
    onBatchAllClasses: (TimetablePrintOrientation, Boolean) -> Unit,
    onPrintTeacher: () -> Unit,
    onBatchAllTeachers: (Boolean) -> Unit,
    onPrintMaster: () -> Unit,
    onDismiss: () -> Unit
) {
    var orientation by remember { mutableStateOf(TimetablePrintOrientation.LANDSCAPE) }
    var includeZip by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Print & Export Timetables (Offline A4 PDF)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Class Timetable Orientation",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimetablePrintOrientation.entries.forEach { orient ->
                        FilterChip(
                            selected = orientation == orient,
                            onClick = { orientation = orient },
                            label = { Text(orient.label) }
                        )
                    }
                }

                // Class Timetable Single
                OutlinedButton(
                    onClick = { onPrintClass(orientation) },
                    enabled = className.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("tt_export_single_class")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Class $className-$section Timetable")
                }

                // Teacher Timetable Single
                OutlinedButton(
                    onClick = onPrintTeacher,
                    enabled = hasSelectedTeacher,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("tt_export_single_teacher")
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedTeacherName.isNotBlank()) "Teacher: $selectedTeacherName (All Schools)" else "Teacher Timetable",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Master Timetable
                OutlinedButton(
                    onClick = onPrintMaster,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("tt_export_master")
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Master Timetable — $schoolName (A4 Landscape)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                HorizontalDivider()

                Text(
                    text = "Batch Exports",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeZip = !includeZip }
                ) {
                    Checkbox(checked = includeZip, onCheckedChange = { includeZip = it })
                    Text("Also bundle individual PDFs as a ZIP archive", style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = { onBatchAllClasses(orientation, includeZip) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("tt_export_batch_classes")
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All Classes of $schoolName (PDF${if (includeZip) " + ZIP" else ""})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Button(
                    onClick = { onBatchAllTeachers(includeZip) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("tt_export_batch_teachers")
                ) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All Active Teachers (PDF${if (includeZip) " + ZIP" else ""})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
internal fun TimetablePdfPreviewDialog(
    pdfState: TimetablePdfPreviewState,
    onSavePdfToUri: (Uri) -> Unit,
    onSaveZipToUri: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) onSavePdfToUri(uri)
    }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onSaveZipToUri(uri)
    }

    AlertDialog(
        onDismissRequest = { if (!pdfState.isGenerating) onDismiss() },
        title = {
            Column {
                Text(
                    text = pdfState.title.ifBlank { "Timetable PDF Preview" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (pdfState.statusLabel.isNotBlank()) {
                    Text(
                        text = pdfState.statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    pdfState.isGenerating -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator()
                            if (pdfState.progressTotal > 0) {
                                LinearProgressIndicator(
                                    progress = { (pdfState.progressCurrent.toFloat() / pdfState.progressTotal).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "${pdfState.progressCurrent} / ${pdfState.progressTotal}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    pdfState.error != null -> {
                        Text(
                            text = pdfState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FailRed
                        )
                    }

                    pdfState.generatedFile != null -> {
                        val pdfFile = pdfState.generatedFile
                        val zipFile = pdfState.generatedZipFile

                        // Action Buttons: Print, Save PDF, Share PDF
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    PdfExportHelper.printPdfFile(
                                        context = context,
                                        file = pdfFile,
                                        jobName = pdfFile.nameWithoutExtension,
                                        isLandscape = pdfState.isLandscape
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tt_preview_print_button")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Print", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { savePdfLauncher.launch(pdfFile.name) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tt_preview_save_pdf_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    PdfExportHelper.shareFile(
                                        context = context,
                                        file = pdfFile,
                                        mimeType = "application/pdf",
                                        chooserTitle = "Share Timetable PDF"
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tt_preview_share_pdf_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", maxLines = 1)
                            }
                        }

                        if (zipFile != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { saveZipLauncher.launch(zipFile.name) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tt_preview_save_zip_button")
                                ) {
                                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save ZIP", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        PdfExportHelper.shareFile(
                                            context = context,
                                            file = zipFile,
                                            mimeType = "application/zip",
                                            chooserTitle = "Share Timetable ZIP"
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("tt_preview_share_zip_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share ZIP", maxLines = 1)
                                }
                            }
                        }

                        // Rendered A4 Page Preview(s)
                        if (pdfState.previewPages.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(pdfState.previewPages) { index, bmp ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Page ${index + 1} of ${pdfState.totalPages}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val ratio = (bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f))
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Timetable page ${index + 1}",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(ratio)
                                                    .background(Color.White)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!pdfState.isGenerating) {
                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    )
}
