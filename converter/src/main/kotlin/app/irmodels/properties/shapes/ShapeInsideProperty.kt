package app.irmodels.properties.shapes

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ShapeInsideValue {
    @Serializable
    @SerialName("auto")
    data object Auto : ShapeInsideValue

    @Serializable
    @SerialName("none")
    data object None : ShapeInsideValue

    @Serializable
    @SerialName("shape")
    data class Shape(val shape: String) : ShapeInsideValue
}

/**
 * Represents the CSS `shape-inside` property.
 * Defines the inner shape for text wrapping.
 */
@Serializable
data class ShapeInsideProperty(
    val value: ShapeInsideValue
) : IRProperty {
    override val propertyName = "shape-inside"
}
