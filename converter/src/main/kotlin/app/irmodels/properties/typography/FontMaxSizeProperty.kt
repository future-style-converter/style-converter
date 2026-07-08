package app.irmodels.properties.typography

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FontMaxSizeValue {
    @Serializable
    @SerialName("none")
    data object None : FontMaxSizeValue

    @Serializable
    @SerialName("infinity")
    data object Infinity : FontMaxSizeValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : FontMaxSizeValue
}

/**
 * Represents the CSS `font-max-size` property.
 * Sets the maximum font size.
 */
@Serializable
data class FontMaxSizeProperty(
    val value: FontMaxSizeValue
) : IRProperty {
    override val propertyName = "font-max-size"
}
