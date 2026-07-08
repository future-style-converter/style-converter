package app.parsing.css.properties.longhands.color

import app.irmodels.IRProperty
import app.irmodels.properties.color.Opacity
import app.irmodels.properties.color.OpacityProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for `opacity` property.
 */
object OpacityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return OpacityProperty(Opacity.fromGlobalKeyword(lower))
        }

        // Handle expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return OpacityProperty(Opacity.fromExpression(trimmed))
        }

        // Parse as percentage
        if (lower.endsWith("%")) {
            val percent = lower.dropLast(1).toDoubleOrNull() ?: return null
            return OpacityProperty(Opacity.fromPercentage(percent))
        }

        // Parse as number
        val opacity = trimmed.toDoubleOrNull() ?: return null
        return OpacityProperty(Opacity.fromNumber(opacity))
    }
}
