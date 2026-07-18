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
        // CASE-PRESERVATION CONTRACT (mirrors BackgroundImagePropertyParser):
        // match keywords/function names against a lowered COPY (they are
        // ASCII case-insensitive, CSS Syntax L3 §4.3) but extract the url()
        // payload from the ORIGINAL bytes — payloads are case-sensitive
        // (base64 data URIs, case-sensitive paths). The old code lowercased
        // the whole value before UrlParser saw it, corrupting those bytes.
        // Schema note: v2 is permissive at property-data leaves
        // (schema/spec/05-versioning.md), so this is a bug fix, not a
        // wire-shape change.
        val trimmed = value.trim()
        // Lowered copy used ONLY for keyword / function-name matching.
        val lowered = trimmed.lowercase()

        // Handle 'none' keyword (case-insensitive per spec).
        if (lowered == "none") {
            return BorderImageSourceProperty(BorderImageSource.None)
        }

        // Handle url() values — UrlParser matches "url(" case-insensitively
        // and returns the payload from the original bytes untouched.
        if (lowered.startsWith("url(")) {
            val url = UrlParser.parse(trimmed) ?: return null
            return BorderImageSourceProperty(BorderImageSource.Url(url.url))
        }

        // Handle gradient functions — detected on the lowered copy, stored
        // as the ORIGINAL bytes (Gradient carries the raw CSS text).
        if (isGradientFunction(lowered)) {
            return BorderImageSourceProperty(BorderImageSource.Gradient(trimmed))
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
