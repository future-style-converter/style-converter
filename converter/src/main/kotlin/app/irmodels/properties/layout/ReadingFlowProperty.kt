package app.irmodels.properties.layout

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ReadingFlowValue {
    NORMAL,
    FLEX_VISUAL,
    FLEX_FLOW,
    GRID_ROWS,
    GRID_COLUMNS,
    GRID_ORDER
}

/**
 * Represents the CSS `reading-flow` property.
 * Specifies reading order for accessibility.
 */
@Serializable
data class ReadingFlowProperty(
    val value: ReadingFlowValue
) : IRProperty {
    override val propertyName = "reading-flow"
}
