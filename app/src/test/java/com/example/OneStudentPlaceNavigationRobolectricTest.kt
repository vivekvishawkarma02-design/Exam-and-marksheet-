package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.example.ui.AppRoutes
import com.example.ui.DashboardStatusFilter
import com.example.ui.StudentDashboardHeaderControls
import com.example.ui.StudentRegisterEmptyState
import com.example.ui.StudentRegisterHeader
import com.example.ui.buildWorkspaceTopNavItems
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

    @Test
    fun bottomNavStudents_opensStudentRegisterAndNothingElse() {
        var currentObservedRoute = AppRoutes.ROUTE_DASHBOARD
        val navItems = buildWorkspaceTopNavItems(
            canViewStudents = true,
            canViewStudentRegister = true,
            canViewMarks = true,
            canViewReports = true,
            canViewMembersOrAdmin = true,
            pendingJoinRequestCount = 0
        )

        val studentsNavItem = navItems.single { it.testTag == "nav_students" }
        assertEquals("Students", studentsNavItem.label)
        assertEquals(
            "Bottom nav 'Students' must point directly to ROUTE_STUDENT_REGISTER",
            AppRoutes.ROUTE_STUDENT_REGISTER,
            studentsNavItem.route
        )

        composeRule.setContent {
            SchoolAppTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val activeRoute = backStack?.destination?.route ?: AppRoutes.ROUTE_DASHBOARD
                currentObservedRoute = activeRoute

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
                        NavigationBar {
                            navItems.forEach { item ->
                                val selected = activeRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
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
                                    onManageAllStudents = { navigateTopLevel(AppRoutes.ROUTE_STUDENT_REGISTER) },
                                    showManageButton = true
                                )
                            }
                        }
                        composable(AppRoutes.ROUTE_STUDENT_REGISTER) {
                            Text("Student Register", modifier = Modifier.testTag("phase9_student_register_screen"))
                        }
                        composable(AppRoutes.ROUTE_MARKS) { Text("Marks") }
                        composable(AppRoutes.ROUTE_REPORTS) { Text("Reports") }
                        composable(AppRoutes.ROUTE_MEMBERS) { Text("Members") }
                        composable(AppRoutes.ROUTE_SETTINGS) { Text("Settings") }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_DASHBOARD, currentObservedRoute)

        // 1. Tap bottom nav "Students" -> must open Student Register
        composeRule.onNodeWithTag("nav_students").performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_STUDENT_REGISTER, currentObservedRoute)
        composeRule.onNodeWithTag("phase9_student_register_screen").assertIsDisplayed()

        // 2. Return Home and tap Dashboard "Manage" ("manage all students") -> must open Student Register
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_DASHBOARD, currentObservedRoute)

        composeRule.onNodeWithTag("dashboard_manage_students_button").performClick()
        composeRule.waitForIdle()
        assertEquals(AppRoutes.ROUTE_STUDENT_REGISTER, currentObservedRoute)
        composeRule.onNodeWithTag("phase9_student_register_screen").assertIsDisplayed()
    }

    @Test
    fun buildWorkspaceTopNavItems_respectsStudentViewPermissionsWithoutDuplicateTabs() {
        // Teacher with STUDENTS=VIEW and STUDENT_REGISTER=NONE still sees ONE "Students" tab pointing to Register
        val teacherItems = buildWorkspaceTopNavItems(
            canViewStudents = true,
            canViewStudentRegister = false,
            canViewMarks = true,
            canViewReports = true,
            canViewMembersOrAdmin = false,
            pendingJoinRequestCount = 0
        )
        assertEquals(1, teacherItems.count { it.route == AppRoutes.ROUTE_STUDENT_REGISTER })
        assertEquals("Students", teacherItems.single { it.route == AppRoutes.ROUTE_STUDENT_REGISTER }.label)

        // Member with neither STUDENTS nor STUDENT_REGISTER sees no Students tab
        val restrictedItems = buildWorkspaceTopNavItems(
            canViewStudents = false,
            canViewStudentRegister = false,
            canViewMarks = false,
            canViewReports = false,
            canViewMembersOrAdmin = false,
            pendingJoinRequestCount = 0
        )
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

        // Both header Add Student and empty-state Add First Student trigger the same action
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
