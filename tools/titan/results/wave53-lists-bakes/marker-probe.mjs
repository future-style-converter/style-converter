#!/usr/bin/env node
//
// tools/titan/results/wave53-lists-bakes/marker-probe.mjs — the L1 CDP ::marker probe (PLAN §2 L1 "Only in an
// orchestrator-granted host window"; rtl-marker-bake.md §8 (a)). Starts Chromium (puppeteer, the bake's own launch
// flags), so it runs ONLY in a device-idle window. Writes nothing into the repo; prints a report and exits 0 when
// every acceptance check holds, 1 otherwise.
//
// Two halves:
//   A. The REAL code path: a fresh static extraction of the test, then bidiBakeFixture (tools/titan/bidi-bake.mjs,
//      which calls bidi-marker-bake.mjs collectMarkerFacts → planMarker), and the resulting fixture is inspected.
//   B. Diagnostics from an independent page load under the same canvas contract: the raw DOMSnapshot ::marker
//      nodes (parseSnapshotMarkers) and the accessibility tree's ListMarker names, so a failing A can be
//      attributed to the CDP source, the probe span, or the planner.
//
// Acceptance (counter-suffix; bake coordinates = frame x − 16):
//   - outcome baked, 2 roots, 10 runs (4 text + 6 marker);
//   - each of the 4 RTL items: list-style-type none, NO _lossyReasons marker stamp (CDP string + box used);
//   - marker run texts per item: ['.', '1'], ['.', '2'], ['א.'], ['ב.'];
//   - every marker run's frame x-range inside [131, 148] (ref ink x133-145) and top within ±1 of the text run's;
//   - DOMSnapshot strings '1. ', '2. ', 'א. ', 'ב. ' on the RTL items, row-1 box left 112 ± 0.5.
// Usage: node tools/titan/results/wave53-lists-bakes/marker-probe.mjs [css/css-counter-styles/counter-suffix.html]
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

const rel = process.argv[2] ?? 'css/css-counter-styles/counter-suffix.html';
const fails = [];
const check = (ok, what) => { console.log(`${ok ? 'PASS' : 'FAIL'} ${what}`); if (!ok) fails.push(what); };

// ── A. the real code path ────────────────────────────────────────────────────
const { fixture } = await extractFixture(rel);
const outcome = await bidiBakeFixture(fixture, rel);
await closeBidiBakeBrowser();
console.log('outcome', JSON.stringify(outcome));
check(outcome.status === 'baked' && outcome.roots === 2 && outcome.runs === 10, 'baked — 2 roots, 10 runs');
const items = [];
const walk = (c) => { for (const k of Object.values(c.children ?? {})) { if (k._tag === 'li' && k.properties?.position === 'absolute') items.push(k); walk(k); } };
for (const c of Object.values(fixture.components)) walk(c);
const want = [['.', '1'], ['.', '2'], ['א.'], ['ב.']];
items.forEach((li, i) => {
  const kids = Object.values(li.children ?? {});
  const text = kids[0], marks = kids.slice(1);
  console.log(li.id, JSON.stringify(li.properties), JSON.stringify(li._lossyReasons ?? []));
  for (const m of marks) console.log('   marker run', JSON.stringify(m._text), JSON.stringify(m.properties));
  check(li.properties['list-style-type'] === 'none', `${li.id} list-style-type none`);
  check(!(li._lossyReasons ?? []).some((r) => r.startsWith('marker-')), `${li.id} no marker-* stamp (measured, not modelled)`);
  check(JSON.stringify(marks.map((m) => m._text)) === JSON.stringify(want[i]), `${li.id} marker runs ${JSON.stringify(want[i])}`);
  const liLeft = 16 + parseFloat(li.properties.left);                  // frame x of the li (root at bake x0)
  for (const m of marks) {
    const x0 = liLeft + parseFloat(m.properties.left), x1 = x0 + parseFloat(m.properties.width);
    check(x0 >= 131 && x1 <= 148, `${li.id} ${JSON.stringify(m._text)} frame x${x0.toFixed(2)}-${x1.toFixed(2)} ⊂ [131,148]`);
    check(Math.abs(parseFloat(m.properties.top) - parseFloat(text.properties.top)) <= 1, `${li.id} ${JSON.stringify(m._text)} top ≈ text top`);
  }
});
check(items.length === 4, `4 baked RTL items (found ${items.length})`);

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
