package com.styleconverter.test.style.layout

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts float and clear configuration from IR properties.
 */
object FloatExtractor {

    /**
     * Extract float configuration from property pairs.
     */
    fun extractFloatConfig(properties: List<Pair<String, JsonElement?>>): FloatConfig {
        var float = FloatValue.NONE
        var clear = ClearValue.NONE

        for ((type, data) in properties) {
            when (type) {
                "Float" -> float = extractFloat(data)
                "Clear" -> clear = extractClear(data)
            }
        }

        return FloatConfig(
            float = float,
            clear = clear
        )
    }

    private fun extractFloat(data: JsonElement?): FloatValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return FloatValue.NONE

        return when (keyword) {
            "NONE" -> FloatValue.NONE
            "LEFT" -> FloatValue.LEFT
            "RIGHT" -> FloatValue.RIGHT
            "INLINE_START" -> FloatValue.INLINE_START
            "INLINE_END" -> FloatValue.INLINE_END
            else -> FloatValue.NONE
        }
    }

    private fun extractClear(data: JsonElement?): ClearValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()?.replace("-", "_")
            ?: return ClearValue.NONE

        return when (keyword) {
            "NONE" -> ClearValue.NONE
            "LEFT" -> ClearValue.LEFT
            "RIGHT" -> ClearValue.RIGHT
            "BOTH" -> ClearValue.BOTH
            "INLINE_START" -> ClearValue.INLINE_START
            "INLINE_END" -> ClearValue.INLINE_END
            else -> ClearValue.NONE
        }
    }

    /**
     * Check if a property type is float-related.
     */
    fun isFloatProperty(type: String): Boolean {
        return type in FLOAT_PROPERTIES
    }

    private val FLOAT_PROPERTIES = setOf("Float", "Clear")
}
