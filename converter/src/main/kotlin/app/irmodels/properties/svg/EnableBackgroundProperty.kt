package app.irmodels.properties.svg

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface EnableBackgroundValue {
    @Serializable
    @SerialName("accumulate")
    data object Accumulate : EnableBackgroundValue

    @Serializable
    @SerialName("new")
    data class New(
        val x: IRNumber? = null,
        val y: IRNumber? = null,
        val width: IRNumber? = null,
        val height: IRNumber? = null
    ) : EnableBackgroundValue
}

/**
 * Represents the CSS `enable-background` property.
 * Controls creation of filter effects buffer.
 */
@Serializable
data class EnableBackgroundProperty(
    val value: EnableBackgroundValue
) : IRProperty {
    override val propertyName = "enable-background"
}
