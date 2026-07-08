package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.StrokeLinecap
import app.irmodels.properties.svg.StrokeLinecapProperty
import app.parsing.css.properties.longhands.PropertyParser

object StrokeLinecapPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()
        val linecap = when (normalized) {
            "butt" -> StrokeLinecap.BUTT
            "round" -> StrokeLinecap.ROUND
            "square" -> StrokeLinecap.SQUARE
            else -> return null
        }
        return StrokeLinecapProperty(linecap)
    }
}
