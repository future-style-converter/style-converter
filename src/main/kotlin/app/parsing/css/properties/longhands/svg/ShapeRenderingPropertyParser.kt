package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.ShapeRendering
import app.irmodels.properties.svg.ShapeRenderingProperty
import app.parsing.css.properties.longhands.PropertyParser

object ShapeRenderingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()
        val rendering = when (normalized) {
            "auto" -> ShapeRendering.AUTO
            "optimizespeed" -> ShapeRendering.OPTIMIZE_SPEED
            "crispedges" -> ShapeRendering.CRISP_EDGES
            "geometricprecision" -> ShapeRendering.GEOMETRIC_PRECISION
            else -> return null
        }
        return ShapeRenderingProperty(rendering)
    }
}
