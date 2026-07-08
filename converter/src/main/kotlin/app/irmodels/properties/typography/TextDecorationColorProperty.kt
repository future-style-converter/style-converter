package app.irmodels.properties.typography

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TextDecorationColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "text-decoration-color"
}
