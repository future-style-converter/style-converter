package app.irmodels.properties.sizing

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `box-sizing` property.
 *
 * ## CSS Property
 * **Syntax**: `box-sizing: content-box | border-box`
 *
 * ## Description
 * Determines how the total width and height of an element is calculated.
 * - content-box: width/height = content only (default CSS behavior)
 * - border-box: width/height = content + padding + border
 *
 * @property sizing The box sizing model
 * @see [MDN box-sizing](https://developer.mozilla.org/en-US/docs/Web/CSS/box-sizing)
 */
@Serializable
data class BoxSizingProperty(
    val sizing: BoxSizing
) : IRProperty {
    override val propertyName = "box-sizing"

    enum class BoxSizing {
        /** Width/height = content only */
        CONTENT_BOX,

        /** Width/height = content + padding + border */
        BORDER_BOX
    }
}
