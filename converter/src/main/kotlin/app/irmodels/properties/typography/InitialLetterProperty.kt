package app.irmodels.properties.typography

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface InitialLetterValue {
    @Serializable
    @SerialName("normal")
    data object Normal : InitialLetterValue

    @Serializable
    @SerialName("size")
    data class Size(
        val size: IRNumber,
        val sink: IRNumber? = null
    ) : InitialLetterValue
}

/**
 * Represents the CSS `initial-letter` property.
 * Specifies size of drop cap or raised cap.
 */
@Serializable
data class InitialLetterProperty(
    val value: InitialLetterValue
) : IRProperty {
    override val propertyName = "initial-letter"
}
