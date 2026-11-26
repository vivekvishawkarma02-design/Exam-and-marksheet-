package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.domain.BackupExportScope
import com.example.domain.BackupExportZipResult
import com.example.util.PdfExportHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Phase14BackupExportScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    val hi = uiState.phase10HindiLabels
    val context = LocalContext.current
    val reminder = uiState.backupReminderStatus
    val isOwner = uiState.canIncludeSalaryInBackup

    var selectedScope by remember { mutableStateOf(BackupExportScope.SCHOOL) }
    var isBuilding by remember { mutableStateOf(false) }
    var pendingSaveZip by remember { mutableStateOf<BackupExportZipResult?>(null) }
    var lastGeneratedZip by remember { mutableStateOf<BackupExportZipResult?>(null) }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val zipRes = pendingSaveZip ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(zipRes.zipBytes)
            }
            viewModel.emitMessage(p10Label(hi, "बैकअप ZIP सुरक्षित सहेजा गया।", "Backup ZIP saved successfully."))
        }.onFailure {
            viewModel.emitMessage(it.message ?: "Could not save backup ZIP.")
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("phase14_backup_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p10Label(hi, "बैकअप और एक्सपोर्ट", "Backup & Export"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.activeSchool.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Phase10LanguageToggleChip(
                        isHindi = hi,
                        onToggle = { viewModel.togglePhase10HindiLabels() }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (!uiState.canUseBackupExport) {
            BoxNoAccessPhase14(
                message = p10Label(
                    hi,
                    "डेटा बैकअप और एक्सपोर्ट केवल संस्था के स्वामी (Owner) और एडमिन (Admin) के लिए उपलब्ध है।",
                    "Data Backup & Export is available only to the Owner and Admins."
                )
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Last Backup Status + Cloud Reassurance
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (reminder.isOverdue30Days) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = p10Label(hi, reminder.headlineHi, reminder.headlineEn),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = p10Label(hi, reminder.subtextHi, reminder.subtextEn),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Scope Picker + What's Included
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = p10Label(hi, "बैकअप का दायरा चुनें", "Choose Backup Scope"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BackupExportScope.entries.forEach { scope ->
                                FilterChip(
                                    selected = selectedScope == scope,
                                    onClick = { selectedScope = scope },
                                    label = { Text(p10Label(hi, scope.labelHi, scope.labelEn)) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("phase14_backup_scope_${scope.wireValue.lowercase(Locale.US)}")
                                )
                            }
                        }

                        Text(
                            text = p10Label(
                                hi,
                                "ZIP फ़ाइल में शामिल Excel-रेडी CSV तालिकाएँ:\n• README.txt, students.csv, guardians.csv, class_structure.csv\n• fee_receipts.csv, fee_dues.csv, attendance_summary.csv, marks.csv\n• teachers.csv, cash_book.csv, certificates_register.csv",
                                "Excel-ready CSV tables included in ZIP:\n• README.txt, students.csv, guardians.csv, class_structure.csv\n• fee_receipts.csv, fee_dues.csv, attendance_summary.csv, marks.csv\n• teachers.csv, cash_book.csv, certificates_register.csv"
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isOwner) {
                                    p10Label(hi, "Owner के रूप में staff_salary.csv भी शामिल होगी।", "Owner export: staff_salary.csv is included.")
                                } else {
                                    p10Label(hi, "Admin एक्सपोर्ट में staff_salary.csv शामिल नहीं होती (केवल Owner)।", "Admin export: staff_salary.csv is excluded (Owner only).")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                enabled = !isBuilding,
                                onClick = {
                                    isBuilding = true
                                    viewModel.buildBackupExportZip(selectedScope) { res ->
                                        isBuilding = false
                                        res.onSuccess { zipRes ->
                                            lastGeneratedZip = zipRes
                                            pendingSaveZip = zipRes
                                            saveZipLauncher.launch(zipRes.fileName)
                                        }.onFailure { err ->
                                            viewModel.emitMessage(err.message ?: "Backup failed.")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("phase14_save_backup_zip_btn")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(hi, "ZIP सहेजें", "Save ZIP"), maxLines = 1)
                            }
                            OutlinedButton(
                                enabled = !isBuilding,
                                onClick = {
                                    isBuilding = true
                                    viewModel.buildBackupExportZip(selectedScope) { res ->
                                        isBuilding = false
                                        res.onSuccess { zipRes ->
                                            lastGeneratedZip = zipRes
                                            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
                                            val file = File(dir, zipRes.fileName)
                                            file.writeBytes(zipRes.zipBytes)
                                            PdfExportHelper.shareFile(context, file, "application/zip", "Share Backup ZIP")
                                        }.onFailure { err ->
                                            viewModel.emitMessage(err.message ?: "Backup failed.")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("phase14_share_backup_zip_btn")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(hi, "ZIP शेयर करें", "Share ZIP"), maxLines = 1)
                            }
                        }

                        val generated = lastGeneratedZip
                        if (generated != null) {
                            Text(
                                text = "${generated.fileName} (${generated.includedFiles.size} files, ${generated.studentCount} students, ${generated.receiptCount} receipts)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Recent Backups History
            if (uiState.backupExportLogs.isNotEmpty()) {
                item {
                    Text(
                        text = p10Label(hi, "पिछले बैकअप", "Recent Manual Backups"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.backupExportLogs.take(8), key = { it.id }) { log ->
                    val dt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(log.exportedAtMs))
                    Card {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(log.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${log.scope.labelEn} • ${log.studentCount} students • ${log.receiptCount} receipts" +
                                    if (log.includedSalaryCsv) " • incl. Salary" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("$dt • ${log.actorName} (${log.actorRole})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
