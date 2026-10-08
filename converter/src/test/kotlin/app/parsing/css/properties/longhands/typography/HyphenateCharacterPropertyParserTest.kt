package app.parsing.css.properties.longhands.typography

// Wave 54 (lane L3, unit U1) — `hyphenate-character` reaches the wire decoded.
//
// Root cause pinned here (tools/titan/results/wave54-plan/hyphenate-character-compose.md §4.A):
//   - `""` was rejected (`if (stringValue.isEmpty()) return null`) and fell back to
//     `Generic {_unmapped: true}`, so every runtime painted the UA hyphen where WPT
//     css-text/hyphens/hyphenate-character-001/-002 assert "no visible hyphens";
//   - quotes were stripped with no css-syntax-3 escape decoding, so `"\2022"` (-003)
//     and `"\00a0\0640"` (-005) reached the wire with a literal backslash and the web
//     runtime painted `im\2022`.
//
// Pinned invariants (css-text-4 §6.3; css-syntax-3 §4.3.5 / §4.3.7):
//   1. `""`            → String("")       (the empty string is a legal <string>);
//   2. `"\2022"`       → "•"              (hex escape decoded);
//   3. `"\00a0\0640"`  → U+00A0 U+0640    (two escapes back to back, -005 verbatim);
//   4. `"\2022 x"`     → "•x"             (ONE whitespace after a hex escape is eaten);
//   5. `'/-/'`         → "/-/"            (single quotes; -004's value is byte-identical);
//   6. `auto`          → Auto             (keyword, any case);
//   plus the §4.3.7 replacement rules, line continuation, and the legacy acceptance of
//   unquoted / malformed input, which must not move.
//
// MUTATIONS EXECUTED (tools/titan/results/wave54-hyphenate-character/_note.md):
//   U1-a drop the decoding (legacy strip only)   → pins 2, 3, 4 go red;
//   U1-b restore `isEmpty() → null` for decoded  → pin 1 and the wire/end-to-end pins go red.

import app.irmodels.IRProperty
import app.irmodels.IRPropertySerializer
import app.irmodels.properties.typography.HyphenateCharacterProperty
import app.irmodels.properties.typography.HyphenateCharacterValue
import app.parsing.css.CssPropertyValue
import app.parsing.css.properties.PropertiesParser
import app.parsing.css.properties.primitiveParsers.CssStringParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HyphenateCharacterPropertyParserTest {

    /** The decoded string payload, or null when the parser produced no String variant. */
    private fun stringOf(css: String): String? =
        // Run the real parser exactly as PropertyParserRegistry would.
        ((HyphenateCharacterPropertyParser.parse(css) as? HyphenateCharacterProperty)
            // Only the String variant carries a payload.
            ?.value as? HyphenateCharacterValue.String)?.value

    @Test
    fun `pin 1 the empty string is kept as String empty`() {
        // css-text-4 §6.3 admits "" — the -001/-002 declaration verbatim.
        assertEquals("", stringOf("\"\""))
        // Single-quoted empty string is the same token.
        assertEquals("", stringOf("''"))
    }

    @Test
    fun `pin 2 a hex escape is decoded`() {
        // -003 verbatim: U+2022 BULLET.
        assertEquals("\u2022", stringOf("\"\\2022\""))
    }

    @Test
    fun `pin 3 two escapes back to back decode in order`() {
        // -005 verbatim: U+00A0 NO-BREAK SPACE then U+0640 ARABIC TATWEEL.
        assertEquals("\u00A0\u0640", stringOf("\"\\00a0\\0640\""))
    }

    @Test
    fun `pin 4 one whitespace after a hex escape is consumed`() {
        // §4.3.7: "If the next input code point is whitespace, consume it as well."
        assertEquals("\u2022x", stringOf("\"\\2022 x\""))
        // Only ONE whitespace: the second space survives.
        assertEquals("\u2022 x", stringOf("\"\\2022  x\""))
    }

    @Test
    fun `pin 5 single-quoted and plain values pass through verbatim`() {
        // -004's value, single-quoted here, double-quoted on the corpus.
        assertEquals("/-/", stringOf("'/-/'"))
        // The corpus spelling of -004 and hyphenate-limit-chars-001.
        assertEquals("/-/", stringOf("\"/-/\""))
        assertEquals("-", stringOf("\"-\""))
    }

    @Test
    fun `pin 6 auto is the keyword in any case`() {
        // css-values-4 §2.1: keywords are ASCII case-insensitive.
        assertEquals(HyphenateCharacterValue.Auto, (HyphenateCharacterPropertyParser.parse("auto") as HyphenateCharacterProperty).value)
        assertEquals(HyphenateCharacterValue.Auto, (HyphenateCharacterPropertyParser.parse(" AUTO ") as HyphenateCharacterProperty).value)
        // A QUOTED "auto" is a string, not the keyword.
        assertEquals("auto", stringOf("\"auto\""))
    }

    @Test
    fun `escape replacement rules follow css-syntax-3 4_3_7`() {
        // Zero → U+FFFD.
        assertEquals("\uFFFD", CssStringParser.parse("\"\\0\""))
        // A surrogate code point → U+FFFD.
        assertEquals("\uFFFD", CssStringParser.parse("\"\\D800\""))
        // Above U+10FFFF → U+FFFD.
        assertEquals("\uFFFD", CssStringParser.parse("\"\\110000\""))
        // An astral code point decodes to its surrogate pair.
        assertEquals(String(Character.toChars(0x1F600)), CssStringParser.parse("\"\\1F600\""))
        // At most six hex digits: the seventh character is literal.
        assertEquals("\u00201", CssStringParser.parse("\"\\0000201\""))
        // A backslash before a non-hex character yields that character.
        assertEquals("\"q", CssStringParser.parse("\"\\\"q\""))
        // A line continuation (backslash + CR LF) is dropped entirely.
        assertEquals("ab", CssStringParser.parse("\"a\\\r\nb\""))
        // A backslash at EOF of an unterminated string is dropped.
        assertEquals("a", CssStringParser.parse("\"a\\"))
    }

    @Test
    fun `non-tokens are not decoded`() {
        // A raw newline makes a bad-string token.
        assertNull(CssStringParser.parse("\"a\nb\""))
        // Content after the ending quote means the value is not ONE token.
        assertNull(CssStringParser.parse("\"a\" \"b\""))
        // Unquoted text is not a string token at all.
        assertNull(CssStringParser.parse("abc"))
        // Nothing at all.
        assertNull(CssStringParser.parse("   "))
    }

    @Test
    fun `legacy acceptance of non-token input does not move`() {
        // Unquoted text kept as-is, exactly as before wave 54.
        assertEquals("-", stringOf("-"))
        // A malformed two-token value keeps the old outer-quote strip.
        assertEquals("a\" \"b", stringOf("\"a\" \"b\""))
        // An empty unquoted remainder still declines (no property).
        assertNull(HyphenateCharacterPropertyParser.parse("   "))
    }

    @Test
    fun `the wire carries the decoded value inside the existing string variant`() {
        // The production Json configuration (IRWireV2 shares the default).
        val wire = Json.encodeToJsonElement(
            IRPropertySerializer,
            HyphenateCharacterPropertyParser.parse("\"\"") as IRProperty
        ).jsonObject
        // {type, data} envelope (schema/spec/01): no new byte shape, only a new value.
        assertEquals("HyphenateCharacter", wire["type"]!!.jsonPrimitive.content)
        // The data object keeps its {type: "string", value} shape.
        assertEquals("string", wire["data"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("", wire["data"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun `end to end an empty string is no longer a Generic passthrough`() {
        // Through the longhand registry, as the converter runs it on -001.
        val out = PropertiesParser.parse(mapOf("hyphenate-character" to CssPropertyValue("\"\"")))
        // Exactly one property, typed, carrying "".
        val only = out.single()
        assertEquals(HyphenateCharacterValue.String(""), (only as HyphenateCharacterProperty).value)
    }
}
