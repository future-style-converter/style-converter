package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.HyphenateLimitLinesProperty
import app.irmodels.properties.typography.HyphenateLimitLinesValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.NumberParser

/**
 * Parser for `hyphenate-limit-lines` property.
 *
 * Values: no-limit | <integer>
 * Sets maximum number of consecutive hyphenated lines
 */
object HyphenateLimitLinesPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle 'no-limit' keyword
        if (trimmed == "no-limit") {
            return HyphenateLimitLinesProperty(HyphenateLimitLinesValue.NoLimit)
        }

        // Parse integer value
        val intValue = NumberParser.parseInt(trimmed) ?: return null

        // Must be positive integer
        if (intValue < 0) return null

        return HyphenateLimitLinesProperty(HyphenateLimitLinesValue.Number(intValue))
    }
}
