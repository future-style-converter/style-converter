package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `overscroll-behavior-block` property.
 *
 * ## CSS Property
 * **Syntax**: `overscroll-behavior-block: auto | contain | none`
 *
 * ## Description
 * Controls what happens when scrolling in the block direction (vertical in horizontal-tb)
 * reaches the edge of a scrolling area. This is a logical property.
 *
 * ## Examples
 * ```kotlin
 * OverscrollBehaviorBlockProperty(behavior = OverscrollBehavior.Auto)
 * OverscrollBehaviorBlockProperty(behavior = OverscrollBehavior.Contain)
 * OverscrollBehaviorBlockProperty(behavior = OverscrollBehavior.None)
 * ```
 *
 * @property behavior The overscroll behavior value
 * @see [MDN overscroll-behavior-block](https://developer.mozilla.org/en-US/docs/Web/CSS/overscroll-behavior-block)
 */
@Serializable
data class OverscrollBehaviorBlockProperty(
    val behavior: OverscrollBehavior
) : IRProperty {
    override val propertyName = "overscroll-behavior-block"
}
