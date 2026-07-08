package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollbarWidthProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `scrollbar-width` property.
 *
 * Syntax: auto | thin | none
 */
object ScrollbarWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val width = when (trimmed) {
            "auto" -> ScrollbarWidthProperty.ScrollbarWidth.AUTO
            "thin" -> ScrollbarWidthProperty.ScrollbarWidth.THIN
            "none" -> ScrollbarWidthProperty.ScrollbarWidth.NONE
            else -> return null
        }

        return ScrollbarWidthProperty(width)
    }
}
