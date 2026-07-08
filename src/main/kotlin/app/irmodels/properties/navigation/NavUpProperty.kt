package app.irmodels.properties.navigation

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavUpValue {
    @Serializable
    @SerialName("auto")
    data object Auto : NavUpValue

    @Serializable
    @SerialName("id")
    data class Id(val elementId: String) : NavUpValue
}

/**
 * Represents the CSS `nav-up` property.
 * Specifies where to navigate when using arrow keys (up direction).
 * Useful for TV apps and accessibility.
 */
@Serializable
data class NavUpProperty(
    val value: NavUpValue
) : IRProperty {
    override val propertyName = "nav-up"
}
