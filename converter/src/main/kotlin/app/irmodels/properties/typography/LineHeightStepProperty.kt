package app.irmodels.properties.typography

import app.irmodels.IRLength
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface LineHeightStepValue {
    @Serializable
    @SerialName("none")
    data object None : LineHeightStepValue

    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : LineHeightStepValue
}

/**
 * Represents the CSS `line-height-step` property.
 * Sets step unit for line box heights.
 */
@Serializable
data class LineHeightStepProperty(
    val value: LineHeightStepValue
) : IRProperty {
    override val propertyName = "line-height-step"
}
