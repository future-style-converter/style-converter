package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.GridAutoTrackProperty
import app.irmodels.properties.layout.grid.GridTrackSize
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object GridAutoTrackPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trackSize = parseTrackSize(value.trim()) ?: return null
        return GridAutoTrackProperty(trackSize)
    }

    private fun parseTrackSize(s: String): GridTrackSize? {
        val trimmed = s.lowercase()

        return when {
            trimmed == "auto" -> GridTrackSize.Auto
            trimmed.startsWith("minmax(") && trimmed.endsWith(")") -> {
                val inner = trimmed.removePrefix("minmax(").removeSuffix(")").trim()
                val parts = inner.split(",").map { it.trim() }
                if (parts.size != 2) return null
                val min = parseTrackSize(parts[0]) ?: return null
                val max = parseTrackSize(parts[1]) ?: return null
                GridTrackSize.MinMax(min, max)
            }
            trimmed.startsWith("fit-content(") && trimmed.endsWith(")") -> {
                val inner = trimmed.removePrefix("fit-content(").removeSuffix(")").trim()
                val length = LengthParser.parse(inner) ?: return null
                GridTrackSize.FitContent(length)
            }
            else -> {
                val length = LengthParser.parse(s) ?: return null
                GridTrackSize.Length(length)
            }
        }
    }
}
