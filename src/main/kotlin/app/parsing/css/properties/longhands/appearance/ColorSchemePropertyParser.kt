package app.parsing.css.properties.longhands.appearance

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.color.ColorSchemeProperty

/**
 * Parser for `color-scheme` property.
 *
 * Syntax: normal | [ light | dark | only ]+
 */
object ColorSchemePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "normal") {
            return ColorSchemeProperty(listOf(ColorSchemeProperty.ColorScheme.NORMAL))
        }
        val parts = trimmed.split(Regex("""\s+"""))
        val schemes = parts.mapNotNull { part ->
            when (part) {
                "light" -> ColorSchemeProperty.ColorScheme.LIGHT
                "dark" -> ColorSchemeProperty.ColorScheme.DARK
                "only" -> ColorSchemeProperty.ColorScheme.ONLY
                else -> null
            }
        }
        if (schemes.isEmpty()) return null
        return ColorSchemeProperty(schemes)
    }
}
