package com.example.ui

import androidx.navigation.NavGraphBuilder

/**
 * Release builds intentionally omit the Design Preview screen and route completely.
 */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.addDebugDesignPreviewRoute(
    onBack: () -> Unit
) {
    // No-op in release builds
}
