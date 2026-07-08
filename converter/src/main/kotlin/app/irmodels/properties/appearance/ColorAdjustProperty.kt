package app.irmodels.properties.appearance

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ColorAdjustValue {
    ECONOMY,
    EXACT
}

/**
 * Represents the CSS `color-adjust` property.
 * Controls color adjustment behavior for printing.
 */
@Serializable
data class ColorAdjustProperty(
    val value: ColorAdjustValue
) : IRProperty {
    override val propertyName = "color-adjust"
}
