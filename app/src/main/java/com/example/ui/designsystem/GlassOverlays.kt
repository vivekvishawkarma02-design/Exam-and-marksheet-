package com.example.ui.designsystem

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.ui.theme.AppTheme

/**
 * Scrollable modal bottom sheet with glass surface styling, navigation-bar safe insets,
 * and keyboard (IME) padding so tall forms and narrow 320dp screens remain usable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val colors = AppTheme.colors
    val glass = AppTheme.glass

    val containerColor = if (glass.glassEnabled) colors.glassTintElevated else colors.solidSurface

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = radii.xl, topEnd = radii.xl),
        containerColor = containerColor,
        contentColor = colors.textPrimary,
        contentWindowInsets = { WindowInsets.navigationBars },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            if (!title.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("glass_sheet_title")
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                            .testTag("glass_sheet_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close sheet",
                            tint = colors.textSecondary
                        )
                    }
                }
                HorizontalDivider(color = colors.solidBorder)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
                content = content
            )

            if (footer != null) {
                HorizontalDivider(color = colors.solidBorder)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    content = footer
                )
            }
        }
    }
}

/**
 * Scrollable dialog built on [GlassSurface] with [FlowRow] action buttons and optional
 * Android 12+ platform window background blur when glass effects are active.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    dismissLabel: String? = "Cancel",
    onDismiss: (() -> Unit)? = onDismissRequest,
    confirmEnabled: Boolean = true,
    isDestructiveConfirm: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val spacing = AppTheme.spacing
    val radii = AppTheme.radii
    val colors = AppTheme.colors
    val blurTokens = AppTheme.blur
    val glass = AppTheme.glass
    val blurActive = isGlassBlurActive(glass)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (blurActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val view = LocalView.current
            SideEffect {
                runCatching {
                    val window = (view.parent as? DialogWindowProvider)?.window
                    if (window != null) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window.setBackgroundBlurRadius(blurTokens.windowBlurRadiusPx)
                    }
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.lg)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            GlassSurface(
                shape = RoundedCornerShape(radii.xl),
                intensity = GlassIntensity.Prominent,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isDestructiveConfirm) colors.negativeAmount else colors.primaryAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 3,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("glass_dialog_title")
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        content = content
                    )

                    Spacer(modifier = Modifier.height(spacing.xs))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        if (!dismissLabel.isNullOrBlank() && onDismiss != null) {
                            GlassButton(
                                text = dismissLabel,
                                onClick = onDismiss,
                                variant = GlassButtonVariant.Text,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("glass_dialog_dismiss")
                            )
                        }
                        GlassButton(
                            text = confirmLabel,
                            onClick = onConfirm,
                            enabled = confirmEnabled,
                            isDestructive = isDestructiveConfirm,
                            variant = GlassButtonVariant.Primary,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("glass_dialog_confirm")
                        )
                    }
                }
            }
        }
    }
}
