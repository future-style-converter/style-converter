package app.irmodels.properties.animations

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `view-transition-group` property.
 * Specifies the view transition group container.
 */
@Serializable
data class ViewTransitionGroupProperty(
    val value: ViewTransitionGroupValue
) : IRProperty {
    override val propertyName = "view-transition-group"

    @Serializable
    sealed interface ViewTransitionGroupValue {
        @Serializable @SerialName("normal") data object Normal : ViewTransitionGroupValue
        @Serializable @SerialName("nearest") data object Nearest : ViewTransitionGroupValue
        @Serializable @SerialName("contain") data object Contain : ViewTransitionGroupValue
        @Serializable @SerialName("root") data object Root : ViewTransitionGroupValue
        @Serializable @SerialName("raw") data class Raw(val value: String) : ViewTransitionGroupValue
    }
}
