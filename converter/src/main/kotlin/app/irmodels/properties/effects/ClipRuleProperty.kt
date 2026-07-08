package app.irmodels.properties.effects

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ClipRuleValue {
    NONZERO,
    EVENODD
}

/**
 * Represents the CSS `clip-rule` property.
 * Determines the fill rule for SVG clipping paths.
 */
@Serializable
data class ClipRuleProperty(
    val value: ClipRuleValue
) : IRProperty {
    override val propertyName = "clip-rule"
}
