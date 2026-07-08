package app.irmodels.properties.svg

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class FloodOpacityProperty(val opacity: IRNumber) : IRProperty {
    override val propertyName = "flood-opacity"
}
