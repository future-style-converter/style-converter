package app.irmodels.properties.scrolling

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-padding-block-start` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-padding-block-start: auto | <length-percentage>`
 *
 * ## Description
 * Defines the scroll padding at the block-start edge (top in horizontal-tb writing mode).
 * This is a logical property that adapts to the writing mode.
 *
 * ## Examples
 * ```kotlin
 * ScrollPaddingBlockStartProperty(padding = ScrollPaddingValue.Length(IRLength(10.0, LengthUnit.PX)))
 * ```
 *
 * @property padding The block-start scroll padding value
 * @see [MDN scroll-padding-block-start](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-padding-block-start)
 */
@Serializable
data class ScrollPaddingBlockStartProperty(
    val padding: ScrollPaddingValue
) : IRProperty {
    override val propertyName = "scroll-padding-block-start"
}
