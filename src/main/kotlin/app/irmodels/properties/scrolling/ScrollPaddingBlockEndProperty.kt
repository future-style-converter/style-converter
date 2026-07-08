package app.irmodels.properties.scrolling

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-padding-block-end` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-padding-block-end: auto | <length-percentage>`
 *
 * ## Description
 * Defines the scroll padding at the block-end edge (bottom in horizontal-tb writing mode).
 * This is a logical property that adapts to the writing mode.
 *
 * ## Examples
 * ```kotlin
 * ScrollPaddingBlockEndProperty(padding = ScrollPaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ```
 *
 * @property padding The block-end scroll padding value
 * @see [MDN scroll-padding-block-end](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-padding-block-end)
 */
@Serializable
data class ScrollPaddingBlockEndProperty(
    val padding: ScrollPaddingValue
) : IRProperty {
    override val propertyName = "scroll-padding-block-end"
}
