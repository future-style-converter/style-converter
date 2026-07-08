package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.LetterSpacing
import app.irmodels.properties.typography.LetterSpacingProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object LetterSpacingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle 'normal' keyword
        if (trimmed == "normal") {
            return LetterSpacingProperty(LetterSpacing.normal())
        }

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return LetterSpacingProperty(LetterSpacing.fromGlobalKeyword(trimmed))
        }

        // Try parsing as length
        val length = LengthParser.parse(trimmed) ?: return null
        return LetterSpacingProperty(LetterSpacing.fromLength(length))
    }
}
