package app.parsing.css.properties.longhands.background

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.color.ColorProperty
import app.parsing.css.properties.primitiveParsers.ColorParser
/**
 * Parser for the `background-color` property.
 */
object BackgroundColorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val color = ColorParser.parse(value) ?: return null
        return app.irmodels.properties.color.BackgroundColorProperty(color)
    }
}
