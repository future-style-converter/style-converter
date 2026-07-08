package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class RubyAlignValue {
    START,
    CENTER,
    SPACE_BETWEEN,
    SPACE_AROUND
}

/**
 * Represents the CSS `ruby-align` property.
 * Controls alignment of ruby text relative to base text.
 */
@Serializable
data class RubyAlignProperty(
    val value: RubyAlignValue
) : IRProperty {
    override val propertyName = "ruby-align"
}
