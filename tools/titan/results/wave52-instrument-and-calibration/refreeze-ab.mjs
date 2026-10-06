#!/usr/bin/env node
// tools/titan/results/wave52-instrument-and-calibration/refreeze-ab.mjs
//
// wave-52 L12-B — the leak check for the CANVAS_REV bump ('…-rootbg-uamargin').
// Every one of the gate's scored tests (the 30 sections' tests.list) has its
// ref re-rendered through the SHIPPED path (capture-browser-ref.mjs
// renderRefPng — the new contract) and sha1-compared with the FROZEN PNG of the
// previous contract ('…-htmlpins'). A ref whose bytes differ is then rendered a
// second time under a replica of the OLD sheet, to tell a CONTRACT effect (old
// arm reproduces the frozen bytes → the sheet change moved this ref) from
// rasteriser INSTABILITY (old arm itself differs from the freeze).
// Read-only on both ref trees; differing new renders are saved under
// tools/titan/runs/wave52-calib/refs-ab/ for the PNG replay.
//
// Usage: node …/refreeze-ab.mjs [RUN=wave52-open] [ONLY=<substring>]
// Writes refreeze-ab.json beside this script.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer';

// Paths, the shipped module and the safe-name stem rule.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const TITAN = path.resolve(HERE, '..', '..');
const REPO = path.resolve(TITAN, '..', '..');
const cbr = await import(path.join(TITAN, 'capture-browser-ref.mjs'));
const { fixtureStem } = await import(path.join(TITAN, 'safe-name.mjs'));
// The frozen tree of the PREVIOUS contract (the scorer view every gate read).
const WPT_SHA = '9b5435e55e0b54a6cd09c1c563861eb3c999cef1';
const OLD_REV = 'white-black-ink-font-lh-imgpad-htmlpins';
const FROZEN_BROWSER = 'chrome-151.0.7922.47';
const oldPath = (t) => path.join(REPO, 'tools', 'wpt', 'refs', WPT_SHA, OLD_REV, t.split('/')[1], `${fixtureStem(t)}.png`);
const sha1 = (buf) => crypto.createHash('sha1').update(buf).digest('hex');

// The test list: every section's tests.list of the run (default the opening gate).
const runId = process.env.RUN || 'wave52-open';
const secRoot = path.join(TITAN, 'runs', runId, 'sections');
let tests = [];
for (const sec of fs.readdirSync(secRoot).sort()) {
  const tl = path.join(secRoot, sec, 'tests.list');
  if (fs.existsSync(tl)) tests.push(...fs.readFileSync(tl, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean));
}
if (process.env.ONLY) tests = tests.filter((t) => t.includes(process.env.ONLY));

// The OLD sheet, rebuilt from the live one by reversing exactly the B10 hunk
// (refused if the literal is absent, so a drifted module cannot fake an arm).
const NEW_SHEET = await cbr.canvasFrameCss();
const NEW_LIT = ':where(html) { background: #FFFFFF; }\n      :where(html, body) { margin: 0; padding: 0; }';
const OLD_LIT = ':where(html, body) { margin: 0; padding: 0; background: #FFFFFF; }';
if (!NEW_SHEET.includes(NEW_LIT)) { console.error('refreeze-ab: live sheet lacks the B10 literal'); process.exit(2); }
const OLD_SHEET = NEW_SHEET.replace(NEW_LIT, OLD_LIT);

// Replica of the pre-wave-52 render path (renderOne before the split): same
// viewport arithmetic, settle timers, font wait and image pad — only the sheet differs.
async function renderOld(page, testRel) {
  const refAbs = await cbr.resolveRefPath(testRel);
  await page.goto('file://' + encodeURI(refAbs), { waitUntil: 'load', timeout: 30_000 });
  await page.addStyleTag({ content: OLD_SHEET });
  await page.setViewport({ width: cbr.REF_RENDER_WIDTH, height: cbr.REF_RENDER_MIN_HEIGHT, deviceScaleFactor: 1 });
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  const h = await page.evaluate((floor) => Math.max(document.documentElement.scrollHeight, document.body?.scrollHeight ?? 0, floor), cbr.REF_RENDER_MIN_HEIGHT);
  await page.setViewport({ width: cbr.REF_RENDER_WIDTH, height: h, deviceScaleFactor: 1 });
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  await page.evaluate(() => document.fonts.ready.then(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))));
  return cbr.padPngBuffer(await page.screenshot({ type: 'png' }), cbr.CANVAS_PAD_PX, cbr.CANVAS_BG);
}

// One browser, one page, sequential — the same discipline as captureRefs.
const browser = await puppeteer.launch({ headless: 'new', args: cbr.BROWSER_LAUNCH_ARGS, protocolTimeout: 300_000 });
const browserRev = cbr.browserRevFrom(await browser.version());
if (browserRev !== FROZEN_BROWSER) console.error(`refreeze-ab: WARNING browser ${browserRev} != frozen ${FROZEN_BROWSER} — expect drift`);
const page = await browser.newPage();
page.on('pageerror', () => {});   // ref page script errors are the page's own business
const rows = [];
const t0 = Date.now();
for (const [i, t] of tests.entries()) {
  const row = { test: t };
  try {
    const frozen = fs.readFileSync(oldPath(t));
    row.frozenSha = sha1(frozen);
    const { png, uaBodyMargin } = await cbr.renderRefPng(page, t);
    row.newSha = sha1(png); row.uaBodyMargin = uaBodyMargin;
    if (row.newSha === row.frozenSha) row.cause = 'identical';
    else {
      // Keep the new render for the PNG replay, then run the old-sheet arm.
      const out = path.join(TITAN, 'runs', 'wave52-calib', 'refs-ab', t.split('/')[1], `${fixtureStem(t)}.png`);
      fs.mkdirSync(path.dirname(out), { recursive: true }); fs.writeFileSync(out, png);
      row.oldArmSha = sha1(await renderOld(page, t));
      row.cause = row.oldArmSha === row.frozenSha ? 'contract' : 'unstable';
    }
  } catch (e) { row.cause = 'error'; row.error = String(e?.message ?? e); }
  rows.push(row);
  if ((i + 1) % 100 === 0) console.error(`  ${i + 1}/${tests.length} ${((Date.now() - t0) / 1000).toFixed(0)}s`);
}
await browser.close();

// Summary + per-row record (the census the plan asked for: "sha1 every OTHER
// frozen ref against the previous freeze — the variant must not leak").
const by = (c) => rows.filter((r) => r.cause === c);
const out = { _lane: 'wave52 L12-B re-freeze A/B', run: runId, browserRev, oldRev: OLD_REV, newRev: cbr.CANVAS_REV,
  tests: rows.length, identical: by('identical').length, contract: by('contract').map((r) => r.test),
  unstable: by('unstable').map((r) => r.test), errors: by('error').map((r) => `${r.test}: ${r.error}`),
  uaBodyMarginFired: rows.filter((r) => r.uaBodyMargin).map((r) => r.test), seconds: Math.round((Date.now() - t0) / 1000), rows };
fs.writeFileSync(path.join(HERE, process.env.ONLY ? 'refreeze-ab.partial.json' : 'refreeze-ab.json'), JSON.stringify(out, null, 1) + '\n');
console.log(`refreeze-ab run=${runId} browser=${browserRev} tests=${rows.length} identical=${out.identical} contract=${out.contract.length} unstable=${out.unstable.length} errors=${out.errors.length} (${out.seconds}s)`);
for (const t of out.contract) console.log(`  CONTRACT  ${t}`);
for (const t of out.unstable) console.log(`  UNSTABLE  ${t}`);
for (const e of out.errors) console.log(`  ERROR     ${e}`);
