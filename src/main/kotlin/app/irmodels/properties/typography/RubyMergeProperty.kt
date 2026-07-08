package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class RubyMergeValue {
    SEPARATE,
    COLLAPSE,
    AUTO
}

/**
 * Represents the CSS `ruby-merge` property.
 * Controls merging of ruby annotations.
 */
@Serializable
data class RubyMergeProperty(
    val value: RubyMergeValue
) : IRProperty {
    override val propertyName = "ruby-merge"
}
