package app.irmodels.properties.container

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `container-name` property.
 * Names a container for use in container queries.
 */
@Serializable
data class ContainerNameProperty(
    val value: ContainerNameValue
) : IRProperty {
    override val propertyName = "container-name"
}

@Serializable
sealed interface ContainerNameValue {
    @Serializable
    @SerialName("none")
    data object None : ContainerNameValue

    @Serializable
    @SerialName("single")
    data class Single(val name: String) : ContainerNameValue

    @Serializable
    @SerialName("multiple")
    data class Multiple(val names: List<String>) : ContainerNameValue
}
