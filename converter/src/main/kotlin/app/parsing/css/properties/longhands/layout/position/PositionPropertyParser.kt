package app.parsing.css.properties.longhands.layout.position

import app.irmodels.IRProperty
import app.irmodels.properties.layout.position.PositionProperty
import app.parsing.css.properties.longhands.PropertyParser

object PositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val position = when (trimmed) {
            "static" -> PositionProperty.Position.STATIC
            "relative" -> PositionProperty.Position.RELATIVE
            "absolute" -> PositionProperty.Position.ABSOLUTE
            "fixed" -> PositionProperty.Position.FIXED
            "sticky" -> PositionProperty.Position.STICKY
            else -> return null
        }
        return PositionProperty(position)
    }
}
