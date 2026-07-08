package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextRenderingProperty
import app.irmodels.properties.typography.TextRenderingValue
import app.parsing.css.properties.longhands.PropertyParser

object TextRenderingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val renderingValue = when (trimmed) {
            "auto" -> TextRenderingValue.AUTO
            "optimizespeed", "optimize-speed" -> TextRenderingValue.OPTIMIZE_SPEED
            "optimizelegibility", "optimize-legibility" -> TextRenderingValue.OPTIMIZE_LEGIBILITY
            "geometricprecision", "geometric-precision" -> TextRenderingValue.GEOMETRIC_PRECISION
            else -> return null
        }

        return TextRenderingProperty(renderingValue)
    }
}
