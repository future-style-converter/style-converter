package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `line-clamp` property.
 *
 * ## CSS Property
 * **Syntax**: `line-clamp: none | <integer>`
 *
 * ## Description
 * Limits the contents of a block to the specified number of lines.
 * Often used with `-webkit-line-clamp` for text truncation.
 *
 * @property clamp The line-clamp value
 * @see [MDN line-clamp](https://developer.mozilla.org/en-US/docs/Web/CSS/line-clamp)
 */
@Serializable
data class LineClampProperty(
    val clamp: LineClamp
) : IRProperty {
    override val propertyName = "line-clamp"

    @Serializable
    sealed interface LineClamp {
        @Serializable
        @SerialName("none")
        data class None(val unit: Unit = Unit) : LineClamp

        @Serializable
        @SerialName("lines")
        data class Lines(val count: IRNumber) : LineClamp
    }
}
