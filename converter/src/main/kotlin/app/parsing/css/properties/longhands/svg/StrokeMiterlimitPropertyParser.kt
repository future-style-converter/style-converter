package app.parsing.css.properties.longhands.svg

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.svg.StrokeMiterlimitProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.NumberParser

object StrokeMiterlimitPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val number = NumberParser.parse(value.trim()) ?: return null

        // Miterlimit must be >= 1.0
        if (number.value < 1.0) return null

        return StrokeMiterlimitProperty(number)
    }
}
