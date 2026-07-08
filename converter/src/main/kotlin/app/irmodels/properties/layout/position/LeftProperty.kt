package app.irmodels.properties.layout.position

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class LeftProperty(
    val value: InsetValue
) : IRProperty {
    override val propertyName = "left"
}
