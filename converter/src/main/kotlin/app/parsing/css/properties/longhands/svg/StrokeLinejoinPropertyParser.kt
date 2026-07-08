package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.StrokeLinejoin
import app.irmodels.properties.svg.StrokeLinejoinProperty
import app.parsing.css.properties.longhands.PropertyParser

object StrokeLinejoinPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()
        val linejoin = when (normalized) {
            "miter" -> StrokeLinejoin.MITER
            "round" -> StrokeLinejoin.ROUND
            "bevel" -> StrokeLinejoin.BEVEL
            "arcs" -> StrokeLinejoin.ARCS
            "miter-clip" -> StrokeLinejoin.MITER_CLIP
            else -> return null
        }
        return StrokeLinejoinProperty(linejoin)
    }
}
