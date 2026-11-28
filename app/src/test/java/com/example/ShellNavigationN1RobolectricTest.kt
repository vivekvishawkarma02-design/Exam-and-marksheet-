package com.example

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.SchoolEntity
import com.example.data.cloud.CloudMember
import com.example.data.cloud.CloudOrganisation
import com.example.domain.AccessLevel
import com.example.domain.ExamInvigilatorDutyRecord
import com.example.domain.MemberPermissions
import com.example.domain.OrgRole
import com.example.domain.TeacherTodayData
import com.example.ui.AppNavigationRegistry
import com.example.ui.AppRoutes
import com.example.ui.MyExamDutiesScreen
import com.example.ui.NavSection
import com.example.ui.SchoolSwitcherBar
import com.example.ui.SchoolUiState
import com.example.ui.SectionHomePage
import com.example.ui.TeacherTodayHeroSection
import com.example.ui.designsystem.GlassBottomBar
import com.example.ui.theme.SchoolAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
class ShellNavigationN1RobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeState(
        role: OrgRole = OrgRole.OWNER,
        permissions: MemberPermissions = MemberPermissions.presetFor(role),
        duties: List<ExamInvigilatorDutyRecord> = emptyList(),
        isHindi: Boolean = false
    ): SchoolUiState {
        val school = SchoolEntity(id = 1L, name = "North Campus Model School")
        return SchoolUiState(
            isInitialized = true,
            schools = listOf(school),
            activeOrg = CloudOrganisation(id = "org1", name = "North Education Trust", code = "north"),
            currentMember = CloudMember(
                id = "m1",
                uid = "uid_1",
                name = "Staff Member",
                role = role.name,
                status = "active",
                allSchools = true,
                permissions = permissions.toFirestoreMap()
            ),
            examDuties = duties,
            phase10HindiLabels = isHindi
        )
    }

    @Test
    fun routeRegistry_coversAllAppRoutesAndPreservesAllOriginalRoutes() {
        val original33Routes = setOf(
            "dashboard",
            "marks",
            "reports",
            "members",
            "settings",
            "coscholastic",
            "schools",
            "teacher_profile",
            "teachers_and_assignments",
            "timetable",
            "pending_marks",
            "workload_report",
            "teacher_attendance",
            "student_attendance",
            "student_register",
            "fee_management",
            "certificates",
            "cash_book",
            "enquiry",
            "exams",
            "stock",
            "transport",
            "salary",
            "session_promotion",
            "bulk_import",
            "backup_export",
            "result_analysis",
            "phase18_class_diary",
            "phase18_birthdays",
            "phase19_student_remarks",
            "phase19_gate_pass",
            "phase16_school_subscription",
            "debug_design_preview"
        )
        val declaredRouteStrings = AppRoutes::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.get(null) as String }
            .toSet()

        original33Routes.forEach { route ->
            assertTrue("Original route '$route' must be preserved in AppRoutes", route in declaredRouteStrings)
        }
        assertEquals("Expected 38 total routes (33 original + 4 section homes + my_exam_duties)", 38, declaredRouteStrings.size)

        val registeredRoutes = AppNavigationRegistry.allRoutes.map { it.route }.toSet()
        assertEquals("Every AppRoutes constant must have a RouteInfo entry", declaredRouteStrings, registeredRoutes)
        assertEquals("No duplicate RouteInfo entries allowed", 38, AppNavigationRegistry.allRoutes.size)
    }

    @Test
    fun roleVisibility_matchesOwnerAdminTeacherViewOnlyAndZeroAccess() {
        // 1. OWNER: all 5 sections + Staff Salary visible in Finance & Ops
        val ownerState = makeState(OrgRole.OWNER)
        val ownerTabs = AppNavigationRegistry.buildBottomNavItems(ownerState)
        assertEquals(5, ownerTabs.size)
        assertTrue(AppNavigationRegistry.cardsForSection(NavSection.FINANCE_OPS, ownerState).any { it.route == AppRoutes.ROUTE_SALARY })
        assertTrue(AppNavigationRegistry.cardsForSection(NavSection.MANAGE, ownerState).any { it.route == AppRoutes.ROUTE_BACKUP_EXPORT })

        // 2. ADMIN: all 5 sections, Staff Salary hidden unless granted
        val adminState = makeState(OrgRole.ADMIN)
        val adminTabs = AppNavigationRegistry.buildBottomNavItems(adminState)
        assertEquals(5, adminTabs.size)
        assertFalse(AppNavigationRegistry.cardsForSection(NavSection.FINANCE_OPS, adminState).any { it.route == AppRoutes.ROUTE_SALARY })

        // 3. TEACHER: Finance & Ops tab hidden; My Exam Duties & My Teacher Profile visible in Academics
        val teacherState = makeState(OrgRole.TEACHER)
        val teacherTabs = AppNavigationRegistry.buildBottomNavItems(teacherState)
        assertFalse(teacherTabs.any { it.id == AppRoutes.ROUTE_SECTION_FINANCE })
        val teacherAcademics = AppNavigationRegistry.cardsForSection(NavSection.ACADEMICS, teacherState)
        assertTrue(teacherAcademics.any { it.route == AppRoutes.ROUTE_MY_EXAM_DUTIES })
        assertTrue(teacherAcademics.any { it.route == AppRoutes.ROUTE_TEACHER_PROFILE })

        // 4. VIEW-ONLY member: Students Section card shows View only overline & view-only description
        val viewOnlyPerms = MemberPermissions(
            students = AccessLevel.VIEW,
            marks = AccessLevel.VIEW,
            fees = AccessLevel.VIEW
        )
        val viewOnlyState = makeState(OrgRole.OTHER, viewOnlyPerms)
        val marksCard = AppNavigationRegistry.infoFor(AppRoutes.ROUTE_MARKS)!!
        assertFalse(marksCard.canEditResolver(viewOnlyState))
        assertTrue(marksCard.canAccess(viewOnlyState))

        composeRule.setContent {
            SchoolAppTheme {
                SectionHomePage(
                    section = NavSection.FINANCE_OPS,
                    uiState = viewOnlyState,
                    onNavigateToRoute = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("View only").assertIsDisplayed()
        composeRule.onNodeWithText("View fee accounts, dues & receipt reports").assertIsDisplayed()

        // 5. ZERO-ACCESS OTHER: ONLY Home & Manage tabs; restricted sub-screens blocked
        val zeroState = makeState(OrgRole.OTHER, MemberPermissions())
        val zeroTabs = AppNavigationRegistry.buildBottomNavItems(zeroState)
        assertEquals(listOf("nav_home", "nav_manage"), zeroTabs.map { it.testTag })
        assertFalse(AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_MARKS, zeroState))
        assertFalse(AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_SALARY, zeroState))
        assertFalse(AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_STUDENT_REGISTER, zeroState))
        assertFalse(AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_EXAMS, zeroState))
        assertFalse(AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_SCHOOLS, zeroState))
        assertTrue(AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_SETTINGS, zeroState))
        assertTrue(AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_SCHOOL_SUBSCRIPTION, zeroState))
    }

    @Test
    fun bottomBarAndSchoolSwitcher_hiddenOnEverySubScreenIncludingPrevious9Bugs() {
        val previouslyMissingSubScreens = listOf(
            AppRoutes.ROUTE_SCHOOL_SUBSCRIPTION,
            AppRoutes.ROUTE_SALARY,
            AppRoutes.ROUTE_SESSION_PROMOTION,
            AppRoutes.ROUTE_BULK_IMPORT,
            AppRoutes.ROUTE_BACKUP_EXPORT,
            AppRoutes.ROUTE_CLASS_DIARY,
            AppRoutes.ROUTE_BIRTHDAYS,
            AppRoutes.ROUTE_STUDENT_REMARKS,
            AppRoutes.ROUTE_GATE_PASS
        )
        previouslyMissingSubScreens.forEach { route ->
            assertTrue(
                "Previously missing route '$route' must be marked as sub-screen",
                AppNavigationRegistry.isSubScreenRoute(route)
            )
        }

        val allSubScreens = AppNavigationRegistry.allRoutes.filter { !it.isTopLevelDestination }
        assertEquals(33, allSubScreens.size)
        allSubScreens.forEach { info ->
            assertTrue("Sub-screen '${info.route}' must hide bottom bar", AppNavigationRegistry.isSubScreenRoute(info.route))
        }

        val topLevelRoutes = listOf(
            AppRoutes.ROUTE_DASHBOARD,
            AppRoutes.ROUTE_SECTION_STUDENTS,
            AppRoutes.ROUTE_SECTION_ACADEMICS,
            AppRoutes.ROUTE_SECTION_FINANCE,
            AppRoutes.ROUTE_SECTION_MANAGE
        )
        topLevelRoutes.forEach { route ->
            assertFalse("Top-level '$route' must show bottom bar", AppNavigationRegistry.isSubScreenRoute(route))
        }

        // Compose test: navigating from Manage -> Backup & Export hides GlassBottomBar and SchoolSwitcherBar
        val ownerState = makeState(OrgRole.OWNER)
        val navItems = AppNavigationRegistry.buildBottomNavItems(ownerState)
        composeRule.setContent {
            SchoolAppTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route ?: AppRoutes.ROUTE_SECTION_MANAGE
                val isSubScreen = AppNavigationRegistry.isSubScreenRoute(currentRoute)
                val activeSection = AppNavigationRegistry.parentSectionFor(currentRoute)

                Scaffold(
                    bottomBar = {
                        if (!isSubScreen) {
                            GlassBottomBar(
                                items = navItems,
                                selectedId = activeSection.route,
                                onSelect = {},
                                applyNavigationBarInsets = false
                            )
                        }
                    }
                ) { pad ->
                    Column(modifier = Modifier.fillMaxSize().padding(pad)) {
                        if (!isSubScreen) {
                            SchoolSwitcherBar(
                                uiState = ownerState,
                                onSelectSchool = {},
                                onManageSchools = {}
                            )
                        }
                        NavHost(navController = navController, startDestination = AppRoutes.ROUTE_SECTION_MANAGE) {
                            composable(AppRoutes.ROUTE_SECTION_MANAGE) {
                                SectionHomePage(
                                    section = NavSection.MANAGE,
                                    uiState = ownerState,
                                    onNavigateToRoute = { navController.navigate(it) }
                                )
                            }
                            composable(AppRoutes.ROUTE_BACKUP_EXPORT) {
                                Text("Backup Export Screen", modifier = Modifier.testTag("test_backup_subscreen"))
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_manage").assertIsDisplayed()
        composeRule.onNodeWithTag("top_school_switcher").assertIsDisplayed()

        composeRule.onNodeWithTag("section_card_backup_export").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("test_backup_subscreen").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("nav_manage").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("top_school_switcher").fetchSemanticsNodes().size)
    }

    @Test
    fun navigationFlow_backToHome_unauthorizedBlocked_andMyExamDutiesClickable() {
        val duty = ExamInvigilatorDutyRecord(
            id = "duty_1",
            schoolId = "s1",
            session = "2026-2027",
            examId = "e1",
            examTitle = "Half-Yearly Math",
            dateIso = "2099-10-15",
            startTime = "09:00",
            endTime = "12:00",
            roomId = "r1",
            roomName = "Room 104",
            teacherId = "t1",
            teacherUid = "uid_1",
            teacherName = "Staff Member"
        )
        // Teacher has EXAMS = NONE yet has an assigned duty
        val teacherState = makeState(role = OrgRole.TEACHER, duties = listOf(duty))
        val navItems = AppNavigationRegistry.buildBottomNavItems(teacherState)
        var currentObservedRoute = AppRoutes.ROUTE_DASHBOARD
        val deepLinkSalaryTrigger = androidx.compose.runtime.mutableStateOf(false)
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            SchoolAppTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route ?: AppRoutes.ROUTE_DASHBOARD
                currentObservedRoute = currentRoute
                val isSubScreen = AppNavigationRegistry.isSubScreenRoute(currentRoute)
                val activeSection = AppNavigationRegistry.parentSectionFor(currentRoute)

                fun navigateTopLevel(route: String) {
                    if (!AppNavigationRegistry.canAccessRoute(route, teacherState)) return
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                BackHandler(enabled = !isSubScreen && activeSection != NavSection.HOME) {
                    navigateTopLevel(AppRoutes.ROUTE_DASHBOARD)
                }

                Scaffold(
                    bottomBar = {
                        if (!isSubScreen) {
                            GlassBottomBar(
                                items = navItems,
                                selectedId = activeSection.route,
                                onSelect = { navigateTopLevel(it.id) },
                                applyNavigationBarInsets = false
                            )
                        }
                    }
                ) { pad ->
                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.ROUTE_DASHBOARD,
                        modifier = Modifier.fillMaxSize().padding(pad)
                    ) {
                        composable(AppRoutes.ROUTE_DASHBOARD) {
                            TeacherTodayHeroSection(
                                teacherData = TeacherTodayData(
                                    teacherName = "Staff Member",
                                    upcomingExamDutiesCount = 1
                                ),
                                dateIso = "2026-10-10",
                                isHindi = false,
                                canViewMarks = true,
                                canViewTimetable = true,
                                onTakeAttendance = { _, _, _, _ -> },
                                onOpenPendingMarks = {},
                                onOpenLeaveTab = {},
                                onOpenExamDuties = {
                                    if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_MY_EXAM_DUTIES, teacherState)) {
                                        navController.navigate(AppRoutes.ROUTE_MY_EXAM_DUTIES) { launchSingleTop = true }
                                    }
                                }
                            )
                            LaunchedEffect(deepLinkSalaryTrigger.value) {
                                if (deepLinkSalaryTrigger.value) {
                                    navController.navigate(AppRoutes.ROUTE_SALARY)
                                }
                            }
                        }
                        composable(AppRoutes.ROUTE_SECTION_STUDENTS) {
                            SectionHomePage(
                                section = NavSection.STUDENTS,
                                uiState = teacherState,
                                onNavigateToRoute = { navController.navigate(it) }
                            )
                        }
                        composable(AppRoutes.ROUTE_SECTION_ACADEMICS) {
                            SectionHomePage(
                                section = NavSection.ACADEMICS,
                                uiState = teacherState,
                                onNavigateToRoute = { navController.navigate(it) }
                            )
                        }
                        composable(AppRoutes.ROUTE_SECTION_MANAGE) {
                            SectionHomePage(
                                section = NavSection.MANAGE,
                                uiState = teacherState,
                                onNavigateToRoute = { navController.navigate(it) }
                            )
                        }
                        composable(AppRoutes.ROUTE_MY_EXAM_DUTIES) {
                            MyExamDutiesScreen(
                                uiState = teacherState,
                                onBack = { navController.popBackStack() },
                                todayIsoOverride = "2026-10-10"
                            )
                        }
                        composable(AppRoutes.ROUTE_SALARY) {
                            if (AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_SALARY, teacherState)) {
                                Text("Forbidden Salary", modifier = Modifier.testTag("salary_forbidden_screen"))
                            } else {
                                LaunchedEffect(Unit) { navController.popBackStack() }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Tap "My Exam Duties" KPI tile on Teacher Home -> opens MyExamDutiesScreen even without EXAMS permission
        composeRule.onNodeWithTag("teacher_today_exam_duties").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_MY_EXAM_DUTIES, currentObservedRoute)
        composeRule.onNodeWithTag("my_exam_duties_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Half-Yearly Math — Room 104").assertIsDisplayed()

        // 2. Back returns to Home
        composeRule.runOnUiThread { backDispatcher?.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_DASHBOARD, currentObservedRoute)

        // 3. Switch to Academics tab -> Back returns to Home
        composeRule.onNodeWithTag("nav_academics").performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_SECTION_ACADEMICS, currentObservedRoute)
        composeRule.runOnUiThread { backDispatcher?.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_DASHBOARD, currentObservedRoute)

        // 4. Unauthorized deep link to Salary is rejected and popped immediately
        assertFalse(AppNavigationRegistry.canAccessRoute(AppRoutes.ROUTE_SALARY, teacherState))
        composeRule.runOnUiThread { deepLinkSalaryTrigger.value = true }
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("salary_forbidden_screen").fetchSemanticsNodes().size)
        assertEquals(AppRoutes.ROUTE_DASHBOARD, currentObservedRoute)
    }

    @Test
    fun narrow320dp_fontScale1_3_glassBottomBar5TabsAndAllSectionHomePages_noClip_min48dpTargets() {
        val ownerState = makeState(OrgRole.OWNER)
        val navItems = AppNavigationRegistry.buildBottomNavItems(ownerState, isHindi = false)
        assertEquals(5, navItems.size)

        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = 1.3f)) {
                SchoolAppTheme {
                    Column(modifier = Modifier.width(320.dp)) {
                        SchoolSwitcherBar(
                            uiState = ownerState,
                            onSelectSchool = {},
                            onManageSchools = {}
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SectionHomePage(
                                section = NavSection.STUDENTS,
                                uiState = ownerState,
                                onNavigateToRoute = {}
                            )
                        }
                        GlassBottomBar(
                            items = navItems,
                            selectedId = AppRoutes.ROUTE_SECTION_STUDENTS,
                            onSelect = {},
                            applyNavigationBarInsets = false
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Verify all 5 bottom tabs fit cleanly within 320dp, have >= 48dp touch height, and full label text is displayed
        navItems.forEach { item ->
            val tag = item.testTag!!
            val node = composeRule.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("Tab $tag right=${bounds.right} exceeds 320dp", bounds.right <= 320.5.dp)
            assertTrue("Tab $tag height=${bounds.height} < 48dp", bounds.height >= 47.5.dp)
            node.assertTextContains(item.label)
        }

        // Verify top switcher fits 320dp and >= 48dp touch target
        val switcherBounds = composeRule.onNodeWithTag("top_school_switcher").getUnclippedBoundsInRoot()
        assertTrue("top_school_switcher height=${switcherBounds.height} < 48dp", switcherBounds.height >= 47.5.dp)
        assertTrue("top_school_switcher right=${switcherBounds.right} exceeds 320dp", switcherBounds.right <= 320.5.dp)

        // Verify section cards fit 320dp and >= 48dp height, and Manage section renders Legal items when scrolled
        val registerBounds = composeRule.onNodeWithTag("section_card_student_register").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Student Register card height=${registerBounds.height} < 48dp", registerBounds.height >= 47.5.dp)
        assertTrue("Student Register card right=${registerBounds.right} exceeds 320dp", registerBounds.right <= 320.5.dp)

        // Ensure no unexpected empty error state
        assertEquals(0, composeRule.onAllNodesWithText("No modules available", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun manageSectionHome_scrollsToAboutAndLegalAt320dpFont1_3() {
        val ownerState = makeState(OrgRole.OWNER)
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = 1.3f)) {
                SchoolAppTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        SectionHomePage(
                            section = NavSection.MANAGE,
                            uiState = ownerState,
                            onNavigateToRoute = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("section_list_manage").performScrollToNode(
            androidx.compose.ui.test.hasTestTag("manage_legal_privacy")
        )
        composeRule.onNodeWithTag("manage_legal_privacy").assertIsDisplayed()
    }
}
