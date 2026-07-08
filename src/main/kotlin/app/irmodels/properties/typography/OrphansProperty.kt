package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `orphans` property.
 *
 * ## CSS Property
 * **Syntax**: `orphans: <integer>`
 *
 * ## Description
 * Sets the minimum number of lines in a block container that must be shown
 * at the bottom of a page, region, or column before a page break.
 *
 * @property count The minimum number of lines
 * @see [MDN orphans](https://developer.mozilla.org/en-US/docs/Web/CSS/orphans)
 */
@Serializable
data class OrphansProperty(
    val count: IRNumber
) : IRProperty {
    override val propertyName = "orphans"
}
