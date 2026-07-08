package app.irmodels.properties.spacing

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MaxWidthProperty(
    val maxWidth: MaxValue
) : IRProperty {
    override val propertyName = "max-width"

    @Serializable
    sealed interface MaxValue {
        @Serializable
        @SerialName("none")
        data class None(val unit: Unit = Unit) : MaxValue

        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : MaxValue

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : MaxValue

        @Serializable
        @SerialName("min-content")
        data class MinContent(val unit: Unit = Unit) : MaxValue

        @Serializable
        @SerialName("max-content")
        data class MaxContent(val unit: Unit = Unit) : MaxValue

        @Serializable
        @SerialName("fit-content")
        data class FitContent(val maxSize: IRLength?) : MaxValue
    }
}
