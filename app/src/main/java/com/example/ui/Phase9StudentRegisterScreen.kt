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
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
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
import com.example.domain.Phase6DateUtils
import com.example.domain.Phase9AccessEvaluator
import com.example.domain.StudentRegisterEngine
import com.example.domain.StudentRegisterStatus
import com.example.domain.StudentRegisterStatusFilter
import com.example.domain.StudentTransportFilter
import com.example.util.ImageUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Phase9StudentRegisterScreen(
    viewModel: SchoolViewModel,
    onBack: () -> Unit,
    onOpenFeeCollectionForStudent: (StudentEntity) -> Unit = {},
    onOpenCertificatesForStudent: (StudentEntity) -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfState by viewModel.timetablePdfState.collectAsStateWithLifecycle()

    val activeSchool = uiState.activeSchool
    val activeSchoolDocId = uiState.activeSchoolDocId
    val canEditRegister = Phase9AccessEvaluator.canEditStudentRegister(
        uiState.accessProfile,
        activeSchoolDocId
    ) || uiState.canEditStudentRegister

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<StudentRegisterStatus?>(StudentRegisterStatus.ACTIVE) }
    var selectedClassFilter by remember { mutableStateOf<String?>(null) }
    var selectedSectionFilter by remember { mutableStateOf<String?>(null) }
    var transportFilter by remember { mutableStateOf<Boolean?>(null) }
    var siblingGroupFilter by remember { mutableStateOf<String?>(null) }

    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var classChangeTarget by remember { mutableStateOf<StudentEntity?>(null) }
    var statusChangeTarget by remember { mutableStateOf<StudentEntity?>(null) }
    var siblingLinkTarget by remember { mutableStateOf<StudentEntity?>(null) }

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
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("phase9_student_register_screen")
    ) {
        // Header bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("register_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "Student Register",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${activeSchool.name} • Session ${uiState.filter.session}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
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
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("register_print_id_cards_button")
                ) {
                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (idCardSelectionMode && selectedIdsForIdCards.isNotEmpty()) {
                            "ID Cards (${selectedIdsForIdCards.size})"
                        } else {
                            "ID Cards PDF"
                        }
                    )
                }
                if (canEditRegister) {
                    Button(
                        onClick = {
                            editingStudent = null
                            showAddEditDialog = true
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("register_add_student_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Student")
                    }
                }
            }
        }

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
                        label = { Text(sch.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Search box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search name, admission no., father/mother name, mobile, transport route…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
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

        // Status, Class, Transport & Sibling filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = statusFilter == StudentRegisterStatus.ACTIVE,
                onClick = { statusFilter = StudentRegisterStatus.ACTIVE },
                label = { Text("Active ($activeCount)") },
                modifier = Modifier.testTag("register_filter_active")
            )
            FilterChip(
                selected = statusFilter == StudentRegisterStatus.LEFT,
                onClick = { statusFilter = StudentRegisterStatus.LEFT },
                label = { Text("Left ($leftCount)") },
                modifier = Modifier.testTag("register_filter_left")
            )
            FilterChip(
                selected = statusFilter == null,
                onClick = { statusFilter = null },
                label = { Text("All (${schoolStudents.size})") },
                modifier = Modifier.testTag("register_filter_all")
            )
            FilterChip(
                selected = transportFilter == true,
                onClick = { transportFilter = if (transportFilter == true) null else true },
                leadingIcon = { Icon(Icons.Default.DirectionsBus, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Uses Transport ($transportCount)") },
                modifier = Modifier.testTag("register_filter_transport")
            )
            FilterChip(
                selected = idCardSelectionMode,
                onClick = {
                    idCardSelectionMode = !idCardSelectionMode
                    if (!idCardSelectionMode) selectedIdsForIdCards.clear()
                },
                label = { Text(if (idCardSelectionMode) "Cancel ID Select" else "Select for ID Cards") }
            )
            if (siblingGroupFilter != null) {
                FilterChip(
                    selected = true,
                    onClick = { siblingGroupFilter = null },
                    leadingIcon = { Icon(Icons.Default.FamilyRestroom, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Family: $siblingGroupFilter ✕") }
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No students match the current filter.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Adjust search/filter chips or add a new student to the register.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                    StudentRegisterCard(
                        student = student,
                        siblings = siblings,
                        canEditRegister = canEditRegister,
                        canViewFees = uiState.canViewFees,
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
                        onLinkSiblings = { siblingLinkTarget = student },
                        onFilterBySiblingGroup = { grp -> siblingGroupFilter = grp },
                        onPrintAdmissionForm = { viewModel.generateStudentAdmissionFormPdf(student) },
                        onPrintSingleIdCard = { viewModel.generateStudentIdCardsPdf(listOf(student), student.name) },
                        onOpenFees = { onOpenFeeCollectionForStudent(student) },
                        canViewCertificates = uiState.canViewCertificates,
                        onOpenCertificates = { onOpenCertificatesForStudent(student) }
                    )
                }
            }
        }
    }

    if (showAddEditDialog) {
        StudentRegisterAddEditDialog(
            viewModel = viewModel,
            existingStudent = editingStudent,
            allSchoolStudents = schoolStudents,
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

    if (siblingLinkTarget != null) {
        SiblingLinkDialog(
            viewModel = viewModel,
            primaryStudent = siblingLinkTarget!!,
            allSchoolStudents = schoolStudents,
            onDismiss = { siblingLinkTarget = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentRegisterCard(
    student: StudentEntity,
    siblings: List<StudentEntity>,
    canEditRegister: Boolean,
    canViewFees: Boolean,
    idCardSelectionMode: Boolean,
    isSelectedForIdCard: Boolean,
    onToggleIdCardSelect: () -> Unit,
    onEditStudent: () -> Unit,
    onChangeClassSection: () -> Unit,
    onChangeStatus: () -> Unit,
    onLinkSiblings: () -> Unit,
    onFilterBySiblingGroup: (String) -> Unit,
    onPrintAdmissionForm: () -> Unit,
    onPrintSingleIdCard: () -> Unit,
    onOpenFees: () -> Unit,
    canViewCertificates: Boolean = false,
    onOpenCertificates: () -> Unit = {}
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
                    Text(
                        text = "Transport: Yes • Route: ${student.transportRouteName.ifBlank { "Standard" }} • Rs. ${student.transportMonthlyAmount}/month",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0D47A1)
                    )
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
            }
        }
    }
}

@Composable
internal fun StudentRegisterAddEditDialog(
    viewModel: SchoolViewModel,
    existingStudent: StudentEntity?,
    allSchoolStudents: List<StudentEntity>,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    var transportStopName by remember { mutableStateOf(existingStudent?.transportStopName.orEmpty()) }
    var transportRouteName by remember { mutableStateOf(existingStudent?.transportRouteName.orEmpty()) }
    var transportAmountText by remember {
        mutableStateOf(
            if ((existingStudent?.transportMonthlyAmount ?: 0) > 0) {
                (existingStudent?.transportMonthlyAmount ?: 0).toString()
            } else {
                ""
            }
        )
    }
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

                // Transport toggle & details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Uses School Transport", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Adds monthly Transport fee column for this student",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = usesTransport,
                        onCheckedChange = { usesTransport = it },
                        modifier = Modifier.testTag("register_dialog_transport_switch")
                    )
                }

                if (usesTransport) {
                    val schoolRoutes = remember(uiState.transportRoutes, uiState.activeSchoolDocId) {
                        uiState.transportRoutes.filter { it.schoolId == uiState.activeSchoolDocId && it.active }
                    }
                    if (schoolRoutes.isNotEmpty()) {
                        val pickedRoute = schoolRoutes.firstOrNull { it.id == transportRouteId }
                        Text(
                            text = "Quick Select Transport Route",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            schoolRoutes.forEach { rt ->
                                FilterChip(
                                    selected = transportRouteId == rt.id,
                                    onClick = {
                                        transportRouteId = rt.id
                                        val firstStop = rt.stops.firstOrNull()?.stopName.orEmpty()
                                        transportStopName = firstStop
                                        transportRouteName = if (firstStop.isNotBlank()) "${rt.routeName} - $firstStop" else rt.routeName
                                        // Preserve existing positive transport amount; only auto-fill if blank/0
                                        val curAmt = transportAmountText.toIntOrNull() ?: 0
                                        if (curAmt <= 0) {
                                            val suggested = rt.effectiveFeeForStop(firstStop)
                                            if (suggested > 0) transportAmountText = suggested.toString()
                                        }
                                    },
                                    label = { Text("${rt.routeName} (₹${rt.monthlyFeeRs})") }
                                )
                            }
                        }
                        if (pickedRoute != null && pickedRoute.stops.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                pickedRoute.stops.forEach { st ->
                                    val stopFee = pickedRoute.effectiveFeeForStop(st.stopName)
                                    FilterChip(
                                        selected = transportStopName.equals(st.stopName, ignoreCase = true),
                                        onClick = {
                                            transportStopName = st.stopName
                                            transportRouteName = "${pickedRoute.routeName} - ${st.stopName}"
                                            val curAmt = transportAmountText.toIntOrNull() ?: 0
                                            if (curAmt <= 0 && stopFee > 0) {
                                                transportAmountText = stopFee.toString()
                                            }
                                        },
                                        label = { Text("${st.stopName} (₹$stopFee)") }
                                    )
                                }
                            }
                            val effectiveFee = pickedRoute.effectiveFeeForStop(transportStopName)
                            val curAmt = transportAmountText.toIntOrNull() ?: 0
                            if (effectiveFee > 0 && curAmt > 0 && curAmt != effectiveFee) {
                                TextButton(
                                    onClick = { transportAmountText = effectiveFee.toString() }
                                ) {
                                    Text("Use route/stop fee ₹$effectiveFee/mo (currently ₹$curAmt)")
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = transportRouteName,
                            onValueChange = { transportRouteName = it },
                            label = { Text("Route / Bus Stop Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("register_dialog_route_input")
                        )
                        OutlinedTextField(
                            value = transportAmountText,
                            onValueChange = { transportAmountText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Monthly Rs. *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("register_dialog_transport_amount_input")
                        )
                    }
                }
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
                        transportStopName = if (usesTransport) transportStopName.trim() else "",
                        transportRouteName = if (usesTransport) transportRouteName.trim() else "",
                        transportMonthlyAmount = if (usesTransport) transportAmt else 0,
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
