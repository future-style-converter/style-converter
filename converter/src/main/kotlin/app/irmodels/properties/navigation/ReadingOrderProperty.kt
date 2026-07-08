package app.irmodels.properties.navigation

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class ReadingOrderValue {
    NORMAL, SOURCE_ORDER
}

/**
 * Represents the CSS `reading-order` property.
 * Controls the reading order for accessibility.
 */
@Serializable
data class ReadingOrderProperty(
    val value: ReadingOrderValue
) : IRProperty {
    override val propertyName = "reading-order"
}
