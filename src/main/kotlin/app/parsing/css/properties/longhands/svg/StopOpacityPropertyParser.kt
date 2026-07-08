package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.StopOpacityProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.NumberParser

object StopOpacityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val number = NumberParser.parse(value.trim()) ?: return null

        // Opacity must be between 0.0 and 1.0
        if (number.value < 0.0 || number.value > 1.0) return null

        return StopOpacityProperty(number)
    }
}
