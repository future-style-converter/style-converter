package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.*
import app.parsing.css.properties.longhands.PropertyParser

object GridTemplateRowsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle keywords
        val template = when {
            lower == "none" -> GridTemplate.None()
            lower == "auto" -> GridTemplate.Auto()
            lower == "subgrid" -> GridTemplate.Subgrid()
            lower.startsWith("subgrid") -> {
                val lineNames = parseSubgridLineNames(trimmed.substring(7).trim())
                GridTemplate.Subgrid(lineNames)
            }
            // Contains line names [name] - store as expression
            trimmed.contains("[") && trimmed.contains("]") -> {
                GridTemplate.Expression(trimmed)
            }
            // Complex nested functions
            isComplexExpression(lower) -> {
                GridTemplate.Expression(trimmed)
            }
            else -> {
                val tracks = GridTrackListParser.parseTrackList(lower)
                if (tracks != null) {
                    GridTemplate.TrackList(tracks)
                } else {
                    GridTemplate.Expression(trimmed)
                }
            }
        }
        return GridTemplateRowsProperty(template)
    }

    private fun parseSubgridLineNames(value: String): List<String>? {
        if (value.isEmpty()) return null
        val names = mutableListOf<String>()
        val regex = Regex("\\[([^\\]]+)\\]")
        regex.findAll(value).forEach { match ->
            names.add(match.groupValues[1])
        }
        return if (names.isEmpty()) null else names
    }

    private fun isComplexExpression(value: String): Boolean {
        val depth = countMaxParenDepth(value)
        return depth > 2 || value.contains("min(") || value.contains("max(") || value.contains("clamp(")
    }

    private fun countMaxParenDepth(value: String): Int {
        var max = 0
        var current = 0
        for (char in value) {
            when (char) {
                '(' -> { current++; if (current > max) max = current }
                ')' -> current--
            }
        }
        return max
    }
}
