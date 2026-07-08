package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.spacing.GapProperty
import app.irmodels.properties.spacing.RowGapProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

object RowGapPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle "normal" keyword
        if (lowered == "normal") {
            return RowGapProperty(GapProperty.LengthPercentageOrNormal.Normal())
        }

        // Try to parse as percentage first
        val percentage = PercentageParser.parse(lowered)
        if (percentage != null) {
            return RowGapProperty(GapProperty.LengthPercentageOrNormal.Percentage(percentage))
        }

        // Try to parse as length
        val length = LengthParser.parse(lowered)
        if (length != null) {
            return RowGapProperty(GapProperty.LengthPercentageOrNormal.Length(length))
        }

        // Handle calc(), var(), clamp() and other complex values as raw
        if (lowered.contains("(")) {
            return RowGapProperty(GapProperty.LengthPercentageOrNormal.Raw(trimmed))
        }

        return null
    }
}
