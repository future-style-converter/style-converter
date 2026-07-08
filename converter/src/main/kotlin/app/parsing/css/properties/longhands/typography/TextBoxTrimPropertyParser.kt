package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextBoxTrimProperty
import app.irmodels.properties.typography.TextBoxTrimValue
import app.parsing.css.properties.longhands.PropertyParser

object TextBoxTrimPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val trimValue = when (trimmed) {
            "none" -> TextBoxTrimValue.NONE
            "trim-start" -> TextBoxTrimValue.TRIM_START
            "trim-end" -> TextBoxTrimValue.TRIM_END
            "trim-both" -> TextBoxTrimValue.TRIM_BOTH
            else -> return null
        }

        return TextBoxTrimProperty(trimValue)
    }
}
