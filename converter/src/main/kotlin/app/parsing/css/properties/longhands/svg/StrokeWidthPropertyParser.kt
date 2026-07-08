package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.StrokeWidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object StrokeWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val length = LengthParser.parse(value) ?: return null
        return StrokeWidthProperty(length)
    }
}
