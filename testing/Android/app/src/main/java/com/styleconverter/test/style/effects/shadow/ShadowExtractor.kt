package com.styleconverter.test.style.effects.shadow

import com.styleconverter.test.style.PropertyRegistry
import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts shadow configuration from IR property JSON data.
 *
 * Handles the following IR property types:
 * - `BoxShadow` - CSS `box-shadow` property
 * - `TextShadow` - CSS `text-shadow` property (stored separately for typography)
 *
 * ## IR Format
 * Shadows are represented as arrays of shadow objects:
 * ```json
 * {
 *   "type": "BoxShadow",
 *   "data": [
 *     {
 *       "x": { "px": 5.0 },
 *       "y": { "px": 5.0 },
 *       "blur": { "px": 10.0 },
 *       "spread": { "px": 0.0 },
 *       "c": { "srgb": { "r": 0.0, "g": 0.0, "b": 0.0, "a": 0.5 } },
 *       "inset": false
 *     }
 *   ]
 * }
 * ```
 */
object ShadowExtractor {

    init {
        // BoxShadow (and TextShadow for typography) — claim them so the
        // legacy dispatch defers to the effects/shadow appliers. BoxShadow
        // is the border-category's only non-border-folder dependency.
        PropertyRegistry.migrated(
            "BoxShadow", "TextShadow",
            owner = "effects/shadow"
        )
    }

    /**
     * Extract shadow configuration from a list of property type/data pairs.
     *
     * @param properties List of pairs where first is the property type
     *                   and second is the JSON data for that property.
     * @return ShadowConfig with extracted shadow values.
     */
    fun extractShadowConfig(properties: List<Pair<String, JsonElement?>>): ShadowConfig {
        for ((type, data) in properties) {
            if (type == "BoxShadow" && data != null) {
                return ShadowConfig(shadows = extractShadows(data))
            }
        }
        return ShadowConfig()
    }

    /**
     * Extract a list of shadow data from JSON.
     *
     * Uses the existing ValueExtractors.extractShadows method and maps
     * to the shadow module's ShadowData type.
     *
     * @param json The JSON data (should be a JsonArray of shadow objects)
     * @return List of parsed ShadowData values
     */
    private fun extractShadows(json: JsonElement): List<ShadowData> {
        val shadowList = ValueExtractors.extractShadows(json)
        return shadowList.map { shadow ->
            ShadowData(
                offsetX = shadow.offsetX,
                offsetY = shadow.offsetY,
                blurRadius = shadow.blurRadius,
                spreadRadius = shadow.spreadRadius,
                color = shadow.color,
                inset = shadow.inset
            )
        }
    }

    /**
     * Check if a property type is a shadow-related property.
     *
     * @param type The property type string.
     * @return True if this is a shadow property.
     */
    fun isShadowProperty(type: String): Boolean {
        return type in setOf("BoxShadow", "TextShadow")
    }
}
