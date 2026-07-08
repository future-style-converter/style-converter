package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `widows` property.
 *
 * ## CSS Property
 * **Syntax**: `widows: <integer>`
 *
 * ## Description
 * Sets the minimum number of lines in a block container that must be shown
 * at the top of a page, region, or column after a page break.
 *
 * @property count The minimum number of lines
 * @see [MDN widows](https://developer.mozilla.org/en-US/docs/Web/CSS/widows)
 */
@Serializable
data class WidowsProperty(
    val count: IRNumber
) : IRProperty {
    override val propertyName = "widows"
}
