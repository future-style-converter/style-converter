package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontVariantEastAsianProperty
import app.irmodels.properties.typography.FontVariantEastAsianValue
import app.parsing.css.properties.longhands.PropertyParser

object FontVariantEastAsianPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "normal") {
            return FontVariantEastAsianProperty(listOf(FontVariantEastAsianValue.NORMAL))
        }

        // Parse space-separated values
        val values = trimmed.split(Regex("\\s+")).mapNotNull { parseVariant(it) }
        if (values.isEmpty()) return null

        return FontVariantEastAsianProperty(values)
    }

    private fun parseVariant(value: String): FontVariantEastAsianValue? {
        return when (value) {
            "normal" -> FontVariantEastAsianValue.NORMAL
            "jis78" -> FontVariantEastAsianValue.JIS78
            "jis83" -> FontVariantEastAsianValue.JIS83
            "jis90" -> FontVariantEastAsianValue.JIS90
            "jis04" -> FontVariantEastAsianValue.JIS04
            "simplified" -> FontVariantEastAsianValue.SIMPLIFIED
            "traditional" -> FontVariantEastAsianValue.TRADITIONAL
            "full-width" -> FontVariantEastAsianValue.FULL_WIDTH
            "proportional-width" -> FontVariantEastAsianValue.PROPORTIONAL_WIDTH
            "ruby" -> FontVariantEastAsianValue.RUBY
            else -> null
        }
    }
}
