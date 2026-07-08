package app.irmodels.properties.speech

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/** CSS `volume` property (deprecated CSS Speech Module). */
@Serializable
data class VolumeProperty(val value: VolumeValue) : IRProperty {
    override val propertyName = "volume"
}
