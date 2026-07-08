package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface HyphenateCharacterValue {
    @Serializable
    @SerialName("auto")
    data object Auto : HyphenateCharacterValue

    @Serializable
    @SerialName("string")
    data class String(val value: kotlin.String) : HyphenateCharacterValue
}

/**
 * Represents the CSS `hyphenate-character` property.
 * Sets the character used at line breaks when hyphenating.
 */
@Serializable
data class HyphenateCharacterProperty(
    val value: HyphenateCharacterValue
) : IRProperty {
    override val propertyName = "hyphenate-character"
}
