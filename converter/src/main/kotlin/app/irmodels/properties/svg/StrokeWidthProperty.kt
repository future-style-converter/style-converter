package app.irmodels.properties.svg

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class StrokeWidthProperty(
    val width: IRLength
) : IRProperty {
    override val propertyName = "stroke-width"
}
