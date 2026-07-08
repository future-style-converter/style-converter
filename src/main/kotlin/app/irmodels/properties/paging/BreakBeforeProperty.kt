package app.irmodels.properties.paging

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class BreakBeforeProperty(val value: BreakValue) : IRProperty {
    override val propertyName = "break-before"
}
