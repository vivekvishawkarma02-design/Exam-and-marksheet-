package com.example.ui.designsystem

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppTheme
import com.example.ui.theme.GlassStyleConfig

/**
 * Intensity presets controlling glass tint opacity and backdrop blur radius.
 */
enum class GlassIntensity {
    Subtle,
    Regular,
    Prominent
}

val GlassBlurActiveKey = SemanticsPropertyKey<Boolean>("GlassBlurActive")
val GlassSimpleLookKey = SemanticsPropertyKey<Boolean>("GlassSimpleLook")

var SemanticsPropertyReceiver.glassBlurActive by GlassBlurActiveKey
var SemanticsPropertyReceiver.glassSimpleLook by GlassSimpleLookKey

/**
 * Returns true only when glass styling is enabled AND the Android platform supports
 * RenderEffect blur (Android 12 / API 31+).
 */
fun isGlassBlurActive(
    glassStyle: GlassStyleConfig,
    sdkInt: Int = Build.VERSION.SDK_INT
): Boolean = glassStyle.glassEnabled && sdkInt >= Build.VERSION_CODES.S

/**
 * Ambient neutral + deep-blue/teal gradient backdrop designed to sit softly behind glass surfaces.
 * When Simple Look is active, falls back to the plain solid canvas background.
 */
@Composable
fun GlassBackdropCanvas(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AppTheme.colors
    val glass = AppTheme.glass
    val backgroundBrush = if (glass.glassEnabled) {
        Brush.linearGradient(
            colors = listOf(
                colors.backdropOrbPrimary.copy(alpha = 0.28f),
                colors.solidSubtleSurface,
                colors.backdropOrbSecondary.copy(alpha = 0.24f)
            ),
            start = Offset.Zero,
            end = Offset(1200f, 1600f)
        )
    } else {
        Brush.verticalGradient(listOf(colors.solidSubtleSurface, colors.solidSubtleSurface))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        content = content
    )
}

/**
 * Core translucent "liquid glass" container with automatic Simple Look & API < 31 fallbacks:
 * - Android 12+ (API 31+) with Simple Look OFF: blurs a dedicated backdrop layer behind crisp text,
 *   overlays a high-contrast translucent tint and top specular highlight, and draws a thin light rim.
 * - Android 7–11 (API 24–30) with Simple Look OFF: renders the same layout with a calm translucent
 *   tinted surface and thin border (no RenderEffect blur crash or slowdown).
 * - Simple Look ON (or low-RAM default): removes all blur and renders a clean solid surface.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(AppTheme.radii.lg),
    intensity: GlassIntensity = GlassIntensity.Regular,
    tintOverride: Color? = null,
    borderOverride: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AppTheme.colors
    val blurTokens = AppTheme.blur
    val glass = AppTheme.glass
    val blurActive = isGlassBlurActive(glass)

    val blurRadius: Dp = when {
        !blurActive -> 0.dp
        intensity == GlassIntensity.Subtle -> blurTokens.subtle
        intensity == GlassIntensity.Regular -> blurTokens.regular
        else -> blurTokens.prominent
    }

    val surfaceFill = tintOverride ?: if (glass.glassEnabled) {
        when (intensity) {
            GlassIntensity.Subtle -> colors.glassTintSubtle
            GlassIntensity.Regular -> colors.glassTint
            GlassIntensity.Prominent -> colors.glassTintElevated
        }
    } else {
        when (intensity) {
            GlassIntensity.Subtle -> colors.solidSubtleSurface
            GlassIntensity.Regular -> colors.solidSurface
            GlassIntensity.Prominent -> colors.solidSurface
        }
    }

    val borderStroke = borderOverride ?: if (glass.glassEnabled) {
        BorderStroke(1.dp, colors.glassBorder)
    } else {
        BorderStroke(1.dp, colors.solidBorder)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .semantics {
                glassBlurActive = blurActive
                glassSimpleLook = glass.simpleLook
            },
        propagateMinConstraints = true
    ) {
        // Dedicated backdrop layer so blur NEVER blurs foreground typography or icons.
        val backdropModifier = Modifier
            .matchParentSize()
            .then(
                if (blurActive && blurRadius > 0.dp) {
                    Modifier.blur(
                        radius = blurRadius,
                        edgeTreatment = BlurredEdgeTreatment.Rectangle
                    )
                } else {
                    Modifier
                }
            )
            .background(
                if (glass.glassEnabled) {
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.backdropOrbPrimary.copy(alpha = 0.22f),
                            colors.backdropOrbSecondary.copy(alpha = 0.14f)
                        )
                    )
                } else {
                    Brush.verticalGradient(listOf(surfaceFill, surfaceFill))
                }
            )

        Box(modifier = backdropModifier)

        // Crisp translucent tint + specular sheen + thin border
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(surfaceFill)
                .then(
                    if (glass.glassEnabled) {
                        Modifier.background(
                            Brush.verticalGradient(
                                colors = listOf(colors.glassHighlight, Color.Transparent)
                            )
                        )
                    } else {
                        Modifier
                    }
                )
                .border(borderStroke, shape)
                .then(
                    if (glass.glassEnabled && borderOverride == null) {
                        Modifier.border(0.5.dp, colors.glassSubtleBorder, shape)
                    } else {
                        Modifier
                    }
                )
        )

        CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
            Box(content = content)
        }
    }
}
