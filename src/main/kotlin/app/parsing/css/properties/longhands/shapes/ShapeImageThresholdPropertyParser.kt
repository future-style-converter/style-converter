package app.parsing.css.properties.longhands.shapes

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.shapes.ShapeImageThresholdProperty
import app.parsing.css.properties.longhands.PropertyParser

object ShapeImageThresholdPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val threshold = trimmed.toDoubleOrNull() ?: return null
        if (threshold < 0.0 || threshold > 1.0) return null
        return ShapeImageThresholdProperty(IRNumber(threshold))
    }
}
