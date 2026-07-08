package app.irmodels.properties.borders

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class OutlineColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "outline-color"
}
