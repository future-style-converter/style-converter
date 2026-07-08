package app.irmodels.properties.svg

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class StopColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "stop-color"
}
