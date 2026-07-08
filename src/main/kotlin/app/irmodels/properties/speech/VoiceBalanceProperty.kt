package app.irmodels.properties.speech

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class VoiceBalanceProperty(val value: IRNumber) : IRProperty {
    override val propertyName = "voice-balance"
}
