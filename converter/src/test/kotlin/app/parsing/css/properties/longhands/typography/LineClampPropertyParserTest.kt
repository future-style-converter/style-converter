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
}
