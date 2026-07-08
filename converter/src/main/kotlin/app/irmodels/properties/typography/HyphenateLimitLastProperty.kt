package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class HyphenateLimitLastValue {
    NONE,
    LAST,
    COLUMN,
    PAGE,
    SPREAD
}

/**
 * Represents the CSS `hyphenate-limit-last` property.
 * Controls hyphenation at end of element, column, or page.
 */
@Serializable
data class HyphenateLimitLastProperty(
    val value: HyphenateLimitLastValue
) : IRProperty {
    override val propertyName = "hyphenate-limit-last"
}
