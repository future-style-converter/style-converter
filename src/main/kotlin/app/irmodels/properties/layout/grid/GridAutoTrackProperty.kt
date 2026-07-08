package app.irmodels.properties.layout.grid

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface GridTrackSize {
    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : GridTrackSize

    @Serializable
    @SerialName("auto")
    data object Auto : GridTrackSize

    @Serializable
    @SerialName("minmax")
    data class MinMax(val min: GridTrackSize, val max: GridTrackSize) : GridTrackSize

    @Serializable
    @SerialName("fit-content")
    data class FitContent(val value: IRLength) : GridTrackSize
}

/**
 * Represents the CSS `grid-auto-track` property.
 * Experimental property for automatically sizing grid tracks.
 */
@Serializable
data class GridAutoTrackProperty(
    val trackSize: GridTrackSize
) : IRProperty {
    override val propertyName = "grid-auto-track"
}
