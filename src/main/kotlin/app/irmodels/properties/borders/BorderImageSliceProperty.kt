package app.irmodels.properties.borders

import app.irmodels.IRProperty
import app.irmodels.IRNumber
import app.irmodels.IRPercentage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `border-image-slice` property.
 *
 * ## CSS Property
 * **Syntax**: `border-image-slice: <number-percentage>{1,4} && fill?`
 *
 * ## Description
 * Divides the border image into regions for the corners, edges, and middle.
 * Values represent inward offsets from the edges of the image.
 *
 * ## Examples
 * ```kotlin
 * // 30 pixels from all sides
 * BorderImageSliceProperty(
 *     top = BorderImageSliceValue.Number(IRNumber(30.0)),
 *     right = BorderImageSliceValue.Number(IRNumber(30.0)),
 *     bottom = BorderImageSliceValue.Number(IRNumber(30.0)),
 *     left = BorderImageSliceValue.Number(IRNumber(30.0)),
 *     fill = false
 * )
 * ```
 *
 * @property top Top slice value
 * @property right Right slice value
 * @property bottom Bottom slice value
 * @property left Left slice value
 * @property fill Whether to preserve the middle region
 * @see [MDN border-image-slice](https://developer.mozilla.org/en-US/docs/Web/CSS/border-image-slice)
 */
@Serializable
data class BorderImageSliceProperty(
    val top: BorderImageSliceValue,
    val right: BorderImageSliceValue,
    val bottom: BorderImageSliceValue,
    val left: BorderImageSliceValue,
    val fill: Boolean = false
) : IRProperty {
    override val propertyName = "border-image-slice"

    companion object {
        fun all(value: BorderImageSliceValue, fill: Boolean = false) =
            BorderImageSliceProperty(value, value, value, value, fill)
    }
}

/**
 * Represents border-image-slice values.
 */
@Serializable
sealed interface BorderImageSliceValue {
    @Serializable
    @SerialName("number")
    data class Number(val value: IRNumber) : BorderImageSliceValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : BorderImageSliceValue
}
