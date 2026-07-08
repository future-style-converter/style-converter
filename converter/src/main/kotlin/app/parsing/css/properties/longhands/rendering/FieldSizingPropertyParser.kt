package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRProperty
import app.irmodels.properties.rendering.FieldSizingProperty
import app.irmodels.properties.rendering.FieldSizingValue
import app.parsing.css.properties.longhands.PropertyParser

object FieldSizingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "fixed" -> FieldSizingValue.FIXED
            "content" -> FieldSizingValue.CONTENT
            else -> return null
        }
        return FieldSizingProperty(v)
    }
}
