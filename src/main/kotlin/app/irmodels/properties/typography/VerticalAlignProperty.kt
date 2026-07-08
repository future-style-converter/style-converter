package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerticalAlignProperty(
    val alignment: VerticalAlignment
) : IRProperty {
    override val propertyName = "vertical-align"

    @Serializable
    sealed interface VerticalAlignment {
        @Serializable
        @SerialName("keyword")
        data class Keyword(val value: AlignKeyword) : VerticalAlignment

        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : VerticalAlignment

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : VerticalAlignment

        enum class AlignKeyword {
            BASELINE, SUB, SUPER, TEXT_TOP, TEXT_BOTTOM,
            MIDDLE, TOP, BOTTOM
        }
    }
}
