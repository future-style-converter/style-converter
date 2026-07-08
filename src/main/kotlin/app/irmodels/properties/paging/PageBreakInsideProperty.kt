package app.irmodels.properties.paging

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class PageBreakInsideProperty(val value: PageBreakInsideValue) : IRProperty {
    override val propertyName = "page-break-inside"
}
