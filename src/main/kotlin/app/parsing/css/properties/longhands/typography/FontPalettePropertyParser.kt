package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontPaletteProperty
import app.irmodels.properties.typography.FontPaletteValue
import app.parsing.css.properties.longhands.PropertyParser

object FontPalettePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val paletteValue = when (trimmed) {
            "normal" -> FontPaletteValue.Normal
            "light" -> FontPaletteValue.Light
            "dark" -> FontPaletteValue.Dark
            else -> FontPaletteValue.Custom(value.trim())
        }

        return FontPaletteProperty(paletteValue)
    }
}
