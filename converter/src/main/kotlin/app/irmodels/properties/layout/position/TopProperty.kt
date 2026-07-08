package app.irmodels.properties.layout.position

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TopProperty(
    val value: InsetValue
) : IRProperty {
    override val propertyName = "top"
}
