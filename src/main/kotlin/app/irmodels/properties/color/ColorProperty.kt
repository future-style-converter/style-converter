package app.irmodels.properties.color

import app.irmodels.IRColor
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * CSS `color` property - sets the foreground/text color.
 *
 * Uses IRColor which provides:
 * - `srgb`: Normalized sRGB (0-1 floats) for cross-platform generators
 * - `representation`: Original CSS format for regeneration
 *
 * @see IRColor for color representation details and supported formats
 */
@Serializable
data class ColorProperty(
    val color: IRColor
) : IRProperty {
    override val propertyName = "color"
}
