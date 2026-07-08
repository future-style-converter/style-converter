package app.irmodels.properties.rendering

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ColorInterpolationValue {
    AUTO,
    SRGB,
    LINEAR_RGB
}

/**
 * Represents the CSS `color-interpolation` property (SVG).
 * Color space for gradients and filters.
 */
@Serializable
data class ColorInterpolationProperty(
    val value: ColorInterpolationValue
) : IRProperty {
    override val propertyName = "color-interpolation"
}
