package app.irmodels.properties.typography

import app.irmodels.IRProperty
import kotlinx.serialization.Serializable

enum class FontVariantEmojiValue {
    NORMAL,
    TEXT,
    EMOJI,
    UNICODE
}

/**
 * Represents the CSS `font-variant-emoji` property.
 * Controls emoji presentation style.
 */
@Serializable
data class FontVariantEmojiProperty(
    val value: FontVariantEmojiValue
) : IRProperty {
    override val propertyName = "font-variant-emoji"
}
