package com.styleconverter.runtime.core.renderer

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
// Source-over blend used by the wave-15 composed-canvas background rule below.
import androidx.compose.ui.graphics.compositeOver

/**
 * Ambient WPT-capture-mode flag for the Compose runtime.
 *
 * ## Why this exists
 * When a component carries styling but NO real text, the renderer paints the
 * component's *name* (underscore-stripped) as a synthesized placeholder so a
 * normal capture isn't a blank box (see `PlaceholderContent`). That is the
 * desired product/baseline behaviour and the 327-pair baseline captures depend
 * on it.
 *
 * In a WPT reftest, though, the Chromium browser-reference shows NO such name
 * text — an empty `background-color` test box is just a coloured rectangle. The
 * WEB harness already handles this: its `PlaceholderContent` renders the name
 * label in normal capture but SUPPRESSES it in WPT capture mode (the `?wpt=1` /
 * WPT_MODE path), keeping web WPT captures clean. This flag is the Compose twin
 * of that switch so native WPT captures match the browser-ref instead of
 * painting spurious "background color rgb 001" text over otherwise-empty boxes.
 *
 * ## Contract
 * - Default is `false` — meaning normal product/baseline behaviour is
 *   completely unchanged (placeholders render exactly as before). Only the
 *   harness inbox/WPT capture path flips it on
 *   (ScreenshotCaptureScreen wraps the render in
 *   `CompositionLocalProvider(LocalWptCaptureMode provides true)` when
 *   `inboxMode` is active — inbox capture IS WPT capture).
 * - When `true`, ONLY the SYNTHESIZED component-name placeholder is suppressed.
 *   REAL text content (the IR `_text`/rawText leading-text channel and genuine
 *   text nodes) must still render — see [shouldSuppressSynthesizedName].
 *
 * Follows the existing CompositionLocal pattern in this runtime
 * (`LocalCssVariables` in core/variables/CssVariableConfig.kt,
 * `LocalAccentColor` in color/AccentApplier.kt).
 */
val LocalWptCaptureMode = compositionLocalOf { false }

/**
 * Pure decision helper for the placeholder-suppression gate — extracted from
 * the `@Composable` `PlaceholderContent` so the exact rule is unit-testable
 * without a Compose/Robolectric runtime.
 *
 * Suppress the placeholder ONLY when BOTH hold:
 *   1. we are in WPT capture mode ([wptCaptureMode] `== true`), AND
 *   2. there is NO real text to show — [rawText] is null or empty, which is
 *      precisely the case where `PlaceholderContent` would otherwise fall back
 *      to painting the synthesized component *name*.
 *
 * When [rawText] is non-empty the IR carried real leading text (the `_text`
 * channel); that must always render, so this returns `false` even in WPT mode.
 * In default mode ([wptCaptureMode] `== false`) this always returns `false`, so
 * the baseline placeholder behaviour is untouched.
 *
 * @param wptCaptureMode value of [LocalWptCaptureMode] at the call site.
 * @param rawText the verbatim IR `_text` override passed to `PlaceholderContent`
 *   (null/empty ⇒ the synthesized-name branch).
 * @return true iff the synthesized-name placeholder should render nothing.
 */
fun shouldSuppressSynthesizedName(wptCaptureMode: Boolean, rawText: String?): Boolean =
    // `isNullOrEmpty()` is the SAME real-text test `PlaceholderContent` uses to
    // compute `hasRawText`, so the gate and the downstream branch never diverge.
    wptCaptureMode && rawText.isNullOrEmpty()

/**
 * Ambient COMPOSED-WPT-capture flag (TITAN Round 4b — the native twin of the
 * web harness's `?wptComposed=1` / WPT_COMPOSED_MODE).
 *
 * ## Why a SEPARATE flag from [LocalWptCaptureMode]
 * [LocalWptCaptureMode] is `true` for BOTH the per-component inbox WPT path and
 * the composed path (both are "WPT capture" → both suppress the synthesized
 * name). The Round-4b text line-box calibration below, however, is specific to
 * the COMPOSED methodology — it pins the placeholder line box to the Chromium
 * browser-ref's default-font line box so a stacked multi-`<p>` document's bar
 * heights + baselines line up with the ref (see [composedDefaultLineHeightPx]).
 * The web fix scopes the identical change to `?wptComposed=1` ONLY, leaving the
 * per-component `?wpt=1` path untouched. To keep the three platforms aligned we
 * mirror that scoping exactly: this flag is provided `true` ONLY by the composed
 * capture path (ScreenshotCaptureScreen's ComposedCaptureView), so:
 *   - the BUNDLED 327-pair baseline (inboxMode=false) → default `false` → byte-
 *     identical, AND
 *   - the per-component inbox WPT path (inboxMode=true, composedMode=false) →
 *     default `false` → also byte-identical (matching web's composed-only gate).
 */
val LocalWptComposedMode = compositionLocalOf { false }

/**
 * The ref's default line-box ratio — recalibrated at the corpus-v4.1
 * LINE-HEIGHT sub-boundary. The Round-4b value was 1.125 (18px @16px):
 * back then capture-browser-ref.mjs forced NO font and Chromium's default
 * SERIF `line-height: normal` box was ~18px at 16px, while the harness
 * Inter `normal` box was ~2px taller — pinning 1.125 removed the per-bar
 * compounding drift down stacked multi-`<p>` tests. From corpus-v4.1 the
 * ref pins the harness Inter face AND an explicit deterministic
 * `line-height: 1.25` (capture-browser-ref.mjs REF_LINE_HEIGHT — 20px
 * @16px, Chromium's measured natural Inter rhythm: default ref paragraphs
 * advance 36px top-to-top), so the OLD 18px calibration became the drift
 * (captures advanced 34px — 2px re-accumulating per paragraph, the whole
 * css-color/css-break/css-flexbox collapse of the first v4.1 run). This
 * ratio now mirrors the ref pin exactly: 1.25 → 20px @16px, scaling with
 * font-size like the ref's inherited unitless number. Web pins the same
 * value (index.html wpt rules + ComponentRenderer '1.25'), iOS the same
 * box (wptRefLineBoxPx 20). Composed WPT capture only — see the callers.
 */
const val REF_DEFAULT_FONT_LINE_HEIGHT_RATIO: Float = 1.25f // 20 / 16

/** The historical native default line-box ratio (see PlaceholderContent) —
 *  used for every non-composed path so those captures stay byte-identical. */
const val NATIVE_DEFAULT_LINE_HEIGHT_RATIO: Float = 1.2f

/**
 * The DEFAULT placeholder line-height (px) to use when the IR declared none.
 * Pure decision extracted for the JVM unit suite (the `@Composable`
 * PlaceholderContent calls this only for the missing-`line-height` branch; an
 * IR-declared line-height is honoured verbatim upstream and never reaches here).
 *
 * @param composedWpt value of [LocalWptComposedMode] at the call site.
 * @param fontSizePx the placeholder's effective font size in px.
 * @return `fontSizePx × 1.25` in composed WPT capture (the corpus-v4.1
 *   pinned ref line box — 20px @16px), else `fontSizePx × 1.2` (the
 *   historical native default box, byte-identical for the 327 baselines).
 */
fun composedDefaultLineHeightPx(composedWpt: Boolean, fontSizePx: Float): Float =
    fontSizePx * (if (composedWpt) REF_DEFAULT_FONT_LINE_HEIGHT_RATIO
                  else NATIVE_DEFAULT_LINE_HEIGHT_RATIO)

/**
 * The placeholder text padding (dp). The product/baseline + per-component paths
 * keep a 4dp label breathing-room band; composed WPT capture drops it to 0 so a
 * text bar is exactly one line-box tall, matching the ref's tight `<p>` (the web
 * fix drops the same placeholder padding from 4px to 0 in `?wptComposed=1`).
 * Pure for the unit suite.
 *
 * @param composedWpt value of [LocalWptComposedMode] at the call site.
 */
fun composedPlaceholderTextPaddingDp(composedWpt: Boolean): Int = if (composedWpt) 0 else 4

/**
 * The WPT capture-canvas background — WHITE, the corpus-v4 canvas contract
 * (TITAN-WHITE lane; the FIRST white-canvas corpus snapshot is corpus-v4 and
 * its numbers are NOT comparable to the v1..v3 dark-canvas snapshots).
 *
 * ## Why white
 * WPT reftests are authored against the spec-default WHITE page: many paint
 * WHITE ink (borders/backgrounds — e.g. the abspos-autopos tests' `border:
 * solid white` frames, ~5,200px of ink over 6 tests) that is SUPPOSED to
 * vanish into the canvas, and their references paint none of it. The
 * browser-ref render (tools/titan/capture-browser-ref.mjs CANVAS_BG) is
 * white from the same boundary, so the SDUI capture must be too — on the
 * old dark #1A1A2E stage that white ink was visible ONLY on our side, a
 * systematic reftest penalty regardless of renderer correctness. All three
 * platforms flip together: web `wptCanvasStyle`/`composedCanvasStyle`,
 * this constant (consumed by the Android harness canvases), and SwiftUI's
 * `WPTCanvas.background`.
 */
val WPT_CANVAS_BACKGROUND = Color(0xFFFFFFFF)

/**
 * The COMPOSED-capture CANVAS FRAME, in dp — the native twin of the ref
 * pipeline's image-space pad (`tools/titan/capture-browser-ref.mjs`
 * CANVAS_PAD_PX). 16 on all four sides of the 390-wide canvas, so the
 * content space is 358 wide, exactly REF_RENDER_WIDTH.
 *
 * ## Why this is now a CONSTANT and not "the body padding"
 * Through wave 24 the composed canvases treated the 16px inset AS the
 * ref's injected `:where(body) { padding: 16px }`, which meant a ref
 * declaring its own `body { padding: 0 }` beat the injection and the canvas
 * had to drop the inset with it (wave-24 B-RC5, clip-path-circle-007).
 *
 * At wave-25 CAL-RC1 the ref pipeline moved that pad OUT of CSS: the page
 * renders at 358 wide with `:where(html, body) { padding: 0 }` and the 16px
 * frame is memcpy'd around the finished PNG. An image-space translation has
 * no cascade and no containing-block semantics, so under the new contract:
 *   * EVERY ref gets the frame — an author `body { padding }` can no longer
 *     remove it, only ADD its own inset inside it;
 *   * in-flow AND out-of-flow content translate by the SAME (+16, +16),
 *     which is why the canvas-root hoist origin moves here from (0,0);
 *   * the ref's initial containing block is the 358-wide render viewport,
 *     NOT the 390-wide image.
 * Hence: frame = this constant, unconditional; author body padding = an
 * ADDITIONAL inset resolved per side with a ZERO default (the harnesses'
 * `resolveComposedCanvasPadding` / `resolvedPadding` / `resolveCanvasPadding`
 * now return frame + declared). All three platforms pin the same number —
 * SwiftUI `WPTCanvas.canvasFramePx`, web `CANVAS_FRAME_PX`.
 */
const val WPT_CANVAS_FRAME_DP: Float = 16f

/**
 * The composed canvas's INITIAL CONTAINING BLOCK extent on one axis, in dp:
 * the outer canvas extent minus the frame on BOTH sides.
 *
 * This is the number css-position-3 §3.1/§3.2 anchors out-of-flow boxes
 * against — the ref's render VIEWPORT (358 × 568 at the 390 × 600 defaults),
 * not the framed image. It matters for `right`/`bottom`-only insets: a
 * `right: 0` hoisted box must land flush with the CONTENT edge (image x =
 * 16 + 358 = 374), leaving the frame visible, exactly as the ref's raster
 * does. Feeding the outer 390 with the shifted origin would push it to 406
 * — 16px PAST the canvas.
 *
 * Pure so the harness's canvas geometry is pinnable on the JVM (same
 * extracted-decision style as [captureCanvasBackground]); clamped at zero so
 * a pathologically narrow capture-width override can never produce a
 * negative containing block.
 *
 * @param canvasExtentDp the outer capture-canvas extent (390 wide / the
 *   600 min-height floor at the defaults).
 * @param frameDp the image-space frame per side — [WPT_CANVAS_FRAME_DP].
 */
fun composedIcbExtentDp(canvasExtentDp: Float, frameDp: Float = WPT_CANVAS_FRAME_DP): Float =
    // Two sides of frame come out of the outer extent; never below zero.
    maxOf(0f, canvasExtentDp - 2f * frameDp)

/**
 * Pure canvas-background decision for the Android capture canvases —
 * extracted (same pattern as [shouldSuppressSynthesizedName]) so the exact
 * mode split is unit-testable on the JVM without a Compose runtime.
 *
 * WPT capture mode ([wptCaptureMode] true — the TITAN inbox/composed paths)
 * paints the corpus-v4 WHITE canvas; every other path returns
 * [defaultBackground] VERBATIM so the bundled 327-pair baseline stage
 * (#1A1A2E, owned by the harness) stays byte-identical — the split is the
 * whole contract, there is no third state.
 *
 * @param wptCaptureMode value of [LocalWptCaptureMode] at the call site.
 * @param defaultBackground the caller's non-WPT stage color (the harness's
 *   `CaptureCanvasBg` #1A1A2E for the property-fixture pipeline).
 */
fun captureCanvasBackground(wptCaptureMode: Boolean, defaultBackground: Color): Color =
    if (wptCaptureMode) WPT_CANVAS_BACKGROUND else defaultBackground

/**
 * Wave 15 (NATIVES-ALPHA) — the COMPOSED-canvas background rule: a body-root's
 * resolved background is ALPHA-COMPOSITED over the white WPT canvas, never
 * painted verbatim.
 *
 * ## Why composite
 * The browser-ref paints the page canvas by CSS compositing: a
 * `body { background: rgba(...) }` with alpha < 1 blends source-over onto the
 * white `:where(html,body)` canvas (css-color-4 transparency), and the web
 * harness gets the identical result for free because its body-root div sits ON
 * the white page. The composed native canvases instead painted the RESOLVED
 * rgba VERBATIM as the surface color — so
 * `background-color-transparent-animation-in-body`'s body background (the
 * wave-14 keyframes sampler's CORRECTLY baked `rgba(0,0,0,0)`) flattened the
 * whole capture toward BLACK on the opaque PNG instead of vanishing into
 * white (iOS scored 0.000; this is the Compose twin of
 * `WPTCanvas.composedBackground` so both natives blend identically).
 *
 * ## Contract
 * - `null` (no body-root, or it declares no background) → the corpus-v4 WHITE
 *   canvas [WPT_CANVAS_BACKGROUND] — the fallback the harness resolver
 *   previously inlined, unchanged.
 * - Fully OPAQUE (alpha >= 1) → the caller's color VERBATIM: source-over with
 *   α=1 is the identity, and skipping the blend keeps opaque body backgrounds
 *   (a98rgb-003's grey) bit-identical to their wave-14 rendering.
 * - Translucent → Compose's [compositeOver] source-over blend onto the white
 *   canvas (per-channel c·α + white·(1−α), opaque result) — the same sRGB
 *   gamma-space math the browsers use for the page canvas.
 *
 * COMPOSED WPT ONLY: the sole consumer is the harness's
 * `resolveComposedCanvasBackground` (ScreenshotCaptureScreen.kt), so the
 * dark-stage 327-pair path and the per-component WPT path never route through
 * this — the [captureCanvasBackground] mode-split pins hold those surfaces
 * byte-identical.
 *
 * @param resolved the body-root's extracted BackgroundColor, or null.
 */
fun composedCanvasBackground(resolved: Color?): Color = when {
    // No author body background → the white canvas verbatim (the ref's
    // zero-specificity `:where(html,body){background:#fff}` wins).
    resolved == null -> WPT_CANVAS_BACKGROUND
    // Opaque → identity (see contract above — no needless re-pack).
    resolved.alpha >= 1f -> resolved
    // Translucent → source-over onto the opaque white canvas.
    else -> resolved.compositeOver(WPT_CANVAS_BACKGROUND)
}

/**
 * The WPT default TEXT INK — spec BLACK, the corpus-v4.1 ink sub-boundary
 * within the v4 white-canvas era.
 *
 * ## Why black
 * Real WPT pages paint default prose in the UA `color: CanvasText` default,
 * which is BLACK on the light-scheme white page (the html.css UA sheet).
 * Through corpus-v4.0 every surface kept the harness's near-white family
 * (web body `color:#eee`, this runtime's [ComponentRenderer.DEFAULT_TEXT_COLOR]
 * #eee bottom-out, the ref injection's old `color:#fff`) — so on the white
 * canvas default-ink text vanished on BOTH sides of the diff and every
 * prose reftest passed VACUOUSLY. From v4.1 all four surfaces flip
 * together: the ref injection (capture-browser-ref.mjs `:where(body)
 * { color:#000 }`), web (index.html wpt-mode rule + PlaceholderContent's
 * WPT_MODE ink), this constant, and SwiftUI's `WPTCanvas.textInk`.
 *
 * ## The FONT half of the same v4.1 sub-boundary (no Compose hook needed)
 * The sub-boundary also pins the default text FONT: the browser-ref now
 * injects the harness Inter stack + embedded faces
 * (capture-browser-ref.mjs REF_FONT_STACK) because visible black prose
 * exposed that the ref wrapped text in Chromium's default serif while the
 * harnesses wrap in Inter — shifting everything below the prose. Compose
 * deliberately has NO font analogue of this ink split: the runtime's
 * default text face is ALREADY the bundled Inter unconditionally
 * (typography/InterFont.kt's InterFontFamily is the bottom-out in
 * TextStyleApplier and ComponentRenderer's placeholder branches, WPT and
 * non-WPT modes alike), so the native side already matches the newly
 * pinned ref face and only ref + web carry explicit font pins.
 */
val WPT_DEFAULT_TEXT_INK = Color(0xFF000000)

/**
 * Pure default-text-ink decision — the ink twin of [captureCanvasBackground]
 * (same extracted-decision pattern, unit-pinned in WptCanvasBackgroundTest so
 * the mode split can never silently drift).
 *
 * WPT capture mode ([wptCaptureMode] true — the TITAN inbox/composed paths)
 * bottoms text ink out at the corpus-v4.1 spec BLACK; every other path
 * returns [defaultInk] VERBATIM so the dark-stage property-fixture pipeline
 * keeps its historical #eee-family defaults ([ComponentRenderer.DEFAULT_TEXT_COLOR]
 * and the placeholder contrast pick) byte-identically — the 327 committed
 * baselines depend on that side never moving.
 *
 * @param wptCaptureMode value of [LocalWptCaptureMode] at the call site.
 * @param defaultInk the caller's non-WPT bottom-out (the #eee-family stage
 *   ink for the property-fixture pipeline).
 */
fun defaultTextInk(wptCaptureMode: Boolean, defaultInk: Color): Color =
    if (wptCaptureMode) WPT_DEFAULT_TEXT_INK else defaultInk
