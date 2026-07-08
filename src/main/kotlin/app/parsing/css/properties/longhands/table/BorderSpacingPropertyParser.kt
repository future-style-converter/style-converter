package app.parsing.css.properties.longhands.table

import app.irmodels.IRProperty
import app.irmodels.properties.table.BorderSpacingProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object BorderSpacingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split(Regex("\\s+"))

        return when (parts.size) {
            1 -> {
                val length = LengthParser.parse(parts[0]) ?: return null
                BorderSpacingProperty(BorderSpacingProperty.Spacing.Single(length))
            }
            2 -> {
                val horizontal = LengthParser.parse(parts[0]) ?: return null
                val vertical = LengthParser.parse(parts[1]) ?: return null
                BorderSpacingProperty(BorderSpacingProperty.Spacing.TwoValues(horizontal, vertical))
            }
            else -> null
        }
    }
}
