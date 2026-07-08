package app.irmodels.properties.svg

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class StopOpacityProperty(
    val opacity: IRNumber  // 0.0 to 1.0
) : IRProperty {
    override val propertyName = "stop-opacity"
}
