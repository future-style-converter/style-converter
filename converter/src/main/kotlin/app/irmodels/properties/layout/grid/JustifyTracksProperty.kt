package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface JustifyTracksValue {
    @Serializable @SerialName("normal") data object Normal : JustifyTracksValue
    @Serializable @SerialName("start") data object Start : JustifyTracksValue
    @Serializable @SerialName("end") data object End : JustifyTracksValue
    @Serializable @SerialName("center") data object Center : JustifyTracksValue
    @Serializable @SerialName("stretch") data object Stretch : JustifyTracksValue
    @Serializable @SerialName("space-between") data object SpaceBetween : JustifyTracksValue
    @Serializable @SerialName("space-around") data object SpaceAround : JustifyTracksValue
    @Serializable @SerialName("space-evenly") data object SpaceEvenly : JustifyTracksValue
    @Serializable @SerialName("multi") data class Multi(val values: List<String>) : JustifyTracksValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : JustifyTracksValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : JustifyTracksValue
}

/**
 * Represents the CSS `justify-tracks` property.
 * Controls alignment of grid tracks in the inline direction.
 */
@Serializable
data class JustifyTracksProperty(
    val value: JustifyTracksValue
) : IRProperty {
    override val propertyName = "justify-tracks"
}
