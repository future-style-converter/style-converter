package app.irmodels.properties.performance

import app.irmodels.IRLength
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ContainIntrinsicValue {
    @Serializable @SerialName("none") data object None : ContainIntrinsicValue
    @Serializable @SerialName("auto") data object Auto : ContainIntrinsicValue
    @Serializable @SerialName("length") data class Length(val value: IRLength) : ContainIntrinsicValue
    @Serializable @SerialName("auto-length") data class AutoLength(val length: IRLength) : ContainIntrinsicValue
}
