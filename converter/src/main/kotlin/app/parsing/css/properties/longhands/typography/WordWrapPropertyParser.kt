package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.WordWrapProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `word-wrap` property.
 *
 * Syntax: normal | break-word | anywhere
 *
 * Note: word-wrap is a legacy name for overflow-wrap.
 * The IR model only supports normal and break-word.
 * The value "anywhere" is not yet supported (added in CSS Text 3).
 */
object WordWrapPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val wrap = when (trimmed) {
            "normal" -> WordWrapProperty.WordWrap.NORMAL
            "break-word" -> WordWrapProperty.WordWrap.BREAK_WORD
            "anywhere" -> {
                // "anywhere" is similar to "break-word" but with subtle differences
                // For now, treat it as break-word since IR doesn't support it yet
                WordWrapProperty.WordWrap.BREAK_WORD
            }
            else -> return null
        }

        return WordWrapProperty(wrap)
    }
}
