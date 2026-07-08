package com.styleconverter.runtime.core.ir

// Tests for IRComponent's `_text` + `_tag` fields (Bug 1 + Bug 2 fixes
// — see testing/titan/investigations/swarm-002/css-text-decor and
// css-counter-styles for the motivating investigations). These pin the
// JSON round-trip contract that the renderer relies on at runtime.
//
// We test serialization/deserialization rather than the full Compose
// renderer because Compose UI tests require androidTest (instrumented
// runtime) and these can be JVM-only. The runtime renderer path is
// exercised by the BASELINE=1 visual-test pipeline.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class IRComponentTextTagTest {

    // Json instance configured to allow unknown keys so legacy fixtures
    // with extra metadata keys don't break decode. Matches the runtime
    // configuration used by ComponentLoader at app launch.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `legacy IR with no _text or _tag deserializes successfully (327-pair compat)`() {
        // The 327-pair baseline fixtures (visual-test.json) have neither
        // field. They must continue to decode cleanly with both fields
        // defaulting to null, otherwise BASELINE=1 fails immediately.
        val src = """
            {
                "id": "legacy-1",
                "name": "Card",
                "properties": []
            }
        """.trimIndent()
        val c = json.decodeFromString(IRComponent.serializer(), src)
        assertEquals("legacy-1", c.id)
        assertEquals("Card", c.name)
        assertNull("_text must default to null for legacy fixtures", c._text)
        assertNull("_tag must default to null for legacy fixtures", c._tag)
    }

    @Test
    fun `IR with _text populates the field (swarm-001 leaf-text fix)`() {
        // FIX-A extractor emits _text for styled-element fixtures.
        val src = """
            {
                "id": "color-001",
                "name": "Test",
                "properties": [],
                "_text": "Test passes if this text is green"
            }
        """.trimIndent()
        val c = json.decodeFromString(IRComponent.serializer(), src)
        assertEquals("Test passes if this text is green", c._text)
        assertNull(c._tag)
    }

    @Test
    fun `IR with _tag populates the field (swarm-002 list-marker fix)`() {
        // The WPT extractor for <ol><li>...</li></ol> emits _tag='ol' on
        // the parent and _tag='li' on each child so the renderer can
        // wire up list-marker generation.
        val parent = """
            {
                "id": "list-1",
                "name": "List",
                "_tag": "ol",
                "children": [
                    { "id": "i1", "name": "Item1", "_tag": "li", "_text": "first" },
                    { "id": "i2", "name": "Item2", "_tag": "li", "_text": "second" }
                ]
            }
        """.trimIndent()
        val c = json.decodeFromString(IRComponent.serializer(), parent)
        assertEquals("ol", c._tag)
        assertNotNull("children must be deserialized", c.children)
        assertEquals(2, c.children!!.size)
        assertEquals("li", c.children[0]._tag)
        assertEquals("first", c.children[0]._text)
        assertEquals("li", c.children[1]._tag)
        assertEquals("second", c.children[1]._text)
    }

    @Test
    fun `IR mixed-content (parent _text + children) deserializes intact (Bug 1)`() {
        // Mixed-content shape from swarm-002 css-text-decor: parent
        // <div>abc <span>x</span> def</div> extracts as a parent with
        // both _text and children. Both must survive decode so the
        // renderer can interleave the text alongside the children.
        val src = """
            {
                "id": "mixed-1",
                "name": "Mixed",
                "_text": "abc def",
                "children": [
                    { "id": "child-1", "name": "Inner", "_text": "x" }
                ]
            }
        """.trimIndent()
        val c = json.decodeFromString(IRComponent.serializer(), src)
        assertEquals("abc def", c._text)
        assertNotNull(c.children)
        assertEquals(1, c.children!!.size)
        assertEquals("x", c.children[0]._text)
    }
}
