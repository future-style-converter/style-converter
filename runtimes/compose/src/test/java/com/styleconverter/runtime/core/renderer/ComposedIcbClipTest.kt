package com.styleconverter.runtime.core.renderer

// wave-52 lane L2 (Fix A) — JVM pins for the composed canvas's INLINE-AXIS
// CLIP BAND ([composedIcbClipBandPx]), the geometry the Android harness's
// `ComposedCaptureCanvas` clips its content draw to.
//
// THE DEFECT. The browser-ref is rendered in a 358-px viewport and padded in
// image space, so the frame columns 0..15 / 374..389 never carry ink; the
// Compose composed canvas had no clip on that box, so a `width: 400px`
// container (css-gaps/flex/flex-gap-decorations-040, wave51-fix android
// P 0.9934) painted to x=389 — its whole 1664-px mismatch. 208 overrun-right
// + 84 overrun-left scored cells on wave51-fix (frame-ink-census.json).
//
// Every number is read out of tools/titan/capture-browser-ref.mjs:
//   CANVAS_WIDTH 390 · CANVAS_PAD_PX 16 · REF_RENDER_WIDTH 358 → band [16, 374).
// The iOS twin (WPTCanvas.icbClipBand, ComposedIcbClipTests) asserts the
// IDENTICAL numbers, so the two natives cannot drift.
//
// EXECUTED MUTATIONS (applied to WptCaptureMode.kt, this class run alone,
// source restored byte-exact — sha256 checked; record in
// tools/titan/results/wave52-composed-canvas/_note.md):
//   M1 `rightPx = canvasExtentPx` (frame not subtracted) → band_is16to374 red.
//   M2 the harness passes the RESOLVED pad instead of the frame — pinned by
//      the harness source scan (ComposedCanvasIcbClipSourceTest), since the
//      Modifier is not runnable on this JVM classpath (RootCanvasClipTest's
//      standing constraint); the pure-side twin here is
//      frame_notTheResolvedPad, which fails if the helper ever read a pad.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposedIcbClipTest {

    @Test
    fun band_is16to374_onTheDefaultCanvas() {
        // THE contract: the 390 canvas with the 16 frame clips content to
        // [16, 374) — image x=374 is the first frame column, exactly where the
        // ref's raster stops having ink (its right ink edge is 373).
        assertEquals(ComposedIcbClipBand(16f, 374f), composedIcbClipBandPx(390f, 16f))
    }

    @Test
    fun band_followsACaptureWidthOverride() {
        // CAPTURE_WIDTH=250 (the width250 harness path): the frame is the
        // same 16 per side, so the band is [16, 234) — the crop tracks the
        // live canvas width, never a hard-coded 390.
        assertEquals(ComposedIcbClipBand(16f, 234f), composedIcbClipBandPx(250f, 16f))
    }

    @Test
    fun band_isInPixels_sameUnitAsTheDrawScope() {
        // The harness draws in px (DrawScope.size), so it converts the dp
        // frame through the density first; at density 2.75 the 390dp canvas
        // is 1072.5 px and the frame 44 px → [44, 1028.5). The helper is
        // unit-agnostic: it never scales, it only subtracts.
        assertEquals(ComposedIcbClipBand(44f, 1028.5f), composedIcbClipBandPx(1072.5f, 44f))
    }

    @Test
    fun frame_notTheResolvedPad() {
        // An author `body { padding: 40px }` makes the harness's RESOLVED pad
        // 56 (frame + declared), but CSS 2.1 §9.1.1's viewport — the ref's
        // crop — does not move: the caller passes the FRAME (16), and 16/374
        // is the answer. Passing the resolved pad (the mutation the source
        // scan guards against) would give 56/334, which is NOT what the ref
        // shows for clip-path-circle-007-style padded bodies.
        val resolvedPad = 16f + 40f
        assertEquals(ComposedIcbClipBand(16f, 374f), composedIcbClipBandPx(390f, 16f))
        assertTrue(composedIcbClipBandPx(390f, resolvedPad) != composedIcbClipBandPx(390f, 16f))
    }

    @Test
    fun band_neverInverts_onADegenerateCanvas() {
        // A canvas narrower than two frames clamps to an EMPTY band at the
        // frame (left == right), never a negative-width rect.
        assertEquals(ComposedIcbClipBand(16f, 16f), composedIcbClipBandPx(20f, 16f))
    }

    @Test
    fun verticalSlack_isLargeAndFinite() {
        // The block axis stays visible: the slack must dwarf the tallest
        // wave51-fix capture (4232 px) yet stay finite so the clip rect's
        // arithmetic cannot overflow (a ±MAX_VALUE rect would).
        assertTrue(COMPOSED_ICB_CLIP_VERTICAL_SLACK_PX > 100_000f)
        assertTrue(COMPOSED_ICB_CLIP_VERTICAL_SLACK_PX.isFinite())
    }
}
