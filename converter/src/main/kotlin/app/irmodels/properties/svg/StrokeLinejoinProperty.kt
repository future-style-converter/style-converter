package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class StrokeLinejoin {
    MITER,
    ROUND,
    BEVEL,
    ARCS,
    MITER_CLIP
}

@Serializable
data class StrokeLinejoinProperty(
    val linejoin: StrokeLinejoin
) : IRProperty {
    override val propertyName = "stroke-linejoin"
}
