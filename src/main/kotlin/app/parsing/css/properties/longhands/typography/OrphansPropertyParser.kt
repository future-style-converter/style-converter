package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.IRNumber
import app.irmodels.properties.typography.*
import app.parsing.css.properties.primitiveParsers.ColorParser
/**
 * Parser for `orphans` property.
 *
 * Syntax: <integer>
 */
object OrphansPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val count = trimmed.toIntOrNull() ?: return null
        if (count < 1) return null
        return OrphansProperty(IRNumber(count.toDouble()))
    }
}
