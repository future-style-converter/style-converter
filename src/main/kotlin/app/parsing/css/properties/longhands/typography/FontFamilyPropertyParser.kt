package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontFamilyProperty
import app.parsing.css.properties.longhands.PropertyParser

object FontFamilyPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Split by commas to get multiple families
        val familyStrings = trimmed.split(",").map { it.trim() }
        if (familyStrings.isEmpty()) return null

        val families = familyStrings.mapNotNull { parseFamily(it) }
        if (families.isEmpty()) return null

        return FontFamilyProperty(families)
    }

    private fun parseFamily(value: String): FontFamilyProperty.FontFamily? {
        val trimmed = value.trim().lowercase()

        // Check for generic families
        val generic = when (trimmed) {
            "serif" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.SERIF)
            "sans-serif" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.SANS_SERIF)
            "monospace" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.MONOSPACE)
            "cursive" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.CURSIVE)
            "fantasy" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.FANTASY)
            "system-ui" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.SYSTEM_UI)
            "ui-serif" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.UI_SERIF)
            "ui-sans-serif" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.UI_SANS_SERIF)
            "ui-monospace" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.UI_MONOSPACE)
            "ui-rounded" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.UI_ROUNDED)
            "emoji" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.EMOJI)
            "math" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.MATH)
            "fangsong" -> FontFamilyProperty.FontFamily.Generic(FontFamilyProperty.FontFamily.GenericFamily.FANGSONG)
            else -> null
        }

        if (generic != null) return generic

        // Otherwise, it's a named font family
        // Remove quotes if present
        val name = value.trim().removeSurrounding("\"").removeSurrounding("'")
        return FontFamilyProperty.FontFamily.Named(name)
    }
}
