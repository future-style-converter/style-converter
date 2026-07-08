package app.irmodels.properties.shapes

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ShapePaddingValue {
    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : ShapePaddingValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : ShapePaddingValue
}

/**
 * Represents the CSS `shape-padding` property.
 * Sets padding for shape-inside (experimental).
 */
@Serializable
data class ShapePaddingProperty(
    val value: ShapePaddingValue
) : IRProperty {
    override val propertyName = "shape-padding"
}
