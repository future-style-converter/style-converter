package app.irmodels.properties.speech

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class VoiceStressProperty(val value: String) : IRProperty {
    override val propertyName = "voice-stress"
}
