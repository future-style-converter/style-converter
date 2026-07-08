package com.styleconverter.test.style.interactions.forms

import androidx.compose.ui.graphics.Color

/**
 * Configuration for form-related CSS properties.
 *
 * ## Supported Properties
 * - accent-color: Color for form controls (checkboxes, radio buttons, etc.)
 * - caret-color: Color of text input cursor
 * - color-scheme: Light/dark mode preference
 *
 * ## Compose Mapping
 * ```kotlin
 * val config = FormStylingExtractor.extractFormConfig(properties)
 *
 * // Checkbox with accent color
 * Checkbox(
 *     checked = isChecked,
 *     colors = CheckboxDefaults.colors(
 *         checkedColor = config.accentColor ?: MaterialTheme.colors.primary
 *     )
 * )
 *
 * // TextField with caret color
 * TextField(
 *     value = text,
 *     cursorBrush = SolidColor(config.caretColor ?: LocalContentColor.current)
 * )
 * ```
 */
data class FormStylingConfig(
    /** Accent color for form controls */
    val accentColor: Color? = null,
    /** Text input caret/cursor color */
    val caretColor: Color? = null,
    /** Color scheme preference */
    val colorScheme: ColorSchemePreference = ColorSchemePreference.NORMAL,
    /** Whether accent color is set to auto */
    val isAccentAuto: Boolean = true,
    /** Whether caret color is set to auto */
    val isCaretAuto: Boolean = true,
    /** Field sizing behavior */
    val fieldSizing: FieldSizingValue = FieldSizingValue.FIXED,
    /** Input security for password fields */
    val inputSecurity: InputSecurityValue = InputSecurityValue.AUTO,
    /** Interactivity state */
    val interactivity: InteractivityValue = InteractivityValue.AUTO,
    /** Forced color adjust for high contrast mode */
    val forcedColorAdjust: ForcedColorAdjustValue = ForcedColorAdjustValue.AUTO,
    /** Print color adjust for printing */
    val printColorAdjust: PrintColorAdjustValue = PrintColorAdjustValue.ECONOMY
) {
    val hasFormStyling: Boolean
        get() = accentColor != null || caretColor != null ||
                colorScheme != ColorSchemePreference.NORMAL ||
                fieldSizing != FieldSizingValue.FIXED ||
                inputSecurity != InputSecurityValue.AUTO ||
                interactivity != InteractivityValue.AUTO ||
                forcedColorAdjust != ForcedColorAdjustValue.AUTO ||
                printColorAdjust != PrintColorAdjustValue.ECONOMY

    /** Get effective accent color with fallback */
    fun getAccentColorOrDefault(default: Color): Color {
        return accentColor ?: default
    }

    /** Get effective caret color with fallback */
    fun getCaretColorOrDefault(default: Color): Color {
        return caretColor ?: default
    }

    /** Check if dark mode is preferred */
    val prefersDarkMode: Boolean
        get() = colorScheme == ColorSchemePreference.DARK ||
                colorScheme == ColorSchemePreference.DARK_LIGHT

    /** Check if light mode is preferred */
    val prefersLightMode: Boolean
        get() = colorScheme == ColorSchemePreference.LIGHT ||
                colorScheme == ColorSchemePreference.LIGHT_DARK
}

/**
 * Color scheme preference values.
 */
enum class ColorSchemePreference {
    /** No preference */
    NORMAL,
    /** Light mode only */
    LIGHT,
    /** Dark mode only */
    DARK,
    /** Light mode preferred, dark supported */
    LIGHT_DARK,
    /** Dark mode preferred, light supported */
    DARK_LIGHT
}

/**
 * Field sizing values.
 *
 * CSS: field-sizing property for form inputs.
 */
enum class FieldSizingValue {
    /** Fixed sizing (default) - field has fixed size */
    FIXED,
    /** Content-based sizing - field grows/shrinks to fit content */
    CONTENT
}

/**
 * Input security values.
 *
 * CSS: input-security property for password fields.
 */
enum class InputSecurityValue {
    /** Auto (default) - password characters obscured */
    AUTO,
    /** None - password characters visible as plain text */
    NONE
}

/**
 * Interactivity values.
 *
 * CSS: interactivity property for element interaction.
 */
enum class InteractivityValue {
    /** Auto (default) - element is interactive */
    AUTO,
    /** Inert - element and descendants are non-interactive */
    INERT
}

/**
 * Forced color adjust values.
 *
 * CSS: forced-color-adjust for high contrast mode.
 */
enum class ForcedColorAdjustValue {
    /** Auto (default) - UA adjusts colors in forced colors mode */
    AUTO,
    /** None - colors are not adjusted in forced colors mode */
    NONE,
    /** Preserve parent value */
    PRESERVE_PARENT_COLOR
}

/**
 * Print color adjust values.
 *
 * CSS: print-color-adjust for print optimization.
 */
enum class PrintColorAdjustValue {
    /** Economy (default) - UA may adjust colors for printing */
    ECONOMY,
    /** Exact - preserve colors exactly as specified */
    EXACT
}
