package app.irmodels.properties.transforms

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `transform-style` property.
 *
 * ## CSS Property
 * **Syntax**: `transform-style: flat | preserve-3d`
 *
 * ## Description
 * Sets whether children of an element are positioned in 3D space or flattened.
 *
 * @property style The transform style value
 * @see [MDN transform-style](https://developer.mozilla.org/en-US/docs/Web/CSS/transform-style)
 */
@Serializable
data class TransformStyleProperty(
    val style: TransformStyle
) : IRProperty {
    override val propertyName = "transform-style"

    enum class TransformStyle {
        FLAT,
        PRESERVE_3D
    }
}
