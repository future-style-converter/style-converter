package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.spacing.GapProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

object GapPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle "normal" keyword
        if (trimmed == "normal") {
            return GapProperty(GapProperty.LengthPercentageOrNormal.Normal())
        }

        // Try to parse as percentage first
        val percentage = PercentageParser.parse(trimmed)
        if (percentage != null) {
            return GapProperty(GapProperty.LengthPercentageOrNormal.Percentage(percentage))
        }

        // Try to parse as length
        val length = LengthParser.parse(trimmed)
        if (length != null) {
            return GapProperty(GapProperty.LengthPercentageOrNormal.Length(length))
        }

        return null
    }
}
