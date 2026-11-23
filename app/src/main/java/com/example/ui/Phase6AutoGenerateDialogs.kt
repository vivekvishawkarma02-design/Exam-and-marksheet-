package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.domain.AutoGenerateConfig
import com.example.domain.AutoGenerateProgress
import com.example.domain.AutoGenerateResult
import com.example.domain.AutoGenerateScopeMode
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoGenerateConfigDialog(
    schoolDocId: String,
    schoolName: String,
    selectedClassName: String,
    selectedSection: String,
    session: String,
    lockedCellsCount: Int,
    onStart: (AutoGenerateConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var scopeMode by remember {
        mutableStateOf(
            if (selectedClassName.isNotBlank()) AutoGenerateScopeMode.WHOLE_SCHOOL
            else AutoGenerateScopeMode.WHOLE_SCHOOL
        )
    }
    var keepUnlocked by remember { mutableStateOf(false) }
    var timeLimitSeconds by remember { mutableIntStateOf(20) }
    var seedText by remember { mutableStateOf(Random.nextLong(100_000L, 999_999L).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Auto-generate timetable",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Auto-Generate Timetable",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "School: $schoolName • Session $session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Scope selector
                Text(
                    text = "Generate scope",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = scopeMode == AutoGenerateScopeMode.WHOLE_SCHOOL,
                        onClick = { scopeMode = AutoGenerateScopeMode.WHOLE_SCHOOL },
                        label = { Text("Whole school (all classes & sections)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("autogen_scope_school")
                    )
                    if (selectedClassName.isNotBlank()) {
                        FilterChip(
                            selected = scopeMode == AutoGenerateScopeMode.SINGLE_CLASS_SECTION,
                            onClick = { scopeMode = AutoGenerateScopeMode.SINGLE_CLASS_SECTION },
                            label = { Text("Only Class $selectedClassName-$selectedSection") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("autogen_scope_class")
                        )
                    }
                }

                // Keep existing cells option
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = keepUnlocked,
                            onCheckedChange = { keepUnlocked = it },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("autogen_keep_unlocked_checkbox")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keep already-filled unlocked cells",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Locked cells ($lockedCellsCount in school) are ALWAYS kept untouched.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Time limit selector
                Text(
                    text = "Search time limit",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30, 60).forEach { sec ->
                        FilterChip(
                            selected = timeLimitSeconds == sec,
                            onClick = { timeLimitSeconds = sec },
                            label = { Text("${sec}s") },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("autogen_time_$sec")
                        )
                    }
                }

                // Deterministic Seed input
                OutlinedTextField(
                    value = seedText,
                    onValueChange = { raw -> seedText = raw.filter { it.isDigit() || it == '-' }.take(14) },
                    label = { Text("Deterministic seed (same seed = same result)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        IconButton(
                            onClick = { seedText = Random.nextLong(100_000L, 999_999L).toString() },
                            modifier = Modifier.testTag("autogen_random_seed_btn")
                        ) {
                            Icon(Icons.Default.Casino, contentDescription = "Generate new random seed")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("autogen_seed_input")
                )

                Text(
                    text = "Hard rules (cross-school real clock times, teacher limits, off-days, inactive teachers) are never broken. You can Undo in 1 tap after applying.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedSeed = seedText.toLongOrNull() ?: System.currentTimeMillis()
                    onStart(
                        AutoGenerateConfig(
                            scopeMode = scopeMode,
                            schoolId = schoolDocId,
                            className = selectedClassName,
                            section = selectedSection,
                            session = session,
                            keepExistingUnlockedCells = keepUnlocked,
                            timeLimitSeconds = timeLimitSeconds,
                            seed = parsedSeed
                        )
                    )
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("autogen_start_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("autogen_cancel_config_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AutoGenerateProgressDialog(
    progress: AutoGenerateProgress,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* User must tap Cancel */ },
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Generating timetable",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Generating Timetable…") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (progress.totalDemandCount > 0) {
                    val fraction = (progress.placedCount.toFloat() / progress.totalDemandCount.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = progress.statusText.ifBlank { "Searching valid slot assignments…" },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Pass #${progress.restartAttempt} • ${(progress.elapsedMs / 1000.0).let { "%.1fs".format(it) }} elapsed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("autogen_stop_button")
            ) {
                Text("Cancel Search")
            }
        }
    )
}

@Composable
fun AutoGenerateResultDialog(
    result: AutoGenerateResult,
    onApply: () -> Unit,
    onRegenerateNewSeed: (AutoGenerateConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val unplacedTotal = result.unplacedItems.sumOf { it.missingPeriods }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (unplacedTotal == 0) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = "Auto-generate result",
                tint = if (unplacedTotal == 0) PassGreen else AbsentAmber
            )
        },
        title = {
            Text(
                text = if (unplacedTotal == 0) "Timetable Generated" else "Partial Result ($unplacedTotal Unplaced)"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (unplacedTotal == 0) PassGreenContainer else AbsentAmberContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Placed ${result.totalPlacedPeriods} of ${result.totalRequestedPeriods} periods",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "New/updated cells: ${result.newlyPlacedPeriods} • Kept locked/filled: ${result.untouchedLockedOrKeptCells.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Seed: ${result.config.seed} • ${result.restartsCompleted} pass(es) in ${result.elapsedMs} ms • Soft score penalty: ${result.softPenaltyScore}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (result.unplacedItems.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Unplaced Periods Report (${result.unplacedItems.size} item(s))",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    result.unplacedItems.forEach { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${item.className}-${item.section} • ${item.subjectName} (Missing ${item.missingPeriods}/${item.requestedPeriods})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Teacher: ${item.teacherName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = item.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                OutlinedButton(
                    onClick = {
                        val freshSeed = Random.nextLong(100_000L, 9_999_999L)
                        onRegenerateNewSeed(result.config.copy(seed = freshSeed))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("autogen_regenerate_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Regenerate with new seed (keeps locked cells)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("autogen_apply_button")
            ) {
                Text("Apply to Timetable")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("autogen_discard_button")
            ) {
                Text("Discard")
            }
        }
    )
}
