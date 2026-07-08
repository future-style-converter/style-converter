package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.LineGridProperty
import app.irmodels.properties.typography.LineGridValue
import app.parsing.css.properties.longhands.PropertyParser

object LineGridPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val gridValue = when (trimmed) {
            "match-parent" -> LineGridValue.Match_Parent
            "create" -> LineGridValue.Create
            else -> {
                // If not a keyword, treat as a named grid identifier
                if (trimmed.isNotEmpty() && trimmed.matches(Regex("[a-zA-Z_][a-zA-Z0-9_-]*"))) {
                    LineGridValue.Named(trimmed)
                } else {
                    return null
                }
            }
        }

        return LineGridProperty(gridValue)
    }
}
