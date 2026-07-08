package app.irmodels.properties.experimental

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class StringSetProperty(val name: String, val value: String) : IRProperty {
    override val propertyName = "string-set"
}
