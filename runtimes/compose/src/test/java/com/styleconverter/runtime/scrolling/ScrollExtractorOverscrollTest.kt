package com.styleconverter.runtime.scrolling

// Retro R6 (audit finding A6#5) — the single-value `overscroll-behavior`
// wire. css-overscroll-1 §4 (the shorthand; §4.1 is the longhands): "If only
// one value is specified, the second value defaults to the same value." The converter serialises that form with
// an explicit JSON null y (`{"x": "CONTAIN", "y": null}` — OverscrollBehavior
// PropertyParser.kt leaves y = null and the nullable field has no default;
// verbatim from converting fixtures/properties/scrolling/longtail.json), and
// the reader's `?: x` fallback was dead code behind a non-null function, so
// y came out AUTO. Proven able to fail: restoring
// `extractOverscrollBehaviorMode(obj["y"]) ?: x` fails the first test.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollExtractorOverscrollTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun overscroll(wire: String): OverscrollConfig =
        ScrollExtractor.extractScrollConfig(listOf("OverscrollBehavior" to parse(wire))).overscroll

    @Test
    fun `single-value wire with explicit null y mirrors x`() {
        // `overscroll-behavior: contain` as the converter writes it.
        val o = overscroll("""{"x": "CONTAIN", "y": null}""")
        assertEquals(OverscrollBehaviorMode.CONTAIN, o.x)
        assertEquals("§4: the second value defaults to the first", OverscrollBehaviorMode.CONTAIN, o.y)
        // `overscroll-behavior: none` likewise.
        val n = overscroll("""{"x": "NONE", "y": null}""")
        assertEquals(OverscrollBehaviorMode.NONE to OverscrollBehaviorMode.NONE, n.x to n.y)
    }

    @Test
    fun `single-value wire with an absent y key mirrors x too`() {
        // A producer that omits the key entirely must read the same way.
        val o = overscroll("""{"x": "CONTAIN"}""")
        assertEquals(OverscrollBehaviorMode.CONTAIN to OverscrollBehaviorMode.CONTAIN, o.x to o.y)
    }

    @Test
    fun `two-value wire keeps independent axes`() {
        // `overscroll-behavior: contain none` (longtail.json's
        // OverscrollBehavior_ContainNone).
        val o = overscroll("""{"x": "CONTAIN", "y": "NONE"}""")
        assertEquals(OverscrollBehaviorMode.CONTAIN, o.x)
        assertEquals(OverscrollBehaviorMode.NONE, o.y)
    }

    @Test
    fun `auto single value stays auto on both axes`() {
        // longtail.json's OverscrollBehavior_Auto → {"x":"AUTO","y":null}.
        val o = overscroll("""{"x": "AUTO", "y": null}""")
        assertEquals(OverscrollBehaviorMode.AUTO to OverscrollBehaviorMode.AUTO, o.x to o.y)
    }

    @Test
    fun `bare keyword legacy shape sets both axes`() {
        val o = overscroll("\"NONE\"")
        assertEquals(OverscrollBehaviorMode.NONE to OverscrollBehaviorMode.NONE, o.x to o.y)
    }
}
