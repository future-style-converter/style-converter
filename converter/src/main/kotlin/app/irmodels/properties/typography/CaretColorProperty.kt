package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `caret-color` property.
 *
 * ## CSS Property
 * **Syntax**: `caret-color: auto | <color>`
 *
 * ## Description
 * Sets the color of the text cursor (caret) in input fields and editable elements.
 *
 * @property color The caret color value
 * @see [MDN caret-color](https://developer.mozilla.org/en-US/docs/Web/CSS/caret-color)
 */
@Serializable
data class CaretColorProperty(
    val color: CaretColor
) : IRProperty {
    override val propertyName = "caret-color"

    @Serializable
    sealed interface CaretColor {
        @Serializable
        @SerialName("auto")
        data class Auto(val unit: Unit = Unit) : CaretColor

        @Serializable
        @SerialName("color")
        data class ColorValue(val color: IRColor) : CaretColor
    }
}
