package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.MarkerSideProperty
import app.irmodels.properties.svg.MarkerSideValue
import app.parsing.css.properties.longhands.PropertyParser

object MarkerSidePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()
        val sideValue = when (normalized) {
            "match" -> MarkerSideValue.MATCH
            "left" -> MarkerSideValue.LEFT
            "right" -> MarkerSideValue.RIGHT
            "left-right" -> MarkerSideValue.LEFT_RIGHT
            else -> return null
        }
        return MarkerSideProperty(sideValue)
    }
}
