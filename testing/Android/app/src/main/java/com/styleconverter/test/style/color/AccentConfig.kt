package com.styleconverter.test.style.color

import androidx.compose.ui.graphics.Color

/**
 * Configuration for CSS accent-color property.
 * Controls the accent color for form controls.
 */
data class AccentConfig(
    val accentColor: Color? = null,
    val isAuto: Boolean = true
) {
    /**
     * Check if this config has an accent color set.
     */
    val hasAccentColor: Boolean
        get() = accentColor != null || !isAuto
}
