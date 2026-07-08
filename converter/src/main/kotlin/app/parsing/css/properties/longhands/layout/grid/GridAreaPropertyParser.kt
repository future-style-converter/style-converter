package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.GridAreaProperty
import app.irmodels.properties.layout.grid.GridLine
import app.parsing.css.properties.longhands.PropertyParser

object GridAreaPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "auto") {
            return GridAreaProperty(GridAreaProperty.GridAreaValue.Auto())
        }

        if (!trimmed.contains('/')) {
            if (trimmed.toIntOrNull() == null && !trimmed.startsWith("span")) {
                return GridAreaProperty(GridAreaProperty.GridAreaValue.AreaName(trimmed))
            }
            val line = parseGridLine(trimmed) ?: return null
            return GridAreaProperty(
                GridAreaProperty.GridAreaValue.Lines(
                    rowStart = line,
                    columnStart = line,
                    rowEnd = line,
                    columnEnd = line
                )
            )
        }

        val parts = trimmed.split('/').map { it.trim() }
        return when (parts.size) {
            2 -> {
                val rowStart = parseGridLine(parts[0]) ?: return null
                val columnStart = parseGridLine(parts[1]) ?: return null
                GridAreaProperty(
                    GridAreaProperty.GridAreaValue.Lines(
                        rowStart = rowStart,
                        columnStart = columnStart,
                        rowEnd = GridLine.Auto(),
                        columnEnd = GridLine.Auto()
                    )
                )
            }
            3 -> {
                val rowStart = parseGridLine(parts[0]) ?: return null
                val columnStart = parseGridLine(parts[1]) ?: return null
                val rowEnd = parseGridLine(parts[2]) ?: return null
                GridAreaProperty(
                    GridAreaProperty.GridAreaValue.Lines(
                        rowStart = rowStart,
                        columnStart = columnStart,
                        rowEnd = rowEnd,
                        columnEnd = GridLine.Auto()
                    )
                )
            }
            4 -> {
                val rowStart = parseGridLine(parts[0]) ?: return null
                val columnStart = parseGridLine(parts[1]) ?: return null
                val rowEnd = parseGridLine(parts[2]) ?: return null
                val columnEnd = parseGridLine(parts[3]) ?: return null
                GridAreaProperty(
                    GridAreaProperty.GridAreaValue.Lines(
                        rowStart = rowStart,
                        columnStart = columnStart,
                        rowEnd = rowEnd,
                        columnEnd = columnEnd
                    )
                )
            }
            else -> null
        }
    }

    private fun parseGridLine(value: String): GridLine? {
        val trimmed = value.trim()
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
