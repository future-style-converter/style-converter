package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRProperty
import app.irmodels.properties.rendering.ColorInterpolationProperty
import app.irmodels.properties.rendering.ColorInterpolationValue
import app.parsing.css.properties.longhands.PropertyParser

object ColorInterpolationPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val colorValue = when (trimmed) {
            "auto" -> ColorInterpolationValue.AUTO
            "srgb" -> ColorInterpolationValue.SRGB
            "linearrgb", "linear-rgb" -> ColorInterpolationValue.LINEAR_RGB
            else -> return null
        }
        return ColorInterpolationProperty(colorValue)
    }
}
