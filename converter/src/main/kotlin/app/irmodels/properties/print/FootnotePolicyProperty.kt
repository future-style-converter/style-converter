package app.irmodels.properties.print

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FootnotePolicyValue {
    AUTO,
    LINE,
    BLOCK
}

/**
 * Represents the CSS `footnote-policy` property.
 * Specifies footnote placement policy.
 */
@Serializable
data class FootnotePolicyProperty(
    val value: FootnotePolicyValue
) : IRProperty {
    override val propertyName = "footnote-policy"
}
