package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import app.irmodels.IRLength
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-margin-block-start` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-margin-block-start: <length>`
 *
 * ## Description
 * Defines the scroll margin at the block-start edge (top in horizontal-tb writing mode).
 * This is a logical property that adapts to the writing mode.
 *
 * ## Examples
 * ```kotlin
 * ScrollMarginBlockStartProperty(margin = IRLength(10.0, LengthUnit.PX))
 * ```
 *
 * @property margin The block-start scroll margin value
 * @see [MDN scroll-margin-block-start](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-margin-block-start)
 */
@Serializable
data class ScrollMarginBlockStartProperty(
    val margin: IRLength
) : IRProperty {
    override val propertyName = "scroll-margin-block-start"
}
