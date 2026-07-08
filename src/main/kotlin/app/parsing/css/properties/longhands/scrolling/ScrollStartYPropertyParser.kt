package app.parsing.css.properties.longhands.scrolling

import app.irmodels.*
import app.irmodels.properties.scrolling.ScrollStartYProperty
import app.irmodels.properties.scrolling.ScrollStartValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * Parser for `scroll-start-y` property.
 *
 * Syntax: auto | start | end | center | <length-percentage>
 */
object ScrollStartYPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Check for keyword values
        val keywordValue = when (trimmed) {
            "auto" -> ScrollStartValue.Auto
            "start" -> ScrollStartValue.Start
            "end" -> ScrollStartValue.End
            "center" -> ScrollStartValue.Center
            else -> null
        }

        if (keywordValue != null) {
            return ScrollStartYProperty(keywordValue)
        }

        // Try to parse as length or percentage
        val length = LengthParser.parse(trimmed) ?: return null

        val scrollStartValue = if (length.unit == IRLength.LengthUnit.PERCENT) {
            ScrollStartValue.Percentage(IRPercentage(length.value))
        } else {
            ScrollStartValue.Length(length)
        }

        return ScrollStartYProperty(scrollStartValue)
    }
}
