package app.irmodels.properties.typography

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface BaselineShiftValue {
    @Serializable
    @SerialName("baseline")
    data object Baseline : BaselineShiftValue

    @Serializable
    @SerialName("sub")
    data object Sub : BaselineShiftValue

    @Serializable
    @SerialName("super")
    data object Super : BaselineShiftValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : BaselineShiftValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : BaselineShiftValue
}

/**
 * Represents the CSS `baseline-shift` property.
 * Shifts the baseline for superscript and subscript.
 */
@Serializable
data class BaselineShiftProperty(
    val value: BaselineShiftValue
) : IRProperty {
    override val propertyName = "baseline-shift"
}
