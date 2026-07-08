package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollbarColorProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ColorParser

/**
 * Parser for `scrollbar-color` property.
 *
 * Syntax: auto | <color> <color>
 */
object ScrollbarColorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Check for 'auto' keyword
        if (trimmed == "auto") {
            return ScrollbarColorProperty(ScrollbarColorProperty.ScrollbarColor.Auto())
        }

        // Split into parts for two colors
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size != 2) return null

        // Parse thumb color (first value)
        val thumb = ColorParser.parse(parts[0]) ?: return null

        // Parse track color (second value)
        val track = ColorParser.parse(parts[1]) ?: return null

        return ScrollbarColorProperty(
            ScrollbarColorProperty.ScrollbarColor.Colors(thumb, track)
        )
    }
}
