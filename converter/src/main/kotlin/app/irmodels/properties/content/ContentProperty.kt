package app.irmodels.properties.content

import app.irmodels.IRProperty
import app.irmodels.IRUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `content` property.
 *
 * Used with ::before and ::after pseudo-elements to insert generated content.
 *
 * Syntax: normal | none | <string> | <url> | <counter> | attr() | open-quote | close-quote
 */
@Serializable
data class ContentProperty(val value: ContentValue) : IRProperty {
    override val propertyName: String = "content"
}

@Serializable
sealed interface ContentValue {
    @Serializable
    @SerialName("normal")
    data object Normal : ContentValue

    @Serializable
    @SerialName("none")
    data object None : ContentValue

    @Serializable
    @SerialName("string")
    data class StringValue(val text: String) : ContentValue

    @Serializable
    @SerialName("url")
    data class UrlValue(val url: IRUrl) : ContentValue

    @Serializable
    @SerialName("url-with-alt")
    data class UrlWithAlt(val url: IRUrl, val altText: String) : ContentValue

    @Serializable
    @SerialName("counter")
    data class Counter(val name: String, val style: String? = null) : ContentValue

    @Serializable
    @SerialName("counters")
    data class Counters(val name: String, val separator: String, val style: String? = null) : ContentValue

    @Serializable
    @SerialName("attr")
    data class Attr(val attributeName: String) : ContentValue

    @Serializable
    @SerialName("open-quote")
    data object OpenQuote : ContentValue

    @Serializable
    @SerialName("close-quote")
    data object CloseQuote : ContentValue

    @Serializable
    @SerialName("no-open-quote")
    data object NoOpenQuote : ContentValue

    @Serializable
    @SerialName("no-close-quote")
    data object NoCloseQuote : ContentValue

    @Serializable
    @SerialName("multiple")
    data class Multiple(val values: List<ContentValue>) : ContentValue

    /**
     * Generic function value for advanced content functions:
     * - leader(dotted), leader(solid), etc.
     * - target-counter(url, name, style?)
     * - target-counters(url, name, separator, style?)
     * - target-text(url, keyword?)
     * - linear-gradient(...), radial-gradient(...), conic-gradient(...)
     * - image-set(...)
     * - cross-fade(...)
     * - element(id)
     */
    @Serializable
    @SerialName("function")
    data class Function(val name: String, val arguments: String) : ContentValue

    /**
     * Image value for gradients or image functions in content
     */
    @Serializable
    @SerialName("image")
    data class Image(val raw: String) : ContentValue

    /**
     * Raw value for complex expressions we can't fully parse (var(), etc.)
     */
    @Serializable
    @SerialName("raw")
    data class Raw(val value: String) : ContentValue

    /**
     * Global CSS keywords
     */
    @Serializable
    @SerialName("keyword")
    data class Keyword(val keyword: String) : ContentValue
}
