package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `offset-path` property.
 * Specifies a motion path for an element.
 */
@Serializable
data class OffsetPathProperty(
    val value: OffsetPathValue
) : IRProperty {
    override val propertyName = "offset-path"
}
