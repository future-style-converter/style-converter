package app.irmodels.properties.transforms

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `scale` property.
 *
 * ## CSS Property
 * **Syntax**: `scale: none | <number>{1,3}`
 *
 * ## Description
 * Allows you to specify scaling transforms individually (instead of using transform property).
 * Part of the individual transform properties.
 *
 * @property scale The scale value
 * @see [MDN scale](https://developer.mozilla.org/en-US/docs/Web/CSS/scale)
 */
@Serializable
data class ScaleProperty(
    val scale: Scale
) : IRProperty {
    override val propertyName = "scale"

    @Serializable
    sealed interface Scale {
        @Serializable
        @SerialName("none")
        data class None(val unit: Unit = Unit) : Scale

        @Serializable
        @SerialName("uniform")
        data class Uniform(val value: IRNumber) : Scale

        @Serializable
        @SerialName("2d")
        data class TwoAxis(val x: IRNumber, val y: IRNumber) : Scale

        @Serializable
        @SerialName("3d")
        data class ThreeAxis(val x: IRNumber, val y: IRNumber, val z: IRNumber) : Scale

        /** Global CSS keywords: inherit, initial, unset, revert, revert-layer */
        @Serializable
        @SerialName("keyword")
        data class Keyword(val value: String) : Scale

        /** Raw/unparsed value (e.g., var(), calc(), env() expressions) */
        @Serializable
        @SerialName("raw")
        data class Raw(val value: String) : Scale
    }
}
