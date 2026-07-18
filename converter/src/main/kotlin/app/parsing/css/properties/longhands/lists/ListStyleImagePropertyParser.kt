package app.parsing.css.properties.longhands.lists

import app.irmodels.IRProperty
import app.irmodels.properties.lists.ListStyleImageProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.UrlParser

object ListStyleImagePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        // CASE-PRESERVATION CONTRACT (mirrors BackgroundImagePropertyParser):
        // keywords/function names are ASCII case-insensitive (CSS Syntax L3
        // §4.3) so they match against a lowered COPY, but the url() payload
        // is case-sensitive author bytes (base64 data URIs, case-sensitive
        // paths) and is extracted from the ORIGINAL value. The old code
        // lowercased the whole value first, corrupting those bytes. The v2
        // schema is permissive at property-data leaves
        // (schema/spec/05-versioning.md): bug fix, not a wire-shape change.
        val trimmed = value.trim()
        // Lowered copy used ONLY for keyword / function-name matching.
        val lowered = trimmed.lowercase()

        val image: ListStyleImageProperty.ListImage = when {
            // 'none' keyword, case-insensitive per spec.
            lowered == "none" -> ListStyleImageProperty.ListImage.None()
            // url(): delegate to the shared UrlParser — it matches "url("
            // case-insensitively and returns the raw payload (also fixing
            // the old ad-hoc removePrefix that missed quoted whitespace).
            lowered.startsWith("url(") -> {
                // A malformed url() (no closing paren, bad quotes) yields
                // null → whole declaration rejected, same as before.
                val url = UrlParser.parse(trimmed) ?: return null
                ListStyleImageProperty.ListImage.Url(url.url)
            }
            else -> return null
        }

        return ListStyleImageProperty(image)
    }
}
