package com.styleconverter.runtime.content

// Wave-49 lane A2 — pin table for PseudoBoxFold, the component rewrite that
// makes a generated `::before` / `::after` BOX a real child of its
// originating element (CSS 2.1 §12.1).
//
// The `pseudos` payloads are VERBATIM from wave48-final per-test-ir; the
// surrounding IRComponent is built through the runtime's own constructor
// because the wire→component hop is IRDocumentDecoder's job (it flattens
// `meta.role` → `role`, `text` → `_text`), not this fold's.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.ir.IRRun
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PseudoBoxFoldTest {

    /** Parse one wire `pseudos` object exactly as the decoder retains it. */
    private fun pseudos(json: String): JsonObject =
        Json.parseToJsonElement(json) as JsonObject

    /**
     * The css-pseudo/before-as-flex-container `pseudos` bucket, VERBATIM
     * from per-test-ir component __0-020.
     */
    private val flexBoxBucket = pseudos(
        """{"before": {"properties": {"content": "\"A B\"", "display": "flex", "justify-content": "space-between", "width": "200px", "height": "100px", "background": "green"}, "_text": "A B", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
    )

    /** The same test's originating `div`: 200x100, red, childless. */
    private fun redDiv(pseudos: JsonObject?, runs: List<IRRun>? = null) = IRComponent(
        id = "wpt__css-pseudo__before-as-flex-container__0-020",
        name = "wpt__css-pseudo__before-as-flex-container__0",
        properties = listOf(
            IRProperty("Width", Json.parseToJsonElement("""{"type":"length","px":200}""")),
            IRProperty("Height", Json.parseToJsonElement("""{"type":"length","px":100}""")),
            IRProperty("BackgroundColor",
                Json.parseToJsonElement("""{"srgb":{"r":1,"g":0,"b":0},"original":"red"}""")),
        ),
        runs = runs,
        pseudos = pseudos,
    )

    @Test
    fun `the generated flex box becomes the div's first child`() {
        val host = redDiv(flexBoxBucket)
        // Pre-condition: the wire div is a childless leaf — which is why the
        // pre-fix Android capture painted its red background bare.
        assertNull(host.children)
        val folded = PseudoBoxFold.resolve(host)
        assertEquals(1, folded.children!!.size)
        val box = folded.children!![0]
        // CSS 2.1 §12.1 order: ::before precedes the element's own content.
        assertEquals("wpt__css-pseudo__before-as-flex-container__0-020::before", box.id)
        // The box carries the typed declarations, so StyleApplier paints a
        // 200x100 green rectangle over the host's red background.
        assertTrue(box.properties.any { it.type == "Width" })
        assertTrue(box.properties.any { it.type == "Height" })
        assertTrue(box.properties.any { it.type == "BackgroundColor" })
        assertEquals("FLEX",
            (box.properties.first { it.type == "Display" }.data as JsonPrimitive).content)
        // Its baked content is the box's own text run.
        assertEquals("A B", box._text)
        // The generated box has no generated content of its own — the fold
        // is structurally non-recursive.
        assertNull(box.pseudos)
        // The host itself is untouched apart from the child list.
        assertEquals(host.properties, folded.properties)
    }

    @Test
    fun `an unclaimed bucket returns the very same instance`() {
        // The dominant corpus shape — a baked inline counter run, owned by
        // PseudoBucketExtractor. Identity (same instance) matters: the
        // renderer's remember{} key must not churn.
        val host = redDiv(pseudos(
            """{"before": {"properties": {"content": "\"1\" \"-\" \"5\""}, "_text": "1-5"}}"""
        ))
        assertSame(host, PseudoBoxFold.resolve(host))
    }

    @Test
    fun `a component with no pseudos bucket is the identity`() {
        // The entire committed 327-fixture baseline corpus: `grep -rl
        // '"pseudos"' fixtures/` returns zero files, so no committed capture
        // can observe this fold at all.
        val host = redDiv(null)
        assertSame(host, PseudoBoxFold.resolve(host))
    }

    @Test
    fun `meta runs refuses the fold rather than guessing a slot`() {
        // spec 03 §4.1: the run list is AUTHORITATIVE over the sibling walk,
        // and it cannot name a component that did not exist on the wire.
        val host = redDiv(flexBoxBucket, runs = listOf(IRRun(text = "x")))
        assertSame(host, PseudoBoxFold.resolve(host))
    }

    @Test
    fun `the display-contents family is returned unfolded, same instance`() {
        // Wave-49 lane F2 (skeptic S5): prove the wave does not silently DROP
        // this family's render. Payload VERBATIM from
        // tools/titan/runs/wave48-final/sections/css-display/per-test-ir/
        // wpt__css-display__display-contents-before-after-002.json, component
        // __2-020. Re-censused for this pin: 10 pseudo buckets across the
        // 1435 frozen per-test-ir docs declare a `border`, and EVERY one of
        // them is `display: contents` (this test plus the two
        // display-contents-dynamic-before-after files) — so the `contents`
        // gate, not the border refusal, is what protects the family.
        // css-display-3 §2.5: `display: contents` generates no box for the
        // element itself, so PseudoGeneratedBox refuses at its display gate,
        // this fold is the identity, and the inline wrapper keeps painting
        // the baked "P"…"S" around the host's "AS" exactly as in wave 48.
        // (PseudoBucketExtractorTest pins the wrapper half of the same claim.)
        val host = IRComponent(
            id = "wpt__css-display__display-contents-before-after-002__2-020",
            name = "wpt__css-display__display-contents-before-after-002__2",
            _text = "AS",
            pseudos = pseudos(
                """{"before": {"properties": {"display": "contents", "border": "100px solid red", "content": "\"P\""}, "_text": "P", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}, "after": {"properties": {"display": "contents", "border": "100px solid red", "content": "\"S\""}, "_text": "S", "_lossy": true, "_lossyReasons": ["generated-content-baked"]}}"""
            ),
        )
        assertSame(host, PseudoBoxFold.resolve(host))
    }

    @Test
    fun `an after box trails the element's own children`() {
        // CSS 2.1 §12.1: ::after is generated as the element's LAST child.
        val host = IRComponent(
            id = "h", name = "h",
            children = listOf(IRComponent(id = "kid", name = "kid")),
            pseudos = pseudos(
                """{"after": {"properties": {"content": "\"\"", "display": "block", "width": "10px", "height": "10px", "background": "green"}}}"""
            ),
        )
        val folded = PseudoBoxFold.resolve(host)
        assertEquals(listOf("kid", "h::after"), folded.children!!.map { it.id })
    }
}
