package app.irmodels.properties.typography

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface TextSizeAdjustValue {
    @Serializable
    @SerialName("none")
    data object None : TextSizeAdjustValue

    @Serializable
    @SerialName("auto")
    data object Auto : TextSizeAdjustValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : TextSizeAdjustValue
}

/**
 * Represents the CSS `text-size-adjust` property.
 * Controls text inflation on mobile devices.
 */
@Serializable
data class TextSizeAdjustProperty(
    val value: TextSizeAdjustValue
) : IRProperty {
    override val propertyName = "text-size-adjust"
}
