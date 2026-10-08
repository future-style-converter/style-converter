package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.HyphenateCharacterProperty
import app.irmodels.properties.typography.HyphenateCharacterValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.CssStringParser

/**
 * Parser for `hyphenate-character` property.
 *
 * Values: auto | <string> (css-text-4 §6.3).
 *
 * Wave 54 (lane L3, U1): a quoted value is now decoded as ONE css-syntax-3
 * `<string>` token ([CssStringParser]) — escapes such as `"\2022"` become the
 * code point (WPT css-text/hyphens/hyphenate-character-003 asks for U+2022),
 * and the EMPTY string `""` is kept as `String("")` (css-text-4 §6.3 admits
 * it; hyphenate-character-001/-002 assert "no visible hyphens appear"). Before
 * wave 54 `""` returned null, fell back to `Generic _unmapped`, and every
 * runtime painted the UA hyphen instead.
 *
 * Unquoted input (and a quoted value that is not exactly one well-formed
 * string token) keeps the pre-wave-54 acceptance byte-for-byte: strip one
 * surrounding quote pair if present and emit the rest, null when empty.
 */
object HyphenateCharacterPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        // Keywords are ASCII case-insensitive (css-values-4 §2.1).
        val trimmed = value.trim().lowercase()

        // The `auto` keyword: the UA picks the hyphen (css-text-4 §6.3).
        if (trimmed == "auto") {
            // Runtimes map Auto to their UA hyphen (U+2010 on both natives).
            return HyphenateCharacterProperty(HyphenateCharacterValue.Auto)
        }

        // css-syntax-3 §4.3.5/§4.3.7: a value that IS one string token is
        // decoded, the empty string included (css-text-4 §6.3 `<string>`).
        CssStringParser.parse(value)?.let { decoded ->
            // Emitted verbatim inside the existing `string` variant (no new byte shape).
            return HyphenateCharacterProperty(HyphenateCharacterValue.String(decoded))
        }

        // Legacy acceptance for everything else (unquoted text, malformed
        // quoting): strip quotes if present, exactly as before wave 54.
        val stringValue = value.trim().removeSurrounding("\"").removeSurrounding("'")

        // An empty legacy remainder carries no value at all.
        if (stringValue.isEmpty()) return null

        // Unchanged pre-wave-54 emission for non-token input.
        return HyphenateCharacterProperty(HyphenateCharacterValue.String(stringValue))
    }
}
