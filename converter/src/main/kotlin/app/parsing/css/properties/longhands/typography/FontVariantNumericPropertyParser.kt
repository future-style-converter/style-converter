package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontVariantNumericProperty
import app.parsing.css.properties.longhands.PropertyParser

object FontVariantNumericPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "normal") {
            return FontVariantNumericProperty(listOf(FontVariantNumericProperty.NumericVariant.NORMAL))
        }

        // Parse space-separated values
        val values = trimmed.split(Regex("\\s+")).mapNotNull { parseVariant(it) }
        if (values.isEmpty()) return null

        return FontVariantNumericProperty(values)
    }

    private fun parseVariant(value: String): FontVariantNumericProperty.NumericVariant? {
        return when (value) {
            "normal" -> FontVariantNumericProperty.NumericVariant.NORMAL
            "ordinal" -> FontVariantNumericProperty.NumericVariant.ORDINAL
            "slashed-zero" -> FontVariantNumericProperty.NumericVariant.SLASHED_ZERO
            "lining-nums" -> FontVariantNumericProperty.NumericVariant.LINING_NUMS
            "oldstyle-nums" -> FontVariantNumericProperty.NumericVariant.OLDSTYLE_NUMS
            "proportional-nums" -> FontVariantNumericProperty.NumericVariant.PROPORTIONAL_NUMS
            "tabular-nums" -> FontVariantNumericProperty.NumericVariant.TABULAR_NUMS
            "diagonal-fractions" -> FontVariantNumericProperty.NumericVariant.DIAGONAL_FRACTIONS
            "stacked-fractions" -> FontVariantNumericProperty.NumericVariant.STACKED_FRACTIONS
            else -> null
        }
    }
}
