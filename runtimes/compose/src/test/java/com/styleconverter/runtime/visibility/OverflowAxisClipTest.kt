package com.styleconverter.runtime.visibility

// Wave 18 RC4 — axis-selective overflow clipping pins.
//
// Wire shapes below are copied from the LIVE per-test IR at
// tools/titan/runs/wave18-gate/sections/css-overflow/per-test-ir/
// wpt__css-overflow__clip-003.json: OverflowX/OverflowY carry a bare
// UPPERCASE keyword string ("CLIP", "AUTO", …). The rules under test are
// css-overflow-3 §3 (every non-visible used value clips its axis) and
// §3.1 (used-value coercion; visible+clip is the only single-axis pair).
// The pure functions pinned here are signature-parallel with the iOS
// twins in StyleEngine/visibility/AxisClipRect.swift.

import androidx.compose.ui.Modifier
import com.styleconverter.runtime.scrolling.AxisClip
import com.styleconverter.runtime.scrolling.OverflowApplier
import com.styleconverter.runtime.scrolling.OverflowBehavior
import com.styleconverter.runtime.scrolling.OverflowConfig
import com.styleconverter.runtime.scrolling.OverflowExtractor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OverflowAxisClipTest {

    // Live-wire helper: property (type, data) pairs exactly as decoded.
    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    @Test
    fun `clip-003 wire - OverflowX CLIP alone clips X only`() {
        // clip-003 component clip-003__1__0 declares ONLY OverflowX: "CLIP".
        // §3.1: visible pairs freely with clip, so Y stays visible — the
        // translucent blue child must spill vertically but not horizontally.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowX", "\"CLIP\""))
        )
        assertTrue("X must clip", cfg.clipsX)
        assertFalse("Y must stay visible (spill)", cfg.clipsY)
        // The used values behind the flags: clip stays clip, visible stays visible.
        assertEquals(OverflowBehavior.CLIP, cfg.usedOverflowX)
        assertEquals(OverflowBehavior.VISIBLE, cfg.usedOverflowY)
    }

    @Test
    fun `clip-003 wire - both axes CLIP clip both`() {
        // Component clip-003__0__0 declares OverflowX+OverflowY: "CLIP" —
        // the both-axis case routes to the plain rectangle clip.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowX", "\"CLIP\""), pair("OverflowY", "\"CLIP\""))
        )
        assertTrue(cfg.clipsX)
        assertTrue(cfg.clipsY)
    }

    @Test
    fun `clip-003 wire - AUTO AUTO wrapper is a clipping scroll container`() {
        // The 50x50 wrapper declares OverflowX+OverflowY: "AUTO". §3: a
        // scroll container ALWAYS clips — pre-wave-18 shouldClip ignored
        // auto/scroll, which let the child's outline+spill leak on Android.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowX", "\"AUTO\""), pair("OverflowY", "\"AUTO\""))
        )
        assertTrue(cfg.clipsX)
        assertTrue(cfg.clipsY)
        assertTrue(cfg.shouldClip)
    }

    @Test
    fun `visible coerces to auto beside hidden - both axes clip`() {
        // §3.1: overflow-x: hidden with overflow-y unset (visible) makes
        // the visible axis compute to auto — so BOTH axes clip, unlike the
        // visible+clip pair above.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowX", "\"HIDDEN\""))
        )
        assertEquals(OverflowBehavior.AUTO, cfg.usedOverflowY)
        assertTrue(cfg.clipsX)
        assertTrue(cfg.clipsY)
    }

    @Test
    fun `clip coerces to hidden beside scroll`() {
        // §3.1: clip cannot coexist with a scroll container — it hardens
        // to hidden. Both axes clip either way.
        val cfg = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowX", "\"CLIP\""), pair("OverflowY", "\"SCROLL\""))
        )
        assertEquals(OverflowBehavior.HIDDEN, cfg.usedOverflowX)
        assertTrue(cfg.clipsX)
        assertTrue(cfg.clipsY)
    }

    @Test
    fun `default visible-visible does not clip`() {
        // No overflow declared → CSS initial visible/visible → identity.
        val cfg = OverflowConfig()
        assertFalse(cfg.clipsX)
        assertFalse(cfg.clipsY)
        assertFalse(cfg.shouldClip)
    }

    @Test
    fun `usedOverflow pure-function coercion table`() {
        // Direct pins on the §3.1 pure function (cross-native parity
        // surface — iOS OverflowClipRules.usedOverflow mirrors this table).
        val v = OverflowBehavior.VISIBLE
        val c = OverflowBehavior.CLIP
        val h = OverflowBehavior.HIDDEN
        val s = OverflowBehavior.SCROLL
        val a = OverflowBehavior.AUTO
        // visible+clip family: used as specified (the single-axis pair).
        assertEquals(v, OverflowConfig.usedOverflow(v, c))
        assertEquals(c, OverflowConfig.usedOverflow(c, v))
        assertEquals(v, OverflowConfig.usedOverflow(v, v))
        assertEquals(c, OverflowConfig.usedOverflow(c, c))
        // visible beside a scroll-container partner → auto.
        assertEquals(a, OverflowConfig.usedOverflow(v, h))
        assertEquals(a, OverflowConfig.usedOverflow(v, s))
        assertEquals(a, OverflowConfig.usedOverflow(v, a))
        // clip beside a scroll-container partner → hidden.
        assertEquals(h, OverflowConfig.usedOverflow(c, s))
        assertEquals(h, OverflowConfig.usedOverflow(c, a))
        assertEquals(h, OverflowConfig.usedOverflow(c, h))
        // Non-coerced values pass through untouched.
        assertEquals(h, OverflowConfig.usedOverflow(h, v))
        assertEquals(s, OverflowConfig.usedOverflow(s, a))
    }

    @Test
    fun `clipBounds extends only the unclipped axis`() {
        // Geometry pin for the drawing clip on a 50x50 box (clip-003's
        // black box size): clipX-only binds X to [0,50] and extends Y by
        // the pinned finite extent (±1e6 — clipRect takes Floats; Skia
        // rejects non-finite rects, so infinity is not usable).
        val b = AxisClip.clipBounds(clipX = true, clipY = false, width = 50f, height = 50f)
        assertEquals(0f, b.left, 0f)
        assertEquals(50f, b.right, 0f)
        assertEquals(-AxisClip.UNCLIPPED_EXTENT, b.top, 0f)
        assertEquals(50f + AxisClip.UNCLIPPED_EXTENT, b.bottom, 0f)
        // Mirror: clipY-only binds Y and extends X.
        val b2 = AxisClip.clipBounds(clipX = false, clipY = true, width = 50f, height = 50f)
        assertEquals(-AxisClip.UNCLIPPED_EXTENT, b2.left, 0f)
        assertEquals(50f + AxisClip.UNCLIPPED_EXTENT, b2.right, 0f)
        assertEquals(0f, b2.top, 0f)
        assertEquals(50f, b2.bottom, 0f)
    }

    @Test
    fun `applyOverflow routes configs to the expected modifier kind`() {
        // Smoke pins on the applier's routing (modifier construction is
        // pure — no composition needed at JVM-unit level).
        val single = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowX", "\"CLIP\""))
        )
        val both = OverflowExtractor.extractOverflowConfig(
            listOf(pair("OverflowX", "\"CLIP\""), pair("OverflowY", "\"CLIP\""))
        )
        // Clipping configs must attach SOME modifier (not identity).
        assertNotSame(Modifier, OverflowApplier.applyOverflow(Modifier, single))
        assertNotSame(Modifier, OverflowApplier.applyOverflow(Modifier, both))
        // visible/visible must stay identity (no stray clip on defaults).
        assertSame(Modifier, OverflowApplier.applyOverflow(Modifier, OverflowConfig()))
    }
}
