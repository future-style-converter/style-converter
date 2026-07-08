package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class TextSpaceCollapseValue {
    COLLAPSE,
    PRESERVE,
    PRESERVE_BREAKS,
    PRESERVE_SPACES,
    BREAK_SPACES
}

/**
 * Represents the CSS `text-space-collapse` property.
 * Controls white space collapsing.
 */
@Serializable
data class TextSpaceCollapseProperty(
    val value: TextSpaceCollapseValue
) : IRProperty {
    override val propertyName = "text-space-collapse"
}
