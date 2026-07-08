package app.irmodels.properties.borders

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-end-start-radius` property.
 *
 * ## CSS Property
 * **Syntax**: `border-end-start-radius: <length-percentage>{1,2}`
 *
 * ## Description
 * Defines the logical border radius at the block-end/inline-start corner.
 * This corresponds to border-bottom-left-radius in LTR horizontal-tb writing mode,
 * and border-bottom-right-radius in RTL.
 *
 * ## Examples
 * ```kotlin
 * BorderEndStartRadiusProperty(
 *     horizontal = BorderRadiusValue.Length(IRLength(8.0, LengthUnit.PX)),
 *     vertical = BorderRadiusValue.Length(IRLength(8.0, LengthUnit.PX))
 * )
 * ```
 *
 * @property horizontal Horizontal radius component
 * @property vertical Vertical radius component (defaults to horizontal if not specified)
 * @see [MDN border-end-start-radius](https://developer.mozilla.org/en-US/docs/Web/CSS/border-end-start-radius)
 */
@Serializable
data class BorderEndStartRadiusProperty(
    val horizontal: BorderRadiusValue,
    val vertical: BorderRadiusValue? = null
) : IRProperty {
    override val propertyName = "border-end-start-radius"

    companion object {
        fun circular(radius: BorderRadiusValue) = BorderEndStartRadiusProperty(radius, radius)
    }
}
