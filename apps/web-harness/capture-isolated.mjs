// apps/web-harness/capture-isolated.mjs
//
// B-RC3 (wave 21) — the ISOLATED composed-capture path.
//
// capture-divergence.mjs flags WPT tests whose fixture pairs a
// `column-span:all` spanner with an out-of-flow (`absolute`/`fixed`) box:
// on the tall multi-canvas composed page headless Chromium paints those
// boxes ~2312px away from their (correct) layout position, poisoning the
// web capture AND the natives' web-oracle. On a FRESH page holding a
// SINGLE canvas the same render paints correctly — verified in-browser on
// css-multicol/abspos-containing-block-outside-spanner (composed web SSIM
// 0.932; isolated page paints the green squares at their corners).
//
// This module re-captures ONE flagged test on such a page: a brand-new
// puppeteer page, the same capture URL plus `&only=<testKey>` (App.tsx →
// ComposedCaptureGallery filter), which mounts exactly one composed canvas.
// Every environment pin of the batch page (viewport, color-scheme
// emulation, font settle, animation seize) is REPLICATED here so the only
// variable between batch and isolated captures is the page composition —
// never the render environment.
//
// Split out of capture-screenshots.mjs (which is already far past the
// 200-line file budget) so the plumbing is unit-testable with a mock
// browser under `node --test` (tools/visual/capture-isolated.test.mjs) —
// the main driver's top-level `await puppeteer.launch()` makes it
// unimportable from tests.

import { resolve } from 'node:path';
// The shared URL builder — `only` rides the same query string as every
// other capture flag so the React app needs no second entry point.
import { buildCaptureUrl } from './capture-url.mjs';
// The ONE canonical compare-pipeline sanitiser (dot KEPT) — the isolated
// PNG must land at exactly the `<safe(testKey)>.png` name the batch path
// would have written and inject-wpt-block.mjs globs for.
import { safe } from '../../tools/titan/safe-name.mjs';

/**
 * Re-capture one divergence-prone WPT test on a fresh single-canvas page.
 *
 * @param {object} opts
 * @param {import('puppeteer').Browser} opts.browser - The LIVE browser the
 *        batch capture already launched — reused so the isolated page
 *        inherits the same Chromium flags (--disable-gpu CPU raster etc.)
 *        without paying a second ~2s launch per flagged test.
 * @param {string} opts.baseUrl - Origin of the running vite server.
 * @param {string} opts.outDir - Screenshot dir (same as the batch path).
 * @param {string} opts.testKey - The WPT test key to isolate — must equal
 *        the composed canvas's `data-capture-name`.
 * @param {boolean} opts.wptMode - `WPT_MODE` passthrough (placeholder
 *        suppression + wpt-mode box-sizing class stay ON, same as batch).
 * @param {number} opts.captureWidth - Viewport/canvas width (CAPTURE_WIDTH).
 * @param {string} [opts.forceState] - CAPTURE_FORCE_STATE passthrough.
 * @param {number} [opts.animationTime] - CAPTURE_ANIMATION_TIME passthrough.
 * @param {boolean} opts.captureDark - CAPTURE_DARK passthrough — the
 *        isolated page must pin the same color scheme as the batch page or
 *        a dark-run isolated capture would silently revert to light.
 * @returns {Promise<string>} Absolute path of the PNG written.
 */
export async function captureIsolatedTest({
  browser, baseUrl, outDir, testKey,
  wptMode, captureWidth, forceState, animationTime, captureDark,
}) {
  // Fresh page = fresh compositor state — the whole point of the workaround.
  const page = await browser.newPage();
  try {
    // Same 1x viewport pin as the batch page (iOS scale=1 / Android 160dpi
    // parity — see capture-screenshots.mjs). Height is nominal: composed
    // canvases are element-screenshotted, not viewport-cropped.
    await page.setViewport({ width: captureWidth, height: 844, deviceScaleFactor: 1 });
    // Same explicit color-scheme pin — "unset means pin light, never
    // inherit the host OS appearance" (the batch page's contract).
    await page.emulateMediaFeatures([
      { name: 'prefers-color-scheme', value: captureDark ? 'dark' : 'light' },
    ]);
    // Real page exceptions must surface in the capture log, same as batch.
    page.on('pageerror', (err) => console.error(`[isolated:${testKey}] pageerror`, err.message));
    // The batch URL plus `&only=` — wptComposed is unconditionally true
    // here because isolation only exists for the composed gallery.
    const url = buildCaptureUrl(baseUrl, wptMode, {
      width: captureWidth, forceState, animationTime, wptComposed: true, only: testKey,
    });
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    // The gallery sentinel fires once the (filtered) canvas list rendered.
    await page.waitForSelector('[data-capture-ready]', { timeout: 30_000 });
    // Contract check — LOUD, no silent fallthrough: the filtered page must
    // hold exactly ONE canvas. 0 ⇒ the key matched no test (driver/page key
    // drift); >1 ⇒ the app ignored `?only=` (e.g. a stale served bundle) and
    // we'd silently re-capture the divergent composed page — the exact
    // failure this module exists to prevent.
    const [expected, actual] = await page.evaluate(() => {
      const sentinel = document.querySelector('[data-capture-ready]');
      return [
        Number(sentinel?.getAttribute('data-capture-ready') ?? '0'),
        document.querySelectorAll('[data-capture-canvas]').length,
      ];
    });
    if (expected !== 1 || actual !== 1) {
      throw new Error(
        `isolated capture for "${testKey}": expected exactly 1 canvas, ` +
        `sentinel=${expected} live=${actual} — ?only= filter not honored`
      );
    }
    // Font + settle sequence copied from the batch page: fonts.ready then a
    // 50ms task-queue settle (rAF is throttled/suppressed in headless
    // Chrome, so setTimeout is the reliable settle primitive — see the
    // batch driver's comment).
    await page.evaluate(() => document.fonts?.ready ?? Promise.resolve());
    await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
    // Deterministic animation seize (spec 07 §5), same contract as batch:
    // when the run pins a time, the isolated page must be seized too or the
    // re-captured PNG would show a different animation frame than its
    // batch-captured neighbors. Marker check keeps a degraded page loud.
    if (animationTime !== undefined) {
      const seized = await page.evaluate((t) => {
        const w = /** @type {any} */ (window);
        if (typeof w.__seizeAnimations !== 'function') return -1; // page too old
        return w.__seizeAnimations(t);
      }, animationTime);
      if (seized === -1) {
        throw new Error(`isolated capture for "${testKey}": page exposes no __seizeAnimations hook`);
      }
      const marked = await page.$$eval('[data-capture-canvas]', (els) =>
        els.every((el) => el.getAttribute('data-animation-time') !== null));
      if (!marked) {
        throw new Error(`isolated capture for "${testKey}": canvas carries no data-animation-time marker`);
      }
    }
    // Identity check on the one canvas — a filter bug that mounted the
    // WRONG single test would otherwise overwrite this test's PNG with
    // another test's pixels, which no downstream stage could detect.
    const canvasName = await page.$eval('[data-capture-canvas]', (el) =>
      el.getAttribute('data-capture-name'));
    if (canvasName !== testKey) {
      throw new Error(
        `isolated capture for "${testKey}": page mounted canvas "${canvasName}" instead`
      );
    }
    // Element screenshot of the single canvas — same call shape as the
    // batch composed path, OVERWRITING the `<safe(testKey)>.png` slot so
    // the compare pipeline is completely unaware of which path produced it
    // (the capture LOG carries that provenance instead).
    const file = resolve(outDir, `${safe(testKey)}.png`);
    const handle = await page.$('[data-capture-canvas]');
    await handle.screenshot({ path: file, type: 'png' });
    return file;
  } finally {
    // Always release the page — a throw above must not leak pages across
    // the (possibly many) flagged tests of a section.
    await page.close();
  }
}
