package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.FailRedContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import com.example.util.BackupAndCsvManager

@Composable
fun MarksCsvImportController(
    uiState: CpiUiState,
    viewModel: CpiViewModel,
    showSetupDialog: Boolean,
    onDismissSetupDialog: () -> Unit
) {
    val pendingPreview by viewModel.pendingMarksCsvImport.collectAsStateWithLifecycle()

    val openCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onDismissSetupDialog()
            viewModel.prepareMarksCsvImportFromUri(uri)
        }
    }

    val saveTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportMarksCsvTemplateToUri(uri)
        }
    }

    val suggestedTemplateFileName =
        "Marks_Template_Class_${uiState.filter.className}_${uiState.filter.section}_${uiState.filter.session}.csv"

    if (showSetupDialog && pendingPreview == null) {
        AlertDialog(
            onDismissRequest = onDismissSetupDialog,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Import Marks from CSV",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Bulk-import Term-I & Term-II marks (IA 10 / UT 30 / Exam 60) for session ${uiState.filter.session}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Standardized CSV Columns",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = BackupAndCsvManager.MARKS_CSV_HEADER,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Text(
                        text = "• Matches students by Adm No. (or Class + Section + Roll No.).\n" +
                            "• Enter AB for absent; blank cells keep existing marks unchanged.\n" +
                            "• Download the pre-filled template for Class ${uiState.filter.className}-${uiState.filter.section} to edit in Excel or Google Sheets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Button(
                        onClick = {
                            openCsvLauncher.launch(
                                arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("choose_marks_csv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose Marks CSV File")
                    }

                    OutlinedButton(
                        onClick = { saveTemplateLauncher.launch(suggestedTemplateFileName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_marks_csv_template_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save CSV Template (${uiState.filter.className}-${uiState.filter.section})")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDismissSetupDialog,
                    modifier = Modifier.testTag("close_marks_csv_setup_button")
                ) {
                    Text("Close")
                }
            }
        )
    }

    val preview = pendingPreview
    if (preview != null) {
        val hasValid = preview.validMarks.isNotEmpty()
        val hasIssues = preview.issues.isNotEmpty()

        AlertDialog(
            onDismissRequest = viewModel::cancelPendingMarksCsvImport,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (hasValid) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (hasValid) PassGreen else FailRed
                    )
                    Text(
                        text = "Review Marks CSV Import",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasValid) PassGreenContainer else FailRedContainer,
                        border = BorderStroke(
                            1.dp,
                            if (hasValid) PassGreen.copy(alpha = 0.4f) else FailRed.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (hasValid) {
                                    "Ready to import ${preview.validMarks.size} mark(s)"
                                } else {
                                    "No valid marks found in this CSV"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasValid) PassGreen else FailRed
                            )
                            Text(
                                text = "Matched Students: ${preview.matchedStudentCount}  •  Rows Checked: ${preview.processedRowCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (preview.skippedEmptyCells > 0) {
                                Text(
                                    text = "${preview.skippedEmptyCells} blank cell(s) ignored (existing marks preserved).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (hasIssues) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AbsentAmberContainer,
                            border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${preview.issues.size} Warning(s) — skipped automatically",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4E2D00)
                                )
                                preview.issues.take(8).forEach { issue ->
                                    Text(
                                        text = "• ${issue.summary}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF6B4200)
                                    )
                                }
                                if (preview.issues.size > 8) {
                                    Text(
                                        text = "… and ${preview.issues.size - 8} more warning(s).",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF6B4200)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmMarksCsvImport,
                    enabled = hasValid,
                    modifier = Modifier.testTag("confirm_marks_csv_import_button")
                ) {
                    Text(if (hasValid) "Import ${preview.validMarks.size} Marks" else "Import")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelPendingMarksCsvImport,
                    modifier = Modifier.testTag("cancel_marks_csv_import_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
