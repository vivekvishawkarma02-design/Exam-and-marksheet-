package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = GlassBrandDeepBlue,
    onPrimary = Color.White,
    primaryContainer = GlassBrandBlueContainer,
    onPrimaryContainer = GlassOnBrandBlueContainer,
    secondary = GlassBrandTeal,
    onSecondary = Color.White,
    secondaryContainer = GlassBrandTealContainer,
    onSecondaryContainer = GlassOnBrandTealContainer,
    tertiary = AcademicGold,
    onTertiary = Color.White,
    tertiaryContainer = AcademicGoldContainer,
    onTertiaryContainer = OnAcademicGoldContainer,
    background = GlassCanvasLight,
    onBackground = GlassTextPrimaryLight,
    surface = GlassSolidSurfaceLight,
    onSurface = GlassTextPrimaryLight,
    surfaceVariant = GlassSolidSubtleLight,
    onSurfaceVariant = GlassTextSecondaryLight,
    outline = GlassOutlineLight,
    outlineVariant = GlassOutlineSubtleLight,
    error = NegativeAmountTextLight,
    onError = NegativeAmountOnSolidLight,
    errorContainer = NegativeAmountContainerLight,
    onErrorContainer = NegativeAmountTextLight
)

private val DarkColorScheme = darkColorScheme(
    primary = GlassBrandBlueDark,
    onPrimary = GlassOnBrandBlueDark,
    primaryContainer = GlassBrandBlueContainerDark,
    onPrimaryContainer = GlassOnBrandBlueContainerDark,
    secondary = GlassBrandTealDark,
    onSecondary = GlassOnBrandTealDark,
    secondaryContainer = GlassBrandTealContainerDark,
    onSecondaryContainer = GlassOnBrandTealContainerDark,
    tertiary = Color(0xFFEBC066),
    onTertiary = Color(0xFF3E2900),
    tertiaryContainer = Color(0xFF593D00),
    onTertiaryContainer = Color(0xFFFFF0CC),
    background = GlassCanvasDark,
    onBackground = GlassTextPrimaryDark,
    surface = GlassSolidSurfaceDark,
    onSurface = GlassTextPrimaryDark,
    surfaceVariant = GlassSolidSubtleDark,
    onSurfaceVariant = GlassTextSecondaryDark,
    outline = GlassOutlineDark,
    outlineVariant = GlassOutlineSubtleDark,
    error = NegativeAmountTextDark,
    onError = NegativeAmountOnSolidDark,
    errorContainer = NegativeAmountContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Root theme wrapping Material 3 color scheme, typography, shapes, and Task D1 tokens
 * (spacing, radii, blur, semantic colors, glass mode, and reduce-motion settings).
 */
@Composable
fun SchoolAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode? = null,
    simpleLook: Boolean? = null,
    reduceMotion: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val resolvedDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
        null -> darkTheme
    }

    val capability = remember(context) { DeviceCapabilityDetector.inspectDevice(context) }
    val resolvedSimpleLook = simpleLook ?: capability.defaultSimpleLook
    val glassStyle = remember(resolvedSimpleLook, capability) {
        GlassStyleConfig(
            simpleLook = resolvedSimpleLook,
            blurSupportedByDevice = capability.blurSupportedByDevice,
            isLowRamDevice = capability.isLowRamDevice
        )
    }

    val semanticColors = remember(resolvedDark) {
        if (resolvedDark) darkAppSemanticColors() else lightAppSemanticColors()
    }
    val motionConfig = rememberMotionConfig(overrideReduceMotion = reduceMotion)
    val colorScheme = if (resolvedDark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(),
        LocalAppRadii provides AppRadii(),
        LocalAppElevation provides AppElevation(),
        LocalAppBlurTokens provides AppBlurTokens(),
        LocalAppSemanticColors provides semanticColors,
        LocalGlassStyle provides glassStyle,
        LocalMotionConfig provides motionConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
