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
}
