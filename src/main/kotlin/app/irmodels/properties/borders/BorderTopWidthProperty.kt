package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * CSS `border-top-width` property - sets top border thickness.
 *
 * Uses BorderWidthValue with dual storage:
 * - `pixels`: Normalized width (thin=1px, medium=3px, thick=5px)
 * - `original`: Original CSS format for regeneration
 *
 * @see BorderWidthValue for normalization details
 */
@Serializable
data class BorderTopWidthProperty(
    val width: BorderWidthValue
) : IRProperty {
    override val propertyName = "border-top-width"
}
