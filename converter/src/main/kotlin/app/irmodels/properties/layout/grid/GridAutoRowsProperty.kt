package app.irmodels.properties.layout.grid

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class GridAutoRowsProperty(
    val sizes: List<TrackSize>
) : IRProperty {
    override val propertyName = "grid-auto-rows"
}
