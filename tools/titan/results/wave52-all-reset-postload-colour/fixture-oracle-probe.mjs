#!/usr/bin/env node
// fixture-oracle-probe.mjs — lane L11 FIX PASS (skeptic M1): prove the
// `_expect` oracle of fixtures/combinations/all-then-color.json is TRUE in CSS
// terms and that the web runtime + harness render it, device-free.
//
// Why: the first cut asserted boxes for `all: initial` components that the
// browser never paints (`all` resets `display` to its initial `inline` —
// css-display-3 §2 — so width/height stop applying; CSS 2.1 §10.3.1/§10.6.1).
// The pin would have blessed the natives' wrong box. This probe judges every
// render with the REAL spec oracle (tools/visual/spec-oracle.mjs
// parseExpectations + measureCapture), each render ALONE:
//
//   css      — the CSS truth: the fixture's declarations written per key in
//              SOURCE order (CSSOM setProperty) on nested <div>s inside a
//              390-px #1A1A2E canvas with 16-px padding (the capture contract),
//              in the pinned headless Chromium. No runtime involved.
//   harness  — the product path: the converted IR (real :converter output,
//              convert-out/all-then-color/tmpOutput.json — run order-premise.mjs
//              first) served to the REAL web harness (vite, `?mode=capture`) by
//              request interception (no file written under apps/), each
//              `[data-capture-canvas]` element-screenshotted.
//
// Executed mutations (each MUST produce violations, proving the probe can fail):
//   M1 css      — the PRE-FIX fixture (every `display` key removed): expected
//                 violations on exactly AllThenProps, InitialUnderRedParent,
//                 DirectionSurvives (the skeptic's repro 20, re-derived).
//   M2 css / harness — `display: block` moved BEFORE `all` (fixture / IR
//                 level; §6.4 order is load-bearing): the same three must fail.
//   M3 css      — a runtime that KEEPS the declarations before `all` (the
//                 W2/M2-class unit mutations), simulated by deleting the `all`
//                 key where it is LAST: PropsThenAll's 150x50 red child paints
//                 and out-votes the green (7500 vs 2100 px) — it must fail.
//   M4 css      — the pre-wave-52 order-blind rule (Compose/iOS: any `all`
//                 drops EVERY declaration), simulated by emptying each
//                 `all`-carrying component: the three box rows must fail.
// Usage: node fixture-oracle-probe.mjs [--mode css|harness|all] [--out f.json]
// Writes its JSON record beside itself (default fixture-oracle-probe.json).
import { spawn } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:net';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..', '..');
// puppeteer + pngjs are hoisted to the workspace root (the titan tools use them).
const req = createRequire(join(ROOT, 'tools', 'titan', 'score-gate.mjs'));
const puppeteer = req('puppeteer');
const { PNG } = req('pngjs');
// The gate's own oracle — the probe never re-implements the measurement.
const { parseExpectations, measureCapture } = await import(join(ROOT, 'tools', 'visual', 'spec-oracle.mjs'));
const { buildCaptureUrl } = await import(join(ROOT, 'apps', 'web-harness', 'capture-url.mjs'));

const FIXTURE = join(ROOT, 'fixtures', 'combinations', 'all-then-color.json');
const IR = join(HERE, 'convert-out', 'all-then-color', 'tmpOutput.json');
const arg = (k, d) => (process.argv.includes(k) ? process.argv[process.argv.indexOf(k) + 1] : d);   // `--k v` flags
const mode = arg('--mode', 'all');
const OUT = join(HERE, arg('--out', 'fixture-oracle-probe.json'));
const fixture = JSON.parse(readFileSync(FIXTURE, 'utf8'));
const expectations = parseExpectations(fixture);   // name → normalized _expect

/** Judge one name → PNG map with the oracle; returns {name: {ok, measured}}. */
function judge(pngs) {
  const out = {};
  for (const [name, exp] of expectations) {
    const png = pngs.get(name);
    if (!png) { out[name] = { ok: false, missing: true }; continue; }   // no capture = violation, never a skip
    const m = measureCapture(png, exp);
    // Same pass rule as evaluateOracle: fill within tolerance per channel AND box within tolerance per axis.
    const fillOk = m.fill !== null && m.fill.every((c, i) => Math.abs(c - exp.fill[i]) <= exp.fillTolerance);
    const boxOk = !exp.box || exp.box.every((d, i) => Math.abs(d - m.box[i]) <= exp.boxTolerance);
    out[name] = { ok: fillOk && boxOk, fill: m.fill, box: m.box, expectBox: exp.box };
  }
  return out;
}

/** Copy of the fixture with `display` keys transformed by `fn(props) → props`. */
function variant(fn) {
  const doc = structuredClone(fixture);
  const walk = (comps) => { for (const c of Object.values(comps ?? {})) { c.properties = fn(c.properties ?? {}); walk(c.children); } };
  walk(doc.components);
  return doc;
}
// M1: the pre-fix fixture = today's minus every `display` key (nothing else changed in the fix).
const noDisplay = (p) => Object.fromEntries(Object.entries(p).filter(([k]) => k !== 'display'));
// M2: `display` moved to just BEFORE `all` (only where both exist).
const displayBeforeAll = (p) => {
  if (!('all' in p) || !('display' in p)) return p;
  const e = Object.entries(p).filter(([k]) => k !== 'display');
  const i = e.findIndex(([k]) => k === 'all');
  e.splice(i, 0, ['display', p.display]);
  return Object.fromEntries(e);
};
// M3: `all` deleted where it is the LAST key (the before-`all` drop not applied).
const keepBefore = (p) => { const k = Object.keys(p); return k.at(-1) === 'all' ? Object.fromEntries(Object.entries(p).slice(0, -1)) : p; };
// M4: any `all` empties the component's declarations (the old natives' rule).
const orderBlind = (p) => ('all' in p ? {} : p);
const browser = await puppeteer.launch({ headless: 'new', args: ['--force-color-profile=srgb'] });
const report = { generated: new Date().toISOString(), fixture: 'fixtures/combinations/all-then-color.json', runs: {} };

/** CSS-truth render of one fixture doc → name → PNG. */
async function renderCss(doc) {
  const page = await browser.newPage();
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 1 });
  const pngs = new Map();
  for (const [name, comp] of Object.entries(doc.components)) {
    await page.setContent('<!doctype html><html><body style="margin:0;background:#1A1A2E"></body></html>');
    // Build the subtree in-page; per-key setProperty in object order = source order (§6.4).
    await page.evaluate((c) => {
      const canvas = Object.assign(document.createElement('div'), { id: 'canvas' });
      canvas.style.cssText = 'width:390px;box-sizing:border-box;padding:16px;background:#1A1A2E;';
      const build = (node) => {
        const el = document.createElement('div');
        for (const [k, v] of Object.entries(node.properties ?? {})) el.style.setProperty(k, v);
        if (node._text) el.append(node._text);
        for (const ch of Object.values(node.children ?? {})) el.append(build(ch));
        return el;
      };
      canvas.append(build(c));
      document.body.append(canvas);
    }, comp);
    const buf = await (await page.$('#canvas')).screenshot({ type: 'png' });
    pngs.set(name, PNG.sync.read(buf));
  }
  await page.close();
  return pngs;
}

/** A free localhost port for the private vite (never the shared :3000). */
const freePort = () => new Promise((res) => { const s = createServer(); s.listen(0, () => { const p = s.address().port; s.close(() => res(p)); }); });

/** Real web-harness render of one IR doc → name → PNG (vite + request interception). */
async function renderHarness(irDoc, port) {
  const page = await browser.newPage();
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 1 });
  await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: 'light' }]);   // capture contract
  await page.setRequestInterception(true);
  // Serve OUR IR at the harness's IR_ASSET_PATH; everything else goes to vite.
  page.on('request', (r) => (new URL(r.url()).pathname === '/ir-components.json'
    ? r.respond({ status: 200, contentType: 'application/json', body: JSON.stringify(irDoc) })
    : r.continue()));
  await page.goto(buildCaptureUrl(`http://localhost:${port}`, false), { waitUntil: 'domcontentloaded', timeout: 60_000 });
  await page.waitForSelector('[data-capture-ready]', { timeout: 60_000 });
  await page.evaluate(() => document.fonts?.ready ?? Promise.resolve());
  const pngs = new Map();
  for (const h of await page.$$('[data-capture-canvas]')) {
    const name = await h.evaluate((el) => el.getAttribute('data-capture-name'));
    pngs.set(name, PNG.sync.read(await h.screenshot({ type: 'png' })));
  }
  await page.close();
  return pngs;
}
try {
  if (mode === 'css' || mode === 'all') {
    report.runs.css = judge(await renderCss(fixture));
    report.runs.css_M1_preFix = judge(await renderCss(variant(noDisplay)));
    report.runs.css_M2_displayBeforeAll = judge(await renderCss(variant(displayBeforeAll)));
    report.runs.css_M3_keepBeforeAll = judge(await renderCss(variant(keepBefore)));
    report.runs.css_M4_orderBlindDrop = judge(await renderCss(variant(orderBlind)));
  }
  if (mode === 'harness' || mode === 'all') {
    const port = await freePort();
    // `npx vite --port P` from the harness dir — exactly test-all.sh's web leg launch, private port.
    const vite = spawn('npx', ['vite', '--port', String(port), '--strictPort'], { cwd: join(ROOT, 'apps', 'web-harness'), stdio: 'ignore', detached: true });
    try {
      for (let i = 0; i < 60; i++) {   // readiness poll, 1 s steps (test-all.sh's loop)
        try { if ((await fetch(`http://localhost:${port}`)).ok) break; } catch { /* not up yet */ }
        await new Promise((r) => setTimeout(r, 1000));
      }
      const ir = JSON.parse(readFileSync(IR, 'utf8'));
      report.runs.harness = judge(await renderHarness(ir, port));
      const irM2 = structuredClone(ir);
      for (const c of irM2.components) {   // M2 at IR level: Display moved before All where both exist
        const d = c.properties.findIndex((p) => p.type === 'Display');
        const a = c.properties.findIndex((p) => p.type === 'All');
        if (d > a && a >= 0) c.properties.splice(a, 0, ...c.properties.splice(d, 1));   // Display follows All here
      }
      report.runs.harness_M2_displayBeforeAll = judge(await renderHarness(irM2, port));
    } finally {
      try { process.kill(-vite.pid, 'SIGTERM'); } catch { /* already gone */ }
    }
  }
} finally {
  await browser.close();
}
// Verdicts: the real runs must be all-green; every mutation run must show ≥1 violation.
const bad = (run) => Object.entries(run ?? {}).filter(([, v]) => !v.ok).map(([n]) => n);
report.verdict = {};
for (const [k, run] of Object.entries(report.runs)) {
  report.verdict[k] = k.includes('_M') ? { violations: bad(run), caught: bad(run).length > 0 } : { violations: bad(run), green: bad(run).length === 0 };
}
writeFileSync(OUT, JSON.stringify(report, null, 1) + '\n');
for (const [k, run] of Object.entries(report.runs)) {
  for (const [n, v] of Object.entries(run)) console.log(`${k.padEnd(28)} ${v.ok ? 'ok  ' : 'FAIL'} ${n.padEnd(32)} fill=${JSON.stringify(v.fill)} box=${JSON.stringify(v.box)} expect=${JSON.stringify(v.expectBox)}`);
}
const ok = Object.entries(report.verdict).every(([k, v]) => (k.includes('_M') ? v.caught : v.green));
console.log(ok ? 'PROBE OK' : 'PROBE FAILED', JSON.stringify(report.verdict));
process.exit(ok ? 0 : 1);
