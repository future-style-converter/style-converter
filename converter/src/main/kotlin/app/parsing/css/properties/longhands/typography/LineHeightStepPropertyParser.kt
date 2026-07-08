package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.LineHeightStepProperty
import app.irmodels.properties.typography.LineHeightStepValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object LineHeightStepPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val stepValue = when (trimmed) {
            "none" -> LineHeightStepValue.None
            else -> {
                // Try to parse as length
                val length = LengthParser.parse(trimmed) ?: return null
                LineHeightStepValue.Length(length)
            }
        }

        return LineHeightStepProperty(stepValue)
    }
}
