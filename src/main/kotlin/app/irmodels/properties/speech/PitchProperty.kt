package app.irmodels.properties.speech

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class PitchProperty(val value: String) : IRProperty {
    override val propertyName = "pitch"
}
