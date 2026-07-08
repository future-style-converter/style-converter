package app.parsing.css.properties.longhands.typography

import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.IRProperty
import app.irmodels.properties.typography.HyphenateLimitCharsProperty
import app.irmodels.properties.typography.HyphenateLimitCharsValue
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for `hyphenate-limit-chars` property.
 *
 * Syntax: auto | <integer>{1,3} (word-min chars-before chars-after)
 */
object HyphenateLimitCharsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return HyphenateLimitCharsProperty(HyphenateLimitCharsValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return HyphenateLimitCharsProperty(HyphenateLimitCharsValue.Raw(trimmed))
        }

        // Handle auto
        if (lower == "auto") {
            return HyphenateLimitCharsProperty(HyphenateLimitCharsValue.Auto)
        }

        // Parse integer values
        val parts = trimmed.split(Regex("""\s+"""))
        if (parts.isEmpty()) {
            return HyphenateLimitCharsProperty(HyphenateLimitCharsValue.Raw(trimmed))
        }

        val wordMin = parts[0].toIntOrNull()
        if (wordMin == null) {
            return HyphenateLimitCharsProperty(HyphenateLimitCharsValue.Raw(trimmed))
        }

        val charsBefore = if (parts.size > 1) parts[1].toIntOrNull() else null
        val charsAfter = if (parts.size > 2) parts[2].toIntOrNull() else null

        return HyphenateLimitCharsProperty(
            HyphenateLimitCharsValue.Values(wordMin, charsBefore, charsAfter)
        )
    }
}
