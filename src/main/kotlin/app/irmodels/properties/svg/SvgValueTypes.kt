package app.irmodels.properties.svg

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SvgLengthValue {
    @Serializable @SerialName("length") data class Length(val value: IRLength) : SvgLengthValue
    @Serializable @SerialName("percentage") data class Percentage(val value: IRPercentage) : SvgLengthValue
    @Serializable @SerialName("raw") data class Raw(val value: String) : SvgLengthValue
}
