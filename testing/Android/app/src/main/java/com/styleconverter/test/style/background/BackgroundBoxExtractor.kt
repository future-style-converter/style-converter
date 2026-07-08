package com.styleconverter.test.style.background

// Pulls background-clip / background-origin keywords out of IR.
//
// IR shapes observed (see /tmp/c4-background-clip, /tmp/c4-background-origin):
//   BackgroundClip   -> ["BORDER_BOX"] | ["PADDING_BOX"] | ["CONTENT_BOX"] | ["TEXT"]
//   BackgroundOrigin -> [{"type": "border-box"}] | [{"type": "padding-box"}] | [{"type": "content-box"}]
//
// Clip is an array of bare uppercase strings; Origin is an array of {type: …}
// objects. Not consistent between the two — we tolerate both shapes at each
// property to keep extraction robust to parser-side drift.

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts background-clip + background-origin configuration from IR properties.
 */
object BackgroundBoxExtractor {

    /**
     * Walk property pairs, return combined config.
     * First array entry wins — ColorConfig holds a single value per property,
     * not a per-layer list.
     */
    fun extractBackgroundBoxConfig(properties: List<Pair<String, JsonElement?>>): BackgroundBoxConfig {
        var backgroundClip = BackgroundBoxValue.BORDER_BOX
        var backgroundOrigin = BackgroundBoxValue.PADDING_BOX

        for ((type, data) in properties) {
            when (type) {
                "BackgroundClip" -> backgroundClip = extractBackgroundBox(data, BackgroundBoxValue.BORDER_BOX)
                "BackgroundOrigin" -> backgroundOrigin = extractBackgroundBox(data, BackgroundBoxValue.PADDING_BOX)
            }
        }

        return BackgroundBoxConfig(
            backgroundClip = backgroundClip,
            backgroundOrigin = backgroundOrigin
        )
    }

    /**
     * Resolve a box value from the observed IR shapes.
     * Accepts: array-of-string, array-of-{type}, bare string, bare {type}.
     */
    private fun extractBackgroundBox(data: JsonElement?, default: BackgroundBoxValue): BackgroundBoxValue {
        if (data == null) return default
        // Unwrap array envelope if present — first entry drives the value.
        val entry: JsonElement = when (data) {
            is JsonArray -> data.firstOrNull() ?: return default
            else -> data
        }
        // Pull the keyword from whatever shape the entry is.
        val keyword = when (entry) {
            is JsonPrimitive -> entry.contentOrNull
            is JsonObject -> entry["type"]?.jsonPrimitive?.contentOrNull
                ?: entry["value"]?.jsonPrimitive?.contentOrNull
            else -> null
        } ?: ValueExtractors.extractKeyword(entry)
        val normalized = keyword?.uppercase()?.replace("-", "_") ?: return default

        return when (normalized) {
            "BORDER_BOX" -> BackgroundBoxValue.BORDER_BOX
            "PADDING_BOX" -> BackgroundBoxValue.PADDING_BOX
            "CONTENT_BOX" -> BackgroundBoxValue.CONTENT_BOX
            "TEXT" -> BackgroundBoxValue.TEXT
            else -> default
        }
    }

    /** True if this extractor claims the property. */
    fun isBackgroundBoxProperty(type: String): Boolean {
        return type in setOf("BackgroundClip", "BackgroundOrigin")
    }
}
