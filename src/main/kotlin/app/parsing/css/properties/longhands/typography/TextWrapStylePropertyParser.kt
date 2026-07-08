package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextWrapStyleProperty
import app.irmodels.properties.typography.TextWrapStyleValue
import app.parsing.css.properties.longhands.PropertyParser

object TextWrapStylePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val styleValue = when (trimmed) {
            "auto" -> TextWrapStyleValue.AUTO
            "balance" -> TextWrapStyleValue.BALANCE
            "stable" -> TextWrapStyleValue.STABLE
            "pretty" -> TextWrapStyleValue.PRETTY
            else -> return null
        }
        return TextWrapStyleProperty(styleValue)
    }
}
