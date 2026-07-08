package app.parsing.css.properties.longhands.appearance

import app.irmodels.IRProperty
import app.irmodels.properties.appearance.ImageRenderingQualityProperty
import app.irmodels.properties.appearance.ImageRenderingQualityValue
import app.parsing.css.properties.longhands.PropertyParser

object ImageRenderingQualityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "auto" -> ImageRenderingQualityValue.AUTO
            "high" -> ImageRenderingQualityValue.HIGH
            "low" -> ImageRenderingQualityValue.LOW
            else -> return null
        }
        return ImageRenderingQualityProperty(v)
    }
}
