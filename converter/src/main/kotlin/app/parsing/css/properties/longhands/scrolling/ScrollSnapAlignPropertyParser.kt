package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollSnapAlignProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `scroll-snap-align` property.
 *
 * Syntax: [ none | start | end | center ]{1,2}
 */
object ScrollSnapAlignPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Split into parts
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.isEmpty() || parts.size > 2) return null

        // Parse block alignment (required)
        val block = parseAlignment(parts[0]) ?: return null

        // Parse inline alignment (optional, defaults to block value)
        val inline = if (parts.size > 1) {
            parseAlignment(parts[1])
        } else {
            null
        }

        return ScrollSnapAlignProperty(block, inline)
    }

    private fun parseAlignment(value: String): ScrollSnapAlignProperty.Alignment? {
        return when (value) {
            "none" -> ScrollSnapAlignProperty.Alignment.NONE
            "start" -> ScrollSnapAlignProperty.Alignment.START
            "end" -> ScrollSnapAlignProperty.Alignment.END
            "center" -> ScrollSnapAlignProperty.Alignment.CENTER
            else -> null
        }
    }
}
