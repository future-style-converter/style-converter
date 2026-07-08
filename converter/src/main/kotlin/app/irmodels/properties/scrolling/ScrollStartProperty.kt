package app.irmodels.properties.scrolling

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ScrollStartValue {
    @Serializable
    @SerialName("auto")
    data object Auto : ScrollStartValue

    @Serializable
    @SerialName("start")
    data object Start : ScrollStartValue

    @Serializable
    @SerialName("end")
    data object End : ScrollStartValue

    @Serializable
    @SerialName("center")
    data object Center : ScrollStartValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : ScrollStartValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : ScrollStartValue
}

/**
 * Represents the CSS `scroll-start` property.
 * Sets the initial scroll position of a scrollable element.
 */
@Serializable
data class ScrollStartProperty(
    val value: ScrollStartValue
) : IRProperty {
    override val propertyName = "scroll-start"
}
