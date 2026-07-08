package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.StopColorProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ColorParser

object StopColorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val color = ColorParser.parse(value.trim()) ?: return null
        return StopColorProperty(color)
    }
}
