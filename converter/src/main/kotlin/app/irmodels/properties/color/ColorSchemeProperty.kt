package app.irmodels.properties.color

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `color-scheme` property.
 *
 * ## CSS Property
 * **Syntax**: `color-scheme: normal | light | dark | light dark`
 *
 * ## Description
 * Indicates which color schemes an element can be rendered in.
 * Allows the element to adapt to the user's preferred color scheme (light/dark mode).
 *
 * @property schemes The color schemes supported
 * @see [MDN color-scheme](https://developer.mozilla.org/en-US/docs/Web/CSS/color-scheme)
 */
@Serializable
data class ColorSchemeProperty(
    val schemes: List<ColorScheme>
) : IRProperty {
    override val propertyName = "color-scheme"

    enum class ColorScheme {
        NORMAL, LIGHT, DARK, ONLY
    }
}
