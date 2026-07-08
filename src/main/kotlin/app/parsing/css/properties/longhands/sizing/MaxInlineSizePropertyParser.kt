package app.parsing.css.properties.longhands.sizing

import app.irmodels.*
import app.irmodels.properties.sizing.MaxInlineSizeProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object MaxInlineSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return MaxInlineSizeProperty(SizeValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return MaxInlineSizeProperty(SizeValue.Expression(trimmed))
        }

        val sizeValue = when (lower) {
            "none" -> SizeValue.None
            "auto" -> SizeValue.Auto
            "min-content" -> SizeValue.MinContent
            "max-content" -> SizeValue.MaxContent
            "fit-content" -> SizeValue.FitContent(null)
            else -> {
                val length = LengthParser.parse(lower)
                if (length == null) {
                    return MaxInlineSizeProperty(SizeValue.Expression(trimmed))
                }
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    SizeValue.PercentageValue(IRPercentage(length.value))
                } else {
                    SizeValue.LengthValue(length)
                }
            }
        }
        return MaxInlineSizeProperty(sizeValue)
    }
}
