package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextDecorationLineProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `text-decoration-line` property.
 *
 * Syntax: none | [ underline || overline || line-through || blink ]
 */
object TextDecorationLinePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle "none" case
        if (trimmed == "none") {
            return TextDecorationLineProperty(listOf(TextDecorationLineProperty.DecorationLine.NONE))
        }

        // Parse space-separated values (can combine)
        val lines = trimmed.split(Regex("\\s+")).mapNotNull { parseLine(it) }
        if (lines.isEmpty()) return null

        // "none" cannot be combined with other values
        if (lines.contains(TextDecorationLineProperty.DecorationLine.NONE) && lines.size > 1) {
            return null
        }

        return TextDecorationLineProperty(lines)
    }

    private fun parseLine(value: String): TextDecorationLineProperty.DecorationLine? {
        return when (value) {
            "none" -> TextDecorationLineProperty.DecorationLine.NONE
            "underline" -> TextDecorationLineProperty.DecorationLine.UNDERLINE
            "overline" -> TextDecorationLineProperty.DecorationLine.OVERLINE
            "line-through" -> TextDecorationLineProperty.DecorationLine.LINE_THROUGH
            "blink" -> TextDecorationLineProperty.DecorationLine.BLINK
            else -> null
        }
    }
}
