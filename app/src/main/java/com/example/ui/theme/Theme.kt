package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = OnNavyContainer,
    secondary = SteelSecondary,
    onSecondary = Color.White,
    secondaryContainer = SteelContainer,
    onSecondaryContainer = OnSteelContainer,
    tertiary = AcademicGold,
    onTertiary = Color.White,
    tertiaryContainer = AcademicGoldContainer,
    onTertiaryContainer = OnAcademicGoldContainer,
    background = SurfaceCanvas,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMist,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSlate,
    outlineVariant = OutlineSubtle,
    error = FailRed,
    onError = Color.White,
    errorContainer = FailRedContainer,
    onErrorContainer = FailRed
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnPrimary,
    secondaryContainer = Color(0xFF1A4361),
    onSecondaryContainer = Color(0xFFD5E9F8),
    tertiary = Color(0xFFEBC066),
    onTertiary = Color(0xFF3E2900),
    tertiaryContainer = Color(0xFF593D00),
    onTertiaryContainer = Color(0xFFFFF0CC),
    background = DarkBackground,
    onBackground = Color(0xFFEAF0F7),
    surface = DarkSurface,
    onSurface = Color(0xFFEAF0F7),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB3C2D4),
    outline = DarkOutline,
    outlineVariant = Color(0xFF2D3E54),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5F0000),
    errorContainer = Color(0xFF5D1919),
    onErrorContainer = Color(0xFFFFDAD6)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun CpiReportCardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
