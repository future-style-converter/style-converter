package app.irmodels.properties.navigation

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavRightValue {
    @Serializable
    @SerialName("auto")
    data object Auto : NavRightValue

    @Serializable
    @SerialName("id")
    data class Id(val elementId: String) : NavRightValue
}

/**
 * Represents the CSS `nav-right` property.
 * Specifies where to navigate when using arrow keys (right direction).
 * Useful for TV apps and accessibility.
 */
@Serializable
data class NavRightProperty(
    val value: NavRightValue
) : IRProperty {
    override val propertyName = "nav-right"
}
