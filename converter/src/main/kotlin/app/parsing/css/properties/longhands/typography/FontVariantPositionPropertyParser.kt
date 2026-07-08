package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontVariantPositionProperty
import app.irmodels.properties.typography.FontVariantPositionValue
import app.parsing.css.properties.longhands.PropertyParser

object FontVariantPositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val positionValue = when (trimmed) {
            "normal" -> FontVariantPositionValue.NORMAL
            "sub" -> FontVariantPositionValue.SUB
            "super" -> FontVariantPositionValue.SUPER
            else -> return null
        }

        return FontVariantPositionProperty(positionValue)
    }
}
