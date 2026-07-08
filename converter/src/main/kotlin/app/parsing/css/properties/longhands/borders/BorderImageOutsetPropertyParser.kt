package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.irmodels.properties.borders.BorderImageOutsetProperty
import app.irmodels.properties.borders.BorderImageOutsetValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.NumberParser

/**
 * Parses the CSS `border-image-outset` property.
 *
 * Syntax: `[ <length> | <number> ]{1,4}`
 *
 * Specifies the distance by which the border image extends beyond the border box.
 * Numbers represent multiples of the border-width.
 *
 * Examples:
 * - "10px" → all sides 10px
 * - "10px 20px" → vertical 10px, horizontal 20px
 * - "10px 20px 30px" → top 10px, horizontal 20px, bottom 30px
 * - "10px 20px 30px 40px" → top 10px, right 20px, bottom 30px, left 40px
 * - "1" → all sides 1× border-width
 */
object BorderImageOutsetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Split the value into parts
        val parts = trimmed.split("""\s+""".toRegex())

        if (parts.isEmpty() || parts.size > 4) {
            return null
        }

        // Parse each part
        val values = parts.mapNotNull { parseOutsetValue(it) }

        if (values.isEmpty()) {
            return null
        }

        // Apply CSS shorthand logic
        val (top, right, bottom, left) = when (values.size) {
            1 -> listOf(values[0], values[0], values[0], values[0])
            2 -> listOf(values[0], values[1], values[0], values[1])
            3 -> listOf(values[0], values[1], values[2], values[1])
            4 -> values
            else -> return null
        }

        return BorderImageOutsetProperty(
            top = top,
            right = right,
            bottom = bottom,
            left = left
        )
    }

    /**
     * Parse a single outset value (length or number).
     */
    private fun parseOutsetValue(value: String): BorderImageOutsetValue? {
        val trimmed = value.trim()

        // Try length first
        LengthParser.parse(trimmed)?.let {
            return BorderImageOutsetValue.Length(it)
        }

        // Try number (as multiplier of border-width)
        NumberParser.parse(trimmed)?.let {
            return BorderImageOutsetValue.Number(it)
        }

        return null
    }
}
