package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.OffsetDistanceProperty
import app.irmodels.properties.layout.advanced.OffsetDistanceValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object OffsetDistancePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return OffsetDistanceProperty(OffsetDistanceValue.Keyword(lowered))
        }

        // Check for var() or other complex expressions
        if (ExpressionDetector.containsExpression(lowered)) {
            return OffsetDistanceProperty(OffsetDistanceValue.Raw(trimmed))
        }

        return when {
            trimmed.endsWith("%") -> {
                val percent = trimmed.removeSuffix("%").toDoubleOrNull()
                    ?: return OffsetDistanceProperty(OffsetDistanceValue.Raw(trimmed))
                OffsetDistanceProperty(OffsetDistanceValue.Percentage(IRPercentage(percent)))
            }
            else -> {
                val length = LengthParser.parse(trimmed)
                    ?: return OffsetDistanceProperty(OffsetDistanceValue.Raw(trimmed))
                OffsetDistanceProperty(OffsetDistanceValue.Length(length))
            }
        }
    }
}
