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
