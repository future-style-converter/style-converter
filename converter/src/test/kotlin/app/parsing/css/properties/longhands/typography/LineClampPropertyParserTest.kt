package app.parsing.css.properties.longhands.typography

// wave-37 lane W2 — `line-clamp: auto` (css-overflow-4 §5.1).
//
// The parser's grammar was `none | <integer>`, so every `line-clamp: auto`
// in the corpus returned null and fell through to GenericProperty
// (`_unmapped:true`) — a declaration no runtime ever sees. Measured on the
// 45 bucket-A `line-clamp-auto-*` tests: the captures rendered the FULL
// unclamped text beside a ref clamped at the height constraint.
//
// Pinned invariants:
//   1. `auto` on the STANDARD property yields the Auto variant, whose wire
//      shape is {"type":"auto"} — no count, because the clamp point is a
//      used value only a runtime can resolve.
//   2. `auto` on the VENDOR alias stays invalid: css-overflow-4 Appendix A
//      freezes `-webkit-line-clamp` at the pre-`auto` grammar, and no
//      corpus test writes it. A shared parser would have silently widened
//      a legacy property.
//   3. `none` and `<integer>` are byte-unchanged on both spellings.

import app.irmodels.properties.typography.LineClampProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LineClampPropertyParserTest {

    private fun clamp(parser: (String) -> Any?, css: String) =
        (parser(css) as? LineClampProperty)?.clamp

    @Test
    fun `auto parses to the Auto variant on the standard property`() {
        val v = clamp(LineClampPropertyParser::parse, "auto")
        assertIs<LineClampProperty.LineClamp.Auto>(v)
    }

    @Test
    fun `auto is case-insensitive like every CSS keyword`() {
        assertIs<LineClampProperty.LineClamp.Auto>(clamp(LineClampPropertyParser::parse, "  AUTO "))
    }

    @Test
    fun `auto serializes as a countless type discriminator`() {
        val prop = LineClampPropertyParser.parse("auto") as LineClampProperty
        val wire = Json.encodeToJsonElement(LineClampProperty.serializer(), prop)
            .jsonObject["clamp"]!!.jsonObject
        assertEquals("auto", wire["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the webkit alias still rejects auto`() {
        // Appendix A's legacy grammar has no `auto` — the declaration must
        // be dropped, exactly as a browser drops it.
        assertNull(WebkitLineClampPropertyParser.parse("auto"))
    }

    @Test
    fun `none and integer are unchanged on both spellings`() {
        assertIs<LineClampProperty.LineClamp.None>(clamp(LineClampPropertyParser::parse, "none"))
        assertIs<LineClampProperty.LineClamp.None>(clamp(WebkitLineClampPropertyParser::parse, "none"))
        val a = clamp(LineClampPropertyParser::parse, "3")
        assertIs<LineClampProperty.LineClamp.Lines>(a)
        assertEquals(3.0, a.count.value)
        val b = clamp(WebkitLineClampPropertyParser::parse, "3")
        assertIs<LineClampProperty.LineClamp.Lines>(b)
        assertEquals(3.0, b.count.value)
    }

    @Test
    fun `garbage is still rejected outright`() {
        assertNull(LineClampPropertyParser.parse("sometimes"))
        assertNull(LineClampPropertyParser.parse("2.5"))
    }

    // ── wave-42 lane W7: the two-value grammar ──────────────────────────
    // css-overflow-4 §5.1 `none | [ <integer [1,∞]> || <'block-ellipsis'> ]
    // -webkit-legacy?`, pinned by WPT parsing/line-clamp-{valid,invalid}.html.
    // The first two declarations are VERBATIM from the failing reftests.

    @Test
    fun `4 no-ellipsis parses count plus the no-marker keyword`() {
        // block-ellipsis-023's exact declaration: clamp at 4, draw nothing.
        val v = clamp(LineClampPropertyParser::parse, "4 no-ellipsis")
        assertIs<LineClampProperty.LineClamp.Lines>(v)
        assertEquals(4.0, v.count.value)
        assertIs<LineClampProperty.BlockEllipsis.NoEllipsis>(v.ellipsis)
    }

    @Test
    fun `4 with an empty string keeps the string verbatim`() {
        // block-ellipsis-024's exact declaration: `""` has the same EFFECT as
        // no-ellipsis but its computed value keeps the (empty) string.
        val v = clamp(LineClampPropertyParser::parse, "4 \"\"")
        assertIs<LineClampProperty.LineClamp.Lines>(v)
        assertEquals(4.0, v.count.value)
        val e = v.ellipsis
        assertIs<LineClampProperty.BlockEllipsis.Text>(e)
        assertEquals("", e.value)
    }

    @Test
    fun `the double-bar accepts both component orders`() {
        // WPT valid set: `no-ellipsis 10` serializes as `10 no-ellipsis`.
        val v = clamp(LineClampPropertyParser::parse, "no-ellipsis 10")
        assertIs<LineClampProperty.LineClamp.Lines>(v)
        assertEquals(10.0, v.count.value)
        assertIs<LineClampProperty.BlockEllipsis.NoEllipsis>(v.ellipsis)
        // And a custom string before the count: `" etc., etc. " 12`.
        val s = clamp(LineClampPropertyParser::parse, "\" etc., etc. \" 12")
        assertIs<LineClampProperty.LineClamp.Lines>(s)
        assertEquals(12.0, s.count.value)
        val e = s.ellipsis
        assertIs<LineClampProperty.BlockEllipsis.Text>(e)
        assertEquals(" etc., etc. ", e.value)
    }

    @Test
    fun `auto as the marker component rides on the count`() {
        // WPT valid set: `8 auto` round-trips as `8 auto` — a distinct value.
        val v = clamp(LineClampPropertyParser::parse, "8 auto")
        assertIs<LineClampProperty.LineClamp.Lines>(v)
        assertEquals(8.0, v.count.value)
        assertIs<LineClampProperty.BlockEllipsis.Auto>(v.ellipsis)
    }

    @Test
    fun `explicit ellipsis keyword is the initial marker and leaves the wire bare`() {
        // WPT pins `8 ellipsis` ≡ `8`: the marker longhand's initial value.
        val v = clamp(LineClampPropertyParser::parse, "8 ellipsis")
        assertIs<LineClampProperty.LineClamp.Lines>(v)
        assertEquals(8.0, v.count.value)
        assertNull(v.ellipsis)
        // Byte-level: the emission must be IDENTICAL to plain `8` — no
        // `ellipsis` key at all (encodeDefaults off + null default).
        val bare = Json.encodeToJsonElement(
            LineClampProperty.serializer(),
            LineClampPropertyParser.parse("8") as LineClampProperty
        )
        val explicit = Json.encodeToJsonElement(
            LineClampProperty.serializer(),
            LineClampPropertyParser.parse("8 ellipsis") as LineClampProperty
        )
        assertEquals(bare, explicit)
    }

    @Test
    fun `no-ellipsis serializes as an additive nested type object`() {
        // The wire the runtimes consume: {"type":"lines","count":4.0,
        // "ellipsis":{"type":"no-ellipsis"}} — additive next to {type,count}.
        val prop = LineClampPropertyParser.parse("4 no-ellipsis") as LineClampProperty
        val wire = Json.encodeToJsonElement(LineClampProperty.serializer(), prop)
            .jsonObject["clamp"]!!.jsonObject
        assertEquals("lines", wire["type"]!!.jsonPrimitive.content)
        assertEquals("no-ellipsis", wire["ellipsis"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a lone marker canonicalizes to the Auto variant`() {
        // WPT pins `line-clamp: ellipsis` → `auto`; a lone <string> likewise
        // has no fixed count, so it clamps at the block-size constraint.
        assertIs<LineClampProperty.LineClamp.Auto>(clamp(LineClampPropertyParser::parse, "ellipsis"))
        assertIs<LineClampProperty.LineClamp.Auto>(clamp(LineClampPropertyParser::parse, "\"abcdefghi\""))
        assertIs<LineClampProperty.LineClamp.Auto>(clamp(LineClampPropertyParser::parse, "no-ellipsis"))
    }

    @Test
    fun `webkit-legacy is tolerated as a trailing token only`() {
        // `1 -webkit-legacy` is valid (opts into legacy layout — no IR change);
        // `-webkit-legacy` alone is invalid (WPT invalid set).
        val v = clamp(LineClampPropertyParser::parse, "1 -webkit-legacy")
        assertIs<LineClampProperty.LineClamp.Lines>(v)
        assertEquals(1.0, v.count.value)
        assertNull(LineClampPropertyParser.parse("-webkit-legacy"))
    }

    @Test
    fun `the invalid combinations from the WPT set are dropped`() {
        assertNull(LineClampPropertyParser.parse("none 2"))              // none combines with nothing
        assertNull(LineClampPropertyParser.parse("3 none"))              // ...in either order
        assertNull(LineClampPropertyParser.parse("none no-ellipsis"))    // ...nor with a marker
        assertNull(LineClampPropertyParser.parse("0"))                   // outside <integer [1,∞]>
        assertNull(LineClampPropertyParser.parse("-5"))                  // outside <integer [1,∞]>
        assertNull(LineClampPropertyParser.parse("0 -webkit-legacy"))    // legacy token saves nothing
        assertNull(LineClampPropertyParser.parse("4 5"))                 // integer slot filled twice
        assertNull(LineClampPropertyParser.parse("auto ellipsis"))       // marker slot filled twice
        assertNull(LineClampPropertyParser.parse("4 \"a"))               // unterminated string
    }

    @Test
    fun `the webkit alias stays frozen at the legacy grammar`() {
        // css-overflow-4 Appendix A: no marker component on the vendor alias.
        assertNull(WebkitLineClampPropertyParser.parse("1 no-ellipsis"))
        assertNull(WebkitLineClampPropertyParser.parse("1 \"~\""))
        assertNull(WebkitLineClampPropertyParser.parse("1 -webkit-legacy"))
    }
}
