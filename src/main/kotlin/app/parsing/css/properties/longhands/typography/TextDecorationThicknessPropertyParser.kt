package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextDecorationThicknessProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Parser for `text-decoration-thickness` property.
 *
 * Syntax: auto | from-font | <length> | <percentage>
 */
object TextDecorationThicknessPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val thickness = when (trimmed) {
            "auto" -> TextDecorationThicknessProperty.Thickness.Auto()
            "from-font" -> TextDecorationThicknessProperty.Thickness.FromFont()
            else -> {
                // Try parsing as percentage first (since it's more specific)
                val percentage = PercentageParser.parse(trimmed)
                if (percentage != null) {
                    TextDecorationThicknessProperty.Thickness.PercentageValue(percentage)
                } else {
                    // Try parsing as length
                    val length = LengthParser.parse(trimmed)
                    if (length != null) {
                        TextDecorationThicknessProperty.Thickness.LengthValue(length)
                    } else {
                        return null
                    }
                }
            }
        }

        return TextDecorationThicknessProperty(thickness)
    }
}
