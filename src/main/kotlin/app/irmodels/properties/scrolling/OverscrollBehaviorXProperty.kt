package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `overscroll-behavior-x` property.
 *
 * ## CSS Property
 * **Syntax**: `overscroll-behavior-x: auto | contain | none`
 *
 * ## Description
 * Controls what happens when horizontal scrolling reaches the edge of a scrolling area.
 * Prevents scroll chaining in the horizontal direction.
 *
 * ## Examples
 * ```kotlin
 * OverscrollBehaviorXProperty(behavior = OverscrollBehavior.Auto)
 * OverscrollBehaviorXProperty(behavior = OverscrollBehavior.Contain)
 * OverscrollBehaviorXProperty(behavior = OverscrollBehavior.None)
 * ```
 *
 * @property behavior The overscroll behavior value
 * @see [MDN overscroll-behavior-x](https://developer.mozilla.org/en-US/docs/Web/CSS/overscroll-behavior-x)
 */
@Serializable
data class OverscrollBehaviorXProperty(
    val behavior: OverscrollBehavior
) : IRProperty {
    override val propertyName = "overscroll-behavior-x"
}
