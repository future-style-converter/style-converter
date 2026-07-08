package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.OverflowAnchorProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `overflow-anchor` property.
 *
 * Syntax: auto | none
 */
object OverflowAnchorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val anchor = when (trimmed) {
            "auto" -> OverflowAnchorProperty.OverflowAnchor.AUTO
            "none" -> OverflowAnchorProperty.OverflowAnchor.NONE
            else -> return null
        }

        return OverflowAnchorProperty(anchor)
    }
}
