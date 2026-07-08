package app.irmodels.properties.regions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class WrapThroughProperty(val value: WrapThroughValue) : IRProperty {
    override val propertyName = "wrap-through"
}
