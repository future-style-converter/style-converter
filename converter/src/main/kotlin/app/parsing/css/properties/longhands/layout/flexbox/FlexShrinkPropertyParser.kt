package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.FlexShrinkProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object FlexShrinkPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return FlexShrinkProperty.fromKeyword(lower)
        }

        // Handle expressions (calc, var, etc.)
        if (ExpressionDetector.containsExpression(lower)) {
            return FlexShrinkProperty.fromExpression(trimmed)
        }

        // Parse as number
        val number = lower.toDoubleOrNull() ?: return null
        return FlexShrinkProperty.fromNumber(number)
    }
}
