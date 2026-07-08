package app.irmodels.properties.paging

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class PageBreakAfterProperty(val value: PageBreakValue) : IRProperty {
    override val propertyName = "page-break-after"
}
