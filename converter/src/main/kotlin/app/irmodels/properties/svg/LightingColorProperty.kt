package app.irmodels.properties.svg

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class LightingColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "lighting-color"
}
