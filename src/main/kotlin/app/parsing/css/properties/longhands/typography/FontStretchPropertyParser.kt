package app.parsing.css.properties.longhands.typography

import app.irmodels.*
import app.irmodels.properties.typography.FontStretch
import app.irmodels.properties.typography.FontStretchProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object FontStretchPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return FontStretchProperty(FontStretch.fromGlobalKeyword(trimmed))
        }

        // Handle stretch keywords
        val keyword = parseKeyword(trimmed)
        if (keyword != null) {
            return FontStretchProperty(FontStretch.fromKeyword(keyword))
        }

        // Try to parse as percentage
        if (trimmed.endsWith("%")) {
            val num = trimmed.removeSuffix("%").toDoubleOrNull() ?: return null
            return FontStretchProperty(FontStretch.fromPercentage(num))
        }

        return null
    }

    private fun parseKeyword(value: String): FontStretch.StretchKeyword? = when (value) {
        "ultra-condensed" -> FontStretch.StretchKeyword.ULTRA_CONDENSED
        "extra-condensed" -> FontStretch.StretchKeyword.EXTRA_CONDENSED
        "condensed" -> FontStretch.StretchKeyword.CONDENSED
        "semi-condensed" -> FontStretch.StretchKeyword.SEMI_CONDENSED
        "normal" -> FontStretch.StretchKeyword.NORMAL
        "semi-expanded" -> FontStretch.StretchKeyword.SEMI_EXPANDED
        "expanded" -> FontStretch.StretchKeyword.EXPANDED
        "extra-expanded" -> FontStretch.StretchKeyword.EXTRA_EXPANDED
        "ultra-expanded" -> FontStretch.StretchKeyword.ULTRA_EXPANDED
        else -> null
    }
}
