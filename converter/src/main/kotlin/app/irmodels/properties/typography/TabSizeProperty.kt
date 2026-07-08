package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TabSizeProperty(
    val size: TabSize
) : IRProperty {
    override val propertyName = "tab-size"

    @Serializable
    sealed interface TabSize {
        @Serializable
        @SerialName("number")
        data class Number(val value: IRNumber) : TabSize

        @Serializable
        @SerialName("length")
        data class LengthValue(val length: IRLength) : TabSize
    }
}
