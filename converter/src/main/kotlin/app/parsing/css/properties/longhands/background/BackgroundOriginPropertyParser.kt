package app.parsing.css.properties.longhands.background

import app.irmodels.IRProperty
import app.irmodels.properties.background.BackgroundOriginProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object BackgroundOriginPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Check for global keywords first
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return BackgroundOriginProperty(listOf(BackgroundOriginProperty.Origin.GlobalKeyword(trimmed)))
        }

        val origins = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            when (part) {
                "border-box" -> BackgroundOriginProperty.Origin.BORDER_BOX
                "padding-box" -> BackgroundOriginProperty.Origin.PADDING_BOX
                "content-box" -> BackgroundOriginProperty.Origin.CONTENT_BOX
                else -> return null
            }
        }
        if (origins.isEmpty()) return null
        return BackgroundOriginProperty(origins)
    }
}
