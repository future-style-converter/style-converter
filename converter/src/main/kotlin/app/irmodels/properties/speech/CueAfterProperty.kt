package app.irmodels.properties.speech

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class CueAfterProperty(val value: CueValue) : IRProperty {
    override val propertyName = "cue-after"
}
