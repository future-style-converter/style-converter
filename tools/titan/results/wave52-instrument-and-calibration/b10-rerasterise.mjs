#!/usr/bin/env node
// tools/titan/results/wave52-instrument-and-calibration/b10-rerasterise.mjs
//
// wave-52 L12-B skeptic re-run of wave-50 B10's unverified claim: "42 corpus
// refs use a negative z-index, 28 of them rasterise differently under the fix"
// (BACKLOG "Instrument decisions pending": S6 called it UNVERIFIABLE). B10's own
// canvas-css-ab.mjs can no longer run (it rebuilds the patched sheet by string
// replacement on the LIVE canvasFrameCss, which now IS the patched sheet), so
// this script reproduces its METHOD with both sheets explicit:
//   B10 geometry: one fresh page per arm, 390×600 viewport, deviceScaleFactor 1,
//   goto(load) → addStyleTag(sheet) → screenshot — no image pad, no settle;
//   arm A = the pre-wave-52 sheet (background on :where(html, body)),
//   arm B = the shipped sheet (background on :where(html) only).
// Comparison is on DECODED PIXELS in one session (byte hashes are not stable
// across PNG encoders — refreeze-ab.mjs's finding), plus B10's chroma counts.
// The ref list is wave50-S6/zneg-census.json `corpusNegRefs` (the 42).
// Usage: node …/b10-rerasterise.mjs   (writes b10-rerasterise.json)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer';
import { PNG } from 'pngjs';

// Paths, the shipped sheet, and its pre-wave-52 twin.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const TITAN = path.resolve(HERE, '..', '..');
const REPO = path.resolve(TITAN, '..', '..');
const cbr = await import(path.join(TITAN, 'capture-browser-ref.mjs'));
const NEW = await cbr.canvasFrameCss();
const OLD = NEW.replace(':where(html) { background: #FFFFFF; }\n      :where(html, body) { margin: 0; padding: 0; }',
  ':where(html, body) { margin: 0; padding: 0; background: #FFFFFF; }');
if (OLD === NEW) { console.error('b10-rerasterise: B10 literal not found'); process.exit(2); }
const refs = JSON.parse(fs.readFileSync(path.join(TITAN, 'results', 'wave50-S6', 'zneg-census.json'), 'utf8')).corpusNegRefs;

// B10's chroma metric: saturated pixels (max−min channel > 40) and non-white.
function counts(p) {
  let chroma = 0, nonwhite = 0;
  for (let i = 0; i < p.data.length; i += 4) {
    const r = p.data[i], g = p.data[i + 1], b = p.data[i + 2];
    if (Math.max(r, g, b) - Math.min(r, g, b) > 40) chroma++;
    if (r < 250 || g < 250 || b < 250) nonwhite++;
  }
  return { chroma, nonwhite };
}

const browser = await puppeteer.launch({ headless: 'new', args: cbr.BROWSER_LAUNCH_ARGS });
const rows = [];
for (const rel of refs) {
  const arms = [];
  for (const css of [OLD, NEW]) {
    // One fresh page per arm, B10's exact sequence.
    const page = await browser.newPage();
    page.on('pageerror', () => {});
    await page.setViewport({ width: 390, height: 600, deviceScaleFactor: 1 });
    try { await page.goto('file://' + path.join(REPO, rel), { waitUntil: 'load', timeout: 15000 }); } catch { /* B10 tolerated load timeouts */ }
    await page.addStyleTag({ content: css });
    arms.push(PNG.sync.read(await page.screenshot({ type: 'png' })));
    await page.close();
  }
  const [a, b] = arms;
  const differs = a.width !== b.width || a.height !== b.height || Buffer.compare(a.data, b.data) !== 0;
  rows.push({ ref: rel, differs, before: counts(a), after: counts(b) });
}
await browser.close();
const out = { _lane: 'wave52 L12-B skeptic re-run of wave50-B10 "28 of 42"', refs: rows.length, differs: rows.filter((r) => r.differs).length, rows };
fs.writeFileSync(path.join(HERE, 'b10-rerasterise.json'), JSON.stringify(out, null, 1) + '\n');
console.log(`b10-rerasterise: ${out.differs} of ${out.refs} negative-z-index corpus refs rasterise differently (B10 claimed 28 of 42)`);
for (const r of rows) console.log(`  ${r.differs ? 'DIFFERS ' : 'same    '} chroma ${r.before.chroma}→${r.after.chroma} nonwhite ${r.before.nonwhite}→${r.after.nonwhite}  ${r.ref}`);
