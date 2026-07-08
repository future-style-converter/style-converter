package app.parsing.css.properties.longhands.transforms

import app.irmodels.IRProperty
import app.irmodels.properties.transforms.TransformOriginProperty
import app.irmodels.properties.transforms.TransformOriginProperty.OriginValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector

/**
 * Parser for the CSS `transform-origin` property.
 *
 * Syntax: <position> [ <length> ]?
 *
 * Position keywords: left, center, right, top, bottom
 * Global keywords: inherit, initial, unset, revert, revert-layer
 * Values: <length> | <percentage>
 * Optional third value for z-offset (3D transforms)
 *
 * Examples:
 * - "inherit" → Keyword("inherit")
 * - "var(--origin)" → Raw
 * - "center" → (Center, Center, null)
 * - "top left" → (Left, Top, null)
 * - "50% 50%" → (Percentage(50), Percentage(50), null)
 * - "100px 200px" → (Length(100px), Length(200px), null)
 * - "center center 10px" → (Center, Center, Length(10px))
 */
object TransformOriginPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lowerValue = trimmed.lowercase()

        // 1. Handle global keywords
        when (lowerValue) {
            "inherit", "initial", "unset", "revert", "revert-layer" ->
                return TransformOriginProperty.Keyword(lowerValue)
        }

        // 2. Handle CSS functions (var(), env(), calc())
        if (ExpressionDetector.startsWithExpression(lowerValue) ||
            lowerValue.contains("calc(")) {
            return TransformOriginProperty.Raw(trimmed)
        }

        // 3. Parse position values
        val parts = lowerValue.split(Regex("\\s+"))
        if (parts.isEmpty()) return TransformOriginProperty.Raw(trimmed)

        // Parse x value
        val x = parseOriginValue(parts[0]) ?: return TransformOriginProperty.Raw(trimmed)

        // Parse y value (default to center if not provided)
        val y = when {
            parts.size < 2 -> {
                // If only one value provided, determine default for y
                when {
                    isHorizontalKeyword(parts[0]) -> parseOriginValue("center")!!
                    isVerticalKeyword(parts[0]) -> x
                    else -> parseOriginValue("center")!!
                }
            }
            else -> parseOriginValue(parts[1]) ?: return TransformOriginProperty.Raw(trimmed)
        }

        // Parse z value (optional, for 3D transforms)
        val z = if (parts.size >= 3) {
            LengthParser.parse(parts[2])
        } else {
            null
        }

        return TransformOriginProperty.Values(x, y, z)
    }

    private fun parseOriginValue(value: String): OriginValue? {
        return when (value) {
            "left" -> OriginValue.Keyword(OriginValue.OriginKeyword.LEFT)
            "center" -> OriginValue.Keyword(OriginValue.OriginKeyword.CENTER)
            "right" -> OriginValue.Keyword(OriginValue.OriginKeyword.RIGHT)
            "top" -> OriginValue.Keyword(OriginValue.OriginKeyword.TOP)
            "bottom" -> OriginValue.Keyword(OriginValue.OriginKeyword.BOTTOM)
            else -> {
                // Try percentage first, then length
                PercentageParser.parse(value)?.let { OriginValue.PercentageValue(it) }
                    ?: LengthParser.parse(value)?.let { OriginValue.LengthValue(it) }
            }
        }
    }

    private fun isHorizontalKeyword(value: String): Boolean {
        return value in listOf("left", "center", "right")
    }

    private fun isVerticalKeyword(value: String): Boolean {
        return value in listOf("top", "center", "bottom")
    }
}
