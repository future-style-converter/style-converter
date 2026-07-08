package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.irmodels.properties.borders.CornerShapeProperty
import app.irmodels.properties.borders.CornerShapeValue
import app.parsing.css.properties.longhands.PropertyParser

object CornerShapePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "round" -> CornerShapeValue.ROUND
            "angle" -> CornerShapeValue.ANGLE
            "notch" -> CornerShapeValue.NOTCH
            "bevel" -> CornerShapeValue.BEVEL
            "scoop" -> CornerShapeValue.SCOOP
            "squircle" -> CornerShapeValue.SQUIRCLE
            else -> return null
        }
        return CornerShapeProperty(v)
    }
}
