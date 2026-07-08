package app.irmodels.properties.navigation

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavDownValue {
    @Serializable
    @SerialName("auto")
    data object Auto : NavDownValue

    @Serializable
    @SerialName("id")
    data class Id(val elementId: String) : NavDownValue
}

/**
 * Represents the CSS `nav-down` property.
 * Specifies where to navigate when using arrow keys (down direction).
 * Useful for TV apps and accessibility.
 */
@Serializable
data class NavDownProperty(
    val value: NavDownValue
) : IRProperty {
    override val propertyName = "nav-down"
}
