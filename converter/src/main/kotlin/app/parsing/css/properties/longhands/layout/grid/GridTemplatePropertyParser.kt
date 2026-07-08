package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.GridTemplateProperty
import app.parsing.css.properties.longhands.PropertyParser

object GridTemplatePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        if (trimmed.lowercase() == "none") {
            return GridTemplateProperty(null, null, null)
        }

        // Check if it's areas only (starts with a quote)
        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
            return GridTemplateProperty(areas = trimmed)
        }

        // Check for rows / columns syntax
        val slashIndex = trimmed.indexOf("/")
        if (slashIndex != -1) {
            val rows = trimmed.substring(0, slashIndex).trim()
            val columns = trimmed.substring(slashIndex + 1).trim()
            return GridTemplateProperty(rows = rows, columns = columns)
        }

        // Assume it's rows only or raw value
        return GridTemplateProperty(rows = trimmed)
    }
}
