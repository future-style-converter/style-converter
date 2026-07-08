package app.parsing.css.properties.longhands.math

import app.irmodels.IRProperty
import app.irmodels.properties.math.MathShiftProperty
import app.irmodels.properties.math.MathShiftValue
import app.parsing.css.properties.longhands.PropertyParser

object MathShiftPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val shiftValue = when (trimmed) {
            "normal" -> MathShiftValue.NORMAL
            "compact" -> MathShiftValue.COMPACT
            else -> return null
        }
        return MathShiftProperty(shiftValue)
    }
}
