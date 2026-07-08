package app.parsing.css.properties.longhands.transforms

import app.irmodels.IRProperty
import app.irmodels.properties.transforms.PerspectiveOriginProperty
import app.irmodels.properties.transforms.PerspectiveOriginValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Parser for the CSS `perspective-origin` property.
 *
 * Syntax: <position> (x y coordinates)
 *
 * Keywords: left, center, right, top, bottom
 * Values: <length> | <percentage>
 *
 * Examples:
 * - "center" → (Center, Center)
 * - "center center" → (Center, Center)
 * - "50% 50%" → (Percentage(50), Percentage(50))
 * - "left top" → (Left, Top)
 * - "100px 200px" → (Length(100px), Length(200px))
 */
object PerspectiveOriginPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val parts = trimmed.split(Regex("\\s+"))

        // Parse x value
        val x = when {
            parts.isEmpty() -> return null
            else -> parsePositionValue(parts[0]) ?: return null
        }

        // Parse y value (default to center if not provided)
        val y = when {
            parts.size < 2 -> {
                // If only one value provided, determine default for y
                when (x) {
                    is PerspectiveOriginValue.Left,
                    is PerspectiveOriginValue.Center,
                    is PerspectiveOriginValue.Right -> PerspectiveOriginValue.Center
                    is PerspectiveOriginValue.Top,
                    is PerspectiveOriginValue.Bottom -> x // If x is top/bottom, swap them
                    else -> PerspectiveOriginValue.Center
                }
            }
            else -> parsePositionValue(parts[1]) ?: return null
        }

        return PerspectiveOriginProperty(x, y)
    }

    private fun parsePositionValue(value: String): PerspectiveOriginValue? {
        return when (value) {
            "left" -> PerspectiveOriginValue.Left
            "center" -> PerspectiveOriginValue.Center
            "right" -> PerspectiveOriginValue.Right
            "top" -> PerspectiveOriginValue.Top
            "bottom" -> PerspectiveOriginValue.Bottom
            else -> {
                // Try percentage first, then length
                PercentageParser.parse(value)?.let { PerspectiveOriginValue.Percentage(it) }
                    ?: LengthParser.parse(value)?.let { PerspectiveOriginValue.Length(it) }
            }
        }
    }
}
