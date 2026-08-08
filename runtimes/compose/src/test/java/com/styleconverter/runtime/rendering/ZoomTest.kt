package com.styleconverter.runtime.rendering

import androidx.compose.ui.unit.Constraints
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wave-37 zoom convergence — unit pins for the Android half of CSS
 * `zoom` (css-viewport-1 §"The zoom property").
 *
 * The defect this fixes: [ZoomExtractor] only understood a bare
 * `JsonPrimitive`, but the converter emits a TAGGED OBJECT —
 * `{"type":"number","value":1.5}` for `zoom: 1.5`, measured on
 * out/tmpOutput.json for fixtures/visual-test.json's Zoom_150/75/200
 * components. Every real document therefore fell through to the default
 * config and `zoom` was silently dropped on Android while the web engine
 * rendered it, splitting the three-way comparison.
 *
 * The pins below are the wire-shape table (one per sealed variant of
 * `ZoomValue` in ZoomPropertyParser.kt) plus the de-scale arithmetic
 * ZoomApplier's layout node runs on the incoming constraints — the half
 * that moves the LAYOUT SLOT and is therefore the difference between
 * this and a paint-only `Modifier.scale`.
 *
 * The SwiftUI twin lives in
 * runtimes/swiftui/Tests/StyleConverterRuntimeTests/ZoomTests.swift.
 */
class ZoomTest {

    private fun props(json: String): List<Pair<String, JsonElement?>> =
        listOf("Zoom" to Json.parseToJsonElement(json))

    // ---- wire shapes ------------------------------------------------

    @Test
    fun `number variant carries the factor verbatim`() {
        val c = ZoomExtractor.extractZoomConfig(props("""{"type":"number","value":1.5}"""))
        assertEquals(1.5f, c.zoom, 1e-6f)
        assertFalse(c.isNormal)
        assertTrue(c.hasZoom)
    }

    @Test
    fun `percentage variant divides by 100`() {
        // The suffix is load-bearing: reading 150% as 150 would zoom two
        // orders of magnitude too far (the exact corruption the web
        // extractor's header documents).
        val c = ZoomExtractor.extractZoomConfig(props("""{"type":"percentage","value":150}"""))
        assertEquals(1.5f, c.zoom, 1e-6f)
        assertFalse(c.isNormal)
    }

    @Test
    fun `sub-unit factors survive`() {
        assertEquals(
            0.75f,
            ZoomExtractor.extractZoomConfig(props("""{"type":"number","value":0.75}""")).zoom,
            1e-6f
        )
    }

    @Test
    fun `normal and reset are identity`() {
        // css-viewport-1: `normal` computes to the same used scale as 1.
        // `reset` is the legacy WebKit keyword a browser rejects outright,
        // so an unzoomed render is the CONVERGENT reading, not a gap.
        for (tag in listOf("normal", "reset")) {
            val c = ZoomExtractor.extractZoomConfig(props("""{"type":"$tag"}"""))
            assertEquals(1.0f, c.zoom, 1e-6f)
            assertTrue(c.isNormal)
            assertFalse("zoom: $tag must not chain a scale node", c.hasZoom)
        }
    }

    @Test
    fun `explicit zoom 1 does not chain a node`() {
        assertFalse(
            ZoomExtractor.extractZoomConfig(props("""{"type":"number","value":1}""")).hasZoom
        )
    }

    @Test
    fun `non-positive and non-finite factors degrade to identity`() {
        // The layout node divides by the factor; <= 0 would produce
        // negative extents and NaN would poison the measure pass.
        for (v in listOf("0", "-2")) {
            assertFalse(
                "zoom: $v must not reach the layout node",
                ZoomExtractor.extractZoomConfig(props("""{"type":"number","value":$v}""")).hasZoom
            )
        }
    }

    @Test
    fun `unknown variant and missing property are identity`() {
        assertFalse(ZoomExtractor.extractZoomConfig(props("""{"type":"future"}""")).hasZoom)
        assertFalse(ZoomExtractor.extractZoomConfig(emptyList()).hasZoom)
    }

    @Test
    fun `last write wins`() {
        val c = ZoomExtractor.extractZoomConfig(
            listOf(
                "Zoom" to Json.parseToJsonElement("""{"type":"number","value":2}"""),
                "Zoom" to Json.parseToJsonElement("""{"type":"percentage","value":50}""")
            )
        )
        assertEquals(0.5f, c.zoom, 1e-6f)
    }

    @Test
    fun `bare primitive payloads still decode`() {
        assertEquals(2f, ZoomExtractor.extractZoomConfig(props("2")).zoom, 1e-6f)
        assertTrue(ZoomExtractor.extractZoomConfig(props(""""normal"""")).isNormal)
    }

    // ---- the slot-moving arithmetic ---------------------------------

    @Test
    fun `descale divides the available room by the factor`() {
        // zoom: 1.5 in a 390px containing block → the element lays out in
        // a 260px space of its own, then paints 1.5x — which is exactly
        // why a zoomed block still fits its parent in the browser.
        assertEquals(260, ZoomApplier.descale(390, 1.5f))
        // zoom: 0.75 GROWS the room (390 / 0.75 = 520).
        assertEquals(520, ZoomApplier.descale(390, 0.75f))
        assertEquals(195, ZoomApplier.descale(390, 2f))
    }

    @Test
    fun `descale preserves unbounded constraints`() {
        // An intrinsic / scrollable pass legitimately hands the node
        // Infinity; dividing Int.MAX_VALUE would overflow Constraints.
        assertEquals(Constraints.Infinity, ZoomApplier.descale(Constraints.Infinity, 1.5f))
        assertEquals(Constraints.Infinity, ZoomApplier.descale(Constraints.Infinity, 0.75f))
    }

    @Test
    fun `descale never returns a negative extent`() {
        assertEquals(0, ZoomApplier.descale(0, 2f))
    }
}
