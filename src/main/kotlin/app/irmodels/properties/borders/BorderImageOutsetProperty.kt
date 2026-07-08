package app.irmodels.properties.borders

import app.irmodels.IRProperty
import app.irmodels.IRLength
import app.irmodels.IRNumber
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-image-outset` property.
 *
 * ## CSS Property
 * **Syntax**: `border-image-outset: [ <length> | <number> ]{1,4}`
 *
 * ## Description
 * Specifies the distance by which the border image extends beyond the border box.
 *
 * ## Examples
 * ```kotlin
 * BorderImageOutsetProperty(
 *     top = BorderImageOutsetValue.Length(IRLength(10.0, LengthUnit.PX)),
 *     right = BorderImageOutsetValue.Number(IRNumber(1.0)),
 *     bottom = BorderImageOutsetValue.Length(IRLength(10.0, LengthUnit.PX)),
 *     left = BorderImageOutsetValue.Number(IRNumber(1.0))
 * )
 * ```
 *
 * @property top Top border image outset
 * @property right Right border image outset
 * @property bottom Bottom border image outset
 * @property left Left border image outset
 * @see [MDN border-image-outset](https://developer.mozilla.org/en-US/docs/Web/CSS/border-image-outset)
 */
@Serializable
data class BorderImageOutsetProperty(
    val top: BorderImageOutsetValue,
    val right: BorderImageOutsetValue,
    val bottom: BorderImageOutsetValue,
    val left: BorderImageOutsetValue
) : IRProperty {
    override val propertyName = "border-image-outset"

    companion object {
        fun all(value: BorderImageOutsetValue) =
            BorderImageOutsetProperty(value, value, value, value)
    }
}

/**
 * Represents border-image-outset values.
 */
@Serializable
sealed interface BorderImageOutsetValue {
    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : BorderImageOutsetValue

    @Serializable
    @SerialName("number")
    data class Number(val value: IRNumber) : BorderImageOutsetValue
}
