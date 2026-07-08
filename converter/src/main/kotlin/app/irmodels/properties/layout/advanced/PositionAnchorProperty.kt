package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `position-anchor` property.
 * Associates an element with a named anchor.
 */
@Serializable
data class PositionAnchorProperty(
    val name: String
) : IRProperty {
    override val propertyName = "position-anchor"
}
