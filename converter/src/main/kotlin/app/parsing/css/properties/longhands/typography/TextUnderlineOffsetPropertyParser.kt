package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextUnderlineOffsetProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Parser for `text-underline-offset` property.
 *
 * Syntax: auto | <length> | <percentage>
 */
object TextUnderlineOffsetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val offset = when (trimmed) {
            "auto" -> TextUnderlineOffsetProperty.Offset.Auto()
            else -> {
                // Try parsing as percentage first (since it's more specific)
                val percentage = PercentageParser.parse(trimmed)
                if (percentage != null) {
                    TextUnderlineOffsetProperty.Offset.PercentageValue(percentage)
                } else {
                    // Try parsing as length
                    val length = LengthParser.parse(trimmed)
                    if (length != null) {
                        TextUnderlineOffsetProperty.Offset.LengthValue(length)
                    } else {
                        return null
                    }
                }
            }
        }

        return TextUnderlineOffsetProperty(offset)
    }
}
