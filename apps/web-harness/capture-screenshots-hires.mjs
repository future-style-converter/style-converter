#!/usr/bin/env node
//
// capture-screenshots-hires.mjs
//
// Hi-res sibling of capture-screenshots.mjs for the B-EXT spec
// (COMPARE_METRICS_B8-B10.md Section 1.1 + Section 7 step 5).
//
// The key difference from the 1× driver:
//   deviceScaleFactor: 4   (vs 1)
// so a 0.25-px sub-pixel baseline drift on the 1× canvas surfaces as a
// 1-px shift on the captured 4× buffer (above AA noise). The 327-pair
// regression baseline is locked at 1× — running this driver populates a
// SEPARATE output directory (testing/probes/) so we can never accidentally
// commit a 4× image into testing/baseline/.
//
// Output contract: one PNG per probe component to testing/probes/web__<comp>.png
// (the `web__` prefix mirrors the iOS__/Android__ pattern compute-text-metrics
// scans for). Captures are intentionally NOT cropped to per-canvas bounds —
// the metric helpers (B8 baseline scan, B9 line FFT, B10 glyph projection)
// expect a single-component framing because the dev-server `?mode=fixture`
// route renders ONE component per page load. This avoids the multi-canvas
// gallery layout that the 1× pipeline uses.
//
// Assumes the dev server is already running. probe-text-metrics.sh starts it.
//
// Usage:
//   node capture-screenshots-hires.mjs [--url http://localhost:3000]
//                                      [--out probes]
//                                      [--probe-dir <abspath-to-_metric_probes>]
//

import puppeteer from 'puppeteer';
import { mkdirSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ── Args ─────────────────────────────────────────────────────────────────────
// Mirror the 1× driver's flag handling so callers reuse the same mental model.
const args = process.argv.slice(2);
const baseUrl = getArg('--url') ?? 'http://localhost:3000';
// Default `out` resolves to `testing/probes/` (the harness now lives in
// apps/web-harness, so hop to the repo root first). Lives outside the
// per-platform screenshot dirs so the standard compare loop never scans
// them (compare-screenshots.mjs walks the iOS/Android/web screenshot dirs).
const outDir = resolve(__dirname, '..', '..', getArg('--out') ?? 'testing/probes');
// The probe-fixture root — the driver discovers component IDs by walking
// every JSON file underneath. Default points at the canonical location
// the spec defines (examples/_metric_probes/). Allows alternate roots so
// the test suite can point at a tmp dir.
const probeDir = resolve(getArg('--probe-dir') ?? resolve(__dirname, '..', '..', 'examples', '_metric_probes'));

function getArg(name) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
}

// ── Probe-component discovery ────────────────────────────────────────────────
// Walk the probe fixture tree and gather { name, fixturePath } pairs. We
// rely on the fixture JSON shape `{ components: { <Name>: {...} } }` —
// the same convention the standard CaptureGallery uses, so a probe fixture
// stays load-compatible with the existing dev server.
function listProbeComponents(root) {
  const out = [];
  const walk = (dir) => {
    for (const entry of readdirSync(dir)) {
      const p = join(dir, entry);
      const st = statSync(p);
      if (st.isDirectory()) walk(p);
      else if (entry.endsWith('.json')) {
        try {
          const doc = JSON.parse(readFileSync(p, 'utf8'));
          if (doc?.components) {
            for (const name of Object.keys(doc.components)) out.push({ name, fixturePath: p });
          }
        } catch (e) {
          // Skip malformed JSON — the converter test pass would have flagged
          // it already; we just don't want a syntax error here to abort the
          // whole probe run.
          console.warn(`[hires] skipping ${p}: ${e.message}`);
        }
      }
    }
  };
  walk(root);
  return out;
}

// ── Setup ────────────────────────────────────────────────────────────────────
mkdirSync(outDir, { recursive: true });

// Same launch flags as the 1× driver — keep paint/RAF active in headless
// mode so per-component screenshots don't time out. protocolTimeout bumped
// because 4× screenshots are ~16× the byte size and CDP serialisation can
// stretch a few seconds per component on cold CI machines.
const browser = await puppeteer.launch({
  headless: 'new',
  protocolTimeout: 300_000,
  args: [
    '--disable-background-timer-throttling',
    '--disable-renderer-backgrounding',
    '--disable-backgrounding-occluded-windows',
  ],
});

try {
  const page = await browser.newPage();

  // The headline difference vs capture-screenshots.mjs: deviceScaleFactor=4.
  // A 390 px logical canvas captures as 1560 actual pixels, so a 0.25-px
  // baseline shift in CSS coords lands as 1 actual pixel in the buffer.
  // Spec Section 1.1 — "B8 needs 4× resolution".
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 4 });

  page.on('pageerror', (err) => console.error('[pageerror]', err.message));
  const ignoredRE = /Unsupported style property|Failed to load resource:.*404/;
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return;
    const text = msg.text();
    if (ignoredRE.test(text)) return;
    console.error('[console error]', text);
  });

  const components = listProbeComponents(probeDir);
  console.log(`→ discovered ${components.length} probe components under ${probeDir}`);

  // Per-component capture loop. The dev server's ?fixture=<Name> route
  // (FixtureCanvas.tsx) renders ONE component, sets data-fixture-ready=1
  // after layout settles, then we element-screenshot the wrapper div.
  // Per-element screenshot is fine here because the probe set is small
  // (≤20 components) — the bulk-page-screenshot trick the 1× driver uses
  // is only needed when N>>50.
  let captured = 0, skipped = 0;
  for (const { name } of components) {
    const url = `${baseUrl.replace(/\/$/, '')}/?fixture=${encodeURIComponent(name)}`;
    try {
      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60_000 });
      // FixtureCanvas flips this attribute 50 ms after the component mounts
      // — see FixtureCanvas.tsx for the rationale (RAF throttling under
      // headless). Wait for it before screenshotting so we don't capture
      // a half-rendered first paint.
      await page.waitForSelector(`[data-testid="${name}"][data-fixture-ready="1"]`, { timeout: 10_000 });
      // Let webfonts settle. document.fonts.ready fires once every
      // currently-pending font has finished loading — if a serif fixture
      // is mid-fetch when we screenshot, we'd capture the fallback face.
      await page.evaluate(() => document.fonts?.ready ?? Promise.resolve());
      const handle = await page.$(`[data-testid="${name}"]`);
      if (!handle) { skipped++; continue; }
      const safe = name.replace(/[^A-Za-z0-9._-]/g, '_');
      // The `web__` prefix matches the iOS__/Android__ shape compute-text-metrics
      // scans for in testing/probes/.
      const outPath = resolve(outDir, `web__${safe}.png`);
      await handle.screenshot({ path: outPath, type: 'png' });
      captured++;
    } catch (e) {
      // One slow / broken component shouldn't abort the whole probe run.
      // Log and move on — compute-text-metrics.mjs treats missing PNGs as
      // null per-platform readings, which the manifest schema explicitly
      // permits (Section 3 — `iOS: number | null`).
      console.error(`[hires] ${name}: ${e.message}`);
      skipped++;
    }
  }
  console.log(`✓ captured ${captured} / ${components.length} probe components (${skipped} skipped)`);
} finally {
  await browser.close();
}
