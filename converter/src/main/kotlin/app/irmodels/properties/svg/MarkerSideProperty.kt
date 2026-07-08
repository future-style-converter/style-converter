package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class MarkerSideValue {
    MATCH,
    LEFT,
    RIGHT,
    LEFT_RIGHT
}

/**
 * Represents the CSS `marker-side` property.
 * Specifies on which side of a path markers are placed.
 */
@Serializable
data class MarkerSideProperty(
    val value: MarkerSideValue
) : IRProperty {
    override val propertyName = "marker-side"
}
