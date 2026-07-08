package com.styleconverter.test.style.borders.outline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts outline configuration from IR property JSON data.
 *
 * Handles the following IR property types:
 * - `OutlineWidth` - CSS `outline-width` property
 * - `OutlineStyle` - CSS `outline-style` property
 * - `OutlineColor` - CSS `outline-color` property
 * - `OutlineOffset` - CSS `outline-offset` property
 *
 * ## IR Format Examples
 * ```json
 * { "type": "OutlineWidth", "data": { "px": 2.0 } }
 * { "type": "OutlineStyle", "data": "solid" }
 * { "type": "OutlineColor", "data": { "srgb": { "r": 0, "g": 0, "b": 1 } } }
 * { "type": "OutlineOffset", "data": { "px": 4.0 } }
 * ```
 */
object OutlineExtractor {

    init {
        // CSS outline is distinct from border: drawn outside the box and
        // doesn't affect layout. Registered so the legacy dispatch skips
        // these — the OutlineApplier is the only code that should paint them.
        PropertyRegistry.migrated(
            "OutlineWidth", "OutlineStyle", "OutlineColor", "OutlineOffset",
            owner = "borders/outline"
        )
    }

    /**
     * Extract outline configuration from a list of property type/data pairs.
     *
     * @param properties List of pairs where first is the property type
     *                   and second is the JSON data for that property.
     * @return OutlineConfig with extracted outline properties.
     */
    fun extractOutlineConfig(properties: List<Pair<String, JsonElement?>>): OutlineConfig {
        var config = OutlineConfig()

        for ((type, data) in properties) {
            config = when (type) {
                "OutlineWidth" -> config.copy(width = extractWidth(data))
                "OutlineStyle" -> config.copy(style = extractStyle(data))
                "OutlineColor" -> config.copy(color = ValueExtractors.extractColor(data) ?: Color.Black)
                "OutlineOffset" -> config.copy(offset = ValueExtractors.extractDp(data) ?: 0.dp)
                else -> config
            }
        }

        return config
    }

    /**
     * Extract outline width from JSON.
     * Supports both pixel values and keywords (thin, medium, thick).
     */
    private fun extractWidth(json: JsonElement?): Dp {
        return ValueExtractors.extractBorderWidth(json) ?: 0.dp
    }

    /**
     * Extract outline style from JSON.
     * Converts CSS style keywords to OutlineStyle enum.
     */
    private fun extractStyle(json: JsonElement?): OutlineStyle {
        val styleKeyword = ValueExtractors.extractKeyword(json)?.uppercase() ?: return OutlineStyle.NONE
        return try {
            OutlineStyle.valueOf(styleKeyword)
        } catch (e: IllegalArgumentException) {
            OutlineStyle.NONE
        }
    }

    /**
     * Check if a property type is an outline-related property.
     *
     * @param type The property type string.
     * @return True if this is an outline property.
     */
    fun isOutlineProperty(type: String): Boolean {
        return type in setOf("OutlineWidth", "OutlineStyle", "OutlineColor", "OutlineOffset")
    }
}
