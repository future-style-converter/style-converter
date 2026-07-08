package app.irmodels.properties.speech

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class VoiceVolumeProperty(val value: VolumeValue) : IRProperty {
    override val propertyName = "voice-volume"
}
