package com.styleconverter.runtime.effects.shadow

import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.PropertyRegistry
import com.styleconverter.runtime.core.types.ValueExtractors
// css-backgrounds-3 §6.1: an absent/`currentColor` shadow colour is the
// element's `color` — resolved by the shared effects ink helper (retro R6).
import com.styleconverter.runtime.effects.EffectsCurrentColorInk
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
     * @param wptCaptureMode the StyleApplier-threaded WPT flag for the
     *   `currentcolor` bottom-out (EffectsCurrentColorInk); default false
     *   keeps every dark-stage caller on the historical #eee ink.
     * @return ShadowConfig with extracted shadow values.
     */
    fun extractShadowConfig(
        properties: List<Pair<String, JsonElement?>>,
        wptCaptureMode: Boolean = false,
    ): ShadowConfig {
        for ((type, data) in properties) {
            if (type == "BoxShadow" && data != null) {
                // Resolved lazily — only a declared box-shadow pays for it.
                return ShadowConfig(
                    shadows = extractShadows(data, EffectsCurrentColorInk.resolve(properties, wptCaptureMode)),
                )
            }
        }
        return ShadowConfig()
    }

    /**
     * Extract a list of shadow data from JSON.
     *
     * Uses the existing ValueExtractors.extractShadows method for the
     * geometry and maps to the shadow module's ShadowData type; the COLOUR
     * is re-read here because ValueExtractors substitutes opaque black for
     * an absent or statically-unresolvable `c` (ValueExtractors.kt
     * extractShadows: `?: Color.Black`), while css-backgrounds-3 §6.1 says
     * "If the color is absent, the used color is taken from the color
     * property" — and the wire's `c: {"original": "currentColor"}` (the
     * css-color/currentcolor-003 carrier) decodes to null for the same
     * reason. Retro R6, sibling of the drop-shadow default (A7#1).
     *
     * @param json The JSON data (should be a JsonArray of shadow objects)
     * @param currentColorInk the element's resolved `color` — the §6.1 default
     * @return List of parsed ShadowData values
     */
    private fun extractShadows(json: JsonElement, currentColorInk: Color): List<ShadowData> {
        val shadowList = ValueExtractors.extractShadows(json)
        // ValueExtractors walks the array's JsonObjects in order (mapNotNull
        // over `as? JsonObject`), so filtering the same way here keeps the
        // two lists index-aligned without duplicating the geometry parse.
        val objects = (json as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()
        return shadowList.mapIndexed { index, shadow ->
            // The colour the author actually declared, or null when absent /
            // `currentColor` / otherwise runtime-dependent.
            val declared = objects.getOrNull(index)?.get("c")?.let { ValueExtractors.extractColor(it) }
            ShadowData(
                offsetX = shadow.offsetX,
                offsetY = shadow.offsetY,
                blurRadius = shadow.blurRadius,
                spreadRadius = shadow.spreadRadius,
                // Declared colour wins; otherwise the element's own `color`.
                color = declared ?: currentColorInk,
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
