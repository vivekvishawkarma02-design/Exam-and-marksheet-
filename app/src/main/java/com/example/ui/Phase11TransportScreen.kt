package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.Phase6DateUtils
import com.example.domain.TransportEngine
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.FailRed
import com.example.ui.theme.PassGreen
import com.example.util.ImageUtils

internal fun currentSessionMonthCode(todayIso: String = Phase6DateUtils.todayIso()): String {
    val mm = todayIso.drop(5).take(2)
    return when (mm) {
        "04" -> "APR"
        "05" -> "MAY"
        "06" -> "JUN"
        "07" -> "JUL"
        "08" -> "AUG"
        "09" -> "SEP"
        "10" -> "OCT"
        "11" -> "NOV"
        "12" -> "DEC"
        "01" -> "JAN"
        "02" -> "FEB"
        "03" -> "MAR"
        else -> "APR"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Phase11TransportScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    initialTab: Int? = null,
    onJumpConsumed: () -> Unit = {},
    onOpenSendQueueForTransportDues: (monthCode: String, routeIdFilter: String?) -> Unit = { _, _ -> },
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()
    val isHindi = uiState.phase10HindiLabels
    val schoolDocId = uiState.activeSchoolDocId
    val session = uiState.filter.session
    val canEditTransport = uiState.canEditTransport
    val canAssignStudents = uiState.canEditTransport || uiState.canEditStudentRegister

    var selectedTab by remember { mutableIntStateOf((initialTab ?: 0).coerceIn(0, 3)) }
    var jumpNeedsAttention by remember { mutableStateOf(false) }
    var jumpRouteId by remember { mutableStateOf<String?>(null) }
    var showMigrationDialog by remember { mutableStateOf(false) }
    var photoPreviewTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            selectedTab = initialTab.coerceIn(0, 3)
            onJumpConsumed()
        }
    }

    val allRoutesForSchool = remember(uiState.transportRoutes, schoolDocId) {
        uiState.transportRoutes
            .filter { it.schoolId == schoolDocId }
            .sortedBy { it.routeName.lowercase() }
    }
    val schoolStudents = remember(uiState.allStudents, uiState.activeSchool.id, session) {
        uiState.allStudents.filter {
            it.schoolId == uiState.activeSchool.id &&
                it.session.equals(session, ignoreCase = true) &&
                it.isActiveStatus
        }
    }
    val studentsWithDocIds = remember(schoolStudents, uiState.studentDocIdByLongId) {
        schoolStudents.map { st -> uiState.studentDocIdFor(st.id) to st }
    }
    val structure = remember(uiState.effectivePhase11FeeStructures, schoolDocId, session, uiState.activeSchoolClassNames) {
        uiState.feeStructureForPhase11(schoolDocId, session)
    }
    val overridesByStudentDocId = remember(uiState.effectivePhase11FeeOverrides, schoolDocId, session) {
        uiState.effectivePhase11FeeOverrides
            .filter { it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true) }
            .associateBy { it.studentId }
    }
    val receipts = remember(uiState.effectivePhase11FeeReceipts, schoolDocId, session) {
        uiState.effectivePhase11FeeReceipts.filter {
            it.schoolId == schoolDocId && it.session.equals(session, ignoreCase = true)
        }
    }

    val schoolSummary = remember(allRoutesForSchool, schoolStudents) {
        TransportEngine.computeSchoolTransportSummary(allRoutesForSchool, schoolStudents)
    }

    val currentMonthCode = remember(structure.monthOrder) {
        val cur = currentSessionMonthCode()
        if (structure.monthOrder.any { it.equals(cur, ignoreCase = true) }) cur
        else structure.monthOrder.firstOrNull() ?: "APR"
    }
    val thisMonthDuesSummary = remember(
        currentMonthCode,
        studentsWithDocIds,
        allRoutesForSchool,
        structure,
        overridesByStudentDocId,
        receipts
    ) {
        TransportEngine.computeTransportDuesForMonth(
            monthCode = currentMonthCode,
            studentsWithDocIds = studentsWithDocIds,
            routes = allRoutesForSchool,
            structure = structure,
            overridesByStudentDocId = overridesByStudentDocId,
            receipts = receipts
        )
    }

    val migrationItems = remember(schoolStudents, allRoutesForSchool, structure) {
        TransportEngine.findAndPrepareLegacyMigration(schoolStudents, allRoutesForSchool, structure)
    }

    if (pdfState.isGenerating || pdfState.generatedFile != null || pdfState.error != null) {
        TimetablePdfPreviewDialog(
            pdfState = pdfState,
            onSavePdfToUri = viewModel::saveTimetablePdfToUri,
            onSaveZipToUri = viewModel::saveTimetableZipToUri,
            onDismiss = viewModel::clearTimetablePdfPreview
        )
    }

    photoPreviewTarget?.let { (label, b64) ->
        val bmp = remember(b64) { ImageUtils.decodeBase64ToBitmap(b64) }
        AlertDialog(
            onDismissRequest = { photoPreviewTarget = null },
            title = { Text(label, fontWeight = FontWeight.Bold) },
            text = {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 380.dp)
                    )
                } else {
                    Text(p10Label(isHindi, "फोटो लोड नहीं हो सकी।", "Unable to decode photo."))
                }
            },
            confirmButton = {
                TextButton(onClick = { photoPreviewTarget = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(p10Label(isHindi, "बंद करें", "Close"))
                }
            }
        )
    }

    if (showMigrationDialog && migrationItems.isNotEmpty()) {
        LegacyTransportMigrationDialog(
            items = migrationItems,
            isHindi = isHindi,
            onDismiss = { showMigrationDialog = false },
            onConfirmMigrate = { setErr ->
                viewModel.applyLegacyTransportMigration(migrationItems) { ok, err ->
                    if (ok) showMigrationDialog = false else setErr(err ?: "Migration failed")
                }
            }
        )
    }

    Scaffold { innerPadding ->
        if (!uiState.canViewTransport) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        p10Label(
                            isHindi,
                            "आपको ट्रांसपोर्ट मॉड्यूल की अनुमति नहीं है। एडमिन से Transport अनुमति लें।",
                            "You do not have access to the Transport module. Ask your Admin for Transport access."
                        )
                    )
                    OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(p10Label(isHindi, "वापस", "Go Back"))
                    }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("phase11_transport_screen")
        ) {
            TransportTopHeader(
                schoolName = uiState.activeSchool.name,
                session = session,
                activeRouteCount = allRoutesForSchool.count { it.active },
                isHindi = isHindi,
                onBack = onBack,
                onToggleHindi = { viewModel.togglePhase10HindiLabels() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            TransportSummaryBar(
                totalTransportStudents = schoolSummary.totalTransportStudents,
                seatsUsed = schoolSummary.totalSeatsUsed,
                totalCapacity = schoolSummary.totalCapacity,
                expectedMonthlyIncomeRs = schoolSummary.totalExpectedMonthlyIncomeRs,
                monthCode = currentMonthCode,
                duesThisMonthRs = thisMonthDuesSummary.totalDueRs,
                isHindi = isHindi,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (migrationItems.isNotEmpty() && canEditTransport) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AbsentAmberContainer,
                    border = BorderStroke(1.dp, AbsentAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "${migrationItems.size} पुराने छात्रों का शुरुआती महीना सेट करें",
                                "${migrationItems.size} legacy student(s) need start month"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { showMigrationDialog = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("transport_open_migration_button")
                        ) {
                            Text(p10Label(isHindi, "समीक्षा करें", "Review"))
                        }
                    }
                }
            }

            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transport_tab_bar")
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(p10Label(isHindi, "रूट्स", "Routes"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.DirectionsBus, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("transport_tab_0")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(p10Label(isHindi, "छात्र", "Students"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("transport_tab_1")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(p10Label(isHindi, "बकाया", "Dues"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("transport_tab_2")
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text(p10Label(isHindi, "वाहन व खर्च", "Vehicles & Expenses"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("transport_tab_3")
                )
            }

            when (selectedTab) {
                0 -> TransportRoutesTab(
                    isHindi = isHindi,
                    canEdit = canEditTransport,
                    allRoutesForSchool = allRoutesForSchool,
                    schoolStudents = schoolStudents,
                    schoolSummary = schoolSummary,
                    structure = structure,
                    viewModel = viewModel,
                    onJumpToNeedsAttentionStudents = {
                        jumpNeedsAttention = true
                        jumpRouteId = null
                        selectedTab = 1
                    },
                    onJumpToAssignRouteStudents = { rtId ->
                        jumpNeedsAttention = false
                        jumpRouteId = rtId
                        selectedTab = 1
                    }
                )
                1 -> TransportStudentsTab(
                    isHindi = isHindi,
                    canAssignStudents = canAssignStudents,
                    allRoutesForSchool = allRoutesForSchool,
                    schoolStudents = schoolStudents,
                    schoolSummary = schoolSummary,
                    classNames = uiState.activeSchoolClassNames,
                    structure = structure,
                    viewModel = viewModel,
                    initialModeNeedsAttention = jumpNeedsAttention,
                    initialPreselectedRouteId = jumpRouteId,
                    onConsumeStudentFilters = {
                        jumpNeedsAttention = false
                        jumpRouteId = null
                    }
                )
                2 -> TransportDuesTab(
                    isHindi = isHindi,
                    canViewFees = uiState.canViewFees,
                    allRoutesForSchool = allRoutesForSchool,
                    studentsWithDocIds = studentsWithDocIds,
                    schoolSummary = schoolSummary,
                    classNames = uiState.activeSchoolClassNames,
                    structure = structure,
                    overridesByStudentDocId = overridesByStudentDocId,
                    receipts = receipts,
                    viewModel = viewModel,
                    onJumpToNeedsAttentionStudents = {
                        jumpNeedsAttention = true
                        jumpRouteId = null
                        selectedTab = 1
                    },
                    onOpenSendQueueForTransportDues = onOpenSendQueueForTransportDues
                )
                3 -> TransportVehiclesTab(
                    uiState = uiState,
                    isHindi = isHindi,
                    allRoutesForSchool = allRoutesForSchool,
                    studentsWithDocIds = studentsWithDocIds,
                    structure = structure,
                    overridesByStudentDocId = overridesByStudentDocId,
                    receipts = receipts,
                    viewModel = viewModel,
                    onPreviewPhotoBase64 = { title, b64 -> photoPreviewTarget = title to b64 }
                )
            }
        }
    }
}

@Composable
internal fun TransportTopHeader(
    schoolName: String,
    session: String,
    activeRouteCount: Int,
    isHindi: Boolean,
    onBack: () -> Unit,
    onToggleHindi: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Two-row header: NEVER squeezes title + language toggle/buttons into one horizontal Row
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("transport_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = p10Label(isHindi, "ट्रांसपोर्ट प्रबंधन", "Transport"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transport_header_title")
                )
                Text(
                    text = "$schoolName • $session • $activeRouteCount route(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transport_header_subtitle")
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Phase10LanguageToggleChip(
                isHindi = isHindi,
                onToggle = onToggleHindi,
                modifier = Modifier.testTag("transport_lang_toggle")
            )
        }
    }
}

@Composable
internal fun TransportSummaryBar(
    totalTransportStudents: Int,
    seatsUsed: Int,
    totalCapacity: Int,
    expectedMonthlyIncomeRs: Int,
    monthCode: String,
    duesThisMonthRs: Int,
    isHindi: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("transport_summary_bar")
    ) {
        // 2x2 KPI grid guarantees zero clipping even at 320dp + 1.5x font scale
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryKpiCell(
                    label = p10Label(isHindi, "ट्रांसपोर्ट छात्र", "Students"),
                    value = "$totalTransportStudents",
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SummaryKpiCell(
                    label = p10Label(isHindi, "सीट उपयोग", "Seats Used"),
                    value = "$seatsUsed/$totalCapacity",
                    valueColor = if (seatsUsed > totalCapacity && totalCapacity > 0) FailRed else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryKpiCell(
                    label = p10Label(isHindi, "अनुमानित मासिक आय", "Expected / Mo"),
                    value = "₹$expectedMonthlyIncomeRs",
                    valueColor = PassGreen,
                    modifier = Modifier.weight(1f)
                )
                SummaryKpiCell(
                    label = p10Label(isHindi, "इस माह बकाया ($monthCode)", "Dues ($monthCode)"),
                    value = "₹$duesThisMonthRs",
                    valueColor = if (duesThisMonthRs > 0) FailRed else PassGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryKpiCell(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
