package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class StrokeLinecap {
    BUTT,
    ROUND,
    SQUARE
}

@Serializable
data class StrokeLinecapProperty(
    val linecap: StrokeLinecap
) : IRProperty {
    override val propertyName = "stroke-linecap"
}
