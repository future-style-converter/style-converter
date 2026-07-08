package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `text-underline-position` property.
 *
 * ## CSS Property
 * **Syntax**: `text-underline-position: auto | from-font | under | left | right`
 *
 * ## Description
 * Sets the position of underlines on text.
 *
 * @property position The underline position
 * @see [MDN text-underline-position](https://developer.mozilla.org/en-US/docs/Web/CSS/text-underline-position)
 */
@Serializable
data class TextUnderlinePositionProperty(
    val position: List<Position>
) : IRProperty {
    override val propertyName = "text-underline-position"

    enum class Position {
        AUTO,
        FROM_FONT,
        UNDER,
        LEFT,
        RIGHT
    }
}
