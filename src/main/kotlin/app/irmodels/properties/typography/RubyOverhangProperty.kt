package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class RubyOverhangValue {
    AUTO, START, END, NONE
}

/**
 * Represents the CSS `ruby-overhang` property.
 * Controls how ruby annotation overhangs adjacent text.
 */
@Serializable
data class RubyOverhangProperty(
    val value: RubyOverhangValue
) : IRProperty {
    override val propertyName = "ruby-overhang"
}
