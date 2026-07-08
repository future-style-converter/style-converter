package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class WhiteSpaceCollapseValue {
    COLLAPSE, PRESERVE, PRESERVE_BREAKS, PRESERVE_SPACES, BREAK_SPACES
}

/**
 * Represents the CSS `white-space-collapse` property.
 * Controls how whitespace is collapsed.
 */
@Serializable
data class WhiteSpaceCollapseProperty(
    val value: WhiteSpaceCollapseValue
) : IRProperty {
    override val propertyName = "white-space-collapse"
}
