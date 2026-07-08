package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollSnapStopProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `scroll-snap-stop` property.
 *
 * Syntax: normal | always
 */
object ScrollSnapStopPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val stop = when (trimmed) {
            "normal" -> ScrollSnapStopProperty.SnapStop.NORMAL
            "always" -> ScrollSnapStopProperty.SnapStop.ALWAYS
            else -> return null
        }

        return ScrollSnapStopProperty(stop)
    }
}
