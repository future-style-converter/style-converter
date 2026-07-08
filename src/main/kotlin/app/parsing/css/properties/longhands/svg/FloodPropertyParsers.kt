package app.parsing.css.properties.longhands.svg

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.svg.FloodColorProperty
import app.irmodels.properties.svg.FloodOpacityProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ColorParser

object FloodColorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val color = ColorParser.parse(value.trim()) ?: return null
        return FloodColorProperty(color)
    }
}

object FloodOpacityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val num = if (trimmed.endsWith("%")) {
            val pct = trimmed.removeSuffix("%").toDoubleOrNull() ?: return null
            pct / 100.0
        } else {
            trimmed.toDoubleOrNull() ?: return null
        }
        if (num < 0 || num > 1) return null
        return FloodOpacityProperty(IRNumber(num))
    }
}
