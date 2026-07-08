package app.irmodels.properties.container

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `container-type` property.
 * Specifies the type of containment for container queries.
 */
@Serializable
data class ContainerTypeProperty(
    val type: ContainerTypeValue
) : IRProperty {
    override val propertyName = "container-type"
}
