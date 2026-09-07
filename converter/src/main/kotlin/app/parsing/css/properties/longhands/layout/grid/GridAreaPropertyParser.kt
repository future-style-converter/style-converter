package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.GridAreaProperty
import app.irmodels.properties.layout.grid.GridLine
import app.parsing.css.properties.longhands.PropertyParser

// A6#14: the private `parseGridLine` this file used to carry was the fifth copy
// of that reader; it now calls the shared GridLineParsing (same package).
// caseFold = false preserves `grid-area`'s case-preserving behaviour, which is
// the css-values-4-correct one for <custom-ident> line names.
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
            val line = GridLineParsing.parse(trimmed, caseFold = false) ?: return null
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
                val rowStart = GridLineParsing.parse(parts[0], caseFold = false) ?: return null
                val columnStart = GridLineParsing.parse(parts[1], caseFold = false) ?: return null
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
                val rowStart = GridLineParsing.parse(parts[0], caseFold = false) ?: return null
                val columnStart = GridLineParsing.parse(parts[1], caseFold = false) ?: return null
                val rowEnd = GridLineParsing.parse(parts[2], caseFold = false) ?: return null
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
                val rowStart = GridLineParsing.parse(parts[0], caseFold = false) ?: return null
                val columnStart = GridLineParsing.parse(parts[1], caseFold = false) ?: return null
                val rowEnd = GridLineParsing.parse(parts[2], caseFold = false) ?: return null
                val columnEnd = GridLineParsing.parse(parts[3], caseFold = false) ?: return null
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
}
