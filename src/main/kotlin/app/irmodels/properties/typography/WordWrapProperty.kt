package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

/**
 * Represents the CSS `word-wrap` property.
 *
 * ## CSS Property
 * **Syntax**: `word-wrap: normal | break-word`
 *
 * ## Description
 * Legacy name for `overflow-wrap`. Specifies whether the browser may break lines within words.
 *
 * @property wrap The word-wrap value
 * @see [MDN word-wrap](https://developer.mozilla.org/en-US/docs/Web/CSS/word-wrap)
 */
@Serializable
data class WordWrapProperty(
    val wrap: WordWrap
) : IRProperty {
    override val propertyName = "word-wrap"

    enum class WordWrap {
        NORMAL,
        BREAK_WORD
    }
}
