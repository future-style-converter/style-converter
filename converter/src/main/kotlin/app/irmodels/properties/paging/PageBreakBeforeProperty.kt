package app.irmodels.properties.paging

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class PageBreakBeforeProperty(val value: PageBreakValue) : IRProperty {
    override val propertyName = "page-break-before"
}
