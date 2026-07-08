package app.parsing.css.properties.longhands.math

import app.irmodels.IRProperty
import app.irmodels.properties.math.MathStyleProperty
import app.irmodels.properties.math.MathStyleValue
import app.parsing.css.properties.longhands.PropertyParser

object MathStylePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val styleValue = when (trimmed) {
            "normal" -> MathStyleValue.NORMAL
            "compact" -> MathStyleValue.COMPACT
            else -> return null
        }
        return MathStyleProperty(styleValue)
    }
}
