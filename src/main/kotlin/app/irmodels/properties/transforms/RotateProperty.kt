package app.irmodels.properties.transforms

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `rotate` property.
 *
 * ## CSS Property
 * **Syntax**: `rotate: none | <angle> | <axis> <angle>`
 *
 * ## Description
 * Allows you to specify rotation transforms individually (instead of using transform property).
 * Part of the individual transform properties.
 *
 * @property rotation The rotation value
 * @see [MDN rotate](https://developer.mozilla.org/en-US/docs/Web/CSS/rotate)
 */
@Serializable
data class RotateProperty(
    val rotation: Rotation
) : IRProperty {
    override val propertyName = "rotate"

    @Serializable
    sealed interface Rotation {
        @Serializable
        @SerialName("none")
        data class None(val unit: Unit = Unit) : Rotation

        @Serializable
        @SerialName("angle")
        data class Angle(val angle: IRAngle) : Rotation

        @Serializable
        @SerialName("axis-angle")
        data class AxisAngle(
            val x: Double,
            val y: Double,
            val z: Double,
            val angle: IRAngle
        ) : Rotation
    }
}
