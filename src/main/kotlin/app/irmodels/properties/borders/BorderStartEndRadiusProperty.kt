package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-start-end-radius` property.
 *
 * ## CSS Property
 * **Syntax**: `border-start-end-radius: <length-percentage>{1,2}`
 *
 * ## Description
 * Defines the logical border radius at the block-start/inline-end corner.
 * This corresponds to border-top-right-radius in LTR horizontal-tb writing mode,
 * and border-top-left-radius in RTL.
 *
 * ## Examples
 * ```kotlin
 * BorderStartEndRadiusProperty(
 *     horizontal = BorderRadiusValue.Length(IRLength(8.0, LengthUnit.PX)),
 *     vertical = BorderRadiusValue.Length(IRLength(8.0, LengthUnit.PX))
 * )
 * ```
 *
 * @property horizontal Horizontal radius component
 * @property vertical Vertical radius component (defaults to horizontal if not specified)
 * @see [MDN border-start-end-radius](https://developer.mozilla.org/en-US/docs/Web/CSS/border-start-end-radius)
 */
@Serializable
data class BorderStartEndRadiusProperty(
    val horizontal: BorderRadiusValue,
    val vertical: BorderRadiusValue? = null
) : IRProperty {
    override val propertyName = "border-start-end-radius"

    companion object {
        fun circular(radius: BorderRadiusValue) = BorderStartEndRadiusProperty(radius, radius)
    }
}
