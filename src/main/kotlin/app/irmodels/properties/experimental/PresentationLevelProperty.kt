package app.irmodels.properties.experimental

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class PresentationLevelProperty(val value: PresentationLevelValue) : IRProperty {
    override val propertyName = "presentation-level"
}
