// apps/web-harness/capture-url.mjs
//
// Pure URL-building helper for capture-screenshots.mjs.
//
// Why is this a separate module?
// ------------------------------
// `capture-screenshots.mjs` does top-level `await puppeteer.launch(...)` so
// the entire CLI body runs as soon as the module is evaluated. Importing it
// from a unit test would therefore boot Chromium, hit whatever vite happens
// to be on :3000, and try to take 109 screenshots — which is the opposite
// of a unit test. Splitting the URL builder into its own module keeps the
// helper trivially importable under `node --test` without dragging in the
// puppeteer side-effect.
//
// The capture pipeline talks to the React app via the URL:
//   * `?mode=capture`   — chromeless capture gallery (App.tsx::isCaptureMode)
//   * `&wpt=1` (optional) — ComponentRenderer's `WPT_MODE` constant resolves
//                           true → PlaceholderContent suppresses its visible
//                           name overlay.
//
// The WPT-suppression flag is the fix for the pilot investigation at
// `tools/titan/investigations/pilot-001/
//   css-backgrounds__background-color-animation-in-body.json`. The pilot
// found that every WPT capture across the corpus carried a
// "wpt <section> <stem> <idx>" text overlay (the component name with
// underscores → spaces) that was the dominant pixel-divergence cause vs
// the chromeless browser-ref. Suppressing it lets `structural-divergence`
// classifications reflect actual layout mismatches rather than the
// harness's own placeholder label.
//
// Legacy 327-pair flow (test-all.sh) does NOT pass WPT_MODE=1, so the URL
// stays exactly what every committed baseline PNG was captured against.

/**
 * Build the capture-mode URL puppeteer navigates to.
 *
 * @param {string} baseUrl - Origin URL of the running vite (e.g. "http://localhost:3000").
 *                           A trailing slash is stripped so the result has at
 *                           most one `/` between origin and `?` (matches the
 *                           legacy URL shape captured into baselines).
 * @param {boolean} wptMode - When true, appends `&wpt=1` so the React app
 *                            suppresses placeholder text. When false, the URL
 *                            stays at the legacy `?mode=capture` form.
 * @param {object} [opts] - Dynamic-styling capture hooks (docs/DYNAMIC_CAPTURE.md).
 *                          Omitted / defaulted ⇒ the URL is byte-identical to
 *                          the legacy two-argument form — committed baselines
 *                          were all captured against that exact string.
 * @param {number} [opts.width] - Render-surface width override in px
 *                          (`CAPTURE_WIDTH` env). Appended as `&width=<px>`
 *                          ONLY when it differs from the 390px default, so
 *                          the default run's URL (and captures) never drift.
 * @param {string} [opts.forceState] - Forced interaction state
 *                          (`CAPTURE_FORCE_STATE` env): one of
 *                          hover|active|focus|disabled|checked
 *                          (schema/spec/06-dynamic-styling.md §6). Appended
 *                          as `&forceState=<state>` when set.
 * @param {number} [opts.animationTime] - Deterministic animation-time seize
 *                          (`CAPTURE_ANIMATION_TIME` env, seconds —
 *                          schema/spec/07-animations.md §5 /
 *                          docs/DYNAMIC_CAPTURE.md §4): every animation on
 *                          the capture surface is forced to its state at
 *                          absolute time t and paused. Appended as
 *                          `&animationTime=<seconds>` when set (0 is a
 *                          meaningful value — the initial frame — so the
 *                          guard is `!= null`, not truthiness).
 * @param {boolean} [opts.wptComposed] - WPT COMPOSED capture mode
 *                          (`WPT_COMPOSED` env). When true, appends
 *                          `&wptComposed=1` so App.tsx renders the
 *                          ComposedCaptureGallery: components are grouped
 *                          per WPT test and rendered COMPOSED on ONE canvas
 *                          each (framed like the Chromium browser-ref), so
 *                          the diff is one composed PNG vs the ref — no
 *                          post-hoc vertical stitch. Independent of `wpt=1`
 *                          (which stays on for placeholder suppression), so
 *                          both suffixes ride the URL together. Omitted /
 *                          false ⇒ the byte-identical legacy per-component
 *                          gallery.
 * @returns {string} The fully formed URL to navigate to.
 */
export function buildCaptureUrl(baseUrl, wptMode, opts = {}) {
  // Strip a single trailing slash. URL stays canonical regardless of how
  // the caller chose to format the origin (`http://x:3000` vs `http://x:3000/`).
  const trimmed = baseUrl.replace(/\/$/, '');
  // `&wpt=1` only — `?` is already in the URL via `?mode=capture`. Order
  // doesn't matter to URLSearchParams on the React side, but keeping
  // `mode=capture` first preserves byte-identity with all pre-fix URLs in
  // logs and historical capture artifacts.
  const wptSuffix = wptMode ? '&wpt=1' : '';
  // `&wptComposed=1` — the composed WPT capture path. Emitted right after
  // `wpt=1` so the two WPT signals stay adjacent in logs; guarded on the
  // opt so a non-composed run's URL is byte-identical to the legacy form.
  const composedSuffix = opts.wptComposed ? '&wptComposed=1' : '';
  // Dynamic-styling hooks, in fixed order (width, then forceState, then
  // animationTime) so the URL stays deterministic for logs / artifact
  // matching. The `!== 390` guard keeps the default path byte-identical
  // to the legacy form.
  const widthSuffix = opts.width && opts.width !== 390 ? `&width=${opts.width}` : '';
  const stateSuffix = opts.forceState ? `&forceState=${opts.forceState}` : '';
  // `0` is legal (freeze at the initial frame), so test presence, not truth.
  const timeSuffix = opts.animationTime != null ? `&animationTime=${opts.animationTime}` : '';
  return `${trimmed}/?mode=capture${wptSuffix}${composedSuffix}${widthSuffix}${stateSuffix}${timeSuffix}`;
}
