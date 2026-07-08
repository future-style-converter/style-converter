package app.irmodels.properties.navigation

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavLeftValue {
    @Serializable
    @SerialName("auto")
    data object Auto : NavLeftValue

    @Serializable
    @SerialName("id")
    data class Id(val elementId: String) : NavLeftValue
}

/**
 * Represents the CSS `nav-left` property.
 * Specifies where to navigate when using arrow keys (left direction).
 * Useful for TV apps and accessibility.
 */
@Serializable
data class NavLeftProperty(
    val value: NavLeftValue
) : IRProperty {
    override val propertyName = "nav-left"
}
