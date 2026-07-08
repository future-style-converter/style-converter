package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontSynthesisWeightProperty
import app.irmodels.properties.typography.FontSynthesisWeightValue
import app.parsing.css.properties.longhands.PropertyParser

object FontSynthesisWeightPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val synthesisValue = when (trimmed) {
            "auto" -> FontSynthesisWeightValue.AUTO
            "none" -> FontSynthesisWeightValue.NONE
            else -> return null
        }

        return FontSynthesisWeightProperty(synthesisValue)
    }
}
