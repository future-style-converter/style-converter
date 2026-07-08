package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FontVariationSettingsProperty(
    val settings: VariationSettings
) : IRProperty {
    override val propertyName = "font-variation-settings"

    @Serializable
    sealed interface VariationSettings {
        @Serializable
        @SerialName("normal")
        data class Normal(val unit: kotlin.Unit = kotlin.Unit) : VariationSettings

        @Serializable
        @SerialName("variations")
        data class Variations(val variations: List<Variation>) : VariationSettings
    }

    @Serializable
    data class Variation(
        val axis: String,
        val value: Double
    )
}
