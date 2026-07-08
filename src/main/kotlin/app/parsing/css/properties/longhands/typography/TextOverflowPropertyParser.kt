package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextOverflowProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for `text-overflow` property.
 *
 * Syntax: clip | ellipsis | <string> | fade() | [clip|ellipsis] [clip|ellipsis]
 */
object TextOverflowPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return TextOverflowProperty(TextOverflowProperty.TextOverflowValue.Keyword(lower))
        }

        // Handle fade function
        if (lower.startsWith("fade(")) {
            val length = if (lower == "fade()") null else {
                trimmed.substring(5, trimmed.lastIndexOf(')'))
            }
            return TextOverflowProperty(TextOverflowProperty.TextOverflowValue.Fade(length))
        }

        // Handle quoted strings
        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
            val content = trimmed.removeSurrounding("\"").removeSurrounding("'")
            return TextOverflowProperty(TextOverflowProperty.TextOverflowValue.CustomString(content))
        }

        // Check for two-value syntax
        val parts = lower.split(Regex("\\s+"))
        if (parts.size == 2) {
            val start = parseOverflow(parts[0]) ?: return null
            val end = parseOverflow(parts[1]) ?: return null
            return TextOverflowProperty(TextOverflowProperty.TextOverflowValue.TwoValue(start, end))
        }

        // Single value
        val overflow = parseOverflow(lower) ?: return null
        return TextOverflowProperty(overflow)
    }

    private fun parseOverflow(value: String): TextOverflowProperty.TextOverflow? {
        return when (value) {
            "clip" -> TextOverflowProperty.TextOverflow.CLIP
            "ellipsis" -> TextOverflowProperty.TextOverflow.ELLIPSIS
            else -> null
        }
    }
}
