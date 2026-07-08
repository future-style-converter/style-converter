package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `overscroll-behavior-inline` property.
 *
 * ## CSS Property
 * **Syntax**: `overscroll-behavior-inline: auto | contain | none`
 *
 * ## Description
 * Controls what happens when scrolling in the inline direction (horizontal in horizontal-tb)
 * reaches the edge of a scrolling area. This is a logical property.
 *
 * ## Examples
 * ```kotlin
 * OverscrollBehaviorInlineProperty(behavior = OverscrollBehavior.Auto)
 * OverscrollBehaviorInlineProperty(behavior = OverscrollBehavior.Contain)
 * OverscrollBehaviorInlineProperty(behavior = OverscrollBehavior.None)
 * ```
 *
 * @property behavior The overscroll behavior value
 * @see [MDN overscroll-behavior-inline](https://developer.mozilla.org/en-US/docs/Web/CSS/overscroll-behavior-inline)
 */
@Serializable
data class OverscrollBehaviorInlineProperty(
    val behavior: OverscrollBehavior
) : IRProperty {
    override val propertyName = "overscroll-behavior-inline"
}
