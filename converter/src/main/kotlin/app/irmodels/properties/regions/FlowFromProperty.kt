package app.irmodels.properties.regions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class FlowFromProperty(val value: FlowValue) : IRProperty {
    override val propertyName = "flow-from"
}
