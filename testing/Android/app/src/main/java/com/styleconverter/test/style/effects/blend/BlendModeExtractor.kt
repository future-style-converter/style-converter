package com.styleconverter.test.style.effects.blend

// Pulls mix-blend-mode (single string) and background-blend-mode (list of
// strings) out of IR property pairs. See CLAUDE.md for fixture shapes:
//   "MixBlendMode" -> "MULTIPLY"                       (scalar primitive)
//   "BackgroundBlendMode" -> ["MULTIPLY", "SCREEN"]     (array of primitives)

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts mix-blend-mode + background-blend-mode configuration from IR.
 */
object BlendModeExtractor {

    /**
     * Extract blend-mode configuration from IR property pairs.
     *
     * @param properties List of (type, data) pairs
     * @return BlendModeConfig with both mix and background blend modes populated.
     */
    fun extractBlendModeConfig(properties: List<Pair<String, JsonElement?>>): BlendModeConfig {
        // Start from defaults — scalar null, empty layer list.
        var mixBlendMode: androidx.compose.ui.graphics.BlendMode? = null
        var backgroundBlendModes: List<androidx.compose.ui.graphics.BlendMode> = emptyList()

        for ((type, data) in properties) {
            when (type) {
                "MixBlendMode" -> {
                    // Scalar primitive — may be a JsonPrimitive directly or a
                    // JsonObject with a string-typed field. Extract the raw string.
                    if (data != null) {
                        val raw = (data as? JsonPrimitive)?.contentOrNull ?: data.jsonPrimitive.contentOrNull
                        if (raw != null) mixBlendMode = BlendModeMapping.fromCssValue(raw)
                    }
                }
                "BackgroundBlendMode" -> {
                    // List of per-layer blend modes. Skip silently when the IR
                    // gave us something unexpected (non-array) — mis-typed data
                    // shouldn't nuke the whole extraction pass.
                    val arr = data as? JsonArray ?: continue
                    backgroundBlendModes = arr.mapNotNull { el ->
                        val raw = (el as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                        BlendModeMapping.fromCssValue(raw)
                    }
                }
            }
        }

        return BlendModeConfig(
            blendMode = mixBlendMode,
            backgroundBlendModes = backgroundBlendModes
        )
    }

    /** True if the property type is one this extractor claims. */
    fun isBlendModeProperty(type: String): Boolean {
        return type == "MixBlendMode" || type == "BackgroundBlendMode"
    }
}
