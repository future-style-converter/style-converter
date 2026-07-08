package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class LineSnapValue {
    NONE,
    BASELINE,
    CONTAIN
}

/**
 * Represents the CSS `line-snap` property.
 * Controls how lines snap to the line grid.
 */
@Serializable
data class LineSnapProperty(
    val value: LineSnapValue
) : IRProperty {
    override val propertyName = "line-snap"
}
