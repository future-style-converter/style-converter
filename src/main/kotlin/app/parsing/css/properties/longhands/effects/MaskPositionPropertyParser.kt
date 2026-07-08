package app.parsing.css.properties.longhands.effects

import app.irmodels.properties.effects.MaskPosition
import app.irmodels.properties.effects.MaskPositionProperty
import app.irmodels.properties.effects.MaskPositionValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Parser for the `mask-position` CSS property.
 *
 * Accepts:
 * - Keywords: center, top, bottom, left, right
 * - <length> (e.g., 10px)
 * - <percentage> (e.g., 50%)
 * - <x> <y> (e.g., center top, 10px 20px, 50% 100%)
 * - Multiple positions separated by comma (for multi-layer masks)
 */
object MaskPositionPropertyParser : PropertyParser {

    override fun parse(value: String): MaskPositionProperty? {
        val trimmed = value.trim().lowercase()

        // Split by comma for multiple positions
        val positionStrings = trimmed.split(",").map { it.trim() }
        val positions = positionStrings.mapNotNull { parseSinglePosition(it) }

        if (positions.isEmpty()) return null

        return MaskPositionProperty(positions)
    }

    private fun parseSinglePosition(value: String): MaskPosition? {
        val parts = value.split(Regex("\\s+"))

        val x = parsePositionValue(parts[0]) ?: return null
        val y = if (parts.size > 1) {
            parsePositionValue(parts[1]) ?: return null
        } else {
            // Single value: vertical keywords use center for x
            when (parts[0].lowercase()) {
                "top", "bottom" -> MaskPositionValue.Center
                else -> MaskPositionValue.Center
            }
        }

        return MaskPosition(x, y)
    }

    private fun parsePositionValue(value: String): MaskPositionValue? {
        return when (value.lowercase()) {
            "center" -> MaskPositionValue.Center
            "top" -> MaskPositionValue.Top
            "bottom" -> MaskPositionValue.Bottom
            "left" -> MaskPositionValue.Left
            "right" -> MaskPositionValue.Right
            else -> {
                // Try length
                LengthParser.parse(value)?.let { return MaskPositionValue.Length(it) }
                // Try percentage
                PercentageParser.parse(value)?.let { return MaskPositionValue.Percentage(it) }
                null
            }
        }
    }
}
