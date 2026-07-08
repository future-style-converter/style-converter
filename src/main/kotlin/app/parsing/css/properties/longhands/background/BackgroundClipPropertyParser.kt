package app.parsing.css.properties.longhands.background

import app.irmodels.IRProperty
import app.irmodels.properties.background.BackgroundClipProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object BackgroundClipPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Check for global keywords first
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return BackgroundClipProperty(global = trimmed)
        }

        val clips = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            when (part) {
                "border-box" -> BackgroundClipProperty.Clip.BORDER_BOX
                "padding-box" -> BackgroundClipProperty.Clip.PADDING_BOX
                "content-box" -> BackgroundClipProperty.Clip.CONTENT_BOX
                "text" -> BackgroundClipProperty.Clip.TEXT
                else -> return null
            }
        }
        if (clips.isEmpty()) return null
        return BackgroundClipProperty(clips = clips)
    }
}
