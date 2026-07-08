package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRProperty
import app.irmodels.properties.rendering.PrintColorAdjustProperty
import app.irmodels.properties.rendering.PrintColorAdjustValue
import app.parsing.css.properties.longhands.PropertyParser

object PrintColorAdjustPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "economy" -> PrintColorAdjustValue.ECONOMY
            "exact" -> PrintColorAdjustValue.EXACT
            else -> return null
        }
        return PrintColorAdjustProperty(v)
    }
}
