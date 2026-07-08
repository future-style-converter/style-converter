package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.FlexGrowProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object FlexGrowPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return FlexGrowProperty.fromKeyword(lower)
        }

        // Handle expressions (calc, var, etc.)
        if (ExpressionDetector.containsExpression(lower)) {
            return FlexGrowProperty.fromExpression(trimmed)
        }

        // Parse as number
        val number = lower.toDoubleOrNull() ?: return null
        return FlexGrowProperty.fromNumber(number)
    }
}
