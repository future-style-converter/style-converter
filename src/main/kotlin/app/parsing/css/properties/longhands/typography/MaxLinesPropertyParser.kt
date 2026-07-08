package app.parsing.css.properties.longhands.typography

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.typography.MaxLinesProperty
import app.irmodels.properties.typography.MaxLinesValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.NumberParser

object MaxLinesPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val maxLinesValue = when (trimmed) {
            "none" -> MaxLinesValue.None
            else -> {
                // Try to parse as integer
                val number = NumberParser.parseInt(trimmed)
                if (number != null && number > 0) {
                    MaxLinesValue.Count(IRNumber(number.toDouble()))
                } else {
                    return null
                }
            }
        }

        return MaxLinesProperty(maxLinesValue)
    }
}
