package com.example

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.example.domain.MemberPermissions
import com.example.domain.OrgRole
import com.example.ui.AppNavigationRegistry
import com.example.ui.AppRoutes
import com.example.ui.DashboardStatusFilter
import com.example.ui.NavSection
import com.example.ui.SchoolUiState
import com.example.ui.SectionHomePage
import com.example.ui.StudentDashboardHeaderControls
import com.example.ui.StudentRegisterEmptyState
import com.example.ui.StudentRegisterHeader
import com.example.ui.designsystem.GlassBottomBar
import com.example.ui.theme.SchoolAppTheme
import java.io.File
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
class OneStudentPlaceNavigationRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun testUiState(
        role: OrgRole = OrgRole.OWNER,
        permissions: MemberPermissions = MemberPermissions.presetFor(role)
    ): SchoolUiState {
        val school = SchoolEntity(id = 1L, name = "Sunrise Public School")
        return SchoolUiState(
            isInitialized = true,
            schools = listOf(school),
            activeOrg = CloudOrganisation(id = "org1", name = "Sunrise Group", code = "sunrise"),
            currentMember = CloudMember(
                id = "m1",
                uid = "u1",
                name = "Test User",
                role = role.name,
                status = "active",
                allSchools = true,
                permissions = permissions.toFirestoreMap()
            ),
            phase10HindiLabels = false
        )
    }

    @Test
    fun bottomNavStudents_opensStudentsSectionAndLeadsToStudentRegister() {
        var currentObservedRoute = AppRoutes.ROUTE_DASHBOARD
        val ownerState = testUiState(OrgRole.OWNER)
        val navItems = AppNavigationRegistry.buildBottomNavItems(ownerState, isHindi = false)

        val studentsNavItem = navItems.single { it.testTag == "nav_students" }
        assertEquals("Students", studentsNavItem.label)
        assertEquals(
            "Bottom nav 'Students' must point to ROUTE_SECTION_STUDENTS",
            AppRoutes.ROUTE_SECTION_STUDENTS,
            studentsNavItem.id
        )

        var backDispatcher: OnBackPressedDispatcher? = null
        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            SchoolAppTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val activeRoute = backStack?.destination?.route ?: AppRoutes.ROUTE_DASHBOARD
                currentObservedRoute = activeRoute
                val activeSection = AppNavigationRegistry.parentSectionFor(activeRoute)

                fun navigateTopLevel(route: String) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (!AppNavigationRegistry.isSubScreenRoute(activeRoute)) {
                            GlassBottomBar(
                                items = navItems,
                                selectedId = activeSection.route,
                                onSelect = { navigateTopLevel(it.id) },
                                applyNavigationBarInsets = false
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.ROUTE_DASHBOARD,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable(AppRoutes.ROUTE_DASHBOARD) {
                            Column {
                                Text("Dashboard", modifier = Modifier.testTag("test_dashboard_destination"))
                                StudentDashboardHeaderControls(
                                    totalCount = 0,
                                    passCount = 0,
                                    needsAttentionCount = 0,
                                    pendingCount = 0,
                                    activeClassLabel = "1st-A",
                                    searchQuery = "",
                                    onSearchQueryChange = {},
                                    statusFilter = DashboardStatusFilter.ALL,
                                    onStatusFilterSelected = {},
                                    onlyActiveClass = false,
                                    onToggleOnlyActiveClass = {},
                                    onManageAllStudents = {
                                        navController.navigate(AppRoutes.ROUTE_STUDENT_REGISTER) {
                                            launchSingleTop = true
                                        }
                                    },
                                    showManageButton = true
                                )
                            }
                        }
                        composable(AppRoutes.ROUTE_SECTION_STUDENTS) {
                            SectionHomePage(
                                section = NavSection.STUDENTS,
                                uiState = ownerState,
                                onNavigateToRoute = { route ->
                                    navController.navigate(route) { launchSingleTop = true }
                                }
                            )
                        }
                        composable(AppRoutes.ROUTE_SECTION_ACADEMICS) { Text("Academics") }
                        composable(AppRoutes.ROUTE_SECTION_FINANCE) { Text("Finance") }
                        composable(AppRoutes.ROUTE_SECTION_MANAGE) { Text("Manage") }
                        composable(AppRoutes.ROUTE_STUDENT_REGISTER) {
                            Text("Student Register", modifier = Modifier.testTag("phase9_student_register_screen"))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_DASHBOARD, currentObservedRoute)

        // 1. Tap bottom nav "Students" -> opens Students Section Home -> tap Student Register card
        composeRule.onNodeWithTag("nav_students").performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_SECTION_STUDENTS, currentObservedRoute)
        composeRule.onNodeWithTag("section_card_student_register").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_STUDENT_REGISTER, currentObservedRoute)
        composeRule.onNodeWithTag("phase9_student_register_screen").assertIsDisplayed()

        // 2. Pop back to Students, switch to Home, and tap Dashboard "Manage" -> opens Student Register
        composeRule.runOnUiThread {
            backDispatcher?.onBackPressed()
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_SECTION_STUDENTS, currentObservedRoute)

        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_DASHBOARD, currentObservedRoute)

        composeRule.onNodeWithTag("dashboard_manage_students_button").performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_STUDENT_REGISTER, currentObservedRoute)
        composeRule.onNodeWithTag("phase9_student_register_screen").assertIsDisplayed()
    }

    @Test
    fun buildBottomNavItems_respectsStudentViewPermissionsWithoutDuplicateTabs() {
        // Teacher with STUDENTS=VIEW and STUDENT_REGISTER=NONE still sees ONE "Students" tab pointing to Section Home
        val teacherPerms = MemberPermissions.presetFor(OrgRole.TEACHER).copy(
            students = AccessLevel.VIEW,
            studentRegister = AccessLevel.NONE
        )
        val teacherState = testUiState(OrgRole.TEACHER, teacherPerms)
        val teacherItems = AppNavigationRegistry.buildBottomNavItems(teacherState, isHindi = false)
        assertEquals(1, teacherItems.count { it.id == AppRoutes.ROUTE_SECTION_STUDENTS })
        assertEquals("Students", teacherItems.single { it.id == AppRoutes.ROUTE_SECTION_STUDENTS }.label)

        val studentCards = AppNavigationRegistry.cardsForSection(NavSection.STUDENTS, teacherState)
        assertEquals(1, studentCards.count { it.route == AppRoutes.ROUTE_STUDENT_REGISTER })

        // Member with zero Students-section permissions sees no Students tab
        val restrictedPerms = MemberPermissions(
            students = AccessLevel.NONE,
            studentRegister = AccessLevel.NONE,
            enquiry = AccessLevel.NONE,
            certificates = AccessLevel.NONE,
            studentRemarks = AccessLevel.NONE,
            gate = AccessLevel.NONE,
            sessionPromotion = AccessLevel.NONE
        )
        val restrictedState = testUiState(OrgRole.OTHER, restrictedPerms)
        val restrictedItems = AppNavigationRegistry.buildBottomNavItems(restrictedState, isHindi = false)
        assertTrue(restrictedItems.none { it.testTag == "nav_students" })
    }

    @Test
    fun studentRegisterEmptyState_showsAddFirstStudentWithSameButtonAt320dpFont1_5() {
        var addStudentClicks = 0
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = 1.5f)) {
                SchoolAppTheme {
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        StudentRegisterHeader(
                            schoolName = "Sunrise Public School",
                            session = "2026-2027",
                            canEditRegister = true,
                            idCardSelectionMode = false,
                            selectedIdCardsCount = 0,
                            onBack = {},
                            onPrintIdCards = {},
                            onAddStudent = { addStudentClicks++ }
                        )
                        Box(modifier = Modifier.width(320.dp)) {
                            StudentRegisterEmptyState(
                                hasAnyStudentsInSession = false,
                                canEditRegister = true,
                                onAddFirstStudent = { addStudentClicks++ }
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("register_empty_state_card").assertIsDisplayed()
        val btnNode = composeRule.onNodeWithTag("register_add_first_student_button").assertIsDisplayed()
        composeRule.onNodeWithText("Add First Student", useUnmergedTree = false)
        val btnBounds = btnNode.getUnclippedBoundsInRoot()
        assertTrue("Add First Student button >= 48dp tall, got ${btnBounds.height}", btnBounds.height >= 47.5.dp)
        assertTrue("Add First Student button fits 320dp, right=${btnBounds.right}", btnBounds.right <= 320.5.dp)

        composeRule.onNodeWithTag("register_add_student_button").performClick()
        composeRule.onNodeWithTag("register_add_first_student_button").performClick()
        assertEquals(2, addStudentClicks)
    }

    @Test
    fun noScreenOrRouteStillLinksToRemovedStudentsScreen() {
        // 1. AppRoutes must NOT declare ROUTE_STUDENTS
        val routeFieldNames = AppRoutes::class.java.declaredFields.map { it.name }
        assertFalse(
            "AppRoutes must no longer declare ROUTE_STUDENTS",
            "ROUTE_STUDENTS" in routeFieldNames
        )
        assertTrue(
            "AppRoutes must declare ROUTE_STUDENT_REGISTER",
            "ROUTE_STUDENT_REGISTER" in routeFieldNames
        )

        // 2. Old StudentsScreen.kt must be deleted and zero production Kotlin files may link to it
        val candidateSrcRoots = listOf(
            File("src/main/java"),
            File("app/src/main/java")
        )
        val srcRoot = candidateSrcRoots.firstOrNull { it.exists() && it.isDirectory }
        assertTrue("Expected main Kotlin source directory to exist for static link check", srcRoot != null)

        val oldScreenFile = File(srcRoot!!, "com/example/ui/StudentsScreen.kt")
        assertFalse("Old StudentsScreen.kt must be deleted", oldScreenFile.exists())

        val staleReferences = mutableListOf<String>()
        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                if (Regex("\\bStudentsScreen\\b").containsMatchIn(text)) {
                    staleReferences += "${file.name}: StudentsScreen"
                }
                if (Regex("\\bROUTE_STUDENTS\\b").containsMatchIn(text)) {
                    staleReferences += "${file.name}: ROUTE_STUDENTS"
                }
                if (text.contains("\"tile_students\"")) {
                    staleReferences += "${file.name}: tile_students"
                }
            }

        assertTrue(
            "Found stale links to removed StudentsScreen: $staleReferences",
            staleReferences.isEmpty()
        )
    }
}
