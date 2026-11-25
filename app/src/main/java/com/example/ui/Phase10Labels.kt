package com.example.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Phase 10 Hindi-friendly label helper with instant English toggle.
 * When [isHindi] is true, shows crisp Hindi-first label (with familiar English term where helpful);
 * when false, shows clean English label.
 */
internal fun p10Label(isHindi: Boolean, hindi: String, english: String): String =
    if (isHindi) hindi else english

@Composable
internal fun Phase10LanguageToggleChip(
    isHindi: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isHindi,
        onClick = onToggle,
        label = {
            Text(
                text = if (isHindi) "हिन्दी / EN" else "English / हि",
                fontWeight = FontWeight.SemiBold
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = "Switch language between Hindi and English",
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("phase10_lang_toggle_chip")
    )
}
