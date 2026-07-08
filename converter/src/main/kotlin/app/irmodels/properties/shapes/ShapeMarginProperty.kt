package app.irmodels.properties.shapes

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ShapeMarginValue {
    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : ShapeMarginValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : ShapeMarginValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : ShapeMarginValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : ShapeMarginValue
}

/**
 * Represents the CSS `shape-margin` property.
 * Sets margin for shape-outside.
 */
@Serializable
data class ShapeMarginProperty(
    val value: ShapeMarginValue
) : IRProperty {
    override val propertyName = "shape-margin"
}
