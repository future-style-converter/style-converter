package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ShapeRendering {
    AUTO,
    OPTIMIZE_SPEED,
    CRISP_EDGES,
    GEOMETRIC_PRECISION
}

@Serializable
data class ShapeRenderingProperty(
    val rendering: ShapeRendering
) : IRProperty {
    override val propertyName = "shape-rendering"
}
