package app.parsing.css.properties.longhands.layout.position

import app.irmodels.IRProperty
import app.irmodels.properties.layout.FloatProperty
import app.parsing.css.properties.longhands.PropertyParser

object FloatPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val float = when (trimmed) {
            "none" -> FloatProperty.Float.NONE
            "left" -> FloatProperty.Float.LEFT
            "right" -> FloatProperty.Float.RIGHT
            "inline-start" -> FloatProperty.Float.INLINE_START
            "inline-end" -> FloatProperty.Float.INLINE_END
            else -> return null
        }
        return FloatProperty(float)
    }
}
