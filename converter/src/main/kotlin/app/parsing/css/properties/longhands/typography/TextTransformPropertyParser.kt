package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextTransformProperty
import app.parsing.css.properties.longhands.PropertyParser

object TextTransformPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val transform = when (trimmed) {
            "none" -> TextTransformProperty.TextTransform.NONE
            "uppercase" -> TextTransformProperty.TextTransform.UPPERCASE
            "lowercase" -> TextTransformProperty.TextTransform.LOWERCASE
            "capitalize" -> TextTransformProperty.TextTransform.CAPITALIZE
            "full-width" -> TextTransformProperty.TextTransform.FULL_WIDTH
            "full-size-kana" -> TextTransformProperty.TextTransform.FULL_SIZE_KANA
            else -> return null
        }
        return TextTransformProperty(transform)
    }
}
