package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.properties.effects.MaskBorderWidthProperty
import app.irmodels.properties.effects.MaskBorderWidthValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object MaskBorderWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return MaskBorderWidthProperty(MaskBorderWidthValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return MaskBorderWidthProperty(MaskBorderWidthValue.Raw(trimmed))
        }

        // Handle auto keyword
        if (lower == "auto") {
            return MaskBorderWidthProperty(MaskBorderWidthValue.Auto)
        }

        // Parse multi-value (1-4 values)
        val parts = lower.split(Regex("\\s+"))
        if (parts.size in 2..4) {
            val (top, right, bottom, left) = when (parts.size) {
                2 -> listOf(parts[0], parts[1], parts[0], parts[1])
                3 -> listOf(parts[0], parts[1], parts[2], parts[1])
                4 -> listOf(parts[0], parts[1], parts[2], parts[3])
                else -> listOf(parts[0], parts[0], parts[0], parts[0])
            }
            return MaskBorderWidthProperty(MaskBorderWidthValue.Multi(top, right, bottom, left))
        }

        // Try parsing as number (unitless)
        val num = trimmed.toDoubleOrNull()
        if (num != null) {
            return MaskBorderWidthProperty(MaskBorderWidthValue.Number(num))
        }

        // Try parsing as length
        val length = LengthParser.parse(trimmed)
        if (length != null) {
            return MaskBorderWidthProperty(MaskBorderWidthValue.Length(length))
        }

        return MaskBorderWidthProperty(MaskBorderWidthValue.Raw(trimmed))
    }
}
