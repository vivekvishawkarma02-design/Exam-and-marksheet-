package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.LegacyBackupPayload
import com.example.data.LegacyImportTarget
import com.example.data.SchoolEntity
import com.example.data.SubjectEntity
import com.example.data.cloud.LocalMigrationSummary
import com.example.domain.AppModule
import com.example.domain.ExamType
import com.example.ads.findActivityOrNull
import com.example.domain.LegalConfig
import com.example.ui.theme.AbsentAmber
import com.example.ui.theme.AbsentAmberContainer
import com.example.ui.theme.AppearancePrefsStore
import com.example.ui.theme.LocalAppearanceController
import com.example.util.PdfExportHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    uiState: SchoolUiState,
    viewModel: SchoolViewModel,
    focusBackupSection: Boolean = false,
    onBackupFocusConsumed: () -> Unit = {},
    onManageSchools: () -> Unit = {},
    localMigrationSummary: LocalMigrationSummary = LocalMigrationSummary(),
    onOpenLocalMigration: () -> Unit = {},
    onSwitchOrganisation: () -> Unit = {},
    onLeaveOrganisation: () -> Unit = {},
    onDeleteOrganisationClick: () -> Unit = {},
    onOpenLegalDoc: (LegalDocType) -> Unit = {},
    onOpenSubscriptionScreen: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onDeleteAccountClick: () -> Unit = {},
    onOpenDesignPreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appearanceController = LocalAppearanceController.current
        ?: remember(context) { AppearancePrefsStore(context.applicationContext) }
    val appearanceSettings by appearanceController.settings.collectAsStateWithLifecycle()
    val activity = remember(context) { context.findActivityOrNull() }
    val adsUiState by viewModel.adsUiState.collectAsStateWithLifecycle()
    val isHindi = uiState.phase10HindiLabels
    var showPrivacyAdChoicesDialog by remember { mutableStateOf(false) }
    var privacyFeedback by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val pendingImport by viewModel.pendingImportBackup.collectAsStateWithLifecycle()
    val pendingLegacyImport by viewModel.pendingLegacyBackup.collectAsStateWithLifecycle()
    val settings = uiState.settings
    val activeSchool = uiState.activeSchool
    val schoolGroups = uiState.activeSchoolGroups
    val canEditSettings = uiState.canEdit(AppModule.SETTINGS)
    val canViewSettings = uiState.canView(AppModule.SETTINGS)

    var passPercentText by remember(settings.passPercent) { mutableStateOf(settings.passPercent.toString()) }
    var selectedGroupId by remember(activeSchool.id, schoolGroups) {
        mutableLongStateOf(schoolGroups.firstOrNull()?.id ?: 0L)
    }
    val selectedGroup = schoolGroups.firstOrNull { it.id == selectedGroupId } ?: schoolGroups.firstOrNull()

    // Backup scope: "All schools" vs active school only
    var backupAllSchools by remember { mutableStateOf(true) }

    var showAddSessionDialog by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var renamingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var deletingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var confirmClearSample by remember { mutableStateOf(false) }
    var confirmLeaveOrg by remember { mutableStateOf(false) }
    var showMarksCsvDialog by remember { mutableStateOf(false) }

    LaunchedEffect(focusBackupSection) {
        if (focusBackupSection && canEditSettings) {
            listState.animateScrollToItem(6)
            onBackupFocusConsumed()
        } else if (focusBackupSection) {
            onBackupFocusConsumed()
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackupToUri(uri, allSchools = backupAllSchools)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.prepareBackupImportFromUri(uri)
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
                    text = if (canViewSettings) "Settings, Cloud & Backup" else "Account & Organisation",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (canViewSettings)
                        "Organisation account, session rules, school subjects, and JSON backup"
                    else
                        "Switch organisation, view privacy terms, sign out, or manage your account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 0.5 ORGANISATION & CLOUD ACCOUNT CARD (Always visible to every signed-in member)
        item {
            val activeOrg = uiState.activeOrg
            val member = uiState.currentMember
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
                                text = activeOrg?.name ?: "Organisation & Account",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sub = buildString {
                                if (activeOrg != null) append("@${activeOrg.code}")
                                if (member != null) {
                                    if (isNotEmpty()) append("  •  ")
                                    append(member.orgRole.label)
                                }
                            }
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        SyncStatusChip(status = uiState.syncStatus)
                    }

                    if (member != null) {
                        Text(
                            text = "Signed in as ${member.name.ifBlank { member.email }} (${member.email})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (localMigrationSummary.hasDataToUpload && uiState.isAdmin) {
                        Button(
                            onClick = onOpenLocalMigration,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_upload_local_data_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload This Phone's Data (${localMigrationSummary.studentCount} students)")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSwitchOrganisation,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_switch_org_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Switch Org", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_sign_out_button"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign Out", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { onOpenLegalDoc(LegalDocType.PRIVACY_POLICY) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_privacy_policy_button")
                        ) {
                            Icon(Icons.Default.PrivacyTip, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Privacy Policy", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(
                            onClick = { onOpenLegalDoc(LegalDocType.TERMS_OF_SERVICE) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_terms_button")
                        ) {
                            Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Terms", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (activeOrg != null && !uiState.isOwner) {
                        OutlinedButton(
                            onClick = { confirmLeaveOrg = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_leave_org_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Leave Organisation")
                        }
                    }

                    if (activeOrg != null && uiState.isOwner) {
                        OutlinedButton(
                            onClick = onDeleteOrganisationClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_delete_org_button")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Organisation (Requires Backup)")
                        }
                    }

                    TextButton(
                        onClick = onDeleteAccountClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("settings_delete_account_button")
                    ) {
                        Text("Delete My Account")
                    }

                    Text(
                        text = "Cloud sync & cache: Your organisation's records are stored securely in Google Cloud Firestore and cached on-device for offline use. Signing out clears the local cloud cache on this phone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 0.75 APPEARANCE (Light / Dark / System + Simple Look)
        item {
            AppearanceSettingsCard(
                settings = appearanceSettings,
                onThemeModeChange = appearanceController::setThemeMode,
                onSimpleLookChange = appearanceController::setSimpleLook
            )
        }

        if (canViewSettings && !canEditSettings) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AbsentAmberContainer,
                    border = BorderStroke(1.dp, AbsentAmber.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "View-only Settings access: You can view the active session and subject configuration, but only members with Settings edit permission can modify them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4E2D00),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // 1. SCHOOLS & CLASSES SHORTCUT CARD
        if (canEditSettings) {
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SchoolLogoAvatar(school = activeSchool, size = 50.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Schools, Logos & Classes",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Active: ${activeSchool.name}  •  ${uiState.activeSchools.size} active / ${uiState.schools.size} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "Add any number of schools with their own name, address, mobile, email, logo (<200 KB), and custom classes (such as 9th or 10th).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onManageSchools,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_manage_schools_button")
                    ) {
                        Icon(Icons.Default.Apartment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Schools & Classes")
                    }
                    }
                }
            }
        }

        // 2. GLOBAL SESSION, PASS % & ANNUAL CARD OPTIONS
        if (canEditSettings) {
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
                            text = "Global Session & Report Options",
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
                                text = "Ranked by overall total within school, session, class & section",
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

            // 2.5 PHASE 7: PER-SCHOOL REPORT CARD LINKS (OFF BY DEFAULT) & OPEN EXAMS FOR TEACHERS
            item {
                val printCtName = uiState.activeSchoolPrintClassTeacherName
                val checkReady = uiState.activeSchoolCheckReportReady
                val openExams = uiState.activeSchoolOpenExams

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.testTag("settings_report_links_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Report Card Links & Open Exams (${activeSchool.name})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Per-school optional switches (both OFF by default so existing report card layouts stay untouched) and which exams teachers can enter marks for.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Print Class Teacher's name on reports",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Shows assigned Class Teacher name under \"CLASS TEACHER'S SIGN\" on UT, Half-Yearly & Annual PDFs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = printCtName,
                                onCheckedChange = { checked ->
                                    viewModel.updateSchoolReportCardLinks(
                                        schoolId = activeSchool.id,
                                        printClassTeacherNameOnReports = checked,
                                        checkReportCardsReadyBeforeBatch = checkReady,
                                        openExamsForTeachers = openExams
                                    )
                                },
                                modifier = Modifier.testTag("toggle_print_class_teacher_name")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "\"Report cards ready\" check before batch export",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Warns with a student list if marks, attendance, co-scholastic grades, or remarks are missing",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = checkReady,
                                onCheckedChange = { checked ->
                                    viewModel.updateSchoolReportCardLinks(
                                        schoolId = activeSchool.id,
                                        printClassTeacherNameOnReports = printCtName,
                                        checkReportCardsReadyBeforeBatch = checked,
                                        openExamsForTeachers = openExams
                                    )
                                },
                                modifier = Modifier.testTag("toggle_report_cards_ready_check")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = "Exams Open for Teacher Marks Entry",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Teachers can only edit marks for exams toggled ON below (Admins keep full access).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ExamType.entries.forEach { exam ->
                                val isOpen = openExams.any { it.equals(exam.name, ignoreCase = true) }
                                FilterChip(
                                    selected = isOpen,
                                    onClick = {
                                        val updated = if (isOpen) {
                                            openExams.filterNot { it.equals(exam.name, ignoreCase = true) }
                                        } else {
                                            (openExams + exam.name).distinct()
                                        }
                                        viewModel.updateSchoolReportCardLinks(
                                            schoolId = activeSchool.id,
                                            printClassTeacherNameOnReports = printCtName,
                                            checkReportCardsReadyBeforeBatch = checkReady,
                                            openExamsForTeachers = updated
                                        )
                                    },
                                    label = { Text(if (isOpen) "${exam.label} • Open" else "${exam.label} • Closed") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("open_exam_chip_${exam.name}")
                                )
                            }
                        }
                    }
                }
            }

            // 2.75 PHASE 8: PER-SCHOOL STUDENT ATTENDANCE SETTINGS
            item {
                val schDocId = uiState.activeSchoolDocId
                val autoFillAttendance = uiState.activeSchoolAutoFillAttendance
                val savedDaysBack = uiState.activeSchoolClassTeacherDaysBack
                var daysBackText by remember(schDocId, savedDaysBack) { mutableStateOf(savedDaysBack.toString()) }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.testTag("settings_student_attendance_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Student Attendance Settings (${activeSchool.name})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Controls how daily student attendance links to report cards and how many past days a Class Teacher may edit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Fill report card attendance automatically from daily attendance",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "OFF by default so existing report cards stay untouched. When ON, manually typed values remain protected with a Manual badge.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoFillAttendance,
                                onCheckedChange = { checked ->
                                    if (schDocId.isNotBlank()) {
                                        viewModel.updateSchoolAttendanceSettings(
                                            schoolDocId = schDocId,
                                            autoFillReportAttendance = checked,
                                            classTeacherDaysBack = (daysBackText.toIntOrNull() ?: savedDaysBack).coerceIn(0, 365)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("toggle_autofill_report_attendance")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = daysBackText,
                                onValueChange = { daysBackText = it.filter(Char::isDigit).take(3) },
                                label = { Text("Class teacher edit limit (days back)") },
                                supportingText = { Text("Default 7 days (0–365). Admins always have unlimited access.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_class_teacher_days_back")
                            )
                            Button(
                                onClick = {
                                    val days = (daysBackText.toIntOrNull() ?: 7).coerceIn(0, 365)
                                    daysBackText = days.toString()
                                    if (schDocId.isNotBlank()) {
                                        viewModel.updateSchoolAttendanceSettings(
                                            schoolDocId = schDocId,
                                            autoFillReportAttendance = autoFillAttendance,
                                            classTeacherDaysBack = days
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("save_class_teacher_days_back_button")
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }

        // 3. EDITABLE SUBJECT LISTS PER CLASS GROUP (for active school)
        if (canEditSettings) {
            item {
                val groupSubjects = selectedGroup?.let { uiState.subjectsForGroupId(it.id) }.orEmpty()
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
                                text = "Subjects (${activeSchool.name})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Add groups or edit, rename & reorder subjects",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { showAddGroupDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Group")
                            }
                            if (selectedGroup != null && selectedGroup.templateKey.isNotEmpty()) {
                                TextButton(onClick = { viewModel.resetGroupSubjects(selectedGroup) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Defaults")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        schoolGroups.forEach { grp ->
                            FilterChip(
                                selected = selectedGroup?.id == grp.id,
                                onClick = { selectedGroupId = grp.id },
                                label = { Text(grp.shortLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier.testTag("subject_group_chip_${grp.id}")
                            )
                        }
                    }

                    if (groupSubjects.isEmpty()) {
                        Text(
                            text = "No subjects configured in this group yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
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
                                    onClick = {
                                        selectedGroup?.let { viewModel.moveSubject(it.id, subject.id, moveUp = true) }
                                    },
                                    enabled = idx > 0
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        selectedGroup?.let { viewModel.moveSubject(it.id, subject.id, moveUp = false) }
                                    },
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
                    }

                    if (selectedGroup != null) {
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
            }
        }

        // 4. JSON BACKUP & RESTORE (with "All schools" vs Active School export scope)
        if (canEditSettings) {
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
                                    text = "Backups include schools, logos, classes, subjects, students, marks, attendance, and settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.isBackupOverdue) Color(0xFF6B4200) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Backup Export Scope: All Schools (recommended) vs Active School Only
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = backupAllSchools,
                            onClick = { backupAllSchools = true },
                            label = { Text("All Schools (${uiState.schools.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_scope_all_schools")
                        )
                        FilterChip(
                            selected = !backupAllSchools,
                            onClick = { backupAllSchools = false },
                            label = {
                                Text(
                                    text = activeSchool.name,
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
                                .testTag("backup_scope_active_school")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                exportBackupLauncher.launch(viewModel.suggestedBackupFileName(backupAllSchools))
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
                                viewModel.createShareableBackupFile(allSchools = backupAllSchools) { file ->
                                    PdfExportHelper.shareFile(
                                        context = context,
                                        file = file,
                                        mimeType = "application/json",
                                        chooserTitle = "Share School Report Card Backup"
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
                        Text("Import Backup JSON (v3 or Older Backup)")
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
        }

        // 5. SAMPLE DATA CONTROLS
        if (canEditSettings) {
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
                            "${uiState.sampleStudentCount} sample student(s) (Nursery, 2nd, 6th) are loaded for quick report card preview."
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
                                Text("Load Sample Data into ${activeSchool.name}")
                            }
                        }
                    }
                }
                }
            }
        }

        // PHASE 16A: ADS, AD-FREE TIME, PRIVACY & AD CHOICES (NO banner ads ever in Settings)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoveAdsForEveryoneCard(
                    effectiveSubscription = adsUiState.effectiveSubscription,
                    isOwner = uiState.isOwner,
                    isHindi = isHindi,
                    onOpenSubscriptionScreen = onOpenSubscriptionScreen
                )

                if (adsUiState.rewardedOfferStatus.visible) {
                    RewardedAdFreeCard(
                        offerStatus = adsUiState.rewardedOfferStatus,
                        isHindi = isHindi,
                        isRewardedLoading = adsUiState.isRewardedLoading,
                        onWatchConfirmed = { viewModel.watchRewardedAdForAdFreeTime(activity) },
                        testTagPrefix = "settings_rewarded"
                    )
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = p10Label(isHindi, "गोपनीयता और विज्ञापन विकल्प", "Privacy and ad choices"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = p10Label(
                                isHindi,
                                "विज्ञापन सहमति विकल्प बदलें या अपने फोन की सेटिंग्स से व्यक्तिगत विज्ञापन बंद करें। कोई भी स्कूल या छात्र डेटा विज्ञापन SDK को नहीं भेजा जाता।",
                                "Manage your ad consent choices or opt out of personalised ads in device settings. No school or student data is ever sent to the ad SDK."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!privacyFeedback.isNullOrBlank()) {
                            Text(
                                text = privacyFeedback!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        OutlinedButton(
                            onClick = { showPrivacyAdChoicesDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_privacy_ad_choices_button")
                        ) {
                            Icon(Icons.Default.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(p10Label(isHindi, "गोपनीयता और विज्ञापन विकल्प", "Privacy and ad choices"))
                        }
                    }
                }

                // Debug-only hidden developer switch (stripped from release builds)
                if (BuildConfig.DEBUG) {
                    DebugAdsSimulationSection(
                        isHindi = isHindi,
                        currentSimulation = adsUiState.debugSimulation,
                        bypassInstallGrace = adsUiState.debugBypassInstallGrace,
                        onSimulationChanged = viewModel::setDebugSubscriptionSimulation,
                        onBypassInstallGraceChanged = viewModel::setDebugBypassInstallGrace
                    )
                }
            }
        }

        // LAST CARD: About App, Version, Privacy Policy, Support & Deletion Guide (Always visible)
        item {
            SettingsAboutAndLegalCard(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                onOpenLegalDoc = onOpenLegalDoc,
                onOpenDesignPreview = if (BuildConfig.DEBUG) onOpenDesignPreview else null
            )
        }
    }

    if (showPrivacyAdChoicesDialog) {
        PrivacyAndAdChoicesDialog(
            isHindi = isHindi,
            isPrivacyOptionsRequired = adsUiState.isPrivacyOptionsRequired,
            onDismiss = { showPrivacyAdChoicesDialog = false },
            onFeedback = { privacyFeedback = it }
        )
    }

    if (confirmLeaveOrg) {
        ConfirmDangerDialog(
            title = "Leave '${uiState.activeOrg?.name ?: "Organisation"}'?",
            message = "You will lose access to this organisation's schools and records unless an admin invites or approves you again.",
            confirmLabel = "Leave Organisation",
            onConfirm = {
                confirmLeaveOrg = false
                onLeaveOrganisation()
            },
            onDismiss = { confirmLeaveOrg = false }
        )
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

    if (showAddGroupDialog) {
        AddClassGroupDialog(
            schoolName = activeSchool.name,
            onSave = { name, shortLabel, subs, onResult ->
                viewModel.addClassGroup(activeSchool.id, name, shortLabel, subs, onResult)
            },
            onDismiss = { showAddGroupDialog = false }
        )
    }

    if (showAddSubjectDialog && selectedGroup != null) {
        SingleInputDialog(
            title = "Add Subject",
            label = "Subject Name",
            initialValue = "",
            confirmLabel = "Add",
            onConfirm = {
                viewModel.addSubject(activeSchool.id, selectedGroup.id, it)
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
            message = "Remove '${sub.name}' from ${selectedGroup?.name ?: "this group"}?",
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

    // Modern Multi-School Backup Restore Dialog
    pendingImport?.let { backup ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingBackupImport,
            title = { Text("Restore Multi-School Backup", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val schoolNames = backup.schools.joinToString(", ") { it.name }.ifEmpty { "None" }
                    Text(
                        text = "Backup contains ${backup.schools.size} school(s) ($schoolNames), ${backup.students.size} student(s), ${backup.marks.size} mark entries, and ${backup.subjects.size} subjects.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Choose how to import:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Merge: keeps existing schools & students and merges matching school names and Admission Nos.\n• Replace All: clears current device data and restores the backup exactly.",
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

    // Legacy Pre-Phase-1 Backup Migration Dialog (asks user which school old students belong to)
    pendingLegacyImport?.let { legacy ->
        LegacyBackupMigrationDialog(
            payload = legacy,
            existingSchools = uiState.schools,
            onConfirm = { target -> viewModel.confirmLegacyBackupImport(target) },
            onDismiss = viewModel::cancelPendingLegacyBackupImport
        )
    }
}

@Composable
private fun LegacyBackupMigrationDialog(
    payload: LegacyBackupPayload,
    existingSchools: List<SchoolEntity>,
    onConfirm: (LegacyImportTarget) -> Unit,
    onDismiss: () -> Unit
) {
    val suggested = payload.suggestedSchools.firstOrNull()
    var createNewSchool by remember { mutableStateOf(true) }
    var newSchoolName by remember { mutableStateOf(suggested?.suggestedName ?: "Imported School") }
    var newSchoolAddress by remember { mutableStateOf(suggested?.address ?: "") }
    var newSchoolMobile by remember { mutableStateOf(suggested?.mobile ?: "") }
    var newSchoolEmail by remember { mutableStateOf(suggested?.email ?: "") }
    var selectedExistingSchoolId by remember {
        mutableLongStateOf(existingSchools.firstOrNull()?.id ?: 0L)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Import Older Backup Format",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "This backup was created before multi-school support (${payload.students.size} student(s), ${payload.marks.size} marks, ${payload.termInfos.size} attendance records). Choose which school these students belong to:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { createNewSchool = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = createNewSchool, onClick = { createNewSchool = true })
                    Text("Create a new school from backup", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }

                if (createNewSchool) {
                    if (payload.suggestedSchools.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            payload.suggestedSchools.forEach { preset ->
                                FilterChip(
                                    selected = newSchoolName == preset.suggestedName,
                                    onClick = {
                                        newSchoolName = preset.suggestedName
                                        newSchoolAddress = preset.address
                                        newSchoolMobile = preset.mobile
                                        newSchoolEmail = preset.email
                                    },
                                    label = { Text(preset.suggestedName) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newSchoolName,
                        onValueChange = { newSchoolName = it },
                        label = { Text("New School Name*") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("legacy_school_name_input")
                    )
                    OutlinedTextField(
                        value = newSchoolAddress,
                        onValueChange = { newSchoolAddress = it },
                        label = { Text("Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (existingSchools.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { createNewSchool = false },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !createNewSchool, onClick = { createNewSchool = false })
                        Text("Attach to an existing school", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }

                    if (!createNewSchool) {
                        val currentName = existingSchools.firstOrNull { it.id == selectedExistingSchoolId }?.name
                            ?: existingSchools.first().name
                        CompactDropdownSelector(
                            label = "Target School",
                            selectedValue = currentName,
                            options = existingSchools.map { it.name },
                            onSelect = { chosen ->
                                existingSchools.firstOrNull { it.name == chosen }?.let {
                                    selectedExistingSchoolId = it.id
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = if (createNewSchool) {
                        LegacyImportTarget.CreateNewSchool(
                            name = newSchoolName.trim(),
                            address = newSchoolAddress.trim(),
                            mobile = newSchoolMobile.trim(),
                            email = newSchoolEmail.trim(),
                            logoBase64 = payload.suggestedSchools.firstOrNull {
                                it.suggestedName == newSchoolName.trim()
                            }?.logoBase64 ?: suggested?.logoBase64.orEmpty()
                        )
                    } else {
                        LegacyImportTarget.ExistingSchool(selectedExistingSchoolId)
                    }
                    onConfirm(target)
                },
                enabled = !createNewSchool || newSchoolName.isNotBlank(),
                modifier = Modifier.testTag("confirm_legacy_backup_import_button")
            ) {
                Text("Import All Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAboutAndLegalCard(
    versionName: String,
    versionCode: Int,
    onOpenLegalDoc: (LegalDocType) -> Unit,
    onOpenDesignPreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_about_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "About ${stringResource(R.string.app_name)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Version $versionName (Build $versionCode)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("settings_about_version_text")
                    )
                }
            }

            Text(
                text = "Support contact: ${LegalConfig.CONTACT_EMAIL_PLACEHOLDER}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onOpenLegalDoc(LegalDocType.PRIVACY_POLICY) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("settings_about_privacy_button")
                ) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Privacy Policy")
                }
                OutlinedButton(
                    onClick = { onOpenLegalDoc(LegalDocType.SUPPORT) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("settings_about_support_button")
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Support & Help")
                }
                OutlinedButton(
                    onClick = { onOpenLegalDoc(LegalDocType.TERMS_OF_SERVICE) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("settings_about_terms_button")
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Terms")
                }
                OutlinedButton(
                    onClick = { onOpenLegalDoc(LegalDocType.DELETE_ACCOUNT) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("settings_about_delete_account_button")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Account Info")
                }
                if (BuildConfig.DEBUG && onOpenDesignPreview != null) {
                    TextButton(
                        onClick = onOpenDesignPreview,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("settings_about_design_preview_button")
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Design Preview (Debug)")
                    }
                }
            }
        }
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
