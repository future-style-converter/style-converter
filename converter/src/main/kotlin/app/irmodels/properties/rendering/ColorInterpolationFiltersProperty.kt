package app.irmodels.properties.rendering

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `color-interpolation-filters` property (SVG).
 * Color space for SVG filter effects.
 */
@Serializable
data class ColorInterpolationFiltersProperty(
    val value: ColorInterpolationValue
) : IRProperty {
    override val propertyName = "color-interpolation-filters"
}
