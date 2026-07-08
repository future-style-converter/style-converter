package app.irmodels.properties.color

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BackgroundColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "background-color"
}
