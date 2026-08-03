package com.styleconverter.runtime.core.renderer

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    // ── wave 27 A-RC1: the CONTAINMENT gate on canvas propagation ───────────
    //
    // The propagation the composed canvas implements is NOT unconditional.
    // css-backgrounds-3 §2.11.2 propagates the root/body background to the
    // canvas only while that element is ON the propagation path, and
    // css-contain-2 §3.5 takes it off that path as soon as it has ANY
    // containment — a contained body paints its background on its OWN box and
    // the canvas keeps the UA default.
    //
    // MEASURED (wave-27 gate, css-contain): contain-body-bg-001..004 declare
    // `body { background: red; contain: layout|paint|size|style }` over a
    // white 300×200 `<p>` and share one PURE WHITE reference ("Test passes if
    // there is no red"). All four scored 0.6204 on Android — the whole capture
    // flooded red. These pins hold the shared rule (web
    // `bodyRootHasContainment`, iOS `WPTCanvas.containmentBlocksPropagation`).

    @Test
    fun containmentGate_absentOrNoneIsNotContainment() {
        // No declaration at all — the overwhelmingly common case, and the one
        // that keeps every pre-wave-27 capture byte-identical.
        assertFalse(containmentBlocksCanvasPropagation(null))
        // An empty list is a malformed leaf, not evidence of containment.
        assertFalse(containmentBlocksCanvasPropagation(emptyList()))
        // css-contain-2 §2: `none` is the initial value and applies NO
        // containment, so the body stays on the propagation path.
        assertFalse(containmentBlocksCanvasPropagation(listOf("NONE")))
    }

    @Test
    fun containmentGate_anyKeywordBlocksRegardlessOfKind() {
        // contain-body-bg-001..004 set layout / paint / size / style and ALL
        // match the same all-white reference, so the gate cannot grade by
        // kind; strict/content are the shorthand keywords of the same family.
        for (kw in listOf("LAYOUT", "PAINT", "SIZE", "STYLE", "STRICT", "CONTENT")) {
            assertTrue(kw, containmentBlocksCanvasPropagation(listOf(kw)))
        }
        // A multi-keyword list (`contain: size style`) needs only one token.
        assertTrue(containmentBlocksCanvasPropagation(listOf("SIZE", "STYLE")))
        // Case/whitespace tolerance — the reader hands tokens through verbatim.
        assertTrue(containmentBlocksCanvasPropagation(listOf(" layout ")))
    }

    @Test
    fun composedCanvas_containedBodyKeepsTheWhiteCanvas() {
        // contain-body-bg-001's exact shape: an OPAQUE red body background
        // that would otherwise take the α=1 identity path straight onto the
        // canvas. The gate fires BEFORE the color is read, so the canvas
        // stays white and the body-root paints its own red box (which the
        // test's white `<p>` then covers — the ref's reading).
        val red = Color(red = 1f, green = 0f, blue = 0f, alpha = 1f)
        assertEquals(
            WPT_CANVAS_BACKGROUND,
            composedCanvasBackground(red, contained = true)
        )
        // Translucent ink is gated identically — no blend, just the canvas.
        assertEquals(
            WPT_CANVAS_BACKGROUND,
            composedCanvasBackground(
                Color(red = 1f, green = 0f, blue = 0f, alpha = 0.5f),
                contained = true
            )
        )
    }

    @Test
    fun composedCanvas_uncontainedDefaultIsUnchanged() {
        // The `contained` parameter defaults to false, so every existing
        // caller and every non-contained document keeps its wave-26 result:
        // an opaque body background still paints the whole canvas.
        val grey = Color(red = 0.4f, green = 0.4f, blue = 0.4f, alpha = 1f)
        assertEquals(grey, composedCanvasBackground(grey))
        assertEquals(grey, composedCanvasBackground(grey, contained = false))
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

    // ── wave-25 round 3: the composed canvas FRAME + ICB extents ──────────

    @Test
    fun canvasFrameIsTheRefImagePad() {
        // The ONE number all three platforms read for the ref's image-space
        // frame (capture-browser-ref.mjs CANVAS_PAD_PX = 16). It is NOT the
        // author-beatable body padding any more: since CAL-RC1 the ref
        // renders unpadded at 358 and the frame is memcpy'd around the PNG,
        // so no cascade can cancel it.
        assertEquals(16f, WPT_CANVAS_FRAME_DP, 0f)
    }

    @Test
    fun icbExtentIsTheRefRenderViewport() {
        // 390 − 2×16 = 358 = capture-browser-ref.mjs REF_RENDER_WIDTH, and
        // 600 − 2×16 = 568 = REF_RENDER_MIN_HEIGHT. This is the containing
        // block out-of-flow boxes anchor in (css-position-3 §3.1/§3.2) — the
        // ref's render VIEWPORT, not the framed 390×600 image.
        assertEquals(358f, composedIcbExtentDp(390f), 0f)
        assertEquals(568f, composedIcbExtentDp(600f), 0f)
    }

    @Test
    fun icbExtentTracksAnOverriddenCaptureWidth() {
        // CAPTURE_WIDTH can widen the canvas (docs/DYNAMIC_CAPTURE.md §2);
        // the frame is per-side and constant, so the ICB tracks it.
        assertEquals(468f, composedIcbExtentDp(500f), 0f)
        // An explicit frame is honoured (the parameter exists so the pin can
        // demonstrate the arithmetic, not so callers can invent frames).
        assertEquals(390f, composedIcbExtentDp(390f, frameDp = 0f), 0f)
    }

    @Test
    fun icbExtentNeverGoesNegative() {
        // A pathologically narrow capture-width override must not hand the
        // anchor a negative containing block (it would place end-anchored
        // boxes off the far side of the canvas). Clamped at zero instead.
        assertEquals(0f, composedIcbExtentDp(20f), 0f)
        assertEquals(0f, composedIcbExtentDp(0f), 0f)
    }
}
