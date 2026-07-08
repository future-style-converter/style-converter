package app.irmodels.properties.spacing

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class MinHeightProperty(
    val minHeight: MinWidthProperty.MinMaxValue
) : IRProperty {
    override val propertyName = "min-height"
}
