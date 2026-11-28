package com.example.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppTheme
import com.example.ui.theme.calmPressFeedback

/**
 * Top app bar using the liquid-glass surface treatment with status-bar inset support,
 * wrapping/ellipsizing title (never squeezed vertically), and >= 48dp touch targets.
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector? = Icons.AutoMirrored.Filled.ArrowBack,
    navigationContentDescription: String = "Back",
    onNavigationClick: (() -> Unit)? = null,
    applyStatusBarInsets: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val spacing = AppTheme.spacing
    val colors = AppTheme.colors

    GlassSurface(
        shape = RectangleShape,
        intensity = GlassIntensity.Prominent,
        modifier = modifier
            .fillMaxWidth()
            .then(if (applyStatusBarInsets) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            if (onNavigationClick != null && navigationIcon != null) {
                IconButton(
                    onClick = onNavigationClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .testTag("glass_top_bar_nav_button")
                ) {
                    Icon(
                        imageVector = navigationIcon,
                        contentDescription = navigationContentDescription,
                        tint = colors.primaryAccent
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(spacing.sm))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = spacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("glass_top_bar_title")
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                content = actions
            )
        }
    }
}

/**
 * Navigation item descriptor for [GlassBottomBar].
 */
@Immutable
data class GlassBottomBarItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val contentDescription: String = label,
    val badgeCount: Int = 0,
    val testTag: String? = null
)

/**
 * Bottom navigation bar with glass surface, navigation-bar safe insets, >= 48dp touch targets,
 * and calm pill highlight for the active destination.
 */
@Composable
fun GlassBottomBar(
    items: List<GlassBottomBarItem>,
    selectedId: String,
    onSelect: (GlassBottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
    applyNavigationBarInsets: Boolean = true
) {
    val spacing = AppTheme.spacing
    val colors = AppTheme.colors
    val radii = AppTheme.radii

    GlassSurface(
        shape = RoundedCornerShape(topStart = radii.lg, topEnd = radii.lg),
        intensity = GlassIntensity.Prominent,
        modifier = modifier
            .fillMaxWidth()
            .then(if (applyNavigationBarInsets) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isTightFiveTab = items.size >= 5 && maxWidth < 360.dp
            val rowHorizontalPad = if (isTightFiveTab) spacing.xs else spacing.sm
            val itemGap = if (isTightFiveTab) 2.dp else spacing.xs
            val itemHorizontalPad = if (isTightFiveTab) 2.dp else spacing.xs
            val pillHorizontalPad = if (isTightFiveTab) spacing.sm else spacing.md

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = rowHorizontalPad, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(itemGap)
            ) {
                items.forEach { item ->
                    val selected = item.id == selectedId
                    val interactionSource = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(radii.md))
                            .calmPressFeedback(interactionSource = interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = androidx.compose.material3.ripple(),
                                role = Role.Tab,
                                onClick = { onSelect(item) }
                            )
                            .then(if (item.testTag != null) Modifier.testTag(item.testTag) else Modifier)
                            .padding(horizontal = itemHorizontalPad, vertical = spacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = pillHorizontalPad, vertical = spacing.xs),
                                contentAlignment = Alignment.Center
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (item.badgeCount > 0) {
                                            Badge {
                                                Text(text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.icon,
                                        contentDescription = item.contentDescription,
                                        tint = if (selected) colors.primaryAccent else colors.textSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.size(2.dp))
                        Text(
                            text = item.label,
                            style = if (isTightFiveTab) {
                                MaterialTheme.typography.labelSmall.copy(
                                    fontSize = androidx.compose.ui.unit.TextUnit(
                                        10f,
                                        androidx.compose.ui.unit.TextUnitType.Sp
                                    )
                                )
                            } else {
                                MaterialTheme.typography.labelSmall
                            },
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.primaryAccent else colors.textSecondary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
