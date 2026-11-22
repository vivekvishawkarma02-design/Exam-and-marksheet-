package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.cloud.CloudMarkEditLog
import com.example.data.cloud.CloudMembershipEntry
import com.example.data.cloud.LocalMigrationSummary
import com.example.data.cloud.MigrationProgress
import com.example.domain.MarkComponent
import com.example.domain.MarkConflictNotice
import com.example.domain.OfflineSyncEngine
import com.example.domain.OrgAccessEvaluator
import com.example.domain.SyncStatus
import com.example.domain.SyncUiState
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncStatusChip(
    status: SyncStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    SyncStatusChip(
        syncUiState = SyncUiState(status = status),
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun SyncStatusChip(
    syncUiState: SyncUiState,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon) = when (syncUiState.status) {
        SyncStatus.ONLINE -> Triple(PassGreenContainer, PassGreen, Icons.Default.CloudDone)
        SyncStatus.OFFLINE -> Triple(AbsentAmberContainer, AbsentAmber, Icons.Default.CloudOff)
        SyncStatus.SYNCING -> Triple(AbsentAmberContainer, AbsentAmber, Icons.Default.CloudSync)
        SyncStatus.ERROR -> Triple(FailRedContainer, FailRed, Icons.Default.ErrorOutline)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.4f)),
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .testTag("sync_status_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = syncUiState.chipLabel,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = syncUiState.chipLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OfflineActionNoticeBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AbsentAmberContainer,
        border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_action_notice_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "Offline",
                tint = AbsentAmber,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ConflictNoticeBanner(
    conflicts: List<MarkConflictNotice>,
    onViewLog: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (conflicts.isEmpty()) return
    Surface(
        color = AbsentAmberContainer,
        border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("mark_conflict_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Marks updated",
                tint = AbsentAmber,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Some of your changes were replaced by newer ones",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val first = conflicts.first()
                val summary = if (conflicts.size == 1) {
                    "${first.studentName} (${first.subjectName}): ${first.yourValue} → ${first.winningValue} by ${first.winnerName}"
                } else {
                    "${conflicts.size} mark entries were updated by a newer edit on another device."
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onViewLog,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("conflict_banner_view_log_button")
            ) {
                Text("View Log", fontWeight = FontWeight.Bold)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("conflict_banner_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss conflict notice",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SyncStatusDetailDialog(
    syncUiState: SyncUiState,
    onRetrySync: () -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit
) {
    val lastSyncedLabel = remember(syncUiState.lastSyncedAtMs) {
        if (syncUiState.lastSyncedAtMs > 0L) {
            SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.US).format(Date(syncUiState.lastSyncedAtMs))
        } else {
            "Waiting for server confirmation"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (syncUiState.status) {
                        SyncStatus.ONLINE -> Icons.Default.CloudDone
                        SyncStatus.OFFLINE -> Icons.Default.CloudOff
                        SyncStatus.SYNCING -> Icons.Default.CloudSync
                        SyncStatus.ERROR -> Icons.Default.ErrorOutline
                    },
                    contentDescription = null,
                    tint = when (syncUiState.status) {
                        SyncStatus.ONLINE -> PassGreen
                        SyncStatus.OFFLINE, SyncStatus.SYNCING -> AbsentAmber
                        SyncStatus.ERROR -> FailRed
                    }
                )
                Text(
                    text = "Offline & Cloud Sync",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = when (syncUiState.status) {
                        SyncStatus.ONLINE -> "Connected and up to date with Google Cloud Firestore."
                        SyncStatus.OFFLINE -> "You are offline. All marks, student edits, attendance, and co-scholastic changes save immediately on this phone and upload automatically in order when internet returns."
                        SyncStatus.SYNCING -> "Uploading ${syncUiState.pendingCount} pending change(s) to the cloud in order…"
                        SyncStatus.ERROR -> "A sync error occurred while uploading changes to the cloud."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                if (!syncUiState.errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = FailRedContainer,
                        border = BorderStroke(1.dp, FailRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = syncUiState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = FailRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Pending uploads: ${syncUiState.pendingCount}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Last cloud sync: $lastSyncedLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Offline disk cache: ${OfflineSyncEngine.FIRESTORE_CACHE_SIZE_MB} MB (Firestore single source of truth)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (syncUiState.status == SyncStatus.ERROR || syncUiState.pendingCount > 0) {
                Button(
                    onClick = {
                        onRetrySync()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("sync_dialog_retry_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry Sync")
                }
            } else {
                Button(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (syncUiState.status == SyncStatus.ERROR) {
                TextButton(
                    onClick = {
                        onClearError()
                        onDismiss()
                    }
                ) {
                    Text("Dismiss Error")
                }
            }
        }
    )
}

@Composable
fun StudentMarksHistoryDialog(
    title: String,
    logs: List<CloudMarkEditLog>,
    conflicts: List<MarkConflictNotice> = emptyList(),
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (conflicts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AbsentAmberContainer,
                        border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Replaced Offline Edits (Latest Edit Won)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AbsentAmber
                            )
                            conflicts.forEach { c ->
                                val compLabel = MarkComponent.fromKey(c.componentKey)?.shortLabel ?: c.componentKey
                                Text(
                                    text = "• ${c.studentName} — ${c.subjectName} ($compLabel): your '${c.yourValue}' → '${c.winningValue}' by ${c.winnerName}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No mark edits recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs, key = { it.id }) { entry ->
                            val compLabel = MarkComponent.fromKey(entry.componentKey)?.shortLabel ?: entry.componentKey
                            val timestampMs = entry.createdAt?.toDate()?.time?.takeIf { it > 0L } ?: entry.clientEditedAtMs
                            val timeText = if (timestampMs > 0L) dateFormat.format(Date(timestampMs)) else "Just now"
                            val oldDisplay = entry.oldValue.ifBlank { "—" }
                            val newDisplay = entry.newValue.ifBlank { "(cleared)" }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mark_edit_log_row_${entry.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${entry.subjectName} • $compLabel",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (entry.hasPendingWrites) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = AbsentAmberContainer
                                            ) {
                                                Text(
                                                    text = "Pending",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AbsentAmber,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (entry.studentName.isNotBlank()) {
                                        Text(
                                            text = "${entry.studentName}${if (entry.className.isNotBlank()) " (${entry.className})" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "$oldDisplay  →  $newDisplay",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "By ${entry.editorName.ifBlank { "Staff" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = timeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_marks_history_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun LocalDataMigrationDialog(
    orgName: String,
    summary: LocalMigrationSummary,
    progress: MigrationProgress,
    onStartUpload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!progress.isRunning) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Upload This Phone's Data", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Existing offline data found on this phone: ${summary.schoolCount} school(s), ${summary.studentCount} student(s), and ${summary.markCount} mark(s).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Upload everything into '$orgName' in batches. Your local phone data stays untouched as a safety copy, and running this upload again never creates duplicates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (progress.isRunning || progress.finished || progress.error != null) {
                    val fraction = if (progress.totalSteps > 0) {
                        (progress.completedSteps.toFloat() / progress.totalSteps.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = progress.error ?: progress.statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (progress.error != null) FailRed else MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            if (!progress.finished) {
                Button(
                    onClick = onStartUpload,
                    enabled = !progress.isRunning,
                    modifier = Modifier.testTag("start_local_migration_button")
                ) {
                    Text(if (progress.isRunning) "Uploading…" else if (progress.error != null) "Retry Upload" else "Upload to Cloud")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            if (!progress.isRunning && !progress.finished) {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        }
    )
}

@Composable
fun DeleteOrganisationDialog(
    orgName: String,
    orgCode: String,
    backupExported: Boolean,
    suggestedBackupFileName: String,
    onExportBackupToUri: (android.net.Uri) -> Unit,
    onConfirmDelete: (typedCode: String) -> Unit,
    onDismiss: () -> Unit
) {
    var typedCode by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportBackupToUri(uri)
    }

    val codeMatches = OrgAccessEvaluator.normalizeOrgCode(typedCode) == OrgAccessEvaluator.normalizeOrgCode(orgCode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = FailRed)
                Text("Delete Organisation?", style = MaterialTheme.typography.titleLarge, color = FailRed)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Deleting '$orgName' permanently removes all its schools, students, marks, members, and releases '@$orgCode'.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (backupExported) PassGreenContainer else AbsentAmberContainer
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (backupExported) Icons.Default.CheckCircle else Icons.Default.Download,
                                contentDescription = null,
                                tint = if (backupExported) PassGreen else AbsentAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (backupExported) "Step 1 complete: JSON backup exported" else "Step 1 (Required): Export JSON backup first",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (backupExported) PassGreen else AbsentAmber
                            )
                        }
                        OutlinedButton(
                            onClick = { exportLauncher.launch(suggestedBackupFileName) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_org_export_backup_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (backupExported) "Export Backup Again" else "Export Backup JSON")
                        }
                    }
                }
                OutlinedTextField(
                    value = typedCode,
                    onValueChange = { typedCode = it },
                    label = { Text("Step 2: Type '$orgCode' to confirm") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_org_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(typedCode) },
                enabled = backupExported && codeMatches,
                colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                modifier = Modifier.testTag("confirm_delete_org_button")
            ) {
                Text("Delete Forever")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteAccountDialog(
    memberships: List<CloudMembershipEntry>,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val guardError = remember(memberships) {
        OrgAccessEvaluator.canDeleteOwnAccount(memberships.map { it.toDomain() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete My Account", style = MaterialTheme.typography.titleLarge, color = FailRed) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (guardError != null) {
                    Surface(shape = RoundedCornerShape(10.dp), color = FailRedContainer) {
                        Text(
                            text = guardError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FailRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = "Open that organisation, go to Members to transfer ownership to another Admin, or go to Settings to export a backup and delete the organisation first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "This will permanently delete your account, remove your personal profile and memberships, and clear cached data on this phone. This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            if (guardError == null) {
                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    Text("Delete Account")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Understood")
                }
            }
        },
        dismissButton = {
            if (guardError == null) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
