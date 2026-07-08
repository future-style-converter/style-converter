package app.irmodels.properties.svg

import app.irmodels.IRUrl
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class MarkerEndProperty(
    val marker: IRUrl  // url(#marker-id)
) : IRProperty {
    override val propertyName = "marker-end"
}
