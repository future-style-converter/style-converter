package app.irmodels.properties.transforms

import app.irmodels.IRLength
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `perspective-origin` property.
 *
 * Sets the origin point for the perspective property (the vanishing point).
 *
 * Syntax: <position> (x y coordinates)
 */
@Serializable
data class PerspectiveOriginProperty(val x: PerspectiveOriginValue, val y: PerspectiveOriginValue) : IRProperty {
    override val propertyName: String = "perspective-origin"
}

@Serializable
sealed interface PerspectiveOriginValue {
    @Serializable
    @SerialName("length")
    data class Length(val value: IRLength) : PerspectiveOriginValue

    @Serializable
    @SerialName("percentage")
    data class Percentage(val value: IRPercentage) : PerspectiveOriginValue

    @Serializable
    @SerialName("left")
    data object Left : PerspectiveOriginValue

    @Serializable
    @SerialName("center")
    data object Center : PerspectiveOriginValue

    @Serializable
    @SerialName("right")
    data object Right : PerspectiveOriginValue

    @Serializable
    @SerialName("top")
    data object Top : PerspectiveOriginValue

    @Serializable
    @SerialName("bottom")
    data object Bottom : PerspectiveOriginValue
}
