package app.irmodels.properties.speech

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class StressProperty(val value: IRNumber) : IRProperty {
    override val propertyName = "stress"
}
