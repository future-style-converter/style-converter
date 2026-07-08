package app.parsing.css.properties.longhands.sizing

import app.irmodels.*
import app.irmodels.properties.sizing.BlockSizeProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object BlockSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return BlockSizeProperty(SizeValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return BlockSizeProperty(SizeValue.Expression(trimmed))
        }

        val sizeValue = when {
            lower == "auto" -> SizeValue.Auto
            lower == "min-content" -> SizeValue.MinContent
            lower == "max-content" -> SizeValue.MaxContent
            lower.startsWith("fit-content(") && lower.endsWith(")") -> {
                val sizeStr = lower.substring(12, lower.length - 1)
                val size = LengthParser.parse(sizeStr)
                SizeValue.FitContent(size)
            }
            else -> {
                val length = LengthParser.parse(lower)
                if (length == null) {
                    return BlockSizeProperty(SizeValue.Expression(trimmed))
                }
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    SizeValue.PercentageValue(IRPercentage(length.value))
                } else {
                    SizeValue.LengthValue(length)
                }
            }
        }
        return BlockSizeProperty(sizeValue)
    }
}
