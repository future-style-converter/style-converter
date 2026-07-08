package app.irmodels.properties.speech

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class CueProperty(val before: CueValue?, val after: CueValue?) : IRProperty {
    override val propertyName = "cue"
}
