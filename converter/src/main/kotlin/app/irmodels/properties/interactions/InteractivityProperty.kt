package app.irmodels.properties.interactions

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class InteractivityValue {
    AUTO, INERT
}

/**
 * Represents the CSS `interactivity` property.
 * Controls whether an element can receive pointer events and focus.
 */
@Serializable
data class InteractivityProperty(
    val value: InteractivityValue
) : IRProperty {
    override val propertyName = "interactivity"
}
