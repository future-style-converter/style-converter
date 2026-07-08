package app.irmodels.properties.spacing

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class MaxHeightProperty(
    val maxHeight: MaxWidthProperty.MaxValue
) : IRProperty {
    override val propertyName = "max-height"
}
