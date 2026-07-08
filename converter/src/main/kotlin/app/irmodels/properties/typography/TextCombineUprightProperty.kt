package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface TextCombineUprightValue {
    @Serializable
    @SerialName("none")
    data object None : TextCombineUprightValue

    @Serializable
    @SerialName("all")
    data object All : TextCombineUprightValue

    @Serializable
    @SerialName("digits")
    data class Digits(val count: Int) : TextCombineUprightValue  // 2-4 digits
}

/**
 * Represents the CSS `text-combine-upright` property.
 * Controls combination of characters into single glyph (CJK vertical text).
 */
@Serializable
data class TextCombineUprightProperty(
    val value: TextCombineUprightValue
) : IRProperty {
    override val propertyName = "text-combine-upright"
}
