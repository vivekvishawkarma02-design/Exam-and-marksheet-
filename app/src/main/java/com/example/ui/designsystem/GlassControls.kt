package com.example.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppTheme
import com.example.ui.theme.calmPressFeedback

/**
 * Variants supported by [GlassButton].
 */
enum class GlassButtonVariant {
    Primary,
    Secondary,
    Text
}

/**
 * Accessible button enforcing >= 48dp minimum touch height, 100–150ms calm press feedback,
 * safe label wrapping, and high-contrast glass/solid surfaces.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.Primary,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val colors = AppTheme.colors
    val glass = AppTheme.glass
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(radii.md)

    when (variant) {
        GlassButtonVariant.Primary -> {
            val containerColor = if (isDestructive) colors.negativeAmount else MaterialTheme.colorScheme.primary
            val contentColor = if (isDestructive) colors.onNegativeAmount else MaterialTheme.colorScheme.onPrimary
            Button(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                shape = shape,
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .calmPressFeedback(interactionSource = interactionSource, enabled = enabled)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        GlassButtonVariant.Secondary -> {
            val fillColor = when {
                isDestructive -> colors.negativeAmountContainer
                glass.glassEnabled -> colors.glassTintElevated
                else -> colors.solidSurface
            }
            val labelColor = if (isDestructive) colors.negativeAmount else colors.primaryAccent
            val border = if (isDestructive) {
                BorderStroke(1.dp, colors.negativeAmount.copy(alpha = 0.55f))
            } else if (glass.glassEnabled) {
                BorderStroke(1.dp, colors.glassSubtleBorder)
            } else {
                BorderStroke(1.dp, colors.solidBorder)
            }

            GlassSurface(
                shape = shape,
                intensity = GlassIntensity.Regular,
                tintOverride = fillColor,
                borderOverride = border,
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .calmPressFeedback(interactionSource = interactionSource, enabled = enabled)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick
                    )
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = spacing.lg, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val alphaTint = if (enabled) labelColor else labelColor.copy(alpha = 0.45f)
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = alphaTint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.sm))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = alphaTint,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        GlassButtonVariant.Text -> {
            val contentColor = if (isDestructive) colors.negativeAmount else colors.primaryAccent
            TextButton(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                shape = shape,
                contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .calmPressFeedback(interactionSource = interactionSource, enabled = enabled)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.xs))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Filter/selection chip enforcing >= 48dp minimum touch height and accessible text contrast.
 */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isWarning: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val colors = AppTheme.colors
    val glass = AppTheme.glass
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(radii.pill)

    val containerColor = when {
        selected && isWarning -> colors.warningMoneyDueContainer
        selected -> MaterialTheme.colorScheme.primary
        glass.glassEnabled -> colors.glassTintSubtle
        else -> colors.solidSurface
    }
    val textColor = when {
        selected && isWarning -> colors.warningMoneyDue
        selected -> MaterialTheme.colorScheme.onPrimary
        isWarning -> colors.warningMoneyDue
        else -> colors.textPrimary
    }
    val border = when {
        selected && isWarning -> BorderStroke(1.dp, colors.warningMoneyDue.copy(alpha = 0.6f))
        selected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        glass.glassEnabled -> BorderStroke(1.dp, colors.glassSubtleBorder)
        else -> BorderStroke(1.dp, colors.solidBorder)
    }

    Surface(
        shape = shape,
        color = containerColor,
        border = border,
        modifier = modifier
            .heightIn(min = 48.dp)
            .calmPressFeedback(interactionSource = interactionSource, enabled = enabled)
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Checkbox,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (enabled) textColor else textColor.copy(alpha = 0.45f),
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Form input field styled cleanly over translucent glass or solid Simple Look surfaces.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val radii = AppTheme.radii
    val colors = AppTheme.colors
    val glass = AppTheme.glass
    val fieldShape = RoundedCornerShape(radii.md)

    val containerBg = if (glass.glassEnabled) {
        colors.glassTintElevated
    } else {
        colors.solidSurface
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            supportingText = supportingText?.let {
                {
                    Text(
                        text = it,
                        color = if (isError) colors.negativeAmount else colors.textSecondary
                    )
                }
            },
            isError = isError,
            singleLine = singleLine,
            maxLines = maxLines,
            enabled = enabled,
            readOnly = readOnly,
            leadingIcon = leadingIcon?.let {
                {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = if (isError) colors.negativeAmount else colors.primaryAccent
                    )
                }
            },
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            shape = fieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = containerBg,
                unfocusedContainerColor = containerBg,
                disabledContainerColor = colors.solidSubtleSurface,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.primaryAccent,
                unfocusedBorderColor = if (glass.glassEnabled) colors.glassSubtleBorder else colors.solidBorder,
                errorBorderColor = colors.negativeAmount,
                errorLabelColor = colors.negativeAmount,
                focusedLabelColor = colors.primaryAccent,
                unfocusedLabelColor = colors.textSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        )
    }
}
