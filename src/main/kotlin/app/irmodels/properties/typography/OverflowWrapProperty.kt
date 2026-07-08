package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `overflow-wrap` property.
 *
 * ## CSS Property
 * **Syntax**: `overflow-wrap: normal | break-word | anywhere`
 *
 * ## Description
 * Specifies whether or not the browser may break lines within an otherwise unbreakable string
 * to prevent text from overflowing its line box.
 *
 * Note: This is the same as `word-wrap` (legacy name).
 *
 * @property wrap The overflow-wrap value
 * @see [MDN overflow-wrap](https://developer.mozilla.org/en-US/docs/Web/CSS/overflow-wrap)
 */
@Serializable
data class OverflowWrapProperty(
    val wrap: OverflowWrap
) : IRProperty {
    override val propertyName = "overflow-wrap"

    enum class OverflowWrap {
        NORMAL,
        BREAK_WORD,
        ANYWHERE
    }
}
