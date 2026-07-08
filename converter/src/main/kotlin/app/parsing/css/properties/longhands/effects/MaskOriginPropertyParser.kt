package app.parsing.css.properties.longhands.effects

import app.irmodels.properties.effects.MaskOriginProperty
import app.irmodels.properties.effects.MaskOriginValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for the `mask-origin` CSS property.
 *
 * Accepts:
 * - content-box
 * - padding-box
 * - border-box
 * - fill-box (SVG)
 * - stroke-box (SVG)
 * - view-box (SVG)
 */
object MaskOriginPropertyParser : PropertyParser {

    override fun parse(value: String): MaskOriginProperty? {
        val trimmed = value.trim().lowercase()

        val originValue = when (trimmed) {
            "content-box" -> MaskOriginValue.CONTENT_BOX
            "padding-box" -> MaskOriginValue.PADDING_BOX
            "border-box" -> MaskOriginValue.BORDER_BOX
            "fill-box" -> MaskOriginValue.FILL_BOX
            "stroke-box" -> MaskOriginValue.STROKE_BOX
            "view-box" -> MaskOriginValue.VIEW_BOX
            else -> return null
        }

        return MaskOriginProperty(originValue)
    }
}
