package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Indicates whether the current composition is running within a floating window service.
 * When true, components should avoid creating nested floating windows.
 */
val LocalIsInFloatingWindow = staticCompositionLocalOf { false }
