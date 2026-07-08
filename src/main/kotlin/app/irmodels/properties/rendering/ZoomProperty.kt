package app.irmodels.properties.rendering

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ZoomValue {
    @Serializable
    @SerialName("normal")
    data object Normal : ZoomValue

    @Serializable
    @SerialName("reset")
    data object Reset : ZoomValue

    @Serializable
    @SerialName("number")
    data class Number(val value: IRNumber) : ZoomValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : ZoomValue
}

/**
 * Represents the CSS `zoom` property.
 * Controls the magnification level of an element.
 */
@Serializable
data class ZoomProperty(
    val value: ZoomValue
) : IRProperty {
    override val propertyName = "zoom"
}
