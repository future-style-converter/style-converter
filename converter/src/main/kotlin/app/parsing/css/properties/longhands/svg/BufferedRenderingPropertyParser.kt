package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.BufferedRenderingProperty
import app.irmodels.properties.svg.BufferedRenderingValue
import app.parsing.css.properties.longhands.PropertyParser

object BufferedRenderingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()
        val renderingValue = when (normalized) {
            "auto" -> BufferedRenderingValue.AUTO
            "dynamic" -> BufferedRenderingValue.DYNAMIC
            "static" -> BufferedRenderingValue.STATIC
            else -> return null
        }
        return BufferedRenderingProperty(renderingValue)
    }
}
