#!/usr/bin/env node
//
// capture-screenshots.mjs
//
// Drives the web app in `?mode=capture` (a chromeless, paginated-free,
// single-page flat list of CaptureCanvas elements) and writes one PNG per
// component to apps/web-harness/screenshots/.
//
// Output contract (matches iOS `CaptureCanvas.swift` + Android `CaptureCanvas`):
//   - width         : exactly 390 px
//   - height        : natural (no clamping)
//   - background    : solid #1A1A2E
//   - padding       : 16 px
//   - no gallery chrome of any kind
//
// Assumes the dev server is already running. test-all.sh starts it.
//
// Usage:
//     node capture-screenshots.mjs [--url http://localhost:3000] [--out screenshots]
//

import puppeteer from 'puppeteer';
import { mkdirSync, rmSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { buildCaptureUrl } from './capture-url.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ── Args ─────────────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
const baseUrl = getArg('--url') ?? 'http://localhost:3000';
const outDir  = resolve(__dirname, getArg('--out') ?? 'screenshots');

// ── Dynamic-styling capture hooks (docs/DYNAMIC_CAPTURE.md) ─────────────────
// CAPTURE_WIDTH: render-surface width override for the media-width fixtures
// (spec 06 §4 — media queries evaluate against the capture canvas, and the
// canvas width IS the viewport width here). Default 390 keeps the committed-
// baseline path byte-identical; test-all.sh needs no changes because the env
// var flows through the shell.
const captureWidth = Number(process.env.CAPTURE_WIDTH || 390);
if (!Number.isInteger(captureWidth) || captureWidth <= 0) {
  console.error(`✗ CAPTURE_WIDTH must be a positive integer, got "${process.env.CAPTURE_WIDTH}"`);
  process.exit(2);
}
// CAPTURE_FORCE_STATE: force one interaction state for the whole run
// (spec 06 §6). Validated against the runtime-v1 condition set so a typo
// fails loudly instead of silently capturing base state.
const FORCE_STATES = ['hover', 'active', 'focus', 'disabled', 'checked'];
const forceState = process.env.CAPTURE_FORCE_STATE || undefined;
if (forceState && !FORCE_STATES.includes(forceState)) {
  console.error(`✗ CAPTURE_FORCE_STATE must be one of ${FORCE_STATES.join('|')}, got "${forceState}"`);
  process.exit(2);
}
// CAPTURE_DARK: color-scheme pin for the run (docs/DYNAMIC_CAPTURE.md §3).
// The capture scheme is ALWAYS emulated explicitly — headless Chrome
// inherits the host OS appearance (a dev machine in dark mode silently
// activated every `(prefers-color-scheme: dark)` bucket and the dark arm
// of light-dark(), violating the light-default capture contract), so
// "unset" must mean "pin light", never "inherit whatever the host says".
// CAPTURE_DARK=1 is the forced-dark run; 0/unset is the standard light run.
const captureDark = process.env.CAPTURE_DARK === '1';
if (process.env.CAPTURE_DARK && !['0', '1'].includes(process.env.CAPTURE_DARK)) {
  console.error(`✗ CAPTURE_DARK must be 0 or 1, got "${process.env.CAPTURE_DARK}"`);
  process.exit(2);
}
// CAPTURE_ANIMATION_TIME: deterministic animation seize for the run
// (schema/spec/07-animations.md §5 / docs/DYNAMIC_CAPTURE.md §4). Seconds,
// ≥ 0 (0 = the initial frame, respecting fill-mode/delay arithmetic).
// undefined when unset — the historical live-capture path stays untouched.
const animationTime = process.env.CAPTURE_ANIMATION_TIME !== undefined
  ? Number(process.env.CAPTURE_ANIMATION_TIME)
  : undefined;
if (animationTime !== undefined && (!Number.isFinite(animationTime) || animationTime < 0)) {
  console.error(`✗ CAPTURE_ANIMATION_TIME must be a non-negative number of seconds, got "${process.env.CAPTURE_ANIMATION_TIME}"`);
  process.exit(2);
}

function getArg(name) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
}

// ── Setup ────────────────────────────────────────────────────────────────────
rmSync(outDir, { recursive: true, force: true });
mkdirSync(outDir, { recursive: true });

// `headless: 'new'` uses the modern Chrome/Chromium headless rendering
// path. The legacy `headless: true` (aka 'shell') suppresses paint / rAF
// callbacks when no display is attached — that's what was causing
// `Runtime.callFunctionOn timed out` during per-element screenshots once
// the page grew beyond ~50 canvases. `'new'` keeps paint running and
// lets elementHandle.screenshot do its internal bounding-box evaluation
// reliably.
//
// `protocolTimeout` bumped from the 30s default so a slow per-element
// screenshot loop (109 captures × ~300ms each) doesn't overflow the
// single-call limit on a cold CI machine.
const browser = await puppeteer.launch({
  headless: 'new',
  protocolTimeout: 300_000,
  args: [
    '--disable-background-timer-throttling',
    '--disable-renderer-backgrounding',
    '--disable-backgrounding-occluded-windows',
    // macOS focus-steal hardening — even in `headless: 'new'`, Chrome
    // can briefly flash a dock icon when N parallel instances launch
    // within seconds (e.g. 8-way swarm). These flags suppress UI surface
    // so the laptop stays usable during a TITAN run.
    //
    // DO NOT add `--no-startup-window` here. It tells Chrome not to open
    // any window at startup, which prevents puppeteer from creating a
    // page target — `puppeteer.launch()` then hangs (no window to host
    // newPage), every `page.waitForSelector` times out at 30 s, and the
    // entire capture run reports zero screenshots. That regression broke
    // both the legacy 327-pair test-all.sh flow and the per-section TITAN
    // runner (manifest.json with 0 rows, 100% no-data classifier labels).
    // If you need stricter dock-icon suppression, prefer Chromium's
    // `LSUIElement` Info.plist trick or `headless: 'shell'` (older mode)
    // — not `--no-startup-window`.
    '--no-default-browser-check',
    '--no-first-run',
    '--disable-features=Translate,MediaRouter,OptimizationHints',
  ],
});
try {
  const page = await browser.newPage();

  // 390 px viewport at 1x scale (or the CAPTURE_WIDTH override for the
  // two-width media recipe). iOS captures at scale=1.0 and Android at
  // 160 dpi (1dp == 1px), so we match that exactly.
  await page.setViewport({ width: captureWidth, height: 844, deviceScaleFactor: 1 });

  // Pin the color scheme for the whole run (docs/DYNAMIC_CAPTURE.md §3):
  // light unless CAPTURE_DARK=1. Without an explicit emulation the page
  // follows the HOST OS appearance, making captures nondeterministic
  // across machines — the exact failure mode the light-default gate for
  // dark-mode.json exists to catch. Mirrors iOS's pinned
  // `.environment(\.colorScheme, …)` on the capture canvas.
  await page.emulateMediaFeatures([
    { name: 'prefers-color-scheme', value: captureDark ? 'dark' : 'light' },
  ]);

  // Page-error = an actual uncaught exception. Always report these.
  page.on('pageerror', (err) => console.error('[pageerror]', err.message));

  // Console errors are noisier — React dev-mode warnings show up here as
  // "error" level. Filter out the pre-existing property-naming warnings
  // that come from `ComponentRenderer.tsx` setting kebab-case style props
  // (an unrelated web-renderer issue that we don't own in this script).
  // Anything else is logged so real problems surface.
  const ignoredRE = /Unsupported style property|Failed to load resource:.*404/;
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    const text = msg.text();
    if (ignoredRE.test(text)) return;
    console.error('[console error]', text);
  });

  // WPT mode flag — when set, the capture URL gets `?wpt=1` so that
  // `ComponentRenderer.PlaceholderContent` suppresses the placeholder
  // text overlay (the pilot agent at tools/titan/investigations/pilot-001/
  // css-backgrounds__background-color-animation-in-body.json showed this
  // text was the dominant pixel-divergence cause across the WPT corpus).
  // The legacy 327-pair flow (test-all.sh) does NOT set WPT_MODE, so the
  // placeholder text continues to render — that's deliberate, since the
  // visual-test fixture relies on it for empty-container identification
  // matching the iOS / Android placeholder labels.
  const wptMode = process.env.WPT_MODE === '1';
  // WPT COMPOSED capture mode. When set, the app renders ONE canvas per WPT
  // test (all of that test's components composed on a single browser-ref-
  // framed surface) instead of one canvas per component, and we drop the
  // numeric index prefix from the filename so each capture lands at
  // `<safe(testKey)>.png` — the exact name inject-wpt-block.mjs's composed
  // path globs for (no vertical stitch needed). Independent of WPT_MODE,
  // but the TITAN driver sets both (placeholder suppression stays on).
  const wptComposed = process.env.WPT_COMPOSED === '1';
  // width/forceState ride the URL so CaptureGallery can size the canvas and
  // stamp `data-force-state` (the runtime reads it at style resolution).
  // Defaults produce the byte-identical legacy URL — see buildCaptureUrl.
  const captureUrl = buildCaptureUrl(baseUrl, wptMode, { width: captureWidth, forceState, animationTime, wptComposed });
  console.log(`→ loading ${captureUrl}${wptMode ? ' (WPT_MODE=1: placeholder text suppressed)' : ''}${wptComposed ? ' (WPT_COMPOSED=1: one composed PNG per test)' : ''}`);
  await page.goto(captureUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });

  // Wait for the CaptureGallery sentinel (emitted after the IR is loaded and
  // every canvas has been rendered). `data-capture-ready` holds the expected
  // canvas count, so we can compare against the live canvas count below.
  await page.waitForSelector('[data-capture-ready]', { timeout: 30_000 });

  const [expected, actual] = await page.evaluate(() => {
    const sentinel = document.querySelector('[data-capture-ready]');
    const expected = Number(sentinel?.getAttribute('data-capture-ready') ?? '0');
    const actual   = document.querySelectorAll('[data-capture-canvas]').length;
    return [expected, actual];
  });
  console.log(`  gallery reports ${actual} / ${expected} canvases rendered`);

  if (actual !== expected) {
    throw new Error(`Canvas count mismatch (${actual} / ${expected})`);
  }

  // Let any lingering async work (images, fonts) settle before we screenshot.
  await page.evaluate(() =>
    document.fonts?.ready ?? Promise.resolve()
  );
  // Brief post-render settle. We can't use requestAnimationFrame here —
  // headless Chrome throttles (and in some versions outright suppresses)
  // RAF callbacks when the page isn't actually being painted to a display,
  // which makes an `await page.evaluate(() => new Promise(r => rAF(r)))`
  // hang until the protocol timeout fires. setTimeout stays on the JS
  // task queue and is not throttled the same way.
  await page.evaluate(() =>
    new Promise((r) => setTimeout(r, 50))
  );

  // Deterministic animation seize (spec 07 §5). The capture page already
  // seized on mount (CaptureGallery's `?animationTime=` effect — the
  // reference implementation); this RE-seize catches animations created
  // AFTER the initial pass (font-swap reflows, transitions started by
  // late style application) so the frame we screenshot is provably the
  // t-state. The data marker check makes a silently-degraded run (page
  // ignored the param) a hard failure, mirroring the forceState contract.
  if (animationTime !== undefined) {
    const seized = await page.evaluate((t) => {
      const w = /** @type {any} */ (window);
      if (typeof w.__seizeAnimations !== 'function') return -1; // page too old
      return w.__seizeAnimations(t);
    }, animationTime);
    if (seized === -1) {
      throw new Error('CAPTURE_ANIMATION_TIME set but the capture page exposes no __seizeAnimations hook');
    }
    const marked = await page.$$eval('[data-capture-canvas]', (els) =>
      els.every((el) => el.getAttribute('data-animation-time') !== null));
    if (!marked) {
      throw new Error('CAPTURE_ANIMATION_TIME set but canvases carry no data-animation-time marker — seize did not run');
    }
    console.log(`  animation clock seized at t=${animationTime}s (${seized} animation(s) paused)`);
  }

  // ── Capture ────────────────────────────────────────────────────────────────
  //
  // We fetch the manifest **and every bounding rect** in a single
  // `$$eval` up-front, then resize the viewport to the full page height,
  // take ONE full-page screenshot, and crop N PNGs from it with sharp.
  //
  // Why this instead of elementHandle.screenshot()?
  //   Puppeteer's per-element screenshot path internally calls
  //   ElementHandle.evaluate(...) to resolve the clip rect, and each
  //   such call has to round-trip through CDP's Runtime.callFunctionOn.
  //   When the page grows past ~50 canvases, that per-element evaluate
  //   starts timing out (`Runtime.callFunctionOn timed out`) on headless
  //   Chrome — probably paint-gated layout queries getting throttled.
  //   One page screenshot + N CPU-side sharp crops avoids every single
  //   one of those round-trips.
  const manifest = await page.$$eval('[data-capture-canvas]', (els) =>
    els.map((el) => {
      const r = el.getBoundingClientRect();
      return {
        index: Number(el.getAttribute('data-capture-index')),
        id:    el.getAttribute('data-capture-id') ?? '',
        name:  el.getAttribute('data-capture-name') ?? 'unknown',
        x:     Math.round(r.left),
        y:     Math.round(r.top + window.scrollY),  // include current scroll offset
        width: Math.round(r.width),
        height: Math.round(r.height),
      };
    })
  );

  // ── Composed WPT capture: per-element screenshots ────────────────────────
  //
  // The composed page is ONE full-height, min-600px canvas PER TEST, so it
  // grows very tall (100 tests × ≥600px ≈ 60 000 px). The single-full-page-
  // screenshot path below silently caps at Chrome's max screenshot height
  // (~16 384 px): everything past that crops to garbage (a composed
  // border-radius canvas at y≈31 000 came back as unrelated yellow pixels).
  // The per-component path masks this because its min-height-600 dark
  // padding dominates SSIM, but the composed diff is honest and exposes it.
  //
  // Fix for composed mode: screenshot each canvas element DIRECTLY
  // (elementHandle.screenshot scrolls it into view and captures its own
  // box, immune to the page-height cap). Only 100 elements here — well under
  // the per-element `Runtime.callFunctionOn` limit that made this approach
  // untenable for the 328-canvas per-component page — and we avoid a
  // per-element evaluate() by aligning handles to the manifest positionally
  // (page.$$ and $$eval share querySelectorAll DOM order). Legacy per-
  // component capture keeps the single-screenshot + sharp-crop path verbatim.
  if (wptComposed) {
    const handles = await page.$$('[data-capture-canvas]');
    if (handles.length !== manifest.length) {
      throw new Error(`composed capture: handle/manifest length mismatch (${handles.length}/${manifest.length})`);
    }
    console.log(`  capturing ${manifest.length} composed canvases via per-element screenshots → ${outDir}`);
    let captured = 0;
    let zeroDim = 0;
    for (let i = 0; i < manifest.length; i++) {
      const entry = manifest[i];
      if (entry.width <= 0 || entry.height <= 0) {
        console.warn(`  ⚠ composed canvas ${entry.index} (${entry.name}) has zero dimensions — skipping`);
        zeroDim += 1;
        continue;
      }
      // Composed filename is the sanitised test key + .png (no index prefix)
      // — exactly what inject-wpt-block.mjs's composed path globs for.
      const safe = entry.name.replace(/[^A-Za-z0-9._-]/g, '_');
      await handles[i].screenshot({ path: resolve(outDir, `${safe}.png`), type: 'png' });
      captured += 1;
    }
    if (zeroDim > 0) console.warn(`  ⚠ skipped ${zeroDim} zero-dim composed canvases`);
    console.log(`✓ captured ${captured} / ${manifest.length} composed canvases`);
  } else {
  // (top-level module code — can't `return` early, so the legacy per-
  //  component single-screenshot + sharp-crop path lives in this else block.)
  //
  // Capture gallery is a flat vertical list starting at (0,0); the page
  // height is the sum of all canvas heights (~20 000 px for 109 canvases).
  //
  // Tried `page.screenshot({ fullPage: true })` — times out on large
  // pages because Puppeteer internally stitches frame-by-frame.
  //
  // Fix: resize the viewport to exactly the document height, then take
  // a single regular (non-full-page) screenshot. Chrome renders a tall
  // viewport in one go without the stitching overhead.
  const pageHeight = await page.evaluate(() => Math.max(
    document.documentElement.scrollHeight,
    document.body?.scrollHeight ?? 0,
  ));
  await page.setViewport({ width: captureWidth, height: pageHeight, deviceScaleFactor: 1 });
  // Give layout one tick to settle into the new viewport.
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));

  console.log(`  capturing ${manifest.length} canvases via ${captureWidth}×${pageHeight} screenshot → ${outDir}`);
  const fullPng = await page.screenshot({ type: 'png' });

  // Crop with sharp — CPU-only, no more round-trips to the browser.
  //
  // Tier 10 round-46 optimisation: for the 10000-element perf bench, the
  // previous loop did `sharp(fullPng).metadata()` once + `sharp(fullPng).extract().toFile()`
  // ONCE PER COMPONENT. With the full PNG at ~390 × 400000 px, each
  // sharp(fullPng) re-runs the libvips PNG decoder. 10000 sequential
  // re-decodes drove the web capture wall to 1845s (~30 min).
  //
  // Two changes:
  //   (a) Hoist metadata() out of the loop — read once. The bounds (imgW/imgH)
  //       are constant for a given page screenshot.
  //   (b) Process extracts in parallel batches of 16. Each clone() shares
  //       the input pipeline and libvips's read-side caching; running 16
  //       concurrent crops keeps both CPU cores busy without OOMing on the
  //       huge fullPng buffer. (Tried Promise.all of all 10000 — sharp
  //       holds intermediate buffers per pipeline → ~12 GB transient
  //       memory. Batch=16 keeps it under 1 GB.)
  //
  // limitInputPixels: false bypasses sharp's default 268M-pixel guard
  // (intent: prevent OOM on adversarial input — for our trusted captures
  // it's a false positive). Passed to ALL sharp() invocations.
  const sharpMod = await import('sharp');
  const sharp = sharpMod.default;

  const baseMeta = await sharp(fullPng, { limitInputPixels: false }).metadata();
  const imgW = baseMeta.width ?? captureWidth;
  const imgH = baseMeta.height ?? pageHeight;

  // Build the per-entry crop spec up-front so the parallel batch only does
  // I/O-bound work (extract + write).
  const cropTasks = [];
  let zeroDim = 0;
  for (const entry of manifest) {
    if (entry.width <= 0 || entry.height <= 0) {
      console.warn(`  ⚠ canvas ${entry.index} (${entry.name}) has zero dimensions — skipping`);
      zeroDim += 1;
      continue;
    }
    const safe = entry.name.replace(/[^A-Za-z0-9._-]/g, '_');
    // Composed mode: the canvas name IS the WPT test key, and the diff side
    // (inject-wpt-block.mjs composed path) looks the file up by exactly
    // `<safe(testKey)>.png` — so we DROP the `<NNN>_` index prefix that the
    // per-component path uses (there it disambiguates same-named components;
    // here each name is a unique test key). Legacy per-component runs keep
    // the padded index prefix byte-for-byte.
    const filename = wptComposed ? `${safe}.png` : `${String(entry.index).padStart(3, '0')}_${safe}.png`;
    // Clamp against the actual screenshot bounds — layout can round a
    // canvas's bottom edge one pixel past the measured pageHeight (e.g. when
    // aspect-ratio produces a fractional height), which makes sharp's
    // extract_area throw "bad extract area". Treat sub-pixel overshoot as
    // benign and trim to what's actually in the screenshot.
    const left   = Math.max(0, Math.min(entry.x, imgW - 1));
    const top    = Math.max(0, Math.min(entry.y, imgH - 1));
    const width  = Math.max(1, Math.min(entry.width,  imgW - left));
    const height = Math.max(1, Math.min(entry.height, imgH - top));
    cropTasks.push({ filename, left, top, width, height });
  }

  // Concurrency cap. 16 was chosen empirically: high enough to saturate
  // a typical 8-core dev machine on libvips's per-pipeline thread pool,
  // low enough that the transient buffer set stays well under 1 GB even
  // on a 400000-px-tall input.
  const CONCURRENCY = 16;
  let captured = 0;
  for (let i = 0; i < cropTasks.length; i += CONCURRENCY) {
    const batch = cropTasks.slice(i, i + CONCURRENCY);
    await Promise.all(batch.map((t) =>
      sharp(fullPng, { limitInputPixels: false })
        .extract({ left: t.left, top: t.top, width: t.width, height: t.height })
        .toFile(resolve(outDir, t.filename))
    ));
    captured += batch.length;
  }
  if (zeroDim > 0) {
    console.warn(`  ⚠ skipped ${zeroDim} zero-dim canvases`);
  }

  console.log(`✓ captured ${captured} / ${manifest.length} canvases`);
  }   // end else (legacy per-component capture path)
} finally {
  await browser.close();
}
