package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontVariantAlternatesProperty
import app.irmodels.properties.typography.FontVariantAlternatesValue
import app.parsing.css.properties.longhands.PropertyParser

object FontVariantAlternatesPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "normal") {
            return FontVariantAlternatesProperty(listOf(FontVariantAlternatesValue.NORMAL))
        }

        val parts = trimmed.split(Regex("\\s+"))
        val values = parts.mapNotNull { part ->
            when {
                part == "historical-forms" -> FontVariantAlternatesValue.HISTORICAL_FORMS
                part.startsWith("stylistic(") -> FontVariantAlternatesValue.STYLISTIC
                part.startsWith("styleset(") -> FontVariantAlternatesValue.STYLESET
                part.startsWith("character-variant(") -> FontVariantAlternatesValue.CHARACTER_VARIANT
                part.startsWith("swash(") -> FontVariantAlternatesValue.SWASH
                part.startsWith("ornaments(") -> FontVariantAlternatesValue.ORNAMENTS
                part.startsWith("annotation(") -> FontVariantAlternatesValue.ANNOTATION
                else -> null
            }
        }

        return if (values.isNotEmpty()) FontVariantAlternatesProperty(values) else null
    }
}
