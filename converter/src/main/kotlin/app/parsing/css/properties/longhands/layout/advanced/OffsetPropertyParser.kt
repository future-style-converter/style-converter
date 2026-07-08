package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.OffsetProperty
import app.irmodels.properties.layout.advanced.OffsetPathValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Parser for the CSS `offset` shorthand property.
 *
 * Syntax: <offset-path> [ <offset-distance> <offset-rotate> ]?
 *
 * Examples:
 * - "none" → OffsetProperty(None, null, null)
 * - "path('M 0 0 L 100 100')" → OffsetProperty(PathString(...), null, null)
 * - "path('M 0 0 L 100 100') 50%" → OffsetProperty(PathString(...), Percentage(50), null)
 * - "path('M 0 0 L 100 100') 50% 45deg" → OffsetProperty(PathString(...), Percentage(50), Angle(45deg))
 */
object OffsetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle "none" keyword
        if (trimmed == "none") {
            return OffsetProperty(
                path = OffsetPathValue.None,
                distance = null,
                rotate = null
            )
        }

        // Try to parse path component
        val pathValue = parsePathValue(trimmed) ?: return null

        // Extract remaining parts after path
        val pathEndIndex = findPathEndIndex(trimmed)
        val remaining = if (pathEndIndex < trimmed.length) {
            trimmed.substring(pathEndIndex).trim()
        } else {
            ""
        }

        if (remaining.isEmpty()) {
            return OffsetProperty(path = pathValue, distance = null, rotate = null)
        }

        // Parse optional distance and rotate
        val parts = remaining.split(Regex("\\s+"))

        val distance = if (parts.isNotEmpty()) {
            PercentageParser.parse(parts[0])
        } else {
            null
        }

        val rotate = if (parts.size >= 2) {
            AngleParser.parse(parts[1])
        } else {
            null
        }

        return OffsetProperty(path = pathValue, distance = distance, rotate = rotate)
    }

    private fun parsePathValue(value: String): OffsetPathValue? {
        if (value == "none") {
            return OffsetPathValue.None
        }

        // Parse path() function
        val pathMatch = """path\(['"](.+?)['"]\)""".toRegex().find(value)
        if (pathMatch != null) {
            val pathData = pathMatch.groupValues[1]
            return OffsetPathValue.PathString(pathData)
        }

        return null
    }

    private fun findPathEndIndex(value: String): Int {
        // Find the end of the path() function
        val pathMatch = """path\(['"](.+?)['"]\)""".toRegex().find(value)
        return pathMatch?.range?.last?.plus(1) ?: 0
    }
}
