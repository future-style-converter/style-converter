package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextDecorationStyleProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `text-decoration-style` property.
 *
 * Syntax: solid | double | dotted | dashed | wavy
 */
object TextDecorationStylePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val style = when (trimmed) {
            "solid" -> TextDecorationStyleProperty.DecorationStyle.SOLID
            "double" -> TextDecorationStyleProperty.DecorationStyle.DOUBLE
            "dotted" -> TextDecorationStyleProperty.DecorationStyle.DOTTED
            "dashed" -> TextDecorationStyleProperty.DecorationStyle.DASHED
            "wavy" -> TextDecorationStyleProperty.DecorationStyle.WAVY
            else -> return null
        }

        return TextDecorationStyleProperty(style)
    }
}
