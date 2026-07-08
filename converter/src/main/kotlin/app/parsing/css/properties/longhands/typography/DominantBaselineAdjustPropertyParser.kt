package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.DominantBaselineAdjustProperty
import app.irmodels.properties.typography.DominantBaselineAdjustValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

object DominantBaselineAdjustPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Check keyword first
        if (trimmed == "auto") {
            return DominantBaselineAdjustProperty(DominantBaselineAdjustValue.Auto)
        }

        // Try parsing as percentage
        PercentageParser.parse(trimmed)?.let { percentage ->
            return DominantBaselineAdjustProperty(DominantBaselineAdjustValue.Percentage(percentage))
        }

        // Try parsing as length
        LengthParser.parse(trimmed)?.let { length ->
            return DominantBaselineAdjustProperty(DominantBaselineAdjustValue.Length(length))
        }

        return null
    }
}
