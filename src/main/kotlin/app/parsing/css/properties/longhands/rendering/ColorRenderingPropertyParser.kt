package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRProperty
import app.irmodels.properties.rendering.ColorRenderingProperty
import app.irmodels.properties.rendering.ColorRenderingValue
import app.parsing.css.properties.longhands.PropertyParser

object ColorRenderingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val renderValue = when (trimmed) {
            "auto" -> ColorRenderingValue.AUTO
            "optimizespeed" -> ColorRenderingValue.OPTIMIZE_SPEED
            "optimizequality" -> ColorRenderingValue.OPTIMIZE_QUALITY
            else -> return null
        }
        return ColorRenderingProperty(renderValue)
    }
}
