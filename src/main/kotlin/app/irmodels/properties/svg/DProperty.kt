package app.irmodels.properties.svg

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class DProperty(val path: String) : IRProperty {
    override val propertyName = "d"
}
