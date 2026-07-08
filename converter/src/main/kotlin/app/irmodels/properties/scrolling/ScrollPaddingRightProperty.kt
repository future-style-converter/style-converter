package app.irmodels.properties.scrolling

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-padding-right` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-padding-right: auto | <length-percentage>`
 *
 * ## Description
 * Defines the right padding within the optimal viewing region of the scrollport.
 *
 * ## Examples
 * ```kotlin
 * ScrollPaddingRightProperty(padding = ScrollPaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ScrollPaddingRightProperty(padding = ScrollPaddingValue.Auto)
 * ```
 *
 * @property padding The right scroll padding value
 * @see [MDN scroll-padding-right](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-padding-right)
 */
@Serializable
data class ScrollPaddingRightProperty(
    val padding: ScrollPaddingValue
) : IRProperty {
    override val propertyName = "scroll-padding-right"
}
