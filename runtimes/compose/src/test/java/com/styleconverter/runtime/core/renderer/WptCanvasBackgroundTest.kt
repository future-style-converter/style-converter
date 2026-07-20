package com.styleconverter.runtime.core.renderer

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit pins for the pure WPT capture-canvas background decision
 * (WptCaptureMode.kt, TITAN-WHITE lane): [WPT_CANVAS_BACKGROUND] and
 * [captureCanvasBackground].
 *
 * THE CONTRACT: from corpus-v4 the WPT capture canvas is WHITE — WPT
 * reftests are authored against the spec-default white page, so white ink
 * (borders/backgrounds) must vanish into the canvas exactly as it does in
 * the white Chromium browser-ref (capture-browser-ref.mjs CANVAS_BG) and
 * the web/iOS WPT canvases. Every NON-WPT path must keep the caller's
 * stage color VERBATIM so the committed 327-pair baselines stay
 * byte-identical. The harness wiring (which call site passes which flag)
 * is separately pinned by tools/titan/wpt-white-canvas.test.mjs — these
 * tests hold the decision's semantics.
 */
class WptCanvasBackgroundTest {

    /** The harness's historical dark stage — the exact #1A1A2E the 327-pair
     *  baselines were captured on (apps/android-harness CaptureCanvasBg). */
    private val darkStage = Color(0xFF1A1A2E)

    @Test
    fun wptCanvasBackground_isOpaqueWhite() {
        // The corpus-v4 canvas is fully-opaque white — no alpha compositing
        // on the capture surface (same rule the dark stage always had).
        assertEquals(Color(0xFFFFFFFF), WPT_CANVAS_BACKGROUND)
    }

    @Test
    fun wptMode_paintsTheWhiteCanvas() {
        // WPT capture mode (TITAN inbox/composed) → the corpus-v4 white,
        // regardless of what stage color the caller would use otherwise.
        assertEquals(
            WPT_CANVAS_BACKGROUND,
            captureCanvasBackground(wptCaptureMode = true, defaultBackground = darkStage)
        )
    }

    @Test
    fun nonWptMode_returnsTheCallerStageVerbatim() {
        // The bundled/baseline path must be byte-identical: whatever stage
        // the harness owns comes back untouched (the 327 baselines depend
        // on this being the dark #1A1A2E stage).
        assertEquals(
            darkStage,
            captureCanvasBackground(wptCaptureMode = false, defaultBackground = darkStage)
        )
        // And the split is real: the two modes never agree on the dark stage.
        assertNotEquals(
            captureCanvasBackground(wptCaptureMode = true, defaultBackground = darkStage),
            captureCanvasBackground(wptCaptureMode = false, defaultBackground = darkStage)
        )
    }

    // ── wave 15 (NATIVES-ALPHA): composed-canvas alpha compositing ──────────
    //
    // The composed canvas paints a body-root's resolved background COMPOSITED
    // over the white WPT canvas, never verbatim: the browser-ref blends a
    // translucent `body{background}` source-over onto its white page (and web
    // composites the same way for free), so a verbatim rgba(0,0,0,0) surface
    // — the wave-14 sampler's CORRECTLY baked value on
    // background-color-transparent-animation-in-body — darkened the opaque
    // capture PNG toward black (iOS scored 0.000; Android had the identical
    // verbatim bug). These pins hold [composedCanvasBackground], the pure
    // rule the harness resolver routes through (the twin of iOS
    // WPTCanvas.composedBackground in WPTCaptureModeTests).

    @Test
    fun composedCanvas_transparentBodyComposesToWhite() {
        // The 0.000 reproduction: rgba(0,0,0,0) source-over white IS white —
        // fully-transparent ink must vanish into the canvas, exactly as the
        // browser-ref renders the transparent-animation-in-body page.
        assertEquals(
            WPT_CANVAS_BACKGROUND,
            composedCanvasBackground(Color(red = 0f, green = 0f, blue = 0f, alpha = 0f))
        )
    }

    @Test
    fun composedCanvas_halfAlphaRedIsPinkOverWhite() {
        // The partial-alpha arithmetic: rgba(1,0,0,0.5) over white is the CSS
        // source-over blend 0.5·src + 0.5·white per channel — opaque pink
        // (1, 0.5, 0.5). Pins the math, not just the degenerate endpoints.
        val out = composedCanvasBackground(Color(red = 1f, green = 0f, blue = 0f, alpha = 0.5f))
        // Channel-wise with a one-quantum tolerance: sRGB Compose Colors pack
        // channels to 8 bits, so the stored 0.5f alpha reads back as 128/255
        // (≈0.50196) and the blend lands one quantum off exact 0.5 — a
        // packing artifact, invisible at capture-PNG precision (±1/255).
        val q = 1f / 255f
        assertEquals(1.0f, out.red, q)   // 1·α + 1·(1−α) = 1 for any α.
        assertEquals(0.5f, out.green, q) // 0·α + 1·(1−α) ≈ 0.5 at α≈0.5.
        assertEquals(0.5f, out.blue, q)  // Same blend as green.
        assertEquals(1.0f, out.alpha, q) // Source-over onto opaque → opaque.
    }

    @Test
    fun composedCanvas_nullFallsBackToTheWhiteCanvas() {
        // No body-root / no declared background → the corpus-v4 white canvas
        // verbatim — the exact fallback the harness resolver always had, so
        // no-body documents render byte-identically across the wave-15 fix.
        assertEquals(WPT_CANVAS_BACKGROUND, composedCanvasBackground(null))
    }

    @Test
    fun composedCanvas_opaqueBodyReturnsVerbatim() {
        // A fully-opaque body background (a98rgb-003's grey) takes the α=1
        // identity path — bit-identical to its wave-14 rendering, no re-pack.
        val grey = Color(red = 0.4f, green = 0.4f, blue = 0.4f, alpha = 1f)
        assertEquals(grey, composedCanvasBackground(grey))
    }

    // ── corpus-v4.1: the BLACK default-ink sub-boundary ─────────────────────
    //
    // Within the v4 white-canvas era the WPT default TEXT INK flipped from
    // the harness near-white family to the spec BLACK (the UA CanvasText
    // default a real WPT page bottoms out at). Through v4.0 both sides of
    // the diff hid default-ink prose on the white canvas (ref injection
    // `color:#fff`, runtime #eee bottom-outs) — vacuous passes. These pins
    // hold the ink twin of the canvas split above.

    /** The dark-stage default ink — the opaque #eee stage contract the
     *  327-pair baselines were captured with (ComponentRenderer.DEFAULT_TEXT_COLOR). */
    private val stageInk = Color(0xFFEEEEEE)

    @Test
    fun wptDefaultTextInk_isOpaqueBlack() {
        // The corpus-v4.1 ink is fully-opaque BLACK — the light-scheme UA
        // CanvasText value the ref injection now pins (`color:#000`).
        assertEquals(Color(0xFF000000), WPT_DEFAULT_TEXT_INK)
    }

    @Test
    fun wptMode_bottomsTextInkOutAtSpecBlack() {
        // WPT capture mode → the spec black, regardless of the caller's
        // stage default (matching the ref's injected :where(body) black).
        assertEquals(
            WPT_DEFAULT_TEXT_INK,
            defaultTextInk(wptCaptureMode = true, defaultInk = stageInk)
        )
    }

    @Test
    fun nonWptMode_returnsTheCallerInkVerbatim() {
        // The dark-stage property-fixture path keeps its #eee-family ink
        // byte-identically — the flip is WPT-mode-only by contract.
        assertEquals(
            stageInk,
            defaultTextInk(wptCaptureMode = false, defaultInk = stageInk)
        )
        // And the ink split is real: the two modes never agree.
        assertNotEquals(
            defaultTextInk(wptCaptureMode = true, defaultInk = stageInk),
            defaultTextInk(wptCaptureMode = false, defaultInk = stageInk)
        )
    }
}
