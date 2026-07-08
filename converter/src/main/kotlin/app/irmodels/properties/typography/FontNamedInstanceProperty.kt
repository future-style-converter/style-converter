package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FontNamedInstanceValue {
    @Serializable
    @SerialName("auto")
    data object Auto : FontNamedInstanceValue

    @Serializable
    @SerialName("named")
    data class Named(val name: String) : FontNamedInstanceValue
}

/**
 * Represents the CSS `font-named-instance` property.
 * Allows selecting a named instance from a variable font.
 */
@Serializable
data class FontNamedInstanceProperty(
    val value: FontNamedInstanceValue
) : IRProperty {
    override val propertyName = "font-named-instance"
}
