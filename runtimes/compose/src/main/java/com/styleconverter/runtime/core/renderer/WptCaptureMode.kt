package com.styleconverter.runtime.core.renderer

import androidx.compose.runtime.compositionLocalOf

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
 * The ref's default-font line-box ratio at a 16px font: capture-browser-ref.mjs
 * forces NO font, so the reference `<p>` uses Chromium's default UA font whose
 * `line-height: normal` box is ~18px at 16px (18/16 = 1.125). The harness forces
 * the Inter face (for iOS/Android/web parity) whose `normal` box is ~1.2× (19.2px
 * @16px) — ~1px taller per line. Flush bars hid that; once UA margins spread the
 * bars out the per-bar error COMPOUNDS down a 10-bar test and drifts every bar
 * off its ref position, collapsing the (edge-phase-sensitive) SSIM. Pinning the
 * DEFAULT line box to this ratio in composed WPT capture removes the drift.
 */
const val REF_DEFAULT_FONT_LINE_HEIGHT_RATIO: Float = 1.125f // 18 / 16

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
 * @return `fontSizePx × 1.125` in composed WPT capture (the ref default-font
 *   line box), else `fontSizePx × 1.2` (the historical native default box).
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
