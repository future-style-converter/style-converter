package app.irmodels.properties.paging

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BreakAfterProperty(val value: BreakValue) : IRProperty {
    override val propertyName = "break-after"
}
