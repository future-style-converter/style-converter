package app.irmodels.properties.spacing

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class HeightProperty(
    val height: WidthProperty.WidthValue
) : IRProperty {
    override val propertyName = "height"
}
