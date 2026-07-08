package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontVariantEmojiProperty
import app.irmodels.properties.typography.FontVariantEmojiValue
import app.parsing.css.properties.longhands.PropertyParser

object FontVariantEmojiPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val emojiValue = when (trimmed) {
            "normal" -> FontVariantEmojiValue.NORMAL
            "text" -> FontVariantEmojiValue.TEXT
            "emoji" -> FontVariantEmojiValue.EMOJI
            "unicode" -> FontVariantEmojiValue.UNICODE
            else -> return null
        }

        return FontVariantEmojiProperty(emojiValue)
    }
}
