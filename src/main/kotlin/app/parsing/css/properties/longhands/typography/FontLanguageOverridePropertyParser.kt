package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontLanguageOverrideProperty
import app.irmodels.properties.typography.FontLanguageOverrideValue
import app.parsing.css.properties.longhands.PropertyParser

object FontLanguageOverridePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val overrideValue = if (trimmed == "normal") {
            FontLanguageOverrideValue.Normal
        } else {
            // Language tag should be a quoted string
            val tag = value.trim().removeSurrounding("\"").removeSurrounding("'")
            FontLanguageOverrideValue.LanguageTag(tag)
        }

        return FontLanguageOverrideProperty(overrideValue)
    }
}
