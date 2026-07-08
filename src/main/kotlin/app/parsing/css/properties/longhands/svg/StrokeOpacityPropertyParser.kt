package app.parsing.css.properties.longhands.svg

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.svg.StrokeOpacityProperty
import app.parsing.css.properties.longhands.PropertyParser

object StrokeOpacityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val opacity = trimmed.toDoubleOrNull() ?: return null
        if (opacity < 0.0 || opacity > 1.0) return null
        return StrokeOpacityProperty(IRNumber(opacity))
    }
}
