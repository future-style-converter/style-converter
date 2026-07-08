package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MinWidthProperty(
    val minWidth: MinMaxValue
) : IRProperty {
    override val propertyName = "min-width"

    @Serializable
    sealed interface MinMaxValue {
        @Serializable
        @SerialName("auto")
        data class Auto(val unit: Unit = Unit) : MinMaxValue

        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : MinMaxValue

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : MinMaxValue

        @Serializable
        @SerialName("min-content")
        data class MinContent(val unit: Unit = Unit) : MinMaxValue

        @Serializable
        @SerialName("max-content")
        data class MaxContent(val unit: Unit = Unit) : MinMaxValue

        @Serializable
        @SerialName("fit-content")
        data class FitContent(val maxSize: IRLength?) : MinMaxValue
    }
}
