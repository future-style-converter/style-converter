package app.irmodels.properties.transforms

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `backface-visibility` property.
 *
 * ## CSS Property
 * **Syntax**: `backface-visibility: visible | hidden`
 *
 * ## Description
 * Determines whether the back face of an element is visible when turned towards the user.
 * Useful for 3D transforms and flip animations.
 *
 * @property visibility The backface visibility value
 * @see [MDN backface-visibility](https://developer.mozilla.org/en-US/docs/Web/CSS/backface-visibility)
 */
@Serializable
data class BackfaceVisibilityProperty(
    val visibility: BackfaceVisibility
) : IRProperty {
    override val propertyName = "backface-visibility"

    enum class BackfaceVisibility {
        VISIBLE,
        HIDDEN
    }
}
