package com.styleconverter.test.style.rendering

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * Extracts zoom configuration from IR properties.
 */
object ZoomExtractor {

    fun extractZoomConfig(properties: List<Pair<String, JsonElement?>>): ZoomConfig {
        for ((type, data) in properties) {
            when (type) {
                "Zoom" -> return extractZoom(data)
            }
        }
        return ZoomConfig()
    }

    private fun extractZoom(data: JsonElement?): ZoomConfig {
        if (data == null) return ZoomConfig()

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                if (content == "normal") {
                    return ZoomConfig(zoom = 1.0f, isNormal = true)
                }
                val value = data.floatOrNull
                if (value != null) {
                    return ZoomConfig(zoom = value, isNormal = false)
                }
            }
            else -> {}
        }
        return ZoomConfig()
    }

    fun isZoomProperty(type: String): Boolean = type == "Zoom"
}
