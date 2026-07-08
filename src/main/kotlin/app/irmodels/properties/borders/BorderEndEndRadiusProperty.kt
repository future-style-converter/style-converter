package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-end-end-radius` property.
 *
 * ## CSS Property
 * **Syntax**: `border-end-end-radius: <length-percentage>{1,2}`
 *
 * ## Description
 * Defines the logical border radius at the block-end/inline-end corner.
 * This corresponds to border-bottom-right-radius in LTR horizontal-tb writing mode,
 * and border-bottom-left-radius in RTL.
 *
 * ## Examples
 * ```kotlin
 * BorderEndEndRadiusProperty(
 *     horizontal = BorderRadiusValue.Length(IRLength(8.0, LengthUnit.PX)),
 *     vertical = BorderRadiusValue.Length(IRLength(8.0, LengthUnit.PX))
 * )
 * ```
 *
 * @property horizontal Horizontal radius component
 * @property vertical Vertical radius component (defaults to horizontal if not specified)
 * @see [MDN border-end-end-radius](https://developer.mozilla.org/en-US/docs/Web/CSS/border-end-end-radius)
 */
@Serializable
data class BorderEndEndRadiusProperty(
    val horizontal: BorderRadiusValue,
    val vertical: BorderRadiusValue? = null
) : IRProperty {
    override val propertyName = "border-end-end-radius"

    companion object {
        fun circular(radius: BorderRadiusValue) = BorderEndEndRadiusProperty(radius, radius)
    }
}
