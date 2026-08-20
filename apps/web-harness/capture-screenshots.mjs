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
// B-RC3 (wave 21) — composed-page paint-divergence guard. The detector
// flags tests whose IR pairs a `column-span:all` spanner with an
// out-of-flow (`absolute`/`fixed`) box: headless Chromium mis-paints those
// ~2312px off ON THE TALL MULTI-CANVAS COMPOSED PAGE ONLY (geometry
// correct, paint wrong — css-multicol/abspos-containing-block-outside-
// spanner, web 0.932). Flagged tests skip the batch loop and are
// re-captured on a fresh single-canvas page (capture-isolated.mjs), where
// the paint is correct. Both modules are batch-page-free, so importing
// them here costs nothing on the legacy per-component path.
import { divergenceProneTestKeys } from './capture-divergence.mjs';
import { captureIsolatedTest } from './capture-isolated.mjs';
// The ONE canonical compare-pipeline sanitiser (dot KEPT), shared with the
// TITAN feeders and inject-wpt-block.mjs so the composed/per-component PNG
// names this driver writes are globbed back identically downstream. The
// module is dependency-free, so importing it across the workspace boundary is
// safe (web-harness does not vendor pngjs).
import { safe } from '../../tools/titan/safe-name.mjs';
// wave-45 lane X6 — the Rule-43 NOTO BUNDLING PILOT (tools/titan/
// noto-pilot.mjs banner). Env-gated on TITAN_NOTO_PILOT=1: by default
// notoPilotWebCss() returns '' and NOTHING below injects, so the standard
// capture is byte-identical. With the flag on, the live page gets the same
// pilot @font-face payloads + extended font stack the pilot browser-ref
// renders under (REF_FONT_STACK is the pinned base both sides extend — the
// wpt-white-canvas.test.mjs contract), so web captures and pilot refs keep
// resolving every glyph to the SAME face. REF_FONT_STACK's home module is
// import-safe (IS_CLI-gated main; no top-level browser work).
import { notoPilotWebCss, NOTO_PILOT_FACES } from '../../tools/titan/noto-pilot.mjs';
import { REF_FONT_STACK } from '../../tools/titan/capture-browser-ref.mjs';
import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';

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
    // Force CPU rasterization. Under `headless: 'new'` on this toolchain the
    // GPU raster path DEADLOCKS `Page.captureScreenshot`: about:blank shots
    // fine, but any page with real paint hangs until protocolTimeout fires
    // (elementHandle.screenshot surfaces it as `Runtime.callFunctionOn timed
    // out`, page.screenshot as `Page.captureScreenshot timed out`). This bit
    // the WPT COMPOSED path specifically — a single wedged shot crashes the
    // whole capture, yielding "0 web screenshots". `--disable-gpu` routes
    // raster through SwiftShader-on-CPU, which returns in ~13ms and is MORE
    // deterministic across machines (no GPU-driver sub-pixel drift) — strictly
    // better for SSIM. Verified: composed css-color captures went from 20s+
    // timeout → 13ms with this flag.
    '--disable-gpu',
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

  // ── wave-45 lane X6: the NOTO-PILOT style injection ────────────────────────
  // '' (and no injection at all) unless TITAN_NOTO_PILOT=1 — the standard
  // capture path is byte-identical. Injected BEFORE the gallery sentinel
  // wait so the pilot faces/stack participate in the same settle the
  // ordinary fonts do (the document.fonts.ready wait + reflow settles
  // below); the dedicated pilot delivery gate further down then SAYS which
  // pilot families actually resolved, mirroring the @font-face gate's
  // loud-never-silent contract.
  const notoPilotCss = await notoPilotWebCss(REF_FONT_STACK, process.env, { readFile, existsSync });
  if (notoPilotCss) {
    await page.addStyleTag({ content: notoPilotCss });
    console.log(`  [noto-pilot] pilot @font-face + stack override injected (TITAN_NOTO_PILOT=1)`);
  }

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

  // ── wave-36 lane M1: the IMAGE DELIVERY gate ───────────────────────────────
  //
  // The exact twin of the @font-face gate below, for the exact same reason.
  // The wire now carries replaced-element sources (`meta.attrs.src`, routed
  // through vite's /wpt-image/ middleware), and an <img> whose fetch has not
  // finished paints NOTHING — no placeholder, no error, just a correctly
  // sized empty box. That is indistinguishable in the screenshot from "the
  // renderer failed to apply object-fit", which is precisely the measurement
  // this lane exists to make. So: await every image's decode, then SAY how
  // many never arrived.
  //
  // `decode()` is the wait, not `load`: it resolves only once the bitmap is
  // ready to paint, which is the state the screenshot actually needs, and it
  // rejects (rather than hanging) on a failed fetch. Images with no `src`
  // attribute at all are excluded — a source-less <img> is legitimately
  // 0×0 and complete, not a delivery failure.
  //
  // Non-fatal by design, like the font gate: one unreachable corpus image
  // must not take down a section's other 40 tests. Loud, then honest.
  const imageReport = await page.evaluate(async () => {
    const imgs = [...document.images].filter((im) => im.getAttribute('src'));
    await Promise.all(imgs.map((im) => im.decode().catch(() => {})));
    const broken = imgs.filter((im) => !im.complete || im.naturalWidth === 0);
    return {
      total: imgs.length,
      brokenCount: broken.length,
      // A few exemplars are enough to name the route/path that failed; the
      // full list would be thousands of identical entries on a big section.
      sample: [...new Set(broken.map((im) => im.getAttribute('src')))].slice(0, 5),
    };
  });
  if (imageReport.total > 0) {
    console.log(`  [images] ${imageReport.total - imageReport.brokenCount} / ${imageReport.total} decoded`);
    if (imageReport.brokenCount > 0) {
      console.warn(`  ⚠️  ${imageReport.brokenCount} image(s) did NOT decode — those captures paint an EMPTY box ` +
                   `(e.g. ${imageReport.sample.join(', ')})`);
    }
  }

  // ── wave-35 lane B2: the @font-face DELIVERY gate ──────────────────────────
  //
  // `fonts.ready` resolves when the font pipeline goes idle — including when
  // every declared face FAILED to fetch. That distinction is invisible in the
  // screenshot: useFontFaces.ts sets `font-display: block`, so a face that
  // never arrives falls back to Inter and paints perfectly plausible text in
  // the WRONG typeface. That is the exact failure the whole @font-face channel
  // exists to end, so the run must SAY which families actually resolved.
  //
  // The check is `document.fonts.check()` against each family the harness
  // mounted (read back off the managed <style>, so this measures what the page
  // really registered rather than what we think it should have). Non-fatal by
  // design: one unreachable corpus font must not take down a section's other
  // 40 tests, and the manifest's per-test delivery stamp is where the scoring
  // consequence lives (tools/titan/build-combined-fixture.mjs →
  // inject-wpt-block.mjs's Rule 15 gate). Loud, then honest.
  const fontReport = await page.evaluate(() => {
    const styleEl = document.getElementById('sc-wpt-font-faces');
    if (!styleEl || !styleEl.textContent) return null;
    // The mounted rules are built by useFontFaces.buildFontFaceCss, so the
    // family always appears as a quoted <string> — one regex recovers them.
    const families = [...styleEl.textContent.matchAll(/font-family:\s*"((?:[^"\\]|\\.)*)"/g)]
      .map((m) => m[1].replace(/\\(.)/g, '$1'));
    const uniq = [...new Set(families)];
    return uniq.map((f) => ({
      family: f,
      // A size is required by the shorthand grammar `check()` parses; 36px
      // matches the css-text/boundary-shaping documents this channel targets
      // and the value is irrelevant to whether the FACE is available.
      loaded: (() => { try { return document.fonts.check(`36px "${f}"`); } catch { return false; } })(),
    }));
  });
  if (fontReport) {
    for (const f of fontReport) {
      console.log(`  [fonts] ${f.loaded ? 'LOADED' : 'NOT LOADED'} @font-face family "${f.family}"`);
    }
    const missing = fontReport.filter((f) => !f.loaded).map((f) => f.family);
    if (missing.length) {
      console.warn(`  ⚠️  ${missing.length} @font-face famil${missing.length === 1 ? 'y' : 'ies'} ` +
                   `did NOT load (${missing.join(', ')}) — those captures render the FALLBACK face`);
    }
    // A face that loaded LATE still needs one more settle: `font-display:
    // block` swaps the invisible text in on load, and that swap reflows every
    // line box the screenshot is about to record.
    await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  }

  // ── wave-45 lane X6: the NOTO-PILOT delivery gate ─────────────────────────
  // The wave-35 gate above only audits the families the harness's managed
  // <style> mounted (per-document @font-face); the pilot faces arrive via
  // OUR addStyleTag injection, so they need their own check or a failed
  // pilot payload would silently measure the pre-pilot cascade under a
  // pilot-labelled run — the exact wrong-face-no-error failure the delivery
  // gates exist to end. Same document.fonts.check probe, same loud-then-
  // honest contract, and the same late-load settle. Skipped entirely (no
  // evaluate, no log) when the pilot injection above did not run.
  if (notoPilotCss) {
    const pilotFamilies = NOTO_PILOT_FACES.map((f) => f.family);
    const pilotReport = await page.evaluate((fams) => fams.map((f) => ({
      family: f,
      loaded: (() => { try { return document.fonts.check(`36px "${f}"`); } catch { return false; } })(),
    })), pilotFamilies);
    for (const f of pilotReport) {
      console.log(`  [noto-pilot] ${f.loaded ? 'LOADED' : 'NOT LOADED'} pilot family "${f.family}"`);
    }
    const missingPilot = pilotReport.filter((f) => !f.loaded).map((f) => f.family);
    if (missingPilot.length) {
      console.warn(`  ⚠️  [noto-pilot] ${missingPilot.length} pilot famil${missingPilot.length === 1 ? 'y' : 'ies'} ` +
                   `did NOT load (${missingPilot.join(', ')}) — those scripts render the PRE-PILOT cascade`);
    }
    // Same reflow argument as the wave-35 gate: a late pilot-face load swaps
    // glyphs under font-display: block and reflows the line boxes.
    await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  }
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
    // B-RC3 divergence guard — fetch the EXACT IR the page rendered (the
    // same-origin /ir-components.json the app itself loaded; App.tsx's
    // IR_ASSET_PATH) and run the spanner+abspos detector over its per-test
    // groups. Fetching through the page rather than guessing a filesystem
    // path guarantees the detector and the render saw identical bytes even
    // under section-runner's rsync'd per-section vite root.
    const combinedIr = await page.evaluate(async () =>
      (await fetch('/ir-components.json')).json()
    );
    const prone = divergenceProneTestKeys(combinedIr);
    // Log the full flagged set up-front — the no-silent-divergence-handling
    // contract: a reader of capture.log must be able to see exactly which
    // tests took the isolated path and why.
    if (prone.size > 0) {
      console.log(`  divergence guard (B-RC3 spanner+abspos): ${prone.size} test(s) flagged for isolated capture: ${[...prone].join(', ')}`);
    }
    const handles = await page.$$('[data-capture-canvas]');
    if (handles.length !== manifest.length) {
      throw new Error(`composed capture: handle/manifest length mismatch (${handles.length}/${manifest.length})`);
    }
    console.log(`  capturing ${manifest.length} composed canvases via per-element screenshots → ${outDir}`);
    let captured = 0;
    let zeroDim = 0;
    // Flagged tests found on this page — queued for the isolated pass below,
    // which OVERWRITES their batch PNG with the fresh-page capture.
    const isolatedQueue = [];
    for (let i = 0; i < manifest.length; i++) {
      const entry = manifest[i];
      // Zero-dim check FIRST (same order as the pre-B-RC3 loop): a zero-dim
      // canvas takes no batch shot — and therefore triggers no scroll — on
      // either driver, keeping the batch scroll sequence byte-identical to
      // the historical one. A zero-dim PRONE canvas still goes to the
      // isolated queue: the fresh page re-lays-out from scratch and either
      // renders it or fails loudly inside captureIsolatedTest.
      if (entry.width <= 0 || entry.height <= 0) {
        console.warn(`  ⚠ composed canvas ${entry.index} (${entry.name}) has zero dimensions — skipping`);
        zeroDim += 1;
        if (prone.has(entry.name)) isolatedQueue.push(entry.name);
        continue;
      }
      const isProne = prone.has(entry.name);
      if (isProne) isolatedQueue.push(entry.name);
      // Screenshot EVERY canvas — including flagged ones. Each element
      // screenshot scrolls its canvas into view, and that scroll SEQUENCE is
      // part of the paint environment on this bug-prone page: skipping the
      // flagged canvases shifted the sequence and flipped the paint of an
      // UNFLAGGED scroll-sensitive neighbor (css-multicol/abspos-multicol-
      // in-second-outer-clipped went green→red vs the wave-21 archived
      // capture — caught by the wave-21 adversarial byte-identity repro).
      // The flagged test's own PNG is provisional: the isolated pass below
      // overwrites it, so the ~300ms batch shot is the price of keeping
      // every unflagged capture byte-identical to the pre-B-RC3 driver.
      const safeName = safe(entry.name);
      await handles[i].screenshot({ path: resolve(outDir, `${safeName}.png`), type: 'png' });
      // Flagged shots are provisional (their slot is overwritten below), so
      // count them via `isolated`, not here — keeps the summary honest.
      if (!isProne) captured += 1;
    }
    if (zeroDim > 0) console.warn(`  ⚠ skipped ${zeroDim} zero-dim composed canvases`);
    // A flagged key with no composed canvas means the detector's grouping
    // and the page's grouping disagreed — impossible while both run
    // splitCombinedIr's algorithm, so surface it loudly if it ever happens.
    const manifestNames = new Set(manifest.map((e) => e.name));
    for (const key of prone) {
      if (!manifestNames.has(key)) {
        console.warn(`  ⚠ divergence guard flagged "${key}" but the page rendered no composed canvas for it`);
      }
    }
    // Isolated pass — one fresh single-canvas page per flagged test (the
    // render is correct there; the multi-canvas paint bug never engages).
    // Sequential on purpose: each page is short-lived and Chromium under
    // --disable-gpu rasters serially anyway; parallel pages would only add
    // memory pressure. A failure THROWS (crashing the capture like any
    // other screenshot failure) rather than silently leaving a stale or
    // missing PNG for the compare stage to misread as engine divergence.
    let isolated = 0;
    for (const testKey of isolatedQueue) {
      console.log(`  ↺ isolated capture (B-RC3): ${testKey}`);
      await captureIsolatedTest({
        browser, baseUrl, outDir, testKey,
        wptMode, captureWidth, forceState, animationTime, captureDark,
      });
      isolated += 1;
    }
    // Summary counts batch + isolated so downstream `grep '✓ captured'`
    // totals stay honest; the parenthetical keeps the isolated count
    // auditable at a glance.
    console.log(`✓ captured ${captured + isolated} / ${manifest.length} composed canvases (${isolated} via isolated pages)`);
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

  // Chrome/Chromium silently caps a SINGLE screenshot at 16384 px tall
  // (SCREENSHOT_CAP — the 2^14 max-texture limit; confirmed on the WPT
  // per-component page, where a composed border-radius canvas at y≈31 000
  // came back as unrelated pixels). Anything the compositor is asked to
  // paint below that offset in ONE screenshot returns garbage/blank, so
  // every per-component crop taken from past the cap was untrustworthy.
  //
  // The tricky part is NOT regressing the committed 327-pair baseline.
  // Those baselines are the visual-test fixture — 121 canvases, page
  // height ≈13 000 px, comfortably UNDER the cap. So we branch on height:
  //   • pageHeight ≤ cap → the exact historical call, byte-for-byte
  //     (`page.screenshot({ type: 'png' })` on the pageHeight-tall
  //     viewport). Every sub-cap capture page — including every baseline —
  //     is captured identically to before. No re-baseline needed.
  //   • pageHeight > cap → capture in vertical bands (each ≤ BAND_HEIGHT)
  //     and stitch them into one full-height PNG, so the crop code below is
  //     unchanged. This is what makes the WPT per-component page (328
  //     canvases, ≈200 000 px) honest. We deliberately do NOT reuse the
  //     WPT-composed per-element path here: that path is only viable at
  //     ~100 elements — at 328 canvases the per-element
  //     `Runtime.callFunctionOn` round-trips time out (the very reason this
  //     legacy path exists). Banding keeps ONE screenshot per band (≈13
  //     bands for 200 000 px), nowhere near the per-element ceiling.
  const SCREENSHOT_CAP = 16384;   // Chrome's per-screenshot height ceiling (2^14).
  const BAND_HEIGHT    = 16000;   // per-band capture height — a margin under the cap.

  // sharp is needed both to stitch the bands and to crop the components, so
  // import it once here (hoisted out of the crop section below). See the
  // crop-section comment for why limitInputPixels:false is on every call.
  const sharpMod = await import('sharp');
  const sharp = sharpMod.default;

  let fullPng;
  if (pageHeight <= SCREENSHOT_CAP) {
    // Fits in one screenshot → identical bytes to the historical path. This
    // is the branch every committed-baseline capture takes.
    console.log(`  capturing ${manifest.length} canvases via ${captureWidth}×${pageHeight} screenshot → ${outDir}`);
    fullPng = await page.screenshot({ type: 'png' });
  } else {
    // Page exceeds the cap. Grab it in vertical bands and composite them
    // back into one full-height PNG at their captured offsets.
    const bandCount = Math.ceil(pageHeight / BAND_HEIGHT);
    console.log(`  capturing ${manifest.length} canvases via ${bandCount} bands of ≤${BAND_HEIGHT}px (page ${captureWidth}×${pageHeight} exceeds ${SCREENSHOT_CAP}px cap) → ${outDir}`);
    const bands = [];
    for (let top = 0; top < pageHeight; top += BAND_HEIGHT) {
      const height = Math.min(BAND_HEIGHT, pageHeight - top);
      // `clip` bounds the OUTPUT image to this band, so no single screenshot
      // ever reaches the cap. `captureBeyondViewport` is the load-bearing
      // flag: it tells Chrome to render the clipped region even when it sits
      // below the composited viewport — without it, every band past the
      // first comes back blank, i.e. the same failure as the cap itself.
      const buf = await page.screenshot({
        type: 'png',
        clip: { x: 0, y: top, width: captureWidth, height },
        captureBeyondViewport: true,
      });
      bands.push({ input: buf, top, left: 0 });
    }
    // The bands tile [0, pageHeight) exactly (each starts where the previous
    // ended; the last is trimmed to the remainder), so compositing them onto
    // a blank full-height canvas reproduces what an uncapped single
    // screenshot would have been — no gaps, no overlap. The background is
    // the capture bg (#1A1A2E) purely as a safety net; full tiling means it
    // is never actually visible.
    fullPng = await sharp({
      create: {
        width: captureWidth,
        height: pageHeight,
        channels: 4,
        background: { r: 0x1A, g: 0x1A, b: 0x2E, alpha: 1 },
      },
      limitInputPixels: false,
    })
      .composite(bands.map((b) => ({ input: b.input, top: b.top, left: b.left })))
      .png()
      .toBuffer();
  }

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
  // it's a false positive). Passed to ALL sharp() invocations. `sharp` is
  // imported once above (hoisted so the band-stitch path can use it too).
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
    const safeName = safe(entry.name);
    // Composed mode: the canvas name IS the WPT test key, and the diff side
    // (inject-wpt-block.mjs composed path) looks the file up by exactly
    // `<safe(testKey)>.png` — so we DROP the `<NNN>_` index prefix that the
    // per-component path uses (there it disambiguates same-named components;
    // here each name is a unique test key). Legacy per-component runs keep
    // the padded index prefix byte-for-byte.
    const filename = wptComposed ? `${safeName}.png` : `${String(entry.index).padStart(3, '0')}_${safeName}.png`;
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
