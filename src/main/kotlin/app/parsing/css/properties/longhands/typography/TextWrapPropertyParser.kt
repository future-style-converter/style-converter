package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextWrapProperty
import app.irmodels.properties.typography.TextWrapValue
import app.parsing.css.properties.longhands.PropertyParser

object TextWrapPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val wrapValue = when (trimmed) {
            "wrap" -> TextWrapValue.WRAP
            "nowrap" -> TextWrapValue.NOWRAP
            "balance" -> TextWrapValue.BALANCE
            "stable" -> TextWrapValue.STABLE
            "pretty" -> TextWrapValue.PRETTY
            else -> return null
        }
        return TextWrapProperty(wrapValue)
    }
}
