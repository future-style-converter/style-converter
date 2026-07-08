package app.parsing.css.properties.longhands.images

import app.irmodels.IRProperty
import app.irmodels.properties.images.ImageRenderingProperty
import app.parsing.css.properties.longhands.PropertyParser

object ImageRenderingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val rendering = when (value.trim().lowercase()) {
            "auto" -> ImageRenderingProperty.ImageRendering.AUTO
            "crisp-edges" -> ImageRenderingProperty.ImageRendering.CRISP_EDGES
            "pixelated" -> ImageRenderingProperty.ImageRendering.PIXELATED
            "smooth" -> ImageRenderingProperty.ImageRendering.SMOOTH
            "high-quality" -> ImageRenderingProperty.ImageRendering.HIGH_QUALITY
            else -> return null
        }
        return ImageRenderingProperty(rendering)
    }
}
