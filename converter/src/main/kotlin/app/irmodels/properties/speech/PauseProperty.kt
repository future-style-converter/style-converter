package app.irmodels.properties.speech

import app.irmodels.IRProperty
import app.irmodels.IRTime
import kotlinx.serialization.Serializable

@Serializable
data class PauseProperty(val before: IRTime?, val after: IRTime?) : IRProperty {
    override val propertyName = "pause"
}
