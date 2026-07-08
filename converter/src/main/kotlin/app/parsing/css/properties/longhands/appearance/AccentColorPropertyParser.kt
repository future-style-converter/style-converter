package app.parsing.css.properties.longhands.appearance

import app.irmodels.IRProperty
import app.irmodels.properties.color.AccentColorProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ColorParser

/**
 * Parser for `accent-color` property.
 *
 * Syntax: auto | <color>
 */
object AccentColorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "auto") {
            return AccentColorProperty(AccentColorProperty.AccentColor.Auto())
        }

        val color = ColorParser.parse(value) ?: return null
        return AccentColorProperty(AccentColorProperty.AccentColor.Color(color))
    }
}
