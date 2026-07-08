package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `text-spacing` shorthand property.
 * Controls spacing around CJK punctuation and other text spacing behaviors.
 */
@Serializable
data class TextSpacingProperty(
    val value: TextSpacingValue
) : IRProperty {
    override val propertyName = "text-spacing"

    @Serializable
    sealed interface TextSpacingValue {
        @Serializable @SerialName("normal") data object Normal : TextSpacingValue
        @Serializable @SerialName("none") data object None : TextSpacingValue
        @Serializable @SerialName("auto") data object Auto : TextSpacingValue
        @Serializable @SerialName("raw") data class Raw(val value: String) : TextSpacingValue
    }
}
