package com.styleconverter.test.style.math

import com.styleconverter.test.style.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Extracts math typography configuration from IR properties.
 */
object MathTypographyExtractor {

    fun extractMathTypographyConfig(properties: List<Pair<String, JsonElement?>>): MathTypographyConfig {
        var mathStyle = MathStyleValue.NORMAL
        var mathShift = MathShiftValue.NORMAL
        var mathDepth = 0

        for ((type, data) in properties) {
            when (type) {
                "MathStyle" -> mathStyle = extractMathStyle(data)
                "MathShift" -> mathShift = extractMathShift(data)
                "MathDepth" -> mathDepth = extractMathDepth(data)
            }
        }

        return MathTypographyConfig(
            mathStyle = mathStyle,
            mathShift = mathShift,
            mathDepth = mathDepth
        )
    }

    private fun extractMathStyle(data: JsonElement?): MathStyleValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return MathStyleValue.NORMAL

        return when (keyword) {
            "NORMAL" -> MathStyleValue.NORMAL
            "COMPACT" -> MathStyleValue.COMPACT
            else -> MathStyleValue.NORMAL
        }
    }

    private fun extractMathShift(data: JsonElement?): MathShiftValue {
        val keyword = ValueExtractors.extractKeyword(data)?.uppercase()
            ?: return MathShiftValue.NORMAL

        return when (keyword) {
            "NORMAL" -> MathShiftValue.NORMAL
            "COMPACT" -> MathShiftValue.COMPACT
            else -> MathShiftValue.NORMAL
        }
    }

    private fun extractMathDepth(data: JsonElement?): Int {
        if (data == null) return 0
        return when (data) {
            is JsonPrimitive -> data.intOrNull ?: 0
            else -> 0
        }
    }

    fun isMathTypographyProperty(type: String): Boolean {
        return type in setOf("MathStyle", "MathShift", "MathDepth")
    }
}
