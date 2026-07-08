package app.irmodels.properties.borders

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class BorderBoundaryValue {
    NONE,
    PARENT,
    DISPLAY
}

/**
 * Represents the CSS `border-boundary` property.
 * Specifies the element's boundary for border rendering.
 */
@Serializable
data class BorderBoundaryProperty(
    val value: BorderBoundaryValue
) : IRProperty {
    override val propertyName = "border-boundary"
}
