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

    @Test
    fun `v2 component missing name errors`() {
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","properties":[]}]}""",
            "name"
        )
    }
}
