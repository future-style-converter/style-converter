package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FontFeatureSettingsProperty(
    val settings: FeatureSettings
) : IRProperty {
    override val propertyName = "font-feature-settings"

    @Serializable
    sealed interface FeatureSettings {
        @Serializable
        @SerialName("normal")
        data class Normal(val unit: kotlin.Unit = kotlin.Unit) : FeatureSettings

        @Serializable
        @SerialName("features")
        data class Features(val features: List<Feature>) : FeatureSettings

        @Serializable
        @SerialName("keyword")
        data class Keyword(val keyword: String) : FeatureSettings

        @Serializable
        @SerialName("raw")
        data class Raw(val value: String) : FeatureSettings
    }

    @Serializable
    data class Feature(
        val tag: String,
        val value: Int? = null
    )
}
