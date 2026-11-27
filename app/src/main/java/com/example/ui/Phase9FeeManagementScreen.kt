package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.StudentEntity
import com.example.domain.FeeMessageType
import com.example.domain.Phase9AccessEvaluator

@Composable
fun Phase9FeeManagementScreen(
    viewModel: SchoolViewModel,
    initialTab: Int = 0,
    preselectedStudent: StudentEntity? = null,
    initialQueueMessageType: FeeMessageType? = null,
    initialQueueMonthFilter: String? = null,
    initialQueueRouteIdFilter: String? = null,
    onQueueJumpConsumed: () -> Unit = {},
    onOpenStudentRegister: () -> Unit = {},
    onOpenTransport: () -> Unit = {},
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()

    val activeSchool = uiState.activeSchool
    val activeSchoolDocId = uiState.activeSchoolDocId
    val canEditFees = Phase9AccessEvaluator.canEditFees(uiState.accessProfile, activeSchoolDocId) || uiState.canEditFees

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }

    if (pdfState.isGenerating || pdfState.generatedFile != null || pdfState.error != null) {
        TimetablePdfPreviewDialog(
            pdfState = pdfState,
            onSavePdfToUri = viewModel::saveTimetablePdfToUri,
            onSaveZipToUri = viewModel::saveTimetableZipToUri,
            onDismiss = viewModel::clearTimetablePdfPreview
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("phase9_fee_management_screen")
    ) {
        // Two-row responsive Top Header Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("fees_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Fee Management",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("fees_header_title")
                    )
                    Text(
                        text = "${activeSchool.name} • Session ${uiState.filter.session} • Device Node ${uiState.deviceNodeCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (uiState.canViewStudentRegister) {
                OutlinedButton(
                    onClick = onOpenStudentRegister,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("fees_open_student_register_button")
                ) {
                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Student Register",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Multi-School Switcher Strip
        if (uiState.schools.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.schools.forEach { sch ->
                    FilterChip(
                        selected = sch.id == activeSchool.id,
                        onClick = { viewModel.selectSchool(sch.id) },
                        label = { Text(sch.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // View-Only Safety Banner
        if (!canEditFees) {
            Surface(
                color = Color(0xFFFFF8E1),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "View-Only Access: You can view student fee accounts, dues, and reports, but cannot issue or cancel fee receipts.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE65100)
                    )
                }
            }
        }

        // 4-Tab Navigation Row
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Collect Fees & Account") },
                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("fees_tab_collect")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Fee Structure") },
                icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("fees_tab_structure")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Reports & Dues") },
                icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("fees_tab_reports")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Send Queue & Notices") },
                icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("fees_tab_send_queue")
            )
        }

        when (selectedTab) {
            0 -> Phase9FeeCollectAndAccountTab(
                viewModel = viewModel,
                preselectedStudent = preselectedStudent,
                onOpenStudentRegister = onOpenStudentRegister,
                onOpenTransport = onOpenTransport
            )
            1 -> Phase9FeeStructureTab(
                viewModel = viewModel
            )
            2 -> Phase9FeeReportsTab(
                viewModel = viewModel
            )
            3 -> Phase9SendQueueAndNoticesTab(
                viewModel = viewModel,
                initialMessageType = initialQueueMessageType,
                initialMonthFilter = initialQueueMonthFilter,
                initialRouteIdFilter = initialQueueRouteIdFilter,
                onJumpConsumed = onQueueJumpConsumed
            )
        }
    }
}
