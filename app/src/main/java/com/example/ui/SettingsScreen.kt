package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SubjectEntity
import com.example.domain.ClassGroup
import com.example.domain.ResolvedSchoolIdentity
import com.example.domain.SchoolClass
import com.example.domain.SchoolIdentityCode
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.util.PdfExportHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    uiState: CpiUiState,
    viewModel: CpiViewModel,
    focusBackupSection: Boolean = false,
    onBackupFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val pendingImport by viewModel.pendingImportBackup.collectAsStateWithLifecycle()
    val settings = uiState.settings

    // Selected school identity editor tab (CPS vs CPI)
    var editingIdentity by remember { mutableStateOf(SchoolIdentityCode.CPS) }

    var cpsName by remember(settings.cpsName) { mutableStateOf(settings.cpsName) }
    var cpsAddress by remember(settings.cpsAddress) { mutableStateOf(settings.cpsAddress) }
    var cpsMobile by remember(settings.cpsMobile) { mutableStateOf(settings.cpsMobile) }
    var cpsEmail by remember(settings.cpsEmail) { mutableStateOf(settings.cpsEmail) }

    var cpiName by remember(settings.cpiName) { mutableStateOf(settings.cpiName) }
    var cpiAddress by remember(settings.cpiAddress) { mutableStateOf(settings.cpiAddress) }
    var cpiMobile by remember(settings.cpiMobile) { mutableStateOf(settings.cpiMobile) }
    var cpiEmail by remember(settings.cpiEmail) { mutableStateOf(settings.cpiEmail) }

    var passPercentText by remember(settings.passPercent) { mutableStateOf(settings.passPercent.toString()) }

    var selectedGroup by remember { mutableStateOf(ClassGroup.PRIMARY) }
    var showAddSessionDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var renamingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var deletingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var confirmClearSample by remember { mutableStateOf(false) }
    var showMarksCsvDialog by remember { mutableStateOf(false) }

    LaunchedEffect(focusBackupSection) {
        if (focusBackupSection) {
            listState.animateScrollToItem(5)
            onBackupFocusConsumed()
        }
    }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.updateIdentityLogoFromUri(editingIdentity, uri)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackupToUri(uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.prepareBackupImportFromUri(uri)
    }

    val classIdentityMap = remember(settings.classIdentityMapCsv) {
        SchoolClass.parseIdentityMap(settings.classIdentityMapCsv)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. Header
        item {
            Column {
                Text(
                    text = "Settings & Backup",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "C.P.S. / C.P.I. school identities, class mapping, subjects, and JSON backup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. DUAL SCHOOL IDENTITIES (C.P.S. SCHOOL & C.P.I. SCHOOL)
        item {
            val previewIdentity = if (editingIdentity == SchoolIdentityCode.CPS) {
                ResolvedSchoolIdentity(
                    code = SchoolIdentityCode.CPS,
                    schoolName = cpsName.ifBlank { "C.P.S. SCHOOL" },
                    address = cpsAddress,
                    mobile = cpsMobile,
                    email = cpsEmail,
                    logoBase64 = settings.cpsLogoBase64,
                    logoCleared = settings.cpsLogoCleared
                )
            } else {
                val effectiveBase64 = if (settings.useSameLogoForBoth) settings.cpsLogoBase64 else settings.cpiLogoBase64
                val effectiveCleared = if (settings.useSameLogoForBoth) settings.cpsLogoCleared else settings.cpiLogoCleared
                ResolvedSchoolIdentity(
                    code = if (settings.useSameLogoForBoth) SchoolIdentityCode.CPS else SchoolIdentityCode.CPI,
                    schoolName = cpiName.ifBlank { "C.P.I. SCHOOL" },
                    address = cpiAddress,
                    mobile = cpiMobile,
                    email = cpiEmail,
                    logoBase64 = effectiveBase64,
                    logoCleared = effectiveCleared
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "School Identities (CPS & CPI)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = editingIdentity == SchoolIdentityCode.CPS,
                            onClick = { editingIdentity = SchoolIdentityCode.CPS },
                            label = { Text("C.P.S. (Nur–5th)", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("identity_tab_cps")
                        )
                        FilterChip(
                            selected = editingIdentity == SchoolIdentityCode.CPI,
                            onClick = { editingIdentity = SchoolIdentityCode.CPI },
                            label = { Text("C.P.I. (6th–8th)", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("identity_tab_cpi")
                        )
                    }

                    // Use same logo checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = settings.useSameLogoForBoth,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings(
                                    settings.copy(useSameLogoForBoth = checked),
                                    if (checked) "Using C.P.S. logo for both schools" else "Using separate logos for C.P.S. and C.P.I."
                                )
                            },
                            modifier = Modifier.testTag("checkbox_use_same_logo")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use the same logo for both schools",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (settings.useSameLogoForBoth)
                                    "Both schools share the C.P.S. logo slot"
                                else
                                    "Each school uses its own bundled or uploaded logo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Logo preview + upload / clear / restore controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SchoolLogoAvatar(identity = previewIdentity, size = 62.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = {
                                    logoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("upload_school_logo_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload ${editingIdentity.code} Logo")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { viewModel.clearIdentityLogo(editingIdentity) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Placeholder Box", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(
                                    onClick = { viewModel.restoreBundledIdentityLogo(editingIdentity) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Official Logo", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    if (editingIdentity == SchoolIdentityCode.CPS) {
                        OutlinedTextField(
                            value = cpsName,
                            onValueChange = { cpsName = it },
                            label = { Text("C.P.S. School Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = cpsAddress,
                            onValueChange = { cpsAddress = it },
                            label = { Text("C.P.S. Address") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = cpsMobile,
                                onValueChange = { cpsMobile = it },
                                label = { Text("Mobile") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = cpsEmail,
                                onValueChange = { cpsEmail = it },
                                label = { Text("E-mail") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.weight(1.1f)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = cpiName,
                            onValueChange = { cpiName = it },
                            label = { Text("C.P.I. School Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = cpiAddress,
                            onValueChange = { cpiAddress = it },
                            label = { Text("C.P.I. Address") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = cpiMobile,
                                onValueChange = { cpiMobile = it },
                                label = { Text("Mobile") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = cpiEmail,
                                onValueChange = { cpiEmail = it },
                                label = { Text("E-mail") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.weight(1.1f)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.updateSettings(
                                settings.copy(
                                    cpsName = cpsName.trim().ifEmpty { "C.P.S. SCHOOL" },
                                    cpsAddress = cpsAddress.trim(),
                                    cpsMobile = cpsMobile.trim(),
                                    cpsEmail = cpsEmail.trim(),
                                    cpiName = cpiName.trim().ifEmpty { "C.P.I. SCHOOL" },
                                    cpiAddress = cpiAddress.trim(),
                                    cpiMobile = cpiMobile.trim(),
                                    cpiEmail = cpiEmail.trim()
                                ),
                                "Saved C.P.S. & C.P.I. school profiles"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_school_settings_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save School Identities")
                    }
                }
            }
        }

        // 2. CLASS-TO-SCHOOL IDENTITY MAPPING
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Class-to-School Mapping",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Choose which school identity prints on each class's report",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                val defaults = SchoolClass.serializeIdentityMap(SchoolClass.defaultClassIdentityMap())
                                viewModel.updateSettings(
                                    settings.copy(classIdentityMapCsv = defaults),
                                    "Reset class-to-school mapping to defaults"
                                )
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }

                    SchoolClass.entries.forEachIndexed { idx, sc ->
                        val assigned = classIdentityMap[sc.displayName] ?: sc.defaultIdentity
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Class ${sc.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = assigned == SchoolIdentityCode.CPS,
                                    onClick = { viewModel.updateClassIdentityMapping(sc.displayName, SchoolIdentityCode.CPS) },
                                    label = { Text("C.P.S.", style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("map_${sc.displayName}_CPS")
                                )
                                FilterChip(
                                    selected = assigned == SchoolIdentityCode.CPI,
                                    onClick = { viewModel.updateClassIdentityMapping(sc.displayName, SchoolIdentityCode.CPI) },
                                    label = { Text("C.P.I.", style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    modifier = Modifier.testTag("map_${sc.displayName}_CPI")
                                )
                            }
                        }
                        if (idx < SchoolClass.entries.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        // 3. SESSION, PASS % & ANNUAL CARD DISPLAY OPTIONS
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Session & Report Options",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = { showAddSessionDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Session")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactDropdownSelector(
                            label = "Active Session",
                            selectedValue = uiState.filter.session,
                            options = settings.sessionsList(),
                            onSelect = { sess ->
                                viewModel.selectSession(sess)
                                viewModel.updateSettings(settings.copy(currentSession = sess), "Active session: $sess")
                            },
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = passPercentText,
                            onValueChange = { raw ->
                                val cleaned = raw.filter { ch -> ch.isDigit() }.take(3)
                                passPercentText = cleaned
                                cleaned.toIntOrNull()?.takeIf { it in 1..100 }?.let { pct ->
                                    if (pct != settings.passPercent) {
                                        viewModel.updateSettings(settings.copy(passPercent = pct), "Pass % set to $pct%")
                                    }
                                }
                            },
                            label = { Text("Pass %") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.75f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Unified Exam Structure: IA (10) + U.T. (30) + Exam (60) = 100",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Used consistently for both Term-I and Term-II (Annual Grand Total = 200 per subject).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show photo and admission no on Annual card",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Off by default to match the school's printed Annual card layout",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.showPhotoAndAdmOnAnnual,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings(
                                    settings.copy(showPhotoAndAdmOnAnnual = checked),
                                    if (checked) "Photo & Adm No. enabled on Annual card" else "Photo & Adm No. hidden on Annual card"
                                )
                            },
                            modifier = Modifier.testTag("toggle_photo_adm_annual")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Class Rank on Annual Card",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Ranked by overall total within session, class & section",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.showClassRank,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings(settings.copy(showClassRank = checked))
                            },
                            modifier = Modifier.testTag("toggle_class_rank")
                        )
                    }
                }
            }
        }

        // 4. EDITABLE SUBJECT LISTS PER CLASS GROUP
        item {
            val groupSubjects = uiState.subjectsForGroup(selectedGroup)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Subjects by Class Group",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Add, rename, reorder, or remove subjects",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.resetGroupSubjects(selectedGroup) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Defaults")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ClassGroup.entries.forEach { grp ->
                            FilterChip(
                                selected = selectedGroup == grp,
                                onClick = { selectedGroup = grp },
                                label = { Text(grp.shortLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    groupSubjects.forEachIndexed { idx, subject ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = subject.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.moveSubject(selectedGroup, subject.id, moveUp = true) },
                                enabled = idx > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { viewModel.moveSubject(selectedGroup, subject.id, moveUp = false) },
                                enabled = idx < groupSubjects.lastIndex
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { renamingSubject = subject }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename subject", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { deletingSubject = subject }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete subject",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (idx < groupSubjects.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    OutlinedButton(
                        onClick = { showAddSubjectDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Subject to ${selectedGroup.shortLabel}")
                    }
                }
            }
        }

        // 5. JSON BACKUP & RESTORE
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Offline Backup & Restore (JSON)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val lastBackupLabel = if (settings.lastBackupEpochMs > 0L) {
                        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
                        "Last backup: ${fmt.format(Date(settings.lastBackupEpochMs))}"
                    } else {
                        "Last backup: Never backed up yet"
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (uiState.isBackupOverdue) AbsentAmberContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.isBackupOverdue) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AbsentAmber, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = lastBackupLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (uiState.isBackupOverdue) Color(0xFF4E2D00) else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "All records live locally on this device. Export a JSON backup regularly to keep your school data safe.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.isBackupOverdue) Color(0xFF6B4200) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                exportBackupLauncher.launch("CPI_CPS_Backup_${uiState.filter.session}.json")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_backup_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save JSON", maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.createShareableBackupFile { file ->
                                    PdfExportHelper.shareFile(
                                        context = context,
                                        file = file,
                                        mimeType = "application/json",
                                        chooserTitle = "Share CPI-CPS Report Card Backup"
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_backup_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share JSON", maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("import_backup_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Backup JSON (Replace or Merge)")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedButton(
                        onClick = { showMarksCsvDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_import_marks_csv_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Marks from CSV / Download Template")
                    }
                }
            }
        }

        // 6. SAMPLE DATA CONTROLS
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
                    Text(
                        text = "Sample Preview Data",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (uiState.sampleStudentCount > 0)
                            "${uiState.sampleStudentCount} sample student(s) (Nursery, 2nd, 6th) are loaded so you can preview both C.P.S. and C.P.I. reports right away."
                        else
                            "No sample students currently loaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.sampleStudentCount > 0) {
                            OutlinedButton(
                                onClick = { confirmClearSample = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("clear_sample_data_button")
                            ) {
                                Text("Clear Sample Data")
                            }
                        } else {
                            OutlinedButton(
                                onClick = viewModel::restoreSampleData,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                            ) {
                                Text("Load Sample Data")
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    MarksCsvImportController(
        uiState = uiState,
        viewModel = viewModel,
        showSetupDialog = showMarksCsvDialog,
        onDismissSetupDialog = { showMarksCsvDialog = false }
    )

    if (showAddSessionDialog) {
        SingleInputDialog(
            title = "Add Academic Session",
            label = "Session (e.g. 2027-2028)",
            initialValue = "",
            confirmLabel = "Add",
            onConfirm = {
                viewModel.addSession(it)
                showAddSessionDialog = false
            },
            onDismiss = { showAddSessionDialog = false }
        )
    }

    if (showAddSubjectDialog) {
        SingleInputDialog(
            title = "Add Subject",
            label = "Subject Name",
            initialValue = "",
            confirmLabel = "Add",
            onConfirm = {
                viewModel.addSubject(selectedGroup, it)
                showAddSubjectDialog = false
            },
            onDismiss = { showAddSubjectDialog = false }
        )
    }

    renamingSubject?.let { sub ->
        SingleInputDialog(
            title = "Rename Subject",
            label = "Subject Name",
            initialValue = sub.name,
            confirmLabel = "Rename",
            onConfirm = {
                viewModel.renameSubject(sub, it)
                renamingSubject = null
            },
            onDismiss = { renamingSubject = null }
        )
    }

    deletingSubject?.let { sub ->
        ConfirmDangerDialog(
            title = "Remove Subject?",
            message = "Remove '${sub.name}' from ${ClassGroup.fromId(sub.classGroup).label}?",
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.deleteSubject(sub)
                deletingSubject = null
            },
            onDismiss = { deletingSubject = null }
        )
    }

    if (confirmClearSample) {
        ConfirmDangerDialog(
            title = "Clear Sample Data?",
            message = "Remove all ${uiState.sampleStudentCount} sample preview student(s) and their sample marks? Your real student records will not be touched.",
            confirmLabel = "Clear Sample Data",
            onConfirm = {
                viewModel.clearSampleData()
                confirmClearSample = false
            },
            onDismiss = { confirmClearSample = false }
        )
    }

    pendingImport?.let { backup ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingBackupImport,
            title = { Text("Restore Backup", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Backup contains ${backup.students.size} student(s), ${backup.marks.size} mark entries, and ${backup.subjects.size} subject definitions (${backup.settings.cpsName} & ${backup.settings.cpiName}).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Choose how to import:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Merge: keeps existing students and adds/updates matching Admission Nos.\n• Replace All: clears current device data and restores the backup exactly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { viewModel.confirmBackupImport(replaceAll = false) }) {
                        Text("Merge", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.confirmBackupImport(replaceAll = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Replace All")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPendingBackupImport) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SingleInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
