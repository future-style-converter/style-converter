package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontSynthesisStyleProperty
import app.irmodels.properties.typography.FontSynthesisStyleValue
import app.parsing.css.properties.longhands.PropertyParser

object FontSynthesisStylePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val synthesisValue = when (trimmed) {
            "auto" -> FontSynthesisStyleValue.AUTO
            "none" -> FontSynthesisStyleValue.NONE
            else -> return null
        }

        return FontSynthesisStyleProperty(synthesisValue)
    }
}
