#!/usr/bin/env node
//
// tools/titan/capture-browser-ref.mjs — Phase 1 browser-ref capture.
//
// Implements TITAN_ARCHITECTURE.md Section 5.2: render every WPT test's
// `*-ref.html` directly in headless Chromium and save it as the
// "spec-truth" reference image.
//
// Cache layout (Section 5.4):
//   tools/wpt/refs/<wpt-sha>/<spec-section>/<test-stem>.png
//
// We key on the WPT_REF SHA so re-pinning regenerates the cache; everything
// else hits cache on subsequent runs (the corpus is byte-identical for a
// given pin, so the rendered ref PNG is too within AA noise).
//
// Capture canvas matches the rest of the Style-Converter pipeline so
// browser-ref images are pixel-comparable against the iOS/Android/web
// captures from compare-screenshots.mjs:
//   - width            : 390 px  (matches CaptureCanvas)
//   - height           : natural (we resize the viewport to documentHeight)
//   - background       : #1A1A2E (matches the chromeless capture mode)
//   - padding          : 16 px around the body root
//   - deviceScaleFactor: 1
//
// We deliberately DO NOT use WPT's spec-default 800×600 white-background
// canvas. Reasons:
//   1. The 327-pair pipeline already standardises on 390×N #1A1A2E. A
//      browser-ref captured at 800×600 would not be directly comparable
//      to our existing platform captures without re-renormalising every
//      pair.
//   2. compare-screenshots.mjs's `padToCanvas` helper pads to #1A1A2E. A
//      white-background ref would inflate edge-pair pixelmatch counts
//      where the test renders past 390 px (very common — WPT tests often
//      assume a wider canvas).
//   3. The Section 5.3 fuzzy-tolerance metadata is stored alongside the
//      ref but applies to the test↔ref pair, not to a particular canvas
//      size. Rendering both halves at 390 px keeps fuzzy semantics
//      consistent.
//
// Usage:
//   node tools/titan/capture-browser-ref.mjs <test-rel-path>...
//   WPT_REF=<sha> node tools/titan/capture-browser-ref.mjs ...
//
// Exit codes:
//   0 — every input rendered (or already cached)
//   1 — at least one render failed; partial cache populated
//   2 — fatal infra error (Puppeteer launch, FS)

import puppeteer from 'puppeteer';
import { promises as fs } from 'node:fs';
import { existsSync } from 'node:fs';
import { resolve, dirname, join, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

import { extractRefHref } from './extract-fixture.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');
const WPT_DIR    = process.env.WPT_DIR ?? join(REPO_ROOT, 'tools', 'wpt');
const REFS_ROOT  = join(REPO_ROOT, 'tools', 'wpt', 'refs');

// Capture canvas dimensions — copied verbatim from the rest of the pipeline
// so browser-ref images line up with iOS/Android/web captures pixel-for-pixel.
const CANVAS_WIDTH  = 390;
const CANVAS_BG     = '#1A1A2E';
const CANVAS_PAD_PX = 16;

/** Resolve the WPT SHA the same way bucket-wpt.mjs and fetch-wpt.sh do. */
async function resolveWptRef() {
  if (process.env.WPT_REF) return process.env.WPT_REF;
  const refFile = join(__dirname, 'WPT_REF');
  const raw = await fs.readFile(refFile, 'utf8');
  for (const line of raw.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    return trimmed.split(/\s+/)[0];
  }
  throw new Error('WPT_REF file contains no SHA line');
}

/** Resolve the on-disk path of the rel="match" reference for a test path. */
export async function resolveRefPath(testRel) {
  const testAbs = join(WPT_DIR, testRel);
  const html = await fs.readFile(testAbs, 'utf8');
  const refHref = extractRefHref(html);
  if (!refHref) throw new Error(`no rel="match" link in ${testRel}`);
  return refHref.startsWith('/')
    ? join(WPT_DIR, refHref.slice(1))
    : resolve(dirname(testAbs), refHref);
}

/** Compute the cache PNG path for a given test under a given WPT_REF. */
export function cachePathFor(wptRef, testRel) {
  const parts = testRel.split('/'); // posix
  // Spec section is the second segment when the test lives under
  // css/<section>/.... When a test lives directly under css/ (rare —
  // some CSS2 stragglers do), there's no section dir and we bucket it
  // under "css" so the path layout stays uniform.
  const section = parts.length >= 3 ? parts[1] : 'css';
  const stem = basename(parts[parts.length - 1], '.html');
  return join(REFS_ROOT, wptRef, section, `${stem}.png`);
}

/** Render a single ref HTML to PNG. Returns the cache path. */
async function renderOne(page, wptRef, testRel) {
  const refAbs = await resolveRefPath(testRel);
  const dest = cachePathFor(wptRef, testRel);
  if (existsSync(dest)) return { dest, cached: true };

  await fs.mkdir(dirname(dest), { recursive: true });

  // Render via file:// so the test's relative resource paths resolve.
  // Puppeteer requires the file:// URL to be absolute and properly encoded
  // for spaces / weird chars.
  const fileUrl = 'file://' + encodeURI(refAbs);

  // Chromeless wrapper page that frames the ref in our canvas. We can't
  // edit the WPT ref HTML in place (the corpus is gitignored, and we'd
  // pollute its hash). Instead we use Puppeteer's `page.goto(refUrl)` and
  // then evaluate a tiny CSS injection that pads the body and sets the
  // background. The ref's own root margin/padding still applies — the
  // injection is the OUTER frame, so reftest geometry stays intact.
  await page.goto(fileUrl, { waitUntil: 'load', timeout: 30_000 });
  // Frame the ref in our 390-wide #1A1A2E canvas WITHOUT clobbering any
  // body/html styling the WPT ref itself declares.
  //
  // Why `:where(...)` and not bare `html, body`: per the CSS Selectors L4
  // spec, `:where()` zeroes out the specificity of its argument list. A
  // bare `body { background: olive; }` in the ref (specificity 0,0,1)
  // therefore wins over our injected `:where(html, body) { background:
  // CANVAS_BG }` (specificity 0,0,0). This was the root cause of
  // https://…/pilot-001/css-backgrounds__background-color-animation-in-body
  // — the original `html, body { background }` injection silently
  // overrode the ref's own `body { background-color: rgb(100,100,0) }`,
  // turning every `body`-painted ref into a uniform CANVAS_BG square.
  //
  // Style precedence per CSS 2.1 §6.4.3 (cascading order, last step is
  // "later one wins" for equal specificity), so injecting *after* page
  // load with equal specificity would still win. `:where()` is the only
  // way to inject a true "default" that any author rule can override.
  //
  // Padding is handled the same way: `:where(body) { padding }` lets a
  // ref that explicitly sets its own body padding/margin keep it.
  await page.addStyleTag({
    content: `
      :where(html, body) { margin: 0; padding: 0; background: ${CANVAS_BG}; }
      :where(body) { padding: ${CANVAS_PAD_PX}px; box-sizing: border-box;
                     min-height: 100vh; color: #fff; }
    `,
  });

  // Match the existing capture pipeline: viewport 390 wide, height = full
  // document height so we capture the whole reftest output without scroll
  // clipping.
  await page.setViewport({ width: CANVAS_WIDTH, height: 600, deviceScaleFactor: 1 });
  // Let layout settle once at the standard height before measuring.
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  const docHeight = await page.evaluate(() => Math.max(
    document.documentElement.scrollHeight,
    document.body?.scrollHeight ?? 0,
    600,
  ));
  await page.setViewport({ width: CANVAS_WIDTH, height: docHeight, deviceScaleFactor: 1 });
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));

  await page.screenshot({ path: dest, type: 'png' });
  return { dest, cached: false };
}

/** Render N tests with one shared browser instance. Returns per-test status. */
export async function captureRefs(testRels, opts = {}) {
  const wptRef = opts.wptRef ?? await resolveWptRef();
  const browser = await puppeteer.launch({
    headless: 'new',
    // Match capture-screenshots.mjs's flag set so any timer-throttling
    // weirdness behaves identically across the two capture paths.
    args: [
      // Force CPU rasterization — see capture-screenshots.mjs. Two reasons
      // it matters HERE too: (1) rendering a real WPT reference page would
      // otherwise deadlock Page.captureScreenshot the same way the platform
      // capture did; (2) the browser-ref and the platform-web capture MUST
      // use the same raster backend or a GPU-vs-CPU sub-pixel delta would
      // depress every web-ref SSIM. Both CPU → apples-to-apples.
      '--disable-gpu',
      '--disable-background-timer-throttling',
      '--disable-renderer-backgrounding',
      '--disable-backgrounding-occluded-windows',
      // macOS focus-steal hardening — mirrors capture-screenshots.mjs.
      // Critical for TITAN swarm: 8+ parallel browser-ref captures
      // would otherwise flash the dock repeatedly.
      //
      // DO NOT add `--no-startup-window` here. It tells Chrome not to
      // open any window at startup, which prevents puppeteer from
      // creating a page target — `puppeteer.launch()` then hangs (no
      // window to host newPage), every `page.waitForSelector` times out
      // at 30 s, and every browser-ref capture fails with "TimeoutError"
      // → section-runner reports `browser-ref had failures` and the
      // downstream classifier sees no reference frames. See
      // apps/web-harness/capture-screenshots.mjs for the matching fix.
      '--no-default-browser-check',
      '--no-first-run',
      '--disable-features=Translate,MediaRouter,OptimizationHints',
    ],
    protocolTimeout: 300_000,
  });

  const results = [];
  try {
    const page = await browser.newPage();
    page.on('pageerror', (err) => console.error('[pageerror]', err.message));

    let i = 0;
    for (const rel of testRels) {
      i++;
      try {
        const { dest, cached } = await renderOne(page, wptRef, rel);
        results.push({ test: rel, dest, cached, ok: true });
        if (!opts.quiet) {
          process.stderr.write(`  [${i}/${testRels.length}] ${cached ? 'cache' : 'rendered'} ${rel}\n`);
        }
      } catch (err) {
        results.push({ test: rel, ok: false, error: err.message ?? String(err) });
        if (!opts.quiet) {
          process.stderr.write(`  [${i}/${testRels.length}] FAIL  ${rel}: ${err.message ?? err}\n`);
        }
      }
    }
  } finally {
    await browser.close();
  }

  return { wptRef, results };
}

// ── CLI ──────────────────────────────────────────────────────────────────────
async function main() {
  const inputs = process.argv.slice(2);
  if (inputs.length === 0) {
    console.error('usage: capture-browser-ref.mjs <relative-test-path>...');
    process.exit(1);
  }
  const { wptRef, results } = await captureRefs(inputs);
  const ok = results.filter((r) => r.ok).length;
  const fail = results.length - ok;
  console.log(`capture-browser-ref: wptRef=${wptRef.slice(0, 12)}  ok=${ok}  fail=${fail}`);
  process.exit(fail > 0 ? 1 : 0);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error('capture-browser-ref: fatal:', err);
    process.exit(2);
  });
}
