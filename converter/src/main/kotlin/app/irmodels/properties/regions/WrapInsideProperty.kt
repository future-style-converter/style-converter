package app.irmodels.properties.regions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class WrapInsideProperty(val value: WrapBreakValue) : IRProperty {
    override val propertyName = "wrap-inside"
}
