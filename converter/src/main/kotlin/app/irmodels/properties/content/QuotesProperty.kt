package app.irmodels.properties.content

import app.irmodels.IRProperty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `quotes` property.
 *
 * ## CSS Property
 * **Syntax**: `quotes: none | auto | <string> <string>+`
 *
 * ## Description
 * Sets quotation marks to be used for quotes. Defines pairs of opening and closing quotes.
 *
 * @property quotesValue The quotes value
 * @see [MDN quotes](https://developer.mozilla.org/en-US/docs/Web/CSS/quotes)
 */
@Serializable
data class QuotesProperty(
    val quotesValue: Quotes
) : IRProperty {
    override val propertyName = "quotes"

    @Serializable
    sealed interface Quotes {
        @Serializable
        @SerialName("none")
        data class None(val unit: Unit = Unit) : Quotes

        @Serializable
        @SerialName("auto")
        data class Auto(val unit: Unit = Unit) : Quotes

        @Serializable
        @SerialName("pairs")
        data class QuotePairs(val pairs: List<QuotePair>) : Quotes

        @Serializable
        @SerialName("keyword")
        data class Keyword(val keyword: String) : Quotes

        @Serializable
        @SerialName("raw")
        data class Raw(val value: String) : Quotes
    }

    @Serializable
    data class QuotePair(
        val open: String,
        val close: String
    )
}
