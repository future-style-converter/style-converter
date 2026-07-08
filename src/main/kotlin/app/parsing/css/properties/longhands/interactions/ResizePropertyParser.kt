package app.parsing.css.properties.longhands.interactions

import app.irmodels.IRProperty
import app.irmodels.properties.interactions.ResizeProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for the `resize` property.
 *
 * Specifies whether an element is resizable and in which directions.
 *
 * Valid values:
 * - none: Element is not resizable
 * - both: Element is resizable in both horizontal and vertical directions
 * - horizontal: Element is resizable horizontally
 * - vertical: Element is resizable vertically
 * - block: Element is resizable in the block direction
 * - inline: Element is resizable in the inline direction
 */
object ResizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val resize = when (trimmed) {
            "none" -> ResizeProperty.Resize.NONE
            "both" -> ResizeProperty.Resize.BOTH
            "horizontal" -> ResizeProperty.Resize.HORIZONTAL
            "vertical" -> ResizeProperty.Resize.VERTICAL
            "block" -> ResizeProperty.Resize.BLOCK
            "inline" -> ResizeProperty.Resize.INLINE
            else -> return null
        }
        return ResizeProperty(resize)
    }
}
