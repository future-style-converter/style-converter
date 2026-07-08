package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontVariantLigaturesProperty
import app.irmodels.properties.typography.FontVariantLigaturesValue
import app.parsing.css.properties.longhands.PropertyParser

object FontVariantLigaturesPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "normal") {
            return FontVariantLigaturesProperty(listOf(FontVariantLigaturesValue.NORMAL))
        }

        if (trimmed == "none") {
            return FontVariantLigaturesProperty(listOf(FontVariantLigaturesValue.NONE))
        }

        // Parse space-separated values
        val values = trimmed.split(Regex("\\s+")).mapNotNull { parseVariant(it) }
        if (values.isEmpty()) return null

        return FontVariantLigaturesProperty(values)
    }

    private fun parseVariant(value: String): FontVariantLigaturesValue? {
        return when (value) {
            "normal" -> FontVariantLigaturesValue.NORMAL
            "none" -> FontVariantLigaturesValue.NONE
            "common-ligatures" -> FontVariantLigaturesValue.COMMON_LIGATURES
            "no-common-ligatures" -> FontVariantLigaturesValue.NO_COMMON_LIGATURES
            "discretionary-ligatures" -> FontVariantLigaturesValue.DISCRETIONARY_LIGATURES
            "no-discretionary-ligatures" -> FontVariantLigaturesValue.NO_DISCRETIONARY_LIGATURES
            "historical-ligatures" -> FontVariantLigaturesValue.HISTORICAL_LIGATURES
            "no-historical-ligatures" -> FontVariantLigaturesValue.NO_HISTORICAL_LIGATURES
            "contextual" -> FontVariantLigaturesValue.CONTEXTUAL
            "no-contextual" -> FontVariantLigaturesValue.NO_CONTEXTUAL
            else -> null
        }
    }
}
