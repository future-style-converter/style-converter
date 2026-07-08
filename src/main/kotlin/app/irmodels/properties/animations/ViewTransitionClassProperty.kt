package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ViewTransitionClassValue {
    @Serializable
    @SerialName("none")
    data object None : ViewTransitionClassValue

    @Serializable
    @SerialName("classes")
    data class Classes(val names: List<String>) : ViewTransitionClassValue
}

/**
 * Represents the CSS `view-transition-class` property.
 * Assigns class names for view transitions.
 */
@Serializable
data class ViewTransitionClassProperty(
    val value: ViewTransitionClassValue
) : IRProperty {
    override val propertyName = "view-transition-class"
}
