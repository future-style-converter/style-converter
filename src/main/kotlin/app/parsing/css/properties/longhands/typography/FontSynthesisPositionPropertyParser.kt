package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontSynthesisPositionProperty
import app.irmodels.properties.typography.FontSynthesisPositionValue
import app.parsing.css.properties.longhands.PropertyParser

object FontSynthesisPositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val synthValue = when (trimmed) {
            "auto" -> FontSynthesisPositionValue.AUTO
            "none" -> FontSynthesisPositionValue.NONE
            else -> return null
        }
        return FontSynthesisPositionProperty(synthValue)
    }
}
