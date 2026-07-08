package app.parsing.css.properties.longhands.effects

import app.irmodels.properties.effects.MaskClipProperty
import app.irmodels.properties.effects.MaskClipValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for the `mask-clip` CSS property.
 *
 * Accepts:
 * - content-box
 * - padding-box
 * - border-box
 * - fill-box (SVG)
 * - stroke-box (SVG)
 * - view-box (SVG)
 * - no-clip
 */
object MaskClipPropertyParser : PropertyParser {

    override fun parse(value: String): MaskClipProperty? {
        val trimmed = value.trim().lowercase()

        val clipValue = when (trimmed) {
            "content-box" -> MaskClipValue.CONTENT_BOX
            "padding-box" -> MaskClipValue.PADDING_BOX
            "border-box" -> MaskClipValue.BORDER_BOX
            "fill-box" -> MaskClipValue.FILL_BOX
            "stroke-box" -> MaskClipValue.STROKE_BOX
            "view-box" -> MaskClipValue.VIEW_BOX
            "no-clip" -> MaskClipValue.NO_CLIP
            else -> return null
        }

        return MaskClipProperty(clipValue)
    }
}
