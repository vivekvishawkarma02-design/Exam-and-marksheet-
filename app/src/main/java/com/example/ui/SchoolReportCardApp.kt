package com.example.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.AppDatabase
import com.example.data.ReportCardRepository
import com.example.data.cloud.FirestoreProvider
import com.example.data.cloud.GoogleAuthHelper
import com.example.data.cloud.LocalMigrationSummary
import com.example.data.cloud.NetworkConnectivityMonitor
import com.example.data.cloud.OfflinePrefsStore
import com.example.data.cloud.OrgAcademicRepository
import com.example.data.cloud.OrgCloudRepository
import com.example.data.cloud.Phase4Repository
import com.example.data.cloud.Phase6Repository
import com.example.data.cloud.Phase7Repository
import com.example.data.cloud.Phase8Repository
import com.example.data.cloud.TimetableRepository
import com.example.data.cloud.authStateFlow
import com.example.domain.AppModule
import com.example.domain.ExamType
import com.example.util.FirebaseNotConfiguredScreen
import com.example.util.LastCrashReportScreen
import com.example.util.StartupSafetyManager
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

object AppRoutes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_STUDENTS = "students"
    const val ROUTE_MARKS = "marks"
    const val ROUTE_REPORTS = "reports"
    const val ROUTE_MEMBERS = "members"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_COSCHOLASTIC = "coscholastic"
    const val ROUTE_SCHOOLS = "schools"
    const val ROUTE_TEACHER_PROFILE = "teacher_profile"
    const val ROUTE_TEACHERS = "teachers_and_assignments"
    const val ROUTE_TIMETABLE = "timetable"
    const val ROUTE_PENDING_MARKS = "pending_marks"
    const val ROUTE_WORKLOAD_REPORT = "workload_report"
    const val ROUTE_TEACHER_ATTENDANCE = "teacher_attendance"
    const val ROUTE_STUDENT_ATTENDANCE = "student_attendance"
    const val ROUTE_STUDENT_REGISTER = "student_register"
    const val ROUTE_FEE_MANAGEMENT = "fee_management"
}

private enum class OnboardingSubScreen {
    PICKER,
    CREATE_ORG,
    JOIN_ORG
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val badgeCount: Int = 0
)

/**
 * Root multi-tenant gate:
 * 0) Startup safety: show previous uncaught crash report if present; if FirebaseApp.getApps(context).isEmpty(),
 *    show FirebaseNotConfiguredScreen without touching Firebase.auth or Firestore.
 * 1) Signed out -> ONLY AuthScreen (zero Firestore listeners, zero data screens reachable).
 * 2) Signed in -> OrgSessionHost (keyed by user.uid) -> Org Picker / Create / Join / Waiting.
 * 3) Selected Org -> OrganisationWorkspace (keyed by "${user.uid}_$orgId") -> Teacher Onboarding or App.
 */
@Composable
fun SchoolReportCardApp() {
    val context = LocalContext.current
    remember(context) {
        StartupSafetyManager.installGlobalExceptionHandler(context)
        true
    }

    var lastCrashTrace by remember(context) {
        mutableStateOf(StartupSafetyManager.readLastCrashStackTrace(context))
    }

    if (!lastCrashTrace.isNullOrBlank()) {
        LastCrashReportScreen(
            stackTrace = lastCrashTrace!!,
            onDismiss = {
                StartupSafetyManager.clearLastCrashStackTrace(context)
                lastCrashTrace = null
            }
        )
        return
    }

    val isFirebaseConfigured = remember(context) {
        !FirebaseApp.getApps(context).isEmpty()
    }
    if (!isFirebaseConfigured) {
        FirebaseNotConfiguredScreen()
        return
    }

    val auth = remember { Firebase.auth }
    val currentUser by remember(auth) { auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var viewingLegalDoc by remember { mutableStateOf<LegalDocType?>(null) }

    if (viewingLegalDoc != null) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { pad ->
            PrivacyAndTermsScreen(
                docType = viewingLegalDoc!!,
                onBack = { viewingLegalDoc = null },
                modifier = Modifier.padding(pad)
            )
        }
        return
    }

    val user = currentUser
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val rootConnectivity = remember(context.applicationContext) {
        NetworkConnectivityMonitor(context.applicationContext)
    }
    val rootIsOnline by rootConnectivity.isOnline.collectAsStateWithLifecycle(
        initialValue = rootConnectivity.isCurrentlyOnline()
    )
    var verificationRefreshTick by remember(user?.uid) { mutableStateOf(0) }

    if (user == null) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { pad ->
            AuthScreen(
                isOnline = rootIsOnline,
                onOpenLegalDoc = { viewingLegalDoc = it },
                modifier = Modifier.padding(pad)
            )
        }
    } else if (remember(user.uid, user.isEmailVerified, verificationRefreshTick) {
            GoogleAuthHelper.requiresEmailVerification(Firebase.auth.currentUser ?: user)
        }
    ) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { pad ->
            EmailVerificationGateScreen(
                email = user.email.orEmpty(),
                isOnline = rootIsOnline,
                onVerified = { verificationRefreshTick++ },
                onSignOutClick = {
                    GoogleAuthHelper.signOutAndClearLocalCache(
                        context = context,
                        credentialManager = credentialManager,
                        scope = scope
                    )
                },
                modifier = Modifier.padding(pad)
            )
        }
    } else {
        key(user.uid) {
            AuthenticatedSessionHost(
                user = user,
                onOpenLegalDoc = { viewingLegalDoc = it }
            )
        }
    }
}

@Composable
private fun AuthenticatedSessionHost(
    user: FirebaseUser,
    onOpenLegalDoc: (LegalDocType) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val connectivityMonitor = remember(appContext) { NetworkConnectivityMonitor(appContext) }
    val offlinePrefs = remember(appContext) { OfflinePrefsStore(appContext) }
    val orgRepo = remember(appContext, connectivityMonitor) {
        OrgCloudRepository(
            dbProvider = { FirestoreProvider.getDatabase(appContext) },
            isOnlineProvider = { connectivityMonitor.isCurrentlyOnline() }
        )
    }
    val academicRepo = remember(orgRepo) { OrgAcademicRepository(orgRepo) }
    val phase4Repo = remember(orgRepo) { Phase4Repository(orgRepo) }
    val timetableRepo = remember(orgRepo) { TimetableRepository(orgRepo) }
    val phase6Repo = remember(orgRepo) { Phase6Repository(orgRepo) }
    val phase7Repo = remember(orgRepo) { Phase7Repository(orgRepo) }
    val phase8Repo = remember(orgRepo) { Phase8Repository(orgRepo) }
    val localRepo = remember(appContext) { ReportCardRepository(AppDatabase.getInstance(appContext).reportCardDao()) }

    val sessionViewModel: OrgSessionViewModel = viewModel(
        key = "org_session_${user.uid}",
        factory = OrgSessionViewModel.provideFactory(
            currentUserId = user.uid,
            orgRepo = orgRepo,
            localRepo = localRepo,
            connectivityMonitor = connectivityMonitor,
            offlinePrefs = offlinePrefs
        )
    )
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var subScreen by remember { mutableStateOf(OnboardingSubScreen.PICKER) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteAccountError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        sessionViewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun performSignOut() {
        GoogleAuthHelper.signOutAndClearLocalCache(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onBeforeSignOut = {
                sessionViewModel.exitOrganisationToPicker()
            }
        )
    }

    val selectedOrgId = sessionState.selectedOrgId
    if (selectedOrgId == null) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            when (subScreen) {
                OnboardingSubScreen.PICKER -> {
                    OrgPickerAndOnboardingScreen(
                        state = sessionState,
                        userDisplayName = user.displayName ?: sessionState.userAccount?.name.orEmpty(),
                        userEmail = user.email.orEmpty(),
                        onSelectOrg = { orgId -> sessionViewModel.selectOrganisation(orgId) },
                        onCreateOrgClick = {
                            sessionViewModel.resetOrgCodeCheck()
                            sessionViewModel.clearError()
                            subScreen = OnboardingSubScreen.CREATE_ORG
                        },
                        onJoinOrgClick = {
                            sessionViewModel.resetJoinLookup()
                            sessionViewModel.clearError()
                            subScreen = OnboardingSubScreen.JOIN_ORG
                        },
                        onRefresh = sessionViewModel::refreshMemberships,
                        onCancelPendingRequest = sessionViewModel::cancelJoinRequest,
                        onOpenLegalDoc = onOpenLegalDoc,
                        onDeleteAccountClick = { showDeleteAccountDialog = true },
                        onSignOutClick = ::performSignOut,
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                OnboardingSubScreen.CREATE_ORG -> {
                    CreateOrganisationScreen(
                        state = sessionState,
                        defaultEmail = user.email.orEmpty(),
                        onCheckCode = sessionViewModel::checkOrgCodeAvailability,
                        onSubmit = { orgName, orgCode, contactMobile, contactEmail, schName, schAddr, schMob, schEmail, schLogo ->
                            sessionViewModel.createOrganisation(
                                orgName = orgName,
                                rawCode = orgCode,
                                contactMobile = contactMobile,
                                contactEmail = contactEmail,
                                firstSchoolName = schName,
                                firstSchoolAddress = schAddr,
                                firstSchoolMobile = schMob,
                                firstSchoolEmail = schEmail,
                                firstSchoolLogoBase64 = schLogo,
                                onCreated = { subScreen = OnboardingSubScreen.PICKER }
                            )
                        },
                        onBack = {
                            sessionViewModel.resetOrgCodeCheck()
                            subScreen = OnboardingSubScreen.PICKER
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }

                OnboardingSubScreen.JOIN_ORG -> {
                    JoinOrganisationScreen(
                        state = sessionState,
                        defaultDisplayName = user.displayName.orEmpty(),
                        onLookupCode = sessionViewModel::lookupOrgCodeForJoin,
                        onResetLookup = sessionViewModel::resetJoinLookup,
                        onSubmitJoinRequest = { lookup, name, msg, role ->
                            sessionViewModel.submitJoinRequest(lookup, name, msg, role) {
                                subScreen = OnboardingSubScreen.PICKER
                            }
                        },
                        onRedeemInviteCode = { orgCode, inviteCode ->
                            sessionViewModel.redeemInviteCode(orgCode, inviteCode) {
                                subScreen = OnboardingSubScreen.PICKER
                            }
                        },
                        onBack = {
                            sessionViewModel.resetJoinLookup()
                            subScreen = OnboardingSubScreen.PICKER
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    } else {
        key("${user.uid}_$selectedOrgId") {
            val schoolViewModel: SchoolViewModel = viewModel(
                key = "school_vm_${user.uid}_$selectedOrgId",
                factory = SchoolViewModel.provideFactory(
                    appContext = appContext,
                    orgId = selectedOrgId,
                    currentUserId = user.uid,
                    orgRepo = orgRepo,
                    academicRepo = academicRepo,
                    phase4Repo = phase4Repo,
                    timetableRepo = timetableRepo,
                    phase6Repo = phase6Repo,
                    phase7Repo = phase7Repo,
                    phase8Repo = phase8Repo,
                    localRepo = localRepo,
                    connectivityMonitor = connectivityMonitor,
                    offlinePrefs = offlinePrefs
                )
            )
            OrganisationWorkspace(
                viewModel = schoolViewModel,
                localMigrationSummary = sessionState.localMigrationSummary,
                shouldAutoPromptMigration = sessionState.promptLocalMigrationForOrgId == selectedOrgId,
                onDismissAutoMigrationPrompt = sessionViewModel::dismissLocalMigrationPrompt,
                onSwitchOrganisation = { notice -> sessionViewModel.exitOrganisationToPicker(notice) },
                onSignOut = ::performSignOut,
                onOpenLegalDoc = onOpenLegalDoc,
                onDeleteAccountClick = { showDeleteAccountDialog = true }
            )
        }
    }

    if (showDeleteAccountDialog) {
        val reauthMethod = remember(user.uid, showDeleteAccountDialog) {
            GoogleAuthHelper.determineReauthMethod(context, Firebase.auth.currentUser ?: user)
        }
        DeleteAccountDialog(
            memberships = sessionState.userAccount?.memberships.orEmpty(),
            reauthMethod = reauthMethod,
            userEmail = user.email.orEmpty(),
            isDeleting = isDeletingAccount,
            errorMessage = deleteAccountError,
            onConfirmDeleteWithPassword = { password ->
                deleteAccountError = null
                isDeletingAccount = true
                sessionViewModel.deleteOwnAccount(
                    reauthenticate = { GoogleAuthHelper.reauthenticateWithPassword(password) },
                    onResult = { ok, err ->
                        isDeletingAccount = false
                        if (ok) {
                            showDeleteAccountDialog = false
                            performSignOut()
                        } else {
                            deleteAccountError = err
                        }
                    }
                )
            },
            onConfirmDeleteWithGoogle = {
                deleteAccountError = null
                isDeletingAccount = true
                sessionViewModel.deleteOwnAccount(
                    reauthenticate = { GoogleAuthHelper.reauthenticateWithGoogle(context, credentialManager) },
                    onResult = { ok, err ->
                        isDeletingAccount = false
                        if (ok) {
                            showDeleteAccountDialog = false
                            performSignOut()
                        } else {
                            deleteAccountError = err
                        }
                    }
                )
            },
            onDismiss = {
                if (!isDeletingAccount) {
                    deleteAccountError = null
                    showDeleteAccountDialog = false
                }
            }
        )
    }
}

@Composable
private fun OrganisationWorkspace(
    viewModel: SchoolViewModel,
    localMigrationSummary: LocalMigrationSummary,
    shouldAutoPromptMigration: Boolean,
    onDismissAutoMigrationPrompt: () -> Unit,
    onSwitchOrganisation: (String?) -> Unit,
    onSignOut: () -> Unit,
    onOpenLegalDoc: (LegalDocType) -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val migrationProgress by viewModel.migrationProgress.collectAsStateWithLifecycle()
    val deleteOrgBackupExported by viewModel.deleteOrgBackupExported.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var focusBackupSection by remember { mutableStateOf(false) }
    var showMigrationDialog by remember(shouldAutoPromptMigration) { mutableStateOf(shouldAutoPromptMigration) }
    var showDeleteOrgDialog by remember { mutableStateOf(false) }
    var showSyncDetailDialog by remember { mutableStateOf(false) }
    var showConflictHistoryDialog by remember { mutableStateOf(false) }
    var pendingMarksJumpTarget by remember { mutableStateOf<Pair<ExamType, String>?>(null) }
    var timetableJumpTab by remember { mutableStateOf<Int?>(null) }
    var timetableJumpSubsDate by remember { mutableStateOf<String?>(null) }
    var attendanceJumpSchoolDocId by remember { mutableStateOf<String?>(null) }
    var attendanceJumpClassName by remember { mutableStateOf<String?>(null) }
    var attendanceJumpSection by remember { mutableStateOf<String?>(null) }
    var attendanceJumpDateIso by remember { mutableStateOf<String?>(null) }
    var attendanceJumpTab by remember { mutableStateOf<Int?>(null) }
    var feeJumpInitialTab by remember { mutableStateOf(0) }
    var feeJumpPreselectedStudent by remember { mutableStateOf<com.example.data.StudentEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Immediate revocation safety: if admin removed this user, exit org workspace cleanly
    LaunchedEffect(uiState.membershipRevoked) {
        if (uiState.membershipRevoked) {
            onSwitchOrganisation("Your access to this organisation was revoked or changed.")
        }
    }

    // Loading state while org + own member profile initialize
    if (!uiState.isInitialized && uiState.currentMember == null) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { pad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = "Opening organisation…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
        return
    }

    // Teacher first-login profile onboarding gate
    if (uiState.needsTeacherProfile && uiState.currentMember != null) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { pad ->
            TeacherProfileOnboardingScreen(
                orgName = uiState.activeOrg?.name.orEmpty(),
                member = uiState.currentMember!!,
                availableClasses = uiState.schoolClasses.map { it.name }.distinct(),
                availableSubjects = uiState.subjects.map { it.name }.distinct(),
                onSaveProfile = { name, mobile, photo, subjects, classes ->
                    viewModel.completeTeacherProfile(name, mobile, photo, subjects, classes)
                },
                onSwitchOrg = { onSwitchOrganisation(null) },
                modifier = Modifier.padding(pad)
            )
        }
        return
    }

    // Build navigation bar items strictly from modules the member is allowed to view
    val navItems = remember(
        uiState.currentMember,
        uiState.pendingJoinRequestCount
    ) {
        buildList {
            add(TopNavItem(AppRoutes.ROUTE_DASHBOARD, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"))
            if (uiState.canView(AppModule.STUDENTS)) {
                add(TopNavItem(AppRoutes.ROUTE_STUDENTS, "Students", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_students"))
            }
            if (uiState.canView(AppModule.MARKS)) {
                add(TopNavItem(AppRoutes.ROUTE_MARKS, "Marks", Icons.Filled.EditNote, Icons.Outlined.EditNote, "nav_marks"))
            }
            if (uiState.canView(AppModule.REPORTS)) {
                add(TopNavItem(AppRoutes.ROUTE_REPORTS, "Reports", Icons.Filled.Description, Icons.Outlined.Description, "nav_reports"))
            }
            if (uiState.canView(AppModule.MEMBERS) || uiState.isAdmin) {
                add(
                    TopNavItem(
                        AppRoutes.ROUTE_MEMBERS,
                        "Members",
                        Icons.Filled.AdminPanelSettings,
                        Icons.Outlined.AdminPanelSettings,
                        "nav_members",
                        badgeCount = uiState.pendingJoinRequestCount
                    )
                )
            }
            add(TopNavItem(AppRoutes.ROUTE_SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings"))
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.ROUTE_DASHBOARD
    val isSubScreen = currentRoute == AppRoutes.ROUTE_COSCHOLASTIC ||
        currentRoute == AppRoutes.ROUTE_SCHOOLS ||
        currentRoute == AppRoutes.ROUTE_TEACHER_PROFILE ||
        currentRoute == AppRoutes.ROUTE_TEACHERS ||
        currentRoute == AppRoutes.ROUTE_TIMETABLE ||
        currentRoute == AppRoutes.ROUTE_PENDING_MARKS ||
        currentRoute == AppRoutes.ROUTE_WORKLOAD_REPORT ||
        currentRoute == AppRoutes.ROUTE_TEACHER_ATTENDANCE ||
        currentRoute == AppRoutes.ROUTE_STUDENT_ATTENDANCE ||
        currentRoute == AppRoutes.ROUTE_STUDENT_REGISTER ||
        currentRoute == AppRoutes.ROUTE_FEE_MANAGEMENT

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openSchoolsScreen() {
        if (uiState.canEdit(AppModule.SETTINGS) && currentRoute != AppRoutes.ROUTE_SCHOOLS) {
            navController.navigate(AppRoutes.ROUTE_SCHOOLS) { launchSingleTop = true }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail && !isSubScreen) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    if (item.badgeCount > 0) {
                                        BadgedBox(badge = { Badge { Text("${item.badgeCount}") } }) {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isSubScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (currentRoute != AppRoutes.ROUTE_SCHOOLS && currentRoute != AppRoutes.ROUTE_TEACHER_PROFILE) {
                        SchoolSwitcherBar(
                            uiState = uiState,
                            onSelectSchool = viewModel::selectSchool,
                            onManageSchools = ::openSchoolsScreen,
                            onSwitchOrganisation = { onSwitchOrganisation(null) },
                            onSyncStatusClick = { showSyncDetailDialog = true },
                            onOpenConflictLog = { showConflictHistoryDialog = true },
                            onDismissConflicts = viewModel::dismissConflictBanner
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = AppRoutes.ROUTE_DASHBOARD
                        ) {
                            composable(AppRoutes.ROUTE_DASHBOARD) {
                                DashboardScreen(
                                    uiState = uiState,
                                    onSessionSelected = viewModel::selectSession,
                                    onClassSelected = viewModel::selectClass,
                                    onSectionSelected = viewModel::selectSection,
                                    onStudentSelected = viewModel::selectStudent,
                                    onDeleteLegacyMarks = viewModel::deleteLegacyMarksData,
                                    onNavigateToSchools = ::openSchoolsScreen,
                                    onNavigateToStudents = {
                                        if (uiState.canView(AppModule.STUDENTS)) navigateTopLevel(AppRoutes.ROUTE_STUDENTS)
                                    },
                                    onNavigateToMarks = {
                                        if (uiState.canView(AppModule.MARKS)) navigateTopLevel(AppRoutes.ROUTE_MARKS)
                                    },
                                    onNavigateToCoScholastic = {
                                        if (uiState.canView(AppModule.CO_SCHOLASTIC)) navController.navigate(AppRoutes.ROUTE_COSCHOLASTIC)
                                    },
                                    onNavigateToReports = {
                                        if (uiState.canView(AppModule.REPORTS)) navigateTopLevel(AppRoutes.ROUTE_REPORTS)
                                    },
                                    onNavigateToMembers = {
                                        if (uiState.canView(AppModule.MEMBERS) || uiState.isAdmin) navigateTopLevel(AppRoutes.ROUTE_MEMBERS)
                                    },
                                    onNavigateToTeachers = {
                                        if (uiState.canView(AppModule.TEACHERS)) {
                                            navController.navigate(AppRoutes.ROUTE_TEACHERS) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToTimetable = {
                                        if (uiState.canView(AppModule.TIMETABLE)) {
                                            timetableJumpTab = null
                                            timetableJumpSubsDate = null
                                            navController.navigate(AppRoutes.ROUTE_TIMETABLE) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToPendingMarks = {
                                        if (uiState.canView(AppModule.MARKS)) {
                                            navController.navigate(AppRoutes.ROUTE_PENDING_MARKS) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToWorkloadReport = {
                                        if (uiState.canView(AppModule.TIMETABLE) || uiState.canView(AppModule.TEACHERS)) {
                                            navController.navigate(AppRoutes.ROUTE_WORKLOAD_REPORT) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToTeacherAttendance = {
                                        if (uiState.canView(AppModule.TIMETABLE)) {
                                            navController.navigate(AppRoutes.ROUTE_TEACHER_ATTENDANCE) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToStudentAttendance = { schDocId, dateIso ->
                                        if (uiState.canAccessStudentAttendance) {
                                            attendanceJumpSchoolDocId = schDocId
                                            attendanceJumpClassName = null
                                            attendanceJumpSection = null
                                            attendanceJumpDateIso = dateIso
                                            attendanceJumpTab = 0
                                            navController.navigate(AppRoutes.ROUTE_STUDENT_ATTENDANCE) { launchSingleTop = true }
                                        }
                                    },
                                    onTakeStudentAttendanceSection = { schDocId, cls, sec, dateIso ->
                                        if (uiState.canAccessStudentAttendance) {
                                            attendanceJumpSchoolDocId = schDocId
                                            attendanceJumpClassName = cls
                                            attendanceJumpSection = sec
                                            attendanceJumpDateIso = dateIso
                                            attendanceJumpTab = 0
                                            navController.navigate(AppRoutes.ROUTE_STUDENT_ATTENDANCE) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToStudentRegister = {
                                        if (uiState.canViewStudentRegister) {
                                            navController.navigate(AppRoutes.ROUTE_STUDENT_REGISTER) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToFeeManagement = {
                                        if (uiState.canViewFees) {
                                            feeJumpInitialTab = 0
                                            feeJumpPreselectedStudent = null
                                            navController.navigate(AppRoutes.ROUTE_FEE_MANAGEMENT) { launchSingleTop = true }
                                        }
                                    },
                                    onOpenPendingMarksItem = { item ->
                                        if (uiState.canView(AppModule.MARKS)) {
                                            viewModel.focusSchoolClassSection(item.schoolDocId, item.className, item.section)
                                            pendingMarksJumpTarget = item.exam to item.subjectName
                                            navigateTopLevel(AppRoutes.ROUTE_MARKS)
                                        }
                                    },
                                    onOpenCoScholasticSection = { schId, cls, sec ->
                                        if (uiState.canView(AppModule.CO_SCHOLASTIC)) {
                                            viewModel.focusSchoolClassSection(schId, cls, sec)
                                            navController.navigate(AppRoutes.ROUTE_COSCHOLASTIC) { launchSingleTop = true }
                                        }
                                    },
                                    onEditOwnTeacherProfile = {
                                        navController.navigate(AppRoutes.ROUTE_TEACHER_PROFILE) { launchSingleTop = true }
                                    },
                                    onNavigateToSettings = {
                                        focusBackupSection = false
                                        navigateTopLevel(AppRoutes.ROUTE_SETTINGS)
                                    },
                                    onNavigateToBackup = {
                                        focusBackupSection = true
                                        navigateTopLevel(AppRoutes.ROUTE_SETTINGS)
                                    },
                                    onOpenStudentMarks = { student ->
                                        if (uiState.canView(AppModule.MARKS)) {
                                            viewModel.focusStudent(student)
                                            navigateTopLevel(AppRoutes.ROUTE_MARKS)
                                        }
                                    },
                                    onOpenStudentCoScholastic = { student ->
                                        if (uiState.canView(AppModule.CO_SCHOLASTIC)) {
                                            viewModel.focusStudent(student)
                                            navController.navigate(AppRoutes.ROUTE_COSCHOLASTIC)
                                        }
                                    },
                                    onOpenStudentReport = { student ->
                                        if (uiState.canView(AppModule.REPORTS)) {
                                            viewModel.focusStudent(student)
                                            navigateTopLevel(AppRoutes.ROUTE_REPORTS)
                                        }
                                    }
                                )
                            }

                            composable(AppRoutes.ROUTE_STUDENTS) {
                                StudentsScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onOpenCoScholastic = {
                                        if (uiState.canView(AppModule.CO_SCHOLASTIC)) {
                                            navController.navigate(AppRoutes.ROUTE_COSCHOLASTIC)
                                        }
                                    }
                                )
                            }

                            composable(AppRoutes.ROUTE_MARKS) {
                                MarksEntryScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onNavigateToStudents = {
                                        if (uiState.canView(AppModule.STUDENTS)) navigateTopLevel(AppRoutes.ROUTE_STUDENTS)
                                    },
                                    onNavigateToPendingMarks = {
                                        if (uiState.canView(AppModule.MARKS)) {
                                            navController.navigate(AppRoutes.ROUTE_PENDING_MARKS) { launchSingleTop = true }
                                        }
                                    },
                                    initialJumpExam = pendingMarksJumpTarget?.first,
                                    initialJumpSubject = pendingMarksJumpTarget?.second,
                                    onJumpConsumed = { pendingMarksJumpTarget = null }
                                )
                            }

                            composable(AppRoutes.ROUTE_REPORTS) {
                                ReportsScreen(
                                    uiState = uiState,
                                    viewModel = viewModel
                                )
                            }

                            composable(AppRoutes.ROUTE_MEMBERS) {
                                MembersScreen(
                                    uiState = uiState,
                                    viewModel = viewModel
                                )
                            }

                            composable(AppRoutes.ROUTE_SETTINGS) {
                                SettingsScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    focusBackupSection = focusBackupSection,
                                    onBackupFocusConsumed = { focusBackupSection = false },
                                    onManageSchools = ::openSchoolsScreen,
                                    localMigrationSummary = localMigrationSummary,
                                    onOpenLocalMigration = {
                                        viewModel.resetMigrationProgress()
                                        showMigrationDialog = true
                                    },
                                    onSwitchOrganisation = { onSwitchOrganisation(null) },
                                    onLeaveOrganisation = {
                                        viewModel.leaveOrganisation { onSwitchOrganisation("Left organisation.") }
                                    },
                                    onDeleteOrganisationClick = {
                                        viewModel.resetDeleteOrgBackupFlag()
                                        showDeleteOrgDialog = true
                                    },
                                    onOpenLegalDoc = onOpenLegalDoc,
                                    onSignOut = onSignOut,
                                    onDeleteAccountClick = onDeleteAccountClick
                                )
                            }

                            composable(AppRoutes.ROUTE_COSCHOLASTIC) {
                                CoScholasticScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(AppRoutes.ROUTE_SCHOOLS) {
                                SchoolsScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(AppRoutes.ROUTE_TEACHER_PROFILE) {
                                uiState.currentMember?.let { member ->
                                    TeacherProfileOnboardingScreen(
                                        orgName = uiState.activeOrg?.name.orEmpty(),
                                        member = member,
                                        availableClasses = uiState.schoolClasses.map { it.name }.distinct(),
                                        availableSubjects = uiState.subjects.map { it.name }.distinct(),
                                        onSaveProfile = { name, mobile, photo, subjects, classes ->
                                            viewModel.completeTeacherProfile(name, mobile, photo, subjects, classes) {
                                                navController.popBackStack()
                                            }
                                        },
                                        onSwitchOrg = { navController.popBackStack() }
                                    )
                                }
                            }

                            composable(AppRoutes.ROUTE_TEACHERS) {
                                if (uiState.canView(AppModule.TEACHERS)) {
                                    TeachersAndAssignmentsScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_TIMETABLE) {
                                if (uiState.canView(AppModule.TIMETABLE)) {
                                    TimetableScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() },
                                        initialTabIndex = timetableJumpTab,
                                        initialSubsDate = timetableJumpSubsDate,
                                        onInitialJumpConsumed = {
                                            timetableJumpTab = null
                                            timetableJumpSubsDate = null
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_PENDING_MARKS) {
                                if (uiState.canView(AppModule.MARKS)) {
                                    PendingMarksScreen(
                                        uiState = uiState,
                                        onBack = { navController.popBackStack() },
                                        onOpenMarksForItem = { item ->
                                            viewModel.focusSchoolClassSection(item.schoolDocId, item.className, item.section)
                                            pendingMarksJumpTarget = item.exam to item.subjectName
                                            navigateTopLevel(AppRoutes.ROUTE_MARKS)
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_WORKLOAD_REPORT) {
                                if (uiState.canView(AppModule.TIMETABLE) || uiState.canView(AppModule.TEACHERS)) {
                                    WorkloadReportScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_TEACHER_ATTENDANCE) {
                                if (uiState.canView(AppModule.TIMETABLE)) {
                                    TeacherAttendanceScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() },
                                        onJumpToSubstitutionsForDate = { dateIso ->
                                            timetableJumpTab = 4 // TimetableTab.LEAVE_SUBS
                                            timetableJumpSubsDate = dateIso
                                            navController.navigate(AppRoutes.ROUTE_TIMETABLE) { launchSingleTop = true }
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_STUDENT_ATTENDANCE) {
                                if (uiState.canAccessStudentAttendance) {
                                    Phase8StudentAttendanceScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() },
                                        initialSchoolDocId = attendanceJumpSchoolDocId,
                                        initialClassName = attendanceJumpClassName,
                                        initialSection = attendanceJumpSection,
                                        initialDateIso = attendanceJumpDateIso,
                                        initialTabIndex = attendanceJumpTab,
                                        onInitialJumpConsumed = {
                                            attendanceJumpSchoolDocId = null
                                            attendanceJumpClassName = null
                                            attendanceJumpSection = null
                                            attendanceJumpDateIso = null
                                            attendanceJumpTab = null
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_STUDENT_REGISTER) {
                                if (uiState.canViewStudentRegister) {
                                    Phase9StudentRegisterScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() },
                                        onOpenFeeCollectionForStudent = { student ->
                                            if (uiState.canViewFees) {
                                                feeJumpInitialTab = 0
                                                feeJumpPreselectedStudent = student
                                                navController.navigate(AppRoutes.ROUTE_FEE_MANAGEMENT) { launchSingleTop = true }
                                            }
                                        }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_FEE_MANAGEMENT) {
                                if (uiState.canViewFees) {
                                    Phase9FeeManagementScreen(
                                        viewModel = viewModel,
                                        initialTab = feeJumpInitialTab,
                                        preselectedStudent = feeJumpPreselectedStudent,
                                        onOpenStudentRegister = {
                                            if (uiState.canViewStudentRegister) {
                                                navController.navigate(AppRoutes.ROUTE_STUDENT_REGISTER) { launchSingleTop = true }
                                            }
                                        },
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMigrationDialog && localMigrationSummary.hasDataToUpload) {
        LocalDataMigrationDialog(
            orgName = uiState.activeOrg?.name.orEmpty(),
            summary = localMigrationSummary,
            progress = migrationProgress,
            onStartUpload = viewModel::uploadLocalRoomDataToCloud,
            onDismiss = {
                showMigrationDialog = false
                onDismissAutoMigrationPrompt()
            }
        )
    }

    if (showDeleteOrgDialog && uiState.activeOrg != null) {
        val org = uiState.activeOrg!!
        DeleteOrganisationDialog(
            orgName = org.name,
            orgCode = org.code,
            backupExported = deleteOrgBackupExported,
            suggestedBackupFileName = viewModel.suggestedBackupFileName(allSchools = true),
            onExportBackupToUri = { uri -> viewModel.exportBackupToUri(uri, allSchools = true) },
            onConfirmDelete = { typedCode ->
                viewModel.deleteOrganisation(typedCode) {
                    showDeleteOrgDialog = false
                    onSwitchOrganisation("Deleted organisation '${org.name}'.")
                }
            },
            onDismiss = { showDeleteOrgDialog = false }
        )
    }

    if (showSyncDetailDialog) {
        SyncStatusDetailDialog(
            syncUiState = uiState.syncUiState,
            onRetrySync = viewModel::retrySync,
            onClearError = viewModel::clearSyncError,
            onDismiss = { showSyncDetailDialog = false }
        )
    }

    if (showConflictHistoryDialog) {
        StudentMarksHistoryDialog(
            title = "${uiState.activeSchool.name} — Marks Edit Log",
            logs = uiState.markEditLogs,
            conflicts = uiState.markConflicts,
            onDismiss = { showConflictHistoryDialog = false }
        )
    }
}
