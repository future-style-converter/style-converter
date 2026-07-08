package com.styleconverter.test.style.color

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Extracts accent color configuration from IR properties.
 */
object AccentExtractor {

    /**
     * Extract accent configuration from property pairs.
     */
    fun extractAccentConfig(properties: List<Pair<String, JsonElement?>>): AccentConfig {
        for ((type, data) in properties) {
            when (type) {
                "AccentColor" -> return extractAccentColor(data)
            }
        }

        return AccentConfig()
    }

    private fun extractAccentColor(data: JsonElement?): AccentConfig {
        if (data == null) return AccentConfig()

        // Check for "auto" keyword
        if (data is JsonPrimitive) {
            val content = data.contentOrNull?.lowercase()
            if (content == "auto") {
                return AccentConfig(accentColor = null, isAuto = true)
            }
        }

        // Try to extract color
        val color = ValueExtractors.extractColor(data)
        return if (color != null) {
            AccentConfig(accentColor = color, isAuto = false)
        } else {
            AccentConfig()
        }
    }

    /**
     * Check if a property type is accent-related.
     */
    fun isAccentProperty(type: String): Boolean {
        return type == "AccentColor"
    }
}
