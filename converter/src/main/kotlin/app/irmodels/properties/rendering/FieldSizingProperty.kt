package app.irmodels.properties.rendering

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FieldSizingValue {
    FIXED,
    CONTENT
}

/**
 * Represents the CSS `field-sizing` property.
 * Controls form control sizing behavior.
 */
@Serializable
data class FieldSizingProperty(
    val value: FieldSizingValue
) : IRProperty {
    override val propertyName = "field-sizing"
}
