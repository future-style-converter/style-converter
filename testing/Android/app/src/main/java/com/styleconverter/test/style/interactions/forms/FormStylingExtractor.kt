package com.styleconverter.test.style.interactions.forms

import androidx.compose.ui.graphics.Color
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts form styling configuration from IR properties.
 */
object FormStylingExtractor {

    fun extractFormConfig(properties: List<Pair<String, JsonElement?>>): FormStylingConfig {
        var accentColor: Color? = null
        var caretColor: Color? = null
        var colorScheme = ColorSchemePreference.NORMAL
        var isAccentAuto = true
        var isCaretAuto = true
        var fieldSizing = FieldSizingValue.FIXED
        var inputSecurity = InputSecurityValue.AUTO
        var interactivity = InteractivityValue.AUTO
        var forcedColorAdjust = ForcedColorAdjustValue.AUTO
        var printColorAdjust = PrintColorAdjustValue.ECONOMY

        for ((type, data) in properties) {
            when (type) {
                "AccentColor" -> {
                    val keyword = ValueExtractors.extractKeyword(data)?.lowercase()
                    if (keyword == "auto") {
                        isAccentAuto = true
                    } else {
                        accentColor = ValueExtractors.extractColor(data)
                        isAccentAuto = accentColor == null
                    }
                }
                "CaretColor" -> {
                    val keyword = ValueExtractors.extractKeyword(data)?.lowercase()
                    if (keyword == "auto" || keyword == "currentcolor") {
                        isCaretAuto = true
                    } else {
                        caretColor = ValueExtractors.extractColor(data)
                        isCaretAuto = caretColor == null
                    }
                }
                "ColorScheme" -> {
                    colorScheme = extractColorScheme(data)
                }
                "FieldSizing" -> {
                    fieldSizing = extractFieldSizing(data)
                }
                "InputSecurity" -> {
                    inputSecurity = extractInputSecurity(data)
                }
                "Interactivity" -> {
                    interactivity = extractInteractivity(data)
                }
                "ForcedColorAdjust" -> {
                    forcedColorAdjust = extractForcedColorAdjust(data)
                }
                "PrintColorAdjust" -> {
                    printColorAdjust = extractPrintColorAdjust(data)
                }
            }
        }

        return FormStylingConfig(
            accentColor = accentColor,
            caretColor = caretColor,
            colorScheme = colorScheme,
            isAccentAuto = isAccentAuto,
            isCaretAuto = isCaretAuto,
            fieldSizing = fieldSizing,
            inputSecurity = inputSecurity,
            interactivity = interactivity,
            forcedColorAdjust = forcedColorAdjust,
            printColorAdjust = printColorAdjust
        )
    }

    private fun extractColorScheme(data: JsonElement?): ColorSchemePreference {
        val keyword = ValueExtractors.extractKeyword(data)?.lowercase() ?: return ColorSchemePreference.NORMAL

        return when {
            keyword == "normal" -> ColorSchemePreference.NORMAL
            keyword == "light" -> ColorSchemePreference.LIGHT
            keyword == "dark" -> ColorSchemePreference.DARK
            keyword.contains("light") && keyword.contains("dark") -> {
                if (keyword.startsWith("light")) ColorSchemePreference.LIGHT_DARK
                else ColorSchemePreference.DARK_LIGHT
            }
            else -> ColorSchemePreference.NORMAL
        }
    }

    private fun extractFieldSizing(data: JsonElement?): FieldSizingValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?.replace("-", "_") ?: return FieldSizingValue.FIXED
        return try {
            FieldSizingValue.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            FieldSizingValue.FIXED
        }
    }

    private fun extractInputSecurity(data: JsonElement?): InputSecurityValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase() ?: return InputSecurityValue.AUTO
        return try {
            InputSecurityValue.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            InputSecurityValue.AUTO
        }
    }

    private fun extractInteractivity(data: JsonElement?): InteractivityValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase() ?: return InteractivityValue.AUTO
        return try {
            InteractivityValue.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            InteractivityValue.AUTO
        }
    }

    private fun extractForcedColorAdjust(data: JsonElement?): ForcedColorAdjustValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?.replace("-", "_") ?: return ForcedColorAdjustValue.AUTO
        return try {
            ForcedColorAdjustValue.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            ForcedColorAdjustValue.AUTO
        }
    }

    private fun extractPrintColorAdjust(data: JsonElement?): PrintColorAdjustValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase() ?: return PrintColorAdjustValue.ECONOMY
        return try {
            PrintColorAdjustValue.valueOf(keyword)
        } catch (e: IllegalArgumentException) {
            PrintColorAdjustValue.ECONOMY
        }
    }

    fun isFormStylingProperty(type: String): Boolean {
        return type in FORM_STYLING_PROPERTIES
    }

    private val FORM_STYLING_PROPERTIES = setOf(
        "AccentColor", "CaretColor", "ColorScheme",
        "FieldSizing", "InputSecurity", "Interactivity",
        "ForcedColorAdjust", "PrintColorAdjust"
    )
}
