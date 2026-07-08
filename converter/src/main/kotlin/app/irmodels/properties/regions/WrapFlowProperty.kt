package app.irmodels.properties.regions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class WrapFlowProperty(val value: WrapFlowValue) : IRProperty {
    override val propertyName = "wrap-flow"
}
