package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ClassGroupEntity
import com.example.data.SchoolClassEntity
import com.example.data.SchoolEntity
import com.example.data.SchoolTemplateSource
import com.example.domain.AppModule
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.PassGreen
import com.example.ui.theme.PassGreenContainer
import com.example.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SchoolsScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingSchool by remember { mutableStateOf<SchoolEntity?>(null) }
    var managingClassesSchoolId by remember { mutableStateOf<Long?>(null) }
    var deletingSchool by remember { mutableStateOf<SchoolEntity?>(null) }
    val canEditSchools = uiState.canEdit(AppModule.SETTINGS)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("schools_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Schools & Classes",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${uiState.activeSchools.size} active  •  ${uiState.schools.size} total school(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.schools.isEmpty()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    EmptyStatePanel(
                        icon = Icons.Default.Apartment,
                        title = "No schools added yet",
                        message = if (canEditSchools)
                            "Tap + to add your first school with its own logo, address, classes, and subjects."
                        else
                            "No schools assigned to your account yet.",
                        actionLabel = if (canEditSchools) "Add School" else null,
                        onAction = if (canEditSchools) {
                            {
                                editingSchool = null
                                showAddEditDialog = true
                            }
                        } else null
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.schools, key = { it.id }) { school ->
                        val studentCount = uiState.studentCountForSchool(school.id)
                        val classCount = uiState.classCountForSchool(school.id)
                        val isCurrent = uiState.activeSchool.id == school.id

                        SchoolListCard(
                            school = school,
                            studentCount = studentCount,
                            classCount = classCount,
                            isCurrent = isCurrent,
                            canEdit = canEditSchools,
                            onSelect = { viewModel.selectSchool(school.id) },
                            onEdit = {
                                editingSchool = school
                                showAddEditDialog = true
                            },
                            onManageClasses = { managingClassesSchoolId = school.id },
                            onToggleActive = { viewModel.setSchoolActive(school, !school.active) },
                            onDelete = { deletingSchool = school }
                        )
                    }
                }
            }
        }

        if (canEditSchools) {
            FloatingActionButton(
                onClick = {
                    editingSchool = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_school_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add School")
            }
        }
    }

    if (showAddEditDialog) {
        SchoolFormDialog(
            existing = editingSchool,
            existingSchools = uiState.schools,
            onSaveNew = { name, address, mobile, email, logoBase64, active, templateSource, onResult ->
                viewModel.createSchool(name, address, mobile, email, logoBase64, active, templateSource, onResult)
            },
            onSaveEdit = { updated, onResult ->
                viewModel.updateSchool(updated, onResult)
            },
            onDismiss = { showAddEditDialog = false }
        )
    }

    val targetSchoolForClasses = managingClassesSchoolId?.let { uiState.schoolById(it) }
    if (targetSchoolForClasses != null) {
        SchoolClassesManagerDialog(
            school = targetSchoolForClasses,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { managingClassesSchoolId = null }
        )
    }

    deletingSchool?.let { school ->
        val count = uiState.studentCountForSchool(school.id)
        if (count > 0) {
            AlertDialog(
                onDismissRequest = { deletingSchool = null },
                title = { Text("Cannot Delete School", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Text(
                        text = "'${school.name}' still has $count student(s). Remove its students first or deactivate the school instead.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setSchoolActive(school, false)
                            deletingSchool = null
                        }
                    ) {
                        Text("Deactivate Instead")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingSchool = null }) { Text("Close") }
                }
            )
        } else {
            ConfirmDangerDialog(
                title = "Delete '${school.name}'?",
                message = "This school has 0 students. Deleting it will permanently remove the school along with its class and subject setup.",
                confirmLabel = "Delete School",
                onConfirm = {
                    viewModel.deleteSchool(school)
                    deletingSchool = null
                },
                onDismiss = { deletingSchool = null }
            )
        }
    }
}

@Composable
private fun SchoolListCard(
    school: SchoolEntity,
    studentCount: Int,
    classCount: Int,
    isCurrent: Boolean,
    canEdit: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onManageClasses: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isCurrent) 1.5.dp else 1.dp,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = school.active, onClick = onSelect)
            .testTag("school_card_${school.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SchoolLogoAvatar(school = school, size = 56.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = school.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (school.active) PassGreenContainer else AbsentAmberContainer
                        ) {
                            Text(
                                text = if (school.active) "ACTIVE" else "INACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (school.active) PassGreen else AbsentAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (school.address.isNotBlank()) {
                        Text(
                            text = school.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val contact = listOfNotNull(
                        school.mobile.takeIf { it.isNotBlank() }?.let { "Mob: $it" },
                        school.email.takeIf { it.isNotBlank() }
                    ).joinToString("  •  ")
                    if (contact.isNotBlank()) {
                        Text(
                            text = contact,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$classCount class(es)  •  $studentCount student(s)" +
                            if (isCurrent) "  •  Currently Selected" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canEdit) {
                        OutlinedButton(
                            onClick = onManageClasses,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("manage_classes_${school.id}")
                        ) {
                            Icon(Icons.Default.Class, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Classes", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (school.active && !isCurrent) {
                        OutlinedButton(
                            onClick = onSelect,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("use_school_${school.id}")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (canEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_school_${school.id}")) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit school",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onToggleActive, modifier = Modifier.testTag("toggle_school_active_${school.id}")) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = if (school.active) "Deactivate school" else "Activate school",
                                tint = if (school.active) AbsentAmber else PassGreen
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_school_${school.id}")) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete school",
                                tint = if (studentCount == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolFormDialog(
    existing: SchoolEntity?,
    existingSchools: List<SchoolEntity>,
    onSaveNew: (String, String, String, String, String, Boolean, SchoolTemplateSource, (Boolean, String?) -> Unit) -> Unit,
    onSaveEdit: (SchoolEntity, (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var mobile by remember { mutableStateOf(existing?.mobile ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var logoBase64 by remember { mutableStateOf(existing?.logoBase64 ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var useCopyTemplate by remember { mutableStateOf(false) }
    var copySourceSchoolId by remember {
        mutableLongStateOf(existingSchools.firstOrNull()?.id ?: 0L)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    ImageUtils.encodeUriToBase64Under200Kb(context, uri)
                }
                if (encoded != null) {
                    logoBase64 = encoded
                    errorMessage = null
                } else {
                    errorMessage = "Could not process selected logo image."
                }
            }
        }
    }

    val logoKb = remember(logoBase64) {
        val bytes = ImageUtils.estimateDecodedBytes(logoBase64)
        if (bytes > 0) ((bytes + 1023) / 1024) else 0
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "Add School" else "Edit School",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SchoolLogoAvatar(
                        logoBase64 = logoBase64,
                        schoolName = name.ifBlank { "SC" },
                        size = 64.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = {
                                logoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("school_pick_logo_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (logoBase64.isEmpty()) "Upload Logo (<200 KB)" else "Change Logo")
                        }
                        if (logoBase64.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Ready (~${logoKb} KB)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PassGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(
                                    onClick = { logoBase64 = "" },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("School Name*") },
                    placeholder = { Text("e.g. STS Public School") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("school_name_input")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("School Address") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-mail") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.weight(1.1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active School", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Shown in the top school switcher",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                if (existing == null) {
                    HorizontalDivider()
                    Text(
                        text = "Initial Classes & Subjects",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useCopyTemplate = false },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !useCopyTemplate, onClick = { useCopyTemplate = false })
                        Column {
                            Text("Built-in Default Template", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Nursery–UKG (7), 1st–5th (6), 6th–8th (8 subjects)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (existingSchools.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { useCopyTemplate = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = useCopyTemplate, onClick = { useCopyTemplate = true })
                            Column {
                                Text("Copy from Existing School", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Copies class list, class groups, and subject lists",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (useCopyTemplate) {
                            val selectedSourceName = existingSchools
                                .firstOrNull { it.id == copySourceSchoolId }?.name
                                ?: existingSchools.first().name
                            CompactDropdownSelector(
                                label = "Copy Structure From",
                                selectedValue = selectedSourceName,
                                options = existingSchools.map { it.name },
                                onSelect = { chosen ->
                                    existingSchools.firstOrNull { it.name == chosen }?.let {
                                        copySourceSchoolId = it.id
                                    }
                                }
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "School name is required."
                        return@Button
                    }
                    if (existing == null) {
                        val templateSource = if (useCopyTemplate && copySourceSchoolId > 0L) {
                            SchoolTemplateSource.CopyFromSchool(copySourceSchoolId)
                        } else {
                            SchoolTemplateSource.DefaultTemplate
                        }
                        onSaveNew(name, address, mobile, email, logoBase64, active, templateSource) { ok, err ->
                            if (ok) onDismiss() else errorMessage = err
                        }
                    } else {
                        onSaveEdit(
                            existing.copy(
                                name = name,
                                address = address,
                                mobile = mobile,
                                email = email,
                                logoBase64 = logoBase64,
                                active = active
                            )
                        ) { ok, err ->
                            if (ok) onDismiss() else errorMessage = err
                        }
                    }
                },
                modifier = Modifier.testTag("save_school_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SchoolClassesManagerDialog(
    school: SchoolEntity,
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    onDismiss: () -> Unit
) {
    val classes = uiState.classesForSchool(school.id)
    val groups = uiState.classGroupsForSchool(school.id)
    val groupById = remember(groups) { groups.associateBy { it.id } }

    var showAddClassDialog by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<SchoolClassEntity?>(null) }
    var showAddGroupDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "${school.name} — Classes",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${classes.size} class(es)  •  ${groups.size} class group(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAddClassDialog = true },
                        enabled = groups.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_class_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Class", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showAddGroupDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_class_group_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Group", maxLines = 1)
                    }
                }

                if (classes.isEmpty()) {
                    Text(
                        text = "No classes yet. Tap 'Add Class' to create classes such as Nursery, 1st, 9th, or 10th.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    classes.forEachIndexed { idx, sc ->
                        val grpName = groupById[sc.classGroupId]?.shortLabel ?: "Unassigned"
                        val stCount = uiState.studentCountForClass(sc)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sc.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$grpName  •  $stCount student(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.moveSchoolClass(school.id, sc.id, moveUp = true) },
                                enabled = idx > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move class up", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { viewModel.moveSchoolClass(school.id, sc.id, moveUp = false) },
                                enabled = idx < classes.lastIndex
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move class down", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { editingClass = sc }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit class", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.deleteSchoolClass(sc) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete class",
                                    tint = if (stCount == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (idx < classes.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )

    if (showAddClassDialog || editingClass != null) {
        ClassFormDialog(
            existing = editingClass,
            groups = groups,
            onSave = { className, groupId, onResult ->
                val target = editingClass
                if (target == null) {
                    viewModel.addSchoolClass(school.id, groupId, className, onResult)
                } else {
                    viewModel.updateSchoolClass(target, className, groupId, onResult)
                }
            },
            onDismiss = {
                showAddClassDialog = false
                editingClass = null
            }
        )
    }

    if (showAddGroupDialog) {
        AddClassGroupDialog(
            schoolName = school.name,
            onSave = { name, shortLabel, subjects, onResult ->
                viewModel.addClassGroup(school.id, name, shortLabel, subjects, onResult)
            },
            onDismiss = { showAddGroupDialog = false }
        )
    }
}

@Composable
private fun ClassFormDialog(
    existing: SchoolClassEntity?,
    groups: List<ClassGroupEntity>,
    onSave: (String, Long, (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var selectedGroupId by remember {
        mutableLongStateOf(existing?.classGroupId ?: groups.lastOrNull()?.id ?: 0L)
    }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedGroupName = groups.firstOrNull { it.id == selectedGroupId }?.name
        ?: groups.firstOrNull()?.name
        ?: "Select Group"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Class" else "Edit Class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Class Name (e.g. 9th, 10th, Playgroup)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_name_input")
                )
                CompactDropdownSelector(
                    label = "Class Group (Subject List)",
                    selectedValue = selectedGroupName,
                    options = groups.map { it.name },
                    onSelect = { chosen ->
                        groups.firstOrNull { it.name == chosen }?.let { selectedGroupId = it.id }
                    }
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Class name is required."
                        return@Button
                    }
                    onSave(name, selectedGroupId) { ok, err ->
                        if (ok) onDismiss() else error = err
                    }
                },
                modifier = Modifier.testTag("save_class_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddClassGroupDialog(
    schoolName: String,
    onSave: (String, String, List<String>, (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var shortLabel by remember { mutableStateOf("") }
    var subjectsCsv by remember { mutableStateOf("ENGLISH, HINDI, MATHS, SCIENCE, SOCIAL SCIENCE, COMPUTER") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Class Group ($schoolName)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name (e.g. 9th – 10th)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_name_input")
                )
                OutlinedTextField(
                    value = shortLabel,
                    onValueChange = { shortLabel = it },
                    label = { Text("Short Chip Label (e.g. 9th–10th)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subjectsCsv,
                    onValueChange = { subjectsCsv = it },
                    label = { Text("Initial Subjects (comma-separated)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Group name is required."
                        return@Button
                    }
                    val subs = subjectsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(name, shortLabel, subs) { ok, err ->
                        if (ok) onDismiss() else error = err
                    }
                },
                modifier = Modifier.testTag("save_group_button")
            ) {
                Text("Add Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
