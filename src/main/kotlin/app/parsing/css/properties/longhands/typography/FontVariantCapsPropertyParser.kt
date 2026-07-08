package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontVariantCapsProperty
import app.irmodels.properties.typography.FontVariantCapsValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `font-variant-caps` property.
 */
object FontVariantCapsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()

        val capsValue = when (normalized) {
            "normal" -> FontVariantCapsValue.NORMAL
            "small-caps" -> FontVariantCapsValue.SMALL_CAPS
            "all-small-caps" -> FontVariantCapsValue.ALL_SMALL_CAPS
            "petite-caps" -> FontVariantCapsValue.PETITE_CAPS
            "all-petite-caps" -> FontVariantCapsValue.ALL_PETITE_CAPS
            "unicase" -> FontVariantCapsValue.UNICASE
            "titling-caps" -> FontVariantCapsValue.TITLING_CAPS
            else -> return null
        }

        return FontVariantCapsProperty(capsValue)
    }
}
