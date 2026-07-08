package app.irmodels.properties.performance

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WillChangeProperty(
    val properties: List<WillChangeValue>
) : IRProperty {
    override val propertyName = "will-change"

    @Serializable
    sealed interface WillChangeValue {
        @Serializable
        @SerialName("auto")
        data class Auto(val unit: kotlin.Unit = kotlin.Unit) : WillChangeValue

        @Serializable
        @SerialName("scroll-position")
        data class ScrollPosition(val unit: kotlin.Unit = kotlin.Unit) : WillChangeValue

        @Serializable
        @SerialName("contents")
        data class Contents(val unit: kotlin.Unit = kotlin.Unit) : WillChangeValue

        @Serializable
        @SerialName("property-name")
        data class PropertyName(val name: String) : WillChangeValue
    }
}
