package app.irmodels.properties.regions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class CopyIntoProperty(val value: String) : IRProperty {
    override val propertyName = "copy-into"
}
