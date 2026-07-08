package app.irmodels.properties.regions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class WrapAfterProperty(val value: WrapBreakValue) : IRProperty {
    override val propertyName = "wrap-after"
}
