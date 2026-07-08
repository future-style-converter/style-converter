package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PositionAreaValue {
    @Serializable @SerialName("none") data object None : PositionAreaValue
    @Serializable @SerialName("top") data object Top : PositionAreaValue
    @Serializable @SerialName("bottom") data object Bottom : PositionAreaValue
    @Serializable @SerialName("left") data object Left : PositionAreaValue
    @Serializable @SerialName("right") data object Right : PositionAreaValue
    @Serializable @SerialName("center") data object Center : PositionAreaValue
    @Serializable @SerialName("block-start") data object BlockStart : PositionAreaValue
    @Serializable @SerialName("block-end") data object BlockEnd : PositionAreaValue
    @Serializable @SerialName("inline-start") data object InlineStart : PositionAreaValue
    @Serializable @SerialName("inline-end") data object InlineEnd : PositionAreaValue
    @Serializable @SerialName("span-all") data object SpanAll : PositionAreaValue
    @Serializable @SerialName("span-left") data object SpanLeft : PositionAreaValue
    @Serializable @SerialName("span-right") data object SpanRight : PositionAreaValue
    @Serializable @SerialName("span-top") data object SpanTop : PositionAreaValue
    @Serializable @SerialName("span-bottom") data object SpanBottom : PositionAreaValue
    @Serializable @SerialName("span-block-start") data object SpanBlockStart : PositionAreaValue
    @Serializable @SerialName("span-block-end") data object SpanBlockEnd : PositionAreaValue
    @Serializable @SerialName("span-inline-start") data object SpanInlineStart : PositionAreaValue
    @Serializable @SerialName("span-inline-end") data object SpanInlineEnd : PositionAreaValue
    @Serializable @SerialName("self-block-start") data object SelfBlockStart : PositionAreaValue
    @Serializable @SerialName("self-block-end") data object SelfBlockEnd : PositionAreaValue
    @Serializable @SerialName("self-inline-start") data object SelfInlineStart : PositionAreaValue
    @Serializable @SerialName("self-inline-end") data object SelfInlineEnd : PositionAreaValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : PositionAreaValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : PositionAreaValue
}

/**
 * Represents the CSS `position-area` property.
 * Specifies the area relative to an anchor.
 */
@Serializable
data class PositionAreaProperty(
    val row: PositionAreaValue,
    val column: PositionAreaValue
) : IRProperty {
    override val propertyName = "position-area"
}
