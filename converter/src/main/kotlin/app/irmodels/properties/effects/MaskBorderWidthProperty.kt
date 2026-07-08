package app.irmodels.properties.effects

import app.irmodels.IRLength
import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MaskBorderWidthValue {
    @Serializable @SerialName("length") data class Length(val value: IRLength) : MaskBorderWidthValue
    @Serializable @SerialName("number") data class Number(val value: Double) : MaskBorderWidthValue
    @Serializable @SerialName("auto") data object Auto : MaskBorderWidthValue
    @Serializable @SerialName("multi") data class Multi(val top: String, val right: String, val bottom: String, val left: String) : MaskBorderWidthValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : MaskBorderWidthValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : MaskBorderWidthValue
}

@Serializable
data class MaskBorderWidthProperty(
    val value: MaskBorderWidthValue
) : IRProperty {
    override val propertyName = "mask-border-width"
}
