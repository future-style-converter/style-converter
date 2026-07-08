package com.styleconverter.test.style.color

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Applies CSS accent-color property to form controls.
 *
 * ## CSS Property
 * ```css
 * .custom-controls {
 *     accent-color: #6200ee;
 * }
 *
 * .auto-accent {
 *     accent-color: auto;
 * }
 * ```
 *
 * ## Compose Mapping
 *
 * CSS accent-color affects:
 * - Checkboxes
 * - Radio buttons
 * - Range sliders
 * - Progress bars
 * - Some text selection colors
 *
 * In Compose, these are controlled by:
 * - Material 3 ColorScheme (primary, secondary colors)
 * - Individual component color parameters
 *
 * ## Usage
 * ```kotlin
 * AccentApplier.AccentColorProvider(
 *     config = accentConfig
 * ) {
 *     // All form controls inside will use the accent color
 *     Checkbox(checked = true, onCheckedChange = {})
 *     RadioButton(selected = true, onClick = {})
 *     Slider(value = 0.5f, onValueChange = {})
 * }
 *
 * // Or apply to individual components
 * Checkbox(
 *     checked = true,
 *     onCheckedChange = {},
 *     colors = AccentApplier.checkboxColors(accentConfig)
 * )
 * ```
 */
object AccentApplier {

    /**
     * CompositionLocal for providing accent color to descendant composables.
     */
    val LocalAccentColor = compositionLocalOf<Color?> { null }

    /**
     * Provide accent color to descendant composables.
     *
     * @param config Accent color configuration
     * @param content Content that will use the accent color
     */
    @Composable
    fun AccentColorProvider(
        config: AccentConfig,
        content: @Composable () -> Unit
    ) {
        val accentColor = if (config.isAuto) {
            MaterialTheme.colorScheme.primary
        } else {
            config.accentColor ?: MaterialTheme.colorScheme.primary
        }

        CompositionLocalProvider(LocalAccentColor provides accentColor) {
            content()
        }
    }

    /**
     * Get the current accent color from CompositionLocal or theme.
     */
    @Composable
    fun currentAccentColor(): Color {
        return LocalAccentColor.current ?: MaterialTheme.colorScheme.primary
    }

    /**
     * Create CheckboxColors with custom accent color.
     *
     * @param config Accent color configuration
     * @return CheckboxColors using the accent color
     */
    @Composable
    fun checkboxColors(config: AccentConfig): CheckboxColors {
        val accent = resolveAccentColor(config)
        val contentColor = calculateContentColor(accent)

        return CheckboxDefaults.colors(
            checkedColor = accent,
            checkmarkColor = contentColor,
            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    /**
     * Create RadioButtonColors with custom accent color.
     *
     * @param config Accent color configuration
     * @return RadioButtonColors using the accent color
     */
    @Composable
    fun radioButtonColors(config: AccentConfig): RadioButtonColors {
        val accent = resolveAccentColor(config)

        return RadioButtonDefaults.colors(
            selectedColor = accent,
            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    /**
     * Create SwitchColors with custom accent color.
     *
     * @param config Accent color configuration
     * @return SwitchColors using the accent color
     */
    @Composable
    fun switchColors(config: AccentConfig): SwitchColors {
        val accent = resolveAccentColor(config)
        val trackColor = accent.copy(alpha = 0.5f)

        return SwitchDefaults.colors(
            checkedThumbColor = accent,
            checkedTrackColor = trackColor,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    /**
     * Create SliderColors with custom accent color.
     *
     * @param config Accent color configuration
     * @return SliderColors using the accent color
     */
    @Composable
    fun sliderColors(config: AccentConfig): SliderColors {
        val accent = resolveAccentColor(config)
        val inactiveTrack = accent.copy(alpha = 0.24f)

        return SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = inactiveTrack
        )
    }

    /**
     * Resolve the accent color from config or theme.
     */
    @Composable
    private fun resolveAccentColor(config: AccentConfig): Color {
        return if (config.isAuto || config.accentColor == null) {
            MaterialTheme.colorScheme.primary
        } else {
            config.accentColor
        }
    }

    /**
     * Calculate appropriate content color (light/dark) for the accent.
     */
    private fun calculateContentColor(accent: Color): Color {
        return if (accent.luminance() > 0.5f) {
            Color.Black
        } else {
            Color.White
        }
    }

    /**
     * Calculate a complementary color for the accent.
     * Useful for creating visual contrast.
     */
    fun complementaryColor(accent: Color): Color {
        // Simple complement by inverting RGB
        return Color(
            red = 1f - accent.red,
            green = 1f - accent.green,
            blue = 1f - accent.blue,
            alpha = accent.alpha
        )
    }

    /**
     * Calculate a lighter variant of the accent color.
     */
    fun lighterVariant(accent: Color, factor: Float = 0.3f): Color {
        return Color(
            red = accent.red + (1f - accent.red) * factor,
            green = accent.green + (1f - accent.green) * factor,
            blue = accent.blue + (1f - accent.blue) * factor,
            alpha = accent.alpha
        )
    }

    /**
     * Calculate a darker variant of the accent color.
     */
    fun darkerVariant(accent: Color, factor: Float = 0.3f): Color {
        return Color(
            red = accent.red * (1f - factor),
            green = accent.green * (1f - factor),
            blue = accent.blue * (1f - factor),
            alpha = accent.alpha
        )
    }

    /**
     * Preset accent colors matching common design systems.
     */
    object Presets {
        /** Material Design 3 default primary */
        val Material3Primary = Color(0xFF6750A4)

        /** Material Design 2 purple primary */
        val Material2Primary = Color(0xFF6200EE)

        /** iOS system blue */
        val iOSBlue = Color(0xFF007AFF)

        /** Android system green */
        val AndroidGreen = Color(0xFF3DDC84)

        /** Windows accent blue */
        val WindowsBlue = Color(0xFF0078D4)

        /** Common brand colors */
        val GoogleBlue = Color(0xFF4285F4)
        val FacebookBlue = Color(0xFF1877F2)
        val TwitterBlue = Color(0xFF1DA1F2)
    }

    /**
     * Notes about CSS accent-color support.
     */
    object Notes {
        const val SCOPE = """
            CSS accent-color primarily affects native form controls.
            In Compose, all controls are custom-drawn, so we apply
            the accent color through Material component parameters.
        """

        const val AUTO_VALUE = """
            When accent-color: auto is specified, the browser picks
            a suitable color. In Compose, we use the theme's primary
            color as the automatic value.
        """

        const val ACCESSIBILITY = """
            Ensure the accent color has sufficient contrast against
            the background. The calculateContentColor function helps
            pick an appropriate foreground color.
        """
    }
}
