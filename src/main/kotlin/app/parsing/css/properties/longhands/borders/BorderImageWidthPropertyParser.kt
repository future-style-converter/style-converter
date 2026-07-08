package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.irmodels.properties.borders.BorderImageWidthProperty
import app.irmodels.properties.borders.BorderImageWidthValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.NumberParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Parses the CSS `border-image-width` property.
 *
 * Syntax: `[ <length-percentage> | <number> | auto ]{1,4}`
 *
 * Specifies the width of the border image. Scales the border image slice.
 *
 * Examples:
 * - "10px" → all sides 10px
 * - "10px auto" → vertical 10px, horizontal auto
 * - "10px 20px 30px" → top 10px, horizontal 20px, bottom 30px
 * - "10px 20px 30px 40px" → top 10px, right 20px, bottom 30px, left 40px
 * - "1" → all sides 1 (multiplier)
 */
object BorderImageWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Split the value into parts
        val parts = trimmed.split("""\s+""".toRegex())

        if (parts.isEmpty() || parts.size > 4) {
            return null
        }

        // Parse each part
        val values = parts.mapNotNull { parseWidthValue(it) }

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

        return BorderImageWidthProperty(
            top = top,
            right = right,
            bottom = bottom,
            left = left
        )
    }

    /**
     * Parse a single width value (auto, length, percentage, or number).
     */
    private fun parseWidthValue(value: String): BorderImageWidthValue? {
        val trimmed = value.trim().lowercase()

        // Check for 'auto' keyword
        if (trimmed == "auto") {
            return BorderImageWidthValue.Auto
        }

        // Try percentage
        PercentageParser.parse(trimmed)?.let {
            return BorderImageWidthValue.Percentage(it)
        }

        // Try length
        LengthParser.parse(trimmed)?.let {
            return BorderImageWidthValue.Length(it)
        }

        // Try number (as multiplier)
        NumberParser.parse(trimmed)?.let {
            return BorderImageWidthValue.Number(it)
        }

        return null
    }
}
