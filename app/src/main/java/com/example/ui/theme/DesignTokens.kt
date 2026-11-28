package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Consistent spacing scale on the 4dp grid: 4 / 8 / 12 / 16 / 24 dp.
 */
@Immutable
data class AppSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp
)

/**
 * Shared corner radii tokens across cards, controls, chips, and sheets.
 */
@Immutable
data class AppRadii(
    val xs: Dp = 6.dp,
    val sm: Dp = 10.dp,
    val md: Dp = 14.dp,
    val lg: Dp = 18.dp,
    val xl: Dp = 24.dp,
    val pill: Dp = 999.dp
)

/**
 * Elevation scale for solid fallbacks and subtle layered depth.
 */
@Immutable
data class AppElevation(
    val none: Dp = 0.dp,
    val subtle: Dp = 1.dp,
    val card: Dp = 3.dp,
    val bar: Dp = 4.dp,
    val overlay: Dp = 8.dp
)

/**
 * Blur radii applied to glass backdrop layers on Android 12+ (API 31+) when Simple Look is off.
 */
@Immutable
data class AppBlurTokens(
    val none: Dp = 0.dp,
    val subtle: Dp = 12.dp,
    val regular: Dp = 18.dp,
    val prominent: Dp = 24.dp,
    val windowBlurRadiusPx: Int = 36
)

/**
 * Semantic and surface tokens tuned for >= 4.5:1 text contrast on both glass and solid surfaces.
 */
@Immutable
data class AppSemanticColors(
    val isDark: Boolean,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val warningMoneyDue: Color,
    val onWarningMoneyDue: Color,
    val warningMoneyDueContainer: Color,
    val negativeAmount: Color,
    val onNegativeAmount: Color,
    val negativeAmountContainer: Color,
    val positiveAmount: Color,
    val positiveAmountContainer: Color,
    val glassTintSubtle: Color,
    val glassTint: Color,
    val glassTintElevated: Color,
    val glassBorder: Color,
    val glassSubtleBorder: Color,
    val glassHighlight: Color,
    val solidSurface: Color,
    val solidSubtleSurface: Color,
    val solidBorder: Color,
    val backdropOrbPrimary: Color,
    val backdropOrbSecondary: Color
)

/**
 * Resolved glass rendering configuration for the current device & Appearance preference.
 */
@Immutable
data class GlassStyleConfig(
    val simpleLook: Boolean = false,
    val blurSupportedByDevice: Boolean = true,
    val isLowRamDevice: Boolean = false
) {
    /** True when translucent liquid-glass styling is active (Simple Look is off). */
    val glassEnabled: Boolean get() = !simpleLook

    /** True only when real RenderEffect/Modifier.blur should be applied on API 31+ with Simple Look off. */
    val useBackdropBlur: Boolean get() = glassEnabled && blurSupportedByDevice
}

fun lightAppSemanticColors(): AppSemanticColors = AppSemanticColors(
    isDark = false,
    primaryAccent = GlassBrandDeepBlue,
    secondaryAccent = GlassBrandTeal,
    secondaryContainer = GlassBrandTealContainer,
    onSecondaryContainer = GlassOnBrandTealContainer,
    textPrimary = GlassTextPrimaryLight,
    textSecondary = GlassTextSecondaryLight,
    warningMoneyDue = DueWarningTextLight,
    onWarningMoneyDue = DueWarningOnSolidLight,
    warningMoneyDueContainer = DueWarningContainerLight,
    negativeAmount = NegativeAmountTextLight,
    onNegativeAmount = NegativeAmountOnSolidLight,
    negativeAmountContainer = NegativeAmountContainerLight,
    positiveAmount = PositiveAmountTextLight,
    positiveAmountContainer = PositiveAmountContainerLight,
    glassTintSubtle = GlassTintSubtleLight,
    glassTint = GlassTintLight,
    glassTintElevated = GlassTintElevatedLight,
    glassBorder = GlassBorderLight,
    glassSubtleBorder = GlassHairlineBorderLight,
    glassHighlight = GlassHighlightLight,
    solidSurface = GlassSolidSurfaceLight,
    solidSubtleSurface = GlassSolidSubtleLight,
    solidBorder = GlassOutlineSubtleLight,
    backdropOrbPrimary = GlassOrbPrimaryLight,
    backdropOrbSecondary = GlassOrbSecondaryLight
)

fun darkAppSemanticColors(): AppSemanticColors = AppSemanticColors(
    isDark = true,
    primaryAccent = GlassBrandBlueDark,
    secondaryAccent = GlassBrandTealDark,
    secondaryContainer = GlassBrandTealContainerDark,
    onSecondaryContainer = GlassOnBrandTealContainerDark,
    textPrimary = GlassTextPrimaryDark,
    textSecondary = GlassTextSecondaryDark,
    warningMoneyDue = DueWarningTextDark,
    onWarningMoneyDue = DueWarningOnSolidDark,
    warningMoneyDueContainer = DueWarningContainerDark,
    negativeAmount = NegativeAmountTextDark,
    onNegativeAmount = NegativeAmountOnSolidDark,
    negativeAmountContainer = NegativeAmountContainerDark,
    positiveAmount = PositiveAmountTextDark,
    positiveAmountContainer = PositiveAmountContainerDark,
    glassTintSubtle = GlassTintSubtleDark,
    glassTint = GlassTintDark,
    glassTintElevated = GlassTintElevatedDark,
    glassBorder = GlassBorderDark,
    glassSubtleBorder = GlassHairlineBorderDark,
    glassHighlight = GlassHighlightDark,
    solidSurface = GlassSolidSurfaceDark,
    solidSubtleSurface = GlassSolidSubtleDark,
    solidBorder = GlassOutlineSubtleDark,
    backdropOrbPrimary = GlassOrbPrimaryDark,
    backdropOrbSecondary = GlassOrbSecondaryDark
)

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val LocalAppRadii = staticCompositionLocalOf { AppRadii() }
val LocalAppElevation = staticCompositionLocalOf { AppElevation() }
val LocalAppBlurTokens = staticCompositionLocalOf { AppBlurTokens() }
val LocalAppSemanticColors = staticCompositionLocalOf { lightAppSemanticColors() }
val LocalGlassStyle = staticCompositionLocalOf { GlassStyleConfig() }

/**
 * Convenience accessor for all Task D1 design tokens inside any Composable.
 */
object AppTheme {
    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSpacing.current

    val radii: AppRadii
        @Composable
        @ReadOnlyComposable
        get() = LocalAppRadii.current

    val elevation: AppElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalAppElevation.current

    val blur: AppBlurTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalAppBlurTokens.current

    val colors: AppSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSemanticColors.current

    val glass: GlassStyleConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassStyle.current

    val motion: MotionConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalMotionConfig.current
}
