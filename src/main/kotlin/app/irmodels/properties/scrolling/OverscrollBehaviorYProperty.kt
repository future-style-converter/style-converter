package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `overscroll-behavior-y` property.
 *
 * ## CSS Property
 * **Syntax**: `overscroll-behavior-y: auto | contain | none`
 *
 * ## Description
 * Controls what happens when vertical scrolling reaches the edge of a scrolling area.
 * Prevents scroll chaining in the vertical direction.
 *
 * ## Examples
 * ```kotlin
 * OverscrollBehaviorYProperty(behavior = OverscrollBehavior.Auto)
 * OverscrollBehaviorYProperty(behavior = OverscrollBehavior.Contain)
 * OverscrollBehaviorYProperty(behavior = OverscrollBehavior.None)
 * ```
 *
 * @property behavior The overscroll behavior value
 * @see [MDN overscroll-behavior-y](https://developer.mozilla.org/en-US/docs/Web/CSS/overscroll-behavior-y)
 */
@Serializable
data class OverscrollBehaviorYProperty(
    val behavior: OverscrollBehavior
) : IRProperty {
    override val propertyName = "overscroll-behavior-y"
}
