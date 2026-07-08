package app.parsing.css.properties.longhands.background

import app.irmodels.IRProperty
import app.irmodels.properties.background.BackgroundAttachmentProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object BackgroundAttachmentPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Check for global keywords first
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return BackgroundAttachmentProperty(listOf(BackgroundAttachmentProperty.Attachment.GlobalKeyword(trimmed)))
        }

        val attachments = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            when (part) {
                "scroll" -> BackgroundAttachmentProperty.Attachment.SCROLL
                "fixed" -> BackgroundAttachmentProperty.Attachment.FIXED
                "local" -> BackgroundAttachmentProperty.Attachment.LOCAL
                else -> return null
            }
        }
        if (attachments.isEmpty()) return null
        return BackgroundAttachmentProperty(attachments)
    }
}
