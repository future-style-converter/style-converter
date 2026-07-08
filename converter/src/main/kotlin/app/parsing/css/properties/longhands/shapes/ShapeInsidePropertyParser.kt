package app.parsing.css.properties.longhands.shapes

import app.irmodels.IRProperty
import app.irmodels.properties.shapes.ShapeInsideProperty
import app.irmodels.properties.shapes.ShapeInsideValue
import app.parsing.css.properties.longhands.PropertyParser

object ShapeInsidePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when {
            trimmed == "auto" -> ShapeInsideValue.Auto
            trimmed == "none" -> ShapeInsideValue.None
            else -> ShapeInsideValue.Shape(value.trim())
        }
        return ShapeInsideProperty(v)
    }
}
