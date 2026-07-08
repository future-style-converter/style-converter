package app.irmodels.properties.typography

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MaxLinesValue {
    @Serializable
    @SerialName("none")
    data object None : MaxLinesValue

    @Serializable
    @SerialName("count")
    data class Count(val value: IRNumber) : MaxLinesValue
}

/**
 * Represents the CSS `max-lines` property.
 * Limits the number of lines in a block container.
 */
@Serializable
data class MaxLinesProperty(
    val value: MaxLinesValue
) : IRProperty {
    override val propertyName = "max-lines"
}
