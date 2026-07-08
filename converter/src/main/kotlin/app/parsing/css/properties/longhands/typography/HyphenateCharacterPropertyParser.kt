package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.HyphenateCharacterProperty
import app.irmodels.properties.typography.HyphenateCharacterValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `hyphenate-character` property.
 *
 * Values: auto | <string>
 * String should be quoted in CSS but we accept both quoted and unquoted
 */
object HyphenateCharacterPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle 'auto' keyword
        if (trimmed == "auto") {
            return HyphenateCharacterProperty(HyphenateCharacterValue.Auto)
        }

        // Parse string value (strip quotes if present)
        val stringValue = value.trim().removeSurrounding("\"").removeSurrounding("'")

        if (stringValue.isEmpty()) return null

        return HyphenateCharacterProperty(HyphenateCharacterValue.String(stringValue))
    }
}
