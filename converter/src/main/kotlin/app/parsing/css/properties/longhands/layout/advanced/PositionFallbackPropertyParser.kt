package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.PositionFallbackProperty
import app.irmodels.properties.layout.advanced.PositionFallbackValue
import app.parsing.css.properties.longhands.PropertyParser

object PositionFallbackPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        val v = if (trimmed.lowercase() == "none") {
            PositionFallbackValue.None
        } else {
            PositionFallbackValue.Named(trimmed)
        }

        return PositionFallbackProperty(v)
    }
}
