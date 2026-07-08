package app.irmodels.properties.shapes

import app.irmodels.IRProperty
import app.irmodels.IRUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ShapeOutsideValue {
    @Serializable
    @SerialName("none")
    data object None : ShapeOutsideValue

    @Serializable
    @SerialName("image-url")
    data class ImageUrl(val url: IRUrl) : ShapeOutsideValue

    @Serializable
    @SerialName("basic-shape")
    data class BasicShape(val shape: String) : ShapeOutsideValue  // circle(), ellipse(), polygon(), path()

    @Serializable
    @SerialName("margin-box")
    data object MarginBox : ShapeOutsideValue

    @Serializable
    @SerialName("border-box")
    data object BorderBox : ShapeOutsideValue

    @Serializable
    @SerialName("padding-box")
    data object PaddingBox : ShapeOutsideValue

    @Serializable
    @SerialName("content-box")
    data object ContentBox : ShapeOutsideValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : ShapeOutsideValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : ShapeOutsideValue
}

/**
 * Represents the CSS `shape-outside` property.
 * Defines a shape for text to wrap around.
 */
@Serializable
data class ShapeOutsideProperty(
    val value: ShapeOutsideValue
) : IRProperty {
    override val propertyName = "shape-outside"
}
