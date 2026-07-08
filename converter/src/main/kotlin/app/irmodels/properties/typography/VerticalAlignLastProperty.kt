package app.irmodels.properties.typography

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface VerticalAlignLastValue {
    @Serializable
    @SerialName("auto")
    data object Auto : VerticalAlignLastValue

    @Serializable
    @SerialName("baseline")
    data object Baseline : VerticalAlignLastValue

    @Serializable
    @SerialName("top")
    data object Top : VerticalAlignLastValue

    @Serializable
    @SerialName("middle")
    data object Middle : VerticalAlignLastValue

    @Serializable
    @SerialName("bottom")
    data object Bottom : VerticalAlignLastValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : VerticalAlignLastValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : VerticalAlignLastValue
}

/**
 * Represents the CSS `vertical-align-last` property.
 * Controls vertical alignment of the last line.
 */
@Serializable
data class VerticalAlignLastProperty(
    val value: VerticalAlignLastValue
) : IRProperty {
    override val propertyName = "vertical-align-last"
}
