package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.PositionTryProperty
import app.parsing.css.properties.longhands.PropertyParser

object PositionTryPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        if (trimmed.lowercase() == "none") {
            return PositionTryProperty(emptyList())
        }

        // Parse comma-separated fallback names
        val fallbacks = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        return PositionTryProperty(fallbacks)
    }
}
