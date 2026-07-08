package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ViewTransitionNameValue {
    @Serializable
    @SerialName("none")
    data object None : ViewTransitionNameValue

    @Serializable
    @SerialName("named")
    data class Named(val name: String) : ViewTransitionNameValue
}

/**
 * Represents the CSS `view-transition-name` property.
 * Names an element for view transitions.
 */
@Serializable
data class ViewTransitionNameProperty(
    val value: ViewTransitionNameValue
) : IRProperty {
    override val propertyName = "view-transition-name"
}
