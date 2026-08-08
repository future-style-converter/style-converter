package com.styleconverter.runtime.core.ir

// Unit pinning for the v2 strict decoder + v1 window routing — the
// decode-level counterpart of SchemaConformanceTest's golden-driven
// suite. Everything here is hand-built JSON so each rule is pinned in
// isolation from the golden fixtures.

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class IRDocumentDecoderTest {

    private fun expectError(json: String, needle: String) {
        try {
            IRDocumentDecoder.decode(json)
            fail("expected IllegalArgumentException containing '$needle'")
        } catch (e: IllegalArgumentException) {
            assertTrue("'${e.message}' should mention '$needle'",
                e.message?.contains(needle, ignoreCase = true) == true)
        }
    }

    // ── version discovery (spec 05) ──────────────────────────────────────

    @Test
    fun `versionless document routes to the tolerant v1 path with nested children`() {
        // v1 wire: nested children + underscore metadata + unknown keys
        // tolerated (the deprecation-window contract, byte-identical to
        // the pre-freeze harness behaviour).
        val doc = IRDocumentDecoder.decode(
            """{"components":[{"id":"p","name":"P","properties":[],"_role":"body-root",
                "children":[{"id":"c","name":"C","properties":[],"_text":"hi","_tag":"li"}]}]}"""
        )
        assertEquals("hi", doc.components[0].children!![0]._text)
        assertEquals("li", doc.components[0].children!![0]._tag)
        // v1 caveat unchanged during the window: _role has no v1 field.
        assertNull(doc.components[0].role)
    }

    @Test
    fun `irVersion other than 2 is refused loudly`() {
        expectError("""{"irVersion":3,"minReaderVersion":2,"components":[]}""", "irVersion 3")
    }

    @Test
    fun `non-object root is refused`() {
        expectError("""[1,2,3]""", "object")
    }

    // ── v2 field mapping ─────────────────────────────────────────────────

    @Test
    fun `v2 renames map onto the in-memory model`() {
        val doc = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[{"type":"Width","data":{"type":"length","px":50.0}}],
                 "text":"body","meta":{"sourceTag":"ol","role":"body-root"},
                 "pseudos":{"before":{"content":"→"}},
                 "slot":{"parent":"root-0","name":"header"}}]}"""
        )
        val c = doc.components[0]
        assertEquals("body", c._text)                 // text → _text
        assertEquals("ol", c._tag)                    // meta.sourceTag → _tag
        assertEquals("body-root", c.role)             // meta.role → role (new)
        // pseudos forwarded verbatim as an opaque object (design §4.2).
        assertEquals("→", c.pseudos!!["before"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        // Explicit non-default slot name survives.
        assertEquals("root-0", c.slot!!.parent)
        assertEquals("header", c.slot!!.name)
        // Property envelope decoded generically.
        assertEquals("Width", c.properties[0].type)
        // v2 components are always flat in-memory until composed.
        assertNull(c.children)
    }

    @Test
    fun `v2 slot name defaults to content when omitted`() {
        val doc = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],"slot":{"parent":"r"}}]}"""
        )
        assertEquals("content", doc.components[0].slot!!.name)
    }

    // ── v2 strictness details not covered by the conformance matrix ─────

    @Test
    fun `v2 unknown slot key errors`() {
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],"slot":{"parent":"r","index":0}}]}""",
            "index"
        )
    }

    @Test
    fun `v2 unknown selector key errors`() {
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],
                 "selectors":[{"condition":"hover","properties":[],"specificity":10}]}]}""",
            "specificity"
        )
    }

    @Test
    fun `v2 property wrapper missing data errors`() {
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[{"type":"Width"}]}]}""",
            "data"
        )
    }

    @Test
    fun `v2 unknown meta key errors`() {
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],"meta":{"tag":"ol"}}]}""",
            "tag"
        )
    }

    // ── wave-22 lane DECOR: meta.decorations ─────────────────────────────

    @Test
    fun `v2 meta decorations decode in order with authored colour tokens`() {
        // The live text-decoration-color__7-008 wire: three decorating
        // boxes, outermost-first, colours AS AUTHORED (no sRGB leaf here —
        // DecorationWire resolves the tokens at paint time).
        val doc = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],"text":"run","meta":{"decorations":[
                    {"line":"underline","color":"blue"},
                    {"line":"overline","color":"gray"},
                    {"line":"line-through"}]}}]}"""
        )
        val decorations = doc.components[0].decorations!!
        assertEquals(3, decorations.size)
        assertEquals(listOf("underline", "overline", "line-through"), decorations.map { it.line })
        assertEquals(listOf("blue", "gray", null), decorations.map { it.color })
    }

    @Test
    fun `v2 meta decorations tolerates an unknown line keyword at DECODE time`() {
        // Spec 05 tolerance rule 1: an unknown keyword is a future contract,
        // not a malformed envelope. The decoder keeps it raw; the paint-time
        // filter (DecorationWire) drops + logs it. Erroring here would make
        // a future §2.1 keyword unrenderable instead of merely unpainted.
        val doc = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],"meta":{"decorations":[{"line":"spiral"}]}}]}"""
        )
        assertEquals("spiral", doc.components[0].decorations!![0].line)
    }

    @Test
    fun `absent meta decorations stays null`() {
        // Null (not empty!) is what makes "present but empty" meaningful.
        val doc = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],"meta":{"sourceTag":"span"}}]}"""
        )
        assertNull(doc.components[0].decorations)
    }

    @Test
    fun `v2 meta decorations rejects every malformed SHAPE`() {
        val head = """{"irVersion":2,"minReaderVersion":2,"components":[
            {"id":"a","name":"A","properties":[],"meta":{"decorations":"""
        // Not an array.
        expectError("""$head{"line":"underline"}}}]}""", "must be an array")
        // Empty array — the converter omits the key instead (minItems 1).
        expectError("""$head[]}}]}""", "minItems")
        // Entry not an object.
        expectError("""$head["underline"]}}]}""", "must be objects")
        // Unknown entry key (schema: additionalProperties false).
        expectError("""$head[{"line":"underline","style":"wavy"}]}}]}""", "style")
        // Missing / non-string `line`.
        expectError("""$head[{"color":"blue"}]}}]}""", "line")
        expectError("""$head[{"line":7}]}}]}""", "line")
        // Non-string `color` — colour tokens are authored CSS text.
        expectError("""$head[{"line":"underline","color":7}]}}]}""", "color")
    }

    @Test
    fun `v2 component missing name errors`() {
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","properties":[]}]}""",
            "name"
        )
    }

    /**
     * Wave 27, lane CBAKE — `meta.markerText` decodes onto
     * [IRComponent.markerText] verbatim, and `meta.attrs.start` joins the
     * strict attr key set. Live shape: css3-counter-styles-159's
     * `<ol start='1860'><li>` under `list-style-type: cambodian`.
     *
     * Why the DECODE is the unit under test rather than the renderer read:
     * the renderer's preference is two lines (`child.markerText ?: …`), but
     * a decoder that dropped the key would make those two lines dead and
     * silent. TWIN of the iOS `ConformanceTests` markerText case.
     */
    @Test
    fun `v2 meta markerText and attrs start decode verbatim`() {
        val doc = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"ol","name":"L","properties":[],
                 "meta":{"sourceTag":"ol","attrs":{"start":"1860"}}},
                {"id":"li","name":"I","properties":[],"slot":{"parent":"ol"},
                 "meta":{"sourceTag":"li","markerText":"\u17e1\u17e8\u17e6\u17e0."}}]}"""
        )
        assertEquals("1860", doc.components[0].attrs?.start)
        assertEquals("\u17e1\u17e8\u17e6\u17e0.", doc.components[1].markerText)
        // Absence stays null — that is what keeps the renderer on its own
        // counter-style table for every unbaked list in the corpus.
        assertNull(doc.components[0].markerText)
    }

    /**
     * Wave 36, lane M1 — `meta.attrs.src` (the REPLACED-ELEMENT SOURCE lane:
     * img/embed/object/video, one canonical key whatever the markup spelled)
     * joins the strict attr key set and decodes as a VERBATIM string.
     *
     * Why the decode is load-bearing here even though Compose does not yet
     * PAINT the source: this decoder is strict on the attr key set, so a
     * missing entry would make it THROW on a document the web consumer
     * needs — the entire css-images object-fit family. Accepting the key is
     * the wire contract; painting it is the named follow-up (a device cannot
     * read the host's corpus, the same asymmetry the @font-face channel
     * documents). TWIN of the iOS `ConformanceTests` replaced-source case.
     */
    @Test
    fun `v2 meta attrs src decodes verbatim for every replaced tag`() {
        val doc = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"i","name":"I","properties":[],
                 "meta":{"sourceTag":"img","attrs":{"src":"css/css-images/support/colors-16x8.png"}}},
                {"id":"o","name":"O","properties":[],
                 "meta":{"sourceTag":"object","attrs":{"src":"css/css-images/support/colors-16x8.svg"}}},
                {"id":"v","name":"V","properties":[],
                 "meta":{"sourceTag":"video","attrs":{"src":"data:image/png,%89%50"}}}]}"""
        )
        assertEquals("css/css-images/support/colors-16x8.png", doc.components[0].attrs?.src)
        // <object> spells it `data` and <video> spells it `poster` in HTML;
        // the wire normalizes both to `src`, so the decoder sees one key.
        assertEquals("css/css-images/support/colors-16x8.svg", doc.components[1].attrs?.src)
        // A `data:` payload is a legal wire value and is never re-resolved.
        assertEquals("data:image/png,%89%50", doc.components[2].attrs?.src)
        // Absence stays null — no fabricated path ever reaches a renderer.
        assertNull(doc.components[0].attrs?.start)
    }

    @Test
    fun `v2 rejects an unknown meta key even next to markerText`() {
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"A","properties":[],
                 "meta":{"markerText":"1.","markerFont":"x"}}]}""",
            "markerFont"
        )
    }
}
