package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TextIndentProperty(
    val indent: TextIndent
) : IRProperty {
    override val propertyName = "text-indent"

    @Serializable
    sealed interface TextIndent {
        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : TextIndent

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : TextIndent
    }
}
