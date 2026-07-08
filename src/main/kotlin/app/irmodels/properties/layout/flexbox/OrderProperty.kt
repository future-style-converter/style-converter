package app.irmodels.properties.layout.flexbox

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class OrderProperty(
    val value: Int
) : IRProperty {
    override val propertyName = "order"
}
