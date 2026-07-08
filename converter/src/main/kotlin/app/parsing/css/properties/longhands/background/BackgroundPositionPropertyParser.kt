package app.parsing.css.properties.longhands.background

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.background.BackgroundPositionProperty
import app.irmodels.properties.background.BackgroundPositionProperty.PositionValue
import app.irmodels.properties.background.BackgroundPositionProperty.EdgeValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for the `background-position` property.
 *
 * Syntax: <position> [, <position>]*
 * Where <position> is: [left|center|right|<length-percentage>] [top|center|bottom|<length-percentage>]?
 *
 * Examples:
 * - "center" → center center
 * - "top left" → left top
 * - "50% 100%" → 50% 100%
 * - "10px 20px" → 10px 20px
 * - "right 10px bottom 20px" → right 10px bottom 20px (4-value syntax)
 */
object BackgroundPositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return BackgroundPositionProperty(listOf(PositionValue.Keyword(trimmed)))
        }

        // Split by commas for multiple positions
        val positions = splitByComma(trimmed).mapNotNull { parsePosition(it.trim()) }

        if (positions.isEmpty()) return null

        return BackgroundPositionProperty(positions)
    }

    private fun parsePosition(value: String): PositionValue? {
        // Handle expressions (calc(), var(), etc.)
        if (value.contains("(")) {
            return PositionValue.Raw(value)
        }

        val parts = value.split("""\s+""".toRegex())

        return when (parts.size) {
            1 -> parseSingleValue(parts[0])
            2 -> parseTwoValues(parts[0], parts[1])
            4 -> parseFourValues(parts)
            else -> PositionValue.Raw(value)
        }
    }

    private fun parseSingleValue(value: String): PositionValue {
        return when (value) {
            "center" -> PositionValue.Center
            "top" -> PositionValue.TwoValue(EdgeValue.Center, EdgeValue.Top)
            "bottom" -> PositionValue.TwoValue(EdgeValue.Center, EdgeValue.Bottom)
            "left" -> PositionValue.TwoValue(EdgeValue.Left, EdgeValue.Center)
            "right" -> PositionValue.TwoValue(EdgeValue.Right, EdgeValue.Center)
            else -> {
                val parsed = parseEdgeValue(value)
                if (parsed != null) {
                    PositionValue.TwoValue(parsed, EdgeValue.Center)
                } else {
                    PositionValue.Raw(value)
                }
            }
        }
    }

    private fun parseTwoValues(x: String, y: String): PositionValue {
        val xVal = parseEdgeValue(x) ?: return PositionValue.Raw("$x $y")
        val yVal = parseEdgeValue(y) ?: return PositionValue.Raw("$x $y")
        return PositionValue.TwoValue(xVal, yVal)
    }

    private fun parseFourValues(parts: List<String>): PositionValue {
        // e.g., "right 10px bottom 20px"
        return PositionValue.Raw(parts.joinToString(" "))
    }

    private fun parseEdgeValue(value: String): EdgeValue? {
        return when (value) {
            "center" -> EdgeValue.Center
            "top" -> EdgeValue.Top
            "bottom" -> EdgeValue.Bottom
            "left" -> EdgeValue.Left
            "right" -> EdgeValue.Right
            else -> {
                if (value.endsWith("%")) {
                    val percent = value.dropLast(1).toDoubleOrNull() ?: return null
                    EdgeValue.Percentage(IRPercentage(percent))
                } else {
                    val length = LengthParser.parse(value) ?: return null
                    EdgeValue.Length(length)
                }
            }
        }
    }

    private fun splitByComma(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in value) {
            when (char) {
                '(' -> { depth++; current.append(char) }
                ')' -> { depth--; current.append(char) }
                ',' -> {
                    if (depth == 0) {
                        if (current.isNotEmpty()) {
                            result.add(current.toString())
                            current = StringBuilder()
                        }
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }
}
