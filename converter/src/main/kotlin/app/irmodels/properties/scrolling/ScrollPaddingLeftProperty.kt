package app.irmodels.properties.scrolling

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-padding-left` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-padding-left: auto | <length-percentage>`
 *
 * ## Description
 * Defines the left padding within the optimal viewing region of the scrollport.
 *
 * ## Examples
 * ```kotlin
 * ScrollPaddingLeftProperty(padding = ScrollPaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ScrollPaddingLeftProperty(padding = ScrollPaddingValue.Auto)
 * ```
 *
 * @property padding The left scroll padding value
 * @see [MDN scroll-padding-left](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-padding-left)
 */
@Serializable
data class ScrollPaddingLeftProperty(
    val padding: ScrollPaddingValue
) : IRProperty {
    override val propertyName = "scroll-padding-left"
}
