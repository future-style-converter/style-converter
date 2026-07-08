package app.irmodels.properties.regions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class ContinueProperty(val value: ContinueValue) : IRProperty {
    override val propertyName = "continue"
}
