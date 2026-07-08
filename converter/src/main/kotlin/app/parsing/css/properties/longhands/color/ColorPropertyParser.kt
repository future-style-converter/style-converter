package app.parsing.css.properties.longhands.color

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.color.ColorProperty
import app.parsing.css.properties.primitiveParsers.ColorParser
/**
 * Parser for the `color` property (text color).
 *
 * Examples:
 * - "#FFFFFF" → ColorProperty(IRColor.Hex("#FFFFFF"))
 * - "rgb(255, 255, 255)" → ColorProperty(IRColor.RGB(...))
 * - "red" → ColorProperty(IRColor.Named("red"))
 */
object ColorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val color = ColorParser.parse(value) ?: return null
        return ColorProperty(color)
    }
}
