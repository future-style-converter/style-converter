package app.irmodels.properties.speech

import app.irmodels.IRProperty
import app.irmodels.IRTime
import kotlinx.serialization.Serializable

@Serializable
data class VoiceDurationProperty(val value: IRTime) : IRProperty {
    override val propertyName = "voice-duration"
}
