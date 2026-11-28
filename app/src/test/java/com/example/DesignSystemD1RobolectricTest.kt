package com.example

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AppearanceSettingsCard
import com.example.ui.DebugDesignPreviewScreen
import com.example.ui.LegalDocType
import com.example.ui.SettingsAboutAndLegalCard
import com.example.ui.designsystem.EmptyState
import com.example.ui.designsystem.ErrorState
import com.example.ui.designsystem.GlassBlurActiveKey
import com.example.ui.designsystem.GlassBottomBar
import com.example.ui.designsystem.GlassBottomBarItem
import com.example.ui.designsystem.GlassButton
import com.example.ui.designsystem.GlassButtonVariant
import com.example.ui.designsystem.GlassCard
import com.example.ui.designsystem.GlassChip
import com.example.ui.designsystem.GlassDialog
import com.example.ui.designsystem.GlassListItem
import com.example.ui.designsystem.GlassSheet
import com.example.ui.designsystem.GlassSimpleLookKey
import com.example.ui.designsystem.GlassSurface
import com.example.ui.designsystem.GlassTextField
import com.example.ui.designsystem.GlassTopBar
import com.example.ui.designsystem.LoadingState
import com.example.ui.designsystem.SectionHeader
import com.example.ui.designsystem.isGlassBlurActive
import com.example.ui.theme.AppearancePrefsStore
import com.example.ui.theme.DeviceCapabilityDetector
import com.example.ui.theme.GlassStyleConfig
import com.example.ui.theme.MotionConfig
import com.example.ui.theme.MotionPreferences
import com.example.ui.theme.SchoolAppTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.darkAppSemanticColors
import com.example.ui.theme.lightAppSemanticColors
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h720dp-xhdpi")
class DesignSystemD1RobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val opaqueBg = background.copy(alpha = 1f)
        val effectiveFg = if (foreground.alpha < 1f) foreground.compositeOver(opaqueBg) else foreground
        val l1 = effectiveFg.luminance().toDouble()
        val l2 = opaqueBg.luminance().toDouble()
        val brighter = max(l1, l2)
        val darker = min(l1, l2)
        return (brighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun lightAndDarkGlassTextContrastMeetsAtLeast4_5To1() {
        val light = lightAppSemanticColors()
        val lightGlassComposite = light.glassTint.compositeOver(light.solidSubtleSurface)
        assertTrue(contrastRatio(light.textPrimary, lightGlassComposite) >= 4.5)
        assertTrue(contrastRatio(light.textSecondary, lightGlassComposite) >= 4.5)
        assertTrue(contrastRatio(light.warningMoneyDue, lightGlassComposite) >= 4.5)
        assertTrue(contrastRatio(light.negativeAmount, lightGlassComposite) >= 4.5)
        assertTrue(contrastRatio(light.warningMoneyDue, light.warningMoneyDueContainer) >= 4.5)
        assertTrue(contrastRatio(light.negativeAmount, light.negativeAmountContainer) >= 4.5)

        val dark = darkAppSemanticColors()
        val darkGlassComposite = dark.glassTint.compositeOver(dark.solidSubtleSurface)
        assertTrue(contrastRatio(dark.textPrimary, darkGlassComposite) >= 4.5)
        assertTrue(contrastRatio(dark.textSecondary, darkGlassComposite) >= 4.5)
        assertTrue(contrastRatio(dark.warningMoneyDue, darkGlassComposite) >= 4.5)
        assertTrue(contrastRatio(dark.negativeAmount, darkGlassComposite) >= 4.5)
        assertTrue(contrastRatio(dark.warningMoneyDue, dark.warningMoneyDueContainer) >= 4.5)
        assertTrue(contrastRatio(dark.negativeAmount, dark.negativeAmountContainer) >= 4.5)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun allCoreComponents_renderCleanlyAt320dp_fontScale1_3_noClipOrTinyTargets() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                SchoolAppTheme(simpleLook = false) {
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassTopBar(
                            title = "Term Assessment & Fee Summary",
                            subtitle = "Academic Session 2026-27",
                            onNavigationClick = {},
                            applyStatusBarInsets = false,
                            actions = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = "Alerts")
                                }
                            },
                            modifier = Modifier.testTag("test_glass_top_bar")
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionHeader(
                                title = "Monthly Attendance Overview",
                                subtitle = "Class 6-A summary",
                                leadingIcon = Icons.Default.School,
                                actionLabel = "View All",
                                onAction = {},
                                modifier = Modifier.testTag("test_section_header")
                            )

                            GlassCard(modifier = Modifier.testTag("test_glass_card")) {
                                Text("Pending Fee Dues: ₹1,250", modifier = Modifier.testTag("test_card_text"))
                            }

                            GlassListItem(
                                headline = "Mathematics Half-Yearly Marks",
                                supportingText = "All 38 learners graded",
                                badgeText = "₹1,250 Due",
                                isWarning = true,
                                leadingIcon = Icons.Default.AccountBalanceWallet,
                                onClick = {},
                                modifier = Modifier.testTag("test_glass_list_item")
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassButton(
                                    text = "Save Marks",
                                    onClick = {},
                                    variant = GlassButtonVariant.Primary,
                                    leadingIcon = Icons.Default.Assessment,
                                    modifier = Modifier.testTag("test_btn_primary")
                                )
                                GlassButton(
                                    text = "Export PDF",
                                    onClick = {},
                                    variant = GlassButtonVariant.Secondary,
                                    modifier = Modifier.testTag("test_btn_secondary")
                                )
                                GlassButton(
                                    text = "Skip",
                                    onClick = {},
                                    variant = GlassButtonVariant.Text,
                                    modifier = Modifier.testTag("test_btn_text")
                                )
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GlassChip(
                                    label = "All Classes",
                                    selected = true,
                                    onClick = {},
                                    modifier = Modifier.testTag("test_chip_normal")
                                )
                                GlassChip(
                                    label = "Dues Pending",
                                    selected = false,
                                    isWarning = true,
                                    leadingIcon = Icons.Default.WarningAmber,
                                    onClick = {},
                                    modifier = Modifier.testTag("test_chip_warning")
                                )
                            }

                            GlassTextField(
                                value = "Class 6-A",
                                onValueChange = {},
                                label = "Class & Section",
                                supportingText = "Active section",
                                modifier = Modifier.testTag("test_text_field")
                            )

                            EmptyState(
                                title = "No Pending Requests",
                                message = "New join requests will appear here.",
                                actionLabel = "Refresh",
                                onAction = {},
                                modifier = Modifier.testTag("test_empty_state")
                            )

                            ErrorState(
                                title = "Offline Mode",
                                message = "Changes will sync when internet returns.",
                                retryLabel = "Retry Now",
                                onRetry = {},
                                modifier = Modifier.testTag("test_error_state")
                            )

                            LoadingState(
                                itemCount = 2,
                                modifier = Modifier.testTag("test_loading_state")
                            )
                        }

                        GlassBottomBar(
                            items = listOf(
                                GlassBottomBarItem("home", "Home", Icons.Default.Home, testTag = "test_nav_home"),
                                GlassBottomBarItem("students", "Students", Icons.Default.Groups, testTag = "test_nav_students"),
                                GlassBottomBarItem("marks", "Marks", Icons.Default.EditNote, testTag = "test_nav_marks"),
                                GlassBottomBarItem("reports", "Reports", Icons.Default.Description, testTag = "test_nav_reports")
                            ),
                            selectedId = "home",
                            onSelect = {},
                            applyNavigationBarInsets = false,
                            modifier = Modifier.testTag("test_glass_bottom_bar")
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val topBarBounds = composeRule.onNodeWithTag("test_glass_top_bar").assertIsDisplayed().getUnclippedBoundsInRoot()
        val topBarTitle = composeRule.onNodeWithTag("glass_top_bar_title").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(
            "TopBar title must wrap normally (topBar=${topBarBounds.width}, w=${topBarTitle.width}, h=${topBarTitle.height})",
            topBarTitle.width > topBarTitle.height
        )
        assertTrue("TopBar title right <= 320dp", topBarTitle.right <= 320.5.dp)

        val navBtn = composeRule.onNodeWithTag("glass_top_bar_nav_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("TopBar nav button >= 48dp", navBtn.height >= 47.5.dp && navBtn.width >= 47.5.dp)

        val secTitle = composeRule.onNodeWithTag("section_header_title").assertIsDisplayed().getUnclippedBoundsInRoot()
        val secAction = composeRule.onNodeWithTag("section_header_action").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(
            "SectionHeader title not squeezed vertically (w=${secTitle.width}, h=${secTitle.height})",
            secTitle.width > secTitle.height
        )
        assertTrue("SectionHeader action >= 48dp", secAction.height >= 47.5.dp)
        assertTrue("SectionHeader action fits inside 320dp", secAction.right <= 320.5.dp)

        listOf(
            "test_glass_card",
            "test_glass_list_item",
            "test_btn_primary",
            "test_btn_secondary",
            "test_btn_text",
            "test_chip_normal",
            "test_chip_warning",
            "test_text_field",
            "test_empty_state",
            "test_error_state",
            "test_loading_state",
            "test_glass_bottom_bar"
        ).forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag)
                .performScrollTo()
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assertTrue("$tag right (${bounds.right}) <= 320.5dp", bounds.right <= 320.5.dp)
        }

        listOf(
            "test_glass_list_item",
            "test_btn_primary",
            "test_btn_secondary",
            "test_btn_text",
            "test_chip_normal",
            "test_chip_warning",
            "empty_state_action",
            "error_state_retry",
            "test_nav_home"
        ).forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag)
                .performScrollTo()
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            assertTrue("$tag touch height >= 48dp (was ${bounds.height})", bounds.height >= 47.5.dp)
        }
    }

    @Test
    fun glassDialog_320dp_font1_3_scrollsAndKeepsButtonsReachable() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                SchoolAppTheme(simpleLook = false) {
                    GlassDialog(
                        onDismissRequest = {},
                        title = "Confirm Session Archive Operation",
                        subtitle = "This action updates the active workspace",
                        icon = Icons.Default.WarningAmber,
                        confirmLabel = "Confirm Archive",
                        dismissLabel = "Cancel",
                        onConfirm = {},
                        onDismiss = {}
                    ) {
                        repeat(12) { idx ->
                            Text("Detail line ${idx + 1}: All student marks and attendance logs remain safely backed up.")
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val titleBounds = composeRule.onNodeWithTag("glass_dialog_title").assertIsDisplayed().getUnclippedBoundsInRoot()
        val confirmBounds = composeRule.onNodeWithTag("glass_dialog_confirm").assertIsDisplayed().getUnclippedBoundsInRoot()
        val dismissBounds = composeRule.onNodeWithTag("glass_dialog_dismiss").assertIsDisplayed().getUnclippedBoundsInRoot()

        assertTrue("Dialog title fits <= 320dp", titleBounds.right <= 320.5.dp)
        assertTrue("Confirm button >= 48dp & inside 320dp", confirmBounds.height >= 47.5.dp && confirmBounds.right <= 320.5.dp)
        assertTrue("Dismiss button >= 48dp & inside 320dp", dismissBounds.height >= 47.5.dp && dismissBounds.right <= 320.5.dp)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun glassSheet_320dp_font1_3_rendersAndScrollsWithoutOverflow() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                SchoolAppTheme(simpleLook = false) {
                    GlassSheet(
                        onDismissRequest = {},
                        title = "Filter Fee Defaulters",
                        subtitle = "Select class & balance threshold",
                        footer = {
                            GlassButton(
                                text = "Apply Filter",
                                onClick = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sheet_apply_btn")
                            )
                        }
                    ) {
                        repeat(10) { idx ->
                            GlassListItem(
                                headline = "Class ${idx + 1} Summary",
                                badgeText = "₹${(idx + 1) * 500}",
                                isWarning = idx % 2 == 0
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val titleBounds = composeRule.onNodeWithTag("glass_sheet_title").assertIsDisplayed().getUnclippedBoundsInRoot()
        val closeBounds = composeRule.onNodeWithTag("glass_sheet_close").assertIsDisplayed().getUnclippedBoundsInRoot()
        val applyBounds = composeRule.onNodeWithTag("sheet_apply_btn").assertIsDisplayed().getUnclippedBoundsInRoot()

        assertTrue("Sheet title fits <= 320dp", titleBounds.right <= 320.5.dp)
        assertTrue("Sheet close button >= 48dp", closeBounds.height >= 47.5.dp && closeBounds.width >= 47.5.dp)
        assertTrue("Sheet footer button >= 48dp & fits <= 320dp", applyBounds.height >= 47.5.dp && applyBounds.right <= 320.5.dp)
    }

    @Test
    fun simpleLook_removesBlurAndSwitchesToSolidSurface() {
        assertTrue(isGlassBlurActive(GlassStyleConfig(simpleLook = false, blurSupportedByDevice = true), sdkInt = Build.VERSION_CODES.S))
        assertFalse(isGlassBlurActive(GlassStyleConfig(simpleLook = true, blurSupportedByDevice = true), sdkInt = Build.VERSION_CODES.S))
        assertFalse(isGlassBlurActive(GlassStyleConfig(simpleLook = false, blurSupportedByDevice = false), sdkInt = Build.VERSION_CODES.P))

        var simpleLookState by mutableStateOf(false)
        composeRule.setContent {
            SchoolAppTheme(simpleLook = simpleLookState) {
                Box(modifier = Modifier.width(300.dp)) {
                    GlassSurface(modifier = Modifier.testTag("blur_check_surface")) {
                        Text("Surface Content")
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("blur_check_surface").assert(
            SemanticsMatcher.expectValue(GlassBlurActiveKey, true)
        ).assert(
            SemanticsMatcher.expectValue(GlassSimpleLookKey, false)
        )

        simpleLookState = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("blur_check_surface").assert(
            SemanticsMatcher.expectValue(GlassBlurActiveKey, false)
        ).assert(
            SemanticsMatcher.expectValue(GlassSimpleLookKey, true)
        )
    }

    @Test
    fun deviceCapabilityAndAppearancePrefsStore_persistCorrectly() {
        assertTrue(DeviceCapabilityDetector.shouldDefaultToSimpleLook(sdkInt = 28, isLowRamDevice = false))
        assertTrue(DeviceCapabilityDetector.shouldDefaultToSimpleLook(sdkInt = 34, isLowRamDevice = true))
        assertTrue(DeviceCapabilityDetector.shouldDefaultToSimpleLook(sdkInt = 34, isLowRamDevice = false, totalMemoryBytes = 1_500_000_000L))
        assertFalse(DeviceCapabilityDetector.shouldDefaultToSimpleLook(sdkInt = 34, isLowRamDevice = false, totalMemoryBytes = 6_000_000_000L))

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store1 = AppearancePrefsStore(context)
        store1.setThemeMode(ThemeMode.DARK)
        store1.setSimpleLook(true)

        val store2 = AppearancePrefsStore(context)
        assertEquals(ThemeMode.DARK, store2.settings.value.themeMode)
        assertTrue(store2.settings.value.simpleLook)

        composeRule.setContent {
            val s = store2.settings.value
            SchoolAppTheme(themeMode = s.themeMode, simpleLook = s.simpleLook) {
                AppearanceSettingsCard(
                    settings = s,
                    onThemeModeChange = store2::setThemeMode,
                    onSimpleLookChange = store2::setSimpleLook
                )
            }
        }
        composeRule.onNodeWithTag("settings_appearance_card").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance_theme_light").performClick()
        composeRule.onNodeWithTag("appearance_simple_look_switch").performClick()
        assertEquals(ThemeMode.LIGHT, store2.settings.value.themeMode)
        assertFalse(store2.settings.value.simpleLook)
    }

    @Test
    fun reduceMotionHelper_returnsZeroDurationsWhenEnabled() {
        assertTrue(MotionPreferences.isReduceMotionActive(animatorDurationScale = 0f, animatorsEnabled = true))
        assertTrue(MotionPreferences.isReduceMotionActive(animatorDurationScale = 1f, animatorsEnabled = false))
        assertFalse(MotionPreferences.isReduceMotionActive(animatorDurationScale = 1f, animatorsEnabled = true))

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertTrue(MotionPreferences.isReduceMotionEnabled(context))
        val reducedMotion = MotionPreferences.resolveMotionConfig(context)
        assertEquals(0, reducedMotion.screenTransitionDurationMs())
        assertEquals(0, reducedMotion.pressFeedbackDurationMs())
        assertEquals(0, reducedMotion.listItemEnterDurationMs())
        assertFalse(reducedMotion.shouldAnimateShimmer)

        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        assertFalse(MotionPreferences.isReduceMotionEnabled(context))
        val normalMotion = MotionPreferences.resolveMotionConfig(context)
        assertEquals(MotionConfig.SCREEN_TRANSITION_MS, normalMotion.screenTransitionDurationMs())
        assertEquals(MotionConfig.PRESS_FEEDBACK_MS, normalMotion.pressFeedbackDurationMs())
        assertEquals(MotionConfig.LIST_ITEM_ENTER_MS, normalMotion.listItemEnterDurationMs())
        assertTrue(normalMotion.shouldAnimateShimmer)
    }

    @Test
    fun settingsAboutDebugEntryAndDesignPreviewScreen_workInDebug() {
        var openedPreview = false
        composeRule.setContent {
            SchoolAppTheme {
                SettingsAboutAndLegalCard(
                    versionName = "1.0.0",
                    versionCode = 1,
                    onOpenLegalDoc = { _: LegalDocType -> },
                    onOpenDesignPreview = { openedPreview = true }
                )
            }
        }
        composeRule.onNodeWithTag("settings_about_design_preview_button").assertIsDisplayed().performClick()
        assertTrue(openedPreview)
    }

    @Test
    fun debugDesignPreviewScreen_rendersAllSectionsAt320dp() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                SchoolAppTheme {
                    DebugDesignPreviewScreen(onBack = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("debug_design_preview_list").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_toggle_dark").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("preview_toggle_simple_look").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Term 1 Fee Balance").assertIsDisplayed()
    }
}
