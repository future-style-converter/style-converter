package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.irmodels.properties.borders.BorderBoundaryProperty
import app.irmodels.properties.borders.BorderBoundaryValue
import app.parsing.css.properties.longhands.PropertyParser

object BorderBoundaryPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val boundaryValue = when (trimmed) {
            "none" -> BorderBoundaryValue.NONE
            "parent" -> BorderBoundaryValue.PARENT
            "display" -> BorderBoundaryValue.DISPLAY
            else -> return null
        }
        return BorderBoundaryProperty(boundaryValue)
    }
}
