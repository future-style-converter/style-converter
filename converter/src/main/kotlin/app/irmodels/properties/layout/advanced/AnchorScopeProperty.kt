package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class AnchorScopeValue {
    NONE,
    ALL
}

/**
 * Represents the CSS `anchor-scope` property.
 * Limits the scope of anchor name visibility.
 */
@Serializable
data class AnchorScopeProperty(
    val value: AnchorScopeValue
) : IRProperty {
    override val propertyName = "anchor-scope"
}
