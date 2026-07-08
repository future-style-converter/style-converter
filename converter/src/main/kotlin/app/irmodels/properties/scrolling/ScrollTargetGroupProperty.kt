package app.irmodels.properties.scrolling

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ScrollTargetGroupValue {
    @Serializable
    @SerialName("none")
    data object None : ScrollTargetGroupValue

    @Serializable
    @SerialName("named")
    data class Named(val name: String) : ScrollTargetGroupValue
}

/**
 * Represents the CSS `scroll-target-group` property.
 * Associates an element with a scroll target group.
 */
@Serializable
data class ScrollTargetGroupProperty(
    val value: ScrollTargetGroupValue
) : IRProperty {
    override val propertyName = "scroll-target-group"
}
