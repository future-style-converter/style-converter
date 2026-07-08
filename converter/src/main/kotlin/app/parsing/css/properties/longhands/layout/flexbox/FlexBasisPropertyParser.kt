package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.FlexBasisProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords
import app.parsing.css.properties.primitiveParsers.LengthParser

object FlexBasisPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle keywords
        return when (lower) {
            "auto" -> FlexBasisProperty.auto()
            "content" -> FlexBasisProperty.content()
            "max-content" -> FlexBasisProperty.maxContent()
            "min-content" -> FlexBasisProperty.minContent()
            "fit-content" -> FlexBasisProperty.fitContent()
            else -> {
                // Handle global keywords
                if (GlobalKeywords.isGlobalKeyword(lower)) {
                    return FlexBasisProperty.fromKeyword(lower)
                }
                // Check for expressions (calc, var, etc.)
                if (ExpressionDetector.containsExpression(lower)) {
                    return FlexBasisProperty.fromExpression(trimmed)
                }
                // Check for percentage
                if (lower.endsWith("%")) {
                    val percentValue = lower.dropLast(1).toDoubleOrNull() ?: return null
                    return FlexBasisProperty.fromPercentage(IRPercentage(percentValue))
                }
                // Parse as length
                val length = LengthParser.parse(lower) ?: return null
                FlexBasisProperty.fromLength(length)
            }
        }
    }
}
