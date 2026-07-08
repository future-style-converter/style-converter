package app.parsing.css.properties.longhands.images

import app.irmodels.*
import app.irmodels.properties.images.ObjectPositionProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords
import app.parsing.css.properties.primitiveParsers.ExpressionDetector

object ObjectPositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle global keywords first (inherit, initial, unset, revert, revert-layer)
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            val globalValue = ObjectPositionProperty.ObjectPositionValue.GlobalKeyword(trimmed)
            return ObjectPositionProperty(ObjectPositionProperty.Position(globalValue, globalValue))
        }

        // Handle CSS functions (var, env, calc, etc.)
        if (ExpressionDetector.containsExpression(trimmed)) {
            val rawValue = ObjectPositionProperty.ObjectPositionValue.Raw(value.trim())
            return ObjectPositionProperty(ObjectPositionProperty.Position(rawValue, rawValue))
        }

        val parts = trimmed.split(Regex("\\s+"))

        val (x, y) = when (parts.size) {
            1 -> Pair(parsePositionValue(parts[0]), parsePositionValue("center"))
            2 -> Pair(parsePositionValue(parts[0]), parsePositionValue(parts[1]))
            4 -> {
                // Handle 4-value syntax: keyword offset keyword offset
                // e.g., "right 10px bottom 20px" or "left 5% top 10%"
                val xKeyword = parseKeyword(parts[0])
                val xOffset = LengthParser.parse(parts[1])
                val yKeyword = parseKeyword(parts[2])
                val yOffset = LengthParser.parse(parts[3])

                if (xKeyword != null && xOffset != null && yKeyword != null && yOffset != null) {
                    Pair(
                        ObjectPositionProperty.ObjectPositionValue.KeywordOffset(xKeyword, xOffset),
                        ObjectPositionProperty.ObjectPositionValue.KeywordOffset(yKeyword, yOffset)
                    )
                } else {
                    // Fall back to Raw for unparseable 4-value combinations
                    val rawValue = ObjectPositionProperty.ObjectPositionValue.Raw(value.trim())
                    return ObjectPositionProperty(ObjectPositionProperty.Position(rawValue, rawValue))
                }
            }
            3 -> {
                // Handle 3-value syntax: keyword offset keyword or keyword keyword offset
                val first = parsePositionValue(parts[0])
                val second = parsePositionValue(parts[1])
                val third = parsePositionValue(parts[2])
                if (first != null && second != null && third != null) {
                    Pair(first, third)
                } else {
                    val rawValue = ObjectPositionProperty.ObjectPositionValue.Raw(value.trim())
                    return ObjectPositionProperty(ObjectPositionProperty.Position(rawValue, rawValue))
                }
            }
            else -> {
                // Fall back to Raw for unparseable values instead of returning null
                val rawValue = ObjectPositionProperty.ObjectPositionValue.Raw(value.trim())
                return ObjectPositionProperty(ObjectPositionProperty.Position(rawValue, rawValue))
            }
        }

        // Fall back to Raw if parsing failed
        if (x == null || y == null) {
            val rawValue = ObjectPositionProperty.ObjectPositionValue.Raw(value.trim())
            return ObjectPositionProperty(ObjectPositionProperty.Position(rawValue, rawValue))
        }

        return ObjectPositionProperty(ObjectPositionProperty.Position(x, y))
    }

    private fun parseKeyword(value: String): ObjectPositionProperty.PositionKeyword? {
        return when (value.lowercase()) {
            "left" -> ObjectPositionProperty.PositionKeyword.LEFT
            "center" -> ObjectPositionProperty.PositionKeyword.CENTER
            "right" -> ObjectPositionProperty.PositionKeyword.RIGHT
            "top" -> ObjectPositionProperty.PositionKeyword.TOP
            "bottom" -> ObjectPositionProperty.PositionKeyword.BOTTOM
            else -> null
        }
    }

    private fun parsePositionValue(value: String): ObjectPositionProperty.ObjectPositionValue? {
        val trimmed = value.trim().lowercase()

        return when (trimmed) {
            "left" -> ObjectPositionProperty.ObjectPositionValue.Keyword(ObjectPositionProperty.PositionKeyword.LEFT)
            "center" -> ObjectPositionProperty.ObjectPositionValue.Keyword(ObjectPositionProperty.PositionKeyword.CENTER)
            "right" -> ObjectPositionProperty.ObjectPositionValue.Keyword(ObjectPositionProperty.PositionKeyword.RIGHT)
            "top" -> ObjectPositionProperty.ObjectPositionValue.Keyword(ObjectPositionProperty.PositionKeyword.TOP)
            "bottom" -> ObjectPositionProperty.ObjectPositionValue.Keyword(ObjectPositionProperty.PositionKeyword.BOTTOM)
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    ObjectPositionProperty.ObjectPositionValue.PercentageValue(IRPercentage(length.value))
                } else {
                    ObjectPositionProperty.ObjectPositionValue.LengthValue(length)
                }
            }
        }
    }
}
