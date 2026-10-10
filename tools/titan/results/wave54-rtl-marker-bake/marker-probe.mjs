#!/usr/bin/env node
//
// tools/titan/results/wave54-rtl-marker-bake/marker-probe.mjs — window [W1] of wave-54 L1 (PLAN §2 L1): the CDP
// ::marker probe, ADAPTED from tools/titan/results/wave53-lists-bakes/marker-probe.mjs to unit M′'s ROOT ownership
// (that original asserts the marker runs as `kids.slice(1)` of each `<li>`, which must FAIL under M′ by design).
// Starts Chromium (puppeteer, the bake's own launch flags), so it runs ONLY in an orchestrator device-idle window.
// Writes nothing into the repo; prints a report and exits 0 when every acceptance check holds, 1 otherwise.
//
// Two halves:
//   A. The REAL code path: a fresh static extraction of the test, then bidiBakeFixture (tools/titan/bidi-bake.mjs →
//      bidi-marker-bake.mjs collectMarkerFacts → planMarker), and the resulting fixture is inspected.
//   B. Diagnostics from an independent page load under the same canvas contract: the raw DOMSnapshot ::marker nodes
//      (parseSnapshotMarkers) and the accessibility tree's ListMarker names.
//
// Acceptance (counter-suffix; bake coordinates = frame x − 16; the RTL roots sit at bake x0):
//   - outcome baked, 2 roots, 10 runs (4 text + 6 marker);
//   - both RTL roots: `padding: 0` (unit P) and children [li, li, …marker runs] — runs ['.','1','.','2'] / ['א.','ב.'];
//   - each of the 4 RTL items: list-style-type none, NO `marker-*` stamp, exactly ONE child (its text run);
//   - every marker run: frame x-range inside [131, 148] (ref ink x133-145) and top = its item's top + 2 (±1);
//   - DOMSnapshot strings '1. ', '2. ', 'א. ', 'ב. ' on the RTL items, row-1 box left 112 ± 0.5.
// Usage: node tools/titan/results/wave54-rtl-marker-bake/marker-probe.mjs [css/css-counter-styles/counter-suffix.html]
import puppeteer from 'puppeteer';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const T = resolve(HERE, '..', '..');                                   // tools/titan
const REPO = resolve(T, '..', '..');
const { extractFixture } = await import(join(T, 'extract-fixture.mjs'));
const { bidiBakeFixture, closeBidiBakeBrowser } = await import(join(T, 'bidi-bake.mjs'));
const { parseSnapshotMarkers } = await import(join(T, 'bidi-marker-bake.mjs'));
const { BROWSER_LAUNCH_ARGS, canvasFrameCss, REF_RENDER_WIDTH, REF_RENDER_MIN_HEIGHT } = await import(join(T, 'capture-browser-ref.mjs'));
const { CANVAS_FRAME_STYLE_ID } = await import(join(T, 'post-load-extract.mjs'));
const { checkBakedCounterSuffix } = await import(join(HERE, 'marker-probe-checks.mjs'));

const rel = process.argv[2] ?? 'css/css-counter-styles/counter-suffix.html';
const fails = [];
// One acceptance line; a FAIL is collected and decides the exit code.
const check = (ok, what) => { console.log(`${ok ? 'PASS' : 'FAIL'} ${what}`); if (!ok) fails.push(what); };

// ── A. the real code path ────────────────────────────────────────────────────
const { fixture } = await extractFixture(rel);
const outcome = await bidiBakeFixture(fixture, rel);
await closeBidiBakeBrowser();
console.log('outcome', JSON.stringify(outcome));
// The half-A acceptance lives in marker-probe-checks.mjs (self-tested offline by marker-probe.selftest.mjs).
checkBakedCounterSuffix(fixture, outcome, check, console.log);

// ── B. diagnostics: raw CDP under the same contract ─────────────────────────
const browser = await puppeteer.launch({ headless: 'new', args: BROWSER_LAUNCH_ARGS });
try {
  const page = await browser.newPage();
  await page.setViewport({ width: REF_RENDER_WIDTH, height: REF_RENDER_MIN_HEIGHT, deviceScaleFactor: 1 });
  await page.goto('file://' + encodeURI(join(REPO, 'tools', 'wpt', rel)), { waitUntil: 'load', timeout: 30_000 });
  await page.evaluate(({ id, css }) => { const s = document.createElement('style'); s.id = id; s.textContent = css; document.head.appendChild(s); },
    { id: CANVAS_FRAME_STYLE_ID, css: await canvasFrameCss() });
  await page.evaluate(() => document.fonts.ready.then(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))));
  const cdp = await page.createCDPSession();
  const markers = parseSnapshotMarkers(await cdp.send('DOMSnapshot.captureSnapshot', { computedStyles: [] }));
  for (const m of markers) console.log('snapshot marker', JSON.stringify(m));
  const rtl = markers.filter((m) => m.hostRect && m.hostRect.y >= 192);
  check(JSON.stringify(rtl.map((m) => m.text)) === JSON.stringify(['1. ', '2. ', 'א. ', 'ב. ']), 'DOMSnapshot strings on the RTL items');
  check(rtl[0]?.box && Math.abs(rtl[0].box.x - 112) <= 0.5, `row-1 marker box left ${rtl[0]?.box?.x} = 112 ± 0.5`);
  const ax = await cdp.send('Accessibility.getFullAXTree');
  console.log('AX ListMarker names', JSON.stringify(ax.nodes.filter((n) => n.role?.value === 'ListMarker').map((n) => n.name?.value)));
  await cdp.detach();
} finally { await browser.close(); }
console.log(fails.length ? `MARKER PROBE: ${fails.length} FAIL` : 'MARKER PROBE: ALL PASS');
process.exit(fails.length ? 1 : 0);
