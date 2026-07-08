package app.parsing.css.properties.longhands.interactions

import app.irmodels.IRProperty
import app.irmodels.properties.interactions.CaretShapeProperty
import app.irmodels.properties.interactions.CaretShapeValue
import app.parsing.css.properties.longhands.PropertyParser

object CaretShapePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "auto" -> CaretShapeValue.AUTO
            "bar" -> CaretShapeValue.BAR
            "block" -> CaretShapeValue.BLOCK
            "underscore" -> CaretShapeValue.UNDERSCORE
            else -> return null
        }
        return CaretShapeProperty(v)
    }
}
