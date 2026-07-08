package app.irmodels.properties.layout.advanced

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class InsetAreaKeyword {
    @SerialName("none") NONE,
    @SerialName("top") TOP,
    @SerialName("bottom") BOTTOM,
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
    @SerialName("start") START,
    @SerialName("end") END,
    @SerialName("center") CENTER,
    @SerialName("span-all") SPAN_ALL,
    @SerialName("span-top") SPAN_TOP,
    @SerialName("span-bottom") SPAN_BOTTOM,
    @SerialName("span-left") SPAN_LEFT,
    @SerialName("span-right") SPAN_RIGHT,
    @SerialName("span-start") SPAN_START,
    @SerialName("span-end") SPAN_END,
    @SerialName("span-x") SPAN_X,
    @SerialName("span-y") SPAN_Y,
    @SerialName("span-block-start") SPAN_BLOCK_START,
    @SerialName("span-block-end") SPAN_BLOCK_END,
    @SerialName("span-inline-start") SPAN_INLINE_START,
    @SerialName("span-inline-end") SPAN_INLINE_END,
    @SerialName("all") ALL,
    @SerialName("block-start") BLOCK_START,
    @SerialName("block-end") BLOCK_END,
    @SerialName("inline-start") INLINE_START,
    @SerialName("inline-end") INLINE_END,
    @SerialName("self-start") SELF_START,
    @SerialName("self-end") SELF_END,
    @SerialName("self-block-start") SELF_BLOCK_START,
    @SerialName("self-block-end") SELF_BLOCK_END,
    @SerialName("self-inline-start") SELF_INLINE_START,
    @SerialName("self-inline-end") SELF_INLINE_END,
    @SerialName("x-start") X_START,
    @SerialName("x-end") X_END,
    @SerialName("y-start") Y_START,
    @SerialName("y-end") Y_END
}

// Keep for backwards compatibility
enum class InsetAreaValue {
    NONE, TOP, BOTTOM, LEFT, RIGHT, START, END, CENTER,
    SPAN_ALL, SPAN_TOP, SPAN_BOTTOM, SPAN_LEFT, SPAN_RIGHT,
    SPAN_START, SPAN_END, SPAN_X, SPAN_Y, ALL
}

@Serializable
sealed interface InsetAreaSpec {
    @Serializable
    @SerialName("single")
    data class Single(val value: InsetAreaKeyword) : InsetAreaSpec

    @Serializable
    @SerialName("combined")
    data class Combined(val first: InsetAreaKeyword, val second: InsetAreaKeyword) : InsetAreaSpec

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : InsetAreaSpec

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : InsetAreaSpec
}

/**
 * Represents the CSS `inset-area` property.
 * Specifies the alignment area for anchor positioning.
 */
@Serializable
data class InsetAreaProperty(
    val spec: InsetAreaSpec
) : IRProperty {
    override val propertyName = "inset-area"

    // Backwards compatible constructor
    constructor(value: InsetAreaValue) : this(
        InsetAreaSpec.Single(value.toKeyword())
    )
}

private fun InsetAreaValue.toKeyword(): InsetAreaKeyword = when (this) {
    InsetAreaValue.NONE -> InsetAreaKeyword.NONE
    InsetAreaValue.TOP -> InsetAreaKeyword.TOP
    InsetAreaValue.BOTTOM -> InsetAreaKeyword.BOTTOM
    InsetAreaValue.LEFT -> InsetAreaKeyword.LEFT
    InsetAreaValue.RIGHT -> InsetAreaKeyword.RIGHT
    InsetAreaValue.START -> InsetAreaKeyword.START
    InsetAreaValue.END -> InsetAreaKeyword.END
    InsetAreaValue.CENTER -> InsetAreaKeyword.CENTER
    InsetAreaValue.SPAN_ALL -> InsetAreaKeyword.SPAN_ALL
    InsetAreaValue.SPAN_TOP -> InsetAreaKeyword.SPAN_TOP
    InsetAreaValue.SPAN_BOTTOM -> InsetAreaKeyword.SPAN_BOTTOM
    InsetAreaValue.SPAN_LEFT -> InsetAreaKeyword.SPAN_LEFT
    InsetAreaValue.SPAN_RIGHT -> InsetAreaKeyword.SPAN_RIGHT
    InsetAreaValue.SPAN_START -> InsetAreaKeyword.SPAN_START
    InsetAreaValue.SPAN_END -> InsetAreaKeyword.SPAN_END
    InsetAreaValue.SPAN_X -> InsetAreaKeyword.SPAN_X
    InsetAreaValue.SPAN_Y -> InsetAreaKeyword.SPAN_Y
    InsetAreaValue.ALL -> InsetAreaKeyword.ALL
}
