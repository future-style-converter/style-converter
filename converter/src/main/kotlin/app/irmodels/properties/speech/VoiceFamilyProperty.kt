package app.irmodels.properties.speech

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class VoiceFamilyProperty(val values: List<String>) : IRProperty {
    override val propertyName = "voice-family"
}
