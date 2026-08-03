package com.styleconverter.runtime.core.renderer

// Wave 26, lane RES — the composed capture canvas's GEOMETRY pins
// (residuals 1 + 2). Every number here is read straight out of
// tools/titan/capture-browser-ref.mjs, so the suite fails the moment the
// natives and the ref pipeline disagree about the render viewport:
//   CANVAS_WIDTH           390
//   CANVAS_PAD_PX           16
//   REF_RENDER_WIDTH       390 − 2×16 = 358
//   REF_MIN_CANVAS_H       600
//   REF_RENDER_MIN_HEIGHT  600 − 2×16 = 568
//   second-pass viewport   max(scrollHeight, REF_RENDER_MIN_HEIGHT)
//
// The iOS twin asserts the IDENTICAL numbers through WPTCanvas.icbExtent
// (WPTCaptureModeTests), so the two natives cannot drift.

import org.junit.Assert.assertEquals
import org.junit.Test

class WptComposedGeometryTest {

    @Test
    fun canvasFloorIsTheRefMinCanvasHeight() {
        // capture-browser-ref.mjs REF_MIN_CANVAS_H — the PADDED png floor.
        assertEquals(600f, COMPOSED_CANVAS_MIN_HEIGHT_DP, 0f)
    }

    @Test
    fun shortDocumentsStillFloorAtTheHistoricalIcbHeight() {
        // The wave-25 behaviour, preserved exactly: anything at or under the
        // 600 outer floor resolves to the constant 568 ICB height, so every
        // committed composed capture whose content fits the floor is
        // byte-identical after residual 1.
        assertEquals(568f, composedIcbHeightDp(600f), 0f)
        assertEquals(568f, composedIcbHeightDp(420f), 0f)
        // A degenerate mid-layout measurement must not produce a stub ICB.
        assertEquals(568f, composedIcbHeightDp(0f), 0f)
    }

    @Test
    fun tallDocumentsGrowTheIcbLikeTheRef() {
        // THE RESIDUAL-1 PIN. The ref's second viewport is
        // max(scrollHeight, 568) and the frame is added to the raster
        // afterwards, so a 1200-tall composed canvas (frame + 1168 of flow)
        // anchors `bottom: 0` at 1168 from the ICB top — NOT at the 568 the
        // wave-25 constant hard-coded, which is what put a bottom-anchored
        // hoisted box 600dp too high on tall Android captures.
        assertEquals(1168f, composedIcbHeightDp(1200f), 0f)
        // One dp past the floor already tracks the content.
        assertEquals(569f, composedIcbHeightDp(601f), 0f)
    }

    @Test
    fun canvasHeightIsTheRefTwoPassRule() {
        // The floor half, isolated: max(measured, floor) — never min().
        assertEquals(600f, composedCanvasHeightDp(0f), 0f)
        assertEquals(600f, composedCanvasHeightDp(600f), 0f)
        assertEquals(1200f, composedCanvasHeightDp(1200f), 0f)
        // An explicit floor is honoured so the pin can show the arithmetic.
        assertEquals(300f, composedCanvasHeightDp(300f, floorDp = 100f), 0f)
    }

    @Test
    fun icbHeightAndIcbExtentAgreeOnWhatFramingMeans() {
        // The height helper must be the SHARED extent helper with the floor
        // applied first — if these two ever diverge, a `right: 0` and a
        // `bottom: 0` box would frame differently on the same canvas.
        assertEquals(
            composedIcbExtentDp(composedCanvasHeightDp(1200f)),
            composedIcbHeightDp(1200f),
            0f,
        )
    }

    @Test
    fun publishedViewportIsTheRefRenderViewport() {
        // THE RESIDUAL-2 PIN. 358 wide (REF_RENDER_WIDTH), NOT the 390-wide
        // framed image the canvases used to publish: Chromium lays the ref
        // page out at 358 and the 16px frame is memcpy'd onto the finished
        // PNG, where no `vw` and no media query can observe it.
        val short = composedViewportFor(canvasWidthDp = 390f, measuredCanvasHeightDp = 600f)
        assertEquals(358f, short.widthPx, 0f)
        assertEquals(568f, short.heightPx, 0f)
        // The height half tracks the document exactly like the ICB does.
        val tall = composedViewportFor(canvasWidthDp = 390f, measuredCanvasHeightDp = 1200f)
        assertEquals(358f, tall.widthPx, 0f)
        assertEquals(1168f, tall.heightPx, 0f)
    }

    @Test
    fun publishedViewportTracksAnOverriddenCaptureWidth() {
        // CAPTURE_WIDTH widens the canvas (docs/DYNAMIC_CAPTURE.md §2); the
        // frame is per-side and constant, so the published basis follows —
        // a two-width media run must compare against the same number the
        // ref would render at.
        assertEquals(
            468f,
            composedViewportFor(canvasWidthDp = 500f, measuredCanvasHeightDp = 600f).widthPx,
            0f,
        )
    }

    @Test
    fun publishedViewportIsNeverTheFramedImage() {
        // Dark-stage guard in pin form: whatever the canvas measures, the
        // published basis must NEVER equal the outer 390×(600|content)
        // image — that equality was the wave-25 bug, and it is the only
        // shape that could re-base a fixture capture's vw/vh by 32px.
        val vp = composedViewportFor(canvasWidthDp = 390f, measuredCanvasHeightDp = 900f)
        assertEquals(390f - 2f * WPT_CANVAS_FRAME_DP, vp.widthPx, 0f)
        assertEquals(900f - 2f * WPT_CANVAS_FRAME_DP, vp.heightPx, 0f)
    }
}
