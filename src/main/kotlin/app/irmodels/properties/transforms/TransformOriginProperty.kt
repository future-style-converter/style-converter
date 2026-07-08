package app.irmodels.properties.transforms

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface TransformOriginProperty : IRProperty {
    override val propertyName get() = "transform-origin"

    @Serializable
    @SerialName("values")
    data class Values(
        val x: OriginValue,
        val y: OriginValue,
        val z: IRLength? = null
    ) : TransformOriginProperty

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : TransformOriginProperty

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : TransformOriginProperty

    @Serializable
    sealed interface OriginValue {
        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : OriginValue

        @Serializable
        @SerialName("percentage")
        data class PercentageValue(val percentage: IRPercentage) : OriginValue

        @Serializable
        @SerialName("keyword")
        data class Keyword(val value: OriginKeyword) : OriginValue

        enum class OriginKeyword {
            LEFT, CENTER, RIGHT, TOP, BOTTOM
        }
    }
}
