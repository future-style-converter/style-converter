package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.RubyPositionProperty
import app.irmodels.properties.typography.RubyPositionValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for `ruby-position` property.
 *
 * Values: alternate | over | under | inter-character | <position> <alignment>
 */
object RubyPositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return RubyPositionProperty(RubyPositionValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return RubyPositionProperty(RubyPositionValue.Raw(trimmed))
        }

        // Parse single or combined values
        val parts = lower.split(Regex("\\s+")).toSet()

        val position = when {
            // Combined values (position + alignment)
            parts.containsAll(setOf("over", "left")) -> RubyPositionValue.OverLeft
            parts.containsAll(setOf("over", "right")) -> RubyPositionValue.OverRight
            parts.containsAll(setOf("under", "left")) -> RubyPositionValue.UnderLeft
            parts.containsAll(setOf("under", "right")) -> RubyPositionValue.UnderRight
            // Single values
            parts == setOf("alternate") -> RubyPositionValue.Alternate
            parts == setOf("over") -> RubyPositionValue.Over
            parts == setOf("under") -> RubyPositionValue.Under
            parts == setOf("inter-character") -> RubyPositionValue.InterCharacter
            else -> RubyPositionValue.Raw(trimmed)
        }

        return RubyPositionProperty(position)
    }
}
