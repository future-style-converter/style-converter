package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontOpticalSizingValue {
    AUTO,
    NONE
}

/**
 * Represents the CSS `font-optical-sizing` property.
 * Controls whether font's optical size variations are enabled.
 */
@Serializable
data class FontOpticalSizingProperty(
    val value: FontOpticalSizingValue
) : IRProperty {
    override val propertyName = "font-optical-sizing"
}
