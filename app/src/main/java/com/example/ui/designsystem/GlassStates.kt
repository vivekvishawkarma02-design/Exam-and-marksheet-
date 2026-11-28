package com.example.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppTheme
import com.example.ui.theme.MotionConfig

/**
 * Calm empty-state panel built on [GlassCard] with generous padding and an optional 48dp action.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val spacing = AppTheme.spacing
    val colors = AppTheme.colors

    GlassCard(
        modifier = modifier.testTag("design_empty_state"),
        intensity = GlassIntensity.Subtle
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Surface(
                shape = CircleShape,
                color = colors.secondaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.secondaryAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                GlassButton(
                    text = actionLabel,
                    onClick = onAction,
                    variant = GlassButtonVariant.Secondary,
                    modifier = Modifier.testTag("empty_state_action")
                )
            }
        }
    }
}

/**
 * High-contrast error state panel with accessible negative status tinting and a >= 48dp retry button.
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ErrorOutline,
    retryLabel: String = "Retry",
    onRetry: (() -> Unit)? = null
) {
    val spacing = AppTheme.spacing
    val colors = AppTheme.colors

    GlassCard(
        modifier = modifier.testTag("design_error_state"),
        intensity = GlassIntensity.Regular,
        borderOverride = BorderStroke(1.dp, colors.negativeAmount.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Surface(
                shape = CircleShape,
                color = colors.negativeAmountContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.negativeAmount,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.negativeAmount,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                GlassButton(
                    text = retryLabel,
                    onClick = onRetry,
                    variant = GlassButtonVariant.Secondary,
                    isDestructive = true,
                    leadingIcon = Icons.Default.Refresh,
                    modifier = Modifier.testTag("error_state_retry")
                )
            }
        }
    }
}

/**
 * Skeleton loading placeholder cards.
 * Uses a gentle shimmer pulse when animations are enabled, and automatically switches
 * to a static calm surface when system reduce-motion is enabled.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    itemCount: Int = 3,
    showHeaderSkeleton: Boolean = true
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val colors = AppTheme.colors
    val motion = AppTheme.motion

    val shimmerAlpha = if (motion.shouldAnimateShimmer) {
        val transition = rememberInfiniteTransition(label = "loading_skeleton_shimmer")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.72f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = MotionConfig.SHIMMER_CYCLE_MS,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "loading_skeleton_alpha"
        )
        animated
    } else {
        0.48f
    }

    val boneColor = colors.solidSubtleSurface.copy(alpha = shimmerAlpha.coerceIn(0.25f, 0.85f))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("design_loading_state"),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        if (showHeaderSkeleton) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(radii.xs))
                    .background(boneColor)
            )
        }

        repeat(itemCount.coerceIn(1, 6)) {
            GlassCard(intensity = GlassIntensity.Subtle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(boneColor)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(radii.xs))
                                .background(boneColor)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.48f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(radii.xs))
                                .background(boneColor)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.xs))
            }
        }
    }
}
