package app.irmodels.properties.typography

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface DominantBaselineAdjustValue {
    @Serializable
    @SerialName("auto")
    data object Auto : DominantBaselineAdjustValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : DominantBaselineAdjustValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : DominantBaselineAdjustValue
}

/**
 * Represents the CSS `dominant-baseline-adjust` property.
 * Adjusts the dominant baseline.
 */
@Serializable
data class DominantBaselineAdjustProperty(
    val value: DominantBaselineAdjustValue
) : IRProperty {
    override val propertyName = "dominant-baseline-adjust"
}
