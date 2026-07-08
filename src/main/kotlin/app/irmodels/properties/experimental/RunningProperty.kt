package app.irmodels.properties.experimental

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class RunningProperty(val value: RunningValue) : IRProperty {
    override val propertyName = "running"
}
