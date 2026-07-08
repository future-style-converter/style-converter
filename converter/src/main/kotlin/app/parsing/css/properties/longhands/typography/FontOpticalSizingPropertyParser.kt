package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontOpticalSizingProperty
import app.irmodels.properties.typography.FontOpticalSizingValue
import app.parsing.css.properties.longhands.PropertyParser

object FontOpticalSizingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val sizingValue = when (trimmed) {
            "auto" -> FontOpticalSizingValue.AUTO
            "none" -> FontOpticalSizingValue.NONE
            else -> return null
        }

        return FontOpticalSizingProperty(sizingValue)
    }
}
