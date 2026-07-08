package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import app.irmodels.IRLength
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scroll-margin-block-end` property.
 *
 * ## CSS Property
 * **Syntax**: `scroll-margin-block-end: <length>`
 *
 * ## Description
 * Defines the scroll margin at the block-end edge (bottom in horizontal-tb writing mode).
 * This is a logical property that adapts to the writing mode.
 *
 * ## Examples
 * ```kotlin
 * ScrollMarginBlockEndProperty(margin = IRLength(10.0, LengthUnit.PX))
 * ```
 *
 * @property margin The block-end scroll margin value
 * @see [MDN scroll-margin-block-end](https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-margin-block-end)
 */
@Serializable
data class ScrollMarginBlockEndProperty(
    val margin: IRLength
) : IRProperty {
    override val propertyName = "scroll-margin-block-end"
}
