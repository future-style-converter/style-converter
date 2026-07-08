package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextWrapModeProperty
import app.irmodels.properties.typography.TextWrapModeValue
import app.parsing.css.properties.longhands.PropertyParser

object TextWrapModePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val modeValue = when (trimmed) {
            "wrap" -> TextWrapModeValue.WRAP
            "nowrap" -> TextWrapModeValue.NOWRAP
            else -> return null
        }
        return TextWrapModeProperty(modeValue)
    }
}
