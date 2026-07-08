package app.irmodels.properties.paging

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `margin-break` property.
 * Controls how margins behave at page/column/region breaks.
 */
@Serializable
data class MarginBreakProperty(
    val value: MarginBreakValue
) : IRProperty {
    override val propertyName = "margin-break"
}
