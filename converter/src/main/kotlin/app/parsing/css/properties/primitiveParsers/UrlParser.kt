package app.parsing.css.properties.primitiveParsers

import app.irmodels.IRUrl

/**
 * Parses CSS url() values into IRUrl instances.
 *
 * Supports:
 * - url(path) - Regular URLs
 * - url("path") - Quoted URLs
 * - url('path') - Single-quoted URLs
 * - data: URLs
 *
 * Examples:
 * - "url(image.png)" → IRUrl("image.png")
 * - "url('image.png')" → IRUrl("image.png")
 * - "url(\"image.png\")" → IRUrl("image.png")
 * - "url(data:image/png;base64,...)" → IRUrl("data:...", isDataUrl = true)
 */
object UrlParser {

    private val urlRegex = """^url\(\s*(['"]?)(.+?)\1\s*\)$""".toRegex()

    /**
     * Parse a CSS url() value.
     *
     * @param value The url string (e.g., "url(image.png)", "url('image.png')")
     * @return IRUrl instance, or null if parsing fails
     */
    fun parse(value: String): IRUrl? {
        val trimmed = value.trim()

        // Match against url pattern
        val match = urlRegex.find(trimmed) ?: return null

        val urlContent = match.groupValues[2]
        val isDataUrl = urlContent.startsWith("data:")

        return IRUrl(urlContent, isDataUrl)
    }
}
