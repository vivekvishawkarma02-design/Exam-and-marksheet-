package com.example.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.ErrorState
import com.example.ui.designsystem.GlassBackdropCanvas
import com.example.ui.designsystem.GlassBottomBar
import com.example.ui.designsystem.GlassBottomBarItem
import com.example.ui.designsystem.GlassButton
import com.example.ui.designsystem.GlassButtonVariant
import com.example.ui.designsystem.GlassCard
import com.example.ui.designsystem.GlassChip
import com.example.ui.designsystem.GlassDialog
import com.example.ui.designsystem.GlassIntensity
import com.example.ui.designsystem.GlassListItem
import com.example.ui.designsystem.GlassSheet
import com.example.ui.designsystem.GlassTextField
import com.example.ui.designsystem.GlassTopBar
import com.example.ui.designsystem.LoadingState
import com.example.ui.designsystem.SectionHeader
import com.example.ui.theme.AppTheme
import com.example.ui.theme.LocalAppearanceController
import com.example.ui.theme.SchoolAppTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.calmEnterOnce

fun NavGraphBuilder.addDebugDesignPreviewRoute(
    onBack: () -> Unit
) {
    composable(AppRoutes.ROUTE_DESIGN_PREVIEW) {
        DebugDesignPreviewScreen(onBack = onBack)
    }
}

/**
 * Debug-only showcase of all Task D1 design system components with live switches for:
 * - Light / Dark theme
 * - Glass effects ON / Simple Look ON
 * - Narrow 320dp container width preview
 * - Large font scale (1.3x) preview
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebugDesignPreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controller = LocalAppearanceController.current
    val currentSettings = controller?.settings?.value

    var previewDark by remember { mutableStateOf(false) }
    var previewSimpleLook by remember { mutableStateOf(currentSettings?.simpleLook ?: false) }
    var previewNarrow320dp by remember { mutableStateOf(false) }
    var previewLargeFont by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf("home") }
    var selectedChip by remember { mutableStateOf("All Classes") }
    var textValue by remember { mutableStateOf("Grade 6 - Section A") }
    var errorTextValue by remember { mutableStateOf("999") }
    var showSheet by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var showLoadingSkeleton by remember { mutableStateOf(true) }

    val baseDensity = LocalDensity.current
    val previewDensity = remember(baseDensity, previewLargeFont) {
        Density(
            density = baseDensity.density,
            fontScale = if (previewLargeFont) 1.3f else 1.0f
        )
    }

    SchoolAppTheme(
        themeMode = if (previewDark) ThemeMode.DARK else ThemeMode.LIGHT,
        simpleLook = previewSimpleLook
    ) {
        CompositionLocalProvider(LocalDensity provides previewDensity) {
            val spacing = AppTheme.spacing
            val colors = AppTheme.colors

            GlassBackdropCanvas(modifier = modifier.fillMaxSize()) {
                val frameModifier = if (previewNarrow320dp) {
                    Modifier
                        .width(320.dp)
                        .align(Alignment.TopCenter)
                } else {
                    Modifier.fillMaxWidth()
                }

                Column(modifier = frameModifier.fillMaxSize()) {
                    GlassTopBar(
                        title = "Design Preview (Debug)",
                        subtitle = if (previewSimpleLook) "Simple look (solid)" else "Liquid glass active",
                        onNavigationClick = onBack,
                        applyStatusBarInsets = false,
                        actions = {
                            IconButton(
                                onClick = { showSheet = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Open sample sheet",
                                    tint = colors.primaryAccent
                                )
                            }
                        }
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("debug_design_preview_list"),
                        contentPadding = PaddingValues(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.lg)
                    ) {
                        // 1. Live Preview Controls
                        item {
                            GlassCard(
                                intensity = GlassIntensity.Prominent,
                                modifier = Modifier.calmEnterOnce(0)
                            ) {
                                SectionHeader(
                                    title = "Preview Environment Controls",
                                    subtitle = "Inspect Dark mode, Simple look, 320dp width & 1.3x font",
                                    leadingIcon = Icons.Default.AutoAwesome
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                                ) {
                                    GlassChip(
                                        label = if (previewDark) "Dark Mode" else "Light Mode",
                                        selected = previewDark,
                                        onClick = { previewDark = !previewDark },
                                        modifier = Modifier.testTag("preview_toggle_dark")
                                    )
                                    GlassChip(
                                        label = if (previewSimpleLook) "Simple Look: ON" else "Glass: ON",
                                        selected = previewSimpleLook,
                                        onClick = { previewSimpleLook = !previewSimpleLook },
                                        modifier = Modifier.testTag("preview_toggle_simple_look")
                                    )
                                    GlassChip(
                                        label = if (previewNarrow320dp) "Width: 320dp" else "Width: Full",
                                        selected = previewNarrow320dp,
                                        onClick = { previewNarrow320dp = !previewNarrow320dp },
                                        modifier = Modifier.testTag("preview_toggle_320dp")
                                    )
                                    GlassChip(
                                        label = if (previewLargeFont) "Font: 1.3x" else "Font: 1.0x",
                                        selected = previewLargeFont,
                                        onClick = { previewLargeFont = !previewLargeFont },
                                        modifier = Modifier.testTag("preview_toggle_font_1_3")
                                    )
                                }
                            }
                        }

                        // 2. SectionHeader & GlassCard
                        item {
                            Column(
                                modifier = Modifier.calmEnterOnce(1),
                                verticalArrangement = Arrangement.spacedBy(spacing.sm)
                            ) {
                                SectionHeader(
                                    title = "Fee Collection & Attendance Summary",
                                    subtitle = "Wraps cleanly when narrow or scaled",
                                    leadingIcon = Icons.Default.School,
                                    actionLabel = "Open Sheet",
                                    onAction = { showSheet = true }
                                )
                                GlassCard {
                                    Text(
                                        text = "Term 1 Fee Balance",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Pending dues: ₹1,250 across 4 invoices",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.warningMoneyDue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "All foreground typography keeps >= 4.5:1 contrast on glass.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }

                        // 3. GlassListItem (standard + warning/money)
                        item {
                            Column(
                                modifier = Modifier.calmEnterOnce(2),
                                verticalArrangement = Arrangement.spacedBy(spacing.sm)
                            ) {
                                SectionHeader(
                                    title = "Glass List Items",
                                    actionLabel = "Dialog",
                                    onAction = { showDialog = true }
                                )
                                GlassListItem(
                                    overlineText = "ACADEMIC TERM 2026-27",
                                    headline = "Annual Science & Mathematics Assessment",
                                    supportingText = "Marks verified for 42 enrolled learners in Class 6-A",
                                    badgeText = "98% Done",
                                    leadingIcon = Icons.Default.CheckCircle,
                                    onClick = { showDialog = true }
                                )
                                GlassListItem(
                                    overlineText = "FEE DUES ALERT",
                                    headline = "Transport Fee Installment Pending",
                                    supportingText = "Due reminder ready for Route 2 families",
                                    badgeText = "₹1,450 Due",
                                    isWarning = true,
                                    leadingIcon = Icons.Default.AccountBalanceWallet,
                                    onClick = { showSheet = true }
                                )
                            }
                        }

                        // 4. GlassButton variants & GlassChip
                        item {
                            GlassCard(modifier = Modifier.calmEnterOnce(3)) {
                                SectionHeader(title = "Buttons & Filter Chips")
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                                ) {
                                    GlassButton(
                                        text = "Primary Action",
                                        onClick = { showDialog = true },
                                        variant = GlassButtonVariant.Primary,
                                        leadingIcon = Icons.Default.Assessment
                                    )
                                    GlassButton(
                                        text = "Secondary",
                                        onClick = { showSheet = true },
                                        variant = GlassButtonVariant.Secondary
                                    )
                                    GlassButton(
                                        text = "Text Action",
                                        onClick = {},
                                        variant = GlassButtonVariant.Text
                                    )
                                    GlassButton(
                                        text = "Clear Cache",
                                        onClick = { showDialog = true },
                                        variant = GlassButtonVariant.Secondary,
                                        isDestructive = true
                                    )
                                }
                                Spacer(modifier = Modifier.height(spacing.xs))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                                ) {
                                    listOf("All Classes", "Class 6-A", "Dues Pending").forEach { chip ->
                                        GlassChip(
                                            label = chip,
                                            selected = selectedChip == chip,
                                            isWarning = chip == "Dues Pending",
                                            leadingIcon = if (chip == "Dues Pending") Icons.Default.WarningAmber else null,
                                            onClick = { selectedChip = chip }
                                        )
                                    }
                                }
                            }
                        }

                        // 5. GlassTextField
                        item {
                            GlassCard(modifier = Modifier.calmEnterOnce(4)) {
                                SectionHeader(title = "Text Fields")
                                GlassTextField(
                                    value = textValue,
                                    onValueChange = { textValue = it },
                                    label = "Search Class or Section",
                                    placeholder = "e.g. Class 6-A",
                                    supportingText = "Supports keyboard-safe IME padding",
                                    leadingIcon = Icons.Default.Search
                                )
                                GlassTextField(
                                    value = errorTextValue,
                                    onValueChange = { errorTextValue = it },
                                    label = "Marks Out of 100",
                                    supportingText = "Marks cannot exceed maximum score (100).",
                                    isError = true
                                )
                            }
                        }

                        // 6. EmptyState, ErrorState, LoadingState
                        item {
                            Column(
                                modifier = Modifier.calmEnterOnce(5),
                                verticalArrangement = Arrangement.spacedBy(spacing.md)
                            ) {
                                SectionHeader(title = "Feedback States")
                                EmptyState(
                                    title = "No Timetable Entries Yet",
                                    message = "Add periods for Monday through Saturday to view teacher schedules.",
                                    actionLabel = "Add First Period",
                                    onAction = { showSheet = true }
                                )
                                ErrorState(
                                    title = "Sync Interrupted",
                                    message = "Unable to refresh attendance records offline. Changes stay saved on device.",
                                    retryLabel = "Retry Sync",
                                    onRetry = {}
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Loading Skeleton State",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary
                                    )
                                    Switch(
                                        checked = showLoadingSkeleton,
                                        onCheckedChange = { showLoadingSkeleton = it }
                                    )
                                }
                                if (showLoadingSkeleton) {
                                    LoadingState(itemCount = 2)
                                }
                            }
                        }
                    }

                    GlassBottomBar(
                        items = listOf(
                            GlassBottomBarItem("home", "Home", Icons.Default.Home),
                            GlassBottomBarItem("students", "Students", Icons.Default.Groups, badgeCount = 3),
                            GlassBottomBarItem("marks", "Marks", Icons.Default.EditNote),
                            GlassBottomBarItem("reports", "Reports", Icons.Default.Description)
                        ),
                        selectedId = selectedTab,
                        onSelect = { selectedTab = it.id },
                        applyNavigationBarInsets = false
                    )
                }

                if (showSheet) {
                    GlassSheet(
                        onDismissRequest = { showSheet = false },
                        title = "Sample Glass Sheet",
                        subtitle = "Scrollable sheet with IME & navigation bar insets",
                        footer = {
                            GlassButton(
                                text = "Done",
                                onClick = { showSheet = false },
                                variant = GlassButtonVariant.Primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    ) {
                        GlassTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            label = "Quick Note"
                        )
                        repeat(6) { row ->
                            GlassListItem(
                                headline = "Term Section ${row + 1}",
                                supportingText = "Sample scrollable row inside GlassSheet.",
                                badgeText = if (row == 1) "Due" else "OK",
                                isWarning = row == 1
                            )
                        }
                    }
                }

                if (showDialog) {
                    GlassDialog(
                        onDismissRequest = { showDialog = false },
                        title = "Confirm Action",
                        subtitle = "Scrollable glass dialog with FlowRow action wrapping",
                        icon = Icons.Default.WarningAmber,
                        confirmLabel = "Confirm",
                        dismissLabel = "Cancel",
                        onConfirm = { showDialog = false },
                        onDismiss = { showDialog = false }
                    ) {
                        Text(
                            text = "This dialog automatically scrolls long messages and wraps actions on narrow 320dp screens or 1.3x font scaling.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}
