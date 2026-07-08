package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.WordSpacing
import app.irmodels.properties.typography.WordSpacingProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object WordSpacingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle 'normal' keyword
        if (trimmed == "normal") {
            return WordSpacingProperty(WordSpacing.normal())
        }

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return WordSpacingProperty(WordSpacing.fromGlobalKeyword(trimmed))
        }

        // Try parsing as length
        val length = LengthParser.parse(trimmed) ?: return null
        return WordSpacingProperty(WordSpacing.fromLength(length))
    }
}
