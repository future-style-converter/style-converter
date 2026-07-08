package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.PositionTryFallbacksProperty
import app.parsing.css.properties.longhands.PropertyParser

object PositionTryFallbacksPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null

        val fallbacks = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (fallbacks.isEmpty()) return null

        return PositionTryFallbacksProperty(fallbacks)
    }
}
