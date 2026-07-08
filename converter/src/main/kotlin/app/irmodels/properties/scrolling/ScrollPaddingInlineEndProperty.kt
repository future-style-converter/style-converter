package app.irmodels.properties.scrolling

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-padding-inline-end` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-padding-inline-end: auto | <length-percentage>`
 *
 * ## Description
 * Defines the scroll padding at the inline-end edge (right in LTR, left in RTL).
 * This is a logical property that adapts to the writing direction.
 *
 * ## Examples
 * ```kotlin
 * ScrollPaddingInlineEndProperty(padding = ScrollPaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ```
 *
 * @property padding The inline-end scroll padding value
 * @see [MDN scroll-padding-inline-end](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-padding-inline-end)
 */
@Serializable
data class ScrollPaddingInlineEndProperty(
    val padding: ScrollPaddingValue
) : IRProperty {
    override val propertyName = "scroll-padding-inline-end"
}
