package app.parsing.css.properties.longhands.svg

import app.irmodels.IRNumber
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.svg.StrokeDashoffsetProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object StrokeDashoffsetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return StrokeDashoffsetProperty(StrokeDashoffsetProperty.DashoffsetValue.Keyword(lower))
        }

        // Handle percentage
        if (lower.endsWith("%")) {
            val percent = lower.dropLast(1).toDoubleOrNull() ?: return null
            return StrokeDashoffsetProperty(StrokeDashoffsetProperty.DashoffsetValue.PercentageValue(IRPercentage(percent)))
        }

        // Try parsing as length
        val length = LengthParser.parse(trimmed)
        if (length != null) {
            return StrokeDashoffsetProperty(length)
        }

        // Try parsing as unitless number (SVG allows this)
        val number = trimmed.toDoubleOrNull()
        if (number != null) {
            return StrokeDashoffsetProperty(StrokeDashoffsetProperty.DashoffsetValue.NumberValue(IRNumber(number)))
        }

        return null
    }
}
