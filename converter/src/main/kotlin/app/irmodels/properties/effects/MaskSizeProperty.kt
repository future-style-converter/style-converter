package app.irmodels.properties.effects

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MaskSizeValue {
    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : MaskSizeValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : MaskSizeValue

    @Serializable
    @SerialName("auto")
    data object Auto : MaskSizeValue

    @Serializable
    @SerialName("cover")
    data object Cover : MaskSizeValue

    @Serializable
    @SerialName("contain")
    data object Contain : MaskSizeValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : MaskSizeValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : MaskSizeValue
}

@Serializable
data class MaskSizeProperty(
    val width: MaskSizeValue,
    val height: MaskSizeValue
) : IRProperty {
    override val propertyName = "mask-size"
}
