package app.irmodels.properties.typography

import app.irmodels.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `line-clamp` property.
 *
 * ## CSS Property
 * **Syntax**: `line-clamp: none | <integer [1,∞]> | auto`
 *
 * ## Description
 * Limits the contents of a block to the specified number of lines.
 * Often used with `-webkit-line-clamp` for text truncation.
 * `auto` clamps at the element's own block-size constraint (max-height /
 * height) instead of at a fixed count — the count is a USED value, so it is
 * left for the runtimes to resolve (`null`-means-runtime-dependent, the same
 * contract lengths use).
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

        /**
         * `line-clamp: auto` (css-overflow-4 §5.1). Carries no count: the
         * clamp point is wherever the element's block-size constraint falls,
         * which only a runtime that knows the used line-height can compute.
         * Serializes as `{"type":"auto"}` — an additive variant on a leaf the
         * v2 schema already treats permissively, and one that every existing
         * decoder already falls through to "no clamp" on (Compose
         * `extractLineClamp`, Swift `LineClampExtractor`).
         */
        @Serializable
        @SerialName("auto")
        data class Auto(val unit: Unit = Unit) : LineClamp
    }
}
