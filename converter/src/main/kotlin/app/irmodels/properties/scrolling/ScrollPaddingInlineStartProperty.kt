package app.irmodels.properties.scrolling

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-padding-inline-start` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-padding-inline-start: auto | <length-percentage>`
 *
 * ## Description
 * Defines the scroll padding at the inline-start edge (left in LTR, right in RTL).
 * This is a logical property that adapts to the writing direction.
 *
 * ## Examples
 * ```kotlin
 * ScrollPaddingInlineStartProperty(padding = ScrollPaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ```
 *
 * @property padding The inline-start scroll padding value
 * @see [MDN scroll-padding-inline-start](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-padding-inline-start)
 */
@Serializable
data class ScrollPaddingInlineStartProperty(
    val padding: ScrollPaddingValue
) : IRProperty {
    override val propertyName = "scroll-padding-inline-start"
}
