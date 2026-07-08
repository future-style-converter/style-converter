package app.irmodels.properties.speech

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class RichnessProperty(val value: IRNumber) : IRProperty {
    override val propertyName = "richness"
}
