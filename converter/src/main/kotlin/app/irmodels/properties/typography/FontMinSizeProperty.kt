package app.irmodels.properties.typography

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface FontMinSizeValue {
    @Serializable
    @SerialName("none")
    data object None : FontMinSizeValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : FontMinSizeValue
}

/**
 * Represents the CSS `font-min-size` property.
 * Sets the minimum font size.
 */
@Serializable
data class FontMinSizeProperty(
    val value: FontMinSizeValue
) : IRProperty {
    override val propertyName = "font-min-size"
}
