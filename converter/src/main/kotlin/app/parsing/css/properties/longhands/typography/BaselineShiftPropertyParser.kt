package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.BaselineShiftProperty
import app.irmodels.properties.typography.BaselineShiftValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

object BaselineShiftPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Check keywords first
        val shiftValue = when (trimmed) {
            "baseline" -> BaselineShiftValue.Baseline
            "sub" -> BaselineShiftValue.Sub
            "super" -> BaselineShiftValue.Super
            else -> {
                // Try parsing as percentage
                PercentageParser.parse(trimmed)?.let { percentage ->
                    return BaselineShiftProperty(BaselineShiftValue.Percentage(percentage))
                }

                // Try parsing as length
                LengthParser.parse(trimmed)?.let { length ->
                    return BaselineShiftProperty(BaselineShiftValue.Length(length))
                }

                return null
            }
        }

        return BaselineShiftProperty(shiftValue)
    }
}
