package com.styleconverter.runtime.core.renderer

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

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
