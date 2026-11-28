package com.example.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppTheme
import com.example.ui.theme.calmPressFeedback

/**
 * Primary liquid-glass card container.
 * Uses [GlassSurface] for API 31+ backdrop blur, API 24-30 translucent fallback,
 * and Simple Look solid surface switching.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    intensity: GlassIntensity = GlassIntensity.Regular,
    shape: Shape = RoundedCornerShape(AppTheme.radii.lg),
    contentPadding: PaddingValues = PaddingValues(AppTheme.spacing.lg),
    tintOverride: Color? = null,
    borderOverride: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val interactiveModifier = if (onClick != null) {
        Modifier
            .heightIn(min = 48.dp)
            .calmPressFeedback(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
    } else {
        Modifier
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .then(interactiveModifier),
        shape = shape,
        intensity = intensity,
        tintOverride = tintOverride,
        borderOverride = borderOverride
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            content = content
        )
    }
}

/**
 * List row built with generous spacing, >= 48dp minimum touch height, accessible warning
 * money/dues styling, and safe title wrapping that never collapses into single-character columns.
 */
@Composable
fun GlassListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    overlineText: String? = null,
    badgeText: String? = null,
    isWarning: Boolean = false,
    leadingIcon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val spacing = AppTheme.spacing
    val colors = AppTheme.colors
    val radii = AppTheme.radii
    val interactionSource = remember { MutableInteractionSource() }

    val clickModifier = if (onClick != null) {
        Modifier
            .calmPressFeedback(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
    } else {
        Modifier
    }

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(clickModifier),
        shape = RoundedCornerShape(radii.md),
        intensity = GlassIntensity.Subtle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = spacing.md, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            if (leadingContent != null) {
                Box(modifier = Modifier.widthIn(max = 48.dp), contentAlignment = Alignment.Center) {
                    leadingContent()
                }
            } else if (leadingIcon != null) {
                Surface(
                    shape = RoundedCornerShape(radii.sm),
                    color = if (isWarning) colors.warningMoneyDueContainer else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = if (isWarning) colors.warningMoneyDue else colors.primaryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!overlineText.isNullOrBlank()) {
                    Text(
                        text = overlineText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondaryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
                if (!supportingText.isNullOrBlank()) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 3,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!badgeText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(radii.xs),
                    color = if (isWarning) colors.warningMoneyDueContainer else colors.secondaryContainer,
                    modifier = Modifier.widthIn(max = 128.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isWarning) colors.warningMoneyDue else colors.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs)
                    )
                }
            }

            if (trailingContent != null) {
                Box(modifier = Modifier.widthIn(max = 128.dp), contentAlignment = Alignment.CenterEnd) {
                    trailingContent()
                }
            }
        }
    }
}

/**
 * Responsive section header that keeps titles wide and readable on narrow 320dp screens and
 * large font scales, moving the optional action button onto a second line whenever space is tight.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val spacing = AppTheme.spacing
    val colors = AppTheme.colors
    val hasAction = !actionLabel.isNullOrBlank() && onAction != null

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val shouldStack = !actionLabel.isNullOrBlank() && onAction != null &&
            (maxWidth < 340.dp || fontScale >= 1.25f)

        @Composable
        fun HeaderContent(contentModifier: Modifier) {
            Row(
                modifier = contentModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("section_header_title")
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (shouldStack) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                HeaderContent(Modifier.fillMaxWidth())
                GlassButton(
                    text = actionLabel,
                    onClick = onAction,
                    variant = GlassButtonVariant.Text,
                    modifier = Modifier.testTag("section_header_action")
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                HeaderContent(Modifier.weight(1f))
                if (!actionLabel.isNullOrBlank() && onAction != null) {
                    GlassButton(
                        text = actionLabel,
                        onClick = onAction,
                        variant = GlassButtonVariant.Text,
                        modifier = Modifier.testTag("section_header_action")
                    )
                }
            }
        }
    }
}
