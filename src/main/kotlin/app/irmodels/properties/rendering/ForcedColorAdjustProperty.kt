package app.irmodels.properties.rendering

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ForcedColorAdjustValue {
    @Serializable @SerialName("auto") data object Auto : ForcedColorAdjustValue
    @Serializable @SerialName("none") data object None : ForcedColorAdjustValue
    @Serializable @SerialName("preserve-parent-color") data object PreserveParentColor : ForcedColorAdjustValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : ForcedColorAdjustValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : ForcedColorAdjustValue
}

/**
 * Represents the CSS `forced-color-adjust` property.
 * Controls forced color mode adjustments.
 */
@Serializable
data class ForcedColorAdjustProperty(
    val value: ForcedColorAdjustValue
) : IRProperty {
    override val propertyName = "forced-color-adjust"
}
