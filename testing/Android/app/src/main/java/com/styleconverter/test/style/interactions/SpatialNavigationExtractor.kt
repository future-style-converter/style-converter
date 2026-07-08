package com.styleconverter.test.style.interactions

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts spatial navigation configuration from IR properties.
 */
object SpatialNavigationExtractor {

    fun extractSpatialNavigationConfig(properties: List<Pair<String, JsonElement?>>): SpatialNavigationConfig {
        var navUp: NavTargetValue = NavTargetValue.Auto
        var navRight: NavTargetValue = NavTargetValue.Auto
        var navDown: NavTargetValue = NavTargetValue.Auto
        var navLeft: NavTargetValue = NavTargetValue.Auto

        for ((type, data) in properties) {
            when (type) {
                "NavUp" -> navUp = extractNavTarget(data)
                "NavRight" -> navRight = extractNavTarget(data)
                "NavDown" -> navDown = extractNavTarget(data)
                "NavLeft" -> navLeft = extractNavTarget(data)
            }
        }

        return SpatialNavigationConfig(
            navUp = navUp,
            navRight = navRight,
            navDown = navDown,
            navLeft = navLeft
        )
    }

    private fun extractNavTarget(data: JsonElement?): NavTargetValue {
        if (data == null) return NavTargetValue.Auto

        when (data) {
            is JsonPrimitive -> {
                val content = data.contentOrNull?.lowercase()
                return when (content) {
                    "auto" -> NavTargetValue.Auto
                    "none" -> NavTargetValue.None
                    else -> NavTargetValue.Selector(content ?: "auto")
                }
            }
            is JsonObject -> {
                val type = data["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
                return when (type) {
                    "auto" -> NavTargetValue.Auto
                    "none" -> NavTargetValue.None
                    "selector" -> {
                        val selector = data["selector"]?.jsonPrimitive?.contentOrNull
                            ?: data["value"]?.jsonPrimitive?.contentOrNull
                            ?: ""
                        NavTargetValue.Selector(selector)
                    }
                    else -> NavTargetValue.Auto
                }
            }
            else -> return NavTargetValue.Auto
        }
    }

    fun isSpatialNavigationProperty(type: String): Boolean {
        return type in setOf("NavUp", "NavRight", "NavDown", "NavLeft")
    }
}
