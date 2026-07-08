package app.irmodels.properties.speech

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/** CSS `speak-as` property (deprecated CSS Speech Module). */
@Serializable
data class SpeakAsProperty(val values: List<String>) : IRProperty {
    override val propertyName = "speak-as"
}
