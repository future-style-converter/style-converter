package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.HyphenateLimitZoneProperty
import app.irmodels.properties.typography.HyphenateLimitZoneValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Parser for `hyphenate-limit-zone` property.
 *
 * Values: <length-percentage>
 * Sets maximum amount of unfilled space before hyphenation
 */
object HyphenateLimitZonePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Try parsing as percentage first
        PercentageParser.parse(trimmed)?.let { percentage ->
            return HyphenateLimitZoneProperty(HyphenateLimitZoneValue.Percentage(percentage))
        }

        // Try parsing as length
        LengthParser.parse(trimmed)?.let { length ->
            return HyphenateLimitZoneProperty(HyphenateLimitZoneValue.Length(length))
        }

        return null
    }
}
