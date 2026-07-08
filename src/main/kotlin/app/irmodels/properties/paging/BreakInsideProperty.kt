package app.irmodels.properties.paging

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BreakInsideProperty(val value: BreakInsideValue) : IRProperty {
    override val propertyName = "break-inside"
}
