package app.irmodels.properties.speech

import app.irmodels.IRProperty
import app.irmodels.IRTime
import kotlinx.serialization.Serializable

@Serializable
data class PauseBeforeProperty(val value: IRTime) : IRProperty {
    override val propertyName = "pause-before"
}
