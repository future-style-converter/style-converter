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
// `testing/titan/investigations/pilot-001/
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
 * @returns {string} The fully formed URL to navigate to.
 */
export function buildCaptureUrl(baseUrl, wptMode) {
  // Strip a single trailing slash. URL stays canonical regardless of how
  // the caller chose to format the origin (`http://x:3000` vs `http://x:3000/`).
  const trimmed = baseUrl.replace(/\/$/, '');
  // `&wpt=1` only — `?` is already in the URL via `?mode=capture`. Order
  // doesn't matter to URLSearchParams on the React side, but keeping
  // `mode=capture` first preserves byte-identity with all pre-fix URLs in
  // logs and historical capture artifacts.
  const wptSuffix = wptMode ? '&wpt=1' : '';
  return `${trimmed}/?mode=capture${wptSuffix}`;
}
