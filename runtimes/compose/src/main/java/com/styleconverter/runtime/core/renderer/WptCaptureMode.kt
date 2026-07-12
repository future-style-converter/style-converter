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
