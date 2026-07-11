package com.styleconverter.runtime.core.ir

// Wire pinning for the additive v2 `keyframes` envelope key (spec 07 §1.2
// / schema $defs/keyframeSet + keyframeStop) on the Compose reader —
// wave-8 companion to IRDocumentDecoderTest. Hand-built JSON mirrors the
// conformance golden's shapes (resolved sorted offsets, typed stop
// envelopes, the dangling animation-name riding a COMPONENT untouched).

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KeyframesDecodeTest {

    private fun expectError(json: String, needle: String) {
        try {
            IRDocumentDecoder.decode(json)
            fail("expected IllegalArgumentException containing '$needle'")
        } catch (e: IllegalArgumentException) {
            assertTrue("'${e.message}' should mention '$needle'",
                e.message?.contains(needle, ignoreCase = true) == true)
        }
    }

    // Minimal valid v2 envelope carrying one two-stop fade set + a
    // multi-property mid stop — the golden's shape vocabulary condensed.
    private val doc = """{
        "irVersion": 2, "minReaderVersion": 2,
        "components": [
            {"id": "a", "name": "A", "properties": [
                {"type": "AnimationName", "data": [{"type": "identifier", "name": "fade"}]}
            ]}
        ],
        "keyframes": {
            "fade": [
                {"offset": 0.0, "properties": [{"type": "Opacity", "data": {"alpha": 0.0}}]},
                {"offset": 0.5, "properties": [
                    {"type": "Opacity", "data": {"alpha": 0.25}},
                    {"type": "Width", "data": {"type": "length", "px": 140.0}}
                ]},
                {"offset": 1.0, "properties": [{"type": "Opacity", "data": {"alpha": 1.0}}]}
            ]
        }
    }"""

    @Test
    fun `keyframes decode with resolved offsets and typed stop envelopes`() {
        val decoded = IRDocumentDecoder.decode(doc)
        val fade = decoded.keyframes?.get("fade")
            ?: fail("keyframes map missing 'fade'").let { return }
        // Offsets arrive RESOLVED and SORTED (readers rely on sortedness).
        assertEquals(listOf(0.0, 0.5, 1.0), fade.map { it.offset })
        // Stop payloads stay standard {type,data} envelopes.
        assertEquals("Opacity", fade[0].properties[0].type)
        assertEquals(0.25,
            ((fade[1].properties[0].data as JsonObject)["alpha"]!!.jsonPrimitive.content).toDouble(), 1e-9)
        // Multi-property stop keeps every declaration.
        assertEquals(listOf("Opacity", "Width"), fade[1].properties.map { it.type })
    }

    @Test
    fun `document without keyframes decodes with null map — omit-when-empty`() {
        val decoded = IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[]}"""
        )
        // Pre-motion documents stay byte-identical: no phantom empty map.
        assertNull(decoded.keyframes)
    }

    @Test
    fun `dangling animation-name rides the component untouched`() {
        // Spec 07 §1.3: the WIRE carries dangling references verbatim —
        // no decode-time validation against the keyframes map (the no-op
        // + log happens at the driver, never at decode).
        val decoded = IRDocumentDecoder.decode("""{
            "irVersion":2,"minReaderVersion":2,
            "components":[{"id":"g","name":"Ghost","properties":[
                {"type":"AnimationName","data":[{"type":"identifier","name":"ghost"}]}
            ]}]
        }""")
        val name = ((decoded.components[0].properties[0].data
            as? kotlinx.serialization.json.JsonArray)?.get(0) as JsonObject)["name"]
        assertEquals("ghost", name!!.jsonPrimitive.content)
    }

    // ── strictness (schema additionalProperties/min* rules) ─────────────

    @Test
    fun `empty keyframes map is a loud writer bug`() {
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[],"keyframes":{}}""",
            "minProperties"
        )
    }

    @Test
    fun `empty stop list is a loud writer bug`() {
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[],"keyframes":{"x":[]}}""",
            "minItems"
        )
    }

    @Test
    fun `unknown stop key is refused`() {
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[],
                "keyframes":{"x":[{"offset":0.0,"properties":[],"easing":"linear"}]}}""",
            "easing"
        )
    }

    @Test
    fun `offset outside the resolved fraction range is refused`() {
        // The converter DROPS invalid selectors (never clamps); a >1 offset
        // reaching a reader means that rule was bypassed upstream.
        expectError(
            """{"irVersion":2,"minReaderVersion":2,"components":[],
                "keyframes":{"x":[{"offset":1.5,"properties":[{"type":"Opacity","data":1}]}]}}""",
            "outside [0, 1]"
        )
    }

    @Test
    fun `unknown property TYPE inside a stop is tolerated`() {
        // Spec 05 tolerance rule 1 applies to stop payloads byte-identically
        // to component payloads: unknown types decode as opaque envelopes.
        val decoded = IRDocumentDecoder.decode("""{
            "irVersion":2,"minReaderVersion":2,"components":[],
            "keyframes":{"x":[{"offset":0.0,"properties":[{"type":"NotARealType","data":{"z":1}}]}]}
        }""")
        assertEquals("NotARealType", decoded.keyframes!!["x"]!![0].properties[0].type)
    }
}
