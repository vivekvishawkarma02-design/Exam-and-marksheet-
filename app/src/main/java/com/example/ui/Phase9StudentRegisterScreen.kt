package com.example.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.StudentEntity
import com.example.domain.AdScreenSurface
import com.example.domain.AppModule
import com.example.domain.Phase6DateUtils
import com.example.domain.Phase9AccessEvaluator
import com.example.domain.StudentRegisterEngine
import com.example.domain.StudentRegisterStatus
import com.example.domain.StudentRegisterStatusFilter
import com.example.domain.StudentTransportFilter
import com.example.domain.TransportEngine
import com.example.domain.TransportRouteRecord
import com.example.domain.TransportTripType
import com.example.util.ImageUtils
import kotlinx.coroutines.launch

@Composable
fun StudentRegisterHeader(
    schoolName: String,
    session: String,
    canEditRegister: Boolean,
    idCardSelectionMode: Boolean,
    selectedIdCardsCount: Int,
    onBack: () -> Unit,
    onPrintIdCards: () -> Unit,
    onAddStudent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("register_header"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Back arrow + full-width title and ellipsized single-line subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("register_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Student Register",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_header_title")
                )
                Text(
                    text = "$schoolName • Session $session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_header_subtitle")
                )
            }
        }

        // Row 2: Equal-width action buttons (>= 48dp tall, single-line labels)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_header_actions_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPrintIdCards,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("register_print_id_cards_button")
            ) {
                Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (idCardSelectionMode && selectedIdCardsCount > 0) {
                        "ID Cards ($selectedIdCardsCount)"
                    } else {
                        "ID Cards"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (canEditRegister) {
                Button(
                    onClick = onAddStudent,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("register_add_student_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Student",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun StudentRegisterEmptyState(
    hasAnyStudentsInSession: Boolean,
    canEditRegister: Boolean,
    onAddFirstStudent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("register_empty_state_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (!hasAnyStudentsInSession) {
                    "Add First Student"
                } else {
                    "No students match the current filter."
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("register_empty_state_title")
            )
            Text(
                text = if (!hasAnyStudentsInSession) {
                    if (canEditRegister) {
                        "Add your first student to the register, or use Bulk Import for CSV and Excel files."
                    } else {
                        "No students have been added to this school yet (view-only access)."
                    }
                } else {
                    "Adjust search/filter chips or add a new student to the register."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canEditRegister) {
                Button(
                    onClick = onAddFirstStudent,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("register_add_first_student_button")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add First Student",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Phase9StudentRegisterScreen(
    viewModel: SchoolViewModel,
    onBack: () -> Unit,
    onOpenFeeCollectionForStudent: (StudentEntity) -> Unit = {},
    onOpenCertificatesForStudent: (StudentEntity) -> Unit = {},
    onOpenCoScholasticForStudent: (StudentEntity) -> Unit = {},
    onNavigateToTransport: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()
    val adsUiState by viewModel.adsUiState.collectAsStateWithLifecycle()

    val activeSchool = uiState.activeSchool
    val activeSchoolDocId = uiState.activeSchoolDocId
    val canEditRegister = Phase9AccessEvaluator.canEditStudentRegister(
        uiState.accessProfile,
        activeSchoolDocId
    ) || uiState.canEditStudentRegister
    val canViewCoScholastic = uiState.canView(AppModule.CO_SCHOLASTIC)

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<StudentRegisterStatus?>(StudentRegisterStatus.ACTIVE) }
    var selectedClassFilter by remember { mutableStateOf<String?>(null) }
    var selectedSectionFilter by remember { mutableStateOf<String?>(null) }
    var transportFilter by remember { mutableStateOf<Boolean?>(null) }
    var siblingGroupFilter by remember { mutableStateOf<String?>(null) }

    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var deletingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var classChangeTarget by remember { mutableStateOf<StudentEntity?>(null) }
    var statusChangeTarget by remember { mutableStateOf<StudentEntity?>(null) }
    var siblingLinkTarget by remember { mutableStateOf<StudentEntity?>(null) }
    var historyTarget by remember { mutableStateOf<StudentEntity?>(null) }
    var pickupTarget by remember { mutableStateOf<StudentEntity?>(null) }
    var remarksTarget by remember { mutableStateOf<StudentEntity?>(null) }

    val selectedIdsForIdCards = remember { mutableStateListOf<Long>() }
    var idCardSelectionMode by remember { mutableStateOf(false) }

    val schoolStudents = remember(uiState.allStudents, activeSchool.id, uiState.filter.session) {
        uiState.allStudents.filter {
            it.schoolId == activeSchool.id && it.session.equals(uiState.filter.session, ignoreCase = true)
        }
    }

    val filteredStudents = remember(
        schoolStudents,
        searchQuery,
        statusFilter,
        selectedClassFilter,
        selectedSectionFilter,
        transportFilter,
        siblingGroupFilter
    ) {
        StudentRegisterEngine.filterStudents(
            students = schoolStudents,
            searchQuery = searchQuery,
            classNameFilter = selectedClassFilter,
            sectionFilter = selectedSectionFilter,
            statusFilter = when (statusFilter) {
                StudentRegisterStatus.ACTIVE -> StudentRegisterStatusFilter.ACTIVE_ONLY
                StudentRegisterStatus.LEFT -> StudentRegisterStatusFilter.LEFT_ONLY
                null -> StudentRegisterStatusFilter.ALL
            },
            transportFilter = when (transportFilter) {
                true -> StudentTransportFilter.TRANSPORT_USERS
                false -> StudentTransportFilter.NON_TRANSPORT
                null -> StudentTransportFilter.ALL
            },
            siblingGroupIdFilter = siblingGroupFilter
        )
    }

    val activeCount = remember(schoolStudents) { schoolStudents.count { it.isActiveStatus } }
    val leftCount = remember(schoolStudents) { schoolStudents.count { !it.isActiveStatus } }
    val transportCount = remember(schoolStudents) { schoolStudents.count { it.isActiveStatus && it.usesTransport } }

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
            .testTag("phase9_student_register_screen")
    ) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Two-row responsive header bar
        StudentRegisterHeader(
            schoolName = activeSchool.name,
            session = uiState.filter.session,
            canEditRegister = canEditRegister,
            idCardSelectionMode = idCardSelectionMode,
            selectedIdCardsCount = selectedIdsForIdCards.size,
            onBack = onBack,
            onPrintIdCards = {
                if (idCardSelectionMode && selectedIdsForIdCards.isNotEmpty()) {
                    val chosen = filteredStudents.filter { it.id in selectedIdsForIdCards }
                    viewModel.generateStudentIdCardsPdf(chosen, "Selected (${chosen.size})")
                } else {
                    val activeFiltered = filteredStudents.filter { it.isActiveStatus }
                    viewModel.generateStudentIdCardsPdf(
                        activeFiltered,
                        selectedClassFilter?.let { "Class $it" } ?: "All Filtered"
                    )
                }
            },
            onAddStudent = {
                editingStudent = null
                showAddEditDialog = true
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Summary KPI strip + School Switcher if multi-school
        if (uiState.schools.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.schools.forEach { sch ->
                    FilterChip(
                        selected = sch.id == activeSchool.id,
                        onClick = { viewModel.selectSchool(sch.id) },
                        label = { Text(sch.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Search box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search name, admission no., mobile, route",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.testTag("register_search_clear_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status, Transport, ID Selection & Family filters (wrapping FlowRow for phone widths)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_filter_chips_flow"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = statusFilter == StudentRegisterStatus.ACTIVE,
                onClick = { statusFilter = StudentRegisterStatus.ACTIVE },
                label = { Text("Active ($activeCount)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("register_filter_active")
            )
            FilterChip(
                selected = statusFilter == StudentRegisterStatus.LEFT,
                onClick = { statusFilter = StudentRegisterStatus.LEFT },
                label = { Text("Left ($leftCount)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("register_filter_left")
            )
            FilterChip(
                selected = statusFilter == null,
                onClick = { statusFilter = null },
                label = { Text("All (${schoolStudents.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("register_filter_all")
            )
            FilterChip(
                selected = transportFilter == true,
                onClick = { transportFilter = if (transportFilter == true) null else true },
                leadingIcon = { Icon(Icons.Default.DirectionsBus, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Transport ($transportCount)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.testTag("register_filter_transport")
            )
            FilterChip(
                selected = idCardSelectionMode,
                onClick = {
                    idCardSelectionMode = !idCardSelectionMode
                    if (!idCardSelectionMode) selectedIdsForIdCards.clear()
                },
                label = {
                    Text(
                        text = if (idCardSelectionMode) "Cancel ID Select" else "Select for ID Cards",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.testTag("register_filter_select_id_cards")
            )
            if (siblingGroupFilter != null) {
                FilterChip(
                    selected = true,
                    onClick = { siblingGroupFilter = null },
                    leadingIcon = { Icon(Icons.Default.FamilyRestroom, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Family: $siblingGroupFilter ✕", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("register_filter_family")
                )
            }
        }

        // Class filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedClassFilter == null,
                onClick = {
                    selectedClassFilter = null
                    selectedSectionFilter = null
                },
                label = { Text("All Classes") }
            )
            uiState.activeSchoolClassNames.forEach { cls ->
                FilterChip(
                    selected = selectedClassFilter.equals(cls, ignoreCase = true),
                    onClick = {
                        selectedClassFilter = if (selectedClassFilter.equals(cls, ignoreCase = true)) null else cls
                        selectedSectionFilter = null
                    },
                    label = { Text(cls) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredStudents.isEmpty()) {
            StudentRegisterEmptyState(
                hasAnyStudentsInSession = schoolStudents.isNotEmpty(),
                canEditRegister = canEditRegister,
                onAddFirstStudent = {
                    editingStudent = null
                    showAddEditDialog = true
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 84.dp)
            ) {
                items(filteredStudents, key = { it.id }) { student ->
                    val siblings = remember(student.id, student.siblingGroupId, schoolStudents) {
                        StudentRegisterEngine.findSiblingsForStudent(student, schoolStudents)
                    }
                    val targetDocKey = uiState.resolveStudentDocKey(student)
                    val targetLegacyKey = student.id.toString()
                    val pickupList = remember(uiState.authorizedPickups, activeSchoolDocId, targetDocKey, targetLegacyKey) {
                        uiState.authorizedPickups.filter {
                            it.schoolId == activeSchoolDocId &&
                                (it.studentId == targetDocKey || it.studentId == targetLegacyKey)
                        }
                    }
                    val canViewStudentRemarks = remember(
                        activeSchoolDocId,
                        student.className,
                        student.section,
                        uiState.accessProfile,
                        uiState.effectiveOwnTeacherScope
                    ) {
                        viewModel.canViewStudentRemarksForClassSection(
                            schoolDocId = activeSchoolDocId,
                            className = student.className,
                            section = student.section
                        )
                    }
                    val studentRemarksCount = remember(
                        uiState.studentRemarks,
                        activeSchoolDocId,
                        targetDocKey,
                        targetLegacyKey,
                        canViewStudentRemarks
                    ) {
                        if (!canViewStudentRemarks) 0
                        else uiState.studentRemarks.count {
                            it.schoolId == activeSchoolDocId &&
                                (it.studentId == targetDocKey || it.studentId == targetLegacyKey) &&
                                it.isActive
                        }
                    }
                    StudentRegisterCard(
                        student = student,
                        transportRoutes = uiState.transportRoutes.filter { it.schoolId == activeSchoolDocId },
                        siblings = siblings,
                        canEditRegister = canEditRegister,
                        canViewFees = uiState.canViewFees,
                        canViewCoScholastic = canViewCoScholastic,
                        idCardSelectionMode = idCardSelectionMode,
                        isSelectedForIdCard = student.id in selectedIdsForIdCards,
                        onToggleIdCardSelect = {
                            if (student.id in selectedIdsForIdCards) {
                                selectedIdsForIdCards.remove(student.id)
                            } else {
                                selectedIdsForIdCards.add(student.id)
                            }
                        },
                        onEditStudent = {
                            editingStudent = student
                            showAddEditDialog = true
                        },
                        onChangeClassSection = { classChangeTarget = student },
                        onChangeStatus = { statusChangeTarget = student },
                        onDeleteStudent = { deletingStudent = student },
                        onLinkSiblings = { siblingLinkTarget = student },
                        onFilterBySiblingGroup = { grp -> siblingGroupFilter = grp },
                        onPrintAdmissionForm = { viewModel.generateStudentAdmissionFormPdf(student) },
                        onPrintSingleIdCard = { viewModel.generateStudentIdCardsPdf(listOf(student), student.name) },
                        onOpenFees = { onOpenFeeCollectionForStudent(student) },
                        onOpenCoScholastic = { onOpenCoScholasticForStudent(student) },
                        canViewCertificates = uiState.canViewCertificates,
                        onOpenCertificates = { onOpenCertificatesForStudent(student) },
                        onOpenSessionHistory = { historyTarget = student },
                        authorizedPickupsCount = pickupList.size,
                        onOpenAuthorizedPickups = { pickupTarget = student },
                        canViewRemarksForStudent = canViewStudentRemarks,
                        remarksCount = studentRemarksCount,
                        onOpenRemarks = { remarksTarget = student }
                    )
                }
            }
        }
    }

    // Phase 16B: ONE adaptive banner at bottom of the single Student Register list (never during dialogs or open PDFs)
    val allowStudentListBanner = !showAddEditDialog &&
        deletingStudent == null &&
        classChangeTarget == null &&
        statusChangeTarget == null &&
        siblingLinkTarget == null &&
        historyTarget == null &&
        pickupTarget == null &&
        remarksTarget == null &&
        !pdfState.isGenerating &&
        pdfState.generatedFile == null &&
        pdfState.generatedZipFile == null
    if (allowStudentListBanner) {
        AdaptiveAdBannerSlot(
            eligibility = adsUiState.bannerEligibility(AdScreenSurface.STUDENT_LIST),
            canRequestAds = adsUiState.canRequestAdsFromConsent,
            bannerUnitId = adsUiState.unitIds.bannerId,
            testTagPrefix = "student_list"
        )
    }
    }

    if (showAddEditDialog) {
        StudentRegisterAddEditDialog(
            viewModel = viewModel,
            existingStudent = editingStudent,
            allSchoolStudents = schoolStudents,
            onNavigateToTransport = {
                showAddEditDialog = false
                onNavigateToTransport()
            },
            onDismiss = { showAddEditDialog = false }
        )
    }

    if (classChangeTarget != null) {
        StudentClassSectionChangeDialog(
            viewModel = viewModel,
            student = classChangeTarget!!,
            availableClasses = uiState.activeSchoolClassNames,
            onDismiss = { classChangeTarget = null }
        )
    }

    if (statusChangeTarget != null) {
        StudentStatusChangeDialog(
            viewModel = viewModel,
            student = statusChangeTarget!!,
            onDismiss = { statusChangeTarget = null }
        )
    }

    deletingStudent?.let { st ->
        ConfirmDangerDialog(
            title = "Delete Student?",
            message = "Delete ${st.name} (Adm No. ${st.admNo}) along with all their marks, attendance, and co-scholastic records? This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteStudent(st)
                deletingStudent = null
            },
            onDismiss = { deletingStudent = null }
        )
    }

    if (siblingLinkTarget != null) {
        SiblingLinkDialog(
            viewModel = viewModel,
            primaryStudent = siblingLinkTarget!!,
            allSchoolStudents = schoolStudents,
            onDismiss = { siblingLinkTarget = null }
        )
    }

    if (historyTarget != null) {
        val target = historyTarget!!
        val allInSchool = uiState.allStudents.filter { it.schoolId == uiState.activeSchool.id }
        val promInSchool = uiState.promotionRecords.filter { it.schoolId == uiState.activeSchoolDocId }
        val entries = remember(target.id, allInSchool.size, promInSchool.size) {
            com.example.domain.PromotionEngine.buildStudentSessionHistory(
                admNo = target.admNo,
                studentName = target.name,
                fatherName = target.fatherName,
                allStudentsInSchool = allInSchool,
                promotionRecordsInSchool = promInSchool
            )
        }
        AlertDialog(
            onDismissRequest = { historyTarget = null },
            title = { Text("Session History — ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Adm No: ${target.admNo.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                    if (entries.isEmpty()) {
                        Text("No multi-session records yet.")
                    } else {
                        entries.forEach { e ->
                            Text(
                                text = "• ${e.session}: ${e.className}${if (e.section.isNotBlank()) "-${e.section}" else ""}${if (e.rollNo > 0) " (Roll #${e.rollNo})" else ""} — ${e.statusLabel}" +
                                    if (e.graceRemark.isNotBlank()) " [${e.graceRemark}]" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { historyTarget = null }) { Text("Close") }
            }
        )
    }

    pickupTarget?.let { targetStudent ->
        val targetDocKey = uiState.resolveStudentDocKey(targetStudent)
        val targetLegacyKey = targetStudent.id.toString()
        val pickups = uiState.authorizedPickups.filter {
            it.schoolId == activeSchoolDocId &&
                (it.studentId == targetDocKey || it.studentId == targetLegacyKey)
        }
        var showAddSubDialog by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { pickupTarget = null },
            title = {
                Text("Authorised Pickup Persons — ${targetStudent.name}")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Class ${targetStudent.className}-${targetStudent.section} • Adm No: ${targetStudent.admNo}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (pickups.isEmpty()) {
                        Text(
                            text = "No authorised pickup persons stored for this student yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        pickups.forEach { person ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (person.photoBase64.isNotBlank()) {
                                        val bmp = remember(person.photoBase64) {
                                            decodeBase64Thumbnail(person.photoBase64)
                                        }
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = person.personName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${person.personName} (${person.relation})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = person.mobile.ifBlank { "No mobile" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (canEditRegister) {
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteAuthorizedPickupPerson(person)
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove pickup person",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (canEditRegister) {
                        OutlinedButton(
                            onClick = { showAddSubDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("register_add_pickup_person_btn")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Authorised Pickup Person")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { pickupTarget = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Close")
                }
            }
        )

        if (showAddSubDialog && canEditRegister) {
            AddAuthorizedPickupPersonDialog(
                studentName = targetStudent.name,
                hi = uiState.phase10HindiLabels,
                onDismiss = { showAddSubDialog = false },
                onSave = { pName, pRel, pMob, pPhoto ->
                    viewModel.saveAuthorizedPickupPerson(
                        schoolDocId = activeSchoolDocId,
                        student = targetStudent,
                        personName = pName,
                        relation = pRel,
                        mobile = pMob,
                        photoBase64 = pPhoto
                    ) { ok, _ ->
                        if (ok) showAddSubDialog = false
                    }
                }
            )
        }
    }

    remarksTarget?.let { targetStudent ->
        val canViewStudentRemarks = viewModel.canViewStudentRemarksForClassSection(
            schoolDocId = activeSchoolDocId,
            className = targetStudent.className,
            section = targetStudent.section
        )
        if (canViewStudentRemarks) {
            val targetDocKey = uiState.resolveStudentDocKey(targetStudent)
            val targetLegacyKey = targetStudent.id.toString()
            val studentRemarks = viewModel.visibleStudentRemarksForActiveSchool(activeSchoolDocId)
                .filter { it.studentId == targetDocKey || it.studentId == targetLegacyKey }
                .sortedWith(compareByDescending<com.example.domain.StudentRemarkRecord> { it.dateIso }.thenByDescending { it.createdAtMs })

            AlertDialog(
                onDismissRequest = { remarksTarget = null },
                title = {
                    Text("Remark History — ${targetStudent.name}")
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Class ${targetStudent.className}-${targetStudent.section} • Adm No: ${targetStudent.admNo}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (studentRemarks.isEmpty()) {
                            Text(
                                text = "No remarks recorded for this student yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            studentRemarks.forEach { r ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${r.dateIso} • ${r.category}" +
                                                if (!r.isActive) " [CANCELLED]" else "",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = r.note,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (!r.isActive) {
                                            Text(
                                                text = "Cancelled Reason: ${r.cancelledReason} (by ${r.cancelledByName})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Text(
                                            text = "By: ${r.authorName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.canPrintStudentRemarkSummaryPdf && studentRemarks.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    viewModel.previewStudentRemarkSummaryPdf(
                                        schoolDocId = activeSchoolDocId,
                                        session = uiState.filter.session,
                                        student = targetStudent,
                                        classFilterLabel = "${targetStudent.className}-${targetStudent.section}",
                                        remarks = studentRemarks.filter { it.isActive }
                                    )
                                    remarksTarget = null
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Print A4 Summary PDF")
                            }
                        }
                        TextButton(
                            onClick = { remarksTarget = null },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentRegisterCard(
    student: StudentEntity,
    transportRoutes: List<TransportRouteRecord> = emptyList(),
    siblings: List<StudentEntity>,
    canEditRegister: Boolean,
    canViewFees: Boolean,
    canViewCoScholastic: Boolean = false,
    idCardSelectionMode: Boolean,
    isSelectedForIdCard: Boolean,
    onToggleIdCardSelect: () -> Unit,
    onEditStudent: () -> Unit,
    onChangeClassSection: () -> Unit,
    onChangeStatus: () -> Unit,
    onDeleteStudent: () -> Unit = {},
    onLinkSiblings: () -> Unit,
    onFilterBySiblingGroup: (String) -> Unit,
    onPrintAdmissionForm: () -> Unit,
    onPrintSingleIdCard: () -> Unit,
    onOpenFees: () -> Unit,
    onOpenCoScholastic: () -> Unit = {},
    canViewCertificates: Boolean = false,
    onOpenCertificates: () -> Unit = {},
    onOpenSessionHistory: () -> Unit = {},
    authorizedPickupsCount: Int = 0,
    onOpenAuthorizedPickups: () -> Unit = {},
    canViewRemarksForStudent: Boolean = false,
    remarksCount: Int = 0,
    onOpenRemarks: () -> Unit = {}
) {
    val isActive = student.isActiveStatus
    val photoBitmap = remember(student.photoBase64) {
        decodeBase64Thumbnail(student.photoBase64)
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("register_student_card_${student.admNo}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (idCardSelectionMode) {
                    Checkbox(
                        checked = isSelectedForIdCard,
                        onCheckedChange = { onToggleIdCardSelect() },
                        modifier = Modifier.size(48.dp)
                    )
                }
                // Avatar
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap.asImageBitmap(),
                            contentDescription = student.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ) {
                            Text(
                                text = if (isActive) "Active" else "Left",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Adm No: ${student.admNo} • Class ${student.className}-${student.section} (Roll ${student.rollNo})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (student.dob.isNotBlank() || student.admissionDate.isNotBlank()) {
                        Text(
                            text = buildList {
                                if (student.dob.isNotBlank()) add("DOB: ${student.dob}")
                                if (student.admissionDate.isNotBlank()) add("Adm Date: ${student.admissionDate}")
                            }.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details Grid
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Father: ${student.fatherName.ifBlank { "—" }} • Mother: ${student.motherName.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                val mobilesText = buildList {
                    if (student.mobile.isNotBlank()) add("Guardian: ${student.mobile}")
                    if (student.secondMobile.isNotBlank()) add("Alt: ${student.secondMobile}")
                }.joinToString(" • ").ifBlank { "No mobile recorded" }
                Text(
                    text = mobilesText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (student.address.isNotBlank()) {
                    Text(
                        text = "Address: ${student.address}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (student.previousSchool.isNotBlank()) {
                    Text(
                        text = "Previous School: ${student.previousSchool}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (student.usesTransport) {
                    val trDisplay = remember(student, transportRoutes) {
                        TransportEngine.resolveDisplay(student, transportRoutes)
                    }
                    val stopPart = if (trDisplay.stopName.isNotBlank()) " - ${trDisplay.stopName}" else ""
                    val monthPart = if (trDisplay.effectiveStartMonth.isNotBlank()) {
                        " • Start: ${trDisplay.effectiveStartMonth}" +
                            if (trDisplay.effectiveEndMonth.isNotBlank()) "–${trDisplay.effectiveEndMonth}" else ""
                    } else ""
                    val overridePart = if (student.transportFeeOverridden) " [Overridden]" else ""
                    Text(
                        text = "Transport: ${trDisplay.routeName}$stopPart • ${trDisplay.tripType.labelEn}$monthPart • Rs. ${student.transportMonthlyAmount}/mo$overridePart",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0D47A1)
                    )
                    if (trDisplay.attentionFlags.isNotEmpty()) {
                        Text(
                            text = "⚠ ${trDisplay.attentionFlags.joinToString(" • ") { it.labelEn }}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }
                if (!isActive) {
                    Text(
                        text = "Left on ${student.leftDate.ifBlank { "—" }} • Reason: ${student.leftReason.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB71C1C)
                    )
                }
                if (siblings.isNotEmpty() || student.siblingGroupId.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.FamilyRestroom,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = if (siblings.isNotEmpty()) {
                                "Siblings: ${siblings.joinToString(", ") { "${it.name} (${it.className}-${it.section})" }}"
                            } else {
                                "Family Group: ${student.siblingGroupId}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.clickable {
                                if (student.siblingGroupId.isNotBlank()) {
                                    onFilterBySiblingGroup(student.siblingGroupId)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(6.dp))

            // Action chips (all touch targets >= 48.dp)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (canEditRegister) {
                    AssistChip(
                        onClick = onEditStudent,
                        label = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("register_edit_${student.admNo}")
                    )
                    AssistChip(
                        onClick = onChangeClassSection,
                        label = { Text("Class / Sec") },
                        leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("register_change_class_${student.admNo}")
                    )
                    AssistChip(
                        onClick = onChangeStatus,
                        label = { Text(if (isActive) "Mark Left" else "Reactivate") },
                        leadingIcon = {
                            Icon(
                                if (isActive) Icons.Default.ExitToApp else Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("register_status_${student.admNo}")
                    )
                    AssistChip(
                        onClick = onLinkSiblings,
                        label = { Text("Siblings") },
                        leadingIcon = { Icon(Icons.Default.FamilyRestroom, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("register_siblings_${student.admNo}")
                    )
                }
                AssistChip(
                    onClick = onPrintAdmissionForm,
                    label = { Text("Admission Form PDF") },
                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("register_adm_pdf_${student.admNo}")
                )
                AssistChip(
                    onClick = onPrintSingleIdCard,
                    label = { Text("ID Card PDF") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("register_id_pdf_${student.admNo}")
                )
                if (canViewFees) {
                    AssistChip(
                        onClick = onOpenFees,
                        label = { Text("Fees & Account") },
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("register_open_fees_${student.admNo}")
                    )
                }
                if (canViewCertificates) {
                    AssistChip(
                        onClick = onOpenCertificates,
                        label = { Text("Certificates (TC / Bonafide)") },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("register_open_certs_${student.admNo}")
                    )
                }
                AssistChip(
                    onClick = onOpenSessionHistory,
                    label = { Text("Session History") },
                    leadingIcon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("register_history_${student.admNo}")
                )
                AssistChip(
                    onClick = onOpenAuthorizedPickups,
                    label = { Text("Authorised Pickups ($authorizedPickupsCount)") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("register_pickups_${student.admNo}")
                )
                if (canViewCoScholastic) {
                    AssistChip(
                        onClick = onOpenCoScholastic,
                        label = { Text("Co-Scholastic") },
                        leadingIcon = { Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("register_coscholastic_${student.admNo}")
                    )
                }
                if (canViewRemarksForStudent) {
                    AssistChip(
                        onClick = onOpenRemarks,
                        label = { Text("Remarks ($remarksCount)") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("register_remarks_${student.admNo}")
                    )
                }
                if (canEditRegister) {
                    AssistChip(
                        onClick = onDeleteStudent,
                        label = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("register_delete_${student.admNo}")
                    )
                }
            }
        }
    }
}

@Composable
internal fun StudentRegisterAddEditDialog(
    viewModel: SchoolViewModel,
    existingStudent: StudentEntity?,
    allSchoolStudents: List<StudentEntity>,
    onNavigateToTransport: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isHindi = uiState.phase10HindiLabels
    val structure = remember(uiState.feeStructures, uiState.activeSchoolDocId, uiState.filter.session, uiState.activeSchoolClassNames) {
        uiState.feeStructureForSchool(uiState.activeSchoolDocId, uiState.filter.session)
    }
    val chargedMonths = remember(structure) {
        structure.chargedTransportMonths.ifEmpty { structure.monthOrder }.ifEmpty {
            listOf("APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC", "JAN", "FEB", "MAR")
        }
    }
    val defaultStartMonth = remember(chargedMonths) {
        val cur = currentSessionMonthCode()
        if (chargedMonths.any { it.equals(cur, ignoreCase = true) }) cur else chargedMonths.first()
    }

    var name by remember { mutableStateOf(existingStudent?.name.orEmpty()) }
    var admNo by remember { mutableStateOf(existingStudent?.admNo.orEmpty()) }
    var admissionDate by remember {
        mutableStateOf(existingStudent?.admissionDate?.ifBlank { Phase6DateUtils.todayIso() } ?: Phase6DateUtils.todayIso())
    }
    var dob by remember { mutableStateOf(existingStudent?.dob.orEmpty()) }
    var className by remember {
        mutableStateOf(existingStudent?.className?.ifBlank { uiState.effectiveClassName } ?: uiState.effectiveClassName)
    }
    var section by remember {
        mutableStateOf(existingStudent?.section?.ifBlank { uiState.filter.section } ?: uiState.filter.section)
    }
    var rollNoText by remember { mutableStateOf((existingStudent?.rollNo ?: 1).toString()) }
    var fatherName by remember { mutableStateOf(existingStudent?.fatherName.orEmpty()) }
    var motherName by remember { mutableStateOf(existingStudent?.motherName.orEmpty()) }
    var mobile by remember { mutableStateOf(existingStudent?.mobile.orEmpty()) }
    var secondMobile by remember { mutableStateOf(existingStudent?.secondMobile.orEmpty()) }
    var address by remember { mutableStateOf(existingStudent?.address.orEmpty()) }
    var previousSchool by remember { mutableStateOf(existingStudent?.previousSchool.orEmpty()) }
    var siblingGroupId by remember { mutableStateOf(existingStudent?.siblingGroupId.orEmpty()) }
    var usesTransport by remember { mutableStateOf(existingStudent?.usesTransport ?: false) }
    var transportRouteId by remember { mutableStateOf(existingStudent?.transportRouteId.orEmpty()) }
    var transportStopId by remember { mutableStateOf(existingStudent?.transportStopId.orEmpty()) }
    var transportStopName by remember { mutableStateOf(existingStudent?.transportStopName.orEmpty()) }
    var transportRouteName by remember { mutableStateOf(existingStudent?.transportRouteName.orEmpty()) }
    var transportTripType by remember {
        mutableStateOf(TransportTripType.fromRaw(existingStudent?.transportTripType))
    }
    var transportStartMonth by remember {
        mutableStateOf(existingStudent?.transportStartMonth?.ifBlank { defaultStartMonth } ?: defaultStartMonth)
    }
    var transportEndMonth by remember { mutableStateOf(existingStudent?.transportEndMonth.orEmpty()) }
    var transportFeeOverridden by remember { mutableStateOf(existingStudent?.transportFeeOverridden ?: false) }
    var transportOverrideRemark by remember { mutableStateOf(existingStudent?.transportOverrideRemark.orEmpty()) }
    var transportAmountText by remember {
        mutableStateOf(
            if ((existingStudent?.transportMonthlyAmount ?: 0) > 0) {
                (existingStudent?.transportMonthlyAmount ?: 0).toString()
            } else {
                ""
            }
        )
    }
    var overCapacityAcknowledged by remember { mutableStateOf(false) }
    var photoBase64 by remember { mutableStateOf(existingStudent?.photoBase64.orEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existingStudent, className, section) {
        if (existingStudent == null) {
            val suggested = viewModel.suggestRollNo(
                schoolId = uiState.activeSchool.id,
                session = uiState.filter.session,
                className = className,
                section = section
            )
            rollNoText = suggested.toString()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = ImageUtils.encodeUriToBase64Under150Kb(context, uri)
                if (!encoded.isNullOrBlank()) {
                    photoBase64 = encoded
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingStudent == null) "Add Student to Register" else "Edit Student Register",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!errorMsg.isNullOrBlank()) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Photo Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val bmp = remember(photoBase64) { decodeBase64Thumbnail(photoBase64) }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Student photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    }
                    Column {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Text(if (photoBase64.isBlank()) "Choose Photo" else "Change Photo")
                        }
                        if (photoBase64.isNotBlank()) {
                            TextButton(onClick = { photoBase64 = "" }) {
                                Text("Remove Photo")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Student Full Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_dialog_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = admNo,
                        onValueChange = { admNo = it },
                        label = { Text("Admission No. *") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("register_dialog_admno_input")
                    )
                    OutlinedTextField(
                        value = admissionDate,
                        onValueChange = { admissionDate = it },
                        label = { Text("Admission Date") },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Class picker chips
                Text("Class *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.activeSchoolClassNames.forEach { cls ->
                        FilterChip(
                            selected = className.equals(cls, ignoreCase = true),
                            onClick = { className = cls },
                            label = { Text(cls) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it.uppercase() },
                        label = { Text("Section *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rollNoText,
                        onValueChange = { rollNoText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Roll No. *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { dob = it },
                        label = { Text("Date of Birth") },
                        placeholder = { Text("DD/MM/YYYY") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fatherName,
                        onValueChange = { fatherName = it },
                        label = { Text("Father Name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("register_dialog_father_input")
                    )
                    OutlinedTextField(
                        value = motherName,
                        onValueChange = { motherName = it },
                        label = { Text("Mother Name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("register_dialog_mother_input")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Guardian Mobile") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("register_dialog_mobile_input")
                    )
                    OutlinedTextField(
                        value = secondMobile,
                        onValueChange = { secondMobile = it },
                        label = { Text("Second Mobile") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("register_dialog_second_mobile_input")
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Residential Address") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = previousSchool,
                    onValueChange = { previousSchool = it },
                    label = { Text("Previous School") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = siblingGroupId,
                    onValueChange = { siblingGroupId = it },
                    label = { Text("Sibling Family Code (optional)") },
                    placeholder = { Text("e.g., FAM-SHARMA or link via Siblings button") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                val schoolRoutes = remember(uiState.transportRoutes, uiState.activeSchoolDocId) {
                    uiState.transportRoutes.filter { it.schoolId == uiState.activeSchoolDocId && it.active }
                }
                val pickedRoute = remember(schoolRoutes, transportRouteId) {
                    schoolRoutes.firstOrNull { it.id == transportRouteId }
                }
                val isOverCapacity = remember(pickedRoute, allSchoolStudents, existingStudent?.id) {
                    val targetId = existingStudent?.id ?: -1L
                    pickedRoute != null && TransportEngine.willExceedCapacity(
                        route = pickedRoute,
                        currentStudents = allSchoolStudents,
                        additionalStudentIds = if (targetId > 0L) setOf(targetId) else setOf(-999999L)
                    )
                }

                StudentRegisterTransportSection(
                    isHindi = isHindi,
                    usesTransport = usesTransport,
                    onUsesTransportChange = { checked ->
                        usesTransport = checked
                        if (checked && transportStartMonth.isBlank()) {
                            transportStartMonth = defaultStartMonth
                        }
                    },
                    routes = schoolRoutes,
                    allSchoolStudents = allSchoolStudents,
                    existingStudentId = existingStudent?.id,
                    selectedRouteId = transportRouteId,
                    selectedStopId = transportStopId,
                    selectedStopName = transportStopName,
                    routeDisplayFallback = transportRouteName,
                    onRouteDisplayFallbackChange = { transportRouteName = it },
                    selectedTripType = transportTripType,
                    selectedStartMonth = transportStartMonth,
                    selectedEndMonth = transportEndMonth,
                    chargedMonths = chargedMonths,
                    amountText = transportAmountText,
                    onAmountTextChange = { transportAmountText = it },
                    feeOverridden = transportFeeOverridden,
                    overrideRemark = transportOverrideRemark,
                    onOverrideRemarkChange = { transportOverrideRemark = it },
                    overCapacityAcknowledged = overCapacityAcknowledged,
                    onOverCapacityAcknowledgedChange = { overCapacityAcknowledged = it },
                    onSelectRoute = { rt ->
                        transportRouteId = rt.id
                        overCapacityAcknowledged = false
                        val firstStop = rt.stops.firstOrNull()
                        transportStopId = firstStop?.stopId.orEmpty()
                        transportStopName = firstStop?.stopName.orEmpty()
                        transportRouteName = if (!firstStop?.stopName.isNullOrBlank()) {
                            "${rt.routeName} - ${firstStop!!.stopName}"
                        } else rt.routeName
                        if (!transportFeeOverridden) {
                            val computed = rt.effectiveFeeFor(
                                stopId = transportStopId,
                                stopName = transportStopName,
                                tripType = transportTripType
                            )
                            transportAmountText = computed.toString()
                        }
                    },
                    onSelectStop = { rt, st ->
                        transportStopId = st.stopId
                        transportStopName = st.stopName
                        transportRouteName = "${rt.routeName} - ${st.stopName}"
                        if (!transportFeeOverridden) {
                            val computed = rt.effectiveFeeFor(
                                stopId = st.stopId,
                                stopName = st.stopName,
                                tripType = transportTripType
                            )
                            transportAmountText = computed.toString()
                        }
                    },
                    onSelectTripType = { trip ->
                        transportTripType = trip
                        if (!transportFeeOverridden && pickedRoute != null) {
                            val computed = pickedRoute.effectiveFeeFor(
                                stopId = transportStopId,
                                stopName = transportStopName,
                                tripType = trip
                            )
                            transportAmountText = computed.toString()
                        }
                    },
                    onSelectStartMonth = { transportStartMonth = it },
                    onSelectEndMonth = { transportEndMonth = it },
                    onToggleOverride = { ov ->
                        transportFeeOverridden = ov
                        if (!ov && pickedRoute != null) {
                            val computed = pickedRoute.effectiveFeeFor(
                                stopId = transportStopId,
                                stopName = transportStopName,
                                tripType = transportTripType
                            )
                            transportAmountText = computed.toString()
                            transportOverrideRemark = ""
                        }
                    },
                    onResetToRouteFee = {
                        if (pickedRoute != null) {
                            val computed = pickedRoute.effectiveFeeFor(
                                stopId = transportStopId,
                                stopName = transportStopName,
                                tripType = transportTripType
                            )
                            transportAmountText = computed.toString()
                        }
                        transportFeeOverridden = false
                        transportOverrideRemark = ""
                    },
                    canNavigateToTransport = uiState.canViewTransport,
                    onCreateRouteShortcut = onNavigateToTransport
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val cleanAdm = admNo.trim()
                    if (cleanName.isBlank() || cleanAdm.isBlank()) {
                        errorMsg = "Student Name and Admission Number are required."
                        return@Button
                    }
                    val transportAmt = if (usesTransport) {
                        transportAmountText.toIntOrNull() ?: 0
                    } else {
                        0
                    }
                    if (usesTransport && transportAmt < 0) {
                        errorMsg = "Transport monthly amount cannot be negative."
                        return@Button
                    }
                    if (usesTransport && transportFeeOverridden && transportOverrideRemark.trim().isEmpty()) {
                        errorMsg = "Please enter an override remark (or reset to route fee)."
                        return@Button
                    }
                    val schoolRoutes = uiState.transportRoutes.filter { it.schoolId == uiState.activeSchoolDocId && it.active }
                    val pickedRoute = schoolRoutes.firstOrNull { it.id == transportRouteId }
                    if (usesTransport && pickedRoute != null) {
                        val targetId = existingStudent?.id ?: -999999L
                        val wouldExceed = TransportEngine.willExceedCapacity(pickedRoute, allSchoolStudents, setOf(targetId))
                        if (wouldExceed && !overCapacityAcknowledged) {
                            errorMsg = "Route '${pickedRoute.routeName}' seats are full. Tick 'Confirm over-capacity assignment' to proceed."
                            return@Button
                        }
                    }
                    val clsEntity = uiState.classEntityForName(className, uiState.activeSchool.id)
                    val studentToSave = (existingStudent ?: StudentEntity(
                        schoolId = uiState.activeSchool.id,
                        session = uiState.filter.session,
                        classId = clsEntity?.id ?: 0L,
                        className = className.trim(),
                        section = section.trim().uppercase().ifBlank { "A" },
                        rollNo = rollNoText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        name = cleanName,
                        fatherName = fatherName.trim(),
                        motherName = motherName.trim(),
                        mobile = mobile.trim(),
                        address = address.trim(),
                        dob = dob.trim(),
                        admNo = cleanAdm
                    )).copy(
                        schoolId = existingStudent?.schoolId ?: uiState.activeSchool.id,
                        session = existingStudent?.session ?: uiState.filter.session,
                        classId = clsEntity?.id ?: existingStudent?.classId ?: 0L,
                        className = className.trim(),
                        section = section.trim().uppercase().ifBlank { "A" },
                        rollNo = rollNoText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        name = cleanName,
                        fatherName = fatherName.trim(),
                        motherName = motherName.trim(),
                        mobile = mobile.trim(),
                        secondMobile = secondMobile.trim(),
                        address = address.trim(),
                        dob = dob.trim(),
                        admNo = cleanAdm,
                        admissionDate = admissionDate.trim(),
                        previousSchool = previousSchool.trim(),
                        siblingGroupId = siblingGroupId.trim(),
                        usesTransport = usesTransport,
                        transportRouteId = if (usesTransport) transportRouteId.trim() else "",
                        transportStopId = if (usesTransport) transportStopId.trim() else "",
                        transportStopName = if (usesTransport) transportStopName.trim() else "",
                        transportRouteName = if (usesTransport) transportRouteName.trim() else "",
                        transportTripType = if (usesTransport) transportTripType.wireValue else TransportTripType.BOTH_WAYS.wireValue,
                        transportMonthlyAmount = if (usesTransport) transportAmt else 0,
                        transportStartMonth = if (usesTransport) transportStartMonth.trim().ifBlank { defaultStartMonth } else "",
                        transportEndMonth = if (usesTransport) transportEndMonth.trim() else "",
                        transportFeeOverridden = if (usesTransport) transportFeeOverridden else false,
                        transportOverrideRemark = if (usesTransport && transportFeeOverridden) transportOverrideRemark.trim() else "",
                        photoBase64 = photoBase64,
                        isSample = false
                    )
                    viewModel.saveStudent(studentToSave) { ok, err ->
                        if (ok) {
                            onDismiss()
                        } else {
                            errorMsg = err
                        }
                    }
                },
                modifier = Modifier.testTag("register_dialog_save_button")
            ) {
                Text("Save Student")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudentRegisterTransportSection(
    isHindi: Boolean,
    usesTransport: Boolean,
    onUsesTransportChange: (Boolean) -> Unit,
    routes: List<TransportRouteRecord>,
    allSchoolStudents: List<StudentEntity>,
    existingStudentId: Long?,
    selectedRouteId: String,
    selectedStopId: String,
    selectedStopName: String,
    routeDisplayFallback: String,
    onRouteDisplayFallbackChange: (String) -> Unit,
    selectedTripType: TransportTripType,
    selectedStartMonth: String,
    selectedEndMonth: String,
    chargedMonths: List<String>,
    amountText: String,
    onAmountTextChange: (String) -> Unit,
    feeOverridden: Boolean,
    overrideRemark: String,
    onOverrideRemarkChange: (String) -> Unit,
    overCapacityAcknowledged: Boolean,
    onOverCapacityAcknowledgedChange: (Boolean) -> Unit,
    onSelectRoute: (TransportRouteRecord) -> Unit,
    onSelectStop: (TransportRouteRecord, com.example.domain.TransportStop) -> Unit,
    onSelectTripType: (TransportTripType) -> Unit,
    onSelectStartMonth: (String) -> Unit,
    onSelectEndMonth: (String) -> Unit,
    onToggleOverride: (Boolean) -> Unit,
    onResetToRouteFee: () -> Unit,
    canNavigateToTransport: Boolean,
    onCreateRouteShortcut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("register_transport_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = p10Label(isHindi, "स्कूल वाहन (Transport)", "Uses School Transport"),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = p10Label(
                        isHindi,
                        "शुल्क चार्ट में मासिक वाहन शुल्क जोड़ेगा",
                        "Adds monthly Transport fee column for this student"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = usesTransport,
                onCheckedChange = onUsesTransportChange,
                modifier = Modifier.testTag("register_dialog_transport_switch")
            )
        }

        if (usesTransport) {
            if (routes.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF8E1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_no_routes_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = p10Label(
                                isHindi,
                                "अभी कोई सक्रिय रूट नहीं बना है। पहले वाहन रूट बनाएं या मैन्युअल लिखें।",
                                "No transport routes exist yet. Create a route first or enter details below."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE65100)
                        )
                        if (canNavigateToTransport) {
                            OutlinedButton(
                                onClick = onCreateRouteShortcut,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("register_create_route_shortcut")
                            ) {
                                Text(p10Label(isHindi, "रूट बनाएं (Transport खोलें)", "Create Transport Route"))
                            }
                        }
                    }
                }
            } else {
                val pickedRoute = routes.firstOrNull { it.id == selectedRouteId }
                Text(
                    text = p10Label(isHindi, "रूट चुनें *", "Select Transport Route *"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    routes.forEach { rt ->
                        val seatsUsed = allSchoolStudents.count { TransportEngine.studentMatchesRoute(it, rt) }
                        FilterChip(
                            selected = selectedRouteId == rt.id,
                            onClick = { onSelectRoute(rt) },
                            label = { Text("${rt.routeName} (₹${rt.monthlyFeeRs} • $seatsUsed/${rt.seatCapacity})") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                if (pickedRoute != null) {
                    val targetIds = if (existingStudentId != null && existingStudentId > 0L) {
                        setOf(existingStudentId)
                    } else {
                        setOf(-999999L)
                    }
                    val wouldExceed = TransportEngine.willExceedCapacity(pickedRoute, allSchoolStudents, targetIds)
                    if (wouldExceed) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFEBEE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = p10Label(
                                        isHindi,
                                        "⚠ रूट '${pickedRoute.routeName}' की सीटें (${pickedRoute.seatCapacity}) भर चुकी हैं।",
                                        "⚠ Seats on '${pickedRoute.routeName}' (${pickedRoute.seatCapacity}) are full."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFB71C1C),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = overCapacityAcknowledged,
                                        onCheckedChange = onOverCapacityAcknowledgedChange
                                    )
                                    Text(
                                        text = p10Label(isHindi, "क्षमता से अधिक जोड़ने की पुष्टि करें", "Confirm over-capacity assignment"),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    if (pickedRoute.stops.isNotEmpty()) {
                        Text(
                            text = p10Label(isHindi, "स्टॉप चुनें", "Select Stop"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            pickedRoute.stops.forEach { st ->
                                val stopFee = pickedRoute.effectiveFeeFor(
                                    stopId = st.stopId,
                                    stopName = st.stopName,
                                    tripType = selectedTripType
                                )
                                val isSel = (selectedStopId.isNotBlank() && st.stopId == selectedStopId) ||
                                    selectedStopName.equals(st.stopName, ignoreCase = true)
                                FilterChip(
                                    selected = isSel,
                                    onClick = { onSelectStop(pickedRoute, st) },
                                    label = { Text("${st.stopName} (₹$stopFee)") },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }
                }

                // Trip type selector
                Text(
                    text = p10Label(isHindi, "यात्रा प्रकार", "Trip Type"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TransportTripType.entries.forEach { trip ->
                        FilterChip(
                            selected = selectedTripType == trip,
                            onClick = { onSelectTripType(trip) },
                            label = { Text(if (isHindi) trip.labelHi else trip.labelEn) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("register_trip_${trip.wireValue}")
                        )
                    }
                }

                // Start & End Months
                Text(
                    text = p10Label(isHindi, "शुरुआती महीना (Start Month)", "Start Month *"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chargedMonths.forEach { m ->
                        FilterChip(
                            selected = selectedStartMonth.equals(m, ignoreCase = true),
                            onClick = { onSelectStartMonth(m) },
                            label = { Text(m) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Text(
                    text = p10Label(isHindi, "समाप्ति महीना (वैकल्पिक)", "End Month (Optional)"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedEndMonth.isBlank(),
                        onClick = { onSelectEndMonth("") },
                        label = { Text(p10Label(isHindi, "पूरा सत्र", "Full Session")) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    chargedMonths.forEach { m ->
                        FilterChip(
                            selected = selectedEndMonth.equals(m, ignoreCase = true),
                            onClick = { onSelectEndMonth(m) },
                            label = { Text(m) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                if (pickedRoute != null) {
                    val computedFee = pickedRoute.effectiveFeeFor(
                        stopId = selectedStopId,
                        stopName = selectedStopName,
                        tripType = selectedTripType
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = p10Label(
                                    isHindi,
                                    "स्वचालित फीस: ₹$computedFee/माह",
                                    "Route/Stop Rate: ₹$computedFee/mo"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = p10Label(isHindi, "व्यक्तिगत शुल्क बदलें (Override)", "Override Amount Manually"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = feeOverridden,
                            onCheckedChange = onToggleOverride,
                            modifier = Modifier.testTag("register_transport_override_switch")
                        )
                    }
                    if (feeOverridden) {
                        OutlinedButton(
                            onClick = onResetToRouteFee,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("register_transport_reset_fee_btn")
                        ) {
                            Text(p10Label(isHindi, "रूट फीस (₹$computedFee) पर रीसेट करें", "Reset to Route Fee (₹$computedFee)"))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = routeDisplayFallback,
                onValueChange = onRouteDisplayFallbackChange,
                label = { Text("Route / Stop Label") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_dialog_route_input")
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    val digits = it.filter { ch -> ch.isDigit() }
                    onAmountTextChange(digits)
                    if (!feeOverridden && routes.isNotEmpty()) {
                        onToggleOverride(true)
                    }
                },
                label = { Text("Monthly Rs. *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_dialog_transport_amount_input")
            )
            if (feeOverridden || routes.isEmpty()) {
                if (feeOverridden) {
                    OutlinedTextField(
                        value = overrideRemark,
                        onValueChange = onOverrideRemarkChange,
                        label = { Text("Override Remark *") },
                        placeholder = { Text("e.g., Sibling discount / half-route concession") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_transport_override_remark_input")
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentClassSectionChangeDialog(
    viewModel: SchoolViewModel,
    student: StudentEntity,
    availableClasses: List<String>,
    onDismiss: () -> Unit
) {
    var selectedClass by remember { mutableStateOf(student.className) }
    var selectedSection by remember { mutableStateOf(student.section.ifBlank { "A" }) }
    var rollNoText by remember { mutableStateOf(student.rollNo.toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedClass, selectedSection) {
        if (!selectedClass.equals(student.className, ignoreCase = true) ||
            !selectedSection.equals(student.section, ignoreCase = true)
        ) {
            val nextRoll = viewModel.suggestRollNo(
                schoolId = student.schoolId,
                session = student.session,
                className = selectedClass,
                section = selectedSection
            )
            rollNoText = nextRoll.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Class & Section • ${student.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text("Current: Class ${student.className}-${student.section} (Roll ${student.rollNo})")
                Text("New Class:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableClasses.forEach { cls ->
                        FilterChip(
                            selected = selectedClass.equals(cls, ignoreCase = true),
                            onClick = { selectedClass = cls },
                            label = { Text(cls) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = selectedSection,
                        onValueChange = { selectedSection = it.uppercase() },
                        label = { Text("New Section") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rollNoText,
                        onValueChange = { rollNoText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("New Roll No.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.changeStudentClassAndSection(
                        student = student,
                        newClassName = selectedClass,
                        newSection = selectedSection,
                        newRollNo = rollNoText.toIntOrNull() ?: student.rollNo
                    ) { ok, err ->
                        if (ok) onDismiss() else errorMsg = err
                    }
                }
            ) {
                Text("Update Class / Section")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StudentStatusChangeDialog(
    viewModel: SchoolViewModel,
    student: StudentEntity,
    onDismiss: () -> Unit
) {
    val isCurrentlyActive = student.isActiveStatus
    var leftDate by remember {
        mutableStateOf(student.leftDate.ifBlank { Phase6DateUtils.todayIso() })
    }
    var leftReason by remember { mutableStateOf(student.leftReason) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isCurrentlyActive) "Mark Student as Left • ${student.name}" else "Reactivate Student • ${student.name}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (isCurrentlyActive) {
                    Text(
                        "Student record and past fee receipts/report cards are preserved safely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = leftDate,
                        onValueChange = { leftDate = it },
                        label = { Text("Date Left (YYYY-MM-DD) *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_status_left_date_input")
                    )
                    OutlinedTextField(
                        value = leftReason,
                        onValueChange = { leftReason = it },
                        label = { Text("Reason for Leaving *") },
                        placeholder = { Text("e.g., TC issued, family relocated, completed schooling") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_status_left_reason_input")
                    )
                } else {
                    Text(
                        "Currently marked as Left on ${student.leftDate} (${student.leftReason}). Reactivating will mark ${student.name} as Active again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetStatus = if (isCurrentlyActive) StudentRegisterStatus.LEFT else StudentRegisterStatus.ACTIVE
                    viewModel.changeStudentRegisterStatus(
                        student = student,
                        newStatus = targetStatus,
                        leftDateIso = leftDate,
                        leftReason = leftReason
                    ) { ok, err ->
                        if (ok) onDismiss() else errorMsg = err
                    }
                },
                modifier = Modifier.testTag("register_status_confirm_button")
            ) {
                Text(if (isCurrentlyActive) "Confirm Left" else "Reactivate Student")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SiblingLinkDialog(
    viewModel: SchoolViewModel,
    primaryStudent: StudentEntity,
    allSchoolStudents: List<StudentEntity>,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf(primaryStudent.fatherName.ifBlank { primaryStudent.mobile }) }
    val selectedSiblingIds = remember {
        mutableStateListOf<Long>().apply {
            if (primaryStudent.siblingGroupId.isNotBlank()) {
                allSchoolStudents
                    .filter { it.id != primaryStudent.id && it.siblingGroupId.equals(primaryStudent.siblingGroupId, ignoreCase = true) }
                    .forEach { add(it.id) }
            }
        }
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val candidates = remember(allSchoolStudents, primaryStudent.id, search) {
        val q = search.trim().lowercase()
        allSchoolStudents
            .filter { it.id != primaryStudent.id }
            .filter {
                q.isBlank() ||
                    it.name.lowercase().contains(q) ||
                    it.fatherName.lowercase().contains(q) ||
                    it.motherName.lowercase().contains(q) ||
                    it.mobile.lowercase().contains(q) ||
                    it.admNo.lowercase().contains(q)
            }
            .take(30)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Siblings • ${primaryStudent.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Search by father name, mobile, student name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(candidates, key = { it.id }) { cand ->
                        val checked = cand.id in selectedSiblingIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedSiblingIds.remove(cand.id) else selectedSiblingIds.add(cand.id)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selectedSiblingIds.remove(cand.id) else selectedSiblingIds.add(cand.id)
                                }
                            )
                            Column {
                                Text(
                                    "${cand.name} (${cand.className}-${cand.section}, Adm ${cand.admNo})",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "Father: ${cand.fatherName.ifBlank { "—" }} • Mob: ${cand.mobile.ifBlank { "—" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (primaryStudent.siblingGroupId.isNotBlank()) {
                    TextButton(
                        onClick = {
                            viewModel.unlinkStudentFromSiblings(primaryStudent) { ok, err ->
                                if (ok) onDismiss() else errorMsg = err
                            }
                        }
                    ) {
                        Text("Unlink ${primaryStudent.name} from family group")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.linkStudentsAsSiblings(primaryStudent, selectedSiblingIds.toList()) { ok, err ->
                        if (ok) onDismiss() else errorMsg = err
                    }
                }
            ) {
                Text("Link Selected (${selectedSiblingIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun decodeBase64Thumbnail(base64Str: String): android.graphics.Bitmap? {
    val clean = base64Str.trim()
    if (clean.isEmpty()) return null
    return runCatching {
        val raw = if (clean.contains(",")) clean.substringAfter(",") else clean
        val bytes = Base64.decode(raw, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
