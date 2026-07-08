package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.BoxOrientProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `-webkit-box-orient` property.
 *
 * This is a legacy flexbox property used for multi-line text truncation.
 * Values: horizontal | vertical | inline-axis | block-axis
 */
object BoxOrientPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val orient = when (value.trim().lowercase()) {
            "horizontal" -> BoxOrientProperty.BoxOrient.HORIZONTAL
            "vertical" -> BoxOrientProperty.BoxOrient.VERTICAL
            "inline-axis" -> BoxOrientProperty.BoxOrient.INLINE_AXIS
            "block-axis" -> BoxOrientProperty.BoxOrient.BLOCK_AXIS
            else -> return null
        }
        return BoxOrientProperty(orient)
    }
}
