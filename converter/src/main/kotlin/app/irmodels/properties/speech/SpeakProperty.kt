package app.irmodels.properties.speech

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/** CSS `speak` property (deprecated CSS Speech Module). */
@Serializable
data class SpeakProperty(val value: SpeakValue) : IRProperty {
    override val propertyName = "speak"
}
