package app.parsing.css.properties.longhands.borders

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.borders.BorderImageSliceProperty
import app.irmodels.properties.borders.BorderImageSliceValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.NumberParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Parses the CSS `border-image-slice` property.
 *
 * Syntax: `<number-percentage>{1,4} && fill?`
 *
 * Divides the border image into regions for corners, edges, and middle.
 * Values represent inward offsets from the edges of the image.
 *
 * Examples:
 * - "30" → all sides 30
 * - "30 40" → vertical 30, horizontal 40
 * - "30 40 50" → top 30, horizontal 40, bottom 50
 * - "30 40 50 60" → top 30, right 40, bottom 50, left 60
 * - "30 fill" → all sides 30, fill center
 */
object BorderImageSlicePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Check for 'fill' keyword
        val hasFill = trimmed.endsWith("fill")
        val valueStr = if (hasFill) {
            trimmed.removeSuffix("fill").trim()
        } else {
            trimmed
        }

        // Split the value into parts
        val parts = valueStr.split("""\s+""".toRegex())

        if (parts.isEmpty() || parts.size > 4) {
            return null
        }

        // Parse each part
        val values = parts.mapNotNull { parseSliceValue(it) }

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

        return BorderImageSliceProperty(
            top = top,
            right = right,
            bottom = bottom,
            left = left,
            fill = hasFill
        )
    }

    /**
     * Parse a single slice value (number or percentage).
     */
    private fun parseSliceValue(value: String): BorderImageSliceValue? {
        // Try percentage first
        PercentageParser.parse(value)?.let {
            return BorderImageSliceValue.Percentage(it)
        }

        // Try number
        NumberParser.parse(value)?.let {
            return BorderImageSliceValue.Number(it)
        }

        return null
    }
}
