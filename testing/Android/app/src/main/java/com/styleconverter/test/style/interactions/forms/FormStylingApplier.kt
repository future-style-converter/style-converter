package com.styleconverter.test.style.interactions.forms

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Applies CSS form styling properties to Compose Material components.
 *
 * ## CSS Properties
 * ```css
 * .custom-form {
 *     accent-color: #6200ee;
 *     caret-color: #ff0000;
 *     color-scheme: light dark;
 *     field-sizing: content;
 * }
 *
 * input[type="password"] {
 *     input-security: auto;
 * }
 *
 * .disabled-section {
 *     interactivity: inert;
 * }
 * ```
 *
 * ## Compose Mapping
 *
 * | CSS Property | Compose Equivalent | Notes |
 * |--------------|-------------------|-------|
 * | accent-color | CheckboxColors, etc. | Control accent color |
 * | caret-color | cursorBrush | TextField cursor |
 * | color-scheme | Theme selection | Light/dark mode |
 * | field-sizing | IntrinsicSize | Content-based sizing |
 * | input-security | VisualTransformation | Password masking |
 * | interactivity | enabled parameter | Disable interaction |
 *
 * ## Usage
 * ```kotlin
 * FormStylingApplier.FormStylingProvider(config = formConfig) {
 *     // All form controls inside use the styling
 *     Checkbox(
 *         checked = isChecked,
 *         onCheckedChange = { },
 *         colors = FormStylingApplier.checkboxColors()
 *     )
 *
 *     TextField(
 *         value = text,
 *         onValueChange = { },
 *         colors = FormStylingApplier.textFieldColors()
 *     )
 * }
 * ```
 */
object FormStylingApplier {

    /**
     * CompositionLocal for form styling configuration.
     */
    val LocalFormStyling = compositionLocalOf { FormStylingConfig() }

    // =========================================================================
    // PROVIDER
    // =========================================================================

    /**
     * Provide form styling to descendant composables.
     *
     * @param config Form styling configuration
     * @param content Content that will use the form styling
     */
    @Composable
    fun FormStylingProvider(
        config: FormStylingConfig,
        content: @Composable () -> Unit
    ) {
        CompositionLocalProvider(LocalFormStyling provides config) {
            content()
        }
    }

    // =========================================================================
    // ACCENT COLOR (CHECKBOX, RADIO, SWITCH, SLIDER)
    // =========================================================================

    /**
     * Get the current accent color from configuration or theme.
     */
    @Composable
    fun currentAccentColor(): Color {
        val config = LocalFormStyling.current
        return if (config.isAccentAuto || config.accentColor == null) {
            MaterialTheme.colorScheme.primary
        } else {
            config.accentColor
        }
    }

    /**
     * Calculate content color (light/dark) based on accent luminance.
     */
    fun contentColorFor(accent: Color): Color {
        return if (accent.luminance() > 0.5f) Color.Black else Color.White
    }

    /**
     * Create CheckboxColors with form styling applied.
     */
    @Composable
    fun checkboxColors(): CheckboxColors {
        val accent = currentAccentColor()
        return CheckboxDefaults.colors(
            checkedColor = accent,
            checkmarkColor = contentColorFor(accent),
            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    /**
     * Create RadioButtonColors with form styling applied.
     */
    @Composable
    fun radioButtonColors(): RadioButtonColors {
        val accent = currentAccentColor()
        return RadioButtonDefaults.colors(
            selectedColor = accent,
            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    /**
     * Create SwitchColors with form styling applied.
     */
    @Composable
    fun switchColors(): SwitchColors {
        val accent = currentAccentColor()
        return SwitchDefaults.colors(
            checkedThumbColor = accent,
            checkedTrackColor = accent.copy(alpha = 0.5f),
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    /**
     * Create SliderColors with form styling applied.
     */
    @Composable
    fun sliderColors(): SliderColors {
        val accent = currentAccentColor()
        return SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = accent.copy(alpha = 0.24f)
        )
    }

    // =========================================================================
    // CARET COLOR (TEXT FIELDS)
    // =========================================================================

    /**
     * Get the current caret color from configuration or theme.
     */
    @Composable
    fun currentCaretColor(): Color {
        val config = LocalFormStyling.current
        return if (config.isCaretAuto || config.caretColor == null) {
            MaterialTheme.colorScheme.primary
        } else {
            config.caretColor
        }
    }

    /**
     * Get cursor brush for TextField.
     */
    @Composable
    fun cursorBrush() = SolidColor(currentCaretColor())

    /**
     * Create TextFieldColors with form styling applied.
     */
    @Composable
    fun textFieldColors(): TextFieldColors {
        val accent = currentAccentColor()
        val caret = currentCaretColor()

        return TextFieldDefaults.colors(
            focusedIndicatorColor = accent,
            cursorColor = caret,
            focusedLabelColor = accent,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    }

    /**
     * Create OutlinedTextFieldColors with form styling applied.
     */
    @Composable
    fun outlinedTextFieldColors(): TextFieldColors {
        val accent = currentAccentColor()
        val caret = currentCaretColor()

        return OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            cursorColor = caret,
            focusedLabelColor = accent
        )
    }

    /**
     * Get TextSelectionColors for text selection.
     */
    @Composable
    fun textSelectionColors(): TextSelectionColors {
        val accent = currentAccentColor()
        return TextSelectionColors(
            handleColor = accent,
            backgroundColor = accent.copy(alpha = 0.4f)
        )
    }

    // =========================================================================
    // COLOR SCHEME
    // =========================================================================

    /**
     * Determine if dark theme should be used based on config.
     */
    @Composable
    fun shouldUseDarkTheme(): Boolean {
        val config = LocalFormStyling.current
        val systemDark = isSystemInDarkTheme()

        return when (config.colorScheme) {
            ColorSchemePreference.NORMAL -> systemDark
            ColorSchemePreference.LIGHT -> false
            ColorSchemePreference.DARK -> true
            ColorSchemePreference.LIGHT_DARK -> systemDark  // Follow system, prefer light
            ColorSchemePreference.DARK_LIGHT -> systemDark  // Follow system, prefer dark
        }
    }

    // =========================================================================
    // INPUT SECURITY
    // =========================================================================

    /**
     * Get VisualTransformation for password fields based on config.
     *
     * @param isPassword Whether this is a password field
     */
    @Composable
    fun passwordTransformation(isPassword: Boolean = true): VisualTransformation {
        val config = LocalFormStyling.current

        return when {
            !isPassword -> VisualTransformation.None
            config.inputSecurity == InputSecurityValue.NONE -> VisualTransformation.None
            else -> PasswordVisualTransformation()
        }
    }

    // =========================================================================
    // INTERACTIVITY
    // =========================================================================

    /**
     * Check if element should be interactive based on config.
     */
    @Composable
    fun isInteractive(): Boolean {
        val config = LocalFormStyling.current
        return config.interactivity != InteractivityValue.INERT
    }

    /**
     * Get enabled state for form controls.
     *
     * @param baseEnabled Base enabled state
     */
    @Composable
    fun effectiveEnabled(baseEnabled: Boolean = true): Boolean {
        return baseEnabled && isInteractive()
    }

    // =========================================================================
    // FIELD SIZING
    // =========================================================================

    /**
     * Check if field should size to content.
     */
    @Composable
    fun shouldSizeToContent(): Boolean {
        val config = LocalFormStyling.current
        return config.fieldSizing == FieldSizingValue.CONTENT
    }

    // =========================================================================
    // COMPLETE FORM CONTROL STYLING
    // =========================================================================

    /**
     * Complete checkbox styling configuration.
     */
    data class CheckboxStyling(
        val colors: CheckboxColors,
        val enabled: Boolean
    )

    @Composable
    fun completeCheckboxStyling(baseEnabled: Boolean = true): CheckboxStyling {
        return CheckboxStyling(
            colors = checkboxColors(),
            enabled = effectiveEnabled(baseEnabled)
        )
    }

    /**
     * Complete text field styling configuration.
     */
    data class TextFieldStyling(
        val colors: TextFieldColors,
        val cursorBrush: SolidColor,
        val enabled: Boolean,
        val visualTransformation: VisualTransformation
    )

    @Composable
    fun completeTextFieldStyling(
        baseEnabled: Boolean = true,
        isPassword: Boolean = false
    ): TextFieldStyling {
        return TextFieldStyling(
            colors = textFieldColors(),
            cursorBrush = cursorBrush(),
            enabled = effectiveEnabled(baseEnabled),
            visualTransformation = passwordTransformation(isPassword)
        )
    }

    // =========================================================================
    // NOTES
    // =========================================================================

    object Notes {
        const val ACCENT_COLOR = """
            CSS accent-color sets the color for native form controls.

            In Compose, we apply it to:
            - Checkbox (checkedColor)
            - RadioButton (selectedColor)
            - Switch (checkedThumbColor, checkedTrackColor)
            - Slider (thumbColor, activeTrackColor)

            When accent-color: auto, we use MaterialTheme.colorScheme.primary.
        """

        const val CARET_COLOR = """
            CSS caret-color sets the text input cursor color.

            In Compose, we apply it to:
            - TextField (cursorColor via colors parameter)
            - BasicTextField (cursorBrush parameter)

            Special values:
            - auto: Use primary color
            - currentcolor: Use text color (approximated)
        """

        const val COLOR_SCHEME = """
            CSS color-scheme hints light/dark mode support.

            Values:
            - normal: Follow system
            - light: Force light mode
            - dark: Force dark mode
            - light dark: Support both, prefer light
            - dark light: Support both, prefer dark

            In Compose, use shouldUseDarkTheme() with MaterialTheme.
        """

        const val FIELD_SIZING = """
            CSS field-sizing controls input sizing behavior.

            Values:
            - fixed: Input has fixed size (default)
            - content: Input grows/shrinks to fit content

            In Compose, content-based sizing requires:
            - IntrinsicSize modifiers
            - Custom measurement logic
            - Or allow BasicTextField to size naturally
        """

        const val INTERACTIVITY = """
            CSS interactivity controls element interaction.

            Values:
            - auto: Normal interaction (default)
            - inert: Element and descendants non-interactive

            In Compose, we propagate this as enabled=false
            to form controls. For full inert behavior, also
            consider semantics { disabled() }.
        """
    }
}
