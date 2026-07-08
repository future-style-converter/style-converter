package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.irmodels.properties.borders.BorderImageRepeat
import app.irmodels.properties.borders.BorderImageRepeatProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parses the CSS `border-image-repeat` property.
 *
 * Syntax: `[ stretch | repeat | round | space ]{1,2}`
 *
 * Controls how the border image's edge regions are scaled and tiled.
 * First value applies to horizontal sides (top, bottom).
 * Second value applies to vertical sides (left, right).
 * If only one value is specified, it applies to all sides.
 *
 * Examples:
 * - "stretch" → all sides stretched
 * - "repeat" → all sides repeated
 * - "repeat stretch" → horizontal repeated, vertical stretched
 * - "round space" → horizontal rounded, vertical spaced
 */
object BorderImageRepeatPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Split the value into parts
        val parts = trimmed.split("""\s+""".toRegex())

        if (parts.isEmpty() || parts.size > 2) {
            return null
        }

        // Parse first value (horizontal)
        val horizontal = parseRepeatValue(parts[0]) ?: return null

        // Parse second value (vertical), if present
        val vertical = if (parts.size == 2) {
            parseRepeatValue(parts[1])
        } else {
            null
        }

        return BorderImageRepeatProperty(
            horizontal = horizontal,
            vertical = vertical
        )
    }

    /**
     * Parse a single repeat value.
     */
    private fun parseRepeatValue(value: String): BorderImageRepeat? {
        return when (value.trim().lowercase()) {
            "stretch" -> BorderImageRepeat.STRETCH
            "repeat" -> BorderImageRepeat.REPEAT
            "round" -> BorderImageRepeat.ROUND
            "space" -> BorderImageRepeat.SPACE
            else -> null
        }
    }
}
