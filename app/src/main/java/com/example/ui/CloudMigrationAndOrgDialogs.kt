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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import com.example.data.cloud.CloudMarkEditLog
import com.example.data.cloud.CloudMembershipEntry
import com.example.data.cloud.LocalMigrationSummary
import com.example.data.cloud.MigrationProgress
import com.example.domain.AuthMethod
import com.example.domain.AuthorDisplayHelper
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
    isHindi: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon) = when (syncUiState.status) {
        SyncStatus.ONLINE -> Triple(PassGreenContainer, PassGreen, Icons.Default.CloudDone)
        SyncStatus.OFFLINE -> Triple(AbsentAmberContainer, AbsentAmber, Icons.Default.CloudOff)
        SyncStatus.SYNCING -> Triple(AbsentAmberContainer, AbsentAmber, Icons.Default.CloudSync)
        SyncStatus.ERROR -> Triple(FailRedContainer, FailRed, Icons.Default.ErrorOutline)
    }
    val label = if (isHindi) {
        when (syncUiState.status) {
            SyncStatus.ONLINE -> "ऑनलाइन"
            SyncStatus.OFFLINE -> if (syncUiState.pendingCount > 0) "ऑफ़लाइन (${syncUiState.pendingCount})" else "ऑफ़लाइन"
            SyncStatus.SYNCING -> if (syncUiState.pendingCount > 0) "सिंक (${syncUiState.pendingCount})" else "सिंक…"
            SyncStatus.ERROR -> "सिंक त्रुटि"
        }
    } else {
        syncUiState.chipLabel
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
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
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
    deletedUserUids: Set<String> = emptySet(),
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
                                        val resolvedEditor = AuthorDisplayHelper.resolveAuthorName(
                                            authorUid = entry.editorUid,
                                            storedName = entry.editorName,
                                            deletedUserUids = deletedUserUids
                                        )
                                        Text(
                                            text = "By $resolvedEditor",
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
    reauthMethod: AuthMethod,
    userEmail: String,
    isDeleting: Boolean,
    errorMessage: String?,
    onConfirmDeleteWithPassword: (password: String, deleteOwnedOrgsConfirmed: Boolean) -> Unit,
    onConfirmDeleteWithGoogle: (deleteOwnedOrgsConfirmed: Boolean) -> Unit,
    onTransferOwnershipClick: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val domainMemberships = remember(memberships) { memberships.map { it.toDomain() } }
    val ownedOrgs = remember(domainMemberships) { OrgAccessEvaluator.ownedActiveOrganisations(domainMemberships) }
    val isSoleOwner = ownedOrgs.isNotEmpty()

    var deleteWholeOrgConfirmed by remember { mutableStateOf(false) }
    var typedOrgConfirmation by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val ownerValidationError = remember(domainMemberships, deleteWholeOrgConfirmed, typedOrgConfirmation) {
        if (!isSoleOwner || !deleteWholeOrgConfirmed) null
        else OrgAccessEvaluator.validateOwnerAccountAndOrgDeletion(domainMemberships, typedOrgConfirmation)
    }

    val canSubmitDeletion = if (!isSoleOwner) {
        !isDeleting && (reauthMethod == AuthMethod.GOOGLE || password.isNotBlank())
    } else {
        !isDeleting &&
            deleteWholeOrgConfirmed &&
            ownerValidationError == null &&
            (reauthMethod == AuthMethod.GOOGLE || password.isNotBlank())
    }

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = FailRed)
                Text(
                    text = "Delete My Account",
                    style = MaterialTheme.typography.titleLarge,
                    color = FailRed
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = FailRedContainer,
                    border = BorderStroke(1.dp, FailRed.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "What WILL be deleted immediately:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = FailRed
                        )
                        Text(
                            text = "• Your Firebase sign-in login (${userEmail.ifBlank { "current user" }}) and user profile\n• All organisation memberships, role permissions, teacher scopes & pending join requests\n• Local offline cache and preferences stored on this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!isSoleOwner) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "What will NOT be deleted (unless an Owner deletes the organisation):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "• School records you created (student marks, attendance sheets, fee receipts, certificates, cash book entries & audit logs) remain in the organisation so school work is not broken.\n• Your author name on those historical entries will display as \"${AuthorDisplayHelper.DELETED_USER_LABEL}\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AbsentAmberContainer,
                        border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "You are the Owner of: ${ownedOrgs.joinToString(", ") { "${it.orgName} (@${it.orgCode})" }}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Choose one option before deleting your account:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (onTransferOwnershipClick != null) {
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onTransferOwnershipClick()
                                    },
                                    enabled = !isDeleting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("delete_account_transfer_ownership_button")
                                ) {
                                    Text("1. Transfer Ownership First (Recommended)")
                                }
                            } else {
                                Text(
                                    text = "1. Transfer Ownership (Recommended): Open your organisation → Members → select an Admin → Make Owner.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (deleteWholeOrgConfirmed) FailRedContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, if (deleteWholeOrgConfirmed) FailRed else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isDeleting) {
                                        deleteWholeOrgConfirmed = !deleteWholeOrgConfirmed
                                    }
                                    .testTag("delete_account_whole_org_checkbox_row")
                            ) {
                                Checkbox(
                                    checked = deleteWholeOrgConfirmed,
                                    onCheckedChange = { deleteWholeOrgConfirmed = it },
                                    enabled = !isDeleting,
                                    colors = CheckboxDefaults.colors(checkedColor = FailRed),
                                    modifier = Modifier.testTag("delete_account_whole_org_checkbox")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "2. Delete the whole organisation(s) & all data instead",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (deleteWholeOrgConfirmed) FailRed else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (deleteWholeOrgConfirmed) {
                                Text(
                                    text = "PERMANENT WARNING — EVERYTHING BELOW WILL BE DESTROYED FOREVER:\n• All schools, classes, teachers, timetables & students\n• All marks, co-scholastic grades & attendance sheets\n• All fee structures, receipts, certificates, cash book & salary records\n• All staff memberships & invite codes (Tip: Export a JSON backup in Settings first if needed)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FailRed
                                )
                                val expectedPrompt = if (ownedOrgs.size == 1) {
                                    "Type '${ownedOrgs.first().orgCode}' to confirm"
                                } else {
                                    "Type DELETE ALL ORGANISATIONS to confirm"
                                }
                                OutlinedTextField(
                                    value = typedOrgConfirmation,
                                    onValueChange = { typedOrgConfirmation = it },
                                    label = { Text(expectedPrompt) },
                                    singleLine = true,
                                    enabled = !isDeleting,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("delete_account_org_confirm_input")
                                )
                            }
                        }
                    }
                }

                if (!isSoleOwner || deleteWholeOrgConfirmed) {
                    if (reauthMethod == AuthMethod.EMAIL_PASSWORD) {
                        Text(
                            text = "Re-authentication required — enter your password:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Current Password") },
                            singleLine = true,
                            enabled = !isDeleting,
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password"
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_account_password_input")
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Re-authentication required: Tapping the button below asks you to confirm with your Google account before deletion.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = FailRedContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_account_error")
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = FailRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isSoleOwner || deleteWholeOrgConfirmed) {
                Button(
                    onClick = {
                        if (reauthMethod == AuthMethod.EMAIL_PASSWORD) {
                            onConfirmDeleteWithPassword(password, isSoleOwner && deleteWholeOrgConfirmed)
                        } else {
                            onConfirmDeleteWithGoogle(isSoleOwner && deleteWholeOrgConfirmed)
                        }
                    },
                    enabled = canSubmitDeletion,
                    colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_delete_account_button")
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Deleting…")
                    } else {
                        Text(
                            when {
                                isSoleOwner && reauthMethod == AuthMethod.GOOGLE -> "Verify Google & Delete Org + Account"
                                isSoleOwner -> "Verify & Delete Org + Account"
                                reauthMethod == AuthMethod.GOOGLE -> "Verify with Google & Delete"
                                else -> "Verify & Delete Account"
                            }
                        )
                    }
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if ((!isSoleOwner || deleteWholeOrgConfirmed) && !isDeleting) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
