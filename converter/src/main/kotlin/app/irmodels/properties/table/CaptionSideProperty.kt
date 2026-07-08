package app.irmodels.properties.table

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CaptionSideValue {
    @Serializable @SerialName("top") data object Top : CaptionSideValue
    @Serializable @SerialName("bottom") data object Bottom : CaptionSideValue
    @Serializable @SerialName("block-start") data object BlockStart : CaptionSideValue
    @Serializable @SerialName("block-end") data object BlockEnd : CaptionSideValue
    @Serializable @SerialName("inline-start") data object InlineStart : CaptionSideValue
    @Serializable @SerialName("inline-end") data object InlineEnd : CaptionSideValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : CaptionSideValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : CaptionSideValue
}

@Serializable
data class CaptionSideProperty(
    val value: CaptionSideValue
) : IRProperty {
    override val propertyName = "caption-side"
}
