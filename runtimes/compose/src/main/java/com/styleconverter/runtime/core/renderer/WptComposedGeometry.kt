package com.styleconverter.runtime.core.renderer

// Compose runtime — the COMPOSED-WPT capture canvas's GEOMETRY contract
// (wave 26, lane RES residuals 1 + 2). Split out of WptCaptureMode.kt, which
// already sits past the 300-line split threshold; that file keeps the MODE
// flags + paint contracts, this one owns "how big is the composed canvas and
// what does the runtime resolve viewport-relative values against".
//
// ## The one source of truth being mirrored
// tools/titan/capture-browser-ref.mjs renders each reference page in a
// viewport of REF_RENDER_WIDTH x max(scrollHeight, REF_RENDER_MIN_HEIGHT)
// (358 x 568-or-content) and then memcpy's a CANVAS_PAD_PX frame around the
// finished PNG. Two consequences the natives must reproduce EXACTLY:
//
//   1. the initial containing block css-position-3 §3.1/§3.2 anchors
//      out-of-flow boxes in is that RENDER VIEWPORT — width AND height. The
//      height half was a CONSTANT 568 on Compose (composedIcbExtentDp(600))
//      while iOS read its live `geo.size.height` and the ref grows with the
//      document, so a `bottom: 0` hoisted box on a document TALLER than 600
//      landed wrong on Android only. [composedIcbHeightDp] closes that.
//
//   2. `100vw` / `100vh` and the runtime-v1 media width feature resolve
//      against that same viewport — 358 wide, not the 390-wide framed image
//      and not the device screen. [composedViewportFor] is the one decision
//      both natives publish (Compose via [LocalComposedViewport], SwiftUI via
//      `styleViewport` on ComposedCaptureCanvas).
//
// ## Dark-stage 327 protection
// Nothing here is consulted outside the COMPOSED capture path:
// [LocalComposedViewport] defaults to null, and ComponentRenderer falls back
// to the LocalConfiguration screen dp it has always used when it is null. The
// per-component WPT path and the property-fixture (#1A1A2E) pipeline never
// provide it, so every committed baseline renders byte-identically.

import androidx.compose.runtime.compositionLocalOf

/**
 * The composed capture canvas's minimum OUTER height in dp — the twin of
 * capture-browser-ref.mjs's `REF_MIN_CANVAS_H` (600).
 *
 * The ref floors its PADDED png at 600 by rendering at
 * `REF_RENDER_MIN_HEIGHT` (600 − 2×pad = 568) and framing afterwards; the
 * composed canvas floors its OUTER box at the same 600 with `heightIn(min=)`
 * and carries the frame as real padding, so the two images floor together.
 * Lives here (rather than in the harness, where the literal used to sit)
 * because [composedIcbHeightDp] needs the same number — a floor spelled in
 * two places is exactly how the 568 constant drifted from the ref.
 */
const val COMPOSED_CANVAS_MIN_HEIGHT_DP: Float = 600f

/**
 * The viewport-relative resolution basis a COMPOSED capture publishes to the
 * runtime: the ref's render viewport, in dp (== CSS px in this runtime).
 *
 * Both fields are ICB extents — the framed content space, NOT the outer
 * image — because that is the box Chromium lays the ref page out in.
 *
 * @param widthPx  `100vw`, `vmin`/`vmax` width term, and the runtime-v1
 *   media `min-width`/`max-width` basis (spec 06 §4).
 * @param heightPx `100vh` and the `vmin`/`vmax` height term.
 */
data class ComposedViewport(val widthPx: Float, val heightPx: Float)

/**
 * Host→runtime channel for [ComposedViewport]. `null` (the default, and the
 * value on EVERY non-composed path) means "no host viewport" and the
 * renderer keeps its historical LocalConfiguration screen-dp basis — the
 * dark-stage identity branch described in the file header.
 *
 * Follows the existing CompositionLocal pattern in this runtime
 * (`LocalWptCaptureMode` above, `LocalCssVariables`, `LocalContainingBlock`).
 */
val LocalComposedViewport = compositionLocalOf<ComposedViewport?> { null }

/**
 * The composed canvas's OUTER extent on the block axis: the measured content
 * height, floored.
 *
 * Mirrors the ref's TWO-PASS document-height rule verbatim
 * (capture-browser-ref.mjs): lay out at the floor viewport once, read
 * `max(documentElement.scrollHeight, body.scrollHeight, floor)`, then set the
 * viewport to THAT number and screenshot — the ref never re-measures, so the
 * rule terminates in exactly one re-layout. The native callers freeze their
 * measurement the same way (first non-degenerate `onGloballyPositioned` /
 * geometry read wins), which is what makes a `100vh` document deterministic
 * instead of a measure↔publish oscillation.
 *
 * @param measuredCanvasHeightDp the composed canvas Box's laid-out OUTER
 *   height (frame + flow + frame), in dp.
 * @param floorDp the outer floor — [COMPOSED_CANVAS_MIN_HEIGHT_DP].
 */
fun composedCanvasHeightDp(
    measuredCanvasHeightDp: Float,
    floorDp: Float = COMPOSED_CANVAS_MIN_HEIGHT_DP,
): Float =
    // max(), never min(): the ref floors the document height and lets it
    // GROW; a canvas measured shorter than the floor (a mid-layout frame
    // reporting 0) must still publish the floor rather than a stub extent.
    maxOf(measuredCanvasHeightDp, floorDp)

/**
 * The composed canvas's INITIAL CONTAINING BLOCK HEIGHT in dp — residual 1.
 *
 * Wave 25 pinned this at a CONSTANT `composedIcbExtentDp(600)` = 568, which
 * is the ref's FLOOR, not the ref's height: `capture-browser-ref.mjs` sets
 * the second viewport to `max(scrollHeight, 568)`, so on any document taller
 * than the floor the ref's ICB grows with the content and a `bottom: 0`
 * hoisted box lands at the CONTENT bottom. Compose anchored such a box 568dp
 * from the canvas top instead — wrong on Android alone, since iOS's
 * FixedHoistOverlay reads its live `geo.size.height` and web's ICB div grows
 * with the page. This threads the same measured extent the CAPTURE uses.
 *
 * The 600/568 floor semantics are preserved exactly: a short document still
 * yields 568, so every wave-25 capture whose content fits the floor is
 * byte-identical.
 *
 * @param measuredCanvasHeightDp the canvas's laid-out OUTER height in dp.
 * @param floorDp the outer floor — [COMPOSED_CANVAS_MIN_HEIGHT_DP].
 * @param frameDp the image-space frame per side — [WPT_CANVAS_FRAME_DP].
 */
fun composedIcbHeightDp(
    measuredCanvasHeightDp: Float,
    floorDp: Float = COMPOSED_CANVAS_MIN_HEIGHT_DP,
    frameDp: Float = WPT_CANVAS_FRAME_DP,
): Float =
    // Floor first (ref pass 1), then subtract the frame from both sides
    // through the SHARED extent helper — so the width and height halves of
    // the ICB can never disagree about what "minus the frame" means.
    composedIcbExtentDp(composedCanvasHeightDp(measuredCanvasHeightDp, floorDp), frameDp)

/**
 * The composed capture's published [ComposedViewport] — residual 2.
 *
 * ## Why 358, not 390
 * Through wave 25 the capture canvases published the OUTER 390 as the
 * vw/media basis (Compose `LocalRenderSurfaceWidthPx`, SwiftUI
 * `StyleViewport.width`). Under the wave-25 CAL-RC1 image-pad contract the
 * ref page is not 390 wide in any sense the CSS engine can see: Chromium
 * lays it out in a 358-wide viewport and the 16px frame is added to the
 * finished raster, where no `vw` and no media query can observe it. So a ref
 * `width: 100vw` box measures 358 while the natives measured 390 — a
 * systematic 32px surplus on every viewport-relative value, and the media
 * bucket boundary sat 32px off too.
 *
 * ## Measured blast radius (wave 26, before changing anything)
 * The sampled corpus is BUCKET A only, and bucket-wpt.mjs classifies ANY
 * test carrying a viewport unit as bucket B (`RX.viewportUnit`) — so no
 * sampled test can carry a vw/vh-derived value at all. Across the 20 corpus
 * sections 215 / 20,008 test files use viewport units and exactly 2 use a
 * width media query (css-values/calc-in-media-queries-001 and -002, whose
 * `@media (min-width: calc(100px))` the runtime-v1 grammar rejects as
 * unsupported anyway, and which holds at both 358 and 390). This change
 * therefore moves ZERO sampled pixels today; it removes the trap that would
 * fire the moment bucket B is enabled, and it makes the three engines agree
 * on one number instead of two.
 *
 * @param canvasWidthDp the canvas's OUTER width (390, or a CAPTURE_WIDTH
 *   override — docs/DYNAMIC_CAPTURE.md §2).
 * @param measuredCanvasHeightDp the canvas's laid-out OUTER height in dp.
 */
fun composedViewportFor(
    canvasWidthDp: Float,
    measuredCanvasHeightDp: Float,
): ComposedViewport = ComposedViewport(
    // Width: the ref's REF_RENDER_WIDTH — outer minus the frame per side.
    widthPx = composedIcbExtentDp(canvasWidthDp),
    // Height: the ref's second-pass viewport height — floored, then framed.
    heightPx = composedIcbHeightDp(measuredCanvasHeightDp),
)
