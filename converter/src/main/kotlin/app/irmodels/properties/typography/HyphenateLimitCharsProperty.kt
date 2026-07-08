package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface HyphenateLimitCharsValue {
    @Serializable
    @SerialName("auto")
    data object Auto : HyphenateLimitCharsValue

    @Serializable
    @SerialName("values")
    data class Values(
        val wordMin: Int,
        val charsBefore: Int? = null,
        val charsAfter: Int? = null
    ) : HyphenateLimitCharsValue

    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : HyphenateLimitCharsValue

    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : HyphenateLimitCharsValue
}

/**
 * Represents the CSS `hyphenate-limit-chars` property.
 * Sets minimum word length and character counts for hyphenation.
 */
@Serializable
data class HyphenateLimitCharsProperty(
    val value: HyphenateLimitCharsValue
) : IRProperty {
    override val propertyName = "hyphenate-limit-chars"
}
