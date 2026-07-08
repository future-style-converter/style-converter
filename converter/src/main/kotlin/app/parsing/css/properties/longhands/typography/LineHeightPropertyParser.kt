package app.parsing.css.properties.longhands.typography

import app.irmodels.*
import app.irmodels.properties.typography.LineHeight
import app.irmodels.properties.typography.LineHeightProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object LineHeightPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle 'normal' keyword
        if (lower == "normal") {
            return LineHeightProperty(LineHeight.normal())
        }

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return LineHeightProperty(LineHeight.fromKeyword(lower))
        }

        // Handle expressions (calc, var, etc.)
        if (ExpressionDetector.containsExpression(lower)) {
            return LineHeightProperty(LineHeight.fromExpression(trimmed))
        }

        // Try unitless number first (most common for line-height)
        val number = lower.toDoubleOrNull()
        if (number != null) {
            return LineHeightProperty(LineHeight.fromNumber(number))
        }

        // Try length/percentage
        val length = LengthParser.parse(lower)
        if (length != null) {
            val lineHeight = if (length.unit == IRLength.LengthUnit.PERCENT) {
                LineHeight.fromPercentage(IRPercentage(length.value))
            } else {
                LineHeight.fromLength(length)
            }
            return LineHeightProperty(lineHeight)
        }

        return null
    }
}
