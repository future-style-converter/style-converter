package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.layout.grid.*

/**
 * Parser for `grid-row-start` property.
 */
object GridRowStartPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val gridLine = parseGridLine(value) ?: return null
        return GridRowStartProperty(gridLine)
    }

    private fun parseGridLine(value: String): GridLine? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "auto") {
            return GridLine.Auto()
        }
        if (trimmed.startsWith("span ")) {
            val spanValue = trimmed.substring(5).trim()
            val spanCount = spanValue.toIntOrNull()
            return if (spanCount != null) {
                GridLine.Span(spanCount)
            } else {
                GridLine.SpanName(spanValue)
            }
        }
        val lineNumber = trimmed.toIntOrNull()
        if (lineNumber != null) {
            return GridLine.LineNumber(lineNumber)
        }
        return GridLine.LineName(trimmed)
    }
}
