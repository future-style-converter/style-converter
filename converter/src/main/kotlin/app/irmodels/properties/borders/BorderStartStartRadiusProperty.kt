package app.irmodels.properties.borders

import app.irmodels.*
import app.irmodels.IRLength
import app.irmodels.IRPercentage
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-start-start-radius` property.
 *
 * ## CSS Property
 * **Syntax**: `border-start-start-radius: <length-percentage>{1,2}`
 *
 * ## Description
 * Defines the logical border radius at the block-start/inline-start corner.
 * This corresponds to border-top-left-radius in LTR horizontal-tb writing mode,
 * and border-top-right-radius in RTL.
 *
 * ## Examples
 * ```kotlin
 * BorderStartStartRadiusProperty(
 *     horizontal = BorderRadiusValue.Length(IRLength(8.0, LengthUnit.PX)),
 *     vertical = BorderRadiusValue.Length(IRLength(8.0, LengthUnit.PX))
 * )
 * ```
 *
 * @property horizontal Horizontal radius component
 * @property vertical Vertical radius component (defaults to horizontal if not specified)
 * @see [MDN border-start-start-radius](https://developer.mozilla.org/en-US/docs/Web/CSS/border-start-start-radius)
 */
@Serializable
data class BorderStartStartRadiusProperty(
    val horizontal: BorderRadiusValue,
    val vertical: BorderRadiusValue? = null
) : IRProperty {
    override val propertyName = "border-start-start-radius"

    companion object {
        fun circular(radius: BorderRadiusValue) = BorderStartStartRadiusProperty(radius, radius)
    }
}
