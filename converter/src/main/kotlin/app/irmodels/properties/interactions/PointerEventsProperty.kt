package app.irmodels.properties.interactions

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PointerEventsValue {
    @Serializable @SerialName("auto") data object Auto : PointerEventsValue
    @Serializable @SerialName("none") data object None : PointerEventsValue
    @Serializable @SerialName("visible-painted") data object VisiblePainted : PointerEventsValue
    @Serializable @SerialName("visible-fill") data object VisibleFill : PointerEventsValue
    @Serializable @SerialName("visible-stroke") data object VisibleStroke : PointerEventsValue
    @Serializable @SerialName("visible") data object Visible : PointerEventsValue
    @Serializable @SerialName("painted") data object Painted : PointerEventsValue
    @Serializable @SerialName("fill") data object Fill : PointerEventsValue
    @Serializable @SerialName("stroke") data object Stroke : PointerEventsValue
    @Serializable @SerialName("all") data object All : PointerEventsValue
    @Serializable @SerialName("bounding-box") data object BoundingBox : PointerEventsValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : PointerEventsValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : PointerEventsValue
}

@Serializable
data class PointerEventsProperty(
    val value: PointerEventsValue
) : IRProperty {
    override val propertyName = "pointer-events"
}
