package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Core Neutral Academic Palette (Navy & Light Blue) — preserved for existing screens
val NavyPrimary = Color(0xFF1F3A5F)
val NavyDark = Color(0xFF132640)
val NavyContainer = Color(0xFFDCE5F0)
val OnNavyContainer = Color(0xFF102542)

val SteelSecondary = Color(0xFF2E6F9E)
val SteelContainer = Color(0xFFD5E9F8)
val OnSteelContainer = Color(0xFF0A2E47)

val AcademicGold = Color(0xFFC58B16)
val AcademicGoldContainer = Color(0xFFFFF0CC)
val OnAcademicGoldContainer = Color(0xFF3E2900)

val SurfaceCanvas = Color(0xFFF5F8FC)
val SurfaceCard = Color(0xFFFFFFFF)
val SurfaceMist = Color(0xFFEEF2F7)
val OutlineSlate = Color(0xFF9FB0C3)
val OutlineSubtle = Color(0xFFD5DFEB)

val TextPrimary = Color(0xFF111D2D)
val TextSecondary = Color(0xFF4B5D73)

// Semantic Status Colors
val PassGreen = Color(0xFF1B7A43)
val PassGreenContainer = Color(0xFFDDF5E6)
val FailRed = Color(0xFFC62828)
val FailRedContainer = Color(0xFFFFE5E5)
val AbsentAmber = Color(0xFFB26A00)
val AbsentAmberContainer = Color(0xFFFFF1D6)

// Dark Theme Palette
val DarkPrimary = Color(0xFFA8C7FA)
val DarkOnPrimary = Color(0xFF0A2342)
val DarkPrimaryContainer = Color(0xFF1F3A5F)
val DarkOnPrimaryContainer = Color(0xFFDCE5F0)
val DarkSecondary = Color(0xFF8CC5EC)
val DarkBackground = Color(0xFF0F1722)
val DarkSurface = Color(0xFF162130)
val DarkSurfaceVariant = Color(0xFF223044)
val DarkOutline = Color(0xFF4C6078)

// =============================================================================
// Task D1: Liquid Glass & Calm Deep Blue / Teal Design System Tokens
// All foreground colors verified for >= 4.5:1 contrast over glass & solid tones
// =============================================================================

// Light Palette Accents & Surfaces
val GlassBrandDeepBlue = Color(0xFF0E4A6F)
val GlassBrandDeepBlueDark = Color(0xFF09324D)
val GlassBrandBlueContainer = Color(0xFFD6E9F8)
val GlassOnBrandBlueContainer = Color(0xFF092A42)

val GlassBrandTeal = Color(0xFF0D6E75)
val GlassBrandTealContainer = Color(0xFFCFF0F2)
val GlassOnBrandTealContainer = Color(0xFF053539)

val GlassCanvasLight = Color(0xFFF1F6FB)
val GlassSolidSurfaceLight = Color(0xFFFFFFFF)
val GlassSolidSubtleLight = Color(0xFFE8F0F7)
val GlassTextPrimaryLight = Color(0xFF101C2B)
val GlassTextSecondaryLight = Color(0xFF3E5066)
val GlassOutlineLight = Color(0xFF8BA0B8)
val GlassOutlineSubtleLight = Color(0xFFD0DCE9)

// Light Translucent Glass Surfaces (tinted high enough for >= 4.5:1 readability)
val GlassTintSubtleLight = Color(0xCCFFFFFF)      // ~80% translucent white
val GlassTintLight = Color(0xDBFFFFFF)            // ~86% translucent white
val GlassTintElevatedLight = Color(0xEBFFFFFF)    // ~92% translucent white
val GlassBorderLight = Color(0xB8FFFFFF)          // Specular rim
val GlassHairlineBorderLight = Color(0x2E102542)  // Soft slate edge
val GlassHighlightLight = Color(0x66FFFFFF)
val GlassOrbPrimaryLight = Color(0x400D6E75)
val GlassOrbSecondaryLight = Color(0x380E4A6F)

// Dark Palette Accents & Surfaces
val GlassBrandBlueDark = Color(0xFF8ECBF5)
val GlassOnBrandBlueDark = Color(0xFF07253B)
val GlassBrandBlueContainerDark = Color(0xFF173D5C)
val GlassOnBrandBlueContainerDark = Color(0xFFD6E9F8)

val GlassBrandTealDark = Color(0xFF6CD6DC)
val GlassOnBrandTealDark = Color(0xFF043034)
val GlassBrandTealContainerDark = Color(0xFF10494E)
val GlassOnBrandTealContainerDark = Color(0xFFCFF0F2)

val GlassCanvasDark = Color(0xFF0B141E)
val GlassSolidSurfaceDark = Color(0xFF142130)
val GlassSolidSubtleDark = Color(0xFF1C2C3E)
val GlassTextPrimaryDark = Color(0xFFEEF4FA)
val GlassTextSecondaryDark = Color(0xFFB8C7D9)
val GlassOutlineDark = Color(0xFF4F657E)
val GlassOutlineSubtleDark = Color(0xFF283A4F)

// Dark Translucent Glass Surfaces
val GlassTintSubtleDark = Color(0xBF152334)       // ~75% translucent slate
val GlassTintDark = Color(0xCF162536)             // ~81% translucent slate
val GlassTintElevatedDark = Color(0xE618293C)     // ~90% translucent slate
val GlassBorderDark = Color(0x47FFFFFF)
val GlassHairlineBorderDark = Color(0x2E90C2E8)
val GlassHighlightDark = Color(0x24FFFFFF)
val GlassOrbPrimaryDark = Color(0x336CD6DC)
val GlassOrbSecondaryDark = Color(0x338ECBF5)

// Accessible Semantic Status / Money / Dues / Negative Tokens (>= 4.5:1 contrast)
val DueWarningTextLight = Color(0xFF9A3412)
val DueWarningOnSolidLight = Color(0xFFFFFFFF)
val DueWarningContainerLight = Color(0xFFFFEDD5)

val DueWarningTextDark = Color(0xFFFDBA74)
val DueWarningOnSolidDark = Color(0xFF431407)
val DueWarningContainerDark = Color(0xFF472108)

val NegativeAmountTextLight = Color(0xFFB91C1C)
val NegativeAmountOnSolidLight = Color(0xFFFFFFFF)
val NegativeAmountContainerLight = Color(0xFFFEE2E2)

val NegativeAmountTextDark = Color(0xFFFCA5A5)
val NegativeAmountOnSolidDark = Color(0xFF450A0A)
val NegativeAmountContainerDark = Color(0xFF4D1518)

val PositiveAmountTextLight = Color(0xFF146C3B)
val PositiveAmountContainerLight = Color(0xFFDDF5E6)

val PositiveAmountTextDark = Color(0xFF86EFAC)
val PositiveAmountContainerDark = Color(0xFF113A24)
