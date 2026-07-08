package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.irmodels.properties.borders.BorderImageSource
import app.irmodels.properties.borders.BorderImageSourceProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.UrlParser

/**
 * Parses the CSS `border-image-source` property.
 *
 * Supports:
 * - none
 * - url(...)
 * - linear-gradient(...)
 * - radial-gradient(...)
 * - conic-gradient(...)
 * - repeating-linear-gradient(...)
 * - repeating-radial-gradient(...)
 * - repeating-conic-gradient(...)
 *
 * Examples:
 * - "none" → BorderImageSource.None
 * - "url(border.png)" → BorderImageSource.Url("border.png")
 * - "linear-gradient(to right, red, blue)" → BorderImageSource.Gradient("linear-gradient(to right, red, blue)")
 */
object BorderImageSourcePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle 'none' keyword
        if (trimmed == "none") {
            return BorderImageSourceProperty(BorderImageSource.None)
        }

        // Handle url() values
        if (trimmed.startsWith("url(")) {
            val url = UrlParser.parse(trimmed) ?: return null
            return BorderImageSourceProperty(BorderImageSource.Url(url.url))
        }

        // Handle gradient functions
        if (isGradientFunction(trimmed)) {
            return BorderImageSourceProperty(BorderImageSource.Gradient(value.trim()))
        }

        return null
    }

    /**
     * Check if the value is a gradient function.
     */
    private fun isGradientFunction(value: String): Boolean {
        return value.startsWith("linear-gradient(") ||
               value.startsWith("radial-gradient(") ||
               value.startsWith("conic-gradient(") ||
               value.startsWith("repeating-linear-gradient(") ||
               value.startsWith("repeating-radial-gradient(") ||
               value.startsWith("repeating-conic-gradient(")
    }
}
