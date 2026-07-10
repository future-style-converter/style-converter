package com.styleconverter.runtime.effects.clip

// Fidelity wave 1 — pinning tests for the legacy `clip: rect(...)` gating.
//
// CSS 2.1 §11.1.2: `clip` applies ONLY to absolutely positioned elements
// (position: absolute | fixed). Browsers ignore it everywhere else, and the
// web reference render for Effects_BoxModel / Effects_Decorated (static
// boxes carrying `clip: rect(10px,150px,110px,10px)`) is UNclipped. The
// extractor used to honour the rect universally AND first-match the IR
// order, so `clip` before `clip-path` silently swallowed the clip-path
// (Effects_C01_BackdropFilter).

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyClipGatingTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // The canonical rect wire shape from the effects.combos fixture.
    private val rectJson =
        """{"type":"rect","top":{"px":10.0},"right":{"px":150.0},"bottom":{"px":110.0},"left":{"px":10.0}}"""

    @Test
    fun `clip rect on a static element is ignored`() {
        // No Position property at all → static → per spec the rect must NOT
        // produce a clip shape (web renders the box unclipped).
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("Clip", rectJson))
        )
        assertNull("legacy clip must be ignored without absolute positioning", cfg.shape)
    }

    @Test
    fun `clip rect on a relative element is ignored`() {
        // position: relative is NOT absolutely positioned — still ignored.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("Position", "\"RELATIVE\""), pair("Clip", rectJson))
        )
        assertNull(cfg.shape)
    }

    @Test
    fun `clip rect on an absolute element is honoured`() {
        // position: absolute → the spec's one applicable case.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("Position", "\"ABSOLUTE\""), pair("Clip", rectJson))
        )
        assertTrue("expected LegacyRect", cfg.shape is ClipShape.LegacyRect)
    }

    @Test
    fun `clip rect on a fixed element is honoured`() {
        // position: fixed is also absolutely positioned per CSS 2.1.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("Position", "\"FIXED\""), pair("Clip", rectJson))
        )
        assertTrue(cfg.shape is ClipShape.LegacyRect)
    }

    @Test
    fun `clip-path wins over legacy clip regardless of IR property order`() {
        // Effects_C01_BackdropFilter regression shape: `clip` FIRST in the
        // stream, then `clip-path: xywh(...)`. The xywh must win — the old
        // first-match loop returned the LegacyRect (region offset upward,
        // no rounded corners; Android-web 0.904).
        val xywh =
            """{"type":"xywh","x":{"px":10.0},"y":{"px":20.0},"w":{"px":120.0},"h":{"px":80.0},"round":{"px":12.0}}"""
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(
                pair("Position", "\"ABSOLUTE\""), // even when clip WOULD apply
                pair("Clip", rectJson),
                pair("ClipPath", xywh)
            )
        )
        assertTrue("clip-path must take precedence", cfg.shape is ClipShape.Xywh)
        val shape = cfg.shape as ClipShape.Xywh
        // Spot-check the xywh payload survived (y=20 differentiates it from
        // the rect's top=10 — exactly the upward offset the report caught).
        assertEquals(20f, shape.y.value, 0.01f)
    }
}
