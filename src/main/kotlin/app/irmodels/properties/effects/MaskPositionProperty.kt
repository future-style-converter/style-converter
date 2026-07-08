package app.irmodels.properties.effects

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MaskPositionValue {
    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : MaskPositionValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : MaskPositionValue

    @Serializable
    @SerialName("center")
    data object Center : MaskPositionValue

    @Serializable
    @SerialName("top")
    data object Top : MaskPositionValue

    @Serializable
    @SerialName("bottom")
    data object Bottom : MaskPositionValue

    @Serializable
    @SerialName("left")
    data object Left : MaskPositionValue

    @Serializable
    @SerialName("right")
    data object Right : MaskPositionValue
}

@Serializable
data class MaskPosition(
    val x: MaskPositionValue,
    val y: MaskPositionValue
)

@Serializable
data class MaskPositionProperty(
    val positions: List<MaskPosition>
) : IRProperty {
    override val propertyName = "mask-position"

    constructor(x: MaskPositionValue, y: MaskPositionValue) : this(listOf(MaskPosition(x, y)))
}
