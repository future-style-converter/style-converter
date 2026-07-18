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

    // The `url` function name is an ASCII case-insensitive keyword per
    // CSS Syntax Level 3 §4.3.4 ("URL(...)" is valid CSS), so the regex
    // carries IGNORE_CASE. That option only relaxes MATCHING — the
    // captured payload (group 2) is returned as the author's original
    // bytes, which matters because url() payloads ARE case-sensitive
    // (base64 data URIs, case-sensitive server paths).
    private val urlRegex = """^url\(\s*(['"]?)(.+?)\1\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Parse a CSS url() value.
     *
     * Callers MUST pass the author's original (non-lowercased) bytes so
     * the extracted payload round-trips byte-exact into the IR.
     *
     * @param value The url string (e.g., "url(image.png)", "URL('image.png')")
     * @return IRUrl instance, or null if parsing fails
     */
    fun parse(value: String): IRUrl? {
        // Leading/trailing whitespace is insignificant around a component value.
        val trimmed = value.trim()

        // Match against url pattern (case-insensitive function name, see above).
        val match = urlRegex.find(trimmed) ?: return null

        // Group 2 is the raw payload between the (optional) quotes — untouched.
        val urlContent = match.groupValues[2]
        // URI schemes are case-insensitive per RFC 3986 §3.1, so detect
        // data: URIs without assuming lowercase input ("DATA:image/png…").
        val isDataUrl = urlContent.startsWith("data:", ignoreCase = true)

        return IRUrl(urlContent, isDataUrl)
    }
}
