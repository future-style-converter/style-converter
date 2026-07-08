package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

@Serializable
data class TextOverflowProperty(
    val value: TextOverflowValue
) : IRProperty {
    override val propertyName = "text-overflow"

    constructor(overflow: TextOverflow) : this(TextOverflowValue.Single(overflow))

    @Serializable
    sealed interface TextOverflowValue {
        @Serializable data class Single(val value: TextOverflow) : TextOverflowValue
        @Serializable data class TwoValue(val start: TextOverflow, val end: TextOverflow) : TextOverflowValue
        @Serializable data class CustomString(val value: String) : TextOverflowValue
        @Serializable data class Fade(val length: String? = null) : TextOverflowValue
        @Serializable data class Keyword(val keyword: String) : TextOverflowValue
    }

    @Serializable
    enum class TextOverflow {
        CLIP, ELLIPSIS
    }

    val overflow: TextOverflow
        get() = when (value) {
            is TextOverflowValue.Single -> value.value
            is TextOverflowValue.TwoValue -> value.end
            else -> TextOverflow.CLIP
        }
}
