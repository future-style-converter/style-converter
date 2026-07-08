package app.parsing.css.properties.longhands.transforms

import app.irmodels.IRProperty
import app.irmodels.properties.transforms.TransformStyleProperty
import app.parsing.css.properties.longhands.PropertyParser

object TransformStylePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val style = when (trimmed) {
            "flat" -> TransformStyleProperty.TransformStyle.FLAT
            "preserve-3d" -> TransformStyleProperty.TransformStyle.PRESERVE_3D
            else -> return null
        }
        return TransformStyleProperty(style)
    }
}
