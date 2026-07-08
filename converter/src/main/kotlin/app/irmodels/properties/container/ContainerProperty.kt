package app.irmodels.properties.container

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `container` shorthand property.
 * Establishes an element as a container for container queries.
 */
@Serializable
data class ContainerProperty(
    val name: String? = null,
    val type: ContainerTypeValue
) : IRProperty {
    override val propertyName = "container"
}

enum class ContainerTypeValue {
    SIZE,           // Both inline and block size
    INLINE_SIZE,    // Inline size only
    NORMAL          // Not a query container
}
