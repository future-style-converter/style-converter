package app.irmodels.properties.color

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class DynamicRangeLimitValue {
    STANDARD, HIGH, CONSTRAINED_HIGH
}

/**
 * Represents the CSS `dynamic-range-limit` property.
 * Controls HDR rendering for an element.
 */
@Serializable
data class DynamicRangeLimitProperty(
    val value: DynamicRangeLimitValue
) : IRProperty {
    override val propertyName = "dynamic-range-limit"
}
