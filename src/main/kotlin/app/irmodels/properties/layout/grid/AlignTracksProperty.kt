package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AlignTracksValue {
    @Serializable @SerialName("normal") data object Normal : AlignTracksValue
    @Serializable @SerialName("start") data object Start : AlignTracksValue
    @Serializable @SerialName("end") data object End : AlignTracksValue
    @Serializable @SerialName("center") data object Center : AlignTracksValue
    @Serializable @SerialName("stretch") data object Stretch : AlignTracksValue
    @Serializable @SerialName("space-between") data object SpaceBetween : AlignTracksValue
    @Serializable @SerialName("space-around") data object SpaceAround : AlignTracksValue
    @Serializable @SerialName("space-evenly") data object SpaceEvenly : AlignTracksValue
    @Serializable @SerialName("baseline") data object Baseline : AlignTracksValue
    @Serializable @SerialName("multi") data class Multi(val values: List<String>) : AlignTracksValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : AlignTracksValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : AlignTracksValue
}

/**
 * Represents the CSS `align-tracks` property.
 * Controls alignment of grid tracks in the block direction.
 */
@Serializable
data class AlignTracksProperty(
    val value: AlignTracksValue
) : IRProperty {
    override val propertyName = "align-tracks"
}
