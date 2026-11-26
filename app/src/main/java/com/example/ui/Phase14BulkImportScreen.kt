package com.example.ui

import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.domain.BulkImportExecutionReport
import com.example.domain.BulkImportPreviewRow
import com.example.domain.BulkStudentImportEngine
import com.example.domain.ImportRowAction
import com.example.domain.ImportRowEditableValues
import com.example.domain.ParsedTabularFile
import com.example.domain.StudentImportField
import com.example.util.PdfExportHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Phase14BulkImportScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit
) {
    val hi = uiState.phase10HindiLabels
    val context = LocalContext.current
    val activeSchool = uiState.activeSchool
    val session = uiState.filter.session
    val validClassNames = remember(uiState.schoolClasses, activeSchool.id) {
        uiState.schoolClasses.filter { it.schoolId == activeSchool.id }.sortedBy { it.sortOrder }.map { it.name }
    }
    val existingInSession = remember(uiState.allStudents, activeSchool.id, session, uiState.studentDocIdByLongId) {
        uiState.allStudents
            .filter { it.schoolId == activeSchool.id && it.session.equals(session, ignoreCase = true) }
            .map { (uiState.studentDocIdByLongId[it.id] ?: "stu_${it.id}") to it }
    }

    var parsedFile by remember { mutableStateOf<ParsedTabularFile?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var columnMapping by remember { mutableStateOf<Map<StudentImportField, Int>>(emptyMap()) }
    var updateMatchingAdmNo by remember { mutableStateOf(false) }
    var editedOverrides by remember { mutableStateOf<Map<Int, ImportRowEditableValues>>(emptyMap()) }
    var skippedRowNumbers by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var editingRow by remember { mutableStateOf<BulkImportPreviewRow?>(null) }
    var executionReport by remember { mutableStateOf<BulkImportExecutionReport?>(null) }
    var filterProblemsOnly by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "students.csv"
        parseError = null
        executionReport = null
        viewModel.parseStudentImportFileUri(context, uri, displayName) { res ->
            res.onSuccess { parsed ->
                parsedFile = parsed
                columnMapping = BulkStudentImportEngine.autoMapColumns(parsed.headers)
                editedOverrides = emptyMap()
                skippedRowNumbers = emptySet()
            }.onFailure { err ->
                parseError = err.message ?: "Could not read file."
            }
        }
    }

    val sampleSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(BulkStudentImportEngine.buildSampleCsv().toByteArray(Charsets.UTF_8))
            }
            viewModel.emitMessage(p10Label(hi, "सैंपल CSV फ़ाइल सहेजी गई।", "Sample CSV file saved."))
        }.onFailure {
            viewModel.emitMessage(it.message ?: "Could not save sample CSV.")
        }
    }

    val previewSummary = remember(
        parsedFile,
        columnMapping,
        validClassNames,
        existingInSession,
        updateMatchingAdmNo,
        editedOverrides,
        skippedRowNumbers
    ) {
        val pf = parsedFile ?: return@remember null
        BulkStudentImportEngine.validateAndPreviewRows(
            rawRows = pf.rows,
            mapping = columnMapping,
            validClassNames = validClassNames,
            existingStudentsInSession = existingInSession,
            updateMatchingAdmNo = updateMatchingAdmNo,
            editedOverrides = editedOverrides,
            skippedRowNumbers = skippedRowNumbers
        )
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Back + Title + Subtitle (full width)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("phase14_import_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(hi, "बल्क छात्र इम्पोर्ट", "Bulk Student Import"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeSchool.name} • $session (CSV / Excel .xlsx)",
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
                    // Row 2: Two equal-width action buttons (>= 48dp height)
                    if (uiState.canUseBulkStudentImport) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { sampleSaver.launch("Sample_Students_Hindi_English.csv") },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("phase14_download_sample_btn")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(hi, "सैंपल CSV", "Sample CSV"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = {
                                    filePicker.launch(
                                        arrayOf(
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "text/plain",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("phase14_choose_import_file_btn")
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(p10Label(hi, "फ़ाइल चुनें", "Choose File"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (!uiState.canUseBulkStudentImport) {
            BoxNoAccessPhase14(
                message = p10Label(
                    hi,
                    "बल्क छात्र इम्पोर्ट केवल संस्था के स्वामी (Owner) और एडमिन (Admin) के लिए उपलब्ध है।",
                    "Bulk Student Import is available only to the Owner and Admins."
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
            // In-app Guide
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = p10Label(hi, "कैसे इम्पोर्ट करें (CSV या Excel .xlsx):", "How to import (CSV or Excel .xlsx):"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = p10Label(
                                hi,
                                "1. सैंपल CSV डाउनलोड करें और Excel/Sheets में छात्र भरें (हिन्दी या अंग्रेज़ी हेडर दोनों चलेंगे)।\n2. 'फ़ाइल चुनें' दबाएँ → कॉलम मिलाएँ → त्रुटियाँ ठीक करें या पंक्ति छोड़ें → इम्पोर्ट करें।\n3. मोबाइल नंबर अपने-आप +91 रूप में सही हो जाते हैं। मौजूदा छात्र तब तक नहीं बदलते जब तक आप 'मैचिंग प्रवेश संख्या अपडेट करें' चालू न करें।",
                                "1. Download Sample CSV and fill students in Excel/Sheets (Hindi & English headers supported).\n2. Tap 'Choose File' (.csv or .xlsx) → verify column mapping → fix or skip problem rows → Import.\n3. Mobile numbers auto-normalise to +91. Existing students are never overwritten unless you enable 'Update matching admission numbers'."
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (parseError != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = parseError!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Execution Result Report Card
            val rep = executionReport
            if (rep != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = p10Label(hi, "इम्पोर्ट रिपोर्ट तैयार", "Import Result Report"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text("Added: ${rep.addedCount}  •  Updated: ${rep.updatedCount}  •  Skipped: ${rep.skippedCount}  •  Errors: ${rep.errorCount}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = {
                                        val csv = BulkStudentImportEngine.buildImportResultCsv(rep)
                                        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
                                        val file = File(dir, "Bulk_Import_Report.csv")
                                        file.writeText(csv, Charsets.UTF_8)
                                        PdfExportHelper.shareFile(context, file, "text/csv", "Share Import Report CSV")
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("CSV")
                                }
                                Button(
                                    onClick = {
                                        viewModel.generateBulkImportReportPdfFile(context, rep) { pdfRes ->
                                            pdfRes.onSuccess { file ->
                                                PdfExportHelper.shareFile(context, file, "application/pdf", "Share Import Report PDF")
                                            }.onFailure { viewModel.emitMessage(it.message ?: "PDF error") }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(p10Label(hi, "PDF रिपोर्ट", "PDF Report"))
                                }
                            }
                        }
                    }
                }
            }

            // Column Mapping & Preview
            val pf = parsedFile
            val summary = previewSummary
            if (pf != null && summary != null) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "${pf.fileName} (${pf.format} • ${pf.rows.size} ${p10Label(hi, "पंक्तियाँ", "rows")})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = p10Label(hi, "कॉलम मैपिंग (बदलने के लिए टैप करें):", "Column Mapping (tap to adjust):"),
                                style = MaterialTheme.typography.labelLarge
                            )
                            StudentImportField.entries.forEach { field ->
                                ColumnMappingDropdownRow(
                                    field = field,
                                    hi = hi,
                                    headers = pf.headers,
                                    selectedIndex = columnMapping[field],
                                    onSelect = { newIdx ->
                                        val next = columnMapping.toMutableMap()
                                        if (newIdx == null) next.remove(field) else next[field] = newIdx
                                        columnMapping = next
                                    }
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = p10Label(hi, "मैचिंग प्रवेश संख्या अपडेट करें", "Update matching admission numbers"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = p10Label(
                                            hi,
                                            "चालू करने पर बदलावों का पूर्वावलोकन दिखेगा; बंद रखने पर पुराने छात्र सुरक्षित रहेंगे।",
                                            "Off by default so existing students are never overwritten. Turn on to preview changes."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = updateMatchingAdmNo,
                                    onCheckedChange = { updateMatchingAdmNo = it },
                                    modifier = Modifier.testTag("phase14_update_matching_switch")
                                )
                            }
                        }
                    }
                }

                // Summary bar + Commit button
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Add: ${summary.addCount}  •  Update: ${summary.updateCount}  •  Skip: ${summary.skipCount}  •  Errors: ${summary.errorCount}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !filterProblemsOnly,
                                    onClick = { filterProblemsOnly = false },
                                    label = { Text("${p10Label(hi, "सभी", "All")} (${summary.rows.size})") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                                FilterChip(
                                    selected = filterProblemsOnly,
                                    onClick = { filterProblemsOnly = true },
                                    label = { Text("${p10Label(hi, "समस्याएँ", "Problems")} (${summary.errorCount})") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.commitBulkStudentImportRows(
                                        session = session,
                                        fileName = pf.fileName,
                                        previewRows = summary.rows,
                                        updateMatchingEnabled = updateMatchingAdmNo
                                    ) { res ->
                                        res.onSuccess { r ->
                                            executionReport = r
                                            parsedFile = null
                                        }
                                    }
                                },
                                enabled = (summary.addCount + summary.updateCount) > 0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("phase14_commit_import_btn")
                            ) {
                                Text(
                                    p10Label(
                                        hi,
                                        "${summary.addCount + summary.updateCount} वैध छात्र इम्पोर्ट करें",
                                        "Import ${summary.addCount + summary.updateCount} Valid Student(s)"
                                    )
                                )
                            }
                        }
                    }
                }

                val rowsToShow = if (filterProblemsOnly) summary.rows.filter { it.action == ImportRowAction.ERROR } else summary.rows
                items(rowsToShow, key = { it.rowNumber }) { row ->
                    BulkImportPreviewRowCard(
                        row = row,
                        hi = hi,
                        onEdit = { editingRow = row },
                        onToggleSkip = {
                            skippedRowNumbers = if (row.rowNumber in skippedRowNumbers) {
                                skippedRowNumbers - row.rowNumber
                            } else {
                                skippedRowNumbers + row.rowNumber
                            }
                        }
                    )
                }
            }

            // Recent Import History
            if (uiState.bulkImportLogs.isNotEmpty()) {
                item {
                    Text(
                        text = p10Label(hi, "पिछले इम्पोर्ट", "Recent Bulk Imports"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.bulkImportLogs.take(5), key = { it.id }) { log ->
                    val dt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(log.importedAtMs))
                    Card {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(log.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${log.session} • +${log.addedCount} added, ${log.updatedCount} updated, ${log.skippedCount} skipped, ${log.errorCount} errors",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("$dt • ${log.actorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Inline Fix Row Dialog
    val editTarget = editingRow
    if (editTarget != null) {
        var name by remember(editTarget.rowNumber) { mutableStateOf(editTarget.values.name) }
        var cls by remember(editTarget.rowNumber) { mutableStateOf(editTarget.values.className) }
        var sec by remember(editTarget.rowNumber) { mutableStateOf(editTarget.values.section) }
        var adm by remember(editTarget.rowNumber) { mutableStateOf(editTarget.values.admNo) }
        var mobile by remember(editTarget.rowNumber) { mutableStateOf(editTarget.values.mobile) }
        var father by remember(editTarget.rowNumber) { mutableStateOf(editTarget.values.fatherName) }

        AlertDialog(
            onDismissRequest = { editingRow = null },
            title = { Text("${p10Label(hi, "पंक्ति सुधारें", "Fix Row")} #${editTarget.rowNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Student Name") }, singleLine = true)
                    OutlinedTextField(value = cls, onValueChange = { cls = it }, label = { Text("Class (${validClassNames.take(5).joinToString("/")})") }, singleLine = true)
                    OutlinedTextField(value = sec, onValueChange = { sec = it.uppercase(Locale.US) }, label = { Text("Section") }, singleLine = true)
                    OutlinedTextField(value = adm, onValueChange = { adm = it }, label = { Text("Admission No.") }, singleLine = true)
                    OutlinedTextField(value = father, onValueChange = { father = it }, label = { Text("Father Name") }, singleLine = true)
                    OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Guardian Mobile") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    editedOverrides = editedOverrides + (
                        editTarget.rowNumber to editTarget.values.copy(
                            name = name.trim(),
                            className = cls.trim(),
                            section = sec.trim().ifBlank { "A" },
                            admNo = adm.trim(),
                            fatherName = father.trim(),
                            mobile = mobile.trim()
                        )
                    )
                    skippedRowNumbers = skippedRowNumbers - editTarget.rowNumber
                    editingRow = null
                }) { Text(p10Label(hi, "सहेजें", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { editingRow = null }) { Text(p10Label(hi, "रद्द करें", "Cancel")) }
            }
        )
    }
}

@Composable
private fun ColumnMappingDropdownRow(
    field: StudentImportField,
    hi: Boolean,
    headers: List<String>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = p10Label(hi, field.labelHi, field.labelEn) + if (field.isRequired) " *" else "",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (field.isRequired) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(
                text = selectedIndex?.let { headers.getOrNull(it) } ?: p10Label(hi, "नहीं चुना", "Unmapped"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(p10Label(hi, "— खाली छोड़ें —", "— Skip / Unmapped —")) },
                    onClick = { onSelect(null); expanded = false }
                )
                headers.forEachIndexed { idx, h ->
                    DropdownMenuItem(
                        text = { Text(h.ifBlank { "Column ${idx + 1}" }) },
                        onClick = { onSelect(idx); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkImportPreviewRowCard(
    row: BulkImportPreviewRow,
    hi: Boolean,
    onEdit: () -> Unit,
    onToggleSkip: () -> Unit
) {
    val bg = when (row.action) {
        ImportRowAction.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        ImportRowAction.UPDATE -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        ImportRowAction.SKIP -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ImportRowAction.ADD -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    Card(colors = CardDefaults.cardColors(containerColor = bg)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "#${row.rowNumber} ${row.values.name.ifBlank { "(No name)" }} • ${row.values.className}-${row.values.section}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = row.action.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Adm: ${row.values.admNo.ifBlank { "Auto" }} • Mobile: ${row.normalizedMobile.ifBlank { row.values.mobile.ifBlank { "—" } }}",
                style = MaterialTheme.typography.bodySmall
            )
            row.problems.forEach { p ->
                Text("• ${p.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (row.action == ImportRowAction.UPDATE && row.updateDiffs.isNotEmpty()) {
                Text(
                    text = "${p10Label(hi, "बदलाव", "Changes")}: " + row.updateDiffs.joinToString("; ") { "${it.fieldLabel}: ${it.oldValue} → ${it.newValue}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(p10Label(hi, "सुधारें", "Fix"))
                }
                TextButton(onClick = onToggleSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        if (row.isSkippedByUser) p10Label(hi, "शामिल करें", "Include")
                        else p10Label(hi, "छोड़ें", "Skip")
                    )
                }
            }
        }
    }
}
