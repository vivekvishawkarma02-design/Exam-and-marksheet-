package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

object AppRoutes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_STUDENTS = "students"
    const val ROUTE_MARKS = "marks"
    const val ROUTE_REPORTS = "reports"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_COSCHOLASTIC = "coscholastic"
}

private data class TopNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topNavItems = listOf(
    TopNavItem(AppRoutes.ROUTE_DASHBOARD, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    TopNavItem(AppRoutes.ROUTE_STUDENTS, "Students", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_students"),
    TopNavItem(AppRoutes.ROUTE_MARKS, "Marks", Icons.Filled.EditNote, Icons.Outlined.EditNote, "nav_marks"),
    TopNavItem(AppRoutes.ROUTE_REPORTS, "Reports", Icons.Filled.Description, Icons.Outlined.Description, "nav_reports"),
    TopNavItem(AppRoutes.ROUTE_SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
)

@Composable
fun CpiReportCardApp(
    viewModel: CpiViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var focusBackupSection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.ROUTE_DASHBOARD

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail && currentRoute != AppRoutes.ROUTE_COSCHOLASTIC) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        topNavItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
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
                if (useRail && currentRoute != AppRoutes.ROUTE_COSCHOLASTIC) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        topNavItems.forEach { item ->
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

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                                onNavigateToStudents = { navigateTopLevel(AppRoutes.ROUTE_STUDENTS) },
                                onNavigateToMarks = { navigateTopLevel(AppRoutes.ROUTE_MARKS) },
                                onNavigateToCoScholastic = { navController.navigate(AppRoutes.ROUTE_COSCHOLASTIC) },
                                onNavigateToReports = { navigateTopLevel(AppRoutes.ROUTE_REPORTS) },
                                onNavigateToSettings = {
                                    focusBackupSection = false
                                    navigateTopLevel(AppRoutes.ROUTE_SETTINGS)
                                },
                                onNavigateToBackup = {
                                    focusBackupSection = true
                                    navigateTopLevel(AppRoutes.ROUTE_SETTINGS)
                                }
                            )
                        }

                        composable(AppRoutes.ROUTE_STUDENTS) {
                            StudentsScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenCoScholastic = { navController.navigate(AppRoutes.ROUTE_COSCHOLASTIC) }
                            )
                        }

                        composable(AppRoutes.ROUTE_MARKS) {
                            MarksEntryScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onNavigateToStudents = { navigateTopLevel(AppRoutes.ROUTE_STUDENTS) }
                            )
                        }

                        composable(AppRoutes.ROUTE_REPORTS) {
                            ReportsScreen(
                                uiState = uiState,
                                viewModel = viewModel
                            )
                        }

                        composable(AppRoutes.ROUTE_SETTINGS) {
                            SettingsScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                focusBackupSection = focusBackupSection,
                                onBackupFocusConsumed = { focusBackupSection = false }
                            )
                        }

                        composable(AppRoutes.ROUTE_COSCHOLASTIC) {
                            CoScholasticScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
