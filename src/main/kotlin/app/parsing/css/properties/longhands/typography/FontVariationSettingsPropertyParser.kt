package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontVariationSettingsProperty
import app.parsing.css.properties.longhands.PropertyParser

object FontVariationSettingsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "normal") {
            return FontVariationSettingsProperty(FontVariationSettingsProperty.VariationSettings.Normal())
        }

        // Parse variation axes: "wght" 500, "wdth" 75, etc.
        val variations = mutableListOf<FontVariationSettingsProperty.Variation>()
        val parts = trimmed.split(",").map { it.trim() }

        for (part in parts) {
            val tokens = part.split(Regex("\\s+"))
            if (tokens.size < 2) return null

            val axis = tokens[0].removeSurrounding("\"").removeSurrounding("'")
            if (axis.length != 4) return null // Variation axes must be 4 characters

            val axisValue = tokens[1].toDoubleOrNull() ?: return null

            variations.add(FontVariationSettingsProperty.Variation(axis, axisValue))
        }

        if (variations.isEmpty()) return null

        return FontVariationSettingsProperty(FontVariationSettingsProperty.VariationSettings.Variations(variations))
    }
}
