package app.irmodels.properties.effects

import app.irmodels.IRLength
import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MaskBorderOutsetValue {
    @Serializable @SerialName("length") data class Length(val value: IRLength) : MaskBorderOutsetValue
    @Serializable @SerialName("number") data class Number(val value: Double) : MaskBorderOutsetValue
    @Serializable @SerialName("multi") data class Multi(val top: String, val right: String, val bottom: String, val left: String) : MaskBorderOutsetValue
    @Serializable @SerialName("keyword") data class Keyword(val keyword: String) : MaskBorderOutsetValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : MaskBorderOutsetValue
}

@Serializable
data class MaskBorderOutsetProperty(
    val value: MaskBorderOutsetValue
) : IRProperty {
    override val propertyName = "mask-border-outset"
}
