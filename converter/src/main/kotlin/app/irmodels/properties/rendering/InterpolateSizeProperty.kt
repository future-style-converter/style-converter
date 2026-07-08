package app.irmodels.properties.rendering

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class InterpolateSizeValue {
    NUMERIC_ONLY,
    ALLOW_KEYWORDS
}

/**
 * Represents the CSS `interpolate-size` property.
 * Controls interpolation of intrinsic size values.
 */
@Serializable
data class InterpolateSizeProperty(
    val value: InterpolateSizeValue
) : IRProperty {
    override val propertyName = "interpolate-size"
}
