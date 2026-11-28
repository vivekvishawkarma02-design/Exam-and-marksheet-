package com.example.ui

import android.app.Application
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.BuildConfig
import com.example.ads.Phase16AdMobController
import com.example.ads.findActivityOrNull
import com.example.ui.designsystem.GlassBottomBar
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    const val ROUTE_SECTION_STUDENTS = "section_students"
    const val ROUTE_SECTION_ACADEMICS = "section_academics"
    const val ROUTE_SECTION_FINANCE = "section_finance"
    const val ROUTE_SECTION_MANAGE = "section_manage"
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
    const val ROUTE_CERTIFICATES = "certificates"
    const val ROUTE_CASH_BOOK = "cash_book"
    const val ROUTE_ENQUIRY = "enquiry"
    const val ROUTE_EXAMS = "exams"
    const val ROUTE_MY_EXAM_DUTIES = "my_exam_duties"
    const val ROUTE_STOCK = "stock"
    const val ROUTE_TRANSPORT = "transport"
    const val ROUTE_SALARY = "salary"
    const val ROUTE_SESSION_PROMOTION = "session_promotion"
    const val ROUTE_BULK_IMPORT = "bulk_import"
    const val ROUTE_BACKUP_EXPORT = "backup_export"
    const val ROUTE_RESULT_ANALYSIS = "result_analysis"
    const val ROUTE_CLASS_DIARY = "phase18_class_diary"
    const val ROUTE_BIRTHDAYS = "phase18_birthdays"
    const val ROUTE_STUDENT_REMARKS = "phase19_student_remarks"
    const val ROUTE_GATE_PASS = "phase19_gate_pass"
    const val ROUTE_SCHOOL_SUBSCRIPTION = "phase16_school_subscription"
    const val ROUTE_DESIGN_PREVIEW = "debug_design_preview"
}

private enum class OnboardingSubScreen {
    PICKER,
    CREATE_ORG,
    JOIN_ORG
}

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
    val sessionAdsUiState by sessionViewModel.adsUiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val hostActivity = remember(context) { context.findActivityOrNull() }

    LaunchedEffect(sessionState.isOnline, sessionAdsUiState.effectiveSubscription.suppressesAllAds) {
        if (sessionState.isOnline && !sessionAdsUiState.effectiveSubscription.suppressesAllAds) {
            Phase16AdMobController.gatherConsentAndInitializeIfAllowed(
                activity = hostActivity,
                isOnline = true,
                unitIds = sessionAdsUiState.unitIds
            )
        }
    }

    var subScreen by remember { mutableStateOf(OnboardingSubScreen.PICKER) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteAccountError by remember { mutableStateOf<String?>(null) }
    var jumpToMembersTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        sessionViewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun performSignOut() {
        Phase16AdMobController.clearPreloadedAds()
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
                        adsUiState = sessionAdsUiState,
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
                jumpToMembersTrigger = jumpToMembersTrigger,
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
            onConfirmDeleteWithPassword = { password, deleteOwnedOrgs ->
                deleteAccountError = null
                isDeletingAccount = true
                sessionViewModel.deleteOwnAccount(
                    reauthenticate = { GoogleAuthHelper.reauthenticateWithPassword(password) },
                    deleteOwnedOrgsConfirmed = deleteOwnedOrgs,
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
            onConfirmDeleteWithGoogle = { deleteOwnedOrgs ->
                deleteAccountError = null
                isDeletingAccount = true
                sessionViewModel.deleteOwnAccount(
                    reauthenticate = { GoogleAuthHelper.reauthenticateWithGoogle(context, credentialManager) },
                    deleteOwnedOrgsConfirmed = deleteOwnedOrgs,
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
            onTransferOwnershipClick = if (selectedOrgId != null) {
                { jumpToMembersTrigger++ }
            } else null,
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
    jumpToMembersTrigger: Int = 0,
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
    val appScope = rememberCoroutineScope()
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
    var feeJumpQueueMessageType by remember { mutableStateOf<com.example.domain.FeeMessageType?>(null) }
    var feeJumpQueueMonthFilter by remember { mutableStateOf<String?>(null) }
    var feeJumpQueueRouteId by remember { mutableStateOf<String?>(null) }
    var transportJumpTab by remember { mutableStateOf<Int?>(null) }
    var certificateJumpStudentId by remember { mutableStateOf<Long?>(null) }
    val workspaceAdsUiState by viewModel.adsUiState.collectAsStateWithLifecycle()
    val workspaceBillingState by viewModel.billingUiState.collectAsStateWithLifecycle()
    val dismissedLapsedNoticeMarker by viewModel.dismissedLapsedNoticeMarkerMs.collectAsStateWithLifecycle()
    val workspaceContext = LocalContext.current
    val workspaceActivity = remember(workspaceContext) { workspaceContext.findActivityOrNull() }

    LaunchedEffect(
        uiState.isOnline,
        workspaceAdsUiState.effectiveSubscription.suppressesAllAds,
        workspaceAdsUiState.canRequestAdsFromConsent
    ) {
        if (uiState.isOnline && !workspaceAdsUiState.effectiveSubscription.suppressesAllAds) {
            Phase16AdMobController.gatherConsentAndInitializeIfAllowed(
                activity = workspaceActivity,
                isOnline = true,
                unitIds = workspaceAdsUiState.unitIds
            )
            if (workspaceAdsUiState.canRequestAdsFromConsent) {
                viewModel.preloadPdfCloseInterstitialIfEligible()
            }
        }
    }

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

    // Build 5-section navigation items from the single route registry
    val canOpenStudentRegister = uiState.canViewStudentRegister || uiState.canView(AppModule.STUDENTS)
    val navItems = remember(
        uiState.currentMember,
        uiState.pendingJoinRequestCount,
        uiState.phase10HindiLabels,
        uiState.ownExamDuties,
        uiState.teachers,
        uiState.activeSchool.id
    ) {
        AppNavigationRegistry.buildBottomNavItems(uiState)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.ROUTE_DASHBOARD
    val isSubScreen = AppNavigationRegistry.isSubScreenRoute(currentRoute)
    val activeSection = AppNavigationRegistry.parentSectionFor(currentRoute)

    fun navigateTopLevel(route: String) {
        if (!AppNavigationRegistry.canAccessRoute(route, uiState)) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateSectionChild(route: String) {
        if (!AppNavigationRegistry.canAccessRoute(route, uiState)) return
        if (currentRoute != route) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    fun resetJumpArgsBeforeOpening(route: String) {
        when (route) {
            AppRoutes.ROUTE_TIMETABLE -> {
                timetableJumpTab = null
                timetableJumpSubsDate = null
            }
            AppRoutes.ROUTE_STUDENT_ATTENDANCE -> {
                attendanceJumpSchoolDocId = null
                attendanceJumpClassName = null
                attendanceJumpSection = null
                attendanceJumpDateIso = null
                attendanceJumpTab = null
            }
            AppRoutes.ROUTE_FEE_MANAGEMENT -> {
                feeJumpInitialTab = 0
                feeJumpPreselectedStudent = null
                feeJumpQueueMessageType = null
                feeJumpQueueMonthFilter = null
                feeJumpQueueRouteId = null
            }
            AppRoutes.ROUTE_CERTIFICATES -> certificateJumpStudentId = null
            AppRoutes.ROUTE_TRANSPORT -> transportJumpTab = null
            AppRoutes.ROUTE_SETTINGS -> focusBackupSection = false
        }
    }

    fun openSchoolsScreen() {
        navigateSectionChild(AppRoutes.ROUTE_SCHOOLS)
    }

    LaunchedEffect(jumpToMembersTrigger) {
        if (jumpToMembersTrigger > 0 && AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_MEMBERS, uiState)) {
            navigateSectionChild(AppRoutes.ROUTE_MEMBERS)
        }
    }

    BackHandler(enabled = !isSubScreen && activeSection != NavSection.HOME) {
        navigateTopLevel(AppRoutes.ROUTE_DASHBOARD)
    }

    CompositionLocalProvider(
        LocalOnFinishedPdfPreviewClosed provides { finishedOk ->
            viewModel.onFinishedPdfPreviewClosed(workspaceActivity, finishedOk)
        }
    ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail && !isSubScreen) {
                    GlassBottomBar(
                        items = navItems,
                        selectedId = activeSection.route,
                        onSelect = { item -> navigateTopLevel(item.id) },
                        applyNavigationBarInsets = true
                    )
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
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        navItems.forEach { item ->
                            val selected = activeSection.route == item.id
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.id) },
                                icon = {
                                    if (item.badgeCount > 0) {
                                        BadgedBox(badge = { Badge { Text("${item.badgeCount}") } }) {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.icon,
                                                contentDescription = item.contentDescription
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.icon,
                                            contentDescription = item.contentDescription
                                        )
                                    }
                                },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = if (item.testTag != null) Modifier.testTag(item.testTag) else Modifier
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (!isSubScreen) {
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
                                        navigateSectionChild(AppRoutes.ROUTE_STUDENT_REGISTER)
                                    },
                                    onNavigateToMarks = {
                                        navigateSectionChild(AppRoutes.ROUTE_MARKS)
                                    },
                                    onNavigateToCoScholastic = {
                                        navigateSectionChild(AppRoutes.ROUTE_COSCHOLASTIC)
                                    },
                                    onNavigateToReports = {
                                        navigateSectionChild(AppRoutes.ROUTE_REPORTS)
                                    },
                                    onNavigateToMembers = {
                                        navigateSectionChild(AppRoutes.ROUTE_MEMBERS)
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
                                        navigateSectionChild(AppRoutes.ROUTE_STUDENT_REGISTER)
                                    },
                                    onNavigateToFeeManagement = {
                                        if (uiState.canViewFees) {
                                            feeJumpInitialTab = 0
                                            feeJumpPreselectedStudent = null
                                            navController.navigate(AppRoutes.ROUTE_FEE_MANAGEMENT) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToCertificates = {
                                        if (uiState.canViewCertificates) {
                                            certificateJumpStudentId = null
                                            navController.navigate(AppRoutes.ROUTE_CERTIFICATES) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToCashBook = {
                                        if (uiState.canViewCashBook) {
                                            navController.navigate(AppRoutes.ROUTE_CASH_BOOK) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToEnquiry = {
                                        if (uiState.canViewEnquiry) {
                                            navController.navigate(AppRoutes.ROUTE_ENQUIRY) { launchSingleTop = true }
                                        }
                                    },
                                    onNavigateToExams = {
                                        navigateSectionChild(AppRoutes.ROUTE_EXAMS)
                                    },
                                    onOpenExamDuties = {
                                        navigateSectionChild(AppRoutes.ROUTE_MY_EXAM_DUTIES)
                                    },
                                    onNavigateToStock = {
                                        navigateSectionChild(AppRoutes.ROUTE_STOCK)
                                    },
                                    onNavigateToTransport = {
                                        if (uiState.canViewTransport) {
                                            transportJumpTab = 0
                                            navigateSectionChild(AppRoutes.ROUTE_TRANSPORT)
                                        }
                                    },
                                    onNavigateToTransportVehiclesDocs = {
                                        if (uiState.canViewTransport) {
                                            transportJumpTab = 3
                                            navigateSectionChild(AppRoutes.ROUTE_TRANSPORT)
                                        }
                                    },
                                    onNavigateToSalary = {
                                        navigateSectionChild(AppRoutes.ROUTE_SALARY)
                                    },
                                    onNavigateToSessionPromotion = {
                                        navigateSectionChild(AppRoutes.ROUTE_SESSION_PROMOTION)
                                    },
                                    onNavigateToBulkImport = {
                                        navigateSectionChild(AppRoutes.ROUTE_BULK_IMPORT)
                                    },
                                    onNavigateToBackupExport = {
                                        navigateSectionChild(AppRoutes.ROUTE_BACKUP_EXPORT)
                                    },
                                    onNavigateToResultAnalysis = {
                                        navigateSectionChild(AppRoutes.ROUTE_RESULT_ANALYSIS)
                                    },
                                    onNavigateToClassDiary = {
                                        navigateSectionChild(AppRoutes.ROUTE_CLASS_DIARY)
                                    },
                                    onNavigateToBirthdays = {
                                        navigateSectionChild(AppRoutes.ROUTE_BIRTHDAYS)
                                    },
                                    onNavigateToStudentRemarks = {
                                        navigateSectionChild(AppRoutes.ROUTE_STUDENT_REMARKS)
                                    },
                                    onNavigateToGatePass = {
                                        navigateSectionChild(AppRoutes.ROUTE_GATE_PASS)
                                    },
                                    onPrintOwnSalarySlip = { slip, pageSize ->
                                        viewModel.generateSalarySlipPdfAndPreview(slip, pageSize)
                                    },
                                    onOpenPendingMarksItem = { item ->
                                        if (uiState.canView(AppModule.MARKS)) {
                                            viewModel.focusSchoolClassSection(item.schoolDocId, item.className, item.section)
                                            pendingMarksJumpTarget = item.exam to item.subjectName
                                            navigateSectionChild(AppRoutes.ROUTE_MARKS)
                                        }
                                    },
                                    onOpenCoScholasticSection = { schId, cls, sec ->
                                        if (uiState.canView(AppModule.CO_SCHOLASTIC)) {
                                            viewModel.focusSchoolClassSection(schId, cls, sec)
                                            navigateSectionChild(AppRoutes.ROUTE_COSCHOLASTIC)
                                        }
                                    },
                                    onEditOwnTeacherProfile = {
                                        navigateSectionChild(AppRoutes.ROUTE_TEACHER_PROFILE)
                                    },
                                    onNavigateToSettings = {
                                        focusBackupSection = false
                                        navigateSectionChild(AppRoutes.ROUTE_SETTINGS)
                                    },
                                    onNavigateToBackup = {
                                        if (uiState.canUseBackupExport) {
                                            navigateSectionChild(AppRoutes.ROUTE_BACKUP_EXPORT)
                                        } else {
                                            focusBackupSection = true
                                            navigateSectionChild(AppRoutes.ROUTE_SETTINGS)
                                        }
                                    },
                                    onOpenStudentMarks = { student ->
                                        if (uiState.canView(AppModule.MARKS)) {
                                            viewModel.focusStudent(student)
                                            navigateSectionChild(AppRoutes.ROUTE_MARKS)
                                        }
                                    },
                                    onOpenStudentCoScholastic = { student ->
                                        if (uiState.canView(AppModule.CO_SCHOLASTIC)) {
                                            viewModel.focusStudent(student)
                                            navigateSectionChild(AppRoutes.ROUTE_COSCHOLASTIC)
                                        }
                                    },
                                    onOpenStudentReport = { student ->
                                        if (uiState.canView(AppModule.REPORTS)) {
                                            viewModel.focusStudent(student)
                                            navigateSectionChild(AppRoutes.ROUTE_REPORTS)
                                        }
                                    },
                                    onOpenFeeManagementTab = { tabIdx, preselected ->
                                        if (uiState.canViewFees) {
                                            feeJumpInitialTab = tabIdx
                                            feeJumpPreselectedStudent = preselected
                                            navController.navigate(AppRoutes.ROUTE_FEE_MANAGEMENT) { launchSingleTop = true }
                                        }
                                    },
                                    onOpenTimetableSubstitutions = { dateIso ->
                                        if (uiState.canView(AppModule.TIMETABLE)) {
                                            timetableJumpTab = 4 // TimetableTab.LEAVE_SUBS
                                            timetableJumpSubsDate = dateIso
                                            navController.navigate(AppRoutes.ROUTE_TIMETABLE) { launchSingleTop = true }
                                        }
                                    },
                                    onToggleHindiLabels = viewModel::togglePhase10HindiLabels,
                                    onSyncStatusClick = { showSyncDetailDialog = true },
                                    onSkipFirstTimeGuide = {
                                        viewModel.setFirstTimeGuideSkipped(true)
                                        val isHi = uiState.phase10HindiLabels
                                        appScope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            val result = snackbarHostState.showSnackbar(
                                                message = p10Label(
                                                    isHi,
                                                    "सेटअप गाइड छुपा दी गई। ऊपर से कभी भी फिर खोलें।",
                                                    "Setup guide hidden. Reopen anytime from the header."
                                                ),
                                                actionLabel = p10Label(isHi, "वापस लाएँ", "Undo"),
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.setFirstTimeGuideSkipped(false)
                                            }
                                        }
                                    },
                                    onReopenFirstTimeGuide = {
                                        viewModel.setFirstTimeGuideSkipped(false)
                                    },
                                    adsUiState = workspaceAdsUiState,
                                    onWatchRewardedAd = {
                                        viewModel.watchRewardedAdForAdFreeTime(workspaceActivity)
                                    },
                                    showOwnerLapsedSubscriptionNotice = workspaceAdsUiState.shouldShowLapsedNoticeToOwner(
                                        isOwner = uiState.isOwner,
                                        dismissedMarkerMs = dismissedLapsedNoticeMarker
                                    ),
                                    onOpenSubscriptionScreen = {
                                        navController.navigate(AppRoutes.ROUTE_SCHOOL_SUBSCRIPTION) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onDismissLapsedSubscriptionNotice = viewModel::dismissLapsedSubscriptionNotice
                                )
                            }

                            composable(AppRoutes.ROUTE_SECTION_STUDENTS) {
                                if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_SECTION_STUDENTS, uiState)) {
                                    SectionHomePage(
                                        section = NavSection.STUDENTS,
                                        uiState = uiState,
                                        onNavigateToRoute = { route ->
                                            resetJumpArgsBeforeOpening(route)
                                            navigateSectionChild(route)
                                        },
                                        onOpenLegalDoc = onOpenLegalDoc
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navigateTopLevel(AppRoutes.ROUTE_DASHBOARD)
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_SECTION_ACADEMICS) {
                                if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_SECTION_ACADEMICS, uiState)) {
                                    SectionHomePage(
                                        section = NavSection.ACADEMICS,
                                        uiState = uiState,
                                        onNavigateToRoute = { route ->
                                            resetJumpArgsBeforeOpening(route)
                                            navigateSectionChild(route)
                                        },
                                        onOpenLegalDoc = onOpenLegalDoc
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navigateTopLevel(AppRoutes.ROUTE_DASHBOARD)
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_SECTION_FINANCE) {
                                if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_SECTION_FINANCE, uiState)) {
                                    SectionHomePage(
                                        section = NavSection.FINANCE_OPS,
                                        uiState = uiState,
                                        onNavigateToRoute = { route ->
                                            resetJumpArgsBeforeOpening(route)
                                            navigateSectionChild(route)
                                        },
                                        onOpenLegalDoc = onOpenLegalDoc
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navigateTopLevel(AppRoutes.ROUTE_DASHBOARD)
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_SECTION_MANAGE) {
                                SectionHomePage(
                                    section = NavSection.MANAGE,
                                    uiState = uiState,
                                    onNavigateToRoute = { route ->
                                        resetJumpArgsBeforeOpening(route)
                                        navigateSectionChild(route)
                                    },
                                    onOpenLegalDoc = onOpenLegalDoc
                                )
                            }

                            composable(AppRoutes.ROUTE_MY_EXAM_DUTIES) {
                                if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_MY_EXAM_DUTIES, uiState)) {
                                    MyExamDutiesScreen(
                                        uiState = uiState,
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_MARKS) {
                                if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_MARKS, uiState)) {
                                    MarksEntryScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onNavigateToStudents = {
                                            navigateSectionChild(AppRoutes.ROUTE_STUDENT_REGISTER)
                                        },
                                        onNavigateToPendingMarks = {
                                            navigateSectionChild(AppRoutes.ROUTE_PENDING_MARKS)
                                        },
                                        initialJumpExam = pendingMarksJumpTarget?.first,
                                        initialJumpSubject = pendingMarksJumpTarget?.second,
                                        onJumpConsumed = { pendingMarksJumpTarget = null },
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_REPORTS) {
                                if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_REPORTS, uiState)) {
                                    ReportsScreen(
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

                            composable(AppRoutes.ROUTE_MEMBERS) {
                                if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_MEMBERS, uiState)) {
                                    MembersScreen(
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
                                    onOpenSubscriptionScreen = {
                                        navigateSectionChild(AppRoutes.ROUTE_SCHOOL_SUBSCRIPTION)
                                    },
                                    onSignOut = onSignOut,
                                    onDeleteAccountClick = onDeleteAccountClick,
                                    onOpenDesignPreview = if (BuildConfig.DEBUG) {
                                        { navigateSectionChild(AppRoutes.ROUTE_DESIGN_PREVIEW) }
                                    } else {
                                        null
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(AppRoutes.ROUTE_SCHOOL_SUBSCRIPTION) {
                                Phase16SchoolSubscriptionScreen(
                                    orgName = uiState.activeOrg?.name.orEmpty(),
                                    isOwner = uiState.isOwner,
                                    isHindi = uiState.phase10HindiLabels,
                                    isOnline = uiState.isOnline,
                                    subscription = workspaceAdsUiState.effectiveSubscription,
                                    billingState = workspaceBillingState,
                                    onRefreshOffers = viewModel::refreshSubscriptionOffers,
                                    onSubscribePlan = { offer ->
                                        viewModel.startOwnerSubscriptionPurchase(workspaceActivity, offer)
                                    },
                                    onRestorePurchase = viewModel::restoreOwnerPurchases,
                                    onOpenPrivacyAndTerms = { onOpenLegalDoc(LegalDocType.PRIVACY_POLICY) },
                                    onToggleLanguage = viewModel::togglePhase10HindiLabels,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(AppRoutes.ROUTE_COSCHOLASTIC) {
                                if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_COSCHOLASTIC, uiState)) {
                                    CoScholasticScreen(
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

                            composable(AppRoutes.ROUTE_SCHOOLS) {
                                if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_SCHOOLS, uiState)) {
                                    SchoolsScreen(
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

                            composable(AppRoutes.ROUTE_TEACHER_PROFILE) {
                                val member = uiState.currentMember
                                if (member != null && AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_TEACHER_PROFILE, uiState)) {
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
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_TEACHERS) {
                                if (uiState.canView(AppModule.TEACHERS)) {
                                    TeachersAndAssignmentsScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        onBack = {
                                            navController.popBackStack()
                                            viewModel.onTaskFinishedReturnFromList(workspaceActivity)
                                        }
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
                                        onBack = {
                                            navController.popBackStack()
                                            viewModel.onTaskFinishedReturnFromList(workspaceActivity)
                                        },
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
                                            navigateSectionChild(AppRoutes.ROUTE_MARKS)
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
                                if (canOpenStudentRegister) {
                                    Phase9StudentRegisterScreen(
                                        viewModel = viewModel,
                                        onBack = {
                                            if (!navController.popBackStack()) {
                                                navigateTopLevel(AppRoutes.ROUTE_DASHBOARD)
                                            }
                                        },
                                        onOpenFeeCollectionForStudent = { student ->
                                            if (uiState.canViewFees) {
                                                feeJumpInitialTab = 0
                                                feeJumpPreselectedStudent = student
                                                navController.navigate(AppRoutes.ROUTE_FEE_MANAGEMENT) { launchSingleTop = true }
                                            }
                                        },
                                        onOpenCertificatesForStudent = { student ->
                                            if (uiState.canViewCertificates) {
                                                certificateJumpStudentId = student.id
                                                navController.navigate(AppRoutes.ROUTE_CERTIFICATES) { launchSingleTop = true }
                                            }
                                        },
                                        onOpenCoScholasticForStudent = { student ->
                                            if (uiState.canView(AppModule.CO_SCHOLASTIC)) {
                                                viewModel.focusStudent(student)
                                                navController.navigate(AppRoutes.ROUTE_COSCHOLASTIC) { launchSingleTop = true }
                                            }
                                        },
                                        onNavigateToTransport = {
                                            if (uiState.canViewTransport) {
                                                transportJumpTab = 0
                                                navController.navigate(AppRoutes.ROUTE_TRANSPORT) { launchSingleTop = true }
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
                                        initialQueueMessageType = feeJumpQueueMessageType,
                                        initialQueueMonthFilter = feeJumpQueueMonthFilter,
                                        initialQueueRouteIdFilter = feeJumpQueueRouteId,
                                        onQueueJumpConsumed = {
                                            feeJumpQueueMessageType = null
                                            feeJumpQueueMonthFilter = null
                                            feeJumpQueueRouteId = null
                                        },
                                        onOpenStudentRegister = {
                                            navigateSectionChild(AppRoutes.ROUTE_STUDENT_REGISTER)
                                        },
                                        onOpenTransport = {
                                            if (uiState.canViewTransport) {
                                                transportJumpTab = 0
                                                navController.navigate(AppRoutes.ROUTE_TRANSPORT) { launchSingleTop = true }
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

                            composable(AppRoutes.ROUTE_CERTIFICATES) {
                                if (uiState.canViewCertificates) {
                                    Phase10CertificatesScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        initialStudentId = certificateJumpStudentId,
                                        onInitialStudentConsumed = { certificateJumpStudentId = null },
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_CASH_BOOK) {
                                if (uiState.canViewCashBook) {
                                    Phase10CashBookScreen(
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

                            composable(AppRoutes.ROUTE_ENQUIRY) {
                                if (uiState.canViewEnquiry) {
                                    Phase10EnquiryScreen(
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

                            composable(AppRoutes.ROUTE_EXAMS) {
                                if (uiState.canViewExams) {
                                    Phase11ExamsScreen(
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

                            composable(AppRoutes.ROUTE_STOCK) {
                                if (uiState.canViewStock) {
                                    Phase11StockScreen(
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

                            composable(AppRoutes.ROUTE_TRANSPORT) {
                                if (uiState.canViewTransport) {
                                    Phase11TransportScreen(
                                        uiState = uiState,
                                        viewModel = viewModel,
                                        initialTab = transportJumpTab,
                                        onJumpConsumed = { transportJumpTab = null },
                                        onOpenSendQueueForTransportDues = { monthCode, routeId ->
                                            if (uiState.canViewFees) {
                                                feeJumpInitialTab = 3
                                                feeJumpQueueMessageType = com.example.domain.FeeMessageType.TRANSPORT_DUE_REMINDER
                                                feeJumpQueueMonthFilter = monthCode
                                                feeJumpQueueRouteId = routeId
                                                navController.navigate(AppRoutes.ROUTE_FEE_MANAGEMENT) { launchSingleTop = true }
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

                            composable(AppRoutes.ROUTE_SALARY) {
                                if (uiState.canViewSalary) {
                                    Phase12SalaryScreen(
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

                            composable(AppRoutes.ROUTE_SESSION_PROMOTION) {
                                if (uiState.canViewSessionPromotion) {
                                    Phase14SessionPromotionScreen(
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

                            composable(AppRoutes.ROUTE_BULK_IMPORT) {
                                if (uiState.canUseBulkStudentImport) {
                                    Phase14BulkImportScreen(
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

                            composable(AppRoutes.ROUTE_BACKUP_EXPORT) {
                                if (uiState.canUseBackupExport) {
                                    Phase14BackupExportScreen(
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

                            composable(AppRoutes.ROUTE_RESULT_ANALYSIS) {
                                if (uiState.canViewResultAnalysis) {
                                    Phase17ResultAnalysisScreen(
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

                            composable(AppRoutes.ROUTE_CLASS_DIARY) {
                                if (uiState.canViewClassDiary) {
                                    Phase18ClassDiaryScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_BIRTHDAYS) {
                                if (uiState.canViewBirthdays) {
                                    Phase18BirthdaysScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_STUDENT_REMARKS) {
                                if (uiState.canOpenStudentRemarksModule) {
                                    Phase19RemarksAndPtmScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            composable(AppRoutes.ROUTE_GATE_PASS) {
                                if (uiState.canViewGateModule) {
                                    Phase19GateAndVisitorScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                } else {
                                    LaunchedEffect(Unit) {
                                        navController.popBackStack()
                                    }
                                }
                            }

                            addDebugDesignPreviewRoute(
                                onBack = { navController.popBackStack() }
                            )
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
            deletedUserUids = uiState.deletedUserUids,
            onDismiss = { showConflictHistoryDialog = false }
        )
    }
}
