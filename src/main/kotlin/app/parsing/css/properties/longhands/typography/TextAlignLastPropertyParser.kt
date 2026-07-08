package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextAlignLastProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for the `text-align-last` property.
 *
 * Specifies how the last line of a block or line before a forced line break is aligned.
 *
 * Valid values:
 * - auto: The last line is aligned according to the text-align value
 * - start: Align to the start edge
 * - end: Align to the end edge
 * - left: Align to the left
 * - right: Align to the right
 * - center: Center the text
 * - justify: Justify the text
 */
object TextAlignLastPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val alignment = when (trimmed) {
            "auto" -> TextAlignLastProperty.TextAlignLast.AUTO
            "start" -> TextAlignLastProperty.TextAlignLast.START
            "end" -> TextAlignLastProperty.TextAlignLast.END
            "left" -> TextAlignLastProperty.TextAlignLast.LEFT
            "right" -> TextAlignLastProperty.TextAlignLast.RIGHT
            "center" -> TextAlignLastProperty.TextAlignLast.CENTER
            "justify" -> TextAlignLastProperty.TextAlignLast.JUSTIFY
            else -> return null
        }
        return TextAlignLastProperty(alignment)
    }
}
