package app.irmodels.properties.transforms

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `transform-box` property.
 *
 * ## CSS Property
 * **Syntax**: `transform-box: content-box | border-box | fill-box | stroke-box | view-box`
 *
 * ## Description
 * Defines the layout box to which the transform and transform-origin properties relate.
 *
 * @property box The transform box value
 * @see [MDN transform-box](https://developer.mozilla.org/en-US/docs/Web/CSS/transform-box)
 */
@Serializable
data class TransformBoxProperty(
    val box: TransformBox
) : IRProperty {
    override val propertyName = "transform-box"

    enum class TransformBox {
        CONTENT_BOX,
        BORDER_BOX,
        FILL_BOX,
        STROKE_BOX,
        VIEW_BOX
    }
}
