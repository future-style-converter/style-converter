package app.irmodels.properties.borders

import app.irmodels.IRProperty
import app.irmodels.IRLength
import app.irmodels.IRNumber
import app.irmodels.IRPercentage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-image-width` property.
 *
 * ## CSS Property
 * **Syntax**: `border-image-width: [ <length-percentage> | <number> | auto ]{1,4}`
 *
 * ## Description
 * Specifies the width of the border image. Scales the border image slice.
 *
 * ## Examples
 * ```kotlin
 * BorderImageWidthProperty(
 *     top = BorderImageWidthValue.Length(IRLength(10.0, LengthUnit.PX)),
 *     right = BorderImageWidthValue.Auto,
 *     bottom = BorderImageWidthValue.Length(IRLength(10.0, LengthUnit.PX)),
 *     left = BorderImageWidthValue.Auto
 * )
 * ```
 *
 * @property top Top border image width
 * @property right Right border image width
 * @property bottom Bottom border image width
 * @property left Left border image width
 * @see [MDN border-image-width](https://developer.mozilla.org/en-US/docs/Web/CSS/border-image-width)
 */
@Serializable
data class BorderImageWidthProperty(
    val top: BorderImageWidthValue,
    val right: BorderImageWidthValue,
    val bottom: BorderImageWidthValue,
    val left: BorderImageWidthValue
) : IRProperty {
    override val propertyName = "border-image-width"

    companion object {
        fun all(value: BorderImageWidthValue) =
            BorderImageWidthProperty(value, value, value, value)
    }
}

/**
 * Represents border-image-width values.
 */
@Serializable
sealed interface BorderImageWidthValue {
    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : BorderImageWidthValue

    @Serializable
    @SerialName("number")
    data class Number(val value: IRNumber) : BorderImageWidthValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : BorderImageWidthValue

    @Serializable
    @SerialName("auto")
    data object Auto : BorderImageWidthValue
}
