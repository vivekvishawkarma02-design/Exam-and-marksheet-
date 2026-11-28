package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.ClassReportReadinessResult
import com.example.domain.ReportType
import com.example.domain.SlipLayoutMode
import com.example.ui.theme.AbsentAmber
import com.example.util.PdfExportHelper

private enum class PendingBatchAction {
    COMBINED_PDF,
    CLASS_ZIP
}

@Composable
fun ReportsScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    val context = LocalContext.current
    val pdfState by viewModel.pdfState.collectAsStateWithLifecycle()
    val adsUiState by viewModel.adsUiState.collectAsStateWithLifecycle()

    var selectedReportType by remember { mutableStateOf(ReportType.ANNUAL) }
    var batchWholeClass by remember { mutableStateOf(false) }
    var slipLayoutMode by remember { mutableStateOf(SlipLayoutMode.TWO_PER_PAGE) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var readinessWarningDialog by remember { mutableStateOf<Pair<ClassReportReadinessResult, PendingBatchAction>?>(null) }

    LaunchedEffect(pdfState.previewPages.size) {
        currentPageIndex = 0
    }

    LaunchedEffect(selectedReportType) {
        if (selectedReportType != ReportType.HALF_YEARLY && slipLayoutMode == SlipLayoutMode.PAIR_UT_HY) {
            slipLayoutMode = SlipLayoutMode.TWO_PER_PAGE
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.saveGeneratedPdfToUri(uri)
        }
    }

    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.saveGeneratedZipToUri(uri)
        }
    }

    val students = uiState.filteredStudents
    val selectedStudent = uiState.selectedStudent
    val activeSchool = uiState.activeSchool
    val activeClassName = uiState.effectiveClassName
    val availableSlipModes = remember(selectedReportType) {
        if (selectedReportType == ReportType.HALF_YEARLY) {
            listOf(SlipLayoutMode.TWO_PER_PAGE, SlipLayoutMode.ONE_PER_PAGE, SlipLayoutMode.PAIR_UT_HY)
        } else {
            listOf(SlipLayoutMode.TWO_PER_PAGE, SlipLayoutMode.ONE_PER_PAGE)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("reports_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                SchoolLogoAvatar(school = activeSchool, size = 46.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "A4 Report Cards & Export",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${activeSchool.name}  •  Class ${activeClassName.ifBlank { "—" }}-${uiState.filter.section}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Scope & Student Selector Card
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
                    SessionClassSectionBar(
                        uiState = uiState,
                        onSessionSelected = viewModel::selectSession,
                        onClassSelected = viewModel::selectClass,
                        onSectionSelected = viewModel::selectSection
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !batchWholeClass,
                            onClick = { batchWholeClass = false },
                            label = { Text("One Student") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("scope_single")
                        )
                        FilterChip(
                            selected = batchWholeClass,
                            onClick = { batchWholeClass = true },
                            label = { Text("Whole Class (${students.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("scope_batch")
                        )
                    }

                    if (!batchWholeClass) {
                        val options = students.map { "Roll ${it.rollNo} • ${it.name}" }
                        val currentLabel = selectedStudent?.let { "Roll ${it.rollNo} • ${it.name}" }
                            ?: "No students in section"
                        CompactDropdownSelector(
                            label = "Student",
                            selectedValue = currentLabel,
                            options = options.ifEmpty { listOf("No students in section") },
                            onSelect = { chosen ->
                                val idx = options.indexOf(chosen)
                                if (idx in students.indices) viewModel.selectStudent(students[idx].id)
                            }
                        )
                    } else {
                        Text(
                            text = "Export all ${students.size} student(s) in Roll No. order as one combined A4 PDF or a Class ZIP (1 PDF per student).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Report Type & Layout Selection Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select Report Type",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ReportType.entries.forEach { type ->
                        val selected = selectedReportType == type
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReportType = type }
                                .testTag("report_type_${type.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = type.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = type.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (selectedReportType.isLandscape) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "A4 Landscape Slip Layout",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableSlipModes.forEach { mode ->
                                FilterChip(
                                    selected = slipLayoutMode == mode,
                                    onClick = { slipLayoutMode = mode },
                                    label = {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("slip_layout_${mode.name}")
                                )
                            }
                        }
                        Text(
                            text = slipLayoutMode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (batchWholeClass && uiState.activeSchoolCheckReportReady) {
                                val check = uiState.checkCurrentClassReportReadiness(selectedReportType)
                                if (!check.isReady) {
                                    readinessWarningDialog = check to PendingBatchAction.COMBINED_PDF
                                    return@Button
                                }
                            }
                            viewModel.generatePdfReport(
                                reportType = selectedReportType,
                                batchWholeClass = batchWholeClass,
                                slipLayoutMode = slipLayoutMode
                            )
                        },
                        enabled = !pdfState.isGenerating && students.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (pdfState.isGenerating) {
                                pdfState.statusLabel.ifEmpty { "Generating A4 PDF…" }
                            } else if (batchWholeClass) {
                                "Generate Combined Class PDF"
                            } else {
                                "Generate & Preview A4 PDF"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (batchWholeClass) {
                        OutlinedButton(
                            onClick = {
                                if (uiState.activeSchoolCheckReportReady) {
                                    val check = uiState.checkCurrentClassReportReadiness(selectedReportType)
                                    if (!check.isReady) {
                                        readinessWarningDialog = check to PendingBatchAction.CLASS_ZIP
                                        return@OutlinedButton
                                    }
                                }
                                viewModel.generateBatchClassZip(
                                    reportType = selectedReportType,
                                    slipLayoutMode = slipLayoutMode,
                                    onReady = { /* ZIP ready actions shown below */ }
                                )
                            },
                            enabled = !pdfState.isGenerating && students.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("generate_class_zip_button")
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generate Class ZIP (1 PDF per Student)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (pdfState.isGenerating) {
                        val progress = if (pdfState.progressTotal > 0) {
                            pdfState.progressCurrent.toFloat() / pdfState.progressTotal.toFloat()
                        } else 0f
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = "${pdfState.statusLabel.ifEmpty { "Rendering" }} (${pdfState.progressCurrent} of ${pdfState.progressTotal})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Class ZIP Ready Action Strip
                    val generatedZip = pdfState.generatedZipFile
                    if (generatedZip != null && !pdfState.isGenerating) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Class ZIP Ready: ${generatedZip.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { saveZipLauncher.launch(generatedZip.name) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("save_zip_button")
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save ZIP", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            PdfExportHelper.shareFile(
                                                context = context,
                                                file = generatedZip,
                                                mimeType = "application/zip",
                                                chooserTitle = "Share Class Report Cards ZIP"
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("share_zip_button")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share ZIP", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live A4 Preview + Print / Save / Share Action Bar
        val generatedFile = pdfState.generatedFile
        if (generatedFile != null && pdfState.previewPages.isNotEmpty()) {
            item {
                val isLandscapePreview = pdfState.reportType.isLandscape
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isLandscapePreview) "A4 Landscape Print Preview" else "A4 Portrait Print Preview",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${pdfState.reportType.label}  •  ${pdfState.totalPages} A4 page(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (pdfState.previewPages.size > 1) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                        enabled = currentPageIndex > 0
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                                    }
                                    Text(
                                        text = "${currentPageIndex + 1}/${pdfState.previewPages.size}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { if (currentPageIndex < pdfState.previewPages.lastIndex) currentPageIndex++ },
                                        enabled = currentPageIndex < pdfState.previewPages.lastIndex
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                                    }
                                }
                            }
                        }

                        // A4 Paper Preview Box (842/595 Landscape for slips, 595/842 Portrait for Annual)
                        val pageBitmap = pdfState.previewPages.getOrNull(currentPageIndex)
                        if (pageBitmap != null && !pageBitmap.isRecycled) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                shadowElevation = 4.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(if (isLandscapePreview) 842f / 595f else 595f / 842f)
                            ) {
                                Image(
                                    bitmap = pageBitmap.asImageBitmap(),
                                    contentDescription = "A4 report preview page ${currentPageIndex + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Print / Save PDF / Share Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    PdfExportHelper.printPdfFile(
                                        context = context,
                                        file = generatedFile,
                                        jobName = generatedFile.nameWithoutExtension,
                                        isLandscape = isLandscapePreview
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("print_pdf_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Print", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { savePdfLauncher.launch(generatedFile.name) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("save_pdf_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    PdfExportHelper.shareFile(
                                        context = context,
                                        file = generatedFile,
                                        mimeType = "application/pdf",
                                        chooserTitle = "Share Report Card PDF"
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_pdf_button"),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    // Phase 16B: ONE adaptive banner at bottom of Report list only (strictly hidden while PDF preview is open or generating)
    if (pdfState.previewPages.isEmpty() && !pdfState.isGenerating && readinessWarningDialog == null) {
        AdaptiveAdBannerSlot(
            eligibility = adsUiState.bannerEligibility(com.example.domain.AdScreenSurface.REPORT_LIST),
            canRequestAds = adsUiState.canRequestAdsFromConsent,
            bannerUnitId = adsUiState.unitIds.bannerId,
            testTagPrefix = "report_list"
        )
    }
    }

    // Phase 7 Optional "Report Cards Ready" Warning Dialog before batch export
    readinessWarningDialog?.let { (result, action) ->
        AlertDialog(
            onDismissRequest = { readinessWarningDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Incomplete report cards warning",
                    tint = AbsentAmber
                )
            },
            title = {
                Text(
                    text = "Report Cards Not Ready (${result.issues.size} of ${result.totalStudents})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${result.readyStudents} student(s) ready, ${result.issues.size} student(s) in Class ${result.className}-${result.section} are missing data for ${result.reportType.label}:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    result.issues.forEach { issue ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Roll ${issue.rollNo} • ${issue.studentName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = issue.summaryLine(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        readinessWarningDialog = null
                        when (action) {
                            PendingBatchAction.COMBINED_PDF -> viewModel.generatePdfReport(
                                reportType = selectedReportType,
                                batchWholeClass = true,
                                slipLayoutMode = slipLayoutMode
                            )
                            PendingBatchAction.CLASS_ZIP -> viewModel.generateBatchClassZip(
                                reportType = selectedReportType,
                                slipLayoutMode = slipLayoutMode,
                                onReady = {}
                            )
                        }
                    },
                    modifier = Modifier.testTag("readiness_export_anyway_button")
                ) {
                    Text("Export Anyway")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { readinessWarningDialog = null },
                    modifier = Modifier.testTag("readiness_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
