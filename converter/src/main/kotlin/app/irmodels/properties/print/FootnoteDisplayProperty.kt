package app.irmodels.properties.print

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FootnoteDisplayValue {
    BLOCK,
    INLINE,
    COMPACT
}

/**
 * Represents the CSS `footnote-display` property.
 * Specifies how footnotes are displayed.
 */
@Serializable
data class FootnoteDisplayProperty(
    val value: FootnoteDisplayValue
) : IRProperty {
    override val propertyName = "footnote-display"
}
