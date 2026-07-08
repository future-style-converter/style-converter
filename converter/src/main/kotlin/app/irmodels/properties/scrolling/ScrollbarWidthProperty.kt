package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scrollbar-width` property.
 *
 * ## CSS Property
 * **Syntax**: `scrollbar-width: auto | thin | none`
 *
 * ## Description
 * Sets the width of the element's scrollbar when shown.
 *
 * @property width The scrollbar width
 * @see [MDN scrollbar-width](https://developer.mozilla.org/en-US/docs/Web/CSS/scrollbar-width)
 */
@Serializable
data class ScrollbarWidthProperty(
    val width: ScrollbarWidth
) : IRProperty {
    override val propertyName = "scrollbar-width"

    enum class ScrollbarWidth {
        AUTO,
        THIN,
        NONE
    }
}
