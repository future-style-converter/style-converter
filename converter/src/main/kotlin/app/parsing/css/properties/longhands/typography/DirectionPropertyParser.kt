package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.DirectionProperty
import app.parsing.css.properties.longhands.PropertyParser

object DirectionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val direction = when (trimmed) {
            "ltr" -> DirectionProperty.Direction.LTR
            "rtl" -> DirectionProperty.Direction.RTL
            else -> return null
        }
        return DirectionProperty(direction)
    }
}
